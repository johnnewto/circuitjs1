/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.Button;
import com.lushprojects.circuitjs1.client.TableEditDialog.ColumnType;

/**
 * Simplified Table Element - Displays voltage values from equations
 * Extends ChipElm to provide output pins at each column
 */
public class TableElm extends ChipElm {
    protected int rows = 0;  // Start with zero rows for new tables
    protected int cols = 3;
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
    
    // Helper classes for separation of concerns
    private TableRenderer renderer;
    private TableEquationManager equationManager;
    private TableDataManager dataManager;
    private TableGeometryManager geometryManager;
    
    // Note: No need to track labeled nodes anymore with direct resolution
    
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
    }
    
    private boolean isValidCell(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }
    
    // Enhanced version that evaluates equations for cell values
    protected double getVoltageForCell(int row, int col) {
        return equationManager.getVoltageForCell(row, col);
    }
    
    // Recompile all equations when labeled nodes change
    private void recompileAllEquations() {
        equationManager.recompileAllEquations();
    }
    
    void compileEquation(int row, int col, String equation) {
        equationManager.compileEquation(row, col, equation);
    }

    // Helper method to find a LabeledNodeElm with matching text
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
        
        // Store the computed voltage value in ComputedValues
        ComputedValues.setComputedValue(labelName, voltage);
    }
    
    // Static method to get computed values by other elements
    public static Double getComputedValue(String labelName) {
        return ComputedValues.getComputedValue(labelName);
    }

    // Helper method to get cell width in pixels
    int getCellWidthPixels() {
        return cellWidthInGrids * cspc;  // Convert grid units to pixels
    }
    
    // Calculate grid-aligned cell width for pin positioning (REMOVED - no longer needed)
    // Cell width is already in grid units, so column pitch in grid units is just cellWidthInGrids
    
    @Override
    void setupPins() {
        if (geometryManager != null) {
            geometryManager.setupPins();
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

        for (int col = 0; col < cols; col++) {
            double columnSum = 0.0;
            String name = outputNames[col];
            
            // Check to see if column computed value is already calculated by another element
            Double existingValue = ComputedValues.getComputedValue(name);
            boolean alreadyComputed = ComputedValues.isComputedThisStep(name);
            
            if (alreadyComputed && existingValue != null) {
                // Use the already computed value
                columnSum = existingValue.doubleValue();
            } else {
                // Compute the value ourselves
                for (int row = 0; row < rows; row++) {
                    double v = getVoltageForCell(row, col);
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
    }

    @Override
    public void stepFinished() {
        // Register computed values for other elements to use - do this after convergence
        for (int col = 0; col < cols; col++) {
            String name = outputNames[col];
            
            // Only register if we computed it ourselves (not if we used a pre-computed value)
            boolean alreadyComputed = ComputedValues.isComputedThisStep(name);
            if (!alreadyComputed && lastColumnSums != null) {
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
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) return new EditInfo("Table Title", tableTitle);
        if (n == 1) return new EditInfo("Cell Width (grids)", cellWidthInGrids, 1, 20);
        if (n == 2) return new EditInfo("Cell Height", cellHeight, 16, 48);
        if (n == 3) return new EditInfo("Cell Spacing", cellSpacing, 0, 5);
        if (n == 4) {
            EditInfo ei = new EditInfo("Show Initial Values", 0, -1, -1);
            ei.checkbox = new Checkbox("", showInitialValues);
            return ei;
        }
        if (n == 5) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.button = new Button("Edit Table Data...");
            return ei;
        }
        
        return null;
    }

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

        } else if (n == 5) {
            // Open table editing dialog
            openTableEditDialog();
        }
        setupPins(); // Recalculate chip size and pin positions
        setPoints();
        sim.analyzeFlag = true; // Trigger circuit re-analysis for pin position changes
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
            TableEditDialog dialog = new TableEditDialog(this, sim);
            dialog.show();
        }
    }
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "Equation Table (" + rows + "x" + cols + ")";
        arr[1] = "All cells use equations (node names or expressions)";

        int idx = 2;

        // Show output pin values
        for (int col = 0; col < Math.min(cols, 3) && idx < arr.length - 1; col++) {
            String header = outputNames[col];
            double output = lastColumnSums != null ? lastColumnSums[col] : 0.0;
            arr[idx++] = header + " = " + TableRenderer.formatTableValue(output, decimalPlaces, tableUnits);
        }

        if (cols > 3 && idx < arr.length - 1) {
            arr[idx++] = "... (" + cols + " outputs total)";
        }
    }
    
    // getCellLabel() removed - it was a duplicate of getCellEquation()
    // Use getCellEquation() instead

    public void setColumnHeader(int col, String header) {
        if (col >= 0 && col < cols) {
            outputNames[col] = header;
        }
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
}


