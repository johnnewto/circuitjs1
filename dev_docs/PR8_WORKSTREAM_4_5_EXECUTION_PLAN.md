# PR8 Workstream 4-5 Execution Plan

## Purpose
Close the remaining PR8 finalization work:
- Workstream 4: visibility tightening pass
- Workstream 5: dialog placement reconciliation

This plan is behavior-neutral and intentionally small-scope.

## Preconditions
- Category A/B shim cleanup is complete.
- Baseline gates are green:
  - `./gradlew compileJava`
  - `./gradlew test`
  - `./gradlew compileGwtDev`

## Workstream 4: Visibility Tightening Pass

### 1. Build Candidate Inventory
- [x] Generate public API inventory in `client`:
  - `rg -n "public (static )?.*\\(" src/com/lushprojects/circuitjs1/client`
- [x] Capture candidates recently expanded during migration:
  - `git log --name-only --oneline -n 30`
- [x] Create `dev_docs/PR8_VISIBILITY_AUDIT.md` with one row per candidate:
  - `symbol`, `file`, `current visibility`, `external callers`, `keep/downgrade`, `reason`

### 2. Apply Visibility Decisions
- [ ] Downgrade `public` to minimum required (`protected`/package-private/`private`) when no cross-package dependency exists.
- [ ] Keep `public` only if one of:
  - Cross-package production usage exists.
  - Required by JS/public app API.
  - Required by tests in another package.
- [ ] Validate known candidate area first: `Scope` helper accessors used for info serialization.

### 3. Verify
- [ ] Run gates:
  - `./gradlew compileJava`
  - `./gradlew test`
  - `./gradlew compileGwtDev`
- [ ] Spot-check impacted flows (import/export + key dialogs touched by changed symbols).

Exit criteria:
- `PR8_VISIBILITY_AUDIT.md` completed.
- All downgraded symbols compile and tests pass.

## Workstream 5: Dialog Placement Reconciliation

### 1. Inventory Dialogs in `client.ui`
- [x] List dialog classes:
  - `rg --files src/com/lushprojects/circuitjs1/client/ui | rg "Dialog\\.java$"`
- [x] For each dialog, classify:
  - `Global UI` (keep in `client.ui`)
  - `Element-domain editor` (candidate move to owning domain package)
- [x] Record decisions in `dev_docs/PR8_DIALOG_PLACEMENT_DECISIONS.md`.

### 2. Decide Move vs Keep
- [x] Move only if all are true:
  - Low churn / low coupling.
  - No new bridge/shim methods required.
  - Ownership clearly belongs to one domain package.
- [x] Keep in `client.ui` when high-risk or shared UI behavior dominates.
- [x] For each "keep", document explicit rationale.

Decision/execution cadence for candidate moves:
- [x] Execute in small batches of `1-3` dialogs at a time (do not queue all 14 at once).
- [x] After each batch, run full gates:
  - `./gradlew compileJava`
  - `./gradlew test`
  - `./gradlew compileGwtDev`
- [ ] After each batch, run focused smoke tests for only the dialogs moved in that batch.

Candidate dialog decision checklist (from `PR8_DIALOG_PLACEMENT_DECISIONS.md`):
- [x] `EditCompositeModelDialog` -> `keep (PR8)`
- [x] `EditDiodeModelDialog` -> `moved (PR8 batch 1)`
- [x] `EditTransistorModelDialog` -> `moved (PR8 batch 1)`
- [x] `ExportScopeDataDialog` -> `keep (PR8)`
- [x] `HintEditorDialog` -> `keep (PR8)`
- [x] `InfoViewerDialog` -> `keep (PR8)`
- [x] `LookupTablesEditorDialog` -> `keep (PR8)`
- [x] `MathElementsTestDialog` -> `keep (PR8)`
- [x] `PieChartDialog` -> `moved (PR8 batch 2, user-requested)`
- [x] `ScopePropertiesDialog` -> `moved (PR8 batch 1)`
- [x] `ScopeViewerDialog` -> `keep (PR8)`
- [x] `SubcircuitDialog` -> `keep (PR8)`
- [x] `TableElementsTestDialog` -> `keep (PR8)`
- [x] `VariableBrowserDialog` -> `keep (PR8)`

### 3. Execute Minimal-Risk Changes
- [x] Apply only low-risk moves (if any), one small batch at a time.
- [x] If a candidate requires new temporary bridges, do not move in PR8.
- [x] Update package/imports and keep behavior identical.

### 4. Verify
- [x] Run gates:
  - `./gradlew compileJava`
  - `./gradlew test`
  - `./gradlew compileGwtDev`
- [ ] Manual smoke for moved/retained dialogs as applicable.

Exit criteria:
- Every `client.ui` dialog has a documented classification and rationale.
- Any completed moves are low-risk and gate-clean.

## Documentation Updates
- [x] Update checkboxes in this plan at each completed step/sub-step.
- [ ] Update [PACKAGE_MIGRATION_PLAN.md](./PACKAGE_MIGRATION_PLAN.md) with final visibility/placement outcomes.
- [ ] Update [CLIENT_PACKAGE_REORGANIZATION.md](./CLIENT_PACKAGE_REORGANIZATION.md) to match final ownership.
- [ ] Update [ELEMENTS_FILE_MAP.md](./ELEMENTS_FILE_MAP.md) if any dialog ownership changed.

## Suggested PR Split
1. `PR8d1`: Visibility tightening + `PR8_VISIBILITY_AUDIT.md`
2. `PR8d2`: Dialog placement decisions/moves + doc reconciliation
