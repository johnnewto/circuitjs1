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
 * Layout: 3 columns
 * - Left: Source sectors (those with outflows)
 * - Middle: Transaction types
 * - Right: Target sectors (those with inflows)
 */
public class SFCSankeyRenderer {
    
    private SFCTableElm table;
    
    // Layout constants
    private static final int PADDING = 10;
    private static final int NODE_WIDTH = 20;
    private static final int NODE_PADDING = 8;  // Vertical gap between nodes
    private static final int COLUMN_GAP = 60;   // Horizontal gap between columns
    private static final int MIN_BAND_HEIGHT = 2;
    
    // Sector colors
    private static final Map<String, String> SECTOR_COLORS = new HashMap<>();
    static {
        SECTOR_COLORS.put("Households", "#636EFA");
        SECTOR_COLORS.put("Firms", "#00CC96");
        SECTOR_COLORS.put("Banks", "#FFA15A");
        SECTOR_COLORS.put("Govt", "#EF553B");
        SECTOR_COLORS.put("Government", "#EF553B");
        SECTOR_COLORS.put("Central Bank", "#AB63FA");
        SECTOR_COLORS.put("Foreign", "#19D3F3");
    }
    private static final String DEFAULT_SECTOR_COLOR = "#AB63FA";
    private static final String TRANSACTION_COLOR = "#888888";
    
    // Cached layout data
    private ArrayList<SankeyNode> leftNodes;    // Source sectors
    private ArrayList<SankeyNode> middleNodes;  // Transactions
    private ArrayList<SankeyNode> rightNodes;   // Target sectors
    private ArrayList<SankeyLink> links;
    
    // Drawing area
    private int drawX, drawY, drawWidth, drawHeight;
    
    /**
     * Represents a node in the Sankey diagram
     */
    private static class SankeyNode {
        String name;
        String color;
        double totalFlow;  // Sum of all flows through this node
        int x, y, width, height;
        int flowOffset;    // Current offset for stacking links
        
        SankeyNode(String name, String color) {
            this.name = name;
            this.color = color;
            this.totalFlow = 0;
            this.flowOffset = 0;
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
        
        // Calculated positions
        int sourceY, targetY, bandwidth;
    }
    
    public SFCSankeyRenderer(SFCTableElm table) {
        this.table = table;
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
        leftNodes = new ArrayList<>();
        middleNodes = new ArrayList<>();
        rightNodes = new ArrayList<>();
        links = new ArrayList<>();
        
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
                        link.color = getSectorColorAlpha(sectorName, 0.6);
                        link.label = sectorName + " → " + transactionName;
                        links.add(link);
                        
                        sourceNode.totalFlow += Math.abs(value);
                        transactionNode.totalFlow += Math.abs(value);
                    }
                } else {
                    // Inflow: transaction -> right sector
                    SankeyNode targetNode = rightNodeMap.get(sectorName);
                    if (transactionNode != null && targetNode != null) {
                        SankeyLink link = new SankeyLink();
                        link.source = transactionNode;
                        link.target = targetNode;
                        link.value = value;
                        link.color = getSectorColorAlpha(sectorName, 0.6);
                        link.label = transactionName + " → " + sectorName;
                        links.add(link);
                        
                        // Note: transaction node flow already counted from left side
                        targetNode.totalFlow += value;
                    }
                }
            }
        }
    }
    
    /**
     * Calculate node positions based on available drawing area
     */
    private void calculatePositions(int x, int y, int width, int height) {
        drawX = x + PADDING;
        drawY = y + PADDING + 20;  // Extra space for title
        drawWidth = width - PADDING * 2;
        drawHeight = height - PADDING * 2 - 30;
        
        // Calculate column X positions
        int col1X = drawX;
        int col2X = drawX + NODE_WIDTH + COLUMN_GAP;
        int col3X = drawX + (NODE_WIDTH + COLUMN_GAP) * 2;
        
        // Find max flow for scaling
        double maxFlow = 1;
        for (SankeyNode node : leftNodes) {
            maxFlow = Math.max(maxFlow, node.totalFlow);
        }
        for (SankeyNode node : middleNodes) {
            maxFlow = Math.max(maxFlow, node.totalFlow);
        }
        for (SankeyNode node : rightNodes) {
            maxFlow = Math.max(maxFlow, node.totalFlow);
        }
        
        // Position left column nodes
        positionColumnNodes(leftNodes, col1X, maxFlow);
        
        // Position middle column nodes
        positionColumnNodes(middleNodes, col2X, maxFlow);
        
        // Position right column nodes
        positionColumnNodes(rightNodes, col3X, maxFlow);
        
        // Calculate link positions
        calculateLinkPositions(maxFlow);
    }
    
    /**
     * Position nodes in a column, distributing them vertically
     */
    private void positionColumnNodes(ArrayList<SankeyNode> nodes, int x, double maxFlow) {
        if (nodes.isEmpty()) return;
        
        // Calculate total height needed
        int availableHeight = drawHeight - (nodes.size() - 1) * NODE_PADDING;
        
        // Calculate heights proportional to flow (with minimum)
        double totalFlow = 0;
        for (SankeyNode node : nodes) {
            totalFlow += Math.max(node.totalFlow, maxFlow * 0.1);  // Minimum 10% of max
        }
        
        int currentY = drawY;
        for (SankeyNode node : nodes) {
            node.x = x;
            node.y = currentY;
            node.width = NODE_WIDTH;
            
            double nodeFlow = Math.max(node.totalFlow, maxFlow * 0.1);
            node.height = (int) Math.max(15, (nodeFlow / totalFlow) * availableHeight);
            
            currentY += node.height + NODE_PADDING;
            node.flowOffset = 0;  // Reset for link positioning
        }
    }
    
    /**
     * Calculate Y positions and bandwidths for all links
     */
    private void calculateLinkPositions(double maxFlow) {
        // Scale factor for bandwidth
        double totalFlow = 0;
        for (SankeyLink link : links) {
            totalFlow += link.value;
        }
        
        double bandwidthScale = (drawHeight * 0.8) / Math.max(totalFlow, 1);
        
        for (SankeyLink link : links) {
            link.bandwidth = (int) Math.max(MIN_BAND_HEIGHT, link.value * bandwidthScale * 0.3);
            
            // Position at source node
            link.sourceY = link.source.y + link.source.flowOffset + link.bandwidth / 2;
            link.source.flowOffset += link.bandwidth + 2;
            
            // Position at target node
            link.targetY = link.target.y + link.target.flowOffset + link.bandwidth / 2;
            link.target.flowOffset += link.bandwidth + 2;
        }
    }
    
    /**
     * Draw the Sankey diagram
     */
    public void draw(Graphics g, int x, int y, int width, int height) {
        buildLayout();
        calculatePositions(x, y, width, height);
        
        // Draw title
        g.setColor(Color.white);
        g.context.setFont("bold 12px sans-serif");
        String title = table.tableTitle != null ? table.tableTitle + " (Sankey)" : "Sankey Diagram";
        g.drawString(title, x + PADDING, y + 15);
        
        // Draw links first (behind nodes)
        for (SankeyLink link : links) {
            drawLink(g, link);
        }
        
        // Draw nodes
        g.context.setFont("10px sans-serif");
        for (SankeyNode node : leftNodes) {
            drawNode(g, node, true);  // Label on left
        }
        for (SankeyNode node : middleNodes) {
            drawNode(g, node, false);  // Label centered below
        }
        for (SankeyNode node : rightNodes) {
            drawNode(g, node, false);  // Label on right
        }
    }
    
    /**
     * Draw a single node rectangle with label
     */
    private void drawNode(Graphics g, SankeyNode node, boolean labelLeft) {
        // Draw node rectangle
        g.setColor(node.color);
        g.context.fillRect(node.x, node.y, node.width, node.height);
        
        // Draw border
        g.setColor("#333333");
        g.context.strokeRect(node.x, node.y, node.width, node.height);
        
        // Draw label
        g.setColor(Color.white);
        String label = truncateLabel(node.name, 10);
        
        if (labelLeft) {
            // Draw label to the left of node
            double textWidth = g.context.measureText(label).getWidth();
            g.drawString(label, (int)(node.x - textWidth - 4), node.y + node.height / 2 + 4);
        } else {
            // Draw label to the right of node
            g.drawString(label, node.x + node.width + 4, node.y + node.height / 2 + 4);
        }
    }
    
    /**
     * Draw a curved flow band between two nodes
     */
    private void drawLink(Graphics g, SankeyLink link) {
        int x1 = link.source.x + link.source.width;
        int y1 = link.sourceY;
        int x2 = link.target.x;
        int y2 = link.targetY;
        int halfBand = link.bandwidth / 2;
        
        // Control points for bezier curve (horizontal S-curve)
        int cpOffset = (x2 - x1) / 2;
        
        g.setColor(link.color);
        
        // Draw filled bezier band
        g.context.beginPath();
        
        // Top edge of band
        g.context.moveTo(x1, y1 - halfBand);
        g.context.bezierCurveTo(
            x1 + cpOffset, y1 - halfBand,
            x2 - cpOffset, y2 - halfBand,
            x2, y2 - halfBand
        );
        
        // Right edge (down)
        g.context.lineTo(x2, y2 + halfBand);
        
        // Bottom edge of band (reverse direction)
        g.context.bezierCurveTo(
            x2 - cpOffset, y2 + halfBand,
            x1 + cpOffset, y1 + halfBand,
            x1, y1 + halfBand
        );
        
        // Left edge (up) - implicit close
        g.context.closePath();
        g.context.fill();
    }
    
    /**
     * Truncate a label to max characters with ellipsis
     */
    private String truncateLabel(String label, int maxChars) {
        if (label == null) return "";
        if (label.length() <= maxChars) return label;
        return label.substring(0, maxChars - 1) + "…";
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
