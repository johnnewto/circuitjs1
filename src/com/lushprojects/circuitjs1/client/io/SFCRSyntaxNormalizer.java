/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.io;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Normalizes SFCR input text by converting R-style syntax to block format.
 * 
 * This enables a single parsing path: R-style input is normalized to block
 * format before the main parser runs, eliminating duplicate code paths.
 * 
 * Conversions performed:
 * <ul>
 *   <li>{@code name <- sfcr_set(...)} → {@code @equations name ... @end}</li>
 *   <li>{@code name = sfcr_set(...)} → {@code @equations name ... @end}</li>
 *   <li>{@code name <- sfcr_matrix(...)} → {@code @matrix name ... @end}</li>
 *   <li>{@code name = sfcr_matrix(...)} → {@code @matrix name ... @end}</li>
 *   <li>R-style metadata comments {@code # [ x=N y=N type: T ]} are preserved and 
 *       converted to block header attributes</li>
 * </ul>
 * 
 * Block-style content passes through unchanged.
 * 
 * @see SFCRParser
 * @see RStyleParseService
 */
public class SFCRSyntaxNormalizer {

    private final RStyleParseService rStyleService = new RStyleParseService();

    /**
     * Normalize input text by converting R-style blocks to block format.
     * 
     * @param input raw SFCR text (may contain R-style or block-style or mixed)
     * @return normalized text with all R-style converted to block format
     */
    public String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String normalized = containsRStyleContent(input) ? normalizeRStyleBlocks(input) : input;
        return mergeDuplicateInitialAssignments(normalized);
    }

    /**
     * Check if input contains R-style sfcr syntax.
     */
    public static boolean containsRStyleContent(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("sfcr_set") || text.contains("sfcr_matrix");
    }

    /**
     * Normalize R-style blocks to block format.
     */
    private String normalizeRStyleBlocks(String input) {
        String[] lines = input.split("\n", -1);
        StringBuilder result = new StringBuilder();
        ArrayList<String> pendingComments = new ArrayList<String>();
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            // Accumulate comments/markdown before R-style blocks
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("%")) {
                // Check for metadata comment that should attach to next R-style block
                if (isMetadataComment(trimmed)) {
                    pendingComments.add(line);
                } else {
                    // Flush pending comments before non-metadata content
                    flushPendingComments(result, pendingComments);
                    result.append(line).append("\n");
                }
                i++;
                continue;
            }

            // Check for R-style assignment start
            if (looksLikeRStyleStart(trimmed)) {
                // Collect the full R-style block
                StringBuilder blockText = new StringBuilder();
                int parenDepth = 0;
                boolean inBlock = false;

                while (i < lines.length) {
                    String blockLine = lines[i];
                    blockText.append(blockLine).append("\n");

                    for (int j = 0; j < blockLine.length(); j++) {
                        char c = blockLine.charAt(j);
                        if (c == '(') {
                            parenDepth++;
                            inBlock = true;
                        } else if (c == ')') {
                            parenDepth--;
                        }
                    }

                    i++;
                    if (inBlock && parenDepth == 0) {
                        break;
                    }
                }

                String block = blockText.toString();
                String normalizedBlock = normalizeRStyleBlock(block, pendingComments);
                
                if (normalizedBlock != null) {
                    // Output any non-metadata comments that preceded the block
                    for (String comment : pendingComments) {
                        if (!isMetadataComment(comment.trim())) {
                            result.append(comment).append("\n");
                        }
                    }
                    result.append(normalizedBlock);
                } else {
                    // Normalization failed, preserve original
                    flushPendingComments(result, pendingComments);
                    result.append(block);
                }
                pendingComments.clear();
            } else {
                // Non-R-style line: flush comments and pass through
                flushPendingComments(result, pendingComments);
                result.append(line).append("\n");
                i++;
            }
        }

        // Flush any trailing comments
        flushPendingComments(result, pendingComments);

        return result.toString();
    }

    private String mergeDuplicateInitialAssignments(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String[] lines = input.split("\n", -1);
        ArrayList<String> mergedLines = new ArrayList<String>();
        HashMap<String, EquationRowRef> firstRows = new HashMap<String, EquationRowRef>();

        boolean inEquationsBlock = false;
        String currentBlockName = "";
        int blockStartIndex = -1;
        int activeRowCount = 0;
        boolean removedDuplicateRowsInBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (!inEquationsBlock) {
                String directive = extractDirective(trimmed);
                if ("@equations".equals(directive) || "@parameters".equals(directive)) {
                    inEquationsBlock = true;
                    currentBlockName = extractBlockName(trimmed, directive);
                    blockStartIndex = mergedLines.size();
                    activeRowCount = 0;
                    removedDuplicateRowsInBlock = false;
                }
                mergedLines.add(line);
                continue;
            }

            if ("@end".equals(extractDirective(trimmed))) {
                if (activeRowCount == 0 && removedDuplicateRowsInBlock) {
                    while (mergedLines.size() > blockStartIndex) {
                        mergedLines.remove(mergedLines.size() - 1);
                    }
                } else {
                    mergedLines.add(line);
                }
                inEquationsBlock = false;
                currentBlockName = "";
                blockStartIndex = -1;
                activeRowCount = 0;
                removedDuplicateRowsInBlock = false;
                continue;
            }

            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("%")) {
                mergedLines.add(line);
                continue;
            }

            ParsedEquationRow row = parseEquationRow(line);
            if (row == null || row.outputName.isEmpty()) {
                mergedLines.add(line);
                continue;
            }

            EquationRowRef firstRow = firstRows.get(row.outputName);
            if (firstRow == null) {
                firstRows.put(row.outputName,
                    new EquationRowRef(mergedLines.size(), currentBlockName, row.hasInitialValue));
                mergedLines.add(line);
                activeRowCount++;
                continue;
            }

            String duplicateError = getDuplicateAssignmentError(row, firstRow, i + 1);
            if (duplicateError != null) {
                mergedLines.add(commentOutDuplicateLine(line, duplicateError));
                continue;
            }

            mergedLines.set(firstRow.lineIndex,
                appendInitialMetadata(mergedLines.get(firstRow.lineIndex), row.expression));
            firstRow.hasInitialValue = true;
            removedDuplicateRowsInBlock = true;
        }

        if (inEquationsBlock && activeRowCount == 0 && removedDuplicateRowsInBlock) {
            while (mergedLines.size() > blockStartIndex) {
                mergedLines.remove(mergedLines.size() - 1);
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < mergedLines.size(); i++) {
            if (i > 0) {
                result.append("\n");
            }
            result.append(mergedLines.get(i));
        }
        return result.toString();
    }

    private ParsedEquationRow parseEquationRow(String line) {
        if (line == null) {
            return null;
        }

        String working = line.trim();
        if (working.isEmpty() || working.startsWith("#") || working.startsWith("%")) {
            return null;
        }

        int commentIdx = working.indexOf('#');
        if (commentIdx > 0) {
            working = working.substring(0, commentIdx).trim();
        }
        if (working.isEmpty()) {
            return null;
        }

        String[] parts = null;
        if (working.contains("~")) {
            parts = working.split("~", 2);
        } else if (working.contains("=")) {
            parts = working.split("=", 2);
        }
        if (parts == null || parts.length != 2) {
            return null;
        }

        String leftPart = parts[0].trim();
        String rightPart = parts[1].trim();

        String exprText = rightPart;
        boolean hasInitialValue = false;
        int metaIdx = rightPart.indexOf(';');
        if (metaIdx >= 0) {
            exprText = rightPart.substring(0, metaIdx).trim();
            String metaText = rightPart.substring(metaIdx + 1).trim();
            String[] metaParts = metaText.split(";");
            for (int i = 0; i < metaParts.length; i++) {
                String token = metaParts[i].trim();
                int eq = token.indexOf('=');
                if (eq > 0) {
                    String key = token.substring(0, eq).trim().toLowerCase();
                    if ("initial".equals(key)) {
                        hasInitialValue = true;
                    }
                }
            }
        }

        String[] lhsAliasParts = splitDifferenceLeftAlias(leftPart);
        String[] nameParts = SFCRUtil.parseCombinedName(lhsAliasParts[0]);
        String outputName = SFCRUtil.normalizeVariableName(nameParts[0]);
        if (outputName.isEmpty()) {
            return null;
        }

        return new ParsedEquationRow(outputName, exprText, hasInitialValue);
    }

    private String[] splitDifferenceLeftAlias(String left) {
        if (left == null) {
            return new String[] { "", null };
        }

        String trimmed = left.trim();
        if (trimmed.isEmpty()) {
            return new String[] { "", null };
        }

        int minusIdx = trimmed.indexOf('-');
        if (minusIdx <= 0) {
            return new String[] { trimmed, null };
        }
        if (minusIdx + 1 < trimmed.length() && trimmed.charAt(minusIdx + 1) == '>') {
            return new String[] { trimmed, null };
        }
        if (trimmed.indexOf("-||-") >= 0) {
            return new String[] { trimmed, null };
        }

        String candidateName = trimmed.substring(0, minusIdx).trim();
        String candidateOffset = trimmed.substring(minusIdx + 1).trim();
        if (candidateOffset.isEmpty() || !SFCRUtil.isValidIdentifier(candidateName)) {
            return new String[] { trimmed, null };
        }

        return new String[] { candidateName, candidateOffset };
    }

    private boolean isSimpleNumericAssignment(String expression) {
        if (expression == null) {
            return false;
        }
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(trimmed);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getDuplicateAssignmentError(ParsedEquationRow row, EquationRowRef firstRow, int lineNumber) {
        if (!isSimpleNumericAssignment(row.expression)) {
            return "Duplicate variable '" + row.outputName + "' at line " + lineNumber
                + " must be a simple numeric assignment to become an initial value";
        }
        if (row.hasInitialValue) {
            return "Duplicate variable '" + row.outputName + "' at line " + lineNumber
                + " cannot itself define an initial value";
        }
        if (firstRow.hasInitialValue) {
            return "Duplicate variable '" + row.outputName + "' at line " + lineNumber
                + " conflicts with an earlier initial value in block '" + firstRow.blockName + "'";
        }
        return null;
    }

    private String commentOutDuplicateLine(String line, String message) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.endsWith(",")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return "  # " + trimmed + "  Exception caught: " + message;
    }

    private String appendInitialMetadata(String line, String initialValue) {
        String value = initialValue == null ? "" : initialValue.trim();
        int commentIdx = line.indexOf('#');
        String comment = "";
        String base = line;
        if (commentIdx >= 0) {
            comment = line.substring(commentIdx).trim();
            base = line.substring(0, commentIdx);
        }

        base = trimTrailingWhitespace(base);
        base = base + " ; initial=" + value;

        if (comment.isEmpty()) {
            return base;
        }
        return base + "  " + comment;
    }

    private String trimTrailingWhitespace(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private String extractDirective(String trimmed) {
        if (trimmed == null || trimmed.isEmpty() || !trimmed.startsWith("@")) {
            return null;
        }
        int end = trimmed.indexOf(' ');
        if (end < 0) {
            end = trimmed.length();
        }
        return trimmed.substring(0, end).toLowerCase();
    }

    private String extractBlockName(String trimmed, String directive) {
        if (trimmed == null || directive == null) {
            return "";
        }
        String remainder = trimmed.substring(directive.length()).trim();
        if (remainder.isEmpty()) {
            return "";
        }

        String[] parts = remainder.split("\\s+");
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith("x=") || part.startsWith("y=")) {
                break;
            }
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(part);
        }
        return name.toString();
    }

    private static class ParsedEquationRow {
        final String outputName;
        final String expression;
        final boolean hasInitialValue;

        ParsedEquationRow(String outputName, String expression, boolean hasInitialValue) {
            this.outputName = outputName;
            this.expression = expression;
            this.hasInitialValue = hasInitialValue;
        }
    }

    private static class EquationRowRef {
        final int lineIndex;
        final String blockName;
        boolean hasInitialValue;

        EquationRowRef(int lineIndex, String blockName, boolean hasInitialValue) {
            this.lineIndex = lineIndex;
            this.blockName = blockName;
            this.hasInitialValue = hasInitialValue;
        }
    }

    /**
     * Check if a line looks like an R-style assignment start.
     */
    private boolean looksLikeRStyleStart(String trimmed) {
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

    /**
     * Check if a comment is a metadata comment (# [ ... ]).
     */
    private boolean isMetadataComment(String trimmed) {
        if (!trimmed.startsWith("#")) {
            return false;
        }
        String inner = trimmed.substring(1).trim();
        return inner.startsWith("[") && inner.endsWith("]");
    }

    /**
     * Flush pending comments to output.
     */
    private void flushPendingComments(StringBuilder result, ArrayList<String> pendingComments) {
        for (String comment : pendingComments) {
            result.append(comment).append("\n");
        }
        pendingComments.clear();
    }

    /**
     * Normalize a single R-style block to block format.
     */
    private String normalizeRStyleBlock(String block, ArrayList<String> pendingComments) {
        // Extract metadata from pending comments
        SFCRParser.RStyleBlockMetadata metadata = extractMetadataFromComments(pendingComments);

        // Use static methods that don't require parser
        String[] normalizedLines;
        if (block.contains("sfcr_matrix")) {
            normalizedLines = rStyleService.normalizeMatrixBlockStatic(block, metadata);
        } else if (block.contains("sfcr_set")) {
            normalizedLines = rStyleService.normalizeEquationsBlockStatic(block, metadata);
        } else {
            return null;
        }

        if (normalizedLines == null || normalizedLines.length == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (String line : normalizedLines) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    /**
     * Extract metadata from pending comments (# [ x=N y=N type: T ]).
     */
    private SFCRParser.RStyleBlockMetadata extractMetadataFromComments(ArrayList<String> comments) {
        SFCRParser.RStyleBlockMetadata metadata = new SFCRParser.RStyleBlockMetadata();
        
        for (int i = comments.size() - 1; i >= 0; i--) {
            String comment = comments.get(i).trim();
            if (parseMetadataLine(comment, metadata)) {
                comments.remove(i);
            }
        }
        
        return metadata;
    }

    /**
     * Parse a metadata comment line into metadata object.
     */
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
            } else if (token.startsWith("y=")) {
                Integer parsed = parseIntSafe(token.substring(2));
                if (parsed != null) {
                    metadata.y = parsed.intValue();
                }
            } else if (token.startsWith("uid=")) {
                String value = token.substring(4).trim();
                if (!value.isEmpty()) {
                    metadata.uid = value;
                }
            } else if (token.equals("uid:") && i + 1 < tokens.length) {
                String value = tokens[++i].trim();
                if (!value.isEmpty()) {
                    metadata.uid = value;
                }
            } else if (token.startsWith("uid:")) {
                String value = token.substring(4).trim();
                if (!value.isEmpty()) {
                    metadata.uid = value;
                }
            } else if (token.startsWith("type=")) {
                String value = token.substring(5).trim();
                if (!value.isEmpty()) {
                    metadata.type = value;
                }
            } else if (token.equals("type:") && i + 1 < tokens.length) {
                String value = tokens[++i].trim();
                if (!value.isEmpty()) {
                    metadata.type = value;
                }
            } else if (token.startsWith("type:")) {
                String value = token.substring(5).trim();
                if (!value.isEmpty()) {
                    metadata.type = value;
                }
            } else if (token.startsWith("invisible=")) {
                metadata.invisible = parseBooleanSafe(token.substring(10));
            } else if (token.equals("invisible:") && i + 1 < tokens.length) {
                metadata.invisible = parseBooleanSafe(tokens[++i]);
            } else if (token.startsWith("invisible:")) {
                metadata.invisible = parseBooleanSafe(token.substring(10));
            } else if (token.startsWith("hidden=")) {
                metadata.invisible = parseBooleanSafe(token.substring(7));
            } else if (token.equals("hidden:") && i + 1 < tokens.length) {
                metadata.invisible = parseBooleanSafe(tokens[++i]);
            } else if (token.startsWith("hidden:")) {
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
}
