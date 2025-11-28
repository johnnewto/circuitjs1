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
    private boolean simulationStarted = false;  // Track if simulation has run beyond initial state
    
    /**
     * State machine for scheduled actions
     * PENDING - Not yet reached action time
     * READY - Action at t=0, should execute immediately on first step
     * WAITING - Action time reached, timer started, waiting to execute
     * EXECUTING - Timer fired, action is executing (transient)
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
     * Schedule auto-resume using a timer
     * @param delaySeconds Seconds to wait before resuming
     * @param actions The actions to execute when resuming (may be multiple if at same time)
     */
    private void scheduleResume(double delaySeconds, final List<ScheduledAction> actions) {
        // Cancel any existing timer
        if (resumeTimer != null) {
            resumeTimer.cancel();
        }
        
        // Create new timer to resume simulation
        int delayMs = (int) (delaySeconds * 1000);
        resumeTimer = new Timer() {
            public void run() {
                isPaused = false;
                
                // Execute ALL actions in the list
                StringBuilder allActionText = new StringBuilder();
                for (ScheduledAction action : actions) {
                    // Transition to EXECUTING state
                    action.state = ActionState.EXECUTING;
                    CirSim.console("ActionScheduler: Action #" + action.id + " entering EXECUTING state");
                    
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
                    
                    // Transition to COMPLETED state
                    action.state = ActionState.COMPLETED;
                    CirSim.console("ActionScheduler: Action #" + action.id + " entering COMPLETED state");
                }
                
                // Set combined display message
                if (allActionText.length() > 0) {
                    displayMessage = allActionText.toString();
                }
                
                sim.setSimRunning(true);
                CirSim.console("ActionScheduler: Auto-resuming after " + pauseTime + "s pause - executed " + actions.size() + " action(s)");
                resumeTimer = null;
                
                // Refresh dialog to show completed state
                ActionTimeDialog.refreshIfOpen();
            }
        };
        
        resumeTimer.schedule(delayMs);
        CirSim.console("ActionScheduler: Resume timer scheduled for " + delaySeconds + "s (" + delayMs + "ms) - will execute " + actions.size() + " action(s)");
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
        CirSim.console("ActionScheduler: Pause time set to " + pauseTime + "s");
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
            }
            CirSim.console("ActionScheduler: Cleared paused state and triggered pending action");
        }
    }
    
    /**
     * Cancel the resume timer (called when user stops simulation)
     */
    public void cancelResumeTimer() {
        if (resumeTimer != null) {
            resumeTimer.cancel();
            resumeTimer = null;
            CirSim.console("ActionScheduler: Cancelled resume timer");
        }
    }
    
    /**
     * Set paused state (called internally when step mode pauses)
     */
    private void setPaused(boolean paused) {
        isPaused = paused;
        if (paused) {
            sim.setSimRunning(false);
            CirSim.console("ActionScheduler: Paused - simulation stopped");
        }
    }
    
    /**
     * Advance to next action (called when ▶ icon clicked)
     * Only works when simulation is stopped
     */
    public void advanceToNextAction() {
        CirSim.console("ActionScheduler: advanceToNextAction() called, simRunning=" + sim.simIsRunning());
        
        // Only advance if simulation is currently stopped
        if (!sim.simIsRunning()) {
            // Find next untriggered action and advance time to it
            ScheduledAction nextAction = getNextAction();
            if (nextAction != null) {
                sim.t = nextAction.actionTime;
                isPaused = false;
                sim.setSimRunning(true);
                CirSim.console("ActionScheduler: Advanced to next action at t=" + nextAction.actionTime + ", starting simulation");
            } else {
                CirSim.console("ActionScheduler: No more actions to advance to");
            }
        } else {
            CirSim.console("ActionScheduler: Cannot advance while simulation is running");
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
        // Display message no longer has time limit - it persists until manually cleared or new message set
        
        // Check if any ActionTimeElm exists and if all are disabled
        boolean anyElementEnabled = false;
        for (int i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof ActionTimeElm) {
                ActionTimeElm ate = (ActionTimeElm) ce;
                if (ate.enabled) {
                    anyElementEnabled = true;
                    break;
                }
            }
        }
        
        // If no ActionTimeElm is enabled, skip action execution
        if (!anyElementEnabled) {
            return;
        }
        
        // If paused, skip action execution
        if (isPaused) {
            return;
        }
        
        // On first call after reset/load, execute READY actions (t=0) immediately
        // Then mark simulation as started for subsequent steps
        if (!simulationStarted) {
            simulationStarted = true;
            CirSim.console("ActionScheduler: First step - simulation initialized");
            
            // Execute any READY actions (t=0) immediately without pause
            List<ScheduledAction> readyActions = new ArrayList<ScheduledAction>();
            for (ScheduledAction action : actions) {
                if (action.enabled && action.state == ActionState.READY) {
                    readyActions.add(action);
                    CirSim.console("ActionScheduler: Action #" + action.id + " at t=0 executing immediately");
                }
            }
            
            if (readyActions.size() > 0) {
                // Execute immediately without timer
                executeActionsNow(readyActions);
                ActionTimeDialog.refreshIfOpen();
            }
            
            // Continue to check other actions on this same step
        }
        
        boolean anyStateChanged = false;
        List<ScheduledAction> actionsToExecute = new ArrayList<ScheduledAction>();
        
        for (ScheduledAction action : actions) {
            if (!action.enabled || action.state == ActionState.COMPLETED) {
                continue;
            }
            
            // State machine transitions
            switch (action.state) {
                case READY:
                    // READY actions execute on first step - already handled above
                    // Should not reach here
                    break;
                    
                case PENDING:
                    // Check if we've reached the action time
                    boolean timeReached = currentTime >= action.actionTime;
                    
                    if (timeReached) {
                        // Check if this is a stop simulation action
                        if (action.stopSimulation) {
                            // Stop actions transition directly to COMPLETED
                            action.state = ActionState.COMPLETED;
                            anyStateChanged = true;
                            sim.setSimRunning(false);
                            CirSim.console("ActionScheduler: Stopped simulation at t=" + 
                                         CircuitElm.getUnitText(currentTime, "s"));
                        } else {
                            // Transition to WAITING state
                            action.state = ActionState.WAITING;
                            anyStateChanged = true;
                            actionsToExecute.add(action);
                            CirSim.console("ActionScheduler: Action #" + action.id + 
                                         " reached at t=" + currentTime + "s, entering WAITING state");
                        }
                    }
                    break;
                    
                case WAITING:
                    // Action is waiting for timer to fire
                    // Timer callback will transition to EXECUTING, then COMPLETED
                    break;
                    
                case EXECUTING:
                    // Transient state - should immediately move to COMPLETED
                    // This happens in scheduleResume timer callback
                    break;
                    
                case COMPLETED:
                    // Action is done, nothing to do
                    break;
            }
        }
        
        // If we have actions to execute, pause and schedule them ALL together
        if (actionsToExecute.size() > 0) {
            setPaused(true);
            double delay = pauseTime > 0 ? pauseTime : 0.001;
            scheduleResume(delay, actionsToExecute);
            CirSim.console("ActionScheduler: Paused - " + actionsToExecute.size() + " action(s) will execute after " + delay + "s");
        }
        
        // Refresh dialog if any actions changed state
        if (anyStateChanged) {
            ActionTimeDialog.refreshIfOpen();
        }
    }
    
    /**
     * Execute actions immediately without timer (for t=0 actions)
     */
    private void executeActionsNow(List<ScheduledAction> actions) {
        StringBuilder allActionText = new StringBuilder();
        
        for (ScheduledAction action : actions) {
            // Transition to EXECUTING state
            action.state = ActionState.EXECUTING;
            
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
            
            // Transition to COMPLETED state
            action.state = ActionState.COMPLETED;
        }
        
        // Set combined display message
        if (allActionText.length() > 0) {
            displayMessage = allActionText.toString();
        }
        
        CirSim.console("ActionScheduler: Executed " + actions.size() + " action(s) immediately at t=0");
    }
    
    /**
     * Find a slider by name and set its value
     */
    private void setSliderValue(String name, double value) {
        for (int i = 0; i < sim.adjustables.size(); i++) {
            Adjustable adj = sim.adjustables.get(i);
            if (adj.sliderText != null && adj.sliderText.equals(name)) {
                // Use the Adjustable's own method to set the value
                adj.setSliderValue(value);
                // Also need to update the element's value
                EditInfo ei = adj.elm.getEditInfo(adj.editItem);
                if (ei != null) {
                    ei.value = value;
                    adj.elm.setEditValue(adj.editItem, ei);
                }
                // if (adj.elm.nonLinear()) {
                    adj.elm.sim.analyzeFlag = true;
                // }
                return;
            }
        }
        CirSim.console("ActionScheduler: Warning - Slider '" + name + "' not found");
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
                CirSim.console("ActionScheduler: Loaded pause time = " + pauseTime + "s");
            } catch (Exception e) {
                CirSim.console("ActionScheduler: Error loading pause time: " + e.getMessage());
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
                CirSim.console("ActionScheduler: Error loading action: " + e.getMessage());
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
        for (int i = 0; i < sim.adjustables.size(); i++) {
            Adjustable adj = sim.adjustables.get(i);
            if (adj.sliderText != null && adj.sliderText.equals(name)) {
                EditInfo ei = adj.elm.getEditInfo(adj.editItem);
                if (ei != null) {
                    return ei.value;
                }
            }
        }
        return 0.0;
    }
    
    /**
     * Get formatted value for a slider/action, checking element for custom formatting
     */
    private String getFormattedSliderValue(String sliderName, double value) {
        // Find the adjustable with this name
        for (int i = 0; i < sim.adjustables.size(); i++) {
            Adjustable adj = sim.adjustables.get(i);
            if (adj.sliderText != null && adj.sliderText.equals(sliderName)) {
                EditInfo ei = adj.elm.getEditInfo(adj.editItem);
                if (ei != null) {
                    // Check if element has custom slider text formatting
                    try {
                        String customText = adj.elm.getSliderUnitText(adj.editItem, ei, value);
                        if (customText != null)
                            return customText;
                    } catch (Exception e) {
                        // Element doesn't have custom formatting
                    }
                    // Use default formatting
                    return EditDialog.unitString(ei, value);
                }
            }
        }
        // Fallback to simple format
        return CircuitElm.showFormat.format(value);
    }
}
