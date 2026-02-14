# SFC MNA Elements Reference

Reference documentation for the two MNA-based Stock-Flow Consistent (SFC) economic modeling elements: `SFCStockElm` and `SFCFlowElm`.

For background theory and architecture, see [MNA_SFC_STOCK_FLOW_USING_CURRENT_INVESTIGATION.md](MNA_SFC_STOCK_FLOW_USING_CURRENT_INVESTIGATION.md).

---

## Overview

These elements implement the **current-as-flow** approach to SFC modeling:

| Concept | Electrical | Element |
|---------|-----------|---------|
| **Stock** (account balance, inventory) | Voltage at a node | `SFCStockElm` (capacitor to ground) |
| **Flow** (transaction, payment) | Current between nodes | `SFCFlowElm` (current source) |
| **Conservation** (SFC identity) | KCL at each node | Automatic — enforced by MNA solver |

The key insight: Kirchhoff's Current Law at every stock node automatically enforces `ΣInflows - ΣOutflows = dStock/dt`, which *is* the SFC accounting identity. No manual bookkeeping is needed.

### Circuit Topology

```
    SFCStockElm          SFCFlowElm           SFCStockElm
    "Households"      "Wages" (I=100)          "Firms"
        ●──────────────→──────────────────●
        │                                 │
       ═══ C=1.0                    C=1.0 ═══
        │                                 │
        ⏚                                 ⏚
```

Each `SFCStockElm` is a single-post capacitor to ground. `SFCFlowElm` elements connect between stock nodes as current sources.

---

## SFCStockElm (Type 268)

**Source**: [SFCStockElm.java](../src/com/lushprojects/circuitjs1/client/SFCStockElm.java)  
**Menu**: SFC Stock (MNA)  
**Posts**: 1 (single node representing the stock)

### Purpose

Represents a named economic stock — an account balance, inventory level, or any quantity that accumulates flows over time. Electrically it is a **capacitor to ground**: voltage = stock level, current = net flow.

### Editable Properties

| # | Property | Default | Description |
|---|----------|---------|-------------|
| 0 | Stock Name | `"Stock"` | Label for display and `ComputedValues` registry (e.g., `"H_h"`, `"H_g"`) |
| 1 | Initial Stock | `0` | Stock level at t=0 (sets initial voltage) |
| 2 | Capacitance | `1.0` | Controls integration rate. With C=1, stock changes by 1 unit per unit-current per unit-time |
| 3 | Trapezoidal Integration | `true` (checkbox) | Trapezoidal (accurate, can ring) vs Backward Euler (stable, damped) |

### File Format (Dump Type 268)

```
268 x1 y1 x2 y2 flags stockName initialStock capacitance
```

Example: `268 176 224 176 224 0 H_h 0 1.0`

Strings are escaped via `CustomLogicModel.escape()` (spaces → `\s`).

### MNA Implementation

The element implements the **capacitor companion model**:

**Stamp phase** (`stamp()`):
- Computes companion resistance: `R_comp = dt / (2C)` (trapezoidal) or `dt / C` (backward Euler)
- Stamps `R_comp` as a resistor from the node to ground
- During DC analysis, stamps a high resistance (1e8) instead

**Iteration start** (`startIteration()`):
- Computes current source for the companion model:
  - Trapezoidal: `I_src = -V_old / R_comp - I_old`
  - Backward Euler: `I_src = -V_old / R_comp`

**Substep** (`doStep()`):
- Stamps the computed current source from node to ground

**Step finished** (`stepFinished()`):
- Updates `stockValue` from solved `volts[0]`
- Registers value in `ComputedValues` so other elements (e.g., `SFCFlowElm` expressions) can reference it by name

### Current Calculation

```
netCurrent = volts[0] / compResistance + curSourceValue
```

`getCurrentIntoNode(0)` returns `-netCurrent` (positive = inflow into the stock).

### Visual Appearance

- Circle with capacitor symbol inside
- Stock name displayed above
- Stock value (voltage) displayed below
- Net flow indicator: green ↓ for inflow, red ↑ for outflow

### Mouse-Over Info

```
SFC Stock: H_h
Stock = 80.0 V
Net Flow = 0.00 A
Initial = 0.00 V
C = 1.0
```

### Public Accessors

| Method | Returns |
|--------|---------|
| `getStockName()` | Stock name string |
| `getStockValue()` | Current stock level (voltage) |
| `getNetCurrent()` | Net current into the stock node |

---

## SFCFlowElm (Type 269)

**Source**: [SFCFlowElm.java](../src/com/lushprojects/circuitjs1/client/SFCFlowElm.java)  
**Menu**: SFC Flow (MNA)  
**Posts**: 2 (source node and destination node)

### Purpose

Represents a named economic flow (transaction) between two stock nodes. Electrically it is a **nonlinear current source**: the flow magnitude is computed from a user-defined equation that can depend on the stock levels at either end.

### Editable Properties

| # | Property | Default | Description |
|---|----------|---------|-------------|
| 0 | Flow Name | `"Flow"` | Label for display and `ComputedValues` registry (e.g., `"Wages"`, `"Consumption"`) |
| 1 | Flow Equation | `"100"` | Expression for the flow rate. Can use `Vs`, `Vd`, `t`, and the slider variable |
| 2 | Slider Variable Name | `"rate"` | Name of the parameterizable variable in the equation |
| 3 | Slider Value | `1.0` | Current value of the slider parameter |

### Flow Equation Variables

| Variable | Meaning |
|----------|---------|
| `Vs` | Voltage (stock level) at the source node (post 0) |
| `Vd` | Voltage (stock level) at the destination node (post 1) |
| `t` | Simulation time |
| *sliderVarName* | The slider parameter (default: `rate`) |

**Examples**:
| Equation | Economic Meaning |
|----------|-----------------|
| `100` | Fixed flow of 100 per time unit |
| `0.8 * Vs` | 80% of source stock (e.g., consumption from wealth) |
| `rate * (Vs - Vd)` | Flow proportional to stock differential, scaled by slider |
| `Wages * 0.2` | 20% of a named computed value (requires `ComputedValues`) |

### File Format (Dump Type 269)

```
269 x1 y1 x2 y2 flags flowName flowEquation sliderVarName sliderValue
```

Example: `269 176 224 400 224 0 Wages 100 rate 1.0`

### MNA Implementation

This is a **nonlinear element** (`nonLinear()` returns `true`).

**Stamp phase** (`stamp()`):
- Marks both nodes as nonlinear via `sim.stampNonLinear()`

**Substep** (`doStep()`):
1. **Convergence check**: Compares current voltages against previous iteration values. If changed more than the convergence limit, sets `sim.converged = false`. The limit relaxes over iterations (0.001 → 0.01 → 0.1).

2. **Expression evaluation**: Sets up `ExprState` with `Vs`, `Vd`, slider value, and time `t`, then evaluates the compiled expression.

3. **Newton-Raphson linearization**: Computes partial derivatives numerically:
   - `∂I/∂Vs` via central difference: `(f(Vs+δ) - f(Vs-δ)) / 2δ`
   - `∂I/∂Vd` via central difference: `(f(Vd+δ) - f(Vd-δ)) / 2δ`

4. **Matrix stamping**: Stamps the linearized current source:
   - VCCS terms via `stampMatrix()` for voltage-dependent contributions
   - Constant term: `I₀ - (∂I/∂Vs)·Vs - (∂I/∂Vd)·Vd` via `stampCurrentSource()`

**Fallback**: If the expression is invalid (`compiledExpr == null`), stamps a high resistance (1e8) between nodes to avoid a singular matrix.

**Step finished** (`stepFinished()`):
- Registers flow value in `ComputedValues` by name
- Updates `ExprState` for `integrate()`/`diff()` functions

### Expression Parsing

The `parseExpression()` method translates the user equation into the internal `Expr` format:
- `Vs` → `_a` (maps to `exprState.values[0]`)
- `Vd` → `_b` (maps to `exprState.values[1]`)
- *sliderVarName* → `_c` (maps to `exprState.values[2]`)

Parse errors are logged to the browser console via `CirSim.console()`.

### Connection Behavior

`getConnection(n1, n2)` returns `false` — the element is a current source, not a conductor. There is no galvanic connection between the two nodes.

### Current Direction Convention

| Node | `getCurrentIntoNode()` |
|------|----------------------|
| 0 (source) | `-flowValue` (current leaves) |
| 1 (destination) | `+flowValue` (current enters) |

Positive `flowValue` = flow from post 0 to post 1.

### Visual Appearance

- Line between posts with an arrow showing flow direction (reverses if flow is negative)
- Circle at midpoint (current source symbol)
- Flow name displayed above
- Flow value displayed below
- Animated dots show flow direction and magnitude

### Mouse-Over Info

```
SFC Flow: Wages
Flow = 100 A
Equation: 100
Vs = 0.00 V
Vd = 0.00 V
rate = 1.0
```

### Public Accessors

| Method | Returns |
|--------|---------|
| `getFlowName()` | Flow name string |
| `getFlowValue()` | Current computed flow (current) |
| `getSliderValue()` | Slider parameter value |
| `setSliderValue(v)` | Set slider parameter programmatically |

---

## How They Work Together

### KCL = SFC Accounting Identity

At every `SFCStockElm` node, the MNA solver enforces:

$$C \frac{dV}{dt} = \sum I_{in} - \sum I_{out}$$

Which is exactly:

$$\frac{d(\text{Stock})}{dt} = \sum \text{Inflows} - \sum \text{Outflows}$$

Multiple `SFCFlowElm` elements can connect to the same stock node. KCL sums all flows automatically.

### Cross-Element References

Both elements register their values in the `ComputedValues` registry each timestep:
- `SFCStockElm` registers its stock level (voltage) under `stockName`
- `SFCFlowElm` registers its flow value (current) under `flowName`

This allows flow equations to reference other flows or stock levels by name.

### Example: 2-Sector Household-Firm Model

**Elements needed:**
1. `SFCStockElm` named `"H_h"` (Household savings, C=1.0)
2. `SFCStockElm` named `"H_f"` (Firm deposits, C=1.0)
3. `SFCFlowElm` named `"Wages"` with equation `"100"` (Firms → Households)
4. `SFCFlowElm` named `"Consumption"` with equation `"0.8 * Vs"` (Households → Firms)

**Behavior:**
- Wages flow adds 100/s to Household stock, removes 100/s from Firm stock
- Consumption flow drains 80% of Household stock per second into Firms
- KCL ensures: `dH_h/dt = Wages - Consumption` and `dH_f/dt = Consumption - Wages`
- Steady state: `H_h* = Wages / 0.8 = 125`, `H_f* = -125` (or adjust with initial conditions)

### Simulation Loop Timing

| Phase | SFCStockElm | SFCFlowElm |
|-------|-------------|------------|
| `stamp()` | Stamp companion resistor | Mark nodes nonlinear |
| `startIteration()` | Compute companion current source | — |
| `doStep()` (per subiteration) | Stamp current source | Evaluate equation, linearize, stamp |
| `stepFinished()` | Update stock, register in ComputedValues | Register flow in ComputedValues |

---

## Circuit File Examples

- [econ_SIM_MNA.txt](../src/com/lushprojects/circuitjs1/public/circuits/econ_SIM_MNA.txt) — Basic G&L SIM model
- [econ_SIM_MNA_Full.txt](../src/com/lushprojects/circuitjs1/public/circuits/econ_SIM_MNA_Full.txt) — Full algebraic version
- [sfcr-sim-mna-model.txt](../tests/sfcr-sim-mna-model.txt) — Test file with SFCR notation

---

## Registration

Both elements are registered in:
- **`CirSim.createCe()`**: `case 268` → `SFCStockElm`, `case 269` → `SFCFlowElm`
- **`CirSim.constructElement()`**: String name lookup for menu creation
- **`menulist.txt`**: `SFCStockElm|SFC Stock (MNA)` and `SFCFlowElm|SFC Flow (MNA)`
