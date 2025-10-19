/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * TableGeometryManager - Handles pin positioning and table geometry calculations
 * Separates geometry logic from circuit simulation logic
 */
public class TableGeometryManager {
    private final TableElm table;
    
    public TableGeometryManager(TableElm table) {
        this.table = table;
    }
    
    /**
     * Setup output pins on bottom edge of table
     */
    public void setupPins() {
        // Cell width is in cspc units (single grid spacing)
        // But chip sizeX and sizeY must be in cspc2 units (double grid spacing)
        int rowDescColWidth = table.cellWidthInGrids;  // In cspc units
        int extraRows = (table.showInitialValues ? 1 : 0) + 1 + 1;
        
        // Calculate table dimensions in pixels
        int tableWidthPixels = (rowDescColWidth + table.cols * table.cellWidthInGrids) * table.cspc + 2 * table.cspc;  // Add margins
        int tableHeightPixels = (table.rows + extraRows) * table.cellHeight + (table.rows + extraRows + 1) * table.cellSpacing + 20;
        
        // Set chip size in cspc2 units (cspc2 = 2*cspc)
        table.sizeX = (tableWidthPixels + table.cspc2 - 1) / table.cspc2; // Round up
        table.sizeY = (tableHeightPixels + table.cspc2 - 1) / table.cspc2; // Round up

        // Create output pins on bottom edge
        table.pins = new ChipElm.Pin[table.cols];
        for (int i = 0; i < table.cols; i++) {
            String label = (table.outputNames != null && i < table.outputNames.length) ?
                          table.outputNames[i] : "Stock" + (i + 1);

            // Calculate pin X position
            // Column center in pixels (relative to table origin x):

            int cellWidthPixels = table.cellWidthInGrids * table.cspc;
            int rowDescColWidthPixels = table.cellWidthInGrids * table.cspc;
            int columnCenterX = rowDescColWidthPixels + table.cellSpacing * 2 + i * (cellWidthPixels + table.cellSpacing) + cellWidthPixels / 2;
            
            // Pin position is relative to x0 = x + cspc2, measured in cspc2 units
            // Column center is at: x + columnCenterX
            // Relative to x0: (x + columnCenterX) - (x + cspc2) = columnCenterX - cspc2
            // In cspc2 units: (columnCenterX - cspc2) / cspc2
            int pinX = (columnCenterX - table.cspc2) / table.cspc2;
            
            // Pin at bottom of chip (SIDE_S)
            table.pins[i] = table.new Pin(pinX, ChipElm.SIDE_S, label);
            table.pins[i].output = true;
        }
    }
    
    /**
     * Set pin positions and bounding box
     */
    public void setPoints() {
        // Calculate table dimensions in pixels for bounding box
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        int tableWidth = rowDescColWidth + table.cellSpacing + table.cols * cellWidthPixels + (table.cols + 1) * table.cellSpacing;
        int extraRows = (table.showInitialValues ? 1 : 0) + 1 + 1;
        int tableHeight = (table.rows + extraRows) * table.cellHeight + (table.rows + extraRows + 1) * table.cellSpacing + 20;

        // Align table origin with chip origin
        // The table should start at the chip's top-left corner
        int tableX = table.x;
        int tableY = table.y;

        // Set bounding box to exactly match the table size
        table.setBbox(tableX, tableY, tableX + tableWidth, tableY + tableHeight);
        
        // Override pin Y positions for precise placement at bottom of table
        // After super.setPoints() sets up pins using chip coordinate system,
        // we adjust only the Y positions to align with the actual table bottom
        // Keep the X positions from super.setPoints() as they're already correct
        int tableBottomY = tableY + tableHeight; // Bottom of table in pixels
        
        for (int i = 0; i < table.pins.length; i++) {
            ChipElm.Pin p = table.pins[i];
            
            // Keep the X positions from super.setPoints(), only override Y
            int pinXPixel = p.post.x;
            
            // Set pin positions with correct Y alignment
            // Post Y must be rounded to cspc for wire connection snapping

            int postY = tableBottomY + 3 * table.cellHeight / 2;  // allow for the Computed row
            postY = ((postY + table.cspc/2) / table.cspc) * table.cspc;  // Round to nearest cspc
            
            // Stub is where the pin meets the chip
            p.post = new Point(pinXPixel, postY);
            p.stub = new Point(pinXPixel, tableBottomY + table.cspc/2);
            p.textloc = new Point(pinXPixel, tableBottomY);
        }
    }
    
    /**
     * Get cell width in pixels
     */
    public int getCellWidthPixels() {
        return table.cellWidthInGrids * table.cspc;
    }
}
