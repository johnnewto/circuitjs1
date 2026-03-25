package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
