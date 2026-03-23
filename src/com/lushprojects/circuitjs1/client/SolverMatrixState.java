package com.lushprojects.circuitjs1.client;

final class SolverMatrixState {
    // The circuit is solved by: circuitMatrix x nodeVoltages = circuitRightSide
    double[][] circuitMatrix;
    double[] circuitRightSide;
    double[] nodeVoltages;
    double[] lastNodeVoltages;

    // Matrix snapshots and row metadata used for simplification and nonlinear iterations.
    double[][] origMatrix;
    double[] origRightSide;
    RowInfo[] circuitRowInfo;
    int[] circuitPermute;

    boolean circuitNonLinear;
    boolean circuitNeedsMap;
    int circuitMatrixSize;
    int circuitMatrixFullSize;
}
