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
    protected boolean showCellValues = false; // Show "equation = value" (true) or just "equation" (false)
    protected boolean collapsedMode = false; // Collapsed mode: show only title, headers, and computed row
    protected ColumnType[] columnTypes; // Type for each column (Asset/Liability/Equity/Computed)
    protected int priority = 5; // Priority for master table selection (higher = evaluated first, becomes master)
    
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
        equationManager.recompileAllEquations();
        
        // Register all stocks with the synchronization registry
        registerAllStocks();
    }
    
    /**
     * Check if a column is the A_L_E computed column
     * The last column is A_L_E when there are 4 or more columns
     * A_L_E columns are display-only (calculated in TableRenderer), not electrical outputs
     */
    private boolean isALEColumn(int col) {
        return cols >= 4 && col == (cols - 1);
    }
    
    /**
     * Check if this table is the master for a given column
     * Calls ComputedValues.isMasterTable() directly (no caching)
     * Protected to allow subclasses (GodlyTableElm) to use it
     * 
     * @param col Column index to check
     * @return true if this table is the master for the column, false otherwise
     */
    protected boolean isMasterForColumn(int col) {
        if (col < 0 || col >= cols || outputNames == null || col >= outputNames.length) {
            return false;
        }
        String name = outputNames[col];
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return ComputedValues.isMasterTable(name.trim(), this);
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
        // Note: For A-L-E columns, the renderer calculates values directly
        // and never calls this method. This method only handles regular columns.
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
            // Note: A_L_E cells are computed dynamically in TableRenderer, not via equations
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
     * Uses a constant base grid size (16 pixels) to ensure consistent sizing
     * regardless of the table's small/normal chip size setting
     */
    int getCellWidthPixels() {
        if (geometryManager != null) {
            return geometryManager.getCellWidthPixels();
        }
        // Fallback: use constant 16-pixel grid (normal grid size)
        final int BASE_GRID_SIZE = 16;
        return cellWidthInGrids * BASE_GRID_SIZE;
    }
    
    @Override
    void setupPins() {
        // CRITICAL: Register as master BEFORE setting up pins
        // This determines which table is master for each column/stock
        registerAsMasterForOutputNames();
        
        if (geometryManager != null) {
            geometryManager.setupPins();
        }
    }
    
    /**
     * Register this table as a potential master for its column outputs
     * Called during circuit initialization to establish which table is master for each stock/column
     * Tables with higher priority are evaluated first and become masters for shared stocks
     * NOTE: A-L-E computed columns are NOT registered as they are not real stocks
     */
    private void registerAsMasterForOutputNames() {
        if (outputNames != null) {
            for (int col = 0; col < cols && col < outputNames.length; col++) {
                // Skip A-L-E computed columns - they are not real stocks
                if (isALEColumn(col)) {
                    continue;
                }
                
                String name = outputNames[col];
                if (name != null && !name.trim().isEmpty()) {
                    // Try to register as master for this column with priority
                    // Higher priority tables become masters for shared stocks
                    ComputedValues.registerMasterTable(name.trim(), this, priority);
                }
            }
        }
    }

    @Override
    int getVoltageSourceCount() {
        // Only count voltage sources for columns where this table is the master
        // AND skip A-L-E columns (they are purely computed, not electrical outputs)
        // Each master column gets a voltage source (even if empty, outputs 0V)
        int count = 0;
        if (outputNames != null) {
            for (int col = 0; col < cols && col < outputNames.length; col++) {
                // Skip A-L-E columns - they have no voltage source
                if (isALEColumn(col)) {
                    continue;
                }
                
                String name = outputNames[col];
                if (name != null && !name.trim().isEmpty()) {
                    if (ComputedValues.isMasterTable(name.trim(), this)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Override
    int getPostCount() {
        return cols; // One post per column (for visual/logical connections)
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

    /**
     * Get the rectangle for the collapse arrow button
     * @return Rectangle representing the clickable area of the collapse arrow
     */
    Rectangle getCollapseArrowRect() {
        int tableX = getTableX();
        int tableY = getTableY();
        
        // Arrow is positioned on the left side of the title
        // Title area is 20 pixels high
        // Make the clickable area larger for better usability (30x20 pixels)
        int arrowX = tableX; // Start at left edge
        int arrowY = tableY;
        int arrowWidth = 30;
        int arrowHeight = 20;
        
        return new Rectangle(arrowX, arrowY, arrowWidth, arrowHeight);
    }

    /**
     * Check if a point is inside the collapse arrow button
     * @param gx grid x coordinate
     * @param gy grid y coordinate
     * @return true if the point is inside the arrow button
     */
    boolean isCollapseArrowClicked(int gx, int gy) {
        Rectangle arrowRect = getCollapseArrowRect();
        return arrowRect.contains(gx, gy);
    }

    /**
     * Check if the mouse is hovering over the collapse arrow
     * @return true if the mouse is hovering over the arrow
     */
    boolean isArrowHovered() {
        if (sim == null) {
            return false;
        }
        int gx = sim.inverseTransformX(sim.mouseCursorX);
        int gy = sim.inverseTransformY(sim.mouseCursorY);
        return isCollapseArrowClicked(gx, gy);
    }

    /**
     * Toggle collapsed mode when arrow is clicked
     */
    void mouseUp() {
        // This will be called by CirSim when the mouse is released on the element
        // The actual click detection happens in the doSwitch-like method
    }

    /**
     * Toggle collapsed mode
     */
    void toggleCollapsedMode() {
        collapsedMode = !collapsedMode;
        setSize((flags & FLAG_SMALL) != 0 ? 1 : 2); // Recalculate size based on current size setting
    }

    @Override
    void draw(Graphics g) {
        if (renderer != null) {
            renderer.draw(g);
        }
    }

    @Override
    void drawPosts(Graphics g) {
        // Override to hide posts - electrical connections remain functional
        // but visual elements (connection dots) are not drawn
    }

    protected double[] lastColumnSums;
   
    // Calculate computed values during simulation step (not during drawing)
    //     Master table optimization (avoids redundant computation)
    //     Convergence checking
    // Note: A-L-E values are calculated in TableRenderer.updateCachedValues() for display
    @Override
    public void doStep() {
        // Update input pin values from circuit
        int postCount = getPostCount();
        for (int i = 0; i < postCount; i++) {
            Pin p = pins[i];
            if (!p.output)
                p.value = volts[i] > getThreshold();
        }

        // Always compute column sums for matrix stamping
        if (lastColumnSums == null) {
            lastColumnSums = new double[cols];
        }

        // Compute all NON-A-L-E columns
        // Performance: Skip A-L-E column by limiting loop (it's always the last column)
        int colLimit = (cols >= 4) ? (cols - 1) : cols; // Exclude A-L-E column if it exists
        
        for (int col = 0; col < colLimit; col++) {
            double columnSum = 0.0;
            
            // Check if we are the master for this column (direct lookup, no cache)
            boolean isMasterForThisName = isMasterForColumn(col);
            
            // ALWAYS evaluate our own equations - needed for display
            // Non-master tables evaluate equations but don't stamp voltage sources
            for (int row = 0; row < rows; row++) {
                columnSum += equationManager.getVoltageForCell(row, col);
            }

            // Check for convergence (avoid boxing by using primitive)
            double diff = Math.abs(columnSum - lastColumnSums[col]);
            if (diff > 1e-6) {
                sim.converged = false;
            }

            // Like VCVSElm: stamp matrix for nonlinear iteration
            // ONLY stamp if we are the master for this column
            if (isMasterForThisName && pins[col].output) {
                int vn = pins[col].voltSource + sim.nodeList.size();
                // Check output voltage convergence like VCVSElm does
                double outputVoltage = volts[col];
                if (Math.abs(outputVoltage - columnSum) > Math.abs(columnSum) * 0.01 && sim.subIterations < 100) {
                    sim.converged = false;
                }
                // Stamp the right side with the computed value
                sim.stampRightSide(vn, columnSum);
            }

            lastColumnSums[col] = columnSum;
        }
    }
    
    @Override
    public void stepFinished() {
        // Register computed values for other elements to use - do this after convergence
        // Skip A-L-E columns - they are not registered as stocks
        // Performance: Skip A-L-E column by limiting loop (it's always the last column)
        int colLimit = (cols >= 4) ? (cols - 1) : cols; // Exclude A-L-E column if it exists
        
        for (int col = 0; col < colLimit; col++) {
            // Check if this table is master for this specific column
            boolean isMasterForThisColumn = isMasterForColumn(col);
            
            // Only register if we are the master for this column and computed it ourselves
            if (isMasterForThisColumn && lastColumnSums != null) {
                String name = outputNames[col];
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
        // Stamp voltage sources ONLY for columns where this table is the master
        // AND skip A-L-E columns (they are purely computed, not electrical outputs)
        int postCount = getPostCount();
        for (int col = 0; col < postCount; col++) {
            // Skip A-L-E columns (display-only, calculated in TableRenderer)
            if (isALEColumn(col)) {
                continue;
            }
            
            Pin p = pins[col];
            if (p.output) {
                // Check if this table is master for this specific column
                boolean isMasterForThisColumn = isMasterForColumn(col);
                if (isMasterForThisColumn) {
                    // Like VCVSElm: stamp nonlinear for the voltage source row
                    int vn = p.voltSource + sim.nodeList.size();
                    sim.stampNonLinear(vn);
                    sim.stampVoltageSource(0, nodes[col], p.voltSource);
                } else {
                    // Non-master column: connect output node to ground to prevent unconnected nodes
                    // Use high-value resistor (10MΩ) so it doesn't affect circuit behavior
                    int outputNode = nodes[col];
                    if (outputNode >= 0 && sim.nodeList != null && outputNode < sim.nodeList.size()) {
                        sim.stampResistor(outputNode, 0, 1e7); // 10MΩ to ground
                    }
                }
            }
        }
        
        // Connect to labeled nodes if output names are specified
        // Skip A-L-E columns - they should not drive labeled nodes
        if (outputNames != null) {
            for (int col = 0; col < cols && col < outputNames.length; col++) {
                // Skip A-L-E columns (display-only, calculated in TableRenderer)
                if (isALEColumn(col)) {
                    continue;
                }
                
                String outputName = outputNames[col];
                if (outputName != null && !outputName.trim().isEmpty()) {
                    // Check if this table is master for this specific column
                    boolean isMasterForThisColumn = isMasterForColumn(col);
                    LabeledNodeElm labeledNode = findLabeledNode(outputName);
                    
                    if (labeledNode != null && isMasterForThisColumn) {
                        // Only stamp resistor connection if we are the master for this column
                        // Stamp low-value resistor between output node and labeled node
                        double resistance = 1e-6; // 1μΩ - very low resistance
                        int outputNode = nodes[col]; // Output node from table
                        int labeledNodeNum = labeledNode.getNode(0); // Get labeled node number
                        
                        // Validate node numbers before stamping
                        if (outputNode >= 0 && labeledNodeNum >= 0 && sim.nodeList != null && 
                            outputNode < sim.nodeList.size() && labeledNodeNum < sim.nodeList.size()) {
                            sim.stampResistor(outputNode, labeledNodeNum, resistance);
                        }
                    } else if (isMasterForThisColumn && labeledNode == null) {
                        // Master column but no labeled node - connect to ground to avoid singular matrix
                        // This prevents isolated voltage source nodes
                        int outputNode = nodes[col];
                        if (outputNode >= 0 && sim.nodeList != null && outputNode < sim.nodeList.size()) {
                            // Stamp high-value resistor to ground (1MΩ) to prevent floating node
                            sim.stampResistor(outputNode, 0, 1e6);
                        }
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
        // Always show standard properties on right-click
        // TableEditDialog is opened only via double-click (handled separately)
        if (n == 0) return new EditInfo("Table Title", tableTitle);
        if (n == 1) {
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
        if (n == 2) return new EditInfo("Cell Width (grids)", cellWidthInGrids, 1, 20);
        if (n == 3) return new EditInfo("Cell Height", cellHeight, 16, 48);
        if (n == 4) return new EditInfo("Cell Spacing", cellSpacing, 0, 5);
        if (n == 5) {
            EditInfo ei = new EditInfo("Show Initial Values", 0, -1, -1);
            ei.checkbox = new Checkbox("", showInitialValues);
            return ei;
        }
        if (n == 6) {
            EditInfo ei = new EditInfo("Show Cell Values", 0, -1, -1);
            ei.checkbox = new Checkbox("", showCellValues);
            return ei;
        }
        if (n == 7) {
            EditInfo ei = new EditInfo("Collapsed Mode", 0, -1, -1);
            ei.checkbox = new Checkbox("", collapsedMode);
            return ei;
        }
        return null;
    }

    @Override
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            tableTitle = ei.textf.getText();
        } else if (n == 1) {
            int oldPriority = priority;
            priority = ei.choice.getSelectedIndex() + 1; // Convert from 0-indexed to 1-9
            // If priority changed, clear master tables and computed values to force re-registration
            // and avoid stale computed values causing convergence issues.
            if (oldPriority != priority) {
                ComputedValues.clearMasterTables();
                ComputedValues.clearComputedValues();
                // Force full circuit analysis to rebuild with new priorities
                sim.needAnalyze();
            }
        } else if (n == 2) {
            cellWidthInGrids = Math.max(1, (int)ei.value);
        } else if (n == 3) {
            cellHeight = Math.max(16, (int)ei.value);
        } else if (n == 4) {
            cellSpacing = Math.max(0, (int)ei.value);
        } else if (n == 5) {
            showInitialValues = ei.checkbox.getValue();
        } else if (n == 6) {
            showCellValues = ei.checkbox.getValue();
        } else if (n == 7) {
            collapsedMode = ei.checkbox.getValue();
        }
        
        setupPins();
        setPoints();
        sim.analyzeFlag = true;
    }
    
    // Resize table method for use by TableEditDialog
    public void resizeTable(int newRows, int newCols) {
        // Delegate data resizing to TableDataManager
        dataManager.resizeTable(newRows, newCols);
        
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
                CirSim.dialogShowing = dialog;
                dialog.show();
            } catch (Exception e) {
                CirSim.console("Error opening table edit dialog: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "TableElm (" + rows + "x" + cols + ")";
        arr[1] = "All cells use equations (node names or expressions)";

        int idx = 2;
        int maxOutputsToShow = 3;

        // Show output pin values
        for (int col = 0; col < Math.min(cols, maxOutputsToShow) && idx < arr.length - 1; col++) {
            String header = outputNames[col];
            double output = lastColumnSums != null ? lastColumnSums[col] : 0.0;
            // Use CircuitElm's getUnitText for proper SI unit formatting
            arr[idx++] = header + " = " + getUnitText(output, tableUnits);
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
        
        // Note: No need to update cache - isMasterForColumn() does direct lookup
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
        if (col < 0 || col >= cols) {
            return 0.0;
        }
        
        // For A-L-E columns, compute dynamically from other columns' initial values
        if (isALEColumn(col)) {
            double assets = 0.0;
            double liabilities = 0.0;
            double equity = 0.0;
            
            // Sum up all non-ALE columns
            for (int c = 0; c < cols - 1; c++) {
                double value = (initialValues != null && c < initialValues.length) ? initialValues[c] : 0.0;
                
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
            
            return assets - liabilities - equity;
        }
        
        // For non-ALE columns, return stored value
        if (initialValues != null && col < initialValues.length) {
            return initialValues[col];
        }
        return 0.0;
    }
    
    /**
     * Get the computed value for a column to display in the "Computed" row.
     * Base implementation returns the column sum (flow).
     * For A-L-E columns, returns the cached value from renderer (calculated from column totals).
     * Subclasses like GodlyTableElm can override to return integrated values (stocks).
     */
    public double getComputedValueForDisplay(int col) {
        // For A-L-E columns, get from renderer cache
        // At t=0, return the initial value
        if (isALEColumn(col)) {
            if (sim.t == 0.0) {
                return getInitialValue(col);
            }
            return renderer.getCachedSumValue(col);
        }
        
        // For other columns, return from lastColumnSums (computed in doStep)
        if (col >= 0 && col < cols && lastColumnSums != null && col < lastColumnSums.length) {
            return lastColumnSums[col];
        }
        return 0.0;
    }
    
    public void setInitialConditionValue(int col, double value) {
        if (col >= 0 && col < cols && initialValues != null && col < initialValues.length) {
            initialValues[col] = value;
            // Note: A_L_E initial values are calculated in TableRenderer, not stored
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
    
    // Priority accessor methods
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
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
    public void reset() {
        // Re-register as master for stock columns when circuit is reset
        // This ensures voltage sources are properly stamped after reset
        registerAsMasterForOutputNames();
        // Note: No cache to update - isMasterForColumn() does direct lookup
        
        // Reset renderer cache so values are recomputed from scratch
        if (renderer != null) {
            renderer.resetCache();
        }
        
        super.reset();
    }
    
    @Override
    public void delete() {
        // Unregister all stocks from synchronization registry
        StockFlowRegistry.unregisterAllStocks(this);
        super.delete();
    }
}


