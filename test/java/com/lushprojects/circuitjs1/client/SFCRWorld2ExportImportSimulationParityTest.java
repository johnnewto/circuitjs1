package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("ComputedValues")
@ResourceLock("SFCRParser")
@DisplayName("World2 export/import simulation parity")
class SFCRWorld2ExportImportSimulationParityTest {

    @Test
    @DisplayName("exported+reloaded world2 matches original P/CI/NR/POLR at 50 years")
    void world2ExportImportMatchesAt50Years() throws Exception {
        String world2Text = readProjectFile("src/com/lushprojects/circuitjs1/public/circuits/economics/world2_forrester.md");

        SFCRParseResult parsed = SFCRParser.parseToResult(world2Text);
        String exportedText = SFCRParseResultExporter.export(parsed);
        assertNotNull(exportedText, "Exported text should not be null");
        assertTrue(!exportedText.trim().isEmpty(), "Exported text should not be empty");

        SimulationSnapshot baseline = runToYears(world2Text, 50.0);
        SimulationSnapshot roundTrip = runToYears(exportedText, 50.0);

        assertNear("t", baseline.t, roundTrip.t, 1e-9);
        assertRelativeNear("P", baseline.values.get("P"), roundTrip.values.get("P"), 1e-8);
        assertRelativeNear("CI", baseline.values.get("CI"), roundTrip.values.get("CI"), 1e-8);
        assertRelativeNear("NR", baseline.values.get("NR"), roundTrip.values.get("NR"), 1e-8);
        assertRelativeNear("POLR", baseline.values.get("POLR"), roundTrip.values.get("POLR"), 1e-8);
    }

    @Test
    @DisplayName("live SFCRExporter round-trip matches original P/CI/NR/POLR at 50 years")
    void world2LiveExporterRoundTripMatchesAt50Years() throws Exception {
        String world2Text = readProjectFile("src/com/lushprojects/circuitjs1/public/circuits/economics/world2_forrester.md");

        String liveExportedText;
        try {
            CirSim sourceSim = createRunnerSim(world2Text);
            liveExportedText = new SFCRExporter(sourceSim, SFCRExporter.ExportSyntax.R_STYLE).export();
        } finally {
            RuntimeMode.setNonInteractiveRuntime(false);
        }

        assertNotNull(liveExportedText, "Live exported text should not be null");
        assertTrue(!liveExportedText.trim().isEmpty(), "Live exported text should not be empty");

        SimulationSnapshot baseline = runToYears(world2Text, 50.0);
        SimulationSnapshot roundTrip = runToYears(liveExportedText, 50.0);

        assertNear("t", baseline.t, roundTrip.t, 1e-9);
        assertRelativeNear("P", baseline.values.get("P"), roundTrip.values.get("P"), 1e-8);
        assertRelativeNear("CI", baseline.values.get("CI"), roundTrip.values.get("CI"), 1e-8);
        assertRelativeNear("NR", baseline.values.get("NR"), roundTrip.values.get("NR"), 1e-8);
        assertRelativeNear("POLR", baseline.values.get("POLR"), roundTrip.values.get("POLR"), 1e-8);
    }

    private SimulationSnapshot runToYears(String circuitText, double years) throws Exception {
        try {
            CirSim sim = createRunnerSim(circuitText);

            int step = 0;
            int maxSteps = 200000;
            double prevT = sim.t;
            while (sim.t < years && step < maxSteps) {
                sim.runCircuit(step == 0);
                ComputedValues.commitConvergedValues();

                assertTrue(sim.stopMessage == null, "Simulation stopped unexpectedly: " + sim.stopMessage);
                assertTrue(sim.t >= prevT, "Simulation time moved backwards");

                prevT = sim.t;
                step++;
            }

            assertTrue(sim.t >= years, "Simulation did not reach target year " + years + " (t=" + sim.t + ")");

            LinkedHashMap<String, Double> values = new LinkedHashMap<String, Double>();
            values.put("P", getConverged("P"));
            values.put("CI", getConverged("CI"));
            values.put("NR", getConverged("NR"));
            values.put("POLR", getConverged("POLR"));

            return new SimulationSnapshot(sim.t, values);
        } finally {
            RuntimeMode.setNonInteractiveRuntime(false);
        }
    }

    private CirSim createRunnerSim(String circuitText) throws Exception {
        RuntimeMode.setNonInteractiveRuntime(true);
        ComputedValues.resetForTesting();

        CirSim sim = new CirSim();
        sim.initHeadless();
        sim.readCircuit(circuitText, 0);
        sim.analyzeCircuit();
        sim.preStampAndStampCircuit();
        return sim;
    }

    private double getConverged(String name) {
        Double value = ComputedValues.getConvergedValue(name);
        assertNotNull(value, "Missing converged value: " + name);
        return value.doubleValue();
    }

    private String readProjectFile(String relativePath) throws Exception {
        Path path = Paths.get(System.getProperty("projectDir"), relativePath);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private void assertNear(String label, double expected, double actual, double tol) {
        double diff = Math.abs(expected - actual);
        assertTrue(diff <= tol, label + " differs: expected=" + expected + ", actual=" + actual + ", diff=" + diff);
    }

    private void assertRelativeNear(String label, double expected, double actual, double relTol) {
        double scale = Math.max(1.0, Math.abs(expected));
        double tol = scale * relTol;
        double diff = Math.abs(expected - actual);
        assertTrue(diff <= tol,
                label + " differs: expected=" + expected + ", actual=" + actual + ", diff=" + diff + ", tol=" + tol);
    }

    private static class SimulationSnapshot {
        final double t;
        final Map<String, Double> values;

        SimulationSnapshot(double t, Map<String, Double> values) {
            this.t = t;
            this.values = values;
        }
    }
}
