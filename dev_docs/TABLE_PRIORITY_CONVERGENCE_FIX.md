# Table Priority Convergence Fix

## Problem

When changing the priority of a table element via right-click properties or the TableEditDialog, the circuit would stop running with convergence problems. This occurred because:

1. Priority determines which table is the "master" for shared stock variables
2. Changing priority should trigger re-registration of master tables
3. The old code only set `analyzeFlag = true` which wasn't sufficient
4. Master table registrations remained stale, causing conflicting voltage sources

## Root Cause

The master table assignment happens during circuit initialization via `ComputedValues.registerMasterTable()`. When priority changes:
- The old master table still thinks it controls the voltage source
- The new high-priority table also tries to control the voltage source
- Both tables stamp voltage sources, creating a conflict
- Circuit matrix becomes singular → convergence failure

## Solution

### 1. Clear Master Tables on Priority Change

When priority changes, clear all master table registrations:
```java
ComputedValues.clearMasterTables();
```

This removes all existing master assignments, allowing tables to re-register with new priorities.

### 2. Force Full Circuit Analysis

After clearing master tables, trigger complete circuit rebuild:
```java
sim.needAnalyze();
```

This ensures:
- All elements call `setupPins()` again
- Tables re-register as masters with current priorities
- Circuit matrix is rebuilt from scratch
- Voltage sources are stamped correctly

## Implementation

### TableElm.setEditValue() - Right-Click Properties

```java
} else if (n == 1) {
    int oldPriority = priority;
    priority = Math.max(0, Math.min(100, (int)ei.value));
    // If priority changed, clear master tables to force re-registration
    if (oldPriority != priority) {
        ComputedValues.clearMasterTables();
        // Force full circuit analysis to rebuild with new priorities
        sim.needAnalyze();
    }
```

Key points:
- Store old priority value
- Compare before/after to detect changes
- Only clear/rebuild if priority actually changed
- Prevents unnecessary work when value unchanged

### TableEditDialog.applyChanges() - Edit Dialog

```java
int oldPriority = tableElement.getPriority();
try {
    int newPriority = Integer.parseInt(priorityBox.getText().trim());
    newPriority = Math.max(0, Math.min(100, newPriority));
    tableElement.setPriority(newPriority);
    // If priority changed, clear master tables to force re-registration
    if (oldPriority != newPriority) {
        ComputedValues.clearMasterTables();
    }
} catch (NumberFormatException e) {
    CirSim.console("Invalid priority value, keeping current: " + tableElement.getPriority());
}

// ... later in method ...

// Force full circuit analysis (important when priority changes)
sim.needAnalyze();
```

Key points:
- Clear master tables if priority changed
- Always call `needAnalyze()` at end (synchronization may affect other tables)
- Ensures all related tables are properly rebuilt

## Testing

To verify the fix works:

1. Create circuit with two tables sharing a stock (e.g., "Cash")
2. Set Table1 priority = 10, Table2 priority = 5
3. Verify Table1 is master (drives voltage)
4. Change Table2 priority to 15
5. Apply changes
6. Circuit should continue running (no convergence error)
7. Table2 should now be master for "Cash"

## Why This Fix Works

1. **Clean Slate**: Clearing master tables removes all stale assignments
2. **Priority-Based Re-registration**: During `setupPins()`, tables re-register with current priorities
3. **Highest Priority Wins**: `ComputedValues.registerMasterTable()` uses priority comparison
4. **Single Master**: Only one table per stock becomes master
5. **Proper Matrix**: Circuit matrix has exactly one voltage source per shared stock

## Related Code

- `ComputedValues.registerMasterTable()` - Priority-based master selection
- `ComputedValues.clearMasterTables()` - Reset all registrations
- `TableElm.registerAsMasterForOutputNames()` - Register with priority
- `CirSim.needAnalyze()` - Trigger full circuit rebuild
- `CirSim.resetAction()` - Example of proper master table clearing during reset

## Future Improvements

Possible enhancements:
- Visual feedback showing which table is master for each stock
- Warning message when priority conflicts exist
- Automatic priority assignment based on table order
- Debug mode showing master table assignments

## Notes

- This fix maintains backward compatibility
- Priority changes require circuit re-analysis (expected behavior)
- No performance impact during normal simulation
- Only affects priority change operations
