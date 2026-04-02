package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramElm;
import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramParser;
import com.lushprojects.circuitjs1.client.elements.economics.ComputedValues;
import com.lushprojects.circuitjs1.client.elements.economics.TableElm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("ComputedValues")
@DisplayName("Sequence diagram source-table binding")
class SequenceDiagramSourceTableBindingTest extends CircuitJavaSimTestBase {

    @Test
    @DisplayName("source-bound PlantUML diagram renders transaction table data and refreshes when flows change")
    void sourceBoundDiagramRendersAndRefreshes() throws Exception {
        Path fixturePath = Paths.get(System.getProperty("projectDir"), "test/resources/sfcr_debug_reference.md");
        String fixture = Files.readString(fixturePath, StandardCharsets.UTF_8);
        String circuitText = fixture + "\n```{r}\n"
            + "@startuml x=96 y=32 scale=0.6\n"
            + "source: Transaction Flow Matrix\n"
            + "@end\n"
            + "```\n";

        loadCircuitText(circuitText);
        SequenceDiagramElm diagram = findFirstSequenceDiagram();
        assertNotNull(diagram, "Expected a source-bound PlantUML sequence diagram");

        TableElm transactionFlowMatrix = findTable("Transaction Flow Matrix");
        assertNotNull(transactionFlowMatrix, "Expected Transaction Flow Matrix source table");
        int wagesRow = findRow(transactionFlowMatrix, "Wages");
        int householdsCol = findColumn(transactionFlowMatrix, "Households");
        int productionCol = findColumn(transactionFlowMatrix, "Production");
        assertTrue(wagesRow >= 0 && householdsCol >= 0 && productionCol >= 0,
            "Expected Wages row with Households and Production sector columns");

        transactionFlowMatrix.setCellEquation(wagesRow, householdsCol, "SeqFlowHouseholds");
        transactionFlowMatrix.setCellEquation(wagesRow, productionCol, "SeqFlowProduction");
        ComputedValues.setComputedValueDirect(ComputedValues.getFlowComputedKeyForName("SeqFlowHouseholds"), 10);
        ComputedValues.setComputedValueDirect(ComputedValues.getFlowComputedKeyForName("SeqFlowProduction"), -10);

        String firstRendered = diagram.getRenderedPlantUmlSource();
        assertEquals("Transaction Flow Matrix", diagram.getSourceTableName(),
            "Expected named source table binding to be detected");
        assertTrue(firstRendered.contains("title Transaction Flow Matrix"),
            "Rendered source should use the source table title");
        assertTrue(firstRendered.contains("participant Households"),
            "Rendered source should include sector participants");
        assertTrue(firstRendered.contains("participant Production"),
            "Rendered source should include sector participants");
        assertTrue(firstRendered.contains("Production -> Households : Wages\\n(" + CircuitElm.showFormat.format(10) + ")"),
            "Rendered source should include transaction arrows from the bound table");

        ComputedValues.setComputedValueDirect(ComputedValues.getFlowComputedKeyForName("SeqFlowHouseholds"), 25);
        ComputedValues.setComputedValueDirect(ComputedValues.getFlowComputedKeyForName("SeqFlowProduction"), -25);
        String secondRendered = diagram.getRenderedPlantUmlSource();
        assertNotEquals(firstRendered, secondRendered,
            "Rendered source should refresh when the bound table values change");
        assertTrue(secondRendered.contains("Production -> Households : Wages\\n(" + CircuitElm.showFormat.format(25) + ")"),
            "Refreshed source should continue to include bound transaction labels with updated values");
    }

    @Test
    @DisplayName("source-bound diagram preserves delta transactions when values are currently zero")
    void sourceBoundDiagramKeepsDeltaTransactions() throws Exception {
        Path fixturePath = Paths.get(System.getProperty("projectDir"), "test/resources/sfcr_debug_reference.md");
        String fixture = Files.readString(fixturePath, StandardCharsets.UTF_8);
        String circuitText = fixture + "\n```{r}\n"
            + "@startuml x=96 y=32 scale=0.6\n"
            + "source: Transaction Flow Matrix\n"
            + "@end\n"
            + "```\n";
        loadCircuitText(circuitText);

        SequenceDiagramElm diagram = findFirstSequenceDiagram();
        assertNotNull(diagram, "Expected a source-bound PlantUML sequence diagram");

        TableElm transactionFlowMatrix = findTable("Transaction Flow Matrix");
        assertNotNull(transactionFlowMatrix, "Expected Transaction Flow Matrix source table");
        int moneyStockRow = findRow(transactionFlowMatrix, "Money stock");
        int householdsCol = findColumn(transactionFlowMatrix, "Households");
        int govtCol = findColumn(transactionFlowMatrix, "Govt");
        assertTrue(moneyStockRow >= 0 && householdsCol >= 0 && govtCol >= 0,
            "Expected Money stock row with Households and Govt sector columns");

        transactionFlowMatrix.setCellEquation(moneyStockRow, householdsCol, "SeqDeltaHouseholds");
        transactionFlowMatrix.setCellEquation(moneyStockRow, govtCol, "SeqDeltaGovt");
        ComputedValues.setComputedValueDirect(ComputedValues.getFlowComputedKeyForName("SeqDeltaHouseholds"), -7);
        ComputedValues.setComputedValueDirect(ComputedValues.getFlowComputedKeyForName("SeqDeltaGovt"), 7);

        String rendered = diagram.getRenderedPlantUmlSource();
        assertTrue(rendered.contains("Households -> Govt : Money stock"),
            "Delta-style zero row should still generate a sequence arrow");
        assertTrue(rendered.contains("Households -> Govt : Money stock\\n(" + CircuitElm.showFormat.format(7) + ")"),
            "Sequence diagram should use the source table's cached display value");
    }

    @Test
    @DisplayName("SFC tables with asset/liability/equity column types still generate participants")
    void sfcTableWithNonSectorTypesStillGeneratesParticipants() throws Exception {
        loadCircuitText(
                "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
                "% voltageUnit $\n" +
                "% showToolbar true\n" +
                "265 304 80 880 212 0 4 5 6 16 0 false 2 0 false 5 0 false SFC\\sTable\\s4 \\0 Households Firms Banks Govt Σ Consumption Wages Interest Taxes 0 0 0 0 0 ASSET LIABILITY EQUITY ASSET COMPUTED -100 \\p1000 0 0 \\0 wages -2*wages 0 0 \\0 \\p5 -10 wages 0 \\0 -200 -15 0 \\p35 \\0 true 0.000001\n"
        );

        TableElm table = findTable("SFC Table 4");
        assertNotNull(table, "Expected SFC table fixture to load");

        String rendered = SequenceDiagramParser.buildDiagramFromSourceTable(table);
        assertTrue(rendered.contains("participant Households"),
            "Sequence diagram should include participants for non-computed SFC columns");
        assertTrue(rendered.contains("participant Firms"),
            "Sequence diagram should include all non-computed SFC columns");
        assertFalse(rendered.contains("No sectors defined"),
            "Sequence diagram should not report missing sectors when SFC columns are Asset/Liability/Equity typed");
    }

    private SequenceDiagramElm findFirstSequenceDiagram() {
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            if (elm instanceof SequenceDiagramElm) {
                return (SequenceDiagramElm) elm;
            }
        }
        return null;
    }

    private TableElm findTable(String title) {
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            if (elm instanceof TableElm) {
                TableElm table = (TableElm) elm;
                if (title.equals(table.getTableTitle())) {
                    return table;
                }
            }
        }
        return null;
    }

    private int findRow(TableElm table, String label) {
        if (table == null || table.rowDescriptions == null) {
            return -1;
        }
        for (int i = 0; i < table.rowDescriptions.length; i++) {
            if (label.equals(table.rowDescriptions[i])) {
                return i;
            }
        }
        return -1;
    }

    private int findColumn(TableElm table, String stockName) {
        if (table == null || table.columns == null) {
            return -1;
        }
        for (int i = 0; i < table.columns.size(); i++) {
            if (stockName.equals(table.columns.get(i).getStockName())) {
                return i;
            }
        }
        return -1;
    }
}
