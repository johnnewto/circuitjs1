/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.util.Locale;

/**
 * EquationTableElm - Table of Equations with Adjustable Sliders
 * 
 * This element displays a visual table where each row defines a named equation output.
 * It provides a spreadsheet-like interface for defining mathematical relationships
 * in the circuit.
 * 
 * <h3>Features:</h3>
 * <ul>
 *   <li>Multiple rows (1-8), each defining an independent equation</li>
 *   <li>Each row has a named output that becomes accessible as a labeled node</li>
 *   <li>Custom slider variable per row for interactive parameter adjustment</li>
 *   <li>Support for initial value equations (evaluated only at t=0)</li>
 *   <li>Row reordering via up/down buttons in edit dialog</li>
 *   <li>Autocomplete support for equation editing</li>
 *   <li>Hint tooltip display when hovering over rows</li>
 * </ul>
 * 
 * <h3>Circuit Behavior:</h3>
 * <ul>
 *   <li>High-impedance design - no current flows between posts</li>
 *   <li>Each row output is driven by a voltage source</li>
 *   <li>Outputs connect to labeled nodes with matching names</li>
 *   <li>Nonlinear element requiring iterative convergence</li>
 * </ul>
 * 
 * <h3>Example Usage:</h3>
 * <pre>
 *   Row 1: Y1 = X + rate    (where "rate" is a slider from 0-1)
 *   Row 2: Y2 = Y1 * 2      (references output of row 1)
 * </pre>
 * 
 * @see TableElm Similar visual table element for display
 * @see ComputedValues Registry for accessing equation outputs
 */
class EquationTableElm extends CircuitElm {
    
    //=============================================================================
    // CONSTANTS
    //=============================================================================
    
    /** Flag indicating small display mode */
    private static final int FLAG_SMALL = 1;
    
    /** Maximum number of equation rows supported */
    private static final int MAX_ROWS = 8;
    
    //=============================================================================
    // DEBUG CONFIGURATION
    //=============================================================================
    
    /** Debug flag - set to true to enable detailed console output for troubleshooting */
    private static boolean DEBUG = false;
    
    //=============================================================================
    // INSTANCE STATE - Core Data
    //=============================================================================
    
    /** User-defined name for this table element (displayed in title bar) */
    private String tableName = "EqnTable";
    
    /** Number of active rows (1 to MAX_ROWS) */
    private int rowCount = 2;
    
    //=============================================================================
    // INSTANCE STATE - Per-Row Arrays
    //=============================================================================
    
    /** Output name per row - becomes a labeled node accessible by other elements */
    private String[] outputNames;
    
    /** Equation string per row - evaluated each timestep */
    private String[] equations;
    
    /** Initial value equation per row - evaluated only at t=0 */
    private String[] initialEquations;
    
    /** Slider variable name per row - can be referenced in equations */
    private String[] sliderVarNames;
    
    /** Current slider value per row */
    private double[] sliderValues;
    
    /** Compiled expression tree per row for efficient evaluation */
    private Expr[] compiledExprs;
    
    /** Compiled initial expression per row */
    private Expr[] compiledInitialExprs;
    
    /** Expression evaluation state per row (holds lastOutput, t, etc.) */
    private ExprState[] exprStates;
    
    /** Current output value per row */
    private double[] outputValues;
    
    /** Previous output value per row (for convergence checking) */
    private double[] lastOutputValues;
    
    /** Track if initial value has been applied this simulation run */
    private boolean[] initialValueApplied;
    
    //=============================================================================
    // INSTANCE STATE - UI Interaction
    //=============================================================================
    
    /** Currently hovered row index (-1 = none) - for tooltip display */
    private int hoveredRow = -1;
    
    //=============================================================================
    // INSTANCE STATE - Geometry & Rendering
    //=============================================================================
    
    /** Display size mode (1 = small, 2 = normal) */
    int opsize;
    
    /** Calculated table width in pixels */
    int tableWidth;
    
    /** Calculated table height in pixels */
    int tableHeight;
    
    /** Height of each row in pixels */
    int rowHeight = 18;
    
    /** Padding inside cells */
    int cellPadding = 4;
    
    /** Font for row labels/names */
    Font labelFont;
    
    /** Font for values */
    Font valueFont;
    
    //=============================================================================
    // CONSTRUCTORS
    //=============================================================================
    
    /**
     * Constructor for creating element from the UI menu.
     * Initializes with default 2 rows and placeholder equations.
     * 
     * @param xx X coordinate
     * @param yy Y coordinate
     */
    public EquationTableElm(int xx, int yy) {
        super(xx, yy);
        noDiagonal = true;
        rowCount = 2;
        initArrays();
        
        // Set default values for the initial rows
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
    
    /**
     * Constructor for loading element from a saved file.
     * Parses all row data from the StringTokenizer.
     * 
     * @param xa Start X coordinate
     * @param ya Start Y coordinate
     * @param xb End X coordinate
     * @param yb End Y coordinate
     * @param f Flags
     * @param st StringTokenizer containing saved data
     */
    public EquationTableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        noDiagonal = true;
        
        // Parse table name
        if (st.hasMoreTokens()) {
            tableName = CustomLogicModel.unescape(st.nextToken());
        }
        
        // Parse row count with validation
        if (st.hasMoreTokens()) {
            try {
                rowCount = Integer.parseInt(st.nextToken());
                rowCount = Math.max(1, Math.min(rowCount, MAX_ROWS));
            } catch (Exception e) {
                rowCount = 2;
            }
        }
        
        initArrays();
        
        // Parse per-row data in order: outputName, equation, initialEquation, sliderVar, sliderValue
        for (int row = 0; row < rowCount; row++) {
            if (st.hasMoreTokens()) {
                outputNames[row] = CustomLogicModel.unescape(st.nextToken());
            }
            if (st.hasMoreTokens()) {
                equations[row] = CustomLogicModel.unescape(st.nextToken());
            }
            if (st.hasMoreTokens()) {
                initialEquations[row] = CustomLogicModel.unescape(st.nextToken());
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
    
    //=============================================================================
    // INITIALIZATION
    //=============================================================================
    
    /**
     * Initialize all per-row arrays with default values.
     * Called by constructors before loading specific data.
     */
    private void initArrays() {
        outputNames = new String[MAX_ROWS];
        equations = new String[MAX_ROWS];
        initialEquations = new String[MAX_ROWS];
        sliderVarNames = new String[MAX_ROWS];
        sliderValues = new double[MAX_ROWS];
        compiledExprs = new Expr[MAX_ROWS];
        compiledInitialExprs = new Expr[MAX_ROWS];
        exprStates = new ExprState[MAX_ROWS];
        outputValues = new double[MAX_ROWS];
        lastOutputValues = new double[MAX_ROWS];
        initialValueApplied = new boolean[MAX_ROWS];
        
        // Set reasonable defaults for all rows
        for (int i = 0; i < MAX_ROWS; i++) {
            outputNames[i] = "Y" + (i + 1);
            equations[i] = "0";
            initialEquations[i] = "";
            sliderVarNames[i] = String.valueOf((char)('a' + i));  // a, b, c, ...
            sliderValues[i] = 0.5;
            exprStates[i] = new ExprState(1);  // 1 variable slot for slider
            outputValues[i] = 0.0;
            lastOutputValues[i] = 0.0;
            initialValueApplied[i] = false;
        }
    }
    
    /**
     * Get output names for all active rows.
     * Used by StockFlowRegistry to expose equation outputs in Variable Browser.
     * 
     * @return Array of output names for active rows
     */
    public String[] getOutputNames() {
        String[] names = new String[rowCount];
        for (int i = 0; i < rowCount; i++) {
            names[i] = outputNames[i];
        }
        return names;
    }
    
    /**
     * Set the display size mode.
     * 
     * @param s Size mode: 1 = small, 2 = normal
     */
    void setSize(int s) {
        opsize = s;
        flags = (flags & ~FLAG_SMALL) | ((s == 1) ? FLAG_SMALL : 0);
        rowHeight = (s == 1) ? 14 : 18;
        cellPadding = (s == 1) ? 2 : 4;
    }
    
    //=============================================================================
    // CIRCUIT ELEMENT INTERFACE - Geometry
    //=============================================================================
    
    /**
     * Calculate table dimensions and set up geometry.
     * Called when the element is created or modified.
     */
    void setPoints() {
        super.setPoints();
        
        // Calculate required width based on content
        int charWidth = opsize == 1 ? 6 : 8;
        int maxTextLen = 10;  // Minimum width
        
        // Include header in width calculation
        maxTextLen = Math.max(maxTextLen, tableName.length());
        
        // Check each row for maximum text length
        for (int row = 0; row < rowCount; row++) {
            String displayText = outputNames[row] + " = " + equations[row];
            // Include initial equation in width calculation if present
            String initEq = initialEquations[row];
            if (initEq != null && !initEq.trim().isEmpty()) {
                displayText += " [" + initEq + "]";
            }
            maxTextLen = Math.max(maxTextLen, displayText.length());
        }
        
        // Calculate final dimensions
        tableWidth = Math.max(80, maxTextLen * charWidth + cellPadding * 4);
        tableHeight = (rowCount + 1) * rowHeight + cellPadding * 2;  // +1 for title row
        
        // Set bounding box for hit testing
        setBbox(x, y, x + tableWidth, y + tableHeight);
        
        // Update x2, y2 for proper bounds
        x2 = x + tableWidth;
        y2 = y + tableHeight;
        
        // Set up fonts
        labelFont = new Font("SansSerif", 0, opsize == 2 ? 12 : 10);
        valueFont = new Font("SansSerif", 0, opsize == 2 ? 10 : 8);
    }
    
    //=============================================================================
    // CIRCUIT ELEMENT INTERFACE - Identification
    //=============================================================================
    
    /** @return Dump type identifier for serialization (266) */
    int getDumpType() { return 266; }
    
    /** @return Number of electrical posts (one per row) */
    int getPostCount() { return rowCount; }
    
    /**
     * Get the position of a post.
     * Posts are positioned along the right edge of the table.
     * 
     * @param n Post index (0 to rowCount-1)
     * @return Point location of the post
     */
    Point getPost(int n) {
        int postY = y + rowHeight + cellPadding + n * rowHeight + rowHeight / 2;
        return new Point(x + tableWidth, postY);
    }
    
    /** @return Number of voltage sources needed (one per row) */
    int getVoltageSourceCount() { return rowCount; }
    
    /** @return true - this element requires iterative nonlinear solving */
    boolean nonLinear() { return true; }
    
    /**
     * Check if two posts are electrically connected.
     * High-impedance design: no current path exists between any posts.
     */
    boolean getConnection(int n1, int n2) { return false; }
    
    /**
     * Check if a post has a direct ground connection.
     * Outputs don't connect to ground - they create independent voltage nodes.
     */
    boolean hasGroundConnection(int n) { return false; }
    
    /**
     * Get current flowing into a node.
     */
    double getCurrentIntoNode(int n) {
        if (n >= 0 && n < rowCount)
            return -current;
        return 0;
    }
    
    //=============================================================================
    // EQUATION PARSING
    //=============================================================================
    
    /**
     * Parse all equations for all rows.
     * Called after loading or when equations are modified.
     */
    private void parseAllEquations() {
        for (int row = 0; row < rowCount; row++) {
            parseEquation(row);
            parseInitialEquation(row);
        }
    }
    
    /**
     * Parse the initial value equation for a specific row.
     * @param row Row index
     */
    private void parseInitialEquation(int row) {
        String initEq = initialEquations[row];
        if (initEq == null || initEq.trim().isEmpty()) {
            compiledInitialExprs[row] = null;
            return;
        }
        
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] Parsing initial equation row " + row + ": '" + initEq + "'");
        }
        
        try {
            ExprParser parser = new ExprParser(initEq);
            compiledInitialExprs[row] = parser.parseExpression();
            String err = parser.gotError();
            
            if (err != null) {
                CirSim.console("EquationTableElm row " + row + ": Parse error in initial equation '" + initEq + "': " + err);
                compiledInitialExprs[row] = null;
            } else if (DEBUG) {
                CirSim.console("[EquationTableElm." + tableName + "] Row " + row + " initial equation parsed successfully");
            }
        } catch (Exception e) {
            CirSim.console("EquationTableElm row " + row + ": Error parsing initial equation '" + initEq + "': " + e.getMessage());
            compiledInitialExprs[row] = null;
        }
    }
    
    /**
     * Parse the main equation for a specific row.
     * Compiles the equation string into an expression tree for efficient evaluation.
     * @param row Row index
     */
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
    
    //=============================================================================
    // CIRCUIT ELEMENT INTERFACE - Convergence
    //=============================================================================
    
    /**
     * Calculate the convergence threshold for a row.
     * Uses adaptive tolerance that relaxes with more iterations.
     * 
     * @param row Row index
     * @return Convergence threshold value
     */
    double getConvergeLimit(int row) {
        // Adaptive tolerance: stricter early, relaxed if struggling
        double relativeTolerance;
        if (sim.subIterations < 10)
            relativeTolerance = 0.001;
        else if (sim.subIterations < 100)
            relativeTolerance = 0.01;
        else
            relativeTolerance = 0.1;
        
        // Scale by the magnitude of the values involved
        double maxMagnitude = Math.max(1.0, Math.abs(outputValues[row]));
        maxMagnitude = Math.max(maxMagnitude, Math.abs(lastOutputValues[row]));
        
        return maxMagnitude * relativeTolerance;
    }
    
    //=============================================================================
    // CIRCUIT ELEMENT INTERFACE - Simulation
    //=============================================================================
    
    /**
     * Set the base voltage source index for this element.
     * Each row gets a consecutive voltage source starting from this base.
     * 
     * @param j Index within element (only j=0 is used)
     * @param vs Voltage source index assigned by simulator
     */
    @Override
    void setVoltageSource(int j, int vs) {
        if (j == 0) {
            voltSource = vs;
        }
    }
    
    /**
     * Stamp the element into the circuit matrix.
     * Called once during circuit analysis setup.
     * 
     * For each row:
     * - Mark the voltage source row as nonlinear (values change each iteration)
     * - Stamp a voltage source from ground to the output node
     * - Add conditioning resistor to help matrix stability
     * - Connect to any matching labeled nodes
     */
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
            
            // Skip if output node is ground (would cause matrix issues)
            if (outputNode == 0) {
                CirSim.console("EquationTableElm [" + tableName + "] Row " + row + " (" + outputNames[row] + "): WARNING - Skipping stamp, output node is ground!");
                continue;
            }
            
            // Stamp the voltage source for this row
            sim.stampNonLinear(vn);
            sim.stampVoltageSource(0, outputNode, voltSource + row);
            
            // Add high-value resistor for matrix conditioning (helps convergence)
            sim.stampResistor(outputNode, 0, 1e8);
        }
        
        // Connect outputs to any labeled nodes with matching names
        connectToLabeledNodes();
    }
    
    /**
     * Connect each output row to its corresponding labeled node.
     * This allows the equation output to appear in labeled nodes with the same name,
     * making the value accessible to other circuit elements.
     */
    private void connectToLabeledNodes() {
        for (int row = 0; row < rowCount; row++) {
            String outputName = outputNames[row];
            if (outputName == null || outputName.trim().isEmpty()) continue;
            
            // Look up labeled node by name
            Integer labeledNodeNum = LabeledNodeElm.getByName(outputName.trim());
            int outputNode = nodes[row];
            
            if (labeledNodeNum != null && labeledNodeNum > 0 && outputNode > 0) {
                // Connect via very low resistance (essentially a wire)
                sim.stampResistor(outputNode, labeledNodeNum, 1e-6);
                
                if (DEBUG) {
                    CirSim.console("[EquationTableElm." + tableName + "] Connected row " + row + " (" + outputName + ") node " + outputNode + " to labeled node " + labeledNodeNum);
                }
            }
        }
    }
    
    /**
     * Perform one iteration step of the simulation.
     * Called multiple times per timestep until convergence is achieved.
     * 
     * For each row, this method:
     * 1. Handles initial value evaluation at t=0 (if initial equation exists)
     * 2. Evaluates the main equation using current circuit state
     * 3. Checks for convergence against previous iteration
     * 4. Stamps the computed value into the matrix right-hand side
     * 5. Registers the output as a computed value for other elements
     */
    void doStep() {
        if (DEBUG && sim.subIterations == 0) {
            CirSim.console("[EquationTableElm." + tableName + "] doStep() at t=" + sim.t);
        }
        
        for (int row = 0; row < rowCount; row++) {
            int vn = voltSource + row + sim.nodeList.size();
            
            // Handle initial value at t=0
            if (sim.t == 0 && compiledInitialExprs[row] != null) {
                // On first sub-iteration, stamp 0 as placeholder to let circuit solve first
                if (sim.subIterations == 0 && !initialValueApplied[row]) {
                    sim.stampRightSide(vn, 0);
                    continue;
                }
                
                // On subsequent iterations, evaluate the initial value expression
                if (!initialValueApplied[row]) {
                    evaluateInitialValue(row);
                }
                
                // Stamp the initial value (skip main equation at t=0)
                sim.stampRightSide(vn, outputValues[row]);
                continue;
            }
            
            // Normal timestep: evaluate main equation
            evaluateMainEquation(row, vn);
        }
    }
    
    /**
     * Evaluate the initial value expression for a row at t=0.
     * @param row Row index
     */
    private void evaluateInitialValue(int row) {
        ExprState state = exprStates[row];
        state.values[0] = sliderValues[row];
        state.t = 0;
        
        // Register slider variable for expression evaluation
        String sliderVar = sliderVarNames[row];
        if (sliderVar != null && !sliderVar.isEmpty()) {
            ComputedValues.setComputedValue(sliderVar, sliderValues[row]);
        }
        
        // Evaluate the initial expression
        double initialValue = compiledInitialExprs[row].eval(state);
        outputValues[row] = initialValue;
        lastOutputValues[row] = initialValue;
        exprStates[row].updateLastValues(initialValue);
        
        // Initialize integration state to start from initial value
        exprStates[row].lastIntOutput = initialValue;
        
        // Register immediately for other elements/rows to use
        String outputName = outputNames[row];
        if (outputName != null && !outputName.trim().isEmpty()) {
            ComputedValues.setComputedValue(outputName.trim(), initialValue);
        }
        
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] Row " + row + " (" + outputNames[row] + "): Applied initial value = " + initialValue);
        }
        
        initialValueApplied[row] = true;
    }
    
    /**
     * Evaluate the main equation for a row during normal simulation.
     * @param row Row index
     * @param vn Voltage source node in matrix
     */
    private void evaluateMainEquation(int row, int vn) {
        if (compiledExprs[row] == null) {
            if (DEBUG) {
                CirSim.console("[EquationTableElm." + tableName + "] Row " + row + ": NULL compiled expression!");
            }
            return;
        }
        
        ExprState state = exprStates[row];
        
        // Set up evaluation context
        state.values[0] = sliderValues[row];
        state.t = sim.t;
        
        // Register slider as computed value for E_NODE_REF lookups
        String sliderVar = sliderVarNames[row];
        if (sliderVar != null && !sliderVar.isEmpty()) {
            ComputedValues.setComputedValue(sliderVar, sliderValues[row]);
        }
        
        // Evaluate the equation
        double equationValue = compiledExprs[row].eval(state);
        
        // Debug output for troubleshooting
        if (DEBUG) {
            logEquationEvaluation(row, state, equationValue);
        }
        
        // Check equation convergence
        checkEquationConvergence(row, equationValue);
        lastOutputValues[row] = equationValue;
        outputValues[row] = equationValue;
        
        // Register output immediately for intra-table dependencies
        // This allows e.g. Y2 = Y1 + 1 to work within the same table
        String outputName = outputNames[row];
        if (outputName != null && !outputName.trim().isEmpty()) {
            ComputedValues.setComputedValue(outputName.trim(), equationValue);
        }
        
        // Check voltage convergence
        checkVoltageConvergence(row);
        
        // Stamp the computed value into the matrix
        sim.stampRightSide(vn, outputValues[row]);
    }
    
    /**
     * Check if the equation value has converged from the previous iteration.
     * @param row Row index
     * @param equationValue New computed value
     */
    private void checkEquationConvergence(int row, double equationValue) {
        double convergeLimit = getConvergeLimit(row);
        boolean converged = Math.abs(equationValue - lastOutputValues[row]) <= convergeLimit;
        
        if (!converged) {
            sim.converged = false;
            if (DEBUG) {
                CirSim.console("  Equation NOT converged: diff=" + Math.abs(equationValue - lastOutputValues[row]) + ", limit=" + convergeLimit);
            }
        } else if (DEBUG) {
            CirSim.console("  Equation converged");
        }
    }
    
    /**
     * Check if the output voltage has converged to the computed value.
     * @param row Row index
     */
    private void checkVoltageConvergence(int row) {
        double outputVoltage = volts[row];
        double voltageDiff = Math.abs(outputVoltage - outputValues[row]);
        double threshold = Math.max(Math.abs(outputValues[row]) * 0.01, 1e-6);
        boolean converged = voltageDiff <= threshold || sim.subIterations >= 100;
        
        if (!converged) {
            sim.converged = false;
            if (DEBUG) {
                CirSim.console("  Voltage NOT converged: diff=" + voltageDiff + ", threshold=" + threshold + ", subIter=" + sim.subIterations);
            }
        } else if (DEBUG) {
            CirSim.console("  Voltage converged");
        }
    }
    
    /**
     * Log detailed equation evaluation info for debugging.
     */
    private void logEquationEvaluation(int row, ExprState state, double equationValue) {
        CirSim.console("[EquationTableElm." + tableName + "] Row " + row + " (" + outputNames[row] + "):");
        CirSim.console("  Equation: " + equations[row]);
        CirSim.console("  Slider " + sliderVarNames[row] + "=" + sliderValues[row] + " (registered as computed value)");
        CirSim.console("  state.lastOutput=" + exprStates[row].lastOutput + " (from previous step)");
        CirSim.console("  state.values[0]=" + state.values[0]);
        CirSim.console("  state.t=" + state.t);
        
        // Log referenced labeled nodes
        CirSim.console("  Checking for labeled nodes in equation...");
        String[] labeledNodes = LabeledNodeElm.getSortedLabeledNodeNames();
        if (labeledNodes != null && labeledNodes.length > 0) {
            String eq = equations[row];
            for (String nodeName : labeledNodes) {
                if (eq.contains(nodeName)) {
                    Integer nodeNum = LabeledNodeElm.getByName(nodeName);
                    double nodeValue = 0;
                    if (nodeNum != null && nodeNum > 0 && nodeNum <= sim.nodeVoltages.length) {
                        nodeValue = sim.nodeVoltages[nodeNum - 1];
                    }
                    CirSim.console("    Found labeled node '" + nodeName + "' (node " + nodeNum + ") = " + nodeValue);
                }
            }
        } else {
            CirSim.console("    No labeled nodes found in circuit");
        }
        
        // Log computed value for slider
        CirSim.console("  Checking computed values...");
        Double sliderComputedVal = ComputedValues.getComputedValue(sliderVarNames[row]);
        if (sliderComputedVal != null) {
            CirSim.console("    Slider '" + sliderVarNames[row] + "' computed value = " + sliderComputedVal);
        }
        
        CirSim.console("  Evaluated value: " + equationValue);
        CirSim.console("  Last value: " + lastOutputValues[row]);
        CirSim.console("  Output voltage: " + volts[row]);
    }
    
    /**
     * Called after a successful timestep is completed.
     * Registers outputs as labeled nodes and updates expression states.
     */
    @Override
    public void stepFinished() {
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] stepFinished() - registering outputs:");
        }
        
        for (int row = 0; row < rowCount; row++) {
            // Register output as computed value
            String name = outputNames[row];
            if (name != null && !name.trim().isEmpty()) {
                ComputedValues.setComputedValue(name.trim(), outputValues[row]);
                ComputedValues.markComputedThisStep(name.trim());
                if (DEBUG) {
                    CirSim.console("  " + name.trim() + " = " + outputValues[row]);
                }
            }
            
            // Commit any pending integration
            if (exprStates[row] != null) {
                exprStates[row].commitIntegration(sim.timeStep);
            }
            
            // Update state for next timestep
            if (exprStates[row] != null) {
                exprStates[row].updateLastValues(outputValues[row]);
            }
        }
    }
    
    /**
     * Reset the element to initial state.
     * Called when simulation is reset.
     */
    @Override
    public void reset() {
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] reset() called");
        }
        super.reset();
        
        for (int row = 0; row < rowCount; row++) {
            // Reset expression state
            if (exprStates[row] != null) {
                exprStates[row].reset();
            }
            
            // Reset output values
            outputValues[row] = 0.0;
            lastOutputValues[row] = 0.0;
            initialValueApplied[row] = false;
            
            // Register initial values with ComputedValues
            String name = outputNames[row];
            if (name != null && !name.trim().isEmpty()) {
                ComputedValues.setComputedValue(name.trim(), outputValues[row]);
            }
        }
    }
    
    //=============================================================================
    // SERIALIZATION
    //=============================================================================
    
    /**
     * Serialize element state to a string for saving.
     * @return Serialized string representation
     */
    @Override
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.dump());
        sb.append(" ").append(CustomLogicModel.escape(tableName));
        sb.append(" ").append(rowCount);
        
        // Serialize each row's data
        for (int row = 0; row < rowCount; row++) {
            sb.append(" ").append(CustomLogicModel.escape(outputNames[row]));
            sb.append(" ").append(CustomLogicModel.escape(equations[row]));
            sb.append(" ").append(CustomLogicModel.escape(initialEquations[row] != null ? initialEquations[row] : ""));
            sb.append(" ").append(CustomLogicModel.escape(sliderVarNames[row]));
            sb.append(" ").append(sliderValues[row]);
        }
        
        return sb.toString();
    }
    
    //=============================================================================
    // DRAWING
    //=============================================================================
    
    /**
     * Override to hide posts - electrical connections remain functional
     * but visual connection dots are not drawn (like TableElm).
     */
    @Override
    void drawPosts(Graphics g) {
        // Intentionally empty - posts are hidden
    }
    
    /**
     * Draw the table element.
     * Renders the table background, title, rows, and hover tooltips.
     */
    void draw(Graphics g) {
        int tableX = x;
        int tableY = y;
        boolean selected = needsHighlight();
        
        // Draw table background
        g.setColor(Color.darkGray);
        g.fillRect(tableX, tableY, tableWidth, tableHeight);
        
        // Draw border (highlighted when selected)
        drawTableBorder(g, tableX, tableY, selected);
        
        // Draw title row
        drawTitleRow(g, tableX, tableY);
        
        // Update hover state
        updateHoveredRow(tableX, tableY);
        
        // Draw data rows
        g.setFont(valueFont);
        for (int row = 0; row < rowCount; row++) {
            drawDataRow(g, tableX, tableY, row);
        }
        
        // Draw tooltip for hovered row
        drawHoverTooltip(g, tableX);
        
        // Update bounding box
        setBbox(tableX, tableY, tableX + tableWidth, tableY + tableHeight);
    }
    
    /**
     * Draw the table border, with highlighting when selected.
     */
    private void drawTableBorder(Graphics g, int tableX, int tableY, boolean selected) {
        g.setColor(selected ? selectColor : Color.gray);
        g.drawRect(tableX, tableY, tableWidth, tableHeight);
        if (selected) {
            g.drawRect(tableX + 1, tableY + 1, tableWidth - 2, tableHeight - 2);
        }
    }
    
    /**
     * Draw the title row with table name.
     */
    private void drawTitleRow(Graphics g, int tableX, int tableY) {
        g.setFont(labelFont);
        g.setColor(whiteColor);
        int titleY = tableY + rowHeight - cellPadding;
        drawCenteredText(g, tableName, tableX + tableWidth / 2, titleY - 2, true);
        
        // Separator line after title
        g.setColor(Color.gray);
        g.drawLine(tableX, tableY + rowHeight, tableX + tableWidth, tableY + rowHeight);
    }
    
    /**
     * Update which row the mouse is hovering over.
     */
    private void updateHoveredRow(int tableX, int tableY) {
        hoveredRow = -1;
        int mouseCircuitX = sim.inverseTransformX(sim.mouseCursorX);
        int mouseCircuitY = sim.inverseTransformY(sim.mouseCursorY);
        
        if (mouseCircuitX >= tableX && mouseCircuitX <= tableX + tableWidth &&
            mouseCircuitY >= tableY && mouseCircuitY <= tableY + tableHeight) {
            // Calculate row index, accounting for title row
            int relativeY = mouseCircuitY - (tableY + rowHeight);
            if (relativeY >= 0) {
                int mouseRowIndex = relativeY / rowHeight;
                if (mouseRowIndex >= 0 && mouseRowIndex < rowCount) {
                    hoveredRow = mouseRowIndex;
                }
            }
        }
    }
    
    /**
     * Draw a single data row.
     */
    private void drawDataRow(Graphics g, int tableX, int tableY, int row) {
        int rowY = tableY + (row + 1) * rowHeight;
        
        // Highlight hovered row
        if (row == hoveredRow) {
            g.setColor(new Color(80, 80, 100));
            g.fillRect(tableX + 1, rowY + 1, tableWidth - 2, rowHeight - 1);
        }
        
        // Build display equation with slider value substituted
        String displayEquation = buildDisplayEquation(row);
        String rowText = outputNames[row] + " = " + displayEquation;
        
        // Draw row text
        g.setColor(whiteColor);
        g.drawString(rowText, tableX + cellPadding, rowY + rowHeight - cellPadding - 2);
        
        // Draw current value on right side
        String valueText = getShortUnitText(outputValues[row], "");
        int valueWidth = (int) g.context.measureText(valueText).getWidth();
        g.setColor(Color.cyan);
        g.drawString(valueText, tableX + tableWidth - valueWidth - cellPadding, rowY + rowHeight - cellPadding - 2);
        
        // Draw initial value indicator if present
        drawInitialValueIndicator(g, tableX, rowY, row, valueWidth);
        
        // Draw row separator
        if (row < rowCount - 1) {
            g.setColor(Color.gray);
            int sepY = tableY + (row + 2) * rowHeight;
            g.drawLine(tableX, sepY, tableX + tableWidth, sepY);
        }
    }
    
    /**
     * Build the display equation string with slider variable substituted.
     */
    private String buildDisplayEquation(int row) {
        String displayEquation = equations[row];
        displayEquation = Locale.convertGreekSymbols(displayEquation);
        
        // Substitute slider variable with its value
        String sliderVar = sliderVarNames[row];
        if (sliderVar != null && !sliderVar.isEmpty()) {
            String valueStr = getShortUnitText(sliderValues[row], "");
            displayEquation = displayEquation.replaceAll("\\b" + sliderVar + "\\b", valueStr);
        }
        
        return displayEquation;
    }
    
    /**
     * Draw the initial value indicator for a row (shown in yellow brackets).
     */
    private void drawInitialValueIndicator(Graphics g, int tableX, int rowY, int row, int valueWidth) {
        String initEq = initialEquations[row];
        if (initEq == null || initEq.trim().isEmpty()) {
            return;
        }
        
        Font smallFont = new Font("SansSerif", 0, opsize == 2 ? 8 : 7);
        g.setFont(smallFont);
        String initText = "[" + initEq + "]";
        int initWidth = (int) g.context.measureText(initText).getWidth();
        g.setColor(Color.yellow);
        g.drawString(initText, tableX + tableWidth - valueWidth - initWidth - cellPadding * 2, rowY + rowHeight - cellPadding - 2);
        g.setFont(valueFont);
    }
    
    /**
     * Draw tooltip for hovered row if a hint is available.
     */
    private void drawHoverTooltip(Graphics g, int tableX) {
        if (hoveredRow < 0 || hoveredRow >= rowCount) {
            return;
        }
        
        String hint = HintRegistry.getHint(outputNames[hoveredRow]);
        if (hint == null || hint.trim().isEmpty()) {
            return;
        }
        
        int mouseCircuitX = sim.inverseTransformX(sim.mouseCursorX);
        int mouseCircuitY = sim.inverseTransformY(sim.mouseCursorY);
        
        g.setFont(valueFont);
        int hintWidth = (int) g.context.measureText(hint).getWidth() + 8;
        int hintHeight = opsize == 1 ? 12 : 16;
        
        // Position above the mouse cursor
        int tooltipX = mouseCircuitX - hintWidth / 2;
        int tooltipY = mouseCircuitY - hintHeight - 4;
        
        // Keep tooltip within table bounds horizontally
        tooltipX = Math.max(tooltipX, tableX);
        tooltipX = Math.min(tooltipX, tableX + tableWidth - hintWidth);
        
        // Draw tooltip background
        g.setColor(new Color(60, 60, 80));
        g.fillRect(tooltipX, tooltipY, hintWidth, hintHeight);
        g.setColor(Color.gray);
        g.drawRect(tooltipX, tooltipY, hintWidth, hintHeight);
        
        // Draw tooltip text
        g.setColor(Color.yellow);
        g.drawString(hint, tooltipX + 4, tooltipY + hintHeight - 3);
    }
    
    //=============================================================================
    // EDIT DIALOG
    //=============================================================================
    
    /**
     * Get edit field information for the properties dialog.
     */
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            return EditInfo.createCheckbox("Small", (flags & FLAG_SMALL) != 0);
        }
        return null;
    }
    
    /**
     * Set a field value from the edit dialog.
     */
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
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
    
    //=============================================================================
    // INFO DISPLAY
    //=============================================================================
    
    /**
     * Get information strings for mouse-over display.
     */
    @Override
    void getInfo(String arr[]) {
        arr[0] = "Equation Table: " + tableName;
        
        if (hoveredRow >= 0 && hoveredRow < rowCount) {
            // Show detailed info for hovered row
            getHoveredRowInfo(arr);
        } else {
            // Show summary when not hovering over any row
            getSummaryInfo(arr);
        }
    }
    
    /**
     * Get info display for a hovered row.
     */
    private void getHoveredRowInfo(String[] arr) {
        // Show hint first if available
        String hint = HintRegistry.getHint(outputNames[hoveredRow]);
        if (hint != null && !hint.trim().isEmpty()) {
            arr[1] = hint;
        } else {
            arr[1] = "Row " + (hoveredRow + 1) + ": " + outputNames[hoveredRow];
        }
        
        arr[2] = "Row " + (hoveredRow + 1) + ": " + outputNames[hoveredRow];
        arr[3] = "Equation: " + equations[hoveredRow];
        
        String initEq = initialEquations[hoveredRow];
        arr[4] = "Initial (t=0): " + (initEq != null && !initEq.trim().isEmpty() ? initEq : "(none)");
        
        arr[5] = "Slider: " + sliderVarNames[hoveredRow] + " = " + getShortUnitText(sliderValues[hoveredRow], "");
        arr[6] = "Output: " + getUnitText(outputValues[hoveredRow], "");
        
        if (compiledExprs[hoveredRow] == null) {
            arr[7] = "⚠ Equation parse error";
        }
    }
    
    /**
     * Get summary info when not hovering over a specific row.
     */
    private void getSummaryInfo(String[] arr) {
        arr[1] = "Rows: " + rowCount;
        
        int idx = 2;
        for (int row = 0; row < rowCount && idx < arr.length - 1; row++) {
            arr[idx++] = outputNames[row] + " = " + getUnitText(outputValues[row], "");
        }
    }
    
    /**
     * Custom formatting for slider fields in edit dialog.
     */
    public String getSliderUnitText(int n, EditInfo ei, double value) {
        int fieldOffset = n - 2;
        int fieldInRow = fieldOffset % 5;
        
        // Only format slider value fields (fieldInRow == 3 in old 5-field layout)
        if (fieldInRow == 3) {
            return getShortUnitText(value, "");
        }
        return null;
    }
    
    //=============================================================================
    // PUBLIC ACCESSORS
    //=============================================================================
    
    /** Get the table name */
    public String getTableName() { return tableName; }
    
    /** Set the table name */
    public void setTableName(String name) { tableName = name; }
    
    /** Get the number of active rows */
    public int getRowCount() { return rowCount; }
    
    /** Set the number of active rows (1 to MAX_ROWS) */
    public void setRowCount(int count) { 
        if (count >= 1 && count <= MAX_ROWS) {
            rowCount = count;
        }
    }
    
    /** Get output name for a row */
    public String getOutputName(int row) {
        return (row >= 0 && row < MAX_ROWS) ? outputNames[row] : "";
    }
    
    /** Set output name for a row */
    public void setOutputName(int row, String name) {
        if (row >= 0 && row < MAX_ROWS) {
            outputNames[row] = name;
        }
    }
    
    /** Get equation for a row */
    public String getEquation(int row) {
        return (row >= 0 && row < MAX_ROWS) ? equations[row] : "";
    }
    
    /** Set equation for a row */
    public void setEquation(int row, String eq) {
        if (row >= 0 && row < MAX_ROWS) {
            equations[row] = eq;
        }
    }
    
    /** Get initial equation for a row */
    public String getInitialEquation(int row) {
        return (row >= 0 && row < MAX_ROWS) ? initialEquations[row] : "";
    }
    
    /** Set initial equation for a row */
    public void setInitialEquation(int row, String eq) {
        if (row >= 0 && row < MAX_ROWS) {
            initialEquations[row] = eq;
        }
    }
    
    /** Get slider variable name for a row */
    public String getSliderVarName(int row) {
        return (row >= 0 && row < MAX_ROWS) ? sliderVarNames[row] : "";
    }
    
    /** Set slider variable name for a row */
    public void setSliderVarName(int row, String name) {
        if (row >= 0 && row < MAX_ROWS) {
            sliderVarNames[row] = name;
        }
    }
    
    /** Get slider value for a row */
    public double getSliderValue(int row) {
        return (row >= 0 && row < MAX_ROWS) ? sliderValues[row] : 0.0;
    }
    
    /** Set slider value for a row */
    public void setSliderValue(int row, double val) {
        if (row >= 0 && row < MAX_ROWS) {
            sliderValues[row] = val;
        }
    }
    
    /** Public method for dialog to trigger equation reparse */
    public void parseAllEquationsPublic() {
        parseAllEquations();
    }
    
    //=============================================================================
    // EDIT DIALOG LAUNCHER
    //=============================================================================
    
    /**
     * Open the custom edit dialog for this element.
     * Called from CirSim.onDoubleClick.
     */
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
