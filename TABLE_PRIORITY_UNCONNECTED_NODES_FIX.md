# Fix for Unconnected Nodes with Priority-Based Tables

## Problem

When two identical tables share the same stock columns but have different priorities (e.g., priority 4 and priority 5), the circuit fails to run with "node X unconnected" warnings. The simulation does not start.

### Example Circuit

```
Table 11: Priority 4, Columns: Stock_1, Stock_2, Stock_3, A-L-E
Table 12: Priority 5, Columns: Stock_1, Stock_2, Stock_3, A-L-E
```

After priority-based master registration:
- Table 12 becomes master for all stocks (higher priority)
- Table 11's output nodes become unconnected
- Simulation cannot start

## Root Cause

The priority system correctly determines which table is "master" for each shared stock:
- Master table: stamps voltage source, drives the stock voltage
- Non-master table: does NOT stamp voltage source (correct)

However, **non-master tables still allocate output nodes** (via `getPostCount()` returning `cols`), but these nodes were not connected to anything, leaving them floating.

### Code Flow

1. `getPostCount()` returns `cols` for all tables → allocates nodes for all columns
2. `getVoltageSourceCount()` returns count only for master columns → non-master columns get no voltage source
3. `stamp()` only stamped voltage sources for master columns
4. **Result**: Non-master output nodes exist but are unconnected → "node X unconnected" error

## Solution

In `TableElm.stamp()`, when a table is NOT master for a column, connect the output node to ground via a high-value resistor (10MΩ). This:
- Prevents unconnected node errors
- Has negligible effect on circuit behavior (very high resistance)
- Allows non-master tables to coexist with master tables

### Code Change

In `TableElm.stamp()`:

```java
if (isMasterForThisColumn) {
    // Master: stamp voltage source
    int vn = p.voltSource + sim.nodeList.size();
    sim.stampNonLinear(vn);
    sim.stampVoltageSource(0, nodes[col], p.voltSource);
} else {
    // NON-MASTER: connect to ground to prevent unconnected node
    // Use high-value resistor (10MΩ) so it doesn't affect circuit
    int outputNode = nodes[col];
    if (outputNode >= 0 && sim.nodeList != null && 
        outputNode < sim.nodeList.size()) {
        sim.stampResistor(outputNode, 0, 1e7); // 10MΩ to ground
    }
}
```

## Why 10MΩ?

- High enough to not affect circuit behavior (essentially open circuit)
- Low enough to prevent matrix issues (node has a path to ground)
- Standard practice in SPICE-like simulators for unused nodes

## Testing

### Before Fix
```
Loading circuit with Table 11 (priority 4) and Table 12 (priority 5)
Result: "node 4 unconnected", "node 9 unconnected", etc.
Simulation: Does not start
```

### After Fix
```
Loading circuit with Table 11 (priority 4) and Table 12 (priority 5)
Result: No unconnected node warnings
Simulation: Starts normally
Master: Table 12 drives all stocks (priority 5 > priority 4)
Non-master: Table 11 output nodes connected to ground via 10MΩ
```

## Edge Cases Handled

1. **No labeled nodes**: Non-master nodes still connected to ground
2. **Multiple tables sharing stocks**: Each non-master connects its own nodes
3. **Priority changes**: After clearing masters and re-registering, new non-masters get ground connections
4. **A-L-E columns**: Skipped (no electrical connection, display-only)

## Related Files

- `TableElm.java` - Main fix in `stamp()` method
- `ComputedValues.java` - Priority-based master registration
- `TableEditDialog.java` - Priority UI and change handling

## Summary

Non-master tables now properly connect their output nodes to ground via 10MΩ resistors, preventing "unconnected node" errors while maintaining correct priority-based master table behavior. The master table still fully controls the stock voltage, and non-master tables can compute values without interfering with circuit simulation.
