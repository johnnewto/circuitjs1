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
        m.rowDescColWidth = table.collapsedMode ? 0 : m.cellWidthPixels;
        
        int titleHeight = 20;
        int sourceRowHeight = table.cellHeight + table.cellSpacing;
        int typeRowHeight = table.collapsedMode ? 0 : (table.cellHeight + table.cellSpacing);
        int headerRowHeight = table.cellHeight + table.cellSpacing;
        int initialRowHeight = (table.showInitialValues && !table.collapsedMode) ? 
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
        
        drawSourceTableRow(g, currentY);
        currentY += table.cellHeight + table.cellSpacing;
        
        if (!table.collapsedMode) {
            drawColumnTypeRow(g, currentY);
            currentY += table.cellHeight + table.cellSpacing;
        }
        
        drawColumnHeaders(g, currentY);
        currentY += table.cellHeight + table.cellSpacing;
        
        if (table.showInitialValues && !table.collapsedMode) {
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

}
