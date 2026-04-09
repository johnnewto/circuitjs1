package com.lushprojects.circuitjs1.client.elements.economics;

import java.util.ArrayList;
import com.lushprojects.circuitjs1.client.*;
import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.TextResource;
import com.google.gwt.user.client.Window;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

/**
 * External popup viewer for sfcr-style DAG blocks visualization.
 *
 * Triggered from EquationTableElm context menu. It aggregates equations from
 * all EquationTableElm elements in the circuit, computes dependency edges,
 * identifies SCC blocks/cycles, and renders an interactive graph in Cytoscape.
 */
public class SFCRDagBlocksViewer {
    private static final String WINDOW_KEY = "sfcrDagBlocksWindow";
    private static final String POPUP_FEATURES = "width=1400,height=900";

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Document")
    private static class DocumentLike {
        @JsMethod native void open();
        @JsMethod native void write(String text);
        @JsMethod native void close();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Window")
    private static class WindowLike {
        @JsProperty(name = "document") native DocumentLike getDocument();
        @JsProperty(name = "closed") native boolean isClosed();
        @JsMethod native void focus();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Array")
    private static class WindowArrayLike {
        WindowArrayLike() {}
        @JsProperty(name = "length") native int getLength();
        @JsMethod(name = "push") native int push(WindowLike value);
        @JsMethod(name = "shift") native WindowLike shift();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "window")
    private static class GlobalWindowLike {
        @JsMethod(name = "open") static native WindowLike open(String url, String target, String features);
        @JsProperty(name = "plotlyWindows") static native WindowArrayLike getPlotlyWindows();
        @JsProperty(name = "plotlyWindows") static native void setPlotlyWindows(WindowArrayLike windows);
        @JsProperty(name = WINDOW_KEY) static native WindowLike getDagWindow();
        @JsProperty(name = WINDOW_KEY) static native void setDagWindow(WindowLike window);
    }

    private static final String[] BLOCK_COLORS = {
        "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
        "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"
    };

    interface DagBlocksViewerResources extends ClientBundle {
        DagBlocksViewerResources INSTANCE = GWT.create(DagBlocksViewerResources.class);

        @Source("SFCRDagBlocksViewerTemplate.html")
        TextResource dagBlocksViewerTemplate();
    }

    private final CirSim sim;

    /** Directed dependency edge: from source equation node to target equation node. */
    private static class EdgeDef {
        int from;
        int to;
        boolean historical;
        boolean lastDependency;

        EdgeDef(int from, int to, boolean historical, boolean lastDependency) {
            this.from = from;
            this.to = to;
            this.historical = historical;
            this.lastDependency = lastDependency;
        }
    }

    /** Fully assembled graph payload consumed by the HTML renderer. */
    private static class GraphData {
        ArrayList<String> nodes = new ArrayList<String>();
        ArrayList<Boolean> nodeIsStock = new ArrayList<Boolean>();
        ArrayList<String> nodeHints = new ArrayList<String>();
        ArrayList<String> nodeHintEquations = new ArrayList<String>();
        ArrayList<EdgeDef> edges = new ArrayList<EdgeDef>();
        int[] blockByNode;
        boolean[] cyclicalByNode;
        int blockCount;
    }

    public SFCRDagBlocksViewer(CirSim sim) {
        this.sim = sim;
    }

    /**
     * Compute cyclical equation node names for the current circuit DAG.
     *
     * Package-visible so debug tooling (e.g. EquationTableMarkdownDebugDialog)
     * can surface cyclical membership without duplicating SCC logic.
     */
    public static java.util.Set<String> getCyclicalNodeNames(CirSim sim, boolean includeHistoricalRefs,
            boolean ignoreExternalSections) {
        return EquationDependencyGraph.getCyclicalNodeNames(sim, includeHistoricalRefs, ignoreExternalSections);
    }

    /**
     * Build all graph variants and open/reuse the external viewer popup.
     */
    public void openExternalWindow() {
        GraphData samePeriodGraph = buildGraph(false, false, false);
        GraphData historicalGraph = buildGraph(true, false, false);
        GraphData samePeriodNoParamsGraph = buildGraph(false, true, false);
        GraphData historicalNoParamsGraph = buildGraph(true, true, false);
        GraphData samePeriodNoAdjustableGraph = buildGraph(false, false, true);
        GraphData historicalNoAdjustableGraph = buildGraph(true, false, true);
        GraphData samePeriodNoParamsNoAdjustableGraph = buildGraph(false, true, true);
        GraphData historicalNoParamsNoAdjustableGraph = buildGraph(true, true, true);
        String html = generateHTML(samePeriodGraph, historicalGraph,
                samePeriodNoParamsGraph, historicalNoParamsGraph,
                samePeriodNoAdjustableGraph, historicalNoAdjustableGraph,
                samePeriodNoParamsNoAdjustableGraph, historicalNoParamsNoAdjustableGraph);
        if (!openOrReuseWindowWithHTML(html)) {
            CirSim.console("DAG Blocks viewer popup was blocked. Please allow popups for this site.");
        }
    }

    /**
     * Build graph data for one dependency mode and filter setting.
     *
     * @param includeHistoricalRefs true to include refs inside historical operators
     * @param ignoreExternalSections true to skip rows under "# Parameters" and "# External" sections
     * @param ignoreAdjustableRows true to skip rows where table.isAdjustableRow(row)
     */
    private GraphData buildGraph(boolean includeHistoricalRefs, boolean ignoreExternalSections,
            boolean ignoreAdjustableRows) {
        EquationDependencyGraph depGraph = EquationDependencyGraph.build(sim, includeHistoricalRefs,
                ignoreExternalSections, ignoreAdjustableRows);
        GraphData graph = new GraphData();
        if (depGraph.getNodeNames().isEmpty()) {
            graph.blockByNode = new int[0];
            graph.cyclicalByNode = new boolean[0];
            graph.blockCount = 0;
            return graph;
        }

        for (int i = 0; i < depGraph.getNodeNames().size(); i++) {
            graph.nodes.add(depGraph.getNodeNames().get(i));
            graph.nodeIsStock.add(depGraph.getNodeIsStock().get(i));
            graph.nodeHints.add(depGraph.getNodeHints().get(i));
            graph.nodeHintEquations.add(depGraph.getNodeHintEquations().get(i));
        }
        for (EquationDependencyGraph.EdgeData edge : depGraph.getEdges()) {
            graph.edges.add(new EdgeDef(edge.from, edge.to, edge.historical, edge.lastDependency));
        }
        graph.blockByNode = depGraph.getBlockByNode();
        graph.cyclicalByNode = depGraph.getCyclicalByNode();
        graph.blockCount = depGraph.getBlockCount();
        return graph;
    }

    /** Build complete standalone popup HTML with Cytoscape renderer and controls. */
    private String generateHTML(GraphData samePeriodGraph, GraphData historicalGraph,
            GraphData samePeriodNoParamsGraph, GraphData historicalNoParamsGraph,
            GraphData samePeriodNoAdjustableGraph, GraphData historicalNoAdjustableGraph,
            GraphData samePeriodNoParamsNoAdjustableGraph, GraphData historicalNoParamsNoAdjustableGraph) {
        String sameJson = graphToJSON(samePeriodGraph, "Same-Period Dependencies");
        String historicalJson = graphToJSON(historicalGraph, "Historical + Same-Period Dependencies");
        String sameNoParamsJson = graphToJSON(samePeriodNoParamsGraph,
            "Same-Period Dependencies (Parameters/External Excluded)");
        String historicalNoParamsJson = graphToJSON(historicalNoParamsGraph,
            "Historical + Same-Period Dependencies (Parameters/External Excluded)");
        String sameNoAdjustableJson = graphToJSON(samePeriodNoAdjustableGraph,
            "Same-Period Dependencies (Adjustable Rows Excluded)");
        String historicalNoAdjustableJson = graphToJSON(historicalNoAdjustableGraph,
            "Historical + Same-Period Dependencies (Adjustable Rows Excluded)");
        String sameNoParamsNoAdjustableJson = graphToJSON(samePeriodNoParamsNoAdjustableGraph,
            "Same-Period Dependencies (Parameters/External + Adjustable Rows Excluded)");
        String historicalNoParamsNoAdjustableJson = graphToJSON(historicalNoParamsNoAdjustableGraph,
            "Historical + Same-Period Dependencies (Parameters/External + Adjustable Rows Excluded)");
        String template = DagBlocksViewerResources.INSTANCE.dagBlocksViewerTemplate().getText();
        return template
            .replace("__SAME_DATA_JSON__", sameJson)
            .replace("__HISTORICAL_DATA_JSON__", historicalJson)
            .replace("__SAME_NO_PARAMS_DATA_JSON__", sameNoParamsJson)
            .replace("__HISTORICAL_NO_PARAMS_DATA_JSON__", historicalNoParamsJson)
            .replace("__SAME_NO_ADJUSTABLE_DATA_JSON__", sameNoAdjustableJson)
            .replace("__HISTORICAL_NO_ADJUSTABLE_DATA_JSON__", historicalNoAdjustableJson)
            .replace("__SAME_NO_PARAMS_NO_ADJUSTABLE_DATA_JSON__", sameNoParamsNoAdjustableJson)
            .replace("__HISTORICAL_NO_PARAMS_NO_ADJUSTABLE_DATA_JSON__", historicalNoParamsNoAdjustableJson)
            .replace("__BLOCK_COLORS_JS_ARRAY__", jsStringArray(BLOCK_COLORS));
    }

    private String graphToJSON(GraphData graph, String titleSuffix) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"title\":\"").append(escapeJson("SFCR DAG Blocks Plot — " + titleSuffix)).append("\",");
        sb.append("\"nodes\":[");
        for (int i = 0; i < graph.nodes.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{");
            sb.append("\"name\":\"").append(escapeJson(graph.nodes.get(i))).append("\",");
            sb.append("\"stock\":").append(graph.nodeIsStock != null && i < graph.nodeIsStock.size() && graph.nodeIsStock.get(i).booleanValue() ? "true" : "false").append(",");
            sb.append("\"hint\":\"").append(escapeJson(graph.nodeHints != null && i < graph.nodeHints.size() ? graph.nodeHints.get(i) : "")).append("\",");
            sb.append("\"hintEq\":\"").append(escapeJson(graph.nodeHintEquations != null && i < graph.nodeHintEquations.size() ? graph.nodeHintEquations.get(i) : "")).append("\",");
            sb.append("\"block\":").append(graph.blockByNode != null && i < graph.blockByNode.length ? graph.blockByNode[i] : 0).append(",");
            sb.append("\"cyclical\":").append(graph.cyclicalByNode != null && i < graph.cyclicalByNode.length && graph.cyclicalByNode[i] ? "true" : "false");
            sb.append("}");
        }
        sb.append("],\"edges\":[");
        for (int i = 0; i < graph.edges.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            EdgeDef edge = graph.edges.get(i);
            sb.append("{");
            sb.append("\"from\":").append(edge.from).append(",");
            sb.append("\"to\":").append(edge.to).append(",");
            sb.append("\"historical\":").append(edge.historical ? "true" : "false").append(",");
            sb.append("\"lastDependency\":").append(edge.lastDependency ? "true" : "false");
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String jsStringArray(String[] values) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(values[i])).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /** Escape string for safe embedding into JSON literals in generated HTML. */
    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\') {
                out.append("\\\\");
            } else if (ch == '"') {
                out.append("\\\"");
            } else if (ch == '\n') {
                out.append("\\n");
            } else if (ch == '\r') {
                out.append("\\r");
            } else if (ch == '\t') {
                out.append("\\t");
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    /**
     * Open or reuse the named popup window and write full HTML content into it.
     * The window is also tracked in global `plotlyWindows` for existing shared cleanup paths.
     */
    private boolean openOrReuseWindowWithHTML(String html) {
        WindowArrayLike windows = GlobalWindowLike.getPlotlyWindows();
        if (windows == null) {
            windows = new WindowArrayLike();
            GlobalWindowLike.setPlotlyWindows(windows);
        }

        WindowLike viewerWindow = GlobalWindowLike.getDagWindow();
        if (viewerWindow != null && !viewerWindow.isClosed()) {
            viewerWindow.getDocument().open();
            viewerWindow.getDocument().write(html);
            viewerWindow.getDocument().close();
            viewerWindow.focus();
            return true;
        }

        viewerWindow = GlobalWindowLike.open("", WINDOW_KEY, POPUP_FEATURES);
        if (viewerWindow == null) {
            Window.alert("Please allow pop-ups for this site to view the DAG blocks plot.");
            return false;
        }

        viewerWindow.getDocument().write(html);
        viewerWindow.getDocument().close();
        GlobalWindowLike.setDagWindow(viewerWindow);

        int len = windows.getLength();
        for (int i = 0; i < len; i++) {
            WindowLike existing = windows.shift();
            if (existing != null && !existing.isClosed()) {
                windows.push(existing);
            }
        }
        windows.push(viewerWindow);
        return true;
    }
}
