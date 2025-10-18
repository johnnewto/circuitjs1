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
    private double[] lastComputedRowValues;      // Track last column sums for convergence checking
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
        
        try {
            ExprState state = integrationStates[col];
            
            // On first step, initialize with the initial condition
            if (sim.t == 0.0) {
                state.lastOutput = integrationStates[col].lastOutput;
            }
            
            // Set up expression state
            state.values[0] = columnSum ; // 'a' = columnSum
            state.t = sim.t;
            
            // Evaluate integration expression: lastoutput + timestep * a
            // where 'a' is already scaled by integrationGain
            double result = integrationExpr.eval(state);
                        
            // Update the expression state for next time
            state.updateLastValues(result);
            state.lastOutput = result;
            integrationStates[col].lastOutput = result;

            
            return result;
            
        } catch (Exception e) {
            CirSim.console("GodlyTableElm: Error in integration calculation: " + e.getMessage());
            return integrationStates[col].lastOutput; // Return last known value on error
        }
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


    // ToDo:  prob should follow the doStep approach used in VCVS but seems to work well enough as a first pass
    // see : VCVS_doStep_Explanation.md

    @Override
    // Calculate computed values during simulation step (not during drawing)
    public void doStep() {
        // Update input pin values from circuit (from TableElm's doStep)
        for (int i = 0; i < getPostCount(); i++) {
            Pin p = pins[i];
            if (!p.output)
                p.value = volts[i] > getThreshold();
        }

        // Always compute column sums for convergence checking
        if (lastComputedRowValues == null) {
            lastComputedRowValues = new double[cols];
        }
        if (integratedValues == null) {
            integratedValues = new double[cols];
        }

        for (int col = 0; col < cols; col++) {
            // Calculate sum for this column using equation evaluation from all rows except for the first
            double columnSum = 0.0;
            for (int row = 0; row < rows; row++) {
                // Use getVoltageForCell which works with equations
                double v = getVoltageForCell(row, col);
                columnSum += v;
            }

            // Check for convergence
            if (Math.abs(columnSum - lastComputedRowValues[col]) > 1e-6) {
                sim.converged = false;
            }

            lastComputedRowValues[col] = columnSum;

            if (col < pins.length && pins[col].output) {
                sim.updateVoltageSource(0, nodes[col], pins[col].voltSource, integratedValues[col]);
            }
        }
    }
    

    @Override
    public void stepFinished() {
        // Don't call super.stepFinished() because we want to output integrated values,
        // not column sums like TableElm does

        // Perform integration only once per completed timestep (not during convergence iterations)
        for (int col = 0; col < cols; col++) {
            // Use the converged column sum from doStep()
            double columnSum = lastComputedRowValues != null ? lastComputedRowValues[col] : 0.0;

            // Perform integration on this column sum with proper initial condition
            integratedValues[col] = performIntegration(col, columnSum);

            // Register the integrated value with a distinct name (e.g., "Col1_Int")
            String integrationLabelName = outputNames[col];
            registerComputedValueAsLabeledNode(integrationLabelName, integratedValues[col]);


            // Update integration states for next timestep
            if (integrationStates[col] != null) {
                integrationStates[col].updateLastValues(integratedValues[col]);
            }
        }
    }
    
    @Override
    double getCurrentIntoNode(int n) {
        // n is the pin/post number (0 to cols-1)
        if (n < 0 || n >= cols) {
            return 0.0;
        }
        
        // Calculate custom current based on column sum (flow value)
        // Current is proportional to the flow (column sum before integration)
        // This represents the rate of change, which makes physical sense:
        // - Positive column sum (inflow) -> positive current into the node
        // - Negative column sum (outflow) -> negative current (current out of node)
        
        double columnSum = lastComputedRowValues != null ? lastComputedRowValues[n] : 0.0;
        
        // Scale the column sum to get current
        // Default: 1V column sum = 1mA current, make negative to show increase in stock as current flow into node
        double current = -columnSum * currentScale;
        
        return current;
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
    
    @Override
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            return new EditInfo("Current Scale (A/V)", currentScale, 0, 1);
        }
        // Continue with parent's edit info (shifted by 1)
        return super.getEditInfo(n - 1);
    }
    
    @Override
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            currentScale = ei.value;
        } else {
            // Pass to parent (shifted by 1)
            super.setEditValue(n - 1, ei);
        }
    }
    
    // Static helper methods
    public static Double getIntegratedValue(String columnHeader) {
        return ComputedValues.getComputedValue(columnHeader);
    }

    public static void resetColumnIntegration(String columnHeader) {
        ComputedValues.setComputedValue(columnHeader, 0.0);
    }
}