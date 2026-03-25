package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.util.Graphics;

final class ScopeWaveformRenderer {
    private ScopeWaveformRenderer() {
    }

    static void render(Scope scope, Graphics g, ScopeFrameContext frame, boolean allPlotsSameUnits, boolean selected) {
        scope.renderWaveformLayers(g, frame, allPlotsSameUnits, selected);
    }
}
