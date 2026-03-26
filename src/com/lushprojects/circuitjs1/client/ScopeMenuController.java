package com.lushprojects.circuitjs1.client;

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
            scope.setLogSpectrum(state);
        }
        if (mi == "showrms") {
            scope.setShowRms(state);
        }
        if (mi == "showaverage") {
            scope.setShowAverage(state);
        }
        if (mi == "showduty") {
            scope.setShowDutyCycle(state);
        }
        if (mi == "showelminfo") {
            scope.setShowElmInfo(state);
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
            scope.showVceVsIc();
        }

        if (mi == "showvvsi") {
            scope.showVVsI(state);
        }
        if (mi == "manualscale") {
            scope.setManualScale(state, true);
        }
        if (mi == "plotxy") {
            scope.setPlotXy(state);
        }
        if (mi == "showresistance") {
            scope.setValue(Scope.VAL_R);
        }
        if (mi == "drawfromzero") {
            scope.applyDrawFromZeroMenu(state);
        }
        if (mi == "autoscaletime") {
            scope.setAutoScaleTime(state);
        }
    }
}
