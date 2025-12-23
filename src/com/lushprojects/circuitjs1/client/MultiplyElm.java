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

// Multiplier element - multiplies input voltages
// Vout = V1 * V2 * ... (nonlinear, requires iteration)
class MultiplyElm extends CircuitElm {
    int inputCount;
    int opsize, opheight, opwidth;
    final int FLAG_SMALL = 2;
    Point inPosts[], inLeads[];
    Polygon bodyPoly;
    Font labelFont;
    
    // Minimum value for convergence tolerance
    static final double MIN_VALUE = 1e-9;
    
    public MultiplyElm(int xx, int yy) {
        super(xx, yy);
        noDiagonal = true;
        inputCount = 2;
        setSize(sim.smallGridCheckItem.getState() ? 1 : 2);
    }
    
    public MultiplyElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        noDiagonal = true;
        inputCount = Integer.parseInt(st.nextToken());
        setSize((f & FLAG_SMALL) != 0 ? 1 : 2);
    }
    
    String dump() {
        return super.dump() + " " + inputCount;
    }
    
    int getDumpType() { return 250; }
    
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
        
        // Calculate input positions like GateElm does
        int i0 = -inputCount/2;
        for (int i = 0; i != inputCount; i++, i0++) {
            if (i0 == 0 && (inputCount & 1) == 0)
                i0++;
            inPosts[i] = interpPoint(point1, point2, 0, hs * i0);
            inLeads[i] = interpPoint(lead1, lead2, 0, hs * i0);
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
        
        // Draw "×" label
        g.setFont(labelFont);
        Point center = interpPoint(lead1, lead2, 0.5);
        drawCenteredText(g, "\u00d7", center.x, center.y, true);
        
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
        // Nonlinear voltage source for output
        int vn = sim.nodeList.size() + voltSource;
        sim.stampNonLinear(vn);
        sim.stampVoltageSource(0, nodes[inputCount], voltSource);
    }
    
    void doStep() {
        int vn = sim.nodeList.size() + voltSource;
        
        // Calculate output: V1 * V2 * ...
        double v0 = 1;
        for (int i = 0; i < inputCount; i++)
            v0 *= volts[i];
        
        // Check convergence with minimum threshold to avoid false failures near zero
        double outputDelta = Math.abs(volts[inputCount] - v0);
        double tolerance = Math.max(Math.abs(v0) * 0.001, MIN_VALUE);
        if (outputDelta > tolerance && sim.subIterations < 100)
            sim.converged = false;
        
        // Stamp the output directly - high-impedance inputs don't need derivative linearization
        sim.stampRightSide(vn, v0);
    }
    
    void getInfo(String arr[]) {
        arr[0] = "multiplier";
        arr[1] = "Vout = ";
        for (int i = 0; i < inputCount; i++) {
            if (i > 0) arr[1] += " \u00d7 ";
            arr[1] += getVoltageText(volts[i]);
        }
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
            allocNodes();
            setPoints();
        }
        if (n == 1) {
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
