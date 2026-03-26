/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.util.Locale;

/**
 * Represents a single plot within a scope, tracking values over time.
 * Each plot can display voltage, current, power, or resistance for a circuit element.
 */
public class ScopePlot {
    // Plot flags
    public static final int FLAG_AC = 1;

    // Default values
    private static final double DEFAULT_MAN_SCALE = 1.0;
    private static final double DEFAULT_AC_ALPHA = 0.9999;
    private static final double AC_ALPHA_TIME_CONSTANT = 1.15;

    // Data storage
    double minValues[], maxValues[];
    private int scopePointCount;
    int ptr; // Pointer to the current sample in circular buffer
    int samplesCaptured; // Number of valid samples currently stored (up to scopePointCount)

    // Plot configuration
    public int value; // The property being shown (e.g., VAL_CURRENT, VAL_VOLTAGE)
    int scopePlotSpeed; // In sim timestep units per pixel
    int units; // Display units (UNITS_V, UNITS_A, UNITS_W, UNITS_OHMS)

    // State tracking
    private double lastUpdateTime;
    double lastValue;
    String color;
    public CircuitElm elm;

    // History buffers for drawFromZero mode (not circular, grows linearly)
    double historyMinValues[], historyMaxValues[];

    // Manual scale settings
    // Has a manual scale in "/div" format been set by the user?
    // If false, scale is inferred from "MaxValue" format or auto-calculated
    boolean manScaleSet = false;
    double manScale = DEFAULT_MAN_SCALE; // Units per division
    int manVPosition = 0; // 0 is center of screen. +V_POSITION_STEPS/2 is top of screen

    // Display parameters
    double gridMult;
    double plotOffset;
    double lhsAxisMin;
    double lhsAxisMax;
    double lhsAxisStep;

    // AC coupling filter
    boolean acCoupled = false;
    private double acAlpha = DEFAULT_AC_ALPHA; // Filter coefficient for AC coupling (y[i] = alpha * (y[i-1] + x[i] - x[i-1]))
    private double acLastOut = 0; // Store y[i-1] term for AC coupling filter

    /**
     * Creates a new ScopePlot for the given element and units.
     * @param e The circuit element to monitor
     * @param u The units for display (UNITS_V, UNITS_A, etc.)
     */
    public ScopePlot(CircuitElm e, int u) {
        elm = e;
        units = u;
    }

    /**
     * Creates a new ScopePlot with manual scale settings.
     * @param e The circuit element to monitor
     * @param u The units for display
     * @param v The value type to display (VAL_VOLTAGE, VAL_CURRENT, etc.)
     * @param manS Manual scale value (units per division)
     */
    public ScopePlot(CircuitElm e, int u, int v, double manS) {
        elm = e;
        units = u;
        value = v;
        manScale = manS;
        // Ohms can only be positive, so move the v position to the bottom.
        // Power can be negative for caps and inductors, but still move to the bottom (for backward compatibility)
        if (units == Scope.UNITS_OHMS || units == Scope.UNITS_W)
            manVPosition = -Scope.V_POSITION_STEPS / 2;
    }

    /**
     * Returns the starting index in the circular buffer for displaying the given width.
     * @param w Width in pixels to display
     * @return Starting index in circular buffer
     */
    int startIndex(int w) {
        int displayWidth = getDisplayWidth(w);
        if (displayWidth <= 0)
            return 0;
        if (samplesCaptured < w)
            return 0;
        return ptr + scopePointCount - displayWidth;
    }

    int getDisplayWidth(int w) {
        if (w <= 0)
            return 0;
        int captured = Math.max(samplesCaptured, 1);
        return Math.min(w, captured);
    }

    /**
     * Resets the plot buffers and speed settings.
     * @param spc New scope point count (buffer size)
     * @param sp New speed (timestep units per pixel)
     * @param full If true, discard all old data; if false, preserve what fits
     */
    void reset(int spc, int sp, boolean full) {
        int oldSpc = scopePointCount;
        int oldSamplesCaptured = samplesCaptured;
        scopePointCount = spc;
        if (scopePlotSpeed != sp) {
            oldSpc = 0; // throw away old data
            oldSamplesCaptured = 0;
        }
        scopePlotSpeed = sp;
        // Adjust the time constant of the AC coupled filter in proportion to the number of samples
        // we are seeing on the scope (if my maths is right). The constant is empirically determined
        acAlpha = 1.0 - 1.0 / (AC_ALPHA_TIME_CONSTANT * scopePlotSpeed * scopePointCount);
        double oldMin[] = minValues;
        double oldMax[] = maxValues;
        minValues = new double[scopePointCount];
        maxValues = new double[scopePointCount];
        if (oldMin != null && !full && oldSpc > 0) {
            // preserve old data if possible
            int i;
            for (i = 0; i != scopePointCount && i != oldSpc; i++) {
                int i1 = (-i) & (scopePointCount - 1);
                int i2 = (ptr - i) & (oldSpc - 1);
                minValues[i1] = oldMin[i2];
                maxValues[i1] = oldMax[i2];
            }
        } else
            lastUpdateTime = CirSim.getInstance().getTime();
        ptr = 0;
        if (oldMin != null && !full && oldSpc > 0)
            samplesCaptured = Math.max(1, Math.min(scopePointCount, Math.min(oldSpc, oldSamplesCaptured)));
        else
            samplesCaptured = 1;
    }

    /**
     * Records a timestep sample for this plot.
     * Updates min/max values and applies AC coupling if enabled.
     */
    void timeStep() {
        if (elm == null)
            return;

        double v = elm.getScopeValue(value);

        // AC coupling filter: 1st order IIR high pass filter
        // Formula: y[i] = alpha × (y[i-1] + x[i] - x[i-1])
        // We calculate for all iterations (even DC coupled) to prime the data in case they switch to AC later
        double newAcOut = acAlpha * (acLastOut + v - lastValue);
        lastValue = v;
        acLastOut = newAcOut;

        if (isAcCoupled())
            v = newAcOut;

        // Update min/max for current sample point
        if (v < minValues[ptr])
            minValues[ptr] = v;
        if (v > maxValues[ptr])
            maxValues[ptr] = v;

        // Advance to next sample point if enough time has elapsed
        if (CirSim.getInstance().getTime() - lastUpdateTime >= CirSim.getInstance().getMaxTimeStep() * scopePlotSpeed) {
            ptr = (ptr + 1) & (scopePointCount - 1);
            minValues[ptr] = maxValues[ptr] = v;
            if (samplesCaptured < scopePointCount)
                samplesCaptured++;
            lastUpdateTime += CirSim.getInstance().getMaxTimeStep() * scopePlotSpeed;
        }
    }

    String getUnitText(double v) {
        switch (units) {
            case Scope.UNITS_V:
                return CircuitElm.getVoltageText(v);
            case Scope.UNITS_A:
                return CircuitElm.getCurrentText(v);
            case Scope.UNITS_OHMS:
                return CircuitElm.getUnitText(v, Locale.ohmString);
            case Scope.UNITS_W:
                return CircuitElm.getUnitText(v, "W");
        }
        return null;
    }

    // Color palette for multiple plots
    private static final String[] colors = {
            "#FF0000", "#FF8000", "#FF00FF", "#7F00FF",
            "#0000FF", "#0080FF", "#FFFF00", "#00FFFF",
    };

    /**
     * Assigns a color to this plot based on its type and count.
     * @param count Plot count (0 for default color based on units, >0 for cycling through palette)
     */
    void assignColor(int count) {
        if (count > 0) {
            color = colors[(count - 1) % colors.length];
            return;
        }
        // Default colors based on units
        switch (units) {
            case Scope.UNITS_V:
                color = CircuitElm.positiveColor.getHexValue();
                break;
            case Scope.UNITS_A:
                color = (CirSim.getInstance().printableCheckItem.getState()) ? "#A0A000" : "#FFFF00";
                break;
            default:
                color = (CirSim.getInstance().printableCheckItem.getState()) ? "#000000" : "#FFFFFF";
                break;
        }
    }

    /**
     * Enables or disables AC coupling for this plot.
     * AC coupling only works for voltage plots.
     * @param b true to enable AC coupling
     */
    public void setAcCoupled(boolean b) {
        if (canAcCouple()) {
            acCoupled = b;
        } else
            acCoupled = false;
    }

    /**
     * Checks if AC coupling is allowed for this plot.
     * @return true if this plot displays voltage
     */
    boolean canAcCouple() {
        return units == Scope.UNITS_V;
    }

    /**
     * Checks if AC coupling is currently enabled.
     * @return true if AC coupled
     */
    boolean isAcCoupled() {
        return acCoupled;
    }

    /**
     * Returns serialization flags for this plot.
     * @return Bit flags representing plot state
     */
    int getPlotFlags() {
        return (acCoupled ? FLAG_AC : 0);
    }
}
