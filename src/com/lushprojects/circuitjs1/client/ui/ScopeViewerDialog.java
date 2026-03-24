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

import com.lushprojects.circuitjs1.client.elements.ActionScheduler;
import com.lushprojects.circuitjs1.client.elements.misc.ScopeElm;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;
import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.TextResource;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.Window;
import com.lushprojects.circuitjs1.client.util.Locale;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

/**
 * Dialog that opens all scope data in an interactive Plotly.js viewer in a new window.
 */
public class ScopeViewerDialog extends DialogBox {

    interface ScopeViewerResources extends ClientBundle {
        ScopeViewerResources INSTANCE = GWT.create(ScopeViewerResources.class);

        @Source("ScopeViewerTemplate.html")
        TextResource scopeViewerTemplate();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Document")
    private static class DocumentLike {
        @JsMethod native void write(String text);
        @JsMethod native void close();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Window")
    private static class WindowLike {
        @JsProperty(name = "document") native DocumentLike getDocument();
        @JsProperty(name = "closed") native boolean isClosed();
        @JsMethod native void close();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Array")
    private static class WindowArrayLike {
        public WindowArrayLike() {}
        @JsProperty(name = "length") native int getLength();
        @JsMethod(name = "push") native int push(WindowLike value);
        @JsMethod(name = "shift") native WindowLike shift();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "window")
    private static class GlobalWindowLike {
        @JsMethod(name = "open") static native WindowLike open(String url, String target, String features);
        @JsProperty(name = "plotlyWindows") static native WindowArrayLike getPlotlyWindows();
        @JsProperty(name = "plotlyWindows") static native void setPlotlyWindows(WindowArrayLike windows);
    }
    
    CirSim sim;
    Scope singleScope;  // If viewing just one scope
    boolean isAutomatic;  // Track if this is an automatic open (for better error messages)
    
    public ScopeViewerDialog(CirSim s) {
        this(s, null);
    }
    
    public ScopeViewerDialog(CirSim s, Scope scope) {
        this(s, scope, false);
    }
    
    public ScopeViewerDialog(CirSim s, Scope scope, boolean openImmediately) {
        super();
        sim = s;
        singleScope = scope;
        isAutomatic = openImmediately;
        
        if (openImmediately) {
            // Open viewer directly without showing dialog
            openViewer();
            return;
        }
        
        String title = (scope != null) ? 
            Locale.LS("Open Scope Viewer") : 
            Locale.LS("Open Interactive Scope Viewer");
        setText(title);
        
        VerticalPanel vp = new VerticalPanel();
        setWidget(vp);
        
        String message = (scope != null) ?
            Locale.LS("This will open this scope data in a new window with interactive Plotly.js graphs.") :
            Locale.LS("This will open all scope data in a new window with interactive Plotly.js graphs.");
        vp.add(new com.google.gwt.user.client.ui.Label(message));
        vp.add(new com.google.gwt.user.client.ui.Label(
            Locale.LS("You can zoom, pan, and export the graphs as images.")));
        
        HorizontalPanel hp = new HorizontalPanel();
        hp.setWidth("100%");
        hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
        hp.setStyleName("topSpace");
        vp.add(hp);
        
        Button openButton = new Button(Locale.LS("Open Viewer"));
        Button cancelButton = new Button(Locale.LS("Cancel"));
        hp.add(openButton);
        hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
        hp.add(cancelButton);
        
        openButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                openViewer();
                hide();
            }
        });
        
        cancelButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                hide();
            }
        });
        
        center();
        show();
    }
    
    /**
     * Opens a new window with Plotly.js visualization of all scopes.
     */
    void openViewer() {
        // Collect all scope data
        StringBuilder allDataJson = new StringBuilder();
        allDataJson.append("[\n");
        
        boolean first = true;
        
        // If single scope specified, only export that one
        if (singleScope != null) {
            exportScope(allDataJson, singleScope, 0, getScopeName(singleScope, "Scope", 1));
        } else {
            int exportedIndex = 0;
            int dockedOrdinal = 1;
            int undockedOrdinal = 1;

            // Export all docked scopes
            for (int i = 0; i < sim.scopeCount; i++) {
                Scope scope = sim.scopes[i];
                if (scope == null || scope.visiblePlots.size() == 0)
                    continue;

                if (!first)
                    allDataJson.append(",\n");
                first = false;

                String scopeName = getScopeName(scope, "Scope", dockedOrdinal++);
                exportScope(allDataJson, scope, exportedIndex++, scopeName);
            }

            // Export all undocked (floating) ScopeElm scopes
            for (int i = 0; i < sim.elmList.size(); i++) {
                if (!(sim.elmList.get(i) instanceof ScopeElm))
                    continue;
                ScopeElm scopeElm = (ScopeElm) sim.elmList.get(i);
                Scope scope = scopeElm.elmScope;
                if (scope == null || scope.visiblePlots.size() == 0)
                    continue;

                if (!first)
                    allDataJson.append(",\n");
                first = false;

                String scopeName = getScopeName(scope, "Undocked Scope", undockedOrdinal++);
                exportScope(allDataJson, scope, exportedIndex++, scopeName);
            }
        }
        
        allDataJson.append("\n]");
        
        // Generate HTML with embedded data and Plotly
        String html = generatePlotlyHTML(allDataJson.toString());
        
        // Open in new window
        if (!openWindowWithHTML(html)) {
            // Popup was blocked
            if (isAutomatic) {
                CirSim.console("Plotly viewer popup was blocked. Please allow popups for this site, then manually open viewer from Scopes menu.");
            }
        }
    }
    
    /**
     * Exports a single scope's data to JSON.
     */
    void exportScope(StringBuilder allDataJson, Scope scope, int index, String scopeName) {
        allDataJson.append("{\n");
        allDataJson.append("  \"scopeName\": \"").append(escapeJSON(scopeName)).append("\",\n");
        allDataJson.append("  \"scopeIndex\": ").append(index).append(",\n");
        
        // Add action times if available
        ActionScheduler scheduler = ActionScheduler.getInstance();
        if (scheduler != null) {
            java.util.List<ActionScheduler.ScheduledAction> allActions = scheduler.getAllActions();
            java.util.List<ActionScheduler.ScheduledAction> enabledActions = new java.util.ArrayList<ActionScheduler.ScheduledAction>();
            
            // Filter for enabled actions
            for (ActionScheduler.ScheduledAction action : allActions) {
                if (action.enabled) {
                    enabledActions.add(action);
                }
            }
            
            if (!enabledActions.isEmpty()) {
                // Add action times array
                allDataJson.append("  \"actionTimes\": [");
                for (int i = 0; i < enabledActions.size(); i++) {
                    if (i > 0) allDataJson.append(", ");
                    allDataJson.append(enabledActions.get(i).actionTime);
                }
                allDataJson.append("],\n");
                
                // Add action annotations array
                allDataJson.append("  \"actionAnnotations\": [");
                for (int i = 0; i < enabledActions.size(); i++) {
                    if (i > 0) allDataJson.append(", ");
                    ActionScheduler.ScheduledAction action = enabledActions.get(i);
                    String annotation = action.postText != null && !action.postText.isEmpty() ? 
                        action.postText : "Action " + action.id;
                    allDataJson.append("\"").append(escapeJSON(annotation)).append("\"");
                }
                allDataJson.append("],\n");
            }
        }
        
        // Use history if available, otherwise circular buffer
        boolean useHistory = scope.hasHistoryForExport();
        String dataJson = scope.exportDataAsJSON(useHistory);
        
        // Strip outer braces and merge
        int startIdx = dataJson.indexOf('{') + 1;
        int endIdx = dataJson.lastIndexOf('}');
        if (startIdx > 0 && endIdx > startIdx) {
            allDataJson.append(dataJson.substring(startIdx, endIdx));
        }
        
        allDataJson.append("\n}");
    }
    
    String getScopeName(Scope scope, String fallbackPrefix, int fallbackIndex) {
        String name = scope.getScopeMenuName();
        if (name == null || name.isEmpty())
            name = fallbackPrefix + " " + fallbackIndex;
        return name;
    }
    
    String escapeJSON(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    /**
     * Generates complete HTML document with Plotly.js and embedded scope data.
     */
    String generatePlotlyHTML(String jsonData) {
        String template = ScopeViewerResources.INSTANCE.scopeViewerTemplate().getText();
        return template
                .replace("__SCOPE_DATA_JSON__", jsonData)
                .replace("__TIME_UNIT_SYMBOL__", escapeJavaScriptString(sim.timeUnitSymbol));
    }

    String escapeJavaScriptString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    /**
     * Opens a new window with the given HTML content.
     * Uses native JavaScript to open window and write content.
     * Tracks opened windows in a global array for cleanup.
     * @return true if window opened successfully, false if blocked
     */
    boolean openWindowWithHTML(String html) {
        WindowArrayLike windows = GlobalWindowLike.getPlotlyWindows();
        if (windows == null) {
            windows = new WindowArrayLike();
            GlobalWindowLike.setPlotlyWindows(windows);
        }

        WindowLike newWindow = GlobalWindowLike.open("", "_blank", "width=1400,height=900");
        if (newWindow == null) {
            Window.alert("Please allow pop-ups for this site to view the scope data.");
            return false;
        }

        newWindow.getDocument().write(html);
        newWindow.getDocument().close();

        int len = windows.getLength();
        for (int i = 0; i < len; i++) {
            WindowLike existing = windows.shift();
            if (existing != null && !existing.isClosed()) {
                windows.push(existing);
            }
        }
        windows.push(newWindow);
        return true;
    }
}
