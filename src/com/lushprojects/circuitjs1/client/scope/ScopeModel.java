package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

import java.util.Vector;

final class ScopeModel {
    private Vector<ScopePlot> plots = new Vector<ScopePlot>();
    private Vector<ScopePlot> visiblePlots = new Vector<ScopePlot>();
    private int historySize;
    private int historyCapacity;
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

    int getHistoryCapacity() {
        return historyCapacity;
    }

    double getHistorySampleInterval() {
        return historySampleInterval;
    }

    void setHistorySampleInterval(double historySampleInterval) {
        this.historySampleInterval = historySampleInterval;
    }

    boolean areHistoryBuffersAllocated() {
        for (int i = 0; i < plots.size(); i++) {
            ScopePlot p = plots.get(i);
            if (p.historyMinValues == null || p.historyMaxValues == null) {
                return false;
            }
        }
        return true;
    }

    void initializeHistoryBuffers(int scopePointCount, double sampleInterval) {
        historySize = 0;
        historyCapacity = scopePointCount * 4;
        historySampleInterval = sampleInterval;
        for (int i = 0; i < plots.size(); i++) {
            ScopePlot p = plots.get(i);
            p.historyMinValues = new double[historyCapacity];
            p.historyMaxValues = new double[historyCapacity];
        }
    }

    void resizeHistoryBuffers(int newCapacity) {
        if (newCapacity == historyCapacity) {
            return;
        }
        historyCapacity = newCapacity;
        for (int i = 0; i < plots.size(); i++) {
            ScopePlot p = plots.get(i);
            double[] newMinValues = new double[historyCapacity];
            double[] newMaxValues = new double[historyCapacity];
            int copySize = Math.min(historySize, historyCapacity);
            for (int j = 0; j < copySize; j++) {
                newMinValues[j] = p.historyMinValues[j];
                newMaxValues[j] = p.historyMaxValues[j];
            }
            p.historyMinValues = newMinValues;
            p.historyMaxValues = newMaxValues;
        }
        if (historySize > historyCapacity) {
            historySize = historyCapacity;
        }
    }

    void clearHistoryBuffers() {
        historySize = 0;
        historyCapacity = 0;
        historySampleInterval = 0;
        for (int i = 0; i < plots.size(); i++) {
            ScopePlot p = plots.get(i);
            p.historyMinValues = null;
            p.historyMaxValues = null;
        }
    }

    boolean captureToHistory(double simTime, double startTime, int scopePointCount) {
        if (historySize > 0) {
            double lastSampleTime = startTime + (historySize - 1) * historySampleInterval;
            if (simTime < lastSampleTime + historySampleInterval * 0.9) {
                return true;
            }
        }

        if (historySize >= historyCapacity) {
            downsampleHistory();
        }

        if (!areHistoryBuffersAllocated()) {
            return false;
        }

        for (int i = 0; i < plots.size(); i++) {
            ScopePlot p = plots.get(i);
            int idx = p.ptr & (scopePointCount - 1);
            p.historyMinValues[historySize] = p.minValues[idx];
            p.historyMaxValues[historySize] = p.maxValues[idx];
        }

        historySize++;
        return true;
    }

    void downsampleHistory() {
        int newSize = historySize / 2;
        historySampleInterval *= 2;
        for (int i = 0; i < plots.size(); i++) {
            ScopePlot p = plots.get(i);
            if (p.historyMinValues == null || p.historyMaxValues == null) {
                continue;
            }
            for (int j = 0; j < newSize; j++) {
                int src1 = j * 2;
                int src2 = j * 2 + 1;
                p.historyMinValues[j] = Math.min(p.historyMinValues[src1],
                        src2 < historySize ? p.historyMinValues[src2] : p.historyMinValues[src1]);
                p.historyMaxValues[j] = Math.max(p.historyMaxValues[src1],
                        src2 < historySize ? p.historyMaxValues[src2] : p.historyMaxValues[src1]);
            }
        }
        historySize = newSize;
    }
}
