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
     * The last column is always A_L_E if there are at least 4 columns
     */
    private ColumnType getDefaultColumnType(int col) {
        // For tables with 4+ columns, the last column is always A_L_E
        if (table.cols >= 4 && col == table.cols - 1) {
            return ColumnType.A_L_E;
        }
        
        switch (col) {
            case 0: return ColumnType.ASSET;
            case 1: return ColumnType.LIABILITY;
            case 2: return ColumnType.EQUITY;
            default: return ColumnType.ASSET;
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
                // A_L_E columns should have blank name
                if (table.columnTypes != null && table.columnTypes[i] == ColumnType.A_L_E) {
                    table.outputNames[i] = "";
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
        
        // Ensure no null or empty values exist in output names (except A_L_E which should be blank)
        for (int col = 0; col < table.cols; col++) {
            // Skip A_L_E columns - they should remain blank
            if (table.columnTypes != null && table.columnTypes[col] == ColumnType.A_L_E) {
                if (table.outputNames[col] == null) {
                    table.outputNames[col] = "";
                }
                continue;
            }
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
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(" ").append(table.rows).append(" ").append(table.cols).append(" ").append(table.showInitialValues);
        sb.append(" ").append(CustomLogicModel.escape(table.tableUnits)).append(" ").append(table.decimalPlaces);
        sb.append(" ").append(CustomLogicModel.escape(table.tableTitle));
        
        // Serialize output names (used as column headers)
        for (int col = 0; col < table.cols; col++) {
            sb.append(" ").append(CustomLogicModel.escape(table.outputNames[col]));
        }
        
        // Serialize row descriptions
        for (int row = 0; row < table.rows; row++) {
            sb.append(" ").append(CustomLogicModel.escape(table.rowDescriptions[row]));
        }
        
        // Always serialize initial conditions values
        if (table.initialValues != null) {
            for (int col = 0; col < table.cols; col++) {
                sb.append(" ").append(table.initialValues[col]);
            }
        }
        
        // Serialize column types
        if (table.columnTypes != null) {
            for (int col = 0; col < table.cols; col++) {
                sb.append(" ").append(table.columnTypes[col].name());
            }
        }
        
        // Serialize equation data only
        for (int row = 0; row < table.rows; row++) {
            for (int col = 0; col < table.cols; col++) {
                String equation = (table.cellEquations[row][col] != null) ? table.cellEquations[row][col] : "";
                sb.append(" ").append(CustomLogicModel.escape(equation));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Parse table data from saved string
     */
    public void parseTableData(StringTokenizer st) {
        try {
            parseDimensions(st);
            initializeArrays();
            parseProperties(st);
            parseColumnHeaders(st);
            parseRowDescriptions(st);
            parseInitialValues(st);
            parseColumnTypes(st);
            parseCellEquations(st);
            
            CirSim.console("TableElm: Successfully parsed table data");
        } catch (Exception e) {
            CirSim.console("TableElm: Error parsing table data: " + e.getMessage());
            initializeDefaults();
        }
    }
    
    private void parseDimensions(StringTokenizer st) {
        if (st.hasMoreTokens()) table.rows = Integer.parseInt(st.nextToken());
        if (st.hasMoreTokens()) table.cols = Integer.parseInt(st.nextToken());
    }
    
    private void initializeArrays() {
        table.outputNames = new String[table.cols];
        table.cellEquations = new String[table.rows][table.cols];
        table.initialValues = new double[table.cols];
        table.rowDescriptions = new String[table.rows];
    }
    
    private void parseProperties(StringTokenizer st) {
        table.showInitialValues = st.hasMoreTokens() ? Boolean.parseBoolean(st.nextToken()) : false;
        table.tableUnits = st.hasMoreTokens() ? CustomLogicModel.unescape(st.nextToken()) : "";
        table.decimalPlaces = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 2;
        table.tableTitle = st.hasMoreTokens() ? CustomLogicModel.unescape(st.nextToken()) : "Table";
    }
    
    private void parseColumnHeaders(StringTokenizer st) {
        for (int col = 0; col < table.cols; col++) {
            if (st.hasMoreTokens()) {
                String headerValue = CustomLogicModel.unescape(st.nextToken());
                // For A_L_E columns, ensure blank header (even if file had a value)
                if (table.columnTypes != null && table.columnTypes[col] == ColumnType.A_L_E) {
                    table.outputNames[col] = "";
                } else {
                    table.outputNames[col] = headerValue;
                }
            } else {
                // Default name for missing headers (except A_L_E which should be blank)
                if (table.columnTypes != null && table.columnTypes[col] == ColumnType.A_L_E) {
                    table.outputNames[col] = "";
                } else {
                    table.outputNames[col] = "Stock" + (col + 1);
                }
            }
        }
    }
    
    private void parseRowDescriptions(StringTokenizer st) {
        for (int row = 0; row < table.rows; row++) {
            if (st.hasMoreTokens()) {
                String token = st.nextToken();
                // Check if token is a row description or initial value (backward compatibility)
                if (isNumeric(token)) {
                    // It's numeric, so no row descriptions in file - use defaults
                    setDefaultRowDescriptions();
                    break;
                } else {
                    table.rowDescriptions[row] = CustomLogicModel.unescape(token);
                }
            } else {
                table.rowDescriptions[row] = "Row" + (row + 1);
            }
        }
    }
    
    private void setDefaultRowDescriptions() {
        for (int row = 0; row < table.rows; row++) {
            table.rowDescriptions[row] = "Row" + (row + 1);
        }
    }
    
    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private void parseInitialValues(StringTokenizer st) {
        for (int col = 0; col < table.cols; col++) {
            if (st.hasMoreTokens()) {
                try {
                    table.initialValues[col] = Double.parseDouble(st.nextToken());
                } catch (NumberFormatException e) {
                    table.initialValues[col] = 0.0;
                }
            } else {
                table.initialValues[col] = 0.0;
            }
        }
    }
    
    private void parseColumnTypes(StringTokenizer st) {
        table.columnTypes = new ColumnType[table.cols];
        boolean hasColumnTypes = false;
        
        for (int col = 0; col < table.cols; col++) {
            if (st.hasMoreTokens()) {
                String typeToken = st.nextToken();
                try {
                    table.columnTypes[col] = ColumnType.valueOf(typeToken);
                    hasColumnTypes = true;
                } catch (IllegalArgumentException e) {
                    // Not a valid column type - must be start of equations
                    if (table.cellEquations == null) {
                        table.cellEquations = new String[table.rows][table.cols];
                    }
                    table.cellEquations[0][col] = CustomLogicModel.unescape(typeToken);
                    break;
                }
            } else {
                break;
            }
        }
        
        if (!hasColumnTypes) {
            initializeColumnTypes();
        }
    }
    
    private void parseCellEquations(StringTokenizer st) {
        for (int row = 0; row < table.rows; row++) {
            for (int col = 0; col < table.cols; col++) {
                if (st.hasMoreTokens()) {
                    table.cellEquations[row][col] = CustomLogicModel.unescape(st.nextToken());
                } else {
                    table.cellEquations[row][col] = "";
                }
            }
        }
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
        // For each column, preserve old type if it exists, unless it's an A_L_E column
        // that's no longer in the last position
        for (int col = 0; col < table.cols; col++) {
            ColumnType newDefaultType = getDefaultColumnType(col);
            ColumnType oldType = (oldColumnTypes != null && col < oldColumnTypes.length) 
                                 ? oldColumnTypes[col] : null;
            
            if (oldType != null) {
                // If this column was A_L_E but the last column has moved, update it
                if (oldType == ColumnType.A_L_E && newDefaultType != ColumnType.A_L_E) {
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
