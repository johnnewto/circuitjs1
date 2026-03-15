package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("ComputedValues")
@DisplayName("Headless runner end-to-end")
class HeadlessRunnerE2ETest {

    @AfterEach
    void resetRuntimeMode() {
        RuntimeMode.setHeadless(false);
    }

    @Test
    @DisplayName("runs mixed-modes fixture and emits advancing CSV time")
    void runsMixedModesFixtureAndEmitsAdvancingCsvTime() throws Exception {
        Path outputCsv = Files.createTempFile("headless-runner-e2e-", ".csv");
        try {
            HeadlessRunner.main(new String[] {
                    getMixedModesFixturePath().toString(),
                    outputCsv.toString(),
                    "5"
            });

            List<String> lines = Files.readAllLines(outputCsv, StandardCharsets.UTF_8);
            assertTrue(lines.size() >= 3, "Expected CSV with header and at least two data rows");

            String header = lines.get(0);
            assertTrue(header.startsWith("t"), "CSV header should start with time column");

            double firstT = Double.parseDouble(lines.get(1).split(",", 2)[0]);
            double lastT = Double.parseDouble(lines.get(lines.size() - 1).split(",", 2)[0]);
            assertTrue(lastT > firstT, "Simulation time should advance across output rows");
        } finally {
            Files.deleteIfExists(outputCsv);
        }
    }

    @Test
    @DisplayName("produces stable key values at step 5 for reference model")
    void producesStableKeyValuesAtStepFiveForReferenceModel() throws Exception {
        Path outputCsv = Files.createTempFile("headless-runner-e2e-values-", ".csv");
        try {
            HeadlessRunner.main(new String[] {
                    getReferenceCircuitPath().toString(),
                    outputCsv.toString(),
                    "5"
            });

            List<String> lines = Files.readAllLines(outputCsv, StandardCharsets.UTF_8);
            assertTrue(lines.size() >= 6, "Expected header plus five data rows");

            String[] headers = lines.get(0).split(",");
            String[] values = lines.get(lines.size() - 1).split(",");
            assertEquals(headers.length, values.length, "CSV header/value column counts should match");

            Map<String, Double> row = new HashMap<String, Double>();
            for (int i = 0; i < headers.length; i++) {
                String key = headers[i].trim();
                String raw = values[i].trim();
                assertFalse(raw.isEmpty(), "Expected non-empty value for column: " + key);
                row.put(key, Double.valueOf(Double.parseDouble(raw)));
            }

            double t = row.get("t").doubleValue();
            double y = row.get("Y").doubleValue();
            double yd = row.get("YD").doubleValue();
            double gd = row.get("G_d").doubleValue();

            assertEquals(5.0, t, 1e-9, "Expected final simulation time at step 5");
            assertTrue(y > 69.8 && y < 70.1, "Y should remain within expected stable range at step 5");
            assertTrue(yd > 55.8 && yd < 56.1, "YD should remain within expected stable range at step 5");
            assertEquals(20.0, gd, 1e-9, "G_d should remain fixed at the configured exogenous level");
        } finally {
            Files.deleteIfExists(outputCsv);
        }
    }

    private Path getReferenceCircuitPath() {
        String projectDir = System.getProperty("projectDir");
        Path referenceCircuit = Paths.get(projectDir, "test/resources/sfcr_debug_reference.md");
        assertTrue(Files.exists(referenceCircuit), "Expected test resource fixture to exist: " + referenceCircuit);
        return referenceCircuit;
    }

    private Path getMixedModesFixturePath() {
        String projectDir = System.getProperty("projectDir");
        Path mixedModes = Paths.get(projectDir, "test/resources/sfcr/mixed_modes_fixture.md");
        assertTrue(Files.exists(mixedModes), "Expected mixed-modes fixture to exist: " + mixedModes);
        return mixedModes;
    }
}
