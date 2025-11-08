/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

class DifferentiatorElm extends VCCSElm {
    public DifferentiatorElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
    }
    
    public DifferentiatorElm(int xx, int yy) {
        super(xx, yy);
        inputCount = 1;
        // Expression for derivative: dadt (da/dt)
        exprString = "dadt";
        parseExpr();
        // Override the size set by parent to be small by default
        flags |= FLAG_SMALL; // Set flag to persist small size
        setSize(1); // Set to small size
        setupPins();
        setPoints(); // Recalculate points with new size
    }

    void setupPins() {
        // V- is internal, always node 0 (ground)
        sizeX = 2;
        sizeY = inputCount > 2 ? inputCount : 2;
        pins = new Pin[inputCount+2];
        int i;
        for (i = 0; i != inputCount; i++) {
            pins[i] = new Pin(i, SIDE_W, ""); // Empty string - no text on pins
        }
        pins[inputCount] = new Pin(0, SIDE_E, exprString);
        pins[inputCount].output = true;
        lastVolts = new double[inputCount];
        exprState = new ExprState(inputCount);
        allocNodes();
    }

    String getChipName() { return "Differentiator"; }
    
    void stamp() {
        int vn = pins[inputCount].voltSource + sim.nodeList.size();
        sim.stampNonLinear(vn);
        sim.stampVoltageSource(0, nodes[inputCount], pins[inputCount].voltSource);
    }


    void doStep() {
        int i;
        
        // On first timestep, initialize lastVolts to current voltages to avoid spike
        if (sim.timeStepCount == 0) {
            for (i = 0; i != inputCount; i++) {
                lastVolts[i] = volts[i];
                exprState.lastValues[i] = volts[i];
            }
        }
        
        // converged yet?
        double convergeLimit = getConvergeLimit();
        for (i = 0; i != inputCount; i++) {
            double diff = Math.abs(volts[i]-lastVolts[i]);
            if (diff > convergeLimit)
                sim.converged = false;
        }
        int vn = pins[inputCount].voltSource + sim.nodeList.size();
        if (expr != null) {
            // calculate output
            for (i = 0; i != inputCount; i++)
                exprState.values[i] = volts[i];

            exprState.t = sim.t;
            double v0 = expr.eval(exprState);
            double vMinus = 0; // V- is always ground
            if (Math.abs(volts[inputCount]-vMinus-v0) > Math.abs(v0)*.01 && sim.subIterations < 100)
                sim.converged = false;
            double rs = v0;
            
            // calculate and stamp output derivatives
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
                // adjust right side
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
    
    int getDumpType() { return 259; }

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
        String label = "d/dt"; // Derivative symbol
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
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Small Size", (flags & FLAG_SMALL) != 0);
            return ei;
        }
        return null;
    }

    public void setChipEditValue(int n, EditInfo ei) {
        if (n == 0) {
            flags = ei.changeFlag(flags, FLAG_SMALL);
            setSize((flags & FLAG_SMALL) != 0 ? 1 : 2);
            setupPins();
            allocNodes();
            setPoints();
        }
    }
}
