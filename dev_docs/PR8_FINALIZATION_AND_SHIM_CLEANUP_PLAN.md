# PR8 Finalization and Shim Cleanup Plan

## Purpose
Finalize package migration work by removing or reducing temporary `CirSim` bridge/shim methods, stabilizing service boundaries, and tightening temporary visibility expansions where safe.

This plan assumes the package move phases in:
- [CLIENT_PACKAGE_REORGANIZATION.md](./CLIENT_PACKAGE_REORGANIZATION.md)
- [PACKAGE_MIGRATION_PLAN.md](./PACKAGE_MIGRATION_PLAN.md)
are complete except PR8 finalization.

## Scope
In scope:
- Remove temporary PR4 bridge methods that are dialog-specific wrappers.
- Replace shared bridge methods with stable service interfaces.
- Keep only intentional public API surface in `CirSim`.
- Tighten temporary visibility expansions where no longer required.
- Reconcile remaining temporary dialog placement decisions.

Out of scope:
- Large new package moves.
- Behavior changes to simulation, serialization, or SFCR semantics.
- Refactors unrelated to bridge/interface cleanup.

## Current State Snapshot
From current codebase inventory:
- PR8 core batch status:
  - `LUSolver`, `SolverMatrixState`, `SimulationTimingState`, `MatrixStamper`, `CircuitMatrixOps` are already in `client/core`.
  - `CircuitAnalyzer` and `SimulationLoop` remain in `client` (acceptable per plan).
- PR4 bridge inventory methods still exist in `CirSim` and are actively used.
- Several `*ForUi` and `*FromDialog` methods are single-caller wrappers and are good removal candidates.
- **Duplicate methods exist**: `alertOrWarnFromDialog` and `alertOrWarnForUi` do the same thing — consolidate before removal.
- **Note**: `getInstance()` has 100+ callers and is NOT a candidate for this cleanup cycle.

## Decision Framework
Use these rules for each bridge method:
1. Single-caller and dialog-specific wrapper:
   - Inline call to target service/manager and remove method.
2. Multi-caller UI operation:
   - Move behind stable service API (`ui` or `io` service), then remove bridge.
3. Core runtime method used by elements:
   - Keep as stable API; rename only if value is clear and low-risk.
4. JS/public app API:
   - Keep intentionally and document as public surface.

## Method Disposition (Initial)

### Category A: Remove in PR8 (single-purpose wrappers)
Target removal after caller migration:
- `getCircuitAsCanvasForExport`
- `getCircuitAsSvgForExport`
- `getScopesAsCanvasForExport`
- `getCacImageType`
- `repaintFromUi`
- `loadCircuitFromExternalText`
- `openDropboxChooserFromDialog`
- `executeMainMenuItemByName`
- `getUserSubcircuitNames`
- `removeSubcircuitByName`
- `getFloatingScopeCountForViewer`
- `getFloatingScopeForViewer`
- `getAllLabeledNodeNamesForPieChart`
- `requestAnalyzeFromDialog`
- `getCircuitAreaHeight`
- `inverseTransformXForUi`
- `inverseTransformYForUi`
- `createLabeledNodeElementForUi`
- `addElementForUi`
- `needAnalyzeForUi`
- `selectElementForUi`

### Category B: Convert to stable service API, then remove bridge names
Migrate callers to new services, then remove old names:
- Import/export text wrappers:
  - `reimportCircuitTextFromDialog`
  - `importCircuitTextFromDialog`
  - `dumpCircuitForUi` (3 callers)
- Alert methods (consolidate first, then migrate):
  - `alertOrWarnFromDialog` → consolidate with `alertOrWarnForUi` → single `alertOrWarn()`
  - `alertOrWarnForUi`
- Search/shortcuts wrappers:
  - `getSearchableMainMenuItemNames`
  - `getShortcutMenuItemCount`
  - `getShortcutMenuItemName`
  - `getShortcutMenuItemValue`
  - `applyShortcutMenuItemValues`
- UI mutation wrappers:
  - `clearSelectionForUi`
  - `repaintForUi`
  - `snapGridForUi`
  - `findCanvasTestLabelForUi`

### Category C: Keep as stable API (not temporary shim)
Keep for now, document intent:
- `getGridSize` (element geometry dependency)
- `isDcAnalysisForUi` (element simulation mode checks)
- `isEuroResistorForUi`, `isEuroGatesForUi` (element rendering behavior)
- `setCursorStyleForUi`, `isAddElementModeForUi` (interactive element UX)
- `getLabeledNodeVoltageForUi`, `resolveSlotValueForUi`, `formatAdjustableValueForUi`
- `getInstance` (global singleton access, separate long-term cleanup)
- `isCacheBustedUrlsEnabled` and related options getters/setters

## Workstreams

## Workstream 1: Baseline and Safety Harness
1. Create a baseline branch/tag for PR8 finalization.
2. Run baseline gates:
   - `./gradlew compileJava`
   - `./gradlew test`
   - `./gradlew compileGwtDev`
3. Add/refresh targeted tests:
   - Import from text/dropbox paths.
   - Export image/text/SFCR paths.
   - Search dialog and shortcuts dialog.
   - Variable browser insertion flow.
4. Capture before/after API inventory:
   - `rg -n "public .*ForUi|public .*FromDialog|public .*ForViewer|public static void repaintFromUi" src/com/lushprojects/circuitjs1/client/CirSim.java`

Exit criteria:
- Baseline green and targeted tests present.

## Workstream 2: Remove Category A wrappers in small batches
Batch A1: Export dialog wrappers
- Migrate `ExportAsImageDialog` to stable APIs or services.
- Remove: `getCircuitAsCanvasForExport`, `getCircuitAsSvgForExport`, `getScopesAsCanvasForExport`, `getCacImageType`.

Batch A2: Dropbox/import wrappers
- Migrate `ImportFromDropboxDialog` and text import callers to `CircuitIOService`/UI service.
- Remove: `loadCircuitFromExternalText`, `openDropboxChooserFromDialog`.

Batch A3: Viewer/subcircuit/pie wrappers
- Migrate `ScopeViewerDialog`, `SubcircuitDialog`, `PieChartDialog`.
- Remove: `getFloatingScopeCountForViewer`, `getFloatingScopeForViewer`, `getUserSubcircuitNames`, `removeSubcircuitByName`, `getAllLabeledNodeNamesForPieChart`, `requestAnalyzeFromDialog`.

Batch A4: Coordinate/placement wrappers
- Migrate `VariableBrowserDialog` to service API.
- Remove: `getCircuitAreaHeight`, `inverseTransformXForUi`, `inverseTransformYForUi`, `createLabeledNodeElementForUi`, `addElementForUi`, `needAnalyzeForUi`, `selectElementForUi`.

Gate after each batch:
- `./gradlew compileJava`
- `./gradlew test`
- `./gradlew compileGwtDev`

## Workstream 3: Service extraction for Category B
Introduce focused interfaces/services (minimize proliferation):
1. `CircuitIOService` (expand existing)
   - import/reimport text, dump circuit, user-facing warnings/alerts.
   - Already exists at `client/CircuitIOService.java` — extend rather than create new.
2. `UiInteractionService`
   - searchable menu names, execute menu command, shortcut table read/write.
   - clear/select/repaint/analyze/snap-grid operations.
3. `UiTestService`
   - test label probing methods currently used by test dialogs.

**Consolidation step before service migration:**
- Merge `alertOrWarnFromDialog` + `alertOrWarnForUi` → single `alertOrWarn()` method.
- Update all 3 callers, then proceed with service migration.

Migration pattern:
1. Add service with implementation delegating to existing internals.
2. Migrate callers from `CirSim` bridge methods to service methods.
3. Remove old bridge method.
4. Gate each service migration batch.

## Workstream 4: Visibility tightening pass
After shim cleanup stabilizes:
1. Inventory recent visibility expansions:
   - `rg -n "public .*\\(" src/com/lushprojects/circuitjs1/client src/com/lushprojects/circuitjs1/client/*`
2. For each expanded symbol:
   - Check external package usage.
   - Downgrade visibility when cross-package access is no longer required.
3. Keep symbols public only if:
   - Used across package boundaries.
   - Required by JS API.
   - Required by tests in separate package.

Specific known candidate to review:
- `Scope` helper accessors added for cross-package info serialization should be validated for minimum required visibility.

## Workstream 5: Reconcile PR4 temporary dialog placements
Audit element-coupled dialogs still under `client.ui`.
1. Classify as global UI versus element-domain editor.
2. For element-domain dialogs:
   - Move to owning domain package only if low-risk and no new bridges are required.
3. For high-risk dialogs:
   - Keep in `ui` and document rationale explicitly in migration docs.

## PR Breakdown Recommendation
1. `PR8a`: Baseline + targeted tests + Category A batch A1 + A2 removals.
   - Ship value early by combining baseline with first removals.
2. `PR8b`: Category A batch A3 + A4 removals.
3. `PR8c`: Alert method consolidation + Category B service migrations and bridge removals.
4. `PR8d`: Visibility tightening + dialog placement reconciliation + docs finalization.

Each PR must stay behavior-neutral and pass full gates.

## Gating and Verification
For every batch:
1. `./gradlew compileJava`
2. `./gradlew test`
3. `./gradlew compileGwtDev`
4. Manual smoke:
   - Open/import circuit text
   - Export image and text
   - Search dialog command execution
   - Shortcuts edit/apply
   - Variable browser insert node flow
5. **Regression check**: Verify no new `*ForUi`, `*FromDialog`, `*ForViewer`, `*ForExport` methods introduced:
   ```bash
   rg -c "ForUi|FromDialog|ForViewer|ForExport" src/com/lushprojects/circuitjs1/client/CirSim.java
   ```
   Count should decrease or stay same, never increase.

## Documentation Updates Required
Update after each merged batch:
- [PACKAGE_MIGRATION_PLAN.md](./PACKAGE_MIGRATION_PLAN.md)
  - Mark removed bridges and final API decisions.
- [CLIENT_PACKAGE_REORGANIZATION.md](./CLIENT_PACKAGE_REORGANIZATION.md)
  - Keep "Files to Keep in client" aligned with actual ownership.
- [ELEMENTS_FILE_MAP.md](./ELEMENTS_FILE_MAP.md)
  - Ensure classifications reflect final locations and package ownership.

## Definition of Done for PR8 Finalization
1. Temporary PR4 bridge methods are removed or replaced with stable services.
2. `CirSim` no longer contains dialog-specific bridge names that only forward.
3. Temporary visibility expansions are reviewed and tightened where safe.
4. All gates pass and smoke flows are stable.
5. Migration docs reflect final state and no stale "temporary shim" tasks remain.

## Implementation Checklist
Execution details, iteration loop, and live progress checklists are maintained in:
- [PR8_ITERATION_IMPLEMENTATION_PLAN.md](./PR8_ITERATION_IMPLEMENTATION_PLAN.md)
