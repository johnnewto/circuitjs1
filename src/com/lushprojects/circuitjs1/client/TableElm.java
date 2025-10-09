/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.Button;

/**
 * Simplified Table Element - Displays voltage values from labeled nodes
 * Extends CircuitElm for lightweight text-based label display
 */
public class TableElm extends CircuitElm {
    protected int rows = 3;
    protected int cols = 2; 
    protected int cellSize = 60;
    protected int cellSpacing = 4;
    protected String[][] labelNames;  // Store label names as text
    protected String[] columnHeaders;
    protected boolean showColumnSums = true; // Show sum row at bottom
    
    // Computed values are now stored in LabeledNodeElm.labelList - no JavaScript needed
    
    // Constructor for new table
    public TableElm(int xx, int yy) {
        super(xx, yy);
        initTable();
    }
    
    // File loading constructor  
    public TableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        parseTableData(st);
        initTable();
    }
    
    private void initTable() {
        // Initialize label names array
        if (labelNames == null) {
            labelNames = new String[rows][cols];
            // Set default label names
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    labelNames[row][col] = "node" + (row * cols + col + 1);
                }
            }
        }
        
        // Initialize column headers if not set
        if (columnHeaders == null) {
            columnHeaders = new String[cols];
            for (int i = 0; i < cols; i++) {
                columnHeaders[i] = "Col" + (i + 1);
            }
        }
        
        // Ensure no null or empty values exist
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (labelNames[row][col] == null || labelNames[row][col].trim().isEmpty()) {
                    labelNames[row][col] = "node" + (row * cols + col + 1);
                }
            }
        }
        
        for (int i = 0; i < cols; i++) {
            if (columnHeaders[i] == null || columnHeaders[i].trim().isEmpty()) {
                columnHeaders[i] = "Col" + (i + 1);
            }
        }
    }
    
    protected double getVoltageForLabel(String labelName) {
        if (labelName == null || labelName.isEmpty()) {
            return 0.0;
        }
        
        // First check if this is a computed value (like a column sum)
        Double computedValue = LabeledNodeElm.getComputedValue(labelName);
        if (computedValue != null) {
            return computedValue.doubleValue();
        }
        
        // Use LabeledNodeElm's static labelList to get voltage
        Integer nodeNum = LabeledNodeElm.getByName(labelName);
        if (nodeNum == null) {
            return 0.0; // Label not found
        }
        
        // Node 0 is ground
        if (nodeNum == 0) {
            return 0.0;
        }
        
        // Get voltage from simulation
        if (sim != null && sim.nodeVoltages != null && 
            nodeNum > 0 && nodeNum <= sim.nodeVoltages.length) {
            return sim.nodeVoltages[nodeNum - 1]; // nodeVoltages is 0-indexed, excludes ground
        }
        
        return 0.0;
    }
    
    protected void registerComputedValueAsLabeledNode(String labelName, double voltage) {
        if (labelName == null || labelName.isEmpty()) {
            return;
        }
        
        // Store the computed voltage value in LabeledNodeElm
        LabeledNodeElm.setComputedValue(labelName, voltage);
    }
    
    // Static method to get computed values by other elements
    public static Double getComputedValue(String labelName) {
        return LabeledNodeElm.getComputedValue(labelName);
    }
    
    void setPoints() {
        super.setPoints();
        
        // Calculate table dimensions
        int tableWidth = cols * cellSize + (cols + 1) * cellSpacing;
        int extraRows = showColumnSums ? 1 : 0; // Add extra row for sums
        int tableHeight = (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20; // Extra space for headers
        
        // Set bounding box
        setBbox(point1.x, point1.y, point1.x + tableWidth, point1.y + tableHeight);
    }

    int getPostCount() { 
        return 0; // No electrical connections
    }
    
    void draw(Graphics g) {
        int extraRows = showColumnSums ? 1 : 0;
        
        // Draw table background
        g.setColor(needsHighlight() ? selectColor : Color.white);
        g.fillRect(point1.x, point1.y, 
                   cols * cellSize + (cols + 1) * cellSpacing,
                   (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20);
        
        // Draw table border
        g.setColor(Color.black);
        g.drawRect(point1.x, point1.y,
                   cols * cellSize + (cols + 1) * cellSpacing,
                   (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20);
        
        // Draw column headers
        drawColumnHeaders(g);
        
        // Draw table cells with voltages
        drawTableCells(g);
        
        // Draw sum row if enabled
        if (showColumnSums) {
            drawSumRow(g);
        }
        
        // Draw grid lines
        drawGridLines(g);
    }

    private void drawColumnHeaders(Graphics g) {
        g.setColor(Color.black);
        int headerY = point1.y + 15;
        
        for (int col = 0; col < cols; col++) {
            int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
            String header = (columnHeaders != null && col < columnHeaders.length) ? 
                           columnHeaders[col] : "Col" + (col + 1);
            drawCenteredText(g, header, cellX + cellSize/2, headerY, true);

        }
    }

    private void drawTableCells(Graphics g) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
                int cellY = point1.y + 20 + cellSpacing + row * (cellSize + cellSpacing);
                
                // Get label name and voltage
                String labelName = labelNames[row][col];
                double voltage = getVoltageForLabel(labelName);
                
                // Draw cell background based on voltage (optional)
                setVoltageColor(g, voltage);
                g.fillRect(cellX, cellY, cellSize, cellSize);
                
                // Draw cell border
                g.setColor(Color.black);
                g.drawRect(cellX, cellY, cellSize, cellSize);
                
                // Draw label name (top half)
                g.setColor(Color.black);
                drawCenteredText(g, labelName, cellX + cellSize/2, cellY + cellSize/3, true);
                
                // Draw voltage value (bottom half)
                String voltageText = getVoltageText(voltage);
                drawCenteredText(g, voltageText, cellX + cellSize/2, cellY + 2*cellSize/3, true);
            }
        }
    }

    protected void drawSumRow(Graphics g) {
        int sumRowY = point1.y + 20 + cellSpacing + rows * (cellSize + cellSpacing);
        
        for (int col = 0; col < cols; col++) {
            int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
            
            // Get the already-calculated sum from computed values (calculated in stepFinished())
            String sumLabelName = columnHeaders[col];
            Double computedSum = LabeledNodeElm.getComputedValue(sumLabelName);
            double columnSum = (computedSum != null) ? computedSum.doubleValue() : 0.0;
            
            // Draw sum cell background (slightly different color)
            g.setColor(Color.lightGray);
            g.fillRect(cellX, sumRowY, cellSize, cellSize);
            
            // Draw cell border
            g.setColor(Color.black);
            g.drawRect(cellX, sumRowY, cellSize, cellSize);
            
            // Draw column header text as label (top half)
            g.setColor(Color.black);
            drawCenteredText(g, sumLabelName, cellX + cellSize/2, sumRowY + cellSize/3, true);
            
            // Draw sum value (bottom half)
            String sumText = getVoltageText(columnSum);
            drawCenteredText(g, sumText, cellX + cellSize/2, sumRowY + 2*cellSize/3, true);
        }
    }

    private void drawGridLines(Graphics g) {
        g.setColor(Color.gray);
        int extraRows = showColumnSums ? 1 : 0;
        
        // Vertical lines
        for (int col = 0; col <= cols; col++) {
            int x = point1.x + cellSpacing + col * (cellSize + cellSpacing);
            g.drawLine(x, point1.y + 20, x, point1.y + 20 + cellSpacing + (rows + extraRows) * (cellSize + cellSpacing));
        }
        
        // Horizontal lines
        for (int row = 0; row <= rows + extraRows; row++) {
            int y = point1.y + 20 + cellSpacing + row * (cellSize + cellSpacing);
            g.drawLine(point1.x, y, point1.x + cellSpacing + cols * (cellSize + cellSpacing), y);
        }
        
        // Draw separator line before sum row if showing sums
        if (showColumnSums) {
            g.setColor(Color.black);
            int separatorY = point1.y + 20 + cellSpacing + rows * (cellSize + cellSpacing);
            g.drawLine(point1.x, separatorY, point1.x + cellSpacing + cols * (cellSize + cellSpacing), separatorY);
        }
    }
    
    // Calculate computed values during simulation step (not during drawing)
    public void stepFinished() {
        super.stepFinished();
        
        if (showColumnSums) {
            // Calculate and register column sums
            for (int col = 0; col < cols; col++) {
                // Calculate sum for this column
                double columnSum = 0.0;
                for (int row = 0; row < rows; row++) {
                    String labelName = labelNames[row][col];
                    columnSum += getVoltageForLabel(labelName);
                }
                
                // Register this sum as a labeled node using column header as the label name
                String name = columnHeaders[col];
                registerComputedValueAsLabeledNode(name, columnSum);
            }
        }
    }
    
    private double[] lastColumnSums;

    public void doStep() {
        super.doStep();
        
        if (showColumnSums) {
            if (lastColumnSums == null) {
                lastColumnSums = new double[cols];
            }
            
            for (int col = 0; col < cols; col++) {
                double columnSum = 0.0;
                for (int row = 0; row < rows; row++) {
                    String labelName = labelNames[row][col];
                    columnSum += getVoltageForLabel(labelName);
                }
                
                // Check for convergence
                if (Math.abs(columnSum - lastColumnSums[col]) > 1e-6) {
                    sim.converged = false;
                }
                
                lastColumnSums[col] = columnSum;
                String name = columnHeaders[col];
                registerComputedValueAsLabeledNode(name, columnSum);
            }
        }
    }

    boolean nonLinear() { 
        return showColumnSums; // Make element nonlinear if computing sums
    }
    
    // No electrical connections - pure display element
    boolean isWireEquivalent() { return false; }
    boolean isRemovableWire() { return false; }
    
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.dump()).append(" ").append(rows).append(" ").append(cols);
        sb.append(" ").append(showColumnSums ? "1" : "0");
        
        // Serialize label names
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                sb.append(" ").append(CustomLogicModel.escape(labelNames[row][col]));
            }
        }
        
        // Serialize column headers
        for (int col = 0; col < cols; col++) {
            sb.append(" ").append(CustomLogicModel.escape(columnHeaders[col]));
        }
        
        return sb.toString();
    }

    protected void parseTableData(StringTokenizer st) {
        try {
            if (st.hasMoreTokens()) rows = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens()) cols = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens()) showColumnSums = "1".equals(st.nextToken());
            
            // Parse label names
            labelNames = new String[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    if (st.hasMoreTokens()) {
                        labelNames[row][col] = CustomLogicModel.unescape(st.nextToken());
                    } else {
                        labelNames[row][col] = "node" + (row * cols + col + 1);
                    }
                }
            }
            
            // Parse column headers
            columnHeaders = new String[cols];
            for (int col = 0; col < cols; col++) {
                if (st.hasMoreTokens()) {
                    columnHeaders[col] = CustomLogicModel.unescape(st.nextToken());
                } else {
                    columnHeaders[col] = "Col" + (col + 1);
                }
            }
        } catch (Exception e) {
            CirSim.console("TableElm: error parsing table data, using defaults");
            initTable(); // Reset to defaults
        }
    }

    int getDumpType() { 
        return 253; // Choose unused dump type
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) return new EditInfo("Cell Size", cellSize, 20, 100);
        if (n == 1) return new EditInfo("Cell Spacing", cellSpacing, 2, 20);
        if (n == 2) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Show Column Sums", showColumnSums);
            return ei;
        }
        if (n == 3) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.button = new Button("Edit Table Data...");
            return ei;
        }
        
        return null;
    }

    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            cellSize = (int)ei.value;
            setPoints();
        } else if (n == 1) {
            cellSpacing = (int)ei.value;
            setPoints();
        } else if (n == 2) {
            showColumnSums = ei.checkbox.getState();
            setPoints();
        } else if (n == 3) {
            // Open table editing dialog
            openTableEditDialog();
        }
    }

    // Resize table method for use by TableEditDialog
    public void resizeTable(int newRows, int newCols) {
        String[][] oldLabels = labelNames;
        String[] oldHeaders = columnHeaders;
        
        // Update dimensions
        rows = newRows;
        cols = newCols;
        
        // Create new arrays
        labelNames = new String[rows][cols];
        columnHeaders = new String[cols];
        
        // Copy over existing labels where possible
        if (oldLabels != null) {
            for (int row = 0; row < Math.min(rows, oldLabels.length); row++) {
                for (int col = 0; col < Math.min(cols, oldLabels[row].length); col++) {
                    labelNames[row][col] = oldLabels[row][col];
                }
            }
        }
        
        // Copy over existing headers where possible  
        if (oldHeaders != null) {
            for (int col = 0; col < Math.min(cols, oldHeaders.length); col++) {
                columnHeaders[col] = oldHeaders[col];
            }
        }
        
        // Fill in missing labels with simple defaults
        fillMissingLabels();
        
        setPoints();
    }
    
    private void fillMissingLabels() {
        // Fill missing cell labels with simple defaults
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (labelNames[row][col] == null || labelNames[row][col].isEmpty()) {
                    labelNames[row][col] = "node" + (row * cols + col + 1);
                }
            }
        }
        
        // Fill missing column headers with simple defaults
        for (int col = 0; col < cols; col++) {
            if (columnHeaders[col] == null || columnHeaders[col].isEmpty()) {
                columnHeaders[col] = "Col" + (col + 1);
            }
        }
    }
    
    protected void openTableEditDialog() {
        // Open the enhanced table editing dialog
        if (sim != null) {
            TableEditDialog dialog = new TableEditDialog(this, sim);
            dialog.show();
        }
    }
    
    void getInfo(String arr[]) {
        arr[0] = "Voltage Table (" + rows + "x" + cols + ")";
        arr[1] = "Displays voltages from labeled nodes";
        
        int idx = 2;
        // Show some sample values
        for (int row = 0; row < Math.min(3, rows) && idx < arr.length - 1; row++) {
            for (int col = 0; col < Math.min(2, cols) && idx < arr.length - 1; col++) {
                String label = labelNames[row][col];
                double voltage = getVoltageForLabel(label);
                arr[idx++] = label + ": " + getVoltageText(voltage);
            }
        }
        
        if (rows * cols > 6) {
            arr[idx++] = "... (showing first few cells)";
        }
    }
    
    public void setCellLabel(int row, int col, String label) {
        if (row >= 0 && row < rows && col >= 0 && col < cols) {
            labelNames[row][col] = label;
        }
    }

    public String getCellLabel(int row, int col) {
        if (row >= 0 && row < rows && col >= 0 && col < cols) {
            return labelNames[row][col];
        }
        return "";
    }

    public void setColumnHeader(int col, String header) {
        if (col >= 0 && col < cols) {
            columnHeaders[col] = header;
        }
    }

    public String getColumnHeader(int col) {
        if (col >= 0 && col < cols) {
            return columnHeaders[col];
        }
        return "";
    }
    
    // Getter methods for TableEditDialog
    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public boolean getShowColumnSums() { return showColumnSums; }
    
    public void setShowColumnSums(boolean show) {
        showColumnSums = show;
        setPoints();
    }

}
