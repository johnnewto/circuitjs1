/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.util.Locale;

/**
 * EquationTableElm - Table of Equations with Adjustable Sliders
 * 
 * This element displays a table of equations where each row:
 * 1. Has a custom output name that becomes a labeled node
 * 2. Has a user-defined equation that can reference labeled nodes
 * 3. Has a custom slider variable name and adjustable value
 * 4. Can be reordered using up/down buttons
 * 
 * Features:
 * - Multiple output pins (one per row), hidden like TableElm
 * - Each row's output becomes a labeled node accessible by other elements
 * - Custom slider variable per row (e.g., "rate", "s")
 * - Row movement via inline ⇑/⇓ buttons
 * - High-impedance design (no current flow between posts)
 * 
 * Example row: W = X + Y * rate (where "rate" is the slider variable)
 */
class EquationTableElm extends CircuitElm {
    final int FLAG_SMALL = 1;
    
    // Debug flag - set to true to enable detailed console output
    private static boolean DEBUG = false;
    
    private String tableName = "EqnTable";  // User-defined name for this element
    
    // Per-row data
    private static final int MAX_ROWS = 8;
    private int rowCount = 2;               // Number of active rows (1-8)
    private String[] outputNames;           // Output name per row (becomes labeled node)
    private String[] equations;             // Equation string per row
    private String[] sliderVarNames;        // Slider variable name per row
    private double[] sliderValues;          // Slider value per row
    private Expr[] compiledExprs;           // Compiled expression per row
    private ExprState[] exprStates;         // Expression state per row
    private double[] outputValues;          // Current output value per row
    private double[] lastOutputValues;      // Last output value per row (for convergence)
    
    // Row movement pending action (-1 = none, row index = pending swap with row+1)
    private int pendingMoveDown = -1;
    private int pendingMoveUp = -1;
    
    // Mouse hover tracking for per-row hints
    private int hoveredRow = -1;
    
    // Geometry
    int opsize;
    int tableWidth, tableHeight;
    int rowHeight = 18;
    int cellPadding = 4;
    Font labelFont, valueFont;
    
    // Constructor for menu creation
    public EquationTableElm(int xx, int yy) {
        super(xx, yy);
        noDiagonal = true;
        rowCount = 2;
        initArrays();
        // Set default values for rows
        outputNames[0] = "Y1";
        equations[0] = "0";
        sliderVarNames[0] = "a";
        sliderValues[0] = 0.5;
        
        outputNames[1] = "Y2";
        equations[1] = "0";
        sliderVarNames[1] = "b";
        sliderValues[1] = 0.5;
        
        setSize(sim.smallGridCheckItem.getState() ? 1 : 2);
        parseAllEquations();
        allocNodes();
    }
    
    // Constructor for file loading
    public EquationTableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        noDiagonal = true;
        
        // Parse table name
        if (st.hasMoreTokens()) {
            tableName = CustomLogicModel.unescape(st.nextToken());
        }
        
        // Parse row count
        if (st.hasMoreTokens()) {
            try {
                rowCount = Integer.parseInt(st.nextToken());
                if (rowCount < 1) rowCount = 1;
                if (rowCount > MAX_ROWS) rowCount = MAX_ROWS;
            } catch (Exception e) {
                rowCount = 2;
            }
        }
        
        initArrays();
        
        // Parse per-row data
        for (int row = 0; row < rowCount; row++) {
            if (st.hasMoreTokens()) {
                outputNames[row] = CustomLogicModel.unescape(st.nextToken());
            }
            if (st.hasMoreTokens()) {
                equations[row] = CustomLogicModel.unescape(st.nextToken());
            }
            if (st.hasMoreTokens()) {
                sliderVarNames[row] = CustomLogicModel.unescape(st.nextToken());
            }
            if (st.hasMoreTokens()) {
                try {
                    sliderValues[row] = Double.parseDouble(st.nextToken());
                } catch (Exception e) {
                    sliderValues[row] = 0.5;
                }
            }
        }
        
        setSize((f & FLAG_SMALL) != 0 ? 1 : 2);
        parseAllEquations();
        allocNodes();
    }
    
    private void initArrays() {
        outputNames = new String[MAX_ROWS];
        equations = new String[MAX_ROWS];
        sliderVarNames = new String[MAX_ROWS];
        sliderValues = new double[MAX_ROWS];
        compiledExprs = new Expr[MAX_ROWS];
        exprStates = new ExprState[MAX_ROWS];
        outputValues = new double[MAX_ROWS];
        lastOutputValues = new double[MAX_ROWS];
        
        for (int i = 0; i < MAX_ROWS; i++) {
            outputNames[i] = "Y" + (i + 1);
            equations[i] = "0";
            sliderVarNames[i] = String.valueOf((char)('a' + i));
            sliderValues[i] = 0.5;
            exprStates[i] = new ExprState(1); // 1 variable slot for slider
            outputValues[i] = 0.0;
            lastOutputValues[i] = 0.0;
        }
    }
    
    void setSize(int s) {
        opsize = s;
        flags = (flags & ~FLAG_SMALL) | ((s == 1) ? FLAG_SMALL : 0);
        rowHeight = (s == 1) ? 14 : 18;
        cellPadding = (s == 1) ? 2 : 4;
    }
    
    void setPoints() {
        super.setPoints();
        
        // Calculate table dimensions
        int charWidth = opsize == 1 ? 6 : 8;
        int maxTextLen = 10; // Minimum width
        
        for (int row = 0; row < rowCount; row++) {
            String displayText = outputNames[row] + " = " + equations[row];
            maxTextLen = Math.max(maxTextLen, displayText.length());
        }
        
        tableWidth = Math.max(80, maxTextLen * charWidth + cellPadding * 4);
        tableHeight = (rowCount + 1) * rowHeight + cellPadding * 2; // +1 for title row
        
        // Set bounding box based on position
        setBbox(x, y, x + tableWidth, y + tableHeight);
        
        // Update x2, y2 for proper bounds
        x2 = x + tableWidth;
        y2 = y + tableHeight;
        
        labelFont = new Font("SansSerif", 0, opsize == 2 ? 12 : 10);
        valueFont = new Font("SansSerif", 0, opsize == 2 ? 10 : 8);
    }
    
    int getDumpType() { return 266; }
    
    int getPostCount() { return rowCount; }
    
    Point getPost(int n) {
        // Posts are conceptual - not displayed but used for node allocation
        // Position them along the right edge of the table
        int postY = y + rowHeight + cellPadding + n * rowHeight + rowHeight / 2;
        return new Point(x + tableWidth, postY);
    }
    
    int getVoltageSourceCount() { return rowCount; }
    
    boolean nonLinear() { return true; }
    
    // High-impedance: no current path between posts
    boolean getConnection(int n1, int n2) { return false; }
    
    // Outputs do NOT connect to ground - they create their own independent nodes
    // (voltage sources drive the nodes)
    boolean hasGroundConnection(int n) { return false; }
    
    double getCurrentIntoNode(int n) {
        if (n >= 0 && n < rowCount)
            return -current; // Placeholder - each row could track its own current
        return 0;
    }
    
    private void parseAllEquations() {
        for (int row = 0; row < rowCount; row++) {
            parseEquation(row);
        }
    }
    
    private void parseEquation(int row) {
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] Parsing row " + row + ": '" + equations[row] + "'");
        }
        try {
            ExprParser parser = new ExprParser(equations[row]);
            compiledExprs[row] = parser.parseExpression();
            String err = parser.gotError();
            if (err != null) {
                CirSim.console("EquationTableElm row " + row + ": Parse error in '" + equations[row] + "': " + err);
                compiledExprs[row] = null;
            } else if (DEBUG) {
                CirSim.console("[EquationTableElm." + tableName + "] Row " + row + " parsed successfully");
            }
        } catch (Exception e) {
            CirSim.console("EquationTableElm row " + row + ": Error parsing '" + equations[row] + "': " + e.getMessage());
            compiledExprs[row] = null;
        }
    }
    
    double getConvergeLimit(int row) {
        double relativeTolerance;
        if (sim.subIterations < 10)
            relativeTolerance = 0.001;
        else if (sim.subIterations < 100)
            relativeTolerance = 0.01;
        else
            relativeTolerance = 0.1;
        
        double maxMagnitude = Math.max(1.0, Math.abs(outputValues[row]));
        maxMagnitude = Math.max(maxMagnitude, Math.abs(lastOutputValues[row]));
        
        return maxMagnitude * relativeTolerance;
    }
    
    void stamp() {
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] stamp() called, rowCount=" + rowCount + ", voltSource=" + voltSource);
        }
        for (int row = 0; row < rowCount; row++) {
            int outputNode = nodes[row];
            int vn = voltSource + row + sim.nodeList.size();
            
            if (DEBUG) {
                CirSim.console("[EquationTableElm." + tableName + "] Row " + row + " (" + outputNames[row] + "): vn=" + vn + ", node=" + outputNode + ", voltSource=" + (voltSource + row));
            }
            
            // Can't stamp voltage source to ground node (would be ground-to-ground)
            if (outputNode == 0) {
                CirSim.console("EquationTableElm [" + tableName + "] Row " + row + " (" + outputNames[row] + "): WARNING - Skipping stamp, output node is ground!");
                continue;
            }
            
            sim.stampNonLinear(vn);
            sim.stampVoltageSource(0, outputNode, voltSource + row);
            
            // Add high-value resistor from output to ground to help matrix conditioning
            sim.stampResistor(outputNode, 0, 1e8);
        }
        
        // Connect output nodes to labeled nodes
        connectToLabeledNodes();
    }
    
    /**
     * Connect each output row to its corresponding labeled node (if it exists)
     * This allows the output voltage to appear in labeled nodes with the same name
     */
    private void connectToLabeledNodes() {
        for (int row = 0; row < rowCount; row++) {
            String outputName = outputNames[row];
            if (outputName == null || outputName.trim().isEmpty()) continue;
            
            // Find labeled node with this name
            Integer labeledNodeNum = LabeledNodeElm.getByName(outputName.trim());
            int outputNode = nodes[row];
            
            if (labeledNodeNum != null && labeledNodeNum > 0 && outputNode > 0) {
                // Connect via very low resistance (1μΩ) - effectively a wire
                sim.stampResistor(outputNode, labeledNodeNum, 1e-6);
                
                if (DEBUG) {
                    CirSim.console("[EquationTableElm." + tableName + "] Connected row " + row + " (" + outputName + ") node " + outputNode + " to labeled node " + labeledNodeNum);
                }
            }
        }
    }
    
    @Override
    void setVoltageSource(int j, int vs) {
        // Store the base voltage source
        if (j == 0) {
            voltSource = vs;
        }
        // Each row gets consecutive voltage sources starting from voltSource
    }
    
    void doStep() {
        if (DEBUG && sim.subIterations == 0) {
            CirSim.console("[EquationTableElm." + tableName + "] doStep() at t=" + sim.t);
        }
        for (int row = 0; row < rowCount; row++) {
            int vn = voltSource + row + sim.nodeList.size();
            
            if (compiledExprs[row] != null) {
                ExprState state = exprStates[row];
                
                // Set slider variable as 'a' (values[0]) for simple variable reference
                state.values[0] = sliderValues[row];
                state.t = sim.t;
                
                // ALSO register slider variable as a computed value so E_NODE_REF can find it
                String sliderVar = sliderVarNames[row];
                if (sliderVar != null && !sliderVar.isEmpty()) {
                    ComputedValues.setComputedValue(sliderVar, sliderValues[row]);
                }
                
                double equationValue = compiledExprs[row].eval(state);
                
                if (DEBUG) {
                    CirSim.console("[EquationTableElm." + tableName + "] Row " + row + " (" + outputNames[row] + "):");
                    CirSim.console("  Equation: " + equations[row]);
                    CirSim.console("  Slider " + sliderVarNames[row] + "=" + sliderValues[row] + " (registered as computed value)");
                    CirSim.console("  state.lastOutput=" + exprStates[row].lastOutput + " (from previous step)");
                    CirSim.console("  state.values[0]=" + state.values[0]);
                    CirSim.console("  state.t=" + state.t);
                    
                    // Try to identify what variables might be in the equation
                    String eq = equations[row];
                    CirSim.console("  Checking for labeled nodes in equation...");
                    String[] labeledNodes = LabeledNodeElm.getSortedLabeledNodeNames();
                    if (labeledNodes != null && labeledNodes.length > 0) {
                        for (String nodeName : labeledNodes) {
                            if (eq.contains(nodeName)) {
                                // Get node number and voltage
                                Integer nodeNum = LabeledNodeElm.getByName(nodeName);
                                double nodeValue = 0;
                                if (nodeNum != null && nodeNum > 0 && nodeNum <= sim.nodeVoltages.length) {
                                    nodeValue = sim.nodeVoltages[nodeNum - 1]; // nodeVoltages is 0-indexed, excludes ground
                                }
                                CirSim.console("    Found labeled node '" + nodeName + "' (node " + nodeNum + ") = " + nodeValue);
                            }
                        }
                    } else {
                        CirSim.console("    No labeled nodes found in circuit");
                    }
                    
                    // Also check computed values (including slider)
                    CirSim.console("  Checking computed values...");
                    Double sliderComputedVal = ComputedValues.getComputedValue(sliderVar);
                    if (sliderComputedVal != null) {
                        CirSim.console("    Slider '" + sliderVar + "' computed value = " + sliderComputedVal);
                    }
                    
                    CirSim.console("  Evaluated value: " + equationValue);
                    CirSim.console("  Last value: " + lastOutputValues[row]);
                    CirSim.console("  Output voltage: " + volts[row]);
                }
                
                // Check convergence
                double convergeLimit = getConvergeLimit(row);
                boolean equationConverged = Math.abs(equationValue - lastOutputValues[row]) <= convergeLimit;
                if (!equationConverged) {
                    sim.converged = false;
                    if (DEBUG) {
                        CirSim.console("  Equation NOT converged: diff=" + Math.abs(equationValue - lastOutputValues[row]) + ", limit=" + convergeLimit);
                    }
                } else if (DEBUG) {
                    CirSim.console("  Equation converged");
                }
                lastOutputValues[row] = equationValue;
                
                outputValues[row] = equationValue;
                
                // Register output immediately so subsequent rows can reference it in same subiteration
                // This allows intra-table dependencies (e.g., Y2 = Y1 + 1) to work correctly
                String outputName = outputNames[row];
                if (outputName != null && !outputName.trim().isEmpty()) {
                    ComputedValues.setComputedValue(outputName.trim(), equationValue);
                }
                
                // Check output voltage convergence
                double outputVoltage = volts[row];
                double voltageDiff = Math.abs(outputVoltage - outputValues[row]);
                double threshold = Math.max(Math.abs(outputValues[row]) * 0.01, 1e-6);
                boolean voltageConverged = voltageDiff <= threshold || sim.subIterations >= 100;
                if (!voltageConverged) {
                    sim.converged = false;
                    if (DEBUG) {
                        CirSim.console("  Voltage NOT converged: diff=" + voltageDiff + ", threshold=" + threshold + ", subIter=" + sim.subIterations);
                    }
                } else if (DEBUG) {
                    CirSim.console("  Voltage converged");
                }
                
                sim.stampRightSide(vn, outputValues[row]);
            } else if (DEBUG) {
                CirSim.console("[EquationTableElm." + tableName + "] Row " + row + ": NULL compiled expression!");
            }
        }
    }
    
    @Override
    public void stepFinished() {
        // Register outputs as labeled nodes
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] stepFinished() - registering outputs:");
        }
        for (int row = 0; row < rowCount; row++) {
            String name = outputNames[row];
            if (name != null && !name.trim().isEmpty()) {
                registerComputedValueAsLabeledNode(name.trim(), outputValues[row]);
                ComputedValues.markComputedThisStep(name.trim());
                if (DEBUG) {
                    CirSim.console("  " + name.trim() + " = " + outputValues[row]);
                }
            }
            
            // Update ExprState for next timestep (like VCVSElm does)
            if (exprStates[row] != null) {
                exprStates[row].updateLastValues(outputValues[row]);
            }
        }
    }
    
    /**
     * Register a computed value so it can be accessed by other elements via ComputedValues
     */
    private void registerComputedValueAsLabeledNode(String name, double value) {
        ComputedValues.setComputedValue(name, value);
    }
    
    @Override
    public void reset() {
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] reset() called");
        }
        super.reset();
        for (int row = 0; row < rowCount; row++) {
            if (exprStates[row] != null) {
                exprStates[row].reset();
            }
            outputValues[row] = 0.0;
            lastOutputValues[row] = 0.0;
            
            // Register initial values with ComputedValues at t=0
            // This ensures labeled nodes show initial values before simulation starts
            String name = outputNames[row];
            if (name != null && !name.trim().isEmpty()) {
                registerComputedValueAsLabeledNode(name.trim(), outputValues[row]);
            }
        }
    }
    
    @Override
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.dump());
        sb.append(" ").append(CustomLogicModel.escape(tableName));
        sb.append(" ").append(rowCount);
        
        for (int row = 0; row < rowCount; row++) {
            sb.append(" ").append(CustomLogicModel.escape(outputNames[row]));
            sb.append(" ").append(CustomLogicModel.escape(equations[row]));
            sb.append(" ").append(CustomLogicModel.escape(sliderVarNames[row]));
            sb.append(" ").append(sliderValues[row]);
        }
        
        return sb.toString();
    }
    
    @Override
    void drawPosts(Graphics g) {
        // Override to hide posts - electrical connections remain functional
        // but visual connection dots are not drawn (like TableElm)
    }
    
    void draw(Graphics g) {
        // Calculate table position
        int tableX = x;
        int tableY = y;
        
        boolean selected = needsHighlight();
        
        // Draw table background (always dark gray)
        g.setColor(Color.darkGray);
        g.fillRect(tableX, tableY, tableWidth, tableHeight);
        
        // Draw border - highlight only the border when selected
        g.setColor(selected ? selectColor : Color.gray);
        g.drawRect(tableX, tableY, tableWidth, tableHeight);
        if (selected) {
            // Draw thicker border when selected
            g.drawRect(tableX + 1, tableY + 1, tableWidth - 2, tableHeight - 2);
        }
        
        // Draw title row
        g.setFont(labelFont);
        g.setColor(whiteColor);
        int titleY = tableY + rowHeight - cellPadding;
        drawCenteredText(g, tableName, tableX + tableWidth / 2, titleY - 2, true);
        
        // Draw separator line after title
        g.setColor(Color.gray);
        g.drawLine(tableX, tableY + rowHeight, tableX + tableWidth, tableY + rowHeight);
        
        // Determine which row the mouse is hovering over
        // Convert mouse screen coordinates to circuit coordinates
        hoveredRow = -1;
        int mouseCircuitX = sim.inverseTransformX(sim.mouseCursorX);
        int mouseCircuitY = sim.inverseTransformY(sim.mouseCursorY);
        if (mouseCircuitX >= tableX && mouseCircuitX <= tableX + tableWidth &&
            mouseCircuitY >= tableY && mouseCircuitY <= tableY + tableHeight) {
            // Calculate which row, accounting for title row at top
            int relativeY = mouseCircuitY - (tableY + rowHeight); // Offset from start of first data row
            if (relativeY >= 0) {
                int mouseRowIndex = relativeY / rowHeight;
                if (mouseRowIndex >= 0 && mouseRowIndex < rowCount) {
                    hoveredRow = mouseRowIndex;
                }
            }
        }
        
        // Draw each row
        g.setFont(valueFont);
        for (int row = 0; row < rowCount; row++) {
            int rowY = tableY + (row + 1) * rowHeight;
            
            // Highlight hovered row
            if (row == hoveredRow) {
                g.setColor(new Color(80, 80, 100)); // Subtle highlight color
                g.fillRect(tableX + 1, rowY + 1, tableWidth - 2, rowHeight - 1);
            }
            
            // Build display string with slider value substituted
            String displayEquation = equations[row];
            displayEquation = Locale.convertGreekSymbols(displayEquation);
            
            // Substitute slider variable with its value
            String sliderVar = sliderVarNames[row];
            if (sliderVar != null && !sliderVar.isEmpty()) {
                String valueStr = getShortUnitText(sliderValues[row], "");
                displayEquation = displayEquation.replaceAll("\\b" + sliderVar + "\\b", valueStr);
            }
            
            String rowText = outputNames[row] + " = " + displayEquation;
            
            // Draw row text
            g.setColor(whiteColor);
            g.drawString(rowText, tableX + cellPadding, rowY + rowHeight - cellPadding - 2);
            
            // Draw current value on right side
            String valueText = getShortUnitText(outputValues[row], "");
            int valueWidth = (int) g.context.measureText(valueText).getWidth();
            g.setColor(Color.cyan);
            g.drawString(valueText, tableX + tableWidth - valueWidth - cellPadding, rowY + rowHeight - cellPadding - 2);
            
            // Draw row separator
            if (row < rowCount - 1) {
                g.setColor(Color.gray);
                int sepY = tableY + (row + 2) * rowHeight;
                g.drawLine(tableX, sepY, tableX + tableWidth, sepY);
            }
        }
        
        // Draw hint tooltip above mouse when hovering over a row
        if (hoveredRow >= 0 && hoveredRow < rowCount) {
            String hint = HintRegistry.getHint(outputNames[hoveredRow]);
            if (hint != null && !hint.trim().isEmpty()) {
                g.setFont(valueFont);
                int hintWidth = (int) g.context.measureText(hint).getWidth() + 8;
                int hintHeight = opsize == 1 ? 12 : 16;
                
                // Position above the mouse cursor
                int tooltipX = mouseCircuitX - hintWidth / 2;
                int tooltipY = mouseCircuitY - hintHeight - 4;
                
                // Keep tooltip within table bounds horizontally
                if (tooltipX < tableX) tooltipX = tableX;
                if (tooltipX + hintWidth > tableX + tableWidth) tooltipX = tableX + tableWidth - hintWidth;
                
                // Draw tooltip background
                g.setColor(new Color(60, 60, 80));
                g.fillRect(tooltipX, tooltipY, hintWidth, hintHeight);
                g.setColor(Color.gray);
                g.drawRect(tooltipX, tooltipY, hintWidth, hintHeight);
                
                // Draw tooltip text
                g.setColor(Color.yellow);
                g.drawString(hint, tooltipX + 4, tooltipY + hintHeight - 3);
            }
        }
        
        // Update bounding box
        setBbox(tableX, tableY, tableX + tableWidth, tableY + tableHeight);
    }
    
    public EditInfo getEditInfo(int n) {
        // Field 0: Table name
        if (n == 0) {
            EditInfo ei = new EditInfo("Table Name", 0, -1, -1);
            ei.text = tableName;
            ei.disallowSliders();
            return ei;
        }
        
        // Field 1: Number of rows
        if (n == 1) {
            EditInfo ei = new EditInfo("Number of Rows", "");
            ei.choice = new Choice();
            for (int i = 1; i <= MAX_ROWS; i++) {
                ei.choice.add(String.valueOf(i));
            }
            ei.choice.select(rowCount - 1);
            return ei;
        }
        
        // Per-row fields: 5 fields per row (output name, equation, slider var, slider value, move buttons)
        // Starting at field 2
        int fieldOffset = n - 2;
        int fieldsPerRow = 5;
        int row = fieldOffset / fieldsPerRow;
        int fieldInRow = fieldOffset % fieldsPerRow;
        
        if (row >= 0 && row < rowCount) {
            switch (fieldInRow) {
                case 0: { // Output name
                    EditInfo ei = new EditInfo("Row " + (row + 1) + " Output Name", 0, -1, -1);
                    ei.text = outputNames[row];
                    ei.disallowSliders();
                    return ei;
                }
                case 1: { // Equation
                    EditInfo ei = new EditInfo("Row " + (row + 1) + " Equation", 0, -1, -1);
                    ei.text = equations[row];
                    ei.disallowSliders();
                    
                    // Build completion list
                    java.util.List<String> completions = new java.util.ArrayList<String>();
                    
                    // Add stock variables
                    java.util.Set<String> stockNames = StockFlowRegistry.getAllStockNames();
                    if (stockNames != null) {
                        for (String stockName : stockNames) {
                            if (!completions.contains(stockName)) completions.add(stockName);
                        }
                    }
                    
                    // Add labeled node names
                    String[] labeledNodes = LabeledNodeElm.getSortedLabeledNodeNames();
                    if (labeledNodes != null) {
                        for (String nodeName : labeledNodes) {
                            if (!completions.contains(nodeName)) completions.add(nodeName);
                        }
                    }
                    
                    // Add this row's slider variable
                    if (!completions.contains(sliderVarNames[row])) {
                        completions.add(sliderVarNames[row]);
                    }
                    
                    // Add math functions
                    String[] funcs = {"sin", "cos", "tan", "exp", "log", "sqrt", "abs", "min", "max", "pow", "floor", "ceil", "integrate"};
                    for (String f : funcs) {
                        if (!completions.contains(f)) completions.add(f);
                    }
                    
                    // Add constants
                    if (!completions.contains("pi")) completions.add("pi");
                    if (!completions.contains("t")) completions.add("t");
                    if (!completions.contains("lastoutput")) completions.add("lastoutput");
                    if (!completions.contains("timestep")) completions.add("timestep");
                    
                    ei.completionList = completions;
                    return ei;
                }
                case 2: { // Slider variable name
                    EditInfo ei = new EditInfo("Row " + (row + 1) + " Slider Variable", 0, -1, -1);
                    ei.text = sliderVarNames[row];
                    ei.disallowSliders();
                    return ei;
                }
                case 3: { // Slider value
                    EditInfo ei = new EditInfo("Row " + (row + 1) + " '" + sliderVarNames[row] + "'", sliderValues[row]);
                    return ei;
                }
                case 4: { // Move buttons
                    // Create a label field that describes available moves
                    String label = "Row " + (row + 1) + " Move:";
                    if (row > 0) label += " ⇑";
                    if (row < rowCount - 1) label += " ⇓";
                    
                    EditInfo ei = new EditInfo(label, 0, -1, -1);
                    ei.choice = new Choice();
                    ei.choice.add("(no action)");
                    if (row > 0) ei.choice.add("Move Up ⇑");
                    if (row < rowCount - 1) ei.choice.add("Move Down ⇓");
                    ei.choice.select(0);
                    return ei;
                }
            }
        }
        
        // Small checkbox after all row fields
        int smallFieldIndex = 2 + (rowCount * fieldsPerRow);
        if (n == smallFieldIndex) {
            return EditInfo.createCheckbox("Small", (flags & FLAG_SMALL) != 0);
        }
        
        return null;
    }
    
    public void setEditValue(int n, EditInfo ei) {
        // Field 0: Table name
        if (n == 0) {
            tableName = ei.textf.getText();
            return;
        }
        
        // Field 1: Number of rows
        if (n == 1) {
            int newRowCount = ei.choice.getSelectedIndex() + 1;
            if (newRowCount != rowCount) {
                rowCount = newRowCount;
                allocNodes();
                parseAllEquations();
                setPoints();
                ei.newDialog = true; // Refresh dialog
            }
            return;
        }
        
        // Per-row fields
        int fieldOffset = n - 2;
        int fieldsPerRow = 5;
        int row = fieldOffset / fieldsPerRow;
        int fieldInRow = fieldOffset % fieldsPerRow;
        
        if (row >= 0 && row < rowCount) {
            switch (fieldInRow) {
                case 0: // Output name
                    outputNames[row] = ei.textf.getText();
                    break;
                case 1: // Equation
                    equations[row] = ei.textf.getText();
                    parseEquation(row);
                    break;
                case 2: // Slider variable name
                    sliderVarNames[row] = ei.textf.getText();
                    break;
                case 3: // Slider value
                    sliderValues[row] = ei.value;
                    break;
                case 4: // Move action
                    int selection = ei.choice.getSelectedIndex();
                    if (selection > 0) {
                        // Determine which action based on row position
                        if (row > 0 && row < rowCount - 1) {
                            // Middle row: 1 = up, 2 = down
                            if (selection == 1) {
                                swapRows(row, row - 1);
                                ei.newDialog = true;
                            } else if (selection == 2) {
                                swapRows(row, row + 1);
                                ei.newDialog = true;
                            }
                        } else if (row == 0) {
                            // First row: 1 = down
                            if (selection == 1) {
                                swapRows(row, row + 1);
                                ei.newDialog = true;
                            }
                        } else if (row == rowCount - 1) {
                            // Last row: 1 = up
                            if (selection == 1) {
                                swapRows(row, row - 1);
                                ei.newDialog = true;
                            }
                        }
                    }
                    break;
            }
            return;
        }
        
        // Small checkbox
        int smallFieldIndex = 2 + (rowCount * fieldsPerRow);
        if (n == smallFieldIndex) {
            flags = ei.changeFlag(flags, FLAG_SMALL);
            boolean small = (flags & FLAG_SMALL) != 0;
            setSize(small ? 1 : 2);
            if (small) {
                sim.smallGridCheckItem.setState(true);
                sim.setGrid();
            }
            setPoints();
        }
    }
    
    /**
     * Swap all data between two rows
     */
    private void swapRows(int from, int to) {
        if (from < 0 || from >= rowCount || to < 0 || to >= rowCount || from == to) {
            return;
        }
        
        // Swap output names
        String tempName = outputNames[from];
        outputNames[from] = outputNames[to];
        outputNames[to] = tempName;
        
        // Swap equations
        String tempEq = equations[from];
        equations[from] = equations[to];
        equations[to] = tempEq;
        
        // Swap slider variable names
        String tempSliderVar = sliderVarNames[from];
        sliderVarNames[from] = sliderVarNames[to];
        sliderVarNames[to] = tempSliderVar;
        
        // Swap slider values
        double tempSliderVal = sliderValues[from];
        sliderValues[from] = sliderValues[to];
        sliderValues[to] = tempSliderVal;
        
        // Swap compiled expressions
        Expr tempExpr = compiledExprs[from];
        compiledExprs[from] = compiledExprs[to];
        compiledExprs[to] = tempExpr;
        
        // Swap expression states
        ExprState tempState = exprStates[from];
        exprStates[from] = exprStates[to];
        exprStates[to] = tempState;
        
        // Swap output values
        double tempOutput = outputValues[from];
        outputValues[from] = outputValues[to];
        outputValues[to] = tempOutput;
        
        // Swap last output values
        double tempLastOutput = lastOutputValues[from];
        lastOutputValues[from] = lastOutputValues[to];
        lastOutputValues[to] = tempLastOutput;
    }
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "Equation Table: " + tableName;
        
        // If hovering over a specific row, show detailed info for that row
        if (hoveredRow >= 0 && hoveredRow < rowCount) {
            // Show hint first if not empty (from central HintRegistry)
            String hint = HintRegistry.getHint(outputNames[hoveredRow]);
            if (hint != null && !hint.trim().isEmpty()) {
                arr[1] = hint;
            } else {
                arr[1] = "Row " + (hoveredRow + 1) + ": " + outputNames[hoveredRow];
            }
            arr[2] = "Row " + (hoveredRow + 1) + ": " + outputNames[hoveredRow];
            arr[3] = "Equation: " + equations[hoveredRow];
            arr[4] = "Slider: " + sliderVarNames[hoveredRow] + " = " + getShortUnitText(sliderValues[hoveredRow], "");
            arr[5] = "Output: " + getUnitText(outputValues[hoveredRow], "");
            if (compiledExprs[hoveredRow] == null) {
                arr[6] = "⚠ Equation parse error";
            }
        } else {
            // Show summary when not hovering over any row
            arr[1] = "Rows: " + rowCount;
            
            int idx = 2;
            for (int row = 0; row < rowCount && idx < arr.length - 1; row++) {
                arr[idx++] = outputNames[row] + " = " + getUnitText(outputValues[row], "");
            }
        }
    }
    
    // Custom formatting for slider fields
    public String getSliderUnitText(int n, EditInfo ei, double value) {
        // Per-row fields start at index 2
        int fieldOffset = n - 2;
        int fieldsPerRow = 5;
        int fieldInRow = fieldOffset % fieldsPerRow;
        
        // Only format slider value fields (fieldInRow == 3)
        if (fieldInRow == 3) {
            return getShortUnitText(value, "");
        }
        return null;
    }
    
    //=== GETTER/SETTER METHODS FOR EDIT DIALOG ===================================
    
    public String getTableName() { return tableName; }
    public void setTableName(String name) { tableName = name; }
    
    public int getRowCount() { return rowCount; }
    public void setRowCount(int count) { 
        if (count >= 1 && count <= MAX_ROWS) {
            rowCount = count;
        }
    }
    
    public String getOutputName(int row) {
        return (row >= 0 && row < MAX_ROWS) ? outputNames[row] : "";
    }
    public void setOutputName(int row, String name) {
        if (row >= 0 && row < MAX_ROWS) {
            outputNames[row] = name;
        }
    }
    
    public String getEquation(int row) {
        return (row >= 0 && row < MAX_ROWS) ? equations[row] : "";
    }
    public void setEquation(int row, String eq) {
        if (row >= 0 && row < MAX_ROWS) {
            equations[row] = eq;
        }
    }
    
    public String getSliderVarName(int row) {
        return (row >= 0 && row < MAX_ROWS) ? sliderVarNames[row] : "";
    }
    public void setSliderVarName(int row, String name) {
        if (row >= 0 && row < MAX_ROWS) {
            sliderVarNames[row] = name;
        }
    }
    
    public double getSliderValue(int row) {
        return (row >= 0 && row < MAX_ROWS) ? sliderValues[row] : 0.0;
    }
    public void setSliderValue(int row, double val) {
        if (row >= 0 && row < MAX_ROWS) {
            sliderValues[row] = val;
        }
    }
    
    /** Public method for dialog to trigger equation reparse */
    public void parseAllEquationsPublic() {
        parseAllEquations();
    }
    
    //=== OPEN EDIT DIALOG (called from CirSim.onDoubleClick) ====================
    
    public void openEditDialog() {
        if (sim != null) {
            try {
                EquationTableEditDialog dialog = new EquationTableEditDialog(this, sim);
                CirSim.dialogShowing = dialog;
                dialog.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
