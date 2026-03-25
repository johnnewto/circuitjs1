package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Map;

final class ScopeFrameContext {
    final ScopeDisplayConfig displayConfig;
    final int plotLeft;
    final int plotWidth;
    final int horizontalPixelStride;
    final double timePerPixel;
    final double defaultDisplayTimeSpan;
    final Map<ScopePlot, PlotScaleResult> plotScaleResults = new HashMap<ScopePlot, PlotScaleResult>();

    ScopeFrameContext(ScopeDisplayConfig displayConfig, int plotLeft, int plotWidth,
                      int horizontalPixelStride, double timePerPixel) {
        this.displayConfig = displayConfig;
        this.plotLeft = plotLeft;
        this.plotWidth = plotWidth;
        this.horizontalPixelStride = horizontalPixelStride;
        this.timePerPixel = timePerPixel;
        this.defaultDisplayTimeSpan = plotWidth * timePerPixel;
    }
}
