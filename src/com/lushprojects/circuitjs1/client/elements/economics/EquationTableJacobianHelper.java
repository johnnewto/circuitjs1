package com.lushprojects.circuitjs1.client.elements.economics;

import com.lushprojects.circuitjs1.client.elements.Expr;
import com.lushprojects.circuitjs1.client.elements.ExprParser;
import com.lushprojects.circuitjs1.client.elements.ExprState;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;
import com.lushprojects.circuitjs1.client.elements.electronics.wiring.LabeledNodeElm;
import java.util.LinkedHashSet;

/**
 * EquationTableJacobianHelper — Phase-1 Newton–Raphson Jacobian stamping for EquationTableElm.
 *
 * <h3>Purpose</h3>
 * Without Jacobian linearization, each subiteration of the nonlinear solver re-solves
 * the same system with a fresh right-hand-side stamp. This converges, but may require
 * many subiterations when equation outputs depend on MNA node voltages that themselves
 * change with each solve.
 *
 * Phase-1 Jacobian stamping improves convergence by linearizing each equation around
 * the current operating point. For an equation output <code>f(v₁, v₂, …)</code>:
 * <ul>
 *   <li>The matrix row is augmented with partial-derivative terms
 *       <code>df/dv_i</code> stamped on the off-diagonal.</li>
 *   <li>The right-hand side is adjusted so the linearization is consistent
 *       ({@code rhs = -sign * (f(x₀) - sum(df/dx_i * x₀_i))}).</li>
 * </ul>
 * Partial derivatives are computed numerically via finite difference perturbation
 * (relative step <code>1e-6</code>), which avoids requiring symbolic differentiation.
 *
 * <h3>Supported Modes</h3>
 * <ul>
 *   <li><b>VOLTAGE_MODE</b>: linearizes {@code Vout = f(…)} on the voltage-source equation row.</li>
 *   <li><b>FLOW_MODE</b>: linearizes flow at both source and target endpoint KCL rows,
 *       using opposite sign conventions (source: {@code matrixSign=+1}, target: {@code matrixSign=-1}).</li>
 * </ul>
 * STOCK_MODE uses a capacitor companion model that is already linear in voltage;
 * PARAM_MODE has no MNA rows; neither requires Jacobian support.
 *
 * <h3>Eligibility Guardrails</h3>
 * Jacobian stamping is skipped when:
 * <ul>
 *   <li>The global toggle {@code sim.equationTableNewtonJacobianEnabled} is off.</li>
 *   <li>The row uses a stateful operator ({@code integrate}, {@code diff}, {@code lag}, etc.)
 *       because historical state makes finite-difference derivatives unreliable.</li>
 *   <li>The expression has no MNA labeled-node references — nothing to linearize.</li>
 *   <li>The perturbation produces NaN/Infinity (numerically degenerate).</li>
 * </ul>
 * When ineligible, the caller falls back to direct right-hand-side stamping.
 *
 * <h3>Thread Safety</h3>
 * All methods are stateless utilities; the class has no instance state and cannot be constructed.
 *
 * @see EquationTableElm#stampVoltageModeNewtonJacobian
 * @see EquationTableElm#stampFlowModeNewtonJacobian
 */
final class EquationTableJacobianHelper {
    private EquationTableJacobianHelper() {}

    /** Return same-period refs that are registered PARAM names (skipped from Jacobian). */
    static LinkedHashSet<String> collectSkippedParameterRefs(LinkedHashSet<String> refs) {
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        if (refs == null) {
            return out;
        }
        for (String refName : refs) {
            if (refName == null || refName.isEmpty()) {
                continue;
            }
            if (ComputedValues.isParameterName(refName)) {
                out.add(refName);
            }
        }
        return out;
    }

    /** Build compact status suffix for skipped PARAM refs, e.g. "; skip params=\alpha0,\alpha1,+2". */
    static String formatSkippedParameterRefs(LinkedHashSet<String> skippedParamRefs) {
        if (skippedParamRefs == null || skippedParamRefs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("; skip params=");
        int shown = 0;
        int total = skippedParamRefs.size();
        for (String name : skippedParamRefs) {
            if (shown >= 3) {
                break;
            }
            if (shown > 0) {
                sb.append(",");
            }
            sb.append(name);
            shown++;
        }
        if (total > shown) {
            sb.append(",+").append(total - shown);
        }
        return sb.toString();
    }

    /**
     * Check whether VOLTAGE_MODE Jacobian stamping is eligible for a given row.
     *
     * Returns {@code null} when stamping is allowed, or a human-readable reason string
     * when stamping should be skipped and the caller should fall back to direct
     * right-hand-side stamping.  The reason string is stored for debug display.
     *
     * Ineligibility conditions (checked in order):
     * <ol>
     *   <li>Table is not in MNA mode.</li>
     *   <li>Simulator is unavailable.</li>
     *   <li>Global toggle {@code sim.equationTableNewtonJacobianEnabled} is off.</li>
     *   <li>Row output mode is not {@code VOLTAGE_MODE}.</li>
     *   <li>Row has no compiled expression.</li>
     *   <li>Row has no assigned voltage source index.</li>
     *   <li>Equation contains stateful operators ({@code integrate}, {@code diff}, etc.).</li>
     * </ol>
     *
     * @param table   The equation table owning this row.
     * @param rowData Runtime state for the row being evaluated.
     * @return {@code null} if eligible; a non-null reason string if ineligible.
     */
    static String getVoltageModeIneligibilityReason(EquationTableElm table, EquationTableElm.EquationRow rowData) {
        CirSim sim = CirSim.getInstance();
        if (!table.isMnaMode()) {
            return "ineligible: table not in mna mode";
        }
        if (sim == null) {
            return "ineligible: sim unavailable";
        }
        if (!sim.equationTableNewtonJacobianEnabled) {
            return "ineligible: global toggle off";
        }
        if (rowData.outputMode != EquationTableElm.RowOutputMode.VOLTAGE_MODE) {
            return "ineligible: row mode is " + rowData.outputMode;
        }
        if (rowData.compiledExpr == null) {
            return "ineligible: missing compiled expr";
        }
        if (rowData.rowVoltSource < 0) {
            return "ineligible: no voltage source row";
        }
        if (hasStatefulOperators(rowData.equation)) {
            return "ineligible: stateful expr";
        }
        return null;
    }

    /**
     * Check whether FLOW_MODE Jacobian stamping is eligible for a given row.
     *
     * Mirrors {@link #getVoltageModeIneligibilityReason} but for FLOW_MODE rows.
     * Additional FLOW-mode-specific condition: both {@code sourceNode} and
     * {@code targetNode} must have been resolved to valid (non-negative) node numbers.
     * If either endpoint is still unresolved, Jacobian stamping is deferred.
     *
     * @param table      The equation table owning this row.
     * @param rowData    Runtime state for the row being evaluated.
     * @param sourceNode MNA node number for the flow source endpoint (≥ 0 if resolved).
     * @param targetNode MNA node number for the flow target endpoint (≥ 0 if resolved).
     * @return {@code null} if eligible; a non-null reason string if ineligible.
     */
    static String getFlowModeIneligibilityReason(EquationTableElm table, EquationTableElm.EquationRow rowData,
            int sourceNode, int targetNode) {
        CirSim sim = CirSim.getInstance();
        if (!table.isMnaMode()) {
            return "ineligible: table not in mna mode";
        }
        if (sim == null) {
            return "ineligible: sim unavailable";
        }
        if (!sim.equationTableNewtonJacobianEnabled) {
            return "ineligible: global toggle off";
        }
        if (rowData.outputMode != EquationTableElm.RowOutputMode.FLOW_MODE) {
            return "ineligible: row mode is " + rowData.outputMode;
        }
        if (rowData.compiledExpr == null) {
            return "ineligible: missing compiled expr";
        }
        if (sourceNode < 0 || targetNode < 0) {
            return "ineligible: unresolved flow endpoints";
        }
        if (hasStatefulOperators(rowData.equation)) {
            return "ineligible: stateful expr";
        }
        return null;
    }

    /**
     * Return {@code true} if at least one name in {@code refs} is an MNA labeled node
     * with a positive node number in the current circuit.
     *
     * This is used as a fast pre-check before the more expensive perturbation loop
     * in {@link #stampSingleNodeJacobian}: if no dependency is an MNA node, there are
     * no off-diagonal entries to stamp and the Jacobian call can be skipped entirely.
     *
     * @param refs Set of variable names collected from the expression's same-period references.
     * @return {@code true} if any ref resolves to an MNA node number &gt; 0.
     */
    static boolean hasAnyMnaRefs(LinkedHashSet<String> refs) {
        for (String refName : refs) {
            if (refName == null || refName.isEmpty()) {
                continue;
            }
            if (ComputedValues.isParameterName(refName)) {
                continue;
            }
            Integer labeledNode = LabeledNodeElm.getByName(refName);
            if (labeledNode != null && labeledNode.intValue() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return {@code true} when equation text contains historical reference syntax.
     *
     * This intentionally includes both explicit function form ({@code last(x)}) and
     * postfix aliases ({@code x[-1]} and {@code x(-1)}), which are semantically
     * equivalent in the expression parser.
     */
    static boolean hasHistoricalRefSyntax(String eq) {
        if (eq == null) {
            return false;
        }
        String lower = eq.toLowerCase();
        return lower.contains("last(") || lower.contains("[-1]") || lower.contains("(-1)");
    }

    /**
     * Compute and stamp Newton–Raphson Jacobian entries for a single MNA equation row.
     *
     * <p>For each reference name in {@code refs} that resolves to a labeled MNA node,
     * a finite-difference partial derivative is computed:
     * <pre>
     *   df/dv_i ≈ (f(v₀ + dv) − f(v₀)) / dv
     * </pre>
     * where {@code dv = max(|v₀| * 1e-6, 1e-6)}.  The stamped entries are:
     * <pre>
     *   A[nodeNumber][labeledNode] += matrixSign * (df/dv_i)
     *   RHS[nodeNumber]            += matrixSign * (−f(v₀) + Σ df/dv_i * v₀_i)
     * </pre>
     * This linearizes the equation around the current operating point, reducing
     * the number of Newton subiterations required for convergence.
     *
     * <p>The reference value is temporarily perturbed in both {@code sim.getSolverMatrixState().nodeVoltages[]}
     * and {@code sim.circuitVariables[]} via {@link #perturbReferenceValue}, then
     * restored via {@link #restoreReferenceValue} after each evaluation.
     *
     * <p>Entries are skipped when the perturbed evaluation produces NaN/Infinity;
     * {@code stats[2]} counts such invalid derivatives.
     *
     * @param table       Owning equation table (used for context only).
     * @param expr        Compiled expression to evaluate.
     * @param state       Expression state (slider values, time, integration history).
     * @param refs        Set of variable names to differentiate against.
     * @param fVal        Unperturbed equation value {@code f(v₀)} at the current operating point.
     * @param nodeNumber  MNA row index in the matrix to stamp into (e.g. voltage-source row).
     * @param matrixSign  Sign convention: {@code -1} for VOLTAGE_MODE (drive row subtracts),
     *                    {@code +1} for FLOW source, {@code -1} for FLOW target.
     * @param stats       Three-element array updated in place:
     *                    {@code [0]} = count of refs seen as MNA nodes,
     *                    {@code [1]} = count of valid derivatives stamped,
     *                    {@code [2]} = count of invalid (NaN/Inf) derivatives skipped.
     * @return Number of Jacobian entries actually stamped ({@code stats[1]} equivalent).
     */
    static int stampSingleNodeJacobian(EquationTableElm table, Expr expr, ExprState state,
            LinkedHashSet<String> refs, double fVal, int nodeNumber, double matrixSign, int[] stats) {
        CirSim sim = CirSim.getInstance();
        if (sim == null) return 0;
        if (nodeNumber <= 0) return 0;
        double rhs = -matrixSign * fVal;
        int count = 0;

        for (String refName : refs) {
            if (refName == null || refName.isEmpty()) continue;
            if (ComputedValues.isParameterName(refName)) continue;

            Integer labeledNode = LabeledNodeElm.getByName(refName);
            if (labeledNode == null || labeledNode.intValue() <= 0) continue;
            stats[0]++;

            double baseValue = sim.resolveSlotValueForUi(refName);
            double dv = Math.abs(baseValue) * 1e-6;
            if (dv < 1e-6) dv = 1e-6;

            double[] restore = perturbReferenceValue(sim, refName, labeledNode.intValue(), baseValue + dv);
            double perturbedValue;
            try {
                perturbedValue = expr.eval(state);
            } finally {
                restoreReferenceValue(sim, labeledNode.intValue(), restore);
            }

            if (Double.isNaN(perturbedValue) || Double.isInfinite(perturbedValue)) {
                stats[2]++;
                continue;
            }

            double dx = (perturbedValue - fVal) / dv;
            if (Double.isNaN(dx) || Double.isInfinite(dx)) {
                stats[2]++;
                continue;
            }

            sim.stampMatrix(nodeNumber, labeledNode.intValue(), matrixSign * dx);
            rhs += matrixSign * dx * baseValue;
            stats[1]++;
            count++;
        }

        if (count > 0) sim.stampRightSide(nodeNumber, rhs);
        return count;
    }

    /**
     * Return {@code true} if the equation string contains any stateful operator name.
     *
     * Stateful operators maintain history across timesteps, making their finite-difference
     * derivatives unreliable (the perturbed evaluation would corrupt the history state).
     * Equations containing these operators are excluded from Jacobian stamping.
     *
    * Checked names (case-insensitive): {@code integrate(}, {@code diff(}, {@code lag(},
    * {@code smooth(}, {@code delay(}.
     *
     * @param eq  Raw equation string (may be {@code null}).
     * @return {@code true} if any stateful operator is present.
     */
    private static boolean hasStatefulOperators(String eq) {
        if (eq == null) {
            return false;
        }
        String lower = eq.toLowerCase();
        return lower.contains("integrate(") || lower.contains("diff(") || lower.contains("lag(")
            || lower.contains("smooth(") || lower.contains("delay(");
    }

    /**
     * Temporarily overwrite a reference variable's value in the simulator state.
     *
     * Two parallel data structures are patched:
     * <ul>
     *   <li>{@code sim.getSolverMatrixState().nodeVoltages[labeledNode - 1]} — the MNA solved-voltage array used
     *       by direct node lookups.</li>
     *   <li>{@code sim.circuitVariables[slot]} — the fast global-slot array used by
     *       expressions after {@code resolveGSlot()} has been called.</li>
     * </ul>
     * Both must be patched because different expression types read from different locations.
     *
     * The original values are returned as a {@code double[3]} restore token:
     * {@code [0]} = old nodeVoltage, {@code [1]} = old slot value,
     * {@code [2]} = slot index (or NaN if no slot was found).
     *
     * @param sim        Active circuit simulator.
     * @param refName    Variable name being perturbed (needed for slot lookup).
     * @param labeledNode MNA node number for the reference (1-based).
     * @param newValue   Perturbed value to write.
     * @return Restore token; pass to {@link #restoreReferenceValue} after evaluation.
     */
    private static double[] perturbReferenceValue(CirSim sim, String refName, int labeledNode, double newValue) {
        double oldNodeVoltage = 0.0;
        if (sim.getSolverMatrixState().nodeVoltages != null && labeledNode > 0 && labeledNode - 1 < sim.getSolverMatrixState().nodeVoltages.length) {
            oldNodeVoltage = sim.getSolverMatrixState().nodeVoltages[labeledNode - 1];
            sim.getSolverMatrixState().nodeVoltages[labeledNode - 1] = newValue;
        }

        double oldSlotValue = Double.NaN;
        double slotIndex = Double.NaN;
        if (sim.nameToSlot != null && sim.circuitVariables != null) {
            Integer slot = sim.nameToSlot.get(refName);
            if (slot != null && slot.intValue() >= 0 && slot.intValue() < sim.circuitVariables.length) {
                int s = slot.intValue();
                oldSlotValue = sim.circuitVariables[s];
                sim.circuitVariables[s] = newValue;
                slotIndex = s;
            }
        }

        return new double[] { oldNodeVoltage, oldSlotValue, slotIndex };
    }

    /**
     * Restore simulator state after a finite-difference perturbation.
     *
     * Reverses the changes made by {@link #perturbReferenceValue}, reinstating the original
     * values in both {@code sim.getSolverMatrixState().nodeVoltages} and {@code sim.circuitVariables}.
     *
     * @param sim         Active circuit simulator.
     * @param labeledNode MNA node number that was perturbed (1-based).
     * @param restore     Restore token returned by {@link #perturbReferenceValue}.
     */
    private static void restoreReferenceValue(CirSim sim, int labeledNode, double[] restore) {
        if (restore == null || restore.length < 3) {
            return;
        }

        if (sim.getSolverMatrixState().nodeVoltages != null && labeledNode > 0 && labeledNode - 1 < sim.getSolverMatrixState().nodeVoltages.length) {
            sim.getSolverMatrixState().nodeVoltages[labeledNode - 1] = restore[0];
        }

        if (!Double.isNaN(restore[2]) && sim.circuitVariables != null) {
            int s = (int) restore[2];
            if (s >= 0 && s < sim.circuitVariables.length && !Double.isNaN(restore[1])) {
                sim.circuitVariables[s] = restore[1];
            }
        }
    }
}
