package com.lushprojects.circuitjs1.client;

import java.util.Map;
import java.util.Set;
import java.util.Vector;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import com.lushprojects.circuitjs1.client.economics.ComputedValues;
import com.lushprojects.circuitjs1.client.electronics.sources.ExtVoltageElm;
import com.lushprojects.circuitjs1.client.electronics.wiring.LabeledNodeElm;

final class CircuitValueSlotManager {
    private final CirSim sim;

    CircuitValueSlotManager(CirSim sim) {
        this.sim = sim;
    }

    void buildCircuitVariableSlots() {
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

        syncAllSlots();
    }

    void syncAllSlots() {
        if (sim.circuitVariables == null || sim.slotNames == null)
            return;
        for (int s = 0; s < sim.slotNames.length; s++) {
            String name = sim.slotNames[s];
            if (name != null)
                sim.circuitVariables[s] = resolveSlotValue(name);
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

    Adjustable findAdjustableByName(String name) {
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
