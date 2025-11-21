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
        CirSim.console("[GEOM_PINS] Table '" + table.tableTitle + "': setupPins() called");
        calculateChipSize();
        createOutputPins();
        CirSim.console("[GEOM_PINS] Table '" + table.tableTitle + "': setupPins() completed");
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
     * Adjusted for collapsed mode
     */
    private int getExtraRowCount() {
        if (table.collapsedMode) {
            // Collapsed mode: only title, header, and computed rows
            return 2; // header + computed (title is separate)
        } else {
            // Normal mode: type + header + optional initial + computed
            return (table.showInitialValues ? 1 : 0) + 3; // type + header + computed
        }
    }
    
    /**
     * Create output pins for table columns
     * Only master columns (excluding A-L-E) are marked as outputs with voltage sources
     */
    private void createOutputPins() {
        CirSim.console("[GEOM_PINS]   Creating " + table.cols + " pins...");
        table.pins = new ChipElm.Pin[table.cols];
        
        int outputPinCount = 0;
        for (int i = 0; i < table.cols; i++) {
            String label = getOutputLabel(i);
            int pinX = calculatePinX(i);
            
            table.pins[i] = table.new Pin(pinX, ChipElm.SIDE_S, label);
            
            // Only mark as output if this column is a master (not A-L-E, not non-master)
            boolean isALE = (i == table.cols - 1 && table.cols >= 4);
            boolean isMaster = table.outputNames != null && i < table.outputNames.length &&
                              table.outputNames[i] != null && !table.outputNames[i].trim().isEmpty() &&
                              ComputedValues.isMasterTable(table.outputNames[i].trim(), table);
            
            table.pins[i].output = !isALE && isMaster;
            
            if (isALE) {
                CirSim.console("[GEOM_PINS]     Pin " + i + " '" + label + "': A-L-E column (not output)");
            } else if (isMaster) {
                outputPinCount++;
                CirSim.console("[GEOM_PINS]     Pin " + i + " '" + label + "': OUTPUT (master)");
            } else {
                CirSim.console("[GEOM_PINS]     Pin " + i + " '" + label + "': not output (not master)");
            }
        }
        CirSim.console("[GEOM_PINS]   Total output pins: " + outputPinCount + "/" + table.cols);
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
     * Must match the dimensions used in TableRenderer.draw()
     */
    private void calculateBoundingBox() {
        int cellWidthPixels = getCellWidthPixels();
        int rowDescColWidth = table.collapsedMode ? 0 : cellWidthPixels; // Hide in collapsed mode
        
        // Calculate table width (matches TableRenderer)
        int tableWidth = rowDescColWidth + table.cellSpacing + 
                        table.cols * cellWidthPixels + (table.cols + 1) * table.cellSpacing;
        
        // Calculate table height (matches TableRenderer.draw() calculations)
        // In collapsed mode: skip type row, initial values, and data rows
        int titleHeight = 10 + 10; // Title offset + space after (increased for better spacing)
        int typeRowHeight = table.collapsedMode ? 0 : (table.cellHeight + table.cellSpacing);
        int headerRowHeight = table.cellHeight + table.cellSpacing;
        int initialRowHeight = (table.showInitialValues && !table.collapsedMode) ? (table.cellHeight + table.cellSpacing) : 0;
        int dataRowsHeight = table.collapsedMode ? 0 : (table.rows * (table.cellHeight + table.cellSpacing));
        int computedRowHeight = table.cellHeight + table.cellSpacing;
        
        int tableHeight = titleHeight + typeRowHeight + headerRowHeight + 
                         initialRowHeight + dataRowsHeight + computedRowHeight;

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
        // Calculate table height to match the exact dimensions from calculateBoundingBox()
        int titleHeight = 10 + 10; // Title offset + space after (increased for better spacing)
        int typeRowHeight = table.collapsedMode ? 0 : (table.cellHeight + table.cellSpacing);
        int headerRowHeight = table.cellHeight + table.cellSpacing;
        int initialRowHeight = (table.showInitialValues && !table.collapsedMode) ? (table.cellHeight + table.cellSpacing) : 0;
        int dataRowsHeight = table.collapsedMode ? 0 : (table.rows * (table.cellHeight + table.cellSpacing));
        int computedRowHeight = table.cellHeight + table.cellSpacing;
        
        int tableHeight = titleHeight + typeRowHeight + headerRowHeight + 
                         initialRowHeight + dataRowsHeight + computedRowHeight;
        
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
