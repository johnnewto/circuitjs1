package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

import com.lushprojects.circuitjs1.client.util.Graphics;

final class ScopeOverlayRenderer {
    private ScopeOverlayRenderer() {
    }

    static void renderInScope(Scope scope, Graphics g, ScopeFrameContext frame) {
        scope.renderOverlayLayer(g, frame);
    }

    static void renderCursor(Scope scope, Graphics g, ScopeFrameContext frame) {
        scope.renderCursorLayer(g, frame);
    }
}
