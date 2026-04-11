# SFCR Format Reference

Human-readable text format for Stock-Flow Consistent (SFC) models in CircuitJS1.

**Implementation**: [SFCRParser.java](../src/com/lushprojects/circuitjs1/client/SFCRParser.java), [SFCRExporter.java](../src/com/lushprojects/circuitjs1/client/SFCRExporter.java), [SFCRUtil.java](../src/com/lushprojects/circuitjs1/client/SFCRUtil.java) (shared utilities)

## Usage

| Action | How |
|--------|-----|
| Import | **File → Import from Text** or **File → Open** |
| Export | **File → Export as SFCR** |
| URL | `?ctz=` compressed parameter |

Auto-detected when content contains `@matrix`, `@equations`, `@parameters`, `@init`, `@action`, `@hints`, `@scope`, `@circuit`, or `@info` blocks.

---

## Block Reference

### @init — Simulation Settings

```
@init
  timestep: 0.1
  voltageUnit: $
  timeUnit: yr
  autoAdjustTimestep: false
  equationTableMnaMode: true
  equationTableNewtonJacobianEnabled: false
  equationTableTolerance: 0.001
  convergenceCheckThreshold: 100
  infoViewerUpdateIntervalMs: 100
  showDots: false
  showVolts: false
@end
```

| Setting | Description |
|---------|-------------|
| `timestep` | Simulation timestep (seconds) |
| `voltageUnit` | Display unit symbol (default: `V`, use `$` for economics) |
| `timeUnit` | Time unit symbol (e.g., `yr`, `qtr`) |
| `autoAdjustTimestep` | Enable adaptive timestep (`true`/`false`); defaults to `false` when omitted |
| `voltageRange` | Voltage display range |
| `showToolbar` | Show/hide toolbar (`true`/`false`) |
| `showDots` | Show current flow dots |
| `showVolts` | Show voltage colors |
| `showValues` | Show component values |
| `showPower` | Show power dissipation |
| `equationTableMnaMode` | Global EquationTable electrical mode toggle (`true` = MNA/electrical, `false` = pure computational) |
| `equationTableNewtonJacobianEnabled` | Global EquationTable Newton Jacobian toggle |
| `equationTableTolerance` | EquationTable convergence tolerance (`> 0`) |
| `equationTableConvergenceTolerance` | Alias of `equationTableTolerance` |
| `convergenceCheckThreshold` | Global subiteration threshold before reporting non-convergence (`>= 0`) |
| `subiterationConvergenceThreshold` | Alias of `convergenceCheckThreshold` |
| `infoViewerUpdateIntervalMs` | Throttle interval (ms) for live Info Viewer updates (default: `200`) |

Notes:
- `equationTableTolerance` and `equationTableConvergenceTolerance` are interchangeable on import.
- `convergenceCheckThreshold` and `subiterationConvergenceThreshold` are interchangeable on import.
- `equationTableMnaMode` and `equationTableNewtonJacobianEnabled` are exported by SFCR exporter and applied at import time.

---

### @action — Scheduled Actions

Defines the Action Scheduler schedule: timed parameter updates.

```
@action
  pauseTime: 0

| time | target | value | text | enabled | stop |
|------|--------|-------|------|---------|------|
| 30   | alpha0 | =10   | propensity to spend | true | false |
| 40   | alpha0 | +10   | After               | true | false |
@end
```

| Field | Description |
|-------|-------------|
| `pauseTime` | Seconds to wait after trigger before applying value (default `0`) |
| `time` | Simulation time (seconds) when action fires |
| `target` | Name of the PARAM_MODE variable to update |
| `value` | Absolute number (`50`) or expression (`+10`, `-5`, `*0.5`, `=50`) |
| `text` | Display text shown on scope / ActionTimeElm at trigger |
| `enabled` | `true` / `false` |
| `stop` | `true` to stop simulation instead of updating a value |

Relative expressions (`+`, `-`, `*`) resolve the target's current value **at the moment the action fires**.

Importing an `@action` block **replaces** the existing schedule entirely.

---

### @info — Model Documentation

Markdown content displayed via **View → Model Info**.

```
@info
# Model Title

Description of the model...

## Key Variables
- Y: National income
- C: Consumption
@end
```

#### InfoViewer Directive Blocks inside `@info`

Inside `@info`, fenced blocks tagged as ` ```{circuit} ` are treated as **viewer directives** (not plain code samples).
They can mount live widgets in the Model Info viewer.

Supported directive patterns include:

- `table: <name>` for live table mounts
- `sankey: <table>` or `@sankey ... @end` for Sankey mounts
- `@scope ... @end` for live scope mounts
- Plot directives via either fenced keys (`plot:` / `vars:`) or block form `@plot ... @end`

Example:

```{circuit}
table: Transaction_Flow_Matrix
```

`@plot` can be used directly in `@info` markdown (fences optional):

```
@plot
vars: Y, C_d
title: Output and Consumption
ylabel: Value
points: 300
xaxis: Time (years)
legend: true
style: lines+points
@end
```

Plot aliases accepted by the viewer:

- `plot:` or `vars:` (same meaning)
- `xaxis:` or `xlabel:` (same meaning)
- `yaxis:` or `ylabel:` (same meaning)
- `window:` or `points:` (same meaning)

---

### @equations — Equations & Constants

All equations and constants. Creates an `EquationTableElm`.

```
@equations Model x=200 y=100
  # Constants
  α_1 ~ 0.6                       # Propensity to consume income
  α_2 ~ 0.4                       # Propensity to consume wealth
  θ ~ 0.2                         # Tax rate
  G_d ~ 20                        # Government demand
  W ~ 1                           # Wage rate
  
  # Equations
  YD ~ W * N_s - T_s              # Disposable income
  C_d ~ α_1 * YD + α_2 * H_h      # Consumption function
  H_h ~ integrate(YD - C_d)       # Stock (integration)
  ∆H_h ~ diff(H_h)                # Flow (differentiation)
@end
```

**Position:** Optional `x=N y=N` places the table at specific canvas coordinates.

- Use `~` (sfcr-style) or `=` for assignment
- Inline `# comment` becomes a hint (unless overridden by `@hints`)
- Lag syntax is preserved as written on import (e.g. `X[-1]`, `X(-1)`, `X [ - 1 ]`)

Optional per-row metadata can be appended using `; key=value` tokens:

```
  WBs ~ W * Ns ; mode=voltage ; target=WBd ; slider=wageRate ; sliderValue=0.88 ; initial=0
```

| Metadata key | Meaning |
|--------------|---------|
| `mode` | Row output mode: `voltage`, `flow`, `param` |
| `target` | FLOW-mode target node name (if not using `A->B` in the output name) |
| `slider` | Slider variable name for this row |
| `sliderValue` | Slider initial/current numeric value |
| `initial` | Initial equation string |

Notes:
- Full-line `# ...` comments inside `@equations` are preserved as non-simulating comment rows.
- Output names can use combined flow syntax (for example `A->B`) and are normalized on import.

> **Note:** `@parameters` is accepted as an alias for `@equations` (for sfcr compatibility).

---

### @lookup — Named Interpolation Tables

Defines reusable lookup tables for ratio → multiplier mappings (for example World2-style control curves).

```
@lookup BRMM
  | x | y |
  | 0 | 1.2 |
  | 1 | 1.0 |
  | 5 | 0.78 |
@end

@lookup BRFM scope=World2
  0, 0
  1, 1
  2, 1.9
@end
```

Row formats accepted:

- Markdown table rows (`| x | y |`)
- CSV-ish rows (`x, y`)
- Whitespace-separated rows (`x y`)

Rules:

- At least 2 numeric points are required
- `x` values must be strictly increasing
- `scope=<equationsName>` creates a local table for one `@equations` block; otherwise table is global
- Local scoped tables override global tables with the same name
- Export may reconcile lookup aliases that differ only by `_lookup` suffix (for example `CFIFR` vs `CFIFR_lookup`) when table points are identical
- When a lookup is referenced from equations as `lookup(Name, x)`, export prefers the equation-referenced name (`Name`) and avoids emitting a duplicate alias block
- Dedupe is signature-based (scope + point pairs), so identical tables are emitted once even if discovered via both template seeding and expression scanning

Equation-call syntax supported:

- `lookup(TableName, x)`
- `TableName(x)`

Implementation note:

- On import, lookup calls are rewritten to `pwlx(...)` expressions.
- `pwlx` uses linear interpolation in-range and linear extrapolation outside endpoints.

---

### @matrix — Transaction Flow Matrix

SFC transaction matrix. Creates an `SFCTableElm`.

```
@matrix Transaction_Flow x=400 y=100
columns: Households, Firms, Govt

| Transaction     | Households  | Firms       | Govt        |
|-----------------|-------------|-------------|-------------|
| Consumption     | -C_d        | C_s         |             |
| Govt Spending   |             | G_s         | -G_d        |
| Wages           | W * N_s     | -W * N_s    |             |
| Taxes           | -T_s        |             | T_d         |
@end
```

**Position:** Optional `x=N y=N` places the table at specific canvas coordinates.

- `columns:` defines column headers (optional if table header row present)
- `codes:` short codes for R-style compatibility (optional)
- `type: transaction_flow | balance_sheet` (optional)
- `showFlowValues:` / `show_flow_values:` (optional boolean)
- `showInitialValues:` / `show_initial_values:` (optional boolean)
- `integration:` accepts `backward_euler`/`backward euler`/`backwardeuler` (optional)
- `useBackwardEuler:` / `use_backward_euler:` (optional boolean)
- Empty cells: leave blank or use `0`
- Σ column auto-added on import

---

### @hints — Variable Documentation

Tooltips for variables. Overrides inline comments.

```
@hints
  Y: National output
  C_d: Consumption demand
  H_h: Household money holdings
  α_1: Propensity to spend income
@end
```

---

### @scope — Scope Viewer (UID Traces)

Defines a docked scope with one source trace and optional additional traces.

```
@scope Main_Scope position=0
  speed: 64
  flags: x800060
  source: uid:Ab3_Xz value:0
  trace: uid:Q9m-L2 value:0
@end
```

| Field | Description |
|-------|-------------|
| `position` | Dock position/index used by the scope layout |
| `speed` | Scope sample speed |
| `flags` | Scope flags (decimal or `x...` hex) |
| `source` | Primary trace (`uid` + `value`) |
| `trace` | Additional trace (`uid` + `value`) |
| `title` | Optional explicit scope title |
| `label` | Optional scope label text |
| `x1`,`y1`,`x2`,`y2` | Optional explicit scope rectangle |
| `elmUid` | Optional UID for scope anchor element |

Legacy single-line form is still accepted for import compatibility:

```
@scope Y
```

Note: legacy one-line scope import is accepted, but variable-name probe binding is not auto-resolved.

---

### @sankey — Sankey Diagram

Defines an `SFCSankeyElm` view for table flows.

```
@sankey x=900 y=220
  source: Transaction_Flow
  layout: linear
  width: 300
  height: 250
  showScaleBar: true
  fixedMaxScale: 0
  useHighWaterMark: false
  showFlowValues: false
@end
```

| Setting | Description |
|---------|-------------|
| `source` | Source table name (optional; blank enables auto-source) |
| `layout` | `linear` or `circular` |
| `width`, `height` | Diagram size |
| `showScaleBar` | Show/hide scale bar |
| `fixedMaxScale` | Fixed max scale (`0` = auto) |
| `useHighWaterMark` | Keep scale at observed max |
| `showFlowValues` | Show numeric flow values |
| `showFlowLabels` | Backward-compatible alias of `showFlowValues` |

---

### @circuit — Raw Circuit Elements

Pass-through for native CircuitJS1 element dump format.

```
@circuit
  431 480 64 592 160 0 50 true false
  w 96 48 96 112 0
@end
```

### Native `%` Directives (Circuit Dump Format)

When importing native CircuitJS1 text dumps (non-SFCR), these `%` lines are recognized:

| Directive | Meaning |
|-----------|---------|
| `% voltageUnit <symbol>` | Set display voltage unit symbol |
| `% showToolbar true\|false` | Show or hide toolbar |
| `% equationTableMnaMode true\|false` | Set global EquationTable MNA mode |
| `% equationTableNewtonJacobianEnabled true\|false` | Set global EquationTable Newton Jacobian toggle |
| `% equationTableConvergenceTolerance <number>` | Set global EquationTable convergence tolerance |
| `% convergenceCheckThreshold <integer>` | Set global convergence-check subiteration threshold |
| `% viewport ...` / `% transform ...` | Set viewport/canvas transform |
| `% AS ...` / `% AST ...` | Load Action Scheduler entries |
| `% Hint ...` | Load variable hint glossary entry |

---

## Element Mapping

| SFCR Block | CircuitJS1 Element |
|------------|-------------------|
| `@matrix` | `SFCTableElm` (265) |
| `@equations` | `EquationTableElm` (266) |
| `@parameters` | `EquationTableElm` (266) — import alias |
| `@action` | `ActionScheduler` (replaces existing schedule) |
| `@hints` | `HintRegistry` |
| `@scope` | `Scope` entries (resolved by element UID) |
| `@sankey` | `SFCSankeyElm` |
| `@circuit` | Native elements |
| `@info` | Model info markdown content |

---

## Expression Syntax

| Feature | Syntax |
|---------|--------|
| Assignment | `~` or `=` |
| Integration | `integrate(expr)` |
| Differentiation | `diff(expr)` |
| Lagged value | `X[-1]`, `X(-1)`, `X [ - 1 ]` (preserved as written) |
| Greek letters | Unicode (`α`, `θ`, `∆`) or ASCII (`alpha`, `theta`) |

---

## R sfcr Compatibility

The parser also supports R-style `sfcr_matrix()` and `sfcr_set()` syntax:

```r
tfm <- sfcr_matrix(
  columns = c("Households", "Firms"),
  codes = c("h", "f"),
  c("Consumption", h = "-C_d", f = "C_s")
)

eqs <- sfcr_set(
  YD ~ W * N_s - T_s,
  C_d ~ alpha1 * YD
)
```

### Exporting R sfcr Syntax

Use **File → Export as SFCR** and select:
- **Block format (@equations/@matrix)**, or
- **R sfcr syntax (sfcr_set/sfcr_matrix)**

When **R sfcr syntax** is selected, export is **hybrid**:
- `@init`, `@action`, `@hints`, `@scope`, `@sankey`, `@circuit` remain block-based.
- Model documentation is exported as inline markdown (without an `@info` wrapper) for round-trip fidelity.
- Equation and matrix content is emitted as R assignments:
  - `name <- sfcr_set(...)` for `EquationTableElm` and `GodlyTableElm`
  - `name <- sfcr_matrix(...)` for `SFCTableElm`

R-style export carries extra metadata to preserve round-trip fidelity:
- **Block metadata comment** before R assignments:
  - `# [ x=400 y=120 type: transaction_flow ]`
  - Parsed on import and used for element position and matrix type.
- **Inline row metadata** in equation comments:
  - `# ... [mode=param, slider=alpha0, sliderValue=0, initial=...]`
  - Parsed on import to restore row mode/slider/initial settings.

Current parser behavior for R-style imports:
- Accepts `sfcr_set()` and `sfcr_matrix()` blocks (with assignment names).
- Accepts metadata comments in `# [ ... ]` form.
- Accepts named equation prefixes like `e1 = X ~ expr` and strips the `e1 =` part.
- Keeps standalone `#` lines as non-simulating comment rows in equation tables.

Notes:
- R-style scope/probe definitions are not a separate syntax; scopes remain `@scope` blocks.
- `codes=` in `sfcr_matrix()` is used to map row entries back to matrix columns on import.

#### Minimal R-Style Export Example

```text
# CircuitJS1 SFCR Export

@init
  timestep: 1
  voltageUnit: $
  autoAdjustTimestep: false
@end

# [ x=120 y=220 ]
bmw_eqs <- sfcr_set(
  e1 = Y ~ C + I,  # [mode=voltage, sliderValue=0 ]
  e2 = C ~ alpha1 * YD + alpha2 * Mh  # Consumption function [mode=param, slider=alpha1, sliderValue=0.75 ]
)

# [ x=420 y=220 type: transaction_flow ]
tfm <- sfcr_matrix(
  columns = c("Households", "Firms"),
  codes = c("h", "f"),
  c("Consumption", h = "-C", f = "C"),
  c("Investment",  h = "",   f = "I")
)

@scope Main_Scope position=0
  speed: 64
  flags: x800060
@end
```

This example shows the hybrid structure produced by R-style export: R assignments for equations/matrices plus standard `@...` blocks for simulator/UI configuration.

---

## Example Files

| File | Description |
|------|-------------|
| [sfcr-sim-model.txt](../tests/sfcr-sim-model.txt) | SIM model (G&L Ch.3) |
| [sfcr-pc-model.txt](../tests/sfcr-pc-model.txt) | Portfolio Choice model |
| [sfcr-lp-model.txt](../tests/sfcr-lp-model.txt) | Liquidity Preference model |
| [sfcr-r-style.txt](../tests/sfcr-r-style.txt) | R sfcr-style syntax |

---

## References

- [sfcr R package](https://github.com/joaomacalos/sfcr)
- Godley & Lavoie (2007), *Monetary Economics*
- [CircuitJS1 SFC Documentation](../docs-template/docs/money/)
