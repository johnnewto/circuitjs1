package com.lushprojects.circuitjs1.client.scope;

import java.util.Vector;

final class ScopeModel {
    private Vector<ScopePlot> plots = new Vector<ScopePlot>();
    private Vector<ScopePlot> visiblePlots = new Vector<ScopePlot>();
    private int historySize;
    private double historySampleInterval;

    Vector<ScopePlot> getPlots() {
        return plots;
    }

    Vector<ScopePlot> getVisiblePlots() {
        return visiblePlots;
    }

    void setPlots(Vector<ScopePlot> plots) {
        this.plots = plots != null ? plots : new Vector<ScopePlot>();
    }

    void clearPlots() {
        plots.clear();
        visiblePlots.clear();
    }

    void rebuildVisiblePlots(boolean plot2d, boolean showV, boolean showI) {
        visiblePlots = new Vector<ScopePlot>();
        int voltCount = 0;
        int currentCount = 0;
        int otherCount = 0;

        if (!plot2d) {
            for (int i = 0; i != plots.size(); i++) {
                ScopePlot plot = plots.get(i);
                if (plot.units == Scope.UNITS_V) {
                    if (showV) {
                        visiblePlots.add(plot);
                        plot.assignColor(voltCount++);
                    }
                } else if (plot.units == Scope.UNITS_A) {
                    if (showI) {
                        visiblePlots.add(plot);
                        plot.assignColor(currentCount++);
                    }
                } else {
                    visiblePlots.add(plot);
                    plot.assignColor(otherCount++);
                }
            }
        } else {
            for (int i = 0; (i < 2) && (i < plots.size()); i++) {
                visiblePlots.add(plots.get(i));
            }
        }
    }

    int getHistorySize() {
        return historySize;
    }

    double getHistorySampleInterval() {
        return historySampleInterval;
    }

    void setHistoryMetadata(int historySize, double historySampleInterval) {
        this.historySize = historySize;
        this.historySampleInterval = historySampleInterval;
    }

    void clearHistoryBuffers() {
        historySize = 0;
        historySampleInterval = 0;
    }
}
