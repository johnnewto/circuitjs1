package com.lushprojects.circuitjs1.client.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScopeFrameContextTest {
    @Test
    void defaultDisplaySpanUsesPlotWidthAndTimePerPixel() {
        ScopeDisplayConfig cfg = new ScopeDisplayConfig(false, false, false, false, false, false);
        ScopeFrameContext frame = new ScopeFrameContext(cfg, 12, 0, 200, 100, 0, 4, 0.0025);
        assertEquals(0.5, frame.defaultDisplayTimeSpan, 1e-12);
        assertEquals(12, frame.plotLeft);
        assertEquals(0, frame.plotTop);
        assertEquals(200, frame.plotWidth);
        assertEquals(100, frame.plotHeight);
        assertEquals(4, frame.horizontalPixelStride);
    }
}
