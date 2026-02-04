/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * ComputedValueSourceElm - Bridge element from ComputedValues to circuit
 * 
 * This element reads a named value from the ComputedValues registry and
 * outputs it as a voltage. It acts as a bridge between pure computational
 * elements (like GodlyTableElm, EquationTableElm) and the electrical circuit.
 * 
 * Use cases:
 * - Connect a stock value from a Godley table to a scope
 * - Wire a computed value to other circuit elements
 * - Probe values that exist only in ComputedValues
 * 
 * The element has one output post that drives a voltage equal to the
 * named computed value.
 */
class ComputedValueSourceElm extends CircuitElm {
    
    /** Name of the value to read from ComputedValues */
    private String valueName = "";
    
    /** Last read value (for display and convergence) */
    private double lastValue = 0;
    
    /** Default value when named value doesn't exist */
    private double defaultValue = 0;
    
    // Drawing constants
    private static final int BODY_LEN = 28;
    private static final int FLAG_SHOW_VALUE = 1;
    
    /**
     * Constructor for creating from menu
     */
    public ComputedValueSourceElm(int xx, int yy) {
        super(xx, yy);
        valueName = "value";
        flags |= FLAG_SHOW_VALUE;
    }
    
    /**
     * Constructor for loading from file
     */
    public ComputedValueSourceElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        
        if (st.hasMoreTokens()) {
            valueName = CustomLogicModel.unescape(st.nextToken());
        }
        if (st.hasMoreTokens()) {
            try {
                defaultValue = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                defaultValue = 0;
            }
        }
    }
    
    @Override
    int getDumpType() { return 267; }
    
    @Override
    String dump() {
        return super.dump() + " " + CustomLogicModel.escape(valueName) + " " + defaultValue;
    }
    
    @Override
    int getPostCount() { return 1; }
    
    @Override
    int getVoltageSourceCount() { return 1; }
    
    @Override
    boolean nonLinear() { return true; }
    
    @Override
    void setVoltageSource(int j, int vs) {
        voltSource = vs;
    }
    
    // Geometry
    Point textPos;
    
    @Override
    void setPoints() {
        super.setPoints();
        
        // Calculate text position (above the element)
        int textX = (point1.x + point2.x) / 2;
        int textY = Math.min(point1.y, point2.y) - 8;
        textPos = new Point(textX, textY);
        
        // Set bounding box
        setBbox(point1, point2, BODY_LEN);
        adjustBbox(textPos.x - 20, textPos.y - 10, textPos.x + 20, textPos.y + 5);
    }
    
    @Override
    void stamp() {
        int vn = voltSource + sim.nodeList.size();
        
        // Mark as nonlinear since value changes each step
        sim.stampNonLinear(vn);
        
        // Stamp voltage source from ground to output node
        sim.stampVoltageSource(0, nodes[0], voltSource);
        
        // Add conditioning resistor for matrix stability
        sim.stampResistor(nodes[0], 0, 1e8);
    }
    
    @Override
    void doStep() {
        // Read value from ComputedValues registry
        Double value = ComputedValues.getComputedValue(valueName);
        
        if (value != null) {
            lastValue = value;
        } else {
            lastValue = defaultValue;
        }
        
        // Check convergence
        double voltageDiff = Math.abs(volts[0] - lastValue);
        double threshold = Math.max(Math.abs(lastValue) * 0.001, 1e-6);
        if (voltageDiff > threshold && sim.subIterations < 100) {
            sim.converged = false;
        }
        
        // Stamp the value to drive output voltage
        int vn = voltSource + sim.nodeList.size();
        sim.stampRightSide(vn, lastValue);
    }
    
    @Override
    void draw(Graphics g) {
        int hs = 10;
        setBbox(point1, point2, hs);
        
        // Draw leads
        setVoltageColor(g, volts[0]);
        drawThickLine(g, point1, point2);
        
        // Draw circle at center
        int circleX = (point1.x + point2.x) / 2;
        int circleY = (point1.y + point2.y) / 2;
        int radius = 8;
        
        // Draw filled circle (background)
        g.setColor(needsHighlight() ? selectColor : whiteColor);
        g.fillOval(circleX - radius, circleY - radius, radius * 2, radius * 2);
        
        // Draw value name above
        g.setColor(needsHighlight() ? selectColor : whiteColor);
        g.setFont(unitsFont);
        String displayName = valueName.length() > 12 ? valueName.substring(0, 12) + "…" : valueName;
        drawCenteredText(g, displayName, circleX, circleY - radius - 4, true);
        
        // Draw current value below if flag set
        if ((flags & FLAG_SHOW_VALUE) != 0) {
            String valueStr = getShortUnitText(lastValue, "");
            drawCenteredText(g, valueStr, circleX, circleY + radius + 12, true);
        }
        
        drawPosts(g);
    }
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "Computed Value Source";
        arr[1] = "Name: " + valueName;
        arr[2] = "V = " + getVoltageText(lastValue);
        
        // Check if value exists
        Double value = ComputedValues.getComputedValue(valueName);
        if (value == null) {
            arr[3] = "⚠ Value not found (using default)";
            arr[4] = "Default: " + getVoltageText(defaultValue);
        }
    }
    
    @Override
    double getVoltageDiff() {
        return volts[0];
    }
    
    @Override
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Value Name", 0);
            ei.text = valueName;
            return ei;
        }
        if (n == 1) {
            return new EditInfo("Default Value (if not found)", defaultValue);
        }
        if (n == 2) {
            EditInfo ei = EditInfo.createCheckbox("Show Value", (flags & FLAG_SHOW_VALUE) != 0);
            return ei;
        }
        return null;
    }
    
    @Override
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            valueName = ei.textf.getText().trim();
        }
        if (n == 1) {
            defaultValue = ei.value;
        }
        if (n == 2) {
            flags = ei.changeFlag(flags, FLAG_SHOW_VALUE);
        }
    }
    
    /**
     * Get the current value being output
     */
    public double getValue() {
        return lastValue;
    }
    
    /**
     * Get the name of the value being read
     */
    public String getValueName() {
        return valueName;
    }
}
