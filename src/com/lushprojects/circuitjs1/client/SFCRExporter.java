/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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
 *   @scope      - Docked and undocked scopes with trace references (UID-based)
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
    private ArrayList<SFCSankeyElm> sankeyDiagrams = new ArrayList<SFCSankeyElm>();
    private ArrayList<CircuitElm> otherElements = new ArrayList<CircuitElm>();
    private HashSet<CircuitElm> scopeElmsExportedAsBlocks = new HashSet<CircuitElm>();
    
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
        scopeElmsExportedAsBlocks.clear();
        
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

        // Export Sankey diagrams as @sankey
        for (SFCSankeyElm sankey : sankeyDiagrams) {
            String block = exportSankeyDiagram(sankey);
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
                sb.append("\n");
            }
        }

        // Export scopes after @circuit so trace targets are defined earlier in file
        String scopesBlock = exportScopes();
        if (!scopesBlock.isEmpty()) {
            sb.append(scopesBlock);
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
        sankeyDiagrams.clear();
        otherElements.clear();
        
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            
            if (elm instanceof EquationTableElm) {
                equationTables.add((EquationTableElm) elm);
            } else if (elm instanceof SFCTableElm) {
                sfcTables.add((SFCTableElm) elm);
            } else if (elm instanceof GodlyTableElm) {
                godlyTables.add((GodlyTableElm) elm);
            } else if (elm instanceof SFCSankeyElm) {
                sankeyDiagrams.add((SFCSankeyElm) elm);
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
        
        // Display options - always export current state
        sb.append("  showDots: ").append(sim.dotsCheckItem.getState()).append("\n");
        sb.append("  showVolts: ").append(sim.voltsCheckItem.getState()).append("\n");
        sb.append("  showValues: ").append(sim.showValuesCheckItem.getState()).append("\n");
        sb.append("  showPower: ").append(sim.powerCheckItem.getState()).append("\n");
        sb.append("  autoAdjustTimestep: ").append(sim.adjustTimeStep).append("\n");
        sb.append("  equationTableTolerance: ").append(Double.toString(sim.equationTableConvergenceTolerance)).append("\n");
        sb.append("  infoViewerUpdateIntervalMs: ").append(sim.infoViewerUpdateIntervalMs).append("\n");
        
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
            String sourceName = eqTable.getOutputName(row);
            if (EquationTableElm.isCommentRowName(sourceName)) {
                String comment = sourceName == null ? "" : sourceName.trim();
                if (comment.startsWith("#")) {
                    comment = comment.substring(1).trim();
                }
                if (!comment.isEmpty()) {
                    sb.append("  # ").append(comment).append("\n");
                }
                continue;
            }

            String name = eqTable.getDisplayOutputName(row);
            String expr = eqTable.getEquation(row);
            
            if (name == null || name.isEmpty()) continue;
            if (expr == null) expr = "0";

            EquationTableElm.RowOutputMode mode = eqTable.getOutputMode(row);
            String sliderVar = eqTable.getSliderVarName(row);
            double sliderValue = eqTable.getSliderValue(row);
            String initialEq = eqTable.getInitialEquation(row);

            String hint = HintRegistry.getHint(sourceName);

            sb.append("  ").append(name).append(" ~ ").append(expr);
            sb.append(" ; mode=").append(formatEquationRowMode(mode));
            if (sliderVar != null && !sliderVar.trim().isEmpty()) {
                sb.append(" ; slider=").append(sliderVar.trim());
            }
            sb.append(" ; sliderValue=").append(sliderValue);
            if (initialEq != null && !initialEq.trim().isEmpty()) {
                sb.append(" ; initial=").append(initialEq.trim());
            }
            if (hint != null && !hint.trim().isEmpty()) {
                sb.append("  # ").append(hint);
            }
            sb.append("\n");
        }
        
        sb.append("@end\n");
        return sb.toString();
    }

    private String formatEquationRowMode(EquationTableElm.RowOutputMode mode) {
        if (mode == null) {
            return "voltage";
        }
        switch (mode) {
            case FLOW_MODE: return "flow";
            case PARAM_MODE: return "param";
            default: return "voltage";
        }
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
        sb.append("  type: transaction_flow\n");
        
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

    /** Export SFCSankeyElm as @sankey block. */
    private String exportSankeyDiagram(SFCSankeyElm sankey) {
        StringBuilder sb = new StringBuilder();
        
        String sourceName = sankey.getSourceTableName();
        String layout = sankey.getLayoutMode().name().toLowerCase();
        int width = sankey.getWidth();
        int height = sankey.getHeight();
        
        // Header with position
        sb.append("@sankey");
        sb.append(formatPosition(sankey)).append("\n");
        
        // Properties
        if (sourceName != null && !sourceName.isEmpty()) {
            sb.append("  source: ").append(sourceName).append("\n");
        }
        sb.append("  layout: ").append(layout).append("\n");
        sb.append("  width: ").append(width).append("\n");
        sb.append("  height: ").append(height).append("\n");
        
        // Scale visualization options
        sb.append("  showScaleBar: ").append(sankey.getShowScaleBar()).append("\n");
        if (sankey.getFixedMaxScale() > 0) {
            sb.append("  fixedMaxScale: ").append(sankey.getFixedMaxScale()).append("\n");
        }
        sb.append("  useHighWaterMark: ").append(sankey.getUseHighWaterMark()).append("\n");
        sb.append("  showFlowValues: ").append(sankey.getShowFlowValues()).append("\n");
        
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
            if (elm instanceof ScopeElm) {
                ScopeElm se = (ScopeElm) elm;
                // Skip raw 403 dump when we can represent this scope in @scope block.
                if (scopeElmsExportedAsBlocks.contains(elm) || canExportScopeAsBlock(se.elmScope)) {
                    continue;
                }
            }
            String dump = sim.getElementDumpWithUid(elm);
            if (dump != null && !dump.isEmpty()) {
                sb.append(dump).append("\n");
            }
        }
        
        sb.append("@end\n");
        return sb.toString();
    }

    /** Export docked scopes in @scope blocks using UID-based trace references. */
    private String exportScopes() {
        StringBuilder sb = new StringBuilder();

        // Export docked scopes (no geometry - they live in the scope dock)
        for (int i = 0; i < sim.scopeCount; i++) {
            Scope s = sim.scopes[i];
            if (!appendScopeBlock(sb, s, i + 1, "Scope", null)) {
                continue;
            }
        }

        // Export embedded scope elements (ScopeElm) with geometry
        int embeddedIndex = 1;
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            if (!(elm instanceof ScopeElm)) {
                continue;
            }
            ScopeElm scopeElm = (ScopeElm) elm;
            if (appendScopeBlock(sb, scopeElm.elmScope, embeddedIndex++, "Embedded_Scope", scopeElm)) {
                scopeElmsExportedAsBlocks.add(scopeElm);
            }
        }

        return sb.toString();
    }

    private boolean canExportScopeAsBlock(Scope s) {
        if (s == null || s.plots == null || s.plots.size() == 0) {
            return false;
        }
        for (int p = 0; p < s.plots.size(); p++) {
            ScopePlot sp = s.plots.get(p);
            if (sp != null && sp.elm != null) {
                String uid = sp.elm.getPersistentUid();
                if (uid != null && !uid.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean appendScopeBlock(StringBuilder sb, Scope s, int defaultIndex, String defaultPrefix, ScopeElm scopeElm) {
        if (s == null || s.plots == null || s.plots.size() == 0) {
            return false;
        }

        int validPlots = 0;
        for (int p = 0; p < s.plots.size(); p++) {
            ScopePlot sp = s.plots.get(p);
            if (sp != null && sp.elm != null && sp.elm.getPersistentUid() != null && !sp.elm.getPersistentUid().isEmpty()) {
                validPlots++;
            }
        }
        if (validPlots == 0) {
            return false;
        }

        String scopeName = s.getScopeMenuName();
        if (scopeName == null || scopeName.isEmpty()) {
            scopeName = defaultPrefix + "_" + defaultIndex;
        }

        sb.append("@scope ").append(sanitizeName(scopeName))
          .append(" position=").append(s.position).append("\n");

        // For embedded scopes, include the ScopeElm geometry and UID
        if (scopeElm != null) {
            sb.append("  x1: ").append(scopeElm.x).append("\n");
            sb.append("  y1: ").append(scopeElm.y).append("\n");
            sb.append("  x2: ").append(scopeElm.x2).append("\n");
            sb.append("  y2: ").append(scopeElm.y2).append("\n");
            String elmUid = scopeElm.getPersistentUid();
            if (elmUid != null && !elmUid.isEmpty()) {
                sb.append("  elmUid: ").append(elmUid).append("\n");
            }
        }

        sb.append("  speed: ").append(s.speed).append("\n");
        sb.append("  flags: ").append(Scope.exportAsDecOrHex(s.getFlags(), s.FLAG_PERPLOTFLAGS)).append("\n");

        if (s.getTitle() != null && !s.getTitle().isEmpty()) {
            sb.append("  title: ").append(CustomLogicModel.escape(s.getTitle())).append("\n");
        }
        if (s.getText() != null && !s.getText().isEmpty()) {
            sb.append("  label: ").append(CustomLogicModel.escape(s.getText())).append("\n");
        }

        boolean wroteSource = false;
        for (int p = 0; p < s.plots.size(); p++) {
            ScopePlot sp = s.plots.get(p);
            if (sp == null || sp.elm == null) {
                continue;
            }
            String uid = sp.elm.getPersistentUid();
            if (uid == null || uid.isEmpty()) {
                continue;
            }
            if (!wroteSource) {
                sb.append("  source: uid:").append(uid).append(" value:").append(sp.value).append("\n");
                wroteSource = true;
            } else {
                sb.append("  trace: uid:").append(uid).append(" value:").append(sp.value).append("\n");
            }
        }

        if (!wroteSource) {
            return false;
        }
        sb.append("@end\n");
        return true;
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
