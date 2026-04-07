package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

import com.lushprojects.circuitjs1.client.util.Graphics;
import java.util.Vector;

/**
 * Renderer responsible for drawing waveform traces in the scope display.
 * Handles both normal scrolling mode and draw-from-zero mode rendering.
 */
final class ScopeWaveformRenderer {
    private ScopeWaveformRenderer() {
    }

    /**
     * Renders all waveform layers in proper z-order.
     * Order: non-V/A units, current, voltage, selected plot (topmost)
     */
    static void render(Scope scope, Graphics g, ScopeFrameContext frame, boolean allPlotsSameUnits, boolean selected) {
        Vector<ScopePlot> visiblePlots = scope.visiblePlots;
        int selectedPlot = scope.selectedPlot;
        boolean firstPlotDrawn = false;

        // Draw non-voltage/current units first (lowest z-order)
        for (int i = 0; i != visiblePlots.size(); i++) {
            if (visiblePlots.get(i).units > Scope.UNITS_A && i != selectedPlot) {
                drawPlot(scope, g, frame, visiblePlots.get(i), allPlotsSameUnits, false, selected);
                if (!firstPlotDrawn) {
                    scope.setDisplayGridStepX(scope.getGridStepX());
                    firstPlotDrawn = true;
                }
            }
        }

        // Draw current plots
        for (int i = 0; i != visiblePlots.size(); i++) {
            if (visiblePlots.get(i).units == Scope.UNITS_A && i != selectedPlot) {
                drawPlot(scope, g, frame, visiblePlots.get(i), allPlotsSameUnits, false, selected);
                if (!firstPlotDrawn) {
                    scope.setDisplayGridStepX(scope.getGridStepX());
                    firstPlotDrawn = true;
                }
            }
        }

        // Draw voltage plots
        for (int i = 0; i != visiblePlots.size(); i++) {
            if (visiblePlots.get(i).units == Scope.UNITS_V && i != selectedPlot) {
                drawPlot(scope, g, frame, visiblePlots.get(i), allPlotsSameUnits, false, selected);
                if (!firstPlotDrawn) {
                    scope.setDisplayGridStepX(scope.getGridStepX());
                    firstPlotDrawn = true;
                }
            }
        }

        // Draw selected plot last (topmost)
        if (selectedPlot >= 0 && selectedPlot < visiblePlots.size()) {
            drawPlot(scope, g, frame, visiblePlots.get(selectedPlot), allPlotsSameUnits, true, selected);
            if (!firstPlotDrawn) {
                scope.setDisplayGridStepX(scope.getGridStepX());
            }
        }
    }

    /**
     * Draws a single plot trace including grid lines (on first call per frame).
     */
    private static void drawPlot(Scope scope, Graphics g, ScopeFrameContext frame, ScopePlot plot,
                                  boolean allPlotsSameUnits, boolean selected, boolean allSelected) {
        if (plot.elm == null)
            return;

        String col;
        double gridMid = 0;
        int x = 0;
        final int plotHeight = frame.plotHeight;
        final int maxy = (plotHeight - 1) / 2;
        int plotWidth = frame.plotWidth;

        String color = (scope.isSomethingSelectedForRender()) ? "#A0A0A0" : plot.color;
        boolean mouseHoverSelected = scope.sim.getScopeSelectedIndexForScope() == -1
                && plot.elm != null
                && plot.elm.isMouseElmForUi();
        if (allSelected || mouseHoverSelected)
            color = CircuitElm.selectColor.getHexValue();
        else if (selected)
            color = plot.color;
        double traceStrokeWidth = selected ? 2.5 : ((allSelected || mouseHoverSelected) ? 2.0 : 1.0);
        double maxV[] = plot.maxValues;
        double minV[] = plot.minValues;
        boolean multiLhsEnabled = frame.displayConfig.isMultiLhsActive(scope.visiblePlots != null ? scope.visiblePlots.size() : 0);
        ScopeDisplayConfig config = frame.displayConfig;
        int[] historyIndexRange = scope.getHistoryVisibleIndexRangeForRender(frame);
        double[] axisRange = (!scope.isManualScale() && multiLhsEnabled)
                ? scope.calcMultiLhsAxisRangeForRender(plot, minV, maxV, historyIndexRange) : null;
        PlotScaleResult scaleResult = frame.plotScaleResults.get(plot);
        if (scaleResult == null) {
            scaleResult = ScopeScaler.buildPlotScaleResult(
                    scope.isManualScale(),
                    multiLhsEnabled,
                    allPlotsSameUnits,
                    scope.getMaxScaleForRender(),
                    scope.getShowNegativeForRender(),
                    scope.getMinValueForRender(),
                    scope.getMaxValueForRender(),
                    scope.getScaleForUnits(plot.units),
                    maxy,
                    scope.getManDivisionsForRender(),
                    plot.manScale,
                    plot.manVPosition,
                    Scope.V_POSITION_STEPS,
                    scope.getMultiLhsTickCountForRender(),
                    Scope.multa,
                    axisRange
            );
            frame.plotScaleResults.put(plot, scaleResult);
        }
        scope.setShowNegative(scaleResult.showNegative);
        gridMid = scaleResult.gridMid;
        plot.plotOffset = scaleResult.plotOffset;
        plot.gridMult = scaleResult.gridMult;
        plot.lhsAxisMin = scaleResult.lhsAxisMin;
        plot.lhsAxisMax = scaleResult.lhsAxisMax;
        plot.lhsAxisStep = scaleResult.lhsAxisStep;
        scope.setGridStepY(scaleResult.gridStepY);
        double gridMax = scaleResult.gridMax;
        int minRangeLo = -10 - (int) (gridMid * plot.gridMult);
        int minRangeHi = 10 - (int) (gridMid * plot.gridMult);

        String minorDiv = "#404040";
        String majorDiv = "#A0A0A0";
        String curColor = "#FFFF00";
        if (scope.sim.printableCheckItem.getState()) {
            minorDiv = "#D0D0D0";
            majorDiv = "#808080";
            curColor = "#A0A000";
        }
        if (allSelected)
            majorDiv = CircuitElm.selectColor.getHexValue();

        // Vertical (T) gridlines timing
        double gridStepX = ScopeScaler.calcGridStepX(frame.timePerPixel, scope.getMinPixelSpacingForRender(), Scope.multa);
        scope.setGridStepX(gridStepX);

        boolean highlightCenter = !scope.isManualScale();

        if (scope.shouldDrawGridLines()) {
            drawGridLines(scope, g, frame, plot, allPlotsSameUnits, plotHeight, plotWidth, maxy,
                    gridMid, minorDiv, majorDiv, highlightCenter);
        }

        // Only need gridlines drawn once per frame
        scope.clearDrawGridLines();

        g.setColor(color);
        g.context.setLineWidth(traceStrokeWidth);

        if (scope.isManualScale()) {
            // Draw zero point
            int y0 = maxy - (int) (plot.gridMult * plot.plotOffset);
            g.drawLine(0, y0, 8, y0);
            g.drawString("0", 0, y0 - 2);
        }

        // Optimize: Use batched drawing for scope waveform
        g.startBatch();

        int prevY = -1;
        boolean[] reduceRange = scope.getReduceRangeForRender();

        if (config.isDrawFromZeroActive()) {
            drawWaveformFromZero(scope, g, frame, plot, plotWidth, plotHeight, maxy, minRangeLo, minRangeHi, reduceRange);
        } else {
            drawWaveformScrolling(scope, g, frame, plot, plotWidth, plotHeight, maxy, minRangeLo, minRangeHi, reduceRange);
        }

        g.endBatch();
        g.context.setLineWidth(1.0);
    }

    /**
     * Draws horizontal and vertical grid lines.
     */
    private static void drawGridLines(Scope scope, Graphics g, ScopeFrameContext frame, ScopePlot plot,
                                       boolean allPlotsSameUnits, int plotHeight, int plotWidth, int maxy,
                                       double gridMid, String minorDiv, String majorDiv, boolean highlightCenter) {
        ScopeDisplayConfig config = frame.displayConfig;
        double gridStepY = scope.getGridStepYForRender();
        double gridStepX = scope.getGridStepX();
        String col;

        // Horizontal gridlines
        boolean showHGridLines = (gridStepY != 0) && (scope.isManualScale() || allPlotsSameUnits);
        for (int ll = -100; ll <= 100; ll++) {
            if (ll != 0 && !showHGridLines)
                continue;
            int yl = maxy - (int) ((ll * gridStepY - gridMid) * plot.gridMult);
            if (yl < 0 || yl >= plotHeight - 1)
                continue;
            col = ll == 0 && highlightCenter ? majorDiv : minorDiv;
            g.setColor(col);
            g.drawLine(0, yl, plotWidth - 1, yl);
        }

        // Vertical gridlines (time axis)
        if (config.isDrawFromZeroActive()) {
            drawVerticalGridLinesFromZero(scope, g, frame, plotWidth, plotHeight, gridStepX, minorDiv, majorDiv);
        } else {
            drawVerticalGridLinesScrolling(scope, g, frame, plot, plotWidth, plotHeight, gridStepX, minorDiv, majorDiv);
        }
    }

    private static void drawVerticalGridLinesFromZero(Scope scope, Graphics g, ScopeFrameContext frame,
                                                       int plotWidth, int plotHeight, double gridStepX,
                                                       String minorDiv, String majorDiv) {
        ScopeDisplayConfig config = frame.displayConfig;
        double startTime = scope.getStartTimeForRender();
        double elapsedTime = scope.sim.getTime() - startTime;
        double displayTimeSpan;

        if (config.autoScaleTime && elapsedTime > 0) {
            displayTimeSpan = elapsedTime;
        } else {
            displayTimeSpan = frame.timePerPixel * plotWidth;
        }

        // Adjust gridStepX if gridlines are too close
        double pixelSpacing = plotWidth * gridStepX / displayTimeSpan;
        int scalePtr = 0;
        while (pixelSpacing < 20 && displayTimeSpan > 0) {
            gridStepX *= Scope.multa[scalePtr % 3];
            pixelSpacing = plotWidth * gridStepX / displayTimeSpan;
            scalePtr++;
        }

        double gridStart = startTime - (startTime % gridStepX);

        for (int ll = 0; ; ll++) {
            double tl = gridStart + gridStepX * ll;
            if (tl < startTime)
                continue;

            double timeFromStart = tl - startTime;
            int gx = (int) (plotWidth * timeFromStart / displayTimeSpan);

            if (gx < 0)
                continue;
            if (gx >= plotWidth)
                break;

            String col = minorDiv;
            if (((tl + gridStepX / 4) % (gridStepX * 10)) < gridStepX) {
                col = majorDiv;
            }
            g.setColor(col);
            g.drawLine(gx, 0, gx, plotHeight - 1);
        }

        // Draw t=0 line in highlighted color
        g.setColor(majorDiv);
        g.drawLine(0, 0, 0, plotHeight - 1);

        // Draw action time markers
        scope.drawActionTimeMarkersForRender(g, startTime, displayTimeSpan);
    }

    private static void drawVerticalGridLinesScrolling(Scope scope, Graphics g, ScopeFrameContext frame,
                                                        ScopePlot plot, int plotWidth, int plotHeight,
                                                        double gridStepX, String minorDiv, String majorDiv) {
        double ts = frame.timePerPixel;
        int displayWidth = scope.getDisplaySampleWidthForRender(plot, frame);
        int displayPixelWidth = displayWidth * frame.horizontalPixelStride;

        if (displayPixelWidth < plotWidth) {
            // Initial fill mode
            double actionStartTime = 0;
            double actionDisplayTimeSpan = ts * plotWidth;
            for (int ll = 0; ; ll++) {
                double tl = ll * gridStepX;
                int gx = (int) (tl / ts);
                if (gx >= plotWidth)
                    break;
                String col = (ll % 10 == 0) ? majorDiv : minorDiv;
                g.setColor(col);
                g.drawLine(gx, 0, gx, plotHeight - 1);
            }
            scope.drawActionTimeMarkersForRender(g, actionStartTime, actionDisplayTimeSpan);
        } else {
            // Normal scrolling mode
            double tstart = scope.sim.getTime() - ts * plotWidth;
            double tx = scope.sim.getTime() - (scope.sim.getTime() % gridStepX);

            for (int ll = 0; ; ll++) {
                double tl = tx - gridStepX * ll;
                int gx = (int) ((tl - tstart) / ts);
                if (gx < 0)
                    break;
                if (gx >= plotWidth)
                    continue;
                if (tl < 0)
                    continue;
                String col = minorDiv;
                if (((tl + gridStepX / 4) % (gridStepX * 10)) < gridStepX) {
                    col = majorDiv;
                }
                g.setColor(col);
                g.drawLine(gx, 0, gx, plotHeight - 1);
            }
            scope.drawActionTimeMarkersForRender(g, tstart, ts * plotWidth);
        }
    }

    /**
     * Draws waveform in draw-from-zero mode using history buffers.
     */
    private static void drawWaveformFromZero(Scope scope, Graphics g, ScopeFrameContext frame, ScopePlot plot,
                                              int plotWidth, int plotHeight, int maxy,
                                              int minRangeLo, int minRangeHi, boolean[] reduceRange) {
        ScopeDisplayConfig config = frame.displayConfig;
        int historySize = scope.getHistorySizeForRender();
        VariableHistoryStore.SeriesSnapshot historySnapshot = scope.getHistorySnapshotForRender(plot);

        if (historySnapshot == null || historySize == 0) {
            return;
        }
        double[] histMinV = historySnapshot.minValues;
        double[] histMaxV = historySnapshot.maxValues;
        int prevY = -1;

        if (config.autoScaleTime) {
            // Auto-scale: map entire history to window width
            for (int i = 0; i < plotWidth; i++) {
                int histIdx = (i * historySize) / plotWidth;
                if (histIdx >= historySize)
                    histIdx = historySize - 1;

                double midVal = (histMinV[histIdx] + histMaxV[histIdx]) / 2.0;
                int midvy = (int) (plot.gridMult * (midVal + plot.plotOffset));

                int minvy = (int) (plot.gridMult * (histMinV[histIdx] + plot.plotOffset));
                int maxvy = (int) (plot.gridMult * (histMaxV[histIdx] + plot.plotOffset));

                if (minvy < minRangeLo || maxvy > minRangeHi) {
                    reduceRange[plot.units] = false;
                }

                int y = Math.max(0, Math.min(plotHeight - 1, maxy - midvy));

                if (prevY != -1) {
                    g.drawLine(i - 1, prevY, i, y);
                }
                prevY = y;
            }
        } else {
            // Fixed scale mode
            double startTime = scope.getStartTimeForRender();
            double elapsedTime = scope.sim.getTime() - startTime;
            double timePerPixel = scope.sim.getMaxTimeStep() * scope.speed;
            int pixelsNeeded = (int) (elapsedTime / timePerPixel);
            int pixelsUsed = Math.min(pixelsNeeded, plotWidth);
            double historySampleInterval = scope.getHistorySampleIntervalForRender();

            if (pixelsUsed < plotWidth) {
                // Not enough data to fill screen yet
                for (int i = 0; i < pixelsUsed; i++) {
                    double time = i * timePerPixel;
                    int histIdx = (int) (time / historySampleInterval);
                    if (histIdx >= historySize)
                        histIdx = historySize - 1;

                    double midVal = (histMinV[histIdx] + histMaxV[histIdx]) / 2.0;
                    int midvy = (int) (plot.gridMult * (midVal + plot.plotOffset));

                    int minvy = (int) (plot.gridMult * (histMinV[histIdx] + plot.plotOffset));
                    int maxvy = (int) (plot.gridMult * (histMaxV[histIdx] + plot.plotOffset));

                    if (minvy < minRangeLo || maxvy > minRangeHi) {
                        reduceRange[plot.units] = false;
                    }

                    int y = Math.max(0, Math.min(plotHeight - 1, maxy - midvy));

                    if (prevY != -1) {
                        g.drawLine(i - 1, prevY, i, y);
                    }
                    prevY = y;
                }
            } else {
                // Screen is full, show most recent window
                double windowTimeSpan = plotWidth * timePerPixel;
                double windowStartTime = elapsedTime - windowTimeSpan;

                for (int i = 0; i < plotWidth; i++) {
                    double time = windowStartTime + i * timePerPixel;
                    int histIdx = (int) (time / historySampleInterval);
                    if (histIdx < 0) histIdx = 0;
                    if (histIdx >= historySize) histIdx = historySize - 1;

                    double midVal = (histMinV[histIdx] + histMaxV[histIdx]) / 2.0;
                    int midvy = (int) (plot.gridMult * (midVal + plot.plotOffset));

                    int minvy = (int) (plot.gridMult * (histMinV[histIdx] + plot.plotOffset));
                    int maxvy = (int) (plot.gridMult * (histMaxV[histIdx] + plot.plotOffset));

                    if (minvy < minRangeLo || maxvy > minRangeHi) {
                        reduceRange[plot.units] = false;
                    }

                    int y = Math.max(0, Math.min(plotHeight - 1, maxy - midvy));

                    if (prevY != -1) {
                        g.drawLine(i - 1, prevY, i, y);
                    }
                    prevY = y;
                }
            }
        }
    }

    /**
     * Draws waveform in normal scrolling mode using circular buffer.
     */
    private static void drawWaveformScrolling(Scope scope, Graphics g, ScopeFrameContext frame, ScopePlot plot,
                                               int plotWidth, int plotHeight, int maxy,
                                               int minRangeLo, int minRangeHi, boolean[] reduceRange) {
        int stride = frame.horizontalPixelStride;
        int displayWidth = scope.getDisplaySampleWidthForRender(plot, frame);
        if (displayWidth <= 0) {
            return;
        }

        double[] minV = plot.minValues;
        double[] maxV = plot.maxValues;
        int scopePointCount = scope.scopePointCount;
        int ipa = plot.startIndex(displayWidth);
        int prevY = -1;

        for (int i = 0; i != displayWidth; i++) {
            int ip = (i + ipa) & (scopePointCount - 1);
            int curX = i * stride;

            double midVal = (minV[ip] + maxV[ip]) / 2.0;
            int midvy = (int) (plot.gridMult * (midVal + plot.plotOffset));

            int minvy = (int) (plot.gridMult * (minV[ip] + plot.plotOffset));
            int maxvy = (int) (plot.gridMult * (maxV[ip] + plot.plotOffset));

            if (minvy < minRangeLo || maxvy > minRangeHi) {
                reduceRange[plot.units] = false;
            }

            int y = Math.max(0, Math.min(plotHeight - 1, maxy - midvy));

            if (prevY != -1) {
                g.drawLine(curX - stride, prevY, curX, y);
            }
            prevY = y;
        }
    }
}
