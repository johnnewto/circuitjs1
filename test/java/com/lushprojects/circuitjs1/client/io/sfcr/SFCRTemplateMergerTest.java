package com.lushprojects.circuitjs1.client.io.sfcr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("SFCR template merger PlantUML handling")
class SFCRTemplateMergerTest {

    @Test
    @DisplayName("collectPlantUmlBlocks strips fenced closing backticks from exported payload")
    void collectPlantUmlBlocksStripsTrailingFenceCloser() {
        String text =
                "```{PlantUML}\n" +
                "@startuml x=-320 y=-136 w=440 h=443 scale=0.7785714285714286\n" +
                "   source: Transaction Flow Matrix\n" +
                "@enduml\n" +
                "```\n";

        ArrayList<String> blocks = SFCRTemplateMerger.collectPlantUmlBlocks(text);

        assertEquals(1, blocks.size(), "Expected one PlantUML payload block");
        assertEquals(
                "@startuml x=-320 y=-136 w=440 h=443 scale=0.7785714285714286\n" +
                "   source: Transaction Flow Matrix\n" +
                "@enduml",
                blocks.get(0),
                "Collected PlantUML payload should exclude the markdown fence closer");
        assertFalse(blocks.get(0).contains("```"),
                "Collected PlantUML payload must not retain stray markdown fence markers");
    }
}