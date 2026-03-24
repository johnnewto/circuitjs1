/*
    Copyright (C) Paul Falstad and Iain Sharp

    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.core;

/**
 * Pure-Java LU factorization/solve helper extracted from {@link CirSim}.
 */
public final class LUSolver {

    private LUSolver() {
    }

    /**
     * Factor matrix {@code a} into LU form with partial pivoting.
     *
     * @param a matrix (modified in-place)
     * @param n matrix size
     * @param ipvt pivot index output
     * @return -1 on success, or row index on singular failure
     */
    public static int factor(double[][] a, int n, int[] ipvt) {
        int i;
        int j;
        int k;

        for (i = 0; i != n; i++) {
            boolean rowAllZeros = true;
            for (j = 0; j != n; j++) {
                if (a[i][j] != 0) {
                    rowAllZeros = false;
                    break;
                }
            }
            if (rowAllZeros) {
                return i;
            }
        }

        for (j = 0; j != n; j++) {
            for (i = 0; i != j; i++) {
                double q = a[i][j];
                for (k = 0; k != i; k++) {
                    q -= a[i][k] * a[k][j];
                }
                a[i][j] = q;
            }

            double largest = 0;
            int largestRow = -1;
            for (i = j; i != n; i++) {
                double q = a[i][j];
                for (k = 0; k != j; k++) {
                    q -= a[i][k] * a[k][j];
                }
                a[i][j] = q;
                double x = Math.abs(q);
                if (x >= largest) {
                    largest = x;
                    largestRow = i;
                }
            }

            if (j != largestRow) {
                if (largestRow == -1) {
                    return j;
                }
                for (k = 0; k != n; k++) {
                    double x = a[largestRow][k];
                    a[largestRow][k] = a[j][k];
                    a[j][k] = x;
                }
            }

            ipvt[j] = largestRow;

            if (a[j][j] == 0.0) {
                return j;
            }

            if (j != n - 1) {
                double mult = 1.0 / a[j][j];
                for (i = j + 1; i != n; i++) {
                    a[i][j] *= mult;
                }
            }
        }

        return -1;
    }

    /**
     * Solve linear system using LU decomposition produced by {@link #factor(double[][], int, int[])}.
     *
     * @param a LU matrix from {@code factor()} (not modified)
     * @param n matrix size
     * @param ipvt pivot vector from {@code factor()}
     * @param b right-side vector, overwritten with solution
     */
    public static void solve(double[][] a, int n, int[] ipvt, double[] b) {
        int i;

        for (i = 0; i != n; i++) {
            int row = ipvt[i];

            double swap = b[row];
            b[row] = b[i];
            b[i] = swap;
            if (swap != 0) {
                break;
            }
        }

        int bi = i++;
        for (; i < n; i++) {
            int row = ipvt[i];
            int j;
            double tot = b[row];

            b[row] = b[i];
            for (j = bi; j < i; j++) {
                tot -= a[i][j] * b[j];
            }
            b[i] = tot;
        }

        for (i = n - 1; i >= 0; i--) {
            double tot = b[i];

            int j;
            for (j = i + 1; j != n; j++) {
                tot -= a[i][j] * b[j];
            }
            b[i] = tot / a[i][i];
        }
    }
}
