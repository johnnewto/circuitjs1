package com.lushprojects.circuitjs1.client.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

/**
 * Service for parsing and normalizing R-style SFCR blocks.
 * 
 * This service can operate in two modes:
 * <ul>
 *   <li><b>Normalization mode</b>: Uses {@code normalizeMatrixBlockStatic()} and 
 *       {@code normalizeEquationsBlockStatic()} which only require static utility methods.
 *       Lookup calls are preserved as-is (no rewriting).</li>
 *   <li><b>Full parsing mode</b>: Uses the instance methods with a {@link NormalizationCallback}
 *       that provides lookup rewriting capability.</li>
 * </ul>
 */
public class RStyleParseService {

    /**
     * Callback interface for normalization operations that may need 
     * parser-specific behavior (e.g., lookup rewriting).
     */
    public interface NormalizationCallback {
        /** Rewrite lookup calls in an expression for the given scope. */
        String rewriteLookupCalls(String expr, String scope);
    }

    public SFCRParser.RStyleBlockMetadata consumeMetadataFromComments(Vector<String> pendingComments) {
        SFCRParser.RStyleBlockMetadata metadata = new SFCRParser.RStyleBlockMetadata();
        if (pendingComments == null || pendingComments.size() == 0) {
            return metadata;
        }

        Vector<String> preserved = new Vector<String>();
        for (int i = 0; i < pendingComments.size(); i++) {
            String raw = pendingComments.get(i);
            if (!parseMetadataLine(raw, metadata)) {
                preserved.add(raw);
            }
        }

        pendingComments.clear();
        for (int i = 0; i < preserved.size(); i++) {
            pendingComments.add(preserved.get(i));
        }
        return metadata;
    }

    /**
     * Normalize a matrix block using only static utilities.
     * Lookup calls are preserved as-is (no rewriting).
     * Use this for pre-parse normalization.
     */
    public String[] normalizeMatrixBlockStatic(String block, SFCRParser.RStyleBlockMetadata metadata) {
        String matrixName = extractAssignmentName(block, "Matrix");
        ArrayList<String> columnNames = extractRVector(block, "columns");
        ArrayList<String> columnCodes = extractRVector(block, "codes");
        ArrayList<String> columnTypes = extractRVector(block, "type");

        SFCRParser.RStyleBlockMetadata effectiveMetadata = new SFCRParser.RStyleBlockMetadata();
        if (metadata != null) {
            effectiveMetadata.x = metadata.x;
            effectiveMetadata.y = metadata.y;
            effectiveMetadata.uid = metadata.uid;
            effectiveMetadata.type = metadata.type;
            effectiveMetadata.invisible = metadata.invisible;
        }

        int matrixStart = block.indexOf("sfcr_matrix(");
        if (matrixStart >= 0) {
            int contentStart = matrixStart + "sfcr_matrix(".length();
            int contentEnd = findMatchingParen(block, contentStart - 1);
            if (contentEnd > contentStart) {
                String content = block.substring(contentStart, contentEnd);
                String[] contentLines = content.split("\\r?\\n");
                for (int li = 0; li < contentLines.length; li++) {
                    String trimmed = contentLines[li].trim();
                    if (trimmed.startsWith("#")) {
                        parseMetadataLine(trimmed, effectiveMetadata);
                    }
                }
            }
        }

        ArrayList<String> rowNames = new ArrayList<String>();
        ArrayList<String[]> tableRows = new ArrayList<String[]>();

        if (matrixStart >= 0) {
            int contentStart = matrixStart + "sfcr_matrix(".length();
            int contentEnd = findMatchingParen(block, contentStart - 1);
            if (contentEnd > contentStart) {
                String content = block.substring(contentStart, contentEnd);
                ArrayList<String> args = splitByTopLevelComma(content);
                for (int ai = 0; ai < args.size(); ai++) {
                    String arg = args.get(ai).trim();
                    if (arg.startsWith("c(")) {
                        int argEnd = arg.lastIndexOf(')');
                        if (argEnd > 2) {
                            String rowDef = arg.substring(2, argEnd);
                            parseRow(rowDef, rowNames, tableRows, columnCodes);
                        }
                    }
                }
            }
        }

        if (columnNames.isEmpty() || tableRows.isEmpty()) {
            return null;
        }

        String matrixType = (effectiveMetadata.type != null && !effectiveMetadata.type.trim().isEmpty())
            ? effectiveMetadata.type.trim() : "transaction_flow";
        StringBuilder normalized = new StringBuilder();
        normalized.append("@matrix ").append(matrixName);
        if (effectiveMetadata.hasPosition()) {
            normalized.append(" x=").append(effectiveMetadata.x).append(" y=").append(effectiveMetadata.y);
        }
        normalized.append("\n");
        if (effectiveMetadata.uid != null && !effectiveMetadata.uid.trim().isEmpty()) {
            normalized.append("  uid: ").append(effectiveMetadata.uid.trim()).append("\n");
        }
        normalized.append("  type: ").append(matrixType).append("\n");
        if (effectiveMetadata.invisible != null) {
            normalized.append("  invisible: ").append(effectiveMetadata.invisible.booleanValue()).append("\n");
        }
        if (!columnTypes.isEmpty()) {
            normalized.append("  columnTypes: ");
            for (int i = 0; i < columnTypes.size(); i++) {
                if (i > 0) {
                    normalized.append(", ");
                }
                normalized.append(columnTypes.get(i));
            }
            normalized.append("\n");
        }
        normalized.append("\n");
        normalized.append("| Transaction |");
        for (int i = 0; i < columnNames.size(); i++) {
            normalized.append(" ").append(columnNames.get(i)).append(" |");
        }
        normalized.append("\n");
        normalized.append("|-------------|");
        for (int i = 0; i < columnNames.size(); i++) {
            normalized.append("------|");
        }
        normalized.append("\n");

        for (int r = 0; r < rowNames.size() && r < tableRows.size(); r++) {
            normalized.append("| ").append(rowNames.get(r)).append(" |");
            String[] rowData = tableRows.get(r);
            for (int c = 0; c < columnNames.size(); c++) {
                String expr = (c < rowData.length) ? rowData[c] : "";
                normalized.append(" ").append(expr).append(" |");
            }
            normalized.append("\n");
        }
        normalized.append("@end\n");
        return normalized.toString().split("\\n");
    }

    /**
     * Normalize an equations block using only static utilities.
     * Lookup calls are preserved as-is (no rewriting).
     * Use this for pre-parse normalization.
     */
    public String[] normalizeEquationsBlockStatic(String block, SFCRParser.RStyleBlockMetadata metadata) {
        return normalizeEquationsBlockInternal(block, metadata, null);
    }

    /**
     * Normalize an equations block with optional lookup rewriting callback.
     * 
     * @param block the R-style block text
     * @param metadata position/type metadata
     * @param callback optional callback for lookup rewriting (null to preserve as-is)
     */
    public String[] normalizeEquationsBlockInternal(String block, SFCRParser.RStyleBlockMetadata metadata,
                                                    NormalizationCallback callback) {
        String blockName = extractAssignmentName(block, "Equations");

        int start = block.indexOf("sfcr_set(");
        if (start < 0) {
            return null;
        }
        start += 9;

        int end = findMatchingParen(block, start - 1);
        if (end < 0) {
            return null;
        }

        String content = block.substring(start, end);

        SFCRParser.RStyleBlockMetadata effectiveMetadata = new SFCRParser.RStyleBlockMetadata();
        if (metadata != null) {
            effectiveMetadata.x = metadata.x;
            effectiveMetadata.y = metadata.y;
            effectiveMetadata.uid = metadata.uid;
            effectiveMetadata.type = metadata.type;
            effectiveMetadata.invisible = metadata.invisible;
        }
        applyInlineMetadataComments(content, effectiveMetadata);
        int currentSectionMode = 0;
        StringBuilder normalized = new StringBuilder();
        normalized.append("@equations ").append(blockName);
        if (effectiveMetadata.hasPosition()) {
            normalized.append(" x=").append(effectiveMetadata.x).append(" y=").append(effectiveMetadata.y);
        }
        normalized.append("\n");
        if (effectiveMetadata.uid != null && !effectiveMetadata.uid.trim().isEmpty()) {
            normalized.append("  uid: ").append(effectiveMetadata.uid.trim()).append("\n");
        }
        if (effectiveMetadata.invisible != null) {
            normalized.append("  invisible: ").append(effectiveMetadata.invisible.booleanValue()).append("\n");
        }
        int rowCount = 0;

        ArrayList<String> parts = splitByTopLevelCommaIgnoringHashComments(content);
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }

            String[] partLines = part.split("\\r?\\n");
            StringBuilder cleanedPart = new StringBuilder();
            for (int li = 0; li < partLines.length; li++) {
                String line = partLines[li].trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#")) {
                    if (parseMetadataLine(line, effectiveMetadata)) {
                        continue;
                    }

                    String sectionText = line.substring(1).trim().toLowerCase();
                    if (isParametersSectionComment(sectionText)) {
                        currentSectionMode = 3;
                    }

                    String commentText = line.substring(1).trim();
                    if (!commentText.isEmpty()) {
                        normalized.append("  # ").append(commentText).append("\n");
                    }
                    continue;
                }
                if (cleanedPart.length() > 0) {
                    cleanedPart.append(" ");
                }
                cleanedPart.append(line);
            }

            part = cleanedPart.toString().trim();
            if (part.isEmpty()) {
                continue;
            }

            int inlineCommentIdx = part.indexOf('#');
            String inlineComment = null;
            if (inlineCommentIdx >= 0) {
                inlineComment = part.substring(inlineCommentIdx + 1).trim();
                part = part.substring(0, inlineCommentIdx).trim();
                if (part.isEmpty()) {
                    continue;
                }
            }

            while (part.endsWith(",")) {
                part = part.substring(0, part.length() - 1).trim();
            }
            if (part.isEmpty()) {
                continue;
            }

            HashMap<String, String> inlineMeta = new HashMap<String, String>();
            if (inlineComment != null && !inlineComment.isEmpty()) {
                String parsedHint = stripTrailingInlineMetadata(inlineComment, inlineMeta);
                inlineComment = (parsedHint == null) ? "" : parsedHint;
            }

            int tildeIdx = part.indexOf('~');
            if (tildeIdx >= 0) {
                String beforeTilde = part.substring(0, tildeIdx).trim();
                int eqIdx = beforeTilde.indexOf("=");
                if (eqIdx > 0) {
                    beforeTilde = beforeTilde.substring(eqIdx + 1).trim();
                    part = beforeTilde + " ~ " + part.substring(tildeIdx + 1);
                }
            }

            tildeIdx = part.indexOf('~');
            if (tildeIdx < 0) {
                continue;
            }

            String name = SFCRUtil.normalizeVariableName(part.substring(0, tildeIdx).trim());
            String expr = SFCRUtil.normalizeExpression(part.substring(tildeIdx + 1).trim());
            if (callback != null) {
                expr = callback.rewriteLookupCalls(expr, blockName);
            }
            int mode = currentSectionMode;
            String inlineMode = inlineMeta.get("mode");
            if (inlineMode != null && !inlineMode.isEmpty()) {
                mode = SFCRParser.parseModeOrdinal(inlineMode);
            }

            String initialEq = inlineMeta.get("initial");
            if (initialEq != null && !initialEq.trim().isEmpty()) {
                if (callback != null) {
                    initialEq = callback.rewriteLookupCalls(initialEq, blockName);
                }
            }

            normalized.append("  ").append(name).append(" ~ ").append(expr)
                .append(" ; mode=").append(toModeKeyword(mode));
            String target = inlineMeta.get("target");
            if (target != null && !target.trim().isEmpty()) {
                normalized.append(" ; target=")
                    .append(SFCRUtil.normalizeVariableName(target.trim()));
            }
            String sliderVar = inlineMeta.get("slider");
            if (sliderVar != null && !sliderVar.trim().isEmpty()) {
                normalized.append(" ; slider=").append(sliderVar.trim());
            }
            String sliderValueStr = inlineMeta.get("slidervalue");
            if (sliderValueStr != null && !sliderValueStr.trim().isEmpty()) {
                normalized.append(" ; sliderValue=").append(sliderValueStr.trim());
            }
            if (initialEq != null && !initialEq.trim().isEmpty()) {
                normalized.append(" ; initial=").append(initialEq.trim());
            }

            if (inlineComment != null) {
                inlineComment = inlineComment.trim();
            }
            if (inlineComment != null && !inlineComment.isEmpty()) {
                normalized.append("  # ").append(inlineComment);
            }
            normalized.append("\n");
            rowCount++;
        }
        if (rowCount == 0) {
            return null;
        }
        normalized.append("@end\n");
        return normalized.toString().split("\\n");
    }

    private void applyInlineMetadataComments(String content, SFCRParser.RStyleBlockMetadata metadata) {
        if (content == null || metadata == null) {
            return;
        }
        String[] contentLines = content.split("\\r?\\n");
        for (int li = 0; li < contentLines.length; li++) {
            String trimmed = contentLines[li].trim();
            if (trimmed.startsWith("#")) {
                parseMetadataLine(trimmed, metadata);
            }
        }
    }

    /**
     * Extract assignment name from R-style block: name <- sfcr_...
     */
    private static String extractAssignmentName(String block, String defaultName) {
        if (block == null) {
            return defaultName;
        }
        String firstAssignmentLine = null;
        String[] lines = block.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (looksLikeRStyleAssignmentStart(line)) {
                firstAssignmentLine = line;
                break;
            }
        }
        if (firstAssignmentLine == null) {
            return defaultName;
        }

        int idx = 0;
        int length = firstAssignmentLine.length();
        while (idx < length && Character.isWhitespace(firstAssignmentLine.charAt(idx))) {
            idx++;
        }
        if (idx >= length || !Character.isLetter(firstAssignmentLine.charAt(idx)) && firstAssignmentLine.charAt(idx) != '_') {
            return defaultName;
        }

        int start = idx;
        idx++;
        while (idx < length) {
            char ch = firstAssignmentLine.charAt(idx);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                break;
            }
            idx++;
        }

        String name = firstAssignmentLine.substring(start, idx).trim();
        return name.isEmpty() ? defaultName : name;
    }

    private static boolean looksLikeRStyleAssignmentStart(String trimmed) {
        if (trimmed == null) {
            return false;
        }
        int idx = 0;
        int length = trimmed.length();
        while (idx < length && Character.isWhitespace(trimmed.charAt(idx))) {
            idx++;
        }
        if (idx >= length) {
            return false;
        }

        char first = trimmed.charAt(idx);
        if (!Character.isLetter(first) && first != '_') {
            return false;
        }
        idx++;
        while (idx < length) {
            char ch = trimmed.charAt(idx);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                break;
            }
            idx++;
        }
        while (idx < length && Character.isWhitespace(trimmed.charAt(idx))) {
            idx++;
        }
        if (idx >= length) {
            return false;
        }

        if (trimmed.charAt(idx) == '=') {
            idx++;
        } else if (trimmed.charAt(idx) == '<' && idx + 1 < length && trimmed.charAt(idx + 1) == '-') {
            idx += 2;
        } else {
            return false;
        }

        while (idx < length && Character.isWhitespace(trimmed.charAt(idx))) {
            idx++;
        }
        if (!trimmed.startsWith("sfcr_", idx)) {
            return false;
        }
        idx += 5;
        if (trimmed.startsWith("set", idx)) {
            idx += 3;
        } else if (trimmed.startsWith("matrix", idx)) {
            idx += 6;
        } else {
            return false;
        }
        while (idx < length && Character.isWhitespace(trimmed.charAt(idx))) {
            idx++;
        }
        return idx < length && trimmed.charAt(idx) == '(';
    }

    private boolean parseMetadataLine(String rawLine, SFCRParser.RStyleBlockMetadata metadata) {
        if (rawLine == null || metadata == null) {
            return false;
        }
        String line = rawLine.trim();
        if (line.startsWith("#")) {
            line = line.substring(1).trim();
        }
        if (!line.startsWith("[") || !line.endsWith("]")) {
            return false;
        }

        String inner = line.substring(1, line.length() - 1).trim();
        if (inner.isEmpty()) {
            return true;
        }

        String[] tokens = inner.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.startsWith("x=")) {
                Integer parsed = parseIntSafe(token.substring(2));
                if (parsed != null) {
                    metadata.x = parsed.intValue();
                }
                continue;
            }
            if (token.startsWith("y=")) {
                Integer parsed = parseIntSafe(token.substring(2));
                if (parsed != null) {
                    metadata.y = parsed.intValue();
                }
                continue;
            }
            if (token.startsWith("uid=")) {
                String value = token.substring(4).trim();
                if (!value.isEmpty()) {
                    metadata.uid = value;
                }
                continue;
            }
            if (token.equals("uid:") && i + 1 < tokens.length) {
                String value = tokens[++i].trim();
                if (!value.isEmpty()) {
                    metadata.uid = value;
                }
                continue;
            }
            if (token.startsWith("uid:")) {
                String value = token.substring(4).trim();
                if (!value.isEmpty()) {
                    metadata.uid = value;
                }
                continue;
            }
            if (token.startsWith("type=")) {
                String value = token.substring(5).trim();
                if (!value.isEmpty()) {
                    metadata.type = value;
                }
                continue;
            }
            if (token.equals("type:") && i + 1 < tokens.length) {
                String value = tokens[++i].trim();
                if (!value.isEmpty()) {
                    metadata.type = value;
                }
                continue;
            }
            if (token.startsWith("type:")) {
                String value = token.substring(5).trim();
                if (!value.isEmpty()) {
                    metadata.type = value;
                }
                continue;
            }
            if (token.startsWith("invisible=")) {
                metadata.invisible = parseBooleanSafe(token.substring(10));
                continue;
            }
            if (token.equals("invisible:") && i + 1 < tokens.length) {
                metadata.invisible = parseBooleanSafe(tokens[++i]);
                continue;
            }
            if (token.startsWith("invisible:")) {
                metadata.invisible = parseBooleanSafe(token.substring(10));
                continue;
            }
            if (token.startsWith("hidden=")) {
                metadata.invisible = parseBooleanSafe(token.substring(7));
                continue;
            }
            if (token.equals("hidden:") && i + 1 < tokens.length) {
                metadata.invisible = parseBooleanSafe(tokens[++i]);
                continue;
            }
            if (token.startsWith("hidden:")) {
                metadata.invisible = parseBooleanSafe(token.substring(7));
            }
        }
        return true;
    }

    private Integer parseIntSafe(String value) {
        try {
            return Integer.valueOf(Integer.parseInt(value.trim()));
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean parseBooleanSafe(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim().toLowerCase();
        if (t.equals("true") || t.equals("1") || t.equals("yes")) {
            return Boolean.TRUE;
        }
        if (t.equals("false") || t.equals("0") || t.equals("no")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private void parseRow(String rowDef, ArrayList<String> rowNames,
                          ArrayList<String[]> tableRows, ArrayList<String> codes) {
        int firstQuote = rowDef.indexOf('"');
        int secondQuote = findClosingQuote(rowDef, firstQuote);
        if (firstQuote < 0 || secondQuote < 0) {
            return;
        }

        String rowName = unescapeRString(rowDef.substring(firstQuote + 1, secondQuote));
        rowNames.add(rowName);

        String[] rowData = new String[codes.size()];
        for (int i = 0; i < rowData.length; i++) {
            rowData[i] = "";
        }

        String rest = rowDef.substring(secondQuote + 1);
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            String exprValue = extractQuotedAssignmentValue(rest, code);
            if (exprValue != null) {
                rowData[i] = SFCRUtil.normalizeExpression(exprValue);
            }
        }

        tableRows.add(rowData);
    }

    private String extractQuotedAssignmentValue(String text, String code) {
        if (text == null || code == null || code.length() == 0) {
            return null;
        }

        String pattern1 = code + " = \"";
        String pattern2 = code + "=\"";
        int idx = text.indexOf(pattern1);
        int patternLen = pattern1.length();
        if (idx < 0) {
            idx = text.indexOf(pattern2);
            patternLen = pattern2.length();
        }
        if (idx < 0) {
            return null;
        }

        int valueStart = idx + patternLen;
        int valueEnd = findClosingQuote(text, valueStart - 1);
        if (valueEnd <= valueStart) {
            return null;
        }
        return unescapeRString(text.substring(valueStart, valueEnd));
    }

    private ArrayList<String> extractRVector(String block, String name) {
        ArrayList<String> result = new ArrayList<String>();
        String pattern = name + " = c(";
        int idx = block.indexOf(pattern);
        if (idx < 0) {
            pattern = name + "=c(";
            idx = block.indexOf(pattern);
        }
        if (idx < 0) {
            return result;
        }

        int start = idx + pattern.length();
        int end = findMatchingParen(block, start - 1);
        if (end < 0) {
            return result;
        }

        String content = block.substring(start, end);
        int pos = 0;
        while (true) {
            int q1 = content.indexOf('"', pos);
            if (q1 < 0) {
                break;
            }
            int q2 = findClosingQuote(content, q1);
            if (q2 < 0) {
                break;
            }
            result.add(unescapeRString(content.substring(q1 + 1, q2)));
            pos = q2 + 1;
        }
        return result;
    }

    private int findClosingQuote(String text, int openQuoteIdx) {
        if (text == null || openQuoteIdx < 0 || openQuoteIdx >= text.length() || text.charAt(openQuoteIdx) != '"') {
            return -1;
        }
        boolean escaped = false;
        for (int i = openQuoteIdx + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private String unescapeRString(String text) {
        if (text == null || text.indexOf('\\') < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!escaped) {
                if (c == '\\') {
                    escaped = true;
                } else {
                    sb.append(c);
                }
                continue;
            }
            switch (c) {
                case '\\': sb.append('\\'); break;
                case '"': sb.append('"'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                default: sb.append('\\').append(c); break;
            }
            escaped = false;
        }
        if (escaped) {
            sb.append('\\');
        }
        return sb.toString();
    }

    private int findMatchingParen(String text, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            }
            if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private ArrayList<String> splitByTopLevelComma(String text) {
        ArrayList<String> result = new ArrayList<String>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            }
            if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                int segmentEnd = i;
                int nextStart = i + 1;
                int j = skipHorizontalWhitespace(text, i + 1);
                if (j < text.length() && text.charAt(j) == '#') {
                    int k = skipToLineEnd(text, j);
                    segmentEnd = k;
                    nextStart = k;
                    while (nextStart < text.length() &&
                        (text.charAt(nextStart) == '\n' || text.charAt(nextStart) == '\r')) {
                        nextStart++;
                    }
                    i = nextStart - 1;
                }
                result.add(text.substring(start, segmentEnd));
                start = nextStart;
            }
        }
        if (start < text.length()) {
            result.add(text.substring(start));
        }
        return result;
    }

    private int skipHorizontalWhitespace(String text, int idx) {
        int out = idx;
        while (out < text.length()) {
            char c = text.charAt(out);
            if (c != ' ' && c != '\t') {
                break;
            }
            out++;
        }
        return out;
    }

    private int skipToLineEnd(String text, int idx) {
        int out = idx;
        while (out < text.length()) {
            char c = text.charAt(out);
            if (c == '\n' || c == '\r') {
                break;
            }
            out++;
        }
        return out;
    }

    private boolean isParametersSectionComment(String sectionText) {
        return sectionText.startsWith("parameters") || sectionText.startsWith("parameter");
    }

    private String stripTrailingInlineMetadata(String comment, HashMap<String, String> outMeta) {
        if (comment == null) {
            return "";
        }
        String trimmed = comment.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (outMeta == null) {
            return trimmed;
        }

        String working = trimmed;
        int parsedTotal = 0;
        while (true) {
            int close = working.lastIndexOf(']');
            if (close != working.length() - 1) {
                break;
            }
            int open = working.lastIndexOf('[', close);
            if (open < 0) {
                break;
            }
            String metaChunk = working.substring(open + 1, close).trim();
            int parsedThisChunk = parseInlineMetadataChunk(metaChunk, outMeta);
            if (parsedThisChunk == 0) {
                break;
            }
            parsedTotal += parsedThisChunk;
            working = working.substring(0, open).trim();
        }

        while (true) {
            int open = working.lastIndexOf('[');
            if (open < 0) {
                break;
            }
            String tail = working.substring(open + 1).trim();
            int parsedTail = parseInlineMetadataChunk(tail, outMeta);
            if (parsedTail == 0) {
                break;
            }
            parsedTotal += parsedTail;
            working = working.substring(0, open).trim();
        }

        if (parsedTotal == 0) {
            return trimmed;
        }
        return working;
    }

    private ArrayList<String> splitByTopLevelCommaIgnoringHashComments(String text) {
        ArrayList<String> result = new ArrayList<String>();
        int depth = 0;
        int start = 0;
        boolean inHashComment = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inHashComment) {
                if (c == '\n' || c == '\r') {
                    inHashComment = false;
                }
                continue;
            }
            if (c == '#') {
                inHashComment = true;
                continue;
            }
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')') {
                depth--;
                continue;
            }
            if (c == ',' && depth == 0) {
                int commentStart = i + 1;
                while (commentStart < text.length()) {
                    char ws = text.charAt(commentStart);
                    if (ws != ' ' && ws != '\t') {
                        break;
                    }
                    commentStart++;
                }
                if (commentStart < text.length() && text.charAt(commentStart) == '#') {
                    int lineEnd = commentStart;
                    while (lineEnd < text.length()) {
                        char lc = text.charAt(lineEnd);
                        if (lc == '\n' || lc == '\r') {
                            break;
                        }
                        lineEnd++;
                    }
                    String partWithComment = text.substring(start, lineEnd).trim();
                    if (!partWithComment.isEmpty()) {
                        result.add(partWithComment);
                    }
                    start = lineEnd;
                    if (start < text.length() && text.charAt(start) == '\r') {
                        start++;
                    }
                    if (start < text.length() && text.charAt(start) == '\n') {
                        start++;
                    }
                    i = start - 1;
                } else {
                    String part = text.substring(start, i).trim();
                    if (!part.isEmpty()) {
                        result.add(part);
                    }
                    start = i + 1;
                }
            }
        }

        if (start < text.length()) {
            String lastPart = text.substring(start).trim();
            if (!lastPart.isEmpty()) {
                result.add(lastPart);
            }
        }
        return result;
    }

    private int parseInlineMetadataChunk(String metaChunk, HashMap<String, String> outMeta) {
        if (metaChunk == null || outMeta == null) {
            return 0;
        }
        String chunk = metaChunk.trim();
        if (chunk.isEmpty()) {
            return 0;
        }
        String[] tokens = chunk.split(",");
        int parsed = 0;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = token.substring(0, eq).trim().toLowerCase();
            String value = token.substring(eq + 1).trim();
            if (!key.isEmpty()) {
                outMeta.put(key, value);
                parsed++;
            }
        }
        return parsed;
    }

    private String toModeKeyword(int mode) {
        if (mode == 1) {
            return "flow";
        }
        if (mode == 3) {
            return "param";
        }
        return "voltage";
    }
}
