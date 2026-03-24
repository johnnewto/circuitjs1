package com.lushprojects.circuitjs1.client.runner;


public final class RunnerLaunchDecision {

    public enum ImmediateRoute {
        EMBEDDED_TEXT,
        MISSING_DUMP_KEY,
        NONE
    }

    private RunnerLaunchDecision() {
    }

    public static int resolveDefaultSteps(String runnerDumpKey, String stepsValue) {
        return (runnerDumpKey != null && stepsValue == null) ? 200 : 1000;
    }

    public static ImmediateRoute resolveImmediateRoute(String startCircuitText, String runnerDumpKey) {
        if (startCircuitText != null && !startCircuitText.isEmpty()) {
            return ImmediateRoute.EMBEDDED_TEXT;
        }
        if (runnerDumpKey != null) {
            return ImmediateRoute.MISSING_DUMP_KEY;
        }
        return ImmediateRoute.NONE;
    }
}
