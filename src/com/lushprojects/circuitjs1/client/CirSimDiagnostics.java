package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.registry.ElementRegistry;

final class CirSimDiagnostics {
    private final CirSim sim;

    CirSimDiagnostics(CirSim sim) {
        this.sim = sim;
    }

    void logElementRegistryInferenceReport() {
	CirSim.console(ElementRegistry.buildInferredUsageReport());
    }
}
