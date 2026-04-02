/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.elements;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;
import com.lushprojects.circuitjs1.client.elements.economics.*;

import com.lushprojects.circuitjs1.client.elements.economics.TableColumn.ColumnType;
import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.TextResource;
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
import com.google.gwt.user.client.Window;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

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

    interface SankeyViewerResources extends ClientBundle {
        SankeyViewerResources INSTANCE = GWT.create(SankeyViewerResources.class);

        @Source("SankeyPlotlyEmbeddedTemplate.html")
        TextResource plotlyEmbeddedTemplate();

        @Source("SankeyPlotlyStandaloneTemplate.html")
        TextResource plotlyStandaloneTemplate();

        @Source("SankeyD3EmbeddedTemplate.html")
        TextResource d3EmbeddedTemplate();

        @Source("SankeyD3StandaloneTemplate.html")
        TextResource d3StandaloneTemplate();
    }

    @JsFunction
    private interface D3UpdateFunction {
        void update(Object data);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Document")
    private static class DocumentLike {
        @JsMethod native void open();
        @JsMethod native void write(String html);
        @JsMethod native void close();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Window")
    private static class WindowLike {
        @JsProperty(name = "document") native DocumentLike getDocument();
        @JsProperty(name = "updateD3Sankey") native D3UpdateFunction getUpdateD3Sankey();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "HTMLIFrameElement")
    private static class IframeLike {
        @JsProperty(name = "contentDocument") native DocumentLike getContentDocument();
        @JsProperty(name = "contentWindow") native WindowLike getContentWindow();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "window")
    private static class GlobalWindowLike {
        @JsMethod(name = "open") static native WindowLike open(String url, String target, String features);
    }

    @JsMethod(namespace = JsPackage.GLOBAL, name = "JSON.parse")
    private static native Object parseJson(String json);
    
    /** Chart rendering library */
    public enum ChartLibrary {
        PLOTLY("Plotly"),
        D3("D3");
        
        private final String displayName;
        
        ChartLibrary(String displayName) {
            this.displayName = displayName;
        }
        
        String getDisplayName() {
            return displayName;
        }
    }
    
    private TableElm table;
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
    
    public SFCSankeyViewer(TableElm table) {
        this.table = table;
    }
    
    /**
     * Create viewer with arrow option.
     */
    public SFCSankeyViewer(TableElm table, boolean showArrows) {
        this.table = table;
        this.showArrows = showArrows;
    }
    
    /**
     * Create viewer with arrow option and chart library.
     */
    public SFCSankeyViewer(TableElm table, boolean showArrows, ChartLibrary library) {
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
    private void setChartLibrary(ChartLibrary library) {
        this.chartLibrary = library;
    }
    
    /**
     * Get the current chart library.
     */
    private ChartLibrary getChartLibrary() {
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
    private void openExternalWindow() {
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
     * Resolve a Sankey cell value using flow-first semantics.
     * If the cell label has a published flow value, use it; otherwise
     * fall back to the table's computed cell voltage/value.
     */
    private double getSankeyCellValue(int row, int col) {
        String label = table.getCellEquation(row, col);
        if (label != null) {
            String trimmed = label.trim();
            if (!trimmed.isEmpty() && !"0".equals(trimmed)) {
                Double publishedFlow = ComputedValues.getComputedFlowValue(trimmed);
                if (publishedFlow != null) {
                    return publishedFlow.doubleValue();
                }
            }
        }
        return table.getVoltageForCell(row, col);
    }
    
    /**
     * Build the Sankey data structure from the SFC table.
     * Package-visible for use by SankeyDialog refresh.
     * 
     * Structure:
     * - Nodes: sectors (left), transactions (middle), sectors_out (right)
     * - Links: from sector→transaction (for negative values), transaction→sector_out (for positive values)
     */
    public String buildSankeyJSON() {
        ArrayList<TableColumn> columns = table.columns;
        int rows = table.rows;
        String[] rowDescriptions = table.rowDescriptions;
        
        if (columns == null || rows == 0) {
            return "{ \"nodes\": [], \"links\": [] }";
        }
        
        // Collect sector names (skip Σ/computed column)
        ArrayList<String> sectorNames = new ArrayList<>();
        for (TableColumn col : columns) {
            if (col.getType() != ColumnType.COMPUTED) {
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
                if (column.getType() == ColumnType.COMPUTED) {
                    continue;  // Skip Σ column
                }
                
                double value = getSankeyCellValue(row, col);
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
    private String generateHTML(boolean embedded) {
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
    private String generatePlotlyHTML(boolean embedded) {
        String jsonData = buildSankeyJSON();
        String title = table.tableTitle != null ? table.tableTitle : "SFC Table";
        String template = embedded
                ? SankeyViewerResources.INSTANCE.plotlyEmbeddedTemplate().getText()
                : SankeyViewerResources.INSTANCE.plotlyStandaloneTemplate().getText();
        return template
                .replace("__TITLE__", escapeHtml(title))
                .replace("__SANKEY_JSON__", jsonData)
                .replace("__SHOW_ARROWS_BOOL__", Boolean.toString(showArrows))
                .replace("__ARROW_BUTTON_TEXT__", showArrows ? "Hide Arrows" : "Show Arrows")
                .replace("__TABLE_ROWS__", Integer.toString(table.rows));
    }
    
    /**
     * Generate complete HTML document with D3.js Sankey diagram.
     * Uses d3-sankey plugin for the layout algorithm.
     * @param embedded If true, generates minimal HTML suitable for iframe embedding
     */
    private String generateD3HTML(boolean embedded) {
        String jsonData = buildSankeyJSON();
        String title = table.tableTitle != null ? table.tableTitle : "SFC Table";
        String template = embedded
                ? SankeyViewerResources.INSTANCE.d3EmbeddedTemplate().getText()
                : SankeyViewerResources.INSTANCE.d3StandaloneTemplate().getText();
        return template
                .replace("__TITLE__", escapeHtml(title))
                .replace("__SANKEY_JSON__", jsonData)
                .replace("__TABLE_ROWS__", Integer.toString(table.rows));
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
    
    /**
     * Opens a new window with the given HTML content.
     * Uses native JavaScript to open window and write content.
     * @return true if window opened successfully, false if blocked
     */
    private boolean openWindowWithHTML(String html) {
        WindowLike newWindow = GlobalWindowLike.open("", "_blank", "width=1400,height=900");
        if (newWindow == null) {
            Window.alert("Please allow pop-ups for this site to view the Sankey diagram.");
            return false;
        }
        newWindow.getDocument().write(html);
        newWindow.getDocument().close();
        return true;
    }
    
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
        
        SankeyDialog(SFCSankeyViewer viewer) {
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
        void loadContent() {
            String html = currentViewer.generateHTML(true);  // Embedded mode
            loadIframeContent(chartFrame.getElement(), html);
        }
        
        /**
         * Refresh D3 chart with updated data (real-time update).
         * Only works for D3 library.
         */
        void refreshD3Content() {
            String jsonData = currentViewer.buildSankeyJSON();
            updateD3Chart(chartFrame.getElement(), jsonData);
        }
        
        /**
         * Update the dialog with new content from a different viewer.
         */
        void updateContent(SFCSankeyViewer viewer) {
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
        private void loadIframeContent(com.google.gwt.dom.client.Element iframe, String html) {
            IframeLike frame = (IframeLike) (Object) iframe;
            DocumentLike doc = frame.getContentDocument();
            if (doc == null && frame.getContentWindow() != null)
                doc = frame.getContentWindow().getDocument();
            if (doc == null)
                return;
            doc.open();
            doc.write(html);
            doc.close();
        }
        
        /**
         * Native method to update D3 chart data via iframe's updateD3Sankey function.
         */
        private void updateD3Chart(com.google.gwt.dom.client.Element iframe, String jsonData) {
            IframeLike frame = (IframeLike) (Object) iframe;
            WindowLike win = frame.getContentWindow();
            if (win == null)
                return;
            D3UpdateFunction update = win.getUpdateD3Sankey();
            if (update == null)
                return;
            update.update(parseJson(jsonData));
        }
    }
}
