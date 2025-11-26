/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.TableColumn.ColumnType;
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
    
    // Global recursion guard to prevent CTM-to-CTM infinite recursion
    private static boolean anyCtmComputing = false;
    
    // Custom renderer for this matrix
    private CurrentTransactionsMatrixRenderer matrixRenderer;
    
    // Track if this CTM has been fully initialized (avoid redundant initialization)
    private boolean initialized = false;
    
    // Custom stock names that can be edited by user (overrides registry auto-population)
    private String[] customStockNames = null;
    
    /**
     * Constructor for new matrix created from menu.
     * Initializes with default settings and auto-populates from registry.
     */
    public CurrentTransactionsMatrixElm(int xx, int yy) {
        super(xx, yy);
        CirSim.console("CTM: Constructor (new from menu)");
        showALE = true; // CTM shows A-L-E column
        initializeMatrix();
    }

    /**
     * Constructor for loading from file.
     * Parses saved state and re-initializes to match saved configuration.
     */
    public CurrentTransactionsMatrixElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        showALE = true; // CTM shows A-L-E column
        
        CirSim.console("CTM: Constructor from file. Initial columns count: " + (columns != null ? columns.size() : "null"));
        
        // Note: initMode is now parsed by TableElm's parseTableData() method
        
        // Parse custom stock names if available (user-set names that override auto-population)
        if (st.hasMoreTokens()) {
            try {
                String customStockNamesStr = st.nextToken();
                CirSim.console("  customStockNamesStr from file: '" + customStockNamesStr + "'");
                if (customStockNamesStr != null && !customStockNamesStr.trim().isEmpty() && 
                    !customStockNamesStr.equals("\"\"")) {
                    // Remove quotes and parse comma-separated names
                    customStockNamesStr = customStockNamesStr.replace("\"", "").trim();
                    if (!customStockNamesStr.isEmpty()) {
                        String[] names = customStockNamesStr.split(",");
                        customStockNames = new String[names.length];
                        for (int i = 0; i < names.length; i++) {
                            customStockNames[i] = names[i].trim();
                        }
                        CirSim.console("  Loaded " + customStockNames.length + " custom stock names from file");
                   }
                }
            } catch (Exception e) {
                customStockNames = null;
                CirSim.console("  Exception parsing custom stock names: " + e.getMessage());
            }
        }
        
        // Clear columns loaded by parent class - we'll regenerate based on current registry
        // Parent TableElm constructor already called parseTableData() which loaded all columns,
        // but for CTM we want to auto-populate based on equity filter or custom names
        
        CirSim.console("  Before filtering: customStockNames=" + (customStockNames != null ? customStockNames.length : "null") + 
                      ", columns=" + (columns != null ? columns.size() : "null"));
        
        // If customStockNames was not provided in the file, extract stock names from loaded columns
        // (excluding any A-L-E columns that shouldn't be there)
        if (customStockNames == null && columns != null && columns.size() > 0) {
            CirSim.console("CTM: Loading from file, filtering columns. Original count: " + columns.size());
            ArrayList<String> stockNamesList = new ArrayList<String>();
            for (TableColumn col : columns) {
                String stockName = col.getStockName();
                CirSim.console("  Checking column: '" + stockName + "'");
                boolean isALE = isStockFromALEColumn(stockName);
                CirSim.console("    isStockFromALEColumn: " + isALE);
                if (!isALE && stockName != null && !stockName.trim().isEmpty()) {
                    stockNamesList.add(stockName);
                    CirSim.console("    -> Added to list");
                } else {
                    CirSim.console("    -> Filtered out");
                }
            }
            if (stockNamesList.size() > 0) {
                customStockNames = new String[stockNamesList.size()];
                for (int i = 0; i < stockNamesList.size(); i++) {
                    customStockNames[i] = stockNamesList.get(i);
                }
                CirSim.console("  Filtered columns. New count: " + customStockNames.length);
            }
        }
        
        columns = null; // Clear and reinitialize from registry
        
        // Initialize the matrix based on loaded configuration
        // If customStockNames is set, it will use those; otherwise defaults to equity stocks
        initializeMatrix();
    }
    
    @Override
    int getDumpType() { 
        return 254;
    }
    
    /**
     * Initialize matrix settings and populate from registry.
     * Consolidated initialization logic used by both constructors.
     * Uses lazy initialization - skips if registry is empty (during menu creation).
     */
    private void initializeMatrix() {
        // Skip initialization if already done
        if (initialized) {
            return;
        }
        
        // Skip initialization if registry is empty (during menu creation)
        // The matrix will be initialized later when actually used
        String[] masterStocks = ComputedValues.getAllMasterStockNames();
        CirSim.console("CTM: initializeMatrix() - masterStocks count: " + 
                      (masterStocks != null ? masterStocks.length : "null"));
        if (masterStocks != null && masterStocks.length > 0) {
            CirSim.console("  Master stocks: " + String.join(", ", masterStocks));
        }
        
        if (masterStocks == null || masterStocks.length == 0) {
            // Set basic properties but don't populate columns yet
            tableTitle = MATRIX_TITLE;
            tableUnits = "";
            showInitialValues = true;
            priority = 1;
            return;
        }
        
        tableTitle = MATRIX_TITLE;
        tableUnits = "";
        showInitialValues = true;
        priority = 1;
        
        // Use custom stock names if set, otherwise default to equity stocks for new CTM
        if (customStockNames != null) {
            applyCustomStockNames();
        } else {
            initializeWithEquityStocksOnly();
        }
        
        matrixRenderer = new CurrentTransactionsMatrixRenderer(this);
        setupPins();
        allocNodes();
        setPoints();
        initialized = true;
    }
    
    /**
     * Setup pins without registering as master.
     * CTM is a display element only - it monitors but doesn't control stocks.
     */
    @Override
    void setupPins() {
        int extraRows = (collapsedMode ? 3 : 5);
        int rowDescColWidth = cellWidthInGrids;
        
        int tableWidthPixels = (rowDescColWidth + getCols() * cellWidthInGrids) * cspc + 2 * cspc;
        int tableHeightPixels = (rows + extraRows) * cellHeight + 
                               (rows + extraRows + 1) * cellSpacing + 20;
        
        sizeX = (tableWidthPixels + cspc2 - 1) / cspc2;
        sizeY = (tableHeightPixels + cspc2 - 1) / cspc2;
        
        // Create output pins
        pins = new Pin[getCols()];
        for (int i = 0; i < getCols(); i++) {
            pins[i] = new Pin(0, SIDE_S, "");
            pins[i].output = true;
        }
    }
    
    /**
     * Apply custom stock names set by user
     */
    private void applyCustomStockNames() {
        if (customStockNames == null || customStockNames.length == 0) return;
        
        CirSim.console("CTM: applyCustomStockNames() with " + customStockNames.length + " names");
        ArrayList<StockInfo> stockInfoList = new ArrayList<StockInfo>();
        
        for (String stockName : customStockNames) {
            CirSim.console("  Applying custom stock: '" + stockName + "'");
            if (stockName != null && !stockName.trim().isEmpty()) {
                // Filter out A-L-E columns
                if (isStockFromALEColumn(stockName)) {
                    CirSim.console("    -> Filtered out (A-L-E column)");
                    continue;
                }
                
                StockInfo info = new StockInfo();
                info.stockName = stockName;
                info.columnType = getColumnTypeFromMasterTable(stockName);
                info.sourceTableName = getSourceTableName(stockName);
                stockInfoList.add(info);
                CirSim.console("    -> Added");
            }
        }
        
        populateFromStockInfoList(stockInfoList);
    }
    
    /**
     * Initialize table with only equity stock names from the registry.
     * This is the default auto-population mode - does NOT set customStockNames.
     */
    private void initializeWithEquityStocksOnly() {
        ArrayList<StockInfo> stockInfoList = buildStockInfoList(stockName -> {
            ColumnType type = getColumnTypeFromMasterTable(stockName);
            boolean isEquity = type == ColumnType.EQUITY;
            return isEquity;
        });
        
        
        if (stockInfoList.isEmpty()) {
            // No equity stocks found, fall back to all stocks
            autoPopulateFromRegistry();
            return;
        }
        
        populateFromStockInfoList(stockInfoList);
    }
    
    /**
     * Auto-populate columns from all master stocks in the registry.
     */
    private void autoPopulateFromRegistry() {
        ArrayList<StockInfo> stockInfoList = buildStockInfoList(stockName -> true);
        
        if (stockInfoList.isEmpty()) {
            initializeEmptyTable();
        } else {
            populateFromStockInfoList(stockInfoList);
        }
    }
    
    /**
     * Build stock info list from registry with optional filter.
     * Skips A-L-E columns and applies provided filter.
     */
    private ArrayList<StockInfo> buildStockInfoList(StockFilter filter) {
        String[] masterStocks = ComputedValues.getAllMasterStockNames();
        ArrayList<StockInfo> stockInfoList = new ArrayList<StockInfo>();
        
        CirSim.console("CTM: buildStockInfoList() processing " + masterStocks.length + " master stocks");
        for (String stockName : masterStocks) {
            CirSim.console("  Checking stock: '" + stockName + "'");
            if (isStockFromALEColumn(stockName)) {
                CirSim.console("    -> Filtered out (A-L-E)");
                continue;
            }
            if (!filter.accept(stockName)) {
                CirSim.console("    -> Filtered out (by filter)");
                continue;
            }
            
            StockInfo info = new StockInfo();
            info.stockName = stockName;
            info.columnType = getColumnTypeFromMasterTable(stockName);
            info.sourceTableName = getSourceTableName(stockName);
            stockInfoList.add(info);
            CirSim.console("    -> Added to list");
        }
        
        CirSim.console("  Final list: " + stockInfoList.size() + " stocks");
        sortBySourceTable(stockInfoList);
        return stockInfoList;
    }
    
    /**
     * Populate table from stock info list
     */
    private void populateFromStockInfoList(ArrayList<StockInfo> stockInfoList) {
        columns = new ArrayList<TableColumn>();
        
        for (StockInfo info : stockInfoList) {
            double initialValue = getInitialValueFromMaster(info.stockName);
            columns.add(new TableColumn(info.stockName, info.columnType, initialValue, rows));
        }
        
        // Add A-L-E computed column if showALE is true
        if (showALE) {
            columns.add(new TableColumn("A-L-E", ColumnType.COMPUTED, 0.0, rows));
        }
        
        // Populate flows/equations when not collapsed
        if (!collapsedMode) {
            populateFlowsAndEquations(stockInfoList);
        } else {
            rows = 0;
            rowDescriptions = new String[rows];
        }
    }
    
    /**
     * Functional interface for stock filtering
     */
    private interface StockFilter {
        boolean accept(String stockName);
    }
    
    /**
     * Initialize empty table with default structure.
     */
    private void initializeEmptyTable() {
        rows = 0;
        columns = new ArrayList<TableColumn>();
        
        for (int i = 0; i < 4; i++) {
            columns.add(new TableColumn("", ColumnType.ASSET, 0.0, rows));
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
     */
    private void sortBySourceTable(ArrayList<StockInfo> list) {
        int n = list.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                if (compareStockInfo(list.get(j), list.get(j + 1)) > 0) {
                    StockInfo temp = list.get(j);
                    list.set(j, list.get(j + 1));
                    list.set(j + 1, temp);
                }
            }
        }
    }
    
    /**
     * Compare two StockInfo objects: first by source table, then by column type
     */
    private int compareStockInfo(StockInfo a, StockInfo b) {
        String nameA = (a.sourceTableName != null) ? a.sourceTableName : "";
        String nameB = (b.sourceTableName != null) ? b.sourceTableName : "";
        
        int tableCompare = nameA.compareTo(nameB);
        if (tableCompare != 0) return tableCompare;
        
        // Same table - compare by type order
        return Integer.compare(getTypeOrder(a.columnType), getTypeOrder(b.columnType));
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
        
        // Check if the stock name itself is "A-L-E" (case insensitive)
        String trimmedName = stockName.trim();
        if (trimmedName.equalsIgnoreCase("A-L-E")) {
            CirSim.console("    isStockFromALEColumn: '" + stockName + "' matches 'A-L-E' by name");
            return true;
        }
        
        // Get the master table for this stock
        Object masterTableObj = ComputedValues.getComputingTable(trimmedName);
        
        if (masterTableObj instanceof TableElm) {
            TableElm masterTable = (TableElm) masterTableObj;
            
            // Find the column index in the master table
            int colIndex = masterTable.findColumnByStockName(trimmedName);
            
            if (colIndex >= 0) {
                // Check if this column is an A-L-E column in the source table
                // A-L-E column is the last column when there are 4+ columns
                int masterCols = masterTable.getCols();
                boolean isLastCol = masterCols >= 4 && colIndex == (masterCols - 1);
                if (isLastCol) {
                    CirSim.console("    isStockFromALEColumn: '" + stockName + "' is A-L-E column in master table");
                }
                return isLastCol;
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
     * Check if the table is empty (no columns or all columns unnamed)
     */
    private boolean isTableEmpty() {
        if (columns == null || columns.size() == 0) {
            return true;
        }
        
        for (TableColumn col : columns) {
            if (col.getStockName() != null && !col.getStockName().trim().isEmpty()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Update columns from registry when loading from file or after reset.
     * Respects initMode setting: 0=Equity only, 1=All stocks, 2=Custom names.
     */
    private void updateFromRegistry() {
        // Custom mode doesn't auto-update from registry
        if (initMode == 2) {
            return;
        }
        
        if (isTableEmpty()) {
            // Empty table: populate based on initMode
            if (initMode == 1) {
                autoPopulateFromRegistry();
            } else {
                // Default to equity-only (initMode == 0)
                initializeWithEquityStocksOnly();
            }
        } else {
            // Non-empty table: check if registry has changed
            String[] masterStocks = ComputedValues.getMasterStockNamesExcluding(MATRIX_TITLE);
            
            if (shouldUpdateColumns(masterStocks)) {
                updateColumnsFromStocks(masterStocks);
            }
        }
    }
    
    /**
     * Check if we should update columns based on registry changes
     */
    private boolean shouldUpdateColumns(String[] registryStocks) {
        if (columns == null || registryStocks.length != columns.size()) {
            return true;
        }
        
        // Check if all our columns exist in registry
        for (TableColumn col : columns) {
            String ourName = col.getStockName();
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
     * Update our columns to match the registry stocks.
     * Respects initMode: 0=equity filter, 1=no filter, 2=custom (shouldn't be called).
     */
    private void updateColumnsFromStocks(String[] stocks) {
        ArrayList<StockInfo> stockInfoList = new ArrayList<StockInfo>();
        
        for (String stockName : stocks) {
            if (isStockFromALEColumn(stockName)) {
                continue;
            }
            
            StockInfo info = new StockInfo();
            info.stockName = stockName;
            info.columnType = getColumnTypeFromMasterTable(stockName);
            info.sourceTableName = getSourceTableName(stockName);
            
            // Apply filter based on initMode
            if (initMode == 0) {
                // Equity-only mode: filter non-equity stocks
                if (info.columnType != ColumnType.EQUITY) {
                    continue;
                }
            }
            // initMode == 1: no filter, include all stocks
            
            stockInfoList.add(info);
        }
        
       sortBySourceTable(stockInfoList);
        populateFromStockInfoList(stockInfoList);
    }
    
    
    /**
     * Populate flows (rows) and cell equations from master tables
     * Consolidates the flow population and equation setup logic
     */
    private void populateFlowsAndEquations(ArrayList<StockInfo> stockInfoList) {
        // Populate flows (rows)
        populateFlowsFromMasterTables(stockInfoList);
        
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
        
        for (int i = 0; i < columns.size(); i++) {
            TableColumn column = columns.get(i);
            String stockName = column.getStockName();
            
            if (stockName != null && !stockName.trim().isEmpty() && !column.isALE()) {
                StockInfo info = new StockInfo();
                info.stockName = stockName;
                info.columnType = column.getType();
                info.sourceTableName = getSourceTableName(stockName);
                if (info.sourceTableName == null) info.sourceTableName = "";
                stockInfoList.add(info);
            }
        }
        
        return stockInfoList;
    }
    
    @Override
    public EditInfo getEditInfo(int n) {
        // Info message about auto-population
        if (n == 0) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.text = "This table automatically shows all master stocks.";
            ei.text += "\n\nMaster stocks are columns that compute values";
            ei.text += "\nfor use by other tables.";
            ei.text += "\n\nTo modify stocks, edit the source tables.";
            return ei;
        }
        // Table Title (read-only display, but included for consistency)
        if (n == 1) return new EditInfo("Table Title", tableTitle);
        // Priority
        if (n == 2) {
            EditInfo ei = new EditInfo("Priority (1-9, higher=master)", "");
            ei.choice = new Choice();
            for (int i = 1; i <= 9; i++) {
                ei.choice.add(String.valueOf(i));
            }
            // Clamp priority to 1-9 range
            int clampedPriority = Math.max(1, Math.min(9, priority));
            ei.choice.select(clampedPriority - 1);
            return ei;
        }
        // Cell Width
        if (n == 3) return new EditInfo("Cell Width (grids)", cellWidthInGrids, 1, 20);
        // Cell Height
        if (n == 4) return new EditInfo("Cell Height", cellHeight, 16, 48);
        // Cell Spacing
        if (n == 5) return new EditInfo("Cell Spacing", cellSpacing, 0, 5);
        // Show Initial Values
        if (n == 6) {
            EditInfo ei = new EditInfo("Show Initial Values", 0, -1, -1);
            ei.checkbox = new Checkbox("", showInitialValues);
            return ei;
        }
        // Show Cell Values
        if (n == 7) {
            EditInfo ei = new EditInfo("Cell Display Mode", "");
            ei.choice = new Choice();
            ei.choice.add("Equation");
            ei.choice.add("Equation: Value");
            ei.choice.add("Value");
            ei.choice.select(showCellValues); // 0, 1, or 2
            return ei;
        }
        // Collapsed Mode
        if (n == 8) {
            EditInfo ei = new EditInfo("Collapsed Mode", 0, -1, -1);
            ei.checkbox = new Checkbox("", collapsedMode);
            return ei;
        }
        // Custom Stock Names (CTM-specific)
        if (n == 9) {
            String stockNamesStr = "";
            if (customStockNames != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < customStockNames.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(customStockNames[i] != null ? customStockNames[i] : "");
                }
                stockNamesStr = sb.toString();
            } else {
                // Show current auto-populated stock names
                if (columns != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < columns.size(); i++) {
                        if (i > 0) sb.append(", ");
                        String name = columns.get(i).getStockName();
                        sb.append(name != null ? name : "");
                    }
                    stockNamesStr = sb.toString();
                }
            }
            EditInfo ei = new EditInfo("Custom Stock Names (comma-separated, leave blank for auto)", stockNamesStr);
            return ei;
        }
        // Table Initialization Mode (CTM-specific)
        if (n == 10) {
            EditInfo ei = new EditInfo("Initialize Table With", "");
            ei.choice = new Choice();
            ei.choice.add("Equity Stock Names Only");
            ei.choice.add("All Master Stock Names");
            ei.choice.add("Current Custom Stock Names");
            ei.choice.select(initMode); // Use saved mode
            return ei;
        }
        return null;
    }
    
    @Override
    public void setEditValue(int n, EditInfo ei) {
        // n=0 is info message, skip
        if (n == 1) {
            // Table Title (allow changes even though it's auto-populated)
            tableTitle = ei.textf.getText();
        } else if (n == 2) {
            // Priority
            int oldPriority = priority;
            priority = ei.choice.getSelectedIndex() + 1; // Convert from 0-indexed to 1-9
            // If priority changed, clear master tables and computed values
            if (oldPriority != priority) {
                ComputedValues.clearMasterTables();
                ComputedValues.clearComputedValues();
                sim.needAnalyze();
            }
        } else if (n == 3) {
            // Cell Width
            cellWidthInGrids = Math.max(1, (int)ei.value);
        } else if (n == 4) {
            // Cell Height
            cellHeight = (int)ei.value;
        } else if (n == 5) {
            // Cell Spacing
            cellSpacing = (int)ei.value;
        } else if (n == 6) {
            // Show Initial Values
            showInitialValues = ei.checkbox.getValue();
        } else if (n == 7) {
            // Cell Display Mode
            showCellValues = ei.choice.getSelectedIndex(); // 0, 1, or 2
        } else if (n == 8) {
            // Collapsed Mode
            boolean newCollapsedMode = ei.checkbox.getValue();
            if (newCollapsedMode != collapsedMode) {
                toggleCollapsedMode();
            }
        } else if (n == 9) {
            // Custom Stock Names
            String stockNamesInput = ei.textf.getText().trim();
            
            if (stockNamesInput.isEmpty()) {
                // Clear custom names - initMode stays as is (user can change it separately)
                customStockNames = null;
                // Re-populate based on current initMode
                if (initMode == 1) {
                    autoPopulateFromRegistry();
                } else {
                    initializeWithEquityStocksOnly();
                }
            } else {
                // Parse comma-separated stock names
                String[] inputNames = stockNamesInput.split(",");
                customStockNames = new String[inputNames.length];
                
                for (int i = 0; i < inputNames.length; i++) {
                    customStockNames[i] = inputNames[i].trim();
                }
                
                // Mark that custom stock names are now set
                initMode = 2; // Set to Custom mode
                
                // Apply custom stock names
                applyCustomStockNames();
            }
        } else if (n == 10) {
            // Table Initialization Mode
            int newInitMode = ei.choice.getSelectedIndex();
            initMode = newInitMode; // Save the selected mode
            
            // Order: Equity=0, All=1, Custom=2
            if (newInitMode == 0) {
                // Equity Stock Names Only
                customStockNames = null;
                initializeWithEquityStocksOnly();
            } else if (newInitMode == 1) {
                // All Master Stock Names
                customStockNames = null;
                autoPopulateFromRegistry();
            } else if (newInitMode == 2) {
                // Current Custom Stock Names
                if (customStockNames != null) {
                    applyCustomStockNames();
                }
            }
        }
        setupPins();
        setPoints();
        sim.analyzeFlag = true;
    }
    
    @Override
    public String dump() {
        // CTM dump: CircuitElm data + minimal TableElm data + CTM-specific flags
        // TableElm format: rows getCols() cellWidth cellHeight cellSpacing showInitialValues decimalPlaces showCellValues collapsedMode priority title units
        // We use minimal values since we auto-populate from registry
        int t = getDumpType();
        String circuitData = (t < 127 ? ((char)t)+" " : t+" ") + x + " " + y + " " + x2 + " " + y2 + " " + flags;
        
        // Build custom stock names string (only if user explicitly set them)
        String customStockNamesStr = "";
        if (customStockNames != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < customStockNames.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(customStockNames[i] != null ? customStockNames[i] : "");
            }
            customStockNamesStr = sb.toString();
        }
        
        // Format: minimal_table_data "customStockNames"
        // Format: rows cols cellWidth cellHeight cellSpacing showInitialValues decimalPlaces showCellValues collapsedMode priority initMode title units customStockNames
        String tableData = " 0 0 6 16 0 false 2 " + showCellValues + " " + collapsedMode + " " + priority + " " + initMode + " " + CustomLogicModel.escape(MATRIX_TITLE) + " \"\" \"" + customStockNamesStr + "\"";
        
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
        arr[2] = "Columns: " + getCols();
        
        // Show current master stocks
        if (columns != null) {
            for (int i = 0; i < Math.min(5, columns.size()); i++) {
                String name = columns.get(i).getStockName();
                if (name != null && !name.trim().isEmpty()) {
                    double value = getComputedValueForDisplay(i);
                    arr[3 + i] = name + " = " + CircuitElm.getUnitText(value, tableUnits);
                }
            }
            if (columns.size() > 5) {
                arr[8] = "... and " + (columns.size() - 5) + " more";
            }
        }
    }
    
    /**
     * Get source table name for a specific column by querying the registry
     * Package-private for access by CurrentTransactionsMatrixRenderer
     */
    String getSourceTableName(int col) {
        if (col < 0 || col >= getCols() || columns == null || col >= columns.size()) {
            return "Unknown";
        }
        
        String stockName = columns.get(col).getStockName();
        if (stockName == null || stockName.trim().isEmpty()) {
            return "Unknown";
        }
        
        // Get the master table for this stock from registry
        Object masterTableObj = ComputedValues.getComputingTable(stockName.trim());
        if (masterTableObj instanceof TableElm) {
            TableElm masterTable = (TableElm) masterTableObj;
            return masterTable.getTableTitle();
        }
        
        return "Unknown";
    }
    
    /**
     * Get output name for a specific column
     * Package-private for access by CurrentTransactionsMatrixRenderer
     */
    String getOutputName(int col) {
        if (columns != null && col >= 0 && col < columns.size()) {
            return columns.get(col).getStockName();
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
        // CirSim.console("CTM: Populating cell equations for " + rows + " rows, " + getCols() + " columns");
        
        for (int row = 0; row < rows; row++) {
            String flowName = rowDescriptions[row];
            // CirSim.console("CTM: Row " + row + " flowName='" + flowName + "'");
            
            for (int col = 0; col < getCols(); col++) {
                TableColumn column = columns.get(col);
                String stockName = column.getStockName();
                
                // Skip A-L-E computed columns - they don't have equations
                if (column.isALE()) {
                    // CirSim.console("  Col " + col + " stock='" + stockName + "' -> Skipping A-L-E (computed)");
                    continue;
                }
                
                if (stockName == null) {
                    columns.get(col).setCellEquation(row, "");
                    continue;
                }
                
                Object masterTableObj = ComputedValues.getComputingTable(stockName.trim());
                
                if (masterTableObj instanceof TableElm) {
                    TableElm masterTable = (TableElm) masterTableObj;
                    int masterRow = findRowByFlowName(masterTable, flowName);
                    int masterCol = masterTable.findColumnByStockName(stockName.trim());
                    
                    // CirSim.console("  Col " + col + " stock='" + stockName + "' masterTable='" + masterTable.getTableTitle() + 
                                //  "' masterRow=" + masterRow + " masterCol=" + masterCol);
                    
                    if (masterRow >= 0 && masterCol >= 0) {
                        String equation = masterTable.getCellEquation(masterRow, masterCol);
                        CirSim.console("    -> Setting equation: '" + equation + "'");
                        columns.get(col).setCellEquation(row, (equation != null) ? equation : "");
                    } else {
                        // CirSim.console("    -> Setting empty (row or col not found)");
                        columns.get(col).setCellEquation(row, "");
                    }
                } else {
                    columns.get(col).setCellEquation(row, "");
                }
            }
        }
    }
    
    /**
     * Find the row index in a master table that matches a flow name
     * Package-private for access by CurrentTransactionsMatrixRenderer
     */
    int findRowByFlowName(TableElm masterTable, String flowName) {
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
        } else {
            rows = 0;
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
        // If not yet initialized (menu instance), try to initialize now
        if (!initialized) {
            initializeMatrix();
        }
        
        // For CTMs with custom stock names, always preserve them
        if (customStockNames != null) {
            applyCustomStockNames();
        } else {
            // Use existing preservation logic for auto-populated CTMs
            // Only update from registry if not in custom mode
            if (initMode != 2) {
                updateFromRegistry();
            }
        }
        
        if (!collapsedMode) {
            ArrayList<StockInfo> stockInfoList = buildStockInfoListFromColumns();
            
            if (stockInfoList.size() > 0) {
                populateFlowsAndEquations(stockInfoList);
            }
        }
        
        setupPins();
        allocNodes();
        setPoints();
        
        // Preserve initMode across super.reset() call
        int savedInitMode = initMode;
        super.reset();
        if (initMode != savedInitMode) {
            initMode = savedInitMode;
        }
    }

    @Override
    public void doStep() {
        // CirSim.console("[doStep] This method should be overridden by subclasses (e.g., GodlyTableElm)");    
    }
    @Override
    public void stepFinished() {
	    // This method should be overridden by subclasses (e.g., GodlyTableElm)
        // CirSim.console("[stepFinished] This method should be overridden by subclasses (e.g., GodlyTableElm)");   
    }
    
    @Override
    void stamp() {
        // Call parent stamp - column updates happen in reset(), not during stamp
        // (stamp is called after node allocation, so structural changes would cause errors)
        super.stamp();
    }
    
    /**
     * Override getComputedValueForDisplay to return values from source tables.
     * Handles A-L-E column computation and prevents infinite recursion between CTMs.
     */
    @Override
    public double getComputedValueForDisplay(int col) {
        if (col < 0 || col >= getCols() || columns == null || col >= columns.size()) {
            return 0.0;
        }
        
        // Prevent infinite recursion between CTMs
        if (anyCtmComputing) return 0.0;
        
        anyCtmComputing = true;
        try {
            return computeValueSafely(col);
        } finally {
            anyCtmComputing = false;
        }
    }
    
    /**
     * Safely compute value for a column
     */
    private double computeValueSafely(int col) {
        String stockName = columns.get(col).getStockName();

        // CTM does not have A-L-E column, all columns are from source tables
        return getValueFromSourceTable(stockName);
    }
    
    /**
     * Get value from the source table for a stock
     */
    private double getValueFromSourceTable(String stockName) {
        if (stockName == null || stockName.trim().isEmpty()) return 0.0;

        Object masterTableObj = ComputedValues.getComputingTable(stockName.trim());
        if (!(masterTableObj instanceof TableElm)) return 0.0;

        TableElm masterTable = (TableElm) masterTableObj;
        int colIndex = masterTable.findColumnByStockName(stockName.trim());
        
        return (colIndex >= 0) ? masterTable.getComputedValueForDisplay(colIndex) : 0.0;
    }
    
    /**
     * Update custom stock names from current columns.
     * Called when columns are manually edited in the table editor.
     * Package-private for access by TableEditDialog.
     */
    void updateCustomStockNamesFromColumns() {
        if (columns == null || columns.size() == 0) {
            customStockNames = null;
            return;
        }
        
        // Extract stock names from current columns (excluding A-L-E if present)
        ArrayList<String> names = new ArrayList<String>();
        for (int i = 0; i < columns.size(); i++) {
            TableColumn col = columns.get(i);
            if (!col.isALE()) {
                String stockName = col.getStockName();
                if (stockName != null && !stockName.trim().isEmpty()) {
                    names.add(stockName.trim());
                }
            }
        }
        
        if (names.size() > 0) {
            customStockNames = new String[names.size()];
            for (int i = 0; i < names.size(); i++) {
                customStockNames[i] = names.get(i);
            }
        } else {
            customStockNames = null;
        }
    }
}


