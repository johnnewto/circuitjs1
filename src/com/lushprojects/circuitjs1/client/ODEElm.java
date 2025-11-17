/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * ODEElm - Simple ODE Calculator with Integration
 * 
 * This element calculates ordinary differential equations (ODEs) by:
 * 1. Evaluating a user-defined equation that can reference labeled nodes
 * 2. Integrating the result over time using numerical integration
 * 
 * Features:
 * - Single output pin providing the integrated value
 * - No input pins - equation references labeled nodes (like TableElm)
 * - User-editable equation string (e.g., "node1 + node2", "sin(t)", etc.)
 * - Initial value parameter for integration
 * - Compact visual representation
 * 
 * Integration equation: y[n+1] = y[n] + dt * f(t, labeled_nodes)
 * Where f(t, labeled_nodes) is the user's equation
 * 
 * Example uses:
 * - Equation: "rate" with labeled node "rate" = 5 -> integrates 5/sec
 * - Equation: "price - cost" -> integrates profit over time
 * - Equation: "-decay * stock" -> exponential decay
 * - Equation: "Predator_Births-(Predator*a)" with parameter 'a' -> adjustable death rate
 */
class ODEElm extends ChipElm {
    private String elementName = "ODE";     // User-defined name for this element
    private String equationString = "1";    // User's equation string
    private Expr compiledExpr;              // Compiled expression
    private ExprState exprState;            // Expression evaluation state
    private double integratedValue;         // Current integration value
    private double initialValue = 0.0;      // Initial condition
    private double lastEquationValue = 0.0; // Last evaluated equation value (for convergence)
    
    // Parameters a-h that can be referenced in equations (right-click to adjust)
    private static final int MAX_PARAMETERS = 8;
    private int numParameters = 1;          // Number of active parameters (1-8)
    private double[] parameters = new double[MAX_PARAMETERS];  // Parameter values
    private boolean[] showPercentage = new boolean[MAX_PARAMETERS]; // Show each parameter as percentage
    private static final String[] PARAM_NAMES = {"a", "b", "c", "d", "e", "f", "g", "h"};
    
    static final int FLAG_SHOW_PERCENTAGE_BASE = 2;  // Flags for percentage display (bits 1-8, shifted to avoid FLAG_SMALL at bit 0)
    
    // Constructor for menu creation
    public ODEElm(int xx, int yy) {
        super(xx, yy);
        noDiagonal = true;
        equationString = "1";
        initialValue = 0.0;
        numParameters = 1;
        // Initialize default parameter values
        for (int i = 0; i < MAX_PARAMETERS; i++) {
            parameters[i] = 0.5;
            showPercentage[i] = false;
        }
        setupPins();
        parseEquation();
        initIntegration();
    }
    
    // Constructor for file loading
    public ODEElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        noDiagonal = true;
        
        // Initialize arrays
        for (int i = 0; i < MAX_PARAMETERS; i++) {
            parameters[i] = 0.5;
            showPercentage[i] = false;
        }
        
        // Parse element name (must be escaped)
        if (st.hasMoreTokens()) {
            elementName = CustomLogicModel.unescape(st.nextToken());
        } else {
            elementName = "ODE";
        }
        
        // Parse equation string (must be escaped)
        if (st.hasMoreTokens()) {
            equationString = CustomLogicModel.unescape(st.nextToken());
        } else {
            equationString = "1";
        }
        
        // Parse initial value
        if (st.hasMoreTokens()) {
            try {
                initialValue = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                initialValue = 0.0;
            }
        }
        
        // Parse number of parameters
        if (st.hasMoreTokens()) {
            try {
                numParameters = Integer.parseInt(st.nextToken());
                if (numParameters < 1) numParameters = 1;
                if (numParameters > MAX_PARAMETERS) numParameters = MAX_PARAMETERS;
            } catch (Exception e) {
                numParameters = 1;
            }
        }
        
        // Parse parameter values
        for (int i = 0; i < numParameters; i++) {
            if (st.hasMoreTokens()) {
                try {
                    parameters[i] = Double.parseDouble(st.nextToken());
                } catch (Exception e) {
                    parameters[i] = 0.5;
                }
            }
        }
        
        // Parse percentage flags from flags field
        for (int i = 0; i < MAX_PARAMETERS; i++) {
            showPercentage[i] = (f & (FLAG_SHOW_PERCENTAGE_BASE << i)) != 0;
        }
        
        setupPins();
        parseEquation();
        initIntegration();
    }
    
    void setupPins() {
        sizeX = 3;  // Wider
        sizeY = 2;  // Shorter
        pins = new Pin[1]; // Single output pin
        pins[0] = new Pin(0, SIDE_E, "");
        pins[0].output = true;
        allocNodes();
    }
    
    String getChipName() { return "ODE"; }
    
    int getDumpType() { return 261; } // Unique dump type
    
    int getPostCount() { return 1; } // Single output post
    
    int getVoltageSourceCount() { return 1; } // One voltage source for output
    
    boolean hasCurrentOutput() { return false; }
    
    private void initIntegration() {
        exprState = new ExprState(MAX_PARAMETERS); // Support up to 8 variables (a-h)
        exprState.lastOutput = initialValue;
        integratedValue = initialValue;
    }
    
    private void parseEquation() {
        try {
            ExprParser parser = new ExprParser(equationString);
            compiledExpr = parser.parseExpression();
            String err = parser.gotError();
            if (err != null) {
                CirSim.console("ODEElm: Parse error in equation '" + equationString + "': " + err);
                compiledExpr = null;
            }
        } catch (Exception e) {
            CirSim.console("ODEElm: Error parsing equation '" + equationString + "': " + e.getMessage());
            compiledExpr = null;
        }
    }
    
    // Get convergence limit (similar to GodlyTableElm)
    double getConvergeLimit() {
        double relativeTolerance;
        if (sim.subIterations < 10)
            relativeTolerance = 0.001;  // 0.1% for early iterations
        else if (sim.subIterations < 100)
            relativeTolerance = 0.01;   // 1% for mid iterations
        else
            relativeTolerance = 0.1;    // 10% for late iterations
        
        // Scale by magnitude
        double maxMagnitude = Math.max(1.0, Math.abs(integratedValue));
        maxMagnitude = Math.max(maxMagnitude, Math.abs(lastEquationValue));
        
        return maxMagnitude * relativeTolerance;
    }
    
    void stamp() {
        int vn = pins[0].voltSource + sim.nodeList.size();
        sim.stampNonLinear(vn);
        sim.stampVoltageSource(0, nodes[0], pins[0].voltSource);
    }
    
    void doStep() {
        // On first timestep, set initial value
        if (sim.timeStepCount == 0) {
            exprState.lastOutput = initialValue;
            integratedValue = initialValue;
        }
        
        int vn = pins[0].voltSource + sim.nodeList.size();
        
        if (compiledExpr != null) {
            // Set all parameter values in expression (a, b, c, d, e, f, g, h)
            for (int i = 0; i < MAX_PARAMETERS; i++) {
                exprState.values[i] = parameters[i];
            }
            
            // Evaluate equation to get derivative f(t, labeled_nodes, a, b, c...)
            exprState.t = sim.t;
            double equationValue = compiledExpr.eval(exprState);
            
            // Check equation convergence
            double convergeLimit = getConvergeLimit();
            if (Math.abs(equationValue - lastEquationValue) > convergeLimit) {
                sim.converged = false;
            }
            lastEquationValue = equationValue;
            
            // Perform integration: y[n+1] = y[n] + dt * f(t)
            integratedValue = exprState.lastOutput + sim.timeStep * equationValue;
            
            // Check output voltage convergence
            double outputVoltage = volts[0];
            double voltageDiff = Math.abs(outputVoltage - integratedValue);
            double threshold = Math.max(Math.abs(integratedValue) * 0.01, 1e-6);
            if (voltageDiff > threshold && sim.subIterations < 100) {
                sim.converged = false;
            }
            
            // Stamp the right side with the integrated value
            sim.stampRightSide(vn, integratedValue);
        }
    }
    
    void stepFinished() {
        // Update integration state for next timestep
        if (exprState != null) {
            exprState.lastOutput = integratedValue;
        }
    }
    
    @Override
    public void reset() {
        super.reset();
        // Reset integration to initial condition
        if (exprState != null) {
            exprState.reset();
            exprState.lastOutput = initialValue;
        }
        integratedValue = initialValue;
        lastEquationValue = 0.0;
    }
    
    void setCurrent(int vn, double c) {
        if (pins[0].voltSource == vn) {
            pins[0].current = c;
        }
    }
    
    @Override
    public String dump() {
        StringBuilder sb = new StringBuilder();
        
        // Update flags before dumping
        // Preserve FLAG_SMALL (bit 0) from ChipElm, clear percentage bits (1-8)
        int newFlags = flags & ~0x1FE; // Clear bits 1-8 (percentage flags), keep bit 0 (FLAG_SMALL)
        for (int i = 0; i < MAX_PARAMETERS; i++) {
            if (showPercentage[i]) {
                newFlags |= (FLAG_SHOW_PERCENTAGE_BASE << i);
            }
        }
        flags = newFlags;
        
        sb.append(super.dump());
        sb.append(" ").append(CustomLogicModel.escape(elementName));
        sb.append(" ").append(CustomLogicModel.escape(equationString));
        sb.append(" ").append(initialValue);
        sb.append(" ").append(numParameters);
        
        // Dump all parameter values
        for (int i = 0; i < numParameters; i++) {
            sb.append(" ").append(parameters[i]);
        }
        
        return sb.toString();
    }
    
    void draw(Graphics g) {
        drawChip(g);
        
        // Draw integral symbol with initial value as subscript in box
        int mid_x = (rectPointsX[0] + rectPointsX[1] + rectPointsX[2] + rectPointsX[3]) / 4;
        int mid_y = (rectPointsY[0] + rectPointsY[1] + rectPointsY[2] + rectPointsY[3]) / 4;
        
        boolean selected = needsHighlight();
        
        // Draw integral symbol
        Font mainFont = new Font("SansSerif", selected ? Font.BOLD : 0, 18);
        g.setFont(mainFont);
        g.setColor(selected ? selectColor : whiteColor);
        drawCenteredText(g, "∫", mid_x - 8, mid_y, true);
        g.restore();
        
        // Draw initial value as subscript (smaller, lower, to the right)
        Font subscriptFont = new Font("SansSerif", 0, 10);
        g.setFont(subscriptFont);
        g.setColor(selected ? selectColor : whiteColor);
        String initStr = getShortUnitText(initialValue, "");
        drawCenteredText(g, initStr, mid_x + 8, mid_y + 6, true);
        g.restore();
        
        // Draw full equation below the box with all parameters substituted
        String displayEquation = equationString;
        for (int i = 0; i < numParameters; i++) {
            String paramValueStr;
            if (showPercentage[i]) {
                double percentValue = parameters[i] * 100;
                // Manual formatting for percentage (GWT doesn't support String.format)
                int intPart = (int) percentValue;
                int decimalPart = (int) ((percentValue - intPart) * 10);
                paramValueStr = intPart + "." + decimalPart + "%";
            } else {
                paramValueStr = getShortUnitText(parameters[i], "");
            }
            // Replace parameter name (a, b, c, etc.) with its value
            displayEquation = displayEquation.replaceAll("\\b" + PARAM_NAMES[i] + "\\b", paramValueStr);
        }
        
        int bottom_y = Math.max(rectPointsY[0], Math.max(rectPointsY[1], 
                                Math.max(rectPointsY[2], rectPointsY[3])));
        Font smallFont = new Font("SansSerif", 0, 10);
        g.setFont(smallFont);
        g.setColor(selected ? selectColor : whiteColor);
        drawCenteredText(g, "d/dt=" + displayEquation, mid_x, bottom_y + 12, true);
        g.restore();
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Name", 0, -1, -1);
            ei.text = elementName;
            ei.disallowSliders();
            
            // Build completion list for element names
            java.util.List<String> completions = new java.util.ArrayList<String>();
            
            // Add stock variables (useful for naming ODEs after what they track)
            java.util.Set<String> stockNames = StockFlowRegistry.getAllStockNames();
            if (stockNames != null && !stockNames.isEmpty()) {
                for (String stockName : stockNames) {
                    completions.add(stockName);
                }
            }
            
            // Add other ODE element names in the circuit
            if (sim != null && sim.elmList != null) {
                for (int i = 0; i < sim.elmList.size(); i++) {
                    CircuitElm ce = sim.getElm(i);
                    if (ce instanceof ODEElm && ce != this) {
                        ODEElm ode = (ODEElm) ce;
                        if (ode.elementName != null && !ode.elementName.isEmpty()) {
                            completions.add(ode.elementName);
                        }
                    }
                }
            }
            
            // Add labeled node names
            String[] labeledNodes = LabeledNodeElm.getSortedLabeledNodeNames();
            if (labeledNodes != null && labeledNodes.length > 0) {
                for (String nodeName : labeledNodes) {
                    completions.add(nodeName);
                }
            }
            
            // Attach completion list for tab completion
            ei.completionList = completions;
            
            return ei;
        }
        if (n == 1) {
            EditInfo ei = new EditInfo("Equation (d/dt)", 0, -1, -1);
            ei.text = equationString;
            ei.disallowSliders();
            
            // Build completion list for bash-style autocompletion
            java.util.List<String> completions = new java.util.ArrayList<String>();
            
            // Add stock variables from TableElm cell equations
            java.util.Set<String> stockNames = StockFlowRegistry.getAllStockNames();
            if (stockNames != null && !stockNames.isEmpty()) {
                for (String stockName : stockNames) {
                    completions.add(stockName);
                }
            }
            
            // Add labeled node names
            String[] labeledNodes = LabeledNodeElm.getSortedLabeledNodeNames();
            if (labeledNodes != null && labeledNodes.length > 0) {
                for (String nodeName : labeledNodes) {
                    completions.add(nodeName);
                }
            }
            
            // Add variables used in cell equations
            java.util.Set<String> cellVariables = StockFlowRegistry.getAllCellEquationVariables();
            if (cellVariables != null && !cellVariables.isEmpty()) {
                for (String varName : cellVariables) {
                    completions.add(varName);
                }
            }
            
            // Add other ODE element names in the circuit
            if (sim != null && sim.elmList != null) {
                for (int i = 0; i < sim.elmList.size(); i++) {
                    CircuitElm ce = sim.getElm(i);
                    if (ce instanceof ODEElm && ce != this) {
                        ODEElm ode = (ODEElm) ce;
                        if (ode.elementName != null && !ode.elementName.isEmpty()) {
                            completions.add(ode.elementName);
                        }
                    }
                }
            }
            
            // Add parameter names for this ODE element
            for (int i = 0; i < numParameters; i++) {
                completions.add(PARAM_NAMES[i]);
            }
            
            // Add mathematical functions
            completions.add("sin");
            completions.add("cos");
            completions.add("tan");
            completions.add("exp");
            completions.add("log");
            completions.add("sqrt");
            completions.add("abs");
            completions.add("min");
            completions.add("max");
            completions.add("pow");
            completions.add("atan2");
            completions.add("floor");
            completions.add("ceil");
            
            // Add common constants
            completions.add("pi");
            completions.add("e");
            completions.add("t");  // time variable
            
            // Attach completion list for tab completion
            ei.completionList = completions;
            
            return ei;
        }
        if (n == 2) {
            EditInfo ei = new EditInfo("Initial Value y(0)", initialValue);
            return ei;
        }
        if (n == 3) {
            EditInfo ei = new EditInfo("Number of Parameters", "");
            ei.choice = new Choice();
            for (int i = 1; i <= MAX_PARAMETERS; i++) {
                ei.choice.add(String.valueOf(i));
            }
            ei.choice.select(numParameters - 1);
            return ei;
        }
        
        // Parameter fields with inline checkboxes: 4, 5, 6, 7, 8, 9, 10, 11
        int paramIndex = n - 4;
        
        if (paramIndex >= 0 && paramIndex < numParameters) {
            EditInfo ei = new EditInfo(elementName + "_'" + PARAM_NAMES[paramIndex] + "'", parameters[paramIndex]);
            // Add inline checkbox for percentage display
            ei.checkboxInline = new Checkbox("Show as Percentage", showPercentage[paramIndex]);
            return ei;
        }
        
        return null;
    }
    
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            elementName = ei.textf.getText();
        }
        if (n == 1) {
            equationString = ei.textf.getText();
            parseEquation();
        }
        if (n == 2) {
            initialValue = ei.value;
            // Reset integration to new initial value
            if (exprState != null) {
                exprState.lastOutput = initialValue;
            }
            integratedValue = initialValue;
        }
        if (n == 3) {
            int newNumParams = ei.choice.getSelectedIndex() + 1;
            if (newNumParams != numParameters) {
                numParameters = newNumParams;
                ei.newDialog = true;  // Refresh dialog to show new parameter fields
            }
        }
        
        // Parameter fields: 4, 5, 6, 7, 8, 9, 10, 11 (one per parameter)
        int paramIndex = n - 4;
        
        if (paramIndex >= 0 && paramIndex < MAX_PARAMETERS) {
            parameters[paramIndex] = ei.value;
            // Update percentage checkbox state if inline checkbox exists
            if (ei.checkboxInline != null) {
                showPercentage[paramIndex] = ei.checkboxInline.getState();
            }
        }
    }
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "ODE Integrator";
        arr[1] = "Equation: d/dt = " + equationString;
        arr[2] = "Initial Value: " + getVoltageText(initialValue);
        
        // Show all active parameters
        int idx = 3;
        for (int i = 0; i < numParameters && idx < arr.length; i++) {
            arr[idx++] = "Parameter '" + PARAM_NAMES[i] + "': " + getVoltageText(parameters[i]);
        }
        
        if (idx < arr.length)
            arr[idx++] = "Current Output: " + getVoltageText(integratedValue);
        if (idx < arr.length)
            arr[idx] = "Time: " + getUnitText(sim.t, "s");
    }
    
    // Custom formatting for parameter sliders
    public String getSliderUnitText(int n, EditInfo ei, double value) {
        // Fields 0-3 are name, equation, initial value, and number of parameters
        if (n <= 3)
            return null;
        
        // Parameter fields: 4, 5, 6, 7, 8, 9, 10, 11 (one per parameter)
        int paramIndex = n - 4;
        
        // Only format parameter value fields
        if (paramIndex >= numParameters)
            return null;
        
        if (showPercentage[paramIndex]) {
            double percentValue = value * 100;
            // Manual formatting for percentage (GWT doesn't support String.format)
            int intPart = (int) percentValue;
            int decimalPart = (int) ((percentValue - intPart) * 10);
            return intPart + "." + decimalPart + "%";
        } else {
            return getShortUnitText(value, "");
        }
    }
}
