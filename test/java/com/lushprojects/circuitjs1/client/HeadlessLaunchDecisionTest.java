package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("HeadlessLaunchDecision")
class HeadlessLaunchDecisionTest {

    @Test
    @DisplayName("uses smaller default steps when dump key is present and steps is omitted")
    void usesSmallerDefaultStepsForDumpKey() {
        int defaultSteps = HeadlessLaunchDecision.resolveDefaultSteps("headlessDump-123", null);
        assertEquals(200, defaultSteps);
    }

    @Test
    @DisplayName("uses standard default steps when dump key is absent")
    void usesStandardDefaultStepsWithoutDumpKey() {
        int defaultSteps = HeadlessLaunchDecision.resolveDefaultSteps(null, null);
        assertEquals(1000, defaultSteps);
    }

    @Test
    @DisplayName("routes to embedded text when start circuit text exists")
    void routesToEmbeddedTextWhenTextExists() {
        HeadlessLaunchDecision.ImmediateRoute route =
            HeadlessLaunchDecision.resolveImmediateRoute("$ test circuit", "headlessDump-123");
        assertEquals(HeadlessLaunchDecision.ImmediateRoute.EMBEDDED_TEXT, route);
    }

    @Test
    @DisplayName("routes to missing dump key when key exists but no text was loaded")
    void routesToMissingDumpKeyWhenTextMissing() {
        HeadlessLaunchDecision.ImmediateRoute route =
            HeadlessLaunchDecision.resolveImmediateRoute(null, "headlessDump-123");
        assertEquals(HeadlessLaunchDecision.ImmediateRoute.MISSING_DUMP_KEY, route);
    }
}
