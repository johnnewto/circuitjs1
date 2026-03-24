# PR8 Dialog Placement Decisions

Date: 2026-03-25  
Scope: Workstream 5, Steps 1-3 ("Inventory Dialogs", "Decide Move vs Keep", "Execute Minimal-Risk Changes")

## Source inventory command

- `rg --files  /client/ui | rg "Dialog\\.java$"`

## Dialog classification

Legend:
- `Global UI`: keep in `client.ui` unless later risk analysis says otherwise.
- `Element-domain editor`: candidate move to the owning domain package in Step 2.

| dialog | current file | classification | owning domain (if candidate move) | rationale |
|---|---|---|---|---|
| `Dialog` | ` /client/ui/Dialog.java` | `Global UI` | N/A | Base dialog infrastructure shared across UI flows. |
| `EditDialog` | ` /client/ui/EditDialog.java` | `Global UI` | N/A | Generic edit shell used broadly across many element types. |
| `EditCompositeModelDialog` | ` /client/ui/EditCompositeModelDialog.java` | `Element-domain editor` | `client.elements.electronics.misc` (`CustomComposite*`) | Strongly coupled to custom composite element/model APIs. |
| `EditDiodeModelDialog` | ` /client/elements/electronics/semiconductors/EditDiodeModelDialog.java` | `Element-domain editor` | `client.elements.electronics.semiconductors` | Dedicated diode model editor with direct `DiodeElm` coupling. |
| `EditTransistorModelDialog` | ` /client/elements/electronics/semiconductors/EditTransistorModelDialog.java` | `Element-domain editor` | `client.elements.electronics.semiconductors` | Dedicated transistor model editor with direct `TransistorElm` coupling. |
| `ExportAsImageDialog` | ` /client/ui/ExportAsImageDialog.java` | `Global UI` | N/A | Cross-cutting export UI, not owned by one element domain. |
| `ExportAsLocalFileDialog` | ` /client/ui/ExportAsLocalFileDialog.java` | `Global UI` | N/A | Generic local file export view. |
| `ExportAsSFCRDialog` | ` /client/ui/ExportAsSFCRDialog.java` | `Global UI` | N/A | App-level export action spanning circuit/model state. |
| `ExportAsTextDialog` | ` /client/ui/ExportAsTextDialog.java` | `Global UI` | N/A | Generic textual export UI. |
| `ExportAsUrlDialog` | ` /client/ui/ExportAsUrlDialog.java` | `Global UI` | N/A | Generic URL export/share UI. |
| `ExportScopeDataDialog` | ` /client/ui/ExportScopeDataDialog.java` | `Element-domain editor` | `client` (`Scope` domain) | Scope-specific export workflow coupled to `Scope`. |
| `HintEditorDialog` | ` /client/ui/HintEditorDialog.java` | `Element-domain editor` | `client.elements.economics` / `client.registry` | Coupled to economics hint definitions and hint registry. |
| `IframeViewerDialog` | ` /client/ui/IframeViewerDialog.java` | `Global UI` | N/A | Generic viewer shell not bound to a single element domain. |
| `ImportFromDropboxDialog` | ` /client/ui/ImportFromDropboxDialog.java` | `Global UI` | N/A | App-level import UI over storage integration. |
| `ImportFromTextDialog` | ` /client/ui/ImportFromTextDialog.java` | `Global UI` | N/A | App-level import UI over circuit text. |
| `InfoViewerDialog` | ` /client/ui/InfoViewerDialog.java` | `Element-domain editor` | `client.elements.economics` | Heavy coupling to economics/table info serializers and table element types. |
| `LookupTablesEditorDialog` | ` /client/ui/LookupTablesEditorDialog.java` | `Element-domain editor` | `client.io` + economics table domain | Primarily lookup-table/domain editing with SFCR parsing/export coupling. |
| `MathElementsTestDialog` | ` /client/ui/MathElementsTestDialog.java` | `Element-domain editor` | `client.elements.math` | Dedicated math-element testing and diagnostics UI. |
| `PieChartDialog` | ` /client/elements/misc/PieChartDialog.java` | `Element-domain editor` | `client.elements.misc` / economics integration | Dedicated editor for `PieChartElm` with economics/labeled-node coupling. |
| `ScopePropertiesDialog` | ` /client/ScopePropertiesDialog.java` | `Element-domain editor` | `client` (`Scope` domain) | Scope-specific properties editor coupled to `Scope` internals. |
| `ScopeViewerDialog` | ` /client/ui/ScopeViewerDialog.java` | `Element-domain editor` | `client.elements.misc` (`ScopeElm`) + `Scope` domain | Viewer specialized for scope rendering and scope element integration. |
| `SearchDialog` | ` /client/ui/SearchDialog.java` | `Global UI` | N/A | Global app search and command/navigation UI. |
| `ShortcutsDialog` | ` /client/ui/ShortcutsDialog.java` | `Global UI` | N/A | Global keyboard-shortcuts configuration UI. |
| `SliderDialog` | ` /client/ui/SliderDialog.java` | `Global UI` | N/A | Shared adjustable-value UI used across multiple element types. |
| `SubcircuitDialog` | ` /client/ui/SubcircuitDialog.java` | `Element-domain editor` | `client` + custom composite domain | Focused on subcircuit/custom-composite lifecycle and naming. |
| `TableElementsTestDialog` | ` /client/ui/TableElementsTestDialog.java` | `Element-domain editor` | `client.elements.economics` | Dedicated economics/table-element testing and diagnostics UI. |
| `VariableBrowserDialog` | ` /client/ui/VariableBrowserDialog.java` | `Element-domain editor` | `client.elements.economics` + wiring vars | Variable/node browser tied to economics tables and labeled nodes. |

## Step 2 decisions (`move` vs `keep`)

Criteria applied:
- Move only when all are true: low churn/low coupling, no bridge/shim required, single clear owner package.
- Keep in `client.ui` when shared UI behavior or integration risk dominates.

| dialog | step 2 decision | criteria result | rationale |
|---|---|---|---|
| `EditCompositeModelDialog` | `keep (PR8)` | `fail` (coupling/risk) | Large dialog with broad UI and custom-composite integration; not a low-risk first move. |
| `EditDiodeModelDialog` | `moved (PR8 batch 1)` | `pass` | Small, low-churn, single-owner semiconductor editor with direct `DiodeElm` ownership. |
| `EditTransistorModelDialog` | `moved (PR8 batch 1)` | `pass` | Small, low-churn, single-owner semiconductor editor with direct `TransistorElm` ownership. |
| `ExportScopeDataDialog` | `keep (PR8)` | `fail` (shared UI coupling) | Scope-specific data source but coupled to shared export dialogs (`ExportAsLocalFileDialog`, `ExportAsTextDialog`) in `client.ui`. |
| `HintEditorDialog` | `keep (PR8)` | `fail` (churn/coupling) | Mixed economics + registry + command routing responsibilities; not a low-coupling move. |
| `InfoViewerDialog` | `keep (PR8)` | `fail` (high churn/coupling) | Highest coupling across renderer, parser, economics/table serializers, and multiple call sites. |
| `LookupTablesEditorDialog` | `keep (PR8)` | `fail` (cross-layer coupling) | Blends UI/editor flow with `io`/SFCR parsing and tests; ownership not cleanly single-package. |
| `MathElementsTestDialog` | `keep (PR8)` | `fail` (shared diagnostics surface) | Large diagnostics/test harness dialog; not a domain-owner move target for this PR. |
| `PieChartDialog` | `moved (PR8 batch 2, user-requested)` | `override` (initially fail risk bar) | Moved on explicit request; compile/test gates passed for the move. |
| `ScopePropertiesDialog` | `moved (PR8 batch 1)` | `pass` | Thin wrapper over `ScopePropertiesDialogCore`, clear scope ownership, no bridge needed. |
| `ScopeViewerDialog` | `keep (PR8)` | `fail` (coupling) | Interacts with `CirSim`, command routing, and `ScopeElm`; shared behavior dominates. |
| `SubcircuitDialog` | `keep (PR8)` | `fail` (shared workflow) | Uses global subcircuit management flow from command routing; keep with shared UI surface. |
| `TableElementsTestDialog` | `keep (PR8)` | `fail` (shared diagnostics surface) | Large diagnostics/test dialog spanning multiple table behaviors. |
| `VariableBrowserDialog` | `keep (PR8)` | `fail` (high churn/coupling) | High churn and cross-coupling with analyzer, coordinator, and command routing. |

### Approved move set for Step 3 (batch execution)

Batch 1:
- `EditDiodeModelDialog` ✅ moved
- `EditTransistorModelDialog` ✅ moved
- `ScopePropertiesDialog` ✅ moved

Batch 2 (user-requested trial):
- `PieChartDialog` ✅ moved

## Step 3 execution status

Gates run after the batch:
- `./gradlew compileJava` ✅
- `./gradlew test` ✅
- `./gradlew compileGwtDev` ✅

Additional gates for batch 2 (`PieChartDialog` move):
- `./gradlew compileJava` ✅
- `./gradlew test` ✅

Manual smoke:
- Pending (not yet recorded in this pass).

## Totals

- Total dialogs inventoried: `27`
- Classified `Global UI`: `13`
- Classified `Element-domain editor`: `14`
- Step 2 `moved` dialogs in PR8: `4`
- Step 2 `keep in client.ui` (for PR8): `10`
