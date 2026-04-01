package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.economics.SFCTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.SFCTableRenderer;
import com.lushprojects.circuitjs1.client.elements.economics.ComputedValues;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("SFCTableRenderer")
class SFCTableRendererTest extends CircuitJavaSimTestBase {

    @Test
    @DisplayName("sum row includes cells evaluated from last()")
    void sumRowIncludesHistoricalCellValues() throws Exception {
        loadCircuitText(FIXTURE);
        SFCTableElm table = findFirstSfcTable();
        assertNotNull(table, "Expected one SFC table in fixture");

        zeroAllSectorCells(table);
        table.setCellEquation(0, 0, "last(wages)");
        table.setCellEquation(0, 1, "-last(wages)");
        ComputedValues.setComputedValueDirect("wages_init", 0.5);
        table.doStep();

        SFCTableRenderer renderer = getRenderer(table);

        assertEquals(0.5, renderer.getCachedSumValue(0), 1e-9,
                "Expected HH sum row to include last(wages)");
        assertEquals(-0.5, renderer.getCachedSumValue(1), 1e-9,
                "Expected Firms sum row to include -last(wages)");
        assertEquals(0.0, renderer.getCachedSumValue(table.getCols() - 1), 1e-9,
                "Expected Σ column total to remain balanced");
    }

    private void zeroAllSectorCells(SFCTableElm table) {
        for (int row = 0; row < table.getRows(); row++) {
            for (int col = 0; col < table.getCols() - 1; col++) {
                table.setCellEquation(row, col, "0");
            }
        }
    }

    private SFCTableElm findFirstSfcTable() {
        for (CircuitElm elm : sim.elmList) {
            if (elm instanceof SFCTableElm) {
                return (SFCTableElm) elm;
            }
        }
        return null;
    }

    private SFCTableRenderer getRenderer(SFCTableElm table) throws Exception {
        Field field = SFCTableElm.class.getDeclaredField("sfcRenderer");
        field.setAccessible(true);
        return (SFCTableRenderer) field.get(table);
    }

    private static final String FIXTURE =
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "% showToolbar true\n" +
            "265 304 80 880 212 0 4 5 6 16 0 false 2 0 false 5 0 false SFC\\sTable\\s4 \\0 Households Firms Banks Govt Σ Consumption Wages Interest Taxes 0 0 0 0 0 SECTOR SECTOR SECTOR SECTOR COMPUTED -100 \\p1000 0 0 \\0 wages -2*wages 0 0 \\0 \\p5 -10 wages 0 \\0 -200 -15 0 \\p35 \\0 true 0.000001\n" +
            "207 480 256 544 256 4 wages\n" +
            "R 480 256 400 256 0 0 40 5 0 0 0.5 V\n" +
            "263 263 37 901 302 0 Main\n" +
            "% AST 1 1\n";
}
