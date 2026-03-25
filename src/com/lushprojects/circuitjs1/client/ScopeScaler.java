package com.lushprojects.circuitjs1.client;

final class ScopeScaler {
    private ScopeScaler() {
    }

    static double computeAutoScale(double currentScale, double maxAbsValue, boolean maxScaleEnabled, Double maxScaleLimit) {
        double gridMax = currentScale;
        if (maxScaleEnabled) {
            gridMax = Math.max(maxAbsValue, gridMax);
        } else {
            while (maxAbsValue > gridMax) {
                gridMax *= 2;
            }
        }
        if (maxScaleLimit != null && gridMax > maxScaleLimit) {
            gridMax = maxScaleLimit;
        }
        return gridMax;
    }

    static double calcGridStepX(double timePerPixel, int minPixelSpacing, double[] multipliers) {
        int multptr = 0;
        double gsx = 1e-15;
        while (gsx < timePerPixel * minPixelSpacing) {
            gsx *= multipliers[(multptr++) % multipliers.length];
        }
        return gsx;
    }

    static double getGridMaxFromManualScale(int manDivisions, double manScale) {
        return ((double) manDivisions / 2 + 0.05) * manScale;
    }

    static double getNiceStep(double targetStep, double[] multipliers) {
        if (!(targetStep > 0)) {
            return 1.0;
        }
        double scaleFactor = Math.pow(10, Math.floor(Math.log10(targetStep)));
        double normalized = targetStep / scaleFactor;
        for (double m : multipliers) {
            if (normalized <= m) {
                return m * scaleFactor;
            }
        }
        return 10 * scaleFactor;
    }

    static double[] calcMultiLhsAxisRange(double[] minValues, double[] maxValues, int scopePointCount,
                                          int startIndex, int displayWidth, boolean showNegative,
                                          double fallbackScale, int tickCount, double[] niceStepMultipliers) {
        if (displayWidth <= 0) {
            return null;
        }
        double dataMin = Double.POSITIVE_INFINITY;
        double dataMax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < displayWidth; i++) {
            int ip = (i + startIndex) & (scopePointCount - 1);
            if (minValues[ip] < dataMin) {
                dataMin = minValues[ip];
            }
            if (maxValues[ip] > dataMax) {
                dataMax = maxValues[ip];
            }
        }
        if (!(dataMax >= dataMin)) {
            return null;
        }
        if (!showNegative && dataMin >= 0) {
            dataMin = 0;
        }
        double range = dataMax - dataMin;
        if (!(range > 0)) {
            double magnitude = Math.max(Math.abs(dataMax), Math.abs(dataMin));
            if (!(magnitude > 0)) {
                magnitude = Math.max(fallbackScale, 1e-6);
            }
            if (!showNegative && dataMax >= 0) {
                dataMin = 0;
                dataMax = magnitude;
            } else {
                dataMin = -magnitude;
                dataMax = magnitude;
            }
            range = dataMax - dataMin;
        }

        double step = getNiceStep(range / (tickCount - 1), niceStepMultipliers);
        double axisMin = Math.floor(dataMin / step) * step;
        double axisMax = axisMin + step * (tickCount - 1);
        for (int guard = 0; axisMax < dataMax - step * 1e-9 && guard < 8; guard++) {
            step = getNiceStep((dataMax - axisMin) / (tickCount - 1), niceStepMultipliers);
            axisMin = Math.floor(dataMin / step) * step;
            axisMax = axisMin + step * (tickCount - 1);
        }
        if (!showNegative && dataMin >= 0 && axisMin < 0 && Math.abs(axisMin) <= step * 0.5) {
            axisMin = 0;
            axisMax = axisMin + step * (tickCount - 1);
        }
        double epsilon = step * 1e-9;
        if (Math.abs(axisMin) < epsilon) {
            axisMin = 0;
        }
        if (Math.abs(axisMax) < epsilon) {
            axisMax = 0;
        }
        return new double[] {axisMin, axisMax, step};
    }

    static PlotScaleResult buildPlotScaleResult(boolean manualScale, boolean multiLhsAxesDrawEnabled,
                                                boolean allPlotsSameUnits, boolean maxScale, boolean showNegative,
                                                double minValue, double maxValue, double unitScale,
                                                int maxy, int manDivisions, double manScale, int manVPosition,
                                                int vPositionSteps, int lhsTickCount, double[] yStepMultipliers,
                                                double[] multiLhsAxisRange) {
        double gridMid = 0;
        double positionOffset = 0;
        double gridMax = Math.max(unitScale, 1e-12);
        double lhsMin = 0;
        double lhsMax = 0;
        double lhsStep = 0;
        boolean nextShowNegative = showNegative;

        if (!manualScale) {
            if (multiLhsAxisRange != null) {
                lhsMin = multiLhsAxisRange[0];
                lhsMax = multiLhsAxisRange[1];
                lhsStep = multiLhsAxisRange[2];
                gridMid = (lhsMax + lhsMin) * .5;
                gridMax = Math.max((lhsMax - lhsMin) * .5, 1e-12);
            } else if (allPlotsSameUnits) {
                double mx = gridMax;
                double mn = 0;
                if (maxScale) {
                    mx = maxValue;
                    mn = minValue;
                } else if (showNegative || minValue < (mx + mn) * .5 - (mx - mn) * .55) {
                    mn = -gridMax;
                    nextShowNegative = true;
                }
                gridMid = (mx + mn) * .5;
                gridMax = (mx - mn) * .55;
            }
        } else {
            gridMax = getGridMaxFromManualScale(manDivisions, manScale);
            positionOffset = gridMax * 2.0 * manVPosition / (double) vPositionSteps;
        }

        double plotOffset = -gridMid + positionOffset;
        double gridMult = maxy / gridMax;

        if (multiLhsAxesDrawEnabled && lhsStep <= 0) {
            lhsMin = gridMid - gridMax;
            lhsMax = gridMid + gridMax;
            lhsStep = (lhsMax - lhsMin) / (lhsTickCount - 1);
        }

        double gridStepY = computeGridStepY(manualScale, multiLhsAxesDrawEnabled, lhsStep, gridMax, maxy,
                manScale, yStepMultipliers);

        return new PlotScaleResult(gridMid, gridMax, plotOffset, gridMult, lhsMin, lhsMax, lhsStep, gridStepY, nextShowNegative);
    }

    static double computeGridStepY(boolean manualScale, boolean multiLhsAxesDrawEnabled, double lhsAxisStep,
                                   double gridMax, int maxy, double manScale, double[] yStepMultipliers) {
        if (manualScale) {
            return manScale;
        }
        if (multiLhsAxesDrawEnabled && lhsAxisStep > 0) {
            return lhsAxisStep;
        }
        int multptr = 0;
        double gridStepY = 1e-8;
        while (gridStepY < 20 * gridMax / maxy) {
            gridStepY *= yStepMultipliers[(multptr++) % yStepMultipliers.length];
        }
        return gridStepY;
    }
}
