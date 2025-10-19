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
    public double getVoltageForCell(int row, int col) {
        if (!isValidCell(row, col)) {
            return 0.0;
        }
        
        // All cells now use equations
        if (table.compiledExpressions[row][col] != null) {
            // Evaluate the compiled expression
            ExprState state = table.expressionStates[row][col];
            updateExpressionState(state);
            return table.compiledExpressions[row][col].eval(state);
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
