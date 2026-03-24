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

package com.lushprojects.circuitjs1.client.elements;

import com.lushprojects.circuitjs1.client.elements.ActionTimeDialog;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.runner.RuntimeMode;
import com.lushprojects.circuitjs1.client.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.google.gwt.user.client.Timer;
import com.lushprojects.circuitjs1.client.elements.economics.ComputedValues;
import com.lushprojects.circuitjs1.client.elements.economics.ScenarioElm;
import com.lushprojects.circuitjs1.client.elements.electronics.wiring.LabeledNodeElm;
import com.lushprojects.circuitjs1.client.elements.misc.ActionTimeElm;

/**
 * Central scheduler for timed actions in the simulation.
 * Manages a list of scheduled actions that execute at specific times.
 * Actions can change slider values and display messages.
 */
public class ActionScheduler {
    private static ActionScheduler instance = null;
    private List<ScheduledAction> actions;
    private CirSim sim;
    private ScheduledAction clipboard = null;
    private String displayMessage = null;
    private boolean isPaused = false;
    private double pauseTime = 0.0;  // Seconds to pause after each action (0 = no pause)
    private Timer resumeTimer = null;  // Timer to auto-resume after pause
    private boolean simulationStarted = false;  // Track if simulation has run beyond initial state
    private Set<String> actionOverrideTargets = new HashSet<String>();
    private static final String ACTION_OVERRIDE_SOURCE = "ActionScheduler";
    
    /**
     * State machine for scheduled actions
     * PENDING - Not yet reached action time
     * READY - Action at t=0, should execute immediately on first step
     * WAITING - Action time reached, timer started, waiting to execute
     * EXECUTING - Action is executing (transient)
     * COMPLETED - Action fully executed
     */
    public enum ActionState {
        PENDING,
        READY,
        WAITING,
        EXECUTING,
        COMPLETED
    }
    
    /**
     * Represents a single scheduled action
     */
    public static class ScheduledAction {
        public int id;
        public double actionTime;
        public String sliderName;
        public double sliderValue;
        public String valueExpression;
        public String preText;
        public String postText;
        public boolean enabled;
        public boolean stopSimulation;
        public ActionState state;  // Current state of the action
        public double resolvedValue;
        public boolean resolvedValueSet;
        
        public ScheduledAction() {
            this.id = 0;
            this.actionTime = 1.0;
            this.sliderName = "";
            this.sliderValue = 0.0;
            this.valueExpression = "";
            this.preText = "";
            this.postText = "After";
            this.enabled = true;
            this.stopSimulation = false;
            this.state = ActionState.PENDING;
            this.resolvedValue = 0.0;
            this.resolvedValueSet = false;
        }
        
        public ScheduledAction(int id, double actionTime, String sliderName, 
                             double sliderValue, String preText, String postText, boolean enabled, boolean stopSimulation) {
            this.id = id;
            this.actionTime = actionTime;
            this.sliderName = sliderName;
            this.sliderValue = sliderValue;
            this.valueExpression = "";
            this.preText = preText;
            this.postText = postText;
            this.enabled = enabled;
            this.stopSimulation = stopSimulation;
            // Actions at t=0 start in READY state to execute immediately
            this.state = (actionTime == 0.0) ? ActionState.READY : ActionState.PENDING;
            this.resolvedValue = 0.0;
            this.resolvedValueSet = false;
        }
        
        public ScheduledAction copy() {
            ScheduledAction copy = new ScheduledAction(id, actionTime, sliderName, 
                sliderValue, preText, postText, enabled, stopSimulation);
            copy.state = this.state;
            copy.valueExpression = this.valueExpression;
            copy.resolvedValue = this.resolvedValue;
            copy.resolvedValueSet = this.resolvedValueSet;
            return copy;
        }
    }
    
    private String lastActionText = null;  // Text to display for last triggered action
    private boolean lastActionTextCleared = false;  // Flag to track if hovering over scope cleared the text
    
    private ActionScheduler(CirSim sim) {
        this.sim = sim;
        this.actions = new ArrayList<ScheduledAction>();
    }
    
    public static ActionScheduler getInstance(CirSim sim) {
        if (instance == null) {
            instance = new ActionScheduler(sim);
        }
        return instance;
    }

    private static void refreshActionTimeDialogIfGwt() {
        if (RuntimeMode.isGwt()) {
            ActionTimeDialog.refreshIfOpen();
        }
    }
    
    public static ActionScheduler getInstance() {
        return instance;
    }
    
    /**
     * Get the current display message (if any)
     */
    public String getDisplayMessage() {
        return displayMessage;
    }
    
    /**
     * Set display message for manual slider adjustment
     */
    public void setManualSliderMessage(String sliderName, String formattedValue) {
        displayMessage = "Manual: " + sliderName + "=" + formattedValue;
        CirSim.console("Manual slider adjustment: " + displayMessage);
    }
    
    /**
     * Check if there is an active display message
     */
    public boolean hasDisplayMessage() {
        return displayMessage != null && !displayMessage.isEmpty();
    }
    
    /**
     * Check if scheduler is paused
     */
    public boolean isPaused() {
        return isPaused;
    }
    
    /**
     * Check if there's a pending resume timer
     */
    public boolean hasPendingTimer() {
        return resumeTimer != null;
    }
    
    /**
     * Schedule auto-resume using a timer
     * @param delaySeconds Seconds to wait before resuming
     * @param actions The actions to execute when resuming (may be multiple if at same time)
     */
    private void scheduleResume(double delaySeconds, final List<ScheduledAction> actions) {
        // Cancel any existing timer
        if (resumeTimer != null) {
            resumeTimer.cancel();
        }

        int waitMs = (int) (Math.max(0, delaySeconds) * 1000);
        resumeTimer = new Timer() {
            public void run() {
                completeActions(actions);
            }
        };

        resumeTimer.schedule(waitMs);
    }

    /**
     * Complete action execution after pause delay
     */
    private void completeActions(List<ScheduledAction> actions) {
        isPaused = false;
        StringBuilder allActionText = new StringBuilder();
        
        for (ScheduledAction action : actions) {
            // WAITING → EXECUTING
            transitionToExecuting(action);
            
            // Set final value
            if (action.sliderName != null && action.sliderName.length() > 0) {
                double targetValue = action.resolvedValueSet ? action.resolvedValue : action.sliderValue;
                setActionTargetValue(action.sliderName, targetValue);
            }

            String actionText = formatActionText(action);

            if (allActionText.length() > 0) {
                allActionText.append("; ");
            }
            if (action.postText != null && !action.postText.isEmpty()) {
                allActionText.append(action.postText);
                if (!actionText.isEmpty()) {
                    allActionText.append(": ").append(actionText);
                }
            } else if (!actionText.isEmpty()) {
                allActionText.append(actionText);
            }
            
            // EXECUTING → COMPLETED
            transitionToCompleted(action, sim.getTime());
            action.resolvedValueSet = false;
        }

        if (allActionText.length() > 0) {
            displayMessage = allActionText.toString();
            sim.repaint();
        }
        
        sim.setSimRunning(true);
        CirSim.console("Resumed: " + actions.size() + " action(s) completed");
        resumeTimer = null;
        
        // Update button to show green running state
        sim.updateRunStopButtonForElements();
        
        // Refresh dialog to show completed state
        refreshActionTimeDialogIfGwt();
    }
    
    /**
     * Get the last triggered action's postText
     * Returns null if no action has been triggered or if text was cleared by hovering
     */
    public String getLastActionText() {
        return lastActionTextCleared ? null : lastActionText;
    }
    
    /**
     * Clear the last action text (called when hovering over scope)
     */
    public void clearLastActionText() {
        lastActionTextCleared = true;
    }
    
    /**
     * Get the pause time after each action (in seconds)
     */
    public double getPauseTime() {
        return pauseTime;
    }
    
    /**
     * Set the pause time after each action (in seconds)
     * @param seconds Seconds to pause after each action (0 = no pause)
     */
    public void setPauseTime(double seconds) {
        pauseTime = Math.max(0.0, seconds);
    }
    
    /**
     * Clear paused state (called when user manually starts simulation)
     * If there's a pending action timer, trigger it immediately
     */
    public void clearPausedState() {
        if (isPaused) {
            isPaused = false;
            
            if (resumeTimer != null) {
                // Trigger the timer immediately instead of just canceling it
                resumeTimer.cancel();
                resumeTimer.run();
                resumeTimer = null;
                CirSim.console("Cleared pause: triggered pending action");
            }
            sim.updateRunStopButtonForElements();  // Update button to show green running state
        }
    }
    
    /**
     * Cancel the resume timer (called when user stops simulation)
     * Triggers pending actions immediately before stopping
     */
    public void cancelResumeTimer() {
        if (resumeTimer != null) {
            // Trigger the timer immediately to execute pending actions
            resumeTimer.cancel();
            resumeTimer.run();
            resumeTimer = null;
            isPaused = false;  // Clear pause state
            
            sim.updateRunStopButtonForElements();  // Update button to show normal stopped state
            refreshActionTimeDialogIfGwt();  // Refresh dialog to show updated states
        }
    }
    
    /**
     * Set paused state (called internally when step mode pauses)
     */
    private void setPaused(boolean paused) {
        isPaused = paused;
        if (paused) {
            sim.setSimRunning(false);
            // Note: updateRunStopButton() is called after scheduleResume() creates the timer
        }
    }
    
    /**
     * Advance to next action (called when ▶ icon clicked)
     * Only works when simulation is stopped
     */
    public void advanceToNextAction() {
        // Only advance if simulation is currently stopped
        if (!sim.simIsRunning()) {
            // Find next untriggered action and advance time to it
            ScheduledAction nextAction = getNextAction();
            if (nextAction != null) {
                sim.setTime(nextAction.actionTime);
                isPaused = false;
                sim.setSimRunning(true);
                CirSim.console("Advanced to action #" + nextAction.id + " [t=" + 
                             CircuitElm.getUnitText(nextAction.actionTime, "s") + "]");
            }
        }
    }
    
    /**
     * Get the next non-completed enabled action
     */
    public ScheduledAction getNextAction() {
        for (ScheduledAction action : actions) {
            if (action.state != ActionState.COMPLETED && action.enabled) {
                return action;
            }
        }
        return null;
    }
    
    /**
     * Add a new action to the schedule
     */
    public void addAction(ScheduledAction action) {
        // Assign a unique ID
        action.id = getNextId();
        actions.add(action);
        sortActions();
        refreshActionTimeDialogIfGwt();
    }
    
    /**
     * Update an existing action
     */
    public void updateAction(ScheduledAction action) {
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).id == action.id) {
                actions.set(i, action);
                sortActions();
                refreshActionTimeDialogIfGwt();
                return;
            }
        }
    }
    
    /**
     * Delete an action by ID
     */
    public void deleteAction(int id) {
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).id == id) {
                actions.remove(i);
                refreshActionTimeDialogIfGwt();
                return;
            }
        }
    }
    
    /**
     * Get an action by ID
     */
    public ScheduledAction getAction(int id) {
        for (ScheduledAction action : actions) {
            if (action.id == id) {
                return action;
            }
        }
        return null;
    }
    
    /**
     * Get all actions (returns a copy to prevent external modification)
     */
    public List<ScheduledAction> getAllActions() {
        List<ScheduledAction> copy = new ArrayList<ScheduledAction>();
        for (ScheduledAction action : actions) {
            copy.add(action.copy());
        }
        return copy;
    }
    
    /**
     * Clear all actions
     */
    public void clearAll() {
        clearActionOverrides();
        actions.clear();
        refreshActionTimeDialogIfGwt();
    }
    
    /**
     * Copy an action to clipboard
     */
    public void copyAction(int id) {
        ScheduledAction action = getAction(id);
        if (action != null) {
            clipboard = action.copy();
        }
    }
    
    /**
     * Paste action from clipboard
     */
    public void pasteAction() {
        if (clipboard != null) {
            ScheduledAction newAction = clipboard.copy();
            addAction(newAction);
        }
    }
    
    /**
     * Check if clipboard has an action
     */
    public boolean hasClipboard() {
        return clipboard != null;
    }
    
    /**
     * Move an action to a new position in the list
     */
    public void moveAction(int id, int newIndex) {
        ScheduledAction action = null;
        int oldIndex = -1;
        
        // Find the action
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).id == id) {
                action = actions.get(i);
                oldIndex = i;
                break;
            }
        }
        
        if (action != null && oldIndex != -1 && newIndex >= 0 && newIndex < actions.size()) {
            actions.remove(oldIndex);
            actions.add(newIndex, action);
            refreshActionTimeDialogIfGwt();
        }
    }
    
    /**
     * Reset all triggered flags (called when simulation is reset)
     */
    public void reset() {
        clearActionOverrides();
        for (ScheduledAction action : actions) {
            // Reset to READY if action is at t=0, otherwise PENDING
            action.state = (action.actionTime == 0.0) ? ActionState.READY : ActionState.PENDING;
            action.resolvedValueSet = false;
        }
        displayMessage = null;
        lastActionText = null;  // Clear last action text on reset
        lastActionTextCleared = false;
        cancelResumeTimer();
        isPaused = false;
        simulationStarted = false;  // Reset on simulation reset
        refreshActionTimeDialogIfGwt();
    }
    
    /**
     * Execute actions that should trigger at current simulation time
     * Called after each timestep is complete and circuit state is settled
     */
    public void stepFinished(double currentTime) {
        // Skip if no ActionTimeElm is enabled or scheduler is paused
        if (!isAnyActionElementEnabled() || isPaused) {
            return;
        }
        
        // On first step, execute READY actions (t=0) immediately
        if (!simulationStarted) {
            handleFirstStep();
        }
        
        // Process pending actions that have reached their execution time
        List<ScheduledAction> actionsToExecute = collectTriggeredActions(currentTime);
        
        // Schedule execution of triggered actions
        if (!actionsToExecute.isEmpty()) {
            scheduleActionExecution(actionsToExecute);
        }
    }
    
    /**
     * Execute actions immediately without timer (for t=0 actions)
     */
    private void executeActionsNow(List<ScheduledAction> actions) {
        StringBuilder allActionText = new StringBuilder();
        
        for (ScheduledAction action : actions) {
            // READY → EXECUTING
            transitionReadyToExecuting(action);
            
            // Execute the action NOW - set slider value
            if (action.sliderName != null && action.sliderName.length() > 0) {
                double targetValue = resolveActionTargetValue(action);
                setActionTargetValue(action.sliderName, targetValue);
            }
            
            String actionText = formatActionText(action);
            
            if (allActionText.length() > 0) {
                allActionText.append("; ");
            }
            if (action.postText != null && !action.postText.isEmpty()) {
                allActionText.append(action.postText);
                if (!actionText.isEmpty()) {
                    allActionText.append(": ").append(actionText);
                }
            } else if (!actionText.isEmpty()) {
                allActionText.append(actionText);
            }
            
            // EXECUTING → COMPLETED
            transitionReadyToCompleted(action);
            action.resolvedValueSet = false;
        }
        
        // Set combined display message
        if (allActionText.length() > 0) {
            displayMessage = allActionText.toString();
            sim.repaint();  // Force redraw to show the message immediately
        }
        
        CirSim.console("Executed " + actions.size() + " action(s) at t=0");
    }
    
    /**
     * Check if any ActionTimeElm is enabled in the circuit
     */
    private boolean isAnyActionElementEnabled() {
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof ActionTimeElm && ((ActionTimeElm) ce).enabled) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Handle first step after reset - execute t=0 actions immediately
     */
    private void handleFirstStep() {
        simulationStarted = true;
        
        List<ScheduledAction> readyActions = new ArrayList<ScheduledAction>();
        for (ScheduledAction action : actions) {
            if (action.enabled && action.state == ActionState.READY) {
                readyActions.add(action);
            }
        }
        
        if (!readyActions.isEmpty()) {
            executeActionsNow(readyActions);
            refreshActionTimeDialogIfGwt();
        }
    }
    
    /**
     * Collect actions that should be triggered at current time
     * Handles both normal actions and stop simulation actions
     */
    private List<ScheduledAction> collectTriggeredActions(double currentTime) {
        List<ScheduledAction> triggered = new ArrayList<ScheduledAction>();
        boolean anyStateChanged = false;
        
        for (ScheduledAction action : actions) {
            // Skip disabled or completed actions
            if (!action.enabled || action.state != ActionState.PENDING) {
                continue;
            }
            
            // Check if action time has been reached
            if (currentTime >= action.actionTime) {
                if (action.stopSimulation) {
                    // Stop simulation actions skip waiting/animation states
                    transitionToCompleted(action, currentTime);
                    sim.setSimRunning(false);
                    anyStateChanged = true;
                } else {
                    // Normal actions transition to WAITING and get queued for execution
                    resolveActionTargetValue(action);
                    transitionToWaiting(action, currentTime);
                    triggered.add(action);
                    anyStateChanged = true;
                }
            }
        }
        
        if (anyStateChanged) {
            refreshActionTimeDialogIfGwt();
        }
        
        return triggered;
    }
    
    /**
     * Schedule execution of a batch of actions
     * Pauses simulation and sets up timer with animation
     */
    private void scheduleActionExecution(List<ScheduledAction> actions) {
        setPaused(true);
        double delay = pauseTime > 0 ? pauseTime : 0.001;
        scheduleResume(delay, actions);
        sim.updateRunStopButtonForElements();
        CirSim.console("Paused: " + actions.size() + " action(s) queued [delay=" + delay + "s]");
    }
    
    // ========== STATE TRANSITION METHODS ==========
    
    /**
     * Transition action from PENDING to WAITING state
     */
    private void transitionToWaiting(ScheduledAction action, double currentTime) {
        action.state = ActionState.WAITING;
        CirSim.console("Action #" + action.id + ": PENDING → WAITING [t=" + 
                     CircuitElm.getUnitText(currentTime, "s") + "]");
    }
    
    /**
     * Transition action from WAITING to EXECUTING state
     */
    private void transitionToExecuting(ScheduledAction action) {
        action.state = ActionState.EXECUTING;
        CirSim.console("Action #" + action.id + ": WAITING → EXECUTING");
    }
    
    /**
     * Transition action from EXECUTING to COMPLETED state
     */
    private void transitionToCompleted(ScheduledAction action, double currentTime) {
        action.state = ActionState.COMPLETED;
        // Store the postText for display in scope
        if (action.postText != null && !action.postText.isEmpty()) {
            lastActionText = action.postText;
            lastActionTextCleared = false;  // Reset the cleared flag
        }
        CirSim.console("Action #" + action.id + ": → COMPLETED [t=" + 
                     CircuitElm.getUnitText(currentTime, "s") + "]");
    }
    
    /**
     * Transition action from READY to EXECUTING state (for t=0 actions)
     */
    private void transitionReadyToExecuting(ScheduledAction action) {
        action.state = ActionState.EXECUTING;
        CirSim.console("Action #" + action.id + ": READY → EXECUTING");
    }
    
    /**
     * Transition action from READY/EXECUTING to COMPLETED state (for t=0 actions)
     */
    private void transitionReadyToCompleted(ScheduledAction action) {
        action.state = ActionState.COMPLETED;
        // Store the postText for display in scope
        if (action.postText != null && !action.postText.isEmpty()) {
            lastActionText = action.postText;
            lastActionTextCleared = false;  // Reset the cleared flag
        }
        CirSim.console("Action #" + action.id + ": EXECUTING → COMPLETED [t=0]");
    }
    
    // ========== END STATE TRANSITION METHODS ==========
    
    /**
     * Find a slider by name and set its value
     */
    private void setActionTargetValue(String name, double value) {
        if (name == null || name.isEmpty()) {
            return;
        }

        String resolvedName = resolveExistingTargetName(name);
        if (resolvedName == null || resolvedName.isEmpty()) {
            resolvedName = name;
        }

        if (sim.setAdjustableValueForElements(resolvedName, value)) {
            return;
        } else {
            ComputedValues.setComputedValueDirect(resolvedName, value);
            ComputedValues.setComputedValue(resolvedName, value);
            ComputedValues.setScenarioOverride(resolvedName, ACTION_OVERRIDE_SOURCE,
                ScenarioElm.MODE_REPLACE, value, true);
            actionOverrideTargets.add(resolvedName);
        }
    }
    
    /**
     * Find an adjustable by its slider name
     */
    private boolean hasAdjustableTarget(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        List<String> sliders = sim.getAdjustableNamesForElements();
        return sliders.contains(name);
    }

    /**
     * Resolve the final target value for an action exactly once when it is triggered.
     * Supports expression forms: *x, +x, -x, =x and plain numeric (treated as absolute value).
     */
    private double resolveActionTargetValue(ScheduledAction action) {
        if (action == null) {
            return 0.0;
        }
        if (action.resolvedValueSet) {
            return action.resolvedValue;
        }

        String expr = action.valueExpression;
        if (expr == null || expr.trim().isEmpty()) {
            action.resolvedValue = action.sliderValue;
            action.resolvedValueSet = true;
            return action.resolvedValue;
        }

        String trimmed = expr.trim();
        double current = getActionTargetValue(action.sliderName);
        if (Double.isNaN(current)) {
            current = action.sliderValue;
        }

        char op = trimmed.charAt(0);
        String rhsText = trimmed;
        if (op == '+' || op == '-' || op == '*' || op == '=') {
            rhsText = trimmed.substring(1).trim();
        } else {
            op = '=';
        }

        double rhs;
        try {
            rhs = Double.parseDouble(rhsText);
        } catch (Exception e) {
            rhs = action.sliderValue;
            op = '=';
        }

        double resolved = rhs;
        if (op == '+') {
            resolved = current + rhs;
        } else if (op == '-') {
            resolved = current - rhs;
        } else if (op == '*') {
            resolved = current * rhs;
        } else {
            resolved = rhs;
        }

        action.resolvedValue = resolved;
        action.resolvedValueSet = true;
        return resolved;
    }
    
    /**
     * Sort actions by execution time
     */
    private void sortActions() {
        Collections.sort(actions, new Comparator<ScheduledAction>() {
            public int compare(ScheduledAction a, ScheduledAction b) {
                return Double.compare(a.actionTime, b.actionTime);
            }
        });
    }
    
    /**
     * Get next available ID
     */
    private int getNextId() {
        int maxId = 0;
        for (ScheduledAction action : actions) {
            if (action.id > maxId) {
                maxId = action.id;
            }
        }
        return maxId + 1;
    }
    
    /**
     * Serialize actions to string for saving
     */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        
        // Save pause time configuration
        sb.append("% AST ").append(pauseTime).append("\n");
        
        // Save individual actions
        for (ScheduledAction action : actions) {
            sb.append("% AS ");
            sb.append(action.id).append(" ");
            sb.append(action.actionTime).append(" ");
            sb.append(CustomLogicModel.escape(action.sliderName)).append(" ");
            sb.append(action.sliderValue).append(" ");
            sb.append(CustomLogicModel.escape(action.preText)).append(" ");
            sb.append(CustomLogicModel.escape(action.postText)).append(" ");
            sb.append(action.enabled).append(" ");
            sb.append(action.stopSimulation).append(" ");
            sb.append(CustomLogicModel.escape(action.valueExpression == null ? "" : action.valueExpression)).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * Load actions from string
     */
    public void load(String line) {
        // Check if this is AST config line (pause time; optional legacy animation time ignored)
        if (line.startsWith("% AST ")) {
            // Line format: % AST pauseTime [animationTime]
            try {
                String[] values = line.substring(6).trim().split("\\s+");
                if (values.length >= 1) {
                    pauseTime = Double.parseDouble(values[0]);
                }
            } catch (Exception e) {
                CirSim.console("Error loading AST config: " + e.getMessage());
            }
            return;
        }
        
        // Check if this is a pause time config line (legacy format; optional second token ignored)
        if (line.startsWith("% APT ")) {
            // Line format: % APT pauseTime [animationTime]
            try {
                String[] values = line.substring(6).trim().split("\\s+");
                pauseTime = Double.parseDouble(values[0]);
            } catch (Exception e) {
                CirSim.console("Error loading pause/animation time: " + e.getMessage());
            }
            return;
        }
        
        // Ignore legacy animation-time-only config line.
        if (line.startsWith("% AAT ")) {
            return;
        }
        
        // Otherwise it's an action line
        // Line format: % AS id time sliderName value preText postText enabled [stopSimulation] [valueExpression]
        String[] parts = line.substring(5).trim().split(" ", 9); // Skip "% AS "
        
        if (parts.length >= 7) {
            try {
                int id = Integer.parseInt(parts[0]);
                double time = Double.parseDouble(parts[1]);
                String sliderName = CustomLogicModel.unescape(parts[2]);
                double value = Double.parseDouble(parts[3]);
                String preText = CustomLogicModel.unescape(parts[4]);
                String postText = CustomLogicModel.unescape(parts[5]);
                boolean enabled = Boolean.parseBoolean(parts[6]);
                boolean stopSimulation = false;
                String valueExpression = "";
                
                // Read stopSimulation flag if present (backward compatibility)
                if (parts.length >= 8) {
                    stopSimulation = Boolean.parseBoolean(parts[7]);
                }
                if (parts.length >= 9) {
                    valueExpression = CustomLogicModel.unescape(parts[8]);
                }
                
                ScheduledAction action = new ScheduledAction(id, time, sliderName,
                    value, preText, postText, enabled, stopSimulation);
                action.valueExpression = valueExpression;
                actions.add(action);
            } catch (Exception e) {
                CirSim.console("Error loading action: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get list of all available slider names in the circuit
     */
    public List<String> getAvailableSliders() {
        return sim.getAdjustableNamesForElements();
    }

    /**
     * Get all available action targets by name:
     * sliders, labeled nodes, computed values, and parameter names.
     */
    public List<String> getAvailableActionTargets() {
        Set<String> targets = new LinkedHashSet<String>();

        targets.addAll(getAvailableSliders());

        Set<String> nodeNames = LabeledNodeElm.getAllNodeNames();
        if (nodeNames != null) {
            targets.addAll(nodeNames);
        }

        Set<String> computedNames = ComputedValues.getAllNames();
        if (computedNames != null) {
            targets.addAll(computedNames);
        }

        Set<String> parameterNames = ComputedValues.getAllParameterNames();
        if (parameterNames != null) {
            targets.addAll(parameterNames);
        }

        return new ArrayList<String>(targets);
    }
    
    /**
     * Get list of all enabled action times (for scope markers)
     */
    public List<Double> getActionTimes() {
        List<Double> times = new ArrayList<Double>();
        for (ScheduledAction action : actions) {
            if (action.enabled) {
                times.add(action.actionTime);
            }
        }
        return times;
    }
    
    /**
     * Get current value of a slider by name
     * @return The current value, or NaN if not found
     */
    public double getSliderValue(String name) {
        return sim.getAdjustableValueForElements(name);
    }

    /**
     * Resolve current action target value by name.
     * Looks for slider first, then computed/current/converged values.
     */
    public double getActionTargetValue(String name) {
        String resolvedName = resolveExistingTargetName(name);
        if (resolvedName == null || resolvedName.isEmpty()) {
            resolvedName = name;
        }

        double sliderValue = getSliderValue(resolvedName);
        if (!Double.isNaN(sliderValue)) {
            return sliderValue;
        }

        Double computed = ComputedValues.getComputedValue(resolvedName);
        if (computed != null) {
            return computed.doubleValue();
        }

        Double converged = ComputedValues.getConvergedValue(resolvedName);
        if (converged != null) {
            return converged.doubleValue();
        }

        return Double.NaN;
    }
    
    /**
     * Get formatted value for a slider/action, checking element for custom formatting
     */
    private String getFormattedSliderValue(String sliderName, double value) {
        return sim.formatAdjustableValueForUi(sliderName, value);
    }

    private String formatActionText(ScheduledAction action) {
        if (action == null) {
            return "";
        }

        String target = action.sliderName == null ? "" : action.sliderName.trim();
        if (target.isEmpty()) {
            return "";
        }

        String expression = action.valueExpression == null ? "" : action.valueExpression.trim();
        if (!expression.isEmpty()) {
            char op = expression.charAt(0);
            if (expression.length() > 1 && (op == '+' || op == '-' || op == '*' || op == '=')) {
                return target + " " + op + " " + expression.substring(1).trim();
            }
            return target + " = " + expression;
        }

        double targetValue = action.resolvedValueSet ? action.resolvedValue : action.sliderValue;
        return target + " = " + getFormattedSliderValue(action.sliderName, targetValue);
    }

    public String getFormattedActionText(ScheduledAction action) {
        if (action == null) {
            return "";
        }
        if (action.stopSimulation) {
            return "[STOP SIMULATION]";
        }
        String text = formatActionText(action);
        if (text == null || text.isEmpty()) {
            return "(no action)";
        }
        return text;
    }

    private void clearActionOverrides() {
        if (actionOverrideTargets == null || actionOverrideTargets.isEmpty()) {
            return;
        }
        for (String target : actionOverrideTargets) {
            ComputedValues.clearScenarioOverride(target, ACTION_OVERRIDE_SOURCE);
        }
        actionOverrideTargets.clear();
    }

    private String resolveExistingTargetName(String rawName) {
        if (rawName == null) {
            return null;
        }
        List<String> candidates = getTargetNameCandidates(rawName);
        if (candidates.isEmpty()) {
            return rawName;
        }

        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            if (hasAdjustableTarget(candidate)) {
                return candidate;
            }
            if (ComputedValues.isParameterName(candidate) ||
                ComputedValues.hasComputedValue(candidate) ||
                ComputedValues.getConvergedValue(candidate) != null ||
                LabeledNodeElm.getByName(candidate) != null) {
                return candidate;
            }
        }

        return candidates.get(0);
    }

    private List<String> getTargetNameCandidates(String rawName) {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        if (rawName == null) {
            return new ArrayList<String>(names);
        }

        String trimmed = rawName.trim();
        addTargetVariants(names, trimmed);

        if (trimmed.length() >= 2 && trimmed.startsWith("$") && trimmed.endsWith("$")) {
            addTargetVariants(names, trimmed.substring(1, trimmed.length() - 1).trim());
        }

        return new ArrayList<String>(names);
    }

    private void addTargetVariants(Set<String> names, String base) {
        if (base == null) {
            return;
        }
        String trimmed = base.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        names.add(trimmed);
        String collapsed = collapseBackslashes(trimmed);
        names.add(collapsed);

        String greek = normalizeGreekTarget(collapsed);
        names.add(greek);
        names.add(collapseBackslashes(greek));
    }

    private String collapseBackslashes(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String collapsed = value;
        while (collapsed.indexOf("\\\\") >= 0) {
            collapsed = collapsed.replace("\\\\", "\\");
        }
        return collapsed;
    }

    private String normalizeGreekTarget(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String normalized = value;
        normalized = normalized.replace("α", "\\alpha");
        normalized = normalized.replace("θ", "\\theta");
        normalized = normalized.replace("Δ", "\\Delta");
        normalized = normalized.replace("∆", "\\Delta");

        normalized = normalized.replaceAll("(^|[^\\\\])alpha_?([0-9])", "$1\\\\alpha$2");
        normalized = normalized.replaceAll("(^|[^\\\\])theta", "$1\\\\theta");
        normalized = normalized.replaceAll("(^|[^\\\\])Delta", "$1\\\\Delta");

        return normalized;
    }
}
