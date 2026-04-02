package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.economics.SFCTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.TableColumn.ColumnType;
import com.lushprojects.circuitjs1.client.io.SFCRExporter;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SFC table column type defaults")
class SFCTableColumnTypeDefaultsTest extends CircuitJavaSimTestBase {

    @Test
    @DisplayName("legacy SFC dump without column types defaults regular columns to none")
    void legacySfcDumpWithoutColumnTypesDefaultsRegularColumnsToNone() throws Exception {
        loadCircuitText(
                "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
                "% voltageUnit $\n" +
                "% showToolbar true\n" +
                "265 304 80 880 212 0 4 5 6 16 0 false 2 0 false 5 0 false SFC\\sTable\\s4 \\0 Households Firms Banks Govt Σ Consumption Wages Interest Taxes 0 0 0 0 0 -100 \\p1000 0 0 \\0 wages -2*wages 0 0 \\0 \\p5 -10 wages 0 \\0 -200 -15 0 \\p35 \\0 true 0.000001\n" +
                "% AST 1 1\n"
        );

        SFCTableElm table = findFirstSfcTable();
        assertNotNull(table, "Expected one SFC table in fixture");
        assertEquals(ColumnType.NONE, table.getColumnType(0), "First regular column should default to None");
        assertEquals(ColumnType.NONE, table.getColumnType(3), "Last regular column should default to None");
        assertEquals(ColumnType.COMPUTED, table.getColumnType(4), "Sigma column should remain computed");
        assertEquals("-100", table.getCellEquation(0, 0), "Legacy dump equations should stay aligned when type section is missing");
    }

    @Test
    @DisplayName("R-style export writes blank type strings for none columns")
    void rStyleExportWritesBlankTypeStringsForNoneColumns() throws Exception {
        loadCircuitText(
                "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
                "% voltageUnit $\n" +
                "% showToolbar true\n" +
                "265 304 80 880 212 0 4 5 6 16 0 false 2 0 false 5 0 false SFC\\sTable\\s4 \\0 Households Firms Banks Govt Σ Consumption Wages Interest Taxes 0 0 0 0 0 NONE NONE NONE NONE COMPUTED -100 \\p1000 0 0 \\0 wages -2*wages 0 0 \\0 \\p5 -10 wages 0 \\0 -200 -15 0 \\p35 \\0 true 0.000001\n" +
                "% AST 1 1\n"
        );

        SFCTableElm table = findFirstSfcTable();
        assertNotNull(table, "Expected one SFC table in fixture");

        String exported = new SFCRExportContext(sim, SFCRExporter.ExportSyntax.R_STYLE)
                .exportMatrixTable(table);

        assertTrue(exported.contains("type = c(\"\", \"\", \"\", \"\")"),
                "None column types should export as blank R-style type strings");
    }

    private SFCTableElm findFirstSfcTable() {
        for (CircuitElm elm : sim.elmList) {
            if (elm instanceof SFCTableElm) {
                return (SFCTableElm) elm;
            }
        }
        return null;
    }
}