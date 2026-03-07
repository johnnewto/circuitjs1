# DAG Viewer Reference

## Overview

`SFCRDagBlocksViewer` adds an sfcr-style DAG blocks plot for equation systems.

- Trigger: right-click an `EquationTableElm` and select **View DAG Blocks Plot...**
- Scope: aggregates rows from all `EquationTableElm` elements in the circuit
- Output: opens/reuses a separate popup window with a Cytoscape-rendered dependency graph

## Dependency Modes

The viewer supports two dependency interpretations:

1. **Same-Period** (default)
   - Uses `Expr.collectSamePeriodRefs(...)`
   - Excludes references nested inside historical operators like `lag`, `last`, `integrate`, `diff`, `smooth`
   - Represents algebraic coupling for current timestep

2. **Historical + Same-Period**
   - Uses `Expr.collectAllRefs(...)`
   - Includes historical operator references in edge construction

The popup includes a mode toggle button to switch between these views.

## Parameters Section Filter

- The popup includes an **Ignore # Parameters section** checkbox.
- When enabled, equations are excluded starting at a comment row whose text begins with `# Parameters` and continuing until the next `#` comment row in that same equation table.
- This filter is applied independently per `EquationTableElm` and works with both dependency modes.

## Graph Semantics

- Node: equation output name (`EquationTableElm.getOutputName(row)`)
- Edge `A -> B`: equation for `B` references `A`
- Block: strongly connected component (SCC)
- Cyclical node: node in SCC size > 1, or SCC containing a self-loop

Blocks are ordered using a topological pass over the SCC condensation graph.

## Layout

- Rendering uses Cytoscape.js for drawing and `cytoscape-dagre` (Sugiyama-style layered layout) for node positioning.
- The Dagre pass reduces crossings and vertical spread to produce a more compact DAG view, similar to `sfcr_dag_blocks_plot` compactness goals.
- If Cytoscape fails to load, the popup shows a load error message.
- The popup includes a **Direction** selector:
  - `Top → Bottom` (default) for mostly downward flow
  - `Left → Right` for horizontal flow
- Dagre uses `acyclicer: greedy`, `network-simplex` ranking, and edge weights to bias shorter, cleaner primary flow directions.

## Labels & Legend

- **Labels** selector:
  - `Compact` (default): truncated node labels for dense graphs
  - `Full`: full equation output names
  - `None`: hide node labels
- Always-visible status badges show active dependency mode and section filter state.
- A collapsible **Legend** shows:
  - color-to-block mapping (`Block N`)
  - shape mapping (`Cyclical` vs `Non-cyclical`)
- Clicking a node highlights its local neighborhood (incoming/outgoing adjacent edges and nodes) and dims unrelated graph elements; clicking empty space clears highlight.

## Integration Points

- Menu declaration and action routing: `CirSim.java`
  - `elmDagBlocksMenuItem`
  - `viewDagBlocks` command handler
  - context enablement for `EquationTableElm`
- Viewer implementation: `SFCRDagBlocksViewer.java`
- Expression reference extraction:
  - `Expr.collectSamePeriodRefs(...)`
  - `Expr.collectAllRefs(...)`

## Notes

- Duplicate output names across equation tables are de-duplicated by first occurrence.
- Edges to symbols not defined by equation outputs are ignored in the displayed DAG.
- Popup windows are tracked via `window.plotlyWindows` for existing close-all behavior compatibility.
