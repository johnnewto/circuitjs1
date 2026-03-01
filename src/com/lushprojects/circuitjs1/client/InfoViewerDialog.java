/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.lushprojects.circuitjs1.client.util.Locale;

/**
 * InfoViewerDialog - Displays markdown-formatted info/documentation
 * 
 * Renders markdown content (from @info blocks in SFCR files) as HTML
 * using the marked.js library loaded from CDN.
 * 
 * Supports two display modes:
 * 1. Embedded dialog (using IframeViewerDialog with data URL)
 * 2. New window popup (for larger documentation)
 * 
 * Markdown features supported (via marked.js):
 * - Headers (#, ##, ###)
 * - Bold (**text**) and Italic (*text*)
 * - Lists (- item or 1. item)
 * - Code blocks (``` code ```)
 * - Links [text](url)
 * - Tables (| col | col |)
 * 
 * Usage:
 *   InfoViewerDialog.showInfo("Model Documentation", markdownText);
 *   InfoViewerDialog.showInfoInWindow("Full Docs", markdownText);
 */
public class InfoViewerDialog extends DialogBox {
    
    private static InfoViewerDialog instance = null;
    private static String currentMarkdown = null;
    private static String currentRawMarkdown = null;
    private static String currentTitle = null;
    private static boolean currentAppendCircuitTables = false;
    private static boolean appendCircuitTablesAsBlocks = true;
    private static long lastLiveUpdateMs = 0;
    private static final int DEFAULT_LIVE_UPDATE_INTERVAL_MS = 200;
    
    VerticalPanel vp;
    String markdownContent;
    
    /**
     * Show info in a modal dialog (simple HTML rendering).
     * For basic display without external dependencies.
     */
    public static void showInfo(String title, String markdown) {
        if (instance != null) {
            instance.hide();
        }
        instance = new InfoViewerDialog(title, markdown);
    }
    
    /**
     * Show info in a new browser window with full markdown rendering.
     * Uses marked.js from CDN for proper markdown parsing.
     */
    public static void showInfoInWindow(String title, String markdown) {
        showInfoInWindow(title, markdown, false);
    }

    /**
     * Show info in a new browser window with optional table appendix.
     */
    public static void showInfoInWindow(String title, String markdown, boolean appendCircuitTables) {
        String rawMarkdown = (markdown == null) ? "" : markdown;
        String displayMarkdown = buildDisplayMarkdown(rawMarkdown, appendCircuitTables);

        currentTitle = title;
        currentRawMarkdown = rawMarkdown;
        currentMarkdown = displayMarkdown;
        currentAppendCircuitTables = appendCircuitTables;
        lastLiveUpdateMs = 0;

        installViewerMessageBridge();
        String html = generateMarkdownViewerHTML(title, displayMarkdown, rawMarkdown, isModelInfoTitle(title));
        openWindowWithHTML(html);
    }
    
    /**
     * Show info using IframeViewerDialog with data URL.
     * Good for embedded display with full markdown support.
     * Includes button to open in new window.
     */
    public static void showInfoInIframe(String title, String markdown) {
        showInfoInIframe(title, markdown, false);
    }
    
    /**
     * Show info using IframeViewerDialog with data URL.
     * Good for embedded display with full markdown support.
     * Includes button to open in new window.
     * 
     * @param title Dialog title
     * @param markdown Markdown content to display
     * @param appendCircuitTables If true, append circuit table data to the markdown
     */
    public static void showInfoInIframe(String title, String markdown, boolean appendCircuitTables) {
        String rawMarkdown = (markdown == null) ? "" : markdown;
        String displayMarkdown = buildDisplayMarkdown(rawMarkdown, appendCircuitTables);

        currentTitle = title;
        currentRawMarkdown = rawMarkdown;
        currentMarkdown = displayMarkdown;
        currentAppendCircuitTables = appendCircuitTables;
        lastLiveUpdateMs = 0;
        
        installViewerMessageBridge();
        String html = generateMarkdownViewerHTML(title, displayMarkdown, rawMarkdown, isModelInfoTitle(title));
        String dataUrl = createDataUrl(html);
        IframeViewerDialog.openDialog(title, dataUrl, 900, 500);
        
        // Add "Open in Window" button to the dialog title bar
        addOpenInWindowButton();
    }
    
    /**
     * Called from JavaScript to open current content in new window.
     */
    public static void openCurrentInWindow() {
        if (currentRawMarkdown != null && currentTitle != null) {
            showInfoInWindow(currentTitle, currentRawMarkdown, currentAppendCircuitTables);
        }
    }

    private static boolean isModelInfoTitle(String title) {
        if (title == null) {
            return false;
        }
        return title.toLowerCase().contains("model information");
    }

    private static String buildDisplayMarkdown(String markdown, boolean appendCircuitTables) {
        String displayMarkdown = (markdown == null) ? "" : markdown;
        if (!appendCircuitTables) {
            return displayMarkdown;
        }

        String tablesMarkdown = appendCircuitTablesAsBlocks
            ? generateCircuitTableBlocksMarkdown()
            : generateCircuitTablesMarkdown();
        if (tablesMarkdown == null || tablesMarkdown.isEmpty()) {
            return displayMarkdown;
        }
        if (!displayMarkdown.trim().isEmpty() &&
            !tablesMarkdown.equals("No circuit loaded.") &&
            !tablesMarkdown.contains("*No tables found")) {
            return displayMarkdown + "\n\n---\n\n" + tablesMarkdown;
        }
        if (displayMarkdown.trim().isEmpty()) {
            return tablesMarkdown;
        }
        return displayMarkdown;
    }

    /**
     * Controls how auto-appended table content is generated.
     * true = append fenced ```{circuit} table: ...``` blocks (live mounts)
     * false = append static "Circuit Tables Overview" snapshot markdown
     */
    public static void setAppendCircuitTablesAsBlocks(boolean enabled) {
        appendCircuitTablesAsBlocks = enabled;
    }

    public static void saveEditedMarkdown(String markdown) {
        String normalized = (markdown == null) ? "" : markdown;
        currentRawMarkdown = normalized;
        currentMarkdown = buildDisplayMarkdown(normalized, currentAppendCircuitTables);

        if (CirSim.theSim != null && isModelInfoTitle(currentTitle)) {
            CirSim.theSim.modelInfoContent = normalized;
            if (CirSim.theSim.viewModelInfoItem != null) {
                CirSim.theSim.viewModelInfoItem.setEnabled(!normalized.isEmpty());
            }
            if (CirSim.theSim.helpViewModelInfoItem != null) {
                CirSim.theSim.helpViewModelInfoItem.setEnabled(!normalized.isEmpty());
            }
        }
    }

    public static void handleSimulationCommand(String command) {
        if (CirSim.theSim == null || command == null) {
            return;
        }
        if ("run".equals(command)) {
            CirSim.theSim.setSimRunning(true);
            return;
        }
        if ("stop".equals(command)) {
            CirSim.theSim.setSimRunning(false);
            return;
        }
        if ("reset".equals(command)) {
            CirSim.theSim.resetAction();
            return;
        }
        if ("step".equals(command)) {
            CirSim.theSim.stepCircuit();
        }
    }

    public static void pushLiveDataUpdate() {
        long now = System.currentTimeMillis();
        int intervalMs = DEFAULT_LIVE_UPDATE_INTERVAL_MS;
        if (CirSim.theSim != null && CirSim.theSim.infoViewerUpdateIntervalMs > 0) {
            intervalMs = CirSim.theSim.infoViewerUpdateIntervalMs;
        }
        if (now - lastLiveUpdateMs < intervalMs) {
            return;
        }
        if (!hasActiveViewer()) {
            return;
        }

        String json = buildLiveDataJson();
        if (json == null || json.isEmpty()) {
            return;
        }

        lastLiveUpdateMs = now;
        postLiveDataToViewers(json);
    }

    private static String buildLiveDataJson() {
        CirSim sim = CirSim.theSim;
        if (sim == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"circuit-live\"");
        sb.append(",\"running\":").append(sim.simIsRunning());
        sb.append(",\"t\":").append(sim.t);
        sb.append(",\"dt\":").append(sim.timeStep);
        sb.append(",\"vars\":{");

        boolean first = true;
        first = appendLiveVar(sb, "t", sim.t, first);
        first = appendLiveVar(sb, "dt", sim.timeStep, first);

        String[] names = ComputedValues.getComputedValueNames();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            Double value = ComputedValues.getConvergedValue(name);
            if (value == null || value.isNaN() || value.isInfinite()) {
                continue;
            }
            first = appendLiveVar(sb, name, value.doubleValue(), first);
        }

        sb.append("}");
        appendLiveTablesJson(sb, sim);
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendLiveTablesJson(StringBuilder sb, CirSim sim) {
        sb.append(",\"tables\":[");
        boolean firstTable = true;

        if (sim.elmList != null) {
            for (int i = 0; i < sim.elmList.size(); i++) {
                CircuitElm elm = sim.elmList.get(i);
                if (elm instanceof TableElm) {
                    TableElm table = (TableElm) elm;
                    if (!firstTable) {
                        sb.append(',');
                    }
                    appendSingleTableSnapshot(sb, table);
                    firstTable = false;
                    continue;
                }
                if (elm instanceof EquationTableElm) {
                    EquationTableElm eqTable = (EquationTableElm) elm;
                    if (!firstTable) {
                        sb.append(',');
                    }
                    appendEquationTableSnapshot(sb, eqTable);
                    firstTable = false;
                }
            }
        }

        sb.append(']');
    }

    private static void appendEquationTableSnapshot(StringBuilder sb, EquationTableElm table) {
        sb.append('{');

        String tableName = table.getTableName();
        if (tableName == null || tableName.trim().isEmpty()) {
            tableName = "Equation Table";
        }

        int rows = Math.max(0, table.getRowCount());

        sb.append("\"type\":\"equation\"");
        sb.append(",\"name\":\"").append(escapeJson(tableName)).append("\"");

        sb.append(",\"cols\":[\"Value\"]");

        sb.append(",\"rowNames\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            String rowName = table.getOutputName(row);
            if (rowName == null || rowName.trim().isEmpty()) {
                rowName = "Row " + (row + 1);
            }
            sb.append('"').append(escapeJson(rowName)).append('"');
        }
        sb.append(']');

        sb.append(",\"values\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            double value = table.getDisplayValue(row);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                value = 0.0;
            }
            sb.append('[').append(value).append(']');
        }
        sb.append(']');

        sb.append(",\"labels\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            String equation = table.getEquation(row);
            if (equation == null) {
                equation = "";
            }
            sb.append("[\"").append(escapeJson(equation.trim())).append("\"]");
        }
        sb.append(']');

        sb.append(",\"hints\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            String outputName = table.getOutputName(row);
            String hint = HintRegistry.getHint(outputName);
            if (hint == null) {
                hint = "";
            }
            sb.append('"').append(escapeJson(hint)).append('"');
        }
        sb.append(']');

        sb.append('}');
    }

    private static void appendSingleTableSnapshot(StringBuilder sb, TableElm table) {
        sb.append('{');

        String tableName = table.getTableTitle();
        if (tableName == null || tableName.trim().isEmpty()) {
            tableName = "Table";
        }

        int rows = Math.max(0, table.getRows());
        int cols = Math.max(0, table.getCols());

        sb.append("\"type\":\"table\"");
        sb.append(",\"name\":\"").append(escapeJson(tableName)).append("\"");

        sb.append(",\"cols\":[");
        for (int col = 0; col < cols; col++) {
            if (col > 0) {
                sb.append(',');
            }
            String header = table.getColumnHeader(col);
            if (header == null || header.trim().isEmpty()) {
                header = "Col" + (col + 1);
            }
            sb.append('"').append(escapeJson(header)).append('"');
        }
        sb.append(']');

        sb.append(",\"rowNames\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            String rowName = table.getRowDescription(row);
            if (rowName == null || rowName.trim().isEmpty()) {
                rowName = "Row " + (row + 1);
            }
            sb.append('"').append(escapeJson(rowName)).append('"');
        }
        sb.append(']');

        sb.append(",\"values\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            sb.append('[');
            for (int col = 0; col < cols; col++) {
                if (col > 0) {
                    sb.append(',');
                }
                double value = 0.0;
                TableColumn column = table.getColumn(col);
                if (column != null) {
                    value = column.getCachedCellValue(row);
                    if (Double.isNaN(value) || Double.isInfinite(value)) {
                        value = 0.0;
                    }
                }
                sb.append(value);
            }
            sb.append(']');
        }
        sb.append(']');

        sb.append(",\"labels\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            sb.append('[');
            for (int col = 0; col < cols; col++) {
                if (col > 0) {
                    sb.append(',');
                }
                String label = "";
                TableColumn column = table.getColumn(col);
                if (column != null) {
                    String equation = column.getCellEquation(row);
                    if (equation != null) {
                        label = equation.trim();
                    }
                }
                sb.append('"').append(escapeJson(label)).append('"');
            }
            sb.append(']');
        }
        sb.append(']');

        sb.append('}');
    }

    private static boolean appendLiveVar(StringBuilder sb, String name, double value, boolean first) {
        if (name == null || name.isEmpty() || Double.isNaN(value) || Double.isInfinite(value)) {
            return first;
        }
        if (!first) {
            sb.append(',');
        }
        sb.append('"').append(escapeJson(name)).append('"').append(':').append(value);
        return false;
    }

    private static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static native boolean hasActiveViewer() /*-{
        var popupOpen = $wnd._modelInfoWindow && !$wnd._modelInfoWindow.closed;
        var iframe = $doc.getElementById('iframeViewerContent');
        var iframeOpen = iframe && iframe.contentWindow;
        return !!(popupOpen || iframeOpen);
    }-*/;

    private static native void postLiveDataToViewers(String jsonData) /*-{
        var payload;
        try {
            payload = JSON.parse(jsonData);
        } catch (e) {
            return;
        }

        var popup = $wnd._modelInfoWindow;
        if (popup && !popup.closed) {
            try {
                popup.postMessage(payload, '*');
            } catch (e1) {}
        }

        var iframe = $doc.getElementById('iframeViewerContent');
        if (iframe && iframe.contentWindow) {
            try {
                iframe.contentWindow.postMessage(payload, '*');
            } catch (e2) {}
        }
    }-*/;

    private static native void installViewerMessageBridge() /*-{
        if ($wnd.__circuitInfoViewerBridgeInstalled) {
            return;
        }
        $wnd.__circuitInfoViewerBridgeInstalled = true;

        $wnd.addEventListener('message', function(event) {
            var data = event.data;
            if (!data || !data.type) {
                return;
            }
            if (data.type === 'info-viewer-save-markdown') {
                @com.lushprojects.circuitjs1.client.InfoViewerDialog::saveEditedMarkdown(Ljava/lang/String;)(data.markdown || '');
                return;
            }
            if (data.type === 'info-viewer-sim-command') {
                @com.lushprojects.circuitjs1.client.InfoViewerDialog::handleSimulationCommand(Ljava/lang/String;)(data.command || '');
            }
        });
    }-*/;
    
    /**
     * Called from JavaScript to open current content in new window and close the dialog.
     */
    public static void openCurrentInWindowAndCloseDialog() {
        openCurrentInWindow();
        IframeViewerDialog.closeDialog();
    }
    
    /**
     * Add an "Open in Window" button to the IframeViewerDialog title bar.
     */
    private static native void addOpenInWindowButton() /*-{
        // Delay to ensure dialog is rendered
        setTimeout(function() {
            var dialog = $doc.getElementById('iframeViewerDialog');
            if (!dialog) return;
            
            // Find the caption/title bar element
            var caption = dialog.querySelector('.Caption');
            if (!caption) {
                caption = dialog.querySelector('td.Caption');
            }
            if (!caption) {
                var cells = dialog.querySelectorAll('td');
                for (var i = 0; i < cells.length; i++) {
                    if (cells[i].className && cells[i].className.indexOf('Caption') >= 0) {
                        caption = cells[i];
                        break;
                    }
                }
            }
            
            if (caption) {
                // Check if button already exists
                if (caption.querySelector('.open-in-window-btn')) return;
                
                // Create "Open in Window" button
                var openBtn = $doc.createElement('span');
                openBtn.className = 'open-in-window-btn';
                openBtn.innerHTML = '&#x2197;'; // Unicode arrow pointing up-right
                openBtn.style.cssText = 'cursor: pointer; font-size: 14px; font-weight: bold; ' +
                                       'padding: 2px 6px; margin-left: 10px; border-radius: 4px; ' +
                                       'color: #666; transition: all 0.2s;';
                openBtn.title = 'Open in New Window';
                
                // Hover effects
                openBtn.onmouseenter = function() {
                    this.style.background = '#4488ff';
                    this.style.color = 'white';
                };
                openBtn.onmouseleave = function() {
                    this.style.background = 'transparent';
                    this.style.color = '#666';
                };
                
                // Click handler - call Java method to open in window and close dialog
                openBtn.onclick = function(e) {
                    e.stopPropagation();
                    @com.lushprojects.circuitjs1.client.InfoViewerDialog::openCurrentInWindowAndCloseDialog()();
                };
                
                // Insert before close button (which should be last)
                var closeBtn = caption.querySelector('span:last-child');
                if (closeBtn) {
                    caption.insertBefore(openBtn, closeBtn);
                } else {
                    caption.appendChild(openBtn);
                }
            }
        }, 100);
    }-*/;
    
    /**
     * Create a data URL from HTML content.
     */
    private static native String createDataUrl(String html) /*-{
        return 'data:text/html;charset=utf-8,' + encodeURIComponent(html);
    }-*/;
    
    /**
     * Open a new window with HTML content.
     * Reuses the same window if already open to avoid multiple popups.
     * Uses blur/focus trick to bring window to front in Chrome.
     */
    private static native boolean openWindowWithHTML(String html) /*-{
        // Use a named window to reuse it if already open
        var windowName = 'circuitjs_model_info';
        
        // Open or reuse window with specific name
        var newWindow = $wnd.open('', windowName, 'width=800,height=600');
        if (newWindow) {
            $wnd._modelInfoWindow = newWindow;
            newWindow.document.open();
            newWindow.document.write(html);
            newWindow.document.close();
            
            // Blur main window then focus popup - helps in Chrome
            $wnd.blur();
            newWindow.focus();
            
            // Also try delayed focus as fallback
            setTimeout(function() {
                newWindow.focus();
            }, 100);
            
            return true;
        } else {
            alert('Please allow pop-ups for this site to view documentation.');
            return false;
        }
    }-*/;
    
    /**
     * Public access to generate markdown viewer HTML.
     * Used by other dialogs (like EquationTableMarkdownDebugDialog) that want to render
     * markdown in their own windows.
     */
    public static String generateMarkdownViewerHTMLPublic(String title, String markdown) {
        return generateMarkdownViewerHTML(title, markdown);
    }
    
    /**
     * Generate complete HTML with marked.js for rendering markdown.
     */
    private static String generateMarkdownViewerHTML(String title, String markdown) {
        return generateMarkdownViewerHTML(title, markdown, markdown, false);
    }

    private static String generateMarkdownViewerHTML(String title, String markdown, String sourceMarkdown, boolean editable) {
        StringBuilder html = new StringBuilder();
        
        // Escape the markdown for embedding in JavaScript
        String escapedMarkdown = escapeForJS(markdown);
        String escapedSourceMarkdown = escapeForJS(sourceMarkdown);
        String escapedAutoTemplateMarkdown = editable ? escapeForJS(generateAutoInfoTemplateMarkdown()) : "";
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>").append(escapeHtml(title)).append("</title>\n");
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/marked/marked.min.js\"></script>\n");
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/html2canvas@1.4.1/dist/html2canvas.min.js\"></script>\n");
        html.append("  <script src=\"https://cdn.plot.ly/plotly-2.27.0.min.js\"></script>\n");
        // Add KaTeX for math rendering
        html.append("  <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css\">\n");
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js\"></script>\n");
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js\"></script>\n");
        html.append("  <style>\n");
        html.append("    body { \n");
        html.append("      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;\n");
        html.append("      margin: 0; padding: 16px; background: #fafbfc; color: #24292f; font-size: 13px;\n");
        html.append("      line-height: 1.4; height: 100vh; overflow: hidden; box-sizing: border-box;\n");
        html.append("    }\n");
        html.append("    .container { max-width: 1100px; margin: 0 auto; background: white; padding: 20px; border-radius: 6px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); height: 100%; box-sizing: border-box; display: flex; flex-direction: column; overflow: hidden; }\n");
        html.append("    h1 { color: #24292f; border-bottom: 1px solid #d0d7de; padding-bottom: 8px; margin: 0 0 16px 0; font-size: 24px; font-weight: 600; }\n");
        html.append("    h2 { color: #24292f; margin: 24px 0 12px 0; font-size: 18px; font-weight: 600; border-bottom: 1px solid #e5e9ec; padding-bottom: 6px; }\n");
        html.append("    h3 { color: #57606a; margin: 16px 0 8px 0; font-size: 15px; font-weight: 600; }\n");
        html.append("    code { background: transparent; padding: 0; font-family: 'SF Mono', 'Consolas', 'Monaco', monospace; font-size: 12px; color: #24292f; }\n");
        html.append("    pre { background: #f6f8fa; color: #24292f; padding: 12px; border-radius: 4px; overflow-x: auto; border: 1px solid #d0d7de; }\n");
        html.append("    pre code { background: none; padding: 0; color: inherit; border: none; }\n");
        html.append("    table { border-collapse: collapse; width: 100%; margin: 10px 0; font-size: 12px; }\n");
        html.append("    th, td { border: 1px solid #d0d7de; padding: 4px 8px; text-align: left; }\n");
        html.append("    th { background: #f6f8fa; color: #24292f; font-weight: 600; }\n");
        html.append("    tr:nth-child(even) { background: #f9fafb; }\n");
        html.append("    tr:hover { background: #f3f4f6; }\n");
        html.append("    td code:not(:empty):first-child { display: block; }\n");
        html.append("    td code:not(:empty) { color: #24292f; }\n");
        html.append("    td code:not(:empty)[style*='color'] { font-weight: 500; }\n");
        html.append("    blockquote { border-left: 3px solid #58a6ff; margin: 12px 0; padding: 8px 16px; background: #f6f8fa; color: #57606a; }\n");
        html.append("    a { color: #0969da; text-decoration: none; }\n");
        html.append("    a:hover { text-decoration: underline; }\n");
        html.append("    ul, ol { padding-left: 24px; margin: 8px 0; }\n");
        html.append("    li { margin: 4px 0; }\n");
        html.append("    p { margin: 8px 0; }\n");
        html.append("    hr { border: none; border-top: 1px solid #d0d7de; margin: 24px 0; }\n");
        html.append("    .export-buttons { margin-bottom: 16px; display: flex; gap: 8px; }\n");
        html.append("    .export-btn { padding: 6px 12px; background: #2da44e; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 13px; font-weight: 500; transition: background 0.2s; }\n");
        html.append("    .export-btn:hover { background: #2c974b; }\n");
        html.append("    .export-btn:active { background: #298a41; }\n");
        html.append("    .export-btn.secondary { background: #6c757d; }\n");
        html.append("    .export-btn.secondary:hover { background: #5c636a; }\n");
        html.append("    .export-btn.secondary:active { background: #565e64; }\n");
        html.append("    .export-btn.tertiary { background: #1f6feb; }\n");
        html.append("    .export-btn.tertiary:hover { background: #1a5fc8; }\n");
        html.append("    .export-btn.tertiary:active { background: #174ea6; }\n");
        html.append("    .sim-toolbar-btn { padding: 4px 12px; font-size: 13px; font-weight: 500; border: 1px solid rgba(150, 150, 150, 0.5); border-radius: 4px; background: linear-gradient(to bottom, rgba(255,255,255,0.9) 0%, rgba(232,232,232,0.9) 100%); cursor: pointer; color: #333; transition: all 0.2s ease; white-space: nowrap; vertical-align: middle; display: inline-block; line-height: 1.2; }\n");
        html.append("    .sim-toolbar-btn:hover { background: linear-gradient(to bottom, #fff 0%, #d0d0d0 100%); border-color: #666; box-shadow: 0 1px 4px rgba(0,0,0,0.2); transform: translateY(-1px); }\n");
        html.append("    .sim-toolbar-btn:active { background: linear-gradient(to bottom, #d0d0d0 0%, #e8e8e8 100%); box-shadow: inset 0 1px 2px rgba(0,0,0,0.2); transform: translateY(0); }\n");
        html.append("    .sim-toolbar-btn-stopped { background: linear-gradient(to bottom, #ff6b6b 0%, #ee5a5a 100%); border-color: #d32f2f; color: #fff; }\n");
        html.append("    .sim-toolbar-btn-stopped:hover { background: linear-gradient(to bottom, #ff8a8a 0%, #ff6b6b 100%); border-color: #d32f2f; }\n");
        html.append("    .sim-toolbar-btn-running { background: linear-gradient(to bottom, #66bb6a 0%, #4caf50 100%); border-color: #388e3c; color: #fff; }\n");
        html.append("    .sim-toolbar-btn-running:hover { background: linear-gradient(to bottom, #81c784 0%, #66bb6a 100%); border-color: #388e3c; }\n");
        html.append("    .viewer-content { display: flex; gap: 12px; align-items: stretch; flex: 1; min-height: 0; overflow: hidden; }\n");
        html.append("    .editor-pane { flex: 1 1 50%; min-width: 0; display: none; flex-direction: column; min-height: 0; }\n");
        html.append("    .editor-pane.visible { display: flex; }\n");
        html.append("    .editor-toolbar { margin-bottom: 8px; display: flex; gap: 8px; }\n");
        html.append("    .editor-textarea { width: 100%; flex: 1; min-height: 0; box-sizing: border-box; padding: 10px; font-family: 'SF Mono', 'Consolas', 'Monaco', monospace; font-size: 12px; border: 1px solid #d0d7de; border-radius: 4px; resize: none; }\n");
        html.append("    .preview-pane { flex: 1 1 50%; min-width: 0; min-height: 0; overflow: auto; }\n");
        html.append("    /* Page break handling for PDF and PNG */\n");
        html.append("    h2, h3 { page-break-after: avoid; break-after: avoid; }\n");
        html.append("    table { page-break-inside: avoid; break-inside: avoid; }\n");
        html.append("    .table-section { page-break-inside: avoid; break-inside: avoid; margin-bottom: 20px; }\n");
        html.append("    .circuit-table-wrap { margin: 12px 0; }\n");
        html.append("    .circuit-table-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; color: #24292f; }\n");
        html.append("    .circuit-table-live { width: 100%; border-collapse: collapse; font-size: 13px; }\n");
        html.append("    .circuit-table-live th, .circuit-table-live td { border: 1px solid #d0d7de; padding: 6px 8px; text-align: right; }\n");
        html.append("    .circuit-table-live th:first-child, .circuit-table-live td:first-child { text-align: left; }\n");
        html.append("    .circuit-table-live thead th { background: #f6f8fa; font-weight: 600; }\n");
        html.append("    .circuit-table-status { color: #57606a; font-size: 12px; font-style: italic; }\n");
        html.append("    .circuit-plot-wrap { margin: 12px 0; }\n");
        html.append("    .circuit-plot-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; color: #24292f; }\n");
        html.append("    .circuit-plot-mount { width: 100%; height: 320px; border: 1px solid #d0d7de; border-radius: 6px; background: #fff; }\n");
        html.append("    .circuit-plot-status { color: #57606a; font-size: 12px; font-style: italic; margin-top: 6px; }\n");
        html.append("    @media print { \n");
        html.append("      .export-buttons { display: none; }\n");
        html.append("      body { background: white; }\n");
        html.append("      .container { box-shadow: none; max-width: 100%; padding: 10px; height: auto; display: block; }\n");
        html.append("      h2, h3 { page-break-after: avoid; break-after: avoid; }\n");
        html.append("      table { page-break-inside: avoid; break-inside: avoid; }\n");
        html.append("      tr { page-break-inside: avoid; break-inside: avoid; }\n");
        html.append("    }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container\">\n");
        html.append("    <div class=\"export-buttons\">\n");
        html.append("      <button class=\"export-btn tertiary\" onclick=\"loadReferenceIndex()\">📚 Back to Index</button>\n");
        if (editable) {
            html.append("      <button id=\"toggleEditorBtn\" class=\"export-btn secondary\" onclick=\"toggleEditor()\">✏️ Edit Markdown</button>\n");
        }
        html.append("      <button class=\"export-btn\" onclick=\"saveToPDF()\">📄 Save as PDF</button>\n");
        html.append("      <button class=\"export-btn secondary\" onclick=\"saveToPNG()\">🖼️ Save as PNG</button>\n");
        html.append("      <button id=\"simRunStopBtn\" class=\"sim-toolbar-btn sim-toolbar-btn-stopped\" onclick=\"simRunStop()\">Run</button>\n");
        html.append("      <button class=\"sim-toolbar-btn\" onclick=\"simReset()\">Reset</button>\n");
        html.append("      <button class=\"sim-toolbar-btn\" onclick=\"simStep()\">Step</button>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"viewer-content\">\n");
        html.append("      <div id=\"editorPane\" class=\"editor-pane\">\n");
        html.append("        <div class=\"editor-toolbar\">\n");
        html.append("          <button class=\"export-btn tertiary\" onclick=\"generateTemplateFromCircuit()\">🧩 Generate Template</button>\n");
        html.append("          <button class=\"export-btn secondary\" onclick=\"applyEditorPreview()\">Apply Preview</button>\n");
        html.append("          <button class=\"export-btn\" onclick=\"saveMarkdownToCircuit()\">Save to Model</button>\n");
        html.append("        </div>\n");
        html.append("        <textarea id=\"markdownEditor\" class=\"editor-textarea\"></textarea>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"preview-pane\">\n");
        html.append("        <div id=\"content\">Loading...</div>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");
        html.append("  <script>\n");
        html.append("    var markdown = \"").append(escapedMarkdown).append("\";\n");
        html.append("    var sourceMarkdown = \"").append(escapedSourceMarkdown).append("\";\n");
        html.append("    var autoTemplateMarkdown = \"").append(escapedAutoTemplateMarkdown).append("\";\n");
        html.append("    var editorEnabled = ").append(editable ? "true" : "false").append(";\n");
        html.append("    var liveValues = {};\n");
        html.append("    var liveTables = [];\n");
        html.append("    var nextCircuitPlotId = 1;\n");
        html.append("    var simRunning = false;\n");
        html.append("    function updateRunStopButton() {\n");
        html.append("      const btn = document.getElementById('simRunStopBtn');\n");
        html.append("      if (!btn) return;\n");
        html.append("      if (simRunning) {\n");
        html.append("        btn.textContent = 'Stop';\n");
        html.append("        btn.classList.remove('sim-toolbar-btn-stopped');\n");
        html.append("        btn.classList.add('sim-toolbar-btn-running');\n");
        html.append("      } else {\n");
        html.append("        btn.textContent = 'Run';\n");
        html.append("        btn.classList.remove('sim-toolbar-btn-running');\n");
        html.append("        btn.classList.add('sim-toolbar-btn-stopped');\n");
        html.append("      }\n");
        html.append("    }\n");
        html.append("    function formatLiveValue(v) {\n");
        html.append("      if (v === null || v === undefined || Number.isNaN(v)) return '—';\n");
        html.append("      if (typeof v !== 'number') return String(v);\n");
        html.append("      if (!Number.isFinite(v)) return String(v);\n");
        html.append("      const absV = Math.abs(v);\n");
        html.append("      if ((absV >= 10000 || (absV > 0 && absV < 0.0001))) return v.toExponential(4);\n");
        html.append("      return v.toFixed(4).replace(/\\.?0+$/, '');\n");
        html.append("    }\n");
        html.append("    function updateLiveValueSpans() {\n");
        html.append("      const spans = document.querySelectorAll('[data-live-var]');\n");
        html.append("      spans.forEach(function(span) {\n");
        html.append("        const key = span.getAttribute('data-live-var');\n");
        html.append("        if (!key) return;\n");
        html.append("        if (Object.prototype.hasOwnProperty.call(liveValues, key)) {\n");
        html.append("          span.textContent = formatLiveValue(liveValues[key]);\n");
        html.append("        }\n");
        html.append("      });\n");
        html.append("    }\n");
        html.append("    function bindLiveValuePlaceholders(root) {\n");
        html.append("      if (!root) return;\n");
        html.append("      const textNodes = [];\n");
        html.append("      const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);\n");
        html.append("      let node = walker.nextNode();\n");
        html.append("      while (node) {\n");
        html.append("        textNodes.push(node);\n");
        html.append("        node = walker.nextNode();\n");
        html.append("      }\n");
        html.append("\n");
        html.append("      const tokenRegex = /\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*\\}\\}/g;\n");
        html.append("      textNodes.forEach(function(textNode) {\n");
        html.append("        const raw = textNode.nodeValue || '';\n");
        html.append("        if (raw.indexOf('{{') < 0) return;\n");
        html.append("\n");
        html.append("        let p = textNode.parentNode;\n");
        html.append("        let skip = false;\n");
        html.append("        while (p && p !== root) {\n");
        html.append("          const tag = (p.nodeName || '').toUpperCase();\n");
        html.append("          if (tag === 'CODE' || tag === 'PRE' || tag === 'SCRIPT' || tag === 'STYLE') {\n");
        html.append("            skip = true;\n");
        html.append("            break;\n");
        html.append("          }\n");
        html.append("          p = p.parentNode;\n");
        html.append("        }\n");
        html.append("        if (skip || !textNode.parentNode) return;\n");
        html.append("\n");
        html.append("        tokenRegex.lastIndex = 0;\n");
        html.append("        let match;\n");
        html.append("        let last = 0;\n");
        html.append("        let changed = false;\n");
        html.append("        const frag = document.createDocumentFragment();\n");
        html.append("\n");
        html.append("        while ((match = tokenRegex.exec(raw)) !== null) {\n");
        html.append("          changed = true;\n");
        html.append("          const start = match.index;\n");
        html.append("          if (start > last) {\n");
        html.append("            frag.appendChild(document.createTextNode(raw.substring(last, start)));\n");
        html.append("          }\n");
        html.append("          const varName = match[1];\n");
        html.append("          const span = document.createElement('span');\n");
        html.append("          span.setAttribute('data-live-var', varName);\n");
        html.append("          span.textContent = formatLiveValue(liveValues[varName]);\n");
        html.append("          frag.appendChild(span);\n");
        html.append("          last = tokenRegex.lastIndex;\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        if (!changed) return;\n");
        html.append("        if (last < raw.length) {\n");
        html.append("          frag.appendChild(document.createTextNode(raw.substring(last)));\n");
        html.append("        }\n");
        html.append("        textNode.parentNode.replaceChild(frag, textNode);\n");
        html.append("      });\n");
        html.append("    }\n");
        html.append("    function escapeHtml(text) {\n");
        html.append("      if (text === null || text === undefined) return '';\n");
        html.append("      return String(text)\n");
        html.append("        .replace(/&/g, '&amp;')\n");
        html.append("        .replace(/</g, '&lt;')\n");
        html.append("        .replace(/>/g, '&gt;')\n");
        html.append("        .replace(/\\\"/g, '&quot;')\n");
        html.append("        .replace(/'/g, '&#39;');\n");
        html.append("    }\n");
        html.append("    function renderGreekAndSubscripts(text) {\n");
        html.append("      const greek = { alpha: 'α', beta: 'β', gamma: 'γ', delta: 'δ', epsilon: 'ε', zeta: 'ζ', eta: 'η', theta: 'θ',\n");
        html.append("        iota: 'ι', kappa: 'κ', lambda: 'λ', mu: 'μ', nu: 'ν', xi: 'ξ', omicron: 'ο', pi: 'π', rho: 'ρ', sigma: 'σ',\n");
        html.append("        tau: 'τ', upsilon: 'υ', phi: 'φ', chi: 'χ', psi: 'ψ', omega: 'ω', Gamma: 'Γ', Delta: 'Δ', Theta: 'Θ',\n");
        html.append("        Lambda: 'Λ', Xi: 'Ξ', Pi: 'Π', Sigma: 'Σ', Upsilon: 'Υ', Phi: 'Φ', Psi: 'Ψ', Omega: 'Ω' };\n");
        html.append("      let s = escapeHtml(text);\n");
        html.append("      s = s.replace(/\\\\([A-Za-z]+)/g, function(_m, name) {\n");
        html.append("        return Object.prototype.hasOwnProperty.call(greek, name) ? greek[name] : name;\n");
        html.append("      });\n");
        html.append("      // Long-form LaTeX-style groups\n");
        html.append("      s = s.replace(/_\\{([^}]+)\\}/g, '<sub>$1</sub>');\n");
        html.append("      s = s.replace(/\\^\\{([^}]+)\\}/g, '<sup>$1</sup>');\n");
        html.append("      // Short-form single-token markers\n");
        html.append("      s = s.replace(/([A-Za-zΑ-Ωα-ω0-9\\)\\]])_([A-Za-z0-9]+)/g, '$1<sub>$2</sub>');\n");
        html.append("      s = s.replace(/([A-Za-zΑ-Ωα-ω0-9\\)\\]])\\^([A-Za-z0-9+-]+)/g, '$1<sup>$2</sup>');\n");
        html.append("      return s;\n");
        html.append("    }\n");
        html.append("    function normalizeTableName(name) {\n");
        html.append("      return (name || '').trim().toLowerCase();\n");
        html.append("    }\n");
        html.append("    function canonicalizeTableName(name) {\n");
        html.append("      const norm = normalizeTableName(name);\n");
        html.append("      return norm.replace(/[^a-z0-9]+/g, '');\n");
        html.append("    }\n");
        html.append("    function parseCircuitTableName(codeText) {\n");
        html.append("      if (!codeText) return null;\n");
        html.append("      const lines = String(codeText).split(/\\r?\\n/);\n");
        html.append("      for (let i = 0; i < lines.length; i++) {\n");
        html.append("        let m = lines[i].match(/^\\s*table\\s*:\\s*(.+?)\\s*$/i);\n");
        html.append("        if (m) {\n");
        html.append("          if (m[1].trim() === '*') return 'ALL_TABLES';\n");
        html.append("          return m[1];\n");
        html.append("        }\n");
        html.append("        m = lines[i].match(/^\\s*tables\\s*:\\s*(.+?)\\s*$/i);\n");
        html.append("        if (m) {\n");
        html.append("          if (/^all$/i.test(m[1].trim())) return 'ALL_TABLES';\n");
        html.append("        }\n");
        html.append("      }\n");
        html.append("      return null;\n");
        html.append("    }\n");
        html.append("    function parseCircuitPlotConfig(codeText) {\n");
        html.append("      if (!codeText) return null;\n");
        html.append("      const lines = String(codeText).split(/\\r?\\n/);\n");
        html.append("      let vars = null;\n");
        html.append("      let title = '';\n");
        html.append("      let yaxis = 'Value';\n");
        html.append("      let windowSize = 200;\n");
        html.append("      for (let i = 0; i < lines.length; i++) {\n");
        html.append("        const line = lines[i];\n");
        html.append("        let m = line.match(/^\\s*plot\\s*:\\s*(.+?)\\s*$/i);\n");
        html.append("        if (m) {\n");
        html.append("          vars = m[1].split(',').map(function(v){ return v.trim(); }).filter(function(v){ return v.length > 0; });\n");
        html.append("          continue;\n");
        html.append("        }\n");
        html.append("        m = line.match(/^\\s*title\\s*:\\s*(.+?)\\s*$/i);\n");
        html.append("        if (m) { title = m[1]; continue; }\n");
        html.append("        m = line.match(/^\\s*yaxis\\s*:\\s*(.+?)\\s*$/i);\n");
        html.append("        if (m) { yaxis = m[1]; continue; }\n");
        html.append("        m = line.match(/^\\s*window\\s*:\\s*(\\d+)\\s*$/i);\n");
        html.append("        if (m) {\n");
        html.append("          const w = parseInt(m[1], 10);\n");
        html.append("          if (!Number.isNaN(w) && w > 0) windowSize = w;\n");
        html.append("        }\n");
        html.append("      }\n");
        html.append("      if (!vars || vars.length === 0) return null;\n");
        html.append("      return { vars: vars, title: title, yaxis: yaxis, windowSize: windowSize };\n");
        html.append("    }\n");
        html.append("    function findLiveTableByName(tableName) {\n");
        html.append("      const wanted = normalizeTableName(tableName);\n");
        html.append("      if (!wanted) return null;\n");
        html.append("      const wantedCanon = canonicalizeTableName(tableName);\n");
        html.append("\n");
        html.append("      // 1) Exact normalized match\n");
        html.append("      for (let i = 0; i < liveTables.length; i++) {\n");
        html.append("        const t = liveTables[i];\n");
        html.append("        if (normalizeTableName(t && t.name) === wanted) return t;\n");
        html.append("      }\n");
        html.append("\n");
        html.append("      // 2) Canonical match (ignore spaces, punctuation, underscore/hyphen differences)\n");
        html.append("      if (wantedCanon) {\n");
        html.append("        for (let i = 0; i < liveTables.length; i++) {\n");
        html.append("          const t = liveTables[i];\n");
        html.append("          if (canonicalizeTableName(t && t.name) === wantedCanon) return t;\n");
        html.append("        }\n");
        html.append("      }\n");
        html.append("\n");
        html.append("      // 3) Fallback contains match for small naming drifts\n");
        html.append("      if (wantedCanon && wantedCanon.length >= 4) {\n");
        html.append("        for (let i = 0; i < liveTables.length; i++) {\n");
        html.append("          const t = liveTables[i];\n");
        html.append("          const tableCanon = canonicalizeTableName(t && t.name);\n");
        html.append("          if (!tableCanon) continue;\n");
        html.append("          if (tableCanon.indexOf(wantedCanon) >= 0 || wantedCanon.indexOf(tableCanon) >= 0) return t;\n");
        html.append("        }\n");
        html.append("      }\n");
        html.append("\n");
        html.append("      return null;\n");
        html.append("    }\n");
        html.append("    function isAllTablesToken(name) {\n");
        html.append("      const n = normalizeTableName(name);\n");
        html.append("      return n === 'all_tables' || n === 'all-tables' || n === 'alltables' || n === 'all' || n === '*';\n");
        html.append("    }\n");
        html.append("    function renderCircuitTableMount(mount) {\n");
        html.append("      if (!mount) return;\n");
        html.append("      const tableName = mount.getAttribute('data-circuit-table') || '';\n");
        html.append("      const table = findLiveTableByName(tableName);\n");
        html.append("      if (!table) {\n");
        html.append("        mount.innerHTML = '<div class=\"circuit-table-wrap\"><div class=\"circuit-table-title\">' + escapeHtml(tableName) + '</div><div class=\"circuit-table-status\">Waiting for live table data…</div></div>';\n");
        html.append("        return;\n");
        html.append("      }\n");
        html.append("      const cols = Array.isArray(table.cols) ? table.cols : [];\n");
        html.append("      const rowNames = Array.isArray(table.rowNames) ? table.rowNames : [];\n");
        html.append("      const values = Array.isArray(table.values) ? table.values : [];\n");
        html.append("      const labels = Array.isArray(table.labels) ? table.labels : [];\n");
        html.append("      const hints = Array.isArray(table.hints) ? table.hints : [];\n");
        html.append("      const tableType = (table.type || '').toLowerCase();\n");
        html.append("      let html = '<div class=\"circuit-table-wrap\">';\n");
        html.append("      html += '<div class=\"circuit-table-title\">' + renderGreekAndSubscripts(table.name || tableName) + '</div>';\n");
        html.append("      if (tableType === 'equation') {\n");
        html.append("        html += '<table class=\"circuit-table-live\"><thead><tr><th>Output</th><th>Equation</th><th>Hint</th></tr></thead><tbody>';\n");
        html.append("        for (let r = 0; r < rowNames.length; r++) {\n");
        html.append("          const outputName = rowNames[r] || '';\n");
        html.append("          const rowVals = Array.isArray(values[r]) ? values[r] : [];\n");
        html.append("          const rowLabels = Array.isArray(labels[r]) ? labels[r] : [];\n");
        html.append("          const equationText = (rowLabels[0] || '').trim();\n");
        html.append("          const v = rowVals[0];\n");
        html.append("          const formattedValue = (typeof v === 'number') ? formatLiveValue(v) : '—';\n");
        html.append("          const eqCell = equationText ? (renderGreekAndSubscripts(equationText) + ' = ' + escapeHtml(formattedValue)) : escapeHtml(formattedValue);\n");
        html.append("          const isNegative = (typeof v === 'number' && v < 0);\n");
        html.append("          const eqStyle = isNegative ? ' style=\\\"color:#cf222e\\\"' : '';\n");
        html.append("          const hintText = hints[r] || '';\n");
        html.append("          html += '<tr>';\n");
        html.append("          html += '<td>' + renderGreekAndSubscripts(outputName) + '</td>';\n");
        html.append("          html += '<td' + eqStyle + '>' + eqCell + '</td>';\n");
        html.append("          html += '<td>' + renderGreekAndSubscripts(hintText) + '</td>';\n");
        html.append("          html += '</tr>';\n");
        html.append("        }\n");
        html.append("        html += '</tbody></table></div>';\n");
        html.append("        mount.innerHTML = html;\n");
        html.append("        return;\n");
        html.append("      }\n");
        html.append("      html += '<table class=\"circuit-table-live\"><thead><tr><th>Flow/Stock</th>';\n");
        html.append("      for (let c = 0; c < cols.length; c++) html += '<th>' + renderGreekAndSubscripts(cols[c]) + '</th>';\n");
        html.append("      html += '</tr></thead><tbody>';\n");
        html.append("      for (let r = 0; r < rowNames.length; r++) {\n");
        html.append("        html += '<tr><td>' + renderGreekAndSubscripts(rowNames[r]) + '</td>';\n");
        html.append("        const rowVals = Array.isArray(values[r]) ? values[r] : [];\n");
        html.append("        const rowLabels = Array.isArray(labels[r]) ? labels[r] : [];\n");
        html.append("        for (let c = 0; c < cols.length; c++) {\n");
        html.append("          const v = rowVals[c];\n");
        html.append("          const label = (rowLabels[c] || '').trim();\n");
        html.append("          const formattedValue = (typeof v === 'number') ? formatLiveValue(v) : '—';\n");
        html.append("          const cell = label ? (renderGreekAndSubscripts(label) + ' = ' + escapeHtml(formattedValue)) : '';\n");
        html.append("          const isNegative = (typeof v === 'number' && v < 0);\n");
        html.append("          const tdStyle = isNegative ? ' style=\\\"color:#cf222e\\\"' : '';\n");
        html.append("          html += '<td' + tdStyle + '>' + cell + '</td>';\n");
        html.append("        }\n");
        html.append("        html += '</tr>';\n");
        html.append("      }\n");
        html.append("      html += '</tbody></table></div>';\n");
        html.append("      mount.innerHTML = html;\n");
        html.append("    }\n");
        html.append("    function updateCircuitTableMounts() {\n");
        html.append("      const mounts = document.querySelectorAll('[data-circuit-table]');\n");
        html.append("      mounts.forEach(function(mount) {\n");
        html.append("        const tableName = mount.getAttribute('data-circuit-table') || '';\n");
        html.append("        if (isAllTablesToken(tableName)) {\n");
        html.append("          if (!liveTables || liveTables.length === 0) {\n");
        html.append("            mount.innerHTML = '<div class=\\\"circuit-table-wrap\\\"><div class=\\\"circuit-table-title\\\">All Tables</div><div class=\\\"circuit-table-status\\\">Waiting for live table data…</div></div>';\n");
        html.append("            return;\n");
        html.append("          }\n");
        html.append("          let html = '';\n");
        html.append("          for (let i = 0; i < liveTables.length; i++) {\n");
        html.append("            const t = liveTables[i];\n");
        html.append("            if (!t || !t.name) continue;\n");
        html.append("            html += '<div data-circuit-table=\\\"' + escapeHtml(t.name) + '\\\"></div>';\n");
        html.append("          }\n");
        html.append("          mount.innerHTML = html;\n");
        html.append("          const nested = mount.querySelectorAll('[data-circuit-table]');\n");
        html.append("          nested.forEach(function(nm) { renderCircuitTableMount(nm); });\n");
        html.append("          return;\n");
        html.append("        }\n");
        html.append("        renderCircuitTableMount(mount);\n");
        html.append("      });\n");
        html.append("    }\n");
        html.append("    function ensureCircuitPlotInitialized(mount, cfg) {\n");
        html.append("      if (!window.Plotly || !cfg || !cfg.vars || cfg.vars.length === 0) {\n");
        html.append("        mount.innerHTML = '<div class=\\\"circuit-plot-status\\\">Plotly unavailable or invalid plot config.</div>';\n");
        html.append("        return false;\n");
        html.append("      }\n");
        html.append("      if (mount.__plotInitialized) return true;\n");
        html.append("      const traces = cfg.vars.map(function(name) {\n");
        html.append("        return { x: [], y: [], mode: 'lines', type: 'scatter', name: name, line: { width: 2 } };\n");
        html.append("      });\n");
        html.append("      const layout = {\n");
        html.append("        title: cfg.title || ('Plot: ' + cfg.vars.join(', ')),\n");
        html.append("        xaxis: { title: 'Time', gridcolor: '#ddd' },\n");
        html.append("        yaxis: { title: cfg.yaxis || 'Value', gridcolor: '#ddd' },\n");
        html.append("        margin: { l: 56, r: 16, t: 48, b: 42 },\n");
        html.append("        paper_bgcolor: '#fff',\n");
        html.append("        plot_bgcolor: '#fff',\n");
        html.append("        showlegend: true,\n");
        html.append("        legend: { orientation: 'h' }\n");
        html.append("      };\n");
        html.append("      const config = { responsive: true, displaylogo: false };\n");
        html.append("      Plotly.newPlot(mount.id, traces, layout, config);\n");
        html.append("      mount.__plotInitialized = true;\n");
        html.append("      return true;\n");
        html.append("    }\n");
        html.append("    function updateCircuitPlotMounts(explicitTime) {\n");
        html.append("      const mounts = document.querySelectorAll('[data-circuit-plot]');\n");
        html.append("      mounts.forEach(function(mount) {\n");
        html.append("        const cfgText = mount.getAttribute('data-circuit-plot') || '';\n");
        html.append("        let cfg = null;\n");
        html.append("        try { cfg = JSON.parse(cfgText); } catch (e) { cfg = null; }\n");
        html.append("        if (!cfg || !cfg.vars || cfg.vars.length === 0) return;\n");
        html.append("        if (!ensureCircuitPlotInitialized(mount, cfg)) return;\n");
        html.append("\n");
        html.append("        const t = (typeof explicitTime === 'number') ? explicitTime : (typeof liveValues.t === 'number' ? liveValues.t : null);\n");
        html.append("        if (typeof t === 'number' && mount.__lastPlotT === t) return;\n");
        html.append("\n");
        html.append("        const xVal = (typeof t === 'number') ? t : ((mount.__plotPointIndex || 0) + 1);\n");
        html.append("        mount.__plotPointIndex = xVal;\n");
        html.append("        if (typeof t === 'number') mount.__lastPlotT = t;\n");
        html.append("\n");
        html.append("        const xArr = [];\n");
        html.append("        const yArr = [];\n");
        html.append("        const traceIdx = [];\n");
        html.append("        for (let i = 0; i < cfg.vars.length; i++) {\n");
        html.append("          const name = cfg.vars[i];\n");
        html.append("          const yVal = Object.prototype.hasOwnProperty.call(liveValues, name) ? liveValues[name] : null;\n");
        html.append("          xArr.push([xVal]);\n");
        html.append("          yArr.push([typeof yVal === 'number' ? yVal : null]);\n");
        html.append("          traceIdx.push(i);\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        Plotly.extendTraces(mount.id, { x: xArr, y: yArr }, traceIdx, cfg.windowSize || 200);\n");
        html.append("      });\n");
        html.append("    }\n");
        html.append("    function clearCircuitPlotMounts() {\n");
        html.append("      const mounts = document.querySelectorAll('[data-circuit-plot]');\n");
        html.append("      mounts.forEach(function(mount) {\n");
        html.append("        mount.__plotInitialized = false;\n");
        html.append("        mount.__lastPlotT = null;\n");
        html.append("        mount.__plotPointIndex = 0;\n");
        html.append("        if (window.Plotly && mount.id) {\n");
        html.append("          try { Plotly.purge(mount.id); } catch (e) {}\n");
        html.append("        }\n");
        html.append("      });\n");
        html.append("      updateCircuitPlotMounts();\n");
        html.append("    }\n");
        html.append("    function installCircuitTableMounts() {\n");
        html.append("      const content = document.getElementById('content');\n");
        html.append("      if (!content) return;\n");
        html.append("      const codeBlocks = content.querySelectorAll('pre > code');\n");
        html.append("      codeBlocks.forEach(function(code) {\n");
        html.append("        const tableName = parseCircuitTableName(code.textContent || '');\n");
        html.append("        if (!tableName) return;\n");
        html.append("        const mount = document.createElement('div');\n");
        html.append("        mount.setAttribute('data-circuit-table', tableName);\n");
        html.append("        const pre = code.parentElement;\n");
        html.append("        if (pre && pre.tagName === 'PRE') {\n");
        html.append("          pre.parentNode.replaceChild(mount, pre);\n");
        html.append("        } else {\n");
        html.append("          code.parentNode.replaceChild(mount, code);\n");
        html.append("        }\n");
        html.append("      });\n");
        html.append("      updateCircuitTableMounts();\n");
        html.append("    }\n");
        html.append("    function installCircuitPlotMounts() {\n");
        html.append("      const content = document.getElementById('content');\n");
        html.append("      if (!content) return;\n");
        html.append("      const codeBlocks = content.querySelectorAll('pre > code');\n");
        html.append("      codeBlocks.forEach(function(code) {\n");
        html.append("        const plotCfg = parseCircuitPlotConfig(code.textContent || '');\n");
        html.append("        if (!plotCfg) return;\n");
        html.append("\n");
        html.append("        const wrap = document.createElement('div');\n");
        html.append("        wrap.className = 'circuit-plot-wrap';\n");
        html.append("\n");
        html.append("        const plotDiv = document.createElement('div');\n");
        html.append("        plotDiv.className = 'circuit-plot-mount';\n");
        html.append("        plotDiv.id = 'circuit-plot-' + (nextCircuitPlotId++);\n");
        html.append("        plotDiv.setAttribute('data-circuit-plot', JSON.stringify(plotCfg));\n");
        html.append("\n");
        html.append("        wrap.appendChild(plotDiv);\n");
        html.append("\n");
        html.append("        const pre = code.parentElement;\n");
        html.append("        if (pre && pre.tagName === 'PRE') {\n");
        html.append("          pre.parentNode.replaceChild(wrap, pre);\n");
        html.append("        } else {\n");
        html.append("          code.parentNode.replaceChild(wrap, code);\n");
        html.append("        }\n");
        html.append("      });\n");
        html.append("      updateCircuitPlotMounts();\n");
        html.append("    }\n");
        html.append("    window.addEventListener('message', function(event) {\n");
        html.append("      const data = event.data;\n");
        html.append("      if (!data || data.type !== 'circuit-live') return;\n");
        html.append("      const prevT = (typeof liveValues.t === 'number') ? liveValues.t : null;\n");
        html.append("      if (typeof data.running === 'boolean') { simRunning = data.running; updateRunStopButton(); }\n");
        html.append("      liveValues = data.vars || {};\n");
        html.append("      liveTables = Array.isArray(data.tables) ? data.tables : [];\n");
        html.append("      if (typeof data.t === 'number') liveValues.t = data.t;\n");
        html.append("      if (typeof data.dt === 'number') liveValues.dt = data.dt;\n");
        html.append("      if (typeof data.t === 'number' && typeof prevT === 'number' && data.t < prevT) { clearCircuitPlotMounts(); }\n");
        html.append("      updateLiveValueSpans();\n");
        html.append("      updateCircuitTableMounts();\n");
        html.append("      updateCircuitPlotMounts(data.t);\n");
        html.append("    });\n");
        html.append("    function toggleEditor() {\n");
        html.append("      if (!editorEnabled) return;\n");
        html.append("      const pane = document.getElementById('editorPane');\n");
        html.append("      const btn = document.getElementById('toggleEditorBtn');\n");
        html.append("      if (!pane) return;\n");
        html.append("      const nowVisible = !pane.classList.contains('visible');\n");
        html.append("      pane.classList.toggle('visible', nowVisible);\n");
        html.append("      if (btn) btn.textContent = nowVisible ? '👁️ Hide Editor' : '✏️ Edit Markdown';\n");
        html.append("      if (nowVisible) {\n");
        html.append("        const editor = document.getElementById('markdownEditor');\n");
        html.append("        if (editor) editor.value = sourceMarkdown;\n");
        html.append("      }\n");
        html.append("    }\n");
        html.append("    function applyEditorPreview() {\n");
        html.append("      const editor = document.getElementById('markdownEditor');\n");
        html.append("      if (!editor) return;\n");
        html.append("      sourceMarkdown = editor.value || '';\n");
        html.append("      markdown = sourceMarkdown;\n");
        html.append("      renderMarkdown(markdown);\n");
        html.append("    }\n");
        html.append("    function generateTemplateFromCircuit() {\n");
        html.append("      if (!editorEnabled) return;\n");
        html.append("      const editor = document.getElementById('markdownEditor');\n");
        html.append("      if (!editor) return;\n");
        html.append("      const nextTemplate = autoTemplateMarkdown || '';\n");
        html.append("      if (!nextTemplate.trim()) return;\n");
        html.append("      const hasExisting = (editor.value || '').trim().length > 0;\n");
        html.append("      if (hasExisting && !confirm('Replace current markdown with generated template?')) return;\n");
        html.append("      editor.value = nextTemplate;\n");
        html.append("      sourceMarkdown = nextTemplate;\n");
        html.append("      markdown = nextTemplate;\n");
        html.append("      renderMarkdown(markdown);\n");
        html.append("    }\n");
        html.append("    function saveMarkdownToCircuit() {\n");
        html.append("      const editor = document.getElementById('markdownEditor');\n");
        html.append("      if (!editor) return;\n");
        html.append("      sourceMarkdown = editor.value || '';\n");
        html.append("      markdown = sourceMarkdown;\n");
        html.append("      renderMarkdown(markdown);\n");
        html.append("      const msg = { type: 'info-viewer-save-markdown', markdown: sourceMarkdown };\n");
        html.append("      try { if (window.parent) window.parent.postMessage(msg, '*'); } catch (e1) {}\n");
        html.append("      try { if (window.opener) window.opener.postMessage(msg, '*'); } catch (e2) {}\n");
        html.append("    }\n");
        html.append("    function getCircuitApi() {\n");
        html.append("      try { if (window.parent && window.parent !== window && window.parent.CircuitJS1) return window.parent.CircuitJS1; } catch (e1) {}\n");
        html.append("      try { if (window.opener && window.opener.CircuitJS1) return window.opener.CircuitJS1; } catch (e2) {}\n");
        html.append("      try { if (window.CircuitJS1) return window.CircuitJS1; } catch (e3) {}\n");
        html.append("      return null;\n");
        html.append("    }\n");
        html.append("    function sendSimCommand(command) {\n");
        html.append("      const msg = { type: 'info-viewer-sim-command', command: command };\n");
        html.append("      try { if (window.parent) window.parent.postMessage(msg, '*'); } catch (e1) {}\n");
        html.append("      try { if (window.opener) window.opener.postMessage(msg, '*'); } catch (e2) {}\n");
        html.append("    }\n");
        html.append("    function simRunStop() {\n");
        html.append("      const api = getCircuitApi();\n");
        html.append("      const nextRunning = !simRunning;\n");
        html.append("      simRunning = nextRunning;\n");
        html.append("      updateRunStopButton();\n");
        html.append("      if (api && api.setSimRunning) { api.setSimRunning(nextRunning); return; }\n");
        html.append("      sendSimCommand(nextRunning ? 'run' : 'stop');\n");
        html.append("    }\n");
        html.append("    function simReset() {\n");
        html.append("      clearCircuitPlotMounts();\n");
        html.append("      const api = getCircuitApi();\n");
        html.append("      if (api && api.reset) { api.reset(); return; }\n");
        html.append("      sendSimCommand('reset');\n");
        html.append("    }\n");
        html.append("    function simStep() {\n");
        html.append("      const api = getCircuitApi();\n");
        html.append("      simRunning = false;\n");
        html.append("      updateRunStopButton();\n");
        html.append("      if (api && api.setSimRunning) api.setSimRunning(false);\n");
        html.append("      if (api && api.step) { api.step(); return; }\n");
        html.append("      sendSimCommand('stop');\n");
        html.append("      sendSimCommand('step');\n");
        html.append("    }\n");
        html.append("    function resolveMarkdownHref(href) {\n");
        html.append("      if (!href) return null;\n");
        html.append("      if (href.startsWith('#') || href.startsWith('http://') || href.startsWith('https://') || href.startsWith('mailto:') || href.startsWith('data:')) return null;\n");
        html.append("      var clean = href.split('#')[0].split('?')[0];\n");
        html.append("      if (!clean || !clean.toLowerCase().endsWith('.md')) return null;\n");
        html.append("      if (clean.startsWith('/')) return clean.substring(1);\n");
        html.append("      if (clean.startsWith('doc/')) return clean;\n");
        html.append("      if (clean.startsWith('./')) clean = clean.substring(2);\n");
        html.append("      return 'doc/reference/' + clean;\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    function attachMarkdownLinkHandlers() {\n");
        html.append("      const content = document.getElementById('content');\n");
        html.append("      const links = content.querySelectorAll('a[href]');\n");
        html.append("      links.forEach(function(link) {\n");
        html.append("        const href = link.getAttribute('href');\n");
        html.append("        const mdUrl = resolveMarkdownHref(href);\n");
        html.append("        if (!mdUrl) return;\n");
        html.append("        link.addEventListener('click', function(e) {\n");
        html.append("          e.preventDefault();\n");
        html.append("          fetch(mdUrl).then(function(resp) {\n");
        html.append("            if (!resp.ok) throw new Error('HTTP ' + resp.status);\n");
        html.append("            return resp.text();\n");
        html.append("          }).then(function(text) {\n");
        html.append("            renderMarkdown(text);\n");
        html.append("          }).catch(function(err) {\n");
        html.append("            const c = document.getElementById('content');\n");
        html.append("            c.innerHTML = '<p>Failed to load markdown: ' + mdUrl + '</p>';\n");
        html.append("          });\n");
        html.append("        });\n");
        html.append("      });\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    function renderMarkdown(md) {\n");
        html.append("      markdown = md;\n");
        html.append("      const content = document.getElementById('content');\n");
        html.append("      // Protect math segments so markdown emphasis parsing doesn't eat '*' inside $...$\n");
        html.append("      const protectedMath = [];\n");
        html.append("      const markdownForParse = markdown.replace(/\\$\\$[\\s\\S]*?\\$\\$|\\$[^$\\n]+\\$/g, function(match) {\n");
        html.append("        const token = '@@MATH_SEGMENT_' + protectedMath.length + '@@';\n");
        html.append("        protectedMath.push(match);\n");
        html.append("        return token;\n");
        html.append("      });\n");
        html.append("      content.innerHTML = marked.parse(markdownForParse);\n");
        html.append("      if (protectedMath.length > 0) {\n");
        html.append("        let restoredHtml = content.innerHTML;\n");
        html.append("        for (let i = 0; i < protectedMath.length; i++) {\n");
        html.append("          const token = '@@MATH_SEGMENT_' + i + '@@';\n");
        html.append("          restoredHtml = restoredHtml.split(token).join(protectedMath[i]);\n");
        html.append("        }\n");
        html.append("        content.innerHTML = restoredHtml;\n");
        html.append("      }\n");
        html.append("      // Render LaTeX math equations with KaTeX\n");
        html.append("      renderMathInElement(content, {\n");
        html.append("        delimiters: [\n");
        html.append("          {left: '$$', right: '$$', display: true},\n");
        html.append("          {left: '$', right: '$', display: false},\n");
        html.append("          {left: '\\\\[', right: '\\\\]', display: true},\n");
        html.append("          {left: '\\\\(', right: '\\\\)', display: false}\n");
        html.append("        ],\n");
        html.append("        throwOnError: false\n");
        html.append("      });\n");
        html.append("      bindLiveValuePlaceholders(content);\n");
        html.append("      installCircuitTableMounts();\n");
        html.append("      installCircuitPlotMounts();\n");
        html.append("      // Wrap tables with preceding headers in sections for better page breaks\n");
        html.append("      const children = Array.from(content.children);\n");
        html.append("      let currentSection = null;\n");
        html.append("      let lastH3 = null;\n");
        html.append("      children.forEach((child, idx) => {\n");
        html.append("        if (child.tagName === 'H3') {\n");
        html.append("          lastH3 = child;\n");
        html.append("          currentSection = null;\n");
        html.append("        } else if (child.tagName === 'TABLE') {\n");
        html.append("          currentSection = document.createElement('div');\n");
        html.append("          currentSection.className = 'table-section';\n");
        html.append("          child.parentNode.insertBefore(currentSection, lastH3 || child);\n");
        html.append("          if (lastH3) currentSection.appendChild(lastH3);\n");
        html.append("          currentSection.appendChild(child);\n");
        html.append("          lastH3 = null;\n");
        html.append("          currentSection = null;\n");
        html.append("        }\n");
        html.append("      });\n");
        html.append("      attachMarkdownLinkHandlers();\n");
        html.append("      updateLiveValueSpans();\n");
        html.append("      updateCircuitTableMounts();\n");
        html.append("      updateCircuitPlotMounts();\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    function loadReferenceIndex() {\n");
        html.append("      fetch('doc/reference/ReferenceIndex.md').then(function(resp) {\n");
        html.append("        if (!resp.ok) throw new Error('HTTP ' + resp.status);\n");
        html.append("        return resp.text();\n");
        html.append("      }).then(function(text) {\n");
        html.append("        renderMarkdown(text);\n");
        html.append("      }).catch(function(err) {\n");
        html.append("        const c = document.getElementById('content');\n");
        html.append("        c.innerHTML = '<p>Failed to load reference index.</p>';\n");
        html.append("      });\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    renderMarkdown(markdown);\n");
        html.append("    updateRunStopButton();\n");
        html.append("    (function syncInitialRunState(){\n");
        html.append("      const api = getCircuitApi();\n");
        html.append("      if (api && api.isRunning) {\n");
        html.append("        try { simRunning = !!api.isRunning(); updateRunStopButton(); } catch (e) {}\n");
        html.append("      }\n");
        html.append("    })();\n");
        html.append("    if (editorEnabled) {\n");
        html.append("      const editor = document.getElementById('markdownEditor');\n");
        html.append("      if (editor) editor.value = sourceMarkdown;\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    \n");
        html.append("    function saveToPDF() {\n");
        html.append("      window.print();\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    function saveToPNG() {\n");
        html.append("      const button = event.target;\n");
        html.append("      const exportButtons = document.querySelector('.export-buttons');\n");
        html.append("      button.disabled = true;\n");
        html.append("      button.textContent = 'Generating...';\n");
        html.append("      \n");
        html.append("      // Hide export buttons during capture\n");
        html.append("      exportButtons.style.display = 'none';\n");
        html.append("      \n");
        html.append("      const container = document.querySelector('.container');\n");
        html.append("      const scale = 2;\n");
        html.append("      \n");
        html.append("      // A4 dimensions at 96 DPI (screen resolution)\n");
        html.append("      const a4Height = 1123; // 297mm\n");
        html.append("      const timestamp = Date.now();\n");
        html.append("      \n");
        html.append("      // Find natural break points (before table-sections, h2, hr)\n");
        html.append("      // Only break BEFORE .table-section divs (which contain h3 + table together)\n");
        html.append("      const containerRect = container.getBoundingClientRect();\n");
        html.append("      const containerTop = containerRect.top;\n");
        html.append("      \n");
        html.append("      // Get table sections (h3 + table combined) and other block elements\n");
        html.append("      const tableSections = container.querySelectorAll('.table-section');\n");
        html.append("      const otherBreaks = container.querySelectorAll('h2, hr');\n");
        html.append("      const breakPoints = [0]; // Start at 0\n");
        html.append("      \n");
        html.append("      // Add table section starts as break points\n");
        html.append("      tableSections.forEach(el => {\n");
        html.append("        const rect = el.getBoundingClientRect();\n");
        html.append("        const relativeTop = rect.top - containerTop;\n");
        html.append("        if (relativeTop > 50) breakPoints.push(relativeTop);\n");
        html.append("      });\n");
        html.append("      \n");
        html.append("      // Add h2 and hr elements as break points\n");
        html.append("      otherBreaks.forEach(el => {\n");
        html.append("        const rect = el.getBoundingClientRect();\n");
        html.append("        const relativeTop = rect.top - containerTop;\n");
        html.append("        if (relativeTop > 50) breakPoints.push(relativeTop);\n");
        html.append("      });\n");
        html.append("      \n");
        html.append("      // Sort break points by position\n");
        html.append("      breakPoints.sort((a, b) => a - b);\n");
        html.append("      \n");
        html.append("      // First, capture the entire content as one big canvas\n");
        html.append("      html2canvas(container, {\n");
        html.append("        scale: scale,\n");
        html.append("        backgroundColor: '#ffffff',\n");
        html.append("        logging: false,\n");
        html.append("        useCORS: true\n");
        html.append("      }).then(fullCanvas => {\n");
        html.append("        // Show buttons again after capture\n");
        html.append("        exportButtons.style.display = 'flex';\n");
        html.append("        \n");
        html.append("        const fullHeight = fullCanvas.height;\n");
        html.append("        const fullWidth = fullCanvas.width;\n");
        html.append("        const pageHeightPx = a4Height * scale;\n");
        html.append("        \n");
        html.append("        // Find smart page breaks\n");
        html.append("        const pageBreaks = [0];\n");
        html.append("        let currentY = 0;\n");
        html.append("        \n");
        html.append("        while (currentY < fullHeight) {\n");;
        html.append("          let idealBreak = currentY + pageHeightPx;\n");
        html.append("          \n");
        html.append("          if (idealBreak >= fullHeight) {\n");
        html.append("            // Last page\n");
        html.append("            break;\n");
        html.append("          }\n");
        html.append("          \n");
        html.append("          // Find the best break point near the ideal break\n");
        html.append("          // Look for a natural break within 15% of page height\n");
        html.append("          const minBreak = idealBreak - (pageHeightPx * 0.15);\n");
        html.append("          const maxBreak = idealBreak;\n");
        html.append("          \n");
        html.append("          let bestBreak = idealBreak;\n");
        html.append("          let foundNatural = false;\n");
        html.append("          \n");
        html.append("          // Search break points in reverse to find one close to but before ideal\n");
        html.append("          for (let i = breakPoints.length - 1; i >= 0; i--) {\n");
        html.append("            const bp = breakPoints[i] * scale;\n");
        html.append("            if (bp >= minBreak && bp <= maxBreak && bp > currentY + (pageHeightPx * 0.5)) {\n");
        html.append("              bestBreak = bp;\n");
        html.append("              foundNatural = true;\n");
        html.append("              break;\n");
        html.append("            }\n");
        html.append("          }\n");
        html.append("          \n");
        html.append("          pageBreaks.push(bestBreak);\n");
        html.append("          currentY = bestBreak;\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        pageBreaks.push(fullHeight); // End marker\n");
        html.append("        const numPages = pageBreaks.length - 1;\n");
        html.append("        \n");
        html.append("        if (numPages === 1) {\n");
        html.append("          // Single page - just download it\n");
        html.append("          fullCanvas.toBlob(function(blob) {\n");
        html.append("            downloadBlob(blob, 'circuit-tables-' + timestamp + '.png');\n");
        html.append("            button.disabled = false;\n");
        html.append("            button.textContent = '🖼️ Save as PNG';\n");
        html.append("          });\n");
        html.append("          return;\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        // Multiple pages - slice and download each\n");
        html.append("        button.textContent = 'Slicing into ' + numPages + ' pages...';\n");
        html.append("        \n");
        html.append("        let currentPage = 0;\n");
        html.append("        \n");
        html.append("        function downloadNextPage() {\n");
        html.append("          if (currentPage >= numPages) {\n");
        html.append("            button.disabled = false;\n");
        html.append("            button.textContent = '🖼️ Save as PNG';\n");
        html.append("            return;\n");
        html.append("          }\n");
        html.append("          \n");
        html.append("          const pageNum = currentPage + 1;\n");
        html.append("          button.textContent = 'Saving page ' + pageNum + '/' + numPages + '...';\n");
        html.append("          \n");
        html.append("          const yStart = pageBreaks[currentPage];\n");
        html.append("          const yEnd = pageBreaks[currentPage + 1];\n");
        html.append("          const sliceHeight = yEnd - yStart;\n");
        html.append("          \n");
        html.append("          // Create a new canvas for this page\n");
        html.append("          const pageCanvas = document.createElement('canvas');\n");
        html.append("          pageCanvas.width = fullWidth;\n");
        html.append("          pageCanvas.height = sliceHeight;\n");
        html.append("          const ctx = pageCanvas.getContext('2d');\n");
        html.append("          \n");
        html.append("          // Draw the slice from the full canvas\n");
        html.append("          ctx.drawImage(fullCanvas, 0, yStart, fullWidth, sliceHeight, 0, 0, fullWidth, sliceHeight);\n");
        html.append("          \n");
        html.append("          pageCanvas.toBlob(function(blob) {\n");
        html.append("            downloadBlob(blob, 'circuit-tables-' + timestamp + '-page-' + pageNum + '.png');\n");
        html.append("            currentPage++;\n");
        html.append("            // User must click each time - browsers block multiple auto downloads\n");
        html.append("            if (currentPage < numPages) {\n");
        html.append("              button.textContent = '📥 Click for page ' + (currentPage + 1) + '/' + numPages;\n");
        html.append("              button.disabled = false;\n");
        html.append("              button.onclick = function(e) {\n");
        html.append("                e.preventDefault();\n");
        html.append("                button.disabled = true;\n");
        html.append("                downloadNextPage();\n");
        html.append("              };\n");
        html.append("            } else {\n");
        html.append("              button.disabled = false;\n");
        html.append("              button.textContent = '🖼️ Save as PNG';\n");
        html.append("              button.onclick = function() { saveToPNG(); };\n");
        html.append("            }\n");
        html.append("          });\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        downloadNextPage();\n");
        html.append("        \n");
        html.append("      }).catch(err => {\n");
        html.append("        console.error('PNG export error:', err);\n");
        html.append("        alert('Error generating PNG: ' + err.message);\n");
        html.append("        button.disabled = false;\n");
        html.append("        button.textContent = '🖼️ Save as PNG';\n");
        html.append("      });\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    function downloadBlob(blob, filename) {\n");
        html.append("      const url = URL.createObjectURL(blob);\n");
        html.append("      const a = document.createElement('a');\n");
        html.append("      a.href = url;\n");
        html.append("      a.download = filename;\n");
        html.append("      document.body.appendChild(a);\n");
        html.append("      a.click();\n");
        html.append("      document.body.removeChild(a);\n");
        html.append("      URL.revokeObjectURL(url);\n");
        html.append("    }\n");
        html.append("  </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    /**
     * Escape string for embedding in JavaScript string literal.
     */
    private static String escapeForJS(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
    
    /**
     * Escape HTML special characters.
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
    
    /**
     * Format text with Greek symbols and subscript/superscript conversion.
     * Converts LaTeX-style syntax (\alpha, X_1, X^2) to HTML.
     * Used for table content display.
     */
    private static String formatWithGreekAndSubscripts(String text) {
        if (text == null) return "";
        return Locale.convertToHTML(text);
    }
    
    /**
     * Create dialog with simple HTML rendering (no external dependencies).
     * Good for basic display when CDN is unavailable.
     */
    private InfoViewerDialog(String title, String markdown) {
        super(true, true); // auto-hide, modal
        
        this.markdownContent = markdown;
        setText(Locale.LS(title));
        setAnimationEnabled(true);
        
        vp = new VerticalPanel();
        vp.setSpacing(10);
        
        // Convert markdown to simple HTML
        String simpleHtml = convertSimpleMarkdown(markdown);
        
        // Create scrollable content area
        HTML content = new HTML(simpleHtml);
        content.setStyleName("infoViewerContent");
        
        ScrollPanel scrollPanel = new ScrollPanel(content);
        scrollPanel.setSize("500px", "400px");
        
        vp.add(scrollPanel);
        
        // Buttons
        Button closeBtn = new Button(Locale.LS("Close"));
        closeBtn.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                hide();
                instance = null;
            }
        });
        
        Button openInWindowBtn = new Button(Locale.LS("Open in New Window"));
        openInWindowBtn.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                showInfoInWindow(getText(), markdownContent);
            }
        });
        
        com.google.gwt.user.client.ui.HorizontalPanel buttonPanel = 
            new com.google.gwt.user.client.ui.HorizontalPanel();
        buttonPanel.setSpacing(5);
        buttonPanel.add(openInWindowBtn);
        buttonPanel.add(closeBtn);
        vp.add(buttonPanel);
        
        setWidget(vp);
        center();
        show();
    }
    
    /**
     * Convert simple markdown to HTML without external libraries.
     * Supports basic formatting for fallback display.
     */
    private String convertSimpleMarkdown(String markdown) {
        if (markdown == null) return "";
        
        StringBuilder html = new StringBuilder();
        html.append("<div style='font-family: sans-serif; line-height: 1.6;'>");
        
        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;
        boolean inList = false;
        
        for (String line : lines) {
            // Handle code blocks
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    html.append("</pre>");
                    inCodeBlock = false;
                } else {
                    html.append("<pre style='background:#f4f4f4; padding:10px; overflow-x:auto;'>");
                    inCodeBlock = true;
                }
                continue;
            }
            
            if (inCodeBlock) {
                html.append(escapeHtml(line)).append("\n");
                continue;
            }
            
            // Handle headers
            if (line.startsWith("### ")) {
                html.append("<h3>").append(formatWithGreekAndSubscripts(line.substring(4))).append("</h3>");
                continue;
            }
            if (line.startsWith("## ")) {
                html.append("<h2>").append(formatWithGreekAndSubscripts(line.substring(3))).append("</h2>");
                continue;
            }
            if (line.startsWith("# ")) {
                html.append("<h1>").append(formatWithGreekAndSubscripts(line.substring(2))).append("</h1>");
                continue;
            }
            
            // Handle horizontal rule (=== or ---)
            if (line.trim().matches("^[=]{3,}$") || line.trim().matches("^[-]{3,}$")) {
                html.append("<hr>");
                continue;
            }
            
            // Handle list items
            if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(formatInline(line.trim().substring(2))).append("</li>");
                continue;
            } else if (inList && !line.trim().isEmpty()) {
                html.append("</ul>");
                inList = false;
            }
            
            // Handle empty lines
            if (line.trim().isEmpty()) {
                html.append("<br>");
                continue;
            }
            
            // Regular paragraph
            html.append("<p>").append(formatInline(line)).append("</p>");
        }
        
        if (inList) html.append("</ul>");
        if (inCodeBlock) html.append("</pre>");
        
        html.append("</div>");
        return html.toString();
    }
    
    /**
     * Format inline markdown (bold, italic, code, links).
     * Also converts Greek symbols and subscript/superscript notation.
     */
    private String formatInline(String text) {
        // First convert Greek and subscripts, which also escapes HTML
        text = formatWithGreekAndSubscripts(text);
        
        // Bold: **text** or __text__
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        text = text.replaceAll("__(.+?)__", "<strong>$1</strong>");
        
        // Italic: *text* or _text_
        text = text.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        text = text.replaceAll("_(.+?)_", "<em>$1</em>");
        
        // Inline code: `code`
        text = text.replaceAll("`(.+?)`", "<code style='background:#f4f4f4; padding:2px 4px;'>$1</code>");
        
        return text;
    }
    
    /**
     * Close the dialog if open.
     */
    public static void closeDialog() {
        if (instance != null) {
            instance.hide();
            instance = null;
        }
    }
    
    /**
     * Generate markdown content displaying all tables from current circuit.
     * Includes balance sheets (GodlyTableElm), transaction matrices (CurrentTransactionsMatrixElm),
     * SFC tables (SFCTableElm), regular tables (TableElm), and equation tables (EquationTableElm).
     */
    public static String generateCircuitTablesMarkdown() {
        if (CirSim.theSim == null || CirSim.theSim.elmList == null) {
            return "No circuit loaded.";
        }
        
        StringBuilder md = new StringBuilder();
        md.append("# Circuit Tables Overview\n\n");
        
        // Collect tables - use same pattern as SFCRExporter.categorizeElements()
        java.util.ArrayList<EquationTableElm> equationTables = new java.util.ArrayList<EquationTableElm>();
        java.util.ArrayList<SFCTableElm> sfcTables = new java.util.ArrayList<SFCTableElm>();
        java.util.ArrayList<GodlyTableElm> godlyTables = new java.util.ArrayList<GodlyTableElm>();
        java.util.ArrayList<CurrentTransactionsMatrixElm> ctmTables = new java.util.ArrayList<CurrentTransactionsMatrixElm>();
        java.util.ArrayList<TableElm> otherTables = new java.util.ArrayList<TableElm>();
        
        for (int i = 0; i < CirSim.theSim.elmList.size(); i++) {
            CircuitElm elm = CirSim.theSim.elmList.get(i);
            
            // Check in order: most specific to least specific, using else-if
            if (elm instanceof EquationTableElm) {
                equationTables.add((EquationTableElm) elm);
            } else if (elm instanceof CurrentTransactionsMatrixElm) {
                ctmTables.add((CurrentTransactionsMatrixElm) elm);
            } else if (elm instanceof SFCTableElm) {
                sfcTables.add((SFCTableElm) elm);
            } else if (elm instanceof GodlyTableElm) {
                godlyTables.add((GodlyTableElm) elm);
            } else if (elm instanceof TableElm) {
                otherTables.add((TableElm) elm);
            }
        }
        
        // Format Godly tables (balance sheets)
        if (!godlyTables.isEmpty()) {
            md.append("## Balance Sheets (Godly Tables)\n\n");
            for (GodlyTableElm table : godlyTables) {
                md.append(formatBalanceTable(table));
                md.append("\n");
            }
        }
        
        // Format SFC tables (transaction matrices)
        if (!sfcTables.isEmpty()) {
            md.append("## SFC Transaction Matrices\n\n");
            for (SFCTableElm table : sfcTables) {
                md.append(formatSFCTable(table));
                md.append("\n");
            }
        }
        
        // Format CTM tables  
        if (!ctmTables.isEmpty()) {
            md.append("## Current Transactions Matrices\n\n");
            for (CurrentTransactionsMatrixElm matrix : ctmTables) {
                md.append(formatTransactionMatrix(matrix));
                md.append("\n");
            }
        }
        
        // Format other TableElm instances
        if (!otherTables.isEmpty()) {
            md.append("## Other Tables\n\n");
            for (TableElm table : otherTables) {
                md.append(formatGenericTable(table));
                md.append("\n");
            }
        }
        
        // Format equation tables
        if (!equationTables.isEmpty()) {
            md.append("## Equation Tables\n\n");
            for (EquationTableElm table : equationTables) {
                md.append(formatEquationTable(table));
                md.append("\n");
            }
        }
        
        if (godlyTables.isEmpty() && sfcTables.isEmpty() && ctmTables.isEmpty() && 
            otherTables.isEmpty() && equationTables.isEmpty()) {
            md.append("*No tables found in the current circuit.*\n");
        }
        
        return md.toString();
    }

    /**
     * Generate fenced circuit blocks for each table so the info viewer can mount live table widgets.
     */
    public static String generateCircuitTableBlocksMarkdown() {
        if (CirSim.theSim == null || CirSim.theSim.elmList == null) {
            return "No circuit loaded.";
        }

        java.util.LinkedHashSet<String> tableTitles = new java.util.LinkedHashSet<String>();
        for (int i = 0; i < CirSim.theSim.elmList.size(); i++) {
            CircuitElm elm = CirSim.theSim.elmList.get(i);
            if (elm instanceof TableElm) {
                TableElm table = (TableElm) elm;
                String title = table.getTableTitle();
                if (title == null) {
                    continue;
                }
                String trimmed = title.trim();
                if (!trimmed.isEmpty()) {
                    tableTitles.add(trimmed);
                }
                continue;
            }
            if (elm instanceof EquationTableElm) {
                EquationTableElm eqTable = (EquationTableElm) elm;
                String title = eqTable.getTableName();
                if (title == null) {
                    continue;
                }
                String trimmed = title.trim();
                if (!trimmed.isEmpty()) {
                    tableTitles.add(trimmed);
                }
            }
        }

        if (tableTitles.isEmpty()) {
            return "*No tables found in the current circuit.*";
        }

        StringBuilder md = new StringBuilder();
        md.append("## Circuit Tables\n\n");
        for (String name : tableTitles) {
            md.append("```{circuit}\n");
            md.append("table: ").append(name).append("\n");
            md.append("```\n\n");
        }
        return md.toString();
    }

    /**
     * Generate a starter @info markdown template using current circuit tables and scope plots.
     */
    public static String generateAutoInfoTemplateMarkdown() {
        CirSim sim = CirSim.theSim;
        if (sim == null) {
            return "# Model Information\n\nNo circuit loaded.";
        }

        java.util.LinkedHashSet<String> tableNames = collectCircuitTableNames(sim);
        java.util.ArrayList<String> keyVars = collectPrimaryComputedNames(6);
        java.util.ArrayList<java.util.ArrayList<String>> scopePlotVars = collectScopePlotVariables(sim, 5);

        StringBuilder md = new StringBuilder();
        md.append("# Model Live Dashboard\n\n");
        md.append("> **Time:** {{t}}  \n");
        md.append("> **Step:** {{dt}}\n\n");

        md.append("## Key Variables\n\n");
        if (keyVars.isEmpty()) {
            md.append("- Add live placeholders like `{{Y}}`, `{{C_d}}`, `{{H_h}}`\n\n");
        } else {
            md.append("| Variable | Live value |\n");
            md.append("|---|---:|\n");
            for (String v : keyVars) {
                md.append("| ").append(v).append(" | {{").append(v).append("}} |\n");
            }
            md.append("\n");
        }

        md.append("## Compose Tables\n\n");
        md.append("- Add live tables like\n\n");
        md.append("| Variable | Live value | Meaning |\n");
        md.append("|---|---:|---|\n");
        md.append("| Output | Y = {{Y}} | Aggregate production/income |\n");
        md.append("| Disposable income | YD = {{YD}} | Household disposable income |\n\n");

        md.append("## Tables\n\n");
        if (tableNames.isEmpty()) {
            md.append("- No table elements found in this circuit.\n\n");
        } else {
            for (String tableName : tableNames) {
                md.append("```{circuit}\n");
                md.append("table: ").append(tableName).append("\n");
                md.append("```\n\n");
            }
        }

        md.append("## Plots\n\n");
        if (scopePlotVars.isEmpty()) {
            java.util.ArrayList<String> fallback = collectPrimaryComputedNames(5);
            if (fallback.isEmpty()) {
                md.append("- No scope plots detected. Add a plot block manually.\n\n");
            } else {
                md.append("```{circuit}\n");
                md.append("plot: ").append(joinCsv(fallback)).append("\n");
                md.append("title: Model Dynamics\n");
                md.append("yaxis: Value\n");
                md.append("window: 200\n");
                md.append("```\n\n");
            }
        } else {
            for (int i = 0; i < scopePlotVars.size(); i++) {
                java.util.ArrayList<String> vars = scopePlotVars.get(i);
                String scopeTitle = "Scope " + (i + 1);
                if (sim.scopes != null && i < sim.scopeCount && sim.scopes[i] != null) {
                    String n = sim.scopes[i].getScopeMenuName();
                    if (n != null && !n.trim().isEmpty()) {
                        scopeTitle = n.trim();
                    }
                }
                md.append("```{circuit}\n");
                md.append("plot: ").append(joinCsv(vars)).append("\n");
                md.append("title: ").append(scopeTitle).append("\n");
                md.append("yaxis: Value\n");
                md.append("window: 200\n");
                md.append("```\n\n");
            }
        }

        md.append("## Notes\n\n");
        md.append("- Edit text and equations as needed.\n");
        md.append("- Use `Save to Model` to persist into the circuit @info block.\n");
        return md.toString();
    }

    private static java.util.LinkedHashSet<String> collectCircuitTableNames(CirSim sim) {
        java.util.LinkedHashSet<String> tableNames = new java.util.LinkedHashSet<String>();
        if (sim == null || sim.elmList == null) {
            return tableNames;
        }
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            if (elm instanceof TableElm) {
                String title = ((TableElm) elm).getTableTitle();
                if (title != null) {
                    String t = title.trim();
                    if (!t.isEmpty()) {
                        tableNames.add(t);
                    }
                }
                continue;
            }
            if (elm instanceof EquationTableElm) {
                String title = ((EquationTableElm) elm).getTableName();
                if (title != null) {
                    String t = title.trim();
                    if (!t.isEmpty()) {
                        tableNames.add(t);
                    }
                }
            }
        }
        return tableNames;
    }

    private static java.util.ArrayList<String> collectPrimaryComputedNames(int maxCount) {
        java.util.ArrayList<String> vars = new java.util.ArrayList<String>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<String>();
        String[] names = ComputedValues.getComputedValueNames();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            if (name == null) {
                continue;
            }
            String n = name.trim();
            if (n.isEmpty()) {
                continue;
            }
            if ("t".equalsIgnoreCase(n) || "dt".equalsIgnoreCase(n)) {
                continue;
            }
            if (!seen.add(n)) {
                continue;
            }
            vars.add(n);
            if (vars.size() >= maxCount) {
                break;
            }
        }
        return vars;
    }

    private static java.util.ArrayList<java.util.ArrayList<String>> collectScopePlotVariables(CirSim sim, int maxVarsPerScope) {
        java.util.ArrayList<java.util.ArrayList<String>> result = new java.util.ArrayList<java.util.ArrayList<String>>();
        if (sim == null || sim.scopes == null || sim.scopeCount <= 0) {
            return result;
        }

        java.util.HashSet<String> allVars = new java.util.HashSet<String>();
        String[] names = ComputedValues.getComputedValueNames();
        for (int i = 0; i < names.length; i++) {
            if (names[i] != null) {
                allVars.add(names[i]);
            }
        }

        java.util.ArrayList<String> fallbackVars = collectPrimaryComputedNames(Math.max(1, maxVarsPerScope));

        for (int i = 0; i < sim.scopeCount; i++) {
            Scope scope = sim.scopes[i];
            if (scope == null || !scope.active()) {
                continue;
            }

            String label = scope.getScopeMenuName();
            java.util.LinkedHashSet<String> scopeVars = new java.util.LinkedHashSet<String>();

            if (label != null) {
                int len = label.length();
                int pos = 0;
                while (pos < len && scopeVars.size() < maxVarsPerScope) {
                    while (pos < len) {
                        char ch = label.charAt(pos);
                        if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || ch == '_') {
                            break;
                        }
                        pos++;
                    }
                    if (pos >= len) {
                        break;
                    }
                    int start = pos;
                    pos++;
                    while (pos < len) {
                        char ch = label.charAt(pos);
                        if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
                            pos++;
                        } else {
                            break;
                        }
                    }
                    String token = label.substring(start, pos);
                    if (allVars.contains(token)) {
                        scopeVars.add(token);
                    }
                }
            }

            if (scopeVars.isEmpty()) {
                for (int j = 0; j < fallbackVars.size() && scopeVars.size() < maxVarsPerScope; j++) {
                    scopeVars.add(fallbackVars.get(j));
                }
            }

            if (!scopeVars.isEmpty()) {
                result.add(new java.util.ArrayList<String>(scopeVars));
            }
        }

        return result;
    }

    private static String joinCsv(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }
    
    /**
     * Format a GodlyTableElm as markdown table (balance sheet).
     */
    private static String formatBalanceTable(GodlyTableElm table) {
        StringBuilder md = new StringBuilder();
        
        String title = table.getTableTitle();
        if (title == null || title.isEmpty()) {
            title = "Balance Sheet";
        }
        md.append("### ").append(formatWithGreekAndSubscripts(title)).append("\n\n");
        
        int rows = table.getRows();
        int cols = table.getCols();
        
        if (cols == 0) {
            md.append("*Empty table*\n\n");
            return md.toString();
        }
        
        // Header row
        md.append("| Flow/Stock |");
        for (int col = 0; col < cols; col++) {
            TableColumn column = table.getColumn(col);
            if (column != null && !column.isALE()) {
                String header = column.getStockName();
                if (header == null || header.isEmpty()) {
                    header = "Col" + (col + 1);
                }
                md.append(" ").append(formatWithGreekAndSubscripts(header)).append(" |");
            }
        }
        md.append("\n");
        
        // Separator
        md.append("|------------|");
        for (int col = 0; col < cols; col++) {
            TableColumn column = table.getColumn(col);
            if (column != null && !column.isALE()) {
                md.append("----------|");
            }
        }
        md.append("\n");
        
        // Data rows
        for (int row = 0; row < rows; row++) {
            String rowDesc = table.getRowDescription(row);
            if (rowDesc == null || rowDesc.isEmpty()) {
                rowDesc = "Row " + (row + 1);
            }
            md.append("| ").append(formatWithGreekAndSubscripts(rowDesc)).append(" |");
            
            for (int col = 0; col < cols; col++) {
                TableColumn column = table.getColumn(col);
                if (column != null && !column.isALE()) {
                    String cellEq = column.getCellEquation(row);
                    if (cellEq == null || cellEq.isEmpty()) {
                        cellEq = "0";
                    }
                    // Color negative values red
                    if (cellEq.trim().startsWith("-")) {
                        md.append(" <span style='color:#cf222e'>").append(formatWithGreekAndSubscripts(cellEq)).append("</span> |");
                    } else {
                        md.append(" ").append(formatWithGreekAndSubscripts(cellEq)).append(" |");
                    }
                }
            }
            md.append("\n");
        }
        md.append("\n");
        
        return md.toString();
    }
    
    /**
     * Format a CurrentTransactionsMatrixElm as markdown table.
     */
    private static String formatTransactionMatrix(CurrentTransactionsMatrixElm matrix) {
        StringBuilder md = new StringBuilder();
        
        String title = matrix.getTableTitle();
        if (title == null || title.isEmpty()) {
            title = "Transaction Matrix";
        }
        md.append("### ").append(formatWithGreekAndSubscripts(title)).append("\n\n");
        
        int rows = matrix.getRows();
        int cols = matrix.getCols();
        
        if (cols == 0) {
            md.append("*Empty matrix*\n\n");
            return md.toString();
        }
        
        // Header row
        md.append("| Transaction |");
        for (int col = 0; col < cols; col++) {
            TableColumn column = matrix.getColumn(col);
            if (column != null) {
                String header = column.getStockName();
                if (header == null || header.isEmpty()) {
                    header = "Col" + (col + 1);
                }
                md.append(" ").append(formatWithGreekAndSubscripts(header)).append(" |");
            }
        }
        md.append("\n");
        
        // Separator
        md.append("|-------------|");
        for (int col = 0; col < cols; col++) {
            md.append("----------|");
        }
        md.append("\n");
        
        // Data rows
        for (int row = 0; row < rows; row++) {
            String rowDesc = matrix.getRowDescription(row);
            if (rowDesc == null || rowDesc.isEmpty()) {
                rowDesc = "Row " + (row + 1);
            }
            md.append("| ").append(formatWithGreekAndSubscripts(rowDesc)).append(" |");
            
            for (int col = 0; col < cols; col++) {
                TableColumn column = matrix.getColumn(col);
                if (column != null) {
                    String cellEq = column.getCellEquation(row);
                    if (cellEq == null || cellEq.isEmpty()) {
                        cellEq = "0";
                    }
                    // Color negative values red
                    if (cellEq.trim().startsWith("-")) {
                        md.append(" <span style='color:#cf222e'>").append(formatWithGreekAndSubscripts(cellEq)).append("</span> |");
                    } else {
                        md.append(" ").append(formatWithGreekAndSubscripts(cellEq)).append(" |");
                    }
                }
            }
            md.append("\n");
        }
        md.append("\n");
        
        return md.toString();
    }
    
    /**
     * Format an SFCTableElm as markdown table.
     */
    private static String formatSFCTable(SFCTableElm table) {
        StringBuilder md = new StringBuilder();
        
        String title = table.getTableTitle();
        if (title == null || title.isEmpty()) {
            title = "SFC Table";
        }
        md.append("### ").append(formatWithGreekAndSubscripts(title)).append("\n\n");
        
        int rows = table.getRows();
        int cols = table.getCols();
        
        if (cols == 0) {
            md.append("*Empty table*\n\n");
            return md.toString();
        }
        
        // Header row
        md.append("| Transaction |");
        for (int col = 0; col < cols; col++) {
            TableColumn column = table.getColumn(col);
            if (column != null) {
                String header = column.getStockName();
                if (header == null || header.isEmpty()) {
                    header = "Col" + (col + 1);
                }
                md.append(" ").append(formatWithGreekAndSubscripts(header)).append(" |");
            }
        }
        md.append("\n");
        
        // Separator
        md.append("|-------------|");
        for (int col = 0; col < cols; col++) {
            md.append("----------|");
        }
        md.append("\n");
        
        // Data rows
        for (int row = 0; row < rows; row++) {
            String rowDesc = table.getRowDescription(row);
            if (rowDesc == null || rowDesc.isEmpty()) {
                rowDesc = "Row " + (row + 1);
            }
            md.append("| ").append(formatWithGreekAndSubscripts(rowDesc)).append(" |");
            
            for (int col = 0; col < cols; col++) {
                TableColumn column = table.getColumn(col);
                if (column != null) {
                    String cellEq = column.getCellEquation(row);
                    if (cellEq == null || cellEq.isEmpty()) {
                        cellEq = "0";
                    }
                    // Color negative values red
                    if (cellEq.trim().startsWith("-")) {
                        md.append(" <span style='color:#cf222e'>").append(formatWithGreekAndSubscripts(cellEq)).append("</span> |");
                    } else {
                        md.append(" ").append(formatWithGreekAndSubscripts(cellEq)).append(" |");
                    }
                }
            }
            md.append("\n");
        }
        md.append("\n");
        
        return md.toString();
    }
    
    /**
     * Format a generic TableElm as markdown table.
     */
    private static String formatGenericTable(TableElm table) {
        StringBuilder md = new StringBuilder();
        
        String title = table.getTableTitle();
        if (title == null || title.isEmpty()) {
            title = "Table";
        }
        md.append("### ").append(formatWithGreekAndSubscripts(title)).append("\n\n");
        
        int rows = table.getRows();
        int cols = table.getCols();
        
        if (cols == 0) {
            md.append("*Empty table*\n\n");
            return md.toString();
        }
        
        // Header row
        md.append("| Row |");
        for (int col = 0; col < cols; col++) {
            TableColumn column = table.getColumn(col);
            if (column != null) {
                String header = column.getStockName();
                if (header == null || header.isEmpty()) {
                    header = "Col" + (col + 1);
                }
                md.append(" ").append(formatWithGreekAndSubscripts(header)).append(" |");
            }
        }
        md.append("\n");
        
        // Separator
        md.append("|-----|");
        for (int col = 0; col < cols; col++) {
            md.append("----------|");
        }
        md.append("\n");
        
        // Data rows
        for (int row = 0; row < rows; row++) {
            String rowDesc = table.getRowDescription(row);
            if (rowDesc == null || rowDesc.isEmpty()) {
                rowDesc = "Row " + (row + 1);
            }
            md.append("| ").append(formatWithGreekAndSubscripts(rowDesc)).append(" |");
            
            for (int col = 0; col < cols; col++) {
                TableColumn column = table.getColumn(col);
                if (column != null) {
                    String cellEq = column.getCellEquation(row);
                    if (cellEq == null || cellEq.isEmpty()) {
                        cellEq = "0";
                    }
                    // Color negative values red
                    if (cellEq.trim().startsWith("-")) {
                        md.append(" <span style='color:#cf222e'>").append(formatWithGreekAndSubscripts(cellEq)).append("</span> |");
                    } else {
                        md.append(" ").append(formatWithGreekAndSubscripts(cellEq)).append(" |");
                    }
                }
            }
            md.append("\n");
        }
        md.append("\n");
        
        return md.toString();
    }
    
    /**
     * Format an EquationTableElm as markdown table.
     */
    private static String formatEquationTable(EquationTableElm table) {
        StringBuilder md = new StringBuilder();
        
        String tableName = table.getTableName();
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Equation Table";
        }
        md.append("### ").append(formatWithGreekAndSubscripts(tableName)).append("\n\n");
        
        int rowCount = table.getRowCount();
        
        if (rowCount == 0) {
            md.append("*No equations*\n\n");
            return md.toString();
        }
        
        // Header with three columns: Output, Equation, Hint
        md.append("| Output | Equation | Hint |\n");
        md.append("|:-------|:---------|:-----|\n");
        
        // Rows
        for (int row = 0; row < rowCount; row++) {
            String outputName = table.getOutputName(row);
            String equation = table.getEquation(row);
            
            if (outputName == null || outputName.isEmpty()) {
                outputName = "Out" + (row + 1);
            }
            if (equation == null || equation.isEmpty()) {
                equation = "0";
            }
            
            // Get hint for this output
            String hint = HintRegistry.getHint(outputName);
            if (hint == null || hint.isEmpty()) {
                hint = "";
            }
            
            md.append("| ").append(formatWithGreekAndSubscripts(outputName)).append(" | ");
            
            // Color negative equations red
            if (equation.trim().startsWith("-")) {
                md.append("<span style='color:#cf222e'>")
                  .append(formatWithGreekAndSubscripts(equation)).append("</span>");
            } else {
                md.append(formatWithGreekAndSubscripts(equation));
            }
            
            md.append(" | ").append(formatWithGreekAndSubscripts(hint)).append(" |\n");
        }
        md.append("\n");
        
        return md.toString();
    }
    
    /**
     * Show circuit tables overview in a dialog.
     */
    public static void showCircuitTables() {
        String markdown = generateCircuitTablesMarkdown();
        showInfoInIframe("Circuit Tables", markdown);
    }
}
