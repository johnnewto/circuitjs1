package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LookupTablesEditorDialog merge behavior")
class LookupTablesEditorDialogTest {

    @Test
    @DisplayName("merge replaces existing lookup blocks in place")
    void mergeReplacesLookupBlocksInPlace() {
        String source =
            "# Title\n\n" +
            "```{r}\n" +
            "@lookup A scope=World2\n" +
            "  0, 1\n" +
            "  1, 2\n" +
            "@end\n" +
            "```\n\n" +
            "Paragraph between blocks.\n\n" +
            "```{r}\n" +
            "@lookup B\n" +
            "  0, 3\n" +
            "  1, 4\n" +
            "@end\n" +
            "```\n";

        String edited =
            "@lookup A scope=World2\n" +
            "  0, 10\n" +
            "  1, 20\n" +
            "@end\n\n" +
            "@lookup B\n" +
            "  0, 30\n" +
            "  1, 40\n" +
            "@end\n";

        String merged = LookupBlocksTextUtil.mergeLookupBlocks(source, edited);

        assertTrue(merged.contains("```{r}\n@lookup A scope=World2"));
        assertTrue(merged.contains("Paragraph between blocks."));
        assertTrue(merged.contains("```{r}\n@lookup B"));
        assertTrue(merged.contains("0, 10"));
        assertTrue(merged.contains("0, 30"));
    }

    @Test
    @DisplayName("extract returns concatenated lookup blocks only")
    void extractReturnsLookupBlocksOnly() {
        String source =
            "# Model\n\n" +
            "@init\n" +
            "  timestep: 0.2\n" +
            "@end\n\n" +
            "@lookup A\n" +
            "  0, 1\n" +
            "  1, 2\n" +
            "@end\n\n" +
            "@equations T\n" +
            "  X ~ 1\n" +
            "@end\n";

        String extracted = LookupBlocksTextUtil.extractLookupBlocks(source);

        assertTrue(extracted.startsWith("@lookup A"));
        assertTrue(extracted.contains("@end"));
        assertEquals("@lookup A\n  0, 1\n  1, 2\n@end", extracted);
    }
}
