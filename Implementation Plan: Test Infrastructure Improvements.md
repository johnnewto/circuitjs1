

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