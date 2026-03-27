package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

import com.lushprojects.circuitjs1.client.util.Graphics;

/**
 * Renderer responsible for drawing grid lines and FFT spectrum in the scope display.
 */
final class ScopeGridRenderer {
    private ScopeGridRenderer() {
    }

    /**
     * Renders the grid layer including FFT when in FFT mode.
     */
    static void render(Scope scope, Graphics g, ScopeFrameContext frame) {
        if (frame.displayConfig.isFFTMode()) {
            drawFFTVerticalGridLines(scope, g, frame);
            drawFFT(scope, g, frame);
        }
    }

    /**
     * Draws vertical grid lines with frequency labels for FFT mode.
     */
    private static void drawFFTVerticalGridLines(Scope scope, Graphics g, ScopeFrameContext frame) {
        int prevEnd = 0;
        int divs = 20;
        int plotWidth = frame.plotWidth;
        double maxFrequency = 1 / (scope.sim.getMaxTimeStep() * scope.speed * divs * 2);
        for (int i = 0; i < divs; i++) {
            int x = plotWidth * i / divs;
            if (x < prevEnd) continue;
            String s = ((int) Math.round(i * maxFrequency)) + "Hz";
            int sWidth = (int) Math.ceil(g.context.measureText(s).getWidth());
            prevEnd = x + sWidth + 4;
            if (i > 0) {
                g.setColor("#880000");
                g.drawLine(x, 0, x, scope.rect.height);
            }
            g.setColor("#FF0000");
            g.drawString(s, x + 2, scope.rect.height);
        }
    }

    /**
     * Draws the FFT spectrum.
     */
    private static void drawFFT(Scope scope, Graphics g, ScopeFrameContext frame) {
        ScopePlot plot = (scope.visiblePlots.size() == 0) ? scope.plots.firstElement() : scope.visiblePlots.firstElement();
        scope.setFFTForRender(ScopeFFTHelper.drawSpectrum(
                g,
                scope.getFFTForRender(),
                scope.scopePointCount,
                plot,
                frame.plotWidth,
                scope.rect.height,
                scope.isLogSpectrumForRender(),
                scope.getScaleForUnits(plot.units)));
    }
}
