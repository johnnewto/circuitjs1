package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramAnimationController;
import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramAnimationController.Phase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SequenceDiagramAnimationController.
 * 
 * Tests the animation state machine that controls how sequence diagram
 * messages are revealed over time.
 */
@DisplayName("SequenceDiagramAnimationController")
class SequenceDiagramAnimationControllerTest {

    private SequenceDiagramAnimationController controller;
    
    // Default test values
    private static final int STEP_MS = 500;  // Time per step
    private static final int MSG_COUNT = 4;
    
    @BeforeEach
    void setUp() {
        controller = new SequenceDiagramAnimationController();
    }
    
    @Nested
    @DisplayName("Initial State")
    class InitialState {
        
        @Test
        @DisplayName("starts in DISABLED phase")
        void startsDisabled() {
            assertEquals(Phase.DISABLED, controller.getPhase());
        }
        
        @Test
        @DisplayName("animation not enabled by default")
        void notEnabledByDefault() {
            assertFalse(controller.isEnabled());
        }
        
        @Test
        @DisplayName("not animating when disabled")
        void notAnimatingWhenDisabled() {
            assertFalse(controller.isAnimating());
        }
        
        @Test
        @DisplayName("all messages visible when disabled")
        void allVisibleWhenDisabled() {
            controller.update(0.0, true, MSG_COUNT, 0);
            for (int i = 0; i < MSG_COUNT; i++) {
                assertTrue(controller.isVisible(i), "Message " + i + " should be visible when disabled");
            }
        }
    }
    
    @Nested
    @DisplayName("Configuration")
    class Configuration {
        
        @Test
        @DisplayName("loadConfig sets enabled and stepMs")
        void loadConfigSetsParams() {
            controller.loadConfig(true, 3000);
            
            assertTrue(controller.isEnabled());
            assertEquals(3000, controller.getStepMs());
        }
        
        @Test
        @DisplayName("setters update individual parameters")
        void settersUpdateParams() {
            controller.setStepMs(4000);
            assertEquals(4000, controller.getStepMs());
        }
    }
    
    @Nested
    @DisplayName("Phase Transitions")
    class PhaseTransitions {
        
        @BeforeEach
        void enableAnimation() {
            controller.setEnabled(true);
            controller.setStepMs(STEP_MS);
        }
        
        @Test
        @DisplayName("transitions from DISABLED to CLEARING immediately when enabled")
        void disabledToClearing() {
            // First update after enabling should start animation immediately
            controller.update(1.0, true, MSG_COUNT, 100);
            assertEquals(Phase.CLEARING, controller.getPhase());
        }
        
        @Test
        @DisplayName("transitions from WAITING to CLEARING immediately")
        void waitingToClearing() {
            // Initial update - goes to WAITING, then immediately to CLEARING
            controller.update(1.0, true, MSG_COUNT, 0);
            // In WAITING phase, immediately starts new cycle
            assertEquals(Phase.CLEARING, controller.getPhase());
        }
        
        @Test
        @DisplayName("transitions from CLEARING to REVEALING after delay")
        void clearingToRevealing() {
            // Start animation - goes directly to CLEARING
            controller.update(1.0, true, MSG_COUNT, 100);
            assertEquals(Phase.CLEARING, controller.getPhase());
            
            // Wait for clearing delay (200ms)
            long afterClearing = 100 + 250;
            controller.update(1.0, true, MSG_COUNT, afterClearing);
            assertEquals(Phase.REVEALING, controller.getPhase());
        }
        
        @Test
        @DisplayName("transitions from REVEALING to HOLDING_END after cycle completes")
        void revealingToHoldingEnd() {
            // Start animation - goes directly to CLEARING
            controller.update(1.0, true, MSG_COUNT, 100);
            
            // Wait for clearing phase to complete
            long clearingEnd = 100 + 250;
            controller.update(1.0, true, MSG_COUNT, clearingEnd);
            assertEquals(Phase.REVEALING, controller.getPhase());
            
            // Advance time past full cycle (from when REVEALING started)
            long endTime = clearingEnd + STEP_MS * MSG_COUNT + 100;
            controller.update(1.0, true, MSG_COUNT, endTime);
            
            assertEquals(Phase.HOLDING_END, controller.getPhase());
        }
        
        @Test
        @DisplayName("transitions from HOLDING_END back to WAITING after hold period")
        void holdingEndToWaiting() {
            // Start animation - goes directly to CLEARING
            controller.update(1.0, true, MSG_COUNT, 100);
            
            // Wait for clearing to end
            long clearingEnd = 100 + 250;
            controller.update(1.0, true, MSG_COUNT, clearingEnd);
            assertEquals(Phase.REVEALING, controller.getPhase());
            
            // Complete cycle (advances to HOLDING_END)
            long cycleEnd = clearingEnd + STEP_MS * MSG_COUNT + 100;
            controller.update(1.0, true, MSG_COUNT, cycleEnd);
            assertEquals(Phase.HOLDING_END, controller.getPhase());
            
            // HOLDING_END lasts 300ms (END_HOLD_MS)
            long afterHold = cycleEnd + 350;
            controller.update(1.0, true, MSG_COUNT, afterHold);
            assertEquals(Phase.WAITING, controller.getPhase());
        }
    }
    
    @Nested
    @DisplayName("Message Visibility")
    class MessageVisibility {
        
        @BeforeEach
        void enableAnimation() {
            controller.setEnabled(true);
            controller.setStepMs(STEP_MS);
        }
        
        @Test
        @DisplayName("all messages visible in WAITING phase (after cycle completes)")
        void allMessagesVisibleInWaiting() {
            // Complete a full cycle to get to WAITING phase
            controller.update(1.0, true, MSG_COUNT, 100);
            long clearingEnd = 100 + 250;
            controller.update(1.0, true, MSG_COUNT, clearingEnd);
            long cycleEnd = clearingEnd + STEP_MS * MSG_COUNT + 100;
            controller.update(1.0, true, MSG_COUNT, cycleEnd);
            long afterHold = cycleEnd + 350;
            controller.update(1.0, true, MSG_COUNT, afterHold);
            assertEquals(Phase.WAITING, controller.getPhase());
            
            for (int i = 0; i < MSG_COUNT; i++) {
                assertTrue(controller.isVisible(i), "Message " + i + " should be visible in WAITING");
            }
        }
        
        @Test
        @DisplayName("all messages hidden during CLEARING phase")
        void allMessagesHiddenInClearing() {
            // First update goes to CLEARING
            controller.update(1.0, true, MSG_COUNT, 100);
            assertEquals(Phase.CLEARING, controller.getPhase());
            
            for (int i = 0; i < MSG_COUNT; i++) {
                assertFalse(controller.isVisible(i), "Message " + i + " should be hidden in CLEARING");
            }
        }
        
        @Test
        @DisplayName("messages revealed progressively during REVEALING")
        void progressiveReveal() {
            // Start animation - goes directly to CLEARING
            controller.update(1.0, true, MSG_COUNT, 100);
            assertEquals(Phase.CLEARING, controller.getPhase());
            
            // Wait for clearing to end
            long clearingEnd = 100 + 250;
            controller.update(1.0, true, MSG_COUNT, clearingEnd);
            assertEquals(Phase.REVEALING, controller.getPhase());
            
            // At start of REVEALING, only first message should be active
            assertEquals(0, controller.getActiveIndex());
            assertTrue(controller.isVisible(0), "First message should be visible");
            
            // Advance to ~50% through cycle - should have revealed ~2 messages
            long midCycle = clearingEnd + STEP_MS * MSG_COUNT / 2;
            controller.update(1.0, true, MSG_COUNT, midCycle);
            
            int activeIdx = controller.getActiveIndex();
            assertTrue(activeIdx >= 1 && activeIdx <= 2, 
                    "Mid-cycle should have revealed ~2 messages, got " + activeIdx);
        }
        
        @Test
        @DisplayName("all messages visible in HOLDING_END phase")
        void allVisibleInHoldingEnd() {
            // Start animation - goes directly to CLEARING
            controller.update(1.0, true, MSG_COUNT, 100);
            
            // Wait for clearing, then revealing, then holding
            long clearingEnd = 100 + 250;
            controller.update(1.0, true, MSG_COUNT, clearingEnd);
            
            long cycleEnd = clearingEnd + STEP_MS * MSG_COUNT + 100;
            controller.update(1.0, true, MSG_COUNT, cycleEnd);
            assertEquals(Phase.HOLDING_END, controller.getPhase());
            
            for (int i = 0; i < MSG_COUNT; i++) {
                assertTrue(controller.isVisible(i), "Message " + i + " should be visible in HOLDING_END");
            }
        }
    }
    
    @Nested
    @DisplayName("Message Highlighting")
    class MessageHighlighting {
        
        @BeforeEach
        void enableAnimation() {
            controller.setEnabled(true);
            controller.setStepMs(STEP_MS);
        }
        
        @Test
        @DisplayName("only active message is highlighted during REVEALING")
        void onlyActiveHighlighted() {
            // Start animation - goes directly to CLEARING
            controller.update(1.0, true, MSG_COUNT, 100);
            
            // Wait for clearing to end
            long clearingEnd = 100 + 250;
            controller.update(1.0, true, MSG_COUNT, clearingEnd);
            assertEquals(Phase.REVEALING, controller.getPhase());
            
            int activeIdx = controller.getActiveIndex();
            
            for (int i = 0; i < MSG_COUNT; i++) {
                if (i == activeIdx) {
                    assertTrue(controller.isHighlighted(i), "Active message should be highlighted");
                } else {
                    assertFalse(controller.isHighlighted(i), "Non-active message should not be highlighted");
                }
            }
        }
        
        @Test
        @DisplayName("no messages highlighted when disabled")
        void noHighlightWhenDisabled() {
            controller.setEnabled(false);
            controller.update(1.0, true, MSG_COUNT, 0);
            
            for (int i = 0; i < MSG_COUNT; i++) {
                assertFalse(controller.isHighlighted(i), "No highlighting when disabled");
            }
        }
    }
    
    @Nested
    @DisplayName("Timed Reveal Mode")
    class TimedRevealMode {
        
        @BeforeEach
        void enableAnimation() {
            controller.setEnabled(true);
            controller.setStepMs(STEP_MS);
        }
        
        @Test
        @DisplayName("enterTimedMode transitions to TIMED_REVEAL phase")
        void enterTimedMode() {
            // First enter CLEARING phase (must be animating to enter timed mode)
            controller.update(1.0, true, MSG_COUNT, 100);
            assertTrue(controller.isAnimating());
            
            controller.enterTimedMode();
            assertEquals(Phase.TIMED_REVEAL, controller.getPhase());
        }
        
        @Test
        @DisplayName("advanceTimedStep increments active index")
        void advanceIncrementsIndex() {
            // Start animation - goes directly to CLEARING
            controller.update(1.0, true, MSG_COUNT, 100);
            
            long clearingEnd = 100 + 250;
            controller.update(1.0, true, MSG_COUNT, clearingEnd);
            assertEquals(Phase.REVEALING, controller.getPhase());
            
            // Transition to timed reveal mode by pausing simulation
            controller.update(1.0, false, MSG_COUNT, clearingEnd + 10);
            assertEquals(Phase.TIMED_REVEAL, controller.getPhase());
            
            int startIdx = controller.getActiveIndex();
            
            assertTrue(controller.advanceTimedStep(), "Should advance");
            assertEquals(startIdx + 1, controller.getActiveIndex());
        }
        
        @Test
        @DisplayName("advanceTimedStep returns false after last message")
        void returnsFalseAtEnd() {
            // Start animation - goes directly to CLEARING
            controller.update(1.0, true, MSG_COUNT, 100);
            
            long clearingEnd = 100 + 250;
            long nearEnd = clearingEnd + STEP_MS * MSG_COUNT - 100;
            controller.update(1.0, true, MSG_COUNT, nearEnd);
            
            // Pause to enter timed reveal mode
            controller.update(1.0, false, MSG_COUNT, nearEnd + 10);
            assertEquals(Phase.TIMED_REVEAL, controller.getPhase());
            
            // Advance to last message
            while (controller.getActiveIndex() < MSG_COUNT - 1) {
                assertTrue(controller.advanceTimedStep(), "Should advance");
            }
            
            // Should return false after last message
            assertFalse(controller.advanceTimedStep(), "Should return false after all messages shown");
        }
        
        @Test
        @DisplayName("messages visible progressively in timed reveal mode")
        void progressiveVisibilityInTimed() {
            // Start animation - goes directly to CLEARING
            controller.update(1.0, true, MSG_COUNT, 100);
            
            long clearingEnd = 100 + 250;
            controller.update(1.0, true, MSG_COUNT, clearingEnd);
            assertEquals(Phase.REVEALING, controller.getPhase());
            assertEquals(0, controller.getActiveIndex());
            
            // Pause to enter timed reveal mode
            controller.update(1.0, false, MSG_COUNT, clearingEnd + 10);
            assertEquals(Phase.TIMED_REVEAL, controller.getPhase());
            
            // Only first message visible (activeIndex = 0)
            assertTrue(controller.isVisible(0), "First message visible");
            assertFalse(controller.isVisible(1), "Second message not yet visible");
            
            controller.advanceTimedStep();
            assertTrue(controller.isVisible(0), "First message still visible");
            assertTrue(controller.isVisible(1), "Second message now visible");
            assertFalse(controller.isVisible(2), "Third message not yet visible");
        }
        
        @Test
        @DisplayName("needsTimerReveal returns true in TIMED_REVEAL phase")
        void needsTimerRevealInTimedPhase() {
            controller.update(1.0, true, MSG_COUNT, 100);
            long clearingEnd = 100 + 250;
            controller.update(1.0, true, MSG_COUNT, clearingEnd);
            
            // Pause to enter timed reveal mode
            controller.update(1.0, false, MSG_COUNT, clearingEnd + 10);
            assertEquals(Phase.TIMED_REVEAL, controller.getPhase());
            
            assertTrue(controller.needsTimerReveal(), "Should need timer in TIMED_REVEAL phase");
        }
        
        @Test
        @DisplayName("needsTimerReveal returns false in other phases")
        void needsTimerRevealFalseInOtherPhases() {
            controller.update(1.0, true, MSG_COUNT, 100);
            assertEquals(Phase.CLEARING, controller.getPhase());
            assertFalse(controller.needsTimerReveal(), "Should not need timer in CLEARING phase");
            
            long clearingEnd = 100 + 250;
            controller.update(1.0, true, MSG_COUNT, clearingEnd);
            assertEquals(Phase.REVEALING, controller.getPhase());
            assertFalse(controller.needsTimerReveal(), "Should not need timer in REVEALING phase");
        }
    }
    
    @Nested
    @DisplayName("Reset")
    class Reset {
        
        @Test
        @DisplayName("reset clears animation state")
        void resetClearsState() {
            controller.setEnabled(true);
            controller.setStepMs(STEP_MS);
            
            // Start animating
            controller.update(1.0, true, MSG_COUNT, 100);
            assertTrue(controller.isAnimating());
            
            controller.reset();
            
            // Should be back to WAITING (since still enabled)
            assertFalse(controller.isAnimating());
            assertEquals(-1, controller.getActiveIndex());
        }
    }
    
    @Nested
    @DisplayName("Repaint Request")
    class RepaintRequest {
        
        @Test
        @DisplayName("wants repaint when animating")
        void wantsRepaintWhenAnimating() {
            controller.setEnabled(true);
            controller.setStepMs(STEP_MS);
            
            controller.update(1.0, true, MSG_COUNT, 100);
            
            assertTrue(controller.isAnimating());
            assertTrue(controller.wantsRepaint());
        }
        
        @Test
        @DisplayName("no repaint when not animating")
        void noRepaintWhenNotAnimating() {
            controller.setEnabled(false);
            controller.update(1.0, true, MSG_COUNT, 0);
            
            assertFalse(controller.isAnimating());
            assertFalse(controller.wantsRepaint());
        }
    }
}
