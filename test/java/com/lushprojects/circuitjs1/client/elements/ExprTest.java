package com.lushprojects.circuitjs1.client.elements;

import com.lushprojects.circuitjs1.client.elements.economics.ComputedValues;
import com.lushprojects.circuitjs1.client.io.LookupTableRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ComputedValues")
@DisplayName("Expr — parser and evaluator")
class ExprTest {

    @BeforeEach
    void setUp() {
        ComputedValues.resetForTesting();
    }

    @Test
    @DisplayName("arithmetic operators and built-in functions produce correct values")
    void testArithmeticAndBuiltinFunctions() {
        Expr expr = parse("2 + 3*4 + sin(pi/2) + max(1, 5, 3) - min(8, 2)");
        ExprState state = new ExprState(0);

        double value = expr.evalFresh(state);
        assertEquals(18.0, value, 1e-12);
    }

    @Test
    @DisplayName("uppercase PI parses as a variable name while lowercase pi remains the math constant")
    void testUppercasePiRemainsVariableName() {
        Expr uppercaseVariableExpr = parse("PI");
        Expr lowercaseConstantExpr = parse("pi");
        ExprState state = new ExprState(0);

        assertEquals(Expr.E_NODE_REF, uppercaseVariableExpr.type);
        assertEquals("PI", uppercaseVariableExpr.nodeName);
        assertEquals(Expr.E_VAL, lowercaseConstantExpr.type);
        assertEquals(Math.PI, lowercaseConstantExpr.evalFresh(state), 1e-12);
    }

    @Test
    @DisplayName("uppercase T parses as a variable name while lowercase t remains simulation time")
    void testUppercaseTRemainsVariableName() {
        Expr uppercaseVariableExpr = parse("T");
        Expr lowercaseTimeExpr = parse("t");
        ExprState state = new ExprState(0);
        state.t = 7.5;

        assertEquals(Expr.E_NODE_REF, uppercaseVariableExpr.type);
        assertEquals("T", uppercaseVariableExpr.nodeName);
        assertEquals(Expr.E_T, lowercaseTimeExpr.type);
        assertEquals(7.5, lowercaseTimeExpr.evalFresh(state), 1e-12);
    }

    @Test
    @DisplayName("invalid expression leaves gotError() non-null")
    void testParseErrorOnInvalidExpression() {
        ExprParser parser = new ExprParser("1 + * 2");
        parser.parseExpression();

        assertNotNull(parser.gotError());
        assertTrue(parser.gotError().length() > 0);
    }

    @Test
    @DisplayName("division by zero returns 0.0 (protected)")
    void testDivisionByZeroReturnsZero() {
        Expr expr = parse("5 / 0");
        ExprState state = new ExprState(0);

        assertEquals(0.0, expr.evalFresh(state), 1e-12);
    }

    @Test
    @DisplayName("log(0) returns -Infinity; sqrt(-1) returns NaN")
    void testLogAndSqrtDomainBehavior() {
        Expr logExpr = parse("log(0)");
        Expr sqrtExpr = parse("sqrt(-1)");
        ExprState state = new ExprState(0);

        double logValue = logExpr.evalFresh(state);
        double sqrtValue = sqrtExpr.evalFresh(state);

        assertTrue(Double.isInfinite(logValue) && logValue < 0);
        assertTrue(Double.isNaN(sqrtValue));
    }

    @Test
    @DisplayName("integrate() accumulates using committed lastIntOutput across timesteps")
    void testIntegrateCommitBoundaryAcrossTimesteps() {
        Expr expr = parse("integrate(_a)");
        ExprState state = new ExprState(0);

        state.t = 0.0;
        state.values[0] = 2.0;
        assertEquals(0.2, expr.evalFresh(state, 0.1), 1e-12);
        state.commitIntegration(0.1);
        assertEquals(0.2, state.lastIntOutput, 1e-12);

        state.t = 0.1;
        state.values[0] = 3.0;
        assertEquals(0.5, expr.evalFresh(state, 0.1), 1e-12);
        state.commitIntegration(0.1);
        assertEquals(0.5, state.lastIntOutput, 1e-12);
    }

    @Test
    @DisplayName("integrate() models dp/dt = 0.1*p with p(0)=100 and reaches ~110.52 at t=1s")
    void testIntegrateExponentialGrowthAtOneSecond() {
        Expr expr = parse("integrate(0.1 * _a)");
        ExprState state = new ExprState(0);

        double dt = 0.001;
        int steps = 1000;

        state.lastIntOutput = 100.0;
        state.t = 0.0;

        for (int step = 0; step < steps; step++) {
            state.values[0] = state.lastIntOutput;
            state.t = step * dt;
            expr.evalFresh(state, dt);
            state.commitIntegration(dt);
        }

        double pAtOneSecond = state.lastIntOutput;
        double exact = 100.0 * Math.exp(0.1);

        assertEquals(exact, pAtOneSecond, 0.01);
        assertEquals(110.52, pAtOneSecond, 0.01);
    }

    @Test
    @DisplayName("integrate() with dt=1 performs one Euler step and gives 110.0 at t=1s")
    void testIntegrateSingleStepEulerAtOneSecond() {
        Expr expr = parse("integrate(0.1 * _a)");
        ExprState state = new ExprState(0);

        state.lastIntOutput = 100.0;
        state.values[0] = state.lastIntOutput;
        state.t = 0.0;

        expr.evalFresh(state, 1.0);
        state.commitIntegration(1.0);

        double pAtOneSecond = state.lastIntOutput;
        double exact = 100.0 * Math.exp(0.1);

        assertEquals(110.0, pAtOneSecond, 1e-12);
        assertTrue(Math.abs(exact - pAtOneSecond) > 0.5);
    }

    @Test
    @DisplayName("pwlx() linearly extrapolates outside endpoint ranges")
    void testPwlxLinearExtrapolationOutsideRange() {
        Expr expr = parse("pwlx(_a, 0, 0, 1, 2, 2, 4)");
        ExprState state = new ExprState(0);

        state.values[0] = -1.0;
        assertEquals(-2.0, expr.evalFresh(state), 1e-12);

        state.values[0] = 3.0;
        assertEquals(6.0, expr.evalFresh(state), 1e-12);
    }

    @Test
    @DisplayName("lookup(name, x) clamps to endpoints by default")
    void testLookupClampsByDefault() {
        LookupTableRegistry.clear();
        java.util.ArrayList<Double> xs = new java.util.ArrayList<Double>();
        java.util.ArrayList<Double> ys = new java.util.ArrayList<Double>();
        xs.add(0.0); ys.add(0.0);
        xs.add(1.0); ys.add(2.0);
        xs.add(2.0); ys.add(4.0);
        LookupTableRegistry.registerGlobal("BRMM", xs, ys);

        Expr expr = parse("lookup(BRMM, _a)");
        ExprState state = new ExprState(0);

        state.values[0] = -1.0;
        assertEquals(0.0, expr.evalFresh(state), 1e-12);

        state.values[0] = 3.0;
        assertEquals(4.0, expr.evalFresh(state), 1e-12);
    }

    @Test
    @DisplayName("lookup(name, x) extrapolates when lookup mode is pwlx")
    void testLookupExtrapolatesWhenModeIsPwlx() {
        LookupTableRegistry.clear();
        java.util.ArrayList<Double> xs = new java.util.ArrayList<Double>();
        java.util.ArrayList<Double> ys = new java.util.ArrayList<Double>();
        xs.add(0.0); ys.add(0.0);
        xs.add(1.0); ys.add(2.0);
        xs.add(2.0); ys.add(4.0);
        LookupTableRegistry.registerGlobal("BRMM", xs, ys);

        Expr expr = parse("lookup(BRMM, _a, 0)");
        ExprState state = new ExprState(0);

        state.values[0] = -1.0;
        assertEquals(-2.0, expr.evalFresh(state), 1e-12);

        state.values[0] = 3.0;
        assertEquals(6.0, expr.evalFresh(state), 1e-12);
    }

    @Test
    @DisplayName("diff() uses committed previous input to compute derivative")
    void testDiffUsesCommittedPreviousInput() {
        Expr expr = parse("diff(_a)");
        ExprState state = new ExprState(0);

        state.t = 0.0;
        state.values[0] = 5.0;
        assertEquals(0.0, expr.evalFresh(state, 0.1), 1e-12);
        state.commitIntegration(0.1);
        assertTrue(state.diffInitialized);
        assertEquals(5.0, state.lastDiffInput, 1e-12);

        state.t = 0.1;
        state.values[0] = 8.0;
        assertEquals(30.0, expr.evalFresh(state, 0.1), 1e-12);
        state.commitIntegration(0.1);
        assertEquals(8.0, state.lastDiffInput, 1e-12);
    }

    @Test
    @DisplayName("d(name) parses to current minus last(name)")
    void testDifferenceAliasUsesPreviousConvergedValue() {
        Expr expr = parse("d(INV)");

        assertEquals(Expr.E_SUB, expr.type);
        assertNotNull(expr.children);
        assertEquals(2, expr.children.size());

        Expr current = expr.children.get(0);
        Expr previous = expr.children.get(1);

        assertEquals(Expr.E_NODE_REF, current.type);
        assertEquals("INV", current.nodeName);
        assertEquals(Expr.E_LAST, previous.type);
        assertNotNull(previous.children);
        assertEquals(1, previous.children.size());
        assertEquals(Expr.E_NODE_REF, previous.children.get(0).type);
        assertEquals("INV", previous.children.get(0).nodeName);
    }

    @Test
    @DisplayName("last(name) prefers lagged flow value over lagged base value when both exist")
    void testLastPrefersLaggedFlowOverLaggedBaseValue() {
        String flowKey = ComputedValues.getFlowComputedKeyForName("Ld");
        assertNotNull(flowKey);

        ComputedValues.setComputedValueDirect("Ld", 200.0);
        ComputedValues.setComputedValueDirect(flowKey, 5.0);
        ComputedValues.commitConvergedValues();

        Expr expr = parse("last(Ld)");
        ExprState state = new ExprState(0);

        assertEquals(5.0, expr.evalFresh(state), 1e-12);
    }

    @Test
    @DisplayName("last(name) in converged display context uses the previous converged timestep")
    void testLastUsesPreviousConvergedValueInDisplayContext() {
        ComputedValues.setComputedValueDirect("Ld", 5.0);
        ComputedValues.commitConvergedValues();

        ComputedValues.setComputedValueDirect("Ld", 10.0);
        ComputedValues.commitConvergedValues();

        Expr expr = parse("last(Ld)");
        ExprState state = new ExprState(0);

        assertEquals(5.0, expr.eval(state, Expr.getEvaluationContext(true)), 1e-12);
        assertEquals(10.0, expr.evalFresh(state), 1e-12,
                "Non-display evaluation should still see the latest converged value as the previous simulation timestep");
    }

    @Test
    @DisplayName("ExprState.reset() zeroes all integration and diff fields")
    void testExprStateResetClearsIntegrationAndDiffState() {
        ExprState state = new ExprState(0);
        state.lastOutput = 10.0;
        state.lastDiffInput = 2.0;
        state.lastIntOutput = 3.0;
        state.pendingIntInput = 4.0;
        state.pendingDiffInput = 5.0;
        state.diffInitialized = true;

        state.reset();

        assertEquals(0.0, state.lastOutput, 1e-12);
        assertEquals(0.0, state.lastDiffInput, 1e-12);
        assertEquals(0.0, state.lastIntOutput, 1e-12);
        assertEquals(0.0, state.pendingIntInput, 1e-12);
        assertEquals(0.0, state.pendingDiffInput, 1e-12);
        assertFalse(state.diffInitialized);
    }

    private Expr parse(String text) {
        ExprParser parser = new ExprParser(text);
        Expr expression = parser.parseExpression();
        assertNull(parser.gotError(), "Parse error: " + parser.gotError());
        return expression;
    }
}
