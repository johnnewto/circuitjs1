# Using MNA to Model Stock-Flow Consistent (SFC) Economic Models with Current as Flow

## Executive Summary

This document investigates how Modified Nodal Analysis (MNA), as implemented in CircuitJS1, can be leveraged to model Stock-Flow Consistent (SFC) economic models using electrical circuit analogies where **current represents economic flows** and **voltage represents stock levels (prices/incentives)**.

## The Economic-Electrical Analogy

### Core Mappings

| Electrical Concept | Economic Meaning | Mathematical Relation |
|--------------------|------------------|----------------------|
| **Current (I)** | Economic flows (goods, money, labor) | Flow rate ($/s, units/s) |
| **Voltage (V)** | Price, utility, incentive | Stock level or potential |
| **Capacitor** | Stock accumulation | $q = \int I \, dt$ (stock = integral of flow) |
| **Inductor** | Demand inertia, reservation price | $V = L \frac{dI}{dt}$ (price resists flow changes) |
| **Resistor** | Transaction costs, friction | $V = IR$ (price proportional to flow) |
| **KCL (Kirchhoff's Current Law)** | Flow conservation | Inflows = Outflows + ΔStock |
| **KVL (Kirchhoff's Voltage Law)** | Price/incentive balance | Sum of price differentials = 0 around loops |

### Why This Mapping is Powerful

1. **KCL enforces SFC accounting identity**: Every flow must go somewhere - no "black holes"
2. **KVL ensures market clearing**: Price differentials balance across economic cycles
3. **Capacitors naturally model stock accumulation**: $S(t) = S_0 + \int_0^t F(\tau) d\tau$
4. **The MNA solver automatically enforces all conservation laws**

## Current CircuitJS1 Implementation Analysis

### Existing Approach (GodlyTableElm)

The current implementation uses **voltage as the primary variable**:
- Stocks are represented as voltages at nodes
- Flows are computed as expressions and integrated
- Voltage sources drive stock values

```java
// GodlyTableElm: stamps voltage source to drive stock value
sim.stampVoltageSource(0, nodeNum, voltSource + vs);
sim.stampRightSide(vn, integratedValue);  // Direct stamp of integrated value
```

**Limitation**: Current is a secondary quantity - it's not the fundamental flow variable.

### MNA Stamp Methods Available

```java
// Current sources - stamp flows directly into KCL equations
void stampCurrentSource(int n1, int n2, double i);

// Voltage-controlled current source - flows proportional to price/voltage
void stampVCCurrentSource(int cn1, int cn2, int vn1, int vn2, double g);

// Current-controlled current source - flows proportional to other flows
void stampCCCS(int n1, int n2, int vs, double gain);

// Resistor - relates flow to price via conductance
void stampResistor(int n1, int n2, double r);

// Voltage source - constrains stock levels
void stampVoltageSource(int n1, int n2, int vs, double v);
```

## Proposed MNA-Based SFC Architecture

### Design Philosophy

**Use current as the fundamental flow variable, with stocks emerging naturally from capacitor integration.**

### Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                    SFC Economic Model in MNA                         │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Sector Nodes (voltage = account balance / stock level)              │
│       ●─────────────────●─────────────────●─────────────────●        │
│    Households       Firms           Banks           Govt             │
│                                                                      │
│  Transactions (current sources between nodes)                        │
│       →─────Wages────→                                               │
│       ←────Consumption──←                                            │
│       →─────Taxes────────────────────────────────────→               │
│       ←───────────────Spending────────────────────────←              │
│                                                                      │
│  Each sector has a "stock capacitor" to ground:                      │
│       ●                ●                 ●                ●          │
│       │                │                 │                │          │
│      ═══ C_h          ═══ C_f           ═══ C_b          ═══ C_g     │
│       │                │                 │                │          │
│      ⏚                ⏚                 ⏚                ⏚           │
│                                                                      │
│  KCL at each node enforces: ΣFlows_in = ΣFlows_out + C·dV/dt        │
│  This IS the SFC accounting identity!                                │
└──────────────────────────────────────────────────────────────────────┘
```

### Component Mapping for SFC

#### 1. Stock Capacitors (Account Balances)

Each economic sector has a capacitor representing its stock (money balance, inventory, etc.):

```java
// Capacitor companion model: I = C·dV/dt
// Discretized: I = (C/dt)·(V_new - V_old)
// MNA: Stamp as resistor + current source

void stampStockCapacitor(int sectorNode, double capacitance) {
    // Companion model resistance
    double compResistance = sim.timeStep / capacitance;
    sim.stampResistor(sectorNode, 0, compResistance);
    sim.stampRightSide(sectorNode);  // Mark for doStep update
}

void doStepStockCapacitor(int sectorNode, double capacitance, double lastVoltage) {
    // Current source for trapezoidal integration
    double compResistance = sim.timeStep / capacitance;
    double curSource = -lastVoltage / compResistance;
    sim.stampCurrentSource(sectorNode, 0, curSource);
}
```

**Key insight**: The capacitor's current IS the net flow into the sector. The voltage IS the stock level. MNA automatically ensures flow conservation.

#### 2. Transaction Flows (Current Sources)

Transaction flows between sectors are current sources, potentially controlled by prices (voltages):

```java
// Fixed flow transaction (e.g., fixed wages)
sim.stampCurrentSource(householdNode, firmNode, wageFlow);

// Price-sensitive flow (e.g., consumption depends on price)
// I = G · (V_goods - V_money)
sim.stampVCCurrentSource(householdNode, firmNode, goodsPriceNode, moneyNode, marginalPropensity);

// Flow proportional to another flow (e.g., taxes as % of income)
sim.stampCCCS(govtNode, householdNode, incomeVoltSource, taxRate);
```

#### 3. Price/Incentive Constraints (Voltage Sources)

Central bank rates, fixed prices, or policy constraints:

```java
// Central bank sets interest rate (voltage)
sim.stampVoltageSource(0, interestRateNode, vs, 0.05);

// Fixed price level
sim.stampVoltageSource(0, priceNode, vs, 1.0);
```

#### 4. Transaction Costs/Friction (Resistors)

Resistors model friction or gradual price adjustment:

```java
// Market friction: flow proportional to price differential
sim.stampResistor(sellerNode, buyerNode, transactionCostResistance);
// I = (V_seller - V_buyer) / R
```

### New Element: SFCFlowElm (Proposed)

```java
/**
 * SFCFlowElm - Stock-Flow Consistent Transaction Element
 * 
 * Uses current as the fundamental flow variable.
 * Connects two sector nodes and stamps current based on equation.
 * 
 * Flow equation can reference:
 * - Source/destination stock levels (voltages)
 * - Other flow values (currents through labeled flows)
 * - Time, parameters
 */
class SFCFlowElm extends CircuitElm {
    String flowName;          // e.g., "Wages", "Consumption"
    String flowEquation;      // e.g., "0.8 * Income" or "propensity * (V_seller - V_buyer)"
    Expr compiledExpr;
    int sourceNode, destNode;
    double currentFlow;       // Computed flow value
    
    @Override
    void stamp() {
        // Mark as nonlinear (equation may depend on voltages)
        sim.stampNonLinear(nodes[0]);
        sim.stampNonLinear(nodes[1]);
    }
    
    @Override
    void doStep() {
        // Evaluate flow equation with current voltages
        exprState.values[0] = volts[0];  // Source stock level
        exprState.values[1] = volts[1];  // Dest stock level
        exprState.t = sim.t;
        
        currentFlow = compiledExpr.eval(exprState);
        
        // Stamp current source for the flow
        sim.stampCurrentSource(nodes[0], nodes[1], currentFlow);
        
        // Newton-Raphson: compute derivatives for faster convergence
        double dI_dV0 = computeDerivative(0);
        double dI_dV1 = computeDerivative(1);
        
        // Stamp linearized terms: I ≈ I_0 + (dI/dV0)·ΔV0 + (dI/dV1)·ΔV1
        if (Math.abs(dI_dV0) > 1e-10) {
            sim.stampMatrix(nodes[0], nodes[0], dI_dV0);
            sim.stampMatrix(nodes[1], nodes[0], -dI_dV0);
            sim.stampCurrentSource(nodes[0], nodes[1], -dI_dV0 * volts[0]);
        }
        if (Math.abs(dI_dV1) > 1e-10) {
            sim.stampMatrix(nodes[0], nodes[1], dI_dV1);
            sim.stampMatrix(nodes[1], nodes[1], -dI_dV1);
            sim.stampCurrentSource(nodes[0], nodes[1], -dI_dV1 * volts[1]);
        }
    }
    
    @Override
    double getCurrentIntoNode(int n) {
        return (n == 0) ? -currentFlow : currentFlow;
    }
}
```

### New Element: SFCSectorElm (Proposed)

```java
/**
 * SFCSectorElm - Economic Sector with Stock Capacitor
 * 
 * Represents an economic agent (household, firm, bank, govt).
 * Has a stock capacitor that accumulates flows.
 * The voltage at the node represents the stock level.
 */
class SFCSectorElm extends CircuitElm {
    String sectorName;
    double initialStock;      // Initial balance
    double stockCapacitance;  // How quickly stock responds to flows
    double compResistance;
    double voltdiff;
    
    @Override
    int getPostCount() { return 1; }  // Single node representing the sector
    
    @Override
    void reset() {
        voltdiff = initialStock;
    }
    
    @Override
    void stamp() {
        // Capacitor companion model
        compResistance = sim.timeStep / stockCapacitance;
        sim.stampResistor(nodes[0], 0, compResistance);
        sim.stampRightSide(nodes[0]);
    }
    
    @Override
    void startIteration() {
        // Trapezoidal current source
        double curSourceValue = -voltdiff / compResistance - current;
    }
    
    @Override
    void doStep() {
        sim.stampCurrentSource(nodes[0], 0, curSourceValue);
    }
    
    @Override
    void stepFinished() {
        voltdiff = volts[0];  // Stock level = voltage
        calculateCurrent();
    }
}
```

## Advantages of Current-as-Flow MNA Approach

### 1. Automatic Conservation (KCL)

MNA inherently enforces KCL at every node:
$$\sum I_{in} = \sum I_{out} + C \frac{dV}{dt}$$

For an economic sector, this IS the SFC identity:
$$\sum \text{Inflows} = \sum \text{Outflows} + \Delta\text{Stock}$$

**No additional enforcement needed** - it's built into the matrix structure.

### 2. Natural Stock Integration

Capacitors automatically integrate flows:
$$V(t) = V_0 + \frac{1}{C}\int_0^t I(\tau) d\tau$$
$$\text{Stock}(t) = \text{Stock}_0 + \int_0^t \text{Flow}(\tau) d\tau$$

The MNA solver handles the discretization (trapezoidal or backward Euler).

### 3. Behavioral Equations via VCCS

Price-sensitive consumption, interest payments, etc., map to voltage-controlled current sources:

```
Consumption = MPC × (Wealth - TargetWealth)
     ↓
I_consumption = G × (V_wealth - V_target)
```

### 4. Multi-Sector Coupling

The matrix structure naturally handles inter-sector flows:
- Banks lend to firms → current from Bank node to Firm node
- Firms pay workers → current from Firm node to Household node
- Households deposit in banks → current from Household node to Bank node

All flows balance automatically via KCL.

### 5. Visualizing Flows

Current animation (dots moving along wires) directly visualizes economic flows:
- Faster dots = higher flow rate
- Direction shows flow direction
- Can see money/goods circulating through the economy

## Implementation Roadmap

### Phase 1: Basic Current-Flow SFC Elements

1. **SFCSectorElm** - Capacitor-based sector with stock accumulation
2. **SFCFlowElm** - Configurable current source between sectors
3. **SFCPolicyElm** - Voltage source for policy constraints

### Phase 2: Behavioral Elements

4. **ConsumptionElm** - VCCS for price-sensitive consumption
5. **InvestmentElm** - Flow based on interest rate differential
6. **TaxElm** - CCCS for proportional taxation

### Phase 3: Enhanced Visualization

7. **SFCBalanceSheetView** - Display stock levels (voltages) in table form
8. **SFCFlowDiagram** - Sankey-style view derived from currents
9. **SFCTimeSeriesScope** - Plot stocks and flows over time

### Phase 4: Advanced Modeling

10. **PortfolioElm** - Multi-asset allocation based on returns (voltages)
11. **ExpectationsElm** - Adaptive expectations for prices
12. **DebtElm** - Bond/loan with principal and interest flows

## Comparison with Current Implementation

| Aspect | Current (Voltage-Centric) | Proposed (Current-Centric) |
|--------|---------------------------|----------------------------|
| **Primary variable** | Stock = Voltage | Flow = Current, Stock = Voltage |
| **Flow computation** | Expression evaluation | MNA current sources |
| **Conservation** | Manual checking | Automatic via KCL |
| **Integration** | Custom code | Capacitor companion model |
| **Multi-sector** | Explicit coordination | Matrix auto-couples |
| **Visualization** | Table values | Current animation |
| **Behavioral eq** | Any expression | VCCS/CCCS forms |

## Example: Simple 2-Sector Model (Households + Firms)

### Circuit Representation

```
         Wages (I_w)
    ●───────→────────●
    │ Households │   │ Firms  │
    │            │   │        │
   ═══ C_h      C_f ═══
    │            │   │        │
    ⏚───────────────⏚
         ←───────────
      Consumption (I_c)
```

### MNA Equations

At Household node (n_h):
$$\frac{C_h}{dt}(V_{h,new} - V_{h,old}) = I_w - I_c$$

At Firms node (n_f):
$$\frac{C_f}{dt}(V_{f,new} - V_{f,old}) = I_c - I_w$$

Where:
- $I_w = W_0$ (fixed wages)
- $I_c = c \cdot V_h$ (consumption proportional to household wealth)

### Matrix Form

$$\begin{pmatrix} G_h + c & 0 \\ -c & G_f \end{pmatrix} \begin{pmatrix} V_h \\ V_f \end{pmatrix} = \begin{pmatrix} I_{w} + G_h V_{h,old} \\ -I_w + G_f V_{f,old} \end{pmatrix}$$

Where $G_h = C_h/dt$, $G_f = C_f/dt$.

This is exactly the standard MNA form - CircuitJS1 can solve it directly!

---

## Example: G&L SIM Model (3-Sector)

The Godley & Lavoie SIM model (Chapter 3 of "Monetary Economics") demonstrates a simple 3-sector economy. Here's how it maps to the MNA current-flow framework.

### Original SFCR Equations

```
C_s ~ C_d                           # Supply = Demand identity
G_s ~ G_d                           # Govt supply = demand  
T_s ~ T_d                           # Tax identity
N_s ~ N_d                           # Labor market clearing
YD ~ W * N_s - T_s                  # Disposable income
T_d ~ θ * W * N_s                   # Tax collection
C_d ~ α₁ * YD + α₂ * H_h            # Consumption function
H_s ~ integrate(G_d - T_d)          # Govt money stock (deficit)
H_h ~ integrate(YD - C_d)           # Household money (savings)
```

### MNA Circuit Mapping

```
                 ┌─────────────────────────────┐
                 │     Firms (pass-through)    │
                 │   (no capacitor - C ≈ 0)    │
                 └──────┬──────────────┬───────┘
                        │              │
              G = 20    │              │  Wages = Y
             (current)  ↓              ↑  (current)
                        │              │
     ┌──────────────────┴──────────────┴────────────────┐
     │                                                   │
     ↓                                                   │
 ●───────●                                          ●────┴────●
 │ Govt  │                                          │  HH     │
 │       │←────────────── Taxes = θ·Y ──────────────│         │
═══ C_g                    (current)                      C_h ═══
 │       │                                          │         │
 │       │                Consumption               │         │
 │       │                 = α₁·YD + α₂·H_h         │         │
 │       │←──────────────  (current)  ──────────────┘         │
 ⏚       ⏚                                          ⏚         ⏚
```

### Key Insights

1. **Sectoral Balances via KCL**: At each sector node, KCL enforces:
   $$\sum I_{in} - \sum I_{out} = C \frac{dV}{dt}$$
   
   For Households: $\text{Wages} - \text{Taxes} - \text{Consumption} = \frac{dH_h}{dt}$
   
   For Government: $\text{Taxes} - G = \frac{dH_s}{dt}$ (negative = deficit)

2. **Automatic Balance**: The sectoral balance identity emerges naturally:
   $$\text{HH Savings} + \text{Govt Surplus} = 0$$
   This is KCL applied to the whole circuit!

3. **Steady State**: When $dH/dt = 0$, solve for equilibrium:
   $$Y^* = \frac{G}{1 - \alpha_1(1-\theta)} = \frac{20}{0.52} \approx 38.46$$
   $$H_h^* = \frac{(1-\alpha_1)(1-\theta)Y^*}{\alpha_2} = 80$$

### Circuit Files

- [econ_SIM_MNA.txt](../src/com/lushprojects/circuitjs1/public/circuits/econ_SIM_MNA.txt) - Basic version
- [econ_SIM_MNA_Full.txt](../src/com/lushprojects/circuitjs1/public/circuits/econ_SIM_MNA_Full.txt) - Full algebraic version

## Implementation Status

Two proof-of-concept elements have been created to demonstrate the current-as-flow MNA approach:

### SFCSectorElm (Type 268)
- Location: [SFCSectorElm.java](../src/com/lushprojects/circuitjs1/client/SFCSectorElm.java)
- Implements capacitor-based sector with stock accumulation
- Stock level = node voltage, Net flow = current into node
- KCL automatically enforces SFC accounting identity

### SFCFlowElm (Type 269)  
- Location: [SFCFlowElm.java](../src/com/lushprojects/circuitjs1/client/SFCFlowElm.java)
- Implements configurable current source for transactions
- Flow equation can reference source/destination stock levels
- Uses Newton-Raphson linearization for convergence

Both elements are registered in the menu under "SFC Sector (MNA)" and "SFC Flow (MNA)".

## Conclusion

Using MNA with **current as the fundamental flow variable** provides:

1. **Mathematical rigor**: Conservation laws (SFC identities) are enforced by construction
2. **Computational efficiency**: Leverages existing optimized MNA solver
3. **Intuitive visualization**: Current animation shows flows, voltage shows stocks
4. **Extensibility**: New behavioral equations via VCCS/CCCS patterns
5. **Multi-sector support**: Matrix structure handles any number of sectors

The proposed implementation adds new element types that map economic concepts directly to MNA primitives, creating a natural and powerful framework for SFC modeling within CircuitJS1.

## References

1. Godley, W. & Lavoie, M. (2007). *Monetary Economics: An Integrated Approach to Credit, Money, Income, Production and Wealth*
2. Keen, S. (2011). *Debunking Economics*
3. "Modeling Economic Systems as Multiport Networks" - Paper on electrical-economic analogies
4. Pillage, Rohrer, & Visweswariah (1999). *Electronic Circuit and System Simulation Methods* - MNA theory
