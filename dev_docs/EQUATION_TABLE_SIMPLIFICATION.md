# EquationTableElm Row Simplification

## Overview

EquationTableElm classifies each row into one of three categories to minimize matrix size and avoid unnecessary nonlinear iteration. This can dramatically reduce simulation cost for models with many parameter definitions and market-clearing aliases.

## Visual Indicators

Each row displays a colored icon indicating its classification:

| Icon | Color | Classification | Meaning |
|------|-------|---------------|---------|
| → | Gray | Alias | Shares node with target (no matrix entry) |
| ● | Blue | Constant | Fixed value (stamped once) |
| L | Green | Linear | VCVS (no iteration needed) |
| ⟳ | Orange | Dynamic | Evaluated each timestep |

The classification is also shown in the hover tooltip when you mouse over a row.

## Row Classifications

Each row's compiled expression is analyzed at circuit setup time. The classification determines how the row participates in the MNA matrix.

### 1. Alias Rows — No Matrix Entry

**Pattern:** `Cs ~ Cd` (bare node reference, `E_NODE_REF`)

**Detection:** `Expr.isNodeAlias()` returns `true` when the expression is a single `E_NODE_REF` with no operations or children.

**Optimization:** The output name is registered in `LabeledNodeElm.labelList` pointing to the **same node number** as the target. No voltage source, no internal node, no matrix row.

**Savings per alias row:** 2 matrix rows eliminated (voltage source row + node row), plus the load resistor stamp.

**Example — MarketClearing table:**
```
Cs ~ Cd    → ALIAS: Cs points to Cd's node
Is ~ Id    → ALIAS: Is points to Id's node
Ns ~ Nd    → ALIAS: Ns points to Nd's node
Ls ~ integrate(diff(Ld))  → DYNAMIC (has integrate/diff)
```

Three alias rows save 6 matrix rows.

### 2. Constant Rows — Linear Stamp

**Pattern:** `rl ~ 0.025` (pure literal values and math, `E_VAL` tree)

**Detection:** `Expr.isConstant()` recursively checks that all leaf nodes are `E_VAL` and all operations are pure math (arithmetic, trig, etc.). Returns `false` for any time-dependent or variable-referencing nodes.

**Optimization:** The value is evaluated once during `stamp()` and baked into the matrix as a linear DC voltage source via `stampVoltageSource(0, node, vs, value)`. No `stampNonLinear()`, no `doStep()` work.

**Effect:** Rows don't force `lsChanges` or `rsChanges` flags, making them eligible for CirSim's matrix simplification pass. If the node has only one nonzero non-const entry, the simplifier can mark it `ROW_CONST` and remove it entirely.

**Example — s_Parameters table:**
```
rl ~ 0.025        → CONSTANT: stamped as V=0.025
\alpha0 ~ 20      → CONSTANT: stamped as V=20
\alpha1 ~ 0.75    → CONSTANT: stamped as V=0.75
\delta ~ 0.1      → CONSTANT: stamped as V=0.1
pr ~ 1            → CONSTANT: stamped as V=1
```

When **all** rows in a table are constant, `nonLinear()` returns `false`, preventing the table from forcing the entire circuit into nonlinear mode.

### 3. Linear Rows — VCVS Stamp

**Pattern:** Linear combinations of node references with constant coefficients.

**Detection:** `Expr.getLinearTerms(terms)` extracts coefficients for each referenced node. Returns non-null if all operations preserve linearity.

**Optimization:** Stamped as a Voltage-Controlled Voltage Source (VCVS) — the output is a linear function of input node voltages, computed by the matrix solver without iterative evaluation.

**Effect:** No `doStep()` evaluation needed. The matrix equation directly encodes the linear relationship, so the solver computes the correct output in one pass.

#### What Qualifies as Linear

| Pattern | Example | Coefficients Extracted |
|---------|---------|------------------------|
| Node reference | `Cs` | Cs: 1.0, const: 0 |
| Constant × node | `2*Cs`, `Cs*0.5` | Cs: 2.0 or 0.5 |
| Node / constant | `Cs/2` | Cs: 0.5 |
| Node + constant | `Cs + 10` | Cs: 1.0, const: 10 |
| Sum of nodes | `Cs + Is` | Cs: 1.0, Is: 1.0 |
| Linear combinations | `2*Cs - 3*Is + 5` | Cs: 2.0, Is: -3.0, const: 5 |
| Unary minus | `-Cs` | Cs: -1.0 |
| Nested linear | `(Cs + Is)/2` | Cs: 0.5, Is: 0.5 |

#### What Is NOT Linear

| Pattern | Example | Why |
|---------|---------|-----|
| Node × node | `Cs * Is` | Product of variables |
| Node / node | `Cs / Is` | Division by variable |
| Functions of nodes | `sin(Cs)`, `exp(Is)` | Non-linear functions |
| Time-dependent | `t`, `sin(t)` | Changes each timestep |
| integrate/diff | `integrate(Cs)` | Stateful functions |
| lag | `lag(Cs, 0.1)` | History-dependent |

**Example — Linear row in action:**
```
Y ~ Cs + Is        → LINEAR: VCVS with Cs coef=1, Is coef=1
Y ~ 2*Cd - Mh/4    → LINEAR: VCVS with Cd coef=2, Mh coef=-0.25
```

#### Deferred VCVS Stamping (postStamp)

Linear VCVS stamping requires the referenced nodes to already exist. Due to element ordering, a table might stamp before another table that creates the referenced nodes.

**Solution:** The `postStamp()` mechanism:

1. During `stamp()`: Check if all referenced nodes exist
2. If yes: Stamp VCVS coefficients immediately
3. If no: Defer to `postStamp()` (called after ALL elements have run `stamp()`)
4. In `postStamp()`: Nodes now exist → stamp VCVS coefficients
5. If still missing: Node is in ComputedValues (pure computational), fall back to nonlinear

This eliminates the need to force nonlinear iteration just because of stamp ordering.

### 4. Dynamic Rows — Nonlinear doStep()

**Pattern:** Everything else — node references, `integrate()`, `diff()`, `lag()`, `t`, arithmetic on variables.

**Examples:**
```
Y ~ Cs + Is                    → DYNAMIC (E_NODE_REF children)
Mh ~ Mh_init + integrate(YD - Cd)  → DYNAMIC (integrate)
KK ~ t                         → DYNAMIC (E_T)
Cd ~ \alpha0 + \alpha1 * YD    → DYNAMIC (E_NODE_REF: YD)
```

**Behavior:** Standard nonlinear stamping — `stampNonLinear(vn)` in `stamp()`, value computed and stamped via `stampRightSide(vn, value)` in `doStep()` each subiteration.

## Classification Priority

Checked in order:
1. **Alias** — bare `E_NODE_REF` with no initial equation → `isAliasRow[row] = true`
2. **Constant** — pure `E_VAL` tree with no initial equation → `isConstantRow[row] = true`
3. **Linear** — linear combination of node refs with no initial equation → `isLinearRow[row] = true`
4. **Dynamic** — everything else

Rows with initial equations (`@init` values) are always dynamic, since they require special t=0 handling.

## Impact on Matrix Size

For the BMW model (1debug.txt):

| Table | Rows | Alias | Constant | Linear | Dynamic | Matrix Rows Saved |
|-------|------|-------|----------|--------|---------|-------------------|
| s_Parameters | 8 | 0 | 8 | 0 | 0 | 8+ (simplifiable) |
| MarketClearing | 4 | 3 | 0 | 0 | 1 | 6 |
| Firms | 8 | 0 | 0 | 2 | 6 | 0 (but 2 rows avoid doStep) |
| Households | 2 | 0 | 0 | 0 | 2 | 0 |
| Banks | 2 | 0 | 0 | 0 | 2 | 0 |
| WageBill | 3 | 0 | 0 | 0 | 3 | 0 |
| Consumption | 1 | 0 | 0 | 0 | 1 | 0 |
| Investment | 4 | 0 | 0 | 0 | 4 | 0 |
| Verify | 4 | 0 | 0 | 0 | 4 | 0 |

**Total: 3 alias rows → 6 fewer matrix rows; 8 constant rows → eligible for simplification; linear rows avoid doStep iteration.**

Additionally, if the only nonlinear rows are in some tables and others are all constant/alias, those pure tables won't force `circuitNonLinear = true` on their own.

## How CirSim Matrix Simplification Works

After stamping, `simplifyMatrix()` scans each matrix row:

1. **Skip rows with `lsChanges` or `rsChanges`** — these are nonlinear or have changing right-hand sides
2. For each remaining row, count non-zero non-const columns
3. If **exactly one** non-zero non-const entry exists → solve for that variable as a constant (`ROW_CONST`)
4. Drop the row from the matrix (`dropRow = true`)
5. Repeat until no more rows can be eliminated

**Key flags set during stamping:**
- `stampNonLinear(vn)` → `circuitRowInfo[vn].lsChanges = true` (prevents simplification)
- `stampRightSide(vn)` (no value) → `circuitRowInfo[vn].rsChanges = true` (prevents simplification)
- `stampVoltageSource(n1, n2, vs, value)` → stamps value directly, no flags → **eligible for simplification**

### Why Constant Rows Are Simplifiable

A constant row stamps:
```java
sim.stampVoltageSource(0, nodeNum, voltSource + vs, constantValue);
```

This creates a voltage source row with equation: `V(node) = constantValue`. Since neither `stampNonLinear` nor `stampRightSide()` (no-arg) is called, the row has `lsChanges = false` and `rsChanges = false`. The simplifier can:
1. Detect row has one non-zero non-const entry
2. Mark the node as `ROW_CONST` with the solved value
3. Fold the constant into other rows' right-hand sides
4. Drop the row entirely

### Why Alias Rows Are Optimal

Alias rows create **zero matrix entries** — they only register a name→node mapping. The output name and target name share the same node number, so any other element referencing either name gets the same node. This is equivalent to a wire in circuit terms.

## Implementation Details

### Expr Methods

```java
// Returns true for bare E_NODE_REF (e.g., compiled from "Cd")
boolean isNodeAlias()

// Returns the referenced node name (e.g., "Cd")
String getNodeName()

// Returns true for pure E_VAL trees (e.g., compiled from "0.025" or "2 * 3.14")
boolean isConstant()

// Extracts linear terms: nodeName → coefficient. Returns constant term, or null if not linear.
// Example: "2*Cs - 3*Is + 5" → terms={Cs:2.0, Is:-3.0}, returns 5.0
Double getLinearTerms(HashMap<String, Double> terms)
```

### CircuitElm Methods

```java
// Called during analysis after all elements have had stamp() called.
// Use for deferred stamping that depends on nodes created by other elements.
void postStamp()  // Default: empty. Override to handle deferred work.
```

### EquationTableElm Flow

```
parseAllEquations()
    ↓
updateRowClassifications()  ← Sets isAliasRow[], isConstantRow[], isLinearRow[], linearTerms[]
    ↓
getVoltageSourceCount()     ← Excludes alias rows
getInternalNodeCount()      ← Excludes alias rows
    ↓
stamp()
  ├─ findLabeledNodes()     ← Skips alias rows (no node allocation)
  ├─ registerAliasNodes()   ← Points alias names to target nodes
  ├─ Constant rows: stampVoltageSource(0, node, vs, value)
  ├─ Linear rows: stampVoltageSource + stampVCVS (or defer to postStamp)
  └─ Dynamic rows: stampNonLinear(vn) + stampVoltageSource(0, node, vs)
    ↓
postStamp()                 ← Stamps deferred VCVS coefficients for linear rows
    ↓
doStep()                    ← Skips alias + constant + linear rows
    ↓
stepFinished()
  ├─ Alias rows: read target node voltage, register in ComputedValues
  ├─ Linear rows: read computed voltage, register in ComputedValues
  └─ Dynamic rows: commit integration, update state
```

### MNA vs Pure Computational Mode

| Mode | Alias | Constant | Linear | Dynamic |
|------|-------|----------|--------|---------|
| **MNA** | Name→node alias in labelList | DC voltage source in matrix | VCVS in matrix | NonLinear doStep |
| **Pure** | Copy target value in doStep | Set ComputedValue in stamp | Evaluate in doStep | Evaluate + set ComputedValue |

Note: In Pure mode, linear rows are evaluated dynamically since there's no MNA matrix to encode the VCVS relationship.

## Related Files

- [Expr.java](../src/com/lushprojects/circuitjs1/client/Expr.java) — `isConstant()`, `isNodeAlias()`, `getNodeName()`
- [EquationTableElm.java](../src/com/lushprojects/circuitjs1/client/EquationTableElm.java) — Row classification and optimized stamping
- [CirSim.java](../src/com/lushprojects/circuitjs1/client/CirSim.java) — `simplifyMatrix()`, `stampVoltageSource()`, `stampNonLinear()`
- [RowInfo.java](../src/com/lushprojects/circuitjs1/client/RowInfo.java) — `ROW_CONST`, `lsChanges`, `rsChanges`, `dropRow`
- [MultiplyConstElm.java](../src/com/lushprojects/circuitjs1/client/MultiplyConstElm.java) — Example of a linear VCVS element for comparison
