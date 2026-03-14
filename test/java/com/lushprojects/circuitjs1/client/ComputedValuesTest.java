package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComputedValuesTest {

    @BeforeEach
    void setUp() {
        ComputedValues.clearComputedValues();
        ComputedValues.setDoubleBufferingEnabled(true);
        ComputedValues.clearPendingValues();
        ComputedValues.resetComputedFlags();
    }

    @Test
    void testDoubleBufferingRequiresCommitForVisibility() {
        ComputedValues.setComputedValue("X", 1.0);

        assertNull(ComputedValues.getComputedValue("X"), "Current buffer should not see pending value before commit");
        assertEquals(1.0, ComputedValues.getPendingValue("X"), 1e-12);

        ComputedValues.commitPendingToCurrentValues();
        assertEquals(1.0, ComputedValues.getComputedValue("X"), 1e-12);

        ComputedValues.setComputedValue("X", 2.0);
        assertEquals(1.0, ComputedValues.getComputedValue("X"), 1e-12,
                "Current value should remain stable until next commit");

        ComputedValues.commitPendingToCurrentValues();
        assertEquals(2.0, ComputedValues.getComputedValue("X"), 1e-12);
    }

    @Test
    void testConvergedCommitIsSeparateFromCurrentCommit() {
        ComputedValues.setComputedValue("Y", 10.0);
        ComputedValues.commitPendingToCurrentValues();
        ComputedValues.commitConvergedValues();

        assertEquals(10.0, ComputedValues.getConvergedValue("Y"), 1e-12);

        ComputedValues.setComputedValue("Y", 20.0);
        ComputedValues.commitPendingToCurrentValues();

        assertEquals(20.0, ComputedValues.getComputedValue("Y"), 1e-12);
        assertEquals(10.0, ComputedValues.getConvergedValue("Y"), 1e-12,
                "Converged value should only update after commitConvergedValues()");

        ComputedValues.commitConvergedValues();
        assertEquals(20.0, ComputedValues.getConvergedValue("Y"), 1e-12);
    }

    @Test
    void testImmediateModeBypassesPendingBuffer() {
        ComputedValues.setDoubleBufferingEnabled(false);

        ComputedValues.setComputedValue("Z", 7.0);
        assertEquals(7.0, ComputedValues.getComputedValue("Z"), 1e-12);
        assertNull(ComputedValues.getPendingValue("Z"),
                "Immediate mode should not use pending buffer");
    }
}
