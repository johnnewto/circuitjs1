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
 * GodlyTableElm - Table with Integration Capabilities
 * Extends TableElm to add integration of column sums over time
 * Uses equation: lastoutput + timestep * integrationGain * columnSum
 * 
 * Current Calculation:
 * - Each output pin has a current proportional to its column sum (flow)
 * - Current = columnSum * currentScale
 * - Default: 1V column sum produces 1mA current
 * - Positive flow (inflow) produces positive current into the node
 * - Negative flow (outflow) produces negative current (current out of node)
 * - This represents the rate of change, making physical sense in stock-flow modeling
 */
public class GodlyTableElm extends TableElm {
    private Expr integrationExpr;                // Compiled integration expression
    private ExprState[] integrationStates;       // Integration state for each column
    private double[] integratedValues;           // Integration value for each column
    private double currentScale = 0.001;         // Scale factor for current calculation (default 1mA per volt)
    
    // Constructor for new table
    public GodlyTableElm(int xx, int yy) {
        super(xx, yy);
        showInitialValues = true; // GodlyTable shows initial values by default
        initIntegration();
    }
    
    // File loading constructor  
    public GodlyTableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        // Call the TableElm constructor that accepts StringTokenizer
        super(xa, ya, xb, yb, f, st);
        // TableElm constructor will handle parsing its own data
        
        // Parse GodlyTableElm-specific data (currentScale)
        if (st.hasMoreTokens()) {
            try {
                currentScale = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                currentScale = 0.001; // Default value if parsing fails
                CirSim.console("GodlyTableElm: Error parsing currentScale, using default: " + e.getMessage());
            }
        }

        initIntegration();
    }
    
    private void initIntegration() {
        // Initialize integration arrays based on current number of columns
        integrationStates = new ExprState[cols];
        
        for (int col = 0; col < cols; col++) {
            integrationStates[col] = new ExprState(1); // 1 input (columnSum as 'a')
            // Initialize with the initial condition for this column
            double initialValue = getInitialValue(col);
            integrationStates[col].lastOutput = initialValue;

        }
        
        // Parse the integration expression
        parseIntegrationExpr();
    }
    
    private double performIntegration(int col, double columnSum) {
        // Bounds check (should never fail if arrays are properly sized)
        if (col < 0 || col >= integrationStates.length) {
            return 0.0;
        }
        
        ExprState state = integrationStates[col];
        
        // On first step, use the initial condition
        if (sim.t == 0.0) {
            double initialValue = getInitialValue(col);
            state.lastOutput = initialValue;
            return initialValue;
        }
        
        // Set up expression state
        state.values[0] = columnSum; // 'a' = columnSum
        state.t = sim.t;
        
        // Evaluate integration expression: lastoutput + timestep * a
        double result = integrationExpr.eval(state);
        
        // Return result (state.lastOutput will be updated in stepFinished)
        return result;
    }
    
    private void parseIntegrationExpr() {
        try {
            // Create expression: lastoutput + timestep * a
            // Where 'a' will be (columnSum * integrationGain) - gain is pre-applied
            // This implements numerical integration: y[n+1] = y[n] + dt * f(t,y)
            String exprStr = "lastoutput + timestep*a";
            ExprParser parser = new ExprParser(exprStr);
            integrationExpr = parser.parseExpression();
            String err = parser.gotError();
            if (err != null) {
                CirSim.console("GodlyTableElm: Parse error in integration expression: " + exprStr + ": " + err);
                integrationExpr = null;
            }
        } catch (Exception e) {
            CirSim.console("GodlyTableElm: Error parsing integration expression: " + e.getMessage());
            integrationExpr = null;
        }
    }
    
    @Override
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.dump()); // Get TableElm serialization
        sb.append(" ").append(currentScale); // Add current scale to serialization
        return sb.toString();
    }
    
    @Override
    int getDumpType() { 
        return 255; // Choose unused dump type (different from TableElm's 253)
    }
    
    private void resetIntegration() {
        for (int col = 0; col < cols; col++) {
            // Reset integration to the initial condition for this column
            double initialValue = getInitialValue(col);
            if (integrationStates[col] != null) {
                integrationStates[col].reset();
                integrationStates[col].lastOutput = initialValue;
            }
        }
    }
    
    @Override
    public void reset() {
        super.reset();
        // Reset integration to initial conditions when circuit is reset
        resetIntegration();
    }
    
    @Override
    void setupPins() {
        super.setupPins();
        // Reinitialize integration arrays when pins/columns change
        ensureArraysSized();
        // Master column cache is updated by parent's setupPins()
    }
    
    // Ensure all arrays are properly sized for current column count
    private void ensureArraysSized() {
        // Check if integrationStates needs to be resized
        if (integrationStates == null || integrationStates.length != cols) {
            ExprState[] oldStates = integrationStates;
            integrationStates = new ExprState[cols];
            
            // Copy existing states and create new ones for new columns
            for (int col = 0; col < cols; col++) {
                if (oldStates != null && col < oldStates.length && oldStates[col] != null) {
                    // Keep existing state
                    integrationStates[col] = oldStates[col];
                } else {
                    // Create new state for new column
                    integrationStates[col] = new ExprState(1); // 1 input (columnSum as 'a')
                    double initialValue = getInitialValue(col);
                    integrationStates[col].lastOutput = initialValue;
                }
            }
        }
        
        // Resize integratedValues array
        if (integratedValues == null || integratedValues.length != cols) {
            double[] oldValues = integratedValues;
            integratedValues = new double[cols];
            if (oldValues != null) {
                System.arraycopy(oldValues, 0, integratedValues, 0, Math.min(oldValues.length, cols));
            }
        }
        
        // Note: Master column cache is managed by parent TableElm
    }

    @Override
    public void doStep() {
        // Update input pin values from circuit
        int postCount = getPostCount();
        for (int i = 0; i < postCount; i++) {
            Pin p = pins[i];
            if (!p.output)
                p.value = volts[i] > getThreshold();
        }

        // Initialize arrays if needed
        if (lastColumnSums == null) {
            lastColumnSums = new double[cols];
        }
        if (integratedValues == null) {
            integratedValues = new double[cols];
        }

        // Like VCVSElm: compute outputs and stamp during each iteration
        // Performance optimizations:
        // 1. Skip A-L-E column by limiting loop (it's always the last column)
        // 2. Use cached master check to reduce redundant lookups
        // 3. Avoid boxing by using primitives throughout
        int colLimit = (cols >= 4) ? (cols - 1) : cols; // Exclude A-L-E column if it exists
        
        for (int col = 0; col < colLimit; col++) {
            // Use parent's optimized master check (cached)
            boolean isMasterForThisName = isMasterForColumn(col);
            
            // Only compute if we are the master for this column
            if (!isMasterForThisName) {
                continue;
            }
            
            // Compute column sum (like parent does)
            double columnSum = 0.0;
            for (int row = 0; row < rows; row++) {
                columnSum += getVoltageForCell(row, col);
            }
            
            // Check for convergence on column sum (avoid boxing by using primitive)
            double diff = Math.abs(columnSum - lastColumnSums[col]);
            if (diff > 1e-6) {
                sim.converged = false;
            }
            lastColumnSums[col] = columnSum;
            
            // Perform integration on the column sum (like VCVSElm evaluates expression)
            double integratedValue = performIntegration(col, columnSum);
            integratedValues[col] = integratedValue;
            
            // Like VCVSElm: stamp matrix for nonlinear iteration
            // Combined check: only stamp if both master AND output (already verified master above)
            if (pins[col].output) {
                int vn = pins[col].voltSource + sim.nodeList.size();
                // Check output voltage convergence
                double outputVoltage = volts[col];
                if (Math.abs(outputVoltage - integratedValue) > Math.abs(integratedValue) * 0.01 && sim.subIterations < 100) {
                    sim.converged = false;
                }
                // Stamp the right side with the integrated value
                sim.stampRightSide(vn, integratedValue);
            }
        }
    }
    

    @Override
    public void stepFinished() {
        // Like VCVSElm: update state after timestep converges
        // Register computed values for other elements to use
        // Performance: Skip A-L-E column by limiting loop (it's always the last column)
        int colLimit = (cols >= 4) ? (cols - 1) : cols; // Exclude A-L-E column if it exists
        
        for (int col = 0; col < colLimit; col++) {
            // Use parent's optimized master check (cached)
            boolean isMasterForThisName = isMasterForColumn(col);
            
            // Only register if we are the master for this column
            if (isMasterForThisName && integratedValues != null) {
                String name = outputNames[col];
                registerComputedValueAsLabeledNode(name, integratedValues[col]);
                ComputedValues.markComputedThisStep(name);
                
                // Update integration state for next timestep (like VCVSElm.stepFinished)
                if (integrationStates[col] != null) {
                    integrationStates[col].lastOutput = integratedValues[col];
                }
            }
        }
    }
    
    @Override
    double getCurrentIntoNode(int n) {
        // n is the pin/post number (0 to cols-1)
        if (n < 0 || n >= cols || lastColumnSums == null || n >= lastColumnSums.length) {
            return 0.0;
        }
        
        // Calculate custom current based on column sum (flow value)
        // Current is proportional to the flow (column sum before integration)
        // This represents the rate of change, which makes physical sense:
        // - Positive column sum (inflow) -> positive current into the node
        // - Negative column sum (outflow) -> negative current (current out of node)
        
        double columnSum = lastColumnSums[n];
        
        // Scale the column sum to get current
        // Default: 1V column sum = 1mA current, make negative to show increase in stock as current flow into node
        return -columnSum * currentScale;
    }

    @Override
    void getInfo(String arr[]) {
        arr[0] = "Godly Table (" + rows + "x" + cols + ") with Integration";
        arr[1] = "Equation: y[n+1] = y[n] + dt * columnSum";
        arr[2] = "Current scale: " + getUnitText(currentScale, "A/V");

        int idx = 3;

        // Show output pin values (integrated results) and currents
        for (int col = 0; col < Math.min(cols, 2) && idx < arr.length - 1; col++) {
            String header = outputNames[col];
            
            // Show integrated value
            Double integratedValue = ComputedValues.getComputedValue(header);
            if (integratedValue != null) {
                arr[idx++] = header + "∫ = " + getVoltageText(integratedValue.doubleValue());
            }
            
            // Show current (flow)
            if (idx < arr.length - 1) {
                double current = getCurrentIntoNode(col);
                String currentDir = current >= 0 ? "→" : "←";
                arr[idx++] = header + " I " + currentDir + " = " + getUnitText(Math.abs(current), "A");
            }
        }

        if (cols > 2 && idx < arr.length - 1) {
            arr[idx++] = "... (" + cols + " integrated outputs total)";
        }
    }
    
    // @Override
    // public EditInfo getEditInfo(int n) {
    //     if (n == 0) {
    //         return new EditInfo("Current Scale (A/V)", currentScale, 0, 1);
    //     }
    //     // Continue with parent's edit info (shifted by 1)
    //     return super.getEditInfo(n - 1);
    // }
    
    // @Override
    // public void setEditValue(int n, EditInfo ei) {
    //     if (n == 0) {
    //         currentScale = ei.value;
    //     } else {
    //         // Pass to parent (shifted by 1)
    //         super.setEditValue(n - 1, ei);
    //     }
    // }
    
    // Static helper methods
    public static Double getIntegratedValue(String columnHeader) {
        return ComputedValues.getComputedValue(columnHeader);
    }

    public static void resetColumnIntegration(String columnHeader) {
        ComputedValues.setComputedValue(columnHeader, 0.0);
    }
}