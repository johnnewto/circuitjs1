package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EquationTableSemanticsTest {

    @Test
    void testRowModeSemantics() {
        assertTrue(EquationTableSemantics.usesVoltageSource(EquationTableElm.RowOutputMode.VOLTAGE_MODE));
        assertFalse(EquationTableSemantics.usesVoltageSource(EquationTableElm.RowOutputMode.FLOW_MODE));
        assertFalse(EquationTableSemantics.usesVoltageSource(EquationTableElm.RowOutputMode.PARAM_MODE));

        assertTrue(EquationTableSemantics.isFlowMode(EquationTableElm.RowOutputMode.FLOW_MODE));
        assertTrue(EquationTableSemantics.isParamMode(EquationTableElm.RowOutputMode.PARAM_MODE));
        assertFalse(EquationTableSemantics.isParamMode(EquationTableElm.RowOutputMode.VOLTAGE_MODE));
    }

    @Test
    void testConvergenceLimitRelaxesWithSubiterations() {
        double base = 0.001;
        double output = 100.0;
        double last = 99.0;

        double early = EquationTableSemantics.convergenceLimit(base, 0, false, output, last);
        double mid = EquationTableSemantics.convergenceLimit(base, 5, false, output, last);
        double late = EquationTableSemantics.convergenceLimit(base, 20, false, output, last);

        assertTrue(mid > early);
        assertTrue(late > mid);
    }

    @Test
    void testDiffRowsSkipEarlyConvergenceChecks() {
        assertTrue(EquationTableSemantics.shouldSkipConvergenceCheck(true, 0));
        assertTrue(EquationTableSemantics.shouldSkipConvergenceCheck(true, 4));
        assertFalse(EquationTableSemantics.shouldSkipConvergenceCheck(true, 5));
        assertFalse(EquationTableSemantics.shouldSkipConvergenceCheck(false, 0));
    }

    @Test
    void testShouldMarkUnconvergedHonorsSkipRule() {
        boolean skipped = EquationTableSemantics.shouldMarkUnconverged(
                10.0, 0.0, 1e-12, true, 0);
        assertFalse(skipped, "diff() rows should skip convergence checks in early subiterations");

        boolean marked = EquationTableSemantics.shouldMarkUnconverged(
                10.0, 0.0, 0.1, false, 10);
        assertTrue(marked, "large change over limit should mark unconverged");

        boolean stable = EquationTableSemantics.shouldMarkUnconverged(
                1.00001, 1.0, 0.01, false, 10);
        assertFalse(stable, "small change within limit should not mark unconverged");
    }

    @Test
    void testConvergenceLimitUsesMagnitudeFloor() {
        double limit = EquationTableSemantics.convergenceLimit(0.001, 0, false, 0.0, 0.0);
        assertEquals(0.001, limit, 1e-12);
    }
}
