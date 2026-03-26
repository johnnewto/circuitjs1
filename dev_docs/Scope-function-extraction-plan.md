# Scope Function Extraction Plan (Post `client/scope` Move)

## Goal
Reduce `Scope.java` to coordinator responsibilities by moving rendering/interaction-heavy methods into existing `scope*` classes without behavior changes.

## Current State Summary
- `Scope.java`: ~3540 lines, still contains rendering method bodies
- `ScopeInteractionController`: ~70% complete (cursor mapping done, selection logic remains)
- `ScopeAxisRenderer`, `ScopeOverlayRenderer`: thin passthrough delegators only
- `ScopeStatsService`: computation extracted; display glue still in Scope

## Constraints
- Do not change persistence format (`dump`/`undump`) behavior.
- Keep `Scope` as the single orchestration entry point used by non-scope packages.
- Prefer package-private internal APIs between `Scope` and `scope*` helpers; keep public API surface stable.
- Compile gate after each step: `./gradlew compileJava -q`.

## Key Design Decision: Method Bodies Move to Renderers
After extraction, renderers will own the method implementations. For example:
```java
// Before: Scope owns implementation
void renderAxisLayer(Graphics g, ScopeFrameContext frame) {
    drawMultiLhsGutter(g, frame);
    drawTopGutterLegend(g, frame);
    // ... 
}

// After: Scope delegates to renderer
void renderAxisLayer(Graphics g, ScopeFrameContext frame) {
    ScopeAxisRenderer.render(g, frame, buildAxisRenderContext());
}
```

## Target Mapping

### 0. Extend ScopeFrameContext for render parameters (Prerequisite)
Current field dependencies that methods need:
- `sim` (printable state, time formatting)
- `displayGridStepX`, `multa`, `scale[]`, `showNegative`
- `visiblePlots`, `selectedPlot`, cursor state (`cursorTime`, `mouseX`, `mouseY`)
- `maxValue`, `minValue`, `textY` (mutable!)

Action: Add immutable snapshot fields to `ScopeFrameContext` or create `ScopeRenderParams`:
- `printableMode: boolean`
- `displayGridStepX: double`
- `cursorState: {time, mouseX, mouseY, selectedPlot}`
- Keep `textY` in Scope (stateful draw loop - see note below)

Authoritative ownership (must not change):
- `Scope` owns mutable UI/runtime state (`selectedPlot`, `cursorTime`, `textY`, `maxValue/minValue`, `showNegative`).
- Renderers read from immutable snapshots/context only.
- Controllers compute/return values; they do not own persistent scope state.

### 1. Complete interaction extraction (partially done)
Already in `ScopeInteractionController`:
- ✅ `isWithinPlotX()`
- ✅ `mapCursorTimeForDrawFromZero()`
- ✅ `mapCursorTimeForScrolling()`
- ✅ `findNearestPlotIndexAtSampleX()`
- ✅ `findNearestPlotIndexInHistory()`
- ✅ `findHoveredActionIndex()`

Move from `Scope` to `ScopeInteractionController`:
- `findNearestMultiLhsAxisSelection(...)` → `findNearestMultiLhsAxisIndex(...)`
- `getHistoryIndexForSelection(...)` → `mapHistoryIndexForSelection(...)`

Reduce in `Scope`:
- `checkForSelection(...)` → pure delegation to controller methods

Keep in `Scope`:
- `selectScope(...)` as state owner (`mouseX`, `mouseY`, `cursorScope`, `cursorUnits`, `selectedPlot`)

### 2. Axis + legend + scale text rendering
Move method bodies from `Scope` to `ScopeAxisRenderer`:
- `drawScale(...)`
- `drawMultiLhsAxes(...)`
- `drawMultiLhsGutter(...)`
- `drawBottomTimeAxis(...)`
- `drawTopGutterLegend(...)`
- `getMultiLhsAxisName(...)`, `getMultiLhsAxisValueText(...)`

Keep in `ScopeScaler` (math/scaling layer):
- `calcMultiLhsAxisRange(...)` (already scaler-domain logic; renderer should consume results only)

Move to a time-mapping helper (not `ScopeLayout`):
- `getDisplayedTimeRange(...)` -> `ScopeInteractionController` (or `ScopeTimeMapping`) for shared axis/cursor time-window mapping.

Keep in `Scope`:
- `renderAxisLayer(...)` as thin delegator

### 3. Cursor overlay rendering
**Depends on Step 1** (uses `selectedPlot` and cursor state from selection)

Move method bodies from `Scope` to `ScopeOverlayRenderer`:
- `drawCursor(...)`
- `drawCursorInfo(...)`

Keep in `Scope`:
- `renderCursorLayer(...)` as thin delegator
- Cursor state fields (`cursorTime`, `selectedPlot`)

### 4. Info-text stat composition
**Note on `textY` mutable state**: `drawInfoTexts()` uses cumulative `textY` mutation:
```java
private void drawInfoText(Graphics g, String text) {
    g.drawString(text, getInfoTextX(), textY);
    textY += scaledSpacing;  // cumulative
}
```

Options:
- **Option A (recommended)**: Keep `drawInfoTexts()` in Scope as stateful orchestrator
- **Option B**: Refactor to compute layout positions first, then draw in second pass

Keep numerical calculations in `ScopeStatsService` (already done).
Stats display methods (`drawRMS`, `drawAverage`, `drawDutyCycle`, `drawFrequency`) stay in Scope as they're thin wrappers around `drawInfoText()`.
This is an intentional exception to keep `textY`-ordered layout deterministic.

### 5. Cleanup and documentation
- Remove dead helpers made redundant by extraction
- Update `dev_docs/Scope-Architecture.md`
- Update `SOURCE_FILE_MAP.md`

## Execution Order

| Step | Description | Gate |
|------|-------------|------|
| 0 | Extend `ScopeFrameContext` or add `ScopeRenderParams` | Compile passes |
| 1 | Complete interaction extraction (2 remaining methods) | Hover selection unchanged in normal + draw-from-zero + multi-LHS |
| 2 | Extract axis rendering (move method bodies) | Axis positions/labels/ticks/legend unchanged in printable + normal |
| 3 | Extract cursor rendering (move method bodies) | Cursor dot, readout text, vertical marker identical |
| 4 | Keep stats in Scope (Option A) or extract display strings | Info text layout unchanged |
| 5 | Cleanup + docs | No new package-private reach-through |

## Verification Checklist
- Compile: `./gradlew compileJava -q`
- Run scope tests:
  - `ScopeSerializationRoundTripTest`
  - `ScopeHeadlessTest`
  - `ScopeYAxisBaselineTest`
  - `ScopeInteractionControllerTest`
- **Add new tests**:
  - Cursor-time → pixel roundtrip for draw-from-zero and scrolling modes
  - Multi-LHS axis index selection accuracy
- Manual smoke:
  - multi-LHS mode (legend, axis ticks, hover select)
  - draw-from-zero hover selection
  - FFT mode cursor readout

## Completion Criteria
- `Scope.java` is primarily coordinator + state owner
- Rendering method bodies live in `ScopeAxisRenderer`, `ScopeOverlayRenderer`
- Interaction logic concentrated in `ScopeInteractionController`
- No behavior regressions in selection, axis layout, or exported/persisted formats
- `getDisplayedTimeRange()` moved to shared time-mapping helper (`ScopeInteractionController` or `ScopeTimeMapping`), not `ScopeLayout`
- `drawRMS`/`drawAverage`/`drawDutyCycle`/`drawFrequency` may remain in `Scope` as the planned stateful-info-layout exception
