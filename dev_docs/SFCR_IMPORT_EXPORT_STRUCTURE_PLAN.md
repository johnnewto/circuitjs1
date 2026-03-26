# SFCR Import/Export Structure Plan

## Goal
Refactor SFCR import/export into a cleaner handler-driven architecture with clear ownership boundaries, lower parser/exporter complexity, and safer long-term extension.

## Scope
- `src/com/lushprojects/circuitjs1/client/io/SFCRParser.java`
- `src/com/lushprojects/circuitjs1/client/io/SFCRExporter.java`
- `src/com/lushprojects/circuitjs1/client/io/sfcr/*`
- `src/com/lushprojects/circuitjs1/client/io/sfcr/handlers/*`
- SFCR tests under `test/java/com/lushprojects/circuitjs1/client/SFCR*`

Out of scope:
- Behavioral changes to SFCR syntax semantics
- New SFCR directives
- UI feature changes

## Current Issues
1. `SFCRParser` and `SFCRExporter` are still large monoliths despite handler infrastructure.
2. `SFCRParseContext` and `SFCRExportContext` are thin wrappers with heavy back-delegation.
3. R-style and block-style paths duplicate logic and increase maintenance risk.
4. Shared helper logic is split across parser/exporter instead of centralized utilities.
5. Handler migration is partial: some blocks still parsed/exported through legacy private methods.

## Target Architecture

### Principles
- Parser/exporter act as orchestrators only.
- Block-specific behavior lives in block handlers.
- Context objects hold mutable state and shared services.
- Unknown directives handled consistently via fallback strategy.
- Export order explicit and tested.

### Package Shape
- `io/`
  - `SFCRParser` (orchestration)
  - `SFCRExporter` (orchestration)
  - `SFCRUtil` (shared pure helpers)
  - `SFCRParseResult`, `SFCRParseResultExporter`
- `io/sfcr/`
  - `SFCRParseContext` (parse state + services)
  - `SFCRExportContext` (export state + services)
  - `SFCRBlockParseHandlerRegistry`
  - `SFCRBlockExportHandlerRegistry`
  - `SFCRBlockType`, `ParseResult`, `ParseWarning`
  - `SFCRSyntaxNormalizer` (optional consolidation of R-style normalization)
- `io/sfcr/handlers/`
  - one handler per directive for parse/export responsibilities

## Implementation Phases

### Phase 1: Baseline + Guardrails
- Freeze current behavior with focused SFCR regression tests.
- Add/confirm tests for:
  - block parse index progression
  - unknown directive fallback behavior
  - round-trip ordering guarantees (`SFCRParseResult.blockDumps`)
- Keep all existing tests green before structural moves.

Deliverable:
- green SFCR test baseline proving no accidental behavior change during refactor.

### Phase 2: Complete Parse Handler Migration
- Move remaining parser block logic from `SFCRParser` into handlers:
  - `@matrix`, `@scope`, `@circuit`, `@sankey`, `@info`.
- Reduce direct block parsing branches in `SFCRParser.parse()`.
- Keep line scanning, fence tracking, pending comments lifecycle in orchestrator.

Deliverable:
- `SFCRParser` no longer owns directive-specific parse implementation details.

### Phase 3: Context Ownership Refactor
- Expand `SFCRParseContext` to own parse state currently stored in parser fields.
- Expand `SFCRExportContext` to own categorized export collections and helper access.
- Remove `getParser()` / `getExporter()` dependence from handlers where possible.
- Replace delegation-heavy `*ForHandler()` bridge methods with direct context API.

Deliverable:
- handlers consume context APIs directly with minimal back-calls into parser/exporter.

### Phase 4: Export Path Simplification
- Move remaining export block assembly logic into export handlers.
- Keep exporter orchestration for:
  - top-level ordering
  - template merge
  - final blank-line normalization
- Ensure export ordering remains deterministic and explicit via `exportOrder()`.

Deliverable:
- `SFCRExporter` becomes orchestration-focused with reduced surface area.

### Phase 5: R-Style Consolidation
- Introduce a normalization strategy to reduce duplicate parse/export code paths:
  - normalize R-style parse input into internal block events/payloads
  - reuse block handlers for semantic processing
- Keep compatibility with existing R-style fixtures and metadata comments.

Deliverable:
- one semantic path for both styles, minimizing divergence risk.

### Phase 6: Utility Consolidation + Cleanup
- Move shared pure helpers into `SFCRUtil` (or dedicated small utility classes).
- Delete dead legacy methods after parity verification.
- Reduce class size and remove obsolete bridging code.

Deliverable:
- smaller, cohesive classes with clearer ownership boundaries.

## Testing Strategy

### Targeted First
- `./gradlew test --tests "com.lushprojects.circuitjs1.client.SFCR*"`

### Full Suite
- `./gradlew test`

### Clean Validation (when parser/exporter internals shift)
- `./gradlew clean test`

### Required Regression Coverage
- Block-format parse/export round-trip parity
- R-style parse/export round-trip parity
- Mixed-mode documents with fenced sections
- Comment attachment across fence boundaries
- Unknown directive skip/warn behavior
- Scope and lookup block ordering stability

## Acceptance Criteria
- No externally visible behavior regression for existing SFCR fixtures.
- Existing SFCR tests pass; new/updated tests cover migration seams.
- `SFCRParser` and `SFCRExporter` are orchestrators, not block-logic monoliths.
- Handlers are stateless; mutable parse/export state lives in context objects.
- Export order and fallback behavior are deterministic and documented.

## Risks and Mitigations
- Risk: comment/fence lifecycle regressions.
  - Mitigation: keep scanner lifecycle in orchestrator + add fence-focused tests.
- Risk: lookup rewrite or scoping drift.
  - Mitigation: dedicated lookup fixture tests and round-trip checks.
- Risk: ordering regressions between lookup/equations/matrix/scope blocks.
  - Mitigation: explicit order assertions and golden exports.

## Suggested Work Breakdown (Small PRs)
1. Test hardening and baseline assertions.
2. Parse handler completion for remaining directives.
3. Parse context ownership migration.
4. Export handler completion + exporter trimming.
5. R-style consolidation.
6. Cleanup and dead code removal.

## Definition of Done
- Structural refactor complete with green SFCR and full test suites.
- No feature-level syntax changes introduced.
- Documentation updated where class responsibilities changed.
