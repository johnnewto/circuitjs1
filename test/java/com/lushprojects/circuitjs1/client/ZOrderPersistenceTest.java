package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm;
import com.lushprojects.circuitjs1.client.io.SFCRExporter;
import com.lushprojects.circuitjs1.client.elements.electronics.DiodeModel;
import com.lushprojects.circuitjs1.client.elements.electronics.TransistorModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Visual z-order persistence")
class ZOrderPersistenceTest extends CircuitJavaSimTestBase {

    @Test
    @DisplayName("native dump round-trip preserves z-order independently of elmList order")
    void nativeDumpRoundTripPreservesZOrder() throws Exception {
        loadCircuitText(twoEquationTables());

        EquationTableElm first = findEquationTable(sim, 0);
        EquationTableElm second = findEquationTable(sim, 1);
        assertNotNull(first, "Expected first equation table");
        assertNotNull(second, "Expected second equation table");

        sim.bringToFront(first);

        String nativeDump = exportNativeDump(sim);

        CirSim reloaded = new CirSim();
        reloaded.getBootstrap().initRunner();
        reloaded.getCircuitIOService().readCircuit(nativeDump, 0);
        reloaded.analyzeCircuit();

        EquationTableElm reloadedFirst = findEquationTable(reloaded, 0);
        EquationTableElm reloadedSecond = findEquationTable(reloaded, 1);
        assertNotNull(reloadedFirst, "Expected first reloaded equation table");
        assertNotNull(reloadedSecond, "Expected second reloaded equation table");
        assertTrue(reloadedFirst.getZOrder() > reloadedSecond.getZOrder(), "First table should still be visually above second after native round-trip");
    }

    @Test
    @DisplayName("legacy native dump without z-order metadata assigns sequential visual order")
    void legacyDumpWithoutZOrderAssignsSequentialVisualOrder() throws Exception {
        loadCircuitText(twoEquationTables());

        String nativeDump = exportNativeDump(sim).replaceAll("\\sZ:-?\\d+", "");

        CirSim reloaded = new CirSim();
        reloaded.getBootstrap().initRunner();
        reloaded.getCircuitIOService().readCircuit(nativeDump, 0);
        reloaded.analyzeCircuit();

        EquationTableElm first = findEquationTable(reloaded, 0);
        EquationTableElm second = findEquationTable(reloaded, 1);
        assertNotNull(first, "Expected first legacy reloaded equation table");
        assertNotNull(second, "Expected second legacy reloaded equation table");
        assertEquals(0, first.getZOrder(), "First legacy element should get the first sequential z-order");
        assertEquals(1, second.getZOrder(), "Second legacy element should get the next sequential z-order");
    }

    @Test
    @DisplayName("SFCR export order remains based on elmList instead of visual z-order")
    void sfcrExportIgnoresVisualZOrder() throws Exception {
        loadCircuitText(twoEquationTables());

        EquationTableElm first = findEquationTable(sim, 0);
        EquationTableElm second = findEquationTable(sim, 1);
        assertNotNull(first, "Expected first equation table");
        assertNotNull(second, "Expected second equation table");

        sim.bringToFront(first);
        sim.sendToBack(second);

        String exported = new SFCRExporter(sim, SFCRExporter.ExportSyntax.BLOCK_FORMAT).export();
        int firstIndex = exported.indexOf("@equations First");
        int secondIndex = exported.indexOf("@equations Second");
        assertTrue(firstIndex >= 0, "Export should contain the first equation block");
        assertTrue(secondIndex >= 0, "Export should contain the second equation block");
        assertTrue(firstIndex < secondIndex, "SFCR export should follow elmList/logical order rather than visual z-order");
    }

    @Test
    @DisplayName("SFCR export and reimport preserve visual z-order via @zorder metadata")
    void sfcrRoundTripPreservesVisualZOrder() throws Exception {
        loadCircuitText(twoEquationTables());

        EquationTableElm first = findEquationTable(sim, 0);
        EquationTableElm second = findEquationTable(sim, 1);
        assertNotNull(first, "Expected first equation table");
        assertNotNull(second, "Expected second equation table");

        sim.bringToFront(first);

        String exported = new SFCRExporter(sim, SFCRExporter.ExportSyntax.BLOCK_FORMAT).export();
        assertTrue(exported.contains("@zorder"), "SFCR export should include a z-order metadata block");

        CirSim reloaded = new CirSim();
        reloaded.getBootstrap().initRunner();
        reloaded.getCircuitIOService().readCircuit(exported, 0);
        reloaded.analyzeCircuit();

        EquationTableElm reloadedFirst = findEquationTable(reloaded, 0);
        EquationTableElm reloadedSecond = findEquationTable(reloaded, 1);
        assertNotNull(reloadedFirst, "Expected first reloaded equation table after SFCR round-trip");
        assertNotNull(reloadedSecond, "Expected second reloaded equation table after SFCR round-trip");
        assertTrue(reloadedFirst.getZOrder() > reloadedSecond.getZOrder(), "SFCR round-trip should preserve the visual z-order relationship");
    }

    @Test
    @DisplayName("R-style SFCR export includes element uid in block metadata comments")
    void rStyleExportIncludesUidInMetadataComments() throws Exception {
        loadCircuitText(twoEquationTables());

        EquationTableElm first = findEquationTable(sim, 0);
        assertNotNull(first, "Expected first equation table");

        String exported = new SFCRExporter(sim, SFCRExporter.ExportSyntax.R_STYLE).export();
        assertTrue(exported.contains("# [ x=40 y=40 uid=" + first.getPersistentUid()),
            "R-style export should include the equation table UID in the block metadata comment");
    }

    private static String twoEquationTables() {
        return "@equations First x=40 y=40\n"
                + "  A ~ 1\n"
                + "@end\n"
                + "\n"
                + "@equations Second x=220 y=40\n"
                + "  B ~ 2\n"
                + "@end\n";
    }

    private static EquationTableElm findEquationTable(CirSim s, int ordinal) {
        int seen = 0;
        for (int i = 0; i < s.elmList.size(); i++) {
            CircuitElm elm = s.elmList.get(i);
            if (elm instanceof EquationTableElm) {
                if (seen == ordinal) {
                    return (EquationTableElm) elm;
                }
                seen++;
            }
        }
        return null;
    }

    private static String exportNativeDump(CirSim s) {
        StringBuilder out = new StringBuilder();
        out.append("$ 0 5.0E-6 10 50 5 50 0\n");
        CustomLogicModel.clearDumpedFlags();
        CustomCompositeModel.clearDumpedFlags();
        DiodeModel.clearDumpedFlags();
        TransistorModel.clearDumpedFlags();
        for (CircuitElm elm : s.elmList) {
            String model = elm.dumpModel();
            if (model != null && !model.isEmpty()) {
                out.append(model).append('\n');
            }
            out.append(s.getElementDumpWithUidForImportExport(elm)).append('\n');
        }
        return out.toString();
    }
}