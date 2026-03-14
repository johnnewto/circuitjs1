/*
    Copyright (C) Paul Falstad and Iain Sharp

    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * Pure-Java semantics helpers for EquationTable row modes and convergence checks.
 */
final class EquationTableSemantics {

    private EquationTableSemantics() {
    }

    static boolean usesVoltageSource(EquationTableElm.RowOutputMode mode) {
        return mode == EquationTableElm.RowOutputMode.VOLTAGE_MODE;
    }

    static boolean isFlowMode(EquationTableElm.RowOutputMode mode) {
        return mode == EquationTableElm.RowOutputMode.FLOW_MODE;
    }

    static boolean isParamMode(EquationTableElm.RowOutputMode mode) {
        return mode == EquationTableElm.RowOutputMode.PARAM_MODE;
    }

    static boolean rowForcesNonLinear(EquationTableElm.RowOutputMode mode, boolean isCommentRow) {
        if (isCommentRow) {
            return false;
        }
        if (isFlowMode(mode)) {
            return true;
        }
        return true;
    }

    static double convergenceLimit(double baseTolerance,
                                   int subIterations,
                                   boolean hasDiffExpr,
                                   double outputValue,
                                   double lastOutputValue) {
        double relativeTolerance;
        if (subIterations < 3) {
            relativeTolerance = baseTolerance;
        } else if (subIterations < 10) {
            relativeTolerance = baseTolerance * 10;
        } else if (subIterations < 50) {
            relativeTolerance = baseTolerance * 50;
        } else {
            relativeTolerance = baseTolerance * 100;
        }

        if (hasDiffExpr) {
            relativeTolerance *= 10;
        }

        double maxMagnitude = Math.max(1.0, Math.abs(outputValue));
        maxMagnitude = Math.max(maxMagnitude, Math.abs(lastOutputValue));

        return maxMagnitude * relativeTolerance;
    }

    static boolean shouldSkipConvergenceCheck(boolean hasDiffExpr, int subIterations) {
        return hasDiffExpr && subIterations < 5;
    }

    static boolean shouldMarkUnconverged(double newValue,
                                         double lastValue,
                                         double convergeLimit,
                                         boolean hasDiffExpr,
                                         int subIterations) {
        if (shouldSkipConvergenceCheck(hasDiffExpr, subIterations)) {
            return false;
        }
        return Math.abs(newValue - lastValue) > convergeLimit;
    }
}
