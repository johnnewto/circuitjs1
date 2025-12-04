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

## Important Notes

- This is a **client-side only** application - all Java code compiles to JavaScript
- Maintain **GWT compatibility** - not all Java features/libraries are available
- **Performance matters** - simulation and rendering happen in real-time (n³ matrix complexity)
- **Cross-browser compatibility** is essential for web deployment
- **Touch/mobile support** should be considered for UI changes
- **Matrix optimization** is critical - use `circuitRowInfo[]` for complex modifications
