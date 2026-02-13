# EquationTableElm — Complete Reference

## Overview

`EquationTableElm` is a table-based circuit element where each row defines a named equation output. It provides a spreadsheet-like interface for defining mathematical relationships, with outputs accessible as labeled nodes (MNA mode) or via the `ComputedValues` registry (pure computational mode).

**Dump type:** 266  
**Source:** [EquationTableElm.java](../src/com/lushprojects/circuitjs1/client/EquationTableElm.java)  
**Renderer:** [EquationTableRenderer.java](../src/com/lushprojects/circuitjs1/client/EquationTableRenderer.java)  
**Max rows:** 32  

## Quick Facts

| Property | Value |
|----------|-------|
| Post count | 0 (no visible electrical posts) |
| High-impedance | Yes — `getConnection()` always returns `false` |
| Ground connection | None — `hasGroundConnection()` returns `false` |
| Nonlinear | Depends on row content (see [Row Classifications](#row-classifications)) |
| Modes | MNA (electrical) or Pure Computational (global setting) |
| Mode toggle | Options → Other Options → "MNA Mode" checkbox (`sim.equationTableMnaMode`) |

## Per-Row Data

Each row stores the following:

| Field | Type | Description |
|-------|------|-------------|
| `outputNames[row]` | String | Name of the output (becomes a labeled node or ComputedValue key) |
| `equations[row]` | String | Expression evaluated each timestep |
| `initialEquations[row]` | String | Expression evaluated only at t=0 (optional) |
| `sliderVarNames[row]` | String | Name of the slider variable (accessible in equations) |
| `sliderValues[row]` | double | Current slider value |
| `outputModes[row]` | RowOutputMode | VOLTAGE_MODE, FLOW_MODE, or SECTOR_MODE |
| `targetNodeNames[row]` | String | Target node for FLOW_MODE/SECTOR_MODE |
| `capacitances[row]` | double | Capacitance value for SECTOR_MODE (default 1.0) |

## Row Output Modes

Each row operates in one of three modes, determining how the equation result is stamped into the circuit.

### VOLTAGE_MODE (Default)

The equation result is driven as a **voltage** via a voltage source.

- **Stamp:** `stampVoltageSource(0, node, vs, value)`
- **Nodes needed:** 1 (output node)
- **Use cases:** Stock levels, prices, parameters, computed quantities
- **Row classifications apply:** alias, constant, linear, or dynamic

### FLOW_MODE

The equation result is stamped as a **current source** flowing between two named nodes.

- **Stamp:** `stampCurrentSource(sourceNode, targetNode, flowValue)` in `doStep()`
- **Nodes needed:** 2 (source = output name, target = `targetNodeNames[row]`)
- **Direction:** Positive value = current flows FROM source TO target
- **Target:** Required — specify a node name or `"gnd"` for ground
- **Use cases:** Transaction flows, wage payments, consumption spending
- **Always nonlinear:** Requires `doStep()` evaluation every subiteration

### SECTOR_MODE

The equation result represents **net inflow current** integrated via a companion capacitor model. The node voltage represents the accumulated stock.

- **Stamp:** Companion resistor `R = dt/C` in `stamp()`, history current source in `doStep()`
- **Nodes needed:** 1 (stock node), optionally connects to a target node (default: ground)
- **Equation meaning:** Net inflow rate (positive → stock increases)
- **Integration:** Built-in backward Euler: `Stock(t) = Stock(t-dt) + dt × NetInflow / C`
- **Use cases:** Accumulating stocks (bank deposits, wealth, inventory)
- **Always nonlinear:** Requires `doStep()` evaluation every subiteration

### Mode Comparison

| Feature | VOLTAGE_MODE | FLOW_MODE | SECTOR_MODE |
|---------|---------|---------|-----------|
| Output | Drives voltage | Drives current | Integrates inflow |
| Drive type | VCVS (linear) or computational (dynamic) | VCCS (computational) | Companion resistor + computational current |
| Stamp type | `stampVoltageSource` | `stampCurrentSource` | `stampResistor` + `stampCurrentSource` |
| Integration | Use `integrate()` in equation | External capacitor | Built-in (backward Euler) |
| Conservation (KCL) | No (voltage forced) | Yes (at target node) | Yes (at stock node) |
| Nodes required | 1 (output) | 2 (source + target) | 1 (stock node) |
| Equation meaning | Output value directly | Flow rate from→to | Net inflow to stock |

## Row Classifications

Each VOLTAGE_MODE row is classified at circuit setup time to minimize matrix size and avoid unnecessary nonlinear iteration. Classifications are checked in priority order.

### 1. Alias — No Matrix Entry

**Pattern:** Bare node reference, e.g. `Cs ~ Cd`

The output name is registered in `LabeledNodeElm.labelList` pointing to the **same node number** as the target. No voltage source, no internal node, no matrix row allocated.

**Savings:** 2 matrix rows eliminated per alias.

### 2. Constant — Linear Stamp

**Pattern:** Pure literal values and math, e.g. `rl ~ 0.025`

Evaluated once during `stamp()` and baked into the matrix as a linear DC voltage source. No `stampNonLinear()`, no `doStep()` work. Eligible for CirSim's matrix simplification pass.

### 3. Linear — VCVS Stamp

**Pattern:** Linear combination of node references with constant coefficients, e.g. `Y ~ Cs + Is`, `Y ~ 2*Cd - 3*Is + 5`

Stamped as a Voltage-Controlled Voltage Source (VCVS). The matrix solver computes the correct output in one pass — no iterative `doStep()` evaluation needed.

**Deferred stamping:** If referenced nodes don't exist yet during `stamp()` (due to element ordering), VCVS coefficients are deferred to `postStamp()`.

### 4. Dynamic — Nonlinear doStep()

**Pattern:** Everything else — node references in non-linear expressions, `integrate()`, `diff()`, `lag()`, `t`, etc.

Standard nonlinear stamping with `stampNonLinear(vn)` in `stamp()`, value computed and stamped via `stampRightSide(vn, value)` in `doStep()` each subiteration.

### Classification Rules

- FLOW_MODE and SECTOR_MODE rows are **never** classified as constant or linear — they always require `doStep()`.
- Rows with initial equations (`initialEquations[row]` non-empty) are always **dynamic**, since they require special t=0 handling.
- If all rows in a table are constant/alias/linear, `nonLinear()` returns `false`, preventing the table from forcing the entire circuit into nonlinear mode.

### Visual Indicators

| Icon | Color | Classification |
|------|-------|---------------|
| → | Gray | Alias |
| ● | Blue | Constant |
| L | Green | Linear |
| ⟳ | Orange | Dynamic |

## Simulation Lifecycle

### Mode Dispatch in stamp()

After row classifications are computed and nodes are allocated, `stamp()` dispatches each row to its mode-specific stamping method:

```java
RowOutputMode mode = outputModes[row];

switch (mode) {
case VOLTAGE_MODE:
    stampVoltageModeRow(row);
    break;
case FLOW_MODE:
    stampCurrentModeRow(row);
    break;
case SECTOR_MODE:
    stampCapacitorModeRow(row);
    break;
}
```

| Method | Mode | What It Stamps |
|--------|------|----------------|
| `stampVoltageModeRow()` | VOLTAGE_MODE | Voltage source (constant, linear VCVS, or nonlinear) + load resistor |
| `stampCurrentModeRow()` | FLOW_MODE | Marks source/target nodes as nonlinear; current stamped in `doStep()` |
| `stampCapacitorModeRow()` | SECTOR_MODE | Companion resistor `R = dt/C` + marks nodes for right-side updates |

### Execution Order Per Timestep

```
analyzeCircuit()
  └── stamp()
        ├── updateRowClassifications()
        ├── findLabeledNodes()          — allocates nodes, skips alias rows
        ├── registerAliasNodes()        — points alias names to target nodes
        └── per-row stamping (mode dispatch above):
              ├── Alias:     (nothing — handled by registerAliasNodes)
              ├── Constant:  stampVoltageSource(0, node, vs, value)
              ├── Linear:    stampVoltageSource + stampVCVS (or defer to postStamp)
              ├── Dynamic:   stampNonLinear(vn) + stampVoltageSource(0, node, vs)
              ├── FLOW_MODE:   stampNonLinear + stampRightSide on both nodes
              └── SECTOR_MODE: stampResistor(companion) + stampNonLinear + stampRightSide

postStamp()
  └── Stamps deferred VCVS coefficients for linear rows

startIteration()                    — once per timestep, before subiterations
  ├── Retry unresolved aliases
  ├── Seed alias values into ComputedValues
  └── Calculate capacitor history currents (I_hist = -V_last / R)

[subiteration loop]
  doStep()                          — called each subiteration
    ├── Skip constant rows
    ├── Skip linear rows (MNA mode) — read computed voltage for display
    ├── Alias rows: copy target value to ComputedValues
    ├── t=0 handling: evaluateInitialValue()
    ├── VOLTAGE_MODE: evaluateVoltageModeRow()
    ├── FLOW_MODE: evaluateCurrentModeRow()
    └── SECTOR_MODE: evaluateCapacitorModeRow()

stepFinished()                      — once per timestep, after convergence
  ├── Alias rows: read target voltage, register in ComputedValues
  ├── SECTOR_MODE rows: save capLastVoltages, capLastCurrents; set outputValues = stock voltage
  ├── Register all output values in ComputedValues
  └── Commit integration state (exprStates[row].commitIntegration)
```

### Initial Value Handling (t=0)

If `initialEquations[row]` is set:

1. **Subiteration 0:** Stamp a placeholder (0) to let the solver warm up.
2. **Subiteration 1:** Call `evaluateInitialValue(row)`:
   - Evaluate the initial expression at `t=0`
   - Set `outputValues[row]`, `lastOutputValues[row]`, `exprStates[row].lastIntOutput`
   - For SECTOR_MODE: set `capLastVoltages[row]`, stamp history current
   - For VOLTAGE_MODE: stamp via `stampRightSide(vn, initialValue)`
   - Register in ComputedValues immediately
3. **Subsequent subiterations at t=0:** Re-stamp the same initial value (right-side resets each subiteration).

After `initialValueApplied[row]` is set to `true`, the row proceeds with normal equation evaluation for all subsequent timesteps.

## Expression System

Equations are parsed by `ExprParser` into `Expr` trees and evaluated via `Expr.eval(ExprState)`.

### Available Functions

**Stateless:** `sin`, `cos`, `tan`, `exp`, `log`, `sqrt`, `abs`, `min`, `max`, `floor`, `ceil`

**Stateful (require ExprState):**
- `integrate(x)` — numerical integration over time
- `diff(x)` — numerical differentiation over time
- `lag(x, tau)` — exponential smoothing / first-order lag

### Variable Resolution

Equations can reference:

| Source | Example | Resolution |
|--------|---------|------------|
| Slider variable | `rate` (if row's slider is named "rate") | `exprStates[row].values[0]` |
| ComputedValues | `YD`, `Wages`, any registered name | `ComputedValues.getComputedValue(name)` |
| Labeled node voltages | `V_HH`, `Firms` | `LabeledNodeElm.getByName(name)` → node voltage |
| Time | `t` | `sim.t` |
| Other row outputs | `Y1`, `Y2` | Via ComputedValues (registered each `doStep`) |

### Slider Variables

Each row has a named slider variable (default: `a`, `b`, `c`, ...) with a value (default 0.5). The slider value is:
- Passed to the expression as `exprStates[row].values[0]`
- Registered in `ComputedValues` under the slider variable name
- Adjustable in the edit dialog

## Convergence

### Adaptive Tolerance

Convergence threshold scales with subiteration count and value magnitude:

| Subiterations | Relative Tolerance |
|--------------|-------------------|
| < 3 | 0.001 (0.1%) |
| 3–9 | 0.01 (1%) |
| 10–49 | 0.05 (5%) |
| ≥ 50 | 0.1 (10%) |

For `diff()` equations, tolerance is multiplied by 10× to account for timestep-amplified variations. Early subiterations (< 5) skip convergence checks entirely for `diff()` rows to let inputs settle.

### Formula

```
threshold = max(1.0, max(|currentValue|, |lastValue|)) × relativeTolerance
```

If `|newValue - lastValue| > threshold`, `sim.converged` is set to `false`.

## Modes: MNA vs Pure Computational

The mode is a **global** setting (`sim.equationTableMnaMode`, toggled in Options → Other Options).

### MNA Mode (Default)

- Rows create labeled nodes and voltage sources in the circuit matrix
- Values participate in MNA solving (KCL enforcement)
- Row classifications (alias, constant, linear) reduce matrix size
- FLOW_MODE/SECTOR_MODE inject current sources

### Pure Computational Mode

- No electrical posts, no voltage sources, no matrix entries
- All values written to `ComputedValues` registry
- Other elements read values via `ComputedValues.getComputedValue(name)`
- Use `ComputedValueSourceElm` or `LabeledNodeElm` to bridge to electrical domain
- Linear rows evaluate dynamically (no VCVS optimization)

| Aspect | MNA Mode | Pure Computational |
|--------|----------|-------------------|
| Posts | 0 (but internal nodes created) | 0 |
| Voltage sources | 1 per VOLTAGE row | 0 |
| Matrix entries | Yes | No |
| ComputedValues | Yes (also registered) | Yes (primary output) |
| FLOW_MODE | Stamps current source | N/A |
| SECTOR_MODE | Companion model | N/A |

## Serialization Format

### Dump Format

```
266 x1 y1 x2 y2 flags tableName rowCount [per-row-data...]
```

**Per-row data (8 tokens per row):**

```
outputName equation initialEquation sliderVarName sliderValue outputModeOrdinal targetNodeName capacitance
```

- `outputModeOrdinal`: 0 = VOLTAGE_MODE, 1 = FLOW_MODE, 2 = SECTOR_MODE
- Strings are escaped via `CustomLogicModel.escape()` (spaces → `\s`, backslash → `\\`)
- Empty initial equations serialize as empty string

**Legacy format (5 tokens per row):** Detected automatically when `tokenCount == rowCount * 5`. Missing mode fields default to VOLTAGE_MODE.

### Flags

| Flag | Value | Meaning |
|------|-------|---------|
| `FLAG_SMALL` | 1 | Small display mode (smaller fonts, tighter rows) |
| `FLAG_MNA_MODE` | 2 | MNA electrical mode (set on construction, but actual mode read from `sim.equationTableMnaMode`) |

## Mouse Interaction

### Mouse Wheel

When hovering over a row whose equation is a simple numeric value (parseable as a `double`), scrolling the mouse wheel adjusts the value:

- **Scroll up:** Increase value
- **Scroll down:** Decrease value
- **Step size:** Proportional to value magnitude (1/10th of the order of magnitude)
- **Precision:** Values formatted with up to 4 significant figures
- **Undo:** First wheel movement pushes undo state

### Hover Tooltip

Hovering over a row displays detailed info in the sidebar:
- Hint text (from `HintRegistry`)
- Row classification with icon (→ alias, ● constant, L linear, ⟳ dynamic)
- Equation text
- Initial equation (if any)
- Slider name and value
- Current output value
- Parse error (if any)

## UI / Edit Dialog

The edit dialog is opened by double-clicking the table element. It provides:

- **Table name** field
- **Row count** adjustment
- **Per-row fields:** output name, equation, initial equation, slider variable name, slider value
- **Per-row mode:** output mode dropdown (VOLTAGE / CURRENT / CAPACITOR)
- **Per-row target:** target node name (for CURRENT/CAPACITOR modes)
- **Per-row capacitance:** capacitance value (for CAPACITOR mode)
- **Row reordering:** up/down buttons
- **Autocomplete:** for equation editing (references labeled nodes, computed values)

The single property in the standard edit dialog (`getEditInfo`/`setEditValue`) is the "Small" checkbox for display size.

## Key Implementation Details

### Node Allocation Strategy

In MNA mode, `findLabeledNodes()` determines how each row connects to the circuit:

1. **Alias rows:** Skipped entirely — no nodes or voltage sources.
2. **CURRENT mode:** Uses existing `LabeledNodeElm` nodes only. Does not allocate internal nodes.
3. **CAPACITOR mode:** Uses existing `LabeledNodeElm` if available, otherwise allocates an internal node and registers it in `labelList`.
4. **VOLTAGE mode:** Uses existing `LabeledNodeElm` if available, otherwise allocates an internal node. Always allocates a voltage source index.

Internal nodes are registered in `LabeledNodeElm.labelList` so other elements can find them by name.

### Companion Model (CAPACITOR Mode)

The backward Euler discretization converts the capacitor into:

```
Capacitor: I = C × dV/dt
Discretized: I = (C/dt) × (V_new - V_old)
Companion: Resistor G = C/dt + Current source I_hist = -V_old × G
```

- **stamp():** Stamps the companion resistor `R = dt/C`
- **startIteration():** Computes `capCurSourceValue[row] = -capLastVoltages[row] / R`
- **doStep():** Stamps `totalCurrent = capCurSourceValue[row] - inflowValue`
- **stepFinished():** Saves `capLastVoltages[row]` and `capLastCurrents[row]` for next timestep

### ComputedValues Timing

Values are registered in `ComputedValues` at multiple points:

1. **stamp():** Constant rows register their fixed values
2. **startIteration():** Alias rows seed target values (direct buffer)
3. **doStep():** Dynamic/VOLTAGE/CURRENT rows register via `setComputedValue` (pending buffer)
4. **stepFinished():** All rows register final values; CAPACITOR rows register stock voltage (not flow)

The double-buffering in ComputedValues ensures that reads during `doStep()` see values from the previous commit, avoiding order-dependent results.

### Debug Logging

Set `DEBUG = true` in the source to enable detailed console output via `CirSim.console()`:
- Equation parsing results
- Row classifications
- Per-row evaluation details (slider values, referenced nodes, computed results)
- Convergence checks (threshold, diff, pass/fail)
- Node allocation and stamping

Also visible at runtime: set `sim.convergenceCheckThreshold` in Options → Other to log convergence failures after N subiterations.

## Related Documentation

| Document | Description |
|----------|-------------|
| [EQUATION_TABLE_SIMPLIFICATION.md](EQUATION_TABLE_SIMPLIFICATION.md) | Row classification system and matrix optimization details |
| [EQUATION_TABLE_CURRENT_FLOW_MODE.md](EQUATION_TABLE_CURRENT_FLOW_MODE.md) | Design and implementation of CURRENT/CAPACITOR modes |
| [PURE_COMPUTATIONAL_TABLES.md](PURE_COMPUTATIONAL_TABLES.md) | Pure computational architecture and ComputedValues bridging |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Overall system architecture and ComputedValues double-buffering |
| [AUTOCOMPLETE_FEATURE.md](AUTOCOMPLETE_FEATURE.md) | Autocomplete in equation editing |
| [GREEK_SYMBOLS_FEATURE.md](GREEK_SYMBOLS_FEATURE.md) | Greek symbol support in variable names |

## Related Source Files

| File | Role |
|------|------|
| [EquationTableElm.java](../src/com/lushprojects/circuitjs1/client/EquationTableElm.java) | Main element class |
| [EquationTableRenderer.java](../src/com/lushprojects/circuitjs1/client/EquationTableRenderer.java) | Drawing/rendering |
| [Expr.java](../src/com/lushprojects/circuitjs1/client/Expr.java) | Expression tree (`isConstant()`, `isNodeAlias()`, `getLinearTerms()`) |
| [ExprParser.java](../src/com/lushprojects/circuitjs1/client/ExprParser.java) | Expression parser |
| [ExprState.java](../src/com/lushprojects/circuitjs1/client/ExprState.java) | Per-row evaluation state (`integrate`, `diff`, `lastOutput`) |
| [ComputedValues.java](../src/com/lushprojects/circuitjs1/client/ComputedValues.java) | Registry for cross-element value sharing |
| [LabeledNodeElm.java](../src/com/lushprojects/circuitjs1/client/LabeledNodeElm.java) | Named node lookup (`labelList`, `getByName()`) |
| [CirSim.java](../src/com/lushprojects/circuitjs1/client/CirSim.java) | `equationTableMnaMode`, `stampVoltageSource()`, `stampCurrentSource()` |
