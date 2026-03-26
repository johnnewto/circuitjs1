package com.lushprojects.circuitjs1.client.scope;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lushprojects.circuitjs1.client.CircuitJavaSimTestBase;
import com.lushprojects.circuitjs1.client.util.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Vector;
import org.junit.jupiter.api.Test;

class ScopeYAxisBaselineTest extends CircuitJavaSimTestBase {

    @Test
    void normalModeAutoScaleExpandsInPowersOfTwo() throws Exception {
        Scope scope = new Scope(sim);
        scope.setRectForEmbedded(new Rectangle(0, 0, 240, 120));
        scope.setSpeed(64);

        ScopePlot plot = createSyntheticPlot(scope, Scope.UNITS_V, 256, 220);
        fillRange(plot, -2.1, 3.2);
        setScale(scope, Scope.UNITS_V, 1.0);

        Method calcPlotScale = Scope.class.getDeclaredMethod("calcPlotScale", ScopePlot.class);
        calcPlotScale.setAccessible(true);
        calcPlotScale.invoke(scope, plot);

        assertEquals(4.0, getScale(scope, Scope.UNITS_V), 1e-12);
    }

    @Test
    void multiLhsAxisRangeUsesNiceTickStep() throws Exception {
        Scope scope = new Scope(sim);
        scope.setRectForEmbedded(new Rectangle(0, 0, 240, 120));
        scope.setSpeed(64);

        ScopePlot plot = createSyntheticPlot(scope, Scope.UNITS_V, 256, 220);
        fillRange(plot, 0.12, 1.93);

        Method calcMulti = Scope.class.getDeclaredMethod("calcMultiLhsAxisRange",
                ScopePlot.class, double[].class, double[].class);
        calcMulti.setAccessible(true);
        double[] axis = (double[]) calcMulti.invoke(scope, plot, plot.minValues, plot.maxValues);

        assertArrayEquals(new double[] {0.0, 2.0, 0.5}, axis, 1e-12);
    }

    private ScopePlot createSyntheticPlot(Scope scope, int units, int pointCount, int samplesCaptured) throws Exception {
        ScopePlot plot = new ScopePlot(null, units);
        plot.minValues = new double[pointCount];
        plot.maxValues = new double[pointCount];
        plot.ptr = 0;
        plot.samplesCaptured = samplesCaptured;

        setIntField(scope, "scopePointCount", pointCount);
        setIntField(plot, "scopePointCount", pointCount);

        Vector<ScopePlot> plots = new Vector<ScopePlot>();
        plots.add(plot);
        setField(scope, "plots", plots);
        setField(scope, "visiblePlots", plots);
        return plot;
    }

    private void fillRange(ScopePlot plot, double min, double max) {
        for (int i = 0; i < plot.minValues.length; i++) {
            plot.minValues[i] = min;
            plot.maxValues[i] = max;
        }
    }

    private void setScale(Scope scope, int units, double value) throws Exception {
        Field scaleField = Scope.class.getDeclaredField("scale");
        scaleField.setAccessible(true);
        double[] scale = (double[]) scaleField.get(scope);
        scale[units] = value;
    }

    private double getScale(Scope scope, int units) throws Exception {
        Field scaleField = Scope.class.getDeclaredField("scale");
        scaleField.setAccessible(true);
        double[] scale = (double[]) scaleField.get(scope);
        return scale[units];
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }
}
