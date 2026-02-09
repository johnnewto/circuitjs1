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
                    parseScopeLine(line);
                    i++;
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
            
            // Add scopes for requested variables
            addScopes();
            
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
     * showDots, showVolts, showValues, showPower.
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
            
            // Parse column definitions
            if (line.startsWith("columns:")) {
                columnNames = parseCommaSeparatedList(line.substring(8));
                i++;
                continue;
            }
            
            if (line.startsWith("codes:")) {
                columnCodes = parseCommaSeparatedList(line.substring(6));
                i++;
                continue;
            }
            
            if (line.startsWith("type:")) {
                matrixType = line.substring(5).trim();
                i++;
                continue;
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
        
        // Create SFCTableElm from parsed data
        if (!columnNames.isEmpty() && !tableRows.isEmpty()) {
            createSFCTable(matrixName, columnNames, rowNames, tableRows, matrixType);
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
                String name = normalizeVariableName(parts[0].trim());
                String expr = normalizeExpression(parts[1].trim());
                
                // Convert self-accumulation pattern: X ~ expr + X[-1] → X ~ integrate(expr)
                expr = convertSelfAccumulation(name, expr);
                
                outputNames.add(name);
                equations.add(expr);
                
                // Store inline comment as auto-hint (only if not already set by @hints)
                if (inlineComment != null && !inlineComment.isEmpty() && !hints.containsKey(name)) {
                    hints.put(name, inlineComment);
                }
            }
            
            i++;
        }
        
        // Create EquationTableElm from parsed data
        if (!outputNames.isEmpty()) {
            createEquationTable(blockName, outputNames, equations);
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
                         CustomLogicModel.escape(sourceName) + " " + layout + " " + width + " " + height;
        
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
            createSFCTable(matrixName, columnNames, rowNames, tableRows, "transaction_flow");
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
                
                // Convert self-accumulation pattern: X ~ expr + X[-1] → X ~ integrate(expr)
                expr = convertSelfAccumulation(name, expr);
                
                outputNames.add(name);
                equations.add(expr);
            }
        }
        
        if (!outputNames.isEmpty()) {
            createEquationTable(blockName, outputNames, equations);
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
        
        // Don't convert lagged values here - that's handled by convertSelfAccumulation
        // after we know the output variable name
        
        return expr.trim();
    }
    
    /**
     * Convert self-accumulation to integrate():
     *   X ~ expr + X[-1] -> X ~ integrate(expr)
     * For other lagged values, converts V[-1] -> last(V).
     */
    private String convertSelfAccumulation(String varName, String expr) {
        if (expr == null || !expr.contains("[-1]")) {
            return expr;
        }
        
        // Check for self-accumulation pattern: expr contains varName[-1]
        String selfLag = varName + "[-1]";
        
        if (expr.contains(selfLag)) {
            // This is a self-accumulation pattern
            // Remove the varName[-1] term and wrap remaining in integrate()
            
            // Handle: expr + varName[-1]
            String remaining = expr.replace("+ " + selfLag, "")
                                   .replace("+" + selfLag, "")
                                   .replace(selfLag + " +", "")
                                   .replace(selfLag + "+", "")
                                   .replace(selfLag, "")  // In case it's the only term
                                   .trim();
            
            // Clean up any trailing/leading operators
            if (remaining.startsWith("+")) {
                remaining = remaining.substring(1).trim();
            }
            if (remaining.endsWith("+")) {
                remaining = remaining.substring(0, remaining.length() - 1).trim();
            }
            
            // If there's remaining expression, wrap in integrate()
            if (!remaining.isEmpty()) {
                return "integrate(" + remaining + ")";
            } else {
                // Just varName[-1] by itself means constant (no change)
                return varName;
            }
        }
        
        // Not self-accumulation - convert V[-1] to last(V) for previous timestep value
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
    
    /** @deprecated Use convertSelfAccumulation instead. */
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
    
    /** Create SFCTableElm from parsed matrix data. */
    private void createSFCTable(String name, ArrayList<String> columnNames,
                                 ArrayList<String> rowNames, ArrayList<String[]> tableRows,
                                 String matrixType) {
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
        dump.append("false 2 1 false 5 0 false ");
        
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
        
        // SFC-specific: highlightImbalances balanceTolerance
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
                sim.elmList.addElement(ce);
                createdElements.add(ce);
                currentY = yb + elementSpacing;
            }
        } catch (Exception e) {
            CirSim.console("Error creating SFCTable: " + e.getMessage());
        }
    }
    
    /** Create EquationTableElm from parsed equation data. */
    private void createEquationTable(String name, ArrayList<String> outputNames,
                                      ArrayList<String> equations) {
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
        for (int i = 0; i < rows; i++) {
            dump.append(CustomLogicModel.escape(outputNames.get(i))).append(" ");
            dump.append(CustomLogicModel.escape(equations.get(i))).append(" ");
            dump.append("\\0 ");  // Initial equation (empty)
            dump.append("\\0 ");  // Slider var name (empty)
            dump.append("0.5 ");  // Slider value (default)
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
                sim.elmList.addElement(ce);
                createdElements.add(ce);
                currentY = yb + elementSpacing;
            }
        } catch (Exception e) {
            CirSim.console("Error creating EquationTable: " + e.getMessage());
        }
    }
    
    /** Add scopes for requested variables (TODO: not yet implemented). */
    private void addScopes() {
        // Scope/probe creation is disabled for now - the probe element
        // requires specific geometry setup that needs more investigation
        if (scopeVariables.isEmpty()) {
            return;
        }
        
        CirSim.console("SFCR: Skipping scope creation for " + scopeVariables.size() + 
                       " variables (not yet implemented)");
        
        // TODO: Implement proper probe creation
        // ProbeElm needs to connect to a labeled node, not just reference a variable name
    }
    
    /** Get list of created elements (for testing/debugging). */
    public ArrayList<CircuitElm> getCreatedElements() {
        return createdElements;
    }
}
