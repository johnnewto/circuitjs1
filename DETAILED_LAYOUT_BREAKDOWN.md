# TableElm Spreadsheet Layout - Detailed Breakdown

## Complete Layout with All Features

```
┌─────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│  Description    │    Cash      │   Inventory  │     Debt     │    Equity    │  ← Row 0: HEADERS
│                 │              │              │              │              │
└─────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
┌─────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│    (empty)      │    Asset     │    Asset     │  Liability   │    Equity    │  ← Row 1: COLUMN TYPES ★NEW
│  (light gray)   │ (light gray) │ (light gray) │ (light gray) │ (light gray) │       Shows class of each column
└─────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
┌─────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│    Initial      │   1000.00$   │    500.00$   │    800.00$   │    700.00$   │  ← Row 2: INITIAL CONDITIONS
│  (light gray)   │ (voltage clr)│ (voltage clr)│ (voltage clr)│ (voltage clr)│       (if showInitialValues=true)
└─────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
═══════════════════════════════════════════════════════════════════════════════  ← Separator Line
┌─────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│   Revenue       │ node1: 100$  │ node2: -50$  │ node3: 0$    │ node4: 150$  │  ← Row 3: DATA ROW 1 ★NEW
│  (light gray)   │ (voltage clr)│ (voltage clr)│ (voltage clr)│ (voltage clr)│       Label: "Revenue"
└─────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
┌─────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│   Expenses      │ node5: -80$  │ node6: 80$   │ node7: 0$    │ node8: 0$    │  ← Row 4: DATA ROW 2 ★NEW
│  (light gray)   │ (voltage clr)│ (voltage clr)│ (voltage clr)│ (voltage clr)│       Label: "Expenses"
└─────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
┌─────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│  Loan Payment   │ node9: -50$  │ node10: 0$   │ node11: -50$ │ node12: 0$   │  ← Row 5: DATA ROW 3 ★NEW
│  (light gray)   │ (voltage clr)│ (voltage clr)│ (voltage clr)│ (voltage clr)│       Label: "Loan Payment"
└─────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
═══════════════════════════════════════════════════════════════════════════════  ← Separator Line
┌─────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│   Computed      │ Cash: 970$   │  Inv: 530$   │ Debt: 750$   │  Eq: 850$    │  ← Last Row: COMPUTED VALUES
│  (light gray)   │ (voltage clr)│ (voltage clr)│ (voltage clr)│ (voltage clr)│       Column sums
└─────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
                  │              │              │              │
                  ▼              ▼              ▼              ▼
               Pin OUT         Pin OUT        Pin OUT        Pin OUT            ← Output Pins
```

## Cell-by-Cell Breakdown

### Header Row (Row 0)
| Cell | Content | Background | Purpose |
|------|---------|------------|---------|
| [0,0] | "Description" | White | Labels the row description column |
| [0,1] | "Cash" | White | Data column 1 header (from outputNames[0]) |
| [0,2] | "Inventory" | White | Data column 2 header (from outputNames[1]) |
| [0,3] | "Debt" | White | Data column 3 header (from outputNames[2]) |
| [0,4] | "Equity" | White | Data column 4 header (from outputNames[3]) |

### Column Type Row (Row 1) ★NEW
| Cell | Content | Background | Purpose |
|------|---------|------------|---------|
| [1,0] | (empty) | Light Gray | Structural cell |
| [1,1] | "Asset" | Light Gray | Shows column type (from columnTypes[0]) |
| [1,2] | "Asset" | Light Gray | Shows column type (from columnTypes[1]) |
| [1,3] | "Liability" | Light Gray | Shows column type (from columnTypes[2]) |
| [1,4] | "Equity" | Light Gray | Shows column type (from columnTypes[3]) |

### Initial Conditions Row (Row 2 - if showInitialValues=true)
| Cell | Content | Background | Purpose |
|------|---------|------------|---------|
| [2,0] | "Initial" | Light Gray | Labels this as initial conditions |
| [2,1] | "1000.00$" | Voltage Color | Initial value (from initialValues[0]) |
| [2,2] | "500.00$" | Voltage Color | Initial value (from initialValues[1]) |
| [2,3] | "800.00$" | Voltage Color | Initial value (from initialValues[2]) |
| [2,4] | "700.00$" | Voltage Color | Initial value (from initialValues[3]) |

### Data Rows (Rows 3-5) ★NEW ROW LABELS
| Cell | Content | Background | Purpose |
|------|---------|------------|---------|
| [3,0] | "Revenue" | Light Gray | Row description (from rowDescriptions[0]) ★NEW |
| [3,1] | "node1: 100$" | Voltage Color | Equation + value (cellEquations[0][0]) |
| [3,2] | "node2: -50$" | Voltage Color | Equation + value (cellEquations[0][1]) |
| ... | ... | ... | ... |

### Computed Row (Last Row)
| Cell | Content | Background | Purpose |
|------|---------|------------|---------|
| [N,0] | "Computed" | Light Gray | Labels this as computed totals |
| [N,1] | "Cash: 970$" | Voltage Color | Column sum (from doStep() calculation) |
| [N,2] | "Inv: 530$" | Voltage Color | Column sum (from doStep() calculation) |
| [N,3] | "Debt: 750$" | Voltage Color | Column sum (from doStep() calculation) |
| [N,4] | "Eq: 850$" | Voltage Color | Column sum (from doStep() calculation) |

## Grid Lines

### Vertical Lines
```
│         │         │         │         │
↑         ↑         ↑         ↑         ↑
After     After     After     After     After
Row Desc  Column 1  Column 2  Column 3  Column 4
```

### Horizontal Lines
```
─────────────────────────────────────  ← After headers
─────────────────────────────────────  ← After column types
═════════════════════════════════════  ← After column types (separator - thick)
─────────────────────────────────────  ← After initial conditions (if shown)
═════════════════════════════════════  ← After initial (separator - thick, if shown)
─────────────────────────────────────  ← After data row 1
─────────────────────────────────────  ← After data row 2
═════════════════════════════════════  ← Before computed (separator - thick)
─────────────────────────────────────  ← After computed
```

## Dimensions and Spacing

### Cell Dimensions
- **cellSize**: Width/height of each cell (default 64 pixels)
- **cellSpacing**: Gap between cells (default 8 pixels)
- **rowDescColWidth**: Width of row description column (= cellSize)

### Table Width Calculation
```
tableWidth = rowDescColWidth + cellSpacing + (cols * cellSize) + ((cols + 1) * cellSpacing)
           = 64             + 8           + (4 * 64)       + (5 * 8)
           = 64             + 8           + 256            + 40
           = 368 pixels
```

### Table Height Calculation
```
extraRows = (showInitialValues ? 1 : 0) + 1 + 1  // Initial + computed + column type
          = 1 + 1 + 1 = 3

tableHeight = (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20
            = (3 + 3) * 64 + (3 + 3 + 1) * 8 + 20
            = 6 * 64 + 7 * 8 + 20
            = 384 + 56 + 20
            = 460 pixels
```

## Color Legend

| Color | Voltage Range | Usage |
|-------|--------------|-------|
| Dark Red | Large negative | Large losses/decreases |
| Red | Negative | Losses/decreases |
| Gray | Zero | No change |
| Green | Positive | Gains/increases |
| Blue | Large positive | Large gains/increases |
| Light Gray | N/A | Structural cells (labels) |
| White | N/A | Headers |

## Data Flow

### From Circuit → Table Display
1. **Labeled Nodes** provide voltage values
2. **Cell Equations** evaluate using node voltages
3. **getVoltageForCell()** returns computed value
4. **drawTableCells()** displays with color coding

### From Table → Circuit Outputs
1. **doStep()** computes column sums
2. **Voltage Sources** drive output pins with computed values
3. **Output Pins** connect to labeled nodes (if specified)

## Key Features Highlighted

### ★ NEW Features
1. **Row Description Column** (leftmost)
   - Editable in TableEditDialog
   - Persists through save/load
   - Light gray background

2. **Column Type Row** (below headers)
   - Shows Asset/Liability/Equity/A-L-E
   - Helps understand accounting structure
   - Light gray background

3. **Row Labels in Data Rows**
   - Custom text like "Revenue", "Expenses"
   - Makes table self-documenting
   - Stored in rowDescriptions[] array

### Enhanced Features
- All drawing methods updated for new layout
- Pin positions adjusted for row description column
- Grid lines properly separate all sections
- Serialization includes row descriptions

## Usage Example

```java
// Creating a table with custom row descriptions
TableElm table = new TableElm(100, 100);
table.setRowDescription(0, "Revenue");
table.setRowDescription(1, "Operating Expenses");
table.setRowDescription(2, "Capital Expenditures");

// Getting row descriptions
String desc = table.getRowDescription(1);  // Returns "Operating Expenses"

// Column types are set in TableEditDialog or programmatically
table.setColumnType(0, ColumnType.ASSET);      // Cash
table.setColumnType(1, ColumnType.ASSET);      // Inventory
table.setColumnType(2, ColumnType.LIABILITY);  // Debt
table.setColumnType(3, ColumnType.EQUITY);     // Equity
```

This layout provides a professional, spreadsheet-like appearance while maintaining all the circuit simulation capabilities!
