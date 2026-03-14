package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SFCRParser")
class SFCRExportParseRoundTripTest {

    @Test
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
