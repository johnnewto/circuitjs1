# TableElm Hidden Pins Feature

## Overview

The TableElm component has been modified to hide all visual pin and terminal elements while maintaining full electrical functionality. This provides a cleaner visual appearance for the table component while preserving its ability to output voltages and connect to other circuit elements.

## Implementation Details

### Files Modified

1. **TableRenderer.java** - Drawing logic
2. **TableElm.java** - Post drawing override
3. **CirSim.java** - Global post list management

### Changes Made

#### 1. TableRenderer.java - `drawPins()` method

**Location:** Line ~384

**Change:** Modified the `drawPins()` method to skip visual rendering while maintaining simulation state.

```java
private void drawPins(Graphics g) {
    // HIDDEN: Pins and posts are not drawn visually, but electrical connections remain functional
    // Update current counts for proper circuit simulation even though not displayed
    for (int i = 0; i < table.getPostCount(); i++) {
        ChipElm.Pin p = table.pins[i];
        p.curcount = table.updateDotCount(p.current, p.curcount);
    }
    // Don't draw pin lines, current dots, or posts - keep visual elements hidden
}
```

**What was removed:**
- `setVoltageColor()` - Color coding for voltage levels
- `drawThickLine()` - Pin connection lines from table to posts
- `drawDots()` - Current flow animation dots
- `drawPosts()` - Connection dots at terminal points

**What was kept:**
- Current count updates via `updateDotCount()` - Required for proper circuit simulation

#### 2. TableElm.java - `drawPosts()` override

**Location:** After `draw()` method (~line 440)

**Change:** Added empty override to prevent post drawing when element is selected.

```java
@Override
void drawPosts(Graphics g) {
    // Override to hide posts - electrical connections remain functional
    // but visual elements (connection dots) are not drawn
}
```

**Purpose:** The base `CircuitElm.drawPosts()` method draws connection dots when an element is selected or being created. This override prevents that behavior for TableElm.

#### 3. CirSim.java - `makePostDrawList()` method

**Location:** Line ~2723

**Change:** Added instanceof check to skip TableElm posts when building the global post draw list.

```java
void makePostDrawList() {
    HashMap<Point,Integer> postCountMap = new HashMap<Point,Integer>();
    int i, j;
    for (i = 0; i != elmList.size(); i++) {
        CircuitElm ce = getElm(i);
        // Skip TableElm posts - they are hidden but remain electrically functional
        if (ce instanceof TableElm)
            continue;
        int posts = ce.getPostCount();
        for (j = 0; j != posts; j++) {
            Point pt = ce.getPost(j);
            Integer g = postCountMap.get(pt);
            postCountMap.put(pt, g == null ? 1 : g+1);
        }
    }
    // ... rest of method
}
```

**Purpose:** The circuit simulator maintains a global list of posts to draw. Posts shared by exactly 2 elements are hidden (assumed to be proper connections), while posts with 1 or 3+ connections are shown. By excluding TableElm posts from this list entirely, we ensure they're never drawn regardless of connection status.

## Visual and Functional Impact

### What's Hidden

✅ **Pin connection lines** - No lines extending from table bottom to connection points
✅ **Connection dots (posts)** - No white circular dots at pin locations  
✅ **Current flow animation** - No moving dots showing current direction
✅ **Voltage color coding** - No color changes on pin lines based on voltage

### What's Preserved

✅ **Electrical functionality** - Voltage sources continue to output column sum values
✅ **Circuit connections** - Wires can still connect to pin locations (though invisible)
✅ **Node creation** - Each column still creates a circuit node
✅ **Simulation accuracy** - Current counts updated for proper circuit analysis

## Architecture Notes

### Why Three Changes Were Needed

1. **TableRenderer.drawPins()** - Prevents drawing during normal rendering loop
2. **TableElm.drawPosts()** - Prevents drawing when element is selected/highlighted
3. **CirSim.makePostDrawList()** - Prevents global post drawing system from rendering terminals

### Alternative Approaches Considered

1. **Internal nodes only** - Would require restructuring voltage output mechanism
2. **Return null from getPost()** - Would break connectivity and wire attachment
3. **Override getPostCount() to return 0** - Would break circuit matrix stamping
4. **Extend GraphicElm** - Would lose electrical functionality entirely

The chosen approach (hiding visual elements) was selected because it:
- Maintains full electrical functionality
- Requires minimal code changes
- Doesn't break existing circuit files
- Allows easy reversal if needed

## Connection Behavior

### Connecting Wires to Hidden Pins

- Wires can still be connected to the (invisible) pin locations
- Connection points exist at column centers on table bottom edge
- Grid snapping helps locate connection points
- No visual feedback when hovering over connection points
- Successful connections work normally despite invisible terminals

### Finding Pin Locations

Pin positions are calculated in `TableGeometryManager.calculatePinX()`:
- Each pin is centered horizontally under its column
- Pins are positioned at `SIDE_S` (bottom) of the chip rectangle
- Spacing matches column widths with proper offset for row description column

## Future Enhancements

Possible improvements to this feature:

1. **Add "Show Pins" option** - Toggle in edit dialog or right-click menu
2. **Hover indication** - Show connection points temporarily when hovering
3. **Selection feedback** - Brief visual indicator when wire connects successfully
4. **Debug mode** - Developer option to show hidden pins for troubleshooting

## Testing

To verify the feature works correctly:

1. **Create a TableElm** - Should appear without any pins or dots
2. **Connect a wire** - Wire should attach (may require finding position by trial)
3. **Verify voltage output** - Connected component should receive correct voltage
4. **Select the table** - No connection dots should appear even when selected
5. **Save and reload** - Hidden pins should remain hidden after circuit reload

## Compatibility

- ✅ Existing circuit files with TableElm load correctly
- ✅ No changes to dump/load format required
- ✅ Circuit simulation results unchanged
- ✅ Works with all circuit elements that connect to table outputs
- ✅ Compatible with subcircuits containing tables

## Related Code

- `ChipElm.java` - Base class that normally draws pins
- `CircuitElm.drawPost()` - Static method that draws connection dots
- `CirSim.postDrawList` - Global list of posts to render
- `TableGeometryManager.setupPins()` - Pin position calculation
- `GraphicElm.java` - Example of element type excluded from post drawing

## Documentation Date

Last Updated: October 29, 2025