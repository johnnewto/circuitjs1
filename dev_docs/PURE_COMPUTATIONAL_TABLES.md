# Pure Computational Tables Architecture

## Overview

Stock-flow table elements now use a **mixed architecture**. Some are pure computational/display-only, while others participate in MNA without exposing visible posts. This document summarizes current behavior and recommended bridging patterns.

## Key Design Principles

### Element Modes

#### Pure computational / display-only
- `SFCTableElm`: no posts, no voltage sources, empty `stamp()`, evaluates/caches values for display.
- `EquationTableElm` when `equationTableMnaMode=false`: no MNA stamping; values are computed and published through `ComputedValues`.

#### MNA-backed with hidden node wiring
- `GodlyTableElm`: `getPostCount()==0` (no visible posts) but it allocates internal nodes/voltage sources and stamps matrix entries to drive master stocks.
- `EquationTableElm` when `equationTableMnaMode=true`: stamps per-row outputs (`VOLTAGE_MODE`, `FLOW_MODE`, `STOCK_MODE`) using voltage/current-source behavior.

### Benefits

1. **Flexible modeling**: choose matrix-coupled rows when conservation coupling matters, or pure mode for lightweight computation.
2. **Cleaner separation**: display/computation-only elements avoid unnecessary matrix complexity.
3. **Registry integration**: `ComputedValues` still provides cross-element name/value sharing.
4. **Stable display reads**: converged-value buffering avoids subiteration flicker.

### Elements Affected

| Element | Type | Notes |
|---------|------|-------|
| GodlyTableElm | MNA-backed (hidden posts) | Drives master stock nodes via voltage-source stamping |
| EquationTableElm | Dual mode | Pure or MNA mode, with per-row output modes |
| SFCTableElm | Pure computational/display | Evaluates table values without circuit stamping |

### Base TableElm

`TableElm` (the base class) still has electrical posts and MNA participation for backward compatibility. Subclasses override to be pure computational.

## Bridging to Electrical Domain

There are **two ways** to connect computed values to scopes and the electrical domain:

### Method 1: Labeled Nodes (Recommended for Scopes)

`LabeledNodeElm` already integrates with `ComputedValues`:

1. Create a `LabeledNodeElm` with the same name as a computed value (e.g., "Firms")
2. Connect a scope to the labeled node
3. The scope's `getScopeValue()` automatically reads from `ComputedValues.getConvergedValue()`

**How it works** (from [LabeledNodeElm.java](../src/com/lushprojects/circuitjs1/client/LabeledNodeElm.java#L288)):
```java
double getScopeValue(int x) {
    // For voltage: prefer converged computed value for stable scope display
    Double computedValue = ComputedValues.getConvergedValue(text);
    if (computedValue != null) {
        return computedValue.doubleValue();
    }
    return volts[0];
}
```

**Advantages**:
- No additional elements needed
- Values appear automatically when names match
- Scopes show stable converged values

### Method 2: ComputedValueSourceElm (For Electrical Connections)

For connecting computed values to other electrical elements (not just scopes), use the **ComputedValueSourceElm** bridge element.

### ComputedValueSourceElm

A voltage source that reads its value from the ComputedValues registry.

**Menu**: Add CV Source

**Properties**:
- **Value Name**: Name of the computed value to read (e.g., "Firms", "GDP")
- **Default Value**: Value to use if named value doesn't exist
- **Show Value**: Display current value on the element

**Usage**:
1. Add a GodlyTable with columns like "Firms", "Banks"
2. Add ComputedValueSourceElm elements
3. Set each CV Source's "Value Name" to match a column name
4. Connect the CV Source output to scopes or other elements

### Example Circuit

```
GodlyTable                    LabeledNode     Scope
┌─────────────┐                 ┌───┐        ┌─────┐
│ Firms│Banks │                 │   │        │     │
│   100│   50 │                 │Firms───────┤  ~  │
│     …│    … │                 │   │        │     │
└─────────────┘                 └───┘        └─────┘
 (no posts)                   (reads from 
                              ComputedValues)

Alternative with CV Source (for electrical connections):

GodlyTable                    Scope
┌─────────────┐              ┌─────┐
│ Firms│Banks │    CVSource  │     │
│   100│   50 │    ┌──┐      │  ~  │
│     …│    … │    │CV├──────┤     │
└─────────────┘    └──┘      └─────┘
 (no posts)      "Firms"
```

## Implementation Details

### ComputedValues Registry

The `ComputedValues` class provides:
- `setComputedValue(name, value)` - Write a value (goes to pending buffer)
- `getComputedValue(name)` - Read current value
- `getConvergedValue(name)` - Read converged value (for display)

Double-buffering ensures reads see values from the previous timestep, avoiding order-dependent results.

### doStep() Pattern for Pure Computational

```java
@Override
void doStep() {
    // Compute values
    double result = /* calculation */;
    
    // Check convergence (optional for nonlinear)
    if (Math.abs(result - lastResult) > threshold) {
        sim.converged = false;
    }
    lastResult = result;
    
    // Write to registry
    ComputedValues.setComputedValue(name, result);
}
```

### stepFinished() Pattern

```java
@Override
void stepFinished() {
    // Update integration state
    integrationState.lastOutput = computedValue;
    
    // Mark values as computed this step
    ComputedValues.markComputedThisStep(name);
}
```

## Migration Notes

### Existing Circuits

Circuits should be validated against the mode in use:
- For pure mode tables, bridge into the electrical domain with `ComputedValueSourceElm` or `LabeledNodeElm` scope reads.
- For MNA-mode equation rows and Godly master stocks, values are already represented in the circuit solve via labeled/internal nodes.

### Variable Browser

Computed values still appear in the Variable Browser through `ComputedValues.getConvergedValue()`.

## Related Documentation

- [STOCK_FLOW_DOCS_INDEX.md](STOCK_FLOW_DOCS_INDEX.md) - Full documentation index
- [STOCK_MASTER_ELM_REFERENCE.md](STOCK_MASTER_ELM_REFERENCE.md) - Master stocks display
- [FLOWS_MASTER_ELM_REFERENCE.md](FLOWS_MASTER_ELM_REFERENCE.md) - Flows display
