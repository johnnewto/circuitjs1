package com.lushprojects.circuitjs1.client;

final class ViewportController {
    private final CirSim sim;

    ViewportController(CirSim sim) {
        this.sim = sim;
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