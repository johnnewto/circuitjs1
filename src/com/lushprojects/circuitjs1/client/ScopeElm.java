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
 * ScopeElm - Circuit element that provides an embedded oscilloscope display.
 * This element doesn't affect the circuit electrically but displays waveforms
 * from connected or selected circuit elements.
 */
class ScopeElm extends CircuitElm {
    
    Scope elmScope;

    /**
     * Creates a new scope element at the given position.
     * @param xx Initial X coordinate
     * @param yy Initial Y coordinate
     */
    public ScopeElm(int xx, int yy) {
	super(xx, yy);
	noDiagonal = false;
	x2 = x + 128;
	y2 = y + 64;
	elmScope = new Scope(sim);
	setPoints();
    }
    
    /**
     * Sets the circuit element to be displayed in this scope.
     * @param e The element to monitor
     */
    public void setScopeElm(CircuitElm e) {
	elmScope.setElm(e);
	elmScope.resetGraph();
    }
    
    /**
     * Creates a scope element from saved circuit data.
     * @param xa X coordinate of first corner
     * @param ya Y coordinate of first corner
     * @param xb X coordinate of second corner
     * @param yb Y coordinate of second corner
     * @param f Flags
     * @param st String tokenizer containing serialized data
     */
    public ScopeElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
	super(xa, ya, xb, yb, f);
	noDiagonal = false;
	String sStr = st.nextToken();
	StringTokenizer sst = new StringTokenizer(sStr, "_");
	elmScope = new Scope(sim);
	elmScope.undump(sst);
	setPoints();
	// Don't call resetGraph() here - setPoints() -> setScopeRect() -> setRect() already did it
	// Calling it again would clear history buffers that were just properly initialized
    }
    
    /**
     * Updates the scope's display rectangle based on element position.
     */
    public void setScopeRect() {
	int i1 = sim.transformX(min(x, x2));
	int i2 = sim.transformX(max(x, x2));
	int j1 = sim.transformY(min(y, y2));
	int j2 = sim.transformY(max(y, y2));
	Rectangle r = new Rectangle(i1, j1, i2 - i1, j2 - j1);
	if (!r.equals(elmScope.rect))
	    elmScope.setRect(r);
    }
    
    @Override
    public void setPoints() {
	super.setPoints();
	setScopeRect();
    }
    
    public void setElmScope(Scope s) {
	elmScope = s;
    }
    
    /**
     * Updates the scope data for one timestep.
     */
    public void stepScope() {
	elmScope.timeStep();
    }
    
    @Override
    public void reset() {
	super.reset();
	elmScope.resetGraph(true);
    }
    
    public void clearElmScope() {
	elmScope = null;
    }
    
    @Override
    boolean canViewInScope() { 
	return false; 
    }
    
    @Override
    int getDumpType() { 
	return 403; 
    }

    @Override
    public String dump() {
	String dumpStr = super.dump();
	String elmDump = elmScope.dump();
	if (elmDump == null)
	    return null;
	String sStr = elmDump.replace(' ', '_');
	sStr = sStr.replaceFirst("o_", ""); // Remove unused prefix for embedded Scope
	return dumpStr + " " + sStr;
    }
    
    @Override
    void draw(Graphics g) {
	g.setColor(needsHighlight() ? selectColor : whiteColor);
	g.context.save();
	
	// Apply inverse transform for proper rendering
	// Note: setTransform() doesn't work in the version of canvas2svg we are using
	g.context.scale(1 / sim.transform[0], 1 / sim.transform[3]);
	g.context.translate(-sim.transform[4], -sim.transform[5]);

	if (sim.isExporting) {
	    drawForExport(g);
	} else {
	    drawNormal(g);
	}
	
	g.context.restore();
	setBbox(point1, point2, 0);
	drawPosts(g);
    }
    
    /**
     * Draws the scope element during normal rendering.
     * @param g Graphics context
     */
    private void drawNormal(Graphics g) {
	// Update rect based on current element position
	setScopeRect();
	
	// Draw shadow box around the scope to separate it from background
	drawShadowBox(g);
	
	elmScope.position = -1;
	elmScope.draw(g);
    }
    
    /**
     * Draws the scope element during export at higher resolution.
     * @param g Graphics context
     */
    private void drawForExport(Graphics g) {
	// Calculate where the scope should be drawn and its size
	// based on current transform (this positions and scales it correctly in the export)
	int i1 = sim.transformX(min(x, x2));
	int i2 = sim.transformX(max(x, x2));
	int j1 = sim.transformY(min(y, y2));
	int j2 = sim.transformY(max(y, y2));
	
	// Save the original rectangle to restore after drawing
	int savedX = elmScope.rect.x;
	int savedY = elmScope.rect.y;
	int savedWidth = elmScope.rect.width;
	int savedHeight = elmScope.rect.height;
	
	// Keep the rect at its original size for proper calculations
	// but we'll transform the graphics context to scale everything up
	elmScope.rect.x = i1;
	elmScope.rect.y = j1;
	
	// Calculate the scale factor
	double scaleX = (double)(i2 - i1) / savedWidth;
	double scaleY = (double)(j2 - j1) / savedHeight;
	
	// Draw shadow box (scaled)
	int shadowOffset = (int)(4 * Math.min(scaleX, scaleY));
	g.context.save();
	g.context.setShadowBlur(8 * Math.min(scaleX, scaleY));
	g.context.setShadowOffsetX(shadowOffset);
	g.context.setShadowOffsetY(shadowOffset);
	
	setShadowColor(g);
	setBackgroundColor(g);
	
	// Draw a background rectangle for the shadow (scaled size)
	g.fillRect(i1, j1, i2 - i1, j2 - j1);
	g.context.restore();
	
	// Apply transform: translate to scope origin, scale, then translate back
	g.context.save();
	g.context.translate(i1, j1);
	g.context.scale(scaleX, scaleY);
	g.context.translate(-i1, -j1);
	
	elmScope.position = -1;
	elmScope.draw(g);
	
	g.context.restore();
	
	// Restore the original rectangle after drawing
	elmScope.rect.x = savedX;
	elmScope.rect.y = savedY;
    }
    
    /**
     * Draws a shadow box around the scope for visual separation.
     * @param g Graphics context
     */
    private void drawShadowBox(Graphics g) {
	final int SHADOW_OFFSET = 4;
	final int SHADOW_BLUR = 8;
	
	g.context.save();
	g.context.setShadowBlur(SHADOW_BLUR);
	g.context.setShadowOffsetX(SHADOW_OFFSET);
	g.context.setShadowOffsetY(SHADOW_OFFSET);
	
	setShadowColor(g);
	setBackgroundColor(g);
	
	// Draw a background rectangle for the shadow
	g.fillRect(elmScope.rect.x, elmScope.rect.y, elmScope.rect.width, elmScope.rect.height);
	g.context.restore();
    }
    
    /**
     * Sets the shadow color based on print mode.
     * @param g Graphics context
     */
    private void setShadowColor(Graphics g) {
	if (sim.printableCheckItem.getState()) {
	    g.context.setShadowColor("rgba(0, 0, 0, 0.4)");  // Dark shadow for white background
	} else {
	    g.context.setShadowColor("rgba(255, 255, 255, 0.3)");  // Light shadow for black background
	}
    }
    
    /**
     * Sets the background color based on print mode.
     * @param g Graphics context
     */
    private void setBackgroundColor(Graphics g) {
	if (sim.printableCheckItem.getState()) {
	    g.setColor(new Color(238, 238, 238));  // Light gray (#eee) - same as docked scopes
	} else {
	    g.setColor(new Color(32, 32, 32));  // Dark gray instead of pure black
	}
    }
    
    @Override
    int getPostCount() { 
	return 0; 
    }
    
    @Override
    int getNumHandles() { 
	return 2; 
    }
    
    void selectScope(int mx, int my, boolean mouseButtonDown) { 
	elmScope.selectScope(mx, my, mouseButtonDown); 
    }
}
