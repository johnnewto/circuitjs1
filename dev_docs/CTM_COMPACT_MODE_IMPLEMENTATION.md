# Current Transactions Matrix - Compact Mode Implementation

## Overview

Compact mode is a display option for the Current Transactions Matrix (CTM) that provides a condensed view by:
- Filtering to show only Asset and Equity columns (hiding Liability and A-L-E columns)
- Removing the type row and stock name (header) row
- Keeping source table row, flow descriptions, initial values, and computed row
- Showing only non-blank cell equations (no computed values in cells)
- Displaying all computed values in the sum row for validation
- Using a wider row description column (2x width) for better flow name visibility

## Implementation Details

### 1. Core Field Addition

**File**: `CurrentTransactionsMatrixElm.java`

Added `compactMode` boolean field:
```java
// Compact mode: filters to Asset/Equity only, hides type/header rows
protected boolean compactMode = false;
```

### 2. Column Filtering Logic

**Location**: `autoPopulateFromRegistry()` and `updateColumnsFromStocks()`

After sorting stocks by source table, filter out LIABILITY columns:
```java
// Filter LIABILITY columns in compact mode
if (compactMode) {
    for (int i = stockInfoList.size() - 1; i >= 0; i--) {
        if (stockInfoList.get(i).columnType == ColumnType.LIABILITY) {
            stockInfoList.remove(i);
        }
    }
}

// Set column count without A-L-E in compact mode
cols = compactMode ? stockInfoList.size() : (stockInfoList.size() + 1);
```

Skip A-L-E column addition:
```java
// Add A-L-E computed column at the end (only in normal mode)
if (!compactMode) {
    int aleIndex = cols - 1;
    outputNames[aleIndex] = "A-L-E";
    columnTypes[aleIndex] = ColumnType.ASSET;
    initialValues[aleIndex] = 0.0;
    sourceTableNames[aleIndex] = "";
}
```

### 3. Geometry Adjustments

**Location**: `setupPins()` in `CurrentTransactionsMatrixElm.java`

```java
// Compact: title + source + initial + computed = 4 rows
int extraRows = compactMode ? 4 : (collapsedMode ? 3 : 5);

// Double row description width in compact mode
int rowDescColWidth = compactMode ? (cellWidthInGrids * 2) : cellWidthInGrids;
```

### 4. Edit Dialog Controls

**Location**: `getEditInfo()` and `setEditValue()` in `CurrentTransactionsMatrixElm.java`

Add compact mode checkbox:
```java
if (n == 3) {
    EditInfo ei = new EditInfo("Compact Mode (Asset/Equity only)", 0, -1, -1);
    ei.checkbox = new Checkbox("", compactMode);
    return ei;
}
```

Hide collapsed mode when compact is active:
```java
if (n == 2) {
    // Hide collapsed mode toggle when compact mode is active
    if (compactMode) return null;
    EditInfo ei = new EditInfo("Collapsed Mode", 0, -1, -1);
    ei.checkbox = new Checkbox("", collapsedMode);
    return ei;
}
```

Handle compact mode toggle:
```java
else if (n == 3) {
    boolean newCompactMode = ei.checkbox.getValue();
    if (newCompactMode != compactMode) {
        compactMode = newCompactMode;
        // If enabling compact mode, ensure we're not in collapsed mode
        if (compactMode && collapsedMode) {
            collapsedMode = false;
        }
        // Re-populate to apply filter
        autoPopulateFromRegistry();
    }
}
```

### 5. Accessor Method

**Location**: After `getSourceTableNames()` in `CurrentTransactionsMatrixElm.java`

```java
/**
 * Check if compact mode is enabled
 * Package-private for access by CurrentTransactionsMatrixRenderer
 */
boolean isCompactMode() {
    return compactMode;
}
```

### 6. Serialization

**File**: `CurrentTransactionsMatrixElm.java` - `dump()` method

Append compactMode to dump string:
```java
String tableData = " 0 0 6 16 0 false 2 false " + collapsedMode + " " + priority + " " + MATRIX_TITLE + " \"\"" + " " + compactMode;
```

**File**: `TableDataManager.java` - `parseTableData()` method

Parse compactMode after tableUnits:
```java
table.tableUnits = readString(st, "");

// Read compactMode if available (for CurrentTransactionsMatrixElm)
// This is optional - old files won't have it
if (table instanceof CurrentTransactionsMatrixElm && st.hasMoreTokens()) {
    String compactToken = st.nextToken();
    if (compactToken.equals("true") || compactToken.equals("false")) {
        // It's compactMode
        ((CurrentTransactionsMatrixElm) table).compactMode = Boolean.parseBoolean(compactToken);
    }
    // If not a boolean, it would be column data but CTM doesn't save column data
}
```

### 7. Renderer Updates

**File**: `CurrentTransactionsMatrixRenderer.java`

#### Calculate Layout
```java
m.rowDescColWidth = matrixElm.isCompactMode() ? (m.cellWidthPixels * 2) : 
    (table.collapsedMode ? 0 : m.cellWidthPixels);

int typeRowHeight = (table.collapsedMode || matrixElm.isCompactMode()) ? 0 : (table.cellHeight + table.cellSpacing);
int headerRowHeight = matrixElm.isCompactMode() ? 0 : (table.cellHeight + table.cellSpacing);
int initialRowHeight = (table.showInitialValues && (!table.collapsedMode || matrixElm.isCompactMode())) ? 
    (table.cellHeight + table.cellSpacing) : 0;
```

#### Draw Components in Order
```java
// Skip type row in compact mode
if (!table.collapsedMode && !matrixElm.isCompactMode()) {
    drawColumnTypeRow(g, currentY);
    currentY += table.cellHeight + table.cellSpacing;
}

// Skip header row in compact mode
if (!matrixElm.isCompactMode()) {
    drawColumnHeaders(g, currentY);
    currentY += table.cellHeight + table.cellSpacing;
}

// Show initial values in compact mode or when explicitly enabled in expanded mode
if (table.showInitialValues && (!table.collapsedMode || matrixElm.isCompactMode())) {
    drawInitialConditionsRow(g, currentY);
    currentY += table.cellHeight + table.cellSpacing;
}
```

#### Override drawTableCells
```java
@Override
protected void drawTableCells(Graphics g, int offsetY) {
    if (!matrixElm.isCompactMode()) {
        // Normal mode: use parent implementation
        super.drawTableCells(g, offsetY);
        return;
    }
    
    // Compact mode: filter cells to show only non-blank Asset/Equity equations
    int tableX = table.getTableX();
    int tableY = table.getTableY();
    int cellWidthPixels = table.getCellWidthPixels();
    int rowDescColWidth = cellWidthPixels * 2; // Wider in compact mode
    
    // Create fonts locally
    Font headerFont = new Font("SansSerif", Font.BOLD, 11);
    Font cellFont = new Font("SansSerif", Font.BOLD, 11);
    String letterSpacing = "0.5px";
    
    int baseY = offsetY;
    
    for (int row = 0; row < table.rows; row++) {
        int cellY = tableY + baseY + row * (table.cellHeight + table.cellSpacing);
        
        // Draw row description with header font
        g.setFont(headerFont);
        g.setLetterSpacing(letterSpacing);
        g.setColor(CircuitElm.whiteColor);
        String rowDesc = (table.rowDescriptions != null && row < table.rowDescriptions.length) ?
                        table.rowDescriptions[row] : "Row" + (row + 1);
        
        table.drawCenteredText(g, rowDesc, tableX + table.cellSpacing + rowDescColWidth/2, 
            cellY + table.cellHeight/2, true);
        
        // Use cell font for cell values
        g.setFont(cellFont);
        
        // Draw data cells - filter in compact mode
        for (int col = 0; col < table.cols; col++) {
            int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                col * (cellWidthPixels + table.cellSpacing);
            
            // In compact mode: skip LIABILITY columns
            if (table.columnTypes != null && col < table.columnTypes.length && 
                table.columnTypes[col] == TableEditDialog.ColumnType.LIABILITY) {
                continue; // Skip rendering LIABILITY columns
            }
            
            // Get equation
            String equation = (table.cellEquations != null && row < table.cellEquations.length && 
                col < table.cellEquations[row].length) ? table.cellEquations[row][col] : "";
            
            // In compact mode: skip blank equations
            if (equation == null || equation.trim().isEmpty()) {
                continue; // Skip blank cells
            }
            
            // Display only the equation (no values in compact mode)
            g.setColor(CircuitElm.whiteColor);
            table.drawCenteredText(g, equation, cellX + cellWidthPixels/2, 
                cellY + table.cellHeight/2, true);
        }
    }
}
```

## Display Characteristics

### Normal Mode (compactMode = false)
- **Columns**: All stocks (Asset, Liability, Equity) + A-L-E
- **Rows**: Title, Source tables, Type, Stock names, Initial values (optional), Data cells, Computed
- **Cell content**: Equations and/or values based on showCellValues setting
- **Row desc width**: Standard (1x cellWidthInGrids)

### Compact Mode (compactMode = true)
- **Columns**: Asset and Equity only (no LIABILITY, no A-L-E)
- **Rows**: Title, Source tables, Initial values, Data cells, Computed
- **Rows hidden**: Type row, Stock name row
- **Cell content**: Non-blank equations only (no values)
- **Computed row**: All values shown for validation
- **Row desc width**: Wider (2x cellWidthInGrids)

### Collapsed Mode (collapsedMode = true, compactMode = false)
- **Columns**: All stocks + A-L-E
- **Rows**: Title, Source tables, Stock names, Computed
- **Rows hidden**: Type, Initial values, Data cells
- **Row desc width**: Hidden (0)

## Mode Interactions

1. **Compact and Collapsed are mutually exclusive**
   - Enabling compact mode automatically disables collapsed mode
   - Collapsed mode toggle is hidden when compact mode is active

2. **Compact mode always shows initial values**
   - Overrides the showInitialValues setting behavior
   - Initial row is displayed regardless of showInitialValues checkbox

3. **Compact mode requires data rows**
   - Unlike collapsed mode, compact mode expands the table to show flow data
   - Automatically calls autoPopulateFromRegistry() to populate flows

## Benefits of Compact Mode

1. **Reduced visual clutter**: Fewer columns and header rows
2. **Focus on transactions**: Shows only Asset/Equity flows that represent actual money movements
3. **Better flow visibility**: Wider row descriptions improve readability
4. **Validation preserved**: Computed row still shows all values including hidden columns
5. **Equation-focused**: Displays formulas rather than computed values for understanding logic

## Testing Checklist

- [ ] Toggle compact mode on/off works correctly
- [ ] LIABILITY columns are hidden in compact mode
- [ ] A-L-E column is hidden in compact mode
- [ ] Type row is hidden in compact mode
- [ ] Stock name row is hidden in compact mode
- [ ] Initial values row is shown in compact mode
- [ ] Row description column is 2x wider in compact mode
- [ ] Only non-blank equations are shown in cells
- [ ] Computed row shows all values (including hidden columns)
- [ ] Save/load circuit preserves compact mode setting
- [ ] Enabling compact mode disables collapsed mode
- [ ] Collapsed mode toggle is hidden when compact is active
- [ ] Switching back to normal mode restores all columns
- [ ] Array validation prevents rendering errors
