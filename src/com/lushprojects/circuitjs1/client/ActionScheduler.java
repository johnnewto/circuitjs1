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

package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import com.google.gwt.user.client.Timer;

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
    private Timer animationTimer = null;  // Timer for animating slider changes
    private boolean simulationStarted = false;  // Track if simulation has run beyond initial state
    
    /**
     * State machine for scheduled actions
     * PENDING - Not yet reached action time
     * READY - Action at t=0, should execute immediately on first step
     * WAITING - Action time reached, timer started, waiting to execute
     * ANIMATING - Slider value is being animated over 2 seconds
     * EXECUTING - Animation complete, action is executing (transient)
     * COMPLETED - Action fully executed
     */
    public enum ActionState {
        PENDING,
        READY,
        WAITING,
        ANIMATING,
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
        public String preText;
        public String postText;
        public boolean enabled;
        public boolean stopSimulation;
        public ActionState state;  // Current state of the action
        
        public ScheduledAction() {
            this.id = 0;
            this.actionTime = 1.0;
            this.sliderName = "";
            this.sliderValue = 0.0;
            this.preText = "";
            this.postText = "After";
            this.enabled = true;
            this.stopSimulation = false;
            this.state = ActionState.PENDING;
        }
        
        public ScheduledAction(int id, double actionTime, String sliderName, 
                             double sliderValue, String preText, String postText, boolean enabled, boolean stopSimulation) {
            this.id = id;
            this.actionTime = actionTime;
            this.sliderName = sliderName;
            this.sliderValue = sliderValue;
            this.preText = preText;
            this.postText = postText;
            this.enabled = enabled;
            this.stopSimulation = stopSimulation;
            // Actions at t=0 start in READY state to execute immediately
            this.state = (actionTime == 0.0) ? ActionState.READY : ActionState.PENDING;
        }
        
        public ScheduledAction copy() {
            ScheduledAction copy = new ScheduledAction(id, actionTime, sliderName, 
                sliderValue, preText, postText, enabled, stopSimulation);
            copy.state = this.state;
            return copy;
        }
    }
    
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
     * Check if there is an active display message
     */
    public boolean hasDisplayMessage() {
        return displayMessage != null;
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
        
        // Animation duration (2 seconds at end of pause)
        final double animationDuration = 2.0;
        final double waitBeforeAnimation = Math.max(0, delaySeconds - animationDuration);
        
        // Create timer to start animation phase
        int waitMs = (int) (waitBeforeAnimation * 1000);
        resumeTimer = new Timer() {
            public void run() {
                // WAITING → ANIMATING
                transitionToAnimating(actions);
                
                // Start slider animation over 2 seconds
                animateActions(actions, animationDuration);
            }
        };
        
        resumeTimer.schedule(waitMs);
    }
    
    /**
     * Animate slider changes over a duration
     */
    private void animateActions(final List<ScheduledAction> actions, double durationSeconds) {
        final int steps = 50;  // 50 animation steps
        final int stepDelayMs = (int) ((durationSeconds * 1000) / steps);
        
        // Build and display message at start of animation
        StringBuilder allActionText = new StringBuilder();
        for (ScheduledAction action : actions) {
            // Build action text with formatted value
            String actionText = "";
            if (action.sliderName != null && !action.sliderName.isEmpty()) {
                actionText = action.sliderName + "=" + getFormattedSliderValue(action.sliderName, action.sliderValue);
            }
            
            // Collect action text for display
            if (action.postText != null && !action.postText.isEmpty()) {
                if (allActionText.length() > 0) {
                    allActionText.append("; ");
                }
                allActionText.append(action.postText);
                if (!actionText.isEmpty()) {
                    allActionText.append(": ").append(actionText);
                }
            }
        }
        
        // Set display message at start of animation
        if (allActionText.length() > 0) {
            displayMessage = allActionText.toString();
            CirSim.console("Animating: " + displayMessage);
            sim.repaint();  // Force redraw to show the message immediately
        }
        
        // Get starting values for all sliders and highlight them
        final double[] startValues = new double[actions.size()];
        for (int i = 0; i < actions.size(); i++) {
            ScheduledAction action = actions.get(i);
            if (action.sliderName != null && !action.sliderName.isEmpty()) {
                startValues[i] = getSliderValue(action.sliderName);
                highlightSlider(action.sliderName, true);  // Highlight during animation
            }
        }
        
        final int[] currentStep = {0};
        
        animationTimer = new Timer() {
            public void run() {
                currentStep[0]++;
                double progress = (double) currentStep[0] / steps;
                
                // Update all slider values
                for (int i = 0; i < actions.size(); i++) {
                    ScheduledAction action = actions.get(i);
                    if (action.sliderName != null && !action.sliderName.isEmpty()) {
                        // Linear interpolation
                        double currentValue = startValues[i] + (action.sliderValue - startValues[i]) * progress;
                        setSliderValue(action.sliderName, currentValue);
                    }
                }
                
                // Check if animation is complete
                if (currentStep[0] >= steps) {
                    // Remove highlights
                    for (int i = 0; i < actions.size(); i++) {
                        ScheduledAction action = actions.get(i);
                        if (action.sliderName != null && !action.sliderName.isEmpty()) {
                            highlightSlider(action.sliderName, false);
                        }
                    }
                    
                    // ANIMATING → EXECUTING → COMPLETED
                    animationTimer = null;
                    completeActions(actions);
                } else {
                    // Continue animation
                    this.schedule(stepDelayMs);
                }
            }
        };
        
        animationTimer.schedule(stepDelayMs);
    }
    
    /**
     * Complete action execution after animation
     */
    private void completeActions(List<ScheduledAction> actions) {
        isPaused = false;
        
        for (ScheduledAction action : actions) {
            // ANIMATING → EXECUTING
            transitionToExecuting(action);
            
            // Ensure final value is set exactly
            if (action.sliderName != null && action.sliderName.length() > 0) {
                setSliderValue(action.sliderName, action.sliderValue);
            }
            
            // EXECUTING → COMPLETED
            transitionToCompleted(action, sim.t);
        }
        
        // Display message is already set at start of animation, so no need to update it here
        
        sim.setSimRunning(true);
        CirSim.console("Resumed: " + actions.size() + " action(s) completed");
        resumeTimer = null;
        
        // Update button to show green running state
        sim.updateRunStopButton();
        
        // Refresh dialog to show completed state
        ActionTimeDialog.refreshIfOpen();
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
            
            // Cancel any animation in progress and unhighlight sliders
            if (animationTimer != null) {
                animationTimer.cancel();
                animationTimer = null;
                // Unhighlight all animating sliders
                unhighlightAllSliders();
            }
            
            if (resumeTimer != null) {
                // Trigger the timer immediately instead of just canceling it
                resumeTimer.cancel();
                resumeTimer.run();
                resumeTimer = null;
                CirSim.console("Cleared pause: triggered pending action");
            }
            sim.updateRunStopButton();  // Update button to show green running state
        }
    }
    
    /**
     * Cancel the resume timer (called when user stops simulation)
     * Triggers pending actions immediately before stopping
     */
    public void cancelResumeTimer() {
        // Cancel any animation in progress and unhighlight sliders
        if (animationTimer != null) {
            animationTimer.cancel();
            animationTimer = null;
            // Unhighlight all animating sliders
            unhighlightAllSliders();
        }
        
        if (resumeTimer != null) {
            // Trigger the timer immediately to execute pending actions
            resumeTimer.cancel();
            resumeTimer.run();  // Execute the actions now (starts animation)
            resumeTimer = null;
            isPaused = false;  // Clear pause state
            
            sim.updateRunStopButton();  // Update button to show normal stopped state
            ActionTimeDialog.refreshIfOpen();  // Refresh dialog to show updated states
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
                sim.t = nextAction.actionTime;
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
        ActionTimeDialog.refreshIfOpen();
    }
    
    /**
     * Update an existing action
     */
    public void updateAction(ScheduledAction action) {
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).id == action.id) {
                actions.set(i, action);
                sortActions();
                ActionTimeDialog.refreshIfOpen();
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
                ActionTimeDialog.refreshIfOpen();
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
        actions.clear();
        ActionTimeDialog.refreshIfOpen();
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
            ActionTimeDialog.refreshIfOpen();
        }
    }
    
    /**
     * Reset all triggered flags (called when simulation is reset)
     */
    public void reset() {
        for (ScheduledAction action : actions) {
            // Reset to READY if action is at t=0, otherwise PENDING
            action.state = (action.actionTime == 0.0) ? ActionState.READY : ActionState.PENDING;
        }
        displayMessage = null;
        cancelResumeTimer();
        isPaused = false;
        simulationStarted = false;  // Reset on simulation reset
        ActionTimeDialog.refreshIfOpen();
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
                setSliderValue(action.sliderName, action.sliderValue);
            }
            
            // Build action text with formatted value
            String actionText = "";
            if (action.sliderName != null && !action.sliderName.isEmpty()) {
                actionText = action.sliderName + "=" + getFormattedSliderValue(action.sliderName, action.sliderValue);
            }
            
            // Collect action text for display
            if (action.postText != null && !action.postText.isEmpty()) {
                if (allActionText.length() > 0) {
                    allActionText.append("; ");
                }
                allActionText.append(action.postText);
                if (!actionText.isEmpty()) {
                    allActionText.append(": ").append(actionText);
                }
            }
            
            // EXECUTING → COMPLETED
            transitionReadyToCompleted(action);
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
            ActionTimeDialog.refreshIfOpen();
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
                    transitionToWaiting(action, currentTime);
                    triggered.add(action);
                    anyStateChanged = true;
                }
            }
        }
        
        if (anyStateChanged) {
            ActionTimeDialog.refreshIfOpen();
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
        sim.updateRunStopButton();
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
     * Transition action(s) from WAITING to ANIMATING state
     */
    private void transitionToAnimating(List<ScheduledAction> actions) {
        for (ScheduledAction action : actions) {
            action.state = ActionState.ANIMATING;
            CirSim.console("Action #" + action.id + ": WAITING → ANIMATING");
        }
        ActionTimeDialog.refreshIfOpen();
    }
    
    /**
     * Transition action from ANIMATING to EXECUTING state
     */
    private void transitionToExecuting(ScheduledAction action) {
        action.state = ActionState.EXECUTING;
        CirSim.console("Action #" + action.id + ": ANIMATING → EXECUTING");
    }
    
    /**
     * Transition action from EXECUTING to COMPLETED state
     */
    private void transitionToCompleted(ScheduledAction action, double currentTime) {
        action.state = ActionState.COMPLETED;
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
        CirSim.console("Action #" + action.id + ": EXECUTING → COMPLETED [t=0]");
    }
    
    // ========== END STATE TRANSITION METHODS ==========
    
    /**
     * Find a slider by name and set its value
     */
    private void setSliderValue(String name, double value) {
        Adjustable adj = findAdjustableByName(name);
        if (adj != null) {
            adj.setSliderValue(value);
            EditInfo ei = adj.elm.getEditInfo(adj.editItem);
            if (ei != null) {
                ei.value = value;
                adj.elm.setEditValue(adj.editItem, ei);
                sim.analyzeFlag = true;
            }
        } else {
            CirSim.console("Warning: Slider '" + name + "' not found");
        }
    }
    
    /**
     * Highlight or unhighlight a slider by name
     */
    private void highlightSlider(String name, boolean highlight) {
        Adjustable adj = findAdjustableByName(name);
        if (adj != null) {
            adj.highlightSlider(highlight);
        }
    }
    
    /**
     * Unhighlight all sliders (called when animation is cancelled)
     */
    private void unhighlightAllSliders() {
        for (int i = 0; i < sim.adjustables.size(); i++) {
            Adjustable adj = sim.adjustables.get(i);
            adj.highlightSlider(false);
        }
    }
    
    /**
     * Find an adjustable by its slider name
     */
    private Adjustable findAdjustableByName(String name) {
        for (int i = 0; i < sim.adjustables.size(); i++) {
            Adjustable adj = sim.adjustables.get(i);
            if (adj.sliderText != null && adj.sliderText.equals(name)) {
                return adj;
            }
        }
        return null;
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
        sb.append("% ActionSchedule\n");
        sb.append("% APT ").append(pauseTime).append("\n");
        
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
            sb.append(action.stopSimulation).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * Load actions from string
     */
    public void load(String line) {
        // Check if this is a pause time config line
        if (line.startsWith("% APT ")) {
            // Line format: % APT pauseTime
            try {
                String value = line.substring(6).trim();
                pauseTime = Double.parseDouble(value);
            } catch (Exception e) {
                CirSim.console("Error loading pause time: " + e.getMessage());
            }
            return;
        }
        
        // Otherwise it's an action line
        // Line format: % AS id time sliderName value preText postText enabled [stopSimulation]
        String[] parts = line.substring(5).trim().split(" ", 8); // Skip "% AS "
        
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
                
                // Read stopSimulation flag if present (backward compatibility)
                if (parts.length >= 8) {
                    stopSimulation = Boolean.parseBoolean(parts[7]);
                }
                
                ScheduledAction action = new ScheduledAction(id, time, sliderName,
                    value, preText, postText, enabled, stopSimulation);
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
        List<String> sliders = new ArrayList<String>();
        for (int i = 0; i < sim.adjustables.size(); i++) {
            Adjustable adj = sim.adjustables.get(i);
            if (adj.sliderText != null && !adj.sliderText.isEmpty()) {
                sliders.add(adj.sliderText);
            }
        }
        return sliders;
    }
    
    /**
     * Get current value of a slider by name
     */
    public double getSliderValue(String name) {
        Adjustable adj = findAdjustableByName(name);
        if (adj != null) {
            EditInfo ei = adj.elm.getEditInfo(adj.editItem);
            if (ei != null) {
                return ei.value;
            }
        }
        return 0.0;
    }
    
    /**
     * Get formatted value for a slider/action, checking element for custom formatting
     */
    private String getFormattedSliderValue(String sliderName, double value) {
        Adjustable adj = findAdjustableByName(sliderName);
        if (adj != null) {
            EditInfo ei = adj.elm.getEditInfo(adj.editItem);
            if (ei != null) {
                // Try custom formatting first
                try {
                    String customText = adj.elm.getSliderUnitText(adj.editItem, ei, value);
                    if (customText != null) {
                        return customText;
                    }
                } catch (Exception e) {
                    // Element doesn't have custom formatting
                }
                // Use default formatting
                return EditDialog.unitString(ei, value);
            }
        }
        // Fallback to simple format
        return CircuitElm.showFormat.format(value);
    }
}
