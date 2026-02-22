
# How the volts Array is Set

The `volts` array in each circuit element stores the voltage at each of its connection nodes (posts). This array is populated through the main circuit simulation process:

## 1. Main Simulation Loop
- [`updateCircuit()`](src/com/lushprojects/circuitjs1/client/CirSim.java#L1502) is the main method called every simulation step
- This calls [`runCircuit()`](src/com/lushprojects/circuitjs1/client/CirSim.java#L3021) which performs the actual matrix solving

## 2. Matrix Solution Process
- The simulator uses **Modified Nodal Analysis (MNA)** to solve the circuit
- It builds a matrix equation: **A × X = B**
- [`lu_solve()`](src/com/lushprojects/circuitjs1/client/CirSim.java#L3118) solves this matrix to get node voltages in `circuitRightSide`

## 3. Voltage Distribution
- [`applySolvedRightSide(circuitRightSide)`](src/com/lushprojects/circuitjs1/client/CirSim.java#L3182) processes the solved matrix
- This extracts node voltages and stores them in the global `nodeVoltages[]` array
- Then calls [`setNodeVoltages(nodeVoltages)`](src/com/lushprojects/circuitjs1/client/CirSim.java#L3208) to distribute voltages to elements

## 4. Element Voltage Assignment
- [`setNodeVoltages()`](src/com/lushprojects/circuitjs1/client/CirSim.java#L3208) iterates through all circuit nodes
- For each node, it finds all connected elements via [`CircuitNodeLink`](src/com/lushprojects/circuitjs1/client/CirSim.java#L3214)
- Calls [`element.setNodeVoltage(postNumber, voltage)`](src/com/lushprojects/circuitjs1/client/CircuitElm.java#L264) on each connected element

## 5. Individual Element Update
- [`CircuitElm.setNodeVoltage(n, c)`](src/com/lushprojects/circuitjs1/client/CircuitElm.java#L264) sets `volts[n] = c`
- Then calls [`calculateCurrent()`](src/com/lushprojects/circuitjs1/client/CircuitElm.java#L270) to update current based on new voltage

## 6. Initialization
- `volts` arrays are initialized to 0 in element constructors
- During `reset()` operations, voltages are typically zeroed out

## Key Flow Summary

```
updateCircuit() → 
runCircuit() → 
lu_solve(matrix) → 
applySolvedRightSide() → 
setNodeVoltages() → 
element.setNodeVoltage() → 
volts[n] = voltage
```

## Warnings vs Stop Messages

CircuitJS1 shows two message levels in the lower-left status area. A **stop message** (`stopMessage`) is fatal for the current run: it is set by `stop(...)` for hard failures (for example matrix errors), simulation is halted, and the message remains until re-analysis/editing. A **warning message** (`warningMessage`) is non-fatal: simulation continues while flagging possible modeling issues (for example a PARAM name colliding with a `LabeledNodeElm` name, which can affect name resolution in MNA mode).

## For Example
In  [`LabeledNodeElm`](src/com/lushprojects/circuitjs1/client/LabeledNodeElm.java), `volts[0]` contains the voltage of the node that this labeled node is connected to, as calculated by the circuit simulation matrix solver. This voltage gets updated every simulation timestep as the circuit state evolves.

This method distributes solved node voltage values from the circuit analysis back to individual circuit elements, essentially propagating the solution throughout the circuit after the Modified Nodal Analysis (MNA) matrix has been solved.

## More Details on Voltage Distribution
 [`setNodeVoltages()`](src/com/lushprojects/circuitjs1/client/CirSim.java#L3208) iterates through all circuit nodes
- For each node, it finds all connected elements via [`CircuitNodeLink`](src/com/lushprojects/circuitjs1/client/CirSim.java#L3214)


``` java
// set node voltages in each element given an array of node voltages
    void setNodeVoltages(double nv[]) {
        int j, k;
        for (j = 0; j != nv.length; j++) {
            double res = nv[j];
            CircuitNode cn = getCircuitNode(j+1);
            for (k = 0; k != cn.links.size(); k++) {
            CircuitNodeLink cnl = cn.links.elementAt(k);
            cnl.elm.setNodeVoltage(cnl.num, res);
            }
        }
    }
```
The method takes an array nv[] containing the computed node voltages from the matrix solver. The outer loop iterates through each voltage value in this array, where j represents the array index (0-based) and res holds the actual voltage value for that node. A key detail here is that the method calls getCircuitNode(j+1) rather than getCircuitNode(j) - this is because CircuitJS1 uses a 1-based node numbering system where node 0 is reserved for ground, while the voltage array is 0-indexed and doesn't include ground (which is always 0V).

The inner loop handles the fact that multiple circuit elements can be connected to the same node. Each CircuitNode maintains a links collection containing CircuitNodeLink objects that represent connections to specific terminals of circuit elements. For every element connected to the current node, the method calls setNodeVoltage(cnl.num, res) where cnl.num specifies which terminal of the element corresponds to this node, and res provides the voltage value.

This distribution mechanism is crucial because circuit elements need to know the voltages at their terminals to calculate currents, update their internal state, and render themselves correctly. For example, a resistor needs both terminal voltages to determine current flow direction and magnitude, while a diode needs the voltage difference to determine if it's conducting. The method essentially bridges the gap between the global circuit analysis (which works with abstract node numbers) and the local element behavior (which works with specific terminal voltages).


## How Computed Values Work:
1. Data Flow

```
TableElm Column Sum → ComputedValues.setComputedValue() → Central storage →
TableVoltageElm.getComputedValue() → Voltage source behavior
```

2. Key Components
**ComputedValues Class:**
- `ComputedValues.setComputedValue(name, value)` - Store computed values by name
- `ComputedValues.getComputedValue(name)` - Retrieve computed values by name
- `ComputedValues.resetComputedFlags()` - Reset tracking flags each simulation step
- `ComputedValues.clearComputedValues()` - Clear all stored values

**In TableElm:**
- Column sums call `ComputedValues.setComputedValue(columnHeader, sum)`
- Values stored in separate ComputedValues system, not in LabeledNodeElm

**In CirSim.setNodeVoltages():**
- **Current behavior:** `res = nv[j]` (uses only simulated voltages)
- **No override:** Circuit simulation voltages are never overridden
- **Result:** All wires show actual simulated circuit voltages

3. What This Achieves
✅ **Separation of Concerns** - Computed values separate from circuit simulation
✅ **Accurate Circuit Display** - LabeledNodeElm shows real simulated voltages
✅ **Computed Value Access** - Elements like TableVoltageElm can access computed values when needed
✅ **Clean Architecture** - No interference between computed values and circuit analysis
✅ **Explicit Usage** - Only elements that explicitly request computed values use them

4. Usage Example
- Create a TableElm with column headers like "Col1", "Col2" 
- TableElm stores computed values: `ComputedValues.setComputedValue("Col1", sum)`
- Create a TableVoltageElm with `computedValueName = "Col1"`
- TableVoltageElm uses computed value as its voltage source
- LabeledNodeElm elements show their actual circuit simulation voltages

5. Benefits
✅ **Circuit Accuracy** - No unexpected voltage overrides
✅ **Clear Data Flow** - Explicit computed value usage
✅ **Maintainable Code** - Computed values isolated in separate class
✅ **Flexible Integration** - Any element can produce/consume computed values
✅ **Debugging** - Clear distinction between simulated and computed values



## ✅ GodlyTableElm Successfully Created!
I have successfully implemented the GodlyTableElm element with integration capabilities. Here's what was accomplished:

🎯 Core Features Implemented
1. ✅ Integration Functionality

Integration equation: lastoutput + timestep * integrationGain * columnSum
Uses CircuitJS1's expression evaluation system (Expr and ExprState)
Configurable integration gain (default: 100)
Proper state management across timesteps
2. ✅ Visual Enhancement

Extended TableElm with yellow background for integration cells
Shows both column label and integrated value in sum row
Integration results labeled as columnHeader_integrated
3. ✅ Data Persistence

Integration gain and last outputs saved/loaded in circuit files
Dump type 255 (distinct from TableElm's 253)
Error handling for malformed data
4. ✅ User Interface

Integration Gain parameter (0.1 to 1000)
Reset Integration checkbox to clear accumulated values
Enhanced info display showing equation and current values

5. ✅ System Integration

Added to CirSim case statement (255) for file loading
Added to element creation method for UI placement
Added to main menu as "Add Godly Table"
Integration states properly updated via stepFinished()

🔧 Technical Implementation

Modified Files:

✅ Created GodlyTableElm.java
✅ Modified TableElm.java to make key fields/methods protected
✅ Updated CirSim.java to add GodlyTableElm to creation system and menu
Key Methods:

performIntegration() - Core integration calculation
drawSumRow() - Override to show integration results
parseIntegrationExpr() - Expression compilation
resetIntegration() - Clear integration state

🚀 Usage
Create Element: Select "Add Godly Table" from Draw menu
Configure Integration: Edit "Integration Gain" parameter (default 100)
View Results: Integration values appear in yellow sum row as Col1_integrated, Col2_integrated, etc.
Reset: Use "Reset Integration" checkbox to clear accumulated values
Reference Values: Other elements can access via ComputedValues.getComputedValue("Col1_integrated")

📊 Integration Formula

``` java
    For each column:
        new_value = last_integrated_value + sim.timeStep * integrationGain * columnSum
```

Where:

last_integrated_value: Previous integration result for this column
sim.timeStep: Current simulation timestep
integrationGain: User-configurable multiplier (default 100)
columnSum: Sum of all voltages in the column
The GodlyTableElm is now fully functional and ready for use in CircuitJS1 simulations! 🎉