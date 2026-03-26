package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

import com.lushprojects.circuitjs1.client.util.Graphics;
import com.lushprojects.circuitjs1.client.util.Locale;

final class ScopeAxisRenderer {
    private ScopeAxisRenderer() {
    }

    static void render(Scope scope, Graphics g, ScopeFrameContext frame) {
        drawMultiLhsGutter(scope, g, frame);
        drawTopGutterLegend(scope, g, frame);
        drawMultiLhsAxes(scope, g, frame);
        drawBottomTimeAxis(scope, g, frame);
    }

    private static boolean isMultiLhsAxesDrawEnabled(Scope scope, ScopeFrameContext frame) {
        return frame.displayConfig.isMultiLhsActive(scope.visiblePlots == null ? 0 : scope.visiblePlots.size());
    }

    private static int getMultiLhsAxisCount(Scope scope) {
        return ScopeLayout.getMultiLhsAxisCount(scope.visiblePlots == null ? 0 : scope.visiblePlots.size());
    }

    private static void drawMultiLhsGutter(Scope scope, Graphics g, ScopeFrameContext frame) {
        int plotLeft = frame.plotLeft;
        if (plotLeft <= 0) {
            return;
        }
        g.setColor(scope.sim.printableCheckItem.getState() ? "#eeeeee" : "#202020");
        g.fillRect(0, 0, plotLeft, scope.rect.height);
        g.setColor(scope.sim.printableCheckItem.getState() ? "#808080" : "#606060");
        g.drawLine(plotLeft, 0, plotLeft, scope.rect.height - 1);
    }

    private static String getMultiLhsAxisName(ScopePlot plot, int axisIndex) {
        if (plot != null && plot.elm != null) {
            String name = plot.elm.getScopeTextForScope(plot.value);
            if (name != null && !name.isEmpty()) {
                return name.length() > 10 ? name.substring(0, 10) : name;
            }
        }
        return "CH" + (axisIndex + 1);
    }

    private static String getMultiLhsAxisValueText(ScopePlot plot, double value) {
        if (plot == null) {
            return "";
        }
        switch (plot.units) {
        case Scope.UNITS_V:
            return CircuitElm.getShortUnitText(value, "V");
        case Scope.UNITS_A:
            return CircuitElm.getShortUnitText(value, "A");
        case Scope.UNITS_OHMS:
            return CircuitElm.getShortUnitText(value, Locale.ohmString);
        case Scope.UNITS_W:
            return CircuitElm.getShortUnitText(value, "W");
        default:
            return plot.getUnitText(value);
        }
    }

    private static void drawTopGutterLegend(Scope scope, Graphics g, ScopeFrameContext frame) {
        if (!isMultiLhsAxesDrawEnabled(scope, frame) || frame.plotTop <= 0 || scope.visiblePlots == null || scope.visiblePlots.isEmpty()) {
            return;
        }
        String bgColor = scope.sim.printableCheckItem.getState() ? "#f3f3f3" : "#181818";
        String textColor = scope.sim.printableCheckItem.getState() ? "#202020" : "#d0d0d0";
        g.setColor(bgColor);
        g.fillRect(frame.plotLeft, 0, frame.plotWidth, frame.plotTop);
        g.setColor(scope.sim.printableCheckItem.getState() ? "#808080" : "#606060");
        g.drawLine(frame.plotLeft, frame.plotTop - 1, frame.plotLeft + frame.plotWidth - 1, frame.plotTop - 1);

        int axisCount = getMultiLhsAxisCount(scope);
        int x = frame.plotLeft + 8;
        int y = Math.max(12, frame.plotTop - 6);
        g.context.save();
        g.context.setFont(scope.getScaledFontForRender(11, false));
        for (int i = 0; i < axisCount; i++) {
            ScopePlot plot = scope.visiblePlots.get(i);
            if (plot == null) {
                continue;
            }
            boolean mouseHoverSelected = scope.sim.getScopeSelectedIndexForScope() == -1
                    && plot.elm != null
                    && plot.elm.isMouseElmForUi();
            boolean plotSelected = scope.selectedPlot == i;
            String legendColor = scope.isSomethingSelectedForRender() ? "#A0A0A0" : plot.color;
            if (scope.sim.isScopeMenuSelectedForScope(scope) || mouseHoverSelected) {
                legendColor = CircuitElm.selectColor.getHexValue();
            } else if (plotSelected) {
                legendColor = plot.color;
            }

            String label = getMultiLhsAxisName(plot, i);
            int bulletSize = 8;
            int bulletY = y - bulletSize + 1;
            int textPad = 6;
            int labelWidth = (int) Math.ceil(g.context.measureText(label).getWidth());
            int needed = bulletSize + textPad + labelWidth + 14;
            if (x + needed > frame.plotLeft + frame.plotWidth - 6) {
                break;
            }
            g.setColor(legendColor);
            g.fillOval(x, bulletY, bulletSize, bulletSize);
            g.context.setFillStyle(textColor);
            g.context.setTextAlign("left");
            g.context.setTextBaseline("alphabetic");
            g.context.fillText(label, x + bulletSize + textPad, y);
            x += needed;
        }
        g.context.restore();
    }

    private static void drawMultiLhsAxes(Scope scope, Graphics g, ScopeFrameContext frame) {
        if (!isMultiLhsAxesDrawEnabled(scope, frame)) {
            return;
        }
        int axisCount = getMultiLhsAxisCount(scope);
        int plotLeft = frame.plotLeft;
        int plotHeight = frame.plotHeight;
        int maxy = (plotHeight - 1) / 2;
        boolean scopeSelected = scope.sim.isScopeMenuSelectedForScope(scope);

        g.context.save();
        g.context.setFont(scope.getScaledFontForRender(9, false));

        for (int i = 0; i < axisCount; i++) {
            ScopePlot plot = scope.visiblePlots.get(i);
            if (plot == null || plot.gridMult == 0) {
                continue;
            }

            int axisX = ScopeLayout.getMultiLhsAxisX(i);
            if (axisX >= plotLeft - 2) {
                break;
            }

            boolean mouseHoverSelected = scope.sim.getScopeSelectedIndexForScope() == -1
                    && plot.elm != null
                    && plot.elm.isMouseElmForUi();
            boolean plotSelected = scope.selectedPlot == i;
            String axisColor = scope.isSomethingSelectedForRender() ? "#A0A0A0" : plot.color;
            if (scopeSelected || mouseHoverSelected) {
                axisColor = CircuitElm.selectColor.getHexValue();
            } else if (plotSelected) {
                axisColor = plot.color;
            }
            double axisStrokeWidth = plotSelected ? 2.5 : ((scopeSelected || mouseHoverSelected) ? 2.0 : 1.0);

            g.setColor(axisColor);
            g.context.setLineWidth(axisStrokeWidth);
            g.drawLine(axisX, frame.plotTop, axisX, frame.plotTop + plotHeight - 1);
            for (int tick = 0; tick < scope.getMultiLhsTickCountForRender(); tick++) {
                int localY = (plotHeight - 1) * tick / (scope.getMultiLhsTickCountForRender() - 1);
                int drawY = frame.plotTop + localY;
                g.drawLine(axisX, drawY, ScopeLayout.getMultiLhsTickEndX(axisX), drawY);
                int textX = ScopeLayout.getMultiLhsTickLabelX(axisX);
                if (textX >= plotLeft - 2) {
                    continue;
                }
                double tickValue;
                if (plot.lhsAxisStep > 0) {
                    tickValue = plot.lhsAxisMax - tick * plot.lhsAxisStep;
                    if (Math.abs(tickValue) < plot.lhsAxisStep * 1e-9) {
                        tickValue = 0;
                    }
                } else {
                    tickValue = ((double) (maxy - localY) / plot.gridMult) - plot.plotOffset;
                }
                String tickText = getMultiLhsAxisValueText(plot, tickValue);
                g.context.save();
                g.context.translate(textX, drawY);
                g.context.rotate(-Math.PI / 2.0);
                g.context.setTextAlign("center");
                g.context.setTextBaseline("middle");
                g.context.fillText(tickText, 0, 0);
                g.context.restore();
            }
            g.context.setLineWidth(1.0);
        }
        g.context.restore();
    }

    private static void drawBottomTimeAxis(Scope scope, Graphics g, ScopeFrameContext frame) {
        if (frame.timeAxisHeight <= 0 || frame.plotWidth <= 0) {
            return;
        }
        double[] timeRange = ScopeInteractionController.getDisplayedTimeRange(
                frame, scope.visiblePlots, scope.getStartTimeForRender(), scope.sim.getTime());
        if (timeRange == null) {
            return;
        }
        double axisGridStep = scope.getDisplayGridStepXForRender() > 0
                ? scope.getDisplayGridStepXForRender()
                : ScopeScaler.calcGridStepX(frame.timePerPixel, scope.getMinPixelSpacingForRender(), Scope.multa);
        if (axisGridStep <= 0) {
            return;
        }
        double span = timeRange[1] - timeRange[0];
        if (span <= 0) {
            return;
        }
        double baseGridPixelSpacing = frame.plotWidth * axisGridStep / span;
        int tickGridMultiple = 10;
        if (baseGridPixelSpacing * 2 >= 64) {
            tickGridMultiple = 2;
        } else if (baseGridPixelSpacing * 5 >= 64) {
            tickGridMultiple = 5;
        }
        double tickStep = axisGridStep * tickGridMultiple;
        int axisTop = frame.plotTop + frame.plotHeight;
        int axisBottom = scope.rect.height - 1;
        String bgColor = scope.sim.printableCheckItem.getState() ? "#f3f3f3" : "#181818";
        String lineColor = scope.sim.printableCheckItem.getState() ? "#707070" : "#8a8a8a";
        String textColor = scope.sim.printableCheckItem.getState() ? "#202020" : "#d0d0d0";

        g.setColor(bgColor);
        g.fillRect(frame.plotLeft, axisTop, frame.plotWidth, frame.timeAxisHeight);
        g.setColor(lineColor);
        g.drawLine(frame.plotLeft, axisTop, frame.plotLeft + frame.plotWidth - 1, axisTop);

        int labelY = axisBottom - 3;
        int tickTop = axisTop + 1;
        int tickBottom = tickTop + 4;
        g.context.save();
        g.context.setFont(scope.getScaledFontForRender(10, false));
        g.context.setFillStyle(textColor);

        double firstTickTime = Math.ceil(timeRange[0] / tickStep) * tickStep;
        for (double t = firstTickTime; t <= timeRange[1] + tickStep * 1e-9; t += tickStep) {
            int x = frame.plotLeft + (int) Math.round((t - timeRange[0]) * frame.plotWidth / span);
            if (x < frame.plotLeft || x > frame.plotLeft + frame.plotWidth - 1) {
                continue;
            }
            g.drawLine(x, tickTop, x, tickBottom);
            if (x - frame.plotLeft < 18) {
                g.context.setTextAlign("left");
            } else if ((frame.plotLeft + frame.plotWidth - 1) - x < 18) {
                g.context.setTextAlign("right");
            } else {
                g.context.setTextAlign("center");
            }
            g.context.fillText(scope.sim.formatTimeFixedForScope(t), x, labelY);
        }
        g.context.restore();
    }
}
