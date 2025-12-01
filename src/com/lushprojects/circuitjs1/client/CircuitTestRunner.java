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

import java.util.Vector;

/**
 * CircuitTestRunner - JUnit test infrastructure for CircuitJS1
 * 
 * Provides utilities for loading circuits, running simulations, and validating results.
 * 
 * STANDARDIZED TIMESTEP: 10 microseconds (10e-6 s)
 * - Balances accuracy with test execution speed
 * - Sufficient for most circuit dynamics
 * - Deterministic results for regression testing
 * 
 * FEATURES:
 * - Load circuits from text
 * - Run simulations for specified duration
 * - Measure node voltages, element currents, power
 * - Tolerance-based assertions for floating point comparisons
 * - Convergence validation
 * 
 * USAGE:
 * <pre>
 * CircuitTestRunner runner = new CircuitTestRunner();
 * runner.loadCircuitFromText(circuitDump);
 * runner.runToTime(0.1); // Run for 100ms
 * double voltage = runner.getNodeVoltage("output");
 * runner.assertVoltage("output", 8.0, 0.01); // 1% tolerance
 * </pre>
 */
public class CircuitTestRunner {
    
    private CirSim sim;
    
    // Standard timestep for all tests: 0.1 millisecond
    public static final double STANDARD_TIMESTEP = 1e-4;
    
    // Standard convergence criteria
    public static final int MAX_ITERATIONS = 5000;
    public static final double CONVERGENCE_THRESHOLD = 1e-3; // 0.1% convergence
    
    // Default tolerance for voltage/current comparisons (1%)
    public static final double DEFAULT_TOLERANCE = 0.01;
    
    /**
     * Create a test runner using the existing simulator instance
     * More efficient than creating a new simulator for each test
     */
    public CircuitTestRunner() {
        sim = CirSim.theSim;
        
        // Configure for deterministic testing
        sim.maxTimeStep = STANDARD_TIMESTEP;
        sim.minTimeStep = STANDARD_TIMESTEP;
        sim.timeStep = STANDARD_TIMESTEP;
        sim.adjustTimeStep = false; // Fixed timestep for consistency
        sim.simRunning = false; // Start paused
    }
    
    /**
     * Format a number to 6 decimal places (GWT-compatible replacement for String.format)
     */
    private static String formatNumber(double value) {
        // Round to 6 decimal places
        double rounded = Math.round(value * 1000000.0) / 1000000.0;
        String str = Double.toString(rounded);
        
        // Ensure we have at least 6 decimal places
        int dotIndex = str.indexOf('.');
        if (dotIndex == -1) {
            str += ".000000";
        } else {
            int decimals = str.length() - dotIndex - 1;
            if (decimals < 6) {
                for (int i = decimals; i < 6; i++) {
                    str += "0";
                }
            }
        }
        return str;
    }
    
    /**
     * Load circuit from text string
     * 
     * @param circuitText Circuit dump in CircuitJS1 format
     */
    public void loadCircuitFromText(String circuitText) {
        sim.readCircuit(circuitText);
        sim.needAnalyze();
        // Force immediate analysis to set up node arrays
        sim.analyzeCircuit();
        // Keep simulation paused to prevent automatic circuit switching
        sim.simRunning = false;
    }
    
    /**
     * Run simulation to specified time
     * 
     * @param targetTime Target simulation time in seconds
     * @throws RuntimeException if simulation fails or doesn't converge
     */
    public void runToTime(double targetTime) {
        sim.simRunning = true;
        
        // Use actual timestep from circuit, not STANDARD_TIMESTEP
        int maxSteps = (int) (targetTime / sim.timeStep) + 1000; // Safety margin
        int steps = 0;
        double endTime = targetTime+sim.timeStep;
        while (sim.t <= endTime && steps < maxSteps) {  // Run until target time reached
            sim.updateCircuit();
            steps++;
            
            if (sim.stopMessage != null) {
                throw new RuntimeException("Simulation stopped: " + sim.stopMessage);
            }
        }
        
        if (steps >= maxSteps) {
            throw new RuntimeException("Simulation timeout: exceeded " + maxSteps + " steps");
        }
        // sim.updateCircuit();
        sim.simRunning = false;

    }
    
    /**
     * Run simulation for specified number of iterations
     * 
     * @param iterations Number of timesteps to execute
     */
    public void runIterations(int iterations) {
        sim.simRunning = true;
        
        for (int i = 0; i < iterations; i++) {
            sim.updateCircuit();
            
            if (sim.stopMessage != null) {
                throw new RuntimeException("Simulation stopped at iteration " + i + ": " + sim.stopMessage);
            }
        }
        // sim.updateCircuit();
        sim.simRunning = false;

    }
    
    /**
     * Run until circuit reaches steady state
     * 
     * Monitors node voltages; when they stabilize (change < threshold), considers circuit stable.
     * 
     * @param timeout Maximum time to wait (seconds)
     * @param stabilityThreshold Maximum voltage change to consider stable (default 1e-6)
     * @throws RuntimeException if timeout reached
     */
    public void runToSteadyState(double timeout, double stabilityThreshold) {
        sim.simRunning = true;
        
        double[] lastVoltages = null;
        int stableCount = 0;
        int requiredStableSteps = 100; // Must be stable for 100 consecutive steps
        
        // Use actual timestep from circuit, not STANDARD_TIMESTEP
        int maxSteps = (int) (timeout / sim.timeStep);
        int steps = 0;
        
        while (steps < maxSteps) {
            sim.updateCircuit();
            steps++;
            
            if (sim.stopMessage != null) {
                throw new RuntimeException("Simulation stopped: " + sim.stopMessage);
            }
            
            // Check stability
            if (lastVoltages != null && sim.nodeVoltages != null) {
                boolean stable = true;
                for (int i = 0; i < Math.min(lastVoltages.length, sim.nodeVoltages.length); i++) {
                    if (Math.abs(sim.nodeVoltages[i] - lastVoltages[i]) > stabilityThreshold) {
                        stable = false;
                        break;
                    }
                }
                
                if (stable) {
                    stableCount++;
                    if (stableCount >= requiredStableSteps) {
                        sim.simRunning = false;
                        return; // Steady state reached
                    }
                } else {
                    stableCount = 0;
                }
            }
            
            // Save current voltages
            if (sim.nodeVoltages != null) {
                lastVoltages = new double[sim.nodeVoltages.length];
                for (int i = 0; i < sim.nodeVoltages.length; i++) {
                    lastVoltages[i] = sim.nodeVoltages[i];
                }
            }
        }
        
        throw new RuntimeException("Steady state timeout after " + timeout + " seconds");
    }
    
    /**
     * Run to steady state with default threshold
     */
    public void runToSteadyState(double timeout) {
        runToSteadyState(timeout, 1e-6);
    }
    
    /**
     * Reset simulation to time 0
     */
    public void reset() {
        sim.resetAction();
    }
    
    /**
     * Get voltage at a labeled node
     * 
     * @param label Node label (from LabeledNodeElm)
     * @return Voltage at node in volts
     * @throws RuntimeException if label not found
     */
    public double getNodeVoltage(String label) {
        double voltage = sim.getLabeledNodeVoltage(label);
        if (Double.isNaN(voltage)) {
            throw new RuntimeException("Node not found: " + label);
        }
        return voltage;
    }
    
    /**
     * Get voltage at an element's post (terminal)
     * 
     * @param elm Element to measure
     * @param post Post number (0-based)
     * @return Voltage at post in volts
     */
    public double getPostVoltage(CircuitElm elm, int post) {
        if (elm == null) {
            throw new RuntimeException("Element is null");
        }
        int node = elm.getNode(post);
        if (node < 0 || node >= sim.nodeVoltages.length) {
            throw new RuntimeException("Invalid node: " + node);
        }
        return sim.nodeVoltages[node];
    }
    
    /**
     * Get current through an element
     * 
     * @param elm Element to measure
     * @return Current in amperes
     */
    public double getElementCurrent(CircuitElm elm) {
        return elm.getCurrent();
    }
    
    /**
     * Get power dissipated by an element
     * 
     * @param elm Element to measure
     * @return Power in watts
     */
    public double getElementPower(CircuitElm elm) {
        return elm.getPower();
    }
    
    /**
     * Find element by class type
     * 
     * @param clazz Element class to find
     * @return First element of that type, or null if not found
     */
    public CircuitElm findElement(Class clazz) {
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.getElm(i);
            if (clazz.equals(elm.getClass()) || elm.getClass().getName().equals(clazz.getName())) {
                return elm;
            }
        }
        return null;
    }
    
    /**
     * Find all elements of a given type
     * 
     * @param clazz Element class to find
     * @return Vector of elements (empty if none found)
     */
    public Vector findElements(Class clazz) {
        Vector found = new Vector();
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.getElm(i);
            if (clazz.equals(elm.getClass()) || elm.getClass().getName().equals(clazz.getName())) {
                found.add(elm);
            }
        }
        return found;
    }
    
    /**
     * Check if simulation converged on last iteration
     * 
     * @return true if converged, false if failed to converge
     */
    public boolean isConverged() {
        return sim.converged;
    }
    
    /**
     * Get current simulation time
     * 
     * @return Time in seconds
     */
    public double getTime() {
        return sim.t;
    }
    
    /**
     * Assert voltage matches expected value within tolerance
     * 
     * @param label Node label
     * @param expected Expected voltage
     * @param tolerance Relative tolerance (e.g., 0.01 for 1%)
     * @throws AssertionError if voltage doesn't match
     */
    public void assertVoltage(String label, double expected, double tolerance) {
        double actual = getNodeVoltage(label);
        double error = Math.abs(actual - expected);
        double relativeError = expected != 0 ? error / Math.abs(expected) : error;
        
        if (error > Math.abs(expected * tolerance)) {
            String msg = "Voltage mismatch at '" + label + "': expected " + 
                formatNumber(expected) + " V, got " + formatNumber(actual) + 
                " V (error: " + formatNumber(error) + " V, " + 
                formatNumber(relativeError * 100) + "%)";
            throw new AssertionError(msg);
        }
    }
    
    /**
     * Assert voltage matches expected value within default tolerance (1%)
     */
    public void assertVoltage(String label, double expected) {
        assertVoltage(label, expected, DEFAULT_TOLERANCE);
    }
    
    /**
     * Assert current matches expected value within tolerance
     * 
     * @param elm Element to measure
     * @param expected Expected current
     * @param tolerance Relative tolerance
     */
    public void assertCurrent(CircuitElm elm, double expected, double tolerance) {
        double actual = getElementCurrent(elm);
        double error = Math.abs(actual - expected);
        
        if (error > Math.abs(expected * tolerance)) {
            String msg = "Current mismatch in " + elm.getClass().getName() + 
                ": expected " + formatNumber(expected) + " A, got " + 
                formatNumber(actual) + " A (error: " + formatNumber(error) + " A)";
            throw new AssertionError(msg);
        }
    }
    
    /**
     * Assert current matches expected value within default tolerance (1%)
     */
    public void assertCurrent(CircuitElm elm, double expected) {
        assertCurrent(elm, expected, DEFAULT_TOLERANCE);
    }
    
    /**
     * Assert power matches expected value within tolerance
     * 
     * @param elm Element to measure
     * @param expected Expected power
     * @param tolerance Relative tolerance
     */
    public void assertPower(CircuitElm elm, double expected, double tolerance) {
        double actual = getElementPower(elm);
        double error = Math.abs(actual - expected);
        
        if (error > Math.abs(expected * tolerance)) {
            String msg = "Power mismatch in " + elm.getClass().getName() + 
                ": expected " + formatNumber(expected) + " W, got " + 
                formatNumber(actual) + " W (error: " + formatNumber(error) + " W)";
            throw new AssertionError(msg);
        }
    }
    
    /**
     * Assert simulation converged
     * 
     * @throws AssertionError if not converged
     */
    public void assertConverged() {
        if (!sim.converged) {
            throw new AssertionError("Circuit failed to converge");
        }
    }
    
    /**
     * Assert no errors occurred during simulation
     * 
     * @throws AssertionError if stopMessage is set
     */
    public void assertNoErrors() {
        if (sim.stopMessage != null) {
            throw new AssertionError("Simulation error: " + sim.stopMessage);
        }
    }
    
    /**
     * Get internal simulator instance (for advanced testing)
     * 
     * @return CirSim instance
     */
    public CirSim getSimulator() {
        return sim;
    }
    
    /**
     * Dump circuit state for debugging
     * 
     * @return Human-readable circuit state
     */
    public String dumpState() {
        StringBuilder sb = new StringBuilder();
        sb.append("Circuit State at t=");
        sb.append(sim.t);
        sb.append("s\n");
        sb.append("Converged: ");
        sb.append(sim.converged);
        sb.append("\n");
        sb.append("Elements: ");
        sb.append(sim.elmList.size());
        sb.append("\n");
        sb.append("Nodes: ");
        sb.append(sim.nodeList != null ? sim.nodeList.size() : 0);
        sb.append("\n");
        
        if (sim.nodeVoltages != null) {
            sb.append("\nNode Voltages:\n");
            for (int i = 0; i < Math.min(10, sim.nodeVoltages.length); i++) {
                sb.append("  Node ");
                sb.append(i);
                sb.append(": ");
                sb.append(formatNumber(sim.nodeVoltages[i]));
                sb.append(" V\n");
            }
            if (sim.nodeVoltages.length > 10) {
                sb.append("  ... (");
                sb.append(sim.nodeVoltages.length - 10);
                sb.append(" more)\n");
            }
        }
        
        return sb.toString();
    }
}
