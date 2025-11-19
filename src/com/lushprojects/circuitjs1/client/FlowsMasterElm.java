/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * FlowsMasterElm - Displays all unique flows from all tables in the circuit
 * 
 * This element shows:
 * - Flow name (row description from tables)
 * - Which tables use this flow
 * - Count of how many tables reference this flow
 * 
 * It provides a compact overview of all flows being used across the circuit.
 */
public class FlowsMasterElm extends ChipElm {
    private String title = "All Flows";
    private int cellHeight = 20; // Height of each row in pixels
    private int cellSpacing = 2;  // Spacing between cells
    private int cellWidthInGrids = 10; // Width in grid units (wider for table names)
    
    // Cache for displayed data (updated periodically)
    private List<FlowInfo> flowInfoList;
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 500; // Update twice per second
    
    // Fonts for rendering
    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 14);
    private static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 11);
    private static final Font CELL_FONT = new Font("SansSerif", 0, 10); // 0 = plain style
    
    /**
     * Helper class to store information about a flow
     */
    private static class FlowInfo {
        String name;
        List<String> tableNames;
        int count;
        
        FlowInfo(String name, List<String> tableNames) {
            this.name = name;
            this.tableNames = tableNames;
            this.count = tableNames.size();
        }
    }
    
    // Constructor for menu creation
    public FlowsMasterElm(int xx, int yy) {
        super(xx, yy);
        flowInfoList = new ArrayList<FlowInfo>();
        x2 = xx + 200; // Initial width
        y2 = yy + 100; // Initial height
    }
    
    // Constructor for file loading
    public FlowsMasterElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        flowInfoList = new ArrayList<FlowInfo>();
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
        return 451; // Unique dump type for FlowsMasterElm
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
     * Update the cached flow information by scanning all tables in the circuit
     */
    private void updateFlowInfo() {
        flowInfoList.clear();
        
        // Map flow name -> list of table names that use it
        HashMap<String, List<String>> flowToTables = new HashMap<String, List<String>>();
        
        // Scan all circuit elements for TableElm instances
        if (sim.elmList != null) {
            for (int i = 0; i < sim.elmList.size(); i++) {
                CircuitElm ce = sim.getElm(i);
                
                if (ce instanceof TableElm) {
                    TableElm table = (TableElm) ce;
                    String tableName = table.tableTitle;
                    
                    // Get all row descriptions (flows) from this table
                    if (table.rowDescriptions != null) {
                        for (int row = 0; row < table.rows && row < table.rowDescriptions.length; row++) {
                            String flowName = table.rowDescriptions[row];
                            
                            // Skip null or empty flow names
                            if (flowName == null || flowName.trim().isEmpty()) {
                                continue;
                            }
                            
                            // Add to map
                            if (!flowToTables.containsKey(flowName)) {
                                flowToTables.put(flowName, new ArrayList<String>());
                            }
                            
                            // Only add table name if not already in list (avoid duplicates)
                            List<String> tables = flowToTables.get(flowName);
                            if (!tables.contains(tableName)) {
                                tables.add(tableName);
                            }
                        }
                    }
                }
            }
        }
        
        // Convert map to list of FlowInfo objects
        for (String flowName : flowToTables.keySet()) {
            List<String> tables = flowToTables.get(flowName);
            flowInfoList.add(new FlowInfo(flowName, tables));
        }
        
        // Sort by flow name for consistent display
        flowInfoList.sort((a, b) -> a.name.compareTo(b.name));
    }
    
    @Override
    public void draw(Graphics g) {
        // Update cache if enough time has passed
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS || flowInfoList.isEmpty()) {
            updateFlowInfo();
            lastUpdateTime = currentTime;
        }
        
        // Calculate dimensions from bounding box (like BoxElm)
        int numRows = Math.max(1, flowInfoList.size());
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
        
        int col1Width = (int)(tableWidth * 0.35); // Flow name
        int col2Width = (int)(tableWidth * 0.15); // Count
        int col3Width = tableWidth - col1Width - col2Width; // Table names
        
        drawLeftText(g, "Flow", tableX + 5, currentY + cellHeight / 2);
        drawCenteredText(g, "Count", tableX + col1Width + col2Width / 2, currentY + cellHeight / 2, true);
        drawLeftText(g, "Tables", tableX + col1Width + col2Width + 5, currentY + cellHeight / 2);
        
        // Draw header separator line
        g.setColor(CircuitElm.lightGrayColor);
        g.drawLine(tableX, currentY + cellHeight, tableX + tableWidth, currentY + cellHeight);
        currentY += headerHeight + cellSpacing;
        
        // Draw flow rows
        g.setFont(CELL_FONT);
        for (FlowInfo info : flowInfoList) {
            // Draw flow name
            g.setColor(CircuitElm.whiteColor);
            String displayName = truncateText(info.name, g, col1Width - 10);
            drawLeftText(g, displayName, tableX + 5, currentY + cellHeight / 2);
            
            // Draw count (highlight if shared across multiple tables)
            if (info.count > 1) {
                g.setColor(new Color(100, 200, 255)); // Light blue for shared flows
            } else {
                g.setColor(CircuitElm.whiteColor);
            }
            drawCenteredText(g, String.valueOf(info.count), 
                           tableX + col1Width + col2Width / 2, currentY + cellHeight / 2, true);
            
            // Draw table names (comma-separated)
            g.setColor(CircuitElm.whiteColor);
            String tableNamesStr = joinTableNames(info.tableNames);
            String displayTables = truncateText(tableNamesStr, g, col3Width - 10);
            drawLeftText(g, displayTables, tableX + col1Width + col2Width + 5, currentY + cellHeight / 2);
            
            // Draw separator line
            g.setColor(new Color(100, 100, 100));
            g.drawLine(tableX, currentY + cellHeight, tableX + tableWidth, currentY + cellHeight);
            
            currentY += cellHeight + cellSpacing;
        }
        
        // Draw posts (none for this element, but required by framework)
        drawPosts(g);
    }
    
    /**
     * Join table names with commas
     */
    private String joinTableNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(names.get(i));
        }
        return sb.toString();
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
        arr[0] = "Flows Table";
        arr[1] = "Showing " + flowInfoList.size() + " unique flow(s)";
        
        // Count shared flows (used by multiple tables)
        int sharedCount = 0;
        for (FlowInfo info : flowInfoList) {
            if (info.count > 1) {
                sharedCount++;
            }
        }
        if (sharedCount > 0) {
            arr[2] = sharedCount + " flow(s) shared across tables";
        }
    }
    
    @Override
    public void setPoints() {
        super.setPoints();
        // Recalculate size based on number of flows
        int numRows = Math.max(1, flowInfoList.size());
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
