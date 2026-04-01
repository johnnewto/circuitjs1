package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramAnimationController;
import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramAnimationController.Phase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SequenceDiagramAnimationController")
class SequenceDiagramAnimationControllerTest {

    private static final int MSG_COUNT = 4;

    private SequenceDiagramAnimationController controller;

    @BeforeEach
    void setUp() {
        controller = new SequenceDiagramAnimationController();
    }

    @Test
    @DisplayName("disabled controller shows all messages")
    void disabledShowsAllMessages() {
        controller.update(0.0, true, MSG_COUNT, 0);
        assertEquals(Phase.DISABLED, controller.getPhase());
        assertFalse(controller.isAnimating());
        for (int i = 0; i < MSG_COUNT; i++) {
            assertTrue(controller.isVisible(i));
        }
    }

    @Test
    @DisplayName("enabled controller stays in live waiting mode outside manual stepping")
    void enabledControllerStaysLiveOutsideManualStep() {
        controller.loadConfig(true, 500);
        controller.update(0.0, true, MSG_COUNT, 0);
        assertEquals(Phase.WAITING, controller.getPhase());
        assertFalse(controller.isAnimating());
        assertFalse(controller.needsTimerReveal());
        for (int i = 0; i < MSG_COUNT; i++) {
            assertTrue(controller.isVisible(i));
            assertFalse(controller.isHighlighted(i));
        }
    }

    @Test
    @DisplayName("manual stepping reveals messages cumulatively")
    void manualSteppingRevealsMessagesCumulatively() {
        controller.loadConfig(true, 500);
        controller.update(0.0, false, MSG_COUNT, 0);

        assertTrue(controller.advanceManualStep());
        assertEquals(Phase.MANUAL_STEP, controller.getPhase());
        assertEquals(0, controller.getActiveIndex());
        assertTrue(controller.isVisible(0));
        assertFalse(controller.isVisible(1));
        assertTrue(controller.isHighlighted(0));

        assertTrue(controller.advanceManualStep());
        assertEquals(1, controller.getActiveIndex());
        assertTrue(controller.isVisible(0));
        assertTrue(controller.isVisible(1));
        assertFalse(controller.isVisible(2));
        assertTrue(controller.isHighlighted(1));
    }

    @Test
    @DisplayName("manual stepping releases control after the last message")
    void manualSteppingReleasesAfterLastMessage() {
        controller.loadConfig(true, 500);
        controller.update(0.0, false, 2, 0);

        assertTrue(controller.advanceManualStep());
        assertTrue(controller.advanceManualStep());
        assertFalse(controller.advanceManualStep());
        assertEquals(Phase.WAITING, controller.getPhase());
        assertEquals(-1, controller.getActiveIndex());
        assertFalse(controller.isAnimating());
        assertTrue(controller.isVisible(0));
        assertTrue(controller.isVisible(1));
    }

    @Test
    @DisplayName("simulation resume exits manual stepping and returns to live mode")
    void simulationResumeExitsManualStepping() {
        controller.loadConfig(true, 500);
        controller.update(0.0, false, MSG_COUNT, 0);
        assertTrue(controller.advanceManualStep());

        controller.update(1.0, true, MSG_COUNT, 100);
        assertEquals(Phase.WAITING, controller.getPhase());
        assertEquals(-1, controller.getActiveIndex());
        assertFalse(controller.isAnimating());
        for (int i = 0; i < MSG_COUNT; i++) {
            assertTrue(controller.isVisible(i));
        }
    }

    @Test
    @DisplayName("timed reveal methods are inert in step-only mode")
    void timedRevealMethodsAreInert() {
        controller.loadConfig(true, 500);
        controller.enterTimedMode();
        assertEquals(Phase.WAITING, controller.getPhase());
        assertFalse(controller.advanceTimedStep());
        assertFalse(controller.needsTimerReveal());
        assertFalse(controller.wantsRepaint());
    }
}
