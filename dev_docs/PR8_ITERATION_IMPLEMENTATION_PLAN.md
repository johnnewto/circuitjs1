# PR8 Iteration Implementation Plan

This document is the execution checklist extracted from:
- [PR8_FINALIZATION_AND_SHIM_CLEANUP_PLAN.md](./PR8_FINALIZATION_AND_SHIM_CLEANUP_PLAN.md)

## Iteration Plan

### Per-Wrapper Migration Loop
For each wrapper method:
1. Find callers: `rg "methodName" src/`
2. Group work into a logical mini-batch (shared caller area), not by fixed count.
3. Migrate callers to use target service/manager directly (or inline the call).
4. Run `./gradlew compileJava` and fix errors immediately.
5. Mark wrapper as done in checklist below.

### Mini-Batch Checkpoint (risk-based, not count-based)
Run a checkpoint when either condition is true:
1. Mini-batch boundary reached (for example Export, Dropbox/Import, VariableBrowser).
2. A high-risk wrapper is touched (import/export, selection/mutation, geometry/placement).

Checkpoint steps:
1. `./gradlew test`
2. `./gradlew compileGwtDev`
3. Manual smoke test (only flows impacted by the mini-batch):
   - Open/import circuit text (if import wrappers touched)
   - Export image/text/SFCR (if export wrappers touched)
   - Variable browser insert (if placement wrappers touched)
   - Search/shortcuts dialogs (if menu/shortcut wrappers touched)
4. Commit checkpoint.

### Rollback Rule
If any mini-batch causes broad instability:
1. Revert only that mini-batch.
2. Split into smaller scope (single dialog/service area).
3. Re-run gates before continuing.

---

## Category A Progress Checklist

### Batch A1: Export dialog wrappers
- [x] `getCircuitAsCanvasForExport` (caller: `ExportAsImageDialog`)
- [x] `getCircuitAsSvgForExport` (caller: `ExportAsImageDialog`)
- [x] `getScopesAsCanvasForExport` (caller: `ExportAsImageDialog`)
- [x] `getCacImageType` (caller: `ExportAsImageDialog`)
- [x] `repaintFromUi` (caller: `CheckboxMenuItem`)

### Batch A2: Dropbox/import wrappers
- [ ] `loadCircuitFromExternalText` (caller: `ImportFromDropboxDialog`)
- [ ] `openDropboxChooserFromDialog` (caller: `ImportFromDropboxDialog`)

### Batch A3: Viewer/subcircuit/pie wrappers
- [ ] `getFloatingScopeCountForViewer` (caller: `ScopeViewerDialog`)
- [ ] `getFloatingScopeForViewer` (caller: `ScopeViewerDialog`)
- [ ] `getUserSubcircuitNames` (caller: `SubcircuitDialog`)
- [ ] `removeSubcircuitByName` (caller: `SubcircuitDialog`)
- [ ] `getAllLabeledNodeNamesForPieChart` (caller: `PieChartDialog`)
- [ ] `requestAnalyzeFromDialog` (caller: `PieChartDialog`)
- [ ] `executeMainMenuItemByName` (caller: `SearchDialog`)

### Batch A4: Coordinate/placement wrappers
- [ ] `getCircuitAreaHeight` (caller: `VariableBrowserDialog`)
- [ ] `inverseTransformXForUi` (caller: `VariableBrowserDialog`)
- [ ] `inverseTransformYForUi` (caller: `VariableBrowserDialog`)
- [ ] `createLabeledNodeElementForUi` (caller: `VariableBrowserDialog`)
- [ ] `addElementForUi` (caller: `VariableBrowserDialog`)
- [ ] `needAnalyzeForUi` (caller: `VariableBrowserDialog`)
- [ ] `selectElementForUi` (caller: `VariableBrowserDialog`)

---

## Category B Progress Checklist

### Consolidation (do first)
- [ ] Merge `alertOrWarnFromDialog` + `alertOrWarnForUi` → `alertOrWarn()`

### Import/export text wrappers
- [ ] `reimportCircuitTextFromDialog` (callers: `ExportAsSFCRDialog`, `ExportAsTextDialog`)
- [ ] `importCircuitTextFromDialog` (callers: `ImportFromTextDialog`, `LookupTablesEditorDialog`)
- [ ] `dumpCircuitForUi` (callers: 3 dialogs)

### Search/shortcuts wrappers
- [ ] `getSearchableMainMenuItemNames` (caller: `SearchDialog`)
- [ ] `getShortcutMenuItemCount` (caller: `ShortcutsDialog`)
- [ ] `getShortcutMenuItemName` (caller: `ShortcutsDialog`)
- [ ] `getShortcutMenuItemValue` (caller: `ShortcutsDialog`)
- [ ] `applyShortcutMenuItemValues` (caller: `ShortcutsDialog`)

### UI mutation wrappers
- [ ] `clearSelectionForUi` (callers: `EditDialogActions`, `VariableBrowserDialog`)
- [ ] `repaintForUi` (callers: `Scrollbar`, `VariableBrowserDialog`)
- [ ] `snapGridForUi` (caller: `VariableBrowserDialog`)
- [ ] `findCanvasTestLabelForUi` (callers: `MathElementsTestDialog`, `TableElementsTestDialog`)
