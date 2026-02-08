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
 * Exports circuit in SFCR-compatible text format.
 * 
 * Generates human-readable Stock-Flow Consistent model definitions compatible
 * with the R sfcr package (https://github.com/joaomacalos/sfcr).
 * 
 * Output blocks:
 *   @init       - Simulation settings (timestep, units)
 *   @info       - Model documentation (markdown)
 *   @equations  - All equations (from EquationTableElm, GodlyTableElm)
 *   @matrix     - Transaction matrices (from SFCTableElm)
 *   @hints      - Variable documentation
 *   @circuit    - Non-SFCR elements (passthrough)
 * 
 * @see SFCRParser
 * @see <a href="../dev_docs/SFCR_FORMAT_REFERENCE.md">SFCR Format Reference</a>
 */
public class SFCRExporter {
    
    // =========================================================================
    // Fields
    // =========================================================================
    
    private CirSim sim;
    private ArrayList<EquationTableElm> equationTables = new ArrayList<EquationTableElm>();
    private ArrayList<SFCTableElm> sfcTables = new ArrayList<SFCTableElm>();
    private ArrayList<GodlyTableElm> godlyTables = new ArrayList<GodlyTableElm>();
    private ArrayList<CircuitElm> otherElements = new ArrayList<CircuitElm>();
    
    // =========================================================================
    // Constructor & Public API
    // =========================================================================
    
    /** Create a new SFCR exporter. */
    public SFCRExporter(CirSim sim) {
        this.sim = sim;
    }
    
    /** Export the current circuit in SFCR format. */
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
        
        // Export equation tables as @equations
        for (EquationTableElm eqTable : equationTables) {
            String block = exportEquationTable(eqTable);
            if (!block.isEmpty()) {
                sb.append(block);
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
    
    // =========================================================================
    // Block Exporters
    // =========================================================================
    
    /** Categorize elements for export. */
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
    
    /** Export @info block with model documentation. */
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
    
    /** Export @init block with simulation settings. */
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
    
    /** Export EquationTableElm as @equations block. */
    private String exportEquationTable(EquationTableElm eqTable) {
        StringBuilder sb = new StringBuilder();
        
        String tableName = eqTable.getTableName();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Equations";
        }
        
        int rowCount = eqTable.getRowCount();
        if (rowCount == 0) return "";
        
        sb.append("@equations ").append(sanitizeName(tableName));
        sb.append(formatPosition(eqTable)).append("\n");
        
        for (int row = 0; row < rowCount; row++) {
            String name = eqTable.getOutputName(row);
            String expr = eqTable.getEquation(row);
            
            if (name == null || name.isEmpty()) continue;
            if (expr == null) expr = "0";
            
            String hint = HintRegistry.getHint(name);
            sb.append("  ").append(name).append(" ~ ").append(expr);
            if (hint != null && !hint.trim().isEmpty()) {
                sb.append("  # ").append(hint);
            }
            sb.append("\n");
        }
        
        sb.append("@end\n");
        return sb.toString();
    }

    /** Export GodlyTableElm as @equations block (integration-based stocks). */
    private String exportGodlyTable(GodlyTableElm godlyTable) {
        StringBuilder sb = new StringBuilder();
        
        String tableName = godlyTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Stocks";
        }
        
        sb.append("@equations ").append(sanitizeName(tableName));
        sb.append(formatPosition(godlyTable)).append("\n");
        
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
    
    /** Build an expression representing the sum of flows in a column. */
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
    
    /** Export SFCTableElm as @matrix block. */
    private String exportSFCTable(SFCTableElm sfcTable) {
        StringBuilder sb = new StringBuilder();
        
        String tableName = sfcTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "SFC_Matrix";
        }
        
        sb.append("@matrix ").append(sanitizeName(tableName));
        sb.append(formatPosition(sfcTable)).append("\n");
        
        // Count data columns (exclude computed Σ column)
        int totalCols = sfcTable.getCols();
        int dataCols = 0;
        for (int col = 0; col < totalCols; col++) {
            TableColumn column = sfcTable.getColumn(col);
            if (column != null && !column.isALE()) {
                dataCols++;
            }
        }
        
        // Export as markdown table only (no separate columns: line to avoid duplication)
        sb.append("\n");
        
        // Header row (exclude computed Σ column - it will be auto-added on import)
        sb.append("| Transaction |");
        for (int col = 0; col < totalCols; col++) {
            TableColumn column = sfcTable.getColumn(col);
            if (column != null && !column.isALE()) {
                sb.append(" ").append(column.getStockName()).append(" |");
            }
        }
        sb.append("\n");
        
        // Separator row
        sb.append("|-------------|");
        for (int col = 0; col < dataCols; col++) {
            sb.append("------|");
        }
        sb.append("\n");
        
        // Data rows (exclude computed Σ column)
        int rows = sfcTable.getRows();
        for (int row = 0; row < rows; row++) {
            String rowDesc = sfcTable.getRowDescription(row);
            if (rowDesc == null) rowDesc = "Row" + row;
            
            sb.append("| ").append(rowDesc).append(" |");
            
            for (int col = 0; col < totalCols; col++) {
                TableColumn column = sfcTable.getColumn(col);
                if (column != null && !column.isALE()) {
                    String cellExpr = column.getCellEquation(row);
                    if (cellExpr == null) cellExpr = "";
                    sb.append(" ").append(cellExpr).append(" |");
                }
            }
            sb.append("\n");
        }
        
        sb.append("@end\n");
        return sb.toString();
    }
    
    /** Export hints as @hints block. */
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
    
    /** Export non-SFCR elements in @circuit block. */
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
    
    // =========================================================================
    // Helpers
    // =========================================================================
    
    /** Sanitize name for SFCR format (replace spaces with underscores). */
    private String sanitizeName(String name) {
        if (name == null) return "Unnamed";
        return name.replaceAll("\\s+", "_");
    }
    
    /** Format position string for block header. */
    private String formatPosition(CircuitElm elm) {
        return " x=" + elm.x + " y=" + elm.y;
    }
}
