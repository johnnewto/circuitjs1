# Mathematical Elements Test Suite Guide

## Overview

This document describes the test suite for CircuitJS1's mathematical circuit elements.

**Test Files**:
- Test Suite: `src/com/lushprojects/circuitjs1/client/MathElementsTest.java`
- Test Infrastructure: `src/com/lushprojects/circuitjs1/client/CircuitTestRunner.java`
- Test Dialog: `src/com/lushprojects/circuitjs1/client/MathElementsTestDialog.java`
- Test Runner: `src/com/lushprojects/circuitjs1/client/MathElementsTestRunner.java`

**Note**: Tests must be in the `client` package (not a subpackage) to access package-private members of `CirSim`.

## Tested Elements

The test suite covers 10 mathematical circuit elements:

| Element | Dump Type | Description |
|---------|-----------|-------------|
| **AdderElm** | 251 | Adds multiple input voltages (V_out = V1 + V2 + V3 + ...) |
| **SubtracterElm** | 252 | Subtracts inputs from first input (V_out = V1 - V2 - V3 - ...) |
| **MultiplyElm** | 250 | Multiplies input voltages (V_out = V1 × V2 × ...) |
| **MultiplyConstElm** | 258 | Multiplies input by constant (V_out = K × V_in) |
| **DividerElm** | 257 | Divides first input by others (V_out = V1 ÷ V2 ÷ V3 ÷ ...) |
| **DifferentiatorElm** | 259 | Computes derivative (V_out = dV/dt) |
| **IntegratorElm** | 260 | Integrates input over time (V_out = ∫V dt) |
| **ODEElm** | 261 | Solves ordinary differential equations |
| **EquationElm** | 262 | Evaluates custom equations |
| **PercentElm** | 'P' | Shows ratio/percentage (display only) |

## Test Coverage

### 1. AdderElm Tests

#### `testAdderElm()`
- **Purpose**: Verify basic two-input addition
- **Circuit**: 3V + 5V
- **Expected**: 8V output
- **Validates**: Linear addition using VCVS

#### `testAdderElmThreeInputs()`
- **Purpose**: Verify multi-input addition
- **Circuit**: 2V + 3V + 4V
- **Expected**: 9V output
- **Validates**: Scalability to multiple inputs

### 2. SubtracterElm Tests

#### `testSubtracterElm()`
- **Purpose**: Verify basic two-input subtraction
- **Circuit**: 8V - 3V
- **Expected**: 5V output
- **Validates**: Linear subtraction using VCVS

#### `testSubtracterElmThreeInputs()`
- **Purpose**: Verify multi-input subtraction
- **Circuit**: 10V - 2V - 3V
- **Expected**: 5V output
- **Validates**: Sequential subtraction

### 3. MultiplyConstElm Tests

#### `testMultiplyConstElm()`
- **Purpose**: Verify multiplication by positive constant
- **Circuit**: 4V × 2.5
- **Expected**: 10V output
- **Validates**: Linear VCVS with gain

#### `testMultiplyConstElmNegative()`
- **Purpose**: Verify multiplication by negative constant
- **Circuit**: 5V × (-2.0)
- **Expected**: -10V output
- **Validates**: Sign handling and negative gains

### 4. MultiplyElm Tests

#### `testMultiplyElm()`
- **Purpose**: Verify voltage multiplication
- **Circuit**: 3V × 4V
- **Expected**: 12V output
- **Validates**: Nonlinear multiplication using VCCS

### 5. DividerElm Tests

#### `testDividerElm()`
- **Purpose**: Verify voltage division
- **Circuit**: 12V ÷ 3V
- **Expected**: 4V output
- **Validates**: Nonlinear division using VCCS

#### `testDividerElmZeroDenominator()`
- **Purpose**: Verify divide-by-zero protection
- **Circuit**: 10V ÷ 0V
- **Expected**: Safe handling (output ≈ 0 or bounded)
- **Validates**: Error handling and numerical stability

### 6. DifferentiatorElm Tests

#### `testDifferentiatorElmConstant()`
- **Purpose**: Verify derivative of constant input
- **Circuit**: Constant 5V input
- **Expected**: Output ≈ 0V (derivative of constant = 0)
- **Validates**: Derivative calculation at steady state

### 7. IntegratorElm Tests

#### `testIntegratorElm()`
- **Purpose**: Verify integration over time
- **Circuit**: Constant 2V input integrated for 5ms
- **Expected**: Positive, growing output
- **Validates**: Numerical integration (∫2V dt > 0)

### 8. PercentElm Tests

#### `testPercentElm()`
- **Purpose**: Verify ratio calculation
- **Circuit**: 8V / 4V
- **Expected**: Element exists and computes ratio (2.0 or 200%)
- **Validates**: Display-only element presence

### 9. EquationElm Tests

#### `testEquationElmConstant()`
- **Purpose**: Verify constant equation evaluation
- **Circuit**: Equation = "5"
- **Expected**: 5V output
- **Validates**: Expression parsing and evaluation

#### `testEquationElmWithParameter()`
- **Purpose**: Verify parameterized equation
- **Circuit**: Equation = "a*10" with a=0.5
- **Expected**: 5V output
- **Validates**: Parameter substitution

### 10. ODEElm Tests

#### `testODEElmConstant()`
- **Purpose**: Verify ODE with zero derivative
- **Circuit**: dy/dt = 0, initial value = 3.0
- **Expected**: Output stays at 3.0V
- **Validates**: ODE integration with constant solution

### 11. Integration Tests

#### `testComplexMathCircuit()`
- **Purpose**: Verify multiple elements working together
- **Circuit**: (5V + 3V) × 2 using AdderElm + MultiplyConstElm
- **Expected**: 16V output
- **Validates**: Element composition and interaction

## Test Infrastructure

### CircuitTestRunner
The tests use `CircuitTestRunner` utility class which provides:
- Circuit loading from text format
- Simulation control (run to steady state, run to time)
- Node voltage measurements
- Convergence checking
- Assertion helpers

**Location**: `src/com/lushprojects/circuitjs1/client/CircuitTestRunner.java`

**Key Features**:
- GWT-compatible implementation (no String.format, no Class.isInstance)
- Custom number formatting for test output
- Manual array copying (GWT doesn't support clone on arrays)
- Class comparison using class names instead of reflection

### Standard Parameters
- **Timestep**: 10 microseconds (10e-6 s)
- **Voltage Tolerance**: 2% for most tests
- **Convergence Check**: All tests verify circuit convergence
- **Package**: Tests are in `com.lushprojects.circuitjs1.client` (same package as CirSim for access to package-private members)

### Circuit Format
Tests use CircuitJS1's dump format:
```
$ 1 5.0E-6 10.0 50 5.0 50                    # Header with timestep
v 64 144 64 240 0 0 40 5.0 0 0 0.5           # Voltage source
251 224 208 320 208 0 2                       # AdderElm with 2 inputs
w 64 144 224 144 0                            # Wire
g 64 240 64 272 0 0                           # Ground
370 320 208 432 208 1 out                     # Labeled node "out"
```

## Running the Tests

### Integrated Test Dialog (Recommended)

The test suite runs through an integrated dialog within CircuitJS1:

```bash
# 1. Compile the GWT application (includes tests)
./gradlew compileGwt

# 2. Copy compiled output to war directory
cp -r build/gwt/out/circuitjs1/* war/circuitjs1/

# 3. Start HTTP server
cd war && python3 -m http.server 8001

# 4. Open CircuitJS1 in browser:
#    http://localhost:8001/circuitjs.html

# 5. Open test dialog:
#    Click: Dialogs → Math Elements Test Suite...
```

**Test Dialog Features**:
- **Non-modal** - Doesn't block CircuitJS1 interface
- **Always on top** - Stays visible while working
- **Live output** - Real-time test results in monospace text area
- **Color-coded** - Easy identification of pass/fail
- **Integrated** - No UI conflicts with CircuitJS1 canvas
- **Pass/fail counters** - Automatic result tallying
- **Positioned** - Top-right corner by default

**Running Tests**:
1. Open the dialog from **Dialogs** menu
2. Click **"▶ Run All Tests"** button
3. View results in the output area
4. Click **"Clear Output"** to reset
5. Click **"Close"** when done

### GWT Development Mode (Alternative)

For interactive development with live reload:

```bash
# Start GWT development mode
./gradlew gwtDev

# Open browser to:
# http://localhost:8888/circuitjs.html

# Then open: Dialogs → Math Elements Test Suite...
```

This mode allows:
- Code changes without full recompilation
- Browser DevTools debugging
- Detailed GWT error messages

**Note**: These tests are GWT-based and run in JavaScript within CircuitJS1, not as traditional JUnit tests. The `./gradlew test` command is not applicable.

## Expected Results

All tests should:
1. ✅ Pass with voltage within 2% tolerance
2. ✅ Converge successfully
3. ✅ Complete without errors
4. ✅ Run in under 100ms each

### Test Output Format

**Console Output Example**:
```
========================================
CircuitJS1 Math Elements Test Suite
========================================

Running 16 tests...

PASS: testAdderElm - Verified 3V + 5V = 8V
PASS: testAdderElmThreeInputs - Verified 2V + 3V + 4V = 9V
PASS: testSubtracterElm - Verified 8V - 3V = 5V
...
FAIL: testDividerElmZeroDenominator - Expected safe handling of divide-by-zero

========================================
Test Results: 15 passed, 1 failed
========================================
```

### Available Test Files

| File | Purpose |
|------|---------||
| `MathElementsTest.java` | Main test suite with 16 test methods |
| `CircuitTestRunner.java` | Test infrastructure and helper utilities |
| `MathElementsTestDialog.java` | Integrated GWT dialog for running tests |
| `MathElementsTestRunner.java` | Console runner with instructions |

## Common Issues and Troubleshooting

### GWT Compilation Issues

**Package Access Errors**
If you see errors like "cannot be accessed from outside package":
- Tests must be in `com.lushprojects.circuitjs1.client` package (not a subpackage)
- This is required to access package-private members of `CirSim`
- Move test files to the main `client` package if needed

**String.format Not Supported**
GWT doesn't support `String.format()`:
- Use string concatenation instead: `"Value: " + value`
- Use custom `formatNumber()` helper for number formatting
- Example: `formatNumber(3.14159)` → `"3.141590"`

**Class.isInstance Not Supported**
GWT has limited reflection support:
- Replace `clazz.isInstance(obj)` with `clazz.equals(obj.getClass())`
- Or use: `obj.getClass().getName().equals(clazz.getName())`

**Array.clone Not Supported**
GWT doesn't support clone on arrays:
- Use manual array copying instead
- Example:
  ```java
  // Instead of: newArray = oldArray.clone();
  newArray = new double[oldArray.length];
  for (int i = 0; i < oldArray.length; i++) {
      newArray[i] = oldArray[i];
  }
  ```

### Test Failures

**Voltage Mismatch**
- Check dump types are correct for each element
- Verify circuit wiring (node connections)
- Ensure grounds are properly placed
- Check voltage source polarities

**Convergence Failures**
- Increase steady state timeout
- Check for floating nodes
- Verify nonlinear elements have proper initial conditions
- Reduce timestep if needed

**Circuit Loading Errors**
- Validate circuit dump syntax
- Check element parameters in dump string
- Ensure all referenced dump types exist in CirSim

### Debugging Tips

1. **Print circuit state**:
   ```java
   System.out.println(runner.dumpState());
   ```

2. **Check element properties**:
   ```java
   AdderElm adder = (AdderElm) runner.findElement(AdderElm.class);
   System.out.println("Input count: " + adder.inputCount);
   ```

3. **Monitor simulation**:
   ```java
   runner.runIterations(10);
   System.out.println("Time: " + runner.getTime());
   System.out.println("Voltage: " + runner.getNodeVoltage("out"));
   ```

## Element Dump Format Reference

### AdderElm (251)
```
251 x1 y1 x2 y2 flags inputCount
```

### SubtracterElm (252)
```
252 x1 y1 x2 y2 flags inputCount
```

### MultiplyElm (250)
```
250 x1 y1 x2 y2 flags inputCount
```

### MultiplyConstElm (258)
```
258 x1 y1 x2 y2 flags gain name
```

### DividerElm (257)
```
257 x1 y1 x2 y2 flags inputCount
```

### DifferentiatorElm (259)
```
259 x1 y1 x2 y2 flags inputCount
```

### IntegratorElm (260)
```
260 x1 y1 x2 y2 flags inputCount initialValue
```

### ODEElm (261)
```
261 x1 y1 x2 y2 flags name equation initialValue numParams param1 param2 ... param8
```

### EquationElm (262)
```
262 x1 y1 x2 y2 flags name equation numParams param1 param2 ... param8
```

### PercentElm ('P')
```
P x1 y1 x2 y2 flags scale name
```

## Future Enhancements

Potential additions to the test suite:

1. **Dynamic Input Tests**
   - Test with time-varying inputs (sine waves, square waves)
   - Verify frequency response of differentiators/integrators

2. **Stress Tests**
   - Very large/small voltages
   - Rapid input changes
   - Extended integration times

3. **Error Conditions**
   - Invalid parameters
   - Overflow/underflow conditions
   - Numerical instabilities

4. **Performance Tests**
   - Benchmark simulation speed
   - Test convergence iteration counts
   - Memory usage profiling

5. **Integration Scenarios**
   - Feedback loops with math elements
   - Multi-stage calculations
   - Real-world circuit models (PID controllers, filters, etc.)

## References

- CircuitJS1 Architecture: `ARCHITECTURE.md`
- CircuitTestRunner Documentation: `CircuitTestRunner.java`
- Element Implementation Guide: `.github/copilot-instructions.md`
- GWT Testing: https://www.gwtproject.org/doc/latest/DevGuideTesting.html
