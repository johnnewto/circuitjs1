# Circuit Java Runner & Testing Reference

## Purpose

This is a quick operational reference for running CircuitJS1 simulations on the JVM (no browser/GWT UI) and validating behavior with automated tests.

---

## What exists today

- CLI entry point: `com.lushprojects.circuitjs1.client.CircuitJavaRunner`
- Gradle task: `runCircuitJava` (preferred)
- Browser runner mode: `circuitjs.html?runner=1...`
- End-to-end tests:
  - `CircuitJavaRunnerE2ETest`
  - `CircuitJavaSimTestBase` (base class for JVM simulation tests)

---

## Quick commands

### Run with defaults

```bash
./gradlew runCircuitJava
```

Defaults from `build.gradle`:
- circuit: `tests/sfcr-sim-model.txt`
- output: stdout (when no output file provided)
- steps: `500`
- format: `csv`

### Run with custom circuit and step count

```bash
./gradlew -q runCircuitJava -Pcircuit="tests/sfcr-sim-model.txt" -Psteps=10
```

### Write CSV to a file

```bash
./gradlew -q runCircuitJava \
  -Pcircuit="test/resources/sfcr_debug_reference.md" \
  -Poutput="/tmp/runner.csv" \
  -Psteps=20
```

### Emit World2 formatted table (t, P, POLR, CI, QL, NR only)

```bash
./gradlew -q runCircuitJava \
  -Pcircuit="src/com/lushprojects/circuitjs1/public/circuits/economics/1debug.md" \
  -Poutput="/tmp/world2.tsv" \
  -Psteps=20 \
  -Pformat="world2"
```

### Emit World2 table and HTML report

```bash
./gradlew -q runCircuitJava \
  -Pcircuit="src/com/lushprojects/circuitjs1/public/circuits/economics/1debug.md" \
  -Poutput="/tmp/world2.tsv" \
  -Psteps=1000 \
  -Pformat="world2" \
  -Phtml="/tmp/world2-runner.html"
```

Plotting options in the generated HTML report:
- `stacked` (default): five vertically stacked panels (`P`, `POLR`, `CI`, `QL`, `NR`)
- `single-lhs`: one combined plot with five left-side y-axes

These are selected in the report UI via the **Plot mode** dropdown (not via a CLI flag).

Run metadata is also included in two places:
- Terminal/stderr: `CircuitJavaRunner: circuit parameters used` block (timestep, MNA mode, equation tolerance, lookup mode, convergence threshold, EqnTable Newton Jacobian, Auto-Adjust Timestep, and related runtime settings)
- HTML report: **Circuit Parameters Used** table near the top of the page

### Use project test wrapper

```bash
./dev.sh test
```

---

## Browser runner mode

This mode runs simulation in the browser, without normal simulator UI, and renders:

- **Runner Output** tab (text output from the same `SimulationExportCore` path used by JVM runner)
- **World2 Report** tab (only when `format=world2`, embedded interactive report)
- **Standard Output** tab (diagnostic logs)

Implementation note:
- Browser runner startup now loads `startCircuit` using a deterministic synchronous browser XHR path before simulation starts. This avoids callback stalls observed in some dev/browser setups and ensures load success/failure is surfaced immediately in **Standard Output**.

Use it from the normal app URL:

```text
http://127.0.0.1:8000/circuitjs.html?runner=1&startCircuit=economics/1debug.md&steps=50
```

### Supported query parameters

- `runner=1` (required to enable this mode)
- `startCircuit=<path>` (e.g. `economics/1debug.md`)
- `steps=<n>` (default `1000`)
- `format=<tsv|csv|world2>` (optional, default `tsv` in browser runner mode)
- `cct=<inline circuit text>` (optional alternative)
- `ctz=<compressed circuit text>` (optional alternative)
- `nonInteractiveDumpKey=<localStorage key>` (preferred key name)

### Open current in-memory circuit in this mode

From **File → Open Runner Output Table...**.

This opens a new tab with `runner=1&ctz=...` for the current unsaved circuit state.

### Quick smoke checklist (browser runner)

1. Open:
  - `.../circuitjs.html?runner=1&startCircuit=economics/1debug.md&steps=5&format=csv`
2. Verify tabs:
  - Runner Output + Standard Output are visible
3. Verify CSV output:
  - Output starts with `t,` header
  - Time column advances across rows
4. Open:
  - `.../circuitjs.html?runner=1&startCircuit=economics/1debug.md&steps=5&format=world2`
5. Verify world2 output/report:
  - Runner Output begins with `Year\tPopulation\t...`
  - World2 Report tab appears and shows table/plot UI
6. Cross-check parity (spot-check):
  - Compare first 1-3 rows against `./gradlew -q runCircuitJava` with same circuit/steps/format

---

## CLI contract

`CircuitJavaRunner` arguments:

1. `circuitPath` (required)
2. `outputPath` (optional, blank means stdout)
3. `steps` (optional, default `1000` in direct `main`, `500` via Gradle task default)
4. `format` (optional: `csv` or `world2`, default `csv`)

Direct usage:

```bash
java com.lushprojects.circuitjs1.client.CircuitJavaRunner <circuit.txt> [output.csv] [steps] [format]
```

Recommended in this repo: use `./gradlew runCircuitJava` rather than direct `java`.

---

## Output format

CSV output format:

- Header starts with `t`
- Remaining columns are sorted `ComputedValues` keys
- One row per simulation step
- Values come from converged committed values (`ComputedValues.commitConvergedValues()`)

Example header:

```text
t,G_d,Y,YD
```

World2 format (`format=world2`) output:

- Fixed columns only: `Year`, `Population`, `Pollution Ratio`, `Capital Investment`, `Quality of Life`, `Natural Resources`
- Values are tab-separated
- Number formatting follows `war/world2.html` table display conventions

---

## Testing workflow

### Targeted runner E2E tests first

```bash
./gradlew :test --tests "*CircuitJavaRunnerE2ETest*"
```

### Targeted base fixture consumers (if needed)

```bash
./gradlew :test --tests "*SFCRSIMModelTest*" --tests "*LookupTextImportTest*"
```

### Full JVM test suite

```bash
./gradlew test
```

### Optional smoke loop over text circuits

```bash
for f in tests/*.txt; do
  if ./gradlew -q runCircuitJava -Pcircuit="$f" -Psteps=1 >/tmp/runner.out 2>/tmp/runner.err; then
    echo "PASS $f"
  else
    echo "FAIL $f"
  fi
done
```

---

## Recommended regression checklist

When changing solver/runtime/economic model behavior:

1. Run `CircuitJavaRunnerE2ETest`
2. Run one or more `runCircuitJava` commands against representative circuits
3. Inspect CSV for monotonic time and expected key values
4. Run full `./gradlew test`

---

## Common failure signals

- `CircuitJavaRunner: no circuit elements available after load`
  - Circuit file did not parse into elements.
- `CircuitJavaRunner: circuit matrix is null after analyze`
  - No solvable electrical network formed.
- `CircuitJavaRunner: simulation stopped during analyze: ...`
  - Analyze phase raised a stop condition.
- `CircuitJavaRunner: time did not advance at step ...`
  - Simulation did not advance time; inspect stop conditions and circuit validity.

Browser mode (`Standard Output` tab):

- `Runner load failed for all candidates of startCircuit=...`
  - The `startCircuit` path could not be resolved.
- `loadFileFromURLRunner HTTP failure: ... status=...`
  - URL returned non-2xx response.
- `loadFileFromURLRunner timeout after 15s: ...`
  - Circuit file request stalled.
- `window.onerror: ...` or `GWT uncaught exception: ...`
  - Runtime exception occurred during load/simulation.

---

## Notes

- JVM runner mode is enabled by `RuntimeMode.setNonInteractiveRuntime(true)`.
- Tests that touch `ComputedValues` should isolate/reset shared state (`@ResourceLock("ComputedValues")`, `ComputedValues.resetForTesting()`).
- For iterative debugging, prefer small `-Psteps` values first (e.g. `1`, `5`, `10`) and scale up after sanity checks.
