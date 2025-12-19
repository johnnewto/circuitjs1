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
 * Test suite for mathematical circuit elements
 * 
 * This is a simple test runner that can be executed directly without JUnit.
 * To run with GWTTestCase, add JUnit dependency to build.gradle:
 *   testImplementation 'junit:junit:4.13.2'
 * 
 * Tests the following elements:
 * - AdderElm: Adds multiple input voltages
 * - SubtracterElm: Subtracts inputs from first input
 * - MultiplyElm: Multiplies input voltages
 * - MultiplyConstElm: Multiplies input by constant
 * - DividerElm: Divides first input by others
 * - DifferentiatorElm: Computes derivative of input
 * - IntegratorElm: Integrates input over time
 * - ODEElm: Solves ordinary differential equations
 * - EquationElm: Evaluates custom equations
 * - PercentElm: Shows ratio/percentage (display only)
 */
public class MathElementsTest {
    
    private static final double VOLTAGE_TOLERANCE = 0.01; // 1% tolerance for voltages
    private static final double TIME_TOLERANCE = 0.001; // Tolerance for time comparisons
    
    private int testsRun = 0;
    private int testsPassed = 0;
    private int testsFailed = 0;
    
    /**
     * Test AdderElm - Should add multiple input voltages
     * Circuit: Two voltage sources (3V and 5V) connected to adder inputs
     * Expected: Output = 3V + 5V = 8V
     */
    public void testAdderElm() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 64 32 178 35 4 18 testAdderElm 808080FF\n" +
            "v 48 64 48 128 0 0 40 3 0 0 0.5 V\n" +
            "v 48 176 48 240 0 0 40 5 0 0 0.5 V\n" +
            "251 208 112 304 112 0 2\n" +
            "w 48 64 208 64 2\n" +
            "w 48 176 208 176 2\n" +
            "w 208 64 208 96 0\n" +
            "w 208 176 208 128 0\n" +
            "g 48 128 48 144 0 0\n" +
            "g 48 240 48 256 0 0\n" +
            "207 304 112 416 112 36 out\n" +
            "% AST 1 1\n";

        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01); // Run to steady state (10ms max)
        
        double outputVoltage = runner.getNodeVoltage("out");
        assertEquals("Adder output should be -3V + -5V = -8V", -8.0, outputVoltage, Math.abs(-8.0) * VOLTAGE_TOLERANCE);
        
        runner.assertConverged();
        runner.assertNoErrors();
    }
    
    /**
     * Test AdderElm with three inputs
     * Expected: Output = 2V + 3V + 4V = 9V
     */
    public void testAdderElmThreeInputs() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 64 32 279 35 4 18 testAdderElmThreeInputs 808080FF\n" +
            "v 64 144 64 96 0 0 40 2 0 0 0.5 V\n" +
            "v 128 224 128 176 0 0 40 3 0 0 0.5 V\n" +
            "v 64 352 64 304 0 0 40 4 0 0 0.5 V\n" +
            "251 256 176 352 176 0 3\n" +
            "w 64 96 256 96 0\n" +
            "w 128 176 256 176 0\n" +
            "w 64 304 256 304 0\n" +
            "w 256 96 256 160 0\n" +
            "w 256 304 256 192 0\n" +
            "g 64 144 64 160 0 0\n" +
            "g 128 224 128 240 0 0\n" +
            "g 64 352 64 368 0 0\n" +
            "207 352 176 464 176 36 out\n" +
            "% AST 1 1\n";

        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01);
        
        double outputVoltage = runner.getNodeVoltage("out");
        assertEquals("Adder output should be 2V + 3V + 4V = 9V", 9.0, outputVoltage, 9.0 * VOLTAGE_TOLERANCE);
        
        runner.assertConverged();
    }
    
    /**
     * Test SubtracterElm - Should subtract inputs from first input
     * Circuit: Two voltage sources (8V and 3V) connected to subtracter
     * Expected: Output = 8V - 3V = 5V
     */
    public void testSubtracterElm() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 64 32 215 35 4 18 testSubtracterElm 808080FF\n" +
            "v 64 192 64 144 0 0 40 8 0 0 0.5 V\n" +
            "v 64 304 64 256 0 0 40 3 0 0 0.5 V\n" +
            "252 224 192 320 192 0 2\n" +
            "w 64 144 224 144 0\n" +
            "w 64 256 224 256 0\n" +
            "w 224 144 224 176 0\n" +
            "w 224 256 224 208 0\n" +
            "g 64 192 64 224 0 0\n" +
            "207 320 192 432 192 36 out\n" +
            "% AST 1 1\n";

        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01);
        
        double outputVoltage = runner.getNodeVoltage("out");
        assertEquals("Subtracter output should be 8V - 3V = 5V", 5.0, outputVoltage, 5.0 * VOLTAGE_TOLERANCE);
        
        runner.assertConverged();
    }
    
    /**
     * Test SubtracterElm with three inputs
     * Expected: Output = 10V - 2V - 3V = 5V
     */
    public void testSubtracterElmThreeInputs() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 64 32 317 35 4 18 testSubtracterElmThreeInputs 808080FF\n" +
            "v 64 160 64 96 0 0 40 10 0 0 0.5 V\n" +
            "v 144 240 144 192 0 0 40 2 0 0 0.5 V\n" +
            "v 64 352 64 288 0 0 40 3 0 0 0.5 V\n" +
            "252 256 192 336 192 0 3\n" +
            "w 64 96 256 96 0\n" +
            "w 64 288 256 288 0\n" +
            "w 256 96 256 176 0\n" +
            "w 256 192 144 192 0\n" +
            "w 256 288 256 208 0\n" +
            "g 64 160 64 176 0 0\n" +
            "g 144 240 144 256 0 0\n" +
            "g 64 352 64 368 0 0\n" +
            "207 336 192 448 192 36 out\n" +
            "% AST 1 1\n";


        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01);
        
        double outputVoltage = runner.getNodeVoltage("out");
        assertEquals("Subtracter output should be 10V - 2V - 3V = 5V", 5.0, outputVoltage, 5.0 * VOLTAGE_TOLERANCE);
        
        runner.assertConverged();
    }
    
    /**
     * Test MultiplyConstElm - Multiplies input by constant
     * Circuit: 4V source multiplied by constant 2.5
     * Expected: Output = 4V × 2.5 = 10V
     */
    public void testMultiplyConstElm() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% transform 1.5 482 224.5\n" +
            "x 64 32 242 35 4 18 testMultiplyConstElm 808080FF\n" +
            "v 64 240 64 144 0 0 40 4 0 0 0.5 V\n" +
            "258 224 208 320 208 0 2.5 name\n" +
            "w 64 144 224 144 0\n" +
            "w 224 144 224 208 0\n" +
            "g 64 240 64 272 0 0\n" +
            "207 336 208 448 208 36 out\n" +
            "w 320 208 336 208 0\n" +
            "% AST 1 1\n";

        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01);
        
        double outputVoltage = runner.getNodeVoltage("out");
        assertEquals("MultiplyConst output should be 4V × 2.5 = 10V", 10.0, outputVoltage, 10.0 * VOLTAGE_TOLERANCE);
        
        runner.assertConverged();
    }
    
    /**
     * Test MultiplyConstElm with negative gain
     * Expected: Output = 5V × (-2.0) = -10V
     */
    public void testMultiplyConstElmNegative() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% transform 1.5 482 224.5\n" +
            "x 64 32 317 35 4 18 testMultiplyConstElmNegative 808080FF\n" +
            "v 64 240 64 144 0 0 40 5 0 0 0.5 V\n" +
            "258 224 208 320 208 0 -2 neg\n" +
            "w 64 144 224 144 0\n" +
            "w 224 144 224 208 0\n" +
            "g 64 240 64 272 0 0\n" +
            "207 336 208 448 208 36 out\n" +
            "w 320 208 336 208 0\n" +
            "% AST 1 1\n";

        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01);
        
        double outputVoltage = runner.getNodeVoltage("out");
        assertEquals("MultiplyConst output should be 5V × -2.0 = -10V", -10.0, outputVoltage, 10.0 * VOLTAGE_TOLERANCE);
        
        runner.assertConverged();
    }
    
    /**
     * Test MultiplyElm - Multiplies two input voltages
     * Circuit: 3V × 4V
     * Expected: Output = 12V
     */
    public void testMultiplyElm() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 64 32 192 35 4 18 testMultiplyElm 808080FF\n" +
            "v 64 192 64 144 0 0 40 3 0 0 0.5 V\n" +
            "v 64 304 64 256 0 0 40 4 0 0 0.5 V\n" +
            "w 64 144 224 144 2\n" +
            "w 64 256 224 256 2\n" +
            "w 224 144 224 176 0\n" +
            "w 224 256 224 208 0\n" +
            "g 64 192 64 208 0 0\n" +
            "g 64 304 64 320 0 0\n" +
            "207 320 192 432 192 36 out\n" +
            "250 224 192 320 192 1 2\n" +
            "% AST 1 1\n";


        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01);
        
        double outputVoltage = runner.getNodeVoltage("out");
        assertEquals("Multiply output should be 3V × 4V = 12V", 12.0, outputVoltage, 12.0 * VOLTAGE_TOLERANCE);
        
        runner.assertConverged();
    }
    
    /**
     * Test DividerElm - Divides first input by second
     * Circuit: 12V ÷ 3V
     * Expected: Output = 4V
     */
    public void testDividerElm() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 64 32 187 35 4 18 testDividerElm 808080FF\n" +
            "v 64 144 64 192 0 0 40 12 0 0 0.5 V\n" +
            "v 64 256 64 304 0 0 40 3 0 0 0.5 V\n" +
            "w 64 144 240 144 2\n" +
            "w 64 256 240 256 2\n" +
            "w 240 144 240 176 0\n" +
            "w 240 256 240 208 0\n" +
            "g 64 192 64 208 0 0\n" +
            "g 64 304 64 320 0 0\n" +
            "207 336 192 448 192 36 out\n" +
            "257 240 192 336 192 1 2\n" +
            "% AST 1 1\n";


        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01);
        
        double outputVoltage = runner.getNodeVoltage("out");
        assertEquals("Divider output should be 12V ÷ 3V = 4V", 4.0, outputVoltage, 4.0 * VOLTAGE_TOLERANCE);
        
        runner.assertConverged();
    }
    
    /**
     * Test DividerElm with three inputs
     * Circuit: 24V ÷ 2V ÷ 3V
     * Expected: Output = 4V
     */
    public void testDividerElmThreeInputs() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 64 32 288 35 4 18 testDividerElmThreeInputs 808080FF\n" +
            "v 64 176 64 128 0 0 40 24 0 0 0.5 V\n" +
            "v 112 256 112 208 0 0 40 2 0 0 0.5 V\n" +
            "v 64 368 64 320 0 0 40 3 0 0 0.5 V\n" +
            "w 64 128 240 128 2\n" +
            "w 112 208 240 208 2\n" +
            "w 64 320 240 320 2\n" +
            "w 240 128 240 192 0\n" +
            "w 240 320 240 224 0\n" +
            "g 64 176 64 192 0 0\n" +
            "g 112 256 112 272 0 0\n" +
            "g 64 368 64 384 0 0\n" +
            "207 336 208 448 208 36 out\n" +
            "257 240 208 336 208 1 3\n" +
            "% AST 1 1\n";


        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01);
        
        double outputVoltage = runner.getNodeVoltage("out");
        assertEquals("Divider output should be 24V ÷ 2V ÷ 3V = 4V", 4.0, outputVoltage, 4.0 * VOLTAGE_TOLERANCE);
        
        runner.assertConverged();
    }
    
    /**
     * Test DividerElm with divide by zero protection
     * Circuit: 10V ÷ 0V (should output 0 or safe value)
     */
    public void testDividerElmZeroDenominator() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 64 32 334 35 4 18 testDividerElmZeroDenominator 808080FF\n" +
            "v 64 144 64 192 0 0 40 10 0 0 0.5 V\n" +
            "v 64 256 64 304 0 0 40 0 0 0 0.5 V\n" +
            "w 64 144 224 144 2\n" +
            "w 64 256 224 256 2\n" +
            "w 224 144 224 176 0\n" +
            "w 224 256 224 208 0\n" +
            "g 64 192 64 208 0 0\n" +
            "g 64 304 64 320 0 0\n" +
            "207 320 192 432 192 36 out\n" +
            "257 224 192 320 192 1 2\n" +
            "% AST 1 1\n";


        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01);
        
        double outputVoltage = runner.getNodeVoltage("out");
        // Divide by zero should output 0 or handle gracefully
        assertTrue("Divider should handle divide by zero", Math.abs(outputVoltage) < 1.0);
        
        runner.assertConverged();
    }
    
    /**
     * Test DifferentiatorElm - Computes derivative
     * Circuit: Constant 5V input (derivative = 0)
     * Expected: Output ≈ 0V
     */
    public void testDifferentiatorElmConstant() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 114 95 363 98 4 18 testDifferentiatorElmConstant 808080FF\n" +
            "v 64 240 64 144 0 0 40 5 0 0 0.5 V\n" +
            "w 64 144 224 144 2\n" +
            "g 64 240 64 272 0 0\n" +
            "207 272 144 384 144 36 out\n" +
            "259 224 144 272 144 1\n" +
            "% AST 1 1\n";

        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01);
        
        double outputVoltage = runner.getNodeVoltage("out");
        // Derivative of constant should be near zero
        assertTrue("Differentiator of constant should be near 0", Math.abs(outputVoltage) < 0.1);
        
        runner.assertConverged();
    }
    
    /**
     * Test DifferentiatorElm - Computes derivative
     * Circuit: Constant 1V input (derivative = +-4)
     * Expected: Output ≈ +-4V
     */
    public void testDifferentiatorElmTriangle() {
        String circuit = 
            "$ 1 0.0001 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 114 95 355 98 4 18 testDifferentiatorElmTriangle 808080FF\n" +
            "v 64 240 64 144 0 3 1 1 0 0 0.5 V\n" +
            "w 64 144 224 144 2\n" +
            "g 64 240 64 272 0 0\n" +
            "207 320 144 432 144 36 out\n" +
            "259 224 144 320 144 0\n" +
            "% AST 1 1\n";


        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToTime(0.01); // Run for 10ms (100 steps at 1e-4s timestep)
        
        double outputVoltage = runner.getNodeVoltage("out");
        // Derivative of 1Hz 1V triangle wave should be near ±4V (slope of triangle)
        assertEquals("Differentiator of 1 hz 1 volt triangle should be near +-4", 4.0, Math.abs(outputVoltage), 4.0 * VOLTAGE_TOLERANCE);
        runner.assertConverged();
    }


    /**
     * Test IntegratorElm - Integrates input over time
     * Circuit: Constant 2V input integrated from t=0
     * Expected: Output increases linearly (integral of constant = constant×t)
     * After 0.005s: integral ≈ 2V × 0.005s = 0.01 V·s
     */
    public void testIntegratorElm() {
        String circuit = 
            "$ 1 0.00005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 83 86 228 89 4 18 testIntegratorElm 808080FF\n" +
            "v 64 192 64 144 0 0 40 10 0 0 0.5 V\n" +
            "v 64 272 64 240 0 0 40 0 0 0 0.5 V\n" +
            "260 224 192 320 192 1 2 0\n" +
            "w 64 144 224 144 0\n" +
            "w 64 240 224 240 0\n" +
            "w 224 144 224 176 0\n" +
            "w 224 240 224 208 0\n" +
            "g 64 192 64 208 0 0\n" +
            "g 64 272 64 288 0 0\n" +
            "207 320 192 432 192 36 out\n" +
            "% AST 1 1\n";


        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        
        // Run for specific time to check integration
        runner.runToTime(0.01); // 10ms
        
        double actualTime = runner.getTime(); // Get actual simulation time
        double inputVoltage = 10.0; // 10V input
        double expectedOutput = inputVoltage * actualTime; // Integral = V * t
        
        double outputVoltage = runner.getNodeVoltage("out");
        // Integral of 10V over actual time
        assertTrue("Integrator output should be positive and growing", outputVoltage > 0.001);
        assertEquals("Integrator output should be " + expectedOutput, expectedOutput, outputVoltage, expectedOutput * VOLTAGE_TOLERANCE);
        
        runner.assertConverged();
    }
    
    /**
     * Test PercentElm - Display only, shows ratio
     * Circuit: 8V and 4V inputs
     * Expected: Ratio = 8/4 = 2.0 (or 200% if showing percentage)
     * Note: PercentElm is display-only and doesn't output voltage
     */
    public void testPercentElm() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 85 63 214 66 4 18 testPercentElm 808080FF\n" +
            "v 64 144 64 208 0 0 40 8 0 0 0.5 V\n" +
            "v 64 256 64 304 0 0 40 4 0 0 0.5 V\n" +
            "w 64 144 224 144 2\n" +
            "w 64 256 224 256 2\n" +
            "g 64 208 64 224 0 0\n" +
            "g 64 304 64 320 0 0\n" +
            "207 320 160 385 160 36 out\n" +
            "P 224 160 320 160 0 2\n" +
            "w 224 176 224 256 0\n" +
            "% AST 1 1\n";


        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01);
        
        // PercentElm is display-only and doesn't output voltage
        // Just verify the circuit loads and runs without errors
        // The element will calculate ratio (8V / 4V = 2.0) internally
        double outputVoltage = runner.getNodeVoltage("out");
       assertEquals("Percent output should be 100 * 8V / 4V = 200", 200.0, outputVoltage, 200.0 * VOLTAGE_TOLERANCE);

        runner.assertConverged();
        runner.assertNoErrors();
    }
    
    /**
     * Test EquationElm - Evaluates custom equation
     * Circuit: Simple equation 'sin(30*pi/180) + v_{in}+a'
     * Expected: Output = 6V
     */
    public void testEquationElmWithParameter() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 64 32 278 35 4 18 testEquationElmParameter 808080FF\n" +
            "262 176 128 272 128 0 Eqn sin(30*pi/180)\\s\\p\\sv_{in}\\pa 1 0.5\n" +
            "207 272 128 384 128 36 out\n" +
            "g 80 160 80 192 0 0\n" +
            "v 80 160 80 112 0 0 40 5 0 0 0.5 V\n" +
            "207 80 112 80 48 36 v_{in}\n" +
            "% AST 1 1\n";


        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01);
        
        double outputVoltage = runner.getNodeVoltage("out");
        assertEquals("Equation 'sin(30*pi/180) + v_{in}+a' should output 6", 6.0, outputVoltage, 6.0 * VOLTAGE_TOLERANCE);
        
        runner.assertConverged();
    }
    

    /**
     * Test ODEElm - Solves differential equation
     * Circuit: Simple ODE d/dt (a*Prey)+(b*Predator), with a=0.1, b=0.2, initial Prey=10, Predator=5
     * Expected: Output remains at initial value
     */
    public void testODEElmConstant() {
        String circuit = 
            "$ 1 0.0001 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 64 32 244 35 4 18 testODEElmConstant 808080FF\n" +
            "261 128 128 240 128 0 ODE (a*Prey)\\p(b*Predator) 0 2 0.1 0.2\n" +
            "207 -80 160 16 160 36 Prey\n" +
            "R -80 160 -160 160 0 0 40 10 0 0 0.5 V\n" +
            "R -80 128 -160 128 0 0 40 5 0 0 0.5 V\n" +
            "207 240 128 352 128 36 out\n" +
            "207 -80 128 16 128 36 Predator\n" +
            "% AST 1 1\n";



        
        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToTime(0.01); // Run for 10ms
        
        double outputVoltage = runner.getNodeVoltage("out");
        double actualTime = runner.getTime(); // Get actual simulation time
        double inputVoltage = 2.0; // should integral of 0.1*10 + 0.2*5 = 1 + 1 = 2 over time
        double expectedOutput = inputVoltage * actualTime; // Integral = V * t
        // ODE with (a*Prey)+(b*Predator), with a=0.1, b=0.2, initial Prey=10, Predator=5
        assertEquals("ODE with dy/dt= 2 * t" + expectedOutput, expectedOutput, outputVoltage, expectedOutput * VOLTAGE_TOLERANCE);

        
        runner.assertConverged();
    }
    
    /**
     * Integration test: Complex circuit using multiple math elements
     * Circuit: (5V + 3V) × 2 = 16V
     */
    public void testComplexMathCircuit() {
        String circuit = 
            "$ 1 0.000005 10.20027730826997 50 5 50 5e-11\n" +
            "% voltageUnit $\n" +
            "x 64 32 266 35 4 18 testComplexMathCircuit 808080FF\n" +
            "v 64 160 64 96 0 0 40 5 0 0 0.5 V\n" +
            "v 64 256 64 192 0 0 40 3 0 0 0.5 V\n" +
            "251 192 128 272 128 0 2\n" +
            "w 64 96 192 96 2\n" +
            "w 64 192 192 192 2\n" +
            "w 192 96 192 112 0\n" +
            "w 192 192 192 144 0\n" +
            "g 64 160 64 176 0 0\n" +
            "g 64 256 64 272 0 0\n" +
            "258 352 128 448 128 0 2 mult\n" +
            "w 272 128 352 128 2\n" +
            "207 448 128 560 128 36 out\n" +
            "% AST 1 1\n";


        CircuitTestRunner runner = new CircuitTestRunner();
        runner.loadCircuitFromText(circuit);
        runner.runToSteadyState(0.01);
        
        double outputVoltage = runner.getNodeVoltage("out");
        // (5V + 3V) × 2 = 16V
        assertEquals("Complex circuit (5+3)×2 should output 16V", 16.0, outputVoltage, 16.0 * VOLTAGE_TOLERANCE);
        
        runner.assertConverged();
    }
    
    /**
     * Get list of all test method names
     */
    public String[] getTestNames() {
        return new String[] {
            "testAdderElm",
            "testAdderElmThreeInputs",
            "testSubtracterElm",
            "testSubtracterElmThreeInputs",
            "testMultiplyConstElm",
            "testMultiplyConstElmNegative",
            "testMultiplyElm",
            "testDividerElm",
            "testDividerElmThreeInputs",
            "testDividerElmZeroDenominator",
            "testDifferentiatorElmConstant",
            "testDifferentiatorElmTriangle",
            "testIntegratorElm",
            "testPercentElm",
            "testEquationElmConstant",
            "testEquationElmWithParameter",
            "testODEElmConstant",
            "testComplexMathCircuit"
        };
    }
    
    /**
     * Run a specific test by name
     */
    public void runTest(String testName) {
        testsRun++;
        try {
            if ("testAdderElm".equals(testName)) testAdderElm();
            else if ("testAdderElmThreeInputs".equals(testName)) testAdderElmThreeInputs();
            else if ("testSubtracterElm".equals(testName)) testSubtracterElm();
            else if ("testSubtracterElmThreeInputs".equals(testName)) testSubtracterElmThreeInputs();
            else if ("testMultiplyConstElm".equals(testName)) testMultiplyConstElm();
            else if ("testMultiplyConstElmNegative".equals(testName)) testMultiplyConstElmNegative();
            else if ("testMultiplyElm".equals(testName)) testMultiplyElm();
            else if ("testDividerElm".equals(testName)) testDividerElm();
            else if ("testDividerElmThreeInputs".equals(testName)) testDividerElmThreeInputs();
            else if ("testDividerElmZeroDenominator".equals(testName)) testDividerElmZeroDenominator();
            else if ("testDifferentiatorElmConstant".equals(testName)) testDifferentiatorElmConstant();
            else if ("testDifferentiatorElmTriangle".equals(testName)) testDifferentiatorElmTriangle();
            else if ("testIntegratorElm".equals(testName)) testIntegratorElm();
            else if ("testPercentElm".equals(testName)) testPercentElm();
            else if ("testEquationElmWithParameter".equals(testName)) testEquationElmWithParameter();
            else if ("testODEElmConstant".equals(testName)) testODEElmConstant();
            else if ("testComplexMathCircuit".equals(testName)) testComplexMathCircuit();
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
            return;  // Exit early on assertion failure
        } catch (Exception e) {
            testsFailed++;
            console("ERROR: " + testName);
            console("  Exception: " + e.getMessage());
            if (e.getStackTrace() != null && e.getStackTrace().length > 0) {
                console("  at " + e.getStackTrace()[0].toString());
            }
            return;  // Exit early on exception
        }
    }
    
    /**
     * Run all tests
     */
    public void runAllTests() {
        console("=== Math Elements Test Suite ===");
        
        runTest("testAdderElm", () -> testAdderElm());
        runTest("testAdderElmThreeInputs", () -> testAdderElmThreeInputs());
        runTest("testSubtracterElm", () -> testSubtracterElm());
        runTest("testSubtracterElmThreeInputs", () -> testSubtracterElmThreeInputs());
        runTest("testMultiplyConstElm", () -> testMultiplyConstElm());
        runTest("testMultiplyConstElmNegative", () -> testMultiplyConstElmNegative());
        runTest("testMultiplyElm", () -> testMultiplyElm());
        runTest("testDividerElm", () -> testDividerElm());
        runTest("testDividerElmThreeInputs", () -> testDividerElmThreeInputs());
        runTest("testDividerElmZeroDenominator", () -> testDividerElmZeroDenominator());
        runTest("testDifferentiatorElmConstant", () -> testDifferentiatorElmConstant());
        runTest("testDifferentiatorElmTriangle", () -> testDifferentiatorElmTriangle());
        runTest("testIntegratorElm", () -> testIntegratorElm());
        runTest("testPercentElm", () -> testPercentElm());
        runTest("testEquationElmWithParameter", () -> testEquationElmWithParameter());
        runTest("testODEElmConstant", () -> testODEElmConstant());
        runTest("testComplexMathCircuit", () -> testComplexMathCircuit());
        
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
