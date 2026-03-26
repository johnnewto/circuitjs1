package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.util.Rectangle;

final class ScopeLifecycleController {
    private ScopeLifecycleController() {
    }

    static void setRect(Scope scope, Rectangle r) {
        scope.applyRectFromLifecycle(r);
    }

    static void setSpeed(Scope scope, int sp) {
        if (sp < 1) {
            sp = 1;
        }
        if (sp > 1024) {
            sp = 1024;
        }
        scope.applySetSpeedFromLifecycle(sp);
    }

    static void speedUp(Scope scope) {
        scope.applySpeedUpFromLifecycle();
    }

    static void slowDown(Scope scope) {
        scope.applySlowDownFromLifecycle();
    }

    static void resetGraph(Scope scope, boolean full, boolean clearHistory) {
        scope.applyResetGraphFromLifecycle(full, clearHistory);
    }

    static void allocImage(Scope scope) {
        scope.allocImageFromLifecycle();
    }
}
