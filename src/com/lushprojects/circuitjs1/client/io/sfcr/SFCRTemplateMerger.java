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

    private SFCRTemplateMerger() {}

    // =========================================================================
    // Template block type (private to this class)
    // =========================================================================

    enum TemplateBlockType {
        LOOKUP,
        EQUATIONS,
        MATRIX,
        SANKEY,
        PLANTUML,
        SCOPE,
        CIRCUIT,
        OTHER
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
        ArrayList<String> equationBlocks = buildCanonicalEquationBlocks(ctx);
        ArrayList<String> lookupBlocks = buildCanonicalLookupBlocks(ctx);
        ArrayList<String> matrixBlocks = buildCanonicalMatrixBlocks(ctx);
        ArrayList<String> sankeyBlocks = buildCanonicalSankeyBlocks(ctx);
        ArrayList<String> plantUmlBlocks = buildCanonicalPlantUmlBlocks(ctx);
        ArrayList<String> scopeBlocks = buildCanonicalScopeBlocks(ctx);
        String circuitBlock = extractStructuralPayload(ctx.exportCircuitElements());

        int lookupIndex = 0;
        int eqIndex = 0;
        int matrixIndex = 0;
        int sankeyIndex = 0;
        int plantUmlIndex = 0;
        int scopeIndex = 0;

        StringBuilder out = new StringBuilder();
        String[] lines = sourceText.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                String fenceHeader = line;
                StringBuilder fenced = new StringBuilder();
                fenced.append(line).append("\n");
                int end = i;
                for (int j = i + 1; j < lines.length; j++) {
                    fenced.append(lines[j]).append("\n");
                    end = j;
                    if (lines[j].trim().startsWith("```")) {
                        break;
                    }
                }

                TemplateBlockType type = detectTemplateBlockType(fenced.toString());
                String replacement = null;
                if (type == TemplateBlockType.LOOKUP && lookupIndex < lookupBlocks.size()) {
                    replacement = lookupBlocks.get(lookupIndex++);
                } else if (type == TemplateBlockType.EQUATIONS && eqIndex < equationBlocks.size()) {
                    replacement = equationBlocks.get(eqIndex++);
                } else if (type == TemplateBlockType.MATRIX && matrixIndex < matrixBlocks.size()) {
                    replacement = matrixBlocks.get(matrixIndex++);
                } else if (type == TemplateBlockType.SANKEY && sankeyIndex < sankeyBlocks.size()) {
                    replacement = sankeyBlocks.get(sankeyIndex++);
                } else if (type == TemplateBlockType.PLANTUML && plantUmlIndex < plantUmlBlocks.size()) {
                    replacement = plantUmlBlocks.get(plantUmlIndex++);
                } else if (type == TemplateBlockType.SCOPE && scopeIndex < scopeBlocks.size()) {
                    replacement = scopeBlocks.get(scopeIndex++);
                } else if (type == TemplateBlockType.CIRCUIT) {
                    replacement = "";
                }

                if (replacement == null) {
                    out.append(fenced.toString());
                } else if (!replacement.isEmpty()) {
                    out.append(wrapReplacementWithFenceLike(fenceHeader, replacement)).append("\n\n");
                }

                i = end;
                continue;
            }

            if (trimmed.startsWith("@lookup") || trimmed.startsWith("@equations") || trimmed.startsWith("@parameters") ||
                trimmed.startsWith("@matrix") || trimmed.startsWith("@sankey") || trimmed.startsWith("@plantuml") ||
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
                String replacement = null;
                if (type == TemplateBlockType.LOOKUP && lookupIndex < lookupBlocks.size()) {
                    replacement = lookupBlocks.get(lookupIndex++);
                } else if (type == TemplateBlockType.EQUATIONS && eqIndex < equationBlocks.size()) {
                    replacement = equationBlocks.get(eqIndex++);
                } else if (type == TemplateBlockType.MATRIX && matrixIndex < matrixBlocks.size()) {
                    replacement = matrixBlocks.get(matrixIndex++);
                } else if (type == TemplateBlockType.SANKEY && sankeyIndex < sankeyBlocks.size()) {
                    replacement = sankeyBlocks.get(sankeyIndex++);
                } else if (type == TemplateBlockType.PLANTUML && plantUmlIndex < plantUmlBlocks.size()) {
                    replacement = plantUmlBlocks.get(plantUmlIndex++);
                } else if (type == TemplateBlockType.SCOPE && scopeIndex < scopeBlocks.size()) {
                    replacement = scopeBlocks.get(scopeIndex++);
                } else if (type == TemplateBlockType.CIRCUIT) {
                    replacement = "";
                }

                if (replacement == null) {
                    out.append(rawBlock.toString());
                } else if (!replacement.isEmpty()) {
                    out.append("```{r}\n");
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
                String replacement = null;
                if (type == TemplateBlockType.LOOKUP && lookupIndex < lookupBlocks.size()) {
                    replacement = lookupBlocks.get(lookupIndex++);
                } else if (type == TemplateBlockType.EQUATIONS && eqIndex < equationBlocks.size()) {
                    replacement = equationBlocks.get(eqIndex++);
                } else if (type == TemplateBlockType.MATRIX && matrixIndex < matrixBlocks.size()) {
                    replacement = matrixBlocks.get(matrixIndex++);
                }

                if (replacement == null) {
                    out.append(rStyle.toString());
                } else if (!replacement.isEmpty()) {
                    out.append("```{r}\n");
                    out.append(replacement).append("\n");
                    out.append("```\n\n");
                }

                i = end;
                continue;
            }

            out.append(line).append("\n");
        }

        while (lookupIndex < lookupBlocks.size()) {
            out.append("\n```{r}\n").append(lookupBlocks.get(lookupIndex++)).append("\n```\n");
        }
        while (eqIndex < equationBlocks.size()) {
            out.append("\n```{r}\n").append(equationBlocks.get(eqIndex++)).append("\n```\n");
        }
        while (matrixIndex < matrixBlocks.size()) {
            out.append("\n```{r}\n").append(matrixBlocks.get(matrixIndex++)).append("\n```\n");
        }
        while (sankeyIndex < sankeyBlocks.size()) {
            out.append("\n```{r}\n").append(sankeyBlocks.get(sankeyIndex++)).append("\n```\n");
        }
        while (scopeIndex < scopeBlocks.size()) {
            out.append("\n```{r}\n").append(scopeBlocks.get(scopeIndex++)).append("\n```\n");
        }

        if (circuitBlock != null && !circuitBlock.trim().isEmpty()) {
            out.append("\n```{r}\n").append(circuitBlock).append("\n```\n");
        }

        return out.toString();
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

            if (trimmed.startsWith("```")) {
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

    static ArrayList<String> buildCanonicalLookupBlocks(SFCRExportContext ctx) {
        String lookupText = renderBlocksForType(SFCRBlockType.LOOKUP, ctx);
        return collectAtDirectiveBlocks(lookupText, "@lookup");
    }

    static ArrayList<String> buildCanonicalMatrixBlocks(SFCRExportContext ctx) {
        String matrixText = renderBlocksForType(SFCRBlockType.MATRIX, ctx);
        ArrayList<String> blocks = collectAtDirectiveBlocks(matrixText, "@matrix");
        blocks.addAll(collectRStyleBlocks(matrixText, "sfcr_matrix"));
        return blocks;
    }

    static ArrayList<String> buildCanonicalSankeyBlocks(SFCRExportContext ctx) {
        return collectAtDirectiveBlocks(renderBlocksForType(SFCRBlockType.SANKEY, ctx), "@sankey");
    }

    static ArrayList<String> buildCanonicalPlantUmlBlocks(SFCRExportContext ctx) {
        return collectPlantUmlBlocks(renderBlocksForType(SFCRBlockType.PLANTUML, ctx));
    }

    static ArrayList<String> buildCanonicalScopeBlocks(SFCRExportContext ctx) {
        return collectAtDirectiveBlocks(renderBlocksForType(SFCRBlockType.SCOPE, ctx), "@scope");
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
                if (lines[j].trim().equals("@end")) {
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
            if (!line.contains("<-") || !line.contains(fnName)) {
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

    static ArrayList<String> collectPlantUmlBlocks(String text) {
        ArrayList<String> blocks = new ArrayList<String>();
        if (text == null || text.trim().isEmpty()) {
            return blocks;
        }
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (isPlantUmlFenceHeader(trimmed)) {
                StringBuilder one = new StringBuilder();
                one.append(lines[i]).append("\n");
                int j = i + 1;
                for (; j < lines.length; j++) {
                    one.append(lines[j]).append("\n");
                    if (lines[j].trim().startsWith("```")) {
                        break;
                    }
                }
                String payload = extractStructuralPayload(one.toString());
                if (!payload.isEmpty()) {
                    blocks.add(payload);
                }
                i = j;
                continue;
            }
            if (!trimmed.startsWith("@plantuml") && !trimmed.startsWith("@startuml")) {
                continue;
            }
            StringBuilder one = new StringBuilder();
            for (int j = i; j < lines.length; j++) {
                one.append(lines[j]).append("\n");
                String endTrimmed = lines[j].trim();
                if ("@end".equals(endTrimmed) || "@enduml".equals(endTrimmed)) {
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
        if (!trimmed.contains("<-") || !trimmed.contains("sfcr_")) {
            return false;
        }
        return trimmed.contains("sfcr_set") || trimmed.contains("sfcr_matrix");
    }

    private static TemplateBlockType detectTemplateBlockType(String text) {
        if (text == null || text.isEmpty()) {
            return TemplateBlockType.OTHER;
        }
        String lower = text.toLowerCase();
        if (lower.contains("@circuit")) {
            return TemplateBlockType.CIRCUIT;
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
        if (lower.contains("@plantuml") || lower.contains("@startuml") || lower.contains("```{plantuml") || lower.startsWith("```plantuml")) {
            return TemplateBlockType.PLANTUML;
        }
        if (lower.contains("@scope")) {
            return TemplateBlockType.SCOPE;
        }
        return TemplateBlockType.OTHER;
    }

    private static String wrapReplacementWithFenceLike(String fenceHeader, String replacement) {
        String header = (fenceHeader == null || fenceHeader.trim().isEmpty()) ? "```{r}" : fenceHeader.trim();
        if (!header.startsWith("```")) {
            header = "```{r}";
        }
        if (isPlantUmlFenceHeader(header)) {
            return wrapPlantUmlReplacementWithFenceLike(header, replacement);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n");
        sb.append(replacement).append("\n");
        sb.append("```");
        return sb.toString();
    }

    private static boolean isPlantUmlFenceHeader(String fenceHeader) {
        if (fenceHeader == null) {
            return false;
        }
        String lower = fenceHeader.trim().toLowerCase();
        return lower.startsWith("```{plantuml") || lower.equals("```plantuml") || lower.startsWith("```plantuml ");
    }

    private static String wrapPlantUmlReplacementWithFenceLike(String fenceHeader, String replacement) {
        String rawSource = replacement == null ? "" : replacement.trim();
        if (rawSource.startsWith("@startuml")) {
            StringBuilder direct = new StringBuilder();
            direct.append("```{PlantUML}\n");
            direct.append(rawSource);
            if (!rawSource.endsWith("\n")) {
                direct.append("\n");
            }
            direct.append("```");
            return direct.toString();
        }
        String[] lines = rawSource.isEmpty() ? new String[0] : rawSource.split("\n");
        String headerLine = (lines.length > 0) ? lines[0].trim() : "";
        String xAttr = "";
        String yAttr = "";
        String widthAttr = "";
        String frameWidthAttr = "";
        String frameHeightAttr = "";
        String scaleAttr = "";

        if (headerLine.startsWith("@plantuml") || headerLine.startsWith("@startuml")) {
            String[] parts = headerLine.split("\\s+");
            for (int i = 1; i < parts.length; i++) {
                if (parts[i].startsWith("x=")) {
                    xAttr = " " + parts[i];
                } else if (parts[i].startsWith("y=")) {
                    yAttr = " " + parts[i];
                } else if (parts[i].startsWith("w=")) {
                    frameWidthAttr = " " + parts[i];
                } else if (parts[i].startsWith("h=")) {
                    frameHeightAttr = " " + parts[i];
                } else if (parts[i].startsWith("width=")) {
                    widthAttr = " " + parts[i];
                } else if (parts[i].startsWith("frameWidth=")) {
                    frameWidthAttr = " " + parts[i];
                } else if (parts[i].startsWith("frameHeight=")) {
                    frameHeightAttr = " " + parts[i];
                } else if (parts[i].startsWith("scale=")) {
                    scaleAttr = " " + parts[i];
                }
            }
        }

        int startBody = 0;
        int endBody = lines.length;
        if (lines.length > 0 && (lines[0].trim().startsWith("@plantuml") || lines[0].trim().startsWith("@startuml"))) {
            startBody = 1;
        }
        if (endBody > startBody && (lines[endBody - 1].trim().equals("@end") || lines[endBody - 1].trim().equals("@enduml"))) {
            endBody--;
        }
        while (endBody > startBody) {
            String trimmed = lines[startBody].trim();
            if (trimmed.startsWith("width:")) {
                String widthValue = trimmed.substring("width:".length()).trim();
                if (!widthValue.isEmpty()) {
                    widthAttr = " width=" + widthValue;
                }
                startBody++;
                continue;
            }
            if (trimmed.startsWith("frameWidth:")) {
                String frameWidthValue = trimmed.substring("frameWidth:".length()).trim();
                if (!frameWidthValue.isEmpty()) {
                    frameWidthAttr = " frameWidth=" + frameWidthValue;
                }
                startBody++;
                continue;
            }
            if (trimmed.startsWith("frameHeight:")) {
                String frameHeightValue = trimmed.substring("frameHeight:".length()).trim();
                if (!frameHeightValue.isEmpty()) {
                    frameHeightAttr = " frameHeight=" + frameHeightValue;
                }
                startBody++;
                continue;
            }
            if (trimmed.startsWith("scale:")) {
                String scaleValue = trimmed.substring("scale:".length()).trim();
                if (!scaleValue.isEmpty()) {
                    scaleAttr = " scale=" + scaleValue;
                }
                startBody++;
                continue;
            }
            break;
        }

        if (startBody < endBody && lines[startBody].trim().startsWith("@startuml")) {
            StringBuilder startLine = new StringBuilder("@startuml");
            if (!xAttr.isEmpty()) {
                startLine.append(xAttr);
            }
            if (!yAttr.isEmpty()) {
                startLine.append(yAttr);
            }
            if (!widthAttr.isEmpty()) {
                startLine.append(widthAttr);
            }
            if (!frameWidthAttr.isEmpty()) {
                startLine.append(frameWidthAttr);
            }
            if (!frameHeightAttr.isEmpty()) {
                startLine.append(frameHeightAttr);
            }
            if (!scaleAttr.isEmpty()) {
                startLine.append(scaleAttr);
            }
            lines[startBody] = startLine.toString();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("```{PlantUML}\n");
        for (int i = startBody; i < endBody; i++) {
            sb.append(lines[i]).append("\n");
        }
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
            if (t.matches("^[A-Za-z_][A-Za-z0-9_\\s-]*<-\\s*sfcr_(set|matrix)\\s*\\(.*$")) {
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
            || trimmed.startsWith("@plantuml")
            || trimmed.startsWith("@startuml")
            || trimmed.startsWith("@scope")
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
            lower.contains("@sankey") || lower.contains("@plantuml") || lower.contains("@startuml") || lower.contains("@scope") ||
            lower.contains("@info")) {
            return false;
        }

        return true;
    }

}
