package com.lushprojects.circuitjs1.client;

final class CircuitMatrixOps {
    private CircuitMatrixOps() {
    }

    // factors a matrix into upper and lower triangular matrices by
    // gaussian elimination.  On entry, a[0..n-1][0..n-1] is the
    // matrix to be factored.  ipvt[] returns an integer vector of pivot
    // indices, used in the luSolve() routine.
    // Returns -1 on success, or the problematic row index on failure (singular matrix)
    static int luFactor(double[][] a, int n, int[] ipvt) {
        int badRow = LUSolver.factor(a, n, ipvt);
        if (badRow >= 0) {
            CirSim.console("didn't avoid zero at row " + badRow);
            CirSim.console("  Non-zero entries in column " + badRow + ":");
            for (int dbg = 0; dbg < n; dbg++) {
                if (a[dbg][badRow] != 0.0) {
                    CirSim.console("    row " + dbg + ": " + a[dbg][badRow]);
                }
            }
            CirSim.console("  Non-zero entries in row " + badRow + ":");
            for (int dbg = 0; dbg < n; dbg++) {
                if (a[badRow][dbg] != 0.0) {
                    CirSim.console("    col " + dbg + ": " + a[badRow][dbg]);
                }
            }
        }
        return badRow;
    }

    // Solves the set of n linear equations using a LU factorization
    // previously performed by luFactor().  On input, b[0..n-1] is the right
    // hand side of the equations, and on output, contains the solution.
    static void luSolve(double[][] a, int n, int[] ipvt, double[] b) {
        LUSolver.solve(a, n, ipvt, b);
    }

    static void invertMatrix(double[][] a, int n) {
        int[] ipvt = new int[n];
        luFactor(a, n, ipvt);
        double[] b = new double[n];
        double[][] inva = new double[n][n];

        // solve for each column of identity matrix
        for (int i = 0; i != n; i++) {
            for (int j = 0; j != n; j++)
                b[j] = 0;
            b[i] = 1;
            luSolve(a, n, ipvt, b);
            for (int j = 0; j != n; j++)
                inva[j][i] = b[j];
        }

        // return in original matrix
        for (int i = 0; i != n; i++)
            for (int j = 0; j != n; j++)
                a[i][j] = inva[i][j];
    }
}
