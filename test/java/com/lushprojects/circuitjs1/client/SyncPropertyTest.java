package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("computeSyncPatches() — pure-function properties")
class SyncPropertyTest {

    private TableContentViewStub sampleA() {
        return new TableContentViewStub(
            "A",
            new String[] {"Cash", "Debt", "Equity", "A_L_E"},
            new String[] {"Wages", "Interest"},
            new String[][] {
                {"100", "", "", ""},
                {"", "5", "", ""}
            },
            new double[] {10.0, 20.0, 30.0, 0.0}
        );
    }

    private TableContentViewStub sampleB() {
        return new TableContentViewStub(
            "B",
            new String[] {"Cash", "Debt", "Equity", "A_L_E"},
            new String[] {"Interest"},
            new String[][] {
                {"", "2", "", ""}
            },
            new double[] {8.0, 20.0, 30.0, 0.0}
        );
    }

    @Test
    @DisplayName("applying patches then recomputing yields empty patch list")
    void idempotence() {
        TableContentViewStub source = sampleA();
        TableContentViewStub target = sampleB();

        List<SyncPatch> first = StockFlowRegistry.computeSyncPatches(source, target);
        TableContentViewStub patchedTarget = target.applyPatches(first);
        List<SyncPatch> second = StockFlowRegistry.computeSyncPatches(source, patchedTarget);

        assertEquals(0, second.size());
    }

    @Test
    @DisplayName("source synced against itself → no patches")
    void selfNoOp() {
        TableContentViewStub source = sampleA();

        List<SyncPatch> patches = StockFlowRegistry.computeSyncPatches(source, source);

        assertEquals(0, patches.size());
    }

    @Test
    @DisplayName("all-blank source equations do not produce ADD_ROW patches")
    void emptySourceDoesNotWipeByAddingRows() {
        TableContentViewStub source = new TableContentViewStub(
            "Source",
            new String[] {"Cash", "Debt", "Equity", "A_L_E"},
            new String[] {"Wages"},
            new String[][] {
                {"", "", "", ""}
            },
            new double[] {10.0, 20.0, 30.0, 0.0}
        );

        TableContentViewStub target = new TableContentViewStub(
            "Target",
            new String[] {"Cash", "Debt", "Equity", "A_L_E"},
            new String[] {"Wages", "Profit"},
            new String[][] {
                {"100", "", "", ""},
                {"7", "", "", ""}
            },
            new double[] {10.0, 20.0, 30.0, 0.0}
        );

        List<SyncPatch> patches = StockFlowRegistry.computeSyncPatches(source, target);

        long addRows = patches.stream().filter(p -> p.kind == SyncPatch.Kind.ADD_ROW).count();
        assertEquals(0, addRows);
    }
}
