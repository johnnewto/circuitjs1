/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.TableColumn.ColumnType;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.dom.client.Style.Unit;
import com.lushprojects.circuitjs1.client.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * SFCSankeyViewer - Displays a Sankey diagram using Plotly.js or D3.js
 * 
 * Visualizes money flows between sectors in an SFC Transaction table.
 * Uses sfcr convention:
 * - Negative cell values = outflows (sector is source)
 * - Positive cell values = inflows (sector is target)
 * 
 * Layout:
 * - Left side: Source sectors (those with outflows)
 * - Middle: Transaction types (Consumption, Wages, etc.)
 * - Right side: Target sectors (those with inflows)
 * 
 * Flow bands are colored by source sector and sized by absolute value.
 * Uses gradient coloring from source to target node colors.
 * 
 * Supports two rendering backends:
 * - Plotly.js: Interactive with hover tooltips, download options
 * - D3.js: Lightweight, real-time updates, gradient link coloring
 * 
 * TODO: Future enhancement - Net Worth visualization
 * - Scale the WIDTH of sector endpoint nodes proportionally to accumulated stock value
 * - Wider bars = more wealth accumulated in that sector
 * - Would require getting integrated stock values from associated GodlyTableElm
 * - Pass nodeWidths[] array in JSON alongside nodeColors[]
 * - Modify D3 node.append('rect').attr('width', d => scaleWidth(d.stockValue))
 */
public class SFCSankeyViewer {
    
    /** Chart rendering library */
    public enum ChartLibrary {
        PLOTLY("Plotly"),
        D3("D3");
        
        private final String displayName;
        
        ChartLibrary(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private SFCTableElm table;
    private boolean showArrows = true;  // Default to showing arrow links
    private ChartLibrary chartLibrary = ChartLibrary.D3;  // Default to D3 (supports real-time updates)
    private static SankeyDialog dialogInstance = null;  // Singleton for internal dialog
    
    // D3 Category10 color palette (matches Observable D3 Sankey examples)
    private static final String[] CATEGORY10_COLORS = {
        "#1f77b4",  // Blue
        "#ff7f0e",  // Orange  
        "#2ca02c",  // Green
        "#d62728",  // Red
        "#9467bd",  // Purple
        "#8c564b",  // Brown
        "#e377c2",  // Pink
        "#7f7f7f",  // Gray
        "#bcbd22",  // Olive
        "#17becf"   // Cyan
    };
    private static final String TRANSACTION_COLOR = "#a0a0a0";  // Gray for transaction nodes
    private static final String DEFAULT_COLOR = "#1f77b4";  // Default blue
    
    // Sector to color index mapping (for consistent colors)
    private static final Map<String, Integer> SECTOR_COLOR_INDEX = new HashMap<>();
    private static int nextColorIndex = 0;
    
    public SFCSankeyViewer(SFCTableElm table) {
        this.table = table;
    }
    
    /**
     * Create viewer with arrow option.
     */
    public SFCSankeyViewer(SFCTableElm table, boolean showArrows) {
        this.table = table;
        this.showArrows = showArrows;
    }
    
    /**
     * Create viewer with arrow option and chart library.
     */
    public SFCSankeyViewer(SFCTableElm table, boolean showArrows, ChartLibrary library) {
        this.table = table;
        this.showArrows = showArrows;
        this.chartLibrary = library;
    }
    
    /**
     * Set whether to show arrow links (Plotly only).
     */
    public void setShowArrows(boolean show) {
        this.showArrows = show;
    }
    
    /**
     * Set the chart library to use.
     */
    public void setChartLibrary(ChartLibrary library) {
        this.chartLibrary = library;
    }
    
    /**
     * Get the current chart library.
     */
    public ChartLibrary getChartLibrary() {
        return chartLibrary;
    }
    
    /**
     * Open the Sankey diagram in an internal dialog window.
     * This is the default method - shows diagram inside the app.
     */
    public void openDialog() {
        if (dialogInstance == null) {
            dialogInstance = new SankeyDialog(this);
        } else {
            dialogInstance.updateContent(this);
        }
        dialogInstance.show();
        dialogInstance.center();
        // Defer content loading until after dialog is attached to DOM
        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            @Override
            public void execute() {
                dialogInstance.loadContent();
            }
        });
    }
    
    /**
     * Open the Sankey diagram in a new external browser window.
     */
    public void openExternalWindow() {
        String html = generateHTML(false);  // Full standalone HTML
        if (!openWindowWithHTML(html)) {
            CirSim.console("Sankey viewer popup was blocked. Please allow popups for this site.");
        }
    }
    
    /**
     * Open the Sankey diagram - defaults to internal dialog.
     * @deprecated Use openDialog() or openExternalWindow() instead.
     */
    public void openViewer() {
        openDialog();
    }
    
    /**
     * Get color for a sector name using D3 Category10 palette.
     * Assigns consistent colors to sectors across views.
     */
    private String getSectorColor(String sectorName) {
        if (!SECTOR_COLOR_INDEX.containsKey(sectorName)) {
            SECTOR_COLOR_INDEX.put(sectorName, nextColorIndex);
            nextColorIndex = (nextColorIndex + 1) % CATEGORY10_COLORS.length;
        }
        int idx = SECTOR_COLOR_INDEX.get(sectorName);
        return CATEGORY10_COLORS[idx];
    }
    
    /**
     * Get color for transaction nodes (gray).
     */
    private String getTransactionColor() {
        return TRANSACTION_COLOR;
    }
    
    /**
     * Build the Sankey data structure from the SFC table.
     * Package-visible for use by SankeyDialog refresh.
     * 
     * Structure:
     * - Nodes: sectors (left), transactions (middle), sectors_out (right)
     * - Links: from sector→transaction (for negative values), transaction→sector_out (for positive values)
     */
    String buildSankeyJSON() {
        ArrayList<TableColumn> columns = table.columns;
        int rows = table.rows;
        String[] rowDescriptions = table.rowDescriptions;
        
        if (columns == null || rows == 0) {
            return "{ \"nodes\": [], \"links\": [] }";
        }
        
        // Collect sector names (skip Σ column)
        ArrayList<String> sectorNames = new ArrayList<>();
        for (TableColumn col : columns) {
            if (col.getType() == ColumnType.SECTOR) {
                sectorNames.add(col.getStockName());
            }
        }
        
        // Build node labels:
        // Index 0 to n-1: sectors (left side - sources)
        // Index n to n+rows-1: transactions (middle)
        // Index n+rows to 2n+rows-1: sectors_out (right side - targets)
        
        int numSectors = sectorNames.size();
        int numTransactions = rows;
        
        StringBuilder nodesJson = new StringBuilder();
        StringBuilder nodeColorsJson = new StringBuilder();
        
        nodesJson.append("[");
        nodeColorsJson.append("[");
        
        // Add left-side sector nodes (sources)
        for (int i = 0; i < numSectors; i++) {
            if (i > 0) {
                nodesJson.append(", ");
                nodeColorsJson.append(", ");
            }
            String name = sectorNames.get(i);
            nodesJson.append("\"").append(escapeJSON(name)).append("\"");
            nodeColorsJson.append("\"").append(getSectorColor(name)).append("\"");
        }
        
        // Add transaction nodes (middle)
        for (int i = 0; i < numTransactions; i++) {
            nodesJson.append(", \"").append(escapeJSON(rowDescriptions[i])).append("\"");
            nodeColorsJson.append(", \"").append(getTransactionColor()).append("\"");
        }
        
        // Add right-side sector nodes (targets)
        for (int i = 0; i < numSectors; i++) {
            String name = sectorNames.get(i);
            nodesJson.append(", \"").append(escapeJSON(name)).append("\"");
            nodeColorsJson.append(", \"").append(getSectorColor(name)).append("\"");
        }
        
        nodesJson.append("]");
        nodeColorsJson.append("]");
        
        // Build links from cell values
        // Negative value in (row, col) = outflow from sector[col] to transaction[row]
        // Positive value in (row, col) = inflow from transaction[row] to sector[col]_out
        
        StringBuilder sourcesJson = new StringBuilder("[");
        StringBuilder targetsJson = new StringBuilder("[");
        StringBuilder valuesJson = new StringBuilder("[");
        StringBuilder linkColorsJson = new StringBuilder("[");
        StringBuilder linkLabelsJson = new StringBuilder("[");
        
        boolean firstLink = true;
        
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns.size(); col++) {
                TableColumn column = columns.get(col);
                if (column.getType() != ColumnType.SECTOR) {
                    continue;  // Skip Σ column
                }
                
                double value = table.getVoltageForCell(row, col);
                if (Math.abs(value) < 1e-10) {
                    continue;  // Skip zero values
                }
                
                // Find sector index in our node list
                int sectorIdx = sectorNames.indexOf(column.getStockName());
                if (sectorIdx < 0) continue;
                
                int transactionIdx = numSectors + row;
                int sectorOutIdx = numSectors + numTransactions + sectorIdx;
                
                if (!firstLink) {
                    sourcesJson.append(", ");
                    targetsJson.append(", ");
                    valuesJson.append(", ");
                    linkColorsJson.append(", ");
                    linkLabelsJson.append(", ");
                }
                firstLink = false;
                
                String sectorName = column.getStockName();
                String transactionName = rowDescriptions[row];
                String sectorColor = getSectorColor(sectorName);
                // Make link color semi-transparent
                String linkColor = sectorColor.replace("#", "rgba(") + "0.5)";
                // Convert hex to rgba
                linkColor = hexToRgba(sectorColor, 0.5);
                
                if (value < 0) {
                    // Outflow: sector → transaction
                    sourcesJson.append(sectorIdx);
                    targetsJson.append(transactionIdx);
                    valuesJson.append(Math.abs(value));
                    linkLabelsJson.append("\"").append(escapeJSON(sectorName + " → " + transactionName + ": " + formatValue(value))).append("\"");
                } else {
                    // Inflow: transaction → sector_out
                    sourcesJson.append(transactionIdx);
                    targetsJson.append(sectorOutIdx);
                    valuesJson.append(value);
                    linkLabelsJson.append("\"").append(escapeJSON(transactionName + " → " + sectorName + ": " + formatValue(value))).append("\"");
                }
                linkColorsJson.append("\"").append(linkColor).append("\"");
            }
        }
        
        sourcesJson.append("]");
        targetsJson.append("]");
        valuesJson.append("]");
        linkColorsJson.append("]");
        linkLabelsJson.append("]");
        
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"nodeLabels\": ").append(nodesJson).append(",\n");
        json.append("  \"nodeColors\": ").append(nodeColorsJson).append(",\n");
        json.append("  \"linkSources\": ").append(sourcesJson).append(",\n");
        json.append("  \"linkTargets\": ").append(targetsJson).append(",\n");
        json.append("  \"linkValues\": ").append(valuesJson).append(",\n");
        json.append("  \"linkColors\": ").append(linkColorsJson).append(",\n");
        json.append("  \"linkLabels\": ").append(linkLabelsJson).append("\n");
        json.append("}");
        
        return json.toString();
    }
    
    /**
     * Convert hex color to rgba string.
     */
    private String hexToRgba(String hex, double alpha) {
        if (hex == null || hex.length() < 7) return "rgba(128,128,128," + alpha + ")";
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            return "rgba(" + r + "," + g + "," + b + "," + alpha + ")";
        } catch (Exception e) {
            return "rgba(128,128,128," + alpha + ")";
        }
    }
    
    /**
     * Format a value for display.
     */
    private String formatValue(double value) {
        if (Math.abs(value) >= 1000) {
            return CircuitElm.showFormat.format(value);
        }
        return CircuitElm.showFormat.format(value);
    }
    
    /**
     * Escape a string for JSON.
     */
    private String escapeJSON(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    /**
     * Generate HTML for the current chart library.
     * @param embedded If true, generates minimal HTML suitable for iframe embedding
     */
    String generateHTML(boolean embedded) {
        switch (chartLibrary) {
            case D3:
                return generateD3HTML(embedded);
            case PLOTLY:
            default:
                return generatePlotlyHTML(embedded);
        }
    }
    
    /**
     * Generate complete HTML document with Plotly.js Sankey diagram.
     * @param embedded If true, generates minimal HTML suitable for iframe embedding
     */
    String generatePlotlyHTML(boolean embedded) {
        String jsonData = buildSankeyJSON();
        String title = table.tableTitle != null ? table.tableTitle : "SFC Table";
        
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>Sankey Diagram - ").append(escapeJSON(title)).append("</title>\n");
        html.append("  <script src=\"https://cdn.plot.ly/plotly-2.27.0.min.js\"></script>\n");
        html.append("  <style>\n");
        
        if (embedded) {
            // Minimal styling for embedded iframe
            html.append("    body { font-family: Arial, sans-serif; margin: 0; padding: 0; background: white; }\n");
            html.append("    #sankey-chart { width: 100%; height: 100%; position: absolute; top: 0; left: 0; }\n");
            html.append("  </style>\n");
            html.append("</head>\n");
            html.append("<body>\n");
            html.append("  <div id=\"sankey-chart\"></div>\n");
        } else {
            // Full standalone styling
            html.append("    body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }\n");
            html.append("    .container { max-width: 1400px; margin: 0 auto; }\n");
            html.append("    h1 { color: #333; margin-bottom: 5px; }\n");
            html.append("    .subtitle { color: #666; margin-bottom: 20px; font-size: 14px; }\n");
            html.append("    .chart-container { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
            html.append("    #sankey-chart { width: 100%; height: 700px; }\n");
            html.append("    .legend { display: flex; flex-wrap: wrap; gap: 15px; margin-top: 20px; padding: 15px; background: #fafafa; border-radius: 4px; }\n");
            html.append("    .legend-item { display: flex; align-items: center; gap: 8px; font-size: 14px; }\n");
            html.append("    .legend-color { width: 20px; height: 20px; border-radius: 4px; }\n");
            html.append("    .controls { margin-top: 20px; }\n");
            html.append("    button { background: #007bff; color: white; border: none; padding: 10px 20px; border-radius: 4px; cursor: pointer; margin-right: 10px; }\n");
            html.append("    button:hover { background: #0056b3; }\n");
            html.append("    .info { margin-top: 20px; padding: 15px; background: #e7f3ff; border-radius: 4px; font-size: 14px; color: #0066cc; }\n");
            html.append("  </style>\n");
            html.append("</head>\n");
            html.append("<body>\n");
            html.append("  <div class=\"container\">\n");
            html.append("    <h1>Sankey Diagram: ").append(escapeJSON(title)).append("</h1>\n");
            html.append("    <p class=\"subtitle\">Stock-Flow Consistent Transaction Flows</p>\n");
            html.append("    <div class=\"chart-container\">\n");
            html.append("      <div id=\"sankey-chart\"></div>\n");
            html.append("    </div>\n");
            html.append("    <div id=\"legend\" class=\"legend\"></div>\n");
            html.append("    <div class=\"controls\">\n");
            html.append("      <button id=\"arrowBtn\" onclick=\"toggleArrows()\">" + (showArrows ? "Hide Arrows" : "Show Arrows") + "</button>\n");
            html.append("      <button onclick=\"downloadAsPNG()\">Download as PNG</button>\n");
            html.append("      <button onclick=\"downloadAsSVG()\">Download as SVG</button>\n");
            html.append("    </div>\n");
            html.append("    <div class=\"info\">\n");
            html.append("      <strong>How to read this diagram:</strong> Money flows from left to right. ");
            html.append("      Sectors on the left are <em>sources</em> (outflows/payments), transactions in the middle show the flow type, ");
            html.append("      and sectors on the right are <em>targets</em> (inflows/receipts). ");
            html.append("      Band width represents the amount of the flow.\n");
            html.append("    </div>\n");
            html.append("  </div>\n");
        }
        
        // Common script section
        html.append("  <script>\n");
        html.append("    const data = ").append(jsonData).append(";\n");
        html.append("    \n");
        html.append("    // Build Plotly Sankey trace\n");
        html.append("    const trace = {\n");
        html.append("      type: 'sankey',\n");
        html.append("      orientation: 'h',\n");
        html.append("      node: {\n");
        html.append("        pad: 15,\n");
        html.append("        thickness: 30,\n");
        html.append("        line: { color: 'black', width: 0.5 },\n");
        html.append("        label: data.nodeLabels,\n");
        html.append("        color: data.nodeColors,\n");
        html.append("        hovertemplate: '%{label}<extra></extra>'\n");
        html.append("      },\n");
        html.append("      link: {\n");
        html.append("        source: data.linkSources,\n");
        html.append("        target: data.linkTargets,\n");
        html.append("        value: data.linkValues,\n");
        html.append("        color: data.linkColors,\n");
        if (showArrows) {
            html.append("        arrowlen: 15,\n");
        }
        html.append("        customdata: data.linkLabels,\n");
        html.append("        hovertemplate: '%{customdata}<extra></extra>'\n");
        html.append("      }\n");
        html.append("    };\n");
        html.append("    \n");
        html.append("    const layout = {\n");
        html.append("      title: '',\n");
        html.append("      font: { size: 12 },\n");
        html.append("      margin: { l: 20, r: 20, t: 20, b: 20 }\n");
        html.append("    };\n");
        html.append("    \n");
        html.append("    const config = {\n");
        html.append("      responsive: true,\n");
        html.append("      displayModeBar: true,\n");
        html.append("      modeBarButtonsToRemove: ['lasso2d', 'select2d']\n");
        html.append("    };\n");
        html.append("    \n");
        html.append("    Plotly.newPlot('sankey-chart', [trace], layout, config);\n");
        
        if (!embedded) {
            // Legend and controls for standalone window only
            html.append("    \n");
            html.append("    // Build legend from unique sectors\n");
            html.append("    const legendDiv = document.getElementById('legend');\n");
            html.append("    const sectors = new Map();\n");
            html.append("    const numSectors = (data.nodeLabels.length - ").append(table.rows).append(") / 2;\n");
            html.append("    for (let i = 0; i < numSectors; i++) {\n");
            html.append("      sectors.set(data.nodeLabels[i], data.nodeColors[i]);\n");
            html.append("    }\n");
            html.append("    sectors.forEach((color, name) => {\n");
            html.append("      const item = document.createElement('div');\n");
            html.append("      item.className = 'legend-item';\n");
            html.append("      item.innerHTML = `<div class=\"legend-color\" style=\"background: ${color}\"></div><span>${name}</span>`;\n");
            html.append("      legendDiv.appendChild(item);\n");
            html.append("    });\n");
            html.append("    \n");
            html.append("    function downloadAsPNG() {\n");
            html.append("      Plotly.downloadImage('sankey-chart', { format: 'png', width: 1400, height: 700, filename: 'sankey-diagram' });\n");
            html.append("    }\n");
            html.append("    \n");
            html.append("    function downloadAsSVG() {\n");
            html.append("      Plotly.downloadImage('sankey-chart', { format: 'svg', width: 1400, height: 700, filename: 'sankey-diagram' });\n");
            html.append("    }\n");
            html.append("    \n");
            html.append("    let showArrows = " + showArrows + ";\n");
            html.append("    function toggleArrows() {\n");
            html.append("      showArrows = !showArrows;\n");
            html.append("      document.getElementById('arrowBtn').textContent = showArrows ? 'Hide Arrows' : 'Show Arrows';\n");
            html.append("      Plotly.restyle('sankey-chart', { 'link.arrowlen': showArrows ? 15 : 0 });\n");
            html.append("    }\n");
        }
        
        html.append("  </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    /**
     * Generate complete HTML document with D3.js Sankey diagram.
     * Uses d3-sankey plugin for the layout algorithm.
     * @param embedded If true, generates minimal HTML suitable for iframe embedding
     */
    String generateD3HTML(boolean embedded) {
        String jsonData = buildSankeyJSON();
        String title = table.tableTitle != null ? table.tableTitle : "SFC Table";
        
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>Sankey Diagram (D3) - ").append(escapeJSON(title)).append("</title>\n");
        html.append("  <script src=\"https://d3js.org/d3.v7.min.js\"></script>\n");
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/d3-sankey@0.12.3/dist/d3-sankey.min.js\"></script>\n");
        html.append("  <style>\n");
        
        if (embedded) {
            // Minimal styling for embedded iframe
            html.append("    body { font-family: Arial, sans-serif; margin: 0; padding: 0; background: white; }\n");
            html.append("    #sankey-chart { width: 100%; height: 100%; position: absolute; top: 0; left: 0; }\n");
            html.append("    .node rect { fill-opacity: 0.9; shape-rendering: crispEdges; }\n");
            html.append("    .node text { font-size: 11px; pointer-events: none; }\n");
            html.append("    .link { fill: none; stroke-opacity: 0.4; }\n");
            html.append("    .link:hover { stroke-opacity: 0.7; }\n");
            html.append("    .tooltip { position: absolute; background: rgba(0,0,0,0.8); color: white; padding: 8px 12px; border-radius: 4px; font-size: 12px; pointer-events: none; z-index: 1000; }\n");
            html.append("  </style>\n");
            html.append("</head>\n");
            html.append("<body>\n");
            html.append("  <div id=\"sankey-chart\"></div>\n");
        } else {
            // Full standalone styling
            html.append("    body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }\n");
            html.append("    .container { max-width: 1400px; margin: 0 auto; }\n");
            html.append("    h1 { color: #333; margin-bottom: 5px; }\n");
            html.append("    .subtitle { color: #666; margin-bottom: 20px; font-size: 14px; }\n");
            html.append("    .chart-container { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
            html.append("    #sankey-chart { width: 100%; height: 700px; }\n");
            html.append("    .node rect { fill-opacity: 0.9; shape-rendering: crispEdges; cursor: move; }\n");
            html.append("    .node text { font-size: 12px; pointer-events: none; }\n");
            html.append("    .link { fill: none; stroke-opacity: 0.4; }\n");
            html.append("    .link:hover { stroke-opacity: 0.7; }\n");
            html.append("    .legend { display: flex; flex-wrap: wrap; gap: 15px; margin-top: 20px; padding: 15px; background: #fafafa; border-radius: 4px; }\n");
            html.append("    .legend-item { display: flex; align-items: center; gap: 8px; font-size: 14px; }\n");
            html.append("    .legend-color { width: 20px; height: 20px; border-radius: 4px; }\n");
            html.append("    .controls { margin-top: 20px; }\n");
            html.append("    button { background: #007bff; color: white; border: none; padding: 10px 20px; border-radius: 4px; cursor: pointer; margin-right: 10px; }\n");
            html.append("    button:hover { background: #0056b3; }\n");
            html.append("    .info { margin-top: 20px; padding: 15px; background: #e7f3ff; border-radius: 4px; font-size: 14px; color: #0066cc; }\n");
            html.append("    .tooltip { position: absolute; background: rgba(0,0,0,0.8); color: white; padding: 8px 12px; border-radius: 4px; font-size: 12px; pointer-events: none; z-index: 1000; }\n");
            html.append("  </style>\n");
            html.append("</head>\n");
            html.append("<body>\n");
            html.append("  <div class=\"container\">\n");
            html.append("    <h1>Sankey Diagram (D3): ").append(escapeJSON(title)).append("</h1>\n");
            html.append("    <p class=\"subtitle\">Stock-Flow Consistent Transaction Flows</p>\n");
            html.append("    <div class=\"chart-container\">\n");
            html.append("      <div id=\"sankey-chart\"></div>\n");
            html.append("    </div>\n");
            html.append("    <div id=\"legend\" class=\"legend\"></div>\n");
            html.append("    <div class=\"controls\">\n");
            html.append("      <button onclick=\"downloadAsSVG()\">Download as SVG</button>\n");
            html.append("    </div>\n");
            html.append("    <div class=\"info\">\n");
            html.append("      <strong>How to read this diagram:</strong> Money flows from left to right. ");
            html.append("      Sectors on the left are <em>sources</em> (outflows/payments), transactions in the middle show the flow type, ");
            html.append("      and sectors on the right are <em>targets</em> (inflows/receipts). ");
            html.append("      Band width represents the amount of the flow.\n");
            html.append("    </div>\n");
            html.append("  </div>\n");
        }
        
        // D3 Sankey script
        html.append("  <script>\n");
        html.append("    const d3RawData = ").append(jsonData).append(";\n");
        html.append("    \n");
        html.append("    // Convert our data format to D3 Sankey format\n");
        html.append("    const d3Nodes = d3RawData.nodeLabels.map((name, i) => ({\n");
        html.append("      name: name,\n");
        html.append("      color: d3RawData.nodeColors[i]\n");
        html.append("    }));\n");
        html.append("    \n");
        html.append("    // Filter out invalid links (zero, NaN, or negative values)\n");
        html.append("    const d3Links = d3RawData.linkSources.map((source, i) => ({\n");
        html.append("      source: source,\n");
        html.append("      target: d3RawData.linkTargets[i],\n");
        html.append("      value: Math.max(0.001, d3RawData.linkValues[i] || 0),\n");  // Minimum value to avoid NaN
        html.append("      color: d3RawData.linkColors[i],\n");
        html.append("      label: d3RawData.linkLabels[i]\n");
        html.append("    })).filter(l => l.value > 0 && !isNaN(l.value) && isFinite(l.value));\n");
        html.append("    \n");
        html.append("    // Check if we have valid data\n");
        html.append("    if (d3Links.length === 0) {\n");
        html.append("      document.getElementById('sankey-chart').innerHTML = '<p style=\"text-align:center;color:#666;padding:50px;\">No flow data yet. Run simulation to see flows.</p>';\n");
        html.append("    } else {\n");
        html.append("    \n");
        html.append("    const d3Data = { nodes: d3Nodes, links: d3Links };\n");
        html.append("    \n");
        html.append("    // Get chart dimensions\n");
        html.append("    const container = document.getElementById('sankey-chart');\n");
        html.append("    const width = container.clientWidth || 800;\n");
        html.append("    const height = container.clientHeight || ").append(embedded ? "450" : "650").append(";\n");
        html.append("    const margin = { top: 10, right: 10, bottom: 10, left: 10 };\n");
        html.append("    \n");
        html.append("    // Create SVG\n");
        html.append("    const svg = d3.select('#sankey-chart')\n");
        html.append("      .append('svg')\n");
        html.append("      .attr('width', width)\n");
        html.append("      .attr('height', height)\n");
        html.append("      .attr('id', 'sankey-svg');\n");
        html.append("    \n");
        html.append("    // Create Sankey layout\n");
        html.append("    const sankey = d3.sankey()\n");
        html.append("      .nodeWidth(20)\n");
        html.append("      .nodePadding(15)\n");
        html.append("      .extent([[margin.left, margin.top], [width - margin.right, height - margin.bottom]]);\n");
        html.append("    \n");
        html.append("    // Generate the Sankey layout\n");
        html.append("    const { nodes: sankeyNodes, links: sankeyLinks } = sankey({\n");
        html.append("      nodes: d3Data.nodes.map(d => Object.assign({}, d)),\n");
        html.append("      links: d3Data.links.map(d => Object.assign({}, d))\n");
        html.append("    });\n");
        html.append("    \n");
        
        // Add gradient definitions for links
        html.append("    // Create gradient definitions for links\n");
        html.append("    const defs = svg.append('defs');\n");
        html.append("    sankeyLinks.forEach((link, i) => {\n");
        html.append("      const gradient = defs.append('linearGradient')\n");
        html.append("        .attr('id', 'gradient-' + i)\n");
        html.append("        .attr('gradientUnits', 'userSpaceOnUse')\n");
        html.append("        .attr('x1', link.source.x1)\n");
        html.append("        .attr('x2', link.target.x0);\n");
        html.append("      gradient.append('stop')\n");
        html.append("        .attr('offset', '0%')\n");
        html.append("        .attr('stop-color', link.source.color);\n");
        html.append("      gradient.append('stop')\n");
        html.append("        .attr('offset', '100%')\n");
        html.append("        .attr('stop-color', link.target.color);\n");
        html.append("    });\n");
        html.append("    \n");
        
        // Add tooltip for both embedded and standalone
        html.append("    // Create tooltip\n");
        html.append("    const tooltip = d3.select('body').append('div')\n");
        html.append("      .attr('class', 'tooltip')\n");
        html.append("      .style('opacity', 0);\n");
        html.append("    \n");
        
        html.append("    // Draw links\n");
        html.append("    const link = svg.append('g')\n");
        html.append("      .attr('class', 'links')\n");
        html.append("      .selectAll('.link')\n");
        html.append("      .data(sankeyLinks)\n");
        html.append("      .enter()\n");
        html.append("      .append('path')\n");
        html.append("      .attr('class', 'link')\n");
        html.append("      .attr('d', d3.sankeyLinkHorizontal())\n");
        html.append("      .attr('stroke', (d, i) => 'url(#gradient-' + i + ')')\n");
        html.append("      .attr('stroke-width', d => Math.max(1, d.width))\n");
        html.append("      .on('mouseover', function(event, d, i) {\n");
        html.append("        window.hoveredLinkIndex = Array.from(svg.selectAll('.link').nodes()).indexOf(this);\n");
        html.append("        d3.select(this).style('stroke-opacity', 0.7);\n");
        html.append("        tooltip.transition().duration(200).style('opacity', 0.9);\n");
        html.append("        tooltip.html(d.label)\n");
        html.append("          .style('left', (event.pageX + 10) + 'px')\n");
        html.append("          .style('top', (event.pageY - 10) + 'px');\n");
        html.append("      })\n");
        html.append("      .on('mousemove', function(event) {\n");
        html.append("        tooltip.style('left', (event.pageX + 10) + 'px')\n");
        html.append("          .style('top', (event.pageY - 10) + 'px');\n");
        html.append("      })\n");
        html.append("      .on('mouseout', function() {\n");
        html.append("        window.hoveredLinkIndex = -1;\n");
        html.append("        d3.select(this).style('stroke-opacity', 0.4);\n");
        html.append("        tooltip.transition().duration(500).style('opacity', 0);\n");
        html.append("      });\n");
        html.append("    \n");
        html.append("    // Track hovered link for real-time tooltip updates\n");
        html.append("    window.hoveredLinkIndex = -1;\n");
        html.append("    \n");
        
        html.append("    // Draw nodes\n");
        html.append("    const node = svg.append('g')\n");
        html.append("      .attr('class', 'nodes')\n");
        html.append("      .selectAll('.node')\n");
        html.append("      .data(sankeyNodes)\n");
        html.append("      .enter()\n");
        html.append("      .append('g')\n");
        html.append("      .attr('class', 'node');\n");
        html.append("    \n");
        html.append("    // Node rectangles\n");
        html.append("    node.append('rect')\n");
        html.append("      .attr('x', d => d.x0)\n");
        html.append("      .attr('y', d => d.y0)\n");
        html.append("      .attr('height', d => d.y1 - d.y0)\n");
        html.append("      .attr('width', d => d.x1 - d.x0)\n");
        html.append("      .attr('fill', d => d.color)\n");
        html.append("      .attr('stroke', '#000');\n");
        html.append("    \n");
        html.append("    // Node labels\n");
        html.append("    node.append('text')\n");
        html.append("      .attr('x', d => d.x0 < width / 2 ? d.x1 + 6 : d.x0 - 6)\n");
        html.append("      .attr('y', d => (d.y1 + d.y0) / 2)\n");
        html.append("      .attr('dy', '0.35em')\n");
        html.append("      .attr('text-anchor', d => d.x0 < width / 2 ? 'start' : 'end')\n");
        html.append("      .text(d => d.name);\n");
        
        if (!embedded) {
            // Legend and download for standalone
            html.append("    \n");
            html.append("    // Build legend from unique sectors\n");
            html.append("    const legendDiv = document.getElementById('legend');\n");
            html.append("    const numSectors = (d3RawData.nodeLabels.length - ").append(table.rows).append(") / 2;\n");
            html.append("    const sectors = new Map();\n");
            html.append("    for (let i = 0; i < numSectors; i++) {\n");
            html.append("      sectors.set(d3RawData.nodeLabels[i], d3RawData.nodeColors[i]);\n");
            html.append("    }\n");
            html.append("    sectors.forEach((color, name) => {\n");
            html.append("      const item = document.createElement('div');\n");
            html.append("      item.className = 'legend-item';\n");
            html.append("      item.innerHTML = `<div class=\"legend-color\" style=\"background: ${color}\"></div><span>${name}</span>`;\n");
            html.append("      legendDiv.appendChild(item);\n");
            html.append("    });\n");
            html.append("    \n");
            html.append("    function downloadAsSVG() {\n");
            html.append("      const svgElement = document.getElementById('sankey-svg');\n");
            html.append("      const serializer = new XMLSerializer();\n");
            html.append("      const svgString = serializer.serializeToString(svgElement);\n");
            html.append("      const blob = new Blob([svgString], { type: 'image/svg+xml' });\n");
            html.append("      const link = document.createElement('a');\n");
            html.append("      link.href = URL.createObjectURL(blob);\n");
            html.append("      link.download = 'sankey-diagram.svg';\n");
            html.append("      link.click();\n");
            html.append("    }\n");
        }
        
        // Add update function for real-time refresh (D3 version)
        html.append("    \n");
        html.append("    // Function to update chart with new data (called from parent window)\n");
        html.append("    window.updateD3Sankey = function(newData) {\n");
        html.append("      // Convert new data format to D3 Sankey format\n");
        html.append("      const newNodes = newData.nodeLabels.map((name, i) => ({\n");
        html.append("        name: name,\n");
        html.append("        color: newData.nodeColors[i]\n");
        html.append("      }));\n");
        html.append("      const newLinks = newData.linkSources.map((source, i) => ({\n");
        html.append("        source: source,\n");
        html.append("        target: newData.linkTargets[i],\n");
        html.append("        value: Math.max(0.001, newData.linkValues[i] || 0),\n");
        html.append("        color: newData.linkColors[i],\n");
        html.append("        label: newData.linkLabels[i]\n");
        html.append("      })).filter(l => l.value > 0 && !isNaN(l.value) && isFinite(l.value));\n");
        html.append("      \n");
        html.append("      // Skip update if no valid links\n");
        html.append("      if (newLinks.length === 0) return;\n");
        html.append("      \n");
        html.append("      // Re-run sankey layout with new data\n");
        html.append("      try {\n");
        html.append("        const newSankeyData = sankey({\n");
        html.append("          nodes: newNodes.map(d => Object.assign({}, d)),\n");
        html.append("          links: newLinks.map(d => Object.assign({}, d))\n");
        html.append("        });\n");
        html.append("        \n");
        html.append("        // Update gradient definitions\n");
        html.append("        newSankeyData.links.forEach((link, i) => {\n");
        html.append("          const gradient = svg.select('defs').select('#gradient-' + i);\n");
        html.append("          if (!gradient.empty()) {\n");
        html.append("            gradient.attr('x1', link.source.x1).attr('x2', link.target.x0);\n");
        html.append("            gradient.select('stop:first-child').attr('stop-color', link.source.color);\n");
        html.append("            gradient.select('stop:last-child').attr('stop-color', link.target.color);\n");
        html.append("          }\n");
        html.append("        });\n");
        html.append("        \n");
        html.append("        // Update links with transition\n");
        html.append("        const linkSelection = svg.selectAll('.link').data(newSankeyData.links);\n");
        html.append("        linkSelection\n");
        html.append("          .transition()\n");
        html.append("          .duration(300)\n");
        html.append("          .attr('d', d3.sankeyLinkHorizontal())\n");
        html.append("          .attr('stroke-width', d => Math.max(1, d.width));\n");
        html.append("        \n");
        html.append("        // Update tooltip if hovering over a link\n");
        html.append("        if (window.hoveredLinkIndex >= 0 && window.hoveredLinkIndex < newSankeyData.links.length) {\n");
        html.append("          const hoveredLink = newSankeyData.links[window.hoveredLinkIndex];\n");
        html.append("          tooltip.html(hoveredLink.label);\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        // Update nodes with transition\n");
        html.append("        const nodeGroups = svg.selectAll('.node').data(newSankeyData.nodes);\n");
        html.append("        nodeGroups.select('rect')\n");
        html.append("          .transition()\n");
        html.append("          .duration(300)\n");
        html.append("          .attr('y', d => d.y0)\n");
        html.append("          .attr('height', d => d.y1 - d.y0);\n");
        html.append("        nodeGroups.select('text')\n");
        html.append("          .transition()\n");
        html.append("          .duration(300)\n");
        html.append("          .attr('y', d => (d.y1 + d.y0) / 2);\n");
        html.append("      } catch(e) {\n");
        html.append("        // Silently ignore update errors\n");
        html.append("      }\n");
        html.append("    };\n");
        html.append("    } // End of if (d3Links.length > 0)\n");
        
        html.append("  </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    /**
     * Opens a new window with the given HTML content.
     * Uses native JavaScript to open window and write content.
     * @return true if window opened successfully, false if blocked
     */
    private native boolean openWindowWithHTML(String html) /*-{
        var newWindow = $wnd.open('', '_blank', 'width=1400,height=900');
        if (newWindow) {
            newWindow.document.write(html);
            newWindow.document.close();
            return true;
        } else {
            $wnd.alert('Please allow pop-ups for this site to view the Sankey diagram.');
            return false;
        }
    }-*/;
    
    /**
     * Internal dialog for displaying the Sankey diagram.
     * Uses an iframe to embed the chart (Plotly or D3).
     * D3 supports real-time updates via timer.
     */
    static class SankeyDialog extends DialogBox {
        private Frame chartFrame;
        private SFCSankeyViewer currentViewer;
        private ListBox librarySelector;
        private Timer refreshTimer;
        private static final int DIALOG_WIDTH = 800;
        private static final int DIALOG_HEIGHT = 550;
        private static final int REFRESH_INTERVAL_MS = 500;  // 0.5 second for smoother updates
        
        public SankeyDialog(SFCSankeyViewer viewer) {
            super(false, false);  // Not auto-hide, not modal
            currentViewer = viewer;
            
            // Create timer for D3 auto-refresh
            refreshTimer = new Timer() {
                @Override
                public void run() {
                    if (isShowing() && currentViewer.getChartLibrary() == ChartLibrary.D3) {
                        refreshD3Content();
                    }
                }
            };
            
            String title = viewer.table.tableTitle != null ? viewer.table.tableTitle : "SFC Table";
            setText(Locale.LS("Sankey Diagram") + ": " + title);
            
            VerticalPanel vp = new VerticalPanel();
            vp.setWidth(DIALOG_WIDTH + "px");
            setWidget(vp);
            
            // Create iframe for chart
            chartFrame = new Frame();
            chartFrame.setSize(DIALOG_WIDTH + "px", (DIALOG_HEIGHT - 80) + "px");
            chartFrame.getElement().getStyle().setBorderWidth(0, Unit.PX);
            chartFrame.getElement().getStyle().setProperty("border", "1px solid #ccc");
            vp.add(chartFrame);
            
            // Content will be loaded after dialog is shown via loadContent()
            
            // Top control panel with library selector
            HorizontalPanel controlPanel = new HorizontalPanel();
            controlPanel.setWidth("100%");
            controlPanel.getElement().getStyle().setMarginTop(10, Unit.PX);
            controlPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
            vp.add(controlPanel);
            
            // Library selector
            controlPanel.add(new com.google.gwt.user.client.ui.Label(Locale.LS("Library") + ": "));
            librarySelector = new ListBox();
            for (ChartLibrary lib : ChartLibrary.values()) {
                librarySelector.addItem(lib.getDisplayName());
            }
            librarySelector.setSelectedIndex(viewer.chartLibrary.ordinal());
            librarySelector.addChangeHandler(new ChangeHandler() {
                @Override
                public void onChange(ChangeEvent event) {
                    int idx = librarySelector.getSelectedIndex();
                    currentViewer.setChartLibrary(ChartLibrary.values()[idx]);
                    loadContent();  // Reload with new library
                }
            });
            controlPanel.add(librarySelector);
            
            // Bottom button panel
            HorizontalPanel hp = new HorizontalPanel();
            hp.setWidth("100%");
            hp.getElement().getStyle().setMarginTop(10, Unit.PX);
            hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
            vp.add(hp);
            
            // Open in external window button
            Button externalBtn = new Button(Locale.LS("Open in Window"));
            externalBtn.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    currentViewer.openExternalWindow();
                }
            });
            hp.add(externalBtn);
            
            // Spacer
            HorizontalPanel spacer = new HorizontalPanel();
            spacer.setWidth("100%");
            hp.add(spacer);
            hp.setCellWidth(spacer, "100%");
            
            // Close button
            hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
            Button closeBtn = new Button(Locale.LS("Close"));
            closeBtn.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    hide();
                }
            });
            hp.add(closeBtn);
        }
        
        @Override
        public void show() {
            super.show();
            // Start timer for D3 real-time updates
            refreshTimer.scheduleRepeating(REFRESH_INTERVAL_MS);
        }
        
        @Override
        public void hide() {
            // Stop timer when hidden
            refreshTimer.cancel();
            super.hide();
        }
        
        /**
         * Load the chart content into the iframe (initial load).
         * Uses the current viewer's chart library setting.
         * Must be called after dialog is attached to DOM.
         */
        public void loadContent() {
            String html = currentViewer.generateHTML(true);  // Embedded mode
            loadIframeContent(chartFrame.getElement(), html);
        }
        
        /**
         * Refresh D3 chart with updated data (real-time update).
         * Only works for D3 library.
         */
        public void refreshD3Content() {
            String jsonData = currentViewer.buildSankeyJSON();
            updateD3Chart(chartFrame.getElement(), jsonData);
        }
        
        /**
         * Update the dialog with new content from a different viewer.
         */
        public void updateContent(SFCSankeyViewer viewer) {
            currentViewer = viewer;
            String title = viewer.table.tableTitle != null ? viewer.table.tableTitle : "SFC Table";
            setText(Locale.LS("Sankey Diagram") + ": " + title);
            // Update library selector to match viewer
            librarySelector.setSelectedIndex(viewer.chartLibrary.ordinal());
            // Content will be loaded by openDialog() after this
        }
        
        /**
         * Native method to write HTML content to an iframe (initial load).
         */
        private native void loadIframeContent(com.google.gwt.dom.client.Element iframe, String html) /*-{
            var doc = iframe.contentDocument || iframe.contentWindow.document;
            doc.open();
            doc.write(html);
            doc.close();
        }-*/;
        
        /**
         * Native method to update D3 chart data via iframe's updateD3Sankey function.
         */
        private native void updateD3Chart(com.google.gwt.dom.client.Element iframe, String jsonData) /*-{
            var win = iframe.contentWindow;
            if (win && win.updateD3Sankey) {
                var data = JSON.parse(jsonData);
                win.updateD3Sankey(data);
            }
        }-*/;
    }
}
