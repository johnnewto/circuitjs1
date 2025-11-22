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
    
    // Compact mode: filters to Asset/Equity only, hides type/header rows
    protected boolean compactMode = false;
    
    /**
     * Constructor for new matrix created from menu.
     * Initializes with default settings and auto-populates from registry.
     */
    public CurrentTransactionsMatrixElm(int xx, int yy) {
        super(xx, yy);
        initializeMatrix();
    }

    /**
     * Constructor for loading from file.
     * Parses saved state but discards column data to auto-populate from current registry.
     */
    public CurrentTransactionsMatrixElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        initializeMatrix();
    }
    
    @Override
    int getDumpType() { 
        return 254;
    }
    
    /**
     * Initialize matrix settings and populate from registry.
     * Consolidated initialization logic used by both constructors.
     */
    private void initializeMatrix() {
        tableTitle = MATRIX_TITLE;
        tableUnits = "";
        showInitialValues = true;
        priority = 1;
        compactMode = false;
        
        autoPopulateFromRegistry();
        matrixRenderer = new CurrentTransactionsMatrixRenderer(this);
        setupPins();
    }
    
    /**
     * Setup pins without registering as master.
     * CTM is a display element only - it monitors but doesn't control stocks.
     */
    @Override
    void setupPins() {
        int extraRows = compactMode ? 4 : (collapsedMode ? 3 : 5);
        int rowDescColWidth = compactMode ? (cellWidthInGrids * 2) : cellWidthInGrids;
        
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
     * Auto-populate columns from all master stocks in the registry.
     * In compact mode, shows one equity column per table.
     * In normal mode, shows all stock columns plus A-L-E.
     */
    private void autoPopulateFromRegistry() {
        String[] masterStocks = ComputedValues.getAllMasterStockNames();
        
        if (masterStocks.length == 0) {
            initializeEmptyTable();
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
            
            // In compact mode, consolidate to one column per source table
            if (compactMode) {
                stockInfoList = consolidateToEquityColumns(stockInfoList);
            }
            
            // Populate with grouped master stocks PLUS one extra column for A-L-E (unless compact mode)
            cols = compactMode ? stockInfoList.size() : (stockInfoList.size() + 1); // +1 for A-L-E in normal mode
            
            outputNames = new String[cols];
            columnTypes = new ColumnType[cols];
            initialValues = new double[cols];
            sourceTableNames = new String[cols];
            
            // Copy stock columns and populate initial values from master tables
            for (int i = 0; i < stockInfoList.size(); i++) {
                StockInfo info = stockInfoList.get(i);
                outputNames[i] = info.stockName;
                columnTypes[i] = info.columnType;
                
                // Get initial value from master table
                initialValues[i] = getInitialValueFromMaster(info.stockName);
                
                sourceTableNames[i] = info.sourceTableName;
            }
            
            // Add A-L-E computed column at the end (only in normal mode)
            if (!compactMode) {
                int aleIndex = cols - 1;
                outputNames[aleIndex] = "A-L-E";
                columnTypes[aleIndex] = ColumnType.ASSET; // A-L-E doesn't have its own type
                initialValues[aleIndex] = 0.0;
                sourceTableNames[aleIndex] = "";
            }
            
            // Populate flows (rows) when not in collapsed mode
            if (!collapsedMode) {
                populateFlowsAndEquations(stockInfoList);
            } else {
                rows = 0; // No data rows in collapsed mode
                // Initialize empty arrays
                cellEquations = new String[rows][cols];
                compiledExpressions = new Expr[rows][cols];
                expressionStates = new ExprState[rows][cols];
                rowDescriptions = new String[rows];
            }
        }
    }
    
    /**
     * Initialize empty table with default structure.
     */
    private void initializeEmptyTable() {
        cols = 4;
        rows = 0;
        outputNames = new String[cols];
        columnTypes = new ColumnType[cols];
        initialValues = new double[cols];
        sourceTableNames = new String[cols];
        
        for (int i = 0; i < cols; i++) {
            outputNames[i] = "";
            columnTypes[i] = ColumnType.ASSET;
            initialValues[i] = 0.0;
            sourceTableNames[i] = "";
        }
    }
    
    /**
     * Helper class to store stock information during population.
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
     * Consolidate stock list to one equity column per source table.
     * Used in compact mode to show only equity positions.
     */
    private ArrayList<StockInfo> consolidateToEquityColumns(ArrayList<StockInfo> stockInfoList) {
        ArrayList<StockInfo> consolidatedList = new ArrayList<StockInfo>();
        String lastTableName = "";
        
        for (StockInfo info : stockInfoList) {
            String tableName = (info.sourceTableName != null) ? info.sourceTableName : "";
            
            if (!tableName.equals(lastTableName)) {
                StockInfo equityStock = findEquityStockForTable(stockInfoList, tableName);
                if (equityStock != null) {
                    consolidatedList.add(equityStock);
                }
                lastTableName = tableName;
            }
        }
        
        return consolidatedList;
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
     * Find the EQUITY stock for a given table name in the stock info list
     * Used in compact mode to select the equity column as the representative for each table
     */
    private StockInfo findEquityStockForTable(ArrayList<StockInfo> stockInfoList, String tableName) {
        for (StockInfo info : stockInfoList) {
            String infoTableName = (info.sourceTableName != null) ? info.sourceTableName : "";
            if (infoTableName.equals(tableName) && info.columnType == ColumnType.EQUITY) {
                return info;
            }
        }
        // If no equity column found, return null (table will be skipped)
        return null;
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
        
        if (compactMode) {
            stockInfoList = consolidateToEquityColumns(stockInfoList);
        }
        
        cols = compactMode ? stockInfoList.size() : (stockInfoList.size() + 1); // +1 for A-L-E in normal mode
        
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
        
        // Add A-L-E computed column at the end (only in normal mode)
        if (!compactMode) {
            int aleIndex = cols - 1;
            newOutputNames[aleIndex] = "A-L-E";
            newColumnTypes[aleIndex] = ColumnType.ASSET; // A-L-E doesn't have its own type
            newInitialValues[aleIndex] = 0.0;
            newSourceTableNames[aleIndex] = "";
        }
        
        // Update arrays
        outputNames = newOutputNames;
        columnTypes = newColumnTypes;
        initialValues = newInitialValues;
        sourceTableNames = newSourceTableNames;
        
        // Populate flows (rows) when not in collapsed mode
        if (!collapsedMode) {
            populateFlowsAndEquations(stockInfoList);
        } else {
            rows = 0; // No data rows in collapsed mode
            initializeEmptyArrays();
        }
    }
    
    /**
     * Initialize empty arrays for collapsed mode
     */
    private void initializeEmptyArrays() {
        cellEquations = new String[rows][cols];
        compiledExpressions = new Expr[rows][cols];
        expressionStates = new ExprState[rows][cols];
        rowDescriptions = new String[rows];
    }
    
    /**
     * Populate flows (rows) and cell equations from master tables
     * Consolidates the flow population and equation setup logic
     */
    private void populateFlowsAndEquations(ArrayList<StockInfo> stockInfoList) {
        // Populate flows (rows)
        populateFlowsFromMasterTables(stockInfoList);
        
        // Initialize cell arrays
        cellEquations = new String[rows][cols];
        compiledExpressions = new Expr[rows][cols];
        expressionStates = new ExprState[rows][cols];
        
        // Populate cell equations if we have rows
        if (rows > 0) {
            populateCellEquationsFromMasters();
        }
    }
    
    /**
     * Build stock info list from current columns (excludes A-L-E)
     */
    private ArrayList<StockInfo> buildStockInfoListFromColumns() {
        ArrayList<StockInfo> stockInfoList = new ArrayList<StockInfo>();
        
        for (int i = 0; i < cols && i < outputNames.length; i++) {
            String stockName = outputNames[i];
            
            if (stockName != null && !stockName.trim().isEmpty() && !stockName.equals("A-L-E")) {
                StockInfo info = new StockInfo();
                info.stockName = stockName;
                info.columnType = (columnTypes != null && i < columnTypes.length) 
                    ? columnTypes[i] : ColumnType.ASSET;
                info.sourceTableName = (sourceTableNames != null && i < sourceTableNames.length) 
                    ? sourceTableNames[i] : "";
                if (info.sourceTableName == null) info.sourceTableName = "";
                stockInfoList.add(info);
            }
        }
        
        return stockInfoList;
    }
    
    @Override
    public EditInfo getEditInfo(int n) {
        // Allow toggling display options but not editing data
        if (n == 0) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.text = "This table automatically shows all master stocks.";
            ei.text += "\n\nMaster stocks are columns that compute values";
            ei.text += "\nfor use by other tables.";
            ei.text += "\n\nTo modify stocks, edit the source tables.";
            return ei;
        }
        if (n == 1) {
            EditInfo ei = new EditInfo("Show Initial Values", 0, -1, -1);
            ei.checkbox = new Checkbox("", showInitialValues);
            return ei;
        }
        if (n == 2) {
            EditInfo ei = new EditInfo("Collapsed Mode", 0, -1, -1);
            ei.checkbox = new Checkbox("", collapsedMode);
            return ei;
        }
        if (n == 3) {
            EditInfo ei = new EditInfo("Compact Mode (Asset/Equity only)", 0, -1, -1);
            ei.checkbox = new Checkbox("", compactMode);
            return ei;
        }
        return null;
    }
    
    @Override
    public void setEditValue(int n, EditInfo ei) {
        // Allow changing display options
        if (n == 1) {
            showInitialValues = ei.checkbox.getValue();
        } else if (n == 2) {
            boolean newCollapsedMode = ei.checkbox.getValue();
            if (newCollapsedMode != collapsedMode) {
                toggleCollapsedMode();
            }
        } else if (n == 3) {
            boolean newCompactMode = ei.checkbox.getValue();
            if (newCompactMode != compactMode) {
                compactMode = newCompactMode;
                // If enabling compact mode, ensure we're not in collapsed mode
                if (compactMode && collapsedMode) {
                    collapsedMode = false;
                }
                // Re-populate to apply filter
                autoPopulateFromRegistry();
            }
        }
        setupPins();
        setPoints();
    }
    
    @Override
    public String dump() {
        // Minimal dump: CircuitElm data + minimal TableElm data + collapsedMode
        // TableElm format: rows cols cellWidth cellHeight cellSpacing showInitialValues decimalPlaces showCellValues collapsedMode priority title units
        // We use minimal values since we auto-populate from registry
        int t = getDumpType();
        String circuitData = (t < 127 ? ((char)t)+" " : t+" ") + x + " " + y + " " + x2 + " " + y2 + " " + flags;
        
        
        // Minimal table data for parseTableData to consume
        // IMPORTANT: Must include empty units string to match TableDataManager.parseTableData() format
        String tableData = " 0 0 6 16 0 false 2 false " + collapsedMode + " " + priority + " " + MATRIX_TITLE + " \"\"" + " " + compactMode;
        
        return circuitData + tableData;
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
     * Check if compact mode is enabled
     * Package-private for access by CurrentTransactionsMatrixRenderer
     */
    boolean isCompactMode() {
        return compactMode;
    }
    
    /**
     * Get source table name for a specific column
     * Package-private for access by CurrentTransactionsMatrixRenderer
     */
    String getSourceTableName(int col) {
        if (sourceTableNames != null && col >= 0 && col < sourceTableNames.length) {
            return sourceTableNames[col];
        }
        return null;
    }
    
    /**
     * Get output name for a specific column
     * Package-private for access by CurrentTransactionsMatrixRenderer
     */
    String getOutputName(int col) {
        if (outputNames != null && col >= 0 && col < outputNames.length) {
            return outputNames[col];
        }
        return null;
    }
    
    /**
     * Get initial value for a stock from its master table
     */
    private double getInitialValueFromMaster(String stockName) {
        if (stockName == null || stockName.trim().isEmpty()) {
            return 0.0;
        }
        
        Object masterTableObj = ComputedValues.getComputingTable(stockName.trim());
        
        if (masterTableObj instanceof TableElm) {
            TableElm masterTable = (TableElm) masterTableObj;
            int colIndex = masterTable.findColumnByStockName(stockName.trim());
            
            if (colIndex >= 0) {
                return masterTable.getInitialValue(colIndex);
            }
        }
        
        return 0.0;
    }
    
    /**
     * Populate flows (rows) from all master tables.
     * Collects unique flow names from source tables.
     */
    private void populateFlowsFromMasterTables(ArrayList<StockInfo> stockInfoList) {
        ArrayList<String> flowNames = new ArrayList<String>();
        
        for (StockInfo stockInfo : stockInfoList) {
            Object masterTableObj = ComputedValues.getComputingTable(stockInfo.stockName.trim());
            
            if (masterTableObj instanceof TableElm) {
                TableElm masterTable = (TableElm) masterTableObj;
                int masterRows = masterTable.getRows();
                
                for (int r = 0; r < masterRows; r++) {
                    String flowName = masterTable.getRowDescription(r);
                    
                    if (flowName != null && !flowName.trim().isEmpty() && !flowNames.contains(flowName)) {
                        flowNames.add(flowName);
                    }
                }
            }
        }
        
        rows = flowNames.size();
        rowDescriptions = new String[rows];
        for (int i = 0; i < rows; i++) {
            rowDescriptions[i] = flowNames.get(i);
        }
    }
    
    /**
     * Populate cell equations from master tables.
     * In compact mode: copies equity column equations.
     * In normal mode: copies specific stock column equations.
     */
    private void populateCellEquationsFromMasters() {
        for (int row = 0; row < rows; row++) {
            String flowName = rowDescriptions[row];
            
            for (int col = 0; col < cols; col++) {
                String stockName = outputNames[col];
                
                if (stockName == null) {
                    cellEquations[row][col] = "";
                    continue;
                }
                
                Object masterTableObj = ComputedValues.getComputingTable(stockName.trim());
                
                if (masterTableObj instanceof TableElm) {
                    TableElm masterTable = (TableElm) masterTableObj;
                    int masterRow = findRowByFlowName(masterTable, flowName);
                    
                    if (compactMode && masterRow >= 0) {
                        cellEquations[row][col] = findEquityEquationInRow(masterTable, masterRow);
                    } else {
                        int masterCol = masterTable.findColumnByStockName(stockName.trim());
                        
                        if (masterRow >= 0 && masterCol >= 0) {
                            String equation = masterTable.getCellEquation(masterRow, masterCol);
                            cellEquations[row][col] = (equation != null) ? equation : "";
                        } else {
                            cellEquations[row][col] = "";
                        }
                    }
                } else {
                    cellEquations[row][col] = "";
                }
            }
        }
    }
    
    /**
     * Find the EQUITY column equation in a row
     * Used in compact mode to get the equity equation for a table
     */
    private String findEquityEquationInRow(TableElm masterTable, int row) {
        int masterCols = masterTable.getCols();
        
        for (int col = 0; col < masterCols; col++) {
            ColumnType colType = masterTable.getColumnType(col);
            
            // Only consider EQUITY columns
            if (colType == ColumnType.EQUITY) {
                String equation = masterTable.getCellEquation(row, col);
                // Return the equation even if blank/zero (equity might legitimately be empty for some flows)
                return (equation != null) ? equation : "";
            }
        }
        
        return ""; // No equity column found
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
        super.toggleCollapsedMode();
        
        if (!collapsedMode) {
            ArrayList<StockInfo> stockInfoList = buildStockInfoListFromColumns();
            
            if (stockInfoList.size() > 0) {
                populateFlowsAndEquations(stockInfoList);
            }
            
            validateArrays();
        } else {
            rows = 0;
            initializeEmptyArrays();
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
        updateFromRegistry();
        
        if (!collapsedMode) {
            ArrayList<StockInfo> stockInfoList = buildStockInfoListFromColumns();
            
            if (stockInfoList.size() > 0) {
                populateFlowsAndEquations(stockInfoList);
            }
        }
        
        validateArrays();
        setupPins();
        allocNodes();
        setPoints();
        super.reset();
    }
    
    /**
     * Validate and fix array consistency to prevent rendering errors.
     */
    private void validateArrays() {
        if (sourceTableNames == null || sourceTableNames.length != cols) {
            sourceTableNames = new String[cols];
            for (int i = 0; i < cols; i++) {
                sourceTableNames[i] = "";
            }
        } else {
            for (int i = 0; i < cols; i++) {
                if (sourceTableNames[i] == null) {
                    sourceTableNames[i] = "";
                }
            }
        }
        
        if (outputNames != null) {
            for (int i = 0; i < cols && i < outputNames.length; i++) {
                if (outputNames[i] == null) {
                    outputNames[i] = "";
                }
            }
        }
        
        if (columnTypes != null) {
            for (int i = 0; i < cols && i < columnTypes.length; i++) {
                if (columnTypes[i] == null) {
                    columnTypes[i] = ColumnType.ASSET;
                }
            }
        }
    }
    
    @Override
    void stamp() {
        // Call parent stamp - column updates happen in reset(), not during stamp
        // (stamp is called after node allocation, so structural changes would cause errors)
        super.stamp();
    }
    
    /**
     * Override getComputedValueForDisplay to return the equity value from each source table.
     * In compact mode, each column represents a whole table (showing its equity).
     * In normal mode, each column shows the specific stock's current value.
     * This includes the last row (computed row) as well.
     */
    @Override
    public double getComputedValueForDisplay(int col) {
        if (col < 0 || col >= cols || outputNames == null || col >= outputNames.length) {
            return 0.0;
        }
        
        String stockName = outputNames[col];
        
        // Handle A-L-E column (only in normal mode)
        if (stockName != null && stockName.equals("A-L-E")) {
            // For A-L-E in CTM, compute from other columns
            double assets = 0.0;
            double liabilities = 0.0;
            double equity = 0.0;
            
            for (int c = 0; c < cols; c++) {
                if (c == col) continue; // Skip A-L-E itself
                
                double value = getComputedValueForDisplay(c);
                
                if (columnTypes != null && c < columnTypes.length && columnTypes[c] != null) {
                    switch (columnTypes[c]) {
                        case ASSET:
                            assets += value;
                            break;
                        case LIABILITY:
                            liabilities += value;
                            break;
                        case EQUITY:
                            equity += value;
                            break;
                    }
                }
            }
            
            double result = assets - liabilities - equity;
            return result;
        }
        
        // Get the equity value from the source table
        if (stockName == null || stockName.trim().isEmpty()) {
            return 0.0;
        }
        
        Object masterTableObj = ComputedValues.getComputingTable(stockName.trim());
        
        if (masterTableObj instanceof TableElm) {
            TableElm masterTable = (TableElm) masterTableObj;
            
            if (compactMode) {
                // In compact mode, find the EQUITY column in the source table
                // The representative stock might be Asset or Equity type, but we want the Equity value
                int masterCols = masterTable.getCols();
                
                // Find the first EQUITY column in the source table
                for (int c = 0; c < masterCols; c++) {
                    ColumnType colType = masterTable.getColumnType(c);
                    
                    if (colType == ColumnType.EQUITY) {
                        double value = masterTable.getComputedValueForDisplay(c);
                        return value;
                    }
                }
                
                // If no EQUITY column found, return 0
                return 0.0;
            } else {
                // In normal mode, return the specific column's value from source table
                int colIndex = masterTable.findColumnByStockName(stockName.trim());
                
                if (colIndex >= 0) {
                    double value = masterTable.getComputedValueForDisplay(colIndex);
                    return value;
                }
            }
        }
        
        return 0.0;
    }
}


