package com.lushprojects.circuitjs1.client.scope;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ScopeScalerTest {
    private static final double[] MULTA = {2.0, 2.5, 2.0};
    private static final double[] NICE = {1.0, 2.0, 2.5, 5.0, 10.0};

    @Test
    void autoScaleUsesPowerOfTwoGrowthAndLimit() {
        assertEquals(4.0, ScopeScaler.computeAutoScale(1.0, 3.2, false, null), 1e-12);
        assertEquals(5.0, ScopeScaler.computeAutoScale(1.0, 7.0, true, 5.0), 1e-12);
    }

    @Test
    void calcGridStepXUsesConfiguredProgression() {
        assertEquals(2e-6, ScopeScaler.calcGridStepX(1e-7, 20, MULTA), 1e-20);
    }

    @Test
    void calcMultiLhsAxisRangeRoundsToNiceTicks() {
        double[] min = new double[8];
        double[] max = new double[8];
        for (int i = 0; i < 8; i++) {
            min[i] = 0.12;
            max[i] = 1.93;
        }
        double[] axis = ScopeScaler.calcMultiLhsAxisRange(min, max, 8, 0, 8, false, 1.0, 5, NICE);
        assertArrayEquals(new double[] {0.0, 2.0, 0.5}, axis, 1e-12);
    }

    @Test
    void buildPlotScaleResultHandlesManualScale() {
        PlotScaleResult result = ScopeScaler.buildPlotScaleResult(
                true, false, true, false, false,
                0, 0, 5.0, 59, 10, 0.25, 20,
                200, 5, MULTA, null);

        assertEquals(0.0, result.gridMid, 1e-12);
        assertEquals(ScopeScaler.getGridMaxFromManualScale(10, 0.25), result.gridMax, 1e-12);
        assertEquals(0.25, result.gridStepY, 1e-12);
    }

    @Test
    void historyVisibleRangeUsesFullHistoryInAutoScaleTimeMode() {
        int[] range = ScopeScaler.calcHistoryVisibleIndexRange(true, 12, 200, 9.0, 0.1, 0.05);
        assertArrayEquals(new int[] {0, 12}, range);
    }

    @Test
    void historyVisibleRangeTracksFixedWindowInDrawFromZeroMode() {
        // Case 1: Not enough data to fill the screen yet
        // historySize=100, plotWidth=40, elapsedTime=15.0, timePerPixel=0.5, historySampleInterval=0.25
        // pixelsNeeded = 15.0/0.5 = 30, pixelsUsed = min(30, 40) = 30
        // windowStartTime = 0, windowEndTime = 30*0.5 = 15.0
        // startIndex = 0, endIndex = ceil(15.0/0.25) = 60
        int[] range = ScopeScaler.calcHistoryVisibleIndexRange(false, 100, 40, 15.0, 0.5, 0.25);
        assertArrayEquals(new int[] {0, 60}, range);

        // Case 2: Window slides when screen is full
        // historySize=200, plotWidth=20, elapsedTime=30.0, timePerPixel=1.0, historySampleInterval=0.5
        // pixelsNeeded = 30.0/1.0 = 30, pixelsUsed = min(30, 20) = 20
        // pixelsUsed >= plotWidth, so windowStartTime = max(0, 30.0 - 20*1.0) = 10.0
        // windowEndTime = 10.0 + 20*1.0 = 30.0
        // startIndex = 10.0/0.5 = 20, endIndex = ceil(30.0/0.5) = 60
        int[] slidingRange = ScopeScaler.calcHistoryVisibleIndexRange(false, 200, 20, 30.0, 1.0, 0.5);
        assertArrayEquals(new int[] {20, 60}, slidingRange);
    }

    @Test
    void minMaxAndMaxAbsRangeHelpersUseRequestedSlice() {
        double[] min = {-1.0, -2.0, 0.5, -0.25};
        double[] max = {1.2, 2.5, 3.0, 0.75};

        assertEquals(3.0, ScopeScaler.calcMaxAbsInRange(min, max, 1, 3), 1e-12);
        assertArrayEquals(new double[] {-2.0, 3.0}, ScopeScaler.calcMinMaxInRange(min, max, 1, 3), 1e-12);
        assertNull(ScopeScaler.calcMinMaxInRange(min, max, 2, 2));
    }
}
