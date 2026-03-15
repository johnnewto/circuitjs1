package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("LUSolver — LU factorization and back-substitution")
class LUSolverTest {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("2×2 system solves to known exact solution")
    void testSolve2x2KnownSolution() {
        double[][] a = {
                {2, 1},
                {5, 7}
        };
        int[] ipvt = new int[2];
        int badRow = LUSolver.factor(a, 2, ipvt);
        assertEquals(-1, badRow, "Factorization should succeed");

        double[] b = {11, 13};
        LUSolver.solve(a, 2, ipvt, b);

        assertEquals(64.0 / 9.0, b[0], EPS);
        assertEquals(-29.0 / 9.0, b[1], EPS);
    }

    @Test
    @DisplayName("zero pivot triggers row exchange and still solves correctly")
    void testSolveRequiresPivoting() {
        double[][] a = {
                {0, 2},
                {1, 3}
        };
        int[] ipvt = new int[2];
        int badRow = LUSolver.factor(a, 2, ipvt);
        assertEquals(-1, badRow, "Factorization with pivoting should succeed");

        double[] b = {4, 5};
        LUSolver.solve(a, 2, ipvt, b);

        assertEquals(-1.0, b[0], EPS);
        assertEquals(2.0, b[1], EPS);
    }

    @Test
    @DisplayName("all-zero row is detected as singular")
    void testSingularAllZeroRowDetected() {
        double[][] a = {
                {1, 2},
                {0, 0}
        };
        int[] ipvt = new int[2];
        int badRow = LUSolver.factor(a, 2, ipvt);

        assertEquals(1, badRow, "All-zero row should be detected as singular");
    }

    @Test
    @DisplayName("linearly dependent rows are detected as singular")
    void testSingularDependentRowsDetected() {
        double[][] a = {
                {1, 2},
                {2, 4}
        };
        int[] ipvt = new int[2];
        int badRow = LUSolver.factor(a, 2, ipvt);

        assertEquals(1, badRow, "Dependent rows should produce a singular pivot row");
    }

    @Test
    @DisplayName("3×3 system solves to known exact solution")
    void testSolve3x3KnownSolution() {
        double[][] a = {
                {3, 2, -1},
                {2, -2, 4},
                {-1, 0.5, -1}
        };
        int[] ipvt = new int[3];
        int badRow = LUSolver.factor(a, 3, ipvt);
        assertEquals(-1, badRow, "Factorization should succeed");

        double[] b = {1, -2, 0};
        LUSolver.solve(a, 3, ipvt, b);

        assertEquals(1.0, b[0], EPS);
        assertEquals(-2.0, b[1], EPS);
        assertEquals(-2.0, b[2], EPS);
    }

    @Test
    @DisplayName("near-singular matrix factors and solves within tolerance")
    void testNearSingular2x2RemainsNumericallyReasonable() {
        double[][] a = {
                {1.0, 1.0},
                {1.0, 1.0 + 1e-12}
        };
        int[] ipvt = new int[2];
        int badRow = LUSolver.factor(a, 2, ipvt);
        assertEquals(-1, badRow, "Near-singular matrix should still factor without singular detection");

        double[] b = {2.0, 2.0 + 1e-12};
        LUSolver.solve(a, 2, ipvt, b);

        assertEquals(1.0, b[0], 1e-6);
        assertEquals(1.0, b[1], 1e-6);
    }
}
