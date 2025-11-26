/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * Custom renderer for CurrentTransactionsMatrixElm.
 * Extends TableRenderer and adds table name row and replaces column headers with stock vars row.
 */
public class CurrentTransactionsMatrixRenderer extends TableRenderer {
    private final CurrentTransactionsMatrixElm matrixElm;
    
    public CurrentTransactionsMatrixRenderer(CurrentTransactionsMatrixElm table) {
        super(table);
        this.matrixElm = table;
    }
    
    /**
     * Override to properly map CTM rows to master table rows by flow name.
     * The base renderer assumes row indices match, but CTM collects flows from multiple tables
     * where the same flow name might be at different row indices.
     */
    @Override
    protected void updateCachedValues() {
        CirSim.console("CTM Renderer: updateCachedValues() called for table '" + table.getTableTitle() + "'");
        CirSim.console("  Rows: " + table.rows + ", Cols: " + table.getCols() + ", hasALE: " + hasALEColumn());
        
        if (cachedCellValues == null || cachedCellValues.length != table.rows || 
            (table.rows > 0 && cachedCellValues[0].length != table.getCols())) {
            cachedCellValues = new double[table.rows][table.getCols()];
            CirSim.console("  Initialized cachedCellValues array");
        }
        
        if (cachedSumValues == null || cachedSumValues.length != table.getCols()) {
            cachedSumValues = new double[table.getCols()];
            CirSim.console("  Initialized cachedSumValues array");
        }
        
        // For CTM: map flow names to master table rows
        int regularColCount = getRegularColumnCount();
        CirSim.console("CTM Renderer: Updating cached values for " + table.rows + " rows, " + regularColCount + " regular cols");
        
        for (int row = 0; row < table.rows; row++) {
            String flowName = table.getRowDescription(row);
            CirSim.console("  Row " + row + " flowName='" + flowName + "'");
            
            for (int col = 0; col < regularColCount; col++) {
                if (table.columns != null && col < table.columns.size()) {
                    TableColumn column = table.columns.get(col);
                    String stockName = column.getStockName();
                    
                    // CTM is never a master, always fetches from source tables
                    TableElm masterTable = ComputedValues.getMasterTable(stockName);
                    
                    if (masterTable != null && masterTable.columns != null) {
                        // Find the row in the master table that matches this flow name
                        int masterRow = matrixElm.findRowByFlowName(masterTable, flowName);
                        int masterCol = masterTable.findColumnByStockName(stockName);
                        
                        CirSim.console("    Col " + col + " stock='" + stockName + "' masterTable='" + 
                                     masterTable.getTableTitle() + "' masterRow=" + masterRow + " masterCol=" + masterCol);
                        
                        if (masterRow >= 0 && masterCol >= 0 && masterCol < masterTable.columns.size()) {
                            TableColumn masterColumn = masterTable.columns.get(masterCol);
                            double value = masterColumn.getCachedCellValue(masterRow);
                            
                            // Debug: check if this is actually from cache
                            CirSim.console("      -> Master column '" + masterColumn.getStockName() + "' cachedCellValue[" + masterRow + "] = " + value);
                            CirSim.console("      -> Master column lastSum = " + masterColumn.getLastSum());
                            
                            cachedCellValues[row][col] = value;
                            CirSim.console("      -> Final value stored: " + value);
                        } else {
                            cachedCellValues[row][col] = 0.0;
                            CirSim.console("      -> Not found, using 0.0");
                        }
                    } else {
                        cachedCellValues[row][col] = 0.0;
                        CirSim.console("    Col " + col + " stock='" + stockName + "' -> No master table, using 0.0");
                    }
                } else {
                    cachedCellValues[row][col] = 0.0;
                }
            }
        }
        
        // Update column sum values (computed row) - CTM always fetches from masters
        CirSim.console("CTM Renderer: Calculating column sums for " + regularColCount + " regular columns");
        for (int col = 0; col < regularColCount; col++) {
            cachedSumValues[col] = getRegularColumnSum(col);
            CirSim.console("  Col " + col + " sum: " + cachedSumValues[col]);
        }
        
        // Calculate ALE column: for CTM, each row's ALE is the sum of all values in that row
        if (hasALEColumn()) {
            int aleCol = table.getCols() - 1;
            CirSim.console("CTM ALE: Calculating for " + table.rows + " rows, " + regularColCount + " regular columns");
            
            // For each row, calculate ALE as sum of all regular columns
            for (int row = 0; row < table.rows; row++) {
                double rowSum = 0.0;
                String flowName = table.getRowDescription(row);
                CirSim.console("  Row " + row + " ('" + flowName + "'):");
                
                for (int col = 0; col < regularColCount; col++) {
                    double value = cachedCellValues[row][col];
                    String stockName = (col < table.columns.size()) ? table.columns.get(col).getStockName() : "?";
                    CirSim.console("    Col " + col + " (" + stockName + "): " + value);
                    rowSum += value;
                }
                
                cachedCellValues[row][aleCol] = rowSum;
                CirSim.console("    -> Row ALE sum: " + rowSum);
            }
            
            // ALE column sum is the sum of all row ALE values
            double aleColumnSum = 0.0;
            for (int row = 0; row < table.rows; row++) {
                aleColumnSum += cachedCellValues[row][aleCol];
            }
            cachedSumValues[aleCol] = aleColumnSum;
            CirSim.console("  -> Total ALE column sum: " + aleColumnSum);
        }
    }
    
    /**
     * Override A-L-E initial value calculation for CTM.
     * For CTM, initial A-L-E is always 0 (sum of initial row sums).
     */
    @Override
    protected double getALEInitialValue() {
        // CTM initial A-L-E is sum of all initial values across all regular columns
        // Since each flow starts at 0, the sum is 0
        if (!hasALEColumn()) {
            return 0.0;
        }
        
        double sum = 0.0;
        for (int col = 0; col < getRegularColumnCount(); col++) {
            if (col < table.columns.size()) {
                sum += table.columns.get(col).getInitialValue();
            }
        }
        return sum;
    }
    
    /**
     * Override A-L-E sum calculation for CTM.
     * For CTM, the sum row A-L-E should be the sum of all regular columns in the sum row.
     * This gives the total across all flows and all stocks.
     */
    @Override
    protected double getALESumValue() {
        if (!hasALEColumn()) {
            return 0.0;
        }
        
        // Sum all regular columns in the sum row
        double total = 0.0;
        int regularColCount = getRegularColumnCount();
        for (int col = 0; col < regularColCount; col++) {
            if (cachedSumValues != null && col < cachedSumValues.length) {
                total += cachedSumValues[col];
            }
        }
        return total;
    }
    
    /**
     * Override A-L-E row calculation for CTM.
     * CTM uses direct value from cached cell values (already calculated as row sums).
     */
    @Override
    protected double getALERowValue(int row, double totalAssets, double totalLiabilities, double totalEquity) {
        if (!hasALEColumn()) {
            return 0.0;
        }
        int aleCol = table.getCols() - 1;
        return getCachedCellValue(row, aleCol);
    }
    
    /**
     * CTM has an extra table name row before the type row
     */
    @Override
    protected int getExtraRowsBeforeTypeRowHeight() {
        return table.cellHeight + table.cellSpacing;
    }
    
    /**
     * Override to insert table name row before the type row
     */
    @Override
    protected int drawExtraRowsBeforeTypeRow(Graphics g, int currentY) {
        drawTableNameRow(g, currentY);
        return currentY + table.cellHeight + table.cellSpacing;
    }
    
    /**
     * Override column headers to draw stock variable names instead
     */
    @Override
    protected void drawColumnHeaders(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 2;
        int rowY = tableY + offsetY;
        
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(CircuitElm.whiteColor);
        
        // Draw "Flows↓/Stock Vars →" label in row description column (skip in collapsed mode)
        if (!table.collapsedMode) {
            table.drawCenteredText(g, "Flows↓/Stock Vars →", tableX + table.cellSpacing + rowDescColWidth/2, 
                rowY + table.cellHeight/2, true);
        }
        
        // Draw stock variable name for each column
        for (int col = 0; col < table.getCols(); col++) {
            String stockVarName = matrixElm.getOutputName(col);
            
            int centerX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                col * (cellWidthPixels + table.cellSpacing) + cellWidthPixels/2;
            
            table.drawCenteredText(g, stockVarName, centerX, rowY + table.cellHeight/2, true);
        }
        
        drawRowGridLine(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
    }
    
    /**
     * Draw table name row - shows source table name in each column header
     * Similar to TableEditDialog's table name row
     */
    private void drawTableNameRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 2;
        int rowY = tableY + offsetY;
        
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(CircuitElm.whiteColor);
        
        // Draw empty row description
        table.drawCenteredText(g, "", tableX + table.cellSpacing + rowDescColWidth/2, 
            rowY + table.cellHeight/2, true);
        
        // Draw table name in each column header
        for (int col = 0; col < table.getCols(); col++) {
            String tableName = matrixElm.getSourceTableName(col);
            if (tableName == null || tableName.isEmpty()) {
                tableName = matrixElm.getOutputName(col);
            }
            
            int centerX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                col * (cellWidthPixels + table.cellSpacing) + cellWidthPixels/2;
            
            table.drawCenteredText(g, tableName, centerX, rowY + table.cellHeight/2, true);
        }
        
        drawRowGridLine(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
    }
    
    /**
     * Draw column type row - shows Asset/Liability/Equity indicators
     * Similar to TableEditDialog's type row
     */
    @Override
    protected void drawColumnTypeRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 2;
        int rowY = tableY + offsetY;
        
        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(CircuitElm.whiteColor);
        
        // Draw "Type:" label in row description column
        table.drawCenteredText(g, "Type:", tableX + table.cellSpacing + rowDescColWidth/2, 
            rowY + table.cellHeight/2, true);
        
        // Draw type for each column
        for (int col = 0; col < table.getCols(); col++) {
            String typeText = "";
            
            // Check if this is an A-L-E column (should show blank type)
            if (col < table.columns.size() && !table.columns.get(col).isALE()) {
                typeText = getColumnTypeName(table.getColumnType(col));
            }
            
            int centerX = tableX + rowDescColWidth + table.cellSpacing * 2 + 
                col * (cellWidthPixels + table.cellSpacing) + cellWidthPixels/2;
            
            table.drawCenteredText(g, typeText, centerX, rowY + table.cellHeight/2, true);
        }
        
        drawRowGridLine(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
    }

}

