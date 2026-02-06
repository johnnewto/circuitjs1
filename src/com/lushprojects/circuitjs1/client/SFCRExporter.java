/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * SFCRExporter - Exports circuit in SFCR-compatible text format
 * 
 * Generates human-readable Stock-Flow Consistent model definitions
 * compatible with the R sfcr package (https://github.com/joaomacalos/sfcr).
 * 
 * Supported output blocks:
 * - @init: Simulation settings (timestep, voltageUnit, timeUnit)
 * - @parameters: Constant equations (pure numeric values)
 * - @equations: Variable equations with expressions
 * - @matrix: SFC transaction matrices (from SFCTableElm)
 * - @hints: Variable documentation
 * - @circuit: Non-SFCR elements (passthrough for full circuit reconstruction)
 * 
 * Example output:
 * <pre>
 * @init
 *   timestep: 0.01
 *   voltageUnit: $
 *   timeUnit: yr
 * @end
 * 
 * @parameters Params
 *   r = 0.025              # Interest rate
 *   alpha = 0.75           # Propensity to consume
 * @end
 * 
 * @equations Model
 *   Y ~ C + I              # National income
 *   C ~ alpha * YD         # Consumption
 * @end
 * </pre>
 */
public class SFCRExporter {
    
    private CirSim sim;
    
    // Track elements by type for export
    private ArrayList<EquationTableElm> equationTables = new ArrayList<EquationTableElm>();
    private ArrayList<SFCTableElm> sfcTables = new ArrayList<SFCTableElm>();
    private ArrayList<GodlyTableElm> godlyTables = new ArrayList<GodlyTableElm>();
    private ArrayList<CircuitElm> otherElements = new ArrayList<CircuitElm>();
    
    /**
     * Create a new SFCR exporter
     * @param sim The circuit simulator instance
     */
    public SFCRExporter(CirSim sim) {
        this.sim = sim;
    }
    
    /**
     * Export the current circuit in SFCR format
     * @return SFCR-formatted text
     */
    public String export() {
        StringBuilder sb = new StringBuilder();
        
        // Categorize elements
        categorizeElements();
        
        // Build header comment
        sb.append("# CircuitJS1 SFCR Export\n");
        sb.append("# Generated from circuit simulation\n");
        sb.append("\n");
        
        // Export @info block if available
        String infoBlock = exportInfoBlock();
        if (!infoBlock.isEmpty()) {
            sb.append(infoBlock);
            sb.append("\n");
        }
        
        // Export @init block
        String initBlock = exportInitBlock();
        if (!initBlock.isEmpty()) {
            sb.append(initBlock);
            sb.append("\n");
        }
        
        // Export equation tables as @parameters and @equations
        for (EquationTableElm eqTable : equationTables) {
            String[] blocks = exportEquationTable(eqTable);
            // blocks[0] = parameters, blocks[1] = equations
            if (blocks[0] != null && !blocks[0].isEmpty()) {
                sb.append(blocks[0]);
                sb.append("\n");
            }
            if (blocks[1] != null && !blocks[1].isEmpty()) {
                sb.append(blocks[1]);
                sb.append("\n");
            }
        }
        
        // Export GodlyTableElm as @equations (since they compute values with integration)
        for (GodlyTableElm godlyTable : godlyTables) {
            String block = exportGodlyTable(godlyTable);
            if (!block.isEmpty()) {
                sb.append(block);
                sb.append("\n");
            }
        }
        
        // Export SFC tables as @matrix
        for (SFCTableElm sfcTable : sfcTables) {
            String block = exportSFCTable(sfcTable);
            if (!block.isEmpty()) {
                sb.append(block);
                sb.append("\n");
            }
        }
        
        // Export hints
        String hintsBlock = exportHints();
        if (!hintsBlock.isEmpty()) {
            sb.append(hintsBlock);
            sb.append("\n");
        }
        
        // Export other circuit elements in @circuit block
        if (!otherElements.isEmpty()) {
            String circuitBlock = exportCircuitElements();
            if (!circuitBlock.isEmpty()) {
                sb.append(circuitBlock);
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Categorize elements for export
     */
    private void categorizeElements() {
        equationTables.clear();
        sfcTables.clear();
        godlyTables.clear();
        otherElements.clear();
        
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            
            if (elm instanceof EquationTableElm) {
                equationTables.add((EquationTableElm) elm);
            } else if (elm instanceof SFCTableElm) {
                sfcTables.add((SFCTableElm) elm);
            } else if (elm instanceof GodlyTableElm) {
                godlyTables.add((GodlyTableElm) elm);
            } else {
                otherElements.add(elm);
            }
        }
    }
    
    /**
     * Export @info block with model documentation
     */
    private String exportInfoBlock() {
        if (sim.modelInfoContent == null || sim.modelInfoContent.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("@info\n");
        sb.append(sim.modelInfoContent);
        
        // Ensure content ends with newline before @end
        if (!sim.modelInfoContent.endsWith("\n")) {
            sb.append("\n");
        }
        sb.append("@end\n");
        
        return sb.toString();
    }
    
    /**
     * Export @init block with simulation settings
     */
    private String exportInitBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("@init\n");
        
        // Timestep
        sb.append("  timestep: ").append(sim.maxTimeStep).append("\n");
        
        // Voltage unit (if customized)
        if (sim.voltageUnitSymbol != null && !sim.voltageUnitSymbol.equals("V")) {
            sb.append("  voltageUnit: ").append(sim.voltageUnitSymbol).append("\n");
        }
        
        // Time unit (if customized)
        if (sim.timeUnitSymbol != null && !sim.timeUnitSymbol.isEmpty()) {
            sb.append("  timeUnit: ").append(sim.timeUnitSymbol).append("\n");
        }
        
        sb.append("@end\n");
        return sb.toString();
    }
    
    /**
     * Export an EquationTableElm as @parameters and/or @equations blocks
     * @return String[2] where [0]=parameters block, [1]=equations block
     */
    private String[] exportEquationTable(EquationTableElm eqTable) {
        StringBuilder params = new StringBuilder();
        StringBuilder eqns = new StringBuilder();
        
        String tableName = eqTable.getTableName();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Equations";
        }
        
        // Separate parameters (pure constants) from equations (expressions)
        ArrayList<String> paramNames = new ArrayList<String>();
        ArrayList<String> paramValues = new ArrayList<String>();
        ArrayList<String> eqnNames = new ArrayList<String>();
        ArrayList<String> eqnExprs = new ArrayList<String>();
        
        int rowCount = eqTable.getRowCount();
        for (int row = 0; row < rowCount; row++) {
            String name = eqTable.getOutputName(row);
            String expr = eqTable.getEquation(row);
            
            if (name == null || name.isEmpty()) continue;
            if (expr == null) expr = "0";
            
            // Check if this is a simple constant
            if (isSimpleConstant(expr)) {
                paramNames.add(name);
                paramValues.add(expr);
            } else {
                eqnNames.add(name);
                eqnExprs.add(expr);
            }
        }
        
        // Build parameters block
        if (!paramNames.isEmpty()) {
            params.append("@parameters ").append(sanitizeName(tableName)).append("\n");
            for (int i = 0; i < paramNames.size(); i++) {
                String name = paramNames.get(i);
                String value = paramValues.get(i);
                String hint = HintRegistry.getHint(name);
                
                params.append("  ").append(name).append(" = ").append(value);
                if (hint != null && !hint.trim().isEmpty()) {
                    params.append("  # ").append(hint);
                }
                params.append("\n");
            }
            params.append("@end\n");
        }
        
        // Build equations block
        if (!eqnNames.isEmpty()) {
            eqns.append("@equations ").append(sanitizeName(tableName)).append("\n");
            for (int i = 0; i < eqnNames.size(); i++) {
                String name = eqnNames.get(i);
                String expr = eqnExprs.get(i);
                String hint = HintRegistry.getHint(name);
                
                eqns.append("  ").append(name).append(" ~ ").append(expr);
                if (hint != null && !hint.trim().isEmpty()) {
                    eqns.append("  # ").append(hint);
                }
                eqns.append("\n");
            }
            eqns.append("@end\n");
        }
        
        return new String[] { params.toString(), eqns.toString() };
    }
    
    /**
     * Export a GodlyTableElm as @equations block
     * GodlyTables use integration, so we export their stock columns
     */
    private String exportGodlyTable(GodlyTableElm godlyTable) {
        StringBuilder sb = new StringBuilder();
        
        String tableName = godlyTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Stocks";
        }
        
        sb.append("@equations ").append(sanitizeName(tableName)).append("\n");
        
        // Export column stock names with their integration expressions
        int cols = godlyTable.getCols();
        for (int col = 0; col < cols; col++) {
            TableColumn column = godlyTable.getColumn(col);
            if (column == null || column.isALE()) continue;
            
            String stockName = column.getStockName();
            if (stockName == null || stockName.isEmpty()) continue;
            
            // Build the flow sum expression
            String flowExpr = buildColumnFlowExpression(godlyTable, col);
            String hint = HintRegistry.getHint(stockName);
            
            // GodlyTable uses integration: stock = integrate(sum of flows)
            double initialValue = godlyTable.getInitialValue(col);
            
            sb.append("  # Initial value: ").append(initialValue).append("\n");
            sb.append("  ").append(stockName).append(" ~ ");
            sb.append(stockName).append("_init + integrate(").append(flowExpr).append(")");
            if (hint != null && !hint.trim().isEmpty()) {
                sb.append("  # ").append(hint);
            }
            sb.append("\n");
        }
        
        sb.append("@end\n");
        return sb.toString();
    }
    
    /**
     * Build an expression representing the sum of flows in a column
     */
    private String buildColumnFlowExpression(GodlyTableElm table, int col) {
        StringBuilder sb = new StringBuilder();
        
        int rows = table.getRows();
        boolean first = true;
        
        for (int row = 0; row < rows; row++) {
            TableColumn column = table.getColumn(col);
            if (column == null) continue;
            
            String cellExpr = column.getCellEquation(row);
            if (cellExpr == null || cellExpr.trim().isEmpty() || cellExpr.equals("0")) {
                continue;
            }
            
            if (!first) {
                sb.append(" + ");
            }
            
            // Wrap in parentheses if it contains operators
            if (cellExpr.contains("+") || cellExpr.contains("-")) {
                sb.append("(").append(cellExpr).append(")");
            } else {
                sb.append(cellExpr);
            }
            first = false;
        }
        
        if (sb.length() == 0) {
            return "0";
        }
        
        return sb.toString();
    }
    
    /**
     * Export an SFCTableElm as @matrix block
     */
    private String exportSFCTable(SFCTableElm sfcTable) {
        StringBuilder sb = new StringBuilder();
        
        String tableName = sfcTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "SFC_Matrix";
        }
        
        sb.append("@matrix ").append(sanitizeName(tableName)).append("\n");
        
        // Export columns line
        sb.append("columns: ");
        int cols = sfcTable.getCols();
        for (int col = 0; col < cols; col++) {
            TableColumn column = sfcTable.getColumn(col);
            if (column == null) continue;
            if (col > 0) sb.append(", ");
            sb.append(column.getStockName());
        }
        sb.append("\n");
        
        // Export type
        sb.append("type: transaction_flow\n");
        
        // Export as markdown table
        sb.append("\n");
        
        // Header row
        sb.append("| Transaction |");
        for (int col = 0; col < cols; col++) {
            TableColumn column = sfcTable.getColumn(col);
            if (column != null) {
                sb.append(" ").append(column.getStockName()).append(" |");
            }
        }
        sb.append("\n");
        
        // Separator row
        sb.append("|-------------|");
        for (int col = 0; col < cols; col++) {
            sb.append("------|");
        }
        sb.append("\n");
        
        // Data rows
        int rows = sfcTable.getRows();
        for (int row = 0; row < rows; row++) {
            String rowDesc = sfcTable.getRowDescription(row);
            if (rowDesc == null) rowDesc = "Row" + row;
            
            sb.append("| ").append(rowDesc).append(" |");
            
            for (int col = 0; col < cols; col++) {
                TableColumn column = sfcTable.getColumn(col);
                String cellExpr = (column != null) ? column.getCellEquation(row) : "0";
                if (cellExpr == null) cellExpr = "0";
                sb.append(" ").append(cellExpr).append(" |");
            }
            sb.append("\n");
        }
        
        sb.append("@end\n");
        return sb.toString();
    }
    
    /**
     * Export hints as @hints block
     */
    private String exportHints() {
        Set<String> names = HintRegistry.getAllNames();
        if (names.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("@hints\n");
        
        for (String name : names) {
            String hint = HintRegistry.getHint(name);
            if (hint != null && !hint.trim().isEmpty()) {
                sb.append("  ").append(name).append(": ").append(hint).append("\n");
            }
        }
        
        sb.append("@end\n");
        return sb.toString();
    }
    
    /**
     * Export non-SFCR elements in @circuit block
     */
    private String exportCircuitElements() {
        if (otherElements.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("@circuit\n");
        
        for (CircuitElm elm : otherElements) {
            String dump = elm.dump();
            if (dump != null && !dump.isEmpty()) {
                sb.append(dump).append("\n");
            }
        }
        
        sb.append("@end\n");
        return sb.toString();
    }
    
    /**
     * Check if an expression is a simple constant (numeric value)
     */
    private boolean isSimpleConstant(String expr) {
        if (expr == null || expr.isEmpty()) return false;
        
        String trimmed = expr.trim();
        
        // Try to parse as a number
        try {
            Double.parseDouble(trimmed);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Sanitize a name for use in SFCR format (replace spaces with underscores)
     */
    private String sanitizeName(String name) {
        if (name == null) return "Unnamed";
        return name.replaceAll("\\s+", "_");
    }
}
