/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.elements.economics;

import com.lushprojects.circuitjs1.client.elements.economics.TableColumn.ColumnType;
import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;

/**
 * SFCTableRenderer - Custom renderer for SFC (Stock-Flow Consistent) tables.
 * 
 * Extends TableRenderer to provide:
 * - Σ column header instead of A-L-E (row sums for horizontal consistency)
 * - Σ row at bottom (column sums for vertical consistency)
 * - Red highlighting for non-zero sums (balance errors)
 * - "Sector" type label for sector columns
 *
 * Rendering cache note:
 * This renderer intentionally reuses TableRenderer's offscreen blit cache
 * (cached canvas + drawImage composite) for static layers.
 */
public class SFCTableRenderer extends TableRenderer {
    private final SFCTableElm sfcTable;
    
    public SFCTableRenderer(SFCTableElm table) {
        super(table);
        this.sfcTable = table;
    }

    /**
     * Uses shared blit caching from TableRenderer.
     */
    @Override
    public void draw(Graphics g) {
        super.draw(g);
    }

    @Override
    protected void onStaticCacheRebuilt() {
        // CirSim.console("[SFCTableRenderer] Blit cache rebuilt and ready");
    }

    @Override
    protected void onCacheHitThrottled() {
        // CirSim.console("[SFCTableRenderer] Blit cache hit (reused cached canvas)");
    }
    
    /**
     * Override hasALEColumn to return true for SFC tables since we have a Σ (COMPUTED) column.
     * The base class checks showALE which is false for SFC tables.
     */
    @Override
    protected boolean hasALEColumn() {
        // SFC tables have a Σ column (COMPUTED type) as the last column
        if (table.columns == null || table.columns.isEmpty()) {
            return false;
        }
        TableColumn lastCol = table.columns.get(table.columns.size() - 1);
        return lastCol.getType() == ColumnType.COMPUTED;
    }
    
    /**
     * Override to update SFC-specific cached values including Σ column (row sums)
     */
    @Override
    protected void updateCachedValues() {
        super.updateCachedValues();
    }
    
    /**
     * Get the number of sector columns (excsluding Σ column)
     */
    private int getSectorColumnCount() {
        if (table.columns == null) return 0;
        int count = 0;
        for (TableColumn col : table.columns) {
            if (col.getType() == ColumnType.SECTOR) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Override to rename Σ column header
     */
    @Override
    protected void drawColumnHeaders(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 3 / 2;
        int rowY = tableY + offsetY;
        
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(getTextColor());
        
        // Draw "Transaction" label in row description column
        if (!table.collapsedMode) {
            table.drawCenteredText(g, "Transaction", tableX + table.cellSpacing + rowDescColWidth/2, 
                rowY + table.cellHeight/2, true);
        }
        
        // Draw column headers
        for (int col = 0; col < table.getCols(); col++) {
            String headerText;
            
            // Check if this is the Σ column (computed type)
            if (col < table.columns.size() && table.columns.get(col).getType() == ColumnType.COMPUTED) {
                headerText = "Σ";  // Use sigma symbol instead of stock name
            } else {
                headerText = table.getColumnHeader(col);
            }
            
            int cellX = getColumnX(col, tableX, rowDescColWidth, cellWidthPixels);
            int colWidth = getColumnWidth(col, cellWidthPixels);
            
            table.drawCenteredText(g, headerText, cellX + colWidth/2, rowY + table.cellHeight/2, true);
        }
        
        drawRowGridLine(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
    }
    
    /**
     * Override to hide type row for SFC tables (no space allocated)
     */
    @Override
    protected boolean shouldShowTypeRow() {
        return false;
    }
    
    /**
     * Override column type row to hide it for SFC tables
     */
    @Override
    protected void drawColumnTypeRow(Graphics g, int offsetY) {
        // Don't draw type row for SFC tables
    }
    
    /**
     * Override sum row to:
     * - Change label to "Σ" (instead of "Computed")
     * - Add red highlighting for imbalanced column sums
     * - Uses cachedSumValues[] which contains the sum of all values in each column
     */
    @Override
    protected void drawSumRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 3 / 2;
        int sumRowY = tableY + offsetY;
        
        g.setFont(CELL_FONT);
        
        // Draw "Σ" label
        g.setColor(getTextColor());
        table.drawCenteredText(g, "Σ", tableX + table.cellSpacing + rowDescColWidth/2, 
            sumRowY + table.cellHeight/2, true);
        
        // Draw column sums for each column
        for (int col = 0; col < table.getCols(); col++) {
            double value = (hasALEColumn() && col == table.getCols() - 1)
                ? getALESumValue()
                : getRegularColumnSum(col);
            
            // Determine text color - red only for negative sums
            if (value < -sfcTable.getBalanceTolerance() && sfcTable.shouldHighlightImbalances()) {
                g.setColor(Color.red);
            } else {
                g.setColor(getTextVoltageColor(value));
            }
            
            String sumText = formatDisplayValue(value, table.tableUnits);
            int cellX = getColumnX(col, tableX, rowDescColWidth, cellWidthPixels);
            int colWidth = getColumnWidth(col, cellWidthPixels);
            
            table.drawCenteredText(g, sumText, cellX + colWidth/2, sumRowY + table.cellHeight/2, true);
        }
        
        // Draw grid lines with double line above (sum row style)
        drawRowGridLine(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, true);
    }
    
    /**
     * Override A-L-E sum calculation for SFC tables.
     * Returns the grand total (sum of all sector column sums)
     */
    @Override
    protected double getALESumValue() {
        double total = 0.0;
        int sectorColCount = getSectorColumnCount();
        
        for (int col = 0; col < sectorColCount; col++) {
            total += getRegularColumnSum(col);
        }
        
        return total;
    }
    
    /**
     * Override A-L-E row value calculation for SFC tables.
     * Returns the row sum (horizontal consistency value).
     */
    @Override
    protected double getALERowValue(int row, double totalAssets, double totalLiabilities, double totalEquity) {
        if (!hasALEColumn()) {
            return 0.0;
        }
        return sfcTable.getSigmaColumnValue(row);
    }
    
    /**
     * Override to color Σ column values red if imbalanced (not blue)
     */
    @Override
    protected boolean shouldColorComputedColumnBlue(int col, double computedValue) {
        // For SFC tables, use red for imbalanced values (not blue)
        return false;
    }
    
    /**
     * Get the grand total (bottom-right cell) - sum of all sector column sums.
     * For a balanced SFC table, this should equal 0.
     * @return The grand total
     */
    public double getGrandTotal() {
        double total = 0.0;
        int sectorColCount = getSectorColumnCount();
        for (int col = 0; col < sectorColCount; col++) {
            total += getRegularColumnSum(col);
        }
        return total;
    }
    
    /**
     * Get displayed cell value - exposed for testing
     * @param row Row index
     * @param col Column index
     * @return The cached cell value
     */
    public double getCachedCellValue(int row, int col) {
        if (hasALEColumn() && col == table.getCols() - 1) {
            return sfcTable.getRowSum(row);
        }
        return table.getDisplayedTransactionValue(row, col);
    }
}
