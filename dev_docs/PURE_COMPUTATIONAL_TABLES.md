# Pure Computational Tables Architecture

## Overview

Stock-flow table elements (GodlyTableElm, EquationTableElm, SFCTableElm) are now **pure computational** - they do not participate in the MNA (Modified Nodal Analysis) circuit matrix. Instead, they compute values and write them to the `ComputedValues` registry.

## Key Design Principles

### Pure Computational Elements

These elements:
- Return `0` from `getPostCount()` - no electrical posts
- Return `0` from `getVoltageSourceCount()` - no voltage sources
- Have empty `stamp()` methods - no MNA matrix entries
- Write results to `ComputedValues` registry in `doStep()`

### Benefits

1. **Order Independence**: No dependency on MNA matrix solving order
2. **Lower Cost**: No matrix entries means faster simulation
3. **Cleaner Separation**: Accounting logic separated from electrical simulation
4. **Double Buffering**: Uses ComputedValues' double-buffer for consistent reads

### Elements Affected

| Element | Type | Notes |
|---------|------|-------|
| GodlyTableElm | Pure Computational | Integration of stocks over time |
| EquationTableElm | Pure Computational | Named equation rows |
| SFCTableElm | Pure Computational | SFC transaction matrix |

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

Circuits saved with the old MNA-based tables may need adjustment:
- Wire connections to table output posts no longer work
- Add ComputedValueSourceElm elements to bridge values to scopes

### Variable Browser

Computed values still appear in the Variable Browser through `ComputedValues.getConvergedValue()`.

## Related Documentation

- [STOCK_FLOW_DOCS_INDEX.md](STOCK_FLOW_DOCS_INDEX.md) - Full documentation index
- [STOCK_MASTER_ELM_REFERENCE.md](STOCK_MASTER_ELM_REFERENCE.md) - Master stocks display
- [FLOWS_MASTER_ELM_REFERENCE.md](FLOWS_MASTER_ELM_REFERENCE.md) - Flows display
