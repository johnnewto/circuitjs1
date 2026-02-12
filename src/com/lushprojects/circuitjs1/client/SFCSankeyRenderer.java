/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.TableColumn.ColumnType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * SFCSankeyRenderer - Canvas-based Sankey diagram renderer for SFC tables
 * 
 * Draws a Sankey diagram directly on the circuit canvas showing money flows
 * between sectors. Uses sfcr convention:
 * - Negative cell values = outflows (sector is source)
 * - Positive cell values = inflows (sector is target)
 * 
 * Supports two layout modes:
 * 
 * LINEAR (default): 3 columns
 * - Left: Source sectors (those with outflows)
 * - Middle: Transaction types
 * - Right: Target sectors (those with inflows)
 * 
 * CIRCULAR: 2 columns with circular/feedback links
 * - Left: All sectors (single instance each)
 * - Right: All transactions
 * - Forward links: Sector → Transaction (outflows)
 * - Circular links: Transaction → Sector (inflows, routed around top/bottom)
 */
public class SFCSankeyRenderer {
    
    /**
     * Layout mode for the Sankey diagram
     */
    public enum SankeyLayout {
        LINEAR,     // 3-column layout (duplicates sectors)
        CIRCULAR    // 2-column layout with circular links
    }
    
    private SFCTableElm table;
    private SankeyLayout layoutMode = SankeyLayout.LINEAR;
    
    // Layout constants
    private static final int PADDING = 10;
    private static final int NODE_WIDTH = 20;
    private static final int NODE_PADDING = 8;  // Vertical gap between nodes
    private static final int COLUMN_GAP = 60;   // Horizontal gap between columns
    private static final int CIRCULAR_GAP = 100; // Wider gap for circular mode
    private static final int MIN_BAND_HEIGHT = 2;
    private static final int CIRCULAR_LINK_MARGIN = 15; // Space for circular links at top/bottom
    
    // Modern sector colors (inspired by Tailwind CSS and modern dashboards)
    private static final Map<String, String> SECTOR_COLORS = new HashMap<>();
    static {
        // Primary sectors - vibrant, accessible colors
        SECTOR_COLORS.put("Households", "#6366F1");      // Indigo-500
        SECTOR_COLORS.put("Households_C", "#818CF8");    // Indigo-400
        SECTOR_COLORS.put("Firms", "#10B981");           // Emerald-500
        SECTOR_COLORS.put("Firms_{Current}", "#34D399"); // Emerald-400
        SECTOR_COLORS.put("Firms_{Capital}", "#059669"); // Emerald-600
        SECTOR_COLORS.put("Banks", "#F59E0B");           // Amber-500
        SECTOR_COLORS.put("Banks_{Current}", "#FBBF24"); // Amber-400
        SECTOR_COLORS.put("Banks_{Capital}", "#D97706"); // Amber-600
        SECTOR_COLORS.put("Govt", "#EF4444");            // Red-500
        SECTOR_COLORS.put("Government", "#EF4444");      // Red-500
        SECTOR_COLORS.put("Central Bank", "#8B5CF6");    // Violet-500
        SECTOR_COLORS.put("Foreign", "#06B6D4");         // Cyan-500
        SECTOR_COLORS.put("External", "#14B8A6");        // Teal-500
        SECTOR_COLORS.put("Rest of World", "#0EA5E9");   // Sky-500
    }
    private static final String DEFAULT_SECTOR_COLOR = "#8B5CF6";  // Violet-500
    private static final String TRANSACTION_COLOR = "#64748B";     // Slate-500 (softer than gray)
    
    // Cached layout data
    private ArrayList<SankeyNode> leftNodes;    // Source sectors
    private ArrayList<SankeyNode> middleNodes;  // Transactions
    private ArrayList<SankeyNode> rightNodes;   // Target sectors
    private ArrayList<SankeyLink> links;
    
    // All node lists for convenient iteration
    private ArrayList<SankeyNode>[] allNodeLists;
    
    // Drawing area
    private int drawX, drawY, drawWidth, drawHeight;
    
    // Dynamic layout values (calculated based on available size)
    private int nodeWidth;
    private int nodePadding;
    private int columnGap;
    private int circularMargin;  // Space above/below for circular link routing
    
    // Hover state for highlighting
    private SankeyNode hoveredNode = null;
    private SankeyLink hoveredLink = null;
    private int lastMouseX = -1, lastMouseY = -1;
    
    // Throttling for slower diagram redraw (but responsive tooltips)
    private long lastFullDrawTime = 0;
    private static final long DRAW_INTERVAL_MS = 200;  // Redraw every 200ms
    private boolean needsFullRedraw = true;
    private int cachedX, cachedY, cachedWidth, cachedHeight;
    
    // Scale visualization options
    private boolean showScaleBar = true;       // Show scale bar on RHS
    private double fixedMaxScale = 0;           // Fixed scale (0 = auto)
    private boolean useHighWaterMark = false;   // Use historical peak
    private boolean showFlowValues = false;     // Show numeric values on links
    private double highWaterMark = 0;           // Tracked maximum flow ever seen
    private double currentMaxFlow = 0;          // Current maximum flow for scale bar
    
    /**
     * Represents a node in the Sankey diagram
     */
    private static class SankeyNode {
        String name;
        String color;
        double totalFlow;  // Max of inFlow/outFlow for node sizing
        double inFlow;     // Sum of flows entering this node (as target)
        double outFlow;    // Sum of flows leaving this node (as source)
        int x, y, width, height;
        int flowOffsetIn;  // Current offset for stacking links on input (left) side
        int flowOffsetOut; // Current offset for stacking links on output (right) side
        
        SankeyNode(String name, String color) {
            this.name = name;
            this.color = color;
            this.totalFlow = 0;
            this.inFlow = 0;
            this.outFlow = 0;
            this.flowOffsetIn = 0;
            this.flowOffsetOut = 0;
        }
    }
    
    /**
     * Represents a flow link between nodes
     */
    private static class SankeyLink {
        SankeyNode source;
        SankeyNode target;
        double value;
        String color;
        String label;
        boolean isCircular;  // True if this link goes "backwards" (right to left)
        boolean routeTop;    // True to route circular link via top, false for bottom
        
        // Calculated positions
        int sourceY, targetY;
        int sourceBandwidth, targetBandwidth;  // Bandwidth at source and target ends (may differ)
        int bandwidth;  // Kept for compatibility (average or source)
    }
    
    public SFCSankeyRenderer(SFCTableElm table) {
        this.table = table;
    }
    
    /**
     * Get the source table
     */
    public SFCTableElm getTable() {
        return table;
    }
    
    /**
     * Get the current layout mode
     */
    public SankeyLayout getLayoutMode() {
        return layoutMode;
    }
    
    /**
     * Set the layout mode
     */
    public void setLayoutMode(SankeyLayout mode) {
        this.layoutMode = mode;
    }
    
    /**
     * Set whether to show the scale bar on the right side
     */
    public void setShowScaleBar(boolean show) {
        this.showScaleBar = show;
    }
    
    /**
     * Set fixed maximum scale (0 = auto-scale)
     */
    public void setFixedMaxScale(double maxScale) {
        this.fixedMaxScale = maxScale;
    }
    
    /**
     * Set whether to use high-water mark for scaling
     */
    public void setUseHighWaterMark(boolean use) {
        this.useHighWaterMark = use;
        if (!use) {
            highWaterMark = 0;  // Reset when disabled
        }
    }
    
    /**
     * Set whether to show numeric values on flow links
     */
    public void setShowFlowValues(boolean show) {
        this.showFlowValues = show;
    }
    
    /**
     * Reset the high-water mark (useful when restarting simulation)
     */
    public void resetHighWaterMark() {
        highWaterMark = 0;
    }
    
    /**
     * Get hover information for a given mouse position.
     * Returns an array with info strings, or null if not over any element.
     */
    public String[] getHoverInfo(int mouseX, int mouseY) {
        if (leftNodes == null || links == null) {
            return null;
        }
        
        // Check nodes first (they are on top visually)
        for (SankeyNode node : leftNodes) {
            if (isPointInNode(mouseX, mouseY, node)) {
                return getNodeInfo(node);
            }
        }
        for (SankeyNode node : middleNodes) {
            if (isPointInNode(mouseX, mouseY, node)) {
                return getNodeInfo(node);
            }
        }
        for (SankeyNode node : rightNodes) {
            if (isPointInNode(mouseX, mouseY, node)) {
                return getNodeInfo(node);
            }
        }
        
        // Check links (approximate - use the source/target Y range)
        for (SankeyLink link : links) {
            if (isPointNearLink(mouseX, mouseY, link)) {
                return getLinkInfo(link);
            }
        }
        
        return null;
    }
    
    private boolean isPointInNode(int x, int y, SankeyNode node) {
        return x >= node.x && x <= node.x + node.width &&
               y >= node.y && y <= node.y + node.height;
    }
    
    private boolean isPointNearLink(int x, int y, SankeyLink link) {
        int x1 = link.source.x + link.source.width;
        int x2 = link.target.x;
        int halfBand = link.bandwidth / 2;
        
        // Check if x is between source and target
        if (x < x1 || x > x2) {
            // For circular links, also check the routing areas
            if (!link.isCircular) return false;
        }
        
        // For non-circular links, check if y is within the band at the midpoint
        if (!link.isCircular) {
            // Linear interpolate Y at mouse X position
            double t = (x - x1) / (double)(x2 - x1);
            if (t < 0 || t > 1) return false;
            double midY = link.sourceY + t * (link.targetY - link.sourceY);
            return Math.abs(y - midY) <= halfBand + 5;
        }
        
        // For circular links, simplified check
        return false;  // Skip for now - complex curved paths
    }
    
    private String[] getNodeInfo(SankeyNode node) {
        String[] info = new String[4];
        info[0] = node.name;
        info[1] = "In: " + CircuitElm.getUnitText(node.inFlow, "$");
        info[2] = "Out: " + CircuitElm.getUnitText(node.outFlow, "$");
        info[3] = "Total: " + CircuitElm.getUnitText(node.totalFlow, "$");
        return info;
    }
    
    private String[] getLinkInfo(SankeyLink link) {
        String[] info = new String[3];
        info[0] = link.source.name + " → " + link.target.name;
        info[1] = "Flow: " + CircuitElm.getUnitText(link.value, "$");
        info[2] = link.isCircular ? "(Feedback)" : "";
        return info;
    }
    
    /**
     * Calculate the required size for the Sankey diagram
     */
    public int[] getRequiredSize() {
        buildLayout();
        
        int numSectors = 0;
        int numTransactions = table.rows;
        
        if (table.columns != null) {
            for (TableColumn col : table.columns) {
                if (col.getType() == ColumnType.SECTOR) {
                    numSectors++;
                }
            }
        }
        
        // Calculate dimensions
        int width = PADDING * 2 + NODE_WIDTH * 3 + COLUMN_GAP * 2 + 40;  // 3 columns + gaps + labels
        int maxNodes = Math.max(numSectors, numTransactions);
        int height = PADDING * 2 + maxNodes * 30 + 40;  // Approximate height per node
        
        return new int[] { width, height };
    }
    
    /**
     * Build the layout data from the table
     */
    private void buildLayout() {
        if (layoutMode == SankeyLayout.CIRCULAR) {
            buildCircularLayout();
        } else {
            buildLinearLayout();
        }
    }
    
    /**
     * Build the linear 3-column layout (original behavior)
     */
    @SuppressWarnings("unchecked")
    private void buildLinearLayout() {
        leftNodes = new ArrayList<>();
        middleNodes = new ArrayList<>();
        rightNodes = new ArrayList<>();
        links = new ArrayList<>();
        allNodeLists = new ArrayList[] { leftNodes, middleNodes, rightNodes };
        
        if (table.columns == null || table.rows == 0) {
            return;
        }
        
        // Collect sector names
        ArrayList<String> sectorNames = new ArrayList<>();
        for (TableColumn col : table.columns) {
            if (col.getType() == ColumnType.SECTOR) {
                sectorNames.add(col.getStockName());
            }
        }
        
        // Create nodes for left (source) sectors
        Map<String, SankeyNode> leftNodeMap = new HashMap<>();
        for (String name : sectorNames) {
            SankeyNode node = new SankeyNode(name, getSectorColor(name));
            leftNodes.add(node);
            leftNodeMap.put(name, node);
        }
        
        // Create nodes for transactions (middle)
        Map<String, SankeyNode> middleNodeMap = new HashMap<>();
        for (int row = 0; row < table.rows; row++) {
            String name = table.rowDescriptions[row];
            SankeyNode node = new SankeyNode(name, TRANSACTION_COLOR);
            middleNodes.add(node);
            middleNodeMap.put(name + "_" + row, node);
        }
        
        // Create nodes for right (target) sectors
        Map<String, SankeyNode> rightNodeMap = new HashMap<>();
        for (String name : sectorNames) {
            SankeyNode node = new SankeyNode(name, getSectorColor(name));
            rightNodes.add(node);
            rightNodeMap.put(name, node);
        }
        
        // Create links from cell values
        for (int row = 0; row < table.rows; row++) {
            String transactionName = table.rowDescriptions[row];
            SankeyNode transactionNode = middleNodeMap.get(transactionName + "_" + row);
            
            for (int col = 0; col < table.columns.size(); col++) {
                TableColumn column = table.columns.get(col);
                if (column.getType() != ColumnType.SECTOR) {
                    continue;
                }
                
                double value = table.getVoltageForCell(row, col);
                if (Math.abs(value) < 1e-10) {
                    continue;
                }
                
                String sectorName = column.getStockName();
                
                if (value < 0) {
                    // Outflow: left sector -> transaction
                    SankeyNode sourceNode = leftNodeMap.get(sectorName);
                    if (sourceNode != null && transactionNode != null) {
                        SankeyLink link = new SankeyLink();
                        link.source = sourceNode;
                        link.target = transactionNode;
                        link.value = Math.abs(value);
                        link.color = getSectorColorAlpha(sectorName, 0.7);
                        link.label = sectorName + " → " + transactionName;
                        link.isCircular = false;
                        links.add(link);
                        
                        sourceNode.outFlow += Math.abs(value);
                        transactionNode.inFlow += Math.abs(value);
                    }
                } else {
                    // Inflow: transaction -> right sector
                    SankeyNode targetNode = rightNodeMap.get(sectorName);
                    if (transactionNode != null && targetNode != null) {
                        SankeyLink link = new SankeyLink();
                        link.source = transactionNode;
                        link.target = targetNode;
                        link.value = value;
                        link.color = getSectorColorAlpha(sectorName, 0.7);
                        link.label = transactionName + " → " + sectorName;
                        link.isCircular = false;
                        links.add(link);
                        
                        transactionNode.outFlow += value;
                        targetNode.inFlow += value;
                    }
                }
            }
        }
        
        // Set totalFlow = max(inFlow, outFlow) for proper node sizing
        updateNodeTotalFlows();
    }
    
    /**
     * Build the circular 2-column layout with feedback links.
     * - Left column: Sectors (single instance each)
     * - Right column: Transactions
     * - Outflows (negative values): Sector → Transaction (forward links)
     * - Inflows (positive values): Transaction → Sector (circular links, routed around edges)
     */
    @SuppressWarnings("unchecked")
    private void buildCircularLayout() {
        leftNodes = new ArrayList<>();   // Sectors
        middleNodes = new ArrayList<>(); // Not used in circular mode
        rightNodes = new ArrayList<>();  // Transactions
        links = new ArrayList<>();
        allNodeLists = new ArrayList[] { leftNodes, middleNodes, rightNodes };
        
        if (table.columns == null || table.rows == 0) {
            return;
        }
        
        // Collect sector names and create sector nodes (left column)
        Map<String, SankeyNode> sectorNodeMap = new HashMap<>();
        for (TableColumn col : table.columns) {
            if (col.getType() == ColumnType.SECTOR) {
                String name = col.getStockName();
                SankeyNode node = new SankeyNode(name, getSectorColor(name));
                leftNodes.add(node);
                sectorNodeMap.put(name, node);
            }
        }
        
        // Create transaction nodes (right column)
        Map<String, SankeyNode> transactionNodeMap = new HashMap<>();
        for (int row = 0; row < table.rows; row++) {
            String name = table.rowDescriptions[row];
            SankeyNode node = new SankeyNode(name, TRANSACTION_COLOR);
            rightNodes.add(node);
            transactionNodeMap.put(name + "_" + row, node);
        }
        
        // Track circular links for routing assignment
        ArrayList<SankeyLink> circularLinks = new ArrayList<>();
        
        // Create links from cell values
        for (int row = 0; row < table.rows; row++) {
            String transactionName = table.rowDescriptions[row];
            SankeyNode transactionNode = transactionNodeMap.get(transactionName + "_" + row);
            
            for (int col = 0; col < table.columns.size(); col++) {
                TableColumn column = table.columns.get(col);
                if (column.getType() != ColumnType.SECTOR) {
                    continue;
                }
                
                double value = table.getVoltageForCell(row, col);
                if (Math.abs(value) < 1e-10) {
                    continue;
                }
                
                String sectorName = column.getStockName();
                SankeyNode sectorNode = sectorNodeMap.get(sectorName);
                
                if (sectorNode == null || transactionNode == null) {
                    continue;
                }
                
                SankeyLink link = new SankeyLink();
                link.value = Math.abs(value);
                link.color = getSectorColorAlpha(sectorName, 0.7);
                
                if (value < 0) {
                    // Outflow: Sector → Transaction (forward link, left to right)
                    link.source = sectorNode;
                    link.target = transactionNode;
                    link.label = sectorName + " → " + transactionName;
                    link.isCircular = false;
                    
                    sectorNode.outFlow += Math.abs(value);
                    transactionNode.inFlow += Math.abs(value);
                } else {
                    // Inflow: Transaction → Sector (circular link, right to left)
                    link.source = transactionNode;
                    link.target = sectorNode;
                    link.label = transactionName + " → " + sectorName;
                    link.isCircular = true;
                    
                    transactionNode.outFlow += value;
                    sectorNode.inFlow += value;
                    circularLinks.add(link);
                }
                
                links.add(link);
            }
        }
        
        // Set totalFlow = max(inFlow, outFlow) for proper node sizing
        updateNodeTotalFlows();
        
        // Assign routing (top vs bottom) for circular links to minimize crossings
        assignCircularLinkRouting(circularLinks);
    }
    
    /**
     * Update totalFlow for all nodes across all lists.
     */
    private void updateNodeTotalFlows() {
        for (ArrayList<SankeyNode> list : allNodeLists) {
            for (SankeyNode node : list) {
                node.totalFlow = Math.max(node.inFlow, node.outFlow);
            }
        }
    }
    
    /**
     * Assign top/bottom routing for circular links to minimize visual crossings.
     * Simple heuristic: links to upper-half nodes route top, lower-half route bottom.
     */
    private void assignCircularLinkRouting(ArrayList<SankeyLink> circularLinks) {
        int numSectors = leftNodes.size();
        int midpoint = numSectors / 2;
        
        for (SankeyLink link : circularLinks) {
            // Find the sector node's index
            int sectorIndex = leftNodes.indexOf(link.target);
            // Route upper-half sectors via top, lower-half via bottom
            link.routeTop = (sectorIndex < midpoint);
        }
    }
    
    /**
     * Calculate node positions based on available drawing area.
     * Scales nodes and gaps to fit the specified dimensions.
     */
    private void calculatePositions(int x, int y, int width, int height) {
        // Determine number of columns
        int numColumns = (layoutMode == SankeyLayout.CIRCULAR) ? 2 : 3;
        
        // Calculate dynamic sizing based on available width
        int availableWidth = width - PADDING * 2;
        
        // Node width = ~8% of available width, min 15, max 30
        nodeWidth = Math.max(15, Math.min(30, availableWidth / 12));
        
        // Column gap = remaining space divided by (numColumns)
        int totalNodeWidth = nodeWidth * numColumns;
        int remainingWidth = availableWidth - totalNodeWidth;
        columnGap = remainingWidth / numColumns;  // Gap after each column (last one is margin)
        
        // Node padding scales with height
        int numNodes = Math.max(leftNodes.size(), Math.max(middleNodes.size(), rightNodes.size()));
        nodePadding = Math.max(4, Math.min(12, (height - 60) / Math.max(numNodes * 3, 1)));
        
        // Left margin for labels (approximate max label width)
        int leftLabelMargin = 80;  // Space for left-side labels
        int rightLabelMargin = showScaleBar ? 90 : 60; // Extra space for scale bar
        
        drawX = x + PADDING + leftLabelMargin;
        drawWidth = availableWidth - leftLabelMargin - rightLabelMargin;
        
        if (layoutMode == SankeyLayout.CIRCULAR) {
            // Extra vertical margin for circular links routed around top/bottom
            circularMargin = Math.max(10, height / 10);
            drawY = y + PADDING + 20 + circularMargin;
            drawHeight = height - PADDING * 2 - 30 - circularMargin * 2;
        } else {
            circularMargin = 0;
            drawY = y + PADDING + 20;  // Extra space for title
            drawHeight = height - PADDING * 2 - 30;
        }
        
        // Calculate column X positions using updated drawWidth
        int totalNodeWidthActual = nodeWidth * numColumns;
        int actualGap = (drawWidth - totalNodeWidthActual) / Math.max(numColumns - 1, 1);
        columnGap = actualGap;
        
        int col1X = drawX;
        int col2X = drawX + nodeWidth + columnGap;
        int col3X = drawX + (nodeWidth + columnGap) * 2;
        
        // Calculate total flow per column - we need to fit the tallest column
        double rawMaxFlow = Math.max(1.0, Math.max(
            sumColumnFlow(leftNodes),
            Math.max(sumColumnFlow(middleNodes), sumColumnFlow(rightNodes))));
        
        // Store current max flow for scale bar display
        currentMaxFlow = rawMaxFlow;
        
        // Update high-water mark if tracking
        if (useHighWaterMark && rawMaxFlow > highWaterMark) {
            highWaterMark = rawMaxFlow;
        }
        
        // Determine effective max flow for scaling
        double maxColumnFlow;
        if (fixedMaxScale > 0) {
            // Use fixed max scale
            maxColumnFlow = fixedMaxScale;
        } else if (useHighWaterMark && highWaterMark > 0) {
            // Use high-water mark
            maxColumnFlow = highWaterMark;
        } else {
            // Auto-scale to current max
            maxColumnFlow = rawMaxFlow;
        }
        
        // Sort nodes to minimize link crossings (order by average connected position)
        sortNodesForStraighterPaths();
        
        // Position left column nodes
        positionColumnNodes(leftNodes, col1X, maxColumnFlow);
        
        if (layoutMode == SankeyLayout.CIRCULAR) {
            // Circular mode: only 2 columns (left = sectors, right = transactions)
            positionColumnNodes(rightNodes, col2X, maxColumnFlow);
        } else {
            // Linear mode: 3 columns
            positionColumnNodes(middleNodes, col2X, maxColumnFlow);
            positionColumnNodes(rightNodes, col3X, maxColumnFlow);
        }
        
        // Calculate link positions
        calculateLinkPositions(maxColumnFlow);
    }
    
    /**
     * Sort nodes in each column to minimize link crossings.
     * Orders nodes by their average connected position in adjacent columns.
     */
    private void sortNodesForStraighterPaths() {
        // Build connection maps: node -> list of connected node indices
        // We'll do multiple passes, optimizing each column based on neighbors
        
        // First, assign initial indices based on current order
        for (int i = 0; i < leftNodes.size(); i++) leftNodes.get(i).flowOffsetIn = i;
        for (int i = 0; i < middleNodes.size(); i++) middleNodes.get(i).flowOffsetIn = i;
        for (int i = 0; i < rightNodes.size(); i++) rightNodes.get(i).flowOffsetIn = i;
        
        // Sort middle column by average position of left and right connections
        if (!middleNodes.isEmpty()) {
            sortColumnByConnections(middleNodes, true, true);
        }
        
        // Sort right column by average position of left connections (sources)
        if (!rightNodes.isEmpty()) {
            sortColumnByConnections(rightNodes, true, false);
        }
        
        // Re-sort left column based on updated middle/right positions
        if (!leftNodes.isEmpty()) {
            sortColumnByConnections(leftNodes, false, true);
        }
    }
    
    /**
     * Sort a column's nodes by average Y position of their connections
     */
    private void sortColumnByConnections(ArrayList<SankeyNode> nodes, boolean considerSources, boolean considerTargets) {
        // Calculate average connection index for each node
        final java.util.HashMap<SankeyNode, Double> avgPosition = new java.util.HashMap<>();
        
        for (SankeyNode node : nodes) {
            double sumPos = 0;
            int count = 0;
            
            for (SankeyLink link : links) {
                if (considerTargets && link.source == node) {
                    // This node is a source, look at target position
                    sumPos += link.target.flowOffsetIn;
                    count++;
                }
                if (considerSources && link.target == node) {
                    // This node is a target, look at source position
                    sumPos += link.source.flowOffsetIn;
                    count++;
                }
            }
            
            avgPosition.put(node, count > 0 ? sumPos / count : node.flowOffsetIn);
        }
        
        // Sort by average position
        java.util.Collections.sort(nodes, new java.util.Comparator<SankeyNode>() {
            @Override
            public int compare(SankeyNode a, SankeyNode b) {
                return Double.compare(avgPosition.get(a), avgPosition.get(b));
            }
        });
        
        // Update indices after sort
        for (int i = 0; i < nodes.size(); i++) {
            nodes.get(i).flowOffsetIn = i;
        }
    }
    
    /**
     * Position nodes in a column, distributing them vertically.
     * Scales to fit the available height based on maxColumnFlow.
     */
    private void positionColumnNodes(ArrayList<SankeyNode> nodes, int x, double maxColumnFlow) {
        if (nodes.isEmpty()) return;
        
        // Calculate total flow in this column
        double totalColumnFlow = 0;
        for (SankeyNode node : nodes) {
            totalColumnFlow += node.totalFlow;
        }
        
        // Available height minus padding between nodes
        int totalPadding = (nodes.size() - 1) * nodePadding;
        int availableHeight = drawHeight - totalPadding;
        
        // Scale factor: pixels per unit of flow, based on the tallest column
        double pixelsPerFlow = availableHeight / maxColumnFlow;
        
        // Calculate total height this column will use
        int minNodeHeight = Math.max(6, availableHeight / (nodes.size() * 6));
        int totalNeededHeight = 0;
        for (SankeyNode node : nodes) {
            int h = (int) Math.max(minNodeHeight, node.totalFlow * pixelsPerFlow);
            totalNeededHeight += h;
        }
        totalNeededHeight += totalPadding;
        
        // Start from top (no centering needed since we scale to fit)
        int currentY = drawY;
        for (SankeyNode node : nodes) {
            node.x = x;
            node.y = currentY;
            node.width = nodeWidth;
            
            // Height based on global scale
            node.height = (int) Math.max(minNodeHeight, node.totalFlow * pixelsPerFlow);
            
            currentY += node.height + nodePadding;
            node.flowOffsetIn = 0;
            node.flowOffsetOut = 0;
        }
    }
    
    /**
     * Calculate Y positions and bandwidths for all links.
     * Uses global scale so ribbon width equals flow value in pixels.
     */
    private void calculateLinkPositions(double maxColumnFlow) {
        // Calculate global pixels-per-flow scale based on tallest column
        int maxNodes = Math.max(leftNodes.size(), Math.max(middleNodes.size(), rightNodes.size()));
        int totalPadding = (maxNodes > 1) ? (maxNodes - 1) * nodePadding : 0;
        int availableHeight = drawHeight - totalPadding;
        double pixelsPerFlow = availableHeight / maxColumnFlow;
        
        // Reset flow offsets on all nodes
        resetFlowOffsets();
        
        // Calculate bandwidth using global scale - same flow = same ribbon width
        for (SankeyLink link : links) {
            // Bandwidth based on global scale (same at source and target)
            int bandwidth = (int) Math.max(MIN_BAND_HEIGHT, link.value * pixelsPerFlow);
            link.sourceBandwidth = bandwidth;
            link.targetBandwidth = bandwidth;
            link.bandwidth = bandwidth;
            
            // Position at source node's OUTPUT side (right edge)
            link.sourceY = link.source.y + link.source.flowOffsetOut + bandwidth / 2;
            link.source.flowOffsetOut += bandwidth;
            
            // Position at target node's INPUT side (left edge)
            link.targetY = link.target.y + link.target.flowOffsetIn + bandwidth / 2;
            link.target.flowOffsetIn += bandwidth;
        }
    }
    
    /**
     * Draw the Sankey diagram.
     * The main diagram is throttled for performance, but tooltips update every frame.
     */
    public void draw(Graphics g, int x, int y, int width, int height) {
        // Check if dimensions changed (forces redraw)
        if (x != cachedX || y != cachedY || width != cachedWidth || height != cachedHeight) {
            needsFullRedraw = true;
            cachedX = x;
            cachedY = y;
            cachedWidth = width;
            cachedHeight = height;
        }
        
        // Throttle full diagram redraws
        long now = System.currentTimeMillis();
        if (needsFullRedraw || (now - lastFullDrawTime) >= DRAW_INTERVAL_MS) {
            buildLayout();
            calculatePositions(x, y, width, height);
            lastFullDrawTime = now;
            needsFullRedraw = false;
        }
        
        // Always update hover state (for responsive tooltips)
        updateHoverState();
        
        // Draw the diagram (uses cached layout if not rebuilt)
        drawDiagram(g, x, y, width, height);
    }
    
    /**
     * Force a full redraw on next draw call (e.g., when data changes).
     */
    public void invalidate() {
        needsFullRedraw = true;
    }
    
    /**
     * Draw the diagram content.
     */
    private void drawDiagram(Graphics g, int x, int y, int width, int height) {
        if (links == null || leftNodes == null) return;
        
        // Draw title
        g.setColor("#333333");
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        String suffix = (layoutMode == SankeyLayout.CIRCULAR) ? " (Circular Sankey)" : " (Sankey)";
        String title = table.tableTitle != null ? table.tableTitle + suffix : "Sankey Diagram";
        g.drawString(title, x + PADDING, y + 15);
        
        // Draw forward links first (behind nodes)
        for (SankeyLink link : links) {
            if (!link.isCircular) {
                drawLink(g, link, link == hoveredLink);
            }
        }
        
        // Draw circular links (routed around the diagram)
        for (SankeyLink link : links) {
            if (link.isCircular) {
                drawCircularLink(g, link, link == hoveredLink);
            }
        }
        
        // Draw nodes
        g.setFont(new Font("SansSerif", 0, 10));
        for (SankeyNode node : leftNodes) {
            drawNode(g, node, true, node == hoveredNode);  // Label on left (outer side)
        }
        if (layoutMode != SankeyLayout.CIRCULAR) {
            for (SankeyNode node : middleNodes) {
                drawNode(g, node, false, node == hoveredNode);  // Label centered below
            }
        }
        for (SankeyNode node : rightNodes) {
            drawNode(g, node, false, node == hoveredNode);  // Label on right (outer side)
        }
        
        // Draw scale bar on right side if enabled
        if (showScaleBar) {
            drawScaleBar(g, x, y, width, height);
        }
        
        // Draw tooltip if hovering
        if (hoveredNode != null || hoveredLink != null) {
            drawTooltip(g, x, y, width, height);
        }
    }
    
    /**
     * Update the hover state based on current mouse position
     */
    private void updateHoverState() {
        hoveredNode = null;
        hoveredLink = null;
        
        if (CirSim.theSim.mouseCursorX < 0) return;
        
        int mouseX = CirSim.theSim.inverseTransformX(CirSim.theSim.mouseCursorX);
        int mouseY = CirSim.theSim.inverseTransformY(CirSim.theSim.mouseCursorY);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        
        // Check nodes first
        for (SankeyNode node : leftNodes) {
            if (isPointInNode(mouseX, mouseY, node)) {
                hoveredNode = node;
                return;
            }
        }
        for (SankeyNode node : middleNodes) {
            if (isPointInNode(mouseX, mouseY, node)) {
                hoveredNode = node;
                return;
            }
        }
        for (SankeyNode node : rightNodes) {
            if (isPointInNode(mouseX, mouseY, node)) {
                hoveredNode = node;
                return;
            }
        }
        
        // Check links
        for (SankeyLink link : links) {
            if (isPointNearLink(mouseX, mouseY, link)) {
                hoveredLink = link;
                return;
            }
        }
    }
    
    /**
     * Draw tooltip near the mouse cursor
     */
    private void drawTooltip(Graphics g, int diagramX, int diagramY, int diagramWidth, int diagramHeight) {
        String[] lines;
        if (hoveredNode != null) {
            lines = getNodeInfo(hoveredNode);
        } else if (hoveredLink != null) {
            lines = getLinkInfo(hoveredLink);
        } else {
            return;
        }
        
        // Calculate tooltip size
        g.setFont(new Font("SansSerif", 0, 11));
        int padding = 6;
        int lineHeight = 14;
        int maxWidth = 0;
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                // Use measureWidth to handle subscripts in node names
                double w = g.measureWidth(line);
                maxWidth = Math.max(maxWidth, (int)w);
            }
        }
        int tooltipWidth = maxWidth + padding * 2;
        int tooltipHeight = lines.length * lineHeight + padding * 2;
        
        // Position tooltip near mouse but keep within diagram bounds
        int tooltipX = lastMouseX + 15;
        int tooltipY = lastMouseY - tooltipHeight / 2;
        
        // Keep within diagram bounds
        if (tooltipX + tooltipWidth > diagramX + diagramWidth - 5) {
            tooltipX = lastMouseX - tooltipWidth - 15;
        }
        if (tooltipY < diagramY + 20) {
            tooltipY = diagramY + 20;
        }
        if (tooltipY + tooltipHeight > diagramY + diagramHeight - 5) {
            tooltipY = diagramY + diagramHeight - tooltipHeight - 5;
        }
        
        // Draw tooltip background with modern styling
        g.setColor("#1E293B");  // Slate-800 dark background
        g.context.fillRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight);
        g.setColor("#475569");  // Slate-600 border
        g.context.strokeRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight);
        
        // Draw tooltip text
        g.setColor("#F8FAFC");  // Slate-50 text (light on dark)
        int textY = tooltipY + padding + 11;
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                g.drawString(line, tooltipX + padding, textY);
                textY += lineHeight;
            }
        }
    }
    
    /**
     * Draw scale bar on the right side showing flow magnitude
     */
    private void drawScaleBar(Graphics g, int diagramX, int diagramY, int diagramWidth, int diagramHeight) {
        // Scale bar position - right side of diagram
        int barX = diagramX + diagramWidth - 55;
        int barY = drawY;
        int barWidth = 15;
        int barHeight = drawHeight;
        
        if (barHeight < 30) return;  // Too small to draw meaningfully
        
        // Determine scale value to display
        double scaleMax;
        String scaleMode;
        if (fixedMaxScale > 0) {
            scaleMax = fixedMaxScale;
            scaleMode = "Fixed";
        } else if (useHighWaterMark && highWaterMark > 0) {
            scaleMax = highWaterMark;
            scaleMode = "Peak";
        } else {
            scaleMax = currentMaxFlow;
            scaleMode = "Auto";
        }
        
        // Draw gradient bar (darker at top = higher value)
        for (int i = 0; i < barHeight; i++) {
            double t = 1.0 - (double) i / barHeight;  // 1 at top, 0 at bottom
            int gray = (int) (220 - t * 150);  // Light gray at bottom, darker at top
            g.setColor("rgb(" + gray + "," + gray + "," + (gray + 20) + ")");
            g.context.fillRect(barX, barY + i, barWidth, 1);
        }
        
        // Draw border
        g.setColor("#666666");
        g.context.strokeRect(barX, barY, barWidth, barHeight);
        
        // Draw tick marks and labels
        g.context.setFont("9px sans-serif");
        int numTicks = 5;
        for (int i = 0; i <= numTicks; i++) {
            double t = (double) i / numTicks;
            int tickY = barY + (int) ((1 - t) * barHeight);
            double value = t * scaleMax;
            
            // Tick mark
            g.setColor("#333333");
            g.context.fillRect(barX + barWidth, tickY, 3, 1);
            
            // Value text
            String valueText = CircuitElm.getUnitText(value, "$");
            g.drawString(valueText, barX + barWidth + 5, tickY + 3);
        }
        
        // Draw current flow indicator if using fixed/HWM scale
        if ((fixedMaxScale > 0 || useHighWaterMark) && currentMaxFlow < scaleMax) {
            double currentT = currentMaxFlow / scaleMax;
            int currentY = barY + (int) ((1 - currentT) * barHeight);
            
            // Draw arrow pointing to current level
            g.setColor("#EF4444");  // Red-500
            g.context.beginPath();
            g.context.moveTo(barX - 3, currentY);
            g.context.lineTo(barX - 8, currentY - 4);
            g.context.lineTo(barX - 8, currentY + 4);
            g.context.closePath();
            g.context.fill();
        }
        
        // Draw scale mode label at bottom
        g.setColor("#666666");
        g.context.setFont("8px sans-serif");
        g.drawString(scaleMode, barX, barY + barHeight + 12);
    }

    /**
     * Draw a single node rectangle with label
     */
    private void drawNode(Graphics g, SankeyNode node, boolean labelLeft, boolean highlighted) {
        // Draw highlight glow if hovered
        if (highlighted) {
            g.setColor("rgba(251, 191, 36, 0.4)");  // Amber-400 glow
            g.context.fillRect(node.x - 3, node.y - 3, node.width + 6, node.height + 6);
        }
        
        // Draw node rectangle
        g.setColor(node.color);
        g.context.fillRect(node.x, node.y, node.width, node.height);
        
        // Draw border (thicker if highlighted)
        if (highlighted) {
            g.setColor("#F59E0B");  // Amber-500 border when highlighted
            g.context.setLineWidth(2);
        } else {
            g.setColor("#475569");  // Slate-600 border
            g.context.setLineWidth(1);
        }
        g.context.strokeRect(node.x, node.y, node.width, node.height);
        g.context.setLineWidth(1);  // Reset
        
        // Draw label
        g.setColor("#333333");
        String label = node.name;  // Show full label text
        
        if (labelLeft) {
            // Draw label to the left of node (right-justified)
            // Use measureWidth which properly handles subscripts _{} at smaller font size
            double textWidth = g.measureWidth(label);
            g.drawString(label, (int)(node.x - textWidth - 4), node.y + node.height / 2 + 4);
        } else {
            // Draw label to the right of node
            g.drawString(label, node.x + node.width + 4, node.y + node.height / 2 + 4);
        }
    }
    
    /**
     * Draw a curved flow band between two nodes (tapered if bandwidths differ)
     */
    private void drawLink(Graphics g, SankeyLink link, boolean highlighted) {
        int x1 = link.source.x + link.source.width;
        int y1 = link.sourceY;
        int x2 = link.target.x;
        int y2 = link.targetY;
        int halfBandSource = link.sourceBandwidth / 2;
        int halfBandTarget = link.targetBandwidth / 2;
        
        // Control points for bezier curve (horizontal S-curve)
        int cpOffset = (x2 - x1) / 2;
        
        // Use brighter color if highlighted
        if (highlighted) {
            g.setColor("rgba(251, 191, 36, 0.9)");  // Amber-400 highlight
        } else {
            g.setColor(link.color);
        }
        
        // Draw filled bezier band (tapered from source to target)
        g.context.beginPath();
        
        // Top edge of band - from source top to target top
        g.context.moveTo(x1, y1 - halfBandSource);
        g.context.bezierCurveTo(
            x1 + cpOffset, y1 - halfBandSource,
            x2 - cpOffset, y2 - halfBandTarget,
            x2, y2 - halfBandTarget
        );
        
        // Right edge (down at target)
        g.context.lineTo(x2, y2 + halfBandTarget);
        
        // Bottom edge of band (reverse direction) - from target bottom to source bottom
        g.context.bezierCurveTo(
            x2 - cpOffset, y2 + halfBandTarget,
            x1 + cpOffset, y1 + halfBandSource,
            x1, y1 + halfBandSource
        );
        
        // Left edge (up at source) - implicit close
        g.context.closePath();
        g.context.fill();
        
        // Draw border if highlighted
        if (highlighted) {
            g.setColor("#F59E0B");  // Amber-500
            g.context.stroke();
        }
        
        // Draw flow value if enabled
        if (showFlowValues && link.bandwidth >= 8) {
            drawFlowValue(g, link, x1, y1, x2, y2);
        }
    }
    
    /**
     * Draw a numeric flow value on a link
     */
    private void drawFlowValue(Graphics g, SankeyLink link, int x1, int y1, int x2, int y2) {
        // Position value 3/4 of the way along the link (closer to target)
        int valueX = x1 + (x2 - x1) * 3 / 4;
        int valueY = y1 + (y2 - y1) * 3 / 4;
        
        // Format the value
        String valueText = CircuitElm.getUnitText(link.value, "$");
        
        // Draw background for readability
        g.context.setFont("9px sans-serif");
        double textWidth = g.context.measureText(valueText).getWidth();
        int padding = 2;
        int bgWidth = (int) textWidth + padding * 2;
        int bgHeight = 12;
        
        g.setColor("rgba(255, 255, 255, 0.85)");
        g.context.fillRect(valueX - bgWidth / 2, valueY - bgHeight / 2, bgWidth, bgHeight);
        
        // Draw value text
        g.setColor("#333333");
        g.drawString(valueText, (int)(valueX - textWidth / 2), valueY + 3);
    }
    
    /**
     * Draw a numeric flow value at a specific position
     */
    private void drawFlowValueAt(Graphics g, double value, int valueX, int valueY) {
        // Format the value
        String valueText = CircuitElm.getUnitText(value, "$");
        
        // Draw background for readability
        g.context.setFont("9px sans-serif");
        double textWidth = g.context.measureText(valueText).getWidth();
        int padding = 2;
        int bgWidth = (int) textWidth + padding * 2;
        int bgHeight = 12;
        
        g.setColor("rgba(255, 255, 255, 0.85)");
        g.context.fillRect(valueX - bgWidth / 2, valueY - bgHeight / 2, bgWidth, bgHeight);
        
        // Draw value text
        g.setColor("#333333");
        g.drawString(valueText, (int)(valueX - textWidth / 2), valueY + 3);
    }
    
    /**
     * Draw a circular link that routes around the top or bottom of the diagram.
     * Used when a link goes "backwards" (right to left) in circular Sankey mode.
     * Supports tapered bands where source and target widths differ.
     */
    private void drawCircularLink(Graphics g, SankeyLink link, boolean highlighted) {
        // Source is on the right (transaction), target is on the left (sector)
        int x1 = link.source.x + link.source.width;  // Right edge of source
        int y1 = link.sourceY;
        int x2 = link.target.x;  // Left edge of target
        int y2 = link.targetY;
        int halfBandSource = link.sourceBandwidth / 2;
        int halfBandTarget = link.targetBandwidth / 2;
        // For the middle routing section, interpolate between source and target bandwidths
        int halfBandMid = (halfBandSource + halfBandTarget) / 2;
        
        // Calculate the routing path around top or bottom
        // Use dynamic circularMargin for routing distance
        int margin = Math.max(8, circularMargin - 5);  // Slightly less than the reserved margin
        int routeY;
        if (link.routeTop) {
            // Route above the diagram
            routeY = drawY - margin;
        } else {
            // Route below the diagram
            routeY = drawY + drawHeight + margin;
        }
        
        // Radius for the curved corners - scales with diagram size
        int cornerRadius = Math.min(Math.max(8, circularMargin / 2), Math.abs(y1 - routeY) / 2);
        
        // Use brighter color if highlighted
        if (highlighted) {
            g.setColor("rgba(251, 191, 36, 0.9)");  // Amber-400 highlight
        } else {
            g.setColor(link.color);
        }
        
        // Draw as a path with rounded corners:
        // Start at source → go right a bit → curve up/down → horizontal across top/bottom → curve down/up → to target
        g.context.beginPath();
        
        // Extension distances scale with column gap
        int extensionRight = Math.max(5, columnGap / 8);
        int extensionLeft = Math.max(5, columnGap / 8);
        
        // Top edge of the band (source uses halfBandSource, mid uses halfBandMid, target uses halfBandTarget)
        // Start from source, right edge
        g.context.moveTo(x1, y1 - halfBandSource);
        
        // Extend right
        g.context.lineTo(x1 + extensionRight, y1 - halfBandSource);
        
        // Curve to vertical direction (up if routeTop, down if routeBottom)
        if (link.routeTop) {
            // Curve up-left
            g.context.quadraticCurveTo(x1 + extensionRight + cornerRadius, y1 - halfBandSource, 
                                        x1 + extensionRight + cornerRadius, y1 - halfBandSource - cornerRadius);
            // Vertical up to route level
            g.context.lineTo(x1 + extensionRight + cornerRadius, routeY + cornerRadius - halfBandMid);
            // Curve to horizontal
            g.context.quadraticCurveTo(x1 + extensionRight + cornerRadius, routeY - halfBandMid,
                                        x1 + extensionRight, routeY - halfBandMid);
            // Horizontal across the top
            g.context.lineTo(x2 - extensionLeft, routeY - halfBandMid);
            // Curve down
            g.context.quadraticCurveTo(x2 - extensionLeft - cornerRadius, routeY - halfBandMid,
                                        x2 - extensionLeft - cornerRadius, routeY + cornerRadius - halfBandMid);
            // Vertical down to target level
            g.context.lineTo(x2 - extensionLeft - cornerRadius, y2 - halfBandTarget - cornerRadius);
            // Curve to horizontal toward target
            g.context.quadraticCurveTo(x2 - extensionLeft - cornerRadius, y2 - halfBandTarget,
                                        x2 - extensionLeft, y2 - halfBandTarget);
        } else {
            // Route via bottom - mirror the logic
            g.context.quadraticCurveTo(x1 + extensionRight + cornerRadius, y1 - halfBandSource,
                                        x1 + extensionRight + cornerRadius, y1 - halfBandSource + cornerRadius);
            g.context.lineTo(x1 + extensionRight + cornerRadius, routeY - cornerRadius - halfBandMid);
            g.context.quadraticCurveTo(x1 + extensionRight + cornerRadius, routeY - halfBandMid,
                                        x1 + extensionRight, routeY - halfBandMid);
            g.context.lineTo(x2 - extensionLeft, routeY - halfBandMid);
            g.context.quadraticCurveTo(x2 - extensionLeft - cornerRadius, routeY - halfBandMid,
                                        x2 - extensionLeft - cornerRadius, routeY - cornerRadius - halfBandMid);
            g.context.lineTo(x2 - extensionLeft - cornerRadius, y2 - halfBandTarget + cornerRadius);
            g.context.quadraticCurveTo(x2 - extensionLeft - cornerRadius, y2 - halfBandTarget,
                                        x2 - extensionLeft, y2 - halfBandTarget);
        }
        
        // End at target
        g.context.lineTo(x2, y2 - halfBandTarget);
        
        // Bottom edge of target (down)
        g.context.lineTo(x2, y2 + halfBandTarget);
        
        // Now trace back the bottom edge of the band (reverse direction)
        g.context.lineTo(x2 - extensionLeft, y2 + halfBandTarget);
        
        if (link.routeTop) {
            g.context.quadraticCurveTo(x2 - extensionLeft - cornerRadius, y2 + halfBandTarget,
                                        x2 - extensionLeft - cornerRadius, y2 + halfBandTarget - cornerRadius);
            g.context.lineTo(x2 - extensionLeft - cornerRadius, routeY + cornerRadius + halfBandMid);
            g.context.quadraticCurveTo(x2 - extensionLeft - cornerRadius, routeY + halfBandMid,
                                        x2 - extensionLeft, routeY + halfBandMid);
            g.context.lineTo(x1 + extensionRight, routeY + halfBandMid);
            g.context.quadraticCurveTo(x1 + extensionRight + cornerRadius, routeY + halfBandMid,
                                        x1 + extensionRight + cornerRadius, routeY + cornerRadius + halfBandMid);
            g.context.lineTo(x1 + extensionRight + cornerRadius, y1 + halfBandSource - cornerRadius);
            g.context.quadraticCurveTo(x1 + extensionRight + cornerRadius, y1 + halfBandSource,
                                        x1 + extensionRight, y1 + halfBandSource);
        } else {
            g.context.quadraticCurveTo(x2 - extensionLeft - cornerRadius, y2 + halfBandTarget,
                                        x2 - extensionLeft - cornerRadius, y2 + halfBandTarget + cornerRadius);
            g.context.lineTo(x2 - extensionLeft - cornerRadius, routeY - cornerRadius + halfBandMid);
            g.context.quadraticCurveTo(x2 - extensionLeft - cornerRadius, routeY + halfBandMid,
                                        x2 - extensionLeft, routeY + halfBandMid);
            g.context.lineTo(x1 + extensionRight, routeY + halfBandMid);
            g.context.quadraticCurveTo(x1 + extensionRight + cornerRadius, routeY + halfBandMid,
                                        x1 + extensionRight + cornerRadius, routeY - cornerRadius + halfBandMid);
            g.context.lineTo(x1 + extensionRight + cornerRadius, y1 + halfBandSource + cornerRadius);
            g.context.quadraticCurveTo(x1 + extensionRight + cornerRadius, y1 + halfBandSource,
                                        x1 + extensionRight, y1 + halfBandSource);
        }
        
        // Back to source start
        g.context.lineTo(x1, y1 + halfBandSource);
        
        g.context.closePath();
        g.context.fill();
        
        // Draw a small arrow to indicate direction (optional enhancement)
        drawCircularLinkArrow(g, link, x2, y2);
        
        // Draw flow value if enabled (position at midpoint of route)
        if (showFlowValues && link.bandwidth >= 8) {
            int valueX = (x1 + x2) / 2;
            int valueY = routeY;
            drawFlowValueAt(g, link.value, valueX, valueY);
        }
    }
    
    /**
     * Draw a small directional arrow on a circular link
     */
    private void drawCircularLinkArrow(Graphics g, SankeyLink link, int tipX, int tipY) {
        int arrowSize = 6;
        
        // Arrow pointing left (toward target)
        g.context.beginPath();
        g.context.moveTo(tipX, tipY);  // Arrow tip
        g.context.lineTo(tipX + arrowSize, tipY - arrowSize);
        g.context.lineTo(tipX + arrowSize, tipY + arrowSize);
        g.context.closePath();
        
        // Use a darker version of the link color
        g.setColor(link.color.replace("0.6", "0.9"));
        g.context.fill();
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Sum the total flow of all nodes in a column.
     */
    private double sumColumnFlow(ArrayList<SankeyNode> nodes) {
        double sum = 0;
        for (SankeyNode node : nodes) {
            sum += node.totalFlow;
        }
        return sum;
    }
    
    /**
     * Reset flow offsets on all nodes in all lists.
     */
    private void resetFlowOffsets() {
        for (ArrayList<SankeyNode> list : allNodeLists) {
            for (SankeyNode node : list) {
                node.flowOffsetIn = 0;
                node.flowOffsetOut = 0;
            }
        }
    }
    
    /**
     * Get color for a sector
     */
    private String getSectorColor(String sectorName) {
        String color = SECTOR_COLORS.get(sectorName);
        return color != null ? color : DEFAULT_SECTOR_COLOR;
    }
    
    /**
     * Get sector color with alpha transparency
     */
    private String getSectorColorAlpha(String sectorName, double alpha) {
        String hex = getSectorColor(sectorName);
        return hexToRgba(hex, alpha);
    }
    
    /**
     * Convert hex color to rgba string
     */
    private String hexToRgba(String hex, double alpha) {
        if (hex == null || hex.length() < 7) {
            return "rgba(128,128,128," + alpha + ")";
        }
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            return "rgba(" + r + "," + g + "," + b + "," + alpha + ")";
        } catch (Exception e) {
            return "rgba(128,128,128," + alpha + ")";
        }
    }
}
