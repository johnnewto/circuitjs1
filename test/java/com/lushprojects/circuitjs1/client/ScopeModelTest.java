package com.lushprojects.circuitjs1.client.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Vector;
import org.junit.jupiter.api.Test;

class ScopeModelTest {
    @Test
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
    void captureToHistoryDownsamplesAndPreservesExtrema() {
        ScopeModel model = new ScopeModel();
        ScopePlot plot = new ScopePlot(null, Scope.UNITS_V);
        plot.minValues = new double[1];
        plot.maxValues = new double[1];

        Vector<ScopePlot> plots = new Vector<ScopePlot>();
        plots.add(plot);
        model.setPlots(plots);
        model.initializeHistoryBuffers(1, 1.0);

        for (int i = 0; i <= 4; i++) {
            plot.ptr = 0;
            plot.minValues[0] = i;
            plot.maxValues[0] = i + 0.5;
            assertTrue(model.captureToHistory(i, 0.0, 1));
        }

        assertEquals(2.0, model.getHistorySampleInterval(), 1e-12);
        assertEquals(3, model.getHistorySize());
        assertEquals(0.0, plot.historyMinValues[0], 1e-12);
        assertEquals(1.5, plot.historyMaxValues[0], 1e-12);
        assertEquals(2.0, plot.historyMinValues[1], 1e-12);
        assertEquals(3.5, plot.historyMaxValues[1], 1e-12);
        assertEquals(4.0, plot.historyMinValues[2], 1e-12);
        assertEquals(4.5, plot.historyMaxValues[2], 1e-12);
    }
}
