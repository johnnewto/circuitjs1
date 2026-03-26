package com.lushprojects.circuitjs1.client.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.CircuitJavaSimTestBase;
import com.lushprojects.circuitjs1.client.util.Rectangle;
import com.lushprojects.circuitjs1.client.util.StringTokenizer;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ScopeSerializationRoundTripTest extends CircuitJavaSimTestBase {

    @Test
    void scopeDumpUndumpPreservesFlagsPlotsAndBindings() throws Exception {
        loadCircuit("src/com/lushprojects/circuitjs1/public/circuits/economics/lrc.txt");
        assertTrue(sim.elmList.size() > 1, "Test circuit needs at least two elements");

        Scope original = new Scope(sim);
        initializeScopeValue(original, Scope.VAL_CURRENT, sim.getElm(0));
        int voltageUnits = Scope.UNITS_V;
        original.addPlot(
                sim.getElm(1),
                voltageUnits,
                Scope.VAL_VOLTAGE,
                original.getManScaleFromMaxScale(voltageUnits, false));
        original.calcVisiblePlots();
        original.setRectForEmbedded(new Rectangle(0, 0, 240, 120));
        original.setText("Scope Label");
        original.setTitle("Scope Title");
        original.handleMenu("drawfromzero", true);
        original.handleMenu("autoscaletime", true);

        String dump = original.dumpForEmbedded();
        assertNotNull(dump);

        Scope restored = new Scope(sim);
        StringTokenizer st = new StringTokenizer(dump);
        assertEquals("o", st.nextToken());
        restored.undumpForEmbedded(st);

        assertEquals(original.getFlags(), restored.getFlags());
        assertEquals(original.getPlotCount(), restored.getPlotCount());
        assertEquals(original.getText(), restored.getText());
        assertEquals(original.getTitle(), restored.getTitle());
        for (int i = 0; i < original.getPlotCount(); i++) {
            assertEquals(original.getPlotValue(i), restored.getPlotValue(i));
            assertEquals(original.getPlotElement(i), restored.getPlotElement(i));
        }
    }

    private void initializeScopeValue(Scope scope, int value, CircuitElm elm) {
        try {
            Method setValue = Scope.class.getDeclaredMethod("setValue", int.class, CircuitElm.class);
            setValue.setAccessible(true);
            setValue.invoke(scope, value, elm);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize scope value for serialization test", e);
        }
    }
}
