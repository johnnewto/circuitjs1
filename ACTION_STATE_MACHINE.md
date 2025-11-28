# Action Scheduler State Machine

## Overview

The ActionScheduler now uses a clear state machine to manage action execution, making the timing behavior easier to understand and delaying status display by one simulation step.

## State Machine Design

### States

```
PENDING → WAITING → EXECUTING → COMPLETED
                ↓
            (stop action)
                ↓
            COMPLETED
```

#### PENDING
- **Description**: Action has not yet reached its trigger time
- **Visual**: Yellow ⏱ (clock icon)
- **Transitions to**: WAITING when `sim.t >= action.actionTime`

#### WAITING
- **Description**: Action time reached, timer started, waiting for execution
- **Visual**: Orange ⏸ (pause icon)
- **Duration**: Configured by `pauseTime` (or 0.001s if pauseTime=0)
- **Transitions to**: EXECUTING when timer fires or Run/Stop pressed

#### EXECUTING (Transient)
- **Description**: Timer fired, action is currently executing
- **Visual**: Blue ▶ (play icon)
- **Actions performed**:
  - Slider value changed
  - Display message built and shown
  - Circuit analysis triggered if needed
- **Transitions to**: COMPLETED immediately after execution

#### COMPLETED
- **Description**: Action fully executed and done
- **Visual**: Green ✓ (checkmark) with green background
- **No further transitions**

## Key Implementation Details

### One-Step Display Delay

The state machine delays the visual status change by one step:

1. **t=10.0s**: Action time reached
   - State: PENDING → WAITING
   - Status: Shows ⏸ (pause icon, orange)
   - Simulation: Pauses
   - Timer: Starts
   - **Action NOT yet executed**

2. **During pause** (t=10.0s to t=10.0s+pauseTime):
   - State: WAITING
   - Status: Still shows ⏸ (pause icon)
   - User sees circuit in **pre-action state**
   - User can press Run/Stop to trigger immediately

3. **Timer fires** (or Run/Stop pressed):
   - State: WAITING → EXECUTING → COMPLETED
   - **Action executes NOW**: Slider changes, message displays
   - Simulation: Resumes
   
4. **Next draw cycle**:
   - State: COMPLETED
   - Status: Shows ✓ (checkmark, green)
   - User sees circuit in **post-action state**

### Timeline Visualization

```
Time:     t=9.9s      t=10.0s         t=10.0s-15.0s        t=15.0s           t=15.0s+
State:    PENDING     WAITING         WAITING              EXECUTING→        COMPLETED
                                                           COMPLETED
Symbol:   ⏱ (yellow)  ⏸ (orange)      ⏸ (orange)          ▶ (blue)          ✓ (green)
Sim:      Running     Paused          Paused               Resuming          Running
Slider:   OLD VALUE   OLD VALUE       OLD VALUE            NEW VALUE         NEW VALUE
Display:  (none)      (none)          (none)               Message shows     Message shows
Circuit:  Pre-action  Pre-action      Pre-action           Post-action       Post-action
                      ↑               ↑                    ↑
                      Timer starts    User can override    Timer fires &
                                     with Run/Stop        action executes
```

### Benefits

- **Clear separation**: Visual status shows COMPLETED only after action executes
- **Predictable behavior**: Each state has clear entry/exit conditions
- **Educational value**: User can see "before" state during WAITING
- **Manual override**: Run/Stop immediately triggers pending action
- **Debuggable**: Console logs show all state transitions

## State Transitions in Code

### stepFinished() - Main State Machine Loop

```java
switch (action.state) {
    case PENDING:
        if (currentTime >= action.actionTime) {
            action.state = ActionState.WAITING;
            setPaused(true);
            scheduleResume(delay, action);
        }
        break;
        
    case WAITING:
        // Timer callback will handle transition
        break;
        
    case EXECUTING:
        // Transient - handled in timer callback
        break;
        
    case COMPLETED:
        // Done - no further action
        break;
}
```

### scheduleResume() - Timer Callback

```java
resumeTimer = new Timer() {
    public void run() {
        // WAITING → EXECUTING
        action.state = ActionState.EXECUTING;
        
        // Execute the action
        setSliderValue(action.sliderName, action.sliderValue);
        buildDisplayMessage(action);
        
        // EXECUTING → COMPLETED
        action.state = ActionState.COMPLETED;
        
        sim.setSimRunning(true);
    }
};
```

### clearPausedState() - Manual Trigger

```java
if (resumeTimer != null) {
    resumeTimer.cancel();
    resumeTimer.run();  // Immediately execute timer callback
}
```

## Visual Status Indicators

| State | Symbol | Color | Background | Meaning |
|-------|--------|-------|------------|---------|
| PENDING | ⏱ | Yellow | None | Waiting for action time |
| WAITING | ⏸ | Orange | None | Paused, timer running |
| EXECUTING | ▶ | Blue | None | Action executing (transient) |
| COMPLETED | ✓ | Green | Green (#c8e6c9) | Action done |

## Backward Compatibility

The `triggered` boolean field is still maintained for backward compatibility:
- Set to `true` when entering WAITING state
- Equivalent to `state != PENDING`
- Kept for any external code that might check this field

## Console Logging

State transitions are logged for debugging:

```
ActionScheduler: Action #1 reached at t=10.0s, entering WAITING state
ActionScheduler: Paused - action will execute after 5.0s
ActionScheduler: Action #1 entering EXECUTING state
ActionScheduler: Action #1 entering COMPLETED state
ActionScheduler: Auto-resuming after 5.0s pause - action executed: Resistance=1000.0
```

## Reset Behavior

When simulation is reset (via Reset button or `sim.resetAction()`):
- All actions: state → PENDING
- All actions: triggered → false
- Clear display message
- Cancel any pending timers
- Clear paused state

## Testing the State Machine

To verify the state machine behavior:

1. **Create action at t=10s** with pauseTime=5s
2. **t=10.0s**: Status shows orange ⏸ (WAITING), simulation pauses
3. **During pause**: Status still orange ⏸, circuit in pre-action state
4. **t=15.0s**: Timer fires, action executes, status becomes green ✓
5. **Next frame**: Status shows green ✓ (COMPLETED), circuit in post-action state

Alternatively, press Run/Stop during WAITING state to trigger immediately.

## Implementation Files

- `ActionScheduler.java`: Core state machine logic
- `ActionTimeElm.java`: Visual status display based on state
- `ActionTimeDialog.java`: Action management UI (uses state for display)

## Related Documentation

- `AUTO_CREATION_FEATURE_SUMMARY.md`: Overview of action scheduler features
- `AUTOCOMPLETE_FEATURE.md`: Slider name autocomplete
- Previous implementation used simple `triggered` boolean flag
