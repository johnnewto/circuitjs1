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

class ScopeElm extends CircuitElm {
    
    Scope elmScope;

    public ScopeElm(int xx, int yy) {
	super(xx, yy);
	noDiagonal=false;
	x2=x+128;
	y2=y+64;
	elmScope = new Scope(sim);
	setPoints();
    }
    
    public void setScopeElm(CircuitElm e) {
	elmScope.setElm(e);
	elmScope.resetGraph();
    }
    
    public ScopeElm(int xa, int ya, int xb, int yb, int f,
		   StringTokenizer st) {
	super(xa, ya, xb, yb, f);
	noDiagonal=false;
	String sStr = st.nextToken();
	StringTokenizer sst = new StringTokenizer(sStr, "_");
	elmScope = new Scope(sim);
	elmScope.undump(sst);
	setPoints();
	elmScope.resetGraph();
    }
    
    public void setScopeRect() {
	int i1 = sim.transformX(min(x,x2));
	int i2 = sim.transformX(max(x,x2));
	int j1 = sim.transformY(min(y,y2));
	int j2 = sim.transformY(max(y,y2));
	Rectangle r = new Rectangle(i1,j1,i2-i1, j2-j1);
	if (!r.equals(elmScope.rect))
	    elmScope.setRect(r);
    }
    
    public void setPoints() {
	super.setPoints();
	setScopeRect();
    }
    
    public void setElmScope( Scope s) {
	elmScope=s;
    }
    
    
    public void stepScope() {
	elmScope.timeStep();
    }
    
    public void reset() {
	super.reset();
	elmScope.resetGraph(true);
    }
    
    public void clearElmScope() {
	elmScope = null;
    }
    
    boolean canViewInScope() { return false; }
    
    int getDumpType() { return 403; }

    public String dump() {
	String dumpStr = super.dump();
	String elmDump = elmScope.dump();
	if (elmDump == null)
	    return null;
	String sStr = elmDump.replace(' ', '_');
	sStr = sStr.replaceFirst("o_", ""); // remove unused prefix for embedded Scope
	return dumpStr + " " + sStr;
    }
    
    void draw(Graphics g) {
	g.setColor(needsHighlight() ? selectColor : whiteColor);
	g.context.save();
	// setTransform() doesn't work in version of canvas2svg we are using
	g.context.scale(1/sim.transform[0], 1/sim.transform[3]);
	g.context.translate(-sim.transform[4], -sim.transform[5]);
	//g.context.scale(CirSim.devicePixelRatio(), CirSim.devicePixelRatio());

	// During export, we need to preserve the scope's plot data while rendering at export size
	// During normal rendering, we can just update the rect based on element position
	if (sim.isExporting) {
	    // Calculate where the scope should be drawn and its size
	    // based on current transform (this positions and scales it correctly in the export)
	    int i1 = sim.transformX(min(x,x2));
	    int i2 = sim.transformX(max(x,x2));
	    int j1 = sim.transformY(min(y,y2));
	    int j2 = sim.transformY(max(y,y2));
	    
	    // Save the original rectangle to restore after drawing
	    int savedX = elmScope.rect.x;
	    int savedY = elmScope.rect.y;
	    int savedWidth = elmScope.rect.width;
	    int savedHeight = elmScope.rect.height;
	    
	    // Keep the rect at its original size for proper calculations
	    // but we'll transform the graphics context to scale everything up
	    elmScope.rect.x = i1;
	    elmScope.rect.y = j1;
	    // Keep original width and height so calculations work correctly
	    
	    // Calculate the scale factor
	    double scaleX = (double)(i2 - i1) / savedWidth;
	    double scaleY = (double)(j2 - j1) / savedHeight;
	    
	    // Draw shadow box around the scope in export as well
	    int shadowOffset = (int)(4 * Math.min(scaleX, scaleY));
	    g.context.save();
	    g.context.setShadowBlur(8 * Math.min(scaleX, scaleY));
	    g.context.setShadowOffsetX(shadowOffset);
	    g.context.setShadowOffsetY(shadowOffset);
	    
	    // Use lighter shadow for dark background, darker shadow for light background
	    if (sim.printableCheckItem.getState()) {
		g.context.setShadowColor("rgba(0, 0, 0, 0.4)");  // Dark shadow for white background
		g.setColor(new Color(238, 238, 238));  // Light gray (#eee) - same as docked scopes
	    } else {
		g.context.setShadowColor("rgba(255, 255, 255, 0.3)");  // Light gray shadow for black background
		g.setColor(new Color(32, 32, 32));  // Dark gray instead of pure black
	    }
	    
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
	    
	    g.context.restore(); // Restore transform
	    
	    // Restore the original rectangle after drawing
	    elmScope.rect.x = savedX;
	    elmScope.rect.y = savedY;
	} else {
	    // Normal rendering - update rect based on current element position
	    setScopeRect();
	    
	    // Draw shadow box around the scope to separate it from background
	    int shadowOffset = 4;
	    g.context.save();
	    g.context.setShadowBlur(8);
	    g.context.setShadowOffsetX(shadowOffset);
	    g.context.setShadowOffsetY(shadowOffset);
	    
	    // Use lighter shadow for dark background, darker shadow for light background
	    if (sim.printableCheckItem.getState()) {
		g.context.setShadowColor("rgba(0, 0, 0, 0.4)");  // Dark shadow for white background
		g.setColor(new Color(238, 238, 238));  // Light gray (#eee) - same as docked scopes
	    } else {
		g.context.setShadowColor("rgba(255, 255, 255, 0.3)");  // Light gray shadow for black background
		g.setColor(new Color(32, 32, 32));  // Dark gray instead of pure black
	    }
	    
	    // Draw a background rectangle for the shadow
	    g.fillRect(elmScope.rect.x, elmScope.rect.y, elmScope.rect.width, elmScope.rect.height);
	    
	    g.context.restore();
	    
	    elmScope.position = -1;
	    elmScope.draw(g);
	}
	
	g.context.restore();
	setBbox(point1, point2, 0);
	drawPosts(g);

    }
    
    int getPostCount() { return 0; }
    int getNumHandles() { return 2; }
    
    void selectScope(int mx, int my) { elmScope.selectScope(mx, my); }
}
