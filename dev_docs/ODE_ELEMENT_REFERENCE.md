# ODE Element (ODEElm)

## Overview

The ODE Element calculates ordinary differential equations by evaluating a user-defined equation and integrating the result over time. It's designed for modeling dynamic systems by referencing labeled nodes directly in equations.

## Key Features

- **No Input Pins**: References labeled nodes directly in the equation
- **Single Output Pin**: Provides the integrated value as a voltage
- **User-Editable Equation**: Can reference labeled nodes, time `t`, and math functions
- **Initial Value**: Configurable starting condition

## Menu Location

**Draw → Add ODE**

## Configuration

### Parameters (Right-click → Edit)

1. **Equation (d/dt)**: The differential equation to integrate
   - Reference labeled nodes by name: `rate`, `stock`, etc.
   - Use time variable: `t`
   - Math functions: `sin`, `cos`, `exp`, `log`, `sqrt`, `abs`, etc.
   - Examples: `5`, `rate`, `births - deaths`, `sin(t)`

2. **Initial Value y(0)**: Starting value for integration (default: 0)

## Example Use Cases

### 1. Constant Rate Integration
**Equation**: `5`  
**Result**: Value increases at 5 units/second

### 2. Reference Labeled Node
**Equation**: `flow_rate`  
**Result**: Integrates the voltage at labeled node "flow_rate"

### 3. Exponential Decay
**Equation**: `-0.1 * stock`  
**Initial Value**: 100  
**Setup**: Connect output to labeled node "stock"  
**Result**: Exponential decay with time constant 10

### 4. Population Model
**Equation**: `births - deaths`  
**Result**: Population changes based on net flow from labeled nodes

### 5. Time-Varying Input
**Equation**: `sin(t)`  
**Result**: Integrates sinusoid, producing -cos(t)

## Comparison with Similar Elements

| Element | Input Source | Use Case |
|---------|--------------|----------|
| **ODEElm** | Labeled nodes in equation | Clean ODE modeling |
| **IntegratorElm** | Voltage on input pins | Simple integration |
| **GodlyTableElm** | Table columns | Multi-stock modeling |

## Example Circuit: Exponential Decay

1. Add ODE element (Draw → Add ODE)
2. Edit: Equation = `-0.1 * stock`, Initial = 100
3. Add LabeledNodeElm named "stock"
4. Connect ODE output to "stock"
5. Add scope to visualize
6. Run - value decays exponentially from 100

## Notes

- Missing labeled nodes return 0.0
- Output resets to initial value on circuit reset
- Works with ComputedValues from TableElm
