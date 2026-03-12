/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashSet;
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
 *   @action     - Action Time schedule (timed target updates)
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

    public enum ExportSyntax {
        BLOCK_FORMAT,
        R_STYLE
    }
    
    // =========================================================================
    // Fields
    // =========================================================================
    
    private CirSim sim;
    private ExportSyntax exportSyntax;
    private ArrayList<EquationTableElm> equationTables = new ArrayList<EquationTableElm>();
    private ArrayList<SFCTableElm> sfcTables = new ArrayList<SFCTableElm>();
    private ArrayList<GodlyTableElm> godlyTables = new ArrayList<GodlyTableElm>();
    private ArrayList<SFCSankeyElm> sankeyDiagrams = new ArrayList<SFCSankeyElm>();
    private ArrayList<CircuitElm> otherElements = new ArrayList<CircuitElm>();
    private ActionTimeElm actionTimeElmForExport = null;
    private HashSet<CircuitElm> scopeElmsExportedAsBlocks = new HashSet<CircuitElm>();
    
    // =========================================================================
    // Constructor & Public API
    // =========================================================================
    
    /** Create a new SFCR exporter. */
    public SFCRExporter(CirSim sim) {
        this(sim, ExportSyntax.BLOCK_FORMAT);
    }

    /** Create a new SFCR exporter with explicit syntax style. */
    public SFCRExporter(CirSim sim, ExportSyntax syntax) {
        this.sim = sim;
        this.exportSyntax = (syntax == null) ? ExportSyntax.BLOCK_FORMAT : syntax;
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

        // Export Action Time schedule as @action block
        String actionBlock = exportActionBlock();
        if (!actionBlock.isEmpty()) {
            sb.append(actionBlock);
            sb.append("\n");
        }
        
        // Export equation tables
        for (EquationTableElm eqTable : equationTables) {
            String block = (exportSyntax == ExportSyntax.R_STYLE)
                ? exportEquationTableRStyle(eqTable)
                : exportEquationTable(eqTable);
            if (!block.isEmpty()) {
                sb.append(block);
                sb.append("\n");
            }
        }
        
        // Export GodlyTableElm equations (integration-based stocks)
        for (GodlyTableElm godlyTable : godlyTables) {
            String block = (exportSyntax == ExportSyntax.R_STYLE)
                ? exportGodlyTableRStyle(godlyTable)
                : exportGodlyTable(godlyTable);
            if (!block.isEmpty()) {
                sb.append(block);
                sb.append("\n");
            }
        }
        
        // Export SFC tables
        for (SFCTableElm sfcTable : sfcTables) {
            String block = (exportSyntax == ExportSyntax.R_STYLE)
                ? exportSFCTableRStyle(sfcTable)
                : exportSFCTable(sfcTable);
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
        actionTimeElmForExport = null;
        
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
            } else if (elm instanceof ActionTimeElm) {
                if (actionTimeElmForExport == null) {
                    actionTimeElmForExport = (ActionTimeElm) elm;
                }
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
        sb.append("  equationTableMnaMode: ").append(sim.equationTableMnaMode).append("\n");
        sb.append("  equationTableNewtonJacobianEnabled: ").append(sim.equationTableNewtonJacobianEnabled).append("\n");
        sb.append("  equationTableTolerance: ").append(Double.toString(sim.equationTableConvergenceTolerance)).append("\n");
        sb.append("  convergenceCheckThreshold: ").append(sim.convergenceCheckThreshold).append("\n");
        sb.append("  infoViewerUpdateIntervalMs: ").append(sim.infoViewerUpdateIntervalMs).append("\n");
        
        sb.append("@end\n");
        return sb.toString();
    }

    /** Export ActionScheduler as @action block. */
    private String exportActionBlock() {
        ActionScheduler scheduler = ActionScheduler.getInstance(sim);
        if (scheduler == null && actionTimeElmForExport == null) {
            return "";
        }

        java.util.List<ActionScheduler.ScheduledAction> actions =
            (scheduler == null) ? null : scheduler.getAllActions();
        boolean hasActions = actions != null && !actions.isEmpty();
        if (!hasActions && actionTimeElmForExport == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (actionTimeElmForExport != null) {
            String actionName = sanitizeName(actionTimeElmForExport.title);
            sb.append("@action ").append(actionName).append(formatPosition(actionTimeElmForExport)).append("\n");
        } else {
            sb.append("@action\n");
        }

        double pauseTime = (scheduler == null) ? 0 : scheduler.getPauseTime();
        sb.append("  pauseTime: ").append(pauseTime).append("\n");

        if (actionTimeElmForExport != null) {
            sb.append("  enabled: ").append(actionTimeElmForExport.enabled).append("\n");
            sb.append("  element: ")
              .append(actionTimeElmForExport.x).append(" ")
              .append(actionTimeElmForExport.y).append(" ")
              .append(actionTimeElmForExport.x2).append(" ")
              .append(actionTimeElmForExport.y2).append(" ")
              .append(actionTimeElmForExport.flags)
              .append("\n");
        }

        if (hasActions) {
        sb.append("\n");
        sb.append("| time | target | value | text | enabled | stop |\n");
        sb.append("|------|--------|-------|------|---------|------|\n");

        for (ActionScheduler.ScheduledAction action : actions) {
            String target = (action.sliderName == null) ? "" : action.sliderName;
            String valueExpr = (action.valueExpression == null) ? "" : action.valueExpression.trim();
            String value = valueExpr.isEmpty() ? Double.toString(action.sliderValue) : valueExpr;
            String text = (action.postText == null) ? "" : action.postText;

            sb.append("| ")
              .append(action.actionTime)
              .append(" | ")
              .append(escapeTableCell(target))
              .append(" | ")
              .append(escapeTableCell(value))
              .append(" | ")
              .append(escapeTableCell(text))
              .append(" | ")
              .append(action.enabled)
              .append(" | ")
              .append(action.stopSimulation)
              .append(" |\n");
        }
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

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_EQUATIONS, sanitizeName(tableName));
        
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
        return SFCRUtil.formatEquationRowMode(mode);
    }

    /** Export EquationTableElm as R-style sfcr_set() assignment. */
    private String exportEquationTableRStyle(EquationTableElm eqTable) {
        StringBuilder sb = new StringBuilder();

        String tableName = eqTable.getTableName();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Equations";
        }

        int rowCount = eqTable.getRowCount();
        if (rowCount == 0) {
            return "";
        }

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_EQUATIONS, sanitizeName(tableName));

        int equationCount = 0;
        for (int row = 0; row < rowCount; row++) {
            String sourceName = eqTable.getOutputName(row);
            if (EquationTableElm.isCommentRowName(sourceName)) {
                continue;
            }
            String name = eqTable.getDisplayOutputName(row);
            if (name != null && !name.isEmpty()) {
                equationCount++;
            }
        }

        if (equationCount == 0) {
            return "";
        }

        String assignmentName = toRAssignmentName(tableName);
        sb.append(assignmentName).append(" <- sfcr_set(\n");

        String metadataComment = formatRBlockMetadataComment(eqTable, null).trim();
        if (!metadataComment.isEmpty()) {
            sb.append("  ").append(metadataComment).append("\n");
        }

        int emitted = 0;
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
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (expr == null) {
                expr = "0";
            }

            EquationTableElm.RowOutputMode mode = eqTable.getOutputMode(row);
            String sliderVar = eqTable.getSliderVarName(row);
            double sliderValue = eqTable.getSliderValue(row);
            String initialEq = eqTable.getInitialEquation(row);

            emitted++;
            sb.append("  e").append(emitted).append(" = ").append(name).append(" ~ ").append(expr);
            if (emitted < equationCount) {
                sb.append(",");
            }

            String hint = HintRegistry.getHint(sourceName);
            // Persist row-editable properties inside bracket metadata so R-style
            // export/import keeps mode/slider settings without changing core syntax.
            String rowMeta = formatRStyleEquationInlineMetadata(mode, sliderVar, sliderValue, initialEq);
            if ((hint != null && !hint.trim().isEmpty()) || !rowMeta.isEmpty()) {
                sb.append("  #");
                if (hint != null && !hint.trim().isEmpty()) {
                    sb.append(" ").append(hint.trim());
                }
                if (!rowMeta.isEmpty()) {
                    if (hint != null && !hint.trim().isEmpty()) {
                        sb.append("  ");
                    } else {
                        sb.append(" ");
                    }
                    sb.append(rowMeta);
                }
            }
            sb.append("\n");
        }
        sb.append(")\n");
        return sb.toString();
    }

    /** Export GodlyTableElm as R-style sfcr_set() assignment. */
    private String exportGodlyTableRStyle(GodlyTableElm godlyTable) {
        StringBuilder sb = new StringBuilder();

        String tableName = godlyTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Stocks";
        }

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_EQUATIONS, sanitizeName(tableName));
        sb.append(formatRBlockMetadataComment(godlyTable, null));

        ArrayList<String> equationLines = new ArrayList<String>();
        int cols = godlyTable.getCols();
        for (int col = 0; col < cols; col++) {
            TableColumn column = godlyTable.getColumn(col);
            if (column == null || column.isALE()) {
                continue;
            }

            String stockName = column.getStockName();
            if (stockName == null || stockName.isEmpty()) {
                continue;
            }

            String flowExpr = buildColumnFlowExpression(godlyTable, col);
            equationLines.add("  e" + (equationLines.size() + 1) + " = "
                + stockName + " ~ " + stockName + "_init + integrate(" + flowExpr + ")");
        }

        if (equationLines.isEmpty()) {
            return "";
        }

        String assignmentName = toRAssignmentName(tableName);
        sb.append(assignmentName).append(" <- sfcr_set(\n");
        for (int i = 0; i < equationLines.size(); i++) {
            sb.append(equationLines.get(i));
            if (i < equationLines.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(")\n");
        return sb.toString();
    }

    /** Export SFCTableElm as R-style sfcr_matrix() assignment. */
    private String exportSFCTableRStyle(SFCTableElm sfcTable) {
        StringBuilder sb = new StringBuilder();

        String tableName = sfcTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "SFC_Matrix";
        }

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_MATRIX, sanitizeName(tableName));

        ArrayList<String> stockNames = new ArrayList<String>();
        ArrayList<String> stockCodes = new ArrayList<String>();
        Set<String> usedCodes = new HashSet<String>();

        int totalCols = sfcTable.getCols();
        for (int col = 0; col < totalCols; col++) {
            TableColumn column = sfcTable.getColumn(col);
            if (column == null || column.isALE()) {
                continue;
            }
            String stockName = column.getStockName();
            if (stockName == null || stockName.trim().isEmpty()) {
                stockName = "Column" + (stockNames.size() + 1);
            }
            stockNames.add(stockName);
            stockCodes.add(makeUniqueRCode(toRCodeIdentifier(stockName), usedCodes));
        }

        if (stockNames.isEmpty()) {
            return "";
        }

        String assignmentName = toRAssignmentName(tableName);
        sb.append(assignmentName).append(" <- sfcr_matrix(\n");
        String metadataComment = formatRBlockMetadataComment(sfcTable, "transaction_flow").trim();
        if (!metadataComment.isEmpty()) {
            sb.append("  ").append(metadataComment).append("\n");
        }
        sb.append("  columns = c(").append(joinRQuoted(stockNames)).append("),\n");
        sb.append("  codes = c(").append(joinRQuoted(stockCodes)).append("),\n");

        int rows = sfcTable.getRows();
        for (int row = 0; row < rows; row++) {
            String rowDesc = sfcTable.getRowDescription(row);
            if (rowDesc == null || rowDesc.trim().isEmpty()) {
                rowDesc = "Row" + row;
            }

            sb.append("  c(\"").append(escapeRString(rowDesc)).append("\"");
            int dataColIndex = 0;
            for (int col = 0; col < totalCols; col++) {
                TableColumn column = sfcTable.getColumn(col);
                if (column == null || column.isALE()) {
                    continue;
                }
                String cellExpr = column.getCellEquation(row);
                if (cellExpr == null) {
                    cellExpr = "";
                }
                sb.append(", ")
                  .append(stockCodes.get(dataColIndex))
                  .append(" = \"")
                  .append(escapeRString(cellExpr))
                  .append("\"");
                dataColIndex++;
            }
            sb.append(")");
            if (row < rows - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append(")\n");
        return sb.toString();
    }

    /** Export GodlyTableElm as @equations block (integration-based stocks). */
    private String exportGodlyTable(GodlyTableElm godlyTable) {
        StringBuilder sb = new StringBuilder();
        
        String tableName = godlyTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Stocks";
        }

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_EQUATIONS, sanitizeName(tableName));
        
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

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_MATRIX, sanitizeName(tableName));
        
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

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_SANKEY, "");
        
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

        Set<String> namesCoveredByEquationBlocks = collectNamesCoveredByEquationBlocks();
        
        StringBuilder sb = new StringBuilder();
        sb.append("@hints\n");
        int exportedCount = 0;
        
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            if (namesCoveredByEquationBlocks.contains(name.trim())) {
                continue;
            }
            String hint = HintRegistry.getHint(name);
            if (hint != null && !hint.trim().isEmpty()) {
                sb.append("  ").append(name.trim()).append(": ").append(hint).append("\n");
                exportedCount++;
            }
        }

        if (exportedCount == 0) {
            return "";
        }
        
        sb.append("@end\n");
        return sb.toString();
    }

    /** Collect names whose hints are already emitted inline in @equations blocks. */
    private Set<String> collectNamesCoveredByEquationBlocks() {
        Set<String> covered = new HashSet<String>();

        for (EquationTableElm eqTable : equationTables) {
            if (eqTable == null) {
                continue;
            }
            int rowCount = eqTable.getRowCount();
            for (int row = 0; row < rowCount; row++) {
                String sourceName = eqTable.getOutputName(row);
                if (sourceName == null) {
                    continue;
                }
                String trimmed = sourceName.trim();
                if (trimmed.isEmpty() || EquationTableElm.isCommentRowName(trimmed)) {
                    continue;
                }
                covered.add(trimmed);
            }
        }

        for (GodlyTableElm godlyTable : godlyTables) {
            if (godlyTable == null) {
                continue;
            }
            int cols = godlyTable.getCols();
            for (int col = 0; col < cols; col++) {
                TableColumn column = godlyTable.getColumn(col);
                if (column == null || column.isALE()) {
                    continue;
                }
                String stockName = column.getStockName();
                if (stockName == null) {
                    continue;
                }
                String trimmed = stockName.trim();
                if (!trimmed.isEmpty()) {
                    covered.add(trimmed);
                }
            }
        }

        return covered;
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

                appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_SCOPE, sanitizeName(scopeName));

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
        return SFCRUtil.sanitizeName(name);
    }

    private String toRAssignmentName(String name) {
        String base = sanitizeName(name);
        return toRCodeIdentifier(base);
    }

    private String toRCodeIdentifier(String text) {
        if (text == null || text.length() == 0) {
            return "x";
        }
        String cleaned = text.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.length() == 0) {
            cleaned = "x";
        }
        char first = cleaned.charAt(0);
        if (!(Character.isLetter(first) || first == '_')) {
            cleaned = "x_" + cleaned;
        }
        return cleaned;
    }

    private String makeUniqueRCode(String base, Set<String> usedCodes) {
        String safeBase = (base == null || base.length() == 0) ? "x" : base;
        String candidate = safeBase;
        int suffix = 1;
        while (usedCodes.contains(candidate)) {
            candidate = safeBase + "_" + suffix;
            suffix++;
        }
        usedCodes.add(candidate);
        return candidate;
    }

    private String joinRQuoted(ArrayList<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(escapeRString(values.get(i))).append("\"");
        }
        return sb.toString();
    }

    private String escapeRString(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String formatRBlockMetadataComment(CircuitElm elm, String type) {
        if (elm == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# [ x=").append(elm.x).append(" y=").append(elm.y);
        if (type != null && type.trim().length() > 0) {
            sb.append(" type: ").append(type.trim());
        }
        sb.append(" ]\n");
        return sb.toString();
    }

    private String formatRStyleEquationInlineMetadata(EquationTableElm.RowOutputMode mode,
            String sliderVar, double sliderValue, String initialEq) {
        // Format: [mode=param, slider=foo, sliderValue=0, initial=... ]
        StringBuilder sb = new StringBuilder();
        sb.append("[mode=").append(formatEquationRowMode(mode));
        if (sliderVar != null && !sliderVar.trim().isEmpty()) {
            sb.append(", slider=").append(sliderVar.trim());
        }
        sb.append(", sliderValue=").append(sliderValue);
        if (initialEq != null && !initialEq.trim().isEmpty()) {
            sb.append(", initial=").append(initialEq.trim());
        }
        sb.append(" ]");
        return sb.toString();
    }

    /** Escape markdown table cell delimiters. */
    private String escapeTableCell(String text) {
        return SFCRUtil.escapeTableCell(text);
    }
    
    /** Format position string for block header. */
    private String formatPosition(CircuitElm elm) {
        return " x=" + elm.x + " y=" + elm.y;
    }

    private void appendLeadingBlockComments(StringBuilder sb, String blockType, String blockName) {
        if (sim == null || sb == null) {
            return;
        }
        String key = SFCRBlockCommentRegistry.makeKey(blockType, blockName);
        Vector<String> comments = sim.getSFCRDocumentState().getBlockComments(key);
        if (comments == null || comments.size() == 0) {
            return;
        }
        for (int i = 0; i < comments.size(); i++) {
            String line = comments.get(i);
            if (line == null) {
                continue;
            }
            String out = line.trim();
            if (out.isEmpty()) {
                continue;
            }
            if (!out.startsWith("#")) {
                out = "# " + out;
            }
            sb.append(out).append("\n");
        }
        sb.append("\n");
    }
}
