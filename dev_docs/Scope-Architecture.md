# Scope Architecture (Post Phase 8)

Implementation location: `src/com/lushprojects/circuitjs1/client/scope/`

## Ownership
- `Scope` is the orchestrator: lifecycle, wiring, and mode/state transitions.
- `ScopePlot` is now a dedicated top-level type for per-trace buffers/config.
- `ScopeModel` owns mutable scope data:
  - plot collections (`plots`, `visiblePlots`)
  - draw-from-zero history buffers and downsampling state.
- `ScopeRuntimeState` owns transient runtime draw state (2D cursor/trace progression and fade state).

## Pure/Low-Coupling Components
- `ScopeLayout`: plot viewport and gutter geometry.
- `ScopeScaler`: range/tick/grid-step calculations.
- `ScopeDisplayConfig`: frame mode snapshot.
- `ScopeFrameContext`: per-frame computed context.
- `PlotScaleResult`: per-plot derived scaling values.

## Rendering and Interaction
- Rendering is split by responsibility:
  - `ScopeGridRenderer`
  - `ScopeWaveformRenderer`
  - `ScopeAxisRenderer`
  - `ScopeOverlayRenderer`
- `ScopeInteractionController` centralizes hit-testing and cursor mapping.

## Export and Serialization
- `ScopeDataExporter` now contains CSV/JSON export formatting for:
  - circular buffer exports
  - draw-from-zero history exports
- `Scope` delegates export entry points to `ScopeDataExporter`.
- Scope persistence remains in `Scope.dump()`/`Scope.undump()` for compatibility with existing load/save wiring.

## Package Boundary Notes
- `Scope` and related implementation classes now live in `client.scope`.
- Non-scope callers (`CirSim`, `ScopeManager`, dialogs, import/export code) depend on explicit public APIs on `Scope`.
- Cross-package element operations used by scope code go through explicit `CircuitElm`/`CirSim` bridge methods (`*ForScope`) instead of package-private reach-through.

## Test Coverage (Scope Refactor)
- Math and layout invariants:
  - `ScopeLayoutTest`
  - `ScopeScalerTest`
  - `ScopeDisplayConfigTest`
  - `ScopeFrameContextTest`
  - `ScopeInteractionControllerTest`
  - `ScopeModelTest`
- Compatibility and runtime behavior:
  - `ScopeHeadlessTest`
  - `ScopeYAxisBaselineTest`
  - `ScopeSerializationRoundTripTest`
  - `ScopePerformanceSmokeTest`
