# SFCR Handler Refactor Plan

## Goal
Refactor SFCR import/export so block-specific logic is distributed into registered handler classes, while keeping document-level orchestration centralized.

## Decision
- Keep central orchestration in `SFCRParser`/`SFCRExporter` for:
  - block ordering
  - template merge behavior
  - fence/comment lifecycle
  - shared parse/export context
- Move block-specific parsing/exporting into handlers under `client/io` (not under element directories), with optional element-side adapters for element-private details.

## Why This Shape
- Current `dump/undump` flow delegates element construction through factories/constructors.
- SFCR currently centralizes too much block logic in monolithic classes.
- Keeping handlers in `io` preserves format cohesion and avoids scattering SFCR syntax rules across `elements/*`.

## Proposed Package Layout
- `src/com/lushprojects/circuitjs1/client/io/sfcr/`
  - `SFCRParseContext.java`
  - `SFCRExportContext.java`
  - `SFCRBlockType.java`
  - `SFCRBlockHandlerRegistry.java`
  - `SFCRParserOrchestrator.java` (optional extraction later)
  - `SFCRExporterOrchestrator.java` (optional extraction later)
- `src/com/lushprojects/circuitjs1/client/io/sfcr/handlers/`
  - `SFCRBlockParseHandler.java`
  - `SFCRBlockExportHandler.java`
  - `InitBlockHandler.java`
  - `ActionBlockHandler.java`
  - `EquationsBlockHandler.java`
  - `MatrixBlockHandler.java`
  - `LookupBlockHandler.java`
  - `ScopeBlockHandler.java`
  - `SankeyBlockHandler.java`
  - `CircuitPassthroughBlockHandler.java`
  - `HintsBlockHandler.java`

## Handler Contracts (Draft)
```java
public interface SFCRBlockParseHandler {
    String[] supportedDirectives();
    ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx);
}

public interface SFCRBlockExportHandler {
    SFCRBlockType blockType();
    int exportOrder(); // lower runs earlier
    String export(SFCRExportContext ctx);
}

public record ParseResult(int nextIndex, java.util.List<ParseWarning> warnings) {}

public record ParseWarning(int line, String message) {}
```

Handler design rule:
- Handlers should be stateless and reusable. Per-parse/per-export mutable data must live in context objects.

## Context Objects (Draft)
- `SFCRParseContext`
  - shared mutable state currently on `SFCRParser` (created elements, hints, lookups, scope blocks, cursor positioning, pending result)
  - utility hooks for element creation and block comment storage
- `SFCRExportContext`
  - categorized element lists
  - syntax mode
  - lookup naming/state maps
  - helper accessors (e.g., UID dump, block comment lookup)

## Migration Phases
1. **Extract interfaces and registry**
   - Introduce handler interfaces, context DTOs, and registry.
   - Registry owns static/default handler registration in one place (no element-side registration).
   - Keep existing parser/exporter behavior unchanged.
2. **Migrate easiest blocks first**
   - `@init`, `@action`, `@hints`, `@lookup`.
   - Wire parser dispatch through registry for these blocks only.
3. **Migrate structural blocks**
   - `@equations`, `@matrix`, `@scope`, `@sankey`.
   - Keep current output/parsing semantics byte-compatible where possible.
4. **Migrate `@circuit` passthrough**
   - Keep passthrough logic centralized enough to preserve dump compatibility.
5. **Orchestrator cleanup**
   - Reduce `SFCRParser`/`SFCRExporter` to orchestration + fallback paths.
   - Delete dead private methods once handler parity is validated.

## Registry and Dispatch Rules
- Central registry maps directive -> parse handler using `supportedDirectives()`.
- No implicit iteration-based `supports(...)` checks in hot path.
- Unknown directive behavior is explicit:
  - parse via `UnknownBlockHandler`
  - emit warning
  - skip until matching `@end` (or EOF)
- Export order is explicit via `exportOrder()` and must not depend on registration order.

## Backward Compatibility Rules
- Preserve existing directive support and aliases (`@parameters` as `@equations` alias).
- Preserve R-style parsing/export semantics and metadata handling.
- Preserve block comment attachment behavior and fence-aware parsing.
- Preserve existing dump passthrough in `@circuit` and UID behavior.
- Preserve `SFCRParseResult.blockDumps` ordering guarantees used by downstream round-trip paths.

## R-Style Handling Strategy
- Keep R-style detection in orchestrator scan phase.
- Normalize one R-style assignment block at a time into a synthetic directive event (`equations` or `matrix`) plus payload.
- Route normalized payload through the same corresponding handlers to minimize duplicate logic.
- Keep metadata extraction (`# [x=.. y=.. type:..]` and inline metadata) in one shared utility path.

## Risks
- Metadata/comment attachment can drift if fence lifecycle is not centralized.
- Lookup scoping and rewrite behavior may regress if split incorrectly.
- Scope export/import may regress due to mixed embedded/docked scope rules.
- Handler export ordering may regress dependencies (e.g., `@lookup` before `@equations` references).

## Mitigations
- Keep a single central scanner for line iteration, fence state, and pending comments.
- Handlers parse/export payload; orchestrator owns sequencing and shared state transitions.
- Add golden-file round-trip tests for representative SFCR models.
- Add an ordering test that asserts stable block sequence for a mixed model export.

## Test Plan
- Keep all existing SFCR tests green.
- Add targeted tests for handler registration/dispatch:
  - unknown directive fallback
  - block parse index progression
  - comment attachment across fenced R blocks
- Add round-trip tests:
  - block-style SFCR -> parse -> export -> parse parity
  - R-style SFCR -> parse -> export -> parse parity
  - mixed-mode with `@circuit` passthrough

## Gradle Gate Test Procedures
Run these gates before merging any phase.

1. Run focused SFCR unit/integration suite:
```bash
./gradlew test --tests "com.lushprojects.circuitjs1.client.SFCR*"
```

2. Run full JVM test suite:
```bash
./gradlew test
```

3. If parser/exporter code was touched, run clean verification:
```bash
./gradlew clean test
```

4. Optional stricter gate for CI parity (if configured in branch):
```bash
./gradlew check
```

Gate pass criteria:
- All commands above (as applicable) exit with code `0`.
- No new failures in existing SFCR tests.
- New handler-dispatch tests are included and passing.
- Round-trip parity tests pass for block-style, R-style, and mixed-mode models.

## Acceptance Criteria
- `SFCRParser` and `SFCRExporter` line counts reduced substantially.
- At least `@init`, `@action`, `@lookup`, `@hints`, `@equations`, `@matrix` handled by registered classes.
- No regressions in existing SFCR unit/integration tests.
- New handlers located under `client/io/sfcr/handlers`, not scattered in element packages.

## Non-Goals
- Replacing core `dump/undump` mechanism.
- Moving SFCR syntax logic into each element class.
- Changing file format semantics during refactor.
