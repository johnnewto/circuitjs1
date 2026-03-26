/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.io;

import com.lushprojects.circuitjs1.client.scope.Scope;

import com.lushprojects.circuitjs1.client.elements.ActionScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.registry.HintRegistry;
import com.lushprojects.circuitjs1.client.util.*;
import com.lushprojects.circuitjs1.client.elements.economics.*;
import com.lushprojects.circuitjs1.client.elements.misc.*;
import com.lushprojects.circuitjs1.client.registry.ElementFactoryFacade;
import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.ParseWarning;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockParseHandlerRegistry;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.SFCRBlockParseHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.UnknownBlockParseHandler;

/**
 * Parser for SFCR-compatible text format.
 * 
 * Parses human-readable Stock-Flow Consistent model definitions inspired by
 * the R sfcr package (https://github.com/joaomacalos/sfcr).
 * 
 * Supported blocks:
 *   @init       - Simulation settings (timestep, units, display options)
 *   @action     - Action Time schedule (timed target updates)
 *   @info       - Model documentation (markdown)
 *   @equations  - Variable equations (creates EquationTableElm)
 *   @parameters - Alias for @equations (sfcr compatibility)
 *   @lookup     - Named lookup tables for equation interpolation
 *   @matrix     - Transaction flow matrices (creates SFCTableElm)
 *   @hints      - Variable tooltips (overrides inline comments)
 *   @scope      - Oscilloscope displays
 *   @circuit    - Raw CircuitJS element passthrough
 * 
 * Inline comments become hints:
 *   YD ~ W * N_s - T_s   # Disposable income
 * 
 * @see SFCRExporter
 * @see <a href="../dev_docs/SFCR_FORMAT_REFERENCE.md">SFCR Format Reference</a>
 */
public class SFCRParser {
    
    // =========================================================================
    // Helper class for block position parsing
    // =========================================================================
    
    /** Parsed block header with optional position. */
    private static class BlockPosition {
        String name;
        int x = Integer.MIN_VALUE;  // MIN_VALUE means auto-position (not set)
        int y = Integer.MIN_VALUE;
        
        BlockPosition(String name) {
            this.name = name;
        }
        
        boolean hasPosition() {
            return x != Integer.MIN_VALUE && y != Integer.MIN_VALUE;
        }
    }

    public static class BlockHeaderInfo {
        public String name;
        public int x = Integer.MIN_VALUE;
        public int y = Integer.MIN_VALUE;

        public boolean hasPosition() {
            return x != Integer.MIN_VALUE && y != Integer.MIN_VALUE;
        }
    }

    /** Parsed scope trace reference. */
    public static class ScopeTraceSpec {
        public String uid;
        public int value = Scope.VAL_VOLTAGE;
    }

    /** Parsed @scope block configuration. */
    public static class ScopeBlockSpec {
        public String name = "scope";
        public int position = 0;
        public int speed = -1;
        public int flags = Integer.MIN_VALUE;
        public String title;
        public String label;
        public ArrayList<ScopeTraceSpec> traces = new ArrayList<ScopeTraceSpec>();
        // Embedded scope geometry (from ScopeElm export)
        public int x1 = Integer.MIN_VALUE;
        public int y1 = Integer.MIN_VALUE;
        public int x2 = Integer.MIN_VALUE;
        public int y2 = Integer.MIN_VALUE;
        public String elmUid;

        public boolean hasGeometry() {
            return x1 != Integer.MIN_VALUE && y1 != Integer.MIN_VALUE
                && x2 != Integer.MIN_VALUE && y2 != Integer.MIN_VALUE;
        }
    }

    /** Parsed metadata from R-style comment: # [ x=.. y=.. type: .. ] */
    public static class RStyleBlockMetadata {
        int x = Integer.MIN_VALUE;
        int y = Integer.MIN_VALUE;
        String type;

        boolean hasPosition() {
            return x != Integer.MIN_VALUE && y != Integer.MIN_VALUE;
        }
    }

    // =========================================================================
    // Fields
    // =========================================================================
    
    private CirSim sim;
    private int currentX = 176;
    private int currentY = 24;
    private int elementSpacing = 16;
    
    private ArrayList<CircuitElm> createdElements = new ArrayList<CircuitElm>();
    private HashMap<String, String> hints = new HashMap<String, String>();
    private ArrayList<String> scopeVariables = new ArrayList<String>();
    private ArrayList<ScopeBlockSpec> scopeBlocks = new ArrayList<ScopeBlockSpec>();
    private HashMap<String, String> initSettings = new HashMap<String, String>();
    private ArrayList<String> rawCircuitLines = new ArrayList<String>();
    private HashMap<String, LookupDefinition> globalLookupTables = new HashMap<String, LookupDefinition>();
    private HashMap<String, HashMap<String, LookupDefinition>> scopedLookupTables =
        new HashMap<String, HashMap<String, LookupDefinition>>();
    private boolean lookupClampDefault = true;
    private String infoContent = null;
    private boolean actionElementFromActionBlock = false;
    private ArrayList<ParseWarning> parseWarnings = new ArrayList<ParseWarning>();
    private final UnknownBlockParseHandler unknownBlockParseHandler = new UnknownBlockParseHandler();
    private final RStyleParseService rStyleParseService = new RStyleParseService();
    private final SFCRTableDumpBuilderService tableDumpBuilderService = new SFCRTableDumpBuilderService();

    /** Non-null when in result-mode ({@link #parseToResult}). */
    private SFCRParseResult pendingResult = null;

    // =========================================================================
    // GWT-independent helpers (usable from plain-Java unit tests)
    // =========================================================================

    /**
     * Map an SFCR mode keyword to its EquationTableElm dump ordinal.
     * Mirrors SFCRUtil.parseEquationRowMode without loading EquationTableElm.
     */
    public static int parseModeOrdinal(String mode) {
        if (mode == null) return 0;
        String m = mode.toLowerCase().trim();
        if (m.equals("flow") || m.equals("flow_mode") || m.equals("stock") || m.equals("stock_mode")) return 1;
        if (m.equals("param") || m.equals("parameter") || m.equals("param_mode")) return 3;
        return 0;
    }

    /**
     * Escape a token for the CircuitJS dump format.
     * Mirrors CustomLogicModel.escape() without loading that class.
     */
    public static String escapeToken(String s) {
        if (s.length() == 0) return "\\0";
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace(" ", "\\s")
                .replace("+", "\\p").replace("=", "\\q").replace("#", "\\h")
                .replace("&", "\\a").replace("\r", "\\r");
    }

    /**
     * Parse a combined "name-&gt;target" notation.
     * Mirrors EquationTableElm.parseCombinedName() without loading that class.
     */
    public static String[] parseCombinedNameLocal(String combined) {
        if (combined == null) return new String[]{"", ""};
        int arrowIdx = combined.indexOf("->");
        int sepLen = 2;
        if (arrowIdx < 0) { arrowIdx = combined.indexOf("-||-"); sepLen = 4; }
        if (arrowIdx < 0) { arrowIdx = combined.indexOf("\u2192"); sepLen = 1; }   // \u2192 = →
        if (arrowIdx < 0) { arrowIdx = combined.indexOf("\u22A3\u22A2"); sepLen = 2; }  // \u22A3\u22A2 = ⊣⊢
        if (arrowIdx < 0) { arrowIdx = combined.indexOf(","); sepLen = 1; }
        if (arrowIdx >= 0) {
            return new String[]{
                combined.substring(0, arrowIdx).trim(),
                combined.substring(arrowIdx + sepLen).trim()
            };
        }
        return new String[]{combined.trim(), ""};
    }

    // =========================================================================
    // Constructor & Public API
    // =========================================================================
    
    /** Create a new SFCR parser. */
    public SFCRParser(CirSim sim) {
        this.sim = sim;
        lookupClampDefault = (sim == null) ? true : sim.isSfcrLookupClampDefault();
    }

    public static class ParseException extends RuntimeException {
        ParseException(String message) {
            super(message);
        }
    }

    /**
     * Parse SFCR text to a plain result object with no CirSim or GWT dependency.
     *
     * <p>Safe to call from plain-Java unit tests running on a standard JVM.
     * Element instantiation is skipped; the dump strings that would normally be
     * fed to {@code ElementFactoryFacade.createFromDumpType(...)} are collected in the returned
     * {@link SFCRParseResult} instead.
     *
     * @param text SFCR-format source text
     * @return parsed result, or {@code null} if text is null/empty
     */
    public static SFCRParseResult parseToResult(String text) {
        return parseToResult(text, false);
    }

    public static SFCRParseResult parseToResult(String text, boolean strict) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        // Normalize R-style syntax to block format before validation/parsing
        String normalizedText = new SFCRSyntaxNormalizer().normalize(text);
        if (strict) {
            validateStrictInput(normalizedText);
        }
        SFCRParser parser = new SFCRParser(null);
        parser.pendingResult = new SFCRParseResult();
        parser.parse(normalizedText);
        // Copy hints collected during block parsing into the result.
        // (parse() also registers them with HintRegistry which is fine for tests.)
        parser.pendingResult.hints.putAll(parser.hints);
        return parser.pendingResult;
    }

    private static void validateStrictInput(String text) {
        String[] lines = text.split("\n");
        String currentBlock = null;
        int currentBlockStartLine = -1;
        boolean hasValidRows = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("%")) {
                continue;
            }

            if (trimmed.startsWith("@")) {
                String directive = extractDirective(trimmed);
                if ("@end".equals(directive)) {
                    if (currentBlock == null) {
                        throw new ParseException("Unexpected @end at line " + (i + 1));
                    }
                    if (requiresRows(currentBlock) && !hasValidRows) {
                        throw new ParseException("Block " + currentBlock + " at line " +
                                currentBlockStartLine + " contains no valid rows");
                    }
                    currentBlock = null;
                    currentBlockStartLine = -1;
                    hasValidRows = false;
                    continue;
                }

                if (isKnownDirective(directive)) {
                    if (currentBlock != null) {
                        throw new ParseException("Missing @end for block " + currentBlock +
                                " started at line " + currentBlockStartLine);
                    }
                    currentBlock = directive;
                    currentBlockStartLine = i + 1;
                    hasValidRows = false;
                    continue;
                }

                throw new ParseException("Unknown directive " + directive + " at line " + (i + 1));
            }

            if (currentBlock == null) {
                continue;
            }

            if ("@equations".equals(currentBlock) || "@parameters".equals(currentBlock)) {
                if (!isValidEquationRow(trimmed)) {
                    throw new ParseException("Invalid equation row at line " + (i + 1) + ": " + trimmed);
                }
                hasValidRows = true;
            } else if ("@lookup".equals(currentBlock)) {
                if (isLookupHeaderRow(trimmed)) {
                    continue;
                }
                if (!isValidLookupRow(trimmed)) {
                    throw new ParseException("Invalid lookup row at line " + (i + 1) + ": " + trimmed);
                }
                hasValidRows = true;
            }
        }

        if (currentBlock != null) {
            throw new ParseException("Missing @end for block " + currentBlock +
                    " started at line " + currentBlockStartLine);
        }
    }

    private static String extractDirective(String line) {
        int end = line.indexOf(' ');
        if (end < 0) {
            end = line.length();
        }
        return line.substring(0, end).toLowerCase();
    }

    private static boolean isKnownDirective(String directive) {
        return "@init".equals(directive) || "@action".equals(directive) ||
                "@matrix".equals(directive) || "@equations".equals(directive) ||
                "@parameters".equals(directive) || "@hints".equals(directive) ||
                "@scope".equals(directive) || "@circuit".equals(directive) ||
                "@info".equals(directive) || "@sankey".equals(directive) ||
                "@lookup".equals(directive);
    }

    private static boolean requiresRows(String directive) {
        return "@equations".equals(directive) || "@parameters".equals(directive) ||
               "@lookup".equals(directive);
    }

    private static boolean isValidEquationRow(String trimmedLine) {
        if (trimmedLine.startsWith("#") || trimmedLine.startsWith("%")) {
            return false;
        }
        return trimmedLine.contains("~");
    }

    private static boolean isLookupHeaderRow(String trimmedLine) {
        if (trimmedLine == null) {
            return false;
        }
        String line = trimmedLine.trim();
        if (line.startsWith("|") && line.endsWith("|")) {
            String[] cells = line.substring(1, line.length() - 1).split("\\|");
            if (cells.length >= 2) {
                String c0 = cells[0].trim().toLowerCase();
                String c1 = cells[1].trim().toLowerCase();
                if (("x".equals(c0) || "ratio".equals(c0)) &&
                    ("y".equals(c1) || "value".equals(c1) || "multiplier".equals(c1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isValidLookupRow(String trimmedLine) {
        if (trimmedLine == null) {
            return false;
        }
        String line = trimmedLine.trim();
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("%")) {
            return false;
        }
        if (line.startsWith("|")) {
            String[] cells = parseTableRowStatic(line);
            if (cells.length < 2) {
                return false;
            }
            return canParseDouble(cells[0]) && canParseDouble(cells[1]);
        }

        String[] parts;
        if (line.contains(",")) {
            parts = line.split(",", 2);
        } else {
            parts = line.split("\\s+");
        }
        if (parts.length < 2) {
            return false;
        }
        return canParseDouble(parts[0]) && canParseDouble(parts[1]);
    }

    private static boolean canParseDouble(String text) {
        if (text == null) {
            return false;
        }
        String s = text.trim();
        if (s.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String[] parseTableRowStatic(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) {
            return new String[0];
        }
        trimmed = trimmed.substring(1, trimmed.length() - 1);
        String[] rawCells = trimmed.split("\\|");
        String[] cells = new String[rawCells.length];
        for (int i = 0; i < rawCells.length; i++) {
            cells[i] = rawCells[i].trim();
        }
        return cells;
    }

    /** Check if text appears to be in SFCR format. */
    public static boolean isSFCRFormat(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String[] lines = text.split("\n");
        for (String line : lines) {
            String l = line == null ? "" : line.trim();
            if (l.startsWith("@matrix") ||
                l.startsWith("@equations") ||
                l.startsWith("@parameters") ||
                l.startsWith("@init") || l.startsWith("@action") || l.startsWith("@hints") ||
                l.startsWith("@scope") || l.startsWith("@circuit") ||
                l.startsWith("@info") || l.startsWith("@sankey") ||
                l.startsWith("@lookup")) {
                return true;
            }
        }

        // Check for R-style sfcr definitions near the top (ignoring comments).
        for (String line : lines) {
            String l = line.trim();
            // Skip comments and empty lines
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("%")) {
                continue;
            }
            // Check for sfcr_matrix or sfcr_set patterns
            if (l.contains("sfcr_matrix") || l.contains("sfcr_set")) {
                return true;
            }
            // First non-comment line doesn't match SFCR - check for block markers
            break;
        }
        
        return false;
    }
    
    /** Parse SFCR-format text and create circuit elements. */
    public boolean parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        // Normalize R-style syntax to block format before parsing
        // This consolidates both code paths into a single block-format parser
        String normalizedText = new SFCRSyntaxNormalizer().normalize(text);
        
        createdElements.clear();
        hints.clear();
        scopeVariables.clear();
        scopeBlocks.clear();
        initSettings.clear();
        rawCircuitLines.clear();
        globalLookupTables.clear();
        scopedLookupTables.clear();
        LookupTableRegistry.clear();
        lookupClampDefault = (sim == null) ? true : sim.isSfcrLookupClampDefault();
        actionElementFromActionBlock = false;
        parseWarnings.clear();
        if (sim != null) sim.getSFCRDocumentState().clearBlockComments();
        currentY = 24;
        
        try {
            String[] lines = normalizedText.split("\n");
            preScanInitLookupSettings(lines);
            preScanLookupTables(lines);
            int i = 0;
            Vector<String> pendingBlockComments = new Vector<String>();
            boolean inFence = false;
            boolean pendingCommentsConsumedInFence = false;
            SFCRParseContext parseContext = new SFCRParseContext(this);
            
            while (i < lines.length) {
                String line = lines[i].trim();
                
                // Skip empty lines (preserve pending comments across blank separators)
                if (line.isEmpty()) {
                    i++;
                    continue;
                }

                // Track markdown fences so pending headings/comments can attach to
                // structural constructs inside fenced blocks (e.g. ```{r} ... sfcr_set ... ```).
                if (line.startsWith("```")) {
                    if (!inFence) {
                        inFence = true;
                        pendingCommentsConsumedInFence = false;
                    } else {
                        inFence = false;
                        if (!pendingCommentsConsumedInFence) {
                            pendingBlockComments.clear();
                        }
                    }
                    i++;
                    continue;
                }

                // Track full-line comments/markdown so they can be attached to the next element block
                if (line.startsWith("#")) {
                    pendingBlockComments.add(lines[i]);
                    i++;
                    continue;
                }
                
                // Preserve metadata comments
                if (line.startsWith("%")) {
                    parseMetadataComment(line);
                    i++;
                    continue;
                }
                
                // Parse block markers (R-style already normalized to block format)
                if (line.startsWith("@")) {
                    String directive = extractDirective(line);
                    if ("@end".equals(directive)) {
                        i++;
                        continue;
                    }

                    boolean consumedPendingComments = false;
                    if ("@matrix".equals(directive)) {
                        if (inFence) pendingCommentsConsumedInFence = true;
                        storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_MATRIX,
                            extractBlockName(line, "@matrix"), pendingBlockComments);
                        consumedPendingComments = true;
                    } else if ("@equations".equals(directive) || "@parameters".equals(directive)) {
                        if (inFence) pendingCommentsConsumedInFence = true;
                        storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_EQUATIONS,
                            extractBlockName(line, directive), pendingBlockComments);
                        consumedPendingComments = true;
                    } else if ("@sankey".equals(directive)) {
                        if (inFence) pendingCommentsConsumedInFence = true;
                        storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_SANKEY, "", pendingBlockComments);
                        consumedPendingComments = true;
                    } else if ("@scope".equals(directive) && looksLikeScopeBlock(lines, i)) {
                        if (inFence) pendingCommentsConsumedInFence = true;
                        storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_SCOPE,
                            extractScopeBlockName(line), pendingBlockComments);
                        consumedPendingComments = true;
                    }

                    if (!consumedPendingComments) {
                        pendingBlockComments.clear();
                    }

                    SFCRBlockParseHandler handler = SFCRBlockParseHandlerRegistry.getHandler(directive);
                    ParseResult result;
                    if (handler != null) {
                        result = handler.parse(lines, i, parseContext);
                    } else {
                        result = unknownBlockParseHandler.parse(lines, i, parseContext);
                    }
                    i = result.getNextIndex();
                } else {
                    // Preserve non-block inline markdown context (headings/prose) so it
                    // can round-trip and remain associated with the next structural block.
                    if (!inFence) {
                        pendingBlockComments.add(lines[i]);
                    }
                    i++;
                }
            }
            parseWarnings.addAll(parseContext.getWarnings());

            // Apply init settings first (timestep, units, etc.)
            applyInitSettings();
            
            // Register all hints
            for (String varName : hints.keySet()) {
                HintRegistry.setHint(varName, hints.get(varName));
            }
            
            return true;
            
        } catch (Exception e) {
            CirSim.console("SFCRParser error: " + e.getMessage());
            return false;
        }
    }

    public ArrayList<ParseWarning> getParseWarnings() {
        return parseWarnings;
    }

    public BlockHeaderInfo parseBlockHeaderForHandler(String line, String keyword) {
        BlockPosition pos = parseBlockHeader(line, keyword);
        BlockHeaderInfo info = new BlockHeaderInfo();
        info.name = pos.name;
        info.x = pos.x;
        info.y = pos.y;
        return info;
    }

    public String[] parseTableRowForHandler(String line) {
        return parseTableRow(line);
    }

    public boolean parseBooleanForHandler(String text, boolean defaultValue) {
        return parseBoolean(text, defaultValue);
    }

    public String unescapeTableCellForHandler(String text) {
        return SFCRUtil.unescapeTableCell(text);
    }

    public ActionScheduler getActionSchedulerForHandler() {
        if (sim == null) {
            return null;
        }
        return ActionScheduler.getInstance(sim);
    }

    public ActionTimeElm findActionTimeElmForHandler() {
        return findActionTimeElm();
    }

    public CirSim getSimForHandler() {
        return sim;
    }

    public void addCreatedElementForHandler(CircuitElm elm) {
        if (elm != null) {
            createdElements.add(elm);
        }
    }

    public void setActionElementFromActionBlockForHandler(boolean value) {
        actionElementFromActionBlock = value;
    }

    private void preScanLookupTables(String[] lines) {
        if (lines == null || lines.length == 0) {
            return;
        }

        int i = 0;
        while (i < lines.length) {
            String headerLine = (lines[i] == null) ? "" : lines[i].trim();
            if (!headerLine.startsWith("@lookup")) {
                i++;
                continue;
            }

            LookupDefinition table = parseLookupHeaderForHandler(headerLine);
            if (table == null) {
                i++;
                continue;
            }

            int j = i + 1;
            while (j < lines.length) {
                String row = (lines[j] == null) ? "" : lines[j].trim();
                if (row.startsWith("@end")) {
                    break;
                }
                if (row.startsWith("#")) {
                    table.comments.add(row);
                } else if (!row.isEmpty() && !row.startsWith("%")) {
                    LookupPoint point = parseLookupPointForHandler(row);
                    if (point != null) {
                        table.xs.add(Double.valueOf(point.x));
                        table.ys.add(Double.valueOf(point.y));
                    }
                }
                j++;
            }

            if (table.xs.size() >= 2 && isStrictlyIncreasingForHandler(table.xs)) {
                String scopeName = table.scope;
                if (scopeName == null || scopeName.isEmpty()) {
                    globalLookupTables.put(table.name, table);
                } else {
                    HashMap<String, LookupDefinition> byScope = scopedLookupTables.get(scopeName);
                    if (byScope == null) {
                        byScope = new HashMap<String, LookupDefinition>();
                        scopedLookupTables.put(scopeName, byScope);
                    }
                    byScope.put(table.name, table);
                }
                LookupTableRegistry.register(table);
            }

            i = (j < lines.length) ? (j + 1) : j;
        }
    }
    
    // =========================================================================
    // Block Parsers
    // =========================================================================
    
    /** Parse metadata comment (% prefix - preserved on export). */
    private void parseMetadataComment(String line) {
        // Could store these for export, for now just skip
    }
    
    /** Parse inline init parameters: @init key=value key=value */
    public void parseInitInlineForHandler(String params) {
        // Parse: key=value key=value or key:value
        String[] parts = params.split("\\s+");
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx < 0) idx = part.indexOf(':');
            if (idx > 0) {
                String key = part.substring(0, idx).trim();
                String value = part.substring(idx + 1).trim();
                registerInitSettingForHandler(key, value);
            }
        }
    }

    public void registerInitSettingForHandler(String key, String value) {
        if (key == null || key.isEmpty()) {
            return;
        }
        initSettings.put(key, value);
        applyLookupInitAlias(key, value, true);
    }

    private void preScanInitLookupSettings(String[] lines) {
        if (lines == null || lines.length == 0) {
            return;
        }

        int i = 0;
        while (i < lines.length) {
            String line = (lines[i] == null) ? "" : lines[i].trim();
            if (!line.startsWith("@init")) {
                i++;
                continue;
            }

            String inlineParams = line.substring(5).trim();
            if (!inlineParams.isEmpty()) {
                String[] parts = inlineParams.split("\\s+");
                for (String part : parts) {
                    int idx = part.indexOf('=');
                    if (idx < 0) idx = part.indexOf(':');
                    if (idx > 0) {
                        String key = part.substring(0, idx).trim();
                        String value = part.substring(idx + 1).trim();
                        applyLookupInitAlias(key, value, false);
                    }
                }
                i++;
                continue;
            }

            i++;
            while (i < lines.length) {
                String initLine = (lines[i] == null) ? "" : lines[i].trim();
                if (initLine.startsWith("@end") || (initLine.startsWith("@") && !initLine.startsWith("@end"))) {
                    break;
                }
                if (initLine.isEmpty() || initLine.startsWith("#")) {
                    i++;
                    continue;
                }

                int colonIdx = initLine.indexOf(':');
                int eqIdx = initLine.indexOf('=');
                int sepIdx = colonIdx >= 0 ? colonIdx : eqIdx;
                if (sepIdx > 0) {
                    String key = initLine.substring(0, sepIdx).trim();
                    String value = initLine.substring(sepIdx + 1).trim();
                    int commentIdx = value.indexOf('#');
                    if (commentIdx >= 0) {
                        value = value.substring(0, commentIdx).trim();
                    }
                    applyLookupInitAlias(key, value, false);
                }
                i++;
            }

            if (i < lines.length && lines[i].trim().startsWith("@end")) {
                i++;
            }
        }
    }

    private void applyLookupInitAlias(String key, String value, boolean storeCanonicalMode) {
        if (key == null || value == null) {
            return;
        }

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

    public void registerHintForHandler(String varName, String description) {
        if (varName == null || varName.isEmpty()) {
            return;
        }
        hints.put(varName, description);
    }

    public String normalizeVariableNameForHandler(String rawName) {
        return SFCRUtil.normalizeVariableName(rawName);
    }

    public String normalizeExpressionForHandler(String rawExpr) {
        return SFCRUtil.normalizeExpression(rawExpr);
    }

    public String rewriteLookupCallsForHandler(String expr, String equationScope) {
        return rewriteLookupCalls(expr, equationScope);
    }

    public String[] splitDifferenceLeftAliasForHandler(String left) {
        return splitDifferenceLeftAlias(left);
    }

    public int getCurrentXForHandler() {
        return currentX;
    }

    public int getCurrentYForHandler() {
        return currentY;
    }

    public void setCurrentPositionForHandler(int x, int y) {
        currentX = x;
        currentY = y;
    }

    public int getElementSpacingForHandler() {
        return elementSpacing;
    }

    public boolean hasHintForHandler(String varName) {
        return hints.containsKey(varName);
    }

    public boolean isActionElementFromActionBlockForHandler() {
        return actionElementFromActionBlock;
    }

    public void addRawCircuitLineForHandler(String line) {
        if (line != null) {
            rawCircuitLines.add(line);
        }
    }

    public boolean hasPendingResultForHandler() {
        return pendingResult != null;
    }

    public void addBlockDumpForHandler(String blockType, String blockName, String dumpString) {
        if (pendingResult == null || blockType == null || dumpString == null) {
            return;
        }
        pendingResult.blockDumps.add(new SFCRParseResult.BlockDump(blockType, blockName, dumpString));
    }

    public String parseLookupDumpLineFromCircuitForHandler(String line) {
        return parseLookupDumpLineFromCircuit(line);
    }

    public String inferBlockTypeFromCircuitDumpLineForHandler(String line) {
        return inferBlockTypeFromCircuitDumpLine(line);
    }

    public boolean looksLikeScopeBlockForHandler(String[] lines, int startIndex) {
        return looksLikeScopeBlock(lines, startIndex);
    }

    public void addScopeVariableForHandler(String varName) {
        if (varName == null) {
            return;
        }
        String normalized = SFCRUtil.normalizeVariableName(varName);
        if (!normalized.isEmpty()) {
            scopeVariables.add(normalized);
        }
    }

    public void addScopeBlockForHandler(ScopeBlockSpec spec) {
        if (spec != null && spec.traces != null && !spec.traces.isEmpty()) {
            scopeBlocks.add(spec);
        }
    }

    public void setInfoContentForHandler(String content) {
        infoContent = content;
    }

    public void createEquationTableForHandler(String name, ArrayList<String> outputNames,
                                              ArrayList<String> equations,
                                              ArrayList<Integer> outputModes,
                                              ArrayList<String> targetNodeNames,
                                              ArrayList<String> sliderVarNames,
                                              ArrayList<Double> sliderValues,
                                              ArrayList<String> initialEquations) {
        createEquationTable(name, outputNames, equations, outputModes, targetNodeNames,
            sliderVarNames, sliderValues, initialEquations);
    }

    public void createMatrixTableForHandler(String name, ArrayList<String> columnNames,
                                            ArrayList<String> rowNames, ArrayList<String[]> tableRows,
                                            String matrixType, Boolean showInitialValuesOverride,
                                            Boolean showFlowValuesOverride,
                                            Boolean useBackwardEulerOverride) {
        createMatrixTable(name, columnNames, rowNames, tableRows, matrixType,
            showInitialValuesOverride, showFlowValuesOverride, useBackwardEulerOverride);
    }
    
    /** Apply parsed init settings to the simulator. */
    private void applyInitSettings() {
        // In result-mode (no sim), just copy raw settings to the result.
        if (pendingResult != null) {
            pendingResult.initSettings.putAll(initSettings);
            return;
        }
        // SFCR default is fixed timestep unless explicitly overridden.
        sim.adjustTimeStep = false;

        for (String key : initSettings.keySet()) {
            String value = initSettings.get(key);
            try {
                switch (key) {
                    case "timestep":
                    case "timeStep":
                        double ts = Double.parseDouble(value);
                        sim.setMaxTimeStep(ts);
                        sim.setTimeStep(ts);
                        break;
                    case "voltageRange":
                        CircuitElm.voltageRange = Double.parseDouble(value);
                        break;
                    case "voltageUnit":
                        sim.voltageUnitSymbol = value;
                        break;
                    case "timeUnit":
                        sim.timeUnitSymbol = value;
                        break;
                    case "showToolbar":
                        if (sim.toolbarCheckItem != null) {
                            sim.toolbarCheckItem.setState(parseBoolean(value, sim.toolbarCheckItem.getState()));
                            sim.setToolbar();
                        }
                        break;
                    case "showDots":
                        if (sim.dotsCheckItem != null) {
                            sim.dotsCheckItem.setState(parseBoolean(value, sim.dotsCheckItem.getState()));
                        }
                        break;
                    case "showVolts":
                        if (sim.voltsCheckItem != null) {
                            sim.voltsCheckItem.setState(parseBoolean(value, sim.voltsCheckItem.getState()));
                        }
                        break;
                    case "showValues":
                        if (sim.showValuesCheckItem != null) {
                            sim.showValuesCheckItem.setState(parseBoolean(value, sim.showValuesCheckItem.getState()));
                        }
                        break;
                    case "showPower":
                        if (sim.powerCheckItem != null) {
                            sim.powerCheckItem.setState(parseBoolean(value, sim.powerCheckItem.getState()));
                        }
                        break;
                    case "autoAdjustTimestep":
                    case "adjustTimeStep":
                        sim.adjustTimeStep = parseBoolean(value, sim.adjustTimeStep);
                        break;
                    case "lookupClamp":
                        // Alias accepted for SFCR import; canonical value is stored in lookupMode.
                        break;
                    case "lookupMode":
                        {
                            String mode = normalizeLookupMode(value);
                            sim.setSfcrLookupClampDefault(mode.equals("pwl"));
                        }
                        break;
                    case "equationTableMnaMode":
                    case "eqnTableMnaMode":
                        sim.setEquationTableMnaMode(parseBoolean(value, sim.isEquationTableMnaMode()));
                        break;
                    case "equationTableNewtonJacobianEnabled":
                    case "eqnTableNewtonJacobian":
                    case "equationTableNewtonJacobian":
                        sim.equationTableNewtonJacobianEnabled = parseBoolean(value, sim.equationTableNewtonJacobianEnabled);
                        break;
                    case "equationTableTolerance":
                    case "equationTableConvergenceTolerance":
                        {
                            double tol = Double.parseDouble(value);
                            if (tol > 0) {
                                sim.setEquationTableConvergenceTolerance(tol);
                            }
                        }
                        break;
                    case "convergenceCheckThreshold":
                    case "subiterationConvergenceThreshold":
                        {
                            int threshold = Integer.parseInt(value);
                            if (threshold >= 0) {
                                sim.convergenceCheckThreshold = threshold;
                            }
                        }
                        break;
                    case "infoViewerUpdateIntervalMs":
                        {
                            int interval = Integer.parseInt(value);
                            if (interval > 0) {
                                sim.infoViewerUpdateIntervalMs = interval;
                            }
                        }
                        break;
                    case "autoOpenModelInfoOnLoad":
                    case "autoOpenModelInfo":
                    case "openModelInfoOnLoad":
                        sim.autoOpenModelInfoOnLoad = parseBoolean(value, sim.autoOpenModelInfoOnLoad);
                        break;
                    default:
                        CirSim.console("SFCRParser: Unknown init setting: " + key);
                }
            } catch (Exception e) {
                CirSim.console("SFCRParser: Invalid init value for " + key + ": " + value);
            }
        }
    }

    private ActionTimeElm findActionTimeElm() {
        for (int idx = 0; idx < sim.elmList.size(); idx++) {
            CircuitElm ce = sim.getElm(idx);
            if (ce instanceof ActionTimeElm) {
                return (ActionTimeElm) ce;
            }
        }
        return null;
    }
    
    /**
     * Parse the {@code @lookup} header line and return a {@link LookupDefinition} with
     * {@code name} and {@code scope} populated, or {@code null} if the header carries no name.
     */
    public LookupDefinition parseLookupHeaderForHandler(String headerLine) {
        String header = (headerLine == null ? "" : headerLine.trim())
            .substring("@lookup".length()).trim();
        if (header.isEmpty()) {
            return null;
        }
        String lookupName = null;
        String scopeName = null;
        String[] tokens = header.split("\\s+");
        for (int t = 0; t < tokens.length; t++) {
            String token = tokens[t].trim();
            if (token.isEmpty()) {
                continue;
            }
            int eq = token.indexOf('=');
            if (eq > 0) {
                String key = token.substring(0, eq).trim().toLowerCase();
                String val = token.substring(eq + 1).trim();
                if ("scope".equals(key) || "local".equals(key) || "equations".equals(key) || "table".equals(key)) {
                    scopeName = SFCRUtil.normalizeVariableName(val);
                }
            } else if (lookupName == null) {
                lookupName = SFCRUtil.normalizeVariableName(token);
            }
        }
        if (lookupName == null || lookupName.isEmpty()) {
            return null;
        }
        LookupDefinition def = new LookupDefinition();
        def.name = lookupName;
        def.scope = (scopeName == null || scopeName.isEmpty()) ? null : scopeName;
        return def;
    }

    public static class LookupPoint {
        public double x;
        public double y;

        LookupPoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public LookupPoint parseLookupPointForHandler(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String[] parts;
        if (trimmed.startsWith("|")) {
            String[] cells = parseTableRow(trimmed);
            if (cells.length < 2) {
                return null;
            }
            String c0 = cells[0].trim().toLowerCase();
            String c1 = cells[1].trim().toLowerCase();
            if (("x".equals(c0) || "ratio".equals(c0)) &&
                ("y".equals(c1) || "value".equals(c1) || "multiplier".equals(c1))) {
                return null;
            }
            parts = new String[] { cells[0], cells[1] };
        } else if (trimmed.contains(",")) {
            parts = trimmed.split(",", 2);
        } else {
            parts = trimmed.split("\\s+");
            if (parts.length < 2) {
                return null;
            }
        }

        if (parts.length < 2) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            return new LookupPoint(x, y);
        } catch (Exception e) {
            return null;
        }
    }

    public void storePendingMatrixBlockCommentsForHandler(String blockName, Vector<String> pendingComments) {
        storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_MATRIX, blockName, pendingComments);
    }

    public void storePendingEquationsBlockCommentsForHandler(String blockName, Vector<String> pendingComments) {
        storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_EQUATIONS, blockName, pendingComments);
    }

    public boolean isStrictlyIncreasingForHandler(ArrayList<Double> xs) {
        if (xs == null || xs.size() < 2) {
            return false;
        }
        for (int i = 1; i < xs.size(); i++) {
            if (xs.get(i).doubleValue() <= xs.get(i - 1).doubleValue()) {
                return false;
            }
        }
        return true;
    }

    public void registerLookupTableForHandler(LookupDefinition table) {
        if (table == null || table.name == null || table.name.isEmpty()) {
            return;
        }
        String scopeName = table.scope;
        if (scopeName == null || scopeName.isEmpty()) {
            globalLookupTables.put(table.name, table);
        } else {
            HashMap<String, LookupDefinition> byScope = scopedLookupTables.get(scopeName);
            if (byScope == null) {
                byScope = new HashMap<String, LookupDefinition>();
                scopedLookupTables.put(scopeName, byScope);
            }
            byScope.put(table.name, table);
        }
        LookupTableRegistry.register(table);

        if (pendingResult != null) {
            String blockName = (scopeName == null || scopeName.isEmpty()) ? table.name : (scopeName + ":" + table.name);
            StringBuilder dump = new StringBuilder();
            dump.append("lookup ").append(table.name);
            if (scopeName != null && !scopeName.isEmpty()) {
                dump.append(" scope=").append(scopeName);
            }
            for (int p = 0; p < table.xs.size(); p++) {
                dump.append(" ").append(table.xs.get(p).doubleValue()).append(",").append(table.ys.get(p).doubleValue());
            }
            pendingResult.blockDumps.add(new SFCRParseResult.BlockDump("lookup", blockName, dump.toString()));
        }
    }

    private LookupDefinition findLookupTable(String equationScope, String lookupName) {
        if (lookupName == null || lookupName.isEmpty()) {
            return null;
        }
        String normalizedName = SFCRUtil.normalizeVariableName(lookupName);
        if (equationScope != null && !equationScope.isEmpty()) {
            HashMap<String, LookupDefinition> local = scopedLookupTables.get(equationScope);
            if (local != null) {
                LookupDefinition t = local.get(normalizedName);
                if (t != null) {
                    return t;
                }
            }
        }
        return globalLookupTables.get(normalizedName);
    }

    private String rewriteLookupCalls(String expr, String equationScope) {
        if (expr == null || expr.trim().isEmpty()) {
            return expr;
        }
        String rewritten = rewriteLookupFunctionCalls(expr, equationScope);
        rewritten = rewriteNamedLookupCalls(rewritten, equationScope);
        return rewritten;
    }

    private String rewriteLookupFunctionCalls(String expr, String equationScope) {
        return expr;
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

            int close = findMatchingParenForLookup(out, j);
            if (close < 0) {
                break;
            }
            String argExpr = out.substring(j + 1, close).trim();
            String replacement = "lookup(" + identifier + ", " + argExpr + ")";
            out = out.substring(0, start) + replacement + out.substring(close + 1);
            i = start + replacement.length();
        }
        return out;
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
            if (idx > 0 && isLookupNamePart(expr.charAt(idx - 1))) {
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

    private int findMatchingParenForLookup(String text, int openIndex) {
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

    private int findTopLevelComma(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private boolean isLookupNameStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '\\';
    }

    private boolean isLookupNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '\\' || c == '^' || c == '{' || c == '}' || c == '.';
    }

    /**
     * Split an alias-form left side "X - term" into ["X", "term"].
     * Returns [left, null] when alias pattern is not detected.
     */
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

        // Do not treat flow target separators as alias subtraction.
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

    /** Conservative variable-name check for left-side alias detection. */
    private boolean looksLikeSimpleVariableName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        char first = name.charAt(0);
        if (!(Character.isLetter(first) || first == '_' || first == '\\')) {
            return false;
        }

        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '\\' || c == '^' || c == '{' || c == '}' || c == '.')) {
                return false;
            }
        }

        return true;
    }
    
    /** Check whether @scope at startIndex is block-form (@scope ... @end). */
    private boolean looksLikeScopeBlock(String[] lines, int startIndex) {
        int i = startIndex + 1;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }
            if (line.equals("@end")) {
                return true;
            }
            if (line.startsWith("@")) {
                return false;
            }
            return true;
        }
        return false;
    }

    /** Parse legacy lookup dump line from @circuit and register the table. */
    private String parseLookupDumpLineFromCircuit(String line) {
        if (line == null) {
            return null;
        }

        String trimmed = line.trim();
        if (!trimmed.startsWith("lookup ")) {
            return null;
        }

        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 4) {
            return null;
        }

        String lookupName = SFCRUtil.normalizeVariableName(tokens[1]);
        if (lookupName == null || lookupName.isEmpty()) {
            return null;
        }

        String scopeName = null;
        LookupDefinition table = new LookupDefinition();
        table.name = lookupName;

        for (int j = 2; j < tokens.length; j++) {
            String token = tokens[j];
            if (token == null) {
                continue;
            }

            String part = token.trim();
            if (part.isEmpty()) {
                continue;
            }

            if (part.startsWith("scope=")) {
                String scopeToken = part.substring(6).trim();
                if (!scopeToken.isEmpty()) {
                    scopeName = SFCRUtil.normalizeVariableName(scopeToken);
                }
                continue;
            }

            LookupPoint point = parseLookupPointForHandler(part);
            if (point != null) {
                table.xs.add(Double.valueOf(point.x));
                table.ys.add(Double.valueOf(point.y));
            }
        }

        if (table.xs.size() < 2) {
            return null;
        }
        if (!isStrictlyIncreasingForHandler(table.xs)) {
            return null;
        }

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

    private String inferBlockTypeFromCircuitDumpLine(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }

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
    
    /** Get raw circuit lines for standard element loader. */
    public ArrayList<String> getRawCircuitLines() {
        return rawCircuitLines;
    }

    /** Finalize scope creation after all elements (including @circuit raw lines) are loaded. */
    public void applyParsedScopes() {
        addScopes();
    }
    
    /** Get the @info block content (markdown), or null if not present. */
    public String getInfoContent() {
        return infoContent;
    }
    
    // =========================================================================
    // R-Style sfcr Syntax Support
    // =========================================================================
    
    /** Extract assignment name from R-style block: name <- sfcr_... */
    private String extractRStyleAssignmentName(String block, String defaultName) {
        if (block == null) {
            return defaultName;
        }
        int assignIdx = block.indexOf("<-");
        if (assignIdx > 0) {
            String name = block.substring(0, assignIdx).trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return defaultName;
    }

    public String extractRStyleAssignmentNameForHandler(String block, String defaultName) {
        return extractRStyleAssignmentName(block, defaultName);
    }

    /** Consume and parse one or more metadata comments (# [ ... ]) from pending comments. */
    private RStyleBlockMetadata consumeRStyleMetadataFromComments(Vector<String> pendingComments) {
        return rStyleParseService.consumeMetadataFromComments(pendingComments);
    }

    public RStyleBlockMetadata consumeRStyleMetadataFromCommentsForHandler(Vector<String> pendingComments) {
        return consumeRStyleMetadataFromComments(pendingComments);
    }

    // R-style parsing methods removed - normalization now happens via SFCRSyntaxNormalizer
    // before the main parse() method runs, consolidating both code paths.
    
    // =========================================================================
    // Helper Methods
    // =========================================================================
    
    /** Extract block name: "@matrix Foo" -> "Foo". */
    private String extractBlockName(String line, String keyword) {
        BlockPosition pos = parseBlockHeader(line, keyword);
        return pos.name;
    }
    
    /**
     * Parse block header with optional position.
     * Format: "@keyword Name x=100 y=200" or "@keyword Name"
     * Position can be in any order: "@keyword x=100 y=200 Name" also works.
     */
    private BlockPosition parseBlockHeader(String line, String keyword) {
        String rest = line.substring(keyword.length()).trim();
        BlockPosition pos = new BlockPosition(keyword.substring(1)); // Default name is keyword without @
        
        if (rest.isEmpty()) {
            return pos;
        }
        
        // Parse x=N and y=N if present
        StringBuilder nameBuilder = new StringBuilder();
        String[] parts = rest.split("\\s+");
        
        for (String part : parts) {
            if (part.toLowerCase().startsWith("x=")) {
                try {
                    pos.x = Integer.parseInt(part.substring(2));
                } catch (NumberFormatException e) {
                    // Ignore invalid x value
                }
            } else if (part.toLowerCase().startsWith("y=")) {
                try {
                    pos.y = Integer.parseInt(part.substring(2));
                } catch (NumberFormatException e) {
                    // Ignore invalid y value
                }
            } else {
                // Part of the name
                if (nameBuilder.length() > 0) {
                    nameBuilder.append(" ");
                }
                nameBuilder.append(part);
            }
        }
        
        if (nameBuilder.length() > 0) {
            pos.name = nameBuilder.toString();
        }
        
        return pos;
    }
    
    /** Parse markdown table row: "| a | b | c |" -> ["a", "b", "c"]. */
    private String[] parseTableRow(String line) {
        // Remove leading/trailing pipes
        if (line.startsWith("|")) line = line.substring(1);
        if (line.endsWith("|")) line = line.substring(0, line.length() - 1);
        
        String[] parts = line.split("\\|", -1);  // -1 to keep trailing empty strings
        
        // Filter out completely empty trailing parts (from trailing whitespace before |)
        // and trim each cell
        ArrayList<String> cells = new ArrayList<String>();
        for (int i = 0; i < parts.length; i++) {
            String cell = parts[i].trim();
            // Always add cells (even empty ones) except trailing empty after last real content
            cells.add(cell);
        }
        
        // Remove trailing empty cells
        while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
            cells.remove(cells.size() - 1);
        }
        
        return cells.toArray(new String[0]);
    }

    /** Persist pending leading # comments for a block key and clear accumulator. */
    private void storePendingBlockComments(String blockType, String blockName, Vector<String> pendingComments) {
        if (pendingComments == null || pendingComments.size() == 0) {
            return;
        }
        String key = SFCRBlockCommentRegistry.makeKey(blockType, blockName);
        if (pendingResult != null) {
            pendingResult.blockComments.put(key, new ArrayList<String>(pendingComments));
        } else {
            sim.getSFCRDocumentState().setBlockComments(key, pendingComments);
        }
        pendingComments.clear();
    }

    /** Extract scope block name from header, ignoring known key=value attributes. */
    private String extractScopeBlockName(String headerLine) {
        if (headerLine == null) {
            return "scope";
        }
        String rest = headerLine.substring(6).trim();
        if (rest.isEmpty()) {
            return "scope";
        }
        String[] parts = rest.split("\\s+");
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.toLowerCase().startsWith("position=")) {
                continue;
            }
            if (nameBuilder.length() > 0) {
                nameBuilder.append(" ");
            }
            nameBuilder.append(part);
        }
        if (nameBuilder.length() == 0) {
            return "scope";
        }
        return nameBuilder.toString();
    }

    /** Parse flexible boolean strings (true/false, 1/0, yes/no). */
    private boolean parseBoolean(String text, boolean defaultValue) {
        if (text == null) return defaultValue;
        String t = text.trim().toLowerCase();
        if (t.equals("true") || t.equals("1") || t.equals("yes")) return true;
        if (t.equals("false") || t.equals("0") || t.equals("no")) return false;
        return defaultValue;
    }

    // =========================================================================
    // Element Creation
    // =========================================================================
    
    /** Create matrix table element from parsed matrix data. */
    private void createMatrixTable(String name, ArrayList<String> columnNames,
                                   ArrayList<String> rowNames, ArrayList<String[]> tableRows,
                                   String matrixType, Boolean showInitialValuesOverride,
                                   Boolean showFlowValuesOverride,
                                   Boolean useBackwardEulerOverride) {
        SFCRTableDumpBuilderService.DumpBuildResult build = tableDumpBuilderService.buildMatrixDump(
            name, currentX, currentY, columnNames, rowNames, tableRows, showInitialValuesOverride);
        if (build == null) {
            CirSim.console("SFCRParser: Skipping matrix table '" + name + "' because table shape is invalid");
            return;
        }
        String dumpString = build.dump;
        int y2 = build.y2;
        
        // --- Result-mode: collect dump without instantiating elements ---
        if (pendingResult != null) {
            pendingResult.blockDumps.add(new SFCRParseResult.BlockDump("matrix", name, dumpString));
            currentY = y2 + elementSpacing;
            return;
        }
        
        // Create element by parsing the dump string
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
                ce.setPointsForImportExport();  // Initialize geometry (required after construction)
                sim.getImportExportHelper().assignPersistentUid(ce, null);
                sim.elmList.addElement(ce);
                createdElements.add(ce);
                currentY = yb + elementSpacing;
            } else {
                CirSim.console("SFCRParser: Failed to instantiate matrix table '" + name + "' (createCe returned null)");
            }
        } catch (Exception e) {
            CirSim.console("Error creating matrix table '" + name + "': " + e.toString());
            e.printStackTrace();
        }
    }

    /** Create EquationTableElm from parsed equation data. */
    private void createEquationTable(String name, ArrayList<String> outputNames,
                                     ArrayList<String> equations,
                                     ArrayList<Integer> outputModes,
                                     ArrayList<String> targetNodeNames,
                                     ArrayList<String> sliderVarNames,
                                     ArrayList<Double> sliderValues,
                                     ArrayList<String> initialEquations) {
        SFCRTableDumpBuilderService.DumpBuildResult build = tableDumpBuilderService.buildEquationDump(
            name, currentX, currentY, outputNames, equations, outputModes, targetNodeNames,
            sliderVarNames, sliderValues, initialEquations);
        if (build == null) {
            return;
        }
        String dumpString = build.dump;
        int y2 = build.y2;
        
        // --- Result-mode: collect dump without instantiating elements ---
        if (pendingResult != null) {
            pendingResult.blockDumps.add(new SFCRParseResult.BlockDump("equations", name, dumpString));
            currentY = y2 + elementSpacing;
            return;
        }
        
        // CirSim.console("Creating EquationTable: " + name + " with " + rows + " equations");
        
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
                ce.setPointsForImportExport();  // Initialize geometry (required after construction)
                sim.getImportExportHelper().assignPersistentUid(ce, null);
                sim.elmList.addElement(ce);
                createdElements.add(ce);
                currentY = yb + elementSpacing;
            }
        } catch (Exception e) {
            CirSim.console("Error creating EquationTable: " + e.getMessage());
        }
    }
    
    /** Add scopes from parsed @scope blocks and legacy @scope var lines. */
    private void addScopes() {
        boolean addedAny = false;

        // New block form: @scope ... @end with uid-based traces
        for (ScopeBlockSpec block : scopeBlocks) {
            if (block.traces.isEmpty()) {
                continue;
            }

            // If this scope has geometry, create an embedded ScopeElm (floating scope).
            // First check if a matching ScopeElm already exists from @circuit.
            ScopeElm matchingScopeElm = findScopeElmByPrimaryTrace(block.traces.get(0).uid);
            if (matchingScopeElm == null && block.hasGeometry()) {
                // Create a new ScopeElm with the exported geometry
                matchingScopeElm = new ScopeElm(block.x1, block.y1);
                matchingScopeElm.x2 = block.x2;
                matchingScopeElm.y2 = block.y2;
                sim.getImportExportHelper().assignPersistentUid(matchingScopeElm, block.elmUid);
                sim.elmList.addElement(matchingScopeElm);
                createdElements.add(matchingScopeElm);
            }

            if (matchingScopeElm != null && matchingScopeElm.elmScope != null) {
                Scope embedded = matchingScopeElm.elmScope;
                embedded.resetPlots();

                for (int i = 0; i < block.traces.size(); i++) {
                    ScopeTraceSpec trace = block.traces.get(i);
                    CircuitElm elm = sim.getImportExportHelper().findElmByUid(trace.uid);
                    if (elm == null) {
                        continue;
                    }
                    int units = elm.getScopeUnitsForImportExport(trace.value);
                    double manScale = embedded.getManScaleFromMaxScale(units, false);
                    embedded.addPlot(elm, units, trace.value, manScale);
                }

                if (embedded.getPlotCount() > 0) {
                    embedded.initialize();

                    // Apply parsed properties AFTER initialize() which resets speed to 64
                    if (block.speed > 0) {
                        embedded.setSpeed(block.speed);
                    }
                    if (block.flags != Integer.MIN_VALUE) {
                        embedded.setFlags(block.flags);
                    }
                    if (block.title != null) {
                        embedded.setTitle(block.title);
                    }
                    if (block.label != null) {
                        embedded.setText(block.label);
                    }

                    embedded.calcVisiblePlots();
                    matchingScopeElm.setPointsForImportExport();
                    addedAny = true;
                    continue;
                }
            }

            // No geometry and no matching ScopeElm: create as docked scope
            if (sim.scopeCount >= sim.scopes.length) {
                break;
            }

            Scope scope = new Scope(sim);
            scope.resetPlots();

            for (int i = 0; i < block.traces.size(); i++) {
                ScopeTraceSpec trace = block.traces.get(i);
                CircuitElm elm = sim.getImportExportHelper().findElmByUid(trace.uid);
                if (elm == null) {
                    continue;
                }
                int units = elm.getScopeUnitsForImportExport(trace.value);
                double manScale = scope.getManScaleFromMaxScale(units, false);
                scope.addPlot(elm, units, trace.value, manScale);
            }

            if (scope.getPlotCount() == 0) {
                continue;
            }

            scope.setPositionForEmbedded(block.position);
            scope.initialize();

            // Apply parsed properties AFTER initialize() which resets speed to 64
            if (block.speed > 0) {
                scope.setSpeed(block.speed);
            }
            if (block.flags != Integer.MIN_VALUE) {
                scope.setFlags(block.flags);
            }
            if (block.title != null) {
                scope.setTitle(block.title);
            }
            if (block.label != null) {
                scope.setText(block.label);
            }

            scope.calcVisiblePlots();

            sim.scopes[sim.scopeCount] = scope;
            sim.scopeCount++;
            addedAny = true;
        }

        // Legacy one-line form: @scope varname (still unsupported for probe binding)
        if (!scopeVariables.isEmpty()) {
            CirSim.console("SFCR: Skipping legacy @scope variable mapping for " + scopeVariables.size() +
                    " variables (probe binding not implemented)");
        }

        if (addedAny) {
            sim.setupScopesForImportExport();
        }
    }

    private ScopeElm findScopeElmByPrimaryTrace(String uid) {
        if (uid == null || uid.length() == 0) {
            return null;
        }
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.elmList.get(i);
            if (!(ce instanceof ScopeElm)) {
                continue;
            }
            ScopeElm se = (ScopeElm) ce;
            if (se.elmScope == null || se.elmScope.getPlotCount() == 0) {
                continue;
            }
            CircuitElm firstElm = se.elmScope.getPlotElement(0);
            if (firstElm == null) {
                continue;
            }
            String firstUid = firstElm.getPersistentUid();
            if (uid.equals(firstUid)) {
                return se;
            }
        }
        return null;
    }
    
    /** Get list of created elements (for testing/debugging). */
    public ArrayList<CircuitElm> getCreatedElements() {
        return createdElements;
    }
}
