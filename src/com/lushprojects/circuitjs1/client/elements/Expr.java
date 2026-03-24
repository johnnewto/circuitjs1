package com.lushprojects.circuitjs1.client.elements;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.io.LookupTableRegistry;
import com.lushprojects.circuitjs1.client.elements.economics.ComputedValues;
import com.lushprojects.circuitjs1.client.elements.electronics.wiring.LabeledNodeElm;
import java.util.Vector;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

public class Expr {
    // Global tracking of unresolved node references during evaluation
    static Vector<String> unresolvedReferences = new Vector<String>();
    
    // Counter for assigning unique lag buffer indices at parse time
    static int nextLagIndex = 0;
	static int nextSmoothIndex = 0;
    
    /** Reset the lag index counter (call when starting a new expression parse) */
    static void resetLagIndexCounter() {
	nextLagIndex = 0;
	nextSmoothIndex = 0;
    }
    
	/**
	 * Explicit evaluation context passed through expression evaluation.
	 * This replaces implicit global mode flags and makes value-source behavior
	 * deterministic per evaluation call.
	 */
	public static class EvaluationContext {
	final boolean useConvergedValues;
	final Double explicitTimeStep;

	EvaluationContext(boolean useConvergedValues) {
	    this(useConvergedValues, null);
	}

	EvaluationContext(boolean useConvergedValues, Double explicitTimeStep) {
	    this.useConvergedValues = useConvergedValues;
	    this.explicitTimeStep = explicitTimeStep;
	}
	}

	private static final EvaluationContext CURRENT_CONTEXT = new EvaluationContext(false);
	private static final EvaluationContext CONVERGED_CONTEXT = new EvaluationContext(true);

	public static EvaluationContext getEvaluationContext(boolean useConvergedValues) {
	return useConvergedValues ? CONVERGED_CONTEXT : CURRENT_CONTEXT;
	}

	public static EvaluationContext getEvaluationContext(boolean useConvergedValues, double dt) {
	return new EvaluationContext(useConvergedValues, Double.valueOf(dt));
	}

	private static double resolveTimeStep(EvaluationContext context) {
	if (context != null && context.explicitTimeStep != null) {
	    return context.explicitTimeStep.doubleValue();
	}
	if (CirSim.getInstance() != null) {
	    return CirSim.getInstance().getTimeStep();
	}
	return 0;
	}

    // Lightweight runtime profiler for expression access paths.
    // - node ref path:    E_NODE_REF (label/computed-value HashMap lookup)
    // - local slot path:  E_A/E_DADT/E_LASTA (ExprState array index)
    // - global slot path: E_GSLOT (circuitVariables[] array index – fastest)
    static boolean perfProbeEnabled = false;
    static long perfNodeRefEvalCount = 0;
    static long perfNodeRefEvalTimeNanos = 0;
    static long perfLocalSlotEvalCount = 0;
    static long perfLocalSlotEvalTimeNanos = 0;
    static long perfGlobalSlotEvalCount = 0;
    // Note: no perfGlobalSlotEvalTimeNanos — E_GSLOT is count-only (per-call timing is dominated by JS timestamp overhead)
    // Captures the first N unique node names still going through E_NODE_REF (diagnostic)
    static java.util.ArrayList<String> perfNodeRefNameSamples = new java.util.ArrayList<String>();
    static final int PERF_SAMPLE_LIMIT = 30;

    public static void setPerfProbeEnabled(boolean enabled) {
	perfProbeEnabled = enabled;
    }

    public static boolean isPerfProbeEnabled() {
	return perfProbeEnabled;
    }

    public static void resetPerfProbe() {
	perfNodeRefEvalCount = 0;
	perfNodeRefEvalTimeNanos = 0;
	perfLocalSlotEvalCount = 0;
	perfLocalSlotEvalTimeNanos = 0;
	perfGlobalSlotEvalCount = 0;
	perfNodeRefNameSamples.clear();
    }

    public static String getPerfProbeReport() {
	double nodeAvgNs = (perfNodeRefEvalCount > 0)
	    ? (double) perfNodeRefEvalTimeNanos / (double) perfNodeRefEvalCount
	    : 0;
	double slotAvgNs = (perfLocalSlotEvalCount > 0)
	    ? (double) perfLocalSlotEvalTimeNanos / (double) perfLocalSlotEvalCount
	    : 0;
	// globalSlot is count-only (no per-call timing — see E_GSLOT case)
	String ratioText;
	if (slotAvgNs > 0) {
	    ratioText = " ratio(nodeRef/localSlot)=" + (nodeAvgNs / slotAvgNs);
	} else {
	    ratioText = " ratio=n/a";
	}
	String samples = perfNodeRefNameSamples.isEmpty() ? "(none)" : perfNodeRefNameSamples.toString();
	return "ExprPerf enabled=" + perfProbeEnabled +
	    " nodeRef[count=" + perfNodeRefEvalCount +
	    ", avgNs=" + nodeAvgNs +
	    ", totalNs=" + perfNodeRefEvalTimeNanos + "]" +
	    " localSlot[count=" + perfLocalSlotEvalCount +
	    ", avgNs=" + slotAvgNs +
	    ", totalNs=" + perfLocalSlotEvalTimeNanos + "]" +
	    " globalSlot[count=" + perfGlobalSlotEvalCount + " (count-only)]" +
	    ratioText +
	    " nodeRefNames=" + samples;
    }

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Performance")
	private interface PerformanceLike {
	double now();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Window")
	private interface WindowLike {
	@JsProperty
	PerformanceLike getPerformance();
	}

	@JsProperty(namespace = JsPackage.GLOBAL, name = "window")
	private static native WindowLike getWindow();

	@JsMethod(namespace = JsPackage.GLOBAL, name = "Date.now")
	private static native double dateNow();

	private static double perfNowMs() {
	WindowLike window = getWindow();
	PerformanceLike performance = (window == null) ? null : window.getPerformance();
	return (performance != null) ? performance.now() : dateNow();
	}

    private static long getPerfNowNanos() {
        // Use performance.now() (microsecond resolution in browsers) converted to nanoseconds.
	return (long)(perfNowMs() * 1_000_000.0);
    }

    private static void recordNodeRefTiming(long startNanos) {
	if (!perfProbeEnabled)
	    return;
	perfNodeRefEvalCount++;
	perfNodeRefEvalTimeNanos += (getPerfNowNanos() - startNanos);
    }

    private static void recordLocalSlotTiming(long startNanos) {
	if (!perfProbeEnabled)
	    return;
	perfLocalSlotEvalCount++;
	perfLocalSlotEvalTimeNanos += (getPerfNowNanos() - startNanos);
    }

    private static double returnNodeRefValue(double value, long startNanos) {
	recordNodeRefTiming(startNanos);
	return value;
    }

    private static Double getComputedByMode(String name, EvaluationContext context) {
	return context.useConvergedValues
	    ? ComputedValues.getConvergedValue(name)
	    : ComputedValues.getComputedValue(name);
    }

    private static Double getComputedFlowByMode(String name, EvaluationContext context) {
	return context.useConvergedValues
	    ? ComputedValues.getConvergedFlowValue(name)
	    : ComputedValues.getComputedFlowValue(name);
    }

    private static Double getComputedFlowOrValueByMode(String name, EvaluationContext context) {
	return context.useConvergedValues
	    ? ComputedValues.getConvergedFlowOrValue(name)
	    : ComputedValues.getComputedFlowOrValue(name);
    }

    /** Clear the unresolved references list (call at start of each timestep) */
    public static void clearUnresolvedReferences() {
	unresolvedReferences.clear();
    }
    
    /** Get all unresolved references found during evaluation */
    public static Vector<String> getUnresolvedReferences() {
	return unresolvedReferences;
    }
    
    Expr(Expr e1, Expr e2, int v) {
	children = new Vector<Expr>();
	children.add(e1);
	if (e2 != null)
	    children.add(e2);
	type = v;
    }
    Expr(int v, double vv) {
	type = v;
	value = vv;
    }
    Expr(int v) {
	type = v;
    }
    Expr(int v, String name) {
	type = v;
	nodeName = name;
    }
    
    // Constructor for E_LAG with assigned buffer index
    Expr(Expr e1, Expr e2, int v, int assignedLagIndex) {
	children = new Vector<Expr>();
	children.add(e1);
	if (e2 != null)
	    children.add(e2);
	type = v;
	lagIndex = assignedLagIndex;
    }
    
    /**
     * Check if this expression is a compile-time constant.
     * Returns true if the expression tree contains only literal values (E_VAL)
     * and pure math operations on them — no time, variable references,
     * integrate/diff/lag/last, slider variables, or other dynamic inputs.
     * 
     * This allows EquationTableElm to treat rows with constant expressions
     * (e.g., parameter definitions like "rl ~ 0.025") as linear elements
     * that can be stamped once instead of recomputed every subiteration.
     */
    boolean isConstant() {
	switch (type) {
	case E_VAL:
	    return true;
	// Pure math operations: constant if all children are constant
	case E_ADD: case E_SUB: case E_MUL: case E_DIV: case E_POW:
	case E_MOD: case E_UMINUS: case E_NOT:
	case E_OR: case E_AND:
	case E_EQUALS: case E_NEQ: case E_LEQ: case E_GEQ:
	case E_LESS: case E_GREATER:
	case E_SIN: case E_COS: case E_TAN:
	case E_ASIN: case E_ACOS: case E_ATAN:
	case E_SINH: case E_COSH: case E_TANH:
	case E_ABS: case E_EXP: case E_LOG: case E_SQRT:
	case E_FLOOR: case E_CEIL:
	case E_MIN: case E_MAX: case E_CLAMP:
	case E_PWR: case E_PWRS:
	case E_STEP: case E_SELECT:
	 case E_PWL: case E_PWLX:
	case E_TERNARY:
	    if (children != null) {
		for (int i = 0; i < children.size(); i++) {
		    if (!children.get(i).isConstant())
			return false;
		}
	    }
	    return true;
	// Everything else is dynamic (time, node refs, integrate, diff, lag, etc.)
	default:
	    return false;
	}
    }
    
    /**
     * Walk this expression tree, converting E_NODE_REF nodes to E_GSLOT where the name
     * has a pre-assigned slot in the circuit-global array.  Also re-resolves E_GSLOT
     * nodes in case slot indices changed after a re-analyzeCircuit call.
     * <p>
     * nodeName is preserved in both cases so the node can be re-resolved later.
     *
     * @param nameToSlot  map built by CirSim.buildCircuitVariableSlots() after stamp phase
     */
    public void resolveGSlot(java.util.HashMap<String, Integer> nameToSlot) {
	if (children != null) {
	    for (int i = 0; i < children.size(); i++)
		children.get(i).resolveGSlot(nameToSlot);
	}
	if ((type == E_NODE_REF || type == E_GSLOT) && nodeName != null) {
	    Integer slot = nameToSlot.get(nodeName);
	    if (slot != null) {
		type = E_GSLOT;
		value = (double) slot;
	    } else if (type == E_GSLOT) {
		// Slot no longer available after circuit change — demote back to HashMap lookup
		type = E_NODE_REF;
	    }
	}
    }

    /**
     * Same as resolveGSlot but also counts outcomes.
     * Returns int[3]: [newly converted, already E_GSLOT, stayed E_NODE_REF].
     */
    int[] resolveGSlotCounted(java.util.HashMap<String, Integer> nameToSlot) {
	int converted = 0, alreadySlot = 0, stayed = 0;
	if (children != null) {
	    for (int i = 0; i < children.size(); i++) {
		int[] sub = children.get(i).resolveGSlotCounted(nameToSlot);
		converted += sub[0]; alreadySlot += sub[1]; stayed += sub[2];
	    }
	}
	if (nodeName != null) {
	    if (type == E_NODE_REF) {
		Integer slot = nameToSlot.get(nodeName);
		if (slot != null) {
		    type = E_GSLOT;
		    value = (double) slot;
		    converted++;
		} else {
		    stayed++;
		}
	    } else if (type == E_GSLOT) {
		Integer slot = nameToSlot.get(nodeName);
		if (slot != null) {
		    value = (double) slot; // refresh slot index
		    alreadySlot++;
		} else {
		    type = E_NODE_REF; // demote
		    stayed++;
		}
	    }
	}
	return new int[] { converted, alreadySlot, stayed };
    }

    /**
     * Collect same-period node references used by this expression.
     *
     * This is used for SCC dependency graph extraction. References nested inside
     * stateful historical operators are excluded because they do not create
     * same-period algebraic coupling:
     * - last(x)
     * - lag(x, d)
     * - integrate(x)
     * - diff(x)
     * - smooth(x, theta)
     *
     * @param out target set to populate with referenced names
     */
    public void collectSamePeriodRefs(java.util.Set<String> out) {
	collectSamePeriodRefsInternal(out, false);
    }

	/**
	 * Collect all node references used by this expression, including references
	 * nested inside historical operators like lag(), last(), integrate(), diff(),
	 * and smooth().
	 *
	 * @param out target set to populate with referenced names
	 */
	public void collectAllRefs(java.util.Set<String> out) {
	collectAllRefsInternal(out);
	}

    private void collectSamePeriodRefsInternal(java.util.Set<String> out, boolean inHistoricalContext) {
	if (out == null)
	    return;

	boolean childHistoricalContext = inHistoricalContext;
	switch (type) {
	case E_LAST:
	case E_LAG:
	case E_INTEGRATE:
	case E_DIFF:
	case E_SMOOTH:
	case E_DELAY:
	    childHistoricalContext = true;
	    break;
	default:
	    break;
	}

	if (!inHistoricalContext && (type == E_NODE_REF || type == E_GSLOT) && nodeName != null) {
	    String trimmed = nodeName.trim();
	    if (!trimmed.isEmpty())
		out.add(trimmed);
	}

	if (children == null)
	    return;

	for (int i = 0; i < children.size(); i++)
	    children.get(i).collectSamePeriodRefsInternal(out, childHistoricalContext);
    }

    private void collectAllRefsInternal(java.util.Set<String> out) {
	if (out == null)
	    return;

	if ((type == E_NODE_REF || type == E_GSLOT) && nodeName != null) {
	    String trimmed = nodeName.trim();
	    if (!trimmed.isEmpty())
		out.add(trimmed);
	}

	if (children == null)
	    return;

	for (int i = 0; i < children.size(); i++)
	    children.get(i).collectAllRefsInternal(out);
    }
    
    /**
     * Check if this expression is a linear combination of node references and constants.
     * Linear expressions can be stamped as VCVS without needing doStep().
     * 
     * Linear forms:
     * - Constants: 5, 2+3
     * - Node refs: Cs, Cd
     * - Addition/subtraction of linear terms: Cs + Is, Y - C
     * - Scalar multiplication: 2 * Cs, Cs * 3
     * - Unary minus: -Cs
     * 
     * Non-linear (require doStep):
     * - Products of node refs: Cs * Is
     * - Division by node refs: Y / pr
     * - Transcendental functions of node refs: sin(Cs)
     * - Time-dependent: t, integrate(), diff(), lag()
     */
    boolean isLinearCombination() {
	return getLinearTerms(new java.util.HashMap<String, Double>()) != null;
    }
    
    /**
     * Extract linear terms from this expression.
     * Returns the constant term if successful, or null if not a linear combination.
     * 
     * @param terms Map to populate with nodeName -> coefficient
     * @return The constant term, or null if expression is not linear
     */
    Double getLinearTerms(java.util.HashMap<String, Double> terms) {
	return getLinearTermsInternal(terms, 1.0);
    }
    
    /**
     * Internal recursive helper for extracting linear terms with a multiplier.
     * @param terms Map to populate with nodeName -> coefficient
     * @param multiplier Current multiplier from parent expressions
     * @return The constant term contribution, or null if not linear
     */
    private Double getLinearTermsInternal(java.util.HashMap<String, Double> terms, double multiplier) {
	Expr left = null;
	Expr right = null;
	if (children != null && children.size() > 0) {
	    left = children.firstElement();
	    if (children.size() >= 2)
		right = children.get(1);
	}
	
	switch (type) {
	case E_VAL:
	    // Constant: contributes to constant term
	    return value * multiplier;
	    
	case E_NODE_REF:
	case E_GSLOT:
	    // Node reference: add coefficient to terms map (E_GSLOT preserves nodeName)
	    if (nodeName == null) return null;
	    Double existing = terms.get(nodeName);
	    terms.put(nodeName, (existing != null ? existing : 0.0) + multiplier);
	    return 0.0;  // No constant contribution
	    
	case E_UMINUS:
	    // Unary minus: negate multiplier
	    return left.getLinearTermsInternal(terms, -multiplier);
	    
	case E_ADD:
	    // Addition: sum of both sides
	    Double leftConst = left.getLinearTermsInternal(terms, multiplier);
	    if (leftConst == null) return null;
	    Double rightConst = right.getLinearTermsInternal(terms, multiplier);
	    if (rightConst == null) return null;
	    return leftConst + rightConst;
	    
	case E_SUB:
	    // Subtraction: left - right
	    leftConst = left.getLinearTermsInternal(terms, multiplier);
	    if (leftConst == null) return null;
	    rightConst = right.getLinearTermsInternal(terms, -multiplier);
	    if (rightConst == null) return null;
	    return leftConst + rightConst;
	    
	case E_MUL:
	    // Multiplication: one side must be constant
	    if (left.isConstant()) {
		// Left is constant, multiply into right
		double scalar = left.eval(null);  // Safe: isConstant means no state needed
		return right.getLinearTermsInternal(terms, multiplier * scalar);
	    } else if (right.isConstant()) {
		// Right is constant, multiply into left
		double scalar = right.eval(null);
		return left.getLinearTermsInternal(terms, multiplier * scalar);
	    }
	    // Both sides have node refs: not linear (e.g., Cs * Is)
	    return null;
	    
	case E_DIV:
	    // Division: divisor must be constant
	    if (right.isConstant()) {
		double divisor = right.eval(null);
		if (Math.abs(divisor) < 1e-12) return null;  // Division by zero
		return left.getLinearTermsInternal(terms, multiplier / divisor);
	    }
	    // Dividing by a node ref: not linear
	    return null;
	    
	default:
	    // If it's a constant expression (trig, etc. of constants), treat as constant
	    if (isConstant()) {
		return eval(null) * multiplier;
	    }
	    // Everything else (integrate, diff, lag, t, etc.) is not linear
	    return null;
	}
    }
    
    /**
     * Evaluate this expression, resetting the lag buffer index first.
     * Use this for top-level expression evaluation to ensure each lag() call
     * gets the correct buffer. For recursive sub-expression evaluation, use eval().
     */
    double evalFresh(ExprState es) {
	es.resetLagIndex();
	return eval(es, CURRENT_CONTEXT);
    }

    double evalFresh(ExprState es, EvaluationContext context) {
	es.resetLagIndex();
	return eval(es, context);
    }

    double evalFresh(ExprState es, double dt) {
	es.resetLagIndex();
	return eval(es, Expr.getEvaluationContext(false, dt));
    }
    
    public double eval(ExprState es) {
	return eval(es, CURRENT_CONTEXT);
    }

    public double eval(ExprState es, double dt) {
	return eval(es, Expr.getEvaluationContext(false, dt));
	}

    public double eval(ExprState es, EvaluationContext context) {
	Expr left = null;
	Expr right = null;
	if (children != null && children.size() > 0) {
	    left = children.firstElement();
	    if (children.size() == 2)
		right = children.lastElement();
	}
	switch (type) {
	case E_ADD: return left.eval(es, context)+right.eval(es, context);
	case E_SUB: return left.eval(es, context)-right.eval(es, context);
	case E_MUL: return left.eval(es, context)*right.eval(es, context);
	case E_DIV: {
	    double divisor = right.eval(es, context);
	    // Protect against division by zero
	    if (Math.abs(divisor) < 1e-12)
		return 0.0;
	    return left.eval(es, context) / divisor;
	}
	case E_POW: return Math.pow(left.eval(es, context), right.eval(es, context));
	case E_OR:  return (left.eval(es, context) != 0 || right.eval(es, context) != 0) ? 1 : 0;
	case E_AND: return (left.eval(es, context) != 0 && right.eval(es, context) != 0) ? 1 : 0;
	case E_EQUALS: return (left.eval(es, context) == right.eval(es, context)) ? 1 : 0;
	case E_NEQ: return (left.eval(es, context) != right.eval(es, context)) ? 1 : 0;
	case E_LEQ: return (left.eval(es, context) <= right.eval(es, context)) ? 1 : 0;
	case E_GEQ: return (left.eval(es, context) >= right.eval(es, context)) ? 1 : 0;
	case E_LESS: return (left.eval(es, context) < right.eval(es, context)) ? 1 : 0;
	case E_GREATER: return (left.eval(es, context) > right.eval(es, context)) ? 1 : 0;
	case E_TERNARY: return children.get(left.eval(es, context) != 0 ? 1 : 2).eval(es, context);
	case E_UMINUS: return -left.eval(es, context);
	case E_NOT: return left.eval(es, context) == 0 ? 1 : 0;
	case E_VAL: return value;
	case E_T: return es.t;
	case E_SIN: return Math.sin(left.eval(es, context));
	case E_COS: return Math.cos(left.eval(es, context));
	case E_ABS: return Math.abs(left.eval(es, context));
	case E_EXP: return Math.exp(left.eval(es, context));
	case E_LOG: return Math.log(left.eval(es, context));
	case E_SQRT: return Math.sqrt(left.eval(es, context));
	case E_TAN: return Math.tan(left.eval(es, context));
	case E_ASIN: return Math.asin(left.eval(es, context));
	case E_ACOS: return Math.acos(left.eval(es, context));
	case E_ATAN: return Math.atan(left.eval(es, context));
	case E_SINH: return Math.sinh(left.eval(es, context));
	case E_COSH: return Math.cosh(left.eval(es, context));
	case E_TANH: return Math.tanh(left.eval(es, context));
	case E_FLOOR: return Math.floor(left.eval(es, context));
	case E_CEIL: return Math.ceil(left.eval(es, context));
	case E_MIN: {
	    int i;
	    double x = left.eval(es, context);
	    for (i = 1; i < children.size(); i++)
		x = Math.min(x,  children.get(i).eval(es, context));
	    return x;
	}
	case E_MAX: {
	    int i;
	    double x = left.eval(es, context);
	    for (i = 1; i < children.size(); i++)
		x = Math.max(x,  children.get(i).eval(es, context));
	    return x;
	}
	case E_CLAMP:
	    return Math.min(Math.max(left.eval(es, context), children.get(1).eval(es, context)), children.get(2).eval(es, context));
	case E_STEP: {
	    double x = left.eval(es, context); 
	    if (right == null)
		return (x < 0) ? 0 : 1;
	    return (x > right.eval(es, context)) ? 0 : (x < 0) ? 0 : 1;
	}
	case E_SELECT: {
	    double x = left.eval(es, context);
	    return children.get(x > 0 ? 2 : 1).eval(es, context);
	}
	case E_TRIANGLE: {
	    double x = posmod(left.eval(es, context), Math.PI*2)/Math.PI;
	    return (x < 1) ? -1+x*2 : 3-x*2;
	}
	case E_SAWTOOTH: {
	    double x = posmod(left.eval(es, context), Math.PI*2)/Math.PI;
	    return x-1;
	}
	case E_MOD: {
	    double divisor = right.eval(es, context);
	    // Protect against modulo by zero
	    if (Math.abs(divisor) < 1e-12)
		return 0.0;
	    return left.eval(es, context) % divisor;
	}
	case E_PWL:
	    return pwl(es, children, context);
	case E_PWLX:
	    return pwlx(es, children, context);
	case E_LOOKUP: {
	    double x = left.eval(es, context);
	    boolean clamp = (CirSim.getInstance() == null) ? true : CirSim.getInstance().isSfcrLookupClampDefault();
	    if (children != null && children.size() >= 2) {
		double clampArg = children.get(1).eval(es, context);
		clamp = (clampArg != 0.0);
	    }
	    return LookupTableRegistry.evaluate(nodeName, x, clamp);
	}
	case E_PWR:
	    return Math.pow(Math.abs(left.eval(es, context)), right.eval(es, context));
	case E_PWRS: {
	    double x = left.eval(es, context);
	    if (x < 0)
		return -Math.pow(-x, right.eval(es, context)); 
	    return Math.pow(x, right.eval(es, context)); 
	}
	case E_LASTOUTPUT:
	    return es.lastOutput;
	case E_TIMESTEP:
	    return resolveTimeStep(context);
	case E_INTEGRATE: {
	    // integrate(x) - integrates the expression over time
	    // Store the current input value - it will be committed at stepFinished()
	    // This ensures we use the converged input value, not the first subiteration's value
		// Mathematically:
		// USEs The forward Euler method is a first-order numerical quadrature / integration scheme for ODEs
		// K(t + Δt) ≈ K(t) + Δt × [I(t) − AF(t)]
	    double inputVal = left.eval(es, context);
	    es.pendingIntInput = inputVal;
	    double dt = resolveTimeStep(context);
	    
	    // Return what the integral WILL be after this timestep commits
	    // This allows the circuit to converge to the correct value
	    double result = es.lastIntOutput + dt * inputVal;
	    return result;
	}
	case E_DIFF: {
	    // diff(x) - differentiate the expression
	    // Returns (current - last) / timeStep
	    // 
	    // DESIGN: diff() computes the rate of change between the CONVERGED values
	    // at consecutive timesteps. During subiterations, the input may still be
	    // converging, but we return (input - lastDiffInput) / dt using whatever
	    // the current input value is.
	    //
	    // The key insight: we always store pendingDiffInput with the latest input,
	    // and at stepFinished(), that converged value becomes lastDiffInput for
	    // the next timestep. So the NEXT timestep's diff will be correct.
	    //
	    // For the CURRENT timestep, diff may vary across subiterations until
	    // the input converges. This is normal nonlinear behavior - the final
	    // converged diff value is what matters for the simulation result.
	    
	    double input = left.eval(es, context);
	    es.pendingDiffInput = input;  // Store for commit at stepFinished
	    
	    // Return 0 until we have a valid previous value to compare against
	    if (!es.diffInitialized) {
		return 0;
	    }
	    double dt = resolveTimeStep(context);
	    if (Math.abs(dt) < 1e-12) {
		return 0;
	    }
	    return (input - es.lastDiffInput) / dt;
	}
	case E_LAST:
	    // last(x) - return the PREVIOUS timestep's converged value
	    // This is used for sfcr-style V[-1] notation
	    // IMPORTANT: last() must NOT fall back to current subiteration values,
	    // otherwise Hs = last(Hs) + 1 would increment on every subiteration!
	    if (left != null && (left.type == E_NODE_REF || left.type == E_GSLOT) && left.nodeName != null) {
		String varName = left.nodeName;
		// Get ONLY the converged value from previous timestep (no fallback!)
		Double laggedValue = ComputedValues.getLaggedValue(varName);
		if (laggedValue != null) {
		    return laggedValue.doubleValue();
		}
		// FLOW rows publish under a dedicated *.flow namespace. If the
		// base name has no lagged value, fall back to its lagged flow key.
		String flowKey = ComputedValues.getFlowComputedKeyForName(varName);
		if (flowKey != null) {
		    Double laggedFlowValue = ComputedValues.getLaggedValue(flowKey);
		    if (laggedFlowValue != null) {
			return laggedFlowValue.doubleValue();
		    }
		}
		// No converged value yet (first timestep) - try X_init as fallback
		// This matches sfcr behavior where initial values are used for V[-1] in period 1
		Double initValue = ComputedValues.getComputedValue(varName + "_init");
		if (initValue != null) {
		    return initValue.doubleValue();
		}
		// Also try without underscore (e.g., Vinit for V)
		initValue = ComputedValues.getComputedValue(varName + "init");
		if (initValue != null) {
		    return initValue.doubleValue();
		}
		// No initial value found - return 0
	    }
	    return 0.0;
	case E_LAG: {
	    // lag(x, delay) - return the value of x from 'delay' time units ago
	    // Uses a circular buffer to store historical values
	    // Example: lag(Y, 1) returns Y from 1 year ago (if timeUnit is yr)
	    double inputVal = left.eval(es, context);
	    double delay = right.eval(es, context);
	    
	    // Use the fixed buffer index assigned at parse time
	    // This ensures the same lag() call uses the same buffer across subiterations
	    int bufIdx = lagIndex;
	    if (bufIdx < 0 || bufIdx >= ExprState.MAX_LAG_BUFFERS) {
		bufIdx = 0;  // Fallback if not properly assigned
	    }
	    
	    // Store pending value for commit
	    es.lagPendingValue[bufIdx] = inputVal;
	    if (es.lagLastCommitTime[bufIdx] < 0) {
		es.lagLastCommitTime[bufIdx] = 0;  // Mark as initialized (use 0 so commits start immediately)
	    }
	    
	    // Check if we have enough history
	    double targetTime = es.t - delay;
	    
	    // Get initial value for this variable (if available)
	    double initValue = 0.0;
	    if (left.type == E_NODE_REF && left.nodeName != null) {
		String varName = left.nodeName;
		// Try X_init first
		Double iv = ComputedValues.getComputedValue(varName + "_init");
		if (iv != null) {
		    initValue = iv.doubleValue();
		} else {
		    // Also try without underscore (e.g., Vinit for V)
		    iv = ComputedValues.getComputedValue(varName + "init");
		    if (iv != null) {
			initValue = iv.doubleValue();
		    }
		}
	    }
	    
	    // MOVING AVERAGE FILTER: Always use a moving average over one period.
	    // This smooths out discontinuities by averaging all samples within
	    // the window [t-delay, t]. For timestep=0.01 and delay=1, averages ~100 points.
	    // During the first period (t < delay), initValue is used to pad the window.
	    if ((left.type == E_NODE_REF || left.type == E_GSLOT) && left.nodeName != null) {
		String varName = left.nodeName;
		// Try X_init first
		Double iv = ComputedValues.getComputedValue(varName + "_init");
		if (iv != null) {
		    initValue = iv.doubleValue();
		} else {
		    // Also try without underscore (e.g., Vinit for V)
		    iv = ComputedValues.getComputedValue(varName + "init");
		    if (iv != null) {
			initValue = iv.doubleValue();
		    }
		}
	    }
	    double result = es.getLaggedMovingAverage(bufIdx, delay, initValue);

	    // Debug logging for Y2
	    // Log all lag() calls to diagnose - left.type=" + left.type + " nodeName=" + left.nodeName
	    // if (left.nodeName != null && left.nodeName.contains("YD")) {
		// CirSim.console("lag(" + left.nodeName + "," + delay + ") t=" + es.t + 
		//     " type=" + left.type + " init=" + initValue + 
		//     " input=" + inputVal + " mavg=" + result);
	    // }
	    
	    return result;
	}
	case E_SMOOTH: {
	    // smooth(x, theta) - first-order implicit Euler smoother:
	    // y[n] = (y[n-1] + theta*dt*x[n]) / (1 + theta*dt)
	    double inputVal = left.eval(es, context);
	    double theta = right.eval(es, context);
	    int idx = smoothIndex;
	    if (idx < 0 || idx >= ExprState.MAX_SMOOTH_STATES) {
		idx = 0;
	    }
	    if (!es.smoothInitialized[idx]) {
		es.smoothInitialized[idx] = true;
		es.smoothLastOutput[idx] = es.lastOutput;
		es.smoothPendingOutput[idx] = es.lastOutput;
		es.smoothLastCommitTime[idx] = -1;
	    }
	    double dt = CirSim.getInstance().getTimeStep();
	    double denom = 1 + theta * dt;
	    if (Math.abs(denom) < 1e-12) {
		es.smoothPendingOutput[idx] = es.smoothLastOutput[idx];
		return 0.0;
	    }
	    double result = (es.smoothLastOutput[idx] + theta * dt * inputVal) / denom;
	    es.smoothPendingOutput[idx] = result;
	    return result;
	}
	case E_DELAY: {
	    // delay(x, tau=1) - first-order delay/lag:
	    // y' = (x - y) / tau
	    // Implicit Euler form:
	    // y[n] = (tau*y[n-1] + dt*x[n]) / (tau + dt)
	    // Equivalent behavior to: y ~ integrate((x - y)/tau)
	    double inputVal = left.eval(es, context);
	    double tau = (right != null) ? right.eval(es, context) : 1.0;
	    int idx = smoothIndex;
	    if (idx < 0 || idx >= ExprState.MAX_SMOOTH_STATES) {
		idx = 0;
	    }
	    if (!es.smoothInitialized[idx]) {
		es.smoothInitialized[idx] = true;
		es.smoothLastOutput[idx] = es.lastOutput;
		es.smoothPendingOutput[idx] = es.lastOutput;
		es.smoothLastCommitTime[idx] = -1;
	    }
	    double dt = CirSim.getInstance().getTimeStep();
	    if (Math.abs(tau) < 1e-12) {
		es.smoothPendingOutput[idx] = inputVal;
		return inputVal;
	    }
	    double denom = tau + dt;
	    if (Math.abs(denom) < 1e-12) {
		es.smoothPendingOutput[idx] = es.smoothLastOutput[idx];
		return es.smoothLastOutput[idx];
	    }
	    double result = (tau * es.smoothLastOutput[idx] + dt * inputVal) / denom;
	    es.smoothPendingOutput[idx] = result;
	    return result;
	}
	case E_GSLOT: {
	    // Fast-path: direct index into circuit-global array (resolved at analysis time from E_NODE_REF).
	    // value holds the slot index. nodeName is preserved for re-resolution after re-analyze.
	    // Perf probe: count only — per-call timing of a ~1 ns array read is dominated by
	    // JS timestamp overhead (~500 ns/call) and produces meaningless avgNs numbers.
	    CirSim gslotSim = CirSim.getInstance();
	    if (gslotSim != null && gslotSim.circuitVariables != null) {
		int gslot = (int) value;
		if (gslot >= 0 && gslot < gslotSim.circuitVariables.length) {
		    if (perfProbeEnabled) perfGlobalSlotEvalCount++;
		    return gslotSim.circuitVariables[gslot];
		}
	    }
	    return 0.0;
	}
	case E_NODE_REF:
	    long nodeRefStartNanos = perfProbeEnabled ? getPerfNowNanos() : 0;
	    if (perfProbeEnabled && nodeName != null && perfNodeRefNameSamples.size() < PERF_SAMPLE_LIMIT
		    && !perfNodeRefNameSamples.contains(nodeName))
		perfNodeRefNameSamples.add(nodeName);
	    // Direct node reference - get voltage from labeled node or computed value.
	    //
	    // Resolution order (MNA mode):
	    // 1) PARAM name exact match from ComputedValues (parameter override)
	    // 2) NAME.flow from ComputedValues (flow-first behavior)
	    // 3) Labeled-node voltage from matrix solution (physical node value)
	    // 4) NAME exact match from ComputedValues (non-physical fallback)
	    //
	    // In pure-computational mode (no MNA): NAME.flow first, then NAME.
	    if (CirSim.getInstance() != null && nodeName != null) {
		if (CirSim.getInstance().isEquationTableMnaMode()) {
			    // PARAM names in MNA mode must resolve from ComputedValues first,
			    // even when a same-named labeled node exists.
			    if (ComputedValues.isParameterName(nodeName)) {
				Double parameterValue = getComputedByMode(nodeName, context);
				if (parameterValue != null) {
				    return returnNodeRefValue(parameterValue.doubleValue(), nodeRefStartNanos);
				}
			    }

		    // If a flow value exists for this name, prefer it.
			    Double flowPreferredValue = getComputedFlowByMode(nodeName, context);
		    if (flowPreferredValue != null) {
			return returnNodeRefValue(flowPreferredValue.doubleValue(), nodeRefStartNanos);
		    }

		    // MNA mode: labeled node voltage first (authoritative from matrix solver)
		    Integer labeledNode = LabeledNodeElm.getByName(nodeName);
		    if (labeledNode != null && labeledNode != 0) {
			int nodeIndex = labeledNode.intValue() - 1;
			if (CirSim.getInstance().getSolverMatrixState().nodeVoltages != null
				&& nodeIndex >= 0
				&& nodeIndex < CirSim.getInstance().getSolverMatrixState().nodeVoltages.length) {
			    return returnNodeRefValue(CirSim.getInstance().getSolverMatrixState().nodeVoltages[nodeIndex], nodeRefStartNanos);
			}
		    }
		    // Fall back to ComputedValues for non-physical variables
		    Double computedValue = getComputedByMode(nodeName, context);
		    if (computedValue != null) {
			return returnNodeRefValue(computedValue.doubleValue(), nodeRefStartNanos);
		    }
		} else {
		    // Pure-computational mode: ComputedValues is the only source
		    Double computedValue = getComputedFlowOrValueByMode(nodeName, context);
		    if (computedValue != null) {
			return returnNodeRefValue(computedValue.doubleValue(), nodeRefStartNanos);
		    }
		}
		// Not found - track as unresolved (only add once)
		if (!unresolvedReferences.contains(nodeName)) {
		    unresolvedReferences.add(nodeName);
		}
	    }
	    return returnNodeRefValue(0.0, nodeRefStartNanos);
	default:
	    if (type >= E_LASTA) {
		if (!perfProbeEnabled)
		    return es.lastValues[type-E_LASTA];
		long slotStartNanos = getPerfNowNanos();
		double slotValue = es.lastValues[type-E_LASTA];
		recordLocalSlotTiming(slotStartNanos);
		return slotValue;
	    }
	    if (type >= E_DADT) {
		if (!perfProbeEnabled)
		    return (es.values[type-E_DADT]-es.lastValues[type-E_DADT])/CirSim.getInstance().getTimeStep();
		long slotStartNanos = getPerfNowNanos();
		double slotValue = (es.values[type-E_DADT]-es.lastValues[type-E_DADT])/CirSim.getInstance().getTimeStep();
		recordLocalSlotTiming(slotStartNanos);
		return slotValue;
	    }
	    if (type >= E_A) {
		if (!perfProbeEnabled)
		    return es.values[type-E_A];
		long slotStartNanos = getPerfNowNanos();
		double slotValue = es.values[type-E_A];
		recordLocalSlotTiming(slotStartNanos);
		return slotValue;
	    }
	    CirSim.console("unknown\n");
	}
	return 0;
    }
    
    double pwl(ExprState es, Vector<Expr> args) {
	return pwl(es, args, CURRENT_CONTEXT);
    }

    double pwl(ExprState es, Vector<Expr> args, EvaluationContext context) {
	double x = args.get(0).eval(es, context);
	double x0 = args.get(1).eval(es, context);
	double y0 = args.get(2).eval(es, context);
	if (x < x0)
	    return y0;
	double x1 = args.get(3).eval(es, context);
	double y1 = args.get(4).eval(es, context);
	int i = 5;
	while (true) {
	    if (x < x1)
		return y0+(x-x0)*(y1-y0)/(x1-x0);
	    if (i+1 >= args.size())
		break;
	    x0 = x1;
	    y0 = y1;
	    x1 = args.get(i  ).eval(es, context);
	    y1 = args.get(i+1).eval(es, context);
	    i += 2;
	}
	return y1;
    }

    double pwlx(ExprState es, Vector<Expr> args, EvaluationContext context) {
	double x = args.get(0).eval(es, context);
	double x0 = args.get(1).eval(es, context);
	double y0 = args.get(2).eval(es, context);
	double x1 = args.get(3).eval(es, context);
	double y1 = args.get(4).eval(es, context);

	if (x < x0) {
	    double dx = x1 - x0;
	    if (Math.abs(dx) < 1e-12)
		return y0;
	    return y0 + (x - x0) * (y1 - y0) / dx;
	}

	int i = 5;
	while (true) {
	    if (x < x1) {
		double dx = x1 - x0;
		if (Math.abs(dx) < 1e-12)
		    return y0;
		return y0 + (x - x0) * (y1 - y0) / dx;
	    }
	    if (i + 1 >= args.size())
		break;
	    x0 = x1;
	    y0 = y1;
	    x1 = args.get(i).eval(es, context);
	    y1 = args.get(i + 1).eval(es, context);
	    i += 2;
	}

	double dx = x1 - x0;
	if (Math.abs(dx) < 1e-12)
	    return y1;
	return y0 + (x - x0) * (y1 - y0) / dx;
    }

    double posmod(double x, double y) {
	x %= y;
	return (x >= 0) ? x : x+y;
    }
    
    Vector<Expr> children;
    double value;
    String nodeName; // For E_NODE_REF expressions
    public int type;
    int lagIndex = -1; // Buffer index for E_LAG expressions, assigned at parse time
	int smoothIndex = -1; // State index for E_SMOOTH expressions, assigned at parse time
    static final int E_ADD = 1;
    static final int E_SUB = 2;
    static final int E_T = 3;
    static final int E_VAL = 6;
    static final int E_MUL = 7;
    static final int E_DIV = 8;
    static final int E_POW = 9;
    static final int E_UMINUS = 10;
    static final int E_SIN = 11;
    static final int E_COS = 12;
    static final int E_ABS = 13;
    static final int E_EXP = 14;
    static final int E_LOG = 15;
    static final int E_SQRT = 16;
    static final int E_TAN = 17;
    static final int E_R = 18;
    static final int E_MAX = 19;
    static final int E_MIN = 20;
    static final int E_CLAMP = 21;
    static final int E_PWL = 22;
    static final int E_TRIANGLE = 23;
    static final int E_SAWTOOTH = 24;
    static final int E_MOD = 25;
    static final int E_STEP = 26;
    static final int E_SELECT = 27;
	static final int E_PWLX = 94;
    static final int E_PWR = 28;
    static final int E_PWRS = 29;
    static final int E_LASTOUTPUT = 30;
    static final int E_TIMESTEP = 31;
    public static final int E_INTEGRATE = 32;
    static final int E_TERNARY = 33;
    static final int E_OR = 34;
    static final int E_AND = 35;
    static final int E_EQUALS = 36;
    static final int E_LEQ = 37;
    static final int E_GEQ = 38;
    static final int E_LESS = 39;
    static final int E_GREATER = 40;
    static final int E_NEQ = 41;
    static final int E_NOT = 42;
    static final int E_FLOOR = 43;
    static final int E_CEIL = 44;
    static final int E_ASIN = 45;
    static final int E_ACOS = 46;
    static final int E_ATAN = 47;
    static final int E_SINH = 48;
    static final int E_COSH = 49;
    static final int E_TANH = 50;
    static final int E_A = 51;
    static final int E_DADT = E_A+10; // must be E_A+10
    static final int E_LASTA = E_DADT+10; // should be at end and equal to E_DADT+10
    static final int E_NODE_REF = E_LASTA+10; // Direct node reference by name
    static final int E_DIFF = E_NODE_REF+1; // Differentiation function diff(x)
    static final int E_LAST = E_DIFF+1; // last(x) - returns previous timestep's converged value
    static final int E_LAG = E_LAST+1;  // lag(x, delay) - returns value from 'delay' time units ago
	static final int E_SMOOTH = E_LAG+1; // smooth(x, theta) - implicit Euler smoothing
	static final int E_DELAY = E_SMOOTH+1; // delay(x, tau=1) - first-order lag y'=(x-y)/tau
	static final int E_LOOKUP = E_DELAY+1; // lookup(tableName, x)
	static final int E_GSLOT = E_LOOKUP+10; // Circuit-global array slot (fast-path replacement for E_NODE_REF)
};
