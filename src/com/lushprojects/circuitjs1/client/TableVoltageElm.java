/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
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

import com.gargoylesoftware.htmlunit.javascript.host.Console;

/**
 * TableVoltageElm - A single-terminal voltage rail that gets its voltage from computed values
 * 
 * Extends RailElm for simplified single-terminal voltage source to ground.
 * References computed values by name (e.g., column sums from TableElm).
 * 
 * Key features:
 * - Only one terminal (connected to ground internally)
 * - References computed values stored in LabeledNodeElm 
 * - Updates voltage in real-time during simulation
 * - Optional DC bias offset
 * - Simplified configuration (just computed value name + bias)
 */
class TableVoltageElm extends RailElm {
    protected String computedValueName = "Col1"; // Name of computed value to reference
    
    // Constructor for new element
    public TableVoltageElm(int xx, int yy) {
        super(xx, yy, WF_DC);
        maxVoltage = 0; // Will be overridden by computed value
        bias = 0;
    }
    
    // Constructor for loading from file
    public TableVoltageElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        waveform = WF_DC; // Force DC mode
        
        // Parse computed value name if available
        try {
            if (st.hasMoreTokens()) {
                computedValueName = CustomLogicModel.unescape(st.nextToken());
            }
        } catch (Exception e) {
            computedValueName = "Col1"; // Default
        }
    }
    
    int getDumpType() { 
        return 256; // Choose unused dump type
    }
    
    String dump() {
        return super.dump() + " " + CustomLogicModel.escape(computedValueName);
    }
    
    // Override getVoltage to use computed value instead of maxVoltage
    double getVoltage() {
        // Always return computed value voltage plus bias
        Double computedVoltage = LabeledNodeElm.getComputedValue(computedValueName);
        if (computedVoltage != null) {
            Double val = computedVoltage.doubleValue() + bias;
            // CirSim.console("TableVoltageElm:" + computedValueName + " = " + computedVoltage);

            return val;
        }
        return bias; // Return just bias if computed value not found
    }
    void stamp() {

        sim.stampVoltageSource(0, nodes[0], voltSource);
    }
    // Override doStep to update voltage source with current computed value
    void doStep() {
        // RailElm.doStep() handles the voltage source update for non-DC waveforms
        // Since we always want updates (computed values can change), call it directly
        sim.updateVoltageSource(0, nodes[0], voltSource, getVoltage());
    }
    
    // Make this element nonlinear so doStep() gets called every iteration
    boolean nonLinear() { 
        return true; 
    }
    
    // Override drawRail to show computed value name instead of voltage
    void drawRail(Graphics g) {
        g.setColor(needsHighlight() ? selectColor : whiteColor);
        setPowerColor(g, false);
        
        // Show computed value name and current voltage
        double v = getVoltage();
        String s = computedValueName + ": ";
        if (Math.abs(v) < 1)
            s += showFormat.format(v) + " V";
        else
            s += getShortUnitText(v, "V");
            
        drawLabeledNode(g, s, point1, lead1);
    }
    
    void getInfo(String arr[]) {
        arr[0] = computedValueName + " (table voltage rail)";
        arr[1] = "I = " + getCurrentText(getCurrent());
        arr[2] = "V = " + getVoltageText(getVoltageDiff());
        arr[3] = "Reference: " + computedValueName;
        
        Double computedVoltage = LabeledNodeElm.getComputedValue(computedValueName);
        if (computedVoltage != null) {
            arr[4] = "Computed: " + getVoltageText(computedVoltage.doubleValue());
        } else {
            arr[4] = "Computed: (not found)";
        }
        
        if (bias != 0) {
            arr[5] = "Bias: " + getVoltageText(bias);
        }
        
        arr[6] = "P = " + getUnitText(getPower(), "W");
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Computed Value Name", 0, -1, -1);
            ei.text = computedValueName;
            return ei;
        }
        if (n == 1) {
            return new EditInfo("DC Bias (V)", bias, -20, 20);
        }
        return null;
    }
    
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            computedValueName = ei.textf.getText();
            if (computedValueName == null || computedValueName.trim().isEmpty()) {
                computedValueName = "Col1"; // Default fallback
            }
        }
        if (n == 1) {
            bias = ei.value;
        }
    }
    
    // Getter for computed value name
    public String getComputedValueName() {
        return computedValueName;
    }
    
    // Setter for computed value name  
    public void setComputedValueName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            computedValueName = name.trim();
        }
    }
}