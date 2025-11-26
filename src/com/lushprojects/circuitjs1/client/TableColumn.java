/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;

/**
 * TableColumn - Encapsulates all data for a single column in a TableElm
 * 
 * This class consolidates column-related data that was previously spread across
 * multiple parallel arrays (stockNames, columnTypes, initialValues, cellEquations[][],
 * compiledExpressions[][], expressionStates[][], lastColumnSums[]).
 * 
 * Benefits:
 * - Automatic synchronization: all column data stays together
 * - Simpler operations: add/remove/move columns without managing parallel arrays
 * - Better encapsulation: column logic in one place
 * - Easier to extend: add new column properties without modifying TableElm
 */
public class TableColumn {
    /**
     * Column type enumeration for financial accounting
     */
    public enum ColumnType {
        ASSET,
        LIABILITY,
        EQUITY,
        COMPUTED  // A-L-E computed columns use this type
    }
    
    // Column identification and type
    private String stockName;           // Column header / stock name
    private ColumnType type;            // Asset, Liability, Equity, or Computed
    
    // Initial condition
    private double initialValue;        // Starting value for this column
    
    // Row data (one entry per row)
    private ArrayList<String> cellEquations;         // Equation strings
    private ArrayList<Expr> compiledExpressions;     // Compiled equation objects
    private ArrayList<ExprState> expressionStates;   // Expression evaluation states
    private ArrayList<Double> cachedCellValues;      // Cached cell values (computed in doStep)
    
    // Computed values
    private double lastSum;             // Last computed column sum (for voltage source)
    
    /**
     * Constructor for a new column
     */
    public TableColumn(String stockName, ColumnType type, double initialValue, int rowCount) {
        this.stockName = (stockName != null) ? stockName : "";
        this.type = (type != null) ? type : ColumnType.ASSET;
        this.initialValue = initialValue;
        this.lastSum = 0.0;
        
        // Initialize row arrays
        this.cellEquations = new ArrayList<String>();
        this.compiledExpressions = new ArrayList<Expr>();
        this.expressionStates = new ArrayList<ExprState>();
        this.cachedCellValues = new ArrayList<Double>();
        
        // Populate with empty/default values for each row
        for (int i = 0; i < rowCount; i++) {
            cellEquations.add("");
            compiledExpressions.add(null);
            expressionStates.add(new ExprState(1)); // Only need time variable
            cachedCellValues.add(0.0);
        }
    }
    
    /**
     * Factory method to create an A-L-E computed column
     */
    public static TableColumn createALE(int rowCount) {
        return new TableColumn("A-L-E", ColumnType.COMPUTED, 0.0, rowCount);
    }
    
    // Getters
    public String getStockName() { return stockName; }
    public ColumnType getType() { return type; }
    public double getInitialValue() { return initialValue; }
    public double getLastSum() { return lastSum; }
    
    // Setters
    public void setStockName(String name) { this.stockName = (name != null) ? name : ""; }
    public void setType(ColumnType type) { this.type = (type != null) ? type : ColumnType.ASSET; }
    public void setInitialValue(double value) { this.initialValue = value; }
    public void setLastSum(double sum) { this.lastSum = sum; }
    
    // Row count operations
    public int getRowCount() { return cellEquations.size(); }
    
    /**
     * Resize to match a new row count
     * Preserves existing data, adds/removes rows as needed
     */
    public void resizeRows(int newRowCount) {
        int currentRows = getRowCount();
        
        if (newRowCount > currentRows) {
            // Add rows
            for (int i = currentRows; i < newRowCount; i++) {
                cellEquations.add("");
                compiledExpressions.add(null);
                expressionStates.add(new ExprState(1));
                cachedCellValues.add(0.0);
            }
        } else if (newRowCount < currentRows) {
            // Remove rows from the end
            while (cellEquations.size() > newRowCount) {
                cellEquations.remove(cellEquations.size() - 1);
                compiledExpressions.remove(compiledExpressions.size() - 1);
                expressionStates.remove(expressionStates.size() - 1);
                cachedCellValues.remove(cachedCellValues.size() - 1);
            }
        }
    }
    
    // Cell equation access
    public String getCellEquation(int row) {
        if (row >= 0 && row < cellEquations.size()) {
            return cellEquations.get(row);
        }
        return "";
    }
    
    public void setCellEquation(int row, String equation) {
        if (row >= 0 && row < cellEquations.size()) {
            cellEquations.set(row, (equation != null) ? equation : "");
        }
    }
    
    // Compiled expression access
    public Expr getCompiledExpression(int row) {
        if (row >= 0 && row < compiledExpressions.size()) {
            return compiledExpressions.get(row);
        }
        return null;
    }
    
    public void setCompiledExpression(int row, Expr expr) {
        if (row >= 0 && row < compiledExpressions.size()) {
            compiledExpressions.set(row, expr);
        }
    }
    
    // Expression state access
    public ExprState getExpressionState(int row) {
        if (row >= 0 && row < expressionStates.size()) {
            return expressionStates.get(row);
        }
        return null;
    }
    
    public void setExpressionState(int row, ExprState state) {
        if (row >= 0 && row < expressionStates.size()) {
            expressionStates.set(row, state);
        }
    }
    
    // Cached cell value access
    public double getCachedCellValue(int row) {
        if (row >= 0 && row < cachedCellValues.size()) {
            return cachedCellValues.get(row);
        }
        return 0.0;
    }
    
    public void setCachedCellValue(int row, double value) {
        if (row >= 0 && row < cachedCellValues.size()) {
            cachedCellValues.set(row, value);
        }
    }
    
    /**
     * Insert a new row at the specified index
     */
    public void insertRow(int index) {
        if (index >= 0 && index <= cellEquations.size()) {
            cellEquations.add(index, "");
            compiledExpressions.add(index, null);
            expressionStates.add(index, new ExprState(1));
            cachedCellValues.add(index, 0.0);
        }
    }
    
    /**
     * Remove a row at the specified index
     */
    public void removeRow(int index) {
        if (index >= 0 && index < cellEquations.size()) {
            cellEquations.remove(index);
            compiledExpressions.remove(index);
            expressionStates.remove(index);
            cachedCellValues.remove(index);
        }
    }
    
    /**
     * Create a deep copy of this column
     */
    public TableColumn copy() {
        int rows = getRowCount();
        TableColumn copy = new TableColumn(this.stockName, this.type, this.initialValue, 0);
        
        // Copy all row data
        for (int i = 0; i < rows; i++) {
            copy.cellEquations.add(this.cellEquations.get(i));
            copy.compiledExpressions.add(this.compiledExpressions.get(i)); // Shallow copy of Expr
            ExprState originalState = this.expressionStates.get(i);
            copy.expressionStates.add(new ExprState(1)); // Create new state
            copy.cachedCellValues.add(this.cachedCellValues.get(i));
        }
        
        copy.lastSum = this.lastSum;
        return copy;
    }
    
    /**
     * Check if this is an A-L-E computed column
     */
    public boolean isALE() {
        return type == ColumnType.COMPUTED;
    }
    
    /**
     * Calculate A-L-E (Assets - Liabilities - Equity) from column values
     * This is a utility method that encapsulates the core A-L-E calculation logic.
     * 
     * @param columns List of columns to calculate from
     * @param valueExtractor Function to extract the value from each column (e.g., initial value, sum, etc.)
     * @return Calculated A-L-E value: sum(Assets) - sum(Liabilities) - sum(Equity)
     */
    public static double calculateALE(java.util.List<TableColumn> columns, ValueExtractor valueExtractor) {
        double assets = 0.0, liabilities = 0.0, equity = 0.0;
        
        for (TableColumn col : columns) {
            if (col.isALE()) continue; // Skip A-L-E columns themselves
            
            double value = valueExtractor.getValue(col);
            
            switch (col.getType()) {
                case ASSET:
                    assets += value;
                    break;
                case LIABILITY:
                    liabilities += value;
                    break;
                case EQUITY:
                    equity += value;
                    break;
                case COMPUTED:
                    // Skip computed columns
                    break;
            }
        }
        
        return assets - liabilities - equity;
    }
    
    /**
     * Functional interface for extracting values from columns
     * Allows flexible value extraction (initial values, sums, cached values, etc.)
     */
    public interface ValueExtractor {
        double getValue(TableColumn column);
    }
    
    /**
     * Check if stock name is empty or blank
     */
    public boolean isEmpty() {
        return stockName == null || stockName.trim().isEmpty();
    }
    
    @Override
    public String toString() {
        return "TableColumn[" + stockName + ", " + type + ", rows=" + getRowCount() + "]";
    }
}
