/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * TableGeometryManager - Handles pin positioning and table geometry calculations
 * Manages the conversion between grid units and pixels, and calculates pin positions
 */
public class TableGeometryManager {
    private final TableElm table;
    
    public TableGeometryManager(TableElm table) {
        this.table = table;
    }
    
    /**
     * Setup output pins on bottom edge of table.
     * Pins are positioned to align with column centers.
     */
    public void setupPins() {
        calculateChipSize();
        createOutputPins();
    }
    
    /**
     * Calculate chip size in cspc2 units (double grid spacing)
     */
    private void calculateChipSize() {
        int rowDescColWidth = table.cellWidthInGrids;
        int extraRows = getExtraRowCount();
        
        // Calculate table dimensions in pixels
        int tableWidthPixels = (rowDescColWidth + table.getCols() * table.cellWidthInGrids) * 
                               table.cspc + 2 * table.cspc;
        int tableHeightPixels = (table.rows + extraRows) * table.cellHeight + 
                               (table.rows + extraRows + 1) * table.cellSpacing + 20;
        
        // Set chip size in cspc2 units (rounded up)
        table.sizeX = (tableWidthPixels + table.cspc2 - 1) / table.cspc2;
        table.sizeY = (tableHeightPixels + table.cspc2 - 1) / table.cspc2;
    }
    
    /**
     * Get number of extra rows (title, type, header, optional initial, computed).
     * Adjusted for collapsed mode.
     */
    private int getExtraRowCount() {
        if (table.collapsedMode) {
            return 2; // header + computed
        } else {
            return (table.showInitialValues ? 1 : 0) + 3; // type + header + computed
        }
    }
    
    /**
     * Calculate table height in pixels.
     * Centralized calculation used by both bounding box and pin positioning.
     */
    private int calculateTableHeight() {
        int titleHeight = 20;
        int typeRowHeight = table.collapsedMode ? 0 : (table.cellHeight + table.cellSpacing);
        int headerRowHeight = table.cellHeight + table.cellSpacing;
        int initialRowHeight = (table.showInitialValues && !table.collapsedMode) ? 
                              (table.cellHeight + table.cellSpacing) : 0;
        int dataRowsHeight = table.collapsedMode ? 0 : 
                            (table.rows * (table.cellHeight + table.cellSpacing));
        int computedRowHeight = table.cellHeight + table.cellSpacing;
        
        return titleHeight + typeRowHeight + headerRowHeight + 
               initialRowHeight + dataRowsHeight + computedRowHeight;
    }
    
    /**
     * Create output pins for table columns.
     * Only master columns (excluding A-L-E) are marked as outputs.
     */
    private void createOutputPins() {
        table.pins = new ChipElm.Pin[table.getCols()];
        
        for (int i = 0; i < table.getCols(); i++) {
            String label = getOutputLabel(i);
            int pinX = calculatePinX(i);
            table.pins[i] = table.new Pin(pinX, ChipElm.SIDE_S, label);
            table.pins[i].output = isPinOutput(i);
        }
    }
    
    /**
     * Check if a pin should be marked as output
     */
    private boolean isPinOutput(int col) {
        if (col >= table.columns.size()) return false;
        
        // A-L-E columns are never outputs
        if (table.columns.get(col).isALE()) return false;
        
        // Check if master for this stock
        String stockName = table.columns.get(col).getStockName();
        return stockName != null && !stockName.trim().isEmpty() &&
               ComputedValues.isMasterTable(stockName.trim(), table);
    }
    
    /**
     * Get output label for column
     */
    private String getOutputLabel(int col) {
        return (col < table.columns.size()) ?
               table.columns.get(col).getStockName() : "Stock" + (col + 1);
    }
    
    /**
     * Calculate pin X position in cspc2 units relative to chip origin
     */
    private int calculatePinX(int col) {
        int cellWidthPixels = getCellWidthPixels();
        int rowDescColWidthPixels = table.cellWidthInGrids * table.cspc;
        
        // Column center in pixels relative to table origin
        int columnCenterX = rowDescColWidthPixels + table.cellSpacing * 2 + 
                           col * (cellWidthPixels + table.cellSpacing) + cellWidthPixels / 2;
        
        // Convert to cspc2 units relative to x0 (chip origin + cspc2)
        return (columnCenterX - table.cspc2) / table.cspc2;
    }
    
    /**
     * Set pin positions and bounding box after chip positioning
     */
    public void setPoints() {
        calculateBoundingBox();
        adjustPinPositions();
    }
    
    /**
     * Calculate and set the bounding box for the table.
     * Must match dimensions used in TableRenderer.draw().
     */
    private void calculateBoundingBox() {
        int cellWidthPixels = getCellWidthPixels();
        int rowDescColWidth = table.collapsedMode ? 0 : cellWidthPixels;
        
        int tableWidth = rowDescColWidth + table.cellSpacing + 
                        table.getCols() * cellWidthPixels + 
                        (table.getCols() + 1) * table.cellSpacing;
        int tableHeight = calculateTableHeight();

        // Set bounding box to match table size
        table.setBbox(table.x, table.y, table.x + tableWidth, table.y + tableHeight);
    }
    
    /**
     * Adjust pin Y positions to align with table bottom.
     * X positions are already set from calculatePinX().
     */
    private void adjustPinPositions() {
        int tableHeight = calculateTableHeight();
        int tableBottomY = table.y + tableHeight;
        
        for (int i = 0; i < table.pins.length; i++) {
            ChipElm.Pin p = table.pins[i];
            int pinXPixel = p.post.x;
            
            // Position post below table with spacing
            int postY = roundToNearestGrid(tableBottomY + 3 * table.cellHeight / 2, table.cspc);
            
            p.post = new Point(pinXPixel, postY);
            p.stub = new Point(pinXPixel, tableBottomY + table.cspc / 2);
            p.textloc = new Point(pinXPixel, tableBottomY);
        }
    }
    
    /**
     * Round value to nearest grid spacing
     */
    private int roundToNearestGrid(int value, int gridSpacing) {
        return ((value + gridSpacing / 2) / gridSpacing) * gridSpacing;
    }
    
    /**
     * Get cell width in pixels
     * Uses a constant base grid size (16 pixels) to ensure consistent sizing
     * regardless of the table's small/normal chip size setting
     */
    public int getCellWidthPixels() {
        // Use constant 16-pixel grid (normal grid size) for cell width calculations
        // This ensures cellWidthInGrids has consistent meaning across all tables
        final int BASE_GRID_SIZE = 16;
        return table.cellWidthInGrids * BASE_GRID_SIZE;
    }
}
