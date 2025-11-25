/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.TableEditDialog.ColumnType;

/**
 * Custom renderer for CurrentTransactionsMatrixElm.
 * Extends TableRenderer to show source table names in column headers
 * and column types in a dedicated type row (similar to TableEditDialog).
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
        m.rowDescColWidth = (table.collapsedMode ? 0 : m.cellWidthPixels);
        
        int titleHeight = 20;
        int tableNameRowHeight = table.cellHeight + table.cellSpacing; // Source table names
        int typeRowHeight = (table.cellHeight + table.cellSpacing); // Column types
        int stockVarsRowHeight = (table.cellHeight + table.cellSpacing); // Stock variable names
        int initialRowHeight = (table.showInitialValues && !table.collapsedMode) ? 
            (table.cellHeight + table.cellSpacing) : 0;
        int dataRowsHeight = table.collapsedMode ? 0 : 
            (table.rows * (table.cellHeight + table.cellSpacing));
        int computedRowHeight = table.cellHeight + table.cellSpacing;
        
        m.tableWidth = m.rowDescColWidth + table.cellSpacing + 
            table.getCols() * m.cellWidthPixels + (table.getCols() + 1) * table.cellSpacing;
        m.tableHeight = titleHeight + tableNameRowHeight + typeRowHeight + stockVarsRowHeight + 
            initialRowHeight + dataRowsHeight + computedRowHeight;
        
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
        
        // Table name row - source table name in each column header
        drawTableNameRow(g, currentY);
        currentY += table.cellHeight + table.cellSpacing;
        
        // Type row - column types
        drawColumnTypeRow(g, currentY);
        currentY += table.cellHeight + table.cellSpacing;
        
        // Stock vars row - shows Flows↓/Stock Vars → label and stock variable names
        drawStockVarsRow(g, currentY);
        currentY += table.cellHeight + table.cellSpacing;
        
        // Initial values row
        if (table.showInitialValues && !table.collapsedMode) {
            drawInitialConditionsRow(g, currentY);
            currentY += table.cellHeight + table.cellSpacing;
        }
        
        // Data rows (skip in collapsed mode)
        if (!table.collapsedMode) {
            drawTableCells(g, currentY);
            currentY += table.rows * (table.cellHeight + table.cellSpacing);
        }
        
        // Sum/computed row
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
     * Draw table name row - shows source table name in each column header
     * Similar to TableEditDialog's table name row
     */
    private void drawTableNameRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = (table.collapsedMode ? 0 : cellWidthPixels);
        int rowY = tableY + offsetY;
        
        setupHeaderFont(g);
        
        // Draw empty row description
        if (!table.collapsedMode) {
            table.drawCenteredText(g, "", tableX + table.cellSpacing + rowDescColWidth/2, 
                rowY + table.cellHeight/2, true);
        }
        
        // Draw table name in each column header
        for (int col = 0; col < table.getCols(); col++) {
            String tableName = matrixElm.getSourceTableName(col);
            if (tableName == null || tableName.isEmpty()) {
                tableName = matrixElm.getOutputName(col);
            }
            
            int centerX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                col * (cellWidthPixels + table.cellSpacing) + cellWidthPixels/2;
            
            g.setColor(CircuitElm.whiteColor);
            table.drawCenteredText(g, tableName, centerX, rowY + table.cellHeight/2, true);
        }
        
        // Draw grid lines using helper
        drawRowGridLines(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
    }
    
    /**
     * Draw column type row - shows Asset/Liability/Equity indicators
     * Similar to TableEditDialog's type row
     */
    @Override
    protected void drawColumnTypeRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        int rowY = tableY + offsetY;
        
        setupHeaderFont(g);
        
        // Draw "Type:" label in row description column
        if (!table.collapsedMode) {
            table.drawCenteredText(g, "Type:", tableX + table.cellSpacing + rowDescColWidth/2, 
                rowY + table.cellHeight/2, true);
        }
        
        // Draw type for each column
        for (int col = 0; col < table.getCols(); col++) {
            boolean isALE = (col == table.getCols() - 1 && table.getCols() >= 4);
            String typeText = isALE ? "🧮 A-L-E" : getColumnTypeName(table.getColumnType(col));
            
            int centerX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                col * (cellWidthPixels + table.cellSpacing) + cellWidthPixels/2;
            
            g.setColor(CircuitElm.whiteColor);
            table.drawCenteredText(g, typeText, centerX, rowY + table.cellHeight/2, true);
        }
        
        // Draw grid lines using helper
        drawRowGridLines(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
    }
    
    /**
     * Draw stock variables row - shows "Flows↓/Stock Vars →" label and stock variable names
     * Similar to TableEditDialog's stock values row
     */
    private void drawStockVarsRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        int rowY = tableY + offsetY;
        
        setupHeaderFont(g);
        
        // Draw "Flows↓/Stock Vars →" label in row description column
        if (!table.collapsedMode) {
            table.drawCenteredText(g, "Flows↓/Stock Vars →", tableX + table.cellSpacing + rowDescColWidth/2, 
                rowY + table.cellHeight/2, true);
        }
        
        // Draw stock variable name for each column
        for (int col = 0; col < table.getCols(); col++) {
            boolean isALE = (col == table.getCols() - 1 && table.getCols() >= 4);
            String stockVarName = isALE ? "A-L-E" : matrixElm.getOutputName(col);
            
            int centerX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                col * (cellWidthPixels + table.cellSpacing) + cellWidthPixels/2;
            
            g.setColor(CircuitElm.whiteColor);
            table.drawCenteredText(g, stockVarName, centerX, rowY + table.cellHeight/2, true);
        }
        
        // Draw grid lines using helper
        drawRowGridLines(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
    }
    
    /** Setup header font for row labels - uses base class constants */
    private void setupHeaderFont(Graphics g) {
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(CircuitElm.whiteColor);
    }
    
    /**
     * Override drawTableCells - uses base class implementation
     * Base class handles all cell rendering logic including showCellValues modes
     */
    @Override
    protected void drawTableCells(Graphics g, int offsetY) {
        // Use parent implementation
        super.drawTableCells(g, offsetY);
    }
    
    /**
     * Override drawSumRow - uses base class implementation
     * Base class handles all value computation and color logic
     */
    @Override
    protected void drawSumRow(Graphics g, int offsetY) {
        // Use parent implementation
        super.drawSumRow(g, offsetY);
    }

}

