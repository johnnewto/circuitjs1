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
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Style.Unit;
import com.lushprojects.circuitjs1.client.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * SFCSankeyViewer - Displays a Plotly.js Sankey diagram in an internal dialog or popup window
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
 */
public class SFCSankeyViewer {
    
    private SFCTableElm table;
    private boolean showArrows = true;  // Default to showing arrow links
    private static SankeyDialog dialogInstance = null;  // Singleton for internal dialog
    
    // Sector color mapping (Plotly color palette)
    private static final Map<String, String> SECTOR_COLORS = new HashMap<>();
    static {
        SECTOR_COLORS.put("Households", "#636EFA");  // Blue
        SECTOR_COLORS.put("Firms", "#00CC96");       // Green
        SECTOR_COLORS.put("Banks", "#FFA15A");       // Orange
        SECTOR_COLORS.put("Govt", "#EF553B");        // Red
        SECTOR_COLORS.put("Government", "#EF553B");  // Red (alternate name)
        SECTOR_COLORS.put("Central Bank", "#AB63FA"); // Purple
        SECTOR_COLORS.put("Foreign", "#19D3F3");     // Cyan
    }
    private static final String DEFAULT_COLOR = "#AB63FA";  // Purple for unknown sectors
    
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
     * Set whether to show arrow links.
     */
    public void setShowArrows(boolean show) {
        this.showArrows = show;
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
        String html = generatePlotlyHTML(false);  // Full standalone HTML
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
     * Get color for a sector name.
     */
    private String getSectorColor(String sectorName) {
        String color = SECTOR_COLORS.get(sectorName);
        return color != null ? color : DEFAULT_COLOR;
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
            nodeColorsJson.append(", \"#888888\"");  // Gray for transactions
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
     * Uses an iframe to embed the Plotly chart.
     */
    static class SankeyDialog extends DialogBox {
        private Frame chartFrame;
        private SFCSankeyViewer currentViewer;
        private static final int DIALOG_WIDTH = 800;
        private static final int DIALOG_HEIGHT = 550;
        
        public SankeyDialog(SFCSankeyViewer viewer) {
            super(false, false);  // Not auto-hide, not modal
            currentViewer = viewer;
            
            String title = viewer.table.tableTitle != null ? viewer.table.tableTitle : "SFC Table";
            setText(Locale.LS("Sankey Diagram") + ": " + title);
            
            VerticalPanel vp = new VerticalPanel();
            vp.setWidth(DIALOG_WIDTH + "px");
            setWidget(vp);
            
            // Create iframe for Plotly chart
            chartFrame = new Frame();
            chartFrame.setSize(DIALOG_WIDTH + "px", (DIALOG_HEIGHT - 50) + "px");
            chartFrame.getElement().getStyle().setBorderWidth(0, Unit.PX);
            chartFrame.getElement().getStyle().setProperty("border", "1px solid #ccc");
            vp.add(chartFrame);
            
            // Content will be loaded after dialog is shown via loadContent()
            
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
        }
        
        @Override
        public void hide() {
            super.hide();
        }
        
        /**
         * Load the Plotly chart content into the iframe (initial load).
         * Must be called after dialog is attached to DOM.
         */
        public void loadContent() {
            String html = currentViewer.generatePlotlyHTML(true);  // Embedded mode
            loadIframeContent(chartFrame.getElement(), html);
        }
        
        /**
         * Update the dialog with new content from a different viewer.
         */
        public void updateContent(SFCSankeyViewer viewer) {
            currentViewer = viewer;
            String title = viewer.table.tableTitle != null ? viewer.table.tableTitle : "SFC Table";
            setText(Locale.LS("Sankey Diagram") + ": " + title);
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
    }
}
