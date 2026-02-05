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
    private Expr[] integrationExprs;             // Compiled integration expression PER COLUMN (to avoid state sharing)
    private ExprState[] integrationStates;       // Integration state for each column
    private double[] integratedValues;           // Integration value for each column
    private double[] lastColumnSums;             // Last column sums for convergence check
    private double currentScale = 0.001;         // Scale factor for current calculation (default 1mA per volt)
    
    // Alternative approach: stamp directly to LabeledNode nodes (no visible posts)
    private int[] labeledNodeNumbers;            // Node number for each column's LabeledNode (-1 if none)
    private int[] colVoltSources;                // Voltage source index for each column (-1 if none)
    private int voltSourceCount;                 // Total voltage sources needed
    private double[] colCurrents;                // Current through each column's voltage source
    
    // Constructor for new table
    public GodlyTableElm(int xx, int yy) {
        super(xx, yy);
        showInitialValues = true; // GodlyTable shows initial values by default
        initIntegration();
    }
    
    // Get convergence limit (same as VCVSElm/VCCSElm)
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
        // Only initialize arrays if we have columns
        // Otherwise, ensureArraysSized() will be called later from setupPins()
        if (getCols() > 0) {
            ensureArraysSized();
            // Parse per-column expressions after we know column count
            parseIntegrationExprs();
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
        
        double lastOut = state.lastOutput;
        
        // Evaluate integration expression: lastoutput + timestep * a
        // Each column has its own Expr instance to avoid state sharing/caching issues
        Expr expr = (integrationExprs != null && col < integrationExprs.length) ? integrationExprs[col] : null;
        if (expr == null) {
            return lastOut;
        }
        double result = expr.eval(state);
        
        // Return result (state.lastOutput will be updated in stepFinished)
        return result;
    }
    
    private void parseIntegrationExprs() {
        int cols = getCols();
        if (cols == 0) return;
        
        integrationExprs = new Expr[cols];
        
        // Create a SEPARATE expression instance for each column
        // This avoids any internal state/caching issues in Expr.eval()
        // Note: Use _a (underscore prefix) for values[0] - plain 'a' would be parsed as a node reference
        String exprStr = "lastoutput + timestep*_a";
        
        for (int col = 0; col < cols; col++) {
            try {
                ExprParser parser = new ExprParser(exprStr);
                integrationExprs[col] = parser.parseExpression();
                String err = parser.gotError();
                if (err != null) {
                    CirSim.console("GodlyTableElm: Parse error in integration expression for col " + col + ": " + err);
                    integrationExprs[col] = null;
                }
            } catch (Exception e) {
                CirSim.console("GodlyTableElm: Error parsing integration expression for col " + col + ": " + e.getMessage());
                integrationExprs[col] = null;
            }
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
    
    @Override
    void setupPins() {
        // Don't call super.setupPins() - we don't use pins for output
        // super.setupPins();
        
        // Register as master if not already done (needed for priority system)
        if (columns != null) {
            for (int col = 0; col < columns.size(); col++) {
                TableColumn column = columns.get(col);
                if (!column.isALE() && !column.isEmpty()) {
                    String name = column.getStockName();
                    // Use base priority (weighted priority is handled in parent class)
                    ComputedValues.registerMasterTable(name.trim(), this, priority);
                }
            }
        }
        
        // Initialize pins array to avoid null checks (but with 0 posts)
        pins = new Pin[0];
        
        // Note: findLabeledNodes() is called in stamp() after internal nodes are allocated
        
        // Reinitialize integration arrays when columns change
        ensureArraysSized();
        // Note: Master column status is checked dynamically via isMasterForColumn()
    }
    
    /**
     * Override getPostCount to return 0 - no visible posts.
     * We stamp directly to LabeledNode nodes instead.
     */
    @Override
    int getPostCount() {
        return 0;  // No visible posts
    }
    
    /**
     * Override getVoltageSourceCount to allocate voltage sources for
     * each master column. One voltage source per master column.
     */
    @Override
    int getVoltageSourceCount() {
        // Same count as internal nodes - one per master column
        return countMasterColumns();
    }
    
    /**
     * Override getInternalNodeCount to create internal nodes only for master columns
     * that do NOT have an existing LabeledNode on the canvas.
     * Columns with existing LabeledNodes will use those nodes instead.
     */
    @Override
    int getInternalNodeCount() {
        if (columns == null) return 0;
        
        int cols = getCols();
        int colLimit = (cols >= 4) ? (cols - 1) : cols; // Exclude A-L-E column
        int count = 0;
        
        for (int col = 0; col < colLimit; col++) {
            if (isMasterForColumn(col)) {
                String stockName = columns.get(col).getStockName();
                if (stockName != null && !stockName.trim().isEmpty()) {
                    // Only need internal node if NO existing LabeledNode
                    Integer existingNode = LabeledNodeElm.getByName(stockName.trim());
                    if (existingNode == null || existingNode < 0) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
    
    /**
     * Count master columns that need voltage sources.
     * ALL master columns need voltage sources to drive their values.
     */
    private int countMasterColumns() {
        if (columns == null) return 0;
        
        int cols = getCols();
        int colLimit = (cols >= 4) ? (cols - 1) : cols; // Exclude A-L-E column
        int count = 0;
        
        for (int col = 0; col < colLimit; col++) {
            if (isMasterForColumn(col)) {
                String stockName = columns.get(col).getStockName();
                if (stockName != null && !stockName.trim().isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * Find or create nodes for each master column.
     * If a LabeledNode exists on canvas, use that node.
     * Otherwise, use our internal node and register it in labelList.
     * Called during stamp() after internal nodes are allocated.
     */
    private void findLabeledNodes() {
        int cols = getCols();
        labeledNodeNumbers = new int[cols];
        colVoltSources = new int[cols];
        colCurrents = new double[cols];
        voltSourceCount = 0;
        
        int colLimit = (cols >= 4) ? (cols - 1) : cols; // Exclude A-L-E column
        int internalNodeIdx = 0;  // Index into internal nodes (nodes[] array)
        
        for (int col = 0; col < cols; col++) {
            labeledNodeNumbers[col] = -1;
            colVoltSources[col] = -1;
            
            if (col >= colLimit) continue;  // Skip A-L-E column
            if (!isMasterForColumn(col)) continue;  // Only master columns
            
            String stockName = columns.get(col).getStockName();
            if (stockName == null || stockName.trim().isEmpty()) continue;
            
            // Check if a LabeledNode already exists on canvas
            Integer existingNodeNum = LabeledNodeElm.getByName(stockName.trim());
            
            if (existingNodeNum != null && existingNodeNum >= 0) {
                // Use existing LabeledNode's node - drive it with our voltage source
                labeledNodeNumbers[col] = existingNodeNum;
                colVoltSources[col] = voltSourceCount;
                voltSourceCount++;
            } else {
                // No existing LabeledNode - use our internal node and register it
                if (nodes != null && internalNodeIdx < nodes.length) {
                    int internalNode = nodes[internalNodeIdx];
                    labeledNodeNumbers[col] = internalNode;
                    
                    // Register this internal node so other elements can find it by name
                    registerInternalNodeAsLabel(stockName.trim(), internalNode);
                    
                    internalNodeIdx++;
                    
                    colVoltSources[col] = voltSourceCount;
                    voltSourceCount++;
                }
            }
        }
    }
    
    /**
     * Register an internal node number with LabeledNodeElm's static labelList.
     * This allows other elements to find this node by name using LabeledNodeElm.getByName().
     */
    private void registerInternalNodeAsLabel(String name, int nodeNum) {
        if (LabeledNodeElm.labelList == null) {
            LabeledNodeElm.resetNodeList();
        }
        
        // Create a LabelEntry for this name
        // Note: we don't have a Point for this, so we use a dummy position
        LabeledNodeElm.LabelEntry entry = new LabeledNodeElm.LabelEntry();
        entry.node = nodeNum;
        entry.point = new Point(x, y);  // Use table position as dummy
        
        LabeledNodeElm.labelList.put(name, entry);
    }
    
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
        
        // Resize integrationExprs array (one Expr per column to avoid state sharing)
        if (integrationExprs == null || integrationExprs.length != cols) {
            parseIntegrationExprs();  // Re-parse to create new array with correct size
        }
        
        // Note: Master column status is determined per-column via isMasterForColumn()
    }

    /**
     * Override stamp() to stamp voltage sources directly to LabeledNode nodes.
     * This is the alternative approach: no visible posts, direct connection.
     * Called once during analyzeCircuit(), after setupPins() and after voltage sources are allocated.
     */
    @Override
    void stamp() {
        // Refresh LabeledNode lookup (in case nodes changed since setupPins)
        findLabeledNodes();
        
        // Stamp voltage sources from ground to each LabeledNode's node
        int cols = getCols();
        int colLimit = (cols >= 4) ? (cols - 1) : cols; // Exclude A-L-E column
        
        for (int col = 0; col < colLimit; col++) {
            if (labeledNodeNumbers == null || col >= labeledNodeNumbers.length) continue;
            
            int nodeNum = labeledNodeNumbers[col];
            int vs = colVoltSources[col];
            
            if (nodeNum >= 0 && vs >= 0) {
                // Stamp voltage source from ground (0) to the LabeledNode's node
                // This drives the LabeledNode to the integrated value
                int vn = voltSource + vs + sim.nodeList.size();
                sim.stampNonLinear(vn);
                sim.stampVoltageSource(0, nodeNum, voltSource + vs);
                
                // IMPORTANT: Stamp a tiny load resistance to ground to prevent
                // the matrix solver from eliminating this node as trivial.
                // Without this, unconnected internal nodes get optimized away.
                double loadResistance = 1e9;  // 1 gigaohm - tiny load
                sim.stampResistor(nodeNum, 0, loadResistance);
            }
        }
    }
    
    /**
     * Override setCurrent to track current for each column's voltage source.
     */
    @Override
    public void setCurrent(int vn, double c) {
        if (colCurrents == null || colVoltSources == null) return;
        
        // Find which column this voltage source belongs to
        for (int col = 0; col < colVoltSources.length; col++) {
            if (colVoltSources[col] >= 0 && voltSource + colVoltSources[col] == vn) {
                colCurrents[col] = c;
                return;
            }
        }
    }
    
    /**
     * Override setVoltageSource to store voltage source indices.
     * Unlike TableElm which uses pins, we track voltage sources in colVoltSources[].
     */
    @Override
    void setVoltageSource(int j, int vs) {
        // Store base voltage source for compatibility
        if (j == 0) {
            voltSource = vs;
        }
        // Voltage source allocation is handled in findLabeledNodes() based on colVoltSources[]
        // No need to assign to pins since we don't have any
    }
    
    /**
     * Override getCurrentIntoNode to return current for each column.
     * Since we don't have visible posts, we return current based on column index.
     */
    double getCurrentForColumn(int col) {
        if (colCurrents != null && col >= 0 && col < colCurrents.length) {
            return colCurrents[col];
        }
        return 0;
    }

    @Override
    public void doStep() {
        // Track if this element caused convergence failure
        boolean wasConverged = sim.converged;
        
        // No input pins in this alternative approach (getPostCount() returns 0)

        // Initialize arrays if needed
        if (lastColumnSums == null) {
            lastColumnSums = new double[getCols()];
        }
        if (integratedValues == null) {
            integratedValues = new double[getCols()];
        }

        // Like VCVSElm: compute outputs and stamp during each iteration
        // Performance optimizations:
        // 1. Skip A-L-E column by limiting loop (it's always the last column)
        // 2. Check master status per-column using isMasterForColumn()
        // 3. Avoid boxing by using primitives throughout
        int colLimit = (getCols() >= 4) ? (getCols() - 1) : getCols(); // Exclude A-L-E column if it exists
        
        // Get convergence limit for input checking (like VCVSElm)
        double convergeLimit = getConvergeLimit();
        
        for (int col = 0; col < colLimit; col++) {
            // Check if this column is mastered by this table (may vary per column)
            boolean isMasterForThisName = isMasterForColumn(col);
            
            // Only compute if we are the master for this specific column
            if (!isMasterForThisName) {
                continue;
            }
            
            // Compute column sum (like parent does) and cache individual cell values
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
            
            // Like VCVSElm: check input convergence using dynamic threshold
            // Check if column sum (our "input") has converged
            if (Math.abs(columnSum - lastColumnSums[col]) > convergeLimit) {
                sim.converged = false;

                // Debug: log convergence failure details
                if (sim.subIterations > 20) {
                    CirSim.console("GodlyTable[" + columns.get(col).getStockName() + "] col " + col + 
                                 " sum convergence failed: diff=" + Math.abs(columnSum - lastColumnSums[col]) + 
                                 " limit=" + convergeLimit +
                                 " (new=" + columnSum + ", old=" + lastColumnSums[col] + 
                                 ") at t=" + sim.t + " subiter=" + sim.subIterations);
                }
            }
            lastColumnSums[col] = columnSum;
            // if (sim.subIterations <= 100)
            //     sim.converged = false;

            // Perform integration on the column sum (like VCVSElm evaluates expression)
            double integratedValue = performIntegration(col, columnSum);
            integratedValues[col] = integratedValue;
            
            // Alternative approach: stamp directly to LabeledNode's node
            // Check if this column has a matching LabeledNode with a voltage source
            if (labeledNodeNumbers != null && col < labeledNodeNumbers.length &&
                labeledNodeNumbers[col] >= 0 && colVoltSources[col] >= 0) {
                
                int vn = voltSource + colVoltSources[col] + sim.nodeList.size();
                
                // Stamp the right side with the integrated value
                sim.stampRightSide(vn, integratedValue);
            }
        }
        
        // Debug: overall convergence status for this element
        if (wasConverged && !sim.converged && sim.subIterations > 20) {
            CirSim.console("GodlyTable (" + rows + "x" + getCols() + ") at (" + x + "," + y + 
                         ") caused convergence failure at subiter=" + sim.subIterations);
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
    
    // Current is tracked in colCurrents[] via setCurrent() override
    // Current flows through voltage sources connected to LabeledNode nodes

    @Override
    void getInfo(String arr[]) {
        arr[0] = "Godly Table (" + rows + "x" + getCols() + ") with Integration";
        arr[1] = "Equation: y[n+1] = y[n] + dt * columnSum";

        int idx = 2;

        // Show output values (integrated results) and currents
        for (int col = 0; col < Math.min(getCols(), 2) && idx < arr.length - 1; col++) {
            String header = columns.get(col).getStockName();
            
            // Show integrated value
            Double integratedValue = ComputedValues.getComputedValue(header);
            if (integratedValue != null) {
                arr[idx++] = header + "∫ = " + getVoltageText(integratedValue.doubleValue());
            }
            
            // Show current (from colCurrents, set via setCurrent)
            if (idx < arr.length - 1 && colCurrents != null && col < colCurrents.length) {
                double current = colCurrents[col];
                String currentDir = current >= 0 ? "→" : "←";
                arr[idx++] = header + " I " + currentDir + " = " + getUnitText(Math.abs(current), "A");
            }
        }

        if (getCols() > 2 && idx < arr.length - 1) {
            arr[idx++] = "... (" + getCols() + " integrated outputs total)";
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
    
    /**
     * Debug method to print detailed state information
     * Call this when investigating convergence issues
     */
    public void debugPrintState() {
        CirSim.console("=== GodlyTable Debug State ===");
        CirSim.console("Position: (" + x + "," + y + ")");
        CirSim.console("Size: " + rows + "x" + getCols());
        CirSim.console("Time: t=" + sim.t + ", timeStep=" + sim.timeStep);
        CirSim.console("Integration Expressions: " + (integrationExprs != null ? integrationExprs.length + " columns" : "NULL"));
        
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
