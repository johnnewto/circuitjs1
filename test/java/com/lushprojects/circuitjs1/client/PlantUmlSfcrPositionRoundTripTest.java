package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramElm;
import com.lushprojects.circuitjs1.client.io.SFCRExporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("SFCRParser")
@DisplayName("PlantUML SFCR position round-trip")
class PlantUmlSfcrPositionRoundTripTest extends CircuitJavaSimTestBase {

    @Test
    @DisplayName("moved fenced PlantUML diagram exports updated x/y on @startuml and reimports at same position")
    void movedPlantUmlDiagramKeepsPositionAfterSfcrExportAndReimport() throws Exception {
        String source =
            "```{r}\n" +
                "@startuml x=200 y=-80 scale=1.5\n" +
                "actor Households\n" +
                "participant Firms\n" +
                "Households -> Firms : Demand\n" +
                "@end\n" +
                "```\n";

        loadCircuitText(source);
        SequenceDiagramElm original = findFirstSequenceDiagram(sim);
        assertNotNull(original, "Expected PlantUML diagram after SFCR import");
        assertEquals(1.5, original.getDiagramScale(), 1e-9,
            "Expected PlantUML scale from fenced header");

        int movedX1 = original.x + 96;
        int movedY1 = original.y + 64;
        int movedX2 = original.x2 + 96;
        int movedY2 = original.y2 + 64;
        int movedFrameWidth = Math.abs(movedX2 - movedX1);
        int movedFrameHeight = Math.abs(movedY2 - movedY1);
        original.setElementPosition(movedX1, movedY1, movedX2, movedY2);

        String exported = new SFCRExporter(sim, SFCRExporter.ExportSyntax.R_STYLE).export();
        assertTrue(exported.contains("```{r}\n@startuml x=" + movedX1 + " y=" + movedY1),
            "Export should update @startuml metadata with moved x/y position");
        assertTrue(exported.contains("w=" + movedFrameWidth),
            "Export should preserve PlantUML frame width metadata on @startuml");
        assertTrue(exported.contains("h=" + movedFrameHeight),
            "Export should preserve PlantUML frame height metadata on @startuml");
        assertTrue(!exported.contains(" width=560"),
            "Export should omit default PlantUML content width metadata");
        assertTrue(exported.contains("scale=1.5"),
            "Export should preserve PlantUML scale metadata on @startuml");

        CirSim reloaded = new CirSim();
        reloaded.getBootstrap().initRunner();
        reloaded.getCircuitIOService().readCircuit(exported, 0);
        reloaded.analyzeCircuit();

        SequenceDiagramElm reloadedDiagram = findFirstSequenceDiagram(reloaded);
        assertNotNull(reloadedDiagram, "Expected PlantUML diagram after SFCR reimport");
        assertEquals(movedX1, reloadedDiagram.x, "Reloaded PlantUML x should match moved x");
        assertEquals(movedY1, reloadedDiagram.y, "Reloaded PlantUML y should match moved y");
        assertEquals(movedX2, reloadedDiagram.x2, "Reloaded PlantUML x2 should match moved frame width");
        assertEquals(movedY2, reloadedDiagram.y2, "Reloaded PlantUML y2 should match moved frame height");
        assertEquals(1.5, reloadedDiagram.getDiagramScale(), 1e-9,
            "Reloaded PlantUML scale should match exported scale");
    }

    @Test
    @DisplayName("inline @startuml block inside r fence imports as a sequence diagram")
    void inlineStartUmlInsideRFenceCreatesSequenceDiagram() throws Exception {
        String source =
            "```{r}\n" +
                "@startuml x=-384 y=56 width=560\n" +
                "source: Transaction Flow Matrix\n" +
                "@end\n" +
                "```\n";

        loadCircuitText(source);
        SequenceDiagramElm original = findFirstSequenceDiagram(sim);
        assertNotNull(original, "Expected PlantUML diagram after inline @startuml import");
        assertEquals(-384, original.x, "Inline @startuml x should be imported");
        assertEquals(56, original.y, "Inline @startuml y should be imported");
        assertEquals("Transaction Flow Matrix", original.getSourceTableName(),
            "Inline @startuml source binding should be preserved");
    }

    @Test
    @DisplayName("exported PlantUML does not have duplicate closing backticks")
    void exportedPlantUmlHasNoDuplicateClosingBackticks() throws Exception {
        String source =
            "```{r}\n" +
                "@startuml x=100 y=50\n" +
                "actor User\n" +
                "participant System\n" +
                "User -> System : Request\n" +
                "@end\n" +
                "```\n";

        loadCircuitText(source);
        SequenceDiagramElm original = findFirstSequenceDiagram(sim);
        assertNotNull(original, "Expected PlantUML diagram after import");

        // Debug: print stored PlantUML source
        String storedSource = original.getPlantUmlSource();
        System.out.println("=== Stored PlantUML source ===");
        System.out.println(storedSource.replace("\n", "\\n\n"));
        System.out.println("=== Contains ```: " + storedSource.contains("```"));

        String exported = new SFCRExporter(sim, SFCRExporter.ExportSyntax.R_STYLE).export();

        // Debug: print exported content with explicit markers
        System.out.println("\n=== Exported SFCR (length=" + exported.length() + ") ===");
        System.out.println("---START---");
        System.out.println(exported);
        System.out.println("---END---");
        
        // Debug: count backticks
        int backtickCount = 0;
        int idx = 0;
        while ((idx = exported.indexOf("```", idx)) != -1) {
            backtickCount++;
            System.out.println("Found ``` at index " + idx + ": context=[" + 
                exported.substring(Math.max(0, idx-10), Math.min(exported.length(), idx+13)) + "]");
            idx += 3;
        }

        // Verify no duplicate closing backticks pattern like "```\n```" or "@end\n```\n\n\n```"
        assertFalse(exported.contains("```\n```"),
            "Export should not have consecutive closing backticks");
        assertFalse(exported.contains("```\n\n```"),
            "Export should not have closing backticks separated by blank line");

        // Count opening and closing fence markers - should be equal
        int openCount = countOccurrences(exported, "```{r}");
        int closeCount = countOccurrences(exported, "\n```\n");
        assertEquals(openCount, closeCount,
            "Number of opening and closing fence markers should match");

        // Verify round-trip: reimport and check diagram still exists
        CirSim reloaded = new CirSim();
        reloaded.getBootstrap().initRunner();
        reloaded.getCircuitIOService().readCircuit(exported, 0);
        reloaded.analyzeCircuit();

        SequenceDiagramElm reloadedDiagram = findFirstSequenceDiagram(reloaded);
        assertNotNull(reloadedDiagram, "PlantUML diagram should survive round-trip export/import");
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }

    private static SequenceDiagramElm findFirstSequenceDiagram(CirSim s) {
        for (int i = 0; i < s.elmList.size(); i++) {
            CircuitElm elm = s.elmList.get(i);
            if (elm instanceof SequenceDiagramElm) {
                return (SequenceDiagramElm) elm;
            }
        }
        return null;
    }
}
