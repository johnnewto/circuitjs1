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

    static int findNearestPlotIndexInHistory(Vector<ScopePlot> visiblePlots, int historyIndex,
                                             int mouseY, int rectY, int plotTop, int centerY) {
        int bestDist = Integer.MAX_VALUE;
        int best = -1;
        for (int i = 0; i < visiblePlots.size(); i++) {
            ScopePlot plot = visiblePlots.get(i);
            if (plot.historyMinValues == null || plot.historyMaxValues == null) {
                continue;
            }
            if (historyIndex < 0
                    || historyIndex >= plot.historyMinValues.length
                    || historyIndex >= plot.historyMaxValues.length) {
                continue;
            }
            double midVal = (plot.historyMinValues[historyIndex] + plot.historyMaxValues[historyIndex]) * 0.5;
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
}
