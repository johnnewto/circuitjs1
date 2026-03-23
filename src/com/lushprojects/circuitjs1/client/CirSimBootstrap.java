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
	sim.getViewportController().setTransformRaw(new double[] {1, 0, 0, 1, 0, 0});
	sim.elmList = new java.util.Vector<CircuitElm>();
	sim.adjustables = new java.util.Vector<Adjustable>();
	sim.scopes = new Scope[20];
	sim.scopeColCount = new int[20];
	sim.scopeCount = 0;
	sim.canvasWidth = 1200;
	sim.canvasHeight = 800;
	sim.getViewportController().setCircuitArea();
	sim.getTimingState().timeStep = 5e-6;
	sim.getTimingState().maxTimeStep = 5e-2;
	sim.getTimingState().minTimeStep = 1e-12;
	sim.getTimingState().t = 0;
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
