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
    
    // =========================================================================
    // Constructor & Public API
    // =========================================================================
    
    /** Create a new SFCR parser. */
    public SFCRParser(CirSim sim) {
        this.sim = sim;
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
            trimmed.contains("@init") || trimmed.contains("@hints") ||
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
        currentY = 24;
        
        try {
            String[] lines = text.split("\n");
            int i = 0;
            
            while (i < lines.length) {
                String line = lines[i].trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
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
                    i = parseInitBlock(lines, i);
                } else if (line.startsWith("@matrix")) {
                    i = parseMatrixBlock(lines, i);
                } else if (line.startsWith("@equations")) {
                    i = parseEquationsBlock(lines, i);
                } else if (line.startsWith("@parameters")) {
                    i = parseParametersBlock(lines, i);
                } else if (line.startsWith("@hints")) {
                    i = parseHintsBlock(lines, i);
                } else if (line.startsWith("@scope")) {
                    if (looksLikeScopeBlock(lines, i)) {
                        i = parseScopeBlock(lines, i);
                    } else {
                        parseScopeLine(line);
                        i++;
                    }
                } else if (line.startsWith("@circuit")) {
                    i = parseCircuitBlock(lines, i);
                } else if (line.startsWith("@sankey")) {
                    i = parseSankeyBlock(lines, i);
                } else if (line.startsWith("@info")) {
                    i = parseInfoBlock(lines, i);
                } else if (line.contains("sfcr_matrix") || line.contains("<-")) {
                    // Try to parse R-style sfcr syntax
                    i = parseRStyleBlock(lines, i);
                } else {
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
    * showDots, showVolts, showValues, showPower, infoViewerUpdateIntervalMs.
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
        for (String key : initSettings.keySet()) {
            String value = initSettings.get(key);
            try {
                switch (key) {
                    case "timestep":
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
                            sim.toolbarCheckItem.setState(value.equals("true"));
                            sim.setToolbar();
                        }
                        break;
                    case "showDots":
                        sim.dotsCheckItem.setState(value.equals("true"));
                        break;
                    case "showVolts":
                        sim.voltsCheckItem.setState(value.equals("true"));
                        break;
                    case "showValues":
                        sim.showValuesCheckItem.setState(value.equals("true"));
                        break;
                    case "showPower":
                        sim.powerCheckItem.setState(value.equals("true"));
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
        ArrayList<EquationTableElm.RowOutputMode> outputModes = new ArrayList<EquationTableElm.RowOutputMode>();
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

                String normalizedLeft = normalizeVariableName(leftPart);
                String[] nameParts = EquationTableElm.parseCombinedName(normalizedLeft);
                String name = normalizeVariableName(nameParts[0]);

                String targetName = "";
                if (nameParts[1] != null && !nameParts[1].trim().isEmpty()) {
                    targetName = normalizeVariableName(nameParts[1].trim());
                }

                String expr = normalizeExpression(exprText);
                
                // Convert lag notation: V[-1] → last(V)
                expr = convertLagNotation(name, expr);
                
                outputNames.add(name);
                equations.add(expr);

                EquationTableElm.RowOutputMode mode = parseEquationRowMode(rowMeta.get("mode"));
                if (mode == EquationTableElm.RowOutputMode.VOLTAGE_MODE && !targetName.isEmpty()) {
                    mode = EquationTableElm.RowOutputMode.FLOW_MODE;
                }
                outputModes.add(mode);

                if (targetName.isEmpty()) {
                    String metaTarget = rowMeta.get("target");
                    if (metaTarget != null && !metaTarget.trim().isEmpty()) {
                        targetName = normalizeVariableName(metaTarget.trim());
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
                String varName = normalizeVariableName(line.substring(0, colonIdx).trim());
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
        varName = normalizeVariableName(varName);
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
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }
            
            // Store raw circuit line for later processing
            rawCircuitLines.add(line);
            i++;
        }
        
        return i;
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
        
        SFCSankeyElm sankeyElm = new SFCSankeyElm(posX, posY);
        
        // Set properties using reflection-like approach via setEditValue
        // We need to set properties directly since the element is already created
        // Use the dump/load mechanism by building a tokenizer
        String dumpStr = "466 " + posX + " " + posY + " " + (posX + 16) + " " + (posY + 16) + " 0 " +
                         CustomLogicModel.escape(sourceName) + " " + layout + " " + width + " " + height + " " +
                         (showScaleBar ? "1" : "0") + " " + fixedMaxScale + " " +
                         (useHighWaterMark ? "1" : "0") + " " + (showFlowValues ? "1" : "0");
        
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
    private int parseRStyleBlock(String[] lines, int startIndex) {
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
        
        // Determine block type and parse
        if (block.contains("sfcr_matrix")) {
            parseRStyleMatrix(block);
        } else if (block.contains("sfcr_set")) {
            parseRStyleEquations(block);
        }
        
        return i;
    }
    
    /** Parse R-style sfcr_matrix definition. */
    private void parseRStyleMatrix(String block) {
        // Extract matrix name from assignment
        String matrixName = "Matrix";
        int assignIdx = block.indexOf("<-");
        if (assignIdx > 0) {
            matrixName = block.substring(0, assignIdx).trim();
        }
        
        // Extract columns
        ArrayList<String> columnNames = extractRVector(block, "columns");
        ArrayList<String> columnCodes = extractRVector(block, "codes");
        
        // Extract rows: c("RowName", code = "expr", ...)
        ArrayList<String> rowNames = new ArrayList<String>();
        ArrayList<String[]> tableRows = new ArrayList<String[]>();
        
        // Find row definitions: c("name", ...)
        int searchStart = block.indexOf("sfcr_matrix");
        while (true) {
            int cStart = block.indexOf("c(\"", searchStart);
            if (cStart < 0) break;
            
            int cEnd = findMatchingParen(block, cStart + 1);
            if (cEnd < 0) break;
            
            String rowDef = block.substring(cStart + 2, cEnd);
            parseRStyleRow(rowDef, rowNames, tableRows, columnCodes);
            
            searchStart = cEnd + 1;
        }
        
        // Create table if we have data
        if (!columnNames.isEmpty() && !tableRows.isEmpty()) {
            createMatrixTable(matrixName, columnNames, rowNames, tableRows, "transaction_flow",
                              null, null, null);
        }
    }
    
    /** Parse a single R-style row: "RowName", code = "expr", ... */
    private void parseRStyleRow(String rowDef, ArrayList<String> rowNames, 
                                 ArrayList<String[]> tableRows, ArrayList<String> codes) {
        int firstQuote = rowDef.indexOf('"');
        int secondQuote = rowDef.indexOf('"', firstQuote + 1);
        if (firstQuote < 0 || secondQuote < 0) return;
        
        String rowName = rowDef.substring(firstQuote + 1, secondQuote);
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
            // Look for "code = "expr"" or "code = +expr" patterns
            String pattern1 = code + " = \"";
            String pattern2 = code + "=\"";
            String pattern3 = code + " = ";
            
            int idx = rest.indexOf(pattern1);
            if (idx >= 0) {
                int exprStart = idx + pattern1.length();
                int exprEnd = rest.indexOf('"', exprStart);
                if (exprEnd > exprStart) {
                    rowData[i] = normalizeExpression(rest.substring(exprStart, exprEnd));
                }
            } else {
                idx = rest.indexOf(pattern2);
                if (idx >= 0) {
                    int exprStart = idx + pattern2.length();
                    int exprEnd = rest.indexOf('"', exprStart);
                    if (exprEnd > exprStart) {
                        rowData[i] = normalizeExpression(rest.substring(exprStart, exprEnd));
                    }
                }
            }
        }
        
        tableRows.add(rowData);
    }
    
    /** Parse R-style sfcr_set for equations. */
    private void parseRStyleEquations(String block) {
        // Extract name from assignment
        String blockName = "Equations";
        int assignIdx = block.indexOf("<-");
        if (assignIdx > 0) {
            blockName = block.substring(0, assignIdx).trim();
        }
        
        ArrayList<String> outputNames = new ArrayList<String>();
        ArrayList<String> equations = new ArrayList<String>();
        ArrayList<EquationTableElm.RowOutputMode> outputModes = new ArrayList<EquationTableElm.RowOutputMode>();
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
        
        // Split by commas (but not commas inside parentheses)
        ArrayList<String> parts = splitByTopLevelComma(content);
        
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            String[] partLines = part.split("\\r?\\n");
            StringBuilder cleanedPart = new StringBuilder();
            for (int li = 0; li < partLines.length; li++) {
                String line = partLines[li].trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#")) {
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
            if (inlineCommentIdx >= 0) {
                part = part.substring(0, inlineCommentIdx).trim();
                if (part.isEmpty()) {
                    continue;
                }
            }
            
            // Strip named equation prefix: "e1 = TX_s ~ expr" -> "TX_s ~ expr"
            // This handles sfcr-style named equations like e1 = ..., e2 = ..., etc.
            if (part.contains("~")) {
                int tildeIdx = part.indexOf("~");
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
            String[] eqParts = part.split("~", 2);
            if (eqParts.length == 2) {
                String name = normalizeVariableName(eqParts[0].trim());
                String expr = normalizeExpression(eqParts[1].trim());
                
                // Convert lag notation: V[-1] → last(V)
                expr = convertLagNotation(name, expr);
                
                outputNames.add(name);
                equations.add(expr);
                outputModes.add(EquationTableElm.RowOutputMode.VOLTAGE_MODE);
                targetNodeNames.add("");
                sliderVarNames.add("");
                sliderValues.add(Double.valueOf(0.0));
                initialEquations.add("");
            }
        }
        
        if (!outputNames.isEmpty()) {
            createEquationTable(blockName, outputNames, equations, outputModes,
                targetNodeNames, sliderVarNames, sliderValues, initialEquations);
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
            int q2 = content.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            
            result.add(content.substring(q1 + 1, q2));
            pos = q2 + 1;
        }
        
        return result;
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
                result.add(text.substring(start, i));
                start = i + 1;
            }
        }
        
        if (start < text.length()) {
            result.add(text.substring(start));
        }
        
        return result;
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
    
    // =========================================================================
    // Expression Normalization
    // =========================================================================
    
    /** Normalize variable name: convert Greek symbol representations. */
    private String normalizeVariableName(String name) {
        if (name == null) return "";
        
        // Map common representations to CircuitJS1 format
        // Only replace if NOT already preceded by backslash
        // Use temporary placeholder to avoid double-replacement
        
        // First, protect existing \alpha, \theta, \Delta patterns by marking them
        name = name.replace("\\alpha", "\u0001ALPHA\u0001");
        name = name.replace("\\theta", "\u0001THETA\u0001");
        name = name.replace("\\Delta", "\u0001DELTA\u0001");
        
        // Now safely replace unescaped variants
        name = name.replace("alpha1", "\u0001ALPHA\u0001_1");
        name = name.replace("alpha2", "\u0001ALPHA\u0001_2");
        name = name.replace("alpha_1", "\u0001ALPHA\u0001_1");
        name = name.replace("alpha_2", "\u0001ALPHA\u0001_2");
        name = name.replace("α_1", "\u0001ALPHA\u0001_1");
        name = name.replace("α_2", "\u0001ALPHA\u0001_2");
        name = name.replace("α1", "\u0001ALPHA\u0001_1");
        name = name.replace("α2", "\u0001ALPHA\u0001_2");
        name = name.replace("theta", "\u0001THETA\u0001");
        name = name.replace("θ", "\u0001THETA\u0001");
        name = name.replace("Delta", "\u0001DELTA\u0001");
        name = name.replace("∆", "\u0001DELTA\u0001");
        
        // Restore the backslash-prefixed versions
        name = name.replace("\u0001ALPHA\u0001", "\\alpha");
        name = name.replace("\u0001THETA\u0001", "\\theta");
        name = name.replace("\u0001DELTA\u0001", "\\Delta");
        
        return name.trim();
    }
    
    /** Normalize expression: convert symbols, d() -> diff(), ∫() -> integrate(). */
    private String normalizeExpression(String expr) {
        if (expr == null) return "";
        
        // Protect existing backslash-prefixed symbols first
        expr = expr.replace("\\alpha", "\u0001ALPHA\u0001");
        expr = expr.replace("\\theta", "\u0001THETA\u0001");
        
        // Normalize variable names in expression (unescaped variants only)
        expr = expr.replace("alpha1", "\u0001ALPHA\u0001_1");
        expr = expr.replace("alpha2", "\u0001ALPHA\u0001_2");
        expr = expr.replace("alpha_1", "\u0001ALPHA\u0001_1");
        expr = expr.replace("alpha_2", "\u0001ALPHA\u0001_2");
        expr = expr.replace("α_1", "\u0001ALPHA\u0001_1");
        expr = expr.replace("α_2", "\u0001ALPHA\u0001_2");
        expr = expr.replace("α1", "\u0001ALPHA\u0001_1");
        expr = expr.replace("α2", "\u0001ALPHA\u0001_2");
        expr = expr.replace("theta", "\u0001THETA\u0001");
        expr = expr.replace("θ", "\u0001THETA\u0001");
        
        // Restore backslash-prefixed versions
        expr = expr.replace("\u0001ALPHA\u0001", "\\alpha");
        expr = expr.replace("\u0001THETA\u0001", "\\theta");
        
        // Convert d(x) or ∆(x) to diff(x) for differentiation
        expr = expr.replace("∆(", "diff(");
        expr = expr.replace("d(", "diff(");
        
        // Convert ∫(x) to integrate(x)
        expr = expr.replace("∫(", "integrate(");
        
        // Don't convert lagged values here - that's handled by convertLagNotation
        // after we know the output variable name
        
        return expr.trim();
    }
    
    /**
     * Convert lagged values to last():
     *   V[-1] -> last(V)
     *
     * Self-references are intentionally kept as last(V) by default so expressions like
     *   Ls ~ Ls[-1] + Ld - Ld[-1]
     * become
     *   Ls ~ last(Ls) + Ld - last(Ld)
     *
     * Use integrate(...) explicitly in source text when integrator semantics are desired.
     */
    private String convertLagNotation(String varName, String expr) {
        if (expr == null || !expr.contains("[-1]")) {
            return expr;
        }
        // Convert all lagged references, including self-lag terms.
        return convertLagToFunction(expr);
    }
    
    /** Convert [-1] lag notation to last() function: V[-1] -> last(V). */
    private String convertLagToFunction(String expr) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        
        while (i < expr.length()) {
            // Look for [-1] pattern
            int lagIdx = expr.indexOf("[-1]", i);
            if (lagIdx < 0) {
                // No more lag patterns, append rest
                result.append(expr.substring(i));
                break;
            }
            
            // Find the variable name before [-1]
            int varEnd = lagIdx;
            int varStart = varEnd;
            
            // Skip whitespace before [-1]
            while (varStart > i && Character.isWhitespace(expr.charAt(varStart - 1))) {
                varStart--;
            }
            varEnd = varStart;
            
            // Find start of identifier (letters, digits, underscores)
            while (varStart > i) {
                char c = expr.charAt(varStart - 1);
                if (Character.isLetterOrDigit(c) || c == '_') {
                    varStart--;
                } else {
                    break;
                }
            }
            
            if (varStart < varEnd) {
                // Found a variable name - convert to last(varName)
                String varName = expr.substring(varStart, varEnd);
                
                // Append everything before the variable
                result.append(expr.substring(i, varStart));
                
                // Append last(varName)
                result.append("last(").append(varName).append(")");
                
                // Move past [-1]
                i = lagIdx + 4;
            } else {
                // No variable found, just append up to and including [-1]
                result.append(expr.substring(i, lagIdx + 4));
                i = lagIdx + 4;
            }
        }
        
        return result.toString();
    }
    
    /** @deprecated Use convertLagToFunction instead. */
    @SuppressWarnings("unused")
    private String stripLagNotation(String expr) {
        return expr.replace("[-1]", "");
    }
    
    /** @deprecated Use convertLagNotation instead. */
    @SuppressWarnings("unused")
    private String convertLaggedValues(String expr) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        
        while (i < expr.length()) {
            // Look for [-1] pattern
            int lagIdx = expr.indexOf("[-1]", i);
            if (lagIdx < 0) {
                // No more lag patterns, append rest
                result.append(expr.substring(i));
                break;
            }
            
            // Find the variable name before [-1]
            // Walk backwards to find start of identifier
            int varEnd = lagIdx;
            int varStart = varEnd;
            
            // Skip whitespace before [-1]
            while (varStart > i && Character.isWhitespace(expr.charAt(varStart - 1))) {
                varStart--;
            }
            varEnd = varStart;
            
            // Find start of identifier (letters, digits, underscores)
            while (varStart > i) {
                char c = expr.charAt(varStart - 1);
                if (Character.isLetterOrDigit(c) || c == '_') {
                    varStart--;
                } else {
                    break;
                }
            }
            
            if (varStart < varEnd) {
                // Found a variable name
                String varName = expr.substring(varStart, varEnd);
                
                // Append everything before the variable
                result.append(expr.substring(i, varStart));
                
                // Append the converted expression: (var - diff(var))
                result.append("(").append(varName).append(" - diff(").append(varName).append("))");
                
                // Move past [-1]
                i = lagIdx + 4;
            } else {
                // No variable found, just append up to and including [-1]
                result.append(expr.substring(i, lagIdx + 4));
                i = lagIdx + 4;
            }
        }
        
        return result.toString();
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
        dump.append(CustomLogicModel.escape(name.replace("_", " "))).append(" ");
        
        // Table units
        dump.append("\\0 ");  // Empty units
        
        // Column headers
        if (hasSumColumn) {
            // Use provided column names as-is (last one is already sum column)
            for (int i = 0; i < columnNames.size() - 1; i++) {
                dump.append(CustomLogicModel.escape(columnNames.get(i))).append(" ");
            }
            dump.append("Σ ");  // Normalize the sum column name
        } else {
            // Add Σ column
            for (String col : columnNames) {
                dump.append(CustomLogicModel.escape(col)).append(" ");
            }
            dump.append("Σ ");
        }
        
        // Row descriptions
        for (String row : rowNames) {
            dump.append(CustomLogicModel.escape(row)).append(" ");
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
                dump.append(CustomLogicModel.escape(eq)).append(" ");
            }
            dump.append("\\0 ");  // Empty equation for Σ column
        }
        
        // SFC-specific fields.
        // For SFCTableElm: highlightImbalances balanceTolerance
        dump.append("true 0.000001");
        
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
            }
        } catch (Exception e) {
            CirSim.console("Error creating matrix table: " + e.getMessage());
        }
    }

    private EquationTableElm.RowOutputMode parseEquationRowMode(String mode) {
        if (mode == null) return EquationTableElm.RowOutputMode.VOLTAGE_MODE;
        String m = mode.trim().toLowerCase();
        if (m.equals("flow") || m.equals("flow_mode")) return EquationTableElm.RowOutputMode.FLOW_MODE;
        if (m.equals("stock") || m.equals("stock_mode")) return EquationTableElm.RowOutputMode.STOCK_MODE;
        if (m.equals("param") || m.equals("parameter") || m.equals("param_mode")) return EquationTableElm.RowOutputMode.PARAM_MODE;
        return EquationTableElm.RowOutputMode.VOLTAGE_MODE;
    }

    /** Append a non-simulating comment row to an equation table payload. */
    private void appendCommentRow(String comment,
                                  ArrayList<String> outputNames,
                                  ArrayList<String> equations,
                                  ArrayList<EquationTableElm.RowOutputMode> outputModes,
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
        outputModes.add(EquationTableElm.RowOutputMode.PARAM_MODE);
        targetNodeNames.add("");
        sliderVarNames.add("");
        sliderValues.add(Double.valueOf(0));
        initialEquations.add("");
    }

    /** Create EquationTableElm from parsed equation data. */
    private void createEquationTable(String name, ArrayList<String> outputNames,
                                     ArrayList<String> equations,
                                     ArrayList<EquationTableElm.RowOutputMode> outputModes,
                                     ArrayList<String> targetNodeNames,
                                     ArrayList<String> sliderVarNames,
                                     ArrayList<Double> sliderValues,
                                     ArrayList<String> initialEquations) {
        int rows = outputNames.size();
        if (rows == 0) return;
        if (rows > 32) rows = 32; // Max rows for EquationTableElm
        
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
        dump.append(CustomLogicModel.escape(name.replace("_", " "))).append(" ");
        
        // Row count
        dump.append(rows).append(" ");
        
        // For each row: outputName equation initialEquation sliderVarName sliderValue
        //               outputMode targetNode capacitance shuntResistance useBackwardEuler
        for (int i = 0; i < rows; i++) {
            dump.append(CustomLogicModel.escape(outputNames.get(i))).append(" ");
            dump.append(CustomLogicModel.escape(equations.get(i))).append(" ");
            String initEq = (i < initialEquations.size() && initialEquations.get(i) != null)
                ? initialEquations.get(i) : "";
            dump.append(CustomLogicModel.escape(initEq)).append(" ");

            String sliderVar = (i < sliderVarNames.size() && sliderVarNames.get(i) != null)
                ? sliderVarNames.get(i) : "";
            dump.append(CustomLogicModel.escape(sliderVar)).append(" ");

            double sliderValue = (i < sliderValues.size() && sliderValues.get(i) != null)
                ? sliderValues.get(i).doubleValue() : 0.5;
            dump.append(sliderValue).append(" ");

            EquationTableElm.RowOutputMode mode = (i < outputModes.size() && outputModes.get(i) != null)
                ? outputModes.get(i) : EquationTableElm.RowOutputMode.VOLTAGE_MODE;
            dump.append(mode.ordinal()).append(" ");

            String target = (i < targetNodeNames.size() && targetNodeNames.get(i) != null)
                ? targetNodeNames.get(i) : "";
            dump.append(CustomLogicModel.escape(target)).append(" ");

            dump.append("1.0 ");     // capacitance
            dump.append(EquationTableElm.getDefaultFlowShuntResistance()).append(" ");   // shuntResistance
            dump.append("0 ");       // useBackwardEuler
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
