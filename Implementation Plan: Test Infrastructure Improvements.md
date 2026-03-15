

## Implementation Plan: Test Infrastructure Improvements

### Step 1 — Wire test resources in Gradle *(10 minutes, zero risk)*

**File**: build.gradle

Add `resources.srcDirs` to the test source set so tests can use `getClass().getResourceAsStream(...)` instead of the fragile `projectDir` system property + `Paths.get` workaround:

```gradle
test {
    java      { srcDirs = ['test/java'] }
    resources { srcDirs = ['test/resources'] }
}
```

Then replace the boilerplate in `SFCRParseResultTest`, `SFCRMixedModeRegressionTest`, and any future test that loads a fixture:

```java
// Before (in every test class):
String projectDir = System.getProperty("projectDir");
assertNotNull(projectDir, ...);
Path f = Paths.get(projectDir, "test", "resources", "sfcr", "foo.md");
assertTrue(Files.exists(f), ...);
String text = new String(Files.readAllBytes(f));

// After (one-liner anywhere):
String text = TestFixtures.loadSfcr("foo.md");
```

The `projectDir` system property in build.gradle `tasks.withType(Test)` can stay — it costs nothing and may still be useful — but it should no longer be required by tests.

**Deliverables**:
- build.gradle: add resources srcDir
- `test/java/.../TestFixtures.java`: helper with `loadSfcr(String name)` and a generic `loadResource(String path)` that uses `getResourceAsStream`
- Update `SFCRParseResultTest.loadAndParse()` and `SFCRMixedModeRegressionTest` to use the helper

---

### Step 2 — Fix JVM bombs in StockFlowRegistry *(30 minutes, low risk)*

**File**: src/.../StockFlowRegistry.java

Two separate problems:

**2a. JSNI native loggers (lines 44–56)** crash with `UnsatisfiedLinkError` on the JVM. Replace all three with a single conditional that is dead code under GWT compilation:

```java
// Replace all three native methods with:
static void log(String method, String message) {
    // no-op on JVM; GWT browser logging via CirSim.console in callers
}
static void MRDlog(String message) { }
static void SRTlog(String message)  { }
```

If actual log output in the browser is still wanted, the call sites can use `CirSim.console(...)` directly — which is already the pattern in the rest of the codebase. The native JSNI block approach is incompatible with JVM tests.

**2b. `CirSim.theSim` access in `getAllEquationOutputNames()` (line 170) and one other method (line 199)** — these pull a live simulator reference. Extract a package-private `setSimProvider(Supplier<CirSim>)` or simply guard with a null check and return an empty set when `theSim` is null. The null-check approach is strictly less invasive and sufficient for test isolation.

**Deliverables**:
- Remove/replace three JSNI native methods
- Null-guard `CirSim.theSim` dereferences in `getAllEquationOutputNames()` and the other method at line 199
- `StockFlowRegistryTest` should pass without any mocking or stubbing after this change

---

### Step 3 — Fix parallel-test race conditions on static state *(1 hour, medium risk)*

**Context**: build.gradle runs tests with `maxParallelForks = cpus/2`. Both `ComputedValues` and `StockFlowRegistry` are entirely static. Tests in different classes that touch these statics can interfere.

The right fix is not a full DI refactor yet — just add `@Isolated` annotations (JUnit 5 `@Isolated` or `@ResourceLock`) to the test classes that mutate static state, to serialise them without changing the production code:

```java
// In StockFlowRegistryTest, ComputedValuesTest, etc:
@ResourceLock("StockFlowRegistry")
class StockFlowRegistryTest { ... }

@ResourceLock("ComputedValues")
class ComputedValuesTest { ... }
```

This is sufficient immediately. The injectable-store refactor (original Q&A item #3) becomes a follow-on once tests are stable — it is the right long-term direction but carries real risk of breaking GWT runtime behaviour if done in a single sweep.

**Deliverables**:
- Add `@ResourceLock` annotations to all test classes touching static registries
- Add JUnit Jupiter `junit-jupiter:5.10.2` already present — `@ResourceLock` is in `junit-jupiter-api`, no new dependency needed

---

### Step 4 — Add `SFCRParser` strict mode *(45 minutes, low risk)*

**File**: src/.../SFCRParser.java

`SFCRParserRobustnessTest` already exists and tests tolerant-mode behaviour. There is no way to test fail-fast behaviour because the parser has no strict-mode flag.

Add a package-private overload:

```java
// New signature:
static SFCRParseResult parseToResult(String text, boolean strict) { ... }

// Existing signature delegates:
public static SFCRParseResult parseToResult(String text) {
    return parseToResult(text, false);
}
```

In strict mode, any malformed block header, unrecognised token, or missing `@end` throws `SFCRParser.ParseException` (a new unchecked inner class) instead of silently skipping.

**Deliverables**:
- `ParseException` inner class in `SFCRParser`
- `parseToResult(String, boolean)` overload with strict branching at the existing skip/warn sites
- New `@Test` methods in `SFCRParserRobustnessTest` using `assertThrows(SFCRParser.ParseException.class, ...)`

---

### Step 5 — Add TestFixtureBuilder for StockFlowRegistry *(1 hour, no production code change)*

**Context**: `StockFlowRegistryTest` currently seeds `stockToTables` by grabbing the private field via reflection and inserting `null` `TableElm` entries. This works but is brittle — it breaks if the field is renamed or the map type changes.

`StockFlowRegistry.registerStock(String, TableElm)` already accepts `null` as a table (the test calls it that way). Expose a test-only builder or just document that `null` is a valid sentinel for "anonymous table" in the registry, and rewrite the test to use the public API only:

```java
// Instead of reflection:
StockFlowRegistry.registerStock("Cash", null);
StockFlowRegistry.registerStock("Cash", null);  // second registration → shared
assertTrue(StockFlowRegistry.isSharedStock("Cash"));
```

Check all reflection-based field access in tests and replace with public API calls wherever possible. Only keep reflection if there is no public path.

**Deliverables**:
- Rewrite reflection-based setup in `StockFlowRegistryTest` to use `registerStock(name, null)` for the anonymous-entry tests
- If any test genuinely needs internal state, create a `StockFlowRegistry.TestSupport` package-private inner class rather than raw reflection

---

### Step 6 — Expr and ExprState unit tests *(1–2 hours, new tests only)*

**Files**: src/.../Expr.java, src/.../ExprState.java (if it exists as a separate class)

Currently zero test coverage on the expression parser and evaluator despite it being pure Java with no GWT dependencies. Minimum coverage needed:

| Area | Tests |
|---|---|
| Arithmetic: `+`, `-`, `*`, `/`, `^` | known-output assertions |
| Built-ins: `sin`, `cos`, `exp`, `log`, `sqrt`, `abs`, `min`, `max` | spot checks |
| `integrate(x)` over fixed timesteps | assert accumulated value matches $\sum x \cdot dt$ |
| `diff(x)` | assert derivative approximation over two steps |
| `ExprState.commitIntegration()` boundary | assert commit vs pending separation |
| `ExprState.reset()` | assert integration state zeroes |
| Division by zero, `log(0)`, `sqrt(-1)` | no exception, defined output (NaN/Inf acceptable) |
| Parse error on invalid expression | defined behaviour (null or exception) |

**Deliverables**:
- `test/java/.../ExprTest.java`
- Uses `ExprState` directly, no CirSim, no GWT

---

### Step 7 — `StockTableView` interface + registry decoupling *(half day, medium risk)*

This is the largest structural change and should come last. Steps 2–5 make it easier to validate.

**Current problem**: `StockFlowRegistry` holds `Map<String, List<TableElm>>` which forces every test involving registry logic to either instantiate `TableElm` (GWT-dependent) or use `null` sentinels.

**Approach**:
1. Define `interface StockTableView { String[] getColumnNames(); String[] getRowDescriptions(); void onSharedStockAdded(String stock); }` in its own file
2. Have `TableElm implements StockTableView`
3. Change `StockFlowRegistry` internals from `List<TableElm>` to `List<StockTableView>`
4. Update all call sites — the runtime behaviour is unchanged because `TableElm` already satisfies the interface
5. In tests, use a simple anonymous `StockTableView` implementation instead of `null`

This unlocks writing `StockFlowRegistryTest` cases that test synchronisation logic (the `synchronizeRelatedTables` path) which is currently completely untestable without a real `TableElm`.

**Deliverables**:
- New file `StockTableView.java`
- `StockFlowRegistry` field type change
- `TableElm implements StockTableView` (plus any removed `null` guards)
- Updated `StockFlowRegistryTest` replacing `null` entries with inline anonymous implementations
- Verify `./gradlew compileGwt` still succeeds

---

### Execution order summary

| Step | Effort | Risk | Unblocks |
|---|---|---|---|
| 1. Wire test resources | 10 min | None | All fixture-loading tests |
| 2. Fix JSNI + CirSim in Registry | 30 min | Low | Steps 5, 7 |
| 3. `@ResourceLock` parallel safety | 20 min | None | Stable CI |
| 4. Parser strict mode | 45 min | Low | Robustness tests |
| 5. Remove reflection in RegistryTest | 1 hr | None | Step 7 |
| 6. Expr/ExprState tests | 1–2 hr | None | — |
| 7. StockTableView interface | half day | Medium | Full registry testing |

Steps 1–4 can be done in any order in a single session. Step 7 should be a separate PR after Steps 2 and 5 are merged and the test suite is green.

---

## Phase 2 — Equation / Godley Table Testing

**Current state**: `EquationTableSemantics` and `StockFlowTableSemantics` pure helpers already exist with initial tests. `StockTableView` is implemented and `TableElm implements StockTableView`. The remaining barriers to full Equation/Godley unit testing are:

1. `synchronizeElements(TableElm, TableElm)` is coupled to concrete `TableElm` — unreachable from tests without constructing real GWT elements.
2. Row evaluation handlers (`VoltageModeHandler`, `FlowModeHandler`, `ParamModeHandler`) are private inner classes of `EquationTableElm` — completely opaque to tests.
3. `ComputedValues` and `StockFlowRegistry` are fully static, forcing test classes to share and manually reset global state.
4. `ExprState.integrate()` / `diff()` reads `sim.timeStep` — making `ExprTest` implicitly simulator-coupled.

---

### Step 8 — `TableContentView`: extend read interface for sync *(45 minutes, low risk)*

**Situation**: `synchronizeElements` reads `getCellEquation(row,col)`, `findColumnByStockName(name)`, and `getInitialValue(col)` from both tables — but these methods exist only on `TableElm`, not on `StockTableView`. This is the single reason the sync engine requires concrete element instances.

**Approach**: Add a package-private interface that extends `StockTableView`:

```java
// New file: TableContentView.java
interface TableContentView extends StockTableView {
    String getCellEquation(int row, int col);
    int findColumnByStockName(String stockName);
    double getInitialValue(int col);
}
```

`TableElm` already implements all three methods — add `implements TableContentView` to its declaration (one-line change). Change `synchronizeElements` parameter types from `TableElm` to `TableContentView`. The public signatures of `synchronizeTable` and `synchronizeRelatedTables` are unchanged.

No logic changes needed. The only new testability: tests can now pass anonymous `TableContentView` implementations to the sync engine.

**Deliverables**:
- New `TableContentView.java`
- `TableElm implements TableContentView` (declaration change only)
- `synchronizeElements` parameter types: `TableElm` → `TableContentView`

---

### Step 9 — Two-phase sync: `SyncPatch` + `computeSyncPatches()` *(1–2 hours, medium risk)*

**Context**: `synchronizeElements` is a 160-line private method that simultaneously computes which changes are needed *and* applies them via direct mutation (`setCellEquation`, `setRowDescription`, `resizeTable`, `setInitialConditionValue`). It cannot be tested without triggering side effects.

**Approach**: Split into a pure compute phase and a thin apply phase:

```java
// Phase 1 — pure (new, testable, takes TableContentView):
static List<SyncPatch> computeSyncPatches(TableContentView source, TableContentView target) { ... }

// Phase 2 — impure (thin wrapper, all logging goes here):
static void applySyncPatches(List<SyncPatch> patches, TableElm target) { ... }

// Existing private method becomes a one-liner (behaviour unchanged):
private static boolean synchronizeElements(TableElm target, TableElm source) {
    List<SyncPatch> patches = computeSyncPatches(source, target);
    if (patches.isEmpty()) return false;
    applySyncPatches(patches, target);
    return true;
}
```

`SyncPatch` is a package-private value class. GWT doesn't support sealed types, so use a tagged approach:

```java
final class SyncPatch {
    enum Kind { SET_CELL_EQUATION, ADD_ROW, SET_INITIAL_VALUE }
    final Kind kind;
    final int row, col;
    final String flowDesc, equation;
    final Map<Integer, String> rowEquations;  // ADD_ROW only
    final double initialValue;                // SET_INITIAL_VALUE only

    static SyncPatch setCellEquation(int row, int col, String eq) { ... }
    static SyncPatch addRow(String flowDesc, Map<Integer,String> eqs) { ... }
    static SyncPatch setInitialValue(int col, double value) { ... }
}
```

`computeSyncPatches` must be strictly pure: no `MRDlog` calls, no side effects. All logging moves into `applySyncPatches`.

**Deliverables**:
- New `SyncPatch.java`
- `StockFlowRegistry.computeSyncPatches(TableContentView, TableContentView)` extracted from the body of `synchronizeElements`
- `StockFlowRegistry.applySyncPatches(List<SyncPatch>, TableElm)` holding all logging and mutation
- `synchronizeElements` delegates to both — behaviour identical, zero logic in the bridge method
- Initial `SyncPatchComputationTest.java` with two anonymous `TableContentView` stubs proving the extraction is correct

---

### Step 10 — Golden fixture tests for sync patches *(1 hour, no production code change)*

**Requires**: Steps 8 and 9

Define a `TableContentViewStub` test helper (inner class or separate test-utility class) that holds all read-only table content as plain arrays:

```java
class TableContentViewStub implements TableContentView {
    final String title;
    final String[] headers;
    final String[] rowDescs;
    final String[][] equations;  // [row][col]
    final double[] initValues;
    // all interface methods wired to array lookups
    public int findColumnByStockName(String name) { /* linear scan over headers */ }
}
```

Six golden scenarios, each a standalone `@Test`:

| Scenario | Expected patch list |
|---|---|
| New flow in source, absent in target | exactly one `ADD_ROW("Wages", ...)` |
| Existing flow, equation changed | exactly one `SET_CELL_EQUATION` with new value |
| Equation cleared (deletion sync) | `SET_CELL_EQUATION` with `""` |
| Initial value drift | `SET_INITIAL_VALUE(col, newValue)` |
| Tables identical (already in sync) | empty list |
| Stock absent in target | no patch emitted for that stock |

**Deliverables**:
- `TableContentViewStub.java` (shared test utility)
- `SyncPatchGoldenTest.java` with one `@Test` per scenario
- All pass under `./gradlew test`

---

### Step 11 — Sync property tests (idempotence, self no-op, empty-source safety) *(1 hour, no production code change)*

**Requires**: Steps 8 and 9

Three pure-function properties verified entirely via `TableContentViewStub` — no `TableElm` involved:

1. **Idempotence**: compute patches from `(A→B)`; create stub `B'` representing B with patches applied; then `computeSyncPatches(A, B')` → empty list.
2. **Self no-op**: `computeSyncPatches(A, A)` where both stubs have identical content → empty list.
3. **Empty-source safety**: when source has all blank equations, no `SET_CELL_EQUATION` wipes a non-empty target equation — new rows are only created for non-empty source equations, by design.

**Deliverables**:
- `SyncPropertyTest.java`: `@Test void idempotence()`, `@Test void selfNoOp()`, `@Test void emptySourceDoesNotWipe()`

---

### Step 12 — `ComputedValues.resetForTesting()` *(15 minutes, zero risk)*

Quick win to eliminate fragile `@BeforeEach` reflection-based state manipulation and potentially retire the `@ResourceLock` annotations from Step 3.

```java
// ComputedValues.java — package-private:
static void resetForTesting() {
    currentValues.clear();
    pendingValues.clear();
    convergedValues.clear();
    computedNames.clear();
    // clear masterTables registry if stored here
}
```

Replace any `@BeforeEach` reflection-based field access in `ComputedValuesTest` and `StockFlowRegistryTest` with a direct call to `resetForTesting()`. The private-field reflection access is then no longer needed, removing a maintenance coupling.

**Deliverables**:
- `ComputedValues.resetForTesting()` package-private method
- `ComputedValuesTest` and `StockFlowRegistryTest` `@BeforeEach` updated to call it instead of using reflection
- Check whether `@ResourceLock` annotations from Step 3 can be removed

---

### Step 13 — Row-evaluation extraction into `EquationTableSemantics` *(half day, medium risk)*

**Context**: The numeric computation inside `VoltageModeHandler.evaluate(int row)`, `FlowModeHandler.evaluate(int row)`, and `ParamModeHandler.evaluate(int row)` is completely opaque to tests. They are private inner classes of `EquationTableElm` that read live node voltages, stamp the MNA matrix, and publish to `ComputedValues` in one pass. There is no way to test the computed value without running a full simulation.

The minimum extraction: move just the *numerical computation* into new static methods on the existing `EquationTableSemantics` class. Stamping and publishing remain in the handlers — they become thin delegators:

```java
// New in EquationTableSemantics:
static double computeFlowRowValue(
        Expr compiledExpr, ExprState state,
        double[] volts, int sourceNodeIdx, int targetNodeIdx,
        double shuntResistance) { ... }

static double computeVoltageRowValue(
        Expr compiledExpr, ExprState state) { ... }

static double computeParamRowValue(
        Expr compiledExpr, ExprState state) { ... }
```

Pure functions: `Expr`, `ExprState`, and plain `double[]` / `int` values only — no `sim` reference, no matrix access. The inner handler `evaluate(int row)` becomes a one-line delegation plus unchanged publish/stamp code.

**Deliverables**:
- Three new static methods in `EquationTableSemantics`
- `EquationTableElm` inner handlers delegate to them
- `EquationTableSemanticsTest` extended: one `@Test` per mode using `Expr.parse(...)` + `ExprState` directly

---

### Step 14 — Simulator-free `Expr.eval` overload for dt-controlled tests *(1 hour, low risk)*

**Context**: `ExprState.integrate()` and `diff()` pull `sim.timeStep` via a global reference. `ExprTest` works today but risks a null-pointer if `CirSim.theSim` is null, and silently passes with `dt = 0` if the global was not primed.

**Approach**: Add a dt-explicit overload without changing existing callers:

```java
// New in Expr.java:
public double eval(ExprState state, double dt) { ... }

// Existing — unchanged:
public double eval(ExprState state) {
    return eval(state, CirSim.theSim.timeStep);
}
```

All `ExprTest` cases that test `integrate` or `diff` migrate to `eval(state, dt)` — they receive explicit, deterministic timestep control with no `CirSim` dependency. Production callers inside `EquationTableElm` and `GodlyTableElm` use the existing overload, unchanged.

**Deliverables**:
- `Expr.eval(ExprState, double dt)` overload
- `ExprTest` integration and diff test cases migrated to dt-explicit overload
- No change to any production call site

---

### Phase 2 execution order

| Step | Effort | Risk | Unblocks | Notes |
|---|---|---|---|---|
| **12. `ComputedValues.resetForTesting()`** | 15 min | None | Clean `@BeforeEach` in all Phase 2 tests | Do this first |
| **8. `TableContentView` interface** | 45 min | Low | Step 9 | One-line `TableElm` change |
| **9. `SyncPatch` + two-phase sync** | 1–2 hr | Medium | Steps 10, 11 | Separate PR recommended |
| **10. Golden patch fixture tests** | 1 hr | None | — | Land in same PR as Step 9 |
| **11. Sync property tests** | 1 hr | None | — | Land in same PR as Step 9 |
| **13. Row-eval extraction** | half day | Medium | Full row-mode testing | Independent of 8–11 |
| **14. Expr dt overload** | 1 hr | Low | Simulator-free `ExprTest` | Independent of 8–11 |

Steps 12 → 8 → 9 form a tight pipeline and should be done as one session. Steps 10 and 11 land in the same PR as Step 9. Steps 13 and 14 are independent and can each be separate PRs.

### What this unlocks

- Sync logic is verifiable at the equation level without any GWT-dependent element construction.
- Row evaluation is testable for each mode with plain `Expr` + `ExprState` and known inputs.
- `ExprTest` is fully simulator-free and safe in all JVM environments.
- Parallel test execution is safe without `@ResourceLock` serialisation.
- Any future refactor of `synchronizeElements` or the row handlers is guarded by golden + property tests.

---

## Here's the status of each step against the code:


## Phase 1

| Step | Status | Notes |
|---|---|---|
| **1. Wire test resources** | ✅ Done | build.gradle has `resources { srcDirs = ['test/resources'] }`. `TestFixtures.java` exists with `loadSfcr()`/`loadResource()`. Both `SFCRParseResultTest` and `SFCRMixedModeRegressionTest` use it — no more `projectDir` boilerplate. |
| **2. Fix JSNI + `CirSim.theSim`** | ✅ Done | `log()`, `MRDlog()`, `SRTlog()` are now empty no-op Java methods (lines 41–52 of StockFlowRegistry.java). `getAllEquationOutputNames()` and `getAllCellEquationVariables()` both null-guard `CirSim.theSim` before use. |
| **3. `@ResourceLock` parallel safety** | ✅ Done | `@ResourceLock("StockFlowRegistry")`, `@ResourceLock("ComputedValues")`, and `@ResourceLock("SFCRParser")` are applied in all affected test classes. |
| **4. Parser strict mode** | ✅ Done | `SFCRParser.ParseException` inner class exists ([line 174](src/com/lushprojects/circuitjs1/client/SFCRParser.java#L174)), `parseToResult(String, boolean)` overload at SFCRParser.java. `SFCRParserRobustnessTest` has two `assertThrows(SFCRParser.ParseException.class, ...)` cases. |
| **5. Remove reflection in RegistryTest** | ✅ Done | `StockFlowRegistryTest` uses `createView(title)` anonymous `StockTableView` implementations and `StockFlowRegistry.TestSupport` (`.seedMergedRowsCache`, `.isMergedRowsCached`) instead of raw field reflection. |
| **6. Expr/ExprState tests** | ✅ Done | ExprTest.java exists. All `integrate` / `diff` tests use `evalFresh(state, 0.1)` dt-explicit form — no `CirSim.theSim` dependency. |
| **7. `StockTableView` interface** | ✅ Done | StockTableView.java exists; `StockFlowRegistry` maps hold `List<StockTableView>`. |

---

## Phase 2

| Step | Status | Notes |
|---|---|---|
| **8. `TableContentView` interface** | ✅ Done | TableContentView.java exists; `computeSyncPatches` takes `TableContentView` parameters. |
| **9. `SyncPatch` + two-phase sync** | ✅ Done | `computeSyncPatches(TableContentView, TableContentView)` and `applySyncPatches(List<SyncPatch>, TableElm)` exist in StockFlowRegistry.java. `synchronizeElements` is a thin bridge. |
| **10. Golden fixture tests** | ✅ Done | SyncPatchGoldenTest.java exists alongside TableContentViewStub.java. |
| **11. Sync property tests** | ✅ Done | SyncPropertyTest.java exists with idempotence, self no-op, and empty-source safety tests. |
| **12. `ComputedValues.resetForTesting()`** | ✅ Done | Method exists at ComputedValues.java; `StockFlowRegistry.clearRegistry()` also exists. Both called in `@BeforeEach` of registry/values tests instead of reflection. |
| **13. Row-eval extraction** | ✅ Done | `computeVoltageRowValue`, `computeFlowRowValue`, `computeParamRowValue` all exist in EquationTableSemantics.java and are tested in `EquationTableSemanticsTest`. |
| **14. `Expr.eval(ExprState, double dt)` overload** | ✅ Done | Both `eval(ExprState, double)` and `evalFresh(ExprState, double)` overloads exist ([Expr.java line 747](src/com/lushprojects/circuitjs1/client/Expr.java#L747)). `ExprTest` consistently uses them. |

---

**Everything in the plan is implemented and the test suite passes (`.dev.sh test` → exit code 0).** 