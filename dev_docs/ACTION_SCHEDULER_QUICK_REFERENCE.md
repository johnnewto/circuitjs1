# Action Scheduler - Quick Reference

**Implementation**: [ActionScheduler.java](../src/com/lushprojects/circuitjs1/client/ActionScheduler.java), [ActionTimeDialog.java](../src/com/lushprojects/circuitjs1/client/ActionTimeDialog.java)

## Access
**Menu**: Dialogs → Action Time Schedule...

## What It Does
Manages timed parameter changes during simulation. Actions fire at a scheduled simulation time and update a named target (PARAM_MODE equation-table variable or ComputedValue).

## Key Features
- ✅ Create/Edit/Delete scheduled actions
- ✅ Target PARAM_MODE variables by name (dropdown shows only valid targets)
- ✅ Absolute or relative value expressions (`+x`, `-x`, `*x`, `=x`)
- ✅ Optional pause delay before applying
- ✅ Stop-simulation action type
- ✅ Real-time status (state machine: PENDING → WAITING → EXECUTING → COMPLETED)
- ✅ Automatic save/load with circuit (native format + SFCR `@action` block)
- ✅ Independent of ActionTimeElm (display element)

## Quick Start

### 1. Create an Action
1. Open dialog from Dialogs menu
2. Click **"Add Action"**
3. Set:
   - **Action Time**: `1.0` (seconds)
   - **Target Name**: choose from dropdown (PARAM_MODE adjustable rows only)
   - **Value / Expr**: `50` (absolute) or `+10` / `-5` / `*0.5` / `=50` (relative/explicit)
   - **Display Text**: shown on scope and ActionTimeElm at trigger time
   - **Enabled**: ✓
4. Click **"Save"**

### 2. Run Simulation
- Press spacebar to start
- Action executes automatically at the scheduled time
- Target value updates immediately (after any configured pause delay)

### 3. Reset
- Press R to reset simulation
- All actions return to PENDING / READY state
- Target overrides cleared

## Value / Expression Syntax

| Input | Meaning |
|-------|---------|
| `50` | Set target to 50 (absolute) |
| `=50` | Set target to 50 (explicit absolute) |
| `+10` | Add 10 to current value at trigger time |
| `-5` | Subtract 5 from current value at trigger time |
| `*0.5` | Multiply current value by 0.5 at trigger time |

Relative expressions resolve the current target value **once** when the action is triggered, then apply after the pause delay.

## Target Resolution

The dropdown in the edit dialog only lists **PARAM_MODE adjustable rows** from `EquationTableElm` instances. If a target name exists but is not PARAM_MODE adjustable, it is shown in **red** with a warning tooltip.

At runtime, `setActionTargetValue()` first looks for a matching slider, then falls through to `ComputedValues` (via `setScenarioOverride`) for non-slider targets.

## Dialog Layout

| Column | Shows |
|--------|-------|
| ⋮⋮ | Drag handle (reserved) |
| Time | When action executes |
| Target (Adjustable Param) | Variable name (red = not PARAM_MODE adjustable) |
| Value | Resolved value or expression |
| Text | Display text shown at trigger |
| Enabled | Checkbox |
| Stop | Stop simulation flag |
| Actions | Edit / Delete buttons |

## Action State Machine

| State | Meaning |
|-------|---------|
| `PENDING` | Waiting for trigger time |
| `READY` | Action at t=0, executes on first step |
| `WAITING` | Time reached, pause timer running |
| `EXECUTING` | Transient — value being applied |
| `COMPLETED` | Fully executed |

## Configuration

**Pause Time** (`pauseTime`): seconds to wait after the action time before the value is applied. Set in the dialog footer.

## File Format (Native)

Actions saved as comments in circuit files:

```
% AST pauseTime
% AS id time targetName value preText postText enabled stopSimulation valueExpression
```

- `valueExpression` is empty for absolute values; contains e.g. `+10` for relative.
- Legacy `AAT` / `APT` animation-time lines are silently ignored on load.

## SFCR Format

Actions are exported/imported as an `@action` block:

```
@action
  pauseTime: 0

| time | target | value | text | enabled | stop |
|------|--------|-------|------|---------|------|
| 30   | alpha0 | =10   | propensity to spend | true | false |
| 40   | alpha0 | +10   | shock               | true | false |
@end
```

## Use Cases
- Parameter shocks (add/multiply/set a variable at a point in time)
- Parameter sweeps across multiple times
- Stop simulation at a specific time
- Educational demos with labelled events on scope

## Tips
- Actions sorted by time automatically
- Non-adjustable targets shown in red — fix the equation row with `mode=param`
- Console logs show each state transition
- Relative changes use the target value at the moment of triggering
