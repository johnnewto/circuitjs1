# MNA Matrix Explanation: 1debug.txt

## 1Debug.txt
```
$ 1 0.1 1.4391916095149893 33 5 43 5e-11
% voltageUnit $
% showToolbar true
266 128 176 227 220 0 Current.Demo 1 rate 3.3 \0 r 0.5 0 \0 1
268 304 -128 304 -96 0 From_s 0 1
431 80 -176 112 -144 0 100 true false
269 304 -128 464 -128 0 Flow 1 rate 1
268 464 -128 464 -96 0 To_s 0 1
% AST 0 2

```
The circuit has:

An EquationTableElm ("Current.Demo") with 1 row outputting "rate" = 3.3 in VOLTAGE_MODE

- SFCStockElm "From_s" - a stock (capacitor to ground)
- SFCFlowElm "Flow" - a flow between From_s and To_s, with equation "1"
- SFCStockElm "To_s" - another stock (capacitor to ground)
- StopTimeElm - no electrical impact

## Matrix Equation: X = A⁻¹ B

Matrix dimensions: **4 x 4**

Composition: **3** node rows (nodes 1..3, ground node 0 omitted) + **1** voltage source rows

### A Matrix (Admittance)

|  | n1 (rate,int) | n2 ($From_s, SFCStock$) | n3 (Flow, SFCFlow) | vs0 (rate) | 
|---|---|---|---|---|
| **n1 (rate,int)** | 1e-9 | 0 | 0 | -1 | 
| **n2 ($From_s, SFCStock$)** | 0 | 20 | 0 | 0 | 
| **n3 (Flow, SFCFlow)** | 0 | 0 | 20 | 0 | 
| **vs0 (rate)** | 1 | 0 | 0 | 0 | 

### X (Solution) and B (Right Side) Vectors

*(Note: X is read from `nodeVoltages[]` and VS element currents, not from `circuitRightSide` which holds B after nonlinear convergence. B shows the full right-hand side including `doStep()` stamps.)*

| Row | Label | Meaning | X (solution) | B (right side) |
|-----|-------|---------|-------------|----------------|
| 0 | n1 (rate,int) | Node voltage (V) | 3.3 | ~0 |
| 1 | n2 ($From_s, SFCStock$) | Node voltage (V) | -100.1 | ~-2001 |
| 2 | n3 (Flow, SFCFlow) | Node voltage (V) | 100.1 | ~2001 |
| 3 | vs0 (rate) | VS current (A) | ~0 | 3.3 |

**Legend:** X contains node voltages (V) and VS currents (A). B contains current source values (A) for node rows and voltage source values (V) for VS rows.

## Circuit Elements

| Element | Type | Dump | Role |
|---------|------|------|------|
| **Current.Demo** | `EquationTableElm` | 266 | Outputs `rate = 3.3` via a voltage source + internal node |
| **From_s** | `SFCStockElm` | 268 | Stock (capacitor to ground) — the source account |
| **To_s** | `SFCStockElm` | 268 | Stock (capacitor to ground) — the destination account |
| **Flow** | `SFCFlowElm` | 269 | Voltage-dependent current source between From_s → To_s, equation = `rate` |
| StopTimeElm | Control | 431 | Stops sim at t=100; no MNA footprint |

## Matrix Equation: X = A⁻¹ B

Matrix dimensions: **4 × 4**

Composition: **3** node rows (nodes 1..3, ground node 0 omitted) + **1** voltage source row

## Nodes & Voltage Sources

| Label | What it is |
|-------|------------|
| **n1 (rate, int)** | Internal node created by `EquationTableElm` for the "rate" output |
| **n2 (From_s, SFCStock)** | Single post of `SFCStockElm` "From_s" |
| **n3 (Flow, SFCFlow)** | Post of `SFCStockElm` "To_s" (labeled via `SFCFlowElm` appearing first in the node's link list — same electrical node) |
| **vs0 (rate)** | Voltage source from `EquationTableElm`, forcing n1 to the computed value of "rate" |

## A Matrix (Admittance) — Entry by Entry

### A Matrix

|  | n1 (rate,int) | n2 (From_s, SFCStock) | n3 (Flow, SFCFlow) | vs0 (rate) |
|---|---|---|---|---|
| **n1 (rate,int)** | 1e-9 | 0 | 0 | -1 |
| **n2 (From_s, SFCStock)** | 0 | 20 | 0 | 0 |
| **n3 (Flow, SFCFlow)** | 0 | 0 | 20 | 0 |
| **vs0 (rate)** | 1 | 0 | 0 | 0 |

### Diagonal Entries

| Entry | Value | Source |
|-------|-------|--------|
| **A[0][0] = 1e-9** | Tiny conductance (n1 → ground) | `EquationTableElm` stamps a 10⁹ Ω isolation resistor on its internal node to keep the matrix non-singular |
| **A[1][1] = 20** | Companion-model conductance (From_s → ground) | `SFCStockElm` models a capacitor (C=1) using trapezoidal integration: R_comp = dt/(2C) = 0.1/2 = 0.05 Ω, so G = 20 S |
| **A[2][2] = 20** | Same companion conductance (To_s → ground) | Identical stamp from the second `SFCStockElm` |

### Voltage-Source Stamps

| Entry | Value | Meaning |
|-------|-------|---------|
| **A[0][3] = −1** | VS relationship at the node row | Standard MNA stamp: the VS column gets −1 in the row of its positive node (n1) |
| **A[3][0] = 1** | VS equation row | Encodes V_n1 = B[3]: whatever value is stamped into B[3] during `doStep()` becomes the forced voltage |

### Off-Diagonal Zeros

All other entries are **zero**. Notably for `SFCFlowElm`: it stamps linearized partial derivatives ∂I/∂V into off-diagonals during `doStep()`. But its equation is `rate` — a lookup of the table output, **not a function of the stock voltages** — so both ∂/∂V_From_s and ∂/∂V_To_s are zero. No coupling terms appear.

## B Vector (Right-Hand Side)

The B vector (`circuitRightSide`) contains both the structural part (`origRightSide`, all zeros here) and nonlinear stamps from `doStep()`. At convergence, for this circuit:

| Row | Value | What stamps it |
|-----|-------|---------------|
| B[0] (rate) | ~0 | Tiny leakage from 10⁹Ω isolation resistor |
| B[1] (From_s) | ~−2001 | Companion current source (−V_old/R_comp − I_old) **+ flow current** (−1, current leaving) |
| B[2] (To_s) | ~+2001 | Companion current source (−V_old/R_comp − I_old) **+ flow current** (+1, current entering) |
| B[3] (vs0) | 3.3 | Voltage source value from `EquationTableElm` |

The large B values (~±2001) are dominated by the companion model: $-V_{old}/R_{comp} = -(\pm100.1)/0.05 = \mp2002$, offset by the companion current term.
| B[3] (vs0) | Voltage value 3.3 from `EquationTableElm` |

## X Vector — Solution Interpretation

| Row | Value | Meaning |
|-----|-------|---------|
| V(n1) = 3.3 | Rate node | Voltage forced by EquationTableElm's voltage source to the value of "rate" |
| V(n2) = −100.1 | From_s balance | Stock drained by flow of 1 unit/s over ~100.1s |
| V(n3) = +100.1 | To_s balance | Stock filled: symmetric accumulation |
| I(vs0) ≈ 0 | VS current | Negligible current through the high-impedance rate node |

The symmetric ±2001 values confirm conservation: every unit leaving From_s arrives at To_s. This is the double-entry bookkeeping enforced by `SFCFlowElm` acting as a current source between the two stock-capacitors.
