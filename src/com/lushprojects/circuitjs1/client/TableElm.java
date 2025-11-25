/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.TableEditDialog.ColumnType;
import java.util.ArrayList;

/**
 * Simplified Table Element - Displays voltage values from equations
 * Extends ChipElm to provide output pins at each column
 */
public class TableElm extends ChipElm {
    protected int rows = 0;  // Start with zero rows for new tables
    protected int cellWidthInGrids = 6;  // Width of each cell in grid units (cspc)
    protected int cellHeight = 16; // Height of each cell in pixels (for drawing)
    protected int cellSpacing = 0;  // Spacing between cells in pixels (for drawing), zero for best appearance
    protected boolean showInitialValues = false; // Control visibility of initial conditions row
    protected String[] rowDescriptions; // Descriptions/labels for each row (left column in spreadsheet view)
    protected String tableTitle = "Table"; // Title for the table (displayed in edit dialog and component)
    protected String tableUnits = ""; // Units to display in table cells ("" , $ or V)
    protected int decimalPlaces = 2; // Number of decimal places to show
    protected int showCellValues = 0; // Cell display mode: 0=Equation, 1=Equation:Value, 2=Value
    protected boolean collapsedMode = false; // Collapsed mode: show only title, headers, and computed row
    protected int priority = 5; // Priority for master table selection (higher = evaluated first, becomes master)
    protected int initMode = 0; // Initialization mode: 0=default, 1=all, 2=custom (set when manually edited)
    
    // Column data - encapsulates stockNames, columnTypes, initialValues, cellEquations, etc.
    protected ArrayList<TableColumn> columns;
    
    // Helper class managers for separation of concerns
    private final TableRenderer renderer;
    protected final TableEquationManager equationManager;
    protected final TableDataManager dataManager;
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
     * A_L_E columns are display-only (calculated in TableRenderer), not electrical outputs
     */
    private boolean isALEColumn(int col) {
        if (columns == null || col < 0 || col >= columns.size()) {
            return false;
        }
        return columns.get(col).isALE();
    }
    
    /**
     * Check if this table is the master for a given column
     * Calls ComputedValues.isMasterTable() directly (no caching)
     * Protected to allow subclasses (GodlyTableElm) to use it
     * 
     * @param col Column index to check
     * @return true if this table is the master for the column, false otherwise
     * NOTE: Empty/blank column headers always return false (never masters)
     */
    protected boolean isMasterForColumn(int col) {
        if (columns == null || col < 0 || col >= columns.size()) {
            return false;
        }
        TableColumn column = columns.get(col);
        // Empty or blank columns are never masters
        if (column.isEmpty()) {
            return false;
        }
        return ComputedValues.isMasterTable(column.getStockName().trim(), this);
    }
    
    /**
     * Register all stocks (column headers) with StockFlowRegistry
     * Package-private for use during initialization
     * NOTE: A-L-E computed columns are NOT registered as they are not real stocks
     * NOTE: Empty/blank column headers are NOT registered as they are ignored
     */
    private void registerAllStocks() {
        if (columns != null) {
            for (int col = 0; col < columns.size(); col++) {
                // Skip A-L-E computed columns - they are not real stocks
                if (isALEColumn(col)) {
                    continue;
                }
                
                TableColumn column = columns.get(col);
                // Skip empty or blank column headers
                if (column.isEmpty()) {
                    continue;
                }
                
                StockFlowRegistry.registerStock(column.getStockName().trim(), this);
            }
        }
    }
    
    /**
     * Find column index by stock name
     * Package-private for use by StockFlowRegistry
     */
    int findColumnByStockName(String stockName) {
        if (columns == null) return -1;
        for (int i = 0; i < columns.size(); i++) {
            if (stockName.equals(columns.get(i).getStockName())) {
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
        
        // Update each column with new row data
        if (columns != null && newCellEquations != null) {
            for (int col = 0; col < columns.size() && col < newCellEquations[0].length; col++) {
                TableColumn column = columns.get(col);
                column.resizeRows(newRowCount);
                
                for (int row = 0; row < newRowCount && row < newCellEquations.length; row++) {
                    String equation = (newCellEquations[row] != null && col < newCellEquations[row].length) 
                        ? newCellEquations[row][col] : "";
                    column.setCellEquation(row, equation);
                }
            }
        }
        
        // Now recompile equations
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
        return row >= 0 && row < rows && col >= 0 && col < getCols();
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
        if (isValidCell(row, col) && columns != null && col < columns.size()) {
            columns.get(col).setCellEquation(row, equation != null ? equation : "");
            compileEquation(row, col, columns.get(col).getCellEquation(row));
            // Note: A_L_E cells are computed dynamically in TableRenderer, not via equations
        }
    }
    
    public String getCellEquation(int row, int col) {
        if (isValidCell(row, col) && columns != null && col < columns.size()) {
            return columns.get(col).getCellEquation(row);
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
        // Register as master if not already done (for backward compatibility)
        if (!isAlreadyRegistered()) {
            registerAsMasterForStockNames();
        }
        
        if (geometryManager != null) {
            geometryManager.setupPins();
        }
    }
    
    /**
     * Check if this table has already been registered during priority-ordered setup
     */
    private boolean isAlreadyRegistered() {
        if (columns == null) return false;
        
        for (int col = 0; col < columns.size(); col++) {
            TableColumn column = columns.get(col);
            if (!isALEColumn(col) && !column.isEmpty()) {
                if (ComputedValues.isMasterTable(column.getStockName().trim(), this)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Update pin output flags after all tables have registered their masters.
     * Must be called AFTER all tables complete setupPins() to ensure pins reflect
     * the final master status, not intermediate status during registration.
     */
    public void updatePinOutputFlags() {
        if (pins == null) return;
        
        for (int col = 0; col < getCols() && col < pins.length; col++) {
            pins[col].output = !isALEColumn(col) && isMasterForColumn(col);
        }
    }
    
    /**
     * Register this table as a potential master for its column outputs.
     * Tables with higher priority become masters for shared stocks.
     * A-L-E computed columns and empty headers are skipped.
     */
    private void registerAsMasterForStockNames() {
        if (columns == null) return;
        
        for (int col = 0; col < columns.size(); col++) {
            if (isALEColumn(col)) continue;
            
            TableColumn column = columns.get(col);
            if (column.isEmpty()) continue;
            
            String name = column.getStockName();
            int effectivePriority = calculateEffectivePriority(column);
            ComputedValues.registerMasterTable(name.trim(), this, effectivePriority);
        }
    }
    
    /**
     * Calculate effective priority with optional type-based weighting.
     * Asset/Equity columns get +10 boost when weighted priority is enabled.
     */
    private int calculateEffectivePriority(TableColumn column) {
        int effectivePriority = priority;
        
        if (sim != null && sim.useWeightedPriority) {
            ColumnType colType = column.getType();
            if (colType == ColumnType.ASSET || colType == ColumnType.EQUITY) {
                effectivePriority += 10;
            }
        }
        
        return effectivePriority;
    }
    
    /**
     * Register as master without setting up pins
     * Used during priority-ordered registration phase
     * Public so CirSim can call it during circuit analysis
     */
    public void registerAsMasterOnly() {
        registerAsMasterForStockNames();
    }

    @Override
    int getVoltageSourceCount() {
        // Count voltage sources for master columns (skip A-L-E and empty columns)
        if (columns == null) return 0;
        
        int count = 0;
        for (int col = 0; col < columns.size(); col++) {
            if (isALEColumn(col)) continue;
            
            TableColumn column = columns.get(col);
            if (column.isEmpty()) continue;
            
            if (ComputedValues.isMasterTable(column.getStockName().trim(), this)) {
                count++;
            }
        }
        return count;
    }

    @Override
    int getPostCount() {
        return getCols(); // One post per column (for visual/logical connections)
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
   
    // Calculate computed values during simulation step (not during drawing)
    //     Master table optimization (avoids redundant computation)
    //     Convergence checking
    // Note: A-L-E values are calculated in TableRenderer.updateCachedValues() for display
    @Override
    public void doStep() {
        // Update input pin values from circuit
        for (int i = 0; i < getPostCount(); i++) {
            Pin p = pins[i];
            if (!p.output) {
                p.value = volts[i] > getThreshold();
            }
        }

        // Compute all NON-A-L-E columns
        if (columns == null) return;
        
        for (int col = 0; col < columns.size(); col++) {
            TableColumn column = columns.get(col);
            if (column.isALE()) continue;
            
            // Evaluate equations for this column
            double columnSum = 0.0;
            for (int row = 0; row < rows; row++) {
                columnSum += equationManager.getVoltageForCell(row, col);
            }

            // Check convergence
            if (Math.abs(columnSum - column.getLastSum()) > 1e-6) {
                sim.converged = false;
            }

            // Stamp matrix if we are master for this column
            boolean isMaster = isMasterForColumn(col);
            if (isMaster && pins[col].output) {
                stampColumnValue(col, columnSum);
            }

            column.setLastSum(columnSum);
        }
    }
    
    /**
     * Stamp a column's computed value to the circuit matrix
     */
    private void stampColumnValue(int col, double columnSum) {
        int vn = pins[col].voltSource + sim.nodeList.size();
        
        // Check output voltage convergence
        if (Math.abs(volts[col] - columnSum) > Math.abs(columnSum) * 0.01 && 
            sim.subIterations < 100) {
            sim.converged = false;
        }
        
        sim.stampRightSide(vn, columnSum);
    }
    
    @Override
    public void stepFinished() {
        // Register computed values for master columns (skip A-L-E and empty)
        if (columns == null) return;
        
        for (int col = 0; col < columns.size(); col++) {
            TableColumn column = columns.get(col);
            if (column.isALE() || column.isEmpty()) continue;
            
            if (isMasterForColumn(col)) {
                registerComputedValueAsLabeledNode(column.getStockName(), column.getLastSum());
                ComputedValues.markComputedThisStep(column.getStockName());
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
        // Stamp voltage sources for master columns (skip A-L-E and empty)
        if (columns == null || pins == null) return;
        
        for (int col = 0; col < getPostCount() && col < columns.size(); col++) {
            TableColumn column = columns.get(col);
            if (column.isALE()) continue;
            
            Pin p = pins[col];
            if (p.output && isMasterForColumn(col)) {
                stampMasterColumn(col, p);
            } else {
                stampNonMasterColumn(col);
            }
        }
        
        // Connect to labeled nodes
        connectToLabeledNodes();
    }
    
    /**
     * Stamp voltage source for a master column
     */
    private void stampMasterColumn(int col, Pin p) {
        int vn = p.voltSource + sim.nodeList.size();
        sim.stampNonLinear(vn);
        sim.stampVoltageSource(0, nodes[col], p.voltSource);
    }
    
    /**
     * Connect non-master column output to ground to prevent unconnected nodes
     */
    private void stampNonMasterColumn(int col) {
        int outputNode = nodes[col];
        if (isValidNode(outputNode)) {
            sim.stampResistor(outputNode, 0, 1e7); // 10MΩ to ground
        }
    }
    
    /**
     * Connect master columns to their labeled nodes if they exist
     */
    private void connectToLabeledNodes() {
        if (columns == null) return;
        
        for (int col = 0; col < columns.size(); col++) {
            TableColumn column = columns.get(col);
            if (column.isALE() || column.isEmpty()) continue;
            
            if (isMasterForColumn(col)) {
                connectColumnToLabeledNode(col, column);
            }
        }
    }
    
    /**
     * Connect a column to its labeled node, or to ground if no labeled node exists
     */
    private void connectColumnToLabeledNode(int col, TableColumn column) {
        String outputName = column.getStockName();
        LabeledNodeElm labeledNode = findLabeledNode(outputName);
        int outputNode = nodes[col];
        
        if (!isValidNode(outputNode)) return;
        
        if (labeledNode != null) {
            // Connect to labeled node via low-resistance connection
            int labeledNodeNum = labeledNode.getNode(0);
            if (isValidNode(labeledNodeNum)) {
                sim.stampResistor(outputNode, labeledNodeNum, 1e-6); // 1μΩ
            }
        } else {
            // No labeled node - connect to ground to prevent singular matrix
            sim.stampResistor(outputNode, 0, 1e6); // 1MΩ to ground
        }
    }
    
    /**
     * Check if a node number is valid for stamping
     */
    private boolean isValidNode(int nodeNum) {
        return nodeNum >= 0 && sim.nodeList != null && nodeNum < sim.nodeList.size();
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
            EditInfo ei = new EditInfo("Cell Display Mode", "");
            ei.choice = new Choice();
            ei.choice.add("Equation");
            ei.choice.add("Equation: Value");
            ei.choice.add("Value");
            ei.choice.select(showCellValues); // 0, 1, or 2
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
                // CirSim.console("[PRIORITY] Table '" + tableTitle + "' priority changed: " + oldPriority + " → " + priority);
                // CirSim.console("[PRIORITY] Clearing master tables and computed values...");
                ComputedValues.clearMasterTables();
                ComputedValues.clearComputedValues();
                // Force full circuit analysis to rebuild with new priorities
                // CirSim.console("[PRIORITY] Requesting circuit re-analysis...");
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
            showCellValues = ei.choice.getSelectedIndex(); // 0, 1, or 2
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
                // CirSim.console("Error opening table edit dialog: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "TableElm (" + rows + "x" + columns.size() + ")";
        arr[1] = "All cells use equations (node names or expressions)";

        int idx = 2;
        int maxOutputsToShow = 3;

        // Show output pin values
        for (int col = 0; col < Math.min(columns.size(), maxOutputsToShow) && idx < arr.length - 1; col++) {
            TableColumn column = columns.get(col);
            String header = column.getStockName();
            double output = column.getLastSum();
            // Use CircuitElm's getUnitText for proper SI unit formatting
            arr[idx++] = header + " = " + getUnitText(output, tableUnits);
        }

        if (columns.size() > maxOutputsToShow && idx < arr.length - 1) {
            arr[idx++] = "... (" + columns.size() + " outputs total)";
        }
    }
    
    /**
     * Set column header and update stock registry
     * NOTE: A-L-E computed columns are NOT registered as they are not real stocks
     * NOTE: Empty/blank column headers are NOT registered (ignored)
     */
    public void setColumnHeader(int col, String header) {
        if (col < 0 || col >= columns.size()) return;
        
        TableColumn column = columns.get(col);
        String oldHeader = column.getStockName();
        
        // Don't register/unregister A-L-E computed columns or empty headers
        if (!column.isALE()) {
            // Unregister old stock name (if not empty)
            if (oldHeader != null && !oldHeader.trim().isEmpty()) {
                StockFlowRegistry.unregisterStock(oldHeader.trim(), this);
            }
            
            // Register new stock name (if not empty)
            if (header != null && !header.trim().isEmpty()) {
                StockFlowRegistry.registerStock(header.trim(), this);
            }
        }
        
        // Set new header (keep as-is, even if empty)
        column.setStockName(header);
        
        // Note: No need to update cache - isMasterForColumn() does direct lookup
    }

    public String getColumnHeader(int col) {
        if (col >= 0 && col < columns.size()) {
            return columns.get(col).getStockName();
        }
        return "";
    }
    
    // Getter methods for TableEditDialog
    public int getRows() { return rows; }
    public int getCols() { return (columns != null) ? columns.size() : 0; }
    
    public double getInitialValue(int col) {
        if (col < 0 || col >= columns.size()) {
            return 0.0;
        }
        
        TableColumn column = columns.get(col);
        
        // For A-L-E columns, compute dynamically from other columns' initial values
        if (column.isALE()) {
            double assets = 0.0;
            double liabilities = 0.0;
            double equity = 0.0;
            
            // Sum up all non-ALE columns
            for (int c = 0; c < columns.size(); c++) {
                TableColumn col_c = columns.get(c);
                if (col_c.isALE()) continue;
                
                double value = col_c.getInitialValue();
                
                switch (col_c.getType()) {
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
            
            return assets - liabilities - equity;
        }
        
        // For non-ALE columns, return stored value
        return column.getInitialValue();
    }
    
    /**
     * Get the computed value for a column to display in the "Computed" row.
     * Base implementation returns the column sum (flow).
     * For A-L-E columns, returns the cached value from renderer (calculated from column totals).
     * Subclasses like GodlyTableElm can override to return integrated values (stocks).
     */
    public double getComputedValueForDisplay(int col) {
        if (col < 0 || col >= columns.size()) {
            return 0.0;
        }
        
        TableColumn column = columns.get(col);
        
        // For A-L-E columns, get from renderer cache
        // At t=0, return the initial value
        if (column.isALE()) {
            if (sim.t == 0.0) {
                return getInitialValue(col);
            }
            return renderer.getCachedSumValue(col);
        }
        
        // For other columns, return from column's lastSum (computed in doStep)
        return column.getLastSum();
    }
    
    public void setInitialConditionValue(int col, double value) {
        if (col >= 0 && col < columns.size()) {
            columns.get(col).setInitialValue(value);
            // Note: A_L_E initial values are calculated in TableRenderer, not stored
        }
    }
    
    // Additional methods for equation support in TableEditDialog

    // Column type accessor methods for TableEditDialog
    public ColumnType getColumnType(int col) {
        if (col >= 0 && col < columns.size()) {
            return columns.get(col).getType();
        }
        return ColumnType.ASSET; // Default
    }
    
    public void setColumnType(int col, ColumnType type) {
        if (col >= 0 && col < columns.size()) {
            columns.get(col).setType(type);
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
        registerAsMasterForStockNames();
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


