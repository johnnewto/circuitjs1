package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.io.SFCRParseResult;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.sfcr.ParseWarning;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockExportHandlerRegistry;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockParseHandlerRegistry;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.EquationBlocksEmitExportHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.LookupBlockExportHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.SFCRBlockExportHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SFCRParser")
@DisplayName("SFCR handler dispatch and ordering")
class SFCRHandlerDispatchTest {

    @Test
    @DisplayName("parse handler registry contains core directives")
    void parseRegistryContainsCoreDirectives() {
        assertNotNull(SFCRBlockParseHandlerRegistry.getHandler("@init"));
        assertNotNull(SFCRBlockParseHandlerRegistry.getHandler("@action"));
        assertNotNull(SFCRBlockParseHandlerRegistry.getHandler("@equations"));
        assertNotNull(SFCRBlockParseHandlerRegistry.getHandler("@parameters"));
        assertNotNull(SFCRBlockParseHandlerRegistry.getHandler("@lookup"));
        assertNotNull(SFCRBlockParseHandlerRegistry.getHandler("@matrix"));
        assertNotNull(SFCRBlockParseHandlerRegistry.getHandler("@hints"));
        assertNotNull(SFCRBlockParseHandlerRegistry.getHandler("@scope"));
        assertNotNull(SFCRBlockParseHandlerRegistry.getHandler("@circuit"));
        assertNotNull(SFCRBlockParseHandlerRegistry.getHandler("@sankey"));
        assertNotNull(SFCRBlockParseHandlerRegistry.getHandler("@info"));
    }

    @Test
    @DisplayName("unknown directive emits warning and parser continues")
    void unknownDirectiveEmitsWarningAndContinues() throws Exception {
        String text =
                "@init\n" +
                "  timestep: 1\n" +
                "@end\n" +
                "@unknown_block Foo\n" +
                "  ignored: true\n" +
                "@end\n" +
                "@init\n" +
                "  timeUnit: yr\n" +
                "@end\n";

        SFCRParser parser = new SFCRParser(null);
        Field pendingResultField = SFCRParser.class.getDeclaredField("pendingResult");
        pendingResultField.setAccessible(true);
        pendingResultField.set(parser, new SFCRParseResult());
        assertTrue(parser.parse(text), "Parser should continue even with unknown directive block");

        ArrayList<ParseWarning> warnings = parser.getParseWarnings();
        assertEquals(1, warnings.size(), "Expected exactly one unknown-directive warning");
        assertTrue(warnings.get(0).getMessage().contains("Unknown SFCR directive"),
                "Warning message should identify unknown directive");
    }

    @Test
    @DisplayName("unknown directive block does not block later equations parse")
    void unknownDirectiveDoesNotBlockFollowingEquations() {
        String text =
                "@unknown_block Foo\n" +
                "  x: 1\n" +
                "@end\n" +
                "@equations T\n" +
                "  A ~ 1\n" +
                "@end\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);
        assertNotNull(result.findBlock("equations", "T"),
                "Equation block after unknown directive must still be parsed");
    }

    @Test
    @DisplayName("export handler ordering is explicit and stable")
    void exportHandlerOrderingIsExplicit() {
        List<SFCRBlockExportHandler> ordered = SFCRBlockExportHandlerRegistry.getOrderedHandlers();
        assertNotNull(ordered);
        assertFalse(ordered.isEmpty());

        int equationEmitIndex = -1;
        int lookupIndex = -1;
        for (int i = 0; i < ordered.size(); i++) {
            SFCRBlockExportHandler handler = ordered.get(i);
            if (handler instanceof EquationBlocksEmitExportHandler) {
                equationEmitIndex = i;
            } else if (handler instanceof LookupBlockExportHandler) {
                lookupIndex = i;
            }
        }

        assertTrue(equationEmitIndex >= 0, "Equation emit handler must be registered");
        assertTrue(lookupIndex >= 0, "Lookup export handler must be registered");
        assertTrue(equationEmitIndex < lookupIndex,
                "Current export policy should emit equations before lookup blocks");
    }
}
