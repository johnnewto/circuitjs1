package com.lushprojects.circuitjs1.client.elements.economics;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.elements.Expr;
import com.lushprojects.circuitjs1.client.elements.ExprParser;
import com.lushprojects.circuitjs1.client.registry.HintRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared dependency-graph model for equation-table tracing and DAG rendering.
 *
 * <p>The graph is built from all non-comment equation rows across all {@link EquationTableElm}
 * instances in the current circuit. Each node is an output name and each directed edge
 * {@code A -> B} means equation {@code B} references symbol {@code A}.</p>
 */
public final class EquationDependencyGraph {

    /** Lightweight input record used by tests and graph construction. */
    public static final class Definition {
        public final String name;
        public final String equation;
        public final boolean stock;
        public final String hint;
        public final String hintEquation;

        public Definition(String name, String equation) {
            this(name, equation, false, "", "");
        }

        public Definition(String name, String equation, boolean stock, String hint, String hintEquation) {
            this.name = safeTrim(name);
            this.equation = safeTrim(equation);
            this.stock = stock;
            this.hint = safeTrim(hint);
            this.hintEquation = safeTrim(hintEquation);
        }
    }

    /** Directed dependency edge: from source equation node to target equation node. */
    public static final class EdgeData {
        public final int from;
        public final int to;
        public final boolean historical;
        public final boolean lastDependency;

        EdgeData(int from, int to, boolean historical, boolean lastDependency) {
            this.from = from;
            this.to = to;
            this.historical = historical;
            this.lastDependency = lastDependency;
        }
    }

    private static final class ReferenceSets {
        LinkedHashSet<String> samePeriodRefs = new LinkedHashSet<String>();
        LinkedHashSet<String> lastRefs = new LinkedHashSet<String>();
        LinkedHashSet<String> otherHistoricalRefs = new LinkedHashSet<String>();
    }

    private static final class TarjanContext {
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

    private final ArrayList<String> nodes = new ArrayList<String>();
    private final ArrayList<Boolean> nodeIsStock = new ArrayList<Boolean>();
    private final ArrayList<String> nodeHints = new ArrayList<String>();
    private final ArrayList<String> nodeHintEquations = new ArrayList<String>();
    private final ArrayList<EdgeData> edges = new ArrayList<EdgeData>();
    private final HashMap<String, Integer> nodeIndexByName = new HashMap<String, Integer>();
    private final ArrayList<LinkedHashSet<Integer>> incomingByNode = new ArrayList<LinkedHashSet<Integer>>();
    private final ArrayList<LinkedHashSet<Integer>> outgoingByNode = new ArrayList<LinkedHashSet<Integer>>();
    private int[] blockByNode = new int[0];
    private boolean[] cyclicalByNode = new boolean[0];
    private int blockCount;

    private EquationDependencyGraph() {
    }

    public static EquationDependencyGraph build(CirSim sim, boolean includeHistoricalRefs,
            boolean ignoreExternalSections, boolean ignoreAdjustableRows) {
        EquationDependencyGraph graph = new EquationDependencyGraph();
        graph.populateFromDefinitions(collectDefinitions(sim, ignoreExternalSections, ignoreAdjustableRows),
                includeHistoricalRefs);
        return graph;
    }

    public static EquationDependencyGraph buildFromDefinitions(List<Definition> definitions,
            boolean includeHistoricalRefs) {
        EquationDependencyGraph graph = new EquationDependencyGraph();
        graph.populateFromDefinitions(definitions, includeHistoricalRefs);
        return graph;
    }

    public static Set<String> getCyclicalNodeNames(CirSim sim, boolean includeHistoricalRefs,
            boolean ignoreExternalSections) {
        EquationDependencyGraph graph = build(sim, includeHistoricalRefs, ignoreExternalSections, false);
        return graph.getCyclicalNodeNames();
    }

    public List<String> getNodeNames() {
        return Collections.unmodifiableList(nodes);
    }

    public List<Boolean> getNodeIsStock() {
        return Collections.unmodifiableList(nodeIsStock);
    }

    public List<String> getNodeHints() {
        return Collections.unmodifiableList(nodeHints);
    }

    public List<String> getNodeHintEquations() {
        return Collections.unmodifiableList(nodeHintEquations);
    }

    public List<EdgeData> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public int[] getBlockByNode() {
        return Arrays.copyOf(blockByNode, blockByNode.length);
    }

    public boolean[] getCyclicalByNode() {
        return Arrays.copyOf(cyclicalByNode, cyclicalByNode.length);
    }

    public int getBlockCount() {
        return blockCount;
    }

    public Set<String> getCyclicalNodeNames() {
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        int n = Math.min(nodes.size(), cyclicalByNode.length);
        for (int i = 0; i < n; i++) {
            if (cyclicalByNode[i]) {
                out.add(nodes.get(i));
            }
        }
        return out;
    }

    public Set<String> getDirectInputs(String name) {
        return collectNeighborNames(incomingByNode, getNodeIndex(name));
    }

    public Set<String> getDirectOutputs(String name) {
        return collectNeighborNames(outgoingByNode, getNodeIndex(name));
    }

    public Set<String> getAllUpstream(String name) {
        return collectReachableNames(incomingByNode, getNodeIndex(name));
    }

    public Set<String> getAllDownstream(String name) {
        return collectReachableNames(outgoingByNode, getNodeIndex(name));
    }

    public int getNodeIndex(String name) {
        Integer idx = nodeIndexByName.get(safeTrim(name));
        return idx == null ? -1 : idx.intValue();
    }

    private void populateFromDefinitions(List<Definition> definitions, boolean includeHistoricalRefs) {
        nodes.clear();
        nodeIsStock.clear();
        nodeHints.clear();
        nodeHintEquations.clear();
        edges.clear();
        nodeIndexByName.clear();
        incomingByNode.clear();
        outgoingByNode.clear();
        blockByNode = new int[0];
        cyclicalByNode = new boolean[0];
        blockCount = 0;

        if (definitions == null || definitions.isEmpty()) {
            return;
        }

        ArrayList<Definition> filtered = new ArrayList<Definition>();
        HashSet<String> seenNames = new HashSet<String>();
        for (int i = 0; i < definitions.size(); i++) {
            Definition def = definitions.get(i);
            if (def == null || def.name.length() == 0 || def.equation.length() == 0) {
                continue;
            }
            if (EquationTableElm.isCommentRowName(def.name) || !seenNames.add(def.name)) {
                continue;
            }
            filtered.add(def);
        }

        ArrayList<Definition> sorted = new ArrayList<Definition>(filtered);
        Collections.sort(sorted, new Comparator<Definition>() {
            @Override
            public int compare(Definition a, Definition b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });

        for (int i = 0; i < sorted.size(); i++) {
            Definition def = sorted.get(i);
            nodeIndexByName.put(def.name, Integer.valueOf(i));
            nodes.add(def.name);
            nodeIsStock.add(Boolean.valueOf(def.stock));
            nodeHints.add(def.hint);
            nodeHintEquations.add(def.hintEquation);
            incomingByNode.add(new LinkedHashSet<Integer>());
            outgoingByNode.add(new LinkedHashSet<Integer>());
        }

        HashSet<String> edgeSet = new HashSet<String>();
        for (int i = 0; i < sorted.size(); i++) {
            Definition def = sorted.get(i);
            ReferenceSets refs = parseReferences(def.equation);
            for (String refName : refs.samePeriodRefs) {
                addEdge(edgeSet, refName, i, false, false);
            }
            if (includeHistoricalRefs) {
                for (String refName : refs.otherHistoricalRefs) {
                    addEdge(edgeSet, refName, i, true, false);
                }
                for (String refName : refs.lastRefs) {
                    addEdge(edgeSet, refName, i, true, true);
                }
            }
        }

        computeBlocksAndCycles();
    }

    private void addEdge(HashSet<String> edgeSet, String refName, int toIdx,
            boolean historical, boolean lastDependency) {
        Integer fromIdxObj = nodeIndexByName.get(refName);
        if (fromIdxObj == null) {
            return;
        }
        int fromIdx = fromIdxObj.intValue();
        String edgeKey = fromIdx + ">" + toIdx + ">" + historical + ">" + lastDependency;
        if (!edgeSet.add(edgeKey)) {
            return;
        }
        edges.add(new EdgeData(fromIdx, toIdx, historical, lastDependency));
        outgoingByNode.get(fromIdx).add(Integer.valueOf(toIdx));
        incomingByNode.get(toIdx).add(Integer.valueOf(fromIdx));
    }

    private void computeBlocksAndCycles() {
        int nodeCount = nodes.size();
        ArrayList<ArrayList<Integer>> adjacency = new ArrayList<ArrayList<Integer>>();
        for (int i = 0; i < nodeCount; i++) {
            adjacency.add(new ArrayList<Integer>(outgoingByNode.get(i)));
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
        for (int i = 0; i < edges.size(); i++) {
            EdgeData edge = edges.get(i);
            if (edge.from == edge.to) {
                compHasSelfLoop[tarjan.componentByNode[edge.from]] = true;
            }
        }

        ArrayList<HashSet<Integer>> compAdj = new ArrayList<HashSet<Integer>>();
        for (int i = 0; i < compCount; i++) {
            compAdj.add(new HashSet<Integer>());
        }
        int[] indegree = new int[compCount];
        for (int i = 0; i < edges.size(); i++) {
            EdgeData edge = edges.get(i);
            int fromComp = tarjan.componentByNode[edge.from];
            int toComp = tarjan.componentByNode[edge.to];
            if (fromComp == toComp) {
                continue;
            }
            if (compAdj.get(fromComp).add(Integer.valueOf(toComp))) {
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
            for (Integer toCompObj : compAdj.get(comp)) {
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

        blockByNode = new int[nodeCount];
        cyclicalByNode = new boolean[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            int comp = tarjan.componentByNode[i];
            blockByNode[i] = blockNumberByComp[comp];
            cyclicalByNode[i] = compSize[comp] > 1 || compHasSelfLoop[comp];
        }
        blockCount = Math.max(0, nextBlock - 1);
    }

    private static List<Definition> collectDefinitions(CirSim sim, boolean ignoreExternalSections,
            boolean ignoreAdjustableRows) {
        ArrayList<Definition> out = new ArrayList<Definition>();
        HashSet<String> seenNames = new HashSet<String>();
        if (sim == null) {
            return out;
        }

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
                        if (isIgnoredSectionHeaderComment(table.getCommentText(row))) {
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
                if (EquationTableElm.isCommentRowName(outputName) || !seenNames.add(outputName)) {
                    continue;
                }

                out.add(new Definition(outputName, equation,
                        EquationTableElm.isStockEquation(outputName, equation),
                        safeTrim(HintRegistry.getHint(outputName)),
                        safeTrim(table.getHintExpandedEquationForDisplay(row))));
            }
        }
        return out;
    }

    private static boolean isIgnoredSectionHeaderComment(String commentText) {
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

    private static ReferenceSets parseReferences(String equation) {
        ReferenceSets refs = new ReferenceSets();
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

        LinkedHashSet<String> allRefs = new LinkedHashSet<String>();
        expr.collectSamePeriodRefs(refs.samePeriodRefs);
        expr.collectLastRefs(refs.lastRefs);
        expr.collectAllRefs(allRefs);

        for (String ref : allRefs) {
            if (!refs.samePeriodRefs.contains(ref) && !refs.lastRefs.contains(ref)) {
                refs.otherHistoricalRefs.add(ref);
            }
        }
        return refs;
    }

    private static Expr tryParseExpression(String expressionText) {
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

    private static String extractAssignmentRightHandSide(String equation) {
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

    private Set<String> collectNeighborNames(ArrayList<LinkedHashSet<Integer>> adjacency, int nodeIndex) {
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        if (nodeIndex < 0 || nodeIndex >= adjacency.size()) {
            return out;
        }
        for (Integer idxObj : adjacency.get(nodeIndex)) {
            int idx = idxObj.intValue();
            if (idx >= 0 && idx < nodes.size()) {
                out.add(nodes.get(idx));
            }
        }
        return out;
    }

    private Set<String> collectReachableNames(ArrayList<LinkedHashSet<Integer>> adjacency, int startIndex) {
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        if (startIndex < 0 || startIndex >= adjacency.size()) {
            return out;
        }
        boolean[] visited = new boolean[nodes.size()];
        ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        visited[startIndex] = true;
        queue.add(Integer.valueOf(startIndex));
        while (!queue.isEmpty()) {
            int current = queue.removeFirst().intValue();
            for (Integer nextObj : adjacency.get(current)) {
                int next = nextObj.intValue();
                if (next < 0 || next >= nodes.size() || visited[next]) {
                    continue;
                }
                visited[next] = true;
                queue.addLast(Integer.valueOf(next));
                out.add(nodes.get(next));
            }
        }
        return out;
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}