# TableEditDialog Architecture - Data Management Implementation

## ✅ Successfully Implemented Separation of Concerns

The refactoring to move data management to `TableEditDialog` while keeping display/simulation in `TableElm` is **COMPLETE**.

---

## Data Management in TableEditDialog

### **Properties Managed by TableEditDialog**

```java
// Data dimensions
private int dataRows;              // Number of data rows
private int dataCols;              // Number of data columns

// Data storage
private String[][] cellData;       // Cell equations (dataRows × dataCols)
private String[] columnHeaders;    // Column header labels (dataCols)
private double[] initialValues;    // Initial condition values (dataCols)

// UI State
private boolean hasChanges;        // Track modifications
```

### **Data Operations in TableEditDialog**

#### **1. Initialization (`copyTableData()`)**
- Creates temporary copies of all table data from `TableElm`
- Sets default column headers: "Asset", "Liability", "Equity", "A_L_E"
- Initializes all arrays with proper dimensions
- Ensures non-null values for all cells

#### **2. Column Operations**
```java
insertColumnAfter(int colIndex)    // Add new column
deleteColumn(int colIndex)         // Remove column
moveColumn(int from, int to)       // Reorder columns
```

#### **3. Row Operations**
```java
insertRowAfter(int rowIndex)       // Add new row
deleteRow(int rowIndex)            // Remove row
moveRow(int from, int to)          // Reorder rows
```

#### **4. Data Persistence (`applyChanges()`)**
- Calls `tableElement.resizeTable(dataRows, dataCols)` to update dimensions
- Updates all cell equations via `setCellEquation()`
- Updates all column headers via `setColumnHeader()`
- Updates all initial values via `setInitialConditionValue()`
- Triggers display refresh

---

## Display/Simulation in TableElm

### **Properties Managed by TableElm**

```java
// Core table data (synchronized from dialog)
protected int rows;
protected int cols;
protected String[][] cellEquations;
protected String[] outputNames;        // Column headers
protected double[] initialValues;

// Compiled expressions (not in dialog)
private Expr[][] compiledExpressions;
private ExprState[][] expressionStates;
private double[][] computedValues;

// Display properties (not in dialog)
protected int cellSize;
protected int cellSpacing;
protected String tableUnits;
protected int decimalPlaces;
protected boolean showInitialValues;
```

### **Operations in TableElm**

#### **1. Drawing & Rendering**
```java
draw()                    // Render table on canvas
drawColumnHeaders()       // Draw header row
drawCells()              // Draw data cells
drawSums()               // Draw computed sums
```

#### **2. Simulation**
```java
doStep()                 // Execute simulation step
stepFinished()           // Post-step processing
stamp()                  // Stamp circuit matrix
```

#### **3. Pin Management**
```java
setupPins()              // Create output pins
allocNodes()             // Allocate circuit nodes
```

#### **4. Data Synchronization**
```java
resizeTable(rows, cols)              // Resize arrays, preserve data
setCellEquation(row, col, equation)  // Update and recompile equation
setColumnHeader(col, name)           // Update header
setInitialConditionValue(col, val)   // Update initial value
```

---

## Data Flow Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     TableEditDialog                         │
│  ┌────────────────────────────────────────────────────┐    │
│  │ User Interface (Grid with contextual buttons)      │    │
│  └────────────┬───────────────────────────────────────┘    │
│               │                                             │
│  ┌────────────▼───────────────────────────────────────┐    │
│  │ Data Management (rows, cols, cellData, headers)    │    │
│  └────────────┬───────────────────────────────────────┘    │
│               │                                             │
│          Apply Changes                                      │
└───────────────┼─────────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────┐
│                        TableElm                             │
│  ┌────────────────────────────────────────────────────┐    │
│  │ resizeTable() - Update dimensions                  │    │
│  │ setCellEquation() - Update & compile equations     │    │
│  │ setColumnHeader() - Update headers                 │    │
│  │ setInitialConditionValue() - Update initials       │    │
│  └────────────┬───────────────────────────────────────┘    │
│               │                                             │
│  ┌────────────▼───────────────────────────────────────┐    │
│  │ Simulation Engine (doStep, compiled expressions)   │    │
│  └────────────┬───────────────────────────────────────┘    │
│               │                                             │
│  ┌────────────▼───────────────────────────────────────┐    │
│  │ Display Engine (draw, pins, geometry)              │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

---

## Key Design Principles

### **1. Separation of Concerns**
- ✅ **TableEditDialog**: UI and data editing logic
- ✅ **TableElm**: Simulation and rendering logic

### **2. Data Ownership**
- ✅ **TableEditDialog**: Owns temporary working copy during editing
- ✅ **TableElm**: Owns authoritative data for simulation/display

### **3. Synchronization**
- ✅ Changes are batched and applied on "Apply" or "OK"
- ✅ No partial updates - atomic commit of all changes
- ✅ TableElm properly handles resize with data preservation

### **4. Independence**
- ✅ Dialog can manipulate rows/columns without affecting simulation
- ✅ TableElm doesn't need to know about dialog internals
- ✅ Clean API boundary via public methods

---

## Unicode Button Symbols

All contextual button symbols are defined as constants for easy configuration:

```java
private static final String SYMBOL_ADD = "⧾";      // Add column/row
private static final String SYMBOL_DELETE = "⧿";   // Delete column/row
private static final String SYMBOL_LEFT = "←";     // Move left
private static final String SYMBOL_RIGHT = "→";    // Move right
private static final String SYMBOL_UP = "↑";       // Move up
private static final String SYMBOL_DOWN = "↓";     // Move down
```

**Location**: Lines 60-65 in `TableEditDialog.java`  
**Benefit**: Easy to modify symbols without searching through code

---

## Grid Layout Structure

```
Row 0: HEADER_ROW          - "", "", "Asset", "Liability", "Equity", "A_L_E"
Row 1: BUTTON_ROW          - Contextual manipulation buttons
Row 2: STOCK_VALUES_ROW    - "", "", [editable stock values]
Row 3: FLOWS_ROW           - "Flows↓/Stock Vars →", "", [column headers]
Row 4: INITIAL_ROW         - "InitialConditions", "", [initial values]
Row 5+: DATA_START_ROW     - [Flow descriptions and data cells]

Col 0: BUTTON_COL          - Row manipulation buttons
Col 1: LABEL_COL           - Row labels
Col 2+: DATA_START_COL     - Data columns
```

---

## Compilation Status

✅ **BUILD SUCCESSFUL** - All code compiles without errors  
✅ **Architecture** - Clean separation implemented  
✅ **Functionality** - Full CRUD operations on rows/columns  
✅ **Data Sync** - Proper synchronization between dialog and element

---

## Summary

The implementation successfully achieves the goal of moving table structure and data editing to `TableEditDialog` while keeping all display and simulation logic in `TableElm`. The architecture provides:

1. **Clean API** - Well-defined methods for data synchronization
2. **User Experience** - Interactive editing without affecting live simulation
3. **Maintainability** - Clear separation makes code easier to understand
4. **Extensibility** - Easy to add new features to either component
5. **Configurability** - Unicode symbols and layout constants easily adjustable

The refactoring is **complete and functional**.
