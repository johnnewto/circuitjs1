# Headless JVM Runner — Implementation Plan

## Goal

Run a CircuitJS1 circuit entirely under the JVM (no GWT runtime, no browser, no Canvas) and emit computed values, named stock/flow variables, and/or MNA node voltages to stdout or a CSV file.  Primary motivation: enable deterministic, end-to-end regression tests that run with `./gradlew test` and catch solver-level and economics-model bugs that unit tests on isolated components cannot.

---

## Existing JVM-Safe Code (No Changes Needed)

These classes compile and run under plain JVM today:

| Class / Package | Status |
|---|---|
| `LUSolver` | Pure Java, already unit-tested |
| `ComputedValues` | Pure Java, zero GWT imports |
| `Expr`, `ExprParser`, `ExprState` | Pure Java, already unit-tested |
| `SFCRParser`, `SFCRDocument`, `SFCRParseResult` | Pure Java, already unit-tested |
| `StockFlowRegistry` | Pure Java, already unit-tested |
| `EquationTableElm` (internal math) | Calc methods have no canvas calls |

These form the natural foundation — no refactoring risk on the paths that matter most.

---

## Root Causes of GWT Coupling

Three patterns account for almost all the coupling.  Fixing these three is the entire job:

### 1. `CirSim` implements browser event handlers and builds widgets in `init()`
[`CirSim.java:138`](../src/com/lushprojects/circuitjs1/client/CirSim.java#L138) — class signature binds to `MouseDownHandler`, `ClickHandler`, etc.  
[`CirSim.java:531`](../src/com/lushprojects/circuitjs1/client/CirSim.java#L531) — `init()` constructs menus, panels, Canvas, Timer.

### 2. `CircuitElm.initClass()` calls GWT-only APIs
[`CircuitElm.java:133`](../src/com/lushprojects/circuitjs1/client/CircuitElm.java#L133) — calls `Storage.getLocalStorageIfSupported()` (JSNI) and `NumberFormat.getFormat()` (GWT i18n).

### 3. UI side-effects scattered through the load path
- `Window.alert()` at [CirSim.java:4643](../src/com/lushprojects/circuitjs1/client/CirSim.java#L4643), [8875](../src/com/lushprojects/circuitjs1/client/CirSim.java#L8875), [8947](../src/com/lushprojects/circuitjs1/client/CirSim.java#L8947) etc.
- `titleLabel.setText()` inside `readCircuit()` at [CirSim.java:5629](../src/com/lushprojects/circuitjs1/client/CirSim.java#L5629)
- `viewModelInfoItem.setEnabled()`, `InfoViewerDialog.showInfoInIframe()` inside the SFCR load path at [CirSim.java:5570–5590](../src/com/lushprojects/circuitjs1/client/CirSim.java#L5568)
- `addWidgetToVerticalPanel()` / `removeWidgetFromVerticalPanel()` called by element constructors (e.g. `PotElm` at [PotElm.java:74](../src/com/lushprojects/circuitjs1/client/PotElm.java#L74))
- `speedBar.getValue()` inside `getIterCount()` at [CirSim.java:4180](../src/com/lushprojects/circuitjs1/client/CirSim.java#L4185)
- `GWT.log()` used as the debug logger throughout

---

## Phase 1 — Runtime Mode Gate (1–2 days)

### New file: `src/.../client/RuntimeMode.java`

```java
package com.lushprojects.circuitjs1.client;

/**
 * Global flag distinguishing GWT browser execution from headless JVM execution.
 * Set to headless BEFORE constructing CirSim in any non-browser context.
 */
public class RuntimeMode {
    private static boolean headless = false;

    public static void setHeadless(boolean v) { headless = v; }
    public static boolean isGwt()     { return !headless; }
    public static boolean isHeadless() { return headless; }
}
```

### Gate `CirSim.console()`

Replace the single `GWT.log()` call in the logging method with:

```java
static void console(String s) {
    if (RuntimeMode.isGwt())
        GWT.log(s);
    else
        System.err.println(s);
}
```

### Gate `CirSim.getIterCount()`

```java
double getIterCount() {
    if (RuntimeMode.isHeadless()) return 1.0;  // one iteration per call in headless
    int val = speedBar.getValue();
    if (val == 0) return 0;
    return .1 * Math.exp((val - 61) / 24.);
}
```

### Gate `addWidgetToVerticalPanel` / `removeWidgetFromVerticalPanel`

[`CirSim.java:8013`](../src/com/lushprojects/circuitjs1/client/CirSim.java#L8013):

```java
void addWidgetToVerticalPanel(Widget w) {
    if (RuntimeMode.isHeadless() || w == null) return;
    // ... existing code ...
}
void removeWidgetFromVerticalPanel(Widget w) {
    if (RuntimeMode.isHeadless() || w == null) return;
    // ... existing code ...
}
```

### Gate `Window.alert()` calls in the load/validation path

Wrap each call:
```java
// before
Window.alert(Locale.LS("Some nodes are unconnected!"));
// after
if (RuntimeMode.isGwt()) Window.alert(Locale.LS("Some nodes are unconnected!"));
else console("WARNING: Some nodes are unconnected!");
```

Locations: [8875](../src/com/lushprojects/circuitjs1/client/CirSim.java#L8875), [8934](../src/com/lushprojects/circuitjs1/client/CirSim.java#L8934), [8947](../src/com/lushprojects/circuitjs1/client/CirSim.java#L8947), [4643](../src/com/lushprojects/circuitjs1/client/CirSim.java#L4643), [5464](../src/com/lushprojects/circuitjs1/client/CirSim.java#L5464), [5697](../src/com/lushprojects/circuitjs1/client/CirSim.java#L5697), [8625](../src/com/lushprojects/circuitjs1/client/CirSim.java#L8625).

---

## Phase 2 — Headless-Safe `CircuitElm.initClass()` (1–2 days)

[`CircuitElm.java:133`](../src/com/lushprojects/circuitjs1/client/CircuitElm.java#L133) currently calls:
- `Storage.getLocalStorageIfSupported()` — JSNI, crashes under JVM
- `NumberFormat.getFormat(...)` — GWT i18n, crashes under JVM

### New file: `src/.../client/NumFmt.java`

```java
package com.lushprojects.circuitjs1.client;

import java.text.DecimalFormat;

/** Thin wrapper so CircuitElm formatting works under both GWT and plain JVM. */
public class NumFmt {
    /** Wraps a GWT NumberFormat or a JVM DecimalFormat behind a common interface. */
    public interface Formatter {
        String format(double value);
    }

    public static Formatter forPattern(String pattern) {
        if (RuntimeMode.isGwt()) {
            com.google.gwt.i18n.client.NumberFormat gwtFmt =
                com.google.gwt.i18n.client.NumberFormat.getFormat(pattern);
            return gwtFmt::format;
        } else {
            // Convert GWT pattern (####.###) to JVM pattern (#,##0.###)
            DecimalFormat jvmFmt = new DecimalFormat(pattern.replace("####.", "#,##0."));
            return jvmFmt::format;
        }
    }
}
```

### Change `CircuitElm.showFormat` / `shortFormat` / `fixedFormat`

Replace their type from `NumberFormat` to `NumFmt.Formatter` and update `initClass()` and `setDecimalDigits()` to use `NumFmt.forPattern(...)`.

Any existing `.format(x)` call sites continue to work unchanged since the interface has the same method name.

### Gate Storage calls in `initClass()`

```java
if (RuntimeMode.isGwt()) {
    Storage stor = Storage.getLocalStorageIfSupported();
    if (stor != null) {
        // read decimalDigits settings
    }
}
```

---

## Phase 3 — `initHeadless()` on CirSim (1–2 days)

Add a new method to `CirSim` that bootstraps only what the simulation loop needs, skipping all widget construction:

```java
/** Initialise simulation state without GWT UI.  Call instead of init() in headless mode. */
public void initHeadless() {
    random = new Random();
    elmList = new Vector<>();
    voltageSources = new CircuitElm[10];
    timeStep = 5e-6;
    maxTimeStep = 5e-2;
    minTimeStep = 1e-12;
    t = 0;
    CircuitElm.initClass(this);   // safe after Phase 2
    // do NOT call composeMainMenu(), buildToolbar(), init() etc.
}
```

The existing `init()` method is untouched — this is additive only.

---

## Phase 4 — Gate the `readCircuit` / SFCR Load Path (1 day)

`readCircuit(String, int)` at [CirSim.java:5540](../src/com/lushprojects/circuitjs1/client/CirSim.java#L5540) currently:
- calls `InfoViewerDialog.showInfoInIframe()` — GWT dialog, crashes under JVM
- calls `titleLabel.setText(null)` — NPE in headless
- calls `viewModelInfoItem.setEnabled(...)` — NPE in headless

Gate each with `if (RuntimeMode.isGwt())`.  The SFCR parse path itself (`SFCRParser.parse()`) is already pure Java and will work once the surrounding UI calls are gated.

---

## Phase 5 — `HeadlessRunner` CLI entry point (1–2 days)

### New file: `src/.../client/HeadlessRunner.java`

```java
package com.lushprojects.circuitjs1.client;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * JVM-only entry point.  Loads a circuit file, runs N timesteps, and writes
 * a CSV of simulation time + all ComputedValues (named stocks/flows) to stdout
 * or a specified output file.
 *
 * Usage:
 *   java HeadlessRunner <circuit.txt> [output.csv] [steps=1000] [dt=0.01]
 */
public class HeadlessRunner {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: HeadlessRunner <circuit.txt> [output.csv] [steps=1000]");
            System.exit(1);
        }

        RuntimeMode.setHeadless(true);

        String circuitPath = args[0];
        String outputPath  = args.length > 1 ? args[1] : null;
        int    steps       = args.length > 2 ? Integer.parseInt(args[2]) : 1000;

        // Load and parse circuit
        String circuitText = Files.readString(Path.of(circuitPath));
        CirSim sim = new CirSim();
        sim.initHeadless();
        sim.readCircuit(circuitText, 0);
        sim.analyzeCircuit();

        // Open output
        PrintWriter out = outputPath != null
            ? new PrintWriter(new FileWriter(outputPath))
            : new PrintWriter(new OutputStreamWriter(System.out));

        // Write CSV header
        List<String> keys = new ArrayList<>(ComputedValues.getRegisteredComputedNames() != null
            ? ComputedValues.getRegisteredComputedNames()
            : Collections.emptySet());
        Collections.sort(keys);

        out.print("t");
        for (String k : keys) out.print("," + k);
        out.println();

        // Simulation loop
        for (int step = 0; step < steps; step++) {
            sim.runCircuit(step == 0);
            ComputedValues.commitConvergedValues();

            out.print(sim.t);
            for (String k : keys) {
                Double v = ComputedValues.getConvergedValue(k);
                out.print("," + (v != null ? v : ""));
            }
            out.println();
        }

        out.flush();
        if (outputPath != null) out.close();
    }
}
```

---

## Phase 6 — Gradle Task + Test Harness (1 day)

### Add to `build.gradle`

```groovy
task headlessCli(type: JavaExec, dependsOn: classes) {
    group = 'circuitjs1'
    description = 'Run a circuit headlessly and emit CSV output'
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'com.lushprojects.circuitjs1.client.HeadlessRunner'
    args = [
        project.findProperty('circuit') ?: 'tests/sfcr-sim-model.txt',
        project.findProperty('output')  ?: '',
        project.findProperty('steps')   ?: '500'
    ]
}
```

Run with:
```
./gradlew headlessCli -Pcircuit=tests/sfcr-sim-model.txt -Poutput=out.csv -Psteps=1000
```

### New JUnit5 base class: `test/java/.../HeadlessSimTest.java`

```java
/** Base fixture for headless simulation regression tests. */
abstract class HeadlessSimTest {
    protected CirSim sim;

    @BeforeEach
    void setUpSim() throws Exception {
        RuntimeMode.setHeadless(true);
        ComputedValues.resetForTesting();
        sim = new CirSim();
        sim.initHeadless();
    }

    protected void loadCircuit(String resourcePath) throws Exception {
        String text = Files.readString(Path.of(resourcePath));
        sim.readCircuit(text, 0);
        sim.analyzeCircuit();
    }

    protected void runSteps(int n) {
        for (int i = 0; i < n; i++) {
            sim.runCircuit(i == 0);
            ComputedValues.commitConvergedValues();
        }
    }

    protected double getConverged(String name) {
        Double v = ComputedValues.getConvergedValue(name);
        assertNotNull(v, "No converged value for: " + name);
        return v;
    }
}
```

### Example regression test

```java
class SFCRSIMModelTest extends HeadlessSimTest {

    @Test
    void balanceSheetRemainsBalancedAfter500Steps() throws Exception {
        loadCircuit("tests/sfcr-sim-model.txt");
        runSteps(500);
        double hh   = getConverged("Households");
        double firms = getConverged("Firms");
        double banks = getConverged("Banks");
        // Net worth must sum to zero (no money created or destroyed)
        assertEquals(0.0, hh + firms + banks, 1e-4);
    }
}
```

---

## Scope Boundaries

### In scope (economics/equation-table focused)

- `EquationTableElm`, `GodlyTableElm`, `SFCTableElm`, `TableElm`
- `ComputedValueSourceElm`, `LabeledNodeElm`
- `WireElm`, `ResistorElm`, `VoltageElm` (all linear, no canvas in hot path)
- `SFCRParser` → auto-created circuits

### Out of scope for initial pass

| Element type | Reason |
|---|---|
| `PotElm` | Widget slider registered in constructor — needs gating first |
| `ChipElm` / digital | `writeOutput()` and setup complex, low value for economics |
| Scope/rendering elements | Canvas-only, no simulation state to read |

---

## Risk Register

| Risk | Mitigation |
|---|---|
| `draw()` called from constructor in some elements — NPE on null context | Gate canvas calls behind `if (RuntimeMode.isGwt())` in `CircuitElm.draw()` stub |
| `Timer`-driven loop: `runCircuit()` assumes wall-clock throttling | `getIterCount()` returns constant 1.0 in headless (Phase 1) |
| `SFCRParser` calls `InfoViewerDialog` indirectly | Gate in `readCircuit()` (Phase 4) |
| `NumberFormat` GWT type leaks via field type change | Use `NumFmt.Formatter` interface everywhere showFormat is referenced (Phase 2) |
| GWT compilation breaks if new non-GWT types are referenced | `NumFmt` and `HeadlessRunner` must be excluded from the GWT module XML, or guarded with `@GwtIncompatible` |

### GWT module exclusion for `HeadlessRunner`

Add to `src/com/lushprojects/circuitjs1/circuitjs1.gwt.xml`:
```xml
<source path="client">
    <exclude name="HeadlessRunner.java"/>
    <exclude name="NumFmt.java"/>
</source>
```

Or annotate with `@GwtIncompatible` if keeping in the same source root.

---

## Effort Summary

| Phase | Deliverable | Estimate |
|---|---|---|
| 1 | `RuntimeMode`, gate Window/GWT.log/speedBar | 1–2 days |
| 2 | `NumFmt`, headless-safe `initClass()` | 1–2 days |
| 3 | `CirSim.initHeadless()` | 1 day |
| 4 | Gate `readCircuit` UI side-effects | 1 day |
| 5 | `HeadlessRunner` CLI | 1–2 days |
| 6 | Gradle task + `HeadlessSimTest` base + first regression test | 1 day |
| **Total** | | **~1.5–2 weeks** |

Phases 1–3 are mechanical and low-risk.  Phase 4 is where SFCR loading lives — test carefully after each gate.  Phase 6 produces the golden-output fixture that makes all future work verifiable automatically.
