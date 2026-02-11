# CircuitJS1 TODOs

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

---

## UI / Editor

### EditCompositeModelDialog Mouse Event Stubs
**File:** [EditCompositeModelDialog.java](src/com/lushprojects/circuitjs1/client/EditCompositeModelDialog.java#L295-L302)

Auto-generated stubs for `onMouseOver` and `onMouseOut` - implement if hover behavior is needed for composite model editing.

## Performance / Optimization

### ✅ Speed Difference Resolved - GWT Optimization Level

**Issue:** Local build (http://127.0.0.1:8000) was 2x slower than GitHub Pages deployment.

**Root Cause:** GWT compiler optimization level differences:

| Build Method | Optimization | Runtime Speed |
|--------------|--------------|---------------|
| `./gradlew compileGwt` (default) | optimize=0 | Slow - 6.2s for 100s simulation |
| `./gradlew compileGwt -Pgwt.compiler.optimize=9` | optimize=9 | Fast - 3.1s for 100s simulation |
| `./dev.sh compile` (ant build) | optimize=9 (GWT default) | Fast - 3.1s for 100s simulation |
| GitHub Pages deployment | optimize=9 | Fast - 3.1s for 100s simulation |

**Solution Implemented:**
- Added `./dev.sh startprod` command that compiles with full optimization (optimize=9) and starts web server
- Updated README.md with documentation
- Updated WASM_MATRIX_SOLVER.md with performance comparison

**Key Finding:** 
Production-optimized V8 JavaScript (optimize=9) **matches WASM performance** for typical circuit sizes. WASM's primary benefit is consistency (avoiding GC pauses and JIT deoptimization) rather than raw speed advantage over fully-optimized JavaScript.

**Usage:**
```bash
./dev.sh startprod  # Compile with optimize=9 and start web server
```

**Related Files:**
- [dev.sh](dev.sh) - Added `startprod` command
- [README.md](README.md) - Updated command table
- [dev_docs/WASM_MATRIX_SOLVER.md](dev_docs/WASM_MATRIX_SOLVER.md) - Added V8 production mode performance section

---
