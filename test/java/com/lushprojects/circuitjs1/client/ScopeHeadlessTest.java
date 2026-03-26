package com.lushprojects.circuitjs1.client.scope;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.CircuitJavaSimTestBase;
import com.lushprojects.circuitjs1.client.util.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Vector;
import org.junit.jupiter.api.Test;

class ScopeHeadlessTest extends CircuitJavaSimTestBase {

    @Test
    void scopeCapturesDataInHeadlessMode() throws Exception {
        loadCircuit("src/com/lushprojects/circuitjs1/public/circuits/economics/lrc.txt");
        Scope scope = attachScopeToFirstElement();
        scope.setRectForEmbedded(new Rectangle(0, 0, 240, 120));
        scope.setSpeed(1);
        ScopePlot plot = getFirstPlot(scope);
        double initialValue = plot.lastValue;
        int initialPtr = plot.ptr;

        for (int i = 0; i < 600; i++) {
            scope.timeStepForEmbedded();
            sim.setTime(sim.getTime() + sim.getMaxTimeStep());
        }

        assertNotNull(plot.minValues, "minValues buffer not initialized");
        assertNotNull(plot.maxValues, "maxValues buffer not initialized");
        assertTrue(plot.samplesCaptured > 1 || plot.ptr != initialPtr || plot.lastValue != initialValue,
                "No scope activity captured in headless mode");
    }

    private Scope attachScopeToFirstElement() {
        assertTrue(sim.elmList.size() > 0, "Loaded circuit has no elements");
        Scope scope = new Scope(sim);
        try {
            Method setValue = Scope.class.getDeclaredMethod("setValue", int.class, CircuitElm.class);
            setValue.setAccessible(true);
            setValue.invoke(scope, Scope.VAL_CURRENT, sim.getElm(0));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize scope plot for test", e);
        }
        if (sim.scopes == null || sim.scopes.length == 0) {
            sim.scopes = new Scope[20];
        }
        sim.scopes[0] = scope;
        sim.scopeCount = 1;
        return scope;
    }

    @SuppressWarnings("unchecked")
    private ScopePlot getFirstPlot(Scope scope) {
        try {
            Field plotsField = Scope.class.getDeclaredField("plots");
            plotsField.setAccessible(true);
            Vector<ScopePlot> plots = (Vector<ScopePlot>) plotsField.get(scope);
            assertNotNull(plots, "Scope plots not initialized");
            assertTrue(!plots.isEmpty(), "Scope has no plots");
            return plots.get(0);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read scope plot data", e);
        }
    }
}
