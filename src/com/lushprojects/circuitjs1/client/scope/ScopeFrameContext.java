package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

import java.util.HashMap;
import java.util.Map;

final class ScopeFrameContext {
    final ScopeDisplayConfig displayConfig;
    final int plotLeft;
    final int plotTop;
    final int plotWidth;
    final int plotHeight;
    final int timeAxisHeight;
    final int horizontalPixelStride;
    final double timePerPixel;
    final double defaultDisplayTimeSpan;
    final Map<ScopePlot, PlotScaleResult> plotScaleResults = new HashMap<ScopePlot, PlotScaleResult>();

    ScopeFrameContext(ScopeDisplayConfig displayConfig, int plotLeft, int plotTop, int plotWidth,
                      int plotHeight, int timeAxisHeight, int horizontalPixelStride, double timePerPixel) {
        this.displayConfig = displayConfig;
        this.plotLeft = plotLeft;
        this.plotTop = plotTop;
        this.plotWidth = plotWidth;
        this.plotHeight = plotHeight;
        this.timeAxisHeight = timeAxisHeight;
        this.horizontalPixelStride = horizontalPixelStride;
        this.timePerPixel = timePerPixel;
        this.defaultDisplayTimeSpan = plotWidth * timePerPixel;
    }
}
