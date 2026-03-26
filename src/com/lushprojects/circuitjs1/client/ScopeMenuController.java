package com.lushprojects.circuitjs1.client;

import java.util.Vector;

final class ScopeMenuController {
    private ScopeMenuController() {
    }

    static void handleMenu(Scope scope, String mi, boolean state) {
        if (mi == "maxscale") {
            scope.maxScale();
        }
        if (mi == "showvoltage") {
            scope.showVoltage(state);
        }
        if (mi == "showcurrent") {
            scope.showCurrent(state);
        }
        if (mi == "showscale") {
            scope.showScale(state);
        }
        if (mi == "showpeak") {
            scope.showMax(state);
        }
        if (mi == "shownegpeak") {
            scope.showMin(state);
        }
        if (mi == "showfreq") {
            scope.showFreq(state);
        }
        if (mi == "showfft") {
            scope.showFFT(state);
        }
        if (mi == "logspectrum") {
            scope.logSpectrum = state;
        }
        if (mi == "showrms") {
            scope.showRMS = state;
        }
        if (mi == "showaverage") {
            scope.showAverage = state;
        }
        if (mi == "showduty") {
            scope.showDutyCycle = state;
        }
        if (mi == "showelminfo") {
            scope.showElmInfo = state;
        }
        if (mi == "multilhsaxes") {
            scope.setMultiLhsAxes(state);
        }
        if (mi == "showpower") {
            scope.setValue(Scope.VAL_POWER);
        }
        if (mi == "showib") {
            scope.setValue(Scope.VAL_IB);
        }
        if (mi == "showic") {
            scope.setValue(Scope.VAL_IC);
        }
        if (mi == "showie") {
            scope.setValue(Scope.VAL_IE);
        }
        if (mi == "showvbe") {
            scope.setValue(Scope.VAL_VBE);
        }
        if (mi == "showvbc") {
            scope.setValue(Scope.VAL_VBC);
        }
        if (mi == "showvce") {
            scope.setValue(Scope.VAL_VCE);
        }
        if (mi == "showvcevsic") {
            scope.plot2d = true;
            scope.plotXY = false;
            scope.setValues(Scope.VAL_VCE, Scope.VAL_IC, scope.getElm(), null);
            scope.resetGraph();
        }

        if (mi == "showvvsi") {
            scope.plot2d = state;
            scope.plotXY = false;
            scope.resetGraph();
        }
        if (mi == "manualscale") {
            scope.setManualScale(state, true);
        }
        if (mi == "plotxy") {
            scope.plotXY = scope.plot2d = state;
            if (scope.plot2d) {
                scope.setPlots(new Vector<ScopePlot>(scope.visiblePlots));
            }
            if (scope.plot2d && scope.plots.size() == 1) {
                scope.selectY();
            }
            scope.resetGraph();
        }
        if (mi == "showresistance") {
            scope.setValue(Scope.VAL_R);
        }
        if (mi == "drawfromzero") {
            scope.applyDrawFromZeroMenu(state);
        }
        if (mi == "autoscaletime") {
            scope.autoScaleTime = state;
            scope.sim.needAnalyze();
        }
    }
}
