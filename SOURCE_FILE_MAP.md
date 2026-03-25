# Client File Map

Inventory for files under src/com/lushprojects/circuitjs1/client.

- Grouped by directory.
- Includes Java and template files.
- Summarizes each file's purpose and close relationships.

## (root)

| File | What It Is For | Close Relationships |
|---|---|---|
| `Adjustable.java` | Creates UI sliders for adjusting element parameter values | EditInfo, Scrollbar, CircuitElm parameter editing |
| `CheckboxAlignedMenuItem.java` | Menu item with checkbox-style alignment spacing for visual consistency | CheckboxMenuItem, GWT MenuItem styling |
| `CirSim.java` | Main simulator controller implementing MNA matrix solving and coordination | Core hub connecting all subsystems and managers |
| `CirSimBootstrap.java` | Initializes simulator state for runner and non-interactive modes | CirSim initialization, RunnerController, RuntimeMode |
| `CirSimCommandRouter.java` | Routes menu commands to appropriate handler methods and dialogs | MyCommand, menu handlers, dialog launchers |
| `CirSimDiagnostics.java` | Logs element registry inference reports for debugging | ElementRegistry diagnostic output |
| `CirSimDialogCoordinator.java` | Static coordinator managing active dialog state and conflicts | EditDialog, AboutBox, ScrollValuePopup tracking |
| `CirSimInitializer.java` | Full application initialization including UI, menus, and query parameters | CirSim setup, preferences, layout construction |
| `CirSimMenuBuilder.java` | Builds component menus from menulist.txt or hardcoded definitions | MenuBar construction, checkbox menu items, shortcuts |
| `CirSimPlatformInterop.java` | Handles Electron desktop app integration and touch events | Electron save/open, touch gesture handlers |
| `CirSimPreferencesManager.java` | Manages user preferences in localStorage including colors and shortcuts | Storage persistence, color settings, voltage units |
| `CirSimUiPanelManager.java` | Manages vertical panel widget additions and iFrame sizing | VerticalPanel layout, widget insertion |
| `CircuitAnalyzer.java` | Analyzes circuit topology: wire closure, node mapping, ground detection | CircuitNode building, wire processing, validation |
| `CircuitElm.java` | Abstract base class for all circuit elements with drawing and simulation | Parent of all *Elm classes, MNA stamping |
| `CircuitIOService.java` | Handles circuit file import/export, URLs, text, SFCR formats | Circuit dump, recovery storage, export dialogs |
| `CircuitRenderer.java` | Draws all circuit elements and handles canvas graphics rendering | Graphics drawing, element rendering, transforms |
| `CircuitValueSlotManager.java` | Maps variable names to slots for expression evaluation access | ComputedValues, LabeledNodeElm, slider values |
| `ClipboardManagerCore.java` | Implements cut/copy/paste/duplicate operations for circuit elements | Element selection, clipboard storage, paste logic |
| `CompositeElm.java` | Base class for subcircuits containing multiple internal elements | Subcircuit composition, internal node mapping |
| `CustomCompositeModel.java` | Stores and manages user-defined subcircuit models | Subcircuit definitions, localStorage persistence |
| `CustomLogicModel.java` | Defines custom digital logic chip behavior with rule-based definitions | CustomLogicElm, input/output rules parsing |
| `Editable.java` | Interface for elements with editable properties via EditInfo | EditInfo, edit dialog integration contract |
| `ExportCompositeActions.java` | Handles SVG export, DC analysis, subcircuit creation, and printing | Canvas2SVG, image export, print functions |
| `FlipTransformController.java` | Handles flip X, flip Y, and rotate operations on selected elements | Element transform, selection geometry |
| `JsApiBridge.java` | Exposes CircuitJS1 API to JavaScript for external control | window.CircuitJS1 API, element access, control hooks |
| `MouseInputHandler.java` | Processes all mouse and keyboard events for canvas interaction | Click/drag handling, mode management, shortcuts |
| `MyCommand.java` | GWT Command wrapper routing menu actions to CirSimCommandRouter | Menu item execution, command routing |
| `RunnerController.java` | Controls non-interactive batch simulation execution from query parameters | Runner mode, async simulation, output generation |
| `Scope.java` | Oscilloscope display for plotting voltage/current/power over time | ScopePlot data, waveform rendering, measurements |
| `ScopeManager.java` | Manages multiple scopes: stacking, combining, selection, sizing | Scope array management, height fraction |
| `ScopePropertiesDialog.java` | Thin wrapper around ScopePropertiesDialogCore for public access | ScopePropertiesDialogCore inheritance |
| `ScopePropertiesDialogCore.java` | Dialog for configuring scope display options and scale settings | Scope settings UI, manual scale, AC/DC coupling |
| `SetupListLoaderCore.java` | Loads circuit menu from setuplist files and builds menu structure | Circuit file menu, economics/electronics lists |
| `SimulationContextAdapter.java` | Adapter implementing SimulationContext interface by delegating to CirSim | SimulationContext bridge, matrix stamping delegation |
| `SimulationExportCore.java` | Runs batch simulations and exports results as TSV/CSV/World2 format | Batch run, ComputedValues export, HTML reports |
| `SimulationLoop.java` | Main simulation loop: analyze, stamp, solve matrix, render graphics | updateCircuit(), runCircuit(), iteration control |
| `StatusInfoRenderer.java` | Renders hint tooltips and status info in bottom area of canvas | Tooltip drawing, ActionScheduler messages |
| `ToolbarModeManager.java` | Switches between electronics and economics toolbar modes | EconomicsToolbar, ElectronicsToolbar, unit symbols |
| `UndoRedoManager.java` | Manages undo/redo stacks with circuit state and transform snapshots | Undo/redo operations, circuit state history |
| `ViewportController.java` | Controls canvas viewport: zoom, pan, coordinate transforms, sizing | Transform matrix, canvas sizing, centering |
| `circuitjs1.java` | GWT EntryPoint that loads locale and launches CirSim application | Program entry point, locale loading, version |

## core

| File | What It Is For | Close Relationships |
|---|---|---|
| `CircuitMatrixOps.java` | Wraps LU factorization/solve with debug logging for singular matrix detection | Delegates to LUSolver; used by circuit analysis |
| `CircuitNode.java` | Represents a circuit node with links to connected circuit elements | Contains CircuitNodeLinks; connects CircuitElms |
| `CircuitNodeLink.java` | Simple struct linking a CircuitElm to its terminal node number | Used by CircuitNode; references CircuitElm and terminal index |
| `ConfigProvider.java` | Interface for equation table MNA mode and SFCR configuration flags | Implemented by CirSim; used by EquationTableElm |
| `ConsoleLogger.java` | Simple interface with single log method for console output | Abstracts CirSim.console(); enables testable logging |
| `LUSolver.java` | Pure Java LU factorization and linear system solving with partial pivoting | Called by CircuitMatrixOps; core MNA matrix solver |
| `MatrixStamper.java` | Stamps resistors, voltage/current sources, and conductances into MNA matrix | Uses SolverMatrixState and RowInfo; called by CircuitElm |
| `RowInfo.java` | Matrix row metadata for simplification: type, mapping, and change flags | Used by MatrixStamper and SolverMatrixState |
| `SimulationContext.java` | Interface defining element stamping methods and simulation state access | Implemented by CirSim; used by CircuitElm |
| `SimulationTimingState.java` | Holds simulation time, timestep values, and timing counters | Used by CirSim simulation loop for time tracking |
| `SolverMatrixState.java` | Holds MNA matrix, right-side vector, node voltages, and solver metadata | Used by MatrixStamper, LUSolver; central solver data |

## elements

| File | What It Is For | Close Relationships |
|---|---|---|
| `ActionScheduler.java` | Central scheduler managing timed actions that change slider values at specific times | ActionTimeDialog, ActionTimeElm, ScenarioElm |
| `ActionTimeDialog.java` | Non-modal UI dialog for creating, editing, and deleting scheduled actions | ActionScheduler, ActionTimeElm, CirSim |
| `ChipElm.java` | Abstract base class for digital IC chips with pin layout and rendering | Extended by flip-flops, counters, gates, logic chips |
| `EquationTableMarkdownDebugDialog.java` | Debug dialog displaying EquationTableElm internal state in markdown | EquationTableElm, HintRegistry, ComputedValues |
| `Expr.java` | Expression tree evaluator with math operations and node references | ExprParser, ExprState, ComputedValues, LabeledNodeElm |
| `ExprParser.java` | Recursive descent parser that tokenizes text and builds Expr trees | Expr, ExprState, LabeledNodeElm identifiers |
| `ExprState.java` | Runtime state for expressions: integrate, diff, lag, and smooth buffers | Expr, ExprParser, timestep commit/reset lifecycle |
| `SFCSankeyRenderer.java` | Canvas-based Sankey diagram renderer showing money flows between sectors | SFCSankeyViewer, TableElm, economics stock-flow elements |
| `SFCSankeyViewer.java` | Dialog-based Sankey viewer using Plotly.js or D3.js for visualization | SFCSankeyRenderer, TableElm, Plotly/D3 templates |
| `SankeyD3EmbeddedTemplate.html` | D3.js Sankey diagram template for embedding in dialog | SFCSankeyViewer, D3 visualization |
| `SankeyD3StandaloneTemplate.html` | D3.js Sankey diagram template for standalone export | SFCSankeyViewer, D3 visualization |
| `SankeyPlotlyEmbeddedTemplate.html` | Plotly.js Sankey diagram template for embedding in dialog | SFCSankeyViewer, Plotly visualization |
| `SankeyPlotlyStandaloneTemplate.html` | Plotly.js Sankey diagram template for standalone export | SFCSankeyViewer, Plotly visualization |

## elements/annotation

| File | What It Is For | Close Relationships |
|---|---|---|
| `BoxElm.java` | Visual dashed rectangle annotation element for grouping regions | GraphicElm (parent), LineElm, TextElm |
| `GraphicElm.java` | Abstract base class for non-electrical visual annotation elements | BoxElm, LineElm, TextElm (children), CircuitElm (parent) |
| `LineElm.java` | Simple visual line annotation element for diagram markup | GraphicElm (parent), BoxElm, TextElm |
| `TextElm.java` | Text annotation with configurable color, size, multiline, and hyperlinks | GraphicElm (parent), InfoViewerDialog, Locale |

## elements/economics

| File | What It Is For | Close Relationships |
|---|---|---|
| `ComputedValueObserver.java` | Interface for elements that want to observe computed value updates | ComputedValueProvider, ComputedValues, CircuitElm |
| `ComputedValueProvider.java` | Base class for elements that provide computed values to observers | ComputedValueObserver, CircuitElm, observer pattern |
| `ComputedValueSourceElm.java` | Bridge element reading from ComputedValues registry, outputs as voltage | ComputedValues registry, LabeledNodeElm, voltage source |
| `ComputedValues.java` | Central registry with double-buffered computed values for evaluation | All economic/table elements, LabeledNodeElm, scenarios |
| `CurrentTransactionsMatrixElm.java` | Auto-populates columns from all master stocks, aggregates flows | TableElm, ComputedValues, CurrentTransactionsMatrixRenderer |
| `CurrentTransactionsMatrixRenderer.java` | Custom renderer adding A-L-E column calculations and flow mapping | TableRenderer, CurrentTransactionsMatrixElm |
| `EquationElm.java` | Standalone equation calculator with adjustable slider parameters a-h | Expr/ExprParser, LabeledNodeElm, voltage source output |
| `EquationTableEditDialog.java` | Modal grid dialog for editing equation table rows with validation | EquationTableElm, Dialog, row operations UI |
| `EquationTableElm.java` | Multi-row equation table with voltage, flow, and parameter modes | EquationTableRenderer, ComputedValues, MNA stamping |
| `EquationTableJacobianHelper.java` | Newton-Raphson partial derivative stamping for faster convergence | EquationTableElm, MNA matrix, finite-difference derivatives |
| `EquationTableRenderer.java` | Drawing operations with off-screen canvas caching and row icons | EquationTableElm, Graphics, theme colors |
| `EquationTableSemantics.java` | Pure-Java helpers for row mode checks and convergence tolerance | EquationTableElm, RowOutputMode enum |
| `FlowsMasterElm.java` | Displays all unique flow names from all tables with usage counts | ChipElm, TableElm, StockFlowRegistry |
| `GodlyTableElm.java` | Table with per-column time integration for stock accumulation | TableElm, Expr integration, voltage sources per column |
| `InfoViewerLiveDataSerializer.java` | Serializes live circuit state (vars, tables, scopes) to JSON | CirSim, TableElm, EquationTableElm, SFCSankeyViewer |
| `InfoViewerTableMarkdown.java` | Generates markdown documentation showing all circuit tables | TableElm, EquationTableElm, SFCTableElm, GodlyTableElm |
| `SFCFlowElm.java` | Current source between two stocks representing economic flow | SFCStockElm, Expr, equation-based flow rate |
| `SFCRDagBlocksViewer.java` | External popup showing equation dependency graph with Cytoscape | EquationTableElm, dependency analysis, Cytoscape |
| `SFCSankeyElm.java` | Standalone Sankey diagram visualizing flows from a linked SFC table | SFCSankeyRenderer, TableElm, layout modes |
| `SFCStockElm.java` | Capacitor-based element representing economic sector stock (balance) | SFCFlowElm, LabeledNodeElm, companion model |
| `SFCTableElm.java` | Stock-flow consistent table with sector columns and quad-entry accounting | TableElm, SFCTableRenderer, SFCSankeyRenderer |
| `SFCTableRenderer.java` | Custom renderer for SFC tables with sum row/column and highlighting | TableRenderer, SFCTableElm, ColumnType.COMPUTED |
| `ScenarioElm.java` | Applies time-triggered parameter overrides for scenarios | ComputedValues.setScenarioOverride, CirSim callbacks |
| `StockFlowRegistry.java` | Tracks which tables share stocks; merges rows; synchronizes tables | StockTableView, TableElm, SyncPatch, ComputedValues |
| `StockFlowTableSemantics.java` | Pure-Java helpers for convergence limits and integration calculations | GodlyTableElm and other integrating table elements |
| `StockMasterElm.java` | Diagnostic element displaying all master stocks with values/owners | ChipElm, ComputedValues registry, StockFlowRegistry |
| `StockTableView.java` | Interface for table views: title, rows, columns, headers, descriptions | TableContentView extends this; implemented by TableElm |
| `SyncPatch.java` | Immutable data class representing table sync operations (add row, set cell) | StockFlowRegistry creates patches for table sync |
| `TableColumn.java` | Encapsulates column data: name, type, equations, expressions, values | TableElm uses ArrayList of TableColumn |
| `TableContentView.java` | Extended interface adding getCellEquation, findColumn, getInitialValue | Extends StockTableView; implemented by TableElm |
| `TableDataManager.java` | Handles table initialization, column defaults, and row/column resizing | Helper class owned by TableElm; uses TableColumn |
| `TableEditDialog.java` | GWT dialog for editing table structure, equations, and synchronization | Dialog for TableElm; interacts with StockFlowRegistry |
| `TableElm.java` | Main circuit element for stock-flow tables with equation columns | ChipElm subclass; uses all Table*Manager classes |
| `TableEquationManager.java` | Compiles and evaluates cell equations using ExprParser and LabeledNodeElm | Helper for TableElm; uses Expr, ExprParser, ExprState |
| `TableGeometryManager.java` | Calculates pin positions, table dimensions, and grid-to-pixel conversions | Helper for TableElm; manages ChipElm.Pin positioning |
| `TableMarkdownDebugDialog.java` | Debug dialog showing markdown of related tables for inspection | Launched from TableEditDialog; uses StockFlowRegistry |
| `TableRenderer.java` | Draws table visuals with caching for backgrounds, grids, and text | Helper for TableElm; uses GWT Canvas for rendering |

## elements/electronics

| File | What It Is For | Close Relationships |
|---|---|---|
| `Diode.java` | Embeddable diode physics with Shockley equation and Zener breakdown | Uses DiodeModel; embedded by DiodeElm, ZenerElm |
| `DiodeModel.java` | Registry of diode parameters: saturation current, emission, breakdown | Provides parameters for Diode; used by diode/LED/Zener |
| `Inductor.java` | Embeddable inductor using trapezoidal/Euler companion circuit | Embedded by InductorElm; companion model stamps |
| `TransistorModel.java` | Registry of BJT SPICE parameters: saturation current, Early voltage | Provides parameters for TransistorElm, OpAmpRealElm |

## elements/electronics/analog

| File | What It Is For | Close Relationships |
|---|---|---|
| `AnalogSwitch2Elm.java` | SPDT (double-throw) voltage-controlled analog switch | Extends AnalogSwitchElm; adds second output pole |
| `AnalogSwitchElm.java` | SPST voltage-controlled analog switch with configurable resistance | Base for AnalogSwitch2Elm; used in ComparatorElm |
| `CC2Elm.java` | Second-generation current conveyor (CCII+): X=Y voltage, Z=gain×X current | Base for CC2NegElm; uses VCVS and CCCS stamps |
| `CC2NegElm.java` | Negative current conveyor (CCII-) with inverted gain (-1) | Extends CC2Elm with gain=-1 |
| `CCCSElm.java` | Current-controlled current source with expression-based transfer | Extends VCCSElm; adds 0V sources to measure current |
| `CCVSElm.java` | Current-controlled voltage source with expression-based transfer | Extends VCCSElm; outputs voltage via voltage source |
| `ComparatorElm.java` | Voltage comparator built as composite of OpAmpElm + AnalogSwitchElm | CompositeElm using OpAmpElm, AnalogSwitchElm, GroundElm |
| `OTAElm.java` | Operational transconductance amplifier with transistor-level model | CompositeElm with 17 transistors and 2 rail elements |
| `OpAmpElm.java` | Ideal op-amp with configurable gain, output limits, and clipping | Base for OpAmpSwapElm; used in ComparatorElm |
| `OpAmpRealElm.java` | Transistor-level op-amp models (741, LM324) with slew rate/limits | CompositeElm with internal transistors, resistors, capacitors |
| `OpAmpSwapElm.java` | Op-amp variant with swapped +/- input terminals for placement | Extends OpAmpElm with FLAG_SWAP |
| `VCCSElm.java` | Voltage-controlled current source with expression-based output | Base for VCVSElm, CCCSElm, CCVSElm; uses Expr |
| `VCVSElm.java` | Voltage-controlled voltage source with expression-based output | Extends VCCSElm; outputs voltage instead of current |

## elements/electronics/digital

| File | What It Is For | Close Relationships |
|---|---|---|
| `ADCElm.java` | Analog-to-digital converter chip converting analog input to binary | ChipElm base; pairs with DACElm |
| `AndGateElm.java` | AND logic gate outputting true only when all inputs are true | GateElm subclass; related to NandGateElm |
| `Counter2Elm.java` | Binary counter with load, enable, and RCO pins (74163-style) | ChipElm; related to CounterElm, RingCounterElm |
| `CounterElm.java` | Configurable binary up/down counter with reset and modulus | ChipElm; related to Counter2Elm, RingCounterElm |
| `CustomLogicElm.java` | User-definable logic chip executing custom truth tables | ChipElm; uses CustomLogicModel for behavior |
| `DACElm.java` | Digital-to-analog converter chip converting binary to analog voltage | ChipElm base; pairs with ADCElm |
| `DFlipFlopElm.java` | D flip-flop storing data input on clock edge with Q/Q̄ outputs | ChipElm; related to JKFlipFlopElm, TFlipFlopElm, LatchElm |
| `DeMultiplexerElm.java` | Routes single input to one of multiple outputs based on select | ChipElm; pairs with MultiplexerElm |
| `DecimalDisplayElm.java` | Displays binary input pins as decimal number on chip face | ChipElm; related to SevenSegElm |
| `DelayBufferElm.java` | Digital buffer with configurable time delay before output changes | CircuitElm; uses GateElm rendering |
| `FullAdderElm.java` | N-bit binary adder with carry-in and carry-out | ChipElm; related to HalfAdderElm |
| `GateElm.java` | Abstract base class for all logic gates (AND, OR, XOR, etc.) | Base for AndGateElm, OrGateElm, XorGateElm |
| `HalfAdderElm.java` | Two-input binary adder producing sum and carry outputs | ChipElm; related to FullAdderElm |
| `InverterElm.java` | NOT gate inverting digital input with slew rate control | CircuitElm; related to GateElm, InvertingSchmittElm |
| `InvertingSchmittElm.java` | Schmitt trigger inverter with hysteresis and thresholds | CircuitElm; base for SchmittElm |
| `JKFlipFlopElm.java` | JK flip-flop with toggle capability on clock edges | ChipElm; related to DFlipFlopElm, TFlipFlopElm |
| `LatchElm.java` | N-bit transparent latch storing inputs when load pin is active | ChipElm; related to DFlipFlopElm |
| `LogicInputElm.java` | Clickable digital input switch providing high/low/ternary voltage | SwitchElm subclass; pairs with LogicOutputElm |
| `LogicOutputElm.java` | Displays high/low/ternary state based on voltage threshold | CircuitElm; pairs with LogicInputElm |
| `MonostableElm.java` | One-shot pulse generator triggered by rising edge with delay | ChipElm; related to TimerElm |
| `MultiplexerElm.java` | Selects one of multiple inputs to route to single output | ChipElm; pairs with DeMultiplexerElm |
| `NandGateElm.java` | NAND gate: inverted AND output | AndGateElm subclass with inversion |
| `NorGateElm.java` | NOR gate: inverted OR output | OrGateElm subclass with inversion |
| `OrGateElm.java` | OR logic gate outputting true when any input is true | GateElm subclass; base for NorGateElm |
| `PhaseCompElm.java` | Phase comparator detecting phase difference between signals (PLL) | ChipElm; used in phase-locked loop circuits |
| `PisoShiftElm.java` | Parallel-in serial-out shift register | ChipElm; pairs with SipoShiftElm |
| `RingCounterElm.java` | Decade/ring counter with one-hot output (CD4017-style) | ChipElm; related to CounterElm |
| `SRAMElm.java` | Static RAM chip with address/data buses and read/write control | ChipElm; uses SRAMLoadFile for data |
| `SchmittElm.java` | Non-inverting Schmitt trigger with hysteresis for noise immunity | InvertingSchmittElm subclass |
| `SeqGenElm.java` | Programmable sequence generator outputting stored bit patterns | ChipElm; related to shift registers |
| `SevenSegDecoderElm.java` | BCD to 7-segment decoder converting 4-bit input to segments | ChipElm; pairs with SevenSegElm |
| `SevenSegElm.java` | 7-segment LED display with optional decimal point and diode model | ChipElm; used with SevenSegDecoderElm |
| `SipoShiftElm.java` | Serial-in parallel-out shift register | ChipElm; pairs with PisoShiftElm |
| `TFlipFlopElm.java` | Toggle flip-flop changing state on clock when T input is high | ChipElm; related to DFlipFlopElm, JKFlipFlopElm |
| `TimerElm.java` | 555 timer IC with threshold/trigger comparators and discharge | ChipElm; related to MonostableElm, VCOElm |
| `TriStateElm.java` | Tri-state buffer with enable control for bus isolation | CircuitElm; related to gates and bus circuits |
| `VCOElm.java` | Voltage-controlled oscillator generating frequency from input voltage | ChipElm; used in PLL circuits with PhaseCompElm |
| `XorGateElm.java` | XOR gate outputting true when odd number of inputs are true | OrGateElm subclass |

## elements/electronics/electromechanical

| File | What It Is For | Close Relationships |
|---|---|---|
| `CrossSwitchElm.java` | 2-pole crossover switch that swaps connections between circuits | Extends SwitchElm; similar to DPDTSwitchElm |
| `CustomTransformerElm.java` | Configurable multi-coil transformer with user-defined ratios and taps | Uses Inductor; extends TransformerElm concepts |
| `DCMotorElm.java` | DC motor with electrical (inductance) and mechanical (torque) modeling | Uses Inductor; related to ThreePhaseMotorElm |
| `DPDTSwitchElm.java` | Double-pole double-throw switch with configurable pole count | Extends SwitchElm; similar to Switch2Elm, CrossSwitchElm |
| `MBBSwitchElm.java` | Make-before-break switch contacting new position before releasing old | Extends SwitchElm; related to Switch2Elm |
| `MotorProtectionSwitchElm.java` | 3-phase motor protection with I²t thermal fuse/trip behavior | Related to ThreePhaseMotorElm |
| `OptocouplerElm.java` | Composite LED/phototransistor for optical isolation | Extends CompositeElm; uses DiodeElm, TransistorElm |
| `PushSwitchElm.java` | Momentary push-button switch (normally open, closes when pressed) | Extends SwitchElm; simplest momentary variant |
| `RelayCoilElm.java` | Relay coil with inductance; controls RelayContactElm by label | Uses Inductor; paired with RelayContactElm |
| `RelayContactElm.java` | SPDT relay contact switch controlled by matching RelayCoilElm | Paired with RelayCoilElm; extends CircuitElm |
| `RelayElm.java` | All-in-one relay with integrated coil and multi-pole SPDT contacts | Uses Inductor; combines coil/contact functionality |
| `Switch2Elm.java` | Single-pole double-throw (SPDT) switch with center-off option | Extends SwitchElm; basis for DPDTSwitchElm |
| `SwitchElm.java` | Base SPST switch class; parent for all switch element types | Parent of Switch2Elm, PushSwitchElm, DPDTSwitchElm |
| `TappedTransformerElm.java` | Center-tapped transformer with 5 terminals (primary + tapped secondary) | Related to TransformerElm, CustomTransformerElm |
| `ThreePhaseMotorElm.java` | 3-phase AC induction motor with 6 terminals and rotating field | Related to DCMotorElm, MotorProtectionSwitchElm |
| `TimeDelayRelayElm.java` | Relay with configurable on/off time delays before switching | Extends ChipElm; related to RelayElm |
| `TransformerElm.java` | Two-winding transformer with inductance, ratio, and coupling | Base for TappedTransformerElm, CustomTransformerElm |

## elements/electronics/measurement

| File | What It Is For | Close Relationships |
|---|---|---|
| `AmmeterElm.java` | Measures current flow, displays instantaneous or RMS values | Extends CircuitElm; similar to WattmeterElm |
| `AudioOutputElm.java` | Records voltage samples and plays back as WAV audio via browser | Extends CircuitElm; uses JS Blob/Audio creation |
| `DataRecorderElm.java` | Records voltage data points and exports as downloadable text file | Extends CircuitElm; uses JS Blob API |
| `OhmMeterElm.java` | Measures resistance using current injection and voltage drop | Extends CurrentElm; displays resistance in ohms |
| `OutputElm.java` | Single-terminal voltage output display for plotting coordinates | Extends CircuitElm; supports SCALE_AUTO |
| `ProbeElm.java` | Two-terminal voltage probe measuring V, RMS, max/min, frequency | Extends CircuitElm; similar to TestPointElm |
| `TestPointElm.java` | Single-terminal test point measuring V, RMS, frequency with label | Extends CircuitElm; similar to ProbeElm |
| `WattmeterElm.java` | Four-terminal power meter measuring watts via V and I sensing | Extends CircuitElm; uses internal voltage sources |

## elements/electronics/misc

| File | What It Is For | Close Relationships |
|---|---|---|
| `CrystalElm.java` | Quartz crystal oscillator with RLC equivalent circuit model | CircuitElm; standalone crystal element |
| `CustomCompositeChipElm.java` | User-defined subcircuit chip with customizable pins and appearance | Extends CompositeElm; uses CustomCompositeModel |
| `CustomCompositeElm.java` | User-defined subcircuit with box display and internal elements | Extends CompositeElm; uses CustomCompositeModel |
| `FuseElm.java` | Current-limiting fuse that blows when current exceeds rating | CircuitElm; standalone protective element |
| `LDRElm.java` | Light-dependent resistor with configurable light level | CircuitElm; standalone photoresistor |
| `LEDArrayElm.java` | Multiple LEDs in series with single current draw visualization | LED family; extends DiodeElm behavior |
| `LEDElm.java` | Light-emitting diode with color and brightness visualization | LED family; extends DiodeElm |
| `LampElm.java` | Incandescent lamp with thermal inertia and brightness modeling | CircuitElm; standalone lamp element |
| `MemristorElm.java` | Memory resistor with state-dependent resistance | CircuitElm; standalone memristor |
| `SparkGapElm.java` | Gas discharge tube with breakdown voltage threshold | CircuitElm; standalone spark gap |
| `StopTriggerElm.java` | Stops simulation when input voltage crosses threshold | CircuitElm; related to StopTimeElm |
| `ThermistorNTCElm.java` | Negative temperature coefficient thermistor | CircuitElm; temperature-dependent resistor |
| `TransLineElm.java` | Transmission line with delay, impedance, and loss parameters | CircuitElm; delay-based model |

## elements/electronics/passives

| File | What It Is For | Close Relationships |
|---|---|---|
| `CapacitorElm.java` | Stores charge using trapezoidal/Euler integration with optional ESR | Extends CircuitElm; base for PolarCapacitorElm |
| `InductorElm.java` | Stores energy in magnetic field using Inductor helper class | Extends CircuitElm; delegates to Inductor |
| `PolarCapacitorElm.java` | Polarized capacitor with max reverse voltage and polarity check | Extends CapacitorElm; adds polarity checking |
| `PotElm.java` | Three-terminal variable resistor with interactive slider control | Extends CircuitElm; uses Scrollbar |
| `ResistorElm.java` | Basic linear resistor with US zigzag or Euro rectangle style | Extends CircuitElm; uses stampResistor() |

## elements/electronics/semiconductors

| File | What It Is For | Close Relationships |
|---|---|---|
| `DarlingtonElm.java` | Compound element creating Darlington pair from two transistors | TransistorElm, CompositeElm, NDarlingtonElm, PDarlingtonElm |
| `DiacElm.java` | Bidirectional trigger diode with breakover voltage and holding current | Diode, SCRElm, TriacElm (thyristor family) |
| `DiodeElm.java` | Base diode element with configurable model, forward drop, ESR | Diode, DiodeModel, ZenerElm, VaractorElm |
| `EditDiodeModelDialog.java` | Dialog for editing diode model parameters and creating new models | DiodeElm, DiodeModel, EditDialog |
| `EditTransistorModelDialog.java` | Dialog for editing transistor model parameters and creating new models | TransistorElm, TransistorModel, EditDialog |
| `JfetElm.java` | Junction FET with gate diode extending MOSFET base | MosfetElm, Diode, NJfetElm, PJfetElm |
| `MosfetElm.java` | Metal-Oxide-Semiconductor FET with threshold, beta, body diodes | Diode, JfetElm, NMosfetElm, PMosfetElm |
| `NDarlingtonElm.java` | NPN Darlington pair - wrapper passing pnp=false | DarlingtonElm, PDarlingtonElm |
| `NJfetElm.java` | N-channel JFET - wrapper passing pnp=false | JfetElm, PJfetElm |
| `NMosfetElm.java` | N-channel MOSFET - wrapper passing pnp=false | MosfetElm, PMosfetElm |
| `NTransistorElm.java` | NPN BJT transistor - wrapper passing pnp=false | TransistorElm, PTransistorElm |
| `PDarlingtonElm.java` | PNP Darlington pair - wrapper passing pnp=true | DarlingtonElm, NDarlingtonElm |
| `PJfetElm.java` | P-channel JFET - wrapper passing pnp=true | JfetElm, NJfetElm |
| `PMosfetElm.java` | P-channel MOSFET - wrapper passing pnp=true | MosfetElm, NMosfetElm |
| `PTransistorElm.java` | PNP BJT transistor - wrapper passing pnp=true | TransistorElm, NTransistorElm |
| `SCRElm.java` | Silicon-Controlled Rectifier thyristor with gate trigger | Diode, TriacElm, DiacElm (thyristor family) |
| `TransistorElm.java` | Bipolar Junction Transistor with Ebers-Moll model and beta | TransistorModel, NTransistorElm, PTransistorElm |
| `TriacElm.java` | Bidirectional thyristor with gate, using back-to-back diodes | Diode, SCRElm, DiacElm (thyristor family) |
| `TriodeElm.java` | Vacuum tube triode with plate, grid, cathode and mu/kg1 | CircuitElm (standalone vacuum tube) |
| `TunnelDiodeElm.java` | Tunnel (Esaki) diode with negative resistance region | CircuitElm, DiodeElm (quantum tunneling) |
| `UnijunctionElm.java` | Unijunction transistor using CompositeElm with diode and sources | CompositeElm, DiodeElm, VCCS, CCVS |
| `VaractorElm.java` | Variable-capacitance diode with voltage-dependent capacitance | DiodeElm, DiodeModel, capacitor behavior |
| `ZenerElm.java` | Zener diode extending DiodeElm with breakdown voltage | DiodeElm, DiodeModel |

## elements/electronics/sources

| File | What It Is For | Close Relationships |
|---|---|---|
| `ACRailElm.java` | Single-terminal AC sinusoidal voltage source to ground | Extends RailElm, dumps as RailElm class |
| `ACVoltageElm.java` | Two-terminal AC sinusoidal voltage source between terminals | Extends VoltageElm, dumps as VoltageElm class |
| `AMElm.java` | Amplitude modulated voltage source with carrier and signal | Standalone CircuitElm; uses SimulationContext for time |
| `AntennaElm.java` | Simulated antenna signal with multiple AM/FM frequencies mixed | Extends RailElm; uses SimulationContext for time |
| `AudioInputElm.java` | Reads audio file data and outputs as voltage waveform | Extends RailElm; uses browser AudioContext API |
| `ClockElm.java` | Square wave clock source with 5V swing at 100Hz default | Extends RailElm; uses FLAG_CLOCK for display |
| `CurrentElm.java` | Constant current source between two terminals | Standalone CircuitElm; checked for broken path |
| `DCVoltageElm.java` | Two-terminal DC constant voltage source | Extends VoltageElm, dumps as VoltageElm class |
| `DataInputElm.java` | Reads text file data and outputs values as voltage | Extends RailElm; uses FileReader browser API |
| `ExtVoltageElm.java` | External voltage source controlled by name from external code | Extends RailElm; used for external system integration |
| `FMElm.java` | Frequency modulated voltage source with configurable deviation | Standalone CircuitElm; uses SimulationContext for time |
| `NoiseElm.java` | Random noise voltage source using WF_NOISE waveform | Extends RailElm; generates random values per timestep |
| `RailElm.java` | Base single-terminal voltage source with multiple waveform types | Extends VoltageElm; base for ACRailElm, ClockElm, etc. |
| `SquareRailElm.java` | Single-terminal square wave voltage source to ground | Extends RailElm, dumps as RailElm class |
| `SweepElm.java` | Frequency sweep voltage source from minF to maxF over time | Standalone CircuitElm; supports log and bidirectional |
| `TableVoltageElm.java` | Gets voltage from ComputedValues registry by name | Extends RailElm; used with TableElm/GodlyTableElm |
| `VarRailElm.java` | User-adjustable DC rail with slider control in UI panel | Extends RailElm; uses Scrollbar for adjustment |
| `VoltageElm.java` | Base two-terminal voltage source supporting DC/AC/square/pulse | Base class for all voltage sources |

## elements/electronics/wiring

| File | What It Is For | Close Relationships |
|---|---|---|
| `GroundElm.java` | Reference ground point with multiple symbol styles | Standalone CircuitElm; stamps 0V or acts as marker |
| `LabeledNodeElm.java` | Named node for connecting distant circuit points by label | Standalone CircuitElm; uses static HashMap for lookup |
| `WireElm.java` | Zero-resistance wire connection between two points | Standalone CircuitElm; wire-equivalent for analysis |

## elements/math

| File | What It Is For | Close Relationships |
|---|---|---|
| `AdderElm.java` | Sums multiple input voltages using linear VCVS | Standalone CircuitElm; uses voltage source for output |
| `DifferentiatorElm.java` | Computes derivative dV/dt of input voltage over time | Uses ExprState; nonlinear element requiring subiterations |
| `DivideConstElm.java` | Divides input voltage by a constant divisor (linear) | Standalone CircuitElm; linear VCVS with fixed gain |
| `DividerElm.java` | Divides first input by subsequent inputs (nonlinear) | Standalone CircuitElm; MIN_DENOMINATOR guard |
| `IntegratorElm.java` | Integrates input voltage over time with initial value | Uses ExprState; supports initial value from input pin |
| `MultiplyConstElm.java` | Multiplies input voltage by a constant gain (linear) | Standalone CircuitElm; linear VCVS with fixed gain |
| `MultiplyElm.java` | Multiplies multiple input voltages together (nonlinear) | Standalone CircuitElm; nonlinear requiring subiterations |
| `ODEElm.java` | ODE calculator with user equation and integration over time | Uses ExprState; references LabeledNodeElm by name |
| `PercentElm.java` | Computes (V1/V2/...) × 100 as percentage (nonlinear) | Standalone CircuitElm; extends DividerElm pattern |
| `SubtracterElm.java` | Subtracts inputs from first input using linear VCVS | Standalone CircuitElm; uses voltage source for output |

## elements/misc

| File | What It Is For | Close Relationships |
|---|---|---|
| `ActionTimeElm.java` | Displays ActionScheduler scheduled actions status visually | Uses ActionScheduler; opens ActionTimeDialog on double-click |
| `PieChartDialog.java` | Dialog for editing pie chart slice names and colors | Used by PieChartElm; provides slice configuration UI |
| `PieChartElm.java` | Displays pie chart of labeled node voltage values | Extends GraphicElm; references LabeledNodeElm by name |
| `ScopeElm.java` | Embedded oscilloscope displaying waveforms from circuit elements | Wraps Scope class; no electrical connections |
| `StopTimeElm.java` | Stops simulation at configured time, optionally opens Plotly | Standalone CircuitElm; can open ScopeViewerDialog |
| `ViewportElm.java` | Defines visible area for circuit loading/centering | Extends BoxElm; used for consistent circuit display |

## io

| File | What It Is For | Close Relationships |
|---|---|---|
| `ClipboardManager.java` | Extends ClipboardManagerCore for GWT-specific clipboard operations | CirSim, ClipboardManagerCore, cut/copy/paste |
| `ImportExportHelper.java` | Imports/exports circuit text, dumps settings and lookup tables | CirSim, LookupTableRegistry, circuit serialization |
| `ImportFromDropbox.java` | Loads circuit files from Dropbox using JavaScript interop | CirSim, Dropbox API via JSInterop |
| `InfoViewerContentBuilder.java` | Extracts and merges inline markdown documentation from SFCR text | SFCRParser/Exporter, model info, markdown processing |
| `LoadFile.java` | Browser file upload handler for loading circuit files | CirSim, FileReader API, GWT FileUpload widget |
| `LookupBlocksTextUtil.java` | Parses and merges @lookup blocks in SFCR documents | SFCRParser, LookupTableRegistry, lookup editing |
| `LookupDefinition.java` | Data class holding lookup table name, scope, x/y values | LookupTableRegistry, Expr.lookup(), SFCR format |
| `LookupTableRegistry.java` | Global runtime registry for lookup tables used by Expr.lookup() | LookupDefinition, SFCRParser, expression evaluation |
| `SFCRBlockCommentRegistry.java` | Creates composite keys for block comments by type and name | SFCRParser, SFCRDocumentState, block comment storage |
| `SFCRDocumentManager.java` | Manages SFCR document state and model info menu items | SFCRDocumentState, MenuItem, model info UI |
| `SFCRDocumentState.java` | Holds document metadata: block comments, model info, current file | SFCRDocumentManager, circuit file state |
| `SFCRExporter.java` | Exports circuit to human-readable SFCR text format | SFCRParser, EquationTableElm, GodlyTableElm, SFCTableElm |
| `SFCRParseResult.java` | Plain-Java result object with parsed init settings and block dumps | SFCRParser, unit testing without GWT dependencies |
| `SFCRParseResultExporter.java` | Exports SFCRParseResult back to SFCR text format | SFCRParseResult, round-trip testing |
| `SFCRParser.java` | Parses SFCR text format into circuit elements (@equations, @matrix) | SFCRExporter, EquationTableElm, SFCTableElm, HintRegistry |
| `SFCRUtil.java` | Shared utilities for SFCR parsing/exporting (sanitization, escaping) | SFCRParser, SFCRExporter, RowOutputMode |
| `SRAMLoadFile.java` | Loads binary data files into SRAM chip elements | SRAMElm, FileReader ArrayBuffer API |
| `SetupListLoader.java` | Extends SetupListLoaderCore for loading circuit menus | SetupListLoaderCore, circuit example menu population |

## registry

| File | What It Is For | Close Relationships |
|---|---|---|
| `ElementCategory.java` | Enum categorizing elements: ELECTRONICS, ECONOMICS, MATH, UI_SUPPORT | ElementRegistry, element classification system |
| `ElementFactoryFacade.java` | Unified facade for creating circuit elements by dump type or class | ElementRegistry, ElementLegacyFactory, instantiation |
| `ElementLegacyFactory.java` | Legacy switch-based element factory for all dump type codes | ElementFactoryFacade, all CircuitElm subclasses |
| `ElementRegistry.java` | Central registry mapping dump types and class names to factories | ElementFactoryFacade, ElementRegistryBootstrap |
| `ElementRegistryBootstrap.java` | Registers all circuit elements into ElementRegistry at startup | ElementRegistry, all element classes |
| `HintRegistry.java` | Global storage for variable hints/tooltips (glossary) | SFCRParser, EquationTableElm, LabeledNodeElm, scopes |
| `TableMasterRegistryManager.java` | Registers table elements as stock masters in priority order | TableElm, ComputedValues, stock ownership resolution |

## runner

| File | What It Is For | Close Relationships |
|---|---|---|
| `CircuitJavaRunner.java` | CLI for running simulations headless on JVM (non-GWT) | CirSim, ComputedValues, SimulationExportCore, batch |
| `RunnerJsBridge.java` | JavaScript interop bridge exposing runner step function to browser | GWT JSInterop, browser automation hooks |
| `RunnerLaunchDecision.java` | Determines runner launch route: embedded text or missing dump key | Runner startup logic, step count defaults |
| `RunnerPanelUi.java` | GWT UI panel for displaying runner output and status | SimulationExportCore, RootPanel, runner HTML output |
| `RuntimeMode.java` | Flag distinguishing GWT browser mode vs non-interactive JVM mode | CircuitJavaRunner, headless detection |

## test

| File | What It Is For | Close Relationships |
|---|---|---|
| `CircuitTestRunner.java` | JUnit test infrastructure for loading circuits and validating results | CirSim, voltage/current assertions, convergence testing |
| `MathElementsTest.java` | Test suite for math elements: Adder, Multiply, Integrator, ODE | CircuitTestRunner, AdderElm, MultiplyElm, IntegratorElm |
| `TableElementsTest.java` | Test suite for table elements: GodlyTableElm, stock-flow models | CircuitTestRunner, GodlyTableElm, economic modeling |

## ui

| File | What It Is For | Close Relationships |
|---|---|---|
| `AboutBox.java` | Popup dialog displaying version info and about.html iframe | PopupPanel, SessionStorage for version |
| `Checkbox.java` | Localized checkbox wrapper extending GWT CheckBox | Locale, GWT CheckBox |
| `CheckboxMenuItem.java` | Menu item with checkmark state and keyboard shortcut support | MenuItem, CirSim menu system |
| `Choice.java` | Localized dropdown list wrapper extending GWT ListBox | Locale, GWT ListBox |
| `Dialog.java` | Base dialog class with keyboard event handling and close logic | DialogBox, CirSimDialogCoordinator |
| `EconomicsToolbar.java` | Toolbar with economic modeling components (tables, equations, charts) | Toolbar, MyCommand, economics elements |
| `EditCompositeModelDialog.java` | Dialog for designing custom composite subcircuit chip appearance | CustomCompositeModel, ChipElm, Pin editing |
| `EditDialog.java` | Primary dialog for editing circuit element properties | Editable, EditInfo, AutocompleteHelper |
| `EditDialogActions.java` | Coordinates opening edit dialogs, sliders, and export actions | EditDialog, SliderDialog, CirSim |
| `EditDialogLoadFile.java` | Abstract file upload handler for loading files into edit dialogs | FileUpload, ChangeHandler |
| `EditInfo.java` | Data class holding editable parameter info (name, value, widget) | EditDialog, TextBox, Choice, Checkbox |
| `EditOptions.java` | Editable interface for global simulator preferences (colors, timestep) | Editable, CirSim settings, Storage |
| `ElectronicsToolbar.java` | Toolbar with electronic components (resistors, capacitors, transistors) | Toolbar, MyCommand, electronics elements |
| `ExportAsImageDialog.java` | Export circuit diagram as PNG or SVG image download | Canvas, Dialog, data URL encoding |
| `ExportAsLocalFileDialog.java` | Export circuit as downloadable .txt file using Blob API | Dialog, download anchor, Blob |
| `ExportAsSFCRDialog.java` | Export circuit as SFCR format text (block or R-style) | SFCRExporter, TextArea, RadioButton |
| `ExportAsTextDialog.java` | Export circuit dump as plain text with copy to clipboard | TextArea, clipboard execCommand |
| `ExportAsUrlDialog.java` | Export circuit as shareable URL with optional URL shortening | LZString compression, short relay URL |
| `ExportScopeDataDialog.java` | Export scope waveform data in CSV or JSON format | Scope, RadioButton, format options |
| `FloatingControlPanel.java` | Draggable control panel with Run/Stop/Reset/Step buttons | RootPanel, fullscreen toggle, drag handlers |
| `HintEditorDialog.java` | Dialog for editing variable hints (glossary) in HintRegistry | HintRegistry, FlexTable, DialogBox |
| `IframeViewerDialog.java` | Non-modal resizable dialog embedding iframe for documentation | DialogBox, iframe, ResizeObserver |
| `ImportFromDropboxDialog.java` | Import circuits from Dropbox via chooser or shared link | ImportFromDropbox, XMLHttpRequest |
| `ImportFromTextDialog.java` | Paste circuit text dump to import into simulator | TextArea, importCircuitFromText |
| `InfoDialogActions.java` | Coordinates opening model info, test dialogs, and reference docs | InfoViewerDialog, IframeViewerDialog |
| `InfoViewerDialog.java` | Display markdown documentation with marked.js rendering | DialogBox, markdown, InfoViewerHtmlBuilder |
| `InfoViewerHtmlBuilder.java` | Generates HTML/CSS for markdown viewer with live data support | InfoViewerDialog, CSS styling, exports |
| `InfoViewerSimpleMarkdown.java` | Fallback simple markdown-to-HTML converter (no external libs) | InfoViewerDialog, string processing |
| `LookupTablesEditorDialog.java` | Dialog for editing @lookup table blocks in SFCR format | SFCRParser, LookupBlocksTextUtil |
| `MathElementsTestDialog.java` | Non-modal test runner dialog for math element tests | MathElementsTest, DialogBox, console capture |
| `MenuUiState.java` | Container for menu bar references and checkbox menu item state | MenuBar, ScopePopupMenu, MenuItem vectors |
| `ReferenceDocs.java` | Opens markdown reference documents in iframe viewer | IframeViewerDialog, RequestBuilder |
| `ScopePopupMenu.java` | Right-click context menu for scope operations (dock, stack, export) | MenuBar, CheckboxMenuItem, Scope |
| `ScopeViewerDialog.java` | Opens scope data in interactive Plotly.js chart window | Scope, Plotly template, new window |
| `ScrollValuePopup.java` | Mouse scroll popup to adjust element values (E12 series) | PopupPanel, CircuitElm, wheel events |
| `Scrollbar.java` | Custom scrollbar widget with canvas rendering and touch support | Canvas, mouse/touch handlers, Composite |
| `SearchDialog.java` | Search/filter component list by name to add elements | ListBox, TextBox, mainMenuItems |
| `ShortcutsDialog.java` | Edit keyboard shortcuts for menu items | FlexTable, ScrollPanel, TextBox |
| `SliderDialog.java` | Add adjustable sliders for element parameters | Adjustable, EditInfo, CircuitElm |
| `SubcircuitDialog.java` | Manage and delete custom subcircuit models | CustomCompositeModel, ListBox |
| `TableElementsTestDialog.java` | Non-modal test runner for table element tests | TableElementsTest, DialogBox |
| `Toolbar.java` | Abstract base class for economics/electronics toolbars | HorizontalPanel, setModeLabel |
| `VariableBrowserDialog.java` | Non-modal browser showing all circuit variables for placement | FlexTable, LabeledNodeElm, stocks/parameters |

## util

| File | What It Is For | Close Relationships |
|---|---|---|
| `AutocompleteHelper.java` | Bash-style Tab completion for TextBox fields with cycling | TextBox, Label, completion state |
| `Color.java` | RGB color class with hex conversion and standard constants | Graphics, hex strings |
| `FFT.java` | Fast Fourier Transform implementation for scope spectrum analysis | Scope frequency display |
| `Font.java` | Simple font descriptor (name, style, size) for canvas rendering | Graphics, text rendering |
| `Graphics.java` | Canvas 2D drawing wrapper with colors, shapes, text caching | Context2d, Color, Font |
| `IntPair.java` | Immutable pair of two integers with equals/hashCode | General utility |
| `Locale.java` | Localization/translation system with Greek symbol mapping | Localization map, UI strings |
| `NumFmt.java` | Number formatter supporting patterns for GWT and non-GWT modes | NumberFormat, decimal/scientific |
| `PerfMonitor.java` | Performance timing helper tracking nested context durations | performance.now, profiling |
| `Point.java` | 2D integer coordinate point class | CircuitElm geometry |
| `Polygon.java` | Dynamic integer polygon with expandable point arrays | Graphics rendering, fillPolygon |
| `QueryParameters.java` | URL query string parser to HashMap | URL decode, startup options |
| `Rectangle.java` | 2D integer rectangle with bounds checking and intersection | CircuitElm bounding boxes |
| `StringTokenizer.java` | GWT-compatible string tokenizer (GNU Classpath port) | Circuit dump parsing |
