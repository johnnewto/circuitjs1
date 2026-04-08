package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm;
import com.lushprojects.circuitjs1.client.io.SFCRParseResult;
import com.lushprojects.circuitjs1.client.io.SFCRParseResultExporter;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.SFCRTableDumpBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.StringTokenizer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;

@ResourceLock("SFCRParser")
@DisplayName("SFCRParser — strict mode rejects malformed input")
class SFCRParserRobustnessTest {

    @Test
    @DisplayName("tolerant mode: block with no valid rows is silently dropped")
    void testMalformedBlockWithNoValidRowsIsRejected() {
        String text =
                "@equations RejectMe\n" +
                "  this is definitely not a valid equation row\n" +
                "  nor is this one\n" +
                "@end\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);
        assertNull(result.findBlock("equations", "RejectMe"),
                "Malformed equations block with no valid rows should be rejected");
    }

        @Test
        @DisplayName("strict mode: malformed equation rows throw ParseException")
        void testStrictModeFailsOnMalformedEquationRows() {
                String text =
                                "@equations RejectMe\n" +
                                "  this is definitely not a valid equation row\n" +
                                "@end\n";

                assertThrows(SFCRParser.ParseException.class, () -> SFCRParser.parseToResult(text, true));
        }

    @Test
    @DisplayName("tolerant mode: malformed lines ignored, valid rows still parsed")
    void testMalformedEquationLinesAreIgnoredButValidRowsParse() {
        String text =
                "@equations TestMalformed\n" +
                "  this is not a valid row\n" +
                "  X ~ 1\n" +
                "@end\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);

        SFCRParseResult.BlockDump block = result.findBlock("equations", "TestMalformed");
        assertNotNull(block);
        assertTrue(block.dumpString.contains(" X ") || block.dumpString.contains(" X\\s"),
                "Valid row should still be captured in dump");
    }

    @Test
    @DisplayName("tolerant mode: missing @end at EOF is tolerated")
    void testMissingEndStillParsesTrailingEquationBlock() {
        String text =
                "@equations NoEnd\n" +
                "  A ~ 42\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);

        SFCRParseResult.BlockDump block = result.findBlock("equations", "NoEnd");
        assertNotNull(block, "Parser should tolerate missing @end at EOF");
    }

        @Test
        @DisplayName("strict mode: missing @end throws ParseException")
        void testStrictModeFailsOnMissingEnd() {
                String text =
                                "@equations NoEnd\n" +
                                "  A ~ 42\n";

                assertThrows(SFCRParser.ParseException.class, () -> SFCRParser.parseToResult(text, true));
        }

    @Test
    @DisplayName("tolerant mode: mixed R-style and @block-style both parse")
    void testMixedRStyleAndBlockStyleBothParse() {
        String text =
                "RBlock <- sfcr_set(\n" +
                "  r1 = RVal ~ 1\n" +
                ")\n" +
                "@equations BlockStyle\n" +
                "  BVal ~ 2\n" +
                "@end\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);

        assertNotNull(result.findBlock("equations", "RBlock"));
        assertNotNull(result.findBlock("equations", "BlockStyle"));
    }

    @Test
    @DisplayName("tolerant mode: extreme numeric values (1e300, 1e-300) survive round-trip")
    void testExtremeNumericValuesParseAndRoundTrip() {
        String text =
                "@equations Extreme\n" +
                "  Huge ~ 1e300\n" +
                "  Tiny ~ 1e-300\n" +
                "@end\n";

        SFCRParseResult first = SFCRParser.parseToResult(text);
        assertNotNull(first);
        SFCRParseResult.BlockDump block = first.findBlock("equations", "Extreme");
        assertNotNull(block);
        assertTrue(block.dumpString.contains("1e300") || block.dumpString.contains("1.0E300"));

        String exported = SFCRParseResultExporter.export(first);
        SFCRParseResult second = SFCRParser.parseToResult(exported);
        assertNotNull(second);
        assertNotNull(second.findBlock("equations", "Extreme"));
    }

        @Test
        @DisplayName("R-style oversized sfcr_set preserves 256 rows including comments")
        void testOversizedRStyleEquationBlockPreservesCommentsUpToNewLimit() {
                StringBuilder text = new StringBuilder();
                text.append("Big <- sfcr_set(\n");
                for (int i = 1; i <= 254; i++) {
                        text.append("  X").append(i).append(" ~ ").append(i).append(",\n");
                }
                text.append("  # Commercial Banks equations\n");
                text.append("  # trailing comment survives\n");
                text.append(")\n");

                SFCRParseResult result = SFCRParser.parseToResult(text.toString(), true);
                assertNotNull(result);

                SFCRParseResult.BlockDump block = result.findBlock("equations", "Big");
                assertNotNull(block, "Expected oversized equation block to be preserved");
                assertTrue(block.dumpString.contains("X254"),
                                "Last equation row within the 256-row limit should be preserved");
                assertTrue(block.dumpString.contains("\\h\\sCommercial\\sBanks\\sequations"),
                                "Comment rows should be preserved in the table dump");
                assertTrue(block.dumpString.contains("\\h\\strailing\\scomment\\ssurvives"),
                                "Trailing comment row should be preserved in the table dump");

                StringTokenizer st = new StringTokenizer(block.dumpString);
                assertEquals("266", st.nextToken(), "Equation table dump should use type 266");
                st.nextToken(); // x1
                st.nextToken(); // y1
                st.nextToken(); // x2
                st.nextToken(); // y2
                st.nextToken(); // flags
                st.nextToken(); // table name
                assertEquals("256", st.nextToken(),
                                "Equation table should now allow 256 rows including comments");
        }

        @Test
        @DisplayName("oversized equation dump reports truncation metadata")
        void testEquationDumpTruncationMetadata() {
                SFCRTableDumpBuilderService service = new SFCRTableDumpBuilderService();
                java.util.ArrayList<String> outputNames = new java.util.ArrayList<String>();
                java.util.ArrayList<String> equations = new java.util.ArrayList<String>();
                java.util.ArrayList<Integer> outputModes = new java.util.ArrayList<Integer>();
                java.util.ArrayList<String> targetNodeNames = new java.util.ArrayList<String>();
                java.util.ArrayList<String> sliderVarNames = new java.util.ArrayList<String>();
                java.util.ArrayList<Double> sliderValues = new java.util.ArrayList<Double>();
                java.util.ArrayList<String> initialEquations = new java.util.ArrayList<String>();

                int totalRows = EquationTableElm.MAX_ROWS + 10;
                for (int i = 0; i < totalRows; i++) {
                        outputNames.add(i == totalRows - 1 ? "# limit message candidate" : "X" + i);
                        equations.add(i == totalRows - 1 ? "" : Integer.toString(i));
                        outputModes.add(Integer.valueOf(i == totalRows - 1 ? 3 : 0));
                        targetNodeNames.add("");
                        sliderVarNames.add("");
                        sliderValues.add(Double.valueOf(0));
                        initialEquations.add("");
                }

                SFCRTableDumpBuilderService.DumpBuildResult build = service.buildEquationDump(
                                "TooBig", 176, 24, outputNames, equations, outputModes, targetNodeNames,
                                sliderVarNames, sliderValues, initialEquations, null);

                assertNotNull(build);
                assertTrue(build.truncated, "Oversized equation tables should report truncation");
                assertEquals(totalRows, build.originalRowCount);
                assertEquals(EquationTableElm.MAX_ROWS, build.finalRowCount);
        }

        @Test
        @DisplayName("strict mode: duplicate non-simple initial import is preserved as commented warning")
        void duplicateNonSimpleInitialImportIsCommentedInStrictMode() {
                String text =
                                "growth_eqs <- sfcr_set(\n" +
                                "  INke ~ INk[-1] + gamma*(INkt - INk[-1])\n" +
                                ")\n\n" +
                                "growth_initial <- sfcr_set(\n" +
                                "  INke ~ Ske + 10\n" +
                                ")\n";

                SFCRParseResult result = SFCRParser.parseToResult(text, true);

                assertNotNull(result);
                SFCRParseResult.BlockDump block = result.findBlock("equations", "growth_initial");
                assertNotNull(block);
                assertTrue(block.dumpString.contains("Exception\\scaught:\\sDuplicate\\svariable"),
                                "Commented duplicate warning should survive parse-to-result in strict mode");
        }
}
