/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.io;

import com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.GodlyTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.SFCSankeyElm;
import com.lushprojects.circuitjs1.client.elements.economics.SFCTableElm;
import com.lushprojects.circuitjs1.client.elements.misc.ActionTimeElm;
import com.lushprojects.circuitjs1.client.elements.misc.ScopeElm;

import java.util.ArrayList;
import java.util.HashMap;

import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockExportHandlerRegistry;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRTemplateMerger;
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

    // Lookup state kept here for test backward-compatibility (accessed via reflection in unit tests).
    // After syncLookupStateToContext(), the context's lookup collections are aliased to these same
    // objects so modifications through the context are immediately visible here and vice versa.
    private ArrayList<EquationTableElm> equationTables = new ArrayList<EquationTableElm>();
    private ArrayList<GodlyTableElm> godlyTables = new ArrayList<GodlyTableElm>();
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
        SFCRExportContext ctx = new SFCRExportContext(sim, exportSyntax);
        ctx.clearScopeElmsExportedAsBlocks();
        categorizeElements(ctx);
        resetLookupExportState();

        if (sim != null && sim.getSFCRDocumentManager().getModelInfoSourceText() != null
                && !sim.getSFCRDocumentManager().getModelInfoSourceText().trim().isEmpty()) {
            seedLookupNamesFromTemplate(sim.getSFCRDocumentManager().getModelInfoSourceText());
            syncLookupStateToContext(ctx);
            String merged = SFCRTemplateMerger.export(
                    sim.getSFCRDocumentManager().getModelInfoSourceText(), ctx);
            if (merged != null && !merged.trim().isEmpty()) {
                return SFCRTemplateMerger.normalizeBlankLinesOutsideFences(merged);
            }
        }

        syncLookupStateToContext(ctx);

        StringBuilder sb = new StringBuilder();
        sb.append("# CircuitJS1 SFCR Export\n");
        sb.append("# Generated from circuit simulation\n");
        sb.append("\n");

        for (SFCRBlockExportHandler handler : SFCRBlockExportHandlerRegistry.getOrderedHandlers()) {
            ctx.appendExportBlock(sb, handler.export(ctx));
        }

        String inlineDocs = SFCRTemplateMerger.exportInlineDocumentation(sim);
        if (!inlineDocs.isEmpty()) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                sb.append("\n");
            }
            sb.append("\n");
            sb.append(inlineDocs);
        }

        return SFCRTemplateMerger.normalizeBlankLinesOutsideFences(sb.toString());
    }

    // =========================================================================
    // Element categorization
    // =========================================================================

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

        // Keep aliases for isEquationNameTakenInScope (called during lookup naming)
        equationTables = eqTables;
        godlyTables = godlyTableList;
    }

    // =========================================================================
    // Lookup state management
    // =========================================================================

    private void resetLookupExportState() {
        lookupExportSpecs.clear();
        lookupExportBySignature.clear();
        lookupCommentsByNameScope.clear();
    }

    /** Alias the context's lookup collections to this exporter's so handlers read/write the same data. */
    private void syncLookupStateToContext(SFCRExportContext ctx) {
        ctx.setLookupExportSpecs(lookupExportSpecs);
        ctx.setLookupExportBySignature(lookupExportBySignature);
        ctx.setLookupCommentsByNameScope(lookupCommentsByNameScope);
    }

    // Used by tests via reflection.
    private String exportLookupBlocks() {
        SFCRExportContext ctx = new SFCRExportContext(sim, exportSyntax);
        syncLookupStateToContext(ctx);
        return SFCRTemplateMerger.renderBlocksForType(SFCRBlockType.LOOKUP, ctx);
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

    private String rewriteExpressionForLookupExport(String expr, String scopeName) {
        if (expr == null || expr.trim().isEmpty()) {
            return expr;
        }
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

    private static String buildLookupNameScopeKey(String name, String scopeName) {
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
                com.lushprojects.circuitjs1.client.elements.economics.TableColumn column = table.getColumn(c);
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

    private static String buildLookupSignatureFromPoints(String scopeName, ArrayList<Double> xs, ArrayList<Double> ys) {
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

    private static int findFunctionCall(String expr, String name, int fromIndex) {
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

    private static int findMatchingParenForExport(String text, int openIndex) {
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

    private static ArrayList<String> splitTopLevelArgs(String text) {
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

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '\\' || c == '^' || c == '{' || c == '}' || c == '.';
    }
}
