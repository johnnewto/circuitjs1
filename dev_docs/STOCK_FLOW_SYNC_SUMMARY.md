# Stock-Flow Row Synchronization

## Overview

When multiple tables share the same stock name (column header), their rows are automatically synchronized so all tables see the same set of flow descriptions.

**Example:**
```
Table A: "Cash" stock with rows [Sales, Interest]
Table B: "Cash" stock with rows [Wages, Rent, Utilities]

RESULT: Both tables show all 5 rows: Sales, Interest, Wages, Rent, Utilities
```

## Key Features

- **Row descriptions synchronized** - All tables sharing a stock see the same flow names
- **Equations remain table-specific** - Each table keeps its own cell values
- **User-triggered sync** - Synchronization happens on edit/load, not every simulation step
- **Visual feedback** - Yellow highlighting indicates shared stocks

## How It Works

1. When you edit a table with shared stocks, the system finds all related tables
2. Row descriptions from all related tables are merged into a unified set
3. Each table is updated with the merged rows
4. Existing equations are preserved; new rows get empty equations

## Visual Feedback

Shared stocks are highlighted with a yellow background in the table editor:

```
┌─────────────────────────┐
│ 💰 Cash  (Yellow BG)    │  ← Yellow = shared with other tables
│ ━━━━━━━━━━━━━━━━━━━━━━ │
│ Sales      │ 100        │
│ Interest   │ 5          │
│ Wages      │ 0          │  ← Empty = added from another table
│ Rent       │ 0          │
└─────────────────────────┘
```

## Related Components

- **StockFlowRegistry** - Central service managing synchronization
- **TableElm / GodlyTableElm** - Table elements that participate in sync
- **StockMasterElm** - Displays which table owns each stock
- **FlowsMasterElm** - Displays all flows across tables
