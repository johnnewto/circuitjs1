package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

import com.lushprojects.circuitjs1.client.util.FFT;
import com.lushprojects.circuitjs1.client.util.Graphics;

final class ScopeFFTHelper {
    private ScopeFFTHelper() {
    }

    static FFT drawSpectrum(
            Graphics g,
            FFT fft,
            int scopePointCount,
            ScopePlot plot,
            int plotWidth,
            int rectHeight,
            boolean logSpectrum,
            double scaleValue) {
        FFT activeFft = fft;
        if (activeFft == null || activeFft.getSize() != scopePointCount) {
            activeFft = new FFT(scopePointCount);
        }

        double[] real = new double[scopePointCount];
        double[] imag = new double[scopePointCount];
        double[] maxV = plot.maxValues;
        double[] minV = plot.minValues;
        int ptr = plot.ptr;
        for (int i = 0; i < scopePointCount; i++) {
            int ii = (ptr - i + scopePointCount) & (scopePointCount - 1);
            real[i] = .5 * (maxV[ii] + minV[ii]);
            imag[i] = 0;
        }

        activeFft.fft(real, imag, true);
        g.setColor("#FF0000");
        if (!logSpectrum) {
            drawLinearSpectrum(g, activeFft, real, imag, scopePointCount, plotWidth, rectHeight);
        } else {
            drawLogSpectrum(g, activeFft, real, imag, scopePointCount, plotWidth, rectHeight, scaleValue);
        }
        return activeFft;
    }

    private static void drawLinearSpectrum(
            Graphics g,
            FFT fft,
            double[] real,
            double[] imag,
            int scopePointCount,
            int plotWidth,
            int rectHeight) {
        double maxM = 1e-8;
        for (int i = 0; i < scopePointCount / 2; i++) {
            double m = fft.magnitude(real[i], imag[i]);
            if (m > maxM) {
                maxM = m;
            }
        }

        int prevX = 0;
        int prevHeight = 0;
        int y = (rectHeight - 1) - 12;
        for (int i = 0; i < scopePointCount / 2; i++) {
            int x = 2 * i * plotWidth / scopePointCount;
            double magnitude = fft.magnitude(real[i], imag[i]);
            int height = (int) ((magnitude * y) / maxM);
            if (x != prevX) {
                g.drawLine(prevX, y - prevHeight, x, y - height);
            }
            prevHeight = height;
            prevX = x;
        }
    }

    private static void drawLogSpectrum(
            Graphics g,
            FFT fft,
            double[] real,
            double[] imag,
            int scopePointCount,
            int plotWidth,
            int rectHeight,
            double scaleValue) {
        int prevX = 0;
        int y0 = 5;
        int prevY = 0;
        double ymult = rectHeight / 10.0;
        double val0 = Math.log(scaleValue) * ymult;
        for (int i = 0; i < scopePointCount / 2; i++) {
            int x = 2 * i * plotWidth / scopePointCount;
            double val = Math.log(fft.magnitude(real[i], imag[i]));
            int y = y0 - (int) (val * ymult - val0);
            if (x != prevX) {
                g.drawLine(prevX, prevY, x, y);
            }
            prevY = y;
            prevX = x;
        }
    }
}
