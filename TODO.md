# CircuitJS1 TODOs

- investigate last()  when timestep is less that one
- [x] increase equation table size


## add equation table prefix or suffix system
so that equations can be not duplicated and  written without _UK, _US

## Add warning to action editor
for targets that are nt adjustable

## allow SFCR to save as SFCR
as well as table

## look at separating out compute
so to make it the backend for page display, running a subset of  R code

## Flows in equation table
make it so that each flow has a 1 ohm resistor so that value and flow are the same, investigate how this could simplify eval.



## Tables should reset
 To the node or computed value in both circuit and info tables

## Stock-Flow / Economic Modeling

### Net Worth Visualization for Sankey Diagrams
**File:** [SFCSankeyViewer.java](src/com/lushprojects/circuitjs1/client/SFCSankeyViewer.java#L49-L55)

Future enhancement - scale the WIDTH of sector endpoint nodes proportionally to accumulated stock value:
- Wider bars = more wealth accumulated in that sector
- Would require getting integrated stock values from associated GodlyTableElm
- Pass `nodeWidths[]` array in JSON alongside `nodeColors[]`
- Modify D3 `node.append('rect').attr('width', d => scaleWidth(d.stockValue))`

---

### Implement Probe Creation for SFCR Scope Variables
**File:** [SFCRParser.java](src/com/lushprojects/circuitjs1/client/SFCRParser.java#L1564-L1577)

The `addScopes()` method currently skips scope creation - needs proper implementation:
- ProbeElm needs to connect to a labeled node, not just reference a variable name
- Scope/probe creation requires specific geometry setup that needs more investigation

---

### Review StockFlowRegistry.synchronizeAllTables() Placement
**File:** [CirSim.java](src/com/lushprojects/circuitjs1/client/CirSim.java#L5567)

```java
StockFlowRegistry.synchronizeAllTables();   // Todo-JN   prob needs to be removed, place elsewhere
```

This call may need to be moved to a more appropriate location in the initialization sequence.


section

---
