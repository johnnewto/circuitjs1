package com.lushprojects.circuitjs1.client.io.sfcr;

import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.CustomLogicModel;
import com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.GodlyTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.SFCSankeyElm;
import com.lushprojects.circuitjs1.client.elements.economics.SFCTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.TableElm;
import com.lushprojects.circuitjs1.client.elements.economics.TableColumn;
import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramElm;
import com.lushprojects.circuitjs1.client.elements.misc.ActionTimeElm;
import com.lushprojects.circuitjs1.client.elements.misc.ScopeElm;
import com.lushprojects.circuitjs1.client.io.LookupDefinition;
import com.lushprojects.circuitjs1.client.io.LookupTableRegistry;
import com.lushprojects.circuitjs1.client.io.SFCRBlockCommentRegistry;
import com.lushprojects.circuitjs1.client.io.SFCRExporter;
import com.lushprojects.circuitjs1.client.io.SFCRUtil;
import com.lushprojects.circuitjs1.client.registry.HintRegistry;
import com.lushprojects.circuitjs1.client.scope.Scope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Context object for SFCR export operations.
 *
 * Holds all mutable state during export and provides access to categorized
 * circuit elements. Handlers use this context instead of calling back to
 * the exporter.
 */
public class SFCRExportContext {

    // =========================================================================
    // Core References
    // =========================================================================

    private final CirSim sim;
    private final SFCRExporter.ExportSyntax exportSyntax;

    // =========================================================================
    // Categorized Elements (populated during categorization)
    // =========================================================================

    private ArrayList<EquationTableElm> equationTables = new ArrayList<EquationTableElm>();
    private ArrayList<SFCTableElm> sfcTables = new ArrayList<SFCTableElm>();
    private ArrayList<GodlyTableElm> godlyTables = new ArrayList<GodlyTableElm>();
    private ArrayList<SFCSankeyElm> sankeyDiagrams = new ArrayList<SFCSankeyElm>();
    private ArrayList<SequenceDiagramElm> sequenceDiagrams = new ArrayList<SequenceDiagramElm>();
    private ArrayList<CircuitElm> otherElements = new ArrayList<CircuitElm>();
    private ActionTimeElm actionTimeElm = null;

    // =========================================================================
    // Export State (mutable during export)
    // =========================================================================

    private HashSet<CircuitElm> scopeElmsExportedAsBlocks = new HashSet<CircuitElm>();
    private final LookupExportManager lookupManager = new LookupExportManager();
    private List<String> equationBlocks = new ArrayList<String>();

    // =========================================================================
    // Constructor
    // =========================================================================

    public SFCRExportContext(CirSim sim, SFCRExporter.ExportSyntax syntax) {
        this.sim = sim;
        this.exportSyntax = syntax;
    }


    // =========================================================================
    // Core Access
    // =========================================================================

    public CirSim getSim() {
        return sim;
    }

    public SFCRExporter.ExportSyntax getExportSyntax() {
        return exportSyntax;
    }

    // =========================================================================
    // Categorized Element Access
    // =========================================================================

    public ArrayList<EquationTableElm> getEquationTables() {
        return equationTables;
    }

    public void setEquationTables(ArrayList<EquationTableElm> tables) {
        this.equationTables = (tables != null) ? tables : new ArrayList<EquationTableElm>();
    }

    public ArrayList<GodlyTableElm> getGodlyTables() {
        return godlyTables;
    }

    public void setGodlyTables(ArrayList<GodlyTableElm> tables) {
        this.godlyTables = (tables != null) ? tables : new ArrayList<GodlyTableElm>();
    }

    public ArrayList<SFCTableElm> getSfcTables() {
        return sfcTables;
    }

    public void setSfcTables(ArrayList<SFCTableElm> tables) {
        this.sfcTables = (tables != null) ? tables : new ArrayList<SFCTableElm>();
    }

    public ArrayList<SFCSankeyElm> getSankeyDiagrams() {
        return sankeyDiagrams;
    }

    public void setSankeyDiagrams(ArrayList<SFCSankeyElm> diagrams) {
        this.sankeyDiagrams = (diagrams != null) ? diagrams : new ArrayList<SFCSankeyElm>();
    }

    public ArrayList<CircuitElm> getOtherElements() {
        return otherElements;
    }

    public ArrayList<SequenceDiagramElm> getSequenceDiagrams() {
        return sequenceDiagrams;
    }

    public void setSequenceDiagrams(ArrayList<SequenceDiagramElm> diagrams) {
        this.sequenceDiagrams = (diagrams != null) ? diagrams : new ArrayList<SequenceDiagramElm>();
    }

    public void setOtherElements(ArrayList<CircuitElm> elements) {
        this.otherElements = (elements != null) ? elements : new ArrayList<CircuitElm>();
    }

    public ActionTimeElm getActionTimeElm() {
        return actionTimeElm;
    }

    public void setActionTimeElm(ActionTimeElm elm) {
        this.actionTimeElm = elm;
    }

    // =========================================================================
    // Scope Tracking
    // =========================================================================

    public int getScopeCount() {
        return (sim != null) ? sim.scopeCount : 0;
    }

    public Scope getScopeAt(int index) {
        return (sim != null && sim.scopes != null && index >= 0 && index < sim.scopeCount)
            ? sim.scopes[index] : null;
    }

    public int getElmListSize() {
        return (sim != null && sim.elmList != null) ? sim.elmList.size() : 0;
    }

    public CircuitElm getElmAt(int index) {
        return (sim != null && sim.elmList != null && index >= 0 && index < sim.elmList.size())
            ? sim.elmList.get(index) : null;
    }

    public void markScopeElmExportedAsBlock(ScopeElm scopeElm) {
        if (scopeElm != null) {
            scopeElmsExportedAsBlocks.add(scopeElm);
        }
    }

    public boolean isScopeElmExportedAsBlock(ScopeElm scopeElm) {
        return scopeElm != null && scopeElmsExportedAsBlocks.contains(scopeElm);
    }

    public void clearScopeElmsExportedAsBlocks() {
        scopeElmsExportedAsBlocks.clear();
    }

    // =========================================================================
    // Lookup Export State
    // =========================================================================

    public ArrayList<LookupDefinition> getLookupExportSpecs() {
        return lookupManager.getSpecs();
    }

    public void setLookupExportSpecs(ArrayList<LookupDefinition> specs) {
        lookupManager.setSpecs(specs);
    }

    public HashMap<String, LookupDefinition> getLookupExportBySignature() {
        return lookupManager.getBySignature();
    }

    public void setLookupExportBySignature(HashMap<String, LookupDefinition> map) {
        lookupManager.setBySignature(map);
    }

    public HashMap<String, ArrayList<String>> getLookupCommentsByNameScope() {
        return lookupManager.getCommentsByNameScope();
    }

    public void setLookupCommentsByNameScope(HashMap<String, ArrayList<String>> map) {
        lookupManager.setCommentsByNameScope(map);
    }

    public void resetLookupExportState() {
        lookupManager.reset();
    }

    // =========================================================================
    // Equation Blocks (intermediate state between collect and emit)
    // =========================================================================

    public List<String> getEquationBlocks() {
        return equationBlocks;
    }

    public void setEquationBlocks(List<String> equationBlocks) {
        this.equationBlocks = (equationBlocks == null) ? new ArrayList<String>() : equationBlocks;
    }

    // =========================================================================
    // Export Helpers (stateless utilities)
    // =========================================================================

    public String formatPosition(CircuitElm elm) {
        return SFCRUtil.formatPosition(elm);
    }

    public String sanitizeName(String text) {
        return SFCRUtil.sanitizeName(text);
    }

    public String escapeTableCell(String text) {
        return SFCRUtil.escapeTableCell(text);
    }

    public void appendLeadingBlockComments(StringBuilder sb, String blockType, String blockName) {
        SFCRUtil.appendLeadingBlockComments(sim, sb, blockType, blockName);
    }

    public void appendExportBlock(StringBuilder sb, String block) {
        if (block == null || block.isEmpty()) {
            return;
        }
        System.out.println("DEBUG appendExportBlock: block contains ```=" + block.contains("```") + ", block length=" + block.length());
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append("\n");
        }
        sb.append(block);
        if (!block.endsWith("\n")) {
            sb.append("\n");
        }
        System.out.println("DEBUG appendExportBlock: sb now contains " + countOccurrences(sb.toString(), "```") + " occurrences of ```");
    }
    
    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    // =========================================================================
    // Scope Block Export
    // =========================================================================

    public boolean appendScopeBlock(StringBuilder sb, Scope s, int defaultIndex, String defaultPrefix, ScopeElm scopeElm) {
        return appendScopeBlockImpl(sb, s, defaultIndex, defaultPrefix, scopeElm);
    }

    private boolean appendScopeBlockImpl(StringBuilder sb, Scope s, int defaultIndex, String defaultPrefix, ScopeElm scopeElm) {
        if (s == null || s.getPlotCount() == 0) {
            return false;
        }

        int validPlots = 0;
        for (int p = 0; p < s.getPlotCount(); p++) {
            CircuitElm elm = s.getPlotElement(p);
            if (elm != null && elm.getPersistentUid() != null && !elm.getPersistentUid().isEmpty()) {
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

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_SCOPE, SFCRUtil.sanitizeName(scopeName));

        sb.append("@scope ").append(SFCRUtil.sanitizeName(scopeName))
          .append(" position=").append(s.getPositionForEmbedded()).append("\n");

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

        sb.append("  speed: ").append(s.getSpeedForEmbedded()).append("\n");
        sb.append("  flags: ").append(Scope.exportAsDecOrHex(s.getFlags(), s.FLAG_PERPLOTFLAGS)).append("\n");

        if (s.getTitle() != null && !s.getTitle().isEmpty()) {
            sb.append("  title: ").append(CustomLogicModel.escape(s.getTitle())).append("\n");
        }
        if (s.getText() != null && !s.getText().isEmpty()) {
            sb.append("  label: ").append(CustomLogicModel.escape(s.getText())).append("\n");
        }

        boolean wroteSource = false;
        for (int p = 0; p < s.getPlotCount(); p++) {
            CircuitElm elm = s.getPlotElement(p);
            if (elm == null) {
                continue;
            }
            String uid = elm.getPersistentUid();
            if (uid == null || uid.isEmpty()) {
                continue;
            }
            if (!wroteSource) {
                sb.append("  source: uid:").append(uid).append(" value:").append(s.getPlotValue(p)).append("\n");
                wroteSource = true;
            } else {
                sb.append("  trace: uid:").append(uid).append(" value:").append(s.getPlotValue(p)).append("\n");
            }
        }

        if (!wroteSource) {
            return false;
        }
        sb.append("@end\n");
        return true;
    }

    private static boolean canExportScopeAsBlock(Scope s) {
        if (s == null || s.getPlotCount() == 0) {
            return false;
        }
        for (int p = 0; p < s.getPlotCount(); p++) {
            CircuitElm elm = s.getPlotElement(p);
            if (elm != null) {
                String uid = elm.getPersistentUid();
                if (uid != null && !uid.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    // =========================================================================
    // Circuit Elements Export
    // =========================================================================

    public String exportCircuitElements() {
        if (otherElements.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("@circuit\n");

        for (CircuitElm elm : otherElements) {
            if (elm instanceof ScopeElm) {
                ScopeElm se = (ScopeElm) elm;
                if ((scopeElmsExportedAsBlocks != null && scopeElmsExportedAsBlocks.contains(elm))
                    || canExportScopeAsBlock(se.elmScope)) {
                    continue;
                }
            }
            String dump = sim.getImportExportHelper().getElementDumpWithUid(elm);
            if (dump != null && !dump.isEmpty()) {
                sb.append(dump).append("\n");
            }
        }

        sb.append("@end\n");
        return sb.toString();
    }

    // =========================================================================
    // Element Export (public dispatchers - dispatch by syntax)
    // =========================================================================

    public String exportEquationTable(EquationTableElm eqTable) {
        return (exportSyntax == SFCRExporter.ExportSyntax.R_STYLE)
            ? exportEquationTableRStyle(eqTable)
            : exportEquationTableBlock(eqTable);
    }

    public String exportGodlyTable(GodlyTableElm godlyTable) {
        return (exportSyntax == SFCRExporter.ExportSyntax.R_STYLE)
            ? exportGodlyTableRStyle(godlyTable)
            : exportGodlyTableBlock(godlyTable);
    }

    public String exportMatrixTable(SFCTableElm sfcTable) {
        return (exportSyntax == SFCRExporter.ExportSyntax.R_STYLE)
            ? exportSFCTableRStyle(sfcTable)
            : exportSFCTableBlock(sfcTable);
    }

    // =========================================================================
    // Inner data classes
    // =========================================================================

    private static class EquationExportRow {
        boolean commentRow;
        String commentText;
        String sourceName;
        String name;
        String expr;
        EquationTableElm.RowOutputMode mode;
        String initialEq;
        String hint;
    }

    private static class GodlyExportRow {
        String stockName;
        String flowExpr;
        double initialValue;
        String hint;
    }

    private static class MatrixExportData {
        ArrayList<String> stockNames = new ArrayList<String>();
        ArrayList<String> stockCodes = new ArrayList<String>();
        ArrayList<String> columnTypes = new ArrayList<String>();
        ArrayList<String> rowNames = new ArrayList<String>();
        ArrayList<String[]> rowValues = new ArrayList<String[]>();
    }

    // =========================================================================
    // Data collection
    // =========================================================================

    private ArrayList<EquationExportRow> collectEquationExportRows(EquationTableElm eqTable, String tableName) {
        ArrayList<EquationExportRow> rows = new ArrayList<EquationExportRow>();
        int rowCount = eqTable.getRowCount();
        for (int row = 0; row < rowCount; row++) {
            String sourceName = eqTable.getOutputName(row);
            if (EquationTableElm.isCommentRowName(sourceName)) {
                String comment = sourceName == null ? "" : sourceName.trim();
                if (comment.startsWith("#")) {
                    comment = comment.substring(1).trim();
                }
                if (!comment.isEmpty()) {
                    EquationExportRow data = new EquationExportRow();
                    data.commentRow = true;
                    data.commentText = comment;
                    rows.add(data);
                }
                continue;
            }

            String name = eqTable.getDisplayOutputName(row);
            if (name == null || name.isEmpty()) {
                continue;
            }

            String expr = eqTable.getEquation(row);
            if (expr == null) {
                expr = "0";
            }
            expr = rewriteExpressionForLookupExport(expr, tableName);

            String initialEq = eqTable.getInitialEquation(row);
            if (initialEq != null && !initialEq.trim().isEmpty()) {
                initialEq = rewriteExpressionForLookupExport(initialEq, tableName);
            }

            EquationExportRow data = new EquationExportRow();
            data.commentRow = false;
            data.sourceName = sourceName;
            data.name = name;
            data.expr = expr;
            data.mode = eqTable.getOutputMode(row);
            data.initialEq = initialEq;
            data.hint = RStyleFormatter.sanitizeHintForRStyleExport(HintRegistry.getHint(sourceName));
            rows.add(data);
        }
        return rows;
    }

    private ArrayList<GodlyExportRow> collectGodlyExportRows(GodlyTableElm godlyTable, String tableName) {
        ArrayList<GodlyExportRow> rows = new ArrayList<GodlyExportRow>();
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
            GodlyExportRow row = new GodlyExportRow();
            row.stockName = stockName;
            row.flowExpr = rewriteExpressionForLookupExport(buildColumnFlowExpression(godlyTable, col), tableName);
            row.initialValue = godlyTable.getInitialValue(col);
            row.hint = HintRegistry.getHint(stockName);
            rows.add(row);
        }
        return rows;
    }

    private MatrixExportData collectMatrixExportData(SFCTableElm sfcTable, String tableName, boolean includeCodes) {
        MatrixExportData data = new MatrixExportData();
        int totalCols = sfcTable.getCols();
        Set<String> usedCodes = includeCodes ? new HashSet<String>() : null;

        for (int col = 0; col < totalCols; col++) {
            TableColumn column = sfcTable.getColumn(col);
            if (column == null || column.isALE()) {
                continue;
            }
            String stockName = column.getStockName();
            if (stockName == null || stockName.trim().isEmpty()) {
                stockName = "Column" + (data.stockNames.size() + 1);
            }
            data.stockNames.add(stockName);
            data.columnTypes.add(formatMatrixColumnType(column.getType()));
            if (includeCodes) {
                data.stockCodes.add(buildSequentialMatrixCode(stockName, data.stockNames.size(), usedCodes));
            }
        }

        int rows = sfcTable.getRows();
        for (int row = 0; row < rows; row++) {
            String rowDesc = sfcTable.getRowDescription(row);
            if (rowDesc == null || rowDesc.trim().isEmpty()) {
                rowDesc = "Row" + row;
            }
            data.rowNames.add(rowDesc);

            String[] rowData = new String[data.stockNames.size()];
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
                rowData[dataColIndex++] = rewriteExpressionForLookupExport(cellExpr, tableName);
            }
            data.rowValues.add(rowData);
        }
        return data;
    }

    private String buildSequentialMatrixCode(String stockName, int index, Set<String> usedCodes) {
        char prefix = 'X';
        if (stockName != null) {
            for (int i = 0; i < stockName.length(); i++) {
                char c = stockName.charAt(i);
                if (Character.isLetter(c)) {
                    prefix = Character.toUpperCase(c);
                    break;
                }
            }
        }

        String candidate = prefix + Integer.toString(Math.max(1, index));
        if (usedCodes == null) {
            return candidate;
        }
        int suffix = Math.max(1, index);
        while (usedCodes.contains(candidate)) {
            suffix++;
            candidate = prefix + Integer.toString(suffix);
        }
        usedCodes.add(candidate);
        return candidate;
    }

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

    // =========================================================================
    // Block-format element exporters
    // =========================================================================

    private String exportEquationTableBlock(EquationTableElm eqTable) {
        StringBuilder sb = new StringBuilder();
        String tableName = eqTable.getTableName();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Equations";
        }
        ArrayList<EquationExportRow> rows = collectEquationExportRows(eqTable, tableName);
        if (rows.isEmpty()) return "";

        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_EQUATIONS, SFCRUtil.sanitizeName(tableName));
        sb.append("@equations ").append(SFCRUtil.sanitizeName(tableName));
        sb.append(SFCRUtil.formatPosition(eqTable)).append("\n");
        sb.append("  uid: ").append(eqTable.getPersistentUid()).append("\n");
        sb.append("  invisible: ").append(eqTable.isInvisible()).append("\n");

        for (int i = 0; i < rows.size(); i++) {
            EquationExportRow row = rows.get(i);
            if (row.commentRow) {
                sb.append("  # ").append(row.commentText).append("\n");
                continue;
            }
            sb.append("  ").append(row.name).append(" ~ ").append(row.expr);
            sb.append(" ; mode=").append(SFCRUtil.formatEquationRowMode(row.mode));
            if (row.initialEq != null && !row.initialEq.trim().isEmpty()) {
                sb.append(" ; initial=").append(row.initialEq.trim());
            }
            if (row.hint != null && !row.hint.trim().isEmpty()) {
                sb.append("  # ").append(row.hint);
            }
            sb.append("\n");
        }
        sb.append("@end\n");
        return sb.toString();
    }

    private String exportGodlyTableBlock(GodlyTableElm godlyTable) {
        StringBuilder sb = new StringBuilder();
        String tableName = godlyTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Stocks";
        }
        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_EQUATIONS, SFCRUtil.sanitizeName(tableName));
        sb.append("@equations ").append(SFCRUtil.sanitizeName(tableName));
        sb.append(SFCRUtil.formatPosition(godlyTable)).append("\n");

        ArrayList<GodlyExportRow> rows = collectGodlyExportRows(godlyTable, tableName);
        for (int i = 0; i < rows.size(); i++) {
            GodlyExportRow row = rows.get(i);
            sb.append("  # Initial value: ").append(row.initialValue).append("\n");
            sb.append("  ").append(row.stockName).append(" ~ ");
            sb.append(row.stockName).append("_init + integrate(").append(row.flowExpr).append(")");
            if (row.hint != null && !row.hint.trim().isEmpty()) {
                sb.append("  # ").append(row.hint);
            }
            sb.append("\n");
            sb.append("\n");
        }
        sb.append("@end\n");
        return sb.toString();
    }

    private String exportSFCTableBlock(SFCTableElm sfcTable) {
        StringBuilder sb = new StringBuilder();
        String tableName = sfcTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "SFC_Matrix";
        }
        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_MATRIX, SFCRUtil.sanitizeName(tableName));
        sb.append("@matrix ").append(SFCRUtil.sanitizeName(tableName));
        sb.append(SFCRUtil.formatPosition(sfcTable)).append("\n");
        sb.append("  uid: ").append(sfcTable.getPersistentUid()).append("\n");
        sb.append("  type: transaction_flow\n");
        sb.append("  invisible: ").append(sfcTable.isInvisible()).append("\n");

        MatrixExportData data = collectMatrixExportData(sfcTable, tableName, false);
        int dataCols = data.stockNames.size();
        sb.append("\n");
        sb.append("  columnTypes: ");
        for (int col = 0; col < data.columnTypes.size(); col++) {
            if (col > 0) {
                sb.append(", ");
            }
            sb.append(data.columnTypes.get(col));
        }
        sb.append("\n\n");

        sb.append("| Transaction |");
        for (int col = 0; col < data.stockNames.size(); col++) {
            sb.append(" ").append(data.stockNames.get(col)).append(" |");
        }
        sb.append("\n");
        sb.append("|-------------|");
        for (int col = 0; col < dataCols; col++) {
            sb.append("------|");
        }
        sb.append("\n");
        for (int row = 0; row < data.rowNames.size(); row++) {
            sb.append("| ").append(data.rowNames.get(row)).append(" |");
            String[] rowData = data.rowValues.get(row);
            for (int col = 0; col < dataCols; col++) {
                String cellExpr = (col < rowData.length) ? rowData[col] : "";
                sb.append(" ").append(cellExpr).append(" |");
            }
            sb.append("\n");
        }
        sb.append("@end\n");
        return sb.toString();
    }

    // =========================================================================
    // R-style element exporters
    // =========================================================================

    private String exportEquationTableRStyle(EquationTableElm eqTable) {
        StringBuilder sb = new StringBuilder();
        String tableName = eqTable.getTableName();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Equations";
        }
        ArrayList<EquationExportRow> rows = collectEquationExportRows(eqTable, tableName);
        if (rows.isEmpty()) {
            return "";
        }
        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_EQUATIONS, SFCRUtil.sanitizeName(tableName));

        int equationCount = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).commentRow) {
                equationCount++;
            }
        }
        if (equationCount == 0) {
            return "";
        }

        String assignmentName = RStyleFormatter.toRAssignmentName(tableName);
        sb.append(assignmentName).append(" <- sfcr_set(\n");
        String metadataComment = RStyleFormatter.formatRBlockMetadataComment(eqTable, null).trim();
        if (!metadataComment.isEmpty()) {
            sb.append("  ").append(metadataComment).append("\n");
        }

        int emitted = 0;
        for (int i = 0; i < rows.size(); i++) {
            EquationExportRow row = rows.get(i);
            if (row.commentRow) {
                sb.append("  # ").append(row.commentText).append("\n");
                continue;
            }
            emitted++;
            sb.append("  ").append(row.name).append(" ~ ").append(row.expr);
            if (emitted < equationCount) {
                sb.append(",");
            }
            String rowMeta = RStyleFormatter.formatRStyleEquationInlineMetadata(row.mode, row.initialEq);
            RStyleFormatter.appendRStyleInlineComment(sb, row.hint, rowMeta);
            sb.append("\n");
        }
        sb.append(")\n");
        return sb.toString();
    }

    private String exportGodlyTableRStyle(GodlyTableElm godlyTable) {
        StringBuilder sb = new StringBuilder();
        String tableName = godlyTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Stocks";
        }
        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_EQUATIONS, SFCRUtil.sanitizeName(tableName));
        sb.append(RStyleFormatter.formatRBlockMetadataComment(godlyTable, null));

        ArrayList<GodlyExportRow> rows = collectGodlyExportRows(godlyTable, tableName);
        if (rows.isEmpty()) {
            return "";
        }

        String assignmentName = RStyleFormatter.toRAssignmentName(tableName);
        sb.append(assignmentName).append(" <- sfcr_set(\n");
        for (int i = 0; i < rows.size(); i++) {
            GodlyExportRow row = rows.get(i);
            sb.append("  ")
                .append(row.stockName).append(" ~ ")
                .append(row.stockName).append("_init + integrate(").append(row.flowExpr).append(")");
            if (i < rows.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(")\n");
        return sb.toString();
    }

    private String exportSFCTableRStyle(SFCTableElm sfcTable) {
        StringBuilder sb = new StringBuilder();
        String tableName = sfcTable.getTableTitle();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "SFC_Matrix";
        }
        appendLeadingBlockComments(sb, SFCRBlockCommentRegistry.TYPE_MATRIX, SFCRUtil.sanitizeName(tableName));

        MatrixExportData data = collectMatrixExportData(sfcTable, tableName, true);
        if (data.stockNames.isEmpty()) {
            return "";
        }

        String assignmentName = RStyleFormatter.toRAssignmentName(tableName);
        sb.append(assignmentName).append(" <- sfcr_matrix(\n");
        String metadataComment = RStyleFormatter.formatRBlockMetadataComment(sfcTable, "transaction_flow").trim();
        if (!metadataComment.isEmpty()) {
            sb.append("  ").append(metadataComment).append("\n");
        }
        sb.append("  columns = c(").append(RStyleFormatter.joinRQuoted(data.stockNames)).append("),\n");
        sb.append("  codes = c(").append(RStyleFormatter.joinRQuoted(data.stockCodes)).append("),\n");
        sb.append("  type = c(").append(RStyleFormatter.joinRQuoted(toRStyleMatrixColumnTypes(data.columnTypes))).append("),\n");

        for (int row = 0; row < data.rowNames.size(); row++) {
            sb.append("  c(\"").append(RStyleFormatter.escapeRString(data.rowNames.get(row))).append("\"");
            String[] rowData = data.rowValues.get(row);
            for (int col = 0; col < data.stockCodes.size(); col++) {
                String cellExpr = (col < rowData.length) ? rowData[col] : "";
                sb.append(", ")
                  .append(data.stockCodes.get(col))
                  .append(" = \"")
                  .append(RStyleFormatter.escapeRString(cellExpr))
                  .append("\"");
            }
            sb.append(")");
            if (row < data.rowNames.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(")\n");
        return sb.toString();
    }

    private String formatMatrixColumnType(TableColumn.ColumnType type) {
        if (type == null) {
            return "None";
        }
        switch (type) {
            case NONE:
                return "None";
            case LIABILITY:
                return "Liability";
            case EQUITY:
                return "Equity";
            case COMPUTED:
                return "Computed";
            case SECTOR:
                return "Sector";
            case ASSET:
            default:
                return "Asset";
        }
    }

    private ArrayList<String> toRStyleMatrixColumnTypes(ArrayList<String> columnTypes) {
        ArrayList<String> values = new ArrayList<String>();
        if (columnTypes == null) {
            return values;
        }
        for (int i = 0; i < columnTypes.size(); i++) {
            String type = columnTypes.get(i);
            values.add("None".equals(type) ? "" : type);
        }
        return values;
    }

    // =========================================================================
    // Lookup registration (delegates to LookupExportManager)
    // =========================================================================

    /**
     * Pre-seed lookup names from an SFCR template so that round-trip export
     * preserves user-assigned lookup names.  Must be called before equation
     * blocks are collected (i.e. before handlers run).
     */
    public void seedLookupNamesFromTemplate(String sourceText) {
        lookupManager.seedFromTemplate(sourceText);
    }

    private String rewriteExpressionForLookupExport(String expr, String scopeName) {
        return lookupManager.rewriteExpression(expr, scopeName);
    }

    // =========================================================================
    // Expression parsing utilities (private nested class)
    // =========================================================================

    /** Static utilities for parsing expressions. */
    private static final class ExpressionParser {

        private ExpressionParser() {}

        static int findFunctionCall(String expr, String name, int fromIndex) {
            String lower = expr.toLowerCase();
            String needle = name.toLowerCase();
            int idx = fromIndex;
            while (true) {
                idx = lower.indexOf(needle, idx);
                if (idx < 0) {
                    return -1;
                }
                int end = idx + needle.length();
                if (idx > 0 && isIdentifierPart(expr.charAt(idx - 1))) {
                    idx = end;
                    continue;
                }
                int j = end;
                while (j < expr.length() && Character.isWhitespace(expr.charAt(j))) {
                    j++;
                }
                if (j < expr.length() && expr.charAt(j) == '(') {
                    return idx;
                }
                idx = end;
            }
        }

        static int findMatchingParen(String text, int openIndex) {
            int depth = 0;
            for (int i = openIndex; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
            return -1;
        }

        static ArrayList<String> splitTopLevelArgs(String text) {
            ArrayList<String> out = new ArrayList<String>();
            int depth = 0;
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    out.add(text.substring(start, i));
                    start = i + 1;
                }
            }
            out.add(text.substring(start));
            return out;
        }

        private static boolean isIdentifierPart(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == '\\' || c == '^' || c == '{' || c == '}' || c == '.';
        }
    }

    // =========================================================================
    // Lookup export state management (private inner class)
    // =========================================================================

    /** Manages lookup table export state and registration. */
    private final class LookupExportManager {

        private ArrayList<LookupDefinition> specs = new ArrayList<LookupDefinition>();
        private HashMap<String, LookupDefinition> bySignature = new HashMap<String, LookupDefinition>();
        private HashMap<String, ArrayList<String>> commentsByNameScope = new HashMap<String, ArrayList<String>>();

        ArrayList<LookupDefinition> getSpecs() { return specs; }
        void setSpecs(ArrayList<LookupDefinition> s) { specs = (s != null) ? s : new ArrayList<LookupDefinition>(); }

        HashMap<String, LookupDefinition> getBySignature() { return bySignature; }
        void setBySignature(HashMap<String, LookupDefinition> m) { bySignature = (m != null) ? m : new HashMap<String, LookupDefinition>(); }

        HashMap<String, ArrayList<String>> getCommentsByNameScope() { return commentsByNameScope; }
        void setCommentsByNameScope(HashMap<String, ArrayList<String>> m) { commentsByNameScope = (m != null) ? m : new HashMap<String, ArrayList<String>>(); }

        void reset() {
            specs.clear();
            bySignature.clear();
            commentsByNameScope.clear();
        }

        void seedFromTemplate(String sourceText) {
            if (sourceText == null || sourceText.trim().isEmpty()) {
                return;
            }
            String[] lines = sourceText.split("\\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String header = lines[i] == null ? "" : lines[i].trim();
                if (!header.startsWith("@lookup")) {
                    continue;
                }
                String body = header.substring("@lookup".length()).trim();
                if (body.isEmpty()) {
                    continue;
                }
                String lookupName = body;
                String scopeName = null;
                int scopePos = body.indexOf(" scope=");
                if (scopePos >= 0) {
                    lookupName = body.substring(0, scopePos).trim();
                    scopeName = body.substring(scopePos + " scope=".length()).trim();
                }
                lookupName = SFCRUtil.sanitizeName(SFCRUtil.normalizeVariableName(lookupName));
                if (scopeName != null && !scopeName.isEmpty()) {
                    scopeName = SFCRUtil.sanitizeName(scopeName);
                } else {
                    scopeName = null;
                }
                if (lookupName.isEmpty()) {
                    continue;
                }
                ArrayList<Double> xs = new ArrayList<Double>();
                ArrayList<Double> ys = new ArrayList<Double>();
                ArrayList<String> comments = new ArrayList<String>();
                int end = i;
                for (int j = i + 1; j < lines.length; j++) {
                    String row = lines[j] == null ? "" : lines[j].trim();
                    end = j;
                    if (row.equals("@end")) {
                        break;
                    }
                    if (row.startsWith("#")) {
                        comments.add(row);
                        continue;
                    }
                    if (row.isEmpty()) {
                        continue;
                    }
                    String[] pair;
                    if (row.indexOf(',') >= 0) {
                        pair = row.split(",", 2);
                    } else {
                        pair = row.split("\\s+", 2);
                    }
                    if (pair.length < 2) {
                        continue;
                    }
                    try {
                        xs.add(Double.valueOf(Double.parseDouble(pair[0].trim())));
                        ys.add(Double.valueOf(Double.parseDouble(pair[1].trim())));
                    } catch (Exception ignored) {
                    }
                }
                i = end;
                if (xs.size() < 2 || ys.size() != xs.size()) {
                    continue;
                }
                if (!comments.isEmpty()) {
                    commentsByNameScope.put(buildNameScopeKey(lookupName, scopeName), new ArrayList<String>(comments));
                }
                String signature = buildSignatureFromPoints(scopeName, xs, ys);
                LookupDefinition existing = bySignature.get(signature);
                if (existing != null) {
                    if (existing.name != null && existing.name.startsWith("Lookup_") && !lookupName.startsWith("Lookup_")) {
                        existing.name = makeLookupName(lookupName, scopeName);
                    }
                    if (existing.comments.isEmpty() && !comments.isEmpty()) {
                        existing.comments.addAll(comments);
                    }
                    continue;
                }
                LookupDefinition spec = new LookupDefinition();
                spec.scope = scopeName;
                spec.name = makeLookupName(lookupName, scopeName);
                spec.xs.addAll(xs);
                spec.ys.addAll(ys);
                spec.comments.addAll(comments);
                specs.add(spec);
                bySignature.put(signature, spec);
            }
        }

        String rewriteExpression(String expr, String scopeName) {
            if (expr == null || expr.trim().isEmpty()) {
                return expr;
            }
            registerNativeSpecs(expr, scopeName);
            return expr;
        }

        private void registerNativeSpecs(String expr, String scopeName) {
            if (expr == null || expr.trim().isEmpty()) {
                return;
            }
            int search = 0;
            while (true) {
                int fn = ExpressionParser.findFunctionCall(expr, "lookup", search);
                if (fn < 0) {
                    break;
                }
                int open = expr.indexOf('(', fn);
                if (open < 0) {
                    break;
                }
                int close = ExpressionParser.findMatchingParen(expr, open);
                if (close < 0) {
                    break;
                }
                String inside = expr.substring(open + 1, close);
                ArrayList<String> args = ExpressionParser.splitTopLevelArgs(inside);
                if (args.size() >= 2) {
                    registerFromNameArg(args.get(0), scopeName);
                }
                search = close + 1;
            }
        }

        private void registerFromNameArg(String nameArg, String scopeName) {
            if (nameArg == null) {
                return;
            }
            String raw = nameArg.trim();
            if (raw.isEmpty()) {
                return;
            }
            if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
                if (raw.length() < 2) {
                    return;
                }
                raw = raw.substring(1, raw.length() - 1).trim();
            }

            String lookupName = SFCRUtil.sanitizeName(SFCRUtil.normalizeVariableName(raw));
            if (lookupName.isEmpty()) {
                return;
            }

            String normalizedScope = (scopeName == null || scopeName.isEmpty()) ? null : SFCRUtil.sanitizeName(scopeName);
            if (findByNameAndScope(lookupName, normalizedScope) != null) {
                return;
            }

            LookupTableRegistry.LookupTableSnapshot snapshot = LookupTableRegistry.getSnapshot(normalizedScope, lookupName);
            if (snapshot == null || snapshot.xs == null || snapshot.ys == null || snapshot.xs.size() < 2 || snapshot.xs.size() != snapshot.ys.size()) {
                return;
            }

            String resolvedScope = (snapshot.resolvedScope == null || snapshot.resolvedScope.isEmpty())
                ? null
                : snapshot.resolvedScope;
            String signature = buildSignatureFromPoints(resolvedScope, snapshot.xs, snapshot.ys);
            LookupDefinition existingBySignature = bySignature.get(signature);
            if (existingBySignature != null) {
                if (existingBySignature.name != null
                        && existingBySignature.name.endsWith("_lookup")
                        && !lookupName.endsWith("_lookup")
                        && !isNameTaken(lookupName, resolvedScope)) {
                    existingBySignature.name = lookupName;
                }
                if (existingBySignature.comments.isEmpty()) {
                    existingBySignature.comments.addAll(getComments(lookupName, resolvedScope));
                }
                return;
            }

            LookupDefinition spec = new LookupDefinition();
            spec.name = lookupName;
            spec.scope = resolvedScope;
            spec.xs.addAll(snapshot.xs);
            spec.ys.addAll(snapshot.ys);
            spec.comments.addAll(getComments(spec.name, spec.scope));
            if (spec.comments.isEmpty()) {
                spec.comments.addAll(getComments(lookupName, resolvedScope));
            }

            specs.add(spec);
            bySignature.put(signature, spec);
        }

        private LookupDefinition findByNameAndScope(String name, String scopeName) {
            String normName = SFCRUtil.sanitizeName(SFCRUtil.normalizeVariableName(name));
            String normScope = (scopeName == null || scopeName.isEmpty()) ? "" : SFCRUtil.sanitizeName(scopeName);
            for (int i = 0; i < specs.size(); i++) {
                LookupDefinition spec = specs.get(i);
                if (spec == null || spec.name == null) {
                    continue;
                }
                String specName = SFCRUtil.sanitizeName(SFCRUtil.normalizeVariableName(spec.name));
                String specScope = (spec.scope == null || spec.scope.isEmpty()) ? "" : SFCRUtil.sanitizeName(spec.scope);
                if (normName.equals(specName) && normScope.equals(specScope)) {
                    return spec;
                }
            }
            return null;
        }

        private String makeLookupName(String preferredName, String scopeName) {
            String base;
            if (preferredName == null || preferredName.trim().isEmpty()) {
                base = "Lookup_" + (specs.size() + 1);
            } else {
                base = SFCRUtil.normalizeVariableName(preferredName.trim());
                base = SFCRUtil.sanitizeName(base);
                if (base.isEmpty()) {
                    base = "Lookup_" + (specs.size() + 1);
                }
            }
            if (isEquationNameTakenInScope(base, scopeName)) {
                base = base + "_lookup";
            }
            String candidate = base;
            int suffix = 2;
            while (isNameTaken(candidate, scopeName) || isEquationNameTakenInScope(candidate, scopeName)) {
                candidate = base + "_" + suffix;
                suffix++;
            }
            return candidate;
        }

        private boolean isEquationNameTakenInScope(String name, String scopeName) {
            if (name == null || name.isEmpty()) {
                return false;
            }
            String wantedScope = scopeName == null ? "" : SFCRUtil.sanitizeName(scopeName);
            for (int i = 0; i < equationTables.size(); i++) {
                EquationTableElm table = equationTables.get(i);
                if (table == null) {
                    continue;
                }
                String tableScope = SFCRUtil.sanitizeName(table.getTableName());
                if (!tableScope.equals(wantedScope)) {
                    continue;
                }
                for (int r = 0; r < table.getRowCount(); r++) {
                    String lhs = SFCRUtil.sanitizeName(table.getOutputName(r));
                    if (name.equals(lhs)) {
                        return true;
                    }
                }
            }
            for (int i = 0; i < godlyTables.size(); i++) {
                GodlyTableElm table = godlyTables.get(i);
                if (table == null || table.getCols() < 2) {
                    continue;
                }
                String tableScope = SFCRUtil.sanitizeName(table.getTableTitle());
                if (!tableScope.equals(wantedScope)) {
                    continue;
                }
                for (int c = 1; c < table.getCols(); c++) {
                    TableColumn column = table.getColumn(c);
                    if (column == null) {
                        continue;
                    }
                    String stockName = SFCRUtil.sanitizeName(column.getStockName());
                    if (name.equals(stockName)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isNameTaken(String name, String scopeName) {
            for (int i = 0; i < specs.size(); i++) {
                LookupDefinition existing = specs.get(i);
                if (existing == null) {
                    continue;
                }
                boolean sameScope =
                    (existing.scope == null ? "" : existing.scope).equals(scopeName == null ? "" : scopeName);
                if (sameScope && name.equals(existing.name)) {
                    return true;
                }
            }
            return false;
        }

        private ArrayList<String> getComments(String name, String scopeName) {
            ArrayList<String> comments = commentsByNameScope.get(buildNameScopeKey(name, scopeName));
            if (comments == null) {
                return new ArrayList<String>();
            }
            return new ArrayList<String>(comments);
        }

        private String buildNameScopeKey(String name, String scopeName) {
            String normName = SFCRUtil.sanitizeName(SFCRUtil.normalizeVariableName(name));
            String normScope = (scopeName == null || scopeName.isEmpty()) ? "" : SFCRUtil.sanitizeName(scopeName);
            return normScope + "|" + normName;
        }

        private String buildSignatureFromPoints(String scopeName, ArrayList<Double> xs, ArrayList<Double> ys) {
            StringBuilder sb = new StringBuilder();
            if (scopeName != null && !scopeName.isEmpty()) {
                sb.append(SFCRUtil.sanitizeName(scopeName));
            }
            sb.append("|");
            for (int i = 0; i < xs.size() && i < ys.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(xs.get(i).doubleValue());
                sb.append(",");
                sb.append(ys.get(i).doubleValue());
            }
            return sb.toString();
        }
    }

    // =========================================================================
    // R-style formatting utilities (private nested class)
    // =========================================================================

    /** Static helper methods for R-style SFCR export formatting. */
    private static final class RStyleFormatter {

        private RStyleFormatter() {}

        static String toRAssignmentName(String name) {
            String base = SFCRUtil.sanitizeName(name);
            return toRCodeIdentifier(base);
        }

        static String toRCodeIdentifier(String text) {
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

        static String makeUniqueRCode(String base, Set<String> usedCodes) {
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

        static String joinRQuoted(ArrayList<String> values) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("\"").append(escapeRString(values.get(i))).append("\"");
            }
            return sb.toString();
        }

        static String escapeRString(String text) {
            if (text == null) {
                return "";
            }
            return text.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        static String formatRBlockMetadataComment(CircuitElm elm, String type) {
            if (elm == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("# [ x=").append(elm.x).append(" y=").append(elm.y);
            String uid = elm.getPersistentUid();
            if (uid != null && uid.length() > 0) {
                sb.append(" uid=").append(uid);
            }
            if (type != null && type.trim().length() > 0) {
                sb.append(" type: ").append(type.trim());
            }
            if (elm instanceof EquationTableElm) {
                sb.append(" invisible=").append(((EquationTableElm) elm).isInvisible());
            } else if (elm instanceof TableElm) {
                sb.append(" invisible=").append(((TableElm) elm).isInvisible());
            }
            sb.append(" ]\n");
            return sb.toString();
        }

        static String formatRStyleEquationInlineMetadata(EquationTableElm.RowOutputMode mode, String initialEq) {
            StringBuilder sb = new StringBuilder();
            sb.append("[mode=").append(SFCRUtil.formatEquationRowMode(mode));
            if (initialEq != null && !initialEq.trim().isEmpty()) {
                sb.append(", initial=").append(initialEq.trim());
            }
            sb.append(" ]");
            return sb.toString();
        }

        static void appendRStyleInlineComment(StringBuilder sb, String hint, String rowMeta) {
            String cleanHint = (hint == null) ? "" : hint.trim();
            String cleanMeta = (rowMeta == null) ? "" : rowMeta.trim();
            if (cleanHint.isEmpty() && cleanMeta.isEmpty()) {
                return;
            }
            sb.append("  #");
            if (!cleanHint.isEmpty()) {
                sb.append(" ").append(cleanHint);
            }
            if (!cleanMeta.isEmpty()) {
                if (!cleanHint.isEmpty()) {
                    sb.append("  ");
                } else {
                    sb.append(" ");
                }
                sb.append(cleanMeta);
            }
        }

        static String sanitizeHintForRStyleExport(String hint) {
            if (hint == null) {
                return null;
            }
            String working = hint.trim();
            if (working.isEmpty()) {
                return "";
            }
            while (true) {
                int close = working.lastIndexOf(']');
                if (close != working.length() - 1) {
                    break;
                }
                int open = working.lastIndexOf('[', close);
                if (open < 0) {
                    break;
                }
                String chunk = working.substring(open + 1, close);
                if (!looksLikeRStyleMetadataChunk(chunk)) {
                    break;
                }
                working = working.substring(0, open).trim();
            }
            while (true) {
                int open = working.lastIndexOf('[');
                if (open < 0) {
                    break;
                }
                String tail = working.substring(open + 1);
                if (!looksLikeRStyleMetadataChunk(tail)) {
                    break;
                }
                working = working.substring(0, open).trim();
            }
            return working;
        }

        private static boolean looksLikeRStyleMetadataChunk(String chunk) {
            if (chunk == null) {
                return false;
            }
            String text = chunk.trim();
            if (text.isEmpty()) {
                return false;
            }
            String[] tokens = text.split(",");
            int parsed = 0;
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i].trim();
                int eq = token.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = token.substring(0, eq).trim().toLowerCase();
                if (key.length() == 0) {
                    continue;
                }
                if (key.equals("mode") || key.equals("slider") || key.equals("slidervalue") || key.equals("initial")) {
                    parsed++;
                }
            }
            return parsed > 0;
        }
    }
}
