# Action Scheduler - Quick Reference

## Access
**Menu**: Dialogs → Action Time Schedule...

## What It Does
Manages timed parameter changes during simulation without needing circuit elements.

## Key Features
- ✅ Create/Edit/Delete scheduled actions
- ✅ Control slider values at specific times
- ✅ Real-time status and countdown
- ✅ Automatic save/load with circuit
- ✅ Reset restores initial values
- ✅ Independent of ActionTimeElm

## Quick Start

### 1. Create an Action
1. Open dialog from Dialogs menu
2. Click **"Add Action"**
3. Set:
   - Action Time: `1.0` (seconds)
   - Slider Name: Choose from dropdown
   - Before Value: `2.0`
   - After Value: `8.0`
   - Before/After Text: Display text
   - Enabled: ✓
4. Click **"Save"**

### 2. Run Simulation
- Press spacebar to start
- Watch status column for countdown
- Action executes automatically at scheduled time
- Slider changes immediately

### 3. Reset
- Press R to reset simulation
- Actions restore "before" values
- Ready to run again

## Dialog Layout

| Column | Shows |
|--------|-------|
| Time | When action executes |
| Slider | Which slider to control |
| Before→After | Value transition |
| Text | Current display text |
| Status | ⏱ Countdown / ✓ Done / ⚠ Pending |
| Actions | Edit / Delete buttons |

## Status Colors
- 🟢 **Green**: Action executed
- 🟡 **Yellow**: About to execute (< 0.1s)
- 🔴 **Red**: Pending/overdue

## Buttons
- **Add Action**: Create new action
- **Clear All**: Delete all (with confirmation)
- **Refresh**: Update table manually
- **Close**: Hide dialog

## File Format
Actions saved as comments in circuit files:
```
% ActionSchedule
% AS id time slider before after preText postText enabled
```

## Use Cases
- Parameter sweeps
- State machine simulation
- Fault injection
- Educational demos
- System dynamics

## Example
```
t=1.0s: Set frequency = 100 Hz
t=2.0s: Set frequency = 500 Hz
t=3.0s: Set frequency = 1000 Hz
```

## Tips
- Actions sorted by time automatically
- Slider dropdown shows available sliders
- Text supports newlines (displayed in table)
- Console shows execution messages
- Actions persist with circuit file

## Test Circuit
`tests/action-scheduler-test.txt`

## Documentation
Full docs: `ACTION_SCHEDULER_IMPLEMENTATION.md`
