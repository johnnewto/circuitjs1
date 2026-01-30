# PercentElm - Percentage Output Element

## Overview

`PercentElm` is an **active circuit element** that computes the percentage of multiple input voltages and outputs the result as an actual voltage. It divides the first input by all subsequent inputs and multiplies by 100, outputting this value through a voltage source.

**Formula**: `Vout = (V0 / V1 / V2 / ...) × 100`

## Key Features

- **Active Output**: Produces actual voltage output via a nonlinear voltage source
- **Multi-Input Support**: Configurable 2-8 input terminals
- **Percentage Calculation**: Outputs (V0 / V1 / V2 / ...) × 100 as voltage
- **Output Clamping**: Optional clamp level to prevent runaway values
- **Division by Zero Protection**: Returns 0 when any denominator is near zero (< 1e-6)
- **High-Impedance Inputs**: No current flows through input terminals
- **Nonlinear Element**: Uses iterative convergence like other arithmetic elements

## Usage

### Adding to Circuit
1. From the menu: **Draw → Add Percent**
2. Connect input terminals to voltage sources
3. The first terminal (top) is the numerator (V0)
4. Subsequent terminals are denominators (V1, V2, ...)
5. The output terminal provides the computed percentage voltage

### Configuration Options

Right-click on the element and select "Edit..." to access:

1. **Number of Inputs**: Set between 2 and 8 input terminals
2. **Clamp Level (%)**: Maximum output magnitude (0 = no clamping)
3. **Small**: Toggle smaller visual size

## Use Cases

### 1. Percentage Calculations
Output the ratio of two voltages as a percentage for use in other calculations.

### 2. Efficiency Calculations  
Compute efficiency (Pout/Pin × 100) and feed to other circuit elements.

### 3. Stock-Flow Models
Calculate percentage changes in economic models.

### 4. Control Systems
Generate percentage-based control signals for feedback loops.

### 5. Signal Processing
Create ratio-based outputs for signal conditioning.

## Technical Details

### File Location
`src/com/lushprojects/circuitjs1/client/PercentElm.java`

### Dump Type
Character: `'P'`

### Electrical Behavior
- **Input Impedance**: Infinite (high-impedance inputs, no current flow)
- **Output**: Active voltage source driving computed percentage
- **Nonlinear**: Uses iterative convergence (doStep() method)
- **Connections**: No electrical connection between input terminals; output connects to ground

### Implementation Pattern

PercentElm follows the **high-impedance arithmetic element** pattern:
- `nonLinear()` returns `true`
- `getVoltageSourceCount()` returns `1`
- `getConnection(n1, n2)` returns `false` (no input-to-input connections)
- `hasGroundConnection(n)` returns `true` for output terminal
- Uses direct stamping in `doStep()` without derivative linearization

### Convergence

The element checks convergence based on output value stability:
```java
double tolerance = Math.max(Math.abs(v0) * 0.001, 1e-6);
if (outputDelta > tolerance && sim.subIterations < 100)
    sim.converged = false;
```

## Example Usage

With two 5V inputs:
- V0 = 5V, V1 = 5V
- Output = (5 / 5) × 100 = 100V

With 10V and 5V:
- V0 = 10V, V1 = 5V  
- Output = (10 / 5) × 100 = 200V

## Comparison with Similar Elements

### vs DividerElm
- **DividerElm**: Outputs V0/V1 directly (raw division)
- **PercentElm**: Outputs (V0/V1/...) × 100 (percentage)

### vs MultiplyElm
- **MultiplyElm**: Outputs V0 × V1 × ... (multiplication)
- **PercentElm**: Outputs V0/V1/... × 100 (division with percentage scaling)

### vs ProbeElm
- **ProbeElm**: Display-only, measures single voltage
- **PercentElm**: Active output, computes and outputs percentage

## Limitations

1. **Minimum Denominator**: Values below 1e-6 treated as zero (outputs 0V)
2. **No Current Ratio**: Cannot directly measure current ratios
3. **Voltage Output**: Result is a voltage, not a unitless percentage

## Related Elements

- `DividerElm`: Division without percentage scaling
- `MultiplyElm`: Multiplication of inputs
- `VCVSElm`: Voltage-controlled voltage source with expressions
- `GodlyTableElm`: Table-based calculations with integration
