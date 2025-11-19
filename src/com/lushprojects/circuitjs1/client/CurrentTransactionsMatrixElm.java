/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.TableEditDialog.ColumnType;
import java.util.ArrayList;

/**
 * CurrentTransactionsMatrixElm - Special table that auto-populates with all master stocks
 * 
 * This element extends TableElm and automatically creates columns for all master stocks
 * registered in the ComputedValues registry. When empty (no columns), it reads all master
 * stocks from the registry. When non-empty, it checks the registry and excludes any stocks
 * from other "Current Transactions Matrix" tables to avoid circular dependencies.
 * 
 * The table title is fixed as "Current Transactions Matrix" and it always operates in
 * collapsed mode to show only the computed row with all master stock values.
 */
public class CurrentTransactionsMatrixElm extends TableElm {
    private static final String MATRIX_TITLE = "Current Transactions Matrix";
    
    // Store source table names for each column (for display on second row)
    private String[] sourceTableNames;
    
    // Custom renderer for this matrix
    private CurrentTransactionsMatrixRenderer matrixRenderer;
    
    // Constructor for new matrix FROM MENU
    public CurrentTransactionsMatrixElm(int xx, int yy) {
        super(xx, yy); // Call parent constructor
        
        // Override title and mode after construction
        tableTitle = MATRIX_TITLE;
        collapsedMode = true; // Always show only computed row
        
        // Auto-populate with master stocks
        autoPopulateFromRegistry();
        
        // Create custom renderer
        matrixRenderer = new CurrentTransactionsMatrixRenderer(this);
        
        // Re-setup pins after changing structure
        setupPins();
    }

    // File loading constructor
    public CurrentTransactionsMatrixElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st); // Call TableElm's file loading constructor
        
        // Override title and mode after construction
        tableTitle = MATRIX_TITLE;
        collapsedMode = true; // Always collapsed
        
        // Check if we need to update from registry
        updateFromRegistry();
        
        // Create custom renderer
        matrixRenderer = new CurrentTransactionsMatrixRenderer(this);
        
        // Re-setup pins after potential update
        setupPins();
    }
    
    @Override
    int getDumpType() { 
        return 254; // Use available dump type (253 is TableElm, 255 is GodlyTableElm)
    }
    
    @Override
    void setupPins() {
        // DO NOT register as master - CTM is a display/monitor element only
        // It should never become the master for any stock
        // Call the geometry manager directly without calling parent setupPins
        // (parent would call registerAsMasterForOutputNames which we want to skip)
        
        // Access geometry manager through reflection of parent's protected method
        // Actually, just duplicate the essential geometry setup here
        int extraRows = collapsedMode ? 3 : 5; // title, type, header [, initial, computed]
        int rowDescColWidth = cellWidthInGrids;
        
        int tableWidthPixels = (rowDescColWidth + cols * cellWidthInGrids) * cspc + 2 * cspc;
        int tableHeightPixels = (rows + extraRows) * cellHeight + 
                               (rows + extraRows + 1) * cellSpacing + 20;
        
        sizeX = (tableWidthPixels + cspc2 - 1) / cspc2;
        sizeY = (tableHeightPixels + cspc2 - 1) / cspc2;
        
        // Create output pins
        pins = new Pin[cols];
        for (int i = 0; i < cols; i++) {
            pins[i] = new Pin(0, SIDE_S, "");
            pins[i].output = true;
        }
    }
    
    /**
     * Auto-populate columns from all master stocks in the registry
     * Columns are grouped by source table
     * When expanded, also populate rows with flows
     */
    private void autoPopulateFromRegistry() {
        String[] masterStocks = ComputedValues.getAllMasterStockNames();
        
        if (masterStocks.length == 0) {
            // No master stocks yet, create empty table with default columns
            cols = 4; // Default size
            rows = 0; // No data rows
            outputNames = new String[cols];
            columnTypes = new ColumnType[cols];
            initialValues = new double[cols];
            sourceTableNames = new String[cols];
            
            // Initialize with empty names
            for (int i = 0; i < cols; i++) {
                outputNames[i] = "";
                columnTypes[i] = ColumnType.ASSET;
                initialValues[i] = 0.0;
                sourceTableNames[i] = "";
            }
        } else {
            // Group stocks by source table for better organization
            ArrayList<StockInfo> stockInfoList = new ArrayList<StockInfo>();
            
            for (String stockName : masterStocks) {
                // Skip A-L-E computed columns - they are display-only, not real stocks
                if (isStockFromALEColumn(stockName)) {
                    continue;
                }
                
                StockInfo info = new StockInfo();
                info.stockName = stockName;
                info.columnType = getColumnTypeFromMasterTable(stockName);
                info.sourceTableName = getSourceTableName(stockName);
                stockInfoList.add(info);
            }
            
            // Sort by source table name to group columns together
            sortBySourceTable(stockInfoList);
            
            // Populate with grouped master stocks PLUS one extra column for A-L-E
            cols = stockInfoList.size() + 1; // +1 for A-L-E column
            outputNames = new String[cols];
            columnTypes = new ColumnType[cols];
            initialValues = new double[cols];
            sourceTableNames = new String[cols];
            
            // Copy stock columns
            for (int i = 0; i < stockInfoList.size(); i++) {
                StockInfo info = stockInfoList.get(i);
                outputNames[i] = info.stockName;
                columnTypes[i] = info.columnType;
                initialValues[i] = 0.0;
                sourceTableNames[i] = info.sourceTableName;
            }
            
            // Add A-L-E computed column at the end
            int aleIndex = cols - 1;
            outputNames[aleIndex] = "A-L-E";
            columnTypes[aleIndex] = ColumnType.ASSET; // A-L-E doesn't have its own type
            initialValues[aleIndex] = 0.0;
            sourceTableNames[aleIndex] = "";
            
            // Populate flows (rows) when not in collapsed mode
            if (!collapsedMode) {
                populateFlowsFromMasterTables(stockInfoList);
            } else {
                rows = 0; // No data rows in collapsed mode
            }
        }
        
        // Initialize cell equations array
        cellEquations = new String[rows][cols];
        compiledExpressions = new Expr[rows][cols];
        expressionStates = new ExprState[rows][cols];
        
        // Initialize row descriptions
        rowDescriptions = new String[rows];
        
        // Populate cell equations from master tables if we have rows
        if (rows > 0 && !collapsedMode) {
            populateCellEquationsFromMasters();
        }
    }
    
    /**
     * Helper class to store stock information
     */
    private static class StockInfo {
        String stockName;
        ColumnType columnType;
        String sourceTableName;
    }
    
    /**
     * Get the source table name for a stock
     */
    private String getSourceTableName(String stockName) {
        if (stockName == null || stockName.trim().isEmpty()) {
            return "";
        }
        
        Object masterTableObj = ComputedValues.getComputingTable(stockName.trim());
        
        if (masterTableObj instanceof TableElm) {
            TableElm masterTable = (TableElm) masterTableObj;
            return masterTable.tableTitle;
        }
        
        return "";
    }
    
    /**
     * Sort stock info list by source table name, then by column type (Asset, Liability, Equity)
     * This creates visual grouping by table with type ordering within each table
     */
    private void sortBySourceTable(ArrayList<StockInfo> list) {
        int n = list.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                StockInfo a = list.get(j);
                StockInfo b = list.get(j + 1);
                
                // Compare source table names first
                String nameA = (a.sourceTableName != null) ? a.sourceTableName : "";
                String nameB = (b.sourceTableName != null) ? b.sourceTableName : "";
                
                int tableCompare = nameA.compareTo(nameB);
                
                if (tableCompare > 0) {
                    // Swap - different tables, nameA comes after nameB
                    list.set(j, b);
                    list.set(j + 1, a);
                } else if (tableCompare == 0) {
                    // Same table - sort by column type: Asset < Liability < Equity
                    int typeOrderA = getTypeOrder(a.columnType);
                    int typeOrderB = getTypeOrder(b.columnType);
                    
                    if (typeOrderA > typeOrderB) {
                        // Swap - a's type comes after b's type
                        list.set(j, b);
                        list.set(j + 1, a);
                    }
                }
            }
        }
    }
    
    /**
     * Get ordering value for column type: Asset=0, Liability=1, Equity=2
     */
    private int getTypeOrder(ColumnType type) {
        if (type == null) return 0;
        switch (type) {
            case ASSET: return 0;
            case LIABILITY: return 1;
            case EQUITY: return 2;
            default: return 0;
        }
    }
    
    /**
     * Check if a stock name comes from an A-L-E computed column
     * A-L-E columns should not be included in the matrix
     * @param stockName The name of the stock to check
     * @return true if this is from an A-L-E column, false otherwise
     */
    private boolean isStockFromALEColumn(String stockName) {
        if (stockName == null || stockName.trim().isEmpty()) {
            return false;
        }
        
        // Get the master table for this stock
        Object masterTableObj = ComputedValues.getComputingTable(stockName.trim());
        
        if (masterTableObj instanceof TableElm) {
            TableElm masterTable = (TableElm) masterTableObj;
            
            // Find the column index in the master table
            int colIndex = masterTable.findColumnByStockName(stockName.trim());
            
            if (colIndex >= 0) {
                // Check if this column is an A-L-E column in the source table
                // A-L-E column is the last column when there are 4+ columns
                int masterCols = masterTable.getCols();
                return masterCols >= 4 && colIndex == (masterCols - 1);
            }
        }
        
        return false;
    }
    
    /**
     * Get the column type from the master table that owns this stock name
     * @param stockName The name of the stock
     * @return The ColumnType from the source table, or ASSET as default
     */
    private ColumnType getColumnTypeFromMasterTable(String stockName) {
        if (stockName == null || stockName.trim().isEmpty()) {
            return ColumnType.ASSET; // Default
        }
        
        // Get the master table for this stock
        Object masterTableObj = ComputedValues.getComputingTable(stockName.trim());
        
        if (masterTableObj instanceof TableElm) {
            TableElm masterTable = (TableElm) masterTableObj;
            
            // Find the column index in the master table
            int colIndex = masterTable.findColumnByStockName(stockName.trim());
            
            if (colIndex >= 0) {
                // Get the column type from that column
                return masterTable.getColumnType(colIndex);
            }
        }
        
        return ColumnType.ASSET; // Default if not found
    }
    
    /**
     * Update columns from registry when loading from file
     * Checks if the table is empty or needs updating
     */
    private void updateFromRegistry() {
        // If table is empty (no columns or all columns unnamed), populate from registry
        boolean isEmpty = true;
        if (outputNames != null && outputNames.length > 0) {
            for (String name : outputNames) {
                if (name != null && !name.trim().isEmpty()) {
                    isEmpty = false;
                    break;
                }
            }
        }
        
        if (isEmpty) {
            // Empty table: populate from all master stocks
            autoPopulateFromRegistry();
        } else {
            // Non-empty table: check registry for master stocks excluding this matrix
            String[] masterStocks = ComputedValues.getMasterStockNamesExcluding(MATRIX_TITLE);
            
            // If registry has different stocks than what we have, update
            if (shouldUpdateColumns(masterStocks)) {
                updateColumnsFromStocks(masterStocks);
            }
        }
    }
    
    /**
     * Check if we should update columns based on registry changes
     */
    private boolean shouldUpdateColumns(String[] registryStocks) {
        if (outputNames == null || registryStocks.length != outputNames.length) {
            return true;
        }
        
        // Check if all our columns exist in registry
        for (String ourName : outputNames) {
            if (ourName != null && !ourName.trim().isEmpty()) {
                boolean foundInRegistry = false;
                for (String regName : registryStocks) {
                    if (ourName.trim().equals(regName)) {
                        foundInRegistry = true;
                        break;
                    }
                }
                if (!foundInRegistry) {
                    return true; // Our column not in registry, needs update
                }
            }
        }
        
        return false;
    }
    
    /**
     * Update our columns to match the registry stocks
     */
    private void updateColumnsFromStocks(String[] stocks) {
        // Group stocks by source table for better organization
        ArrayList<StockInfo> stockInfoList = new ArrayList<StockInfo>();
        
        for (String stockName : stocks) {
            // Skip A-L-E computed columns - they are display-only, not real stocks
            if (isStockFromALEColumn(stockName)) {
                continue;
            }
            
            StockInfo info = new StockInfo();
            info.stockName = stockName;
            info.columnType = getColumnTypeFromMasterTable(stockName);
            info.sourceTableName = getSourceTableName(stockName);
            stockInfoList.add(info);
        }
        
        // Sort by source table name to group columns together
        sortBySourceTable(stockInfoList);
        
        cols = stockInfoList.size() + 1; // +1 for A-L-E column
        
        // Reallocate arrays
        String[] newOutputNames = new String[cols];
        ColumnType[] newColumnTypes = new ColumnType[cols];
        double[] newInitialValues = new double[cols];
        String[] newSourceTableNames = new String[cols];
        
        // Copy stock info
        for (int i = 0; i < stockInfoList.size(); i++) {
            StockInfo info = stockInfoList.get(i);
            newOutputNames[i] = info.stockName;
            newColumnTypes[i] = info.columnType;
            newInitialValues[i] = 0.0;
            newSourceTableNames[i] = info.sourceTableName;
        }
        
        // Add A-L-E computed column at the end
        int aleIndex = cols - 1;
        newOutputNames[aleIndex] = "A-L-E";
        newColumnTypes[aleIndex] = ColumnType.ASSET; // A-L-E doesn't have its own type
        newInitialValues[aleIndex] = 0.0;
        newSourceTableNames[aleIndex] = "";
        
        // Update arrays
        outputNames = newOutputNames;
        columnTypes = newColumnTypes;
        initialValues = newInitialValues;
        sourceTableNames = newSourceTableNames;
        
        // Populate flows (rows) when not in collapsed mode
        if (!collapsedMode) {
            populateFlowsFromMasterTables(stockInfoList);
        } else {
            rows = 0; // No data rows in collapsed mode
        }
        
        // Reinitialize cell arrays
        cellEquations = new String[rows][cols];
        compiledExpressions = new Expr[rows][cols];
        expressionStates = new ExprState[rows][cols];
        rowDescriptions = new String[rows];
        
        // Populate cell equations from master tables if we have rows
        if (rows > 0 && !collapsedMode) {
            populateCellEquationsFromMasters();
        }
    }
    
    @Override
    public EditInfo getEditInfo(int n) {
        // Override to prevent editing - this table auto-updates from registry
        if (n == 0) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.text = "This table automatically shows all master stocks.";
            ei.text += "\n\nMaster stocks are columns that compute values";
            ei.text += "\nfor use by other tables.";
            ei.text += "\n\nTo modify stocks, edit the source tables.";
            return ei;
        }
        return null;
    }
    
    @Override
    public void setEditValue(int n, EditInfo ei) {
        // No-op: prevent editing
    }
    
    @Override
    public String getChipName() {
        return "Current Transactions Matrix";
    }
    
    @Override
    public void getInfo(String arr[]) {
        arr[0] = "Current Transactions Matrix";
        arr[1] = "Displays all master stock values";
        arr[2] = "Columns: " + cols;
        
        // Show current master stocks
        if (outputNames != null) {
            for (int i = 0; i < Math.min(5, outputNames.length); i++) {
                String name = outputNames[i];
                if (name != null && !name.trim().isEmpty()) {
                    double value = getComputedValueForDisplay(i);
                    arr[3 + i] = name + " = " + CircuitElm.getUnitText(value, tableUnits);
                }
            }
            if (outputNames.length > 5) {
                arr[8] = "... and " + (outputNames.length - 5) + " more";
            }
        }
    }
    
    /**
     * Get source table names array
     * Package-private for access by CurrentTransactionsMatrixRenderer
     */
    String[] getSourceTableNames() {
        return sourceTableNames;
    }
    
    /**
     * Populate flows (rows) from all master tables
     * Collects all unique flow names from source tables
     */
    private void populateFlowsFromMasterTables(ArrayList<StockInfo> stockInfoList) {
        // Use ArrayList to collect all flow names
        ArrayList<String> flowNames = new ArrayList<String>();
        
        // Iterate through each master table and collect flow names
        for (StockInfo stockInfo : stockInfoList) {
            Object masterTableObj = ComputedValues.getComputingTable(stockInfo.stockName.trim());
            
            if (masterTableObj instanceof TableElm) {
                TableElm masterTable = (TableElm) masterTableObj;
                int masterRows = masterTable.getRows();
                
                // Collect all flow names from this master table
                for (int r = 0; r < masterRows; r++) {
                    String flowName = masterTable.getRowDescription(r);
                    
                    // Only add if not already in the list (avoid duplicates)
                    if (flowName != null && !flowName.trim().isEmpty() && !flowNames.contains(flowName)) {
                        flowNames.add(flowName);
                    }
                }
            }
        }
        
        // Set the number of rows
        rows = flowNames.size();
        
        // Initialize row descriptions from collected flow names
        rowDescriptions = new String[rows];
        for (int i = 0; i < rows; i++) {
            rowDescriptions[i] = flowNames.get(i);
        }
    }
    
    /**
     * Populate cell equations from master tables
     * Each cell references the corresponding flow row and stock column in the master table
     */
    private void populateCellEquationsFromMasters() {
        // For each cell, find the equation from the corresponding master table
        for (int row = 0; row < rows; row++) {
            String flowName = rowDescriptions[row];
            
            for (int col = 0; col < cols; col++) {
                String stockName = outputNames[col];
                
                // Get the master table for this stock
                Object masterTableObj = ComputedValues.getComputingTable(stockName.trim());
                
                if (masterTableObj instanceof TableElm) {
                    TableElm masterTable = (TableElm) masterTableObj;
                    
                    // Find the row in master table that matches this flow name
                    int masterRow = findRowByFlowName(masterTable, flowName);
                    
                    // Find the column in master table that matches this stock name
                    int masterCol = masterTable.findColumnByStockName(stockName.trim());
                    
                    if (masterRow >= 0 && masterCol >= 0) {
                        // Get the equation from master table
                        String equation = masterTable.getCellEquation(masterRow, masterCol);
                        cellEquations[row][col] = (equation != null) ? equation : "";
                    } else {
                        cellEquations[row][col] = ""; // Empty if not found
                    }
                } else {
                    cellEquations[row][col] = ""; // Empty if master table not found
                }
            }
        }
    }
    
    /**
     * Find the row index in a master table that matches a flow name
     */
    private int findRowByFlowName(TableElm masterTable, String flowName) {
        if (flowName == null || flowName.trim().isEmpty()) {
            return -1;
        }
        
        int masterRows = masterTable.getRows();
        for (int r = 0; r < masterRows; r++) {
            String masterFlowName = masterTable.getRowDescription(r);
            if (flowName.equals(masterFlowName)) {
                return r;
            }
        }
        
        return -1; // Not found
    }
    
    @Override
    void toggleCollapsedMode() {
        // Call parent to toggle the mode
        super.toggleCollapsedMode();
        
        // Re-populate flows when expanding (if we're now expanded)
        if (!collapsedMode) {
            // Recreate stock info list from current columns
            ArrayList<StockInfo> stockInfoList = new ArrayList<StockInfo>();
            
            for (int i = 0; i < cols && i < outputNames.length; i++) {
                if (outputNames[i] != null && !outputNames[i].trim().isEmpty()) {
                    StockInfo info = new StockInfo();
                    info.stockName = outputNames[i];
                    info.columnType = columnTypes[i];
                    info.sourceTableName = sourceTableNames[i];
                    stockInfoList.add(info);
                }
            }
            
            // Populate flows
            if (stockInfoList.size() > 0) {
                populateFlowsFromMasterTables(stockInfoList);
                
                // Reinitialize cell arrays with new row count
                // NOTE: Don't reinitialize rowDescriptions - it was set by populateFlowsFromMasterTables
                cellEquations = new String[rows][cols];
                compiledExpressions = new Expr[rows][cols];
                expressionStates = new ExprState[rows][cols];
                
                // Populate cell equations from masters
                populateCellEquationsFromMasters();
            }
        } else {
            // When collapsing, clear rows
            rows = 0;
            cellEquations = new String[rows][cols];
            compiledExpressions = new Expr[rows][cols];
            expressionStates = new ExprState[rows][cols];
            rowDescriptions = new String[rows];
        }
    }
    
    @Override
    void draw(Graphics g) {
        if (matrixRenderer != null) {
            matrixRenderer.draw(g);
        } else {
            super.draw(g); // Fallback to parent
        }
    }
    
    @Override
    public void reset() {
        // Refresh columns from registry on reset
        updateFromRegistry();
        
        // Re-setup pins after potential update
        setupPins();
        allocNodes();
        setPoints();
        
        // Call parent reset
        super.reset();
    }
    
    @Override
    void stamp() {
        // Call parent stamp - column updates happen in reset(), not during stamp
        // (stamp is called after node allocation, so structural changes would cause errors)
        super.stamp();
    }
    
    /**
     * Called during circuit analysis setup, before stamp()
     * This is the safe place to check for structural changes
     */
    @Override
    public void stepFinished() {
        // Check if master stocks have changed after this timestep
        // If so, trigger re-analysis on next step
        String[] currentMasterStocks = ComputedValues.getAllMasterStockNames();
        
        if (shouldUpdateColumns(currentMasterStocks)) {
            // Defer the update and trigger re-analysis
            sim.needAnalyze();
        }
        
        // Call parent
        super.stepFinished();
    }
}
