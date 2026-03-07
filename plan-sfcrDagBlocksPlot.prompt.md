## Plan: SFCR DAG Blocks Viewer (Menu Trigger)

DRAFT: Implement a first-pass `sfcr_dag_blocks_plot` equivalent as an external popup launched from the element context menu (right-click) on an equation table, using the Sankey viewer architecture as the baseline. The graph will aggregate equations from all `EquationTableElm` instances in the circuit (interpreting “all equation-like tables” as all equation tables), build a same-period dependency graph by default (`collectSamePeriodRefs`), compute SCC-based blocks plus cyclical flags, and render a directed layered DAG in a standalone HTML window. A historical-dependency option will be included as an explicit alternate mode, while keeping the default aligned with sfcr same-period semantics.

**Steps**
1. Add menu wiring in [src/com/lushprojects/circuitjs1/client/CirSim.java](src/com/lushprojects/circuitjs1/client/CirSim.java#L160-L230), [src/com/lushprojects/circuitjs1/client/CirSim.java](src/com/lushprojects/circuitjs1/client/CirSim.java#L989-L1003), [src/com/lushprojects/circuitjs1/client/CirSim.java](src/com/lushprojects/circuitjs1/client/CirSim.java#L6645-L6715), [src/com/lushprojects/circuitjs1/client/CirSim.java](src/com/lushprojects/circuitjs1/client/CirSim.java#L4760-L4825): add `elmDagBlocksMenuItem`, enable for `EquationTableElm`, and route `viewDagBlocks` action.
2. Create viewer class [src/com/lushprojects/circuitjs1/client/SFCRDagBlocksViewer.java](src/com/lushprojects/circuitjs1/client/SFCRDagBlocksViewer.java) modeled on Sankey external window flow in [src/com/lushprojects/circuitjs1/client/SFCSankeyViewer.java](src/com/lushprojects/circuitjs1/client/SFCSankeyViewer.java#L145-L167) and [src/com/lushprojects/circuitjs1/client/SFCSankeyViewer.java](src/com/lushprojects/circuitjs1/client/SFCSankeyViewer.java#L891-L905), with single-window reuse.
3. Add equation collection pass: iterate `elmList`, gather active rows (`outputName`, `equation`, mode filtering) from `EquationTableElm` via accessors in [src/com/lushprojects/circuitjs1/client/EquationTableElm.java](src/com/lushprojects/circuitjs1/client/EquationTableElm.java#L2410-L2760), skipping comment/empty rows.
4. Build dependency graph per sfcr method analog: parse each equation via `ExprParser` and collect refs with [src/com/lushprojects/circuitjs1/client/Expr.java](src/com/lushprojects/circuitjs1/client/Expr.java#L522-L566); default mode uses same-period refs; optional historical mode uses a second extraction path.
5. Implement block/cycle analysis in viewer: adjacency matrix/list → SCC decomposition (block id), mark cyclical nodes (SCC size > 1 or self-loop), then condense to DAG ordering for layered layout.
6. Render popup HTML/JS (Plotly-based for consistency with existing viewers): nodes colored by block, shape/style by cyclical flag, directed arrows, labels, and title; include lightweight mode toggle control (same-period vs historical) that rebuilds data in-window.
7. Add localization strings/menu labels and short internal docs reference md in dev_docs/DAG_viewer_reference.md

**Verification**
- Manual: right-click an `EquationTableElm` → “View DAG Blocks Plot…” opens popup and reuses same window on repeated invocations.
- Functional: verify graph includes equations across multiple equation tables, block colors remain stable, cycles/self-loops are flagged.
- Semantics: compare a small known model against `sfcr_dag_blocks_plot` expectations (same-period default), then confirm historical mode changes edges as intended.
- Safety: confirm no regression to existing Sankey/context menu behavior.

**Decisions**
- Trigger: context-menu action (not left-click).
- Scope: aggregate all `EquationTableElm` equations circuit-wide.
- Default edge semantics: same-period only.
- Additional mode: optional historical dependency view.
- Window behavior: reuse a single external DAG window.
