# GodlyTableElm Implementation Instructions

## Overview

Create a `GodlyTableElm` that extends `TableElm` to add integration capabilities. The last row (sum row) will perform integration using the equation **"lastoutput+timestep*100*columnSum"** for each column. This creates an integrator where each column's sum is integrated over time, similar to how `VCVSElm` uses expressions with `lastoutput` and `timestep` variables.

**Key Features:**
- Extends `TableElm` to inherit all table display and column sum functionality
- Integration equation: `lastoutput + timestep * 100 * columnSum`
- Uses CircuitJS1's expression evaluation system (`Expr` and `ExprState`) 
- Integration results stored as computed values accessible by other elements
- Configurable integration gain (default 100)
- Reset functionality to clear integration state

## Implementation Plan

### 1. Class Structure Design

```java
package com.lushprojects.circuitjs1.client;

import java.util.StringTokenizer;

/**
 * GodlyTableElm - Table with Integration Capabilities
 * Extends TableElm to add integration of column sums over time
 * Uses equation: lastoutput + timestep * integrationGain * columnSum
 */
public class GodlyTableElm extends TableElm {
    private double integrationGain = 100.0; // Multiplier for integration (default 100)
    private Expr integrationExpr;           // Compiled integration expression
    private ExprState[] integrationStates;  // Integration state for each column
    private double[] lastIntegrationOutputs; // Previous integration outputs for each column
    private String integrationExprString = "lastoutput+timestep*100*a"; // Template expression
    
    // Constructor for new table
    public GodlyTableElm(int xx, int yy) {
        super(xx, yy);
        initIntegration();
    }
    
    // File loading constructor  
    public GodlyTableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        parseGodlyTableData(st);
        initIntegration();
    }
}
```

### 2. Integration Initialization

```java
private void initIntegration() {
    // Initialize integration arrays based on current number of columns
    integrationStates = new ExprState[cols];
    lastIntegrationOutputs = new double[cols];
    
    for (int col = 0; col < cols; col++) {
        integrationStates[col] = new ExprState(1); // 1 input (columnSum as 'a')
        lastIntegrationOutputs[col] = 0.0;
    }
    
    // Parse the integration expression
    parseIntegrationExpr();
}

private void parseIntegrationExpr() {
    try {
        // Create expression: lastoutput + timestep * integrationGain * a
        // Where 'a' will be the column sum
        String exprStr = "lastoutput+timestep*" + integrationGain + "*a";
        ExprParser parser = new ExprParser(exprStr);
        integrationExpr = parser.parseExpression();
        String err = parser.gotError();
        if (err != null) {
            CirSim.console("GodlyTableElm: Parse error in integration expression: " + exprStr + ": " + err);
            integrationExpr = null;
        }
    } catch (Exception e) {
        CirSim.console("GodlyTableElm: Error parsing integration expression: " + e.getMessage());
        integrationExpr = null;
    }
}
```

### 3. Override Table Resizing to Handle Integration Arrays

```java
@Override
private void resizeTable() {
    // Call parent resize
    super.resizeTable();
    
    // Resize integration arrays
    ExprState[] oldStates = integrationStates;
    double[] oldOutputs = lastIntegrationOutputs;
    
    integrationStates = new ExprState[cols];
    lastIntegrationOutputs = new double[cols];
    
    // Copy existing states where possible
    for (int col = 0; col < cols; col++) {
        if (oldStates != null && col < oldStates.length) {
            integrationStates[col] = oldStates[col];
            lastIntegrationOutputs[col] = oldOutputs[col];
        } else {
            integrationStates[col] = new ExprState(1);
            lastIntegrationOutputs[col] = 0.0;
        }
    }
    
    // Re-parse integration expression in case gain changed
    parseIntegrationExpr();
}
```

### 4. Enhanced Sum Row with Integration

```java
@Override
private void drawSumRow(Graphics g) {
    int sumRowY = point1.y + 20 + cellSpacing + rows * (cellSize + cellSpacing);
    
    for (int col = 0; col < cols; col++) {
        int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
        
        // Calculate sum for this column (same as parent)
        double columnSum = 0.0;
        for (int row = 0; row < rows; row++) {
            String labelName = labelNames[row][col];
            columnSum += getVoltageForLabel(labelName);
        }
        
        // Perform integration: lastoutput + timestep * gain * columnSum
        double integratedValue = performIntegration(col, columnSum);
        
        // Register both sum and integrated value as computed values
        String sumLabelName = columnHeaders[col];
        String integrationLabelName = columnHeaders[col] + "_integrated";
        
        registerSumAsLabeledNode(sumLabelName, columnSum);
        LabeledNodeElm.setComputedValue(integrationLabelName, integratedValue);
        
        // Draw integration cell background (different color for integration)
        g.setColor(Color.yellow); // Distinct color for integration cells
        g.fillRect(cellX, sumRowY, cellSize, cellSize);
        
        // Draw cell border
        g.setColor(Color.black);
        g.drawRect(cellX, sumRowY, cellSize, cellSize);
        
        // Draw integration label name (top half) 
        g.setColor(Color.black);
        drawCenteredText(g, integrationLabelName, cellX + cellSize/2, sumRowY + cellSize/3, true);
        
        // Draw integrated value (bottom half)
        String integrationText = getVoltageText(integratedValue);
        drawCenteredText(g, integrationText, cellX + cellSize/2, sumRowY + 2*cellSize/3, true);
    }
}

private double performIntegration(int col, double columnSum) {
    if (integrationExpr == null || col >= integrationStates.length) {
        return 0.0; // No integration if expression failed to parse
    }
    
    try {
        ExprState state = integrationStates[col];
        
        // Set up expression state
        state.values[0] = columnSum; // 'a' = columnSum
        state.lastOutput = lastIntegrationOutputs[col];
        state.t = sim.t;
        
        // Evaluate integration expression: lastoutput + timestep * gain * columnSum
        double result = integrationExpr.eval(state);
        
        // Store result for next iteration
        lastIntegrationOutputs[col] = result;
        
        // Update the expression state for next time
        state.updateLastValues(result);
        
        return result;
        
    } catch (Exception e) {
        CirSim.console("GodlyTableElm: Error in integration calculation: " + e.getMessage());
        return lastIntegrationOutputs[col]; // Return last known value on error
    }
}
```

### 5. Enhanced Serialization

```java
@Override
public String dump() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.dump()); // Get TableElm serialization
    sb.append(" ").append(integrationGain); // Add integration gain
    
    // Add integration states (last outputs)
    for (int col = 0; col < cols; col++) {
        sb.append(" ").append(lastIntegrationOutputs[col]);
    }
    
    return sb.toString();
}

private void parseGodlyTableData(StringTokenizer st) {
    // First parse parent TableElm data (this will consume most tokens)
    super.parseTableData(st);
    
    try {
        // Parse integration-specific data
        if (st.hasMoreTokens()) {
            integrationGain = Double.parseDouble(st.nextToken());
        }
        
        // Parse last integration outputs
        if (lastIntegrationOutputs == null) {
            lastIntegrationOutputs = new double[cols];
        }
        
        for (int col = 0; col < cols && st.hasMoreTokens(); col++) {
            lastIntegrationOutputs[col] = Double.parseDouble(st.nextToken());
        }
        
    } catch (Exception e) {
        CirSim.console("GodlyTableElm: error parsing integration data, using defaults");
        integrationGain = 100.0;
        // lastIntegrationOutputs will be initialized to zeros by initIntegration()
    }
}

@Override
int getDumpType() { 
    return 254; // Choose unused dump type (different from TableElm's 253)
}
```

### 6. Enhanced Edit Interface

```java
@Override
public EditInfo getEditInfo(int n) {
    // First handle integration-specific edits
    if (n == 0) {
        return new EditInfo("Integration Gain", integrationGain, 0.1, 1000.0);
    }
    if (n == 1) {
        EditInfo ei = new EditInfo("", 0, -1, -1);
        ei.checkbox = new Checkbox("Reset Integration", false);
        return ei;
    }
    
    // Delegate to parent for other edits, but offset index
    return super.getEditInfo(n - 2);
}

@Override 
public void setEditValue(int n, EditInfo ei) {
    if (n == 0) {
        // Integration gain changed
        integrationGain = ei.value;
        parseIntegrationExpr(); // Reparse expression with new gain
        return;
    }
    if (n == 1) {
        // Reset integration checkbox
        if (ei.checkbox.getState()) {
            resetIntegration();
        }
        return;
    }
    
    // Delegate to parent for other edits
    super.setEditValue(n - 2, ei);
    
    // If table structure changed, reinitialize integration
    if (n - 2 <= 1) { // If rows or cols changed
        initIntegration();
    }
}

private void resetIntegration() {
    for (int col = 0; col < cols; col++) {
        lastIntegrationOutputs[col] = 0.0;
        if (integrationStates[col] != null) {
            integrationStates[col].reset();
        }
    }
}
```

### 7. Information Display

```java
@Override
void getInfo(String arr[]) {
    arr[0] = "Godly Table (" + rows + "x" + cols + ") with Integration";
    arr[1] = "Integration gain: " + integrationGain;
    arr[2] = "Equation: lastoutput + timestep * " + integrationGain + " * columnSum";
    
    int idx = 3;
    // Show some sample values including integration results
    for (int row = 0; row < Math.min(2, rows) && idx < arr.length - 2; row++) {
        for (int col = 0; col < Math.min(2, cols) && idx < arr.length - 2; col++) {
            String label = labelNames[row][col];
            double voltage = getVoltageForLabel(label);
            arr[idx++] = label + ": " + getVoltageText(voltage);
        }
    }
    
    // Show integration results for first few columns
    for (int col = 0; col < Math.min(2, cols) && idx < arr.length - 1; col++) {
        String integrationLabel = columnHeaders[col] + "_integrated";
        Double integratedValue = LabeledNodeElm.getComputedValue(integrationLabel);
        if (integratedValue != null) {
            arr[idx++] = integrationLabel + ": " + getVoltageText(integratedValue.doubleValue());
        }
    }
    
    if (rows * cols > 4) {
        arr[idx++] = "... (showing first few cells)";
    }
}
```

### 8. Reset and Simulation Control

```java
@Override
public void reset() {
    super.reset();
    resetIntegration();
}

@Override  
public void stepFinished() {
    // Update integration states after each simulation step
    // This is called by the simulator after each timestep
    for (int col = 0; col < cols; col++) {
        if (integrationStates[col] != null) {
            integrationStates[col].updateLastValues(lastIntegrationOutputs[col]);
        }
    }
}
```

### 9. Static Helper Methods

```java
// Static method to get integration results by other elements
public static Double getIntegratedValue(String columnHeader) {
    return LabeledNodeElm.getComputedValue(columnHeader + "_integrated");
}

// Static method to reset specific column integration
public static void resetColumnIntegration(String columnHeader) {
    LabeledNodeElm.setComputedValue(columnHeader + "_integrated", 0.0);
}
```

## Key Features Summary

### **Integration Functionality**
- **Equation**: `lastoutput + timestep * integrationGain * columnSum`
- **Variables**: 
  - `lastoutput`: Previous integration result for this column
  - `timestep`: Current simulation timestep (`sim.timeStep`)
  - `integrationGain`: User-configurable multiplier (default 100)
  - `columnSum`: Sum of all values in the column

### **Expression System Integration**
- Uses CircuitJS1's `Expr` and `ExprState` classes (same as `VCVSElm`)
- Integration equation parsed once, evaluated each timestep
- Proper state management with `updateLastValues()`
- Error handling for expression parsing and evaluation

### **Computed Values Extension**
- Column sums stored as `columnHeader` (inherited from TableElm)
- Integration results stored as `columnHeader_integrated`
- Both accessible by other circuit elements via `LabeledNodeElm.getComputedValue()`

### **User Interface**
- Integration gain parameter (editable)
- Reset integration button (checkbox)
- Visual distinction (yellow background for integration cells)
- Info display shows equation and current values

### **Persistence**
- Integration gain saved/loaded
- Integration states (last outputs) preserved in circuit files
- Backward compatibility with TableElm format

## Usage Examples

### **Basic Integration**
```
Column A contains voltages: 1V, 2V, 3V (sum = 6V)
Integration: 0 + 0.001 * 100 * 6 = 0.6
Next step: 0.6 + 0.001 * 100 * 6 = 1.2
Result accumulates over time...
```

### **Referencing Integration Results**
```java
// Other elements can access integration results
Double integratedA = GodlyTableElm.getIntegratedValue("Col1");
Double integratedB = LabeledNodeElm.getComputedValue("Col2_integrated");
```

### **Reset Integration**
- Use "Reset Integration" checkbox in edit dialog
- Or call `resetIntegration()` method programmatically
- Clears all `lastoutput` values to 0

This implementation creates a powerful table element that can perform real-time integration of column data, making it useful for accumulating values over time in circuit simulations, such as charge accumulation, energy calculations, or other time-dependent quantities.