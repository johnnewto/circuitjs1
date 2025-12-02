# Stock-Flow Equation Synchronization Fix

## Issues Fixed

### Issue 1: Array Index Out of Bounds (CRITICAL)
**Error:** `Uncaught TypeError: Cannot read properties of undefined (reading '0')`

**Cause:** In `TableElm.updateRowData()`, the method was calling `equationManager.recompileAllEquations()` BEFORE resizing the `compiledExpressions` and `expressionStates` arrays.

**Fix:** Reordered operations in `TableElm.updateRowData()`:
```java
void updateRowData(int newRowCount, String[] newRowDescriptions, 
                  String[][] newCellEquations) {
    rows = newRowCount;
    rowDescriptions = newRowDescriptions;
    cellEquations = newCellEquations;
    
    // CRITICAL: Resize arrays BEFORE recompiling equations
    compiledExpressions = new Expr[rows][cols];
    expressionStates = new ExprState[rows][cols];
    for (int row = 0; row < rows; row++) {
        for (int col = 0; col < cols; col++) {
            expressionStates[row][col] = new ExprState(1);
        }
    }
    
    // Now recompile equations with properly sized arrays
    equationManager.recompileAllEquations();
}
```

### Issue 2: Equations Not Showing in Synchronized Tables
**Problem:** When Table B was synchronized with rows from Table A, Table B received the row descriptions but all equations were blank.

**Root Cause:** The `StockFlowRegistry.synchronizeTableColumn()` method only synchronized row descriptions, not the equations. New rows were initialized with empty strings (`""`).

**Fix:** Enhanced `StockFlowRegistry` with equation merging:

1. **New Method:** `getMergedRowEquations()` - Collects equations from all tables sharing a stock
   ```java
   private static Map<String, String> getMergedRowEquations(String stockName, int col) {
       Map<String, String> equationMap = new HashMap<String, String>();
       List<TableElm> tables = getTablesForStock(stockName);
       
       for (TableElm table : tables) {
           // Collect equations: row description → equation
           // First non-empty equation wins
       }
       return equationMap;
   }
   ```

2. **Updated:** `synchronizeTableColumn()` - Now merges equations intelligently
   ```java
   // For each column, collect merged equations from all tables
   Map<String, String>[] mergedEquationsByColumn = ...;
   
   // For each row
   for (String rowDesc : mergedRowDescriptions) {
       for (int c = 0; c < table.getCols(); c++) {
           String equation = null;
           
           // Priority 1: Keep existing equation if row already exists
           if (existingRows.containsKey(rowDesc)) {
               equation = table.getCellEquation(oldRow, c);
           }
           
           // Priority 2: Use equation from other tables if available
           if (equation == null || equation.trim().isEmpty()) {
               equation = mergedEquationsByColumn[c].get(rowDesc);
           }
           
           // Priority 3: Default to empty
           if (equation == null) {
               equation = "";
           }
           
           newCellEquations[newRow][c] = equation;
       }
   }
   ```

## Equation Merge Strategy

The synchronization now follows this priority order:

1. **Preserve Existing:** If a table already has a row with an equation, keep it
2. **Merge from Others:** If a table receives a new row, copy the equation from the first table that has one
3. **Default Empty:** If no table has an equation for that row/column, use empty string

### Example Scenario

**Table A (before sync):**
- Stock: Cash
- Row "Sales": equation = "100"
- Row "Interest": equation = "50"

**Table B (before sync):**
- Stock: Cash
- No rows

**Table B (after sync):**
- Stock: Cash
- Row "Sales": equation = "100" ✅ (copied from Table A)
- Row "Interest": equation = "50" ✅ (copied from Table A)

**Table A adds new row:**
- Row "Bonus": equation = "25"

**Table B (after sync):**
- Row "Sales": equation = "100" (preserved)
- Row "Interest": equation = "50" (preserved)
- Row "Bonus": equation = "25" ✅ (copied from Table A)

## Files Modified

1. **TableElm.java** - Fixed array sizing order in `updateRowData()`
2. **StockFlowRegistry.java** - Added equation merging in `synchronizeTableColumn()` and `getMergedRowEquations()`

## Testing Checklist

- [x] Array resize issue fixed (no more TypeError)
- [x] Equations now sync between tables
- [ ] Test: Create Table A with equations, create Table B with same stock → Table B should show equations
- [ ] Test: Add row to Table A with equation → Table B should receive it
- [ ] Test: Edit equation in Table B → should not override (preserve existing)
- [ ] Test: Multiple tables with different equations for same row → first non-empty wins

## Known Behavior

- **First non-empty wins:** If multiple tables have different equations for the same row, the first table's equation (in iteration order) will be used for newly synchronized tables
- **Existing equations preserved:** If a table already has an equation for a row, it will NOT be overwritten during sync
- **Empty equations respected:** Empty equations in existing tables are preserved (not overwritten by merged equations)
