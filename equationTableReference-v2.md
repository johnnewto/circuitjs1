# Equation Table Reference v2

## Overview

`EquationTableElm` is a table-driven equation element that can run in two simulator-wide modes:

- **MNA/Electrical mode** (`sim.equationTableMnaMode = true`): each row can stamp into the circuit as voltage, flow, or stock dynamics.
- **Pure Computational mode**: rows evaluate equations and publish values only through `ComputedValues`.

The element is designed as a **high-impedance computational source** (`getConnection()` returns `false`) and supports up to **32 rows**.

---

## Row Model

Each row stores:

- `outputName`
- `equation`
- `initialEquation` (applies at `t=0`)
- `sliderVarName`, `sliderValue`
- `outputMode` (`VOLTAGE_MODE`, `FLOW_MODE`, `STOCK_MODE`)
- `targetNodeName` (for 2-node flow/stock)
- `capacitance`, `useBackwardEuler` (for stock integration)

Runtime state also includes compiled expressions, `ExprState`, last/current values, alias classification, and stock companion-model history terms.

---

## Output Modes

## 1) `VOLTAGE_MODE`

- Equation value is interpreted as a **voltage**.
- In MNA mode, row stamps a voltage source to its resolved node.
- A tiny load resistor (`1e9 Ω`) is stamped to reduce row elimination side effects.
- Value is registered in `ComputedValues` by output name.

## 2) `FLOW_MODE`

- Equation value is interpreted as **current**.
- Single-node form: current from `gnd -> outputName` (positive injects into node).
- Two-node form: current from `source(outputName) -> targetNodeName`.
- Stamps with `sim.stampCurrentSource(source, target, flowValue)`.
- Flow rows intentionally do **not** overwrite `ComputedValues[outputName]` with flow magnitude (to avoid clobbering stock/node values).

## 3) `STOCK_MODE`

- Equation value is interpreted as **net inflow current** for an integrating stock.
- Uses a capacitor companion model between source and target.
- Companion resistance:
  - Trapezoidal: $R = \frac{\Delta t}{2C}$
  - Backward Euler: $R = \frac{\Delta t}{C}$
- `doStep()` stamps current source with:
  - `totalCurrent = capCurSourceValue - inflowValue`
- Display value for stock rows is **stock level (node voltage)**, not inflow.

---

## Alias Rows

A row is classified as **alias** when:

- `initialEquation` is empty, and
- main expression is a bare node alias (`compiledExpr.isNodeAlias()`).

Alias rows:

- do not allocate voltage source rows,
- do not allocate internal nodes,
- map output label directly to target node label,
- are resolved in `registerLabels()` (pre-wire-closure) and retried in `startIteration()` if needed.

This avoids extra matrix rows and keeps aliased labels physically merged.

---

## Naming and Node Syntax

`setOutputName()` accepts combined naming and splits source/target automatically.

Supported separators (input):

- `->`
- `-||-`
- `→`
- `⊣⊢`
- `,`

Behavior:

- no separator: source only (implicit ground target for flow/stock semantics where needed)
- separator present: `source` + explicit `target`

Display helpers:

- `getDisplayOutputName()` returns ASCII `source->target` for dump compatibility
- `getUIDisplayOutputName()` uses Unicode symbols for UI (`→`, `⊣⊢`)

---

## Simulation Lifecycle

## `stamp()`

1. Update row classifications.
2. If pure mode: no matrix stamping.
3. Resolve/create labeled nodes (`findLabeledNodes()`).
4. Register aliases (`registerAliasNodes()`).
5. Stamp each non-alias row by mode handler.

## `startIteration()`

- Retries unresolved aliases.
- Seeds alias values and stock node voltages into `ComputedValues` (direct buffer write).
- Computes stock history source term (`capCurSourceValue`) from last state.

## `doStep()`

Per row:

- alias rows copy target value.
- if `t==0` and `initialEquation` exists, evaluate initial equation path.
- otherwise evaluate/stamp via mode handler.
- convergence check uses adaptive, magnitude-scaled limit.

## `stepFinished()`

- Commits final outputs to `ComputedValues` (except flow magnitude clobber case).
- For stock rows, saves `capLastVoltage`, `capLastCurrent` from companion model state.
- Commits `ExprState` integration and updates last values.

## `reset()`

- Resets per-row runtime state and republishes row outputs.

---

## Convergence Strategy

`getConvergeLimit(row)` uses adaptive relative tolerance by subiteration count:

- early iterations stricter, later iterations looser.
- rows with `diff()` get a 10x relaxed relative tolerance.
- threshold scales with magnitude of current/last outputs.

A row marks non-convergence by setting `sim.converged = false` and stores row-level diagnostics.

---

## Initial Value Semantics (`initialEquation`)

If defined, initial equation is evaluated at `t=0`:

- sets row output baseline,
- initializes expression integration state (`lastIntOutput`),
- for voltage rows: stamps initial voltage,
- for stock rows: sets initial stock voltage and stamps companion/history current,
- forces another subiteration where needed to apply initial condition correctly.

---

## UI and Editing

Editing is provided by `EquationTableEditDialog`.

Columns:

1. row controls (add/delete/move)
2. Node(s)
3. Equation
4. Initial (t=0)
5. Mode
6. Cap
7. Integ.
8. Slider Var
9. Slider Value
10. Hint

Capabilities:

- row reorder/add/remove (1..32),
- autocomplete for equations,
- flow/stock node pair entry in Node(s) field,
- per-row integration method (Trap/Euler for stock),
- per-row hint text persisted through `HintRegistry`.

Apply path:

- pushes all edited row fields back into `EquationTableElm`,
- reparses equations,
- reallocates nodes (`allocNodes()`),
- recalculates geometry (`setPoints()`),
- requests reanalysis and repaint.

---

## Mouse Wheel Numeric Tuning

When hovering a row whose equation is a plain numeric literal:

- wheel adjusts the literal directly,
- step size scales with magnitude,
- undo state is pushed before change,
- equation is reformatted to compact significant digits.

Rows with non-numeric expressions are not wheel-adjustable.

---

## Serialization Format

`dump()` writes:

1. `super.dump()`
2. escaped table name
3. row count
4. per row:
   - escaped combined output name (`source` or `source->target`)
   - escaped equation
   - escaped initial equation
   - escaped slider var
   - slider value
   - output mode ordinal
   - empty target token (kept for compatibility)
   - capacitance
   - backward Euler flag (`0/1`)

Loader supports legacy token layouts (old/new/newest) and normalizes combined names.

---

## Public API Highlights

Common getters/setters:

- table: `getTableName()`, `setTableName()`
- rows: `getRowCount()`, `setRowCount()`
- row fields: `get/setOutputName`, `get/setEquation`, `get/setInitialEquation`, `get/setOutputMode`, `get/setCapacitance`, `get/setUseBackwardEuler`, `get/setSliderVarName`, `get/setSliderValue`
- display helpers: `getDisplayOutputName()`, `getUIDisplayOutputName()`, `getFlowDisplayName()`
- values: `getOutputValue()`, `getDisplayValue()`
- classification: `isAliasRow()`, `getRowClassification()`
- parsing trigger: `parseAllEquationsPublic()`
- UI launch: `openEditDialog()`

---

## Debugging

`EquationTableMarkdownDebugDialog` provides a generated markdown debug view including:

- table overview,
- matrix summary (`X = A⁻¹B`),
- row summaries and per-row details,
- computed-values checks,
- labeled node mapping,
- circuit dump,
- rendered markdown viewer popup.

---

## Practical Notes

- MNA mode is controlled globally (`sim.equationTableMnaMode`), not per-table.
- Stock rows should be interpreted as **stateful voltage stocks** with current-defined evolution.
- Flow rows are directional current injections; naming should reflect source/target intent.
- Alias rows are the cheapest way to make one label mirror another node electrically.
