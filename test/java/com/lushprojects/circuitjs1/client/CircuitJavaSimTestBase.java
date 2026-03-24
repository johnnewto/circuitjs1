package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.lushprojects.circuitjs1.client.elements.economics.ComputedValues;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("ComputedValues")
abstract class CircuitJavaSimTestBase {
    protected CirSim sim;

    @BeforeEach
    void setUpSim() throws Exception {
        RuntimeMode.setNonInteractiveRuntime(true);
        ComputedValues.resetForTesting();
        sim = new CirSim();
	    sim.getBootstrap().initRunner();
    }

    @AfterEach
    void tearDownSim() {
        RuntimeMode.setNonInteractiveRuntime(false);
    }

    protected void loadCircuit(String relativePath) throws Exception {
        Path circuitPath = Paths.get(System.getProperty("projectDir"), relativePath);
        String text = new String(Files.readAllBytes(circuitPath), StandardCharsets.UTF_8);
        loadCircuitText(text);
    }

    protected void loadCircuitText(String text) throws Exception {
        sim.getCircuitIOService().readCircuit(text, 0);
        sim.analyzeCircuit();
    }

    protected void runSteps(int n) {
        for (int i = 0; i < n; i++) {
            sim.getSimulationLoop().runCircuit(i == 0);
            ComputedValues.commitConvergedValues();
        }
    }

    protected double getConverged(String name) {
        Double value = ComputedValues.getConvergedValue(name);
        assertNotNull(value, "No converged value for: " + name);
        return value;
    }
}
