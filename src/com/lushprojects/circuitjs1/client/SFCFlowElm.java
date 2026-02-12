/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * SFCFlowElm - Stock-Flow Consistent Transaction Flow Element
 * 
 * Represents an economic flow (transaction) between two sector nodes
 * using current as the fundamental flow variable.
 * 
 * <h3>Physical Analogy:</h3>
 * This is a current source between two nodes:
 * <ul>
 *   <li>Current magnitude = Flow rate ($/s, units/s)</li>
 *   <li>Current direction = Direction of flow (from source to destination)</li>
 *   <li>Flow can be fixed, or computed from an equation</li>
 * </ul>
 * 
 * <h3>Flow Equation:</h3>
 * The flow can be specified as an equation using:
 * <ul>
 *   <li><code>Vs</code> - Source sector stock level (voltage)</li>
 *   <li><code>Vd</code> - Destination sector stock level (voltage)</li>
 *   <li><code>t</code> - Simulation time</li>
 *   <li>Parameters (slider variables)</li>
 *   <li>References to other computed values</li>
 * </ul>
 * 
 * <h3>Examples:</h3>
 * <pre>
 *   "100"                      - Fixed flow of 100/s
 *   "0.8 * Vs"                 - Consumption = 80% of household wealth
 *   "rate * (Vs - Vd)"         - Flow proportional to price differential
 *   "Wages * taxRate"          - Tax = rate × income
 * </pre>
 * 
 * <h3>MNA Behavior:</h3>
 * <ul>
 *   <li>Nonlinear element (flow may depend on voltages)</li>
 *   <li>Stamps current source between nodes</li>
 *   <li>Uses Newton-Raphson iteration for voltage-dependent flows</li>
 *   <li>KCL at sector nodes automatically enforces SFC identity</li>
 * </ul>
 * 
 * @see SFCSectorElm For the sector nodes this connects
 */
public class SFCFlowElm extends CircuitElm {
    
    /** Flow name (e.g., "Wages", "Consumption", "Taxes") */
    private String flowName = "Flow";
    
    /** Flow equation string */
    private String flowEquation = "0";
    
    /** Compiled expression */
    private Expr compiledExpr;
    
    /** Expression evaluation state */
    private ExprState exprState;
    
    /** Computed flow value (current) */
    private double flowValue;
    
    /** Previous flow value for convergence checking (kept for future use) */
    @SuppressWarnings("unused")
    private double lastFlowValue;
    
    /** Previous source voltage for derivative calculation */
    private double lastVs;
    
    /** Previous destination voltage for derivative calculation */
    private double lastVd;
    
    /** Slider variable name */
    private String sliderVarName = "rate";
    
    /** Slider value */
    private double sliderValue = 1.0;
    
    /** Arrow polygon for drawing */
    private Polygon arrow1, arrow2;
    private Point midPoint;
    
    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================
    
    /**
     * Constructor for creating element from UI menu.
     */
    public SFCFlowElm(int xx, int yy) {
        super(xx, yy);
        flowName = "Flow";
        flowEquation = "100";  // Default: fixed flow of 100
        sliderVarName = "rate";
        sliderValue = 1.0;
        parseExpression();
    }
    
    /**
     * Constructor for loading from file.
     */
    public SFCFlowElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        
        if (st.hasMoreTokens()) {
            flowName = CustomLogicModel.unescape(st.nextToken());
        }
        if (st.hasMoreTokens()) {
            flowEquation = CustomLogicModel.unescape(st.nextToken());
        }
        if (st.hasMoreTokens()) {
            sliderVarName = CustomLogicModel.unescape(st.nextToken());
        }
        if (st.hasMoreTokens()) {
            try {
                sliderValue = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                sliderValue = 1.0;
            }
        }
        
        parseExpression();
    }
    
    /**
     * Parse the flow equation string into a compiled expression.
     */
    private void parseExpression() {
        // Expression has access to:
        // values[0] = Vs (source voltage/stock)
        // values[1] = Vd (destination voltage/stock)
        // values[2] = slider parameter
        exprState = new ExprState(3);
        
        if (flowEquation == null || flowEquation.trim().isEmpty()) {
            flowEquation = "0";
        }
        
        try {
            // Replace Vs, Vd with indexed variables (_a=Vs, _b=Vd, _c=slider)
            String parsed = flowEquation
                .replace("Vs", "_a")
                .replace("Vd", "_b")
                .replace(sliderVarName, "_c");
            
            ExprParser parser = new ExprParser(parsed);
            compiledExpr = parser.parseExpression();
            
            String err = parser.gotError();
            if (err != null) {
                CirSim.console("SFCFlowElm: Parse error in '" + flowEquation + "': " + err);
                compiledExpr = null;
            }
        } catch (Exception e) {
            CirSim.console("SFCFlowElm: Exception parsing '" + flowEquation + "': " + e.getMessage());
            compiledExpr = null;
        }
    }
    
    // =========================================================================
    // ELEMENT IDENTIFICATION
    // =========================================================================
    
    @Override
    int getDumpType() { return 269; }  // Unique element type
    
    @Override
    String dump() {
        return super.dump() + " " + 
               CustomLogicModel.escape(flowName) + " " + 
               CustomLogicModel.escape(flowEquation) + " " +
               CustomLogicModel.escape(sliderVarName) + " " +
               sliderValue;
    }
    
    // =========================================================================
    // GEOMETRY
    // =========================================================================
    
    @Override
    int getPostCount() { return 2; }  // Source and destination nodes
    
    @Override
    void setPoints() {
        super.setPoints();
        calcLeads(24);
        
        // Calculate arrow points for flow direction indicator
        midPoint = interpPoint(lead1, lead2, 0.5);
        
        int arrowLen = 8;
        int arrowWidth = 4;
        arrow1 = calcArrow(lead1, midPoint, arrowLen, arrowWidth);
        arrow2 = calcArrow(midPoint, lead2, arrowLen, arrowWidth);
    }
    
    // =========================================================================
    // MNA CIRCUIT INTERFACE
    // =========================================================================
    
    @Override
    boolean nonLinear() { 
        return true;  // Flow equation may depend on voltages
    }
    
    @Override
    void reset() {
        super.reset();
        flowValue = 0;
        lastFlowValue = 0;
        lastVs = 0;
        lastVd = 0;
        if (exprState != null) {
            exprState.reset();
        }
    }
    
    @Override
    void stamp() {
        // Mark as nonlinear (equation depends on voltages)
        sim.stampNonLinear(nodes[0]);
        sim.stampNonLinear(nodes[1]);
    }
    
    /**
     * Get convergence limit based on iteration count.
     * More lenient over time to help convergence.
     */
    double getConvergeLimit() {
        if (sim.subIterations < 10)
            return 0.001;
        if (sim.subIterations < 100)
            return 0.01;
        return 0.1;
    }
    
    @Override
    void doStep() {
        if (compiledExpr == null) {
            // No valid expression, stamp small resistor to avoid singular matrix
            sim.stampResistor(nodes[0], nodes[1], 1e8);
            flowValue = 0;
            current = 0;
            return;
        }
        
        // Check convergence of input voltages
        double convergeLimit = getConvergeLimit();
        double vs = volts[0];  // Source sector stock
        double vd = volts[1];  // Destination sector stock
        
        if (Math.abs(vs - lastVs) > convergeLimit || 
            Math.abs(vd - lastVd) > convergeLimit) {
            sim.converged = false;
        }
        
        // Set up expression state
        exprState.values[0] = vs;              // Vs
        exprState.values[1] = vd;              // Vd  
        exprState.values[2] = sliderValue;    // slider parameter
        exprState.t = sim.t;
        
        // Evaluate flow equation
        flowValue = compiledExpr.eval(exprState);
        
        // Newton-Raphson: compute partial derivatives for linearization
        // I = f(Vs, Vd) ≈ I0 + (∂I/∂Vs)·ΔVs + (∂I/∂Vd)·ΔVd
        
        double dv = 1e-6;  // Small voltage for numerical derivative
        
        // Compute ∂I/∂Vs
        exprState.values[0] = vs + dv;
        double flowPlus = compiledExpr.eval(exprState);
        exprState.values[0] = vs - dv;
        double flowMinus = compiledExpr.eval(exprState);
        double dI_dVs = (flowPlus - flowMinus) / (2 * dv);
        exprState.values[0] = vs;
        
        // Compute ∂I/∂Vd
        exprState.values[1] = vd + dv;
        flowPlus = compiledExpr.eval(exprState);
        exprState.values[1] = vd - dv;
        flowMinus = compiledExpr.eval(exprState);
        double dI_dVd = (flowPlus - flowMinus) / (2 * dv);
        exprState.values[1] = vd;
        
        // Stamp linearized current source
        // Current flows from node[0] (source) to node[1] (destination)
        // Positive flow = current out of source, into destination
        
        // Stamp voltage-controlled current contributions
        if (Math.abs(dI_dVs) > 1e-12) {
            // I increases as Vs increases: stamp VCCS
            sim.stampMatrix(nodes[0], nodes[0], -dI_dVs);
            sim.stampMatrix(nodes[1], nodes[0], dI_dVs);
        }
        
        if (Math.abs(dI_dVd) > 1e-12) {
            // I increases as Vd increases
            sim.stampMatrix(nodes[0], nodes[1], -dI_dVd);
            sim.stampMatrix(nodes[1], nodes[1], dI_dVd);
        }
        
        // Stamp the constant part: I0 - (∂I/∂Vs)·Vs - (∂I/∂Vd)·Vd
        double constantPart = flowValue - dI_dVs * vs - dI_dVd * vd;
        sim.stampCurrentSource(nodes[0], nodes[1], constantPart);
        
        // Update current variable for display
        current = flowValue;
        
        // Save for next iteration
        lastVs = vs;
        lastVd = vd;
        lastFlowValue = flowValue;
    }
    
    @Override
    void stepFinished() {
        // Register flow value for use by other elements
        ComputedValues.setComputedValue(flowName, flowValue, this);
        ComputedValues.markComputedThisStep(flowName);
        
        // Update expression state for integrate/diff functions
        if (exprState != null) {
            exprState.updateLastValues(flowValue);
        }
    }
    
    @Override
    double getCurrent() {
        return flowValue;
    }
    
    @Override
    double getCurrentIntoNode(int n) {
        // Flow is from node[0] to node[1]
        // Current into node[0] = -flow (flow leaves source)
        // Current into node[1] = +flow (flow enters destination)
        if (n == 0) return -flowValue;
        return flowValue;
    }
    
    @Override
    boolean getConnection(int n1, int n2) {
        // Current source has no direct connection between nodes
        // (current flows through it, but it's not a conductor)
        return false;
    }
    
    // =========================================================================
    // DRAWING
    // =========================================================================
    
    @Override
    void draw(Graphics g) {
        setBbox(point1, point2, 6);
        
        // Draw leads
        setVoltageColor(g, volts[0]);
        drawThickLine(g, point1, lead1);
        setVoltageColor(g, volts[1]);
        drawThickLine(g, lead2, point2);
        
        // Draw flow symbol (line with arrow showing direction)
        g.setColor(needsHighlight() ? selectColor : Color.gray);
        drawThickLine(g, lead1, lead2);
        
        // Draw arrow indicating flow direction
        if (flowValue >= 0) {
            // Flow from source to destination
            g.fillPolygon(arrow2);
        } else {
            // Reverse flow
            g.fillPolygon(arrow1);
        }
        
        // Draw current source symbol (circle with stroke)
        int radius = 8;
        g.context.beginPath();
        g.context.arc(midPoint.x, midPoint.y, radius, 0, 2 * Math.PI);
        g.context.stroke();
        
        // Draw flow name above
        g.setColor(whiteColor);
        int nameY = midPoint.y - 15;
        drawCenteredText(g, flowName, midPoint.x, nameY, true);
        
        // Draw flow value below
        String flowText = getCurrentText(Math.abs(flowValue));
        if (flowValue < 0) flowText = "-" + flowText;
        drawCenteredText(g, flowText, midPoint.x, midPoint.y + 20, true);
        
        // Draw current dots (animation showing flow)
        updateDotCount();
        if (sim.dragElm != this) {
            drawDots(g, point1, point2, curcount);
        }
        
        // Draw posts
        drawPosts(g);
    }
    
    // =========================================================================
    // INFO DISPLAY
    // =========================================================================
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "SFC Flow: " + flowName;
        arr[1] = "Flow = " + getCurrentText(flowValue);
        arr[2] = "Equation: " + flowEquation;
        arr[3] = "Vs = " + getVoltageText(volts[0]);
        arr[4] = "Vd = " + getVoltageText(volts[1]);
        if (!sliderVarName.isEmpty()) {
            arr[5] = sliderVarName + " = " + sliderValue;
        }
    }
    
    // =========================================================================
    // EDIT INTERFACE
    // =========================================================================
    
    @Override
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Flow Name", 0, -1, -1);
            ei.text = flowName;
            return ei;
        }
        if (n == 1) {
            EditInfo ei = new EditInfo("Flow Equation (Vs=source, Vd=dest)", 0, -1, -1);
            ei.text = flowEquation;
            return ei;
        }
        if (n == 2) {
            EditInfo ei = new EditInfo("Slider Variable Name", 0, -1, -1);
            ei.text = sliderVarName;
            return ei;
        }
        if (n == 3) {
            return new EditInfo("Slider Value", sliderValue, 0, 10);
        }
        return null;
    }
    
    @Override
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            flowName = ei.textf.getText();
        }
        if (n == 1) {
            flowEquation = ei.textf.getText();
            parseExpression();
        }
        if (n == 2) {
            sliderVarName = ei.textf.getText();
            parseExpression();  // Re-parse to update variable reference
        }
        if (n == 3) {
            sliderValue = ei.value;
        }
    }
    
    // =========================================================================
    // ACCESSORS
    // =========================================================================
    
    /** Get flow name for display and equation references */
    public String getFlowName() { return flowName; }
    
    /** Get current flow value */
    public double getFlowValue() { return flowValue; }
    
    /** Get slider value for external use */
    public double getSliderValue() { return sliderValue; }
    
    /** Set slider value programmatically */
    public void setSliderValue(double v) { sliderValue = v; }
}
