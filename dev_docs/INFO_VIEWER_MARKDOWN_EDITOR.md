# InfoViewer Live Markdown Editor — Design Document

## Overview

Extend the existing `InfoViewerDialog` into a live **markdown editor + viewer** where:

- The `@info` block content is editable in-browser
- Inline values (`{{VarName}}`) update in real-time as the simulation runs
- Fenced code blocks with ` ```{circuit} ``` ` syntax embed live tables and Plotly charts
- The overall syntax echoes Quarto's document-with-computation model

---

## What Exists Today

| Component | Role |
|-----------|------|
| `InfoViewerDialog.java` | Renders static `@info` markdown via marked.js + KaTeX in an iframe or popup window |
| `generateCircuitTablesMarkdown()` | Serialises all circuit tables as a markdown snapshot on demand |
| `ComputedValues` | Global registry of every live variable (stock, flow, equation output); exposes `getComputedValueNames()` / `getComputedValue()` |
| `ScopeViewerDialog` | Snapshots scope data into a Plotly.js window — one-shot, not live |
| `@info` block in SFCR | Arbitrary markdown stored in the circuit file, shown via **View → Model Info** |

The foundation is strong. The two gaps are:

1. No in-browser editor for the `@info` markdown
2. The rendered view is static — it re-renders only when manually re-opened

---

## Architecture

### 1 — Markdown editor pane

Add a split-pane layout to the existing popup HTML (`generateMarkdownViewerHTML`):

- **Left pane**: raw `<textarea>` pre-filled with the markdown source
- **Right pane**: live preview (current rendering pipeline unchanged)
- **Save** button: calls back via GWT JSNI to update `CirSim.infoMarkdown` in memory (persisted on next **Export as SFCR**)
- **Toggle** button: collapse left pane for read-only view (default for end-users)

This is entirely a change to the generated HTML/JS; no new GWT classes needed for Phase 1.

### 2 — Live data channel (postMessage)

The popup window is already opened with a named handle (`circuitjs_model_info`) and filled via `document.write()`, making it same-origin with the parent. The live channel:

```
CirSim.updateCircuit()  (each draw frame, throttled ~50 ms)
  └─► JSNI: pushCircuitDataToInfoWindow()
        └─► $wnd.circuitjs_model_info.postMessage(jsonSnapshot, '*')
```

The info window JS listens with `window.addEventListener('message', ...)` and patches only the live-data mount points — the full marked.js render is **not** repeated (preserving scroll position, KaTeX, and editor state).

#### JSON snapshot shape

```json
{
  "t": 12.4,
  "vars": {
    "Y": 120.5,
    "C": 85.2,
    "H_h": 34.3,
    "G_d": 20.0
  },
  "tables": [
    {
      "name": "Firms",
      "cols": ["Loans", "Deposits"],
      "rowNames": ["Wages", "Interest"],
      "values": [[10.2, -10.2], [1.5, -1.5]]
    }
  ],
  "scopes": [
    {
      "name": "GDP",
      "time": [0, 0.1, 0.2],
      "plots": [{ "name": "Y", "values": [100, 110, 120] }]
    }
  ]
}
```

### 3 — Quarto-style fenced block syntax

Standard marked.js renders fenced blocks first, then a post-render pass scans `pre > code` blocks and replaces `{circuit}` directives with live-data mount `<div>` elements.

#### Inline value substitution

```markdown
Current GDP: **{{Y}}** — Consumption: **{{C}}**
```

Rendered HTML gets `<span data-live-var="Y">…</span>` spans; the postMessage handler updates their `textContent` each tick.

#### Live table block

````markdown
```{circuit}
table: Firms
```
````

Mounts a `<div data-circuit-table="Firms">` which is re-rendered as an HTML table whenever new data arrives.

##### Table directive details (implemented)

- Accepted table directives:
  - `table: <name>`
  - `table: *`
  - `table: ALL_TABLES`
  - `tables: all`
- Name matching is tolerant and uses 3 tiers:
  1. Exact normalized match
  2. Canonical match (ignores punctuation/spacing/underscore-hyphen differences)
  3. Contains fallback for mild naming drift
- If no table is matched yet, the mount shows a waiting status until live payloads arrive.

##### Short “all tables” example

````markdown
```{circuit}
table: ALL_TABLES
```
````

Equivalent aliases:
- `table: *`
- `tables: all`

##### Live table rendering behavior (implemented)

- Regular tables render as `Flow/Stock` + dynamic stock columns.
- Cell text is rendered as `label = value` when label exists.
- Blank cells stay blank (no forced `0`).
- Negative numeric values are rendered in red text.
- Greek letters plus subscript/superscript forms are supported in labels and names, including braced forms like `_{...}` and `^{...}`.

##### Equation table rendering behavior (implemented)

- Equation tables render in legacy-style columns:
  - `Output`
  - `Equation`
  - `Hint`
- Equation column shows live values in the form `equation = value`.
- Negative live values in the equation column are rendered in red text.

#### Live Plotly chart block

````markdown
```{circuit}
plot: Y, C, H_h
title: Sector Balances
yaxis: $
window: 50
```
````

Mounts a Plotly.js div. Each postMessage tick appends the new point and trims the window. Reuses the chart-initialisation logic already in `ScopeViewerDialog.generatePlotlyHTML()` (refactored into a shared helper).

#### Scope mirror block

````markdown
```{circuit}
scope: 0
```
````

Embeds a canvas that mirrors scope *n* from the simulation.

---

## Implementation Phases

| Phase | Risk | Deliverable |
|-------|------|-------------|
| **1 — Editor** | Low | Split-pane textarea + preview in popup HTML. Save → JSNI → `CirSim.infoMarkdown`. Toggle for read-only view. |
| **2 — Live values** | Low–Med | `pushCircuitDataToInfoWindow()` JSNI in draw loop (throttled). `{{VarName}}` span substitution in viewer JS. |
| **3 — Live tables** | Medium | JSON table snapshots. Viewer JS patches `data-circuit-table` mounts without full re-render. |
| **4 — Live plots** | Medium–High | Plotly.js in info window fed from scope/variable JSON. Streaming append with configurable window length. |
| **5 — Quarto blocks** | High | Full ` ```{circuit} ``` ` block parser post marked.js. Mount tables, plots, and scope canvases from block attributes. |

---

## Key Technical Constraints

| Constraint | Detail |
|-----------|--------|
| **GWT JSNI** | All cross-window calls use JSNI (`/*-{ ... }-*/`); already used extensively in `InfoViewerDialog` |
| **Same-origin popup** | `$wnd.open('', 'circuitjs_model_info', ...)` + `document.write()` keeps the popup same-origin; `window.opener` access and `postMessage` both work without CORS issues |
| **No server** | JSON snapshot generated in-browser from `ComputedValues` + table element APIs |
| **Markdown rendered once per save** | Live-data sections are DOM-patched in place, preserving scroll, KaTeX rendering, and editor cursor |
| **Throttling** | `CirSim.updateCircuit()` runs at simulation rate (potentially >>1000/s). A `lastInfoWindowUpdateMs` guard limits pushes to ~20 fps |
| **Plotly reuse** | `ScopeViewerDialog` serialisation logic should be extracted to a `ScopeDataSerializer` helper used by both viewers |

---

## File Locations for Changes

| File | Change |
|------|--------|
| `InfoViewerDialog.java` | `generateMarkdownViewerHTML()` extended with split-pane editor; new `pushCircuitDataToInfoWindow()` JSNI; new `buildCircuitDataJson()` Java method |
| `CirSim.java` | Call `InfoViewerDialog.pushCircuitDataToInfoWindow()` in draw loop when info window is open; store `infoMarkdown` field |
| `SFCRParser.java` / `SFCRExporter.java` | Read/write `infoMarkdown` from `CirSim` (already done via `@info` block) |
| `ScopeViewerDialog.java` | Extract JSON serialisation to `ScopeDataSerializer.java` (new shared helper) |

---

## Interactive Examples Based on `1debug.txt`

The following examples use names that already exist in:

- `@equations`: `Y`, `YD`, `C_d`, `C_s`, `G_d`, `N_s`, `N_d`, `T_d`, `T_s`, `H_h`, `H_s`, `W`
- `@matrix`: `Balance_Sheet`, `Transaction_Flow_Matrix`
- `@scope`: the embedded GDP/output scope (`Y`)

### Example A — Full Model Dashboard (drop-in replacement)

````
@info
# SIM Model (Godley & Lavoie, Ch.3) — Live Dashboard

This document is editable in the Info Viewer and updates while simulation runs.

> **Time:** {{t}} yr  
> **Step:** {{dt}} yr

## 1) Headline Variables

| Variable | Live value | Meaning |
|---|---:|---|
| Output | {{Y}} | Aggregate production/income |
| Disposable income | {{YD}} | Household disposable income |
| Consumption demand | {{C_d}} | Desired consumption |
| Government demand | {{G_d}} | Exogenous government spending |
| Taxes paid | {{T_d}} | Taxes transferred to government |
| Household money stock | {{H_h}} | Wealth stock |
| Government money stock | {{H_s}} | Negative of debt position |

## 2) Behavioral/Accounting Equations (from `1debug.txt`)

$$Y = C_s + G_s$$
$$N_d = \frac{Y}{W}$$
$$YD = W\cdot N_s - T_s$$
$$T_d = \theta \cdot W \cdot N_s$$
$$C_d = \alpha_1\cdot YD + \alpha_2\cdot H_h$$
$$H_h = \int (YD - C_d)\,dt$$
$$H_s = \int (G_d - T_d)\,dt$$

## 3) Accounting Tables

```{circuit}
table: Balance_Sheet
```

```{circuit}
table: Transaction_Flow_Matrix
```

## 4) Time-Series Panel

```{circuit}
plot: Y, YD, C_d, H_h, H_s
title: SIM3 Core Stocks and Flows
yaxis: $
window: 200
```

## 5) Existing Scope Mirror

```{circuit}
scope: 0
```
@end
````

### Example B — Policy Experiment Sheet (change `G_d` and observe)

````
@info
# Policy Experiment — Government Demand Shock

Use the parameter equation table to modify **G_d** and watch adjustment paths.

## Before/After Tracking

| Metric | Live |
|---|---:|
| t | {{t}} |
| Y | {{Y}} |
| C_d | {{C_d}} |
| T_d | {{T_d}} |
| H_h | {{H_h}} |
| H_s | {{H_s}} |

## Experiment Notes

1. Set baseline `G_d = 20`
2. Increase `G_d` to `25`
3. Compare convergence of `Y`, `T_d`, and stock accumulation

## Dynamics Plot

```{circuit}
plot: G_d, Y, T_d, H_h, H_s
title: Fiscal Shock Transmission
yaxis: $
window: 300
```

## Consistency Check

```{circuit}
table: Transaction_Flow_Matrix
```
@end
````

### Example C — Teaching View (minimal but interactive)

````
@info
# SIM3 Quick Teaching View

At equilibrium, stocks stabilize when net flow into each stock is zero.

$$\frac{dH_h}{dt} = YD - C_d$$
$$\frac{dH_s}{dt} = G_d - T_d$$

If model converges:
- `YD - C_d → 0`
- `G_d - T_d → 0`

## Live Residuals

| Residual | Live |
|---|---:|
| Household residual (`YD - C_d`) | {{YD}} - {{C_d}} |
| Government residual (`G_d - T_d`) | {{G_d}} - {{T_d}} |

## Selected State Variables

- Output `Y`: **{{Y}}**
- Household stock `H_h`: **{{H_h}}**
- Government stock `H_s`: **{{H_s}}**

```{circuit}
plot: Y, H_h, H_s
title: Convergence to Steady State
yaxis: $
window: 150
```
@end
````

### Example D — Compact `@info` Template for 1debug-like models

Use this as a reusable scaffold:

````
@info
# {{MODEL_NAME}} — Live Report

> t={{t}}, dt={{dt}}

## Key outputs
| Var | Value |
|---|---:|
| Y | {{Y}} |
| YD | {{YD}} |
| C_d | {{C_d}} |
| T_d | {{T_d}} |
| H_h | {{H_h}} |
| H_s | {{H_s}} |

## Tables
```{circuit}
table: Balance_Sheet
```

```{circuit}
table: Transaction_Flow_Matrix
```

```{circuit}
table: ALL_TABLES
```

_Aliases: `table: *` and `tables: all`_

## Plot
```{circuit}
plot: Y, YD, C_d, H_h, H_s
title: Model dynamics
yaxis: $
window: 200
```
@end
````

### Notes for `1debug.txt` Compatibility

- Use placeholder names that match parser-safe variable keys (e.g. `{{Y}}`, `{{C_d}}`, `{{H_h}}`).
- Greek-escaped symbols like `\\alpha_1` and `\\theta` can stay in displayed equations, but live placeholders should reference plain computed keys available in `ComputedValues`.
- `table:` names should match table titles from the SFCR blocks (`Balance_Sheet`, `Transaction_Flow_Matrix`).

---

## Related Files

- [`InfoViewerDialog.java`](../src/com/lushprojects/circuitjs1/client/InfoViewerDialog.java) — current implementation
- [`ScopeViewerDialog.java`](../src/com/lushprojects/circuitjs1/client/ScopeViewerDialog.java) — Plotly serialisation to reuse
- [`ComputedValues.java`](../src/com/lushprojects/circuitjs1/client/ComputedValues.java) — live variable registry
- [`SFCR_FORMAT_REFERENCE.md`](./SFCR_FORMAT_REFERENCE.md) — `@info` block context
- [`SCOPE_ELEMENT_VIEWER_REFERENCE.md`](./SCOPE_ELEMENT_VIEWER_REFERENCE.md) — scope data model
- [`PLOTLY_VIEWER_FEATURE.md`](./PLOTLY_VIEWER_FEATURE.md) — existing Plotly integration
