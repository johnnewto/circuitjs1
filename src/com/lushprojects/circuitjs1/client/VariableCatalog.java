package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.lushprojects.circuitjs1.client.elements.economics.ComputedValues;
import com.lushprojects.circuitjs1.client.elements.economics.StockFlowRegistry;

/**
 * Collects the variables exposed to UI features like the Variable Browser and
 * variable history tracing.
 */
public final class VariableCatalog {
    private VariableCatalog() {
    }

    public static final String TYPE_STOCK = "Stock";
    public static final String TYPE_EQUATION = "Equation";
    public static final String TYPE_NODE = "node";
    public static final String TYPE_PARAMETER = "Parameter";
    public static final String TYPE_VARIABLE = "Variable";

    public static final class VariableEntry implements Comparable<VariableEntry> {
        public final String name;
        public final String type;

        public VariableEntry(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public int compareTo(VariableEntry other) {
            if (other == null || other.name == null) {
                return -1;
            }
            if (name == null) {
                return 1;
            }
            return name.compareToIgnoreCase(other.name);
        }
    }

    public static List<VariableEntry> collectVariables(CirSim sim) {
        ArrayList<VariableEntry> variables = new ArrayList<VariableEntry>();
        LinkedHashSet<String> addedNames = new LinkedHashSet<String>();

        addEntries(variables, addedNames, StockFlowRegistry.getAllStockNames(), TYPE_STOCK);
        addEntries(variables, addedNames, StockFlowRegistry.getAllEquationOutputNames(), TYPE_EQUATION);

        String[] labeledNodes = sim != null ? sim.getSortedLabeledNodeNames() : null;
        if (labeledNodes != null) {
            for (int i = 0; i < labeledNodes.length; i++) {
                addEntry(variables, addedNames, labeledNodes[i], TYPE_NODE);
            }
        }

        addEntries(variables, addedNames, ComputedValues.getAllParameterNames(), TYPE_PARAMETER);
        addEntries(variables, addedNames, StockFlowRegistry.getAllCellEquationVariables(), TYPE_VARIABLE);
        Collections.sort(variables);
        return variables;
    }

    public static Set<String> collectVariableNames(CirSim sim) {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        addNames(names, StockFlowRegistry.getAllStockNames());
        addNames(names, StockFlowRegistry.getAllEquationOutputNames());

        String[] labeledNodes = sim != null ? sim.getSortedLabeledNodeNames() : null;
        if (labeledNodes != null) {
            for (int i = 0; i < labeledNodes.length; i++) {
                if (labeledNodes[i] != null && !labeledNodes[i].isEmpty()) {
                    names.add(labeledNodes[i]);
                }
            }
        }

        addNames(names, ComputedValues.getAllParameterNames());
        addNames(names, StockFlowRegistry.getAllCellEquationVariables());
        return names;
    }

    private static void addEntries(List<VariableEntry> variables, Set<String> addedNames, Set<String> names, String type) {
        if (names == null || names.isEmpty()) {
            return;
        }
        for (String name : names) {
            addEntry(variables, addedNames, name, type);
        }
    }

    private static void addEntry(List<VariableEntry> variables, Set<String> addedNames, String name, String type) {
        if (name == null || name.isEmpty() || addedNames.contains(name)) {
            return;
        }
        variables.add(new VariableEntry(name, type));
        addedNames.add(name);
    }

    private static void addNames(Set<String> allNames, Set<String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        allNames.addAll(names);
    }
}