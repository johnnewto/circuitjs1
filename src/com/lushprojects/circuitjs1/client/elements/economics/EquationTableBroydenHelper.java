package com.lushprojects.circuitjs1.client.elements.economics;

/**
 * Small pure-Java helper for Broyden-style Jacobian updates used by EquationTable rows.
 */
final class EquationTableBroydenHelper {
    static final int DEFAULT_REFRESH_INTERVAL = 4;

    private static final double MIN_UPDATE_DENOM = 1e-18;

    private EquationTableBroydenHelper() {
    }

    static final class UpdateResult {
        final boolean cacheCompatible;
        final boolean updated;

        UpdateResult(boolean cacheCompatible, boolean updated) {
            this.cacheCompatible = cacheCompatible;
            this.updated = updated;
        }
    }

    static boolean hasCompatibleCache(String[] cachedRefNames, double[] cachedGradient,
            double[] cachedX, String[] refNames) {
        if (cachedRefNames == null || cachedGradient == null || cachedX == null || refNames == null) {
            return false;
        }
        if (cachedRefNames.length != refNames.length
                || cachedGradient.length != refNames.length
                || cachedX.length != refNames.length) {
            return false;
        }
        for (int i = 0; i < refNames.length; i++) {
            String expected = refNames[i];
            String actual = cachedRefNames[i];
            if (expected == null ? actual != null : !expected.equals(actual)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Apply the scalar good-Broyden update in-place to a gradient vector.
     *
     * <p>For scalar $f(x)$ and row-vector Jacobian $g$, the update is:</p>
     *
     * $$
     * g_{k+1} = g_k + \frac{\Delta f - g_k \cdot \Delta x}{\Delta x \cdot \Delta x} \Delta x
     * $$
     */
    static UpdateResult applyGoodBroydenUpdate(double[] gradient,
            double[] previousX,
            double previousF,
            double[] currentX,
            double currentF) {
        if (gradient == null || previousX == null || currentX == null) {
            return new UpdateResult(false, false);
        }
        if (gradient.length != previousX.length || gradient.length != currentX.length) {
            return new UpdateResult(false, false);
        }

        double denom = 0;
        double predictedDelta = 0;
        double[] dx = new double[currentX.length];
        for (int i = 0; i < currentX.length; i++) {
            dx[i] = currentX[i] - previousX[i];
            denom += dx[i] * dx[i];
            predictedDelta += gradient[i] * dx[i];
        }

        if (denom <= MIN_UPDATE_DENOM) {
            return new UpdateResult(true, false);
        }

        double correction = (currentF - previousF - predictedDelta) / denom;
        if (Double.isNaN(correction) || Double.isInfinite(correction)) {
            return new UpdateResult(false, false);
        }

        for (int i = 0; i < gradient.length; i++) {
            gradient[i] += correction * dx[i];
            if (Double.isNaN(gradient[i]) || Double.isInfinite(gradient[i])) {
                return new UpdateResult(false, false);
            }
        }
        return new UpdateResult(true, true);
    }
}
