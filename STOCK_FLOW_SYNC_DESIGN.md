# Stock-Flow Row Synchronization Design

## Problem Statement

In stock-flow modeling with multiple TableElm/GodlyTableElm instances, different tables may contribute flows to the same stock (identified by matching `outputNames[col]`). When one table adds or removes a flow row, all tables sharing that stock should synchronize their rows to maintain consistency.

### Current Behavior

```java
// TableElm.doStep() - Line 241
boolean alreadyComputed = ComputedValues.isComputedThisStep(name);
if (alreadyComputed && existingValue != null) {
    columnSum = existingValue.doubleValue();  // Use pre-computed value
} else {
    // Compute ourselves (first table wins)
    for (int row = 0; row < rows; row++) {
        double v = getVoltageForCell(row, col);
        columnSum += v;
    }
}
```

**Issue:** Execution-order dependent calculation, but more critically: **mismatched row counts** when tables share stocks.

## Example Scenario

```
Table A (CALCULATE):         Table B (CONSUME):
Stock: "Cash"                Stock: "Cash"
Rows:                        Rows:
  - Sales: 100                 - Wages: -50
  - Interest: 5                - Rent: -20

User adds to Table B:
  - Utilities: -10

DESIRED: Both tables see all 5 rows (synchronized)
CURRENT: Table A has 2 rows, Table B has 3 rows (out of sync)
```

## Architecture Overview

### Key Components

1. **TableElm/GodlyTableElm**: Circuit elements with stock columns (`outputNames[]`)
2. **ComputedValues**: Centralized value storage (name → value)
3. **TableEditDialog**: UI for editing tables
4. **CirSim**: Main simulation controller

### Data Flow

```
User edits Table A → TableEditDialog.applyChanges()
                   → TableElm.resizeTable()
                   → Trigger: syncRelatedTables()
                   → Find all TableElm with matching outputNames
                   → Merge row descriptions & equations
                   → Update all affected tables
```

## Proposed Solution

### Phase 1: Row Registry & Synchronization Service

Create a **service class** that handles both tracking AND synchronization logic, keeping `TableElm` thin.

```java
// New class: StockFlowRegistry.java
package com.lushprojects.circuitjs1.client;

import java.util.*;

/**
 * StockFlowRegistry - Service class for managing stock-flow synchronization
 * 
 * Responsibilities:
 * - Track which tables share which stocks (registry)
 * - Collect and merge row descriptions across related tables
 * - Synchronize tables when stocks are shared
 * - Provide visual feedback data (shared stocks list)
 * 
 * This keeps TableElm focused on simulation logic, not synchronization.
 */
public class StockFlowRegistry {
    
    // Map: stock name → list of TableElm instances
    private static Map<String, List<TableElm>> stockToTables = new HashMap<>();
    
    // Cache of merged rows per stock (invalidated on changes)
    private static Map<String, LinkedHashSet<String>> mergedRowsCache = new HashMap<>();
    
    // Synchronization guard to prevent infinite recursion
    private static Set<TableElm> currentlySynchronizing = new HashSet<>();
    
    /**
     * Register a table's stock (column) for synchronization tracking
     */
    public static void registerStock(String stockName, TableElm table) {
        if (stockName == null || stockName.trim().isEmpty()) return;
        
        if (!stockToTables.containsKey(stockName)) {
            stockToTables.put(stockName, new ArrayList<>());
        }
        List<TableElm> tables = stockToTables.get(stockName);
        if (!tables.contains(table)) {
            tables.add(table);
            invalidateCache(stockName); // Cache is now stale
        }
    }
    
    /**
     * Unregister a table (e.g., when deleted or stock name changed)
     */
    public static void unregisterStock(String stockName, TableElm table) {
        if (stockToTables.containsKey(stockName)) {
            stockToTables.get(stockName).remove(table);
            invalidateCache(stockName);
        }
    }
    
    /**
     * Unregister all stocks for a table (e.g., on deletion)
     */
    public static void unregisterAllStocks(TableElm table) {
        List<String> stocksToRemove = new ArrayList<>();
        for (Map.Entry<String, List<TableElm>> entry : stockToTables.entrySet()) {
            if (entry.getValue().contains(table)) {
                entry.getValue().remove(table);
                stocksToRemove.add(entry.getKey());
            }
        }
        for (String stock : stocksToRemove) {
            invalidateCache(stock);
        }
    }
    
    /**
     * Get all tables that share this stock name
     */
    public static List<TableElm> getTablesForStock(String stockName) {
        return stockToTables.getOrDefault(stockName, new ArrayList<>());
    }
    
    /**
     * Clear registry (on circuit load/reset)
     */
    public static void clearRegistry() {
        stockToTables.clear();
        mergedRowsCache.clear();
        currentlySynchronizing.clear();
    }
    
    /**
     * Get all stocks shared by multiple tables
     */
    public static Set<String> getSharedStocks() {
        Set<String> shared = new HashSet<>();
        for (Map.Entry<String, List<TableElm>> entry : stockToTables.entrySet()) {
            if (entry.getValue().size() > 1) {
                shared.add(entry.getKey());
            }
        }
        return shared;
    }
    
    /**
     * Check if a stock is shared by multiple tables
     */
    public static boolean isSharedStock(String stockName) {
        List<TableElm> tables = stockToTables.get(stockName);
        return tables != null && tables.size() > 1;
    }
    
    /**
     * Invalidate merged rows cache for a stock
     */
    private static void invalidateCache(String stockName) {
        mergedRowsCache.remove(stockName);
    }
    
    /**
     * Collect all unique row descriptions for a given stock
     * Returns a LinkedHashSet to preserve insertion order
     * Uses caching for performance
     */
    public static LinkedHashSet<String> getMergedRowDescriptions(String stockName) {
        // Check cache first
        if (mergedRowsCache.containsKey(stockName)) {
            return new LinkedHashSet<>(mergedRowsCache.get(stockName));
        }
        
        LinkedHashSet<String> mergedRows = new LinkedHashSet<>();
        List<TableElm> tables = getTablesForStock(stockName);
        
        for (TableElm table : tables) {
            // Find column index for this stock in the table
            int colIndex = table.findColumnByStockName(stockName);
            if (colIndex >= 0) {
                // Collect all row descriptions from this table
                for (int row = 0; row < table.getRows(); row++) {
                    String desc = table.getRowDescription(row);
                    if (desc != null && !desc.trim().isEmpty()) {
                        mergedRows.add(desc.trim());
                    }
                }
            }
        }
        
        // Cache the result
        mergedRowsCache.put(stockName, new LinkedHashSet<>(mergedRows));
        
        return mergedRows;
    }
    
    /**
     * Synchronize a single table with merged rows for its stocks
     * 
     * @param table The table to synchronize
     * @return true if table was modified, false if already in sync
     */
    public static boolean synchronizeTable(TableElm table) {
        // Prevent recursive synchronization
        if (currentlySynchronizing.contains(table)) {
            return false;
        }
        
        try {
            currentlySynchronizing.add(table);
            
            boolean modified = false;
            
            // For each column in this table
            for (int col = 0; col < table.getCols(); col++) {
                String stockName = table.getColumnHeader(col);
                
                // Only synchronize if this stock is shared
                if (isSharedStock(stockName)) {
                    LinkedHashSet<String> mergedRows = getMergedRowDescriptions(stockName);
                    if (synchronizeTableColumn(table, col, mergedRows)) {
                        modified = true;
                    }
                }
            }
            
            return modified;
            
        } finally {
            currentlySynchronizing.remove(table);
        }
    }
    
    /**
     * Synchronize a specific column of a table with merged row descriptions
     * 
     * @param table The table to synchronize
     * @param col The column index
     * @param mergedRowDescriptions The merged row descriptions for this column's stock
     * @return true if table was modified
     */
    private static boolean synchronizeTableColumn(TableElm table, int col, 
                                                   LinkedHashSet<String> mergedRowDescriptions) {
        int currentRowCount = table.getRows();
        int newRowCount = mergedRowDescriptions.size();
        
        // Quick check: if row descriptions already match, skip
        if (currentRowCount == newRowCount && 
            checkRowsAlreadyMatch(table, mergedRowDescriptions)) {
            return false;
        }
        
        // Build mapping: existing row description → row index
        Map<String, Integer> existingRows = new HashMap<>();
        for (int row = 0; row < currentRowCount; row++) {
            String desc = table.getRowDescription(row);
            if (desc != null && !desc.trim().isEmpty()) {
                existingRows.put(desc.trim(), row);
            }
        }
        
        // Create new data structures with merged rows
        String[][] newCellEquations = new String[newRowCount][table.getCols()];
        String[] newRowDescriptions = new String[newRowCount];
        
        // Fill in merged rows
        int newRow = 0;
        for (String rowDesc : mergedRowDescriptions) {
            newRowDescriptions[newRow] = rowDesc;
            
            // If this row existed in current table, copy its equations
            if (existingRows.containsKey(rowDesc)) {
                int oldRow = existingRows.get(rowDesc);
                for (int c = 0; c < table.getCols(); c++) {
                    newCellEquations[newRow][c] = table.getCellEquation(oldRow, c);
                }
            } else {
                // New row - initialize with empty equations
                for (int c = 0; c < table.getCols(); c++) {
                    newCellEquations[newRow][c] = "";
                }
            }
            newRow++;
        }
        
        // Update table data via TableElm's update method
        table.updateRowData(newRowCount, newRowDescriptions, newCellEquations);
        
        return true;
    }
    
    /**
     * Check if table's current rows already match the merged rows
     */
    private static boolean checkRowsAlreadyMatch(TableElm table, 
                                                  LinkedHashSet<String> mergedRows) {
        if (table.getRows() != mergedRows.size()) {
            return false;
        }
        
        int row = 0;
        for (String mergedRow : mergedRows) {
            String tableRow = table.getRowDescription(row);
            if (tableRow == null || !tableRow.trim().equals(mergedRow)) {
                return false;
            }
            row++;
        }
        return true;
    }
    
    /**
     * Synchronize all tables that share stocks with the given table
     * 
     * @param triggerTable The table that triggered synchronization
     */
    public static void synchronizeRelatedTables(TableElm triggerTable) {
        Set<TableElm> affectedTables = new HashSet<>();
        
        // Find all tables that share any stock with trigger table
        for (int col = 0; col < triggerTable.getCols(); col++) {
            String stockName = triggerTable.getColumnHeader(col);
            if (stockName != null && !stockName.trim().isEmpty()) {
                List<TableElm> tables = getTablesForStock(stockName);
                affectedTables.addAll(tables);
            }
        }
        
        // Synchronize each affected table (including trigger table)
        for (TableElm table : affectedTables) {
            synchronizeTable(table);
        }
    }
    
    /**
     * Synchronize all tables in the circuit
     * Called after circuit load
     */
    public static void synchronizeAllTables() {
        // Get all unique tables from registry
        Set<TableElm> allTables = new HashSet<>();
        for (List<TableElm> tables : stockToTables.values()) {
            allTables.addAll(tables);
        }
        
        // Synchronize each table
        for (TableElm table : allTables) {
            synchronizeTable(table);
        }
    }
    
    /**
     * Get diagnostic information about the registry state
     * Useful for debugging
     */
    public static String getDiagnosticInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== StockFlowRegistry Diagnostics ===\n");
        sb.append("Total stocks tracked: ").append(stockToTables.size()).append("\n");
        sb.append("Shared stocks: ").append(getSharedStocks().size()).append("\n");
        sb.append("\nStock → Tables mapping:\n");
        
        for (Map.Entry<String, List<TableElm>> entry : stockToTables.entrySet()) {
            sb.append("  '").append(entry.getKey()).append("' → ");
            sb.append(entry.getValue().size()).append(" table(s)");
            if (entry.getValue().size() > 1) {
                sb.append(" [SHARED]");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
}
```
```

### Phase 2: Minimal TableElm Interface

`TableElm` only needs minimal methods to support synchronization - the heavy lifting is in `StockFlowRegistry`.

```java
// Add to TableElm.java
public class TableElm extends ChipElm {
    
    /**
     * Find column index by stock name
     * Package-private for use by StockFlowRegistry
     */
    int findColumnByStockName(String stockName) {
        if (outputNames == null) return -1;
        for (int i = 0; i < outputNames.length; i++) {
            if (stockName.equals(outputNames[i])) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Update row data during synchronization
     * Package-private for use by StockFlowRegistry
     * 
     * @param newRowCount New number of rows
     * @param newRowDescriptions New row descriptions
     * @param newCellEquations New cell equations
     */
    void updateRowData(int newRowCount, String[] newRowDescriptions, 
                      String[][] newCellEquations) {
        rows = newRowCount;
        rowDescriptions = newRowDescriptions;
        cellEquations = newCellEquations;
        
        // Recompile equations and reinitialize states
        equationManager.recompileAllEquations();
        dataManager.initTable();
    }
    
    /**
     * Trigger synchronization with other tables sharing the same stocks
     * Simple wrapper - all logic is in StockFlowRegistry
     */
    public void synchronizeWithRelatedTables() {
        StockFlowRegistry.synchronizeRelatedTables(this);
    }
}
```

### Phase 3: Registration & Lifecycle Management

Tables register/unregister their stocks - uses `StockFlowRegistry` methods.

```java
// Modify TableElm.java

// In constructor - register stocks
private void initTable() {
    dataManager.initTable();
    equationManager.recompileAllEquations();
    
    // Register all stocks with the registry
    registerAllStocks();
}

// Register stocks - simple delegation
private void registerAllStocks() {
    if (outputNames != null) {
        for (int col = 0; col < cols && col < outputNames.length; col++) {
            String stockName = outputNames[col];
            if (stockName != null && !stockName.trim().isEmpty()) {
                StockFlowRegistry.registerStock(stockName, this);
            }
        }
    }
}

// Unregister stocks when column header changes
public void setColumnHeader(int col, String newHeader) {
    if (col >= 0 && col < cols && outputNames != null && col < outputNames.length) {
        String oldHeader = outputNames[col];
        
        // Unregister old stock name
        if (oldHeader != null && !oldHeader.trim().isEmpty()) {
            StockFlowRegistry.unregisterStock(oldHeader, this);
        }
        
        // Set new header
        outputNames[col] = newHeader;
        
        // Register new stock name
        if (newHeader != null && !newHeader.trim().isEmpty()) {
            StockFlowRegistry.registerStock(newHeader, this);
        }
        
        // Trigger synchronization for both old and new stock
        StockFlowRegistry.synchronizeRelatedTables(this);
    }
}

// Override delete method to unregister
@Override
public void delete() {
    // Unregister all stocks - single call
    StockFlowRegistry.unregisterAllStocks(this);
    super.delete();
}
```

### Phase 4: UI Integration

Trigger synchronization when tables are edited - simple delegation to `StockFlowRegistry`.

```java
// Modify TableEditDialog.java

private void applyChanges() {
    if (!hasChanges) return;
    
    // Track old stock names for unregistration
    String[] oldStockNames = new String[tableElement.getCols()];
    for (int col = 0; col < tableElement.getCols(); col++) {
        oldStockNames[col] = tableElement.getColumnHeader(col);
    }
    
    // Apply data changes with new size
    if (tableElement != null) {
        tableElement.resizeTable(dataRows, dataCols);
        
        // Apply cell equations
        for (int row = 0; row < dataRows; row++) {
            for (int col = 0; col < dataCols; col++) {
                tableElement.setCellEquation(row, col, cellData[row][col]);
            }
        }
        
        // Unregister old stock names before changing
        for (int col = 0; col < oldStockNames.length; col++) {
            if (oldStockNames[col] != null && !oldStockNames[col].trim().isEmpty()) {
                StockFlowRegistry.unregisterStock(oldStockNames[col], tableElement);
            }
        }
        
        // Apply stock values (column headers), initial values, and types
        for (int col = 0; col < dataCols; col++) {
            tableElement.setColumnHeader(col, stockValues[col]);
            tableElement.setInitialConditionValue(col, initialValues[col]);
            tableElement.setColumnType(col, columnTypes[col]);
        }
        
        // Apply table settings
        tableElement.setTableTitle(tableTitleInput.getText().trim());
        tableElement.setTableUnits(tableUnitsInput.getText().trim());
        tableElement.setDecimalPlaces(decimalPlaces);
        
        // *** Trigger synchronization - all logic in StockFlowRegistry ***
        StockFlowRegistry.synchronizeRelatedTables(tableElement);
        
        tableElement.setSize(tableElement.flags);
        tableElement.sim.needAnalyze();
        tableElement.sim.needRepaint();
    }
    
    hasChanges = false;
    setStatus("Changes applied and tables synchronized");
}
```

### Phase 5: Visual Feedback

Provide visual indicators for shared stocks - uses `StockFlowRegistry` to query shared status.

```java
// Add to TableRenderer.java

/**
 * Indicate which stocks are shared across multiple tables
 * Draw a colored indicator (e.g., yellow background) on shared stock headers
 */
private void highlightSharedStocks(Graphics g) {
    // Query registry for shared stocks (no logic in renderer)
    Set<String> sharedStocks = StockFlowRegistry.getSharedStocks();
    
    for (int col = 0; col < table.cols; col++) {
        String stockName = table.getColumnHeader(col);
        
        if (sharedStocks.contains(stockName)) {
            // Draw yellow background for shared stock column header
            int cellX = getCellX(col);
            int headerY = getHeaderY();
            
            g.setColor(new Color(255, 255, 200)); // Light yellow
            g.fillRect(cellX, headerY, table.getCellWidthPixels(), table.cellHeight);
            
            // Redraw header text on top
            g.setColor(Color.black);
            table.drawCenteredText(g, stockName, cellX + table.getCellWidthPixels()/2, 
                                   headerY + table.cellHeight/2, true);
        }
    }
}

// Modify draw() to call highlightSharedStocks()
public void draw(Graphics g) {
    // ... existing drawing code ...
    
    // Highlight shared stocks
    highlightSharedStocks(g);
    
    // ... rest of drawing code ...
}
```

### Phase 6: Circuit Load/Reset Integration

Circuit lifecycle management - all synchronization logic in `StockFlowRegistry`.

```java
// Modify CirSim.java

// In readCircuit() or similar circuit loading method
private void readCircuit(String text, int flags) {
    // Clear registry before loading new circuit
    StockFlowRegistry.clearRegistry();
    
    // ... existing circuit loading code ...
    
    // After all elements loaded, trigger synchronization
    // Single method call - all logic in StockFlowRegistry
    StockFlowRegistry.synchronizeAllTables();
}

// In resetCircuit() or reset()
public void resetAction() {
    // Don't clear registry on reset - keep row synchronization
    // Only clear when loading new circuit
    
    // ... existing reset code ...
}
```

## Implementation Phases

### Phase 1: Core Registry (Minimal Viable)
- Create `StockFlowRegistry.java`
- Add registration methods to `TableElm`
- Test with 2 tables sharing 1 stock

### Phase 2: Row Synchronization
- Implement `collectRowDescriptionsForSharedStocks()`
- Implement `synchronizeRowsWithSharedStocks()`
- Test row merging logic

### Phase 3: UI Integration
- Modify `TableEditDialog.applyChanges()`
- Add `synchronizeAllRelatedTables()`
- Test with interactive editing

### Phase 4: Visual Feedback
- Add `highlightSharedStocks()` to `TableRenderer`
- Show which stocks are shared
- Optional: show row source indicators

### Phase 5: Polish & Edge Cases
- Handle table deletion
- Handle column deletion
- Handle stock name changes
- Circuit load/save testing

## Edge Cases & Considerations

### 1. Circular Dependencies
**Issue:** Table A synchronizes → triggers Table B sync → triggers Table A sync...

**Solution:** Built into `StockFlowRegistry.synchronizeTable()`:
```java
// In StockFlowRegistry
private static Set<TableElm> currentlySynchronizing = new HashSet<>();

public static boolean synchronizeTable(TableElm table) {
    // Prevent recursive synchronization
    if (currentlySynchronizing.contains(table)) {
        return false;
    }
    
    try {
        currentlySynchronizing.add(table);
        // ... synchronization logic ...
    } finally {
        currentlySynchronizing.remove(table);
    }
}
```

**No code needed in `TableElm`** - handled entirely by registry.

### 2. Row Description Conflicts
**Issue:** Two tables have same row description with different equations.

**Solution:** **Per-table equations are preserved**. Row descriptions are unified, but each table keeps its own equations for that row. This is actually desired behavior:
- Table A: "Wages" → `-500` (expense)
- Table B: "Wages" → `0` (no contribution)

### 3. Row Ordering
**Issue:** Different tables may add rows in different orders.

**Solution:** Use `LinkedHashSet` to preserve insertion order. First-seen row appears first in merged set.

### 4. Empty Row Descriptions
**Issue:** User creates row with empty description.

**Solution:** Generate default: `"Flow_" + (row+1)`, or skip empty rows during merging.

### 5. Stock Name Changes
**Issue:** User renames stock from "Cash" to "Money".

**Solution:** 
- Unregister old name
- Register new name
- Trigger synchronization for both old (cleanup) and new (merge) names

### 6. Performance with Many Tables
**Issue:** 100 tables sharing 1 stock → O(n²) synchronization.

**Solution:** 
- **Batch synchronization**: mark tables dirty, sync once at end
- **Lazy sync**: only sync on user request or simulation start
- **Incremental sync**: only sync changed rows

### 7. Undo/Redo
**Issue:** User adds row, triggers sync across tables. Undo should revert all tables.

**Solution:** Future enhancement - implement multi-table undo stack.

## Testing Strategy

### Unit Tests
1. Registry add/remove stocks
2. Find shared stocks
3. Merge row descriptions (2 tables)
4. Merge row descriptions (3+ tables)
5. Handle empty rows
6. Handle duplicate rows

### Integration Tests
1. Create 2 tables with shared stock
2. Add row to Table A → verify Table B updated
3. Delete row from Table B → verify Table A updated
4. Rename stock → verify synchronization
5. Delete table → verify unregistration
6. Load circuit with shared stocks → verify sync

### UI Tests
1. Edit table dialog → apply → verify other tables update
2. Visual feedback: shared stock highlighting
3. Performance: 10 tables, 50 rows each

## Alternative Approaches Considered

### Approach A: Central Row Registry
**Pros:** Single source of truth for all rows per stock
**Cons:** High complexity, breaks table encapsulation, harder to maintain

### Approach B: Event-Driven Synchronization
**Pros:** Real-time updates, reactive design
**Cons:** Complex event handling, potential race conditions

### Approach C: Lazy Synchronization (Chosen)
**Pros:** Simple, predictable, user-triggered
**Cons:** Not real-time (sync on apply/load only)

## Future Enhancements

1. **Smart Row Matching**: Use fuzzy matching for similar row descriptions
2. **Conflict Resolution UI**: Show conflicts when merging, let user choose
3. **Row Ownership**: Track which table "owns" each row, show in UI
4. **Sync Modes**: 
   - Auto (current design)
   - Manual (user clicks "Sync" button)
   - Off (no synchronization)
5. **Row Templates**: Define common row sets (e.g., "Standard P&L")
6. **Import/Export**: Share row definitions across circuits

## Summary of Code Distribution

### `StockFlowRegistry.java` (~350 lines) - **Heavy Lifting**
✅ Registry management (register/unregister)  
✅ Shared stock detection  
✅ Row description merging  
✅ Synchronization logic (columns, tables, all)  
✅ Circular dependency prevention  
✅ Caching for performance  
✅ Diagnostic information

### `TableElm.java` (~30 lines added) - **Minimal Interface**
✅ `findColumnByStockName()` - helper for registry  
✅ `updateRowData()` - apply synchronized data  
✅ `synchronizeWithRelatedTables()` - simple delegation  
✅ `registerAllStocks()` - simple delegation  
✅ `delete()` override - single unregister call

### `TableEditDialog.java` (~5 lines changed) - **Simple Trigger**
✅ Replace old sync code with: `StockFlowRegistry.synchronizeRelatedTables(tableElement)`

### `TableRenderer.java` (~10 lines added) - **Simple Query**
✅ Query `StockFlowRegistry.getSharedStocks()`  
✅ Highlight shared stocks (yellow background)

### `CirSim.java` (~5 lines changed) - **Simple Call**
✅ `StockFlowRegistry.clearRegistry()` on circuit load  
✅ `StockFlowRegistry.synchronizeAllTables()` after load

## Result: ~85% of Logic in StockFlowRegistry

**Before refactor:** Logic spread across TableElm, TableEditDialog, helper methods  
**After refactor:** Centralized service class with clear responsibilities  

**TableElm stays focused on:**
- Circuit simulation (doStep, stamp, etc.)
- Data storage (rows, columns, equations)
- Basic accessors/mutators

**StockFlowRegistry handles all:**
- Stock relationship tracking
- Row synchronization algorithms
- Performance optimization (caching)
- Circular dependency prevention
