# ODE Element Implementation

## Overview

The `ODEElm` (ODE Element) is a new circuit element that calculates ordinary differential equations (ODEs) by evaluating a user-defined equation and integrating the result over time. It's designed to be simple and intuitive for modeling dynamic systems without requiring input pins.

## Key Features

- **No Input Pins**: Unlike IntegratorElm, ODEElm has no input pins. Instead, it references labeled nodes directly in its equation (similar to how TableElm works)
- **Single Output Pin**: Provides the integrated value as a voltage output
- **User-Editable Equation**: The equation can reference:
  - Labeled nodes by name (e.g., `rate`, `temperature`, `stock1`)
  - Time variable `t`
  - Mathematical functions (sin, cos, exp, log, etc.)
  - Computed values from other elements (TableElm, GodlyTableElm)
- **Initial Value**: Configurable initial condition for the integration
- **Compact Design**: Small visual footprint (2x2 grid)

## Integration Method

The element uses numerical integration with the equation:

```
y[n+1] = y[n] + dt * f(t, labeled_nodes)
```

Where:
- `y[n]` is the current integrated value
- `dt` is the simulation time step
- `f(t, labeled_nodes)` is the user's equation

## Example Use Cases

### 1. Constant Rate Integration
**Equation**: `5`  
**Result**: Integrates at constant rate of 5 units/second

### 2. Reference Labeled Node
**Equation**: `flow_rate`  
**Setup**: Create a labeled node named "flow_rate" with value from another element  
**Result**: Integrates the flow rate over time (accumulates stock)

### 3. Exponential Decay
**Equation**: `-0.1 * stock`  
**Setup**: 
- Create labeled node "stock" connected to the ODE output
- Set initial value to 100
**Result**: Stock decays exponentially with time constant 10

### 4. System Dynamics Model
**Equation**: `births - deaths`  
**Setup**: 
- Labeled nodes "births" and "deaths" from other calculations
- Initial value represents starting population
**Result**: Population changes based on net flow

### 5. Time-Varying Input
**Equation**: `sin(t)`  
**Result**: Integrates sinusoidal input, producing -cos(t) + initial_value

## Configuration

### Parameters
1. **Equation (d/dt)**: The differential equation to integrate
   - Text field supporting expressions
   - Can reference labeled nodes, time `t`, and mathematical functions
   - Examples: `rate`, `a + b`, `sin(t)`, `max(in1, in2)`

2. **Initial Value y(0)**: The starting value for integration
   - Default: 0.0
   - Sets the value at t=0

### Visual Representation
The element displays:
- "ODE" label at the top
- "d/dt=" with the equation (truncated if long) at the bottom
- Single output pin labeled "∫" on the right side

## Implementation Details

### Technical Characteristics
- **Dump Type**: 261
- **Base Class**: ChipElm
- **Voltage Sources**: 1 (for output)
- **Convergence**: Adaptive threshold based on iteration count and value magnitude
- **File Format**: `261 xa ya xb yb flags escaped_equation initial_value`

### Convergence Strategy
The element uses adaptive convergence checking similar to GodlyTableElm:
- Early iterations (< 10): 0.1% relative tolerance
- Mid iterations (< 100): 1% relative tolerance  
- Late iterations (≥ 100): 10% relative tolerance (helps convergence)
- Minimum absolute threshold: 1e-6 (prevents false failures near zero)

### Expression Evaluation
The element uses the existing `Expr` and `ExprParser` classes to:
- Parse equation strings into compiled expressions
- Resolve labeled node references via `ComputedValues.getComputedValue()` or `CirSim.getLabeledNodeVoltage()`
- Evaluate mathematical functions and operators
- Handle time variable `t`

## Comparison with Other Elements

### vs. IntegratorElm
- **IntegratorElm**: Takes voltage input on pins, can have initial value input
- **ODEElm**: No input pins, references labeled nodes in equation
- **Use IntegratorElm when**: You have a voltage source to integrate
- **Use ODEElm when**: You want to reference multiple labeled nodes or compute a dynamic expression

### vs. GodlyTableElm
- **GodlyTableElm**: Table with multiple rows/columns, integrates column sums
- **ODEElm**: Single output, integrates a single equation
- **Use GodlyTableElm when**: You need stock-flow modeling with multiple stocks
- **Use ODEElm when**: You need a simple single-variable ODE integration

### vs. VCVSElm (with integration expression)
- **VCVSElm**: Voltage-controlled voltage source, requires input pins
- **ODEElm**: Directly references labeled nodes, simpler for pure computation
- **Use VCVSElm when**: You need derivative-based stamping
- **Use ODEElm when**: You want clean labeled node references

## Menu Location

The element is added to the main menu between "Integrator" and "Percent/Ratio Meter":
```
Draw → Add ODE
```

## Example Circuit

Here's a simple exponential decay example:

1. Add an ODEElm
2. Edit the ODE element:
   - Equation: `-0.1 * stock`
   - Initial Value: 100
3. Add a LabeledNodeElm named "stock"
4. Connect the ODE output to the "stock" labeled node
5. Add a scope to visualize the decay
6. Run simulation - stock decays from 100 exponentially

## File Format

When saved, the element serializes as:
```
261 x1 y1 x2 y2 flags escaped_equation initial_value
```

Example:
```
261 100 100 200 200 0 -0.1*stock 100
```

## Error Handling

- **Parse Errors**: Logged to console with equation details
- **Null Expression**: Element continues but doesn't integrate
- **Convergence Issues**: Adaptive threshold helps with difficult equations
- **Missing Labeled Nodes**: Returns 0.0 (handled by Expr evaluation)

## Future Enhancements

Potential improvements:
1. **Multiple Outputs**: Support multiple ODEs in one element
2. **RK4 Integration**: Higher-order integration method option
3. **Output Labeling**: Automatically create labeled node for output
4. **Equation Library**: Pre-built common equations (decay, growth, oscillation)
5. **Graphical Equation Editor**: Visual equation builder

## Code Structure

```java
class ODEElm extends ChipElm {
    // Configuration
    private String equationString;
    private double initialValue;
    
    // Computation
    private Expr compiledExpr;
    private ExprState exprState;
    private double integratedValue;
    private double lastEquationValue;
    
    // Key methods
    void parseEquation()      // Compile equation string
    void doStep()             // Evaluate and integrate
    void stepFinished()       // Update state for next step
    void stamp()              // Stamp matrix (nonlinear)
    double getConvergeLimit() // Adaptive convergence
}
```

## Dependencies

- `ChipElm`: Base class for chip-like elements
- `Expr/ExprParser`: Expression parsing and evaluation
- `ExprState`: State management for expressions
- `ComputedValues`: Labeled node value lookup
- `CustomLogicModel`: String escaping for serialization

## Testing Recommendations

1. **Constant Integration**: Verify linear accumulation
2. **Labeled Node Reference**: Test with various labeled node values
3. **Time-Varying**: Test with `sin(t)`, `exp(-t)`, etc.
4. **Convergence**: Test with stiff equations and multiple coupled ODEs
5. **Save/Load**: Verify serialization and deserialization
6. **Reset**: Ensure integration resets to initial value

## Summary

The ODEElm provides a simple, intuitive way to perform ODE integration in CircuitJS1 without requiring explicit input pins. By referencing labeled nodes directly in equations (like TableElm), it enables clean system dynamics modeling where stocks are computed from flows expressed as algebraic combinations of other labeled values.
