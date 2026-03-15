package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StockFlowTableSemantics — convergence and integration helpers")
class StockFlowTableSemanticsTest {

    @Test
    @DisplayName("convergence limit grows with subiteration count and magnitude")
    void testConvergenceLimitScalesWithMagnitudeAndIterations() {
        double[] integrated = {1000.0, -200.0};
        double[] sums = {50.0, -25.0};

        double early = StockFlowTableSemantics.convergenceLimit(1, integrated, sums);
        double mid = StockFlowTableSemantics.convergenceLimit(50, integrated, sums);
        double late = StockFlowTableSemantics.convergenceLimit(150, integrated, sums);

        assertTrue(mid > early);
        assertTrue(late > mid);
    }

    @Test
    @DisplayName("shouldMarkUnconverged triggers on large delta, not on small")
    void testShouldMarkUnconvergedByColumnSumDelta() {
        assertTrue(StockFlowTableSemantics.shouldMarkUnconverged(10.0, 0.0, 1.0));
        assertFalse(StockFlowTableSemantics.shouldMarkUnconverged(1.0001, 1.0, 0.01));
    }

    @Test
    @DisplayName("integratedValue returns initialValue when t=0")
    void testIntegrationUsesInitialValueAtT0() {
        double result = StockFlowTableSemantics.integratedValue(0.0, 42.0, 5.0, 0.1, 100.0);
        assertEquals(42.0, result, 1e-12);
    }

    @Test
    @DisplayName("integratedValue accumulates flow × dt after t=0")
    void testIntegrationAccumulatesAfterT0() {
        double result = StockFlowTableSemantics.integratedValue(1.0, 0.0, 10.0, 0.5, 4.0);
        assertEquals(12.0, result, 1e-12);
    }

    @Test
    @DisplayName("committedIntegrationState returns the given integrated value")
    void testCommitUsesIntegratedValue() {
        assertEquals(12.34, StockFlowTableSemantics.committedIntegrationState(12.34), 1e-12);
    }
}
