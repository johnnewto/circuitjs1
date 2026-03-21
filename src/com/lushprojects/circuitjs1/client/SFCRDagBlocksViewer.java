package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
class SFCRDagBlocksViewer {
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

    /** Lightweight equation record used during dependency graph construction. */
    private static class EquationDef {
        String name;
        String equation;
        boolean isStock;
        String hint;
        String hintEquation;
    }

    /** Directed dependency edge: from source equation node to target equation node. */
    private static class EdgeDef {
        int from;
        int to;

        EdgeDef(int from, int to) {
            this.from = from;
            this.to = to;
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

    /**
     * Tarjan SCC implementation used to assign nodes to strongly connected components.
     * SCCs are then used to compute block IDs and cyclical flags.
     */
    private static class TarjanContext {
        int[] index;
        int[] low;
        boolean[] onStack;
        int[] stack;
        int stackSize;
        int nextIndex;
        int[] componentByNode;
        int componentCount;
        ArrayList<ArrayList<Integer>> adjacency;

        TarjanContext(ArrayList<ArrayList<Integer>> adjacency) {
            this.adjacency = adjacency;
            int n = adjacency.size();
            index = new int[n];
            low = new int[n];
            onStack = new boolean[n];
            stack = new int[n];
            componentByNode = new int[n];
            for (int i = 0; i < n; i++) {
                index[i] = -1;
                low[i] = -1;
                componentByNode[i] = -1;
            }
            stackSize = 0;
            nextIndex = 0;
            componentCount = 0;
        }

        void run() {
            for (int v = 0; v < adjacency.size(); v++) {
                if (index[v] == -1) {
                    strongConnect(v);
                }
            }
        }

        private void strongConnect(int v) {
            index[v] = nextIndex;
            low[v] = nextIndex;
            nextIndex++;

            stack[stackSize++] = v;
            onStack[v] = true;

            ArrayList<Integer> neighbors = adjacency.get(v);
            for (int i = 0; i < neighbors.size(); i++) {
                int w = neighbors.get(i).intValue();
                if (index[w] == -1) {
                    strongConnect(w);
                    if (low[w] < low[v]) {
                        low[v] = low[w];
                    }
                } else if (onStack[w] && index[w] < low[v]) {
                    low[v] = index[w];
                }
            }

            if (low[v] == index[v]) {
                while (stackSize > 0) {
                    int w = stack[--stackSize];
                    onStack[w] = false;
                    componentByNode[w] = componentCount;
                    if (w == v) {
                        break;
                    }
                }
                componentCount++;
            }
        }
    }

    SFCRDagBlocksViewer(CirSim sim) {
        this.sim = sim;
    }

    /**
     * Compute cyclical equation node names for the current circuit DAG.
     *
     * Package-visible so debug tooling (e.g. EquationTableMarkdownDebugDialog)
     * can surface cyclical membership without duplicating SCC logic.
     */
    static java.util.Set<String> getCyclicalNodeNames(CirSim sim, boolean includeHistoricalRefs,
            boolean ignoreExternalSections) {
        java.util.LinkedHashSet<String> cyclical = new java.util.LinkedHashSet<String>();
        if (sim == null) {
            return cyclical;
        }

        SFCRDagBlocksViewer viewer = new SFCRDagBlocksViewer(sim);
        GraphData graph = viewer.buildGraph(includeHistoricalRefs, ignoreExternalSections, false);
        if (graph == null || graph.nodes == null || graph.cyclicalByNode == null) {
            return cyclical;
        }

        int n = Math.min(graph.nodes.size(), graph.cyclicalByNode.length);
        for (int i = 0; i < n; i++) {
            if (graph.cyclicalByNode[i]) {
                String name = graph.nodes.get(i);
                if (name != null && name.trim().length() > 0) {
                    cyclical.add(name.trim());
                }
            }
        }
        return cyclical;
    }

    /**
     * Build all graph variants and open/reuse the external viewer popup.
     */
    void openExternalWindow() {
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
        ArrayList<EquationDef> equations = collectEquations(ignoreExternalSections, ignoreAdjustableRows);
        GraphData graph = new GraphData();
        if (equations.isEmpty()) {
            graph.blockByNode = new int[0];
            graph.cyclicalByNode = new boolean[0];
            graph.blockCount = 0;
            return graph;
        }

        HashMap<String, Integer> nodeIndexByName = new HashMap<String, Integer>();
        for (int i = 0; i < equations.size(); i++) {
            EquationDef eq = equations.get(i);
            nodeIndexByName.put(eq.name, Integer.valueOf(i));
            graph.nodes.add(eq.name);
            graph.nodeIsStock.add(Boolean.valueOf(eq.isStock));
            graph.nodeHints.add(eq.hint);
            graph.nodeHintEquations.add(eq.hintEquation);
        }

        HashSet<String> edgeSet = new HashSet<String>();
        for (int i = 0; i < equations.size(); i++) {
            EquationDef eq = equations.get(i);
            LinkedHashSet<String> refs = parseReferences(eq.equation, includeHistoricalRefs);
            for (String refName : refs) {
                Integer fromIdxObj = nodeIndexByName.get(refName);
                if (fromIdxObj == null) {
                    continue;
                }
                int fromIdx = fromIdxObj.intValue();
                int toIdx = i;
                String edgeKey = fromIdx + ">" + toIdx;
                if (!edgeSet.contains(edgeKey)) {
                    edgeSet.add(edgeKey);
                    graph.edges.add(new EdgeDef(fromIdx, toIdx));
                }
            }
        }

        computeBlocksAndCycles(graph);
        return graph;
    }

    /**
     * Collect equation rows from all EquationTableElm instances.
     * Optionally excludes rows inside "# Parameters" or "# External" sections until the next comment row.
     */
    private ArrayList<EquationDef> collectEquations(boolean ignoreExternalSections, boolean ignoreAdjustableRows) {
        ArrayList<EquationDef> out = new ArrayList<EquationDef>();
        HashSet<String> seenNames = new HashSet<String>();

        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (!(ce instanceof EquationTableElm)) {
                continue;
            }

            EquationTableElm table = (EquationTableElm) ce;
            int rowCount = table.getRowCount();
            boolean ignoringSpecialSectionInThisTable = false;
            for (int row = 0; row < rowCount; row++) {
                if (table.isCommentRow(row)) {
                    if (ignoreExternalSections) {
                        if (ignoringSpecialSectionInThisTable) {
                            ignoringSpecialSectionInThisTable = false;
                        }
                        String commentText = safeTrim(table.getCommentText(row));
                        if (isIgnoredSectionHeaderComment(commentText)) {
                            ignoringSpecialSectionInThisTable = true;
                        }
                    }
                    continue;
                }

                if (ignoreExternalSections && ignoringSpecialSectionInThisTable) {
                    continue;
                }

                if (ignoreAdjustableRows && table.isAdjustableRow(row)) {
                    continue;
                }

                String outputName = safeTrim(table.getOutputName(row));
                String equation = safeTrim(table.getEquation(row));
                if (outputName.length() == 0 || equation.length() == 0) {
                    continue;
                }
                if (EquationTableElm.isCommentRowName(outputName)) {
                    continue;
                }

                if (seenNames.contains(outputName)) {
                    continue;
                }
                seenNames.add(outputName);

                EquationDef eq = new EquationDef();
                eq.name = outputName;
                eq.equation = equation;
                eq.isStock = EquationTableElm.isStockEquation(outputName, equation);
                eq.hint = safeTrim(HintRegistry.getHint(outputName));
                eq.hintEquation = safeTrim(table.getHintExpandedEquationForDisplay(row));
                out.add(eq);
            }
        }

        Collections.sort(out, new Comparator<EquationDef>() {
            @Override
            public int compare(EquationDef a, EquationDef b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return out;
    }

    /** Returns true when comment text marks the start of an ignored section. */
    private boolean isIgnoredSectionHeaderComment(String commentText) {
        if (commentText == null) {
            return false;
        }
        String normalized = commentText.trim().toLowerCase();
        while (normalized.length() > 0 &&
                (normalized.charAt(0) == '[' || normalized.charAt(0) == '(' || normalized.charAt(0) == '{')) {
            normalized = normalized.substring(1).trim();
        }
        while (normalized.length() > 0) {
            char last = normalized.charAt(normalized.length() - 1);
            if (last == ']' || last == ')' || last == '}' || last == ':') {
                normalized = normalized.substring(0, normalized.length() - 1).trim();
            } else {
                break;
            }
        }
        return "parameter".equals(normalized)
                || "parameters".equals(normalized)
                || "external".equals(normalized)
                || "externals".equals(normalized);
    }

    /** Parse equation expression and collect dependency refs by selected mode. */
    private LinkedHashSet<String> parseReferences(String equation, boolean includeHistoricalRefs) {
        LinkedHashSet<String> refs = new LinkedHashSet<String>();
        if (equation == null || equation.length() == 0) {
            return refs;
        }

        Expr expr = tryParseExpression(equation);
        if (expr == null) {
            String rhs = extractAssignmentRightHandSide(equation);
            if (rhs != null && rhs.length() > 0) {
                expr = tryParseExpression(rhs);
            }
        }
        if (expr == null) {
            return refs;
        }

        if (includeHistoricalRefs) {
            expr.collectAllRefs(refs);
        } else {
            expr.collectSamePeriodRefs(refs);
        }
        return refs;
    }

    private Expr tryParseExpression(String expressionText) {
        if (expressionText == null || expressionText.trim().length() == 0) {
            return null;
        }
        ExprParser parser = new ExprParser(expressionText);
        Expr expr = parser.parseExpression();
        if (parser.gotError() != null || expr == null) {
            return null;
        }
        return expr;
    }

    /**
     * For rows authored as "LHS = RHS" inside the equation field, return RHS.
     * Returns null if no plain assignment marker is found.
     */
    private String extractAssignmentRightHandSide(String equation) {
        if (equation == null) {
            return null;
        }

        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        for (int i = 0; i < equation.length(); i++) {
            char ch = equation.charAt(i);
            if (ch == '(') {
                parenDepth++;
                continue;
            }
            if (ch == ')') {
                parenDepth = Math.max(0, parenDepth - 1);
                continue;
            }
            if (ch == '[') {
                bracketDepth++;
                continue;
            }
            if (ch == ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
                continue;
            }
            if (ch == '{') {
                braceDepth++;
                continue;
            }
            if (ch == '}') {
                braceDepth = Math.max(0, braceDepth - 1);
                continue;
            }

            if (ch != '=' || parenDepth > 0 || bracketDepth > 0 || braceDepth > 0) {
                continue;
            }

            char prev = (i > 0) ? equation.charAt(i - 1) : '\0';
            char next = (i + 1 < equation.length()) ? equation.charAt(i + 1) : '\0';
            if (prev == '=' || next == '=' || prev == '!' || prev == '<' || prev == '>') {
                continue;
            }

            String lhs = equation.substring(0, i).trim();
            String rhs = equation.substring(i + 1).trim();
            if (lhs.length() == 0 || rhs.length() == 0) {
                return null;
            }
            return rhs;
        }
        return null;
    }

    /**
     * Compute SCC blocks and cyclical flags.
     *
     * Block IDs are assigned by topological ordering of the SCC condensation graph,
     * giving stable forward-flow layering in the renderer.
     */
    private void computeBlocksAndCycles(GraphData graph) {
        int nodeCount = graph.nodes.size();
        ArrayList<ArrayList<Integer>> adjacency = new ArrayList<ArrayList<Integer>>();
        for (int i = 0; i < nodeCount; i++) {
            adjacency.add(new ArrayList<Integer>());
        }

        for (int i = 0; i < graph.edges.size(); i++) {
            EdgeDef edge = graph.edges.get(i);
            adjacency.get(edge.from).add(Integer.valueOf(edge.to));
        }

        TarjanContext tarjan = new TarjanContext(adjacency);
        tarjan.run();

        int compCount = tarjan.componentCount;
        int[] compSize = new int[compCount];
        boolean[] compHasSelfLoop = new boolean[compCount];
        for (int i = 0; i < nodeCount; i++) {
            int comp = tarjan.componentByNode[i];
            compSize[comp]++;
        }
        for (int i = 0; i < graph.edges.size(); i++) {
            EdgeDef e = graph.edges.get(i);
            if (e.from == e.to) {
                int comp = tarjan.componentByNode[e.from];
                compHasSelfLoop[comp] = true;
            }
        }

        ArrayList<HashSet<Integer>> compAdj = new ArrayList<HashSet<Integer>>();
        for (int i = 0; i < compCount; i++) {
            compAdj.add(new HashSet<Integer>());
        }
        int[] indegree = new int[compCount];
        for (int i = 0; i < graph.edges.size(); i++) {
            EdgeDef edge = graph.edges.get(i);
            int fromComp = tarjan.componentByNode[edge.from];
            int toComp = tarjan.componentByNode[edge.to];
            if (fromComp == toComp) {
                continue;
            }
            HashSet<Integer> outs = compAdj.get(fromComp);
            Integer boxedTo = Integer.valueOf(toComp);
            if (!outs.contains(boxedTo)) {
                outs.add(boxedTo);
                indegree[toComp]++;
            }
        }

        ArrayList<Integer> queue = new ArrayList<Integer>();
        for (int i = 0; i < compCount; i++) {
            if (indegree[i] == 0) {
                queue.add(Integer.valueOf(i));
            }
        }

        int[] blockNumberByComp = new int[compCount];
        int qIndex = 0;
        int nextBlock = 1;
        while (qIndex < queue.size()) {
            int comp = queue.get(qIndex++).intValue();
            blockNumberByComp[comp] = nextBlock++;
            HashSet<Integer> outs = compAdj.get(comp);
            for (Integer toCompObj : outs) {
                int toComp = toCompObj.intValue();
                indegree[toComp]--;
                if (indegree[toComp] == 0) {
                    queue.add(Integer.valueOf(toComp));
                }
            }
        }

        for (int i = 0; i < compCount; i++) {
            if (blockNumberByComp[i] == 0) {
                blockNumberByComp[i] = nextBlock++;
            }
        }

        graph.blockByNode = new int[nodeCount];
        graph.cyclicalByNode = new boolean[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            int comp = tarjan.componentByNode[i];
            graph.blockByNode[i] = blockNumberByComp[comp];
            graph.cyclicalByNode[i] = compSize[comp] > 1 || compHasSelfLoop[comp];
        }

        graph.blockCount = Math.max(0, nextBlock - 1);
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
            sb.append("\"to\":").append(edge.to);
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

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
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
