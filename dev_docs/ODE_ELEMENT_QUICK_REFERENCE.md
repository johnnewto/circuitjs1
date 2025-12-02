# ODEElm Quick Reference

## What is ODEElm?

ODEElm is a circuit element that performs ODE (Ordinary Differential Equation) integration by evaluating an equation that references labeled nodes, then integrating the result over time.

## Quick Start

1. **Add Element**: Draw → Add ODE
2. **Edit Equation**: Right-click → Edit → Enter equation in "Equation (d/dt)" field
3. **Set Initial Value**: Enter starting value in "Initial Value y(0)" field
4. **Connect Output**: Wire the output pin to a labeled node or scope

## Equation Syntax

### Reference Labeled Nodes
```
rate              # Single labeled node
flow_in - flow_out  # Combination of labeled nodes
price * quantity   # Multiplication
```

### Time-Based
```
sin(t)            # Sine of time
exp(-0.5*t)       # Exponential decay over time
1 + 0.1*t         # Linear ramp
```

### Mathematical Functions
```
max(a, b)         # Maximum of two values
min(a, b)         # Minimum
abs(x)            # Absolute value
sqrt(x)           # Square root
```

### Conditional Logic
```
x > 5 ? 10 : 0    # If-then-else (ternary operator)
x > 0 && y > 0 ? 1 : 0  # Logical AND
```

## Common Patterns

### Constant Accumulation
- **Equation**: `5`
- **Result**: Linear growth at 5 units/second

### Stock-Flow Model
- **Equation**: `inflow - outflow`
- **Setup**: Create labeled nodes "inflow" and "outflow"
- **Result**: Stock changes by net flow

### Exponential Decay
- **Equation**: `-0.1 * stock`
- **Setup**: Wire output to labeled node "stock"
- **Initial**: Set to starting value (e.g., 100)
- **Result**: Exponential decay with time constant 10

### Proportional Growth
- **Equation**: `0.05 * population`
- **Setup**: Wire output to labeled node "population"
- **Result**: Population grows at 5% per second

### Feedback Loop
- **Equation**: `birth_rate - death_rate * population`
- **Setup**: 
  - Labeled node "birth_rate" = constant birth rate
  - Labeled node "death_rate" = per-capita death rate
  - Wire output to "population"
- **Result**: Population dynamics with carrying capacity

## Properties

| Property | Description | Default |
|----------|-------------|---------|
| Equation (d/dt) | The differential equation to integrate | `1` |
| Initial Value y(0) | Starting value at t=0 | `0.0` |

## Output Pin

- **Label**: ∫ (integral symbol)
- **Type**: Voltage source
- **Value**: Current integrated result

## Display

The element shows:
- **Top**: "ODE" label
- **Bottom**: "d/dt=" followed by equation (truncated if long)
- **Right side**: Single output pin with ∫ symbol

## File Format

```
261 x1 y1 x2 y2 flags escaped_equation initial_value
```

Example:
```
261 100 100 200 200 0 5 0
261 100 100 200 200 0 -0.1*stock 100
```

## Tips

1. **Use Descriptive Names**: Name labeled nodes clearly (e.g., "flow_rate" not "x")
2. **Check Units**: Ensure equation units are consistent with integration
3. **Initial Values**: Set initial value to match system's starting state
4. **Circular References**: You can wire output to a labeled node referenced in the equation (creates feedback)
5. **Scope Output**: Connect a scope to visualize the integrated value over time
6. **Multiple ODEs**: Use multiple ODE elements for systems of differential equations

## Common Mistakes

❌ **Forgetting to create labeled nodes**
- Equation: `rate`
- Problem: No labeled node named "rate" exists
- Solution: Create a LabeledNodeElm with text "rate"

❌ **Wrong direction for decay**
- Equation: `0.1 * stock` (should be negative)
- Problem: Stock grows instead of decays
- Solution: Use `-0.1 * stock`

❌ **Not wiring output**
- Problem: Integrated value not visible or used
- Solution: Wire output to labeled node or scope

❌ **Circular reference without feedback**
- Equation: `stock` with output to "stock"
- Problem: Just copies value, no dynamics
- Solution: Use `rate - 0.1*stock` or similar

## Examples

### 1. Constant Rate
```
Equation: 5
Initial: 0
Result: y(t) = 5*t
```

### 2. Exponential Decay from 100
```
Equation: -0.2 * stock
Initial: 100
Output → labeled node "stock"
Result: y(t) = 100 * exp(-0.2*t)
```

### 3. Sine Wave Integration
```
Equation: sin(t)
Initial: 0
Result: y(t) = -cos(t) + 1
```

### 4. Net Flow
```
Labeled nodes: inflow=5, outflow=2
Equation: inflow - outflow
Initial: 0
Result: y(t) = 3*t (net accumulation)
```

## Debugging

- **Info Display**: Right-click → "View Info" shows current values
- **Console**: Check browser console for parse errors
- **Convergence**: If simulation is slow, equation may be stiff
- **Zero Values**: Missing labeled nodes return 0.0

## Related Elements

- **IntegratorElm**: Integration with voltage input pins
- **GodlyTableElm**: Table-based stock-flow modeling
- **TableElm**: Multi-cell equation evaluation
- **VCVSElm**: Voltage-controlled voltage source

## Summary

ODEElm provides simple ODE integration without input pins by directly referencing labeled nodes in equations. Perfect for system dynamics, stock-flow models, and any time-integrated calculations.
