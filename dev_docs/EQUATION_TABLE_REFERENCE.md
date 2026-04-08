# EquationTableElm — Complete Reference (Source-Accurate Feb 28)

## Overview

`EquationTableElm` is a table-based element where each row defines a named equation output.
It supports two simulator-wide execution modes:

- **MNA (Electrical) mode**: rows stamp voltage/current behavior into the circuit matrix.
- **Pure Computational mode**: rows evaluate and publish values through `ComputedValues` only.

**Dump type:** 266  
**Source:** [EquationTableElm.java](../src/com/lushprojects/circuitjs1/client/EquationTableElm.java)  
**Renderer:** [EquationTableRenderer.java](../src/com/lushprojects/circuitjs1/client/EquationTableRenderer.java)  
**Max rows:** 256

## Quick Facts

| Property | Value |
|----------|-------|
| Post count | 0 (no visible electrical posts) |
| High-impedance | Yes — `getConnection()` always returns `false` |
| Ground connection | None — `hasGroundConnection()` returns `false` |
| Nonlinear | True whenever any non-comment row exists (VOLTAGE, FLOW, PARAM rows are all evaluated) |
| Modes | MNA (electrical) or Pure Computational (global setting) |
| Mode toggle | Options → Other Options → `sim.equationTableMnaMode` |
| Newton toggle | Options → Other Options → `sim.equationTableNewtonJacobianEnabled` |

## Per-Row Data Model

Current implementation stores row state in `EquationRow` objects (not parallel arrays):

| Field | Type | Description |
|-------|------|-------------|
| `outputName` | String | Source/output node name; may be combined with target in UI input |
| `equation` | String | Main expression evaluated each step |
| `initialEquation` | String | Optional expression evaluated at `t=0` |
| `sliderVarName` | String | Slider variable available in equations |
| `sliderValue` | double | Slider variable value |
| `outputMode` | `RowOutputMode` | `VOLTAGE_MODE`, `FLOW_MODE`, or `PARAM_MODE` |
| `targetNodeName` | String | Optional target node for FLOW |
| `shuntResistance` | double | FLOW shunt resistance (`Shunt R`), default `1e9` |
| `useBackwardEuler` | boolean | **Legacy/unused** — persisted in dump for compatibility but not used by any mode handler |

Runtime fields include compiled expressions, `ExprState`, node ids, and voltage-source index.

## Row Output Modes

### VOLTAGE_MODE (Default)

Equation result is interpreted as voltage and applied to a ground-referenced output via voltage source topology.

- **Topology stamp (in `stamp()`):** `sim.stampVoltageSource(0, node, vs)`
- **Value stamp (in `doStep()`):** `sim.stampRightSide(vn, equationValue)`
- **Extra stabilization:** tiny load resistor `sim.stampResistor(node, 0, 1e9)`
- **Nodes needed:** 1 output node — uses an existing `LabeledNodeElm` with the matching name if present; otherwise allocates an internal node and registers it under that name, so it is reachable by name even without a visible labeled node on canvas.

### FLOW_MODE

Equation result is interpreted as current and stamped with `stampCurrentSource`.

- **Stamp:** `sim.stampCurrentSource(sourceNode, targetNode, flowValue)` in `doStep()`
- **Shunt R:** each non-ground FLOW endpoint stamps `sim.stampResistor(node, 0, shuntR)`
- **Default:** `shuntR = 1e9` (minimal loading, mainly stabilization)
- **Important:** lowering `Shunt R` creates a **real electrical load** to ground and changes circuit behavior
- **Single-node form (`S3`):** flow is `gnd -> S3`; positive means current injected into `S3`
- **Two-node form (`S1->S2`):** flow is `S1 -> S2`; positive means source-to-target
- **Target default:** empty or `gnd` means ground
- **Always nonlinear:** evaluated each subiteration
- **Newton path (optional):** with `sim.equationTableNewtonJacobianEnabled`, eligible FLOW rows stamp Jacobian terms into source/target KCL rows; otherwise they use direct `stampCurrentSource`
- **ComputedValues key:** FLOW rows publish magnitude under `<outputName>.flow` (sanitized to parser-safe identifier chars)
- **Important:** FLOW rows still do **not** register to `ComputedValues[outputName]` (prevents clobbering node/value channels)

### PARAM_MODE

Equation result is interpreted as a computed parameter value only.

- **Stamping:** none (no `stampVoltageSource`, no `stampCurrentSource`, no node stamping)
- **Output:** published directly to `ComputedValues[outputName]`
- **Nodes needed:** 0
- **Voltage sources needed:** 0
- **Use case:** shared coefficients/parameters/intermediate values that should not create electrical nodes
- **Always nonlinear:** evaluated each subiteration (like other dynamic rows)

### Mode Comparison

| Feature | VOLTAGE_MODE | FLOW_MODE | PARAM_MODE |
|---------|--------------|-----------|------------|
| Equation meaning | Output voltage | Flow rate | Computed parameter |
| Primary stamp | `stampVoltageSource` + RHS | `stampCurrentSource` | none |
| Newton Jacobian (optional) | VS-equation-row Jacobian + RHS adjust | Source/target KCL Jacobian + node RHS adjust | not applicable |
| Node model | Ground-referenced output | Source/target current path | no node |
| Integration | Only if expression uses `integrate()` | External to mode | None (unless expression itself uses stateful funcs) |

## Row Classification (Current Implementation)

Rows are classified as (returned by `getRowClassification(row)`):

| Classification | Condition | Behaviour |
|---|---|---|
| `comment` | `outputName` starts with `#` | Non-simulating; no expression compile, no stamping |
| `cyclic` | Output name appears in the same-period SCC (strongly connected component) set | Normal equation row; `⟳` icon shown in renderer |
| `other` | All remaining normal rows | Normal equation row |

Notes:

- Comment rows show comment text from the row name body (text after `#`).
- Cyclic row detection uses `SFCRDagBlocksViewer.getCyclicalNodeNames(sim, false, true)` — same-period deps, parameters/external sections excluded.
- `useBackwardEuler` is persisted in the dump but is currently **legacy/unused** by row mode logic.
- Bare-node equations like `A = B` are evaluated as normal equations and do **not** imply electrical node merging.

> Note: older docs may mention `constant`/`linear`/`VCVS` classifications. Those are not part of the current `EquationTableElm` behavior.

## Simulation Lifecycle

### `stamp()`

Before per-element `stamp()` calls, `CirSim.stampCircuit()` invokes the static
`EquationTableElm.coordinateLabelsForStamp()` across all `EquationTableElm` instances:

1. **Pass 1 (VOLTAGE rows only — FLOW and PARAM excluded):** pre-register output names to node numbers
  so those names resolve globally regardless of table stamp order.
2. **Pass 2 (FLOW rows only):** auto-create missing source/target endpoint labels using
  reserved internal nodes, then emit diagnostics only if an endpoint still cannot
  be resolved.

After the coordination pass, each element's own `stamp()` runs:

1. `updateRowClassifications()`
2. `refreshParameterNameRegistry()` and `refreshComputedNameRegistry()`
3. if pure mode: return (no matrix stamping)
4. `findLabeledNodes()` (resolve existing or allocate/register internal nodes)
5. per non-comment row: mode handler `stamp(row)`

### `startIteration()`

- Ensures parameter/computed registries are up to date before row evaluation.

### `doStep()`

Per row:

- if `t == 0` and `initialEquation` exists, run initial-value path
- otherwise evaluate and stamp via mode handler
- run convergence check using adaptive threshold

### Newton Jacobian path (kept)

For MNA rows, EquationTable supports an optional Newton linearization path for `VOLTAGE_MODE` and `FLOW_MODE` before falling back to direct stamping.

- Global toggle: `sim.equationTableNewtonJacobianEnabled`
- Scope:
  - `VOLTAGE_MODE` rows with usable same-period MNA references
  - `FLOW_MODE` rows with usable same-period MNA references
- Excludes stateful historical expressions (`integrate`, `diff`, `lag`, `last`, `smooth`)
- If eligible:
  - `VOLTAGE_MODE`: stamps Jacobian terms into the VS equation row plus adjusted RHS
  - `FLOW_MODE`: stamps Jacobian terms into source/target KCL rows plus adjusted node RHS terms
- If not eligible (or derivatives invalid):
  - `VOLTAGE_MODE` falls back to direct `stampRightSide`
  - `FLOW_MODE` falls back to direct `stampCurrentSource`
- Per-row debug status is exposed by:
  - `getNewtonJacobianDebugStatus(row)`
  - `wasNewtonJacobianApplied(row)`

### `stepFinished()`

- Publish non-FLOW outputs to `ComputedValues`
- Commit `ExprState` integration and update last values

## Initial Value Handling (`initialEquation`)

At `t=0`:

1. First subiteration may stamp placeholder values.
2. `evaluateInitialValue(row)` computes initial value and initializes expression integration state.
3. VOLTAGE rows stamp initial RHS voltage.
4. FLOW/PARAM rows publish initial values in their normal namespaces.
5. Initial value is registered immediately for dependent expressions.

## Expression System

Rows parse with `ExprParser` and evaluate with `Expr.eval(ExprState)`.

### Functions (commonly used)

- Stateless: `sin`, `cos`, `tan`, `exp`, `log`, `sqrt`, `abs`, `min`, `max`, `floor`, `ceil`
- Stateful: `integrate(x)`, `diff(x)`
- Historical: `last(x)` — returns `x` from the previous timestep

### Postfix Lag Syntax

As a convenience alias for `last(X)`, the parser accepts SFCR-style postfix forms:

| Postfix form | Equivalent | Notes |
|---|---|---|
| `X(-1)` | `last(X)` | Parenthesis form |
| `X[-1]` | `last(X)` | Bracket form (sfcr-compatible) |
| `X[ - 1 ]` | `last(X)` | Spaces around `-1` are ignored |

Only the index value `-1` is supported; any other index produces a parse error. These forms are recognized at the `parseTerm` stage inside `ExprParser`.

### Variable Sources

| Source | Resolution |
|--------|------------|
| Slider variable | `ExprState.values[0]` |
| Time `t` | `sim.t` |
| Named values | `ComputedValues` |
| FLOW magnitudes | `ComputedValues` key `<outputName>.flow` |
| Labeled nodes | via node/labeled-node lookup used by expression evaluation |

## Convergence

Convergence limit is adaptive and magnitude-scaled:

- relative tolerance increases with subiteration count,
- rows containing `diff()` get a 10x looser tolerance,
- early subiterations for `diff()` skip strict checks.

Formula shape:

$$limit = max(1, |current|, |last|) \times relativeTolerance$$

If `|new - last| > limit`, row marks simulator unconverged (`sim.converged = false`) and stores diagnostics.

## MNA Mode vs Pure Computational Mode

Mode is global (`sim.equationTableMnaMode`).

### MNA Mode

- Voltage rows drive circuit nodes.
- Flow rows stamp current behavior.
- Param rows are computation-only (no electrical stamp).
- Outputs are also published to `ComputedValues` for cross-expression use.

### Pure Computational Mode

- No matrix stamping.
- Rows evaluate and publish values to `ComputedValues`.

## Naming, Targets, and Separators

`setOutputName()` accepts combined source/target syntax and splits automatically.

Accepted separators:

- `->`
- `-||-`
- `→`
- `⊣⊢`
- `,`

Helpers:

- `getDisplayOutputName()` uses ASCII `source->target` (dump-safe)
- `getUIDisplayOutputName()` uses Unicode arrows/symbols for UI
- `normalizeArrows()` converts Unicode separators to ASCII internally

For `PARAM_MODE`, any explicit target is ignored for stamping because the row has no electrical connection.

## Serialization

`dump()` writes:

1. `super.dump()`
2. escaped table name
3. row count
4. per-row tokens:
   - combined output name (`source` or `source->target`)
   - equation
   - initial equation
   - slider var name
   - slider value
   - output mode ordinal
   - reserved target token (currently empty for compatibility)
  - legacy stock-token placeholder (`1.0`)
  - shunt resistance
   - backward-Euler flag (`0/1`)

Loader supports legacy formats:

- 10 tokens/row (newest; includes shunt resistance)
- 9 tokens/row (previous newest; no shunt resistance token)
- 8 tokens/row (without backward-Euler flag)
- 5 tokens/row (legacy)

Current output mode ordinals in new dumps: `0=VOLTAGE`, `1=FLOW`, `3=PARAM`.

Legacy loader compatibility: ordinal `2` (old `STOCK_MODE`) is mapped to `FLOW_MODE` when loading.

## Edit Dialog

`EquationTableEditDialog` supports:

- table name editing
- row add/delete/reorder (1..64)
- fields: Node(s), Equation, Initial (t=0), Mode, Shunt R, Integ. (legacy/disabled), Slider Var, Slider Value, Hint
- equation autocomplete
- `PARAM_MODE` disables Shunt R control for that row
- comment rows using `#` in Node(s): mode is locked to non-simulating comment behavior and equation text is treated as comment content
- apply path: set fields → parse → alloc nodes → recompute points → `needAnalyze()` + repaint

## Mouse Interaction

When hovering a row with a plain numeric equation:

- mouse wheel increments/decrements value,
- step size scales by magnitude,
- undo state is pushed,
- numeric literal is reformatted for compact display.

## Debugging and Inspection

- `DEBUG` flag in `EquationTableElm` enables detailed console logs.
- `EquationTableMarkdownDebugDialog` provides a markdown debug report with:
  - table summary,
  - row details (including Jacobian status/path),
  - labeled-node mappings,
  - `ComputedValues` checks,
  - matrix summary (`X = A⁻¹B`),
  - full circuit dump.

## Related Source Files

| File | Role |
|------|------|
| [EquationTableElm.java](../src/com/lushprojects/circuitjs1/client/EquationTableElm.java) | Core behavior, stamping, lifecycle |
| [EquationTableRenderer.java](../src/com/lushprojects/circuitjs1/client/EquationTableRenderer.java) | Drawing and row visuals |
| [EquationTableEditDialog.java](../src/com/lushprojects/circuitjs1/client/EquationTableEditDialog.java) | Editing UI and apply flow |
| [EquationTableMarkdownDebugDialog.java](../src/com/lushprojects/circuitjs1/client/EquationTableMarkdownDebugDialog.java) | Debug markdown inspection |
| [ExprParser.java](../src/com/lushprojects/circuitjs1/client/ExprParser.java) | Equation parsing |
| [ExprState.java](../src/com/lushprojects/circuitjs1/client/ExprState.java) | Stateful expression evaluation |
| [ComputedValues.java](../src/com/lushprojects/circuitjs1/client/ComputedValues.java) | Shared computed-value registry |
| [LabeledNodeElm.java](../src/com/lushprojects/circuitjs1/client/LabeledNodeElm.java) | Named node lookup and registration |
| [CirSim.java](../src/com/lushprojects/circuitjs1/client/CirSim.java) | Global mode switch and matrix stamping APIs |