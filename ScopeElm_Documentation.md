# ScopeElm Documentation

## Overview

The `ScopeElm` class in CircuitJS1 provides oscilloscope functionality for visualizing circuit behavior over time. It acts as a wrapper around the more complex `Scope` class, providing the circuit element interface for placing scopes within circuits. The scope can display voltage, current, power, and resistance waveforms with configurable timebase, scaling, and display options.

## Architecture

### Class Hierarchy
```
CircuitElm (base class)
└── ScopeElm (scope circuit element)
    └── Scope (actual scope implementation)
        └── ScopePlot (individual waveform plots)
```

### Key Components

1. **ScopeElm**: The circuit element that can be placed on the circuit board
2. **Scope**: The main oscilloscope implementation handling data collection and visualization
3. **ScopePlot**: Individual plot traces for different measurements (voltage, current, etc.)
4. **ScopePopupMenu**: Context menu for scope configuration and operations

## ScopeElm Class Details

### Core Properties
- **Position**: Defined by `(x, y, x2, y2)` coordinates on the circuit
- **Size**: Fixed at 128x64 pixels by default
- **elmScope**: Reference to the underlying `Scope` object
- **Dump Type**: 403 (for circuit save/load functionality)

### Key Methods

#### Constructor
```java
public ScopeElm(int xx, int yy)
```
Creates a new scope element at the specified position with default dimensions.

#### Element Management
```java
public void setScopeElm(CircuitElm e)
```
Assigns a circuit element to be monitored by this scope. The scope will automatically determine what values to plot (voltage, current, etc.) based on the element type.

#### Simulation Integration
```java
public void stepScope()
```
Called each simulation timestep to update scope data. This delegates to `elmScope.timeStep()`.

```java
public void reset()
```
Resets the scope display and clears all collected data.

#### Rendering
```java
void draw(Graphics g)
```
Renders the scope display. Uses graphics transformation to handle zoom and pan operations properly.

## Scope Class Details

The `Scope` class contains the core oscilloscope functionality:

### Data Collection Architecture

#### ScopePlot Structure
Each `ScopePlot` represents one trace on the scope:
- **minValues[]**: Array storing minimum values for each time point
- **maxValues[]**: Array storing maximum values for each time point
- **scopePointCount**: Number of data points stored (default: 128)
- **scopePlotSpeed**: Time units per pixel (controls horizontal timebase)
- **ptr**: Current write pointer into the circular buffer

#### Time Management
- **Circular Buffer**: Data is stored in circular arrays for efficient memory usage
- **Timebase Control**: `scopePlotSpeed` determines how many simulation timesteps correspond to one pixel
- **Adaptive Sampling**: Stores min/max values within each time window for accurate representation

### Display Modes

#### Standard Oscilloscope Mode
- **Voltage Traces**: Green waveforms showing voltage vs. time
- **Current Traces**: Yellow waveforms showing current vs. time  
- **Multiple Plots**: Can display multiple signals simultaneously with different colors
- **Grid Lines**: Configurable grid overlay for measurement

#### 2D Plot Mode (`plot2d = true`)
- **X-Y Display**: Plots one signal against another (Lissajous patterns)
- **Phase Relationships**: Useful for analyzing phase relationships between signals
- **Real-time Drawing**: Traces are drawn as simulation progresses

#### FFT Mode (`showFFT = true`)
- **Frequency Domain**: Shows frequency spectrum of the signal
- **Logarithmic Scale**: Optional logarithmic amplitude scaling
- **Spectral Analysis**: Identifies frequency components and harmonics

### Configuration Options

#### Scale Control
```java
// Automatic scaling based on signal amplitude
boolean maxScale = false;

// Manual scaling with user-defined units per division
boolean manualScale = false;
double[] scale = new double[UNITS_COUNT]; // Per-unit scaling

// Manual scale per plot
double manScale = 1.0; // Units per division
int manVPosition = 0; // Vertical position offset
```

#### Timebase Control
```java
int speed; // Simulation timesteps per pixel
int scopePointCount = 128; // Number of data points stored
```

#### Display Options
```java
boolean showV = true;        // Show voltage traces
boolean showI = false;       // Show current traces  
boolean showScale = false;   // Show scale information
boolean showMax = false;     // Show maximum values
boolean showMin = false;     // Show minimum values
boolean showFreq = false;    // Show frequency information
boolean showFFT = false;     // Show FFT spectrum
```

### Data Processing

#### AC Coupling
```java
boolean acCoupled = false;
double acAlpha = 0.9999;     // High-pass filter coefficient
double acLastOut = 0;        // Filter state variable
```
AC coupling removes DC component using a first-order IIR high-pass filter:
```
y[i] = alpha × (y[i-1] + x[i] - x[i-1])
```

#### Value Extraction
Different circuit elements provide different measurement capabilities:
- **Resistors**: Voltage, current, power, resistance
- **Capacitors**: Voltage, current, power  
- **Inductors**: Voltage, current, power
- **Transistors**: Multiple voltages (VBE, VCE, VBC) and currents (IB, IC, IE)
- **Diodes**: Voltage, current, power

### Measurement Units

The scope supports four measurement units:
```java
static final int UNITS_V = 0;    // Volts
static final int UNITS_A = 1;    // Amperes  
static final int UNITS_W = 2;    // Watts
static final int UNITS_OHMS = 3; // Ohms
```

Each unit has associated:
- **Color coding**: Automatic trace coloring based on measurement type
- **Scale factors**: Appropriate scaling for typical values
- **Text formatting**: Proper unit display (mV, μA, etc.)

### User Interface Integration

#### Context Menu (ScopePopupMenu)
- **Remove Scope**: Delete the scope from circuit
- **Dock/Undock**: Toggle between embedded and floating scope windows
- **Max Scale**: Toggle automatic amplitude scaling
- **Stack/Unstack**: Arrange multiple scopes vertically
- **Combine**: Merge multiple scope traces into one display
- **Reset**: Clear all collected data and restart
- **Properties**: Open detailed configuration dialog

#### Mouse Interaction
- **Scope Selection**: Click to select scope for configuration
- **Drag Handles**: Resize scope display area
- **Scroll Wheel**: Adjust timebase or amplitude scaling
- **Right-click**: Open context menu

### Performance Considerations

#### Memory Management
- **Circular Buffers**: Efficient fixed-memory data storage
- **Canvas Caching**: Off-screen canvas for smooth rendering
- **Selective Updates**: Only redraw when data changes

#### Real-time Performance
- **Fixed Update Rate**: Scope updates tied to simulation timestep
- **Adaptive Quality**: Reduces detail at high zoom levels for smooth performance
- **Background Processing**: Non-blocking data collection

### Serialization and Persistence

#### Save Format
Scopes are saved as part of the circuit with format:
```
403 x1 y1 x2 y2 flags scopeData
```
Where `scopeData` contains:
- Element associations
- Scale settings  
- Display configuration
- Plot parameters

#### Dump/Restore
```java
public String dump()           // Serialize scope state
void undump(StringTokenizer)   // Restore scope state
```

## Common Usage Patterns

### Basic Voltage Monitoring
1. Place scope element in circuit
2. Right-click and select target element
3. Scope automatically shows voltage waveform
4. Adjust timebase and scale as needed

### Multi-Signal Analysis  
1. Add multiple elements to same scope
2. Use "Stack" mode for separate traces
3. Use "Combine" mode for overlay comparison
4. Color coding differentiates signals

### Frequency Analysis
1. Enable "Show FFT" option
2. Adjust frequency range and scaling
3. Identify harmonics and spectral content
4. Use logarithmic scale for wide dynamic range

### Phase Relationship Analysis
1. Enable 2D plot mode
2. Select two related signals
3. Observe Lissajous patterns
4. Measure phase differences and correlation

## Implementation Notes

### GWT Compatibility
- All code must be GWT-compatible (client-side Java)
- Uses HTML5 Canvas for rendering
- No server-side components or native libraries

### Thread Safety
- Single-threaded execution model
- All updates happen on main simulation thread
- No concurrent access issues

### Browser Compatibility
- Works across modern browsers
- Uses feature detection for Canvas API
- Degrades gracefully on older browsers

### Performance Optimization
- Minimizes object allocation during simulation
- Uses efficient array operations
- Caches graphics operations where possible

## Debugging and Troubleshooting

### Common Issues
1. **Scope not updating**: Check element associations and simulation state
2. **Poor waveform quality**: Adjust timebase and sample rate
3. **Scale problems**: Verify manual vs. automatic scaling settings
4. **Performance issues**: Reduce scope count or simplify display options

### Development Guidelines
- Always test with various circuit elements and conditions
- Verify save/load functionality after changes
- Ensure proper cleanup in reset operations
- Test performance with multiple scopes and complex circuits