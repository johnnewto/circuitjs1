/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

// Differentiator element - computes da/dt (derivative of input)
class DifferentiatorElm extends CircuitElm {
    final int FLAG_SMALL = 1;
    
    int opsize, opheight, opwidth;
    Point inPost, inLead;
    Polygon bodyPoly;
    Font labelFont;
    
    // Expression evaluation state
    Expr expr;
    ExprState exprState;
    String exprString;
    double lastVolts[];
    
    public DifferentiatorElm(int xx, int yy) {
        super(xx, yy);
        noDiagonal = true;
        exprString = "dadt";
        parseExpr();
        lastVolts = new double[1];
        exprState = new ExprState(1);
        setSize(sim.smallGridCheckItem.getState() ? 1 : 2);
    }
    
    public DifferentiatorElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        noDiagonal = true;
        exprString = "dadt";
        parseExpr();
        lastVolts = new double[1];
        exprState = new ExprState(1);
        setSize((f & FLAG_SMALL) != 0 ? 1 : 2);
    }
    
    void parseExpr() {
        ExprParser ep = new ExprParser(exprString);
        expr = ep.parseExpression();
    }
    
    String dump() {
        return super.dump();
    }
    
    int getDumpType() { return 259; }
    
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
        
        // Single input post on the left side
        inPost = interpPoint(point1, point2, 0, 0);
        inLead = interpPoint(lead1, lead2, 0, 0);
        
        // Create rectangular body
        Point[] pts = newPointArray(4);
        interpPoint2(lead1, lead2, pts[0], pts[1], 0, hs);
        interpPoint2(lead1, lead2, pts[3], pts[2], 1, hs);
        bodyPoly = createPolygon(pts);
        
        setBbox(point1, point2, opheight);
        labelFont = new Font("SansSerif", 0, opsize == 2 ? 14 : 10);
    }
    
    void draw(Graphics g) {
        setBbox(point1, point2, opheight);
        
        // Draw input lead
        setVoltageColor(g, volts[0]);
        drawThickLine(g, inPost, inLead);
        
        // Draw output lead
        setVoltageColor(g, volts[1]);
        drawThickLine(g, lead2, point2);
        
        // Draw body
        g.setColor(needsHighlight() ? selectColor : lightGrayColor);
        drawThickPolygon(g, bodyPoly);
        
        // Draw "d/dt" label inside the box
        g.setFont(labelFont);
        Point center = interpPoint(lead1, lead2, 0.5);
        g.setColor(needsHighlight() ? selectColor : whiteColor);
        drawCenteredText(g, "d/dt", center.x, center.y, true);
        
        // Draw current dots
        curcount = updateDotCount(current, curcount);
        drawDots(g, point2, lead2, curcount);
        drawPosts(g);
    }
    
    int getPostCount() { return 2; }
    
    Point getPost(int n) {
        if (n == 1)
            return point2;
        return inPost;
    }
    
    int getVoltageSourceCount() { return 1; }
    
    boolean nonLinear() { return true; }
    
    void stamp() {
        int vn = voltSource + sim.nodeList.size();
        sim.stampNonLinear(vn);
        sim.stampVoltageSource(0, nodes[1], voltSource);
    }
    
    double sign(double a, double b) {
        return a > 0 ? b : -b;
    }
    
    void doStep() {
        // On first timestep, initialize lastVolts to current voltages to avoid spike
        if (sim.timeStepCount == 0) {
            lastVolts[0] = volts[0];
            exprState.lastValues[0] = volts[0];
        }
        
        int vn = voltSource + sim.nodeList.size();
        if (expr != null) {
            // calculate output
            exprState.values[0] = volts[0];
            exprState.t = sim.t;
            double v0 = expr.eval(exprState);
            
            // Check output voltage convergence (relaxed threshold for derivative calculation)
            if (Math.abs(volts[1] - v0) > Math.abs(v0) * 0.01 && sim.subIterations < 100) {
                sim.converged = false;
            }
            
            double rs = v0;
            
            // calculate and stamp output derivatives
            double dv = volts[0] - lastVolts[0];
            if (Math.abs(dv) < 1e-6)
                dv = 1e-6;
            exprState.values[0] = volts[0];
            double v = expr.eval(exprState);
            exprState.values[0] = volts[0] - dv;
            double v2 = expr.eval(exprState);
            double dx = (v - v2) / dv;
            if (Math.abs(dx) < 1e-6)
                dx = sign(dx, 1e-6);
            sim.stampMatrix(vn, nodes[0], -dx);
            rs -= dx * volts[0];
            exprState.values[0] = volts[0];
            
            sim.stampRightSide(vn, rs);
        }

        lastVolts[0] = volts[0];
    }

    void stepFinished() {
        exprState.updateLastValues(volts[1]);
    }
    
    void getInfo(String arr[]) {
        arr[0] = "differentiator";
        arr[1] = "Vin = " + getVoltageText(volts[0]);
        arr[2] = "Vout = " + getVoltageText(volts[1]);
        arr[3] = "Iout = " + getCurrentText(-current);
    }
    
    // No current path through input, but output connects to ground
    boolean getConnection(int n1, int n2) { return false; }
    boolean hasGroundConnection(int n1) { return n1 == 1; }
    
    double getCurrentIntoNode(int n) {
        if (n == 1)
            return -current;
        return 0;
    }
    
    void reset() {
        super.reset();
        lastVolts = new double[1];
        if (exprState != null)
            exprState.reset();
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0)
            return EditInfo.createCheckbox("Small", (flags & FLAG_SMALL) != 0);
        return null;
    }

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
}
