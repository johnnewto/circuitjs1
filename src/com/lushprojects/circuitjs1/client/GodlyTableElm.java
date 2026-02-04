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
 * GodlyTableElm - Pure Computational Table with Integration Capabilities
 * 
 * Extends TableElm but operates as a PURE COMPUTATIONAL element:
 * - No circuit posts or voltage sources
 * - Does not participate in MNA matrix solving
 * - Computes values and writes them to ComputedValues registry
 * - Other elements can read these values via ComputedValues or use
 *   ComputedValueSourceElm to bridge values into the electrical domain
 * 
 * Integration:
 * - Uses equation: lastoutput + timestep * columnSum
 * - Accumulated values are registered in ComputedValues for each column
 * 
 * Benefits of pure computational approach:
 * - Order-independent evaluation (no voltage source iteration needed)
 * - Lower computational cost (no matrix entries)
 * - Cleaner separation between accounting logic and electrical simulation
 * 
 * To connect a computed value to electrical circuit, use ComputedValueSourceElm.
 */
public class GodlyTableElm extends TableElm {
    private Expr integrationExpr;                // Compiled integration expression
    private ExprState[] integrationStates;       // Integration state for each column
    private double[] integratedValues;           // Integration value for each column
    private double[] lastColumnSums;             // Last column sums for convergence check
    private double[] lastIntegratedValues;       // For convergence checking
    private double currentScale = 0.001;         // Scale factor (kept for backward compat in dump)
    
    // Pure computational mode: no electrical posts
    private static final boolean PURE_COMPUTATIONAL = true;
    
    // Constructor for new table
    public GodlyTableElm(int xx, int yy) {
        super(xx, yy);
        showInitialValues = true; // GodlyTable shows initial values by default
        initIntegration();
    }
    
    //=========================================================================
    // PURE COMPUTATIONAL: Override circuit element methods
    //=========================================================================
    
    /**
     * Pure computational element has no posts.
     * Use ComputedValueSourceElm to bridge values to electrical domain.
     */
    @Override
    int getPostCount() {
        return 0;  // No electrical posts
    }
    
    /**
     * Pure computational element has no voltage sources.
     */
    @Override
    int getVoltageSourceCount() {
        return 0;  // No voltage sources
    }
    
    /**
     * Pure computational element doesn't stamp to MNA matrix.
     */
    @Override
    void stamp() {
        // No MNA matrix participation - pure computational
    }
    
    /**
     * Override setupPins to create no pins.
     */
    @Override
    void setupPins() {
        // Calculate visual size based on table dimensions (needed for drawing)
        int cellWidthPixels = getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 3 / 2;
        
        // Standard rows: header + data rows + initial values + computed values
        int totalRows = rows + 3; // +3 for header, initial, computed
        if (showInitialValues)
            totalRows++;
        
        int tableWidthPixels = (rowDescColWidth + getCols() * cellWidthPixels) + 2 * cellSpacing;
        int tableHeightPixels = (totalRows + 2) * cellHeight + (totalRows + 3) * cellSpacing + 20;
        
        sizeX = (tableWidthPixels + cspc2 - 1) / cspc2;
        sizeY = (tableHeightPixels + cspc2 - 1) / cspc2;
        
        // No pins for pure computational element
        pins = new Pin[0];
        
        // Still need to initialize integration arrays
        ensureArraysSized();
    }
    
    // Get convergence limit for pure computational convergence check
    // More lenient over time to help convergence
    double getConvergeLimit() {
        // Base relative tolerance (0.1% to 1% depending on iteration count)
        double relativeTolerance;
        if (sim.subIterations < 10)
            relativeTolerance = 0.001;  // 0.1% for early iterations
        else if (sim.subIterations < 100)
            relativeTolerance = 0.01;   // 1% for mid iterations
        else
            relativeTolerance = 0.1;    // 10% for late iterations (help convergence)
        
        // Find maximum absolute value across all integrated values and column sums
        // This makes the threshold scale with the magnitude of values
        double maxMagnitude = 1.0;  // Minimum threshold (prevent division by zero)
        
        if (integratedValues != null) {
            for (int i = 0; i < integratedValues.length; i++) {
                maxMagnitude = Math.max(maxMagnitude, Math.abs(integratedValues[i]));
            }
        }
        
        if (lastColumnSums != null) {
            for (int i = 0; i < lastColumnSums.length; i++) {
                maxMagnitude = Math.max(maxMagnitude, Math.abs(lastColumnSums[i]));
            }
        }
        
        // Return relative limit scaled by magnitude
        // For large values (e.g., 1000V), this gives reasonable thresholds (1V at 0.1%)
        return maxMagnitude * relativeTolerance;
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
        // Parse the integration expression (doesn't depend on column count)
        parseIntegrationExpr();
        
        // Only initialize arrays if we have columns
        // Otherwise, ensureArraysSized() will be called later from setupPins()
        if (getCols() > 0) {
            ensureArraysSized();
        }
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
        for (int col = 0; col < getCols(); col++) {
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
        
        // Register initial values with ComputedValues at t=0
        // This ensures labeled nodes show initial values before simulation starts
        // Skip A-L-E column by limiting loop (it's always the last column)
        int colLimit = (getCols() >= 4) ? (getCols() - 1) : getCols();
        
        for (int col = 0; col < colLimit; col++) {
            // Only register if we are the master for this column
            if (isMasterForColumn(col)) {
                String name = columns.get(col).getStockName();
                double initialValue = getInitialValue(col);
                registerComputedValueAsLabeledNode(name, initialValue);
            }
        }
    }
    
    // setupPins() is now overridden above for pure computational mode
    
    // Ensure all arrays are properly sized for current column count
    private void ensureArraysSized() {
        int cols = getCols();
        
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
        
        // Resize lastIntegratedValues array for convergence checking
        if (lastIntegratedValues == null || lastIntegratedValues.length != cols) {
            double[] oldValues = lastIntegratedValues;
            lastIntegratedValues = new double[cols];
            if (oldValues != null) {
                System.arraycopy(oldValues, 0, lastIntegratedValues, 0, Math.min(oldValues.length, cols));
            }
        }
        
        // Note: Master column status is determined per-column via isMasterForColumn()
    }

    @Override
    public void doStep() {
        // Pure computational: compute values and write to ComputedValues
        // No MNA matrix interaction
        
        // Initialize arrays if needed
        if (lastColumnSums == null) {
            lastColumnSums = new double[getCols()];
        }
        if (integratedValues == null) {
            integratedValues = new double[getCols()];
        }
        if (lastIntegratedValues == null) {
            lastIntegratedValues = new double[getCols()];
        }

        // Pure computational: compute outputs for all master columns
        // Performance optimizations:
        // 1. Skip A-L-E column by limiting loop (it's always the last column)
        // 2. Check master status per-column using isMasterForColumn()
        // 3. Avoid boxing by using primitives throughout
        int colLimit = (getCols() >= 4) ? (getCols() - 1) : getCols(); // Exclude A-L-E column if it exists
        
        // Get convergence limit for equation checking
        double convergeLimit = getConvergeLimit();
        
        for (int col = 0; col < colLimit; col++) {
            // Check if this column is mastered by this table (may vary per column)
            boolean isMasterForThisName = isMasterForColumn(col);
            
            // Only compute if we are the master for this specific column
            if (!isMasterForThisName) {
                continue;
            }
            
            // Compute column sum and cache individual cell values
            // Also track max cell magnitude for convergence threshold calculation
            // This handles the case where large values cancel to near-zero
            double columnSum = 0.0;
            double maxCellMagnitude = 0.0;
            TableColumn column = columns.get(col);
            for (int row = 0; row < rows; row++) {
                double cellValue = getVoltageForCell(row, col);
                column.setCachedCellValue(row, cellValue); // Cache the value for rendering
                columnSum += cellValue;
                maxCellMagnitude = Math.max(maxCellMagnitude, Math.abs(cellValue));
            }
            
            // Check equation convergence (integrated value stability)
            lastColumnSums[col] = columnSum;

            // Perform integration on the column sum
            double integratedValue = performIntegration(col, columnSum);
            integratedValues[col] = integratedValue;
            
            // Check convergence: has the integrated value stabilized?
            // Use threshold based on the larger of:
            // 1. Relative threshold (0.1%) of integrated value
            // 2. Relative threshold (0.1%) of max cell magnitude (handles large cancelling values)
            // 3. Minimum absolute threshold (1e-6) for numerical stability
            double threshold = Math.max(
                Math.max(Math.abs(integratedValue), maxCellMagnitude) * 0.001,
                1e-6
            );
            double valueDiff = Math.abs(integratedValue - lastIntegratedValues[col]);
            if (valueDiff > threshold && sim.subIterations < 100) {
                sim.converged = false;
                // Debug: log convergence failure details
                if (sim.subIterations > 20) {
                    CirSim.console("GodlyTable[" + columns.get(col).getStockName() + "] col " + col + 
                                 " value convergence failed: diff=" + valueDiff + 
                                 " threshold=" + threshold +
                                 " (new=" + integratedValue + ", old=" + lastIntegratedValues[col] + 
                                 ") at t=" + sim.t + " subiter=" + sim.subIterations);
                }
            }
            lastIntegratedValues[col] = integratedValue;
            
            // Write computed value to registry immediately (for intra-step reads)
            // This uses pending values that will be committed after all elements compute
            String name = columns.get(col).getStockName();
            ComputedValues.setComputedValue(name, integratedValue);
        }
    }
    

    @Override
    public void stepFinished() {
        // Like VCVSElm: update state after timestep converges
        // Register computed values for other elements to use
        // Performance: Skip A-L-E column by limiting loop (it's always the last column)
        int colLimit = (getCols() >= 4) ? (getCols() - 1) : getCols(); // Exclude A-L-E column if it exists
        
        for (int col = 0; col < colLimit; col++) {
            // Check if this column is mastered by this table (may vary per column)
            boolean isMasterForThisColumn = isMasterForColumn(col);
            
            // Only register if we are the master for this specific column
            if (isMasterForThisColumn && integratedValues != null) {
                String name = columns.get(col).getStockName();
                registerComputedValueAsLabeledNode(name, integratedValues[col]);
                ComputedValues.markComputedThisStep(name);
                
                // Update integration state for next timestep (like VCVSElm.stepFinished)
                if (integrationStates[col] != null) {
                    integrationStates[col].lastOutput = integratedValues[col];
                }
            }
        }
    }
    
    /**
     * Override to return integrated values (stocks) instead of column sums (flows)
     * for display in the "Computed" row.
     * At t=0, returns the initial value.
     * NOTE: A-L-E columns are NOT integrated - they use the base class implementation
     */
    @Override
    public double getComputedValueForDisplay(int col) {
        // Check if this is an A-L-E column (last column when getCols() >= 4)
        boolean isALEColumn = (col == getCols() - 1 && getCols() >= 4);
        
        if (isALEColumn) {
            // A-L-E column: use base class implementation (returns lastColumnSums)
            return super.getComputedValueForDisplay(col);
        }
        
        // At t=0 or before first step, return initial value
        if (sim.t == 0.0 || integratedValues == null) {
            return getInitialValue(col);
        }
        
        // Return integrated value (stock) for display
        if (col >= 0 && col < getCols() && integratedValues != null && col < integratedValues.length) {
            return integratedValues[col];
        }
        return 0.0;
    }
    
    @Override
    double getCurrentIntoNode(int n) {
        // Pure computational element has no posts, so no current flow
        return 0.0;
    }

    @Override
    void getInfo(String arr[]) {
        arr[0] = "Godly Table (" + rows + "x" + getCols() + ") Pure Computational";
        arr[1] = "Integration: y[n+1] = y[n] + dt * columnSum";
        arr[2] = "Values available via ComputedValues registry";

        int idx = 3;

        // Show integrated values (stocks)
        for (int col = 0; col < Math.min(getCols(), 3) && idx < arr.length - 1; col++) {
            String header = columns.get(col).getStockName();
            
            // Show integrated value - use converged value for stable display
            Double integratedValue = ComputedValues.getConvergedValue(header);
            if (integratedValue != null) {
                arr[idx++] = header + " = " + getVoltageText(integratedValue.doubleValue());
            }
        }

        if (getCols() > 3 && idx < arr.length - 1) {
            arr[idx++] = "... (" + getCols() + " computed outputs total)";
        }
        
        // Hint about bridging to circuit
        if (idx < arr.length - 1) {
            arr[idx++] = "Use CV Source to connect to circuit";
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
    
    /**
     * Get the integrated value for a column header.
     * Returns converged value for stable display.
     */
    public static Double getIntegratedValue(String columnHeader) {
        return ComputedValues.getConvergedValue(columnHeader);
    }

    public static void resetColumnIntegration(String columnHeader) {
        ComputedValues.setComputedValue(columnHeader, 0.0);
    }
    
    /**
     * Debug method to print detailed state information
     * Call this when investigating convergence issues
     */
    public void debugPrintState() {
        CirSim.console("=== GodlyTable Debug State ===");
        CirSim.console("Position: (" + x + "," + y + ")");
        CirSim.console("Size: " + rows + "x" + getCols());
        CirSim.console("Time: t=" + sim.t + ", timeStep=" + sim.timeStep);
        CirSim.console("Integration Expression: " + (integrationExpr != null ? "valid" : "NULL"));
        
        int colLimit = (getCols() >= 4) ? (getCols() - 1) : getCols();
        for (int col = 0; col < colLimit; col++) {
            CirSim.console("--- Column " + col + ": " + columns.get(col).getStockName() + " ---");
            CirSim.console("  Master for this column: " + isMasterForColumn(col));
            
            if (pins != null && col < pins.length) {
                Pin p = pins[col];
                CirSim.console("  Pin output: " + p.output);
                CirSim.console("  Pin voltage: " + volts[col]);
                if (p.output && p.voltSource >= 0) {
                    CirSim.console("  Voltage source index: " + p.voltSource);
                }
            }
            
            if (lastColumnSums != null && col < lastColumnSums.length) {
                CirSim.console("  Last column sum: " + lastColumnSums[col]);
            }
            
            if (integratedValues != null && col < integratedValues.length) {
                CirSim.console("  Integrated value: " + integratedValues[col]);
            }
            
            if (integrationStates != null && col < integrationStates.length && integrationStates[col] != null) {
                CirSim.console("  Integration lastOutput: " + integrationStates[col].lastOutput);
            }
            
            // Show cell values for this column
            CirSim.console("  Cell voltages:");
            for (int row = 0; row < rows; row++) {
                double cellVoltage = getVoltageForCell(row, col);
                CirSim.console("    [" + row + "," + col + "]: " + cellVoltage);
            }
        }
        CirSim.console("=== End Debug State ===");
    }
}
