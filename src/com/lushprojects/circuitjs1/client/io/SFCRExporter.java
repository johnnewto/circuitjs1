/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.io;

import com.lushprojects.circuitjs1.client.scope.Scope;

import com.lushprojects.circuitjs1.client.elements.economics.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.registry.HintRegistry;
import com.lushprojects.circuitjs1.client.util.*;
import com.lushprojects.circuitjs1.client.elements.misc.*;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockExportHandlerRegistry;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.SFCRBlockExportHandler;

/**
 * Exports circuit in SFCR-compatible text format.
 * 
 * Generates human-readable Stock-Flow Consistent model definitions compatible
 * with the R sfcr package (https://github.com/joaomacalos/sfcr).
 * 
 * Output blocks:
 *   @init       - Simulation settings (timestep, units)
 *   @action     - Action Time schedule (timed target updates)
 *   (inline markdown) - Model documentation (no @info wrapper)
 *   @equations  - All equations (from EquationTableElm, GodlyTableElm)
 *   @lookup     - Named lookup tables referenced by lookup(name, x[, clamp]) equations
 *   @matrix     - Transaction matrices (from SFCTableElm)
 *   @hints      - Variable documentation
 *   @circuit    - Non-SFCR elements (passthrough)
 *   @scope      - Docked and undocked scopes with trace references (UID-based)
 * 
 * @see SFCRParser
 * @see <a href="../dev_docs/SFCR_FORMAT_REFERENCE.md">SFCR Format Reference</a>
 */
public class SFCRExporter {

    public enum ExportSyntax {
        BLOCK_FORMAT,
        R_STYLE
    }
    
    // =========================================================================
    // Fields
    // =========================================================================
    
    private CirSim sim;
    private ExportSyntax exportSyntax;
    private ArrayList<EquationTableElm> equationTables = new ArrayList<EquationTableElm>();
    private ArrayList<SFCTableElm> sfcTables = new ArrayList<SFCTableElm>();
    private ArrayList<GodlyTableElm> godlyTables = new ArrayList<GodlyTableElm>();
    private ArrayList<SFCSankeyElm> sankeyDiagrams = new ArrayList<SFCSankeyElm>();
    private ArrayList<CircuitElm> otherElements = new ArrayList<CircuitElm>();
    private ActionTimeElm actionTimeElmForExport = null;
    private HashSet<CircuitElm> scopeElmsExportedAsBlocks = new HashSet<CircuitElm>();
    private ArrayList<LookupDefinition> lookupExportSpecs = new ArrayList<LookupDefinition>();
    private HashMap<String, LookupDefinition> lookupExportBySignature = new HashMap<String, LookupDefinition>();
    private HashMap<String, ArrayList<String>> lookupCommentsByNameScope = new HashMap<String, ArrayList<String>>();
    
    // =========================================================================
    // Constructor & Public API
    // =========================================================================
    
    /** Create a new SFCR exporter. */
    public SFCRExporter(CirSim sim) {
        this(sim, ExportSyntax.R_STYLE);
    }

    /** Create a new SFCR exporter with explicit syntax style. */
    public SFCRExporter(CirSim sim, ExportSyntax syntax) {
        this.sim = sim;
        this.exportSyntax = (syntax == null) ? ExportSyntax.R_STYLE : syntax;
    }
    
    /** Get the simulator instance. */
    public CirSim getSim() {
        return sim;
    }
    
    /** Get the export syntax style. */
    public ExportSyntax getExportSyntax() {
        return exportSyntax;
    }
    
    /** Export the current circuit in SFCR format. */
    public String export() {
        if (sim != null && sim.getSFCRDocumentManager().getModelInfoSourceText() != null
                && !sim.getSFCRDocumentManager().getModelInfoSourceText().trim().isEmpty()) {
            String merged = exportWithTemplateMerge(sim.getSFCRDocumentManager().getModelInfoSourceText());
            if (merged != null && !merged.trim().isEmpty()) {
                return normalizeBlankLinesOutsideFences(merged);
            }
        }

        StringBuilder sb = new StringBuilder();
        
        // Create export context
        SFCRExportContext exportContext = new SFCRExportContext(this, sim, exportSyntax);
        exportContext.clearScopeElmsExportedAsBlocks();
        
        // Categorize elements into the context
        categorizeElements(exportContext);
        
        // Reset lookup extraction state
        exportContext.resetLookupExportState();
        
        // Build header comment
        sb.append("# CircuitJS1 SFCR Export\n");
        sb.append("# Generated from circuit simulation\n");
        sb.append("\n");

        for (SFCRBlockExportHandler handler : SFCRBlockExportHandlerRegistry.getOrderedHandlers()) {
            exportContext.appendExportBlock(sb, handler.export(exportContext));
        }

        // Export model documentation last as inline markdown (no @info wrapper)
        String inlineDocs = exportInlineDocumentation();
        if (!inlineDocs.isEmpty()) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                sb.append("\n");
            }
            sb.append("\n");
            sb.append(inlineDocs);
        }
        
        return normalizeBlankLinesOutsideFences(sb.toString());
    }

    /** Internal accessor for lookup export specs (used by context transition). */
    public ArrayList<LookupDefinition> getLookupExportSpecsInternal() {
        return lookupExportSpecs;
    }
    
    /** Internal accessor for lookup export by signature (used by context transition). */
    public HashMap<String, LookupDefinition> getLookupExportBySignatureInternal() {
        return lookupExportBySignature;
    }
    
    /** Internal accessor for lookup comments by name/scope (used by context transition). */
    public HashMap<String, ArrayList<String>> getLookupCommentsByNameScopeInternal() {
        return lookupCommentsByNameScope;
    }

    // =========================================================================
    // Public Export Methods (for context delegation)
    // =========================================================================
    
    /**
     * Export an equation table with the specified syntax.
     * @param eqTable the equation table element
     * @param syntax the export syntax style
     * @return the exported block string
     */
    public String exportEquationTable(EquationTableElm eqTable, ExportSyntax syntax) {
        return (syntax == ExportSyntax.R_STYLE)
            ? exportEquationTableRStyle(eqTable)
            : exportEquationTable(eqTable);
    }
    
    /**
     * Export a godly table with the specified syntax.
     * @param godlyTable the godly table element
     * @param syntax the export syntax style
     * @return the exported block string
     */
    public String exportGodlyTable(GodlyTableElm godlyTable, ExportSyntax syntax) {
        return (syntax == ExportSyntax.R_STYLE)
            ? exportGodlyTableRStyle(godlyTable)
            : exportGodlyTable(godlyTable);
    }
    
    /**
     * Export a matrix table with the specified syntax.
     * @param sfcTable the SFC table element
     * @param syntax the export syntax style
     * @return the exported block string
     */
    public String exportMatrixTable(SFCTableElm sfcTable, ExportSyntax syntax) {
        return (syntax == ExportSyntax.R_STYLE)
            ? exportSFCTableRStyle(sfcTable)
            : exportSFCTable(sfcTable);
    }
    
    /**
     * Export circuit elements that don't have dedicated block handlers.
     * @param elements list of elements to export
     * @param scopeElmsExported set of scope elements already exported as blocks
     * @return the @circuit block string
     */
    public String exportCircuitElements(ArrayList<CircuitElm> elements, HashSet<CircuitElm> scopeElmsExported) {
        if (elements == null || elements.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("@circuit\n");
        
        for (CircuitElm elm : elements) {
            if (elm instanceof ScopeElm) {
                ScopeElm se = (ScopeElm) elm;
                // Skip raw 403 dump when we can represent this scope in @scope block.
                if ((scopeElmsExported != null && scopeElmsExported.contains(elm)) 
                    || canExportScopeAsBlock(se.elmScope)) {
                    continue;
                }
            }
            String dump = sim.getImportExportHelper().getElementDumpWithUid(elm);
            if (dump != null && !dump.isEmpty()) {
                sb.append(dump).append("\n");
            }
        }
        
        sb.append("@end\n");
        return sb.toString();
    }

    private enum TemplateBlockType {
        LOOKUP,
        EQUATIONS,
        MATRIX,
        SANKEY,
        SCOPE,
        CIRCUIT,
        OTHER
    }

    private String exportWithTemplateMerge(String sourceText) {
        scopeElmsExportedAsBlocks.clear();
        SFCRExportContext ctx = new SFCRExportContext(this, sim, exportSyntax);
        categorizeElements(ctx);
        resetLookupExportState();
        seedLookupNamesFromTemplate(sourceText);

        ArrayList<String> equationBlocks = buildCanonicalEquationBlocks();
        ArrayList<String> lookupBlocks = buildCanonicalLookupBlocks();
        ArrayList<String> matrixBlocks = buildCanonicalMatrixBlocks();
        ArrayList<String> sankeyBlocks = buildCanonicalSankeyBlocks();
        ArrayList<String> scopeBlocks = buildCanonicalScopeBlocks();
        String circuitBlock = extractStructuralPayload(exportCircuitElements());

        int lookupIndex = 0;
        int eqIndex = 0;
        int matrixIndex = 0;
        int sankeyIndex = 0;
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
                trimmed.startsWith("@matrix") || trimmed.startsWith("@sankey") ||
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

    private void seedLookupNamesFromTemplate(String sourceText) {
        if (sourceText == null || sourceText.trim().isEmpty()) {
            return;
        }

        String[] lines = sourceText.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String header = lines[i] == null ? "" : lines[i].trim();
            if (!header.startsWith("@lookup")) {
                continue;
            }

            String body = header.substring("@lookup".length()).trim();
            if (body.isEmpty()) {
                continue;
            }

            String lookupName = body;
            String scopeName = null;

            int scopePos = body.indexOf(" scope=");
            if (scopePos >= 0) {
                lookupName = body.substring(0, scopePos).trim();
                scopeName = body.substring(scopePos + " scope=".length()).trim();
            }

            lookupName = SFCRUtil.sanitizeName(SFCRUtil.normalizeVariableName(lookupName));
            if (scopeName != null && !scopeName.isEmpty()) {
                scopeName = SFCRUtil.sanitizeName(scopeName);
            } else {
                scopeName = null;
            }
            if (lookupName.isEmpty()) {
                continue;
            }

            ArrayList<Double> xs = new ArrayList<Double>();
            ArrayList<Double> ys = new ArrayList<Double>();
            ArrayList<String> comments = new ArrayList<String>();

            int end = i;
            for (int j = i + 1; j < lines.length; j++) {
                String row = lines[j] == null ? "" : lines[j].trim();
                end = j;
                if (row.equals("@end")) {
                    break;
                }
                if (row.startsWith("#")) {
                    comments.add(row);
                    continue;
                }
                if (row.isEmpty()) {
                    continue;
                }

                String[] pair;
                if (row.indexOf(',') >= 0) {
                    pair = row.split(",", 2);
                } else {
                    pair = row.split("\\s+", 2);
                }
                if (pair.length < 2) {
                    continue;
                }

                try {
                    xs.add(Double.valueOf(Double.parseDouble(pair[0].trim())));
                    ys.add(Double.valueOf(Double.parseDouble(pair[1].trim())));
                } catch (Exception ignored) {
                }
            }

            i = end;

            if (xs.size() < 2 || ys.size() != xs.size()) {
                continue;
            }

            if (!comments.isEmpty()) {
                lookupCommentsByNameScope.put(buildLookupNameScopeKey(lookupName, scopeName), new ArrayList<String>(comments));
            }

            String signature = buildLookupSignatureFromPoints(scopeName, xs, ys);
            LookupDefinition existing = lookupExportBySignature.get(signature);
            if (existing != null) {
                if (existing.name != null && existing.name.startsWith("Lookup_") && !lookupName.startsWith("Lookup_")) {
                    existing.name = makeLookupName(lookupName, scopeName);
                }
                if (existing.comments.isEmpty() && !comments.isEmpty()) {
                    existing.comments.addAll(comments);
                }
                continue;
            }

            LookupDefinition spec = new LookupDefinition();
            spec.scope = scopeName;
            spec.name = makeLookupName(lookupName, scopeName);
            spec.xs.addAll(xs);
            spec.ys.addAll(ys);
            spec.comments.addAll(comments);
            lookupExportSpecs.add(spec);
            lookupExportBySignature.put(signature, spec);
        }
    }

    private ArrayList<String> buildCanonicalEquationBlocks() {
        ArrayList<String> blocks = new ArrayList<String>();
        for (EquationTableElm eqTable : equationTables) {
            String block = (exportSyntax == ExportSyntax.R_STYLE)
                ? exportEquationTableRStyle(eqTable)
                : exportEquationTable(eqTable);
            String payload = extractStructuralPayload(block);
            if (!payload.isEmpty()) {
                blocks.add(payload);
            }
        }
        for (GodlyTableElm godlyTable : godlyTables) {
            String block = (exportSyntax == ExportSyntax.R_STYLE)
                ? exportGodlyTableRStyle(godlyTable)
                : exportGodlyTable(godlyTable);
            String payload = extractStructuralPayload(block);
            if (!payload.isEmpty()) {
                blocks.add(payload);
            }
        }
        return blocks;
    }

    private ArrayList<String> buildCanonicalLookupBlocks() {
        String lookupText = renderBlocksForType(SFCRBlockType.LOOKUP);
        return collectAtDirectiveBlocks(lookupText, "@lookup");
    }

    private ArrayList<String> buildCanonicalMatrixBlocks() {
        String matrixText = renderBlocksForType(SFCRBlockType.MATRIX);
        ArrayList<String> blocks = collectAtDirectiveBlocks(matrixText, "@matrix");
        blocks.addAll(collectRStyleBlocks(matrixText, "sfcr_matrix"));
        return blocks;
    }

    private ArrayList<String> buildCanonicalSankeyBlocks() {
        return collectAtDirectiveBlocks(renderBlocksForType(SFCRBlockType.SANKEY), "@sankey");
    }

    private ArrayList<String> buildCanonicalScopeBlocks() {
        return collectAtDirectiveBlocks(renderBlocksForType(SFCRBlockType.SCOPE), "@scope");
    }

    private String renderBlocksForType(SFCRBlockType blockType) {
        if (blockType == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        SFCRExportContext context = new SFCRExportContext(this);
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

    private ArrayList<String> collectAtDirectiveBlocks(String text, String directive) {
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

    private ArrayList<String> collectRStyleBlocks(String text, String fnName) {
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

    private String extractStructuralPayload(String block) {
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

    private boolean looksLikeRStyleAssignmentStart(String trimmed) {
        if (trimmed == null || trimmed.isEmpty()) {
            return false;
        }
        if (!trimmed.contains("<-") || !trimmed.contains("sfcr_")) {
            return false;
        }
        return trimmed.contains("sfcr_set") || trimmed.contains("sfcr_matrix");
    }

    private TemplateBlockType detectTemplateBlockType(String text) {
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
        if (lower.contains("@scope")) {
            return TemplateBlockType.SCOPE;
        }
        return TemplateBlockType.OTHER;
    }

    private String wrapReplacementWithFenceLike(String fenceHeader, String replacement) {
        String header = (fenceHeader == null || fenceHeader.trim().isEmpty()) ? "```{r}" : fenceHeader.trim();
        if (!header.startsWith("```")) {
            header = "```{r}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n");
        sb.append(replacement).append("\n");
        sb.append("```");
        return sb.toString();
    }

    /** Collapse excessive blank lines outside fenced code blocks. */
    private String normalizeBlankLinesOutsideFences(String text) {
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

    private void appendExportBlock(StringBuilder sb, String block) {
        if (sb == null || block == null) {
            return;
        }
        String trimmed = block.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (trimmed.startsWith("```")) {
            sb.append(trimmed).append("\n\n");
            return;
        }
        String[] lines = trimmed.split("\n");
        int structuralStart = findStructuralStartLine(lines);

        // Keep associated markdown/comments ABOVE the fenced structural block.
        if (structuralStart > 0) {
            for (int i = 0; i < structuralStart; i++) {
                sb.append(lines[i]).append("\n");
            }
            sb.append("\n");
        }

        sb.append("```{r}\n");
        if (structuralStart < lines.length) {
            for (int i = structuralStart; i < lines.length; i++) {
                sb.append(lines[i]).append("\n");
            }
        }
        sb.append("```\n\n");
    }

    private void resetLookupExportState() {
        lookupExportSpecs.clear();
        lookupExportBySignature.clear();
        lookupCommentsByNameScope.clear();
    }

    // Kept for test/reflection compatibility during handler migration.
    private String exportLookupBlocks() {
        return renderBlocksForType(SFCRBlockType.LOOKUP);
    }

    private String rewriteExpressionForLookupExport(String expr, String scopeName) {
        if (expr == null || expr.trim().isEmpty()) {
            return expr;
        }

        // Keep expression text unchanged. Only native lookup(name, x[, clamp])
        // contributes @lookup blocks in live export.
        registerNativeLookupSpecs(expr, scopeName);
        return expr;
    }

    private void registerNativeLookupSpecs(String expr, String scopeName) {
        if (expr == null || expr.trim().isEmpty()) {
            return;
        }

        int search = 0;
        while (true) {
            int fn = findFunctionCall(expr, "lookup", search);
            if (fn < 0) {
                break;
            }

            int open = expr.indexOf('(', fn);
            if (open < 0) {
                break;
            }
            int close = findMatchingParenForExport(expr, open);
            if (close < 0) {
                break;
            }

            String inside = expr.substring(open + 1, close);
            ArrayList<String> args = splitTopLevelArgs(inside);
            if (args.size() >= 2) {
                registerLookupFromNameArg(args.get(0), scopeName);
            }

            search = close + 1;
        }
    }

    private void registerLookupFromNameArg(String nameArg, String scopeName) {
        if (nameArg == null) {
            return;
        }

        String raw = nameArg.trim();
        if (raw.isEmpty()) {
            return;
        }
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
            if (raw.length() < 2) {
                return;
            }
            raw = raw.substring(1, raw.length() - 1).trim();
        }

        String lookupName = SFCRUtil.sanitizeName(SFCRUtil.normalizeVariableName(raw));
        if (lookupName.isEmpty()) {
            return;
        }

        String normalizedScope = (scopeName == null || scopeName.isEmpty()) ? null : SFCRUtil.sanitizeName(scopeName);
        if (findLookupSpecByNameAndScope(lookupName, normalizedScope) != null) {
            return;
        }

        LookupTableRegistry.LookupTableSnapshot snapshot = LookupTableRegistry.getSnapshot(normalizedScope, lookupName);
        if (snapshot == null || snapshot.xs == null || snapshot.ys == null || snapshot.xs.size() < 2 || snapshot.xs.size() != snapshot.ys.size()) {
            return;
        }

        String resolvedScope = (snapshot.resolvedScope == null || snapshot.resolvedScope.isEmpty())
            ? null
            : snapshot.resolvedScope;
        String signature = buildLookupSignatureFromPoints(resolvedScope, snapshot.xs, snapshot.ys);
        LookupDefinition existingBySignature = lookupExportBySignature.get(signature);
        if (existingBySignature != null) {
            // For identical lookup point signatures, prefer the equation-referenced base
            // name over a template-seeded *_lookup alias to keep lookup(Name, x) stable.
            if (existingBySignature.name != null
                    && existingBySignature.name.endsWith("_lookup")
                    && !lookupName.endsWith("_lookup")
                    && !isLookupNameTaken(lookupName, resolvedScope)) {
                existingBySignature.name = lookupName;
            }
            if (existingBySignature.comments.isEmpty()) {
                existingBySignature.comments.addAll(getLookupComments(lookupName, resolvedScope));
            }
            return;
        }

        LookupDefinition spec = new LookupDefinition();
        spec.name = lookupName;
        spec.scope = resolvedScope;
        spec.xs.addAll(snapshot.xs);
        spec.ys.addAll(snapshot.ys);
        spec.comments.addAll(getLookupComments(spec.name, spec.scope));
        if (spec.comments.isEmpty()) {
            spec.comments.addAll(getLookupComments(lookupName, resolvedScope));
        }

        lookupExportSpecs.add(spec);
        lookupExportBySignature.put(signature, spec);
    }

    private String buildLookupNameScopeKey(String name, String scopeName) {
        String normName = SFCRUtil.sanitizeName(SFCRUtil.normalizeVariableName(name));
        String normScope = (scopeName == null || scopeName.isEmpty()) ? "" : SFCRUtil.sanitizeName(scopeName);
        return normScope + "|" + normName;
    }

    private ArrayList<String> getLookupComments(String name, String scopeName) {
        ArrayList<String> comments = lookupCommentsByNameScope.get(buildLookupNameScopeKey(name, scopeName));
        if (comments == null) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(comments);
    }

    private LookupDefinition findLookupSpecByNameAndScope(String name, String scopeName) {
        String normName = SFCRUtil.sanitizeName(SFCRUtil.normalizeVariableName(name));
        String normScope = (scopeName == null || scopeName.isEmpty()) ? "" : SFCRUtil.sanitizeName(scopeName);
        for (int i = 0; i < lookupExportSpecs.size(); i++) {
            LookupDefinition spec = lookupExportSpecs.get(i);
            if (spec == null || spec.name == null) {
                continue;
            }
            String specName = SFCRUtil.sanitizeName(SFCRUtil.normalizeVariableName(spec.name));
            String specScope = (spec.scope == null || spec.scope.isEmpty()) ? "" : SFCRUtil.sanitizeName(spec.scope);
            if (normName.equals(specName) && normScope.equals(specScope)) {
                return spec;
            }
        }
        return null;
    }

    private String makeLookupName(String preferredLookupName, String scopeName) {
        String base;
        if (preferredLookupName == null || preferredLookupName.trim().isEmpty()) {
            base = "Lookup_" + (lookupExportSpecs.size() + 1);
        } else {
            base = SFCRUtil.normalizeVariableName(preferredLookupName.trim());
            base = SFCRUtil.sanitizeName(base);
            if (base.isEmpty()) {
                base = "Lookup_" + (lookupExportSpecs.size() + 1);
            }
        }

        if (isEquationNameTakenInScope(base, scopeName)) {
            base = base + "_lookup";
        }

        String candidate = base;
        int suffix = 2;
        while (isLookupNameTaken(candidate, scopeName) || isEquationNameTakenInScope(candidate, scopeName)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean isEquationNameTakenInScope(String name, String scopeName) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String wantedScope = scopeName == null ? "" : SFCRUtil.sanitizeName(scopeName);

        for (int i = 0; i < equationTables.size(); i++) {
            EquationTableElm table = equationTables.get(i);
            if (table == null) {
                continue;
            }
            String tableScope = SFCRUtil.sanitizeName(table.getTableName());
            if (!tableScope.equals(wantedScope)) {
                continue;
            }
            for (int r = 0; r < table.getRowCount(); r++) {
                String lhs = SFCRUtil.sanitizeName(table.getOutputName(r));
                if (name.equals(lhs)) {
                    return true;
                }
            }
        }

        for (int i = 0; i < godlyTables.size(); i++) {
            GodlyTableElm table = godlyTables.get(i);
            if (table == null || table.getCols() < 2) {
                continue;
            }
            String tableScope = SFCRUtil.sanitizeName(table.getTableTitle());
            if (!tableScope.equals(wantedScope)) {
                continue;
            }
            for (int c = 1; c < table.getCols(); c++) {
                TableColumn column = table.getColumn(c);
                if (column == null) {
                    continue;
                }
                String stockName = SFCRUtil.sanitizeName(column.getStockName());
                if (name.equals(stockName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isLookupNameTaken(String name, String scopeName) {
        for (int i = 0; i < lookupExportSpecs.size(); i++) {
            LookupDefinition existing = lookupExportSpecs.get(i);
            if (existing == null) {
                continue;
            }
            boolean sameScope =
                (existing.scope == null ? "" : existing.scope).equals(scopeName == null ? "" : scopeName);
            if (sameScope && name.equals(existing.name)) {
                return true;
            }
        }
        return false;
    }

    private String buildLookupSignatureFromPoints(String scopeName, ArrayList<Double> xs, ArrayList<Double> ys) {
        StringBuilder sb = new StringBuilder();
        if (scopeName != null && !scopeName.isEmpty()) {
            sb.append(SFCRUtil.sanitizeName(scopeName));
        }
        sb.append("|");
        for (int i = 0; i < xs.size() && i < ys.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(xs.get(i).doubleValue());
            sb.append(",");
            sb.append(ys.get(i).doubleValue());
        }
        return sb.toString();
    }

    private int findFunctionCall(String expr, String name, int fromIndex) {
        String lower = expr.toLowerCase();
        String needle = name.toLowerCase();
        int idx = fromIndex;
        while (true) {
            idx = lower.indexOf(needle, idx);
            if (idx < 0) {
                return -1;
            }
            int end = idx + needle.length();
            if (idx > 0 && isIdentifierPart(expr.charAt(idx - 1))) {
                idx = end;
                continue;
            }
            int j = end;
            while (j < expr.length() && Character.isWhitespace(expr.charAt(j))) {
                j++;
            }
            if (j < expr.length() && expr.charAt(j) == '(') {
                return idx;
            }
            idx = end;
        }
    }

    private int findMatchingParenForExport(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private ArrayList<String> splitTopLevelArgs(String text) {
        ArrayList<String> out = new ArrayList<String>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                out.add(text.substring(start, i));
                start = i + 1;
            }
        }
        out.add(text.substring(start));
        return out;
    }

    private boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '\\' || c == '^' || c == '{' || c == '}' || c == '.';
    }

    private int findStructuralStartLine(String[] lines) {
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
    // Block Exporters
    // =========================================================================
    
    /** Categorize elements for export. */
    private void categorizeElements(SFCRExportContext ctx) {
        ArrayList<EquationTableElm> eqTables = new ArrayList<EquationTableElm>();
        ArrayList<SFCTableElm> matrixTables = new ArrayList<SFCTableElm>();
        ArrayList<GodlyTableElm> godlyTableList = new ArrayList<GodlyTableElm>();
        ArrayList<SFCSankeyElm> sankeyList = new ArrayList<SFCSankeyElm>();
        ArrayList<CircuitElm> otherElms = new ArrayList<CircuitElm>();
        ActionTimeElm actionElm = null;
        
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            
            if (elm instanceof EquationTableElm) {
                eqTables.add((EquationTableElm) elm);
            } else if (elm instanceof SFCTableElm) {
                matrixTables.add((SFCTableElm) elm);
            } else if (elm instanceof GodlyTableElm) {
                godlyTableList.add((GodlyTableElm) elm);
            } else if (elm instanceof SFCSankeyElm) {
                sankeyList.add((SFCSankeyElm) elm);
            } else if (elm instanceof ActionTimeElm) {
                if (actionElm == null) {
                    actionElm = (ActionTimeElm) elm;
                }
            } else {
                otherElms.add(elm);
            }
        }
        
        ctx.setEquationTables(eqTables);
        ctx.setSfcTables(matrixTables);
        ctx.setGodlyTables(godlyTableList);
        ctx.setSankeyDiagrams(sankeyList);
        ctx.setOtherElements(otherElms);
        ctx.setActionTimeElm(actionElm);
        
        // Also update legacy fields for backward compatibility during transition
        equationTables = eqTables;
        sfcTables = matrixTables;
        godlyTables = godlyTableList;
        sankeyDiagrams = sankeyList;
        otherElements = otherElms;
        actionTimeElmForExport = actionElm;
    }
    
    /** Export model documentation as inline markdown (no @info wrapper). */
    private String exportInlineDocumentation() {
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

        // Ensure trailing newline for clean concatenation
        if (!sanitized.endsWith("\n")) {
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Keep narrative inline markdown and viewer plot directives, but drop structural
     * SFCR definitions that are exported in native blocks (@equations/@matrix/etc)
     * to avoid duplicated sections in the exported file.
     */
    private String sanitizeInlineDocumentation(String markdown) {
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

    private boolean startsStructuralSfcrBlock(String trimmed) {
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
            || trimmed.startsWith("@scope")
            || trimmed.startsWith("@circuit")
            || trimmed.startsWith("@info");
    }

    private boolean shouldKeepFencedInlineBlock(String block) {
        if (block == null || block.isEmpty()) {
            return false;
        }
        String lower = block.toLowerCase();

        // Keep plot directives in fenced blocks.
        if (lower.contains("@plot") || lower.contains("plot:") || lower.contains("vars:")) {
            return true;
        }

        // Drop structural duplicated blocks.
        if (lower.contains("sfcr_set") || lower.contains("sfcr_matrix")) {
            return false;
        }
        if (lower.contains("@equations") || lower.contains("@parameters") ||
            lower.contains("@matrix") || lower.contains("@circuit") ||
            lower.contains("@sankey") || lower.contains("@scope") ||
            lower.contains("@info")) {
            return false;
        }

        return true;
    }

    private static class EquationExportRow {
        boolean commentRow;
        String commentText;
        String sourceName;
        String name;
        String expr;
        EquationTableElm.RowOutputMode mode;
        String sliderVar;
        double sliderValue;
        String initialEq;
        String hint;
    }

    private ArrayList<EquationExportRow> collectEquationExportRows(EquationTableElm eqTable, String tableName) {
        ArrayList<EquationExportRow> rows = new ArrayList<EquationExportRow>();
        int rowCount = eqTable.getRowCount();
        for (int row = 0; row < rowCount; row++) {
            String sourceName = eqTable.getOutputName(row);
            if (EquationTableElm.isCommentRowName(sourceName)) {
                String comment = sourceName == null ? "" : sourceName.trim();
                if (comment.startsWith("#")) {
                    comment = comment.substring(1).trim();
                }
                if (!comment.isEmpty()) {
                    EquationExportRow data = new EquationExportRow();
                    data.commentRow = true;
                    data.commentText = comment;
                    rows.add(data);
                }
                continue;
            }

            String name = eqTable.getDisplayOutputName(row);
            if (name == null || name.isEmpty()) {
                continue;
            }

            String expr = eqTable.getEquation(row);
            if (expr == null) {
                expr = "0";
            }
            expr = rewriteExpressionForLookupExport(expr, tableName);

            String initialEq = eqTable.getInitialEquation(row);
            if (initialEq != null && !initialEq.trim().isEmpty()) {
                initialEq = rewriteExpressionForLookupExport(initialEq, tableName);
            }

            EquationExportRow data = new EquationExportRow();
            data.commentRow = false;
            data.sourceName = sourceName;
            data.name = name;
            data.expr = expr;
            data.mode = eqTable.getOutputMode(row);
            data.sliderVar = eqTable.getSliderVarName(row);
            data.sliderValue = eqTable.getSliderValue(row);
            data.initialEq = initialEq;
            data.hint = sanitizeHintForRStyleExport(HintRegistry.getHint(sourceName));
            rows.add(data);
        }
        return rows;
    }

    private static class GodlyExportRow {
        String stockName;
        String flowExpr;
        double initialValue;
        String hint;
    }

    private ArrayList<GodlyExportRow> collectGodlyExportRows(GodlyTableElm godlyTable, String tableName) {
        ArrayList<GodlyExportRow> rows = new ArrayList<GodlyExportRow>();
        int cols = godlyTable.getCols();
        for (int col = 0; col < cols; col++) {
            TableColumn column = godlyTable.getColumn(col);
            if (column == null || column.isALE()) {
                continue;
            }
            String stockName = column.getStockName();
            if (stockName == null || stockName.isEmpty()) {
                continue;
            }
            GodlyExportRow row = new GodlyExportRow();
            row.stockName = stockName;
            row.flowExpr = rewriteExpressionForLookupExport(buildColumnFlowExpression(godlyTable, col), tableName);
            row.initialValue = godlyTable.getInitialValue(col);
            row.hint = HintRegistry.getHint(stockName);
            rows.add(row);
        }
        return rows;
    }

    private static class MatrixExportData {
        ArrayList<String> stockNames = new ArrayList<String>();
        ArrayList<String> stockCodes = new ArrayList<String>();
        ArrayList<String> rowNames = new ArrayList<String>();
        ArrayList<String[]> rowValues = new ArrayList<String[]>();
    }

    private MatrixExportData collectMatrixExportData(SFCTableElm sfcTable, String tableName, boolean includeCodes) {
        MatrixExportData data = new MatrixExportData();
        int totalCols = sfcTable.getCols();
        Set<String> usedCodes = includeCodes ? new HashSet<String>() : null;

        for (int col = 0; col < totalCols; col++) {
            TableColumn column = sfcTable.getColumn(col);
            if (column == null || column.isALE()) {
                continue;
            }
            String stockName = column.getStockName();
            if (stockName == null || stockName.trim().isEmpty()) {
                stockName = "Column" + (data.stockNames.size() + 1);
            }
            data.stockNames.add(stockName);
            if (includeCodes) {
                data.stockCodes.add(makeUniqueRCode(toRCodeIdentifier(stockName), usedCodes));
            }
        }

        int rows = sfcTable.getRows();
        for (int row = 0; row < rows; row++) {
            String rowDesc = sfcTable.getRowDescription(row);
            if (rowDesc == null || rowDesc.trim().isEmpty()) {
                rowDesc = "Row" + row;
            }
            data.rowNames.add(rowDesc);

            String[] rowData = new String[data.stockNames.size()];
            int dataColIndex = 0;
            for (int col = 0; col < totalCols; col++) {
                TableColumn column = sfcTable.getColumn(col);
                if (column == null || column.isALE()) {
                    continue;
                }
                String cellExpr = column.getCellEquation(row);
                if (cellExpr == null) {
                    cellExpr = "";
                }
                rowData[dataColIndex++] = rewriteExpressionForLookupExport(cellExpr, tableName);
            }
            data.rowValues.add(rowData);
        }
        return data;
    }
    
    /** Export EquationTableElm as @equations block. */
    private String exportEquationTable(EquationTableElm eqTable) {
        StringBuilder sb = new StringBuilder();
        
        String tableName = eqTable.getTableName();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Equations";
        }
        
        ArrayList<EquationExportRow> rows = collectEquationExportRows(eqTable, tableName);
        if (rows.isEmpty()) return "";

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_EQUATIONS, SFCRUtil.sanitizeName(tableName));
        
        sb.append("@equations ").append(SFCRUtil.sanitizeName(tableName));
        sb.append(formatPosition(eqTable)).append("\n");
        
        for (int i = 0; i < rows.size(); i++) {
            EquationExportRow row = rows.get(i);
            if (row.commentRow) {
                sb.append("  # ").append(row.commentText).append("\n");
                continue;
            }

            sb.append("  ").append(row.name).append(" ~ ").append(row.expr);
            sb.append(" ; mode=").append(SFCRUtil.formatEquationRowMode(row.mode));
            if (row.sliderVar != null && !row.sliderVar.trim().isEmpty()) {
                sb.append(" ; slider=").append(row.sliderVar.trim());
            }
            sb.append(" ; sliderValue=").append(row.sliderValue);
            if (row.initialEq != null && !row.initialEq.trim().isEmpty()) {
                sb.append(" ; initial=").append(row.initialEq.trim());
            }
            if (row.hint != null && !row.hint.trim().isEmpty()) {
                sb.append("  # ").append(row.hint);
            }
            sb.append("\n");
        }
        
        sb.append("@end\n");
        return sb.toString();
    }

    /** Export EquationTableElm as R-style sfcr_set() assignment. */
    private String exportEquationTableRStyle(EquationTableElm eqTable) {
        StringBuilder sb = new StringBuilder();

        String tableName = eqTable.getTableName();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Equations";
        }

        ArrayList<EquationExportRow> rows = collectEquationExportRows(eqTable, tableName);
        if (rows.isEmpty()) {
            return "";
        }

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_EQUATIONS, SFCRUtil.sanitizeName(tableName));

        int equationCount = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).commentRow) {
                equationCount++;
            }
        }

        if (equationCount == 0) {
            return "";
        }

        String assignmentName = toRAssignmentName(tableName);
        sb.append(assignmentName).append(" <- sfcr_set(\n");

        String metadataComment = formatRBlockMetadataComment(eqTable, null).trim();
        if (!metadataComment.isEmpty()) {
            sb.append("  ").append(metadataComment).append("\n");
        }

        int emitted = 0;
        for (int i = 0; i < rows.size(); i++) {
            EquationExportRow row = rows.get(i);
            if (row.commentRow) {
                sb.append("  # ").append(row.commentText).append("\n");
                continue;
            }

            emitted++;
            sb.append("  e").append(emitted).append(" = ").append(row.name).append(" ~ ").append(row.expr);
            if (emitted < equationCount) {
                sb.append(",");
            }

            // Persist row-editable properties inside bracket metadata so R-style
            // export/import keeps mode/slider settings without changing core syntax.
            String rowMeta = formatRStyleEquationInlineMetadata(row.mode, row.sliderVar, row.sliderValue, row.initialEq);
            appendRStyleInlineComment(sb, row.hint, rowMeta);
            sb.append("\n");
        }
        sb.append(")\n");
        return sb.toString();
    }

    /** Export GodlyTableElm as R-style sfcr_set() assignment. */
    private String exportGodlyTableRStyle(GodlyTableElm godlyTable) {
        StringBuilder sb = new StringBuilder();

        String tableName = godlyTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Stocks";
        }

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_EQUATIONS, SFCRUtil.sanitizeName(tableName));
        sb.append(formatRBlockMetadataComment(godlyTable, null));

        ArrayList<GodlyExportRow> rows = collectGodlyExportRows(godlyTable, tableName);
        if (rows.isEmpty()) {
            return "";
        }

        String assignmentName = toRAssignmentName(tableName);
        sb.append(assignmentName).append(" <- sfcr_set(\n");
        for (int i = 0; i < rows.size(); i++) {
            GodlyExportRow row = rows.get(i);
            sb.append("  e").append(i + 1).append(" = ")
                .append(row.stockName).append(" ~ ")
                .append(row.stockName).append("_init + integrate(").append(row.flowExpr).append(")");
            if (i < rows.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(")\n");
        return sb.toString();
    }

    /** Export SFCTableElm as R-style sfcr_matrix() assignment. */
    private String exportSFCTableRStyle(SFCTableElm sfcTable) {
        StringBuilder sb = new StringBuilder();

        String tableName = sfcTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "SFC_Matrix";
        }

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_MATRIX, SFCRUtil.sanitizeName(tableName));

        MatrixExportData data = collectMatrixExportData(sfcTable, tableName, true);
        if (data.stockNames.isEmpty()) {
            return "";
        }

        String assignmentName = toRAssignmentName(tableName);
        sb.append(assignmentName).append(" <- sfcr_matrix(\n");
        String metadataComment = formatRBlockMetadataComment(sfcTable, "transaction_flow").trim();
        if (!metadataComment.isEmpty()) {
            sb.append("  ").append(metadataComment).append("\n");
        }
        sb.append("  columns = c(").append(joinRQuoted(data.stockNames)).append("),\n");
        sb.append("  codes = c(").append(joinRQuoted(data.stockCodes)).append("),\n");

        for (int row = 0; row < data.rowNames.size(); row++) {
            sb.append("  c(\"").append(escapeRString(data.rowNames.get(row))).append("\"");
            String[] rowData = data.rowValues.get(row);
            for (int col = 0; col < data.stockCodes.size(); col++) {
                String cellExpr = (col < rowData.length) ? rowData[col] : "";
                sb.append(", ")
                  .append(data.stockCodes.get(col))
                  .append(" = \"")
                  .append(escapeRString(cellExpr))
                  .append("\"");
            }
            sb.append(")");
            if (row < data.rowNames.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append(")\n");
        return sb.toString();
    }

    /** Export GodlyTableElm as @equations block (integration-based stocks). */
    private String exportGodlyTable(GodlyTableElm godlyTable) {
        StringBuilder sb = new StringBuilder();
        
        String tableName = godlyTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Stocks";
        }

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_EQUATIONS, SFCRUtil.sanitizeName(tableName));
        
        sb.append("@equations ").append(SFCRUtil.sanitizeName(tableName));
        sb.append(formatPosition(godlyTable)).append("\n");
        
        ArrayList<GodlyExportRow> rows = collectGodlyExportRows(godlyTable, tableName);
        for (int i = 0; i < rows.size(); i++) {
            GodlyExportRow row = rows.get(i);
            sb.append("  # Initial value: ").append(row.initialValue).append("\n");
            sb.append("  ").append(row.stockName).append(" ~ ");
            sb.append(row.stockName).append("_init + integrate(").append(row.flowExpr).append(")");
            if (row.hint != null && !row.hint.trim().isEmpty()) {
                sb.append("  # ").append(row.hint);
            }
            sb.append("\n");
        }
        
        sb.append("@end\n");
        return sb.toString();
    }
    
    /** Build an expression representing the sum of flows in a column. */
    private String buildColumnFlowExpression(GodlyTableElm table, int col) {
        StringBuilder sb = new StringBuilder();
        
        int rows = table.getRows();
        boolean first = true;
        
        for (int row = 0; row < rows; row++) {
            TableColumn column = table.getColumn(col);
            if (column == null) continue;
            
            String cellExpr = column.getCellEquation(row);
            if (cellExpr == null || cellExpr.trim().isEmpty() || cellExpr.equals("0")) {
                continue;
            }
            
            if (!first) {
                sb.append(" + ");
            }
            
            // Wrap in parentheses if it contains operators
            if (cellExpr.contains("+") || cellExpr.contains("-")) {
                sb.append("(").append(cellExpr).append(")");
            } else {
                sb.append(cellExpr);
            }
            first = false;
        }
        
        if (sb.length() == 0) {
            return "0";
        }
        
        return sb.toString();
    }
    
    /** Export SFCTableElm as @matrix block. */
    private String exportSFCTable(SFCTableElm sfcTable) {
        StringBuilder sb = new StringBuilder();
        
        String tableName = sfcTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "SFC_Matrix";
        }

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_MATRIX, SFCRUtil.sanitizeName(tableName));
        
        sb.append("@matrix ").append(SFCRUtil.sanitizeName(tableName));
        sb.append(formatPosition(sfcTable)).append("\n");
        sb.append("  type: transaction_flow\n");
        
        MatrixExportData data = collectMatrixExportData(sfcTable, tableName, false);
        int dataCols = data.stockNames.size();
        
        // Export as markdown table only (no separate columns: line to avoid duplication)
        sb.append("\n");
        
        // Header row (exclude computed Σ column - it will be auto-added on import)
        sb.append("| Transaction |");
        for (int col = 0; col < data.stockNames.size(); col++) {
            sb.append(" ").append(data.stockNames.get(col)).append(" |");
        }
        sb.append("\n");
        
        // Separator row
        sb.append("|-------------|");
        for (int col = 0; col < dataCols; col++) {
            sb.append("------|");
        }
        sb.append("\n");
        
        // Data rows (exclude computed Σ column)
        for (int row = 0; row < data.rowNames.size(); row++) {
            sb.append("| ").append(data.rowNames.get(row)).append(" |");
            String[] rowData = data.rowValues.get(row);
            for (int col = 0; col < dataCols; col++) {
                String cellExpr = (col < rowData.length) ? rowData[col] : "";
                sb.append(" ").append(cellExpr).append(" |");
            }
            sb.append("\n");
        }
        
        sb.append("@end\n");
        return sb.toString();
    }

    /** Export non-SFCR elements in @circuit block. */
    private String exportCircuitElements() {
        if (otherElements.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("@circuit\n");
        
        for (CircuitElm elm : otherElements) {
            if (elm instanceof ScopeElm) {
                ScopeElm se = (ScopeElm) elm;
                // Skip raw 403 dump when we can represent this scope in @scope block.
                if (scopeElmsExportedAsBlocks.contains(elm) || canExportScopeAsBlock(se.elmScope)) {
                    continue;
                }
            }
            String dump = sim.getImportExportHelper().getElementDumpWithUid(elm);
            if (dump != null && !dump.isEmpty()) {
                sb.append(dump).append("\n");
            }
        }
        
        sb.append("@end\n");
        return sb.toString();
    }

    private boolean canExportScopeAsBlock(Scope s) {
        if (s == null || s.getPlotCount() == 0) {
            return false;
        }
        for (int p = 0; p < s.getPlotCount(); p++) {
            CircuitElm elm = s.getPlotElement(p);
            if (elm != null) {
                String uid = elm.getPersistentUid();
                if (uid != null && !uid.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Public accessor for scope block export (used by SFCRExportContext). */
    public boolean appendScopeBlockPublic(StringBuilder sb, Scope s, int defaultIndex, String defaultPrefix, ScopeElm scopeElm) {
        return appendScopeBlock(sb, s, defaultIndex, defaultPrefix, scopeElm);
    }

    private boolean appendScopeBlock(StringBuilder sb, Scope s, int defaultIndex, String defaultPrefix, ScopeElm scopeElm) {
        if (s == null || s.getPlotCount() == 0) {
            return false;
        }

        int validPlots = 0;
        for (int p = 0; p < s.getPlotCount(); p++) {
            CircuitElm elm = s.getPlotElement(p);
            if (elm != null && elm.getPersistentUid() != null && !elm.getPersistentUid().isEmpty()) {
                validPlots++;
            }
        }
        if (validPlots == 0) {
            return false;
        }

        String scopeName = s.getScopeMenuName();
        if (scopeName == null || scopeName.isEmpty()) {
            scopeName = defaultPrefix + "_" + defaultIndex;
        }

                appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_SCOPE, SFCRUtil.sanitizeName(scopeName));

        sb.append("@scope ").append(SFCRUtil.sanitizeName(scopeName))
          .append(" position=").append(s.getPositionForEmbedded()).append("\n");

        // For embedded scopes, include the ScopeElm geometry and UID
        if (scopeElm != null) {
            sb.append("  x1: ").append(scopeElm.x).append("\n");
            sb.append("  y1: ").append(scopeElm.y).append("\n");
            sb.append("  x2: ").append(scopeElm.x2).append("\n");
            sb.append("  y2: ").append(scopeElm.y2).append("\n");
            String elmUid = scopeElm.getPersistentUid();
            if (elmUid != null && !elmUid.isEmpty()) {
                sb.append("  elmUid: ").append(elmUid).append("\n");
            }
        }

        sb.append("  speed: ").append(s.getSpeedForEmbedded()).append("\n");
        sb.append("  flags: ").append(Scope.exportAsDecOrHex(s.getFlags(), s.FLAG_PERPLOTFLAGS)).append("\n");

        if (s.getTitle() != null && !s.getTitle().isEmpty()) {
            sb.append("  title: ").append(CustomLogicModel.escape(s.getTitle())).append("\n");
        }
        if (s.getText() != null && !s.getText().isEmpty()) {
            sb.append("  label: ").append(CustomLogicModel.escape(s.getText())).append("\n");
        }

        boolean wroteSource = false;
        for (int p = 0; p < s.getPlotCount(); p++) {
            CircuitElm elm = s.getPlotElement(p);
            if (elm == null) {
                continue;
            }
            String uid = elm.getPersistentUid();
            if (uid == null || uid.isEmpty()) {
                continue;
            }
            if (!wroteSource) {
                sb.append("  source: uid:").append(uid).append(" value:").append(s.getPlotValue(p)).append("\n");
                wroteSource = true;
            } else {
                sb.append("  trace: uid:").append(uid).append(" value:").append(s.getPlotValue(p)).append("\n");
            }
        }

        if (!wroteSource) {
            return false;
        }
        sb.append("@end\n");
        return true;
    }
    
    // =========================================================================
    // Helpers
    // =========================================================================

    private String toRAssignmentName(String name) {
        String base = SFCRUtil.sanitizeName(name);
        return toRCodeIdentifier(base);
    }

    private String toRCodeIdentifier(String text) {
        if (text == null || text.length() == 0) {
            return "x";
        }
        String cleaned = text.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.length() == 0) {
            cleaned = "x";
        }
        char first = cleaned.charAt(0);
        if (!(Character.isLetter(first) || first == '_')) {
            cleaned = "x_" + cleaned;
        }
        return cleaned;
    }

    private String makeUniqueRCode(String base, Set<String> usedCodes) {
        String safeBase = (base == null || base.length() == 0) ? "x" : base;
        String candidate = safeBase;
        int suffix = 1;
        while (usedCodes.contains(candidate)) {
            candidate = safeBase + "_" + suffix;
            suffix++;
        }
        usedCodes.add(candidate);
        return candidate;
    }

    private String joinRQuoted(ArrayList<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(escapeRString(values.get(i))).append("\"");
        }
        return sb.toString();
    }

    private String escapeRString(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String formatRBlockMetadataComment(CircuitElm elm, String type) {
        if (elm == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# [ x=").append(elm.x).append(" y=").append(elm.y);
        if (type != null && type.trim().length() > 0) {
            sb.append(" type: ").append(type.trim());
        }
        sb.append(" ]\n");
        return sb.toString();
    }

    private String formatRStyleEquationInlineMetadata(EquationTableElm.RowOutputMode mode,
            String sliderVar, double sliderValue, String initialEq) {
        // Format: [mode=param, slider=foo, sliderValue=0, initial=... ]
        StringBuilder sb = new StringBuilder();
        sb.append("[mode=").append(SFCRUtil.formatEquationRowMode(mode));
        if (sliderVar != null && !sliderVar.trim().isEmpty()) {
            sb.append(", slider=").append(sliderVar.trim());
        }
        sb.append(", sliderValue=").append(sliderValue);
        if (initialEq != null && !initialEq.trim().isEmpty()) {
            sb.append(", initial=").append(initialEq.trim());
        }
        sb.append(" ]");
        return sb.toString();
    }

    /** Append an inline R-style comment from optional hint + metadata payload. */
    private void appendRStyleInlineComment(StringBuilder sb, String hint, String rowMeta) {
        String cleanHint = (hint == null) ? "" : hint.trim();
        String cleanMeta = (rowMeta == null) ? "" : rowMeta.trim();
        if (cleanHint.isEmpty() && cleanMeta.isEmpty()) {
            return;
        }

        sb.append("  #");
        if (!cleanHint.isEmpty()) {
            sb.append(" ").append(cleanHint);
        }
        if (!cleanMeta.isEmpty()) {
            if (!cleanHint.isEmpty()) {
                sb.append("  ");
            } else {
                sb.append(" ");
            }
            sb.append(cleanMeta);
        }
    }

    /**
     * Remove trailing metadata chunks that may have leaked into hints from older
     * malformed imports (e.g. repeated "[mode=voltage ..." fragments).
     */
    private String sanitizeHintForRStyleExport(String hint) {
        if (hint == null) {
            return null;
        }

        String working = hint.trim();
        if (working.isEmpty()) {
            return "";
        }

        while (true) {
            int close = working.lastIndexOf(']');
            if (close != working.length() - 1) {
                break;
            }
            int open = working.lastIndexOf('[', close);
            if (open < 0) {
                break;
            }
            String chunk = working.substring(open + 1, close);
            if (!looksLikeRStyleMetadataChunk(chunk)) {
                break;
            }
            working = working.substring(0, open).trim();
        }

        while (true) {
            int open = working.lastIndexOf('[');
            if (open < 0) {
                break;
            }
            String tail = working.substring(open + 1);
            if (!looksLikeRStyleMetadataChunk(tail)) {
                break;
            }
            working = working.substring(0, open).trim();
        }

        return working;
    }

    /** Heuristic check for comma-separated key=value metadata chunk. */
    private boolean looksLikeRStyleMetadataChunk(String chunk) {
        if (chunk == null) {
            return false;
        }
        String text = chunk.trim();
        if (text.isEmpty()) {
            return false;
        }

        String[] tokens = text.split(",");
        int parsed = 0;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = token.substring(0, eq).trim().toLowerCase();
            if (key.length() == 0) {
                continue;
            }
            if (key.equals("mode") || key.equals("slider") || key.equals("slidervalue") || key.equals("initial")) {
                parsed++;
            }
        }
        return parsed > 0;
    }

    /** Format position string for block header. */
    private String formatPosition(CircuitElm elm) {
        return " x=" + elm.x + " y=" + elm.y;
    }

    private void appendLeadingBlockComments(StringBuilder sb, String blockType, String blockName) {
        if (sim == null || sb == null) {
            return;
        }
        String key = SFCRBlockCommentRegistry.makeKey(blockType, blockName);
        Vector<String> comments = sim.getSFCRDocumentState().getBlockComments(key);
        if (comments == null || comments.size() == 0) {
            return;
        }
        for (int i = 0; i < comments.size(); i++) {
            String line = comments.get(i);
            if (line == null) {
                continue;
            }
            sb.append(line).append("\n");
        }
        sb.append("\n");
    }
}
