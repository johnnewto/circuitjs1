/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client;

/**
 * Test suite for table-based circuit elements
 * 
 * This is a simple test runner that can be executed directly without JUnit.
 * 
 * Tests the following elements:
 * - GodleyTableElm: Double-entry bookkeeping tables for system dynamics
 * - CTMElm: Custom Truth Table for logic operations
 * - Stock-flow modeling elements
 */
public class TableElementsTest {
    
    private static final double VOLTAGE_TOLERANCE = 0.01; // 1% tolerance for voltages
    private static final double TIME_TOLERANCE = 0.001; // Tolerance for time comparisons
    
    private int testsRun = 0;
    private int testsPassed = 0;
    private int testsFailed = 0;
    
    /**
     * Test basic GodleyTableElm functionality
     * Expected: Table loads and computes correctly
     */
    public void testGodleyTableBasic() {
        String circuit = 
            "$ 17 0.05 14.841315910257661 37 5 43 5e-11\n" +
            "% transform 1.53 192.15999999999997 -84.33000000000001\n" +
            "R 720 336 672 336 0 0 40 10 0 0 0.5 V\n" +
            "x 133 176 316 179 4 18 testGodleyTableBasic 808080FF\n" +
            "x 106 265 625 268 4 24 Test,\\safter\\s10\\sseconds\\sout\\svalue\\sshould\\sbe\\s100 FF8080FF\n" +
            "207 720 336 768 336 28 ten\n" +
            "207 640 208 704 208 60 table6_e\n" +
            "255 48 320 160 320 0 2 4 6 16 0 true 2 1 false 5 0 true Table\\s5 \\0 t5_a t5_i out A-L-E Flow_2 Flow_1 0 0 0 0 ASSET LIABILITY EQUITY COMPUTED \\0 \\0 ten-1 \\0 ten -ten 0.1*ten \\0 0.001\n" +
            "431 704 416 720 432 0 10.05 true false\n" +
            "207 496 528 544 528 60 out\n" +
            "% AST 1 1\n";


        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        // runner.runToSteadyState(0.01);
        runner.runToTime(10);
        double actualTime = runner.getTime(); // Get actual simulation time
        // Basic validation that circuit loads
        // runner.assertConverged();
        runner.assertNoErrors();
        double outputVoltage = runner.getNodeVoltage("out");
        assertEquals("Table Integrator output should be 100", 100, outputVoltage, 100 * VOLTAGE_TOLERANCE);

    }
    
    /**
     * Test CTM (Custom Truth Table) element
     * Expected: Truth table logic evaluates correctly
     */
    public void testCTMBasic() {
        String circuit = 
            "$ 17 0.05 15 37 5 43 5e-11\n" +
            "% transform 1.5 173 -191.5\n" +
            "R 720 336 672 336 0 0 40 10 0 0 0.5 V\n" +
            "x 53 150 633 153 4 24 Test,\\safter\\s10\\sseconds\\stable6_e\\svalue\\sshould\\sbe\\s606 FF8080FF\n" +
            "207 720 336 768 336 28 ten\n" +
            "207 640 144 704 144 60 table6_e\n" +
            "255 48 320 160 320 0 2 4 6 16 0 true 2 1 false 5 0 true Table\\s5 \\0 t5_a t5_i table5_e A-L-E Flow_2 Flow_1 0 5 5 0 ASSET LIABILITY EQUITY COMPUTED \\0 \\0 2\\pten \\0 ten -ten 1\\pten \\0 0.001\n" +
            "255 48 448 64 448 0 3 4 6 16 0 true 2 1 false 5 0 true Table\\s6 \\0 Stock_1 Stock_2 table6_e A-L-E Flow_1 flow\\s3 Flow_2 0 2 6 0 ASSET LIABILITY EQUITY COMPUTED ten -ten 1*ten \\0 \\0 \\0 3*ten \\0 -ten -ten 2*ten \\0 0.001\n" +
            "254 80 160 96 160 0 0 0 6 16 0 false 2 1 false 1 0 Current\\sTransactions\\sMatrix \"\" \"\" \"\"\n" +
            "431 704 416 720 432 0 10.05 true false\n" +
            "x 602 300 881 303 4 24 CTM\\sSUM\\sshould\\sbe\\s735 808080FF\n" +
            "% AST 1 1\n";

        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        // runner.runToSteadyState(0.01);
        runner.runToTime(10);
        // Basic validation that circuit loads
        // runner.assertConverged();
        runner.assertNoErrors();
        double outputVoltage = runner.getNodeVoltage("table6_e");
        assertEquals("Table Integrator output should be 606", 606, outputVoltage, 606 * VOLTAGE_TOLERANCE);

    }
    
    /**
     * Test stock-flow modeling basic circuit
     * Expected: Stock accumulates flow over time
     */
    public void testCTM() {
        String circuit = 
            "$ 17 0.05 14.841315910257661 37 5 43 5e-11\n" +
            "% transform 1.5 410 -137.75\n" +
            "R 720 336 672 336 0 0 40 10 0 0 0.5 V\n" +
            "x 53 150 633 153 4 24 Test,\\safter\\s10\\sseconds\\stable6_e\\svalue\\sshould\\sbe\\s606 FF8080FF\n" +
            "207 720 336 768 336 28 ten\n" +
            "207 640 144 704 144 60 table6_e\n" +
            "255 48 320 160 320 0 2 4 6 16 0 true 2 1 false 5 0 true Table\\s5 \\0 t5_a t5_i table5_e A-L-E Flow_2 Flow_1 0 5 5 0 ASSET LIABILITY EQUITY COMPUTED \\0 \\0 2\\pten \\0 ten -ten 1\\pten \\0 0.001\n" +
            "255 48 448 64 448 0 3 4 6 16 0 true 2 1 false 5 0 true Table\\s6 \\0 Stock_1 Stock_2 table6_e A-L-E Flow_1 flow\\s3 Flow_2 0 2 6 0 ASSET LIABILITY EQUITY COMPUTED ten -ten 1*ten \\0 \\0 \\0 3*ten \\0 -ten -ten 2*ten \\0 0.001\n" +
            "254 -176 160 -160 160 0 0 0 6 16 0 false 2 1 false 1 1 Current\\sTransactions\\sMatrix \"\" \"\" \"\"\n" +
            "431 704 416 720 432 0 10.05 true false\n" +
            "x 372 640 651 643 4 24 CTM\\sSUM\\sshould\\sbe\\s648 808080FF\n" +
            "% AST 1 1\n";

        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToTime(0.01);
        
        runner.assertConverged();
        runner.assertNoErrors();
    }
    
    /**
     * Get list of all test method names
     */
    public String[] getTestNames() {
        return new String[] {
            "testGodleyTableBasic",
            "testCTMBasic",
            "testCTM"
        };
    }
    
    /**
     * Run a specific test by name
     */
    public void runTest(String testName) {
        testsRun++;
        try {
            if ("testGodleyTableBasic".equals(testName)) testGodleyTableBasic();
            else if ("testCTMBasic".equals(testName)) testCTMBasic();
            else if ("testCTM".equals(testName)) testCTM();
            else {
                throw new IllegalArgumentException("Unknown test: " + testName);
            }
            testsPassed++;
            console("PASS: " + testName);
        } catch (AssertionError e) {
            testsFailed++;
            console("FAIL: " + testName);
            console("  Reason: " + e.getMessage());
            if (e.getStackTrace() != null && e.getStackTrace().length > 0) {
                console("  at " + e.getStackTrace()[0].toString());
            }
            return;
        } catch (Exception e) {
            testsFailed++;
            console("ERROR: " + testName);
            console("  Exception: " + e.getMessage());
            if (e.getStackTrace() != null && e.getStackTrace().length > 0) {
                console("  at " + e.getStackTrace()[0].toString());
            }
            return;
        }
    }
    
    /**
     * Run all tests
     */
    public void runAllTests() {
        console("=== Table Elements Test Suite ===");
        
        runTest("testGodleyTableBasic", () -> testGodleyTableBasic());
        runTest("testCTMBasic", () -> testCTMBasic());
        runTest("testStockFlowBasic", () -> testCTM());
        
        console("");
        console("=== Test Results ===");
        console("Tests run: " + testsRun);
        console("Tests passed: " + testsPassed);
        console("Tests failed: " + testsFailed);
        
        if (testsFailed == 0) {
            console("✅ ALL TESTS PASSED");
        } else {
            console("❌ SOME TESTS FAILED");
        }
    }
    
    /**
     * Helper method to run a test
     */
    private void runTest(String testName, Runnable test) {
        testsRun++;
        try {
            test.run();
            testsPassed++;
            console("PASS: " + testName);
        } catch (AssertionError e) {
            testsFailed++;
            console("FAIL: " + testName + " - " + e.getMessage());
        } catch (Exception e) {
            testsFailed++;
            console("ERROR: " + testName + " - " + e.getMessage());
        }
    }
    
    /**
     * Native console.log via JSNI
     */
    private native void console(String message) /*-{
        $wnd.console.log(message);
    }-*/;
    
    /**
     * Helper assertions
     */
    private void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
    
    private void assertNotNull(String message, Object obj) {
        if (obj == null) {
            throw new AssertionError(message);
        }
    }
    
    private void assertEquals(String message, double expected, double actual, double tolerance) {
        double error = Math.abs(expected - actual);
        if (error > tolerance) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual + ", Error: " + error);
        }
    }
}
