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

import com.google.gwt.user.client.ui.Button;

/**
 * PieChartElm - Displays a pie chart of voltage values from multiple labeled nodes.
 * Each slice represents the proportional value of a node, with customizable colors.
 */
class PieChartElm extends GraphicElm {
    
    // Color palette (same as Scope)
    static final String colors[] = {
        "#FFFFFF", "#FFFF00", "#00FF00", "#00FFFF", "#FF00FF", "#FF0000",
        "#FFA500", "#90EE90", "#87CEEB", "#DDA0DD", "#FFB6C1"
    };
    
    // Configuration
    String[] nodeNames;     // Names of labeled nodes to monitor
    String[] nodeColors;    // Color for each slice
    double[] nodeValues;    // Current values of each node
    int radius = 40;        // Radius of pie chart
    Point center;           // Center of pie chart
    
    /**
     * Creates a new pie chart element at the given position.
     */
    public PieChartElm(int xx, int yy) {
        super(xx, yy);
        noDiagonal = false;  // Allow diagonal dragging for resizing
        x2 = x + radius * 2;
        y2 = y + radius * 2;
        
        // Default: 3 slices
        nodeNames = new String[]{"node1", "node2", "node3"};
        nodeColors = new String[]{colors[1], colors[2], colors[3]};
        nodeValues = new double[nodeNames.length];
        
        setPoints();
    }
    
    /**
     * Creates a pie chart element from saved circuit data.
     */
    public PieChartElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        noDiagonal = false;  // Allow diagonal dragging for resizing
        
        // Read node count
        int count = Integer.parseInt(st.nextToken());
        nodeNames = new String[count];
        nodeColors = new String[count];
        nodeValues = new double[count];
        
        // Read node names
        for (int i = 0; i < count; i++) {
            nodeNames[i] = CustomLogicModel.unescape(st.nextToken());
        }
        
        // Read colors
        for (int i = 0; i < count; i++) {
            nodeColors[i] = st.nextToken();
        }
        
        setPoints();
    }
    
    @Override
    public void setPoints() {
        super.setPoints();
        center = new Point((x + x2) / 2, (y + y2) / 2);
        
        // Calculate radius from bounding box
        int w = Math.abs(x2 - x);
        int h = Math.abs(y2 - y);
        radius = Math.min(w, h) / 2;
        if (radius < 20)
            radius = 20;
        
        // Set bounding box
        setBbox(x, y, x2, y2);
    }
    
    @Override
    int getDumpType() {
        return 217;
    }
    
    @Override
    String dump() {
        StringBuilder sb = new StringBuilder(super.dump());
        
        // Save node count
        sb.append(" ").append(nodeNames.length);
        
        // Save node names
        for (String name : nodeNames) {
            sb.append(" ").append(CustomLogicModel.escape(name));
        }
        
        // Save colors
        for (String color : nodeColors) {
            sb.append(" ").append(color);
        }
        
        return sb.toString();
    }
    
    @Override
    void drag(int xx, int yy) {
        // Default drag behavior from CircuitElm handles resizing
        x = xx;
        y = yy;
        setPoints();
    }
    
    @Override
    void draw(Graphics g) {
        // Update values from labeled nodes
        updateNodeValues();
        
        // Calculate total (use absolute values for pie chart)
        double total = 0;
        for (double val : nodeValues) {
            total += Math.abs(val);
        }
        
        // Draw background circle
        g.setColor(sim.printableCheckItem.getState() ? Color.white : Color.darkGray);
        g.context.beginPath();
        g.context.arc(center.x, center.y, radius, 0, 2 * Math.PI);
        g.context.fill();
        
        // Draw pie slices and labels
        if (total > 0) {
            double startAngle = -Math.PI / 2; // Start at top (12 o'clock)
            
            for (int i = 0; i < nodeValues.length; i++) {
                double value = Math.abs(nodeValues[i]);
                if (value < 1e-10)
                    continue; // Skip zero values
                
                double sliceAngle = (value / total) * 2 * Math.PI;
                double endAngle = startAngle + sliceAngle;
                double midAngle = startAngle + sliceAngle / 2;
                
                // Draw slice
                g.setColor(nodeColors[i]);
                g.context.beginPath();
                g.context.moveTo(center.x, center.y);
                g.context.arc(center.x, center.y, radius, startAngle, endAngle);
                g.context.closePath();
                g.context.fill();
                
                // Draw slice border
                g.setColor(sim.printableCheckItem.getState() ? Color.black : Color.white);
                g.context.beginPath();
                g.context.moveTo(center.x, center.y);
                g.context.arc(center.x, center.y, radius, startAngle, endAngle);
                g.context.closePath();
                g.context.stroke();
                
                // Draw label if slice is large enough (at least 5% of pie)
                if (sliceAngle > 0.1 * Math.PI) {
                    // Position label at 60% of radius from center
                    double labelRadius = radius * 0.6;
                    int labelX = (int) (center.x + labelRadius * Math.cos(midAngle));
                    int labelY = (int) (center.y + labelRadius * Math.sin(midAngle));
                    
                    // Choose contrasting text color based on slice color
                    g.setColor(getContrastColor(nodeColors[i]));
                    g.setFont(new Font("SansSerif", 0, Math.max(10, radius / 6)));
                    
                    // Draw node name
                    drawCenteredText(g, nodeNames[i], labelX, labelY, true);
                }
                
                startAngle = endAngle;
            }
        } else {
            // No data - draw empty circle
            g.setColor(sim.printableCheckItem.getState() ? Color.lightGray : Color.gray);
            g.context.beginPath();
            g.context.arc(center.x, center.y, radius, 0, 2 * Math.PI);
            g.context.stroke();
        }
        
        // Draw outer border
        g.setColor(needsHighlight() ? selectColor : 
                  (sim.printableCheckItem.getState() ? Color.black : Color.white));
        g.setLineWidth(needsHighlight() ? 3.0 : 1.0);
        g.context.beginPath();
        g.context.arc(center.x, center.y, radius, 0, 2 * Math.PI);
        g.context.stroke();
        g.setLineWidth(1.0);
    }
    
    /**
     * Gets a contrasting color (black or white) for text on the given background color.
     */
    Color getContrastColor(String hexColor) {
        try {
            // Parse hex color (assuming format like "#RRGGBB")
            String hex = hexColor.replaceAll("#", "");
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            
            // Calculate perceived brightness
            double brightness = (r * 299 + g * 587 + b * 114) / 1000.0;
            
            // Return black for bright colors, white for dark colors
            return brightness > 128 ? Color.black : Color.white;
        } catch (Exception e) {
            // Default to white if parsing fails
            return Color.white;
        }
    }
    
    /**
     * Updates node values from labeled nodes and TableElm stocks in the circuit.
     * First checks ComputedValues (for TableElm stock columns), 
     * then falls back to LabeledNodeElm voltage via sim.getLabeledNodeVoltage().
     */
    void updateNodeValues() {
        for (int i = 0; i < nodeNames.length; i++) {
            String nodeName = nodeNames[i];
            
            // First, try to get value from ComputedValues (TableElm stock columns)
            Double computedValue = ComputedValues.getComputedValue(nodeName);
            if (computedValue != null) {
                nodeValues[i] = computedValue;
            } else {
                // Fall back to LabeledNodeElm voltage lookup (uses HashMap, not loop)
                nodeValues[i] = sim.getLabeledNodeVoltage(nodeName);
            }
        }
    }
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "Pie Chart";
        
        // Calculate total
        double total = 0;
        for (double val : nodeValues) {
            total += Math.abs(val);
        }
        
        // Show each slice
        for (int i = 0; i < nodeNames.length; i++) {
            double value = Math.abs(nodeValues[i]);
            double percent = (total > 0) ? (value / total * 100) : 0;
            
            // Format percent to 1 decimal place manually
            int percentInt = (int) (percent * 10);
            String percentStr = (percentInt / 10) + "." + (percentInt % 10);
            
            arr[i + 1] = nodeNames[i] + " = " + 
                        getUnitText(value, "V") + 
                        " (" + percentStr + "%)";
        }
    }
    
    @Override
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.button = new Button("Edit Slices...");
            return ei;
        }
        return null;
    }
    
    @Override
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0 && ei.button != null) {
            // Open custom dialog
            new PieChartDialog(this, sim);
        }
    }
    
    @Override
    boolean needsShortcut() {
        return false;
    }
}
