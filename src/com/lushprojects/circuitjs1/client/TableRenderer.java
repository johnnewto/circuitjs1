/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import java.util.Set;
import com.lushprojects.circuitjs1.client.TableEditDialog.ColumnType;

/**
 * TableRenderer - Handles all drawing operations for TableElm
 * Separates rendering logic from circuit simulation logic
 */
public class TableRenderer {
    private final TableElm table;
    
    public TableRenderer(TableElm table) {
        this.table = table;
    }
    
    /**
     * Static utility method to convert ColumnType enum to display string
     * Used by both TableRenderer and TableEditDialog
     */
    public static String getColumnTypeName(ColumnType type) {
        if (type == null) {
            return "Unknown";
        }
        switch (type) {
            case ASSET: return "Asset";
            case LIABILITY: return "Liability";
            case EQUITY: return "Equity";
            case A_L_E: return "A-L-E";
            default: return "Unknown";
        }
    }
    
    /**
     * Static utility method to format numeric values with specified decimal places and units
     * Used for displaying formatted values in table cells and info displays
     */
    public static String formatTableValue(double value, int decimalPlaces, String units) {
        // Format the number to the specified decimal places using GWT-compatible approach
        double multiplier = Math.pow(10, decimalPlaces);
        double rounded = Math.round(value * multiplier) / multiplier;
        String formattedValue = Double.toString(rounded);
        
        // Ensure we have the right number of decimal places
        if (formattedValue.indexOf('.') == -1) {
            formattedValue += ".";
        }
        
        int dotIndex = formattedValue.indexOf('.');
        int currentDecimals = formattedValue.length() - dotIndex - 1;
        
        // Pad with zeros if needed
        while (currentDecimals < decimalPlaces) {
            formattedValue += "0";
            currentDecimals++;
        }
        
        // Truncate if too many decimals
        if (currentDecimals > decimalPlaces) {
            formattedValue = formattedValue.substring(0, dotIndex + decimalPlaces + 1);
        }
        
        return formattedValue + units;
    }
    
    /**
     * Main draw method - orchestrates all drawing operations
     */
    public void draw(Graphics g) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        
        // Calculate the actual table height by accumulating all components
        int titleHeight = 10 + 5; // Title offset + space after
        int typeRowHeight = table.cellHeight + table.cellSpacing;
        int headerRowHeight = table.cellHeight + table.cellSpacing;
        int initialRowHeight = table.showInitialValues ? (table.cellHeight + table.cellSpacing) : 0;
        int dataRowsHeight = table.rows * (table.cellHeight + table.cellSpacing);
        int computedRowHeight = table.cellHeight + table.cellSpacing;
        
        int tableWidth = rowDescColWidth + table.cellSpacing + table.cols * cellWidthPixels + (table.cols + 1) * table.cellSpacing;
        int tableHeight = titleHeight + typeRowHeight + headerRowHeight + initialRowHeight + dataRowsHeight + computedRowHeight;

        // Draw table background
        g.setColor(table.needsHighlight() ? CircuitElm.selectColor : Color.white);
        g.fillRect(tableX, tableY, tableWidth, tableHeight);

        // Draw table border
        g.setColor(Color.black);
        g.drawRect(tableX, tableY, tableWidth, tableHeight);

        // Draw components in order with consistent positioning
        int currentY = 10; // Start position after table border
        
        // 1. Draw title
        drawTitle(g, currentY);
        currentY += 5; // Space after title
        
        // 2. Draw column type row
        drawColumnTypeRow(g, currentY);
        currentY += table.cellHeight + table.cellSpacing; // Move down by row height
        
        // 3. Draw column headers
        drawColumnHeaders(g, currentY);
        currentY += table.cellHeight + table.cellSpacing; // Move down by row height
        
        // 4. Draw initial conditions row if enabled
        if (table.showInitialValues) {
            drawInitialConditionsRow(g, currentY);
            currentY += table.cellHeight + table.cellSpacing;
        }

        // 5. Draw table cells with voltages (includes row descriptions)
        drawTableCells(g, currentY);
        currentY += table.rows * (table.cellHeight + table.cellSpacing);

        // 6. Draw computed row
        drawSumRow(g, currentY);
        currentY += table.cellHeight + table.cellSpacing;

        // 7. Draw chip pins and posts at the bottom
        drawPins(g);
    }
    
    private void drawTitle(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        
        // Draw title centered at top of table
        g.setColor(Color.black);
        int rowDescColWidth = cellWidthPixels;
        int tableWidth = rowDescColWidth + table.cellSpacing + table.cols * cellWidthPixels + (table.cols + 1) * table.cellSpacing;
        int titleY = tableY + offsetY;
        table.drawCenteredText(g, table.tableTitle, tableX + tableWidth / 2, titleY, true);
    }

    private void drawColumnHeaders(Graphics g, int offsetY) {
        g.setColor(Color.black);
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int headerY = tableY + offsetY;
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;

        // Get shared stocks for highlighting
        Set<String> sharedStocks = StockFlowRegistry.getSharedStocks();

        // Draw row description column header cell text
        int rowDescHeaderX = tableX + table.cellSpacing;
        table.drawCenteredText(g, "Flows↓/Stocks→", rowDescHeaderX + rowDescColWidth/2, headerY + table.cellHeight/2, true);

        // Draw data column header cells text
        for (int col = 0; col < table.cols; col++) {
            int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            
            String header = (table.outputNames != null && col < table.outputNames.length) ?
                           table.outputNames[col] : "Stock" + (col + 1);
            
            // Highlight shared stocks with yellow background
            if (sharedStocks.contains(header)) {
                g.setColor(new Color(255, 255, 200)); // Light yellow
                g.fillRect(cellX, headerY, cellWidthPixels, table.cellHeight);
            }
            
            // Check if this table is the master for this output name and add star prefix
            String displayHeader = header;
            if (header != null && !header.isEmpty()) {
                boolean isMaster = ComputedValues.isMasterTable(header, table);
                if (isMaster) {
                    displayHeader = "★" + header;
                }
            }
            
            // Draw header text
            g.setColor(Color.black);
            table.drawCenteredText(g, displayHeader, cellX + cellWidthPixels/2, headerY + table.cellHeight/2, true);
        }
        
        // Draw grid lines for this row
        drawRowGridLines(g, offsetY, tableX, rowDescColWidth, cellWidthPixels);
    }
    
    private void drawColumnTypeRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        int typeRowY = tableY + offsetY;
        
        // Draw row description column cell text
        g.setColor(Color.black);
        table.drawCenteredText(g, "Type", tableX + table.cellSpacing + rowDescColWidth/2, typeRowY + table.cellHeight/2, true);
        
        // Draw column type cells text
        for (int col = 0; col < table.cols; col++) {
            int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            
            // Draw column type text
            ColumnType type = table.getColumnType(col);
            String typeName = getColumnTypeName(type);
            table.drawCenteredText(g, typeName, cellX + cellWidthPixels/2, typeRowY + table.cellHeight/2, true);
        }
        
        // Draw grid lines for this row
        drawRowGridLines(g, offsetY, tableX, rowDescColWidth, cellWidthPixels);
    }

    private void drawInitialConditionsRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        int initialRowY = tableY + offsetY;
        
        // Draw row description cell for initial conditions
        g.setColor(Color.black);
        table.drawCenteredText(g, "Initial", tableX + table.cellSpacing + rowDescColWidth/2, initialRowY + table.cellHeight/2, true);

        for (int col = 0; col < table.cols; col++) {
            int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            
            // Get initial conditions value for this column
            double initialValue = (table.initialValues != null && col < table.initialValues.length) ? 
                                 table.initialValues[col] : 0.0;
            
            // Draw initial conditions cell background - always white
            g.setColor(Color.white);
            g.fillRect(cellX, initialRowY, cellWidthPixels, table.cellHeight);
            
            // Draw value with text color based on voltage
            g.setColor(table.getVoltageColor(g, initialValue));
            String voltageText = formatTableValue(initialValue, table.decimalPlaces, table.tableUnits);
            table.drawCenteredText(g, voltageText, cellX + cellWidthPixels/2, initialRowY + table.cellHeight/2, true);
        }
        
        // Draw grid lines for this row
        drawRowGridLines(g, offsetY, tableX, rowDescColWidth, cellWidthPixels);
    }

    private void drawTableCells(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;

        // Use the passed offsetY directly - no need to recalculate
        int baseY = offsetY;

        for (int row = 0; row < table.rows; row++) {
            int cellY = tableY + baseY + row * (table.cellHeight + table.cellSpacing);
            
            // Draw row description text
            g.setColor(Color.black);
            String rowDesc = (table.rowDescriptions != null && row < table.rowDescriptions.length) ?
                            table.rowDescriptions[row] : "Row" + (row + 1);
            table.drawCenteredText(g, rowDesc, tableX + table.cellSpacing + rowDescColWidth/2, cellY + table.cellHeight/2, true);
            
            // Draw data cells for this row
            for (int col = 0; col < table.cols; col++) {
                int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
                
                // Get voltage using equation evaluation
                double voltage = table.getVoltageForCell(row, col);
                
                // Draw cell background - always white
                g.setColor(Color.white);
                g.fillRect(cellX, cellY, cellWidthPixels, table.cellHeight);
                
                // Display equation and voltage in cell (only if equation is not empty)
                String equation = table.cellEquations[row][col];
                if (equation != null && !equation.trim().isEmpty()) {
                    g.setColor(table.getVoltageColor(g, voltage));
                    String displayText = truncateEquation(equation);
                    String voltageText = formatTableValue(voltage, table.decimalPlaces, table.tableUnits);
                    String combinedText = displayText + " = " + voltageText;
                    table.drawCenteredText(g, combinedText, cellX + cellWidthPixels/2, cellY + table.cellHeight/2, true);
                }
            }
            
            // Draw grid lines for this row
            int rowY = tableY + baseY + row * (table.cellHeight + table.cellSpacing);
            drawRowGridLines(g, rowY - tableY, tableX, rowDescColWidth, cellWidthPixels);
        }
    }
    
    /**
     * Truncate long equations for display
     */
    private String truncateEquation(String equation) {
        int maxLength = 8;
        if (equation.length() > maxLength) {
            return equation.substring(0, maxLength - 2) + "..";
        }
        return equation;
    }

    private void drawSumRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;

        // Use the passed offsetY directly - no need to recalculate
        int sumRowY = tableY + offsetY;

        // Draw row description text for computed row
        g.setColor(Color.black);
        table.drawCenteredText(g, "Computed", tableX + table.cellSpacing + rowDescColWidth/2, sumRowY + table.cellHeight/2, true);

        for (int col = 0; col < table.cols; col++) {
            int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            
            // Get the already-calculated sum from computed values (calculated in doStep())
            String sumLabelName = table.outputNames[col];
            Double computedSum = ComputedValues.getComputedValue(sumLabelName);
            double computedValue = (computedSum != null) ? computedSum.doubleValue() : 0.0;
            
            // Draw sum cell background - always white
            g.setColor(Color.white);
            g.fillRect(cellX, sumRowY, cellWidthPixels, table.cellHeight);
            
            // Draw column name and value with text color based on voltage
            g.setColor(table.getVoltageColor(g, computedValue));
            String sumText = formatTableValue(computedValue, table.decimalPlaces, table.tableUnits);
            String combinedText = sumLabelName + ": " + sumText;
            table.drawCenteredText(g, combinedText, cellX + cellWidthPixels/2, sumRowY + table.cellHeight/2, true);
        }
        
        // Draw grid lines for computed row
        drawRowGridLines(g, offsetY, tableX, rowDescColWidth, cellWidthPixels);
    }

    /**
     * Draw grid lines for a single row
     */
    private void drawRowGridLines(Graphics g, int offsetY, int tableX, int rowDescColWidth, int cellWidthPixels) {
        int tableY = table.getTableY();
        int rowY = tableY + offsetY;
        
        g.setColor(table.needsHighlight() ? CircuitElm.selectColor : Color.black);
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
        // Between and after data columns
        for (int col = 0; col <= table.cols; col++) {
            x = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            g.drawLine(x, rowY, x, rowY + table.cellHeight);
        }
    }
    
    private void drawPins(Graphics g) {
        // Draw pins on the bottom edge
        for (int i = 0; i < table.getPostCount(); i++) {
            ChipElm.Pin p = table.pins[i];
            table.setVoltageColor(g, table.volts[i]);
            Point a = p.post;
            Point b = p.stub;
            CircuitElm.drawThickLine(g, a, b);
            p.curcount = table.updateDotCount(p.current, p.curcount);
            table.drawDots(g, b, a, p.curcount);
        }
        table.drawPosts(g);
    }
}
