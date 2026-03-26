# Scope Commit Checklist (Updated Status)

1. `scope: add migration safety net tests` ✅ Done
- Files: `ScopeSerializationRoundTripTest.java`, `ScopeHeadlessTest.java`, `ScopeYAxisBaselineTest.java`
- Add coverage for manual scale, FFT flag, multi-LHS, draw-from-zero, and mixed-unit plots.
- Gate: all scope tests pass before any refactor.

2. `scope: extract ScopePlot to top-level class` ✅ Done
- Files: new `ScopePlot.java`, edit `Scope.java`
- Move inner class out with no behavior changes.
- Gate: compile + tests unchanged.

3. `scope: introduce ScopeRuntimeState facade for mutable draw state` ✅ Done
- Files: new `ScopeRuntimeState.java`, edit `Scope.java`
- Move transient per-frame/per-draw mutable fields out of `Scope`.
- Gate: `Scope.draw()` logic still identical, tests pass.

4. `scope: centralize mode/flag access through ScopeDisplayConfig + state` ✅ Done
- Files: `Scope.java`, `ScopeDisplayConfig.java`
- Replace scattered `plot2d/showFFT/multiLhsAxes/manualScale` checks with one access path.
- Gate: no behavior change in baseline tests.

5. `scope: move remaining scale computations behind ScopeScaler` ✅ Mostly done
- Files: `Scope.java`, `ScopeScaler.java`, `PlotScaleResult.java`
- Pull remaining `calcPlotScale`/related math out of `Scope`.
- Gate: y-axis/tick tests unchanged.

6. `scope: complete renderer ownership of draw layers` ✅ In place (with residual helpers in `Scope`)
- Files: `ScopeGridRenderer.java`, `ScopeWaveformRenderer.java`, `ScopeAxisRenderer.java`, `ScopeOverlayRenderer.java`, `Scope.java`
- Remove direct draw helpers from `Scope` where possible.
- Gate: render order preserved; headless/perf smoke still pass.

7. `scope: move interaction/hit-testing fully to ScopeInteractionController` ✅ Done
- Files: `ScopeInteractionController.java`, `Scope.java`, `ScopeInteractionControllerTest.java`
- Keep `Scope` as delegator only for mouse coordinate mapping and selection updates.
- Gate: interaction tests pass.

8. `scope: isolate menu command handling and state transitions` ✅ Done
- Files: `ScopeMenuController.java`, `ScopeLifecycleController.java`, `Scope.java`
- Minimize `handleMenu` branches in `Scope`.
- Gate: menu actions behave the same in manual test circuits.

9. `scope: prepare package move by removing package-private coupling` ✅ Done
- Files: all `Scope*` classes touched as needed
- Replace package-private field reach-through with explicit methods/facades needed for cross-package move.
- Gate: no direct package-private dependence from non-scope classes.
- Additional pre-move gate: all extracted services/controllers operate via explicit API/DTO boundaries; avoid direct mutable field reach-through.

10. `scope: move scope subsystem to client/scope package` ✅ Done
- Move `Scope.java`, `ScopePlot.java`, `ScopeModel.java`, renderers/controllers/scaler/layout/context/config/persistence/export classes to `src/com/lushprojects/circuitjs1/client/scope`.
- Update imports in `CirSim` and dependent dialogs/controllers.
- Gate: full compile and scope test suite pass.

11. `scope: cleanup + docs after move` ✅ Done
- Files: `Scope-Architecture.md`, `SOURCE_FILE_MAP.md`, plus dead code removals
- Remove temporary adapters and compatibility shims introduced during migration.
- Gate: `Scope.java` is coordinator-focused and materially smaller.

## Remaining Execution Order
All planned steps in this checklist are complete.
