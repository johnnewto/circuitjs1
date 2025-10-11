# Test Case: Computed Values Reset Issue

## Problem
When the circuit is reset via the Reset button, computed values from TableElm and other sources were persisting instead of being cleared. This caused nodes to have incorrect values on reset.

## Root Cause
`CirSim.resetAction()` method was not calling `LabeledNodeElm.clearComputedValues()` to clear computed values stored in the global labeled node list.

## Solution
Added `LabeledNodeElm.clearComputedValues()` call to `CirSim.resetAction()` before calling `reset()` on individual elements.

## Test Steps
1. Create a circuit with a TableElm that computes values using column sum feature
2. Run simulation to let computed values be calculated and stored  
3. Hit the Reset button
4. Verify that computed values are cleared and nodes start with correct initial values

## Expected Behavior After Fix
- Reset button clears all computed values
- Nodes start with correct initial voltages (0V unless specified otherwise)
- TableElm recalculates values from scratch after reset
- No stale computed values interfere with new simulation run

## Code Changes
```java
// In CirSim.resetAction()
public void resetAction(){
    int i;
    analyzeFlag = true;
    if (t == 0)
        setSimRunning(true);
    t = timeStepAccum = 0;
    timeStepCount = 0;
    
    // Clear computed values before resetting elements to prevent stale values
    LabeledNodeElm.clearComputedValues();
    
    for (i = 0; i != elmList.size(); i++)
        getElm(i).reset();
    for (i = 0; i != scopeCount; i++)
        scopes[i].resetGraph(true);
    repaint();
}
```