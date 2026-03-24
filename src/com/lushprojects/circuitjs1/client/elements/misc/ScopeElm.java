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

package com.lushprojects.circuitjs1.client.elements.misc;

import com.lushprojects.circuitjs1.client.*;

/**
 * ScopeElm - Circuit element that provides an embedded oscilloscope display.
 * This element doesn't affect the circuit electrically but displays waveforms
 * from connected or selected circuit elements.
 */
public class ScopeElm extends CircuitElm {
    
    public Scope elmScope;

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
	elmScope.setElmForEmbedded(e);
	elmScope.resetGraphForEmbedded();
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
	elmScope.undumpForEmbedded(sst);
	setPoints();
	// Don't call resetGraph() here - setPoints() -> setScopeRect() -> setRect() already did it
	// Calling it again would clear history buffers that were just properly initialized
    }
    
    /**
     * Updates the scope's display rectangle based on element position.
     */
    public void setScopeRect() {
	int i1 = sim.transformXForUiElement(min(x, x2));
	int i2 = sim.transformXForUiElement(max(x, x2));
	int j1 = sim.transformYForUiElement(min(y, y2));
	int j2 = sim.transformYForUiElement(max(y, y2));
	Rectangle r = new Rectangle(i1, j1, i2 - i1, j2 - j1);
	Rectangle scopeRect = elmScope.getRectForEmbedded();
	if (!r.equals(scopeRect))
	    elmScope.setRectForEmbedded(r);
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
	elmScope.timeStepForEmbedded();
    }
    
    @Override
    public void reset() {
	super.reset();
	elmScope.resetGraphForEmbedded(true);
    }
    
    public void clearElmScope() {
	elmScope = null;
    }
    
    @Override
    protected boolean canViewInScope() { 
	return false; 
    }
    
    @Override
    protected int getDumpType() { 
	return 403; 
    }

    @Override
    public String dump() {
	String dumpStr = super.dump();
	String elmDump = elmScope.dumpForEmbedded();
	if (elmDump == null)
	    return null;
	String sStr = elmDump.replace(' ', '_');
	sStr = sStr.replaceFirst("o_", ""); // Remove unused prefix for embedded Scope
	return dumpStr + " " + sStr;
    }
    
    @Override
    protected void draw(Graphics g) {
	g.setColor(needsHighlight() ? selectColor : whiteColor);
	g.context.save();
	
	// Apply inverse transform for proper rendering
	// Note: setTransform() doesn't work in the version of canvas2svg we are using
	double[] transform = sim.getTransformForUiElement();
	g.context.scale(1 / transform[0], 1 / transform[3]);
	g.context.translate(-transform[4], -transform[5]);

	if (sim.isExportingImage()) {
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
	
	// Set zoom scale for text sizing - use average of X and Y scale factors
	double[] transform = sim.getTransformForUiElement();
	double zoomScale = (transform[0] + transform[3]) / 2.0;
	elmScope.setZoomScaleForEmbedded(zoomScale);
	
	// Draw shadow box around the scope to separate it from background
	drawShadowBox(g);
	
	elmScope.setPositionForEmbedded(-1);
	elmScope.drawForEmbedded(g);
    }
    
    /**
     * Draws the scope element during export at higher resolution.
     * @param g Graphics context
     */
    private void drawForExport(Graphics g) {
	// Calculate where the scope should be drawn and its size
	// based on current transform (this positions and scales it correctly in the export)
	int i1 = sim.transformXForUiElement(min(x, x2));
	int i2 = sim.transformXForUiElement(max(x, x2));
	int j1 = sim.transformYForUiElement(min(y, y2));
	int j2 = sim.transformYForUiElement(max(y, y2));
	
	// Save the original rectangle to restore after drawing
	Rectangle scopeRect = elmScope.getRectForEmbedded();
	int savedX = scopeRect.x;
	int savedY = scopeRect.y;
	int savedWidth = scopeRect.width;
	int savedHeight = scopeRect.height;
	
	// Keep the rect at its original size for proper calculations
	// but we'll transform the graphics context to scale everything up
	scopeRect.x = i1;
	scopeRect.y = j1;
	
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
	
	elmScope.setPositionForEmbedded(-1);
	elmScope.drawForEmbedded(g);
	
	g.context.restore();
	
	// Restore the original rectangle after drawing
	scopeRect.x = savedX;
	scopeRect.y = savedY;
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
	Rectangle scopeRect = elmScope.getRectForEmbedded();
	g.fillRect(scopeRect.x, scopeRect.y, scopeRect.width, scopeRect.height);
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
    protected int getPostCount() { 
	return 0; 
    }
    
    int getNumHandles() { 
	return 2; 
    }
    
    public void selectScope(int mx, int my, boolean mouseButtonDown) {
	elmScope.selectScopeForEmbedded(mx, my, mouseButtonDown);
    }
}
