package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.economics.SFCTableElm;
import com.lushprojects.circuitjs1.client.elements.electronics.DiodeModel;
import com.lushprojects.circuitjs1.client.elements.electronics.TransistorModel;
import com.lushprojects.circuitjs1.client.io.SFCRExporter;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.StringTokenizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SFC table export position")
class SFCTableExportPositionTest extends CircuitJavaSimTestBase {

    @Test
    @DisplayName("moving an SFC table updates exported coordinates")
    void movingSfcTableUpdatesExportedCoordinates() throws Exception {
        loadCircuitText(
                "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
                "% voltageUnit $\n" +
                "% showToolbar true\n" +
                "265 304 80 880 212 0 4 5 6 16 0 false 2 0 false 5 0 false SFC\\sTable\\s4 \\0 Households Firms Banks Govt Σ Consumption Wages Interest Taxes 0 0 0 0 0 SECTOR SECTOR SECTOR SECTOR COMPUTED -100 \\p1000 0 0 \\0 wages -2*wages 0 0 \\0 \\p5 -10 wages 0 \\0 -200 -15 0 \\p35 \\0 true 0.000001\n" +
                "207 480 256 544 256 4 wages\n" +
                "R 480 256 400 256 0 0 40 5 0 0 0.5 V\n" +
                "263 263 37 901 302 0 Main\n" +
                "% AST 1 1\n"
        );

        SFCTableElm table = findFirstSfcTable();
        assertNotNull(table, "Expected one SFC table in fixture");

        int originalX1 = table.x;
        int originalY1 = table.y;
        int originalX2 = table.x2;
        int originalY2 = table.y2;

        int dx = 96;
        int dy = 48;
        table.setElementPosition(originalX1 + dx, originalY1 + dy, originalX2 + dx, originalY2 + dy);

        String exported = exportElementDumpOnly(firstLineOfCircuitFixture(), sim);
        int[] coords = extractCoordsForDumpType(exported, 265);
        assertNotNull(coords, "Expected exported circuit to contain an SFC table line");

        assertEquals(originalX1 + dx, coords[0], "Exported x1 should match moved position");
        assertEquals(originalY1 + dy, coords[1], "Exported y1 should match moved position");
        assertEquals(originalX2 + dx, coords[2], "Exported x2 should match moved position");
        assertEquals(originalY2 + dy, coords[3], "Exported y2 should match moved position");
    }

    @Test
    @DisplayName("moving an SFC table updates SFCR @matrix position metadata")
    void movingSfcTableUpdatesSfcrMatrixHeaderPosition() throws Exception {
        loadCircuitText(
                "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
                "% voltageUnit $\n" +
                "% showToolbar true\n" +
                "265 304 80 880 212 0 4 5 6 16 0 false 2 0 false 5 0 false SFC\\sTable\\s4 \\0 Households Firms Banks Govt Σ Consumption Wages Interest Taxes 0 0 0 0 0 SECTOR SECTOR SECTOR SECTOR COMPUTED -100 \\p1000 0 0 \\0 wages -2*wages 0 0 \\0 \\p5 -10 wages 0 \\0 -200 -15 0 \\p35 \\0 true 0.000001\n" +
                "207 480 256 544 256 4 wages\n" +
                "R 480 256 400 256 0 0 40 5 0 0 0.5 V\n" +
                "263 263 37 901 302 0 Main\n" +
                "% AST 1 1\n"
        );

        SFCTableElm table = findFirstSfcTable();
        assertNotNull(table, "Expected one SFC table in fixture");

        int originalX1 = table.x;
        int originalY1 = table.y;
        int originalX2 = table.x2;
        int originalY2 = table.y2;

        String sfcrBeforeMove = new SFCRExportContext(sim, SFCRExporter.ExportSyntax.BLOCK_FORMAT)
                .exportMatrixTable(table);
        String matrixHeaderBefore = findMatrixHeader(sfcrBeforeMove, "SFC_Table_4");
        assertNotNull(matrixHeaderBefore, "Expected SFCR export to include @matrix SFC_Table_4");
        assertTrue(matrixHeaderBefore.contains("x=" + originalX1), "Expected pre-move x metadata in @matrix header");
        assertTrue(matrixHeaderBefore.contains("y=" + originalY1), "Expected pre-move y metadata in @matrix header");

        int dx = 96;
        int dy = 48;
        table.setElementPosition(originalX1 + dx, originalY1 + dy, originalX2 + dx, originalY2 + dy);

        String sfcrAfterMove = new SFCRExportContext(sim, SFCRExporter.ExportSyntax.BLOCK_FORMAT)
                .exportMatrixTable(table);
        String matrixHeaderAfter = findMatrixHeader(sfcrAfterMove, "SFC_Table_4");
        assertNotNull(matrixHeaderAfter, "Expected moved export to include @matrix SFC_Table_4");

        assertTrue(matrixHeaderAfter.contains("x=" + (originalX1 + dx)), "Expected moved x metadata in @matrix header");
        assertTrue(matrixHeaderAfter.contains("y=" + (originalY1 + dy)), "Expected moved y metadata in @matrix header");
        assertFalse(matrixHeaderAfter.equals(matrixHeaderBefore), "Expected @matrix header to change after moving table");
    }

    private SFCTableElm findFirstSfcTable() {
        for (CircuitElm elm : sim.elmList) {
            if (elm instanceof SFCTableElm) {
                return (SFCTableElm) elm;
            }
        }
        return null;
    }

    private int[] extractCoordsForDumpType(String dump, int dumpType) {
        String[] lines = dump.split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            StringTokenizer tok = new StringTokenizer(line);
            if (!tok.hasMoreTokens()) {
                continue;
            }

            String typeToken = tok.nextToken();
            int type;
            try {
                type = Integer.parseInt(typeToken);
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (type != dumpType) {
                continue;
            }

            int x1 = Integer.parseInt(tok.nextToken());
            int y1 = Integer.parseInt(tok.nextToken());
            int x2 = Integer.parseInt(tok.nextToken());
            int y2 = Integer.parseInt(tok.nextToken());
            return new int[] { x1, y1, x2, y2 };
        }
        return null;
    }

    private String findMatrixHeader(String sfcrText, String tableName) {
        String needle = "@matrix " + tableName;
        String[] lines = sfcrText.split("\\R");
        for (String line : lines) {
            if (line.startsWith(needle)) {
                return line;
            }
        }
        return null;
    }

    private static String exportElementDumpOnly(String optionsLine, CirSim s) {
        StringBuilder out = new StringBuilder();
        out.append(optionsLine).append('\n');
        CustomLogicModel.clearDumpedFlags();
        CustomCompositeModel.clearDumpedFlags();
        DiodeModel.clearDumpedFlags();
        TransistorModel.clearDumpedFlags();
        for (CircuitElm elm : s.elmList) {
            String model = elm.dumpModel();
            if (model != null && !model.isEmpty()) {
                out.append(model).append('\n');
            }
            out.append(s.getImportExportHelper().getElementDumpWithUid(elm)).append('\n');
        }
        return out.toString();
    }

    private static String firstLineOfCircuitFixture() {
        return "$ 1 0.000005 10.20027730826997 50 5 50 5e-11";
    }
}
