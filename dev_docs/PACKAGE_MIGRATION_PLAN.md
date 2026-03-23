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
├── electronics/ # Electronic circuit elements
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

### Economics-Coupled Dialogs
- Move economics-specific dialogs together with economics elements, same package/directory:
  - `TableEditDialog.java`
  - `TableMarkdownDebugDialog.java`
  - `EquationTableEditDialog.java`
  - `EquationTableMarkdownDebugDialog.java`
  - `ActionTimeDialog.java`
  - `PieChartDialog.java` (if kept economics-coupled)

### Visibility Promotions Expected
- Many package-private methods become public
- Consider `@VisibleForTesting` annotations

---

## PR 7: `client.electronics`

**Largest batch — split into sub-PRs:**

1. **Passives**: ResistorElm, CapacitorElm, InductorElm, PotElm
2. **Sources**: VoltageElm, CurrentElm, RailElm variants, NoiseElm
3. **Semiconductors**: DiodeElm, TransistorElm, MosfetElm, JfetElm, etc.
4. **Digital**: GateElm subclasses, ChipElm, FlipFlops, etc.
5. **Misc**: Switches, Relays, Transformers, etc.

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

---

## Next Action

Implement **PR 1** — `getClassName()` fix with tests.
