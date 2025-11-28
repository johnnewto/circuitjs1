# Action Scheduler System - Complete Implementation

## Overview

The Action Scheduler is a standalone system for managing timed actions in CircuitJS1 simulations. It is completely independent of ActionTimeElm elements and provides a centralized way to schedule parameter changes at specific simulation times.

## Architecture

### Components

1. **ActionScheduler** - Central singleton that manages all scheduled actions
2. **ActionTimeDialog** - UI for creating, editing, and viewing actions  
3. **CirSim Integration** - Hooks into simulation loop and file I/O

### Design Philosophy

Unlike ActionTimeElm which requires individual circuit elements, the Action Scheduler:
- Stores all actions in a central registry
- Persists with circuit files (not as separate elements)
- Provides CRUD operations through a dialog interface
- Executes independently during simulation

## Key Features

### 1. Action Management
- **Create**: Add new timed actions with full parameter control
- **Edit**: Modify existing actions
- **Delete**: Remove individual actions
- **Clear All**: Reset entire schedule

### 2. Scheduled Actions
Each action contains:
- **Action Time**: When to execute (in seconds)
- **Slider Name**: Target slider to modify
- **Before Value**: Initial slider value (set on reset)
- **After Value**: Slider value after action executes
- **Before Text**: Display text before action time
- **After Text**: Display text after action time
- **Enabled**: Whether action is active

### 3. Execution
- Actions execute automatically when simulation time reaches action time
- Slider values change immediately
- "Before" values are restored on simulation reset
- Console logging for debugging

### 4. Persistence
- Actions saved as `% AS` lines in circuit files
- Format: `% AS id time sliderName valueBefore valueAfter preText postText enabled`
- Loaded automatically when circuit is loaded
- Survives circuit save/load cycles

## Usage

### Opening the Dialog

**Menu**: Dialogs → Action Time Schedule...

The dialog appears on the right side of the screen and stays open while you work.

### Creating an Action

1. Click **"Add Action"** button
2. Fill in the form:
   - **Action Time (s)**: When to execute (e.g., 1.0, 2.5, 4.0)
   - **Slider Name**: Choose from dropdown of available sliders
   - **Before Value**: Initial value for slider
   - **After Value**: Value after execution
   - **Before Text**: Text to display before action
   - **After Text**: Text to display after action
   - **Enabled**: Check to activate
3. Click **"Save"**

### Editing an Action

1. Find the action in the table
2. Click **"Edit"** button in the Actions column
3. Modify values in the form
4. Click **"Save"**

### Deleting an Action

1. Find the action in the table
2. Click **"Delete"** button in the Actions column
3. Confirm deletion

### Running with Actions

1. Create your actions
2. Start simulation (spacebar or Run button)
3. Watch the **Status** column for countdown
4. Actions execute automatically at their scheduled times
5. Slider changes immediately when action fires
6. Reset (R key) to restore "before" values

## Dialog Features

### Table Columns

| Column | Description |
|--------|-------------|
| **⋮⋮** | Drag handle for reordering (click and drag to new position) |
| **Time** | Action execution time (formatted with units) |
| **Slider** | Name of slider to control |
| **Value** | Target value for slider |
| **Text** | Current display text (changes at action time) |
| **Status** | ⏱ Countdown / ✓ Done / ⚠ Pending |
| **Actions** | Copy, Paste, Edit, and Delete buttons |

### Status Indicators

- **⏱ In X.XX s** - Countdown to action (yellow text)
- **✓ Done** - Action has executed (green text, green row)
- **⚠ Pending** - Action time passed but not executed (red text)

### Visual Feedback

- **Green background**: Action executed
- **Yellow background**: Action about to execute (< 0.1s)
- **Alternating rows**: Easier reading
- **Current time display**: Track simulation progress

### Buttons

- **Add Action**: Create new scheduled action
- **Clear All**: Delete all actions (with confirmation)
- **Refresh**: Manually update table
- **Close**: Hide dialog

### Action Buttons (per row)

- **Copy**: Copy this action to clipboard for pasting elsewhere
- **Paste**: Paste previously copied action (creates new action with copied parameters)
- **Edit**: Modify action parameters
- **Del**: Delete this action (with confirmation)

### Drag and Drop

- Click and hold the **⋮⋮** drag handle at the start of any row
- Drag the row to a new position
- Release to reorder actions
- Visual feedback shows drag state (semi-transparent row) and drop target (blue border)

## Implementation Details

### ActionScheduler.java

Singleton class managing all actions:

```java
// Get instance
ActionScheduler scheduler = ActionScheduler.getInstance(sim);

// Add action
ScheduledAction action = new ScheduledAction(0, 1.0, "slider1", 
    2.0, 8.0, "Before", "After", true);
scheduler.addAction(action);

// Update action
scheduler.updateAction(action);

// Delete action
scheduler.deleteAction(action.id);

// Get all actions
List<ScheduledAction> actions = scheduler.getAllActions();

// Execute actions (called by CirSim)
scheduler.step(currentTime);

// Reset (called on simulation reset)
scheduler.reset();

// Save/load
String dump = scheduler.dump();
scheduler.load(line);
```

### ScheduledAction Class

```java
public static class ScheduledAction {
    public int id;                      // Unique identifier
    public double actionTime;           // Execution time
    public String sliderName;           // Target slider
    public double sliderValueBefore;    // Initial value
    public double sliderValueAfter;     // Final value
    public String preText;              // Display before
    public String postText;             // Display after
    public boolean enabled;             // Is active
    public boolean triggered;           // Has executed
}
```

### CirSim Integration

**Simulation Loop** (`updateCircuit()`):
```java
// After runCircuit completes
ActionScheduler scheduler = ActionScheduler.getInstance(this);
scheduler.step(t);
```

**Reset** (`resetAction()`):
```java
// Reset scheduler when simulation resets
ActionScheduler scheduler = ActionScheduler.getInstance(this);
scheduler.reset();
```

**Save** (`dumpCircuit()`):
```java
// Add scheduler dump to circuit file
ActionScheduler scheduler = ActionScheduler.getInstance(this);
String schedulerDump = scheduler.dump();
if (schedulerDump != null && !schedulerDump.isEmpty()) {
    dump += schedulerDump;
}
```

**Load** (`readCircuit()`):
```java
// Parse % AS lines
if (settingType.equals("AS")) {
    ActionScheduler scheduler = ActionScheduler.getInstance(this);
    scheduler.load(line);
}

// Clear on new circuit
ActionScheduler scheduler = ActionScheduler.getInstance(this);
scheduler.clearAll();
```

### File Format

Actions are stored as comment lines in circuit files:

```
% ActionSchedule
% AS 1 1.0 MySlider 2.0 8.0 Before\nTime\n1s After\nTime\n1s true
% AS 2 2.5 MySlider 5.0 10.0 Idle Active true
% AS 3 4.0 MySlider 3.0 1.0 Stage\n1 Stage\n2 true
```

Format: `% AS id time sliderName valueBefore valueAfter preText postText enabled`

- Text with spaces is escaped using CustomLogicModel.escape/unescape
- Newlines in text are escaped as `\n`
- Boolean enabled flag (true/false)

## Use Cases

### 1. Parameter Sweep Testing
```
t=0.5s: Set frequency = 100 Hz
t=1.0s: Set frequency = 500 Hz  
t=1.5s: Set frequency = 1000 Hz
```

### 2. State Machine Simulation
```
t=1.0s: Enter State A (set control = 1)
t=2.0s: Enter State B (set control = 2)
t=3.0s: Enter State C (set control = 3)
```

### 3. Educational Demonstrations
```
t=1.0s: Normal operation
t=2.0s: Fault injection (resistance = 1MΩ)
t=3.0s: Recovery (resistance = 1kΩ)
```

### 4. System Dynamics
```
t=0s: Initial population = 100
t=5s: Growth phase (rate = 2.0)
t=10s: Decline phase (rate = 0.5)
```

## Advantages Over ActionTimeElm

### Centralized Management
- All actions in one place
- No need to place elements on canvas
- Easy to see complete schedule

### Better UI/UX
- Table view with sorting
- Edit/delete without right-click menus
- Dropdown for slider selection
- Form-based editing

### Persistent Storage
- Saved with circuit file
- Not dependent on element positions
- Survives circuit modifications

### Programmatic Control
- Easy to add actions from code
- Clear API for CRUD operations
- Better for scripting/automation

## Performance

- Minimal overhead: O(n) check per simulation step
- Actions sorted by time for efficiency
- Only enabled actions are checked
- No canvas rendering required

## Debugging

Console messages show action execution:
```
ActionScheduler: Executed action #1 at t=1.0s: MySlider = 8.0
ActionScheduler: Executed action #2 at t=2.5s: MySlider = 10.0
```

Warnings for missing sliders:
```
ActionScheduler: Warning - Slider 'BadName' not found
```

## Limitations

1. **Sliders Only**: Can only control sliders (not arbitrary parameters)
2. **No Complex Logic**: Actions are simple value changes
3. **Time-Based Only**: Cannot trigger on conditions
4. **No Loops**: Actions execute once per simulation

## Future Enhancements

Potential improvements:
- Conditional actions (trigger when voltage > threshold)
- Action sequences (chain actions together)
- Import/export schedule separately
- Timeline visualization
- Keyboard shortcuts
- Templates for common patterns
- Undo/redo for action editing

## Migration from ActionTimeElm

To convert existing ActionTimeElm elements:

1. Open Action Time Dialog
2. For each ActionTimeElm:
   - Note its parameters
   - Click "Add Action"
   - Fill in matching values
   - Save
3. Delete ActionTimeElm elements
4. Test simulation
5. Save circuit (actions now in file)

## Testing

Test circuit: `tests/action-scheduler-test.txt`

This circuit includes:
- 3 scheduled actions
- Slider controlled by actions
- Simple RC circuit for timing reference

## Files

### New Files
- `ActionScheduler.java` - Core scheduler logic
- `ActionTimeDialog.java` - UI (completely rewritten)
- `tests/action-scheduler-test.txt` - Test circuit

### Modified Files
- `CirSim.java`:
  - Added `ActionScheduler.step()` call in simulation loop
  - Added `ActionScheduler.reset()` call in reset
  - Added scheduler dump to `dumpCircuit()`
  - Added scheduler load to `readCircuit()`
  - Added scheduler clear when loading new circuit
  - Menu item already existed

## Summary

The Action Scheduler provides a modern, centralized approach to managing timed parameter changes in CircuitJS1. It offers better usability, persistence, and maintainability compared to element-based approaches, while remaining fully integrated with the simulation engine.
