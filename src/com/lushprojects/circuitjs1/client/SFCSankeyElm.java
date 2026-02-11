/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.SFCSankeyRenderer.SankeyLayout;

/**
 * SFCSankeyElm - Standalone Sankey diagram element for SFC tables
 * 
 * A separate circuit element that displays a Sankey diagram visualization
 * of money flows from an SFCTableElm. Can be placed alongside the table
 * to show both views simultaneously.
 * 
 * The element automatically finds an SFCTableElm in the circuit to visualize,
 * or can be configured to use a specific table by name.
 */
public class SFCSankeyElm extends CircuitElm {
    
    // Reference to the source table
    private SFCTableElm sourceTable;
    private String sourceTableName = "";  // Empty = auto-find first SFC table
    
    // Sankey renderer
    private SFCSankeyRenderer sankeyRenderer;
    
    // Layout properties
    private SankeyLayout layoutMode = SankeyLayout.LINEAR;
    private int width = 300;
    private int height = 250;
    
    // Scale visualization options
    private boolean showScaleBar = true;       // Option 1: Show scale bar on RHS
    private double fixedMaxScale = 0;           // Option 2: Fixed scale (0 = auto)
    private boolean useHighWaterMark = false;   // Option 3: Use historical peak
    private boolean showFlowLabels = false;     // Option 5: Numeric labels on links
    
    // Size in grid units
    private int sizeX, sizeY;
    
    /**
     * Constructor for new element created from menu
     */
    public SFCSankeyElm(int xx, int yy) {
        super(xx, yy);
        setupSize();
    }
    
    /**
     * Constructor for loading from file
     */
    public SFCSankeyElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        
        if (st.hasMoreTokens()) {
            try {
                sourceTableName = CustomLogicModel.unescape(st.nextToken());
            } catch (Exception e) {
                sourceTableName = "";
            }
        }
        if (st.hasMoreTokens()) {
            try {
                layoutMode = SankeyLayout.valueOf(st.nextToken());
            } catch (Exception e) {
                layoutMode = SankeyLayout.LINEAR;
            }
        }
        if (st.hasMoreTokens()) {
            try {
                width = Integer.parseInt(st.nextToken());
            } catch (Exception e) {
                width = 300;
            }
        }
        if (st.hasMoreTokens()) {
            try {
                height = Integer.parseInt(st.nextToken());
            } catch (Exception e) {
                height = 250;
            }
        }
        // Load scale visualization options
        if (st.hasMoreTokens()) {
            try {
                showScaleBar = st.nextToken().equals("1");
            } catch (Exception e) {
                showScaleBar = true;
            }
        }
        if (st.hasMoreTokens()) {
            try {
                fixedMaxScale = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                fixedMaxScale = 0;
            }
        }
        if (st.hasMoreTokens()) {
            try {
                useHighWaterMark = st.nextToken().equals("1");
            } catch (Exception e) {
                useHighWaterMark = false;
            }
        }
        if (st.hasMoreTokens()) {
            try {
                showFlowLabels = st.nextToken().equals("1");
            } catch (Exception e) {
                showFlowLabels = false;
            }
        }
        
        setupSize();
    }
    
    private void setupSize() {
        sizeX = (width + sim.gridSize - 1) / sim.gridSize;
        sizeY = (height + sim.gridSize - 1) / sim.gridSize;
        
        // Adjust x2, y2 based on size
        x2 = x + sizeX * sim.gridSize;
        y2 = y + sizeY * sim.gridSize;
    }
    
    @Override
    int getDumpType() {
        return 466;  // Unique dump type for SFC Sankey element
    }
    
    @Override
    public String dump() {
        return super.dump() + " " + CustomLogicModel.escape(sourceTableName) + " " + 
               layoutMode.name() + " " + width + " " + height + " " +
               (showScaleBar ? "1" : "0") + " " + fixedMaxScale + " " +
               (useHighWaterMark ? "1" : "0") + " " + (showFlowLabels ? "1" : "0");
    }
    
    @Override
    public int getPostCount() {
        return 0;  // Display-only, no circuit connections
    }
    
    @Override
    void setPoints() {
        super.setPoints();
        setupSize();
    }
    
    /**
     * Find the source SFCTableElm to visualize
     */
    private SFCTableElm findSourceTable() {
        if (sim == null || sim.elmList == null) {
            return null;
        }
        
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            if (elm instanceof SFCTableElm) {
                SFCTableElm table = (SFCTableElm) elm;
                
                // If no specific name, use first found
                if (sourceTableName == null || sourceTableName.isEmpty()) {
                    return table;
                }
                
                // Match by table title
                if (table.tableTitle != null && table.tableTitle.equals(sourceTableName)) {
                    return table;
                }
            }
        }
        
        return null;
    }
    
    @Override
    void draw(Graphics g) {
        // Find source table if needed
        sourceTable = findSourceTable();
        
        if (sourceTable == null) {
            // Draw placeholder when no table found
            drawPlaceholder(g);
            return;
        }
        
        // Create or update renderer
        if (sankeyRenderer == null || sankeyRenderer.getTable() != sourceTable) {
            sankeyRenderer = new SFCSankeyRenderer(sourceTable);
        }
        sankeyRenderer.setLayoutMode(layoutMode);
        sankeyRenderer.setShowScaleBar(showScaleBar);
        sankeyRenderer.setFixedMaxScale(fixedMaxScale);
        sankeyRenderer.setUseHighWaterMark(useHighWaterMark);
        sankeyRenderer.setShowFlowLabels(showFlowLabels);
        
        // Draw the Sankey diagram
        int drawWidth = sizeX * sim.gridSize;
        int drawHeight = sizeY * sim.gridSize;
        
        // Draw background
        g.setColor("#ffffff");
        g.context.fillRect(x, y, drawWidth, drawHeight);
        
        // Draw border
        if (needsHighlight()) {
            g.setColor(selectColor);
        } else {
            g.setColor("#cccccc");
        }
        g.context.strokeRect(x, y, drawWidth, drawHeight);
        
        // Draw the Sankey
        sankeyRenderer.draw(g, x, y, drawWidth, drawHeight);
        
        // Set bounding box for selection
        setBbox(x, y, x + drawWidth, y + drawHeight);
    }
    
    /**
     * Draw placeholder when no source table is found
     */
    private void drawPlaceholder(Graphics g) {
        int drawWidth = sizeX * sim.gridSize;
        int drawHeight = sizeY * sim.gridSize;
        
        // Draw background
        g.setColor("#ffffff");
        g.context.fillRect(x, y, drawWidth, drawHeight);
        
        // Draw border
        if (needsHighlight()) {
            g.setColor(selectColor);
        } else {
            g.setColor("#cccccc");
        }
        g.context.strokeRect(x, y, drawWidth, drawHeight);
        
        // Draw message
        g.setColor("#888888");
        g.context.setFont("12px sans-serif");
        String msg = "Sankey Diagram";
        g.drawString(msg, x + 10, y + 20);
        
        g.context.setFont("10px sans-serif");
        if (sourceTableName != null && !sourceTableName.isEmpty()) {
            g.drawString("Table: " + sourceTableName, x + 10, y + 40);
            g.drawString("(not found)", x + 10, y + 55);
        } else {
            g.drawString("No SFC Table found", x + 10, y + 40);
        }
        
        setBbox(x, y, x + drawWidth, y + drawHeight);
    }
    
    @Override
    public void getInfo(String[] arr) {
        // Check if mouse is over a specific node or link
        if (sankeyRenderer != null && CirSim.theSim.mouseCursorX >= 0) {
            // Get mouse position in grid coords
            int mouseX = CirSim.theSim.inverseTransformX(CirSim.theSim.mouseCursorX);
            int mouseY = CirSim.theSim.inverseTransformY(CirSim.theSim.mouseCursorY);
            
            String[] hoverInfo = sankeyRenderer.getHoverInfo(mouseX, mouseY);
            if (hoverInfo != null) {
                // Show specific node/link info
                for (int i = 0; i < hoverInfo.length && i < arr.length; i++) {
                    arr[i] = hoverInfo[i];
                }
                return;
            }
        }
        
        // Default info
        arr[0] = "SFC Sankey Diagram";
        arr[1] = "Layout: " + (layoutMode == SankeyLayout.CIRCULAR ? "Circular" : "Linear");
        arr[2] = "Size: " + width + " x " + height;
        if (sourceTable != null) {
            arr[3] = "Source: " + sourceTable.tableTitle;
        } else if (sourceTableName != null && !sourceTableName.isEmpty()) {
            arr[3] = "Source: " + sourceTableName + " (not found)";
        } else {
            arr[3] = "Source: (auto)";
        }
    }
    
    @Override
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Source Table (blank=auto)", sourceTableName);
            return ei;
        }
        if (n == 1) {
            EditInfo ei = new EditInfo("Layout", 0);
            ei.choice = new Choice();
            ei.choice.add("Linear (3 columns)");
            ei.choice.add("Circular (feedback links)");
            ei.choice.select(layoutMode == SankeyLayout.CIRCULAR ? 1 : 0);
            return ei;
        }
        if (n == 2) {
            return new EditInfo("Width (pixels)", width, 150, 800);
        }
        if (n == 3) {
            return new EditInfo("Height (pixels)", height, 100, 600);
        }
        // Scale visualization options
        if (n == 4) {
            return EditInfo.createCheckbox("Show Scale Bar", showScaleBar);
        }
        if (n == 5) {
            return new EditInfo("Fixed Max Scale (0=auto)", fixedMaxScale, 0, 1e12);
        }
        if (n == 6) {
            return EditInfo.createCheckbox("Use High-Water Mark", useHighWaterMark);
        }
        if (n == 7) {
            return EditInfo.createCheckbox("Show Flow Labels", showFlowLabels);
        }
        return null;
    }
    
    @Override
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            sourceTableName = ei.textf.getText().trim();
            sourceTable = null;  // Force re-find
            sankeyRenderer = null;  // Force re-create
        } else if (n == 1) {
            layoutMode = (ei.choice.getSelectedIndex() == 1) ? SankeyLayout.CIRCULAR : SankeyLayout.LINEAR;
            if (sankeyRenderer != null) {
                sankeyRenderer.setLayoutMode(layoutMode);
            }
        } else if (n == 2) {
            width = Math.max(150, (int)ei.value);
            setupSize();
        } else if (n == 3) {
            height = Math.max(100, (int)ei.value);
            setupSize();
        } else if (n == 4) {
            showScaleBar = ei.checkbox.getState();
            if (sankeyRenderer != null) {
                sankeyRenderer.setShowScaleBar(showScaleBar);
            }
        } else if (n == 5) {
            fixedMaxScale = Math.max(0, ei.value);
            if (sankeyRenderer != null) {
                sankeyRenderer.setFixedMaxScale(fixedMaxScale);
            }
        } else if (n == 6) {
            useHighWaterMark = ei.checkbox.getState();
            if (sankeyRenderer != null) {
                sankeyRenderer.setUseHighWaterMark(useHighWaterMark);
            }
        } else if (n == 7) {
            showFlowLabels = ei.checkbox.getState();
            if (sankeyRenderer != null) {
                sankeyRenderer.setShowFlowLabels(showFlowLabels);
            }
        }
    }
    
    @Override
    boolean canViewInScope() {
        return false;
    }
    
    // Accessors for export
    public String getSourceTableName() {
        return sourceTableName;
    }
    
    public SankeyLayout getLayoutMode() {
        return layoutMode;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public boolean getShowScaleBar() {
        return showScaleBar;
    }
    
    public double getFixedMaxScale() {
        return fixedMaxScale;
    }
    
    public boolean getUseHighWaterMark() {
        return useHighWaterMark;
    }
    
    public boolean getShowFlowLabels() {
        return showFlowLabels;
    }
}
