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
 * Similar to GodlyTableElm but simplified for one integration channel
 */
class IntegratorElm extends VCCSElm {
    private Expr integrationExpr;           // Compiled integration expression
    private ExprState integrationState;     // Integration state
    private double integratedValue;         // Current integration value
    private double initialValue = 0.0;      // Initial condition parameter
    private static final int FLAG_USE_INIT_INPUT = 1; // Flag for using init value input pin
    
    public IntegratorElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        // Parse initial value if present
        if (st.hasMoreTokens()) {
            try {
                initialValue = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                initialValue = 0.0;
            }
        }
        // Check if using initial value input (flag in parent's flags)
        inputCount = useInitialValueInput() ? 2 : 1;
        initIntegration();
    }
    
    public IntegratorElm(int xx, int yy) {
        super(xx, yy);
        // Default: use input pin for initial value
        flags |= FLAG_USE_INIT_INPUT;
        inputCount = 2; // Two inputs: 0=integrate, 1=initial value
        // Expression for integral: lastoutput + timestep*a
        exprString = "lastoutput+timestep*a";
        parseExpr();
        // Override the size set by parent to be small by default
        flags |= FLAG_SMALL; // Set flag to persist small size
        setSize(1); // Set to small size
        setupPins();
        setPoints(); // Recalculate points with new size
        initIntegration();
    }
    
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

    void setupPins() {
        // V- is internal, always node 0 (ground)
        sizeX = 2;
        sizeY = inputCount > 2 ? inputCount : 2;
        pins = new Pin[inputCount+2];
        int i;
        for (i = 0; i != inputCount; i++) {
            String label = "";
            // if (i == 0) {
            //     label = "f(t)"; // Input to integrate
            // } else if (i == 1 && useInitialValueInput()) {
            //     label = "y(0)"; // Initial value (only if using input)
            // }
            pins[i] = new Pin(i, SIDE_W, label);
        }
        // pins[inputCount] = new Pin(0, SIDE_E, "∫");
        pins[inputCount] = new Pin(0, SIDE_E, "");
        pins[inputCount].output = true;
        lastVolts = new double[inputCount];
        exprState = new ExprState(inputCount);
        allocNodes();
    }

    String getChipName() { return "Integrator"; }
    
    // Get convergence limit (same as GodlyTableElm)
    // More lenient over time to help convergence
    double getConvergeLimit() {
        // Base relative tolerance (0.1% to 1% depending on iteration count)
        double relativeTolerance;
        if (sim.subIterations < 10)
            relativeTolerance = 0.001;  // 0.1% for early iterations
        else if (sim.subIterations < 100)
            relativeTolerance = 0.01;   // 1% for mid iterations
        else
            relativeTolerance = 0.1;    // 10% for late iterations (help convergence)
        
        // Find maximum absolute value across integrated value and input
        double maxMagnitude = 1.0;  // Minimum threshold (prevent division by zero)
        maxMagnitude = Math.max(maxMagnitude, Math.abs(integratedValue));
        if (lastVolts != null && lastVolts.length > 0) {
            maxMagnitude = Math.max(maxMagnitude, Math.abs(lastVolts[0]));
        }
        
        // Return relative limit scaled by magnitude
        return maxMagnitude * relativeTolerance;
    }
    
    void stamp() {
        int vn = pins[inputCount].voltSource + sim.nodeList.size();
        sim.stampNonLinear(vn);
        sim.stampVoltageSource(0, nodes[inputCount], pins[inputCount].voltSource);
    }


    void doStep() {
        int i;
        
        // On first timestep, set initial value
        if (sim.timeStepCount == 0) {
            for (i = 0; i != inputCount; i++) {
                lastVolts[i] = volts[i];
                exprState.lastValues[i] = volts[i];
            }
            // Get initial value from input pin or parameter
            double initVal;
            if (useInitialValueInput()) {
                // Sample from input 1 (y(0))
                initVal = volts[1];
            } else {
                // Use parameter value
                initVal = initialValue;
            }
            integrationState.lastOutput = initVal;
            integratedValue = initVal;
        }
        
        // Get input voltage to integrate (input 0)
        double inputVoltage = volts[0];
        
        int vn = pins[inputCount].voltSource + sim.nodeList.size();
        if (integrationExpr != null) {
            // Set up integration state
            integrationState.values[0] = inputVoltage; // 'a' = input voltage (f(t))
            integrationState.t = sim.t;
            
            // Evaluate integration expression: lastoutput + timestep * a
            integratedValue = integrationExpr.eval(integrationState);
            
            // Check output voltage convergence (like GodlyTableElm)
            double outputVoltage = volts[inputCount];
            double voltageDiff = Math.abs(outputVoltage - integratedValue);
            // Use relative threshold (1%) but with minimum absolute threshold (1e-6)
            // to avoid false convergence failures when values are near zero
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
        if (integrationState != null) {
            integrationState.lastOutput = integratedValue;
        }
    }
    
    @Override
    public void reset() {
        super.reset();
        // Reset integration to initial condition
        if (integrationState != null) {
            integrationState.reset();
            integrationState.lastOutput = initialValue;
        }
        integratedValue = initialValue;
    }
    
    @Override
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.dump());
        sb.append(" ").append(initialValue);
        return sb.toString();
    }
    
    int getDumpType() { return 260; }

    int getPostCount() { return inputCount+1; } // since V- is not a post anymore
    
    int getVoltageSourceCount() { return 1; }
    
    boolean hasCurrentOutput() { return false; }
    
    void setCurrent(int vn, double c) {
        if (pins[inputCount].voltSource == vn) {
            pins[inputCount].current = c;
        }
    }

    void draw(Graphics g) {
        drawChip(g);
        String label = "∫dt"; // Integral symbol
        // Calculate midpoint using rectPointsX and rectPointsY arrays
        int mid_x = (rectPointsX[0] + rectPointsX[1] + rectPointsX[2] + rectPointsX[3]) / 4;
        int mid_y = (rectPointsY[0] + rectPointsY[1] + rectPointsY[2] + rectPointsY[3]) / 4;

        boolean selected = needsHighlight();
        Font f = new Font("SansSerif", selected ? Font.BOLD : 0, 16);
        g.setFont(f);
        g.setColor(selected ? selectColor : whiteColor);

        drawCenteredText(g, label, mid_x, mid_y, true);

        // Restore original font
        g.restore();
    }

    public EditInfo getChipEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Initial Value", initialValue);
            return ei;
        }
        if (n == 1) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Use Initial Value Input Pin", useInitialValueInput());
            return ei;
        }
        if (n == 2) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Small Size", (flags & FLAG_SMALL) != 0);
            return ei;
        }
        return null;
    }

    public void setChipEditValue(int n, EditInfo ei) {
        if (n == 0) {
            initialValue = ei.value;
            // Reset integration to new initial value
            if (integrationState != null) {
                integrationState.lastOutput = initialValue;
            }
            integratedValue = initialValue;
        }
        if (n == 1) {
            // Toggle use initial value input flag
            boolean oldUseInput = useInitialValueInput();
            flags = ei.changeFlag(flags, FLAG_USE_INIT_INPUT);
            boolean newUseInput = useInitialValueInput();
            
            // If mode changed, update input count and recreate pins
            if (oldUseInput != newUseInput) {
                inputCount = newUseInput ? 2 : 1;
                setupPins();
                allocNodes();
                setPoints();
            }
        }
        if (n == 2) {
            flags = ei.changeFlag(flags, FLAG_SMALL);
            setSize((flags & FLAG_SMALL) != 0 ? 1 : 2);
            setupPins();
            allocNodes();
            setPoints();
        }
    }
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "Integrator";
        arr[1] = "Equation: y[n+1] = y[n] + dt * f(t)";
        if (useInitialValueInput()) {
            arr[2] = "Mode: Initial value from input pin";
            arr[3] = "Input f(t): " + getVoltageText(volts[0]);
            arr[4] = "Init y(0): " + getVoltageText(volts[1]);
            arr[5] = "Output (∫): " + getVoltageText(integratedValue);
        } else {
            arr[2] = "Initial value y(0): " + getVoltageText(initialValue);
            arr[3] = "Input f(t): " + getVoltageText(volts[0]);
            arr[4] = "Output (∫): " + getVoltageText(integratedValue);
        }
    }
}
