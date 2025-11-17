# Bash-Style Autocompletion Feature

## Overview

The EditDialog now supports bash-style autocompletion for text fields with **automatic match display**. This feature allows users to see available completions as they type, and complete them with Tab, making it easy to enter variable names, node names, stock names, and other identifiers.

## Implementation

The autocompletion feature was implemented in:
- `EditDialog.java` - Main implementation with automatic match display and tab completion
- `EditInfo.java` - Added `completionList` field to store completion options

## How It Works

### User Experience

1. **Type a partial word**: As you type (e.g., `sto`), matching completions automatically appear in a hint label above the input field
2. **Continue typing or use Tab**:
   - **Continue typing**: Matches update in real-time as you type
   - **Press Tab once**: 
     - If one match: Completes the full word immediately
     - If multiple matches: Completes to longest common prefix
   - **Press Tab again**: Cycles through all matching options
3. **Hint visibility**: 
   - Shows automatically when there are relevant matches
   - Hides when typing non-matching characters
   - Hides when you've completed the full word

### Features

- **Real-time match display**: Matches appear automatically as you type (no Tab needed to see options)
- **Case-insensitive matching**: Matches variables regardless of case
- **Word boundary detection**: Only completes valid identifiers (letters, numbers, underscores)
- **Cycle through matches**: Multiple tabs cycle through all possibilities
- **Longest common prefix**: First tab completes to common prefix (like bash)
- **Cursor positioning**: Cursor moves to end of completed word
- **Visual feedback**: Shows available matches in a styled hint label above the input field
- **Smart visibility**: Hint only appears when there are relevant matches (1 character minimum)
- **No UI clutter**: Hint disappears when you've typed the complete word

## Usage Example

### In Circuit Elements

To enable autocompletion for a text field in your circuit element's `getEditInfo()` method:

```java
public EditInfo getEditInfo(int n) {
    if (n == 1) {
        EditInfo ei = new EditInfo("Equation (d/dt)", 0, -1, -1);
        ei.text = equationString;
        ei.disallowSliders();
        
        // Create completion list from available variables
        java.util.List<String> completions = new java.util.ArrayList<String>();
        
        // Add node names from circuit
        for (int i = 0; i < sim.getNodeCount(); i++) {
            String nodeName = sim.getNodeName(i);
            if (nodeName != null && !nodeName.isEmpty()) {
                completions.add(nodeName);
            }
        }
        
        // Add stock/variable names
        completions.add("stock1");
        completions.add("stock2");
        completions.add("flow_rate");
        completions.add("accumulator");
        
        // Add parameter names
        completions.add("param_a");
        completions.add("param_b");
        completions.add("param_c");
        
        // Attach completion list to EditInfo
        ei.completionList = completions;
        
        return ei;
    }
    return null;
}
```

### Example with ODEElm

For the ODE element, you might want to autocomplete:
- Other ODE element names
- Stock names
- Labeled node names
- Parameter names
- Mathematical functions

**Actual implementation in ODEElm.java:**

```java
public EditInfo getEditInfo(int n) {
    if (n == 0) {
        EditInfo ei = new EditInfo("Name", 0, -1, -1);
        ei.text = elementName;
        ei.disallowSliders();
        
        // Build completion list for element names
        java.util.List<String> completions = new java.util.ArrayList<String>();
        
        // Add stock variables (useful for naming ODEs after what they track)
        java.util.Set<String> stockNames = StockFlowRegistry.getAllStockNames();
        if (stockNames != null && !stockNames.isEmpty()) {
            for (String stockName : stockNames) {
                completions.add(stockName);
            }
        }
        
        // Add other ODE element names in the circuit
        if (sim != null && sim.elmList != null) {
            for (int i = 0; i < sim.elmList.size(); i++) {
                CircuitElm ce = sim.getElm(i);
                if (ce instanceof ODEElm && ce != this) {
                    ODEElm ode = (ODEElm) ce;
                    if (ode.elementName != null && !ode.elementName.isEmpty()) {
                        completions.add(ode.elementName);
                    }
                }
            }
        }
        
        // Add labeled node names
        String[] labeledNodes = LabeledNodeElm.getSortedLabeledNodeNames();
        if (labeledNodes != null && labeledNodes.length > 0) {
            for (String nodeName : labeledNodes) {
                completions.add(nodeName);
            }
        }
        
        // Attach completion list for tab completion
        ei.completionList = completions;
        
        return ei;
    }
    if (n == 1) {
        EditInfo ei = new EditInfo("Equation (d/dt)", 0, -1, -1);
        ei.text = equationString;
        ei.disallowSliders();
        
        // Build completion list for bash-style autocompletion
        java.util.List<String> completions = new java.util.ArrayList<String>();
        
        // Add stock variables from TableElm cell equations
        java.util.Set<String> stockNames = StockFlowRegistry.getAllStockNames();
        if (stockNames != null && !stockNames.isEmpty()) {
            for (String stockName : stockNames) {
                completions.add(stockName);
            }
        }
        
        // Add labeled node names
        String[] labeledNodes = LabeledNodeElm.getSortedLabeledNodeNames();
        if (labeledNodes != null && labeledNodes.length > 0) {
            for (String nodeName : labeledNodes) {
                completions.add(nodeName);
            }
        }
        
        // Add variables used in cell equations
        java.util.Set<String> cellVariables = StockFlowRegistry.getAllCellEquationVariables();
        if (cellVariables != null && !cellVariables.isEmpty()) {
            for (String varName : cellVariables) {
                completions.add(varName);
            }
        }
        
        // Add other ODE element names in the circuit
        if (sim != null && sim.elmList != null) {
            for (int i = 0; i < sim.elmList.size(); i++) {
                CircuitElm ce = sim.getElm(i);
                if (ce instanceof ODEElm && ce != this) {
                    ODEElm ode = (ODEElm) ce;
                    if (ode.elementName != null && !ode.elementName.isEmpty()) {
                        completions.add(ode.elementName);
                    }
                }
            }
        }
        
        // Add parameter names for this ODE element
        for (int i = 0; i < numParameters; i++) {
            completions.add(PARAM_NAMES[i]);
        }
        
        // Add mathematical functions
        completions.add("sin");
        completions.add("cos");
        completions.add("tan");
        completions.add("exp");
        completions.add("log");
        completions.add("sqrt");
        completions.add("abs");
        completions.add("min");
        completions.add("max");
        completions.add("pow");
        completions.add("atan2");
        completions.add("floor");
        completions.add("ceil");
        
        // Add common constants
        completions.add("pi");
        completions.add("e");
        completions.add("t");  // time variable
        
        // Attach completion list for tab completion
        ei.completionList = completions;
        
        return ei;
    }
    // ... rest of method
}
```

## Implementation Details

### Key Methods

- `addAutocompleteHandler()`: Attaches KeyDownHandler to TextBox and creates hint label above it
- `handleTabCompletion()`: Main completion logic, shows matches in hint label when multiple options exist
- `findWordStart()`: Finds start of current word being typed
- `findMatches()`: Filters completion list by prefix
- `findLongestCommonPrefix()`: Finds common prefix among matches
- `replaceWord()`: Replaces partial word with completion
- `showMatchesInLabel()`: Displays available matches in hint label above input field
- `resetAutocompleteState()`: Clears state and hides hint label when user types non-tab key

### Visual Design

The hint label has the following styling:
- **Font**: 11px monospace (consistent spacing)
- **Color**: Gray (#666) on light gray background (#f0f0f0)
- **Border**: 1px solid border with rounded corners
- **Position**: Appears directly above the input field
- **Visibility**: Only shown when there are multiple matches (2+)
- **Content**: Shows match count and up to 20 matches inline

### State Tracking

The EditDialog maintains three state variables:
- `lastAutocompletePrefix`: Last prefix that was completed
- `autocompleteIndex`: Current position in matches list
- `autocompleteMatches`: Filtered list of matching completions

These are reset whenever the user types anything other than Tab.

## Benefits

1. **Faster typing**: Users don't need to type full variable names
2. **Fewer errors**: Reduces typos in variable names
3. **Discovery**: Users can see available variables by pressing Tab (shown in hint label)
4. **Familiar UX**: Behaves like bash/terminal completion
5. **No UI clutter**: No dropdown boxes - hint label only appears when needed
6. **Visual feedback**: Immediate visual feedback directly above the input field
7. **Integrated design**: Hint label is styled to blend with the dialog interface

### Visual Example

When typing `s` + Tab with multiple matches, a hint label appears above the input:

```
┌─────────────────────────────────────────────────────┐
│ Matches (8): stock1  stock2  sin  sqrt  source1... │
├─────────────────────────────────────────────────────┤
│ s█                                                   │
└─────────────────────────────────────────────────────┘
```

The hint automatically:
- Appears when pressing Tab with multiple matches
- Disappears when typing any other key
- Updates as you cycle through matches with Tab

## Future Enhancements

Possible improvements:
- Show all matches in a tooltip on first Tab
- Support completion in middle of text (currently only before cursor)
- Context-aware completion based on equation syntax
- Fuzzy matching (not just prefix matching)
- Multi-word completion (for namespaced variables)

## Migration from SuggestBox

If you're currently using `SuggestBox` for autocompletion, you can easily migrate to the bash-style completion:

### Before (using SuggestBox):
```java
public EditInfo getEditInfo(int n) {
    if (n == 0) {
        EditInfo ei = new EditInfo("Text", 0, -1, -1);
        ei.text = text;
        
        // Create autocomplete SuggestBox
        MultiWordSuggestOracle oracle = new MultiWordSuggestOracle();
        
        // Add labeled node names
        if (labelList != null && !labelList.isEmpty()) {
            for (String labelName : labelList.keySet()) {
                oracle.add(labelName);
            }
        }
        
        // Add stock variables
        java.util.Set<String> stockNames = StockFlowRegistry.getAllStockNames();
        if (stockNames != null && !stockNames.isEmpty()) {
            for (String stockName : stockNames) {
                oracle.add(stockName);
            }
        }
        
        ei.suggestBox = new SuggestBox(oracle);
        ei.suggestBox.setText(text);
        ei.suggestBox.setWidth("200px");
        
        return ei;
    }
    return null;
}
```

### After (using bash-style completion):
```java
public EditInfo getEditInfo(int n) {
    if (n == 0) {
        EditInfo ei = new EditInfo("Text", 0, -1, -1);
        ei.text = text;
        
        // Create completion list
        java.util.List<String> completions = new java.util.ArrayList<String>();
        
        // Add labeled node names
        if (labelList != null && !labelList.isEmpty()) {
            for (String labelName : labelList.keySet()) {
                completions.add(labelName);
            }
        }
        
        // Add stock variables
        java.util.Set<String> stockNames = StockFlowRegistry.getAllStockNames();
        if (stockNames != null && !stockNames.isEmpty()) {
            for (String stockName : stockNames) {
                completions.add(stockName);
            }
        }
        
        // Attach completion list (enables tab completion)
        ei.completionList = completions;
        
        return ei;
    }
    return null;
}
```

### Key Differences:

- **SuggestBox**: Creates dropdown menu as you type, requires mouse interaction or arrow keys
- **Bash-style**: Uses Tab key only, no UI overlay, more keyboard-friendly
- **Performance**: Bash-style is lighter weight (no DOM elements for dropdown)
- **UX**: Bash-style is more familiar to users comfortable with terminals

You can use both approaches in different contexts based on user preference. SuggestBox is better for discovery, while bash-style is faster for power users.
