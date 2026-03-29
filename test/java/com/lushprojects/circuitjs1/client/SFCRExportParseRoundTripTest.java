package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.io.SFCRParseResult;
import com.lushprojects.circuitjs1.client.io.SFCRParseResultExporter;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SFCRParser")
@DisplayName("SFCRParser ↔ SFCRParseResultExporter — round-trip fidelity")
class SFCRExportParseRoundTripTest {

    @Test
    @DisplayName("parse → export → parse preserves block structure, init settings, and hints")
    void testRoundTripParseExportParsePreservesStructure() throws Exception {
        String originalText = TestFixtures.loadSfcr("parse_result_fixture.md");
        SFCRParseResult first = SFCRParser.parseToResult(originalText);
        assertNotNull(first, "First parse result must not be null");

        String exported = SFCRParseResultExporter.export(first);
        assertNotNull(exported, "Exported text must not be null");
        assertFalse(exported.trim().isEmpty(), "Exported text must not be empty");

        SFCRParseResult second = SFCRParser.parseToResult(exported);
        assertNotNull(second, "Second parse result must not be null");

        assertEquals(first.initSettings, second.initSettings, "@init settings must round-trip");
        assertEquals(first.hints, second.hints, "Hints must round-trip");

        List<String> firstSignature = toBlockSignature(first.blockDumps);
        List<String> secondSignature = toBlockSignature(second.blockDumps);
        assertEquals(firstSignature, secondSignature,
                "Block dump signature (type/name/order/dump) must round-trip");
    }

    @Test
    @DisplayName("lookup blocks survive parse-result round-trip with scoped and global tables")
    void testLookupBlocksRoundTrip() {
        String text =
                "@lookup BRMM\n" +
                "  0, 1.2\n" +
                "  1, 1.0\n" +
                "  5, 0.78\n" +
                "@end\n" +
                "@lookup BRFM scope=World2\n" +
                "  0, 0\n" +
                "  1, 1\n" +
                "  2, 1.9\n" +
                "@end\n" +
                "@equations World2\n" +
                "  BRMM ~ lookup(BRMM, QL_R)\n" +
                "  BRFM ~ BRFM(FR)\n" +
                "@end\n";

        SFCRParseResult first = SFCRParser.parseToResult(text);
        assertNotNull(first);

        String exported = SFCRParseResultExporter.export(first);
        SFCRParseResult second = SFCRParser.parseToResult(exported);
        assertNotNull(second);

        assertNotNull(second.findBlock("lookup", "BRMM"));
        assertNotNull(second.findBlock("lookup", "World2:BRFM"));
        assertNotNull(second.findBlock("equations", "World2"));
    }

    @Test
    @DisplayName("PlantUML blocks survive parse-result round-trip with explicit @startuml export")
    void testPlantUmlBlocksRoundTrip() {
        String text =
                "@equations Demo\n" +
                "  Y ~ 1\n" +
                "@end\n\n" +
                "@plantuml x=200 y=120\n" +
                "width: 620\n" +
            "scale: 1.5\n" +
                "@startuml\n" +
                "source: Transaction Flow Matrix\n" +
                "actor Households\n" +
                "participant Firms\n" +
                "Households -> Firms : Demand\n" +
                "@enduml\n" +
                "@end\n";

        SFCRParseResult first = SFCRParser.parseToResult(text);
        assertNotNull(first);
        assertNotNull(first.findBlock("plantuml", ""));

        String exported = SFCRParseResultExporter.export(first);
        assertTrue(exported.contains("```{PlantUML}\n@startuml x=200 y=120 w=16 h=16 width=620"),
                "Parse-result export should emit explicit @startuml blocks");
        assertTrue(exported.contains("scale=1.5"),
            "Parse-result export should preserve PlantUML scale metadata");
        assertTrue(exported.contains("source: Transaction Flow Matrix"),
            "Parse-result export should preserve PlantUML source bindings");
        assertTrue(!exported.contains("@plantuml"),
            "Parse-result export should not emit legacy @plantuml wrappers");

        SFCRParseResult second = SFCRParser.parseToResult(exported);
        assertNotNull(second);

        List<String> firstSignature = toBlockSignature(first.blockDumps);
        List<String> secondSignature = toBlockSignature(second.blockDumps);
        assertEquals(firstSignature, secondSignature,
                "PlantUML block dump signature must round-trip unchanged");
    }

    private List<String> toBlockSignature(List<SFCRParseResult.BlockDump> blocks) {
        ArrayList<String> signature = new ArrayList<String>();
        if (blocks == null) {
            return signature;
        }

        for (SFCRParseResult.BlockDump block : blocks) {
            String type = (block.blockType == null) ? "" : block.blockType;
            String name = (block.blockName == null) ? "" : block.blockName;
            String dump = (block.dumpString == null) ? "" : block.dumpString.trim();
            signature.add(type + "|" + name + "|" + dump);
        }
        return signature;
    }
}
