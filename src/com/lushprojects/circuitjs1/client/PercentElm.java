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

/**
 * PercentElm - Display-only element that shows the ratio or percentage of two input voltages
 * Computes V1/V2 and displays as ratio or percentage
 * Does not affect the circuit (no stamping)
 */
class PercentElm extends CircuitElm {
    static final int FLAG_SHOWVALUE = 1;
    static final int FLAG_SHOWPERCENT = 2;  // If set, show as percentage; otherwise show as ratio
    static final int FLAG_FIXED = 4;
    static final int FLAG_ESCAPE = 8;  // Flag to indicate escaped text in dump
    
    int scale;
    Point center;
    double ratio;
    String text;  // Name/label for this element
    
    public PercentElm(int xx, int yy) {
        super(xx, yy);
        // Default: show value and show as percentage
        flags = FLAG_SHOWVALUE | FLAG_SHOWPERCENT;
        scale = SCALE_AUTO;
        text = "";  // Default empty name
    }
    
    public PercentElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        scale = SCALE_AUTO;
        text = "";
        try {
            scale = Integer.parseInt(st.nextToken());
            // Try to read the name field if it exists
            if (st.hasMoreTokens()) {
                text = st.nextToken();
                if ((flags & FLAG_ESCAPE) != 0) {
                    // New-style dump with escaped text
                    text = CustomLogicModel.unescape(text);
                } else {
                    // Old-style dump - concatenate remaining tokens with spaces
                    while (st.hasMoreTokens())
                        text += ' ' + st.nextToken();
                }
            }
        } catch (Exception e) {}
    }
    
    int getDumpType() { return 'P'; }  // Using 'P' instead of '%' which is filtered out
    
    String dump() {
        flags |= FLAG_ESCAPE;  // Mark that we're using escaped text format
        String dump = super.dump() + " " + scale;
        if (text != null && !text.isEmpty()) {
            dump += " " + CustomLogicModel.escape(text);
        }
        return dump;
    }
    
    int getPostCount() { return 2; }
    
    void setPoints() {
        super.setPoints();
        center = interpPoint(point1, point2, .5);
    }
    
    void draw(Graphics g) {
        g.save();
        int hs = 8;
        setBbox(point1, point2, hs);
        boolean selected = needsHighlight();
        double len = (selected || sim.dragElm == this || mustShowValue()) ? 16 : dn-32;
        calcLeads((int) len);
        
        // Draw leads with voltage coloring
        setVoltageColor(g, volts[0]);
        if (selected)
            g.setColor(selectColor);
        drawThickLine(g, point1, lead1);
        
        setVoltageColor(g, volts[1]);
        if (selected)
            g.setColor(selectColor);
        drawThickLine(g, lead2, point2);
        
        // Draw the division symbol or label in the center
        Font f = new Font("SansSerif", Font.BOLD, 14);
        g.setFont(f);
        g.setColor(selected ? selectColor : whiteColor);
        drawCenteredText(g, "÷", center.x, center.y, true);
        
        // Display the name if provided
        if (text != null && !text.isEmpty()) {
            g.setFont(unitsFont);
            g.setColor(whiteColor);
            // Draw name above the element
            drawCenteredText(g, text, center.x, center.y - 15, false);
        }
        
        // Display the computed value
        if (mustShowValue()) {
            String s = "";
            if (showAsPercent()) {
                // Show as percentage
                s = getUnitTextWithScale(ratio * 100, "%", scale, isFixed());
            } else {
                // Show as ratio
                s = getUnitTextWithScale(ratio, "", scale, isFixed());
            }
            drawValues(g, s, 4);
        }
        
        // Draw +/- labels
        g.setColor(whiteColor);
        g.setFont(unitsFont);
        Point plusPoint1 = interpPoint(point1, point2, (dn/2-len/2-4)/dn, -10*dsign);
        Point plusPoint2 = interpPoint(point1, point2, (dn/2+len/2+4)/dn, -10*dsign);
        
        if (y2 > y) {
            plusPoint1.y += 4;
            plusPoint2.y += 4;
        }
        if (y > y2) {
            plusPoint1.y += 3;
            plusPoint2.y += 3;
        }
        
        int w1 = (int)g.context.measureText("+").getWidth();
        int w2 = (int)g.context.measureText("-").getWidth();
        g.drawString("+", plusPoint1.x-w1/2, plusPoint1.y);
        g.drawString("-", plusPoint2.x-w2/2, plusPoint2.y);
        
        drawPosts(g);
        g.restore();
    }
    
    boolean mustShowValue() {
        return (flags & FLAG_SHOWVALUE) != 0;
    }
    
    boolean showAsPercent() {
        return (flags & FLAG_SHOWPERCENT) != 0;
    }
    
    boolean isFixed() {
        return (flags & FLAG_FIXED) != 0;
    }
    
    void stepFinished() {
        // Calculate the ratio V1/V2
        double v1 = volts[0];
        double v2 = volts[1];
        
        // Avoid division by zero
        if (Math.abs(v2) < 1e-12) {
            ratio = 0;
        } else {
            ratio = v1 / v2;
        }
    }
    
    void stamp() {
        // No stamping - this is a display-only element
        // Acts like an open circuit (infinite impedance)
    }
    
    void getInfo(String arr[]) {
        arr[0] = "ratio/percent meter";
        arr[1] = "V1 = " + getVoltageText(volts[0]);
        arr[2] = "V2 = " + getVoltageText(volts[1]);
        if (showAsPercent()) {
            arr[3] = "Ratio = " + getUnitText(ratio * 100, "%");
        } else {
            arr[3] = "Ratio = " + ratio;
        }
    }
    
    boolean getConnection(int n1, int n2) { 
        // No connection between terminals (open circuit)
        return false; 
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Name", 0, -1, -1);
            ei.text = text;
            return ei;
        }
        if (n == 1) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Show Value", mustShowValue());
            return ei;
        }
        if (n == 2) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Show as Percentage", showAsPercent());
            return ei;
        }
        if (n == 3) {
            EditInfo ei = new EditInfo("Scale", 0);
            ei.choice = new Choice();
            ei.choice.add("Auto");
            ei.choice.add("m");
            ei.choice.add("1");
            ei.choice.add("K");
            ei.choice.add("M");
            ei.choice.select(scale);
            return ei;
        }
        if (n == 4) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Fixed Scale", isFixed());
            return ei;
        }
        return null;
    }
    
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            text = ei.textf.getText();
        }
        if (n == 1) {
            flags = ei.changeFlag(flags, FLAG_SHOWVALUE);
        }
        if (n == 2) {
            flags = ei.changeFlag(flags, FLAG_SHOWPERCENT);
        }
        if (n == 3) {
            scale = ei.choice.getSelectedIndex();
            ei.newDialog = true;
        }
        if (n == 4) {
            flags = ei.changeFlag(flags, FLAG_FIXED);
        }
    }
    
    // This element doesn't need nodes (it's display only)
    // But we still need to call super to get node allocation
    // The element will have high impedance (open circuit)
    
    double getVoltageDiff() {
        return volts[0] - volts[1];
    }
    
    // No power dissipation (open circuit)
    double getPower() { 
        return 0; 
    }
    
    // Override scope methods to display the computed ratio instead of voltage difference
    @Override
    double getScopeValue(int x) {
        // For voltage plot, return the computed ratio
        // For current, return 0 (no current flows through this element)
        // For power, return 0 (no power dissipation)
        if (x == Scope.VAL_CURRENT)
            return 0;
        if (x == Scope.VAL_POWER)
            return 0;
        // Default case (VAL_VOLTAGE) - return the ratio
        if (showAsPercent())
            return ratio * 100;  // Show as percentage
        else
            return ratio;  // Show as ratio
    }
    
    @Override
    int getScopeUnits(int x) {
        // For current/power, use default units
        if (x == Scope.VAL_CURRENT)
            return Scope.UNITS_A;
        if (x == Scope.VAL_POWER)
            return Scope.UNITS_W;
        // For voltage (default), return V units
        // The scope will append units based on this
        return Scope.UNITS_V;
    }
    
    @Override
    String getScopeText(int v) {
        // If a name is provided, use it; otherwise use default label
        if (text != null && !text.isEmpty()) {
            return text;
        }
        if (showAsPercent())
            return "Percent Meter (%)";
        else
            return "Ratio Meter";
    }
    
    String getName() { 
        return text != null ? text : ""; 
    }
}
