# TableElm Equation Enhancement - Dynamic Labeled Node Support

## Problem Fixed
Previously, equations in TableElm were hardcoded to use a fixed set of node names ("node1", "node2", etc.) which often didn't match the actual labeled nodes in users' circuits. This meant equations couldn't reference the actual labeled nodes that existed in the circuit.

## Solution Implemented
Modified `TableElm.java` to dynamically map actual labeled nodes from the circuit to equation variables `a-i`.

### Key Changes:

1. **Dynamic Node Discovery**: `updateExpressionState()` now scans `LabeledNodeElm.labelList` to find all actual labeled nodes in the circuit.

2. **Sorted Mapping**: Labeled nodes are sorted alphabetically for consistent variable assignments:
   - First labeled node (alphabetically) → variable `a`
   - Second labeled node → variable `b` 
   - And so on up to the 9th node → variable `i`

3. **Improved Error Messages**: `getAvailableVariablesString()` provides helpful debugging info showing which variables map to which labeled nodes.

### How It Works:
- When a cell is in equation mode, variables `a-i` now refer to actual labeled nodes in the circuit
- The mapping is dynamic - if you add/remove labeled nodes, the mapping updates automatically
- Users can write equations like `a+b`, `max(a,b,c)`, `a>2.5?5:0`, etc. where `a`, `b`, `c` refer to real labeled nodes
- Time variable `t` is still available for time-based equations

### Example Usage:
If your circuit has labeled nodes: "input", "output", "vcc"
- Variable `a` = voltage at "input" node
- Variable `b` = voltage at "output" node  
- Variable `c` = voltage at "vcc" node
- Equation `a+b-c` would compute the sum of input and output voltages minus vcc

### Benefits:
- Equations now work with any labeled node names (not just hardcoded ones)
- More intuitive for users - they can see which variables map to which nodes
- Better debugging with informative error messages
- Maintains backward compatibility

### Code Changes:
- Modified `updateExpressionState()` method for dynamic node mapping
- Added `getAvailableVariablesString()` helper for better error messages
- Updated comments to reflect new behavior
- Used GWT-compatible sorting (bubble sort instead of Arrays.sort)

The fix successfully resolves the issue where equation mode wasn't properly using labeled node names.