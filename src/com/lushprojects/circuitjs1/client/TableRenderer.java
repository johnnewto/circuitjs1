/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.TableColumn.ColumnType;

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
    protected static final long UPDATE_INTERVAL_MS = 200; // Update 5 times per second
    
    // Fonts for different parts of the table
    // Protected to allow subclasses (CurrentTransactionsMatrixRenderer) to use these constants
    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 13);
    protected static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 11);
    protected static final Font CELL_FONT = new Font("SansSerif", 0, 11);  // Non-bold like ActionTimeElm
    protected static final String LETTER_SPACING = "0.5px"; // For better readability
    
    // Modern styling configuration
    protected static final boolean MODERN_STYLE = true; // Enable modern CSS-inspired styling
    protected static final int CORNER_RADIUS = 8; // Rounded corner radius for modern look
    
    // Modern color scheme (inspired by Piccalilli's table styling)
    // Dark mode colors
    protected static final Color HEADER_BG_DARK = new Color(55, 55, 75);     // Subtle blue-tinted header
    protected static final Color FOOTER_BG_DARK = new Color(55, 55, 75);     // Match header
    protected static final Color ROW_EVEN_BG_DARK = new Color(45, 45, 50);   // Slightly lighter alternate
    protected static final Color ROW_ODD_BG_DARK = new Color(35, 35, 40);    // Base row color
    protected static final Color TABLE_BG_DARK = new Color(30, 30, 35);      // Table background
    protected static final Color GRID_LINE_DARK = new Color(60, 60, 65);     // Subtle grid lines
    protected static final Color HEADER_BORDER_DARK = new Color(80, 80, 100); // Stronger header border
    
    // Light mode colors (printable)
    protected static final Color HEADER_BG_LIGHT = new Color(235, 235, 245);   // Light purple-tinted
    protected static final Color FOOTER_BG_LIGHT = new Color(235, 235, 245);   // Match header
    protected static final Color ROW_EVEN_BG_LIGHT = new Color(248, 248, 252); // Very subtle alternate
    protected static final Color ROW_ODD_BG_LIGHT = new Color(255, 255, 255);  // White base
    protected static final Color TABLE_BG_LIGHT = new Color(255, 255, 255);    // White background
    protected static final Color GRID_LINE_LIGHT = new Color(220, 220, 230);   // Subtle grid
    protected static final Color HEADER_BORDER_LIGHT = new Color(180, 180, 200); // Stronger header border
    
    public TableRenderer(TableElm table) {
        this.table = table;
    }
    
    // Helper methods for modern styling colors based on theme
    protected boolean isPrintable() {
        return CirSim.theSim.printableCheckItem.getState();
    }
    
    protected Color getHeaderBgColor() {
        return isPrintable() ? HEADER_BG_LIGHT : HEADER_BG_DARK;
    }
    
    protected Color getFooterBgColor() {
        return isPrintable() ? FOOTER_BG_LIGHT : FOOTER_BG_DARK;
    }
    
    protected Color getRowBgColor(int row) {
        if (isPrintable()) {
            return (row % 2 == 0) ? ROW_EVEN_BG_LIGHT : ROW_ODD_BG_LIGHT;
        }
        return (row % 2 == 0) ? ROW_EVEN_BG_DARK : ROW_ODD_BG_DARK;
    }
    
    protected Color getTableBgColor() {
        return isPrintable() ? TABLE_BG_LIGHT : TABLE_BG_DARK;
    }
    
    protected Color getGridLineColor() {
        return isPrintable() ? GRID_LINE_LIGHT : GRID_LINE_DARK;
    }
    
    protected Color getHeaderBorderColor() {
        return isPrintable() ? HEADER_BORDER_LIGHT : HEADER_BORDER_DARK;
    }
    
    /**
     * Get text color that's appropriate for the current theme.
     * Uses white for dark mode and dark gray for light/printable mode.
     */
    protected Color getTextColor() {
        if (MODERN_STYLE && isPrintable()) {
            return new Color(30, 30, 30); // Dark text for light backgrounds
        }
        return CircuitElm.whiteColor;
    }
    
    /**
     * Format a value for display, treating values < 0.01 as 0.00
     * @param value The value to format
     * @param units The unit suffix (e.g., "V", "$", "")
     * @return Formatted string
     */
    protected String formatDisplayValue(double value, String units) {
        // Treat very small values as zero
        if (Math.abs(value) < 0.01) {
            value = 0.0;
        }
        return CircuitElm.getUnitText(value, units);
    }
    
    /**
     * Get cached A-L-E cell value for display
     * Returns 0.0 if cache not initialized or out of bounds
     * Package-private for TableElm access
     */
    double getCachedALECellValue(int row, int aleColumnIndex) {
        return getCachedCellValue(row, aleColumnIndex);
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
            case SECTOR: return "Sector";
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
     * Protected to allow subclasses (CurrentTransactionsMatrixRenderer) to reuse this logic
     */
    protected Color getTextVoltageColor(double volts) {
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
        TableDimensions dims = calculateTableDimensions();
        
        // Update cached values if needed
        updateCachedValuesIfNeeded();

        // Draw background first
        drawTableBackground(g, dims);

        // Draw components in order (includes row backgrounds)
        drawComponentsInOrder(g, dims);
        
        // Draw border LAST so it's on top of row backgrounds
        drawTableBorder(g, dims);

        // Draw pins
        drawPins(g);
    }
    
    /**
     * Calculate all table dimensions
     */
    private TableDimensions calculateTableDimensions() {
        TableDimensions dims = new TableDimensions();
        dims.tableX = table.getTableX();
        dims.tableY = table.getTableY();
        dims.cellWidthPixels = table.getCellWidthPixels();
        
        // Always show row description column (1.5x width for better readability)
        dims.rowDescColWidth = dims.cellWidthPixels * 3 / 2;
        
        // Calculate heights
        dims.titleHeight = 20;
        dims.typeRowHeight = (table.collapsedMode || !shouldShowTypeRow()) ? 0 : (table.cellHeight + table.cellSpacing);
        int extraRowsHeight = table.collapsedMode ? 0 : getExtraRowsBeforeTypeRowHeight();
        dims.headerRowHeight = table.cellHeight + table.cellSpacing;
        dims.initialRowHeight = (table.showInitialValues && !table.collapsedMode) ? 
                               (table.cellHeight + table.cellSpacing) : 0;
        dims.dataRowsHeight = table.collapsedMode ? 0 : 
                             (table.rows * (table.cellHeight + table.cellSpacing));
        dims.computedRowHeight = table.cellHeight + table.cellSpacing;
        
        dims.tableWidth = dims.rowDescColWidth + table.cellSpacing + 
                         getTotalDataColumnsWidth(dims.cellWidthPixels);
        dims.tableHeight = dims.titleHeight + extraRowsHeight + dims.typeRowHeight + dims.headerRowHeight + 
                          dims.initialRowHeight + dims.dataRowsHeight + dims.computedRowHeight;
        
        return dims;
    }
    
    /**
     * Hook method for subclasses to control whether the type row is shown.
     * SFCTableRenderer overrides this to return false.
     * @return true to show type row, false to hide it
     */
    protected boolean shouldShowTypeRow() {
        return true;
    }
    
    /**
     * Hook method for subclasses to specify height of extra rows before type row.
     * CTM uses this to account for the table name row.
     * @return Height in pixels of extra rows (0 for base class)
     */
    protected int getExtraRowsBeforeTypeRowHeight() {
        return 0;
    }
    
    /**
     * Update cached values if enough time has passed
     */
    private void updateCachedValuesIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS || cachedCellValues == null) {
            updateCachedValues();
            lastUpdateTime = currentTime;
        }
    }
    
    /**
     * Draw table background
     */
    private void drawTableBackground(Graphics g, TableDimensions dims) {
        if (MODERN_STYLE) {
            // Modern style: use rounded corners and theme-aware colors
            g.setColor(getTableBgColor());
            g.fillRoundRect(dims.tableX + 1, dims.tableY + 1, dims.tableWidth - 2, dims.tableHeight - 2, CORNER_RADIUS);
        } else {
            // Legacy style
            Color bgColor = CirSim.theSim.printableCheckItem.getState() ? 
                new Color(230, 230, 230) : new Color(40, 40, 40);
            g.setColor(bgColor);
            g.fillRect(dims.tableX + 1, dims.tableY + 1, dims.tableWidth - 2, dims.tableHeight - 2);
        }
    }
    
    /**
     * Draw table border
     */
    private void drawTableBorder(Graphics g, TableDimensions dims) {
        if (MODERN_STYLE) {
            // Modern style: subtle rounded border
            boolean selected = table.needsHighlight();
            g.setColor(table.nonConverged ? Color.red : (selected ? CircuitElm.selectColor : getGridLineColor()));
            g.drawRoundRect(dims.tableX, dims.tableY, dims.tableWidth, dims.tableHeight, CORNER_RADIUS);
            if (selected) {
                // Draw double border when selected for better visibility
                g.drawRoundRect(dims.tableX + 1, dims.tableY + 1, dims.tableWidth - 2, dims.tableHeight - 2, CORNER_RADIUS);
            }
        } else {
            g.setColor(table.nonConverged ? Color.red : CircuitElm.lightGrayColor);
            g.drawRect(dims.tableX, dims.tableY, dims.tableWidth, dims.tableHeight);
        }
    }
    
    /**
     * Draw all table components in order, return final Y position
     */
    private int drawComponentsInOrder(Graphics g, TableDimensions dims) {
        int currentY = 10;
        
        // In modern style, draw row backgrounds first (before content)
        if (MODERN_STYLE) {
            drawRowBackgrounds(g, dims);
        }
        
        drawTitle(g, currentY);
        currentY += 10;
        
        // Hook for subclasses to draw extra rows before type row (e.g., CTM table name row)
        if (!table.collapsedMode) {
            currentY = drawExtraRowsBeforeTypeRow(g, currentY);
        }
        
        if (!table.collapsedMode && shouldShowTypeRow()) {
            drawColumnTypeRow(g, currentY);
            currentY += table.cellHeight + table.cellSpacing;
        }
        
        drawColumnHeaders(g, currentY);
        currentY += table.cellHeight + table.cellSpacing;
        
        if (table.showInitialValues && !table.collapsedMode) {
            drawInitialConditionsRow(g, currentY);
            currentY += table.cellHeight + table.cellSpacing;
        }

        if (!table.collapsedMode) {
            drawTableCells(g, currentY);
            currentY += table.rows * (table.cellHeight + table.cellSpacing);
        }

        drawSumRow(g, currentY);
        currentY += table.cellHeight + table.cellSpacing;
        
        return currentY;
    }
    
    /**
     * Draw row backgrounds for modern styling (zebra striping, header/footer backgrounds)
     */
    private void drawRowBackgrounds(Graphics g, TableDimensions dims) {
        int tableX = dims.tableX;
        int tableY = dims.tableY;
        int tableWidth = dims.tableWidth;
        int rowHeight = table.cellHeight + table.cellSpacing;
        
        // Inset from edges to preserve rounded corners
        int edgeInset = MODERN_STYLE ? CORNER_RADIUS / 2 + 1 : 2;
        
        // Calculate Y positions for different sections
        int currentY = tableY + dims.titleHeight;
        
        // Type row background (header style)
        if (!table.collapsedMode && dims.typeRowHeight > 0) {
            g.setColor(getHeaderBgColor());
            g.fillRect(tableX + edgeInset, currentY, tableWidth - edgeInset * 2, rowHeight);
            currentY += rowHeight;
        }
        
        // Column headers row background (header style)
        g.setColor(getHeaderBgColor());
        g.fillRect(tableX + edgeInset, currentY, tableWidth - edgeInset * 2, rowHeight);
        currentY += rowHeight;
        
        // Initial values row background (if shown) - use odd row color to distinguish from header
        if (table.showInitialValues && !table.collapsedMode) {
            g.setColor(getRowBgColor(1)); // Use odd row color to distinguish from header
            g.fillRect(tableX + edgeInset, currentY, tableWidth - edgeInset * 2, rowHeight);
            currentY += rowHeight;
        }
        
        // Data rows with zebra striping
        if (!table.collapsedMode) {
            for (int row = 0; row < table.rows; row++) {
                g.setColor(getRowBgColor(row));
                g.fillRect(tableX + edgeInset, currentY + row * rowHeight, tableWidth - edgeInset * 2, rowHeight);
            }
            currentY += table.rows * rowHeight;
        }
        
        // Computed/Sum row background (footer style) - reduce height slightly for bottom edge
        g.setColor(getFooterBgColor());
        g.fillRect(tableX + edgeInset, currentY, tableWidth - edgeInset * 2, rowHeight - 2);
    }
    
    /**
     * Hook method for subclasses to draw extra rows before the type row.
     * CTM uses this to draw the table name row.
     * @param currentY The current Y offset
     * @return The updated Y offset after drawing (if any)
     */
    protected int drawExtraRowsBeforeTypeRow(Graphics g, int currentY) {
        // Base class does nothing, subclasses can override
        return currentY;
    }
    
    /**
     * Helper class for table dimensions
     */
    private static class TableDimensions {
        int tableX, tableY, tableWidth, tableHeight;
        int cellWidthPixels, rowDescColWidth;
        int titleHeight, typeRowHeight, headerRowHeight;
        int initialRowHeight, dataRowsHeight, computedRowHeight;
    }
    
    /**
     * Update cached cell and sum values from the table
     * Called automatically when UPDATE_INTERVAL_MS has elapsed (200ms = 5 times per second)
     * 
     * SYNCHRONIZED DISPLAY ARCHITECTURE:
     * - Cell values: Each table evaluates its own equations (allows different formulas)
     * - Column sums: Non-master columns display the master's computed sum
     * - ALE values: Calculated during cache update for consistency
     * 
     * This allows tables to have independent cell-level calculations while
     * showing synchronized stock totals in the "Computed" row.
     */
    protected void updateCachedValues() {
        initializeCacheArrays();
        updateRegularCellValues();
        updateColumnSums();
    }
    
    /**
     * Initialize cache arrays if needed or if dimensions changed
     */
    private void initializeCacheArrays() {
        if (cachedCellValues == null || cachedCellValues.length != table.rows || 
            (cachedCellValues.length > 0 && cachedCellValues[0].length != table.getCols())) {
            cachedCellValues = new double[table.rows][table.getCols()];
        }
        if (cachedSumValues == null || cachedSumValues.length != table.getCols()) {
            cachedSumValues = new double[table.getCols()];
        }
    }
    
    /**
     * Update cell values for regular (non-ALE) columns
     */
    private void updateRegularCellValues() {
        int regularColCount = getRegularColumnCount();
        
        // Update cell values for regular (non-ALE) columns only
        // For master columns: use cached values from our doStep()
        // For non-master columns: fetch cached values from the master table BY FLOW NAME
        for (int row = 0; row < table.rows; row++) {
            String flowName = table.getRowDescription(row);
            
            for (int col = 0; col < regularColCount; col++) {
                if (table.columns != null && col < table.columns.size()) {
                    TableColumn column = table.columns.get(col);
                    
                    // Check if we're the master for this column
                    if (table.isMasterForColumn(col)) {
                        // Master: use our own cached value from doStep()
                        cachedCellValues[row][col] = column.getCachedCellValue(row);
                        
        
                    } else {
                        // Non-master: fetch cached value from the master table
                        // Map by flow name, not by row index
                        cachedCellValues[row][col] = fetchCellValueFromMaster(row, col, flowName);
                    }
                } else {
                    cachedCellValues[row][col] = 0.0;
                }
            }
        }
    }
    
    /**
     * Fetch a single cell value from the master table by mapping flow names.
     * This ensures that when multiple tables reference the same stock, 
     * the correct row is retrieved even if flow names appear at different indices.
     * 
     * @param row Current row index in this table
     * @param col Current column index in this table
     * @param flowName Flow name for this row
     * @return Cell value from master table or 0.0 if not found
     */
    private double fetchCellValueFromMaster(int row, int col, String flowName) {
        if (table.columns == null || col >= table.columns.size()) {
            return 0.0;
        }
        
        TableColumn column = table.columns.get(col);
        String stockName = column.getStockName();
        
        TableElm masterTable = ComputedValues.getMasterTable(stockName);
        
        if (masterTable == null || masterTable.columns == null) {
            return 0.0;
        }
        
        // Find the row in the master table that matches this flow name
        int masterRow = findRowByFlowName(masterTable, flowName);
        int masterCol = masterTable.findColumnByStockName(stockName);
        
        if (masterRow >= 0 && masterCol >= 0 && masterCol < masterTable.columns.size()) {
            TableColumn masterColumn = masterTable.columns.get(masterCol);
            return masterColumn.getCachedCellValue(masterRow);
        }
        
        return 0.0;
    }
    
    /**
     * Find the row index in a master table that matches a flow name.
     * This is critical for proper value synchronization when multiple tables
     * share stock names but have flows in different orders.
     * 
     * Uses trimmed comparison to handle cases where flow names may have
     * trailing/leading whitespace differences between tables.
     * 
     * @param masterTable The master table to search
     * @param flowName The flow name to find
     * @return Row index in master table, or -1 if not found
     */
    private int findRowByFlowName(TableElm masterTable, String flowName) {
        if (flowName == null || flowName.trim().isEmpty()) {
            return -1;
        }
        
        String trimmedFlowName = flowName.trim();
        int masterRows = masterTable.getRows();
        for (int r = 0; r < masterRows; r++) {
            String masterFlowName = masterTable.getRowDescription(r);
            // Compare trimmed names to handle whitespace differences
            if (masterFlowName != null && trimmedFlowName.equals(masterFlowName.trim())) {
                return r;
            }
        }
        
        return -1; // Not found
    }
    
    /**
     * Update column sums (computed row) - regular columns first, then ALE
     * ALE calculation depends on regular column sums being calculated first
     */
    private void updateColumnSums() {
        int regularColCount = getRegularColumnCount();
        
        // Step 1: Calculate all regular (non-ALE) column sums
        for (int col = 0; col < regularColCount; col++) {
            cachedSumValues[col] = getRegularColumnSum(col);
        }
        
        // Step 2: Calculate ALE column sum using the already-calculated regular column sums
        if (hasALEColumn()) {
            int aleCol = table.getCols() - 1;
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
     * Protected to allow subclasses (CurrentTransactionsMatrixRenderer) to reuse this logic
     */
    protected double getCachedCellValue(int row, int col) {
        if (cachedCellValues != null && row >= 0 && row < cachedCellValues.length &&
            col >= 0 && col < cachedCellValues[row].length) {
            return cachedCellValues[row][col];
        }
        return 0.0;
    }
    
    /**
     * Get sum for a regular (non-ALE) column
     * Master tables use their own computed values, non-master tables fetch from ComputedValues
     * Protected to allow subclasses to reuse this logic
     */
    protected double getRegularColumnSum(int col) {
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
     * Get A-L-E initial value for the initial conditions row.
     * Base implementation uses accounting equation (A - L - E).
     * Subclasses (like CTM) can override for custom calculation.
     * Protected to allow subclasses to override.
     */
    protected double getALEInitialValue() {
        if (!hasALEColumn()) {
            return 0.0;
        }
        
        double totalAssets = 0.0, totalLiabilities = 0.0, totalEquity = 0.0;
        for (int c = 0; c < table.columns.size(); c++) {
            if (c == table.getCols() - 1) continue; // Skip ALE column itself
            TableColumn column = table.columns.get(c);
            double initialValue = column.getInitialValue();
            ColumnType type = column.getType();
            if (type == ColumnType.ASSET) totalAssets += initialValue;
            else if (type == ColumnType.LIABILITY) totalLiabilities += initialValue;
            else if (type == ColumnType.EQUITY) totalEquity += initialValue;
        }
        return totalAssets - totalLiabilities - totalEquity;
    }
    
    /**
     * Get A-L-E sum value for the sum row.
     * Base implementation uses accounting equation (A - L - E).
     * Subclasses (like CTM) can override for custom calculation.
     * Protected to allow subclasses to override.
     */
    protected double getALESumValue() {
        if (!hasALEColumn()) {
            return 0.0;
        }
        
        double totalAssets = 0.0, totalLiabilities = 0.0, totalEquity = 0.0;
        int regularColCount = getRegularColumnCount();
        for (int c = 0; c < regularColCount; c++) {
            double sumValue = (cachedSumValues != null && c < cachedSumValues.length) 
                ? cachedSumValues[c] : 0.0;
            ColumnType type = getColumnType(c);
            if (type == ColumnType.ASSET) totalAssets += sumValue;
            else if (type == ColumnType.LIABILITY) totalLiabilities += sumValue;
            else if (type == ColumnType.EQUITY) totalEquity += sumValue;
        }
        return totalAssets - totalLiabilities - totalEquity;
    }
    
    /**
     * Get A-L-E value for a specific row.
     * Base implementation uses accounting equation (A - L - E).
     * Subclasses (like CTM) can override for custom calculation.
     * Protected to allow subclasses to override.
     * 
     * @param row Row index
     * @param totalAssets Pre-calculated total assets for this row
     * @param totalLiabilities Pre-calculated total liabilities for this row
     * @param totalEquity Pre-calculated total equity for this row
     */
    protected double getALERowValue(int row, double totalAssets, double totalLiabilities, double totalEquity) {
        return totalAssets - totalLiabilities - totalEquity;
    }
    
    /**
     * Check if the table has an ALE column (controlled by showALE property)
     * Protected to allow subclasses to reuse this logic
     */
    protected boolean hasALEColumn() {
        if (!table.shouldShowALE()) {
            return false;
        }
        
        // Check if we have enough columns for standard tables (3 regular + 1 ALE)
        // OR check if the last column is actually named "A-L-E" (for CTM with fewer columns)
        if (table.getCols() >= 4) {
            return true;
        }
        
        // For tables with fewer than 4 columns, check if the last column is "A-L-E"
        if (table.getCols() > 0 && table.columns != null && table.columns.size() > 0) {
            TableColumn lastCol = table.columns.get(table.columns.size() - 1);
            return lastCol.isALE() || "A-L-E".equalsIgnoreCase(lastCol.getStockName());
        }
        
        return false;
    }
    
    /**
     * Get the number of regular (non-ALE) columns
     * Protected to allow subclasses to reuse this logic
     */
    protected int getRegularColumnCount() {
        return hasALEColumn() ? (table.getCols() - 1) : table.getCols();
    }
    
    /**
     * Get the width of a specific column in pixels.
     * ALE columns are half the width of regular columns.
     * @param col Column index
     * @param cellWidthPixels Standard cell width in pixels
     * @return Width of the column in pixels
     */
    protected int getColumnWidth(int col, int cellWidthPixels) {
        boolean isALECol = hasALEColumn() && col == table.getCols() - 1;
        return isALECol ? cellWidthPixels / 2 : cellWidthPixels;
    }
    
    /**
     * Get the X position of a specific column, accounting for variable column widths.
     * ALE columns are half the width of regular columns.
     * @param col Column index
     * @param tableX Table X position
     * @param rowDescColWidth Row description column width
     * @param cellWidthPixels Standard cell width in pixels
     * @return X position of the column
     */
    protected int getColumnX(int col, int tableX, int rowDescColWidth, int cellWidthPixels) {
        int x = tableX + rowDescColWidth + table.cellSpacing * 2;
        for (int c = 0; c < col; c++) {
            x += getColumnWidth(c, cellWidthPixels) + table.cellSpacing;
        }
        return x;
    }
    
    /**
     * Calculate the total width of all data columns, accounting for ALE column being half width.
     * @param cellWidthPixels Standard cell width in pixels
     * @return Total width of all data columns including spacing
     */
    protected int getTotalDataColumnsWidth(int cellWidthPixels) {
        int totalWidth = 0;
        for (int c = 0; c < table.getCols(); c++) {
            totalWidth += getColumnWidth(c, cellWidthPixels) + table.cellSpacing;
        }
        return totalWidth + table.cellSpacing; // Add extra spacing at end
    }
    
    /**
     * Get the stock name for a column (null if none)
     * Protected to allow subclasses to reuse this logic
     */
    protected String getColumnStockName(int col) {
        if (col >= 0 && col < table.columns.size()) {
            String name = table.columns.get(col).getStockName();
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
        }
        return null;
    }
    
    /**
     * Get the column type, handling null cases
     * Protected to allow subclasses (CurrentTransactionsMatrixRenderer) to reuse this logic
     */
    protected ColumnType getColumnType(int col) {
        if (col >= 0 && col < table.columns.size()) {
            return table.columns.get(col).getType();
        }
        return ColumnType.ASSET; // Default
    }
    
    /**
     * Determine if a computed column should be colored blue to indicate accounting discrepancy.
     * Base implementation returns true for A-L-E columns with non-zero values.
     * Subclasses can override to customize behavior (e.g., CTM's SUM column uses normal coloring).
     * 
     * @param col Column index
     * @param value Computed value for this column
     * @return true if column should be colored blue, false for normal voltage coloring
     */
    protected boolean shouldColorComputedColumnBlue(int col, double value) {
        // Default: color blue if non-zero (indicates accounting discrepancy in A-L-E)
        return Math.abs(value) > 1e-6;
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
        
        // Always show row description column (1.5x width for better readability)
        int rowDescColWidth = cellWidthPixels * 3 / 2;
        int tableWidth = rowDescColWidth + table.cellSpacing + getTotalDataColumnsWidth(cellWidthPixels);
        int titleY = tableY + offsetY;
        
        // Note: Highlight is shown via border only (in drawTableBorder), not title bar fill
        
        // Draw collapse state indicator on the LEFT side first
        // ▼ (U+25BC) for expanded, ▲ (U+25B6) for collapsed
        String collapseIndicator = table.collapsedMode ? "▲" : "▼";
        int indicatorX = tableX + 15; // Position near left edge
        
        // Highlight the arrow if hovering over it
        boolean isArrowHovered = table.isArrowHovered();
        if (isArrowHovered) {
            // Draw a subtle background for the arrow to indicate it's clickable
            Rectangle arrowRect = table.getCollapseArrowRect();
            g.setColor(isPrintable() ? "#d0d0d0" : "#4a4a4a"); // Theme-aware hover background
            g.fillRect(arrowRect.x, arrowRect.y, arrowRect.width, arrowRect.height);
            g.setColor(CircuitElm.selectColor); // Make arrow blue when hovering
        } else {
            g.setColor(getTextColor());
        }
        
        table.drawCenteredText(g, collapseIndicator, indicatorX, titleY, true);
        
        // Draw title centered at top of table with enhanced font
        g.setFont(TITLE_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(getTextColor());
        table.drawCenteredText(g, table.tableTitle, tableX + tableWidth / 2, titleY, true);
        
        // Draw priority on the RIGHT side of the title row
        String priorityText = "P:" + table.priority;
        int priorityX = tableX + tableWidth - 30; // Position near right edge
        g.setFont(HEADER_FONT); // Smaller font for priority
        g.setColor(getTextColor());
        table.drawCenteredText(g, priorityText, priorityX, titleY, true);
        
        // Draw grid line at bottom of title row (20 pixels high, not cellHeight)
        // Modern style uses stronger header border color
        if (MODERN_STYLE) {
            g.setColor(getHeaderBorderColor());
        } else {
            g.setColor(CircuitElm.lightGrayColor);
        }
        int titleBottomY = tableY + 20;
        // Inset from edges to preserve rounded corners
        int inset = MODERN_STYLE ? CORNER_RADIUS / 2 : 0;
        g.drawLine(tableX + inset, titleBottomY, tableX + tableWidth - inset, titleBottomY);
    }

    protected void drawColumnHeaders(Graphics g, int offsetY) {
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(getTextColor());
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int headerY = tableY + offsetY;
        int cellWidthPixels = table.getCellWidthPixels();
        // Always show row description column (1.5x width for better readability)
        int rowDescColWidth = cellWidthPixels * 3 / 2;

        // Draw row description column header cell text (skip in collapsed mode)
        if (!table.collapsedMode) {
            int rowDescHeaderX = tableX + table.cellSpacing;
            table.drawCenteredText(g, "Flows↓/Stocks→", rowDescHeaderX + rowDescColWidth/2, headerY + table.cellHeight/2, true);
        }

        // Draw data column header cells text
        for (int col = 0; col < table.getCols(); col++) {
            int cellX = getColumnX(col, tableX, rowDescColWidth, cellWidthPixels);
            int colWidth = getColumnWidth(col, cellWidthPixels);
            
            String header = (col < table.columns.size()) ?
                           table.columns.get(col).getStockName() : "";
            
            // Check if this table is the master for this output name and add star prefix
            String displayHeader = header;
            if (header != null && !header.isEmpty()) {
                boolean isMaster = ComputedValues.isMasterTable(header, table);
                if (isMaster) {
                    displayHeader = "★" + header;
                }
            }
            
            // Truncate header if needed to fit in cell (with 10px padding on each side)
            displayHeader = truncateText(displayHeader, g, colWidth - 10);
            
            // Draw header text
            g.setColor(getTextColor());
            table.drawCenteredText(g, displayHeader, cellX + colWidth/2, headerY + table.cellHeight/2, true);
        }
        
        // Draw grid lines for header row - use header border style for stronger separation
        drawRowGridLineWithStyle(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false, true);
    }
    
    /**
     * Draw grid lines for a row with optional header-style border
     * @param isHeaderRow if true, uses stronger header border color for bottom line
     */
    private void drawRowGridLineWithStyle(Graphics g, int offsetY, int tableX, int rowDescColWidth, int cellWidthPixels, boolean isSumRow, boolean isHeaderRow) {
        int tableY = table.getTableY();
        int rowY = tableY + offsetY;
        int tableWidth = rowDescColWidth + table.cellSpacing + getTotalDataColumnsWidth(cellWidthPixels);
        
        // Set appropriate color
        if (MODERN_STYLE) {
            g.setColor(getGridLineColor());
        } else {
            g.setColor(CircuitElm.lightGrayColor);
        }
        
        // Inset from edges to preserve rounded corners
        int inset = MODERN_STYLE ? CORNER_RADIUS / 2 : 0;
        
        // Draw double line above sum row
        if (isSumRow) {
            if (MODERN_STYLE) {
                g.setColor(getHeaderBorderColor());
            }
            g.drawLine(tableX + inset, rowY - 2, tableX + tableWidth - inset, rowY - 2);
            if (MODERN_STYLE) {
                g.setColor(getGridLineColor());
            }
        }
        
        // Bottom line - use stronger color for header rows
        if (MODERN_STYLE && isHeaderRow) {
            g.setColor(getHeaderBorderColor());
        }
        g.drawLine(tableX + inset, rowY + table.cellHeight, tableX + tableWidth - inset, rowY + table.cellHeight);
        
        // Reset to grid line color for vertical lines
        if (MODERN_STYLE) {
            g.setColor(getGridLineColor());
        }
        
        // Vertical lines - skip left edge (outer border handles it)
        int x = tableX + table.cellSpacing + rowDescColWidth;
        g.drawLine(x, rowY, x, rowY + table.cellHeight);
        
        // Between data columns (skip last column - right edge handled by outer border)
        for (int col = 0; col < table.getCols(); col++) {
            x = getColumnX(col, tableX, rowDescColWidth, cellWidthPixels);
            
            boolean drawDouble = false;
            if (col > 0 && col < table.getCols()) {
                ColumnType prevType = table.getColumnType(col - 1);
                ColumnType currType = table.getColumnType(col);
                if (prevType != null && currType != null && prevType != currType) {
                    drawDouble = true;
                }
            }
            
            if (drawDouble) {
                g.drawLine(x - 1, rowY, x - 1, rowY + table.cellHeight);
                g.drawLine(x + 1, rowY, x + 1, rowY + table.cellHeight);
            } else {
                g.drawLine(x, rowY, x, rowY + table.cellHeight);
            }
        }
    }
    
    protected void drawColumnTypeRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 3 / 2;
        int typeRowY = tableY + offsetY;
        
        // Draw row description column cell text with header font
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(getTextColor());
        table.drawCenteredText(g, "", tableX + table.cellSpacing + rowDescColWidth/2, typeRowY + table.cellHeight/2, true);
        
        // Draw column type cells text - merge adjacent cells with same type
        int col = 0;
        while (col < table.getCols()) {
            // Check if this is an A-L-E computed column
            boolean isALEColumn = (col == table.getCols() - 1 && table.getCols() >= 4);
            
            if (isALEColumn) {
                // A-L-E column: don't merge, just draw empty
                int cellX = getColumnX(col, tableX, rowDescColWidth, cellWidthPixels);
                int colWidth = getColumnWidth(col, cellWidthPixels);
                table.drawCenteredText(g, "", cellX + colWidth/2, typeRowY + table.cellHeight/2, true);
                col++;
            } else {
                // Regular column: find how many consecutive columns have the same type
                ColumnType type = table.getColumnType(col);
                String typeName = getColumnTypeName(type);
                
                int startCol = col;
                int endCol = col;
                
                // Count consecutive columns with same type (but stop before A-L-E column)
                while (endCol + 1 < table.getCols()) {
                    // Check if next column would be A-L-E
                    boolean nextIsALE = (endCol + 1 == table.getCols() - 1 && table.getCols() >= 4);
                    if (nextIsALE) break;
                    
                    ColumnType nextType = table.getColumnType(endCol + 1);
                    if (nextType == type) {
                        endCol++;
                    } else {
                        break;
                    }
                }
                
                // Calculate the center position across merged cells
                int startCellX = getColumnX(startCol, tableX, rowDescColWidth, cellWidthPixels);
                int endCellX = getColumnX(endCol, tableX, rowDescColWidth, cellWidthPixels);
                int endColWidth = getColumnWidth(endCol, cellWidthPixels);
                int centerX = startCellX + (endCellX - startCellX + endColWidth) / 2;
                
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
        int rowDescColWidth = cellWidthPixels * 3 / 2;
        int initialRowY = tableY + offsetY;
        
        // Draw row description cell for initial conditions with header font
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(getTextColor());
        table.drawCenteredText(g, "Initial", tableX + table.cellSpacing + rowDescColWidth/2, initialRowY + table.cellHeight/2, true);

        // Use cell font for values
        g.setFont(CELL_FONT);
        
        // Calculate A-L-E initial value using overridable method
        final double aleValue = getALEInitialValue();
        
        for (int col = 0; col < table.getCols(); col++) {
            int cellX = getColumnX(col, tableX, rowDescColWidth, cellWidthPixels);
            int colWidth = getColumnWidth(col, cellWidthPixels);
            
            // Get initial value
            double initialValue;
            boolean isALECol = hasALEColumn() && col == table.getCols() - 1;
            
            if (isALECol) {
                // ALE column: use pre-calculated value
                initialValue = aleValue;
            } else {
                initialValue = (col < table.columns.size()) 
                    ? table.columns.get(col).getInitialValue() : 0.0;
            }
            
            // Draw value with text color based on voltage (no background fill needed - canvas already colored)
            // For A-L-E columns, use blue if non-zero (indicates accounting discrepancy)
            if (isALECol && shouldColorComputedColumnBlue(col, initialValue)) {
                g.setColor(Color.blue);
            } else {
                g.setColor(getTextVoltageColor(initialValue));
            }
            String voltageText = formatDisplayValue(initialValue, table.tableUnits);
            table.drawCenteredText(g, voltageText, cellX + colWidth/2, initialRowY + table.cellHeight/2, true);
        }
        
        // Draw grid lines for this row
        drawRowGridLine(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
    }

    protected void drawTableCells(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 3 / 2;

        // Use the passed offsetY directly - no need to recalculate
        int baseY = offsetY;

        for (int row = 0; row < table.rows; row++) {
            int cellY = tableY + baseY + row * (table.cellHeight + table.cellSpacing);
            
            // Draw row description text with header font
            g.setFont(HEADER_FONT);
            g.setLetterSpacing(LETTER_SPACING);
            g.setColor(getTextColor());
            String rowDesc = (table.rowDescriptions != null && row < table.rowDescriptions.length) ?
                            table.rowDescriptions[row] : "Row" + (row + 1);
            
            // Truncate row description if needed to fit in cell (with 10px padding on each side)
            rowDesc = truncateText(rowDesc, g, rowDescColWidth - 20);
            
            table.drawCenteredText(g, rowDesc, tableX + table.cellSpacing + rowDescColWidth/2, cellY + table.cellHeight/2, true);
            
            // Use cell font for cell values
            g.setFont(CELL_FONT);
            
            // Pre-calculate A-L-E value for this row using cached cell values
            double totalAssets = 0.0, totalLiabilities = 0.0, totalEquity = 0.0;
            if (hasALEColumn()) {
                int regularColCount = getRegularColumnCount();
                for (int c = 0; c < regularColCount; c++) {
                    double cellValue = getCachedCellValue(row, c);
                    ColumnType type = getColumnType(c);
                    if (type == ColumnType.ASSET) totalAssets += cellValue;
                    else if (type == ColumnType.LIABILITY) totalLiabilities += cellValue;
                    else if (type == ColumnType.EQUITY) totalEquity += cellValue;
                }
            }
            final double aleRowValue = getALERowValue(row, totalAssets, totalLiabilities, totalEquity);
            
            // Draw data cells for this row
            for (int col = 0; col < table.getCols(); col++) {
                int cellX = getColumnX(col, tableX, rowDescColWidth, cellWidthPixels);
                int colWidth = getColumnWidth(col, cellWidthPixels);
                
                // Get voltage
                double voltage = getCachedCellValue(row, col);

                // No background fill needed - canvas is already filled with background color by CirSim
                
                // Check if this is an A-L-E column (computed, no equation)
                boolean isALECol = hasALEColumn() && col == table.getCols() - 1;
                
                // Display equation and voltage in cell (or just voltage for A-L-E)
                String equation = (col < table.columns.size()) ? table.columns.get(col).getCellEquation(row) : "";
                if (isALECol) {
                    voltage = aleRowValue;
                    // A-L-E column: ALWAYS display only the computed value (no equation)
                    // Color blue if non-zero (indicates accounting discrepancy), otherwise use voltage color
                    if (shouldColorComputedColumnBlue(col, voltage)) {
                        g.setColor(Color.blue);
                    } else {
                        g.setColor(getTextVoltageColor(voltage));
                    }
                    String voltageText = formatDisplayValue(voltage, table.tableUnits);
                    table.drawCenteredText(g, voltageText, cellX + colWidth/2, cellY + table.cellHeight/2, true);
                } else if (equation != null && !equation.trim().isEmpty()) {
                    // Regular cell: display based on showCellValues mode (0=Equation, 1=Equation:Value, 2=Value)
                    g.setColor(getTextVoltageColor(voltage));
                    String displayText;
                    
                    if (table.showCellValues == 2) {
                        // Mode 2: Show just "value"
                        displayText = formatDisplayValue(voltage, table.tableUnits);
                    } else if (table.showCellValues == 1) {
                        // Mode 1: Show "equation: value"
                        String equation_truncated = truncateEquation(equation, g);
                        String voltageText = formatDisplayValue(voltage, table.tableUnits);
                        displayText = equation_truncated + ": " + voltageText;
                    } else {
                        // Mode 0 (default): Show just "equation"
                        displayText = truncateEquation(equation, g);
                    }
                    
                    table.drawCenteredText(g, displayText, cellX + colWidth/2, cellY + table.cellHeight/2, true);
                }
            }
            
            // Draw grid lines for this row
            int rowY = tableY + baseY + row * (table.cellHeight + table.cellSpacing);
            drawRowGridLine(g, rowY - tableY, tableX, rowDescColWidth, cellWidthPixels, false);
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
        // Always show row description column (1.5x width for better readability)
        int rowDescColWidth = cellWidthPixels * 3 / 2;

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
        
        // Pre-calculate A-L-E sum value using accounting equation
        // Subclasses can override getALESumValue() for custom calculation
        final double aleSumValue = getALESumValue();
        
        for (int col = 0; col < table.getCols(); col++) {
            int cellX = getColumnX(col, tableX, rowDescColWidth, cellWidthPixels);
            int colWidth = getColumnWidth(col, cellWidthPixels);
            
            // Get the computed value from cache (updated twice per second)
            double computedValue;
            
            // Check if this is an A-L-E column
            boolean isALEColumn = hasALEColumn() && col == table.getCols() - 1;
            
            if (isALEColumn) {
                // ALE column: use pre-calculated value
                computedValue = aleSumValue;
            } else {
                computedValue = (cachedSumValues != null && col < cachedSumValues.length) 
                    ? cachedSumValues[col] : 0.0;
            }
            
            // No background fill needed - canvas is already filled with background color by CirSim
            
            // Draw value with text color based on voltage
            // For A-L-E columns, use blue if non-zero (indicates accounting discrepancy)
            if (isALEColumn && shouldColorComputedColumnBlue(col, computedValue)) {
                g.setColor(Color.blue);
            } else {
                g.setColor(getTextVoltageColor(computedValue));
            }
            String sumText = formatDisplayValue(computedValue, table.tableUnits);
            table.drawCenteredText(g, sumText, cellX + colWidth/2, sumRowY + table.cellHeight/2, true);
        }
        
        // Draw grid lines for computed row (with double line above)
        drawRowGridLine(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, true);
    }

    /**
     * Draw grid lines for the type row with merged cells (no vertical lines between same types)
     */
    private void drawTypeRowGridLines(Graphics g, int offsetY, int tableX, int rowDescColWidth, int cellWidthPixels) {
        int tableY = table.getTableY();
        int rowY = tableY + offsetY;
        
        // Modern style uses theme-aware colors
        if (MODERN_STYLE) {
            g.setColor(getGridLineColor());
        } else {
            g.setColor(CircuitElm.lightGrayColor);
        }
        int tableWidth = rowDescColWidth + table.cellSpacing + getTotalDataColumnsWidth(cellWidthPixels);
        
        // Inset from edges to preserve rounded corners
        int inset = MODERN_STYLE ? CORNER_RADIUS / 2 : 0;
        
        // Horizontal lines (top and bottom of row) - inset from edges
        g.drawLine(tableX + inset, rowY, tableX + tableWidth - inset, rowY);
        g.drawLine(tableX + inset, rowY + table.cellHeight, tableX + tableWidth - inset, rowY + table.cellHeight);
        
        // Vertical lines - skip left edge (outer border handles it)
        // After description column
        int x = tableX + table.cellSpacing + rowDescColWidth;
        g.drawLine(x, rowY, x, rowY + table.cellHeight);
        
        // Between data columns - merge cells with same type (no vertical lines between same types)
        // Skip right edge (outer border handles it)
        for (int col = 0; col < table.getCols(); col++) {
            x = getColumnX(col, tableX, rowDescColWidth, cellWidthPixels);
            
            // Check if we should draw a line at this position
            boolean drawDouble = false;
            boolean drawLine = false;
            
            if (col > 0 && col < table.getCols()) {
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
                    // Draw single vertical line
                    g.drawLine(x, rowY, x, rowY + table.cellHeight);
                }
            }
        }
    }
    
    /**
     * Draw grid lines for a single row
     * Protected to allow subclasses (CurrentTransactionsMatrixRenderer) to reuse this logic
     * @param isSumRow if true, draws a double line above the row
     */
    protected void drawRowGridLine(Graphics g, int offsetY, int tableX, int rowDescColWidth, int cellWidthPixels, boolean isSumRow) {
        int tableY = table.getTableY();
        int rowY = tableY + offsetY;
        
        // Modern style uses theme-aware colors, stronger border for sum row
        if (MODERN_STYLE) {
            g.setColor(isSumRow ? getHeaderBorderColor() : getGridLineColor());
        } else {
            g.setColor(CircuitElm.lightGrayColor);
        }
        int tableWidth = rowDescColWidth + table.cellSpacing + getTotalDataColumnsWidth(cellWidthPixels);
        
        // Inset from edges to preserve rounded corners
        int inset = MODERN_STYLE ? CORNER_RADIUS / 2 : 0;
        
        // Horizontal lines - inset from edges
        // Draw double line above sum row (stronger separator)
        if (isSumRow) {
            if (MODERN_STYLE) {
                g.setColor(getHeaderBorderColor()); // Use stronger color for footer separator
            }
            g.drawLine(tableX + inset, rowY - 2, tableX + tableWidth - inset, rowY - 2);
            if (MODERN_STYLE) {
                g.setColor(getGridLineColor()); // Back to subtle for other lines
            }
        }
        // Only draw bottom line (top line is drawn by table border or previous row's bottom)
        g.drawLine(tableX + inset, rowY + table.cellHeight, tableX + tableWidth - inset, rowY + table.cellHeight);
        
        // Vertical lines - skip left edge (outer border handles it)
        // After description column
        int x = tableX + table.cellSpacing + rowDescColWidth;
        g.drawLine(x, rowY, x, rowY + table.cellHeight);
        
        // Between data columns - skip right edge (outer border handles it)
        for (int col = 0; col < table.getCols(); col++) {
            x = getColumnX(col, tableX, rowDescColWidth, cellWidthPixels);
            
            // Check if we should draw a double line (when column type changes)
            boolean drawDouble = false;
            if (col > 0 && col < table.getCols()) {
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
