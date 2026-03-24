package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.RootLayoutPanel;
import com.lushprojects.circuitjs1.client.miscElm.ViewportElm;

final class ViewportController {
    private final CirSim sim;
    private final double[] transform = new double[6];
    private int menuClientX;
    private int menuClientY;
    private int menuX;
    private int menuY;

    ViewportController(CirSim sim) {
        this.sim = sim;
        transform[0] = 1;
        transform[3] = 1;
    }

    double[] getTransform() {
        return transform;
    }

    int getMenuClientX() {
        return menuClientX;
    }

    void setMenuClientX(int value) {
        menuClientX = value;
    }

    int getMenuClientY() {
        return menuClientY;
    }

    void setMenuClientY(int value) {
        menuClientY = value;
    }

    int getMenuX() {
        return menuX;
    }

    void setMenuX(int value) {
        menuX = value;
    }

    int getMenuY() {
        return menuY;
    }

    void setMenuY(int value) {
        menuY = value;
    }

    void setTransform(double scale, double translateX, double translateY) {
        transform[0] = transform[3] = scale;
        transform[4] = translateX;
        transform[5] = translateY;
    }

    void setTransformRaw(double[] values) {
        if (values == null || values.length < 6)
            return;
        for (int i = 0; i < 6; i++)
            transform[i] = values[i];
    }

    void translate(double dx, double dy) {
        transform[4] += dx;
        transform[5] += dy;
    }

    int inverseTransformX(double x) {
        return (int) ((x - transform[4]) / transform[0]);
    }

    int inverseTransformY(double y) {
        return (int) ((y - transform[5]) / transform[3]);
    }

    int transformX(double x) {
        return (int) ((x * transform[0]) + transform[4]);
    }

    int transformY(double y) {
        return (int) ((y * transform[3]) + transform[5]);
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

	if (transform[0] == 0)
	    centreCircuit();
    }

    void setCircuitArea() {
	int height = sim.canvasHeight;
	int width = sim.canvasWidth;
	int h = (int) ((double) height * sim.getScopeManager().getScopeHeightFraction());
	if (sim.scopeCount == 0)
	    h = 0;
	sim.circuitArea = new Rectangle(0, 0, width, height - h);
    }

    void centreCircuit() {
	if (sim.elmList == null)
	    return;

	Rectangle bounds = sim.getCircuitBounds();
	setCircuitArea();

	double scale = 1;
	int cheight = sim.circuitArea.height;

	if (sim.scopeCount == 0 && sim.circuitArea.width < 800) {
	    int h = (int) ((double)cheight * sim.getScopeManager().getScopeHeightFraction());
	    cheight -= h;
	}

	if (bounds != null)
	    scale = Math.min(sim.circuitArea.width /(double)(bounds.width+140),
			     cheight/(double)(bounds.height+100));
	scale = Math.min(scale, 1.5);

	transform[0] = transform[3] = scale;
	transform[1] = transform[2] = transform[4] = transform[5] = 0;
	if (bounds != null) {
	    transform[4] = (sim.circuitArea.width -bounds.width *scale)/2 - bounds.x*scale;
	    transform[5] = (cheight-bounds.height*scale)/2 - bounds.y*scale;
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
	setCircuitArea();
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

	transform[0] = transform[3] = scale;
	transform[4] = translateX;
	transform[5] = translateY;
    }

    void zoomCircuit(double dy) {
	zoomCircuit(dy, false);
    }

    void zoomCircuit(double dy, boolean menu) {
	double oldScale = transform[0];
	double val = dy * .01;
	double newScale = Math.max(oldScale + val, .2);
	newScale = Math.min(newScale, 2.5);
	setCircuitScale(newScale, menu);
    }

    void setCircuitScale(double newScale, boolean menu) {
	int constX = !menu ? sim.getMouseCursorX() : sim.circuitArea.width / 2;
	int constY = !menu ? sim.getMouseCursorY() : sim.circuitArea.height / 2;
	int cx = inverseTransformX(constX);
	int cy = inverseTransformY(constY);
	transform[0] = transform[3] = newScale;
	transform[4] = constX - cx * newScale;
	transform[5] = constY - cy * newScale;
    }
}
