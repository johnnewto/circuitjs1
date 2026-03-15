/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

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

    /** Parsed scope trace reference. */
    private static class ScopeTraceSpec {
        String uid;
        int value = Scope.VAL_VOLTAGE;
    }

    /** Parsed @scope block configuration. */
    private static class ScopeBlockSpec {
        String name = "scope";
        int position = 0;
        int speed = -1;
        int flags = Integer.MIN_VALUE;
        String title;
        String label;
        ArrayList<ScopeTraceSpec> traces = new ArrayList<ScopeTraceSpec>();
        // Embedded scope geometry (from ScopeElm export)
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        int x2 = Integer.MIN_VALUE;
        int y2 = Integer.MIN_VALUE;
        String elmUid;

        boolean hasGeometry() {
            return x1 != Integer.MIN_VALUE && y1 != Integer.MIN_VALUE
                && x2 != Integer.MIN_VALUE && y2 != Integer.MIN_VALUE;
        }
    }

    /** Parsed metadata from R-style comment: # [ x=.. y=.. type: .. ] */
    private static class RStyleBlockMetadata {
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
    private String infoContent = null;
    private boolean actionElementFromActionBlock = false;

    /** Non-null when in result-mode ({@link #parseToResult}). */
    SFCRParseResult pendingResult = null;

    // =========================================================================
    // GWT-independent helpers (usable from plain-Java unit tests)
    // =========================================================================

    /**
     * Map an SFCR mode keyword to its EquationTableElm dump ordinal.
     * Mirrors SFCRUtil.parseEquationRowMode without loading EquationTableElm.
     */
    static int parseModeOrdinal(String mode) {
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
    static String escapeToken(String s) {
        if (s.length() == 0) return "\\0";
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace(" ", "\\s")
                .replace("+", "\\p").replace("=", "\\q").replace("#", "\\h")
                .replace("&", "\\a").replace("\r", "\\r");
    }

    /**
     * Parse a combined "name-&gt;target" notation.
     * Mirrors EquationTableElm.parseCombinedName() without loading that class.
     */
    static String[] parseCombinedNameLocal(String combined) {
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
     * fed to {@code CirSim.createCe()} are collected in the returned
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
        if (strict) {
            validateStrictInput(text);
        }
        SFCRParser parser = new SFCRParser(null);
        parser.pendingResult = new SFCRParseResult();
        parser.parse(text);
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
                "@info".equals(directive) || "@sankey".equals(directive);
    }

    private static boolean requiresRows(String directive) {
        return "@equations".equals(directive) || "@parameters".equals(directive);
    }

    private static boolean isValidEquationRow(String trimmedLine) {
        if (trimmedLine.startsWith("#") || trimmedLine.startsWith("%")) {
            return false;
        }
        return trimmedLine.contains("~");
    }

    /** Check if text appears to be in SFCR format. */
    public static boolean isSFCRFormat(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = text.trim();
        
        // Check for SFCR block markers
        if (trimmed.contains("@matrix") || 
            trimmed.contains("@equations") || 
            trimmed.contains("@parameters") ||
            trimmed.contains("@init") || trimmed.contains("@action") || trimmed.contains("@hints") ||
            trimmed.contains("@scope") || trimmed.contains("@circuit") ||
            trimmed.contains("@info") || trimmed.contains("@sankey") )
            {
            return true;
        }
        
        // Check for sfcr-style definitions at start
        String[] lines = trimmed.split("\n");
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
        
        createdElements.clear();
        hints.clear();
        scopeVariables.clear();
        scopeBlocks.clear();
        initSettings.clear();
        rawCircuitLines.clear();
        actionElementFromActionBlock = false;
        if (sim != null) sim.getSFCRDocumentState().clearBlockComments();
        currentY = 24;
        
        try {
            String[] lines = text.split("\n");
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
                
                // Parse block markers
                if (line.startsWith("@init")) {
                    pendingBlockComments.clear();
                    i = parseInitBlock(lines, i);
                } else if (line.startsWith("@action")) {
                    pendingBlockComments.clear();
                    i = parseActionBlock(lines, i);
                } else if (line.startsWith("@matrix")) {
                    if (inFence) pendingCommentsConsumedInFence = true;
                    storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_MATRIX,
                        extractBlockName(line, "@matrix"), pendingBlockComments);
                    i = parseMatrixBlock(lines, i);
                } else if (line.startsWith("@equations")) {
                    if (inFence) pendingCommentsConsumedInFence = true;
                    storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_EQUATIONS,
                        extractBlockName(line, "@equations"), pendingBlockComments);
                    i = parseEquationsBlock(lines, i);
                } else if (line.startsWith("@parameters")) {
                    if (inFence) pendingCommentsConsumedInFence = true;
                    storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_EQUATIONS,
                        extractBlockName(line, "@parameters"), pendingBlockComments);
                    i = parseParametersBlock(lines, i);
                } else if (line.startsWith("@hints")) {
                    pendingBlockComments.clear();
                    i = parseHintsBlock(lines, i);
                } else if (line.startsWith("@scope")) {
                    if (looksLikeScopeBlock(lines, i)) {
                        if (inFence) pendingCommentsConsumedInFence = true;
                        storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_SCOPE,
                            extractScopeBlockName(line), pendingBlockComments);
                        i = parseScopeBlock(lines, i);
                    } else {
                        pendingBlockComments.clear();
                        parseScopeLine(line);
                        i++;
                    }
                } else if (line.startsWith("@circuit")) {
                    pendingBlockComments.clear();
                    i = parseCircuitBlock(lines, i);
                } else if (line.startsWith("@sankey")) {
                    if (inFence) pendingCommentsConsumedInFence = true;
                    storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_SANKEY,
                        "", pendingBlockComments);
                    i = parseSankeyBlock(lines, i);
                } else if (line.startsWith("@info")) {
                    pendingBlockComments.clear();
                    i = parseInfoBlock(lines, i);
                } else if (line.contains("sfcr_matrix") || line.contains("<-")) {
                    // Try to parse R-style sfcr syntax
                    if (inFence) pendingCommentsConsumedInFence = true;
                    i = parseRStyleBlock(lines, i, pendingBlockComments);
                } else {
                    // Preserve non-block inline markdown context (headings/prose) so it
                    // can round-trip and remain associated with the next structural block.
                    if (!inFence) {
                        pendingBlockComments.add(lines[i]);
                    }
                    i++;
                }
            }

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
    
    // =========================================================================
    // Block Parsers
    // =========================================================================
    
    /** Parse metadata comment (% prefix - preserved on export). */
    private void parseMetadataComment(String line) {
        // Could store these for export, for now just skip
    }
    
    /**
     * Parse @init block - simulation settings.
    * Supports: timestep, voltageRange, voltageUnit, timeUnit, showToolbar,
    * showDots, showVolts, showValues, showPower, autoAdjustTimestep,
    * equationTableMnaMode, equationTableNewtonJacobianEnabled,
    * equationTableTolerance/equationTableConvergenceTolerance,
    * convergenceCheckThreshold, infoViewerUpdateIntervalMs.
     */
    private int parseInitBlock(String[] lines, int startIndex) {
        String headerLine = lines[startIndex].trim();
        
        // Check for inline format: @init key=value key=value
        String inlineParams = headerLine.substring(5).trim();
        if (!inlineParams.isEmpty()) {
            parseInitInline(inlineParams);
            return startIndex + 1;
        }
        
        // Block format
        int i = startIndex + 1;
        while (i < lines.length) {
            String line = lines[i].trim();
            
            // End of block
            if (line.startsWith("@end") || (line.startsWith("@") && !line.startsWith("@end"))) {
                break;
            }
            
            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }
            
            // Parse key: value or key=value
            int colonIdx = line.indexOf(':');
            int eqIdx = line.indexOf('=');
            int sepIdx = colonIdx >= 0 ? colonIdx : eqIdx;
            
            if (sepIdx > 0) {
                String key = line.substring(0, sepIdx).trim();
                String value = line.substring(sepIdx + 1).trim();
                // Remove trailing comment
                int commentIdx = value.indexOf('#');
                if (commentIdx >= 0) {
                    value = value.substring(0, commentIdx).trim();
                }
                initSettings.put(key, value);
            }
            
            i++;
        }
        
        // Skip @end if present
        if (i < lines.length && lines[i].trim().startsWith("@end")) {
            i++;
        }
        
        return i;
    }
    
    /** Parse inline init parameters: @init key=value key=value */
    private void parseInitInline(String params) {
        // Parse: key=value key=value or key:value
        String[] parts = params.split("\\s+");
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx < 0) idx = part.indexOf(':');
            if (idx > 0) {
                String key = part.substring(0, idx).trim();
                String value = part.substring(idx + 1).trim();
                initSettings.put(key, value);
            }
        }
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
                        sim.maxTimeStep = sim.timeStep = Double.parseDouble(value);
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
                    case "equationTableMnaMode":
                    case "eqnTableMnaMode":
                        sim.equationTableMnaMode = parseBoolean(value, sim.equationTableMnaMode);
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
                                sim.equationTableConvergenceTolerance = tol;
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
                    default:
                        CirSim.console("SFCRParser: Unknown init setting: " + key);
                }
            } catch (Exception e) {
                CirSim.console("SFCRParser: Invalid init value for " + key + ": " + value);
            }
        }
    }

    /** Parse @action block - timed target updates for ActionScheduler. */
    private int parseActionBlock(String[] lines, int startIndex) {
        String headerLine = lines[startIndex].trim();
        BlockPosition actionBlockPos = parseBlockHeader(headerLine, "@action");
        ActionScheduler scheduler = ActionScheduler.getInstance(sim);
        if (scheduler == null) {
            return startIndex + 1;
        }

        // Replace existing schedule with block contents.
        scheduler.clearAll();

        boolean hasAnyActionRows = false;
        boolean actionElmEnabled = true;
        boolean actionElmEnabledSpecified = false;
        boolean actionElmSpecified = false;
        int actionElmX1 = 704;
        int actionElmY1 = 416;
        int actionElmX2 = 720;
        int actionElmY2 = 432;
        int actionElmFlags = 0;
        String actionElmTitle = "Action Schedule";

        if (actionBlockPos != null && actionBlockPos.name != null && !actionBlockPos.name.isEmpty() &&
                !actionBlockPos.name.equalsIgnoreCase("action")) {
            actionElmTitle = actionBlockPos.name.replace('_', ' ');
        }
        if (actionBlockPos != null && actionBlockPos.hasPosition()) {
            actionElmX1 = actionBlockPos.x;
            actionElmY1 = actionBlockPos.y;
            actionElmX2 = actionElmX1 + 16;
            actionElmY2 = actionElmY1 + 16;
            actionElmSpecified = true;
        }

        int i = startIndex + 1;
        while (i < lines.length) {
            String line = lines[i].trim();

            if (line.startsWith("@end")) {
                i++;
                break;
            }

            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }

            int colonIdx = line.indexOf(':');
            if (colonIdx > 0 && !line.startsWith("|")) {
                String key = line.substring(0, colonIdx).trim().toLowerCase();
                String value = line.substring(colonIdx + 1).trim();

                try {
                    if (key.equals("pausetime") || key.equals("pause_time")) {
                        scheduler.setPauseTime(Double.parseDouble(value));
                    } else if (key.equals("enabled") || key.equals("actionelementenabled") || key.equals("action_element_enabled")) {
                        actionElmEnabled = parseBoolean(value, true);
                        actionElmEnabledSpecified = true;
                    } else if (key.equals("name") || key.equals("title")) {
                        if (value != null && value.trim().length() > 0) {
                            actionElmTitle = value.trim().replace('_', ' ');
                        }
                    } else if (key.equals("element") || key.equals("actionelement") || key.equals("action_element")) {
                        String[] parts = value.trim().split("\\s+");
                        if (parts.length >= 4) {
                            actionElmX1 = Integer.parseInt(parts[0]);
                            actionElmY1 = Integer.parseInt(parts[1]);
                            actionElmX2 = Integer.parseInt(parts[2]);
                            actionElmY2 = Integer.parseInt(parts[3]);
                            if (parts.length >= 5) {
                                actionElmFlags = Integer.parseInt(parts[4]);
                            }
                            actionElmSpecified = true;
                        }
                    } else if (key.equals("animationtime") || key.equals("animation_time")) {
                        // legacy key: ignored
                    }
                } catch (Exception e) {
                    CirSim.console("SFCRParser: Invalid @action setting " + key + "=" + value);
                }

                i++;
                continue;
            }

            if (line.startsWith("|")) {
                // Skip markdown header/separator rows.
                if (line.contains("---") || line.toLowerCase().contains("time") && line.toLowerCase().contains("target")) {
                    i++;
                    continue;
                }

                String[] cells = parseTableRow(line);
                if (cells.length >= 6) {
                    try {
                        double actionTime = Double.parseDouble(cells[0].trim());
                        String target = SFCRUtil.unescapeTableCell(cells[1]);
                        String valueSpec = SFCRUtil.unescapeTableCell(cells[2]);
                        String postText = SFCRUtil.unescapeTableCell(cells[3]);
                        boolean enabled = parseBoolean(cells[4], true);
                        boolean stop = parseBoolean(cells[5], false);

                        double numericValue = 0.0;
                        String expression = "";
                        String trimmedValue = valueSpec == null ? "" : valueSpec.trim();
                        if (!trimmedValue.isEmpty()) {
                            char lead = trimmedValue.charAt(0);
                            if (lead == '+' || lead == '-' || lead == '*' || lead == '=') {
                                expression = trimmedValue;
                            } else {
                                numericValue = Double.parseDouble(trimmedValue);
                            }
                        }

                        ActionScheduler.ScheduledAction action =
                            new ActionScheduler.ScheduledAction(0, actionTime, target,
                                numericValue, "", postText, enabled, stop);
                        action.valueExpression = expression;
                        scheduler.addAction(action);
                        hasAnyActionRows = true;
                    } catch (Exception e) {
                        CirSim.console("SFCRParser: Invalid @action row: " + line);
                    }
                }
            }

            i++;
        }

        if (hasAnyActionRows || actionElmEnabledSpecified || actionElmSpecified) {
            ActionTimeElm actionElm = findActionTimeElm();
            if (actionElm == null) {
                actionElm = new ActionTimeElm(actionElmX1, actionElmY1, actionElmX2, actionElmY2, actionElmFlags, null);
                actionElm.setPoints();
                sim.assignPersistentUid(actionElm, null);
                sim.elmList.addElement(actionElm);
                createdElements.add(actionElm);
            } else if (actionElmSpecified) {
                actionElm.x = actionElmX1;
                actionElm.y = actionElmY1;
                actionElm.x2 = actionElmX2;
                actionElm.y2 = actionElmY2;
                actionElm.flags = actionElmFlags;
                actionElm.setPoints();
            }

            actionElm.title = actionElmTitle;

            if (actionElmEnabledSpecified || actionElmSpecified || hasAnyActionRows) {
                actionElm.enabled = actionElmEnabled;
            }
            actionElementFromActionBlock = true;
        }

        return i;
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
    
    /** Parse @matrix block - creates SFCTableElm. */
    private int parseMatrixBlock(String[] lines, int startIndex) {
        String headerLine = lines[startIndex].trim();
        BlockPosition blockPos = parseBlockHeader(headerLine, "@matrix");
        String matrixName = blockPos.name;
        
        // Store position for element creation
        int savedX = currentX;
        int savedY = currentY;
        if (blockPos.hasPosition()) {
            currentX = blockPos.x;
            currentY = blockPos.y;
        }
        
        // Parse matrix properties
        ArrayList<String> columnNames = new ArrayList<String>();
        ArrayList<String> columnCodes = new ArrayList<String>();
        String matrixType = "transaction_flow"; // default
        Boolean showInitialValues = null;
        Boolean showFlowValues = null;
        Boolean useBackwardEuler = null;
        
        int i = startIndex + 1;
        ArrayList<String[]> tableRows = new ArrayList<String[]>();
        ArrayList<String> rowNames = new ArrayList<String>();
        
        while (i < lines.length) {
            String line = lines[i].trim();
            
            // End of block
            if (line.startsWith("@end")) {
                i++;
                break;
            }
            
            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }
            
            // Parse key/value properties
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0 && !line.startsWith("|")) {
                String key = line.substring(0, colonIdx).trim().toLowerCase();
                String value = line.substring(colonIdx + 1).trim();

                if (key.equals("columns")) {
                    columnNames = parseCommaSeparatedList(value);
                    i++;
                    continue;
                }

                if (key.equals("codes")) {
                    columnCodes = parseCommaSeparatedList(value);
                    i++;
                    continue;
                }

                if (key.equals("type")) {
                    matrixType = value;
                    i++;
                    continue;
                }

                if (key.equals("showflowvalues") || key.equals("show_flow_values")) {
                    showFlowValues = Boolean.valueOf(value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("yes"));
                    i++;
                    continue;
                }

                if (key.equals("showinitialvalues") || key.equals("show_initial_values")) {
                    showInitialValues = Boolean.valueOf(value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("yes"));
                    i++;
                    continue;
                }

                if (key.equals("integration")) {
                    String mode = value.toLowerCase();
                    useBackwardEuler = Boolean.valueOf(mode.equals("backward_euler") || mode.equals("backward euler") || mode.equals("backwardeuler"));
                    i++;
                    continue;
                }

                if (key.equals("usebackwardeuler") || key.equals("use_backward_euler")) {
                    useBackwardEuler = Boolean.valueOf(value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("yes"));
                    i++;
                    continue;
                }
            }
            
            // Parse markdown table row
            if (line.startsWith("|")) {
                // Skip separator rows (|---|---|)
                if (line.contains("---")) {
                    i++;
                    continue;
                }
                
                String[] cells = parseTableRow(line);
                if (cells.length > 1) {
                    // If no columns defined yet, this is the header row
                    if (columnNames.isEmpty()) {
                        // Use cells 1..n as column names (skip cell 0 which is row header label)
                        for (int j = 1; j < cells.length; j++) {
                            columnNames.add(cells[j]);
                        }
                    } else {
                        // This is a data row
                        // First cell is row name
                        rowNames.add(cells[0]);
                        
                        // Rest are cell values (match number of columns)
                        String[] rowData = new String[columnNames.size()];
                        for (int j = 0; j < columnNames.size(); j++) {
                            if (j + 1 < cells.length) {
                                rowData[j] = cells[j + 1];
                            } else {
                                rowData[j] = "";
                            }
                        }
                        tableRows.add(rowData);
                    }
                }
            }
            
            i++;
        }
        
        // Create matrix table from parsed data
        if (!columnNames.isEmpty() && !tableRows.isEmpty()) {
            createMatrixTable(matrixName, columnNames, rowNames, tableRows, matrixType,
                              showInitialValues, showFlowValues, useBackwardEuler);
        }
        
        // Restore position if we used explicit positioning
        if (blockPos.hasPosition()) {
            currentX = savedX;
            // Keep currentY updated from element creation
        }
        
        return i;
    }
    
    /** Parse @equations block - creates EquationTableElm. Inline # comments become hints. */
    private int parseEquationsBlock(String[] lines, int startIndex) {
        String headerLine = lines[startIndex].trim();
        BlockPosition blockPos = parseBlockHeader(headerLine, "@equations");
        String blockName = blockPos.name;
        
        // Store position for element creation
        int savedX = currentX;
        int savedY = currentY;
        if (blockPos.hasPosition()) {
            currentX = blockPos.x;
            currentY = blockPos.y;
        }
        
        ArrayList<String> outputNames = new ArrayList<String>();
        ArrayList<String> equations = new ArrayList<String>();
        ArrayList<Integer> outputModes = new ArrayList<Integer>();
        ArrayList<String> targetNodeNames = new ArrayList<String>();
        ArrayList<String> sliderVarNames = new ArrayList<String>();
        ArrayList<Double> sliderValues = new ArrayList<Double>();
        ArrayList<String> initialEquations = new ArrayList<String>();
        
        int i = startIndex + 1;
        
        while (i < lines.length) {
            String line = lines[i].trim();
            
            // End of block
            if (line.startsWith("@end")) {
                i++;
                break;
            }
            
            // Keep full-line comments as dedicated non-simulating table rows
            if (line.startsWith("#")) {
                appendCommentRow(line,
                    outputNames, equations, outputModes, targetNodeNames,
                    sliderVarNames, sliderValues, initialEquations);
                i++;
                continue;
            }

            // Skip empty lines
            if (line.isEmpty()) {
                i++;
                continue;
            }

            // Extract inline comment as hint BEFORE removing it
            String inlineComment = null;
            int commentIdx = line.indexOf('#');
            if (commentIdx > 0) {
                inlineComment = line.substring(commentIdx + 1).trim();
                line = line.substring(0, commentIdx).trim();
            }
            
            // Parse equation: "name ~ expression" or "name = expression"
            String[] parts = null;
            if (line.contains("~")) {
                parts = line.split("~", 2);
            } else if (line.contains("=")) {
                parts = line.split("=", 2);
            }
            
            if (parts != null && parts.length == 2) {
                String leftPart = parts[0].trim();
                String rightPart = parts[1].trim();

                String exprText = rightPart;
                HashMap<String, String> rowMeta = new HashMap<String, String>();
                int metaIdx = rightPart.indexOf(';');
                if (metaIdx >= 0) {
                    exprText = rightPart.substring(0, metaIdx).trim();
                    String metaText = rightPart.substring(metaIdx + 1).trim();
                    String[] metaParts = metaText.split(";");
                    for (int m = 0; m < metaParts.length; m++) {
                        String token = metaParts[m].trim();
                        int eq = token.indexOf('=');
                        if (eq > 0) {
                            String key = token.substring(0, eq).trim().toLowerCase();
                            String val = token.substring(eq + 1).trim();
                            rowMeta.put(key, val);
                        }
                    }
                }

                String[] lhsAliasParts = splitDifferenceLeftAlias(leftPart);
                boolean hasDifferenceAlias = lhsAliasParts[1] != null && !lhsAliasParts[1].isEmpty();

                String[] nameParts = parseCombinedNameLocal(lhsAliasParts[0]);
                String name = SFCRUtil.normalizeVariableName(nameParts[0]);

                String targetName = "";
                if (nameParts[1] != null && !nameParts[1].trim().isEmpty()) {
                    targetName = SFCRUtil.normalizeVariableName(nameParts[1].trim());
                }

                String expr = SFCRUtil.normalizeExpression(exprText);
                
                // Keep lag notation exactly as imported (e.g. X[-1], X(-1), X [ - 1 ]).
                // Equation parsing/evaluation supports lag forms directly.
                
                outputNames.add(name);
                if (hasDifferenceAlias) {
                    String lhsDisplay = lhsAliasParts[0] + " - " + lhsAliasParts[1];
                    equations.add(lhsDisplay + " = " + expr);
                } else {
                    equations.add(expr);
                }

                int mode = parseModeOrdinal(rowMeta.get("mode"));
                if (mode == 0 && !targetName.isEmpty()) {
                    mode = 1;  // FLOW_MODE (has a target but no explicit flow mode)
                }
                outputModes.add(mode);

                if (targetName.isEmpty()) {
                    String metaTarget = rowMeta.get("target");
                    if (metaTarget != null && !metaTarget.trim().isEmpty()) {
                        targetName = SFCRUtil.normalizeVariableName(metaTarget.trim());
                    }
                }
                targetNodeNames.add(targetName);

                String sliderVar = rowMeta.get("slider");
                if (sliderVar == null) {
                    sliderVar = "";
                } else {
                    sliderVar = sliderVar.trim();
                }
                sliderVarNames.add(sliderVar);

                double sliderValue = 0.0;
                String sliderValueStr = rowMeta.get("slidervalue");
                if (sliderValueStr != null) {
                    try {
                        sliderValue = Double.parseDouble(sliderValueStr);
                    } catch (Exception e) {
                    }
                }
                sliderValues.add(Double.valueOf(sliderValue));

                String initEq = rowMeta.get("initial");
                initialEquations.add((initEq != null) ? initEq : "");
                
                // Store inline comment as auto-hint (only if not already set by @hints)
                if (inlineComment != null && !inlineComment.isEmpty() && !hints.containsKey(name)) {
                    hints.put(name, inlineComment);
                }
            }
            
            i++;
        }
        
        // Create EquationTableElm from parsed data
        if (!outputNames.isEmpty()) {
            createEquationTable(blockName, outputNames, equations, outputModes,
                targetNodeNames, sliderVarNames, sliderValues, initialEquations);
        }
        
        // Restore position if we used explicit positioning
        if (blockPos.hasPosition()) {
            currentX = savedX;
            // Keep currentY updated from element creation
        }
        
        return i;
    }
    
    /** Parse @parameters block (constants - same as @equations). */
    private int parseParametersBlock(String[] lines, int startIndex) {
        // Parameters are just equations with constant values
        return parseEquationsBlock(lines, startIndex);
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
    
    /** Parse @hints block - overrides auto-generated hints from inline comments. */
    private int parseHintsBlock(String[] lines, int startIndex) {
        int i = startIndex + 1;
        
        while (i < lines.length) {
            String line = lines[i].trim();
            
            // End of block
            if (line.startsWith("@end")) {
                i++;
                break;
            }
            
            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }
            
            // Parse hint: "varname: description"
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String varName = SFCRUtil.normalizeVariableName(line.substring(0, colonIdx).trim());
                String description = line.substring(colonIdx + 1).trim();
                hints.put(varName, description);
            }
            
            i++;
        }
        
        return i;
    }
    
    /** Parse @scope line. */
    private void parseScopeLine(String line) {
        String varName = line.substring(6).trim(); // Remove "@scope"
        varName = SFCRUtil.normalizeVariableName(varName);
        if (!varName.isEmpty()) {
            scopeVariables.add(varName);
        }
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

    /** Parse @scope block with UID-based source/trace references. */
    private int parseScopeBlock(String[] lines, int startIndex) {
        String header = lines[startIndex].trim();
        ScopeBlockSpec spec = new ScopeBlockSpec();

        String rest = header.substring(6).trim(); // Remove "@scope"
        if (!rest.isEmpty()) {
            String[] parts = rest.split("\\s+");
            StringBuilder nameBuilder = new StringBuilder();
            for (String part : parts) {
                if (part.toLowerCase().startsWith("position=")) {
                    try {
                        spec.position = Integer.parseInt(part.substring(9));
                    } catch (Exception e) {
                        // Ignore malformed position
                    }
                } else {
                    if (nameBuilder.length() > 0) {
                        nameBuilder.append(" ");
                    }
                    nameBuilder.append(part);
                }
            }
            if (nameBuilder.length() > 0) {
                spec.name = nameBuilder.toString();
            }
        }

        int i = startIndex + 1;
        while (i < lines.length) {
            String line = lines[i].trim();

            if (line.equals("@end")) {
                i++;
                break;
            }
            if (line.startsWith("@")) {
                break;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }

            int sep = line.indexOf(':');
            int eq = line.indexOf('=');
            int split = sep >= 0 ? sep : eq;
            if (split > 0) {
                String key = line.substring(0, split).trim().toLowerCase();
                String value = line.substring(split + 1).trim();

                if (key.equals("speed")) {
                    try {
                        spec.speed = Integer.parseInt(value);
                    } catch (Exception e) {
                        // Ignore malformed speed
                    }
                } else if (key.equals("flags")) {
                    try {
                        spec.flags = Scope.importDecOrHex(value);
                    } catch (Exception e) {
                        // Ignore malformed flags
                    }
                } else if (key.equals("title")) {
                    spec.title = CustomLogicModel.unescape(value);
                } else if (key.equals("label")) {
                    spec.label = CustomLogicModel.unescape(value);
                } else if (key.equals("source") || key.equals("trace")) {
                    ScopeTraceSpec trace = parseScopeTraceSpec(value);
                    if (trace != null && trace.uid != null && trace.uid.length() > 0) {
                        if (key.equals("source")) {
                            spec.traces.add(0, trace);
                        } else {
                            spec.traces.add(trace);
                        }
                    }
                } else if (key.equals("x1")) {
                    try { spec.x1 = Integer.parseInt(value); } catch (Exception e) {}
                } else if (key.equals("y1")) {
                    try { spec.y1 = Integer.parseInt(value); } catch (Exception e) {}
                } else if (key.equals("x2")) {
                    try { spec.x2 = Integer.parseInt(value); } catch (Exception e) {}
                } else if (key.equals("y2")) {
                    try { spec.y2 = Integer.parseInt(value); } catch (Exception e) {}
                } else if (key.equals("elmuid")) {
                    spec.elmUid = value;
                }
            }

            i++;
        }

        if (!spec.traces.isEmpty()) {
            scopeBlocks.add(spec);
        }

        return i;
    }

    /** Parse "uid:ABC123 value:0" or "uid=ABC123 value=0" trace payload. */
    private ScopeTraceSpec parseScopeTraceSpec(String payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }

        ScopeTraceSpec trace = new ScopeTraceSpec();
        String[] parts = payload.split("\\s+");
        for (String part : parts) {
            if (part.toLowerCase().startsWith("uid:")) {
                trace.uid = part.substring(4);
            } else if (part.toLowerCase().startsWith("uid=")) {
                trace.uid = part.substring(4);
            } else if (part.toLowerCase().startsWith("value:")) {
                try {
                    trace.value = Integer.parseInt(part.substring(6));
                } catch (Exception e) {
                    // Keep default
                }
            } else if (part.toLowerCase().startsWith("value=")) {
                try {
                    trace.value = Integer.parseInt(part.substring(6));
                } catch (Exception e) {
                    // Keep default
                }
            }
        }

        if (trace.uid != null && trace.uid.length() > 0) {
            return trace;
        }
        return null;
    }
    
    /** Parse @circuit block - raw CircuitJS element definitions (passthrough). */
    private int parseCircuitBlock(String[] lines, int startIndex) {
        int i = startIndex + 1;
        String hintedBlockType = "";
        String hintedBlockName = "";
        
        while (i < lines.length) {
            String line = lines[i].trim();
            
            // Check for end of block
            if (line.equals("@end") || line.startsWith("@")) {
                if (line.equals("@end")) {
                    return i + 1;
                }
                return i;  // Start of another block
            }
            
            // Skip empty lines and comments
            if (line.isEmpty()) {
                i++;
                continue;
            }

            // Optional metadata hint for result-mode export/import round-trips:
            // #@sfcrblock equations equations_1A
            if (line.startsWith("#")) {
                if (pendingResult != null && line.startsWith("#@sfcrblock")) {
                    String[] parts = line.split("\\s+", 3);
                    hintedBlockType = (parts.length >= 2) ? parts[1].trim() : "";
                    hintedBlockName = (parts.length >= 3) ? parts[2].trim() : "";
                }
                i++;
                continue;
            }

            // If @action already carried ActionTimeElm state, skip duplicate raw 432 line.
            if (actionElementFromActionBlock && line.startsWith("432 ")) {
                i++;
                continue;
            }
            
            // Store raw circuit line for later processing
            rawCircuitLines.add(line);

            // In result-mode, also recover structured block dumps from known dump types.
            if (pendingResult != null) {
                String blockType = hintedBlockType;
                String blockName = hintedBlockName;

                if (blockType.isEmpty()) {
                    blockType = inferBlockTypeFromCircuitDumpLine(line);
                    blockName = "";
                }

                if (blockType != null && !blockType.isEmpty()) {
                    pendingResult.blockDumps.add(new SFCRParseResult.BlockDump(blockType, blockName, line));
                }

                hintedBlockType = "";
                hintedBlockName = "";
            }
            i++;
        }
        
        return i;
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
    
    /** 
     * Parse @sankey block - creates SFCSankeyElm.
     * Format:
     *   @sankey [Title] x=X y=Y
     *     source: TableName   (optional - blank for auto)
     *     layout: linear|circular
     *     width: 300
     *     height: 250
     *   @end
     */
    private int parseSankeyBlock(String[] lines, int startIndex) {
        String headerLine = lines[startIndex].trim();
        BlockPosition blockPos = parseBlockHeader(headerLine, "@sankey");
        String sourceName = "";
        String layout = "LINEAR";
        int width = 300;
        int height = 250;
        // Scale visualization options
        boolean showScaleBar = true;
        double fixedMaxScale = 0;
        boolean useHighWaterMark = false;
        boolean showFlowValues = false;
        
        int i = startIndex + 1;
        
        // Parse options
        while (i < lines.length) {
            String line = lines[i].trim();
            
            // Check for end of block
            if (line.equals("@end") || line.startsWith("@")) {
                if (line.equals("@end")) {
                    i++;
                }
                break;
            }
            
            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }
            
            // Parse key: value
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String key = line.substring(0, colonIdx).trim().toLowerCase();
                String value = line.substring(colonIdx + 1).trim();
                
                // Remove trailing comment
                int commentIdx = value.indexOf('#');
                if (commentIdx >= 0) {
                    value = value.substring(0, commentIdx).trim();
                }
                
                switch (key) {
                    case "source":
                        sourceName = value;
                        break;
                    case "layout":
                        layout = value.toUpperCase();
                        if (!layout.equals("CIRCULAR")) {
                            layout = "LINEAR";
                        }
                        break;
                    case "width":
                        try {
                            width = Integer.parseInt(value);
                        } catch (Exception e) {}
                        break;
                    case "height":
                        try {
                            height = Integer.parseInt(value);
                        } catch (Exception e) {}
                        break;
                    case "showscalebar":
                        showScaleBar = value.equalsIgnoreCase("true") || value.equals("1");
                        break;
                    case "fixedmaxscale":
                        try {
                            fixedMaxScale = Double.parseDouble(value);
                        } catch (Exception e) {}
                        break;
                    case "usehighwatermark":
                        useHighWaterMark = value.equalsIgnoreCase("true") || value.equals("1");
                        break;
                    case "showflowlabels":  // backward compatibility
                    case "showflowvalues":
                        showFlowValues = value.equalsIgnoreCase("true") || value.equals("1");
                        break;
                }
            }
            
            i++;
        }
        
        // Create the Sankey element
        int posX = blockPos.hasPosition() ? blockPos.x : currentX;
        int posY = blockPos.hasPosition() ? blockPos.y : currentY;

        // Build dump string first so result-mode can capture it without loading GWT classes.
        String dumpStr = "466 " + posX + " " + posY + " " + (posX + 16) + " " + (posY + 16) + " 0 " +
                         escapeToken(sourceName) + " " + layout + " " + width + " " + height + " " +
                         (showScaleBar ? "1" : "0") + " " + fixedMaxScale + " " +
                         (useHighWaterMark ? "1" : "0") + " " + (showFlowValues ? "1" : "0");

        // Result-mode: keep dump only, do not instantiate SFCSankeyElm/CircuitElm.
        if (pendingResult != null) {
            pendingResult.blockDumps.add(new SFCRParseResult.BlockDump("sankey", "", dumpStr));
            if (!blockPos.hasPosition()) {
                currentY += height + elementSpacing;
            }
            return i;
        }

        SFCSankeyElm sankeyElm = new SFCSankeyElm(posX, posY);
        
        try {
            StringTokenizer st = new StringTokenizer(dumpStr);
            st.nextToken(); // skip dump type
            int x1 = Integer.parseInt(st.nextToken());
            int y1 = Integer.parseInt(st.nextToken());
            int x2 = Integer.parseInt(st.nextToken());
            int y2 = Integer.parseInt(st.nextToken());
            int f = Integer.parseInt(st.nextToken());
            
            sankeyElm = new SFCSankeyElm(x1, y1, x2, y2, f, st);
        } catch (Exception e) {
            CirSim.console("SFCRParser: Error creating Sankey element: " + e.getMessage());
        }
        
        sim.assignPersistentUid(sankeyElm, null);
        sim.elmList.addElement(sankeyElm);
        createdElements.add(sankeyElm);
        
        // Update position for next element
        if (!blockPos.hasPosition()) {
            currentY += height + elementSpacing;
        }
        
        return i;
    }
    
    /** Get the @info block content (markdown), or null if not present. */
    public String getInfoContent() {
        return infoContent;
    }
    
    /** Parse @info block - markdown documentation for InfoViewerDialog. */
    private int parseInfoBlock(String[] lines, int startIndex) {
        String headerLine = lines[startIndex].trim();
        
        // Extract optional title from header line (e.g., "@info Model Documentation")
        String title = headerLine.length() > 5 ? headerLine.substring(5).trim() : "";
        
        StringBuilder content = new StringBuilder();
        
        int i = startIndex + 1;  // Skip @info line
        
        // Check if first content line already starts with a markdown header
        boolean hasMarkdownHeader = false;
        if (i < lines.length) {
            String firstLine = lines[i].trim();
            if (firstLine.startsWith("#") && !firstLine.equals("@end") && !firstLine.startsWith("@")) {
                hasMarkdownHeader = true;
            }
        }
        
        // Only add a title header if there isn't one already and we have a title
        if (!hasMarkdownHeader && !title.isEmpty()) {
            content.append("# ").append(title).append("\n\n");
        }
        
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();
            
            // Check for end of block
            if (trimmed.equals("@end") || (trimmed.startsWith("@") && !trimmed.equals("@end"))) {
                if (trimmed.equals("@end")) {
                    i++;
                }
                break;
            }
            
            // Preserve the line as-is (including indentation for code blocks)
            content.append(line).append("\n");
            i++;
        }
        
        infoContent = content.toString();
        return i;
    }

    // =========================================================================
    // R-Style sfcr Syntax Support
    // =========================================================================
    
    /** Parse R-style sfcr_matrix() or sfcr_set() syntax. */
    private int parseRStyleBlock(String[] lines, int startIndex, Vector<String> pendingBlockComments) {
        RStyleBlockMetadata metadata = consumeRStyleMetadataFromComments(pendingBlockComments);
        StringBuilder blockText = new StringBuilder();
        int i = startIndex;
        int parenDepth = 0;
        boolean inBlock = false;
        
        // Collect lines until parentheses are balanced
        while (i < lines.length) {
            String line = lines[i];
            blockText.append(line).append("\n");
            
            for (char c : line.toCharArray()) {
                if (c == '(') {
                    parenDepth++;
                    inBlock = true;
                }
                if (c == ')') {
                    parenDepth--;
                }
            }
            
            i++;
            
            if (inBlock && parenDepth == 0) {
                break;
            }
        }
        
        String block = blockText.toString();

        String blockName = extractRStyleAssignmentName(block, "Equations");
        
        // Determine block type and parse
        if (block.contains("sfcr_matrix")) {
            blockName = extractRStyleAssignmentName(block, "Matrix");
            storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_MATRIX, blockName, pendingBlockComments);
            parseRStyleMatrix(block, metadata);
        } else if (block.contains("sfcr_set")) {
            storePendingBlockComments(SFCRBlockCommentRegistry.TYPE_EQUATIONS, blockName, pendingBlockComments);
            parseRStyleEquations(block, metadata);
        } else if (pendingBlockComments != null) {
            pendingBlockComments.clear();
        }
        
        return i;
    }

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

    /** Consume and parse one or more metadata comments (# [ ... ]) from pending comments. */
    private RStyleBlockMetadata consumeRStyleMetadataFromComments(Vector<String> pendingComments) {
        RStyleBlockMetadata metadata = new RStyleBlockMetadata();
        if (pendingComments == null || pendingComments.size() == 0) {
            return metadata;
        }

        Vector<String> preserved = new Vector<String>();
        for (int i = 0; i < pendingComments.size(); i++) {
            String raw = pendingComments.get(i);
            if (!parseRStyleMetadataLine(raw, metadata)) {
                preserved.add(raw);
            }
        }

        pendingComments.clear();
        for (int i = 0; i < preserved.size(); i++) {
            pendingComments.add(preserved.get(i));
        }
        return metadata;
    }

    /** Parse one metadata line in # [ ... ] syntax. */
    private boolean parseRStyleMetadataLine(String rawLine, RStyleBlockMetadata metadata) {
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
                continue;
            }
            if (token.startsWith("y=")) {
                Integer parsed = parseIntSafe(token.substring(2));
                if (parsed != null) {
                    metadata.y = parsed.intValue();
                }
                continue;
            }
            if (token.startsWith("type=")) {
                String value = token.substring(5).trim();
                if (!value.isEmpty()) {
                    metadata.type = value;
                }
                continue;
            }
            if (token.equals("type:") && i + 1 < tokens.length) {
                String value = tokens[++i].trim();
                if (!value.isEmpty()) {
                    metadata.type = value;
                }
                continue;
            }
            if (token.startsWith("type:")) {
                String value = token.substring(5).trim();
                if (!value.isEmpty()) {
                    metadata.type = value;
                }
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
    
    /** Parse R-style sfcr_matrix definition. */
    private void parseRStyleMatrix(String block, RStyleBlockMetadata metadata) {
        // Extract matrix name from assignment
        String matrixName = "Matrix";
        int assignIdx = block.indexOf("<-");
        if (assignIdx > 0) {
            matrixName = block.substring(0, assignIdx).trim();
        }
        
        // Extract columns
        ArrayList<String> columnNames = extractRVector(block, "columns");
        ArrayList<String> columnCodes = extractRVector(block, "codes");

        RStyleBlockMetadata effectiveMetadata = new RStyleBlockMetadata();
        if (metadata != null) {
            effectiveMetadata.x = metadata.x;
            effectiveMetadata.y = metadata.y;
            effectiveMetadata.type = metadata.type;
        }

        int matrixStart = block.indexOf("sfcr_matrix(");
        if (matrixStart >= 0) {
            int contentStart = matrixStart + "sfcr_matrix(".length();
            int contentEnd = findMatchingParen(block, contentStart - 1);
            if (contentEnd > contentStart) {
                String content = block.substring(contentStart, contentEnd);
                String[] contentLines = content.split("\\r?\\n");
                for (int li = 0; li < contentLines.length; li++) {
                    String trimmed = contentLines[li].trim();
                    if (trimmed.startsWith("#")) {
                        parseRStyleMetadataLine(trimmed, effectiveMetadata);
                    }
                }
            }
        }
        
        // Extract rows: c("RowName", code = "expr", ...)
        // Parse top-level sfcr_matrix(...) arguments so we don't treat
        // columns=c(...) and codes=c(...) vectors as table rows.
        ArrayList<String> rowNames = new ArrayList<String>();
        ArrayList<String[]> tableRows = new ArrayList<String[]>();

        if (matrixStart >= 0) {
            int contentStart = matrixStart + "sfcr_matrix(".length();
            int contentEnd = findMatchingParen(block, contentStart - 1);
            if (contentEnd > contentStart) {
                String content = block.substring(contentStart, contentEnd);
                ArrayList<String> args = splitByTopLevelComma(content);
                for (int ai = 0; ai < args.size(); ai++) {
                    String arg = args.get(ai).trim();
                    if (arg.startsWith("c(")) {
                        int argEnd = arg.lastIndexOf(')');
                        if (argEnd > 2) {
                            String rowDef = arg.substring(2, argEnd);
                            parseRStyleRow(rowDef, rowNames, tableRows, columnCodes);
                        }
                    }
                }
            }
        }
        
        int savedX = currentX;
        int savedY = currentY;
        if (effectiveMetadata.hasPosition()) {
            currentX = effectiveMetadata.x;
            currentY = effectiveMetadata.y;
        }

        // Create table if we have data
        if (!columnNames.isEmpty() && !tableRows.isEmpty()) {
            String matrixType = (effectiveMetadata.type != null && !effectiveMetadata.type.trim().isEmpty())
                ? effectiveMetadata.type.trim() : "transaction_flow";
            createMatrixTable(matrixName, columnNames, rowNames, tableRows, matrixType,
                              null, null, null);
        }

        if (effectiveMetadata.hasPosition()) {
            currentX = savedX;
            currentY = savedY;
        }
    }
    
    /** Parse a single R-style row: "RowName", code = "expr", ... */
    private void parseRStyleRow(String rowDef, ArrayList<String> rowNames, 
                                 ArrayList<String[]> tableRows, ArrayList<String> codes) {
        int firstQuote = rowDef.indexOf('"');
        int secondQuote = findClosingQuote(rowDef, firstQuote);
        if (firstQuote < 0 || secondQuote < 0) return;
        
        String rowName = unescapeRString(rowDef.substring(firstQuote + 1, secondQuote));
        rowNames.add(rowName);
        
        // Initialize row with empty values
        String[] rowData = new String[codes.size()];
        for (int i = 0; i < rowData.length; i++) {
            rowData[i] = "";
        }
        
        // Parse code = "expr" pairs
        String rest = rowDef.substring(secondQuote + 1);
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            String exprValue = extractRQuotedAssignmentValue(rest, code);
            if (exprValue != null) {
                rowData[i] = SFCRUtil.normalizeExpression(exprValue);
            }
        }
        
        tableRows.add(rowData);
    }

    /** Extract quoted assignment value for code = "..." from a row definition tail. */
    private String extractRQuotedAssignmentValue(String text, String code) {
        if (text == null || code == null || code.length() == 0) {
            return null;
        }

        String pattern1 = code + " = \"";
        String pattern2 = code + "=\"";
        int idx = text.indexOf(pattern1);
        int patternLen = pattern1.length();
        if (idx < 0) {
            idx = text.indexOf(pattern2);
            patternLen = pattern2.length();
        }
        if (idx < 0) {
            return null;
        }

        int valueStart = idx + patternLen;
        int valueEnd = findClosingQuote(text, valueStart - 1);
        if (valueEnd <= valueStart) {
            return null;
        }

        return unescapeRString(text.substring(valueStart, valueEnd));
    }
    
    /** Parse R-style sfcr_set for equations. */
    private void parseRStyleEquations(String block, RStyleBlockMetadata metadata) {
        // Extract name from assignment
        String blockName = extractRStyleAssignmentName(block, "Equations");
        
        ArrayList<String> outputNames = new ArrayList<String>();
        ArrayList<String> equations = new ArrayList<String>();
        ArrayList<Integer> outputModes = new ArrayList<Integer>();
        ArrayList<String> targetNodeNames = new ArrayList<String>();
        ArrayList<String> sliderVarNames = new ArrayList<String>();
        ArrayList<Double> sliderValues = new ArrayList<Double>();
        ArrayList<String> initialEquations = new ArrayList<String>();
        
        // Find content between sfcr_set( and final )
        int start = block.indexOf("sfcr_set(");
        if (start < 0) return;
        start += 9;
        
        int end = findMatchingParen(block, start - 1);
        if (end < 0) return;
        
        String content = block.substring(start, end);

        RStyleBlockMetadata effectiveMetadata = new RStyleBlockMetadata();
        if (metadata != null) {
            effectiveMetadata.x = metadata.x;
            effectiveMetadata.y = metadata.y;
            effectiveMetadata.type = metadata.type;
        }
        int currentSectionMode = 0;  // VOLTAGE_MODE
        
        // Split by commas (but not commas inside parentheses)
        ArrayList<String> parts = splitByTopLevelComma(content);
        
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            // Each comma-delimited token may contain multiple lines (metadata comments,
            // section comments, and one equation line). We normalize into one equation
            // string while preserving standalone comments as non-simulating rows.
            String[] partLines = part.split("\\r?\\n");
            StringBuilder cleanedPart = new StringBuilder();
            for (int li = 0; li < partLines.length; li++) {
                String line = partLines[li].trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#")) {
                    if (parseRStyleMetadataLine(line, effectiveMetadata)) {
                        continue;
                    }

                    String sectionText = line.substring(1).trim().toLowerCase();
                    if (isParametersSectionComment(sectionText)) {
                        currentSectionMode = 3;  // PARAM_MODE
                    }

                    appendCommentRow(line,
                        outputNames, equations, outputModes, targetNodeNames,
                        sliderVarNames, sliderValues, initialEquations);
                    continue;
                }
                if (cleanedPart.length() > 0) {
                    cleanedPart.append(" ");
                }
                cleanedPart.append(line);
            }

            part = cleanedPart.toString().trim();
            if (part.isEmpty()) {
                continue;
            }

            int inlineCommentIdx = part.indexOf('#');
            String inlineComment = null;
            if (inlineCommentIdx >= 0) {
                inlineComment = part.substring(inlineCommentIdx + 1).trim();
                part = part.substring(0, inlineCommentIdx).trim();
                if (part.isEmpty()) {
                    continue;
                }
            }

            // Defensive cleanup: a trailing delimiter comma may remain when the
            // source used "...,  # hint [meta]" style. Remove it before parsing.
            while (part.endsWith(",")) {
                part = part.substring(0, part.length() - 1).trim();
            }
            if (part.isEmpty()) {
                continue;
            }

            HashMap<String, String> inlineMeta = new HashMap<String, String>();
            if (inlineComment != null && !inlineComment.isEmpty()) {
                String parsedHint = stripTrailingRStyleInlineMetadata(inlineComment, inlineMeta);
                inlineComment = (parsedHint == null) ? "" : parsedHint;
            }
            
            // Strip named equation prefix: "e1 = TX_s ~ expr" -> "TX_s ~ expr"
            // This handles sfcr-style named equations like e1 = ..., e2 = ..., etc.
            int tildeIdx = part.indexOf('~');
            if (tildeIdx >= 0) {
                String beforeTilde = part.substring(0, tildeIdx).trim();
                
                // Check if there's an "=" before the "~" (indicating a named equation)
                int eqIdx = beforeTilde.indexOf("=");
                if (eqIdx > 0) {
                    // Strip the "eN = " prefix, keep only the variable name after "="
                    beforeTilde = beforeTilde.substring(eqIdx + 1).trim();
                    part = beforeTilde + " ~ " + part.substring(tildeIdx + 1);
                }
            }
            
            // Parse: var ~ expr
            tildeIdx = part.indexOf('~');
            if (tildeIdx >= 0) {
                String name = SFCRUtil.normalizeVariableName(part.substring(0, tildeIdx).trim());
                String expr = SFCRUtil.normalizeExpression(part.substring(tildeIdx + 1).trim());
                
                // Keep lag notation exactly as imported (e.g. V[-1], V(-1), V [ - 1 ]).
                
                outputNames.add(name);
                equations.add(expr);
                int mode = currentSectionMode;
                String inlineMode = inlineMeta.get("mode");
                if (inlineMode != null && !inlineMode.isEmpty()) {
                    mode = parseModeOrdinal(inlineMode);
                }
                outputModes.add(mode);
                targetNodeNames.add("");

                String sliderVar = inlineMeta.get("slider");
                sliderVarNames.add((sliderVar != null) ? sliderVar : "");

                double sliderValue = 0.0;
                String sliderValueStr = inlineMeta.get("slidervalue");
                if (sliderValueStr != null) {
                    try {
                        sliderValue = Double.parseDouble(sliderValueStr.trim());
                    } catch (Exception e) {
                    }
                }
                sliderValues.add(Double.valueOf(sliderValue));

                String initialEq = inlineMeta.get("initial");
                initialEquations.add((initialEq != null) ? initialEq : "");

                if (inlineComment != null) {
                    inlineComment = inlineComment.trim();
                }
                if (inlineComment != null && !inlineComment.isEmpty() && !hints.containsKey(name)) {
                    hints.put(name, inlineComment);
                }
            }
        }
        
        int savedX = currentX;
        int savedY = currentY;
        if (effectiveMetadata.hasPosition()) {
            currentX = effectiveMetadata.x;
            currentY = effectiveMetadata.y;
        }

        if (!outputNames.isEmpty()) {
            createEquationTable(blockName, outputNames, equations, outputModes,
                targetNodeNames, sliderVarNames, sliderValues, initialEquations);
        }

        if (effectiveMetadata.hasPosition()) {
            currentX = savedX;
            currentY = savedY;
        }
    }
    
    /** Extract a named vector from R code: name = c("a", "b", "c"). */
    private ArrayList<String> extractRVector(String block, String name) {
        ArrayList<String> result = new ArrayList<String>();
        
        String pattern = name + " = c(";
        int idx = block.indexOf(pattern);
        if (idx < 0) {
            // Try without spaces
            pattern = name + "=c(";
            idx = block.indexOf(pattern);
        }
        if (idx < 0) return result;
        
        int start = idx + pattern.length();
        int end = findMatchingParen(block, start - 1);
        if (end < 0) return result;
        
        String content = block.substring(start, end);
        
        // Extract quoted strings
        int pos = 0;
        while (true) {
            int q1 = content.indexOf('"', pos);
            if (q1 < 0) break;
            int q2 = findClosingQuote(content, q1);
            if (q2 < 0) break;
            
            result.add(unescapeRString(content.substring(q1 + 1, q2)));
            pos = q2 + 1;
        }
        
        return result;
    }

    /** Find closing quote in a string, honoring escaped quotes (\"). */
    private int findClosingQuote(String text, int openQuoteIdx) {
        if (text == null || openQuoteIdx < 0 || openQuoteIdx >= text.length() || text.charAt(openQuoteIdx) != '"') {
            return -1;
        }
        boolean escaped = false;
        for (int i = openQuoteIdx + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    /** Unescape R-style string content (\\, \", \n, \r, \t). */
    private String unescapeRString(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!escaped) {
                if (c == '\\') {
                    escaped = true;
                } else {
                    sb.append(c);
                }
                continue;
            }

            switch (c) {
                case '\\': sb.append('\\'); break;
                case '"': sb.append('"'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                default:
                    sb.append('\\').append(c);
                    break;
            }
            escaped = false;
        }
        if (escaped) {
            sb.append('\\');
        }
        return sb.toString();
    }
    
    /** Find matching closing parenthesis. */
    private int findMatchingParen(String text, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
    
    /** Split string by top-level commas (not inside parentheses). */
    private ArrayList<String> splitByTopLevelComma(String text) {
        ArrayList<String> result = new ArrayList<String>();
        int depth = 0;
        int start = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                int segmentEnd = i;
                int nextStart = i + 1;

                // Keep trailing inline comment with the current segment:
                // e1 = x ~ y,  # hint
                int j = skipHorizontalWhitespace(text, i + 1);
                if (j < text.length() && text.charAt(j) == '#') {
                    int k = skipToLineEnd(text, j);
                    segmentEnd = k;
                    nextStart = k;
                    while (nextStart < text.length() && (text.charAt(nextStart) == '\n' || text.charAt(nextStart) == '\r')) {
                        nextStart++;
                    }
                    i = nextStart - 1;
                }

                result.add(text.substring(start, segmentEnd));
                start = nextStart;
            }
        }
        
        if (start < text.length()) {
            result.add(text.substring(start));
        }
        
        return result;
    }

    /** Returns true for section comment headings that imply PARAM rows. */
    private boolean isParametersSectionComment(String sectionText) {
        return sectionText.startsWith("parameters") || sectionText.startsWith("parameter");
    }

    /** Skip spaces/tabs only (not newlines) from the given index. */
    private int skipHorizontalWhitespace(String text, int idx) {
        int out = idx;
        while (out < text.length()) {
            char c = text.charAt(out);
            if (c != ' ' && c != '\t') {
                break;
            }
            out++;
        }
        return out;
    }

    /** Advance to line end (\n or \r) or end of text. */
    private int skipToLineEnd(String text, int idx) {
        int out = idx;
        while (out < text.length()) {
            char c = text.charAt(out);
            if (c == '\n' || c == '\r') {
                break;
            }
            out++;
        }
        return out;
    }

    /**
     * Parse trailing inline metadata in brackets from R-style comments.
     * Example: "Interest rate  [mode=param, sliderValue=0 ]"
     * Returns comment text without metadata and fills outMeta with parsed key/value pairs.
     */
    private String stripTrailingRStyleInlineMetadata(String comment, HashMap<String, String> outMeta) {
        if (comment == null) {
            return "";
        }
        String trimmed = comment.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (outMeta == null) {
            return trimmed;
        }

        String working = trimmed;
        int parsedTotal = 0;

        // Consume one or more trailing bracketed metadata chunks, e.g.
        // "hint [mode=voltage] [sliderValue=0]".
        while (true) {
            int close = working.lastIndexOf(']');
            if (close != working.length() - 1) {
                break;
            }
            int open = working.lastIndexOf('[', close);
            if (open < 0) {
                break;
            }

            String metaChunk = working.substring(open + 1, close).trim();
            int parsedThisChunk = parseRStyleInlineMetadataChunk(metaChunk, outMeta);
            if (parsedThisChunk == 0) {
                break;
            }

            parsedTotal += parsedThisChunk;
            working = working.substring(0, open).trim();
        }

        // Also consume dangling unclosed trailing metadata prefixes such as
        // "hint [mode=voltage" left behind by malformed repeated exports.
        while (true) {
            int open = working.lastIndexOf('[');
            if (open < 0) {
                break;
            }

            String tail = working.substring(open + 1).trim();
            int parsedTail = parseRStyleInlineMetadataChunk(tail, outMeta);
            if (parsedTail == 0) {
                break;
            }

            parsedTotal += parsedTail;
            working = working.substring(0, open).trim();
        }

        if (parsedTotal == 0) {
            return trimmed;
        }
        return working;
    }

    /** Parse comma-separated key=value metadata tokens into outMeta. */
    private int parseRStyleInlineMetadataChunk(String metaChunk, HashMap<String, String> outMeta) {
        if (metaChunk == null || outMeta == null) {
            return 0;
        }

        String chunk = metaChunk.trim();
        if (chunk.isEmpty()) {
            return 0;
        }

        String[] tokens = chunk.split(",");
        int parsed = 0;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = token.substring(0, eq).trim().toLowerCase();
            String value = token.substring(eq + 1).trim();
            if (!key.isEmpty()) {
                outMeta.put(key, value);
                parsed++;
            }
        }
        return parsed;
    }
    
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
    
    /** Parse comma-separated list: "a, b, c" -> ["a", "b", "c"]. */
    private ArrayList<String> parseCommaSeparatedList(String text) {
        ArrayList<String> result = new ArrayList<String>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
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
        // Build the dump string for SFCTableElm (type 265)
        int rows = rowNames.size();

        if (rows <= 0) {
            CirSim.console("SFCRParser: Skipping matrix table '" + name + "' because it has no rows");
            return;
        }
        if (columnNames == null || columnNames.isEmpty()) {
            CirSim.console("SFCRParser: Skipping matrix table '" + name + "' because it has no columns");
            return;
        }
        
        // Check if last column is already a sum column (Σ, Sigma, Total, Sum, etc.)
        boolean hasSumColumn = false;
        if (!columnNames.isEmpty()) {
            String lastCol = columnNames.get(columnNames.size() - 1).trim();
            if (lastCol.equals("Σ") || lastCol.equalsIgnoreCase("Sigma") || 
                lastCol.equalsIgnoreCase("Sum") || lastCol.equalsIgnoreCase("Total") ||
                lastCol.equalsIgnoreCase("Row total") || lastCol.equals("∑")) {
                hasSumColumn = true;
            }
        }
        
        int cols = hasSumColumn ? columnNames.size() : columnNames.size() + 1; // +1 for Σ column if not present
        if (cols <= 1) {
            CirSim.console("SFCRParser: Skipping matrix table '" + name + "' because computed column count is invalid: " + cols);
            return;
        }
        
        StringBuilder dump = new StringBuilder();
        
        // Position: x1 y1 x2 y2 flags
        int x1 = currentX;
        int y1 = currentY;
        int x2 = currentX + 400; // Approximate width
        int y2 = currentY + (rows + 3) * 16; // Approximate height
        
        dump.append("265 ").append(x1).append(" ").append(y1).append(" ");
        dump.append(x2).append(" ").append(y2).append(" 0 ");
        
        // Table data: rows cols cellWidthInGrids cellHeight cellSpacing
        dump.append(rows).append(" ");
        dump.append(cols).append(" ");
        dump.append("6 16 0 ");  // cellWidthInGrids, cellHeight, cellSpacing
        
        // showInitialValues decimalPlaces showCellValues collapsedMode priority initMode showALE
        boolean showInitial = (showInitialValuesOverride != null) ? showInitialValuesOverride.booleanValue() : false;
        dump.append(showInitial ? "true 2 1 false 5 0 false " : "false 2 1 false 5 0 false ");
        
        // Table title (escaped)
        dump.append(escapeToken(name.replace("_", " "))).append(" ");
        
        // Table units
        dump.append("\\0 ");  // Empty units
        
        // Column headers
        if (hasSumColumn) {
            // Use provided column names as-is (last one is already sum column)
            for (int i = 0; i < columnNames.size() - 1; i++) {
                dump.append(escapeToken(columnNames.get(i))).append(" ");
            }
            dump.append("Σ ");  // Normalize the sum column name
        } else {
            // Add Σ column
            for (String col : columnNames) {
                dump.append(escapeToken(col)).append(" ");
            }
            dump.append("Σ ");
        }
        
        // Row descriptions
        for (String row : rowNames) {
            dump.append(escapeToken(row)).append(" ");
        }
        
        // Initial values (zeros)
        for (int i = 0; i < cols; i++) {
            dump.append("0 ");
        }
        
        // Column types (SECTOR for data columns, COMPUTED for Σ)
        for (int i = 0; i < cols - 1; i++) {
            dump.append("SECTOR ");
        }
        dump.append("COMPUTED ");
        
        // Cell equations (row by row)
        // Number of data columns (excluding the Σ column)
        int dataCols = cols - 1;
        for (int r = 0; r < rows; r++) {
            String[] rowData = tableRows.get(r);
            for (int c = 0; c < dataCols; c++) {
                String eq = (c < rowData.length) ? rowData[c] : "";
                // Convert "-" (empty cell marker) to empty string
                if (eq.equals("-") || eq.trim().isEmpty()) {
                    eq = "";
                }
                dump.append(escapeToken(eq)).append(" ");
            }
            dump.append("\\0 ");  // Empty equation for Σ column
        }
        
        // SFC-specific fields.
        // For SFCTableElm: highlightImbalances balanceTolerance
        dump.append("true 0.000001");
        
        // --- Result-mode: collect dump without instantiating elements ---
        if (pendingResult != null) {
            pendingResult.blockDumps.add(new SFCRParseResult.BlockDump("matrix", name, dump.toString()));
            currentY = y2 + elementSpacing;
            return;
        }
        
        // Create element by parsing the dump string
        CirSim.console("Creating SFCTable: " + name);
        
        try {
            StringTokenizer st = new StringTokenizer(dump.toString());
            int type = Integer.parseInt(st.nextToken());
            int xa = Integer.parseInt(st.nextToken());
            int ya = Integer.parseInt(st.nextToken());
            int xb = Integer.parseInt(st.nextToken());
            int yb = Integer.parseInt(st.nextToken());
            int flags = Integer.parseInt(st.nextToken());
            
            CircuitElm ce = CirSim.createCe(type, xa, ya, xb, yb, flags, st);
            if (ce != null) {
                ce.setPoints();  // Initialize geometry (required after construction)
                sim.assignPersistentUid(ce, null);
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

    /** Append a non-simulating comment row to an equation table payload. */
    private void appendCommentRow(String comment,
                                  ArrayList<String> outputNames,
                                  ArrayList<String> equations,
                                  ArrayList<Integer> outputModes,
                                  ArrayList<String> targetNodeNames,
                                  ArrayList<String> sliderVarNames,
                                  ArrayList<Double> sliderValues,
                                  ArrayList<String> initialEquations) {
        if (comment == null) return;
        String text = comment.trim();
        if (text.startsWith("#")) {
            text = text.substring(1).trim();
        }
        if (text.isEmpty()) return;

        outputNames.add("# " + text);
        equations.add("");
        outputModes.add(3);  // PARAM_MODE
        targetNodeNames.add("");
        sliderVarNames.add("");
        sliderValues.add(Double.valueOf(0));
        initialEquations.add("");
    }

    /** Create EquationTableElm from parsed equation data. */
    private void createEquationTable(String name, ArrayList<String> outputNames,
                                     ArrayList<String> equations,
                                     ArrayList<Integer> outputModes,
                                     ArrayList<String> targetNodeNames,
                                     ArrayList<String> sliderVarNames,
                                     ArrayList<Double> sliderValues,
                                     ArrayList<String> initialEquations) {
        int rows = outputNames.size();
        if (rows == 0) return;
        if (rows > 64) rows = 64;  // EquationTableElm.MAX_ROWS
        
        StringBuilder dump = new StringBuilder();
        
        // Position
        int x1 = currentX;
        int y1 = currentY;
        int x2 = currentX + 200;
        int y2 = currentY + (rows + 2) * 16;
        
        // Type 266 = EquationTableElm
        // Flag 2 = FLAG_MNA_MODE (electrical outputs mode)
        dump.append("266 ").append(x1).append(" ").append(y1).append(" ");
        dump.append(x2).append(" ").append(y2).append(" 2 ");
        
        // Table name (escaped)
        dump.append(escapeToken(name.replace("_", " "))).append(" ");
        
        // Row count
        dump.append(rows).append(" ");
        
        // For each row: outputName equation initialEquation sliderVarName sliderValue
        //               outputMode targetNode capacitance shuntResistance useBackwardEuler
        for (int i = 0; i < rows; i++) {
            dump.append(escapeToken(outputNames.get(i))).append(" ");
            dump.append(escapeToken(equations.get(i))).append(" ");
            String initEq = (i < initialEquations.size() && initialEquations.get(i) != null)
                ? initialEquations.get(i) : "";
            dump.append(escapeToken(initEq)).append(" ");

            String sliderVar = (i < sliderVarNames.size() && sliderVarNames.get(i) != null)
                ? sliderVarNames.get(i) : "";
            dump.append(escapeToken(sliderVar)).append(" ");

            double sliderValue = (i < sliderValues.size() && sliderValues.get(i) != null)
                ? sliderValues.get(i).doubleValue() : 0.5;
            dump.append(sliderValue).append(" ");

            int modeOrdinal = (i < outputModes.size() && outputModes.get(i) != null)
                ? outputModes.get(i) : 0;  // default VOLTAGE_MODE
            dump.append(modeOrdinal).append(" ");

            String target = (i < targetNodeNames.size() && targetNodeNames.get(i) != null)
                ? targetNodeNames.get(i) : "";
            dump.append(escapeToken(target)).append(" ");

            dump.append("1.0 ");     // capacitance
            dump.append("1.0 ");     // shuntResistance (DEFAULT_FLOW_SHUNT_RESISTANCE)
            dump.append("0 ");       // useBackwardEuler
        }
        
        // --- Result-mode: collect dump without instantiating elements ---
        if (pendingResult != null) {
            pendingResult.blockDumps.add(new SFCRParseResult.BlockDump("equations", name, dump.toString()));
            currentY = y2 + elementSpacing;
            return;
        }
        
        // CirSim.console("Creating EquationTable: " + name + " with " + rows + " equations");
        
        try {
            StringTokenizer st = new StringTokenizer(dump.toString());
            int type = Integer.parseInt(st.nextToken());
            int xa = Integer.parseInt(st.nextToken());
            int ya = Integer.parseInt(st.nextToken());
            int xb = Integer.parseInt(st.nextToken());
            int yb = Integer.parseInt(st.nextToken());
            int flags = Integer.parseInt(st.nextToken());
            
            CircuitElm ce = CirSim.createCe(type, xa, ya, xb, yb, flags, st);
            if (ce != null) {
                ce.setPoints();  // Initialize geometry (required after construction)
                sim.assignPersistentUid(ce, null);
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
                sim.assignPersistentUid(matchingScopeElm, block.elmUid);
                sim.elmList.addElement(matchingScopeElm);
                createdElements.add(matchingScopeElm);
            }

            if (matchingScopeElm != null && matchingScopeElm.elmScope != null) {
                Scope embedded = matchingScopeElm.elmScope;
                embedded.plots = new Vector<ScopePlot>();

                for (int i = 0; i < block.traces.size(); i++) {
                    ScopeTraceSpec trace = block.traces.get(i);
                    CircuitElm elm = sim.findElmByUid(trace.uid);
                    if (elm == null) {
                        continue;
                    }
                    int units = elm.getScopeUnits(trace.value);
                    double manScale = embedded.getManScaleFromMaxScale(units, false);
                    embedded.plots.add(new ScopePlot(elm, units, trace.value, manScale));
                }

                if (embedded.plots.size() > 0) {
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
                    matchingScopeElm.setPoints();
                    addedAny = true;
                    continue;
                }
            }

            // No geometry and no matching ScopeElm: create as docked scope
            if (sim.scopeCount >= sim.scopes.length) {
                break;
            }

            Scope scope = new Scope(sim);
            scope.plots = new Vector<ScopePlot>();

            for (int i = 0; i < block.traces.size(); i++) {
                ScopeTraceSpec trace = block.traces.get(i);
                CircuitElm elm = sim.findElmByUid(trace.uid);
                if (elm == null) {
                    continue;
                }
                int units = elm.getScopeUnits(trace.value);
                double manScale = scope.getManScaleFromMaxScale(units, false);
                scope.plots.add(new ScopePlot(elm, units, trace.value, manScale));
            }

            if (scope.plots.isEmpty()) {
                continue;
            }

            scope.position = block.position;
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
            sim.setupScopes();
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
            if (se.elmScope == null || se.elmScope.plots == null || se.elmScope.plots.size() == 0) {
                continue;
            }
            ScopePlot firstPlot = se.elmScope.plots.get(0);
            if (firstPlot == null || firstPlot.elm == null) {
                continue;
            }
            String firstUid = firstPlot.elm.getPersistentUid();
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
