package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("RunnerLaunchDecision default and route behavior")
class RunnerLaunchDecisionLegacyCompatTest {

    @Test
    @DisplayName("uses smaller default steps when dump key is present and steps is omitted")
    void usesSmallerDefaultStepsForDumpKey() {
        int defaultSteps = RunnerLaunchDecision.resolveDefaultSteps("runnerDump-123", null);
        assertEquals(200, defaultSteps);
    }

    @Test
    @DisplayName("uses standard default steps when dump key is absent")
    void usesStandardDefaultStepsWithoutDumpKey() {
        int defaultSteps = RunnerLaunchDecision.resolveDefaultSteps(null, null);
        assertEquals(1000, defaultSteps);
    }

    @Test
    @DisplayName("routes to embedded text when start circuit text exists")
    void routesToEmbeddedTextWhenTextExists() {
        RunnerLaunchDecision.ImmediateRoute route =
            RunnerLaunchDecision.resolveImmediateRoute("$ test circuit", "runnerDump-123");
        assertEquals(RunnerLaunchDecision.ImmediateRoute.EMBEDDED_TEXT, route);
    }

    @Test
    @DisplayName("routes to missing dump key when key exists but no text was loaded")
    void routesToMissingDumpKeyWhenTextMissing() {
        RunnerLaunchDecision.ImmediateRoute route =
            RunnerLaunchDecision.resolveImmediateRoute(null, "runnerDump-123");
        assertEquals(RunnerLaunchDecision.ImmediateRoute.MISSING_DUMP_KEY, route);
    }
}
