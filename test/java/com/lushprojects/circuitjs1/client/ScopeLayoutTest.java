package com.lushprojects.circuitjs1.client.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScopeLayoutTest {

    @Test
    void plotAreaDefaultsToFullWidthWhenNoGutter() {
        int axisCount = ScopeLayout.getMultiLhsAxisCount(3);
        int gutterWidth = ScopeLayout.getMultiLhsGutterWidth(false, axisCount);
        int plotLeft = ScopeLayout.getPlotAreaLeft(320, gutterWidth);
        int plotWidth = ScopeLayout.getPlotAreaWidth(320, plotLeft);

        assertEquals(0, gutterWidth);
        assertEquals(0, plotLeft);
        assertEquals(320, plotWidth);
        assertEquals(0, ScopeLayout.getInfoTextAnchorX(plotLeft));
    }

    @Test
    void plotAreaReservesExpectedSpaceForMultiLhsAxes() {
        int axisCount = ScopeLayout.getMultiLhsAxisCount(3);
        int gutterWidth = ScopeLayout.getMultiLhsGutterWidth(true, axisCount);
        int plotLeft = ScopeLayout.getPlotAreaLeft(320, gutterWidth);
        int plotWidth = ScopeLayout.getPlotAreaWidth(320, plotLeft);

        assertEquals(3, axisCount);
        assertEquals(112, gutterWidth);
        assertEquals(112, plotLeft);
        assertEquals(208, plotWidth);
        assertEquals(114, ScopeLayout.getInfoTextAnchorX(plotLeft));
    }

    @Test
    void plotAreaLeftIsClampedWhenGutterExceedsRectWidth() {
        int axisCount = ScopeLayout.getMultiLhsAxisCount(99);
        int gutterWidth = ScopeLayout.getMultiLhsGutterWidth(true, axisCount);
        int plotLeft = ScopeLayout.getPlotAreaLeft(20, gutterWidth);
        int plotWidth = ScopeLayout.getPlotAreaWidth(20, plotLeft);

        assertEquals(ScopeLayout.MULTI_LHS_MAX_AXES, axisCount);
        assertEquals(160, gutterWidth);
        assertEquals(19, plotLeft);
        assertEquals(1, plotWidth);
    }

    @Test
    void axisPositionsUseStableSpacing() {
        assertEquals(8, ScopeLayout.getMultiLhsAxisX(0));
        assertEquals(32, ScopeLayout.getMultiLhsAxisX(1));
        assertEquals(56, ScopeLayout.getMultiLhsAxisX(2));
    }

    @Test
    void cursorXMapsToTimeWithinPlotViewport() {
        double startTime = 2.0;
        double elapsedTime = 3.0;
        int plotWidth = 240;
        int localPlotX = 80;
        double timePerPixel = 0.0025;

        double fixedScaleTime = ScopeLayout.mapCursorXToTime(startTime, elapsedTime, localPlotX, plotWidth,
                false, timePerPixel);
        double autoScaleTime = ScopeLayout.mapCursorXToTime(startTime, elapsedTime, localPlotX, plotWidth,
                true, timePerPixel);

        assertEquals(startTime + localPlotX * timePerPixel, fixedScaleTime, 1e-12);
        assertEquals(startTime + (localPlotX / (double) plotWidth) * elapsedTime, autoScaleTime, 1e-12);
    }
}
