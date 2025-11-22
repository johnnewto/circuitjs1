/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * Custom renderer for CurrentTransactionsMatrixElm.
 * Extends TableRenderer to add grouped source table visualization:
 * - Source table row: Shows which table each column comes from (merged cells)
 * - Type row: Shows column types (A/L/E) merged by table and type
 */
public class CurrentTransactionsMatrixRenderer extends TableRenderer {
    private final CurrentTransactionsMatrixElm matrixElm;
    
    public CurrentTransactionsMatrixRenderer(CurrentTransactionsMatrixElm table) {
        super(table);
        this.matrixElm = table;
    }
    
    @Override
    public void draw(Graphics g) {
        updateCachedValuesIfNeeded();
        
        LayoutMetrics layout = calculateLayout();
        drawBackground(g, layout);
        drawComponentsInOrder(g, layout);
        drawPins(g);
    }
    
    /** Update cached values if enough time has passed */
    private void updateCachedValuesIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS || cachedCellValues == null) {
            updateCachedValues();
            lastUpdateTime = currentTime;
        }
    }
    
    /** Calculate all layout dimensions */
    private LayoutMetrics calculateLayout() {
        LayoutMetrics m = new LayoutMetrics();
        m.tableX = table.getTableX();
        m.tableY = table.getTableY();
        m.cellWidthPixels = table.getCellWidthPixels();
        m.rowDescColWidth = matrixElm.isCompactMode() ? (m.cellWidthPixels * 2) : 
            (table.collapsedMode ? 0 : m.cellWidthPixels);
        
        int titleHeight = 20;
        int sourceRowHeight = table.cellHeight + table.cellSpacing;
        int typeRowHeight = (table.collapsedMode || matrixElm.isCompactMode()) ? 0 : (table.cellHeight + table.cellSpacing);
        int headerRowHeight = matrixElm.isCompactMode() ? 0 : (table.cellHeight + table.cellSpacing);
        int initialRowHeight = (table.showInitialValues && (!table.collapsedMode || matrixElm.isCompactMode())) ? 
            (table.cellHeight + table.cellSpacing) : 0;
        int dataRowsHeight = table.collapsedMode ? 0 : 
            (table.rows * (table.cellHeight + table.cellSpacing));
        int computedRowHeight = table.cellHeight + table.cellSpacing;
        
        m.tableWidth = m.rowDescColWidth + table.cellSpacing + 
            table.cols * m.cellWidthPixels + (table.cols + 1) * table.cellSpacing;
        m.tableHeight = titleHeight + sourceRowHeight + typeRowHeight + 
            headerRowHeight + initialRowHeight + dataRowsHeight + computedRowHeight;
        
        return m;
    }
    
    /** Draw background and border */
    private void drawBackground(Graphics g, LayoutMetrics m) {
        Color bgColor = CirSim.theSim.printableCheckItem.getState() ? 
            new Color(230, 230, 230) : new Color(40, 40, 40);
        g.setColor(bgColor);
        g.fillRect(m.tableX + 1, m.tableY + 1, m.tableWidth - 2, m.tableHeight - 2);
        
        g.setColor(table.nonConverged ? Color.blue : CircuitElm.lightGrayColor);
        g.drawRect(m.tableX, m.tableY, m.tableWidth, m.tableHeight);
    }
    
    /** Draw all table components in order, return final Y position */
    private int drawComponentsInOrder(Graphics g, LayoutMetrics m) {
        int currentY = 10;
        
        drawTitle(g, currentY);
        currentY += 10;
        
        // Skip source table row in compact mode (table names shown as headers instead)
        if (!matrixElm.isCompactMode()) {
            drawSourceTableRow(g, currentY);
            currentY += table.cellHeight + table.cellSpacing;
        }
        
        // Skip type row in compact mode
        if (!table.collapsedMode && !matrixElm.isCompactMode()) {
            drawColumnTypeRow(g, currentY);
            currentY += table.cellHeight + table.cellSpacing;
        }
        
        // Skip header row in compact mode
        if (!matrixElm.isCompactMode()) {
            drawColumnHeaders(g, currentY);
            currentY += table.cellHeight + table.cellSpacing;
        } else {
            // In compact mode, show source table names as headers
            drawTableNamesAsHeaders(g, currentY);
            currentY += table.cellHeight + table.cellSpacing;
        }
        
        // Show initial values in compact mode or when explicitly enabled in expanded mode
        if (table.showInitialValues && (!table.collapsedMode || matrixElm.isCompactMode())) {
            drawInitialConditionsRow(g, currentY);
            currentY += table.cellHeight + table.cellSpacing;
        }
        
        if (!table.collapsedMode) {
            drawTableCells(g, currentY);
            currentY += table.rows * (table.cellHeight + table.cellSpacing);
        }
        
        drawSumRow(g, currentY);
        currentY += table.cellHeight + table.cellSpacing;
        
        return currentY;
    }
    
    /** Layout metrics helper class */
    private static class LayoutMetrics {
        int tableX, tableY, tableWidth, tableHeight;
        int cellWidthPixels, rowDescColWidth;
    }
    
    /**
     * Draw source table row with merged cells for adjacent columns from same table.
     */
    private void drawSourceTableRow(Graphics g, int offsetY) {
        String[] sourceNames = matrixElm.getSourceTableNames();
        if (sourceNames == null || sourceNames.length == 0) {
            return;
        }
        
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = table.collapsedMode ? 0 : cellWidthPixels;
        int rowY = tableY + offsetY;
        
        setupHeaderFont(g);
        
        // Draw empty row description
        if (!table.collapsedMode) {
            table.drawCenteredText(g, "", tableX + table.cellSpacing + rowDescColWidth/2, 
                rowY + table.cellHeight/2, true);
        }
        
        // Draw merged source names
        MergedCellIterator iter = new MergedCellIterator(sourceNames, sourceNames);
        while (iter.hasNext()) {
            MergedCell cell = iter.next();
            int centerX = calculateMergedCenterX(tableX, rowDescColWidth, cellWidthPixels, 
                cell.startCol, cell.endCol);
            g.setColor(CircuitElm.whiteColor);
            table.drawCenteredText(g, cell.value, centerX, rowY + table.cellHeight/2, true);
        }
        
        // Draw grid lines with double lines where source changes
        drawGridLinesWithMerging(g, offsetY, sourceNames, sourceNames, true);
    }
    
    /**
     * Draw source table names as column headers in compact mode.
     * Shows one column per source table.
     */
    private void drawTableNamesAsHeaders(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 2; // Wider in compact mode to match cell bodies
        int rowY = tableY + offsetY;
        
        // CirSim.console("[CTM-Renderer] drawTableNamesAsHeaders: tableX=" + tableX + ", tableY=" + tableY + 
        //     ", cellWidthPixels=" + cellWidthPixels + ", rowDescColWidth=" + rowDescColWidth + 
        //     ", cols=" + table.cols + ", cellSpacing=" + table.cellSpacing);
        
        setupHeaderFont(g);
        
        // Draw empty row description
        if (!table.collapsedMode) {
            table.drawCenteredText(g, "", tableX + table.cellSpacing + rowDescColWidth/2, 
                rowY + table.cellHeight/2, true);
        }
        
        // Draw table names as column headers
        for (int col = 0; col < table.cols; col++) {
            String tableName = matrixElm.getSourceTableName(col);
            String header = (tableName != null && !tableName.isEmpty()) ? tableName : matrixElm.getOutputName(col);
            
            int centerX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                col * (cellWidthPixels + table.cellSpacing) + cellWidthPixels/2;
            
            g.setColor(CircuitElm.whiteColor);
            table.drawCenteredText(g, header, centerX, rowY + table.cellHeight/2, true);
        }
        
        // Draw simple grid lines (no merging in compact mode)
        g.setColor(CircuitElm.lightGrayColor);
        int tableWidth = rowDescColWidth + table.cellSpacing * 2 + 
            table.cols * (cellWidthPixels + table.cellSpacing);
        
        // Horizontal lines
        g.drawLine(tableX, rowY, tableX + tableWidth, rowY);
        g.drawLine(tableX, rowY + table.cellHeight, tableX + tableWidth, rowY + table.cellHeight);
        
        // Left edge
        g.drawLine(tableX, rowY, tableX, rowY + table.cellHeight);
        
        // After description column
        if (!table.collapsedMode) {
            int x = tableX + table.cellSpacing + rowDescColWidth;
            g.drawLine(x, rowY, x, rowY + table.cellHeight);
        }
        
        // Vertical lines between columns
        for (int col = 0; col <= table.cols; col++) {
            int x = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                col * (cellWidthPixels + table.cellSpacing);
            g.drawLine(x, rowY, x, rowY + table.cellHeight);
        }
    }
    
    /** Setup header font for row labels */
    private void setupHeaderFont(Graphics g) {
        Font headerFont = new Font("SansSerif", Font.BOLD, 11);
        g.setFont(headerFont);
        g.setLetterSpacing("0.5px");
        g.setColor(CircuitElm.whiteColor);
    }
    
    /** Calculate center X position for merged cells */
    private int calculateMergedCenterX(int tableX, int rowDescColWidth, int cellWidthPixels, 
                                        int startCol, int endCol) {
        int startCellX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
            startCol * (cellWidthPixels + table.cellSpacing);
        int endCellX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
            endCol * (cellWidthPixels + table.cellSpacing);
        return startCellX + (endCellX - startCellX + cellWidthPixels) / 2;
    }
    
    /**
     * Helper class for iterating over merged cell groups.
     * Merges adjacent columns where both group arrays have equal values.
     */
    private class MergedCellIterator {
        private final String[] groupBy1;
        private final String[] groupBy2;
        private int currentCol = 0;
        
        MergedCellIterator(String[] groupBy1, String[] groupBy2) {
            this.groupBy1 = groupBy1;
            this.groupBy2 = groupBy2;
        }
        
        boolean hasNext() {
            return currentCol < table.cols;
        }
        
        MergedCell next() {
            String value1 = safeGet(groupBy1, currentCol);
            String value2 = groupBy2 != null ? safeGet(groupBy2, currentCol) : null;
            
            int startCol = currentCol;
            int endCol = currentCol;
            
            // Find consecutive columns with same values
            while (endCol + 1 < table.cols) {
                String nextValue1 = safeGet(groupBy1, endCol + 1);
                String nextValue2 = groupBy2 != null ? safeGet(groupBy2, endCol + 1) : null;
                
                if (value1.equals(nextValue1) && 
                    (groupBy2 == null || value2.equals(nextValue2))) {
                    endCol++;
                } else {
                    break;
                }
            }
            
            currentCol = endCol + 1;
            return new MergedCell(value1, startCol, endCol);
        }
        
        private String safeGet(String[] arr, int index) {
            if (arr == null || index >= arr.length) return "";
            String val = arr[index];
            return val != null ? val : "";
        }
    }
    
    /** Represents a merged cell group */
    private static class MergedCell {
        final String value;
        final int startCol;
        final int endCol;
        
        MergedCell(String value, int startCol, int endCol) {
            this.value = value;
            this.startCol = startCol;
            this.endCol = endCol;
        }
    }
    
    /** Draw grid lines with optional double lines where grouping changes */
    private void drawGridLinesWithMerging(Graphics g, int offsetY, String[] groupBy1, 
                                          String[] groupBy2, boolean useDoubleLine) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = table.collapsedMode ? 0 : cellWidthPixels;
        int rowY = tableY + offsetY;
        
        g.setColor(CircuitElm.lightGrayColor);
        int tableWidth = rowDescColWidth + table.cellSpacing * 2 + 
            table.cols * (cellWidthPixels + table.cellSpacing);
        
        // Horizontal lines
        g.drawLine(tableX, rowY, tableX + tableWidth, rowY);
        g.drawLine(tableX, rowY + table.cellHeight, tableX + tableWidth, rowY + table.cellHeight);
        
        // Left edge
        g.drawLine(tableX, rowY, tableX, rowY + table.cellHeight);
        
        // After description column
        if (!table.collapsedMode) {
            int x = tableX + table.cellSpacing + rowDescColWidth;
            g.drawLine(x, rowY, x, rowY + table.cellHeight);
        }
        
        // Vertical lines between columns
        for (int col = 0; col <= table.cols; col++) {
            int x = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                col * (cellWidthPixels + table.cellSpacing);
            
            if (col == table.cols) {
                // Right edge
                g.drawLine(x, rowY, x, rowY + table.cellHeight);
            } else if (col > 0 && shouldDrawVerticalLine(col, groupBy1, groupBy2)) {
                if (useDoubleLine) {
                    // Double line where grouping changes
                    g.drawLine(x - 1, rowY, x - 1, rowY + table.cellHeight);
                    g.drawLine(x + 1, rowY, x + 1, rowY + table.cellHeight);
                } else {
                    g.drawLine(x, rowY, x, rowY + table.cellHeight);
                }
            }
        }
    }
    
    /** Check if vertical line should be drawn between columns */
    private boolean shouldDrawVerticalLine(int col, String[] groupBy1, String[] groupBy2) {
        String prev1 = safeGetString(groupBy1, col - 1);
        String curr1 = safeGetString(groupBy1, col);
        
        if (!prev1.equals(curr1)) return true;
        
        if (groupBy2 != null) {
            String prev2 = safeGetString(groupBy2, col - 1);
            String curr2 = safeGetString(groupBy2, col);
            if (!prev2.equals(curr2)) return true;
        }
        
        return false;
    }
    
    /** Safely get string from array */
    private String safeGetString(String[] arr, int index) {
        if (arr == null || index >= arr.length) return "";
        String val = arr[index];
        return val != null ? val : "";
    }
    
    /**
     * Draw column type row with merged cells by source table AND type.
     */
    @Override
    protected void drawColumnTypeRow(Graphics g, int offsetY) {
        String[] sourceNames = matrixElm.getSourceTableNames();
        if (sourceNames == null || sourceNames.length == 0) {
            super.drawColumnTypeRow(g, offsetY);
            return;
        }
        
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        int rowY = tableY + offsetY;
        
        setupHeaderFont(g);
        table.drawCenteredText(g, "", tableX + table.cellSpacing + rowDescColWidth/2, 
            rowY + table.cellHeight/2, true);
        
        // Build type names array
        String[] typeNames = new String[table.cols];
        for (int i = 0; i < table.cols; i++) {
            boolean isALE = (i == table.cols - 1 && table.cols >= 4);
            typeNames[i] = isALE ? "" : getColumnTypeName(table.getColumnType(i));
        }
        
        // Draw merged type cells (group by source AND type, but display type names)
        MergedCellIterator iter = new MergedCellIterator(sourceNames, typeNames);
        while (iter.hasNext()) {
            MergedCell cell = iter.next();
            int centerX = calculateMergedCenterX(tableX, rowDescColWidth, cellWidthPixels, 
                cell.startCol, cell.endCol);
            // Display the type name (value2), not the source name (value1)
            String displayValue = (cell.startCol < typeNames.length) ? typeNames[cell.startCol] : "";
            table.drawCenteredText(g, displayValue, centerX, rowY + table.cellHeight/2, true);
        }
        
        // Draw grid lines (single lines where source OR type changes)
        drawGridLinesWithMerging(g, offsetY, sourceNames, typeNames, false);
    }
    
    /**
     * Override drawTableCells to filter cells in compact mode
     * In compact mode, only show non-blank equations for Asset/Equity columns
     */
    @Override
    protected void drawTableCells(Graphics g, int offsetY) {
        if (!matrixElm.isCompactMode()) {
            // Normal mode: use parent implementation
            super.drawTableCells(g, offsetY);
            return;
        }
        
        // Compact mode: filter cells to show only non-blank Asset/Equity equations
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 2; // Wider in compact mode
        
        CirSim.console("[CTM-Renderer] drawTableCells (compact): tableX=" + tableX + ", tableY=" + tableY + 
            ", cellWidthPixels=" + cellWidthPixels + ", rowDescColWidth=" + rowDescColWidth + 
            ", rows=" + table.rows + ", cols=" + table.cols);
        
        // Create fonts locally
        Font headerFont = new Font("SansSerif", Font.BOLD, 11);
        Font cellFont = new Font("SansSerif", Font.BOLD, 11);
        String letterSpacing = "0.5px";
        
        int baseY = offsetY;
        
        for (int row = 0; row < table.rows; row++) {
            int cellY = tableY + baseY + row * (table.cellHeight + table.cellSpacing);
            
            // CirSim.console("[CTM-Renderer]   row " + row + ": cellY=" + cellY + ", rowDesc='" + 
            //     (table.rowDescriptions != null && row < table.rowDescriptions.length ? table.rowDescriptions[row] : "N/A") + "'");
            
            // Draw row description with header font
            g.setFont(headerFont);
            g.setLetterSpacing(letterSpacing);
            g.setColor(CircuitElm.whiteColor);
            String rowDesc = (table.rowDescriptions != null && row < table.rowDescriptions.length) ?
                            table.rowDescriptions[row] : "Row" + (row + 1);
            
            table.drawCenteredText(g, rowDesc, tableX + table.cellSpacing + rowDescColWidth/2, 
                cellY + table.cellHeight/2, true);
            
            // Use cell font for cell values
            g.setFont(cellFont);
            
            // Draw data cells - all cells to maintain column alignment
            for (int col = 0; col < table.cols; col++) {
                int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                    col * (cellWidthPixels + table.cellSpacing);
                
                // Get equation
                String equation = (table.cellEquations != null && row < table.cellEquations.length && 
                    col < table.cellEquations[row].length) ? table.cellEquations[row][col] : "";
                
                // CirSim.console("[CTM-Renderer]     col " + col + ": cellX=" + cellX + 
                //     ", centerX=" + (cellX + cellWidthPixels/2) + ", equation='" + 
                //     (equation != null && !equation.isEmpty() ? equation : "BLANK") + "'");
                
                // Display the equation (empty string if blank) to maintain alignment
                if (equation != null && !equation.trim().isEmpty()) {
                    g.setColor(CircuitElm.whiteColor);
                    table.drawCenteredText(g, equation, cellX + cellWidthPixels/2, 
                        cellY + table.cellHeight/2, true);
                }
                // Empty cells are intentionally not drawn, but their space is preserved
            }
            
            // Draw grid lines for this row
            g.setColor(CircuitElm.lightGrayColor);
            int tableWidth = rowDescColWidth + table.cellSpacing * 2 + table.cols * (cellWidthPixels + table.cellSpacing);
            
            // CirSim.console("[CTM-Renderer]     Grid lines: tableWidth=" + tableWidth + ", drawing " + (table.cols + 1) + " vertical lines");
            
            // Horizontal lines (top and bottom of row)
            g.drawLine(tableX, cellY, tableX + tableWidth, cellY);
            g.drawLine(tableX, cellY + table.cellHeight, tableX + tableWidth, cellY + table.cellHeight);
            
            // Vertical lines
            // Left edge
            g.drawLine(tableX, cellY, tableX, cellY + table.cellHeight);
            // After description column
            int x = tableX + table.cellSpacing + rowDescColWidth;
            g.drawLine(x, cellY, x, cellY + table.cellHeight);
            
            // Between and after data columns
            for (int col = 0; col <= table.cols; col++) {
                x = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
                // CirSim.console("[CTM-Renderer]       vertical line at col " + col + ": x=" + x);
                g.drawLine(x, cellY, x, cellY + table.cellHeight);
            }
        }
    }
    
    /**
     * Override drawSumRow to handle compact mode's wider row description column
     */
    @Override
    protected void drawSumRow(Graphics g, int offsetY) {
        if (!matrixElm.isCompactMode()) {
            // Normal mode: use parent implementation
            super.drawSumRow(g, offsetY);
            return;
        }
        
        // Compact mode: use wider row description column
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 2; // Wider in compact mode
        
        int sumRowY = tableY + offsetY;
        
        // Use cell font for values
        Font cellFont = new Font("SansSerif", 0, 11); // 0 = Font.PLAIN
        g.setFont(cellFont);
        
        for (int col = 0; col < table.cols; col++) {
            int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            
            // Get the computed value (from our overridden getComputedValueForDisplay)
            double computedValue = table.getComputedValueForDisplay(col);
            
            // Draw value with appropriate color
            Color textColor = computedValue > 0 ? Color.green : (computedValue < 0 ? Color.red : CircuitElm.whiteColor);
            g.setColor(textColor);
            String sumText = CircuitElm.getUnitText(computedValue, table.tableUnits);
            table.drawCenteredText(g, sumText, cellX + cellWidthPixels/2, sumRowY + table.cellHeight/2, false);
        }
        
        // Draw grid lines
        g.setColor(CircuitElm.lightGrayColor);
        
        // Horizontal lines
        g.drawLine(tableX, sumRowY, tableX + rowDescColWidth + table.cellSpacing * 2 + 
            table.cols * (cellWidthPixels + table.cellSpacing), sumRowY);
        g.drawLine(tableX, sumRowY + table.cellHeight, tableX + rowDescColWidth + table.cellSpacing * 2 + 
            table.cols * (cellWidthPixels + table.cellSpacing), sumRowY + table.cellHeight);
        
        // Vertical lines
        g.drawLine(tableX, sumRowY, tableX, sumRowY + table.cellHeight); // Left edge
        g.drawLine(tableX + table.cellSpacing + rowDescColWidth, sumRowY, 
            tableX + table.cellSpacing + rowDescColWidth, sumRowY + table.cellHeight); // After desc column
        
        for (int col = 0; col <= table.cols; col++) {
            int x = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            g.drawLine(x, sumRowY, x, sumRowY + table.cellHeight);
        }
    }

}

