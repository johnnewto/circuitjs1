/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.Button;

/**
 * Simplified Table Element - Displays voltage values from equations
 * Extends ChipElm to provide output pins at each column
 */
public class TableElm extends ChipElm {
    protected int rows = 3;
    protected int cols = 2;
    protected int cellSize = 64;  // Default to 4*cspc (cspc=16 when size=2, so 4*16=64)
    protected int cellSpacing = 8;  // Default to 1*cspc (8 or 16 depending on chip size)
    protected double[] initialValues; // Values for initial conditions row
    protected boolean showInitialValues = false; // Control visibility of initial conditions row
    protected String[] outputNames; // Names for connecting outputs to labeled nodes (also used as column headers)
    protected String tableUnits = "$"; // Units to display in table cells ($ or V)
    protected int decimalPlaces = 2; // Number of decimal places to show
    
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
        noDiagonal = true;
        initTable();
        setupPins();
        setSize(sim.smallGridCheckItem.getState() ? 1 : 2);
    }

    // File loading constructor
    public TableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        noDiagonal = true;
        parseTableData(st);
        initTable();
        setupPins();
        setSize((f & FLAG_SMALL) != 0 ? 1 : 2);
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
//            CirSim.console("TableElm: Initialized cellEquations array");
        }
        
        if (compiledExpressions == null) {
            compiledExpressions = new Expr[rows][cols];
//            CirSim.console("TableElm: Initialized compiledExpressions array");
        }
        
        if (expressionStates == null) {
            expressionStates = new ExprState[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    expressionStates[row][col] = new ExprState(1); // Only need time variable
                }
            }
        }
        
        // Initialize output names if not set
        if (outputNames == null) {
            outputNames = new String[cols];
            for (int i = 0; i < cols; i++) {
                outputNames[i] = "Col" + (i + 1);
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
            if (outputNames[i] == null || outputNames[i].trim().isEmpty()) {
                outputNames[i] = "Col" + (i + 1);
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
            // Evaluate the compiled expression
            ExprState state = expressionStates[row][col];
            updateExpressionState(state);
            return compiledExpressions[row][col].eval(state);

        }
        return 0.0;
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
    
    // Custom method to format values with table units and decimal places
    protected String getTableFormattedText(double value) {
        // Format the number to the specified decimal places using GWT-compatible approach
        double multiplier = Math.pow(10, decimalPlaces);
        double rounded = Math.round(value * multiplier) / multiplier;
        String formattedValue = Double.toString(rounded);
        
        // Ensure we have the right number of decimal places
        if (formattedValue.indexOf('.') == -1) {
            formattedValue += ".";
        }
        
        int dotIndex = formattedValue.indexOf('.');
        int currentDecimals = formattedValue.length() - dotIndex - 1;
        
        // Pad with zeros if needed
        while (currentDecimals < decimalPlaces) {
            formattedValue += "0";
            currentDecimals++;
        }
        
        // Truncate if too many decimals
        if (currentDecimals > decimalPlaces) {
            formattedValue = formattedValue.substring(0, dotIndex + decimalPlaces + 1);
        }
        
        return formattedValue + tableUnits;
    }
    
    // Use the standard CircuitJS1 voltage coloring system
    protected Color getCellVoltageColor(double voltage) {
        // Use the same voltage coloring as other circuit elements
        // This properly handles zero voltage with gray and uses the global voltage range setting
        return getVoltageColor(null, voltage);
    }
    
    @Override
    void setupPins() {
        // Set chip size based on table dimensions
        // Calculate size to match table cell layout better
        int tableWidth = cols * cellSize + (cols + 1) * cellSpacing;
        int extraRows = (showInitialValues ? 1 : 0) + 1;
        int tableHeight = (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20;

        // Convert table dimensions to chip grid units (cspc2 = 16 pixels typically)
        // Add extra size so pins appear at the bottom of the table
        sizeX = Math.max(cols, (tableWidth / cspc2) + 1);
        sizeY = Math.max(rows + extraRows, (tableHeight / cspc2) + 1);

        // Create output pins on bottom edge, positioned to align with table columns
        pins = new Pin[cols];
        for (int i = 0; i < cols; i++) {
            String label = (outputNames != null && i < outputNames.length) ?
                          outputNames[i] : "Col" + (i + 1);

            // Calculate pin position to align with center of each column
            // Each column center is at: cellSpacing + col*(cellSize+cellSpacing) + cellSize/2
            // Subtract cspc (chip padding) to account for ChipElm's coordinate offset
            int columnCenterPixel = cellSpacing / 2 + i * (cellSize + cellSpacing) + cellSize / 2  - cspc;
            // Convert to chip grid units
            int pinPos = columnCenterPixel / cspc2;

            pins[i] = new Pin(pinPos, SIDE_S, label);
            pins[i].output = true;
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

        // Calculate table dimensions based on chip position
        int x0 = x + cspc2;
        int y0 = y;
        int xr = x0 - cspc;
        int yr = y0 - cspc;

        int tableWidth = cols * cellSize + (cols + 1) * cellSpacing;
        int extraRows = (showInitialValues ? 1 : 0) + 1; // Initial conditions (if shown) + computed row
        int tableHeight = (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20; // Extra space for headers

        // Set bounding box to include both chip body and table
        setBbox(xr, yr, xr + tableWidth, yr + tableHeight);
    }
    
    // Helper method to get table origin coordinates
    private int getTableX() {
        int x0 = x + cspc2;
        return x0 - cspc;
    }

    private int getTableY() {
        int y0 = y;
        return y0 - cspc;
    }

    @Override
    void draw(Graphics g) {
        int extraRows = (showInitialValues ? 1 : 0) + 1; // Initial conditions (if shown) + computed row
        int tableX = getTableX();
        int tableY = getTableY();

        // Draw table background
        g.setColor(needsHighlight() ? selectColor : Color.white);
        g.fillRect(tableX, tableY,
                   cols * cellSize + (cols + 1) * cellSpacing,
                   (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20);

        // Draw table border
        g.setColor(Color.black);
        g.drawRect(tableX, tableY,
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

        // Draw chip pins and posts at the bottom
        drawPins(g);
    }

    private void drawPins(Graphics g) {
        // Draw pins on the bottom edge
        for (int i = 0; i < getPostCount(); i++) {
            Pin p = pins[i];
            setVoltageColor(g, volts[i]);
            Point a = p.post;
            Point b = p.stub;
            drawThickLine(g, a, b);
            p.curcount = updateDotCount(p.current, p.curcount);
            drawDots(g, b, a, p.curcount);
        }
        drawPosts(g);
    }

    private void drawColumnHeaders(Graphics g) {
        g.setColor(Color.black);
        int tableX = getTableX();
        int tableY = getTableY();
        int headerY = tableY + 15;

        for (int col = 0; col < cols; col++) {
            int cellX = tableX + cellSpacing + col * (cellSize + cellSpacing);
            String header = (outputNames != null && col < outputNames.length) ?
                           outputNames[col] : "Col" + (col + 1);
            drawCenteredText(g, header, cellX + cellSize/2, headerY, true);

        }
    }

    private void drawInitialConditionsRow(Graphics g) {
        int tableX = getTableX();
        int tableY = getTableY();
        int initialRowY = tableY + 20 + cellSpacing; // First row after headers

        for (int col = 0; col < cols; col++) {
            int cellX = tableX + cellSpacing + col * (cellSize + cellSpacing);
            
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
            String voltageText = getTableFormattedText(initialValue);
            drawCenteredText(g, voltageText, cellX + cellSize/2, initialRowY + 2*cellSize/3, true);
        }
    }

    private void drawTableCells(Graphics g) {
        int tableX = getTableX();
        int tableY = getTableY();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int cellX = tableX + cellSpacing + col * (cellSize + cellSpacing);
                // Adjust Y position to account for initial conditions row (if shown)
                int rowOffset = showInitialValues ? 1 : 0; // Initial conditions row offset
                int cellY = tableY + 20 + cellSpacing + (row + rowOffset) * (cellSize + cellSpacing);
                
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
                String voltageText = getTableFormattedText(voltage);
                drawCenteredText(g, voltageText, cellX + cellSize/2, cellY + 2*cellSize/3, true);
            }
        }
    }

    protected void drawSumRow(Graphics g) {
        int tableX = getTableX();
        int tableY = getTableY();

        // Adjust Y position to account for initial conditions row (if shown)
        int rowOffset = showInitialValues ? 1 : 0; // Initial conditions row offset
        int sumRowY = tableY + 20 + cellSpacing + (rows + rowOffset) * (cellSize + cellSpacing);

        for (int col = 0; col < cols; col++) {
            int cellX = tableX + cellSpacing + col * (cellSize + cellSpacing);
            
            // Get the already-calculated sum from computed values (calculated in doStep())
            String sumLabelName = outputNames[col];
            Double computedSum = ComputedValues.getComputedValue(sumLabelName);
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
            String sumText = getTableFormattedText(computedValue);
            drawCenteredText(g, sumText, cellX + cellSize/2, sumRowY + 2*cellSize/3, true);
        }
    }

    private void drawGridLines(Graphics g) {
        g.setColor(Color.gray);
        int tableX = getTableX();
        int tableY = getTableY();
        int extraRows = (showInitialValues ? 1 : 0) + 1; // Initial conditions (if shown) + computed row

        // Vertical lines
        for (int col = 0; col <= cols; col++) {
            int x = tableX + cellSpacing + col * (cellSize + cellSpacing);
            g.drawLine(x, tableY + 20, x, tableY + 20 + cellSpacing + (rows + extraRows) * (cellSize + cellSpacing));
        }

        // Horizontal lines
        for (int row = 0; row <= rows + extraRows; row++) {
            int y = tableY + 20 + cellSpacing + row * (cellSize + cellSpacing);
            g.drawLine(tableX, y, tableX + cellSpacing + cols * (cellSize + cellSpacing), y);
        }

        // Draw separator line after initial conditions row (if shown)
        if (showInitialValues) {
            g.setColor(Color.black);
            int separatorY = tableY + 20 + cellSpacing + 1 * (cellSize + cellSpacing);
            g.drawLine(tableX, separatorY, tableX + cellSpacing + cols * (cellSize + cellSpacing), separatorY);
        }

        // Draw separator line before computed row
        g.setColor(Color.black);
        int rowOffset = showInitialValues ? 1 : 0; // Initial conditions row offset
        int separatorY = tableY + 20 + cellSpacing + (rows + rowOffset) * (cellSize + cellSpacing);
        g.drawLine(tableX, separatorY, tableX + cellSpacing + cols * (cellSize + cellSpacing), separatorY);
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
                        } else {
                            int nodeListSize = (sim.nodeList != null) ? sim.nodeList.size() : -1;
                            //CirSim.console("TableElm: ERROR - Invalid node numbers: outputNode=" + outputNode + ", labeledNodeNum=" + labeledNodeNum + ", nodeList.size=" + nodeListSize);
                        }
                    } else {
//                        CirSim.console("TableElm: No labeled node found for output name '" + outputName + "'");
                        ;
                    }
                }
            }
        }
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.dump()).append(" ").append(rows).append(" ").append(cols).append(" ").append(showInitialValues);
        sb.append(" ").append(CustomLogicModel.escape(tableUnits)).append(" ").append(decimalPlaces);
        
        // Serialize output names (used as column headers)
        for (int col = 0; col < cols; col++) {
            sb.append(" ").append(CustomLogicModel.escape(outputNames[col]));
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
            outputNames = new String[cols];
            cellEquations = new String[rows][cols];
            initialValues = new double[cols];
            
            // Auto-detect format based on total tokens expected
            // Count remaining tokens to determine format
            int remainingTokens = st.countTokens();
            int expectedNewestFormat = 1 + 2 + cols + cols + (rows * cols); // showInitialValues + tableUnits + decimalPlaces + output names + initial conditions + equations
            int expectedNewFormatWithFlag = 1 + cols + cols + (rows * cols); // showInitialValues + output names (headers) + initial conditions + equations
            int expectedOldFormatWithFlags = 2 + cols + cols + (rows * cols); // 2 old boolean flags + headers + initial conditions + equations
            // Note: expectedOldFormat = cols + cols + (rows * cols) for very old format (no flags)
            
            // Initialize defaults
            showInitialValues = false;
            tableUnits = "$"; // Default to $ for new tables
            decimalPlaces = 2; // Default to 2 decimal places
            
            // Detect format and parse accordingly
            if (remainingTokens == expectedNewestFormat) {
                // Newest format with all fields
                if (st.hasMoreTokens()) {
                    showInitialValues = Boolean.parseBoolean(st.nextToken());
                }
                if (st.hasMoreTokens()) {
                    tableUnits = CustomLogicModel.unescape(st.nextToken());
                }
                if (st.hasMoreTokens()) {
                    decimalPlaces = Integer.parseInt(st.nextToken());
                }
            } else if (remainingTokens == expectedNewFormatWithFlag) {
                // New format with showInitialValues flag only (backwards compatibility)
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
            
            // Parse output names (used as column headers)
            for (int col = 0; col < cols; col++) {
                if (st.hasMoreTokens()) {
                    outputNames[col] = CustomLogicModel.unescape(st.nextToken());
                } else {
                    outputNames[col] = "Col" + (col + 1);
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

            // Output names are already set from column headers parsing above
            
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
            if (outputNames == null) {
                outputNames = new String[cols];
                for (int col = 0; col < cols; col++) {
                    outputNames[col] = "Col" + (col + 1);
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
        // Cell Size and Cell Spacing should be in steps of cspc for proper alignment
        if (n == 0) return new EditInfo("Cell Size (multiples of " + cspc + ")", cellSize, cspc, 200);
        if (n == 1) return new EditInfo("Cell Spacing (multiples of " + cspc + ")", cellSpacing, cspc, 64);
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
            // Round to nearest multiple of cspc
            cellSize = Math.max(cspc, ((int)ei.value / cspc) * cspc);
            setupPins(); // Recalculate pin positions
            setPoints();
        } else if (n == 1) {
            // Round to nearest multiple of cspc
            cellSpacing = Math.max(cspc, ((int)ei.value / cspc) * cspc);
            setupPins(); // Recalculate pin positions
            setPoints();
        } else if (n == 2) {
            showInitialValues = ei.checkbox.getValue();
            setupPins(); // Recalculate chip size and pin positions
            setPoints();
        } else if (n == 3) {
            // Open table editing dialog
            openTableEditDialog();
        }
    }

    // Resize table method for use by TableEditDialog
    public void resizeTable(int newRows, int newCols) {
        String[] oldOutputNames = outputNames;
        String[][] oldEquations = cellEquations;
        double[] oldInitialConditions = initialValues;

        // Update dimensions
        rows = newRows;
        cols = newCols;

        // Create new arrays
        outputNames = new String[cols];
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

        // Copy over existing output names where possible (already handled below)

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

        // Copy over existing output names where possible
        if (oldOutputNames != null) {
            for (int col = 0; col < Math.min(cols, oldOutputNames.length); col++) {
                outputNames[col] = oldOutputNames[col];
            }
        }

        // Initialize remaining output names with empty strings
        for (int col = 0; col < cols; col++) {
            if (oldOutputNames == null || col >= oldOutputNames.length) {
                outputNames[col] = "";
            }
        }

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
            arr[idx++] = header + " = " + getTableFormattedText(output);
        }

        if (cols > 3 && idx < arr.length - 1) {
            arr[idx++] = "... (" + cols + " outputs total)";
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


