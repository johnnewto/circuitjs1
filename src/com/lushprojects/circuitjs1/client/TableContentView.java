package com.lushprojects.circuitjs1.client;

interface TableContentView extends StockTableView {
    String getCellEquation(int row, int col);
    int findColumnByStockName(String stockName);
    double getInitialValue(int col);
}
