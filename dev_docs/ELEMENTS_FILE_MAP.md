# Client File Map

Inventory for files under src/com/lushprojects/circuitjs1/client.

- Grouped by directory.
- Includes Java and template files.
- Folder connection counts are direct import counts to internal client folders.
- Columns: root core elements io registry ui util.
- move to uses a conservative dependency-first heuristic; Keep is preferred when signal is weak.

## Placement Guide

Use the 7 connection columns and move to as architectural signal (not hard rule):
1. Prefer placing a file near its highest non-trivial dependency cluster.
2. If ui is high but elements is also high, keep UI in ui and extract shared logic behind interfaces.
3. High root counts indicate legacy coupling and likely candidates to move out of root.
4. Review outliers manually before moving: wildcard imports and same-package references can skew counts.

## (root)

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `Adjustable.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `AutocompleteHelper.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `Checkbox.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `CheckboxAlignedMenuItem.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 1 | 0 |
| `Choice.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `CirSim.java` | Client runtime/support class. | Main coordinator; close to CircuitElm SimulationLoop CircuitAnalyzer CircuitRenderer and UI managers. | `core` | 0 | 6 | 6 | 1 | 0 | 6 | 1 |
| `CirSimBootstrap.java` | Client runtime/support class. | Bootstraps CirSim runtime and startup wiring. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `CirSimCommandRouter.java` | Client runtime/support class. | CirSim subsystem helper; closely tied to CirSim. | `ui` | 0 | 0 | 2 | 0 | 0 | 11 | 1 |
| `CirSimDiagnostics.java` | Client runtime/support class. | CirSim subsystem helper; closely tied to CirSim. | `Keep` | 0 | 0 | 0 | 0 | 1 | 0 | 0 |
| `CirSimDialogCoordinator.java` | Client runtime/support class. | CirSim subsystem helper; closely tied to CirSim. | `ui` | 0 | 0 | 0 | 0 | 0 | 2 | 0 |
| `CirSimInitializer.java` | Client runtime/support class. | Bootstraps CirSim runtime and startup wiring. | `ui` | 0 | 0 | 1 | 0 | 0 | 5 | 1 |
| `CirSimMenuBuilder.java` | Client runtime/support class. | CirSim subsystem helper; closely tied to CirSim. | `Keep` | 0 | 0 | 1 | 0 | 1 | 1 | 1 |
| `CirSimPlatformInterop.java` | Bridge/interop integration class. | CirSim subsystem helper; closely tied to CirSim. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CirSimPreferencesManager.java` | Client manager/coordinator class. | CirSim subsystem helper; closely tied to CirSim. | `Keep` | 0 | 0 | 0 | 0 | 0 | 1 | 0 |
| `CirSimUiPanelManager.java` | Client manager/coordinator class. | CirSim subsystem helper; closely tied to CirSim. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CircuitAnalyzer.java` | Client runtime/support class. | Analysis pass over CircuitElm CircuitNode RowInfo and element graph. | `elements` | 0 | 1 | 8 | 0 | 0 | 1 | 0 |
| `CircuitElm.java` | Base/legacy element class in root package. | Base element type; parent/peer relationship to nearly all *Elm classes. | `elements` | 0 | 1 | 5 | 0 | 0 | 0 | 1 |
| `CircuitIOService.java` | Client runtime/support class. | Connects root runtime to io package parser/exporter/lookup services. | `io` | 0 | 0 | 4 | 5 | 1 | 4 | 1 |
| `CircuitJavaRunner.java` | Runner/execution integration class. | Runner subsystem family for headless/automation execution. | `runner` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `CircuitNode.java` | Client runtime/support class. | Core simulation node graph primitive. | `core` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CircuitNodeLink.java` | Client runtime/support class. | Core simulation node-edge link primitive. | `core` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CircuitRenderer.java` | Client rendering helper class. | Rendering/geometry subsystem family. | `Keep` | 0 | 0 | 1 | 0 | 0 | 1 | 1 |
| `CircuitTestRunner.java` | Runner/execution integration class. | Runner subsystem family for headless/automation execution. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CircuitValueSlotManager.java` | Client manager/coordinator class. | Close to package peers and shared base classes. | `elements` | 0 | 0 | 3 | 0 | 0 | 0 | 0 |
| `ClipboardManager.java` | Client manager/coordinator class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `Color.java` | Client runtime/support class. | Rendering/geometry subsystem family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CompositeElm.java` | Base/legacy element class in root package. | Composite-element tooling; tied to electronics/misc CustomComposite* elements. | `Keep` | 0 | 0 | 1 | 0 | 1 | 0 | 0 |
| `CustomCompositeModel.java` | Client runtime/support class. | Composite-element tooling; tied to electronics/misc CustomComposite* elements. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CustomLogicModel.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `Dialog.java` | Client dialog/controller class. | Close to package peers and shared base classes. | `ui` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `Diode.java` | Client runtime/support class. | Diode device primitive used by semiconductor elements. | `elements/electronics` | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| `DiodeModel.java` | Client runtime/support class. | Diode model/editing family. | `elements/electronics` | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `EditCompositeModelDialog.java` | Client dialog/controller class. | Composite-element tooling; tied to electronics/misc CustomComposite* elements. | `elements` | 1 | 0 | 2 | 0 | 0 | 0 | 1 |
| `EditDialog.java` | Client dialog/controller class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 1 |
| `EditDialogActions.java` | Client runtime/support class. | Close to package peers and shared base classes. | `ui` | 0 | 0 | 0 | 0 | 0 | 2 | 1 |
| `EditDialogLoadFile.java` | Client runtime/support class. | Load/import path helpers. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `EditDiodeModelDialog.java` | Client dialog/controller class. | Diode model/editing family. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `EditInfo.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `EditOptions.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 1 |
| `EditTransistorModelDialog.java` | Client dialog/controller class. | Transistor model/editing family. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `Editable.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ElementLegacyFactory.java` | Client factory/helper class. | Close to package peers and shared base classes. | `elements` | 0 | 0 | 13 | 0 | 0 | 0 | 0 |
| `ElementRegistryBootstrap.java` | Client runtime/support class. | Close to package peers and shared base classes. | `elements` | 0 | 0 | 8 | 0 | 2 | 0 | 0 |
| `ExportCompositeActions.java` | Client runtime/support class. | Composite-element tooling; tied to electronics/misc CustomComposite* elements. | `elements` | 0 | 0 | 3 | 0 | 0 | 1 | 0 |
| `Expr.java` | Client runtime/support class. | Expression parsing/evaluation family used by table/equation features. | `elements` | 0 | 0 | 2 | 1 | 0 | 0 | 0 |
| `ExprParser.java` | Client runtime/support class. | Expression parsing/evaluation family used by table/equation features. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `ExprState.java` | Client runtime/support class. | Expression parsing/evaluation family used by table/equation features. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `FFT.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `FlipTransformController.java` | Client controller class. | Close to package peers and shared base classes. | `elements` | 0 | 0 | 2 | 0 | 0 | 0 | 0 |
| `FloatingControlPanel.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 1 | 1 |
| `Font.java` | Client runtime/support class. | Rendering/geometry subsystem family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `Graphics.java` | Client runtime/support class. | Rendering/geometry subsystem family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `HintRegistry.java` | Client registry class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ImportFromDropbox.java` | Client runtime/support class. | Load/import path helpers. | `io` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `Inductor.java` | Client runtime/support class. | Inductor device primitive used by passive and electromechanical elements. | `elements/electronics` | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| `InfoViewerLiveDataSerializer.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `InfoViewerTableMarkdown.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 1 |
| `IntPair.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `JsApiBridge.java` | Bridge/interop integration class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `LoadFile.java` | Client runtime/support class. | Load/import path helpers. | `Keep` | 0 | 0 | 0 | 0 | 0 | 1 | 0 |
| `MathElementsTest.java` | Client runtime/support class. | Table/economics test/debug helpers; close to elements/economics and elements/math. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `MenuUiState.java` | Client runtime/support class. | Close to package peers and shared base classes. | `ui` | 0 | 0 | 0 | 0 | 0 | 2 | 0 |
| `MouseInputHandler.java` | Client runtime/support class. | Close to package peers and shared base classes. | `elements` | 0 | 0 | 8 | 0 | 1 | 0 | 0 |
| `MyCommand.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `NumFmt.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `Point.java` | Client runtime/support class. | Rendering/geometry subsystem family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `Polygon.java` | Client runtime/support class. | Rendering/geometry subsystem family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `QueryParameters.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `Rectangle.java` | Client runtime/support class. | Rendering/geometry subsystem family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `RowInfo.java` | Client runtime/support class. | Matrix row metadata used by solver simplification. | `core` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `RunnerController.java` | Client controller class. | Runner subsystem family for headless/automation execution. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `RunnerJsBridge.java` | Bridge/interop integration class. | Runner subsystem family for headless/automation execution. | `runner` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `RunnerLaunchDecision.java` | Runner/execution integration class. | Runner subsystem family for headless/automation execution. | `runner` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `RunnerPanelUi.java` | Runner/execution integration class. | Runner subsystem family for headless/automation execution. | `runner` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `RuntimeMode.java` | Client runtime/support class. | Runner subsystem family for headless/automation execution. | `runner` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SFCRDagBlocksViewer.java` | Client runtime/support class. | SFCR DAG visualization pair. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `SFCRDagBlocksViewerTemplate.html` | HTML template/resource used by client viewer/export UI. | SFCR DAG visualization pair. | `ui` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SFCRDocumentManager.java` | Client manager/coordinator class. | Close to package peers and shared base classes. | `io` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SFCRDocumentState.java` | Client runtime/support class. | Close to package peers and shared base classes. | `io` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SRAMLoadFile.java` | Client runtime/support class. | Load/import path helpers. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `SankeyD3EmbeddedTemplate.html` | HTML template/resource used by client viewer/export UI. | Sankey visualization family; close to elements/economics SFCSankeyElm. | `ui` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SankeyD3StandaloneTemplate.html` | HTML template/resource used by client viewer/export UI. | Sankey visualization family; close to elements/economics SFCSankeyElm. | `ui` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SankeyPlotlyEmbeddedTemplate.html` | HTML template/resource used by client viewer/export UI. | Sankey visualization family; close to elements/economics SFCSankeyElm. | `ui` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SankeyPlotlyStandaloneTemplate.html` | HTML template/resource used by client viewer/export UI. | Sankey visualization family; close to elements/economics SFCSankeyElm. | `ui` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `Scope.java` | Client runtime/support class. | Scope visualization family; linked with elements/misc ScopeElm. | `elements` | 0 | 0 | 5 | 0 | 0 | 0 | 1 |
| `ScopeManager.java` | Client manager/coordinator class. | Scope visualization family; linked with elements/misc ScopeElm. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `ScopePropertiesDialog.java` | Client dialog/controller class. | Scope visualization family; linked with elements/misc ScopeElm. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 1 |
| `ScrollValuePopup.java` | Client runtime/support class. | Element parameter editing UI flow. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `Scrollbar.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SetupListLoader.java` | Client runtime/support class. | Load/import path helpers. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `SimulationContextAdapter.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| `SimulationExportCore.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `SimulationLoop.java` | Client runtime/support class. | Close to package peers and shared base classes. | `elements` | 0 | 2 | 3 | 0 | 0 | 0 | 1 |
| `SliderDialog.java` | Client dialog/controller class. | Element parameter editing UI flow. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `StatusInfoRenderer.java` | Client rendering helper class. | Close to package peers and shared base classes. | `elements` | 0 | 0 | 4 | 0 | 0 | 0 | 1 |
| `StockFlowTableSemantics.java` | Client runtime/support class. | Table/economics test/debug helpers; close to elements/economics and elements/math. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `StockTableView.java` | Client runtime/support class. | Table/economics test/debug helpers; close to elements/economics and elements/math. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `StringTokenizer.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TableEditDialog-old._java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TableElementsTest.java` | Client runtime/support class. | Table/economics test/debug helpers; close to elements/economics and elements/math. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `TableMasterRegistryManager.java` | Client manager/coordinator class. | Table/economics test/debug helpers; close to elements/economics and elements/math. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `ToolbarModeManager.java` | Client manager/coordinator class. | Close to package peers and shared base classes. | `ui` | 0 | 0 | 0 | 0 | 0 | 2 | 0 |
| `TransistorModel.java` | Client runtime/support class. | Transistor model/editing family. | `elements/electronics` | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| `UndoRedoManager.java` | Client manager/coordinator class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ViewportController.java` | Client controller class. | Viewport/camera handling; linked with elements/misc ViewportElm. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| `circuitjs1.java` | Client runtime/support class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 1 |

## core

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `CircuitMatrixOps.java` | Core simulation infrastructure class. | Solver/timestep core family used by simulation loop and CirSim. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ConfigProvider.java` | Core interface for simulation/runtime dependencies. | Interfaces implemented/used by CirSim and migrated elements. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ConsoleLogger.java` | Core interface for simulation/runtime dependencies. | Interfaces implemented/used by CirSim and migrated elements. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `LUSolver.java` | Core matrix/solver implementation. | Solver/timestep core family used by simulation loop and CirSim. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `MatrixStamper.java` | Core matrix-stamping helper. | Solver/timestep core family used by simulation loop and CirSim. | `root` | 4 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SimulationContext.java` | Core interface for simulation/runtime dependencies. | Interfaces implemented/used by CirSim and migrated elements. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SimulationTimingState.java` | Core simulation state container. | Solver/timestep core family used by simulation loop and CirSim. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SolverMatrixState.java` | Core simulation state container. | Solver/timestep core family used by simulation loop and CirSim. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |

## elements

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `ActionScheduler.java` | Client runtime/support class. | Coordinates with ActionTimeDialog and elements/misc ActionTimeElm. | `Keep` | 0 | 0 | 4 | 0 | 0 | 0 | 0 |
| `ActionTimeDialog.java` | Client dialog/controller class. | Works with ActionScheduler and elements/misc ActionTimeElm. | `Keep` | 1 | 0 | 2 | 0 | 0 | 0 | 1 |
| `ChipElm.java` | Base element class in elements package. | Extends CircuitElm; base for many digital/chip elements. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `EquationTableMarkdownDebugDialog.java` | Client dialog/controller class. | Table/economics test/debug helpers; close to elements/economics and elements/math. | `Keep` | 0 | 0 | 3 | 0 | 0 | 1 | 0 |
| `SFCSankeyRenderer.java` | Client rendering helper class. | Sankey visualization family; close to elements/economics SFCSankeyElm. | `Keep` | 0 | 0 | 2 | 0 | 0 | 0 | 0 |
| `SFCSankeyViewer.java` | Client runtime/support class. | Sankey visualization family; close to elements/economics SFCSankeyElm. | `Keep` | 0 | 0 | 2 | 0 | 0 | 0 | 1 |

## elements/annotation

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `BoxElm.java` | Visual annotation element class. | Close to annotation base GraphicElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `GraphicElm.java` | Visual annotation element class. | Base annotation class; peers TextElm LineElm BoxElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `LineElm.java` | Visual annotation element class. | Close to annotation base GraphicElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TextElm.java` | Visual annotation element class. | Close to annotation base GraphicElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 1 | 1 |

## elements/economics

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `ComputedValueObserver.java` | Supporting economics type for data/behavior orchestration. | Computed-values publish/observe flow. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ComputedValueProvider.java` | Supporting economics type for data/behavior orchestration. | Computed-values publish/observe flow. | `Keep` | 2 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ComputedValueSourceElm.java` | Economics simulation element class. | Computed-values publish/observe flow. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ComputedValues.java` | Economics simulation element class. | Computed-values publish/observe flow. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CurrentTransactionsMatrixElm.java` | Economics simulation element class. | CurrentTransactionsMatrix Elm/Renderer pair. | `Keep` | 1 | 0 | 2 | 0 | 0 | 0 | 0 |
| `CurrentTransactionsMatrixRenderer.java` | Rendering helper for economics element UI. | CurrentTransactionsMatrix Elm/Renderer pair. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| `EquationElm.java` | Economics simulation element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 1 | 0 | 0 | 0 | 1 |
| `EquationTableEditDialog.java` | Editing/debug dialog for economics element data. | EquationTableElm toolchain. | `Keep` | 1 | 0 | 2 | 0 | 0 | 1 | 1 |
| `EquationTableElm.java` | Economics simulation element class. | Core with EquationTableRenderer EquationTableEditDialog EquationTableJacobianHelper EquationTableSemantics. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| `EquationTableJacobianHelper.java` | Supporting economics type for data/behavior orchestration. | EquationTableElm toolchain. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| `EquationTableRenderer.java` | Rendering helper for economics element UI. | EquationTableElm toolchain. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 1 |
| `EquationTableSemantics.java` | Supporting economics type for data/behavior orchestration. | EquationTableElm toolchain. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `FlowsMasterElm.java` | Economics simulation element class. | Stock-flow runtime family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `GodlyTableElm.java` | Economics simulation element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| `SFCFlowElm.java` | Economics simulation element class. | Stock-flow runtime family. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| `SFCSankeyElm.java` | Economics simulation element class. | Consumed by Sankey viewer/renderer flows. | `Keep` | 2 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SFCStockElm.java` | Economics simulation element class. | Stock-flow runtime family. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| `SFCTableElm.java` | Economics simulation element class. | SFCTable Elm/Renderer pair. | `Keep` | 2 | 0 | 1 | 0 | 0 | 0 | 0 |
| `SFCTableRenderer.java` | Rendering helper for economics element UI. | SFCTable Elm/Renderer pair. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| `ScenarioElm.java` | Economics simulation element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 1 |
| `StockFlowRegistry.java` | Supporting economics type for data/behavior orchestration. | Stock-flow runtime family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `StockMasterElm.java` | Economics simulation element class. | Stock-flow runtime family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SyncPatch.java` | Supporting economics type for data/behavior orchestration. | Used with table content synchronization. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TableColumn.java` | Supporting economics type for data/behavior orchestration. | TableElm toolchain. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TableContentView.java` | Supporting economics type for data/behavior orchestration. | TableElm toolchain. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TableDataManager.java` | Supporting economics type for data/behavior orchestration. | TableElm toolchain. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| `TableEditDialog.java` | Editing/debug dialog for economics element data. | TableElm toolchain. | `Keep` | 1 | 0 | 2 | 0 | 0 | 1 | 1 |
| `TableElm.java` | Economics simulation element class. | Core with TableRenderer TableEditDialog TableDataManager TableGeometryManager TableEquationManager TableColumn TableContentView. | `Keep` | 1 | 0 | 2 | 0 | 0 | 0 | 0 |
| `TableEquationManager.java` | Supporting economics type for data/behavior orchestration. | TableElm toolchain. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| `TableGeometryManager.java` | Supporting economics type for data/behavior orchestration. | TableElm toolchain. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TableMarkdownDebugDialog.java` | Editing/debug dialog for economics element data. | TableElm toolchain. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TableRenderer.java` | Rendering helper for economics element UI. | TableElm toolchain. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |

## elements/electronics/analog

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `AnalogSwitch2Elm.java` | Analog electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `AnalogSwitchElm.java` | Analog electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CC2Elm.java` | Analog electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CC2NegElm.java` | Analog electronics element class. | Variant of CC2Elm. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CCCSElm.java` | Analog electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 1 | 0 | 0 | 0 | 0 |
| `CCVSElm.java` | Analog electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 1 | 0 | 0 | 0 | 0 |
| `ComparatorElm.java` | Analog electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `OTAElm.java` | Analog electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| `OpAmpElm.java` | Analog electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `OpAmpRealElm.java` | Analog electronics element class. | Variants of OpAmpElm. | `Keep` | 1 | 0 | 3 | 0 | 0 | 0 | 0 |
| `OpAmpSwapElm.java` | Analog electronics element class. | Variants of OpAmpElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `VCCSElm.java` | Analog electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 1 |
| `VCVSElm.java` | Analog electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 0 |

## elements/electronics/digital

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `ADCElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `AndGateElm.java` | Digital electronics element class. | Gate family around GateElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `Counter2Elm.java` | Digital electronics element class. | Counter/sequence family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `CounterElm.java` | Digital electronics element class. | Counter/sequence family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `CustomLogicElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `DACElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `DFlipFlopElm.java` | Digital electronics element class. | Sequential-logic family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `DeMultiplexerElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `DecimalDisplayElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `DelayBufferElm.java` | Digital electronics element class. | Gate family around GateElm. | `Keep` | 1 | 1 | 1 | 0 | 0 | 0 | 1 |
| `FullAdderElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `GateElm.java` | Digital electronics element class. | Base for logic-gate family. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| `HalfAdderElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `InverterElm.java` | Digital electronics element class. | Gate family around GateElm. | `Keep` | 1 | 1 | 1 | 0 | 0 | 0 | 0 |
| `InvertingSchmittElm.java` | Digital electronics element class. | Gate family around GateElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `JKFlipFlopElm.java` | Digital electronics element class. | Sequential-logic family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `LatchElm.java` | Digital electronics element class. | Sequential-logic family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `LogicInputElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| `LogicOutputElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `MonostableElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| `MultiplexerElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `NandGateElm.java` | Digital electronics element class. | Gate family around GateElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `NorGateElm.java` | Digital electronics element class. | Gate family around GateElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `OrGateElm.java` | Digital electronics element class. | Gate family around GateElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `PhaseCompElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `PisoShiftElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `RingCounterElm.java` | Digital electronics element class. | Counter/sequence family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SRAMElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SchmittElm.java` | Digital electronics element class. | Gate family around GateElm. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| `SeqGenElm.java` | Digital electronics element class. | Counter/sequence family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SevenSegDecoderElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SevenSegElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SipoShiftElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TFlipFlopElm.java` | Digital electronics element class. | Sequential-logic family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TimerElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TriStateElm.java` | Digital electronics element class. | Gate family around GateElm. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| `VCOElm.java` | Digital electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `XorGateElm.java` | Digital electronics element class. | Gate family around GateElm. | `Keep` | 2 | 0 | 0 | 0 | 0 | 0 | 0 |

## elements/electronics/electromechanical

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `CrossSwitchElm.java` | Electromechanical element class. | Switch family around SwitchElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CustomTransformerElm.java` | Electromechanical element class. | Transformer family. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| `DCMotorElm.java` | Electromechanical element class. | Motor family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `DPDTSwitchElm.java` | Electromechanical element class. | Switch family around SwitchElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `MBBSwitchElm.java` | Electromechanical element class. | Switch family around SwitchElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `MotorProtectionSwitchElm.java` | Electromechanical element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `OptocouplerElm.java` | Electromechanical element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 3 | 0 | 0 | 0 | 0 |
| `PushSwitchElm.java` | Electromechanical element class. | Switch family around SwitchElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `RelayCoilElm.java` | Electromechanical element class. | Relay family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `RelayContactElm.java` | Electromechanical element class. | Relay family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `RelayElm.java` | Electromechanical element class. | Relay family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `Switch2Elm.java` | Electromechanical element class. | Switch family around SwitchElm. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SwitchElm.java` | Electromechanical element class. | Base for switch family. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| `TappedTransformerElm.java` | Electromechanical element class. | Transformer family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ThreePhaseMotorElm.java` | Electromechanical element class. | Motor family. | `Keep` | 1 | 2 | 0 | 0 | 0 | 0 | 1 |
| `TimeDelayRelayElm.java` | Electromechanical element class. | Relay family. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| `TransformerElm.java` | Electromechanical element class. | Transformer family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |

## elements/electronics/measurement

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `AmmeterElm.java` | Measurement/monitoring element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `AudioOutputElm.java` | Measurement/monitoring element class. | Pairs with sources AudioInputElm. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 1 |
| `DataRecorderElm.java` | Measurement/monitoring element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `OhmMeterElm.java` | Measurement/monitoring element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 1 |
| `OutputElm.java` | Measurement/monitoring element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ProbeElm.java` | Measurement/monitoring element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `TestPointElm.java` | Measurement/monitoring element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `WattmeterElm.java` | Measurement/monitoring element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |

## elements/electronics/misc

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `CrystalElm.java` | Specialized electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 1 |
| `CustomCompositeChipElm.java` | Specialized electronics element class. | Composite element family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CustomCompositeElm.java` | Specialized electronics element class. | Composite element family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `FuseElm.java` | Specialized electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 1 |
| `LDRElm.java` | Specialized electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `LEDArrayElm.java` | Specialized electronics element class. | LED family tied to diode behavior. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `LEDElm.java` | Specialized electronics element class. | LED family tied to diode behavior. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 1 |
| `LampElm.java` | Specialized electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 1 |
| `MemristorElm.java` | Specialized electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `SparkGapElm.java` | Specialized electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `StopTriggerElm.java` | Specialized electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 1 |
| `ThermistorNTCElm.java` | Specialized electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `TransLineElm.java` | Specialized electronics element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |

## elements/electronics/passives

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `CapacitorElm.java` | Passive component element class. | Capacitor family. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 1 |
| `InductorElm.java` | Passive component element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `PolarCapacitorElm.java` | Passive component element class. | Capacitor family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `PotElm.java` | Passive component element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ResistorElm.java` | Passive component element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |

## elements/electronics/semiconductors

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `DarlingtonElm.java` | Semiconductor element class. | Transistor-variant family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `DiacElm.java` | Semiconductor element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `DiodeElm.java` | Semiconductor element class. | Diode family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `JfetElm.java` | Semiconductor element class. | JFET family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `MosfetElm.java` | Semiconductor element class. | MOSFET family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `NDarlingtonElm.java` | Semiconductor element class. | Transistor-variant family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `NJfetElm.java` | Semiconductor element class. | JFET family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `NMosfetElm.java` | Semiconductor element class. | MOSFET family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `NTransistorElm.java` | Semiconductor element class. | Transistor-variant family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `PDarlingtonElm.java` | Semiconductor element class. | Transistor-variant family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `PJfetElm.java` | Semiconductor element class. | JFET family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `PMosfetElm.java` | Semiconductor element class. | MOSFET family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `PTransistorElm.java` | Semiconductor element class. | Transistor-variant family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SCRElm.java` | Semiconductor element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TransistorElm.java` | Semiconductor element class. | Base for transistor variants. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `TriacElm.java` | Semiconductor element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TriodeElm.java` | Semiconductor element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TunnelDiodeElm.java` | Semiconductor element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `UnijunctionElm.java` | Semiconductor element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `VaractorElm.java` | Semiconductor element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ZenerElm.java` | Semiconductor element class. | Diode family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |

## elements/electronics/sources

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `ACRailElm.java` | Signal/power source element class. | Voltage/rail source family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ACVoltageElm.java` | Signal/power source element class. | Voltage/rail source family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `AMElm.java` | Signal/power source element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| `AntennaElm.java` | Signal/power source element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| `AudioInputElm.java` | Signal/power source element class. | Pairs with measurement AudioOutputElm. | `Keep` | 1 | 1 | 1 | 0 | 0 | 0 | 1 |
| `ClockElm.java` | Signal/power source element class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CurrentElm.java` | Signal/power source element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `DCVoltageElm.java` | Signal/power source element class. | Voltage/rail source family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `DataInputElm.java` | Signal/power source element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 1 |
| `ExtVoltageElm.java` | Signal/power source element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `FMElm.java` | Signal/power source element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| `NoiseElm.java` | Signal/power source element class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `RailElm.java` | Signal/power source element class. | Voltage/rail source family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SquareRailElm.java` | Signal/power source element class. | Voltage/rail source family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SweepElm.java` | Signal/power source element class. | Close to package peers and shared base classes. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| `TableVoltageElm.java` | Signal/power source element class. | Bridge between source behavior and economics tables. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| `VarRailElm.java` | Signal/power source element class. | Voltage/rail source family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `VoltageElm.java` | Signal/power source element class. | Base for voltage/rail variants. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 1 |

## elements/electronics/wiring

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `GroundElm.java` | Wiring/connectivity element class. | Core connectivity family used by most elements. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `LabeledNodeElm.java` | Wiring/connectivity element class. | Core connectivity family used by most elements. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 1 |
| `WireElm.java` | Wiring/connectivity element class. | Core connectivity family used by most elements. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |

## elements/math

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `AdderElm.java` | Math operation/dynamics element class. | Arithmetic math family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `DifferentiatorElm.java` | Math operation/dynamics element class. | Dynamic-system math family. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| `DivideConstElm.java` | Math operation/dynamics element class. | Arithmetic math family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `DividerElm.java` | Math operation/dynamics element class. | Arithmetic math family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `IntegratorElm.java` | Math operation/dynamics element class. | Dynamic-system math family. | `Keep` | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| `MultiplyConstElm.java` | Math operation/dynamics element class. | Arithmetic math family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `MultiplyElm.java` | Math operation/dynamics element class. | Arithmetic math family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ODEElm.java` | Math operation/dynamics element class. | Dynamic-system math family. | `Keep` | 1 | 1 | 2 | 0 | 0 | 0 | 0 |
| `PercentElm.java` | Math operation/dynamics element class. | Arithmetic math family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SubtracterElm.java` | Math operation/dynamics element class. | Arithmetic math family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |

## elements/misc

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `ActionTimeElm.java` | UI-support element class (scope/viewport/time/chart). | Close to scheduler/time-control flows. | `Keep` | 2 | 1 | 0 | 0 | 0 | 0 | 1 |
| `PieChartElm.java` | UI-support element class (scope/viewport/time/chart). | Close to PieChartDialog and economics value sources. | `Keep` | 1 | 0 | 2 | 0 | 0 | 1 | 0 |
| `ScopeElm.java` | UI-support element class (scope/viewport/time/chart). | Close to Scope and ScopeManager flows. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `StopTimeElm.java` | UI-support element class (scope/viewport/time/chart). | Close to scheduler/time-control flows. | `Keep` | 1 | 1 | 0 | 0 | 0 | 1 | 1 |
| `ViewportElm.java` | UI-support element class (scope/viewport/time/chart). | Close to viewport and render transform flows. | `Keep` | 1 | 0 | 1 | 0 | 0 | 0 | 0 |

## io

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `ImportExportHelper.java` | IO/import-export support class. | SFCR export/util family. | `root` | 3 | 0 | 0 | 0 | 0 | 0 | 0 |
| `InfoViewerContentBuilder.java` | IO/import-export support class. | SFCR export/util family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `LookupBlocksTextUtil.java` | IO/import-export support class. | SFCR parse + lookup family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `LookupDefinition.java` | IO lookup/metadata registry type. | SFCR parse + lookup family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `LookupTableRegistry.java` | IO lookup/metadata registry type. | SFCR parse + lookup family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SFCRBlockCommentRegistry.java` | IO lookup/metadata registry type. | SFCR parse + lookup family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SFCRExporter.java` | Export writer for circuit/SFCR outputs. | SFCR export/util family. | `Keep` | 1 | 0 | 2 | 0 | 0 | 0 | 0 |
| `SFCRParseResult.java` | IO/import-export support class. | SFCR parse + lookup family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SFCRParseResultExporter.java` | Export writer for circuit/SFCR outputs. | SFCR export/util family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SFCRParser.java` | Import parser for circuit/SFCR text. | SFCR parse + lookup family. | `Keep` | 1 | 0 | 2 | 0 | 1 | 0 | 0 |
| `SFCRUtil.java` | IO/import-export support class. | SFCR export/util family. | `Keep` | 0 | 0 | 1 | 0 | 0 | 0 | 0 |

## registry

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `ElementCategory.java` | Element registry/factory support type. | Element registry/factory family; used by bootstrap and runtime creation. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ElementFactoryFacade.java` | Element factory/creation abstraction. | Element registry/factory family; used by bootstrap and runtime creation. | `root` | 4 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ElementRegistry.java` | Element registry and lookup implementation. | Element registry/factory family; used by bootstrap and runtime creation. | `root` | 4 | 0 | 1 | 0 | 0 | 0 | 0 |

## ui

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `AboutBox.java` | UI component/helper class. | Close to package peers and shared base classes. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `CheckboxMenuItem.java` | UI menu item widget. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `EconomicsToolbar.java` | UI toolbar component. | Toolbar family for mode/domain switching. | `Keep` | 2 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ElectronicsToolbar.java` | UI toolbar component. | Toolbar family for mode/domain switching. | `Keep` | 2 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ExportAsImageDialog.java` | UI dialog component. | Export dialog family; close to CircuitIOService/SimulationExportCore. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ExportAsLocalFileDialog.java` | UI dialog component. | Export dialog family; close to CircuitIOService/SimulationExportCore. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ExportAsSFCRDialog.java` | UI dialog component. | Export dialog family; close to CircuitIOService/SimulationExportCore. | `Keep` | 1 | 0 | 0 | 1 | 0 | 0 | 1 |
| `ExportAsTextDialog.java` | UI dialog component. | Export dialog family; close to CircuitIOService/SimulationExportCore. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ExportAsUrlDialog.java` | UI dialog component. | Export dialog family; close to CircuitIOService/SimulationExportCore. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ExportScopeDataDialog.java` | UI dialog component. | Export dialog family; close to CircuitIOService/SimulationExportCore. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `HintEditorDialog.java` | UI dialog component. | Info-viewer/dialog family. | `root` | 3 | 0 | 1 | 0 | 0 | 0 | 1 |
| `IframeViewerDialog.java` | UI dialog component. | Close to package peers and shared base classes. | `Keep` | 2 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ImportFromDropboxDialog.java` | UI dialog component. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ImportFromTextDialog.java` | UI dialog component. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `InfoDialogActions.java` | UI component/helper class. | Close to package peers and shared base classes. | `Keep` | 2 | 0 | 0 | 0 | 0 | 0 | 0 |
| `InfoViewerDialog.java` | UI dialog component. | Info-viewer/dialog family. | `root` | 4 | 0 | 1 | 2 | 0 | 0 | 1 |
| `InfoViewerHtmlBuilder.java` | UI content/HTML builder helper. | Info-viewer/dialog family. | `Keep` | 2 | 0 | 0 | 0 | 0 | 0 | 0 |
| `InfoViewerSimpleMarkdown.java` | UI component/helper class. | Info-viewer/dialog family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `LookupTablesEditorDialog.java` | UI dialog component. | Linked to io LookupTableRegistry and lookup definitions. | `io` | 1 | 0 | 0 | 3 | 0 | 0 | 1 |
| `MathElementsTestDialog.java` | UI dialog component. | Close to package peers and shared base classes. | `root` | 3 | 0 | 0 | 0 | 0 | 0 | 0 |
| `PieChartDialog.java` | UI dialog component. | Linked to elements/misc PieChartElm and economics values. | `Keep` | 1 | 0 | 2 | 0 | 0 | 0 | 1 |
| `ReferenceDocs.java` | UI component/helper class. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `ScopePopupMenu.java` | UI component/helper class. | Scope UI family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ScopeViewerDialog.java` | UI dialog component. | Scope UI family. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ScopeViewerTemplate.html` | HTML template/resource used by client viewer/export UI. | Scope UI family. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `SearchDialog.java` | UI dialog component. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `ShortcutsDialog.java` | UI dialog component. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `SubcircuitDialog.java` | UI dialog component. | Close to package peers and shared base classes. | `Keep` | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| `TableElementsTestDialog.java` | UI dialog component. | Close to package peers and shared base classes. | `root` | 3 | 0 | 0 | 0 | 0 | 0 | 0 |
| `Toolbar.java` | UI toolbar component. | Toolbar family for mode/domain switching. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `VariableBrowserDialog.java` | UI dialog component. | Info-viewer/dialog family. | `root` | 3 | 0 | 1 | 0 | 0 | 0 | 1 |

## util

| File | What It Is For | Close Relationships | move to | root | core | elements | io | registry | ui | util |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| `Locale.java` | General utility class used across client runtime. | Shared utility family used across client runtime. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `PerfMonitor.java` | General utility class used across client runtime. | Shared utility family used across client runtime. | `Keep` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
