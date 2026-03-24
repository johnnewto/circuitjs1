package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.lushprojects.circuitjs1.client.SimulationExportCore;

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

    @Test
    @DisplayName("runner status content escapes message and includes format hint")
    void runnerStatusContentEscapesMessageAndIncludesFormatHint() {
        String html = SimulationExportCore.buildRunnerStatusContentHtml("loading <model> & \"params\"");

        assertTrue(html.contains("runner-status-message"), "Expected status message container id");
        assertTrue(html.contains("loading &lt;model&gt; &amp; &quot;params&quot;"), "Expected escaped status message");
        assertTrue(html.contains("default is <b>tsv</b>"), "Expected format hint text");
    }

    @Test
    @DisplayName("runner error content escapes message and applies error style")
    void runnerErrorContentEscapesMessageAndAppliesErrorStyle() {
        String html = SimulationExportCore.buildRunnerErrorContentHtml("bad <input> & \"format\"");

        assertTrue(html.contains("color:#c33"), "Expected error color style");
        assertTrue(html.contains("bad &lt;input&gt; &amp; &quot;format&quot;"), "Expected escaped error message");
    }

    @Test
    @DisplayName("runner summary content includes key metadata and escaped values")
    void runnerSummaryContentIncludesMetadataAndEscapesValues() {
        String html = SimulationExportCore.buildRunnerSummaryContentHtml("embedded <src>", 25, "tsv", 24);

        assertTrue(html.contains("<b>Source:</b> embedded &lt;src&gt;"), "Expected escaped source value");
        assertTrue(html.contains("<b>Requested steps:</b> 25"), "Expected requested steps value");
        assertTrue(html.contains("<b>Output format:</b> tsv"), "Expected output format value");
        assertTrue(html.contains("<b>Completed steps:</b> 24"), "Expected completed steps value");
        assertTrue(html.contains("default is <b>tsv</b>"), "Expected format hint text");
    }

    @Test
    @DisplayName("runner world2 raw output wraps escaped preformatted text")
    void runnerWorld2RawOutputWrapsEscapedPreformattedText() {
        String html = SimulationExportCore.buildRunnerWorld2RawOutputHtml("x<y & \"z\"");

        assertTrue(html.contains("<pre"), "Expected preformatted block");
        assertTrue(html.contains("x&lt;y &amp; &quot;z&quot;"), "Expected escaped output text");
    }

    @Test
    @DisplayName("runner world2 report tab escapes iframe srcdoc")
    void runnerWorld2ReportTabEscapesIframeSrcdoc() {
        String html = SimulationExportCore.buildRunnerWorld2ReportTabHtml("<html><body>\"x\" & y</body></html>");

        assertTrue(html.contains("runner-report-tab"), "Expected report tab container id");
        assertTrue(html.contains("&lt;html&gt;&lt;body&gt;&quot;x&quot; &amp; y&lt;/body&gt;&lt;/html&gt;"), "Expected escaped srcdoc content");
    }

    @Test
    @DisplayName("runner table helpers escape cells and render header")
    void runnerTableHelpersEscapeCellsAndRenderHeader() {
        List<String> keys = new ArrayList<String>();
        keys.add("A<B>");
        keys.add("C&D");

        String cell = SimulationExportCore.buildRunnerTableCell("x<y & \"z\"");
        String header = SimulationExportCore.buildRunnerTableHeader(keys);

        assertTrue(cell.contains("x&lt;y &amp; &quot;z&quot;"), "Expected escaped table cell value");
        assertTrue(header.contains("<th>t</th>"), "Expected time column in header");
        assertTrue(header.contains("A&lt;B&gt;"), "Expected escaped header key A<B>");
        assertTrue(header.contains("C&amp;D"), "Expected escaped header key C&D");
    }

    @Test
    @DisplayName("runner table status and tab shell render escaped content")
    void runnerTableStatusAndTabShellRenderEscapedContent() {
        String status = SimulationExportCore.buildRunnerTableStatusContentHtml("loading <table> & \"data\"");
        String tabbed = SimulationExportCore.buildRunnerTableTabbedHtml("Output <Table>", status, "line1<br/>line2");

        assertTrue(status.contains("runner-status-message"), "Expected status message id");
        assertTrue(status.contains("loading &lt;table&gt; &amp; &quot;data&quot;"), "Expected escaped status text");
        assertTrue(tabbed.contains("Runner Output Table"), "Expected runner table title");
        assertTrue(tabbed.contains("Output &lt;Table&gt;"), "Expected escaped tab title");
        assertTrue(tabbed.contains("line1<br/>line2"), "Expected provided stdout html");
    }
}
