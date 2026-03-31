/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client.elements.annotation;

import com.lushprojects.circuitjs1.client.CirSim;

/**
 * Controls animation state for sequence diagram transaction visualization.
 * 
 * <p>This controller manages a state machine that progressively reveals
 * transactions in a sequence diagram, either automatically (wall-clock based)
 * or manually (step-by-step).
 * 
 * <h3>Animation Phases:</h3>
 * <ul>
 *   <li>{@link Phase#DISABLED} - Animation is turned off, all messages visible</li>
 *   <li>{@link Phase#WAITING} - Counting simulation timesteps until next cycle</li>
 *   <li>{@link Phase#REVEALING} - Progressively showing messages over time</li>
 *   <li>{@link Phase#HOLDING_END} - Brief pause after all messages shown</li>
 *   <li>{@link Phase#MANUAL_STEP} - Paused, waiting for user to press Step</li>
 * </ul>
 * 
 * <h3>Usage:</h3>
 * <pre>
 * // In draw():
 * controller.update(simTime, simRunning, messageCount, currentTimeMs);
 * 
 * // When rendering each message:
 * if (controller.isVisible(msgIndex)) {
 *     drawMessage(...);
 *     if (controller.isHighlighted(msgIndex)) {
 *         // Use highlight color
 *     }
 * }
 * </pre>
 */
public class SequenceDiagramAnimationController {
    
    // ══════════════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Configuration settings for sequence diagram animation.
     * Encapsulates user-editable settings and timing constants.
     */
    public static class Config {
        
        /** Default time per animation step in milliseconds */
        public static final int DEFAULT_STEP_MS = 500;
        
        /** Minimum allowed step duration */
        public static final int MIN_STEP_MS = 50;
        
        /** Duration to show blank diagram before revealing starts (ms) */
        public static final int CLEARING_DELAY_MS = 200;
        
        /** Duration to hold at end after all messages shown (ms) */
        public static final int END_HOLD_MS = 300;
        
        /** Stroke width multiplier for highlighted arrows */
        public static final double HIGHLIGHT_STROKE_MULTIPLIER = 1.5;
        
        private boolean enabled = false;
        private int stepMs = DEFAULT_STEP_MS;
        
        public Config() {}
        
        public Config(boolean enabled, int stepMs) {
            this.enabled = enabled;
            setStepMs(stepMs);
        }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getStepMs() { return stepMs; }
        public void setStepMs(int stepMs) { this.stepMs = Math.max(MIN_STEP_MS, stepMs); }
        
        public void load(boolean enabled, int stepMs) {
            this.enabled = enabled;
            setStepMs(stepMs);
        }
        
        public int[] toArray() {
            return new int[] { enabled ? 1 : 0, stepMs };
        }
        
        public Config copy() { return new Config(enabled, stepMs); }
    }
    
    /**
     * Manages wall-clock timing for animation cycles.
     */
    public static class Clock {
        
        private long cycleStartMs = 0;
        
        public void startCycle(long currentTimeMs) { cycleStartMs = currentTimeMs; }
        
        public long elapsed(long currentTimeMs) { return currentTimeMs - cycleStartMs; }
        
        public void reset() { cycleStartMs = 0; }
        
        public int calculateRevealIndex(long currentTimeMs, int stepMs) {
            return (int) (elapsed(currentTimeMs) / stepMs);
        }
        
        public void adjustForIndex(long currentTimeMs, int currentIndex, int stepMs) {
            cycleStartMs = currentTimeMs - (long)(currentIndex * stepMs);
        }
        
        public boolean isClearingComplete(long currentTimeMs) {
            return elapsed(currentTimeMs) >= Config.CLEARING_DELAY_MS;
        }
        
        public boolean isEndHoldComplete(long currentTimeMs) {
            return elapsed(currentTimeMs) >= Config.END_HOLD_MS;
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // CONSTANTS (for backward compatibility)
    // ══════════════════════════════════════════════════════════════════════════
    
    public static final int DEFAULT_STEP_MS = Config.DEFAULT_STEP_MS;
    public static final int CLEARING_DELAY_MS = Config.CLEARING_DELAY_MS;
    public static final int END_HOLD_MS = Config.END_HOLD_MS;
    public static final double HIGHLIGHT_STROKE_MULTIPLIER = Config.HIGHLIGHT_STROKE_MULTIPLIER;
    
    // ══════════════════════════════════════════════════════════════════════════
    // PHASE ENUM
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Animation state machine phases.
     */
    public enum Phase {
        /** Animation disabled - all messages visible in normal color */
        DISABLED,
        
        /** Counting timesteps until next animation cycle starts */
        WAITING,
        
        /** Brief blank period - all messages hidden before reveal starts */
        CLEARING,
        
        /** Progressively revealing messages based on elapsed time */
        REVEALING,
        
        /** All messages shown, brief pause before cycle ends */
        HOLDING_END,
        
        /** Manual stepping mode - waiting for user Step command */
        MANUAL_STEP,
        
        /** Timer-based reveal when simulation stops - auto-advances using stepMs */
        TIMED_REVEAL
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // CONFIGURATION & TIMING
    // ══════════════════════════════════════════════════════════════════════════
    
    /** Enable debug logging to browser console */
    private static boolean DEBUG = false;
    
    private final Config config = new Config();
    private final Clock clock = new Clock();
    
    // ══════════════════════════════════════════════════════════════════════════
    // RUNTIME STATE
    // ══════════════════════════════════════════════════════════════════════════
    
    private Phase phase = Phase.DISABLED;
    private int activeIndex = -1;
    private int messageCount = 0;
    
    // ══════════════════════════════════════════════════════════════════════════
    // CONFIGURATION ACCESSORS
    // ══════════════════════════════════════════════════════════════════════════
    
    public Config getConfig() {
        return config;
    }
    
    public boolean isEnabled() {
        return config.isEnabled();
    }
    
    public void setEnabled(boolean enabled) {
        config.setEnabled(enabled);
        if (!enabled) {
            reset();
        }
    }
    
    public int getStepMs() {
        return config.getStepMs();
    }
    
    public void setStepMs(int stepMs) {
        config.setStepMs(stepMs);
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // STATE ACCESSORS
    // ══════════════════════════════════════════════════════════════════════════
    
    public Phase getPhase() {
        return phase;
    }
    
    public int getActiveIndex() {
        return activeIndex;
    }
    
    public int getMessageCount() {
        return messageCount;
    }
    
    /**
     * Returns true if animation is currently in progress (CLEARING, REVEALING, HOLDING_END, MANUAL_STEP, or TIMED_REVEAL).
     */
    public boolean isAnimating() {
        return phase == Phase.CLEARING || phase == Phase.REVEALING || phase == Phase.HOLDING_END 
            || phase == Phase.MANUAL_STEP || phase == Phase.TIMED_REVEAL;
    }
    
    /**
     * Returns true if continuous repaint is needed for smooth animation.
     */
    public boolean wantsRepaint() {
        return phase == Phase.CLEARING || phase == Phase.REVEALING || phase == Phase.HOLDING_END;
    }
    
    /**
     * Returns true if timed reveal mode is active and a timer should be running.
     */
    public boolean needsTimerReveal() {
        return phase == Phase.TIMED_REVEAL;
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // VISIBILITY & HIGHLIGHTING
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Returns true if the message at the given index should be visible.
     * Uses cumulative reveal: all messages up to activeIndex are shown.
     * 
     * @param msgIndex Message index (0-based)
     * @return true if message should be drawn
     */
    public boolean isVisible(int msgIndex) {
        if (!config.isEnabled()) {
            return true;
        }
        if (phase == Phase.DISABLED || phase == Phase.WAITING) {
            return true;
        }
        if (phase == Phase.CLEARING) {
            return false;  // All hidden during clearing period
        }
        if (activeIndex < 0) {
            return false;
        }
        return msgIndex <= activeIndex;
    }
    
    /**
     * Returns true if the message at the given index should be highlighted.
     * Uses flash mode: only the current activeIndex is highlighted.
     * 
     * @param msgIndex Message index (0-based)
     * @return true if message should use highlight color/stroke
     */
    public boolean isHighlighted(int msgIndex) {
        if (!config.isEnabled() || activeIndex < 0) {
            return false;
        }
        return msgIndex == activeIndex;
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // STATE MACHINE UPDATE
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Resets animation state to initial conditions.
     */
    public void reset() {
        Phase oldPhase = phase;
        phase = config.isEnabled() ? Phase.WAITING : Phase.DISABLED;
        activeIndex = -1;
        clock.reset();
        logPhaseChange(oldPhase, phase, "reset");
    }
    
    /**
     * Updates animation state. Call this each draw() frame.
     * 
     * @param simTime Current simulation time (unused, for API compatibility)
     * @param simRunning True if simulation is actively running (not paused)
     * @param msgCount Number of messages in the diagram
     * @param currentTimeMs Current wall-clock time in milliseconds
     */
    public void update(double simTime, boolean simRunning, int msgCount, long currentTimeMs) {
        this.messageCount = msgCount;
        
        // Handle disabled state
        if (!config.isEnabled() || msgCount == 0) {
            if (phase != Phase.DISABLED) {
                logPhaseChange(phase, Phase.DISABLED, "disabled/no messages");
            }
            phase = Phase.DISABLED;
            activeIndex = -1;
            return;
        }
        
        // State machine transitions
        switch (phase) {
            case DISABLED:
                // First update - start cycle immediately
                startCycle(currentTimeMs, simRunning);
                break;
                
            case WAITING:
                // Only start new cycle if simulation is running
                // When stopped, stay in WAITING (all messages visible)
                if (simRunning) {
                    startCycle(currentTimeMs, simRunning);
                }
                break;
                
            case CLEARING:
                if (simRunning) {
                    updateClearingPhase(currentTimeMs);
                } else {
                    // Paused during clearing - go to timed reveal mode
                    logPhaseChange(phase, Phase.TIMED_REVEAL, "paused during clearing");
                    phase = Phase.TIMED_REVEAL;
                    activeIndex = -1;
                }
                break;
                
            case REVEALING:
                if (simRunning) {
                    updateRevealingPhase(currentTimeMs);
                } else {
                    // Switched to timed reveal mode
                    logPhaseChange(phase, Phase.TIMED_REVEAL, "paused during revealing");
                    phase = Phase.TIMED_REVEAL;
                    if (activeIndex < 0) {
                        activeIndex = 0;
                    }
                }
                break;
                
            case HOLDING_END:
                if (simRunning) {
                    updateHoldingPhase(currentTimeMs);
                } else {
                    logPhaseChange(phase, Phase.TIMED_REVEAL, "paused during holding");
                    phase = Phase.TIMED_REVEAL;
                }
                break;
                
            case MANUAL_STEP:
                if (simRunning) {
                    // Resumed simulation - continue from current position
                    logPhaseChange(phase, Phase.REVEALING, "resumed simulation");
                    phase = Phase.REVEALING;
                    clock.adjustForIndex(currentTimeMs, activeIndex, config.getStepMs());
                }
                break;
                
            case TIMED_REVEAL:
                if (simRunning) {
                    // Resumed simulation - continue from current position
                    logPhaseChange(phase, Phase.REVEALING, "resumed simulation");
                    phase = Phase.REVEALING;
                    clock.adjustForIndex(currentTimeMs, activeIndex, config.getStepMs());
                }
                // Timer-driven advancement happens via advanceTimedStep()
                break;
        }
    }
    
    /**
     * Starts a new animation cycle.
     */
    private void startCycle(long currentTimeMs, boolean simRunning) {
        clock.startCycle(currentTimeMs);
        activeIndex = -1;  // No messages visible yet
        
        Phase oldPhase = phase;
        if (simRunning) {
            phase = Phase.CLEARING;  // Start with blank period
        } else {
            phase = Phase.TIMED_REVEAL;  // Timer will advance animation
            activeIndex = -1;  // Timed mode starts with nothing visible
        }
        logPhaseChange(oldPhase, phase, "startCycle simRunning=" + simRunning);
    }
    
    /**
     * Updates state during the CLEARING phase (blank period before reveal).
     */
    private void updateClearingPhase(long currentTimeMs) {
        if (clock.isClearingComplete(currentTimeMs)) {
            // Clearing period complete - start revealing
            logPhaseChange(phase, Phase.REVEALING, "clearing complete");
            phase = Phase.REVEALING;
            activeIndex = 0;
            // Adjust cycle start so reveal timing is correct
            clock.startCycle(currentTimeMs);
        }
    }
    
    /**
     * Updates state during the REVEALING phase.
     */
    private void updateRevealingPhase(long currentTimeMs) {
        // Each step takes stepMs milliseconds
        activeIndex = clock.calculateRevealIndex(currentTimeMs, config.getStepMs());
        
        if (activeIndex >= messageCount) {
            activeIndex = messageCount - 1;
            logPhaseChange(phase, Phase.HOLDING_END, "all messages revealed");
            phase = Phase.HOLDING_END;
            // Reset cycle start for hold timing
            clock.startCycle(currentTimeMs);
        }
    }
    
    /**
     * Updates state during the HOLDING_END phase.
     */
    private void updateHoldingPhase(long currentTimeMs) {
        if (clock.isEndHoldComplete(currentTimeMs)) {
            // Hold complete
            logPhaseChange(phase, Phase.WAITING, "hold complete");
            phase = Phase.WAITING;
            activeIndex = -1;
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // MANUAL STEPPING
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Advances one frame in manual step mode.
     * Call this when the user presses Step while simulation is paused.
     * Works from any phase - switches to MANUAL_STEP mode.
     * Once the last message is already visible, the step is not consumed so the
     * simulator can advance its timestep.
     *
     * @return true if the step was consumed by animation (don't advance simulation),
     *         false if animation is disabled or all messages are already visible
     */
    public boolean advanceManualStep() {
        if (!config.isEnabled()) {
            return false;
        }
        
        // Switch to manual mode from any phase
        if (phase != Phase.MANUAL_STEP) {
            logPhaseChange(phase, Phase.MANUAL_STEP, "user pressed step");
            phase = Phase.MANUAL_STEP;
        }
        
        if (activeIndex < 0) {
            activeIndex = 0;
            if (DEBUG) CirSim.console("SeqDiagram: manual step -> activeIndex=0");
            return true;
        }
        
        if (activeIndex < messageCount - 1) {
            activeIndex++;
            if (DEBUG) CirSim.console("SeqDiagram: manual step -> activeIndex=" + activeIndex);
            return true;
        }

        // Last message is already visible. Reset the manual reveal cycle so the
        // next paused Step starts from the beginning again, then allow one
        // simulation timestep to proceed.
        activeIndex = -1;
        logPhaseChange(phase, Phase.WAITING, "manual cycle complete");
        phase = Phase.WAITING;
        clock.reset();

        if (DEBUG) CirSim.console("SeqDiagram: manual step complete -> allow simulation step");
        return false;
    }
    
    /**
     * Advances one frame in timed reveal mode.
     * Call this from a timer when simulation is stopped.
     * 
     * @return true if there are more steps to reveal,
     *         false if animation is complete (all messages shown)
     */
    public boolean advanceTimedStep() {
        if (!config.isEnabled() || phase != Phase.TIMED_REVEAL) {
            return false;
        }
        
        if (activeIndex < 0) {
            activeIndex = 0;
            if (DEBUG) CirSim.console("SeqDiagram: timed step -> activeIndex=0");
            return true;
        }
        
        if (activeIndex < messageCount - 1) {
            activeIndex++;
            if (DEBUG) CirSim.console("SeqDiagram: timed step -> activeIndex=" + activeIndex);
            return true;
        }
        
        // All messages shown - end timed reveal cycle
        logPhaseChange(phase, Phase.WAITING, "timed reveal complete");
        phase = Phase.WAITING;
        activeIndex = -1;
        return false;
    }
    
    /**
     * Forces transition to timed reveal mode.
     * Call this when simulation is paused externally.
     */
    public void enterTimedMode() {
        if (isAnimating()) {
            logPhaseChange(phase, Phase.TIMED_REVEAL, "enterTimedMode");
            phase = Phase.TIMED_REVEAL;
            if (activeIndex < 0) {
                activeIndex = 0;
            }
        }
    }
    
    /**
     * @deprecated Use {@link #enterTimedMode()} instead
     */
    @Deprecated
    public void enterManualMode() {
        enterTimedMode();
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // SERIALIZATION HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Loads configuration from serialized values.
     */
    public void loadConfig(boolean enabled, int stepMs) {
        config.load(enabled, stepMs);
        reset();
    }
    
    /**
     * Returns configuration as array for serialization: [enabled, stepMs]
     */
    public int[] getConfigForDump() {
        return config.toArray();
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // LOGGING
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Logs a phase transition to the browser console.
     */
    private void logPhaseChange(Phase from, Phase to, String reason) {
        if (DEBUG && from != to) {
            CirSim.console("SeqDiagram: " + from + " -> " + to + " (" + reason + ")");
        }
    }
    
    /**
     * Enables or disables debug logging.
     */
    public static void setDebug(boolean enabled) {
        DEBUG = enabled;
    }
    
    /**
     * Returns true if debug logging is enabled.
     */
    public static boolean isDebug() {
        return DEBUG;
    }
}
