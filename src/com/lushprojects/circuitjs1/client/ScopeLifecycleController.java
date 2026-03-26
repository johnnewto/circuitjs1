package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.util.Rectangle;
import java.util.Vector;

final class ScopeLifecycleController {
    private ScopeLifecycleController() {
    }

    static void setRect(Scope scope, Rectangle r) {
        int w = scope.rect.width;
        scope.rect = r;
        if (scope.rect.width != w) {
            resetGraph(scope, false, !scope.drawFromZero);
        }
    }

    static void setSpeed(Scope scope, int sp) {
        if (sp < 1) {
            sp = 1;
        }
        if (sp > 1024) {
            sp = 1024;
        }
        scope.speed = sp;
        resetGraph(scope, false, true);
    }

    static void speedUp(Scope scope) {
        if (scope.drawFromZero) {
            return;
        }
        if (scope.speed > 1) {
            scope.speed /= 2;
            resetGraph(scope, false, !scope.drawFromZero);
        }
    }

    static void slowDown(Scope scope) {
        if (scope.drawFromZero) {
            return;
        }
        if (scope.speed < 1024) {
            scope.speed *= 2;
        }
        resetGraph(scope, false, !scope.drawFromZero);
    }

    static void resetGraph(Scope scope, boolean full, boolean clearHistory) {
        scope.scopePointCount = 1;
        while (scope.scopePointCount <= scope.rect.width) {
            scope.scopePointCount *= 2;
        }
        if (scope.plots == null) {
            scope.setPlots(new Vector<ScopePlot>());
        }
        scope.showNegative = false;
        for (int i = 0; i != scope.plots.size(); i++) {
            scope.plots.get(i).reset(scope.scopePointCount, scope.speed, full);
        }
        scope.calcVisiblePlots();
        scope.scopeTimeStep = scope.sim.getMaxTimeStep();

        if (clearHistory) {
            scope.lastDisplayedActionIndex = -1;
            scope.lastDisplayedActionText = null;
            scope.lastDisplayedWasHover = false;
            scope.lastLoggedActionIndex = -1;
            scope.actionVerticalPositions.clear();
        }

        if (scope.drawFromZero) {
            double sampleInterval = scope.sim.getMaxTimeStep() * scope.speed;
            if (clearHistory) {
                scope.startTime = scope.sim.getTime();
                scope.model.initializeHistoryBuffers(scope.scopePointCount, sampleInterval);
            } else {
                scope.model.setHistorySampleInterval(sampleInterval);
                boolean needsAllocation = !scope.model.areHistoryBuffersAllocated();
                if (needsAllocation) {
                    scope.startTime = scope.sim.getTime();
                    scope.model.initializeHistoryBuffers(scope.scopePointCount, sampleInterval);
                } else {
                    int newCapacity = scope.scopePointCount * 4;
                    if (newCapacity != scope.model.getHistoryCapacity()) {
                        scope.model.resizeHistoryBuffers(newCapacity);
                    }
                }
            }
        } else {
            scope.model.clearHistoryBuffers();
        }

        allocImage(scope);
    }

    static void allocImage(Scope scope) {
        if (scope.imageCanvas != null) {
            scope.imageCanvas.setWidth(scope.rect.width + "PX");
            scope.imageCanvas.setHeight(scope.rect.height + "PX");
            scope.imageCanvas.setCoordinateSpaceWidth(scope.rect.width);
            scope.imageCanvas.setCoordinateSpaceHeight(scope.rect.height);
            scope.clear2dView();
        }
    }
}
