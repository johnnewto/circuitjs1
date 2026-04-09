package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.economics.EquationDependencyGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Equation dependency graph — traversal and historical edge handling")
class EquationDependencyGraphTest {

    @Test
    @DisplayName("direct and transitive traversal follow same-period references")
    void testDirectAndTransitiveTraversal() {
        EquationDependencyGraph graph = EquationDependencyGraph.buildFromDefinitions(Arrays.asList(
                new EquationDependencyGraph.Definition("A", "1"),
                new EquationDependencyGraph.Definition("B", "A + 1"),
                new EquationDependencyGraph.Definition("C", "B + 1"),
                new EquationDependencyGraph.Definition("D", "C + A")
        ), false);

        assertEquals(setOf("A"), graph.getDirectInputs("B"));
        assertEquals(setOf("B", "C", "D"), graph.getAllDownstream("A"));
        assertEquals(setOf("A", "B", "C"), graph.getAllUpstream("D"));
    }

    @Test
    @DisplayName("historical refs are excluded from same-period graph and included when requested")
    void testHistoricalReferencesToggle() {
        EquationDependencyGraph sameOnly = EquationDependencyGraph.buildFromDefinitions(Arrays.asList(
                new EquationDependencyGraph.Definition("A", "1"),
                new EquationDependencyGraph.Definition("C", "2"),
                new EquationDependencyGraph.Definition("B", "last(A) + C")
        ), false);
        EquationDependencyGraph withHistorical = EquationDependencyGraph.buildFromDefinitions(Arrays.asList(
                new EquationDependencyGraph.Definition("A", "1"),
                new EquationDependencyGraph.Definition("C", "2"),
                new EquationDependencyGraph.Definition("B", "last(A) + C")
        ), true);

        assertEquals(setOf("C"), sameOnly.getDirectInputs("B"));
        assertEquals(setOf("A", "C"), withHistorical.getDirectInputs("B"));
    }

    @Test
    @DisplayName("cyclical nodes are identified from same-period SCCs")
    void testCyclicalNodeDetection() {
        EquationDependencyGraph graph = EquationDependencyGraph.buildFromDefinitions(Arrays.asList(
                new EquationDependencyGraph.Definition("A", "B + 1"),
                new EquationDependencyGraph.Definition("B", "A + 1"),
                new EquationDependencyGraph.Definition("C", "A + 2")
        ), false);

        assertEquals(setOf("A", "B"), graph.getCyclicalNodeNames());
    }

    @Test
    @DisplayName("assignment-style equations use the right-hand side for dependency extraction")
    void testAssignmentRightHandSideExtraction() {
        EquationDependencyGraph graph = EquationDependencyGraph.buildFromDefinitions(Arrays.asList(
                new EquationDependencyGraph.Definition("A", "1"),
                new EquationDependencyGraph.Definition("B", "output = A + 1")
        ), false);

        assertEquals(setOf("A"), graph.getDirectInputs("B"));
    }

    @Test
    @DisplayName("duplicate or comment definitions are ignored after first occurrence")
    void testDuplicateAndCommentFiltering() {
        EquationDependencyGraph graph = EquationDependencyGraph.buildFromDefinitions(Arrays.asList(
                new EquationDependencyGraph.Definition("A", "1"),
                new EquationDependencyGraph.Definition("# Parameters", "ignored"),
                new EquationDependencyGraph.Definition("A", "2"),
                new EquationDependencyGraph.Definition("B", "A + 1")
        ), false);

        assertEquals(2, graph.getNodeNames().size());
        assertTrue(graph.getNodeNames().contains("A"));
        assertTrue(graph.getNodeNames().contains("B"));
        assertEquals(setOf("A"), graph.getDirectInputs("B"));
    }

    private static Set<String> setOf(String... values) {
        return new LinkedHashSet<String>(Arrays.asList(values));
    }
}