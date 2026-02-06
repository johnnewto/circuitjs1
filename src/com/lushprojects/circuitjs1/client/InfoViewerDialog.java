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
import com.google.gwt.user.client.Window;
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
    private static String currentTitle = null;
    
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
        String html = generateMarkdownViewerHTML(title, markdown);
        openWindowWithHTML(html);
    }
    
    /**
     * Show info using IframeViewerDialog with data URL.
     * Good for embedded display with full markdown support.
     * Includes button to open in new window.
     */
    public static void showInfoInIframe(String title, String markdown) {
        // Store for later use by "Open in Window" button
        currentTitle = title;
        currentMarkdown = markdown;
        
        String html = generateMarkdownViewerHTML(title, markdown);
        String dataUrl = createDataUrl(html);
        IframeViewerDialog.openDialog(title, dataUrl, 600, 500);
        
        // Add "Open in Window" button to the dialog title bar
        addOpenInWindowButton();
    }
    
    /**
     * Called from JavaScript to open current content in new window.
     */
    public static void openCurrentInWindow() {
        if (currentMarkdown != null && currentTitle != null) {
            showInfoInWindow(currentTitle, currentMarkdown);
        }
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
     * Generate complete HTML with marked.js for rendering markdown.
     */
    private static String generateMarkdownViewerHTML(String title, String markdown) {
        StringBuilder html = new StringBuilder();
        
        // Escape the markdown for embedding in JavaScript
        String escapedMarkdown = escapeForJS(markdown);
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>").append(escapeHtml(title)).append("</title>\n");
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/marked/marked.min.js\"></script>\n");
        html.append("  <style>\n");
        html.append("    body { \n");
        html.append("      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;\n");
        html.append("      margin: 0; padding: 20px; background: #f8f9fa; color: #333;\n");
        html.append("      line-height: 1.6;\n");
        html.append("    }\n");
        html.append("    .container { max-width: 900px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }\n");
        html.append("    h1 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; margin-top: 0; }\n");
        html.append("    h2 { color: #34495e; margin-top: 30px; }\n");
        html.append("    h3 { color: #7f8c8d; }\n");
        html.append("    code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; font-family: 'Consolas', 'Monaco', monospace; }\n");
        html.append("    pre { background: #2c3e50; color: #ecf0f1; padding: 15px; border-radius: 5px; overflow-x: auto; }\n");
        html.append("    pre code { background: none; padding: 0; color: inherit; }\n");
        html.append("    table { border-collapse: collapse; width: 100%; margin: 15px 0; }\n");
        html.append("    th, td { border: 1px solid #ddd; padding: 10px; text-align: left; }\n");
        html.append("    th { background: #3498db; color: white; }\n");
        html.append("    tr:nth-child(even) { background: #f9f9f9; }\n");
        html.append("    blockquote { border-left: 4px solid #3498db; margin: 15px 0; padding: 10px 20px; background: #f8f9fa; }\n");
        html.append("    a { color: #3498db; text-decoration: none; }\n");
        html.append("    a:hover { text-decoration: underline; }\n");
        html.append("    ul, ol { padding-left: 25px; }\n");
        html.append("    li { margin: 5px 0; }\n");
        html.append("    .variable { font-family: 'Consolas', monospace; background: #e8f4f8; padding: 1px 4px; border-radius: 2px; }\n");
        html.append("    .hint-table { font-size: 14px; }\n");
        html.append("    .hint-table td:first-child { font-weight: bold; width: 120px; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container\">\n");
        html.append("    <div id=\"content\">Loading...</div>\n");
        html.append("  </div>\n");
        html.append("  <script>\n");
        html.append("    var markdown = \"").append(escapedMarkdown).append("\";\n");
        html.append("    document.getElementById('content').innerHTML = marked.parse(markdown);\n");
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
                html.append("<h3>").append(escapeHtml(line.substring(4))).append("</h3>");
                continue;
            }
            if (line.startsWith("## ")) {
                html.append("<h2>").append(escapeHtml(line.substring(3))).append("</h2>");
                continue;
            }
            if (line.startsWith("# ")) {
                html.append("<h1>").append(escapeHtml(line.substring(2))).append("</h1>");
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
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
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
     */
    private String formatInline(String text) {
        text = escapeHtml(text);
        
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
}
