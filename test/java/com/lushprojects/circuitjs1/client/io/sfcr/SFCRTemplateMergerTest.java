package com.lushprojects.circuitjs1.client.io.sfcr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("SFCR template merger PlantUML handling")
class SFCRTemplateMergerTest {

    @Test
    @DisplayName("collectAtDirectiveBlocks extracts @startuml...@end blocks")
    void collectAtDirectiveBlocksExtractsStartumlBlocks() {
        String text =
                "@startuml x=-320 y=-136 w=440 h=443 scale=0.7785714285714286\n" +
                "   source: Transaction Flow Matrix\n" +
                "@end\n";

        ArrayList<String> blocks = SFCRTemplateMerger.collectAtDirectiveBlocks(text, "@startuml");

        assertEquals(1, blocks.size(), "Expected one PlantUML payload block");
        assertEquals(
                "@startuml x=-320 y=-136 w=440 h=443 scale=0.7785714285714286\n" +
                "   source: Transaction Flow Matrix\n" +
                "@end",
                blocks.get(0),
                "Collected PlantUML payload should contain the full @startuml block");
    }

    @Test
    @DisplayName("detectTemplateBlockType classifies @action as a structural block")
    void detectTemplateBlockTypeClassifiesActionBlocks() {
        SFCRTemplateMerger.TemplateBlockType type = SFCRTemplateMerger.detectTemplateBlockType(
                "@action Action_Schedule x=1072 y=896\n" +
                "pauseTime: 0\n" +
                "@end\n");

        assertSame(SFCRTemplateMerger.TemplateBlockType.ACTION, type);
    }
}
