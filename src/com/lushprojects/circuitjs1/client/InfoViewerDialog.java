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
    private static boolean currentLoadConstructsInCodeBlocks = true;
    private static boolean currentRenderSfcrConstructTables = true;
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
        String rawMarkdown = normalizeMarkdown(markdown);
        String displayMarkdown = deriveDisplayMarkdown(title, rawMarkdown, appendCircuitTables);

        currentTitle = title;
        currentRawMarkdown = rawMarkdown;
        currentMarkdown = displayMarkdown;
        currentAppendCircuitTables = appendCircuitTables;
        lastLiveUpdateMs = 0;

        installViewerMessageBridge();
        String html = InfoViewerHtmlBuilder.generateMarkdownViewerHTML(
            title,
            displayMarkdown,
            rawMarkdown,
            isModelInfoTitle(title),
            currentLoadConstructsInCodeBlocks,
            currentRenderSfcrConstructTables
        );
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
        String rawMarkdown = normalizeMarkdown(markdown);
        String displayMarkdown = deriveDisplayMarkdown(title, rawMarkdown, appendCircuitTables);

        currentTitle = title;
        currentRawMarkdown = rawMarkdown;
        currentMarkdown = displayMarkdown;
        currentAppendCircuitTables = appendCircuitTables;
        lastLiveUpdateMs = 0;
        
        installViewerMessageBridge();
        String html = InfoViewerHtmlBuilder.generateMarkdownViewerHTML(
            title,
            displayMarkdown,
            rawMarkdown,
            isModelInfoTitle(title),
            currentLoadConstructsInCodeBlocks,
            currentRenderSfcrConstructTables
        );
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

    private static String normalizeMarkdown(String markdown) {
        return (markdown == null) ? "" : markdown;
    }

    private static boolean isUsableTablesMarkdown(String s) {
        return s != null && !s.isEmpty()
            && !s.equals("No circuit loaded.")
            && !s.contains("*No tables found");
    }

    private static String deriveDisplayMarkdown(String title, String rawMarkdown, boolean appendCircuitTables) {
        String baseMarkdown = rawMarkdown;
        if (isModelInfoTitle(title) && SFCRParser.isSFCRFormat(rawMarkdown)) {
            baseMarkdown = InfoViewerContentBuilder.buildModelInfoMarkdown(rawMarkdown, "");
        }
        return buildDisplayMarkdown(baseMarkdown, appendCircuitTables);
    }

    private static String buildDisplayMarkdown(String markdown, boolean appendCircuitTables) {
        String displayMarkdown = (markdown == null) ? "" : markdown;
        if (!appendCircuitTables) {
            return displayMarkdown;
        }
        String tablesMarkdown = appendCircuitTablesAsBlocks
            ? generateCircuitTableBlocksMarkdown()
            : generateCircuitTablesMarkdown();
        if (!isUsableTablesMarkdown(tablesMarkdown)) {
            return displayMarkdown;
        }
        if (displayMarkdown.trim().isEmpty()) {
            return tablesMarkdown;
        }
        return displayMarkdown + "\n\n---\n\n" + tablesMarkdown;
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
        String normalized = normalizeMarkdown(markdown);
        currentRawMarkdown = normalized;
        currentMarkdown = deriveDisplayMarkdown(currentTitle, normalized, currentAppendCircuitTables);

        if (CirSim.theSim != null && isModelInfoTitle(currentTitle)) {
            if (SFCRParser.isSFCRFormat(normalized)) {
                CirSim.theSim.importCircuitFromText(normalized, false);
                return;
            }
            CirSim.theSim.modelInfoContent = normalized;
            CirSim.theSim.modelInfoSourceText = null;
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
        } else if ("stop".equals(command)) {
            CirSim.theSim.setSimRunning(false);
        } else if ("reset".equals(command)) {
            CirSim.theSim.resetAction();
        } else if ("step".equals(command)) {
            CirSim.theSim.stepCircuit();
        }
    }

    public static void handleViewerOptionChanged(String key, boolean enabled) {
        if (key == null) {
            return;
        }
        if ("parse-code-fence-constructs".equals(key)) {
            currentLoadConstructsInCodeBlocks = enabled;
            return;
        }
        if ("render-sfcr-construct-tables".equals(key)) {
            currentRenderSfcrConstructTables = enabled;
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

        String json = InfoViewerLiveDataSerializer.buildLiveDataJson(CirSim.theSim);
        if (json == null || json.isEmpty()) {
            return;
        }

        lastLiveUpdateMs = now;
        postLiveDataToViewers(json);
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
                return;
            }
            if (data.type === 'info-viewer-option-changed') {
                @com.lushprojects.circuitjs1.client.InfoViewerDialog::handleViewerOptionChanged(Ljava/lang/String;Z)(data.key || '', !!data.enabled);
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
        return InfoViewerHtmlBuilder.generateMarkdownViewerHTMLPublic(title, markdown);
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
        String simpleHtml = InfoViewerSimpleMarkdown.convert(markdown);
        
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
        return InfoViewerTableMarkdown.generateCircuitTablesMarkdown();
    }

    /**
     * Generate fenced circuit blocks for each table so the info viewer can mount live table widgets.
     */
    public static String generateCircuitTableBlocksMarkdown() {
        return InfoViewerTableMarkdown.generateCircuitTableBlocksMarkdown();
    }

    /**
     * Generate a starter @info markdown template using current circuit tables and scope plots.
     */
    public static String generateAutoInfoTemplateMarkdown() {
        return InfoViewerTableMarkdown.generateAutoInfoTemplateMarkdown();
    }
    
    /**
     * Show circuit tables overview in a dialog.
     */
    public static void showCircuitTables() {
        String markdown = generateCircuitTablesMarkdown();
        showInfoInIframe("Circuit Tables", markdown);
    }
}
