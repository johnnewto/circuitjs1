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

import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;

/**
 * TableMarkdownDebugDialog - Resizable dialog for viewing markdown representation of Stock-Flow tables
 * 
 * This dialog shows a formatted markdown view of all tables that share stock variables,
 * making it easy to debug synchronization issues and inspect table contents.
 * 
 * Features:
 * - Resizable text area for viewing large markdown output
 * - Non-modal so it can be kept open while editing tables
 * - Auto-refresh capability to update content on demand
 * - Positioned in top-right corner by default
 */
public class TableMarkdownDebugDialog {
    
    private DialogBox dialog;
    private TextArea textArea;
    private TableElm sourceTable;
    private CirSim sim;
    
    /**
     * Create and show the markdown debug dialog
     * @param sourceTable The table to generate markdown for (and its related tables)
     */
    public TableMarkdownDebugDialog(TableElm sourceTable) {
        this.sourceTable = sourceTable;
        this.sim = CirSim.theSim;
        createDialog();
    }
    
    /**
     * Create the dialog UI
     */
    private void createDialog() {
        dialog = new DialogBox();
        dialog.setText("Markdown Debug View");
        dialog.setModal(false);  // Non-modal so it doesn't block interaction
        dialog.setGlassEnabled(false);  // No glass pane background
        
        VerticalPanel panel = new VerticalPanel();
        panel.setWidth("800px");
        
        // Text area with markdown content
        textArea = new TextArea();
        textArea.setText(generateMarkdownContent());
        textArea.setWidth("780px");
        textArea.setHeight("500px");
        textArea.getElement().getStyle().setProperty("fontFamily", "monospace");
        textArea.getElement().getStyle().setProperty("fontSize", "10px");
        
        // Make text area resizable
        makeResizable(textArea.getElement());
        
        panel.add(textArea);
        
        // Buttons
        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setSpacing(5);
        buttonPanel.getElement().getStyle().setProperty("marginTop", "10px");
        
        Button copyButton = new Button("Copy to Clipboard");
        copyButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                textArea.setFocus(true);
                textArea.selectAll();
                copyToClipboard();
                textArea.setSelectionRange(0, 0);
            }
        });
        buttonPanel.add(copyButton);
        
        Button refreshButton = new Button("🔄 Refresh");
        refreshButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                refresh();
            }
        });
        buttonPanel.add(refreshButton);
        
        Button closeButton = new Button("Close");
        closeButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                hide();
            }
        });
        buttonPanel.add(closeButton);
        
        panel.add(buttonPanel);
        dialog.setWidget(panel);
        
        // Initialize resizable styles
        addResizableStyles();
    }
    
    /**
     * Show the dialog
     */
    public void show() {
        dialog.show();
        
        // Position in top-right corner instead of centering
        dialog.setPopupPosition(
            com.google.gwt.user.client.Window.getClientWidth() - 820,  // 800px width + 20px margin
            20  // 20px from top
        );
    }
    
    /**
     * Hide the dialog
     */
    public void hide() {
        dialog.hide();
    }
    
    /**
     * Check if dialog is currently showing
     */
    public boolean isShowing() {
        return dialog.isShowing();
    }
    
    /**
     * Refresh the markdown content
     */
    public void refresh() {
        textArea.setText(generateMarkdownContent());
    }
    
    /**
     * Copy content to clipboard using native browser API
     */
    private static native boolean copyToClipboard() /*-{
        return $doc.execCommand('copy');
    }-*/;
    
    /**
     * Generate markdown debug content
     */
    private String generateMarkdownContent() {
        StringBuilder md = new StringBuilder();
        md.append("# Stock Flow Tables - Markdown Debug View\n\n");
        
        appendPrioritySystemInfo(md);
        appendCircuitMatrixInfo(md);
        appendStockLookupResults(md);
        
        java.util.Set<TableElm> relatedTables = findRelatedTables();
        appendRelatedTablesInfo(md, relatedTables);
        appendMasterStockInfo(md, relatedTables);
        appendRegistryInfo(md);
        
        return md.toString();
    }
    
    /**
     * Append priority system information
     */
    private void appendPrioritySystemInfo(StringBuilder md) {
        md.append("**Priority System:**\n");
        if (sim != null) {
            boolean weighted = sim.useWeightedPriority;
            md.append("- Weighted Priority by Type: ")
              .append(weighted ? "**ENABLED** ✓" : "DISABLED").append("\n");
            if (weighted) {
                md.append("  - Asset/Equity columns get +10 priority boost\n");
                md.append("  - Liability columns use base priority\n");
            } else {
                md.append("  - All columns use base priority (1-9)\n");
            }
        }
        md.append("\n");
    }
    
    /**
     * Append circuit matrix information
     */
    private void appendCircuitMatrixInfo(StringBuilder md) {
        md.append("**Circuit Matrix Info:**\n");
        if (sim != null) {
            md.append("- Matrix size: ").append(sim.circuitMatrixSize)
              .append(" × ").append(sim.circuitMatrixSize).append("\n");
            md.append("- Nodes: ")
              .append(sim.nodeList != null ? sim.nodeList.size() : 0)
              .append(" (including ground)\n");
            md.append("- Voltage sources: ").append(sim.voltageSourceCount).append("\n");
        } else {
            md.append("- *(Simulator not available)*\n");
        }
        md.append("\n");
    }
    /**
     * Append stock lookup results
     */
    private void appendStockLookupResults(StringBuilder md) {
        md.append("**Current table:** ").append(sourceTable.getTableTitle())
          .append(" (Object ID: ").append(System.identityHashCode(sourceTable))
          .append(")\n\n");
        
        md.append("**Stock lookup results:**\n");
        for (int col = 0; col < sourceTable.getCols(); col++) {
            String stockName = sourceTable.getColumnHeader(col);
            md.append("- Column ").append(col).append(": '").append(stockName).append("'");
            
            if (isALEColumn(col)) {
                md.append(" → (A-L-E computed column, skipped)\n");
                continue;
            }
            
            if (stockName != null && !stockName.trim().isEmpty()) {
                java.util.List<TableElm> tables = StockFlowRegistry.getTablesForStock(stockName);
                md.append(" → ").append(tables.size()).append(" table(s): ");
                for (TableElm t : tables) {
                    md.append("[").append(t.getTableTitle()).append(" #")
                      .append(System.identityHashCode(t)).append("] ");
                }
            } else {
                md.append(" → (empty/null, skipped)");
            }
            md.append("\n");
        }
        md.append("\n");
    }
    
    /**
     * Check if column is A-L-E
     */
    private boolean isALEColumn(int col) {
        return col == sourceTable.getCols() - 1 && sourceTable.getCols() >= 4;
    }
    
    /**
     * Find all tables related to source table via shared stocks
     */
    private java.util.Set<TableElm> findRelatedTables() {
        java.util.Set<TableElm> relatedTables = new java.util.HashSet<TableElm>();
        relatedTables.add(sourceTable);
        
        for (int col = 0; col < sourceTable.getCols(); col++) {
            String stockName = sourceTable.getColumnHeader(col);
            if (isALEColumn(col) || stockName == null || stockName.trim().isEmpty()) {
                continue;
            }
            
            java.util.List<TableElm> tables = StockFlowRegistry.getTablesForStock(stockName);
            relatedTables.addAll(tables);
        }
        
        return relatedTables;
    }
    
    /**
     * Append information about related tables
     */
    private void appendRelatedTablesInfo(StringBuilder md, java.util.Set<TableElm> relatedTables) {
        md.append("## Tables Sharing Stocks: ").append(relatedTables.size()).append("\n\n");
        
        for (TableElm table : relatedTables) {
            appendTableInfo(md, table);
        }
    }
    
    /**
     * Append detailed information about a single table
     */
    private void appendTableInfo(StringBuilder md, TableElm table) {
            // Calculate column widths for alignment
            int[] colWidths = calculateColumnWidths(table);
            
            // Table header with effective priority for master columns
            md.append("| ").append(padRight("Flows↓/Stock Vars →", colWidths[0])).append(" ");
            for (int col = 0; col < table.getCols(); col++) {
                String colHeader = table.getColumnHeader(col);
                
                // Check if this table is master for this column
                boolean isALEColumn = (col == table.getCols() - 1 && table.getCols() >= 4);
                if (!isALEColumn && colHeader != null && !colHeader.trim().isEmpty()) {
                    boolean isMaster = ComputedValues.isMasterTable(colHeader.trim(), table);
                    if (isMaster) {
                        // Calculate effective priority for this column
                        int basePriority = table.getPriority();
                        int effectivePriority = basePriority;
                        
                        if (sim != null && sim.useWeightedPriority) {
                            TableColumn.ColumnType colType = table.getColumnType(col);
                            if (colType == TableColumn.ColumnType.ASSET || 
                                colType == TableColumn.ColumnType.EQUITY) {
                                effectivePriority = basePriority + 10;
                            }
                        }
                        
                        // Add effective priority in brackets if master
                        colHeader = colHeader + " [" + effectivePriority + "]";
                    }
                }
                
                md.append("| ").append(padRight(colHeader, colWidths[col + 1])).append(" ");
            }
            md.append("|\n");
            
            // Column types row
            md.append("| ").append(padRight("*Type*", colWidths[0])).append(" ");
            for (int col = 0; col < table.getCols(); col++) {
                String colType = table.getColumnTypeName(col);
                md.append("| ").append(padRight("*" + colType + "*", colWidths[col + 1])).append(" ");
            }
            md.append("|\n");
            
            // Separator
            md.append("|");
            for (int col = 0; col <= table.getCols(); col++) {
                md.append(repeat("-", colWidths[col] + 2)).append("|");
            }
            md.append("\n");
            
            // Initial values row (if enabled) - shown first, right after header
            if (table.showInitialValues) {
                md.append("| ").append(padRight("Initial", colWidths[0])).append(" ");
                for (int col = 0; col < table.getCols(); col++) {
                    double initValue = table.getInitialValue(col);
                    // Use CircuitElm.getUnitText for consistent formatting with table display
                    String valueStr = CircuitElm.getUnitText(initValue, table.tableUnits);
                    md.append("| ").append(padRight(valueStr, colWidths[col + 1])).append(" ");
                }
                md.append("|\n");
            }
            
            // Rows
            for (int row = 0; row < table.getRows(); row++) {
                md.append("| ").append(padRight(table.getRowDescription(row), colWidths[0])).append(" ");
                for (int col = 0; col < table.getCols(); col++) {
                    String cellContent;
                    
                    // Check if this is an A-L-E column
                    boolean isALEColumn = (col == table.getCols() - 1 && table.getCols() >= 4);
                    
                    if (isALEColumn) {
                        // Generate A-L-E equation for this row
                        String aleEquation = calculateALECellEquation(table, row);
                        if (aleEquation != null && !aleEquation.isEmpty()) {
                            cellContent = "`" + aleEquation + "`";
                        } else {
                            cellContent = "";
                        }
                    } else {
                        // Regular cell - use stored equation
                        String equation = table.getCellEquation(row, col);
                        if (equation == null || equation.trim().isEmpty()) {
                            cellContent = "";
                        } else {
                            cellContent = "`" + equation + "`";
                        }
                    }
                    
                    md.append("| ").append(padRight(cellContent, colWidths[col + 1])).append(" ");
                }
                md.append("|\n");
            }
            md.append("\n");
            
            // Add non-zero flow/stock pairs for this table
            md.append("#### Non-Zero Flow/Stock Pairs\n\n");
            boolean foundNonZero = false;
            for (int row = 0; row < table.getRows(); row++) {
                String flowDesc = table.getRowDescription(row);
                if (flowDesc == null || flowDesc.trim().isEmpty()) {
                    flowDesc = "Flow" + row;
                }
                
                for (int col = 0; col < table.getCols(); col++) {
                    // Check if this is an A-L-E column
                    boolean isALEColumn = (col == table.getCols() - 1 && table.getCols() >= 4);
                    
                    String equation;
                    if (isALEColumn) {
                        // Generate A-L-E equation for this row
                        equation = calculateALECellEquation(table, row);
                    } else {
                        // Regular cell - use stored equation
                        equation = table.getCellEquation(row, col);
                    }
                    
                    if (equation != null && !equation.trim().isEmpty() && !equation.trim().equals("0")) {
                        String stockName = table.getColumnHeader(col);
                        md.append("- **").append(flowDesc).append("** → **").append(stockName)
                          .append("**: `").append(equation).append("`\n");
                        foundNonZero = true;
                    }
                }
            }
            if (!foundNonZero) {
                md.append("- *(No non-zero equations)*\n");
            }
            md.append("\n");
    }
    
    /**
     * Append master stock information showing which table is master for each stock
     */
    private void appendMasterStockInfo(StringBuilder md, java.util.Set<TableElm> relatedTables) {
        // Add master stock/column information
        md.append("---\n\n");
        md.append("## Master Stock Columns\n\n");
        md.append("This shows which table is the **master** (electrical driver) for each stock column:\n\n");
        
        // Collect all unique stock names from related tables
        java.util.Set<String> allStocks = new java.util.HashSet<String>();
        for (TableElm table : relatedTables) {
            for (int col = 0; col < table.getCols(); col++) {
                String stockName = table.getColumnHeader(col);
                // Skip A-L-E computed columns - they are not real stocks
                boolean isALEColumn = (col == table.getCols() - 1 && table.getCols() >= 4);
                if (!isALEColumn && stockName != null && !stockName.trim().isEmpty()) {
                    allStocks.add(stockName);
                }
            }
        }
        
        // List each stock and its master
        if (allStocks.isEmpty()) {
            md.append("- *(No stocks found)*\n\n");
        } else {
            for (String stockName : allStocks) {
                md.append("- **").append(stockName).append("**: ");
                
                // Find master table for this stock
                TableElm masterTable = null;
                for (TableElm table : relatedTables) {
                    if (ComputedValues.isMasterTable(stockName, table)) {
                        masterTable = table;
                        break;
                    }
                }
                
                if (masterTable != null) {
                    int basePriority = masterTable.getPriority();
                    
                    // Find the column in the master table to get its type
                    int masterCol = -1;
                    for (int col = 0; col < masterTable.getCols(); col++) {
                        if (stockName.equals(masterTable.getColumnHeader(col))) {
                            masterCol = col;
                            break;
                        }
                    }
                    
                    // Calculate effective priority if weighted priority is enabled
                    int effectivePriority = basePriority;
                    String priorityNote = "";
                    if (sim != null && sim.useWeightedPriority && masterCol >= 0) {
                        TableColumn.ColumnType colType = masterTable.getColumnType(masterCol);
                        if (colType == TableColumn.ColumnType.ASSET || 
                            colType == TableColumn.ColumnType.EQUITY) {
                            effectivePriority = basePriority + 10;
                            priorityNote = " [Effective: " + effectivePriority + " due to " + colType + "]";
                        }
                    }
                    
                    md.append("✓ **").append(masterTable.getTableTitle())
                      .append("** (Priority: ").append(basePriority).append(priorityNote)
                      .append(", Object ID: #").append(System.identityHashCode(masterTable))
                      .append(") - *computes and drives voltage*");
                } else {
                    md.append("⚠ **NO MASTER** - *no table registered as master for this stock*");
                }
                
                // List all tables that reference this stock (with priorities and types)
                java.util.List<TableElm> referencingTables = StockFlowRegistry.getTablesForStock(stockName);
                if (referencingTables.size() > 1) {
                    md.append("\n  - Also referenced by: ");
                    boolean first = true;
                    for (TableElm table : referencingTables) {
                        if (table != masterTable) {
                            if (!first) md.append(", ");
                            
                            int basePriority = table.getPriority();
                            
                            // Find the column in this table to get its type
                            int col = -1;
                            for (int c = 0; c < table.getCols(); c++) {
                                if (stockName.equals(table.getColumnHeader(c))) {
                                    col = c;
                                    break;
                                }
                            }
                            
                            // Calculate effective priority if weighted priority is enabled
                            String priorityStr = String.valueOf(basePriority);
                            if (sim != null && sim.useWeightedPriority && col >= 0) {
                                TableColumn.ColumnType colType = table.getColumnType(col);
                                if (colType == TableColumn.ColumnType.ASSET || 
                                    colType == TableColumn.ColumnType.EQUITY) {
                                    int effectivePriority = basePriority + 10;
                                    priorityStr = basePriority + "→" + effectivePriority + " (" + colType + ")";
                                }
                            }
                            
                            md.append(table.getTableTitle())
                              .append(" (Priority: ").append(priorityStr).append(")");
                            first = false;
                        }
                    }
                }
                md.append("\n");
            }
            md.append("\n");
        }
    }
    
    /**
     * Append registry diagnostic information
     */
    private void appendRegistryInfo(StringBuilder md) {
        // Add registry information
        md.append("---\n\n");
        md.append("## Stock Registry Information\n\n");
        md.append("```\n");
        md.append(StockFlowRegistry.getDiagnosticInfo());
        md.append("```\n");
    }
    
    /**
     * Calculate A-L-E equation for a specific table and row
     * Returns: sum(Assets) - sum(Liabilities) - Equity
     */
    private String calculateALECellEquation(TableElm table, int row) {
        StringBuilder eq = new StringBuilder();
        boolean first = true;
        
        // Skip the last column (A-L-E itself)
        int numCols = table.getCols() - 1;
        
        // Add asset terms (positive)
        for (int col = 0; col < numCols; col++) {
            if (table.getColumnType(col) == TableColumn.ColumnType.ASSET) {
                String cell = table.getCellEquation(row, col);
                if (cell != null && !cell.trim().isEmpty()) {
                    if (!first) eq.append(" + ");
                    eq.append(wrapIfComplex(cell));
                    first = false;
                }
            }
        }
        
        // Subtract liability terms
        for (int col = 0; col < numCols; col++) {
            if (table.getColumnType(col) == TableColumn.ColumnType.LIABILITY) {
                String cell = table.getCellEquation(row, col);
                if (cell != null && !cell.trim().isEmpty()) {
                    eq.append(" - ").append(wrapIfComplex(cell));
                }
            }
        }
        
        // Subtract equity term
        for (int col = 0; col < numCols; col++) {
            if (table.getColumnType(col) == TableColumn.ColumnType.EQUITY) {
                String cell = table.getCellEquation(row, col);
                if (cell != null && !cell.trim().isEmpty()) {
                    eq.append(" - ").append(wrapIfComplex(cell));
                }
            }
        }
        
        return eq.length() > 0 ? eq.toString() : "0";
    }
    
    /**
     * Wrap expression in parentheses if it contains operators
     */
    private String wrapIfComplex(String expr) {
        if (expr.contains("+") || expr.contains("-") || 
            expr.contains("*") || expr.contains("/")) {
            return "(" + expr + ")";
        }
        return expr;
    }
    
    /**
     * Calculate the maximum width needed for each column in the table
     */
    private int[] calculateColumnWidths(TableElm table) {
        int[] widths = new int[table.getCols() + 1];
        
        // Initialize with header widths (minimum 8 characters for empty headers)
        widths[0] = "Flows↓/Stock Vars →".length();
        for (int col = 0; col < table.getCols(); col++) {
            String header = table.getColumnHeader(col);
            if (header == null || header.trim().isEmpty()) {
                widths[col + 1] = 8; // Minimum width for empty columns
            } else {
                widths[col + 1] = header.length();
            }
        }
        
        // Check all row data
        for (int row = 0; row < table.getRows(); row++) {
            // Row description
            String rowDesc = table.getRowDescription(row);
            if (rowDesc != null && rowDesc.length() > widths[0]) {
                widths[0] = rowDesc.length();
            }
            
            // Cell equations (include backticks in width calculation)
            for (int col = 0; col < table.getCols(); col++) {
                String equation = table.getCellEquation(row, col);
                if (equation != null && !equation.trim().isEmpty()) {
                    int cellWidth = equation.length() + 2; // +2 for backticks
                    if (cellWidth > widths[col + 1]) {
                        widths[col + 1] = cellWidth;
                    }
                }
            }
        }
        
        return widths;
    }
    
    /**
     * Pad a string to the right with spaces
     */
    private String padRight(String str, int width) {
        if (str == null) str = "";
        if (str.length() >= width) return str;
        
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
    
    /**
     * Repeat a character n times
     */
    private String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    /**
     * Add CSS for resizable panels
     */
    private native void addResizableStyles() /*-{
        // Add resize handle CSS only once
        if (!$doc.getElementById('resizable-panel-style')) {
            var style = $doc.createElement('style');
            style.id = 'resizable-panel-style';
            style.textContent = 
                '.resizable-panel {' +
                '  resize: both !important;' +
                '  overflow: auto !important;' +
                '  min-width: 300px !important;' +
                '  min-height: 200px !important;' +
                '}';
            $doc.head.appendChild(style);
        }
    }-*/;
    
    /**
     * Make a panel resizable
     */
    private native void makeResizable(com.google.gwt.dom.client.Element element) /*-{
        element.classList.add('resizable-panel');
    }-*/;
}
