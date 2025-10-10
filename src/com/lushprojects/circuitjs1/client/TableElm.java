/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.Button;

/**
 * Simplified Table Element - Displays voltage values from equations
 * Extends CircuitElm for lightweight equation-based display
 */
public class TableElm extends CircuitElm {
    protected int rows = 3;
    protected int cols = 2; 
    protected int cellSize = 60;
    protected int cellSpacing = 4;
    protected String[] columnHeaders;
    protected boolean showComputedRow = true; // Show computed row at bottom
    
    // All cells now use equations
    // Variables available: a-i map to labeled nodes OR use labeled node names directly
    // Example equations: "a+b", "node1+node2", "sin(t)", "max(a,b,c)", "vcc>2.5?5:0"
    String[][] cellEquations;     // Equation strings for each cell
    Expr[][] compiledExpressions; // Compiled expressions for evaluation
    ExprState[][] expressionStates; // Expression evaluation state for each cell
    
    // Track labeled nodes to detect changes that require recompilation
    String[] lastKnownNodes;  // Last known labeled node list (sorted)
    
    // Constructor for new table
    public TableElm(int xx, int yy) {
        super(xx, yy);
        initTable();
    }
    
    // File loading constructor  
    public TableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        parseTableData(st);
        initTable();
    }
    
    private void initTable() {
        // Initialize equation arrays
        if (cellEquations == null) {
            cellEquations = new String[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    // Default equation is just the node name (like "node1", "vcc", etc.)
                    cellEquations[row][col] = "node" + (row * cols + col + 1);
                }
            }
            CirSim.console("TableElm: Initialized cellEquations array");
        }
        
        if (compiledExpressions == null) {
            compiledExpressions = new Expr[rows][cols];
            CirSim.console("TableElm: Initialized compiledExpressions array");
        }
        
        if (expressionStates == null) {
            expressionStates = new ExprState[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    expressionStates[row][col] = new ExprState(9); // Support variables a-i
                }
            }
        }
        
        // Initialize column headers if not set
        if (columnHeaders == null) {
            columnHeaders = new String[cols];
            for (int i = 0; i < cols; i++) {
                columnHeaders[i] = "Col" + (i + 1);
            }
        }
        
        // Ensure no null or empty values exist
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (cellEquations[row][col] == null || cellEquations[row][col].trim().isEmpty()) {
                    cellEquations[row][col] = "node" + (row * cols + col + 1);
                }
            }
        }
        
        for (int i = 0; i < cols; i++) {
            if (columnHeaders[i] == null || columnHeaders[i].trim().isEmpty()) {
                columnHeaders[i] = "Col" + (i + 1);
            }
        }
        
        // Compile all equations
        recompileAllEquations();
    }
    
    // Enhanced version that evaluates equations for cell values
    protected double getVoltageForCell(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            return 0.0;
        }
        
        // All cells now use equations
        if (compiledExpressions[row][col] != null) {
            return evaluateEquation(row, col);
        }
        
        // If no compiled expression, try to evaluate as simple node name
        String equation = cellEquations[row][col];
        if (equation != null && !equation.trim().isEmpty()) {
            // Try direct node lookup first
            if (sim != null) {
                double voltage = sim.getLabeledNodeVoltage(equation);
                if (voltage != 0.0 || LabeledNodeElm.getByName(equation) != null) {
                    return voltage;
                }
            }
            
            // Check computed values
            Double computedValue = LabeledNodeElm.getComputedValue(equation);
            if (computedValue != null) {
                return computedValue.doubleValue();
            }
        }
        
        return 0.0;
    }
    
private double evaluateEquation(int row, int col) {
    try {
        // Handle deferred compilation (null expression with valid equation text)
        if (compiledExpressions[row][col] == null && 
            cellEquations[row][col] != null && !cellEquations[row][col].trim().isEmpty()) {
            
            CirSim.console("TableElm: Attempting deferred compilation [" + row + "][" + col + "]");
            compileEquation(row, col, cellEquations[row][col]);
            
            // If still null after compilation attempt, return 0
            if (compiledExpressions[row][col] == null) {
                return 0.0;
            }
        }
        
        // If expression is null (no equation or compilation failed), return 0
        if (compiledExpressions[row][col] == null) {
            return 0.0;
        }
        
        // Evaluate the compiled expression
        ExprState state = expressionStates[row][col];
        updateExpressionState(state);
        return compiledExpressions[row][col].eval(state);
        
    } catch (Exception e) {
        CirSim.console("TableElm: Error evaluating equation [" + row + "][" + col + "]: " + e.getMessage());
        return 0.0;
    }
}
    
    // Check if labeled nodes have changed and recompile equations if needed
    private void checkAndRecompileEquations() {
        // Get current labeled nodes
        String[] currentNodes = getSortedLabeledNodesArray();
        
        // Check if nodes have changed
        boolean nodesChanged = false;
        if (lastKnownNodes == null || lastKnownNodes.length != currentNodes.length) {
            nodesChanged = true;
        } else {
            for (int i = 0; i < currentNodes.length; i++) {
                if (!currentNodes[i].equals(lastKnownNodes[i])) {
                    nodesChanged = true;
                    break;
                }
            }
        }
        
        // If nodes changed, recompile all equations
        if (nodesChanged) {
            CirSim.console("TableElm: Labeled nodes changed, recompiling equations...");
            lastKnownNodes = currentNodes;
            recompileAllEquations();
        }
    }
    
    // Helper method to get current labeled nodes as array (uses cached method from LabeledNodeElm)
    private String[] getSortedLabeledNodesArray() {
        return LabeledNodeElm.getSortedLabeledNodeNames();
    }
        
    // Recompile all equations when labeled nodes change
    private void recompileAllEquations() {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (cellEquations[row][col] != null && !cellEquations[row][col].trim().isEmpty()) {
                    compileEquation(row, col, cellEquations[row][col]);
                }
            }
        }
    }
    
    private void updateExpressionState(ExprState state) {
        // Check if labeled nodes have changed and recompile if needed
        checkAndRecompileEquations();
        
        // Update state with current simulation time
        state.t = sim != null ? sim.t : 0.0;
        
        // Dynamically map actual labeled nodes to variables a-i
        if (sim != null && LabeledNodeElm.labelList != null && !LabeledNodeElm.labelList.isEmpty()) {
            String[] availableNodes = LabeledNodeElm.getSortedLabeledNodeNames(); // Use cached method
            
            // Map first 9 labeled nodes to variables a-i
            for (int i = 0; i < Math.min(availableNodes.length, state.values.length); i++) {
                state.values[i] = sim.getLabeledNodeVoltage(availableNodes[i]);
            }
            
            // Clear remaining variables if we have fewer than 9 nodes
            for (int i = availableNodes.length; i < state.values.length; i++) {
                state.values[i] = 0.0;
            }
        } else {
            // No labeled nodes available - clear all variables
            for (int i = 0; i < state.values.length; i++) {
                state.values[i] = 0.0;
            }
        }
    }
    
    private void compileEquation(int row, int col, String equation) {
        if (equation == null || equation.trim().isEmpty()) {
            compiledExpressions[row][col] = null;
            return;
        }
        
        try {
            ExprParser parser = new ExprParser(equation);
            compiledExpressions[row][col] = parser.parseExpression();
            String err = parser.gotError();
            if (err != null) {
                // Provide helpful error message with available variables
                String availableVars = getAvailableVariablesString();
                CirSim.console("TableElm: Parse error in equation [" + row + "][" + col + "]: " + equation + ": " + err);
                CirSim.console("TableElm: " + availableVars);
                compiledExpressions[row][col] = null;
                return;
            }
        } catch (Exception e) {
            String availableVars = getAvailableVariablesString();
            CirSim.console("TableElm: Exception parsing equation [" + row + "][" + col + "]: " + e.getMessage());
            CirSim.console("TableElm: " + availableVars);
            compiledExpressions[row][col] = null;
        }
    }
    
    // Helper method to show which variables are available for equations
    private String getAvailableVariablesString() {
        StringBuilder sb = new StringBuilder();
        String[] availableNodes = LabeledNodeElm.getSortedLabeledNodeNames(); // Use cached method
        
        sb.append("Available: t (time)");
        for (int i = 0; i < Math.min(availableNodes.length, 9); i++) {
            char varName = (char)('a' + i);
            sb.append(", ").append(varName).append("/").append(availableNodes[i]);
        }
        if (availableNodes.length > 9) {
            sb.append(" (only first 9 nodes supported)");
        }
        if (availableNodes.length == 0) {
            sb.append(", no labeled nodes in circuit");
        }
        
        return sb.toString();
    }
    
    // Public methods for managing equations
    public void setCellEquation(int row, int col, String equation) {
        if (row >= 0 && row < rows && col >= 0 && col < cols) {
            cellEquations[row][col] = equation != null ? equation : "";
            compileEquation(row, col, cellEquations[row][col]);
        }
    }
    
    public String getCellEquation(int row, int col) {
        if (row >= 0 && row < rows && col >= 0 && col < cols) {
            return cellEquations[row][col];
        }
        return "";
    }
    
    public void setCellMode(int row, int col, boolean equationMode) {
        // Remove - no longer needed since all cells are equation mode
    }   
    protected void registerComputedValueAsLabeledNode(String labelName, double voltage) {
        if (labelName == null || labelName.isEmpty()) {
            return;
        }
        
        // Store the computed voltage value in LabeledNodeElm
        LabeledNodeElm.setComputedValue(labelName, voltage);
    }
    
    // Static method to get computed values by other elements
    public static Double getComputedValue(String labelName) {
        return LabeledNodeElm.getComputedValue(labelName);
    }
    
    void setPoints() {
        super.setPoints();
        
        // Calculate table dimensions
        int tableWidth = cols * cellSize + (cols + 1) * cellSpacing;
        int extraRows = showComputedRow ? 1 : 0; // Add extra row for computed values
        int tableHeight = (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20; // Extra space for headers
        
        // Set bounding box
        setBbox(point1.x, point1.y, point1.x + tableWidth, point1.y + tableHeight);
    }

    int getPostCount() { 
        return 0; // No electrical connections
    }
    
    void draw(Graphics g) {
        int extraRows = showComputedRow ? 1 : 0;
        
        // Draw table background
        g.setColor(needsHighlight() ? selectColor : Color.white);
        g.fillRect(point1.x, point1.y, 
                   cols * cellSize + (cols + 1) * cellSpacing,
                   (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20);
        
        // Draw table border
        g.setColor(Color.black);
        g.drawRect(point1.x, point1.y,
                   cols * cellSize + (cols + 1) * cellSpacing,
                   (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20);
        
        // Draw column headers
        drawColumnHeaders(g);
        
        // Draw table cells with voltages
        drawTableCells(g);
        
        // Draw computed row if enabled
        if (showComputedRow) {
            drawSumRow(g);
        }
        
        // Draw grid lines
        drawGridLines(g);
    }

    private void drawColumnHeaders(Graphics g) {
        g.setColor(Color.black);
        int headerY = point1.y + 15;
        
        for (int col = 0; col < cols; col++) {
            int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
            String header = (columnHeaders != null && col < columnHeaders.length) ? 
                           columnHeaders[col] : "Col" + (col + 1);
            drawCenteredText(g, header, cellX + cellSize/2, headerY, true);

        }
    }

    private void drawTableCells(Graphics g) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
                int cellY = point1.y + 20 + cellSpacing + row * (cellSize + cellSpacing);
                
                // Get voltage using equation evaluation
                double voltage = getVoltageForCell(row, col);
                
                // Draw cell background - light blue for all equation cells
                g.setColor(needsHighlight() ? selectColor : new Color(240, 248, 255));
                g.fillRect(cellX, cellY, cellSize, cellSize);
                
                // Draw cell border
                g.setColor(Color.black);
                g.drawRect(cellX, cellY, cellSize, cellSize);
                
                // Draw equation (top half)
                g.setColor(Color.black);
                String displayText = cellEquations[row][col];
                if (displayText.length() > 8) {
                    displayText = displayText.substring(0, 6) + ".."; // Truncate long equations
                }
                drawCenteredText(g, displayText, cellX + cellSize/2, cellY + cellSize/3, true);
                
                // Draw voltage value (bottom half)
                String voltageText = getVoltageText(voltage);
                drawCenteredText(g, voltageText, cellX + cellSize/2, cellY + 2*cellSize/3, true);
            }
        }
    }

    protected void drawSumRow(Graphics g) {
        int sumRowY = point1.y + 20 + cellSpacing + rows * (cellSize + cellSpacing);
        
        for (int col = 0; col < cols; col++) {
            int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
            
            // Get the already-calculated sum from computed values (calculated in doStep())
            String sumLabelName = columnHeaders[col];
            Double computedSum = LabeledNodeElm.getComputedValue(sumLabelName);
            double columnSum = (computedSum != null) ? computedSum.doubleValue() : 0.0;
            
            // Draw sum cell background (slightly different color)
            g.setColor(Color.lightGray);
            g.fillRect(cellX, sumRowY, cellSize, cellSize);
            
            // Draw cell border
            g.setColor(Color.black);
            g.drawRect(cellX, sumRowY, cellSize, cellSize);
            
            // Draw column header text as label (top half)
            g.setColor(Color.black);
            drawCenteredText(g, sumLabelName, cellX + cellSize/2, sumRowY + cellSize/3, true);
            
            // Draw sum value (bottom half)
            String sumText = getVoltageText(columnSum);
            drawCenteredText(g, sumText, cellX + cellSize/2, sumRowY + 2*cellSize/3, true);
        }
    }

    private void drawGridLines(Graphics g) {
        g.setColor(Color.gray);
        int extraRows = showComputedRow ? 1 : 0;
        
        // Vertical lines
        for (int col = 0; col <= cols; col++) {
            int x = point1.x + cellSpacing + col * (cellSize + cellSpacing);
            g.drawLine(x, point1.y + 20, x, point1.y + 20 + cellSpacing + (rows + extraRows) * (cellSize + cellSpacing));
        }
        
        // Horizontal lines
        for (int row = 0; row <= rows + extraRows; row++) {
            int y = point1.y + 20 + cellSpacing + row * (cellSize + cellSpacing);
            g.drawLine(point1.x, y, point1.x + cellSpacing + cols * (cellSize + cellSpacing), y);
        }
        
        // Draw separator line before computed row if showing it
        if (showComputedRow) {
            g.setColor(Color.black);
            int separatorY = point1.y + 20 + cellSpacing + rows * (cellSize + cellSpacing);
            g.drawLine(point1.x, separatorY, point1.x + cellSpacing + cols * (cellSize + cellSpacing), separatorY);
        }
    }
    
    private double[] lastColumnSums;
    // Calculate computed values during simulation step (not during drawing)
    public void doStep() {
        super.doStep();
        
        if (showComputedRow) {
            if (lastColumnSums == null) {
                lastColumnSums = new double[cols];
            }
            
            for (int col = 0; col < cols; col++) {
                double columnSum = 0.0;
                for (int row = 0; row < rows; row++) {
                    // Use getVoltageForCell to support equations
                    columnSum += getVoltageForCell(row, col);
                }
                
                // Check for convergence
                if (Math.abs(columnSum - lastColumnSums[col]) > 1e-6) {
                    sim.converged = false;
                }
                
                lastColumnSums[col] = columnSum;
                String name = columnHeaders[col];
                registerComputedValueAsLabeledNode(name, columnSum);
            }
        }
    }

    boolean nonLinear() { 
        // Always nonlinear since we use equations (which may be nonlinear)
        return true;
    }
    
    // No electrical connections - pure display element
    boolean isWireEquivalent() { return false; }
    boolean isRemovableWire() { return false; }
    
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.dump()).append(" ").append(rows).append(" ").append(cols);
        sb.append(" ").append(showComputedRow ? "1" : "0");
        
        // Serialize column headers
        for (int col = 0; col < cols; col++) {
            sb.append(" ").append(CustomLogicModel.escape(columnHeaders[col]));
        }
        
        // Serialize equation data only
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                String equation = (cellEquations[row][col] != null) ? cellEquations[row][col] : "";
                sb.append(" ").append(CustomLogicModel.escape(equation));
            }
        }
        
        return sb.toString();
    }

    protected void parseTableData(StringTokenizer st) {
        try {
            if (st.hasMoreTokens()) rows = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens()) cols = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens()) showComputedRow = "1".equals(st.nextToken());
            
            // Initialize arrays
            columnHeaders = new String[cols];
            cellEquations = new String[rows][cols];
            
            // Parse column headers
            for (int col = 0; col < cols; col++) {
                if (st.hasMoreTokens()) {
                    columnHeaders[col] = CustomLogicModel.unescape(st.nextToken());
                } else {
                    columnHeaders[col] = "Col" + (col + 1);
                }
            }
            
            // Parse equation data
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    if (st.hasMoreTokens()) {
                        cellEquations[row][col] = CustomLogicModel.unescape(st.nextToken());
                    } else {
                        cellEquations[row][col] = "node" + (row * cols + col + 1);
                    }
                }
            }
            
            CirSim.console("TableElm: Successfully parsed table data with equations");
            
        } catch (Exception e) {
            CirSim.console("TableElm: Error parsing table data: " + e.getMessage());
            // Initialize with defaults on error
            if (columnHeaders == null) {
                columnHeaders = new String[cols];
                for (int col = 0; col < cols; col++) {
                    columnHeaders[col] = "Col" + (col + 1);
                }
            }
            if (cellEquations == null) {
                cellEquations = new String[rows][cols];
                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < cols; col++) {
                        cellEquations[row][col] = "node" + (row * cols + col + 1);
                    }
                }
            }
        }
    }

    int getDumpType() { 
        return 253; // Choose unused dump type
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) return new EditInfo("Cell Size", cellSize, 20, 100);
        if (n == 1) return new EditInfo("Cell Spacing", cellSpacing, 2, 20);
        if (n == 2) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Show Computed Row", showComputedRow);
            return ei;
        }
        if (n == 3) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.button = new Button("Edit Table Data...");
            return ei;
        }
        
        return null;
    }

    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            cellSize = (int)ei.value;
            setPoints();
        } else if (n == 1) {
            cellSpacing = (int)ei.value;
            setPoints();
        } else if (n == 2) {
            showComputedRow = ei.checkbox.getState();
            setPoints();
        } else if (n == 3) {
            // Open table editing dialog
            openTableEditDialog();
        }
    }

    // Resize table method for use by TableEditDialog
    public void resizeTable(int newRows, int newCols) {
        String[] oldHeaders = columnHeaders;
        String[][] oldEquations = cellEquations;
        
        // Update dimensions
        rows = newRows;
        cols = newCols;
        
        // Create new arrays
        columnHeaders = new String[cols];
        cellEquations = new String[rows][cols];
        compiledExpressions = new Expr[rows][cols];
        expressionStates = new ExprState[rows][cols];
        
        // Initialize expression states
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                expressionStates[row][col] = new ExprState(9);
            }
        }
        
        // Copy over existing data where possible
        if (oldEquations != null) {
            for (int row = 0; row < Math.min(rows, oldEquations.length); row++) {
                for (int col = 0; col < Math.min(cols, oldEquations[row].length); col++) {
                    cellEquations[row][col] = oldEquations[row][col];
                    // Recompile equations for copied cells
                    if (cellEquations[row][col] != null && !cellEquations[row][col].isEmpty()) {
                        compileEquation(row, col, cellEquations[row][col]);
                    }
                }
            }
        }
        
        // Copy over existing headers where possible  
        if (oldHeaders != null) {
            for (int col = 0; col < Math.min(cols, oldHeaders.length); col++) {
                columnHeaders[col] = oldHeaders[col];
            }
        }
        
        // Fill in missing labels with simple defaults
        fillMissingLabels();
        
        setPoints();
    }
    
    private void fillMissingLabels() {
        // Fill missing cell equations with simple node names
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (cellEquations[row][col] == null || cellEquations[row][col].isEmpty()) {
                    cellEquations[row][col] = "node" + (row * cols + col + 1);
                }
            }
        }
        
        // Fill missing column headers with simple defaults
        for (int col = 0; col < cols; col++) {
            if (columnHeaders[col] == null || columnHeaders[col].isEmpty()) {
                columnHeaders[col] = "Col" + (col + 1);
            }
        }
    }
    
    protected void openTableEditDialog() {
        // Open the enhanced table editing dialog
        if (sim != null) {
            TableEditDialog dialog = new TableEditDialog(this, sim);
            dialog.show();
        }
    }
    
    void getInfo(String arr[]) {
        arr[0] = "Equation Table (" + rows + "x" + cols + ")";
        arr[1] = "All cells use equations (node names or expressions)";
        
        int idx = 2;
        
        // Show some sample values
        for (int row = 0; row < Math.min(3, rows) && idx < arr.length - 1; row++) {
            for (int col = 0; col < Math.min(2, cols) && idx < arr.length - 1; col++) {
                double voltage = getVoltageForCell(row, col);
                String equation = cellEquations[row][col];
                if (equation.length() > 15) equation = equation.substring(0, 12) + "...";
                arr[idx++] = "=" + equation + ": " + getVoltageText(voltage);
            }
        }
        
        if (rows * cols > 6) {
            arr[idx++] = "... (showing first few cells)";
        }
    }
    
    public void setCellLabel(int row, int col, String label) {
        // Remove - no longer applicable since labels are now equations
    }

    public String getCellLabel(int row, int col) {
        if (row >= 0 && row < rows && col >= 0 && col < cols) {
            return cellEquations[row][col];
        }
        return "";
    }

    public void setColumnHeader(int col, String header) {
        if (col >= 0 && col < cols) {
            columnHeaders[col] = header;
        }
    }

    public String getColumnHeader(int col) {
        if (col >= 0 && col < cols) {
            return columnHeaders[col];
        }
        return "";
    }
    
    // Getter methods for TableEditDialog
    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public boolean getShowComputedRow() { return showComputedRow; }
    
    public void setShowComputedRow(boolean show) {
        showComputedRow = show;
        setPoints();
    }
    
    // Additional methods for equation support in TableEditDialog
    public void toggleCellMode(int row, int col) {
        // Remove - no longer needed since all cells are equation mode
    }
    
    public String getCellDisplayText(int row, int col) {
        if (row >= 0 && row < rows && col >= 0 && col < cols) {
            return cellEquations[row][col];
        }
        return "";
    }
    
    public void setCellDisplayText(int row, int col, String text) {
        if (row >= 0 && row < rows && col >= 0 && col < cols) {
            setCellEquation(row, col, text);
        }
    }
    
    public double getCellVoltage(int row, int col) {
        return getVoltageForCell(row, col);
    }

    // Add a method to be called when the circuit is fully loaded
    public void circuitLoaded() {
        // Recompile all equations now that the circuit and labeled nodes should be available
        CirSim.console("TableElm: Circuit loaded, recompiling all equations...");
        recompileAllEquations();
    }
    
    @Override
    public void reset() {
        super.reset();
        // Clear cached node list so equations get recompiled on next evaluation
        lastKnownNodes = null;
    }
    
    // // Override doStep to ensure equations are compiled before first evaluation
    // @Override
    // public void doStep() {
    //     // Check if we have any uncompiled equations that need compilation now
    //     boolean hasUncompiledEquations = false;
    //     for (int row = 0; row < rows && !hasUncompiledEquations; row++) {
    //         for (int col = 0; col < cols && !hasUncompiledEquations; col++) {
    //             if (cellEquations[row][col] != null && !cellEquations[row][col].trim().isEmpty() &&
    //                 compiledExpressions[row][col] == null) {
    //                 hasUncompiledEquations = true;
    //             }
    //         }
    //     }
        
    //     // If we have uncompiled equations and labeled nodes are now available, recompile
    //     if (hasUncompiledEquations && LabeledNodeElm.labelList != null && !LabeledNodeElm.labelList.isEmpty()) {
    //         CirSim.console("TableElm: Found uncompiled equations with available nodes, recompiling...");
    //         recompileAllEquations();
    //     }
        
    //     super.doStep();
    // }
}

