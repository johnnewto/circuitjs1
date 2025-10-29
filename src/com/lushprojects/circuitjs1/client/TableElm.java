/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.TableEditDialog.ColumnType;

/**
 * Simplified Table Element - Displays voltage values from equations
 * Extends ChipElm to provide output pins at each column
 */
public class TableElm extends ChipElm {
    protected int rows = 0;  // Start with zero rows for new tables
    protected int cols = 4;  // Default: Asset, Liability, Equity, A_L_E columns
    protected int cellWidthInGrids = 6;  // Width of each cell in grid units (cspc)
    protected int cellHeight = 16; // Height of each cell in pixels (for drawing)
    protected int cellSpacing = 0;  // Spacing between cells in pixels (for drawing), zero for best appearance
    protected double[] initialValues; // Values for initial conditions row
    protected boolean showInitialValues = false; // Control visibility of initial conditions row
    protected String[] outputNames; // Names for connecting outputs to labeled nodes (also used as column headers)
    protected String[] rowDescriptions; // Descriptions/labels for each row (left column in spreadsheet view)
    protected String tableTitle = "Table"; // Title for the table (displayed in edit dialog and component)
    protected String tableUnits = ""; // Units to display in table cells ("" , $ or V)
    protected int decimalPlaces = 2; // Number of decimal places to show
    protected ColumnType[] columnTypes; // Type for each column (Asset/Liability/Equity/Computed)
    
    // All cells now use equations
    // Variables available: a-i map to labeled nodes OR use labeled node names directly
    // Example equations: "a+b", "node1+node2", "sin(t)", "max(a,b,c)", "vcc>2.5?5:0"
    String[][] cellEquations;     // Equation strings for each cell
    Expr[][] compiledExpressions; // Compiled expressions for evaluation
    ExprState[][] expressionStates; // Expression evaluation state for each cell
    
    // Helper class managers for separation of concerns
    private final TableRenderer renderer;
    private final TableEquationManager equationManager;
    private final TableDataManager dataManager;
    private final TableGeometryManager geometryManager;
    
    // Storage for computed A-L-E cell values (if A-L-E column exists)
    private double[] computedALEValues;
    
    // Flag to bypass table dialog intercept when showing properties
    private boolean showingProperties = false;
    
    // Constructor for new table FROM MENU - receives auto-increment flag
    public TableElm(int xx, int yy) {
        this(xx, yy, true); // Always auto-increment for menu-created tables
    }

    // Internal constructor with control over auto-increment
    private TableElm(int xx, int yy, boolean autoIncrement) {
        super(xx, yy);
        noDiagonal = true;
        
        // Initialize helper classes
        dataManager = new TableDataManager(this);
        equationManager = new TableEquationManager(this, sim);
        geometryManager = new TableGeometryManager(this);
        renderer = new TableRenderer(this);
        
        if (autoIncrement) {
            tableTitle = "Table " + nextTableNumber;
            nextTableNumber++;
        } else {
            tableTitle = "Table"; // Default for programmatic creation
        }
        
        initTable();
        setupPins();
        setSize(sim.smallGridCheckItem.getState() ? 1 : 2);
    }

    // File loading constructor - NEVER auto-increments
    public TableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        noDiagonal = true;
        
        // Initialize helper classes
        dataManager = new TableDataManager(this);
        equationManager = new TableEquationManager(this, sim);
        geometryManager = new TableGeometryManager(this);
        renderer = new TableRenderer(this);
        
        parseTableData(st);
        
        // Update counter based on loaded table title
        updateTableCounter(tableTitle);
        
        initTable();
        setupPins();
        setSize((f & FLAG_SMALL) != 0 ? 1 : 2);
    }
    
    private void initTable() {
        dataManager.initTable();
        
        // Calculate A_L_E equations after data is initialized
        updateALEEquations();
        
        equationManager.recompileAllEquations();
        
        // Register all stocks with the synchronization registry
        registerAllStocks();
    }
    
    /**
     * Update all A_L_E column equations based on current Asset, Liability, and Equity values
     * NOTE: A_L_E columns don't use equation strings anymore - they are calculated directly in doStep()
     * This method is kept for compatibility but does nothing since A_L_E is computed at runtime
     */
    public void updateALEEquations() {
        // No-op: A_L_E is now calculated directly in doStep(), not via equations
        // Just update initial values
        updateALEInitialValues();
    }
    
    /**
     * Update A_L_E column initial values based on Asset, Liability, and Equity initial values
     * Uses direct arithmetic calculation, no expressions
     */
    private void updateALEInitialValues() {
        if (columnTypes == null || initialValues == null) return;
        
        for (int col = 0; col < cols; col++) {
            if (isALEColumn(col)) {
                initialValues[col] = calculateALEInitialValue();
            }
        }
    }
    
    /**
     * Calculate A_L_E initial value: sum(Assets) - sum(Liabilities) - Equity
     * Direct arithmetic calculation on initial values
     */
    private double calculateALEInitialValue() {
        double assets = 0.0;
        double liabilities = 0.0;
        double equity = 0.0;
        
        // Only process columns BEFORE the last column (exclude A_L_E itself)
        for (int col = 0; col < cols - 1; col++) {
            if (columnTypes != null && col < columnTypes.length && columnTypes[col] != null) {
                switch (columnTypes[col]) {
                    case ASSET:
                        assets += initialValues[col];
                        break;
                    case LIABILITY:
                        liabilities += initialValues[col];
                        break;
                    case EQUITY:
                        equity += initialValues[col];
                        break;
                }
            }
        }
        
        return assets - liabilities - equity;
    }
    
    /**
     * Check if a column is the A_L_E computed column
     * The last column is A_L_E when there are 4 or more columns
     */
    private boolean isALEColumn(int col) {
        return col == cols - 1 && cols >= 4;
    }
    
    /**
     * Calculate A_L_E column sum directly: Assets - Liabilities - Equity
     * This method sums the already-evaluated cell values from each column,
     * then applies the accounting equation formula.
     * No expression compilation needed - pure arithmetic on voltages.
     * 
     * Also stores individual cell values in computedALEValues for rendering.
     * Must be called AFTER all regular columns have been computed in doStep().
     * 
     * @return The total A_L_E value (sum of all A_L_E cells in the column)
     */
    private double calculateALEColumnSum() {
        // Initialize storage for A-L-E cell values
        if (computedALEValues == null || computedALEValues.length != rows) {
            computedALEValues = new double[rows];
        }
        
        double totalALE = 0.0;
        
        // Calculate A-L-E for each row
        for (int row = 0; row < rows; row++) {
            double assets = 0.0;
            double liabilities = 0.0;
            double equity = 0.0;
            
            // Only process columns BEFORE the last column (exclude A_L_E itself)
            for (int col = 0; col < cols - 1; col++) {
                // Call equationManager directly to avoid circular dependency
                double cellValue = equationManager.getVoltageForCell(row, col);
                
                // Add to appropriate bucket based on column type
                if (columnTypes != null && col < columnTypes.length && columnTypes[col] != null) {
                    switch (columnTypes[col]) {
                        case ASSET:
                            assets += cellValue;
                            break;
                        case LIABILITY:
                            liabilities += cellValue;
                            break;
                        case EQUITY:
                            equity += cellValue;
                            break;
                    }
                }
            }
            
            // Calculate A-L-E for this row: Assets - Liabilities - Equity
            double rowALE = assets - liabilities - equity;
            computedALEValues[row] = rowALE;
            totalALE += rowALE;
        }
        
        return totalALE;
    }
    
    /**
     * Register all stocks (column headers) with StockFlowRegistry
     * Package-private for use during initialization
     * NOTE: A-L-E computed columns are NOT registered as they are not real stocks
     */
    private void registerAllStocks() {
        if (outputNames != null) {
            for (int col = 0; col < cols && col < outputNames.length; col++) {
                // Skip A-L-E computed columns - they are not real stocks
                if (isALEColumn(col)) {
                    continue;
                }
                
                String stockName = outputNames[col];
                if (stockName != null && !stockName.trim().isEmpty()) {
                    StockFlowRegistry.registerStock(stockName, this);
                }
            }
        }
    }
    
    /**
     * Find column index by stock name
     * Package-private for use by StockFlowRegistry
     */
    int findColumnByStockName(String stockName) {
        if (outputNames == null) return -1;
        for (int i = 0; i < outputNames.length; i++) {
            if (stockName.equals(outputNames[i])) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Update row data during synchronization
     * Package-private for use by StockFlowRegistry
     * 
     * @param newRowCount New number of rows
     * @param newRowDescriptions New row descriptions
     * @param newCellEquations New cell equations
     */
    void updateRowData(int newRowCount, String[] newRowDescriptions, 
                      String[][] newCellEquations) {
        rows = newRowCount;
        rowDescriptions = newRowDescriptions;
        cellEquations = newCellEquations;
        
        // CRITICAL: Resize arrays BEFORE recompiling equations
        // compiledExpressions and expressionStates must match the new row count
        compiledExpressions = new Expr[rows][cols];
        expressionStates = new ExprState[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                expressionStates[row][col] = new ExprState(1); // Only need time variable
            }
        }
        
        // Now recompile equations with properly sized arrays
        equationManager.recompileAllEquations();
    }
    
    /**
     * Trigger synchronization with other tables sharing the same stocks
     * Simple wrapper - all logic is in StockFlowRegistry
     */
    public void synchronizeWithRelatedTables() {
        StockFlowRegistry.synchronizeRelatedTables(this);
    }
    
    private boolean isValidCell(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }
    
    protected double getVoltageForCell(int row, int col) {
        // Special handling for A-L-E columns - return computed value
        if (isALEColumn(col)) {
            if (computedALEValues != null && row >= 0 && row < computedALEValues.length) {
                return computedALEValues[row];
            }
            return 0.0;
        }
        
        // Normal columns use equation manager
        return equationManager.getVoltageForCell(row, col);
    }
    
    void compileEquation(int row, int col, String equation) {
        equationManager.compileEquation(row, col, equation);
    }

    /**
     * Find a LabeledNodeElm with matching text
     */
    private LabeledNodeElm findLabeledNode(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        
        String trimmedName = name.trim();
        for (int i = 0; i < sim.elmList.size(); i++) {
            Object o = sim.elmList.elementAt(i);
            if (o instanceof LabeledNodeElm) {
                LabeledNodeElm lne = (LabeledNodeElm) o;
                if (trimmedName.equals(lne.text)) {
                    return lne;
                }
            }
        }
        return null;
    }
        
    // Public methods for managing equations
    public void setCellEquation(int row, int col, String equation) {
        if (isValidCell(row, col)) {
            cellEquations[row][col] = equation != null ? equation : "";
            compileEquation(row, col, cellEquations[row][col]);
            
            // If this is not an A_L_E column, recalculate A_L_E initial values
            if (columnTypes != null && col < columnTypes.length && !isALEColumn(col)) {
                updateALEEquations();
                // Note: A_L_E cells are computed in doStep(), no equations to compile
            }
        }
    }
    
    public String getCellEquation(int row, int col) {
        if (isValidCell(row, col)) {
            return cellEquations[row][col];
        }
        return "";
    }
    
   
    protected void registerComputedValueAsLabeledNode(String labelName, double voltage) {
        if (labelName == null || labelName.isEmpty()) {
            return;
        }
        
        // Store the computed voltage value in ComputedValues with this table as the computer
        ComputedValues.setComputedValue(labelName, voltage, this);
    }
    
    public static Double getComputedValue(String labelName) {
        return ComputedValues.getComputedValue(labelName);
    }
    
    /**
     * Get cell width in pixels - delegates to GeometryManager
     */
    int getCellWidthPixels() {
        return geometryManager != null ? geometryManager.getCellWidthPixels() : cellWidthInGrids * cspc;
    }
    
    @Override
    void setupPins() {
        if (geometryManager != null) {
            geometryManager.setupPins();
        }
        registerAsMasterForOutputNames();
    }
    
    /**
     * Register this table as a potential master computer for its output names
     * Called during circuit initialization to establish which table computes each value
     */
    private void registerAsMasterForOutputNames() {
        if (outputNames != null) {
            for (int col = 0; col < cols && col < outputNames.length; col++) {
                String name = outputNames[col];
                if (name != null && !name.trim().isEmpty()) {
                    // Try to register as master - the first table to register becomes master
                    ComputedValues.registerMasterTable(name.trim(), this);
                }
            }
        }
    }

    @Override
    int getVoltageSourceCount() {
        return cols; // One voltage source per output pin
    }

    @Override
    int getPostCount() {
        return cols; // One post per column
    }

    @Override
    void setPoints() {
        super.setPoints();
        if (geometryManager != null) {
            geometryManager.setPoints();
        }
    }

    // Helper method to get table origin coordinates
    int getTableX() {
        // Table starts at chip origin
        return x;
    }

    int getTableY() {
        // Table starts at chip origin  
        return y;
    }

    @Override
    void draw(Graphics g) {
        if (renderer != null) {
            renderer.draw(g);
        }
    }

    private double[] lastColumnSums;
   
    // Calculate computed values during simulation step (not during drawing)
    @Override
    public void doStep() {
        // Update input pin values from circuit
        for (int i = 0; i < getPostCount(); i++) {
            Pin p = pins[i];
            if (!p.output)
                p.value = volts[i] > getThreshold();
        }

        // Always compute column sums for matrix stamping
        if (lastColumnSums == null) {
            lastColumnSums = new double[cols];
        }

        // FIRST PASS: Compute all NON-A-L-E columns
        for (int col = 0; col < cols; col++) {
            // Skip A-L-E column in first pass
            if (isALEColumn(col)) {
                continue;
            }
            
            double columnSum = 0.0;
            
            // NORMAL COLUMNS: Sum cell values from equations
            String name = outputNames[col];
            
            // Check if this table is the master computer for this output name
            boolean isMasterForThisName = ComputedValues.isMasterTable(name, this);
            
            // Check to see if column computed value is already calculated by another element
            Double existingValue = ComputedValues.getComputedValue(name);
            boolean alreadyComputed = ComputedValues.isComputedThisStep(name);
            
            if (alreadyComputed && existingValue != null && !isMasterForThisName) {
                // Use the already computed value from the master table
                columnSum = existingValue.doubleValue();
            } else if (isMasterForThisName) {
                // We are the master - compute the value ourselves
                for (int row = 0; row < rows; row++) {
                    double v = equationManager.getVoltageForCell(row, col);
                    columnSum += v;
                }
            } else {
                // No master registered yet or no computed value - compute it ourselves
                for (int row = 0; row < rows; row++) {
                    double v = equationManager.getVoltageForCell(row, col);
                    columnSum += v;
                }
            }

            // Check for convergence
            Double diff = Math.abs(columnSum - lastColumnSums[col]);
            if (diff > 1e-6) {
                sim.converged = false;
            }

            lastColumnSums[col] = columnSum;

            // Update output pin voltage source with converged column sum
            if (col < pins.length && pins[col].output) {
                sim.updateVoltageSource(0, nodes[col], pins[col].voltSource, columnSum);
            }
        }
        
        // SECOND PASS: Now compute A-L-E column (depends on other columns being computed)
        for (int col = 0; col < cols; col++) {
            if (isALEColumn(col)) {
                // Calculate A-L-E sum and store individual cell values
                double columnSum = calculateALEColumnSum();
                
                // Check for convergence
                Double diff = Math.abs(columnSum - lastColumnSums[col]);
                if (diff > 1e-6) {
                    sim.converged = false;
                }

                lastColumnSums[col] = columnSum;

                // Update output pin voltage source with converged column sum
                if (col < pins.length && pins[col].output) {
                    sim.updateVoltageSource(0, nodes[col], pins[col].voltSource, columnSum);
                }
            }
        }
    }

    @Override
    public void stepFinished() {
        // Register computed values for other elements to use - do this after convergence
        for (int col = 0; col < cols; col++) {
            String name = outputNames[col];
            
            // Check if this table is the master computer for this output name
            boolean isMasterForThisName = ComputedValues.isMasterTable(name, this);
            
            // Only register if we are the master and computed it ourselves
            if (isMasterForThisName && lastColumnSums != null) {
                registerComputedValueAsLabeledNode(name, lastColumnSums[col]);    
                ComputedValues.markComputedThisStep(name);
            }
        }
    }
    
    boolean nonLinear() {
        // Always nonlinear since we use equations (which may be nonlinear)
        return true;
    }
    
    // Has electrical connections via output pins
    @Override
    boolean isWireEquivalent() { return false; }

    @Override
    boolean isRemovableWire() { return false; }

    @Override
    boolean isDigitalChip() { return false; } // Analog chip with voltage outputs

    @Override
    String getChipName() { return "Table"; }

    @Override
    void stamp() {
        // Stamp voltage sources for all output pins
        for (int i = 0; i < getPostCount(); i++) {
            Pin p = pins[i];
            if (p.output) {
                sim.stampVoltageSource(0, nodes[i], p.voltSource);
            }
        }
        
        // Connect to labeled nodes if output names are specified
        if (outputNames != null) {
            for (int col = 0; col < cols && col < outputNames.length; col++) {
                String outputName = outputNames[col];
                if (outputName != null && !outputName.trim().isEmpty()) {
                    LabeledNodeElm labeledNode = findLabeledNode(outputName);
                    if (labeledNode != null) {
                        // Stamp low-value resistor between output node and labeled node
                        double resistance = 1e-6; // 1μΩ - very low resistance
                        int outputNode = nodes[col]; // Output node from table
                        int labeledNodeNum = labeledNode.getNode(0); // Get labeled node number
                        
                        // Validate node numbers before stamping
                        if (outputNode >= 0 && labeledNodeNum >= 0 && sim.nodeList != null && 
                            outputNode < sim.nodeList.size() && labeledNodeNum < sim.nodeList.size()) {
                            //CirSim.console("TableElm: Stamping resistor between nodes " + outputNode + " and " + labeledNodeNum + " (resistance=" + resistance + ")");
                            sim.stampResistor(outputNode, labeledNodeNum, resistance);
                            //CirSim.console("TableElm: Successfully stamped resistor for output '" + outputName + "'");
                        }
                        // Error handling removed - invalid nodes are silently ignored
                    } else {
//                        CirSim.console("TableElm: No labeled node found for output name '" + outputName + "'");
                        ;
                    }
                }
            }
        }
    }

    public String dump() {
        return super.dump() + dataManager.dump();
    }

    protected void parseTableData(StringTokenizer st) {
        dataManager.parseTableData(st);
    }

    int getDumpType() { 
        return 253; // Choose unused dump type
    }
    
    @Override
    public EditInfo getEditInfo(int n) {
        // If showing properties dialog, don't intercept - show standard properties
        if (showingProperties) {
            if (n == 0) return new EditInfo("Table Title", tableTitle);
            if (n == 1) return new EditInfo("Cell Width (grids)", cellWidthInGrids, 1, 20);
            if (n == 2) return new EditInfo("Cell Height", cellHeight, 16, 48);
            if (n == 3) return new EditInfo("Cell Spacing", cellSpacing, 0, 5);
            if (n == 4) {
                EditInfo ei = new EditInfo("Show Initial Values", 0, -1, -1);
                ei.checkbox = new Checkbox("", showInitialValues);
                return ei;
            }
            return null;
        }
        
        // On first call (n=0) in normal mode, immediately open the table edit dialog
        if (n == 0) {
            openTableEditDialog();
            return null; // Return null to prevent standard dialog from showing
        }
        
        return null;
    }

    @Override
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            tableTitle = ei.textf.getText();
        } else if (n == 1) {
            cellWidthInGrids = Math.max(1, (int)ei.value);
        } else if (n == 2) {
            cellHeight = Math.max(16, (int)ei.value);
        } else if (n == 3) {
            cellSpacing = Math.max(0, (int)ei.value);
        } else if (n == 4) {
            showInitialValues = ei.checkbox.getValue();
        }
        
        setupPins();
        setPoints();
        sim.analyzeFlag = true;
    }

    /**
     * Open standard properties dialog (called from TableEditDialog)
     */
    public void openPropertiesDialog() {
        showingProperties = true;
        if (sim != null) {
            sim.doEdit(this);
        }
        showingProperties = false;
    }
    
    // Resize table method for use by TableEditDialog
    public void resizeTable(int newRows, int newCols) {
        // Delegate data resizing to TableDataManager
        dataManager.resizeTable(newRows, newCols);
        
        // Recalculate A_L_E equations after resize
        updateALEEquations();
        
        // Recreate pins with new column count
        setupPins();
        allocNodes();
        setPoints();
    }
    
    protected void openTableEditDialog() {
        // Open the enhanced table editing dialog
        if (sim != null) {
            try {
                TableEditDialog dialog = new TableEditDialog(this, sim);
                dialog.show();
            } catch (Exception e) {
                CirSim.console("Error opening table edit dialog: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "Equation Table (" + rows + "x" + cols + ")";
        arr[1] = "All cells use equations (node names or expressions)";

        int idx = 2;
        int maxOutputsToShow = 3;

        // Show output pin values
        for (int col = 0; col < Math.min(cols, maxOutputsToShow) && idx < arr.length - 1; col++) {
            String header = outputNames[col];
            double output = lastColumnSums != null ? lastColumnSums[col] : 0.0;
            arr[idx++] = header + " = " + TableRenderer.formatTableValue(output, decimalPlaces, tableUnits);
        }

        if (cols > maxOutputsToShow && idx < arr.length - 1) {
            arr[idx++] = "... (" + cols + " outputs total)";
        }
    }
    
    /**
     * Set column header and update stock registry
     * NOTE: A-L-E computed columns are NOT registered as they are not real stocks
     */
    public void setColumnHeader(int col, String header) {
        if (col < 0 || col >= cols) return;
        
        String oldHeader = outputNames[col];
        
        // Don't register/unregister A-L-E computed columns
        if (!isALEColumn(col)) {
            // Unregister old stock name
            if (oldHeader != null && !oldHeader.trim().isEmpty()) {
                StockFlowRegistry.unregisterStock(oldHeader, this);
            }
            
            // Register new stock name
            if (header != null && !header.trim().isEmpty()) {
                StockFlowRegistry.registerStock(header, this);
            }
        }
        
        // Set new header
        outputNames[col] = header;
    }

    public String getColumnHeader(int col) {
        if (col >= 0 && col < cols) {
            return outputNames[col];
        }
        return "";
    }
    
    // Getter methods for TableEditDialog
    public int getRows() { return rows; }
    public int getCols() { return cols; }
    
    public double getInitialValue(int col) {
        if (col >= 0 && col < cols && initialValues != null && col < initialValues.length) {
            return initialValues[col];
        }
        return 0.0;
    }
    
    public void setInitialConditionValue(int col, double value) {
        if (col >= 0 && col < cols && initialValues != null && col < initialValues.length) {
            initialValues[col] = value;
            
            // If this is not an A_L_E column, recalculate A_L_E initial values
            if (columnTypes != null && col < columnTypes.length && !isALEColumn(col)) {
                updateALEInitialValues();
            }
        }
    }
    
    // Additional methods for equation support in TableEditDialog

    // Column type accessor methods for TableEditDialog
    public ColumnType getColumnType(int col) {
        if (col >= 0 && col < cols && columnTypes != null && col < columnTypes.length) {
            return columnTypes[col];
        }
        return ColumnType.ASSET; // Default
    }
    
    public void setColumnType(int col, ColumnType type) {
        if (col >= 0 && col < cols) {
            if (columnTypes == null || columnTypes.length != cols) {
                // Initialize if needed
                columnTypes = new ColumnType[cols];
                for (int i = 0; i < cols; i++) {
                    columnTypes[i] = ColumnType.ASSET;
                }
            }
            columnTypes[col] = type;
        }
    }
    
    public String getColumnTypeName(int col) {
        ColumnType type = getColumnType(col);
        return TableRenderer.getColumnTypeName(type);
    }
    
    // Table title accessor methods
    public String getTableTitle() {
        return tableTitle;
    }
    
    public void setTableTitle(String title) {
        this.tableTitle = (title != null) ? title : "Table";
    }
    
    // Row description accessor methods
    public String getRowDescription(int row) {
        if (row >= 0 && row < rows && rowDescriptions != null && row < rowDescriptions.length) {
            return rowDescriptions[row];
        }
        return "Row" + (row + 1);
    }
    
    public void setRowDescription(int row, String description) {
        if (row >= 0 && row < rows) {
            if (rowDescriptions == null || rowDescriptions.length != rows) {
                rowDescriptions = new String[rows];
                for (int i = 0; i < rows; i++) {
                    rowDescriptions[i] = "Row" + (i + 1);
                }
            }
            rowDescriptions[row] = description;
        }
    }
    
    // Add static counter for table numbering
    private static int nextTableNumber = 1;

    // Add method to reset counter when loading circuits
    public static void resetTableCounter() {
        nextTableNumber = 1;
    }

    // Add method to update counter based on existing tables
    public static void updateTableCounter(String title) {
        // Extract number from title like "Table 5"
        if (title != null && title.startsWith("Table ")) {
            try {
                String numStr = title.substring(6).trim();
                int num = Integer.parseInt(numStr);
                if (num >= nextTableNumber) {
                    nextTableNumber = num + 1;
                }
            } catch (NumberFormatException e) {
                // Not a numbered table, ignore
            }
        }
    }
    
    @Override
    public void delete() {
        // Unregister all stocks from synchronization registry
        StockFlowRegistry.unregisterAllStocks(this);
        super.delete();
    }
}


