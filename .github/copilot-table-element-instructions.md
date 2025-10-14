# Simplified Table Element Implementation Instructions

## Overview

Create a simplified Table Element that extends `CircuitElm` and displays voltages for labeled nodes by referencing text label names. This element reads voltage values using `LabeledNodeElm.labelList.get(labelName)` instead of creating actual circuit connections.

**Updated Implementation Features:**
- **Computed Values Support**: Can store and retrieve calculated values (like column sums) via `ComputedValues.getComputedValue()` / `setComputedValue()` 
- **Column Sums**: Optional bottom row showing calculated sums for each column
- **Enhanced UI**: Checkbox control for toggling column sum display
- **Robust Serialization**: Includes all new features in save/load format
- **Visual Enhancements**: Improved grid lines, color coding, and layout

## Refactored Implementation Plan: Simplified Table Element

### Key Design Changes:
- **Extend `CircuitElm`** instead of `CompositeElm` for simplicity
- **Text-only labels**: Store array of label names as strings
- **Voltage lookup**: Use `LabeledNodeElm.labelList.get(labelName)` to get voltages
- **No electrical connections**: Pure display element with no posts
- **Lightweight**: No sub-elements or complex composite structure

### 1. Class Structure Design
```java
package com.lushprojects.circuitjs1.client;

import java.util.StringTokenizer;

/**
 * Simplified Table Element - Displays voltage values from labeled nodes
 * Extends CircuitElm for lightweight text-based label display
 */
public class TableElm extends CircuitElm {
    private int rows = 3;
    private int cols = 2; 
    private int cellSize = 60;
    private int cellSpacing = 4;
    private String[][] labelNames;  // Store label names as text
    private String[] columnHeaders;
    private boolean showColumnSums = true; // Show sum row at bottom
    
    // Computed values are now stored in LabeledNodeElm.labelList - no JavaScript needed
    
    // Constructor for new table
    public TableElm(int xx, int yy) {
        super(xx, yy);
        initTable();
    }
    
    // File loading constructor  
    public TableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        parseTableData(st);
        initTable();
    }
}
```

### 2. Table Initialization
```java
private void initTable() {
    // Initialize label names array
    if (labelNames == null) {
        labelNames = new String[rows][cols];
        // Set default label names
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                labelNames[row][col] = "node" + (row * cols + col + 1);
            }
        }
    }
    
    // Initialize column headers if not set
    if (columnHeaders == null) {
        columnHeaders = new String[cols];
        for (int i = 0; i < cols; i++) {
            columnHeaders[i] = "Col" + (i + 1);
        }
    }
}
```

### 3. Voltage Lookup Method
```java
private double getVoltageForLabel(String labelName) {
    if (labelName == null || labelName.isEmpty()) {
        return 0.0;
    }
    
    // First check if this is a computed value (like a column sum)
    Double computedValue = ComputedValues.getComputedValue(labelName);
    if (computedValue != null) {
        return computedValue.doubleValue();
    }
    
    // Use LabeledNodeElm's static labelList to get voltage
    Integer nodeNum = LabeledNodeElm.getByName(labelName);
    if (nodeNum == null) {
        return 0.0; // Label not found
    }
    
    // Node 0 is ground
    if (nodeNum == 0) {
        return 0.0;
    }
    
    // Get voltage from simulation
    if (sim != null && sim.nodeVoltages != null && 
        nodeNum > 0 && nodeNum <= sim.nodeVoltages.length) {
        return sim.nodeVoltages[nodeNum - 1]; // nodeVoltages is 0-indexed, excludes ground
    }
    
    return 0.0;
}

private void registerSumAsLabeledNode(String labelName, double voltage) {
    if (labelName == null || labelName.isEmpty()) {
        return;
    }
    
    // Store the computed voltage value in ComputedValues
    ComputedValues.setComputedValue(labelName, voltage);
}

// Static method to get computed values by other elements
public static Double getComputedValue(String labelName) {
    return ComputedValues.getComputedValue(labelName);
}
```

### 4. Layout and Positioning
```java
void setPoints() {
    super.setPoints();
    
    // Calculate table dimensions
    int tableWidth = cols * cellSize + (cols + 1) * cellSpacing;
    int extraRows = showColumnSums ? 1 : 0; // Add extra row for sums
    int tableHeight = (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20; // Extra space for headers
    
    // Set bounding box
    setBbox(point1.x, point1.y, point1.x + tableWidth, point1.y + tableHeight);
}

int getPostCount() { 
    return 0; // No electrical connections
}
```

### 5. Drawing Implementation
```java
void draw(Graphics g) {
    int extraRows = showColumnSums ? 1 : 0;
    
    // Clear previous computed values to ensure fresh calculations
    if (showColumnSums) {
        LabeledNodeElm.clearComputedValues();
    }
    
    // Draw table background
    g.setColor(needsHighlight() ? selectColor : Color.white);
    g.fillRect(point1.x, point1.y, 
               cols * cellSize + (cols + 1) * cellSpacing,
               (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20);
    
    // Draw table border
    g.setColor(Color.black);
    g.drawRect(point1.x, point1.y,
               cols * cellSize + (cols + 1) * cellSpacing,
               (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20);
    
    // Draw column headers
    drawColumnHeaders(g);
    
    // Draw table cells with voltages
    drawTableCells(g);
    
    // Draw sum row if enabled
    if (showColumnSums) {
        drawSumRow(g);
    }
    
    // Draw grid lines
    drawGridLines(g);
}

private void drawColumnHeaders(Graphics g) {
    g.setColor(Color.black);
    int headerY = point1.y + 15;
    
    for (int col = 0; col < cols; col++) {
        int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
        String header = (columnHeaders != null && col < columnHeaders.length) ? 
                       columnHeaders[col] : "Col" + (col + 1);
        drawCenteredText(g, header, cellX + cellSize/2, headerY, true);
    }
}

private void drawTableCells(Graphics g) {
    for (int row = 0; row < rows; row++) {
        for (int col = 0; col < cols; col++) {
            int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
            int cellY = point1.y + 20 + cellSpacing + row * (cellSize + cellSpacing);
            
            // Get label name and voltage
            String labelName = labelNames[row][col];
            double voltage = getVoltageForLabel(labelName);
            
            // Draw cell background based on voltage (optional)
            setVoltageColor(g, voltage);
            g.fillRect(cellX, cellY, cellSize, cellSize);
            
                // Draw cell border
                g.setColor(Color.black);
                g.drawRect(cellX, cellY, cellSize, cellSize);
                
                // Draw label name (top half)
                g.setColor(Color.black);
                drawCenteredText(g, labelName, cellX + cellSize/2, cellY + cellSize/3, true);
                
                // Draw voltage value (bottom half)
                String voltageText = getVoltageText(voltage);
                drawCenteredText(g, voltageText, cellX + cellSize/2, cellY + 2*cellSize/3, true);
            }
        }
    }

private void drawSumRow(Graphics g) {
    int sumRowY = point1.y + 20 + cellSpacing + rows * (cellSize + cellSpacing);
    
    for (int col = 0; col < cols; col++) {
        int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
        
        // Calculate sum for this column
        double columnSum = 0.0;
        for (int row = 0; row < rows; row++) {
            String labelName = labelNames[row][col];
            columnSum += getVoltageForLabel(labelName);
        }
        
        // Register this sum as a labeled node using column header as the label name
        String sumLabelName = columnHeaders[col];
        registerSumAsLabeledNode(sumLabelName, columnSum);
        
        // Draw sum cell background (slightly different color)
        g.setColor(Color.lightGray);
        g.fillRect(cellX, sumRowY, cellSize, cellSize);
        
        // Draw cell border
        g.setColor(Color.black);
        g.drawRect(cellX, sumRowY, cellSize, cellSize);
        
        // Draw column header text as label (top half)
        g.setColor(Color.black);
        drawCenteredText(g, sumLabelName, cellX + cellSize/2, sumRowY + cellSize/3, true);
        
        // Draw sum value (bottom half)
        String sumText = getVoltageText(columnSum);
        drawCenteredText(g, sumText, cellX + cellSize/2, sumRowY + 2*cellSize/3, true);
    }
}

private void drawGridLines(Graphics g) {
    g.setColor(Color.gray);
    int extraRows = showColumnSums ? 1 : 0;    // Vertical lines
    for (int col = 0; col <= cols; col++) {
        int x = point1.x + cellSpacing + col * (cellSize + cellSpacing);
        g.drawLine(x, point1.y + 20, x, point1.y + 20 + cellSpacing + (rows + extraRows) * (cellSize + cellSpacing));
    }
    
    // Horizontal lines
    for (int row = 0; row <= rows + extraRows; row++) {
        int y = point1.y + 20 + cellSpacing + row * (cellSize + cellSpacing);
        g.drawLine(point1.x, y, point1.x + cellSpacing + cols * (cellSize + cellSpacing), y);
    }
    
    // Draw separator line before sum row if showing sums
    if (showColumnSums) {
        g.setColor(Color.black);
        int separatorY = point1.y + 20 + cellSpacing + rows * (cellSize + cellSpacing);
        g.drawLine(point1.x, separatorY, point1.x + cellSpacing + cols * (cellSize + cellSpacing), separatorY);
    }
}
```

### 6. No Connection Logic Needed
```java
// No electrical connections - pure display element
boolean isWireEquivalent() { return false; }
boolean isRemovableWire() { return false; }
### 7. Serialization Support
```java
public String dump() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.dump()).append(" ").append(rows).append(" ").append(cols);
    sb.append(" ").append(showColumnSums ? "1" : "0");
    
    // Serialize label names
    for (int row = 0; row < rows; row++) {
        for (int col = 0; col < cols; col++) {
            sb.append(" ").append(CustomLogicModel.escape(labelNames[row][col]));
        }
    }
    
    // Serialize column headers
    for (int col = 0; col < cols; col++) {
        sb.append(" ").append(CustomLogicModel.escape(columnHeaders[col]));
    }
    
    return sb.toString();
}

private void parseTableData(StringTokenizer st) {
    try {
        if (st.hasMoreTokens()) rows = Integer.parseInt(st.nextToken());
        if (st.hasMoreTokens()) cols = Integer.parseInt(st.nextToken());
        if (st.hasMoreTokens()) showColumnSums = "1".equals(st.nextToken());
        
        // Parse label names
        labelNames = new String[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (st.hasMoreTokens()) {
                    labelNames[row][col] = CustomLogicModel.unescape(st.nextToken());
                } else {
                    labelNames[row][col] = "node" + (row * cols + col + 1);
                }
            }
        }
        
        // Parse column headers
        columnHeaders = new String[cols];
        for (int col = 0; col < cols; col++) {
            if (st.hasMoreTokens()) {
                columnHeaders[col] = CustomLogicModel.unescape(st.nextToken());
            } else {
                columnHeaders[col] = "Col" + (col + 1);
            }
        }
    } catch (Exception e) {
        CirSim.console("TableElm: error parsing table data, using defaults");
        initTable(); // Reset to defaults
    }
}

int getDumpType() { 
    return 253; // Choose unused dump type
}
```

### 8. User Interface Integration
```java
public EditInfo getEditInfo(int n) {
    if (n == 0) return new EditInfo("Rows", rows, 1, 20);
    if (n == 1) return new EditInfo("Columns", cols, 1, 10);
    if (n == 2) return new EditInfo("Cell Size", cellSize, 20, 100);
    if (n == 3) return new EditInfo("Cell Spacing", cellSpacing, 2, 20);
    if (n == 4) {
        EditInfo ei = new EditInfo("", 0, -1, -1);
        ei.checkbox = new Checkbox("Show Column Sums", showColumnSums);
        return ei;
    }
    
    // Edit individual cell labels
    int cellIndex = n - 5;
    if (cellIndex >= 0 && cellIndex < rows * cols) {
        int row = cellIndex / cols;
        int col = cellIndex % cols;
        EditInfo ei = new EditInfo("Cell [" + row + "," + col + "] Label", 0, -1, -1);
        ei.text = labelNames[row][col];
        return ei;
    }
    
    // Edit column headers
    int headerIndex = cellIndex - rows * cols;
    if (headerIndex >= 0 && headerIndex < cols) {
        EditInfo ei = new EditInfo("Column " + headerIndex + " Header", 0, -1, -1);
        ei.text = columnHeaders[headerIndex];
        return ei;
    }
    
    return null;
}

public void setEditValue(int n, EditInfo ei) {
    if (n == 0 && ei.value != rows) {
        rows = (int)ei.value;
        resizeTable();
    } else if (n == 1 && ei.value != cols) {
        cols = (int)ei.value;
        resizeTable();
    } else if (n == 2) {
        cellSize = (int)ei.value;
        setPoints();
    } else if (n == 3) {
        cellSpacing = (int)ei.value;
        setPoints();
    } else if (n == 4) {
        showColumnSums = ei.checkbox.getState();
        setPoints();
    } else {
        // Edit cell labels or column headers
        int cellIndex = n - 5;
        if (cellIndex >= 0 && cellIndex < rows * cols) {
            int row = cellIndex / cols;
            int col = cellIndex % cols;
            labelNames[row][col] = ei.textf.getText();
        } else {
            int headerIndex = cellIndex - rows * cols;
            if (headerIndex >= 0 && headerIndex < cols) {
                columnHeaders[headerIndex] = ei.textf.getText();
            }
        }
    }
}

private void resizeTable() {
    String[][] oldLabels = labelNames;
    String[] oldHeaders = columnHeaders;
    
    initTable(); // Create new arrays with default values
    
    // Copy over existing labels where possible
    if (oldLabels != null) {
        for (int row = 0; row < Math.min(rows, oldLabels.length); row++) {
            for (int col = 0; col < Math.min(cols, oldLabels[row].length); col++) {
                labelNames[row][col] = oldLabels[row][col];
            }
        }
    }
    
    // Copy over existing headers where possible
    if (oldHeaders != null) {
        for (int col = 0; col < Math.min(cols, oldHeaders.length); col++) {
            columnHeaders[col] = oldHeaders[col];
        }
    }
    
    setPoints();
}
```

### 9. Information Display
```java
void getInfo(String arr[]) {
    arr[0] = "Voltage Table (" + rows + "x" + cols + ")";
    arr[1] = "Displays voltages from labeled nodes";
    
    int idx = 2;
    // Show some sample values
    for (int row = 0; row < Math.min(3, rows) && idx < arr.length - 1; row++) {
        for (int col = 0; col < Math.min(2, cols) && idx < arr.length - 1; col++) {
            String label = labelNames[row][col];
            double voltage = getVoltageForLabel(label);
            arr[idx++] = label + ": " + getVoltageText(voltage);
        }
    }
    
    if (rows * cols > 6) {
        arr[idx++] = "... (showing first few cells)";
    }
}
```

### 10. Table-Specific Helper Methods
```java
public void setCellLabel(int row, int col, String label) {
    if (row >= 0 && row < rows && col >= 0 && col < cols) {
        labelNames[row][col] = label;
    }
}

public String getCellLabel(int row, int col) {
    if (row >= 0 && row < rows && col >= 0 && col < cols) {
        return labelNames[row][col];
    }
    return "";
}

public void setColumnHeader(int col, String header) {
    if (col >= 0 && col < cols) {
        columnHeaders[col] = header;
    }
}

public String getColumnHeader(int col) {
    if (col >= 0 && col < cols) {
        return columnHeaders[col];
    }
    return "";
}

// Helper methods inherited from CircuitElm or available via simulation
// Note: drawCenteredText signature updated to include boolean parameter
private void drawCenteredText(Graphics g, String text, int x, int y, boolean realtime) {
    // This method is inherited from CircuitElm base class
    // The boolean parameter is used for font rendering optimizations
}

private void setVoltageColor(Graphics g, double voltage) {
    // This method is inherited from CircuitElm base class
    // Sets background color based on voltage value for visual indication
}

private String getVoltageText(double voltage) {
    // This method is inherited from CircuitElm base class  
    // Formats voltage values for display (handles units, precision, etc.)
}
```

## Key Advantages of Simplified Design

### 1. **Simplicity**
- Extends `CircuitElm` instead of complex `CompositeElm`
- No sub-elements to manage
- Direct voltage lookup using existing `LabeledNodeElm.labelList`

### 2. **Performance**
- Lightweight rendering
- No electrical simulation overhead
- Pure display element

### 3. **Flexibility** 
- Easy to edit label names in UI
- Configurable table dimensions
- Customizable column headers
- Visual voltage indication via colors
- Optional column sum calculations
- **NEW**: Computed value support for storing calculated results

### 4. **Integration**
- Leverages existing `LabeledNodeElm` infrastructure
- Uses standard CircuitJS1 patterns
- Compatible with save/load system
- **NEW**: Column sums automatically registered as labeled nodes for use by other elements

### 5. **Maintainability**
- Simple codebase
- Clear separation of concerns
- Easy to extend with new features

## New Features in Current Implementation

### **Column Sum Calculations**
- Optional bottom row showing sum of each column
- Controlled by `showColumnSums` boolean flag
- Sum values are automatically registered as computed values using column headers as label names
- Other circuit elements can reference these computed sums via `TableElm.getComputedValue(labelName)`

### **Computed Values Integration**  
- Supports both real node voltages and computed/calculated values
- Uses `ComputedValues.getComputedValue()` and `ComputedValues.setComputedValue()` methods
- Computed values managed by separate ComputedValues class for better separation of concerns
- Enables complex calculations and referencing between table elements

### **Enhanced Drawing**
- Improved grid lines with separator before sum row
- Visual distinction for sum cells (light gray background)
- Updated `drawCenteredText()` with boolean parameter for rendering optimization
- Color-coded voltage visualization via inherited `setVoltageColor()` method

### **Robust Serialization**
- Includes `showColumnSums` flag in circuit file format
- Proper escaping/unescaping of label names and headers via `CustomLogicModel`
- Error handling with fallback to defaults on parse failure

### **User Interface Enhancements**  
- Checkbox for toggling column sums in edit dialog
- Proper indexing adjustment for cell/header editing after adding checkbox
- Dynamic table resizing preserves existing labels where possible

## Suggested Class Extension

**Recommended**: Extend `CircuitElm` as shown above.

**Alternative Options**:
- `TextElm` - If you want text-editing capabilities
- `OutputElm` - If you want scope/probing features  
- `CircuitElm` - Best choice for custom display elements

The `CircuitElm` base class provides the essential framework for positioning, selection, serialization, and UI integration while keeping the implementation straightforward.

## Computed Values API Usage

### **Storing Computed Values**
```java
// Store a computed value (like a column sum) with ComputedValues
ComputedValues.setComputedValue("columnA_sum", 15.7);

// Register via TableElm helper method  
registerSumAsLabeledNode("columnA_sum", columnSum);
```

### **Retrieving Computed Values**
```java  
// From within TableElm
Double value = ComputedValues.getComputedValue("columnA_sum");

// From other circuit elements
Double value = TableElm.getComputedValue("columnA_sum");

// In voltage lookup (automatically checks computed values first)
double voltage = getVoltageForLabel("columnA_sum"); // Checks computed values before node voltages
```

### **Clearing Computed Values**
```java
// Called at start of each redraw to ensure fresh calculations
LabeledNodeElm.clearComputedValues();
```

This system allows TableElm instances to perform calculations (like column sums) and make those results available to other circuit elements as if they were labeled node voltages, enabling complex multi-element calculations and dependencies.