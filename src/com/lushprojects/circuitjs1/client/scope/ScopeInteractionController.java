package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

import com.lushprojects.circuitjs1.client.elements.ActionScheduler;
import java.util.List;
import java.util.Vector;

final class ScopeInteractionController {
    private ScopeInteractionController() {
    }

    static boolean isWithinPlotX(int localX, int plotWidth) {
        return localX >= 0 && localX < plotWidth;
    }

    static double mapCursorTimeForDrawFromZero(double startTime, double currentTime, int localPlotX, int plotWidth,
                                               boolean autoScaleTime, double timePerPixel) {
        double elapsedTime = currentTime - startTime;
        return ScopeLayout.mapCursorXToTime(startTime, elapsedTime, localPlotX, plotWidth, autoScaleTime, timePerPixel);
    }

    static double mapCursorTimeForScrolling(double currentTime, double maxTimeStep, int speed,
                                            int localPlotX, int stride, int displayWidth) {
        int displayPixelWidth = displayWidth * stride;
        if (displayWidth <= 0 || localPlotX < 0 || localPlotX >= displayPixelWidth) {
            return -1;
        }
        int sampleX = localPlotX / stride;
        int ageSamples = (displayWidth - 1) - sampleX;
        return currentTime - maxTimeStep * speed * ageSamples;
    }

    static int findNearestPlotIndexAtSampleX(Vector<ScopePlot> visiblePlots, int sampleX, int requiredSamples,
                                             int scopePointCount, int mouseY, int rectY, int plotTop, int centerY) {
        int bestDist = Integer.MAX_VALUE;
        int best = -1;
        for (int i = 0; i < visiblePlots.size(); i++) {
            ScopePlot plot = visiblePlots.get(i);
            int plotDisplayWidth = plot.getDisplayWidth(requiredSamples);
            if (plotDisplayWidth <= 0) {
                continue;
            }
            int clampedSampleX = Math.min(sampleX, plotDisplayWidth - 1);
            int plotStartIndex = plot.startIndex(plotDisplayWidth);
            int idx = (clampedSampleX + plotStartIndex) & (scopePointCount - 1);
            // Match hover distance to the actual rendered trace (midpoint between min/max).
            double midVal = (plot.minValues[idx] + plot.maxValues[idx]) * 0.5;
            int midvy = (int) (plot.gridMult * (midVal + plot.plotOffset));
            int dist = Math.abs(mouseY - (rectY + plotTop + centerY - midvy));
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    static int findNearestPlotIndexInHistory(Scope scope, Vector<ScopePlot> visiblePlots, int historyIndex,
                                             int mouseY, int rectY, int plotTop, int centerY) {
        int bestDist = Integer.MAX_VALUE;
        int best = -1;
        for (int i = 0; i < visiblePlots.size(); i++) {
            ScopePlot plot = visiblePlots.get(i);
            VariableHistoryStore.SeriesSnapshot historySnapshot = scope.getHistorySnapshotForRender(plot);
            if (historySnapshot == null) {
                continue;
            }
            double[] histMinValues = historySnapshot.minValues;
            double[] histMaxValues = historySnapshot.maxValues;
            if (historyIndex < 0
                    || historyIndex >= histMinValues.length
                    || historyIndex >= histMaxValues.length) {
                continue;
            }
            double midVal = (histMinValues[historyIndex] + histMaxValues[historyIndex]) * 0.5;
            int midvy = (int) (plot.gridMult * (midVal + plot.plotOffset));
            int dist = Math.abs(mouseY - (rectY + plotTop + centerY - midvy));
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    static int findHoveredActionIndex(List<ActionScheduler.ScheduledAction> enabledActions, int mousePlotX, int plotWidth,
                                      double startTime, double displayTimeSpan, int hoverThresholdPx) {
        if (!isWithinPlotX(mousePlotX, plotWidth)) {
            return -1;
        }
        for (int i = 0; i < enabledActions.size(); i++) {
            ActionScheduler.ScheduledAction action = enabledActions.get(i);
            double timeFromStart = action.actionTime - startTime;
            if (timeFromStart < 0 || timeFromStart > displayTimeSpan) {
                continue;
            }
            int gx = (int) (plotWidth * timeFromStart / displayTimeSpan);
            if (Math.abs(mousePlotX - gx) < hoverThresholdPx) {
                return i;
            }
        }
        return -1;
    }

    static int mapHistoryIndexForSelection(ScopeFrameContext frame, int localX, int historySize,
                                           double historySampleInterval, double startTime, double currentTime,
                                           double maxTimeStep, int speed) {
        if (historySize <= 0 || localX < 0 || localX >= frame.plotWidth) {
            return -1;
        }
        ScopeDisplayConfig config = frame.displayConfig;
        if (config.autoScaleTime) {
            int idx = (int) (((long) localX * historySize) / frame.plotWidth);
            return Math.min(Math.max(idx, 0), historySize - 1);
        }
        double elapsedTime = currentTime - startTime;
        double timePerPixel = maxTimeStep * speed;
        if (!(timePerPixel > 0)) {
            return -1;
        }
        int pixelsNeeded = (int) (elapsedTime / timePerPixel);
        int pixelsUsed = Math.min(pixelsNeeded, frame.plotWidth);
        if (pixelsUsed < frame.plotWidth) {
            if (localX >= pixelsUsed) {
                return -1;
            }
            double time = localX * timePerPixel;
            int idx = (int) (time / historySampleInterval);
            return Math.min(Math.max(idx, 0), historySize - 1);
        }
        double windowTimeSpan = frame.plotWidth * timePerPixel;
        double windowStart = elapsedTime - windowTimeSpan;
        double time = windowStart + localX * timePerPixel;
        int idx = (int) (time / historySampleInterval);
        return Math.min(Math.max(idx, 0), historySize - 1);
    }

    static int findNearestMultiLhsAxisIndex(int localScopeX, int axisCount, int maxDistancePx) {
        int bestAxis = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < axisCount; i++) {
            int axisX = ScopeLayout.getMultiLhsAxisX(i);
            int dist = Math.abs(localScopeX - axisX);
            if (dist < bestDist) {
                bestDist = dist;
                bestAxis = i;
            }
        }
        return bestDist <= maxDistancePx ? bestAxis : -1;
    }

    static double[] getDisplayedTimeRange(ScopeFrameContext frame, Vector<ScopePlot> visiblePlots,
                                          double startTime, double currentTime) {
        if (frame.plotWidth <= 0) {
            return null;
        }
        if (frame.displayConfig.isDrawFromZeroActive()) {
            double elapsedTime = currentTime - startTime;
            double span = (frame.displayConfig.autoScaleTime && elapsedTime > 0)
                    ? elapsedTime
                    : frame.defaultDisplayTimeSpan;
            if (span <= 0) {
                span = frame.defaultDisplayTimeSpan;
            }
            return new double[]{startTime, startTime + span};
        }
        ScopePlot primary = (visiblePlots != null && !visiblePlots.isEmpty()) ? visiblePlots.firstElement() : null;
        int requiredSamples = (frame.plotWidth + frame.horizontalPixelStride - 1) / frame.horizontalPixelStride;
        int displayWidth = primary != null ? primary.getDisplayWidth(requiredSamples) : frame.plotWidth;
        int displayPixelWidth = displayWidth * frame.horizontalPixelStride;
        if (displayPixelWidth < frame.plotWidth) {
            return new double[]{0, frame.defaultDisplayTimeSpan};
        }
        double end = currentTime;
        double start = end - frame.defaultDisplayTimeSpan;
        if (start < 0) {
            start = 0;
        }
        return new double[]{start, end};
    }
}
