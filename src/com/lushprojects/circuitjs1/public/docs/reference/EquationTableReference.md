# EquationTableElm — Source-Accurate Reference

## Overview

`EquationTableElm` is a table element where each row defines a named equation output.
It supports two simulator-wide execution modes:

- **MNA (Electrical) mode**: rows stamp voltage/current behavior into the circuit matrix.
- **Pure Computational mode**: rows evaluate and publish through `ComputedValues` only.

**Dump type:** `266`  
**Source:** `src/com/lushprojects/circuitjs1/client/EquationTableElm.java`  
**Renderer:** `src/com/lushprojects/circuitjs1/client/EquationTableRenderer.java`  
**Max rows:** `256`

## Quick Facts

| Property | Value |
|---|---|
| Post count | `0` |
| High-impedance | `getConnection()` always returns `false` |
| Ground connection | `hasGroundConnection()` always returns `false` |
| Nonlinear | effectively true for all non-comment rows |
| Global mode switch | `sim.equationTableMnaMode` |

## Per-Row Data Model (`EquationRow`)

Each row stores:

- `outputName`
- `equation`
- `initialEquation`
- `sliderVarName`
- `sliderValue`
- `outputMode` (`VOLTAGE_MODE`, `PARAM_MODE`; legacy `FLOW_MODE` inputs are normalized on load)
- `targetNodeName` (legacy flow-compatibility metadata)
- `shuntResistance` (legacy flow-compatibility metadata, default `1`)
- `useBackwardEuler` (persisted, currently not used in mode-specific stamping)

Runtime state also includes parsed expressions, `ExprState`, node ids, voltage-source index, legacy `.flow` alias state, and Newton Jacobian debug flags.

## Row Output Modes

### `VOLTAGE_MODE` (default)

Equation result is treated as a voltage and applied via a voltage source.

- Stamps nonlinearity at voltage-source equation row.
- Stamps `sim.stampVoltageSource(0, node, vs)`.
- Stamps a tiny load resistor `1e9 Ω` from node to ground.
- In `doStep()`: stamps RHS value (or Newton Jacobian linearization when eligible).

### Legacy Flow Compatibility

`FLOW_MODE` is no longer a runtime stamping mode.

- Older files using `mode=flow`, `mode=stock`, or legacy flow ordinals still load.
- Those rows now execute through the voltage-mode stamping path.
- If legacy source/target metadata is present, compatibility aliases are still published:
  - `<source>.flow = -value` for two-node legacy flow metadata
  - `<target>.flow = +value` for two-node legacy flow metadata
  - source-only publish for ground-target case
- New content should use `VOLTAGE_MODE` or `PARAM_MODE`.

### `PARAM_MODE`

Computation-only row.

- No matrix stamping.
- No nodes or voltage sources.
- Equation value is published to `ComputedValues[outputName]`.

## Comment Rows

A row is a comment if `outputName.trim().startsWith("#")`.

- Comment rows are non-simulating.
- They are excluded from parsing/stamping/evaluation behavior.
- UI helpers expose comment text without `#`.

## Simulation Lifecycle

### `stamp()`

1. Updates row classifications.
2. Ensures pre-registration of parameter/computed names.
3. Returns immediately in pure-computational global mode.
4. Resolves labeled/internal nodes.
5. Dispatches per-row stamp through mode handlers (`VOLTAGE`/`PARAM`).

### `postStamp()`

Resolves expression node references to global slot indices (`E_GSLOT`) using `sim.nameToSlot`.

### `startIteration()`

Ensures name registries are up to date.

### `doStep()`

Per row:

- If `t=0` and `initialEquation` exists, performs initial-value path.
- Otherwise evaluates via row mode handler.
- Runs convergence checking (`checkEquationConvergence`).

### `stepFinished()`

- Re-publishes outputs to `ComputedValues`.
- Re-publishes legacy `.flow` compatibility keys and marks them computed-this-step when present.
- Commits integration state (`ExprState.commitIntegration`).
- Updates last-values state for next timestep.

### `reset()` / `delete()`

- `reset()` clears runtime row state and re-syncs global name registries.
- `delete()` unregisters this element’s tracked names from global registries.

## Initial Value Handling (`initialEquation`)

At `t=0` for rows with an initial expression:

1. First subiteration stamps placeholder zero (voltage RHS path).
2. Next subiteration evaluates initial expression.
3. Seeds row output state and expression history.
4. Immediately publishes output (and legacy `.flow` compatibility aliases when present).

## Global Label Coordination (MNA)

`coordinateLabelsForStamp(elmList)` performs two passes across all MNA Equation Tables:

1. Pre-registers non-FLOW output labels (VOLTAGE/PARAM filtering keeps FLOW separate).
2. Auto-creates missing FLOW endpoints from reserved internal nodes, then logs unresolved diagnostics.

This avoids stamp-order issues for inter-table references.

## Convergence

Row convergence uses magnitude-scaled adaptive tolerance:

- Base tolerance from `sim.equationTableConvergenceTolerance` (fallback default `0.001`).
- Tolerance is relaxed at higher subiteration counts.
- Rows containing `diff()` get additional tolerance relaxation.
- Early convergence checks for `diff()` rows are skipped for first few subiterations.

If a row exceeds limit, it marks `sim.converged = false` and stores failure diagnostics.

## Naming and Parsing

`setOutputName()` accepts combined source/target syntax and splits it via `parseCombinedName()`.
Accepted separators include:

- `->`
- `-||-`
- `→`
- `⊣⊢`
- `,`

Display helpers:

- `getDisplayOutputName()` returns ASCII-safe `source->target` (dump-friendly).
- `getUIDisplayOutputName()` returns UI arrow form (`source→target`).

## Serialization Format

`dump()` writes per row (current format):

1. combined output name (`source` or `source->target`)
2. equation
3. initial equation
4. slider var
5. slider value
6. mode ordinal (`0=VOLTAGE`, `1=FLOW`, `3=PARAM`; `2` reserved legacy compatibility)
7. empty target compatibility token
8. legacy capacitance compatibility token (`1.0`)
9. shunt resistance
10. backward-Euler flag (`0/1`)

Loader compatibility recognizes:

- `10` tokens/row (newest)
- `9` tokens/row
- `8` tokens/row
- `5` tokens/row (legacy)

## What Is Not In Current `EquationTableElm`

The current implementation does **not** include:

- `STOCK_MODE` row mode
- alias-row classification/registration flow
- stock companion-capacitor stamping behavior

If older docs mention these, they describe earlier/experimental behavior, not the current source.
