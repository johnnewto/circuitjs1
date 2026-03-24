package com.lushprojects.circuitjs1.client;


final class RunnerLaunchDecision {

    enum ImmediateRoute {
        EMBEDDED_TEXT,
        MISSING_DUMP_KEY,
        NONE
    }

    private RunnerLaunchDecision() {
    }

    static int resolveDefaultSteps(String runnerDumpKey, String stepsValue) {
        return (runnerDumpKey != null && stepsValue == null) ? 200 : 1000;
    }

    static ImmediateRoute resolveImmediateRoute(String startCircuitText, String runnerDumpKey) {
        if (startCircuitText != null && !startCircuitText.isEmpty()) {
            return ImmediateRoute.EMBEDDED_TEXT;
        }
        if (runnerDumpKey != null) {
            return ImmediateRoute.MISSING_DUMP_KEY;
        }
        return ImmediateRoute.NONE;
    }
}
