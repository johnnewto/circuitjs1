package com.lushprojects.circuitjs1.client.elements.economics;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;

public interface TableContentView extends StockTableView {
    String getCellEquation(int row, int col);
    int findColumnByStockName(String stockName);
    double getInitialValue(int col);
}
