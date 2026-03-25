# Scope Refactor Plan

## Goal
Refactor `Scope` into smaller, testable components while preserving current behavior and keeping each change safe to ship.

## Optimization Mode
This plan is optimized for **minimum risk**:
- smaller, reversible changes
- behavior-preserving wrappers before logic moves
- explicit phase gates (tests + compatibility + perf checks) before progressing

## Current Problems
- `Scope` mixes state management, layout math, scaling math, rendering, and interaction handling.
- Rendering paths recompute geometry/scaling in multiple places.
- Mode checks (`plot2d`, `showFFT`, `multiLhsAxes`, etc.) are scattered.
- Derived per-frame values are stored in mutable fields, increasing coupling and stale-state risk.
- Limited unit-test surface for core math (ticks, axis ranges, mapping).

## Target Architecture
- `Scope` (orchestrator): lifecycle, persistence, wiring of collaborators.
- `ScopeModel`: scope state and plot buffers (data ownership).
- `ScopeLayout`: pure geometry calculations.
- `ScopeScaler`: pure scale/range/tick calculations.
- `ScopeRenderer` split into focused renderers:
  - `ScopeGridRenderer`
  - `ScopeWaveformRenderer`
  - `ScopeAxisRenderer`
  - `ScopeOverlayRenderer`
- `ScopeInteractionController`: hit-testing, cursor/selection mapping.
- DTOs:
  - `ScopeFrameContext` (per-frame shared computed values)
  - `PlotScaleResult` (per-plot derived scale data)
  - `ScopeDisplayConfig` (mode/config snapshot)

## JVM/Headless Testing Infrastructure

Scope is testable via `CircuitJavaRunner` headless mode. When `RuntimeMode.isNonInteractiveRuntime()` is true:
- Scope initializes with null canvas (no GWT Canvas dependency)
- `timeStep()` populates data buffers without rendering
- Storage/localStorage calls are skipped

### What's testable now (no changes needed)

| Category | Access via |
|----------|------------|
| Data capture | `sim.scopes[i].plots.get(j).minValues[]`, `maxValues[]`, `samplesCaptured` |
| Scope state | `sim.scopes[i]` fields (flags, scale values, mode booleans) |
| Buffer logic | `timeStep()` populates buffers without canvas |

### What needs exposure for testing

Prefer testing extracted pure classes directly (`ScopeLayout`, `ScopeScaler`) rather than adding temporary public `*ForTesting()` methods on `Scope`.

### Example test pattern

```java
@Test
void scopeCapturesDataInHeadlessMode() throws Exception {
    RuntimeMode.setNonInteractiveRuntime(true);
    CirSim sim = new CirSim();
    sim.initializeRunnerForHeadlessExecution();
    sim.readCircuitFromModel(circuitWithScope);
    sim.analyzeAndPreStampForHeadlessExecution();
    
    // Run simulation steps
    for (int i = 0; i < 100; i++) {
        sim.runCircuitForTesting();
    }
    
    // Verify scope captured data
    assertTrue(sim.scopeCount > 0);
    Scope scope = sim.scopes[0];
    ScopePlot plot = scope.plots.get(0);
    assertTrue(plot.samplesCaptured > 0);
}
```

## Migration Strategy
Perform extraction in small phases. Keep compatibility shims until each phase is complete.

### Phase 0: Baseline and Test Harness
Deliverables:
- Add baseline tests for highest-risk math only before major moves.
- **Test infrastructure**:
  - Create `ScopeHeadlessTest.java` using `CircuitJavaRunner` pattern
  - Use existing simulation loop support; only add helper APIs if strictly required
- Capture baseline values for:
  - plot area/gutter math (LHS vs no-gutter modes)
  - y-axis range/tick values in normal + Multi-LHS mode
  - cursor-x to time mapping in plot viewport space

Deferred to later phases:
- FFT frequency label positioning
- `drawFromZero` downsample/history math

Acceptance criteria:
- New tests pass without behavior changes.
- Tests cover regular + Multi-LHS core invariants.
- No new temporary public testing methods added to `Scope`.

### Phase 1: Extract Layout (Low-Risk)
Deliverables:
- Create `ScopeLayout` with pure methods for:
  - `plotLeft`, `plotWidth`
  - gutter width
  - axis x-positions
  - info text anchor positions
- Add wrapper/delegation in `Scope` first, then migrate call sites gradually.

Acceptance criteria:
- No visual regressions in normal + Multi-LHS mode.
- Repeated geometry code in `Scope` reduced.
- No behavior changes.

### Phase 2: Extract Scaling (Low-Risk Math Isolation)
Deliverables:
- Create `ScopeScaler` with pure methods for:
  - auto/manual y-range
  - nice-step selection (`1,2,2.5,5 * 10^n`)
  - grid step selection (time axis via `calcGridStepX()`)
- Create `PlotScaleResult`; keep old fields during transition and map results into existing draw path.

Acceptance criteria:
- Same plotted output for existing test circuits.
- Multi-LHS ticks/ranges remain stable and readable.

### Phase 3: Introduce Mode Config Early
Deliverables:
- Add `ScopeDisplayConfig` and helper predicates (`isMultiLhsActive()`, `isFFTMode()`, `is2DMode()`).
- Replace repeated mode-condition chains incrementally in existing methods.

Acceptance criteria:
- Mode gating logic appears in one place.
- No behavior changes.

### Phase 4: Introduce Frame Context
Deliverables:
- Create `ScopeFrameContext` built once in `draw()`.
- Move shared computed values into context:
  - layout
  - stride/time-per-pixel
  - display time span
  - per-plot scale results
- Update draw helpers to consume context.

Acceptance criteria:
- Reduced parameter duplication and recomputation.
- No behavior changes in cursor/action marker placement.

### Phase 5: Split Rendering (With Shim)
Deliverables:
- Extract renderers with narrow responsibilities:
  - `ScopeGridRenderer`: grid lines (normal + FFT vertical grid)
  - `ScopeWaveformRenderer`: plot traces, 2D plot rendering (`draw2d()`)
  - `ScopeAxisRenderer`: axes (including Multi-LHS), FFT frequency labels
  - `ScopeOverlayRenderer`: info text, title, cursor (`drawCursor()`, `drawTitle()`, `drawInfoTexts()`)
- Define render order contract (lower z-order renders first):
  ```
  1. Grid (background)
  2. Waveforms (non-voltage units)
  3. Waveforms (current)
  4. Waveforms (voltage - on top)
  5. Selected plot (topmost)
  6. Axes/overlays
  ```
- Handle `draw2d()` early-return path explicitly.
- Keep render order controlled by `Scope`.

Acceptance criteria:
- Render output parity with baseline screenshots/manual checks.
- `Scope.draw()` becomes orchestration-focused.
- 2D plot mode renders correctly with extracted components.
- Existing rendering method signatures preserved until this phase is complete.

### Phase 6: Split Interaction
Deliverables:
- Create `ScopeInteractionController` for:
  - hover detection
  - cursor mapping
  - selection/hit-testing
- Use `ScopeLayout` + `ScopeFrameContext` for coordinate mapping.
- Accept `ScopeDisplayConfig` for mode-aware hit-testing.

Acceptance criteria:
- Mouse interactions remain correct with and without LHS gutter.
- Fewer direct references to raw `rect.width` and ad hoc offsets.

### Phase 7: Extract ScopeModel (Higher-Risk, Delayed)
Deliverables:
- Create `ScopeModel` to own data state:
  - `plots`, `visiblePlots` collections
  - circular buffer management
  - history buffers and `drawFromZero` helpers
- Move state ownership only after layout/scaling/render/interaction are already isolated.

Acceptance criteria:
- `drawFromZero` history capture/downsample logic testable in isolation.
- No behavior changes.

### Phase 8: Cleanup and Hardening
Deliverables:
- Remove compatibility shims and dead code.
- Add architecture notes and ownership docs.
- Add/refresh snapshot-style visual checks where practical.
- Add backward-compatibility checks for scope serialization/load paths.
- Add simple draw-path non-regression perf check.

Acceptance criteria:
- `Scope` is primarily orchestration, not mixed logic.
- Test coverage exists for core math and coordinate transforms.
- Legacy scope state load/save remains compatible.
- No measurable draw-path regression under baseline scenario.

## Suggested Commit Plan
1. Add baseline tests + small helpers (no behavior change).
2. Extract `ScopeLayout` and switch geometry call sites (with wrappers).
3. Extract `ScopeScaler` and `PlotScaleResult` (map into existing path).
4. Introduce `ScopeDisplayConfig` and centralize mode checks.
5. Add `ScopeFrameContext`; migrate draw helpers.
6. Split renderer classes (with compatibility shims).
7. Extract interaction controller.
8. Extract `ScopeModel` data ownership.
9. Add persistence/perf gates, then remove legacy paths and finalize docs/tests.

## Risks and Mitigations
- Risk: subtle rendering drift.
  - Mitigation: baseline tests + screenshot/manual diff checkpoints after each phase.
- Risk: interaction regressions from coordinate remapping.
  - Mitigation: focused tests around gutter + plot viewport boundaries.
- Risk: oversized refactor PR.
  - Mitigation: enforce phase-by-phase, reviewable commits.
- Risk: serialization compatibility break while moving state ownership.
  - Mitigation: add explicit load/save round-trip tests before and after Phase 7.
- Risk: draw performance regression from abstraction overhead.
  - Mitigation: establish baseline and compare draw timing at Phase 5 and Phase 8.
- Risk: FFT mode visual regression.
  - Mitigation: add FFT label/scale checks once renderer split begins.
- Risk: drawFromZero history corruption during downsample.
  - Mitigation: isolate and test downsample math in Phase 7 before ownership cutover.
- Risk: RHS info text overlap in LHS gutter mode.
  - Mitigation: test info text positioning doesn't clip during LHS gutter mode.
- Risk: 2D plot mode early-return breaks renderer extraction.
  - Mitigation: handle `draw2d()` path explicitly in Phase 5; consider `Scope2DRenderer` if complex.

## Definition of Done
- `Scope` orchestration logic is clearly separated from layout/scaling/render/interaction logic.
- Core math is unit-tested.
- Multi-LHS behavior remains intact:
  - axis drawn left of grid
  - no overlap clipping regressions
  - rounded tick scales and vertical labels
- Existing scope modes (normal, FFT, 2D, manual scale) continue working.

## Post-Phase-8 Size Reduction Options
To further trim `Scope` into a thinner coordinator, split remaining mixed concerns into focused collaborators:

1. `ScopePersistence`
- Move `dump()/undump()`, flag encode/decode, element reference token parsing/resolution, and default load/save.
- Keep `Scope` responsible only for orchestration and applying returned state.
- **Low risk**: dump/undump is well-defined I/O with clear contracts, ideal first extraction.

2. `ScopeMenuController`
- Move `handleMenu()` command dispatch and menu-string mapping.
- `Scope` exposes narrow intent methods (toggle flag, set mode, reset).
- **Low risk**: isolated command dispatch with no rendering dependencies.

3. `Scope2DController`
- Isolate 2D/XY-specific draw path, fade state, and XY selection behavior.
- Keeps normal waveform flow independent from 2D mode branching.
- **Note**: Use controller (not renderer) since XY selection behavior requires interaction logic.

4. `ScopeSelectionService`
- Move add/remove/combine/separate/selectY plot-management logic and visibility-policy updates.
- Keeps model-mutation policy outside rendering/orchestration code.
- **Note**: Touches visibility policy and plot ordering which affects rendering. Consider extracting before or alongside renderers to avoid coordination issues.

5. `ScopeLifecycleController`
- Move reset/reinit/image allocation flows (`initialize`, `resetGraph`, `allocImage`, rect/speed update behavior).
- Centralizes state transition rules for draw-from-zero/history resize cases.
- **Alternative**: Consider merging lifecycle into `ScopeModel` (Phase 7) since lifecycle (reset, reinit, resize) is tightly coupled to buffer state. Separating them may create awkward coordination.

6. `ScopeStatsService`
- Move RMS/average/frequency/duty/stat preparation into a dedicated service.
- Overlay rendering consumes prepared stats instead of recalculating inline.
- **Low risk**: Read-only computation, can wait until last.

7. `ScopeFFTHelper` (optional)
- Isolate FFT-specific frequency labeling, vertical grid rules, and scale logic.
- Consider extracting if FFT complexity grows or causes maintenance burden.
- Could be extracted alongside or instead of `ScopeStatsService`.

### Recommended implementation order (risk-first)
1. `ScopePersistence` — lowest coupling, easily testable
2. `ScopeMenuController` — isolated command dispatch
3. `Scope2DController` — XY mode isolation
4. `ScopeSelectionService` — before renderers to avoid coordination issues
5. `ScopeLifecycleController` (or merge into `ScopeModel`)
6. `ScopeStatsService` — read-only, can wait
7. `ScopeFFTHelper` — optional, based on complexity

### Design Notes
- Prefer merging `ScopeLifecycleController` into `ScopeModel` if lifecycle operations are primarily buffer-state transitions
- `ScopeSelectionService` extraction should consider render ordering dependencies
- FFT extraction is optional but recommended if FFT-specific logic becomes a maintenance burden
