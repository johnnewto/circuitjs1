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

import com.lushprojects.circuitjs1.client.scope.Scope;

import com.lushprojects.circuitjs1.client.elements.ActionScheduler;
import com.lushprojects.circuitjs1.client.elements.misc.ScopeElm;

import com.lushprojects.circuitjs1.client.*;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.lushprojects.circuitjs1.client.util.Locale;

/**
 * Dialog that opens all scope data in an interactive Plotly.js viewer in a new window.
 */
public class ScopeViewerDialog extends DialogBox {
    
    private CirSim sim;
    private Scope singleScope;  // If viewing just one scope
    private boolean isAutomatic;  // Track if this is an automatic open (for better error messages)
    
    public ScopeViewerDialog(CirSim s) {
        this(s, null);
    }
    
    private ScopeViewerDialog(CirSim s, Scope scope) {
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
    private void openViewer() {
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
                if (scope == null || scope.getVisiblePlotCount() == 0)
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
                if (scope == null || scope.getVisiblePlotCount() == 0)
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
    private void exportScope(StringBuilder allDataJson, Scope scope, int index, String scopeName) {
        allDataJson.append("{\n");
        allDataJson.append("  \"scopeName\": \"").append(PlotlyWindowHelper.escapeJSON(scopeName)).append("\",\n");
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
                    allDataJson.append("\"").append(PlotlyWindowHelper.escapeJSON(annotation)).append("\"");
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
    
    private String getScopeName(Scope scope, String fallbackPrefix, int fallbackIndex) {
        String name = scope.getScopeMenuName();
        if (name == null || name.isEmpty())
            name = fallbackPrefix + " " + fallbackIndex;
        return name;
    }
    
    /**
     * Generates complete HTML document with Plotly.js and embedded scope data.
     */
    private String generatePlotlyHTML(String jsonData) {
        return PlotlyWindowHelper.generatePlotlyHTML(jsonData, sim.timeUnitSymbol);
    }
    
    /**
     * Opens a new window with the given HTML content.
     * Uses native JavaScript to open window and write content.
     * Tracks opened windows in a global array for cleanup.
     * @return true if window opened successfully, false if blocked
     */
    private boolean openWindowWithHTML(String html) {
        return PlotlyWindowHelper.openWindowWithHTML(html,
                "Please allow pop-ups for this site to view the scope data.");
    }
}
