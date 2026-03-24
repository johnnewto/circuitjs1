package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.economics.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("EquationTableSemantics — row-mode helpers and convergence logic")
class EquationTableSemanticsTest {

    private Expr parse(String text) {
        ExprParser parser = new ExprParser(text);
        Expr expression = parser.parseExpression();
        assertNull(parser.gotError(), "Parse error: " + parser.gotError());
        return expression;
    }

    @Test
    @DisplayName("VOLTAGE uses voltage source; FLOW and PARAM do not")
    void testRowModeSemantics() {
        assertTrue(EquationTableSemantics.usesVoltageSource(EquationTableElm.RowOutputMode.VOLTAGE_MODE));
        assertFalse(EquationTableSemantics.usesVoltageSource(EquationTableElm.RowOutputMode.FLOW_MODE));
        assertFalse(EquationTableSemantics.usesVoltageSource(EquationTableElm.RowOutputMode.PARAM_MODE));

        assertTrue(EquationTableSemantics.isFlowMode(EquationTableElm.RowOutputMode.FLOW_MODE));
        assertTrue(EquationTableSemantics.isParamMode(EquationTableElm.RowOutputMode.PARAM_MODE));
        assertFalse(EquationTableSemantics.isParamMode(EquationTableElm.RowOutputMode.VOLTAGE_MODE));
    }

    @Test
    @DisplayName("convergence limit grows with subiteration count")
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
    @DisplayName("diff() rows skip convergence check for first 4 subiterations")
    void testDiffRowsSkipEarlyConvergenceChecks() {
        assertTrue(EquationTableSemantics.shouldSkipConvergenceCheck(true, 0));
        assertTrue(EquationTableSemantics.shouldSkipConvergenceCheck(true, 4));
        assertFalse(EquationTableSemantics.shouldSkipConvergenceCheck(true, 5));
        assertFalse(EquationTableSemantics.shouldSkipConvergenceCheck(false, 0));
    }

    @Test
    @DisplayName("shouldMarkUnconverged respects diff skip rule and magnitude threshold")
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
    @DisplayName("convergence limit uses absolute floor when magnitude is zero")
    void testConvergenceLimitUsesMagnitudeFloor() {
        double limit = EquationTableSemantics.convergenceLimit(0.001, 0, false, 0.0, 0.0);
        assertEquals(0.001, limit, 1e-12);
    }

    @Test
    @DisplayName("computeVoltageRowValue evaluates expression against ExprState")
    void testComputeVoltageRowValue() {
        Expr expr = parse("_a + 1");
        ExprState state = new ExprState(0);
        state.values[0] = 2.5;

        double value = EquationTableSemantics.computeVoltageRowValue(expr, state);
        assertEquals(3.5, value, 1e-12);
    }

    @Test
    @DisplayName("computeFlowRowValue scales expression by shunt resistance")
    void testComputeFlowRowValue() {
        Expr expr = parse("_a*2");
        ExprState state = new ExprState(0);
        state.values[0] = 4.0;

        double value = EquationTableSemantics.computeFlowRowValue(expr, state, new double[0], 0, 1, 1.0);
        assertEquals(8.0, value, 1e-12);
    }

    @Test
    @DisplayName("computeParamRowValue evaluates expression for PARAM mode")
    void testComputeParamRowValue() {
        Expr expr = parse("max(_a, 3)");
        ExprState state = new ExprState(0);
        state.values[0] = 1.0;

        double value = EquationTableSemantics.computeParamRowValue(expr, state);
        assertEquals(3.0, value, 1e-12);
    }
}
