# Adding Current Flow Mode to EquationTableElm

## Implementation Status: ✅ IMPLEMENTED

The core implementation is complete as of the latest commit. The following features have been added to `EquationTableElm.java`:

### What's Implemented
- **RowOutputMode enum**: `VOLTAGE`, `CURRENT`, `CAPACITOR`
- **Per-row mode arrays**: `outputModes[]`, `targetNodeNames[]`, `capacitances[]`
- **Capacitor state tracking**: `capLastVoltages[]`, `capLastCurrents[]`, `capCurSourceValue[]`
- **stamp() method**: Dispatches to mode-specific stamping (`stampVoltageModeRow()`, `stampCurrentModeRow()`, `stampCapacitorModeRow()`)
- **doStep() method**: Dispatches to mode-specific evaluation (`evaluateCurrentModeRow()`, `evaluateCapacitorModeRow()`)
- **startIteration()**: Calculates capacitor history current for backward Euler integration
- **stepFinished()**: Saves capacitor state for next timestep and registers stock voltage in ComputedValues
- **Serialization**: dump()/load() support for mode, target, and capacitance per row

### ComputedValues Timing (Critical for CAPACITOR Mode)

CAPACITOR mode rows update their stock voltage in `stepFinished()`, which runs AFTER the matrix solve. To ensure display elements (like `LabeledNodeElm`) see the correct value, the execution order is:

```
doStep() for ALL elements  → stamps current sources, writes to pendingValues
commitPendingTo...()       → pendingValues → computedValues
[repeat if not converged]

stepFinished() for ALL     → CAPACITOR rows write stock voltage to pendingValues
commitPendingTo...()       → pendingValues → computedValues
commitConvergedValues()    → computedValues → convergedValues (for display)
```

**Key point**: `stepFinished()` values are committed to both `computedValues` AND `convergedValues` before the next draw cycle. See [ARCHITECTURE.md](ARCHITECTURE.md#computedvalues-double-buffering-system) for full details on the double-buffering system.

### What's Pending
- **UI Edit Dialog**: Mode selector dropdown, target node field, capacitance field
- **Renderer Icons**: Visual indicators for row output modes
- **Integration Testing**: Full circuit testing with the new modes

---

## Executive Summary

This document investigates adding an optional "current flow mode" to `EquationTableElm`, where equations output as **current sources** instead of **voltage sources**. This enables direct modeling of economic flows in Stock-Flow Consistent (SFC) models where current represents transaction flows.

## Motivation

### Current Behavior (Voltage Mode)
- Each row outputs a **voltage** via a voltage source
- Equations compute a value and stamp it as `stampRightSide(vn, value)`
- Good for: Stock levels, prices, incentives (things that exist at a point)

### Proposed Behavior (Current Mode)
- Each row outputs a **current** via a current source between two nodes
- Equations compute a flow rate and stamp it as `stampCurrentSource(from, to, value)`
- Good for: Flows, transactions, rates (things that happen over time)

### Why This Matters for SFC Modeling

| Quantity | Electrical Analog | Unit |
|----------|-------------------|------|
| Stock (Money balance) | Voltage | $ |
| Flow (Transaction rate) | Current | $/s |
| Accumulation | Capacitor | Stock = ∫ Flow dt |

KCL (Kirchhoff's Current Law) **automatically enforces SFC accounting identity**:
```
Σ Inflows = Σ Outflows + ΔStock
```

## Design Proposal

### 1. Per-Row Output Mode Flag

Add a per-row flag to control output mode:

```java
// New per-row arrays
private boolean[] isCurrentMode;     // true = current source, false = voltage source
private String[] targetNodeNames;    // For current mode: destination node name
```

### 2. Two-Post Current Output

For current mode rows, the equation needs **two nodes** (from/to):
- **Source node**: The row's output name (e.g., "Wages")
- **Target node**: A second node name specified in the equation syntax

#### Proposed Syntax Options

**Option A: Equation with arrow notation**
```
Wages -> Firms = 100        # Current from Wages node to Firms node
Consumption -> HH = 0.8*YD  # Current from Consumption to HH node
```

**Option B: Separate target field**
```
Output: Wages
Target: Firms  
Equation: 100
```

**Option C: Function notation**
```
flow(Firms, 100)            # Flow TO Firms (from table's implicit source)
flow(HH, Firms, rate*Y)     # Flow FROM HH TO Firms
```

### 3. Modified stamp() for Current Mode

```java
void stamp() {
    // ... existing voltage mode code ...
    
    for (int row = 0; row < rowCount; row++) {
        if (isCurrentMode[row]) {
            // Current mode: mark as nonlinear for doStep stamping
            sim.stampNonLinear(nodes[0]);  // Source node (or implicit source)
            // Target node is looked up by name in doStep/stamp
        } else {
            // Voltage mode: existing behavior
            // ...
        }
    }
}
```

### 4. Modified doStep() for Current Mode

```java
void doStep() {
    for (int row = 0; row < rowCount; row++) {
        if (isCurrentMode[row]) {
            evaluateCurrentModeRow(row);
        } else {
            evaluateVoltageModeRow(row);  // Existing behavior
        }
    }
}

private void evaluateCurrentModeRow(int row) {
    // Evaluate the equation
    double flowValue = compiledExprs[row].eval(exprStates[row]);
    
    // Get source and target nodes
    int sourceNode = getSourceNodeForRow(row);
    int targetNode = getTargetNodeForRow(row);
    
    if (sourceNode >= 0 && targetNode >= 0) {
        // Stamp current source flowing from source to target
        sim.stampCurrentSource(sourceNode, targetNode, flowValue);
        
        // For nonlinear equations, also compute derivatives
        if (!isConstantRow[row]) {
            stampCurrentModeDerivatives(row, sourceNode, targetNode, flowValue);
        }
    }
    
    // Store for display and convergence
    outputValues[row] = flowValue;
    checkCurrentConvergence(row, flowValue);
}
```

### 5. Newton-Raphson for Nonlinear Current Expressions

If the flow equation depends on voltages (e.g., `flow = rate * V_source`), use linearization:

```java
private void stampCurrentModeDerivatives(int row, int srcNode, int tgtNode, double flowValue) {
    // I = f(V_src, V_tgt, ...)
    // Linearized: I ≈ I_0 + (∂I/∂V_src)·ΔV_src + (∂I/∂V_tgt)·ΔV_tgt
    
    double dv = 1e-6;
    double v_src = sim.nodeVoltages[srcNode];
    double v_tgt = sim.nodeVoltages[tgtNode];
    
    // Compute ∂I/∂V_src numerically
    ExprState state = exprStates[row];
    state.values[SRC_VOLTAGE_IDX] = v_src + dv;
    double flowPlus = compiledExprs[row].eval(state);
    state.values[SRC_VOLTAGE_IDX] = v_src - dv;
    double flowMinus = compiledExprs[row].eval(state);
    double dI_dVsrc = (flowPlus - flowMinus) / (2 * dv);
    state.values[SRC_VOLTAGE_IDX] = v_src;
    
    // Similarly for ∂I/∂V_tgt...
    
    // Stamp VCCS matrix entries (following SFCFlowElm pattern)
    if (Math.abs(dI_dVsrc) > 1e-12) {
        sim.stampMatrix(srcNode, srcNode, -dI_dVsrc);
        sim.stampMatrix(tgtNode, srcNode, dI_dVsrc);
    }
    
    // Stamp constant part: I_0 - (∂I/∂Vsrc)·Vsrc - (∂I/∂Vtgt)·Vtgt
    double constantPart = flowValue - dI_dVsrc * v_src - dI_dVtgt * v_tgt;
    sim.stampCurrentSource(srcNode, tgtNode, constantPart);
}
```

## Integration in Current Mode

### The Key Question

How does `integrate()` work in current mode?

### Answer: It Still Works the Same Way

Integration is performed **within the expression evaluation**, not by the stamping:

```java
// In Expr.java, E_INTEGRATE case:
double inputVal = left.eval(es);
es.pendingIntInput = inputVal;
double result = es.lastIntOutput + sim.timeStep * inputVal;
return result;
```

The integration function accumulates a **scalar value** over time. The result can then be:
- **Voltage mode**: Stamped as voltage → Stock level
- **Current mode**: Stamped as current → Flow rate

### Example: Stock Accumulation in Current Mode

Consider modeling household savings where inflows accumulate as stock:

```
# Voltage mode (stock as voltage):
Savings ~ integrate(Income - Consumption)

# Current mode (flow drives stock via capacitor):
NetSavings -> HH_Cap = Income - Consumption
# The capacitor at HH_Cap naturally integrates: V = (1/C)∫I dt
```

In current mode, you'd typically connect the current source to a **capacitor** (SFCSectorElm) which performs the integration in the circuit domain.

### Hybrid Approach: Explicit vs Implicit Integration

| Approach | Integration Method | Best For |
|----------|-------------------|----------|
| **Voltage + integrate()** | Explicit in expression | Direct control over accumulation |
| **Current + Capacitor** | Implicit in MNA | SFC models, automatic KCL |
| **Capacitor Mode** | Built-in companion model | Self-contained integrating stocks |

## Capacitor Mode (Third Output Mode)

### Motivation

Rather than requiring a separate `SFCSectorElm` capacitor element for each stock, a row can directly implement the **capacitor companion model**. This makes the row act as an **integrating stock** that receives current (flow) and accumulates it as voltage (stock level).

### How It Works

The capacitor companion model converts a capacitor into:
1. A **resistor** with conductance G = C/dt (the "companion resistance")
2. A **current source** that maintains continuity with the previous timestep

```
Capacitor: I = C × dV/dt

Discretized (Backward Euler): I = (C/dt) × (V_new - V_old)

Rearranged as companion model:
  - Resistor: G = C/dt connected node to ground
  - Current source: I_hist = -V_old × G (preserves previous state)
```

### Implementation

```java
// New row mode enum
enum RowOutputMode {
    VOLTAGE,      // Existing: stamps voltage source
    CURRENT,      // New: stamps current source to target
    CAPACITOR     // New: stamps capacitor companion model
}

// Per-row fields for capacitor mode
private double[] capacitances;       // Capacitance value (default 1.0)
private double[] lastVoltages;       // V_old for companion model
private double[] lastCurrents;       // For trapezoidal integration
private boolean[] useTrapezoidal;    // Integration method flag
```

### stamp() for Capacitor Mode

```java
void stamp() {
    for (int row = 0; row < rowCount; row++) {
        if (outputModes[row] == RowOutputMode.CAPACITOR) {
            // Stamp companion resistor (linear, done once)
            double compResistance = sim.timeStep / capacitances[row];
            int stockNode = labeledNodeNumbers[row];
            sim.stampResistor(stockNode, 0, compResistance);
            sim.stampRightSide(stockNode);  // Mark for doStep updates
            
            // Mark as nonlinear if equation depends on voltages
            if (!isConstantRow[row]) {
                sim.stampNonLinear(stockNode);
            }
        }
    }
}
```

### startIteration() for Capacitor Mode

```java
@Override
void startIteration() {
    for (int row = 0; row < rowCount; row++) {
        if (outputModes[row] == RowOutputMode.CAPACITOR) {
            double compResistance = sim.timeStep / capacitances[row];
            
            // Trapezoidal: I_hist = -V_old/R - I_old
            // Backward Euler: I_hist = -V_old/R
            if (useTrapezoidal[row]) {
                capCurSourceValue[row] = -lastVoltages[row] / compResistance - lastCurrents[row];
            } else {
                capCurSourceValue[row] = -lastVoltages[row] / compResistance;
            }
        }
    }
}
```

### doStep() for Capacitor Mode

```java
private void evaluateCapacitorModeRow(int row) {
    int stockNode = labeledNodeNumbers[row];
    double compResistance = sim.timeStep / capacitances[row];
    
    // Stamp history current source (maintains previous state)
    sim.stampCurrentSource(stockNode, 0, capCurSourceValue[row]);
    
    // Evaluate equation to get net inflow current
    exprStates[row].t = sim.t;
    exprStates[row].values[0] = sliderValues[row];  // Slider parameter
    double netInflow = compiledExprs[row].eval(exprStates[row]);
    
    // Stamp the equation's flow as additional current into the stock node
    sim.stampCurrentSource(stockNode, 0, netInflow);
    
    // For nonlinear equations, stamp derivatives (Newton-Raphson)
    if (!isConstantRow[row]) {
        stampCapacitorModeDerivatives(row, stockNode, netInflow);
    }
    
    // Store for display
    outputValues[row] = volts[row];  // Stock level is the node voltage
    flowValues[row] = netInflow;      // Flow value for info display
}
```

### stepFinished() for Capacitor Mode

```java
@Override
void stepFinished() {
    for (int row = 0; row < rowCount; row++) {
        if (outputModes[row] == RowOutputMode.CAPACITOR) {
            // Save state for next timestep
            lastVoltages[row] = volts[row];
            lastCurrents[row] = calculateCapacitorCurrent(row);
            
            // Register stock level in ComputedValues
            String name = outputNames[row];
            if (name != null && !name.trim().isEmpty()) {
                ComputedValues.setComputedValue(name.trim(), volts[row]);
            }
        }
    }
}
```

### Capacitor Mode Syntax

**Proposed UI representation:**

```
╔══════════════════════════════════════════════════════╗
║ EqnTable: Sector Stocks                              ║
╠══════════════════════════════════════════════════════╣
║ ⊥ HH [C=1]  ~ Wages - Consumption - Taxes            ║  ← Capacitor mode
║ ⊥ Firms [C=1] ~ Consumption - Wages                  ║  ← Capacitor mode  
║ ⊥ Govt [C=1] ~ Taxes - GovtSpending                  ║  ← Capacitor mode
╚══════════════════════════════════════════════════════╝
```

The `⊥` symbol (or capacitor icon) indicates capacitor mode. The `[C=1]` shows capacitance (optional, default 1.0).

**Equation meaning in capacitor mode:**
- The equation computes **net inflow** (current into the stock)
- Positive value → stock increases
- Negative value → stock decreases
- Result: `Stock(t) = Stock(t-dt) + dt × NetInflow / C`

### Capacitance Interpretation

| C Value | Meaning | Stock Response |
|---------|---------|----------------|
| C = 1 | Unity integration | Stock changes by flow × dt |
| C = 0.5 | Fast response | Stock changes by 2 × flow × dt |
| C = 2 | Slow response | Stock changes by 0.5 × flow × dt |
| C = 1e6 | Very slow / nearly fixed | Stock barely changes |

For most SFC models, **C = 1** is appropriate (direct integration of flows).

### Initial Conditions

```java
// Initial equation sets starting stock level
initialEquations[row] = "100";  // Start with stock of 100

@Override
void reset() {
    for (int row = 0; row < rowCount; row++) {
        if (outputModes[row] == RowOutputMode.CAPACITOR) {
            if (compiledInitialExprs[row] != null) {
                lastVoltages[row] = compiledInitialExprs[row].eval(exprStates[row]);
            } else {
                lastVoltages[row] = 0;
            }
            lastCurrents[row] = 0;
        }
    }
}
```

### Advantages of Capacitor Mode

1. **Self-contained**: No need for separate `SFCSectorElm` elements
2. **KCL automatic**: Multiple flows can reference the same stock node
3. **Accurate integration**: Uses proper companion model (trapezoidal or backward Euler)
4. **Initial conditions**: Supports initial value equations
5. **Mixed models**: Can combine voltage, current, and capacitor rows in one table

### Comparison of All Three Modes

| Feature | Voltage Mode | Current Mode | Capacitor Mode |
|---------|--------------|--------------|----------------|
| **Output** | Drives voltage | Drives current | Integrates inflow |
| **Stamp** | `stampVoltageSource` | `stampCurrentSource` | Companion model |
| **Integration** | Use `integrate()` | External capacitor | Built-in |
| **Nodes needed** | 1 (output) | 2 (source + target) | 1 (stock node) |
| **Use case** | Prices, rates | Flows, transactions | Stock accumulation |
| **KCL applies** | No (voltage forced) | Yes (at target) | Yes (at stock node) |

### Example: Complete SFC Model with All Modes

```
╔══════════════════════════════════════════════════════════╗
║ EqnTable: SIM Model (All Modes)                          ║
╠══════════════════════════════════════════════════════════╣
║ V  α1 ~ 0.6                       [Propensity to consume]║
║ V  α2 ~ 0.4                       [Wealth propensity]    ║
║ V  θ ~ 0.2                        [Tax rate]             ║
║ V  G ~ 20                         [Govt spending]        ║
║ V  Y ~ G / (1 - α1*(1-θ))         [Equilibrium income]   ║
║ V  Yd ~ Y * (1 - θ)               [Disposable income]    ║
║ I→ Wages: HH <- Firms ~ Y         [Wage flow]            ║
║ I→ Cons: Firms <- HH ~ α1*Yd + α2*H_hh [Consumption]     ║
║ I→ Taxes: Govt <- HH ~ θ*Y        [Tax flow]             ║
║ ⊥ H_hh [C=1] ~ Yd - Cons          [Household wealth]     ║
║ ⊥ H_gov [C=1] ~ Taxes - G         [Govt balance]         ║
╚══════════════════════════════════════════════════════════╝
```

## Parameters in Current Mode

Parameters (slider variables) work identically in current mode:

```java
// In evaluateCurrentModeRow():
exprStates[row].values[SLIDER_IDX] = sliderValues[row];
exprStates[row].t = sim.t;
double flowValue = compiledExprs[row].eval(exprStates[row]);
```

Example equation referencing parameters:
```
Flow ~ rate * (V_source - threshold)
# Where 'rate' and 'threshold' are slider parameters
```

## Accessing Other Variables

Current mode equations can reference:

1. **Labeled node voltages**: `V_HH`, `V_Firms` (stock levels)
2. **Computed values from other tables**: `YD`, `Wages`
3. **Slider parameters**: `rate`, `alpha1`
4. **Time**: `t`
5. **Previous values**: `last(...)`, `integrate(...)`, `diff(...)`

```java
// Expression variable resolution (existing mechanism):
Double value = ComputedValues.getComputedValue(varName);
if (value == null) {
    Integer nodeNum = LabeledNodeElm.getByName(varName);
    if (nodeNum != null) {
        value = sim.nodeVoltages[nodeNum];
    }
}
```

## UI Design

### Edit Dialog Changes

Add a dropdown per row for output mode:

```
┌──────────────────────────────────────────────────┐
│ Row 1: Wages                                     │
│ ┌──────────────────────────────────────────────┐ │
│ │ Output Mode:  [▼ Voltage / Current / Capacitor]│
│ │ Target Node:  [Firms____________] (if Current)│
│ │ Capacitance:  [1.0____] (if Capacitor)        │
│ │ Equation:     [100_______________]           │ │
│ │ Initial:      [____________________]         │ │
│ │ Slider (a):   [1.0__] ═══════════◉═         │ │
│ └──────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
```

### Visual Distinction

In the table display, indicate mode with an icon:
- **V** or voltage icon for voltage mode rows
- **I→** or arrow icon for current mode rows
- **⊥** or capacitor icon for capacitor mode rows

```
╔══════════════════════════════════════╗
║ EqnTable: SFC Model                  ║
╠══════════════════════════════════════╣
║ V  Yd ~ 100                          ║  ← Voltage mode
║ I→ Wages: Firms <- HH ~ rate*Y       ║  ← Current mode
║ I→ Cons: HH <- Firms ~ alpha1*Yd     ║  ← Current mode
║ ⊥ H_hh [C=1] ~ Yd - Cons             ║  ← Capacitor mode
╚══════════════════════════════════════╝
```

## Serialization

Extend the dump format to include current mode flags:

```java
// New dump format:
// ... existing ... mode1 target1 cap1 mode2 target2 cap2 ...
// Where mode: 0=voltage, 1=current, 2=capacitor
// target: node name for current mode (empty string otherwise)
// cap: capacitance for capacitor mode (1.0 default)

@Override
public String dump() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.dump());
    sb.append(" ").append(CustomLogicModel.escape(tableName));
    sb.append(" ").append(rowCount);
    
    for (int row = 0; row < rowCount; row++) {
        sb.append(" ").append(CustomLogicModel.escape(outputNames[row]));
        sb.append(" ").append(CustomLogicModel.escape(equations[row]));
        sb.append(" ").append(CustomLogicModel.escape(initialEquations[row]));
        sb.append(" ").append(CustomLogicModel.escape(sliderVarNames[row]));
        sb.append(" ").append(sliderValues[row]);
        // New fields for output mode:
        sb.append(" ").append(outputModes[row].ordinal());  // 0=V, 1=I, 2=C
        sb.append(" ").append(CustomLogicModel.escape(
            outputModes[row] == RowOutputMode.CURRENT ? targetNodeNames[row] : ""));
        sb.append(" ").append(
            outputModes[row] == RowOutputMode.CAPACITOR ? capacitances[row] : 1.0);
    }
    
    return sb.toString();
}
```

## Implementation Roadmap

### Phase 1: Data Structures
1. Add `RowOutputMode` enum (VOLTAGE, CURRENT, CAPACITOR)
2. Add `outputModes[]`, `targetNodeNames[]`, `capacitances[]` arrays
3. Add capacitor state arrays: `lastVoltages[]`, `lastCurrents[]`, `capCurSourceValue[]`

### Phase 2: Current Mode Implementation
4. Implement `evaluateCurrentModeRow()`
5. Add target node lookup in `stamp()` and `doStep()`
6. Implement Newton-Raphson derivatives for voltage-dependent flows
7. Test with simple flow equations

### Phase 3: Capacitor Mode Implementation
8. Implement companion model stamping in `stamp()`
9. Implement `startIteration()` for history current calculation
10. Implement `evaluateCapacitorModeRow()` in `doStep()`
11. Implement `stepFinished()` state saving
12. Test with integrating stock equations

### Phase 4: UI Integration
13. Add per-row mode dropdown in edit dialog
14. Add target node field (current mode) with autocomplete
15. Add capacitance field (capacitor mode)
16. Update visual rendering to show mode icons (V, I→, ⊥)

### Phase 5: Testing & Polish
17. Test mixed-mode tables (V + I + C rows)
18. Test full SFC models (SIM, SIMEX)
19. Verify KCL conservation in models
20. Add trapezoidal integration option for capacitor mode

## Example: Complete SFC Model

The following example shows how all three modes work together for a complete SIM-style model:

```
╔══════════════════════════════════════════════════════════════════════╗
║ EqnTable: SIM Model (Complete)                                       ║
╠══════════════════════════════════════════════════════════════════════╣
║ V  α1 ~ 0.6                         [Propensity to consume income]   ║
║ V  α2 ~ 0.4                         [Propensity to consume wealth]   ║
║ V  θ ~ 0.2                          [Tax rate]                       ║
║ V  G ~ 20                           [Government spending]            ║
║ V  Y ~ G / (1 - α1*(1-θ))           [Equilibrium income]            ║
║ V  Yd ~ Y * (1 - θ)                 [Disposable income]             ║
║ V  Cons ~ α1*Yd + α2*H_hh           [Consumption function]          ║
║ I→ Wages: HH <- Firms ~ Y           [Wage flow]                      ║
║ I→ Purchases: Firms <- HH ~ Cons    [Consumption flow]               ║
║ I→ Taxes: Govt <- HH ~ θ*Y          [Tax flow]                       ║
║ I→ GovtPurch: HH <- Govt ~ G        [Govt spending flow]             ║
║ ⊥ H_hh [C=1] ~ Yd - Cons            [Household wealth stock]         ║
║ ⊥ H_gov [C=1] ~ Taxes - G           [Govt balance (negative=debt)]  ║
╚══════════════════════════════════════════════════════════════════════╝
```

**How it works:**
- **Voltage rows (V)**: Define parameters, compute derived values
- **Current rows (I→)**: Model transaction flows between sectors
- **Capacitor rows (⊥)**: Track stock accumulation from net inflows

The capacitor mode rows automatically enforce the SFC identity:
```
H_hh + H_gov = 0  (household wealth = govt debt)
```

This emerges from KCL: all flows that leave one sector enter another.

## Comparison: All Three Output Modes

| Aspect | Voltage Mode | Current Mode | Capacitor Mode |
|--------|--------------|--------------|----------------|
| **Output** | Voltage at node | Current between nodes | Integrating stock |
| **MNA stamp** | `stampVoltageSource` + `stampRightSide` | `stampCurrentSource` | Companion resistor + current |
| **Integration** | Explicit via `integrate()` | External capacitor | Built-in companion model |
| **Conservation** | Not automatic | Automatic via KCL | Automatic via KCL |
| **Good for** | Stock levels, prices, rates | Flows, transactions | Stock accumulation |
| **Nodes required** | 1 (output) | 2 (source + target) | 1 (stock node) |
| **Equation meaning** | Output value directly | Flow rate from→to | Net inflow to stock |

## Conclusion

Adding current flow mode and capacitor mode to `EquationTableElm` would provide a powerful tool for SFC economic modeling that:

1. **Leverages MNA** for automatic conservation enforcement (KCL = SFC identity)
2. **Maintains compatibility** with existing voltage mode for mixed models
3. **Current mode** enables direct flow modeling with `SFCSectorElm` capacitors
4. **Capacitor mode** provides self-contained integrating stocks (no external elements needed)
5. **Preserves all expression features** including `integrate()`, `diff()`, parameters
6. **Mixed tables** can combine all three modes for comprehensive SFC models

The implementation builds on existing patterns:
- Current stamping from `SFCFlowElm`
- Companion model from `CapacitorElm`
- Expression evaluation from existing `EquationTableElm`

## References

- [MNA_SFC_CURRENT_FLOW_INVESTIGATION.md](MNA_SFC_CURRENT_FLOW_INVESTIGATION.md) - Theoretical background
- [SFCFlowElm.java](../src/com/lushprojects/circuitjs1/client/SFCFlowElm.java) - Existing current source implementation
- [CapacitorElm.java](../src/com/lushprojects/circuitjs1/client/CapacitorElm.java) - Companion model for integration
