package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

final class ScopeDisplayConfig {
    final boolean manualScale;
    final boolean plot2d;
    final boolean showFFT;
    final boolean multiLhsAxes;
    final boolean drawFromZero;
    final boolean autoScaleTime;

    ScopeDisplayConfig(boolean manualScale, boolean plot2d, boolean showFFT, boolean multiLhsAxes,
                       boolean drawFromZero, boolean autoScaleTime) {
        this.manualScale = manualScale;
        this.plot2d = plot2d;
        this.showFFT = showFFT;
        this.multiLhsAxes = multiLhsAxes;
        this.drawFromZero = drawFromZero;
        this.autoScaleTime = autoScaleTime;
    }

    boolean is2DMode() {
        return plot2d;
    }

    boolean isFFTMode() {
        return showFFT;
    }

    boolean isMultiLhsActive(int visiblePlotCount) {
        return multiLhsAxes && !plot2d && !showFFT && visiblePlotCount > 1;
    }

    boolean isDrawFromZeroActive() {
        return drawFromZero && !plot2d;
    }
}
