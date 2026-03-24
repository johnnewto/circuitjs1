/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.elements.economics;


import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.registry.HintRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.lushprojects.circuitjs1.client.util.Locale;

public final class InfoViewerTableMarkdown {

    private static final String NO_TABLES_COMMENT =
        "<!-- No table elements found — replace the variable names below with your circuit's computed values -->\n\n";
    private static final String SANKEY_COMMENT =
        "<!-- Replace 'Transaction_Flow_Matrix' with the name of a table element in your circuit -->\n\n";

    private InfoViewerTableMarkdown() {
    }

    /**
     * Generate markdown content displaying all tables from current circuit.
     * Includes balance sheets (GodlyTableElm), transaction matrices (CurrentTransactionsMatrixElm),
     * SFC tables (SFCTableElm), regular tables (TableElm), and equation tables (EquationTableElm).
     */
    public static String generateCircuitTablesMarkdown() {
        if (CirSim.getInstance() == null || CirSim.getInstance().elmList == null) {
            return "No circuit loaded.";
        }

        StringBuilder md = new StringBuilder();
        md.append("# Circuit Tables Overview\n\n");

        ArrayList<EquationTableElm> equationTables = new ArrayList<EquationTableElm>();
        ArrayList<SFCTableElm> sfcTables = new ArrayList<SFCTableElm>();
        ArrayList<GodlyTableElm> godlyTables = new ArrayList<GodlyTableElm>();
        ArrayList<CurrentTransactionsMatrixElm> ctmTables = new ArrayList<CurrentTransactionsMatrixElm>();
        ArrayList<TableElm> otherTables = new ArrayList<TableElm>();

        for (int i = 0; i < CirSim.getInstance().elmList.size(); i++) {
            CircuitElm elm = CirSim.getInstance().elmList.get(i);

            if (elm instanceof EquationTableElm) {
                equationTables.add((EquationTableElm) elm);
            } else if (elm instanceof CurrentTransactionsMatrixElm) {
                ctmTables.add((CurrentTransactionsMatrixElm) elm);
            } else if (elm instanceof SFCTableElm) {
                sfcTables.add((SFCTableElm) elm);
            } else if (elm instanceof GodlyTableElm) {
                godlyTables.add((GodlyTableElm) elm);
            } else if (elm instanceof TableElm) {
                otherTables.add((TableElm) elm);
            }
        }

        if (!godlyTables.isEmpty()) {
            md.append("## Balance Sheets (Godly Tables)\n\n");
            for (GodlyTableElm table : godlyTables) {
                md.append(formatBalanceTable(table));
                md.append("\n");
            }
        }

        if (!sfcTables.isEmpty()) {
            md.append("## SFC Transaction Matrices\n\n");
            for (SFCTableElm table : sfcTables) {
                md.append(formatSFCTable(table));
                md.append("\n");
            }
        }

        if (!ctmTables.isEmpty()) {
            md.append("## Current Transactions Matrices\n\n");
            for (CurrentTransactionsMatrixElm matrix : ctmTables) {
                md.append(formatTransactionMatrix(matrix));
                md.append("\n");
            }
        }

        if (!otherTables.isEmpty()) {
            md.append("## Other Tables\n\n");
            for (TableElm table : otherTables) {
                md.append(formatGenericTable(table));
                md.append("\n");
            }
        }

        if (!equationTables.isEmpty()) {
            md.append("## Equation Tables\n\n");
            for (EquationTableElm table : equationTables) {
                md.append(formatEquationTable(table));
                md.append("\n");
            }
        }

        if (godlyTables.isEmpty() && sfcTables.isEmpty() && ctmTables.isEmpty() &&
            otherTables.isEmpty() && equationTables.isEmpty()) {
            md.append("*No tables found in the current circuit.*\n");
        }

        return md.toString();
    }

    /**
     * Generate fenced circuit blocks for each table so the info viewer can mount live table widgets.
     */
    public static String generateCircuitTableBlocksMarkdown() {
        if (CirSim.getInstance() == null || CirSim.getInstance().elmList == null) {
            return "No circuit loaded.";
        }

        LinkedHashSet<String> tableTitles = new LinkedHashSet<String>();
        for (int i = 0; i < CirSim.getInstance().elmList.size(); i++) {
            CircuitElm elm = CirSim.getInstance().elmList.get(i);
            if (elm instanceof TableElm) {
                TableElm table = (TableElm) elm;
                addNonEmptyTitle(tableTitles, table.getTableTitle());
                continue;
            }
            if (elm instanceof EquationTableElm) {
                EquationTableElm eqTable = (EquationTableElm) elm;
                addNonEmptyTitle(tableTitles, eqTable.getTableName());
            }
        }

        if (tableTitles.isEmpty()) {
            return "*No tables found in the current circuit.*";
        }

        StringBuilder md = new StringBuilder();
        md.append("## Circuit Tables\n\n");
        for (String name : tableTitles) {
            md.append("```{circuit}\n");
            md.append("table: ").append(name).append("\n");
            md.append("```\n\n");
        }
        return md.toString();
    }

    /**
     * Generate a starter @info markdown template using current circuit tables and scope plots.
     */
    public static String generateAutoInfoTemplateMarkdown() {
        CirSim sim = CirSim.getInstance();
        if (sim == null) {
            return "# Model Information\n\nNo circuit loaded.";
        }

        LinkedHashSet<String> tableNames = collectCircuitTableNames(sim);
        LinkedHashSet<String> sankeyTableNames = collectSankeyTableNames(sim);
        ArrayList<String> keyVars = collectPrimaryComputedNames(6);
        ArrayList<ArrayList<String>> scopePlotVars = collectScopePlotVariables(sim, 5);

        StringBuilder md = new StringBuilder();
        md.append("# Model Live Dashboard\n\n");
        md.append("> **Time:** {{t}}  \n");
        md.append("> **Step:** {{dt}}\n\n");

        md.append("## Key Variables\n\n");
        if (keyVars.isEmpty()) {
            md.append("- Add live placeholders like `{{Y}}`, `{{C_d}}`, `{{H_h}}`\n\n");
        } else {
            md.append("| Variable | Live value |\n");
            md.append("|---|---:|\n");
            for (String v : keyVars) {
                md.append("| ").append(v).append(" | {{").append(v).append("}} |\n");
            }
            md.append("\n");
        }

        md.append("## Compose Tables\n\n");
        md.append("- Add live tables like\n\n");
        md.append("| Variable | Live value | Meaning |\n");
        md.append("|---|---:|---|\n");
        md.append("| Output | Y = {{Y}} | Aggregate production/income |\n");
        md.append("| Disposable income | YD = {{YD}} | Household disposable income |\n\n");

        md.append("## Tables\n\n");
        if (tableNames.isEmpty()) {
            md.append(NO_TABLES_COMMENT);
            md.append("### Transaction Flow Matrix\n\n");
            // Build a compact example using whatever vars are available, or generic placeholders
            java.util.ArrayList<String> exVars = collectPrimaryComputedNames(8);
            // Try to guess sector-like groups: pick up to 4 non-empty vars for columns
            String s1 = exVars.size() > 0 ? exVars.get(0) : "C_d";
            String s2 = exVars.size() > 1 ? exVars.get(1) : "C_s";
            String s3 = exVars.size() > 2 ? exVars.get(2) : "G_d";
            String s4 = exVars.size() > 3 ? exVars.get(3) : "G_s";
            md.append("| Flow/Stock | Households | Production | Govt | \u03a3 |\n");
            md.append("|---|---|---|---|---|\n");
            md.append("| Consumption | -").append(s1).append(" = {{").append(s1).append("}} | ")
              .append(s2).append(" = {{").append(s2).append("}} | | |\n");
            md.append("| Govt Expenditure | | ").append(s3).append(" = {{").append(s3).append("}} | -")
              .append(s4).append(" = {{").append(s4).append("}} | |\n");
            if (exVars.size() > 5) {
                String s5 = exVars.get(4);
                String s6 = exVars.get(5);
                md.append("| Income | ").append(s5).append(" = {{").append(s5).append("}} | -")
                  .append(s6).append(" = {{").append(s6).append("}} | | |\n");
            }
            md.append("\n");
        } else {
            for (String tableName : tableNames) {
                md.append("```{circuit}\n");
                md.append("table: ").append(tableName).append("\n");
                md.append("```\n\n");
            }
        }

        md.append("## Plots\n\n");
        if (scopePlotVars.isEmpty()) {
            java.util.ArrayList<String> fallback = collectPrimaryComputedNames(5);
            if (fallback.isEmpty()) {
                md.append("- No scope plots detected. Add a plot block manually.\n\n");
            } else {
                md.append("@plot\n");
                md.append("vars: ").append(joinCsv(fallback)).append("\n");
                md.append("title: Model Dynamics\n");
                md.append("yaxis: Value\n");
                md.append("window: 200\n");
                md.append("@end\n\n");
            }
        } else {
            for (int i = 0; i < scopePlotVars.size(); i++) {
                ArrayList<String> vars = scopePlotVars.get(i);
                String scopeTitle = "Scope " + (i + 1);
                if (sim.scopes != null && i < sim.scopeCount && sim.scopes[i] != null) {
                    String n = sim.scopes[i].getScopeMenuName();
                    if (n != null && !n.trim().isEmpty()) {
                        scopeTitle = n.trim();
                    }
                }
                md.append("@plot\n");
                md.append("vars: ").append(joinCsv(vars)).append("\n");
                md.append("title: ").append(scopeTitle).append("\n");
                md.append("yaxis: Value\n");
                md.append("window: 200\n");
                md.append("@end\n\n");
            }
        }

        md.append("## Sankeys\n\n");
        if (sankeyTableNames.isEmpty()) {
            md.append(SANKEY_COMMENT);
            md.append("```{circuit}\n");
            md.append("sankey: Transaction_Flow_Matrix\n");
            md.append("title: Money Flows\n");
            md.append("width: 600\n");
            md.append("height: 320\n");
            md.append("```\n\n");
        } else {
            for (String tableName : sankeyTableNames) {
                md.append("```{circuit}\n");
                md.append("sankey: ").append(tableName).append("\n");
                md.append("title: ").append(tableName).append("\n");
                md.append("width: 600\n");
                md.append("height: 320\n");
                md.append("```\n\n");
            }
        }

        md.append("## Notes\n\n");
        md.append("- Edit text and equations as needed.\n");
        md.append("- Use `Save to Model` to persist edits into the model source.\n");
        return md.toString();
    }

    private static LinkedHashSet<String> collectSankeyTableNames(CirSim sim) {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        if (sim == null || sim.elmList == null) {
            return names;
        }
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            // Only TableElm subclasses (not EquationTableElm) have column/row data for Sankey
            if (elm instanceof TableElm && !(elm instanceof EquationTableElm)) {
                addNonEmptyTitle(names, ((TableElm) elm).getTableTitle());
            }
        }
        return names;
    }

    private static LinkedHashSet<String> collectCircuitTableNames(CirSim sim) {
        LinkedHashSet<String> tableNames = new LinkedHashSet<String>();
        if (sim == null || sim.elmList == null) {
            return tableNames;
        }
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            if (elm instanceof TableElm) {
                addNonEmptyTitle(tableNames, ((TableElm) elm).getTableTitle());
                continue;
            }
            if (elm instanceof EquationTableElm) {
                addNonEmptyTitle(tableNames, ((EquationTableElm) elm).getTableName());
            }
        }
        return tableNames;
    }

    private static void addNonEmptyTitle(Set<String> output, String title) {
        if (output == null || title == null) {
            return;
        }
        String trimmed = title.trim();
        if (!trimmed.isEmpty()) {
            output.add(trimmed);
        }
    }

    private static ArrayList<String> collectPrimaryComputedNames(int maxCount) {
        ArrayList<String> vars = new ArrayList<String>();
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        String[] names = ComputedValues.getComputedValueNames();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            if (name == null) {
                continue;
            }
            String n = name.trim();
            if (n.isEmpty()) {
                continue;
            }
            if ("t".equalsIgnoreCase(n) || "dt".equalsIgnoreCase(n)) {
                continue;
            }
            if (!seen.add(n)) {
                continue;
            }
            vars.add(n);
            if (vars.size() >= maxCount) {
                break;
            }
        }
        return vars;
    }

    private static ArrayList<ArrayList<String>> collectScopePlotVariables(CirSim sim, int maxVarsPerScope) {
        ArrayList<ArrayList<String>> result = new ArrayList<ArrayList<String>>();
        if (sim == null || sim.scopes == null || sim.scopeCount <= 0) {
            return result;
        }

        HashSet<String> allVars = new HashSet<String>();
        String[] names = ComputedValues.getComputedValueNames();
        for (int i = 0; i < names.length; i++) {
            if (names[i] != null) {
                allVars.add(names[i]);
            }
        }

        ArrayList<String> fallbackVars = collectPrimaryComputedNames(Math.max(1, maxVarsPerScope));

        for (int i = 0; i < sim.scopeCount; i++) {
            Scope scope = sim.scopes[i];
            if (scope == null || !scope.active()) {
                continue;
            }

            String label = scope.getScopeMenuName();
            LinkedHashSet<String> scopeVars = new LinkedHashSet<String>();

            if (label != null) {
                int len = label.length();
                int pos = 0;
                while (pos < len && scopeVars.size() < maxVarsPerScope) {
                    while (pos < len) {
                        char ch = label.charAt(pos);
                        if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || ch == '_') {
                            break;
                        }
                        pos++;
                    }
                    if (pos >= len) {
                        break;
                    }
                    int start = pos;
                    pos++;
                    while (pos < len) {
                        char ch = label.charAt(pos);
                        if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
                            pos++;
                        } else {
                            break;
                        }
                    }
                    String token = label.substring(start, pos);
                    if (allVars.contains(token)) {
                        scopeVars.add(token);
                    }
                }
            }

            if (scopeVars.isEmpty()) {
                for (int j = 0; j < fallbackVars.size() && scopeVars.size() < maxVarsPerScope; j++) {
                    scopeVars.add(fallbackVars.get(j));
                }
            }

            if (!scopeVars.isEmpty()) {
                result.add(new ArrayList<String>(scopeVars));
            }
        }

        return result;
    }

    private static String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    /**
     * Format a GodlyTableElm as markdown table (balance sheet).
     */
    private static String formatBalanceTable(GodlyTableElm table) {
        return formatTableElmMarkdown(table, "Balance Sheet", "Flow/Stock", true, "*Empty table*");
    }

    /**
     * Format a CurrentTransactionsMatrixElm as markdown table.
     */
    private static String formatTransactionMatrix(CurrentTransactionsMatrixElm matrix) {
        return formatTableElmMarkdown(matrix, "Transaction Matrix", "Transaction", false, "*Empty matrix*");
    }

    /**
     * Format an SFCTableElm as markdown table.
     */
    private static String formatSFCTable(SFCTableElm table) {
        return formatTableElmMarkdown(table, "SFC Table", "Transaction", false, "*Empty table*");
    }

    /**
     * Format a generic TableElm as markdown table.
     */
    private static String formatGenericTable(TableElm table) {
        return formatTableElmMarkdown(table, "Table", "Row", false, "*Empty table*");
    }

    private static String formatTableElmMarkdown(TableElm table, String defaultTitle, String firstColumnHeader,
                                                boolean skipALEColumns, String emptyMessage) {
        StringBuilder md = new StringBuilder();

        String title = table.getTableTitle();
        if (title == null || title.isEmpty()) {
            title = defaultTitle;
        }
        md.append("### ").append(formatWithGreekAndSubscripts(title)).append("\n\n");

        int rows = table.getRows();
        int cols = table.getCols();

        ArrayList<Integer> includedCols = new ArrayList<Integer>();
        for (int col = 0; col < cols; col++) {
            TableColumn column = table.getColumn(col);
            if (column == null) {
                continue;
            }
            if (skipALEColumns && column.isALE()) {
                continue;
            }
            includedCols.add(Integer.valueOf(col));
        }

        if (includedCols.isEmpty()) {
            md.append(emptyMessage).append("\n\n");
            return md.toString();
        }

        md.append("| ").append(firstColumnHeader).append(" |");
        for (int i = 0; i < includedCols.size(); i++) {
            int col = includedCols.get(i).intValue();
            TableColumn column = table.getColumn(col);
            String header = column.getStockName();
            if (header == null || header.isEmpty()) {
                header = "Col" + (col + 1);
            }
            md.append(" ").append(formatWithGreekAndSubscripts(header)).append(" |");
        }
        md.append("\n");

        md.append("|----------|");
        for (int i = 0; i < includedCols.size(); i++) {
            md.append("----------|");
        }
        md.append("\n");

        for (int row = 0; row < rows; row++) {
            String rowDesc = table.getRowDescription(row);
            if (rowDesc == null || rowDesc.isEmpty()) {
                rowDesc = "Row " + (row + 1);
            }
            md.append("| ").append(formatWithGreekAndSubscripts(rowDesc)).append(" |");

            for (int i = 0; i < includedCols.size(); i++) {
                int col = includedCols.get(i).intValue();
                TableColumn column = table.getColumn(col);
                String cellEq = column.getCellEquation(row);
                if (cellEq == null || cellEq.isEmpty()) {
                    cellEq = "0";
                }
                if (cellEq.trim().startsWith("-")) {
                    md.append(" ").append(wrapNegativeEquation(formatWithGreekAndSubscripts(cellEq))).append(" |");
                } else {
                    md.append(" ").append(formatWithGreekAndSubscripts(cellEq)).append(" |");
                }
            }
            md.append("\n");
        }
        md.append("\n");

        return md.toString();
    }

    /**
     * Format an EquationTableElm as markdown table.
     */
    private static String formatEquationTable(EquationTableElm table) {
        StringBuilder md = new StringBuilder();

        String tableName = table.getTableName();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Equation Table";
        }
        md.append("### ").append(formatWithGreekAndSubscripts(tableName)).append("\n\n");

        int rowCount = table.getRowCount();

        if (rowCount == 0) {
            md.append("*No equations*\n\n");
            return md.toString();
        }

        md.append("| Output | Equation | Hint |\n");
        md.append("|:-------|:---------|:-----|\n");

        for (int row = 0; row < rowCount; row++) {
            String outputName = table.getOutputName(row);
            String equation = table.getEquation(row);

            if (outputName == null || outputName.isEmpty()) {
                outputName = "Out" + (row + 1);
            }
            if (equation == null || equation.isEmpty()) {
                equation = "0";
            }

            String hint = HintRegistry.getHint(outputName);
            if (hint == null || hint.isEmpty()) {
                hint = "";
            }

            md.append("| ").append(formatWithGreekAndSubscripts(outputName)).append(" | ");

            if (equation.trim().startsWith("-")) {
            md.append(wrapNegativeEquation(formatWithGreekAndSubscripts(equation)));
            } else {
                md.append(formatWithGreekAndSubscripts(equation));
            }

            md.append(" | ").append(formatWithGreekAndSubscripts(hint)).append(" |\n");
        }
        md.append("\n");

        return md.toString();
    }

    private static String formatWithGreekAndSubscripts(String text) {
        if (text == null) {
            return "";
        }
        return Locale.convertToHTML(text);
    }

        private static String wrapNegativeEquation(String equationHtml) {
    	return "<span style='color:#cf222e'>" + equationHtml + "</span>";
        }
}
