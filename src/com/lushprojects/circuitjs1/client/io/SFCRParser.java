/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.io;

import com.lushprojects.circuitjs1.client.scope.Scope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.registry.HintRegistry;
import com.lushprojects.circuitjs1.client.util.*;
import com.lushprojects.circuitjs1.client.elements.economics.*;
import com.lushprojects.circuitjs1.client.elements.misc.*;
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
 *   @plantuml   - PlantUML sequence diagrams (creates SequenceDiagramElm)
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
        Boolean invisible;

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
    private boolean lookupClampDefault = true;
    private String infoContent = null;
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
     * @deprecated Use {@link SFCRUtil#parseModeOrdinal(String)} instead.
     */
    public static int parseModeOrdinal(String mode) {
        return SFCRUtil.parseModeOrdinal(mode);
    }

    /**
     * Escape a token for the CircuitJS dump format.
     * @deprecated Use {@link SFCRUtil#escapeToken(String)} instead.
     */
    public static String escapeToken(String s) {
        return SFCRUtil.escapeToken(s);
    }

    /**
     * Parse a combined "name-&gt;target" notation.
     * @deprecated Use {@link SFCRUtil#parseCombinedName(String)} instead.
     */
    public static String[] parseCombinedNameLocal(String combined) {
        return SFCRUtil.parseCombinedName(combined);
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
        // Normalize for validation only - parse() will normalize again
        // (normalization is idempotent so this is safe)
        if (strict) {
            String normalizedText = new SFCRSyntaxNormalizer().normalize(text);
            validateStrictInput(normalizedText);
        }
        SFCRParser parser = new SFCRParser(null);
        parser.pendingResult = new SFCRParseResult();
        parser.parse(text);  // parse() normalizes internally
        // Copy hints collected during block parsing into the result.
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
                "@plantuml".equals(directive) ||
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
        return parseTableRowCommon(line, true);
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
                l.startsWith("@info") || l.startsWith("@sankey") || l.startsWith("@plantuml") ||
                l.startsWith("@startuml") ||
                isPlantUmlFenceHeaderStatic(l) ||
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

    private static boolean isPlantUmlFenceHeaderStatic(String trimmedLine) {
        if (trimmedLine == null) {
            return false;
        }
        String lower = trimmedLine.trim().toLowerCase();
        return lower.startsWith("```{plantuml") || lower.equals("```plantuml") || lower.startsWith("```plantuml ");
    }
    
    /** Parse SFCR-format text and create circuit elements. */
    public boolean parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        // Normalize R-style syntax to block format before parsing
        // This consolidates both code paths into a single block-format parser
        String normalizedText = new SFCRSyntaxNormalizer().normalize(text);
        
        // Create context that owns all mutable parse state
        SFCRParseContext ctx = new SFCRParseContext(sim, tableDumpBuilderService, rStyleParseService);
        if (pendingResult != null) {
            ctx.setPendingResult(pendingResult);
        }
        
        // Clear registries
        LookupTableRegistry.clear();
        if (sim != null) sim.getSFCRDocumentState().clearBlockComments();
        
        try {
            String[] lines = normalizedText.split("\n");
            preScanInitLookupSettings(lines, ctx);
            preScanLookupTables(lines, ctx);
            int i = 0;
            Vector<String> pendingBlockComments = new Vector<String>();
            boolean inFence = false;
            boolean pendingCommentsConsumedInFence = false;
            
            while (i < lines.length) {
                String line = lines[i].trim();
                
                // Skip empty lines (preserve pending comments across blank separators)
                if (line.isEmpty()) {
                    i++;
                    continue;
                }

                if (line.startsWith("@startuml")) {
                    if (inFence) pendingCommentsConsumedInFence = true;
                    storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_PLANTUML, "", pendingBlockComments);
                    i = parseInlinePlantUmlBlock(lines, i, ctx);
                    continue;
                }

                if (!inFence && isPlantUmlFenceHeader(line)) {
                    if (inFence) pendingCommentsConsumedInFence = true;
                    storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_PLANTUML, "", pendingBlockComments);
                    i = parsePlantUmlFence(lines, i, ctx);
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
                
                // Preserve metadata comments (% prefix – no-op for now)
                if (line.startsWith("%")) {
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
                        ctx.storePendingMatrixBlockComments(
                            ctx.parseBlockHeader(line, "@matrix").name, pendingBlockComments);
                        consumedPendingComments = true;
                    } else if ("@equations".equals(directive) || "@parameters".equals(directive)) {
                        if (inFence) pendingCommentsConsumedInFence = true;
                        ctx.storePendingEquationsBlockComments(
                            ctx.parseBlockHeader(line, directive).name, pendingBlockComments);
                        consumedPendingComments = true;
                    } else if ("@sankey".equals(directive)) {
                        if (inFence) pendingCommentsConsumedInFence = true;
                        storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_SANKEY, "", pendingBlockComments);
                        consumedPendingComments = true;
                    } else if ("@plantuml".equals(directive)) {
                        if (inFence) pendingCommentsConsumedInFence = true;
                        storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_PLANTUML, "", pendingBlockComments);
                        consumedPendingComments = true;
                    } else if ("@scope".equals(directive) && ctx.looksLikeScopeBlock(lines, i)) {
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
                        result = handler.parse(lines, i, ctx);
                    } else {
                        result = unknownBlockParseHandler.parse(lines, i, ctx);
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
            
            // Copy state from context back to parser fields for external access
            parseWarnings.clear();
            parseWarnings.addAll(ctx.getWarnings());
            createdElements.clear();
            createdElements.addAll(ctx.getCreatedElements());
            hints.clear();
            hints.putAll(ctx.getHints());
            scopeVariables.clear();
            scopeVariables.addAll(ctx.getScopeVariables());
            scopeBlocks.clear();
            scopeBlocks.addAll(ctx.getScopeBlocks());
            initSettings.clear();
            initSettings.putAll(ctx.getInitSettings());
            rawCircuitLines.clear();
            rawCircuitLines.addAll(ctx.getRawCircuitLines());
            infoContent = ctx.getInfoContent();
            currentX = ctx.getCurrentX();
            currentY = ctx.getCurrentY();

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

    private void preScanLookupTables(String[] lines, SFCRParseContext ctx) {
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

            LookupDefinition table = ctx.parseLookupHeader(headerLine);
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
                    SFCRParser.LookupPoint point = ctx.parseLookupPoint(row);
                    if (point != null) {
                        table.xs.add(Double.valueOf(point.x));
                        table.ys.add(Double.valueOf(point.y));
                    }
                }
                j++;
            }

            if (table.xs.size() >= 2 && ctx.isStrictlyIncreasing(table.xs)) {
                ctx.registerLookupTable(table);
            }

            i = (j < lines.length) ? (j + 1) : j;
        }
    }
    
    private void preScanInitLookupSettings(String[] lines, SFCRParseContext ctx) {
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
                ctx.parseInitInline(inlineParams);
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
                    ctx.registerInitSetting(key, value);
                }
                i++;
            }

            if (i < lines.length && lines[i].trim().startsWith("@end")) {
                i++;
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

    public static class LookupPoint {
        public double x;
        public double y;

        public LookupPoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
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
    
    private static String[] parseTableRowCommon(String line, boolean requireWrappedPipes) {
        if (line == null) {
            return new String[0];
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        if (requireWrappedPipes && (!trimmed.startsWith("|") || !trimmed.endsWith("|"))) {
            return new String[0];
        }
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        String[] parts = trimmed.split("\\|", -1);
        ArrayList<String> cells = new ArrayList<String>(parts.length);
        for (int i = 0; i < parts.length; i++) {
            cells.add(parts[i].trim());
        }
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

    private boolean isPlantUmlFenceHeader(String trimmedLine) {
        if (trimmedLine == null) {
            return false;
        }
        String lower = trimmedLine.trim().toLowerCase();
        return lower.startsWith("```{plantuml") || lower.equals("```plantuml") || lower.startsWith("```plantuml ");
    }

    private int parsePlantUmlFence(String[] lines, int startIndex, SFCRParseContext ctx) {
        ArrayList<String> synthetic = new ArrayList<String>();
        appendPlantUmlSyntheticHeader(lines[startIndex].trim(), synthetic);

        int i = startIndex + 1;
        while (i < lines.length) {
            String rawLine = lines[i];
            if (rawLine.trim().startsWith("```")) {
                break;
            }
            synthetic.add(rawLine);
            i++;
        }
        synthetic.add("@end");

        SFCRBlockParseHandler handler = SFCRBlockParseHandlerRegistry.getHandler("@plantuml");
        if (handler != null) {
            handler.parse(synthetic.toArray(new String[0]), 0, ctx);
        }
        if (i < lines.length && lines[i].trim().startsWith("```")) {
            i++;
        }
        return i;
    }

    private int parseInlinePlantUmlBlock(String[] lines, int startIndex, SFCRParseContext ctx) {
        ArrayList<String> synthetic = new ArrayList<String>();
        synthetic.add("@plantuml");

        int i = startIndex;
        boolean foundEnd = false;
        while (i < lines.length) {
            String rawLine = lines[i];
            String trimmed = rawLine == null ? "" : rawLine.trim();
            if (i > startIndex && trimmed.startsWith("```")) {
                break;
            }
            synthetic.add(rawLine);
            i++;
            if ("@enduml".equals(trimmed)) {
                foundEnd = true;
                break;
            }
        }

        if (!foundEnd) {
            synthetic.add("@enduml");
        }
        synthetic.add("@end");

        SFCRBlockParseHandler handler = SFCRBlockParseHandlerRegistry.getHandler("@plantuml");
        if (handler != null) {
            handler.parse(synthetic.toArray(new String[0]), 0, ctx);
        }
        return i;
    }

    private void appendPlantUmlSyntheticHeader(String fenceHeader, ArrayList<String> synthetic) {
        if (synthetic == null) {
            return;
        }
        String trimmed = (fenceHeader == null) ? "" : fenceHeader.trim();
        String rest = "";

        if (trimmed.startsWith("```{")) {
            int close = trimmed.lastIndexOf('}');
            if (close > 4) {
                rest = trimmed.substring(4, close).trim();
            }
        } else if (trimmed.startsWith("```")) {
            rest = trimmed.substring(3).trim();
        }

        StringBuilder header = new StringBuilder("@plantuml");
        if (rest.isEmpty()) {
            synthetic.add(header.toString());
            return;
        }

        String[] parts = rest.split("\\s+");
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            String lowerPart = part.toLowerCase();
            if (lowerPart.startsWith("x=") || lowerPart.startsWith("y=")) {
                header.append(" ").append(part);
            } else if (lowerPart.startsWith("width=")) {
                synthetic.add("width: " + part.substring(part.indexOf('=') + 1));
            } else if (lowerPart.startsWith("scale=")) {
                synthetic.add("scale: " + part.substring(part.indexOf('=') + 1));
            }
        }
        synthetic.add(0, header.toString());
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
