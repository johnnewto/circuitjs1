package com.lushprojects.circuitjs1.client.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

class RStyleParseService {

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

    public void parseMatrix(SFCRParser parser, String block, SFCRParser.RStyleBlockMetadata metadata) {
        String matrixName = parser.extractRStyleAssignmentNameForHandler(block, "Matrix");
        ArrayList<String> columnNames = extractRVector(block, "columns");
        ArrayList<String> columnCodes = extractRVector(block, "codes");

        SFCRParser.RStyleBlockMetadata effectiveMetadata = new SFCRParser.RStyleBlockMetadata();
        if (metadata != null) {
            effectiveMetadata.x = metadata.x;
            effectiveMetadata.y = metadata.y;
            effectiveMetadata.type = metadata.type;
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

        int savedX = parser.getCurrentXForHandler();
        int savedY = parser.getCurrentYForHandler();
        if (effectiveMetadata.hasPosition()) {
            parser.setCurrentPositionForHandler(effectiveMetadata.x, effectiveMetadata.y);
        }

        if (!columnNames.isEmpty() && !tableRows.isEmpty()) {
            String matrixType = (effectiveMetadata.type != null && !effectiveMetadata.type.trim().isEmpty())
                ? effectiveMetadata.type.trim() : "transaction_flow";
            parser.createMatrixTableForHandler(matrixName, columnNames, rowNames, tableRows, matrixType,
                null, null, null);
        }

        if (effectiveMetadata.hasPosition()) {
            parser.setCurrentPositionForHandler(savedX, savedY);
        }
    }

    public void parseEquations(SFCRParser parser, String block, SFCRParser.RStyleBlockMetadata metadata) {
        String blockName = parser.extractRStyleAssignmentNameForHandler(block, "Equations");

        ArrayList<String> outputNames = new ArrayList<String>();
        ArrayList<String> equations = new ArrayList<String>();
        ArrayList<Integer> outputModes = new ArrayList<Integer>();
        ArrayList<String> targetNodeNames = new ArrayList<String>();
        ArrayList<String> sliderVarNames = new ArrayList<String>();
        ArrayList<Double> sliderValues = new ArrayList<Double>();
        ArrayList<String> initialEquations = new ArrayList<String>();

        int start = block.indexOf("sfcr_set(");
        if (start < 0) {
            return;
        }
        start += 9;

        int end = findMatchingParen(block, start - 1);
        if (end < 0) {
            return;
        }

        String content = block.substring(start, end);

        SFCRParser.RStyleBlockMetadata effectiveMetadata = new SFCRParser.RStyleBlockMetadata();
        if (metadata != null) {
            effectiveMetadata.x = metadata.x;
            effectiveMetadata.y = metadata.y;
            effectiveMetadata.type = metadata.type;
        }
        int currentSectionMode = 0;

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

                    appendCommentRow(line, outputNames, equations, outputModes, targetNodeNames,
                        sliderVarNames, sliderValues, initialEquations);
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

            String name = parser.normalizeVariableNameForHandler(part.substring(0, tildeIdx).trim());
            String expr = parser.normalizeExpressionForHandler(part.substring(tildeIdx + 1).trim());
            expr = parser.rewriteLookupCallsForHandler(expr, blockName);

            outputNames.add(name);
            equations.add(expr);
            int mode = currentSectionMode;
            String inlineMode = inlineMeta.get("mode");
            if (inlineMode != null && !inlineMode.isEmpty()) {
                mode = SFCRParser.parseModeOrdinal(inlineMode);
            }
            outputModes.add(Integer.valueOf(mode));
            targetNodeNames.add("");

            String sliderVar = inlineMeta.get("slider");
            sliderVarNames.add((sliderVar != null) ? sliderVar : "");

            double sliderValue = 0.0;
            String sliderValueStr = inlineMeta.get("slidervalue");
            if (sliderValueStr != null) {
                try {
                    sliderValue = Double.parseDouble(sliderValueStr.trim());
                } catch (Exception e) {
                }
            }
            sliderValues.add(Double.valueOf(sliderValue));

            String initialEq = inlineMeta.get("initial");
            if (initialEq != null && !initialEq.trim().isEmpty()) {
                initialEq = parser.rewriteLookupCallsForHandler(initialEq, blockName);
            }
            initialEquations.add((initialEq != null) ? initialEq : "");

            if (inlineComment != null) {
                inlineComment = inlineComment.trim();
            }
            if (inlineComment != null && !inlineComment.isEmpty() && !parser.hasHintForHandler(name)) {
                parser.registerHintForHandler(name, inlineComment);
            }
        }

        int savedX = parser.getCurrentXForHandler();
        int savedY = parser.getCurrentYForHandler();
        if (effectiveMetadata.hasPosition()) {
            parser.setCurrentPositionForHandler(effectiveMetadata.x, effectiveMetadata.y);
        }

        if (!outputNames.isEmpty()) {
            parser.createEquationTableForHandler(blockName, outputNames, equations, outputModes,
                targetNodeNames, sliderVarNames, sliderValues, initialEquations);
        }

        if (effectiveMetadata.hasPosition()) {
            parser.setCurrentPositionForHandler(savedX, savedY);
        }
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

    private void appendCommentRow(String comment,
                                  ArrayList<String> outputNames,
                                  ArrayList<String> equations,
                                  ArrayList<Integer> outputModes,
                                  ArrayList<String> targetNodeNames,
                                  ArrayList<String> sliderVarNames,
                                  ArrayList<Double> sliderValues,
                                  ArrayList<String> initialEquations) {
        if (comment == null) {
            return;
        }
        String text = comment.trim();
        if (text.startsWith("#")) {
            text = text.substring(1).trim();
        }
        if (text.isEmpty()) {
            return;
        }
        outputNames.add("# " + text);
        equations.add("");
        outputModes.add(Integer.valueOf(3));
        targetNodeNames.add("");
        sliderVarNames.add("");
        sliderValues.add(Double.valueOf(0));
        initialEquations.add("");
    }
}
