/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.io.sfcr;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.io.SFCRUtil;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.SFCRBlockExportHandler;

import java.util.ArrayList;

/**
 * Handles template-merge export and inline-documentation export for SFCR.
 *
 * Replaces structural blocks in an existing model-info template with
 * freshly-computed data from the circuit, leaving narrative prose untouched.
 */
public final class SFCRTemplateMerger {

    // =========================================================================
    // Constants
    // =========================================================================

    private static final String FENCE_START = "```";
    private static final String R_FENCE = "```{r}";

    private SFCRTemplateMerger() {}

    // =========================================================================
    // Template block type
    // =========================================================================

    enum TemplateBlockType {
        INIT, ACTION, LOOKUP, EQUATIONS, MATRIX, SANKEY, PLANTUML, SCOPE, ZORDER, CIRCUIT, OTHER
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Merge fresh circuit data into {@code sourceText}, replacing structural blocks
     * (@equations, @lookup, @matrix, @sankey, @scope, @circuit / fenced equivalents)
     * with up-to-date content from {@code ctx}.
     *
     * <p>The context must already have elements categorized and lookup state seeded
     * before this method is called.
     */
    public static String export(String sourceText, SFCRExportContext ctx) {
        BlockReplacer replacer = new BlockReplacer(ctx);
        StringBuilder out = new StringBuilder();
        String[] lines = sourceText.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith(FENCE_START)) {
                String fenceHeader = line;
                StringBuilder fenced = new StringBuilder();
                fenced.append(line).append("\n");
                int end = i;
                for (int j = i + 1; j < lines.length; j++) {
                    fenced.append(lines[j]).append("\n");
                    end = j;
                    if (lines[j].trim().startsWith(FENCE_START)) {
                        break;
                    }
                }

                TemplateBlockType type = detectTemplateBlockType(fenced.toString());
                String replacement = replacer.nextReplacement(type);

                if (replacement == null) {
                    out.append(fenced.toString());
                } else if (!replacement.isEmpty()) {
                    out.append(wrapReplacementWithFenceLike(fenceHeader, replacement)).append("\n\n");
                }

                i = end;
                continue;
            }

            if (trimmed.startsWith("@init") || trimmed.startsWith("@action") || trimmed.startsWith("@lookup") || trimmed.startsWith("@equations") || trimmed.startsWith("@parameters") ||
                trimmed.startsWith("@matrix") || trimmed.startsWith("@sankey") || trimmed.startsWith("@startuml") ||
                trimmed.startsWith("@scope") || trimmed.startsWith("@circuit")) {

                StringBuilder rawBlock = new StringBuilder();
                int end = i;
                for (int j = i; j < lines.length; j++) {
                    rawBlock.append(lines[j]).append("\n");
                    end = j;
                    if (lines[j].trim().equals("@end")) {
                        break;
                    }
                }

                TemplateBlockType type = detectTemplateBlockType(rawBlock.toString());
                String replacement = replacer.nextReplacement(type);

                if (replacement == null) {
                    out.append(rawBlock.toString());
                } else if (!replacement.isEmpty()) {
                    out.append(R_FENCE).append("\n");
                    out.append(replacement).append("\n");
                    out.append("```\n\n");
                }

                i = end;
                continue;
            }

            if (looksLikeRStyleAssignmentStart(trimmed)) {
                StringBuilder rStyle = new StringBuilder();
                int end = i;
                int parenDepth = 0;
                boolean hasParen = false;
                for (int j = i; j < lines.length; j++) {
                    String rLine = lines[j];
                    rStyle.append(rLine).append("\n");
                    end = j;
                    int delta = SFCRUtil.parenthesesDelta(rLine);
                    if (delta != 0 || rLine.indexOf('(') >= 0) {
                        hasParen = true;
                    }
                    parenDepth += delta;
                    if (hasParen && parenDepth <= 0) {
                        break;
                    }
                }

                TemplateBlockType type = detectTemplateBlockType(rStyle.toString());
                String replacement = replacer.nextReplacement(type);

                if (replacement == null) {
                    out.append(rStyle.toString());
                } else if (!replacement.isEmpty()) {
                    out.append(R_FENCE).append("\n");
                    out.append(replacement).append("\n");
                    out.append("```\n\n");
                }

                i = end;
                continue;
            }

            out.append(line).append("\n");
        }

        replacer.appendRemaining(out);
        return out.toString();
    }

    /** Helper class to manage block replacement state during export. */
    private static class BlockReplacer {
        private final ArrayList<String> equationBlocks;
        private final ArrayList<String> actionBlocks;
        private final ArrayList<String> lookupBlocks;
        private final ArrayList<String> matrixBlocks;
        private final ArrayList<String> sankeyBlocks;
        private final ArrayList<String> plantUmlBlocks;
        private final ArrayList<String> scopeBlocks;
        private final String initBlock;
        private final String circuitBlock;

        private boolean initConsumed = false;
        private int actionIndex = 0;
        private int eqIndex = 0;
        private int lookupIndex = 0;
        private int matrixIndex = 0;
        private int sankeyIndex = 0;
        private int plantUmlIndex = 0;
        private int scopeIndex = 0;
        private int zOrderIndex = 0;

        BlockReplacer(SFCRExportContext ctx) {
            initBlock = extractStructuralPayload(renderBlocksForType(SFCRBlockType.INIT, ctx));
            actionBlocks = buildCanonicalActionBlocks(ctx);
            equationBlocks = buildCanonicalEquationBlocks(ctx);
            lookupBlocks = buildCanonicalLookupBlocks(ctx);
            matrixBlocks = buildCanonicalMatrixBlocks(ctx);
            sankeyBlocks = buildCanonicalSankeyBlocks(ctx);
            plantUmlBlocks = buildCanonicalPlantUmlBlocks(ctx);
            scopeBlocks = buildCanonicalScopeBlocks(ctx);
            zOrderBlocks = buildCanonicalZOrderBlocks(ctx);
            circuitBlock = extractStructuralPayload(ctx.exportCircuitElements());
        }

        private final ArrayList<String> zOrderBlocks;

        /** Returns next replacement for the given type, or null if none available. */
        String nextReplacement(TemplateBlockType type) {
            switch (type) {
                case INIT:
                    if (initConsumed) {
                        return "";
                    }
                    initConsumed = true;
                    return initBlock;
                case ACTION:
                    return actionIndex < actionBlocks.size() ? actionBlocks.get(actionIndex++) : null;
                case LOOKUP:
                    return lookupIndex < lookupBlocks.size() ? lookupBlocks.get(lookupIndex++) : null;
                case EQUATIONS:
                    return eqIndex < equationBlocks.size() ? equationBlocks.get(eqIndex++) : null;
                case MATRIX:
                    return matrixIndex < matrixBlocks.size() ? matrixBlocks.get(matrixIndex++) : null;
                case SANKEY:
                    return sankeyIndex < sankeyBlocks.size() ? sankeyBlocks.get(sankeyIndex++) : null;
                case PLANTUML:
                    return plantUmlIndex < plantUmlBlocks.size() ? plantUmlBlocks.get(plantUmlIndex++) : null;
                case SCOPE:
                    return scopeIndex < scopeBlocks.size() ? scopeBlocks.get(scopeIndex++) : null;
                case ZORDER:
                    return zOrderIndex < zOrderBlocks.size() ? zOrderBlocks.get(zOrderIndex++) : null;
                case CIRCUIT:
                    return "";
                default:
                    return null;
            }
        }

        /** Append any remaining unused blocks to the output. */
        void appendRemaining(StringBuilder out) {
            if (!initConsumed && initBlock != null && !initBlock.trim().isEmpty()) {
                out.append("\n").append(initBlock).append("\n");
                initConsumed = true;
            }
            appendRemainingFromList(out, actionBlocks, actionIndex);
            appendRemainingFromList(out, lookupBlocks, lookupIndex);
            appendRemainingFromList(out, equationBlocks, eqIndex);
            appendRemainingFromList(out, matrixBlocks, matrixIndex);
            appendRemainingFromList(out, sankeyBlocks, sankeyIndex);
            appendRemainingFromList(out, scopeBlocks, scopeIndex);
            appendRemainingFromList(out, zOrderBlocks, zOrderIndex);

            if (circuitBlock != null && !circuitBlock.trim().isEmpty()) {
                out.append("\n").append(R_FENCE).append("\n").append(circuitBlock).append("\n```\n");
            }
        }

        private void appendRemainingFromList(StringBuilder out, ArrayList<String> blocks, int index) {
            while (index < blocks.size()) {
                out.append("\n").append(R_FENCE).append("\n").append(blocks.get(index++)).append("\n```\n");
            }
        }
    }

    /** Collapse excessive blank lines outside fenced code blocks. */
    public static String normalizeBlankLinesOutsideFences(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String[] lines = text.split("\\n", -1);
        StringBuilder out = new StringBuilder();
        boolean inFence = false;
        int blankRun = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith(FENCE_START)) {
                inFence = !inFence;
                blankRun = 0;
                out.append(line).append("\n");
                continue;
            }

            if (!inFence && trimmed.isEmpty()) {
                blankRun++;
                if (blankRun > 1) {
                    continue;
                }
                out.append("\n");
                continue;
            }

            blankRun = 0;
            out.append(line).append("\n");
        }

        String normalized = out.toString();
        while (normalized.endsWith("\n\n\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * Export model documentation as inline markdown (no @info wrapper).
     * Structural SFCR blocks are stripped to avoid duplication.
     */
    public static String exportInlineDocumentation(CirSim sim) {
        if (sim == null) {
            return "";
        }
        String modelInfoContent = sim.getSFCRDocumentManager().getModelInfoContent();
        if (modelInfoContent == null || modelInfoContent.isEmpty()) {
            return "";
        }

        String sanitized = sanitizeInlineDocumentation(modelInfoContent);
        if (sanitized.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(sanitized);
        if (!sanitized.endsWith("\n")) {
            sb.append("\n");
        }
        return sb.toString();
    }

    // =========================================================================
    // Block builders
    // =========================================================================

    static ArrayList<String> buildCanonicalEquationBlocks(SFCRExportContext ctx) {
        ArrayList<String> blocks = new ArrayList<String>();
        for (int i = 0; i < ctx.getEquationTables().size(); i++) {
            String block = ctx.exportEquationTable(ctx.getEquationTables().get(i));
            String payload = extractStructuralPayload(block);
            if (!payload.isEmpty()) {
                blocks.add(payload);
            }
        }
        for (int i = 0; i < ctx.getGodlyTables().size(); i++) {
            String block = ctx.exportGodlyTable(ctx.getGodlyTables().get(i));
            String payload = extractStructuralPayload(block);
            if (!payload.isEmpty()) {
                blocks.add(payload);
            }
        }
        return blocks;
    }

    /** Helper to collect directive blocks for a given block type. */
    private static ArrayList<String> collectBlocksForType(SFCRBlockType blockType, String directive, SFCRExportContext ctx) {
        return collectAtDirectiveBlocks(renderBlocksForType(blockType, ctx), directive);
    }

    static ArrayList<String> buildCanonicalLookupBlocks(SFCRExportContext ctx) {
        return collectBlocksForType(SFCRBlockType.LOOKUP, "@lookup", ctx);
    }

    static ArrayList<String> buildCanonicalActionBlocks(SFCRExportContext ctx) {
        return collectBlocksForType(SFCRBlockType.ACTION, "@action", ctx);
    }

    static ArrayList<String> buildCanonicalMatrixBlocks(SFCRExportContext ctx) {
        String matrixText = renderBlocksForType(SFCRBlockType.MATRIX, ctx);
        ArrayList<String> blocks = collectAtDirectiveBlocks(matrixText, "@matrix");
        blocks.addAll(collectRStyleBlocks(matrixText, "sfcr_matrix"));
        return blocks;
    }

    static ArrayList<String> buildCanonicalSankeyBlocks(SFCRExportContext ctx) {
        return collectBlocksForType(SFCRBlockType.SANKEY, "@sankey", ctx);
    }

    static ArrayList<String> buildCanonicalPlantUmlBlocks(SFCRExportContext ctx) {
        return collectBlocksForType(SFCRBlockType.PLANTUML, "@startuml", ctx);
    }

    static ArrayList<String> buildCanonicalScopeBlocks(SFCRExportContext ctx) {
        return collectBlocksForType(SFCRBlockType.SCOPE, "@scope", ctx);
    }

    static ArrayList<String> buildCanonicalZOrderBlocks(SFCRExportContext ctx) {
        return collectBlocksForType(SFCRBlockType.ZORDER, "@zorder", ctx);
    }

    /** Render all handler output for a given block type. */
    public static String renderBlocksForType(SFCRBlockType blockType, SFCRExportContext context) {
        if (blockType == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (SFCRBlockExportHandler handler : SFCRBlockExportHandlerRegistry.getOrderedHandlers()) {
            if (handler.blockType() != blockType) {
                continue;
            }
            String block = handler.export(context);
            if (block == null || block.trim().isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append("\n");
            }
            out.append(block.trim()).append("\n");
        }
        return out.toString();
    }

    // =========================================================================
    // Collection helpers
    // =========================================================================

    static ArrayList<String> collectAtDirectiveBlocks(String text, String directive) {
        ArrayList<String> blocks = new ArrayList<String>();
        if (text == null || text.trim().isEmpty() || directive == null || directive.isEmpty()) {
            return blocks;
        }
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!trimmed.startsWith(directive)) {
                continue;
            }
            StringBuilder one = new StringBuilder();
            for (int j = i; j < lines.length; j++) {
                one.append(lines[j]).append("\n");
                String endTrimmed = lines[j].trim();
                if (endTrimmed.equals("@end") || endTrimmed.equals("@enduml")) {
                    i = j;
                    break;
                }
            }
            String payload = extractStructuralPayload(one.toString());
            if (!payload.isEmpty()) {
                blocks.add(payload);
            }
        }
        return blocks;
    }

    static ArrayList<String> collectRStyleBlocks(String text, String fnName) {
        ArrayList<String> blocks = new ArrayList<String>();
        if (text == null || text.trim().isEmpty() || fnName == null || fnName.isEmpty()) {
            return blocks;
        }
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!looksLikeRStyleAssignmentStart(line) || !line.contains(fnName)) {
                continue;
            }
            StringBuilder one = new StringBuilder();
            int parenDepth = 0;
            boolean inExpr = false;
            int end = i;
            for (int j = i; j < lines.length; j++) {
                String row = lines[j];
                one.append(row).append("\n");
                end = j;
                int delta = SFCRUtil.parenthesesDelta(row);
                if (delta != 0 || row.indexOf('(') >= 0) {
                    inExpr = true;
                }
                parenDepth += delta;
                if (inExpr && parenDepth <= 0) {
                    break;
                }
            }
            String payload = extractStructuralPayload(one.toString());
            if (!payload.isEmpty()) {
                blocks.add(payload);
            }
            i = end;
        }
        return blocks;
    }

    static String extractStructuralPayload(String block) {
        if (block == null) {
            return "";
        }
        String trimmed = block.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] lines = trimmed.split("\n");
        int start = findStructuralStartLine(lines);
        StringBuilder payload = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            payload.append(lines[i]).append("\n");
        }
        return payload.toString().trim();
    }

    // =========================================================================
    // Detection helpers
    // =========================================================================

    private static boolean looksLikeRStyleAssignmentStart(String trimmed) {
        if (trimmed == null || trimmed.isEmpty()) {
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

    static TemplateBlockType detectTemplateBlockType(String text) {
        if (text == null || text.isEmpty()) {
            return TemplateBlockType.OTHER;
        }
        String lower = text.toLowerCase();
        if (lower.contains("@circuit")) {
            return TemplateBlockType.CIRCUIT;
        }
        if (lower.contains("@init")) {
            return TemplateBlockType.INIT;
        }
        if (lower.contains("@action")) {
            return TemplateBlockType.ACTION;
        }
        if (lower.contains("@lookup")) {
            return TemplateBlockType.LOOKUP;
        }
        if (lower.contains("@equations") || lower.contains("@parameters") || lower.contains("sfcr_set")) {
            return TemplateBlockType.EQUATIONS;
        }
        if (lower.contains("@matrix") || lower.contains("sfcr_matrix")) {
            return TemplateBlockType.MATRIX;
        }
        if (lower.contains("@sankey")) {
            return TemplateBlockType.SANKEY;
        }
        if (lower.contains("@startuml")) {
            return TemplateBlockType.PLANTUML;
        }
        if (lower.contains("@scope")) {
            return TemplateBlockType.SCOPE;
        }
        if (lower.contains("@zorder")) {
            return TemplateBlockType.ZORDER;
        }
        return TemplateBlockType.OTHER;
    }

    private static String wrapReplacementWithFenceLike(String fenceHeader, String replacement) {
        String header = (fenceHeader == null || fenceHeader.trim().isEmpty()) ? R_FENCE : fenceHeader.trim();
        if (!header.startsWith(FENCE_START)) {
            header = R_FENCE;
        }
        if (replacement != null && replacement.contains("@startuml")) {
            header = R_FENCE;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n");
        sb.append(replacement).append("\n");
        sb.append("```");
        return sb.toString();
    }

    private static int findStructuralStartLine(String[] lines) {
        if (lines == null || lines.length == 0) {
            return 0;
        }
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i] == null ? "" : lines[i].trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("@")) {
                return i;
            }
            if (looksLikeRStyleAssignmentStart(t)) {
                return i;
            }
        }
        return 0;
    }

    // =========================================================================
    // Inline documentation helpers
    // =========================================================================

    /**
     * Keep narrative inline markdown and viewer plot directives; drop structural
     * SFCR definitions that are exported in native blocks to avoid duplication.
     */
    private static String sanitizeInlineDocumentation(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return "";
        }

        String[] lines = markdown.split("\n");
        StringBuilder out = new StringBuilder();
        boolean inFence = false;
        StringBuilder fenceBuffer = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (!inFence) {
                    inFence = true;
                    fenceBuffer.setLength(0);
                    fenceBuffer.append(line).append("\n");
                    continue;
                }

                fenceBuffer.append(line).append("\n");
                String block = fenceBuffer.toString();
                if (shouldKeepFencedInlineBlock(block)) {
                    out.append(block);
                }
                inFence = false;
                continue;
            }

            if (inFence) {
                fenceBuffer.append(line).append("\n");
                continue;
            }

            if (startsStructuralSfcrBlock(trimmed)) {
                int endIndex = i;
                for (int j = i; j < lines.length; j++) {
                    endIndex = j;
                    if ("@end".equals(lines[j].trim())) {
                        break;
                    }
                }
                i = endIndex;
                continue;
            }

            out.append(line).append("\n");
        }

        return out.toString().trim();
    }

    private static boolean startsStructuralSfcrBlock(String trimmed) {
        if (trimmed == null || trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.startsWith("@plot")) {
            return false;
        }
        return trimmed.startsWith("@equations")
            || trimmed.startsWith("@lookup")
            || trimmed.startsWith("@parameters")
            || trimmed.startsWith("@matrix")
            || trimmed.startsWith("@sankey")
            || trimmed.startsWith("@startuml")
            || trimmed.startsWith("@scope")
            || trimmed.startsWith("@zorder")
            || trimmed.startsWith("@circuit")
            || trimmed.startsWith("@info");
    }

    private static boolean shouldKeepFencedInlineBlock(String block) {
        if (block == null || block.isEmpty()) {
            return false;
        }
        String lower = block.toLowerCase();

        if (lower.contains("@plot") || lower.contains("plot:") || lower.contains("vars:")) {
            return true;
        }

        if (lower.contains("sfcr_set") || lower.contains("sfcr_matrix")) {
            return false;
        }
        if (lower.contains("@equations") || lower.contains("@parameters") ||
            lower.contains("@matrix") || lower.contains("@circuit") ||
            lower.contains("@sankey") || lower.contains("@startuml") || lower.contains("@scope") ||
            lower.contains("@zorder") ||
            lower.contains("@info")) {
            return false;
        }

        return true;
    }

}
