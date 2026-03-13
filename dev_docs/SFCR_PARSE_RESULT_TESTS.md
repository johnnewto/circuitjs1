# SFCR Parse Result Tests

## Overview

`SFCRParseResultTest` is a plain-Java JUnit 5 test suite that exercises the SFCR
parse-to-result path without requiring GWT, a browser, or a running `CirSim`
instance.  It runs on a standard JVM in under 1 second.

```
test/java/com/lushprojects/circuitjs1/client/SFCRParseResultTest.java
```

Run with:

```bash
./gradlew test --tests "*.SFCRParseResultTest"
```

---

## How it works

### The problem

`SFCRParser.parse()` is GWT-dependent because it:

1. Instantiates `CircuitElm` subclasses (which have GWT Canvas static fields)
2. Calls `CirSim` instance methods to mutate UI check-items and wire elements into
   the live element list

Loading any of these on a plain JVM causes `ClassNotFoundException` or
`ExceptionInInitializerError` at the class-loading stage — before any test
assertion can run.

### The solution — `parseToResult()`

A new static factory method on `SFCRParser` runs the full text-parsing pipeline
but bypasses all GWT-dependent code:

```
SFCRParseResult result = SFCRParser.parseToResult(sfcrText);
```

**What it does:**

| Normal `parse()` | `parseToResult()` |
|---|---|
| Calls `sim.getSFCRDocumentState()` | Stores block-comments in `SFCRParseResult` |
| Calls `applyInitSettings()` → mutates `sim.*` fields and GWT check-items | Copies raw key/value map into `SFCRParseResult.initSettings` |
| Calls `CirSim.createCe()` → instantiates `EquationTableElm`, `SFCTableElm` | Stores dump string in `SFCRParseResult.blockDumps` |
| Calls `sim.elmList.add()`, `sim.assignPersistentUid()` | Skipped entirely |
| Calls `HintRegistry.setHint()` | Still called (pure Java, no GWT) |

The gate is a `pendingResult` field on the parser:

```java
// In createEquationTable() and createMatrixTable():
if (pendingResult != null) {
    pendingResult.blockDumps.add(new SFCRParseResult.BlockDump("equations", name, dump.toString()));
    currentY = y2 + elementSpacing;
    return;   // skip CirSim.createCe() entirely
}
```

### GWT dependency removal

Three helper methods were added to `SFCRParser` as GWT-free substitutes:

| Helper | Replaces |
|---|---|
| `SFCRParser.escapeToken(s)` | `CustomLogicModel.escape(s)` (has GWT `Window` import) |
| `SFCRParser.parseModeOrdinal(mode)` | `SFCRUtil.parseEquationRowMode()` (returns `EquationTableElm.RowOutputMode` enum whose parent class loads Canvas) |
| `SFCRParser.parseCombinedNameLocal(s)` | `EquationTableElm.parseCombinedName()` (same reason) |

`ArrayList<EquationTableElm.RowOutputMode>` was replaced with
`ArrayList<Integer>` throughout both block parsers and `appendCommentRow()`, so
the `EquationTableElm` class is never loaded during a `parseToResult()` call.

---

## SFCRParseResult structure

```java
public class SFCRParseResult {

    // Raw @init key→value pairs (e.g. "timestep" → "1")
    public Map<String, String> initSettings;

    // One entry per element block in source order
    public List<BlockDump> blockDumps;

    // Variable hints from inline # comments + @hints block
    public Map<String, String> hints;

    // Leading markdown lines before each block (for round-trip)
    public Map<String, List<String>> blockComments;

    public static class BlockDump {
      public final String blockType;   // "equations" | "matrix" | "sankey" | "action"
      public final String blockName;   // e.g. "equations_1A", "Parameters"
        public final String dumpString;  // CircuitJS element serialization string
    }
}
```

`dumpString` is the exact text that would normally be fed to `CirSim.createCe()`
— the tokenised format used throughout CircuitJS1's file format (e.g.
`266 168 264 368 424 2 equations_1A\s 8 ...`).

---

## Test fixture used

```
test/resources/sfcr/parse_result_fixture.md
```

This is markdown with fenced code blocks that contain SFCR syntax. It contains:

- `@init` block with `timestep`, `voltageUnit`, `timeUnit`, display settings
- 3 equation blocks from `sfcr_set`: `equations_1A`, `equations_2`, `Parameters`
- 2 matrix blocks from `sfcr_matrix`: `Balance_Sheet`, `Transaction_Flow_Matrix`
- Inline `#` comments inside `sfcr_set` rows that become hints
- `@circuit` block with raw element passthrough lines
- `@sankey` block plus one `@scope` block

The fixture is resolved via a system property injected by Gradle:

```groovy
// build.gradle
tasks.withType(Test) {
    systemProperty 'projectDir', projectDir.absolutePath
}
```

```java
// In @BeforeAll:
Path fixtureFile = Paths.get(System.getProperty("projectDir"),
  "test", "resources", "sfcr", "parse_result_fixture.md");
```

---

## Test cases (24 total)

### Null / empty input

| Test | Checks |
|---|---|
| `testNullInputReturnsNull` | `parseToResult(null)` returns `null` |
| `testEmptyInputReturnsNull` | `parseToResult("   ")` returns `null` |

### Valid file result

| Test | Checks |
|---|---|
| `testResultNotNull` | Non-null result returned for valid SFCR text |

### `@init` settings

| Test | Checks |
|---|---|
| `testTimestepParsed` | `initSettings.get("timestep")` == `"1"` |
| `testVoltageUnitParsed` | `initSettings.get("voltageUnit")` == `"$"` |
| `testTimeUnitParsed` | `initSettings.get("timeUnit")` == `"yr"` |
| `testShowDotsParsed` | `initSettings.get("showDots")` == `"true"` |

### Equation blocks

| Test | Checks |
|---|---|
| `testThreeEquationBlocksFound` | Exactly 3 blocks with type `"equations"` |
| `testEquationBlockNamesPresent` | `equations_1A`, `equations_2`, `Parameters` present |
| `testEquationDumpContainsYDAndTheta` | `equations_1A` dump contains `YD` and `\theta` |
| `testDumpStartsWithType266` | All equation dumps start with `"266 "` (EquationTableElm type) |
| `testTwoMatrixBlocksFound` | Exactly 2 blocks with type `"matrix"` |
| `testMatrixBlockNamesPresent` | `Balance_Sheet` and `Transaction_Flow_Matrix` present |
| `testOneSankeyBlockCaptured` | Exactly 1 `"sankey"` block, dump starts with `"466 "` |

### Hints

| Test | Checks |
|---|---|
| `testHintsPopulatedFromInlineComments` | `hints["YD"]` contains `"Disposable Income"` |
| `testHintsFromHintsBlock` | `hints["Y"]` is present from inline `sfcr_set` comments |

### `escapeToken` helper

| Test | Checks |
|---|---|
| `testEscapeTokenEmpty` | `""` → `"\\0"` |
| `testEscapeTokenSpaces` | `"hello world"` → `"hello\\sworld"` |
| `testEscapeTokenSpecialChars` | `=` → `\\q`, space → `\\s`, `+` → `\\p` |

### `parseModeOrdinal` helper

| Test | Checks |
|---|---|
| `testParseModeOrdinalVoltage` | `null`, `"voltage"`, `"VOLTAGE"` all return `0` |
| `testParseModeOrdinalFlow` | `"flow"`, `"stock"`, `"stock_mode"` return `1` |
| `testParseModeOrdinalParam` | `"param"`, `"parameter"`, `"PARAM_MODE"` return `3` |

### `parseCombinedNameLocal` helper

| Test | Checks |
|---|---|
| `testParseCombinedNameNoSeparator` | `"myVar"` → `["myVar", ""]` |
| `testParseCombinedNameArrow` | `"source->target"` → `["source", "target"]` |
| `testParseCombinedNameComma` | `"A , B"` → `["A", "B"]` |
| `testParseCombinedNameNull` | `null` → `["", ""]` |

---

## Adding more tests

### For a file with `@matrix` blocks

```java
String sfcr = Files.readString(Paths.get(projectDir, "src/.../econ_BOMDSimple.txt"));
SFCRParseResult result = SFCRParser.parseToResult(sfcr);

List<SFCRParseResult.BlockDump> matrices = result.getBlocksByType("matrix");
assertEquals(1, matrices.size());
assertTrue(matrices.get(0).dumpString.startsWith("265 "));  // SFCTableElm type
```

### Round-trip smoke test (parse → inspect dump → re-tokenise)

The `dumpString` in each `BlockDump` is valid CircuitJS format.  You can verify
structure without instantiating elements:

```java
String dump = result.findBlock("equations", "equations_1A").dumpString;
StringTokenizer st = new StringTokenizer(dump);
assertEquals(266, Integer.parseInt(st.nextToken()));  // type
int x1 = Integer.parseInt(st.nextToken());
int y1 = Integer.parseInt(st.nextToken());
// ... parse further fields to assert geometry, row count, etc.
```

### Testing a custom SFCR string inline

```java
String sfcr = "@equations Test\n  Y ~ 2 * X # Doubled input\n@end\n";
SFCRParseResult result = SFCRParser.parseToResult(sfcr);
assertNotNull(result.findBlock("equations", "Test"));
assertEquals("Doubled input", result.hints.get("Y"));
```

---

## Limitations

- **`@action` blocks** are not captured in `pendingResult` yet — they require
  `ActionScheduler.getInstance(sim)` which guards early return when `sim` is
  null. The block is silently skipped.
- **`@scope` blocks** are intentionally skipped — they need live element UIDs
  from the element list to resolve trace references.
- **`@sankey` blocks** are now captured in result-mode as `BlockDump` entries
  with `blockType="sankey"` and a serialized dump string. They are not instantiated.
- **`@circuit` passthrough** lines are collected in `rawCircuitLines` but not
  added to `blockDumps` (they are direct element dumps, not SFCR structure).
- Full round-trip test (parse → export back to SFCR → compare) requires
  `SFCRExporter` to accept an `SFCRParseResult` instead of a live `CirSim`.
  That is a separate, larger refactor.
