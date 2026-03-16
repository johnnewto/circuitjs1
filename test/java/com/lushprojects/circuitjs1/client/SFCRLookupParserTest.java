package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SFCRParser")
@DisplayName("SFCRParser — @lookup rewrite and validation")
class SFCRLookupParserTest {

    @Test
    @DisplayName("global @lookup rewrites lookup(Name, x) to pwlx(x, ...)")
    void testGlobalLookupFunctionRewrite() {
        String text =
                "@lookup BRMM\n" +
                "| x | y |\n" +
                "| 0 | 1.2 |\n" +
                "| 1 | 1.0 |\n" +
                "| 5 | 0.78 |\n" +
                "@end\n" +
                "@equations World2\n" +
                "  BRMM ~ lookup(BRMM, QL_R)\n" +
                "@end\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);

        SFCRParseResult.BlockDump block = result.findBlock("equations", "World2");
        assertNotNull(block);
        assertTrue(block.dumpString.contains("pwlx(QL_R,0.0,1.2,1.0,1.0,5.0,0.78)"));
    }

    @Test
    @DisplayName("local scoped @lookup overrides global lookup when using Name(x) syntax")
    void testScopedLookupOverridesGlobalLookup() {
        String text =
                "@lookup BRFM\n" +
                "  0, 1\n" +
                "  1, 2\n" +
                "@end\n" +
                "@lookup BRFM scope=World2\n" +
                "  0, 10\n" +
                "  1, 20\n" +
                "@end\n" +
                "@equations World2\n" +
                "  BRFM ~ BRFM(FR)\n" +
                "@end\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);

        SFCRParseResult.BlockDump block = result.findBlock("equations", "World2");
        assertNotNull(block);
        assertTrue(block.dumpString.contains("pwlx(FR,0.0,10.0,1.0,20.0)"));
        assertFalse(block.dumpString.contains("pwlx(FR,0.0,1.0,1.0,2.0)"));
    }

    @Test
    @DisplayName("strict mode: malformed @lookup row throws ParseException")
    void testStrictModeFailsOnMalformedLookupRow() {
        String text =
                "@lookup Bad\n" +
                "  this is not numeric\n" +
                "@end\n";

        assertThrows(SFCRParser.ParseException.class, () -> SFCRParser.parseToResult(text, true));
    }

    @Test
    @DisplayName("strict mode: valid @lookup with markdown header rows parses")
    void testStrictModeAcceptsLookupMarkdownHeaderRows() {
        String text =
                "@lookup BRPM\n" +
                "| x | y |\n" +
                "| 0 | 1.02 |\n" +
                "| 10 | 0.90 |\n" +
                "@end\n" +
                "@equations World2\n" +
                "  BRPM ~ lookup(BRPM, POLR)\n" +
                "@end\n";

        SFCRParseResult result = SFCRParser.parseToResult(text, true);
        assertNotNull(result);
        assertNotNull(result.findBlock("lookup", "BRPM"));
    }
}
