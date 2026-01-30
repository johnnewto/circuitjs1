# CircuitJS1 Project - Copilot Instructions

## Project Overview

CircuitJS1 is an electronic circuit simulator that runs in web browsers. It's a Java-based project that uses Google Web Toolkit (GWT) to compile Java code to JavaScript for browser execution. The project was originally written by Paul Falstad as a Java Applet and adapted by Iain Sharp for web browsers.

## Technology Stack

- **Language**: Java (client-side code compiled to JavaScript via GWT)
- **Build System**: Gradle (with legacy Ant build.xml support)
- **Framework**: Google Web Toolkit (GWT) for Java-to-JavaScript compilation
- **Target Environment**: Web browsers (HTML5 Canvas)

## Project Structure

```
src/com/lushprojects/circuitjs1/client/    # Main Java source code (GWT client-side)
war/                                       # Web application resources
  circuitjs1/                             # Generated GWT output
  circuitjs.html                          # Main application HTML
build.gradle                              # Gradle build configuration
build.xml                                 # Legacy Ant build configuration
app/                                      # Electron app wrapper
lang/                                     # Internationalization files
tests/                                    # Circuit test files
websocket/                                # WebSocket server components
```

## Development Guidelines
- **do not include** 
1. import java.util.StringTokenizer;  // causes

### Code Style & Conventions

- **Package Structure**: All main code is in `com.lushprojects.circuitjs1.client`
- **Naming**: Use camelCase for variables/methods, PascalCase for classes
- **File Naming**: Circuit element classes end with "Elm" (e.g., `ResistorElm`, `CapacitorElm`)
- **GWT Compatibility**: Only use GWT-compatible Java libraries and APIs

### Key Classes & Components

- **CirSim**: Main simulator class, central controller
- **CircuitElm**: Base class for all circuit elements
- **[Component]Elm**: Individual circuit element implementations (ResistorElm, etc.)
- **Locale utilities**: For internationalization support


### Build & Development

- **Primary Build**: Use Gradle (`./gradlew compileGwt`) for building
- **Development Mode**: use `./dev.sh start` to launch dev server
- **Output**: Compiled JavaScript goes to `war/circuitjs1/`
- **Local Testing**: Use development server or open `war/circuitjs.html` directly

### GWT-Specific Considerations

- **Client-Side Only**: Code runs in browser, no server-side Java
- **Limited Java API**: Only GWT-emulated Java libraries available
- **JavaScript Interop**: Some components interact with browser APIs
- **Canvas Rendering**: Uses HTML5 Canvas for circuit visualization
- **GWT doesn't support String.format. Option ia to use `CircuitElm.showFormat.format()` for formatting numbers instead.**

### Adding New Circuit Elements

1. Create new class extending `CircuitElm`
2. Implement required methods: `draw()`, `stamp()`, `getInfo()`, etc.
3. Add to element selection UI
4. Update element factory/creation logic
5. Add internationalization strings if needed

### File Modification Guidelines

- **Core Logic**: Most changes go in `src/com/lushprojects/circuitjs1/client/`
- **UI Elements**: HTML modifications in `war/circuitjs.html`
- **Styling**: CSS changes in `war/circuitjs1/style.css`
- **Build Config**: Gradle changes in `build.gradle`

### Testing

- **Circuit Files**: Test circuits stored in `tests/` directory
- **Manual Testing**: Load test circuits in browser to verify functionality
- **Build Verification**: Ensure `gradle compileGwt` completes successfully

### Internationalization

- **String Extraction**: Use `lang/getstrings.py` to extract translatable strings
- **Locale Files**: Stored in `war/circuitjs1/locale_*.txt`
- **Missing Translations**: Track in `lang/missing_*.txt` files

### Common Tasks

- **Adding Components**: Extend `CircuitElm`, add to UI menus
- **UI Changes**: Modify GWT widgets in main classes
- **Performance**: Optimize drawing/simulation loops in core classes
- **Browser Compatibility**: Test across different browsers
- **Mobile Support**: Consider touch interfaces and responsive design

### Development Environment

- **IDE**: Eclipse with GWT plugin recommended
- **Alternative**: Any Java IDE with Gradle support
- **Container**: Dev container support available (`dev.sh`)
- **Cloud Development**: Compatible with GitHub Codespaces, Gitpod

### Dependencies & Libraries

- **GWT Core**: Google Web Toolkit framework
- **Canvas API**: HTML5 Canvas for rendering
- **No External JARs**: Project is self-contained
- **Browser APIs**: Direct JavaScript interop where needed

## Circuit Simulation Theory

CircuitJS1 uses **Modified Nodal Analysis (MNA)** based on the book "Electronic Circuit and System Simulation Methods" by Pillage, Rohrer, & Visweswariah (1999). The core simulation solves the matrix equation:

**X = A⁻¹B**

Where:
- **A**: Square matrix with one row per circuit node and voltage source (admittance-based)
- **B**: Column vector with entries for nodes and voltage sources  
- **X**: Solution vector containing node voltages and voltage source currents
- **A⁻¹**: Matrix inversion via LU decomposition

### Key Simulation Concepts

- **Linear Elements** (resistors, capacitors, inductors): Stamped once during analysis
- **Nonlinear Elements** (diodes, transistors): Require iterative solving with linearization
- **Timestep Integration**: Backward Euler (stable) or Trapezoidal (accurate) methods
- **Matrix Optimization**: Simplification removes trivial rows to improve performance (n³ complexity)

### Element Implementation Patterns

- **Resistors**: Use `stampResistor()` with conductance values
- **Current Sources**: Use `stampCurrentSource()` to modify right-hand side
- **Voltage Sources**: Require extra matrix rows and `stampVoltageSource()`
- **Capacitors/Inductors**: Implemented as current source + resistor for numerical integration
- **Nonlinear Devices**: Use iterative linearization with convergence checking

## Program Loop Architecture

### Main Loop (`updateCircuit()`)
1. **Run Circuit Simulation**:
   - `analyzeCircuit()`: Setup and validation
   - `stampCircuit()`: Build MNA matrices
   - `runCircuit()`: Solve and iterate
2. **Draw Graphics**: Render circuit visualization

### Analysis Phase (`analyzeCircuit()`)
- Wire closure calculation: `calculateWireClosure()`
- Ground node setup: `setGroundNode()`
- Unconnected node detection: `findUnconnectedNodes()`
- Circuit validation: `validateCircuit()`

### Matrix Building (`stampCircuit()`)
- Connect isolated nodes to ground via large resistors
- Matrix simplification for performance optimization
- LU factorization for linear circuits (one-time operation)

### Simulation Execution (`runCircuit()`)
- **Iteration Loop**: Full simulation timesteps
- **Subiteration Loop**: Convergence iterations for nonlinear circuits
- Matrix solving via `lu_solve()`

## Adding New Circuit Elements - Detailed Guide

### 1. Basic Setup
- Choose unused dump type number (200-300 range) in `createCe()`
- Add entry to `constructElement()` for class name
- Add to UI menu in `composeMainMenu()`
- Create class extending `CircuitElm` or appropriate subclass

### 2. Required Method Implementations

#### Constructor & Identification
```java
// Constructor for UI creation (x1, y1, x2, y2 coordinates)
public YourElement(int xx, int yy, int xx2, int yy2, int f)

// Constructor for file loading (StringTokenizer)
public YourElement(int xa, int ya, int xb, int yb, int f, StringTokenizer st)

int getDumpType() // Return your chosen dump type
int getPostCount() // Return number of terminals
```

#### Geometry & Visualization
```java
void setPoints() // Calculate post positions and layout
Point getPost(int n) // Return post coordinates
void draw() // Render element and call drawPosts()
void setBbox() // Set bounding box for mouse selection
```

#### Circuit Analysis Methods
```java
void stamp() // Stamp linear matrix values (called once)
void doStep() // Stamp nonlinear values (called per iteration)
boolean nonLinear() // Return true if element is nonlinear
int getInternalNodeCount() // Return number of internal nodes needed
int getVoltageSourceCount() // Return number of voltage sources needed
```

#### Current Calculation
```java
void startIteration() // Pre-timestep work
void stepFinished() // Post-timestep work
double getCurrentIntoNode(int n) // Return current into node n
void setCurrent(int voltSourceIdx, double c) // Set voltage source current
```

#### Connection Logic
```java
boolean getConnection(int n1, int n2) // Are terminals n1, n2 connected?
boolean hasGroundConnection(int n) // Does terminal n connect to ground?
```

#### User Interface
```java
EditInfo getEditInfo(int n) // Get nth editable parameter
void setEditInfo(int n, EditInfo ei) // Set parameter from user input
void getInfo(String arr[]) // Return mouse-over information
String dump() // Serialize element state
```

### 3. Implementation Tips

- **Linear Elements**: Implement `stamp()` only, use `sim.stampResistor()`, etc.
- **Nonlinear Elements**: Implement `doStep()` with convergence checking
- **Digital Chips**: Extend `ChipElm`, implement `setupPins()`, `execute()`
- **Composite Elements**: Extend `CompositeElm` to build from other elements
- **Current Animation**: Use `updateDotCount()` and `drawDots()` for visualization

### 4. Testing Checklist

- Test with/without ground connections
- Verify current calculations with `getCurrentIntoNode()`
- Test save/load functionality via export/import
- Check subcircuit compatibility
- Verify white background compatibility
- Test parameter editing interface

## High-Impedance Arithmetic Elements

Elements like `MultiplyElm`, `DividerElm`, `PercentElm`, and `DifferentiatorElm` have **high-impedance inputs** - no current flows through them. This has important implications for implementation.

### Key Insight: No Derivative Linearization Needed

Traditional Newton-Raphson linearization uses derivatives to accelerate convergence:
```java
// DON'T DO THIS for high-impedance inputs:
rs -= derivative * volts[i];  // Causes catastrophic cancellation with large values
sim.stampMatrix(vn, nodes[i], derivative);
```

**Problem**: When input voltages are very large (e.g., 1e15), the derivative calculation causes catastrophic floating-point cancellation, leading to wildly incorrect outputs.

**Solution**: Use **direct stamping** instead - simply stamp the computed value directly:
```java
// DO THIS for high-impedance arithmetic elements:
@Override
public void doStep() {
    double v0 = volts[0];  // Read current input voltage
    double result = computeResult(v0);  // Your computation
    int vn = nodes[inputCount];  // Internal node for voltage source
    sim.stampRightSide(vn, result);  // Direct stamp - no derivatives
}
```

### Why This Works

1. **No current flows** through high-impedance inputs (`getConnection()` returns `false`)
2. The solver naturally iterates: each subiteration reads updated `volts[]`, computes new result, stamps it
3. Convergence happens through the voltage source's natural feedback, not derivative acceleration
4. Eliminates numerical instability with extreme values

### Standard Pattern for Arithmetic Elements

```java
class MyArithmeticElm extends CircuitElm {
    int inputCount = 2;
    int voltSource;
    
    @Override
    public boolean nonLinear() { return true; }
    
    @Override
    public int getVoltageSourceCount() { return 1; }
    
    @Override
    public int getInternalNodeCount() { return 1; }
    
    @Override
    public boolean getConnection(int n1, int n2) { 
        return false;  // High-impedance: no connections between terminals
    }
    
    @Override
    public void stamp() {
        int vn = nodes[inputCount];  // Internal node
        sim.stampNonLinear(vn);  // Mark as nonlinear
        sim.stampVoltageSource(0, nodes[inputCount + 1], voltSource);  // Output driver
    }
    
    @Override
    public void doStep() {
        double result = /* compute from volts[0], volts[1], etc. */;
        
        // Optional: clamp extreme values
        result = Math.max(-1e12, Math.min(1e12, result));
        
        int vn = nodes[inputCount];
        sim.stampRightSide(vn, result);  // Direct stamp
    }
}
```

### Convergence Behavior

- **Linear-only circuits**: 1 subiteration (no convergence checking needed)
- **With nonlinear elements**: Typically 2-4 subiterations
- **All elements participate** in every subiteration - the solver runs globally
- **Any unconverged element** forces another iteration for the whole circuit

### Convergence Threshold for Large Cancelling Values

When summing values that may cancel (e.g., large positive + large negative ≈ 0), use a threshold based on the **maximum magnitude**, not the result:

```java
// Track maximum magnitude during computation
double maxMagnitude = 0;
double sum = 0;
for (int i = 0; i < values.length; i++) {
    sum += values[i];
    maxMagnitude = Math.max(maxMagnitude, Math.abs(values[i]));
}

// Threshold based on max magnitude, not the near-zero sum
double threshold = Math.max(maxMagnitude * 0.001, 1e-6);
if (Math.abs(newValue - oldValue) > threshold) {
    sim.converged = false;
}
```

### Voltage Convergence Checks for High-Impedance Elements

For high-impedance elements that output via a voltage source, **do NOT add a separate voltage convergence check**. This is a common pitfall:

```java
// DON'T DO THIS for voltage-source outputs:
void checkVoltageConvergence() {
    double expected = computedValue;
    double actual = volts[outputNode];
    if (Math.abs(expected - actual) > 1e-6) {
        sim.converged = false;  // WRONG - causes extra iterations
    }
}
```

**Why this is wrong**: The voltage source *forces* the output node to the stamped value. If the equation has converged (stamped value is stable), the voltage will automatically match on the next iteration. Adding a voltage check creates a one-iteration lag that causes unnecessary extra subiterations.

**Correct approach**: Only check convergence of the *computed value*, not the resulting voltage:

```java
// DO THIS - check equation convergence only:
void checkConvergence() {
    double newValue = computeResult();
    if (Math.abs(newValue - lastComputedValue) > threshold) {
        sim.converged = false;
    }
    lastComputedValue = newValue;
}
```

**Key insight**: For any high-impedance element that directly stamps its computed value via a voltage source:
- The voltage check is **redundant** because the voltage source guarantees the node voltage equals the stamped value
- Adding the check introduces a **timing mismatch** - the voltage reflects the *previous* subiteration's stamped value
- This mismatch causes the solver to run 1-2 extra iterations unnecessarily

## Money Circuit Calculations / Stock-Flow Economic Modeling

CircuitJS1 extends circuit simulation to support **Stock-Flow Consistent (SFC) economic models** inspired by Steve Keen's Minsky program. These models use voltage to represent monetary values (stocks) and current to represent flows between accounts.

### Core Concepts

| Electrical Analog | Economic Meaning | Example |
|-------------------|------------------|---------|
| Voltage | Stock level (account balance) | Bank deposits = 100V |
| Current | Flow rate (transaction/second) | Wages = 50 A/s |
| Ground | Reference point | Central bank reserves |
| Voltage source | Initial stock value | Starting balance |

### Key Element Classes

- **TableElm**: Base table for balance sheets with rows (flows) and columns (stocks)
- **GodlyTableElm**: Extended table with integration (stock accumulation over time)
- **EquationTableElm**: Table with expression-based cell values
- **SFCTableElm**: Stock-Flow Consistent matrix for multi-sector models
- **CurrentTransactionsMatrixElm (CTM)**: Aggregates flows across all sectors
- **StockMasterElm**: Displays which table "owns" each stock (diagnostic)
- **FlowsMasterElm**: Lists all flows across all tables (diagnostic)

### Stock Master Pattern

When multiple tables share a stock name (column), only **ONE table can be the electrical master** - the table that actually drives the voltage:

```java
// In ComputedValues registry:
// Each stock has exactly one master table that stamps its voltage
ComputedValues.registerComputedValue("Firms", firmsTable, initialValue);
ComputedValues.getComputingTable("Firms");  // Returns the master table
```

**Rule**: The first table to register a stock becomes its master. Other tables read the voltage but don't drive it.

### Stock-Flow Synchronization

Tables sharing stocks need synchronized row descriptions (flow names):

```
Table A: "Cash" stock with flows [Sales, Interest]
Table B: "Cash" stock with flows [Wages, Rent]

SYNCHRONIZED: Both tables show [Sales, Interest, Wages, Rent]
```

The `StockFlowRegistry` service handles this synchronization - see [STOCK_FLOW_SYNC_SUMMARY.md](../dev_docs/STOCK_FLOW_SYNC_SUMMARY.md).

### Balance Sheet Accounting (A = L + E)

Godley Tables enforce double-entry bookkeeping:
- **Asset columns**: Positive values
- **Liability columns**: Negative values  
- **Equity columns**: Balancing entry
- **A-L-E column**: Should always sum to zero (computed automatically)

```
| Flow↓ / Stock→ | Assets | Liabilities | Equity | A-L-E |
|----------------|--------|-------------|--------|-------|
| Type           | ASSET  | LIABILITY   | EQUITY | COMPUTED |
| Initial        | 100    | -80         | -20    | 0     |
| Transaction    | +10    | -10         |        | 0     |
```

### Integration in Tables

Godley Tables can integrate flows to accumulate stocks over time:

```java
// Cell equation with integration:
"0.001"  // means: stock += 0.001 * flow_value * dt

// The table stamps:
sim.stampVoltageSource(0, stockNode, voltSource, accumulatedValue);
```

### Convergence Considerations for Economic Elements

Economic table elements are **high-impedance arithmetic elements** that follow the patterns described above:

1. **Direct stamping**: Tables stamp computed values directly via voltage sources
2. **No voltage convergence check**: Redundant for voltage-source outputs
3. **Equation convergence only**: Check if computed cell values have stabilized
4. **Integration state**: Use committed/pending pattern for `integrate()` and `diff()` functions

### Convergence Threshold for Large Cancelling Values

When summing values that may cancel (e.g., large positive + large negative ≈ 0), use a threshold based on the **maximum magnitude**, not the result:

```java
// Track maximum magnitude during computation
double maxMagnitude = 0;
double sum = 0;
for (int i = 0; i < values.length; i++) {
    sum += values[i];
    maxMagnitude = Math.max(maxMagnitude, Math.abs(values[i]));
}

// Threshold based on max magnitude, not the near-zero sum
double threshold = Math.max(maxMagnitude * 0.001, 1e-6);
if (Math.abs(newValue - oldValue) > threshold) {
    sim.converged = false;
}
```

### Adding New Economic Elements

1. Extend `TableElm` or `GodlyTableElm` for table-based elements
2. Use `ComputedValues` registry for cross-table variable sharing
3. Register stock ownership to prevent conflicts
4. Implement `getConnection()` returning `false` for high-impedance inputs
5. Follow direct-stamping pattern (no derivative linearization needed)

### Testing Economic Models

Test circuits for economic models are in `src/com/lushprojects/circuitjs1/public/circuits/`:
- `econ_BOMDSimple.txt` - Bank Originated Money and Debt (simple)
- `econ_BOMDwithGovt.txt` - BOMD with government sector
- `econ_SimpleSFCModel.txt` - Stock-Flow Consistent example

### Documentation Resources

- [dev_docs/STOCK_FLOW_DOCS_INDEX.md](../dev_docs/STOCK_FLOW_DOCS_INDEX.md) - Complete documentation index
- [dev_docs/STOCK_MASTER_ELM_REFERENCE.md](../dev_docs/STOCK_MASTER_ELM_REFERENCE.md) - Stock master element
- [dev_docs/FLOWS_MASTER_ELM_REFERENCE.md](../dev_docs/FLOWS_MASTER_ELM_REFERENCE.md) - Flows master element
- [docs-template/docs/money/](../docs-template/docs/money/) - Economic modeling tutorials

## Important Notes

- This is a **client-side only** application - all Java code compiles to JavaScript
- Maintain **GWT compatibility** - not all Java features/libraries are available
- **Performance matters** - simulation and rendering happen in real-time (n³ matrix complexity)
- **Cross-browser compatibility** is essential for web deployment
- **Touch/mobile support** should be considered for UI changes
- **Matrix optimization** is critical - use `circuitRowInfo[]` for complex modifications
- **Matrix optimization** is critical - use `circuitRowInfo[]` for complex modifications
## Common Pitfalls & Solutions

### GWT-Specific Pitfalls

| What Doesn't Work | What To Do Instead |
|-------------------|-------------------|
| `String.format()` | Use `CircuitElm.showFormat.format()` or `NumberFormat` |
| `StringTokenizer` import | Already available, don't import `java.util.StringTokenizer` |
| `System.out.println()` | Use `CirSim.console("message")` for browser console |
| `Math.random()` | Use `Math.random()` (GWT supports this) |
| Java reflection | Not supported - use explicit class mapping |
| Most `java.io.*` | Use GWT-specific file handling |

### Simulation Loop Timing

**Method Call Order** (per timestep):
1. `startIteration()` - Called once before subiterations begin
2. `doStep()` - Called each subiteration (for nonlinear elements)
3. `stepFinished()` - Called once after convergence achieved

**Committed/Pending Pattern** for stateful calculations (integrate, diff):
```java
// In ExprState or element state:
double pendingValue;   // Written during doStep()
double lastValue;      // Read during doStep(), committed from pending

@Override void startIteration() {
    // Commit pending → last at START of new timestep
    lastValue = pendingValue;
}

@Override void doStep() {
    // Read from lastValue (stable), write to pendingValue
    pendingValue = computeNewValue(lastValue);
}
```

### Debugging Tips

- **Console logging**: `CirSim.console("message: " + value)` outputs to browser dev console
- **Element info**: Override `getInfo(String[] arr)` to show debug values on mouse hover
- **Convergence issues**: Check `sim.subIterations` after timestep to see iteration count
- **Non-converged elements**: Set `sim.convergenceCheckThreshold` in Options → Other to identify slow-converging elements

### Common Convergence Problems

| Symptom | Likely Cause | Solution |
|---------|--------------|----------|
| Excessive subiterations | Voltage convergence check on voltage-source output | Remove voltage check, only check equation convergence |
| Values oscillating | Using current subiteration input for derivative | Use committed/pending pattern |
| Never converges | Threshold too tight for large values | Use magnitude-based threshold |
| 1-2 extra iterations | Redundant convergence check | Remove redundant checks |

### Expression Parser (Expr.java)

The `Expr` class parses mathematical expressions with these built-in functions:

**Stateless functions**: `sin`, `cos`, `tan`, `exp`, `log`, `sqrt`, `abs`, `min`, `max`, `floor`, `ceil`

**Stateful functions** (require ExprState):
- `integrate(x)` - Numerical integration over time
- `diff(x)` - Numerical differentiation over time

**ExprState Pattern**:
```java
ExprState es = new ExprState();  // One per expression instance
double result = expr.eval(es);   // Pass state for integrate/diff
es.commitIntegration();          // Call in stepFinished()
es.reset();                      // Call when circuit resets
```

### Adding UI Options

To add a new option in **Options → Other Options**:

1. Add variable to `CirSim.java`: `int myNewOption = defaultValue;`
2. In `EditOptions.java`, add to `getEditInfo(int n)`:
   ```java
   if (n == nextNumber)
       return new EditInfo("Option Label", sim.myNewOption, minVal, maxVal);
   ```
3. In `EditOptions.java`, add to `setEditValue(int n, EditInfo ei)`:
   ```java
   if (n == nextNumber) { sim.myNewOption = (int) ei.value; return; }
   ```

### File Format (Circuit Dump)

Circuit files use text format with element dump strings:
- First line: `$ speed timeStep ...` (simulation parameters)
- Each element: `dumpType x1 y1 x2 y2 flags [parameters...]`
- Text escaping: spaces → `\s`, backslash → `\\`

```java
// In dump():
return super.dump() + " " + myParam1 + " " + CustomLogicModel.escape(myString);

// In constructor from StringTokenizer:
myParam1 = Double.parseDouble(st.nextToken());
myString = CustomLogicModel.unescape(st.nextToken());
```
