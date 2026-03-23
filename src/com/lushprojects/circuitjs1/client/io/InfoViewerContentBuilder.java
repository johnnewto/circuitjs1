package com.lushprojects.circuitjs1.client.io;

public final class InfoViewerContentBuilder {
    private InfoViewerContentBuilder() {
    }

    public static String buildModelInfoMarkdown(String sfcrText, String infoBlockMarkdown) {
        String inlineMarkdown = extractInlineMarkdown(sfcrText);
        String infoMarkdown = normalize(infoBlockMarkdown);

        if (inlineMarkdown.isEmpty()) {
            return infoMarkdown;
        }
        if (infoMarkdown.isEmpty()) {
            return inlineMarkdown;
        }
        if (inlineMarkdown.equals(infoMarkdown)) {
            return infoMarkdown;
        }
        return inlineMarkdown + "\n\n---\n\n" + infoMarkdown;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private static String extractInlineMarkdown(String sfcrText) {
        if (sfcrText == null || sfcrText.trim().isEmpty()) {
            return "";
        }

        String[] lines = sfcrText.split("\n");
        StringBuilder markdown = new StringBuilder();
        boolean seenDirective = false;
        boolean inSfcrBlock = false;
        boolean inFence = false;
        StringBuilder fenceBuffer = null;
        boolean fenceHasViewerConstruct = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (!inFence) {
                    inFence = true;
                    fenceBuffer = new StringBuilder();
                    fenceBuffer.append(line).append("\n");
                    fenceHasViewerConstruct = false;
                    continue;
                }

                if (fenceBuffer != null) {
                    fenceBuffer.append(line).append("\n");
                }
                if (fenceHasViewerConstruct && !inSfcrBlock && seenDirective && fenceBuffer != null) {
                    markdown.append(fenceBuffer.toString());
                }
                inFence = false;
                fenceBuffer = null;
                fenceHasViewerConstruct = false;
                continue;
            }

            if (inFence) {
                if (fenceBuffer != null) {
                    fenceBuffer.append(line).append("\n");
                }
                if (isSfcrBlockHeader(trimmed) || startsUnfencedSfcrConstruct(trimmed)) {
                    seenDirective = true;
                }
                if (isViewerConstructLine(trimmed)) {
                    fenceHasViewerConstruct = true;
                }
                continue;
            }

            if (startsUnfencedSankeyBlock(trimmed)) {
                StringBuilder block = new StringBuilder();
                int endIndex = i;
                for (int j = i; j < lines.length; j++) {
                    String blockLine = lines[j];
                    String blockTrimmed = blockLine.trim();
                    block.append(blockLine).append("\n");
                    endIndex = j;
                    if ("@end".equals(blockTrimmed)) {
                        break;
                    }
                }

                markdown.append("```{circuit}\n");
                markdown.append(block.toString());
                markdown.append("```\n\n");
                seenDirective = true;
                i = endIndex;
                continue;
            }

            if (startsUnfencedScopeBlock(trimmed)) {
                StringBuilder block = new StringBuilder();
                int endIndex = i;
                for (int j = i; j < lines.length; j++) {
                    String blockLine = lines[j];
                    String blockTrimmed = blockLine.trim();
                    block.append(blockLine).append("\n");
                    endIndex = j;
                    if ("@end".equals(blockTrimmed)) {
                        break;
                    }
                }

                markdown.append("```{circuit}\n");
                markdown.append(block.toString());
                markdown.append("```\n\n");
                seenDirective = true;
                i = endIndex;
                continue;
            }

            if (startsUnfencedPlotBlock(trimmed)) {
                StringBuilder block = new StringBuilder();
                int endIndex = i;
                for (int j = i; j < lines.length; j++) {
                    String blockLine = lines[j];
                    String blockTrimmed = blockLine.trim();
                    block.append(blockLine).append("\n");
                    endIndex = j;
                    if ("@end".equals(blockTrimmed)) {
                        break;
                    }
                }

                markdown.append("```{circuit}\n");
                markdown.append(block.toString());
                markdown.append("```\n\n");
                seenDirective = true;
                i = endIndex;
                continue;
            }

            if (trimmed.startsWith("@")) {
                seenDirective = true;
                if ("@end".equals(trimmed)) {
                    inSfcrBlock = false;
                } else if (isSfcrBlockHeader(trimmed)) {
                    inSfcrBlock = true;
                }
                continue;
            }
            if (inSfcrBlock) {
                continue;
            }
            if (!seenDirective) {
                continue;
            }

            if (trimmed.startsWith("%")) {
                continue;
            }
            if (trimmed.startsWith("# [") && trimmed.endsWith("]")) {
                continue;
            }

            if (startsUnfencedSfcrConstruct(trimmed)) {
                StringBuilder block = new StringBuilder();
                int parenDepth = 0;
                boolean hasParen = false;
                int endIndex = i;

                for (int j = i; j < lines.length; j++) {
                    String blockLine = lines[j];
                    String blockTrimmed = blockLine.trim();

                    if (j > i && blockTrimmed.startsWith("@") && !"@end".equals(blockTrimmed)) {
                        endIndex = j - 1;
                        break;
                    }

                    block.append(blockLine).append("\n");

                    int delta = SFCRUtil.parenthesesDelta(blockLine);
                    if (delta != 0 || blockLine.indexOf('(') >= 0) {
                        hasParen = true;
                    }
                    parenDepth += delta;
                    endIndex = j;

                    if (hasParen && parenDepth <= 0) {
                        break;
                    }
                }

                markdown.append("```{r}\n");
                markdown.append(block.toString());
                markdown.append("```\n\n");
                i = endIndex;
                continue;
            }

            markdown.append(line).append("\n");
        }

        return markdown.toString().trim();
    }

    private static boolean isSfcrBlockHeader(String trimmedLine) {
        return trimmedLine.startsWith("@init")
            || trimmedLine.startsWith("@action")
            || trimmedLine.startsWith("@matrix")
            || trimmedLine.startsWith("@equations")
            || trimmedLine.startsWith("@parameters")
            || trimmedLine.startsWith("@hints")
            || trimmedLine.startsWith("@scope")
            || trimmedLine.startsWith("@circuit")
            || trimmedLine.startsWith("@sankey")
            || trimmedLine.startsWith("@info");
    }

    private static boolean startsUnfencedSfcrConstruct(String trimmedLine) {
        if (trimmedLine == null || trimmedLine.isEmpty()) {
            return false;
        }
        if (trimmedLine.startsWith("@") || trimmedLine.startsWith("#") || trimmedLine.startsWith("%")) {
            return false;
        }
        if (!trimmedLine.contains("<-")) {
            return false;
        }
        return trimmedLine.contains("sfcr_set") || trimmedLine.contains("sfcr_matrix");
    }

    private static boolean startsUnfencedSankeyBlock(String trimmedLine) {
        if (trimmedLine == null || trimmedLine.isEmpty()) {
            return false;
        }
        return trimmedLine.startsWith("@sankey");
    }

    private static boolean startsUnfencedScopeBlock(String trimmedLine) {
        if (trimmedLine == null || trimmedLine.isEmpty()) {
            return false;
        }
        return trimmedLine.startsWith("@scope");
    }

    private static boolean startsUnfencedPlotBlock(String trimmedLine) {
        if (trimmedLine == null || trimmedLine.isEmpty()) {
            return false;
        }
        return trimmedLine.startsWith("@plot");
    }

    private static boolean isViewerConstructLine(String trimmedLine) {
        if (trimmedLine == null || trimmedLine.isEmpty()) {
            return false;
        }
        return trimmedLine.contains("sfcr_set")
            || trimmedLine.contains("sfcr_matrix")
            || trimmedLine.startsWith("plot:")
            || trimmedLine.startsWith("vars:")
            || trimmedLine.startsWith("@sankey")
            || trimmedLine.startsWith("@scope")
            || trimmedLine.startsWith("@plot");
    }

}
