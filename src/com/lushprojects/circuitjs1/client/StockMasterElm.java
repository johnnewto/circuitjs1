/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.List;

/**
 * StockMasterElm - Displays all master stocks from all tables in the circuit
 * 
 * This element shows:
 * - Stock name (from ComputedValues registry)
 * - Current value
 * - Which table is the master (owner) of that stock
 * 
 * It provides a compact overview of all stocks being tracked across the circuit.
 */
public class StockMasterElm extends ChipElm {
    private String title = "Master Stocks";
    private int cellHeight = 20; // Height of each row in pixels
    private int cellSpacing = 2;  // Spacing between cells
    private int cellWidthInGrids = 8; // Width in grid units
    
    // Cache for displayed data (updated periodically)
    private List<StockInfo> stockInfoList;
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 500; // Update twice per second
    
    // Fonts for rendering
    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 14);
    private static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 11);
    private static final Font CELL_FONT = new Font("SansSerif", 0, 10); // 0 = plain style
    
    /**
     * Helper class to store information about a stock
     */
    private static class StockInfo {
        String name;
        double value;
        String tableName;
        
        StockInfo(String name, double value, String tableName) {
            this.name = name;
            this.value = value;
            this.tableName = tableName;
        }
    }
    
    // Constructor for menu creation
    public StockMasterElm(int xx, int yy) {
        super(xx, yy);
        stockInfoList = new ArrayList<StockInfo>();
        x2 = xx + 200; // Initial width
        y2 = yy + 100; // Initial height
    }
    
    // Constructor for file loading
    public StockMasterElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        stockInfoList = new ArrayList<StockInfo>();
        x2 = xb;
        y2 = yb;
        
        // Parse title if saved (after super constructor processes ChipElm data)
        if (st.hasMoreTokens()) {
            title = CustomLogicModel.unescape(st.nextToken());
        }
    }
    
    @Override
    void drag(int xx, int yy) {
        // Allow free diagonal resizing like BoxElm
        x2 = xx;
        y2 = yy;
    }
    
    @Override
    public String dump() {
        return super.dump() + " " + CustomLogicModel.escape(title);
    }
    
    @Override
    public int getDumpType() {
        return 450; // Unique dump type for StockMasterElm
    }
    
    @Override
    public int getPostCount() {
        return 0; // No electrical connections
    }
    
    @Override
    public int getVoltageSourceCount() {
        return 0; // No voltage sources
    }
    
    @Override
    public void setupPins() {
        sizeX = cellWidthInGrids;
        sizeY = 4; // Initial size, will be adjusted
        allocNodes();
        pins = new Pin[0]; // No pins
    }
    
    /**
     * Update the cached stock information from ComputedValues registry
     */
    private void updateStockInfo() {
        stockInfoList.clear();
        
        // Get all computed value names from the registry
        String[] names = ComputedValues.getComputedValueNames();
        
        for (String name : names) {
            // Skip empty names
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            
            // Get the value
            Double valueObj = ComputedValues.getComputedValue(name);
            double value = (valueObj != null) ? valueObj : 0.0;
            
            // Get the master table
            Object masterTable = ComputedValues.getComputingTable(name);
            String tableName = "Unknown";
            
            if (masterTable != null) {
                if (masterTable instanceof TableElm) {
                    tableName = ((TableElm) masterTable).tableTitle;
                } else {
                    tableName = masterTable.getClass().getSimpleName();
                }
            }
            
            stockInfoList.add(new StockInfo(name, value, tableName));
        }
        
        // Sort by name for consistent display
        stockInfoList.sort((a, b) -> a.name.compareTo(b.name));
    }
    
    @Override
    public void draw(Graphics g) {
        // Update cache if enough time has passed
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS || stockInfoList.isEmpty()) {
            updateStockInfo();
            lastUpdateTime = currentTime;
        }
        
        // Calculate dimensions from bounding box (like BoxElm)
        int numRows = Math.max(1, stockInfoList.size());
        int titleHeight = 25;
        int headerHeight = cellHeight;
        
        // Use absolute difference to handle any drag direction
        int tableWidth = Math.abs(x2 - x);
        int tableHeight = Math.abs(y2 - y);
        
        // Minimum constraints
        if (tableWidth < 150) {
            tableWidth = 150;
        }
        
        int minHeight = titleHeight + headerHeight + (cellHeight + cellSpacing);
        if (tableHeight < minHeight) {
            tableHeight = minHeight;
        }
        
        // Calculate required height for all rows
        int requiredHeight = titleHeight + headerHeight + (numRows * (cellHeight + cellSpacing));
        if (tableHeight < requiredHeight) {
            tableHeight = requiredHeight;
        }
        
        // Get position (handle any corner as origin)
        int tableX = Math.min(x, x2);
        int tableY = Math.min(y, y2);
        
        // Update bounding box
        setBbox(tableX, tableY, tableX + tableWidth, tableY + tableHeight);
        
        // Draw background
        Color bgColor = CirSim.theSim.printableCheckItem.getState() ? 
            new Color(240, 240, 240) : new Color(30, 30, 30);
        g.setColor(bgColor);
        g.fillRect(tableX, tableY, tableWidth, tableHeight);
        
        // Draw border
        g.setColor(needsHighlight() ? CircuitElm.selectColor : CircuitElm.lightGrayColor);
        g.drawRect(tableX, tableY, tableWidth, tableHeight);
        
        int currentY = tableY + 5;
        
        // Draw title
        g.setFont(TITLE_FONT);
        g.setColor(CircuitElm.whiteColor);
        drawCenteredText(g, title, tableX + tableWidth / 2, currentY + 10, true);
        currentY += titleHeight;
        
        // Draw header row
        g.setFont(HEADER_FONT);
        g.setColor(CircuitElm.whiteColor);
        
        int col1Width = (int)(tableWidth * 0.35); // Stock name
        int col2Width = (int)(tableWidth * 0.25); // Value
        int col3Width = tableWidth - col1Width - col2Width; // Table name
        
        drawLeftText(g, "Stock", tableX + 5, currentY + cellHeight / 2);
        drawCenteredText(g, "Value", tableX + col1Width + col2Width / 2, currentY + cellHeight / 2, true);
        drawLeftText(g, "Master Table", tableX + col1Width + col2Width + 5, currentY + cellHeight / 2);
        
        // Draw header separator line
        g.setColor(CircuitElm.lightGrayColor);
        g.drawLine(tableX, currentY + cellHeight, tableX + tableWidth, currentY + cellHeight);
        currentY += headerHeight + cellSpacing;
        
        // Draw stock rows
        g.setFont(CELL_FONT);
        for (StockInfo info : stockInfoList) {
            // Get voltage color for the value
            Color valueColor = getTextVoltageColor(info.value);
            
            // Draw stock name
            g.setColor(CircuitElm.whiteColor);
            String displayName = truncateText(info.name, g, col1Width - 10);
            drawLeftText(g, displayName, tableX + 5, currentY + cellHeight / 2);
            
            // Draw value with voltage coloring
            g.setColor(valueColor);
            String valueText = CircuitElm.getUnitText(info.value, "V");
            drawCenteredText(g, valueText, tableX + col1Width + col2Width / 2, currentY + cellHeight / 2, true);
            
            // Draw table name
            g.setColor(CircuitElm.whiteColor);
            String displayTable = truncateText(info.tableName, g, col3Width - 10);
            drawLeftText(g, displayTable, tableX + col1Width + col2Width + 5, currentY + cellHeight / 2);
            
            // Draw separator line
            g.setColor(new Color(100, 100, 100));
            g.drawLine(tableX, currentY + cellHeight, tableX + tableWidth, currentY + cellHeight);
            
            currentY += cellHeight + cellSpacing;
        }
        
        // Draw posts (none for this element, but required by framework)
        drawPosts(g);
    }
    
    /**
     * Get voltage color for text display
     */
    private Color getTextVoltageColor(double volts) {
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
     * Draw left-aligned text
     */
    private void drawLeftText(Graphics g, String text, int x, int y) {
        if (text == null || text.isEmpty()) return;
        g.save();
        g.context.setTextBaseline("middle");
        g.context.setTextAlign("left");
        g.drawString(text, x, y);
        g.restore();
    }
    
    /**
     * Truncate text to fit within specified width
     */
    private String truncateText(String text, Graphics g, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        double fullWidth = g.context.measureText(text).getWidth();
        
        if (fullWidth <= maxWidth) {
            return text;
        }
        
        // Binary search for optimal length
        int left = 1;
        int right = text.length();
        String bestFit = text.substring(0, Math.min(3, text.length())) + "..";
        
        while (left <= right) {
            int mid = (left + right) / 2;
            String candidate = text.substring(0, mid) + "..";
            double candidateWidth = g.context.measureText(candidate).getWidth();
            
            if (candidateWidth <= maxWidth) {
                bestFit = candidate;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        
        return bestFit;
    }
    
    @Override
    public void getInfo(String arr[]) {
        arr[0] = "Master Stocks Table";
        arr[1] = "Showing " + stockInfoList.size() + " master stock(s)";
    }
    
    @Override
    public void setPoints() {
        super.setPoints();
        // Recalculate size based on number of stocks
        int numRows = Math.max(1, stockInfoList.size());
        sizeY = (numRows + 2); // +2 for title and header
        allocNodes();
    }
    
    @Override
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Title", 0, -1, -1);
            ei.text = title;
            return ei;
        }
        return null;
    }
    
    @Override
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            title = ei.textf.getText();
        }
    }
}
