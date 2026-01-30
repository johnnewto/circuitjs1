# StockMasterElm - Master Stocks Display Component

## Overview

`StockMasterElm` is a specialized circuit element that provides a real-time view of all **master stocks** in the circuit. It displays which table is the electrical "master" (driver) for each stock variable across all TableElm and GodlyTableElm elements.

## Purpose

In stock-flow models with multiple tables sharing the same stock variables (e.g., "Firms", "Debt", "Reserves"), only ONE table should electrically drive each stock. The StockMasterElm provides visibility into:

1. **All master stocks** - Which stocks have registered masters
2. **Current values** - Real-time voltages of each stock
3. **Master table assignment** - Which table is driving each stock

## Features

- **Auto-discovery**: Automatically finds all master stocks from ComputedValues registry
- **Real-time updates**: Refreshes display twice per second (500ms interval)
- **Voltage coloring**: Stock values use voltage color scaling when enabled
- **Sortable display**: Stocks are displayed in alphabetical order
- **Compact layout**: Shows stock name, value, and owning table in a table format
- **No electrical connections**: Pure display element (no pins/posts)
- **Resizable**: Drag diagonal endpoint to adjust table width

## Menu Location

**Main Menu → Add Master Stocks Table**

## Display Format

```
┌──────────────────────────────────────┐
│        Master Stocks                 │
├──────────────┬────────┬──────────────┤
│ Stock        │ Value  │ Master Table │
├──────────────┼────────┼──────────────┤
│ Banks        │ 10 V   │ Banks        │
│ Debt         │ 100 V  │ Firms        │
│ Firms        │ 90 V   │ Firms        │
│ Firm_equity  │ 10 V   │ Firms        │
│ Gov_Bonds    │ 0 V    │ Banks        │
│ Reserves     │ 100 V  │ Banks        │
└──────────────┴────────┴──────────────┘
```

## Technical Details

- **Source**: `src/com/lushprojects/circuitjs1/client/StockMasterElm.java`
- **Dump Type**: 450
- **Base Class**: `ChipElm`

## Usage Scenarios

### 1. Debugging Stock Synchronization

When multiple tables reference the same stock but show different values, StockMasterElm helps identify:
- Which table is the master (electrically driving the stock)
- Whether other tables are properly reading from the master

### 2. Circuit Documentation

Provides a quick visual reference of:
- All stock variables in the model
- Current stock levels
- Table ownership structure

### 3. Model Validation

Verify that:
- Each shared stock has exactly one master
- Stock values are reasonable
- Master assignments match expectations

## Comparison with Markdown Debug View

| Feature | StockMasterElm | Markdown Debug View |
|---------|----------------|---------------------|
| Real-time values | ✓ | ✓ |
| Master identification | ✓ | ✓ |
| Flow details | ✗ | ✓ |
| Stock sharing info | ✗ | ✓ |
| In-circuit display | ✓ | ✗ |
| Always visible | ✓ | ✗ |
| Updates automatically | ✓ (500ms) | ✓ (on dialog open) |

**Use StockMasterElm when**: You want a persistent, in-circuit view of master stocks during simulation

**Use Markdown Debug View when**: You need detailed flow-by-flow analysis and complete stock sharing information

## Configuration

### Edit Properties (Right-click → Edit)

- **Title**: Customize the display title (default: "Master Stocks")

## Example Circuit

Given a circuit with these tables:
- **Firms** table with columns: Firms, Debt, Firm_equity
- **Banks** table with columns: Reserves, Debt, Gov_Bonds, Workers, Firms, Banks

The StockMasterElm would show:
- **Firms** → master: Firms
- **Debt** → master: Firms (first to register)
- **Firm_equity** → master: Firms
- **Reserves** → master: Banks
- **Gov_Bonds** → master: Banks
- **Banks** → master: Banks
- **Workers** → master: (depends on which table registered first)

## Limitations

1. **Display only** - No electrical outputs
2. **Master stocks only** - Doesn't show non-master table columns
3. **No flow information** - Only shows final stock values
4. **No A-L-E columns** - A-L-E computed columns are not registered as stocks

## Future Enhancements

Possible improvements:
- Filter by table name
- Show which other tables reference each stock
- Highlight stocks with multiple tables (shared stocks)
- Export stock values to CSV
- Add warning indicators for stocks with no master

## Related Components

- **TableElm** - Main stock-flow table component
- **GodlyTableElm** - Specialized stock-flow table (Godley's accounting)
- **FlowsMasterElm** - Displays all flows across tables
- **ComputedValues** - Static registry for stock values and master assignments
