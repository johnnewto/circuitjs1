package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.io.SFCRParseResult;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.SFCRSyntaxNormalizer;
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
}
