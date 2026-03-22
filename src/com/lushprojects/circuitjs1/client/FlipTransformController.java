package com.lushprojects.circuitjs1.client;

final class FlipTransformController {
    private final CirSim sim;

    static class FlipInfo {
	int cx;
	int cy;
	int count;
    }

    FlipTransformController(CirSim sim) {
        this.sim = sim;
    }

    private FlipInfo prepareFlip() {
	sim.getUndoRedoManager().pushUndo();
	sim.getClipboardManager().setMenuSelection();
	int minx = 30000, maxx = -30000;
	int miny = 30000, maxy = -30000;
	int count = sim.getClipboardManager().countSelected();
	for (int i = 0; i != sim.elmList.size(); i++) {
	    CircuitElm ce = sim.getElm(i);
	    if (ce.isSelected() || count == 0) {
		minx = sim.min(ce.x, sim.min(ce.x2, minx));
		maxx = sim.max(ce.x, sim.max(ce.x2, maxx));
		miny = sim.min(ce.y, sim.min(ce.y2, miny));
		maxy = sim.max(ce.y, sim.max(ce.y2, maxy));
	    }
	}
	FlipInfo fi = new FlipInfo();
	fi.cx = (minx+maxx)/2;
	fi.cy = (miny+maxy)/2;
	fi.count = count;
	return fi;
    }

    void flipX() {
	FlipInfo fi = prepareFlip();
	int center2 = fi.cx*2;
	for (CircuitElm ce : sim.elmList) {
	    if (ce.isSelected() || fi.count == 0)
		ce.flipX(center2, fi.count);
	}
	sim.needAnalyze();
    }

    void flipY() {
	FlipInfo fi = prepareFlip();
	int center2 = fi.cy*2;
	for (CircuitElm ce : sim.elmList) {
	    if (ce.isSelected() || fi.count == 0)
		ce.flipY(center2, fi.count);
	}
	sim.needAnalyze();
    }

    void flipXY() {
	FlipInfo fi = prepareFlip();
	int xmy = sim.snapGrid(fi.cx-fi.cy);
	CirSim.console("xmy " + xmy + " grid " + sim.gridSize + " " + fi.cx + " " + fi.cy);
	for (CircuitElm ce : sim.elmList) {
	    if (ce.isSelected() || fi.count == 0)
		ce.flipXY(xmy, fi.count);
	}
	sim.needAnalyze();
    }
}