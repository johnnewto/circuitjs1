package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SFCRParser")
@DisplayName("SFCRParser — @lookup rewrite and validation")
class SFCRLookupParserTest {

    @Test
    @DisplayName("global @lookup keeps lookup(Name, x) expression unchanged")
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
        assertTrue(block.dumpString.contains("lookup(BRMM,"), block.dumpString);
        assertTrue(block.dumpString.contains("QL_R)"), block.dumpString);
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
        assertTrue(block.dumpString.contains("lookup(BRFM,"), block.dumpString);
        assertTrue(block.dumpString.contains("FR)"), block.dumpString);
        assertFalse(block.dumpString.contains("BRFM(FR)"), block.dumpString);
    }

    @Test
    @DisplayName("@init lookupMode does not force parser-time rewrite")
    void testLookupModeOverrideToPwlx() {
        String text =
                "@init\n" +
                "  lookupMode: pwlx\n" +
                "@end\n" +
                "@lookup BRMM\n" +
                "  0, 1.2\n" +
                "  1, 1.0\n" +
                "@end\n" +
                "@equations World2\n" +
                "  BRMM ~ lookup(BRMM, QL_R)\n" +
                "@end\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);

        SFCRParseResult.BlockDump block = result.findBlock("equations", "World2");
        assertNotNull(block);
        assertTrue(block.dumpString.contains("lookup(BRMM,"), block.dumpString);
        assertTrue(block.dumpString.contains("QL_R)"), block.dumpString);
    }

    @Test
    @DisplayName("@init lookupClamp alias does not force parser-time rewrite")
    void testLookupClampAliasOverrideToPwlx() {
        String text =
                "@init\n" +
                "  lookupClamp: false\n" +
                "@end\n" +
                "@lookup BRMM\n" +
                "  0, 1.2\n" +
                "  1, 1.0\n" +
                "@end\n" +
                "@equations World2\n" +
                "  BRMM ~ lookup(BRMM, QL_R)\n" +
                "@end\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);

        SFCRParseResult.BlockDump block = result.findBlock("equations", "World2");
        assertNotNull(block);
        assertTrue(block.dumpString.contains("lookup(BRMM,"), block.dumpString);
        assertTrue(block.dumpString.contains("QL_R)"), block.dumpString);
    }

    @Test
    @DisplayName("lookup mode pre-scan preserves lookup(...) when @init appears after equations")
    void testLookupModePrescanWhenInitAppearsAfterEquations() {
        String text =
                "@lookup BRMM\n" +
                "  0, 1.2\n" +
                "  1, 1.0\n" +
                "@end\n" +
                "@equations World2\n" +
                "  BRMM ~ lookup(BRMM, QL_R)\n" +
                "@end\n" +
                "@init\n" +
                "  lookupMode: pwlx\n" +
                "@end\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);

        SFCRParseResult.BlockDump block = result.findBlock("equations", "World2");
        assertNotNull(block);
        assertTrue(block.dumpString.contains("lookup(BRMM,"), block.dumpString);
        assertTrue(block.dumpString.contains("QL_R)"), block.dumpString);
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
