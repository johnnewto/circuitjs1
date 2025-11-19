/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.TableEditDialog.ColumnType;

/**
 * TableDataManager - Handles table data initialization and serialization
 * Separates data management from circuit simulation logic
 */
public class TableDataManager {
    private final TableElm table;
    
    public TableDataManager(TableElm table) {
        this.table = table;
    }
    
    /**
     * Check if a column is the A_L_E computed column
     * The last column is A_L_E when there are 4 or more columns
     */
    private boolean isALEColumn(int col) {
        return col == table.cols - 1 && table.cols >= 4;
    }
    
    /**
     * Initialize column types with default values
     */
    private void initializeColumnTypes() {
        table.columnTypes = new ColumnType[table.cols];
        for (int col = 0; col < table.cols; col++) {
            table.columnTypes[col] = getDefaultColumnType(col);
        }
    }
    
    /**
     * Get default column type based on column index
     * Note: The last column (when cols >= 4) is implicitly A_L_E (computed), but stored as ASSET type
     */
    private ColumnType getDefaultColumnType(int col) {
        // Note: We don't return A_L_E type anymore - last column detection is positional
        // Last column for tables with 4+ cols will have blank name and be computed at runtime
        
        switch (col) {
            case 0: return ColumnType.ASSET;
            case 1: return ColumnType.LIABILITY;
            case 2: return ColumnType.EQUITY;
            default: return ColumnType.ASSET;  // All additional columns are ASSET by default
        }
    }
    
    /**
     * Initialize all table data structures
     */
    public void initTable() {
        // Initialize equation arrays
        if (table.cellEquations == null) {
            table.cellEquations = new String[table.rows][table.cols];
            for (int row = 0; row < table.rows; row++) {
                for (int col = 0; col < table.cols; col++) {
                    table.cellEquations[row][col] = "";
                }
            }
        }
        
        if (table.compiledExpressions == null) {
            table.compiledExpressions = new Expr[table.rows][table.cols];
        }
        
        if (table.expressionStates == null) {
            table.expressionStates = new ExprState[table.rows][table.cols];
            for (int row = 0; row < table.rows; row++) {
                for (int col = 0; col < table.cols; col++) {
                    table.expressionStates[row][col] = new ExprState(1); // Only need time variable
                }
            }
        }
        
        // Initialize column types FIRST (before output names need them)
        if (table.columnTypes == null) {
            initializeColumnTypes();
        }
        
        // Initialize output names if not set
        if (table.outputNames == null) {
            table.outputNames = new String[table.cols];
            for (int i = 0; i < table.cols; i++) {
                // A_L_E columns (last column when cols >= 4) should be labeled "A-L-E"
                // Note: They still won't be registered as stocks (handled in registerAllStocks)
                if (isALEColumn(i)) {
                    table.outputNames[i] = "A-L-E";
                } else {
                    table.outputNames[i] = "Stock_" + (i + 1);
                }
            }
        }
        
        // Initialize row descriptions if not set
        if (table.rowDescriptions == null) {
            table.rowDescriptions = new String[table.rows];
            for (int i = 0; i < table.rows; i++) {
                table.rowDescriptions[i] = "Flow_" + (i + 1);
            }
        }
        
        // Initialize initial conditions values if not set
        if (table.initialValues == null) {
            table.initialValues = new double[table.cols];
            for (int i = 0; i < table.cols; i++) {
                table.initialValues[i] = 0.0;
            }
        }
        
        // Ensure no null or empty values exist in cell equations
        for (int row = 0; row < table.rows; row++) {
            for (int col = 0; col < table.cols; col++) {
                if (table.cellEquations[row][col] == null || table.cellEquations[row][col].trim().isEmpty()) {
                    table.cellEquations[row][col] = "";
                }
            }
        }
        
        // Ensure no null or empty values exist in output names
        for (int col = 0; col < table.cols; col++) {
            // A_L_E columns (last column when cols >= 4) should be labeled "A-L-E"
            if (isALEColumn(col)) {
                if (table.outputNames[col] == null || table.outputNames[col].trim().isEmpty()) {
                    table.outputNames[col] = "A-L-E";
                }
                continue;
            }
            // Other columns get default Stock names if empty
            if (table.outputNames[col] == null || table.outputNames[col].trim().isEmpty()) {
                table.outputNames[col] = "Stock" + (col + 1);
            }
        }
    }
    
    /**
     * Initialize with default values (error fallback)
     */
    public void initializeDefaults() {
        initializeOutputNames();
        initializeRowDescriptions();
        initializeColumnTypes();
        initializeCellEquations();
        initializeInitialValues();
        
        table.showInitialValues = false;
        table.tableUnits = "";
        table.decimalPlaces = 2;
        table.tableTitle = "Table";
    }
    
    private void initializeOutputNames() {
        if (table.outputNames == null) {
            table.outputNames = new String[table.cols];
        }
        for (int col = 0; col < table.cols; col++) {
            table.outputNames[col] = "Stock" + (col + 1);
        }
    }
    
    private void initializeRowDescriptions() {
        if (table.rowDescriptions == null) {
            table.rowDescriptions = new String[table.rows];
        }
        for (int row = 0; row < table.rows; row++) {
            table.rowDescriptions[row] = "Row" + (row + 1);
        }
    }
    
    private void initializeCellEquations() {
        if (table.cellEquations == null) {
            table.cellEquations = new String[table.rows][table.cols];
        }
        for (int row = 0; row < table.rows; row++) {
            for (int col = 0; col < table.cols; col++) {
                table.cellEquations[row][col] = "";
            }
        }
    }
    
    private void initializeInitialValues() {
        if (table.initialValues == null) {
            table.initialValues = new double[table.cols];
        }
        for (int col = 0; col < table.cols; col++) {
            table.initialValues[col] = 0.0;
        }
    }
    
    /**
     * Serialize table data to string for saving
     */
    /**
     * Simplified dump format (not backward compatible)
     * Format: rows cols cellWidth cellHeight cellSpacing showInitialValues decimalPlaces showCellValues collapsedMode priority
     *         tableTitle tableUnits
     *         [col headers] [row descriptions] [initial values] [column types] [equations]
     */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        
        // Basic dimensions and display settings (all integers/booleans)
        sb.append(" ").append(table.rows);
        sb.append(" ").append(table.cols);
        sb.append(" ").append(table.cellWidthInGrids);
        sb.append(" ").append(table.cellHeight);
        sb.append(" ").append(table.cellSpacing);
        sb.append(" ").append(table.showInitialValues);
        sb.append(" ").append(table.decimalPlaces);
        sb.append(" ").append(table.showCellValues);
        sb.append(" ").append(table.collapsedMode);
        sb.append(" ").append(table.priority);
        
        // Text fields (escaped)
        sb.append(" ").append(CustomLogicModel.escape(table.tableTitle));
        sb.append(" ").append(CustomLogicModel.escape(table.tableUnits));
        
        // Column headers (cols count)
        for (int col = 0; col < table.cols; col++) {
            sb.append(" ").append(CustomLogicModel.escape(table.outputNames[col]));
        }
        
        // Row descriptions (rows count)
        for (int row = 0; row < table.rows; row++) {
            sb.append(" ").append(CustomLogicModel.escape(table.rowDescriptions[row]));
        }
        
        // Initial values (cols count)
        for (int col = 0; col < table.cols; col++) {
            sb.append(" ").append(table.initialValues[col]);
        }
        
        // Column types (cols count)
        for (int col = 0; col < table.cols; col++) {
            sb.append(" ").append(table.columnTypes[col].name());
        }
        
        // Cell equations (rows * cols count)
        for (int row = 0; row < table.rows; row++) {
            for (int col = 0; col < table.cols; col++) {
                String equation = (table.cellEquations[row][col] != null) ? table.cellEquations[row][col] : "";
                sb.append(" ").append(CustomLogicModel.escape(equation));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Simplified parsing with backward compatibility
     * New format: rows cols cellWidth cellHeight cellSpacing showInitialValues decimalPlaces showCellValues collapsedMode priority
     *             tableTitle tableUnits
     *             [col headers] [row descriptions] [initial values] [column types] [equations]
     * Old format: rows cols cellWidth cellHeight cellSpacing showInitialValues decimalPlaces
     *             tableTitle tableUnits
     *             [col headers] [row descriptions] [initial values] [column types] [equations]
     */
    public void parseTableData(StringTokenizer st) {
        try {
            // Parse dimensions and display settings (all simple types in order)
            table.rows = readInt(st, 0);
            table.cols = readInt(st, 4);
            table.cellWidthInGrids = readInt(st, 6);
            table.cellHeight = readInt(st, 16);
            table.cellSpacing = readInt(st, 0);
            table.showInitialValues = readBoolean(st, false);
            table.decimalPlaces = readInt(st, 2);
            
            // Peek ahead to detect file format version
            // If next token is a boolean, it's the new format with showCellValues
            // If next token is a string, it's the old format without showCellValues (it's the table title)
            String nextToken = st.hasMoreTokens() ? st.nextToken() : "Table";
            if (nextToken.equals("true") || nextToken.equals("false")) {
                // New format: read showCellValues and check for collapsedMode
                table.showCellValues = Boolean.parseBoolean(nextToken);
                
                // Peek again to check for collapsedMode (even newer format)
                String nextToken2 = st.hasMoreTokens() ? st.nextToken() : "Table";
                if (nextToken2.equals("true") || nextToken2.equals("false")) {
                    // Newest format: has collapsedMode, check for priority
                    table.collapsedMode = Boolean.parseBoolean(nextToken2);
                    
                    // Peek again to check for priority (newest format)
                    String nextToken3 = st.hasMoreTokens() ? st.nextToken() : "5";
                    try {
                        // Try to parse as integer (priority)
                        table.priority = Integer.parseInt(nextToken3);
                        table.tableTitle = readString(st, "Table");
                    } catch (NumberFormatException e) {
                        // Not a number - it's the table title (format without priority)
                        table.priority = 5; // Default priority
                        table.tableTitle = CustomLogicModel.unescape(nextToken3);
                    }
                } else {
                    // New format without collapsedMode
                    table.collapsedMode = false; // Default for files without collapsedMode
                    table.priority = 5; // Default priority
                    table.tableTitle = CustomLogicModel.unescape(nextToken2);
                }
            } else {
                // Old format: the token we just read IS the table title
                table.showCellValues = false; // Default for old files
                table.collapsedMode = false; // Default for old files
                table.priority = 5; // Default priority
                table.tableTitle = CustomLogicModel.unescape(nextToken);
            }
            
            table.tableUnits = readString(st, "");
            
            // Initialize arrays now that we know dimensions
            initializeArrays();
            
            // Parse column headers (cols count)
            for (int col = 0; col < table.cols; col++) {
                String header = readString(st, "Stock" + (col + 1));
                // A_L_E columns (last column when cols >= 4) should have blank header
                table.outputNames[col] = isALEColumn(col) ? "" : header;
            }
            
            // Parse row descriptions (rows count)
            for (int row = 0; row < table.rows; row++) {
                table.rowDescriptions[row] = readString(st, "Row" + (row + 1));
            }
            
            // Parse initial values (cols count)
            for (int col = 0; col < table.cols; col++) {
                table.initialValues[col] = readDouble(st, 0.0);
            }
            
            // Parse column types (cols count)
            for (int col = 0; col < table.cols; col++) {
                table.columnTypes[col] = readColumnType(st, getDefaultColumnType(col));
            }
            
            // Parse cell equations (rows * cols count)
            for (int row = 0; row < table.rows; row++) {
                for (int col = 0; col < table.cols; col++) {
                    table.cellEquations[row][col] = readString(st, "");
                }
            }
            
            CirSim.console("TableElm: Successfully parsed table data");
        } catch (Exception e) {
            CirSim.console("TableElm: Error parsing table data: " + e.getMessage());
            e.printStackTrace();
            initializeDefaults();
        }
    }
    
    // Simple helper methods for reading tokens
    private int readInt(StringTokenizer st, int defaultValue) {
        if (!st.hasMoreTokens()) return defaultValue;
        try {
            return Integer.parseInt(st.nextToken());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private double readDouble(StringTokenizer st, double defaultValue) {
        if (!st.hasMoreTokens()) return defaultValue;
        try {
            return Double.parseDouble(st.nextToken());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private boolean readBoolean(StringTokenizer st, boolean defaultValue) {
        if (!st.hasMoreTokens()) return defaultValue;
        return Boolean.parseBoolean(st.nextToken());
    }
    
    private String readString(StringTokenizer st, String defaultValue) {
        if (!st.hasMoreTokens()) return defaultValue;
        return CustomLogicModel.unescape(st.nextToken());
    }
    
    private ColumnType readColumnType(StringTokenizer st, ColumnType defaultValue) {
        if (!st.hasMoreTokens()) return defaultValue;
        try {
            return ColumnType.valueOf(st.nextToken());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
    
    private void initializeArrays() {
        table.outputNames = new String[table.cols];
        table.cellEquations = new String[table.rows][table.cols];
        table.initialValues = new double[table.cols];
        table.rowDescriptions = new String[table.rows];
        table.columnTypes = new ColumnType[table.cols];
    }
    
    /**
     * Resize table and preserve existing data where possible
     */
    public void resizeTable(int newRows, int newCols) {
        // Save old data
        String[] oldOutputNames = table.outputNames;
        String[][] oldEquations = table.cellEquations;
        double[] oldInitialValues = table.initialValues;
        ColumnType[] oldColumnTypes = table.columnTypes;
        String[] oldRowDescriptions = table.rowDescriptions;

        // Update dimensions
        table.rows = newRows;
        table.cols = newCols;

        // Create new arrays
        createNewArrays();
        
        // Copy existing data
        copyExistingData(oldOutputNames, oldEquations, oldInitialValues, 
                        oldColumnTypes, oldRowDescriptions);
    }
    
    private void createNewArrays() {
        table.outputNames = new String[table.cols];
        table.cellEquations = new String[table.rows][table.cols];
        table.compiledExpressions = new Expr[table.rows][table.cols];
        table.expressionStates = new ExprState[table.rows][table.cols];
        table.initialValues = new double[table.cols];
        table.columnTypes = new ColumnType[table.cols];
        table.rowDescriptions = new String[table.rows];

        // Initialize all cells with empty strings
        for (int row = 0; row < table.rows; row++) {
            for (int col = 0; col < table.cols; col++) {
                table.cellEquations[row][col] = "";
                table.compiledExpressions[row][col] = null;
                table.expressionStates[row][col] = new ExprState(1);
            }
        }
    }
    
    private void copyExistingData(String[] oldOutputNames, String[][] oldEquations,
                                 double[] oldInitialValues, ColumnType[] oldColumnTypes,
                                 String[] oldRowDescriptions) {
        copyEquations(oldEquations);
        copyInitialValues(oldInitialValues);
        copyOutputNames(oldOutputNames);
        copyColumnTypes(oldColumnTypes);
        copyRowDescriptions(oldRowDescriptions);
    }
    
    private void copyEquations(String[][] oldEquations) {
        if (oldEquations == null) return;
        
        int copyRows = Math.min(table.rows, oldEquations.length);
        for (int row = 0; row < copyRows; row++) {
            if (oldEquations[row] != null) {
                int copyCols = Math.min(table.cols, oldEquations[row].length);
                for (int col = 0; col < copyCols; col++) {
                    if (oldEquations[row][col] != null) {
                        table.cellEquations[row][col] = oldEquations[row][col];
                        // Recompile equations for copied cells
                        if (!table.cellEquations[row][col].isEmpty()) {
                            table.compileEquation(row, col, table.cellEquations[row][col]);
                        }
                    }
                }
            }
        }
    }
    
    private void copyInitialValues(double[] oldInitialValues) {
        if (oldInitialValues != null) {
            int copyCols = Math.min(table.cols, oldInitialValues.length);
            for (int col = 0; col < copyCols; col++) {
                table.initialValues[col] = oldInitialValues[col];
            }
        }
        // Initialize remaining values with zeros
        for (int col = 0; col < table.cols; col++) {
            if (oldInitialValues == null || col >= oldInitialValues.length) {
                table.initialValues[col] = 0.0;
            }
        }
    }
    
    private void copyOutputNames(String[] oldOutputNames) {
        if (oldOutputNames != null) {
            int copyCols = Math.min(table.cols, oldOutputNames.length);
            for (int col = 0; col < copyCols; col++) {
                table.outputNames[col] = oldOutputNames[col];
            }
        }
        // Initialize remaining output names
        for (int col = 0; col < table.cols; col++) {
            if (oldOutputNames == null || col >= oldOutputNames.length || 
                table.outputNames[col] == null || table.outputNames[col].isEmpty()) {
                table.outputNames[col] = "Stock_" + (col + 1);
            }
        }
    }
    
    private void copyColumnTypes(ColumnType[] oldColumnTypes) {
        // For each column, preserve old type if it exists
        // Clear equations from old last column if table size changed
        int oldCols = (oldColumnTypes != null) ? oldColumnTypes.length : 0;
        boolean oldHadALE = oldCols >= 4;
        int oldALECol = oldHadALE ? oldCols - 1 : -1;
        
        for (int col = 0; col < table.cols; col++) {
            ColumnType newDefaultType = getDefaultColumnType(col);
            ColumnType oldType = (oldColumnTypes != null && col < oldColumnTypes.length) 
                                 ? oldColumnTypes[col] : null;
            
            if (oldType != null) {
                // If this column was the old A_L_E position but is no longer A_L_E, clear equations
                if (col == oldALECol && !isALEColumn(col)) {
                    // Old A_L_E column is no longer the last - make it ASSET and clear equations
                    table.columnTypes[col] = ColumnType.ASSET;
                    for (int row = 0; row < table.rows; row++) {
                        if (table.cellEquations != null && table.cellEquations[row] != null) {
                            table.cellEquations[row][col] = "";
                            table.compiledExpressions[row][col] = null;
                        }
                    }
                } else {
                    // Preserve the old type
                    table.columnTypes[col] = oldType;
                }
            } else {
                // New column - use default type
                table.columnTypes[col] = newDefaultType;
            }
        }
    }
    
    private void copyRowDescriptions(String[] oldRowDescriptions) {
        if (oldRowDescriptions != null) {
            int copyRows = Math.min(table.rows, oldRowDescriptions.length);
            for (int row = 0; row < copyRows; row++) {
                table.rowDescriptions[row] = oldRowDescriptions[row];
            }
        }
        // Initialize remaining row descriptions
        for (int row = 0; row < table.rows; row++) {
            if (oldRowDescriptions == null || row >= oldRowDescriptions.length ||
                table.rowDescriptions[row] == null || table.rowDescriptions[row].isEmpty()) {
                table.rowDescriptions[row] = "Flow_" + (row + 1);
            }
        }
    }
}
