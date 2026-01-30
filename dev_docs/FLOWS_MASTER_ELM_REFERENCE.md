# FlowsMasterElm - All Flows Display Component

## Overview

`FlowsMasterElm` is a specialized circuit element that provides a real-time view of all **flows** (rows) across all tables in the circuit. It shows which flows are used, how many tables use each flow, and helps identify shared flows between tables.

## Purpose

In stock-flow models with multiple tables, flows represent transactions/operations that modify stock levels. The FlowsMasterElm provides visibility into:

1. **All unique flows** - Complete list of flow names from all tables
2. **Usage count** - How many tables use each flow
3. **Table references** - Which specific tables contain each flow

## Features

- **Auto-discovery**: Automatically scans all TableElm and GodlyTableElm elements
- **Real-time updates**: Refreshes display twice per second (500ms interval)
- **Shared flow highlighting**: Flows used by multiple tables shown in light blue
- **Sortable display**: Flows displayed in alphabetical order
- **Compact layout**: Shows flow name, count, and table names in a table format
- **No electrical connections**: Pure display element (no pins/posts)
- **Resizable**: Drag diagonal endpoint to adjust table width

## Menu Location

**Main Menu → Add Flows Table**

## Display Format

```
┌─────────────────────────────────────────────┐
│              All Flows                      │
├──────────────────┬───────┬──────────────────┤
│ Flow             │ Count │ Tables           │
├──────────────────┼───────┼──────────────────┤
│ Bank Spending    │ 1     │ Banks            │
│ Govt Sells Bonds │ 1     │ Banks            │
│ Govt Spends      │ 2     │ Banks, Treasury  │ ← Shared (blue)
│ Govt Taxes       │ 2     │ Banks, Treasury  │ ← Shared (blue)
│ Hire Workers     │ 1     │ Firms            │
│ Interest on Bond │ 1     │ Banks            │
│ Lending          │ 2     │ Firms, Banks     │ ← Shared (blue)
│ Pay Interest     │ 2     │ Firms, Banks     │ ← Shared (blue)
│ Workers Consume  │ 1     │ Firms            │
└──────────────────┴───────┴──────────────────┘
```

## Technical Details

- **Source**: `src/com/lushprojects/circuitjs1/client/FlowsMasterElm.java`
- **Dump Type**: 451
- **Base Class**: `ChipElm`

## Usage Scenarios

### 1. Flow Consistency Verification

When multiple tables should use the same flow names (e.g., "Lending" appears in both Firms and Banks tables), FlowsMasterElm helps verify:
- Flow names are spelled consistently
- All expected flows are present
- No duplicate/misspelled flows exist

### 2. Model Documentation

Provides a quick reference of:
- All operations/transactions in the model
- Which sectors perform which operations
- Inter-table dependencies

### 3. Accounting Balance Verification

In Godley table accounting:
- Shared flows (count > 1) indicate flows that affect multiple sectors
- Each shared flow should balance across sectors (debits = credits)
- Missing flows might indicate incomplete accounting

## Color Coding

| Color | Meaning |
|-------|---------|
| White | Flow used by single table only |
| Light Blue | **Shared flow** - used by 2+ tables |

Shared flows are important in stock-flow accounting as they represent transactions between sectors.

## Comparison with TableMasterElm

| Feature | FlowsMasterElm | TableMasterElm |
|---------|----------------|----------------|
| Shows | Flows (rows) | Stocks (columns) |
| Display | Flow usage count | Current stock values |
| Focus | Transaction names | Asset/Liability levels |
| Highlights | Shared flows | Master ownership |
| Use case | Flow consistency | Stock synchronization |

**Use FlowsMasterElm when**: You want to see all transaction types and their distribution across tables

**Use TableMasterElm when**: You want to monitor stock values and master table assignments

## Configuration

### Edit Properties (Right-click → Edit)

- **Title**: Customize the display title (default: "All Flows")

## Example Output

Given a circuit with these tables:

**Firms Table** (rows):
- Pay Interest
- Lending
- Bank Spending
- Govt Taxes
- Govt Spends
- Workers Consume
- Hire Workers

**Banks Table** (rows):
- Lending
- Pay Interest
- Bank Spending
- Govt Taxes
- Govt Spends
- Govt Sells Bonds
- Interest on Bonds
- Workers Consume
- Hire Workers

FlowsMasterElm would show:
- **Bank Spending**: Count=2, Tables=Firms, Banks
- **Govt Sells Bonds**: Count=1, Tables=Banks
- **Govt Spends**: Count=2, Tables=Firms, Banks (highlighted blue)
- **Govt Taxes**: Count=2, Tables=Firms, Banks (highlighted blue)
- **Hire Workers**: Count=2, Tables=Firms, Banks (highlighted blue)
- **Interest on Bonds**: Count=1, Tables=Banks
- **Lending**: Count=2, Tables=Firms, Banks (highlighted blue)
- **Pay Interest**: Count=2, Tables=Firms, Banks (highlighted blue)
- **Workers Consume**: Count=2, Tables=Firms, Banks (highlighted blue)

## Info Display (Mouse Hover)

When hovering over the element, shows:
- Total number of unique flows
- Number of shared flows (if any)

Example:
```
Flows Table
Showing 12 unique flow(s)
5 flow(s) shared across tables
```

## Limitations

1. **Display only** - No electrical outputs
2. **Row descriptions only** - Relies on TableElm.rowDescriptions[] being populated
3. **No flow values** - Shows names only, not computed flow rates
4. **No equation display** - Doesn't show cell equations or calculations

## Use Cases in Stock-Flow Modeling

### Godley Table Accounting

In double-entry bookkeeping (Godley tables), flows must balance:
- Each flow row should sum to zero across all columns
- Shared flows coordinate transactions between sectors
- FlowsMasterElm helps verify all sectors record their side of transactions

### Debugging

Common issues FlowsMasterElm helps catch:
- **Typos**: "Goverment Spending" vs "Government Spending"
- **Inconsistent naming**: "Pay Interest" vs "Interest Payment"
- **Missing flows**: One table has a flow the other doesn't
- **Unexpected sharing**: Flows that shouldn't be shared between tables

## Related Components

- **TableElm** - Main stock-flow table component (contains rowDescriptions)
- **GodlyTableElm** - Specialized stock-flow table (also uses rowDescriptions)
- **StockMasterElm** - Displays master stocks (columns, not rows)
