package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.io.SFCRParseResult;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain-Java unit tests for the SFCR parse-to-result path.
 *
 * <p>These tests run on a standard JVM with no GWT, no CirSim, and no
 * browser.  They exercise the text-parsing and dump-string generation
 * logic of {@link SFCRParser#parseToResult(String)} using the circuit
 * fixture file {@code test/resources/sfcr/parse_result_fixture.md}.
 *
 * <p>Run with: {@code ./gradlew test --tests "*.SFCRParseResultTest"}
 */
@ResourceLock("SFCRParser")
@DisplayName("SFCRParser.parseToResult() — full fixture parse")
class SFCRParseResultTest {

    private static String sfcrText;
    private static SFCRParseResult result;

    @BeforeAll
    static void loadAndParse() throws Exception {
        sfcrText = TestFixtures.loadSfcr("parse_result_fixture.md");
        result = SFCRParser.parseToResult(sfcrText);
    }

    // -------------------------------------------------------------------------
    // Null / empty input
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("null input returns null")
    void testNullInputReturnsNull() {
        assertNull(SFCRParser.parseToResult(null));
    }

    @Test
    @DisplayName("blank/whitespace-only input returns null")
    void testEmptyInputReturnsNull() {
        assertNull(SFCRParser.parseToResult("   "));
    }

    // -------------------------------------------------------------------------
    // Result is not null for a valid file
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("valid SFCR text returns non-null result")
    void testResultNotNull() {
        assertNotNull(result, "parseToResult() must return a non-null result for valid SFCR text");
    }

    // -------------------------------------------------------------------------
    // @init settings
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("@init timestep is parsed")
    void testTimestepParsed() {
        assertEquals("1", result.initSettings.get("timestep"),
            "@init timestep must be '1'");
    }

    @Test
    @DisplayName("@init voltageUnit is parsed")
    void testVoltageUnitParsed() {
        assertEquals("$", result.initSettings.get("voltageUnit"),
                "@init voltageUnit must be '$'");
    }

    @Test
    @DisplayName("@init timeUnit is parsed")
    void testTimeUnitParsed() {
        assertEquals("yr", result.initSettings.get("timeUnit"),
                "@init timeUnit must be 'yr'");
    }

    @Test
    @DisplayName("@init showDots is parsed")
    void testShowDotsParsed() {
        assertEquals("true", result.initSettings.get("showDots"),
            "@init showDots must be 'true'");
    }

    @Test
    @DisplayName("@init autoOpenModelInfoOnLoad is parsed")
    void testAutoOpenModelInfoOnLoadParsed() {
        SFCRParseResult parsed = SFCRParser.parseToResult("@init autoOpenModelInfoOnLoad=false");
        assertNotNull(parsed, "parseToResult() should return a result for inline @init input");
        assertEquals("false", parsed.initSettings.get("autoOpenModelInfoOnLoad"),
            "@init autoOpenModelInfoOnLoad must be 'false'");
    }

    // -------------------------------------------------------------------------
    // Equation blocks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fixture contains 3 equation blocks")
    void testThreeEquationBlocksFound() {
        List<SFCRParseResult.BlockDump> eqBlocks = result.getBlocksByType("equations");
        assertEquals(3, eqBlocks.size(),
                "Expected 3 equation blocks from sfcr_set assignments (equations_1A, equations_2, Parameters)");
    }

    @Test
    @DisplayName("equation block names: equations_1A, equations_2, Parameters")
    void testEquationBlockNamesPresent() {
        List<SFCRParseResult.BlockDump> eqBlocks = result.getBlocksByType("equations");
        boolean hasEq1 = false, hasEq2 = false, hasParameters = false;
        for (SFCRParseResult.BlockDump bd : eqBlocks) {
            String n = bd.blockName;
            if ("equations_1A".equals(n)) hasEq1 = true;
            if ("equations_2".equals(n))  hasEq2 = true;
            if ("Parameters".equals(n))   hasParameters = true;
        }
        assertTrue(hasEq1, "Missing 'equations_1A' block");
        assertTrue(hasEq2, "Missing 'equations_2' block");
        assertTrue(hasParameters, "Missing 'Parameters' block");
    }

    @Test
    @DisplayName("equations_1A dump contains YD and \\theta")
        void testEquationDumpContainsYDAndTheta() {
        SFCRParseResult.BlockDump block = result.findBlock("equations", "equations_1A");
        assertNotNull(block, "equations_1A block must be present");
        assertTrue(block.dumpString.contains("YD"),
            "equations_1A dump must contain 'YD'");
        assertTrue(block.dumpString.contains("\\theta"),
            "equations_1A dump must contain '\\theta'");
    }

    @Test
    @DisplayName("every equation dump starts with '266 ' (EquationTableElm type)")
    void testDumpStartsWithType266() {
        // EquationTableElm type number is 266
        for (SFCRParseResult.BlockDump bd : result.getBlocksByType("equations")) {
            assertTrue(bd.dumpString.startsWith("266 "),
                    "Equation block dump must start with '266 ' (EquationTableElm type): " + bd.blockName);
        }
    }

    @Test
    @DisplayName("fixture contains 2 matrix blocks")
    void testTwoMatrixBlocksFound() {
        assertEquals(2, result.getBlocksByType("matrix").size(),
                "Expected 2 matrix blocks (Balance_Sheet, Transaction_Flow_Matrix)");
    }

    @Test
    @DisplayName("matrix block names: Balance_Sheet, Transaction_Flow_Matrix")
    void testMatrixBlockNamesPresent() {
        List<SFCRParseResult.BlockDump> matrixBlocks = result.getBlocksByType("matrix");
        boolean hasBalanceSheet = false, hasTFM = false;
        for (SFCRParseResult.BlockDump bd : matrixBlocks) {
            if ("Balance_Sheet".equals(bd.blockName)) hasBalanceSheet = true;
            if ("Transaction_Flow_Matrix".equals(bd.blockName)) hasTFM = true;
        }
        assertTrue(hasBalanceSheet, "Missing 'Balance_Sheet' matrix block");
        assertTrue(hasTFM, "Missing 'Transaction_Flow_Matrix' matrix block");
    }

    @Test
    @DisplayName("fixture contains 1 sankey block with dump type 466")
    void testOneSankeyBlockCaptured() {
        List<SFCRParseResult.BlockDump> sankeyBlocks = result.getBlocksByType("sankey");
        assertEquals(1, sankeyBlocks.size(),
            "Expected 1 sankey block captured from @sankey in parse_result_fixture.md");
        assertTrue(sankeyBlocks.get(0).dumpString.startsWith("466 "),
                "Sankey dump must start with '466 ' (SFCSankeyElm type)");
    }

        @Test
        @DisplayName("@plantuml block parses to SequenceDiagramElm dump type 467")
        void testPlantUmlBlockCaptured() {
        String text =
            "@plantuml x=200 y=120\n" +
            "width: 640\n" +
            "scale: 1.5\n" +
            "@startuml\n" +
            "actor Households\n" +
            "participant Firms\n" +
            "Households -> Firms : Demand\n" +
            "@enduml\n" +
            "@end\n";

        SFCRParseResult parsed = SFCRParser.parseToResult(text);
        assertNotNull(parsed);

        List<SFCRParseResult.BlockDump> plantUmlBlocks = parsed.getBlocksByType("plantuml");
        assertEquals(1, plantUmlBlocks.size(), "Expected one parsed PlantUML block");
        assertTrue(plantUmlBlocks.get(0).dumpString.startsWith("467 "),
            "PlantUML dump must start with '467 ' (SequenceDiagramElm type)");
        assertTrue(plantUmlBlocks.get(0).dumpString.endsWith(" 640 1.5"),
            "PlantUML dump should preserve parsed width and scale metadata");
        }

        @Test
        @DisplayName("fenced PlantUML block loads as SequenceDiagramElm without explicit @plantuml wrapper")
        void testFencedPlantUmlBlockCaptured() {
        String text =
                "```{PlantUML}\n" +
            "@startuml x=672 y=-8 width=640 scale=1.25\n" +
            "actor Households\n" +
            "participant Firms\n" +
            "Households -> Firms : Demand\n" +
            "@enduml\n" +
            "```\n";

        SFCRParseResult parsed = SFCRParser.parseToResult(text);
        assertNotNull(parsed);

        List<SFCRParseResult.BlockDump> plantUmlBlocks = parsed.getBlocksByType("plantuml");
        assertEquals(1, plantUmlBlocks.size(), "Expected one parsed fenced PlantUML block");
        assertTrue(plantUmlBlocks.get(0).dumpString.startsWith("467 "),
            "Fenced PlantUML must instantiate dump type 467");
        assertTrue(plantUmlBlocks.get(0).dumpString.startsWith("467 672 -8 "),
            "Fenced PlantUML x/y should be parsed from the @startuml line");
        assertTrue(plantUmlBlocks.get(0).dumpString.endsWith(" 560 1.25"),
            "Fenced PlantUML render width should ignore @startuml width while scale is preserved");
        }

    // -------------------------------------------------------------------------
    // Hints
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("inline # comments populate hints map")
    void testHintsPopulatedFromInlineComments() {
        // Inline comments in sfcr_set rows populate hints
        assertTrue(result.hints.containsKey("YD"),
            "Hints map must contain 'YD' (from inline # comment)");
        String ydHint = result.hints.get("YD");
        assertTrue(ydHint.contains("Disposable Income"),
            "YD hint must contain 'Disposable Income', got: " + ydHint);
    }

    @Test
    @DisplayName("hints map contains 'Y' from inline sfcr_set comment")
    void testHintsFromHintsBlock() {
        // This markdown fixture has no @hints block, but inline hints should still exist.
        assertTrue(result.hints.containsKey("Y"),
            "Hints map must contain 'Y' from inline sfcr_set comment");
    }

    // -------------------------------------------------------------------------
    // escapeToken helper
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("empty string escapes to \\0")
    void testEscapeTokenEmpty() {
        assertEquals("\\0", SFCRParser.escapeToken(""));
    }

    @Test
    @DisplayName("spaces in token escape to \\s")
    void testEscapeTokenSpaces() {
        assertEquals("hello\\sworld", SFCRParser.escapeToken("hello world"));
    }

    @Test
    @DisplayName("=, space, + escape to \\q, \\s, \\p respectively")
    void testEscapeTokenSpecialChars() {
        // backslash first, then equals, space, plus
        String result = SFCRParser.escapeToken("a=b c+d");
        assertTrue(result.contains("\\q"), "= must be escaped to \\q");
        assertTrue(result.contains("\\s"), "space must be escaped to \\s");
        assertTrue(result.contains("\\p"), "+ must be escaped to \\p");
    }

    // -------------------------------------------------------------------------
    // parseModeOrdinal helper
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("null / 'voltage' → ordinal 0")
    void testParseModeOrdinalVoltage() {
        assertEquals(0, SFCRParser.parseModeOrdinal(null));
        assertEquals(0, SFCRParser.parseModeOrdinal("voltage"));
        assertEquals(0, SFCRParser.parseModeOrdinal("VOLTAGE"));
    }

    @Test
    @DisplayName("'flow' / 'stock' → ordinal 1")
    void testParseModeOrdinalFlow() {
        assertEquals(1, SFCRParser.parseModeOrdinal("flow"));
        assertEquals(1, SFCRParser.parseModeOrdinal("stock"));
        assertEquals(1, SFCRParser.parseModeOrdinal("stock_mode"));
    }

    @Test
    @DisplayName("'param' / 'parameter' → ordinal 3")
    void testParseModeOrdinalParam() {
        assertEquals(3, SFCRParser.parseModeOrdinal("param"));
        assertEquals(3, SFCRParser.parseModeOrdinal("parameter"));
        assertEquals(3, SFCRParser.parseModeOrdinal("PARAM_MODE"));
    }

    // -------------------------------------------------------------------------
    // parseCombinedNameLocal helper
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("name with no separator returns full name and empty target")
    void testParseCombinedNameNoSeparator() {
        String[] parts = SFCRParser.parseCombinedNameLocal("myVar");
        assertEquals("myVar", parts[0]);
        assertEquals("",      parts[1]);
    }

    @Test
    @DisplayName("arrow syntax splits into source and target")
    void testParseCombinedNameArrow() {
        String[] parts = SFCRParser.parseCombinedNameLocal("source->target");
        assertEquals("source", parts[0]);
        assertEquals("target", parts[1]);
    }

    @Test
    @DisplayName("comma syntax splits into name and target")
    void testParseCombinedNameComma() {
        String[] parts = SFCRParser.parseCombinedNameLocal("A , B");
        assertEquals("A", parts[0]);
        assertEquals("B", parts[1]);
    }

    @Test
    @DisplayName("null input returns two empty strings")
    void testParseCombinedNameNull() {
        String[] parts = SFCRParser.parseCombinedNameLocal(null);
        assertEquals("", parts[0]);
        assertEquals("", parts[1]);
    }
}
