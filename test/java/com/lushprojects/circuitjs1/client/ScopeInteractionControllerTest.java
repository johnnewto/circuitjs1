package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lushprojects.circuitjs1.client.elements.ActionScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import org.junit.jupiter.api.Test;

class ScopeInteractionControllerTest {
    @Test
    void plotXBoundsCheckIsInclusiveExclusive() {
        assertTrue(ScopeInteractionController.isWithinPlotX(0, 10));
        assertTrue(ScopeInteractionController.isWithinPlotX(9, 10));
        assertFalse(ScopeInteractionController.isWithinPlotX(-1, 10));
        assertFalse(ScopeInteractionController.isWithinPlotX(10, 10));
    }

    @Test
    void drawFromZeroCursorMappingDelegatesToLayoutRule() {
        double t = ScopeInteractionController.mapCursorTimeForDrawFromZero(2.0, 5.0, 80, 240, false, 0.0025);
        assertEquals(2.2, t, 1e-12);
    }

    @Test
    void scrollingCursorMappingUsesSampleAge() {
        double t = ScopeInteractionController.mapCursorTimeForScrolling(1.0, 0.01, 2, 8, 2, 10);
        assertEquals(0.9, t, 1e-12);
        assertEquals(-1.0, ScopeInteractionController.mapCursorTimeForScrolling(1.0, 0.01, 2, 40, 2, 10), 1e-12);
    }

    @Test
    void hoveredActionSelectionUsesThreshold() {
        List<ActionScheduler.ScheduledAction> actions = new ArrayList<ActionScheduler.ScheduledAction>();
        ActionScheduler.ScheduledAction a = new ActionScheduler.ScheduledAction(1, 0.5, "", 0.0, "", "", true, false);
        actions.add(a);
        int idx = ScopeInteractionController.findHoveredActionIndex(actions, 50, 100, 0.0, 1.0, 5);
        assertEquals(0, idx);
        assertEquals(-1, ScopeInteractionController.findHoveredActionIndex(actions, 70, 100, 0.0, 1.0, 5));
    }

    @Test
    void nearestPlotSelectionChoosesSmallestDistance() {
        ScopePlot p1 = new ScopePlot(null, Scope.UNITS_V);
        ScopePlot p2 = new ScopePlot(null, Scope.UNITS_V);
        p1.maxValues = new double[] {0.0, 0.0, 0.0, 0.0};
        p2.maxValues = new double[] {0.0, 0.0, 0.0, 0.0};
        p1.gridMult = 10;
        p2.gridMult = 10;
        p1.plotOffset = 0;
        p2.plotOffset = 3; // moves displayed point to y=20 for centerY=50
        Vector<ScopePlot> visible = new Vector<ScopePlot>();
        visible.add(p1);
        visible.add(p2);

        int idx = ScopeInteractionController.findNearestPlotIndex(visible, 0, 4, 20, 0, 50);
        assertEquals(1, idx);
    }
}
