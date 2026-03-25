package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.util.Graphics;

final class ScopeGridRenderer {
    private ScopeGridRenderer() {
    }

    static void render(Scope scope, Graphics g, ScopeFrameContext frame) {
        scope.renderGridLayer(g, frame);
    }
}
