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
        // Append circuit tables if requested
        if (appendCircuitTables) {
            String tablesMarkdown = generateCircuitTablesMarkdown();
            if (tablesMarkdown != null && !tablesMarkdown.isEmpty()) {
                // Add separator if there's existing content
                if (markdown != null && !markdown.trim().isEmpty() && 
                    !tablesMarkdown.equals("No circuit loaded.") &&
                    !tablesMarkdown.contains("*No tables found")) {
                    markdown = markdown + "\n\n---\n\n" + tablesMarkdown;
                } else if (markdown == null || markdown.trim().isEmpty()) {
                    markdown = tablesMarkdown;
                }
            }
        }
        
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
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/html2canvas@1.4.1/dist/html2canvas.min.js\"></script>\n");
        // Add KaTeX for math rendering
        html.append("  <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css\">\n");
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js\"></script>\n");
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js\"></script>\n");
        html.append("  <style>\n");
        html.append("    body { \n");
        html.append("      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;\n");
        html.append("      margin: 0; padding: 16px; background: #fafbfc; color: #24292f; font-size: 13px;\n");
        html.append("      line-height: 1.4;\n");
        html.append("    }\n");
        html.append("    .container { max-width: 1100px; margin: 0 auto; background: white; padding: 20px; border-radius: 6px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }\n");
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
        html.append("    /* Page break handling for PDF and PNG */\n");
        html.append("    h2, h3 { page-break-after: avoid; break-after: avoid; }\n");
        html.append("    table { page-break-inside: avoid; break-inside: avoid; }\n");
        html.append("    .table-section { page-break-inside: avoid; break-inside: avoid; margin-bottom: 20px; }\n");
        html.append("    @media print { \n");
        html.append("      .export-buttons { display: none; }\n");
        html.append("      body { background: white; }\n");
        html.append("      .container { box-shadow: none; max-width: 100%; padding: 10px; }\n");
        html.append("      h2, h3 { page-break-after: avoid; break-after: avoid; }\n");
        html.append("      table { page-break-inside: avoid; break-inside: avoid; }\n");
        html.append("      tr { page-break-inside: avoid; break-inside: avoid; }\n");
        html.append("    }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container\">\n");
        html.append("    <div class=\"export-buttons\">\n");
        html.append("      <button class=\"export-btn\" onclick=\"saveToPDF()\">📄 Save as PDF</button>\n");
        html.append("      <button class=\"export-btn secondary\" onclick=\"saveToPNG()\">🖼️ Save as PNG</button>\n");
        html.append("    </div>\n");
        html.append("    <div id=\"content\">Loading...</div>\n");
        html.append("  </div>\n");
        html.append("  <script>\n");
        html.append("    var markdown = \"").append(escapedMarkdown).append("\";\n");
        html.append("    document.getElementById('content').innerHTML = marked.parse(markdown);\n");
        html.append("    \n");
        html.append("    // Render LaTeX math equations with KaTeX\n");
        html.append("    renderMathInElement(document.getElementById('content'), {\n");
        html.append("      delimiters: [\n");
        html.append("        {left: '$$', right: '$$', display: true},\n");
        html.append("        {left: '$', right: '$', display: false},\n");
        html.append("        {left: '\\\\[', right: '\\\\]', display: true},\n");
        html.append("        {left: '\\\\(', right: '\\\\)', display: false}\n");
        html.append("      ],\n");
        html.append("      throwOnError: false\n");
        html.append("    });\n");
        html.append("    \n");
        html.append("    // Wrap tables with preceding headers in sections for better page breaks\n");
        html.append("    const content = document.getElementById('content');\n");
        html.append("    const children = Array.from(content.children);\n");
        html.append("    let currentSection = null;\n");
        html.append("    let lastH3 = null;\n");
        html.append("    children.forEach((child, idx) => {\n");
        html.append("      if (child.tagName === 'H3') {\n");
        html.append("        // Remember this h3 for upcoming table\n");
        html.append("        lastH3 = child;\n");
        html.append("        currentSection = null;\n");
        html.append("      } else if (child.tagName === 'TABLE') {\n");
        html.append("        // Create section with preceding h3 (if any)\n");
        html.append("        currentSection = document.createElement('div');\n");
        html.append("        currentSection.className = 'table-section';\n");
        html.append("        child.parentNode.insertBefore(currentSection, lastH3 || child);\n");
        html.append("        if (lastH3) currentSection.appendChild(lastH3);\n");
        html.append("        currentSection.appendChild(child);\n");
        html.append("        lastH3 = null;\n");
        html.append("        currentSection = null;\n");
        html.append("      }\n");
        html.append("    });\n");
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
