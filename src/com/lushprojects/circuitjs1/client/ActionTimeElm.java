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

import com.lushprojects.circuitjs1.client.util.Locale;

class ActionTimeElm extends CircuitElm {
    double actionTime;
    boolean actionTriggered;
    String preText;
    String postText;
    String sliderName;
    double sliderValueBefore;
    double sliderValueAfter;
    boolean enabled;
    
    public ActionTimeElm(int xx, int yy) {
        super(xx, yy);
        actionTime = 1.0; // Default 1 second
        preText = "Before";
        postText = "After";
        sliderName = "";
        sliderValueBefore = 0.0;
        sliderValueAfter = 0.0;
        enabled = true;
        actionTriggered = false;
    }
    
    public ActionTimeElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        actionTime = Double.parseDouble(st.nextToken());
        preText = CustomLogicModel.unescape(st.nextToken());
        postText = CustomLogicModel.unescape(st.nextToken());
        sliderName = CustomLogicModel.unescape(st.nextToken());
        sliderValueBefore = Double.parseDouble(st.nextToken());
        // Try to read sliderValueAfter for backward compatibility
        sliderValueAfter = sliderValueBefore; // Default to same as before
        try {
            sliderValueAfter = Double.parseDouble(st.nextToken());
        } catch (Exception e) {
            // Old files only have one slider value
        }
        enabled = true;
        try {
            enabled = Boolean.parseBoolean(st.nextToken());
        } catch (Exception e) {
            // Default to enabled for backward compatibility
        }
        actionTriggered = false;
    }
    
    String dump() { 
        return super.dump() + " " + actionTime + " " + 
               CustomLogicModel.escape(preText) + " " + 
               CustomLogicModel.escape(postText) + " " + 
               CustomLogicModel.escape(sliderName) + " " + 
               sliderValueBefore + " " + sliderValueAfter + " " + enabled; 
    }
    
    void reset() {
        actionTriggered = false;
        // Set slider to "before" value on reset only if enabled
        if (enabled && sliderName != null && sliderName.length() > 0) {
            setSliderValue(sliderName, sliderValueBefore);
        }
    }
    
    int getDumpType() { 
        return 432; 
    }
    
    int getPostCount() { 
        return 0; // No electrical connections
    }
    
    void setPoints() {
        super.setPoints();
    }
    
    void draw(Graphics g) {
        g.save();
        boolean selected = needsHighlight();
        
        // Calculate the center point between point1 and point2
        int cx = (x + x2) / 2;
        int cy = (y + y2) / 2;
        
        // Determine which text to display
        String displayText = (sim.t >= actionTime) ? postText : preText;
        
        // Calculate box dimensions based on text content
        Font f = new Font("SansSerif", Font.BOLD, 12);
        g.setFont(f);
        
        // Split text into lines (handle both \n and literal \n in the string)
        String[] lines = displayText.split("\\\\n|\\n");
        
        // Calculate required width and height
        int maxWidth = 80; // Minimum width
        for (String line : lines) {
            int lineWidth = (int)g.context.measureText(line).getWidth();
            if (lineWidth > maxWidth)
                maxWidth = lineWidth;
        }
        
        // Add padding
        int width = maxWidth + 20;
        int lineHeight = 16;
        int height = Math.max(60, 30 + lines.length * lineHeight + 20);
        
        // Also check action text width
        if (sliderName != null && sliderName.length() > 0) {
            Font smallFont = new Font("SansSerif", 0, 12);
            g.setFont(smallFont);
            double displayValue = (sim.t >= actionTime) ? sliderValueAfter : sliderValueBefore;
            String actionText = sliderName + "=" + displayValue;
            int actionWidth = (int)g.context.measureText(actionText).getWidth();
            if (actionWidth + 20 > width)
                width = actionWidth + 20;
        }
        
        // Draw box background with color coding
        Color bgColor;
        if (!enabled) {
            bgColor = Color.lightGray;
        } else if (actionTriggered) {
            bgColor = new Color(255, 255, 150); // Light yellow after trigger
        } else {
            bgColor = new Color(200, 255, 200); // Light green before trigger
        }
        g.setColor(bgColor);
        g.fillRect(cx - width/2, cy - height/2, width, height);
        
        // Draw border
        g.setColor(selected ? selectColor : Color.black);
        g.setLineWidth(2.0);
        g.drawRect(cx - width/2, cy - height/2, width, height);
        
        // Draw time label at top
        f = new Font("SansSerif", 0, 12);
        g.setFont(f);
        g.setColor(Color.black);
        String timeLabel = "t=" + getUnitText(actionTime, "s");
        int textWidth = (int)g.context.measureText(timeLabel).getWidth();
        g.drawString(timeLabel, cx - textWidth/2, cy - height/2 + 12);
        
        // Draw warning symbol if triggered
        if (enabled && actionTriggered) {
            f = new Font("SansSerif", Font.BOLD, 30);
            g.setFont(f);
            g.setColor(Color.red);
            String warningSymbol = "⚠";
            g.drawString(warningSymbol, cx - width/2 + 10, cy - height/2 + 18);
        }
        
        // Draw main display text (multiline)
        f = new Font("SansSerif", Font.BOLD, 12);
        g.setFont(f);
        g.setColor(enabled ? Color.black : Color.gray);
        
        int startY = cy - (lines.length - 1) * lineHeight / 2;
        for (int i = 0; i < lines.length; i++) {
            textWidth = (int)g.context.measureText(lines[i]).getWidth();
            g.drawString(lines[i], cx - textWidth/2, startY + i * lineHeight);
        }
        
        // Draw action info at bottom if slider is set
        if (sliderName != null && sliderName.length() > 0) {
            f = new Font("SansSerif", 0, 12);
            g.setFont(f);
            double displayValue = (sim.t >= actionTime) ? sliderValueAfter : sliderValueBefore;
            String actionText = sliderName + "=" + displayValue;
            textWidth = (int)g.context.measureText(actionText).getWidth();
            g.drawString(actionText, cx - textWidth/2, cy + height/2 - 5);
        }
        
        // If disabled, draw diagonal line
        if (!enabled) {
            g.setColor(Color.red);
            g.setLineWidth(2.0);
            g.drawLine(cx - width/2, cy - height/2, cx + width/2, cy + height/2);
        }
        
        // Set bounding box
        setBbox(cx - width/2 - 5, cy - height/2 - 5, cx + width/2 + 5, cy + height/2 + 5);
        
        g.restore();
    }
    
    void stepFinished() {
        if (!enabled)
            return;
            
        // Check if we've reached the action time and haven't triggered yet
        if (sim.t >= actionTime && !actionTriggered) {
            actionTriggered = true;
            
            // Execute the action - set slider to "after" value
            if (sliderName != null && sliderName.length() > 0) {
                // Find and set the slider value
                setSliderValue(sliderName, sliderValueAfter);
            }
        }
    }
    
    // Find a slider by name and set its value
    void setSliderValue(String name, double value) {
        int i;
        for (i = 0; i != sim.adjustables.size(); i++) {
            Adjustable adj = sim.adjustables.get(i);
            if (adj.sliderText != null && adj.sliderText.equals(name)) {
                // Use the Adjustable's own method to set the value
                adj.setSliderValue(value);
                // Also need to update the element's value
                EditInfo ei = adj.elm.getEditInfo(adj.editItem);
                if (ei != null) {
                    ei.value = value;
                    adj.elm.setEditValue(adj.editItem, ei);
                }
                // Trigger circuit analysis so changes propagate
                sim.analyzeFlag = true;
                CirSim.console("ActionTimeElm: Set " + name + " = " + value);
                return;
            }
        }
        CirSim.console("ActionTimeElm: Slider '" + name + "' not found");
    }
    
    void getInfo(String arr[]) {
        arr[0] = "action time";
        arr[1] = "enabled = " + (enabled ? "yes" : "no");
        arr[2] = "current time = " + getUnitText(sim.t, "s");
        arr[3] = "action time = " + getUnitText(actionTime, "s");
        arr[4] = "action = " + sliderName + ": " + sliderValueBefore + " → " + sliderValueAfter;
        if (enabled) {
            if (actionTriggered) {
                arr[5] = "action triggered";
            } else if (sim.t < actionTime) {
                arr[5] = "triggering in " + getUnitText(actionTime - sim.t, "s");
            }
        } else {
            arr[5] = "disabled";
        }
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Action Time (s)", actionTime);
            return ei;
        }
        if (n == 1) {
            EditInfo ei = new EditInfo("Text Before Time", 0, -1, -1);
            ei.text = preText;
            return ei;
        }
        if (n == 2) {
            EditInfo ei = new EditInfo("Text After Time", 0, -1, -1);
            ei.text = postText;
            return ei;
        }
        if (n == 3) {
            EditInfo ei = new EditInfo("Slider Name", 0, -1, -1);
            ei.text = sliderName;
            return ei;
        }
        if (n == 4) {
            EditInfo ei = new EditInfo("Before Slider Value", sliderValueBefore);
            return ei;
        }
        if (n == 5) {
            EditInfo ei = new EditInfo("After Slider Value", sliderValueAfter);
            return ei;
        }
        if (n == 6) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Enabled", enabled);
            return ei;
        }
        return null;
    }
    
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0)
            actionTime = ei.value;
        if (n == 1)
            preText = ei.textf.getText();
        if (n == 2)
            postText = ei.textf.getText();
        if (n == 3)
            sliderName = ei.textf.getText();
        if (n == 4)
            sliderValueBefore = ei.value;
        if (n == 5)
            sliderValueAfter = ei.value;
        if (n == 6)
            enabled = ei.checkbox.getState();
    }
    
    // Override to prevent trying to find voltages (no posts)
    void setNodeVoltage(int n, double c) {
    }
    
    void stamp() {
        // No electrical connections
    }
}
