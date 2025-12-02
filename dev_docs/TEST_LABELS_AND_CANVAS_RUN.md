# Test Labels and Canvas Test Running Feature

## Overview
Enhanced the Math Elements Test Suite with:
1. Text labels on each test circuit for identification
2. Ability to run individual tests that are currently loaded on the canvas

## Implementation Details

### 1. Text Labels on Test Circuits
All 16 test circuits now include a text label at the top identifying the test:

```
x 64 32 200 35 4 18 testAdderElm
```

The text element (type 'x') displays the test name prominently on the canvas, making it easy to identify which test circuit is loaded.

Test circuits with labels:
- testAdderElm
- testAdderElmThreeInputs
- testSubtracterElm
- testSubtracterElmThreeInputs
- testMultiplyConstElm
- testMultiplyConstElmNegative
- testMultiplyElm
- testDividerElm
- testDividerElmZeroDenominator
- testDifferentiatorElmConstant
- testIntegratorElm
- testPercentElm
- testEquationElmConstant
- testEquationElmWithParameter
- testODEElmConstant
- testComplexMathCircuit

### 2. Canvas Test Detection
**MathElementsTestDialog.java** - Added `detectCanvasTest()` method:
- Scans all circuit elements on the canvas
- Looks for TextElm elements
- Matches text content against known test names
- Returns the test name if found, null otherwise

### 3. Individual Test Execution
**MathElementsTest.java** - Added two new methods:

**`getTestNames()`**
- Returns array of all 16 test method names
- Used by dialog to validate test names

**`runTest(String testName)`**
- Executes a specific test by name
- Handles all 16 test cases via if-else chain
- Catches and reports errors
- Updates pass/fail counters

### 4. New Dialog Button
**MathElementsTestDialog.java** - Added "Run Test on Canvas" button:
- Blue styling to distinguish from "Run All Tests" (green)
- Detects current test circuit on canvas
- Runs only that specific test
- Shows helpful error message if no test detected

## Usage

### Running Individual Tests
1. Load a test circuit on the canvas (manually or via export/import)
2. The circuit will have a text label showing its name (e.g., "testAdderElm")
3. Open the test dialog: **Dialogs → Math Elements Test Suite...**
4. Click **"▶ Run Test on Canvas"**
5. The dialog detects the test name and runs only that test

### Running All Tests
- Click **"▶ Run All Tests"** to run the complete suite of 16 tests
- Each test loads its own circuit, runs verification, and reports results

### Error Handling
If no test circuit is detected on canvas:
```
❌ No test circuit detected on canvas

To run a canvas test:
1. Load a test circuit (it will have a label like 'testAdderElm')
2. Click 'Run Test on Canvas' to test that specific circuit

Or click 'Run All Tests' to run the complete test suite.
```

## Benefits

1. **Quick Testing**: Test individual circuits without running entire suite
2. **Visual Feedback**: Text labels clearly identify test circuits
3. **Debugging**: Easy to isolate and debug specific test cases
4. **Flexibility**: Can modify a test circuit on canvas and re-run it
5. **User-Friendly**: Clear instructions when no test is detected

## Technical Implementation

### Text Element Format
- Element type: `x` (TextElm)
- Position: 64 32 200 35 (top-left corner)
- Flags: 4 (standard text)
- Size: 18 (readable size)
- Content: Test method name

### Circuit Detection Algorithm
```java
for (CircuitElm ce : sim.elmList) {
    if (ce instanceof TextElm) {
        String text = ((TextElm) ce).text;
        if (text matches known test name) {
            return testName;
        }
    }
}
```

### Test Execution
- Reuses existing `CirSim.theSim` singleton
- Loads circuit via `sim.readCircuit()`
- Runs specific test method
- Validates results and reports pass/fail

## Files Modified

1. **MathElementsTest.java**
   - Added text labels to all 16 test circuits
   - Added `getTestNames()` method
   - Added `runTest(String testName)` method

2. **MathElementsTestDialog.java**
   - Added "Run Test on Canvas" button
   - Added `detectCanvasTest()` method
   - Added `runCanvasTest()` method
   - Enhanced error messaging

## Future Enhancements

Potential improvements:
- Add dropdown to select and load specific tests
- Show current canvas test name in dialog
- Add "Load Test" buttons to load circuits without running
- Export/import test circuits to files
- Color-code text labels (green for passing tests, etc.)

## Example Workflow

1. **Develop a new math element**
2. **Create test circuit** with text label
3. **Load circuit** on canvas
4. **Click "Run Test on Canvas"** to verify
5. **Iterate** - modify circuit and re-test
6. **When ready**, add to full test suite

This makes test-driven development much more efficient!
