package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

import com.lushprojects.circuitjs1.client.elements.electronics.semiconductors.TransistorElm;
import java.util.Vector;

final class ScopeSelectionService {
    private ScopeSelectionService() {
    }

    static void setElm(Scope scope, CircuitElm ce) {
        scope.clearPlotsInternal();
        if (ce instanceof TransistorElm) {
            scope.setValue(Scope.VAL_VCE, ce);
        } else {
            scope.setValue(0, ce);
        }
        scope.initialize();
    }

    static void addElm(Scope scope, CircuitElm ce) {
        if (ce instanceof TransistorElm) {
            addValue(scope, Scope.VAL_VCE, ce);
        } else {
            addValue(scope, 0, ce);
        }
    }

    static void addValue(Scope scope, int val, CircuitElm ce) {
        if (val == 0) {
            scope.addPlotInternal(ce, Scope.UNITS_V, Scope.VAL_VOLTAGE, scope.getManScaleFromMaxScale(Scope.UNITS_V, false));
            if (scope.shouldAutoAddCurrentPlot(ce)) {
                scope.addPlotInternal(ce, Scope.UNITS_A, Scope.VAL_CURRENT, scope.getManScaleFromMaxScale(Scope.UNITS_A, false));
            }
        } else {
            int u = ce.getScopeUnitsForScope(val);
            scope.addPlotInternal(ce, u, val, scope.getManScaleFromMaxScale(u, false));
            if (u == Scope.UNITS_V) {
                scope.setShowVoltageVisible(true);
            }
        }
        scope.calcVisiblePlots();
        scope.resetGraph();
    }

    static void combine(Scope scope, Scope other) {
        Vector<ScopePlot> combinedPlots = scope.getVisiblePlotsSnapshot();
        combinedPlots.addAll(other.getVisiblePlotsSnapshot());
        scope.setPlots(combinedPlots);
        other.clearPlotsInternal();
        other.calcVisiblePlots();
        scope.calcVisiblePlots();
    }

    static int separate(Scope scope, Scope arr[], int pos) {
        ScopePlot lastPlot = null;
        int visibleCount = scope.getVisiblePlotCount();
        for (int i = 0; i != visibleCount; i++) {
            if (pos >= arr.length) {
                return pos;
            }
            Scope child = new Scope(scope.getSimForDialogs());
            ScopePlot sp = scope.getVisiblePlotAt(i);
            if (lastPlot != null && lastPlot.elm == sp.elm && lastPlot.value == Scope.VAL_VOLTAGE && sp.value == Scope.VAL_CURRENT) {
                continue;
            }
            child.setValue(sp.value, sp.elm);
            child.setPositionForEmbedded(pos);
            arr[pos++] = child;
            lastPlot = sp;
            child.setFlags(scope.getFlags());
            child.setSpeed(scope.getCurrentSpeed());
        }
        return pos;
    }

    static void removePlot(Scope scope, int plot) {
        if (plot < scope.getVisiblePlotCount()) {
            ScopePlot p = scope.getVisiblePlotAt(plot);
            scope.removePlotRef(p);
            scope.calcVisiblePlots();
        }
    }

    static void resetPlots(Scope scope) {
        scope.clearPlotsInternal();
    }

    static void addPlot(Scope scope, CircuitElm elm, int units, int value, double manualScale) {
        scope.addPlotInternal(elm, units, value, manualScale);
    }
}
