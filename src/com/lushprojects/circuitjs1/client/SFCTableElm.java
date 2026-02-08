/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.TableColumn.ColumnType;
import java.util.ArrayList;

/**
 * SFCTableElm - Stock-Flow Consistent Table Element
 * 
 * A standalone table for stock-flow consistent (SFC) macroeconomic modeling.
 * Implements quadruple-entry accounting with:
 * - Columns representing economic sectors (Households, Firms, Banks, Govt)
 * - Rows representing transaction types (Consumption, Wages, Interest, Taxes)
 * - Σ column showing row sums (horizontal consistency - must equal 0)
 * - Σ row showing column sums (vertical consistency - must equal 0)
 * 
 * Key features:
 * - Pre-populated with example sectors and transactions
 * - Uses SECTOR column type for economic sectors
 * - Replaces A-L-E with Σ for sum columns
 * - Display-only element (no circuit connections)
 * - Red highlighting for non-zero sums (balance errors)
 */
public class SFCTableElm extends TableElm {
    private static final String SFC_TITLE = "SFC Table";
    
    // SFC-specific properties
    protected boolean highlightImbalances = true; // Red highlighting for non-zero Σ values
    protected double balanceTolerance = 1e-6;   // Tolerance for balance checking
    
    // Custom renderer for SFC tables
    private SFCTableRenderer sfcRenderer;
    
    // Static counter for table numbering
    private static int nextSFCTableNumber = 1;
    
    /**
     * Constructor for new SFC table created from menu.
     * Creates pre-populated structure with example sectors and transactions.
     */
    public SFCTableElm(int xx, int yy) {
        super(xx, yy);
        
        // Set SFC-specific defaults
        tableTitle = SFC_TITLE + " " + nextSFCTableNumber;
        nextSFCTableNumber++;
        showALE = false;  // We use Σ instead of A-L-E
        showInitialValues = false;  // SFC tables don't use initial values
        collapsedMode = false;
        
        // SFC tables are display-only - use converged values for stable display
        if (equationManager != null) {
            equationManager.setUseConvergedValues(true);
        }
        
        // Initialize with pre-populated structure
        initializeSFCTable();
        
        // Create custom renderer
        sfcRenderer = new SFCTableRenderer(this);
        
        setupPins();
        setPoints();
    }
    
    /**
     * Constructor for loading SFC table from file.
     */
    public SFCTableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        
        showALE = false;  // We use Σ instead of A-L-E
        
        // SFC tables are display-only - use converged values for stable display
        if (equationManager != null) {
            equationManager.setUseConvergedValues(true);
        }
        
        // Parse SFC-specific properties if available
        if (st.hasMoreTokens()) {
            try {
                highlightImbalances = Boolean.parseBoolean(st.nextToken());
            } catch (Exception e) {
                highlightImbalances = true;
            }
        }
        if (st.hasMoreTokens()) {
            try {
                balanceTolerance = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                balanceTolerance = 1e-6;
            }
        }
        
        // Update counter based on loaded title
        updateSFCTableCounter(tableTitle);
        
        // Create custom renderer
        sfcRenderer = new SFCTableRenderer(this);
        
        setupPins();
        setPoints();
    }
    
    /**
     * Initialize the SFC table with pre-populated sectors and transactions.
     */
    private void initializeSFCTable() {
        // Define sectors (columns)
        String[] sectorNames = {"Households", "Firms", "Banks", "Govt"};
        
        // Define transactions (rows)
        String[] transactionNames = {"Consumption", "Wages", "Interest", "Taxes"};
        
        // Pre-defined equations for balanced example
        // Each row sums to 0 (horizontal consistency)
        String[][] equations = {
            // Consumption: HH pays, Firms receive
            {"-100", "+100", "0", "0"},
            // Wages: Firms pay, HH receive
            {"+80", "-80", "0", "0"},
            // Interest: Banks pay to HH, receive from Firms
            {"+5", "-10", "+5", "0"},
            // Taxes: HH and Firms pay, Govt receives
            {"-20", "-15", "0", "+35"}
        };
        
        // Set up rows
        rows = transactionNames.length;
        rowDescriptions = new String[rows];
        for (int i = 0; i < rows; i++) {
            rowDescriptions[i] = transactionNames[i];
        }
        
        // Set up columns with SECTOR type
        columns = new ArrayList<TableColumn>();
        for (int col = 0; col < sectorNames.length; col++) {
            TableColumn column = new TableColumn(sectorNames[col], ColumnType.SECTOR, 0.0, rows);
            
            // Set equations for each cell
            for (int row = 0; row < rows; row++) {
                column.setCellEquation(row, equations[row][col]);
            }
            
            columns.add(column);
        }
        
        // Add Σ column (computed, for row sums)
        TableColumn sigmaColumn = new TableColumn("Σ", ColumnType.COMPUTED, 0.0, rows);
        columns.add(sigmaColumn);
        
        // Compile all equations
        equationManager.recompileAllEquations();
    }
    
    @Override
    int getDumpType() { 
        return 265;  // Unique dump type for SFC tables
    }
    
    @Override
    public String dump() {
        // Include SFC-specific properties in dump
        return super.dump() + " " + highlightImbalances + " " + balanceTolerance;
    }
    
    /**
     * SFC table is display-only - no circuit connections.
     * Return 0 posts to avoid creating unconnected circuit nodes.
     */
    @Override
    public int getPostCount() {
        return 0;  // Display-only, no circuit nodes
    }
    
    /**
     * SFC table is display-only - no circuit connections
     */
    @Override
    void setupPins() {
        // Calculate size based on table dimensions
        int cellWidthPixels = getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels * 3 / 2;
        
        // Standard rows: header + data rows + sum row
        int totalRows = rows + 2; // +2 for header and sum (Σ) rows
        
        int tableWidthPixels = (rowDescColWidth + getCols() * cellWidthPixels) + 2 * cellSpacing;
        int tableHeightPixels = (totalRows + 2) * cellHeight + (totalRows + 3) * cellSpacing + 20;
        
        sizeX = (tableWidthPixels + cspc2 - 1) / cspc2;
        sizeY = (tableHeightPixels + cspc2 - 1) / cspc2;
        
        // No pins needed for display-only table
        pins = new Pin[0];
    }
    
    /**
     * SFC table evaluates equations and caches values for display,
     * but doesn't drive circuit voltages.
     */
    @Override
    public void doStep() {
        if (columns == null || rows == 0) {
            return;
        }
        
        // Evaluate all NON-Σ columns (SECTOR columns)
        for (int col = 0; col < columns.size(); col++) {
            TableColumn column = columns.get(col);
            
            // Skip the Σ column (COMPUTED type) - it's calculated in the renderer
            if (column.getType() == ColumnType.COMPUTED) {
                continue;
            }
            
            // Evaluate equations for this column and cache individual cell values
            double columnSum = 0.0;
            for (int row = 0; row < rows; row++) {
                double cellValue = equationManager.getVoltageForCell(row, col);
                column.setCachedCellValue(row, cellValue);
                columnSum += cellValue;
            }
            
            column.setLastSum(columnSum);
        }
    }
    
    /**
     * SFC table doesn't need post-step processing
     */
    @Override
    public void stepFinished() {
        // No post-step processing needed for display-only table
    }
    
    /**
     * SFC table doesn't stamp anything to the circuit matrix.
     * Pure computational element - no MNA participation.
     */
    @Override
    void stamp() {
        // Pure computational - no circuit connections
    }
    
    /**
     * SFC table doesn't have voltage sources.
     * Pure computational element - no MNA participation.
     */
    @Override
    int getVoltageSourceCount() {
        return 0;
    }
    
    /**
     * SFC table doesn't need internal nodes.
     * Pure computational element - no MNA participation.
     */
    @Override
    int getInternalNodeCount() {
        return 0;
    }
    
    /**
     * SFC table is not nonlinear (it's purely computational).
     * Returns false to avoid unnecessary convergence iterations.
     */
    @Override
    public boolean nonLinear() {
        return false;
    }
    
    /**
     * SFC table has no electrical connections between terminals.
     * Pure computational element - no MNA participation.
     */
    @Override
    public boolean getConnection(int n1, int n2) {
        return false;
    }
    
    /**
     * SFC table has no ground connections.
     * Pure computational element - no MNA participation.
     */
    @Override
    public boolean hasGroundConnection(int n) {
        return false;
    }
    
    /**
     * SFC table doesn't register its columns as stocks.
     * This prevents sector names from appearing in the variable browser/suggest box.
     */
    @Override
    protected boolean shouldRegisterStocks() {
        return false;  // Display-only, don't register stocks
    }
    
    /**
     * Calculate row sum (for horizontal consistency check)
     * @param row Row index
     * @return Sum of all sector columns in this row
     */
    public double getRowSum(int row) {
        if (row < 0 || row >= rows || columns == null) {
            return 0.0;
        }
        
        double sum = 0.0;
        for (int col = 0; col < columns.size(); col++) {
            TableColumn column = columns.get(col);
            // Skip Σ column (computed)
            if (column.getType() == ColumnType.COMPUTED) {
                continue;
            }
            sum += getVoltageForCell(row, col);
        }
        return sum;
    }
    
    /**
     * Calculate column sum (for vertical consistency check)
     * @param col Column index
     * @return Sum of all rows in this column
     */
    public double getColumnSum(int col) {
        if (col < 0 || col >= getCols() || columns == null) {
            return 0.0;
        }
        
        // Skip Σ column
        TableColumn column = columns.get(col);
        if (column.getType() == ColumnType.COMPUTED) {
            return 0.0;
        }
        
        double sum = 0.0;
        for (int row = 0; row < rows; row++) {
            sum += getVoltageForCell(row, col);
        }
        return sum;
    }
    
    /**
     * Check if a row is balanced (sum equals 0 within tolerance)
     * @param row Row index
     * @return true if row sum is within tolerance of 0
     */
    public boolean isRowBalanced(int row) {
        return Math.abs(getRowSum(row)) <= balanceTolerance;
    }
    
    /**
     * Check if a column is balanced (sum equals 0 within tolerance)
     * @param col Column index
     * @return true if column sum is within tolerance of 0
     */
    public boolean isColumnBalanced(int col) {
        return Math.abs(getColumnSum(col)) <= balanceTolerance;
    }
    
    /**
     * Check if the entire table is balanced
     * @return true if all rows and columns sum to 0
     */
    public boolean isFullyBalanced() {
        // Check all rows
        for (int row = 0; row < rows; row++) {
            if (!isRowBalanced(row)) {
                return false;
            }
        }
        
        // Check all sector columns
        for (int col = 0; col < getCols(); col++) {
            if (columns.get(col).getType() != ColumnType.COMPUTED && !isColumnBalanced(col)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get the number of sector columns (excluding Σ column)
     */
    public int getSectorColumnCount() {
        if (columns == null) return 0;
        int count = 0;
        for (TableColumn col : columns) {
            if (col.getType() == ColumnType.SECTOR) {
                count++;
            }
        }
        return count;
    }
    
    @Override
    void draw(Graphics g) {
        if (sfcRenderer != null) {
            sfcRenderer.draw(g);
        } else {
            super.draw(g);
        }
    }
    
    @Override
    String getChipName() {
        return "SFC Table";
    }
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "SFC Table (" + rows + " transactions × " + getSectorColumnCount() + " sectors)";
        arr[1] = isFullyBalanced() ? "✓ Fully Balanced" : "⚠ Imbalanced";
        
        int idx = 2;
        
        // Show grand total
        if (idx < arr.length) {
            double grandTotal = getGrandTotal();
            arr[idx++] = "Grand Total (Σ): " + CircuitElm.showFormat.format(grandTotal);
        }
        
        // Show sector names
        if (columns != null && idx < arr.length) {
            StringBuilder sectors = new StringBuilder("Sectors: ");
            boolean first = true;
            for (TableColumn col : columns) {
                if (col.getType() == ColumnType.SECTOR) {
                    if (!first) sectors.append(", ");
                    sectors.append(col.getStockName());
                    first = false;
                }
            }
            arr[idx++] = sectors.toString();
        }
        
        // Show balance info
        if (idx < arr.length) {
            int imbalancedRows = 0;
            for (int row = 0; row < rows; row++) {
                if (!isRowBalanced(row)) imbalancedRows++;
            }
            if (imbalancedRows > 0) {
                arr[idx++] = imbalancedRows + " imbalanced row(s)";
            }
        }
    }
    
    @Override
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            return new EditInfo("Table Title", tableTitle);
        }
        if (n == 1) {
            EditInfo ei = new EditInfo("Highlight Imbalances", 0, -1, -1);
            ei.checkbox = new Checkbox("", highlightImbalances);
            return ei;
        }
        if (n == 2) {
            return new EditInfo("Balance Tolerance", balanceTolerance, 0, 1);
        }
        if (n == 3) {
            return new EditInfo("Cell Width (grids)", cellWidthInGrids, 1, 20);
        }
        if (n == 4) {
            return new EditInfo("Cell Height", cellHeight, 16, 48);
        }
        if (n == 5) {
            EditInfo ei = new EditInfo("Cell Display Mode", "");
            ei.choice = new Choice();
            ei.choice.add("Equation");
            ei.choice.add("Equation: Value");
            ei.choice.add("Value");
            ei.choice.select(showCellValues);
            return ei;
        }
        return null;
    }
    
    @Override
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            tableTitle = ei.textf.getText();
        } else if (n == 1) {
            highlightImbalances = ei.checkbox.getValue();
        } else if (n == 2) {
            balanceTolerance = ei.value;
        } else if (n == 3) {
            cellWidthInGrids = Math.max(1, (int)ei.value);
        } else if (n == 4) {
            cellHeight = (int)ei.value;
        } else if (n == 5) {
            showCellValues = ei.choice.getSelectedIndex();
        }
        
        setupPins();
        setPoints();
    }
    
    // Accessors for SFC-specific properties
    public boolean shouldHighlightImbalances() {
        return highlightImbalances;
    }
    
    public double getBalanceTolerance() {
        return balanceTolerance;
    }
    
    /**
     * Get the grand total (bottom-right cell) - sum of all column sums.
     * For a balanced SFC table, this should equal 0.
     * This is useful for testing and validation.
     * @return The grand total (sum of all Σ row values)
     */
    public double getGrandTotal() {
        if (sfcRenderer != null) {
            return sfcRenderer.getGrandTotal();
        }
        // Fallback: calculate directly
        double total = 0.0;
        for (int col = 0; col < getCols(); col++) {
            if (columns != null && col < columns.size() && 
                columns.get(col).getType() != ColumnType.COMPUTED) {
                total += getColumnSum(col);
            }
        }
        return total;
    }
    
    /**
     * Get the sum value for a specific column (Σ row value).
     * @param col Column index
     * @return The column sum from the Σ row
     */
    public double getSumRowValue(int col) {
        if (sfcRenderer != null) {
            return sfcRenderer.getCachedSumValue(col);
        }
        return getColumnSum(col);
    }
    
    /**
     * Get the row sum value (Σ column value) for a specific row.
     * @param row Row index
     * @return The row sum from the Σ column
     */
    public double getSigmaColumnValue(int row) {
        if (sfcRenderer != null) {
            return sfcRenderer.getCachedCellValue(row, getCols() - 1);
        }
        return getRowSum(row);
    }
    
    /**
     * Reset SFC table counter
     */
    public static void resetSFCTableCounter() {
        nextSFCTableNumber = 1;
    }
    
    /**
     * Update counter based on existing table title
     */
    private static void updateSFCTableCounter(String title) {
        if (title != null && title.startsWith(SFC_TITLE + " ")) {
            try {
                String numStr = title.substring((SFC_TITLE + " ").length());
                int num = Integer.parseInt(numStr.trim());
                if (num >= nextSFCTableNumber) {
                    nextSFCTableNumber = num + 1;
                }
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
        }
    }
}
