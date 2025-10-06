/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/
// Custom element for CircuitJS1: Voltage Controlled Voltage Source using named (numbered) nodes for input
// Only has output pins; input nodes specified by their indices (enable View > Show Nodes to see numbers)
// Based on VCVSElm.java
package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.LabeledNodeElm.LabelEntry;

// import com.google.gwt.canvas.client.Canvas;
// import com.lushprojects.circuitjs1.client.util.Locale;
// package com.lushprojects.circuitjs1.client;

// import java.awt.*;
// import java.util.StringTokenizer;
import com.lushprojects.circuitjs1.client.util.Locale;

class NamedVCVSElm extends MultiplyElm {
    final int FLAG_INVERT = 2;
    int inNodeP;
    int inNodeN;
    double gain;
    boolean isInverting;
    Font gfont;

    public NamedVCVSElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        // inNodeP = Integer.parseInt(st.nextToken());
        // inNodeN = Integer.parseInt(st.nextToken());
        gain = Double.parseDouble(st.nextToken());
        setupPins();
        setup();
    }

    public NamedVCVSElm(int xx, int yy) {
        super(xx, yy);
        // inNodeP = 0;
        // inNodeN = 0;
        gain = 1.0;
        setupPins();
        setup();
    }

    void setupPins() {
        sizeX = 2;
        sizeY = 2;
        pins = new Pin[2];
        pins[0] = new Pin(0, SIDE_W, "+");
        pins[1] = new Pin(0, SIDE_E, "-");
        pins[1].output = true;
        allocNodes();
    }

    void setup() {
        isInverting = (flags & FLAG_INVERT) != 0;
        if (isInverting) {
            flags &= ~FLAG_INVERT;
            gain = -Math.abs(gain);
        } else {
            gain = Math.abs(gain);
        }
        if (gfont == null) {
            gfont = new Font("SansSerif", Font.BOLD, 20);
        }
    }

    String getChipName() { return "Named VCVS"; }

    int getPostCount() { return 2; }
    int getVoltageSourceCount() { return 1; }
    int getDumpType() { return 172; }

    boolean hasCurrentOutput() { return false; }

    void stamp() {
        sim.stampVoltageSource(nodes[0], nodes[1], pins[1].voltSource);
    }

    void doStep() {
        LabelEntry entry = LabeledNodeElm.labelList.get("myLabel");
        if (entry != null) {
            int nodeIndex = entry.node;
            // use nodeIndex as needed
            // get the voltage at that node
            if (nodeIndex >= 0 && nodeIndex < sim.nodeList.size()) {
            }

        }
        double vin = 0;
        if (inNodeP >= 0 && inNodeP < sim.nodeList.size() && inNodeN >= 0 && inNodeN < sim.nodeList.size()) {
            // vin = sim.nodeList.get(inNodeP).voltage - sim.nodeList.get(inNodeN).voltage;
            vin = 0.0;
        }
        double vout = gain * vin;
        sim.updateVoltageSource(pins[1].voltSource, nodes[0], nodes[1], vout);
    }

    void stepFinished() {
        // No state to update for this element
    }

    void draw(Graphics g) {
        drawChip(g);
        String label = "VCVS";
        int mid_x = (rectPointsX[0] + rectPointsX[1] + rectPointsX[2] + rectPointsX[3]) / 4;
        int mid_y = (rectPointsY[0] + rectPointsY[1] + rectPointsY[2] + rectPointsY[3]) / 4;
        boolean selected = needsHighlight();
        g.setFont(gfont);
        g.setColor(selected ? selectColor : whiteColor);
        drawCenteredText(g, label, mid_x, mid_y, true);
        g.restore();
    }

    void setCurrent(int vn, double c) {
        if (pins[1].voltSource == vn) {
            pins[1].current = c;
        }
    }

    public String dump() {
        return super.dump() + " " + inNodeP + " " + inNodeN + " " + gain;
    }

    public EditInfo getChipEditInfo(int n) {
        if (n == 0)
            return new EditInfo("Input + Node #", inNodeP, 0, sim.nodeList.size()).setDimensionless();
        if (n == 1)
            return new EditInfo("Input - Node #", inNodeN, 0, sim.nodeList.size()).setDimensionless();
        if (n == 2)
            return new EditInfo("Gain", gain, -1000, 1000);
        // if (n == 3)
        //     return new EditInfo("Invert Output", isInverting).checkbox();
        return null;
    }

    public void setChipEditValue(int n, EditInfo ei) {
        if (n == 0) {
            inNodeP = (int) ei.value;
        } else if (n == 1) {
            inNodeN = (int) ei.value;
        } else if (n == 2) {
            gain = ei.value;
            setup();
        // } else if (n == 3) {
        //     isInverting = ei.checkbox.isSelected();
        //     setup();
        }
        // sim.setChanged(true); // Removed because CirSim does not define setChanged(boolean)
    }
}