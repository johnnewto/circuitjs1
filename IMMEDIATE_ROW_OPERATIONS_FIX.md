# Immediate Row Operations in Table Edit Dialog

## Problem Statement

Row operations (move up/down, add, delete) in the table edit dialog were not immediately reflected in the table or synchronized with related tables. Users had to click the "Apply" or "OK" button to see changes take effect.

### Previous Behavior
1. Click up/down arrow to reorder rows
   - ✅ Row description text moved in the grid immediately
   - ❌ Equation values didn't move until Apply clicked
   - ❌ Related tables weren't updated until Apply clicked
2. Add a new row
   - ❌ Row not added to actual table until Apply clicked
   - ❌ Related tables not synchronized until Apply clicked
3. Delete a row
   - ❌ Row not removed from actual table until Apply clicked
   - ❌ Related tables not synchronized until Apply clicked

## Solution

Modified `TableEditDialog` to apply changes to the `TableElm` immediately when row operations occur, and trigger synchronization with related tables right away.

### Changes Made

#### 1. Immediate Row Movement (`moveRow()`)

**Before:**
```java
private void moveRow(int fromIndex, int toIndex) {
    // Swap local cellData array
    String[] tempRow = cellData[fromIndex];
    cellData[fromIndex] = cellData[toIndex];
    cellData[toIndex] = tempRow;
    
    // Swap row descriptions in TableElm
    String tempDesc = tableElement.getRowDescription(fromIndex);
    tableElement.setRowDescription(fromIndex, tableElement.getRowDescription(toIndex));
    tableElement.setRowDescription(toIndex, tempDesc);
    
    // Only update UI grid - changes not applied to TableElm until Apply button
    markChanged();
    populateGrid();
}
```

**After:**
```java
private void moveRow(int fromIndex, int toIndex) {
    // Swap local cellData array
    String[] tempRow = cellData[fromIndex];
    cellData[fromIndex] = cellData[toIndex];
    cellData[toIndex] = tempRow;
    
    // Swap row descriptions in TableElm
    String tempDesc = tableElement.getRowDescription(fromIndex);
    tableElement.setRowDescription(fromIndex, tableElement.getRowDescription(toIndex));
    tableElement.setRowDescription(toIndex, tempDesc);
    
    // *** IMMEDIATE UPDATE: Apply equation changes to TableElm right away ***
    for (int col = 0; col < dataCols; col++) {
        tableElement.setCellEquation(fromIndex, col, cellData[fromIndex][col]);
        tableElement.setCellEquation(toIndex, col, cellData[toIndex][col]);
    }
    
    // *** Trigger immediate synchronization with related tables ***
    StockFlowRegistry.synchronizeRelatedTables(tableElement);
    
    markChanged();
    populateGrid();
    
    // *** Refresh simulation display ***
    if (sim != null) {
        sim.repaint();
    }
}
```

**Key Changes:**
- Immediately updates equations in TableElm for both affected rows
- Triggers synchronization with related tables
- Forces screen repaint to show changes

#### 2. Immediate Row Addition (`insertRowAfter()`)

**Before:**
```java
private void insertRowAfter(int rowIndex) {
    dataRows++;
    // ... expand local cellData array ...
    
    // Only update local data - changes not applied until Apply button
    markChanged();
    populateGrid();
}
```

**After:**
```java
private void insertRowAfter(int rowIndex) {
    dataRows++;
    // ... expand local cellData array ...
    
    // *** IMMEDIATE UPDATE: Resize TableElm and apply all changes ***
    tableElement.resizeTable(dataRows, dataCols);
    
    // Apply all cell equations to TableElm
    for (int row = 0; row < dataRows; row++) {
        for (int col = 0; col < dataCols; col++) {
            tableElement.setCellEquation(row, col, cellData[row][col]);
        }
    }
    
    // *** Trigger immediate synchronization with related tables ***
    StockFlowRegistry.synchronizeRelatedTables(tableElement);
    
    markChanged();
    populateGrid();
    
    // *** Refresh simulation display ***
    if (sim != null) {
        sim.repaint();
    }
}
```

**Key Changes:**
- Resizes the TableElm immediately
- Applies all cell equations to TableElm
- Triggers synchronization with related tables
- Forces screen repaint

#### 3. Immediate Row Deletion (`deleteRow()`)

**Before:**
```java
private void deleteRow(int rowIndex) {
    if (dataRows <= 1) return; // Protection
    
    dataRows--;
    // ... shrink local cellData array ...
    
    // Only update local data - changes not applied until Apply button
    markChanged();
    populateGrid();
}
```

**After:**
```java
private void deleteRow(int rowIndex) {
    if (dataRows <= 1) return; // Protection
    
    dataRows--;
    // ... shrink local cellData array ...
    
    // *** IMMEDIATE UPDATE: Resize TableElm and apply all changes ***
    tableElement.resizeTable(dataRows, dataCols);
    
    // Apply all cell equations to TableElm
    for (int row = 0; row < dataRows; row++) {
        for (int col = 0; col < dataCols; col++) {
            tableElement.setCellEquation(row, col, cellData[row][col]);
        }
    }
    
    // *** Trigger immediate synchronization with related tables ***
    StockFlowRegistry.synchronizeRelatedTables(tableElement);
    
    markChanged();
    populateGrid();
    
    // *** Refresh simulation display ***
    if (sim != null) {
        sim.repaint();
    }
}
```

**Key Changes:**
- Resizes the TableElm immediately
- Applies all remaining cell equations to TableElm
- Triggers synchronization with related tables
- Forces screen repaint

## User Experience After Fix

### Scenario 1: Reordering Rows
1. User clicks ⇑ (up arrow) on "Wages" row
2. **Immediately:**
   - Row description moves up ✅
   - Equation values move with the row ✅
   - Other tables sharing same stock see the reordered rows ✅
   - Circuit display updates ✅
3. No need to click Apply - changes are live!

### Scenario 2: Adding a Row
1. User clicks ⧾ (add row) button
2. **Immediately:**
   - New row appears in the grid ✅
   - TableElm is resized with new row ✅
   - Other tables sharing same stock receive the new row ✅
   - Circuit display updates ✅
3. User can start typing equation in new row right away

### Scenario 3: Deleting a Row
1. User clicks ⧿ (delete row) button
2. **Immediately:**
   - Row disappears from the grid ✅
   - TableElm is resized without deleted row ✅
   - Other tables sharing same stock lose the deleted row ✅
   - Circuit display updates ✅
3. Change is reflected everywhere instantly

## Implementation Details

### Key Operations in Each Method

**Move Row:**
- Update only the 2 affected rows (fromIndex and toIndex)
- Swap equations using `setCellEquation()`
- Trigger sync once

**Add Row:**
- Resize table using `resizeTable()`
- Reapply ALL equations (simple, ensures consistency)
- Trigger sync once

**Delete Row:**
- Resize table using `resizeTable()`
- Reapply ALL equations (simple, ensures consistency)
- Trigger sync once

### Performance Considerations

- **Move operations:** Very fast - only 2 rows * N columns are updated
- **Add/Delete operations:** Slightly slower - all rows * columns are reapplied, but still negligible for typical table sizes (< 100 rows)
- **Synchronization:** Runs once per operation, efficiently updates related tables

### Interaction with Apply Button

The Apply button (`applyChanges()`) is now somewhat redundant for row operations, but it's still useful for:
- Applying changes to column headers (stock names)
- Applying changes to initial values
- Applying changes to column types
- Batching multiple cell equation edits

The Apply button now mostly handles column-level changes and cell equation edits, while row operations are immediate.

## Edge Cases Handled

1. **Last row protection:** Cannot delete if only 1 row remains
2. **Bounds checking:** Move operations check valid indices
3. **Null safety:** All operations check sim != null before repaint
4. **Synchronization guard:** StockFlowRegistry prevents recursive sync
5. **Row order preservation:** Trigger table's order is respected (from previous fix)

## Files Modified

- `TableEditDialog.java` - Updated `moveRow()`, `insertRowAfter()`, `deleteRow()` methods

## Testing Checklist

- [ ] Test: Reorder rows with arrows → equations move immediately
- [ ] Test: Add row → appears in table and related tables immediately
- [ ] Test: Delete row → removed from table and related tables immediately
- [ ] Test: Multiple rapid operations → no errors or corruption
- [ ] Test: Reorder rows in Table A → Table B updates immediately
- [ ] Test: Add row in Table B → Table A receives it immediately
- [ ] Test: Delete shared row in Table A → disappears from Table B immediately
- [ ] Test: Edit cell equation → still need Apply button (expected)
- [ ] Test: Change stock name → still need Apply button (expected)

## Benefits

1. **Better UX:** Changes are visible immediately - no waiting for Apply
2. **Live synchronization:** Related tables update in real-time
3. **Fewer surprises:** What you see in the dialog is what's in the table
4. **Faster workflow:** No need to keep clicking Apply during row operations
5. **Consistent behavior:** Row description and equation move together

## Known Behavior

- **Apply button still needed for:** Column headers, initial values, column types, cell equation edits
- **Immediate for:** Row reordering, row addition, row deletion
- **hasChanges flag:** Still set to true even though changes are applied (maintains consistency with Apply button workflow)
