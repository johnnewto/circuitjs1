/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

// Linear subtracter element - subtracts input voltages from first input
// Vout = V1 - V2 - V3 - ... using VCVS (linear element, no iteration needed)
class SubtracterElm extends AdderElm {
    public SubtracterElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
    }
    
    public SubtracterElm(int xx, int yy) {
        super(xx, yy);
    }

    int getDumpType() { return 252; }
    
    String getChipName() { return "Σ-"; }
    
    void stamp() {
        // This is a voltage-controlled voltage source (VCVS)
        // Vout = V1 - V2 - V3 - ... = V1 + (-1)×V2 + (-1)×V3 + ...
        // Create voltage source at output
        sim.stampVoltageSource(nodes[inputCount], 0, pins[inputCount].voltSource);
        
        // Add control from first input with coefficient +1.0
        sim.stampVCVS(nodes[0], 0, 1.0, pins[inputCount].voltSource);
        
        // Subtract all other inputs with coefficient -1.0
        for (int i = 1; i < inputCount; i++) {
            sim.stampVCVS(nodes[i], 0, -1.0, pins[inputCount].voltSource);
        }
    }
    
    void drawLabel(Graphics g, int x, int y) {
        g.save();
        Font f = new Font("SansSerif", 0, 30);
        g.setFont(f);
        g.context.setTextBaseline("middle");
        g.context.setTextAlign("center");
        g.setColor(needsHighlight() ? selectColor : whiteColor);
        g.drawString("−", x, y);  // Use proper minus sign character
        g.restore();
    }
    
    void getInfo(String arr[]) {
        arr[0] = "subtracter";
        arr[1] = "Vout = " + getVoltageText(volts[0]);
        for (int i = 1; i < inputCount; i++) {
            arr[1] += " - " + getVoltageText(volts[i]);
        }
        arr[2] = "Vout = " + getVoltageText(volts[inputCount]);
    }
}