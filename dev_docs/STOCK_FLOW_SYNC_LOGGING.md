# Console Logging for Stock-Flow Synchronization

## Added Logging to Track Synchronization

To help debug and verify that stock-flow synchronization is working correctly, I've added comprehensive console logging to `StockFlowRegistry`.

## What Gets Logged

### 1. When `synchronizeRelatedTables()` is Called

**Entry Log:**
```
StockFlowRegistry: Synchronizing X tables sharing stocks: [Stock1, Stock2, ...]
```
or
```
StockFlowRegistry: No related tables to synchronize (only 1 table)
```

**Exit Log:**
```
StockFlowRegistry: Updated X table(s)
```

### 2. When Each Individual Table is Synchronized

**Per-table logs:**
```
  ✓ TableName updated for stock 'StockName' (3 → 5 rows)
```
or
```
  TableName already in sync
```
or
```
  TableName has no shared stocks, skipping
```
or
```
  Skipping recursive sync for TableName
```

## Example Console Output

### Scenario 1: Moving Row in Table A (affects Table B)
```
StockFlowRegistry: Synchronizing 2 tables sharing stocks: [Cash]
  ✓ Table A updated for stock 'Cash' (4 → 4 rows)
  ✓ Table B updated for stock 'Cash' (3 → 4 rows)
StockFlowRegistry: Updated 2 table(s)
```

### Scenario 2: Adding Row in Table A
```
StockFlowRegistry: Synchronizing 2 tables sharing stocks: [Cash]
  ✓ Table A updated for stock 'Cash' (4 → 5 rows)
  ✓ Table B updated for stock 'Cash' (4 → 5 rows)
StockFlowRegistry: Updated 2 table(s)
```

### Scenario 3: Reordering Rows (no row count change)
```
StockFlowRegistry: Synchronizing 2 tables sharing stocks: [Cash]
  ✓ Table A updated for stock 'Cash' (5 → 5 rows)
  ✓ Table B updated for stock 'Cash' (5 → 5 rows)
StockFlowRegistry: Updated 2 table(s)
```

### Scenario 4: Table with No Shared Stocks
```
StockFlowRegistry: No related tables to synchronize (only 1 table)
  Table A has no shared stocks, skipping
```

### Scenario 5: Recursive Sync Attempt (prevented)
```
StockFlowRegistry: Synchronizing 2 tables sharing stocks: [Cash]
  ✓ Table A updated for stock 'Cash' (4 → 5 rows)
  Skipping recursive sync for Table A
StockFlowRegistry: Updated 1 table(s)
```

## Where Synchronization is Called

Based on the current implementation, `StockFlowRegistry.synchronizeRelatedTables()` is called in:

### 1. **TableEditDialog** - Immediate Row Operations
- `moveRow()` - Line ~787 - When user clicks ⇑ or ⇓
- `insertRowAfter()` - Line ~747 - When user clicks ⧾ (add row)
- `deleteRow()` - Line ~807 - When user clicks ⧿ (delete row)
- `applyChanges()` - Line ~1049 - When user clicks Apply/OK button

### 2. **CirSim** - Circuit Lifecycle
- `readCircuit()` end - When circuit is loaded from file
  - Calls `synchronizeAllTables()` which internally uses `synchronizeTable()`

### 3. **TableElm** - Stock Name Changes
- When `setColumnHeader()` is called and stock name changes
  - Unregisters old stock, registers new stock
  - Triggers synchronization

## Expected Frequency

### During Normal Editing:
- **Per row move:** 1 sync call (immediate)
- **Per row add:** 1 sync call (immediate)
- **Per row delete:** 1 sync call (immediate)
- **On Apply button:** 1 sync call (catches column header changes, etc.)

### During Circuit Load:
- **Once** at the end of `readCircuit()`

## Checking if Sync is Called Often Enough

You should see console logs:

✅ **After every up/down arrow click** - Row moves immediately
✅ **After every add row button** - New row appears immediately
✅ **After every delete row button** - Row removed immediately
✅ **After Apply/OK button** - Catches any column header changes
✅ **After circuit load** - All tables synchronized on load

If you're NOT seeing logs in one of these scenarios, there may be a missing call.

## How to Use This for Debugging

1. **Open browser console** (F12 in most browsers)
2. **Look for "StockFlowRegistry:" messages**
3. **Verify logs appear when expected:**
   - Click arrow buttons → should see sync logs
   - Add/delete rows → should see sync logs
   - Apply button → should see sync logs
   - Load circuit → should see sync logs

4. **Check the details:**
   - Are the right tables being updated?
   - Are row counts changing as expected?
   - Are shared stocks being identified correctly?

## Troubleshooting

### If you see too many logs:
- Check if Apply button is being triggered automatically
- Check if row operations are calling sync multiple times

### If you see no logs:
- Verify browser console is open
- Check that `CirSim.console()` is working
- Ensure tables share at least one stock name

### If rows aren't synchronizing:
- Check logs to see if sync was called
- Check if tables were identified as "already in sync"
- Check if stocks are registered correctly in the registry

## Implementation Details

**Modified Method:**
```java
public static void synchronizeRelatedTables(TableElm triggerTable) {
    // ... find affected tables ...
    
    // Log entry
    if (affectedTables.size() > 1) {
        CirSim.console("StockFlowRegistry: Synchronizing " + affectedTables.size() + 
                      " tables sharing stocks: " + sharedStocks);
    }
    
    // Sync each table with detailed logging
    int tablesUpdated = 0;
    for (TableElm table : affectedTables) {
        boolean wasModified = synchronizeTable(table, triggerTable);
        if (wasModified) {
            tablesUpdated++;
        }
    }
    
    // Log exit
    if (tablesUpdated > 0) {
        CirSim.console("StockFlowRegistry: Updated " + tablesUpdated + " table(s)");
    }
}
```

**Per-table logging in `synchronizeTable()`:**
```java
public static boolean synchronizeTable(TableElm table, TableElm priorityTable) {
    // ... synchronization logic ...
    
    if (synchronizeTableColumn(table, col, mergedRows)) {
        modified = true;
        CirSim.console("  ✓ " + table.getTableTitle() + " updated for stock '" + 
                      stockName + "' (" + oldRows + " → " + table.getRows() + " rows)");
    }
    
    // ... more logging ...
}
```

## Files Modified

- `StockFlowRegistry.java` - Added logging to `synchronizeRelatedTables()` and `synchronizeTable()`

## Testing

After building and running, you should be able to:
1. Open browser console
2. Create two tables with same stock name
3. Perform operations and see logs
4. Verify synchronization is happening at the right times
