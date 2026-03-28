package com.lushprojects.circuitjs1.client.io.sfcr;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.elements.ActionScheduler;
import com.lushprojects.circuitjs1.client.elements.misc.ActionTimeElm;
import com.lushprojects.circuitjs1.client.io.LookupDefinition;
import com.lushprojects.circuitjs1.client.io.LookupTableRegistry;
import com.lushprojects.circuitjs1.client.io.SFCRBlockCommentRegistry;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.SFCRParseResult;
import com.lushprojects.circuitjs1.client.io.SFCRTableDumpBuilderService;
import com.lushprojects.circuitjs1.client.io.SFCRUtil;
import com.lushprojects.circuitjs1.client.io.RStyleParseService;
import com.lushprojects.circuitjs1.client.registry.ElementFactoryFacade;
import com.lushprojects.circuitjs1.client.util.StringTokenizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

/**
 * Context object for SFCR parsing that owns all mutable parse state.
 * 
 * This class centralizes state management during parsing, eliminating the need
 * for bridge methods between SFCRParser and handlers. Handlers access state
 * directly through this context.
 */
public class SFCRParseContext {
    // =========================================================================
    // Core References (immutable during parse)
    // =========================================================================
    
    private final CirSim sim;
    private final SFCRTableDumpBuilderService tableDumpBuilderService;
    private final RStyleParseService rStyleParseService;
    
    // =========================================================================
    // Mutable Parse State
    // =========================================================================
    
    // Position tracking for element placement
    private int currentX = 176;
    private int currentY = 24;
    private int elementSpacing = 16;
    
    // Created elements during this parse
    private final ArrayList<CircuitElm> createdElements = new ArrayList<CircuitElm>();
    
    // Hints (variable name -> description)
    private final HashMap<String, String> hints = new HashMap<String, String>();
    
    // Scope configuration
    private final ArrayList<String> scopeVariables = new ArrayList<String>();
    private final ArrayList<SFCRParser.ScopeBlockSpec> scopeBlocks = new ArrayList<SFCRParser.ScopeBlockSpec>();
    
    // Init settings
    private final HashMap<String, String> initSettings = new HashMap<String, String>();
    
    // Raw circuit lines for passthrough
    private final ArrayList<String> rawCircuitLines = new ArrayList<String>();
    
    // Lookup tables
    private final HashMap<String, LookupDefinition> globalLookupTables = new HashMap<String, LookupDefinition>();
    private final HashMap<String, HashMap<String, LookupDefinition>> scopedLookupTables = 
        new HashMap<String, HashMap<String, LookupDefinition>>();
    private boolean lookupClampDefault = true;
    
    // Info content (markdown)
    private String infoContent = null;
    
    // Action element state
    private boolean actionElementFromActionBlock = false;
    
    // Parse warnings
    private final ArrayList<ParseWarning> warnings = new ArrayList<ParseWarning>();
    
    // Result-mode output (null when instantiating elements, non-null in parseToResult mode)
    private SFCRParseResult pendingResult = null;

    // =========================================================================
    // Constructor
    // =========================================================================
    
    /**
     * Create a new parse context.
     * 
     * @param sim The simulator instance (may be null for result-mode parsing)
     * @param tableDumpBuilderService Service for building element dump strings
     * @param rStyleParseService Service for R-style metadata parsing
     */
    public SFCRParseContext(CirSim sim, SFCRTableDumpBuilderService tableDumpBuilderService,
                            RStyleParseService rStyleParseService) {
        this.sim = sim;
        this.tableDumpBuilderService = tableDumpBuilderService;
        this.rStyleParseService = rStyleParseService;
        this.lookupClampDefault = (sim == null) ? true : sim.isSfcrLookupClampDefault();
    }

    // =========================================================================
    // Position Tracking
    // =========================================================================
    
    public int getCurrentX() {
        return currentX;
    }
    
    public int getCurrentY() {
        return currentY;
    }
    
    public void setCurrentPosition(int x, int y) {
        this.currentX = x;
        this.currentY = y;
    }
    
    public void advanceY(int amount) {
        this.currentY += amount;
    }
    
    public int getElementSpacing() {
        return elementSpacing;
    }

    // =========================================================================
    // Element Tracking
    // =========================================================================
    
    public void addCreatedElement(CircuitElm elm) {
        if (elm != null) {
            createdElements.add(elm);
        }
    }
    
    public ArrayList<CircuitElm> getCreatedElements() {
        return createdElements;
    }

    // =========================================================================
    // Hints
    // =========================================================================
    
    public void registerHint(String varName, String description) {
        if (varName != null && !varName.isEmpty()) {
            hints.put(varName, description);
        }
    }
    
    public boolean hasHint(String varName) {
        return hints.containsKey(varName);
    }
    
    public HashMap<String, String> getHints() {
        return hints;
    }

    // =========================================================================
    // Scope Configuration
    // =========================================================================
    
    public void addScopeVariable(String varName) {
        if (varName != null) {
            String normalized = SFCRUtil.normalizeVariableName(varName);
            if (!normalized.isEmpty()) {
                scopeVariables.add(normalized);
            }
        }
    }
    
    public ArrayList<String> getScopeVariables() {
        return scopeVariables;
    }
    
    public void addScopeBlock(SFCRParser.ScopeBlockSpec spec) {
        if (spec != null && spec.traces != null && !spec.traces.isEmpty()) {
            scopeBlocks.add(spec);
        }
    }
    
    public ArrayList<SFCRParser.ScopeBlockSpec> getScopeBlocks() {
        return scopeBlocks;
    }

    // =========================================================================
    // Init Settings
    // =========================================================================
    
    public void registerInitSetting(String key, String value) {
        if (key != null && !key.isEmpty()) {
            initSettings.put(key, value);
            applyLookupInitAlias(key, value, true);
        }
    }
    
    public void parseInitInline(String params) {
        if (params == null || params.isEmpty()) return;
        String[] parts = params.split("\\s+");
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx < 0) idx = part.indexOf(':');
            if (idx > 0) {
                String key = part.substring(0, idx).trim();
                String value = part.substring(idx + 1).trim();
                registerInitSetting(key, value);
            }
        }
    }
    
    public HashMap<String, String> getInitSettings() {
        return initSettings;
    }
    
    private void applyLookupInitAlias(String key, String value, boolean storeCanonicalMode) {
        if (key == null || value == null) return;
        
        if (key.equals("lookupClamp")) {
            boolean clamp = parseBoolean(value, lookupClampDefault);
            lookupClampDefault = clamp;
            if (storeCanonicalMode) {
                initSettings.put("lookupMode", clamp ? "pwl" : "pwlx");
            }
            return;
        }
        
        if (key.equals("lookupMode")) {
            String mode = normalizeLookupMode(value);
            lookupClampDefault = mode.equals("pwl");
            if (storeCanonicalMode) {
                initSettings.put("lookupMode", mode);
            }
        }
    }
    
    private String normalizeLookupMode(String rawMode) {
        String mode = (rawMode == null) ? "" : rawMode.trim().toLowerCase();
        if (mode.equals("pwl") || mode.equals("clamp") || mode.equals("clamped") || mode.equals("bounded")) {
            return "pwl";
        }
        if (mode.equals("pwlx") || mode.equals("extrapolate") || mode.equals("extrapolating") || mode.equals("linear")) {
            return "pwlx";
        }
        return lookupClampDefault ? "pwl" : "pwlx";
    }

    // =========================================================================
    // Raw Circuit Lines
    // =========================================================================
    
    public void addRawCircuitLine(String line) {
        if (line != null) {
            rawCircuitLines.add(line);
        }
    }
    
    public ArrayList<String> getRawCircuitLines() {
        return rawCircuitLines;
    }

    // =========================================================================
    // Lookup Tables
    // =========================================================================
    
    public LookupDefinition parseLookupHeader(String headerLine) {
        String header = (headerLine == null ? "" : headerLine.trim())
            .substring("@lookup".length()).trim();
        if (header.isEmpty()) return null;
        
        String lookupName = null;
        String scopeName = null;
        String[] tokens = header.split("\\s+");
        
        for (String token : tokens) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            
            int eq = t.indexOf('=');
            if (eq > 0) {
                String key = t.substring(0, eq).trim().toLowerCase();
                String val = t.substring(eq + 1).trim();
                if ("scope".equals(key) || "local".equals(key) || "equations".equals(key) || "table".equals(key)) {
                    scopeName = SFCRUtil.normalizeVariableName(val);
                }
            } else if (lookupName == null) {
                lookupName = SFCRUtil.normalizeVariableName(t);
            }
        }
        
        if (lookupName == null || lookupName.isEmpty()) return null;
        
        LookupDefinition def = new LookupDefinition();
        def.name = lookupName;
        def.scope = (scopeName == null || scopeName.isEmpty()) ? null : scopeName;
        return def;
    }
    
    public SFCRParser.LookupPoint parseLookupPoint(String line) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return null;
        
        String[] parts;
        if (trimmed.startsWith("|")) {
            String[] cells = parseTableRow(trimmed);
            if (cells.length < 2) return null;
            String c0 = cells[0].trim().toLowerCase();
            String c1 = cells[1].trim().toLowerCase();
            // Skip header row
            if (("x".equals(c0) || "ratio".equals(c0)) &&
                ("y".equals(c1) || "value".equals(c1) || "multiplier".equals(c1))) {
                return null;
            }
            parts = new String[] { cells[0], cells[1] };
        } else if (trimmed.contains(",")) {
            parts = trimmed.split(",", 2);
        } else {
            parts = trimmed.split("\\s+");
            if (parts.length < 2) return null;
        }
        
        if (parts.length < 2) return null;
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            return new SFCRParser.LookupPoint(x, y);
        } catch (Exception e) {
            return null;
        }
    }
    
    public boolean isStrictlyIncreasing(ArrayList<Double> xs) {
        if (xs == null || xs.size() < 2) return false;
        for (int i = 1; i < xs.size(); i++) {
            if (xs.get(i).doubleValue() <= xs.get(i - 1).doubleValue()) {
                return false;
            }
        }
        return true;
    }
    
    public void registerLookupTable(LookupDefinition table) {
        if (table == null || table.name == null || table.name.isEmpty()) return;
        
        String scopeName = table.scope;
        
        // Check if already registered (idempotent)
        if (scopeName == null || scopeName.isEmpty()) {
            if (globalLookupTables.containsKey(table.name)) return;
            globalLookupTables.put(table.name, table);
        } else {
            HashMap<String, LookupDefinition> byScope = scopedLookupTables.get(scopeName);
            if (byScope == null) {
                byScope = new HashMap<String, LookupDefinition>();
                scopedLookupTables.put(scopeName, byScope);
            } else if (byScope.containsKey(table.name)) {
                return;  // Already registered
            }
            byScope.put(table.name, table);
        }
        LookupTableRegistry.register(table);
        
        // In result-mode, add to blockDumps
        if (pendingResult != null) {
            String blockName = (scopeName == null || scopeName.isEmpty()) 
                ? table.name : (scopeName + ":" + table.name);
            StringBuilder dump = new StringBuilder();
            dump.append("lookup ").append(table.name);
            if (scopeName != null && !scopeName.isEmpty()) {
                dump.append(" scope=").append(scopeName);
            }
            for (int p = 0; p < table.xs.size(); p++) {
                dump.append(" ").append(table.xs.get(p).doubleValue())
                    .append(",").append(table.ys.get(p).doubleValue());
            }
            pendingResult.blockDumps.add(new SFCRParseResult.BlockDump("lookup", blockName, dump.toString()));
        }
    }
    
    public LookupDefinition findLookupTable(String equationScope, String lookupName) {
        if (lookupName == null || lookupName.isEmpty()) return null;
        String normalizedName = SFCRUtil.normalizeVariableName(lookupName);
        
        if (equationScope != null && !equationScope.isEmpty()) {
            HashMap<String, LookupDefinition> local = scopedLookupTables.get(equationScope);
            if (local != null) {
                LookupDefinition t = local.get(normalizedName);
                if (t != null) return t;
            }
        }
        return globalLookupTables.get(normalizedName);
    }
    
    public HashMap<String, LookupDefinition> getGlobalLookupTables() {
        return globalLookupTables;
    }
    
    public HashMap<String, HashMap<String, LookupDefinition>> getScopedLookupTables() {
        return scopedLookupTables;
    }
    
    public boolean isLookupClampDefault() {
        return lookupClampDefault;
    }
    
    public String rewriteLookupCalls(String expr, String equationScope) {
        if (expr == null || expr.trim().isEmpty()) return expr;
        return rewriteNamedLookupCalls(expr, equationScope);
    }
    
    private String rewriteNamedLookupCalls(String expr, String equationScope) {
        String out = expr;
        int i = 0;
        while (i < out.length()) {
            char c = out.charAt(i);
            if (!isLookupNameStart(c)) {
                i++;
                continue;
            }
            
            int start = i;
            i++;
            while (i < out.length() && isLookupNamePart(out.charAt(i))) {
                i++;
            }
            String identifier = out.substring(start, i);
            LookupDefinition table = findLookupTable(equationScope, identifier);
            if (table == null || "lookup".equalsIgnoreCase(identifier)) {
                continue;
            }
            
            int j = i;
            while (j < out.length() && Character.isWhitespace(out.charAt(j))) {
                j++;
            }
            if (j >= out.length() || out.charAt(j) != '(') {
                continue;
            }
            
            int close = findMatchingParen(out, j);
            if (close < 0) break;
            
            String argExpr = out.substring(j + 1, close).trim();
            String replacement = "lookup(" + identifier + ", " + argExpr + ")";
            out = out.substring(0, start) + replacement + out.substring(close + 1);
            i = start + replacement.length();
        }
        return out;
    }
    
    private boolean isLookupNameStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '\\';
    }
    
    private boolean isLookupNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '\\' || c == '^' || c == '{' || c == '}' || c == '.';
    }
    
    private int findMatchingParen(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    // =========================================================================
    // Info Content
    // =========================================================================
    
    public void setInfoContent(String content) {
        this.infoContent = content;
    }
    
    public String getInfoContent() {
        return infoContent;
    }

    // =========================================================================
    // Action Element State
    // =========================================================================
    
    public void setActionElementFromActionBlock(boolean value) {
        this.actionElementFromActionBlock = value;
    }
    
    public boolean isActionElementFromActionBlock() {
        return actionElementFromActionBlock;
    }

    // =========================================================================
    // Parse Warnings
    // =========================================================================
    
    public void addWarning(int line, String message) {
        warnings.add(new ParseWarning(line, message));
    }
    
    public List<ParseWarning> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    // =========================================================================
    // Result Mode (parseToResult)
    // =========================================================================
    
    public void setPendingResult(SFCRParseResult result) {
        this.pendingResult = result;
    }
    
    public SFCRParseResult getPendingResult() {
        return pendingResult;
    }
    
    public boolean hasPendingResult() {
        return pendingResult != null;
    }
    
    public void addBlockDump(String blockType, String blockName, String dumpString) {
        if (pendingResult != null && blockType != null && dumpString != null) {
            pendingResult.blockDumps.add(new SFCRParseResult.BlockDump(blockType, blockName, dumpString));
        }
    }

    // =========================================================================
    // CirSim Access
    // =========================================================================
    
    public CirSim getSim() {
        return sim;
    }
    
    public ActionScheduler getActionScheduler() {
        if (sim == null) return null;
        return ActionScheduler.getInstance(sim);
    }
    
    public ActionTimeElm findActionTimeElm() {
        if (sim == null) return null;
        for (int idx = 0; idx < sim.elmList.size(); idx++) {
            CircuitElm ce = sim.getElm(idx);
            if (ce instanceof ActionTimeElm) {
                return (ActionTimeElm) ce;
            }
        }
        return null;
    }

    // =========================================================================
    // Parsing Utilities (stateless - delegated to SFCRUtil or inline)
    // =========================================================================
    
    public String normalizeVariableName(String rawName) {
        return SFCRUtil.normalizeVariableName(rawName);
    }
    
    public String normalizeExpression(String rawExpr) {
        return SFCRUtil.normalizeExpression(rawExpr);
    }
    
    public String unescapeTableCell(String text) {
        return SFCRUtil.unescapeTableCell(text);
    }
    
    public boolean parseBoolean(String text, boolean defaultValue) {
        if (text == null) return defaultValue;
        String t = text.trim().toLowerCase();
        if (t.equals("true") || t.equals("1") || t.equals("yes")) return true;
        if (t.equals("false") || t.equals("0") || t.equals("no")) return false;
        return defaultValue;
    }
    
    public SFCRParser.BlockHeaderInfo parseBlockHeader(String line, String keyword) {
        String rest = line.substring(keyword.length()).trim();
        SFCRParser.BlockHeaderInfo info = new SFCRParser.BlockHeaderInfo();
        info.name = keyword.substring(1); // Default name is keyword without @
        
        if (rest.isEmpty()) return info;
        
        StringBuilder nameBuilder = new StringBuilder();
        String[] parts = rest.split("\\s+");
        
        for (String part : parts) {
            if (part.toLowerCase().startsWith("x=")) {
                try {
                    info.x = Integer.parseInt(part.substring(2));
                } catch (NumberFormatException e) { /* ignore */ }
            } else if (part.toLowerCase().startsWith("y=")) {
                try {
                    info.y = Integer.parseInt(part.substring(2));
                } catch (NumberFormatException e) { /* ignore */ }
            } else {
                if (nameBuilder.length() > 0) nameBuilder.append(" ");
                nameBuilder.append(part);
            }
        }
        
        if (nameBuilder.length() > 0) {
            info.name = nameBuilder.toString();
        }
        
        return info;
    }
    
    public String[] parseTableRow(String line) {
        String l = line;
        if (l.startsWith("|")) l = l.substring(1);
        if (l.endsWith("|")) l = l.substring(0, l.length() - 1);
        
        String[] parts = l.split("\\|", -1);
        ArrayList<String> cells = new ArrayList<String>();
        for (String part : parts) {
            cells.add(part.trim());
        }
        
        // Remove trailing empty cells
        while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
            cells.remove(cells.size() - 1);
        }
        
        return cells.toArray(new String[0]);
    }
    
    public String[] splitDifferenceLeftAlias(String left) {
        if (left == null) return new String[] { "", null };
        
        String trimmed = left.trim();
        if (trimmed.isEmpty()) return new String[] { "", null };
        
        int minusIdx = trimmed.indexOf('-');
        if (minusIdx <= 0) return new String[] { trimmed, null };
        
        // Don't treat flow target separators as alias subtraction
        if (minusIdx + 1 < trimmed.length() && trimmed.charAt(minusIdx + 1) == '>') {
            return new String[] { trimmed, null };
        }
        if (trimmed.indexOf("-||-") >= 0) {
            return new String[] { trimmed, null };
        }
        
        String candidateName = trimmed.substring(0, minusIdx).trim();
        String candidateOffset = trimmed.substring(minusIdx + 1).trim();
        
        if (candidateOffset.isEmpty() || !looksLikeSimpleVariableName(candidateName)) {
            return new String[] { trimmed, null };
        }
        
        return new String[] { candidateName, candidateOffset };
    }
    
    private boolean looksLikeSimpleVariableName(String name) {
        if (name == null || name.isEmpty()) return false;
        
        char first = name.charAt(0);
        if (!(Character.isLetter(first) || first == '_' || first == '\\')) return false;
        
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '\\' || c == '^' || c == '{' || c == '}' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // R-Style Support
    // =========================================================================
    
    public SFCRParser.RStyleBlockMetadata consumeRStyleMetadataFromComments(Vector<String> pendingComments) {
        return rStyleParseService.consumeMetadataFromComments(pendingComments);
    }
    
    public String extractRStyleAssignmentName(String block, String defaultName) {
        if (block == null) return defaultName;
        int assignIdx = block.indexOf("<-");
        if (assignIdx > 0) {
            String name = block.substring(0, assignIdx).trim();
            if (!name.isEmpty()) return name;
        }
        return defaultName;
    }

    // =========================================================================
    // Block Comment Storage
    // =========================================================================
    
    public void storePendingMatrixBlockComments(String blockName, Vector<String> pendingComments) {
        storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_MATRIX, blockName, pendingComments);
    }
    
    public void storePendingEquationsBlockComments(String blockName, Vector<String> pendingComments) {
        storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_EQUATIONS, blockName, pendingComments);
    }
    
    private void storePendingBlockComments(String blockType, String blockName, Vector<String> pendingComments) {
        if (pendingComments == null || pendingComments.isEmpty()) return;
        
        String key = SFCRBlockCommentRegistry.makeKey(blockType, blockName);
        if (pendingResult != null) {
            pendingResult.blockComments.put(key, new ArrayList<String>(pendingComments));
        } else if (sim != null) {
            sim.getSFCRDocumentState().setBlockComments(key, pendingComments);
        }
        pendingComments.clear();
    }

    // =========================================================================
    // Circuit Block Helpers
    // =========================================================================
    
    public String parseLookupDumpLineFromCircuit(String line) {
        if (line == null) return null;
        
        String trimmed = line.trim();
        if (!trimmed.startsWith("lookup ")) return null;
        
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 4) return null;
        
        String lookupName = SFCRUtil.normalizeVariableName(tokens[1]);
        if (lookupName == null || lookupName.isEmpty()) return null;
        
        String scopeName = null;
        LookupDefinition table = new LookupDefinition();
        table.name = lookupName;
        
        for (int j = 2; j < tokens.length; j++) {
            String token = tokens[j];
            if (token == null) continue;
            
            String part = token.trim();
            if (part.isEmpty()) continue;
            
            if (part.startsWith("scope=")) {
                String scopeToken = part.substring(6).trim();
                if (!scopeToken.isEmpty()) {
                    scopeName = SFCRUtil.normalizeVariableName(scopeToken);
                }
                continue;
            }
            
            SFCRParser.LookupPoint point = parseLookupPoint(part);
            if (point != null) {
                table.xs.add(Double.valueOf(point.x));
                table.ys.add(Double.valueOf(point.y));
            }
        }
        
        if (table.xs.size() < 2) return null;
        if (!isStrictlyIncreasing(table.xs)) return null;
        
        if (scopeName == null || scopeName.isEmpty()) {
            globalLookupTables.put(table.name, table);
            LookupTableRegistry.register(table);
            return table.name;
        }
        
        table.scope = scopeName;
        HashMap<String, LookupDefinition> byScope = scopedLookupTables.get(scopeName);
        if (byScope == null) {
            byScope = new HashMap<String, LookupDefinition>();
            scopedLookupTables.put(scopeName, byScope);
        }
        byScope.put(table.name, table);
        LookupTableRegistry.register(table);
        return scopeName + ":" + table.name;
    }
    
    public String inferBlockTypeFromCircuitDumpLine(String line) {
        if (line == null || line.isEmpty()) return null;
        
        int space = line.indexOf(' ');
        String token = (space > 0) ? line.substring(0, space) : line;
        
        int dumpType;
        try {
            dumpType = Integer.parseInt(token);
        } catch (Exception e) {
            return null;
        }
        
        if (dumpType == 266) return "equations";
        if (dumpType == 265) return "matrix";
        if (dumpType == 466) return "sankey";
        if (dumpType == 432) return "action";
        return null;
    }
    
    public boolean looksLikeScopeBlock(String[] lines, int startIndex) {
        int i = startIndex + 1;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }
            if (line.equals("@end")) return true;
            if (line.startsWith("@")) return false;
            return true;
        }
        return false;
    }

    // =========================================================================
    // Element Creation
    // =========================================================================
    
    public void createEquationTable(String name, ArrayList<String> outputNames,
                                    ArrayList<String> equations, ArrayList<Integer> outputModes,
                                    ArrayList<String> targetNodeNames, ArrayList<String> sliderVarNames,
                                    ArrayList<Double> sliderValues, ArrayList<String> initialEquations,
                                    Boolean invisibleOverride) {
        SFCRTableDumpBuilderService.DumpBuildResult build = tableDumpBuilderService.buildEquationDump(
            name, currentX, currentY, outputNames, equations, outputModes, targetNodeNames,
            sliderVarNames, sliderValues, initialEquations, invisibleOverride);
        
        if (build == null) return;
        
        String dumpString = build.dump;
        int y2 = build.y2;
        
        // Result-mode: collect dump without instantiating elements
        if (pendingResult != null) {
            pendingResult.blockDumps.add(new SFCRParseResult.BlockDump("equations", name, dumpString));
            currentY = y2 + elementSpacing;
            return;
        }
        
        // Create element by parsing the dump string
        try {
            StringTokenizer st = new StringTokenizer(dumpString);
            int type = Integer.parseInt(st.nextToken());
            int xa = Integer.parseInt(st.nextToken());
            int ya = Integer.parseInt(st.nextToken());
            int xb = Integer.parseInt(st.nextToken());
            int yb = Integer.parseInt(st.nextToken());
            int flags = Integer.parseInt(st.nextToken());
            
            CircuitElm ce = ElementFactoryFacade.createFromDumpType(type, xa, ya, xb, yb, flags, st);
            if (ce != null) {
                ce.setPointsForImportExport();
                sim.getImportExportHelper().assignPersistentUid(ce, null);
                sim.elmList.addElement(ce);
                createdElements.add(ce);
                currentY = yb + elementSpacing;
            } else {
                CirSim.console("SFCRParser: Failed to instantiate equation table '" + name + "'");
            }
        } catch (Exception e) {
            CirSim.console("Error creating equation table '" + name + "': " + e.toString());
        }
    }
    
    public void createMatrixTable(String name, ArrayList<String> columnNames,
                                  ArrayList<String> rowNames, ArrayList<String[]> tableRows,
                                  String matrixType, Boolean showInitialValuesOverride,
                                  Boolean showFlowValuesOverride, Boolean useBackwardEulerOverride,
                                  Boolean invisibleOverride) {
        SFCRTableDumpBuilderService.DumpBuildResult build = tableDumpBuilderService.buildMatrixDump(
            name, currentX, currentY, columnNames, rowNames, tableRows, showInitialValuesOverride,
            invisibleOverride);
        
        if (build == null) {
            CirSim.console("SFCRParser: Skipping matrix table '" + name + "' - invalid table shape");
            return;
        }
        
        String dumpString = build.dump;
        int y2 = build.y2;
        
        // Result-mode: collect dump without instantiating elements
        if (pendingResult != null) {
            pendingResult.blockDumps.add(new SFCRParseResult.BlockDump("matrix", name, dumpString));
            currentY = y2 + elementSpacing;
            return;
        }
        
        CirSim.console("Creating SFCTable: " + name);
        
        try {
            StringTokenizer st = new StringTokenizer(dumpString);
            int type = Integer.parseInt(st.nextToken());
            int xa = Integer.parseInt(st.nextToken());
            int ya = Integer.parseInt(st.nextToken());
            int xb = Integer.parseInt(st.nextToken());
            int yb = Integer.parseInt(st.nextToken());
            int flags = Integer.parseInt(st.nextToken());
            
            CircuitElm ce = ElementFactoryFacade.createFromDumpType(type, xa, ya, xb, yb, flags, st);
            if (ce != null) {
                ce.setPointsForImportExport();
                sim.getImportExportHelper().assignPersistentUid(ce, null);
                sim.elmList.addElement(ce);
                createdElements.add(ce);
                currentY = yb + elementSpacing;
            } else {
                CirSim.console("SFCRParser: Failed to instantiate matrix table '" + name + "'");
            }
        } catch (Exception e) {
            CirSim.console("Error creating matrix table '" + name + "': " + e.toString());
            e.printStackTrace();
        }
    }
}
