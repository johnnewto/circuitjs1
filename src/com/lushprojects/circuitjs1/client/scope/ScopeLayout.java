package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

final class ScopeLayout {
    static final int MULTI_LHS_MAX_AXES = 5;
    private static final int MULTI_LHS_TOP_INFO_GUTTER = 20;
    private static final int MULTI_LHS_TIME_AXIS_HEIGHT = 20;
    private static final int MULTI_LHS_AXIS_SPACING = 24;
    private static final int MULTI_LHS_AXIS_START_X = 8;
    private static final int MULTI_LHS_TICK_LEN = 4;
    private static final int MULTI_LHS_TICK_LABEL_OFFSET = 4;
    private static final int MULTI_LHS_GUTTER_RIGHT_PADDING = 8;
    private static final int AXIS_LABEL_RESERVED_WIDTH = 44;
    private static final int INFO_TEXT_PADDING_X = 2;

    private ScopeLayout() {
    }

    static int getMultiLhsAxisCount(int visiblePlotCount) {
        if (visiblePlotCount <= 0) {
            return 0;
        }
        return Math.min(MULTI_LHS_MAX_AXES, visiblePlotCount);
    }

    static int getMultiLhsGutterWidth(boolean multiLhsDrawEnabled, int axisCount) {
        if (!multiLhsDrawEnabled || axisCount <= 0) {
            return 0;
        }
        return MULTI_LHS_AXIS_START_X
                + (axisCount - 1) * MULTI_LHS_AXIS_SPACING
                + MULTI_LHS_TICK_LEN
                + AXIS_LABEL_RESERVED_WIDTH
                + MULTI_LHS_GUTTER_RIGHT_PADDING;
    }

    static int getPlotAreaLeft(int rectWidth, int gutterWidth) {
        int left = Math.max(0, gutterWidth);
        if (left > rectWidth - 1) {
            return Math.max(0, rectWidth - 1);
        }
        return left;
    }

    static int getPlotAreaWidth(int rectWidth, int plotLeft) {
        return Math.max(1, rectWidth - plotLeft);
    }

    static int getMultiLhsTimeAxisHeight(boolean multiLhsDrawEnabled, int rectHeight) {
        if (!multiLhsDrawEnabled || rectHeight < 60) {
            return 0;
        }
        return Math.min(MULTI_LHS_TIME_AXIS_HEIGHT, rectHeight / 3);
    }

    static int getMultiLhsTopInfoGutterHeight(boolean multiLhsDrawEnabled, int rectHeight) {
        if (!multiLhsDrawEnabled || rectHeight < 60) {
            return 0;
        }
        return Math.min(MULTI_LHS_TOP_INFO_GUTTER, rectHeight / 3);
    }

    static int getMainPlotHeight(int rectHeight, int topInfoGutterHeight, int timeAxisHeight) {
        return Math.max(1, rectHeight - Math.max(0, topInfoGutterHeight) - Math.max(0, timeAxisHeight));
    }

    static int getInfoTextAnchorX(int plotLeft) {
        return plotLeft > 0 ? plotLeft + INFO_TEXT_PADDING_X : 0;
    }

    static int getMultiLhsAxisX(int axisIndex) {
        return MULTI_LHS_AXIS_START_X + axisIndex * MULTI_LHS_AXIS_SPACING;
    }

    static int getMultiLhsTickEndX(int axisX) {
        return axisX + MULTI_LHS_TICK_LEN;
    }

    static int getMultiLhsTickLabelX(int axisX) {
        return axisX + MULTI_LHS_TICK_LEN + MULTI_LHS_TICK_LABEL_OFFSET;
    }

    static double mapCursorXToTime(double startTime, double elapsedTime, int localPlotX, int plotWidth,
                                   boolean autoScaleTime, double timePerPixel) {
        if (plotWidth <= 0 || localPlotX < 0) {
            return -1;
        }
        if (autoScaleTime && elapsedTime > 0) {
            return startTime + (localPlotX / (double) plotWidth) * elapsedTime;
        }
        return startTime + (localPlotX / (double) plotWidth) * plotWidth * timePerPixel;
    }
}
