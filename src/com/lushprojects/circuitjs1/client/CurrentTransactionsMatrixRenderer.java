/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * Custom renderer for CurrentTransactionsMatrixElm
 * Adds a row showing source table names grouped by table
 */
public class CurrentTransactionsMatrixRenderer extends TableRenderer {
    private final CurrentTransactionsMatrixElm matrixElm;
    
    public CurrentTransactionsMatrixRenderer(CurrentTransactionsMatrixElm table) {
        super(table);
        this.matrixElm = table;
    }
    
    /**
     * Override draw to add source table row after type row
     */
    @Override
    public void draw(Graphics g) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = table.collapsedMode ? 0 : cellWidthPixels;
        
        // Calculate the actual table height by accumulating all components
        int titleHeight = 10 + 10; // Title offset + space after
        int sourceTableRowHeight = table.cellHeight + table.cellSpacing; // NEW: Source table row
        int typeRowHeight = table.collapsedMode ? 0 : (table.cellHeight + table.cellSpacing);
        int headerRowHeight = table.cellHeight + table.cellSpacing;
        int initialRowHeight = (table.showInitialValues && !table.collapsedMode) ? (table.cellHeight + table.cellSpacing) : 0;
        int dataRowsHeight = table.collapsedMode ? 0 : (table.rows * (table.cellHeight + table.cellSpacing));
        int computedRowHeight = table.cellHeight + table.cellSpacing;
        
        int tableWidth = rowDescColWidth + table.cellSpacing + table.cols * cellWidthPixels + (table.cols + 1) * table.cellSpacing;
        int tableHeight = titleHeight + sourceTableRowHeight + typeRowHeight + headerRowHeight + initialRowHeight + dataRowsHeight + computedRowHeight;

        // IMPORTANT: Update cached values before drawing (needed for sum row to update)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS || cachedCellValues == null) {
            updateCachedValues();
            lastUpdateTime = currentTime;
        }

        // Draw light gray background for entire table
        Color bgColor = CirSim.theSim.printableCheckItem.getState() ? 
            new Color(230, 230, 230) : new Color(40, 40, 40);
        g.setColor(bgColor);
        g.fillRect(tableX + 1, tableY + 1, tableWidth - 2, tableHeight - 2);
        
        // Draw table border
        g.setColor(table.nonConverged ? Color.blue : CircuitElm.lightGrayColor);
        g.drawRect(tableX, tableY, tableWidth, tableHeight);

        // Draw components in order with consistent positioning
        int currentY = 10; // Start position after table border
        
        // 1. Draw title
        drawTitle(g, currentY);
        currentY += 10; // Space after title
        
        // 2. NEW: Draw source table row
        drawSourceTableRow(g, currentY);
        currentY += table.cellHeight + table.cellSpacing;
        
        // 3. Draw column type row (skip in collapsed mode)
        if (!table.collapsedMode) {
            drawColumnTypeRow(g, currentY);
            currentY += table.cellHeight + table.cellSpacing;
        }
        
        // 4. Draw column headers
        drawColumnHeaders(g, currentY);
        currentY += table.cellHeight + table.cellSpacing;
        
        // 5. Draw initial conditions row if enabled (skip in collapsed mode)
        if (table.showInitialValues && !table.collapsedMode) {
            drawInitialConditionsRow(g, currentY);
            currentY += table.cellHeight + table.cellSpacing;
        }

        // 6. Draw table cells with voltages (skip in collapsed mode)
        if (!table.collapsedMode) {
            drawTableCells(g, currentY);
            currentY += table.rows * (table.cellHeight + table.cellSpacing);
        }

        // 7. Draw computed row
        drawSumRow(g, currentY);
        currentY += table.cellHeight + table.cellSpacing;

        // 8. Draw chip pins and posts at the bottom
        drawPins(g);
    }
    
    /**
     * Draw source table row showing which table each column comes from
     * Merges adjacent cells from the same source table
     */
    private void drawSourceTableRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = table.collapsedMode ? 0 : cellWidthPixels;
        int sourceRowY = tableY + offsetY;
        
        // Get source table names
        String[] sourceNames = matrixElm.getSourceTableNames();
        if (sourceNames == null || sourceNames.length == 0) {
            // Fallback: draw empty row
            drawEmptySourceTableRow(g, offsetY);
            return;
        }
        
        // Draw row description cell (empty for source table row)
        Font headerFont = new Font("SansSerif", Font.BOLD, 11);
        g.setFont(headerFont);
        g.setLetterSpacing("0.5px");
        g.setColor(CircuitElm.whiteColor);
        
        if (!table.collapsedMode) {
            table.drawCenteredText(g, "", tableX + table.cellSpacing + rowDescColWidth/2, sourceRowY + table.cellHeight/2, true);
        }
        
        // Draw source table names - merge adjacent columns with same source
        int col = 0;
        while (col < table.cols) {
            String sourceName = (col < sourceNames.length) ? sourceNames[col] : "";
            if (sourceName == null) sourceName = "";
            
            int startCol = col;
            int endCol = col;
            
            // Count consecutive columns with same source table
            while (endCol + 1 < table.cols) {
                String nextSource = (endCol + 1 < sourceNames.length) ? sourceNames[endCol + 1] : "";
                if (nextSource == null) nextSource = "";
                
                if (sourceName.equals(nextSource)) {
                    endCol++;
                } else {
                    break;
                }
            }
            
            // Calculate the center position across merged cells
            int startCellX = tableX + rowDescColWidth + table.cellSpacing * 2 + startCol * (cellWidthPixels + table.cellSpacing);
            int endCellX = tableX + rowDescColWidth + table.cellSpacing * 2 + endCol * (cellWidthPixels + table.cellSpacing);
            int centerX = startCellX + (endCellX - startCellX + cellWidthPixels) / 2;
            
            // Draw the source table name centered across the merged cells
            g.setColor(CircuitElm.whiteColor);
            table.drawCenteredText(g, sourceName, centerX, sourceRowY + table.cellHeight/2, true);
            
            // Move to next group
            col = endCol + 1;
        }
        
        // Draw grid lines for this row (with merging)
        drawSourceTableRowGridLines(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, sourceNames);
    }
    
    /**
     * Draw grid lines for source table row with merged cells
     */
    private void drawSourceTableRowGridLines(Graphics g, int offsetY, int tableX, int rowDescColWidth, int cellWidthPixels, String[] sourceNames) {
        int tableY = table.getTableY();
        int rowY = tableY + offsetY;
        
        g.setColor(CircuitElm.lightGrayColor);
        int tableWidth = rowDescColWidth + table.cellSpacing * 2 + table.cols * (cellWidthPixels + table.cellSpacing);
        
        // Horizontal lines (top and bottom of row)
        g.drawLine(tableX, rowY, tableX + tableWidth, rowY);
        g.drawLine(tableX, rowY + table.cellHeight, tableX + tableWidth, rowY + table.cellHeight);
        
        // Vertical lines
        // Left edge
        g.drawLine(tableX, rowY, tableX, rowY + table.cellHeight);
        
        // After description column (if not collapsed)
        if (!table.collapsedMode) {
            int x = tableX + table.cellSpacing + rowDescColWidth;
            g.drawLine(x, rowY, x, rowY + table.cellHeight);
        }
        
        // Between and after data columns - only draw where source changes
        for (int col = 0; col <= table.cols; col++) {
            int x = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            
            boolean drawLine = false;
            boolean drawDouble = false;
            
            if (col == table.cols) {
                // Always draw line at right edge
                drawLine = true;
            } else if (col > 0 && col < table.cols && sourceNames != null) {
                // Check if source table changes between adjacent columns
                String prevSource = (col - 1 < sourceNames.length) ? sourceNames[col - 1] : "";
                String currSource = (col < sourceNames.length) ? sourceNames[col] : "";
                if (prevSource == null) prevSource = "";
                if (currSource == null) currSource = "";
                
                if (!prevSource.equals(currSource)) {
                    drawLine = true;
                    drawDouble = true; // Use double line where source changes
                }
            }
            
            if (drawLine) {
                if (drawDouble) {
                    // Draw double vertical line where source changes
                    g.drawLine(x - 1, rowY, x - 1, rowY + table.cellHeight);
                    g.drawLine(x + 1, rowY, x + 1, rowY + table.cellHeight);
                } else {
                    // Draw single vertical line
                    g.drawLine(x, rowY, x, rowY + table.cellHeight);
                }
            }
        }
    }
    
    /**
     * Fallback for drawing empty source table row
     */
    private void drawEmptySourceTableRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = table.collapsedMode ? 0 : cellWidthPixels;
        int rowY = tableY + offsetY;
        
        g.setColor(CircuitElm.lightGrayColor);
        int tableWidth = rowDescColWidth + table.cellSpacing * 2 + table.cols * (cellWidthPixels + table.cellSpacing);
        
        // Draw horizontal lines
        g.drawLine(tableX, rowY, tableX + tableWidth, rowY);
        g.drawLine(tableX, rowY + table.cellHeight, tableX + tableWidth, rowY + table.cellHeight);
    }
    
    /**
     * Override drawColumnTypeRow to merge cells by type within each table group
     * This creates merged "A", "L", "E" cells for each table's stocks
     */
    @Override
    protected void drawColumnTypeRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        int typeRowY = tableY + offsetY;
        
        // Get source table names
        String[] sourceNames = matrixElm.getSourceTableNames();
        if (sourceNames == null || sourceNames.length == 0) {
            super.drawColumnTypeRow(g, offsetY);
            return;
        }
        
        // Draw row description column cell text with header font
        Font headerFont = new Font("SansSerif", Font.BOLD, 11);
        g.setFont(headerFont);
        g.setLetterSpacing("0.5px");
        g.setColor(CircuitElm.whiteColor);
        table.drawCenteredText(g, "", tableX + table.cellSpacing + rowDescColWidth/2, typeRowY + table.cellHeight/2, true);
        
        // Draw column type cells - merge by BOTH source table AND type
        int col = 0;
        while (col < table.cols) {
            // Check if this is A-L-E column
            boolean isALEColumn = (col == table.cols - 1 && table.cols >= 4);
            
            if (isALEColumn) {
                // A-L-E column: don't show type
                int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
                table.drawCenteredText(g, "", cellX + cellWidthPixels/2, typeRowY + table.cellHeight/2, true);
                col++;
            } else {
                // Regular column: find consecutive columns with SAME source table AND SAME type
                String sourceName = (col < sourceNames.length) ? sourceNames[col] : "";
                if (sourceName == null) sourceName = "";
                
                TableEditDialog.ColumnType type = table.getColumnType(col);
                String typeName = getColumnTypeName(type);
                
                int startCol = col;
                int endCol = col;
                
                // Count consecutive columns with same source AND same type
                while (endCol + 1 < table.cols) {
                    // Check if next column would be A-L-E
                    boolean nextIsALE = (endCol + 1 == table.cols - 1 && table.cols >= 4);
                    if (nextIsALE) break;
                    
                    // Check if next column has same source AND same type
                    String nextSource = (endCol + 1 < sourceNames.length) ? sourceNames[endCol + 1] : "";
                    if (nextSource == null) nextSource = "";
                    
                    TableEditDialog.ColumnType nextType = table.getColumnType(endCol + 1);
                    
                    if (sourceName.equals(nextSource) && type == nextType) {
                        endCol++;
                    } else {
                        break;
                    }
                }
                
                // Calculate center position across merged cells
                int startCellX = tableX + rowDescColWidth + table.cellSpacing * 2 + startCol * (cellWidthPixels + table.cellSpacing);
                int endCellX = tableX + rowDescColWidth + table.cellSpacing * 2 + endCol * (cellWidthPixels + table.cellSpacing);
                int centerX = startCellX + (endCellX - startCellX + cellWidthPixels) / 2;
                
                // Draw the type name centered across merged cells
                table.drawCenteredText(g, typeName, centerX, typeRowY + table.cellHeight/2, true);
                
                col = endCol + 1;
            }
        }
        
        // Draw grid lines for type row
        drawColumnTypeRowGridLines(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, sourceNames);
    }
    
    /**
     * Draw grid lines for column type row with merged cells
     */
    private void drawColumnTypeRowGridLines(Graphics g, int offsetY, int tableX, int rowDescColWidth, int cellWidthPixels, String[] sourceNames) {
        int tableY = table.getTableY();
        int rowY = tableY + offsetY;
        
        g.setColor(CircuitElm.lightGrayColor);
        int tableWidth = rowDescColWidth + table.cellSpacing * 2 + table.cols * (cellWidthPixels + table.cellSpacing);
        
        // Horizontal lines (top and bottom of row)
        g.drawLine(tableX, rowY, tableX + tableWidth, rowY);
        g.drawLine(tableX, rowY + table.cellHeight, tableX + tableWidth, rowY + table.cellHeight);
        
        // Vertical lines
        // Left edge
        g.drawLine(tableX, rowY, tableX, rowY + table.cellHeight);
        
        // After description column
        int x = tableX + table.cellSpacing + rowDescColWidth;
        g.drawLine(x, rowY, x, rowY + table.cellHeight);
        
        // Between and after data columns - draw where source OR type changes
        for (int col = 0; col <= table.cols; col++) {
            x = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            
            if (col == table.cols) {
                // Always draw line at right edge
                g.drawLine(x, rowY, x, rowY + table.cellHeight);
            } else if (col > 0 && col < table.cols && sourceNames != null) {
                // Check if source table OR type changes between adjacent columns
                String prevSource = (col - 1 < sourceNames.length) ? sourceNames[col - 1] : "";
                String currSource = (col < sourceNames.length) ? sourceNames[col] : "";
                if (prevSource == null) prevSource = "";
                if (currSource == null) currSource = "";
                
                TableEditDialog.ColumnType prevType = table.getColumnType(col - 1);
                TableEditDialog.ColumnType currType = table.getColumnType(col);
                
                // Draw line if source OR type changes
                if (!prevSource.equals(currSource) || prevType != currType) {
                    g.drawLine(x, rowY, x, rowY + table.cellHeight);
                }
            }
        }
    }
}
