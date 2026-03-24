package com.lushprojects.circuitjs1.client.elements.economics;


public interface StockTableView {
    String getTableTitle();
    int getRows();
    int getCols();
    String getRowDescription(int row);
    String getColumnHeader(int col);

    default String[] getColumnNames() {
        int cols = getCols();
        String[] names = new String[cols];
        for (int col = 0; col < cols; col++) {
            names[col] = getColumnHeader(col);
        }
        return names;
    }

    default String[] getRowDescriptions() {
        int rows = getRows();
        String[] descriptions = new String[rows];
        for (int row = 0; row < rows; row++) {
            descriptions[row] = getRowDescription(row);
        }
        return descriptions;
    }

    default void onSharedStockAdded(String stockName) {
    }
}
