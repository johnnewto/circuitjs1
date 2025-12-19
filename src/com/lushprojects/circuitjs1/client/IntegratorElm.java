/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * IntegratorElm - Integrator with optional initial value input
 * Can have 1 or 2 inputs based on useInitialValueInput flag:
 * - If useInitialValueInput=true: Two inputs (f(t) to integrate, y(0) initial value)
 * - If useInitialValueInput=false: One input (f(t) to integrate), uses parameter for y(0)
 * Uses numerical integration: y[n+1] = y[n] + dt * f(t)
 */
class IntegratorElm extends CircuitElm {
    final int FLAG_SMALL = 2;
    private static final int FLAG_USE_INIT_INPUT = 4; // Flag for using init value input pin
    
    private Expr integrationExpr;           // Compiled integration expression
    private ExprState integrationState;     // Integration state
    private double integratedValue;         // Current integration value
    private double initialValue = 0.0;      // Initial condition parameter
    private double lastVolts[];             // Last voltage values for convergence
    int inputCount;
    
    // Geometry
    int opsize, opheight, opwidth;
    Point inPosts[], inLeads[];
    Polygon bodyPoly;
    Font labelFont;
    
    public IntegratorElm(int xx, int yy) {
        super(xx, yy);
        noDiagonal = true;
        // Default: use input pin for initial value
        flags |= FLAG_USE_INIT_INPUT;
        inputCount = 2; // Two inputs: 0=integrate, 1=initial value
        setSize(sim.smallGridCheckItem.getState() ? 1 : 2);
        lastVolts = new double[inputCount];
        initIntegration();
    }
    
    public IntegratorElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        noDiagonal = true;
        inputCount = Integer.parseInt(st.nextToken());
        // Parse initial value if present
        if (st.hasMoreTokens()) {
            try {
                initialValue = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                initialValue = 0.0;
            }
        }
        setSize((f & FLAG_SMALL) != 0 ? 1 : 2);
        lastVolts = new double[inputCount];
        initIntegration();
    }
    
    String dump() {
        return super.dump() + " " + inputCount + " " + initialValue;
    }
    
    int getDumpType() { return 260; }
    
    private boolean useInitialValueInput() {
        return (flags & FLAG_USE_INIT_INPUT) != 0;
    }
    
    private void initIntegration() {
        // Initialize integration state with 1 input (the input voltage as 'a')
        integrationState = new ExprState(1);
        integrationState.lastOutput = initialValue;
        integratedValue = initialValue;
        
        // Parse the integration expression
        parseIntegrationExpr();
    }
    
    private void parseIntegrationExpr() {
        try {
            // Create expression: lastoutput + timestep * a
            // Where 'a' is the input voltage
            // This implements numerical integration: y[n+1] = y[n] + dt * f(t,y)
            String exprStr = "lastoutput+timestep*a";
            ExprParser parser = new ExprParser(exprStr);
            integrationExpr = parser.parseExpression();
            String err = parser.gotError();
            if (err != null) {
                CirSim.console("IntegratorElm: Parse error in integration expression: " + exprStr + ": " + err);
                integrationExpr = null;
            }
        } catch (Exception e) {
            CirSim.console("IntegratorElm: Error parsing integration expression: " + e.getMessage());
            integrationExpr = null;
        }
    }
    
    void setSize(int s) {
        opsize = s;
        opheight = 8 * s;
        opwidth = 13 * s;
        flags = (flags & ~FLAG_SMALL) | ((s == 1) ? FLAG_SMALL : 0);
    }
    
    void setPoints() {
        super.setPoints();
        if (dn > 150 && this == sim.dragElm)
            setSize(2);
        int ww = opwidth;
        if (ww > dn/2)
            ww = (int) (dn/2);
        calcLeads(ww*2);
        
        int hs = opheight * dsign;
        inPosts = new Point[inputCount];
        inLeads = new Point[inputCount];
        
        // Calculate input positions
        int i0 = -inputCount/2;
        for (int i = 0; i != inputCount; i++, i0++) {
            if (i0 == 0 && (inputCount & 1) == 0)
                i0++;
            inPosts[i] = interpPoint(point1, point2, 0, -hs * i0);
            inLeads[i] = interpPoint(lead1, lead2, 0, -hs * i0);
        }
        
        // Create rectangular body
        Point[] pts = newPointArray(4);
        interpPoint2(lead1, lead2, pts[0], pts[1], 0, hs * (inputCount/2 + 1));
        interpPoint2(lead1, lead2, pts[3], pts[2], 1, hs * (inputCount/2 + 1));
        bodyPoly = createPolygon(pts);
        
        setBbox(point1, point2, opheight * (inputCount/2 + 1));
        labelFont = new Font("SansSerif", 0, opsize == 2 ? 14 : 10);
    }
    
    void draw(Graphics g) {
        setBbox(point1, point2, opheight * (inputCount/2 + 1));
        
        // Draw input leads
        for (int i = 0; i < inputCount; i++) {
            setVoltageColor(g, volts[i]);
            drawThickLine(g, inPosts[i], inLeads[i]);
        }
        
        // Draw output lead
        setVoltageColor(g, volts[inputCount]);
        drawThickLine(g, lead2, point2);
        
        // Draw body
        g.setColor(needsHighlight() ? selectColor : lightGrayColor);
        drawThickPolygon(g, bodyPoly);
        
        // Draw "∫dt" label
        g.setFont(labelFont);
        Point center = interpPoint(lead1, lead2, 0.5);
        g.setColor(needsHighlight() ? selectColor : whiteColor);
        drawCenteredText(g, "∫dt", center.x, center.y, true);
        
        // Draw current dots
        curcount = updateDotCount(current, curcount);
        drawDots(g, point2, lead2, curcount);
        drawPosts(g);
    }
    
    int getPostCount() { return inputCount + 1; }
    
    Point getPost(int n) {
        if (n == inputCount)
            return point2;
        return inPosts[n];
    }
    
    int getVoltageSourceCount() { return 1; }
    
    boolean nonLinear() { return true; }
    
    // Get convergence limit - more lenient over time to help convergence
    double getConvergeLimit() {
        double relativeTolerance;
        if (sim.subIterations < 10)
            relativeTolerance = 0.001;  // 0.1% for early iterations
        else if (sim.subIterations < 100)
            relativeTolerance = 0.01;   // 1% for mid iterations
        else
            relativeTolerance = 0.1;    // 10% for late iterations
        
        double maxMagnitude = 1.0;
        maxMagnitude = Math.max(maxMagnitude, Math.abs(integratedValue));
        if (lastVolts != null && lastVolts.length > 0) {
            maxMagnitude = Math.max(maxMagnitude, Math.abs(lastVolts[0]));
        }
        
        return maxMagnitude * relativeTolerance;
    }
    
    void stamp() {
        int vn = sim.nodeList.size() + voltSource;
        sim.stampNonLinear(vn);
        sim.stampVoltageSource(0, nodes[inputCount], voltSource);
    }
    
    void doStep() {
        int i;
        
        // On first timestep, set initial value
        if (sim.timeStepCount == 0) {
            for (i = 0; i != inputCount; i++) {
                lastVolts[i] = volts[i];
            }
            // Get initial value from input pin or parameter
            double initVal;
            if (useInitialValueInput() && inputCount > 1) {
                initVal = volts[1];
            } else {
                initVal = initialValue;
            }
            integrationState.lastOutput = initVal;
            integratedValue = initVal;
        }
        
        // Get input voltage to integrate (input 0)
        double inputVoltage = volts[0];
        
        int vn = sim.nodeList.size() + voltSource;
        if (integrationExpr != null) {
            // Set up integration state
            integrationState.values[0] = inputVoltage;
            integrationState.t = sim.t;
            
            // Evaluate integration expression: lastoutput + timestep * a
            integratedValue = integrationExpr.eval(integrationState);
            
            // Check output voltage convergence
            double outputVoltage = volts[inputCount];
            double voltageDiff = Math.abs(outputVoltage - integratedValue);
            double threshold = Math.max(Math.abs(integratedValue) * 0.01, 1e-6);
            if (voltageDiff > threshold && sim.subIterations < 100) {
                sim.converged = false;
            }
            
            // Stamp the right side with the integrated value
            sim.stampRightSide(vn, integratedValue);
        }
        
        for (i = 0; i != inputCount; i++)
            lastVolts[i] = volts[i];
    }
    
    void stepFinished() {
        if (integrationState != null) {
            integrationState.lastOutput = integratedValue;
        }
    }
    
    @Override
    public void reset() {
        super.reset();
        if (integrationState != null) {
            integrationState.reset();
            integrationState.lastOutput = initialValue;
        }
        integratedValue = initialValue;
        lastVolts = new double[inputCount];
    }
    
    // No current path through inputs, but output connects to ground
    boolean getConnection(int n1, int n2) { return false; }
    boolean hasGroundConnection(int n1) { return n1 == inputCount; }
    
    double getCurrentIntoNode(int n) {
        if (n == inputCount)
            return -current;
        return 0;
    }
    
    void getInfo(String arr[]) {
        arr[0] = "Integrator";
        arr[1] = "Equation: y[n+1] = y[n] + dt * f(t)";
        if (useInitialValueInput()) {
            arr[2] = "Mode: Initial value from input pin";
            arr[3] = "Input f(t): " + getVoltageText(volts[0]);
            if (inputCount > 1)
                arr[4] = "Init y(0): " + getVoltageText(volts[1]);
            arr[5] = "Output (∫): " + getVoltageText(integratedValue);
        } else {
            arr[2] = "Initial value y(0): " + getVoltageText(initialValue);
            arr[3] = "Input f(t): " + getVoltageText(volts[0]);
            arr[4] = "Output (∫): " + getVoltageText(integratedValue);
        }
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Initial Value", initialValue);
            return ei;
        }
        if (n == 1) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Use Initial Value Input Pin", useInitialValueInput());
            return ei;
        }
        if (n == 2)
            return EditInfo.createCheckbox("Small", (flags & FLAG_SMALL) != 0);
        return null;
    }
    
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            initialValue = ei.value;
            if (integrationState != null) {
                integrationState.lastOutput = initialValue;
            }
            integratedValue = initialValue;
        }
        if (n == 1) {
            boolean oldUseInput = useInitialValueInput();
            flags = ei.changeFlag(flags, FLAG_USE_INIT_INPUT);
            boolean newUseInput = useInitialValueInput();
            
            if (oldUseInput != newUseInput) {
                inputCount = newUseInput ? 2 : 1;
                lastVolts = new double[inputCount];
                allocNodes();
                setPoints();
            }
        }
        if (n == 2) {
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
}
