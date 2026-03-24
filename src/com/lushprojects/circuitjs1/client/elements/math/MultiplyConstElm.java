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

package com.lushprojects.circuitjs1.client.elements.math;

import com.lushprojects.circuitjs1.client.ui.EditInfo;

import com.lushprojects.circuitjs1.client.ui.Checkbox;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;

// Multiply input by a constant - this is a linear element (VCVS with fixed gain)
public class MultiplyConstElm extends CircuitElm {
    private static final int FLAG_SHOWPERCENT = 2;  // If set, show as percentage; otherwise show as multiplier
    private final int FLAG_SMALL = 1;
    
    private double gain;
    private String elmName;
    private int opsize;
    private int opheight;
    private int opwidth;
    private Point inPost;
    private Point inLead;
    private Polygon bodyPoly;
    private Font labelFont;
    
    public MultiplyConstElm(int xx, int yy) {
        super(xx, yy);
        noDiagonal = true;
        gain = 1.0;
        elmName = "";
        setSize(sim.smallGridCheckItem.getState() ? 1 : 2);
    }
    
    public MultiplyConstElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        noDiagonal = true;
        gain = Double.parseDouble(st.nextToken());
        elmName = CustomLogicModel.unescape(st.nextToken());
        setSize((f & FLAG_SMALL) != 0 ? 1 : 2);
    }
    
    protected String dump() {
        return super.dump() + " " + gain + " " + CustomLogicModel.escape(elmName);
    }
    
    protected int getDumpType() { return 258; }
    
    private void setSize(int s) {
        opsize = s;
        opheight = 8 * s;
        opwidth = 13 * s;
        flags = (flags & ~FLAG_SMALL) | ((s == 1) ? FLAG_SMALL : 0);
    }
    
    protected void setPoints() {
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
    
    protected void draw(Graphics g) {
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
        
        // Draw "×" label inside the box
        g.setFont(labelFont);
        Point center = interpPoint(lead1, lead2, 0.5);
        g.setColor(needsHighlight() ? selectColor : whiteColor);
        drawCenteredText(g, "×", center.x, center.y, true);
        
        // Draw the gain/name label below the box
        boolean selected = needsHighlight();
        Font f = new Font("SansSerif", selected ? Font.BOLD : 0, opsize == 2 ? 12 : 10);
        g.setFont(f);
        g.setColor(selected ? selectColor : whiteColor);
        
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
        int labelY = center.y + opheight + 8;
        drawCenteredText(g, label, center.x, labelY, true);
        
        // Draw current dots
        curcount = updateDotCount(current, curcount);
        drawDots(g, point2, lead2, curcount);
        drawPosts(g);
    }
    
    protected int getPostCount() { return 2; }
    
    protected Point getPost(int n) {
        if (n == 1)
            return point2;
        return inPost;
    }
    
    protected int getVoltageSourceCount() { return 1; }
    
    protected boolean nonLinear() { return false; }
    
    protected void stamp() {
        // This is a voltage-controlled voltage source (VCVS)
        // V_out = gain * V_in
        // For very small gains (< 1e-9), clamp to avoid numerical issues
        double effectiveGain = gain;
        if (Math.abs(effectiveGain) < 1e-9)
            effectiveGain = (effectiveGain >= 0) ? 1e-9 : -1e-9;
        
        // stampVoltageSource creates the voltage source from ground to output
        sim.stampVoltageSource(nodes[1], 0, voltSource);
        // stampVCVS makes it controlled by the input voltage with gain coefficient
        sim.stampVCVS(nodes[0], 0, effectiveGain, voltSource);
    }
    
    private boolean showAsPercent() {
        return (flags & FLAG_SHOWPERCENT) != 0;
    }
    
    protected void getInfo(String arr[]) {
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
    
    // No current path through input, but output connects to ground
    protected boolean getConnection(int n1, int n2) { return false; }
    protected boolean hasGroundConnection(int n1) { return n1 == 1; }
    
    protected double getCurrentIntoNode(int n) {
        if (n == 1)
            return -current;
        return 0;
    }
    
    public EditInfo getEditInfo(int n) {
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
        if (n == 3)
            return EditInfo.createCheckbox("Small", (flags & FLAG_SMALL) != 0);
        return null;
    }
    
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            elmName = ei.textf.getText();
        }
        if (n == 1) {
            gain = ei.value;
            // Trigger re-analysis since gain affects the circuit matrix
            sim.analyzeFlag = true;
        }
        if (n == 2) {
            flags = ei.changeFlag(flags, FLAG_SHOWPERCENT);
        }
        if (n == 3) {
            flags = ei.changeFlag(flags, FLAG_SMALL);
            boolean small = (flags & FLAG_SMALL) != 0;
            setSize(small ? 1 : 2);
            if (small) {
                sim.smallGridCheckItem.setState(true);
                sim.getPreferencesManager().setGrid();
            }
            setPoints();
        }
    }
    
    // Override to provide custom slider text formatting
    public String getSliderUnitText(int n, EditInfo ei, double value) {
        // For the gain parameter (n==1), format based on percentage flag
        if (n == 1) {
            if (showAsPercent()) {
                return getUnitText(value * 100, "%");
            } else {
                return String.valueOf(value);
            }
        }
        return null; // Use default formatting for other parameters
    }
}
