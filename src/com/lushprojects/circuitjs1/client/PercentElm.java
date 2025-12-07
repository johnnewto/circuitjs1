/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * PercentElm - Computes (a/b/c...) * 100 and outputs the percentage value
 * Similar to DividerElm but multiplies the result by 100
 */
class PercentElm extends VCCSElm {
    public PercentElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
    }
    
    public PercentElm(int xx, int yy) {
        super(xx, yy);
        // Implement percentage as (a/b/c...) * 100 = a * (1/b) * (1/c) * 100
        exprString = "a";
        for (int i = 1; i != inputCount; i++) {
            char var = (char)('a' + i);
            // Add epsilon to denominator before inverting to prevent 1/0
            exprString += "*(1/(" + var + "!=0?" + var + ":1e-9))";
        }
        exprString += "*100";
        parseExpr();
        // Override the size set by parent to be small by default
        flags |= FLAG_SMALL; // Set flag to persist small size
        setSize(1); // Set to small size
        setPoints(); // Recalculate points with new size
    }
    
    void setupPins() {
        // V- is internal, always node 0 (ground)
        sizeX = 2;
        sizeY = inputCount > 2 ? inputCount : 2;
        pins = new Pin[inputCount+2];
        int i;
        for (i = 0; i != inputCount; i++) {
            pins[i] = new Pin(i, SIDE_W, ""); 
        }
        pins[inputCount] = new Pin(0, SIDE_E, "");
        pins[inputCount].output = true;
        lastVolts = new double[inputCount];
        exprState = new ExprState(inputCount);
        allocNodes();
    }

    String getChipName() { return "Percent"; }
    
    void stamp() {
        int vn = pins[inputCount].voltSource + sim.nodeList.size();
        sim.stampNonLinear(vn);
        sim.stampVoltageSource(0, nodes[inputCount], pins[inputCount].voltSource);
    }
    
    // Minimum denominator value to prevent numerical instability
    static final double MIN_DENOMINATOR = 1e-6;
    // Maximum derivative magnitude to prevent solver instability
    static final double MAX_DERIVATIVE = 1e6;
    
    double getConvergeLimit() {
        // get maximum change in voltage per step when testing for convergence.  be more lenient over time
        if (sim.subIterations < 10)
            return .001;
        if (sim.subIterations < 200)
            return .01;
        return .1;
    }
    
    void doStep() {
        int i;
        
        int vn = pins[inputCount].voltSource + sim.nodeList.size();
        if (expr != null) {
            // Check for divide by zero on denominators (inputs 1+)
            // Use a larger threshold to prevent numerical instability before derivatives explode
            boolean divByZero = false;
            for (i = 1; i < inputCount; i++) {
                if (Math.abs(volts[i]) < MIN_DENOMINATOR) {
                    divByZero = true;
                    break;
                }
            }
            
            // If divide by zero detected, output zero and converge
            if (divByZero) {
                sim.stampRightSide(vn, 0.0);
                return;
            }
            
            // // Check input convergence (like VCCSElm does)
            // double convergeLimit = getConvergeLimit();
            // for (i = 0; i != inputCount; i++) {
            //     if (Math.abs(volts[i]-lastVolts[i]) > convergeLimit) {
            //         sim.converged = false;
            //     }
            // }
            
            // Calculate output
            for (i = 0; i != inputCount; i++)
                exprState.values[i] = volts[i];

            exprState.t = sim.t;
            double v0 = expr.eval(exprState);
            double vMinus = 0; // V- is always ground
            
            // Check output convergence
            // Use relative tolerance for large values, absolute tolerance for values near zero
            double outputDelta = Math.abs(volts[inputCount]-vMinus-v0);
            double tolerance = Math.max(Math.abs(v0)*.01, 1e-9);  // At least 1e-9 absolute tolerance
            if (outputDelta > tolerance && sim.subIterations < 100)
                sim.converged = false;
            double rs = v0;
            
            // Calculate and stamp output derivatives
            for (i = 0; i != inputCount; i++) {
                double dv = volts[i]-lastVolts[i];
                if (Math.abs(dv) < 1e-6)
                    dv = 1e-6;
                exprState.values[i] = volts[i];
                double v = expr.eval(exprState);
                exprState.values[i] = volts[i]-dv;
                double v2 = expr.eval(exprState);
                double dx = (v-v2)/dv;
                // Clamp derivative to prevent solver instability with small denominators
                if (Math.abs(dx) < 1e-6)
                    dx = sign(dx, 1e-6);
                if (Math.abs(dx) > MAX_DERIVATIVE)
                    dx = sign(dx, MAX_DERIVATIVE);
                sim.stampMatrix(vn, nodes[i], -dx);
                // Adjust right side
                rs -= dx*volts[i];
                exprState.values[i] = volts[i];
            }
            
            sim.stampRightSide(vn, rs);
        }

        for (i = 0; i != inputCount; i++)
            lastVolts[i] = volts[i];
    }
    
    void stepFinished() {
        double vMinus = 0; // V- is always ground
        exprState.updateLastValues(volts[inputCount]-vMinus);
    }

    int getPostCount() { return inputCount+1; } // since V- is not a post anymore
    
    int getVoltageSourceCount() { return 1; }
    
    int getDumpType() { return 'P'; }

    boolean hasCurrentOutput() { return false; }

    void setCurrent(int vn, double c) {
        if (pins[inputCount].voltSource == vn) {
            pins[inputCount].current = c;
        }
    }

    void draw(Graphics g) {
        drawChip(g);
        String label = "%"; // Percent symbol
        // Calculate midpoint using rectPointsX and rectPointsY arrays
        int mid_x = (rectPointsX[0] + rectPointsX[1] + rectPointsX[2] + rectPointsX[3]) / 4;
        int mid_y = (rectPointsY[0] + rectPointsY[1] + rectPointsY[2] + rectPointsY[3]) / 4;

        boolean selected = needsHighlight();
        g.setColor(selected ? selectColor : whiteColor);
        
        // Draw % symbol using the chip's standard font size (scales with chip size)
        int fontSize = cspc > 30 ? 25 : 14;  // Larger font for larger chip size
        g.context.save();
        g.context.setFont((selected ? "bold " : "") + fontSize + "px sans-serif");
        drawCenteredText(g, label, mid_x, mid_y, true);
        g.context.restore();
    }
    
    public EditInfo getChipEditInfo(int n) {
        if (n == 0)
            return new EditInfo("# of Inputs", inputCount, 1, 8).
                setDimensionless();
        if (n == 1) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Small Size", (flags & FLAG_SMALL) != 0);
            return ei;
        }
        return null;
    }

    public void setChipEditValue(int n, EditInfo ei) {
        CirSim.console("PercentElm.setChipEditValue n=" + n);
        if (n == 0) {
            CirSim.console("  Changing input count from " + inputCount + " to " + ei.value);
            if (ei.value < 1 || ei.value > 8) {
                CirSim.console("  Invalid value, returning");
                return;
            }
            inputCount = (int) ei.value;
            CirSim.console("  Building expression string...");
            exprString = "a";
            for (int i = 1; i != inputCount; i++) {
                char var = (char)('a' + i);
                exprString += "*(1/(" + var + "!=0?" + var + ":1e-9))";
            }
            exprString += "*100";
            CirSim.console("  Expression: " + exprString);
            CirSim.console("  Parsing expression...");
            parseExpr();
            CirSim.console("  Setting up pins...");
            setupPins();
            CirSim.console("  Allocating nodes...");
            allocNodes();
            CirSim.console("  Setting points...");
            setPoints();
            CirSim.console("  Done changing input count");
        }
        if (n == 1) {
            CirSim.console("  Changing size");
            flags = ei.changeFlag(flags, FLAG_SMALL);
            setSize((flags & FLAG_SMALL) != 0 ? 1 : 2);
            setupPins();
            allocNodes();
            setPoints();
            CirSim.console("  Done changing size");
        }
        CirSim.console("PercentElm.setChipEditValue DONE");
    }
}
