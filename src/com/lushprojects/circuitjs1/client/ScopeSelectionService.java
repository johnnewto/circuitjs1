package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.electronics.digital.LogicOutputElm;
import com.lushprojects.circuitjs1.client.elements.electronics.measurement.AudioOutputElm;
import com.lushprojects.circuitjs1.client.elements.electronics.measurement.OutputElm;
import com.lushprojects.circuitjs1.client.elements.electronics.measurement.ProbeElm;
import com.lushprojects.circuitjs1.client.elements.electronics.semiconductors.TransistorElm;
import java.util.Vector;

final class ScopeSelectionService {
    private ScopeSelectionService() {
    }

    static void setElm(Scope scope, CircuitElm ce) {
        scope.setPlots(new Vector<ScopePlot>());
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
            scope.plots.add(new ScopePlot(ce, Scope.UNITS_V, Scope.VAL_VOLTAGE, scope.getManScaleFromMaxScale(Scope.UNITS_V, false)));
            if (ce != null &&
                    scope.sim.dotsCheckItem.getState() &&
                    !(ce instanceof OutputElm ||
                            ce instanceof LogicOutputElm ||
                            ce instanceof AudioOutputElm ||
                            ce instanceof ProbeElm)) {
                scope.plots.add(new ScopePlot(ce, Scope.UNITS_A, Scope.VAL_CURRENT, scope.getManScaleFromMaxScale(Scope.UNITS_A, false)));
            }
        } else {
            int u = ce.getScopeUnits(val);
            scope.plots.add(new ScopePlot(ce, u, val, scope.getManScaleFromMaxScale(u, false)));
            if (u == Scope.UNITS_V) {
                scope.showV = true;
            }
        }
        scope.calcVisiblePlots();
        scope.resetGraph();
    }

    static void combine(Scope scope, Scope other) {
        Vector<ScopePlot> combinedPlots = new Vector<ScopePlot>(scope.visiblePlots);
        combinedPlots.addAll(other.visiblePlots);
        scope.setPlots(combinedPlots);
        other.setPlots(new Vector<ScopePlot>());
        other.calcVisiblePlots();
        scope.calcVisiblePlots();
    }

    static int separate(Scope scope, Scope arr[], int pos) {
        ScopePlot lastPlot = null;
        for (int i = 0; i != scope.visiblePlots.size(); i++) {
            if (pos >= arr.length) {
                return pos;
            }
            Scope child = new Scope(scope.sim);
            ScopePlot sp = scope.visiblePlots.get(i);
            if (lastPlot != null && lastPlot.elm == sp.elm && lastPlot.value == Scope.VAL_VOLTAGE && sp.value == Scope.VAL_CURRENT) {
                continue;
            }
            child.setValue(sp.value, sp.elm);
            child.position = pos;
            arr[pos++] = child;
            lastPlot = sp;
            child.setFlags(scope.getFlags());
            child.setSpeed(scope.speed);
        }
        return pos;
    }

    static void removePlot(Scope scope, int plot) {
        if (plot < scope.visiblePlots.size()) {
            ScopePlot p = scope.visiblePlots.get(plot);
            scope.plots.remove(p);
            scope.calcVisiblePlots();
        }
    }

    static void resetPlots(Scope scope) {
        scope.setPlots(new Vector<ScopePlot>());
    }

    static void addPlot(Scope scope, CircuitElm elm, int units, int value, double manualScale) {
        if (scope.plots == null) {
            scope.setPlots(new Vector<ScopePlot>());
        }
        scope.plots.add(new ScopePlot(elm, units, value, manualScale));
    }
}
