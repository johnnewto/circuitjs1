package com.lushprojects.circuitjs1.client.core;

import com.lushprojects.circuitjs1.client.RowInfo;

public final class SolverMatrixState {
    // The circuit is solved by: circuitMatrix x nodeVoltages = circuitRightSide
    public double[][] circuitMatrix;
    public double[] circuitRightSide;
    public double[] nodeVoltages;
    public double[] lastNodeVoltages;

    // Matrix snapshots and row metadata used for simplification and nonlinear iterations.
    public double[][] origMatrix;
    public double[] origRightSide;
    public RowInfo[] circuitRowInfo;
    public int[] circuitPermute;

    public boolean circuitNonLinear;
    public boolean circuitNeedsMap;
    public int circuitMatrixSize;
    public int circuitMatrixFullSize;
}
