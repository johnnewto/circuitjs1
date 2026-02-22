## Contents

- [Foreword](#foreword)
- [Internals](#internals)
  - [The Matrix Equation](#the-matrix-equation)
  - [Resistors](#resistors)
  - [Current Sources](#current-sources)
  - [Voltage Sources](#voltage-sources)
  - [Inductors & Numerical Integration](#inductors--numerical-integration)
  - [Capacitors](#capacitors)
  - [Nonlinear Devices](#nonlinear-devices)
  - [Circuit Analysis & Stamping](#circuit-analysis--stamping)
  - [Node List & Internal Nodes](#node-list--internal-nodes)
  - [Wire Handling](#wire-handling)
  - [Named Node Registration (registerLabels)](#named-node-registration-registerlabels)
  - [Wire Currents](#wire-currents)
  - [Matrix Simplification](#matrix-simplification)
- [Adding New Elements](#adding-new-elements)
- [Program Loop](#program-loop)

---

# Foreword

The original design and implementation of the simulation is based on the book **_Electronic Circuit and System Simulation Methods_ (Pillage, L., Rohrer, R., & Visweswariah, C. (1999))**.

The core part of the simulation uses **Modified Nodal Analysis** to determine the voltage of each node in a given circuit, as well as the current of select elements. You can find a detailed introduction to modified nodal analysis [here (Cheever, E., Swarthmore College, (2005))](https://lpsa.swarthmore.edu/Systems/Electrical/mna/MNA1.html).

---

# Internals

## The Matrix Equation

The simulation constructs and solves the following matrix equation:

$$X = A^{-1}B$$

Where:

| Symbol | Description |
|--------|-------------|
| **A** | A _square matrix_ with one row per circuit _node_ and one row per _independent voltage source_. Its contents describe how elements are connected, expressed as _admittance_. |
| **B** | A _column vector_ with one entry per node and one per voltage source. Node entries are usually zero unless an _independent current source_ is present. Voltage source entries contain the source voltage. |
| **X** | The _solution vector_: node voltages followed by voltage source currents. |
| **A⁻¹** | The inversion of matrix **A** via _LU decomposition_ (also called _LU factorization_). |

## Resistors

For a resistor, we want $V_b - V_a = IR$, or equivalently $V_b/R - V_a/R = I$. The net current out of node b and into node a depends on the voltages $V_b$ and $V_a$. We add matrix elements $-1/R$ and $1/R$ at rows a and b and columns a and b to reflect this. (See `CirSim.stampResistor()`.)

After adding all resistors to the matrix, we solve for X, giving us the voltage at each node, from which we can derive the currents through each element. We leave out node 0 (the ground node) because the matrix would be singular otherwise — there would be infinite solutions since all nodes could be shifted by the same voltage.

## Current Sources

To implement a current source of current I, we simply subtract I from row a of the right-side vector and add I to row b. This represents a flow of current I from a to b. (See `CirSim.stampCurrentSource()`.)

## Voltage Sources

Voltage sources need **additional rows** in the matrix. Each voltage source requires:

- An extra row to enforce the voltage constraint
- An extra element in X (and extra matrix column) to solve for the current

For a voltage source with voltage $V_s$ across nodes a and b, the constraint equation is $V_b - V_a = V_s$. To express this as a matrix row:

1. Add matrix elements +1 and -1 in the extra row at columns b and a
2. Set the corresponding right-side element (B) to $V_s$
3. Add matrix elements +1 and -1 in rows a and b in the extra column (to represent current flow)

(See `CirSim.stampVoltageSource()`.)

Now when solving for X, we get both the voltage at each node and the current through each voltage source.

## Inductors & Numerical Integration

When simulating inductors, the current state changes over time. We use a small timestep to step through time iteratively. An inductor is modeled as a **current source in parallel with a resistor**:

- The current source has current equal to the inductor current at a particular time
- The resistor represents resistance to changes in current (value proportional to inductance)
- Each timestep: solve the equation, get the new current, update the current source value (only the right side changes, not the matrix)

**Integration methods** — there are several options:

| Method | Accuracy | Stability |
|--------|----------|-----------|
| Forward Euler | — | Not used |
| Backward Euler | Lower | More stable (less oscillation on switching) |
| Trapezoidal | Higher (better for LRC/filters) | Less stable |

To switch between methods, we simply choose different values for the resistor and current source.

## Capacitors

A capacitor could be simulated as a voltage source in series with a resistor, but that would require two extra matrix rows. Instead, we model it as a **current source in parallel with a resistor** (same as inductors):

- The resistor value is inversely proportional to capacitance (larger capacitors accept more current without large voltage changes)
- The current source flows in a loop through the resistor, simulating the stored charge
- After each timestep, we update the current source based on the new voltage and current

## Nonlinear Devices

For nonlinear devices (e.g., diodes), the matrix must be solved **iteratively**:

1. Start with a given voltage and **linearize** the device's response at that point
2. Find the tangent line to the response curve — this can be expressed as a resistance (slope) and current source (offset)
3. Solve the matrix to get a new voltage
4. Compare with the old voltage — if not nearly the same, create a new linearization and repeat
5. Continue until convergence (new value ≈ previous value)

> **Caution:** When simulating diodes and transistors, voltage changes must be limited at each iteration. Since the response is exponential, unconstrained changes could produce enormous currents that are too large to represent accurately in the matrix.

**Performance optimization:** Nonlinear devices require creating and fully solving a new matrix at least once per timestep. For purely linear circuits, the matrix doesn't change — only the right side does. We can pre-compute an LU factorization and reuse it, which saves significant time for large linear circuits. We call `CircuitElm.nonLinear()` for each element during analysis to determine if this optimization is possible.

## Circuit Analysis & Stamping

Whenever the circuit changes, we call `analyzeCircuit()`. During analysis, we call `stamp()` for each `CircuitElm` to create the matrix — this sets up the circuit elements that don't change. Then for each timestep, we call `doStep()`, which:

- Modifies the right side as needed (for linear elements), or
- Modifies the matrix further (for nonlinear elements)

`doStep()` should also check convergence limits and set the converged flag to false if not met.

## Node List & Internal Nodes

One of the first steps in analyzing the circuit is **building the node list**. Every circuit element is connected to one or more nodes (connection points). We allocate a node for each point and assign it a number.

We also allocate **internal nodes**, used by elements that need an extra node in the matrix to determine their internal state. For example, a tri-state buffer is implemented as a voltage source in series with a resistor — the internal node (not shown on screen) connects them in series.

## Wire Handling

Previously, each wire end got its own node, and wires were implemented as zero-valued voltage sources. This was very inefficient — every wire required two extra matrix rows. Now, **all points connected by wires are considered the same node**. The `calculateWireClosure()` method figures this out and builds a map of connected nodes.

## Named Node Registration (registerLabels)

Some elements have **named posts** that should automatically merge with `LabeledNodeElm` elements sharing the same name. For example, an `SFCStockElm` named "H_h" should share the same MNA node as any `LabeledNodeElm` with text "H_h" — without requiring a physical wire between them.

This is accomplished via a **pre-registration hook** in `calculateWireClosure()`:

1. **Before** the wire closure loop, `calculateWireClosure()` calls `registerLabels()` on every element
2. Elements with named posts (e.g., `SFCStockElm`) override `registerLabels()` to call `LabeledNodeElm.preRegisterLabel(name, point)`, which seeds the `labelList` with the element's post location
3. When the wire closure loop later encounters a `LabeledNodeElm` with the same name, `getConnectedPost()` finds the pre-registered point and returns it, causing the wire closure algorithm to merge both physical points into the same `NodeMapEntry`
4. After node allocation in `makeNodeList()`, the element's `setNode()` override updates the label entry with the assigned MNA node number via `LabeledNodeElm.registerLabeledNode()`

**Result:** The `LabeledNodeElm` and the named element share the same MNA node, so currents flow correctly between them — just as if a physical wire connected them.

**Key methods:**

| Method | Location | Purpose |
|--------|----------|---------|
| `registerLabels()` | `CircuitElm` (virtual) | Hook called before wire closure; no-op by default |
| `preRegisterLabel(name, point)` | `LabeledNodeElm` (static) | Seeds `labelList` before wire closure |
| `registerLabeledNode(name, point, nodeNum)` | `LabeledNodeElm` (static) | Updates label entry with MNA node number after allocation |

**Timing matters:** Pre-registration must happen before the wire closure loop iterates over `isRemovableWire()` elements. If registration happens later (e.g., in `stamp()` or `setNode()`), the nodes will already be allocated separately and won't be merged.

## Wire Currents

The `calcWireInfo()` method calculates wire currents. These aren't needed for simulation, but they're necessary for the **current animation display**.

When wires were voltage sources, their current came directly from the matrix solution. With the current approach (merged nodes), we only know the voltage. So we derive wire currents from connected elements:

- **Series connection:** If a wire connects two resistors in series, the wire current equals the resistor current. We use `getCurrentIntoNode()` to retrieve this.
- **Parallel connection:** If one side connects to multiple elements, we sum their currents.
- **Wire-only connections:** If a wire only connects to other wires, we wait until those wires' currents are calculated first.

This requires determining the processing order, which side to examine, and which elements to query — essentially solving a matrix equation with predetermined steps.

## Matrix Simplification

After stamping all elements, we simplify the matrix in `simplifyMatrix()`. (This was more important before wires were removed from the matrix.) Since solving time is proportional to $n^3$ (where n is the number of rows), reducing matrix size is valuable.

The simplification works by removing trivial rows. For example, if a row has only one nonzero element, we can remove that row and column and solve it independently. This gets tricky with nonlinear circuits, since the matrix may be modified later. We build a mapping from old to new row numbers and track which rows may be filled in later. This is what `circuitRowInfo[]` is for.

---

# Adding New Elements

## Step 1: Register the Element

1. **Pick a dump type:** Go to `createCe()` in CirSim.java and pick an unused number. Avoid picking the next sequential number (that's reserved for the original author). Use something in the 200–300 range.
2. **Add to `createCe()`** for your new dump type.
3. **Add to `constructElement()`** (also in CirSim.java) for your class name.
4. **Add to the menu** somewhere in `composeMainMenu()`.

## Step 2: Create the Class

- Create a new class deriving from `CircuitElm`, or from a more specific parent if appropriate:
  - **Chips:** Derive from `ChipElm`
  - **Composite elements:** Derive from `CompositeElm`
  - **Otherwise:** Pick the most similar existing element and copy its implementation
- Implement the **first constructor** (takes two integers) — used when creating from the UI. The second constructor (takes a `StringTokenizer`) is for loading from files; it can come later.

## Step 3: Identification & Geometry

- **`getDumpType()`** — return your chosen dump type number.
- **`getPostCount()`** — return the number of posts (terminals).
- **`setPoints()`** — lay out the element geometry. Call `super.setPoints()` first, then:
  - `point1` (or `x`, `y`) = where the user started dragging
  - `point2` (or `x2`, `y2`) = where the user stopped dragging
  - These are the first two posts by default (but don't have to be)
  - `dn` = total length
  - For simple two-terminal elements, call `CircuitElm.calcLeads()` to simplify
- **`getPost()`** — return post positions.
- **`drag()`** — override if needed (e.g., for horizontal/vertical-only elements).

## Step 4: Drawing

- **`draw()`** — draw the element and call `drawPosts()`.
  - Calculate the bounding box using `setBbox()` and `adjustBbox()`. If you forget, the element won't be selectable with the mouse.
- **Test your drawing.** Make sure all nodes connect to something with a well-defined voltage, or you'll get matrix errors (if `stamp()` isn't implemented yet and you're using the default `getConnection()`).

## Step 5: Matrix Setup

- **`getInternalNodeCount()`** — override if your element needs internal nodes.
- **`getVoltageSourceCount()`** — override if your element needs voltage sources.
  - Single voltage source: id stored in `voltSource`
  - Multiple: also implement `setVoltageSource()`
  - Digital chips: this equals the number of outputs

### For Digital Chips

If making a chip, implement `setupPins()`, `getChipName()`, and `execute()`. You do **not** need: `setPoints()`, `getPost()`, `draw()`, `stamp()`, `doStep()`, `getCurrentIntoNode()`, `getConnection()`, or `hasGroundConnection()`.

### For Analog Elements

- **`stamp()`** — stamp matrix values for linear elements. Called once when the circuit is analyzed. Use `sim.stamp*` methods and the `nodes[]` array (posts first, then internal nodes). Make sure all nodes are connected when testing.
- **`doStep()`** — stamp additional values for nonlinear elements. Called at least once per timestep (possibly multiple times for convergence). Also implement `nonLinear()` returning true. Use `volts[]` to access node voltages.
- **`startIteration()`** / **`stepFinished()`** — override for pre/post timestep work.

### For Elements with Named Posts

If your element has a named post that should automatically merge with `LabeledNodeElm` elements sharing the same name (e.g., a stock named "H_h" should share a node with any `LabeledNodeElm` labeled "H_h"):

1. **Override `registerLabels()`** — call `LabeledNodeElm.preRegisterLabel(name, point)` to seed the label list before wire closure:
   ```java
   void registerLabels() {
       if (myName != null && !myName.isEmpty())
           LabeledNodeElm.preRegisterLabel(myName, point1);
   }
   ```
2. **Override `setNode()`** — call `LabeledNodeElm.registerLabeledNode(name, point, nodeNum)` to update the label entry with the assigned MNA node number:
   ```java
   void setNode(int p, int n) {
       super.setNode(p, n);
       if (p == 0 && myName != null && !myName.isEmpty())
           LabeledNodeElm.registerLabeledNode(myName, point1, n);
   }
   ```

`registerLabels()` runs during `calculateWireClosure()` **before** nodes are allocated. This is critical — if you only register in `stamp()` or `setNode()`, the nodes will already be separate and won't merge. See [Named Node Registration](#named-node-registration-registerlabels) for details.

## Step 6: Testing the Simulation

Test your implementation. If you have matrix problems, search CirSim.java for "uncomment this line to disable matrix simplification" to aid debugging.

## Step 7: Current Calculation

The simulator provides voltages but **not currents** — you calculate those from the voltages. Exceptions:

- Single voltage source: `current` is set automatically
- Multiple voltage sources: implement `setCurrent()` to receive the currents

### Current Animation

Update `draw()` to animate current flow:

- For each separate current path, calculate a `curcount` using `updateDotCount()` and draw with `drawDots()`
- For simple two-terminal elements, call `doDots()`
- Multi-terminal elements will have multiple currents and curcounts

## Step 8: Edit Dialog

- **`getEditInfo(int n)`** — called repeatedly to get editable values. Return null when done.
- **`setEditValue(int n, EditInfo ei)`** — called when the user changes a value. If the change affects the number of posts, call `allocNodes()` and `setPoints()`.

## Step 9: Serialization

Implement the **second constructor** (with `StringTokenizer`) and **`dump()`** (start with `super.dump()`):

- Make a test circuit, export as text, re-import, and verify all state is preserved
- Boolean state can be stored as bits in `flags` (saved/restored automatically). Check parent classes for already-used bits.

## Step 10: Scope & Info

- **Scope display:** Override `getVoltageDiff()`, `getCurrent()`, and/or `getPower()` if the defaults aren't correct.
- **`getInfo()`** — implement to display mouse-over information.
- **`reset()`** — override if extra cleanup is needed on simulation reset.
- **`getShortcut()`** — remove or fix if you copied an element that has one. Override to return 0 if you subclassed an element that has one.

## Step 11: Connection & Ground Methods

- **`getConnection(int n1, int n2)`** — determines if terminals are internally connected. Default returns true (all terminals connected). Important for avoiding singular matrices when a terminal is unconnected. Examples:
  - Transistor: all terminals connected → default is fine
  - Relay/transformer: two separate circuits → return false for cross-circuit pairs
- **`hasGroundConnection(int n)`** — similar, but for terminals connected to ground or a voltage reference. Example: digital chip outputs return true.
- **`getCurrentIntoNode(int n)`** — return current flowing into node n. Without this, wires connected to your element may show incorrect currents. Test with a circuit like [this example](https://tinyurl.com/yyycodmj) (pretend the resistor is your element, with shunt stub wires forcing `getCurrentIntoNode()` calls).

## Step 12: Final Checks

- **Subcircuits:** Test that your element works inside a subcircuit. Note that `setPoints()` and `draw()` are never called for subcircuit elements — don't put critical logic there.
- **White background:** Use `whiteColor` instead of `Color.white`.
- **Backward compatibility:** If modifying an existing element, make sure old dump formats still load correctly and existing circuits aren't broken.

---

# Program Loop

An incomplete description of the program loop and descriptions of major functions.

## `updateCircuit()`

The main loop. Runs after platform initialization and loops continuously until shutdown. Two main sections:

1. **Run the circuit** (single timestep):
   1. Analyze the circuit: `analyzeCircuit()`
   2. Build the circuit matrices: `stampCircuit()`
   3. Run the simulation: `runCircuit()`
2. **Draw the circuit graphics** (center screen rendering; menus and property UIs are managed by GWT)

## `analyzeCircuit()`

Called when something in the circuit changes. Performs initial setup, then searches for invalid configurations:

1. **`calculateWireClosure()`** — map groups of connected wire-like elements to the same node
2. **`setGroundNode()`** — set the root ground node
3. **`findUnconnectedNodes()`** — find nodes not indirectly connected to ground (all nodes must connect to ground, or we get a matrix error). Unconnected nodes are handled in the next step (`stampCircuit()`).
4. **`validateCircuit()`** — detect invalid cases:
   - Inductors with no current path
   - Current sources with no current path
   - VCCS elements with no current path
   - Voltage loops with no resistance
   - Voltages connecting to ground with no resistance

`stampCircuit()` is always called after `analyzeCircuit()`.

### Warnings vs Stop Messages

CircuitJS1 uses two message levels in the lower-left status area. A **stop message** (`stopMessage`) is fatal for the current run: it is set by `stop(...)` for hard failures (for example matrix errors), simulation is halted, and the message is shown until the circuit is re-analyzed or edited. A **warning message** (`warningMessage`) is non-fatal: simulation continues, but the user is informed about potential model issues (for example a PARAM name colliding with a `LabeledNodeElm` name, which can change name resolution behavior in MNA mode). Warnings are recomputed during pre-stamp analysis and cleared when no hazard is detected.

## `stampCircuit()`

Creates the MNA matrices using info from `analyzeCircuit()` and fills them with data from each circuit element.

1. **`connectUnconnectedNodes()`** — connects isolated nodes to ground via large resistors
2. **`simplifyMatrix()`** — reduces matrix size (solving time is proportional to $n^3$, so smaller is much faster)
3. **Linear optimization:** If the circuit is linear, the matrix can be LU-factored once here instead of every frame in `runCircuit()`

## `runCircuit()`

Contains two nested loops:

- **Iteration loop** (outer): executes a single full simulation timestep, incrementing circuit time by the timestep value
- **Subiteration loop** (inner): solves the circuit matrix via `lu_solve()`. Runs at least once per iteration. Repeats until the circuit converges (or the maximum iteration count is reached)
