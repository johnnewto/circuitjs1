package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScopeFrameContextTest {
    @Test
    void defaultDisplaySpanUsesPlotWidthAndTimePerPixel() {
        ScopeDisplayConfig cfg = new ScopeDisplayConfig(false, false, false, false, false, false);
        ScopeFrameContext frame = new ScopeFrameContext(cfg, 12, 200, 4, 0.0025);
        assertEquals(0.5, frame.defaultDisplayTimeSpan, 1e-12);
        assertEquals(12, frame.plotLeft);
        assertEquals(200, frame.plotWidth);
        assertEquals(4, frame.horizontalPixelStride);
    }
}
