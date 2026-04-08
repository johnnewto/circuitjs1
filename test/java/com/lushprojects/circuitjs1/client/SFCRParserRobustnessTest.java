package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.io.SFCRParseResult;
import com.lushprojects.circuitjs1.client.io.SFCRParseResultExporter;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

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
