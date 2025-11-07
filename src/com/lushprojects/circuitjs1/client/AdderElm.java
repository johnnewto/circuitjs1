/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

// Linear adder element - adds multiple input voltages
// Vout = V1 + V2 + V3 + ... using VCVS (linear element, no iteration needed)
class AdderElm extends ChipElm {
    int inputCount;
    
    public AdderElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        inputCount = Integer.parseInt(st.nextToken());
        setupPins();
    }
    
    public AdderElm(int xx, int yy) {
        super(xx, yy);
        inputCount = 2;
        setupPins();
        // Set to small size by default
        flags |= FLAG_SMALL;
        setSize(1);
        setPoints();
    }
    
    String dump() {
        return super.dump() + " " + inputCount;
    }
    
    void setupPins() {
        sizeX = 2;
        sizeY = inputCount > 2 ? inputCount : 2;
        pins = new Pin[inputCount + 1];
        int i;
        for (i = 0; i != inputCount; i++) {
            pins[i] = new Pin(i, SIDE_W, "");
        }
        pins[inputCount] = new Pin(0, SIDE_E, "");
        pins[inputCount].output = true;
        allocNodes();
    }
    
    String getChipName() { return "Σ"; }
    
    boolean nonLinear() { return false; }
    
    @Override boolean isDigitalChip() { return false; }
    
    void stamp() {
        // This is a voltage-controlled voltage source (VCVS)
        // Vout = V1 + V2 + V3 + ...
        // Create voltage source at output
        sim.stampVoltageSource(nodes[inputCount], 0, pins[inputCount].voltSource);
        
        // Add control from each input with coefficient 1.0
        for (int i = 0; i < inputCount; i++) {
            sim.stampVCVS(nodes[i], 0, 1.0, pins[inputCount].voltSource);
        }
    }
    
    void drawLabel(Graphics g, int x, int y) {
        g.save();
        Font f = new Font("SansSerif", 0, 30);
        g.setFont(f);
        g.context.setTextBaseline("middle");
        g.context.setTextAlign("center");
        g.setColor(needsHighlight() ? selectColor : whiteColor);
        g.drawString("+", x, y);
        g.restore();
    }

    int getDumpType() { return 251; }
    
    int getPostCount() { return inputCount + 1; }
    
    int getVoltageSourceCount() { return 1; }
    
    boolean hasCurrentOutput() { return false; }
    
    void setCurrent(int vn, double c) {
        if (pins[inputCount].voltSource == vn) {
            pins[inputCount].current = c;
        }
    }
    
    void getInfo(String arr[]) {
        arr[0] = "adder";
        arr[1] = "Vout = ";
        for (int i = 0; i < inputCount; i++) {
            if (i > 0) arr[1] += " + ";
            arr[1] += getVoltageText(volts[i]);
        }
        arr[2] = "Vout = " + getVoltageText(volts[inputCount]);
    }
    
    public EditInfo getChipEditInfo(int n) {
        if (n == 0)
            return new EditInfo("Number of Inputs", inputCount, 2, 8);
        if (n == 1) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Small Size", (flags & FLAG_SMALL) != 0);
            return ei;
        }
        return null;
    }
    
    public void setChipEditValue(int n, EditInfo ei) {
        if (n == 0) {
            if (ei.value < 2 || ei.value > 8)
                return;
            inputCount = (int) ei.value;
            setupPins();
            allocNodes();
            setPoints();
        }
        if (n == 1) {
            flags = ei.changeFlag(flags, FLAG_SMALL);
            setSize((flags & FLAG_SMALL) != 0 ? 1 : 2);
            setupPins();
            allocNodes();
            setPoints();
        }
    }
}