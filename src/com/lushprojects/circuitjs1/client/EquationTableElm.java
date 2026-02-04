/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.google.gwt.event.dom.client.MouseWheelEvent;
import com.google.gwt.event.dom.client.MouseWheelHandler;

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
/**
 * EquationTableElm - Pure Computational Equation Table
 * 
 * A table of named equations that compute values each timestep.
 * This is a PURE COMPUTATIONAL element:
 * - No electrical posts or circuit connections
 * - Values are written to ComputedValues registry
 * - Other elements can read values via ComputedValues
 * - Use ComputedValueSourceElm to bridge values into electrical domain
 * 
 * Features:
 * - Multiple rows of named equations
 * - Initial value equations for t=0
 * - Slider variables for interactive adjustment
 * - integrate() and diff() functions for dynamic systems
 */
class EquationTableElm extends CircuitElm implements MouseWheelHandler {
    
    //=============================================================================
    // CONSTANTS
    //=============================================================================
    
    /** Flag indicating small display mode */
    private static final int FLAG_SMALL = 1;
    
    /** Maximum number of equation rows supported */
    private static final int MAX_ROWS = 32;
    
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
    private int opsize;
    
    /** Calculated table width in pixels */
    private int tableWidth;
    
    /** Calculated table height in pixels */
    private int tableHeight;
    
    /** Height of each row in pixels */
    private int rowHeight = 18;
    
    /** Padding inside cells */
    private int cellPadding = 4;
    
    /** Renderer for drawing the table */
    private EquationTableRenderer renderer;
    
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
        
        // Create renderer
        renderer = new EquationTableRenderer(this);
        renderer.updateFonts(opsize);
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
        
        // Create renderer
        renderer = new EquationTableRenderer(this);
        renderer.updateFonts(opsize);
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
        if (renderer != null) {
            renderer.updateFonts(opsize);
        }
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
        
        // Use canvas context for accurate text measurement when available
        int maxTextWidth = 50;  // Minimum width in pixels
        
        if (sim != null && sim.cvcontext != null) {
            // Accurate measurement using canvas context
            String titleFontSpec = (opsize == 1 ? "10" : "12") + "pt SansSerif";
            String rowFontSpec = (opsize == 1 ? "8" : "10") + "pt SansSerif";
            String initFontSpec = (opsize == 1 ? "7" : "8") + "pt SansSerif";
            
            // Measure title width
            sim.cvcontext.setFont(titleFontSpec);
            maxTextWidth = Math.max(maxTextWidth, (int) sim.cvcontext.measureText(tableName).getWidth());
            
            // Measure each row
            sim.cvcontext.setFont(rowFontSpec);
            int scrollIconWidth = opsize == 1 ? 10 : 14;
            int valueSpacing = opsize == 1 ? 16 : 20;
            // valueSpacing = 10; // No extra spacing needed

            for (int row = 0; row < rowCount; row++) {
                String displayText = outputNames[row] + " = " + equations[row];
                int rowWidth = scrollIconWidth + (int) sim.cvcontext.measureText(displayText).getWidth() + valueSpacing;
                
                // Include initial equation width if present
                String initEq = initialEquations[row];
                if (initEq != null && !initEq.trim().isEmpty()) {
                    sim.cvcontext.setFont(initFontSpec);
                    rowWidth += (int) sim.cvcontext.measureText("[" + initEq + "]").getWidth() + cellPadding;
                    sim.cvcontext.setFont(rowFontSpec);
                }
                maxTextWidth = Math.max(maxTextWidth, rowWidth);
            }
        } /* else {
            // Fallback: estimate using average character widths
            double avgCharWidth = opsize == 1 ? 4.5 : 5.5;
            double titleCharWidth = opsize == 1 ? 5.5 : 6.5;
            maxTextWidth = Math.max(maxTextWidth, (int)(tableName.length() * titleCharWidth));
            
            int scrollIconWidth = opsize == 1 ? 10 : 14;
            int valueSpacing = opsize == 1 ? 20 : 24;
            
            for (int row = 0; row < rowCount; row++) {
                String displayText = outputNames[row] + " = " + equations[row];
                int rowWidth = scrollIconWidth + (int)(displayText.length() * avgCharWidth) + valueSpacing;
                
                String initEq = initialEquations[row];
                if (initEq != null && !initEq.trim().isEmpty()) {
                    double initCharWidth = opsize == 1 ? 3.5 : 4.5;
                    rowWidth += (int)((initEq.length() + 2) * initCharWidth);
                }
                maxTextWidth = Math.max(maxTextWidth, rowWidth);
            }
        } */
        
        // Calculate final dimensions with padding
        tableWidth = Math.max(80, maxTextWidth + cellPadding * 2);
        tableHeight = (rowCount + 1) * rowHeight + cellPadding * 2;  // +1 for title row
        
        // Set bounding box for hit testing
        setBbox(x, y, x + tableWidth, y + tableHeight);
        
        // Update x2, y2 for proper bounds
        x2 = x + tableWidth;
        y2 = y + tableHeight;
    }
    
    //=============================================================================
    // CIRCUIT ELEMENT INTERFACE - Identification
    //=============================================================================
    
    /** @return Dump type identifier for serialization (266) */
    int getDumpType() { return 266; }
    
    /** @return Number of electrical posts - 0 for pure computational */
    int getPostCount() { return 0; }
    
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
    
    /** @return Number of voltage sources needed - 0 for pure computational */
    int getVoltageSourceCount() { return 0; }
    
    /** @return true - equations may depend on other values that change */
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
     * Pure computational - no posts, no current.
     */
    double getCurrentIntoNode(int n) {
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
        // More lenient thresholds help diff() equations converge faster
        double relativeTolerance;
        if (sim.subIterations < 3)
            relativeTolerance = 0.001;
        else if (sim.subIterations < 10)
            relativeTolerance = 0.01;
        else if (sim.subIterations < 50)
            relativeTolerance = 0.05;
        else
            relativeTolerance = 0.1;
        
        // For diff() equations, increase tolerance to account for division by timestep
        // which amplifies small input variations
        if (equations[row].contains("diff")) {
            relativeTolerance *= 10;  // 10x more lenient for diff equations
        }
        
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
     * Pure computational - does nothing.
     */
    void stamp() {
        // Pure computational element - no MNA matrix participation
    }
    
    // connectToLabeledNodes removed - pure computational uses ComputedValues instead
    
    /**
     * Perform one iteration step of the simulation.
     * Pure computational: evaluates equations and writes to ComputedValues.
     * 
     * For each row:
     * 1. Handles initial value evaluation at t=0
     * 2. Evaluates the main equation
     * 3. Checks for convergence
     * 4. Registers output in ComputedValues
     */
    void doStep() {
        if (DEBUG && sim.subIterations == 0) {
            CirSim.console("[EquationTableElm." + tableName + "] doStep() at t=" + sim.t);
        }
        
        for (int row = 0; row < rowCount; row++) {
            // Handle initial value at t=0
            if (sim.t == 0 && compiledInitialExprs[row] != null) {
                // On first sub-iteration, use 0 as placeholder
                if (sim.subIterations == 0 && !initialValueApplied[row]) {
                    outputValues[row] = 0;
                    continue;
                }
                
                // On subsequent iterations, evaluate the initial value expression
                if (!initialValueApplied[row]) {
                    evaluateInitialValue(row);
                }
                continue;
            }
            
            // Normal timestep: evaluate main equation
            evaluateMainEquationPure(row);
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
     * Pure computational - writes to ComputedValues instead of MNA.
     * @param row Row index
     */
    private void evaluateMainEquationPure(int row) {
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
        
        // Register slider as computed value for lookups
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
        
        // Register output in ComputedValues for other elements to use
        String outputName = outputNames[row];
        if (outputName != null && !outputName.trim().isEmpty()) {
            ComputedValues.setComputedValue(outputName.trim(), equationValue);
        }
    }
    
    // Legacy method kept for compatibility - not used in pure computational mode
    private void evaluateMainEquation(int row, int vn) {
        evaluateMainEquationPure(row);
    }
    
    /**
     * Check if the equation value has converged from the previous iteration.
     * @param row Row index
     * @param equationValue New computed value
     */
    private void checkEquationConvergence(int row, double equationValue) {
        double convergeLimit = getConvergeLimit(row);
        double diff = Math.abs(equationValue - lastOutputValues[row]);
        boolean converged = diff <= convergeLimit;
        
        // For diff() equations, skip convergence check during early subiterations
        // because the input to diff() needs time to settle first
        boolean hasDiff = equations[row].contains("diff");
        
        if (hasDiff && sim.subIterations < 5) {
            // Don't report non-convergence yet - let input settle
            return;
        }
        
        if (!converged) {
            sim.converged = false;
            if (DEBUG) {
                CirSim.console("  Equation NOT converged: diff=" + diff + ", limit=" + convergeLimit);
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
        
        // For diff() equations, use much larger threshold since derivative amplifies variations
        boolean hasDiff = equations[row].contains("diff");
        if (hasDiff) {
            threshold = Math.max(Math.abs(outputValues[row]) * 0.1, 0.01);  // 10% or 0.01 minimum
        }
        
        boolean converged = voltageDiff <= threshold || sim.subIterations >= 100;
        
        // Log voltage convergence for diff equations
        if (hasDiff) {
            CirSim.console("[diff voltage] " + outputNames[row] + " subIter=" + sim.subIterations +
                ", voltageDiff=" + voltageDiff + ", threshold=" + threshold + 
                ", converged=" + converged);
        }
        
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
     * Delegates to EquationTableRenderer for actual drawing.
     */
    void draw(Graphics g) {
        renderer.draw(g);
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
        } else if (isAdjustableRow(hoveredRow)) {
            arr[7] = "scroll to adjust value";
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
    
    /** Get the table width in pixels */
    public int getTableWidth() { return tableWidth; }
    
    /** Get the table height in pixels */
    public int getTableHeight() { return tableHeight; }
    
    /** Get the row height in pixels */
    public int getRowHeight() { return rowHeight; }
    
    /** Get the cell padding in pixels */
    public int getCellPadding() { return cellPadding; }
    
    /** Get the display size mode (1 = small, 2 = normal) */
    public int getOpsize() { return opsize; }
    
    /** Get the hovered row index (-1 if none) */
    public int getHoveredRow() { return hoveredRow; }
    
    /** Set the hovered row index and update cursor for adjustable rows */
    public void setHoveredRow(int row) {
        hoveredRow = row;
        // Update cursor based on whether row is adjustable
        if (row >= 0 && isAdjustableRow(row)) {
            sim.setCursorStyle("cursorAdjust");
        } else {
            // Reset cursor to default based on mouse mode
            sim.setCursorStyle(sim.mouseMode == CirSim.MODE_ADD_ELM ? "cursorCross" : "cursorPointer");
        }
    }
    
    /** Get output value for a row */
    public double getOutputValue(int row) {
        return (row >= 0 && row < MAX_ROWS) ? outputValues[row] : 0.0;
    }
    
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
    
    //=============================================================================
    // MOUSE WHEEL HANDLING
    //=============================================================================
    
    /**
     * Handle mouse wheel events to adjust numeric equation values.
     * Only works when hovering over a row whose equation is a simple number.
     */
    public void onMouseWheel(MouseWheelEvent e) {
        // Must have a hovered row
        if (hoveredRow < 0 || hoveredRow >= rowCount) return;
        
        // Check if equation is a simple number
        String eq = equations[hoveredRow].trim();
        Double currentValue = parseNumericEquation(eq);
        if (currentValue == null) return;  // Not a simple number
        
        // Push undo state on first wheel movement
        sim.pushUndo();
        
        // Calculate step size based on value magnitude
        double magnitude = Math.abs(currentValue);
        double scale;
        if (magnitude == 0) {
            scale = 0.1;  // Default step for zero
        } else if (magnitude < 1) {
            // For small values, use finer steps
            scale = Math.pow(10, Math.floor(Math.log10(magnitude)) - 1);
        } else {
            // Step size is ~1% of magnitude, rounded to power of 10
            scale = Math.pow(10, Math.floor(Math.log10(magnitude)) - 1);
        }
        
        // Apply change based on wheel direction (negative deltaY = scroll up = increase)
        double delta = e.getDeltaY() * sim.wheelSensitivity;
        int direction = (delta > 0) ? -1 : 1;  // scroll down = decrease, scroll up = increase
        double newValue = currentValue + direction * scale;
        
        // Update equation string
        equations[hoveredRow] = formatNumericValue(newValue);
        parseEquation(hoveredRow);
        sim.needAnalyze();
    }
    
    /**
     * Try to parse equation as a simple numeric value.
     * @param eq The equation string
     * @return The numeric value, or null if not a simple number
     */
    private Double parseNumericEquation(String eq) {
        if (eq == null || eq.isEmpty()) return null;
        
        try {
            // Handle optional leading sign
            String trimmed = eq.trim();
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    
    /**
     * Check if a row's equation is adjustable via mouse wheel.
     * @param row Row index
     * @return true if equation is a simple numeric value
     */
    public boolean isAdjustableRow(int row) {
        if (row < 0 || row >= rowCount) return false;
        return parseNumericEquation(equations[row]) != null;
    }
    
    /**
     * Format a numeric value for display as an equation.
     * Shows maximum of 4 significant digits.
     * @param val The value to format
     * @return Formatted string
     */
    private String formatNumericValue(double val) {
        // Handle zero specially
        if (val == 0) return "0";
        
        double absVal = Math.abs(val);
        
        // For integers in reasonable range that fit in 4 digits, use integer format
        if (val == Math.floor(val) && absVal < 10000 && absVal >= 1) {
            return String.valueOf((long) val);
        }
        
        // Calculate number of digits before decimal point
        int digitsBeforeDecimal = (absVal >= 1) ? (int) Math.floor(Math.log10(absVal)) + 1 : 0;
        
        // Calculate decimal places needed for 4 significant figures
        int decimalPlaces = Math.max(0, 4 - digitsBeforeDecimal);
        
        // For very small or very large values, use scientific notation with 4 sig figs
        if (absVal < 1e-4 || absVal >= 1e6) {
            // Format with 3 decimal places in scientific notation (4 sig figs total)
            double exponent = Math.floor(Math.log10(absVal));
            double mantissa = val / Math.pow(10, exponent);
            String mantissaStr = formatDecimal(mantissa, 3);
            return mantissaStr + "e" + (int) exponent;
        }
        
        // Format with appropriate decimal places
        return formatDecimal(val, decimalPlaces);
    }
    
    /**
     * Format a double with specified decimal places, trimming trailing zeros.
     */
    private String formatDecimal(double val, int decimalPlaces) {
        // Round to specified decimal places
        double factor = Math.pow(10, decimalPlaces);
        double rounded = Math.round(val * factor) / factor;
        
        // Convert to string
        String formatted;
        if (decimalPlaces == 0) {
            formatted = String.valueOf((long) rounded);
        } else {
            formatted = String.valueOf(rounded);
            
            // Trim trailing zeros after decimal point
            if (formatted.contains(".") && !formatted.contains("E") && !formatted.contains("e")) {
                formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
        }
        
        return formatted;
    }
}
