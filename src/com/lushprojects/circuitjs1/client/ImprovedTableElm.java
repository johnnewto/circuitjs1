/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Map;
// import java.util.StringTokenizer;



/**
 * Improved Table Element using Observer pattern for computed values
 * This version doesn't interfere with the main simulation system
 */
public class ImprovedTableElm extends ComputedValueProvider implements ComputedValueObserver {
    protected int rows = 3;
    protected int cols = 2; 
    protected int cellSize = 60;
    protected int cellSpacing = 4;
    protected String[][] labelNames;
    protected String[] columnHeaders;
    protected boolean showColumnSums = true;
    
    // Cache for observed values from other elements
    private Map<String, Double> observedValues = new HashMap<>();
    
    public ImprovedTableElm(int xx, int yy) {
        super(xx, yy);
        initTable();
    }
    
    public ImprovedTableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        parseTableData(st);
        initTable();
    }
    
    private void initTable() {
        if (labelNames == null) {
            labelNames = new String[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    labelNames[row][col] = "node" + (row * cols + col + 1);
                }
            }
        }
        
        if (columnHeaders == null) {
            columnHeaders = new String[cols];
            for (int i = 0; i < cols; i++) {
                columnHeaders[i] = "Col" + (i + 1);
            }
        }
        
        // Ensure no null values
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
        
        registerWithProviders();
    }
    
    /**
     * Register this element to observe computed values from other elements
     */
    private void registerWithProviders() {
        // This would be called when the circuit is built
        // For now, we'll implement the registration logic in a setup phase
    }
    
    /**
     * Method to be called by circuit setup to register with other computed value providers
     */
    public void registerWithOtherElements() {
        if (sim == null || sim.elmList == null) return;
        
        // Find other ComputedValueProvider elements and register as observer
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.getElm(i);
            if (elm instanceof ComputedValueProvider && elm != this) {
                ComputedValueProvider provider = (ComputedValueProvider) elm;
                
                // Register for all computed values this provider offers
                String[] keys = provider.getComputedValueKeys();
                for (String key : keys) {
                    provider.addComputedValueObserver(key, this);
                }
            }
        }
    }
    
    @Override
    public void onComputedValueChanged(CircuitElm source, String key, double value) {
        // Store the observed value for use in our calculations
        String sourceKey = source.getClass().getSimpleName() + "_" + key;
        observedValues.put(sourceKey, value);
    }
    
    @Override
    public CircuitElm getObserverElement() {
        return this;
    }
    
    protected double getVoltageForLabel(String labelName) {
        if (labelName == null || labelName.isEmpty()) {
            return 0.0;
        }
        
        // First check if this is an observed computed value
        Double observedValue = observedValues.get(labelName);
        if (observedValue != null) {
            return observedValue.doubleValue();
        }
        
        // Check our own computed values
        Double computedValue = getComputedValue(labelName);
        if (computedValue != null) {
            return computedValue.doubleValue();
        }
        
        // Use the official CirSim method for actual circuit nodes
        if (sim != null) {
            return sim.getLabeledNodeVoltage(labelName);
        }
        
        return 0.0;
    }
    
    void setPoints() {
        super.setPoints();
        
        int tableWidth = cols * cellSize + (cols + 1) * cellSpacing;
        int extraRows = showColumnSums ? 1 : 0;
        int tableHeight = (rows + extraRows) * cellSize + (rows + extraRows + 1) * cellSpacing + 20;
        
        setBbox(point1.x, point1.y, point1.x + tableWidth, point1.y + tableHeight);
    }

    int getPostCount() { 
        return 0;
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
        
        drawColumnHeaders(g);
        drawTableCells(g);
        
        if (showColumnSums) {
            drawSumRow(g);
        }
        
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
                
                String labelName = labelNames[row][col];
                double voltage = getVoltageForLabel(labelName);
                
                setVoltageColor(g, voltage);
                g.fillRect(cellX, cellY, cellSize, cellSize);
                
                g.setColor(Color.black);
                g.drawRect(cellX, cellY, cellSize, cellSize);
                
                drawCenteredText(g, labelName, cellX + cellSize/2, cellY + cellSize/3, true);
                
                String voltageText = getVoltageText(voltage);
                drawCenteredText(g, voltageText, cellX + cellSize/2, cellY + 2*cellSize/3, true);
            }
        }
    }

    private void drawSumRow(Graphics g) {
        int sumRowY = point1.y + 20 + cellSpacing + rows * (cellSize + cellSpacing);
        
        for (int col = 0; col < cols; col++) {
            int cellX = point1.x + cellSpacing + col * (cellSize + cellSpacing);
            
            String sumLabelName = columnHeaders[col];
            Double computedSum = getComputedValue(sumLabelName);
            double columnSum = (computedSum != null) ? computedSum.doubleValue() : 0.0;
            
            g.setColor(Color.lightGray);
            g.fillRect(cellX, sumRowY, cellSize, cellSize);
            
            g.setColor(Color.black);
            g.drawRect(cellX, sumRowY, cellSize, cellSize);
            
            drawCenteredText(g, sumLabelName, cellX + cellSize/2, sumRowY + cellSize/3, true);
            
            String sumText = getVoltageText(columnSum);
            drawCenteredText(g, sumText, cellX + cellSize/2, sumRowY + 2*cellSize/3, true);
        }
    }

    private void drawGridLines(Graphics g) {
        g.setColor(Color.gray);
        int extraRows = showColumnSums ? 1 : 0;
        
        for (int col = 0; col <= cols; col++) {
            int x = point1.x + cellSpacing + col * (cellSize + cellSpacing);
            g.drawLine(x, point1.y + 20, x, point1.y + 20 + cellSpacing + (rows + extraRows) * (cellSize + cellSpacing));
        }
        
        for (int row = 0; row <= rows + extraRows; row++) {
            int y = point1.y + 20 + cellSpacing + row * (cellSize + cellSpacing);
            g.drawLine(point1.x, y, point1.x + cellSpacing + cols * (cellSize + cellSpacing), y);
        }
        
        if (showColumnSums) {
            g.setColor(Color.black);
            int separatorY = point1.y + 20 + cellSpacing + rows * (cellSize + cellSpacing);
            g.drawLine(point1.x, separatorY, point1.x + cellSpacing + cols * (cellSize + cellSpacing), separatorY);
        }
    }
    
    @Override
    public void stepFinished() {
        super.stepFinished();
        
        if (showColumnSums) {
            calculateColumnSums();
        }
    }
    
    private void calculateColumnSums() {
        for (int col = 0; col < cols; col++) {
            double columnSum = 0.0;
            for (int row = 0; row < rows; row++) {
                String labelName = labelNames[row][col];
                columnSum += getVoltageForLabel(labelName);
            }
            
            // Use our computed value system instead of global LabeledNodeElm
            String sumKey = columnHeaders[col];
            setComputedValue(sumKey, columnSum);
        }
    }

    boolean nonLinear() { 
        return showColumnSums;
    }
    
    boolean isWireEquivalent() { return false; }
    boolean isRemovableWire() { return false; }
    
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.dump()).append(" ").append(rows).append(" ").append(cols);
        sb.append(" ").append(showColumnSums ? "1" : "0");
        
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                sb.append(" ").append(CustomLogicModel.escape(labelNames[row][col]));
            }
        }
        
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
            
            columnHeaders = new String[cols];
            for (int col = 0; col < cols; col++) {
                if (st.hasMoreTokens()) {
                    columnHeaders[col] = CustomLogicModel.unescape(st.nextToken());
                } else {
                    columnHeaders[col] = "Col" + (col + 1);
                }
            }
        } catch (Exception e) {
            CirSim.console("ImprovedTableElm: error parsing table data, using defaults");
            initTable();
        }
    }

    int getDumpType() { 
        return 254; // Different dump type for improved version
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) return new EditInfo("Cell Size", cellSize, 20, 100);
        if (n == 1) return new EditInfo("Cell Spacing", cellSpacing, 2, 20);
        if (n == 2) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Show Column Sums", showColumnSums);
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
        }
    }
    
    void getInfo(String arr[]) {
        arr[0] = "Improved Voltage Table (" + rows + "x" + cols + ")";
        arr[1] = "Uses observer pattern for computed values";
        
        int idx = 2;
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
}