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
        assertNotNull(SFCRBlockParseHandlerRegistry.getHandler("@startuml"));
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

    @Test
    @DisplayName("R-style sfcr_set dispatch parses equations and attaches preceding comments")
    void rStyleSfcrSetDispatchParsesAndAttachesComments() {
        String text =
                "# Heading for R block\n" +
                "RBlock <- sfcr_set(\n" +
                "  Y ~ 1\n" +
                ")\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);
        assertNotNull(result.findBlock("equations", "RBlock"),
                "R-style sfcr_set assignment should parse as equations block");

        List<String> comments = result.blockComments.get("equations|RBlock");
        assertNotNull(comments, "Preceding markdown/comment lines should attach to R-style equations block");
        assertTrue(comments.contains("# Heading for R block"));
    }

    @Test
    @DisplayName("R-style sfcr_matrix metadata comment is consumed and matrix still parses")
    void rStyleSfcrMatrixMetadataIsConsumedAndParses() {
        String text =
                "# Matrix heading\n" +
                "# [ x=320 y=480 type: balance ]\n" +
                "BS <- sfcr_matrix(\n" +
                "  columns = c(\"Households\"),\n" +
                "  codes = c(\"h\"),\n" +
                "  c(\"Assets\", h = \"1\")\n" +
                ")\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);
        assertNotNull(result.findBlock("matrix", "BS"),
                "R-style sfcr_matrix assignment should parse as matrix block");

        List<String> comments = result.blockComments.get("matrix|BS");
        assertNotNull(comments, "Non-metadata heading should still attach to matrix block");
        assertTrue(comments.contains("# Matrix heading"));
        assertFalse(comments.contains("# [ x=320 y=480 type: balance ]"),
                "R-style metadata line should be consumed, not preserved as block comment");
    }

    @Test
    @DisplayName("R-style sfcr_matrix explicit column types are parsed into matrix dump")
    void rStyleSfcrMatrixExplicitColumnTypesAreParsed() {
        String text =
                "BS <- sfcr_matrix(\n" +
                "  columns = c(\"Households\", \"Firms\", \"Banks\"),\n" +
                "  codes = c(\"h\", \"f\", \"b\"),\n" +
                "  type = c(\"Asset\", \"Liability\", \"Equity\"),\n" +
                "  c(\"Balance\", h = \"1\", f = \"-1\", b = \"0\")\n" +
                ")\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);
        SFCRParseResult.BlockDump block = result.findBlock("matrix", "BS");
        assertNotNull(block, "Matrix block should parse");
        assertTrue(block.dumpString.contains(" ASSET LIABILITY EQUITY COMPUTED "),
                "Parsed matrix dump should preserve explicit column types before the computed Σ column");
    }

    @Test
        @DisplayName("R-style sfcr_matrix defaults missing column types to none")
        void rStyleSfcrMatrixDefaultsMissingColumnTypesToNone() {
        String text =
                "BS <- sfcr_matrix(\n" +
                "  columns = c(\"Households\", \"Firms\"),\n" +
                "  codes = c(\"h\", \"f\"),\n" +
                "  c(\"Balance\", h = \"1\", f = \"-1\")\n" +
                ")\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);
        SFCRParseResult.BlockDump block = result.findBlock("matrix", "BS");
        assertNotNull(block, "Matrix block should parse");
        assertTrue(block.dumpString.contains(" NONE NONE COMPUTED "),
                "Missing matrix type vector should default each non-computed column to None");
    }

    @Test
    @DisplayName("R-style sfcr_matrix blank column types are parsed into none types")
    void rStyleSfcrMatrixBlankColumnTypesParseToNone() {
        String text =
                "BS <- sfcr_matrix(\n" +
                "  columns = c(\"Households\", \"Firms\"),\n" +
                "  codes = c(\"h\", \"f\"),\n" +
                "  type = c(\"\", \"\"),\n" +
                "  c(\"Balance\", h = \"1\", f = \"-1\")\n" +
                ")\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result);
        SFCRParseResult.BlockDump block = result.findBlock("matrix", "BS");
        assertNotNull(block, "Matrix block should parse");
        assertTrue(block.dumpString.contains(" NONE NONE COMPUTED "),
                "Blank matrix type strings should parse as None types");
    }

    @Test
    @DisplayName("unknown R-style assignment is skipped without parse failure")
    void unknownRStyleAssignmentSkipsWithoutFailure() {
        String text =
                "Foo <- list(\n" +
                "  a = 1,\n" +
                "  b = 2\n" +
                ")\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result, "Unknown R-style assignment should not crash parse-to-result");
        assertTrue(result.blockDumps.isEmpty(),
                "Unknown R-style assignment should not create parsed SFCR blocks");
    }

    @Test
    @DisplayName("mixed equals and arrow sfcr_set assignments all parse")
    void mixedEqualsAndArrowSfcrSetAssignmentsParse() {
        String text =
                "growth_eqs <- sfcr_set(\n" +
                "  Yk ~ Ske + INke - INk[-1],\n" +
                "  Ske ~ beta*Sk + (1-beta)*Sk[-1]*(1 + (GRpr + RA)),\n" +
                "  INke ~ INk[-1] + gamma*(INkt - INk[-1])\n" +
                ")\n\n" +
                "growth_parameters = sfcr_set(\n" +
                "  alpha1 ~ 0.75,\n" +
                "  alpha2 ~ 0.064,\n" +
                "  beta ~ 0.5,\n" +
                "  betab ~ 0.4,\n" +
                "  gamma ~ 0.15\n" +
                ")\n\n" +
                "growth_initial <- sfcr_set(\n" +
                "  sigmase ~ 0.16667,\n" +
                "  eta ~ 0.04918,\n" +
                "  phi ~ 0.26417,\n" +
                "  phit ~ 0.26417\n" +
                ")\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);

        assertNotNull(result, "Mixed assignment styles should parse successfully");
        assertNotNull(result.findBlock("equations", "growth_eqs"),
                "Arrow-assigned equations block should parse");
        assertNotNull(result.findBlock("equations", "growth_parameters"),
                "Equals-assigned parameters block should parse");
        assertNotNull(result.findBlock("equations", "growth_initial"),
                "Trailing arrow-assigned block should still parse");
    }

    @Test
    @DisplayName("duplicate simple imports merge into first equations block initial values")
    void duplicateSimpleImportsMergeIntoInitialValues() {
        String text =
                "growth_eqs <- sfcr_set(\n" +
                "  ADDl ~ base + premium,\n" +
                "  Ske ~ beta*Sk\n" +
                ")\n\n" +
                "growth_initial <- sfcr_set(\n" +
                "  ADDl ~ 0.04592,\n" +
                "  Ske ~ 22222\n" +
                ")\n";

        SFCRParseResult result = SFCRParser.parseToResult(text);

        assertNotNull(result);
        SFCRParseResult.BlockDump eqBlock = result.findBlock("equations", "growth_eqs");
        assertNotNull(eqBlock, "Primary equations block should still parse");
        assertTrue(eqBlock.dumpString.contains("0.04592"),
                "Merged dump should contain ADDl initial value");
        assertTrue(eqBlock.dumpString.contains("22222"),
                "Merged dump should contain Ske initial value");
        assertNull(result.findBlock("equations", "growth_initial"),
                "Duplicate-only initial block should be removed after merge");
    }
}
