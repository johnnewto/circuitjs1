package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm;
import com.lushprojects.circuitjs1.client.io.SFCRExporter;
import com.lushprojects.circuitjs1.client.io.SFCRSyntaxNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ResourceLock("SFCRParser")
@DisplayName("Equation table SFCR position round-trip")
class EquationTableSfcrPositionRoundTripTest extends CircuitJavaSimTestBase {

    @Test
    @DisplayName("moved equation table keeps position after SFCR export and reimport")
    void movedEquationTableKeepsPositionAfterSfcrExportAndReimport() throws Exception {
        String source =
                "@equations Demo x=200 y=120\n" +
                "  Y ~ 1\n" +
                "@end\n";

        loadCircuitText(source);
        EquationTableElm original = findFirstEquationTable(sim);
        assertNotNull(original, "Expected equation table after initial SFCR import");

        int movedX1 = original.x + 96;
        int movedY1 = original.y + 64;
        int movedX2 = original.x2 + 96;
        int movedY2 = original.y2 + 64;
        original.setElementPosition(movedX1, movedY1, movedX2, movedY2);

        String exported = new SFCRExporter(sim, SFCRExporter.ExportSyntax.R_STYLE).export();
        String normalizedForImport = new SFCRSyntaxNormalizer().normalize(exported);

        CirSim reloaded = new CirSim();
        reloaded.getBootstrap().initRunner();
        reloaded.getCircuitIOService().readCircuit(normalizedForImport, 0);
        reloaded.analyzeCircuit();

        EquationTableElm reloadedTable = findFirstEquationTable(reloaded);
        assertNotNull(reloadedTable, "Expected equation table after SFCR reimport");
        assertEquals(movedX1, reloadedTable.x, "Reloaded table x should match moved x");
        assertEquals(movedY1, reloadedTable.y, "Reloaded table y should match moved y");
    }

    private static EquationTableElm findFirstEquationTable(CirSim s) {
        for (int i = 0; i < s.elmList.size(); i++) {
            CircuitElm elm = s.elmList.get(i);
            if (elm instanceof EquationTableElm) {
                return (EquationTableElm) elm;
            }
        }
        return null;
    }
}
