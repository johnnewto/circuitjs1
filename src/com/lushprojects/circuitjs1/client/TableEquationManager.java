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
        for (int col = 0; col < table.columns.size(); col++) {
            TableColumn column = table.columns.get(col);
            for (int row = 0; row < table.rows; row++) {
                String equation = column.getCellEquation(row);
                if (equation != null && !equation.trim().isEmpty()) {
                    compileEquation(row, col, equation);
                }
            }
        }
    }
    
    /**
     * Compile a single equation
     */
    public void compileEquation(int row, int col, String equation) {
        if (!isValidCell(row, col)) return;
        
        TableColumn column = table.columns.get(col);
        
        if (equation == null || equation.trim().isEmpty()) {
            column.setCompiledExpression(row, null);
            return;
        }
        
        try {
            ExprParser parser = new ExprParser(equation);
            Expr compiledExpr = parser.parseExpression();
            String err = parser.gotError();
            
            if (err != null) {
                logParseError(row, col, equation, err);
                column.setCompiledExpression(row, null);
            } else {
                column.setCompiledExpression(row, compiledExpr);
            }
        } catch (Exception e) {
            logParseException(row, col, e);
            column.setCompiledExpression(row, null);
        }
    }
    
    /**
     * Log parse error with context
     */
    private void logParseError(int row, int col, String equation, String error) {
        CirSim.console("TableElm: Parse error [" + row + "][" + col + "]: " + equation + ": " + error);
        CirSim.console("TableElm: " + getAvailableVariablesString());
    }
    
    /**
     * Log parse exception with context
     */
    private void logParseException(int row, int col, Exception e) {
        CirSim.console("TableElm: Exception [" + row + "][" + col + "]: " + e.getMessage());
        CirSim.console("TableElm: " + getAvailableVariablesString());
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
        if (!isValidCell(row, col)) return 0.0;
        
        TableColumn column = table.columns.get(col);
        Expr e = column.getCompiledExpression(row);
        
        if (e == null) return 0.0;
        
        // Fast-path: direct node reference optimization
        if (e.type == Expr.E_NODE_REF && e.nodeName != null) {
            return evaluateNodeReference(e.nodeName);
        }

        // Full expression evaluation
        ExprState state = column.getExpressionState(row);
        updateExpressionState(state);
        return e.eval(state);
    }
    
    /**
     * Evaluate a direct node reference (optimized path)
     */
    private double evaluateNodeReference(String nodeName) {
        // Check computed values first
        Double computedValue = ComputedValues.getComputedValue(nodeName);
        if (computedValue != null) {
            return computedValue;
        }
        
        // Fall back to labeled node voltage
        return sim != null ? sim.getLabeledNodeVoltage(nodeName) : 0.0;
    }
    
    /**
     * Get available variables for equations
     */
    private String getAvailableVariablesString() {
        String[] availableNodes = LabeledNodeElm.getSortedLabeledNodeNames();
        
        if (availableNodes.length == 0) {
            return "Available: t (time), no labeled nodes in circuit";
        }
        
        StringBuilder sb = new StringBuilder("Available: t (time)");
        for (String nodeName : availableNodes) {
            sb.append(", ").append(nodeName);
        }
        
        return sb.toString();
    }
    
    /**
     * Check if cell coordinates are valid
     */
    private boolean isValidCell(int row, int col) {
        return row >= 0 && row < table.rows && col >= 0 && col < table.columns.size();
    }
}
