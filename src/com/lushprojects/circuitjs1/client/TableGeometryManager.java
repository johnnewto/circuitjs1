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
     * Setup output pins on bottom edge of table
     * Pins are positioned to align with column centers
     */
    public void setupPins() {
        calculateChipSize();
        createOutputPins();
    }
    
    /**
     * Calculate chip size in cspc2 units (double grid spacing)
     */
    private void calculateChipSize() {
        int rowDescColWidth = table.cellWidthInGrids;  // In cspc units
        int extraRows = getExtraRowCount();
        
        // Calculate table dimensions in pixels
        int tableWidthPixels = (rowDescColWidth + table.cols * table.cellWidthInGrids) * table.cspc + 2 * table.cspc;
        int tableHeightPixels = (table.rows + extraRows) * table.cellHeight + 
                               (table.rows + extraRows + 1) * table.cellSpacing + 20;
        
        // Set chip size in cspc2 units (rounded up)
        table.sizeX = (tableWidthPixels + table.cspc2 - 1) / table.cspc2;
        table.sizeY = (tableHeightPixels + table.cspc2 - 1) / table.cspc2;
    }
    
    /**
     * Get number of extra rows (title, type, header, optional initial, computed)
     */
    private int getExtraRowCount() {
        return (table.showInitialValues ? 1 : 0) + 3; // type + header + computed
    }
    
    /**
     * Create output pins positioned at column centers on bottom edge
     */
    private void createOutputPins() {
        table.pins = new ChipElm.Pin[table.cols];
        
        for (int i = 0; i < table.cols; i++) {
            String label = getOutputLabel(i);
            int pinX = calculatePinX(i);
            
            table.pins[i] = table.new Pin(pinX, ChipElm.SIDE_S, label);
            table.pins[i].output = true;
        }
    }
    
    /**
     * Get output label for column
     */
    private String getOutputLabel(int col) {
        return (table.outputNames != null && col < table.outputNames.length) ?
               table.outputNames[col] : "Stock" + (col + 1);
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
     * Calculate and set the bounding box for the table
     */
    private void calculateBoundingBox() {
        int cellWidthPixels = getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        int tableWidth = rowDescColWidth + table.cellSpacing + 
                        table.cols * cellWidthPixels + (table.cols + 1) * table.cellSpacing;
        
        int extraRows = getExtraRowCount();
        int tableHeight = (table.rows + extraRows) * table.cellHeight + 
                         (table.rows + extraRows + 1) * table.cellSpacing + 20;

        // Table origin aligns with chip origin
        int tableX = table.x;
        int tableY = table.y;

        // Set bounding box to match table size
        table.setBbox(tableX, tableY, tableX + tableWidth, tableY + tableHeight);
    }
    
    /**
     * Adjust pin Y positions to align with table bottom
     * X positions are already correct from super.setPoints()
     */
    private void adjustPinPositions() {
        int extraRows = getExtraRowCount();
        int tableHeight = (table.rows + extraRows) * table.cellHeight + 
                         (table.rows + extraRows + 1) * table.cellSpacing + 20;
        
        int tableY = table.y;
        int tableBottomY = tableY + tableHeight;
        
        for (int i = 0; i < table.pins.length; i++) {
            ChipElm.Pin p = table.pins[i];
            int pinXPixel = p.post.x;
            
            // Position post below table with spacing for computed row
            int postY = tableBottomY + 3 * table.cellHeight / 2;
            postY = roundToNearestGrid(postY, table.cspc);
            
            // Stub is where pin meets the chip
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
     */
    public int getCellWidthPixels() {
        return table.cellWidthInGrids * table.cspc;
    }
}
