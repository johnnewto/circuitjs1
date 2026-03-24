# Client Package Reorganization Plan

This document outlines candidates for moving files from the flat `client/` package into organized subpackages.

## Current Subpackages

| Package | Purpose | Files |
|---------|---------|-------|
| `core/` | Simulation engine internals | `CircuitMatrixOps`, `LUSolver`, `MatrixStamper`, `SimulationContext`, etc. |
| `elements/` | Circuit element implementations | Organized by type: `math/`, `economics/`, `electronics/`, `annotation/`, `misc/` |
| `io/` | File parsing, import/export | `SFCRParser`, `SFCRExporter`, `LookupTableRegistry`, etc. |
| `registry/` | Element registry system | `ElementRegistry`, `ElementFactoryFacade`, `ElementCategory` |
| `ui/` | Dialog and UI components | `MathElementsTestDialog`, `InfoViewerDialog`, toolbars, etc. |
| `util/` | Utility classes | `Locale`, `PerfMonitor` |

## Reorganization Candidates

### Move to `core/`

Simulation engine internals (matrix operations, analysis).

| File | Rationale |
|------|-----------|
| - [ ] `CircuitNode.java` | Node representation |
| - [ ] `CircuitNodeLink.java` | Node connection data |
| - [ ] `RowInfo.java` | Matrix row information |

### Move to `elements/electronics/`

Device models used by electronics elements. Keep models close to the elements that use them.

| File | Rationale |
|------|-----------|
| - [ ] `Inductor.java` | Inductor device model (used by `InductorElm`) |
| - [ ] `Diode.java` | Diode device model (used by `DiodeElm`, `LEDElm`, etc.) |
| - [ ] `DiodeModel.java` | Diode model parameters |
| - [ ] `TransistorModel.java` | Transistor model parameters |

### Move to `ui/`

Dialog and UI components.

| File | Rationale |
|------|-----------|
| - [x] `Dialog.java` | Base dialog class |
| - [x] `EditDialog.java` | Element edit dialog |
| - [x] `EditDialogActions.java` | Edit dialog actions |
| - [x] `EditDialogLoadFile.java` | Edit dialog file loading |
| - [x] `EditInfo.java` | Edit field information |
| - [x] `EditOptions.java` | Options dialog |
| - [x] `EditCompositeModelDialog.java` | Composite model editor |
| - [x] `EditDiodeModelDialog.java` | Diode model editor |
| - [x] `EditTransistorModelDialog.java` | Transistor model editor |
| - [x] `ScopePropertiesDialog.java` | Scope properties |
| - [x] `SliderDialog.java` | Slider configuration |
| - [x] `Checkbox.java` | Custom checkbox widget |
| - [x] `Choice.java` | Custom dropdown widget |
| - [x] `Scrollbar.java` | Scrollbar widget |
| - [x] `ScrollValuePopup.java` | Value adjustment popup |
| - [x] `FloatingControlPanel.java` | Floating control UI |
| - [x] `MenuUiState.java` | Menu state management |

### Move to `io/`

File handling, import/export, clipboard.

| File | Rationale |
|------|-----------|
| - [ ] `LoadFile.java` | Circuit file loading |
| - [ ] `SRAMLoadFile.java` | SRAM data loading |
| - [ ] `ImportFromDropbox.java` | Dropbox import logic |
| - [ ] `ClipboardManager.java` | Clipboard operations |
| - [ ] `SetupListLoader.java` | Setup list loading |
| - [ ] `SimulationExportCore.java` | Export functionality |
| - [ ] `CircuitIOService.java` | Circuit I/O abstraction |
| - [ ] `SFCRDocumentManager.java` | SFCR document state management |
| - [ ] `SFCRDocumentState.java` | SFCR document state (pure state class) |

### Move to `util/`

Utilities and primitive types.

| File | Rationale |
|------|-----------|
| - [x] `Color.java` | Color representation |
| - [x] `Font.java` | Font abstraction |
| - [x] `Point.java` | 2D point |
| - [x] `Polygon.java` | Polygon shape |
| - [x] `Rectangle.java` | Rectangle shape |
| - [x] `Graphics.java` | Graphics context wrapper |
| - [x] `FFT.java` | Fast Fourier Transform |
| - [x] `NumFmt.java` | Number formatting |
| - [x] `IntPair.java` | Integer pair utility |
| - [x] `StringTokenizer.java` | String tokenization |
| - [x] `QueryParameters.java` | URL query parsing |
| - [x] `AutocompleteHelper.java` | Autocomplete utilities |

### Move to `registry/`

Registry and factory classes.

| File | Rationale |
|------|-----------|
| - [ ] `ElementLegacyFactory.java` | Legacy element creation |
| - [ ] `ElementRegistryBootstrap.java` | Registry initialization |
| - [ ] `HintRegistry.java` | Hint message registry |
| - [ ] `TableMasterRegistryManager.java` | Table registry management |

### New Package: `test/`

In-browser GWT test infrastructure.

| File | Rationale |
|------|-----------|
| - [ ] `MathElementsTest.java` | Math element tests |
| - [ ] `TableElementsTest.java` | Table element tests |
| - [ ] `CircuitTestRunner.java` | Test runner infrastructure |

### New Package: `runner/`

Headless/automation runner subsystem.

| File | Rationale |
|------|-----------|
| - [ ] `RunnerController.java` | Runner orchestration |
| - [ ] `RunnerJsBridge.java` | JavaScript bridge |
| - [ ] `RunnerLaunchDecision.java` | Launch mode detection |
| - [ ] `RunnerPanelUi.java` | Runner panel UI |
| - [ ] `CircuitJavaRunner.java` | Java runner interface |
| - [ ] `RuntimeMode.java` | Runtime mode enum |

### Move to `elements/economics/`

Stock-flow consistent modeling support.

| File | Rationale |
|------|-----------|
| - [ ] `StockFlowTableSemantics.java` | SFC table logic |
| - [ ] `StockTableView.java` | Stock table visualization |
| - [ ] `InfoViewerTableMarkdown.java` | Table markdown generation |
| - [ ] `InfoViewerLiveDataSerializer.java` | Live data serialization |

## Files to Keep in `client/`

These are core entry points and base classes that should remain at the top level.

| File | Rationale |
|------|-----------|
| - [ ] `CirSim.java` | Main simulator class |
| - [ ] `CirSimBootstrap.java` | Bootstrap initialization |
| - [ ] `CirSimCommandRouter.java` | Command routing |
| - [ ] `CirSimDiagnostics.java` | Diagnostics |
| - [ ] `CirSimDialogCoordinator.java` | Dialog coordination |
| - [ ] `CirSimInitializer.java` | Initialization |
| - [ ] `CirSimMenuBuilder.java` | Menu construction |
| - [ ] `CirSimPlatformInterop.java` | Platform interop |
| - [ ] `CirSimPreferencesManager.java` | Preferences |
| - [ ] `CirSimUiPanelManager.java` | UI panel management |
| - [ ] `SimulationLoop.java` | Simulation orchestration (tightly coupled to CirSim) |
| - [ ] `CircuitAnalyzer.java` | Analysis orchestration (imports many element types, uses CirSim inner classes) |
| - [ ] `circuitjs1.java` | GWT entry point |
| - [ ] `CircuitElm.java` | Base element class |
| - [ ] `CompositeElm.java` | Composite element base |
| - [ ] `CircuitRenderer.java` | Main renderer |
| - [ ] `Scope.java` | Oscilloscope |
| - [ ] `ScopeManager.java` | Scope management |
| - [ ] `Editable.java` | Editable interface |
| - [ ] `Adjustable.java` | Adjustable interface |
| - [ ] `CustomCompositeModel.java` | Composite models |
| - [ ] `CustomLogicModel.java` | Logic models |
| - [ ] `ExportCompositeActions.java` | Composite export |
| - [ ] `JsApiBridge.java` | JavaScript API |
| - [ ] `CircuitValueSlotManager.java` | Value slots |
| - [ ] `FlipTransformController.java` | Transform handling |
| - [ ] `MouseInputHandler.java` | Mouse input |
| - [ ] `StatusInfoRenderer.java` | Status display |
| - [ ] `ToolbarModeManager.java` | Toolbar modes |
| - [ ] `UndoRedoManager.java` | Undo/redo |
| - [ ] `ViewportController.java` | Viewport control |
| - [ ] `MyCommand.java` | Command interface |
| - [ ] `SimulationContextAdapter.java` | Context adapter |
| - [ ] `CheckboxAlignedMenuItem.java` | Menu item widget |

## Migration Strategy

1. **Phase 1: Low-risk moves** - Start with utility classes (`util/`) that have minimal dependencies
2. **Phase 2: Test infrastructure** - Move test files to `test/` package
3. **Phase 3: UI components** - Move dialog classes to `ui/`
4. **Phase 4: I/O classes** - Move file handling to `io/`
5. **Phase 5: Core internals** - Move simulation internals to `core/`
6. **Phase 6: Runner subsystem** - Create `runner/` package
7. **Phase 7: Stock-flow** - Organize SFC support classes

### For Each Move

1. Update the package declaration in the moved file
2. Update all import statements in files that reference the moved class
3. Run Gates Per Batch
- [ ] `./gradlew compileJava`
- [ ] `./gradlew test`
- [ ] `./gradlew compileGwtDev`
4. Update documentation references (e.g., `ELEMENTS_FILE_MAP.md`)

## Dependencies to Check

Before moving any file, check:
- Imports from other `client/` files
- Which files import the candidate file
- GWT module references (if any)
- Documentation references

Use: `grep -r "import.*ClassName" src/`
