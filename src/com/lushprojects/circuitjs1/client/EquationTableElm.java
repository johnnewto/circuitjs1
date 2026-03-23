/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.economics.*;

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
 *       or current sources. Outputs connect to labeled nodes.</li>
 *   <li><b>Pure Computational mode:</b> No electrical posts or circuit connections.
 *       Values are written to the ComputedValues registry for other elements to read.</li>
 * </ul>
 * 
 * <h3>Features:</h3>
 * <ul>
 *   <li>Multiple rows (1-128), each defining an independent equation</li>
 *   <li>Each row has a named output that becomes accessible as a labeled node</li>
 *   <li>Custom slider variable per row for interactive parameter adjustment</li>
 *   <li>Support for initial value equations (evaluated only at t=0)</li>
 *   <li>Row output modes: VOLTAGE (default), FLOW (current source), PARAM (computed-only)</li>
 *   <li>FLOW rows publish endpoint flow keys with direction sign:
 *       &lt;source&gt;.flow = -value, &lt;target&gt;.flow = +value
 *       (target omitted when it is ground)</li>
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
public class EquationTableElm extends CircuitElm implements MouseWheelHandler {
    
    //=============================================================================
    // CONSTANTS
    //=============================================================================
    
    /** Flag indicating small display mode */
    private static final int FLAG_SMALL = 1;
    
    /** Flag indicating MNA (electrical) mode vs pure computational mode */
    private static final int FLAG_MNA_MODE = 2;
    
    /** Maximum number of equation rows supported */
    public static final int MAX_ROWS = 128;

    /** Default FLOW shunt resistance to avoid loading by default. */
    private static final double DEFAULT_FLOW_SHUNT_RESISTANCE = 1;

    /** Default base convergence tolerance for equation convergence checks. */
    private static final double DEFAULT_CONVERGENCE_TOLERANCE = 0.001;
    
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
        /** Parameter mode: computes value and publishes to ComputedValues only */
        PARAM_MODE
    }

    /** True when a row name encodes a non-simulating comment line. */
    public static boolean isCommentRowName(String name) {
        return name != null && name.trim().startsWith("#");
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
        double shuntResistance;
        boolean useBackwardEuler;  // false = trapezoidal (default), true = backward Euler
        
        // Compiled/derived state
        Expr compiledExpr;
        Expr compiledInitialExpr;
        ExprState exprState;
        double outputValue;
        double lastOutputValue;
        boolean initialValueApplied;
        boolean hasDiffExpr;
        boolean isStock;
        boolean isCyclic;
        boolean lastNewtonJacobianApplied;
        String lastNewtonJacobianStatus;
        
        // MNA runtime state (FLOW_MODE)
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
            sliderVarName = "";
            sliderValue = 0;
            exprState = null;  // lazily allocated when row is evaluated
            outputMode = RowOutputMode.VOLTAGE_MODE;
            targetNodeName = "";
            shuntResistance = DEFAULT_FLOW_SHUNT_RESISTANCE;
            useBackwardEuler = false;  // trapezoidal by default
            targetNodeNumber = -1;
            labeledNodeNumber = -1;
            rowVoltSource = -1;
            lastNewtonJacobianApplied = false;
            lastNewtonJacobianStatus = "not attempted";
        }
        
        /** Reset runtime state for simulation restart */
        protected void reset() {
            if (exprState != null) exprState.reset();
            outputValue = 0.0;
            lastOutputValue = 0.0;
            initialValueApplied = false;
            flowValue = 0.0;
            isStock = false;
            isCyclic = false;
            lastNewtonJacobianApplied = false;
            lastNewtonJacobianStatus = "not attempted";
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
                int vn = voltSource + vs + sim.getCircuitAnalyzer().getNodeList().size();
                sim.stampNonLinear(vn);
                sim.stampVoltageSource(0, nodeNum, voltSource + vs);
                
                // Stamp tiny load resistance to prevent matrix elimination
                sim.stampResistor(nodeNum, 0, 1e9);
            }
        }
        
        @Override
        public void evaluate(int row) {
            if (rows[row].compiledExpr == null) return;

            rows[row].lastNewtonJacobianApplied = false;
            rows[row].lastNewtonJacobianStatus = "not attempted";
            
            ExprState state = prepareEvalState(row);
            double equationValue = EquationTableSemantics.computeVoltageRowValue(rows[row].compiledExpr, state);
            
            if (DEBUG) {
                CirSim.console("[EquationTableElm." + tableName + "] Row " + row + 
                    " (" + rows[row].outputName + "): eq=" + rows[row].equation + " val=" + equationValue);
            }
            
            checkEquationConvergence(row, equationValue);
            rows[row].lastOutputValue = equationValue;
            rows[row].outputValue = equationValue;
            
            // MNA mode: stamp to matrix
            if (isMnaMode() && rows[row].rowVoltSource >= 0) {
                int vn = voltSource + rows[row].rowVoltSource + sim.getCircuitAnalyzer().getNodeList().size();
                if (!stampVoltageModeNewtonJacobian(row, state, equationValue, vn)) {
                    if ("not attempted".equals(rows[row].lastNewtonJacobianStatus)) {
                        rows[row].lastNewtonJacobianStatus = "fallback: direct rhs";
                    }
                    sim.stampRightSide(vn, equationValue);
                }
            } else {
                rows[row].lastNewtonJacobianStatus = "not in mna voltage path";
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

            // Stabilize FLOW-only/floating nodes with configurable shunt resistance.
            // Lowering shunt R creates a real electrical load at FLOW endpoints.
            double shuntR = rows[row].shuntResistance;
            if (shuntR <= 0) shuntR = DEFAULT_FLOW_SHUNT_RESISTANCE;
            if (sourceNode > 0) sim.stampResistor(sourceNode, 0, shuntR);
            if (targetNode > 0) sim.stampResistor(targetNode, 0, shuntR);
            
            sim.stampRightSide(sourceNode);
            sim.stampRightSide(targetNode);
        }
        
        @Override
        public void evaluate(int row) {
            if (rows[row].compiledExpr == null) return;

            rows[row].lastNewtonJacobianApplied = false;
            rows[row].lastNewtonJacobianStatus = "not attempted";
            
            int sourceNode = rows[row].labeledNodeNumber;
            int targetNode = rows[row].targetNodeNumber;
            if (sourceNode < 0 || targetNode < 0) return;
            
            ExprState state = prepareEvalState(row);
            double flowValue = EquationTableSemantics.computeFlowRowValue(
                rows[row].compiledExpr,
                state,
                volts,
                sourceNode,
                targetNode,
                rows[row].shuntResistance
            );
            
            rows[row].flowValue = flowValue;
            checkEquationConvergence(row, flowValue);
            rows[row].lastOutputValue = flowValue;
            rows[row].outputValue = flowValue;
            registerFlowValue(row, flowValue);

            if (!stampFlowModeNewtonJacobian(row, state, flowValue, sourceNode, targetNode)) {
                if ("not attempted".equals(rows[row].lastNewtonJacobianStatus)) {
                    rows[row].lastNewtonJacobianStatus = "fallback: direct current source";
                }
                sim.stampCurrentSource(sourceNode, targetNode, flowValue);
            }
            // Do NOT registerOutputValue here: outputName for FLOW is the source
            // node name (e.g. "S1" from "S1->S2"), and registering the flow
            // magnitude would clobber the node's main computed voltage/value name.
            // FLOW values are published via a separate .flow key namespace.
        }
    }

    /**
     * PARAM_MODE handler: computes an equation and publishes the value only.
     * No matrix stamping, no nodes, no voltage/current sources.
     */
    private class ParamModeHandler implements RowModeHandler {
        @Override
        public void stamp(int row) {
            // PARAM mode is computation-only.
        }

        @Override
        public void evaluate(int row) {
            if (rows[row].compiledExpr == null) return;

            ExprState state = prepareEvalState(row);
            double parameterValue = EquationTableSemantics.computeParamRowValue(rows[row].compiledExpr, state);

            checkEquationConvergence(row, parameterValue);
            rows[row].lastOutputValue = parameterValue;
            rows[row].outputValue = parameterValue;

            registerOutputValue(row, parameterValue);
        }
    }
    
    /** Cached handler instances (created lazily) */
    private RowModeHandler voltageModeHandler, flowModeHandler, paramModeHandler;
    
    /** Get the handler for a given output mode */
    private RowModeHandler getHandler(RowOutputMode mode) {
        switch (mode) {
        case FLOW_MODE:
            if (flowModeHandler == null) flowModeHandler = new FlowModeHandler();
            return flowModeHandler;
        case PARAM_MODE:
            if (paramModeHandler == null) paramModeHandler = new ParamModeHandler();
            return paramModeHandler;
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
    
    /** Whether row classifications are up to date (avoids redundant recomputation) */
    private boolean classificationsValid;

    /** PARAM_MODE names currently registered for this table (reference-counted globally). */
    private java.util.HashSet<String> registeredParamNames;

    /** Pre-registered ComputedValues keys currently registered for this table. */
    private java.util.HashSet<String> registeredComputedNames;

    /** True when parameter/computed name registries need recomputation. */
    private boolean nameRegistriesDirty;
    
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
        rows[0].sliderVarName = "";
        rows[0].sliderValue = 0;
        
        rows[1].outputName = "Y2";
        rows[1].equation = "0";
        rows[1].sliderVarName = "";
        rows[1].sliderValue = 0;
        
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
        // Newer format: includes outputMode/target and legacy compatibility fields
        int tokenCount = 0;
        java.util.ArrayList<String> tokens = new java.util.ArrayList<String>();
        while (st.hasMoreTokens()) {
            tokens.add(st.nextToken());
            tokenCount++;
        }
        
        // Determine format:
        // 10 tokens/row = newest format (+ shuntResistance)
        // 9 tokens/row  = previous newest format (no shuntResistance)
        // 8 tokens/row  = new format (no backward Euler flag)
        // 5 tokens/row  = old format
        int tokenStartIndex = 0;
        int remainingTokenCount = tokenCount;

        boolean newestFormatWithShunt = (remainingTokenCount == rowCount * 10);
        boolean newestFormat = newestFormatWithShunt || (remainingTokenCount == rowCount * 9);
        boolean newFormat = newestFormat || (remainingTokenCount == rowCount * 8);
        
        // Parse per-row data
        int tokenIndex = tokenStartIndex;
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
                    rows[row].sliderValue = 0;
                }
            }
            
            // New format fields (only if new format detected)
            if (newFormat) {
                // Output mode
                if (tokenIndex < tokens.size()) {
                    try {
                        int modeOrdinal = Integer.parseInt(tokens.get(tokenIndex++));
                        if (modeOrdinal == 0) {
                            rows[row].outputMode = RowOutputMode.VOLTAGE_MODE;
                        } else if (modeOrdinal == 1) {
                            rows[row].outputMode = RowOutputMode.FLOW_MODE;
                        } else if (modeOrdinal == 2) {
                            // Backward compatibility: old dumps used 2=STOCK_MODE.
                            rows[row].outputMode = RowOutputMode.FLOW_MODE;
                        } else if (modeOrdinal == 3) {
                            // Backward compatibility: old dumps used 3=PARAM_MODE.
                            rows[row].outputMode = RowOutputMode.PARAM_MODE;
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
                // Legacy capacitance token (obsolete): consume and ignore
                if (tokenIndex < tokens.size()) {
                    try {
                        Double.parseDouble(tokens.get(tokenIndex++));
                    } catch (Exception e) {
                        tokenIndex++; // Skip invalid token
                    }
                }
                // Shunt resistance (newest format only)
                if (newestFormatWithShunt && tokenIndex < tokens.size()) {
                    try {
                        rows[row].shuntResistance = Double.parseDouble(tokens.get(tokenIndex++));
                        if (rows[row].shuntResistance <= 0) rows[row].shuntResistance = DEFAULT_FLOW_SHUNT_RESISTANCE;
                    } catch (Exception e) {
                        tokenIndex++;
                        rows[row].shuntResistance = DEFAULT_FLOW_SHUNT_RESISTANCE;
                    }
                } else {
                    rows[row].shuntResistance = DEFAULT_FLOW_SHUNT_RESISTANCE;
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
        registeredParamNames = new java.util.HashSet<String>();
        registeredComputedNames = new java.util.HashSet<String>();
        nameRegistriesDirty = true;
    }

    /** Mark global parameter/computed name registries for this table as stale. */
    private void markNameRegistriesDirty() {
        nameRegistriesDirty = true;
    }

    /** Refresh global name registries only when row metadata changed. */
    private void ensureNameRegistriesUpToDate() {
        if (!nameRegistriesDirty) {
            return;
        }
        refreshParameterNameRegistry();
        refreshComputedNameRegistry();
        nameRegistriesDirty = false;
    }

    /**
     * Synchronize PARAM_MODE output names with the global parameter-name registry.
     * This allows Expr node references in MNA mode to resolve PARAM names from
     * ComputedValues before same-named labeled-node voltages.
     */
    private void refreshParameterNameRegistry() {
        if (registeredParamNames == null) {
            registeredParamNames = new java.util.HashSet<String>();
        }

        java.util.HashSet<String> currentParamNames = new java.util.HashSet<String>();
        for (int row = 0; row < rowCount; row++) {
            if (rows[row].outputMode != RowOutputMode.PARAM_MODE) continue;
            if (!isValidOutputName(row)) continue;
            currentParamNames.add(rows[row].outputName.trim());
        }

        for (String name : registeredParamNames) {
            if (!currentParamNames.contains(name)) {
                ComputedValues.unregisterParameterName(name);
            }
        }

        for (String name : currentParamNames) {
            if (!registeredParamNames.contains(name)) {
                ComputedValues.registerParameterName(name);
            }
        }

        registeredParamNames = currentParamNames;
    }

    /**
     * Pre-register expected ComputedValues keys for this table.
     *
     * Keys tracked:
     * - row output names
     * - FLOW namespace keys (<name>.flow) for source/target
     * - per-row slider variable names
     */
    private void refreshComputedNameRegistry() {
        if (registeredComputedNames == null) {
            registeredComputedNames = new java.util.HashSet<String>();
        }

        java.util.HashSet<String> currentComputedNames = new java.util.HashSet<String>();
        for (int row = 0; row < rowCount; row++) {
            if (isValidOutputName(row)) {
                String outputName = rows[row].outputName.trim();
                currentComputedNames.add(outputName);

                if (rows[row].outputMode == RowOutputMode.FLOW_MODE) {
                    String sourceFlowKey = getFlowComputedKeyForName(outputName);
                    if (sourceFlowKey != null && !sourceFlowKey.isEmpty()) {
                        currentComputedNames.add(sourceFlowKey);
                    }

                    String targetName = rows[row].targetNodeName;
                    if (targetName != null) {
                        String trimmedTarget = targetName.trim();
                        boolean hasNonGroundTarget = !trimmedTarget.isEmpty() && !trimmedTarget.equalsIgnoreCase("gnd");
                        if (hasNonGroundTarget) {
                            String targetFlowKey = getFlowComputedKeyForName(trimmedTarget);
                            if (targetFlowKey != null && !targetFlowKey.isEmpty()) {
                                currentComputedNames.add(targetFlowKey);
                            }
                        }
                    }
                }
            }

            String sliderVar = rows[row].sliderVarName;
            if (sliderVar != null) {
                String trimmedSlider = sliderVar.trim();
                if (!trimmedSlider.isEmpty()) {
                    currentComputedNames.add(trimmedSlider);
                }
            }
        }

        for (String name : registeredComputedNames) {
            if (!currentComputedNames.contains(name)) {
                ComputedValues.unregisterComputedName(name);
            }
        }

        for (String name : currentComputedNames) {
            if (!registeredComputedNames.contains(name)) {
                ComputedValues.registerComputedName(name);
            }
        }

        registeredComputedNames = currentComputedNames;
    }

    /**
     * Unregister all names currently tracked by this element from global registries.
     */
    private void unregisterNameRegistries() {
        if (registeredParamNames != null) {
            for (String name : registeredParamNames) {
                ComputedValues.unregisterParameterName(name);
            }
            registeredParamNames.clear();
        }

        if (registeredComputedNames != null) {
            for (String name : registeredComputedNames) {
                ComputedValues.unregisterComputedName(name);
            }
            registeredComputedNames.clear();
        }
        nameRegistriesDirty = false;
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
    protected void setPoints() {
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
            int classIconWidth = opsize == 1 ? 12 : 16;  // "⟳" icon (cyclic rows only)
            int valueSpacing = opsize == 1 ? 16 : 20;

            for (int row = 0; row < rowCount; row++) {
                String displayText;
                int leftIconsWidth;
                if (isCommentRow(row)) {
                    displayText = "# " + getCommentText(row);
                    leftIconsWidth = 0;
                } else {
                    displayText = getUIDisplayOutputName(row) + " = " + rows[row].equation;
                    leftIconsWidth = 0;
                    if ("cyclic".equals(getRowClassification(row))) {
                        leftIconsWidth += classIconWidth;
                    }
                    // Add adjustable scroll icon if applicable
                    if (isAdjustableRow(row))
                        leftIconsWidth += scrollIconWidth;
                    // Add output mode icon if not VOLTAGE_MODE
                    if (rows[row].outputMode != RowOutputMode.VOLTAGE_MODE)
                        leftIconsWidth += modeIconWidth;
                }
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
    protected int getDumpType() { return 266; }
    
    /** @return true if in MNA (electrical) mode, false for pure computational */
    boolean isMnaMode() { return sim != null && sim.isEquationTableMnaMode(); }
    
    /** @return Number of electrical posts - always 0 (no visible posts) */
    protected int getPostCount() { return 0; }
    
    /**
     * Get the position of a post.
     * Posts are positioned along the right edge of the table.
     * 
     * @param n Post index (0 to rowCount-1)
     * @return Point location of the post
     */
    protected Point getPost(int n) {
        int postY = y + rowHeight + cellPadding + n * rowHeight + rowHeight / 2;
        return new Point(x + tableWidth, postY);
    }
    
    /** 
    * @return Number of voltage sources needed - one per VOLTAGE_MODE row with valid output name.
    * FLOW_MODE and PARAM_MODE don't use voltage sources.
     * This counts voltage sources before findLabeledNodes() runs, so we count all valid rows.
     */
    protected int getVoltageSourceCount() { 
        if (!isMnaMode()) return 0;
        
        updateRowClassifications();
        int count = 0;
        for (int row = 0; row < rowCount; row++) {
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
    * VOLTAGE_MODE may need internal nodes.
     * FLOW_MODE may also reserve internal nodes for missing source/target names;
     * these are auto-created during global coordination pass 2.
    * PARAM_MODE is computation-only and never needs nodes.
     */
    int getInternalNodeCount() {
        if (!isMnaMode()) return 0;
        
        updateRowClassifications();
        int count = 0;
        for (int row = 0; row < rowCount; row++) {
            if (rows[row].outputMode == RowOutputMode.FLOW_MODE) {
                // FLOW source may need an internal node if unresolved
                if (isValidOutputName(row)) {
                    Integer existingSource = LabeledNodeElm.getByName(rows[row].outputName.trim());
                    if (existingSource == null || existingSource < 0) {
                        count++;
                    }
                }

                // FLOW target may need an internal node if explicit and unresolved
                String targetName = rows[row].targetNodeName;
                boolean singleNode = (targetName == null || targetName.trim().isEmpty() ||
                        targetName.trim().equalsIgnoreCase("gnd"));
                if (!singleNode) {
                    Integer existingTarget = LabeledNodeElm.getByName(targetName.trim());
                    if (existingTarget == null || existingTarget < 0) {
                        count++;
                    }
                }
            } else if (rows[row].outputMode != RowOutputMode.PARAM_MODE && isValidOutputName(row)) {
                // VOLTAGE: only need internal node if NO existing LabeledNode
                Integer existingNode = LabeledNodeElm.getByName(rows[row].outputName.trim());
                if (existingNode == null || existingNode < 0) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /** @return true if any row has an expression requiring iterative solving,
     *  or if any row uses FLOW_MODE output mode (which always requires doStep) */
    protected boolean nonLinear() {
        updateRowClassifications();
        for (int row = 0; row < rowCount; row++) {
            // FLOW_MODE always requires doStep() evaluation
            if (rows[row].outputMode == RowOutputMode.FLOW_MODE) {
                return true;
            }
            if (!isCommentRow(row))
                return true;
        }
        return false;
    }
    
    /**
     * Update cached per-row classification metadata used by simulation.
     * Results are cached; call {@link #invalidateClassifications()} when equations change.
     */
    private void updateRowClassifications() {
        if (classificationsValid) return;

        java.util.Set<String> cyclicalNodeNames = (sim != null)
                ? SFCRDagBlocksViewer.getCyclicalNodeNames(sim, false, true)
                : null;
        for (int row = 0; row < rowCount; row++) {
            rows[row].hasDiffExpr = false;
            rows[row].isStock = false;
            rows[row].isCyclic = false;

            if (isCommentRow(row)) continue;

            String outputName = rows[row].outputName;
            if (cyclicalNodeNames != null && outputName != null) {
                String trimmed = outputName.trim();
                if (trimmed.length() > 0 && cyclicalNodeNames.contains(trimmed)) {
                    rows[row].isCyclic = true;
                }
            }
            
            if (rows[row].compiledExpr == null) continue;

            rows[row].isStock = isTopLevelStockExpression(rows[row].compiledExpr);
            
            // Precompute diff() presence for convergence checks
            rows[row].hasDiffExpr = rows[row].equation.contains("diff");
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
     * Coordinate EquationTable label resolution across all tables before stamping.
     *
     * Lightweight global coordination (no merged master table):
    * 1) First pass: register non-FLOW row outputs (VOLTAGE/PARAM) so their names
     *    resolve globally regardless of per-element stamp order.
     * 2) Second pass: validate FLOW row endpoints and emit diagnostics when missing.
     *
     * Called from CirSim.stampCircuit() after node allocation and before per-element
     * stamp() calls.
     */
    public static void coordinateLabelsForStamp(java.util.Vector<CircuitElm> elmList) {
        if (elmList == null || elmList.isEmpty()) {
            return;
        }

        java.util.ArrayList<EquationTableElm> tables = new java.util.ArrayList<EquationTableElm>();
        for (int i = 0; i < elmList.size(); i++) {
            CircuitElm ce = elmList.get(i);
            if (ce instanceof EquationTableElm) {
                EquationTableElm table = (EquationTableElm) ce;
                if (table.isMnaMode()) {
                    // Force a fresh classification pass now that all EquationTables
                    // are present in sim.elmList.
                    table.invalidateClassifications();
                    table.updateRowClassifications();
                    tables.add(table);
                }
            }
        }

        if (tables.isEmpty()) {
            return;
        }

        java.util.HashMap<EquationTableElm, Integer> nextInternalIdx = new java.util.HashMap<EquationTableElm, Integer>();

        // Pass 1: create/register all non-FLOW row output names first.
        for (int t = 0; t < tables.size(); t++) {
            EquationTableElm table = tables.get(t);
            int internalNodeIdx = 0;

            for (int row = 0; row < table.rowCount; row++) {
                EquationRow rowData = table.rows[row];

                if (rowData.outputMode == RowOutputMode.FLOW_MODE || rowData.outputMode == RowOutputMode.PARAM_MODE) continue;
                if (!table.isValidOutputName(row)) continue;

                String outputName = rowData.outputName.trim();
                Integer existingNodeNum = LabeledNodeElm.getByName(outputName);
                if (existingNodeNum != null && existingNodeNum >= 0) {
                    continue;
                }

                if (table.nodes != null && internalNodeIdx < table.nodes.length) {
                    int internalNode = table.nodes[internalNodeIdx];
                    table.registerInternalNodeAsLabel(outputName, internalNode);
                    internalNodeIdx++;
                }
            }

            nextInternalIdx.put(table, Integer.valueOf(internalNodeIdx));
        }

        // Pass 2: auto-create missing FLOW endpoints, then validate.
        for (int t = 0; t < tables.size(); t++) {
            EquationTableElm table = tables.get(t);
            int internalNodeIdx = 0;
            Integer savedIdx = nextInternalIdx.get(table);
            if (savedIdx != null) {
                internalNodeIdx = savedIdx.intValue();
            }

            for (int row = 0; row < table.rowCount; row++) {
                EquationRow rowData = table.rows[row];

                if (rowData.outputMode != RowOutputMode.FLOW_MODE) continue;
                if (!table.isValidOutputName(row)) continue;

                String sourceName = rowData.outputName.trim();
                Integer sourceNode = LabeledNodeElm.getByName(sourceName);
                if (sourceNode == null || sourceNode < 0) {
                    if (table.nodes != null && internalNodeIdx < table.nodes.length) {
                        int internalNode = table.nodes[internalNodeIdx++];
                        table.registerInternalNodeAsLabel(sourceName, internalNode);
                        sourceNode = Integer.valueOf(internalNode);
                    }
                    if (sourceNode == null || sourceNode.intValue() < 0) {
                        CirSim.console("[EquationTableCoord." + table.tableName + "] FLOW row " + row +
                                " missing source node '" + sourceName + "'");
                    }
                }

                String targetName = rowData.targetNodeName;
                boolean singleNode = (targetName == null || targetName.trim().isEmpty() ||
                        targetName.trim().equalsIgnoreCase("gnd"));
                if (!singleNode) {
                    String trimmedTarget = targetName.trim();
                    Integer targetNode = LabeledNodeElm.getByName(trimmedTarget);
                    if (targetNode == null || targetNode < 0) {
                        if (table.nodes != null && internalNodeIdx < table.nodes.length) {
                            int internalNode = table.nodes[internalNodeIdx++];
                            table.registerInternalNodeAsLabel(trimmedTarget, internalNode);
                            targetNode = Integer.valueOf(internalNode);
                        }
                        if (targetNode == null || targetNode.intValue() < 0) {
                            CirSim.console("[EquationTableCoord." + table.tableName + "] FLOW row " + row +
                                    " missing target node '" + trimmedTarget + "'");
                        }
                    }
                }
            }

            nextInternalIdx.put(table, Integer.valueOf(internalNodeIdx));
        }
    }
    
    /**
     * Check if a row's output name is non-null and non-empty.
     * Consolidates the repeated `name != null && !name.trim().isEmpty()` pattern.
     * @param row Row index
     * @return true if the output name is usable
     */
    private boolean isValidOutputName(int row) {
        String name = rows[row].outputName;
        return name != null && !name.trim().isEmpty() && !isCommentRowName(name);
    }

    /** Ensure row has an ExprState allocated before evaluation/state updates. */
    private ExprState getOrCreateExprState(int row) {
        if (rows[row].exprState == null) {
            rows[row].exprState = new ExprState(1);
        }
        return rows[row].exprState;
    }
    
    /**
     * Prepare the evaluation state for a row before evaluating its expression.
     * Sets slider value, time, and registers the slider variable in ComputedValues.
     * @param row Row index
     * @return The prepared ExprState, ready for eval()
     */
    private ExprState prepareEvalState(int row) {
        ExprState state = getOrCreateExprState(row);
        state.values[0] = rows[row].sliderValue;
        state.t = sim.getTime();
        
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
            ComputedValues.setComputedValue(rows[row].outputName.trim(), value, this);
        }
    }

    /**
     * Build parser-safe ComputedValues key for a FLOW output name.
     * Key format: <sanitizedOutputName>.flow
     */
    static String getFlowComputedKeyForName(String outputName) {
        return ComputedValues.getFlowComputedKeyForName(outputName);
    }

    /**
        * Register FLOW value in ComputedValues under dedicated *.flow keys.
     *
     * Sign convention for two-node flow S1->S2:
        * - S1.flow = -value (outflow from source stock)
        * - S2.flow = +value (inflow to target stock)
     *
        * For one-node/ground-target flow, only <source>.flow is published.
     */
    private void registerFlowValue(int row, double value) {
        if (!isValidOutputName(row)) return;

        String sourceName = rows[row].outputName.trim();
        String sourceKey = getFlowComputedKeyForName(sourceName);
        String targetName = rows[row].targetNodeName;
        boolean hasNonGroundTarget = false;
        if (targetName != null) {
            String trimmedTarget = targetName.trim();
            hasNonGroundTarget = !trimmedTarget.isEmpty() && !trimmedTarget.equalsIgnoreCase("gnd");
        }

        // Two-node flows publish source as negative (outflow), otherwise source-only.
        double sourcePublished = hasNonGroundTarget ? -value : value;
        if (sourceKey != null) {
            ComputedValues.setComputedValue(sourceKey, sourcePublished);
        }

        if (hasNonGroundTarget) {
            String trimmedTarget = targetName.trim();
            String targetKey = getFlowComputedKeyForName(trimmedTarget);
            if (targetKey != null) {
                ComputedValues.setComputedValue(targetKey, value);
            }
        }
    }

    /**
     * Phase 1 Newton Jacobian stamping for VOLTAGE_MODE MNA rows.
     *
     * Linearizes Vout = f(x) into: Vout - sum(df/dx_i * x_i) = f(x0) - sum(df/dx_i * x0_i)
     * and stamps the off-diagonal Jacobian entries on the voltage-source equation row.
     *
     * Scope guardrails for Phase 1:
     * - VOLTAGE_MODE rows only
     * - MNA/labeled-node dependencies only
     * - expressions with stateful historical operators are excluded
     *
     * @return true if Jacobian stamping was applied; false to use direct RHS stamping fallback.
     */
    private boolean stampVoltageModeNewtonJacobian(int row, ExprState state, double equationValue, int vn) {
        EquationRow rowData = rows[row];
        String ineligibleReason = EquationTableJacobianHelper.getVoltageModeIneligibilityReason(this, rowData);
        if (ineligibleReason != null) {
            rowData.lastNewtonJacobianStatus = ineligibleReason;
            return false;
        }

        java.util.LinkedHashSet<String> refs = new java.util.LinkedHashSet<String>();
        rowData.compiledExpr.collectSamePeriodRefs(refs);
        java.util.LinkedHashSet<String> skippedParamRefs = EquationTableJacobianHelper.collectSkippedParameterRefs(refs);
        String skippedParamsSuffix = EquationTableJacobianHelper.formatSkippedParameterRefs(skippedParamRefs);
        if (refs.isEmpty()) {
            rowData.lastNewtonJacobianStatus = "no same-period refs";
            return false;
        }

        // Value/voltage mode: single node, matrixSign = -1 → stamps -dx, rhs = +val - sum(dx·base)
        int[] stats = new int[3]; // [sawMnaRef, stampedCount, invalidCount]
        int stamped = EquationTableJacobianHelper.stampSingleNodeJacobian(this, rowData.compiledExpr,
                state, refs, equationValue, vn, -1.0, stats);
        if (stamped == 0) {
            if (stats[0] == 0) {
                rowData.lastNewtonJacobianStatus = "no mna refs" + skippedParamsSuffix;
            } else if (stats[2] > 0) {
                rowData.lastNewtonJacobianStatus = "mna refs but invalid derivatives" + skippedParamsSuffix;
            } else {
                rowData.lastNewtonJacobianStatus = "mna refs but no usable derivatives" + skippedParamsSuffix;
            }
            return false;
        }

        rowData.lastNewtonJacobianApplied = true;
        if (EquationTableJacobianHelper.hasHistoricalRefSyntax(rowData.equation)) {
            rowData.lastNewtonJacobianStatus = "applied (refs=" + refs.size() + "; hist ok)" + skippedParamsSuffix;
        } else {
            rowData.lastNewtonJacobianStatus = "applied (refs=" + refs.size() + ")" + skippedParamsSuffix;
        }
        return true;
    }

    /**
     * Phase 1 Newton Jacobian stamping for FLOW_MODE MNA rows.
     *
     * With a 1Ω shunt resistor to ground on every flow endpoint, the KCL identity
     * V_node = ±flowValue holds at each endpoint, making the flow Jacobian mathematically
     * equivalent to two value Jacobians — one per endpoint with opposite sign conventions:
     *
     *   sourceNode: matrixSign = +1  →  stamps +dx,  rhs = −flowValue + Σ dx·V₀
     *   targetNode: matrixSign = −1  →  stamps −dx,  rhs = +flowValue − Σ dx·V₀
     *
     * Both calls delegate to {@link #stampSingleNodeJacobian}, the same helper used by
     * VOLTAGE_MODE, eliminating the previously duplicated per-ref perturbation loop.
     *
     * @return true if Jacobian stamping was applied; false to use direct current-source fallback.
     */
    private boolean stampFlowModeNewtonJacobian(int row, ExprState state, double flowValue, int sourceNode, int targetNode) {
        EquationRow rowData = rows[row];
        String ineligibleReason = EquationTableJacobianHelper.getFlowModeIneligibilityReason(this, rowData, sourceNode, targetNode);
        if (ineligibleReason != null) {
            rowData.lastNewtonJacobianStatus = ineligibleReason;
            return false;
        }

        java.util.LinkedHashSet<String> refs = new java.util.LinkedHashSet<String>();
        rowData.compiledExpr.collectSamePeriodRefs(refs);
        java.util.LinkedHashSet<String> skippedParamRefs = EquationTableJacobianHelper.collectSkippedParameterRefs(refs);
        String skippedParamsSuffix = EquationTableJacobianHelper.formatSkippedParameterRefs(skippedParamRefs);
        if (refs.isEmpty()) {
            rowData.lastNewtonJacobianStatus = "no same-period refs";
            return false;
        }

        if (!EquationTableJacobianHelper.hasAnyMnaRefs(refs)) {
            rowData.lastNewtonJacobianStatus = "no mna refs" + skippedParamsSuffix;
            return false;
        }

        // Stamp each endpoint using the shared single-node value Jacobian helper.
        // sourceNode (current leaves):  matrixSign = +1 → stamps +dx, rhs = −flow + Σ dx·V₀
        // targetNode (current enters):  matrixSign = −1 → stamps −dx, rhs = +flow − Σ dx·V₀
        int[] statsSource = new int[3]; // [sawMnaRef, stampedCount, invalidCount]
        int[] statsTarget = new int[3];
        int stampedSource = (sourceNode > 0)
                ? EquationTableJacobianHelper.stampSingleNodeJacobian(this, rowData.compiledExpr,
                        state, refs, flowValue, sourceNode, +1.0, statsSource)
                : 0;
        int stampedTarget = (targetNode > 0)
                ? EquationTableJacobianHelper.stampSingleNodeJacobian(this, rowData.compiledExpr,
                        state, refs, flowValue, targetNode, -1.0, statsTarget)
                : 0;
        int totalStamped = stampedSource + stampedTarget;

        if (totalStamped == 0) {
            int totalInvalid = statsSource[2] + statsTarget[2];
            rowData.lastNewtonJacobianStatus = (totalInvalid > 0)
                    ? "mna refs but invalid derivatives" + skippedParamsSuffix
                    : "mna refs but no usable derivatives" + skippedParamsSuffix;
            return false;
        }

        rowData.lastNewtonJacobianApplied = true;
        if (EquationTableJacobianHelper.hasHistoricalRefSyntax(rowData.equation)) {
            rowData.lastNewtonJacobianStatus = "applied flow jacobian (refs=" + refs.size() + "; hist ok)" + skippedParamsSuffix;
        } else {
            rowData.lastNewtonJacobianStatus = "applied flow jacobian (refs=" + refs.size() + ")" + skippedParamsSuffix;
        }
        return true;
    }
    
    /**
     * Check if two posts are electrically connected.
     * High-impedance design: no current path exists between any posts.
     */
    protected boolean getConnection(int n1, int n2) { return false; }
    
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
    protected double getCurrentIntoNode(int n) {
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
        if (isCommentRow(row)) {
            rows[row].compiledInitialExpr = null;
            return;
        }
        String initEq = rows[row].initialEquation;
        if (initEq == null || initEq.trim().isEmpty()) {
            rows[row].compiledInitialExpr = null;
            return;
        }
        
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] Parsing initial equation row " + row + ": '" + initEq + "'");
        }
        
        try {
            String initialForParse = normalizeEquationSyntaxForRow(row, initEq);
            ExprParser parser = new ExprParser(initialForParse);
            rows[row].compiledInitialExpr = parser.parseExpression();
            String err = parser.gotError();
            
            if (err != null) {
                CirSim.console("EquationTableElm row " + row + ": Parse error in initial equation '" + initEq +
                        "' (normalized: '" + initialForParse + "'): " + err);
                rows[row].compiledInitialExpr = null;
            } else if (DEBUG) {
                CirSim.console("[EquationTableElm." + tableName + "] Row " + row + " initial equation parsed successfully");
            }
        } catch (Exception e) {
            String initialForParse = normalizeEquationSyntaxForRow(row, initEq);
            CirSim.console("EquationTableElm row " + row + ": Error parsing initial equation '" + initEq +
                    "' (normalized: '" + initialForParse + "'): " + e.getMessage());
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
        if (isCommentRow(row)) {
            rows[row].compiledExpr = null;
            return;
        }
        String equationForParse = normalizeEquationSyntaxForRow(row, rows[row].equation);
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] Parsing row " + row + ": '" + equationForParse + "'");
        }
        
        try {
            ExprParser parser = new ExprParser(equationForParse);
            rows[row].compiledExpr = parser.parseExpression();
            String err = parser.gotError();
            
            if (err != null) {
                CirSim.console("EquationTableElm row " + row + ": Parse error in '" + rows[row].equation + "' (normalized: '" + equationForParse + "'): " + err);
                rows[row].compiledExpr = null;
            } else if (DEBUG) {
                CirSim.console("[EquationTableElm." + tableName + "] Row " + row + " parsed successfully");
            }
        } catch (Exception e) {
            CirSim.console("EquationTableElm row " + row + ": Error parsing '" + rows[row].equation + "' (normalized: '" + equationForParse + "'): " + e.getMessage());
            rows[row].compiledExpr = null;
        }
    }

    /**
     * Normalize user-friendly equation forms into the row-value expression expected by ExprParser.
     *
     * Supported convenience forms for row output name X:
     * - X = expr        -> expr
     * - X - a = b       -> b + a
     *
     * This keeps scope intentionally narrow and does not attempt full symbolic algebra.
     */
    private String normalizeEquationSyntaxForRow(int row, String equation) {
        String outputName = rows[row].outputName == null ? "" : rows[row].outputName.trim();
        return normalizeEquationSyntaxForOutput(outputName, equation);
    }

    /**
     * Normalize user-friendly equation forms into expression form for a known output name.
     * Shared by parser flow and public stock helpers.
     */
    private static String normalizeEquationSyntaxForOutput(String outputName, String equation) {
        if (equation == null) {
            return "";
        }

        String trimmed = equation.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        int eqIndex = findSingleEqualsIndex(trimmed);
        if (eqIndex < 0) {
            return trimmed;
        }

        String left = trimmed.substring(0, eqIndex).trim();
        String right = trimmed.substring(eqIndex + 1).trim();
        if (left.isEmpty() || right.isEmpty()) {
            return trimmed;
        }

        String normalizedOutputName = outputName == null ? "" : outputName.trim();
        if (normalizedOutputName.isEmpty()) {
            return trimmed;
        }

        if (left.equals(normalizedOutputName)) {
            return right;
        }

        if (left.startsWith(normalizedOutputName)) {
            String remainder = left.substring(normalizedOutputName.length()).trim();
            if (remainder.startsWith("-")) {
                String offset = remainder.substring(1).trim();
                if (!offset.isEmpty()) {
                    return "(" + right + ") + (" + offset + ")";
                }
            }
        }

        return trimmed;
    }

    /** Returns true when expression root is integrate(...), i.e. pure stock form. */
    private static boolean isTopLevelStockExpression(Expr expr) {
        return expr != null && expr.type == Expr.E_INTEGRATE;
    }

    /**
     * Relaxed stock detection helper for cross-module use.
     * A row is stock-like when its normalized equation's top-level operator is integrate(...).
     */
    public static boolean isStockEquation(String outputName, String equation) {
        String normalized = normalizeEquationSyntaxForOutput(outputName, equation);
        if (normalized == null || normalized.trim().isEmpty()) {
            return false;
        }
        try {
            ExprParser parser = new ExprParser(normalized);
            Expr expr = parser.parseExpression();
            if (parser.gotError() != null || expr == null) {
                return false;
            }
            return isTopLevelStockExpression(expr);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Find an assignment '=' that is not part of ==, <=, >=, or !=.
     */
    private static int findSingleEqualsIndex(String expression) {
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c != '=') {
                continue;
            }

            char prev = (i > 0) ? expression.charAt(i - 1) : '\0';
            char next = (i + 1 < expression.length()) ? expression.charAt(i + 1) : '\0';
            if (prev == '=' || prev == '<' || prev == '>' || prev == '!' || next == '=') {
                continue;
            }
            return i;
        }
        return -1;
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
        return EquationTableSemantics.convergenceLimit(
            getConvergenceTolerance(),
            sim.getSubIterations(),
            rows[row].hasDiffExpr,
            rows[row].outputValue,
            rows[row].lastOutputValue
        );
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
    protected void setVoltageSource(int j, int vs) {
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
     * VOLTAGE_MODE uses voltage sources.
     */
    private void findLabeledNodes() {
        voltSourceCount = 0;
        
        int internalNodeIdx = 0;  // Index into internal nodes (nodes[] array)
        
        for (int row = 0; row < rowCount; row++) {
            rows[row].labeledNodeNumber = -1;
            rows[row].rowVoltSource = -1;
            
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

            // PARAM_MODE is computation-only (no nodes, no voltage source)
            if (rows[row].outputMode == RowOutputMode.PARAM_MODE) {
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
     * Stamp the element into the circuit matrix.
     * In MNA mode, stamps voltage sources for each row output using hybrid approach.
     * In pure computational mode, does nothing.
     */
    protected void stamp() {
        // Update row classifications
        updateRowClassifications();

        // Explicit pre-registration during analysis/stamp so expected keys are
        // available before the first startIteration()/doStep() pass.
        ensureNameRegistriesUpToDate();
        
        if (!isMnaMode()) {
            // Pure computational mode: no matrix stamping needed.
            return;
        }
        
        // Find or create nodes for each row
        findLabeledNodes();
        
        // MNA mode: stamp each row according to its output mode
        for (int row = 0; row < rowCount; row++) {
            if (isCommentRow(row)) continue;
            
            getHandler(rows[row].outputMode).stamp(row);
        }
    }
    
    /**
     * Called by CirSim after ALL elements have run stamp() and after the circuit-global
     * slot table (nameToSlot / circuitVariables[]) has been built by
     * buildCircuitVariableSlots().  Walks every compiled expression in this table and
     * converts E_NODE_REF leaf nodes to E_GSLOT nodes so that subsequent eval() calls
     * bypass the HashMap waterfall and read directly from circuitVariables[].
     */
    @Override
    protected void postStamp() {
        super.postStamp();
        resolveExprSlots();
    }

    /**
     * Walk all compiled expressions in this table and call resolveGSlot() on each,
     * converting E_NODE_REF nodes to E_GSLOT where the name has a slot assignment.
     */
    void resolveExprSlots() {
        CirSim sim = CirSim.getInstance();
        if (sim == null || sim.nameToSlot == null) return;
        for (int row = 0; row < rowCount; row++) {
            if (rows[row].compiledExpr != null)
                rows[row].compiledExpr.resolveGSlot(sim.nameToSlot);
            if (rows[row].compiledInitialExpr != null)
                rows[row].compiledInitialExpr.resolveGSlot(sim.nameToSlot);
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
     */
    @Override
    public void startIteration() {
        ensureNameRegistriesUpToDate();

        // No per-timestep precomputation required.
    }
    
    /**
     * Perform one simulation subiteration step.
     *
     * Called every subiteration of the nonlinear solver until convergence.
     * Iterates over all rows and dispatches to the appropriate handler:
     * <ol>
    *   <li>At {@code t=0} with an {@code initialEquation} — stamp placeholder then evaluate initial value.</li>
    *   <li>All other rows — evaluate via the mode handler ({@link RowModeHandler#evaluate}).</li>
     * </ol>
     * Convergence is checked inside each mode handler via {@link #checkEquationConvergence}.
     */
    protected void doStep() {
        if (DEBUG && sim.getSubIterations() == 0) {
            CirSim.console("[EquationTableElm." + tableName + "] doStep() at t=" + sim.getTime() + " mnaMode=" + isMnaMode());
        }
        
        for (int row = 0; row < rowCount; row++) {
            if (handleInitialValueRowAtT0(row)) {
                continue;
            }
            
            // Normal timestep: evaluate via mode handler
            getHandler(rows[row].outputMode).evaluate(row);
        }
    }

    /**
     * Handle a row that has an {@code initialEquation} during the {@code t=0} timestep.
     *
     * At {@code t=0}, up to three subiteration paths are taken (in order):
     * <ol>
     *   <li>First subiteration ({@code subIterations==0}): stamp a zero placeholder so
     *       the solver has a valid RHS to solve from.</li>
     *   <li>Subsequent subiterations, not yet applied: call {@link #evaluateInitialValue}
     *       to compute and stamp the true initial value and seed expression state.</li>
    *   <li>After initial value is applied: continue normal stepping.</li>
     * </ol>
     *
     * @param row Row index to check and handle.
     * @return {@code true} if this is an initial-value row at t=0 (caller should skip
     *         normal equation evaluation for this subiteration).
     */
    private boolean handleInitialValueRowAtT0(int row) {
        if (sim.getTime() != 0 || rows[row].compiledInitialExpr == null) {
            return false;
        }

        if (sim.getSubIterations() == 0 && !rows[row].initialValueApplied) {
            stampInitialPlaceholder(row);
            return true;
        }

        if (!rows[row].initialValueApplied) {
            evaluateInitialValue(row);
            return true;
        }

        // Keep initial seeds fixed for the full t=0 solve, matching sfcr-style
        // period-1 initialization semantics.
        restampAppliedInitialValue(row);
        return true;
    }

    /**
     * Stamp a zero-value placeholder into the MNA matrix for a row at the start of the
     * {@code t=0} timestep (first subiteration only).
     *
     * This gives the solver a valid, non-garbage RHS before the initial equation has been
     * evaluated.  It is replaced in the next subiteration by the true initial value.
     *
     * @param row Row index to stamp a placeholder for.
     */
    private void stampInitialPlaceholder(int row) {
        rows[row].outputValue = 0;
        if (isMnaMode() && rows[row].rowVoltSource >= 0) {
            int vn = voltSource + rows[row].rowVoltSource + sim.getCircuitAnalyzer().getNodeList().size();
            sim.stampRightSide(vn, 0);
        }
    }
    
    /**
     * Evaluate the initial value expression for a row at t=0.
     * @param row Row index
     */
    private void evaluateInitialValue(int row) {
        ExprState state = getOrCreateExprState(row);
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
        state.updateLastValues(initialValue);
        
        // Initialize integration state to start from initial value
        state.lastIntOutput = initialValue;
        
        // Handle based on output mode
        RowOutputMode mode = rows[row].outputMode;
        if (isMnaMode() && rows[row].rowVoltSource >= 0) {
            // VOLTAGE mode in MNA: stamp the initial value via stampRightSide
            int vn = voltSource + rows[row].rowVoltSource + sim.getCircuitAnalyzer().getNodeList().size();
            sim.stampRightSide(vn, initialValue);
        }
        
        // Register immediately for other elements/rows to use
        registerOutputValue(row, initialValue);
        registerInitialSeedValue(row, initialValue);
        if (mode == RowOutputMode.FLOW_MODE) {
            registerFlowValue(row, initialValue);
        }
        
        // Store initial value for re-stamping in subsequent iterations
        rows[row].outputValue = initialValue;
        
        if (DEBUG) {
            CirSim.console("[EquationTableElm." + tableName + "] Row " + row + " (" + rows[row].outputName + "): Applied initial value = " + initialValue);
        }
        
        rows[row].initialValueApplied = true;
    }

    /**
     * Re-stamp and re-register an already-applied initial value during t=0 subiterations.
     */
    private void restampAppliedInitialValue(int row) {
        double initialValue = rows[row].outputValue;

        if (isMnaMode() && rows[row].rowVoltSource >= 0) {
            int vn = voltSource + rows[row].rowVoltSource + sim.getCircuitAnalyzer().getNodeList().size();
            sim.stampRightSide(vn, initialValue);
        }

        registerOutputValue(row, initialValue);
        if (rows[row].outputMode == RowOutputMode.FLOW_MODE) {
            registerFlowValue(row, initialValue);
        }
    }

    /**
     * Publish explicit sfcr-style initial seed aliases for lag fallback.
     */
    private void registerInitialSeedValue(int row, double value) {
        if (!isValidOutputName(row)) {
            return;
        }

        String outputName = rows[row].outputName.trim();
        ComputedValues.setComputedValue(outputName + "_init", value, this);
        ComputedValues.setComputedValue(outputName + "init", value, this);
    }
    
    /**
     * Check if the equation value has converged from the previous iteration.
     * @param row Row index
     * @param equationValue New computed value
     */
    private void checkEquationConvergence(int row, double equationValue) {
        double convergeLimit = getConvergeLimit(row);
        double diff = Math.abs(equationValue - rows[row].lastOutputValue);
        if (EquationTableSemantics.shouldMarkUnconverged(
                equationValue,
                rows[row].lastOutputValue,
                convergeLimit,
                rows[row].hasDiffExpr,
                sim.getSubIterations())) {
            sim.setConverged(false);
            failedConvergenceRow = row;
            convergenceFailureInfo = buildConvergenceFailureInfo(row, diff, convergeLimit, equationValue);
            logConvergenceFailureIfThresholdExceeded();
        } else if (failedConvergenceRow == row) {
            // This row converged now, clear the failure
            failedConvergenceRow = -1;
            convergenceFailureInfo = null;
        }
    }

    /**
     * Return {@code true} when convergence checking should be skipped for this row and subiteration.
     *
     * For rows that use {@code diff()}, the numerical differentiation is not meaningful until
     * the solver has run at least 5 subiterations (the first few passes have transient state).
     * Skipping early checks prevents premature divergence flags.
     *
     * @param row Row index.
     * @return {@code true} if convergence checking should be bypassed.
     */
    private boolean shouldSkipConvergenceCheck(int row) {
        return EquationTableSemantics.shouldSkipConvergenceCheck(rows[row].hasDiffExpr, sim.getSubIterations());
    }

    /**
     * Build a human-readable description of a convergence failure for debug display.
     *
     * Shows the output name, the difference between old and new values, the convergence
     * limit, and both old and new values in short-unit form.
     *
     * @param row           Row that failed to converge.
     * @param diff          Absolute difference between the new and previous values.
     * @param convergeLimit Convergence threshold that was exceeded.
     * @param equationValue New (not-yet-converged) computed value.
     * @return Formatted diagnostics string stored in {@link #convergenceFailureInfo}.
     */
    private String buildConvergenceFailureInfo(int row, double diff, double convergeLimit, double equationValue) {
        return rows[row].outputName + ": diff=" + getShortUnitText(diff, "")
            + ", limit=" + getShortUnitText(convergeLimit, "")
            + ", last=" + getShortUnitText(rows[row].lastOutputValue, "")
            + ", new=" + getShortUnitText(equationValue, "");
    }

    /**
     * Log the current convergence failure to the browser console if the subiteration count
     * has exceeded {@link CirSim#convergenceCheckThreshold}.
     *
     * The threshold prevents log spam during normal solving; messages only appear when
     * the solver is genuinely struggling (too many iterations).
     */
    private void logConvergenceFailureIfThresholdExceeded() {
        if (sim.getSubIterations() > sim.convergenceCheckThreshold) {
            CirSim.console("[" + tableName + "] t=" + sim.getTime() + " dt=" + sim.getTimeStep()
                    + " Convergence failure: " + convergenceFailureInfo);
        }
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
            // Skip FLOW_MODE rows: their outputName is the source node name
            // (e.g. "S1" from "S1->S2"), and registering the flow magnitude
            // would clobber the main node value in ComputedValues.
            if (rows[row].outputMode != RowOutputMode.FLOW_MODE && isValidOutputName(row)) {
                String name = rows[row].outputName.trim();
                ComputedValues.setComputedValue(name, rows[row].outputValue, this);
                ComputedValues.markComputedThisStep(name);
                if (DEBUG) {
                    CirSim.console("  " + name + " = " + rows[row].outputValue);
                }
            }
            if (rows[row].outputMode == RowOutputMode.FLOW_MODE && isValidOutputName(row)) {
                // Re-publish using the same signed dual-endpoint convention as doStep().
                registerFlowValue(row, rows[row].outputValue);

                String sourceKey = getFlowComputedKeyForName(rows[row].outputName.trim());
                if (sourceKey != null) {
                    ComputedValues.markComputedThisStep(sourceKey);
                }

                String targetName = rows[row].targetNodeName;
                if (targetName != null) {
                    String trimmedTarget = targetName.trim();
                    if (!trimmedTarget.isEmpty() && !trimmedTarget.equalsIgnoreCase("gnd")) {
                        String targetKey = getFlowComputedKeyForName(trimmedTarget);
                        if (targetKey != null) {
                            ComputedValues.markComputedThisStep(targetKey);
                        }
                    }
                }
            }
            
            // Commit any pending integration
            if (rows[row].exprState != null) {
                rows[row].exprState.commitIntegration(sim.getTimeStep());
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
            if (rows[row].outputMode == RowOutputMode.FLOW_MODE) {
                registerFlowValue(row, rows[row].outputValue);
            }
        }

        // Keep PARAM registry accurate after reset/edit-mode changes.
        // Clear internal tracking sets first so that refreshXxx() always re-registers
        // everything from scratch.  This is necessary because resetAction() calls
        // ComputedValues.clearComputedValues() which wipes parameterNameRefCounts and
        // computedNameRefCounts before calling reset() on each element.  Without this
        // clear, refreshParameterNameRegistry() sees all names still in
        // registeredParamNames and skips the registerParameterName() calls, leaving the
        // registry empty and breaking buildCircuitVariableSlots() → E_GSLOT conversion.
        if (registeredParamNames != null) registeredParamNames.clear();
        if (registeredComputedNames != null) registeredComputedNames.clear();
        markNameRegistriesDirty();
        ensureNameRegistriesUpToDate();
    }

    @Override
    protected void delete() {
        unregisterNameRegistries();
        super.delete();
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
            // New: output mode and target node (target token kept empty for format compat)
            int modeOrdinal = 0;
            if (rows[row].outputMode == RowOutputMode.FLOW_MODE) {
                modeOrdinal = 1;
            } else if (rows[row].outputMode == RowOutputMode.PARAM_MODE) {
                // Keep old on-disk numbering for backward compatibility.
                modeOrdinal = 3;
            }
            sb.append(" ").append(modeOrdinal);  // 0=VOLTAGE, 1=FLOW, 2=legacy STOCK, 3=PARAM
            sb.append(" ").append(CustomLogicModel.escape(""));  // target now in combined name
            sb.append(" ").append(1.0); // legacy capacitance token retained for compatibility
            sb.append(" ").append(rows[row].shuntResistance);
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
    protected void drawPosts(Graphics g) {
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
    protected void draw(Graphics g) {
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
                sim.getPreferencesManager().setGrid();
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
    protected void getInfo(String arr[]) {
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
        
        String classification = getRowClassification(hoveredRow);
        String classDesc;
        if ("comment".equals(classification)) {
            classDesc = "# comment";
        } else if ("cyclic".equals(classification)) {
            classDesc = "⟳ cyclic";
        } else {
            classDesc = "other";
        }
        
        arr[2] = getFlowDisplayName(hoveredRow) + " = " + getUnitText(rows[hoveredRow].outputValue, "") + " [" + classDesc + "]";
        String hintExpandedEquation = getHintExpandedEquationForDisplay(hoveredRow);
        arr[3] = "Equation: " + hintExpandedEquation;
        
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
            arr[8] = "Convergence Tol: " + Double.toString(getConvergenceTolerance());
            
            if (rows[hoveredRow].compiledExpr == null) {
                arr[9] = "\u26A0 Equation parse error";
            } else if (isAdjustableRow(hoveredRow)) {
                arr[9] = "scroll to adjust value";
            }
        } else {
            arr[4] = "Initial (t=0): " + (initEq != null && !initEq.trim().isEmpty() ? initEq : "(none)");
        
            arr[5] = "Slider: " + rows[hoveredRow].sliderVarName + " = " + getShortUnitText(rows[hoveredRow].sliderValue, "");
            arr[6] = "Output: " + getUnitText(rows[hoveredRow].outputValue, "");
            arr[7] = "Convergence Tol: " + Double.toString(getConvergenceTolerance());
        
            if (rows[hoveredRow].compiledExpr == null) {
                arr[8] = "\u26A0 Equation parse error";
            } else if (isAdjustableRow(hoveredRow)) {
                arr[8] = "scroll to adjust value";
            }
        }
    }

    /**
     * Build a display-only equation string with variable tokens replaced by their hint text
     * when available in {@link HintRegistry}. Function names (tokens followed by '(') are
     * intentionally left unchanged.
     */
    public String getHintExpandedEquationForDisplay(int row) {
        if (row < 0 || row >= rowCount) return "";

        String equationText = rows[row].equation;
        if (equationText == null || equationText.isEmpty()) return "";

        String trimmed = equationText.trim();
        int eqIndex = findSingleEqualsIndex(trimmed);
        if (eqIndex >= 0) {
            String left = trimmed.substring(0, eqIndex).trim();
            String right = trimmed.substring(eqIndex + 1).trim();
            if (!left.isEmpty() && !right.isEmpty()) {
                return expandHintsInExpression(left) + " = " + expandHintsInExpression(right);
            }
        }

        String expanded = expandHintsInExpression(equationText);

        String lhsName = rows[row].outputName == null ? "" : rows[row].outputName.trim();
        String lhsHint = HintRegistry.getHint(lhsName);
        String lhsDisplay = (lhsHint != null && !lhsHint.trim().isEmpty()) ? lhsHint.trim() : lhsName;
        if (lhsDisplay.isEmpty()) {
            return expanded;
        }
        return lhsDisplay + " = " + expanded;
    }

    private String expandHintsInExpression(String expressionText) {
        if (expressionText == null || expressionText.isEmpty()) {
            return "";
        }

        StringBuilder expanded = new StringBuilder(expressionText.length() + 16);
        int i = 0;
        while (i < expressionText.length()) {
            char c = expressionText.charAt(i);
            if (c == '\\' && i + 1 < expressionText.length() && isIdentifierStartChar(expressionText.charAt(i + 1))) {
                int start = i;
                i += 2;
                while (i < expressionText.length() && isIdentifierPartChar(expressionText.charAt(i))) {
                    i++;
                }

                String token = expressionText.substring(start, i);

                int next = i;
                while (next < expressionText.length() && Character.isWhitespace(expressionText.charAt(next))) {
                    next++;
                }
                boolean functionLike = next < expressionText.length() && expressionText.charAt(next) == '(';
                if (functionLike) {
                    expanded.append(token);
                    continue;
                }

                String hint = HintRegistry.getHint(token);
                if (hint != null && !hint.trim().isEmpty()) {
                    expanded.append(hint.trim());
                } else {
                    expanded.append(token);
                }
                continue;
            }

            if (!isIdentifierStartChar(c)) {
                expanded.append(c);
                i++;
                continue;
            }

            int start = i;
            i++;
            while (i < expressionText.length() && isIdentifierPartChar(expressionText.charAt(i))) {
                i++;
            }

            String token = expressionText.substring(start, i);

            int next = i;
            while (next < expressionText.length() && Character.isWhitespace(expressionText.charAt(next))) {
                next++;
            }
            boolean functionLike = next < expressionText.length() && expressionText.charAt(next) == '(';
            if (functionLike) {
                expanded.append(token);
                continue;
            }

            String hint = HintRegistry.getHint(token);
            if (hint != null && !hint.trim().isEmpty()) {
                expanded.append(hint.trim());
            } else {
                expanded.append(token);
            }
        }

        return expanded.toString();
    }

    private static boolean isIdentifierStartChar(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierPartChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '$';
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

    /** Get global base convergence tolerance used by equation tables. */
    public double getConvergenceTolerance() {
        if (sim != null && sim.getEquationTableConvergenceTolerance() > 0) {
            return sim.getEquationTableConvergenceTolerance();
        }
        return DEFAULT_CONVERGENCE_TOLERANCE;
    }

    /** Set global base convergence tolerance used by equation tables. Must be positive. */
    public void setConvergenceTolerance(double tolerance) {
        if (sim != null) {
            sim.setEquationTableConvergenceTolerance((tolerance > 0) ? tolerance : DEFAULT_CONVERGENCE_TOLERANCE);
        }
    }
    
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
            sim.getMouseInputHandler().setCursorStyle("cursorAdjust");
        } else {
            // Reset cursor to default based on mouse mode
            sim.getMouseInputHandler().setCursorStyle(sim.getMouseMode() == CirSim.MODE_ADD_ELM ? "cursorCross" : "cursorPointer");
        }
    }
    
    /** Get output value for a row */
    public double getOutputValue(int row) {
        return (row >= 0 && row < MAX_ROWS) ? rows[row].outputValue : 0.0;
    }
    
    /** 
     * Get display value for a row.
     */
    public double getDisplayValue(int row) {
        if (row < 0 || row >= MAX_ROWS) return 0.0;

        // For non-flow rows, prefer the published ComputedValues value so UI reflects
        // Scenario/Action overrides and any other registry-level adjustments.
        if (rows[row].outputMode != RowOutputMode.FLOW_MODE && isValidOutputName(row)) {
            Double computed = ComputedValues.getComputedValue(rows[row].outputName.trim());
            if (computed != null) {
                return computed.doubleValue();
            }
        }

        return rows[row].outputValue;
    }
    
    /** Get the number of active rows */
    public int getRowCount() { return rowCount; }
    
    /** Get row classification as string */
    public String getRowClassification(int row) {
        if (row < 0 || row >= MAX_ROWS) return "unknown";
        updateRowClassifications();
        if (isCommentRow(row)) return "comment";
        return rows[row].isCyclic ? "cyclic" : "other";
    }

    /** True when row equation is stock-like (top-level integrate(...)). */
    public boolean isStockRow(int row) {
        if (row < 0 || row >= MAX_ROWS || isCommentRow(row)) {
            return false;
        }
        updateRowClassifications();
        return rows[row].isStock;
    }

    /** Get per-row Newton Jacobian debug status for VOLTAGE_MODE MNA path. */
    public String getNewtonJacobianDebugStatus(int row) {
        if (row < 0 || row >= MAX_ROWS) return "invalid row";
        return rows[row].lastNewtonJacobianStatus;
    }

    /** True when Newton Jacobian stamping was applied on the most recent row evaluation. */
    public boolean wasNewtonJacobianApplied(int row) {
        if (row < 0 || row >= MAX_ROWS) return false;
        return rows[row].lastNewtonJacobianApplied;
    }
    
    /** Set the number of active rows (1 to MAX_ROWS) */
    public void setRowCount(int count) { 
        if (count >= 1 && count <= MAX_ROWS) {
            rowCount = count;
            markNameRegistriesDirty();
        }
    }
    
    /** Get output name for a row (source node only, for internal use) */
    public String getOutputName(int row) {
        return (row >= 0 && row < MAX_ROWS) ? rows[row].outputName : "";
    }
    
    /**
     * Get the display output name for a row (combined "source->target" format).
    * For FLOW mode with an explicit non-ground target, returns "source->target".
     * For VOLTAGE mode, no target, or target="gnd", returns just the source name.
     * Always uses ASCII "->" separator for dump compatibility.
     * Ground targets are omitted since they are the implicit default.
     */
    public String getDisplayOutputName(int row) {
        if (row < 0 || row >= MAX_ROWS) return "";
        String name = rows[row].outputName;
        if (isCommentRowName(name)) {
            return name;
        }
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
    * Get the UI display output name for a row.
    * For FLOW mode with an explicit non-ground target, returns "source\u2192target" (→).
     * Single-node names (no target, or target="gnd") display as just the node name,
     * since ground reference is the implicit default.
     * Use this for rendering, tooltips, and user-facing display.
     * For serialization (dump), use getDisplayOutputName() instead.
     */
    public String getUIDisplayOutputName(int row) {
        if (row < 0 || row >= MAX_ROWS) return "";
        if (isCommentRow(row)) {
            return rows[row].outputName;
        }
        String name = normalizeArrows(rows[row].outputName);
        String target = rows[row].targetNodeName;
        if (target != null && !target.isEmpty() && !target.trim().equalsIgnoreCase("gnd")) {
            target = normalizeArrows(target);
            return name + "\u2192" + target;
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
            if (isCommentRowName(name)) {
                String trimmed = name == null ? "" : name.trim();
                if (!trimmed.startsWith("#")) {
                    trimmed = "# " + trimmed;
                }
                rows[row].outputName = trimmed;
                rows[row].targetNodeName = "";
                markNameRegistriesDirty();
                return;
            }
            String[] parts = parseCombinedName(name);
            rows[row].outputName = parts[0];
            if (!parts[1].isEmpty()) {
                rows[row].targetNodeName = parts[1];
            } else {
                // No "->" in name: clear target (combined format with no target)
                rows[row].targetNodeName = "";
            }
            markNameRegistriesDirty();
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
            markNameRegistriesDirty();
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

    /** True if this row is a non-simulating comment row. */
    public boolean isCommentRow(int row) {
        return row >= 0 && row < MAX_ROWS && isCommentRowName(rows[row].outputName);
    }

    /** Returns comment text without the leading '#', or empty string for non-comment rows. */
    public String getCommentText(int row) {
        if (!isCommentRow(row)) {
            return "";
        }
        String text = rows[row].outputName == null ? "" : rows[row].outputName.trim();
        if (text.startsWith("#")) {
            text = text.substring(1).trim();
        }
        return text;
    }

    /** Set output mode for a row */
    public void setOutputMode(int row, RowOutputMode mode) {
        if (row >= 0 && row < MAX_ROWS) {
            rows[row].outputMode = mode;
            markNameRegistriesDirty();
        }
    }
    
    /** Get target node name for a row (used in FLOW_MODE). */
    public String getTargetNodeName(int row) {
        return (row >= 0 && row < MAX_ROWS && rows[row].targetNodeName != null) ? rows[row].targetNodeName : "";
    }
    
    /** Set target node name for a row */
    public void setTargetNodeName(int row, String name) {
        if (row >= 0 && row < MAX_ROWS) {
            rows[row].targetNodeName = (name != null && !name.isEmpty()) ? name : null;
            markNameRegistriesDirty();
        }
    }
    
    /** Get FLOW shunt resistance for a row. */
    public double getFlowShuntResistance(int row) {
        return (row >= 0 && row < MAX_ROWS) ? rows[row].shuntResistance : DEFAULT_FLOW_SHUNT_RESISTANCE;
    }

    /** Get default FLOW shunt resistance used for initialization/fallback. */
    public static double getDefaultFlowShuntResistance() {
        return DEFAULT_FLOW_SHUNT_RESISTANCE;
    }

    /** Set FLOW shunt resistance for a row. */
    public void setFlowShuntResistance(int row, double shuntR) {
        if (row >= 0 && row < MAX_ROWS) {
            rows[row].shuntResistance = (shuntR > 0) ? shuntR : DEFAULT_FLOW_SHUNT_RESISTANCE;
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
                CirSimDialogCoordinator.setDialogShowing(dialog);
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
        sim.getUndoRedoManager().pushUndo();
        
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
