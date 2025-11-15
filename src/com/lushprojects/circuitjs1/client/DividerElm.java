/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

class DividerElm extends VCCSElm {
    public DividerElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
    }
    
    public DividerElm(int xx, int yy) {
        super(xx, yy);
        // Implement division as multiplication by reciprocal: a/b/c = a * (1/b) * (1/c)
        exprString = "a";
        for (int i = 1; i != inputCount; i++) {
            char var = (char)('a' + i);
            // Add epsilon to denominator before inverting to prevent 1/0
            exprString += "*(1/(" + var + "!=0?" + var + ":1e-9))";
        }
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

    String getChipName() { return "Divider"; }
    
    void stamp() {
        int vn = pins[inputCount].voltSource + sim.nodeList.size();
        sim.stampNonLinear(vn);
        sim.stampVoltageSource(0, nodes[inputCount], pins[inputCount].voltSource);
    }
    
    void doStep() {
        int i;
        
        int vn = pins[inputCount].voltSource + sim.nodeList.size();
        if (expr != null) {
            // Check for divide by zero on denominators (inputs 1+)
            boolean divByZero = false;
            for (i = 1; i < inputCount; i++) {
                if (Math.abs(volts[i]) < 1e-12) {
                    divByZero = true;
                    break;
                }
            }
            
            // If divide by zero detected, output zero and converge
            if (divByZero) {
                sim.stampRightSide(vn, 0.0);
                return;
            }
            
            // Calculate output
            for (i = 0; i != inputCount; i++)
                exprState.values[i] = volts[i];

            exprState.t = sim.t;
            double v0 = expr.eval(exprState);
            double vMinus = 0; // V- is always ground
            
            // Check output convergence
            if (Math.abs(volts[inputCount]-vMinus-v0) > Math.abs(v0)*.01 && sim.subIterations < 100)
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
                if (Math.abs(dx) < 1e-6)
                    dx = sign(dx, 1e-6);
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
    
    int getDumpType() { return 257; }

    boolean hasCurrentOutput() { return false; }

    void setCurrent(int vn, double c) {
        if (pins[inputCount].voltSource == vn) {
            pins[inputCount].current = c;
        }
    }

    void draw(Graphics g) {
        drawChip(g);
        String label = "÷"; // Division symbol
        // Calculate midpoint using rectPointsX and rectPointsY arrays
        int mid_x = (rectPointsX[0] + rectPointsX[1] + rectPointsX[2] + rectPointsX[3]) / 4;
        int mid_y = (rectPointsY[0] + rectPointsY[1] + rectPointsY[2] + rectPointsY[3]) / 4;

        boolean selected = needsHighlight();
        Font f = new Font("SansSerif", selected ? Font.BOLD : 0, 30);
        g.setFont(f);
        g.setColor(selected ? selectColor : whiteColor);

        drawCenteredText(g, label, mid_x, mid_y, true);

        // Restore original font
        g.restore();
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
        CirSim.console("DividerElm.setChipEditValue n=" + n);
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
        CirSim.console("DividerElm.setChipEditValue DONE");
    }
}
