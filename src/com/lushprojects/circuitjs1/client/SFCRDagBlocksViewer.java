package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;

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

    private static final String[] BLOCK_COLORS = {
        "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
        "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"
    };

    private final CirSim sim;

    /** Lightweight equation record used during dependency graph construction. */
    private static class EquationDef {
        String name;
        String equation;
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

        StringBuilder html = new StringBuilder();
        appendHtmlHead(html);
        appendHtmlBodyStart(html);
        appendHtmlControls(html);
        appendHtmlStatusAndContainers(html);
        appendHtmlScript(html, sameJson, historicalJson, sameNoParamsJson, historicalNoParamsJson,
            sameNoAdjustableJson, historicalNoAdjustableJson,
            sameNoParamsNoAdjustableJson, historicalNoParamsNoAdjustableJson);
        appendHtmlBodyEnd(html);
        return html.toString();
    }

    /** Append HTML head, JS library imports, and CSS styling. */
    private void appendHtmlHead(StringBuilder html) {
        html.append("<!doctype html>\n");
        html.append("<html><head><meta charset='utf-8'>\n");
        html.append("<title>SFCR DAG Blocks Plot</title>\n");
        html.append("<script src='https://cdn.jsdelivr.net/npm/cytoscape@3.29.2/dist/cytoscape.min.js'></script>\n");
        html.append("<script src='https://cdn.jsdelivr.net/npm/dagre@0.8.5/dist/dagre.min.js'></script>\n");
        html.append("<script src='https://cdn.jsdelivr.net/npm/cytoscape-dagre@2.5.0/cytoscape-dagre.min.js'></script>\n");
        html.append("<style>\n");
        html.append("body{font-family:sans-serif;margin:0;padding:12px;background:#fff;}\n");
        html.append("#controls{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:6px;}\n");
        html.append("button{padding:6px 10px;border:1px solid #ccc;background:#f8f8f8;cursor:pointer;}\n");
        html.append("button.active{background:#e8f0fe;border-color:#9bb6f0;}\n");
        html.append("#status{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:0 0 8px 0;}\n");
        html.append(".badge{display:inline-block;padding:2px 8px;border:1px solid #ccc;border-radius:999px;background:#fafafa;font-size:12px;color:#333;}\n");
        html.append("#legend{border:1px solid #ddd;border-radius:6px;padding:8px;background:#fafafa;margin:0 0 8px 0;}\n");
        html.append("#legend.hidden{display:none;}\n");
        html.append(".legendRow{display:flex;align-items:center;gap:8px;margin:4px 0;font-size:12px;}\n");
        html.append(".shapeKey{display:inline-flex;align-items:center;justify-content:center;width:12px;height:12px;border:1px solid #333;background:#fff;}\n");
        html.append("#dagPlot{width:100%;height:calc(100vh - 170px);min-height:420px;border:1px solid #eee;}\n");
        html.append("#nodeHoverTip{position:fixed;z-index:99999;max-width:560px;padding:8px 10px;background:#fff;border:1px solid #bbb;border-radius:6px;box-shadow:0 2px 8px rgba(0,0,0,0.15);font-size:12px;line-height:1.35;color:#222;white-space:normal;pointer-events:none;display:none;}\n");
        html.append("</style></head>\n");
    }

    /** Append opening body tag. */
    private void appendHtmlBodyStart(StringBuilder html) {
        html.append("<body>\n");
    }

    /** Append top control row (mode, direction, labels, filters, legend toggle). */
    private void appendHtmlControls(StringBuilder html) {
        html.append("<div id='controls'>\n");
        html.append("<strong>Mode:</strong>\n");
        html.append("<button id='btnSame' class='active'>Same-Period</button>\n");
        html.append("<button id='btnHist'>Historical + Same-Period</button>\n");
        html.append("<label style='margin-left:8px;'>Direction:</label>\n");
        html.append("<select id='layoutDir'><option value='TB'>Top → Bottom</option><option value='LR' selected>Left → Right</option></select>\n");
        html.append("<label>Labels:</label>\n");
        html.append("<select id='labelMode'><option value='compact' selected>Compact</option><option value='full'>Full</option><option value='none'>None</option></select>\n");
        html.append("<label style='user-select:none;'><input id='useHints' type='checkbox'/> Use hint text for labels</label>\n");
        html.append("<label style='user-select:none;'><input id='ignoreParams' type='checkbox' checked/> Ignore # Parameters / # External sections</label>\n");
        html.append("<label style='user-select:none;'><input id='ignoreAdjustable' type='checkbox' checked/> Ignore adjustable rows</label>\n");
        html.append("<button id='toggleLegend'>Hide Legend</button>\n");
        html.append("</div>\n");
    }

    /** Append status badges, legend container, and graph container. */
    private void appendHtmlStatusAndContainers(StringBuilder html) {
        html.append("<div id='status'>\n");
        html.append("<span id='modeBadge' class='badge'></span>\n");
        html.append("<span id='filterBadge' class='badge'></span>\n");
        html.append("<span class='badge'>Tip: click node to highlight neighborhood</span>\n");
        html.append("</div>\n");
        html.append("<div id='nodeHoverTip'></div>\n");
        html.append("<div id='legend'></div>\n");
        html.append("<div id='dagPlot'></div>\n");
    }

    /** Append all viewer JavaScript and inline data payloads. */
    private void appendHtmlScript(StringBuilder html, String sameJson, String historicalJson,
            String sameNoParamsJson, String historicalNoParamsJson,
            String sameNoAdjustableJson, String historicalNoAdjustableJson,
            String sameNoParamsNoAdjustableJson, String historicalNoParamsNoAdjustableJson) {
        html.append("<script>\n");
        html.append("const sameData=").append(sameJson).append(";\n");
        html.append("const historicalData=").append(historicalJson).append(";\n");
        html.append("const sameNoParamsData=").append(sameNoParamsJson).append(";\n");
        html.append("const historicalNoParamsData=").append(historicalNoParamsJson).append(";\n");
        html.append("const sameNoAdjustableData=").append(sameNoAdjustableJson).append(";\n");
        html.append("const historicalNoAdjustableData=").append(historicalNoAdjustableJson).append(";\n");
        html.append("const sameNoParamsNoAdjustableData=").append(sameNoParamsNoAdjustableJson).append(";\n");
        html.append("const historicalNoParamsNoAdjustableData=").append(historicalNoParamsNoAdjustableJson).append(";\n");
        html.append("let active='same';\n");
        html.append("let cy=null;\n");
        html.append("let hoverTipEl=null;\n");
        html.append("function colorForBlock(block){const c=").append(jsStringArray(BLOCK_COLORS)).append(";return c[(Math.max(1,block)-1)%c.length];}\n");
        html.append("function formatMathLabel(text){\n");
        html.append("  if(!text) return '';\n");
        html.append("  const greek = {\n");
        html.append("    alpha:'α',beta:'β',gamma:'γ',delta:'δ',epsilon:'ε',zeta:'ζ',eta:'η',theta:'θ',iota:'ι',kappa:'κ',lambda:'λ',mu:'μ',nu:'ν',xi:'ξ',omicron:'ο',pi:'π',rho:'ρ',sigma:'σ',tau:'τ',upsilon:'υ',phi:'φ',chi:'χ',psi:'ψ',omega:'ω',\n");
        html.append("    Alpha:'Α',Beta:'Β',Gamma:'Γ',Delta:'Δ',Epsilon:'Ε',Zeta:'Ζ',Eta:'Η',Theta:'Θ',Iota:'Ι',Kappa:'Κ',Lambda:'Λ',Mu:'Μ',Nu:'Ν',Xi:'Ξ',Omicron:'Ο',Pi:'Π',Rho:'Ρ',Sigma:'Σ',Tau:'Τ',Upsilon:'Υ',Phi:'Φ',Chi:'Χ',Psi:'Ψ',Omega:'Ω',\n");
        html.append("    degree:'°',pm:'±',times:'×',div:'÷',infty:'∞',sqrt:'√',approx:'≈',neq:'≠',leq:'≤',geq:'≥'\n");
        html.append("  };\n");
        html.append("  const subMap = {\n");
        html.append("    '0':'₀','1':'₁','2':'₂','3':'₃','4':'₄','5':'₅','6':'₆','7':'₇','8':'₈','9':'₉','+':'₊','-':'₋','=':'₌','(':'₍',')':'₎',\n");
        html.append("    'a':'ₐ','e':'ₑ','h':'ₕ','i':'ᵢ','j':'ⱼ','k':'ₖ','l':'ₗ','m':'ₘ','n':'ₙ','o':'ₒ','p':'ₚ','r':'ᵣ','s':'ₛ','t':'ₜ','u':'ᵤ','v':'ᵥ','x':'ₓ'\n");
        html.append("  };\n");
        html.append("  const supMap = {\n");
        html.append("    '0':'⁰','1':'¹','2':'²','3':'³','4':'⁴','5':'⁵','6':'⁶','7':'⁷','8':'⁸','9':'⁹','+':'⁺','-':'⁻','=':'⁼','(':'⁽',')':'⁾',\n");
        html.append("    'a':'ᵃ','b':'ᵇ','c':'ᶜ','d':'ᵈ','e':'ᵉ','f':'ᶠ','g':'ᵍ','h':'ʰ','i':'ⁱ','j':'ʲ','k':'ᵏ','l':'ˡ','m':'ᵐ','n':'ⁿ','o':'ᵒ','p':'ᵖ','r':'ʳ','s':'ˢ','t':'ᵗ','u':'ᵘ','v':'ᵛ','w':'ʷ','x':'ˣ','y':'ʸ','z':'ᶻ'\n");
        html.append("  };\n");
        html.append("  let s = String(text).replace(/\\\\([A-Za-z]+)/g, function(_, key){ return Object.prototype.hasOwnProperty.call(greek, key) ? greek[key] : _; });\n");
        html.append("  function mapScriptWithFallback(value, map, marker, wasBraced){\n");
        html.append("    let out='';\n");
        html.append("    for(let i=0;i<value.length;i++){\n");
        html.append("      const ch=value.charAt(i);\n");
        html.append("      if(Object.prototype.hasOwnProperty.call(map, ch)) out += map[ch];\n");
        html.append("      else return marker + (wasBraced ? ('{' + value + '}') : value);\n");
        html.append("    }\n");
        html.append("    return out;\n");
        html.append("  }\n");
        html.append("  let out = '';\n");
        html.append("  for(let i=0;i<s.length;i++){\n");
        html.append("    const ch = s.charAt(i);\n");
        html.append("    if(ch !== '_' && ch !== '^'){ out += ch; continue; }\n");
        html.append("    const isSub = (ch === '_');\n");
        html.append("    if(i + 1 >= s.length){ out += ch; continue; }\n");
        html.append("    let script = '';\n");
        html.append("    let wasBraced = false;\n");
        html.append("    if(s.charAt(i + 1) === '{'){\n");
        html.append("      wasBraced = true;\n");
        html.append("      let j = i + 2;\n");
        html.append("      while(j < s.length && s.charAt(j) !== '}') j++;\n");
        html.append("      if(j < s.length){ script = s.substring(i + 2, j); i = j; }\n");
        html.append("      else { script = s.substring(i + 2); i = s.length - 1; }\n");
        html.append("    } else {\n");
        html.append("      script = s.charAt(i + 1);\n");
        html.append("      i = i + 1;\n");
        html.append("    }\n");
        html.append("    out += mapScriptWithFallback(script, isSub ? subMap : supMap, ch, wasBraced);\n");
        html.append("  }\n");
        html.append("  return out;\n");
        html.append("}\n");
        html.append("function compactLabel(s){if(!s) return ''; return s.length>22 ? s.substring(0,21)+'…' : s;}\n");
        html.append("function toElements(g){\n");
        html.append("  const elements=[];\n");
        html.append("  const useHints = !!(document.getElementById('useHints') && document.getElementById('useHints').checked);\n");
        html.append("  for(let i=0;i<g.nodes.length;i++){const n=g.nodes[i];const raw=(useHints && n.hint && String(n.hint).trim().length>0)?n.hint:n.name;const fmt=formatMathLabel(raw);const hintEq=formatMathLabel(n.hintEq||'');elements.push({data:{id:'n'+i,labelFull:fmt,labelDisplay:compactLabel(fmt),hintEq:hintEq,block:n.block,cyclical:n.cyclical?1:0,color:colorForBlock(n.block)}});}\n");
        html.append("  for(let i=0;i<g.edges.length;i++){const e=g.edges[i];const cyc=(g.nodes[e.from]&&g.nodes[e.from].block===g.nodes[e.to].block)?1:0;elements.push({data:{id:'e'+i,source:'n'+e.from,target:'n'+e.to,weight:cyc?1:3}});}\n");
        html.append("  return elements;\n");
        html.append("}\n");
        html.append("function getHoverTipEl(){\n");
        html.append("  if(!hoverTipEl){ hoverTipEl = document.getElementById('nodeHoverTip'); }\n");
        html.append("  return hoverTipEl;\n");
        html.append("}\n");
        html.append("function hideHoverTip(){\n");
        html.append("  const el = getHoverTipEl();\n");
        html.append("  if(!el) return;\n");
        html.append("  el.style.display = 'none';\n");
        html.append("}\n");
        html.append("function showHoverTip(evt, text){\n");
        html.append("  const el = getHoverTipEl();\n");
        html.append("  if(!el || !text) return;\n");
        html.append("  el.textContent = text;\n");
        html.append("  const oe = evt && evt.originalEvent ? evt.originalEvent : null;\n");
        html.append("  const x = oe && typeof oe.clientX === 'number' ? oe.clientX : 12;\n");
        html.append("  const y = oe && typeof oe.clientY === 'number' ? oe.clientY : 12;\n");
        html.append("  el.style.left = (x + 12) + 'px';\n");
        html.append("  el.style.top = (y + 12) + 'px';\n");
        html.append("  el.style.display = 'block';\n");
        html.append("}\n");
        html.append("function getActiveDataset(mode){\n");
        html.append("  const ignoreParams = !!document.getElementById('ignoreParams').checked;\n");
        html.append("  const ignoreAdjustable = !!document.getElementById('ignoreAdjustable').checked;\n");
        html.append("  if(mode==='historical'){\n");
        html.append("    if(ignoreParams && ignoreAdjustable) return historicalNoParamsNoAdjustableData;\n");
        html.append("    if(ignoreParams) return historicalNoParamsData;\n");
        html.append("    if(ignoreAdjustable) return historicalNoAdjustableData;\n");
        html.append("    return historicalData;\n");
        html.append("  }\n");
        html.append("  if(ignoreParams && ignoreAdjustable) return sameNoParamsNoAdjustableData;\n");
        html.append("  if(ignoreParams) return sameNoParamsData;\n");
        html.append("  if(ignoreAdjustable) return sameNoAdjustableData;\n");
        html.append("  return sameData;\n");
        html.append("}\n");
        html.append("function updateStatusBadges(){\n");
        html.append("  const modeText = (active==='historical') ? 'Mode: Historical + Same-Period' : 'Mode: Same-Period';\n");
        html.append("  const filters = [];\n");
        html.append("  if(document.getElementById('ignoreParams').checked) filters.push('Parameters/External Excluded');\n");
        html.append("  if(document.getElementById('ignoreAdjustable').checked) filters.push('Adjustable Rows Excluded');\n");
        html.append("  const filterText = filters.length ? ('Filter: ' + filters.join(' + ')) : 'Filter: None';\n");
        html.append("  document.getElementById('modeBadge').textContent = formatMathLabel(modeText);\n");
        html.append("  document.getElementById('filterBadge').textContent = formatMathLabel(filterText);\n");
        html.append("}\n");
        html.append("function buildLegend(){\n");
        html.append("  const legend = document.getElementById('legend');\n");
        html.append("  let html = '<div style=\"font-weight:600;margin-bottom:4px;\">Legend</div>';\n");
        html.append("  html += '<div class=\"legendRow\"><span class=\"shapeKey\" style=\"border-radius:2px;\"></span><span>Cyclical</span><span class=\"shapeKey\" style=\"border-radius:6px;\"></span><span>Non-cyclical</span></div>';\n");
        html.append("  legend.innerHTML = html;\n");
        html.append("}\n");
        html.append("function applyLabelMode(){\n");
        html.append("  if(!cy) return;\n");
        html.append("  const mode = document.getElementById('labelMode').value;\n");
        html.append("  cy.nodes().forEach(function(n){\n");
        html.append("    const full = n.data('labelFull') || '';\n");
        html.append("    let v = full;\n");
        html.append("    if(mode==='compact') v = compactLabel(full);\n");
        html.append("    if(mode==='none') v = '';\n");
        html.append("    n.data('labelDisplay', v);\n");
        html.append("  });\n");
        html.append("}\n");
        html.append("function clearHighlight(){\n");
        html.append("  if(!cy) return;\n");
        html.append("  cy.elements().removeClass('faded');\n");
        html.append("}\n");
        html.append("function enableNeighborhoodHighlight(){\n");
        html.append("  if(!cy) return;\n");
        html.append("  cy.on('mouseover', 'node', function(evt){\n");
        html.append("    const n = evt.target;\n");
        html.append("    const tip = (n && n.data) ? (n.data('hintEq') || '') : '';\n");
        html.append("    if(tip) showHoverTip(evt, tip);\n");
        html.append("    else hideHoverTip();\n");
        html.append("  });\n");
        html.append("  cy.on('mousemove', 'node', function(evt){\n");
        html.append("    const n = evt.target;\n");
        html.append("    const tip = (n && n.data) ? (n.data('hintEq') || '') : '';\n");
        html.append("    if(tip) showHoverTip(evt, tip);\n");
        html.append("  });\n");
        html.append("  cy.on('mouseout', 'node', function(){ hideHoverTip(); });\n");
        html.append("  cy.on('tap', 'node', function(evt){\n");
        html.append("    const n = evt.target;\n");
        html.append("    cy.elements().addClass('faded');\n");
        html.append("    const keep = n.closedNeighborhood();\n");
        html.append("    keep.removeClass('faded');\n");
        html.append("  });\n");
        html.append("  cy.on('tap', function(evt){ if(evt.target === cy){ clearHighlight(); } });\n");
        html.append("}\n");
        html.append("function renderGraph(mode){\n");
        html.append("  const g=getActiveDataset(mode);\n");
        html.append("  const title = g.title || 'SFCR DAG Blocks Plot';\n");
        html.append("  document.title = formatMathLabel(title);\n");
        html.append("  updateStatusBadges();\n");
        html.append("  buildLegend();\n");
        html.append("  if(typeof cytoscape === 'undefined'){\n");
        html.append("    document.getElementById('dagPlot').innerHTML='<div style=\"padding:12px;color:#b00;\">Cytoscape failed to load.</div>';\n");
        html.append("    return;\n");
        html.append("  }\n");
        html.append("  if(cy){cy.destroy();cy=null;}\n");
        html.append("  hideHoverTip();\n");
        html.append("  cy = cytoscape({\n");
        html.append("    container: document.getElementById('dagPlot'),\n");
        html.append("    elements: toElements(g),\n");
        html.append("    style: [\n");
        html.append("      {selector:'node',style:{'label':'data(labelDisplay)','font-size':10,'text-wrap':'wrap','text-max-width':140,'text-valign':'center','text-halign':'center','width':'label','height':26,'padding-left':8,'padding-right':8,'background-color':'data(color)','border-width':1,'border-color':'#333','shape':'round-rectangle'}},\n");
        html.append("      {selector:'node[cyclical = 1]',style:{'shape':'rectangle','border-width':2}},\n");
        html.append("      {selector:'edge',style:{'curve-style':'bezier','width':1.2,'line-color':'#999','target-arrow-color':'#999','target-arrow-shape':'triangle','arrow-scale':0.9,'opacity':0.65}},\n");
        html.append("      {selector:'.faded',style:{'opacity':0.14}},\n");
        html.append("      {selector:':selected',style:{'overlay-opacity':0,'border-color':'#111','line-color':'#555','target-arrow-color':'#555'}}\n");
        html.append("    ],\n");
        html.append("    layout: {\n");
        html.append("      name:'dagre',\n");
        html.append("      rankDir:(document.getElementById('layoutDir') ? document.getElementById('layoutDir').value : 'LR'),\n");
        html.append("      rankSep:52,\n");
        html.append("      nodeSep:12,\n");
        html.append("      edgeSep:6,\n");
        html.append("      animate:false,\n");
        html.append("      acyclicer:'greedy',\n");
        html.append("      ranker:'network-simplex',\n");
        html.append("      edgeWeight:function(edge){ return edge.data('weight') || 1; }\n");
        html.append("    }\n");
        html.append("  });\n");
        html.append("  applyLabelMode();\n");
        html.append("  enableNeighborhoodHighlight();\n");
        html.append("  if(cy && cy.fit){cy.fit(undefined, 24);}\n");
        html.append("}\n");
        html.append("function setMode(mode){active=mode;document.getElementById('btnSame').classList.toggle('active',mode==='same');document.getElementById('btnHist').classList.toggle('active',mode==='historical');renderGraph(mode);}\n");
        html.append("document.getElementById('btnSame').addEventListener('click',()=>setMode('same'));\n");
        html.append("document.getElementById('btnHist').addEventListener('click',()=>setMode('historical'));\n");
        html.append("document.getElementById('layoutDir').addEventListener('change',()=>renderGraph(active));\n");
        html.append("document.getElementById('useHints').addEventListener('change',()=>renderGraph(active));\n");
        html.append("document.getElementById('ignoreParams').addEventListener('change',()=>renderGraph(active));\n");
        html.append("document.getElementById('ignoreAdjustable').addEventListener('change',()=>renderGraph(active));\n");
        html.append("document.getElementById('labelMode').addEventListener('change',()=>{applyLabelMode(); if(cy){cy.fit(undefined,24);}});\n");
        html.append("document.getElementById('toggleLegend').addEventListener('click',()=>{const lg=document.getElementById('legend');const hidden=lg.classList.toggle('hidden');document.getElementById('toggleLegend').textContent=hidden?'Show Legend':'Hide Legend';});\n");
        html.append("window.addEventListener('resize', function(){ if(cy){ cy.fit(undefined, 24); } });\n");
        html.append("setMode('same');\n");
        html.append("</script>\n");
    }

    /** Append closing body/html tags. */
    private void appendHtmlBodyEnd(StringBuilder html) {
        html.append("</body></html>\n");
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
    private native boolean openOrReuseWindowWithHTML(String html) /*-{
        if (!$wnd.plotlyWindows) {
            $wnd.plotlyWindows = [];
        }

        var windowKey = "sfcrDagBlocksWindow";
        var viewerWindow = $wnd[windowKey];
        if (viewerWindow && !viewerWindow.closed) {
            viewerWindow.document.open();
            viewerWindow.document.write(html);
            viewerWindow.document.close();
            viewerWindow.focus();
            return true;
        }

        viewerWindow = $wnd.open('', windowKey, "width=1400,height=900");
        if (!viewerWindow) {
            $wnd.alert('Please allow pop-ups for this site to view the DAG blocks plot.');
            return false;
        }

        viewerWindow.document.write(html);
        viewerWindow.document.close();
        $wnd[windowKey] = viewerWindow;
        $wnd.plotlyWindows.push(viewerWindow);
        $wnd.plotlyWindows = $wnd.plotlyWindows.filter(function(w) {
            return w && !w.closed;
        });
        return true;
    }-*/;
}
