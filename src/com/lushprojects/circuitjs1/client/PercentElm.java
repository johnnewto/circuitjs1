/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client;

// Percent element - computes (V1 / V2 / V3 / ...) * 100
// Outputs the percentage value (nonlinear, requires iteration)
class PercentElm extends CircuitElm {
    int inputCount;
    int opsize, opheight, opwidth;
    final int FLAG_SMALL = 2;
    Point inPosts[], inLeads[];
    Polygon bodyPoly;
    Font labelFont;
    double lastVolts[];
    
    // Minimum denominator value to prevent numerical instability
    static final double MIN_DENOMINATOR = 1e-6;
    // Maximum derivative magnitude to prevent solver instability
    static final double MAX_DERIVATIVE = 1e6;
    
    public PercentElm(int xx, int yy) {
        super(xx, yy);
        noDiagonal = true;
        inputCount = 2;
        setSize(sim.smallGridCheckItem.getState() ? 1 : 2);
        lastVolts = new double[inputCount];
    }
    
    public PercentElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        noDiagonal = true;
        inputCount = Integer.parseInt(st.nextToken());
        setSize((f & FLAG_SMALL) != 0 ? 1 : 2);
        lastVolts = new double[inputCount];
    }
    
    String dump() {
        return super.dump() + " " + inputCount;
    }
    
    int getDumpType() { return 'P'; }
    
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
        
        // Calculate input positions - input 0 (numerator) at top, input 1+ (denominators) below
        // Use negative i0 multiplier so input 0 gets positive offset (top position)
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
        
        // Draw "%" label
        g.setFont(labelFont);
        Point center = interpPoint(lead1, lead2, 0.5);
        drawCenteredText(g, "%", center.x, center.y, true);
        
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
    
    void stamp() {
        // Nonlinear voltage source for output - use same pattern as VCVSElm
        int vn = sim.nodeList.size() + voltSource;
        sim.stampNonLinear(vn);
        sim.stampVoltageSource(0, nodes[inputCount], voltSource);
     }
    
    double sign(double a, double b) {
        return a > 0 ? b : -b;
    }
    
    void doStep() {
        int vn = sim.nodeList.size() + voltSource;
        
        // Check for divide by zero on denominators (inputs 1+)
        boolean divByZero = false;
        for (int i = 1; i < inputCount; i++) {
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
        
        // Calculate output: (V1 / V2 / V3 / ...) * 100
        double v0 = volts[0];
        for (int i = 1; i < inputCount; i++)
            v0 /= volts[i];
        v0 *= 100;
        
        // Check convergence
        double outputDelta = Math.abs(volts[inputCount] - v0);
        double tolerance = Math.max(Math.abs(v0) * 0.01, 1e-9);
        if (outputDelta > tolerance && sim.subIterations < 100)
            sim.converged = false;
        
        double rs = v0;
        
        // Calculate and stamp output derivatives for each input
        for (int i = 0; i < inputCount; i++) {
            double dv = volts[i] - lastVolts[i];
            if (Math.abs(dv) < 1e-6)
                dv = 1e-6;
            
            // Calculate partial derivative dV0/dVi numerically
            double vPlus = volts[0];
            double vMinus = volts[0];
            for (int j = 1; j < inputCount; j++) {
                if (j == i) {
                    vPlus /= volts[j];
                    // Guard against divide by zero in vMinus calculation
                    double denom = volts[j] - dv;
                    if (Math.abs(denom) < MIN_DENOMINATOR)
                        denom = sign(denom, MIN_DENOMINATOR);
                    vMinus /= denom;
                } else {
                    // Guard against divide by zero for all denominators
                    double denom = volts[j];
                    if (Math.abs(denom) < MIN_DENOMINATOR)
                        denom = sign(denom, MIN_DENOMINATOR);
                    vPlus /= denom;
                    vMinus /= denom;
                }
            }
            // For input 0 (numerator)
            if (i == 0) {
                vPlus = volts[0];
                vMinus = volts[0] - dv;
                for (int j = 1; j < inputCount; j++) {
                    // Guard against divide by zero for all denominators
                    double denom = volts[j];
                    if (Math.abs(denom) < MIN_DENOMINATOR)
                        denom = sign(denom, MIN_DENOMINATOR);
                    vPlus /= denom;
                    vMinus /= denom;
                }
            }
            vPlus *= 100;
            vMinus *= 100;
            
            double dx = (vPlus - vMinus) / dv;
            
            // Clamp derivative to prevent solver instability
            if (Math.abs(dx) < 1e-6)
                dx = sign(dx, 1e-6);
            if (Math.abs(dx) > MAX_DERIVATIVE)
                dx = sign(dx, MAX_DERIVATIVE);
            
            sim.stampMatrix(vn, nodes[i], -dx);
            rs -= dx * volts[i];
        }
        
        // stampVoltageSource already stamped +1 for output node, so just stamp right side
        sim.stampRightSide(vn, rs);
        
        // Save last voltages
        for (int i = 0; i < inputCount; i++)
            lastVolts[i] = volts[i];
    }
    
    void getInfo(String arr[]) {
        arr[0] = "percent";
        arr[1] = "Vout = (";
        for (int i = 0; i < inputCount; i++) {
            if (i > 0) arr[1] += " \u00f7 ";
            arr[1] += getVoltageText(volts[i]);
        }
        arr[1] += ") \u00d7 100";
        arr[2] = "Vout = " + getVoltageText(volts[inputCount]);
        arr[3] = "Iout = " + getCurrentText(-current);
    }
    
    // No current path through inputs, but output connects to ground
    boolean getConnection(int n1, int n2) { return false; }
    boolean hasGroundConnection(int n1) { return n1 == inputCount; }
    
    double getCurrentIntoNode(int n) {
        if (n == inputCount)
            return -current;
        return 0;
    }
    
    void reset() {
        super.reset();
        lastVolts = new double[inputCount];
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0)
            return new EditInfo("Number of Inputs", inputCount, 2, 8).setDimensionless();
        if (n == 1)
            return EditInfo.createCheckbox("Small", (flags & FLAG_SMALL) != 0);
        return null;
    }
    
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            if (ei.value < 2 || ei.value > 8)
                return;
            inputCount = (int) ei.value;
            lastVolts = new double[inputCount];
            allocNodes();
            setPoints();
        }
        if (n == 1) {
            flags = ei.changeFlag(flags, FLAG_SMALL);
            boolean small = (flags & FLAG_SMALL) != 0;
            setSize(small ? 1 : 2);
            sim.smallGridCheckItem.setState(small);
            sim.setGrid();
            setPoints();
        }
    }
}
