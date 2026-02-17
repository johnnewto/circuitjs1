/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.TableColumn.ColumnType;

/**
 * Renderer for SFCFlowTable.
 *
 * Uses the SFC visual style (Σ headers/rows) but displays solver-derived
 * column currents in the final Σ row.
 */
public class SFCFlowTableRenderer extends TableRenderer {
    private final SFCFlowTable flowTable;

    public SFCFlowTableRenderer(SFCFlowTable table) {
        super(table);
        this.flowTable = table;
    }

    @Override
    protected boolean hasALEColumn() {
        if (table.columns == null || table.columns.isEmpty()) {
            return false;
        }
        TableColumn lastCol = table.columns.get(table.columns.size() - 1);
        return lastCol.getType() == ColumnType.COMPUTED;
    }

    @Override
    protected void updateCachedValues() {
        int rows = Math.max(0, table.rows);
        int cols = Math.max(0, table.getCols());

        if (cachedCellValues == null || cachedCellValues.length != rows ||
            (rows > 0 && cachedCellValues[0].length != cols)) {
            cachedCellValues = new double[rows][cols];
        }
        if (cachedSumValues == null || cachedSumValues.length != cols) {
            cachedSumValues = new double[cols];
        }

        int sectorColCount = flowTable.getSectorColumnCount();
        int sigmaCol = hasALEColumn() ? (cols - 1) : -1;

        for (int row = 0; row < rows; row++) {
            double rowSum = 0.0;
            for (int col = 0; col < sectorColCount && col < cols; col++) {
                double value = 0.0;
                if (table.columns != null && col < table.columns.size()) {
                    value = table.columns.get(col).getCachedCellValue(row);
                }
                cachedCellValues[row][col] = value;
                rowSum += value;
            }
            if (sigmaCol >= 0 && sigmaCol < cols) {
                cachedCellValues[row][sigmaCol] = rowSum;
            }
        }

        for (int col = 0; col < cols; col++) {
            if (col < sectorColCount) {
                cachedSumValues[col] = flowTable.getStockColumnVoltage(col);
            } else {
                cachedSumValues[col] = flowTable.getStockVoltageTotal();
            }
        }
    }

    @Override
    protected void drawColumnHeaders(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 3 / 2;
        int rowY = tableY + offsetY;

        g.setFont(HEADER_FONT);
        g.setLetterSpacing(LETTER_SPACING);
        g.setColor(getTextColor());

        if (!table.collapsedMode) {
            table.drawCenteredText(g, "Transaction", tableX + table.cellSpacing + rowDescColWidth / 2,
                rowY + table.cellHeight / 2, true);
        }

        for (int col = 0; col < table.getCols(); col++) {
            String headerText;
            if (col < table.columns.size() && table.columns.get(col).getType() == ColumnType.COMPUTED) {
                headerText = "Σ";
            } else {
                headerText = table.getColumnHeader(col);
            }

            int cellX = getColumnX(col, tableX, rowDescColWidth, cellWidthPixels);
            int colWidth = getColumnWidth(col, cellWidthPixels);
            table.drawCenteredText(g, headerText, cellX + colWidth / 2, rowY + table.cellHeight / 2, true);
        }

        drawRowGridLine(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, false);
    }

    @Override
    protected boolean shouldShowTypeRow() {
        return false;
    }

    @Override
    protected void drawColumnTypeRow(Graphics g, int offsetY) {
    }

    @Override
    protected void drawSumRow(Graphics g, int offsetY) {
        int tableX = table.getTableX();
        int tableY = table.getTableY();
        int cellWidthPixels = table.getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 3 / 2;
        int sumRowY = tableY + offsetY;

        g.setFont(CELL_FONT);
        g.setColor(getTextColor());
        table.drawCenteredText(g, "Σ", tableX + table.cellSpacing + rowDescColWidth / 2,
            sumRowY + table.cellHeight / 2, true);

        int sectorColCount = flowTable.getSectorColumnCount();
        for (int col = 0; col < table.getCols(); col++) {
            double value;
            if (col < sectorColCount) {
                value = flowTable.getStockColumnVoltage(col);
            } else {
                value = flowTable.getStockVoltageTotal();
            }

            if (flowTable.shouldHighlightImbalances() && Math.abs(value) > flowTable.getBalanceTolerance()) {
                g.setColor(Color.red);
            } else {
                g.setColor(getTextVoltageColor(value));
            }

            String sumText = formatDisplayValue(value, table.tableUnits);
            int cellX = getColumnX(col, tableX, rowDescColWidth, cellWidthPixels);
            int colWidth = getColumnWidth(col, cellWidthPixels);
            table.drawCenteredText(g, sumText, cellX + colWidth / 2, sumRowY + table.cellHeight / 2, true);
        }

        drawRowGridLine(g, offsetY, tableX, rowDescColWidth, cellWidthPixels, true);
    }

    @Override
    protected double getALESumValue() {
        return flowTable.getStockVoltageTotal();
    }

    @Override
    protected double getALERowValue(int row, double totalAssets, double totalLiabilities, double totalEquity) {
        if (!hasALEColumn() || cachedCellValues == null || row >= cachedCellValues.length) {
            return 0.0;
        }
        return cachedCellValues[row][table.getCols() - 1];
    }

    @Override
    protected boolean shouldColorComputedColumnBlue(int col, double computedValue) {
        return false;
    }

    @Override
    protected String formatDisplayValue(double value, String units) {
        return super.formatDisplayValue(value, units);
    }
}