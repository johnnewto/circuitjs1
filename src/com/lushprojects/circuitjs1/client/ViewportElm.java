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
 * ViewportElm - Defines a visible viewport area for circuit loading.
 * 
 * When a circuit is loaded, the transform is calculated to show the area
 * defined by this element. This makes circuits portable across different
 * screen sizes - the same area will be visible regardless of canvas dimensions.
 * 
 * Extends BoxElm to provide a dashed rectangle visualization.
 */
class ViewportElm extends BoxElm {

    String name = "Main";  // Viewport name (for future multi-viewport support)
    
    // Blue color for viewport display
    static Color viewportColor = new Color(64, 128, 255);

    public ViewportElm(int xx, int yy) {
        super(xx, yy);
    }

    public ViewportElm(int xa, int ya, int xb, int yb, int f,
                       StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        if (st.hasMoreTokens()) {
            name = CustomLogicModel.unescape(st.nextToken());
        }
    }

    String dump() {
        return super.dump() + " " + CustomLogicModel.escape(name);
    }

    int getDumpType() { return 263; }

    void draw(Graphics g) {
        // Draw a blue dashed rectangle to distinguish from regular boxes
        // Use 30% alpha for a fainter outline (unless highlighted)
        if (!needsHighlight()) {
            g.context.setGlobalAlpha(0.3);
        }
        g.setColor(needsHighlight() ? selectColor : viewportColor);
        setBbox(x, y, x2, y2);
        g.setLineDash(12, 4);
        
        int minX = Math.min(x, x2);
        int maxX = Math.max(x, x2);
        int minY = Math.min(y, y2);
        int maxY = Math.max(y, y2);
        
        g.drawRect(minX, minY, maxX - minX, maxY - minY);
        g.setLineDash(0, 0);
        
        // Draw name label in corner
        g.setColor(needsHighlight() ? selectColor : Color.white);
        int textX = minX + 4;
        int textY = minY + 14;
        g.drawString(name, textX, textY);
        
        // Draw small "viewport" indicator icon in opposite corner
        g.setColor(viewportColor);
        int iconSize = 8;
        int iconX = maxX - iconSize - 4;
        int iconY = maxY - iconSize - 4;
        g.drawRect(iconX, iconY, iconSize, iconSize);
        g.drawLine(iconX, iconY, iconX + iconSize, iconY + iconSize);
        
        // Restore full opacity
        if (!needsHighlight()) {
            g.context.setGlobalAlpha(1.0);
        }
    }

    public EditInfo getEditInfo(int n) {
        if (n == 0)
            return new EditInfo("Name", name);
        return null;
    }

    public void setEditValue(int n, EditInfo ei) {
        if (n == 0)
            name = ei.textf.getText();
    }

    void getInfo(String arr[]) {
        arr[0] = "viewport";
        arr[1] = "name: " + name;
        int minX = Math.min(x, x2);
        int maxX = Math.max(x, x2);
        int minY = Math.min(y, y2);
        int maxY = Math.max(y, y2);
        arr[2] = "area: (" + minX + "," + minY + ") to (" + maxX + "," + maxY + ")";
        arr[3] = "size: " + (maxX - minX) + " x " + (maxY - minY);
    }
    
    /**
     * Get the viewport bounds in circuit coordinates
     */
    public Rectangle getViewportBounds() {
        int minX = Math.min(x, x2);
        int maxX = Math.max(x, x2);
        int minY = Math.min(y, y2);
        int maxY = Math.max(y, y2);
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }
}
