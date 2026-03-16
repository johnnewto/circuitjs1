package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SFCRParser")
@DisplayName("World2 SFCR markdown — end-to-end export/load")
class SFCRWorld2EndToEndTest {

    @Test
    @DisplayName("world2_forrester.md parses, exports, and re-parses with lookup/equation fidelity")
    void testWorld2MarkdownEndToEndExportAndLoad() throws Exception {
        Path world2Path = Path.of("src/com/lushprojects/circuitjs1/public/circuits/economics/world2_forrester.md");
        assertTrue(Files.exists(world2Path), "world2_forrester.md must exist");

        String originalText = Files.readString(world2Path, StandardCharsets.UTF_8);
        assertNotNull(originalText);
        assertFalse(originalText.trim().isEmpty(), "world2_forrester.md must not be empty");

        SFCRParseResult first = SFCRParser.parseToResult(originalText);
        assertNotNull(first, "First parse result must not be null");
        assertNotNull(first.findBlock("equations", "World2"), "World2 equations block must parse");
        assertTrue(first.getBlocksByType("lookup").size() >= 20,
                "World2 markdown should include many lookup tables");

        String exported = SFCRParseResultExporter.export(first);
        assertNotNull(exported, "Exported text must not be null");
        assertFalse(exported.trim().isEmpty(), "Exported text must not be empty");

        SFCRParseResult second = SFCRParser.parseToResult(exported);
        assertNotNull(second, "Second parse result must not be null");
        assertNotNull(second.findBlock("equations", "World2"), "World2 equations block must survive round-trip");
        assertLookupPresent(second, "World2", "BRMM");
        assertLookupPresent(second, "World2", "DRMM");
        assertLookupPresent(second, "World2", "POLAT");
        assertLookupPresent(second, "World2", "NRMM");
        assertLookupPresent(second, "World2", "QLM");
        assertLookupPresent(second, "World2", "QLP");

        List<String> firstSignature = toBlockSignature(first.blockDumps);
        List<String> secondSignature = toBlockSignature(second.blockDumps);
        assertEquals(firstSignature, secondSignature,
                "Block dump signature (type/name/order/dump) must round-trip for world2 markdown");

        assertEquals(first.hints, second.hints, "Hints must round-trip for world2 markdown");
        assertEquals(first.initSettings, second.initSettings, "@init settings must round-trip for world2 markdown");
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

    private void assertLookupPresent(SFCRParseResult result, String scopeName, String lookupName) {
        boolean foundUnscoped = result.findBlock("lookup", lookupName) != null;
        boolean foundScoped = result.findBlock("lookup", scopeName + ":" + lookupName) != null;
        assertTrue(foundUnscoped || foundScoped,
                lookupName + " lookup must survive round-trip");
    }
}
