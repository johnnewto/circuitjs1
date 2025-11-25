# TableColumn Refactor Implementation Plan

## Status: TableColumn.java created ✅

## Overview
Convert TableElm from parallel arrays to ArrayList<TableColumn> for better encapsulation and simpler column operations.

## Phase 1: Core TableElm Conversion ⏳

### 1.1 Field Replacement
**Replace:**
```java
protected String[] stockNames;
protected ColumnType[] columnTypes;
protected double[] initialValues;
String[][] cellEquations;
Expr[][] compiledExpressions;
ExprState[][] expressionStates;
protected double[] lastColumnSums;
```

**With:**
```java
protected ArrayList<TableColumn> columns;
```

### 1.2 Add Import
```java
import java.util.ArrayList;
```

### 1.3 Update `cols` Management
- `cols` becomes `columns.size()`
- Add helper: `protected int getCols() { return columns != null ? columns.size() : 0; }`

### 1.4 Core Accessor Methods (High Priority)
- `getColumnHeader(int col)` → `columns.get(col).getStockName()`
- `setColumnHeader(int col, String header)` → `columns.get(col).setStockName(header)`
- `getColumnType(int col)` → `columns.get(col).getType()`
- `setColumnType(int col, ColumnType type)` → `columns.get(col).setType(type)`
- `getInitialValue(int col)` → handle A-L-E special case, else `columns.get(col).getInitialValue()`
- `setInitialConditionValue(int col, double value)` → `columns.get(col).setInitialValue(value)`
- `getCellEquation(int row, int col)` → `columns.get(col).getCellEquation(row)`
- `setCellEquation(int row, int col, String eq)` → `columns.get(col).setCellEquation(row, eq)`

### 1.5 Helper Methods
- `findColumnByStockName(String name)` → iterate columns, compare names
- `isALEColumn(int col)` → `col < columns.size() && columns.get(col).isALE()`

## Phase 2: TableDataManager Updates

### 2.1 initTable()
Replace array initialization with:
```java
if (columns == null) {
    columns = new ArrayList<TableColumn>();
    for (int i = 0; i < 4; i++) {
        columns.add(new TableColumn("", ColumnType.ASSET, 0.0, rows));
    }
}
```

### 2.2 resizeTable(newRows, newCols)
```java
// Resize rows in each column
for (TableColumn col : columns) {
    col.resizeRows(newRows);
}

// Add/remove columns
while (columns.size() < newCols) {
    columns.add(new TableColumn("", ColumnType.ASSET, 0.0, newRows));
}
while (columns.size() > newCols) {
    columns.remove(columns.size() - 1);
}
```

### 2.3 parseTableData(StringTokenizer st)
- Parse data into temporary arrays
- Convert to TableColumn objects
- Handle backward compatibility

### 2.4 dump()
- Convert TableColumn data back to string format
- Maintain backward compatibility with existing circuit files

## Phase 3: TableEquationManager Updates

### 3.1 compileEquation(row, col, equation)
```java
TableColumn column = columns.get(col);
Expr expr = parser.parseExpression(equation);
column.setCompiledExpression(row, expr);
```

### 3.2 getVoltageForCell(row, col)
```java
TableColumn column = columns.get(col);
Expr expr = column.getCompiledExpression(row);
ExprState state = column.getExpressionState(row);
// evaluate...
```

### 3.3 recompileAllEquations()
```java
for (int c = 0; c < columns.size(); c++) {
    TableColumn column = columns.get(c);
    for (int r = 0; r < rows; r++) {
        compileEquation(r, c, column.getCellEquation(r));
    }
}
```

## Phase 4: doStep() and stepFinished()

### 4.1 doStep()
```java
for (int col = 0; col < columns.size(); col++) {
    TableColumn column = columns.get(col);
    if (column.isALE()) continue; // Skip A-L-E
    
    double columnSum = 0.0;
    for (int row = 0; row < rows; row++) {
        columnSum += equationManager.getVoltageForCell(row, col);
    }
    
    column.setLastSum(columnSum);
    // convergence checking...
    // stamping...
}
```

### 4.2 stepFinished()
```java
for (int col = 0; col < columns.size(); col++) {
    TableColumn column = columns.get(col);
    if (column.isALE() || column.isEmpty()) continue;
    
    if (isMasterForColumn(col)) {
        registerComputedValueAsLabeledNode(column.getStockName(), column.getLastSum());
        ComputedValues.markComputedThisStep(column.getStockName());
    }
}
```

## Phase 5: TableRenderer Updates

### 5.1 Update all array access patterns
```java
// OLD: stockNames[col]
// NEW: columns.get(col).getStockName()

// OLD: columnTypes[col]
// NEW: columns.get(col).getType()

// OLD: initialValues[col]
// NEW: columns.get(col).getInitialValue()

// OLD: lastColumnSums[col]
// NEW: columns.get(col).getLastSum()
```

### 5.2 A-L-E Calculation
Keep cached calculation in renderer, but use column.getType() for summation

## Phase 6: Subclass Updates

### 6.1 CurrentTransactionsMatrixElm
**Simplifications:**
- `applyCustomStockNames()`: Build columns directly from customStockNames array
- `autoPopulateFromRegistry()`: Build columns from registry 
- `initializeWithEquityStocksOnly()`: Use `columns.removeIf(c -> c.getType() != EQUITY)`

### 6.2 GodlyTableElm (Stock/Flow tables)
- Update to use columns.get(col) pattern
- Simplify integration logic

### 6.3 MasterTableElm (if exists)
- Update accessor patterns

## Phase 7: TableEditDialog Updates

### 7.1 Column Operations
```java
// Move column
TableColumn temp = table.columns.remove(fromIndex);
table.columns.add(toIndex, temp);

// Insert column
table.columns.add(index, new TableColumn("", ColumnType.ASSET, 0.0, table.rows));

// Delete column
table.columns.remove(index);

// Copy column
TableColumn copy = table.columns.get(index).copy();
table.columns.add(afterIndex + 1, copy);
```

### 7.2 Update all data access
Replace array indexing with column.get/set methods

## Phase 8: Testing & Validation

### 8.1 Unit Tests
- Column CRUD operations
- Row resizing
- Data preservation during moves
- A-L-E calculations

### 8.2 Integration Tests
- Load existing circuits
- Save/load round-trip
- CTM auto-population
- Table synchronization
- Priority-based master selection

### 8.3 Backward Compatibility
- Old circuit files load correctly
- Data migrates properly
- No visual changes to user

## Migration Strategy

### Option A: Big Bang (Risky)
Convert everything at once, fix compilation errors systematically

### Option B: Gradual (Recommended)
1. ✅ Create TableColumn class
2. Add columns field alongside existing arrays
3. Maintain both during transition
4. Convert one subsystem at a time
5. Remove old arrays when fully converted
6. Test at each step

## Rollback Plan
If issues arise:
1. Git revert to before refactor
2. Keep TableColumn.java for future attempt
3. Document lessons learned

## Success Criteria
- ✅ All existing circuits load and function
- ✅ CTM operations work correctly
- ✅ Table editing simplified (fewer lines of code)
- ✅ Column move/add/delete operations cleaner
- ✅ No performance regression
- ✅ Code more maintainable

## Estimated Impact
- **Lines Changed**: ~800-1000 across 5-7 files
- **Risk Level**: High (core data structure change)
- **Benefit**: High (major simplification of column operations)
- **Time**: 4-6 hours with testing

## Current Status
**Phase 1.1**: Ready to begin field replacement in TableElm
