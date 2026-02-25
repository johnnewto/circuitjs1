# Scope Element and Plotly Viewer Reference

## Overview

This document explains how the embedded scope element (`ScopeElm`) and the Plotly viewer (`ScopeViewerDialog`) work end-to-end in CircuitJS1.

At a high level:
- `ScopeElm` is a **docked or floating, on-canvas scope container** (an element with no electrical posts).
- `Scope` is the **core engine** for scope data collection, scaling, rendering, and serialization.
- `ScopeViewerDialog` exports one or more `Scope` instances and opens an interactive Plotly window.

---

## Core Classes and Responsibilities

### `ScopeElm` (`src/com/lushprojects/circuitjs1/client/ScopeElm.java`)

`ScopeElm` extends `CircuitElm`, but is display-only:
- `getPostCount()` returns `0` (no electrical terminals).
- `getDumpType()` returns `403`.
- Holds one `Scope` instance in `elmScope`.
- Maintains a rectangle mapped from circuit coordinates via simulator transforms.
- Delegates sampling to `elmScope.timeStep()` through `stepScope()`.
- Delegates drawing to `elmScope.draw(g)` and adds a shadow/background box for visual separation.

It supports two draw paths:
- **Normal rendering** (`drawNormal`) for live canvas display.
- **Export rendering** (`drawForExport`) that scales drawing context for higher-resolution export output.

### `Scope` (`src/com/lushprojects/circuitjs1/client/Scope.java`)

`Scope` is the main implementation for both docked and floating scopes:
- Owns plots (`Vector<ScopePlot>`) and visible plot filtering.
- Captures waveform min/max over time.
- Handles manual scale, auto scale, max scale, draw-from-zero, FFT, 2D plotting, labels, titles, and cursor info.
- Draws action-time markers and annotations from `ActionScheduler`.
- Serializes/deserializes scope settings and data state (`dump()`/`undump()`).
- Exports data as CSV/JSON (circular buffer or history).

### `ScopePlot` (`Scope.java`, inner class)

`ScopePlot` stores per-trace state:
- Circular min/max buffers (`minValues[]`, `maxValues[]`) with pointer `ptr`.
- Value type (`value`) and display units (`units`) — see reference tables below.
- Plot speed (`scopePlotSpeed`) and manual scale state (`manScale`, `manScaleSet`).
- Optional AC coupling filter state (`acCoupled`, `acAlpha`, `acLastOut`).
- Optional history arrays (`historyMinValues[]`, `historyMaxValues[]`) for draw-from-zero mode.

#### Value-type constants (`VAL_*`)

| Constant | Value | Meaning | Source method in `CircuitElm` |
|----------|-------|---------|-------------------------------|
| `VAL_VOLTAGE` | 0 | Voltage across element | `getVoltageDiff()` |
| `VAL_POWER_OLD` | 1 | Power (legacy, conflicts with `VAL_IB`) | `getPower()` |
| `VAL_R` | 2 | Resistance | element-specific |
| `VAL_CURRENT` | 3 | Current through element | `getCurrent()` |
| `VAL_POWER` | 7 | Power (current) | `getPower()` |
| `VAL_IB` | 1 | Base current (transistors only) | transistor-specific |
| `VAL_IC` | 2 | Collector current (transistors only) | transistor-specific |
| `VAL_IE` | 3 | Emitter current (transistors only) | transistor-specific |
| `VAL_VBE` | 4 | V(base–emitter) (transistors only) | transistor-specific |
| `VAL_VBC` | 5 | V(base–collector) (transistors only) | transistor-specific |
| `VAL_VCE` | 6 | V(collector–emitter) (transistors only) | transistor-specific |

The value to sample is read via `elm.getScopeValue(value)`, which `CircuitElm` dispatches to `getCurrent()`, `getPower()`, or `getVoltageDiff()`. Elements can override `getScopeValue()` and `getScopeUnits()` to expose custom quantities.

#### Unit constants (`UNITS_*`)

| Constant | Value | Format |
|----------|-------|--------|
| `UNITS_V` | 0 | Volts |
| `UNITS_A` | 1 | Amperes |
| `UNITS_W` | 2 | Watts |
| `UNITS_OHMS` | 3 | Ohms |

Each `ScopePlot` carries its own `units` value, which controls axis labelling, colour defaults, and AC-coupling eligibility (only `UNITS_V` plots may be AC-coupled).

### `ScopeViewerDialog` (`src/com/lushprojects/circuitjs1/client/ScopeViewerDialog.java`)

`ScopeViewerDialog` builds a self-contained Plotly HTML page:
- Collects one scope or all scopes.
- Calls `scope.exportHistoryAsJSON()` when draw-from-zero history is present, otherwise `scope.exportCircularBufferAsJSON()`.
- Injects data into generated HTML + JavaScript.
- Opens a popup window via JSNI (`window.open`) and tracks open viewer windows in `window.plotlyWindows`.

---

## Runtime Flow

### 1) Creation and wiring

Floating scope creation path:
- User invokes `viewInFloatScope` from element menu.
- `CirSim.menuPerformed()` creates `new ScopeElm(...)`, adds it to `elmList`, and calls `newScope.setScopeElm(menuElm)`.
- `setScopeElm()` sets monitored element + resets graph.

Docked scope creation path:
- User invokes `viewInScope` and `CirSim` creates/assigns an entry in `scopes[]`.

### 2) Scope registration/caching in simulation

During analysis setup, `CirSim`:
- Copies all elements to `elmArr`.
- Builds `scopeElmArr` containing only `ScopeElm` instances to avoid repeated type checks in the timestep loop.

`setupScopes()` arranges docked scope rectangles/columns and ensures scope list consistency.

### 3) Sampling each simulation step

After circuit solve convergence in `runCircuit()`:
- Docked scopes are sampled via `scopes[i].timeStep()`.
- Floating scopes are sampled via `scopeElmArr[i].stepScope()`.

Inside `Scope.timeStep()`:
- Each plot calls `ScopePlot.timeStep()`.
- Plot captures min/max of the current value into the current pixel bucket.
- The bucket pointer advances when `sim.t - lastUpdateTime >= sim.maxTimeStep * scopePlotSpeed`.
- If draw-from-zero mode is enabled, settled data is copied to history buffers (with ×2 downsampling when `historyCapacity` is reached).

#### Speed / `scopePlotSpeed` explained

`Scope.speed` (set by `setSpeed()` or the UI speed controls) is stored in **sim-timestep units per pixel**.  During `ScopePlot.reset()` this is copied into `ScopePlot.scopePlotSpeed`.  Higher values compress more simulation time into each pixel column, slowing the apparent scroll rate.

The circular buffer length `scopePointCount` is the smallest power of 2 ≥ `rect.width` (recalculated in `resetGraph()`).  So a 500 px wide scope has a 512-entry buffer; each entry holds the min and max sample seen during `scopePlotSpeed` sim-timesteps.

### 4) Rendering

`ScopeElm.draw(g)`:
- Saves and applies an **inverse canvas transform** (`scale(1/transform[0], 1/transform[3])` + `translate`) so that internal scope pixel coordinates are screen-absolute, unaffected by circuit zoom/pan.
- Draws a shadow background rectangle for visual separation.
- Calls `elmScope.draw(g)` for the actual content.
- Floating scopes also pass a `zoomScale` (average of x/y transform scale factors) to `Scope.setZoomScale()` so that labels, axis text, and the settings wheel scale proportionally with the circuit zoom level.

`Scope.draw(g)` handles:
- Standard scrolling time-plot rendering (`drawPlot()` per visible plot, voltage drawn last/on top).
- Optional FFT mode (`drawFFT()`).
- Optional 2D/XY mode (`draw2d()`).
- Scale/grid calculation and labels (`drawScale()`).
- Cursor readout and selected-plot highlighting.
- RMS, average, frequency, and duty-cycle overlays.
- Action-time vertical markers and annotation popups.
- Settings wheel icon (bottom-left corner; hidden when scope is too small).

#### Display modes

| Mode | Flags | Description |
|------|-------|-------------|
| Standard (scrolling) | default | Continuous circular-buffer waveform scroll from right to left |
| Draw From Zero | `drawFromZero = true` | Waveform grows from left edge; time axis anchored at `startTime`; history buffer preserves full run |
| Auto-scale time | `autoScaleTime = true` | Automatically slows `speed` when waveform reaches right edge in draw-from-zero mode |
| Max scale | `maxScale = true` | Y scale tracks the peak value seen in the visible window |
| Manual scale | `manualScale = true` | Fixed Y scale; each plot has its own `manScale` (units/division) and `manVPosition` |
| FFT | `showFFT = true` | Replaces time plot with frequency-domain spectrum of the first plot |
| 2D / Lissajous | `plot2d = true` | Plots first two traces against each other in an XY plane on an off-screen canvas |
| XY | `plot2d && plotXY` | Same as 2D but the x-axis trace is an explicit second element rather than a phase-shifted version of the first |

---

## Data Modes and Buffers

### Circular buffer mode (default)

- Fixed-size ring buffer per plot (`minValues[]` / `maxValues[]`, length = `scopePointCount`).
- `scopePointCount` is the smallest power of 2 ≥ `rect.width`, recalculated on `resetGraph()`.
- Buffer pointer is masked: `ptr = (ptr + 1) & (scopePointCount - 1)`.
- Efficient for continuous scrolling display.
- Export methods: `exportCircularBufferAsCSV()` and `exportCircularBufferAsJSON()`.

### Draw-from-zero history mode

- Activated via `toggleDrawFromZero()` or the scope popup menu.
- Keeps long-run history from simulation start (`startTime`).
- Uses per-plot `historyMinValues[]` / `historyMaxValues[]` and `historySize` at the `Scope` level.
- `historySampleInterval` doubles each time capacity is reached; old entries are downsampled by keeping min-of-mins and max-of-maxs across each pair of adjacent samples.
- Export methods: `exportHistoryAsCSV()` and `exportHistoryAsJSON()`.

### AC coupling filter

Enabled per-plot via the scope properties dialog (voltage plots only).  Implemented as a first-order IIR high-pass filter computed on every sample regardless of coupling mode so it stays primed:

```
y[i] = α × (y[i-1] + x[i] − x[i-1])
```

α is adjusted so the time constant spans the full visible window: `α = 1 − 1 / (1.15 × scopePlotSpeed × scopePointCount)`.  When AC coupling is enabled, `y[i]` is stored in the buffer instead of `x[i]`.

### Visible-plot filtering

`calcVisiblePlots()` builds `visiblePlots` from `plots`:
- In normal mode it honours `showV` (show voltage traces) and `showI` (show current traces) flags; `showV = showI = false` means show all plots.
- In 2D mode only the first two plots are included.
- After filtering, `assignColor()` is called on each visible plot to set default palette colours (keyed to units) or cycle through the colour array for multi-trace scopes.

### How plots are referenced

There are three distinct indexing/reference layers:

1. Internal scope storage (`plots`)
  - `Scope.plots` is the canonical list of all traces in a scope.
  - Many operations (sampling, dump/undump, export payload generation) iterate this vector.
  - In standard single-element usage, index 0 is usually voltage and index 1 may be current.

2. Rendered subset (`visiblePlots`)
  - `Scope.visiblePlots` is rebuilt from `plots` by `calcVisiblePlots()` based on mode and flags.
  - UI-driven selection uses this index space (`selectedPlot` and popup `menuPlot`).
  - Because this is a filtered view, `visiblePlots[i]` is not guaranteed to equal `plots[i]`.

3. Viewer/export references
  - In Plotly export, each scope gets a `scopeIndex` (its position in exported scope list).
  - Inside each scope JSON object, traces are emitted as `plots[]` entries; Plotly renders these as trace order 0..N-1 per chart.
  - In generated HTML, each chart container uses `plot-{scopeIndex}` as the DOM id.

Practical implication: if you add or remove traces dynamically, call `calcVisiblePlots()` before relying on selection indices, and use object identity (`ScopePlot` instance) rather than raw index equality when mapping between `plots` and `visiblePlots`.

---

## Serialization and Persistence

### Scope element dump format

`ScopeElm.dump()`:
- Starts with `super.dump()` element geometry/flags.
- Appends scope payload from `elmScope.dump()`.
- Replaces spaces with underscores and strips leading `o_` prefix for embedded scope format compatibility.

`ScopeElm` restore constructor:
- Parses scope payload with `StringTokenizer` (`_` delimiter).
- Calls `elmScope.undump(...)`.
- Calls `setPoints()` to set rectangle without clearing restored graph state.

### Scope flags

`Scope` stores many display flags and per-plot settings in its dump format (manual scales, draw-from-zero, max scale limits, per-plot flags, etc.), then reconstructs via `undump()`.

### How plots are referenced in the dump payload

Current implementation uses **UID-based plot references** with legacy index fallback.

#### Primary method (UID-based)

When `FLAG_PLOT_REFS` is set, each plot carries a `U:<escaped persistentUid>` token:

- Plot 0 UID token is stored in the scope header.
- Plot `i > 0` UID tokens are stored in each per-plot payload.
- On load, `Scope.undump()` resolves by UID first.

This is stable across element-line reordering and typical element parameter changes.

#### Payload layout (high level)

Scope header still includes standard fields:
- `e0`, `speed0`, `value0`, `flags`, `scaleV`, `scaleA`, `position`, `plotCount`
- Optional `manDivisions`, max-limit tokens (`Lx:value`)

Per-plot payload still includes:
- Optional `plotFlags[i]`
- For `i > 0`: `ei`, `vi` (legacy index/value pair)
- Optional scale token for non-V/A units
- Optional manual scale tokens

UID tokens are additive metadata used for primary binding; index tokens remain for compatibility.

#### Minimal UID example

For two plots (V on element UID `Ab3_-9`, I on UID `K2mQx0`), the logical pattern is:

`o e0 speed0 value0 flags U:Ab3_-9 scaleV scaleA position 2 ... U:K2mQx0 e1 v1 ...`

#### Legacy / fallback behavior (short)

- If UID lookup fails, loader falls back to `ei` element index.
- If `FLAG_PLOTS` is absent, loader uses the legacy pre-plot-list format.
- Older `R:` tokens are still accepted for backward compatibility.

---

## Plotly Viewer Flow

### Entry points

- Scopes menu: `viewAllPlotly` → `new ScopeViewerDialog(this)`.
- Scope popup menu: `viewplotly` → `new ScopeViewerDialog(this, s)`.
- Auto-open on stop (from `StopTimeElm`): `new ScopeViewerDialog(sim, null, true)`.

### `openViewer()` sequence

1. Build JSON array for one scope or all scopes.
2. For each scope, call `exportScope(...)`:
   - Add scope name/index metadata.
   - Add enabled action times/annotations from `ActionScheduler`.
   - Merge scope JSON payload (history or circular).
3. Build full HTML string in `generatePlotlyHTML(...)`.
4. Open popup and write HTML via `openWindowWithHTML(...)`.

### Generated viewer capabilities

- One Plotly chart block per scope.
- Range slider on x-axis.
- Optional auto-scale to visible range.
- Interactive zoom/pan/hover/legend toggling.
- Action markers rendered as vertical lines + annotations.
- Bulk download buttons (JSON/CSV) for all plotted scopes.
- Runtime resizing controls (width/height).

### Exported JSON shape

Each scope in the top-level JSON array has the following structure:

```json
{
  "scopeName": "Resistor V",
  "scopeIndex": 0,
  "actionTimes": [0.005, 0.010],          // optional – from ActionScheduler
  "actionAnnotations": ["Step", "Stop"],   // optional
  "source": "CircuitJS1 Scope",
  "exportType": "history",                 // or "circularBuffer"
  "historySize": 512,                      // history export only
  "sampleInterval": 2e-5,                  // history export only
  "simulationTime": 0.01024,               // circular buffer export only
  "timeStep": 2e-5,                        // circular buffer export only
  "plots": [
    {
      "name": "Voltage",
      "units": "V",
      "color": "#00FF00",
      "time":      [0.0, 2e-5, 4e-5, ...],
      "minValues": [0.0, 0.94, 1.83, ...],
      "maxValues": [0.0, 1.06, 1.97, ...]
    }
  ]
}
```

The `plots[*].time` array is synthesised by the exporter from simulation-time metadata; it is not stored in the scope's own buffers.

---

## Stacking, Combining, Docking, and Undocking

### Stack / Unstack

Docked scopes in the same column share horizontal space.  `stackScope(n)` sets `scopes[n].position = scopes[n-1].position`; `setupScopes()` then recalculates each column's row height by dividing by `scopeColCount[pos]`.  The speed of all scopes in a column is locked to the first scope's speed.

### Combine / Separate

`combineScope(n)` merges another scope's `visiblePlots` into the target's `plots` vector and empties the source.  `separate()` does the inverse, splitting each visible plot into its own scope.

### Dock / Undock

- **Undocking** a docked scope (`item == "undock"`): a new `ScopeElm` is created on the canvas, the `Scope` instance is moved from `scopes[]` into the new `ScopeElm.elmScope`, and `needAnalyze()` rebuilds `scopeElmArr`.
- **Docking** a floating scope (`item == "dock"`): the `Scope` from `ScopeElm.elmScope` is appended to `scopes[]`, the `ScopeElm` is deleted from `elmList`, and `scopeCount` is incremented.  The scope data (including history) is preserved across both operations.

---

## Menu and UX Integration

`ScopePopupMenu` includes:
- `Draw From Zero`
- `Export Data...`
- `View in Plotly...`
- `Properties...`
- Dock/undock and stack/combine controls

`CirSim.menuPerformed()` routes menu commands for both docked and floating scopes using the same `menu == "scopepop"` block, so all core operations are available regardless of scope type.

---

## Practical Notes and Common Gotchas

- `ScopeElm` is purely visual; do not add electrical stamping behaviour.
- Keep `ScopeElm` rectangle updates transform-aware (`transformX/transformY`) to avoid zoom/pan drift.
- Sampling runs in the main simulation loop, so expensive per-point logic in `ScopePlot.timeStep()` affects runtime.
- For long simulations, prefer history downsampling behaviour over unbounded arrays.
- The Plotly popup viewer is opened with `window.open(); document.write(html)`.  If the window does not appear, it is almost always browser popup-blocking.  The `StopTimeElm` auto-open path logs a console message when blocked.
- **Speed mismatch data loss**: when a scope's `speed` changes, `ScopePlot.reset()` detects the mismatch (`scopePlotSpeed != sp`) and discards the entire buffer.  This is intentional (incompatible time bases) but can surprise users who adjust speed mid-run.
- **Buffer size and `resetGraph()`**: calling `resetGraph(false)` preserves as much of the old buffer as fits in the new size.  Calling `resetGraph(true)` discards everything.  The `ScopeElm` restore constructor deliberately avoids calling `resetGraph()` after `undump()` to preserve loaded data.
- **Adding a new plottable quantity**: override `getScopeValue(int x)` and `getScopeUnits(int x)` in the element class.  Choose a `VAL_*` constant not yet used by that element type; transistor constants `VAL_IB`–`VAL_VCE` (1–6) are reserved for transistors and clash with legacy power value 1 on non-transistor elements.
- **`manDivisions`** is a global default (`lastManDivisions`) shared across all newly created scopes; changing it in the properties dialog updates the static and all subsequently created scopes.

---

## Related Docs

- `dev_docs/SCOPE_EXPORT_FEATURE.md`
- `dev_docs/PLOTLY_VIEWER_FEATURE.md`
- `dev_docs/ARCHITECTURE.md`

---

## Migration Note (UID-based plot refs)

- Older circuit files that do not include element `U:` tokens still load normally.
- On first load of a legacy file, elements without a UID are assigned one in memory.
- On next save/export/copy, element lines are written with `U:<uid>` metadata.
- Scope refs now prefer UID tokens (`U:`) and only fall back to element index (`ei`) when UID lookup fails.
- Existing older scope reference tokens (`R:`) remain supported for backward compatibility.
- Mixed UID lengths are valid; resolver treats UID as an opaque string.
- Current generator emits 6-character URL-safe UIDs (`A-Z a-z 0-9 - _`).
