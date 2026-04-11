package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.io.SFCRParseResult;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.SFCRSyntaxNormalizer;
import com.lushprojects.circuitjs1.client.io.SFCRUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SFCRParser")
@DisplayName("SFCRSyntaxNormalizer — R-style to block format conversion")
class SFCRSyntaxNormalizerTest {

    @Test
    @DisplayName("normalizes sfcr_set to @equations block")
    void testNormalizeSfcrSet() {
        String rStyle = "eqs <- sfcr_set(\n" +
                "  Y ~ C + I,\n" +
                "  C ~ alpha * YD\n" +
                ")";
        
        SFCRSyntaxNormalizer normalizer = new SFCRSyntaxNormalizer();
        String normalized = normalizer.normalize(rStyle);
        
        assertTrue(normalized.contains("@equations"), "Should contain @equations block");
        assertTrue(normalized.contains("eqs"), "Should preserve block name");
        assertTrue(normalized.contains("@end"), "Should contain @end");
        assertFalse(normalized.contains("sfcr_set"), "Should not contain sfcr_set after normalization");
    }

    @Test
    @DisplayName("normalizes equals-assigned sfcr_set to @equations block")
    void testNormalizeEqualsAssignedSfcrSet() {
        String rStyle = "growth_parameters = sfcr_set(\n" +
                "  alpha1 ~ 0.75,\n" +
                "  alpha2 ~ 0.064\n" +
                ")";

        SFCRSyntaxNormalizer normalizer = new SFCRSyntaxNormalizer();
        String normalized = normalizer.normalize(rStyle);

        assertTrue(normalized.contains("@equations growth_parameters"),
                "Equals-assigned sfcr_set should normalize to an equations block with the assignment name");
        assertTrue(normalized.contains("0.75"), "Should preserve parameter values");
        assertTrue(normalized.contains("0.064"), "Should preserve parameter values");
    }

    @Test
    @DisplayName("normalizes sfcr_matrix to @matrix block")
    void testNormalizeSfcrMatrix() {
        String rStyle = "# [ x=100 y=200 type: transaction_flow ]\n" +
                "tfm <- sfcr_matrix(\n" +
                "  columns = c(\"Households\", \"Firms\"),\n" +
                "  codes = c(\"h\", \"f\"),\n" +
                "  c(\"Consumption\", h = \"-C\", f = \"C\")\n" +
                ")";
        
        SFCRSyntaxNormalizer normalizer = new SFCRSyntaxNormalizer();
        String normalized = normalizer.normalize(rStyle);
        
        assertTrue(normalized.contains("@matrix"), "Should contain @matrix block");
        assertTrue(normalized.contains("tfm"), "Should preserve block name");
        assertTrue(normalized.contains("Households"), "Should preserve column names");
        assertTrue(normalized.contains("Consumption"), "Should preserve row names");
        assertTrue(normalized.contains("@end"), "Should contain @end");
        assertFalse(normalized.contains("sfcr_matrix"), "Should not contain sfcr_matrix after normalization");
    }

    @Test
    @DisplayName("normalizes sfcr_matrix type vector to block columnTypes")
    void testNormalizeSfcrMatrixColumnTypes() {
        String rStyle = "tfm <- sfcr_matrix(\n" +
                "  columns = c(\"Households\", \"Firms\", \"Banks\"),\n" +
                "  codes = c(\"h\", \"f\", \"b\"),\n" +
                "  type = c(\"Asset\", \"Liability\", \"Equity\"),\n" +
                "  c(\"Loans\", h = \"1\", f = \"-1\", b = \"0\")\n" +
                ")";

        SFCRSyntaxNormalizer normalizer = new SFCRSyntaxNormalizer();
        String normalized = normalizer.normalize(rStyle);

        assertTrue(normalized.contains("columnTypes: Asset, Liability, Equity"),
                "R-style type vector should normalize to block columnTypes metadata");
    }

    @Test
    @DisplayName("preserves metadata comment position info")
    void testPreservesMetadataPosition() {
        String rStyle = "# [ x=400 y=100 ]\n" +
                "Model <- sfcr_set(\n" +
                "  Y ~ 100\n" +
                ")";
        
        SFCRSyntaxNormalizer normalizer = new SFCRSyntaxNormalizer();
        String normalized = normalizer.normalize(rStyle);
        
        assertTrue(normalized.contains("x=400"), "Should preserve x position");
        assertTrue(normalized.contains("y=100"), "Should preserve y position");
    }

    @Test
    @DisplayName("passes through block-style content unchanged")
    void testPassThroughBlockStyle() {
        String blockStyle = "@equations Model\n" +
                "  Y ~ 100\n" +
                "@end\n";
        
        SFCRSyntaxNormalizer normalizer = new SFCRSyntaxNormalizer();
        String normalized = normalizer.normalize(blockStyle);
        
        assertEquals(blockStyle, normalized, "Block-style should pass through unchanged");
    }

    @Test
    @DisplayName("preserves d(name) alias instead of expanding or converting to diff()")
    void testNormalizeDifferenceAlias() {
        assertEquals("d(INV)", SFCRUtil.normalizeExpression("d(INV)"));
        assertEquals("x + d(INV)", SFCRUtil.normalizeExpression("x + d(INV)"));
        assertEquals("diff(t)", SFCRUtil.normalizeExpression("diff(t)"));
    }

    @Test
    @DisplayName("handles mixed block and R-style content")
    void testMixedContent() {
        String mixed = "@init\n" +
                "  timestep: 1\n" +
                "@end\n" +
                "\n" +
                "Model <- sfcr_set(\n" +
                "  Y ~ 100\n" +
                ")\n";
        
        SFCRSyntaxNormalizer normalizer = new SFCRSyntaxNormalizer();
        String normalized = normalizer.normalize(mixed);
        
        assertTrue(normalized.contains("@init"), "Should preserve @init block");
        assertTrue(normalized.contains("@equations"), "Should convert sfcr_set to @equations");
        assertFalse(normalized.contains("sfcr_set"), "Should remove sfcr_set");
    }

    @Test
    @DisplayName("R-style normalized content parses correctly")
    void testNormalizedContentParses() {
        String rStyle = "Model <- sfcr_set(\n" +
                "  Y ~ C + I,\n" +
                "  C ~ 0.8 * Y\n" +
                ")";
        
        SFCRParseResult result = SFCRParser.parseToResult(rStyle);
        
        assertNotNull(result, "Should parse successfully");
        SFCRParseResult.BlockDump eqBlock = result.findBlock("equations", "Model");
        assertNotNull(eqBlock, "Should create equations block named 'Model'");
    }

    @Test
    @DisplayName("equals-assigned R-style content parses correctly")
    void testEqualsAssignedNormalizedContentParses() {
        String rStyle = "growth_parameters = sfcr_set(\n" +
                "  alpha1 ~ 0.75,\n" +
                "  beta ~ 0.5\n" +
                ")";

        SFCRParseResult result = SFCRParser.parseToResult(rStyle);

        assertNotNull(result, "Equals-assigned R-style content should parse successfully");
        SFCRParseResult.BlockDump eqBlock = result.findBlock("equations", "growth_parameters");
        assertNotNull(eqBlock, "Should create equations block named 'growth_parameters'");
    }

    @Test
    @DisplayName("world2 fixture with sfcr_set parses correctly after normalization")
    void testWorld2FixtureParsesCorrectly() throws Exception {
        String world2 = TestFixtures.loadSfcr("world2_fixture.md");
        
        SFCRParseResult result = SFCRParser.parseToResult(world2);
        
        assertNotNull(result, "World2 fixture should parse");
        SFCRParseResult.BlockDump eqBlock = result.findBlock("equations", "World2");
        assertNotNull(eqBlock, "Should find World2 equations block");
    }

    @Test
    @DisplayName("preserves inline row metadata through normalization")
    void testPreservesInlineMetadata() {
        String rStyle = "Model <- sfcr_set(\n" +
                "  Y ~ 100,  # [mode=param, slider=alpha]\n" +
                "  C ~ 50\n" +
                ")";
        
        SFCRSyntaxNormalizer normalizer = new SFCRSyntaxNormalizer();
        String normalized = normalizer.normalize(rStyle);
        
        // The normalizer should preserve mode info in the normalized output
        assertTrue(normalized.contains("mode=param") || normalized.contains("; mode=param"),
                "Should preserve mode metadata");
    }

    @Test
    @DisplayName("containsRStyleContent correctly identifies R-style")
    void testContainsRStyleContent() {
        assertTrue(SFCRSyntaxNormalizer.containsRStyleContent("x <- sfcr_set(Y ~ 1)"));
        assertTrue(SFCRSyntaxNormalizer.containsRStyleContent("tfm <- sfcr_matrix(...)"));
        assertFalse(SFCRSyntaxNormalizer.containsRStyleContent("@equations Model"));
        assertFalse(SFCRSyntaxNormalizer.containsRStyleContent(null));
    }

    @Test
    @DisplayName("handles multiline R-style blocks correctly")
    void testMultilineRStyleBlocks() {
        String rStyle = "Model <- sfcr_set(\n" +
                "  # Parameters section\n" +
                "  alpha ~ 0.8,\n" +
                "  beta ~ 0.2,\n" +
                "  \n" +
                "  # Equations\n" +
                "  Y ~ C + I,\n" +
                "  C ~ alpha * Y\n" +
                ")";
        
        SFCRSyntaxNormalizer normalizer = new SFCRSyntaxNormalizer();
        String normalized = normalizer.normalize(rStyle);
        
        assertTrue(normalized.contains("@equations"), "Should contain @equations");
        assertTrue(normalized.contains("alpha"), "Should contain alpha");
        assertTrue(normalized.contains("beta"), "Should contain beta");
        assertTrue(normalized.contains("@end"), "Should contain @end");
    }

        @Test
        @DisplayName("ignores parentheses inside R-style comments when normalizing")
        void ignoresParenthesesInsideCommentsWhenNormalizing() {
        String rStyle = "growth_eqs <- sfcr_set(\n" +
            "  A ~ 1,\n" +
            "  # --- Section (unfinished\n" +
            "  B ~ A + 1\n" +
            ")\n" +
            "\n" +
            "@zorder\n" +
            "  uid:block z:0\n" +
            "@end\n";

        String normalized = new SFCRSyntaxNormalizer().normalize(rStyle);

        assertTrue(normalized.contains("@equations growth_eqs"),
            "R-style block should still normalize even if a comment contains an unmatched parenthesis");
        assertTrue(normalized.contains("B ~ A + 1"),
            "Rows after the commented parenthesis should remain inside the normalized equations block");
        }

        @Test
        @DisplayName("parseToResult handles unmatched parentheses inside R-style comments")
        void parseToResultHandlesUnmatchedParenthesesInsideComments() {
        String rStyle = "growth_eqs <- sfcr_set(\n" +
            "  A ~ 1,\n" +
            "  # --- Section (unfinished\n" +
            "  B ~ A + 1\n" +
            ")\n";

        SFCRParseResult result = SFCRParser.parseToResult(rStyle);

        assertNotNull(result, "Parser should accept unmatched parentheses inside comments");
        SFCRParseResult.BlockDump eqBlock = result.findBlock("equations", "growth_eqs");
        assertNotNull(eqBlock, "Equations block should still be produced");
        }

    @Test
    @DisplayName("normalizes R-style if expressions to ternary switch equations")
    void normalizesIfExpressionsToTernarySwitchEquations() {
        String rStyle =
            "Switches <- sfcr_set(\n" +
            "  z2a ~ if (BLR[-1] > (top + .05)) {1} else {0},\n" +
            "  z1b ~ if (BLR[-1] <= (bot -.05)) {1} else {0}\n" +
            ")\n";

        String normalized = new SFCRSyntaxNormalizer().normalize(rStyle);

        assertTrue(normalized.contains("z2a ~ (last(BLR) > (top + 0.05)) ? 1 : 0 ; mode=voltage ; initial=0  # Switch"),
            "if/else switch rows should normalize to ternary form with last() and initial=0");
        assertTrue(normalized.contains("z1b ~ (last(BLR) <= (bot - 0.05)) ? 1 : 0 ; mode=voltage ; initial=0  # Switch"),
            "Negative leading decimals should normalize to explicit 0-prefixed literals");
    }

        @Test
        @DisplayName("merges duplicate simple assignments into initial metadata")
        void mergesDuplicateSimpleAssignmentsIntoInitialMetadata() {
        String rStyle =
            "growth_eqs <- sfcr_set(\n" +
            "  ADDl ~ 1 + rate,\n" +
            "  Ske ~ beta*Sk\n" +
            ")\n\n" +
            "growth_initial <- sfcr_set(\n" +
            "  ADDl ~ 0.04592,\n" +
            "  Ske ~ 22222\n" +
            ")\n";

        String normalized = new SFCRSyntaxNormalizer().normalize(rStyle);

        assertTrue(normalized.contains("ADDl ~ 1 + rate ; mode=voltage ; initial=0.04592"),
            "Duplicate ADDl assignment should become initial metadata on the first row");
        assertTrue(normalized.contains("Ske ~ beta*Sk ; mode=voltage ; initial=22222"),
            "Duplicate Ske assignment should become initial metadata on the first row");
        assertFalse(normalized.contains("@equations growth_initial"),
            "A block reduced to duplicate initial assignments should be removed entirely");
        }

        @Test
        @DisplayName("duplicate non-simple assignment is kept as comment with appended exception")
        void duplicateNonSimpleAssignmentBecomesCommentWithException() {
        String rStyle =
            "growth_eqs <- sfcr_set(\n" +
            "  INke ~ INk[-1] + gamma*(INkt - INk[-1])\n" +
            ")\n\n" +
            "growth_initial <- sfcr_set(\n" +
            "  INke ~ Ske + 10\n" +
            ")\n";

            String normalized = new SFCRSyntaxNormalizer().normalize(rStyle);

            assertTrue(normalized.contains("@equations growth_initial"),
                "Second block should be preserved");
            assertTrue(normalized.contains("# INke ~ Ske + 10"),
                "Duplicate non-simple assignment should be preserved as a comment");
            assertTrue(normalized.contains("Exception caught: Duplicate variable 'INke'"),
                "Duplicate non-simple assignment comment should append the duplicate-variable message");
        }
}
