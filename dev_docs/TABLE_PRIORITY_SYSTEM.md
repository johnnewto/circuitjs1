# Table Priority System Implementation

## Overview

Added a priority system to TableElm that controls which table becomes the "master" for shared stock variables. When multiple tables have columns with the same stock name, the table with the **highest priority** becomes the master and is responsible for computing and driving that stock's voltage output.

## Key Features

- **Priority Range**: 0-100 (integer values)
- **Default Priority**: 5
- **Master Selection**: Tables with higher priority become masters for shared stocks
- **Tie Breaking**: When priorities are equal, the first table to register becomes master (preserves backward compatibility)
- **Evaluation Order**: Higher priority tables are evaluated first

## Implementation Details

### 1. TableElm Changes

**New Field:**
```java
protected int priority = 5; // Priority for master table selection
```

**New Methods:**
```java
public int getPriority()
public void setPriority(int priority)
```

**Updated Serialization:**
- `TableDataManager.dump()`: Now includes priority value in saved format
- `TableDataManager.parseTableData()`: Reads priority with backward compatibility (defaults to 5 for old files)

**Updated Master Registration:**
- `registerAsMasterForOutputNames()`: Now passes priority to `ComputedValues.registerMasterTable()`

**Updated Edit Properties:**
- Added "Priority (0-100, higher=master)" as property #1 in right-click edit menu
- Clamped to 0-100 range for safety

### 2. ComputedValues Changes

**New Storage:**
```java
private static HashMap<String, Integer> masterTablePriorities;
```

**Updated Method:**
```java
public static boolean registerMasterTable(String name, Object table, int priority)
```

**Logic:**
- Stores priority for each master table
- When a new table tries to register for a stock:
  - If no master exists: new table becomes master
  - If new table has **higher priority**: replaces current master
  - If new table has **equal or lower priority**: current master remains

**Backward Compatibility:**
```java
public static boolean registerMasterTable(String name, Object table) {
    return registerMasterTable(name, table, 5); // Default priority
}
```

### 3. TableEditDialog Changes

**New UI Component:**
- Added `priorityBox` TextBox in title panel
- Shows current priority value
- Updates are tracked and applied with validation
- Tooltip: "Priority for master table selection (higher = evaluated first, default = 5)"

**Data Handling:**
- `applyChanges()`: Reads priority from text box, validates, and clamps to 0-100
- Invalid inputs are logged and current value is preserved

### 4. File Format

**New Format:**
```
253 <x> <y> <x2> <y2> <flags> <rows> <cols> <cellWidth> <cellHeight> <cellSpacing> 
    <showInitialValues> <decimalPlaces> <showCellValues> <collapsedMode> <priority>
    <tableTitle> <tableUnits> ...
```

**Backward Compatibility:**
- Old files without priority field default to priority=5
- Parser uses token peeking to detect format version
- New files save with priority value

## Usage

### Setting Priority via Edit Dialog

1. Double-click a table to open TableEditDialog
2. See "Priority:" field next to "Table Title:"
3. Enter a number 0-100 (higher = higher priority)
4. Click Apply/OK to save

### Setting Priority via Right-Click Properties

1. Right-click a table
2. Select "Edit" or "Properties"
3. Find "Priority (0-100, higher=master)" slider/input
4. Adjust value
5. Click OK

### How It Works

**Example: Two tables with shared stock "Cash"**

- **Table A**: Priority = 10, has column "Cash"
- **Table B**: Priority = 5, has column "Cash"

Result: **Table A becomes master** for "Cash" stock
- Table A computes the voltage and drives the labeled node
- Table B evaluates its equations but doesn't drive the voltage source
- Only Table A's output affects the circuit

**Example: Equal Priority**

- **Table A**: Priority = 5, registered first
- **Table B**: Priority = 5, registered second

Result: **Table A remains master** (first-come, first-served)

## Use Cases

### 1. Primary vs. Backup Tables
Set primary table to priority 10, backup to priority 1

### 2. Master Balance Sheet
Balance sheet at priority 10, transaction tables at priority 5

### 3. Override During Testing
Temporarily set test table to priority 99 to override production tables

### 4. Hierarchical Systems
- Level 1 (Strategy): Priority 20
- Level 2 (Operations): Priority 10
- Level 3 (Details): Priority 5

## Technical Notes

### Master Selection Timing

Master registration happens during:
1. Circuit initialization (when `setupPins()` is called)
2. Circuit reset (via `reset()` method)
3. When tables are added/modified

### Priority Changes

Changing priority requires circuit re-analysis:
- Close and reopen circuit file, OR
- Remove and re-add tables, OR  
- Reset simulation

The system automatically re-registers masters on reset.

### A-L-E Columns

A-L-E (computed) columns are **never** registered for master selection - they are purely display columns calculated from other column values.

### Performance

Priority checking adds minimal overhead:
- Single integer comparison during registration
- No impact on per-timestep simulation performance
- HashMap lookup remains O(1)

## Testing

Test circuit created: `/war/circuits/test-table-priority.txt`

This circuit demonstrates:
- Table 1 with priority 10
- Table 2 with priority 5
- Both tables sharing "Stock0" column
- Table 1 should be master

## Future Enhancements

Possible improvements:
- Visual indicator in table showing master status
- Debug dialog showing priority values
- Auto-priority assignment based on table creation order
- Priority groups/categories

## Backward Compatibility

✓ Old circuit files load correctly (default to priority 5)
✓ Existing tables work without modification
✓ API maintains backward compatibility with default priority parameter
✓ File format gracefully handles missing priority field

## Files Modified

1. `TableElm.java` - Added priority field, accessors, edit properties
2. `ComputedValues.java` - Updated master registration with priority logic
3. `TableDataManager.java` - Updated serialization/deserialization
4. `TableEditDialog.java` - Added priority UI field and validation

## Summary

The priority system provides fine-grained control over master table selection for shared stocks, enabling complex multi-table circuits with deterministic evaluation order. The implementation maintains full backward compatibility while providing an intuitive interface for both quick edits (right-click) and detailed configuration (edit dialog).
