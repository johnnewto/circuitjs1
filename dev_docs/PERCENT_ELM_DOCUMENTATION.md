# PercentElm - Ratio/Percentage Display Element

## Overview

`PercentElm` is a display-only circuit element that computes and displays the ratio or percentage of two input voltages. It divides the first input by the second input (V1/V2) and can display the result either as a simple ratio or as a percentage.

## Key Features

- **Display-Only**: Does not affect circuit behavior (acts as open circuit/infinite impedance)
- **No Circuit Stamping**: Pure computational element without derivative stamping
- **Ratio Calculation**: Computes V1/V2 where V1 is the voltage at the first terminal and V2 is at the second
- **Dual Display Modes**: 
  - Ratio mode: Shows the raw division result
  - Percentage mode: Shows the ratio × 100 with % symbol
- **Configurable Scaling**: Auto, m, 1, K, M scale options
- **Division by Zero Protection**: Returns 0 when denominator is near zero (< 1e-12)

## Usage

### Adding to Circuit
1. From the menu: **Draw → Add Percent/Ratio Meter**
2. Place two terminals in your circuit to measure the voltage ratio
3. The first terminal is the numerator (V1)
4. The second terminal is the denominator (V2)

### Configuration Options

Right-click on the element and select "Edit..." to access:

1. **Show Value**: Toggle display of the computed value
2. **Show as Percentage**: 
   - Checked: Displays result × 100 with % symbol
   - Unchecked: Displays raw ratio value
3. **Scale**: Choose display scale (Auto, m, 1, K, M)
4. **Fixed Precision**: Use fixed decimal precision for the scale

## Use Cases

### 1. Voltage Divider Analysis
Monitor the output/input ratio of a voltage divider to verify the divider formula.

### 2. Efficiency Calculations
Display power efficiency by connecting to power measurements (Pout/Pin).

### 3. Feedback Ratio Monitoring
In amplifier circuits, monitor the feedback ratio for gain calculations.

### 4. Comparative Measurements
Compare two voltage levels in a circuit and see their relationship as a percentage.

### 5. Battery State of Charge
Display battery voltage as a percentage of nominal voltage.

## Technical Details

### File Location
`src/com/lushprojects/circuitjs1/client/PercentElm.java`

### Dump Type
Character: `%`

### Electrical Behavior
- **Impedance**: Infinite (open circuit)
- **Power Dissipation**: 0 watts
- **Circuit Impact**: None - purely for display
- **Connections**: No electrical connection between terminals

### Implementation Notes

1. **Extends CircuitElm**: Inherits basic circuit element functionality
2. **No stamp() implementation**: Does not modify circuit matrices
3. **Computation in stepFinished()**: Calculates ratio after each simulation step
4. **Safe Division**: Handles division by zero gracefully

### Registration

The element is registered in `CirSim.java`:
- **createCe()**: Case '%' for file loading
- **constructElement()**: "PercentElm" for UI creation
- **composeMainMenu()**: Menu entry "Add Percent/Ratio Meter"

## Example Circuit

```
$ 1 0.000005 10.20027730826997 50 5 50 5e-11
v 128 176 128 320 0 0 40 10 0 0 0.5
r 128 176 256 176 0 100
g 128 320 128 352 0 0
v 384 176 384 320 0 0 40 5 0 0 0.5
r 384 176 512 176 0 100
g 384 320 384 352 0 0
% 256 176 512 176 3 0
o 6 64 0 4098 10 0.1 0 2 6 3
```

This circuit:
- Creates two voltage sources (10V and 5V)
- Connects them through resistors to the PercentElm
- Displays the ratio 10V/5V = 2.0 (or 200% if percentage mode enabled)

## Comparison with Similar Elements

### vs ProbeElm
- **ProbeElm**: Measures single voltage with various metrics (RMS, peak, etc.)
- **PercentElm**: Computes ratio of two voltages

### vs DividerElm
- **DividerElm**: Produces an output voltage that is V1/V2 (affects circuit)
- **PercentElm**: Display-only, does not produce output or affect circuit

### vs OutputElm
- **OutputElm**: Shows single voltage value
- **PercentElm**: Shows ratio of two voltages

## Limitations

1. **No Output Pin**: Cannot drive other circuit elements
2. **Display Only**: Cannot be used in feedback loops
3. **Two Terminal Only**: Cannot compute ratios of more than two inputs
4. **Voltage Only**: Cannot directly compute ratios of currents (use voltage measurements across known resistors)

## Future Enhancements

Potential improvements could include:
- Multi-input ratio calculations (V1/(V2+V3))
- Current ratio measurement
- Logarithmic display (dB)
- Min/max ratio tracking
- Time-averaged ratio calculation

## Related Elements

- `ProbeElm`: Voltage measurement with various modes
- `DividerElm`: Division with circuit output
- `MultiplyElm`: Multiplication of inputs
- `OutputElm`: Single voltage display
- `OhmMeterElm`: Resistance measurement
