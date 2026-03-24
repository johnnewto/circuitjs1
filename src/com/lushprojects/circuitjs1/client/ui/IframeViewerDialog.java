/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client.ui;
import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;

import com.lushprojects.circuitjs1.client.CirSim;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.lushprojects.circuitjs1.client.util.Locale;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

/**
 * Non-modal dialog that displays an iframe with embedded content.
 * The dialog stays on top but doesn't steal focus from the main circuit.
 * Supports loading specific CSS selectors from a page and is resizable.
 * 
 * This can be used to show documentation, tutorials, or secondary circuit views
 * in a floating popup dialog that can be moved around.
 * 
 * Usage example:
 *   IframeViewerDialog.openDialog("Tutorial", "circuitjs.html?startCircuit=demo.txt");
 *   IframeViewerDialog.openUrlWithSelector("Docs", "http://localhost:5217/page.html", ".split-right");
 */
public class IframeViewerDialog extends DialogBox {

    @JsFunction
    private interface EventCallback {
        void handle(EventLike event);
    }

    @JsFunction
    private interface ResizeObserverCallback {
        void handle(ResizeObserverEntries entries);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Event")
    private static class EventLike {
        @JsMethod native void stopPropagation();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "CSSStyleDeclaration")
    private static class StyleLike {
        @JsProperty(name = "display") native void setDisplay(String value);
        @JsProperty(name = "alignItems") native void setAlignItems(String value);
        @JsProperty(name = "justifyContent") native void setJustifyContent(String value);
        @JsProperty(name = "paddingRight") native void setPaddingRight(String value);
        @JsProperty(name = "background") native void setBackground(String value);
        @JsProperty(name = "color") native void setColor(String value);
        @JsProperty(name = "margin") native void setMargin(String value);
        @JsProperty(name = "padding") native void setPadding(String value);
        @JsProperty(name = "fontFamily") native void setFontFamily(String value);
        @JsProperty(name = "overflow") native void setOverflow(String value);
        @JsProperty(name = "width") native void setWidth(String value);
        @JsProperty(name = "height") native void setHeight(String value);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "NodeList")
    private static class NodeListLike {
        @JsProperty(name = "length") native int getLength();
        @JsMethod(name = "item") native ElementLike item(int index);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Element")
    private static class ElementLike {
        @JsProperty(name = "className") native String getClassName();
        @JsProperty(name = "innerHTML") native void setInnerHTML(String value);
        @JsProperty(name = "title") native void setTitle(String value);
        @JsProperty(name = "style") native StyleLike getStyle();
        @JsProperty(name = "onmouseenter") native void setOnMouseEnter(EventCallback callback);
        @JsProperty(name = "onmouseleave") native void setOnMouseLeave(EventCallback callback);
        @JsProperty(name = "onclick") native void setOnClick(EventCallback callback);
        @JsMethod native ElementLike querySelector(String selector);
        @JsMethod native NodeListLike querySelectorAll(String selector);
        @JsMethod native void appendChild(ElementLike child);
        @JsMethod native ElementLike cloneNode(boolean deep);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Document")
    private static class DocumentLike {
        @JsProperty(name = "body") native ElementLike getBody();
        @JsMethod native ElementLike getElementById(String id);
        @JsMethod native ElementLike createElement(String tagName);
        @JsMethod native ElementLike querySelector(String selector);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Window")
    private static class WindowLike {
        @JsProperty(name = "document") native DocumentLike getDocument();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "HTMLIFrameElement")
    private static class IframeElementLike extends ElementLike {
        @JsProperty(name = "onload") native void setOnLoad(EventCallback callback);
        @JsProperty(name = "contentDocument") native DocumentLike getContentDocument();
        @JsProperty(name = "contentWindow") native WindowLike getContentWindow();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "DOMRectReadOnly")
    private static class DomRectLike {
        @JsProperty(name = "height") native double getHeight();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "ResizeObserverEntry")
    private static class ResizeObserverEntryLike {
        @JsProperty(name = "contentRect") native DomRectLike getContentRect();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Array")
    private static class ResizeObserverEntries {
        @JsProperty(name = "length") native int getLength();
        @JsMethod(name = "shift") native ResizeObserverEntryLike shift();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "ResizeObserver")
    private static class ResizeObserverLike {
        public ResizeObserverLike(ResizeObserverCallback callback) {}
        @JsMethod native void observe(ElementLike target);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "window")
    private static class GlobalWindowLike {
        @JsProperty(name = "ResizeObserver") static native Object getResizeObserverCtor();
    }

    @JsProperty(namespace = JsPackage.GLOBAL, name = "document")
    private static native DocumentLike getDocument();

    @JsMethod(namespace = JsPackage.GLOBAL, name = "console.log")
    private static native void consoleLog(String message);
    
    private static IframeViewerDialog instance = null;
    private static final String DIALOG_ID = "iframeViewerDialog";
    
    VerticalPanel vp;
    String iframeSrc;
    String cssSelector;
    int width;
    int height;
    
    /**
     * Open or show the iframe viewer dialog (singleton pattern).
     * If already open, brings it to front.
     * @param title Dialog title
     * @param src URL for the iframe source
     */
    public static void openDialog(String title, String src) {
        openDialog(title, src, 
                   (int)(Window.getClientWidth() * 0.3), 
                   (int)(Window.getClientHeight() * 0.6));
    }
    
    /**
     * Open or show the iframe viewer dialog with specified size.
     * @param title Dialog title
     * @param src URL for the iframe source
     * @param w Width in pixels
     * @param h Height in pixels
     */
    public static void openDialog(String title, String src, int w, int h) {
        openDialogWithSelector(title, src, null, w, h);
    }
    
    /**
     * Open iframe viewer loading only content matching a CSS selector.
     * @param title Dialog title
     * @param src URL for the iframe source
     * @param selector CSS selector (e.g., ".split-right", "#content")
     */
    public static void openUrlWithSelector(String title, String src, String selector) {
        openDialogWithSelector(title, src, selector,
                   (int)(Window.getClientWidth() * 0.3), 
                   (int)(Window.getClientHeight() * 0.6));
    }
    
    /**
     * Open iframe viewer loading only content matching a CSS selector with size.
     * @param title Dialog title
     * @param src URL for the iframe source
     * @param selector CSS selector (e.g., ".split-right", "#content")
     * @param w Width in pixels
     * @param h Height in pixels
     */
    public static void openDialogWithSelector(String title, String src, String selector, int w, int h) {
        if (instance != null) {
            instance.hide();
            instance = null;
        }
        instance = new IframeViewerDialog(title, src, selector, w, h);
    }
    
    /**
     * Check if the dialog is currently open.
     */
    public static boolean isOpen() {
        return instance != null && instance.isShowing();
    }
    
    /**
     * Close the dialog if open.
     */
    public static void closeDialog() {
        if (instance != null) {
            instance.close();
        }
    }
    
    /**
     * Create an iframe viewer dialog with specified size.
     * Non-modal, draggable, resizable, and stays on top.
     * @param title Dialog title
     * @param src URL for the iframe source
     * @param selector CSS selector to extract (null for full page)
     * @param w Width in pixels
     * @param h Height in pixels
     */
    private IframeViewerDialog(String title, String src, String selector, int w, int h) {
        super(true, false); // auto-hide when clicking outside, NOT modal
        
        this.iframeSrc = src;
        this.cssSelector = selector;
        this.width = w;
        this.height = h;
        
        setText(Locale.LS(title));
        setAnimationEnabled(true);
        
        // Set ID for CSS targeting
        getElement().setId(DIALOG_ID);
        
        // Make it stay on top with high z-index and add resize styles
        getElement().getStyle().setZIndex(10000);
        
        vp = new VerticalPanel();
        setWidget(vp);
        vp.setWidth("100%");
        
        // Create iframe with unique ID for manipulation
        String iframeId = "iframeViewerContent";
        String iframeHtml = "<iframe id=\"" + iframeId + "\" " +
                           "src=\"" + escapeHtml(src) + "\" " +
                           "width=\"100%\" " +
                           "height=\"" + h + "\" " +
                           "frameborder=\"0\" " +
                           "style=\"border: none; border-radius: 0 0 4px 4px; display: block;\" " +
                           "title=\"Embedded Content\">" +
                           "</iframe>";
        
        // Wrap in a resizable container
        String containerHtml = "<div id=\"iframeContainer\" style=\"" +
                              "width: " + w + "px; " +
                              "height: " + h + "px; " +
                              "resize: both; " +
                              "overflow: hidden; " +
                              "min-width: 150px; " +
                              "min-height: 100px; " +
                              "max-width: 90vw; " +
                              "max-height: 90vh; " +
                              "border: 2px solid #888; " +
                              "border-radius: 4px; " +
                              "background: #fff;\">" +
                              iframeHtml +
                              "</div>";
        
        vp.add(new HTML(containerHtml));
        
        // Position at left side of screen
        setPopupPosition(20, 60);
        show();
        
        // Add close button to title bar
        addCloseButtonToTitleBar();
        
        // If selector specified, set up content extraction after iframe loads
        if (selector != null && !selector.isEmpty()) {
            setupSelectorExtraction(iframeId, selector);
        }
        
        // Set up resize observer to sync iframe size with container
        setupResizeObserver();
    }
    
    /**
     * Add a close icon button to the right side of the title bar.
     */
    private void addCloseButtonToTitleBar() {
        ElementLike dialog = getDocument().getElementById(DIALOG_ID);
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

        if (caption == null)
            return;

        caption.getStyle().setDisplay("flex");
        caption.getStyle().setAlignItems("center");
        caption.getStyle().setJustifyContent("space-between");
        caption.getStyle().setPaddingRight("4px");

        final ElementLike closeBtn = getDocument().createElement("span");
        closeBtn.setInnerHTML("&#x2715;");
        closeBtn.setTitle("Close");
        closeBtn.getStyle().setPadding("2px 6px");
        closeBtn.getStyle().setMargin("0 0 0 10px");
        closeBtn.getStyle().setColor("#666");

        closeBtn.setOnMouseEnter(new EventCallback() {
            public void handle(EventLike event) {
                closeBtn.getStyle().setBackground("#ff4444");
                closeBtn.getStyle().setColor("white");
            }
        });
        closeBtn.setOnMouseLeave(new EventCallback() {
            public void handle(EventLike event) {
                closeBtn.getStyle().setBackground("transparent");
                closeBtn.getStyle().setColor("#666");
            }
        });
        closeBtn.setOnClick(new EventCallback() {
            public void handle(EventLike event) {
                if (event != null)
                    event.stopPropagation();
                close();
            }
        });

        caption.appendChild(closeBtn);
    }
    /**
     * Set up JavaScript to extract content matching selector from iframe.
     */
    private void setupSelectorExtraction(String iframeId, final String selector) {
        final IframeElementLike iframe = (IframeElementLike) getDocument().getElementById(iframeId);
        if (iframe == null)
            return;

        iframe.setOnLoad(new EventCallback() {
            public void handle(EventLike event) {
                try {
                    DocumentLike iframeDoc = iframe.getContentDocument();
                    if (iframeDoc == null && iframe.getContentWindow() != null)
                        iframeDoc = iframe.getContentWindow().getDocument();
                    if (iframeDoc == null)
                        return;
                    ElementLike selectedContent = iframeDoc.querySelector(selector);
                    if (selectedContent == null)
                        return;
                    iframeDoc.getBody().setInnerHTML("");
                    iframeDoc.getBody().appendChild(selectedContent.cloneNode(true));
                    iframeDoc.getBody().getStyle().setMargin("0");
                    iframeDoc.getBody().getStyle().setPadding("16px");
                    iframeDoc.getBody().getStyle().setFontFamily("system-ui, -apple-system, sans-serif");
                    iframeDoc.getBody().getStyle().setOverflow("auto");
                } catch (Throwable e) {
                    consoleLog("Cannot extract selector - cross-origin restriction");
                }
            }
        });
    }
    
    /**
     * Set up resize observer to sync iframe size when container is resized.
     */
    private void setupResizeObserver() {
        final ElementLike container = getDocument().getElementById("iframeContainer");
        final ElementLike iframe = getDocument().getElementById("iframeViewerContent");
        if (container == null || iframe == null || GlobalWindowLike.getResizeObserverCtor() == null)
            return;

        ResizeObserverLike observer = new ResizeObserverLike(new ResizeObserverCallback() {
            public void handle(ResizeObserverEntries entries) {
                try {
                    if (entries == null || entries.getLength() < 1)
                        return;
                    ResizeObserverEntryLike entry = entries.shift();
                    if (entry == null || entry.getContentRect() == null)
                        return;
                    iframe.getStyle().setWidth("100%");
                    iframe.getStyle().setHeight(entry.getContentRect().getHeight() + "px");
                } catch (Throwable e) {
                    consoleLog("ResizeObserver callback failed: " + e);
                }
            }
        });
        observer.observe(container);
    }
    
    /**
     * Open an iframe viewer with a circuit file.
     * @param title Dialog title
     * @param circuitFile Circuit filename to load
     * @param editable Whether the circuit should be editable
     * @param w Width in pixels
     * @param h Height in pixels
     */
    public static void openCircuit(String title, String circuitFile, boolean editable, int w, int h) {
        String src = "circuitjs.html?startCircuit=" + circuitFile + "&editable=" + editable;
        src = addCacheBustParamIfEnabled(src);
        openDialog(title, src, w, h);
    }
    
    /**
     * Open an iframe viewer with a circuit file using default size.
     * @param title Dialog title
     * @param circuitFile Circuit filename to load
     * @param editable Whether the circuit should be editable
     */
    public static void openCircuit(String title, String circuitFile, boolean editable) {
        String src = "circuitjs.html?startCircuit=" + circuitFile + "&editable=" + editable;
        src = addCacheBustParamIfEnabled(src);
        openDialog(title, src);
    }
    
    /**
     * Open an iframe viewer with full circuit URL (including ctz parameter).
     * @param title Dialog title  
     * @param circuitUrl Full URL including parameters
     */
    public static void openCircuitUrl(String title, String circuitUrl) {
        openDialog(title, addCacheBustParamIfEnabled(circuitUrl));
    }

    private static String addCacheBustParamIfEnabled(String url) {
        if (url == null)
            return null;
        if (CirSim.getInstance() == null || !CirSim.getInstance().isCacheBustedUrlsEnabled())
            return url;
        return url + (url.indexOf('?') >= 0 ? "&" : "?") + "v=" + System.currentTimeMillis();
    }
    
    /**
     * Open an iframe viewer with any URL (external documentation, etc.).
     * @param title Dialog title  
     * @param url Full URL to display
     */
    public static void openUrl(String title, String url) {
        openDialog(title, url);
    }
    
    /**
     * Open an iframe viewer with any URL and specified size.
     * @param title Dialog title  
     * @param url Full URL to display
     * @param w Width in pixels
     * @param h Height in pixels
     */
    public static void openUrl(String title, String url, int w, int h) {
        openDialog(title, url, w, h);
    }
    
    /**
     * Simple HTML escaping for the src attribute.
     */
    private String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
    
    public void close() {
        hide();
        instance = null;
    }
}
