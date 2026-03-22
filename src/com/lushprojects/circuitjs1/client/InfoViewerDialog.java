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
import com.google.gwt.user.client.Window;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

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

    @JsFunction
    private interface MessageEventHandler {
        void handle(MessageEventLike event);
    }

    @JsFunction
    private interface TimeoutCallback {
        void run();
    }

    @JsFunction
    private interface ElementEventHandler {
        void handle(EventLike event);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
    private static class JsObjectLike {
        @JsProperty(name = "type") native String getType();
        @JsProperty(name = "markdown") native String getMarkdown();
        @JsProperty(name = "command") native String getCommand();
        @JsProperty(name = "key") native String getKey();
        @JsProperty(name = "enabled") native boolean isEnabled();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "MessageEvent")
    private static class MessageEventLike {
        @JsProperty(name = "data") native JsObjectLike getData();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Event")
    private static class EventLike {
        @JsMethod native void stopPropagation();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "CSSStyleDeclaration")
    private static class StyleLike {
        @JsProperty(name = "background") native void setBackground(String value);
        @JsProperty(name = "color") native void setColor(String value);
        @JsProperty(name = "padding") native void setPadding(String value);
        @JsProperty(name = "margin") native void setMargin(String value);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "NodeList")
    private static class NodeListLike {
        @JsProperty(name = "length") native int getLength();
        @JsMethod(name = "item") native ElementLike item(int index);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Element")
    private static class ElementLike {
        @JsProperty(name = "className") native String getClassName();
        @JsProperty(name = "className") native void setClassName(String value);
        @JsProperty(name = "innerHTML") native void setInnerHTML(String value);
        @JsProperty(name = "title") native void setTitle(String value);
        @JsProperty(name = "style") native StyleLike getStyle();
        @JsProperty(name = "onmouseenter") native void setOnMouseEnter(ElementEventHandler handler);
        @JsProperty(name = "onmouseleave") native void setOnMouseLeave(ElementEventHandler handler);
        @JsProperty(name = "onclick") native void setOnClick(ElementEventHandler handler);
        @JsMethod native ElementLike querySelector(String selector);
        @JsMethod native NodeListLike querySelectorAll(String selector);
        @JsMethod native void appendChild(ElementLike child);
        @JsMethod native void insertBefore(ElementLike node, ElementLike child);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Document")
    private static class DocumentLike {
        @JsMethod native void open();
        @JsMethod native void write(String text);
        @JsMethod native void close();
        @JsMethod native ElementLike getElementById(String id);
        @JsMethod native ElementLike createElement(String tagName);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Window")
    private static class WindowLike {
        @JsProperty(name = "closed") native boolean isClosed();
        @JsMethod native void postMessage(Object message, String targetOrigin);
        @JsMethod native void focus();
        @JsMethod native void blur();
        @JsProperty(name = "document") native DocumentLike getDocument();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "HTMLIFrameElement")
    private static class IframeLike extends ElementLike {
        @JsProperty(name = "contentWindow") native WindowLike getContentWindow();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "window")
    private static class GlobalWindowLike {
        @JsMethod(name = "open") static native WindowLike open(String url, String target, String features);
        @JsMethod(name = "addEventListener") static native void addEventListener(String type, MessageEventHandler handler);
        @JsMethod(name = "setTimeout") static native void setTimeout(TimeoutCallback cb, int ms);
        @JsProperty(name = "_modelInfoWindow") static native WindowLike getModelInfoWindow();
        @JsProperty(name = "_modelInfoWindow") static native void setModelInfoWindow(WindowLike w);
        @JsProperty(name = "__circuitInfoViewerBridgeInstalled") static native boolean isBridgeInstalled();
        @JsProperty(name = "__circuitInfoViewerBridgeInstalled") static native void setBridgeInstalled(boolean v);
    }

    @JsProperty(namespace = JsPackage.GLOBAL, name = "document")
    private static native DocumentLike getDocument();

    @JsMethod(namespace = JsPackage.GLOBAL, name = "JSON.parse")
    private static native Object parseJson(String jsonData);

    @JsMethod(namespace = JsPackage.GLOBAL, name = "encodeURIComponent")
    private static native String encodeURIComponent(String text);
    
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
                CirSim.theSim.getImportExportHelper().importCircuitFromText(normalized, false);
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

    private static boolean hasActiveViewer() {
        WindowLike popup = GlobalWindowLike.getModelInfoWindow();
        boolean popupOpen = popup != null && !popup.isClosed();
        IframeLike iframe = (IframeLike) getDocument().getElementById("iframeViewerContent");
        boolean iframeOpen = iframe != null && iframe.getContentWindow() != null;
        return popupOpen || iframeOpen;
    }

    private static void postLiveDataToViewers(String jsonData) {
        Object payload;
        try {
            payload = parseJson(jsonData);
        } catch (Throwable e) {
            return;
        }

        WindowLike popup = GlobalWindowLike.getModelInfoWindow();
        if (popup != null && !popup.isClosed()) {
            try {
                popup.postMessage(payload, "*");
            } catch (Throwable e1) {}
        }

        IframeLike iframe = (IframeLike) getDocument().getElementById("iframeViewerContent");
        if (iframe != null && iframe.getContentWindow() != null) {
            try {
                iframe.getContentWindow().postMessage(payload, "*");
            } catch (Throwable e2) {}
        }
    }

    private static void installViewerMessageBridge() {
        if (GlobalWindowLike.isBridgeInstalled())
            return;
        GlobalWindowLike.setBridgeInstalled(true);
        GlobalWindowLike.addEventListener("message", new MessageEventHandler() {
            public void handle(MessageEventLike event) {
                JsObjectLike data = event == null ? null : event.getData();
                if (data == null || data.getType() == null)
                    return;
                String type = data.getType();
                if ("info-viewer-save-markdown".equals(type)) {
                    saveEditedMarkdown(data.getMarkdown() == null ? "" : data.getMarkdown());
                    return;
                }
                if ("info-viewer-sim-command".equals(type)) {
                    handleSimulationCommand(data.getCommand() == null ? "" : data.getCommand());
                    return;
                }
                if ("info-viewer-option-changed".equals(type)) {
                    handleViewerOptionChanged(data.getKey() == null ? "" : data.getKey(), data.isEnabled());
                }
            }
        });
    }
    
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
    private static void addOpenInWindowButton() {
        GlobalWindowLike.setTimeout(new TimeoutCallback() {
            public void run() {
                ElementLike dialog = getDocument().getElementById("iframeViewerDialog");
                if (dialog == null)
                    return;

                ElementLike caption = dialog.querySelector(".Caption");
                if (caption == null)
                    caption = dialog.querySelector("td.Caption");
                if (caption == null) {
                    NodeListLike cells = dialog.querySelectorAll("td");
                    for (int i = 0; i < cells.getLength(); i++) {
                        ElementLike cell = cells.item(i);
                        String className = cell.getClassName();
                        if (className != null && className.indexOf("Caption") >= 0) {
                            caption = cell;
                            break;
                        }
                    }
                }

                if (caption == null || caption.querySelector(".open-in-window-btn") != null)
                    return;

                final ElementLike openBtn = getDocument().createElement("span");
                openBtn.setClassName("open-in-window-btn");
                openBtn.setInnerHTML("&#x2197;");
                openBtn.setTitle("Open in New Window");
                openBtn.getStyle().setPadding("2px 6px");
                openBtn.getStyle().setMargin("0 0 0 10px");
                openBtn.getStyle().setColor("#666");

                openBtn.setOnMouseEnter(new ElementEventHandler() {
                    public void handle(EventLike event) {
                        openBtn.getStyle().setBackground("#4488ff");
                        openBtn.getStyle().setColor("white");
                    }
                });
                openBtn.setOnMouseLeave(new ElementEventHandler() {
                    public void handle(EventLike event) {
                        openBtn.getStyle().setBackground("transparent");
                        openBtn.getStyle().setColor("#666");
                    }
                });
                openBtn.setOnClick(new ElementEventHandler() {
                    public void handle(EventLike event) {
                        if (event != null)
                            event.stopPropagation();
                        openCurrentInWindowAndCloseDialog();
                    }
                });

                ElementLike closeBtn = caption.querySelector("span:last-child");
                if (closeBtn != null)
                    caption.insertBefore(openBtn, closeBtn);
                else
                    caption.appendChild(openBtn);
            }
        }, 100);
    }
    
    /**
     * Create a data URL from HTML content.
     */
    private static String createDataUrl(String html) {
        return "data:text/html;charset=utf-8," + encodeURIComponent(html);
    }
    
    /**
     * Open a new window with HTML content.
     * Reuses the same window if already open to avoid multiple popups.
     * Uses blur/focus trick to bring window to front in Chrome.
     */
    private static boolean openWindowWithHTML(String html) {
        String windowName = "circuitjs_model_info";
        WindowLike newWindow = GlobalWindowLike.open("", windowName, "width=800,height=600");
        if (newWindow == null) {
            Window.alert("Please allow pop-ups for this site to view documentation.");
            return false;
        }
        GlobalWindowLike.setModelInfoWindow(newWindow);
        newWindow.getDocument().open();
        newWindow.getDocument().write(html);
        newWindow.getDocument().close();
        newWindow.focus();
        GlobalWindowLike.setTimeout(new TimeoutCallback() {
            public void run() {
                WindowLike modelWindow = GlobalWindowLike.getModelInfoWindow();
                if (modelWindow != null && !modelWindow.isClosed())
                    modelWindow.focus();
            }
        }, 100);
        return true;
    }
    
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
