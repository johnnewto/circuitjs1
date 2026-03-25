package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lushprojects.circuitjs1.client.util.Rectangle;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ScopePerformanceSmokeTest extends CircuitJavaSimTestBase {

    @Test
    void scopeTimeStepThroughputStaysWithinSmokeBudget() throws Exception {
        loadCircuit("src/com/lushprojects/circuitjs1/public/circuits/economics/lrc.txt");
        Scope scope = new Scope(sim);
        initializeScopeValue(scope, Scope.VAL_CURRENT, sim.getElm(0));
        scope.setRectForEmbedded(new Rectangle(0, 0, 320, 160));
        scope.setSpeed(1);

        long start = System.nanoTime();
        for (int i = 0; i < 5000; i++) {
            scope.timeStepForEmbedded();
            sim.setTime(sim.getTime() + sim.getMaxTimeStep());
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(elapsedMs < 15000, "Scope timestep smoke budget exceeded: " + elapsedMs + "ms");
    }

    private void initializeScopeValue(Scope scope, int value, CircuitElm elm) {
        try {
            Method setValue = Scope.class.getDeclaredMethod("setValue", int.class, CircuitElm.class);
            setValue.setAccessible(true);
            setValue.invoke(scope, value, elm);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize scope value for performance test", e);
        }
    }
}
