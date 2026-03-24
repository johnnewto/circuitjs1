package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.economics.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("computeSyncPatches() — golden scenarios")
class SyncPatchGoldenTest {

    private TableContentViewStub sourceBase() {
        return new TableContentViewStub(
            "Source",
            new String[] {"Cash", "Debt", "Equity", "A_L_E"},
            new String[] {"Wages", "Interest"},
            new String[][] {
                {"100", "", "", ""},
                {"", "5", "", ""}
            },
            new double[] {10.0, 20.0, 30.0, 0.0}
        );
    }

    @Test
    @DisplayName("new flow in source absent from target → ADD_ROW for Wages")
    void newFlowInSourceProducesSingleAddRowPatch() {
        TableContentViewStub source = sourceBase();
        TableContentViewStub target = new TableContentViewStub(
            "Target",
            new String[] {"Cash", "Debt", "Equity", "A_L_E"},
            new String[] {"Interest"},
            new String[][] {
                {"", "5", "", ""}
            },
            new double[] {10.0, 20.0, 30.0, 0.0}
        );

        List<SyncPatch> patches = StockFlowRegistry.computeSyncPatches(source, target);

        assertEquals(1, patches.size());
        assertEquals(SyncPatch.Kind.ADD_ROW, patches.get(0).kind);
        assertEquals("Wages", patches.get(0).flowDesc);
        assertEquals("100", patches.get(0).rowEquations.get(Integer.valueOf(0)));
    }

    @Test
    @DisplayName("changed cell equation → SET_CELL_EQUATION patch")
    void changedEquationProducesSetCellEquationPatch() {
        TableContentViewStub source = sourceBase();
        TableContentViewStub target = new TableContentViewStub(
            "Target",
            new String[] {"Cash", "Debt", "Equity", "A_L_E"},
            new String[] {"Wages", "Interest"},
            new String[][] {
                {"90", "", "", ""},
                {"", "5", "", ""}
            },
            new double[] {10.0, 20.0, 30.0, 0.0}
        );

        List<SyncPatch> patches = StockFlowRegistry.computeSyncPatches(source, target);

        assertEquals(1, patches.size());
        assertEquals(SyncPatch.Kind.SET_CELL_EQUATION, patches.get(0).kind);
        assertEquals(0, patches.get(0).row);
        assertEquals(0, patches.get(0).col);
        assertEquals("100", patches.get(0).equation);
    }

    @Test
    @DisplayName("source equation cleared to blank → SET_CELL_EQUATION with empty string")
    void clearingEquationProducesEmptySetCellEquationPatch() {
        TableContentViewStub source = new TableContentViewStub(
            "Source",
            new String[] {"Cash", "Debt", "Equity", "A_L_E"},
            new String[] {"Wages"},
            new String[][] {
                {"0", "", "", ""}
            },
            new double[] {10.0, 20.0, 30.0, 0.0}
        );
        TableContentViewStub target = new TableContentViewStub(
            "Target",
            new String[] {"Cash", "Debt", "Equity", "A_L_E"},
            new String[] {"Wages"},
            new String[][] {
                {"100", "", "", ""}
            },
            new double[] {10.0, 20.0, 30.0, 0.0}
        );

        List<SyncPatch> patches = StockFlowRegistry.computeSyncPatches(source, target);

        assertEquals(1, patches.size());
        assertEquals(SyncPatch.Kind.SET_CELL_EQUATION, patches.get(0).kind);
        assertEquals("", patches.get(0).equation);
    }

    @Test
    @DisplayName("differing initial value → SET_INITIAL_VALUE patch")
    void initialValueDriftProducesSetInitialValuePatch() {
        TableContentViewStub source = sourceBase();
        TableContentViewStub target = new TableContentViewStub(
            "Target",
            new String[] {"Cash", "Debt", "Equity", "A_L_E"},
            new String[] {"Wages", "Interest"},
            new String[][] {
                {"100", "", "", ""},
                {"", "5", "", ""}
            },
            new double[] {999.0, 20.0, 30.0, 0.0}
        );

        List<SyncPatch> patches = StockFlowRegistry.computeSyncPatches(source, target);

        assertEquals(1, patches.size());
        assertEquals(SyncPatch.Kind.SET_INITIAL_VALUE, patches.get(0).kind);
        assertEquals(0, patches.get(0).col);
        assertEquals(10.0, patches.get(0).initialValue, 1e-12);
    }

    @Test
    @DisplayName("identical source and target → no patches")
    void identicalTablesProduceNoPatches() {
        TableContentViewStub source = sourceBase();
        TableContentViewStub target = sourceBase();

        List<SyncPatch> patches = StockFlowRegistry.computeSyncPatches(source, target);

        assertEquals(0, patches.size());
    }

    @Test
    @DisplayName("stock absent in target → no patches emitted for that stock")
    void absentStockInTargetProducesNoPatchForThatStock() {
        TableContentViewStub source = sourceBase();
        TableContentViewStub target = new TableContentViewStub(
            "Target",
            new String[] {"Debt", "Equity"},
            new String[] {"Wages", "Interest"},
            new String[][] {
                {"", ""},
                {"5", ""}
            },
            new double[] {20.0, 30.0}
        );

        List<SyncPatch> patches = StockFlowRegistry.computeSyncPatches(source, target);

        assertEquals(0, patches.size());
    }
}
