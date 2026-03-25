# uPlot PlotElm Implementation Plan

## Goal

Add a new `PlotElm` that:

- plots simulation values sourced from gslots (`sim.circuitVariables[]`)
- supports multiple left-hand-side y-axes (multi-LHS)
- uses `uPlot` for interactive chart rendering
- persists with normal CircuitJS dump/load and SFCR import/export

---

## Scope and Non-Goals

### In scope

- New display element type (`PlotElm`, no electrical posts)
- Per-trace binding to gslot names/indices
- Per-trace axis assignment (`axisId`) with multi-LHS rendering
- Rolling time window buffers
- uPlot-based viewer (interactive)
- Serialization/deserialization
- SFCR parser/exporter support for plot element blocks

### Out of scope (initial release)

- Right-side axes
- FFT/2D modes parity with `Scope`
- Derived expression traces inside `PlotElm` (raw gslot traces only)
- Advanced annotation tooling

---

## Proposed Architecture

## 1. Data capture layer in `PlotElm`

`PlotElm` owns:

- `TraceConfig[]`: `slotName`, `slotIndex`, `label`, `color`, `axisId`, `enabled`
- rolling buffers per trace (`double[] y`, fixed-size ring)
- shared x-buffer (time values)
- axis metadata (`axisId -> min/max/auto/fixed range`)

Sampling occurs once per converged simulation step (same cadence as scopes).

## 2. Gslot binding strategy

- Resolve `slotName -> slotIndex` via `sim.nameToSlot` after analyze/re-analyze
- Read values via `sim.circuitVariables[slotIndex]`
- Keep `slotName` as canonical persisted key; `slotIndex` is cached runtime optimization
- If a slot disappears, mark trace unavailable (do not crash, show status in editor/viewer)

## 3. Rendering strategy

Use uPlot in a DOM container (popup/iframe/template), not directly on simulator canvas.

Why:

- `uPlot` is DOM/canvas managed and interactive
- simulator main canvas rendering path is immediate-mode and not suited to embedded uPlot lifecycle
- popup/iframe path matches existing Plotly viewer patterns and lowers risk

## 4. Multi-LHS axis model

- `axisId` is integer starting at `0`
- each unique `axisId` maps to one left y-scale
- cap axes count to 5 (same practical limit as existing scope multi-LHS behavior)
- each series declares `scale` matching its axis key in uPlot config

---

## Files and Touchpoints

## New

- `src/com/lushprojects/circuitjs1/client/elements/misc/PlotElm.java`
- `src/com/lushprojects/circuitjs1/client/ui/PlotElmViewerDialog.java` (or template-backed helper)
- `src/com/lushprojects/circuitjs1/client/ui/PlotElmViewerTemplate.html` (if template approach used)
- optional: local vendored uPlot assets under `war/` or public resources

## Modified

- `src/com/lushprojects/circuitjs1/client/registry/ElementRegistryBootstrap.java`
- `src/com/lushprojects/circuitjs1/client/registry/ElementLegacyFactory.java`
- `src/com/lushprojects/circuitjs1/client/io/SFCRParser.java`
- `src/com/lushprojects/circuitjs1/client/io/SFCRExporter.java`
- menu wiring locations if needed (`CirSim` command routing / UI menus)

## Existing runtime dependencies

- slot sync path in `SimulationLoop` already updates `sim.circuitVariables[]`
- slot metadata in `CircuitValueSlotManager` (`nameToSlot`, `slotNames`)

---

## Serialization Design

`PlotElm.dump()` should include:

- geometry (`super.dump()`)
- plot settings (`windowSize`, legend/style flags)
- axis definitions (fixed/auto ranges)
- traces count + escaped trace entries
  - `slotName`
  - `axisId`
  - `label`
  - `color`
  - `enabled`

On load:

- restore configs
- defer slot resolution until post-analyze/first step

---

## SFCR Format Extension

Add block form:

```text
@plotelm MyPlot position=2
  title: World variables
  window: 400
  axis: 0 auto
  axis: 1 range 0,100
  trace: slot=P label=Population color=#1f77b4 axis=0
  trace: slot=POLR label=Pollution color=#d62728 axis=1
@end
```

Parser behavior:

- collect block specs
- create `PlotElm`
- apply properties/traces

Exporter behavior:

- emit deterministic block order
- preserve escaped names/colors/labels

---

## Phased Implementation

## Phase 0: Static Data MVP (Research Spike)

Validate uPlot integration with hardcoded data before full implementation.

### Deliverables

1. **Static test page** (`war/uplot-test.html`):
   - uPlot loaded via CDN (and local fallback)
   - 3 traces with 100 static data points each
   - Multi-LHS axis configuration (2 left axes, different scales)

2. **Popup opener test**:
   - JSNI `window.open()` from GWT (copy pattern from `ScopeViewerDialog`)
   - Verify popup works in GWT context

3. **Canvas placeholder sketch**:
   - Basic rect with "Click to open" text in any test element's `draw()`

### Test HTML Skeleton

```html
<!DOCTYPE html>
<html>
<head>
  <script src="https://unpkg.com/uplot@1.6.24/dist/uPlot.iife.min.js"></script>
  <link rel="stylesheet" href="https://unpkg.com/uplot@1.6.24/dist/uPlot.min.css">
</head>
<body>
  <div id="chart"></div>
  <script>
    const data = [
      Array.from({length: 100}, (_, i) => i * 0.1),
      Array.from({length: 100}, (_, i) => Math.sin(i * 0.1) * 10),
      Array.from({length: 100}, (_, i) => Math.cos(i * 0.1) * 100),
      Array.from({length: 100}, (_, i) => i * 0.5),
    ];

    const opts = {
      width: 800, height: 400,
      scales: { x: { time: false }, y1: {}, y2: {} },
      axes: [
        {},
        { scale: 'y1', side: 3, label: 'Small (-10 to 10)' },
        { scale: 'y2', side: 3, label: 'Large (0 to 100)' },
      ],
      series: [
        {},
        { scale: 'y1', stroke: 'red', label: 'Sin' },
        { scale: 'y2', stroke: 'blue', label: 'Cos' },
        { scale: 'y1', stroke: 'green', label: 'Linear' },
      ],
    };

    new uPlot(opts, data, document.getElementById('chart'));
  </script>
</body>
</html>
```

### Validation Checklist

- [ ] Multi-LHS axes render on left side (both `side: 3`)
- [ ] Independent scaling per axis works
- [ ] Zoom/pan interactions work
- [ ] Popup opens from GWT via `window.open()`
- [ ] Data embedded in popup HTML (no external fetch)
- [ ] Works offline with local uPlot assets

### Acceptance Criteria

- uPlot renders multi-axis chart in popup from GWT context
- Static test page works standalone and via popup
- Decision: proceed with full implementation or adjust approach

**Estimated effort:** 3-4 hours

## Phase 1: Element skeleton + data capture

1. Add `PlotElm` class with geometry, dump/load, edit hooks.
2. Add trace config model and ring buffers.
3. Hook sampling in `stepFinished()` using gslot values.
4. Register dump type and constructors.

Deliverable: non-interactive element captures data and is serializable.

## Phase 2: uPlot viewer integration

1. Add viewer dialog/template and uPlot asset loading.
2. Build data payload from `PlotElm` buffers.
3. Render series with per-series scale mapping.
4. Implement live update tick and reset behavior.

Deliverable: interactive chart with multi-LHS axes.

## Phase 3: Editing + SFCR support

1. Add edit UI for traces/axes/options.
2. Add SFCR parser/exporter support.
3. Add import/export compatibility tests.

Deliverable: user-configurable model-level feature.

## Phase 4: hardening

1. Validate missing/renamed slots.
2. Ensure behavior across re-analyze and reset.
3. Performance tune for high trace/window sizes.
4. Add docs and examples.

Deliverable: production-ready behavior.

---

## Test Plan

## Unit tests

- trace slot resolution (`slotName -> slotIndex`)
- missing slot fallback behavior
- ring buffer correctness (wrap, ordering)
- dump/undump round trip
- axis assignment and max-axis cap

## Integration tests

- load SFCR with `@plotelm`, run sim, verify element exists and captures
- rename/remove variable and verify graceful degradation
- re-analyze circuit and ensure slot rebinding

## Manual checks

- world/economics model with 3-5 scales
- mixed magnitudes on separate LHS axes
- run/stop/reset behavior
- popup blocker and viewer error messaging

---

## Risks and Mitigations

- **Risk:** slot names drift after model edits  
  **Mitigation:** keep `slotName` canonical, re-resolve on analyze, expose unresolved status.

- **Risk:** performance with many traces and large windows  
  **Mitigation:** fixed-size buffers, capped update rate, bounded max traces/points.

- **Risk:** uPlot asset/CDN availability  
  **Mitigation:** prefer local vendored assets for deterministic offline behavior.

- **Risk:** parity expectations with `Scope`  
  **Mitigation:** document MVP constraints and optional follow-up roadmap.

---

## Acceptance Criteria

- `PlotElm` can be added, moved, saved, and loaded.
- At least 1-5 traces can bind to gslot names and stream values live.
- Multi-LHS axes render correctly with independent scaling per axis.
- Viewer remains stable during run/stop/reset and re-analyze.
- SFCR export/import preserves `PlotElm` configuration.
- No simulation instability introduced by `PlotElm` presence.

