package com.lushprojects.circuitjs1.client.elements.economics;

import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.CircuitJavaSimTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @DisplayName("sum row uses current converged timestep instead of stale doStep cache")
    void sumRowUsesCurrentConvergedTimestep() throws Exception {
        loadCircuitText(FIXTURE);
        SFCTableElm table = findFirstSfcTable();
        assertNotNull(table, "Expected one SFC table in fixture");

        zeroAllSectorCells(table);
        table.setCellEquation(0, 0, "wages");
        table.setCellEquation(0, 1, "-wages");

        ComputedValues.setComputedValueDirect("wages.flow", 1.0);
        ComputedValues.commitConvergedValues();

        ComputedValues.setComputedValueDirect("wages.flow", 2.0);
        table.doStep();
        ComputedValues.commitConvergedValues();

        SFCTableRenderer renderer = getRenderer(table);

        assertEquals(2.0, renderer.getCachedSumValue(0), 1e-9,
                "Expected HH sum row to use the current converged flow value");
        assertEquals(-2.0, renderer.getCachedSumValue(1), 1e-9,
                "Expected Firms sum row to use the current converged flow value");
        assertEquals(0.0, renderer.getCachedSumValue(table.getCols() - 1), 1e-9,
                "Expected Σ grand total to stay balanced with current values");
    }

    @Test
    @DisplayName("SFC tables allow duplicate column headers")
    void sfcTablesAllowDuplicateColumnHeaders() throws Exception {
        loadCircuitText(FIXTURE);
        SFCTableElm table = findFirstSfcTable();
        assertNotNull(table, "Expected one SFC table in fixture");

        assertTrue(invokeAllowsDuplicateColumnHeaders(table),
                "SFC tables should allow duplicate column headers");
    }

    @Test
    @DisplayName("adjacent duplicate SFC headers are merged visually")
    void adjacentDuplicateSfcHeadersAreMergedVisually() throws Exception {
        loadCircuitText(NONE_TYPE_FIXTURE);
        SFCTableElm table = findFirstSfcTable();
        assertNotNull(table, "Expected one SFC table in fixture");

        table.setColumnHeader(0, "Households");
        table.setColumnHeader(1, "Households");
        table.setColumnHeader(2, "Banks");

        SFCTableRenderer renderer = getRenderer(table);

        assertEquals(1, invokeMergedHeaderEnd(renderer, 0),
                "Adjacent duplicate headers should merge into one visual span");
        assertFalse(invokeShouldDrawHeaderBoundary(renderer, 0, 1),
                "Merged adjacent duplicate headers should not draw an interior boundary");
        assertTrue(invokeShouldDrawHeaderBoundary(renderer, 1, 2),
                "A boundary should still be drawn when the next header label differs");
    }

    @Test
    @DisplayName("type row hides when all regular columns are none")
    void typeRowHidesWhenAllRegularColumnsAreNone() throws Exception {
        loadCircuitText(NONE_TYPE_FIXTURE);
        SFCTableElm table = findFirstSfcTable();
        assertNotNull(table, "Expected one SFC table in fixture");

        SFCTableRenderer renderer = getRenderer(table);

        assertFalse(invokeShouldShowColumnTypeRow(table),
                "Type row should be hidden when every regular column is None");
        assertEquals(0, invokeExtraRowsAfterHeaderHeight(renderer),
                "Hidden type row should not consume extra header height");
    }

    @Test
    @DisplayName("type row shows when any regular column has a visible type")
    void typeRowShowsWhenAnyRegularColumnHasVisibleType() throws Exception {
        loadCircuitText(FIXTURE);
        SFCTableElm table = findFirstSfcTable();
        assertNotNull(table, "Expected one SFC table in fixture");

        SFCTableRenderer renderer = getRenderer(table);

        assertTrue(invokeShouldShowColumnTypeRow(table),
                "Type row should remain visible when at least one regular column has a type");
        assertTrue(invokeExtraRowsAfterHeaderHeight(renderer) > 0,
                "Visible type row should reserve header height");
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

    private boolean invokeShouldShowColumnTypeRow(SFCTableElm table) throws Exception {
        Method method = SFCTableElm.class.getDeclaredMethod("shouldShowColumnTypeRow");
        method.setAccessible(true);
        return ((Boolean) method.invoke(table)).booleanValue();
    }

    private int invokeExtraRowsAfterHeaderHeight(SFCTableRenderer renderer) throws Exception {
        Method method = SFCTableRenderer.class.getDeclaredMethod("getExtraRowsAfterHeaderHeight");
        method.setAccessible(true);
        return ((Integer) method.invoke(renderer)).intValue();
    }

    private boolean invokeAllowsDuplicateColumnHeaders(TableElm table) throws Exception {
        Method method = TableElm.class.getDeclaredMethod("allowsDuplicateColumnHeaders");
        method.setAccessible(true);
        return ((Boolean) method.invoke(table)).booleanValue();
    }

    private int invokeMergedHeaderEnd(SFCTableRenderer renderer, int startCol) throws Exception {
        Method method = SFCTableRenderer.class.getDeclaredMethod("getMergedHeaderEnd", int.class);
        method.setAccessible(true);
        return ((Integer) method.invoke(renderer, startCol)).intValue();
    }

    private boolean invokeShouldDrawHeaderBoundary(SFCTableRenderer renderer, int leftCol, int rightCol) throws Exception {
        Method method = SFCTableRenderer.class.getDeclaredMethod("shouldDrawHeaderBoundary", int.class, int.class);
        method.setAccessible(true);
        return ((Boolean) method.invoke(renderer, leftCol, rightCol)).booleanValue();
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

    private static final String NONE_TYPE_FIXTURE =
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "% showToolbar true\n" +
            "265 304 80 880 212 0 4 5 6 16 0 false 2 0 false 5 0 false SFC\\sTable\\s4 \\0 Households Firms Banks Govt Σ Consumption Wages Interest Taxes 0 0 0 0 0 NONE NONE NONE NONE COMPUTED -100 \\p1000 0 0 \\0 wages -2*wages 0 0 \\0 \\p5 -10 wages 0 \\0 -200 -15 0 \\p35 \\0 true 0.000001\n" +
            "207 480 256 544 256 4 wages\n" +
            "R 480 256 400 256 0 0 40 5 0 0 0.5 V\n" +
            "263 263 37 901 302 0 Main\n" +
            "% AST 1 1\n";
}
