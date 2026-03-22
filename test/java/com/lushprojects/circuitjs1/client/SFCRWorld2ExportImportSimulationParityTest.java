package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertRoundTripBlockInventory("parse-result exporter", world2Text, exportedText);
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

        assertRoundTripBlockInventory("live exporter", world2Text, liveExportedText);
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
        sim.getBootstrap().initRunner();
        sim.getCircuitIOService().readCircuit(circuitText, 0);
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

    private void assertRoundTripBlockInventory(String label, String originalText, String exportedText) {
        SFCRParseResult original = SFCRParser.parseToResult(originalText);
        SFCRParseResult roundTrip = SFCRParser.parseToResult(exportedText);

        assertNotNull(original, label + " original parse should not be null");
        assertNotNull(roundTrip, label + " round-trip parse should not be null");

        Map<String, Integer> originalByType = countBlocksByType(original);
        Map<String, Integer> roundTripByType = countBlocksByType(roundTrip);
        assertEquals(originalByType, roundTripByType,
                label + " block count-by-type changed (possible omission/duplication)");

        assertNoDuplicateNamedBlocks(label + " original", original,
                Arrays.asList("lookup", "equations", "parameters", "matrix", "sankey", "scope"));
        assertNoDuplicateNamedBlocks(label + " round-trip", roundTrip,
                Arrays.asList("lookup", "equations", "parameters", "matrix", "sankey", "scope"));

        Set<String> originalLookupKeys = collectNamedBlockKeysForType(original, "lookup");
        Set<String> roundTripLookupKeys = collectNamedBlockKeysForType(roundTrip, "lookup");
        assertEquals(originalLookupKeys, roundTripLookupKeys,
                label + " lookup block key set changed (possible missing or extra lookup tables)");
    }

    private Map<String, Integer> countBlocksByType(SFCRParseResult result) {
        HashMap<String, Integer> counts = new HashMap<String, Integer>();
        if (result == null || result.blockDumps == null) {
            return counts;
        }
        for (SFCRParseResult.BlockDump block : result.blockDumps) {
            if (block == null || block.blockType == null) {
                continue;
            }
            String type = block.blockType.trim().toLowerCase();
            if (type.isEmpty()) {
                continue;
            }
            Integer current = counts.get(type);
            counts.put(type, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
        }
        return counts;
    }

    private void assertNoDuplicateNamedBlocks(String label, SFCRParseResult result, List<String> types) {
        HashMap<String, Integer> keyCounts = countNamedBlockKeys(result, types);
        for (Map.Entry<String, Integer> entry : keyCounts.entrySet()) {
            assertTrue(entry.getValue().intValue() <= 1,
                    label + " has duplicated named block: " + entry.getKey() + " count=" + entry.getValue());
        }
    }

    private HashMap<String, Integer> countNamedBlockKeys(SFCRParseResult result, List<String> types) {
        HashSet<String> allowed = new HashSet<String>();
        for (int i = 0; i < types.size(); i++) {
            String type = types.get(i);
            if (type != null) {
                allowed.add(type.trim().toLowerCase());
            }
        }

        HashMap<String, Integer> keyCounts = new HashMap<String, Integer>();
        if (result == null || result.blockDumps == null) {
            return keyCounts;
        }

        for (SFCRParseResult.BlockDump block : result.blockDumps) {
            if (block == null || block.blockType == null || block.blockName == null) {
                continue;
            }
            String type = block.blockType.trim().toLowerCase();
            String name = canonicalizeBlockName(type, block.blockName.trim());
            if (!allowed.contains(type) || name.isEmpty()) {
                continue;
            }
            String key = type + "|" + name;
            Integer current = keyCounts.get(key);
            keyCounts.put(key, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
        }

        return keyCounts;
    }

    private Set<String> collectNamedBlockKeysForType(SFCRParseResult result, String wantedType) {
        HashSet<String> keys = new HashSet<String>();
        if (result == null || result.blockDumps == null || wantedType == null) {
            return keys;
        }
        String normalizedType = wantedType.trim().toLowerCase();
        for (SFCRParseResult.BlockDump block : result.blockDumps) {
            if (block == null || block.blockType == null || block.blockName == null) {
                continue;
            }
            String type = block.blockType.trim().toLowerCase();
            String name = canonicalizeBlockName(type, block.blockName.trim());
            if (normalizedType.equals(type) && !name.isEmpty()) {
                keys.add(type + "|" + name);
            }
        }
        return keys;
    }

    private String canonicalizeBlockName(String type, String rawName) {
        if (rawName == null) {
            return "";
        }
        String name = rawName.trim();
        if (name.isEmpty()) {
            return name;
        }
        if ("lookup".equals(type)) {
            int scopeSep = name.indexOf(':');
            String scope = "";
            String local = name;
            if (scopeSep >= 0) {
                scope = name.substring(0, scopeSep + 1);
                local = name.substring(scopeSep + 1);
            }
            if (local.endsWith("_lookup") && local.length() > "_lookup".length()) {
                local = local.substring(0, local.length() - "_lookup".length());
            }
            return scope + local;
        }
        return name;
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
