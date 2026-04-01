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
 * Controls sequence diagram reveal state.
 *
 * <p>Animation is step-only: during normal run and pause modes all messages
 * stay visible and the diagram reflects live model state. Manual Step enters a
 * cumulative reveal mode that advances one message at a time.</p>
 */
public class SequenceDiagramAnimationController {

    public static class Config {
        public static final int DEFAULT_STEP_MS = 500;
        public static final int MIN_STEP_MS = 50;
        public static final int CLEARING_DELAY_MS = 200;
        public static final int END_HOLD_MS = 300;
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

        public int[] toArray() { return new int[] { enabled ? 1 : 0, stepMs }; }
    }

    /**
     * Legacy timing helper retained for compatibility with existing callers.
     */
    public static class Clock {
        public void reset() {}
    }

    public static final int DEFAULT_STEP_MS = Config.DEFAULT_STEP_MS;
    public static final int CLEARING_DELAY_MS = Config.CLEARING_DELAY_MS;
    public static final int END_HOLD_MS = Config.END_HOLD_MS;
    public static final double HIGHLIGHT_STROKE_MULTIPLIER = Config.HIGHLIGHT_STROKE_MULTIPLIER;

    public enum Phase {
        DISABLED,
        WAITING,
        CLEARING,
        REVEALING,
        HOLDING_END,
        MANUAL_STEP,
        TIMED_REVEAL
    }

    private static boolean DEBUG = false;

    private final Config config = new Config();
    private final Clock clock = new Clock();

    private Phase phase = Phase.DISABLED;
    private int activeIndex = -1;
    private int messageCount = 0;

    public Config getConfig() { return config; }
    public boolean isEnabled() { return config.isEnabled(); }

    public void setEnabled(boolean enabled) {
        config.setEnabled(enabled);
        reset();
    }

    public int getStepMs() { return config.getStepMs(); }
    public void setStepMs(int stepMs) { config.setStepMs(stepMs); }

    public Phase getPhase() { return phase; }
    public int getActiveIndex() { return activeIndex; }
    public int getMessageCount() { return messageCount; }

    public boolean isAnimating() { return phase == Phase.MANUAL_STEP; }
    public boolean wantsRepaint() { return false; }
    public boolean needsTimerReveal() { return false; }

    public boolean isVisible(int msgIndex) {
        if (!config.isEnabled()) {
            return true;
        }
        if (phase != Phase.MANUAL_STEP) {
            return true;
        }
        return activeIndex >= 0 && msgIndex <= activeIndex;
    }

    public boolean isHighlighted(int msgIndex) {
        if (!config.isEnabled() || phase != Phase.MANUAL_STEP || activeIndex < 0) {
            return false;
        }
        return msgIndex == activeIndex;
    }

    public void reset() {
        Phase oldPhase = phase;
        phase = config.isEnabled() ? Phase.WAITING : Phase.DISABLED;
        activeIndex = -1;
        clock.reset();
        logPhaseChange(oldPhase, phase, "reset");
    }

    /**
     * In step-only mode, update just keeps the controller aligned with whether
     * the simulation is running. Live mode always shows all messages.
     */
    public void update(double simTime, boolean simRunning, int msgCount, long currentTimeMs) {
        messageCount = msgCount;

        if (!config.isEnabled() || msgCount == 0) {
            if (phase != Phase.DISABLED) {
                logPhaseChange(phase, Phase.DISABLED, "disabled/no messages");
            }
            phase = Phase.DISABLED;
            activeIndex = -1;
            return;
        }

        if (simRunning && phase == Phase.MANUAL_STEP) {
            logPhaseChange(phase, Phase.WAITING, "resumed simulation");
            phase = Phase.WAITING;
            activeIndex = -1;
            return;
        }

        if (phase != Phase.MANUAL_STEP) {
            phase = Phase.WAITING;
            activeIndex = -1;
        }
    }

    public boolean advanceManualStep() {
        if (!config.isEnabled()) {
            return false;
        }

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

        activeIndex = -1;
        logPhaseChange(phase, Phase.WAITING, "manual cycle complete");
        phase = Phase.WAITING;
        clock.reset();
        if (DEBUG) CirSim.console("SeqDiagram: manual step complete -> allow simulation step");
        return false;
    }

    public boolean advanceTimedStep() { return false; }
    public void enterTimedMode() {}

    @Deprecated
    public void enterManualMode() {
        enterTimedMode();
    }

    public void loadConfig(boolean enabled, int stepMs) {
        config.load(enabled, stepMs);
        reset();
    }

    public int[] getConfigForDump() { return config.toArray(); }

    private void logPhaseChange(Phase from, Phase to, String reason) {
        if (DEBUG && from != to) {
            CirSim.console("SeqDiagram: " + from + " -> " + to + " (" + reason + ")");
        }
    }

    public static void setDebug(boolean enabled) { DEBUG = enabled; }
    public static boolean isDebug() { return DEBUG; }
}
