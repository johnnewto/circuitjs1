# Headless Runner & Testing Reference

## Purpose

This is a quick operational reference for running CircuitJS1 simulations on the JVM (no browser/GWT UI) and validating behavior with automated tests.

---

## What exists today

- CLI entry point: `com.lushprojects.circuitjs1.client.HeadlessRunner`
- Gradle task: `headlessCli`
- End-to-end tests:
  - `HeadlessRunnerE2ETest`
  - `HeadlessSimTest` (base class for headless simulation tests)

---

## Quick commands

### Run headless with defaults

```bash
./gradlew headlessCli
```

Defaults from `build.gradle`:
- circuit: `tests/sfcr-sim-model.txt`
- output: stdout (when no output file provided)
- steps: `500`

### Run headless with custom circuit and step count

```bash
./gradlew -q headlessCli -Pcircuit="tests/sfcr-sim-model.txt" -Psteps=10
```

### Write CSV to a file

```bash
./gradlew -q headlessCli \
  -Pcircuit="test/resources/sfcr_debug_reference.md" \
  -Poutput="/tmp/headless.csv" \
  -Psteps=20
```

### Use project test wrapper

```bash
./dev.sh test
```

---

## CLI contract

`HeadlessRunner` arguments:

1. `circuitPath` (required)
2. `outputPath` (optional, blank means stdout)
3. `steps` (optional, default `1000` in direct `main`, `500` via Gradle task default)

Direct usage:

```bash
java com.lushprojects.circuitjs1.client.HeadlessRunner <circuit.txt> [output.csv] [steps]
```

Recommended in this repo: use `./gradlew headlessCli` rather than direct `java`.

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

---

## Testing workflow

### Targeted headless E2E tests first

```bash
./gradlew test --tests "*HeadlessRunnerE2ETest*"
```

### Targeted helper/base behavior (if needed)

```bash
./gradlew test --tests "*HeadlessSimTest*"
```

### Full JVM test suite

```bash
./gradlew test
```

### Optional smoke loop over text circuits

```bash
for f in tests/*.txt; do
  if ./gradlew -q headlessCli -Pcircuit="$f" -Psteps=1 >/tmp/headless.out 2>/tmp/headless.err; then
    echo "PASS $f"
  else
    echo "FAIL $f"
  fi
done
```

---

## Recommended regression checklist

When changing solver/runtime/economic model behavior:

1. Run `HeadlessRunnerE2ETest`
2. Run one or more `headlessCli` commands against representative circuits
3. Inspect CSV for monotonic time and expected key values
4. Run full `./gradlew test`

---

## Common failure signals

- `HeadlessRunner: no circuit elements available after load`
  - Circuit file did not parse into elements.
- `HeadlessRunner: circuit matrix is null after analyze`
  - No solvable electrical network formed.
- `HeadlessRunner: simulation stopped during analyze: ...`
  - Analyze phase raised a stop condition.
- `HeadlessRunner: time did not advance at step ...`
  - Simulation did not advance time; inspect stop conditions and circuit validity.

---

## Notes

- Headless mode is enabled by `RuntimeMode.setHeadless(true)`.
- Tests that touch `ComputedValues` should isolate/reset shared state (`@ResourceLock("ComputedValues")`, `ComputedValues.resetForTesting()`).
- For iterative debugging, prefer small `-Psteps` values first (e.g. `1`, `5`, `10`) and scale up after sanity checks.
