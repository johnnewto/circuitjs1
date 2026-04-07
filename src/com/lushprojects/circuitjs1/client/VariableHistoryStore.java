package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Bounded historical storage for browser-visible variables.
 *
 * Samples are captured once per converged timestep. Capacity is bounded by
 * pairwise downsampling so long simulations remain viewable without unbounded
 * memory growth.
 */
public final class VariableHistoryStore {
    private static final int DEFAULT_CAPACITY = 512;
    private static final double TIME_RESET_TOLERANCE = 1e-12;
    private static final String VARIABLE_KEY_PREFIX = "var:";

    public static final class SeriesSnapshot {
        public final String name;
        public final double[] time;
        public final double[] values;
        public final double[] minValues;
        public final double[] maxValues;

        private SeriesSnapshot(String name, double[] time, double[] values, double[] minValues, double[] maxValues) {
            this.name = name;
            this.time = time;
            this.values = values;
            this.minValues = minValues;
            this.maxValues = maxValues;
        }

        public int size() {
            return time.length;
        }

        public double averageSampleInterval() {
            if (time.length < 2) {
                return 0;
            }
            return (time[time.length - 1] - time[0]) / (time.length - 1);
        }

        public boolean hasEnvelope() {
            for (int i = 0; i < minValues.length; i++) {
                if (minValues[i] != maxValues[i]) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class Series {
        private String name;
        private double[] time = new double[DEFAULT_CAPACITY];
        private double[] minValues = new double[DEFAULT_CAPACITY];
        private double[] maxValues = new double[DEFAULT_CAPACITY];
        private int size;
        private double sampleInterval;
        private SeriesSnapshot cachedSnapshot;
        private boolean dirty = true;

        private Series(String name) {
            this.name = name;
        }

        private void append(double sampleTime, double minValue, double maxValue) {
            if (sampleInterval > 0 && size > 0) {
                double nextAllowedTime = time[size - 1] + sampleInterval;
                if (sampleTime < nextAllowedTime - sampleInterval * 0.1) {
                    return;
                }
            }
            if (size >= time.length) {
                downsample();
            }
            if (sampleInterval == 0 && size > 0) {
                double delta = sampleTime - time[size - 1];
                if (delta > 0) {
                    sampleInterval = delta;
                }
            }
            time[size] = sampleTime;
            minValues[size] = minValue;
            maxValues[size] = maxValue;
            size++;
            dirty = true;
        }

        private void downsample() {
            if (size <= 1) {
                return;
            }
            int newSize = 0;
            for (int src = 0; src < size; src += 2) {
                int src2 = src + 1;
                if (src2 < size) {
                    time[newSize] = time[src];
                    minValues[newSize] = Math.min(minValues[src], minValues[src2]);
                    maxValues[newSize] = Math.max(maxValues[src], maxValues[src2]);
                } else {
                    time[newSize] = time[src];
                    minValues[newSize] = minValues[src];
                    maxValues[newSize] = maxValues[src];
                }
                newSize++;
            }
            size = newSize;
            if (sampleInterval > 0) {
                sampleInterval *= 2;
            }
            dirty = true;
        }

        private SeriesSnapshot snapshot() {
            if (!dirty && cachedSnapshot != null) {
                return cachedSnapshot;
            }
            double[] times = Arrays.copyOf(time, size);
            double[] mins = Arrays.copyOf(minValues, size);
            double[] maxs = Arrays.copyOf(maxValues, size);
            double[] values = new double[size];
            for (int i = 0; i < size; i++) {
                values[i] = (mins[i] + maxs[i]) * 0.5;
            }
            cachedSnapshot = new SeriesSnapshot(name, times, values, mins, maxs);
            dirty = false;
            return cachedSnapshot;
        }
    }

    private final Map<String, Series> seriesByName = new LinkedHashMap<String, Series>();
    private Set<String> trackedVariableNames = new LinkedHashSet<String>();
    private double lastCaptureTime = Double.NaN;

    public void clear() {
        seriesByName.clear();
        trackedVariableNames.clear();
        lastCaptureTime = Double.NaN;
    }

    public void clearVariableSeries() {
        clearSeriesWithPrefix(VARIABLE_KEY_PREFIX);
    }

    public void clearSeries(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        seriesByName.remove(key);
    }

    public void clearSeriesWithPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        java.util.ArrayList<String> toRemove = new java.util.ArrayList<String>();
        for (String key : seriesByName.keySet()) {
            if (key.startsWith(prefix)) {
                toRemove.add(key);
            }
        }
        for (int i = 0; i < toRemove.size(); i++) {
            seriesByName.remove(toRemove.get(i));
        }
    }

    public void clearNonVariableSeries() {
        java.util.ArrayList<String> toRemove = new java.util.ArrayList<String>();
        for (String key : seriesByName.keySet()) {
            if (!key.startsWith(VARIABLE_KEY_PREFIX)) {
                toRemove.add(key);
            }
        }
        for (int i = 0; i < toRemove.size(); i++) {
            seriesByName.remove(toRemove.get(i));
        }
    }

    public void refreshTrackedVariableNames(CirSim sim) {
        trackedVariableNames = new LinkedHashSet<String>(VariableCatalog.collectVariableNames(sim));
    }

    public void capture(CirSim sim, double sampleTime) {
        if (sim == null) {
            return;
        }
        if (!Double.isNaN(lastCaptureTime) && sampleTime + TIME_RESET_TOLERANCE < lastCaptureTime) {
            clearVariableSeries();
        }
        if (trackedVariableNames.isEmpty()) {
            refreshTrackedVariableNames(sim);
        }
        for (String name : trackedVariableNames) {
            captureVariableSample(name, sampleTime, sim.resolveSlotValueForUi(name));
        }
        lastCaptureTime = sampleTime;
    }

    public boolean hasHistory(String name) {
        Series series = seriesByName.get(name);
        return series != null && series.size > 0;
    }

    public boolean hasVariableHistory(String name) {
        Series series = seriesByName.get(makeVariableKey(name));
        return series != null && series.size > 0;
    }

    public SeriesSnapshot getSeriesSnapshot(String name) {
        return getSeriesSnapshotByKey(name);
    }

    public SeriesSnapshot getVariableSeriesSnapshot(String name) {
        return getSeriesSnapshotByKey(makeVariableKey(name));
    }

    public SeriesSnapshot getSeriesSnapshotByKey(String key) {
        Series series = seriesByName.get(key);
        return series != null ? series.snapshot() : null;
    }

    public int getTrackedSeriesCount() {
        return seriesByName.size();
    }

    public void captureVariableSample(String name, double sampleTime, double value) {
        captureSeriesSample(makeVariableKey(name), name, sampleTime, value, value);
    }

    public void captureSeriesSample(String key, String displayName, double sampleTime, double value) {
        captureSeriesSample(key, displayName, sampleTime, value, value);
    }

    public void captureSeriesSample(String key, String displayName, double sampleTime, double minValue, double maxValue) {
        if (key == null || key.isEmpty()) {
            return;
        }
        Series series = seriesByName.get(key);
        if (series == null) {
            series = new Series(displayName != null ? displayName : key);
            seriesByName.put(key, series);
        } else if (displayName != null && !displayName.isEmpty()) {
            series.name = displayName;
        }
        series.append(sampleTime, minValue, maxValue);
    }

    void captureSample(String name, double sampleTime, double value) {
        captureSeriesSample(name, name, sampleTime, value, value);
    }

    private static String makeVariableKey(String name) {
        return VARIABLE_KEY_PREFIX + name;
    }
}