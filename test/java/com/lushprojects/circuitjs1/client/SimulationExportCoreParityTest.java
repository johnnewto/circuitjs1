package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SimulationExportCore parity formatting")
class SimulationExportCoreParityTest {

    @Test
    @DisplayName("formats SI values consistently for world2 output")
    void formatsSiValuesConsistentlyForWorld2Output() {
        assertEquals("1.650 B", SimulationExportCore.fmtSI(1_650_000_000d));
        assertEquals("400.000 M", SimulationExportCore.fmtSI(400_000_000d));
        assertEquals("900.000 B", SimulationExportCore.fmtSI(900_000_000_000d));
        assertEquals("0.6116", SimulationExportCore.fmtSI(0.6116d));
    }

    @Test
    @DisplayName("appends large JSON numbers without overflow artifacts")
    void appendsLargeJsonNumbersWithoutOverflowArtifacts() {
        StringBuilder out = new StringBuilder();
        SimulationExportCore.appendJsonNumber(out, 1_650_000_000d);

        assertEquals("1650000000", out.toString());
        assertTrue(!out.toString().startsWith("9223"), "JSON value should not be clamped by rounding overflow");
    }

    @Test
    @DisplayName("world2 HTML embeds full precision row data for large values")
    void world2HtmlEmbedsFullPrecisionRowDataForLargeValues() {
        List<SimulationExportCore.World2Row> rows = new ArrayList<SimulationExportCore.World2Row>();
        rows.add(new SimulationExportCore.World2Row(0.2d, 1_650_000_000d, 0.0556d, 400_000_000d, 0.6116d, 900_000_000_000d));

        SimulationExportCore.RunParameters params = new SimulationExportCore.RunParameters();
        params.circuitPath = "embedded";
        params.outputPath = "(browser)";
        params.htmlPath = "(browser)";
        params.stepsRequested = 1;
        params.outputFormat = "world2";
        params.world2Format = true;
        params.timestep = 0.2;
        params.currentTimeStep = 0.2;
        params.timeUnit = "yr";
        params.mnaMode = true;
        params.equationTableTolerance = 0.001;
        params.lookupMode = "pwl";
        params.convergenceCheckThreshold = 199;
        params.eqnTableNewtonJacobian = false;
        params.autoAdjustTimestep = false;
        params.minTimeStep = 5e-11;
        params.maxTimeStep = 0.2;
        params.lookupClamp = true;
        params.computedValueCount = 50;

        String html = SimulationExportCore.buildWorld2HtmlReport("embedded", 1, rows, params);

        assertTrue(html.contains("\"P\":1650000000"), "Expected unscaled P value in embedded JSON");
        assertTrue(html.contains("\"CI\":400000000"), "Expected unscaled CI value in embedded JSON");
        assertTrue(html.contains("\"NR\":900000000000"), "Expected unscaled NR value in embedded JSON");
    }
}
