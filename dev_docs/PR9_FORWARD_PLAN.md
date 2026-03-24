# PR9 Forward Plan

Focused planning doc for **PR 9: Root -> `client.elements`** follow-up work.

## Completed in PR9

- [x] `ActionScheduler.java` -> `client.elements`
- [x] `ActionTimeDialog.java` -> `client.elements`
- [x] `ChipElm.java` -> `client.elements`
- [x] `EquationTableMarkdownDebugDialog.java` -> `client.elements`
- [x] `SFCSankeyRenderer.java` -> `client.elements`
- [x] `SFCSankeyViewer.java` -> `client.elements`

## Next Actionable PR9 Candidates

Root files still flagged `move to = elements` and not currently blocked by known high-coupling constraints:

- [ ] `CircuitValueSlotManager.java`
- [ ] `ExportCompositeActions.java`

## Deferred (Keep in `client` for now)

Hold due complex/package-private/cross-cutting coupling:

- [ ] `CirSim.java`
- [ ] `CircuitElm.java`
- [ ] `SimulationLoop.java`
- [ ] `Scope.java`
- [ ] `EditCompositeModelDialog.java`
- [ ] `MouseInputHandler.java`
- [ ] `FlipTransformController.java`
- [ ] `CircuitAnalyzer.java`
- [ ] `StatusInfoRenderer.java`

## Not PR9 Elements Targets

Route through architecture-specific track (not `elements`):

- [ ] `ElementLegacyFactory.java` -> `client.registry`
- [ ] `ElementRegistryBootstrap.java` -> `client.registry`
- [ ] `SFCRDocumentManager.java` -> `client.io`
- [ ] `SFCRDocumentState.java` -> `client.io`

## PR9b: More files to Move

### Scope

- [ ] Move `SFCRDagBlocksViewer.java` -> `client.elements.economics`
- [ ] Move `SFCRDagBlocksViewerTemplate.html` with it (required for `@Source` resource lookup)
- [ ] Update imports/usages in:
  - `CirSimCommandRouter.java`
  - `elements/economics/EquationTableElm.java`
  - `elements/EquationTableMarkdownDebugDialog.java`

### Sankey Templates (Dependency-Driven)

`SFCSankeyRenderer.java` and `SFCSankeyViewer.java` are already in `client.elements` and should stay there.
Remaining Sankey templates are referenced by `SFCSankeyViewer` `@Source(...)` and should move with viewer assets.

- [ ] Move `SankeyPlotlyEmbeddedTemplate.html` -> `client/elements/`
- [ ] Move `SankeyPlotlyStandaloneTemplate.html` -> `client/elements/`
- [ ] Move `SankeyD3EmbeddedTemplate.html` -> `client/elements/`
- [ ] Move `SankeyD3StandaloneTemplate.html` -> `client/elements/`
- [ ] Update `@Source` paths in `SFCSankeyViewer.java` after template moves

### Expression Engine Bundle (Dependency-Driven)

Move as one batch (do not split):

- [ ] Move `Expr.java` -> `client/elements/`
- [ ] Move `ExprParser.java` -> `client/elements/`
- [ ] Move `ExprState.java` -> `client/elements/`
- [ ] Update imports/usages across `client`, `elements/*`, and `io/*`

### PR9b Acceptance

- [ ] EquationTable context menu -> **View DAG Blocks** opens correctly
- [ ] DAG viewer content renders from moved template
- [ ] Sankey internal dialog renders in Plotly and D3 modes
- [ ] Sankey external window renders in Plotly and D3 modes
- [ ] Expression parsing/evaluation paths still compile and run for economics, math, and analog element flows

## Gates Per Batch

- [ ] `./gradlew compileJava`
- [ ] `./gradlew test`
- [ ] `./gradlew compileGwtDev`
- [ ] Manual smoke test for moved feature paths
