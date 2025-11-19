/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.TableEditDialog.ColumnType;

/**
 * TableRenderer - Handles all drawing operations for TableElm
 * Separates rendering logic from circuit simulation logic
 */
public class TableRenderer {
    protected final TableElm table;  // Protected to allow subclass access
    
    // Cache for cell values to avoid recalculating every frame
    protected double[][] cachedCellValues;
    protected double[] cachedSumValues;
    protected long lastUpdateTime = 0;  // Timestamp of last cache update
    protected static final long UPDATE_INTERVAL_MS = 500; // Update twice per second
    
    // Fonts for different parts of the table - all bold for better readability
    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 13);
    private static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 11);
    private static final Font CELL_FONT = new Font("SansSerif", Font.BOLD, 11);
    private static final String LETTER_SPACING = "0.5px"; // For better readability
    
    public TableRenderer(TableElm table) {
        this.table = table;
    }
    
    /**
     * Get cached A-L-E cell value for display
     * Returns 0.0 if cache not initialized or out of bounds
     * Package-private for TableElm access
     */
    double getCachedALECellValue(int row, int aleColumnIndex) {
        if (cachedCellValues != null && 
            row >= 0 && row < cachedCellValues.length &&
            aleColumnIndex >= 0 && aleColumnIndex < cachedCellValues[row].length) {
            return cachedCellValues[row][aleColumnIndex];
        }
        return 0.0;
    }
    
    /**
     * Get cached sum value (computed row) for a column
     * Returns 0.0 if cache not initialized or out of bounds
     * Package-private for TableElm access
     */
    double getCachedSumValue(int col) {
        if (cachedSumValues != null && col >= 0 && col < cachedSumValues.length) {
            return cachedSumValues[col];
        }
        return 0.0;
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
            default: return "Unknown";
        }
        // Note: A_L_E is detected positionally (last column when cols >= 4), not by type
    }
    
    /**
     * Static utility method to format numeric values with specified decimal places and units
     * Used for displaying formatted values in table cells and info displays
     * 
     * NOTE: This method is deprecated in favor of using CircuitElm.getUnitText() directly
     * which provides proper SI unit prefixes (p, n, μ, m, k, M, G)
     */
    @Deprecated
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
     * Get voltage color for text display - bypasses highlight coloring
     * so text stays readable even when table is highlighted
     */
    private Color getTextVoltageColor(double volts) {
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
    
    /**
     * Main draw method - orchestrates all drawing operations
     */
    public void draw(Graphics g) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = table.collapsedMode ? 0 : cellWidthPixels; // Hide row description column in collapsed mode
        
        // Calculate the actual table height by accumulating all components
        // In collapsed mode: skip type row, initial values, and data rows
        int titleHeight = 10 + 10; // Title offset + space after (increased for better spacing)
        int typeRowHeight = table.collapsedMode ? 0 : (table.cellHeight + table.cellSpacing);
        int headerRowHeight = table.cellHeight + table.cellSpacing;
        int initialRowHeight = (table.showInitialValues && !table.collapsedMode) ? (table.cellHeight + table.cellSpacing) : 0;
        int dataRowsHeight = table.collapsedMode ? 0 : (table.rows * (table.cellHeight + table.cellSpacing));
        int computedRowHeight = table.cellHeight + table.cellSpacing;
        
        int tableWidth = rowDescColWidth + table.cellSpacing + table.cols * cellWidthPixels + (table.cols + 1) * table.cellSpacing;
        int tableHeight = titleHeight + typeRowHeight + headerRowHeight + initialRowHeight + dataRowsHeight + computedRowHeight;

        // Update cached values if enough time has passed (500ms = twice per second)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS || cachedCellValues == null) {
            updateCachedValues();
            lastUpdateTime = currentTime;
        }

        // Draw light gray background for entire table
        // Use a very light gray that works with both black and white backgrounds
        // RGB(230, 230, 230) for white background mode, RGB(40, 40, 40) for black background mode
        Color bgColor = CirSim.theSim.printableCheckItem.getState() ? 
            new Color(230, 230, 230) : new Color(40, 40, 40);
        g.setColor(bgColor);
        g.fillRect(tableX + 1, tableY + 1, tableWidth - 2, tableHeight - 2);
        
        // Draw table border
        // Use blue border if non-converged to make the condition visually distinct
        g.setColor(table.nonConverged ? Color.blue : CircuitElm.lightGrayColor);
        g.drawRect(tableX, tableY, tableWidth, tableHeight);

        // Draw components in order with consistent positioning
        int currentY = 10; // Start position after table border
        
        // 1. Draw title
        drawTitle(g, currentY);
        currentY += 10; // Space after title (increased for better spacing)
        
        // 2. Draw column type row (skip in collapsed mode)
        if (!table.collapsedMode) {
            drawColumnTypeRow(g, currentY);
            currentY += table.cellHeight + table.cellSpacing; // Move down by row height
        }
        
        // 3. Draw column headers
        drawColumnHeaders(g, currentY);
        currentY += table.cellHeight + table.cellSpacing; // Move down by row height
        
        // 4. Draw initial conditions row if enabled (skip in collapsed mode)
        if (table.showInitialValues && !table.collapsedMode) {
            drawInitialConditionsRow(g, currentY);
            currentY += table.cellHeight + table.cellSpacing;
        }

        // 5. Draw table cells with voltages (skip in collapsed mode)
        if (!table.collapsedMode) {
            // Use cached values for drawing
            drawTableCells(g, currentY);
            currentY += table.rows * (table.cellHeight + table.cellSpacing);
        }

        // 6. Draw computed row
        drawSumRow(g, currentY);
        currentY += table.cellHeight + table.cellSpacing;

        // 7. Draw chip pins and posts at the bottom
        drawPins(g);
    }
    
    /**
     * Update cached cell and sum values from the table
     * Called automatically when UPDATE_INTERVAL_MS has elapsed (500ms = twice per second)
     * 
     * SYNCHRONIZED DISPLAY ARCHITECTURE:
     * - Cell values: Each table evaluates its own equations (allows different formulas)
     * - Column sums: Non-master columns display the master's computed sum
     * - ALE values: Calculated inline during drawing (not cached)
     * 
     * This allows tables to have independent cell-level calculations while
     * showing synchronized stock totals in the "Computed" row.
     */
    protected void updateCachedValues() {
        // Initialize cache arrays if needed
        if (cachedCellValues == null || cachedCellValues.length != table.rows || 
            (cachedCellValues.length > 0 && cachedCellValues[0].length != table.cols)) {
            cachedCellValues = new double[table.rows][table.cols];
        }
        if (cachedSumValues == null || cachedSumValues.length != table.cols) {
            cachedSumValues = new double[table.cols];
        }
        
        // Update cell values for regular (non-ALE) columns only
        // ALE values are calculated inline during drawing
        int regularColCount = getRegularColumnCount();
        for (int row = 0; row < table.rows; row++) {
            for (int col = 0; col < regularColCount; col++) {
                cachedCellValues[row][col] = table.getVoltageForCell(row, col);
            }
        }
        
        // Update column sum values (computed row)
        // IMPORTANT: Process regular columns FIRST, then ALE column
        // This ensures ALE can use the already-calculated regular column sums
        
        // Step 1: Calculate all regular (non-ALE) column sums
        for (int col = 0; col < regularColCount; col++) {
            cachedSumValues[col] = getRegularColumnSum(col);
        }
        
        // Step 2: Calculate ALE column sum using the already-calculated regular column sums
        if (hasALEColumn()) {
            int aleCol = table.cols - 1;
            double totalAssets = 0.0, totalLiabilities = 0.0, totalEquity = 0.0;
            
            // Use the CACHED sum values that were just calculated
            for (int c = 0; c < regularColCount; c++) {
                double columnTotal = cachedSumValues[c]; // Use cached value, not getComputedValueForDisplay
                ColumnType type = getColumnType(c);
                if (type == ColumnType.ASSET) totalAssets += columnTotal;
                else if (type == ColumnType.LIABILITY) totalLiabilities += columnTotal;
                else if (type == ColumnType.EQUITY) totalEquity += columnTotal;
            }
            cachedSumValues[aleCol] = totalAssets - totalLiabilities - totalEquity;
        }
    }
    
    /**
     * Get cached cell value, handling bounds checking
     * @param row Row index
     * @param col Column index
     * @return Cached cell value or 0.0 if out of bounds
     */
    private double getCachedCellValue(int row, int col) {
        if (cachedCellValues != null && row >= 0 && row < cachedCellValues.length &&
            col >= 0 && col < cachedCellValues[row].length) {
            return cachedCellValues[row][col];
        }
        return 0.0;
    }
    
    /**
     * Get sum for a regular (non-ALE) column
     * Master tables use their own computed values, non-master tables fetch from ComputedValues
     */
    private double getRegularColumnSum(int col) {
        if (table.isMasterForColumn(col)) {
            // Master column: use our own computed value
            return table.getComputedValueForDisplay(col);
        } else {
            // Non-master column: fetch master's computed sum
            String stockName = getColumnStockName(col);
            if (stockName != null) {
                Double masterSum = ComputedValues.getComputedValue(stockName);
                return (masterSum != null) ? masterSum : 0.0;
            }
            return 0.0;
        }
    }
    
    // Helper methods for ALE column detection and column info
    
    /**
     * Check if the table has an ALE column (last column when cols >= 4)
     */
    private boolean hasALEColumn() {
        return table.cols >= 4;
    }
    
    /**
     * Get the number of regular (non-ALE) columns
     */
    private int getRegularColumnCount() {
        return hasALEColumn() ? (table.cols - 1) : table.cols;
    }
    
    /**
     * Get the stock name for a column (null if none)
     */
    private String getColumnStockName(int col) {
        if (table.outputNames != null && col < table.outputNames.length) {
            String name = table.outputNames[col];
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
        }
        return null;
    }
    
    /**
     * Get the column type, handling null cases
     */
    private ColumnType getColumnType(int col) {
        if (table.columnTypes != null && col < table.columnTypes.length && table.columnTypes[col] != null) {
            return table.columnTypes[col];
        }
        return ColumnType.ASSET; // Default
    }
    
    /**
     * Reset the renderer cache
     * Called when the circuit is reset (Reset button pressed)
     */
    public void resetCache() {
        cachedCellValues = null;
        cachedSumValues = null;
        lastUpdateTime = 0;
    }
    
    protected void drawTitle(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        
        int rowDescColWidth = table.collapsedMode ? 0 : cellWidthPixels; // Hide in collapsed mode
        int tableWidth = rowDescColWidth + table.cellSpacing + table.cols * cellWidthPixels + (table.cols + 1) * table.cellSpacing;
        int titleY = tableY + offsetY;
        
        // Draw light blue background for title area if hovering, but exclude the arrow area
        // The title area spans from the top of the table to just before the Type row (20 pixels total)
        if (table.needsHighlight()) {
            Rectangle arrowRect = table.getCollapseArrowRect();
            g.setColor(CircuitElm.selectColor); // Light blue
            // Draw background rectangle covering the title area, but skip the arrow area on the left
            g.fillRect(arrowRect.x + arrowRect.width, tableY, tableWidth - arrowRect.width, 20);
        }
        
        // Draw collapse state indicator on the LEFT side first
        // ▼ (U+25BC) for expanded, ▲ (U+25B6) for collapsed
        String collapseIndicator = table.collapsedMode ? "▲" : "▼";
        int indicatorX = tableX + 15; // Position near left edge
        
        // Highlight the arrow if hovering over it
        boolean isArrowHovered = table.isArrowHovered();
        if (isArrowHovered) {
            // Draw a subtle background for the arrow to indicate it's clickable
            Rectangle arrowRect = table.getCollapseArrowRect();
            g.setColor("#4a4a4a"); // Slightly lighter gray
            g.fillRect(arrowRect.x, arrowRect.y, arrowRect.width, arrowRect.height);
            g.setColor(CircuitElm.selectColor); // Make arrow blue when hovering
        } else {
            g.setColor(CircuitElm.whiteColor);
        }
        
        table.drawCenteredText(g, collapseIndicator, indicatorX, titleY, true);
        
        // Draw title centered at top of table with enhanced font
        g.setFont(TITLE_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(CircuitElm.whiteColor);
        table.drawCenteredText(g, table.tableTitle, tableX + tableWidth / 2, titleY, true);
        
        // Draw priority on the RIGHT side of the title row
        String priorityText = "P:" + table.priority;
        int priorityX = tableX + tableWidth - 30; // Position near right edge
        g.setFont(HEADER_FONT); // Smaller font for priority
        g.setColor(CircuitElm.whiteColor);
        table.drawCenteredText(g, priorityText, priorityX, titleY, true);
    }

    protected void drawColumnHeaders(Graphics g, int offsetY) {
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(CircuitElm.whiteColor);
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int headerY = tableY + offsetY;
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = table.collapsedMode ? 0 : cellWidthPixels; // Hide in collapsed mode

        // Draw row description column header cell text (skip in collapsed mode)
        if (!table.collapsedMode) {
            int rowDescHeaderX = tableX + table.cellSpacing;
            table.drawCenteredText(g, "Flows↓/Stocks→", rowDescHeaderX + rowDescColWidth/2, headerY + table.cellHeight/2, true);
        }

        // Draw data column header cells text
        for (int col = 0; col < table.cols; col++) {
            int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            
            String header = (table.outputNames != null && col < table.outputNames.length) ?
                           table.outputNames[col] : "Stock" + (col + 1);
            
            // Check if this table is the master for this output name and add star prefix
            String displayHeader = header;
            if (header != null && !header.isEmpty()) {
                boolean isMaster = ComputedValues.isMasterTable(header, table);
                if (isMaster) {
                    displayHeader = "★" + header;
                }
            }
            
            // Truncate header if needed to fit in cell (with 10px padding on each side)
            displayHeader = truncateText(displayHeader, g, cellWidthPixels - 20);
            
            // Draw header text
            g.setColor(CircuitElm.whiteColor);
            table.drawCenteredText(g, displayHeader, cellX + cellWidthPixels/2, headerY + table.cellHeight/2, true);
        }
        
        // Draw grid lines for this row
        drawRowGridLines(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
    }
    
    protected void drawColumnTypeRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        int typeRowY = tableY + offsetY;
        
        // Draw row description column cell text with header font
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(CircuitElm.whiteColor);
        table.drawCenteredText(g, "", tableX + table.cellSpacing + rowDescColWidth/2, typeRowY + table.cellHeight/2, true);
        
        // Draw column type cells text - merge adjacent cells with same type
        int col = 0;
        while (col < table.cols) {
            // Check if this is an A-L-E computed column
            boolean isALEColumn = (col == table.cols - 1 && table.cols >= 4);
            
            if (isALEColumn) {
                // A-L-E column: don't merge, just draw empty
                int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
                table.drawCenteredText(g, "", cellX + cellWidthPixels/2, typeRowY + table.cellHeight/2, true);
                col++;
            } else {
                // Regular column: find how many consecutive columns have the same type
                ColumnType type = table.getColumnType(col);
                String typeName = getColumnTypeName(type);
                
                int startCol = col;
                int endCol = col;
                
                // Count consecutive columns with same type (but stop before A-L-E column)
                while (endCol + 1 < table.cols) {
                    // Check if next column would be A-L-E
                    boolean nextIsALE = (endCol + 1 == table.cols - 1 && table.cols >= 4);
                    if (nextIsALE) break;
                    
                    ColumnType nextType = table.getColumnType(endCol + 1);
                    if (nextType == type) {
                        endCol++;
                    } else {
                        break;
                    }
                }
                
                // Calculate the center position across merged cells
                int startCellX = tableX + rowDescColWidth + table.cellSpacing * 2 + startCol * (cellWidthPixels + table.cellSpacing);
                int endCellX = tableX + rowDescColWidth + table.cellSpacing * 2 + endCol * (cellWidthPixels + table.cellSpacing);
                int centerX = startCellX + (endCellX - startCellX + cellWidthPixels) / 2;
                
                // Draw the type name centered across the merged cells
                table.drawCenteredText(g, typeName, centerX, typeRowY + table.cellHeight/2, true);
                
                // Move to next group
                col = endCol + 1;
            }
        }
        
        // Draw grid lines for this row (special handling for type row merging)
        drawTypeRowGridLines(g, offsetY, tableX, rowDescColWidth, cellWidthPixels);
    }

    protected void drawInitialConditionsRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        int initialRowY = tableY + offsetY;
        
        // Draw row description cell for initial conditions with header font
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(CircuitElm.whiteColor);
        table.drawCenteredText(g, "Initial", tableX + table.cellSpacing + rowDescColWidth/2, initialRowY + table.cellHeight/2, true);

        // Use cell font for values
        g.setFont(CELL_FONT);
        
        // Accumulate values for ALE calculation
        double assets = 0.0, liabilities = 0.0, equity = 0.0;
        
        for (int col = 0; col < table.cols; col++) {
            int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            
            // Get initial value - calculate ALE inline for last column when cols >= 4
            double initialValue;
            boolean isALECol = hasALEColumn() && col == table.cols - 1;
            
            if (isALECol) {
                // ALE column: sum(Assets) - sum(Liabilities) - sum(Equity)
                initialValue = assets - liabilities - equity;
            } else {
                initialValue = (table.initialValues != null && col < table.initialValues.length) 
                    ? table.initialValues[col] : 0.0;
                
                // Accumulate values by type for ALE calculation
                ColumnType type = getColumnType(col);
                if (type == ColumnType.ASSET) assets += initialValue;
                else if (type == ColumnType.LIABILITY) liabilities += initialValue;
                else if (type == ColumnType.EQUITY) equity += initialValue;
            }
            
            // Draw value with text color based on voltage (no background fill needed - canvas already colored)
            // For A-L-E columns, use blue if non-zero (indicates accounting discrepancy)
            if (isALECol && Math.abs(initialValue) > 1e-6) {
                g.setColor(Color.blue);
            } else {
                g.setColor(getTextVoltageColor(initialValue));
            }
            String voltageText = CircuitElm.getUnitText(initialValue, table.tableUnits);
            table.drawCenteredText(g, voltageText, cellX + cellWidthPixels/2, initialRowY + table.cellHeight/2, true);
        }
        
        // Draw grid lines for this row
        drawRowGridLines(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
    }

    protected void drawTableCells(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;

        // Use the passed offsetY directly - no need to recalculate
        int baseY = offsetY;

        for (int row = 0; row < table.rows; row++) {
            int cellY = tableY + baseY + row * (table.cellHeight + table.cellSpacing);
            
            // Draw row description text with header font
            g.setFont(HEADER_FONT);
            g.setLetterSpacing(LETTER_SPACING);
            g.setColor(CircuitElm.whiteColor);
            String rowDesc = (table.rowDescriptions != null && row < table.rowDescriptions.length) ?
                            table.rowDescriptions[row] : "Row" + (row + 1);
            
            // Truncate row description if needed to fit in cell (with 10px padding on each side)
            rowDesc = truncateText(rowDesc, g, rowDescColWidth - 20);
            
            table.drawCenteredText(g, rowDesc, tableX + table.cellSpacing + rowDescColWidth/2, cellY + table.cellHeight/2, true);
            
            // Use cell font for cell values
            g.setFont(CELL_FONT);
            double assets = 0.0, liabilities = 0.0, equity = 0.0;
            // Draw data cells for this row
            for (int col = 0; col < table.cols; col++) {
                int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
                
                // Get voltage - calculate ALE inline for last column when cols >= 4
                double voltage;


                voltage = getCachedCellValue(row, col);
                ColumnType type = getColumnType(col);
                if (type == ColumnType.ASSET) assets += voltage;
                else if (type == ColumnType.LIABILITY) liabilities += voltage;
                else if (type == ColumnType.EQUITY) equity += voltage;

                // No background fill needed - canvas is already filled with background color by CirSim
                
                // Check if this is an A-L-E column (computed, no equation)
                boolean isALECol = hasALEColumn() && col == table.cols - 1;
                
                // Display equation and voltage in cell (or just voltage for A-L-E)
                String equation = table.cellEquations[row][col];
                if (isALECol) {
                    voltage = assets - liabilities - equity;
                    // A-L-E column: ALWAYS display only the computed value (no equation)
                    // Color blue if non-zero (indicates accounting discrepancy), otherwise use voltage color
                    if (Math.abs(voltage) > 1e-6) {
                        g.setColor(Color.blue);
                    } else {
                        g.setColor(getTextVoltageColor(voltage));
                    }
                    String voltageText = CircuitElm.getUnitText(voltage, table.tableUnits);
                    table.drawCenteredText(g, voltageText, cellX + cellWidthPixels/2, cellY + table.cellHeight/2, true);
                } else if (equation != null && !equation.trim().isEmpty()) {
                    // Regular cell: display equation = value OR just equation based on showCellValues setting
                    g.setColor(getTextVoltageColor(voltage));
                    String displayText = truncateEquation(equation, g);
                    
                    if (table.showCellValues) {
                        // Show "equation = value"
                        String voltageText = CircuitElm.getUnitText(voltage, table.tableUnits);
                        String combinedText = displayText + ": " + voltageText;
                        table.drawCenteredText(g, combinedText, cellX + cellWidthPixels/2, cellY + table.cellHeight/2, true);
                    } else {
                        // Show just "equation"
                        table.drawCenteredText(g, displayText, cellX + cellWidthPixels/2, cellY + table.cellHeight/2, true);
                    }
                }
            }
            
            // Draw grid lines for this row
            int rowY = tableY + baseY + row * (table.cellHeight + table.cellSpacing);
            drawRowGridLines(g, rowY - tableY, tableX, rowDescColWidth, cellWidthPixels, false);
        }
    }
    
    /**
     * Truncate text to fit within a specified width using actual text measurement.
     * This properly handles Greek symbols, subscripts, and superscripts with accurate width.
     * 
     * @param text The text to truncate
     * @param g Graphics context for measuring text
     * @param maxWidth Maximum width in pixels
     * @return Truncated text with ".." appended if truncation occurred
     */
    private String truncateText(String text, Graphics g, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // Measure actual width of the full text
        double fullWidth = g.measureWidth(text);
        
        // If it fits, return as-is
        if (fullWidth <= maxWidth) {
            return text;
        }
        
        // Binary search to find the optimal truncation point
        int left = 1;
        int right = text.length();
        String bestFit = text.substring(0, Math.min(3, text.length())) + "..";
        
        while (left <= right) {
            int mid = (left + right) / 2;
            String candidate = text.substring(0, mid) + "..";
            double candidateWidth = g.measureWidth(candidate);
            
            if (candidateWidth <= maxWidth) {
                bestFit = candidate;
                left = mid + 1;  // Try to fit more
            } else {
                right = mid - 1;  // Too wide, try shorter
            }
        }
        
        return bestFit;
    }
    
    /**
     * Truncate long equations for display based on cell width using actual text measurement.
     * This properly handles Greek symbols, subscripts, and superscripts with accurate width.
     */
    private String truncateEquation(String equation, Graphics g) {
        // Get cell width in pixels with padding (10 pixels on each side)
        int cellWidthPixels = table.getCellWidthPixels();
        int availableWidth = cellWidthPixels - 20;
        return truncateText(equation, g, availableWidth);
    }    protected void drawSumRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = table.collapsedMode ? 0 : cellWidthPixels; // Hide in collapsed mode

        // Use the passed offsetY directly - no need to recalculate
        int sumRowY = tableY + offsetY;

        // // Draw row description text for computed row with header font (skip in collapsed mode)
        // if (!table.collapsedMode) {
        //     g.setFont(HEADER_FONT);
        //     g.setLetterSpacing(LETTER_SPACING);
        //     g.setColor(CircuitElm.whiteColor);
        //     table.drawCenteredText(g, "Computed", tableX + table.cellSpacing + rowDescColWidth/2, sumRowY + table.cellHeight/2, true);
        // }

        // Use cell font for values
        g.setFont(CELL_FONT);
        
        // Accumulate values for ALE calculation (using cached values)
        double assets = 0.0, liabilities = 0.0, equity = 0.0;
        
        for (int col = 0; col < table.cols; col++) {
            int cellX = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            
            // Get the computed value from cache (updated twice per second)
            double computedValue;
            
            // Check if this is an A-L-E column
            boolean isALEColumn = (col == table.cols - 1 && table.cols >= 4);
            
            if (isALEColumn) {
                // ALE column: sum(Assets) - sum(Liabilities) - sum(Equity)
                computedValue = assets - liabilities - equity;
            } else {
                computedValue = (cachedSumValues != null && col < cachedSumValues.length) 
                    ? cachedSumValues[col] : 0.0;
                
                // Accumulate values by type for ALE calculation
                ColumnType type = getColumnType(col);
                if (type == ColumnType.ASSET) assets += computedValue;
                else if (type == ColumnType.LIABILITY) liabilities += computedValue;
                else if (type == ColumnType.EQUITY) equity += computedValue;
            }
            
            // No background fill needed - canvas is already filled with background color by CirSim
            
            // Draw value with text color based on voltage
            // For A-L-E columns, use blue if non-zero (indicates accounting discrepancy)
            if (isALEColumn && Math.abs(computedValue) > 1e-6) {
                g.setColor(Color.blue);
            } else {
                g.setColor(getTextVoltageColor(computedValue));
            }
            String sumText = CircuitElm.getUnitText(computedValue, table.tableUnits);
            table.drawCenteredText(g, sumText, cellX + cellWidthPixels/2, sumRowY + table.cellHeight/2, true);
        }
        
        // Draw grid lines for computed row (with double line above)
        drawRowGridLines(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, true);
    }

    /**
     * Draw grid lines for the type row with merged cells (no vertical lines between same types)
     */
    private void drawTypeRowGridLines(Graphics g, int offsetY, int tableX, int rowDescColWidth, int cellWidthPixels) {
        int tableY = table.getTableY();
        int rowY = tableY + offsetY;
        
        g.setColor(CircuitElm.lightGrayColor);
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
        
        // Between and after data columns - merge cells with same type (no vertical lines)
        for (int col = 0; col <= table.cols; col++) {
            x = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            
            // Check if we should draw a line at this position
            boolean drawDouble = false;
            boolean drawLine = false;
            
            if (col == table.cols) {
                // Always draw line at right edge of table
                drawLine = true;
            } else if (col > 0 && col < table.cols) {
                // Check if column type changes between adjacent columns
                ColumnType prevType = table.getColumnType(col - 1);
                ColumnType currType = table.getColumnType(col);
                
                // Draw double line if types are different (ignoring null/unknown)
                if (prevType != null && currType != null && prevType != currType) {
                    drawDouble = true;
                    drawLine = true;
                }
                // Don't draw line if types are the same (merged cells)
            }
            
            if (drawLine) {
                if (drawDouble) {
                    // Draw double vertical line where types change
                    g.drawLine(x - 1, rowY, x - 1, rowY + table.cellHeight);
                    g.drawLine(x + 1, rowY, x + 1, rowY + table.cellHeight);
                } else {
                    // Draw single vertical line (only at right edge)
                    g.drawLine(x, rowY, x, rowY + table.cellHeight);
                }
            }
        }
    }
    
    /**
     * Draw grid lines for a single row
     * @param isSumRow if true, draws a double line above the row
     */
    private void drawRowGridLines(Graphics g, int offsetY, int tableX, int rowDescColWidth, int cellWidthPixels, boolean isSumRow) {
        int tableY = table.getTableY();
        int rowY = tableY + offsetY;
        
        g.setColor(CircuitElm.lightGrayColor);
        int tableWidth = rowDescColWidth + table.cellSpacing * 2 + table.cols * (cellWidthPixels + table.cellSpacing);
        
        // Horizontal lines (top and bottom of row)
        // For sum row, draw double line at top
        if (isSumRow) {
            g.drawLine(tableX, rowY - 2, tableX + tableWidth, rowY - 2);
        }
        g.drawLine(tableX, rowY, tableX + tableWidth, rowY);
        g.drawLine(tableX, rowY + table.cellHeight, tableX + tableWidth, rowY + table.cellHeight);
        
        // Vertical lines
        // Left edge
        g.drawLine(tableX, rowY, tableX, rowY + table.cellHeight);
        // After description column
        int x = tableX + table.cellSpacing + rowDescColWidth;
        g.drawLine(x, rowY, x, rowY + table.cellHeight);
        
        // Between and after data columns - always draw all vertical lines
        for (int col = 0; col <= table.cols; col++) {
            x = tableX + rowDescColWidth + table.cellSpacing * 2 + col * (cellWidthPixels + table.cellSpacing);
            
            // Check if we should draw a double line (when column type changes)
            boolean drawDouble = false;
            if (col > 0 && col < table.cols) {
                ColumnType prevType = table.getColumnType(col - 1);
                ColumnType currType = table.getColumnType(col);
                
                // Draw double line if types are different (ignoring null/unknown)
                if (prevType != null && currType != null && prevType != currType) {
                    drawDouble = true;
                }
            }
            
            if (drawDouble) {
                // Draw double vertical line where types change
                g.drawLine(x - 1, rowY, x - 1, rowY + table.cellHeight);
                g.drawLine(x + 1, rowY, x + 1, rowY + table.cellHeight);
            } else {
                // Draw single vertical line
                g.drawLine(x, rowY, x, rowY + table.cellHeight);
            }
        }
    }
    
    protected void drawPins(Graphics g) {
        // HIDDEN: Pins and posts are not drawn visually, but electrical connections remain functional
        // Update current counts for proper circuit simulation even though not displayed
        for (int i = 0; i < table.getPostCount(); i++) {
            ChipElm.Pin p = table.pins[i];
            p.curcount = table.updateDotCount(p.current, p.curcount);
        }
        // Don't draw pin lines, current dots, or posts - keep visual elements hidden
    }
}
