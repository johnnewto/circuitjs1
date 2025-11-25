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
    
    // Global recursion guard to prevent CTM-to-CTM infinite recursion
    private static boolean anyCtmComputing = false;
    
    // Custom renderer for this matrix
    private CurrentTransactionsMatrixRenderer matrixRenderer;
    
    // Track if this CTM has been manually edited (to preserve column order on reset)
    private boolean hasBeenManuallyEdited = false;
    
    // Custom stock names that can be edited by user (overrides registry auto-population)
    private String[] customStockNames = null;
    
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
     * Parses saved state and re-initializes to match saved configuration.
     */
    public CurrentTransactionsMatrixElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        
        // Parse hasBeenManuallyEdited flag if available
        if (st.hasMoreTokens()) {
            try {
                String editedToken = st.nextToken();
                // Handle backward compatibility - old files might have column data here
                if (editedToken.equals("true") || editedToken.equals("false")) {
                    hasBeenManuallyEdited = Boolean.parseBoolean(editedToken);
                } else {
                    // Old format - this token is column data, ignore it
                    hasBeenManuallyEdited = false;
                }
            } catch (Exception e) {
                hasBeenManuallyEdited = false;
            }
        }
        
        // Parse custom stock names if available (user-set names that override auto-population)
        if (st.hasMoreTokens()) {
            try {
                String customStockNamesStr = st.nextToken();
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
                    }
                }
            } catch (Exception e) {
                customStockNames = null;
            }
        }
        
        // Parse current column names (saved for reference, but ignored unless customStockNames is set)
        // This token exists but we skip it - we'll auto-populate from current registry state
        if (st.hasMoreTokens()) {
            st.nextToken(); // Skip the saved column names - we regenerate from registry
        }
        
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
     */
    private void initializeMatrix() {
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
        if (customStockNames == null || customStockNames.length == 0) {
            return;
        }
        
        // Build columns directly from custom stock names
        columns = new ArrayList<TableColumn>();
        
        for (int i = 0; i < customStockNames.length; i++) {
            String stockName = customStockNames[i];
            ColumnType type = ColumnType.ASSET;
            double initialValue = 0.0;
            
            if (stockName != null && !stockName.trim().isEmpty()) {
                // Get column type and initial value from master table
                type = getColumnTypeFromMasterTable(stockName);
                initialValue = getInitialValueFromMaster(stockName);
            }
            
            columns.add(new TableColumn(stockName != null ? stockName : "", type, initialValue, rows));
        }
        
        // Add A-L-E computed column at the end
        columns.add(TableColumn.createALE(rows));
        
        // Populate flows (rows) when not in collapsed mode
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
    
    /**
     * Initialize table with only equity stock names from the registry.
     * This is the default auto-population mode - does NOT set customStockNames.
     */
    private void initializeWithEquityStocksOnly() {
        String[] masterStocks = ComputedValues.getAllMasterStockNames();
        
        if (masterStocks.length == 0) {
            initializeEmptyTable();
            return;
        }
        
        // Group stocks by source table for better organization
        ArrayList<StockInfo> stockInfoList = new ArrayList<StockInfo>();
        
        for (String stockName : masterStocks) {
            // Skip A-L-E computed columns - they are display-only, not real stocks
            if (isStockFromALEColumn(stockName)) {
                continue;
            }
            
            // Check if this stock is of EQUITY type
            ColumnType columnType = getColumnTypeFromMasterTable(stockName);
            if (columnType == ColumnType.EQUITY) {
                StockInfo info = new StockInfo();
                info.stockName = stockName;
                info.columnType = columnType;
                info.sourceTableName = getSourceTableName(stockName);
                stockInfoList.add(info);
            }
        }
        
        if (stockInfoList.size() == 0) {
            // No equity stocks found, fall back to all stocks
            autoPopulateFromRegistry();
            return;
        }
        
        // Sort by source table name to group columns together
        sortBySourceTable(stockInfoList);
        
        // Populate with equity stocks PLUS one extra column for A-L-E
        columns = new ArrayList<TableColumn>();
        
        // Copy stock columns and populate initial values from master tables
        for (int i = 0; i < stockInfoList.size(); i++) {
            StockInfo info = stockInfoList.get(i);
            double initialValue = getInitialValueFromMaster(info.stockName);
            columns.add(new TableColumn(info.stockName, info.columnType, initialValue, rows));
        }
        
        // Add A-L-E computed column at the end
        columns.add(TableColumn.createALE(rows));
        
        // Populate flows (rows) when not in collapsed mode
        if (!collapsedMode) {
            populateFlowsAndEquations(stockInfoList);
        } else {
            rows = 0; // No data rows in collapsed mode
            rowDescriptions = new String[rows];
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
            
            // Populate with grouped master stocks PLUS one extra column for A-L-E
            columns = new ArrayList<TableColumn>();
            
            // Copy stock columns and populate initial values from master tables
            for (int i = 0; i < stockInfoList.size(); i++) {
                StockInfo info = stockInfoList.get(i);
                double initialValue = getInitialValueFromMaster(info.stockName);
                columns.add(new TableColumn(info.stockName, info.columnType, initialValue, rows));
            }
            
            // Add A-L-E computed column at the end
            columns.add(TableColumn.createALE(rows));
            
            // Populate flows (rows) when not in collapsed mode
            if (!collapsedMode) {
                populateFlowsAndEquations(stockInfoList);
            } else {
                rows = 0; // No data rows in collapsed mode
                rowDescriptions = new String[rows];
            }
        }
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
        if (columns != null && columns.size() > 0) {
            for (TableColumn col : columns) {
                if (col.getStockName() != null && !col.getStockName().trim().isEmpty()) {
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
        
        // Build columns directly
        columns = new ArrayList<TableColumn>();
        
        for (int i = 0; i < stockInfoList.size(); i++) {
            StockInfo info = stockInfoList.get(i);
            columns.add(new TableColumn(info.stockName, info.columnType, 0.0, rows));
        }
        
        // Add A-L-E computed column at the end
        columns.add(TableColumn.createALE(rows));
        
        // Populate flows (rows) when not in collapsed mode
        if (!collapsedMode) {
            populateFlowsAndEquations(stockInfoList);
        } else {
            rows = 0; // No data rows in collapsed mode
            rowDescriptions = new String[rows];
        }
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
            ei.choice.select(0); // Default to equity stock names
            return ei;
        }
        return null;
    }
    
    @Override
    public void setEditValue(int n, EditInfo ei) {
        // Remember the edit state before making changes
        boolean wasManuallyEditedBefore = hasBeenManuallyEdited;
        
        // Mark as manually edited when any property is changed
        hasBeenManuallyEdited = true;
        
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
                // Clear custom names, revert to auto-population
                customStockNames = null;
                hasBeenManuallyEdited = false; // Allow auto-population to resume
                autoPopulateFromRegistry();
            } else {
                // Parse comma-separated stock names
                String[] inputNames = stockNamesInput.split(",");
                customStockNames = new String[inputNames.length];
                
                for (int i = 0; i < inputNames.length; i++) {
                    customStockNames[i] = inputNames[i].trim();
                }
                
                // Apply custom stock names
                applyCustomStockNames();
            }
        } else if (n == 10) {
            // Table Initialization Mode
            int initMode = ei.choice.getSelectedIndex();
            
            if (initMode == 0) {
                // Equity Stock Names Only (default auto-population)
                customStockNames = null;
                hasBeenManuallyEdited = false;
                initializeWithEquityStocksOnly();
            } else if (initMode == 1) {
                // All Master Stock Names
                customStockNames = null;
                hasBeenManuallyEdited = false;
                autoPopulateFromRegistry();
            } else if (initMode == 2) {
                // Current Custom Stock Names (keep as is)
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
        // CTM dump: CircuitElm data + minimal TableElm data + CTM-specific flags + current column names
        // TableElm format: rows getCols() cellWidth cellHeight cellSpacing showInitialValues decimalPlaces showCellValues collapsedMode priority title units
        // We use minimal values since we auto-populate from registry
        int t = getDumpType();
        String circuitData = (t < 127 ? ((char)t)+" " : t+" ") + x + " " + y + " " + x2 + " " + y2 + " " + flags;
        
        // Build current column names string (always save what's currently displayed)
        String currentColumnsStr = "";
        if (columns != null && columns.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sb.append(",");
                String name = columns.get(i).getStockName();
                sb.append(name != null ? name : "");
            }
            currentColumnsStr = sb.toString();
        }
        
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
        
        // Format: minimal_table_data hasBeenManuallyEdited "customStockNames" "currentColumns"
        String tableData = " 0 0 6 16 0 false 2 false " + collapsedMode + " " + priority + " " + MATRIX_TITLE + " \"\" " + hasBeenManuallyEdited + " \"" + customStockNamesStr + "\" \"" + currentColumnsStr + "\"";
        
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
        
        // A-L-E column doesn't have a source table
        if ("A-L-E".equals(stockName)) {
            return "";
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
        for (int row = 0; row < rows; row++) {
            String flowName = rowDescriptions[row];
            
            for (int col = 0; col < getCols(); col++) {
                String stockName = columns.get(col).getStockName();
                
                if (stockName == null) {
                    columns.get(col).setCellEquation(row, "");
                    continue;
                }
                
                Object masterTableObj = ComputedValues.getComputingTable(stockName.trim());
                
                if (masterTableObj instanceof TableElm) {
                    TableElm masterTable = (TableElm) masterTableObj;
                    int masterRow = findRowByFlowName(masterTable, flowName);
                    int masterCol = masterTable.findColumnByStockName(stockName.trim());
                    
                    if (masterRow >= 0 && masterCol >= 0) {
                        String equation = masterTable.getCellEquation(masterRow, masterCol);
                        columns.get(col).setCellEquation(row, (equation != null) ? equation : "");
                    } else {
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
        // For CTMs with custom stock names, always preserve them
        if (customStockNames != null) {
            applyCustomStockNames();
        } else {
            // Use existing preservation logic for auto-populated CTMs
            // Preserve column order for manually edited CTMs
            ArrayList<TableColumn> preservedColumns = null;
            
            if (hasBeenManuallyEdited && columns != null) {
                // Make a deep copy of columns to preserve order and data
                preservedColumns = new ArrayList<TableColumn>();
                for (TableColumn col : columns) {
                    preservedColumns.add(new TableColumn(col.getStockName(), col.getType(), col.getInitialValue(), rows));
                }
            }
            
            // Only update from registry if this CTM hasn't been manually edited
            // This preserves user-customized column orders
            if (!hasBeenManuallyEdited) {
                updateFromRegistry();
            }
            
            // Restore preserved columns for manually edited CTMs
            if (hasBeenManuallyEdited && preservedColumns != null) {
                columns = preservedColumns;
                // No need to rebuild - preservedColumns is already a deep copy
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
        super.reset();
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
        if (col < 0 || col >= getCols() || columns == null || col >= columns.size()) {
            return 0.0;
        }
        
        // Prevent infinite recursion between CTMs
        if (anyCtmComputing) {
            return 0.0;
        }
        
        anyCtmComputing = true;
        try {
            return computeValueSafely(col);
        } finally {
            anyCtmComputing = false;
        }
    }
    
    private double computeValueSafely(int col) {
        String stockName = columns.get(col).getStockName();

        // Handle A-L-E column (only in normal mode)
        if (stockName != null && stockName.equals("A-L-E")) {
            // For A-L-E in CTM, compute from other columns
            double assets = 0.0;
            double liabilities = 0.0;
            double equity = 0.0;
            
            for (int c = 0; c < getCols(); c++) {
                if (c == col) continue; // Skip A-L-E itself
                
                double value = getComputedValueForDisplay(c);
                
                ColumnType type = columns.get(c).getType();
                if (type != null) {
                    switch (type) {
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
            
            // Return the specific column's value from source table
            int colIndex = masterTable.findColumnByStockName(stockName.trim());
            
            if (colIndex >= 0) {
                double value = masterTable.getComputedValueForDisplay(colIndex);
                return value;
            }
        }

        return 0.0;
    }
}


