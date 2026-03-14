package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SFCRParser")
class SFCRParserRobustnessTest {

    @Test
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
        void testStrictModeFailsOnMalformedEquationRows() {
                String text =
                                "@equations RejectMe\n" +
                                "  this is definitely not a valid equation row\n" +
                                "@end\n";

                assertThrows(SFCRParser.ParseException.class, () -> SFCRParser.parseToResult(text, true));
        }

    @Test
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
        void testStrictModeFailsOnMissingEnd() {
                String text =
                                "@equations NoEnd\n" +
                                "  A ~ 42\n";

                assertThrows(SFCRParser.ParseException.class, () -> SFCRParser.parseToResult(text, true));
        }

    @Test
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
}
