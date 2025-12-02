# Scope "Draw From Zero" Feature

## Overview

The "Draw From Zero" feature allows oscilloscope waveforms to be drawn from left-to-right starting at time t=0, with the y-axis always visible at the left edge. This is an alternative to the default right-to-left scrolling behavior where the most recent data appears on the right edge.

## Description

### Standard Scope Behavior
- Fixed-width time window scrolling right-to-left
- Newest data always on the right edge
- Time window relative to current simulation time: `[t - window_duration, t]`
- Uses circular buffer for efficient data storage

### Draw From Zero Behavior
- Waveforms start from t=0 on the left edge
- Plot grows left-to-right as simulation progresses
- Time axis shows absolute time from simulation start
- Automatically scales horizontally to fit entire simulation history
- Uses linear history buffers with automatic downsampling
- Resets simulation to t=0 when enabled

## Key Features

### Automatic Time Scaling
When enabled, the scope automatically adjusts the horizontal time scale to display the entire simulation history from t=0 to current time. This provides a complete view of the waveform evolution.

### History Buffer System
Unlike the standard circular buffer approach, Draw From Zero mode uses:
- **Linear history buffers** storing min/max pairs per sample
- **Automatic downsampling** by 2x when buffer capacity is reached
- **Preserves peak information** during downsampling (keeps min of mins, max of maxs)
- Initial capacity: 4× the display width
- Console logging when downsampling occurs

### Gridline Behavior
- Vertical gridlines fixed at t=0 and regular intervals
- Spacing automatically adjusts when too small (minimum 20 pixels)
- Uses standard scope time scale steps (2.0, 2.5, 2.0 multipliers)
- Gridlines stay stationary, providing clear time reference

### Connected Waveforms
Plots are drawn with connecting lines between consecutive points to avoid "dotted" appearance on steep slopes. Each pixel position draws:
1. Vertical line segment (captures signal range at that time)
2. Diagonal connecting line to previous point

### Cursor and Mouse Interaction
- Mouse hover shows cursor at correct time position
- Cursor reads values from history buffer
- Time displayed in fixed format (seconds with 2 decimal places)
- Cursor synchronized across multiple scopes

## Implementation Details

### Core Files Modified

**`Scope.java`** (~300 lines modified/added)
- New fields: `drawFromZero`, `autoScaleTime`, `startTime`, `historySize`, `historyCapacity`, `historySampleInterval`
- New flags: `FLAG_DRAW_FROM_ZERO` (1<<22), `FLAG_AUTO_SCALE_TIME` (1<<23)
- Modified methods: `drawPlot()`, `resetGraph()`, `selectScope()`, `drawCursor()`, `toggleDrawFromZero()`
- New methods: `captureToHistory()`, `downsampleHistory()`

**`ScopePlot.java`** (~5 lines)
- Added: `historyMinValues[]`, `historyMaxValues[]` arrays

**`ScopePopupMenu.java`** (~10 lines)
- Added: `drawFromZeroItem` checkbox menu item

**`CirSim.java`** (~5 lines)
- Added: Menu handler for "drawfromzero" command

### Data Storage

#### History Buffers (per plot)
```java
double historyMinValues[];  // Minimum values per sample
double historyMaxValues[];  // Maximum values per sample
int historySize;            // Current number of samples
int historyCapacity;        // Maximum buffer size
double historySampleInterval; // Time between samples (in simulation seconds)
```

#### Circular Buffer (standard mode)
```java
double minValues[];  // Circular buffer of min values
double maxValues[];  // Circular buffer of max values
int ptr;            // Current write position
```

### Key Algorithms

#### History Capture (`captureToHistory()`)
```java
// Sample at fixed intervals based on historySampleInterval
double timeSinceLastSample = sim.t - lastSampleTime;
if (timeSinceLastSample >= historySampleInterval) {
    // Store current min/max in history buffers
    historyMinValues[historySize] = currentMin;
    historyMaxValues[historySize] = currentMax;
    historySize++;
    
    // Downsample if buffer full
    if (historySize >= historyCapacity) {
        downsampleHistory();
    }
}
```

#### Downsampling (`downsampleHistory()`)
```java
// Reduce buffer size by 2x, preserving peaks
int newSize = historySize / 2;
historySampleInterval *= 2;

for (int j = 0; j < newSize; j++) {
    int src1 = j * 2;
    int src2 = j * 2 + 1;
    // Keep minimum of mins and maximum of maxs
    historyMinValues[j] = Math.min(historyMinValues[src1], historyMinValues[src2]);
    historyMaxValues[j] = Math.max(historyMaxValues[src1], historyMaxValues[src2]);
}
```

#### Drawing (`drawPlot()`)
```java
if (drawFromZero && !plot2d) {
    double elapsedTime = sim.t - startTime;
    double displayTimeSpan = elapsedTime; // Auto-scale enabled by default
    
    for (int i = 0; i < rect.width; i++) {
        // Map pixel to history index
        int histIdx = (i * historySize) / rect.width;
        
        // Get values from history
        double minValue = historyMinValues[histIdx];
        double maxValue = historyMaxValues[histIdx];
        
        // Draw vertical segment
        if (minValue != maxValue) {
            g.drawLine(x+i, maxy-minvy, x+i, maxy-maxvy);
        }
        
        // Connect to previous point
        if (prevMaxY != -1) {
            g.drawLine(x+i-1, maxy-prevMaxY, x+i, maxy-minvy);
        }
    }
}
```

#### Cursor Position (`drawCursor()`)
```java
if (drawFromZero && !plot2d) {
    double elapsedTime = sim.t - startTime;
    double displayTimeSpan = elapsedTime;
    double timeFromStart = cursorTime - startTime;
    cursorX = rect.x + (int)(rect.width * timeFromStart / displayTimeSpan);
    
    // Read value from history
    int historyIndex = (int)(timeFromStart / historySampleInterval);
    value = plot.historyMaxValues[historyIndex];
}
```

### Serialization Format

#### Flag Storage
```java
// In dump flags (4th parameter)
flags |= (drawFromZero ? FLAG_DRAW_FROM_ZERO : 0);  // Bit 22
```

#### Example Dump Line
```
o 11 2 0 xc01006 562949953421312 0.1 0 2 11 3
          │                                   
          └─ flags with FLAG_DRAW_FROM_ZERO set (bit 22 = 1)
```

**Note:** `startTime` is NOT saved - it's always 0.0 when the feature is enabled, and simulation is reset when toggling on.

## User Interface

### Enabling the Feature
1. Right-click on scope
2. Select "Draw From Zero" checkbox
3. Simulation automatically resets to t=0
4. Auto Scale Time is automatically enabled

### Menu Items
- **Draw From Zero**: Toggle the feature on/off
- ~~Auto Scale Time~~: Removed from menu (automatically managed)

### Visual Indicators
- **Scale Display**: Shows by default (H=time/div, V=voltage/div)
- **Cursor Time**: Fixed format with 2 decimal places (e.g., "1.50 s" not "1.5 m")
- **Gridlines**: Stationary at t=0 with adaptive spacing

## Behavior Notes

### Simulation Reset
When Draw From Zero is enabled, the simulation automatically resets to t=0 via `sim.resetAction()`. This ensures:
- Clean starting point for waveform capture
- Consistent time reference across all scopes
- No confusion about arbitrary start times

### Auto Scale Time
Auto Scale Time is automatically enabled/disabled with Draw From Zero:
- **Draw From Zero ON** → Auto Scale Time ON
- **Draw From Zero OFF** → Auto Scale Time OFF

This simplifies the interface since Auto Scale Time only makes sense in Draw From Zero mode.

### Buffer Management
- Initial capacity: `scopePointCount × 4` samples
- Downsampling triggers at capacity
- Console message: `"Downsampling scope history: 1024 -> 512 samples, interval: 2x"`
- Peak preservation maintains signal fidelity

### Performance
- Batched drawing reduces canvas API calls
- History buffers grow dynamically
- Downsampling prevents unbounded memory growth
- No performance impact in standard mode

## Usage Examples

### Basic Circuit Analysis
Enable Draw From Zero to see complete waveform from startup:
- Power-on transients
- Oscillator startup behavior
- RC/RLC step responses
- Complete charge/discharge cycles

### Long-Running Simulations
Automatic downsampling allows viewing very long simulations:
- Battery discharge curves
- Temperature drift
- Slow oscillations
- Multi-hour simulations

### Comparative Analysis
Use multiple scopes (some standard, some Draw From Zero):
- Recent detail in standard scope
- Historical context in Draw From Zero scope
- Combined view of past and present

## Known Limitations

1. **Fixed Scale Mode Removed**: Auto Scale Time is always enabled in Draw From Zero mode
2. **Memory Usage**: History buffers grow until downsampling occurs
3. **Downsampling Loss**: Very high-frequency details may be lost over long simulations
4. **No Zoom/Pan**: Cannot zoom into specific historical time ranges
5. **No XY Mode**: Feature disabled for 2D/XY plots

## Future Enhancements

Potential improvements for future versions:
- Manual time window selection
- Zoom/pan controls for historical data
- Alternative downsampling algorithms (averaging, decimation)
- Export historical data to file
- Marker placement at specific times
- Trigger-based capture start

## Testing

Feature has been tested with:
- Various circuit types (RC, RLC, oscillators, digital circuits)
- Multiple scopes in same circuit
- Save/load circuit functionality
- Long-running simulations with downsampling
- Mouse interaction and cursor display
- Gridline rendering at various time scales

## References

- **Main Implementation**: `src/com/lushprojects/circuitjs1/client/Scope.java`
- **Plot Data**: `src/com/lushprojects/circuitjs1/client/ScopePlot.java`
- **Menu Integration**: `src/com/lushprojects/circuitjs1/client/ScopePopupMenu.java`
- **Simulator Core**: `src/com/lushprojects/circuitjs1/client/CirSim.java`
- **Test Circuit**: `tests/scope-draw-from-zero-test.txt`
