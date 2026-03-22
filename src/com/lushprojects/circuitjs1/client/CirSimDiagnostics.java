package com.lushprojects.circuitjs1.client;

final class CirSimDiagnostics {
    private final CirSim sim;

    CirSimDiagnostics(CirSim sim) {
        this.sim = sim;
    }

    void logElementRegistryInferenceReport() {
	CirSim.console(ElementRegistry.buildInferredUsageReport());
    }
}