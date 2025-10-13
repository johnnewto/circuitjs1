# VCVS doStep() Method Explanation

This document explains how the `doStep()` method works for a **Voltage Controlled Voltage Source (VCVS)** element in CircuitJS1.

## Overview

The VCVS is a **nonlinear circuit element** that outputs a voltage based on an arbitrary expression involving its input voltages. The `doStep()` method is called during each simulation iteration to update the circuit's matrix equations.

## Step-by-Step Breakdown

### 1. Convergence Checking
```java
double convergeLimit = getConvergeLimit();
for (i = 0; i != inputCount; i++) {
    if (Math.abs(volts[i]-lastVolts[i]) > convergeLimit)
        sim.converged = false;
}
```
- Compares current input voltages with previous iteration values
- If any input voltage changed significantly, marks the simulation as not converged
- This triggers additional iterations until the circuit stabilizes

### 2. Expression Evaluation
```java
for (i = 0; i != inputCount; i++)
    exprState.values[i] = volts[i];
exprState.t = sim.t;
double v0 = expr.eval(exprState);
```
- Updates the expression state with current input voltages and simulation time
- Evaluates the user-defined expression to get the target output voltage `v0`

### 3. Output Convergence Check
```java
if (Math.abs(volts[inputCount]-volts[inputCount+1]-v0) > Math.abs(v0)*.01 && sim.subIterations < 100)
    sim.converged = false;
```
- Checks if the actual output voltage matches the desired voltage `v0`
- If the error is > 1% and we haven't exceeded iteration limits, forces another iteration

### 4. Numerical Differentiation (Jacobian Calculation)
```java
for (i = 0; i != inputCount; i++) {
    double dv = volts[i]-lastVolts[i];
    if (Math.abs(dv) < 1e-6)
        dv = 1e-6;
    // Evaluate expression at current point and slightly perturbed point
    exprState.values[i] = volts[i];
    double v = expr.eval(exprState);
    exprState.values[i] = volts[i]-dv;
    double v2 = expr.eval(exprState);
    double dx = (v-v2)/dv;  // Numerical derivative
```
This calculates **∂(output)/∂(input_i)** - how much the output changes with respect to each input voltage.

### 5. Matrix Stamping
```java
sim.stampMatrix(vn, nodes[i], -dx);
rs -= dx*volts[i];
```
- Stamps the calculated derivatives into the circuit's **admittance matrix**
- Updates the right-hand side vector for the Newton-Raphson iteration
- `vn` is the voltage source node number in the expanded matrix

### 6. State Update
```java
for (i = 0; i != inputCount; i++)
    lastVolts[i] = volts[i];
```
- Saves current voltages for the next iteration's convergence check

## Why This Approach?

Since the VCVS can implement arbitrary nonlinear voltage relationships, the simulator uses **Newton-Raphson iteration** to solve the nonlinear equations. The method:

1. **Linearizes** the nonlinear element around the current operating point
2. **Calculates derivatives** numerically to build the Jacobian matrix  
3. **Stamps** these values into the Modified Nodal Analysis (MNA) matrix
4. **Iterates** until convergence is achieved

This allows CircuitJS1 to simulate complex voltage-controlled sources with user-defined mathematical expressions while maintaining the efficiency of matrix-based circuit analysis.

## Mathematical Background

### Modified Nodal Analysis (MNA)
CircuitJS1 uses MNA to solve the circuit equations in matrix form:
**X = A⁻¹B**

Where:
- **A**: Square matrix with one row per circuit node and voltage source (admittance-based)
- **B**: Column vector with entries for nodes and voltage sources  
- **X**: Solution vector containing node voltages and voltage source currents

### Newton-Raphson Method
For nonlinear elements like VCVS, the simulator uses iterative solving:
1. Guess initial values
2. Linearize around current point using derivatives
3. Solve linear system
4. Update values and repeat until convergence

### Numerical Differentiation
The derivative calculation uses a finite difference approximation:
```
∂f/∂x ≈ (f(x) - f(x-δx)) / δx
```
Where δx is a small perturbation (minimum 1e-6 to avoid numerical issues).

## Implementation Notes

- The VCVS extends `VCCSElm` (Voltage Controlled Current Source) but implements voltage output
- Convergence checking prevents infinite loops in nonlinear circuits
- The 1% tolerance balances accuracy with simulation speed
- Matrix stamping follows CircuitJS1's MNA implementation patterns