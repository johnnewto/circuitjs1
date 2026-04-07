package com.lushprojects.circuitjs1.client.elements.economics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EquationTableBroydenHelper — quasi-Newton gradient updates")
class EquationTableBroydenHelperTest {

    @Test
    @DisplayName("compatible cache requires identical ordered reference names")
    void compatibleCacheRequiresMatchingRefOrder() {
        assertTrue(EquationTableBroydenHelper.hasCompatibleCache(
                new String[] {"A", "B"},
                new double[] {1.0, 2.0},
                new double[] {3.0, 4.0},
                new String[] {"A", "B"}));

        assertFalse(EquationTableBroydenHelper.hasCompatibleCache(
                new String[] {"B", "A"},
                new double[] {1.0, 2.0},
                new double[] {3.0, 4.0},
                new String[] {"A", "B"}));
    }

    @Test
    @DisplayName("good Broyden update enforces latest secant condition")
    void goodBroydenUpdateEnforcesSecantCondition() {
        double[] gradient = {0.0, 0.0};
        double[] previousX = {1.0, 2.0};
        double[] currentX = {3.0, 5.0};

        EquationTableBroydenHelper.UpdateResult result = EquationTableBroydenHelper.applyGoodBroydenUpdate(
                gradient,
                previousX,
                4.0,
                currentX,
                11.0);

        assertTrue(result.cacheCompatible);
        assertTrue(result.updated);

        double[] dx = {currentX[0] - previousX[0], currentX[1] - previousX[1]};
        double secantDelta = gradient[0] * dx[0] + gradient[1] * dx[1];
        org.junit.jupiter.api.Assertions.assertEquals(7.0, secantDelta, 1e-12);
    }

    @Test
    @DisplayName("tiny state changes reuse cached gradient without modifying it")
    void tinyStateChangesReuseGradient() {
        double[] gradient = {1.5, -2.0};
        double[] original = {1.5, -2.0};

        EquationTableBroydenHelper.UpdateResult result = EquationTableBroydenHelper.applyGoodBroydenUpdate(
                gradient,
                new double[] {10.0, 20.0},
                3.0,
                new double[] {10.0, 20.0},
                3.0);

        assertTrue(result.cacheCompatible);
        assertFalse(result.updated);
        assertArrayEquals(original, gradient, 1e-12);
    }
}
