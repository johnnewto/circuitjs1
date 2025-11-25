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
     */
    private boolean isALEColumn(int col) {
        return col >= 0 && col < table.columns.size() && table.columns.get(col).isALE();
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
        // Initialize columns ArrayList if not set
        if (table.columns == null) {
            table.columns = new java.util.ArrayList<TableColumn>();
            int cols = table.getCols(); // Get from any remaining cols field or default
            for (int i = 0; i < cols; i++) {
                ColumnType type = getDefaultColumnType(i);
                table.columns.add(new TableColumn("", type, 0.0, table.rows));
            }
        }
        
        // Initialize row descriptions if not set
        if (table.rowDescriptions == null) {
            table.rowDescriptions = new String[table.rows];
            for (int i = 0; i < table.rows; i++) {
                table.rowDescriptions[i] = "Flow_" + (i + 1);
            }
        }
        
        // Ensure all columns have correct row count
        for (int col = 0; col < table.columns.size(); col++) {
            TableColumn column = table.columns.get(col);
            if (column.getRowCount() != table.rows) {
                column.resizeRows(table.rows);
            }
            
            // Set A-L-E label if needed
            if (column.isALE() && (column.getStockName() == null || column.getStockName().isEmpty())) {
                column.setStockName("A-L-E");
            }
        }
    }
    
    /**
     * Initialize with default values (error fallback)
     */
    public void initializeDefaults() {
        int cols = table.getCols();
        
        // Initialize columns ArrayList
        table.columns = new java.util.ArrayList<TableColumn>();
        for (int col = 0; col < cols; col++) {
            ColumnType type = getDefaultColumnType(col);
            table.columns.add(new TableColumn("", type, 0.0, table.rows));
        }
        
        initializeRowDescriptions();
        
        table.showInitialValues = false;
        table.tableUnits = "";
        table.decimalPlaces = 2;
        table.tableTitle = "Table";
    }
    

    
    private void initializeRowDescriptions() {
        if (table.rowDescriptions == null) {
            table.rowDescriptions = new String[table.rows];
        }
        for (int row = 0; row < table.rows; row++) {
            table.rowDescriptions[row] = "Row" + (row + 1);
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
        int cols = table.getCols();
        
        // Basic dimensions and display settings (all integers/booleans)
        sb.append(" ").append(table.rows);
        sb.append(" ").append(cols);
        sb.append(" ").append(table.cellWidthInGrids);
        sb.append(" ").append(table.cellHeight);
        sb.append(" ").append(table.cellSpacing);
        sb.append(" ").append(table.showInitialValues);
        sb.append(" ").append(table.decimalPlaces);
        sb.append(" ").append(table.showCellValues);
        sb.append(" ").append(table.collapsedMode);
        sb.append(" ").append(table.priority);
        sb.append(" ").append(table.initMode);
        
        // Text fields (escaped)
        sb.append(" ").append(CustomLogicModel.escape(table.tableTitle));
        sb.append(" ").append(CustomLogicModel.escape(table.tableUnits));
        
        // Column headers (cols count)
        for (int col = 0; col < cols; col++) {
            String colName = table.columns.get(col).getStockName();
            sb.append(" ").append(CustomLogicModel.escape(colName != null ? colName : ""));
        }
        
        // Row descriptions (rows count)
        for (int row = 0; row < table.rows; row++) {
            String rowDesc = (table.rowDescriptions[row] != null) ? table.rowDescriptions[row] : "";
            sb.append(" ").append(CustomLogicModel.escape(rowDesc));
        }
        
        // Initial values (cols count)
        for (int col = 0; col < cols; col++) {
            sb.append(" ").append(table.columns.get(col).getInitialValue());
        }
        
        // Column types (cols count)
        for (int col = 0; col < cols; col++) {
            sb.append(" ").append(table.columns.get(col).getType().name());
        }
        
        // Cell equations (rows * cols count)
        for (int row = 0; row < table.rows; row++) {
            for (int col = 0; col < cols; col++) {
                String equation = table.columns.get(col).getCellEquation(row);
                sb.append(" ").append(CustomLogicModel.escape(equation != null ? equation : ""));
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
            int cols = readInt(st, 4);
            table.cellWidthInGrids = readInt(st, 6);
            table.cellHeight = readInt(st, 16);
            table.cellSpacing = readInt(st, 0);
            table.showInitialValues = readBoolean(st, false);
            table.decimalPlaces = readInt(st, 2);
            
            // Peek ahead to detect file format version
            // If next token is a boolean or number 0-2, it's the new format with showCellValues
            // If next token is a string, it's the old format without showCellValues (it's the table title)
            String nextToken = st.hasMoreTokens() ? st.nextToken() : "Table";
            if (nextToken.equals("true") || nextToken.equals("false") || nextToken.equals("0") || nextToken.equals("1") || nextToken.equals("2")) {
                // New format: read showCellValues (backward compatible: false=0, true=1)
                if (nextToken.equals("false")) {
                    table.showCellValues = 0; // Equation only
                } else if (nextToken.equals("true")) {
                    table.showCellValues = 1; // Equation: Value
                } else {
                    table.showCellValues = Integer.parseInt(nextToken); // 0, 1, or 2
                }
                
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
                        
                        // Peek again to check for initMode (even newer format)
                        String nextToken4 = st.hasMoreTokens() ? st.nextToken() : "0";
                        try {
                            // Try to parse as integer (initMode)
                            table.initMode = Integer.parseInt(nextToken4);
                            table.tableTitle = readString(st, "Table");
                        } catch (NumberFormatException e2) {
                            // Not a number - it's the table title (format without initMode)
                            table.initMode = 0; // Default initMode
                            table.tableTitle = CustomLogicModel.unescape(nextToken4);
                        }
                    } catch (NumberFormatException e) {
                        // Not a number - it's the table title (format without priority)
                        table.priority = 5; // Default priority
                        table.initMode = 0; // Default initMode
                        table.tableTitle = CustomLogicModel.unescape(nextToken3);
                    }
                } else {
                    // New format without collapsedMode
                    table.collapsedMode = false; // Default for files without collapsedMode
                    table.priority = 5; // Default priority
                    table.initMode = 0; // Default initMode
                    table.tableTitle = CustomLogicModel.unescape(nextToken2);
                }
            } else {
                // Old format: the token we just read IS the table title
                table.showCellValues = 0; // Default for old files (Equation only)
                table.collapsedMode = false; // Default for old files
                table.priority = 5; // Default priority
                table.initMode = 0; // Default initMode
                table.tableTitle = CustomLogicModel.unescape(nextToken);
            }
            
            table.tableUnits = readString(st, "");
            
            // Parse temporary arrays for data
            String[] stockNames = new String[cols];
            double[] initialValues = new double[cols];
            ColumnType[] columnTypes = new ColumnType[cols];
            String[][] cellEquations = new String[table.rows][cols];
            
            // Initialize row descriptions array
            table.rowDescriptions = new String[table.rows];
            
            // Parse column headers (cols count)
            for (int col = 0; col < cols; col++) {
                stockNames[col] = readString(st, "");
            }
            
            // Parse row descriptions (rows count)
            for (int row = 0; row < table.rows; row++) {
                table.rowDescriptions[row] = readString(st, "Row" + (row + 1));
            }
            
            // Parse initial values (cols count)
            for (int col = 0; col < cols; col++) {
                initialValues[col] = readDouble(st, 0.0);
            }
            
            // Parse column types (cols count)
            for (int col = 0; col < cols; col++) {
                columnTypes[col] = readColumnType(st, getDefaultColumnType(col));
            }
            
            // Parse cell equations (rows * cols count)
            for (int row = 0; row < table.rows; row++) {
                for (int col = 0; col < cols; col++) {
                    cellEquations[row][col] = readString(st, "");
                }
            }
            
            // Now build columns ArrayList from parsed data
            table.columns = new java.util.ArrayList<TableColumn>();
            for (int col = 0; col < cols; col++) {
                TableColumn column = new TableColumn(
                    stockNames[col] != null ? stockNames[col] : "",
                    columnTypes[col],
                    initialValues[col],
                    table.rows
                );
                
                // Set cell equations for this column
                for (int row = 0; row < table.rows; row++) {
                    column.setCellEquation(row, cellEquations[row][col]);
                }
                
                table.columns.add(column);
            }
            
            // Set A-L-E labels if needed
            for (int col = 0; col < table.columns.size(); col++) {
                TableColumn column = table.columns.get(col);
                if (column.isALE() && (column.getStockName() == null || column.getStockName().isEmpty())) {
                    column.setStockName("A-L-E");
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
    
    /**
     * Resize table and preserve existing data where possible
     */
    public void resizeTable(int newRows, int newCols) {
        int oldCols = table.columns.size();
        
        // Update row count
        table.rows = newRows;
        
        // Resize existing columns
        for (int col = 0; col < oldCols && col < newCols; col++) {
            table.columns.get(col).resizeRows(newRows);
        }
        
        // Add new columns if needed
        while (table.columns.size() < newCols) {
            int col = table.columns.size();
            ColumnType type = getDefaultColumnType(col);
            table.columns.add(new TableColumn("", type, 0.0, newRows));
        }
        
        // Remove extra columns if needed
        while (table.columns.size() > newCols) {
            table.columns.remove(table.columns.size() - 1);
        }
        
        // Resize row descriptions
        String[] oldRowDescriptions = table.rowDescriptions;
        table.rowDescriptions = new String[newRows];
        if (oldRowDescriptions != null) {
            int copyRows = Math.min(newRows, oldRowDescriptions.length);
            for (int row = 0; row < copyRows; row++) {
                table.rowDescriptions[row] = oldRowDescriptions[row];
            }
        }
        // Initialize remaining row descriptions
        for (int row = 0; row < newRows; row++) {
            if (table.rowDescriptions[row] == null || table.rowDescriptions[row].isEmpty()) {
                table.rowDescriptions[row] = "Flow_" + (row + 1);
            }
        }
        
        // Recompile all equations after resize
        table.equationManager.recompileAllEquations();
    }
}
