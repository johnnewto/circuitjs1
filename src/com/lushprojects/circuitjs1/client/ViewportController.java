package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.RootLayoutPanel;

final class ViewportController {
    private final CirSim sim;

    ViewportController(CirSim sim) {
        this.sim = sim;
    }

    void checkCanvasSize() {
	if (sim.cv.getCoordinateSpaceWidth() != (int) (sim.canvasWidth * CirSim.devicePixelRatio()))
	    setCanvasSize();
    }

    void setCanvasSize() {
	int width, height;
	width = (int) RootLayoutPanel.get().getOffsetWidth();
	height = (int) RootLayoutPanel.get().getOffsetHeight();
	height = height - (sim.hideMenu ? 0 : CirSim.MENUBARHEIGHT);

	if (!sim.isMobile(sim.sidePanelCheckboxLabel))
	    width = width - CirSim.VERTICALPANELWIDTH;
	if (sim.toolbarCheckItem.getState())
	    height -= CirSim.TOOLBARHEIGHT;

	width = Math.max(width, 0);
	height = Math.max(height, 0);

	if (sim.cv != null) {
	    sim.cv.setWidth(width + "PX");
	    sim.cv.setHeight(height + "PX");
	    sim.canvasWidth = width;
	    sim.canvasHeight = height;
	    float scale = CirSim.devicePixelRatio();
	    sim.cv.setCoordinateSpaceWidth((int) (width * scale));
	    sim.cv.setCoordinateSpaceHeight((int) (height * scale));
	}

	setCircuitArea();

	if (sim.transform[0] == 0)
	    centreCircuit();
    }

    void setCircuitArea() {
	int height = sim.canvasHeight;
	int width = sim.canvasWidth;
	int h = (int) ((double) height * sim.scopeHeightFraction);
	if (sim.scopeCount == 0)
	    h = 0;
	sim.circuitArea = new Rectangle(0, 0, width, height - h);
    }

    void centreCircuit() {
	if (sim.elmList == null)
	    return;

	Rectangle bounds = sim.getCircuitBounds();
	sim.setCircuitArea();

	double scale = 1;
	int cheight = sim.circuitArea.height;

	if (sim.scopeCount == 0 && sim.circuitArea.width < 800) {
	    int h = (int) ((double)cheight * sim.scopeHeightFraction);
	    cheight -= h;
	}

	if (bounds != null)
	    scale = Math.min(sim.circuitArea.width /(double)(bounds.width+140),
			     cheight/(double)(bounds.height+100));
	scale = Math.min(scale, 1.5);

	sim.transform[0] = sim.transform[3] = scale;
	sim.transform[1] = sim.transform[2] = sim.transform[4] = sim.transform[5] = 0;
	if (bounds != null) {
	    sim.transform[4] = (sim.circuitArea.width -bounds.width *scale)/2 - bounds.x*scale;
	    sim.transform[5] = (cheight-bounds.height*scale)/2 - bounds.y*scale;
	}
    }

    ViewportElm findViewportElm() {
	for (int i = 0; i < sim.elmList.size(); i++) {
	    CircuitElm ce = sim.getElm(i);
	    if (ce instanceof ViewportElm)
		return (ViewportElm) ce;
	}
	return null;
    }

    void applyViewportTransform(ViewportElm viewport) {
	sim.setCircuitArea();
	Rectangle bounds = viewport.getViewportBounds();

	int viewWidth = bounds.width;
	int viewHeight = bounds.height;

	if (viewWidth <= 0 || viewHeight <= 0)
	    return;

	double scaleX = (double)sim.circuitArea.width / viewWidth;
	double scaleY = (double)sim.circuitArea.height / viewHeight;
	double scale = Math.min(scaleX, scaleY);

	double translateX = (sim.circuitArea.width - viewWidth * scale) / 2 - bounds.x * scale;
	double translateY = (sim.circuitArea.height - viewHeight * scale) / 2 - bounds.y * scale;

	sim.transform[0] = sim.transform[3] = scale;
	sim.transform[4] = translateX;
	sim.transform[5] = translateY;
    }

    void zoomCircuit(double dy) {
	zoomCircuit(dy, false);
    }

    void zoomCircuit(double dy, boolean menu) {
	double oldScale = sim.transform[0];
	double val = dy * .01;
	double newScale = Math.max(oldScale + val, .2);
	newScale = Math.min(newScale, 2.5);
	setCircuitScale(newScale, menu);
    }

    void setCircuitScale(double newScale, boolean menu) {
	int constX = !menu ? sim.mouseCursorX : sim.circuitArea.width / 2;
	int constY = !menu ? sim.mouseCursorY : sim.circuitArea.height / 2;
	int cx = sim.inverseTransformX(constX);
	int cy = sim.inverseTransformY(constY);
	sim.transform[0] = sim.transform[3] = newScale;
	sim.transform[4] = constX - cx * newScale;
	sim.transform[5] = constY - cy * newScale;
    }
}