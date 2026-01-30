# Variable Browser

## Overview

The Variable Browser is a dialog that displays all available variables in the circuit, allowing you to quickly place them as labeled nodes on the canvas.

## Quick Access

- **Menu**: Edit → Variable Browser...
- **Keyboard Shortcut**: `\` (backslash)

## Variable Types

The browser shows three types of variables:

| Type | Source |
|------|--------|
| **Stocks** | Column names from TableElm elements |
| **Labeled Nodes** | Named connection points in the circuit |
| **Variables** | Other variables used in equations |

## How To Use

1. Press `\` or use Edit → Variable Browser...
2. Click any variable name in the list
3. Variable appears on canvas as a labeled node
4. Drag it to desired location
5. Dialog stays open - click more variables as needed

## Features

- **Non-modal**: Doesn't block interaction with the canvas
- **Always-on-top**: Stays visible while you work
- **Auto-refresh**: Updates when circuit changes
- **Click to place**: Instant labeled node creation
- **Alphabetically sorted**: Easy to find variables

## Dialog Layout

```
┌─────────────────────────────────────┐
│ Variable Browser                  [X]│
├─────────────────────────────────────┤
│ Click on any variable to place it   │
├─────────────────────────────────────┤
│ │ Variable Name │ Type            │ │
│ ├───────────────┼─────────────────┤ │
│ │ Population    │ Stock           │ │
│ │ rate          │ Labeled Node    │ │
│ │ growth        │ Variable        │ │
│ └───────────────┴─────────────────┘ │
├─────────────────────────────────────┤
│ [Refresh]                   [Close] │
└─────────────────────────────────────┘
```

## Integration

The Variable Browser uses the same variable sources as:
- ODEElm equation autocomplete
- TableElm cell references
- Any equation field with autocomplete
