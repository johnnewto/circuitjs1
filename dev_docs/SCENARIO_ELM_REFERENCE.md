# ScenarioElm Reference

A non-electrical circuit element that applies a time-windowed override to a named `ComputedValues` variable. Use it to model structural changes, policy shocks, or regime shifts without editing equations.

**Implementation**: [ScenarioElm.java](../src/com/lushprojects/circuitjs1/client/ScenarioElm.java)  
**Dump type**: `236`  
**Menu**: Economics → Add Scenario

---

## Concept

`ScenarioElm` registers a **scenario override** on a named target during a configured time window `[startTime, endTime]`. While active, the override is applied to every call to `ComputedValues.getComputedValue()` and `ComputedValues.getConvergedValue()` for that target, so all consumers (equation tables, displays, scopes) see the modified value automatically.

Multiple `ScenarioElm` instances can target the same variable; overrides are applied in iteration order (HashMap). For deterministic stacking, use a single element per target.

---

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| Target Name | string | `alpha0` | Name of the `ComputedValues` variable to override |
| Mode | int 0–2 | `0` (ADD) | How the magnitude is applied (see table below) |
| Start Time | double | `20` | Simulation time (s) when override becomes active |
| End Time | double | `-1` | Simulation time (s) when override ends; `-1` = forever |
| Magnitude | double | `10` | Value used according to mode |
| Enabled | bool | `true` | If false, no override is ever applied |
| Reset Plots on Activate | bool | `true` | Clear scope graphs when the scenario activates |
| Open Plotly Viewer on Activate | bool | `false` | Open the Plotly Viewer dialog when the scenario activates |

### Override Modes

| Mode | Value | Effect |
|------|-------|--------|
| ADD | `0` | `result = baseValue + magnitude` |
| MULTIPLY | `1` | `result = baseValue * magnitude` |
| REPLACE | `2` | `result = magnitude` |

---

## Display

The element renders a labelled box showing:

```
┌────────────────────────┐
│        Scenario        │
│  target=alpha0         │
│  mode=ADD  mag=10      │
│  t=[20, ∞]             │
└────────────────────────┘
```

When **disabled**, a diagonal cross-out line is drawn and the background turns grey.

---

## Interaction with Equation Tables

`EquationTableElm.getDisplayValue()` calls `ComputedValues.getComputedValue()` for non-flow PARAM_MODE rows, so the displayed value and the value used in dependent equations automatically reflect the active scenario override.

---

## Interaction with ActionScheduler

`ActionScheduler.setActionTargetValue()` also uses `ComputedValues.setScenarioOverride()` when the named target is not a slider. The source key is `"ActionScheduler"`, while each `ScenarioElm` uses a unique key `"ScenarioElm@<hashCode>"`, so both mechanisms can coexist on the same target without conflicts.

---

## Lifecycle

| Event | Behaviour |
|-------|-----------|
| `doStep()` | Evaluates active window; calls `setScenarioOverride(active=true/false)` |
| Transition inactive → active | Also calls `sim.onScenarioActivated(resetPlots, openPlotly)` once |
| `reset()` | Clears override and resets `wasActive = false` |
| `delete()` | Clears override before removal |

---

## File Format (dump)

```
236 x1 y1 x2 y2 flags targetName mode startTime endTime magnitude enabled resetPlotsOnActivate openPlotlyOnActivate
```

Example:
```
236 1232 656 1296 656 164 alpha0 0 20.0 -1.0 10.0 true true false
```

---

## Typical Use Cases

- **Policy shock**: multiply `alpha0` (propensity to spend) by `1.2` from `t=30` onwards
- **Structural break**: replace an interest rate parameter at a specific time
- **Regime window**: add a temporary stimulus (`startTime=10, endTime=20`)
- **Scenario comparison**: enable/disable multiple `ScenarioElm` instances to compare paths

---

## Related

- [ACTION_SCHEDULER_QUICK_REFERENCE.md](ACTION_SCHEDULER_QUICK_REFERENCE.md) — timed actions that also use `setScenarioOverride`
- [EQUATION_TABLE_REFERENCE.md](EQUATION_TABLE_REFERENCE.md) — PARAM_MODE rows that consume overrides
- [PLOTLY_VIEWER_FEATURE.md](PLOTLY_VIEWER_FEATURE.md) — viewer optionally opened on activate
