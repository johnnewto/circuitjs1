package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.*;

class ExprTest {

    @Test
    void testArithmeticAndBuiltinFunctions() {
        Expr expr = parse("2 + 3*4 + sin(pi/2) + max(1, 5, 3) - min(8, 2)");
        ExprState state = new ExprState(0);

        double value = expr.evalFresh(state);
        assertEquals(18.0, value, 1e-12);
    }

    @Test
    void testParseErrorOnInvalidExpression() {
        ExprParser parser = new ExprParser("1 + * 2");
        parser.parseExpression();

        assertNotNull(parser.gotError());
        assertTrue(parser.gotError().length() > 0);
    }

    @Test
    void testDivisionByZeroReturnsZero() {
        Expr expr = parse("5 / 0");
        ExprState state = new ExprState(0);

        assertEquals(0.0, expr.evalFresh(state), 1e-12);
    }

    @Test
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
    void testIntegrateCommitBoundaryAcrossTimesteps() throws Exception {
        withTimeStep(0.1, new ThrowingRunnable() {
            @Override
            public void run() {
                Expr expr = parse("integrate(_a)");
                ExprState state = new ExprState(0);

                state.t = 0.0;
                state.values[0] = 2.0;
                assertEquals(0.2, expr.evalFresh(state), 1e-12);
                state.commitIntegration(0.1);
                assertEquals(0.2, state.lastIntOutput, 1e-12);

                state.t = 0.1;
                state.values[0] = 3.0;
                assertEquals(0.5, expr.evalFresh(state), 1e-12);
                state.commitIntegration(0.1);
                assertEquals(0.5, state.lastIntOutput, 1e-12);
            }
        });
    }

    @Test
    void testDiffUsesCommittedPreviousInput() throws Exception {
        withTimeStep(0.1, new ThrowingRunnable() {
            @Override
            public void run() {
                Expr expr = parse("diff(_a)");
                ExprState state = new ExprState(0);

                state.t = 0.0;
                state.values[0] = 5.0;
                assertEquals(0.0, expr.evalFresh(state), 1e-12);
                state.commitIntegration(0.1);
                assertTrue(state.diffInitialized);
                assertEquals(5.0, state.lastDiffInput, 1e-12);

                state.t = 0.1;
                state.values[0] = 8.0;
                assertEquals(30.0, expr.evalFresh(state), 1e-12);
                state.commitIntegration(0.1);
                assertEquals(8.0, state.lastDiffInput, 1e-12);
            }
        });
    }

    @Test
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

    private void withTimeStep(double timeStep, ThrowingRunnable runnable) throws Exception {
        CirSim original = CirSim.theSim;
        CirSim stub = newCirSimStub(timeStep);
        try {
            CirSim.theSim = stub;
            runnable.run();
        } finally {
            CirSim.theSim = original;
        }
    }

    private CirSim newCirSimStub(double timeStep) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        CirSim sim = (CirSim) unsafe.allocateInstance(CirSim.class);
        sim.timeStep = timeStep;
        return sim;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
