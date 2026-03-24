# Package Migration Plan

## Overview

Restructure `com.lushprojects.circuitjs1.client` into coherent sub-packages to improve maintainability, onboarding, and compile-time isolation.

**Target Structure:**
```
client/
├── core/        # CirSim, solver, matrix, simulation loop
├── registry/    # Element registry, bootstrap, factories
├── io/          # SFCR parser/exporter, import/export helpers
├── ui/          # Dialogs, toolbar, scope UI
├── expr/        # Expression parser/evaluator
├── annotation/  # Visual-only schematic elements (TextElm, LineElm, BoxElm, GraphicElm)
├── electronics/ # Electronic circuit elements
│   ├── passives/
│   ├── sources/
│   ├── digital/
│   ├── semiconductors/
│   ├── analog/
│   ├── electromechanical/
│   ├── measurement/
│   ├── wiring/
│   └── misc/
├── economics/   # Stock-flow economic elements
├── math/        # Mathematical operation elements
└── util/        # (already exists) Locale, PerfMonitor
```

---

## PR 1: Prep — `getClassName()` Fix (No Package Moves)

**Goal:** Remove hardcoded package prefix assumption, add regression tests.

### Changes

**File:** `CircuitElm.java` line 1384

```java
// BEFORE:
String getClassName() { return getClass().getName().replace("com.lushprojects.circuitjs1.client.", ""); }

// AFTER:
String getClassName() {
    String name = getClass().getName();
    int idx = name.lastIndexOf('.');
    return idx >= 0 ? name.substring(idx + 1) : name;
}
```

### Tests Required

1. **Serialization round-trip test** (`test/java/.../SerializationRoundTripTest.java`)
   - Create circuit with multiple element types
   - Export to dump string
   - Import and verify all elements have correct class names
   - Verify dump types match before/after

2. **JS API type string test** (`test/java/.../JsApiTypeStringTest.java`)
   - Verify `getClassName()` returns simple name for:
     - `ResistorElm` → `"ResistorElm"`
     - `SFCTableElm` → `"SFCTableElm"`
     - Nested classes if any

### Acceptance Criteria
- [ ] `./gradlew test` passes
- [ ] `./gradlew compileGwt` succeeds
- [ ] Manual test: Export circuit, reload, verify identical

---

## PR 2: Pilot — `client.registry` Package

**Goal:** Move registry classes to validate migration tooling with zero-dependency batch.

### Files to Move

| Current Location | New Location |
|------------------|--------------|
| `client/ElementRegistry.java` | `client/registry/ElementRegistry.java` |
| `client/ElementRegistryBootstrap.java` | `client/registry/ElementRegistryBootstrap.java` |
| `client/ElementCategory.java` | `client/registry/ElementCategory.java` |
| `client/ElementFactoryFacade.java` | `client/registry/ElementFactoryFacade.java` |
| `client/ElementLegacyFactory.java` | `client/registry/ElementLegacyFactory.java` |

### Dependency Analysis

| File | CirSim Refs | Action |
|------|-------------|--------|
| ElementRegistry.java | 0 | Clean move |
| ElementRegistryBootstrap.java | 0 | Clean move |
| ElementCategory.java | 0 | Clean move |
| ElementFactoryFacade.java | 1 (`CirSim.console()`) | Accept for now, fix in Phase 7 |
| ElementLegacyFactory.java | 0 | Clean move |

### Visibility Changes Required

1. `ElementRegistry.Entry` — make `public` (needed by ElementFactoryFacade consumers)
2. `ElementRegistry.NameLookupResult` — make `public`
3. `ElementCategory` — already `public`

### Import Updates

Search for files importing from old location:
```bash
grep -r "import.*ElementRegistry\|import.*ElementCategory\|import.*ElementFactoryFacade\|import.*ElementLegacyFactory\|import.*ElementRegistryBootstrap" src/
```

Expected updates:
- `CircuitIOService.java`
- `CirSimDiagnostics.java`
- Any test files

### Steps

1. Create directory: `src/com/lushprojects/circuitjs1/client/registry/`
2. Move files with `git mv`
3. Update package declarations
4. Update imports in consuming files
5. Promote visibility where needed
6. Verify GWT module compiles (`<source path='client'>` includes subpackages)

### Acceptance Criteria
- [ ] `./gradlew test` passes
- [ ] `./gradlew compileGwt` succeeds
- [ ] `ElementRegistry.getEntryByClassKey("ResistorElm")` returns non-null
- [ ] Element creation by dump type unchanged (regression test)
- [ ] Element creation by class key unchanged (regression test)

---

## PR 3: `client.io` Package

### Files to Move

| File | CirSim Refs | Notes |
|------|-------------|-------|
| SFCRParser.java | 18 (console + sim field) | Keep sim reference, console → accept |
| SFCRExporter.java | 3 (sim field) | Keep sim reference |
| SFCRParseResult.java | 0 | Clean |
| SFCRParseResultExporter.java | ? | Check |
| SFCRUtil.java | ? | Check |
| SFCRBlockCommentRegistry.java | ? | Check |
| ImportExportHelper.java | ? | Check |
| CircuitIOService.java | Many | Keep sim reference |

### Acceptance Criteria
- [ ] SFCR parse → export round-trip test passes
- [ ] Circuit import/export unchanged

---

## PR 4: `client.ui` Package

### Files to Move (partial list)
- Only cross-cutting/global `*Dialog.java` files (import/export/search/shortcuts/viewer shells)
- `Toolbar.java`, `EconomicsToolbar.java`, `ElectronicsToolbar.java`
- `ScopeManager.java`, `ScopePopupMenu.java`
- `FloatingControlPanel.java`
- `ScrollValuePopup.java`

### Placement Rule (Dialogs)
- If a dialog is tightly coupled to a specific `*Elm`, keep/move it with that element domain package.
- For this migration, colocate element-coupled dialogs in the **same directory/package** as their element classes (no `electronics/ui` or `economics/ui` subdir requirement).
- `client.ui` is for shared/global UI, not element-internal editors.

### Acceptance Criteria
- [ ] All dialogs open correctly
- [ ] Toolbar switching works

### PR 4 Follow-up: Temporary `CirSim` Bridge Cleanup

**Why this exists:** During dialog moves from `client` → `client.ui`, package-private access breaks.  
Temporary `CirSim` bridge methods are allowed to keep PR4 incremental and compile-safe.

**Bridge inventory (to review/remove later):**
- [ ] `isElectron()`
- [ ] `getCircuitAsCanvasForExport(int)`
- [ ] `getCircuitAsSvgForExport()`
- [ ] `getScopesAsCanvasForExport()`
- [ ] `getCacImageType()`
- [ ] `repaintFromUi()`
- [ ] `reimportCircuitTextFromDialog(String)`
- [ ] `importCircuitTextFromDialog(String, boolean)`
- [ ] `loadCircuitFromExternalText(String, String)`
- [ ] `openDropboxChooserFromDialog()`
- [ ] `getSearchableMainMenuItemNames()`
- [ ] `executeMainMenuItemByName(String)`
- [ ] `getShortcutMenuItemCount()`
- [ ] `getShortcutMenuItemName(int)`
- [ ] `getShortcutMenuItemValue(int)`
- [ ] `applyShortcutMenuItemValues(Vector<String>)`
- [ ] `alertOrWarnFromDialog(String)`
- [ ] `getUserSubcircuitNames()`
- [ ] `removeSubcircuitByName(String)`
- [ ] `getFloatingScopeCountForViewer()`
- [ ] `getFloatingScopeForViewer(int)`
- [ ] `getAllLabeledNodeNamesForPieChart()`
- [ ] `requestAnalyzeFromDialog()`
- [ ] `getSortedLabeledNodeNames()`
- [ ] `getCircuitAreaHeight()`
- [ ] `getGridSize()`
- [ ] `inverseTransformXForUi(double)`
- [ ] `inverseTransformYForUi(double)`
- [ ] `snapGridForUi(int)`
- [ ] `createLabeledNodeElementForUi(int, int, String, int)`
- [ ] `addElementForUi(CircuitElm)`
- [ ] `clearSelectionForUi()`
- [ ] `selectElementForUi(CircuitElm, boolean)`
- [ ] `needAnalyzeForUi()`
- [ ] `repaintForUi()`
- [ ] `findCanvasTestLabelForUi(String[])`
- [ ] `getInstance()`
- [ ] `isCacheBustedUrlsEnabled()`

**Cleanup rules (PR5/PR8):**
1. If a bridge only forwards to one manager call, replace with a dedicated UI/service interface and remove the bridge.
2. If multiple dialogs need the same operation, move it to a stable service API (not `CirSim`), then remove dialog-specific bridge names.
3. Keep only bridges that are intentionally part of public JS/app API surface; rename to non-dialog-specific names.
4. No new bridge methods after PR4 unless added to this list with explicit removal plan.

---

## PR 5: Interface Boundaries Before Element Moves

**Goal:** Introduce interfaces to decouple elements from CirSim internals.

### New Interfaces

**`client/core/SimulationContext.java`:**
```java
public interface SimulationContext {
    // Matrix stamping
    void stampResistor(int n1, int n2, double r);
    void stampConductance(int n1, int n2, double g);
    void stampMatrix(int i, int j, double x);
    void stampRightSide(int i, double x);
    void stampRightSide(int i);
    void stampNonLinear(int i);
    void stampVoltageSource(int n1, int n2, int vs, double v);
    void stampVoltageSource(int n1, int n2, int vs);
    void updateVoltageSource(int n1, int n2, int vs, double v);
    void stampCurrentSource(int n1, int n2, double i);
    void stampVCCurrentSource(int cn1, int cn2, int vn1, int vn2, double g);
    void stampCCCS(int n1, int n2, int vs, double gain);
    void stampVCVS(int n1, int n2, double coef, int vs);
    
    // Simulation state
    double getTimeStep();
    double getTime();
    boolean isConverged();
    void setConverged(boolean converged);
}
```

**`client/core/ConfigProvider.java`:**
```java
public interface ConfigProvider {
    boolean isEquationTableMnaMode();
    boolean isSfcrLookupClampDefault();
    double getEquationTableConvergenceTolerance();
}
```

**`client/core/ConsoleLogger.java`:**
```java
public interface ConsoleLogger {
    void log(String message);
}
```

### Migration Path
1. CirSim implements these interfaces
2. Elements receive interface references instead of CirSim
3. Gradual migration — not all at once

### PR5 Completion Status
- [x] `SimulationContext`, `ConfigProvider`, and `ConsoleLogger` introduced under `client/core`
- [x] `CirSim` exposes stable time/config/convergence accessors used by migrated code paths
- [x] Major element/helper stamp + timing + convergence paths migrated off direct `sim.getTimingState().t/timeStep` and direct `sim.converged` field use
- [x] Remaining direct timing-state usage is intentional and scoped to core timestep bookkeeping (`timeStepCount`, `timeStepAccum`) and `CirSim` timing accessor internals

---

## PR 6: `client.math` + `client.economics`

### Required Order (Do Not Skip)
1. Visibility-prep first:
   - Promote required package-private APIs/types used by moved classes (starting with `CircuitElm`/`ChipElm`, then table-support types as needed).
   - Ensure cross-package subclassing/overrides remain valid before any package declaration changes.
2. Package moves second:
   - Move one coherent batch at a time (`math` or `economics` subset).
   - In the same batch, update package declarations and all imports/usages.
3. Gate each batch before continuing:
   - `./gradlew compileJava`
   - `./gradlew test`
   - `./gradlew compileGwt`
   - smoke test load/save

### Staging Rule
- Path-only moves are not allowed as an intermediate state for PR6.
- A batch is only complete when visibility + package + imports are all updated and gates pass.

### Package Move Execution Guidelines (applies to PR6/PR7/PR8 element moves)
1. Do a visibility inventory before moving files:
   - List package-private classes/constructors/methods/fields touched by target elements.
   - Promote only what cross-package compilation actually requires, then re-run gates.
2. Move one domain batch atomically:
   - In a single batch, do `git mv` + package declaration updates + all import/reference updates.
   - Do not land temporary states with moved paths but old package declarations.
3. Treat inheritance hooks as a migration hotspot:
   - When subclasses move packages, overridden methods in `CircuitElm`/`ChipElm` hierarchies often need protected/public visibility alignment.
   - Hotspot signatures to pre-check before any move: `drag`, `setNodeVoltage`, `canViewInScope`, plus common draw/stamp hooks.
   - Prefer systematic signature checks over ad-hoc fixes.
4. Avoid blind bulk regex edits for method signatures:
   - Restrict replacements to explicit symbols/files, then compile immediately.
   - If doing wide edits, review diffs for known fragile signatures (`setVoltageSource`, stamp/draw hooks, etc.).
5. Update registry/bootstrap/tests in the same batch:
   - Keep `ElementLegacyFactory`, `ElementRegistryBootstrap`, and package-specific tests aligned with new imports before running full builds.
6. Use explicit Gradle gates for this project:
   - Run `./gradlew compileJava`, then `./gradlew test`, then `./gradlew compileGwt`.
 7. Keep PR6 bridge/API growth intentional:
   - If visibility promotion broadens API surface, document why in the PR description and mark candidates for later tightening after package migration stabilizes.
8. Use a strict batch fallback rule:
   - If a proposed move requires broad visibility expansion into solver/node/core internals, split the batch immediately and ship only the low-coupling subset.
9. Follow domain ownership over current package location:
   - If an element is conceptually in another domain (for example a source element), defer it to that domain PR even if currently under `client` or economics-adjacent code.

### Batch Checklist (Repeat For Each Migration Batch)
- [ ] Run pre-move visibility inventory for touched classes and base hooks.
- [ ] Apply move atomically: `git mv` + package declaration + imports/usages in the same change.
- [ ] Verify inheritance hooks compile (`draw/stamp/doStep/reset/setPoints/getInfo/getPost` families).
- [ ] Update registry/bootstrap/tests references in the same batch.
- [ ] Run gates: `./gradlew compileJava`, `./gradlew test`, `./gradlew compileGwt`.
- [ ] Run smoke test: load/save for at least one migrated element in the batch.
- [ ] Record any temporary visibility expansions with follow-up tightening notes.

### Math Elements
- `AdderElm.java`
- `SubtracterElm.java`
- `MultiplyElm.java`
- `DividerElm.java`
- `MultiplyConstElm.java`
- `DivideConstElm.java`
- `IntegratorElm.java`
- `DifferentiatorElm.java`
- `ODEElm.java`
- `PercentElm.java`

### Economics Elements
- `TableElm.java` + `TableRenderer.java` + `TableDataManager.java` etc.
- `GodlyTableElm.java`
- `EquationTableElm.java` + helpers
- `SFCTableElm.java` + `SFCTableRenderer.java`
- `SFCStockElm.java`, `SFCFlowElm.java`
- `CurrentTransactionsMatrixElm.java` + renderer
- `ScenarioElm.java`
- `StockMasterElm.java`, `FlowsMasterElm.java`
- `ComputedValues.java`, `ComputedValueSourceElm.java`
- `StockFlowRegistry.java`
- `EquationElm.java`
- `SFCSankeyElm.java`

### PR6 Scope Note
- `TableVoltageElm.java` is intentionally **excluded** from PR6 economics batches.
- Treat `TableVoltageElm` as an electronics source element and migrate it with PR7 Sources.

### Economics-Coupled Dialogs
- Move economics-specific dialogs together with economics elements, same package/directory:
  - `TableEditDialog.java`
  - `TableMarkdownDebugDialog.java`
  - `EquationTableEditDialog.java`
  - `EquationTableMarkdownDebugDialog.java`
  - `ActionTimeDialog.java`
  - `PieChartDialog.java` (if kept economics-coupled)

### Debug Tooling Policy
- Keep heavy debug dialogs/tools in `client` unless there is a dedicated cross-package debug/service API.
- Do not move debug tooling in element batches by default; migrate them in a dedicated bridge/API batch once required internals are intentionally exposed.

### Visibility Promotions Expected
- Many package-private methods become public
- Consider `@VisibleForTesting` annotations

---

## PR 7: `client.electronics`

**Largest batch — split into sub-PRs:**

Use the **Package Move Execution Guidelines** and **Batch Checklist** above in PR6 for each PR7 sub-batch.

### PR7 Migration Checklist (Live)

Completed batches:
- [x] **Passives** → `com.lushprojects.circuitjs1.client.electronics.passives`
- [x] **Sources** → `com.lushprojects.circuitjs1.client.electronics.sources`
- [x] **Digital** (gates/chips/hybrid digital family) → `com.lushprojects.circuitjs1.client.electronics.digital`
- [x] **Electromechanical (switch family)** → `com.lushprojects.circuitjs1.client.electronics.electromechanical`
  - `SwitchElm.java`
  - `PushSwitchElm.java`
  - `Switch2Elm.java`
  - `DPDTSwitchElm.java`
  - `MBBSwitchElm.java`
  - `CrossSwitchElm.java`
- [x] **Semiconductors (transistor family)** → `com.lushprojects.circuitjs1.client.electronics.semiconductors`
  - `TransistorElm.java`
  - `NTransistorElm.java`
  - `PTransistorElm.java`
  - `DarlingtonElm.java`
  - `NDarlingtonElm.java`
  - `PDarlingtonElm.java`
- [x] **Semiconductors (diodes/FETs family)** → `com.lushprojects.circuitjs1.client.electronics.semiconductors`
  - `DiodeElm.java`
  - `ZenerElm.java`
  - `MosfetElm.java`
  - `NMosfetElm.java`
  - `PMosfetElm.java`
  - `JfetElm.java`
  - `NJfetElm.java`
  - `PJfetElm.java`
  - `SCRElm.java`
  - `DiacElm.java`
  - `TriacElm.java`
  - `TriodeElm.java`
  - `UnijunctionElm.java`
  - `TunnelDiodeElm.java`
  - `VaractorElm.java`
- [x] **Electromechanical (relay/motor/transformer family)** → `com.lushprojects.circuitjs1.client.electronics.electromechanical`
  - `RelayElm.java`
  - `RelayCoilElm.java`
  - `RelayContactElm.java`
  - `TimeDelayRelayElm.java`
  - `MotorProtectionSwitchElm.java`
  - `OptocouplerElm.java`
  - `TransformerElm.java`
  - `TappedTransformerElm.java`
  - `CustomTransformerElm.java`
  - `DCMotorElm.java`
  - `ThreePhaseMotorElm.java`
- [x] **Analog family** → `com.lushprojects.circuitjs1.client.electronics.analog`
  - `OpAmpElm.java`
  - `OpAmpSwapElm.java`
  - `OpAmpRealElm.java`
  - `OTAElm.java`
  - `ComparatorElm.java`
  - `AnalogSwitchElm.java`
  - `AnalogSwitch2Elm.java`
  - `VCVSElm.java`
  - `VCCSElm.java`
  - `CCVSElm.java`
  - `CCCSElm.java`
  - `CC2Elm.java`
  - `CC2NegElm.java`
- [x] **Measurement family** → `com.lushprojects.circuitjs1.client.electronics.measurement`
  - `OutputElm.java`
  - `ProbeElm.java`
  - `OhmMeterElm.java`
  - `AmmeterElm.java`
  - `WattmeterElm.java`
  - `AudioOutputElm.java`
  - `DataRecorderElm.java`
  - `TestPointElm.java`
- [x] **Wiring family** → `com.lushprojects.circuitjs1.client.electronics.wiring`
  - `WireElm.java`
  - `GroundElm.java`
  - `LabeledNodeElm.java`
- [x] **Annotation family** → `com.lushprojects.circuitjs1.client.annotation`
  - `TextElm.java`
  - `LineElm.java`
  - `BoxElm.java`
  - `GraphicElm.java`
- [x] **Misc family (low-coupling subset)** → `com.lushprojects.circuitjs1.client.electronics.misc`
  - `TransLineElm.java`
  - `CrystalElm.java`
  - `FuseElm.java`
  - `SparkGapElm.java`
  - `MemristorElm.java`
  - `ThermistorNTCElm.java`
  - `LDRElm.java`
  - `LampElm.java`
  - `LEDElm.java`
  - `LEDArrayElm.java`
  - `StopTriggerElm.java`

**Next recommended batch:** PR7 electronics migration complete; continue with deferred UI-support ownership batch as listed below.

Remaining component moves (check off as completed):

#### Semiconductors → `client.electronics.semiconductors`
Diodes and FETs (transistors already completed above):
- [x] `DiodeElm.java`
- [x] `ZenerElm.java`
- [x] `MosfetElm.java`
- [x] `NMosfetElm.java`
- [x] `PMosfetElm.java`
- [x] `JfetElm.java`
- [x] `NJfetElm.java`
- [x] `SCRElm.java`
- [x] `DiacElm.java`
- [x] `TriacElm.java`
- [x] `TriodeElm.java`
- [x] `UnijunctionElm.java`
- [x] `TunnelDiodeElm.java`
- [x] `VaractorElm.java`

#### Analog → `client.electronics.analog`
Op-amps, controlled sources, and analog switches:
- [x] `OpAmpElm.java`
- [x] `OpAmpSwapElm.java`
- [x] `OpAmpRealElm.java`
- [x] `OTAElm.java`
- [x] `ComparatorElm.java`
- [x] `AnalogSwitchElm.java`
- [x] `AnalogSwitch2Elm.java`
- [x] `VCVSElm.java`
- [x] `VCCSElm.java`
- [x] `CCVSElm.java`
- [x] `CCCSElm.java`
- [x] `CC2Elm.java`

#### Electromechanical → `client.electronics.electromechanical`
Relays, transformers, motors, and isolation devices (switches already completed above):
- [x] `RelayElm.java`
- [x] `RelayCoilElm.java`
- [x] `RelayContactElm.java`
- [x] `TimeDelayRelayElm.java`
- [x] `MotorProtectionSwitchElm.java`
- [x] `OptocouplerElm.java`
- [x] `TransformerElm.java`
- [x] `TappedTransformerElm.java`
- [x] `CustomTransformerElm.java`
- [x] `DCMotorElm.java`
- [x] `ThreePhaseMotorElm.java`

#### Measurement → `client.electronics.measurement`
Meters, probes, and data recording:
- [x] `OutputElm.java`
- [x] `ProbeElm.java`
- [x] `OhmMeterElm.java`
- [x] `AmmeterElm.java`
- [x] `WattmeterElm.java`
- [x] `AudioOutputElm.java`
- [x] `DataRecorderElm.java`
- [x] `TestPointElm.java`

#### Wiring → `client.electronics.wiring`
Basic connectivity elements:
- [x] `WireElm.java`
- [x] `GroundElm.java`
- [x] `LabeledNodeElm.java`

#### Annotation → `client.annotation` (top-level, no electrical behavior)
Visual-only schematic elements:
- [x] `TextElm.java`
- [x] `LineElm.java`
- [x] `BoxElm.java`
- [x] `GraphicElm.java`

#### Misc → `client.electronics.misc`
Remaining specialized components:
- [x] `TransLineElm.java`
- [x] `CrystalElm.java`
- [x] `FuseElm.java`
- [x] `SparkGapElm.java`
- [x] `MemristorElm.java`
- [x] `ThermistorNTCElm.java`
- [x] `LDRElm.java`
- [x] `LampElm.java`
- [x] `LEDElm.java`
- [x] `LEDArrayElm.java`
- [x] `CustomCompositeElm.java`
- [x] `CustomCompositeChipElm.java`
- [x] `StopTriggerElm.java`

#### UI-Support Batch (`client.miscElm`)
- [x] `ScopeElm.java` → `client.miscElm`
- [x] `ViewportElm.java` → `client.miscElm`
- [x] `StopTimeElm.java` → `client.miscElm`
- [x] `ActionTimeElm.java` → `client.miscElm`
- [x] `PieChartElm.java` → `client.miscElm`

### PR7a Sources Target Package Map
- Use subpackage: `com.lushprojects.circuitjs1.client.electronics.sources`
- Move:
  - `VoltageElm.java`
  - `RailElm.java`
  - `VarRailElm.java`
  - `CurrentElm.java`
  - `NoiseElm.java`
  - `AMElm.java`
  - `FMElm.java`
  - `SweepElm.java`
  - `AntennaElm.java`
  - `TableVoltageElm.java` (deferred from PR6 by design)
- Update compile-time consumers in the same batch:
  - `ElementLegacyFactory.java`
  - `ElementRegistryBootstrap.java`
  - `CirSimMenuBuilder.java`
  - any parser/exporter classes that directly instantiate source elements

### Electronics-Coupled Dialogs
- Move electronics-specific dialogs together with electronics elements, same package/directory:
  - `ScopePropertiesDialog.java`
  - `SliderDialog.java`
  - Other element editor dialogs that primarily serve electronics elements

---

## PR 8: `client.core` Finalization

### Files to Move
- `CirSim.java` (stays as main coordinator)
- `CircuitAnalyzer.java`
- `CircuitMatrixOps.java`
- `LUSolver.java`
- `SimulationLoop.java`
- `SimulationTimingState.java`
- `SolverMatrixState.java`
- `MatrixStamper.java`

### Final Cleanup
- Remove temporary compatibility shims
- Finalize interface implementations
- Update all imports
- Reconcile any PR4 temporary dialog moves so element-coupled dialogs end in their domain packages

---

## Execution Rules

1. **One phase per PR** — no mixed refactor/features
2. **Preserve serialization** — dump format unchanged at every phase
3. **Gate each phase:**
   - `./gradlew test` passes
   - `./gradlew compileGwt` succeeds
   - Manual smoke test (circuit load/save)
4. **If phase fails gate:** revert only that phase, split smaller
5. **No `CirSim.console()` logging removal yet** — defer to final cleanup

---

## Quick Reference: Dependency Check Commands

```bash
# Check CirSim references in a file
grep -n "CirSim" src/com/lushprojects/circuitjs1/client/SomeFile.java

# Check package-private usage
grep -n "package-private\|default access" src/com/lushprojects/circuitjs1/client/

# Find all imports of a class
grep -rn "import.*ElementRegistry" src/

# Verify GWT compilation
./gradlew compileGwt

# Run specific test
./gradlew test --tests "*ElementRegistry*"
```

## Optional Migration Automation (Perl + git mv)

Use this only for **mechanical moves** (file path + package/import rewrites).  
Do **not** rely on it for visibility/API fallout; those still require manual compile-fix loops.

### Good fit
- Moving a coherent set of files from one package to another.
- Rewriting well-known imports/usages with exact string replacements.

### Not a good fit
- Batches that require `public/protected` promotions.
- Subclass override access alignment (`attempting to assign weaker access privileges` errors).
- Core API boundary changes (`CircuitElm`, `ChipElm`, `CirSim`, dialogs/services).

### Template script
```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT="src/com/lushprojects/circuitjs1/client/electronics"
FROM="$ROOT/misc"
TO="$ROOT/electromechanical"

FILES=(
  SwitchElm.java
  PushSwitchElm.java
  Switch2Elm.java
  DPDTSwitchElm.java
  MBBSwitchElm.java
  CrossSwitchElm.java
)

mkdir -p "$TO"

for f in "${FILES[@]}"; do
  git mv "$FROM/$f" "$TO/$f"
  perl -0pi -e \
    's/package com\.lushprojects\.circuitjs1\.client\.electronics\.misc;/package com.lushprojects.circuitjs1.client.electronics.electromechanical;/' \
    "$TO/$f"
done

# Example focused usage rewrites (edit list per batch)
perl -0pi -e \
  's/com\.lushprojects\.circuitjs1\.client\.electronics\.misc\.SwitchElm/com.lushprojects.circuitjs1.client.electronics.electromechanical.SwitchElm/g' \
  src/com/lushprojects/circuitjs1/client/CirSim.java \
  src/com/lushprojects/circuitjs1/client/MouseInputHandler.java \
  src/com/lushprojects/circuitjs1/client/electronics/digital/LogicInputElm.java

perl -0pi -e \
  's/import com\.lushprojects\.circuitjs1\.client\.electronics\.misc\.\*;/import com.lushprojects.circuitjs1.client.electronics.electromechanical.*;/' \
  src/com/lushprojects/circuitjs1/client/ElementLegacyFactory.java
```

### Mandatory gates after each automated batch
1. `./gradlew compileJava`
2. `./gradlew test`
3. `./gradlew compileGwt`

If any gate fails, stop and fix manually before continuing.

-----
