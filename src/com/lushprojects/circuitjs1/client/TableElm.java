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
    protected double[] initialValues; // Values for initial conditions row
    protected boolean showInitialValues = false; // Control visibility of initial conditions row
    
    // All cells now use equations
    // Variables available: a-i map to labeled nodes OR use labeled node names directly
    // Example equations: "a+b", "node1+node2", "sin(t)", "max(a,b,c)", "vcc>2.5?5:0"
    String[][] cellEquations;     // Equation strings for each cell
    Expr[][] compiledExpressions; // Compiled expressions for evaluation
    ExprState[][] expressionStates; // Expression evaluation state for each cell
    
    // Note: No need to track labeled nodes anymore with direct resolution
    
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
                    cellEquations[row][col] = "";
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
                    expressionStates[row][col] = new ExprState(1); // Only need time variable
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
        
        // Initialize initial conditions values if not set
        if (initialValues == null) {
            initialValues = new double[cols];
            // Initialize with zero values
            for (int i = 0; i < cols; i++) {
                initialValues[i] = 0.0;
            }
        }
        
        // Ensure no null or empty values exist
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (cellEquations[row][col] == null || cellEquations[row][col].trim().isEmpty()) {
                    cellEquations[row][col] = "";
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
    
    private boolean isValidCell(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }
    
    // Enhanced version that evaluates equations for cell values
    protected double getVoltageForCell(int row, int col) {
        if (!isValidCell(row, col)) {
            return 0.0;
        }
        
        // All cells now use equations
        if (compiledExpressions[row][col] != null) {
            return evaluateEquation(row, col);
        }
        
        // If no compiled expression, try to evaluate as simple node name
        String equation = cellEquations[row][col];
        if (equation != null && !equation.trim().isEmpty()) {
            // for debug if equation == "Lend'"


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
    
    // // Check if labeled nodes have changed and recompile equations if needed
    // private void checkAndRecompileEquations() {
    //     // Get current labeled nodes
    //     String[] currentNodes = getSortedLabeledNodesArray();
        
    //     // Check if nodes have changed
    //     boolean nodesChanged = false;
    //     if (lastKnownNodes == null || lastKnownNodes.length != currentNodes.length) {
    //         nodesChanged = true;
    //     } else {
    //         for (int i = 0; i < currentNodes.length; i++) {
    //             if (!currentNodes[i].equals(lastKnownNodes[i])) {
    //                 nodesChanged = true;
    //                 break;
    //             }
    //         }
    //     }
        
    //     // If nodes changed, recompile all equations
    //     if (nodesChanged) {
    //         CirSim.console("TableElm: Labeled nodes changed, recompiling equations...");
    //         lastKnownNodes = currentNodes;
    //         recompileAllEquations();
    //     }
    // }
    

        
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
        // Only update time - direct node resolution handles everything else
        state.t = sim != null ? sim.t : 0.0;
        
        // All node references are resolved directly in Expr.eval() via E_NODE_REF
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
    // Update helper method documentation
    private String getAvailableVariablesString() {
        StringBuilder sb = new StringBuilder();
        String[] availableNodes = LabeledNodeElm.getSortedLabeledNodeNames();
        
        sb.append("Available: t (time)");
        
        // Show direct node names (only method now)
        for (String nodeName : availableNodes) {
            sb.append(", ").append(nodeName);
        }
        
        if (availableNodes.length == 0) {
            sb.append(", no labeled nodes in circuit");
        }
        
        return sb.toString();
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
        
        // Store the computed voltage value in LabeledNodeElm
        LabeledNodeElm.setComputedValue(labelName, voltage);
    }
    
    // Static method to get computed values by other elements
    public static Double getComputedValue(String labelName) {
        return LabeledNodeElm.getComputedValue(labelName);
    }
    
    // Use the standard CircuitJS1 voltage coloring system
    protected Color getCellVoltageColor(double voltage) {
        // Use the same voltage coloring as other circuit elements
        // This properly handles zero voltage with gray and uses the global voltage range setting
        return getVoltageColor(null, voltage);
    }
    
    void setPoints() {
        super.setPoints();
        
        // Calculate table dimensions
        int tableWidth = cols * cellSize + (cols + 1) * cellSpacing;
        int extraRows = (showInitialValues ? 1 : 0) + 1; // Initial conditions (if shown) + computed row
        int tableHeight = (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20; // Extra space for headers
        
        // Set bounding box
        setBbox(point1.x, point1.y, point1.x + tableWidth, point1.y + tableHeight);
    }

    int getPostCount() { 
        return 0; // No electrical connections
    }
    
    void draw(Graphics g) {
        int extraRows = (showInitialValues ? 1 : 0) + 1; // Initial conditions (if shown) + computed row
        
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
        
        // Draw initial conditions row if enabled
        if (showInitialValues) {
            drawInitialConditionsRow(g);
        }
        
        // Draw table cells with voltages
        drawTableCells(g);
        
        // Always draw computed row
        drawSumRow(g);
        
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

    private void drawInitialConditionsRow(Graphics g) {
        int initialRowY = point1.y + 20 + cellSpacing; // First row after headers
        
        for (int col = 0; col < cols; col++) {
            int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
            
            // Get initial conditions value for this column
            double initialValue = (initialValues != null && col < initialValues.length) ? 
                                 initialValues[col] : 0.0;
            
            // Draw initial conditions cell background - color based on initial value voltage
            g.setColor(needsHighlight() ? selectColor : getCellVoltageColor(initialValue));
            g.fillRect(cellX, initialRowY, cellSize, cellSize);
            
            // Draw cell border
            g.setColor(Color.black);
            g.drawRect(cellX, initialRowY, cellSize, cellSize);
            
            // Draw "Initial" label (top half)
            g.setColor(Color.black);
            drawCenteredText(g, "Initial", cellX + cellSize/2, initialRowY + cellSize/3, true);
            
            // Draw initial conditions voltage value (bottom half)
            String voltageText = getVoltageText(initialValue);
            drawCenteredText(g, voltageText, cellX + cellSize/2, initialRowY + 2*cellSize/3, true);
        }
    }

    private void drawTableCells(Graphics g) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
                // Adjust Y position to account for initial conditions row (if shown)
                int rowOffset = showInitialValues ? 1 : 0; // Initial conditions row offset
                int cellY = point1.y + 20 + cellSpacing + (row + rowOffset) * (cellSize + cellSpacing);
                
                // Get voltage using equation evaluation
                double voltage = getVoltageForCell(row, col);
                
                // Draw cell background - color based on voltage using CircuitJS1 standard colors
                g.setColor(needsHighlight() ? selectColor : getCellVoltageColor(voltage));
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
        // Adjust Y position to account for initial conditions row (if shown)
        int rowOffset = showInitialValues ? 1 : 0; // Initial conditions row offset
        int sumRowY = point1.y + 20 + cellSpacing + (rows + rowOffset) * (cellSize + cellSpacing);
        
        for (int col = 0; col < cols; col++) {
            int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
            
            // Get the already-calculated sum from computed values (calculated in doStep())
            String sumLabelName = columnHeaders[col];
            Double computedSum = LabeledNodeElm.getComputedValue(sumLabelName);
            double computedValue = (computedSum != null) ? computedSum.doubleValue() : 0.0;
            
            // Draw sum cell background - color based on computed sum voltage
            g.setColor(needsHighlight() ? selectColor : getCellVoltageColor(computedValue));
            g.fillRect(cellX, sumRowY, cellSize, cellSize);
            
            // Draw cell border
            g.setColor(Color.black);
            g.drawRect(cellX, sumRowY, cellSize, cellSize);
            
            // Draw column header text as label (top half)
            g.setColor(Color.black);
            drawCenteredText(g, sumLabelName, cellX + cellSize/2, sumRowY + cellSize/3, true);
            
            // Draw sum value (bottom half)
            String sumText = getVoltageText(computedValue);
            drawCenteredText(g, sumText, cellX + cellSize/2, sumRowY + 2*cellSize/3, true);
        }
    }

    private void drawGridLines(Graphics g) {
        g.setColor(Color.gray);
        int extraRows = (showInitialValues ? 1 : 0) + 1; // Initial conditions (if shown) + computed row
        
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
        
        // Draw separator line after initial conditions row (if shown)
        if (showInitialValues) {
            g.setColor(Color.black);
            int separatorY = point1.y + 20 + cellSpacing + 1 * (cellSize + cellSpacing);
            g.drawLine(point1.x, separatorY, point1.x + cellSpacing + cols * (cellSize + cellSpacing), separatorY);
        }
        
        // Draw separator line before computed row
        g.setColor(Color.black);
        int rowOffset = showInitialValues ? 1 : 0; // Initial conditions row offset
        int separatorY = point1.y + 20 + cellSpacing + (rows + rowOffset) * (cellSize + cellSpacing);
        g.drawLine(point1.x, separatorY, point1.x + cellSpacing + cols * (cellSize + cellSpacing), separatorY);
    }
    
    private double[] lastColumnSums;
   
    // Calculate computed values during simulation step (not during drawing)
    public void doStep() {
        super.doStep();
        
        // Always compute column sums
        if (lastColumnSums == null) {
            lastColumnSums = new double[cols];
        }
        

        
        for (int col = 0; col < cols; col++) {
            double columnSum = 0.0;
            
            // Add values from all equation rows except for the initial value row
            for (int row = 1; row < rows; row++) {
                // Use getVoltageForCell to support equations
                columnSum += getVoltageForCell(row, col);
            }
            
            // Check for convergence
            Double diff = Math.abs(columnSum - lastColumnSums[col]);
            if (diff > 1e-6) {
                sim.converged = false;
            }
            
            lastColumnSums[col] = columnSum;
            String name = columnHeaders[col];
            registerComputedValueAsLabeledNode(name, columnSum);
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
        sb.append(super.dump()).append(" ").append(rows).append(" ").append(cols).append(" ").append(showInitialValues);
        
        // Serialize column headers
        for (int col = 0; col < cols; col++) {
            sb.append(" ").append(CustomLogicModel.escape(columnHeaders[col]));
        }
        
        // Always serialize initial conditions values
        if (initialValues != null) {
            for (int col = 0; col < cols; col++) {
                sb.append(" ").append(initialValues[col]);
            }
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
            
            // Initialize arrays
            columnHeaders = new String[cols];
            cellEquations = new String[rows][cols];
            initialValues = new double[cols];
            
            // Auto-detect format based on total tokens expected
            // Count remaining tokens to determine format
            int remainingTokens = st.countTokens();
            int expectedNewFormatWithFlag = 1 + cols + cols + (rows * cols); // showInitialValues + headers + initial conditions + equations
            int expectedOldFormatWithFlags = 2 + cols + cols + (rows * cols); // 2 old boolean flags + headers + initial conditions + equations
            // Note: expectedOldFormat = cols + cols + (rows * cols) for very old format (no flags)
            
            // Detect format and parse accordingly
            if (remainingTokens == expectedNewFormatWithFlag) {
                // New format with showInitialValues flag
                if (st.hasMoreTokens()) {
                    showInitialValues = Boolean.parseBoolean(st.nextToken());
                }
            } else if (remainingTokens == expectedOldFormatWithFlags) {
                // Old format with 2 boolean flags - skip them for backwards compatibility
                if (st.hasMoreTokens()) st.nextToken(); // Skip old showComputedRow flag
                if (st.hasMoreTokens()) st.nextToken(); // Skip old hasInitialConditions flag
                showInitialValues = false; // Default for old TableElm
            } else {
                // Very old format or new format without flag
                showInitialValues = false; // Default for TableElm
            }
            
            // Parse column headers
            for (int col = 0; col < cols; col++) {
                if (st.hasMoreTokens()) {
                    columnHeaders[col] = CustomLogicModel.unescape(st.nextToken());
                } else {
                    columnHeaders[col] = "Col" + (col + 1);
                }
            }
            
            // Parse initial conditions values
            // Store tokens we can't parse as numbers for equation parsing later
            java.util.ArrayList<String> unparsedTokens = new java.util.ArrayList<String>();
            
            for (int col = 0; col < cols; col++) {
                if (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    try {
                        initialValues[col] = Double.parseDouble(token);
                    } catch (NumberFormatException e) {
                        // This token is not a number, probably an equation
                        initialValues[col] = 0.0;
                        unparsedTokens.add(token);
                        // All remaining tokens for this section should also be treated as equations
                        break;
                    }
                } else {
                    initialValues[col] = 0.0;
                }
            }
            
            // Parse equation data
            // First use any unparsed tokens from initial conditions parsing
            int unparsedIndex = 0;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    if (unparsedIndex < unparsedTokens.size()) {
                        // Use unparsed token
                        cellEquations[row][col] = CustomLogicModel.unescape(unparsedTokens.get(unparsedIndex));
                        unparsedIndex++;
                    } else if (st.hasMoreTokens()) {
                        // Use token from stream
                        cellEquations[row][col] = CustomLogicModel.unescape(st.nextToken());
                    } else {
                        // Default equation
                        cellEquations[row][col] = "";
                    }
                }
            }
            
            CirSim.console("TableElm: Successfully parsed table data with equations and initial conditions");
            
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
                        cellEquations[row][col] = "";
                    }
                }
            }
            if (initialValues == null) {
                initialValues = new double[cols];
                for (int col = 0; col < cols; col++) {
                    initialValues[col] = 0.0;
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
            EditInfo ei = new EditInfo("Show Initial Values", 0, -1, -1);
            ei.checkbox = new Checkbox("", showInitialValues);
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
            showInitialValues = ei.checkbox.getValue();
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
        double[] oldInitialConditions = initialValues;
        
        // Update dimensions
        rows = newRows;
        cols = newCols;
        
        // Create new arrays
        columnHeaders = new String[cols];
        cellEquations = new String[rows][cols];
        compiledExpressions = new Expr[rows][cols];
        expressionStates = new ExprState[rows][cols];
        initialValues = new double[cols];
        
        // Initialize expression states
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                expressionStates[row][col] = new ExprState(1); // Only need time variable
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
        
        // Copy over existing initial conditions where possible
        if (oldInitialConditions != null) {
            for (int col = 0; col < Math.min(cols, oldInitialConditions.length); col++) {
                initialValues[col] = oldInitialConditions[col];
            }
        }
        
        // Initialize remaining initial conditions values with zeros
        for (int col = 0; col < cols; col++) {
            if (oldInitialConditions == null || col >= oldInitialConditions.length) {
                initialValues[col] = 0.0;
            }
        }

        setPoints();
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
    

    public String getCellLabel(int row, int col) {
        if (isValidCell(row, col)) {
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

    
    public String getCellDisplayText(int row, int col) {
        if (isValidCell(row, col)) {
            return cellEquations[row][col];
        }
        return "";
    }
    
    public void setCellDisplayText(int row, int col, String text) {
        if (isValidCell(row, col)) {
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
    

}


