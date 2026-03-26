package com.lushprojects.circuitjs1.client.scope;

final class PlotScaleResult {
    final double gridMid;
    final double gridMax;
    final double plotOffset;
    final double gridMult;
    final double lhsAxisMin;
    final double lhsAxisMax;
    final double lhsAxisStep;
    final double gridStepY;
    final boolean showNegative;

    PlotScaleResult(double gridMid, double gridMax, double plotOffset, double gridMult,
                    double lhsAxisMin, double lhsAxisMax, double lhsAxisStep,
                    double gridStepY, boolean showNegative) {
        this.gridMid = gridMid;
        this.gridMax = gridMax;
        this.plotOffset = plotOffset;
        this.gridMult = gridMult;
        this.lhsAxisMin = lhsAxisMin;
        this.lhsAxisMax = lhsAxisMax;
        this.lhsAxisStep = lhsAxisStep;
        this.gridStepY = gridStepY;
        this.showNegative = showNegative;
    }
}
