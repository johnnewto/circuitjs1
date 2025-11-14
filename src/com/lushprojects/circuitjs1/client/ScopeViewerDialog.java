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
    
    CirSim sim;
    Scope singleScope;  // If viewing just one scope
    
    public ScopeViewerDialog(CirSim s) {
        this(s, null);
    }
    
    public ScopeViewerDialog(CirSim s, Scope scope) {
        super();
        sim = s;
        singleScope = scope;
        
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
            exportScope(allDataJson, singleScope, 0, true);
        } else {
            // Export all scopes
            for (int i = 0; i < sim.scopeCount; i++) {
                Scope scope = sim.scopes[i];
                if (scope.visiblePlots.size() == 0)
                    continue;
                    
                if (!first)
                    allDataJson.append(",\n");
                first = false;
                
                exportScope(allDataJson, scope, i, first);
            }
        }
        
        allDataJson.append("\n]");
        
        // Generate HTML with embedded data and Plotly
        String html = generatePlotlyHTML(allDataJson.toString());
        
        // Open in new window
        openWindowWithHTML(html);
    }
    
    /**
     * Exports a single scope's data to JSON.
     */
    void exportScope(StringBuilder allDataJson, Scope scope, int index, boolean isFirst) {
        allDataJson.append("{\n");
        allDataJson.append("  \"scopeName\": \"").append(escapeJSON(getScopeName(scope, index))).append("\",\n");
        allDataJson.append("  \"scopeIndex\": ").append(index).append(",\n");
        
        // Use history if available, otherwise circular buffer
        boolean useHistory = scope.drawFromZero && scope.historySize > 0;
        String dataJson;
        if (useHistory) {
            dataJson = scope.exportHistoryAsJSON();
        } else {
            dataJson = scope.exportCircularBufferAsJSON();
        }
        
        // Strip outer braces and merge
        int startIdx = dataJson.indexOf('{') + 1;
        int endIdx = dataJson.lastIndexOf('}');
        if (startIdx > 0 && endIdx > startIdx) {
            allDataJson.append(dataJson.substring(startIdx, endIdx));
        }
        
        allDataJson.append("\n}");
    }
    
    String getScopeName(Scope scope, int index) {
        String name = scope.getScopeMenuName();
        if (name == null || name.isEmpty())
            name = "Scope " + (index + 1);
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
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>CircuitJS1 Scope Viewer</title>\n");
        html.append("  <script src=\"https://cdn.plot.ly/plotly-2.27.0.min.js\"></script>\n");
        html.append("  <style>\n");
        html.append("    body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }\n");
        html.append("    .container { max-width: 1400px; margin: 0 auto; }\n");
        html.append("    h1 { color: #333; margin-bottom: 10px; display: inline-block; }\n");
        html.append("    .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }\n");
        html.append("    .hamburger { cursor: pointer; padding: 10px; background: #007bff; color: white; border-radius: 4px; font-size: 20px; line-height: 1; user-select: none; }\n");
        html.append("    .hamburger:hover { background: #0056b3; }\n");
        html.append("    .help-popup { display: none; position: fixed; top: 60px; right: 20px; background: white; border: 1px solid #ddd; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.2); padding: 20px; max-width: 450px; z-index: 1000; }\n");
        html.append("    .help-popup.show { display: block; }\n");
        html.append("    .help-popup h3 { margin-top: 0; color: #007bff; }\n");
        html.append("    .help-popup .close-btn { float: right; cursor: pointer; font-size: 24px; color: #999; line-height: 1; }\n");
        html.append("    .help-popup .close-btn:hover { color: #333; }\n");
        html.append("    .help-popup button { display: block; width: 100%; margin: 5px 0; }\n");
        html.append("    .help-popup input[type=number] { padding: 5px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; }\n");
        html.append("    .help-popup label { color: #333; font-size: 14px; }\n");
        html.append("    .scope-container { background: white; margin: 20px 0; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        html.append("    .scope-title { font-size: 18px; font-weight: bold; margin-bottom: 10px; color: #007bff; }\n");
        html.append("    .plot { width: 100%; height: 600px; }\n");
        html.append("    .controls { margin: 20px 0; }\n");
        html.append("    button { background: #007bff; color: white; border: none; padding: 10px 20px; border-radius: 4px; cursor: pointer; margin-right: 10px; }\n");
        html.append("    button:hover { background: #0056b3; }\n");
        html.append("    button.secondary { background: #6c757d; }\n");
        html.append("    button.secondary:hover { background: #545b62; }\n");
        html.append("    .metadata { font-size: 12px; color: #666; margin-top: 5px; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container\">\n");
        html.append("    <div class=\"header\">\n");
        html.append("      <h1>CircuitJS1 Scope Viewer</h1>\n");
        html.append("      <div class=\"hamburger\" onclick=\"toggleHelp()\">☰</div>\n");
        html.append("    </div>\n");
        html.append("    <div id=\"help-popup\" class=\"help-popup\">\n");
        html.append("      <span class=\"close-btn\" onclick=\"toggleHelp()\">&times;</span>\n");
        html.append("      <h3>Interactive Scope Data Visualization</h3>\n");
        html.append("      <p><strong>Mouse Controls:</strong></p>\n");
        html.append("      <ul>\n");
        html.append("        <li><strong>Zoom:</strong> Click and drag on the graph</li>\n");
        html.append("        <li><strong>Pan:</strong> Shift + drag</li>\n");
        html.append("        <li><strong>Reset:</strong> Double-click</li>\n");
        html.append("        <li><strong>Hover:</strong> See exact values</li>\n");
        html.append("      </ul>\n");
        html.append("      <p><strong>Legend:</strong> Click items to show/hide traces</p>\n");
        html.append("      <p><strong>Range Slider:</strong> Use the slider below each graph to quickly navigate through the entire time series</p>\n");
        html.append("      <hr style=\"margin: 15px 0; border: none; border-top: 1px solid #ddd;\">\n");
        html.append("      <div style=\"margin-top: 15px;\">\n");
        html.append("        <label for=\"graphWidth\" style=\"display: block; margin-bottom: 5px;\"><strong>Graph Width:</strong></label>\n");
        html.append("        <input type=\"number\" id=\"graphWidth\" value=\"100\" min=\"50\" max=\"100\" style=\"width: 60px; margin-right: 5px;\">%\n");
        html.append("        <label for=\"graphHeight\" style=\"display: block; margin-top: 10px; margin-bottom: 5px;\"><strong>Graph Height:</strong></label>\n");
        html.append("        <input type=\"number\" id=\"graphHeight\" value=\"600\" min=\"300\" max=\"1200\" step=\"50\" style=\"width: 80px; margin-right: 5px;\">px\n");
        html.append("        <button class=\"secondary\" onclick=\"resizeGraphs()\" style=\"margin-top: 10px;\">Apply Size</button>\n");
        html.append("      </div>\n");
        html.append("      <hr style=\"margin: 15px 0; border: none; border-top: 1px solid #ddd;\">\n");
        html.append("      <div style=\"margin-top: 15px;\">\n");
        html.append("        <label style=\"display: block; margin-bottom: 5px;\"><strong>Y-Axis Range (leave blank for auto):</strong></label>\n");
        html.append("        <label for=\"yMin\" style=\"display: inline-block; margin-right: 5px;\">Min:</label>\n");
        html.append("        <input type=\"number\" id=\"yMin\" placeholder=\"auto\" step=\"any\" style=\"width: 80px; margin-right: 10px;\">\n");
        html.append("        <label for=\"yMax\" style=\"display: inline-block; margin-right: 5px;\">Max:</label>\n");
        html.append("        <input type=\"number\" id=\"yMax\" placeholder=\"auto\" step=\"any\" style=\"width: 80px;\">\n");
        html.append("        <button class=\"secondary\" onclick=\"setYAxisRange()\" style=\"margin-top: 10px;\">Apply Y-Axis</button>\n");
        html.append("        <button class=\"secondary\" onclick=\"resetYAxisRange()\" style=\"margin-top: 5px;\">Reset to Auto</button>\n");
        html.append("        <div style=\"margin-top: 10px;\">\n");
        html.append("          <label style=\"display: inline-block;\"><input type=\"checkbox\" id=\"autoScaleEnabled\" checked style=\"margin-right: 5px;\">Auto-Scale to Visible Range</label>\n");
        html.append("        </div>\n");
        html.append("      </div>\n");
        html.append("      <hr style=\"margin: 15px 0; border: none; border-top: 1px solid #ddd;\">\n");
        html.append("      <div style=\"margin-top: 15px;\">\n");
        html.append("        <button onclick=\"downloadAllAsJSON(); toggleHelp();\">Download All Data (JSON)</button>\n");
        html.append("        <button onclick=\"downloadAllAsCSV(); toggleHelp();\">Download All Data (CSV)</button>\n");
        html.append("        <button class=\"secondary\" onclick=\"toggleRangeSliders(); toggleHelp();\">Toggle Range Sliders</button>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        html.append("    <div id=\"scopes\"></div>\n");
        html.append("  </div>\n");
        html.append("  <script>\n");
        html.append("    const scopeData = ").append(jsonData).append(";\n");
        html.append("    const plotIds = [];\n");
        html.append("    let rangeSlidersVisible = true;\n");
        html.append("    \n");
        html.append("    function toggleHelp() {\n");
        html.append("      const popup = document.getElementById('help-popup');\n");
        html.append("      popup.classList.toggle('show');\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    function resizeGraphs() {\n");
        html.append("      const width = document.getElementById('graphWidth').value;\n");
        html.append("      const height = document.getElementById('graphHeight').value;\n");
        html.append("      \n");
        html.append("      plotIds.forEach(plotId => {\n");
        html.append("        const plotDiv = document.getElementById(plotId);\n");
        html.append("        if (plotDiv) {\n");
        html.append("          plotDiv.style.width = width + '%';\n");
        html.append("          plotDiv.style.height = height + 'px';\n");
        html.append("          Plotly.Plots.resize(plotDiv);\n");
        html.append("        }\n");
        html.append("      });\n");
        html.append("      \n");
        html.append("      toggleHelp();\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    function setYAxisRange() {\n");
        html.append("      const yMin = document.getElementById('yMin').value;\n");
        html.append("      const yMax = document.getElementById('yMax').value;\n");
        html.append("      \n");
        html.append("      const update = { 'yaxis.autorange': false };\n");
        html.append("      \n");
        html.append("      if (yMin !== '' && yMax !== '') {\n");
        html.append("        update['yaxis.range'] = [parseFloat(yMin), parseFloat(yMax)];\n");
        html.append("      } else if (yMin !== '') {\n");
        html.append("        update['yaxis.range[0]'] = parseFloat(yMin);\n");
        html.append("      } else if (yMax !== '') {\n");
        html.append("        update['yaxis.range[1]'] = parseFloat(yMax);\n");
        html.append("      }\n");
        html.append("      \n");
        html.append("      plotIds.forEach(plotId => {\n");
        html.append("        Plotly.relayout(plotId, update);\n");
        html.append("      });\n");
        html.append("      \n");
        html.append("      toggleHelp();\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    function resetYAxisRange() {\n");
        html.append("      document.getElementById('yMin').value = '';\n");
        html.append("      document.getElementById('yMax').value = '';\n");
        html.append("      \n");
        html.append("      plotIds.forEach(plotId => {\n");
        html.append("        Plotly.relayout(plotId, { 'yaxis.autorange': true });\n");
        html.append("      });\n");
        html.append("      \n");
        html.append("      toggleHelp();\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    function autoScaleToVisible() {\n");
        html.append("      plotIds.forEach(plotId => {\n");
        html.append("        const plotDiv = document.getElementById(plotId);\n");
        html.append("        if (!plotDiv || !plotDiv.data || !plotDiv.layout) return;\n");
        html.append("        \n");
        html.append("        // Get current X-axis range (visible range in range slider)\n");
        html.append("        const xaxis = plotDiv.layout.xaxis;\n");
        html.append("        let xMin, xMax;\n");
        html.append("        \n");
        html.append("        if (xaxis.range) {\n");
        html.append("          xMin = xaxis.range[0];\n");
        html.append("          xMax = xaxis.range[1];\n");
        html.append("        } else {\n");
        html.append("          // Find min/max from data if no range set\n");
        html.append("          xMin = Infinity;\n");
        html.append("          xMax = -Infinity;\n");
        html.append("          plotDiv.data.forEach(trace => {\n");
        html.append("            if (trace.x) {\n");
        html.append("              xMin = Math.min(xMin, ...trace.x);\n");
        html.append("              xMax = Math.max(xMax, ...trace.x);\n");
        html.append("            }\n");
        html.append("          });\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        // Find Y min/max for data points within visible X range\n");
        html.append("        let yMin = Infinity;\n");
        html.append("        let yMax = -Infinity;\n");
        html.append("        \n");
        html.append("        plotDiv.data.forEach(trace => {\n");
        html.append("          if (trace.x && trace.y) {\n");
        html.append("            for (let i = 0; i < trace.x.length; i++) {\n");
        html.append("              if (trace.x[i] >= xMin && trace.x[i] <= xMax) {\n");
        html.append("                yMin = Math.min(yMin, trace.y[i]);\n");
        html.append("                yMax = Math.max(yMax, trace.y[i]);\n");
        html.append("              }\n");
        html.append("            }\n");
        html.append("          }\n");
        html.append("        });\n");
        html.append("        \n");
        html.append("        // Add 5% padding to Y range\n");
        html.append("        if (isFinite(yMin) && isFinite(yMax)) {\n");
        html.append("          const yPadding = (yMax - yMin) * 0.05;\n");
        html.append("          yMin -= yPadding;\n");
        html.append("          yMax += yPadding;\n");
        html.append("          \n");
        html.append("          Plotly.relayout(plotId, {\n");
        html.append("            'yaxis.autorange': false,\n");
        html.append("            'yaxis.range': [yMin, yMax]\n");
        html.append("          });\n");
        html.append("          \n");
        html.append("          // Update input fields with calculated values\n");
        html.append("          document.getElementById('yMin').value = yMin.toFixed(3);\n");
        html.append("          document.getElementById('yMax').value = yMax.toFixed(3);\n");
        html.append("        }\n");
        html.append("      });\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    function plotScope(scopeInfo, index) {\n");
        html.append("      const container = document.getElementById('scopes');\n");
        html.append("      \n");
        html.append("      const scopeDiv = document.createElement('div');\n");
        html.append("      scopeDiv.className = 'scope-container';\n");
        html.append("      \n");
        html.append("      const title = document.createElement('div');\n");
        html.append("      title.className = 'scope-title';\n");
        html.append("      title.textContent = scopeInfo.scopeName;\n");
        html.append("      scopeDiv.appendChild(title);\n");
        html.append("      \n");
        html.append("      const metadata = document.createElement('div');\n");
        html.append("      metadata.className = 'metadata';\n");
        html.append("      let metaText = 'Export Type: ' + scopeInfo.exportType;\n");
        html.append("      if (scopeInfo.exportType === 'history') {\n");
        html.append("        metaText += ' | Samples: ' + scopeInfo.historySize + ' | Interval: ' + scopeInfo.sampleInterval + 's';\n");
        html.append("      } else {\n");
        html.append("        metaText += ' | Time: ' + scopeInfo.simulationTime + 's | Step: ' + scopeInfo.timeStep + 's';\n");
        html.append("      }\n");
        html.append("      metadata.textContent = metaText;\n");
        html.append("      scopeDiv.appendChild(metadata);\n");
        html.append("      \n");
        html.append("      const plotDiv = document.createElement('div');\n");
        html.append("      plotDiv.className = 'plot';\n");
        html.append("      plotDiv.id = 'plot-' + index;\n");
        html.append("      scopeDiv.appendChild(plotDiv);\n");
        html.append("      \n");
        html.append("      container.appendChild(scopeDiv);\n");
        html.append("      \n");
        html.append("      const traces = [];\n");
        html.append("      scopeInfo.plots.forEach((plot) => {\n");
        html.append("        const hasMinMax = plot.minValues.some((v, i) => v !== plot.maxValues[i]);\n");
        html.append("        \n");
        html.append("        if (hasMinMax) {\n");
        html.append("          traces.push({\n");
        html.append("            x: plot.time,\n");
        html.append("            y: plot.minValues,\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'lines',\n");
        html.append("            name: plot.name + ' (min)',\n");
        html.append("            line: { color: plot.color, width: 1, dash: 'dot' },\n");
        html.append("            legendgroup: plot.name\n");
        html.append("          });\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        traces.push({\n");
        html.append("          x: plot.time,\n");
        html.append("          y: plot.maxValues,\n");
        html.append("          type: 'scatter',\n");
        html.append("          mode: 'lines',\n");
        html.append("          name: plot.name,\n");
        html.append("          line: { color: plot.color, width: 2 },\n");
        html.append("          legendgroup: plot.name\n");
        html.append("        });\n");
        html.append("      });\n");
        html.append("      \n");
        html.append("      const layout = {\n");
        html.append("        title: scopeInfo.scopeName,\n");
        html.append("        xaxis: { \n");
        html.append("          title: 'Time (s)', \n");
        html.append("          gridcolor: '#ddd',\n");
        html.append("          rangeslider: { visible: true },\n");
        html.append("          type: 'linear'\n");
        html.append("        },\n");
        html.append("        yaxis: { title: 'Value', gridcolor: '#ddd' },\n");
        html.append("        hovermode: 'closest',\n");
        html.append("        showlegend: true,\n");
        html.append("        legend: { x: 1.02, y: 1, xanchor: 'left' },\n");
        html.append("        margin: { r: 150 }\n");
        html.append("      };\n");
        html.append("      \n");
        html.append("      const config = {\n");
        html.append("        responsive: true,\n");
        html.append("        displayModeBar: true,\n");
        html.append("        displaylogo: false,\n");
        html.append("        toImageButtonOptions: {\n");
        html.append("          format: 'png',\n");
        html.append("          filename: 'scope_' + index,\n");
        html.append("          height: 600,\n");
        html.append("          width: 1200,\n");
        html.append("          scale: 2\n");
        html.append("        }\n");
        html.append("      };\n");
        html.append("      \n");
        html.append("      Plotly.newPlot(plotDiv.id, traces, layout, config);\n");
        html.append("      plotIds.push(plotDiv.id);\n");
        html.append("      \n");
        html.append("      // Add event listener for auto-scaling on range changes\n");
        html.append("      document.getElementById(plotDiv.id).on('plotly_relayout', function(eventData) {\n");
        html.append("        if (document.getElementById('autoScaleEnabled').checked) {\n");
        html.append("          // Check if X-axis range changed (from zoom or range slider)\n");
        html.append("          if (eventData['xaxis.range[0]'] !== undefined || eventData['xaxis.range'] !== undefined) {\n");
        html.append("            autoScaleToVisible();\n");
        html.append("          }\n");
        html.append("        }\n");
        html.append("      });\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    function toggleRangeSliders() {\n");
        html.append("      rangeSlidersVisible = !rangeSlidersVisible;\n");
        html.append("      plotIds.forEach(plotId => {\n");
        html.append("        Plotly.relayout(plotId, {\n");
        html.append("          'xaxis.rangeslider.visible': rangeSlidersVisible\n");
        html.append("        });\n");
        html.append("      });\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    function downloadAllAsJSON() {\n");
        html.append("      const dataStr = JSON.stringify(scopeData, null, 2);\n");
        html.append("      const blob = new Blob([dataStr], { type: 'application/json' });\n");
        html.append("      const url = URL.createObjectURL(blob);\n");
        html.append("      const a = document.createElement('a');\n");
        html.append("      a.href = url;\n");
        html.append("      a.download = 'all-scopes-data.json';\n");
        html.append("      a.click();\n");
        html.append("      URL.revokeObjectURL(url);\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    function downloadAllAsCSV() {\n");
        html.append("      let csv = '';\n");
        html.append("      scopeData.forEach((scope, scopeIdx) => {\n");
        html.append("        csv += '# ' + scope.scopeName + '\\n';\n");
        html.append("        csv += 'Time (s)';\n");
        html.append("        scope.plots.forEach(plot => {\n");
        html.append("          csv += ',' + plot.name + ' Min,' + plot.name + ' Max';\n");
        html.append("        });\n");
        html.append("        csv += '\\n';\n");
        html.append("        \n");
        html.append("        const numPoints = scope.plots[0].time.length;\n");
        html.append("        for (let i = 0; i < numPoints; i++) {\n");
        html.append("          csv += scope.plots[0].time[i];\n");
        html.append("          scope.plots.forEach(plot => {\n");
        html.append("            csv += ',' + plot.minValues[i] + ',' + plot.maxValues[i];\n");
        html.append("          });\n");
        html.append("          csv += '\\n';\n");
        html.append("        }\n");
        html.append("        csv += '\\n';\n");
        html.append("      });\n");
        html.append("      \n");
        html.append("      const blob = new Blob([csv], { type: 'text/csv' });\n");
        html.append("      const url = URL.createObjectURL(blob);\n");
        html.append("      const a = document.createElement('a');\n");
        html.append("      a.href = url;\n");
        html.append("      a.download = 'all-scopes-data.csv';\n");
        html.append("      a.click();\n");
        html.append("      URL.revokeObjectURL(url);\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    // Plot all scopes\n");
        html.append("    scopeData.forEach((scope, index) => plotScope(scope, index));\n");
        html.append("  </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    /**
     * Opens a new window with the given HTML content.
     * Uses native JavaScript to open window and write content.
     */
    native void openWindowWithHTML(String html) /*-{
        var newWindow = $wnd.open('', '_blank', 'width=1400,height=900');
        if (newWindow) {
            newWindow.document.write(html);
            newWindow.document.close();
        } else {
            alert('Please allow pop-ups for this site to view the scope data.');
        }
    }-*/;
}
