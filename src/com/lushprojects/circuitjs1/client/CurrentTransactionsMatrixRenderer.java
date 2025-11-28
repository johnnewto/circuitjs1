/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * Custom renderer for CurrentTransactionsMatrixElm.
 * Extends TableRenderer and adds table name row and replaces column headers with stock vars row.
 */
public class CurrentTransactionsMatrixRenderer extends TableRenderer {
    private final CurrentTransactionsMatrixElm matrixElm;
    
    public CurrentTransactionsMatrixRenderer(CurrentTransactionsMatrixElm table) {
        super(table);
        this.matrixElm = table;
    }
    
    /**
     * Override to add ALE calculation for CTM.
     * The base class now handles flow name mapping, so we only need to calculate ALE.
     */
    @Override
    protected void updateCachedValues() {
        // Call base class to handle standard cell value updates with flow name mapping
        super.updateCachedValues();
        
        // Calculate ALE column values (specific to CTM)
        calculateALEColumn();
    }
    
    /**
     * Calculate ALE column values (each row's ALE is sum of all values in that row)
     * For CTM, ALE represents the total transaction amount across all stocks for each flow
     */
    private void calculateALEColumn() {
        if (!hasALEColumn()) {
            return;
        }
        
        int aleCol = table.getCols() - 1;
        int regularColCount = getRegularColumnCount();
        
        // Calculate ALE for each row (sum of all regular columns)
        double aleColumnSum = 0.0;
        for (int row = 0; row < table.rows; row++) {
            double rowSum = 0.0;
            for (int col = 0; col < regularColCount; col++) {
                rowSum += cachedCellValues[row][col];
            }
            cachedCellValues[row][aleCol] = rowSum;
            aleColumnSum += rowSum;
        }
        
        cachedSumValues[aleCol] = aleColumnSum;
    }
    
    /**
     * Override A-L-E initial value calculation for CTM.
     * For CTM, initial A-L-E is always 0 (sum of initial row sums).
     */
    @Override
    protected double getALEInitialValue() {
        // CTM initial A-L-E is sum of all initial values across all regular columns
        // Since each flow starts at 0, the sum is 0
        if (!hasALEColumn()) {
            return 0.0;
        }
        
        double sum = 0.0;
        for (int col = 0; col < getRegularColumnCount(); col++) {
            if (col < table.columns.size()) {
                sum += table.columns.get(col).getInitialValue();
            }
        }
        return sum;
    }
    
    /**
     * Override A-L-E sum calculation for CTM.
     * For CTM, the sum row A-L-E is the total of all transactions across all stocks.
     * This represents the grand total of all flows across all stocks.
     * @return Sum of all regular column sums
     */
    @Override
    protected double getALESumValue() {
        if (!hasALEColumn() || cachedSumValues == null) {
            return 0.0;
        }
        
        double total = 0.0;
        int regularColCount = getRegularColumnCount();
        
        for (int col = 0; col < regularColCount && col < cachedSumValues.length; col++) {
            total += cachedSumValues[col];
        }
        
        return total;
    }
    
    /**
     * Override A-L-E row calculation for CTM.
     * CTM uses direct value from cached cell values (already calculated as row sums).
     * Parameters totalAssets, totalLiabilities, totalEquity are ignored for CTM.
     * @param row Row index
     * @return ALE value for this row (sum of all regular columns in this row)
     */
    @Override
    protected double getALERowValue(int row, double totalAssets, double totalLiabilities, double totalEquity) {
        if (!hasALEColumn()) {
            return 0.0;
        }
        return getCachedCellValue(row, table.getCols() - 1);
    }
    
    /**
     * CTM has an extra table name row before the type row
     */
    @Override
    protected int getExtraRowsBeforeTypeRowHeight() {
        return table.cellHeight + table.cellSpacing;
    }
    
    /**
     * Override to insert table name row before the type row
     */
    @Override
    protected int drawExtraRowsBeforeTypeRow(Graphics g, int currentY) {
        drawTableNameRow(g, currentY);
        return currentY + table.cellHeight + table.cellSpacing;
    }
    
    /**
     * Override column headers to draw stock variable names instead
     */
    @Override
    protected void drawColumnHeaders(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 2;
        int rowY = tableY + offsetY;
        
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(CircuitElm.whiteColor);
        
        // Draw "Flows↓/Stock Vars →" label in row description column (skip in collapsed mode)
        if (!table.collapsedMode) {
            table.drawCenteredText(g, "Flows↓/Stock Vars →", tableX + table.cellSpacing + rowDescColWidth/2, 
                rowY + table.cellHeight/2, true);
        }
        
        // Draw stock variable name for each column
        for (int col = 0; col < table.getCols(); col++) {
            String stockVarName = matrixElm.getOutputName(col);
            
            // For CTM, label the ALE column as "SUM" instead of "A-L-E"
            if (hasALEColumn() && col == table.getCols() - 1) {
                stockVarName = "SUM";
            }
            
            int centerX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                col * (cellWidthPixels + table.cellSpacing) + cellWidthPixels/2;
            
            table.drawCenteredText(g, stockVarName, centerX, rowY + table.cellHeight/2, true);
        }
        
        drawRowGridLine(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
    }
    
    /**
     * Draw table name row - shows source table name in each column header
     * Similar to TableEditDialog's table name row
     */
    private void drawTableNameRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 2;
        int rowY = tableY + offsetY;
        
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(CircuitElm.whiteColor);
        
        // Draw empty row description
        table.drawCenteredText(g, "", tableX + table.cellSpacing + rowDescColWidth/2, 
            rowY + table.cellHeight/2, true);
        
        // Draw table name in each column header
        for (int col = 0; col < table.getCols(); col++) {
            String tableName = "";
            
            // For computed columns (A-L-E), show blank
            if (col < table.columns.size() && table.columns.get(col).isALE()) {
                tableName = "";
            } else {
                tableName = matrixElm.getSourceTableName(col);
                if (tableName == null || tableName.isEmpty()) {
                    tableName = matrixElm.getOutputName(col);
                }
            }
            
            int centerX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                col * (cellWidthPixels + table.cellSpacing) + cellWidthPixels/2;
            
            table.drawCenteredText(g, tableName, centerX, rowY + table.cellHeight/2, true);
        }
        
        drawRowGridLine(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
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
        int rowDescColWidth = cellWidthPixels * 2;
        int rowY = tableY + offsetY;
        
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(CircuitElm.whiteColor);
        
        // Draw "Type:" label in row description column
        table.drawCenteredText(g, "Type:", tableX + table.cellSpacing + rowDescColWidth/2, 
            rowY + table.cellHeight/2, true);
        
        // Draw type for each column
        for (int col = 0; col < table.getCols(); col++) {
            String typeText = "";
            
            // Check if this is an A-L-E column (should show blank type)
            if (col < table.columns.size() && !table.columns.get(col).isALE()) {
                typeText = getColumnTypeName(table.getColumnType(col));
            }
            
            int centerX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                col * (cellWidthPixels + table.cellSpacing) + cellWidthPixels/2;
            
            table.drawCenteredText(g, typeText, centerX, rowY + table.cellHeight/2, true);
        }
        
        drawRowGridLine(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
    }
    
    /**
     * Override to prevent blue coloring of SUM column in CTM.
     * CTM's last column is a simple SUM, not an accounting equation (A-L-E),
     * so it should use normal voltage-based coloring, not blue for non-zero values.
     * 
     * @return false for CTM - never use blue for discrepancy indication
     */
    @Override
    protected boolean shouldColorComputedColumnBlue(int col, double value) {
        // CTM uses SUM column, not A-L-E accounting equation
        // No need for blue discrepancy warning
        return false;
    }

}

