package com.lushprojects.circuitjs1.client.ui;

import com.google.gwt.user.client.Window;
import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.VariableHistoryStore;

/**
 * Opens a Plotly viewer for one named variable using the shared variable history store.
 */
public final class VariableTraceViewerDialog {
    private final CirSim sim;
    private final String variableName;

    public VariableTraceViewerDialog(CirSim sim, String variableName) {
        this.sim = sim;
        this.variableName = variableName;
    }

    public void openViewer() {
        if (sim == null || variableName == null || variableName.isEmpty()) {
            return;
        }
        VariableHistoryStore.SeriesSnapshot snapshot = sim.getVariableHistoryStore().getVariableSeriesSnapshot(variableName);
        if (snapshot == null || snapshot.size() == 0) {
            Window.alert("No variable history available yet. Run the simulation and try again.");
            return;
        }
        String html = generatePlotlyHTML(buildScopeLikeJson(snapshot));
        openWindowWithHTML(html);
    }

    private String buildScopeLikeJson(VariableHistoryStore.SeriesSnapshot snapshot) {
        StringBuilder json = new StringBuilder();
        json.append("[{\n");
        json.append("  \"scopeName\": \"Variable: ").append(PlotlyWindowHelper.escapeJSON(snapshot.name)).append("\",\n");
        json.append("  \"scopeIndex\": 0,\n");
        json.append("  \"exportType\": \"history\",\n");
        json.append("  \"historySize\": ").append(snapshot.size()).append(",\n");
        json.append("  \"sampleInterval\": ").append(snapshot.averageSampleInterval()).append(",\n");
        json.append("  \"plots\": [\n");
        json.append("    {\n");
        json.append("      \"name\": \"").append(PlotlyWindowHelper.escapeJSON(snapshot.name)).append("\",\n");
        json.append("      \"color\": \"#007bff\",\n");
        json.append("      \"time\": ");
        appendNumberArray(json, snapshot.time);
        json.append(",\n");
        json.append("      \"values\": ");
        appendNumberArray(json, snapshot.values);
        json.append("\n    }\n  ]\n}]");
        return json.toString();
    }

    private void appendNumberArray(StringBuilder json, double[] values) {
        json.append("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                json.append(", ");
            }
            if (Double.isNaN(values[i]) || Double.isInfinite(values[i])) {
                json.append("null");
            } else {
                json.append(values[i]);
            }
        }
        json.append("]");
    }

    private String generatePlotlyHTML(String jsonData) {
        return PlotlyWindowHelper.generatePlotlyHTML(jsonData, sim.timeUnitSymbol);
    }

    private boolean openWindowWithHTML(String html) {
        return PlotlyWindowHelper.openWindowWithHTML(html,
                "Please allow pop-ups for this site to view the variable trace.");
    }
}