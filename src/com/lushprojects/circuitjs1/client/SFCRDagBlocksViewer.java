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
 * identifies SCC blocks/cycles, and renders an interactive graph in Plotly.
 */
class SFCRDagBlocksViewer {
    private static final String[] BLOCK_COLORS = {
        "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
        "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"
    };

    private final CirSim sim;

    private static class EquationDef {
        String name;
        String equation;
    }

    private static class EdgeDef {
        int from;
        int to;

        EdgeDef(int from, int to) {
            this.from = from;
            this.to = to;
        }
    }

    private static class GraphData {
        ArrayList<String> nodes = new ArrayList<String>();
        ArrayList<EdgeDef> edges = new ArrayList<EdgeDef>();
        int[] blockByNode;
        boolean[] cyclicalByNode;
        int blockCount;
    }

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

    void openExternalWindow() {
        GraphData samePeriodGraph = buildGraph(false, false);
        GraphData historicalGraph = buildGraph(true, false);
        GraphData samePeriodNoParamsGraph = buildGraph(false, true);
        GraphData historicalNoParamsGraph = buildGraph(true, true);
        String html = generateHTML(samePeriodGraph, historicalGraph, samePeriodNoParamsGraph, historicalNoParamsGraph);
        if (!openOrReuseWindowWithHTML(html)) {
            CirSim.console("DAG Blocks viewer popup was blocked. Please allow popups for this site.");
        }
    }

    private GraphData buildGraph(boolean includeHistoricalRefs, boolean ignoreParameterSections) {
        ArrayList<EquationDef> equations = collectEquations(ignoreParameterSections);
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

    private ArrayList<EquationDef> collectEquations(boolean ignoreParameterSections) {
        ArrayList<EquationDef> out = new ArrayList<EquationDef>();
        HashSet<String> seenNames = new HashSet<String>();

        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (!(ce instanceof EquationTableElm)) {
                continue;
            }

            EquationTableElm table = (EquationTableElm) ce;
            int rowCount = table.getRowCount();
            boolean ignoringParametersInThisTable = false;
            for (int row = 0; row < rowCount; row++) {
                if (table.isCommentRow(row)) {
                    if (ignoreParameterSections) {
                        if (ignoringParametersInThisTable) {
                            ignoringParametersInThisTable = false;
                        }
                        String commentText = safeTrim(table.getCommentText(row)).toLowerCase();
                        if (commentText.startsWith("parameters")) {
                            ignoringParametersInThisTable = true;
                        }
                    }
                    continue;
                }

                if (ignoreParameterSections && ignoringParametersInThisTable) {
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

    private LinkedHashSet<String> parseReferences(String equation, boolean includeHistoricalRefs) {
        LinkedHashSet<String> refs = new LinkedHashSet<String>();
        if (equation == null || equation.length() == 0) {
            return refs;
        }

        ExprParser parser = new ExprParser(equation);
        Expr expr = parser.parseExpression();
        if (parser.gotError() != null || expr == null) {
            return refs;
        }

        if (includeHistoricalRefs) {
            expr.collectAllRefs(refs);
        } else {
            expr.collectSamePeriodRefs(refs);
        }
        return refs;
    }

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

        private String generateHTML(GraphData samePeriodGraph, GraphData historicalGraph,
            GraphData samePeriodNoParamsGraph, GraphData historicalNoParamsGraph) {
        String sameJson = graphToJSON(samePeriodGraph, "Same-Period Dependencies");
        String historicalJson = graphToJSON(historicalGraph, "Historical + Same-Period Dependencies");
        String sameNoParamsJson = graphToJSON(samePeriodNoParamsGraph,
            "Same-Period Dependencies (Parameters Excluded)");
        String historicalNoParamsJson = graphToJSON(historicalNoParamsGraph,
            "Historical + Same-Period Dependencies (Parameters Excluded)");

        StringBuilder html = new StringBuilder();
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
        html.append(".swatch{display:inline-block;width:12px;height:12px;border:1px solid #333;}\n");
        html.append(".shapeKey{display:inline-flex;align-items:center;justify-content:center;width:12px;height:12px;border:1px solid #333;background:#fff;}\n");
        html.append("#dagPlot{width:100%;height:calc(100vh - 170px);min-height:420px;border:1px solid #eee;}\n");
        html.append("</style></head><body>\n");
        html.append("<div id='controls'>\n");
        html.append("<strong>Mode:</strong>\n");
        html.append("<button id='btnSame' class='active'>Same-Period</button>\n");
        html.append("<button id='btnHist'>Historical + Same-Period</button>\n");
        html.append("<label style='margin-left:8px;'>Direction:</label>\n");
        html.append("<select id='layoutDir'><option value='TB'>Top → Bottom</option><option value='LR' selected>Left → Right</option></select>\n");
        html.append("<label>Labels:</label>\n");
        html.append("<select id='labelMode'><option value='compact' selected>Compact</option><option value='full'>Full</option><option value='none'>None</option></select>\n");
        html.append("<label style='user-select:none;'><input id='ignoreParams' type='checkbox' checked/> Ignore # Parameters section</label>\n");
        html.append("<button id='toggleLegend'>Hide Legend</button>\n");
        html.append("</div>\n");
        html.append("<div id='status'>\n");
        html.append("<span id='modeBadge' class='badge'></span>\n");
        html.append("<span id='filterBadge' class='badge'></span>\n");
        html.append("<span class='badge'>Tip: click node to highlight neighborhood</span>\n");
        html.append("</div>\n");
        html.append("<div id='legend'></div>\n");
        html.append("<div id='dagPlot'></div>\n");
        html.append("<script>\n");
        html.append("const sameData=").append(sameJson).append(";\n");
        html.append("const historicalData=").append(historicalJson).append(";\n");
        html.append("const sameNoParamsData=").append(sameNoParamsJson).append(";\n");
        html.append("const historicalNoParamsData=").append(historicalNoParamsJson).append(";\n");
        html.append("let active='same';\n");
        html.append("let cy=null;\n");
        html.append("function colorForBlock(block){const c=").append(jsStringArray(BLOCK_COLORS)).append(";return c[(Math.max(1,block)-1)%c.length];}\n");
        html.append("function compactLabel(s){if(!s) return ''; return s.length>22 ? s.substring(0,21)+'…' : s;}\n");
        html.append("function toElements(g){\n");
        html.append("  const elements=[];\n");
        html.append("  for(let i=0;i<g.nodes.length;i++){const n=g.nodes[i];elements.push({data:{id:'n'+i,labelFull:n.name,labelDisplay:compactLabel(n.name),block:n.block,cyclical:n.cyclical?1:0,color:colorForBlock(n.block)}});}\n");
        html.append("  for(let i=0;i<g.edges.length;i++){const e=g.edges[i];const cyc=(g.nodes[e.from]&&g.nodes[e.from].block===g.nodes[e.to].block)?1:0;elements.push({data:{id:'e'+i,source:'n'+e.from,target:'n'+e.to,weight:cyc?1:3}});}\n");
        html.append("  return elements;\n");
        html.append("}\n");
        html.append("function getActiveDataset(mode){\n");
        html.append("  const ignoreParams = !!document.getElementById('ignoreParams').checked;\n");
        html.append("  if(mode==='historical'){ return ignoreParams ? historicalNoParamsData : historicalData; }\n");
        html.append("  return ignoreParams ? sameNoParamsData : sameData;\n");
        html.append("}\n");
        html.append("function updateStatusBadges(){\n");
        html.append("  const modeText = (active==='historical') ? 'Mode: Historical + Same-Period' : 'Mode: Same-Period';\n");
        html.append("  const filterText = document.getElementById('ignoreParams').checked ? 'Filter: Parameters Excluded' : 'Filter: All Sections';\n");
        html.append("  document.getElementById('modeBadge').textContent = modeText;\n");
        html.append("  document.getElementById('filterBadge').textContent = filterText;\n");
        html.append("}\n");
        html.append("function buildLegend(g){\n");
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
        html.append("  document.title = title;\n");
        html.append("  updateStatusBadges();\n");
        html.append("  buildLegend(g);\n");
        html.append("  if(typeof cytoscape === 'undefined'){\n");
        html.append("    document.getElementById('dagPlot').innerHTML='<div style=\"padding:12px;color:#b00;\">Cytoscape failed to load.</div>';\n");
        html.append("    return;\n");
        html.append("  }\n");
        html.append("  if(cy){cy.destroy();cy=null;}\n");
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
        html.append("document.getElementById('ignoreParams').addEventListener('change',()=>renderGraph(active));\n");
        html.append("document.getElementById('labelMode').addEventListener('change',()=>{applyLabelMode(); if(cy){cy.fit(undefined,24);}});\n");
        html.append("document.getElementById('toggleLegend').addEventListener('click',()=>{const lg=document.getElementById('legend');const hidden=lg.classList.toggle('hidden');document.getElementById('toggleLegend').textContent=hidden?'Show Legend':'Hide Legend';});\n");
        html.append("window.addEventListener('resize', function(){ if(cy){ cy.fit(undefined, 24); } });\n");
        html.append("setMode('same');\n");
        html.append("</script></body></html>\n");
        return html.toString();
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

    private native boolean openOrReuseWindowWithHTML(String html) /*-{
        if (!$wnd.plotlyWindows) {
            $wnd.plotlyWindows = [];
        }

        var windowKey = 'sfcrDagBlocksWindow';
        var viewerWindow = $wnd[windowKey];
        if (viewerWindow && !viewerWindow.closed) {
            viewerWindow.document.open();
            viewerWindow.document.write(html);
            viewerWindow.document.close();
            viewerWindow.focus();
            return true;
        }

        viewerWindow = $wnd.open('', windowKey, 'width=1400,height=900');
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
