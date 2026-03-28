/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.io;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Normalizes SFCR input text by converting R-style syntax to block format.
 * 
 * This enables a single parsing path: R-style input is normalized to block
 * format before the main parser runs, eliminating duplicate code paths.
 * 
 * Conversions performed:
 * <ul>
 *   <li>{@code name <- sfcr_set(...)} → {@code @equations name ... @end}</li>
 *   <li>{@code name <- sfcr_matrix(...)} → {@code @matrix name ... @end}</li>
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

        // Fast path: no R-style content
        if (!containsRStyleContent(input)) {
            return input;
        }

        return normalizeRStyleBlocks(input);
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
                int startLine = i;

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

    /**
     * Check if a line looks like an R-style assignment start.
     */
    private boolean looksLikeRStyleStart(String trimmed) {
        return (trimmed.contains("<-") && 
                (trimmed.contains("sfcr_set") || trimmed.contains("sfcr_matrix")));
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
