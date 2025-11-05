/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * TableEquationManager - Handles equation compilation and evaluation for TableElm
 * Separates equation management from circuit simulation logic
 */
public class TableEquationManager {
    private final TableElm table;
    private final CirSim sim;
    
    public TableEquationManager(TableElm table, CirSim sim) {
        this.table = table;
        this.sim = sim;
    }
    
    /**
     * Recompile all equations when labeled nodes change
     */
    public void recompileAllEquations() {
        for (int row = 0; row < table.rows; row++) {
            for (int col = 0; col < table.cols; col++) {
                if (table.cellEquations[row][col] != null && !table.cellEquations[row][col].trim().isEmpty()) {
                    compileEquation(row, col, table.cellEquations[row][col]);
                }
            }
        }
    }
    
    /**
     * Compile a single equation
     */
    public void compileEquation(int row, int col, String equation) {
        if (!isValidCell(row, col)) {
            return;
        }
        
        if (equation == null || equation.trim().isEmpty()) {
            table.compiledExpressions[row][col] = null;
            return;
        }
        
        try {
            ExprParser parser = new ExprParser(equation);
            table.compiledExpressions[row][col] = parser.parseExpression();
            String err = parser.gotError();
            if (err != null) {
                // Provide helpful error message with available variables
                String availableVars = getAvailableVariablesString();
                CirSim.console("TableElm: Parse error in equation [" + row + "][" + col + "]: " + equation + ": " + err);
                CirSim.console("TableElm: " + availableVars);
                table.compiledExpressions[row][col] = null;
                return;
            }
        } catch (Exception e) {
            String availableVars = getAvailableVariablesString();
            CirSim.console("TableElm: Exception parsing equation [" + row + "][" + col + "]: " + e.getMessage());
            CirSim.console("TableElm: " + availableVars);
            table.compiledExpressions[row][col] = null;
        }
    }
    
    /**
     * Update expression state with current simulation time
     */
    public void updateExpressionState(ExprState state) {
        // Only update time - direct node resolution handles everything else
        state.t = sim != null ? sim.t : 0.0;
        
        // All node references are resolved directly in Expr.eval() via E_NODE_REF
    }
    
    /**
     * Get voltage value for a cell by evaluating its equation
     */
    public double 
    getVoltageForCell(int row, int col) {
        if (!isValidCell(row, col)) {
            return 0.0;
        }
        
        // Check if this is an A-L-E computed column
        // A-L-E is always the last column when there are 4+ columns
        boolean isALEColumn = (col == table.cols - 1 && table.cols >= 4);
        if (isALEColumn) {
            // Return the precomputed A-L-E value for this row
            return table.getComputedALEValue(row);
        }
        
        // All cells now use equations
        Expr e = table.compiledExpressions[row][col];
        if (e != null) {
            // Fast-path: if the compiled expression is a direct node reference,
            // return the value without invoking the general evaluator.
            // This avoids overhead of recursion for the very common case of
            // a cell that simply references a labeled node.
            if (e.type == Expr.E_NODE_REF && e.nodeName != null) {
                // First check for computed values (from TableElm or other sources)
                Double computedValue = ComputedValues.getComputedValue(e.nodeName);
                if (computedValue != null) {
                    return computedValue.doubleValue();
                }
                // Fall back to the labeled node voltage from the simulator
                return sim != null ? sim.getLabeledNodeVoltage(e.nodeName) : 0.0;
            }

            // Otherwise evaluate the compiled expression normally
            ExprState state = table.expressionStates[row][col];
            updateExpressionState(state);
            return e.eval(state);
        }
        return 0.0;
    }
    
    /**
     * Helper method to show which variables are available for equations
     */
    private String getAvailableVariablesString() {
        StringBuilder sb = new StringBuilder();
        String[] availableNodes = LabeledNodeElm.getSortedLabeledNodeNames();
        
        sb.append("Available: t (time)");
        
        // Show direct node names (only method now)
        for (String nodeName : availableNodes) {
            sb.append(", ").append(nodeName);
        }
        
        if (availableNodes.length == 0) {
            sb.append(", no labeled nodes in circuit");
        }
        
        return sb.toString();
    }
    
    /**
     * Check if cell coordinates are valid
     */
    private boolean isValidCell(int row, int col) {
        return row >= 0 && row < table.rows && col >= 0 && col < table.cols;
    }
}
