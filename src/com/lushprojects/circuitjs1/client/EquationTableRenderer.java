/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.util.Locale;

/**
 * EquationTableRenderer - Handles all drawing operations for EquationTableElm
 * Separates rendering logic from circuit simulation logic
 * 
 * Similar pattern to SFCTableRenderer which extends TableRenderer,
 * this class handles the visual presentation of the equation table.
 */
public class EquationTableRenderer {
    private final EquationTableElm table;
    
    // Fonts for different parts of the table
    private Font labelFont;
    private Font valueFont;
    
    public EquationTableRenderer(EquationTableElm table) {
        this.table = table;
    }
    
    //=============================================================================
    // FONT MANAGEMENT
    //=============================================================================
    
    /**
     * Update fonts based on current display size.
     * Called when the table size changes.
     */
    public void updateFonts(int opsize) {
        labelFont = new Font("SansSerif", 0, opsize == 2 ? 12 : 10);
        valueFont = new Font("SansSerif", 0, opsize == 2 ? 10 : 8);
    }
    
    //=============================================================================
    // MAIN DRAWING
    //=============================================================================
    
    /**
     * Draw the table element.
     * Renders the table background, title, rows, and hover tooltips.
     */
    public void draw(Graphics g) {
        int tableX = table.x;
        int tableY = table.y;
        boolean selected = table.needsHighlight();
        
        // Draw table background
        g.setColor(Color.darkGray);
        g.fillRect(tableX, tableY, table.getTableWidth(), table.getTableHeight());
        
        // Draw border (highlighted when selected)
        drawTableBorder(g, tableX, tableY, selected);
        
        // Draw title row
        drawTitleRow(g, tableX, tableY);
        
        // Update hover state
        updateHoveredRow(tableX, tableY);
        
        // Draw data rows
        g.setFont(valueFont);
        for (int row = 0; row < table.getRowCount(); row++) {
            drawDataRow(g, tableX, tableY, row);
        }
        
        // Draw tooltip for hovered row
        drawHoverTooltip(g, tableX);
        
        // Update bounding box
        table.setBbox(tableX, tableY, tableX + table.getTableWidth(), tableY + table.getTableHeight());
    }
    
    /**
     * Draw the table border, with highlighting when selected.
     */
    private void drawTableBorder(Graphics g, int tableX, int tableY, boolean selected) {
        g.setColor(selected ? CircuitElm.selectColor : Color.gray);
        g.drawRect(tableX, tableY, table.getTableWidth(), table.getTableHeight());
        if (selected) {
            g.drawRect(tableX + 1, tableY + 1, table.getTableWidth() - 2, table.getTableHeight() - 2);
        }
    }
    
    /**
     * Draw the title row with table name.
     */
    private void drawTitleRow(Graphics g, int tableX, int tableY) {
        g.setFont(labelFont);
        g.setColor(CircuitElm.whiteColor);
        int titleY = tableY + table.getRowHeight() - table.getCellPadding();
        table.drawCenteredText(g, table.getTableName(), tableX + table.getTableWidth() / 2, titleY - 2, true);
        
        // Separator line after title
        g.setColor(Color.gray);
        g.drawLine(tableX, tableY + table.getRowHeight(), tableX + table.getTableWidth(), tableY + table.getRowHeight());
    }
    
    /**
     * Update which row the mouse is hovering over.
     */
    private void updateHoveredRow(int tableX, int tableY) {
        table.setHoveredRow(-1);
        int mouseCircuitX = table.sim.inverseTransformX(table.sim.mouseCursorX);
        int mouseCircuitY = table.sim.inverseTransformY(table.sim.mouseCursorY);
        
        if (mouseCircuitX >= tableX && mouseCircuitX <= tableX + table.getTableWidth() &&
            mouseCircuitY >= tableY && mouseCircuitY <= tableY + table.getTableHeight()) {
            // Calculate row index, accounting for title row
            int relativeY = mouseCircuitY - (tableY + table.getRowHeight());
            if (relativeY >= 0) {
                int mouseRowIndex = relativeY / table.getRowHeight();
                if (mouseRowIndex >= 0 && mouseRowIndex < table.getRowCount()) {
                    table.setHoveredRow(mouseRowIndex);
                }
            }
        }
    }
    
    /**
     * Draw a single data row.
     */
    private void drawDataRow(Graphics g, int tableX, int tableY, int row) {
        int rowY = tableY + (row + 1) * table.getRowHeight();
        int rowHeight = table.getRowHeight();
        int cellPadding = table.getCellPadding();
        int tableWidth = table.getTableWidth();
        
        // Highlight hovered row
        if (row == table.getHoveredRow()) {
            g.setColor(new Color(80, 80, 100));
            g.fillRect(tableX + 1, rowY + 1, tableWidth - 2, rowHeight - 1);
        }
        
        // Build display equation with slider value substituted
        String displayEquation = buildDisplayEquation(row);
        String rowText = table.getOutputName(row) + " = " + displayEquation;
        
        // Draw row text
        g.setColor(CircuitElm.whiteColor);
        g.drawString(rowText, tableX + cellPadding, rowY + rowHeight - cellPadding - 2);
        
        // Draw current value on right side
        String valueText = CircuitElm.getShortUnitText(table.getOutputValue(row), "");
        int valueWidth = (int) g.context.measureText(valueText).getWidth();
        g.setColor(Color.cyan);
        g.drawString(valueText, tableX + tableWidth - valueWidth - cellPadding, rowY + rowHeight - cellPadding - 2);
        
        // Draw initial value indicator if present
        drawInitialValueIndicator(g, tableX, rowY, row, valueWidth);
        
        // Draw row separator
        if (row < table.getRowCount() - 1) {
            g.setColor(Color.gray);
            int sepY = tableY + (row + 2) * rowHeight;
            g.drawLine(tableX, sepY, tableX + tableWidth, sepY);
        }
    }
    
    /**
     * Build the display equation string with slider variable substituted.
     */
    private String buildDisplayEquation(int row) {
        String displayEquation = table.getEquation(row);
        displayEquation = Locale.convertGreekSymbols(displayEquation);
        
        // Substitute slider variable with its value
        String sliderVar = table.getSliderVarName(row);
        if (sliderVar != null && !sliderVar.isEmpty()) {
            String valueStr = CircuitElm.getShortUnitText(table.getSliderValue(row), "");
            displayEquation = displayEquation.replaceAll("\\b" + sliderVar + "\\b", valueStr);
        }
        
        return displayEquation;
    }
    
    /**
     * Draw the initial value indicator for a row (shown in yellow brackets).
     */
    private void drawInitialValueIndicator(Graphics g, int tableX, int rowY, int row, int valueWidth) {
        String initEq = table.getInitialEquation(row);
        if (initEq == null || initEq.trim().isEmpty()) {
            return;
        }
        
        int opsize = table.getOpsize();
        Font smallFont = new Font("SansSerif", 0, opsize == 2 ? 8 : 7);
        g.setFont(smallFont);
        String initText = "[" + initEq + "]";
        int initWidth = (int) g.context.measureText(initText).getWidth();
        g.setColor(Color.yellow);
        g.drawString(initText, tableX + table.getTableWidth() - valueWidth - initWidth - table.getCellPadding() * 2, 
                     rowY + table.getRowHeight() - table.getCellPadding() - 2);
        g.setFont(valueFont);
    }
    
    /**
     * Draw tooltip for hovered row if a hint is available.
     */
    private void drawHoverTooltip(Graphics g, int tableX) {
        int hoveredRow = table.getHoveredRow();
        if (hoveredRow < 0 || hoveredRow >= table.getRowCount()) {
            return;
        }
        
        String hint = HintRegistry.getHint(table.getOutputName(hoveredRow));
        if (hint == null || hint.trim().isEmpty()) {
            return;
        }
        
        int mouseCircuitX = table.sim.inverseTransformX(table.sim.mouseCursorX);
        int mouseCircuitY = table.sim.inverseTransformY(table.sim.mouseCursorY);
        
        g.setFont(valueFont);
        int hintWidth = (int) g.context.measureText(hint).getWidth() + 8;
        int hintHeight = table.getOpsize() == 1 ? 12 : 16;
        
        // Position above the mouse cursor
        int tooltipX = mouseCircuitX - hintWidth / 2;
        int tooltipY = mouseCircuitY - hintHeight - 4;
        
        // Keep tooltip within table bounds horizontally
        tooltipX = Math.max(tooltipX, tableX);
        tooltipX = Math.min(tooltipX, tableX + table.getTableWidth() - hintWidth);
        
        // Draw tooltip background
        g.setColor(new Color(60, 60, 80));
        g.fillRect(tooltipX, tooltipY, hintWidth, hintHeight);
        g.setColor(Color.gray);
        g.drawRect(tooltipX, tooltipY, hintWidth, hintHeight);
        
        // Draw tooltip text
        g.setColor(Color.yellow);
        g.drawString(hint, tooltipX + 4, tooltipY + hintHeight - 3);
    }
}
