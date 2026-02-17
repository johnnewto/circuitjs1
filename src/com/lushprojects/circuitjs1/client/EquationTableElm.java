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
 * <h3>Operational Modes:</h3>
 * <ul>
 *   <li><b>MNA (Electrical) mode:</b> Stamps into the circuit matrix via voltage sources,
 *       current sources, or capacitor companion models. Outputs connect to labeled nodes.</li>
 *   <li><b>Pure Computational mode:</b> No electrical posts or circuit connections.
 *       Values are written to the ComputedValues registry for other elements to read.</li>
 * </ul>
 * 
 * <h3>Features:</h3>
 * <ul>
 *   <li>Multiple rows (1-32), each defining an independent equation</li>
 *   <li>Each row has a named output that becomes accessible as a labeled node</li>
 *   <li>Custom slider variable per row for interactive parameter adjustment</li>
 *   <li>Support for initial value equations (evaluated only at t=0)</li>
 *   <li>Row output modes: VOLTAGE (default), FLOW (current source), STOCK (capacitor)</li>
 *   <li>integrate() and diff() functions for dynamic systems</li>
 *   <li>Row reordering via up/down buttons in edit dialog</li>
 *   <li>Autocomplete support for equation editing</li>
 *   <li>Mouse wheel adjustment for numeric equations</li>
 * </ul>
 * 
 * @see TableElm Similar visual table element for display
 * @see ComputedValues Registry for accessing equation outputs
 * @see EquationTableRenderer Rendering logic
 * @see EquationTableEditDialog Edit UI
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
    // ROW OUTPUT MODE - Determines how each row's equation result is used
    //=============================================================================
    
    /**
     * Output mode for each row - determines how the equation result is stamped.
     */
    public enum RowOutputMode {
        /** Voltage mode (default): stamps voltage source, equation = voltage value */
        VOLTAGE_MODE,
        /** Flow mode: stamps current source between two nodes, equation = flow rate */
        FLOW_MODE,
        /** Stock mode: stamps companion model, equation = net inflow current */
        STOCK_MODE
    }
    
    //=============================================================================
    // ROW DATA CLASS - Consolidates all per-row state
    //=============================================================================
    
    /**
     * Holds all state for a single equation table row.
     * Eliminates the previous pattern of 20+ parallel arrays indexed by row number.
     */
    static class EquationRow {
        // User-editable fields (persisted in dump)
        String outputName;
        String equation;
        String initialEquation;
        String sliderVarName;
        double sliderValue;
        RowOutputMode outputMode;
        String targetNodeName;
        double capacitance;
        boolean useBackwardEuler;  // false = trapezoidal (default), true = backward Euler
        
        // Compiled/derived state
        Expr compiledExpr;
        Expr compiledInitialExpr;
        ExprState exprState;
        double outputValue;
        double lastOutputValue;
        boolean initialValueApplied;
        boolean isAlias;
        boolean hasDiffExpr;
        
        // MNA runtime state (STOCK_MODE)
        double capLastVoltage;
        double capLastCurrent;
        double capCurSourceValue;
        
        // MNA runtime state (FLOW_MODE / STOCK_MODE)
        int targetNodeNumber;
        double flowValue;
        
        // MNA node tracking
        int labeledNodeNumber;
        int rowVoltSource;
        
        /** Create a row with default values for the given index */
        EquationRow(int index) {
            outputName = "Y" + (index + 1);
            equation = "0";
            initialEquation = "";
            sliderVarName = String.valueOf((char)('a' + (index % 26)));
            sliderValue = 0.5;
            exprState = new ExprState(1);  // 1 variable slot for slider
            outputMode = RowOutputMode.VOLTAGE_MODE;
            targetNodeName = "";
            capacitance = 1.0;
            useBackwardEuler = false;  // trapezoidal by default
            targetNodeNumber = -1;
            labeledNodeNumber = -1;
            rowVoltSource = -1;
        }
        
        /** Reset runtime state for simulation restart */
        void reset() {
            if (exprState != null) exprState.reset();
            outputValue = 0.0;
            lastOutputValue = 0.0;
            initialValueApplied = false;
            capLastVoltage = 0.0;
            capLastCurrent = 0.0;
            capCurSourceValue = 0.0;
            flowValue = 0.0;
        }
    }
    
    //=============================================================================
    // MODE HANDLERS - Isolate stamp/evaluate logic per output mode
    //=============================================================================
    
    /**
     * Interface for mode-specific stamp and evaluate operations.
     * Each RowOutputMode has a corresponding handler that encapsulates
     * its MNA stamping and equation evaluation logic.
     */
    private interface RowModeHandler {
        /** Stamp this row's contributions into the MNA matrix */
        void stamp(int row);
        /** Evaluate this row's equation and apply result during doStep */
        void evaluate(int row);
    }
    
    /**
     * VOLTAGE_MODE handler: drives a labeled node via voltage source.
     * Equation result is the voltage value.
     */
    private class VoltageModeHandler implements RowModeHandler {
        @Override
        public void stamp(int row) {
            int nodeNum = rows[row].labeledNodeNumber;
            int vs = rows[row].rowVoltSource;
            
            if (nodeNum >= 0 && vs >= 0) {
                int vn = voltSource + vs + sim.nodeList.size();
                sim.stampNonLinear(vn);
                sim.stampVoltageSource(0, nodeNum, voltSource + vs);
                
                // Stamp tiny load resistance to prevent matrix elimination
                sim.stampResistor(nodeNum, 0, 1e9);
            }
        }
        
        @Override
        public void evaluate(int row) {
            if (rows[row].compiledExpr == null) return;
            
            ExprState state = prepareEvalState(row);
            double equationValue = rows[row].compiledExpr.eval(state);
            
            if (DEBUG) {
                CirSim.console("[EquationTableElm." + tableName + "] Row " + row + 
                    " (" + rows[row].outputName + "): eq=" + rows[row].equation + " val=" + equationValue);
            }
            
            checkEquationConvergence(row, equationValue);
            rows[row].lastOutputValue = equationValue;
            rows[row].outputValue = equationValue;
            
            // MNA mode: stamp to matrix
            if (isMnaMode() && rows[row].rowVoltSource >= 0) {
                int vn = voltSource + rows[row].rowVoltSource + sim.nodeList.size();
                sim.stampRightSide(vn, equationValue);
            }
            
            registerOutputValue(row, equationValue);
        }
    }
    
    /**
     * FLOW_MODE handler: injects current between source and target nodes.
     * Equation result is the current magnitude.
     */
    private class FlowModeHandler implements RowModeHandler {
        @Override
        public void stamp(int row) {
            // Resolve the named node
            Integer namedNode = LabeledNodeElm.getByName(rows[row].outputName);
            if (namedNode == null || namedNode < 0) return;
            
            // Resolve target node (default to ground if empty or "gnd")
            String targetName = rows[row].targetNodeName;
            boolean singleNode = (targetName == null || targetName.trim().isEmpty() ||
                targetName.trim().equalsIgnoreCase("gnd"));
            
            int sourceNode, targetNode;
            if (singleNode) {
                // Single-node FLOW: current flows FROM ground TO the named node.
                // Positive equation value = current injected into the node.
                sourceNode = 0;   // ground
                targetNode = namedNode;
            } else {
                // Two-node FLOW: current flows FROM source TO target.
                // Source is the output name, target is the explicit target node.
                sourceNode = namedNode;
                Integer resolvedTarget = LabeledNodeElm.getByName(targetName.trim());
                if (resolvedTarget == null || resolvedTarget < 0) return;
                targetNode = resolvedTarget;
            }
            
            // Store resolved node numbers for doStep()
            rows[row].labeledNodeNumber = sourceNode;
            rows[row].targetNodeNumber = targetNode;
            
            // Mark nodes as nonlinear to prevent matrix elimination
            sim.stampNonLinear(sourceNode);
            if (targetNode > 0) sim.stampNonLinear(targetNode);
            
            sim.stampRightSide(sourceNode);
            sim.stampRightSide(targetNode);
        }
        
        @Override
        public void evaluate(int row) {
            if (rows[row].compiledExpr == null) return;
            
            int sourceNode = rows[row].labeledNodeNumber;
            int targetNode = rows[row].targetNodeNumber;
            if (sourceNode < 0 || targetNode < 0) return;
            
            ExprState state = prepareEvalState(row);
            double flowValue = rows[row].compiledExpr.eval(state);
            
            rows[row].flowValue = flowValue;
            checkEquationConvergence(row, flowValue);
            rows[row].lastOutputValue = flowValue;
            rows[row].outputValue = flowValue;
            
            sim.stampCurrentSource(sourceNode, targetNode, flowValue);
            // Do NOT registerOutputValue here: outputName for FLOW is the source
            // node name (e.g. "S1" from "S1->S2"), and registering the flow
            // magnitude (1.0) would clobber any STOCK row's value for that name
            // in ComputedValues, causing expressions like "-S1*0.5" to read the
            // flow value instead of the actual stock voltage.
        }
    }
    
    /**
     * STOCK_MODE handler: integrating current source with companion model.
     * Equation result is the net inflow current; integration via capacitor dynamics.
     */
    private class StockModeHandler implements RowModeHandler {
        @Override
        public void stamp(int row) {
            // Resolve source node
            Integer sourceNode = LabeledNodeElm.getByName(rows[row].outputName);
            if (sourceNode == null || sourceNode < 0) return;
            
            // Resolve target node (default to ground)
            String targetName = rows[row].targetNodeName;
            Integer targetNode;
            if (targetName == null || targetName.trim().isEmpty() || 
                targetName.trim().equalsIgnoreCase("gnd")) {
                targetNode = 0;
            } else {
                targetNode = LabeledNodeElm.getByName(targetName.trim());
                if (targetNode == null || targetNode < 0) return;
            }
            
            // Store resolved node numbers
            rows[row].targetNodeNumber = targetNode;
            rows[row].labeledNodeNumber = sourceNode;
            
            // Companion resistance: R = dt/(2C) (trapezoidal) or R = dt/C (backward Euler)
            double cap = rows[row].capacitance;
            if (cap <= 0) cap = 1.0;
            double compR = rows[row].useBackwardEuler
                ? sim.timeStep / cap
                : sim.timeStep / (2 * cap);
            sim.stampResistor(sourceNode, targetNode, compR);
            
            // Mark as nonlinear to prevent matrix elimination
            sim.stampNonLinear(sourceNode);
            if (targetNode > 0) sim.stampNonLinear(targetNode);
            
            sim.stampRightSide(sourceNode);
            sim.stampRightSide(targetNode);
        }
        
        @Override
        public void evaluate(int row) {
            if (rows[row].compiledExpr == null) return;
            
            int sourceNode = rows[row].labeledNodeNumber;
            int targetNode = rows[row].targetNodeNumber;
            if (sourceNode < 0 || targetNode < 0) return;
            
            ExprState state = prepareEvalState(row);
            double inflowValue = rows[row].compiledExpr.eval(state);
            
            rows[row].flowValue = inflowValue;
            rows[row].outputValue = inflowValue;
            checkEquationConvergence(row, inflowValue);
            rows[row].lastOutputValue = inflowValue;
            
            // Total current = history (from startIteration) - inflow
            double totalCurrent = rows[row].capCurSourceValue - inflowValue;
            sim.stampCurrentSource(sourceNode, targetNode, totalCurrent);
        }
    }
    
    /** Cached handler instances (created lazily) */
    private RowModeHandler voltageModeHandler, flowModeHandler, stockModeHandler;
    
    /** Get the handler for a given output mode */
    private RowModeHandler getHandler(RowOutputMode mode) {
        switch (mode) {
        case FLOW_MODE:
            if (flowModeHandler == null) flowModeHandler = new FlowModeHandler();
            return flowModeHandler;
        case STOCK_MODE:
            if (stockModeHandler == null) stockModeHandler = new StockModeHandler();
            return stockModeHandler;
        default:
            if (voltageModeHandler == null) voltageModeHandler = new VoltageModeHandler();
            return voltageModeHandler;
        }
    }
    
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
    // INSTANCE STATE - Per-Row Data
    //=============================================================================
    
    /** All per-row state consolidated into EquationRow objects */
    private EquationRow[] rows;
    
    /** Whether all alias rows have been successfully resolved to target nodes */
    private boolean aliasesResolved;
    
    /** Whether row classifications are up to date (avoids redundant recomputation) */
    private boolean classificationsValid;
    
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
    // INSTANCE STATE - MNA Node Tracking
    //=============================================================================
    
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
        rows[0].outputName = "Y1";
        rows[0].equation = "0";
        rows[0].sliderVarName = "a";
        rows[0].sliderValue = 0.5;
        
        rows[1].outputName = "Y2";
        rows[1].equation = "0";
        rows[1].sliderVarName = "b";
        rows[1].sliderValue = 0.5;
        
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
        
        // Count remaining tokens to determine format version
        // Old format: 5 tokens per row (name, equation, initEq, sliderVar, sliderValue)
        // New format: 8 tokens per row (+ outputMode, targetNode, capacitance)
        int tokenCount = 0;
        java.util.ArrayList<String> tokens = new java.util.ArrayList<String>();
        while (st.hasMoreTokens()) {
            tokens.add(st.nextToken());
            tokenCount++;
        }
        
        // Determine format: if totalTokens == rowCount * 9, it's newest format
        // If totalTokens == rowCount * 8, it's new format (no backward euler flag)
        // If totalTokens == rowCount * 5, it's old format
        // Otherwise, assume old format for safety
        boolean newestFormat = (tokenCount == rowCount * 9);
        boolean newFormat = newestFormat || (tokenCount == rowCount * 8);
        
        // Parse per-row data
        int tokenIndex = 0;
        for (int row = 0; row < rowCount; row++) {
            if (tokenIndex < tokens.size()) {
                rows[row].outputName = CustomLogicModel.unescape(tokens.get(tokenIndex++));
            }
            if (tokenIndex < tokens.size()) {
                rows[row].equation = CustomLogicModel.unescape(tokens.get(tokenIndex++));
            }
            if (tokenIndex < tokens.size()) {
                rows[row].initialEquation = CustomLogicModel.unescape(tokens.get(tokenIndex++));
            }
            if (tokenIndex < tokens.size()) {
                rows[row].sliderVarName = CustomLogicModel.unescape(tokens.get(tokenIndex++));
            }
            if (tokenIndex < tokens.size()) {
                try {
                    rows[row].sliderValue = Double.parseDouble(tokens.get(tokenIndex++));
                } catch (Exception e) {
                    rows[row].sliderValue = 0.5;
                }
            }
            
            // New format fields (only if new format detected)
            if (newFormat) {
                // Output mode
                if (tokenIndex < tokens.size()) {
                    try {
                        int modeOrdinal = Integer.parseInt(tokens.get(tokenIndex++));
                        if (modeOrdinal >= 0 && modeOrdinal < RowOutputMode.values().length) {
                            rows[row].outputMode = RowOutputMode.values()[modeOrdinal];
                        }
                    } catch (Exception e) {
                        tokenIndex++; // Skip invalid token
                    }
                }
                // Target node name (may be empty if combined name format used)
                if (tokenIndex < tokens.size()) {
                    rows[row].targetNodeName = CustomLogicModel.unescape(tokens.get(tokenIndex++));
                    if (rows[row].targetNodeName.isEmpty()) {
                        rows[row].targetNodeName = null;
                    }
                }
                // Capacitance
                if (tokenIndex < tokens.size()) {
                    try {
                        rows[row].capacitance = Double.parseDouble(tokens.get(tokenIndex++));
                        if (rows[row].capacitance <= 0) rows[row].capacitance = 1.0;
                    } catch (Exception e) {
                        tokenIndex++; // Skip invalid token
                        rows[row].capacitance = 1.0;
                    }
                }
                // Backward Euler flag (newest format only)
                if (newestFormat && tokenIndex < tokens.size()) {
                    try {
                        rows[row].useBackwardEuler = Integer.parseInt(tokens.get(tokenIndex++)) != 0;
                    } catch (Exception e) {
                        tokenIndex++;
                        rows[row].useBackwardEuler = false;
                    }
                }
            }
            
            // Parse combined "source->target" name format
            // If outputName contains "->", split into source and target parts.
            // This handles both new combined format and provides forward compatibility.
            String[] parts = parseCombinedName(rows[row].outputName);
            if (!parts[1].isEmpty()) {
                rows[row].outputName = parts[0];
                rows[row].targetNodeName = parts[1];
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
     * Initialize row array with default values.
     * Called by constructors before loading specific data.
     */
    private void initArrays() {
        rows = new EquationRow[MAX_ROWS];
        for (int i = 0; i < MAX_ROWS; i++) {
            rows[i] = new EquationRow(i);
        }
        classificationsValid = false;
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
            names[i] = rows[i].outputName;
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
            int modeIconWidth = opsize == 1 ? 14 : 18;   // "I→" or "C∫" icon
            int classIconWidth = opsize == 1 ? 12 : 16;  // "→" or "⟳" icon (always shown)
            int valueSpacing = opsize == 1 ? 16 : 20;

            for (int row = 0; row < rowCount; row++) {
                String displayText = getUIDisplayOutputName(row) + " = " + rows[row].equation;
                // Start with classification icon width (always present)
                int leftIconsWidth = classIconWidth;
                // Add adjustable scroll icon if applicable
                if (isAdjustableRow(row))
                    leftIconsWidth += scrollIconWidth;
                // Add output mode icon if not VOLTAGE_MODE
                if (rows[row].outputMode != RowOutputMode.VOLTAGE_MODE)
                    leftIconsWidth += modeIconWidth;
                int rowWidth = leftIconsWidth + (int) sim.cvcontext.measureText(displayText).getWidth() + valueSpacing;
                
                // Include initial equation width if present
                String initEq = rows[row].initialEquation;
                if (initEq != null && !initEq.trim().isEmpty()) {
                    sim.cvcontext.setFont(initFontSpec);
                    rowWidth += (int) sim.cvcontext.measureText("[" + initEq + "]").getWidth() + cellPadding;
                    sim.cvcontext.setFont(rowFontSpec);
                }
                maxTextWidth = Math.max(maxTextWidth, rowWidth);
            }
        }
        
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
     * @return Number of voltage sources needed - one per VOLTAGE_MODE row with valid output name.
     * FLOW_MODE and STOCK_MODE don't use voltage sources.
     * This counts voltage sources before findLabeledNodes() runs, so we count all valid rows.
     */
    int getVoltageSourceCount() { 
        if (!isMnaMode()) return 0;
        
        updateRowClassifications();
        int count = 0;
        for (int row = 0; row < rowCount; row++) {
            if (rows[row].isAlias) continue;  // Aliases need no voltage source
            // Only VOLTAGE mode uses voltage sources
            if (rows[row].outputMode != RowOutputMode.VOLTAGE_MODE) continue;
            if (isValidOutputName(row)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Override getInternalNodeCount to create internal nodes for rows
     * that do NOT have an existing LabeledNode on the canvas.
     * FLOW_MODE doesn't need internal nodes (uses existing labeled nodes for source/target).
     * VOLTAGE_MODE and STOCK_MODE may need internal nodes.
     */
    int getInternalNodeCount() {
        if (!isMnaMode()) return 0;
        
        updateRowClassifications();
        int count = 0;
        for (int row = 0; row < rowCount; row++) {
            if (rows[row].isAlias) continue;  // Aliases share target node, need no internal node
            // FLOW_MODE doesn't need internal nodes
            if (rows[row].outputMode == RowOutputMode.FLOW_MODE) continue;
            if (isValidOutputName(row)) {
                // Only need internal node if NO existing LabeledNode
                Integer existingNode = LabeledNodeElm.getByName(rows[row].outputName.trim());
                if (existingNode == null || existingNode < 0) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /** @return true if any row has a non-alias expression requiring iterative solving,
     *  or if any row uses FLOW_MODE or STOCK_MODE output mode (which always require doStep) */
    boolean nonLinear() {
        updateRowClassifications();
        for (int row = 0; row < rowCount; row++) {
            // FLOW_MODE and STOCK_MODE always require doStep() evaluation
            if (rows[row].outputMode == RowOutputMode.FLOW_MODE || rows[row].outputMode == RowOutputMode.STOCK_MODE) {
                return true;
            }
            // Non-alias VOLTAGE mode rows are nonlinear (need doStep)
            if (!rows[row].isAlias)
                return true;
        }
        return false;
    }
    
    /**
     * Update isAliasRow and hasDiffExpr flags based on compiled expressions.
     * 
     * Row classifications (checked in priority order):
     * 1. ALIAS: Expression is a bare node reference (E_NODE_REF), e.g. "Cs ~ Cd".
     *    The output name becomes an alias for the target node — no voltage source,
     *    no internal node, no matrix row needed. Eliminates 2 matrix rows per alias.
     * 2. DYNAMIC: Everything else — requires nonlinear doStep() evaluation.
     * 
     * Results are cached; call {@link #invalidateClassifications()} when equations change.
     */
    private void updateRowClassifications() {
        if (classificationsValid) return;
        for (int row = 0; row < rowCount; row++) {
            rows[row].isAlias = false;
            rows[row].hasDiffExpr = false;
            
            if (rows[row].compiledExpr == null) continue;
            
            // Precompute diff() presence for convergence checks
            rows[row].hasDiffExpr = rows[row].equation.contains("diff");
            
            String initEq = rows[row].initialEquation;
            boolean hasInitEq = (initEq != null && !initEq.trim().isEmpty());
            
            // Check alias (bare node reference with no initial equation)
            if (!hasInitEq && rows[row].compiledExpr.isNodeAlias()) {
                rows[row].isAlias = true;
            }
        }
        classificationsValid = true;
    }
    
    /**
     * Mark row classifications as stale. Call when equations, output modes,
     * or initial equations change.
     */
    private void invalidateClassifications() {
        classificationsValid = false;
    }
    
    //=============================================================================
    // HELPERS - Used across evaluation and registration methods
    //=============================================================================
    
    /**
     * Check if a row's output name is non-null and non-empty.
     * Consolidates the repeated `name != null && !name.trim().isEmpty()` pattern.
     * @param row Row index
     * @return true if the output name is usable
     */
    private boolean isValidOutputName(int row) {
        String name = rows[row].outputName;
        return name != null && !name.trim().isEmpty();
    }
    
    /**
     * Prepare the evaluation state for a row before evaluating its expression.
     * Sets slider value, time, and registers the slider variable in ComputedValues.
     * @param row Row index
     * @return The prepared ExprState, ready for eval()
     */
    private ExprState prepareEvalState(int row) {
        ExprState state = rows[row].exprState;
        state.values[0] = rows[row].sliderValue;
        state.t = sim.t;
        
        String sliderVar = rows[row].sliderVarName;
        if (sliderVar != null && !sliderVar.isEmpty()) {
            ComputedValues.setComputedValue(sliderVar, rows[row].sliderValue);
        }
        return state;
    }
    
    /**
     * Register a row's output value in ComputedValues by its output name.
     * @param row Row index
     * @param value Value to register
     */
    private void registerOutputValue(int row, double value) {
        if (isValidOutputName(row)) {
            ComputedValues.setComputedValue(rows[row].outputName.trim(), value);
        }
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
     * Invalidates cached row classifications since initial equations affect classification.
     * @param row Row index
     */
    private void parseInitialEquation(int row) {
        invalidateClassifications();
        String initEq = rows[row].initialEquation;
        if (initEq == null || initEq.trim().isEmpty()) {
            rows[row].compiledInitialExpr = null;
            return;
        }
        
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] Parsing initial equation row " + row + ": '" + initEq + "'");
        }
        
        try {
            ExprParser parser = new ExprParser(initEq);
            rows[row].compiledInitialExpr = parser.parseExpression();
            String err = parser.gotError();
            
            if (err != null) {
                CirSim.console("EquationTableElm row " + row + ": Parse error in initial equation '" + initEq + "': " + err);
                rows[row].compiledInitialExpr = null;
            } else if (DEBUG) {
                CirSim.console("[EquationTableElm." + tableName + "] Row " + row + " initial equation parsed successfully");
            }
        } catch (Exception e) {
            CirSim.console("EquationTableElm row " + row + ": Error parsing initial equation '" + initEq + "': " + e.getMessage());
            rows[row].compiledInitialExpr = null;
        }
    }
    
    /**
     * Parse the main equation for a specific row.
     * Compiles the equation string into an expression tree for efficient evaluation.
     * Invalidates cached row classifications since expression content changed.
     * @param row Row index
     */
    private void parseEquation(int row) {
        invalidateClassifications();
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] Parsing row " + row + ": '" + rows[row].equation + "'");
        }
        
        try {
            ExprParser parser = new ExprParser(rows[row].equation);
            rows[row].compiledExpr = parser.parseExpression();
            String err = parser.gotError();
            
            if (err != null) {
                CirSim.console("EquationTableElm row " + row + ": Parse error in '" + rows[row].equation + "': " + err);
                rows[row].compiledExpr = null;
            } else if (DEBUG) {
                CirSim.console("[EquationTableElm." + tableName + "] Row " + row + " parsed successfully");
            }
        } catch (Exception e) {
            CirSim.console("EquationTableElm row " + row + ": Error parsing '" + rows[row].equation + "': " + e.getMessage());
            rows[row].compiledExpr = null;
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
        if (rows[row].hasDiffExpr) {
            relativeTolerance *= 10;  // 10x more lenient for diff equations
        }
        
        // Scale by the magnitude of the values involved
        double maxMagnitude = Math.max(1.0, Math.abs(rows[row].outputValue));
        maxMagnitude = Math.max(maxMagnitude, Math.abs(rows[row].lastOutputValue));
        
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
     * 
     * FLOW_MODE uses existing labeled nodes for source/target.
     * STOCK_MODE may need internal nodes if no LabeledNode exists.
     * VOLTAGE_MODE uses voltage sources.
     */
    private void findLabeledNodes() {
        voltSourceCount = 0;
        
        int internalNodeIdx = 0;  // Index into internal nodes (nodes[] array)
        
        for (int row = 0; row < rowCount; row++) {
            rows[row].labeledNodeNumber = -1;
            rows[row].rowVoltSource = -1;
            
            // Alias rows don't need nodes or voltage sources
            if (rows[row].isAlias) continue;
            
            if (!isValidOutputName(row)) continue;
            String outputName = rows[row].outputName.trim();
            
            // FLOW_MODE uses existing labeled nodes only (no internal nodes)
            if (rows[row].outputMode == RowOutputMode.FLOW_MODE) {
                Integer existingNodeNum = LabeledNodeElm.getByName(outputName);
                if (existingNodeNum != null && existingNodeNum >= 0) {
                    rows[row].labeledNodeNumber = existingNodeNum;
                }
                continue;
            }
            
            // STOCK_MODE: use existing node or create internal node (no voltage source)
            if (rows[row].outputMode == RowOutputMode.STOCK_MODE) {
                Integer existingNodeNum = LabeledNodeElm.getByName(outputName);
                if (existingNodeNum != null && existingNodeNum >= 0) {
                    rows[row].labeledNodeNumber = existingNodeNum;
                } else {
                    // No existing LabeledNode - use our internal node and register it
                    if (nodes != null && internalNodeIdx < nodes.length) {
                        int internalNode = nodes[internalNodeIdx];
                        rows[row].labeledNodeNumber = internalNode;
                        registerInternalNodeAsLabel(outputName, internalNode);
                        internalNodeIdx++;
                    }
                }
                continue;
            }
            
            // VOLTAGE mode: Check if a LabeledNode already exists on canvas
            Integer existingNodeNum = LabeledNodeElm.getByName(outputName);
            
            if (existingNodeNum != null && existingNodeNum >= 0) {
                // Use existing LabeledNode's node - drive it with our voltage source
                rows[row].labeledNodeNumber = existingNodeNum;
                rows[row].rowVoltSource = voltSourceCount;
                voltSourceCount++;
            } else {
                // No existing LabeledNode - use our internal node and register it
                if (nodes != null && internalNodeIdx < nodes.length) {
                    int internalNode = nodes[internalNodeIdx];
                    rows[row].labeledNodeNumber = internalNode;
                    
                    // Register this internal node so other elements can find it by name
                    registerInternalNodeAsLabel(outputName, internalNode);
                    
                    internalNodeIdx++;
                    
                    rows[row].rowVoltSource = voltSourceCount;
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
            if (!rows[row].isAlias) continue;
            
            if (!isValidOutputName(row)) continue;
            String outputName = rows[row].outputName.trim();
            
            String targetName = rows[row].compiledExpr.getNodeName();
            if (targetName == null) continue;
            
            // Look up the target node number
            Integer targetNodeNum = LabeledNodeElm.getByName(targetName);
            if (targetNodeNum != null && targetNodeNum >= 0) {
                // Register output name as pointing to the same node
                registerInternalNodeAsLabel(outputName, targetNodeNum);
                rows[row].labeledNodeNumber = targetNodeNum;
                
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
     * Pre-register alias labels before wire closure so that alias names
     * merge into the same MNA node as their target during calculateWireClosure().
     *
     * For an alias row like "S1" with equation "S0", this finds the LabeledNodeElm
     * with text "S0" on the canvas and pre-registers "S1" pointing to S0's physical
     * point. When wire closure later processes the S1 LabeledNodeElm, it finds this
     * entry and merges S1 into S0's node — giving them the same physical MNA node.
     *
     * This eliminates the split-brain problem where the labelList node differs from
     * the element's nodes[0], making highlighting and other node-based lookups work
     * correctly without special-case workarounds.
     *
     * Falls back to registerAliasNodes() during stamp() for cases where no canvas
     * LabeledNodeElm exists for the target (e.g. target created by another table).
     */
    @Override
    void registerLabels() {
        if (!isMnaMode()) return;

        updateRowClassifications();

        for (int row = 0; row < rowCount; row++) {
            if (!rows[row].isAlias) continue;
            if (!isValidOutputName(row)) continue;

            String aliasName = rows[row].outputName.trim();
            String targetName = rows[row].compiledExpr.getNodeName();
            if (targetName == null) continue;

            // Find the target's LabeledNodeElm on the canvas to get its physical point
            Point targetPoint = findLabeledNodePoint(targetName);
            if (targetPoint != null) {
                LabeledNodeElm.preRegisterLabel(aliasName, targetPoint);
                if (DEBUG) {
                    CirSim.console("[EquationTableElm." + tableName + "] registerLabels: " +
                        "pre-registered alias '" + aliasName + "' -> '" + targetName +
                        "' point=(" + targetPoint.x + "," + targetPoint.y + ")");
                }
            }
        }
    }

    /**
     * Find the physical point of a LabeledNodeElm on the canvas with the given text.
     * Scans the element list for a matching LabeledNodeElm.
     * @param name The label text to search for
     * @return The element's point1, or null if not found
     */
    private Point findLabeledNodePoint(String name) {
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.elmList.get(i);
            if (ce instanceof LabeledNodeElm) {
                LabeledNodeElm lne = (LabeledNodeElm) ce;
                if (name.equals(lne.getName())) {
                    return lne.point1;
                }
            }
        }
        return null;
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
            // Pure computational mode: no matrix stamping needed.
            // Alias rows handled in doStep() by copying target value.
            return;
        }
        
        // Find or create nodes for each row (skips alias rows)
        findLabeledNodes();
        
        // Register alias rows: point output name to target node (no VS, no matrix row)
        registerAliasNodes();
        
        // MNA mode: stamp each row according to its output mode
        for (int row = 0; row < rowCount; row++) {
            // Skip alias rows - handled by registerAliasNodes()
            if (rows[row].isAlias) continue;
            
            getHandler(rows[row].outputMode).stamp(row);
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
     * Calculates capacitor history currents for STOCK_MODE rows.
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
                if (!rows[row].isAlias) continue;
                if (!isValidOutputName(row)) continue;
                String outputName = rows[row].outputName.trim();
                
                String targetName = rows[row].compiledExpr.getNodeName();
                if (targetName != null) {
                    // Read target voltage and seed into ComputedValues
                    double val = sim.getLabeledNodeVoltage(targetName);
                    rows[row].outputValue = val;
                    // Write directly to current buffer so it's available during doStep
                    ComputedValues.setComputedValueDirect(outputName, val);
                }
            }
            
            // Seed STOCK_MODE node voltages into ComputedValues so expressions
            // in doStep read the actual stock level, not a stale or wrong value.
            for (int row = 0; row < rowCount; row++) {
                if (rows[row].outputMode != RowOutputMode.STOCK_MODE) continue;
                if (!isValidOutputName(row)) continue;
                String name = rows[row].outputName.trim();
                double stockVoltage = sim.getLabeledNodeVoltage(name);
                ComputedValues.setComputedValueDirect(name, stockVoltage);
            }
            
            // Calculate capacitor history currents for STOCK_MODE rows
            for (int row = 0; row < rowCount; row++) {
                if (rows[row].outputMode != RowOutputMode.STOCK_MODE) continue;
                
                double cap = rows[row].capacitance;
                if (cap <= 0) cap = 1.0;
                double compResistance = rows[row].useBackwardEuler
                    ? sim.timeStep / cap
                    : sim.timeStep / (2 * cap);
                
                // History current based on integration method
                if (rows[row].useBackwardEuler) {
                    // Backward Euler: I_hist = -V_last / R
                    rows[row].capCurSourceValue = -rows[row].capLastVoltage / compResistance;
                } else {
                    // Trapezoidal: I_hist = -V_last / R - I_last
                    rows[row].capCurSourceValue = -rows[row].capLastVoltage / compResistance - rows[row].capLastCurrent;
                }
            }
        }
    }
    
    void doStep() {
        if (DEBUG && sim.subIterations == 0) {
            CirSim.console("[EquationTableElm." + tableName + "] doStep() at t=" + sim.t + " mnaMode=" + isMnaMode());
        }
        
        for (int row = 0; row < rowCount; row++) {
            // Alias rows: copy target value to ComputedValues so other expressions
            // can resolve the alias name. In MNA mode, read from shared node voltage.
            // In pure mode, read from ComputedValues.
            if (rows[row].isAlias) {
                String targetName = rows[row].compiledExpr.getNodeName();
                double val;
                if (isMnaMode()) {
                    val = sim.getLabeledNodeVoltage(targetName);
                } else {
                    Double targetValue = ComputedValues.getComputedValue(targetName);
                    val = (targetValue != null) ? targetValue.doubleValue() : 0.0;
                }
                rows[row].outputValue = val;
                rows[row].lastOutputValue = val;
                registerOutputValue(row, val);
                continue;
            }
            
            // Handle initial value at t=0
            if (sim.t == 0 && rows[row].compiledInitialExpr != null) {
                // On first sub-iteration, use 0 as placeholder for VOLTAGE_MODE
                // For STOCK_MODE, we need to stamp something reasonable
                if (sim.subIterations == 0 && !rows[row].initialValueApplied) {
                    rows[row].outputValue = 0;
                    if (rows[row].outputMode == RowOutputMode.STOCK_MODE) {
                        // STOCK_MODE: stamp zero current for now, will be corrected on next subiteration
                        int sourceNode = rows[row].labeledNodeNumber;
                        int targetNode = rows[row].targetNodeNumber;
                        if (sourceNode >= 0 && targetNode >= 0) {
                            sim.stampCurrentSource(sourceNode, targetNode, 0);
                        }
                    } else if (isMnaMode() && rows[row].rowVoltSource >= 0) {
                        int vn = voltSource + rows[row].rowVoltSource + sim.nodeList.size();
                        sim.stampRightSide(vn, 0);
                    }
                    continue;
                }
                
                // Evaluate initial value and compute history current on first pass
                if (!rows[row].initialValueApplied) {
                    evaluateInitialValue(row);
                    continue;
                }
                
                // After initial value is computed, KEEP stamping the history current on subsequent iterations
                // (circuitRightSide gets reset each iteration, so we must re-stamp)
                if (rows[row].outputMode == RowOutputMode.STOCK_MODE) {
                    int sourceNode = rows[row].labeledNodeNumber;
                    int targetNode = rows[row].targetNodeNumber;
                    if (sourceNode >= 0 && targetNode >= 0) {
                        double cap = rows[row].capacitance;
                        if (cap <= 0) cap = 1.0;
                        double compResistance = sim.timeStep / (2 * cap);
                        double initialValue = rows[row].outputValue; // stored from evaluateInitialValue
                        double historyCurrent = -initialValue / compResistance - rows[row].capLastCurrent;
                        double inflowValue = rows[row].flowValue; // also stored from evaluateInitialValue
                        // inflowValue is current INTO node, so SUBTRACT (negative = into)
                        double totalCurrent = historyCurrent - inflowValue;
                        sim.stampCurrentSource(sourceNode, targetNode, totalCurrent);
                    }
                }
                continue;
            }
            
            // Normal timestep: evaluate via mode handler
            getHandler(rows[row].outputMode).evaluate(row);
        }
    }
    
    /**
     * Evaluate the initial value expression for a row at t=0.
     * @param row Row index
     */
    private void evaluateInitialValue(int row) {
        ExprState state = rows[row].exprState;
        state.values[0] = rows[row].sliderValue;
        state.t = 0;
        
        // Register slider variable for expression evaluation
        String sliderVar = rows[row].sliderVarName;
        if (sliderVar != null && !sliderVar.isEmpty()) {
            ComputedValues.setComputedValue(sliderVar, rows[row].sliderValue);
        }
        
        // Evaluate the initial expression
        double initialValue = rows[row].compiledInitialExpr.eval(state);
        rows[row].outputValue = initialValue;
        rows[row].lastOutputValue = initialValue;
        rows[row].exprState.updateLastValues(initialValue);
        
        // Initialize integration state to start from initial value
        rows[row].exprState.lastIntOutput = initialValue;
        
        // Handle based on output mode
        RowOutputMode mode = rows[row].outputMode;
        
        if (mode == RowOutputMode.STOCK_MODE) {
            // STOCK_MODE: initial value is the starting stock voltage
            rows[row].capLastVoltage = initialValue;
            
            // Stamp the correct current source for this initial voltage
            int sourceNode = rows[row].labeledNodeNumber;
            int targetNode = rows[row].targetNodeNumber;
            
            if (sourceNode >= 0 && targetNode >= 0) {
                double cap = rows[row].capacitance;
                if (cap <= 0) cap = 1.0;
                double compResistance = sim.timeStep / (2 * cap);
                
                // History current: I = -V_init / R - I_last (trapezoidal)
                // stampCurrentSource(src, tgt, I) means I flows FROM src TO tgt
                // Negative I = current flows INTO src = increases voltage at src
                // So historyCurrent = -V/R - I puts current INTO the Stock node to maintain V
                double inflowValue = 0;
                if (rows[row].compiledExpr != null) {
                    inflowValue = rows[row].compiledExpr.eval(state);
                }
                rows[row].flowValue = inflowValue;
                
                double historyCurrent = -initialValue / compResistance - rows[row].capLastCurrent;
                // Inflow is conceptually current INTO Stock, so SUBTRACT it (since negative = into)
                double totalCurrent = historyCurrent - inflowValue;
                
                sim.stampCurrentSource(sourceNode, targetNode, totalCurrent);
                
                // CRITICAL: Force another iteration so the solver actually applies this current
                sim.converged = false;
                
                // Update capCurSourceValue for next startIteration
                rows[row].capCurSourceValue = historyCurrent;
            }
        } else if (isMnaMode() && rows[row].rowVoltSource >= 0) {
            // VOLTAGE mode in MNA: stamp the initial value via stampRightSide
            int vn = voltSource + rows[row].rowVoltSource + sim.nodeList.size();
            sim.stampRightSide(vn, initialValue);
        }
        
        // Register immediately for other elements/rows to use
        registerOutputValue(row, initialValue);
        
        // Store initial value for re-stamping in subsequent iterations
        rows[row].outputValue = initialValue;
        
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] Row " + row + " (" + rows[row].outputName + "): Applied initial value = " + initialValue);
        }
        
        rows[row].initialValueApplied = true;
    }
    
    /**
     * Check if the equation value has converged from the previous iteration.
     * @param row Row index
     * @param equationValue New computed value
     */
    private void checkEquationConvergence(int row, double equationValue) {
        double convergeLimit = getConvergeLimit(row);
        double diff = Math.abs(equationValue - rows[row].lastOutputValue);
        boolean converged = diff <= convergeLimit;
        
        // For diff() equations, skip convergence check during early subiterations
        // because the input to diff() needs time to settle first
        if (rows[row].hasDiffExpr && sim.subIterations < 5) {
            // Don't report non-convergence yet - let input settle
            return;
        }
        
        if (!converged) {
            sim.converged = false;
            failedConvergenceRow = row;
            convergenceFailureInfo = rows[row].outputName + ": diff=" + getShortUnitText(diff, "") + 
                ", limit=" + getShortUnitText(convergeLimit, "") + 
                ", last=" + getShortUnitText(rows[row].lastOutputValue, "") +
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
     * Called after a successful timestep is completed.
     * Registers outputs as labeled nodes and updates expression states.
     * Saves capacitor state for STOCK_MODE rows.
     */
    @Override
    public void stepFinished() {
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] stepFinished() - registering outputs:");
        }
        
        for (int row = 0; row < rowCount; row++) {
            // For alias rows in MNA mode, read the shared node voltage
            // and register in ComputedValues for consistency
            if (rows[row].isAlias) {
                if (isValidOutputName(row)) {
                    String name = rows[row].outputName.trim();
                    String targetName = rows[row].compiledExpr.getNodeName();
                    if (isMnaMode()) {
                        // MNA mode: read voltage from the shared node via labeled node lookup
                        rows[row].outputValue = sim.getLabeledNodeVoltage(targetName);
                    } else {
                        // Pure mode: copy from target ComputedValue
                        Double targetValue = ComputedValues.getComputedValue(targetName);
                        rows[row].outputValue = (targetValue != null) ? targetValue.doubleValue() : 0.0;
                    }
                    ComputedValues.setComputedValue(name, rows[row].outputValue);
                    ComputedValues.markComputedThisStep(name);
                }
                continue;
            }
            
            // Save capacitor state for STOCK_MODE rows
            if (rows[row].outputMode == RowOutputMode.STOCK_MODE && isMnaMode()) {
                if (isValidOutputName(row)) {
                    String sourceName = rows[row].outputName.trim();
                    String targetName = rows[row].targetNodeName;
                    // Save voltage difference for next timestep's history current calculation
                    double sourceVoltage = sim.getLabeledNodeVoltage(sourceName);
                    // If targetName is empty or "gnd", target voltage is 0 (ground)
                    double targetVoltage = 0;
                    if (targetName != null && !targetName.trim().isEmpty() && 
                        !targetName.trim().equalsIgnoreCase("gnd")) {
                        targetVoltage = sim.getLabeledNodeVoltage(targetName.trim());
                    }
                    rows[row].capLastVoltage = sourceVoltage - targetVoltage;
                    
                    // Compute pure companion model current for history tracking,
                    // matching CapacitorElm: current = voltdiff/compResistance + curSourceValue.
                    // This must NOT include the equation's flowValue, because the
                    // trapezoidal recurrence tracks the pure capacitor current (C*dV/dt),
                    // not the total stamped current source. Including -flowValue
                    // would double-count the equation contribution through the history,
                    // causing wrong steady-state values (e.g. V=-4 instead of V=-2).
                    double cap = rows[row].capacitance;
                    if (cap <= 0) cap = 1.0;
                    double compResistance = rows[row].useBackwardEuler
                        ? sim.timeStep / cap
                        : sim.timeStep / (2 * cap);
                    rows[row].capLastCurrent = rows[row].capLastVoltage / compResistance
                        + rows[row].capCurSourceValue;
                    
                    // For STOCK_MODE, outputValue should show the absolute stock node voltage,
                    // not the differential voltage across the companion model.
                    // capLastVoltage = V(source) - V(target) is needed for companion math,
                    // but economically, the stock's absolute level (sourceVoltage) is what matters.
                    rows[row].outputValue = sourceVoltage;
                    
                    if (DEBUG) {
                        CirSim.console("[EquationTableElm." + tableName + 
                            "] STOCK_MODE row '" + rows[row].outputName + "': saved V=" + rows[row].capLastVoltage + 
                            ", I=" + rows[row].capLastCurrent);
                    }
                }
            }
            
            // Register output as computed value
            // Skip FLOW_MODE rows: their outputName is the source node name
            // (e.g. "S1" from "S1->S2"), and registering the flow magnitude
            // would clobber the stock's value in ComputedValues.
            if (rows[row].outputMode != RowOutputMode.FLOW_MODE && isValidOutputName(row)) {
                String name = rows[row].outputName.trim();
                ComputedValues.setComputedValue(name, rows[row].outputValue);
                ComputedValues.markComputedThisStep(name);
                if (DEBUG) {
                    CirSim.console("  " + name + " = " + rows[row].outputValue);
                }
            }
            
            // Commit any pending integration
            if (rows[row].exprState != null) {
                rows[row].exprState.commitIntegration(sim.timeStep);
            }
            
            // Update state for next timestep
            if (rows[row].exprState != null) {
                rows[row].exprState.updateLastValues(rows[row].outputValue);
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
            rows[row].reset();
            
            // Register initial values with ComputedValues
            registerOutputValue(row, rows[row].outputValue);
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
            // Write combined "source->target" name (target token kept empty for format compat)
            sb.append(" ").append(CustomLogicModel.escape(getDisplayOutputName(row)));
            sb.append(" ").append(CustomLogicModel.escape(rows[row].equation));
            sb.append(" ").append(CustomLogicModel.escape(rows[row].initialEquation != null ? rows[row].initialEquation : ""));
            sb.append(" ").append(CustomLogicModel.escape(rows[row].sliderVarName));
            sb.append(" ").append(rows[row].sliderValue);
            // New: output mode, target node (empty - now in combined name), capacitance
            sb.append(" ").append(rows[row].outputMode.ordinal());  // 0=VOLTAGE, 1=CURRENT, 2=CAPACITOR
            sb.append(" ").append(CustomLogicModel.escape(""));  // target now in combined name
            sb.append(" ").append(rows[row].capacitance);
            sb.append(" ").append(rows[row].useBackwardEuler ? 1 : 0);  // 0=trapezoidal, 1=backward Euler
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
        String hint = HintRegistry.getHint(rows[hoveredRow].outputName);
        if (hint != null && !hint.trim().isEmpty()) {
            arr[1] = hint;
        } else {
            arr[1] = "Row " + (hoveredRow + 1) + ": " + getFlowDisplayName(hoveredRow);
        }
        
        // Build classification description with icon
        String classDesc;
        if (rows[hoveredRow].isAlias) {
            classDesc = "→ alias (shares node with " + rows[hoveredRow].compiledExpr.getNodeName() + ")";
        } else {
            classDesc = "⟳ dynamic (evaluated each step)";
        }
        
        arr[2] = getFlowDisplayName(hoveredRow) + " = " + getUnitText(rows[hoveredRow].outputValue, "") + " [" + classDesc + "]";
        arr[3] = "Equation: " + rows[hoveredRow].equation;
        
        String initEq = rows[hoveredRow].initialEquation;
        
        // For FLOW_MODE, show the full current source direction
        if (rows[hoveredRow].outputMode == RowOutputMode.FLOW_MODE) {
            String sourceName = rows[hoveredRow].outputName;
            String targetName = rows[hoveredRow].targetNodeName;
            boolean singleNode = (targetName == null || targetName.trim().isEmpty() ||
                targetName.trim().equalsIgnoreCase("gnd"));
            if (singleNode) {
                arr[4] = "Flow: gnd \u2192 " + sourceName + " (positive = into " + sourceName + ")";
            } else {
                arr[4] = "Flow: " + sourceName + " \u2192 " + targetName + " (positive = " + sourceName + " to " + targetName + ")";
            }
            arr[5] = "Initial (t=0): " + (initEq != null && !initEq.trim().isEmpty() ? initEq : "(none)");
            arr[6] = "Slider: " + rows[hoveredRow].sliderVarName + " = " + getShortUnitText(rows[hoveredRow].sliderValue, "");
            arr[7] = "Output: " + getUnitText(rows[hoveredRow].outputValue, "");
            
            if (rows[hoveredRow].compiledExpr == null) {
                arr[8] = "\u26A0 Equation parse error";
            } else if (isAdjustableRow(hoveredRow)) {
                arr[8] = "scroll to adjust value";
            }
        } else {
            arr[4] = "Initial (t=0): " + (initEq != null && !initEq.trim().isEmpty() ? initEq : "(none)");
        
            arr[5] = "Slider: " + rows[hoveredRow].sliderVarName + " = " + getShortUnitText(rows[hoveredRow].sliderValue, "");
            arr[6] = "Output: " + getUnitText(rows[hoveredRow].outputValue, "");
        
            if (rows[hoveredRow].compiledExpr == null) {
                arr[7] = "\u26A0 Equation parse error";
            } else if (isAdjustableRow(hoveredRow)) {
                arr[7] = "scroll to adjust value";
            }
        }
    }
    
    /**
     * Get the full flow-direction display name for a FLOW_MODE row.
     * Single-node: "gnd \u2192 S3", two-node: "S1 \u2192 S2".
     * For non-FLOW rows, returns getUIDisplayOutputName().
     */
    public String getFlowDisplayName(int row) {
        if (rows[row].outputMode == RowOutputMode.FLOW_MODE) {
            String nodeName = rows[row].outputName;
            String targetName = rows[row].targetNodeName;
            boolean singleNode = (targetName == null || targetName.trim().isEmpty() ||
                targetName.trim().equalsIgnoreCase("Gnd"));
            if (singleNode) {
                return "Gnd \u2192 " + nodeName;
            } else {
                return nodeName + " \u2192 " + targetName;
            }
        }
        return getUIDisplayOutputName(row);
    }

    /**
     * Get summary info when not hovering over a specific row.
     */
    private void getSummaryInfo(String[] arr) {
        arr[1] = "Rows: " + rowCount;
        
        int idx = 2;
        for (int row = 0; row < rowCount && idx < arr.length - 1; row++) {
            arr[idx++] = getFlowDisplayName(row) + " = " + getUnitText(rows[row].outputValue, "");
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
        return (row >= 0 && row < MAX_ROWS) ? rows[row].outputValue : 0.0;
    }
    
    /** 
     * Get display value for a row. For STOCK_MODE rows, returns the stock level
     * (node voltage) rather than the inflow rate stored in outputValue.
     * For other modes, returns outputValue.
     */
    public double getDisplayValue(int row) {
        if (row < 0 || row >= MAX_ROWS) return 0.0;
        if (rows[row].outputMode == RowOutputMode.STOCK_MODE && isMnaMode()) {
            // Show stock level (node voltage) for STOCK rows
            int nodeNum = rows[row].labeledNodeNumber;
            if (nodeNum > 0 && sim.nodeVoltages != null && nodeNum - 1 < sim.nodeVoltages.length) {
                return sim.nodeVoltages[nodeNum - 1];
            }
        }
        return rows[row].outputValue;
    }
    
    /** Get the number of active rows */
    public int getRowCount() { return rowCount; }
    
    /** Check if row is classified as alias (bare node reference) */
    public boolean isAliasRow(int row) {
        return (row >= 0 && row < MAX_ROWS) ? rows[row].isAlias : false;
    }
    
    /** Get row classification as string */
    public String getRowClassification(int row) {
        if (row < 0 || row >= MAX_ROWS) return "unknown";
        if (rows[row].isAlias) return "alias";
        return "dynamic";
    }
    
    /** Set the number of active rows (1 to MAX_ROWS) */
    public void setRowCount(int count) { 
        if (count >= 1 && count <= MAX_ROWS) {
            rowCount = count;
        }
    }
    
    /** Get output name for a row (source node only, for internal use) */
    public String getOutputName(int row) {
        return (row >= 0 && row < MAX_ROWS) ? rows[row].outputName : "";
    }
    
    /**
     * Get the display output name for a row (combined "source->target" format).
     * For FLOW/STOCK modes with an explicit non-ground target, returns "source->target".
     * For VOLTAGE mode, no target, or target="gnd", returns just the source name.
     * Always uses ASCII "->" separator for dump compatibility.
     * Ground targets are omitted since they are the implicit default.
     */
    public String getDisplayOutputName(int row) {
        if (row < 0 || row >= MAX_ROWS) return "";
        String name = rows[row].outputName;
        // Normalize any Unicode arrow characters in stored name to ASCII
        name = normalizeArrows(name);
        String target = rows[row].targetNodeName;
        if (target != null && !target.isEmpty() && !target.trim().equalsIgnoreCase("gnd")) {
            target = normalizeArrows(target);
            return name + "->" + target;
        }
        return name;
    }
    
    /**
     * Get the UI display output name for a row with mode-aware Unicode separators.
     * For FLOW mode with an explicit non-ground target, returns "source\u2192target" (→).
     * For STOCK mode with an explicit non-ground target, returns "source\u22A3\u22A2target" (⊣⊢).
     * Single-node names (no target, or target="gnd") display as just the node name,
     * since ground reference is the implicit default.
     * Use this for rendering, tooltips, and user-facing display.
     * For serialization (dump), use getDisplayOutputName() instead.
     */
    public String getUIDisplayOutputName(int row) {
        if (row < 0 || row >= MAX_ROWS) return "";
        String name = normalizeArrows(rows[row].outputName);
        String target = rows[row].targetNodeName;
        if (target != null && !target.isEmpty() && !target.trim().equalsIgnoreCase("gnd")) {
            target = normalizeArrows(target);
            RowOutputMode mode = rows[row].outputMode;
            if (mode == RowOutputMode.STOCK_MODE) {
                return name + "\u22A3\u22A2" + target;  // ⊣⊢
            } else {
                return name + "\u2192" + target;  // → (flow and default)
            }
        }
        return name;
    }
    
    /**
     * Normalize Unicode arrow/separator characters to ASCII.
     * Strips → (U+2192) and ⊣⊢ (U+22A3 U+22A2) that may have been
     * entered via font ligatures or user input.
     */
    static String normalizeArrows(String s) {
        if (s == null) return s;
        return s.replace("\u2192", "->").replace("\u22A3\u22A2", "->"); 
    }
    
    /**
     * Set output name for a row. Accepts combined "source->target" format.
     * If the name contains "->", it is split into source and target parts.
     * Otherwise, only the source name is set (target is not changed).
     */
    public void setOutputName(int row, String name) {
        if (row >= 0 && row < MAX_ROWS) {
            String[] parts = parseCombinedName(name);
            rows[row].outputName = parts[0];
            if (!parts[1].isEmpty()) {
                rows[row].targetNodeName = parts[1];
            } else {
                // No "->" in name: clear target (combined format with no target)
                rows[row].targetNodeName = "";
            }
        }
    }
    
    /**
     * Parse a combined "source->target" name into its parts.
     * @param combined The combined name (e.g., "S1->S2", "rate", "S1->gnd")
     * @return String[2] where [0]=source name, [1]=target name (empty if no separator)
     */
    static String[] parseCombinedName(String combined) {
        if (combined == null) return new String[]{"", ""};
        
        // Check for separators in priority order:
        // 1. ASCII "->" (standard format)
        // 2. ASCII "-||-" (stock separator typed without Unicode)
        // 3. Unicode "→" (U+2192, flow arrow from UI display)
        // 4. Unicode "⊣⊢" (U+22A3 U+22A2, stock separator from UI display)
        // 5. Comma "," (convenient shorthand for either mode)
        int arrowIdx = combined.indexOf("->");
        int sepLen = 2;
        if (arrowIdx < 0) {
            arrowIdx = combined.indexOf("-||-");
            sepLen = 4;
        }
        if (arrowIdx < 0) {
            arrowIdx = combined.indexOf("\u2192"); // →
            sepLen = 1;
        }
        if (arrowIdx < 0) {
            arrowIdx = combined.indexOf("\u22A3\u22A2"); // ⊣⊢
            sepLen = 2;
        }
        if (arrowIdx < 0) {
            arrowIdx = combined.indexOf(",");
            sepLen = 1;
        }
        if (arrowIdx >= 0) {
            return new String[]{
                combined.substring(0, arrowIdx).trim(),
                combined.substring(arrowIdx + sepLen).trim()
            };
        }
        return new String[]{combined.trim(), ""};
    }
    
    /** Get equation for a row */
    public String getEquation(int row) {
        return (row >= 0 && row < MAX_ROWS) ? rows[row].equation : "";
    }
    
    /** Set equation for a row */
    public void setEquation(int row, String eq) {
        if (row >= 0 && row < MAX_ROWS) {
            rows[row].equation = eq;
        }
    }
    
    /** Get initial equation for a row */
    public String getInitialEquation(int row) {
        return (row >= 0 && row < MAX_ROWS) ? rows[row].initialEquation : "";
    }
    
    /** Set initial equation for a row */
    public void setInitialEquation(int row, String eq) {
        if (row >= 0 && row < MAX_ROWS) {
            rows[row].initialEquation = eq;
        }
    }
    
    /** Get slider variable name for a row */
    public String getSliderVarName(int row) {
        return (row >= 0 && row < MAX_ROWS) ? rows[row].sliderVarName : "";
    }
    
    /** Set slider variable name for a row */
    public void setSliderVarName(int row, String name) {
        if (row >= 0 && row < MAX_ROWS) {
            rows[row].sliderVarName = name;
        }
    }
    
    /** Get slider value for a row */
    public double getSliderValue(int row) {
        return (row >= 0 && row < MAX_ROWS) ? rows[row].sliderValue : 0.0;
    }
    
    /** Set slider value for a row */
    public void setSliderValue(int row, double val) {
        if (row >= 0 && row < MAX_ROWS) {
            rows[row].sliderValue = val;
        }
    }
    
    /** Get output mode for a row */
    public RowOutputMode getOutputMode(int row) {
        return (row >= 0 && row < MAX_ROWS) ? rows[row].outputMode : RowOutputMode.VOLTAGE_MODE;
    }
    
    /** Set output mode for a row */
    public void setOutputMode(int row, RowOutputMode mode) {
        if (row >= 0 && row < MAX_ROWS) {
            rows[row].outputMode = mode;
        }
    }
    
    /** Get target node name for a row (used in FLOW_MODE/STOCK_MODE) */
    public String getTargetNodeName(int row) {
        return (row >= 0 && row < MAX_ROWS && rows[row].targetNodeName != null) ? rows[row].targetNodeName : "";
    }
    
    /** Set target node name for a row */
    public void setTargetNodeName(int row, String name) {
        if (row >= 0 && row < MAX_ROWS) {
            rows[row].targetNodeName = (name != null && !name.isEmpty()) ? name : null;
        }
    }
    
    /** Get capacitance for a row (used in STOCK_MODE) */
    public double getCapacitance(int row) {
        return (row >= 0 && row < MAX_ROWS) ? rows[row].capacitance : 1.0;
    }
    
    /** Set capacitance for a row */
    public void setCapacitance(int row, double cap) {
        if (row >= 0 && row < MAX_ROWS) {
            rows[row].capacitance = (cap > 0) ? cap : 1.0;
        }
    }
    
    /** Get whether a row uses backward Euler (true) or trapezoidal (false, default) */
    public boolean getUseBackwardEuler(int row) {
        return (row >= 0 && row < MAX_ROWS) && rows[row].useBackwardEuler;
    }
    
    /** Set whether a row uses backward Euler (true) or trapezoidal (false) */
    public void setUseBackwardEuler(int row, boolean backwardEuler) {
        if (row >= 0 && row < MAX_ROWS) {
            rows[row].useBackwardEuler = backwardEuler;
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
        String eq = rows[hoveredRow].equation.trim();
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
        rows[hoveredRow].equation = formatNumericValue(newValue);
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
        return parseNumericEquation(rows[row].equation) != null;
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
