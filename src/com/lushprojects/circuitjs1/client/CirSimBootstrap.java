package com.lushprojects.circuitjs1.client;

import java.util.Random;

import com.google.gwt.core.client.GWT;

final class CirSimBootstrap {
    private final CirSim sim;

    CirSimBootstrap(CirSim sim) {
        this.sim = sim;
    }

    void initRunner() {
	sim.random = new Random();
	sim.transform = new double[6];
	sim.transform[0] = sim.transform[3] = 1;
	sim.transform[1] = sim.transform[2] = sim.transform[4] = sim.transform[5] = 0;
	sim.elmList = new java.util.Vector<CircuitElm>();
	sim.adjustables = new java.util.Vector<Adjustable>();
	sim.undoStack = new java.util.Vector<CirSim.UndoItem>();
	sim.redoStack = new java.util.Vector<CirSim.UndoItem>();
	sim.scopes = new Scope[20];
	sim.scopeColCount = new int[20];
	sim.scopeCount = 0;
	sim.canvasWidth = 1200;
	sim.canvasHeight = 800;
	sim.getViewportController().setCircuitArea();
	sim.timeStep = 5e-6;
	sim.maxTimeStep = 5e-2;
	sim.minTimeStep = 1e-12;
	sim.t = 0;
	CircuitElm.initClass(sim);
    }

    void initRunnerPanel(QueryParameters qp) {
	RuntimeMode.setNonInteractiveRuntime(true);
	RunnerPanelUi.clearRunnerStdout();
	RunnerPanelUi.setRunnerStdoutEnabled(true);
	GWT.setUncaughtExceptionHandler(new GWT.UncaughtExceptionHandler() {
	    public void onUncaughtException(Throwable e) {
		CirSim.console("GWT uncaught exception: " + e);
	    }
	});
	ComputedValues.resetForTesting();
	initRunner();
	CirSim.console("Runner panel mode enabled");
	sim.getRunnerController().launchFromQuery(qp);
    }
}