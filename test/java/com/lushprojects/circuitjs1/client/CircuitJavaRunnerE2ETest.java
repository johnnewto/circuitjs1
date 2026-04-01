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
import java.util.regex.Pattern;

import com.lushprojects.circuitjs1.client.elements.economics.ComputedValues;
import com.lushprojects.circuitjs1.client.runner.CircuitJavaRunner;
import com.lushprojects.circuitjs1.client.runner.RuntimeMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("ComputedValues")
@DisplayName("Circuit Java runner end-to-end")
class CircuitJavaRunnerE2ETest {

    @AfterEach
    void resetRuntimeMode() {
        RuntimeMode.setNonInteractiveRuntime(false);
    }

    @Test
    @DisplayName("runs mixed-modes fixture and emits advancing CSV time")
    void runsMixedModesFixtureAndEmitsAdvancingCsvTime() throws Exception {
        Path outputCsv = Files.createTempFile("runner-e2e-", ".csv");
        try {
            CircuitJavaRunner.main(new String[] {
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
        Path outputCsv = Files.createTempFile("runner-e2e-values-", ".csv");
        try {
            CircuitJavaRunner.main(new String[] {
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

        @Test
        @DisplayName("runner preserves param-mode outputs when same-named labeled nodes are present")
        void runnerPreservesParamModeOutputsWithPassiveLabeledNodes() throws Exception {
        Path circuitFile = Files.createTempFile("runner-param-labeled-", ".md");
        Path outputCsv = Files.createTempFile("runner-param-labeled-", ".csv");
        try {
            String circuit = "# CircuitJS1 SFCR Export\n"
                + "# Generated from circuit simulation\n\n"
                + "```{r}\n"
                + "@init\n"
                + "  timestep: 1\n"
                + "  voltageUnit: $\n"
                + "  timeUnit: yr\n"
                + "  showDots: true\n"
                + "  showVolts: true\n"
                + "  showValues: true\n"
                + "  showPower: false\n"
                + "  autoAdjustTimestep: false\n"
                + "  equationTableMnaMode: true\n"
                + "  EqnTable Newton Jacobian: true\n"
                + "  equationTableTolerance: 0.000001\n"
                + "  lookupMode: pwl\n"
                + "  lookupClamp: true\n"
                + "  convergenceCheckThreshold: 100\n"
                + "  infoViewerUpdateIntervalMs: 200\n"
                + "  Auto-Open Model Info on Load: true\n"
                + "@end\n"
                + "```\n\n"
                + "```{r}\n"
                + "EqnTable <- sfcr_set(\n"
                + "  # [ x=336 y=96 invisible=false ]\n"
                + "  # #---------------------------------------------------------------------\n"
                + "  e1 = Yin ~ Ld,  # [mode=param ]\n"
                + "  e2 = Ylast ~ last(Ld),  # [mode=param ]\n"
                + "  e3 = Mh ~ last(Mh) + Yin - Cd  # [mode=voltage ]\n"
                + ")\n"
                + "```\n\n"
                + "```{r}\n"
                + "@circuit\n"
                + "R -32 128 -112 128 0 0 40 5 0 0 0.5 V U:_sFwM6\n"
                + "207 -32 128 32 128 172 Ld U:kot4se\n"
                + "207 -48 208 16 208 164 Yin U:zZQiBY\n"
                + "207 -48 176 16 176 164 Ylast U:KVNsmb\n"
                + "207 64 128 128 128 172 Mh U:testMh\n"
                + "@end\n"
                + "```\n";

            Files.write(circuitFile, circuit.getBytes(StandardCharsets.UTF_8));

            CircuitJavaRunner.main(new String[] {
                circuitFile.toString(),
                outputCsv.toString(),
                "6"
            });

            List<String> lines = Files.readAllLines(outputCsv, StandardCharsets.UTF_8);
            assertEquals(7, lines.size(), "Expected header plus six data rows");

            String[] headers = lines.get(0).split(",");
            int mhIdx = indexOf(headers, "Mh");
            int yinIdx = indexOf(headers, "Yin");
            int ylastIdx = indexOf(headers, "Ylast");
            assertTrue(mhIdx >= 0, "Expected Mh column");
            assertTrue(yinIdx >= 0, "Expected Yin column");
            assertTrue(ylastIdx >= 0, "Expected Ylast column");

            double[] expectedMh = {5, 10, 15, 20, 25, 30};
            double[] expectedYin = {5, 5, 5, 5, 5, 5};
            double[] expectedYlast = {0, 5, 5, 5, 5, 5};

            for (int i = 1; i < lines.size(); i++) {
            String[] cols = lines.get(i).split(",", -1);
            assertEquals((double) i, Double.parseDouble(cols[0]), 1e-9,
                "Expected time column at row " + i);
            assertEquals(expectedMh[i - 1], Double.parseDouble(cols[mhIdx]), 1e-9,
                "Unexpected Mh at row " + i);
            assertEquals(expectedYin[i - 1], Double.parseDouble(cols[yinIdx]), 1e-9,
                "Unexpected Yin at row " + i);
            assertEquals(expectedYlast[i - 1], Double.parseDouble(cols[ylastIdx]), 1e-9,
                "Unexpected Ylast at row " + i);
            }
        } finally {
            Files.deleteIfExists(circuitFile);
            Files.deleteIfExists(outputCsv);
        }
        }

    @Test
    @DisplayName("emits world2 formatted six-column table when format is world2")
    void emitsWorld2FormattedSixColumnTableWhenFormatIsWorld2() throws Exception {
        Path outputTable = Files.createTempFile("runner-world2-", ".tsv");
        try {
            CircuitJavaRunner.main(new String[] {
                    getWorld2CircuitPath().toString(),
                    outputTable.toString(),
                    "5",
                    "world2"
            });

            List<String> lines = Files.readAllLines(outputTable, StandardCharsets.UTF_8);
            assertTrue(lines.size() >= 2, "Expected world2 table with header and at least one data row");

            assertEquals("Year\tPopulation\tPollution Ratio\tCapital Investment\tQuality of Life\tNatural Resources",
                    lines.get(0), "Expected world2 tabular header");

            String[] columns = lines.get(1).split("\t", -1);
            assertEquals(6, columns.length, "Expected exactly six world2 columns");

            assertTrue(Pattern.matches("-?\\d+\\.\\d", columns[0]), "Year should use one decimal place");
            assertTrue(Pattern.matches("-?\\d+\\.\\d{3} [TBMK]", columns[1]), "Population should use SI format");
            assertTrue(Pattern.matches("-?\\d+\\.\\d{4}", columns[2]), "Pollution Ratio should use four decimals");
            assertTrue(Pattern.matches("-?\\d+\\.\\d{3} [TBMK]", columns[3]), "Capital Investment should use SI format");
            assertTrue(Pattern.matches("-?\\d+\\.\\d{4}", columns[4]), "Quality of Life should use four decimals");
            assertTrue(Pattern.matches("-?\\d+\\.\\d{3} [TBMK]", columns[5]), "Natural Resources should use SI format");
        } finally {
            Files.deleteIfExists(outputTable);
        }
    }

    @Test
    @DisplayName("embeds circuit parameter summary in world2 HTML report")
    void embedsCircuitParameterSummaryInWorld2HtmlReport() throws Exception {
        Path outputTable = Files.createTempFile("runner-world2-html-", ".tsv");
        Path outputHtml = Files.createTempFile("runner-world2-html-", ".html");
        try {
            CircuitJavaRunner.main(new String[] {
                    getWorld2CircuitPath().toString(),
                    outputTable.toString(),
                    "3",
                    "world2",
                    outputHtml.toString()
            });

            String html = new String(Files.readAllBytes(outputHtml), StandardCharsets.UTF_8);
            assertTrue(html.contains("Circuit Parameters Used"), "Expected HTML report to include parameter summary heading");
            assertTrue(html.contains("MnaMode"), "Expected HTML report to include MnaMode parameter");
            assertTrue(html.contains("equationTableTolerance"), "Expected HTML report to include equationTableTolerance parameter");
            assertTrue(html.contains("lookupMode"), "Expected HTML report to include lookupMode parameter");
            assertTrue(html.contains("convergenceCheckThreshold"), "Expected HTML report to include convergenceCheckThreshold parameter");
            assertTrue(html.contains("EqnTable Newton Jacobian"), "Expected HTML report to include EqnTable Newton Jacobian parameter");
            assertTrue(html.contains("Auto-Adjust Timestep"), "Expected HTML report to include Auto-Adjust Timestep parameter");
        } finally {
            Files.deleteIfExists(outputTable);
            Files.deleteIfExists(outputHtml);
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

    private int indexOf(String[] headers, String key) {
        for (int i = 0; i < headers.length; i++) {
            if (key.equals(headers[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    private Path getWorld2CircuitPath() {
        String projectDir = System.getProperty("projectDir");
        Path world2Circuit = Paths.get(projectDir, "test/resources/sfcr/world2_fixture.md");
        assertTrue(Files.exists(world2Circuit), "Expected world2 fixture to exist: " + world2Circuit);
        return world2Circuit;
    }
}
