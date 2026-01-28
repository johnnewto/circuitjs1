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
            "R 720 336 672 336 0 0 40 10 0 0 0.5 V\n" +
            "x 133 176 316 179 4 18 testGodleyTableBasic 808080FF\n" +
            "x 106 265 625 268 4 24 Test,\\safter\\s10\\sseconds\\sout\\svalue\\sshould\\sbe\\s100 FF8080FF\n" +
            "207 720 336 768 336 28 ten\n" +
            "207 640 208 704 208 60 table6_e\n" +
            "255 -48 304 64 304 0 2 5 6 16 0 true 2 1 false 5 0 true Table\\s5 \\0 t5_a1 t5-a2 t5_i out A-L-E Flow_2 Flow_1 3 2 5 0 0 ASSET ASSET LIABILITY EQUITY COMPUTED 5 4 \\0 ten-1 \\0 1 -ten -ten 0.1*ten \\0 0.001\n" +
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
     * Test basic GodleyTableElm functionality
     * Expected: Table loads and computes correctly
     */
    public void testGodleyTableComplex() {
String circuit = 
    "$ 67 0.05 7.010541234668786 44 5000 50 5e-11\n" +
    "% voltageUnit $\n" +
    "% showToolbar true\n" +
    "255 -632 -552 -152 -388 0 5 4 6 16 0 true 2 1 false 5 0 true Firms \\0 Firms .. Firms_{Equity} A-L-E Pay\\sWages Buy\\sGoods Borrow\\sMoney Banks\\sSpend Pay\\sInterest 50 0 50 0 ASSET LIABILITY EQUITY COMPUTED -Wages \\0 -Wages \\0 Consume \\0 Consume \\0 Credit \\0 Credit \\0 Spend_{Banks} \\0 Spend_{Banks} \\0 -Int_{Firms} \\0 -Int_{Firms} \\0 0.001\n" +
    "255 -1328 -552 -752 -388 0 5 5 6 16 0 true 2 1 false 5 0 true Households \\0 HouseHolds Debt_{Firms} . HH_{Equity} A-L-E Pay\\sWages\\s Borrow\\sMoney Pay\\sInterest Pay\\sBank\\sFee Buy\\sGoods 40 0 0 40 0 ASSET ASSET LIABILITY EQUITY COMPUTED Wages \\0 \\0 Wages \\0 -Credit Credit \\0 \\0 \\0 Int_{Firms} \\0 \\0 Int_{Firms} \\0 -Fee \\0 \\0 -Fee \\0 -Consume \\0 \\0 -Consume \\0 0.001\n" +
    "255 -944 -752 -368 -572 0 6 5 6 16 0 true 2 1 false 6 0 true Private\\sBanks \\0 Reserves HouseHolds Firms Banks A-L-E Pay\\sWages Buy\\sGoods Borrow\\sMoney Pay\\sInterest Pay\\sBank\\sFee Banks\\sSpend 100 40 50 10 0 ASSET LIABILITY LIABILITY EQUITY COMPUTED \\0 Wages -Wages \\0 \\0 \\0 -Consume Consume \\0 \\0 \\0 -Credit Credit \\0 \\0 \\0 Int_{Firms} -Int_{Firms} \\0 \\0 \\0 -Fee \\0 Fee \\0 \\0 \\0 Spend_{Banks} -Spend_{Banks} \\0 0.001\n" +
    "x -243 -718 11 -715 4 24 Loanable\\sFunds\\sModel 808080FF\n" +
    "263 -1376 -771 83 -106 0 Main\n" +
    "207 -504 -240 -456 -240 36 Money\n" +
    "207 -552 -232 -584 -232 36 HouseHolds\n" +
    "207 -552 -248 -584 -248 36 Firms\n" +
    "251 -552 -240 -504 -240 3 2\n" +
    "258 -944 -192 -896 -192 1 12.5 V_{HH}\n" +
    "258 -992 -288 -944 -288 1 4 V_{Firms}\n" +
    "207 -944 -192 -992 -192 36 HouseHolds\n" +
    "207 -736 -264 -696 -264 36 Profits\n" +
    "w -800 -272 -848 -288 0\n" +
    "w -848 -256 -800 -256 0\n" +
    "252 -800 -264 -736 -264 3 2\n" +
    "207 -896 -192 -832 -192 36 Consume\n" +
    "w -896 -256 -896 -288 0\n" +
    "258 -896 -256 -848 -256 3 0.6 Wage_{Share}\n" +
    "w -896 -288 -848 -288 0\n" +
    "w -944 -288 -896 -288 0\n" +
    "207 -992 -288 -1040 -288 36 Firms\n" +
    "207 -896 -288 -912 -320 36 GDP\n" +
    "207 -800 -256 -800 -208 36 Wages\n" +
    "207 -896 -144 -832 -144 36 Credit\n" +
    "207 -944 -144 -992 -144 36 GDP\n" +
    "258 -944 -144 -896 -144 3 0.009999999999999995 Credit_{Rate}\n" +
    "258 -552 -144 -504 -144 3 0.009999999999999995 Int_{Rate}\n" +
    "207 -552 -144 -592 -144 36 Debt_{Firms}\n" +
    "207 -504 -144 -440 -144 36 Int_{Firms}\n" +
    "207 -272 -176 -240 -176 36 Banks\n" +
    "207 -320 -240 -288 -240 36 HouseHolds\n" +
    "207 -272 -144 -224 -144 4 Firms\n" +
    "207 -240 -320 -280 -320 4 GDP\n" +
    "207 -240 -336 -264 -336 4 Debt_{Firms}\n" +
    "257 -240 -328 -192 -328 3 2\n" +
    "207 -192 -328 -160 -328 36 Debt_{GDP}\n" +
    "207 -320 -264 -288 -264 36 Int_{Firms}\n" +
    "207 -464 -312 -432 -312 36 Fee\n" +
    "207 -512 -312 -552 -312 36 Int_{Firms}\n" +
    "258 -512 -312 -464 -312 3 0.1 Fee_{Rate}\n" +
    "207 -320 -200 -280 -200 36 Spend_{Banks}\n" +
    "207 -368 -200 -416 -200 36 Banks\n" +
    "258 -368 -200 -320 -200 1 1 V_{Banks}\n" +
    "431 -88 -664 -72 -648 0 50 true false\n" +
    "217 -33 -556 128 -368 0 3 Banks HH_{Equity} Firms_{Equity} #FFFF00 #00FF00 #00FFFF\n" +
    "o 31 8 0 4614 640 0.1 0 8 31 3 30 0 30 3 32 0 32 3 22 0 22 3\n" +
    "o 36 4 0 4614 0.3125 0.1 1 2 36 3\n" +
    "38 26 F1 1 -0.1 0.1 -1 Credit_{Rate} 0.01\n" +
    "38 27 F1 1 -0.1 0.1 -1 Int_{Rate} 0.01\n" +
    "% AST 1 1\n";


        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        // runner.runToSteadyState(0.01);
        runner.runToTime(9);
        double actualTime = runner.getTime(); // Get actual simulation time
        // Basic validation that circuit loads
        // runner.assertConverged();
        runner.assertNoErrors();
        double outputVoltage = runner.getNodeVoltage("Firms");
        assertEquals("Firms  output should be 84.074", 84.074, outputVoltage, 84.074 * VOLTAGE_TOLERANCE);

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
     * Test SFC (Stock-Flow Consistent) Table element
     * Expected: Grand total (bottom-right cell) should equal 715
     */
    public void testSFCTable() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "% showToolbar true\n" +
            "265 304 80 880 212 0 4 5 6 16 0 false 2 0 false 5 0 false SFC\\sTable\\s4 \\0 Households Firms Banks Govt Σ Consumption Wages Interest Taxes 0 0 0 0 0 SECTOR SECTOR SECTOR SECTOR COMPUTED -100 \\p1000 0 0 \\0 wages -2*wages 0 0 \\0 \\p5 -10 wages 0 \\0 -200 -15 0 \\p35 \\0 true 0.000001\n" +
            "207 480 256 544 256 4 wages\n" +
            "R 480 256 400 256 0 0 40 5 0 0 0.5 V\n" +
            "263 263 37 901 302 0 Main\n" +
            "% AST 1 1\n";

        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToTime(0.01);
        
        runner.assertNoErrors();
        
        // Find the SFC table and check grand total
        SFCTableElm sfcTable = (SFCTableElm) runner.findElement(SFCTableElm.class);
        assertNotNull("SFC Table should exist", sfcTable);
        
        double grandTotal = sfcTable.getGrandTotal();
        assertEquals("SFC Table grand total should be 715", 715, grandTotal, 715 * VOLTAGE_TOLERANCE);
    }

    /**
     * Test Equation Table element
     * Expected: Y1 integrates 1 (constant), Y2 integrates X (5V source)
     */
    public void testEquationTable() {
        String circuit = 
            "$ 3 0.01 19.867427341514983 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "% showToolbar true\n" +
            "266 280 504 426 578 1 EqnTable 4 Y1 integrate(1)\\s\\p\\s10 \\0 \\0 0.5 Y2 integrate(X\\s\\p\\s2*X) \\0 \\0 0.5 Y3 Y1 \\0 \\0 0.5 Y4 diff(ramp_1) \\0 \\0 0.5\n" +
            "207 288 632 345 632 36 Y2\n" +
            "207 288 600 345 600 36 Y1\n" +
            "207 288 664 345 664 36 Y3\n" +
            "207 544 536 608 536 4 X\n" +
            "R 544 536 496 536 0 0 40 5 0 0 0.5 V\n" +
            "R 536 616 488 616 0 3 0.025 5 5 0 0.5 V\n" +
            "207 536 616 600 616 4 ramp_1\n" +
            "207 288 696 345 696 36 Y4\n" +
            "o 7 64 0 4614 10 0.1 0 2 7 3\n" +
            "38 0 F1 5 0 1 -1 Row\\s1\\s's_W' 0\n" +
            "% AST 0 2\n";


        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToTime(10);
        
        runner.assertNoErrors();
        
        // Y1 = integrate(1) with initial 0, so after 10s: Y1 = 10 + 0 = 10
        double y1Voltage = runner.getNodeVoltage("Y1");
        assertEquals("Y1 should integrate to 20", 20, y1Voltage, 20 * VOLTAGE_TOLERANCE);
        
        // Y2 = integrate(X + 2 * X) where X=5V with initial 0, so after 10s: Y2 = 5*30  = 150
        double y2Voltage = runner.getNodeVoltage("Y2");
        assertEquals("Y2 should integrate to 150", 150, y2Voltage, 150 * VOLTAGE_TOLERANCE);

        // Y3 = Y1, so after 10s: Y3 = 10
        double y3Voltage = runner.getNodeVoltage("Y3");
        assertEquals("Y3 should equal Y1", y1Voltage, y3Voltage, y1Voltage * VOLTAGE_TOLERANCE);

        // Y4 = diff(ramp_1), ramp_1 increases from 0 to 10V over 20s, so diff = 0.5V
        double y4Voltage = runner.getNodeVoltage("Y4");
        assertEquals("Y4 should equal diff(ramp_1)", 0.5, y4Voltage, 0.5 * VOLTAGE_TOLERANCE);

    }

    /**
     * Get list of all test method names
     */
    public String[] getTestNames() {
        return new String[] {
            "testGodleyTableComplex",
            "testGodleyTableBasic",
            "testCTMBasic",
            "testCTM",
            "testSFCTable",
            "testEquationTable"
        };
    }
    
    /**
     * Run a specific test by name
     */
    public void runTest(String testName) {
        testsRun++;
        try {
            if ("testGodleyTableComplex".equals(testName)) testGodleyTableComplex();
            else if ("testGodleyTableBasic".equals(testName)) testGodleyTableBasic();
            else if ("testCTMBasic".equals(testName)) testCTMBasic();
            else if ("testCTM".equals(testName)) testCTM();
            else if ("testSFCTable".equals(testName)) testSFCTable();
            else if ("testEquationTable".equals(testName)) testEquationTable();
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
        runTest("testGodleyTableComplex", () -> testGodleyTableComplex());
        runTest("testCTMBasic", () -> testCTMBasic());
        runTest("testStockFlowBasic", () -> testCTM());
        runTest("testEquationTable", () -> testEquationTable());
        runTest("testSFCTable", () -> testSFCTable());
        
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
