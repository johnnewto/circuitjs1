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

package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.lushprojects.circuitjs1.client.util.Locale;

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
        
        // Position at right side of screen
        setPopupPosition(Window.getClientWidth() - w - 40, 60);
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
    private native void addCloseButtonToTitleBar() /*-{
        var dialog = $doc.getElementById('iframeViewerDialog');
        if (!dialog) return;
        
        // Find the caption/title bar element
        var caption = dialog.querySelector('.Caption');
        if (!caption) {
            // Try alternate selector for DialogBox caption
            caption = dialog.querySelector('td.Caption');
        }
        if (!caption) {
            // Last resort - first child table cell
            var cells = dialog.querySelectorAll('td');
            for (var i = 0; i < cells.length; i++) {
                if (cells[i].className && cells[i].className.indexOf('Caption') >= 0) {
                    caption = cells[i];
                    break;
                }
            }
        }
        
        if (caption) {
            // Make caption a flex container
            caption.style.display = 'flex';
            caption.style.alignItems = 'center';
            caption.style.justifyContent = 'space-between';
            caption.style.paddingRight = '4px';
            
            // Create close button
            var closeBtn = $doc.createElement('span');
            closeBtn.innerHTML = '&#x2715;'; // Unicode X
            closeBtn.style.cssText = 'cursor: pointer; font-size: 16px; font-weight: bold; ' +
                                     'padding: 2px 6px; margin-left: 10px; border-radius: 4px; ' +
                                     'color: #666; transition: all 0.2s;';
            closeBtn.title = 'Close';
            
            // Hover effects
            closeBtn.onmouseenter = function() {
                this.style.background = '#ff4444';
                this.style.color = 'white';
            };
            closeBtn.onmouseleave = function() {
                this.style.background = 'transparent';
                this.style.color = '#666';
            };
            
            // Click handler
            var self = this;
            closeBtn.onclick = function(e) {
                e.stopPropagation();
                self.@com.lushprojects.circuitjs1.client.IframeViewerDialog::close()();
            };
            
            caption.appendChild(closeBtn);
        }
    }-*/;
    /**
     * Set up JavaScript to extract content matching selector from iframe.
     */
    private native void setupSelectorExtraction(String iframeId, String selector) /*-{
        var iframe = $doc.getElementById(iframeId);
        if (iframe) {
            iframe.onload = function() {
                try {
                    var iframeDoc = iframe.contentDocument || iframe.contentWindow.document;
                    var selectedContent = iframeDoc.querySelector(selector);
                    if (selectedContent) {
                        // Replace iframe body with just the selected content
                        iframeDoc.body.innerHTML = '';
                        iframeDoc.body.appendChild(selectedContent.cloneNode(true));
                        // Add some basic styling
                        iframeDoc.body.style.margin = '0';
                        iframeDoc.body.style.padding = '16px';
                        iframeDoc.body.style.fontFamily = 'system-ui, -apple-system, sans-serif';
                        iframeDoc.body.style.overflow = 'auto';
                    }
                } catch(e) {
                    // Cross-origin restriction - can't access iframe content
                    console.log('Cannot extract selector - cross-origin restriction');
                }
            };
        }
    }-*/;
    
    /**
     * Set up resize observer to sync iframe size when container is resized.
     */
    private native void setupResizeObserver() /*-{
        var container = $doc.getElementById('iframeContainer');
        var iframe = $doc.getElementById('iframeViewerContent');
        if (container && iframe && $wnd.ResizeObserver) {
            var observer = new $wnd.ResizeObserver(function(entries) {
                if (entries && entries.length > 0) {
                    var entry = entries[0];
                    iframe.style.width = '100%';
                    iframe.style.height = entry.contentRect.height + 'px';
                }
            });
            observer.observe(container);
        }
    }-*/;
    
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
        openDialog(title, src);
    }
    
    /**
     * Open an iframe viewer with full circuit URL (including ctz parameter).
     * @param title Dialog title  
     * @param circuitUrl Full URL including parameters
     */
    public static void openCircuitUrl(String title, String circuitUrl) {
        openDialog(title, circuitUrl);
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
