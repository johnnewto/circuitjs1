package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

import com.lushprojects.circuitjs1.client.util.Color;
import com.lushprojects.circuitjs1.client.util.Graphics;

/**
 * Renderer responsible for drawing overlay elements (settings wheel, cursor info, etc.)
 */
final class ScopeOverlayRenderer {
    // Settings wheel dimensions
    private static final int SETTINGS_WHEEL_SIZE = 36;
    private static final int SETTINGS_WHEEL_MARGIN = 100;
    private static final int OUTER_RADIUS = 8;
    private static final int INNER_RADIUS = 5;
    private static final int INNER_RADIUS_45 = 4;
    private static final int OUTER_RADIUS_45 = 6;

    private ScopeOverlayRenderer() {
    }

    static void renderInScope(Scope scope, Graphics g, ScopeFrameContext frame) {
        scope.renderOverlayLayer(g, frame);
    }

    /**
     * Draws the settings wheel icon in the bottom-left corner of the scope.
     */
    static void drawSettingsWheel(Scope scope, Graphics g) {
        if (!showSettingsWheel(scope))
            return;

        g.context.save();

        // Set color based on cursor position
        g.setColor(cursorInSettingsWheel(scope) ? CircuitElm.selectColor : Color.dark_gray);

        // Position at bottom-left corner
        g.context.translate(scope.rect.x + 18, scope.rect.y + scope.rect.height - 18);

        // Draw center circle
        CircuitElm.drawThickCircleForScope(g, 0, 0, INNER_RADIUS);

        // Draw horizontal spokes
        CircuitElm.drawThickLine(g, -OUTER_RADIUS, 0, -INNER_RADIUS, 0);
        CircuitElm.drawThickLine(g, OUTER_RADIUS, 0, INNER_RADIUS, 0);

        // Draw vertical spokes
        CircuitElm.drawThickLine(g, 0, -OUTER_RADIUS, 0, -INNER_RADIUS);
        CircuitElm.drawThickLine(g, 0, OUTER_RADIUS, 0, INNER_RADIUS);

        // Draw diagonal spokes
        CircuitElm.drawThickLine(g, -OUTER_RADIUS_45, -OUTER_RADIUS_45, -INNER_RADIUS_45, -INNER_RADIUS_45);
        CircuitElm.drawThickLine(g, OUTER_RADIUS_45, -OUTER_RADIUS_45, INNER_RADIUS_45, -INNER_RADIUS_45);
        CircuitElm.drawThickLine(g, -OUTER_RADIUS_45, OUTER_RADIUS_45, -INNER_RADIUS_45, INNER_RADIUS_45);
        CircuitElm.drawThickLine(g, OUTER_RADIUS_45, OUTER_RADIUS_45, INNER_RADIUS_45, INNER_RADIUS_45);

        g.context.restore();
    }

    /**
     * Determines if settings wheel should be shown based on scope size.
     */
    private static boolean showSettingsWheel(Scope scope) {
        return scope.rect.height > SETTINGS_WHEEL_MARGIN && scope.rect.width > SETTINGS_WHEEL_MARGIN;
    }

    /**
     * Checks if cursor is over the settings wheel icon.
     */
    static boolean cursorInSettingsWheel(Scope scope) {
        return showSettingsWheel(scope) &&
                scope.sim.getMouseCursorX() >= scope.rect.x &&
                scope.sim.getMouseCursorX() <= scope.rect.x + SETTINGS_WHEEL_SIZE &&
                scope.sim.getMouseCursorY() >= scope.rect.y + scope.rect.height - SETTINGS_WHEEL_SIZE &&
                scope.sim.getMouseCursorY() <= scope.rect.y + scope.rect.height;
    }

    static void renderCursor(Scope scope, Graphics g, ScopeFrameContext frame) {
        if (scope.sim.isDialogShowingForScope())
            return;
        if (Scope.getCursorScopeForRender() == null)
            return;
        ScopeDisplayConfig config = frame.displayConfig;
        String info[] = new String[5];
        int cursorX = -1;
        int ct = 0;

        String plotName = scope.getScopeLabelOrTextForRender();
        if (plotName != null && !plotName.isEmpty()) {
            info[ct++] = plotName;
        }

        if (Scope.getCursorTimeForRender() >= 0) {
            int plotLeft = frame.plotLeft;
            int plotWidth = frame.plotWidth;

            if (config.isDrawFromZeroActive()) {
                double elapsedTime = scope.sim.getTime() - scope.getStartTimeForRender();
                double displayTimeSpan = config.autoScaleTime ? elapsedTime : frame.defaultDisplayTimeSpan;
                if (displayTimeSpan <= 0) {
                    displayTimeSpan = frame.defaultDisplayTimeSpan;
                }
                double timeFromStart = Scope.getCursorTimeForRender() - scope.getStartTimeForRender();
                cursorX = scope.rect.x + plotLeft + (int) (plotWidth * timeFromStart / displayTimeSpan);
            } else {
                int stride = frame.horizontalPixelStride;
                int displayWidth = scope.getDisplaySampleWidthForRender(scope.plots.get(0), frame);
                if (displayWidth <= 0) {
                    cursorX = -1;
                } else {
                    int ageSamples = (int) ((scope.sim.getTime() - Scope.getCursorTimeForRender()) / (scope.sim.getMaxTimeStep() * scope.speed));
                    cursorX = scope.rect.x + plotLeft + (displayWidth - 1 - ageSamples) * stride;
                }
            }

            int cursorMinX = scope.rect.x + plotLeft;
            int cursorMaxX = cursorMinX + scope.getDisplaySampleWidthForRender(scope.plots.get(0), frame) * frame.horizontalPixelStride;
            if (cursorX >= cursorMinX && cursorX < cursorMaxX) {
                int maxy = (scope.rect.height - 1) / 2;
                int y = maxy;
                if (scope.visiblePlots.size() > 0) {
                    ScopePlot plot = scope.visiblePlots.get(scope.selectedPlot >= 0 ? scope.selectedPlot : 0);
                    double value;
                    VariableHistoryStore.SeriesSnapshot historySnapshot = scope.getHistorySnapshotForRender(plot);

                    if (config.isDrawFromZeroActive() && historySnapshot != null) {
                        double timeFromStart = Scope.getCursorTimeForRender() - scope.getStartTimeForRender();
                        int historyIndex;
                        historyIndex = (int) (timeFromStart / scope.getHistorySampleIntervalForRender());
                        if (historyIndex >= 0 && historyIndex < historySnapshot.maxValues.length) {
                            value = historySnapshot.maxValues[historyIndex];
                        } else {
                            value = 0;
                        }
                    } else {
                        int stride = frame.horizontalPixelStride;
                        int displayWidth = scope.getDisplaySampleWidthForRender(scope.plots.get(0), frame);
                        if (displayWidth > 0) {
                            int ipa = scope.plots.get(0).startIndex(displayWidth);
                            int sampleX = (cursorX - scope.rect.x - plotLeft) / stride;
                            int ip = (sampleX + ipa) & (scope.scopePointCount - 1);
                            value = plot.maxValues[ip];
                        } else {
                            value = 0;
                        }
                    }

                    info[ct++] = plot.getUnitText(value);
                    int maxvy = (int) (plot.gridMult * (value + plot.plotOffset));
                    g.setColor(plot.color);
                    g.fillOval(cursorX - 2, scope.rect.y + y - maxvy - 2, 5, 5);
                }
            }
        }

        if (config.isFFTMode() && Scope.getCursorScopeForRender() == scope) {
            double maxFrequency = 1 / (scope.sim.getMaxTimeStep() * scope.speed * 2);
            if (cursorX < 0)
                cursorX = scope.sim.getMouseCursorX();
            int mousePlotX = scope.sim.getMouseCursorX() - scope.rect.x - frame.plotLeft;
            if (mousePlotX < 0)
                mousePlotX = 0;
            if (mousePlotX > frame.plotWidth)
                mousePlotX = frame.plotWidth;
            info[ct++] = CircuitElm.getUnitText(maxFrequency * mousePlotX / frame.plotWidth, "Hz");
        } else if (scope.plots.size() == 0 || cursorX < scope.rect.x + frame.plotLeft || cursorX >= scope.rect.x + frame.plotLeft + scope.getDisplaySampleWidthForRender(scope.plots.get(0), frame) * frame.horizontalPixelStride)
            return;

        if (scope.visiblePlots.size() > 0)
            info[ct++] = scope.sim.formatTimeFixedForScope(Scope.getCursorTimeForRender());

        if (Scope.getCursorScopeForRender() != scope) {
            if (scope.rect.height < 40 || (scope.position >= 0 && Scope.getCursorScopeForRender().position == scope.position)) {
                drawCursorInfo(scope, g, null, 0, cursorX, false);
                return;
            }
        }
        drawCursorInfo(scope, g, info, ct, cursorX, false);
    }

    static void drawCursorInfo(Scope scope, Graphics g, String[] info, int ct, int x, Boolean drawY) {
        int szw = 0, szh = 15 * ct;
        int i;
        for (i = 0; i != ct; i++) {
            int w = (int) g.context.measureText(info[i]).getWidth();
            if (w > szw)
                szw = w;
        }

        g.setColor(CircuitElm.whiteColor);
        g.drawLine(x, scope.rect.y, x, scope.rect.y + scope.rect.height);
        if (drawY)
            g.drawLine(scope.rect.x, scope.sim.getMouseCursorY(), scope.rect.x + scope.rect.width, scope.sim.getMouseCursorY());
        g.setColor(scope.sim.printableCheckItem.getState() ? "#FFFFFF" : "#000000");
        int bx = x;
        if (bx < szw / 2)
            bx = szw / 2;
        g.fillRect(bx - szw / 2, scope.rect.y - szh, szw, szh);
        g.setColor(CircuitElm.whiteColor);
        for (i = 0; i != ct; i++) {
            int w = (int) g.context.measureText(info[i]).getWidth();
            g.drawString(info[i], bx - w / 2, scope.rect.y - 2 - (ct - 1 - i) * 15);
        }
    }
}
