/*
    Copyright (C) Paul Falstad and Iain Sharp

    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.elements.economics;

import com.lushprojects.circuitjs1.client.elements.Expr;
import com.lushprojects.circuitjs1.client.elements.ExprParser;
import com.lushprojects.circuitjs1.client.elements.ExprState;

import com.lushprojects.circuitjs1.client.*;
/**
 * Pure-Java semantics helpers for EquationTable row modes and convergence checks.
 */
public final class EquationTableSemantics {

    private EquationTableSemantics() {
    }

    public static boolean usesVoltageSource(EquationTableElm.RowOutputMode mode) {
        return mode == EquationTableElm.RowOutputMode.VOLTAGE_MODE;
    }

    public static boolean isFlowMode(EquationTableElm.RowOutputMode mode) {
        return mode == EquationTableElm.RowOutputMode.FLOW_MODE;
    }

    public static boolean isParamMode(EquationTableElm.RowOutputMode mode) {
        return mode == EquationTableElm.RowOutputMode.PARAM_MODE;
    }

    public static boolean rowForcesNonLinear(EquationTableElm.RowOutputMode mode, boolean isCommentRow) {
        if (isCommentRow) {
            return false;
        }
        if (isFlowMode(mode)) {
            return true;
        }
        return true;
    }

    /**
     * Calculate the convergence tolerance for equation table row values.
     * 
     * Uses combined absolute + relative tolerance:
     *   tolerance = max(absTol, relTol * maxMagnitude)
     * 
     * This ensures from the world2 model that:
     * - Small values (e.g., CIAF ~0.2): absolute tolerance dominates
     * - Large values (e.g., NR ~9e11): relative tolerance dominates
     * 
     * Adaptive relaxation is applied when convergence is slow to prevent
     * infinite iteration loops.
     * 
     * @param baseTolerance      Base tolerance from @init (e.g., 1e-8)
     * @param subIterations      Current subiteration count
     * @param hasDiffExpr        Whether the expression contains diff()
     * @param outputValue        Current computed value
     * @param lastOutputValue    Previous iteration's value
     * @return Combined tolerance threshold
     */
    public static double convergenceLimit(double baseTolerance,
                                   int subIterations,
                                   boolean hasDiffExpr,
                                   double outputValue,
                                   double lastOutputValue) {
        // Absolute tolerance - floor for near-zero values
        double absTol = baseTolerance;
        
        // Relative tolerance - scales with value magnitude
        double relTol = baseTolerance;
        
        // Adaptive relaxation for slow convergence
        if (subIterations >= 50) {
            relTol *= 100;
            absTol *= 100;
        } else if (subIterations >= 10) {
            relTol *= 10;
            absTol *= 10;
        } else if (subIterations >= 5) {
            relTol *= 2;
            absTol *= 2;
        }
        
        // diff() expressions need looser tolerance due to numerical sensitivity
        if (hasDiffExpr) {
            relTol *= 10;
            absTol *= 10;
        }
        
        // Maximum magnitude of current and previous values
        double maxMagnitude = Math.max(Math.abs(outputValue), Math.abs(lastOutputValue));
        
        // Combined criterion: max(absTol, relTol * magnitude)
        // - For small values: absTol dominates (prevents div-by-zero issues)
        // - For large values: relTol * magnitude dominates (scales appropriately)
        return Math.max(absTol, relTol * maxMagnitude);
    }

    public static boolean shouldSkipConvergenceCheck(boolean hasDiffExpr, int subIterations) {
        return hasDiffExpr && subIterations < 5;
    }

    public static boolean shouldMarkUnconverged(double newValue,
                                         double lastValue,
                                         double convergeLimit,
                                         boolean hasDiffExpr,
                                         int subIterations) {
        if (shouldSkipConvergenceCheck(hasDiffExpr, subIterations)) {
            return false;
        }
        return Math.abs(newValue - lastValue) > convergeLimit;
    }

    public static double computeVoltageRowValue(Expr compiledExpr, ExprState state) {
        if (compiledExpr == null) {
            return 0;
        }
        return compiledExpr.eval(state);
    }

    public static double computeFlowRowValue(Expr compiledExpr,
                                      ExprState state,
                                      double[] volts,
                                      int sourceNodeIdx,
                                      int targetNodeIdx,
                                      double shuntResistance) {
        if (compiledExpr == null) {
            return 0;
        }
        return compiledExpr.eval(state);
    }

    public static double computeParamRowValue(Expr compiledExpr, ExprState state) {
        if (compiledExpr == null) {
            return 0;
        }
        return compiledExpr.eval(state);
    }
}
