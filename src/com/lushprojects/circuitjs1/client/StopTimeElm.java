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

class StopTimeElm extends CircuitElm {
    double stopTime;
    boolean stopped;
    
    public StopTimeElm(int xx, int yy) {
        super(xx, yy);
        stopTime = 1.0; // Default 1 second
    }
    
    public StopTimeElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        stopTime = Double.parseDouble(st.nextToken());
    }
    
    String dump() { 
        return super.dump() + " " + stopTime; 
    }
    
    void reset() {
        stopped = false;
    }
    
    int getDumpType() { 
        return 431; 
    }
    
    int getPostCount() { 
        return 0; // No electrical connections
    }
    
    void setPoints() {
        super.setPoints();
    }
    
    void draw(Graphics g) {
        g.save();
        boolean selected = needsHighlight() || stopped;
        
        // Calculate the center point between point1 and point2
        int cx = (x + x2) / 2;
        int cy = (y + y2) / 2;
        
        // Size of the stop sign
        int size = 30;
        
        // Draw octagon (stop sign shape) - create polygon
        Polygon poly = new Polygon();
        double angle = Math.PI / 8; // 22.5 degrees for octagon
        for (int i = 0; i < 8; i++) {
            double theta = angle + i * Math.PI / 4;
            int px = cx + (int)(size * Math.cos(theta));
            int py = cy + (int)(size * Math.sin(theta));
            poly.addPoint(px, py);
        }
        
        // Fill with red
        g.setColor(Color.red);
        g.fillPolygon(poly);
        
        // Draw border using drawPolygon from CircuitElm
        g.setColor(selected ? selectColor : Color.white);
        g.setLineWidth(2.0);
        drawPolygon(g, poly);
        
        // Draw "STOP" text in white
        Font f = new Font("SansSerif", Font.BOLD, 12);
        g.setFont(f);
        g.setColor(Color.white);
        String stopText = "STOP";
        int textWidth = (int)g.context.measureText(stopText).getWidth();
        g.drawString(stopText, cx - textWidth/2, cy + 4);
        
        // Draw time above the stop sign
        f = new Font("SansSerif", selected ? Font.BOLD : 0, 14);
        g.setFont(f);
        g.setColor(selected ? selectColor : whiteColor);
        String timeText = "t=" + getUnitText(stopTime, "s");
        textWidth = (int)g.context.measureText(timeText).getWidth();
        g.drawString(timeText, cx - textWidth/2, cy - size - 5);
        
        // Set bounding box
        setBbox(cx - size - 5, cy - size - 20, cx + size + 5, cy + size + 5);
        
        g.restore();
    }
    
    void stepFinished() {
        stopped = false;
        if (sim.t >= stopTime) {
            stopped = true;
            sim.setSimRunning(false);
        }
    }
    
    void getInfo(String arr[]) {
        arr[0] = "stop time";
        arr[1] = "current time = " + getUnitText(sim.t, "s");
        arr[2] = "stop time = " + getUnitText(stopTime, "s");
        if (sim.t >= stopTime) {
            arr[3] = "stopped";
        } else {
            arr[3] = "stopping in " + getUnitText(stopTime - sim.t, "s");
        }
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Stop Time (s)", stopTime);
            return ei;
        }
        return null;
    }
    
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0)
            stopTime = ei.value;
    }
    
    // Override to prevent trying to find voltages (no posts)
    void setNodeVoltage(int n, double c) {
    }
    
    void stamp() {
        // No electrical connections
    }
}
