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
        
        // Initialize output names if not set
        if (table.outputNames == null) {
            table.outputNames = new String[table.cols];
            for (int i = 0; i < table.cols; i++) {
                table.outputNames[i] = "Stock_" + (i + 1);
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
        
        // Initialize column types if not set
        if (table.columnTypes == null) {
            table.columnTypes = new ColumnType[table.cols];
            for (int i = 0; i < table.cols; i++) {
                if (i == 0) {
                    table.columnTypes[i] = ColumnType.ASSET;
                } else if (i == 1) {
                    table.columnTypes[i] = ColumnType.LIABILITY;
                } else if (i == 2) {
                    table.columnTypes[i] = ColumnType.EQUITY;
                } else if (i == 3) {
                    table.columnTypes[i] = ColumnType.A_L_E;
                } else {
                    table.columnTypes[i] = ColumnType.ASSET;
                }
            }
        }
        
        // Ensure no null or empty values exist
        for (int row = 0; row < table.rows; row++) {
            for (int col = 0; col < table.cols; col++) {
                if (table.cellEquations[row][col] == null || table.cellEquations[row][col].trim().isEmpty()) {
                    table.cellEquations[row][col] = "";
                }
            }
        }
        
        for (int i = 0; i < table.cols; i++) {
            if (table.outputNames[i] == null || table.outputNames[i].trim().isEmpty()) {
                table.outputNames[i] = "Stock" + (i + 1);
            }
        }
    }
    
    /**
     * Initialize with default values (error fallback)
     */
    public void initializeDefaults() {
        if (table.outputNames == null) {
            table.outputNames = new String[table.cols];
            for (int col = 0; col < table.cols; col++) {
                table.outputNames[col] = "Stock" + (col + 1);
            }
        }
        if (table.rowDescriptions == null) {
            table.rowDescriptions = new String[table.rows];
            for (int row = 0; row < table.rows; row++) {
                table.rowDescriptions[row] = "Row" + (row + 1);
            }
        }
        if (table.columnTypes == null) {
            table.columnTypes = new ColumnType[table.cols];
            for (int col = 0; col < table.cols; col++) {
                if (col == 0) {
                    table.columnTypes[col] = ColumnType.ASSET;
                } else if (col == 1) {
                    table.columnTypes[col] = ColumnType.LIABILITY;
                } else if (col == 2) {
                    table.columnTypes[col] = ColumnType.EQUITY;
                } else if (col == 3) {
                    table.columnTypes[col] = ColumnType.A_L_E;
                } else {
                    table.columnTypes[col] = ColumnType.ASSET;
                }
            }
        }
        if (table.cellEquations == null) {
            table.cellEquations = new String[table.rows][table.cols];
            for (int row = 0; row < table.rows; row++) {
                for (int col = 0; col < table.cols; col++) {
                    table.cellEquations[row][col] = "";
                }
            }
        }
        if (table.initialValues == null) {
            table.initialValues = new double[table.cols];
            for (int col = 0; col < table.cols; col++) {
                table.initialValues[col] = 0.0;
            }
        }
        table.showInitialValues = false;
        table.tableUnits = "";
        table.decimalPlaces = 2;
        table.tableTitle = "Table";
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
            // Parse basic dimensions
            if (st.hasMoreTokens()) table.rows = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens()) table.cols = Integer.parseInt(st.nextToken());
            
            // Initialize arrays
            table.outputNames = new String[table.cols];
            table.cellEquations = new String[table.rows][table.cols];
            table.initialValues = new double[table.cols];
            table.rowDescriptions = new String[table.rows];
            
            // Parse table properties 
            table.showInitialValues = st.hasMoreTokens() ? Boolean.parseBoolean(st.nextToken()) : false;
            table.tableUnits = st.hasMoreTokens() ? CustomLogicModel.unescape(st.nextToken()) : "";
            table.decimalPlaces = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 2;
            table.tableTitle = st.hasMoreTokens() ? CustomLogicModel.unescape(st.nextToken()) : "Table";
            
            // Parse column headers
            for (int col = 0; col < table.cols; col++) {
                if (st.hasMoreTokens()) {
                    table.outputNames[col] = CustomLogicModel.unescape(st.nextToken());
                } else {
                    table.outputNames[col] = "Stock" + (col + 1);
                }
            }
            
            // Parse row descriptions (if available - for backwards compatibility)
            for (int row = 0; row < table.rows; row++) {
                if (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    // Try to determine if this is a row description or initial value
                    try {
                        // If it parses as a double, it's probably an initial value
                        Double.parseDouble(token);
                        // It's a number, so no row descriptions in this file
                        // Use default row descriptions
                        for (int r = 0; r < table.rows; r++) {
                            table.rowDescriptions[r] = "Row" + (r + 1);
                        }
                        // Don't consume this token - it's for initial values
                        break;
                    } catch (NumberFormatException e) {
                        // It's a string, so it's a row description
                        table.rowDescriptions[row] = CustomLogicModel.unescape(token);
                    }
                } else {
                    table.rowDescriptions[row] = "Row" + (row + 1);
                }
            }
            
            // Parse initial values
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
            
            // Parse column types (if available in saved data)
            table.columnTypes = new ColumnType[table.cols];
            boolean hasColumnTypes = false;
            for (int col = 0; col < table.cols; col++) {
                if (st.hasMoreTokens()) {
                    String typeToken = st.nextToken();
                    // Check if this looks like a column type or an equation
                    try {
                        table.columnTypes[col] = ColumnType.valueOf(typeToken);
                        hasColumnTypes = true;
                    } catch (IllegalArgumentException e) {
                        // Not a valid column type, must be start of equations
                        // Push back by saving for equation parsing
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
            
            // If no column types were found, initialize with defaults
            if (!hasColumnTypes) {
                for (int col = 0; col < table.cols; col++) {
                    if (col == 0) {
                        table.columnTypes[col] = ColumnType.ASSET;
                    } else if (col == 1) {
                        table.columnTypes[col] = ColumnType.LIABILITY;
                    } else if (col == 2) {
                        table.columnTypes[col] = ColumnType.EQUITY;
                    } else if (col == 3) {
                        table.columnTypes[col] = ColumnType.A_L_E;
                    } else {
                        table.columnTypes[col] = ColumnType.ASSET;
                    }
                }
            }
            
            // Parse cell equations
            for (int row = 0; row < table.rows; row++) {
                for (int col = 0; col < table.cols; col++) {
                    if (st.hasMoreTokens()) {
                        table.cellEquations[row][col] = CustomLogicModel.unescape(st.nextToken());
                    } else {
                        table.cellEquations[row][col] = "";
                    }
                }
            }
            
            CirSim.console("TableElm: Successfully parsed simplified table data");
            
        } catch (Exception e) {
            CirSim.console("TableElm: Error parsing table data: " + e.getMessage());
            // Initialize with defaults on error
            initializeDefaults();
        }
    }
    
    /**
     * Resize table and preserve existing data where possible
     * This method handles all data structure resizing and copying
     */
    public void resizeTable(int newRows, int newCols) {
        String[] oldOutputNames = table.outputNames;
        String[][] oldEquations = table.cellEquations;
        double[] oldInitialConditions = table.initialValues;
        ColumnType[] oldColumnTypes = table.columnTypes;
        String[] oldRowDescriptions = table.rowDescriptions;

        // Update dimensions
        table.rows = newRows;
        table.cols = newCols;

        // Create new arrays
        table.outputNames = new String[table.cols];
        table.cellEquations = new String[table.rows][table.cols];
        table.compiledExpressions = new Expr[table.rows][table.cols];
        table.expressionStates = new ExprState[table.rows][table.cols];
        table.initialValues = new double[table.cols];
        table.columnTypes = new ColumnType[table.cols];
        table.rowDescriptions = new String[table.rows];

        // Initialize ALL cells with empty strings (null equivalent)
        for (int row = 0; row < table.rows; row++) {
            for (int col = 0; col < table.cols; col++) {
                table.cellEquations[row][col] = "";
                table.compiledExpressions[row][col] = null;
                table.expressionStates[row][col] = new ExprState(1); // Only need time variable
            }
        }

        // Copy over existing data where possible
        if (oldEquations != null) {
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

        // Copy over existing initial conditions where possible
        if (oldInitialConditions != null) {
            for (int col = 0; col < Math.min(table.cols, oldInitialConditions.length); col++) {
                table.initialValues[col] = oldInitialConditions[col];
            }
        }

        // Initialize remaining initial conditions values with zeros
        for (int col = 0; col < table.cols; col++) {
            if (oldInitialConditions == null || col >= oldInitialConditions.length) {
                table.initialValues[col] = 0.0;
            }
        }

        // Copy over existing output names where possible
        if (oldOutputNames != null) {
            for (int col = 0; col < Math.min(table.cols, oldOutputNames.length); col++) {
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
        
        // Copy over existing column types where possible
        if (oldColumnTypes != null) {
            for (int col = 0; col < Math.min(table.cols, oldColumnTypes.length); col++) {
                table.columnTypes[col] = oldColumnTypes[col];
            }
        }
        
        // Initialize remaining column types with default values
        for (int col = 0; col < table.cols; col++) {
            if (oldColumnTypes == null || col >= oldColumnTypes.length) {
                if (col == 0) {
                    table.columnTypes[col] = ColumnType.ASSET;
                } else if (col == 1) {
                    table.columnTypes[col] = ColumnType.LIABILITY;
                } else if (col == 2) {
                    table.columnTypes[col] = ColumnType.EQUITY;
                } else if (col == 3) {
                    table.columnTypes[col] = ColumnType.A_L_E;
                } else {
                    table.columnTypes[col] = ColumnType.ASSET;
                }
            }
        }
        
        // Copy over existing row descriptions where possible
        if (oldRowDescriptions != null) {
            for (int row = 0; row < Math.min(table.rows, oldRowDescriptions.length); row++) {
                table.rowDescriptions[row] = oldRowDescriptions[row];
            }
        }
        
        // Initialize remaining row descriptions with default values
        for (int row = 0; row < table.rows; row++) {
            if (oldRowDescriptions == null || row >= oldRowDescriptions.length ||
                table.rowDescriptions[row] == null || table.rowDescriptions[row].isEmpty()) {
                table.rowDescriptions[row] = "Flow_" + (row + 1);
            }
        }
    }
}
