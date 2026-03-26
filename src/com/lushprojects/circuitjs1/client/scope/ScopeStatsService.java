package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

final class ScopeStatsService {
    private ScopeStatsService() {
    }

    static boolean canShowRms(ScopePlot plot) {
        if (plot == null) {
            return false;
        }
        return plot.units == Scope.UNITS_V || plot.units == Scope.UNITS_A;
    }

    static Double computeRms(ScopePlot plot, int displayWidth, int scopePointCount, double mid) {
        int i;
        double avg = 0;
        int ipa = plot.startIndex(displayWidth);
        double[] maxV = plot.maxValues;
        double[] minV = plot.minValues;
        int state = -1;

        for (i = 0; i != displayWidth; i++) {
            int ip = (i + ipa) & (scopePointCount - 1);
            if (maxV[ip] != 0) {
                if (maxV[ip] > mid) {
                    state = 1;
                }
                break;
            }
        }
        int firstState = -state;
        int start = i;
        int end = 0;
        int waveCount = 0;
        double endAvg = 0;
        for (; i != displayWidth; i++) {
            int ip = (i + ipa) & (scopePointCount - 1);
            boolean sw = false;
            if (state == 1) {
                if (maxV[ip] < mid) {
                    sw = true;
                }
            } else if (minV[ip] > mid) {
                sw = true;
            }
            if (sw) {
                state = -state;
                if (firstState == state) {
                    if (waveCount == 0) {
                        start = i;
                        firstState = state;
                        avg = 0;
                    }
                    waveCount++;
                    end = i;
                    endAvg = avg;
                }
            }
            if (waveCount > 0) {
                double m = (maxV[ip] + minV[ip]) * .5;
                avg += m * m;
            }
        }
        if (waveCount > 1) {
            return Math.sqrt(endAvg / (end - start));
        }
        return null;
    }

    static Double computeAverage(ScopePlot plot, int displayWidth, int scopePointCount, double mid) {
        int i;
        double avg = 0;
        int ipa = plot.startIndex(displayWidth);
        double[] maxV = plot.maxValues;
        double[] minV = plot.minValues;
        int state = -1;

        for (i = 0; i != displayWidth; i++) {
            int ip = (i + ipa) & (scopePointCount - 1);
            if (maxV[ip] != 0) {
                if (maxV[ip] > mid) {
                    state = 1;
                }
                break;
            }
        }
        int firstState = -state;
        int start = i;
        int end = 0;
        int waveCount = 0;
        double endAvg = 0;
        for (; i != displayWidth; i++) {
            int ip = (i + ipa) & (scopePointCount - 1);
            boolean sw = false;
            if (state == 1) {
                if (maxV[ip] < mid) {
                    sw = true;
                }
            } else if (minV[ip] > mid) {
                sw = true;
            }
            if (sw) {
                state = -state;
                if (firstState == state) {
                    if (waveCount == 0) {
                        start = i;
                        firstState = state;
                        avg = 0;
                    }
                    waveCount++;
                    end = i;
                    endAvg = avg;
                }
            }
            if (waveCount > 0) {
                double m = (maxV[ip] + minV[ip]) * .5;
                avg += m;
            }
        }
        if (waveCount > 1) {
            return endAvg / (end - start);
        }
        return null;
    }

    static Integer computeDutyCyclePercent(ScopePlot plot, int displayWidth, int scopePointCount, double mid) {
        int i;
        int ipa = plot.startIndex(displayWidth);
        double[] maxV = plot.maxValues;
        double[] minV = plot.minValues;
        int state = -1;

        for (i = 0; i != displayWidth; i++) {
            int ip = (i + ipa) & (scopePointCount - 1);
            if (maxV[ip] != 0) {
                if (maxV[ip] > mid) {
                    state = 1;
                }
                break;
            }
        }
        int firstState = 1;
        int start = i;
        int end = 0;
        int waveCount = 0;
        int dutyLen = 0;
        int middle = 0;
        for (; i != displayWidth; i++) {
            int ip = (i + ipa) & (scopePointCount - 1);
            boolean sw = false;
            if (state == 1) {
                if (maxV[ip] < mid) {
                    sw = true;
                }
            } else if (minV[ip] > mid) {
                sw = true;
            }
            if (sw) {
                state = -state;
                if (firstState == state) {
                    if (waveCount == 0) {
                        start = end = i;
                    } else {
                        end = start;
                        start = i;
                        dutyLen = end - middle;
                    }
                    waveCount++;
                } else {
                    middle = i;
                }
            }
        }
        if (waveCount > 1 && end != start) {
            return 100 * dutyLen / (end - start);
        }
        return null;
    }

    static Double computeFrequency(ScopePlot plot, int displayWidth, int scopePointCount, double maxTimeStep, int speed) {
        double avg = 0;
        int ipa = plot.startIndex(displayWidth);
        double[] minV = plot.minValues;
        double[] maxV = plot.maxValues;
        int i;
        for (i = 0; i != displayWidth; i++) {
            int ip = (i + ipa) & (scopePointCount - 1);
            avg += minV[ip] + maxV[ip];
        }
        avg /= i * 2;
        int state = 0;
        double thresh = avg * .05;
        int oi = 0;
        double avperiod = 0;
        int periodct = -1;
        double avperiod2 = 0;
        for (i = 0; i != displayWidth; i++) {
            int ip = (i + ipa) & (scopePointCount - 1);
            double q = maxV[ip] - avg;
            int os = state;
            if (q < thresh) {
                state = 1;
            } else if (q > -thresh) {
                state = 2;
            }
            if (state == 2 && os == 1) {
                int pd = i - oi;
                oi = i;
                if (pd < 12) {
                    continue;
                }
                if (periodct >= 0) {
                    avperiod += pd;
                    avperiod2 += pd * pd;
                }
                periodct++;
            }
        }
        if (periodct <= 0) {
            return null;
        }
        avperiod /= periodct;
        avperiod2 /= periodct;
        double periodstd = Math.sqrt(avperiod2 - avperiod * avperiod);
        double freq = 1 / (avperiod * maxTimeStep * speed);
        if (periodct < 1 || periodstd > 2) {
            return null;
        }
        return freq;
    }
}
