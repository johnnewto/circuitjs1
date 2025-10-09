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
 */
public class GodlyTableElm extends TableElm {
    private double integrationGain = 1.0;      // Multiplier for integration (default 1)
    private Expr integrationExpr;                // Compiled integration expression
    private ExprState[] integrationStates;       // Integration state for each column
    private double[] lastIntegrationOutputs;     // Previous integration outputs for each column
    private double[] lastComputedRowValues;      // Track last column sums for convergence checking
    private boolean showComputedRow = true;      // Show computed row at bottom (replaces showColumnSums)
    
    // Constructor for new table
    public GodlyTableElm(int xx, int yy) {
        super(xx, yy);
        initIntegration();
    }
    
    // File loading constructor  
    public GodlyTableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        // Call the TableElm constructor that accepts StringTokenizer
        super(xa, ya, xb, yb, f, st);
        // TableElm constructor will handle parsing its own data
        
        // Then parse GodlyTable-specific data
        parseGodlyTableData(st);
        initIntegration();
    }
    
    private void initIntegration() {
        // Initialize integration arrays based on current number of columns
        integrationStates = new ExprState[cols];
        lastIntegrationOutputs = new double[cols];
        
        
        for (int col = 0; col < cols; col++) {
            integrationStates[col] = new ExprState(1); // 1 input (columnSum as 'a')
            lastIntegrationOutputs[col] = 0.0;
        }
        
        // Parse the integration expression
        parseIntegrationExpr();
    }
    
    private double performIntegration(int col, double columnSum) {
        if (integrationExpr == null || col >= integrationStates.length) {
            return 0.0; // No integration if expression failed to parse
        }
        
        try {
            ExprState state = integrationStates[col];
            
            // Set up expression state
            state.values[0] = columnSum; // 'a' = columnSum
            state.lastOutput = lastIntegrationOutputs[col];
            state.t = sim.t;
            
            // Evaluate integration expression: lastoutput + timestep * gain * columnSum
            double result = integrationExpr.eval(state);
            
            // Store result for next iteration
            lastIntegrationOutputs[col] = result;
            
            // Update the expression state for next time
            state.updateLastValues(result);
            
            return result;
            
        } catch (Exception e) {
            CirSim.console("GodlyTableElm: Error in integration calculation: " + e.getMessage());
            return lastIntegrationOutputs[col]; // Return last known value on error
        }
    }
    
    private void parseIntegrationExpr() {
        try {
            // Create expression: lastoutput + timestep * integrationGain * a
            // Where 'a' will be the column sum
            String exprStr = "lastoutput+timestep*" + integrationGain + "*a";
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
    
    private void parseGodlyTableData(StringTokenizer st) {
        // Parse integration-specific data if available
        try {
            // Parse integration gain if present
            if (st.hasMoreTokens()) {
                integrationGain = Double.parseDouble(st.nextToken());
            }
            
            // Parse last integration outputs if present
            if (lastIntegrationOutputs == null) {
                lastIntegrationOutputs = new double[cols];
            }
            
            for (int col = 0; col < cols && st.hasMoreTokens(); col++) {
                lastIntegrationOutputs[col] = Double.parseDouble(st.nextToken());
            }
            
        } catch (Exception e) {
            CirSim.console("GodlyTableElm: error parsing integration data, using defaults");
            integrationGain = 100.0;
            // lastIntegrationOutputs will be initialized to zeros by initIntegration()
        }
    }
    
    @Override
    protected void drawSumRow(Graphics g) {
        int sumRowY = point1.y + 20 + cellSpacing + rows * (cellSize + cellSpacing);
        
        for (int col = 0; col < cols; col++) {
            int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
            
            // Get the already-calculated integrated value (calculated in stepFinished())
            String integrationLabelName = columnHeaders[col];
            Double computedIntegration = LabeledNodeElm.getComputedValue(integrationLabelName);
            double integratedValue = (computedIntegration != null) ? computedIntegration.doubleValue() : 0.0;
            
            // Draw integration cell background (different color for integration)
            g.setColor(Color.yellow); // Distinct color for integration cells
            g.fillRect(cellX, sumRowY, cellSize, cellSize);
            
            // Draw cell border
            g.setColor(Color.black);
            g.drawRect(cellX, sumRowY, cellSize, cellSize);
            
            // Draw integration label name (top half) 
            g.setColor(Color.black);
            drawCenteredText(g, integrationLabelName, cellX + cellSize/2, sumRowY + cellSize/3, true);
            
            // Draw integrated value (bottom half)
            String integrationText = getVoltageText(integratedValue);
            drawCenteredText(g, integrationText, cellX + cellSize/2, sumRowY + 2*cellSize/3, true);
        }
    }
    
    @Override
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.dump()); // Get TableElm serialization
        sb.append(" ").append(integrationGain); // Add integration gain
        
        // Add integration states (last outputs)
        for (int col = 0; col < cols; col++) {
            sb.append(" ").append(lastIntegrationOutputs[col]);
        }
        
        return sb.toString();
    }
    
    @Override
    int getDumpType() { 
        return 255; // Choose unused dump type (different from TableElm's 253)
    }
    
    @Override
    public EditInfo getEditInfo(int n) {
        // First handle integration-specific edits
        if (n == 0) {
            return new EditInfo("Integration Gain", integrationGain, 0.1, 1000.0);
        }
        if (n == 1) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Reset Integration", false);
            return ei;
        }
        
        // Delegate to parent for other edits, but offset index
        return super.getEditInfo(n - 2);
    }

    @Override 
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            // Integration gain changed
            integrationGain = ei.value;
            parseIntegrationExpr(); // Reparse expression with new gain
            return;
        }
        if (n == 1) {
            // Reset integration checkbox
            if (ei.checkbox.getState()) {
                resetIntegration();
            }
            return;
        }
        
        // Delegate to parent for other edits
        super.setEditValue(n - 2, ei);
        
        // If table structure changed, reinitialize integration
        if (n - 2 <= 1) { // If rows or cols changed
            initIntegration();
        }
    }

    private void resetIntegration() {
        for (int col = 0; col < cols; col++) {
            lastIntegrationOutputs[col] = 0.0;
            if (integrationStates[col] != null) {
                integrationStates[col].reset();
            }
        }
    }
    
    @Override
    public void reset() {
        super.reset();
        resetIntegration();
    }

    @Override  
    // Calculate computed values during simulation step (not during drawing)
    public void doStep() {
        super.doStep();
        if (showComputedRow) {
            if (lastComputedRowValues == null) {
                lastComputedRowValues = new double[cols];
            }
            
            for (int col = 0; col < cols; col++) {
                 // Calculate sum for this column
                double columnSum = 0.0;
                for (int row = 0; row < rows; row++) {
                    String labelName = labelNames[row][col];
                    columnSum += getVoltageForLabel(labelName);
                }
                
                // Check for convergence
                if (Math.abs(columnSum - lastComputedRowValues[col]) > 1e-6) {
                    sim.converged = false;
                }
                
                lastComputedRowValues[col] = columnSum;
                
                // Perform integration on this column sum
                double integratedValue = performIntegration(col, columnSum);
                
                // Register the integrated value with a distinct name (e.g., "Col1_Int")
                String integrationLabelName = columnHeaders[col];
                registerComputedValueAsLabeledNode(integrationLabelName, integratedValue);
                
                // Update integration states for next timestep
                if (integrationStates[col] != null) {
                    integrationStates[col].updateLastValues(integratedValue);
                }
            }
        }
    }
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "Godly Table (" + rows + "x" + cols + ") with Integration";
        arr[1] = "Integration gain: " + integrationGain;
        arr[2] = "Equation: lastoutput + timestep * " + integrationGain + " * columnSum";
        
        int idx = 3;
        // Show some sample values including integration results
        for (int row = 0; row < Math.min(2, rows) && idx < arr.length - 2; row++) {
            for (int col = 0; col < Math.min(2, cols) && idx < arr.length - 2; col++) {
                String label = labelNames[row][col];
                double voltage = getVoltageForLabel(label);
                arr[idx++] = label + ": " + getVoltageText(voltage);
            }
        }
        
        // Show integration results for first few columns
        for (int col = 0; col < Math.min(2, cols) && idx < arr.length - 1; col++) {
            String integrationLabel = columnHeaders[col] + "Σ";
            Double integratedValue = LabeledNodeElm.getComputedValue(integrationLabel);
            if (integratedValue != null) {
                arr[idx++] = integrationLabel + ": " + getVoltageText(integratedValue.doubleValue());
            }
        }
        
        if (rows * cols > 4) {
            arr[idx++] = "... (showing first few cells)";
        }
    }
    
    // Static helper methods
    public static Double getIntegratedValue(String columnHeader) {
        return LabeledNodeElm.getComputedValue(columnHeader);
    }

    public static void resetColumnIntegration(String columnHeader) {
        LabeledNodeElm.setComputedValue(columnHeader, 0.0);
    }
}