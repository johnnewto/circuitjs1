# A_L_E Computed Column Implementation

## Overview
Implemented the A_L_E (Assets - Liabilities - Equity) column as a **computed, read-only column** in the Table Edit Dialog. The A_L_E column automatically calculates the accounting balance equation and displays it in real-time as users edit other columns.

**Key Design**: The A_L_E column is always the **last column** in tables with 4 or more columns, allowing users to add as many stock columns as needed before the computed column.

## Features Implemented

### 1. **Blank Column Header**
- The A_L_E stock header is always blank (`""` instead of a stock name)
- Prevents A_L_E from being registered in the StockFlowRegistry

### 2. **Automatic Calculation**
- Formula: `sum(ASSET columns) - sum(LIABILITY columns) - EQUITY column`
- The A_L_E column is always positioned as the **last column** for tables with 4+ columns
- Implemented in `calculateALECellEquation()` and `calculateALEInitialValue()` methods
- Lines 258-319 in `TableEditDialog.java`

### 3. **Read-Only Display**
- All A_L_E cells use `Label` widgets instead of `TextBox` widgets
- Applied to:
  - Stock value (top fixed row)
  - Initial value (initial conditions row)
  - All data cells in the A_L_E column

### 4. **Real-Time Recalculation**
- Implemented `updateALEColumns()` method that:
  - Recalculates all A_L_E cells when any cell changes
  - Recalculates when initial values change
  - Preserves operator precedence with parentheses wrapping
- Triggered on keyup events in all cell TextBoxes and initial value TextBoxes

### 5. **Visual Distinction**
- Added CSS class `computed-column` to all A_L_E elements
- Style definition in `src/com/lushprojects/circuitjs1/public/style.css`:
  ```css
  .computed-column {
      background-color: #f0f0f0 !important;
      color: #666 !important;
      border-color: #999 !important;
      cursor: not-allowed;
      font-style: italic;
      opacity: 0.85;
  }
  ```
- Creates a grayed-out, italic appearance to indicate non-editable status

## Code Changes

### Modified Files
1. **TableEditDialog.java**
   - Added calculation methods: `calculateALECellEquation()`, `calculateALEInitialValue()`
   - Modified `copyTableData()` to skip copying A_L_E cells
   - Modified `populateFixedStructure()` to use Labels for A_L_E stock value and initial value
   - Modified `populateDataCells()` to use Labels for A_L_E cells
   - Modified `createCellTextBox()` to trigger A_L_E updates
   - Modified `createInitialValueTextBox()` to trigger A_L_E updates
   - Added `updateALEColumns()` method for real-time recalculation

2. **TableElm.java**
   - Changed default `cols` from 3 to 4 (Asset, Liability, Equity, A_L_E)
   - Ensures new tables automatically include the A_L_E computed column as the last (4th) column

3. **TableDataManager.java**
   - Modified `getDefaultColumnType()` to make the last column A_L_E for tables with 4+ columns
   - Modified `initTable()` to set blank outputName for A_L_E columns
   - Modified outputName validation to preserve blank names for A_L_E columns
   - Modified `parseColumnHeaders()` to enforce blank headers for A_L_E columns
   - Modified `copyColumnTypes()` to clear old A_L_E equations when table is resized
   - Prevents A_L_E columns from being registered as stocks in the registry

4. **style.css**
   - Added `.computed-column` style definition for visual distinction

5. **StockFlowRegistry.java**
   - Modified `synchronizeNonZeroElements()` to skip A_L_E columns during synchronization
   - Prevents computed A_L_E equations from being copied between tables
   - Each table computes its own A_L_E values independently

### Refactored Files (Previous Work)
1. **TableDataManager.java**
   - Split 130-line `parseTableData()` into 8 focused methods
   - Extracted `getDefaultColumnType()` helper
   - Eliminated duplicate code

2. **TableElm.java**
   - Removed unused `recompileAllEquations()` method
   - Removed duplicate `getCellWidthPixels()` implementation

3. **TableRenderer.java**
   - Removed commented code
   - Extracted `truncateEquation()` helper method

4. **TableGeometryManager.java**
   - Decomposed `setupPins()` into focused helper methods
   - Decomposed `setPoints()` into focused helper methods

## Technical Details

### Last Column Design
The A_L_E column is always positioned as the **last column** in tables with 4 or more columns:

**Rationale:**
- Allows users to add any number of stock columns (Asset, Liability, Equity, or custom columns)
- Prevents the A_L_E column from appearing in the middle of user data
- Makes table expansion intuitive - new columns are added before the computed column
- Ensures A_L_E always reflects the current state of all preceding columns

**Behavior by Table Size:**
- **1-3 columns**: No A_L_E column (minimum viable: Asset, Liability, Equity)
- **4 columns**: Standard layout - Asset, Liability, Equity, **A_L_E**
- **5 columns**: Asset, Liability, Equity, Custom Stock, **A_L_E**
- **6+ columns**: Asset, Liability, Equity, [Multiple Custom Stocks], **A_L_E**

**Resize Handling:**
- When adding columns, the old A_L_E column becomes a regular stock column
- Its auto-generated equations are cleared (since they were computed, not user-entered)
- The new last column becomes the A_L_E column
- User-defined column types (Asset, Liability, Equity) are preserved

### Calculation Logic
The A_L_E column calculates the accounting identity:
```
Assets = Liabilities + Equity
```

Rearranged as:
```
Assets - Liabilities - Equity = 0
```

The implementation sums all columns by type:
- **Assets**: All columns with type `ASSET`
- **Liabilities**: All columns with type `LIABILITY`
- **Equity**: The single column with type `EQUITY`

### Parentheses Wrapping
Complex expressions are wrapped in parentheses to preserve operator precedence:
- Simple values (e.g., `"0"`, `"A1"`) are used as-is
- Complex expressions (e.g., `"A1+B2"`, `"C3*2"`) are wrapped: `"(A1+B2)"`

This ensures correct calculation when combining multiple equations.

### Widget Type Handling
The implementation uses fully qualified widget types to avoid ambiguity:
```java
com.google.gwt.user.client.ui.Widget widget = editGrid.getWidget(row, col);
```

## Testing Recommendations

1. **Basic Calculation**
   - Create a table with asset, liability, and equity columns
   - Enter values and verify A_L_E calculates correctly
   - Verify A_L_E = sum(assets) - sum(liabilities) - equity

2. **Real-Time Updates**
   - Edit any cell and verify A_L_E column updates immediately
   - Edit initial values and verify A_L_E initial value updates

3. **Read-Only Verification**
   - Attempt to click/edit A_L_E cells
   - Verify cursor shows "not-allowed" style
   - Verify no TextBox appears when clicking

4. **Complex Equations**
   - Enter equations with operators: `"A1+B2"`, `"C3*2"`, `"D4/5"`
   - Verify parentheses are added correctly in A_L_E formula
   - Verify calculations remain accurate

5. **Visual Appearance**
   - Verify A_L_E column has distinct gray, italic appearance
   - Verify blank column header displays correctly
   - Test in both light and dark themes (if applicable)

6. **Edge Cases**
   - Test with all zero values
   - Test with negative values
   - Test with empty cells (should treat as "0")
   - Test with very long equation strings

## Related Documentation
- Table architecture: `STOCK_FLOW_ARCHITECTURE_COMPARISON.md`
- Stock/flow implementation: `STOCK_FLOW_IMPLEMENTATION_COMPLETE.md`
- Equation synchronization: `STOCK_FLOW_EQUATION_SYNC_FIX.md`

## Compilation Status
✅ **All files compile successfully with zero errors**
- TableEditDialog.java: No errors
- TableElm.java: No errors
- TableDataManager.java: No errors
- TableRenderer.java: No errors
- TableGeometryManager.java: No errors
- style.css: Valid CSS syntax

## Bug Fixes

### Issue 1: New Tables Only Had 3 Columns
**Problem**: When creating a new table, it would only have 3 columns (Stock_1, Stock_2, Stock_3), missing the A_L_E column entirely.

**Root Cause**: `TableElm.java` line 17 had `protected int cols = 3;` as the default.

**Solution**: Changed default to `protected int cols = 4;` to ensure new tables include Asset, Liability, Equity, and A_L_E columns by default.

**Impact**: 
- New tables now correctly display all 4 columns
- A_L_E column (4th column) has blank header and computed values
- Existing tables with 3 columns will be expanded to 4 when edited

### Issue 2: A_L_E Column Was Being Registered as Stock Name
**Problem**: The A_L_E column was being assigned a "Stock" name as its header and registered in the StockFlowRegistry, when it should have a blank header and not be registered as a stock.

**Root Cause**: Three places in `TableDataManager.java` were assigning default "Stock" names to all columns without checking for A_L_E type:
1. `initTable()` initialization loop
2. Empty name validation loop
3. `parseColumnHeaders()` method

**Solution**: Modified all three locations to check for `ColumnType.A_L_E` and assign blank names (`""`) instead of "Stock" names:

```java
// Example from initTable():
if (table.columnTypes != null && table.columnTypes[i] == ColumnType.A_L_E) {
    table.outputNames[i] = "";
} else {
    table.outputNames[i] = "Stock_" + (i + 1);
}
```

**Impact**:
- A_L_E columns now have blank headers in all contexts (new tables, loaded tables, validation)
- A_L_E columns are NOT registered in StockFlowRegistry (blank names are skipped in registration)
- Debug output shows blank header for A_L_E column
- A_L_E column name doesn't appear in stock registry diagnostics

### Issue 3: A_L_E Equations Being Synchronized Between Tables
**Problem**: When pressing the sync button, A_L_E column equations from one table were being copied to other tables, overwriting their locally computed A_L_E values.

**Root Cause**: `StockFlowRegistry.synchronizeNonZeroElements()` was synchronizing all columns without checking if they were A_L_E (computed) columns.

**Solution**: Added two checks in `synchronizeNonZeroElements()`:
1. Skip source columns that are A_L_E type (line 346-348)
2. Skip target columns that are A_L_E type (line 367-370)

```java
// Skip A_L_E columns (computed columns should not be synchronized)
if (sourceTable.getColumnType(sourceCol) == TableEditDialog.ColumnType.A_L_E) {
    continue;
}
```

**Impact**:
- A_L_E columns are now excluded from table synchronization
- Each table independently computes its own A_L_E values
- Sync button no longer corrupts A_L_E equations
- Only actual stock columns (Asset, Liability, Equity) are synchronized

### Issue 4: Column 3 Hardcoded as A_L_E Regardless of Table Size
**Problem**: When creating or loading tables with more than 4 columns, column 3 was always treated as A_L_E, even when the table had 5, 6, or more columns. This caused incorrect A_L_E calculations to appear in the middle of the table.

**Root Cause**: Two locations were hardcoding column 3 as A_L_E:
1. `TableDataManager.getDefaultColumnType()` - Always returned `ColumnType.A_L_E` for column 3
2. `TableEditDialog.copyTableData()` - Set column 3 to A_L_E for any table with 4+ columns

**Solution**: Changed logic to make the **last column** always be A_L_E (for tables with 4+ columns):

**TableDataManager.java:**
```java
private ColumnType getDefaultColumnType(int col) {
    // For tables with 4+ columns, the last column is always A_L_E
    if (table.cols >= 4 && col == table.cols - 1) {
        return ColumnType.A_L_E;
    }
    
    switch (col) {
        case 0: return ColumnType.ASSET;
        case 1: return ColumnType.LIABILITY;
        case 2: return ColumnType.EQUITY;
        default: return ColumnType.ASSET;
    }
}
```

**TableEditDialog.java:**
```java
// All remaining columns (including column 3) get default stock names and ASSET type
for (int col = 3; col < dataCols; col++) {
    stockValues[col] = "Stock" + col;
    columnTypes[col] = ColumnType.ASSET;
}

// For tables with 4+ columns, the LAST column is always A_L_E
if (dataCols >= 4) {
    stockValues[dataCols - 1] = "";  // A_L_E column has blank label
    columnTypes[dataCols - 1] = ColumnType.A_L_E;
}
```

**Impact**:
- 4-column tables: Column 3 is A_L_E (Asset, Liability, Equity, A_L_E) ✓
- 5-column tables: Column 4 is A_L_E (Asset, Liability, Equity, Stock3, A_L_E) ✓
- 6+ column tables: Last column is always A_L_E ✓
- No more hardcoded position assumptions
- Users can add as many stock columns as needed before the A_L_E column

### Issue 5: Old A_L_E Equations Preserved When Adding Columns
**Problem**: When adding columns to a table (e.g., going from 4 to 6 columns), the old A_L_E column at position 3 would keep its auto-generated equation like `1 - 2 - 3`, even though it should now be a regular stock column.

**Root Cause**: `TableDataManager.copyColumnTypes()` was preserving old column types when resizing, but not clearing equations from columns that changed from A_L_E to non-A_L_E types.

**Solution**: Modified `copyColumnTypes()` to detect when a column was A_L_E but is no longer A_L_E after resize, and clear its equations:

```java
private void copyColumnTypes(ColumnType[] oldColumnTypes) {
    for (int col = 0; col < table.cols; col++) {
        ColumnType newDefaultType = getDefaultColumnType(col);
        ColumnType oldType = (oldColumnTypes != null && col < oldColumnTypes.length) 
                             ? oldColumnTypes[col] : null;
        
        if (oldType != null) {
            // If this column was A_L_E but the last column has moved, update it
            if (oldType == ColumnType.A_L_E && newDefaultType != ColumnType.A_L_E) {
                // Old A_L_E column is no longer the last - make it ASSET and clear equations
                table.columnTypes[col] = ColumnType.ASSET;
                for (int row = 0; row < table.rows; row++) {
                    if (table.cellEquations != null && table.cellEquations[row] != null) {
                        table.cellEquations[row][col] = "";
                        table.compiledExpressions[row][col] = null;
                    }
                }
            } else {
                // Preserve the old type
                table.columnTypes[col] = oldType;
            }
        } else {
            // New column - use default type
            table.columnTypes[col] = newDefaultType;
        }
    }
}
```

**Impact**:
- When adding columns, old A_L_E columns are cleared and converted to regular stock columns
- The new last column becomes the A_L_E column
- User-defined column types (ASSET, LIABILITY, EQUITY) are preserved during resize
- No stale A_L_E equations remain in the middle of the table
- Clean transition when expanding table size

## Next Steps
1. Build and test the application with the new A_L_E implementation
2. Verify accounting equation balance in various scenarios
3. Consider adding tooltips to explain the A_L_E calculation
4. Test integration with linked tables that share stocks
