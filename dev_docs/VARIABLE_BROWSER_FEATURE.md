# Variable Browser Feature

## Overview

The Variable Browser is a non-modal, always-on-top dialog that displays all available variables in the circuit. It provides a convenient way to view and place variables (labeled nodes, stock names, and equation variables) onto the canvas.

## Features

### Display
- **Non-modal Dialog**: The dialog doesn't block interaction with the main canvas
- **Always-on-top**: Stays visible while you work on the circuit
- **No Focus Stealing**: Clicking on variables doesn't steal focus from the canvas
- **Auto-refresh**: Updates automatically when the circuit changes

### Variable Types

The Variable Browser displays three types of variables:

1. **Stocks** - Variables from TableElm cell equations
2. **Labeled Nodes** - Named connection points in the circuit  
3. **Variables** - Other variables used in cell equations

### Usage

#### Opening the Dialog

**Menu**: Edit → Variable Browser...  
**Keyboard Shortcut**: `\` (backslash)

#### Placing Variables

1. Open the Variable Browser dialog
2. Click on any variable name in the table
3. The variable will be placed on the canvas as a labeled node at the center of the visible area
4. The newly placed element is automatically selected and ready to be dragged to the desired location
5. The dialog remains open, allowing you to place multiple variables

#### Refreshing

The dialog automatically refreshes when:
- The circuit is analyzed (after adding/removing elements)
- Circuit elements are modified

You can also manually refresh by clicking the **Refresh** button in the dialog.

## Implementation Details

### Files Modified

1. **VariableBrowserDialog.java** (new file)
   - Main dialog implementation
   - Table display with clickable variable names
   - Non-modal behavior with `setModal(false)` and `setGlassEnabled(false)`

2. **CirSim.java** (modified)
   - Added menu item: Edit → Variable Browser...
   - Added keyboard shortcut: `\`
   - Added refresh hook in `analyzeCircuit()` method
   - Menu handler in `menuPerformed()` method

### Key Design Decisions

1. **Non-modal**: The dialog uses `setModal(false)` and `setGlassEnabled(false)` to allow interaction with the circuit while the dialog is open

2. **Singleton Pattern**: Only one instance of the dialog can exist at a time to avoid confusion

3. **Auto-refresh**: The dialog automatically refreshes when `analyzeCircuit()` is called, ensuring the variable list is always up-to-date

4. **No Focus Stealing**: Variables are placed without closing the dialog, and the new element is immediately selected for repositioning

5. **Centered Placement**: New labeled nodes are placed at the center of the visible canvas area, then automatically selected for immediate dragging

## User Interface

```
┌─────────────────────────────────────┐
│ Variable Browser                  [X]│
├─────────────────────────────────────┤
│ Click on any variable to place it  │
│ on the canvas                       │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ Variable Name │ Type            │ │
│ ├───────────────┼─────────────────┤ │
│ │ Population    │ Stock           │ │
│ │ rate          │ Labeled Node    │ │
│ │ time          │ Variable        │ │
│ │ ...           │ ...             │ │
│ └─────────────────────────────────┘ │
├─────────────────────────────────────┤
│ [Refresh]                   [Close] │
└─────────────────────────────────────┘
```

## Example Workflow

1. Create a circuit with a TableElm containing stock variables
2. Add some labeled nodes for connections
3. Open the Variable Browser (Edit → Variable Browser... or press `\`)
4. Click on a stock variable name - it appears on the canvas as a labeled node
5. Drag it to the desired location
6. Click another variable - it's placed and ready to position
7. Continue working with the dialog open for easy access to all variables

## Integration with ODEElm

The Variable Browser is particularly useful when working with ODEElm (ODE Calculator) elements:

1. ODEElm equations can reference variables displayed in the Variable Browser
2. The browser shows all stocks, labeled nodes, and variables that can be used in ODE equations
3. Autocomplete in ODEElm equation fields uses the same variable list

## Technical Notes

### Variable Collection

Variables are collected from:
- `StockFlowRegistry.getAllStockNames()` - Stock variables from TableElm
- `LabeledNodeElm.getSortedLabeledNodeNames()` - All labeled nodes
- `StockFlowRegistry.getAllCellEquationVariables()` - Variables used in equations

### Performance

- Variables are sorted alphabetically for easy lookup
- The dialog caches results until refresh is called
- Refreshing only happens when the circuit structure changes

### Browser Compatibility

Works in all modern browsers that support:
- GWT DialogBox
- HTML5 table rendering
- CSS styling

## Future Enhancements

Potential improvements:
- Filter/search functionality within the dialog
- Show variable values in real-time during simulation
- Drag-and-drop from dialog to canvas
- Group variables by type (collapsible sections)
- Export variable list to CSV
- Show which elements use each variable
