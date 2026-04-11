package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.ui.EditInfo;



import com.lushprojects.circuitjs1.client.elements.Expr;

import java.util.Map;
import java.util.Set;
import java.util.Vector;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import com.lushprojects.circuitjs1.client.elements.economics.ComputedValues;
import com.lushprojects.circuitjs1.client.elements.electronics.sources.ExtVoltageElm;
import com.lushprojects.circuitjs1.client.elements.electronics.wiring.LabeledNodeElm;

final class CircuitValueSlotManager {
    private final CirSim sim;

    // Per-slot resolution strategy (computed once at build time to avoid repeated HashMap probes)
    private static final int RESOLVE_COMPUTED = 0;      // ComputedValues.getComputedValue(name)
    private static final int RESOLVE_FLOW = 1;           // ComputedValues.getComputedFlowValue(name)
    private static final int RESOLVE_LABELED_NODE = 2;   // nodeVoltages[nodeIndex]
    private static final int RESOLVE_PARAMETER = 3;      // ComputedValues.getComputedValue(name) (parameter priority)
    private static final int RESOLVE_FLOW_OR_VALUE = 4;  // ComputedValues.getComputedFlowOrValue(name) (non-MNA mode)

    /** Resolution type for each slot, indexed by slot number. */
    private int[] slotResolveType;
    /** For RESOLVE_LABELED_NODE slots, the (node-1) index into nodeVoltages[]. */
    private int[] slotNodeIndex;
    /** For RESOLVE_FLOW slots, the pre-computed flow key string. */
    private String[] slotFlowKey;

    CircuitValueSlotManager(CirSim sim) {
        this.sim = sim;
    }

    void buildCircuitVariableSlots() {
        sim.getVariableHistoryStore().clearVariableSeries();
        sim.getVariableHistoryStore().clearNonVariableSeries();
        sim.nameToSlot = new java.util.HashMap<String, Integer>();
        int slot = 0;

        String[] labeledNames = LabeledNodeElm.getSortedLabeledNodeNames();
        if (labeledNames != null) {
            for (String name : labeledNames) {
                if (name != null && !sim.nameToSlot.containsKey(name))
                    sim.nameToSlot.put(name, slot++);
            }
        }

        Set<String> allComputedNames = ComputedValues.getAllNames();
        if (allComputedNames != null) {
            for (String name : allComputedNames) {
                if (name != null && !sim.nameToSlot.containsKey(name))
                    sim.nameToSlot.put(name, slot++);
            }
        }

        Set<String> allParamNames = ComputedValues.getAllParameterNames();
        CirSim.console("[buildSlots] paramNames=" + (allParamNames != null ? allParamNames.toString() : "NULL"));
        if (allParamNames != null) {
            for (String name : allParamNames) {
                if (name != null && !sim.nameToSlot.containsKey(name))
                    sim.nameToSlot.put(name, slot++);
            }
        }

        sim.circuitVariables = new double[slot];
        sim.slotNames = new String[slot];
        for (Map.Entry<String, Integer> e : sim.nameToSlot.entrySet())
            sim.slotNames[e.getValue()] = e.getKey();

        // Build per-slot resolution strategy to avoid repeated HashMap probes at runtime
        buildSlotResolutionStrategies(slot);

        sim.getVariableHistoryStore().refreshTrackedVariableNames(sim);

        syncAllSlots();
    }

    /** Determine the fastest resolution path for each slot, once at analysis time. */
    private void buildSlotResolutionStrategies(int slotCount) {
        slotResolveType = new int[slotCount];
        slotNodeIndex = new int[slotCount];
        slotFlowKey = new String[slotCount];

        boolean mnaMode = sim.isEquationTableMnaMode();
        for (int s = 0; s < slotCount; s++) {
            String name = sim.slotNames[s];
            if (name == null) {
                slotResolveType[s] = RESOLVE_COMPUTED;
                continue;
            }
            if (!mnaMode) {
                slotResolveType[s] = RESOLVE_FLOW_OR_VALUE;
                continue;
            }
            // MNA mode: determine the primary resolution path for this name
            if (ComputedValues.isParameterName(name)) {
                slotResolveType[s] = RESOLVE_PARAMETER;
            } else {
                // Check if this name has a flow value
                String flowKey = ComputedValues.getFlowComputedKeyForName(name);
                if (flowKey != null && ComputedValues.hasComputedValue(flowKey)) {
                    slotResolveType[s] = RESOLVE_FLOW;
                    slotFlowKey[s] = flowKey;
                } else if (name.endsWith(".flow")) {
                    // Name IS a flow key itself — resolve directly
                    slotResolveType[s] = RESOLVE_COMPUTED;
                } else {
                    Integer node = LabeledNodeElm.getByName(name);
                    if (node != null && node != 0) {
                        slotResolveType[s] = RESOLVE_LABELED_NODE;
                        slotNodeIndex[s] = node.intValue() - 1;
                    } else {
                        slotResolveType[s] = RESOLVE_COMPUTED;
                    }
                }
            }
        }
    }

    void syncAllSlots() {
        if (sim.circuitVariables == null || sim.slotNames == null)
            return;
        final int len = sim.slotNames.length;
        final double[] vars = sim.circuitVariables;
        final String[] names = sim.slotNames;
        final double[] nodeVoltages = (sim.getSolverMatrixState() != null) ? sim.getSolverMatrixState().nodeVoltages : null;

        for (int s = 0; s < len; s++) {
            String name = names[s];
            if (name == null) continue;
            vars[s] = resolveSlotFast(s, name, nodeVoltages);
        }
    }

    /** Fast slot resolution using pre-computed strategy. Falls back to full resolution if needed. */
    private double resolveSlotFast(int s, String name, double[] nodeVoltages) {
        if (slotResolveType == null) return resolveSlotValue(name);
        switch (slotResolveType[s]) {
        case RESOLVE_PARAMETER: {
            Double v = ComputedValues.getComputedValue(name);
            if (v != null) return v;
            // Parameter may not have a value yet; fall through to full resolution
            return resolveSlotValue(name);
        }
        case RESOLVE_FLOW: {
            String fk = slotFlowKey[s];
            if (fk != null) {
                Double fv = ComputedValues.getComputedValue(fk);
                if (fv != null) return fv;
            }
            // Flow key not resolved yet; fall through
            return resolveSlotValue(name);
        }
        case RESOLVE_LABELED_NODE: {
            int ni = slotNodeIndex[s];
            if (nodeVoltages != null && ni >= 0 && ni < nodeVoltages.length)
                return nodeVoltages[ni];
            return resolveSlotValue(name);
        }
        case RESOLVE_FLOW_OR_VALUE: {
            Double cv = ComputedValues.getComputedFlowOrValue(name);
            return cv != null ? cv : 0.0;
        }
        case RESOLVE_COMPUTED:
        default: {
            Double cv = ComputedValues.getComputedValue(name);
            return cv != null ? cv : 0.0;
        }
        }
    }

    double resolveSlotValue(String name) {
        if (sim.isEquationTableMnaMode()) {
            if (ComputedValues.isParameterName(name)) {
                Double v = ComputedValues.getComputedValue(name);
                if (v != null)
                    return v;
            }
            Double flowVal = ComputedValues.getComputedFlowValue(name);
            if (flowVal != null)
                return flowVal;
            Integer node = LabeledNodeElm.getByName(name);
            if (node != null && node != 0 && sim.getSolverMatrixState().nodeVoltages != null && (node - 1) < sim.getSolverMatrixState().nodeVoltages.length)
                return sim.getSolverMatrixState().nodeVoltages[node - 1];
            Double cv = ComputedValues.getComputedValue(name);
            return cv != null ? cv : 0.0;
        } else {
            Double cv = ComputedValues.getComputedFlowOrValue(name);
            return cv != null ? cv : 0.0;
        }
    }

    double getLabeledNodeVoltage(String name) {
        Integer node = LabeledNodeElm.getByName(name);
        if (node == null || node == 0)
            return 0;
        return sim.getSolverMatrixState().nodeVoltages[node.intValue() - 1];
    }

    String getUnresolvedReferencesMessage() {
        Vector<String> unresolved = Expr.getUnresolvedReferences();
        if (unresolved.size() == 0)
            return null;
        StringBuilder sb = new StringBuilder("Not found: ");
        for (int i = 0; i < unresolved.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(unresolved.get(i));
        }
        return sb.toString();
    }

    void setExtVoltage(String name, double v) {
        for (int i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof ExtVoltageElm) {
                ExtVoltageElm eve = (ExtVoltageElm) ce;
                if (eve.getName().equals(name))
                    eve.setVoltage(v);
            }
        }
    }

    private Adjustable findAdjustableByName(String name) {
        for (int i = 0; i < sim.adjustables.size(); i++) {
            Adjustable adj = sim.adjustables.get(i);
            if (adj.sliderText != null && adj.sliderText.equals(name)) {
                return adj;
            }
        }
        return null;
    }

    double getSliderValue(String name) {
        Adjustable adj = findAdjustableByName(name);
        if (adj != null) {
            EditInfo ei = adj.elm.getEditInfo(adj.editItem);
            if (ei != null) {
                return ei.value;
            }
        }
        return Double.NaN;
    }

    boolean setSliderValue(String name, double value) {
        Adjustable adj = findAdjustableByName(name);
        if (adj != null) {
            adj.setSliderValue(value);
            EditInfo ei = adj.elm.getEditInfo(adj.editItem);
            if (ei != null) {
                ei.value = value;
                adj.elm.setEditValue(adj.editItem, ei);
                sim.analyzeFlag = true;
            sim.getUiPanelManager().refreshModelInfoEditorAfterCircuitMutation();

                if (adj.label != null) {
                    String valueStr = adj.getFormattedValue(ei, value);
                    adj.updateLabelHTML(adj.sliderText, valueStr);
                }
                return true;
            }
        }
        return false;
    }

    private JsArrayString getJSArrayString() {
        return JavaScriptObject.createArray().cast();
    }

    JsArrayString getSliderNames() {
        JsArrayString names = getJSArrayString();
        for (int i = 0; i < sim.adjustables.size(); i++) {
            Adjustable adj = sim.adjustables.get(i);
            if (adj.sliderText != null) {
                names.push(adj.sliderText);
            }
        }
        return names;
    }

    JsArrayString getLabeledNodeNames() {
        JsArrayString names = getJSArrayString();
        Set<String> nodeNames = LabeledNodeElm.getAllNodeNames();
        if (nodeNames != null) {
            for (String name : nodeNames) {
                names.push(name);
            }
        }
        return names;
    }

    double getLabeledNodeValue(String name) {
        Double computed = ComputedValues.getComputedValue(name);
        if (computed != null) {
            return computed;
        }
        return getLabeledNodeVoltage(name);
    }

    JsArrayString getComputedValueNames() {
        JsArrayString names = getJSArrayString();
        Set<String> valueNames = ComputedValues.getAllNames();
        if (valueNames != null) {
            for (String name : valueNames) {
                names.push(name);
            }
        }
        return names;
    }
}
