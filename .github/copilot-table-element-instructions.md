# Table Circuit Element Creation Instructions

## Overview

This document provides specific instructions for creating a **Table Circuit Element** - a single-column table displaying named nodes with functionality similar to `LabeledNodeElm.java`. The element should extend `ChipElm.java` for layout structure but modify the post positioning to place connections on the right-hand side of the table.

## Reference Documentation

Before starting, review the main project instructions in `.github/copilot-instructions.md`, particularly:
- Adding New Circuit Elements section
- Circuit Simulation Theory
- Program Loop Architecture
- GWT-Specific Considerations

## Table Element Specifications

### Visual Design
- **Shape**: Single-column rectangular table
- **Rows**: Variable number of rows (configurable, default 4-8)
- **Labels**: Text labels in each row (left side)
- **Posts**: Connection points on the right-hand side of each row
- **Styling**: Similar to ChipElm but optimized for table layout

### Functional Requirements
- Each row represents a named node (like LabeledNodeElm)
- Node names should be editable via EditInfo interface
- Support for connecting multiple elements with same node name
- Maintain node voltage consistency across same-named nodes
- Support save/load functionality with node names

## Implementation Guide

### 1. Class Structure (Recommended Approach)

```java
class TableElm extends ChipElm {
    // Inherit from ChipElm for layout infrastructure
    // Contains array of virtual LabeledNodeElm instances for each row
    // Each row acts as a full LabeledNodeElm with its own connectivity
}
```

**Alternative Approach**: Consider creating a composite element that manages multiple LabeledNodeElm instances internally, leveraging the existing node connectivity system rather than reimplementing it.

### Key Design Insight
Instead of reimplementing LabeledNodeElm's node mapping functionality, we should:
1. **Leverage existing LabeledNodeElm.labelList** - Use the static HashMap that already manages node connections
2. **Reuse LabeledNodeElm connectivity** - Each table row should behave exactly like a LabeledNodeElm
3. **Extend ChipElm for layout** - Use ChipElm's pin management and drawing infrastructure

### 2. Key Modifications from ChipElm

#### Post Positioning Override
In `drawChip(Graphics g)` method:
- **Standard ChipElm**: Posts distributed around rectangle perimeter
- **Table Element**: All posts on RIGHT side of rectangle
- Posts and stubs may overlap with table edge for compact design

#### Table-Specific Layout
```java
void setupPins() {
    // Create pins array with all pins on SIDE_E (right side)
    // Position pins vertically spaced for table rows
    pins = new Pin[getRowCount()];
    for (int i = 0; i < getRowCount(); i++) {
        pins[i] = new Pin(i, SIDE_E, getNodeName(i));
        pins[i].output = false; // These are connection points, not outputs
    }
}
```

### 3. Node Name Management (Revised Approach)

#### Leverage LabeledNodeElm Infrastructure
```java
// Store node names array
String[] nodeNames;

// Instead of creating our own mapping, use LabeledNodeElm's static labelList
// Each table row interfaces with LabeledNodeElm.labelList HashMap

// For each node name, we simulate a LabeledNodeElm connection:
Point getConnectedPostForRow(int rowIndex) {
    String nodeName = nodeNames[rowIndex];
    LabeledNodeElm.LabelEntry le = LabeledNodeElm.labelList.get(nodeName);
    
    if (le != null) {
        return le.point;
    }
    
    // Register this table row's post as a new labeled node
    le = new LabeledNodeElm.LabelEntry();
    le.point = pins[rowIndex].post;
    LabeledNodeElm.labelList.put(nodeName, le);
    return null;
}
```

#### Benefits of This Approach
- **Consistency**: Same node names in TableElm and LabeledNodeElm connect automatically
- **Compatibility**: Works with existing circuits using LabeledNodeElm
- **Maintainability**: No duplicate node management code
- **Reliability**: Leverages tested LabeledNodeElm connectivity logic

#### Node Name Interface
```java
public EditInfo getChipEditInfo(int n) {
    if (n < nodeNames.length) {
        return new EditInfo("Row " + (n+1) + " Name", nodeNames[n]);
    }
    if (n == nodeNames.length) {
        return new EditInfo("Row Count", getRowCount(), 1, 20);
    }
    return null;
}

public EditInfo void setChipEditValue(int n, EditInfo ei) {
    if (n < nodeNames.length) {
        nodeNames[n] = ei.textf.getText();
        setupPins(); // Refresh pin labels
        setPoints(); // Recalculate layout
    }
    if (n == nodeNames.length) {
        setRowCount(ei.value);
    }
}
```

### 4. Drawing Override

#### Custom drawChip Implementation
```java
void drawChip(Graphics g) {
    // Call parent for basic setup but override post drawing
    
    // Draw table background
    g.setColor(sim.getBackgroundColor());
    g.fillRect(rectPointsX[0], rectPointsY[0], 
               rectPointsX[2] - rectPointsX[0], 
               rectPointsY[2] - rectPointsY[0]);
    
    // Draw table border
    g.setColor(needsHighlight() ? selectColor : lightGrayColor);
    drawThickPolygon(g, rectPointsX, rectPointsY, 4);
    
    // Draw row separators
    int rowHeight = (rectPointsY[2] - rectPointsY[0]) / getRowCount();
    for (int i = 1; i < getRowCount(); i++) {
        int y = rectPointsY[0] + i * rowHeight;
        g.drawLine(rectPointsX[0], y, rectPointsX[1], y);
    }
    
    // Draw node names and posts
    for (int i = 0; i < getPostCount(); i++) {
        Pin p = pins[i];
        
        // Draw post connection on RIGHT side (may overlap table edge)
        setVoltageColor(g, volts[i]);
        Point a = p.post;
        Point b = p.stub;
        drawThickLine(g, a, b);
        
        // Draw current dots
        p.curcount = updateDotCount(p.current, p.curcount);
        drawDots(g, b, a, p.curcount);
        
        // Draw node name label (LEFT side of table)
        g.setColor(whiteColor);
        int labelX = rectPointsX[0] + 5; // Small margin from left edge
        int labelY = rectPointsY[0] + (i + 0.5) * rowHeight;
        g.drawString(nodeNames[i], labelX, labelY);
    }
}
```

### 5. Sizing and Geometry

#### Table Dimensions
```java
void setPoints() {
    // Calculate table size based on row count and content
    sizeX = 3; // Fixed width for single column
    sizeY = Math.max(getRowCount(), 2); // Height based on rows
    
    // Call parent setPoints for basic geometry
    super.setPoints();
    
    // Override pin positions to be on right side only
    repositionPinsToRightSide();
}

void repositionPinsToRightSide() {
    // Move all pins to right edge, evenly spaced vertically
    int rowHeight = (rectPointsY[2] - rectPointsY[0]) / getRowCount();
    for (int i = 0; i < getPostCount(); i++) {
        Pin p = pins[i];
        int pinY = rectPointsY[0] + (i + 0.5) * rowHeight;
        
        // Post is ON the right edge or slightly outside
        p.post = new Point(rectPointsX[2], pinY);
        p.stub = new Point(rectPointsX[2] - cspc/2, pinY);
        p.textloc = new Point(rectPointsX[0] + 5, pinY);
    }
}
```

### 6. Node Connectivity (Core Functionality - Revised)

#### Interface with LabeledNodeElm System
```java
// Override calcWireClosure participation (called by CirSim during analysis)
Point getConnectedPost() {
    // This method should be called for each row individually during wire closure
    // We need to modify this to work with multiple posts
    return null; // TableElm doesn't use single-post connectivity
}

// Implement multi-post connectivity by integrating with LabeledNodeElm.labelList
void handleNodeConnectivity() {
    for (int i = 0; i < getPostCount(); i++) {
        String nodeName = nodeNames[i];
        LabeledNodeElm.LabelEntry le = LabeledNodeElm.labelList.get(nodeName);
        
        if (le != null) {
            // Existing labeled node - we should connect to it
            // This logic should be handled during wire closure calculation
        } else {
            // First occurrence - register this post
            le = new LabeledNodeElm.LabelEntry();
            le.point = pins[i].post;
            LabeledNodeElm.labelList.put(nodeName, le);
        }
    }
}

void setNode(int p, int n) {
    super.setNode(p, n);
    
    // Update the LabeledNodeElm system with our node assignments
    if (p < nodeNames.length) {
        String nodeName = nodeNames[p];
        LabeledNodeElm.LabelEntry le = LabeledNodeElm.labelList.get(nodeName);
        if (le != null) {
            le.node = n;
        }
    }
}

// Support LabeledNodeElm.getByName() functionality
// This allows other code to find node numbers by name, including table nodes
boolean isWireEquivalent() { 
    return true; // Table acts like multiple labeled wires
}
```

### 7. Required CircuitElm Overrides

```java
int getDumpType() { return 253; } // Choose unused number 200-300 range

int getPostCount() { return getRowCount(); }

boolean isDigitalChip() { return false; } // This is analog node connections

String dump() {
    String result = super.dump();
    result += " " + getRowCount();
    for (String name : nodeNames) {
        result += " " + CustomLogicModel.escape(name);
    }
    return result;
}

// Constructor for loading from file
public TableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
    super(xa, ya, xb, yb, f, st);
    
    int rowCount = Integer.parseInt(st.nextToken());
    nodeNames = new String[rowCount];
    
    for (int i = 0; i < rowCount; i++) {
        nodeNames[i] = st.hasMoreTokens() ? 
            CustomLogicModel.unescape(st.nextToken()) : "node" + i;
    }
    
    setupPins();
}
```

### 8. Integration Steps

#### Add to CirSim.java
```java
// In createCe() method:
case 253: return new TableElm(xa, ya, xb, yb, f, st);

// In constructElement() method:
if (n.compareTo("TableElm") == 0) return new TableElm(xa, ya, xb, yb, f, st);

// In composeMainMenu() method:
// Add under appropriate menu section:
m.add(getMenuItem("Add Table", "add table"));
```

#### Menu Handler
```java
// In menuPerformed() method:
if (ac.compareTo("add table") == 0) {
    mouseModeStr = "Add Table";
    setMouseMode(MODE_ADD_ELM);
    addingClass = TableElm.class;
    return;
}
```

### 9. Testing Checklist

1. **Basic Functionality**
   - Element appears in menu and can be placed
   - Table draws correctly with configurable rows
   - Posts appear on right side of table

2. **Node Connectivity**
   - Same-named nodes in different table elements connect properly
   - Voltage consistency across same-named nodes
   - Current flow visualization works correctly

3. **User Interface**
   - Node names can be edited via right-click
   - Row count can be modified
   - Changes update display immediately

4. **Save/Load**
   - Circuits with table elements save correctly
   - Loading preserves all node names and connections
   - Export/import maintains functionality

5. **Integration**
   - Works in subcircuits
   - Compatible with white background mode
   - No conflicts with existing elements

## Alternative Implementation Approaches

### Option A: Composite LabeledNodeElm Approach
```java
class TableElm extends ChipElm {
    LabeledNodeElm[] nodeElements; // Array of actual LabeledNodeElm instances
    
    // Each row creates and manages a LabeledNodeElm internally
    // Benefits: Full LabeledNodeElm functionality per row
    // Drawbacks: More complex object management
}
```

### Option B: LabeledNodeElm Integration (Recommended)
```java
class TableElm extends ChipElm {
    String[] nodeNames;
    
    // Directly interface with LabeledNodeElm.labelList
    // Implement calcWireClosure() integration
    // Each table row participates in wire closure like a LabeledNodeElm
    
    // Benefits: Lightweight, leverages existing system
    // This is the approach detailed in the main instructions above
}
```

### Option C: Multiple Inheritance Simulation
```java
class TableElm extends ChipElm implements LabeledNodeInterface {
    // Create interface that captures LabeledNodeElm behavior
    // Implement the interface methods for each table row
    // Benefits: Clean separation of concerns
}
```

## Advanced Features (Optional)

### Multi-Column Support
- Extend to support multiple columns of nodes
- Add column headers with different node categories
- Support different connection behaviors per column

### LabeledNodeElm Compatibility Enhancements
- Support LabeledNodeElm's internal node flag per row
- Implement per-row edit interfaces matching LabeledNodeElm
- Add scope text functionality for debugging

### Visual Enhancements
- Color coding for different node types (matching LabeledNodeElm colors)
- Icons or symbols in table cells
- Resizable table dimensions
- Show connection status (connected/unconnected) per row

### Advanced Connectivity Features
- Bus connections (multiple bits with naming patterns)
- Hierarchical node naming (dot notation: "cpu.data.bit0")
- Node grouping and organization
- Import/export node lists from CSV or other formats

## Common Pitfalls

1. **Post Overlap**: Ensure posts don't interfere with table drawing
2. **Node Mapping Integration**: 
   - **DON'T** create separate node mapping system
   - **DO** integrate with LabeledNodeElm.labelList for consistency
   - Handle node name changes by updating the shared labelList
3. **Wire Closure Integration**: 
   - Ensure TableElm participates properly in CirSim.calcWireClosure()
   - Each table row should behave like a LabeledNodeElm during wire analysis
4. **Matrix Integration**: Table elements should not add extra matrix complexity
5. **LabeledNodeElm Compatibility**:
   - Ensure same node names connect between TableElm and LabeledNodeElm
   - Test mixed circuits with both element types
   - Maintain compatibility with LabeledNodeElm.getByName() functionality
6. **Performance**: Efficient redrawing for large tables
7. **GWT Compatibility**: Use only GWT-compatible Java features
8. **Static State Management**: 
   - Coordinate with LabeledNodeElm.resetNodeList() calls
   - Ensure proper cleanup when circuits are cleared

## Reference Code Locations

- **ChipElm.java**: Base class for layout and pin management
- **LabeledNodeElm.java**: Node naming and connectivity patterns
- **CirSim.java**: Element registration and menu integration
- **CircuitElm.java**: Base circuit element functionality