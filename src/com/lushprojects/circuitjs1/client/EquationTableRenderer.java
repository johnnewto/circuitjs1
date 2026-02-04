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
 * Uses the same modern color scheme as TableRenderer for consistency.
 */
public class EquationTableRenderer {
    private final EquationTableElm table;
    
    // Fonts for different parts of the table
    private Font labelFont;
    private Font valueFont;
    
    // Modern styling configuration (matches TableRenderer)
    private static final boolean MODERN_STYLE = true;
    private static final int CORNER_RADIUS = 8;
    
    // Dark mode colors (matches TableRenderer)
    private static final Color HEADER_BG_DARK = new Color(55, 55, 75);
    private static final Color ROW_EVEN_BG_DARK = new Color(45, 45, 50);
    private static final Color ROW_ODD_BG_DARK = new Color(35, 35, 40);
    private static final Color TABLE_BG_DARK = new Color(30, 30, 35);
    private static final Color GRID_LINE_DARK = new Color(60, 60, 65);
    
    // Light mode colors (matches TableRenderer)
    private static final Color HEADER_BG_LIGHT = new Color(235, 235, 245);
    private static final Color ROW_EVEN_BG_LIGHT = new Color(248, 248, 252);
    private static final Color ROW_ODD_BG_LIGHT = new Color(255, 255, 255);
    private static final Color TABLE_BG_LIGHT = new Color(255, 255, 255);
    private static final Color GRID_LINE_LIGHT = new Color(220, 220, 230);
    
    public EquationTableRenderer(EquationTableElm table) {
        this.table = table;
    }
    
    // Helper methods for theme-aware colors
    private boolean isPrintable() {
        return CirSim.theSim.printableCheckItem.getState();
    }
    
    private Color getHeaderBgColor() {
        return isPrintable() ? HEADER_BG_LIGHT : HEADER_BG_DARK;
    }
    
    private Color getRowBgColor(int row) {
        if (isPrintable()) {
            return (row % 2 == 0) ? ROW_EVEN_BG_LIGHT : ROW_ODD_BG_LIGHT;
        }
        return (row % 2 == 0) ? ROW_EVEN_BG_DARK : ROW_ODD_BG_DARK;
    }
    
    private Color getTableBgColor() {
        return isPrintable() ? TABLE_BG_LIGHT : TABLE_BG_DARK;
    }
    
    private Color getGridLineColor() {
        return isPrintable() ? GRID_LINE_LIGHT : GRID_LINE_DARK;
    }
    
    private Color getTextColor() {
        if (MODERN_STYLE && isPrintable()) {
            return new Color(30, 30, 30); // Dark text for light backgrounds
        }
        return CircuitElm.whiteColor;
    }
    
    /**
     * Get voltage color for value display - red/green based on voltage.
     * Matches TableRenderer.getTextVoltageColor() behavior.
     */
    private Color getVoltageColor(double volts) {
        if (!CirSim.theSim.voltsCheckItem.getState()) {
            return CircuitElm.whiteColor;
        }
        int c = (int) ((volts + CircuitElm.voltageRange) * (CircuitElm.colorScaleCount - 1) /
                       (CircuitElm.voltageRange * 2));
        if (c < 0)
            c = 0;
        if (c >= CircuitElm.colorScaleCount)
            c = CircuitElm.colorScaleCount - 1;
        return CircuitElm.colorScale[c];
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
     * Uses modern styling matching TableRenderer.
     */
    public void draw(Graphics g) {
        int tableX = table.x;
        int tableY = table.y;
        boolean selected = table.needsHighlight();
        int tableWidth = table.getTableWidth();
        int tableHeight = table.getTableHeight();
        int rowHeight = table.getRowHeight();
        
        // Draw table background with rounded corners
        if (MODERN_STYLE) {
            g.setColor(getTableBgColor());
            g.fillRoundRect(tableX + 1, tableY + 1, tableWidth - 2, tableHeight - 2, CORNER_RADIUS);
        } else {
            g.setColor(Color.darkGray);
            g.fillRect(tableX, tableY, tableWidth, tableHeight);
        }
        
        // Draw row backgrounds (zebra striping)
        if (MODERN_STYLE) {
            drawRowBackgrounds(g, tableX, tableY, tableWidth, rowHeight);
        }
        
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
        
        // Note: Tooltip is now drawn centrally by CirSim.drawHintTooltip()
        
        // Update bounding box
        table.setBbox(tableX, tableY, tableX + tableWidth, tableY + tableHeight);
    }
    
    /**
     * Draw row backgrounds with zebra striping and header styling.
     */
    private void drawRowBackgrounds(Graphics g, int tableX, int tableY, int tableWidth, int rowHeight) {
        // Title row background (header style)
        g.setColor(getHeaderBgColor());
        g.fillRect(tableX + 2, tableY + 2, tableWidth - 4, rowHeight - 2);
        
        // Data rows with zebra striping
        for (int row = 0; row < table.getRowCount(); row++) {
            int rowY = tableY + (row + 1) * rowHeight;
            g.setColor(getRowBgColor(row));
            g.fillRect(tableX + 2, rowY, tableWidth - 4, rowHeight);
        }
    }
    
    /**
     * Draw the table border, with highlighting when selected.
     * Uses modern rounded corners when MODERN_STYLE is enabled.
     */
    private void drawTableBorder(Graphics g, int tableX, int tableY, boolean selected) {
        if (MODERN_STYLE) {
            g.setColor(selected ? CircuitElm.selectColor : getGridLineColor());
            g.drawRoundRect(tableX, tableY, table.getTableWidth(), table.getTableHeight(), CORNER_RADIUS);
            if (selected) {
                g.drawRoundRect(tableX + 1, tableY + 1, table.getTableWidth() - 2, table.getTableHeight() - 2, CORNER_RADIUS);
            }
        } else {
            g.setColor(selected ? CircuitElm.selectColor : Color.gray);
            g.drawRect(tableX, tableY, table.getTableWidth(), table.getTableHeight());
            if (selected) {
                g.drawRect(tableX + 1, tableY + 1, table.getTableWidth() - 2, table.getTableHeight() - 2);
            }
        }
    }
    
    /**
     * Draw the title row with table name.
     */
    private void drawTitleRow(Graphics g, int tableX, int tableY) {
        g.setFont(labelFont);
        g.setColor(getTextColor());
        int titleY = tableY + table.getRowHeight() - table.getCellPadding();
        table.drawCenteredText(g, table.getTableName(), tableX + table.getTableWidth() / 2, titleY - 2, true);
        
        // Separator line after title
        g.setColor(getGridLineColor());
        g.drawLine(tableX, tableY + table.getRowHeight(), tableX + table.getTableWidth(), tableY + table.getRowHeight());
    }
    
    /**
     * Update which row the mouse is hovering over.
     */
    private void updateHoveredRow(int tableX, int tableY) {
        int newHoveredRow = -1;
        int mouseCircuitX = table.sim.inverseTransformX(table.sim.mouseCursorX);
        int mouseCircuitY = table.sim.inverseTransformY(table.sim.mouseCursorY);
        
        if (mouseCircuitX >= tableX && mouseCircuitX <= tableX + table.getTableWidth() &&
            mouseCircuitY >= tableY && mouseCircuitY <= tableY + table.getTableHeight()) {
            // Calculate row index, accounting for title row
            int relativeY = mouseCircuitY - (tableY + table.getRowHeight());
            if (relativeY >= 0) {
                int mouseRowIndex = relativeY / table.getRowHeight();
                if (mouseRowIndex >= 0 && mouseRowIndex < table.getRowCount()) {
                    newHoveredRow = mouseRowIndex;
                }
            }
        }
        
        // Only update if changed to avoid cursor flickering
        if (newHoveredRow != table.getHoveredRow()) {
            table.setHoveredRow(newHoveredRow);
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
        boolean isHovered = (row == table.getHoveredRow());
        
        // Highlight hovered row (slightly brighter than zebra stripe)
        if (isHovered) {
            Color hoverColor = isPrintable() ? new Color(220, 220, 240) : new Color(65, 65, 85);
            g.setColor(hoverColor);
            g.fillRect(tableX + 1, rowY + 1, tableWidth - 2, rowHeight - 1);
        }
        
        // Build display equation with slider value substituted
        String displayEquation = buildDisplayEquation(row);
        String outputName = Locale.convertGreekSymbols(table.getOutputName(row));
        String rowText = outputName + " = " + displayEquation;
        
        // Draw scroll icon on adjustable rows (numeric equations) to indicate mouse wheel adjustment
        int textX = tableX + cellPadding;
        boolean isAdjustable = table.isAdjustableRow(row);
        if (isAdjustable) {
            // Use larger bold font for icon
            int iconSize = table.getOpsize() == 1 ? 12 : 16;
            g.setFont(new Font("SansSerif", Font.BOLD, iconSize));
            g.setColor(isHovered ? new Color(0, 100, 200) : new Color(0, 60, 140));  // Dark blue, darker on hover
            g.drawString("↕", textX, rowY + rowHeight - cellPadding - 1);
            int iconWidth = (int) g.context.measureText("↕ ").getWidth();
            g.setFont(valueFont);  // Restore to valueFont (what drawDataRow uses)
            textX += iconWidth;
        }
        
        // Draw row text
        g.setColor(getTextColor());
        g.drawString(rowText, textX, rowY + rowHeight - cellPadding - 2);
        
        // Draw current value on right side with voltage coloring
        double outputValue = table.getOutputValue(row);
        String valueText = CircuitElm.getShortUnitText(outputValue, "");
        int valueWidth = (int) g.context.measureText(valueText).getWidth();
        g.setColor(getVoltageColor(outputValue));
        g.drawString(valueText, tableX + tableWidth - valueWidth - cellPadding, rowY + rowHeight - cellPadding - 2);
        
        // Draw initial value indicator if present
        drawInitialValueIndicator(g, tableX, rowY, row, valueWidth);
        
        // Draw row separator
        if (row < table.getRowCount() - 1) {
            g.setColor(getGridLineColor());
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
        g.setColor(getTextColor());
        g.drawString(initText, tableX + table.getTableWidth() - valueWidth - initWidth - table.getCellPadding() * 2, 
                     rowY + table.getRowHeight() - table.getCellPadding() - 2);
        g.setFont(valueFont);
    }
}
