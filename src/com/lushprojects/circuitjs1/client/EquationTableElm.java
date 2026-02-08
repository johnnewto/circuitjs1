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
    
    /** Flag indicating MNA (electrical) mode vs pure computational mode */
    private static final int FLAG_MNA_MODE = 2;
    
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
    
    /** Track which rows have constant expressions (can be stamped linearly) */
    private boolean[] isConstantRow;
    
    /** Track which rows are node aliases (e.g. Cs ~ Cd) — no VS or matrix row needed */
    private boolean[] isAliasRow;
    
    /** Track which rows are linear combinations (e.g. Y ~ Cs + Is) — stamped as VCVS */
    private boolean[] isLinearRow;
    
    /** Track which linear rows need deferred VCVS stamping in postStamp() */
    private boolean[] needsPostStamp;
    
    /** Linear term coefficients for each row: nodeName -> coefficient */
    @SuppressWarnings("unchecked")
    private java.util.HashMap<String, Double>[] linearTerms = new java.util.HashMap[MAX_ROWS];
    
    /** Constant term for linear rows */
    private double[] linearConstant;
    
    /** Whether all alias rows have been successfully resolved to target nodes */
    private boolean aliasesResolved;
    
    //=============================================================================
    // INSTANCE STATE - UI Interaction
    //=============================================================================
    
    /** Currently hovered row index (-1 = none) - for tooltip display */
    private int hoveredRow = -1;
    
    /** Row that failed convergence (-1 = none) - for debugging */
    private int failedConvergenceRow = -1;
    
    /** Details about the convergence failure */
    private String convergenceFailureInfo = null;
    
    //=============================================================================
    // INSTANCE STATE - MNA Node Tracking (Hybrid Approach)
    //=============================================================================
    
    /** Node number for each row's output (-1 if none) */
    private int[] labeledNodeNumbers;
    
    /** Voltage source index for each row (-1 if none) */
    private int[] rowVoltSources;
    
    /** Total voltage sources allocated */
    private int voltSourceCount;
    
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
        flags |= FLAG_MNA_MODE;  // MNA mode is the default
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
        isConstantRow = new boolean[MAX_ROWS];
        isAliasRow = new boolean[MAX_ROWS];
        isLinearRow = new boolean[MAX_ROWS];
        needsPostStamp = new boolean[MAX_ROWS];
        linearConstant = new double[MAX_ROWS];
        
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
    
    /** @return true if in MNA (electrical) mode, false for pure computational */
    boolean isMnaMode() { return sim.equationTableMnaMode; }
    
    /** @return Number of electrical posts - always 0 (no visible posts) */
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
    
    /** 
     * @return Number of voltage sources needed - one per row with valid output name
     * This counts voltage sources before findLabeledNodes() runs, so we count all valid rows.
     */
    int getVoltageSourceCount() { 
        if (!isMnaMode()) return 0;
        
        updateRowClassifications();
        int count = 0;
        for (int row = 0; row < rowCount; row++) {
            if (isAliasRow[row]) continue;  // Aliases need no voltage source
            String outputName = outputNames[row];
            if (outputName != null && !outputName.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Override getInternalNodeCount to create internal nodes for rows
     * that do NOT have an existing LabeledNode on the canvas.
     */
    int getInternalNodeCount() {
        if (!isMnaMode()) return 0;
        
        updateRowClassifications();
        int count = 0;
        for (int row = 0; row < rowCount; row++) {
            if (isAliasRow[row]) continue;  // Aliases share target node, need no internal node
            String outputName = outputNames[row];
            if (outputName != null && !outputName.trim().isEmpty()) {
                // Only need internal node if NO existing LabeledNode
                Integer existingNode = LabeledNodeElm.getByName(outputName.trim());
                if (existingNode == null || existingNode < 0) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /** @return true if any row has a non-constant, non-alias, non-linear expression requiring iterative solving */
    boolean nonLinear() {
        updateRowClassifications();
        for (int row = 0; row < rowCount; row++) {
            if (!isConstantRow[row] && !isAliasRow[row] && !isLinearRow[row])
                return true;
        }
        return false;
    }
    
    /**
     * Update isConstantRow and isAliasRow flags based on compiled expressions.
     * 
     * Row classifications (checked in priority order):
     * 1. ALIAS: Expression is a bare node reference (E_NODE_REF), e.g. "Cs ~ Cd".
     *    The output name becomes an alias for the target node — no voltage source,
     *    no internal node, no matrix row needed. Eliminates 2 matrix rows per alias.
     * 2. CONSTANT: Expression contains only literal values and pure math, e.g. "rl ~ 0.025".
     *    Stamped once as a linear DC voltage source.
     * 3. DYNAMIC: Everything else — requires nonlinear doStep() evaluation.
     */
    private void updateRowClassifications() {
        for (int row = 0; row < rowCount; row++) {
            isConstantRow[row] = false;
            isAliasRow[row] = false;
            isLinearRow[row] = false;
            linearTerms[row] = null;
            linearConstant[row] = 0.0;
            
            if (compiledExprs[row] == null) continue;
            
            String initEq = initialEquations[row];
            boolean hasInitEq = (initEq != null && !initEq.trim().isEmpty());
            
            // Check alias first (bare node reference with no initial equation)
            if (!hasInitEq && compiledExprs[row].isNodeAlias()) {
                isAliasRow[row] = true;
                continue;
            }
            
            // Check constant (pure literal math with no initial equation)
            if (!hasInitEq && compiledExprs[row].isConstant()) {
                isConstantRow[row] = true;
                continue;
            }
            
            // Check linear combination (e.g., Y ~ Cs + Is, Y ~ 2*Cs - 3)
            if (!hasInitEq) {
                java.util.HashMap<String, Double> terms = new java.util.HashMap<String, Double>();
                Double constTerm = compiledExprs[row].getLinearTerms(terms);
                if (constTerm != null && !terms.isEmpty()) {
                    // It's a linear combination with at least one node reference
                    isLinearRow[row] = true;
                    linearTerms[row] = terms;
                    linearConstant[row] = constTerm.doubleValue();
                }
            }
        }
    }
    
    // Keep old name for any external callers
    private void updateConstantFlags() {
        updateRowClassifications();
    }
    
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
     * In MNA mode, returns the current from the voltage source for that row.
     * In pure computational mode, returns 0 (no posts, no current).
     */
    double getCurrentIntoNode(int n) {
        if (isMnaMode() && n < rowCount) {
            return current[n];
        }
        return 0;
    }
    
    /** Current for each row's voltage source (MNA mode only) */
    private double[] current = new double[MAX_ROWS];
    
    /**
     * Set current for a voltage source (called by simulator).
     * @param vs Voltage source index within this element
     * @param c Current value
     */
    @Override
    void setCurrent(int vs, double c) {
        if (vs < rowCount) {
            current[vs] = c;
        }
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
     * Find or create nodes for each row output.
     * If a LabeledNode exists on canvas, use that node.
     * Otherwise, use our internal node and register it in labelList.
     * Called during stamp() after internal nodes are allocated.
     */
    private void findLabeledNodes() {
        labeledNodeNumbers = new int[rowCount];
        rowVoltSources = new int[rowCount];
        voltSourceCount = 0;
        
        int internalNodeIdx = 0;  // Index into internal nodes (nodes[] array)
        
        for (int row = 0; row < rowCount; row++) {
            labeledNodeNumbers[row] = -1;
            rowVoltSources[row] = -1;
            
            // Alias rows don't need nodes or voltage sources
            if (isAliasRow[row]) continue;
            
            String outputName = outputNames[row];
            if (outputName == null || outputName.trim().isEmpty()) continue;
            
            // Check if a LabeledNode already exists on canvas
            Integer existingNodeNum = LabeledNodeElm.getByName(outputName.trim());
            
            if (existingNodeNum != null && existingNodeNum >= 0) {
                // Use existing LabeledNode's node - drive it with our voltage source
                labeledNodeNumbers[row] = existingNodeNum;
                rowVoltSources[row] = voltSourceCount;
                voltSourceCount++;
            } else {
                // No existing LabeledNode - use our internal node and register it
                if (nodes != null && internalNodeIdx < nodes.length) {
                    int internalNode = nodes[internalNodeIdx];
                    labeledNodeNumbers[row] = internalNode;
                    
                    // Register this internal node so other elements can find it by name
                    registerInternalNodeAsLabel(outputName.trim(), internalNode);
                    
                    internalNodeIdx++;
                    
                    rowVoltSources[row] = voltSourceCount;
                    voltSourceCount++;
                }
            }
        }
    }
    
    /**
     * Register an internal node number with LabeledNodeElm's static labelList.
     * This allows other elements to find this node by name.
     */
    private void registerInternalNodeAsLabel(String name, int nodeNum) {
        if (LabeledNodeElm.labelList == null) {
            LabeledNodeElm.resetNodeList();
        }
        
        LabeledNodeElm.LabelEntry entry = new LabeledNodeElm.LabelEntry();
        entry.node = nodeNum;
        entry.point = new Point(x, y);  // Use table position as dummy
        
        LabeledNodeElm.labelList.put(name, entry);
    }
    
    /**
     * Register alias rows: point the output name to the target node.
     * For "Cs ~ Cd", registers "Cs" in labelList pointing to the same node as "Cd".
     * This eliminates the voltage source and matrix row entirely —
     * Cs and Cd become the same electrical node.
     */
    private void registerAliasNodes() {
        aliasesResolved = true;  // Assume success; set false if any fail
        for (int row = 0; row < rowCount; row++) {
            if (!isAliasRow[row]) continue;
            
            String outputName = outputNames[row];
            if (outputName == null || outputName.trim().isEmpty()) continue;
            
            String targetName = compiledExprs[row].getNodeName();
            if (targetName == null) continue;
            
            // Look up the target node number
            Integer targetNodeNum = LabeledNodeElm.getByName(targetName);
            if (targetNodeNum != null && targetNodeNum >= 0) {
                // Register output name as pointing to the same node
                registerInternalNodeAsLabel(outputName.trim(), targetNodeNum);
                labeledNodeNumbers[row] = targetNodeNum;
                
                if (DEBUG) {
                    CirSim.console("[EquationTableElm." + tableName + "] Alias: " + 
                        outputName.trim() + " -> " + targetName + " (node " + targetNodeNum + ")");
                }
            } else {
                // Target node doesn't exist yet — will retry in startIteration()
                aliasesResolved = false;
                if (DEBUG) {
                    CirSim.console("[EquationTableElm." + tableName + "] Alias target '" + 
                        targetName + "' not found yet, deferring resolution");
                }
            }
        }
    }
    
    /**
     * Stamp the element into the circuit matrix.
     * In MNA mode, stamps voltage sources for each row output using hybrid approach.
     * In pure computational mode, does nothing.
     */
    void stamp() {
        // Update row classifications (alias, constant, dynamic)
        updateRowClassifications();
        
        if (!isMnaMode()) {
            // Pure computational mode: register constants and aliases immediately
            for (int row = 0; row < rowCount; row++) {
                if (isConstantRow[row] && compiledExprs[row] != null) {
                    double constantValue = compiledExprs[row].eval(exprStates[row]);
                    outputValues[row] = constantValue;
                    lastOutputValues[row] = constantValue;
                    String outputName = outputNames[row];
                    if (outputName != null && !outputName.trim().isEmpty()) {
                        ComputedValues.setComputedValue(outputName.trim(), constantValue);
                    }
                }
                // Alias rows in pure mode: no action needed at stamp time.
                // In doStep(), we'll copy the target's value to the alias name.
            }
            return;
        }
        
        // Find or create nodes for each row (skips alias rows)
        findLabeledNodes();
        
        // Register alias rows: point output name to target node (no VS, no matrix row)
        registerAliasNodes();
        
        // MNA mode: stamp voltage sources for each row
        for (int row = 0; row < rowCount; row++) {
            int nodeNum = labeledNodeNumbers[row];
            int vs = rowVoltSources[row];
            
            if (nodeNum >= 0 && vs >= 0) {
                if (isConstantRow[row]) {
                    // Constant expression: stamp as linear voltage source with fixed value
                    // Evaluate once and stamp directly (like DC VoltageElm)
                    double constantValue = compiledExprs[row].eval(exprStates[row]);
                    sim.stampVoltageSource(0, nodeNum, voltSource + vs, constantValue);
                    outputValues[row] = constantValue;
                    lastOutputValues[row] = constantValue;
                    
                    // Register in ComputedValues so other elements can reference it
                    String outputName = outputNames[row];
                    if (outputName != null && !outputName.trim().isEmpty()) {
                        ComputedValues.setComputedValue(outputName.trim(), constantValue);
                    }
                } else if (isLinearRow[row]) {
                    // Linear expression: stamp as VCVS (Voltage Controlled Voltage Source)
                    // For Y ~ c1*N1 + c2*N2 + const, stamp:
                    //   V_Y = c1*V_N1 + c2*V_N2 + const
                    // Using: stampVoltageSource for the output, stampVCVS for each input
                    
                    // First, verify ALL referenced nodes exist - if any are missing,
                    // defer to postStamp() where they should exist after all stamp() calls complete
                    boolean allNodesFound = true;
                    for (String refName : linearTerms[row].keySet()) {
                        Integer refNode = LabeledNodeElm.getByName(refName);
                        if (refNode == null || refNode < 0) {
                            allNodesFound = false;
                            break;
                        }
                    }
                    
                    if (allNodesFound) {
                        // All nodes found - stamp as linear VCVS now
                        sim.stampVoltageSource(0, nodeNum, voltSource + vs, linearConstant[row]);
                        stampLinearRow(row, nodeNum, vs);
                    } else {
                        // Defer VCVS stamping to postStamp() when all nodes will exist
                        needsPostStamp[row] = true;
                        sim.stampVoltageSource(0, nodeNum, voltSource + vs, linearConstant[row]);
                        if (DEBUG) {
                            CirSim.console("[EquationTableElm." + tableName + 
                                "] Linear row '" + outputNames[row] + "': deferring VCVS to postStamp()");
                        }
                    }
                } else {
                    // Non-linear expression: stamp as nonlinear (value set in doStep)
                    int vn = voltSource + vs + sim.nodeList.size();
                    sim.stampNonLinear(vn);
                    sim.stampVoltageSource(0, nodeNum, voltSource + vs);
                }
                
                // Stamp tiny load resistance to prevent matrix elimination
                double loadResistance = 1e9;
                sim.stampResistor(nodeNum, 0, loadResistance);
            }
        }
    }
    
    /**
     * Stamp a linear row as VCVS (Voltage Controlled Voltage Source).
     * Helper method used by both stamp() and postStamp().
     * 
     * @param row Row index
     * @param nodeNum Output node number
     * @param vs Voltage source index within this element
     */
    private void stampLinearRow(int row, int nodeNum, int vs) {
        // Stamp VCVS coefficients for each referenced node
        for (java.util.Map.Entry<String, Double> entry : linearTerms[row].entrySet()) {
            String refName = entry.getKey();
            double coef = entry.getValue();
            Integer refNode = LabeledNodeElm.getByName(refName);
            if (refNode != null && refNode >= 0) {
                // stampVCVS(controlNode, controlNode2, coef, voltSourceIdx)
                sim.stampVCVS(refNode, 0, coef, voltSource + vs);
            }
        }
        
        // Register initial value in ComputedValues
        String outputName = outputNames[row];
        if (outputName != null && !outputName.trim().isEmpty()) {
            ComputedValues.setComputedValue(outputName.trim(), linearConstant[row]);
        }
        
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + 
                "] Stamped linear VCVS for '" + outputNames[row] + "'");
        }
    }
    
    /**
     * Called after all elements have had stamp() called.
     * Handles deferred VCVS stamping for linear rows whose referenced nodes
     * weren't available during stamp() due to element ordering.
     */
    @Override
    void postStamp() {
        if (!isMnaMode()) return;
        
        for (int row = 0; row < rowCount; row++) {
            if (!needsPostStamp[row]) continue;
            
            int nodeNum = labeledNodeNumbers[row];
            int vs = rowVoltSources[row];
            
            if (nodeNum >= 0 && vs >= 0) {
                // Now all nodes should exist - stamp the VCVS coefficients
                boolean allNodesFound = true;
                String missingNode = null;
                for (String refName : linearTerms[row].keySet()) {
                    Integer refNode = LabeledNodeElm.getByName(refName);
                    if (refNode == null || refNode < 0) {
                        allNodesFound = false;
                        missingNode = refName;
                        break;
                    }
                }
                
                if (allNodesFound) {
                    stampLinearRow(row, nodeNum, vs);
                    needsPostStamp[row] = false;  // Successfully stamped
                } else {
                    // Node not found even after all stamp() calls - it's probably defined
                    // in ComputedValues (pure computational) rather than as an MNA node.
                    // Fall back to nonlinear evaluation which can read from both sources.
                    if (DEBUG) {
                        CirSim.console("[EquationTableElm." + tableName + 
                            "] postStamp: node '" + missingNode + "' not an MNA node for row '" + 
                            outputNames[row] + "', using nonlinear evaluation");
                    }
                    isLinearRow[row] = false;
                    int vn = voltSource + vs + sim.nodeList.size();
                    sim.stampNonLinear(vn);
                }
            }
        }
    }
    
    // connectToLabeledNodes removed - pure computational uses ComputedValues instead
    
    /**
     * Perform one iteration step of the simulation.
     * 
     * In pure computational mode: evaluates equations and writes to ComputedValues.
     * In MNA mode: evaluates equations and stamps voltage sources.
     * 
     * For each row:
     * 1. Handles initial value evaluation at t=0
     * 2. Evaluates the main equation
     * 3. Checks for convergence
     * 4. Outputs to ComputedValues (pure) or MNA matrix (electrical)
     */
    
    /**
     * Called once per timestep before subiterations begin.
     * Resolves any alias rows whose targets weren't available during stamp()
     * (due to stamp ordering — target table may stamp after this table).
     * Also seeds alias ComputedValues for the upcoming doStep cycle.
     */
    @Override
    public void startIteration() {
        // Retry alias resolution if any failed during stamp()
        if (!aliasesResolved && isMnaMode()) {
            registerAliasNodes();
        }
        
        // Seed alias values into ComputedValues so other expressions can find them.
        // In MNA mode, the alias shares the target's node, so read the node voltage
        // and write it to ComputedValues (current buffer, not pending).
        if (isMnaMode()) {
            for (int row = 0; row < rowCount; row++) {
                if (!isAliasRow[row]) continue;
                String outputName = outputNames[row];
                if (outputName == null || outputName.trim().isEmpty()) continue;
                
                String targetName = compiledExprs[row].getNodeName();
                if (targetName != null) {
                    // Read target voltage and seed into ComputedValues
                    double val = sim.getLabeledNodeVoltage(targetName);
                    outputValues[row] = val;
                    // Write directly to current buffer so it's available during doStep
                    ComputedValues.setComputedValueDirect(outputName.trim(), val);
                }
            }
        }
    }
    
    void doStep() {
        if (DEBUG && sim.subIterations == 0) {
            CirSim.console("[EquationTableElm." + tableName + "] doStep() at t=" + sim.t + " mnaMode=" + isMnaMode());
        }
        
        for (int row = 0; row < rowCount; row++) {
            // Skip constant rows - they were stamped linearly in stamp()
            if (isConstantRow[row]) continue;
            
            // Skip linear rows in MNA mode - they were stamped as VCVS in stamp()
            // (Still need to update outputValues for display purposes)
            if (isLinearRow[row] && isMnaMode()) {
                // Read the computed voltage from the labeled node
                String outputName = outputNames[row];
                if (outputName != null && !outputName.trim().isEmpty()) {
                    double val = sim.getLabeledNodeVoltage(outputName.trim());
                    outputValues[row] = val;
                    lastOutputValues[row] = val;
                    ComputedValues.setComputedValue(outputName.trim(), val);
                }
                continue;
            }
            
            // Alias rows: copy target value to ComputedValues so other expressions
            // can resolve the alias name. In MNA mode, read from shared node voltage.
            // In pure mode, read from ComputedValues.
            if (isAliasRow[row]) {
                String targetName = compiledExprs[row].getNodeName();
                double val;
                if (isMnaMode()) {
                    val = sim.getLabeledNodeVoltage(targetName);
                } else {
                    Double targetValue = ComputedValues.getComputedValue(targetName);
                    val = (targetValue != null) ? targetValue.doubleValue() : 0.0;
                }
                outputValues[row] = val;
                lastOutputValues[row] = val;
                String outputName = outputNames[row];
                if (outputName != null && !outputName.trim().isEmpty()) {
                    ComputedValues.setComputedValue(outputName.trim(), val);
                }
                continue;
            }
            
            // Handle initial value at t=0
            if (sim.t == 0 && compiledInitialExprs[row] != null) {
                // On first sub-iteration, use 0 as placeholder
                if (sim.subIterations == 0 && !initialValueApplied[row]) {
                    outputValues[row] = 0;
                    if (isMnaMode() && rowVoltSources != null && rowVoltSources[row] >= 0) {
                        int vn = voltSource + rowVoltSources[row] + sim.nodeList.size();
                        sim.stampRightSide(vn, 0);
                    }
                    continue;
                }
                
                // On subsequent iterations, evaluate the initial value expression
                if (!initialValueApplied[row]) {
                    evaluateInitialValue(row);
                }
                continue;
            }
            
            // Normal timestep: evaluate main equation
            if (isMnaMode()) {
                evaluateMainEquationMna(row);
            } else {
                evaluateMainEquationPure(row);
            }
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
        
        // In MNA mode, stamp the initial value via stampRightSide (consistent with EquationElm)
        if (isMnaMode() && rowVoltSources != null && rowVoltSources[row] >= 0) {
            int vn = voltSource + rowVoltSources[row] + sim.nodeList.size();
            sim.stampRightSide(vn, initialValue);
        }
        
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
    
    /**
     * Evaluate the main equation for a row in MNA mode.
     * Stamps the computed value to the voltage source in the MNA matrix.
     * 
     * Following INTERNALS.md and EquationElm pattern:
     * - Calculate vn = voltage source row in matrix
     * - Use stampRightSide(vn, value) to set the voltage
     * 
     * @param row Row index
     */
    private void evaluateMainEquationMna(int row) {
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
        
        // Stamp to MNA matrix via stampRightSide (consistent with EquationElm)
        // vn = voltage source row in matrix
        if (rowVoltSources != null && rowVoltSources[row] >= 0) {
            int vn = voltSource + rowVoltSources[row] + sim.nodeList.size();
            sim.stampRightSide(vn, equationValue);
        }
        
        // Also register in ComputedValues for consistency
        String outputName = outputNames[row];
        if (outputName != null && !outputName.trim().isEmpty()) {
            ComputedValues.setComputedValue(outputName.trim(), equationValue);
        }
    }
    
    // Legacy method kept for compatibility
    private void evaluateMainEquation(int row, int vn) {
        if (isMnaMode()) {
            evaluateMainEquationMna(row);
        } else {
            evaluateMainEquationPure(row);
        }
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
            failedConvergenceRow = row;
            convergenceFailureInfo = outputNames[row] + ": diff=" + getShortUnitText(diff, "") + 
                ", limit=" + getShortUnitText(convergeLimit, "") + 
                ", last=" + getShortUnitText(lastOutputValues[row], "") +
                ", new=" + getShortUnitText(equationValue, "");
            if (sim.subIterations > sim.convergenceCheckThreshold) {
                CirSim.console("[" + tableName + "] t=" + sim.t + " dt=" + sim.timeStep + " Convergence failure: " + convergenceFailureInfo);
            }
        } else if (failedConvergenceRow == row) {
            // This row converged now, clear the failure
            failedConvergenceRow = -1;
            convergenceFailureInfo = null;
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
            // For alias rows in MNA mode, read the shared node voltage
            // and register in ComputedValues for consistency
            if (isAliasRow[row]) {
                String name = outputNames[row];
                if (name != null && !name.trim().isEmpty()) {
                    String targetName = compiledExprs[row].getNodeName();
                    if (isMnaMode()) {
                        // MNA mode: read voltage from the shared node via labeled node lookup
                        outputValues[row] = sim.getLabeledNodeVoltage(targetName);
                    } else {
                        // Pure mode: copy from target ComputedValue
                        Double targetValue = ComputedValues.getComputedValue(targetName);
                        outputValues[row] = (targetValue != null) ? targetValue.doubleValue() : 0.0;
                    }
                    ComputedValues.setComputedValue(name.trim(), outputValues[row]);
                    ComputedValues.markComputedThisStep(name.trim());
                }
                continue;
            }
            
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
     * Override to conditionally hide posts.
     * In MNA mode, draw posts normally. In pure computational mode, hide them.
     */
    @Override
    void drawPosts(Graphics g) {
        if (isMnaMode()) {
            // MNA mode: draw posts for electrical connections
            for (int i = 0; i < getPostCount(); i++) {
                Point p = getPost(i);
                drawPost(g, p);
            }
        }
        // Pure computational mode: intentionally empty - no posts to draw
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
        arr[0] = "Equation Table: " + tableName + (isMnaMode() ? " (Electrical)" : " (Computed)");
        
        // Show convergence failure info if applicable
        if (nonConverged && convergenceFailureInfo != null) {
            arr[1] = "⚠ Convergence failure: " + convergenceFailureInfo;
            // Still show row info after convergence warning
            if (hoveredRow >= 0 && hoveredRow < rowCount) {
                String[] rowInfo = new String[10];
                getHoveredRowInfo(rowInfo);
                for (int i = 1; i < rowInfo.length && rowInfo[i] != null && i + 1 < arr.length; i++) {
                    arr[i + 1] = rowInfo[i];
                }
            }
            return;
        }
        
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
        
        // Build classification description with icon
        String classDesc;
        if (isAliasRow[hoveredRow]) {
            classDesc = "→ alias (shares node with " + compiledExprs[hoveredRow].getNodeName() + ")";
        } else if (isConstantRow[hoveredRow]) {
            classDesc = "● constant (stamped once)";
        } else if (isLinearRow[hoveredRow]) {
            classDesc = "L linear (VCVS, no iteration)";
        } else {
            classDesc = "⟳ dynamic (evaluated each step)";
        }
        
        arr[2] = "Row " + (hoveredRow + 1) + ": " + outputNames[hoveredRow] + " [" + classDesc + "]";
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
    
    /** Check if row is classified as alias (bare node reference) */
    public boolean isAliasRow(int row) {
        return (row >= 0 && row < MAX_ROWS) ? isAliasRow[row] : false;
    }
    
    /** Check if row is classified as constant (pure literal math) */
    public boolean isConstantRow(int row) {
        return (row >= 0 && row < MAX_ROWS) ? isConstantRow[row] : false;
    }
    
    /** Check if row is classified as linear (VCVS, no iteration) */
    public boolean isLinearRow(int row) {
        return (row >= 0 && row < MAX_ROWS) ? isLinearRow[row] : false;
    }
    
    /** Get row classification as string */
    public String getRowClassification(int row) {
        if (row < 0 || row >= MAX_ROWS) return "unknown";
        if (isAliasRow[row]) return "alias";
        if (isConstantRow[row]) return "constant";
        if (isLinearRow[row]) return "linear";
        return "dynamic";
    }
    
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
