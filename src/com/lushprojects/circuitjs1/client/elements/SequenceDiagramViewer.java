/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.elements;

import com.lushprojects.circuitjs1.client.*;
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
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
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
 * SequenceDiagramViewer - Displays a sequence diagram using Mermaid.js
 * 
 * Visualizes transactions between sectors in an SFC Transaction table as a sequence diagram.
 * Uses sfcr convention:
 * - Negative cell values = outflows (source sector)
 * - Positive cell values = inflows (target sector)
 * 
 * Layout:
 * - Participants: sectors from the table columns
 * - Messages: transactions (rows) showing money flows between sectors
 * 
 * The diagram shows the sequence of economic transactions, making it clear
 * which sector pays which sector for each type of transaction.
 */
public class SequenceDiagramViewer {

    interface SequenceViewerResources extends ClientBundle {
        SequenceViewerResources INSTANCE = GWT.create(SequenceViewerResources.class);

        @Source("SequenceDiagramEmbeddedTemplate.html")
        TextResource embeddedTemplate();

        @Source("SequenceDiagramStandaloneTemplate.html")
        TextResource standaloneTemplate();
    }

    @JsFunction
    private interface MermaidUpdateFunction {
        void update(String mermaidCode);
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
        @JsProperty(name = "updateMermaidSequence") native MermaidUpdateFunction getUpdateMermaidSequence();
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

    private TableElm table;
    private static SequenceDialog dialogInstance = null;  // Singleton for internal dialog
    
    public SequenceDiagramViewer(TableElm table) {
        this.table = table;
    }
    
    /**
     * Open the sequence diagram in an internal dialog window.
     * This is the default method - shows diagram inside the app.
     */
    public void openDialog() {
        if (dialogInstance == null) {
            dialogInstance = new SequenceDialog(this);
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
     * Open the sequence diagram in a new external browser window.
     */
    private void openExternalWindow() {
        String html = generateHTML(false);  // Full standalone HTML
        if (!openWindowWithHTML(html)) {
            CirSim.console("Sequence diagram viewer popup was blocked. Please allow popups for this site.");
        }
    }
    
    /**
     * Build Mermaid sequence diagram code from the SFC table.
     * 
     * The diagram shows transactions as messages between sector participants.
     * Negative values in a cell indicate that sector is the source (payer).
     * Positive values indicate the sector is the target (receiver).
     */
    public String buildMermaidCode() {
        ArrayList<TableColumn> columns = table.columns;
        int rows = table.rows;
        String[] rowDescriptions = table.rowDescriptions;
        
        if (columns == null || rows == 0) {
            return "sequenceDiagram\n    Note over A: No transaction data";
        }
        
        // Collect sector names (skip Σ column)
        ArrayList<String> sectorNames = new ArrayList<>();
        Map<String, Integer> sectorIndex = new HashMap<>();
        for (TableColumn col : columns) {
            if (col.getType() == ColumnType.SECTOR) {
                String name = col.getStockName();
                sectorIndex.put(name, sectorNames.size());
                sectorNames.add(name);
            }
        }
        
        if (sectorNames.isEmpty()) {
            return "sequenceDiagram\n    Note over A: No sectors defined";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("sequenceDiagram\n");
        
        // Get the table title
        String title = table.tableTitle != null ? table.tableTitle : "SFC Transaction Table";
        sb.append("    title ").append(escapeMermaid(title)).append("\n");
        sb.append("\n");
        
        // Declare participants in order
        for (String sector : sectorNames) {
            String alias = sanitizeAlias(sector);
            sb.append("    participant ").append(alias).append(" as ").append(escapeMermaid(sector)).append("\n");
        }
        sb.append("\n");
        
        // Iterate through rows (transactions) and build messages
        for (int row = 0; row < rows; row++) {
            String transactionName = rowDescriptions != null && row < rowDescriptions.length 
                ? rowDescriptions[row] 
                : "Transaction " + (row + 1);
            
            // Find source and target sectors for this transaction
            // Source = column with negative value (payer)
            // Target = column with positive value (receiver)
            String sourceSector = null;
            String targetSector = null;
            double flowValue = 0;
            
            for (int col = 0; col < columns.size(); col++) {
                TableColumn column = columns.get(col);
                if (column.getType() != ColumnType.SECTOR) {
                    continue;
                }
                
                double value = getTransactionValue(row, col);
                if (Math.abs(value) < 1e-10) {
                    continue;
                }
                
                if (value < 0 && sourceSector == null) {
                    sourceSector = column.getStockName();
                    flowValue = Math.abs(value);
                } else if (value > 0 && targetSector == null) {
                    targetSector = column.getStockName();
                    if (flowValue == 0) flowValue = value;
                }
            }
            
            // Only create message if we have both source and target
            if (sourceSector != null && targetSector != null) {
                String sourceAlias = sanitizeAlias(sourceSector);
                String targetAlias = sanitizeAlias(targetSector);
                String valueStr = formatValue(flowValue);
                
                // Add a section note for each transaction group
                sb.append("    ").append(sourceAlias).append("->>").append(targetAlias)
                    .append(": ").append(escapeMermaid(transactionName));
                if (flowValue > 0) {
                    sb.append(" (").append(valueStr).append(")");
                }
                sb.append("\n");
            } else if (sourceSector != null || targetSector != null) {
                // Only one side (external flow)
                String sector = sourceSector != null ? sourceSector : targetSector;
                String alias = sanitizeAlias(sector);
                String direction = sourceSector != null ? "outflow" : "inflow";
                sb.append("    Note over ").append(alias).append(": ")
                    .append(escapeMermaid(transactionName)).append(" (").append(direction).append(")\n");
            }
        }
        
        return sb.toString();
    }

    /**
     * Get the value for a transaction cell.
     * Uses flow-first semantics if available.
     */
    private double getTransactionValue(int row, int col) {
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
     * Sanitize a sector name to use as a Mermaid participant alias.
     * Removes spaces and special characters.
     */
    private String sanitizeAlias(String name) {
        if (name == null) return "Unknown";
        // Replace spaces with underscores, remove special chars
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }
    
    /**
     * Escape a string for Mermaid syntax.
     */
    private String escapeMermaid(String s) {
        if (s == null) return "";
        // Mermaid uses # and ; for special sequences, also escape quotes
        return s.replace("#", "").replace(";", ",").replace("\"", "'");
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
     * Generate HTML for the sequence diagram viewer.
     * @param embedded If true, generates minimal HTML suitable for iframe embedding
     */
    private String generateHTML(boolean embedded) {
        String mermaidCode = buildMermaidCode();
        String title = table.tableTitle != null ? table.tableTitle : "SFC Table";
        String template = embedded
                ? SequenceViewerResources.INSTANCE.embeddedTemplate().getText()
                : SequenceViewerResources.INSTANCE.standaloneTemplate().getText();
        return template
                .replace("__TITLE__", escapeHtml(title))
                .replace("__MERMAID_CODE__", escapeMermaidInHtml(mermaidCode));
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
     * Escape mermaid code for embedding in JavaScript string within HTML.
     */
    private String escapeMermaidInHtml(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("$", "\\$");
    }
    
    /**
     * Opens a new window with the given HTML content.
     * @return true if window opened successfully, false if blocked
     */
    private boolean openWindowWithHTML(String html) {
        WindowLike newWindow = GlobalWindowLike.open("", "_blank", "width=1000,height=700");
        if (newWindow == null) {
            Window.alert("Please allow pop-ups for this site to view the sequence diagram.");
            return false;
        }
        newWindow.getDocument().write(html);
        newWindow.getDocument().close();
        return true;
    }
    
    /**
     * Internal dialog for displaying the sequence diagram.
     * Uses an iframe to embed the Mermaid chart.
     * Supports real-time updates via timer.
     */
    static class SequenceDialog extends DialogBox {
        private Frame chartFrame;
        private TextArea codeArea;
        private SequenceDiagramViewer currentViewer;
        private Timer refreshTimer;
        private boolean showCode = false;
        private static final int DIALOG_WIDTH = 900;
        private static final int DIALOG_HEIGHT = 600;
        private static final int REFRESH_INTERVAL_MS = 1000;  // 1 second refresh
        
        SequenceDialog(SequenceDiagramViewer viewer) {
            super(false, false);  // Not auto-hide, not modal
            currentViewer = viewer;
            
            // Create timer for auto-refresh
            refreshTimer = new Timer() {
                @Override
                public void run() {
                    if (isShowing()) {
                        refreshContent();
                    }
                }
            };
            
            String title = viewer.table.tableTitle != null ? viewer.table.tableTitle : "SFC Table";
            setText(Locale.LS("Sequence Diagram") + ": " + title);
            
            VerticalPanel vp = new VerticalPanel();
            vp.setWidth(DIALOG_WIDTH + "px");
            setWidget(vp);
            
            // Create iframe for chart
            chartFrame = new Frame();
            chartFrame.setSize(DIALOG_WIDTH + "px", (DIALOG_HEIGHT - 100) + "px");
            chartFrame.getElement().getStyle().setBorderWidth(0, Unit.PX);
            chartFrame.getElement().getStyle().setProperty("border", "1px solid #ccc");
            vp.add(chartFrame);
            
            // Create text area for code (hidden by default)
            codeArea = new TextArea();
            codeArea.setSize(DIALOG_WIDTH + "px", "150px");
            codeArea.setReadOnly(true);
            codeArea.setVisible(false);
            vp.add(codeArea);
            
            // Bottom button panel
            HorizontalPanel hp = new HorizontalPanel();
            hp.setWidth("100%");
            hp.getElement().getStyle().setMarginTop(10, Unit.PX);
            hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
            vp.add(hp);
            
            // Show/hide code button
            Button codeBtn = new Button(Locale.LS("Show Code"));
            codeBtn.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    showCode = !showCode;
                    codeArea.setVisible(showCode);
                    ((Button) event.getSource()).setText(Locale.LS(showCode ? "Hide Code" : "Show Code"));
                    if (showCode) {
                        codeArea.setText(currentViewer.buildMermaidCode());
                    }
                }
            });
            hp.add(codeBtn);
            
            // Copy code button
            Button copyBtn = new Button(Locale.LS("Copy Code"));
            copyBtn.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    copyToClipboard(currentViewer.buildMermaidCode());
                }
            });
            hp.add(copyBtn);
            
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
            // Start timer for real-time updates
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
         * Must be called after dialog is attached to DOM.
         */
        void loadContent() {
            String html = currentViewer.generateHTML(true);  // Embedded mode
            loadIframeContent(chartFrame.getElement(), html);
            if (showCode) {
                codeArea.setText(currentViewer.buildMermaidCode());
            }
        }
        
        /**
         * Refresh diagram with updated data (real-time update).
         */
        void refreshContent() {
            String mermaidCode = currentViewer.buildMermaidCode();
            updateMermaidDiagram(chartFrame.getElement(), mermaidCode);
            if (showCode) {
                codeArea.setText(mermaidCode);
            }
        }
        
        /**
         * Update the dialog with new content from a different viewer.
         */
        void updateContent(SequenceDiagramViewer viewer) {
            currentViewer = viewer;
            String title = viewer.table.tableTitle != null ? viewer.table.tableTitle : "SFC Table";
            setText(Locale.LS("Sequence Diagram") + ": " + title);
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
         * Native method to update Mermaid diagram via iframe's updateMermaidSequence function.
         */
        private void updateMermaidDiagram(com.google.gwt.dom.client.Element iframe, String mermaidCode) {
            IframeLike frame = (IframeLike) (Object) iframe;
            WindowLike win = frame.getContentWindow();
            if (win == null)
                return;
            MermaidUpdateFunction update = win.getUpdateMermaidSequence();
            if (update == null)
                return;
            update.update(mermaidCode);
        }
        
        /**
         * Copy text to clipboard using native JavaScript.
         */
        private native void copyToClipboard(String text) /*-{
            if ($wnd.navigator.clipboard) {
                $wnd.navigator.clipboard.writeText(text);
            } else {
                var ta = $doc.createElement('textarea');
                ta.value = text;
                $doc.body.appendChild(ta);
                ta.select();
                $doc.execCommand('copy');
                $doc.body.removeChild(ta);
            }
        }-*/;
    }
}
