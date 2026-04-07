package com.lushprojects.circuitjs1.client.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scope model")
class ScopeModelTest {
    @Test
    @DisplayName("2D mode shows only the first two plots")
    void rebuildVisiblePlotsUsesFirstTwoPlotsIn2dMode() {
        ScopeModel model = new ScopeModel();
        Vector<ScopePlot> plots = new Vector<ScopePlot>();
        plots.add(new ScopePlot(null, Scope.UNITS_V));
        plots.add(new ScopePlot(null, Scope.UNITS_A));
        plots.add(new ScopePlot(null, Scope.UNITS_W));
        model.setPlots(plots);

        model.rebuildVisiblePlots(true, false, false);
        assertEquals(2, model.getVisiblePlots().size());
        assertEquals(Scope.UNITS_V, model.getVisiblePlots().get(0).units);
        assertEquals(Scope.UNITS_A, model.getVisiblePlots().get(1).units);
    }

    @Test
    @DisplayName("history metadata is stored without owning history buffers")
    void setHistoryMetadataStoresSizeAndInterval() {
        ScopeModel model = new ScopeModel();

        model.setHistoryMetadata(42, 0.25);

        assertEquals(42, model.getHistorySize());
        assertEquals(0.25, model.getHistorySampleInterval(), 1e-12);
    }
}
