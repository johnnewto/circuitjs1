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

// Multiply input by a constant - this is a linear element (VCVS with fixed gain)
class MultiplyConstElm extends ChipElm {
    static final int FLAG_SHOWPERCENT = 2;  // If set, show as percentage; otherwise show as multiplier
    double gain;
    String elmName;
    
    public MultiplyConstElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        gain = Double.parseDouble(st.nextToken());
        elmName = CustomLogicModel.unescape(st.nextToken());
        setupPins();
    }
    
    public MultiplyConstElm(int xx, int yy) {
        super(xx, yy);
        gain = 1.0;
        elmName = "";
        setupPins();
        // Set to small size by default
        flags |= FLAG_SMALL;
        setSize(1);
        setPoints();
    }
    
    String dump() {
        return super.dump() + " " + gain + " " + CustomLogicModel.escape(elmName);
    }
    
    void setupPins() {
        sizeX = 2;
        sizeY = 1;  // Half height since we only have 1 input
        pins = new Pin[2];
        pins[0] = new Pin(0, SIDE_W, "");
        pins[1] = new Pin(0, SIDE_E, "");
        pins[1].output = true;
        allocNodes();
    }
    
    String getChipName() { return "×K"; }
    
    boolean nonLinear() { return false; }
    
    @Override boolean isDigitalChip() { return false; }
    
    void stamp() {
        // This is a voltage-controlled voltage source (VCVS)
        // V_out = gain * V_in
        // For very small gains (< 1e-9), clamp to avoid numerical issues
        double effectiveGain = gain;
        if (Math.abs(effectiveGain) < 1e-9)
            effectiveGain = (effectiveGain >= 0) ? 1e-9 : -1e-9;
        
        // stampVoltageSource creates the voltage source from ground to output
        sim.stampVoltageSource(nodes[1], 0, pins[1].voltSource);
        // stampVCVS makes it controlled by the input voltage with gain coefficient
        sim.stampVCVS(nodes[0], 0, effectiveGain, pins[1].voltSource);
    }
    
    void drawLabel(Graphics g, int x, int y) {
        g.save();
        Font f = new Font("SansSerif", 0, 20);  // 2x larger font
        g.setFont(f);
        g.context.setTextBaseline("middle");
        g.context.setTextAlign("center");
        g.setColor(needsHighlight() ? selectColor : whiteColor);
        g.drawString("×", x, y);
        g.restore();
    }
    
    void draw(Graphics g) {
        drawChip(g);
        
        // Calculate bottom center position for text
        int bottom_x = (rectPointsX[0] + rectPointsX[1] + rectPointsX[2] + rectPointsX[3]) / 4;
        int bottom_y = Math.max(Math.max(rectPointsY[0], rectPointsY[1]), 
                                Math.max(rectPointsY[2], rectPointsY[3])) + 8;
        
        boolean selected = needsHighlight();
        Font f = new Font("SansSerif", selected ? Font.BOLD : 0, 10);
        g.setFont(f);
        g.setColor(selected ? selectColor : whiteColor);
        
        // Show the name and gain value at the bottom
        String label;
        if (elmName != null && elmName.length() > 0) {
            if (showAsPercent()) {
                label = elmName + " (×" + getUnitText(gain * 100, "%") + ")";
            } else {
                label = elmName + " (×" + getUnitText(gain, "") + ")";
            }
        } else {
            if (showAsPercent()) {
                label = "×" + getUnitText(gain * 100, "%");
            } else {
                label = "×" + getUnitText(gain, "");
            }
        }
        drawCenteredText(g, label, bottom_x, bottom_y, true);
        
        g.restore();
    }
    
    int getPostCount() { return 2; }
    
    int getVoltageSourceCount() { return 1; }
    
    int getDumpType() { return 258; }
    
    boolean hasCurrentOutput() { return false; }
    
    boolean showAsPercent() {
        return (flags & FLAG_SHOWPERCENT) != 0;
    }
    
    void setCurrent(int vn, double c) {
        if (pins[1].voltSource == vn) {
            pins[1].current = c;
        }
    }
    
    void getInfo(String arr[]) {
        arr[0] = "multiply by constant";
        if (elmName != null && elmName.length() > 0)
            arr[1] = "name = " + elmName;
        else
            arr[1] = "Vin = " + getVoltageText(volts[0]);
        arr[2] = "Vin = " + getVoltageText(volts[0]);
        arr[3] = "Vout = " + getVoltageText(volts[1]);
        if (showAsPercent()) {
            arr[4] = "gain = " + getUnitText(gain * 100, "%");
        } else {
            arr[4] = "gain = " + gain;
        }
        if (Math.abs(gain) < 1e-9)
            arr[5] = "warning: gain clamped to 1e-9";
    }
    
    public EditInfo getChipEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Name", 0, -1, -1);
            ei.text = elmName;
            return ei;
        }
        if (n == 1)
            return new EditInfo("Gain (Multiplier)", gain, -100, 100);
        if (n == 2) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Show as Percentage", showAsPercent());
            return ei;
        }
        if (n == 3) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Small Size", (flags & FLAG_SMALL) != 0);
            return ei;
        }
        return null;
    }
    
    public void setChipEditValue(int n, EditInfo ei) {
        if (n == 0) {
            elmName = ei.textf.getText();
        }
        if (n == 1) {
            gain = ei.value;
        }
        if (n == 2) {
            flags = ei.changeFlag(flags, FLAG_SHOWPERCENT);
        }
        if (n == 3) {
            flags = ei.changeFlag(flags, FLAG_SMALL);
            setSize((flags & FLAG_SMALL) != 0 ? 1 : 2);
            setupPins();
            allocNodes();
            setPoints();
        }
    }
}
