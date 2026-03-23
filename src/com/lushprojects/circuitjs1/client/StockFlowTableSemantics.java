/*
    Copyright (C) Paul Falstad and Iain Sharp

    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * Pure-Java helpers for stock-flow integration and convergence semantics.
 */
public final class StockFlowTableSemantics {

    private StockFlowTableSemantics() {
    }

    public static double convergenceLimit(int subIterations, double[] integratedValues, double[] lastColumnSums) {
        double relativeTolerance;
        if (subIterations < 10) {
            relativeTolerance = 0.001;
        } else if (subIterations < 100) {
            relativeTolerance = 0.01;
        } else {
            relativeTolerance = 0.1;
        }

        double maxMagnitude = 1.0;
        if (integratedValues != null) {
            for (int i = 0; i < integratedValues.length; i++) {
                maxMagnitude = Math.max(maxMagnitude, Math.abs(integratedValues[i]));
            }
        }
        if (lastColumnSums != null) {
            for (int i = 0; i < lastColumnSums.length; i++) {
                maxMagnitude = Math.max(maxMagnitude, Math.abs(lastColumnSums[i]));
            }
        }

        return maxMagnitude * relativeTolerance;
    }

    public static boolean shouldMarkUnconverged(double newColumnSum, double oldColumnSum, double convergeLimit) {
        return Math.abs(newColumnSum - oldColumnSum) > convergeLimit;
    }

    public static double integratedValue(double t, double initialValue, double lastOutput, double timestep, double columnSum) {
        if (t == 0.0) {
            return initialValue;
        }
        return lastOutput + timestep * columnSum;
    }

    public static double committedIntegrationState(double integratedValue) {
        return integratedValue;
    }
}
