package com.lushprojects.circuitjs1.client;

final class HeadlessLaunchDecision {

    enum ImmediateRoute {
        EMBEDDED_TEXT,
        MISSING_DUMP_KEY,
        NONE
    }

    private HeadlessLaunchDecision() {
    }

    static int resolveDefaultSteps(String headlessDumpKey, String stepsValue) {
        return (headlessDumpKey != null && stepsValue == null) ? 200 : 1000;
    }

    static ImmediateRoute resolveImmediateRoute(String startCircuitText, String headlessDumpKey) {
        if (startCircuitText != null && !startCircuitText.isEmpty()) {
            return ImmediateRoute.EMBEDDED_TEXT;
        }
        if (headlessDumpKey != null) {
            return ImmediateRoute.MISSING_DUMP_KEY;
        }
        return ImmediateRoute.NONE;
    }
}
