package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class ExprState {
    //int n;
    double values[];
    double lastValues[];
    double lastOutput;
    double lastDiffInput;  // For diff() function - committed value from last timestep
    double lastIntOutput;  // For integrate() function - committed value from last timestep
    double lastIntTime;    // Last time integrate() was committed
    double pendingIntInput; // Current input value for integrate (updated each subiteration)
    double pendingDiffInput; // Current input value for diff (to be committed at stepFinished)
    boolean diffInitialized; // True after first commit, so diff has valid lastDiffInput
    double t;
    
    // For lag() function - circular buffer of historical values
    // Each lag() call in an expression gets its own buffer, indexed by lagIndex
    static final int LAG_BUFFER_SIZE = 10000;  // Max history entries per lag
    static final int MAX_LAG_BUFFERS = 10;     // Max number of lag() calls per expression
    double[][] lagBufferValues;    // [lagIndex][bufferPos] = value
    double[][] lagBufferTimes;     // [lagIndex][bufferPos] = time
    int[] lagBufferHead;           // Write position for each buffer
    int[] lagBufferCount;          // Number of valid entries in each buffer
    double[] lagPendingValue;      // Current value to commit at stepFinished
    double[] lagLastCommitTime;    // Last time we committed to each buffer
    int lagBufferIndex;            // Current lag buffer being used during eval
    
    ExprState(int xx) {
	//n = xx;
	values = new double[9];
	lastValues = new double[9];
	values[4] = Math.E;
	lastDiffInput = 0;
	lastIntOutput = 0;
	lastIntTime = -1;
	pendingIntInput = 0;
	pendingDiffInput = 0;
	diffInitialized = false;
	
	// Initialize lag buffers
	lagBufferValues = new double[MAX_LAG_BUFFERS][LAG_BUFFER_SIZE];
	lagBufferTimes = new double[MAX_LAG_BUFFERS][LAG_BUFFER_SIZE];
	lagBufferHead = new int[MAX_LAG_BUFFERS];
	lagBufferCount = new int[MAX_LAG_BUFFERS];
	lagPendingValue = new double[MAX_LAG_BUFFERS];
	lagLastCommitTime = new double[MAX_LAG_BUFFERS];
	for (int i = 0; i < MAX_LAG_BUFFERS; i++) {
	    lagLastCommitTime[i] = -1;
	}
	lagBufferIndex = 0;
    }
    
    void updateLastValues(double lastOut) {
	lastOutput = lastOut;
	int i;
	for (i = 0; i != values.length; i++)
	    lastValues[i] = values[i];
    }
    
    void reset() {
	for (int i = 0; i != values.length; i++)
	    lastValues[i] = 0;
	lastOutput = 0;
	lastDiffInput = 0;
	lastIntOutput = 0;
	lastIntTime = -1;
	pendingIntInput = 0;
	pendingDiffInput = 0;
	diffInitialized = false;
	
	// Reset lag buffers
	for (int i = 0; i < MAX_LAG_BUFFERS; i++) {
	    lagBufferHead[i] = 0;
	    lagBufferCount[i] = 0;
	    lagPendingValue[i] = 0;
	    lagLastCommitTime[i] = -1;
	}
	lagBufferIndex = 0;
    }
    
    // Call this at the end of each timestep to commit the integration and differentiation
    void commitIntegration(double timeStep) {
	if (t != lastIntTime) {
	    lastIntOutput = lastIntOutput + timeStep * pendingIntInput;
	    lastIntTime = t;
	}
	lastDiffInput = pendingDiffInput;
	diffInitialized = true;
	
	// Commit lag buffer values
	for (int i = 0; i < MAX_LAG_BUFFERS; i++) {
	    if (t != lagLastCommitTime[i] && lagLastCommitTime[i] >= 0) {
		// Add new entry to circular buffer
		lagBufferValues[i][lagBufferHead[i]] = lagPendingValue[i];
		lagBufferTimes[i][lagBufferHead[i]] = t;
		lagBufferHead[i] = (lagBufferHead[i] + 1) % LAG_BUFFER_SIZE;
		if (lagBufferCount[i] < LAG_BUFFER_SIZE) {
		    lagBufferCount[i]++;
		}
		lagLastCommitTime[i] = t;
	    }
	}
    }
    
    // Reset the lag buffer index at the start of each evaluation
    void resetLagIndex() {
	lagBufferIndex = 0;
    }
    
    // Get the value from the lag buffer at time (currentTime - delay)
    // Returns the input value if not enough history exists
    double getLaggedValue(int bufferIdx, double delay, double currentValue) {
	if (bufferIdx >= MAX_LAG_BUFFERS || lagBufferCount[bufferIdx] == 0) {
	    return currentValue;  // No history yet
	}
	
	double targetTime = t - delay;
	if (targetTime < 0) {
	    return currentValue;  // Before simulation start
	}
	
	// Search backwards through the circular buffer to find the value at targetTime
	int count = lagBufferCount[bufferIdx];
	int head = lagBufferHead[bufferIdx];
	
	// Start from most recent and go backwards
	double prevTime = -1;
	double prevValue = currentValue;
	
	for (int i = 0; i < count; i++) {
	    int idx = (head - 1 - i + LAG_BUFFER_SIZE) % LAG_BUFFER_SIZE;
	    double bufTime = lagBufferTimes[bufferIdx][idx];
	    double bufValue = lagBufferValues[bufferIdx][idx];
	    
	    if (bufTime <= targetTime) {
		// Found it - interpolate if we have a next point
		if (prevTime >= 0 && prevTime > bufTime) {
		    // Linear interpolation between bufTime and prevTime
		    double alpha = (targetTime - bufTime) / (prevTime - bufTime);
		    return bufValue + alpha * (prevValue - bufValue);
		}
		return bufValue;
	    }
	    prevTime = bufTime;
	    prevValue = bufValue;
	}
	
	// targetTime is before our oldest record - return oldest value
	int oldestIdx = (head - count + LAG_BUFFER_SIZE) % LAG_BUFFER_SIZE;
	return lagBufferValues[bufferIdx][oldestIdx];
    }
}

class Expr {
    // Global tracking of unresolved node references during evaluation
    static Vector<String> unresolvedReferences = new Vector<String>();
    
    // Counter for assigning unique lag buffer indices at parse time
    static int nextLagIndex = 0;
    
    /** Reset the lag index counter (call when starting a new expression parse) */
    static void resetLagIndexCounter() {
	nextLagIndex = 0;
    }
    
    /**
     * When true, E_NODE_REF lookups use getConvergedValue() instead of getComputedValue().
     * This should be set to true for display-only evaluations (SFC tables, etc.)
     * to get stable values that don't vary during subiterations.
     */
    static boolean useConvergedValues = false;
    
    /** Clear the unresolved references list (call at start of each timestep) */
    static void clearUnresolvedReferences() {
	unresolvedReferences.clear();
    }
    
    /** Get all unresolved references found during evaluation */
    static Vector<String> getUnresolvedReferences() {
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
     * Evaluate this expression, resetting the lag buffer index first.
     * Use this for top-level expression evaluation to ensure each lag() call
     * gets the correct buffer. For recursive sub-expression evaluation, use eval().
     */
    double evalFresh(ExprState es) {
	es.resetLagIndex();
	return eval(es);
    }
    
    double eval(ExprState es) {
	Expr left = null;
	Expr right = null;
	if (children != null && children.size() > 0) {
	    left = children.firstElement();
	    if (children.size() == 2)
		right = children.lastElement();
	}
	switch (type) {
	case E_ADD: return left.eval(es)+right.eval(es);
	case E_SUB: return left.eval(es)-right.eval(es);
	case E_MUL: return left.eval(es)*right.eval(es);
	case E_DIV: {
	    double divisor = right.eval(es);
	    // Protect against division by zero
	    if (Math.abs(divisor) < 1e-12)
		return 0.0;
	    return left.eval(es) / divisor;
	}
	case E_POW: return Math.pow(left.eval(es), right.eval(es));
	case E_OR:  return (left.eval(es) != 0 || right.eval(es) != 0) ? 1 : 0;
	case E_AND: return (left.eval(es) != 0 && right.eval(es) != 0) ? 1 : 0;
	case E_EQUALS: return (left.eval(es) == right.eval(es)) ? 1 : 0;
	case E_NEQ: return (left.eval(es) != right.eval(es)) ? 1 : 0;
	case E_LEQ: return (left.eval(es) <= right.eval(es)) ? 1 : 0;
	case E_GEQ: return (left.eval(es) >= right.eval(es)) ? 1 : 0;
	case E_LESS: return (left.eval(es) < right.eval(es)) ? 1 : 0;
	case E_GREATER: return (left.eval(es) > right.eval(es)) ? 1 : 0;
	case E_TERNARY: return children.get(left.eval(es) != 0 ? 1 : 2).eval(es);
	case E_UMINUS: return -left.eval(es);
	case E_NOT: return left.eval(es) == 0 ? 1 : 0;
	case E_VAL: return value;
	case E_T: return es.t;
	case E_SIN: return Math.sin(left.eval(es));
	case E_COS: return Math.cos(left.eval(es));
	case E_ABS: return Math.abs(left.eval(es));
	case E_EXP: return Math.exp(left.eval(es));
	case E_LOG: return Math.log(left.eval(es));
	case E_SQRT: return Math.sqrt(left.eval(es));
	case E_TAN: return Math.tan(left.eval(es));
	case E_ASIN: return Math.asin(left.eval(es));
	case E_ACOS: return Math.acos(left.eval(es));
	case E_ATAN: return Math.atan(left.eval(es));
	case E_SINH: return Math.sinh(left.eval(es));
	case E_COSH: return Math.cosh(left.eval(es));
	case E_TANH: return Math.tanh(left.eval(es));
	case E_FLOOR: return Math.floor(left.eval(es));
	case E_CEIL: return Math.ceil(left.eval(es));
	case E_MIN: {
	    int i;
	    double x = left.eval(es);
	    for (i = 1; i < children.size(); i++)
		x = Math.min(x,  children.get(i).eval(es));
	    return x;
	}
	case E_MAX: {
	    int i;
	    double x = left.eval(es);
	    for (i = 1; i < children.size(); i++)
		x = Math.max(x,  children.get(i).eval(es));
	    return x;
	}
	case E_CLAMP:
	    return Math.min(Math.max(left.eval(es), children.get(1).eval(es)), children.get(2).eval(es));
	case E_STEP: {
	    double x = left.eval(es); 
	    if (right == null)
		return (x < 0) ? 0 : 1;
	    return (x > right.eval(es)) ? 0 : (x < 0) ? 0 : 1;
	}
	case E_SELECT: {
	    double x = left.eval(es);
	    return children.get(x > 0 ? 2 : 1).eval(es);
	}
	case E_TRIANGLE: {
	    double x = posmod(left.eval(es), Math.PI*2)/Math.PI;
	    return (x < 1) ? -1+x*2 : 3-x*2;
	}
	case E_SAWTOOTH: {
	    double x = posmod(left.eval(es), Math.PI*2)/Math.PI;
	    return x-1;
	}
	case E_MOD: {
	    double divisor = right.eval(es);
	    // Protect against modulo by zero
	    if (Math.abs(divisor) < 1e-12)
		return 0.0;
	    return left.eval(es) % divisor;
	}
	case E_PWL:
	    return pwl(es, children);
	case E_PWR:
	    return Math.pow(Math.abs(left.eval(es)), right.eval(es));
	case E_PWRS: {
	    double x = left.eval(es);
	    if (x < 0)
		return -Math.pow(-x, right.eval(es)); 
	    return Math.pow(x, right.eval(es)); 
	}
	case E_LASTOUTPUT:
	    return es.lastOutput;
	case E_TIMESTEP:
	    return CirSim.theSim.timeStep;
	case E_INTEGRATE: {
	    // integrate(x) - integrates the expression over time
	    // Store the current input value - it will be committed at stepFinished()
	    // This ensures we use the converged input value, not the first subiteration's value
		// Mathematically:
		// USEs The forward Euler method is a first-order numerical quadrature / integration scheme for ODEs
		// K(t + Δt) ≈ K(t) + Δt × [I(t) − AF(t)]
	    double inputVal = left.eval(es);
	    es.pendingIntInput = inputVal;
	    
	    // Return what the integral WILL be after this timestep commits
	    // This allows the circuit to converge to the correct value
	    double result = es.lastIntOutput + CirSim.theSim.timeStep * inputVal;
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
	    
	    double input = left.eval(es);
	    es.pendingDiffInput = input;  // Store for commit at stepFinished
	    
	    // Return 0 until we have a valid previous value to compare against
	    if (!es.diffInitialized) {
		return 0;
	    }
	    
	    return (input - es.lastDiffInput) / CirSim.theSim.timeStep;
	}
	case E_LAST:
	    // last(x) - return the PREVIOUS timestep's converged value
	    // This is used for sfcr-style V[-1] notation
	    // IMPORTANT: last() must NOT fall back to current subiteration values,
	    // otherwise Hs = last(Hs) + 1 would increment on every subiteration!
	    if (left != null && left.type == E_NODE_REF && left.nodeName != null) {
		String varName = left.nodeName;
		// Get ONLY the converged value from previous timestep (no fallback!)
		Double laggedValue = ComputedValues.getLaggedValue(varName);
		if (laggedValue != null) {
		    return laggedValue.doubleValue();
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
	    double inputVal = left.eval(es);
	    double delay = right.eval(es);
	    
	    // Use the fixed buffer index assigned at parse time
	    // This ensures the same lag() call uses the same buffer across subiterations
	    int bufIdx = lagIndex;
	    if (bufIdx < 0 || bufIdx >= ExprState.MAX_LAG_BUFFERS) {
		bufIdx = 0;  // Fallback if not properly assigned
	    }
	    
	    // Store pending value for commit
	    es.lagPendingValue[bufIdx] = inputVal;
	    if (es.lagLastCommitTime[bufIdx] < 0) {
		es.lagLastCommitTime[bufIdx] = es.t - 1;  // Mark as initialized
	    }
	    
	    // Check if we have enough history
	    double targetTime = es.t - delay;
	    boolean needsInitialValue = (es.lagBufferCount[bufIdx] == 0) || (targetTime < 0);
	    
	    if (needsInitialValue) {
		// Try to find an initial value if the argument is a node reference
		// This matches sfcr behavior where V[-1] in period 1 uses V_init
		if (left.type == E_NODE_REF && left.nodeName != null) {
		    String varName = left.nodeName;
		    // Try X_init first
		    Double initValue = ComputedValues.getComputedValue(varName + "_init");
		    if (initValue != null) {
			return initValue.doubleValue();
		    }
		    // Also try without underscore (e.g., Vinit for V)
		    initValue = ComputedValues.getComputedValue(varName + "init");
		    if (initValue != null) {
			return initValue.doubleValue();
		    }
		}
		// No initial value found - return 0 (sfcr default)
		return 0.0;
	    }
	    
	    // Get the lagged value from the buffer
	    return es.getLaggedValue(bufIdx, delay, inputVal);
	}
	case E_NODE_REF:
	    // Direct node reference - get voltage from labeled node or computed value
	    if (CirSim.theSim != null && nodeName != null) {
		// First check for computed values (from TableElm)
		// Use converged values if flag is set (for stable display)
		Double computedValue = useConvergedValues 
		    ? ComputedValues.getConvergedValue(nodeName)
		    : ComputedValues.getComputedValue(nodeName);
		if (computedValue != null) {
		    return computedValue.doubleValue();
		}
		// Then check for actual labeled node voltage
		Integer labeledNode = LabeledNodeElm.getByName(nodeName);
		if (labeledNode != null && labeledNode != 0) {
		    return CirSim.theSim.getLabeledNodeVoltage(nodeName);
		}
		// Not found - track as unresolved (only add once)
		if (!unresolvedReferences.contains(nodeName)) {
		    unresolvedReferences.add(nodeName);
		}
	    }
	    return 0.0;
	default:
	    if (type >= E_LASTA)
		return es.lastValues[type-E_LASTA];
	    if (type >= E_DADT)
		return (es.values[type-E_DADT]-es.lastValues[type-E_DADT])/CirSim.theSim.timeStep;
	    if (type >= E_A)
		return es.values[type-E_A];
	    CirSim.console("unknown\n");
	}
	return 0;
    }
    
    double pwl(ExprState es, Vector<Expr> args) {
	double x = args.get(0).eval(es);
	double x0 = args.get(1).eval(es);
	double y0 = args.get(2).eval(es);
	if (x < x0)
	    return y0;
	double x1 = args.get(3).eval(es);
	double y1 = args.get(4).eval(es);
	int i = 5;
	while (true) {
	    if (x < x1)
		return y0+(x-x0)*(y1-y0)/(x1-x0);
	    if (i+1 >= args.size())
		break;
	    x0 = x1;
	    y0 = y1;
	    x1 = args.get(i  ).eval(es);
	    y1 = args.get(i+1).eval(es);
	    i += 2;
	}
	return y1;
    }

    double posmod(double x, double y) {
	x %= y;
	return (x >= 0) ? x : x+y;
    }
    
    Vector<Expr> children;
    double value;
    String nodeName; // For E_NODE_REF expressions
    int type;
    int lagIndex = -1; // Buffer index for E_LAG expressions, assigned at parse time
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
    static final int E_PWR = 28;
    static final int E_PWRS = 29;
    static final int E_LASTOUTPUT = 30;
    static final int E_TIMESTEP = 31;
    static final int E_INTEGRATE = 32;
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
};

class ExprParser {
    String text;
    String token;
    int pos;
    int tlen;
    String err;

    void getToken() {
		while (pos < tlen && text.charAt(pos) == ' ')
			pos++;
		if (pos == tlen) {
			token = "";
			return;
		}
		int i = pos;
		int c = text.charAt(i);
		if ((c >= '0' && c <= '9') || c == '.') {
			for (i = pos; i != tlen; i++) {
			if (text.charAt(i) == 'e' || text.charAt(i) == 'E') {
				i++;
				if (i < tlen && (text.charAt(i) == '+' || text.charAt(i) == '-'))
				i++;
			}
			if (!((text.charAt(i) >= '0' && text.charAt(i) <= '9') ||
				text.charAt(i) == '.'))
				break;
			}
		} else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '\\') {
			// Support identifiers with letters, numbers, underscores, Greek symbols (\beta), 
			// and LaTeX formatting (Z_1, x^2, Z_{banks})
			// Must start with letter, underscore, or backslash
			for (i = pos; i != tlen; i++) {
				char ch = text.charAt(i);
				if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || 
					  (ch >= '0' && ch <= '9') || ch == '_' || ch == '\\' || ch == '^' || ch == '{' || ch == '}'))
					break;
			}
		} else {
			i++;
			if (i < tlen) {
			// ||, &&, <<, >>, ==
			if (text.charAt(i) == c && (c == '|' || c == '&' || c == '<' || c == '>' || c == '='))
				i++;
			// <=, >=
			else if ((c == '<' || c == '>' || c == '!') && text.charAt(i) == '=')
				i++;
			}
		}
		token = text.substring(pos, i);
		pos = i;
    }

    boolean skip(String s) {
	if (token.compareTo(s) != 0)
	    return false;
	getToken();
	return true;
    }
    
    // Case-insensitive version for function names
    boolean skipIgnoreCase(String s) {
	if (!token.equalsIgnoreCase(s))
	    return false;
	getToken();
	return true;
    }
    
    // Check if a token looks like a valid identifier (GWT-compatible)
    // Now supports Greek symbols like \beta, \omega and LaTeX scripts like Z_1, x^2
    boolean isValidIdentifier(String token) {
	if (token == null || token.length() == 0)
	    return false;
	
	// First character must be letter, underscore, or backslash (for Greek symbols)
	char first = token.charAt(0);
	if (!((first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z') || first == '_' || first == '\\'))
	    return false;
	
	// Remaining characters must be letters, numbers, underscores, backslashes, or script markers
	for (int i = 1; i < token.length(); i++) {
	    char c = token.charAt(i);
	    if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || 
		  (c >= '0' && c <= '9') || c == '_' || c == '\\' || c == '^' || c == '{' || c == '}'))
		return false;
	}
	
	return true;
    }

    void setError(String s) {
	if (err == null)
	    err = s;
    }
    
    void skipOrError(String s) {
	if (!skip(s)) {
	    setError("expected " + s + ", got " + token);
	}
    }

    Expr parseExpression() {
	if (token.length() == 0)
	    return new Expr(Expr.E_VAL, 0.);
	Expr e = parse();
	if (token.length() > 0)
	    setError("unexpected token: " + token);
	return e;
    }

    Expr parse() {
	Expr e = parseOr();
	Expr e2, e3;
	if (skip("?")) {
	    e2 = parseOr();
	    skipOrError(":");
	    e3 = parse();
	    Expr ret = new Expr(e, e2, Expr.E_TERNARY);
	    ret.children.add(e3);
	    return ret;
	}
	return e;
    }
    
    Expr parseOr() {
	Expr e = parseAnd();
	while (skip("||")) {
	    e = new Expr(e, parseAnd(), Expr.E_OR);
	}
	return e;
    }
    
    Expr parseAnd() {
	Expr e = parseEquals();
	while (skip("&&")) {
	    e = new Expr(e, parseEquals(), Expr.E_AND);
	}
	return e;
    }
    
    Expr parseEquals() {
	Expr e = parseCompare();
	if (skip("=="))
	    return new Expr(e, parseCompare(), Expr.E_EQUALS);
	return e;
    }
    
    Expr parseCompare() {
	Expr e = parseAdd();
	if (skip("<="))
	    return new Expr(e, parseAdd(), Expr.E_LEQ);
	if (skip(">="))
	    return new Expr(e, parseAdd(), Expr.E_GEQ);
	if (skip("!="))
	    return new Expr(e, parseAdd(), Expr.E_NEQ);
	if (skip("<"))
	    return new Expr(e, parseAdd(), Expr.E_LESS);
	if (skip(">"))
	    return new Expr(e, parseAdd(), Expr.E_GREATER);
	return e;
    }

    Expr parseAdd() {
	Expr e = parseMult();
	while (true) {
	    if (skip("+"))
		e = new Expr(e, parseMult(), Expr.E_ADD);
	    else if (skip("-"))
		e = new Expr(e, parseMult(), Expr.E_SUB);
	    else
		break;
	}
	return e;
    }

    Expr parseMult() {
	Expr e = parseUminus();
	while (true) {
	    if (skip("*"))
		e = new Expr(e, parseUminus(), Expr.E_MUL);
	    else if (skip("/"))
		e = new Expr(e, parseUminus(), Expr.E_DIV);
	    else
		break;
	}
	return e;
    }

    Expr parseUminus() {
	skip("+");
	if (skip("!"))
	    return new Expr(parseUminus(), null, Expr.E_NOT);
	if (skip("-"))
	    return new Expr(parseUminus(), null, Expr.E_UMINUS);
	return parsePow();
    }

    Expr parsePow() {
	Expr e = parseTerm();
	while (true) {
	    if (skip("^"))
		e = new Expr(e, parseTerm(), Expr.E_POW);
	    else
		break;
	}
	return e;
    }

    Expr parseFunc(int t) {
	skipOrError("(");
	Expr e = parse();
	skipOrError(")");
	return new Expr(e, null, t);
    }

    Expr parseFuncMulti(int t, int minArgs, int maxArgs) {
	int args = 1;
	skipOrError("(");
	Expr e1 = parse();
	Expr e = new Expr(e1, null, t);
	while (skip(",")) {
	    Expr enext = parse();
	    e.children.add(enext);
	    args++;
	}
	skipOrError(")");
	if (args < minArgs || args > maxArgs)
	    setError("bad number of function args: " + args);
	return e;
    }
    
    // Special parser for lag() that assigns a unique buffer index at parse time
    Expr parseLag() {
	skipOrError("(");
	Expr e1 = parse();  // The value expression
	skipOrError(",");
	Expr e2 = parse();  // The delay expression
	skipOrError(")");
	// Assign a unique buffer index at parse time
	int assignedIndex = Expr.nextLagIndex++;
	if (assignedIndex >= ExprState.MAX_LAG_BUFFERS) {
	    setError("too many lag() calls in expression (max " + ExprState.MAX_LAG_BUFFERS + ")");
	    assignedIndex = ExprState.MAX_LAG_BUFFERS - 1;
	}
	return new Expr(e1, e2, Expr.E_LAG, assignedIndex);
    }

    Expr parseTerm() {
		if (skip("(")) {
			Expr e = parse();
			skipOrError(")");
			return e;
		}
		// Handle case-insensitive 't' for time
		if (token.equalsIgnoreCase("t")) {
			getToken();
			return new Expr(Expr.E_T);
		}
		// Handle built-in variables _a through _i (underscore prefix)
		// This avoids conflict with single-letter labeled nodes like 'I', 'K'
		if (token.length() == 2 && token.charAt(0) == '_') {
			char c = Character.toLowerCase(token.charAt(1));
			if (c >= 'a' && c <= 'i') {
			getToken();
			return new Expr(Expr.E_A + (c-'a'));
			}
		}
		
		// Handle last variables (_lasta, _lastb, etc.) - case insensitive
		// Format: _last followed by letter a-i
		if (token.toLowerCase().startsWith("_last") && token.length() == 6) {
			char c = Character.toLowerCase(token.charAt(5));
			if (c >= 'a' && c <= 'i') {
			getToken();
			return new Expr(Expr.E_LASTA + (c-'a'));
			}
		}
		// Handle derivatives (_dadt, _dbdt, etc.) - case insensitive
		// Format: _d followed by letter a-i, followed by dt
		if (token.toLowerCase().startsWith("_d") && token.toLowerCase().endsWith("dt") && token.length() == 5) {
			char c = Character.toLowerCase(token.charAt(2));
			if (c >= 'a' && c <= 'i') {
			getToken();
			return new Expr(Expr.E_DADT + (c-'a'));
			}
		}
		// Also support dadt without underscore (dadt, dbdt, etc.) - case insensitive
		// Format: d followed by letter a-i, followed by dt (length 4)
		if (token.toLowerCase().startsWith("d") && token.toLowerCase().endsWith("dt") && token.length() == 4) {
			char c = Character.toLowerCase(token.charAt(1));
			if (c >= 'a' && c <= 'i') {
			getToken();
			return new Expr(Expr.E_DADT + (c-'a'));
			}
		}
		if (token.equalsIgnoreCase("lastoutput")) {
			getToken();
			return new Expr(Expr.E_LASTOUTPUT);
		}
		if (token.equalsIgnoreCase("timestep")) {
			getToken();
			return new Expr(Expr.E_TIMESTEP);
		}
		if (token.equalsIgnoreCase("pi")) {
			getToken();
			return new Expr(Expr.E_VAL, 3.14159265358979323846);
		}

		// Handle known functions first (sin, cos, etc.)
				//	if (skipIgnoreCase("e"))
				//	    return new Expr(Expr.E_VAL, 2.7182818284590452354);
		if (skipIgnoreCase("sin"))
			return parseFunc(Expr.E_SIN);
		if (skipIgnoreCase("cos"))
			return parseFunc(Expr.E_COS);
		if (skipIgnoreCase("asin"))
			return parseFunc(Expr.E_ASIN);
		if (skipIgnoreCase("acos"))
			return parseFunc(Expr.E_ACOS);
		if (skipIgnoreCase("atan"))
			return parseFunc(Expr.E_ATAN);
		if (skipIgnoreCase("sinh"))
			return parseFunc(Expr.E_SINH);
		if (skipIgnoreCase("cosh"))
			return parseFunc(Expr.E_COSH);
		if (skipIgnoreCase("tanh"))
			return parseFunc(Expr.E_TANH);
		if (skipIgnoreCase("abs"))
			return parseFunc(Expr.E_ABS);
		if (skipIgnoreCase("exp"))
			return parseFunc(Expr.E_EXP);
		if (skipIgnoreCase("log"))
			return parseFunc(Expr.E_LOG);
		if (skipIgnoreCase("sqrt"))
			return parseFunc(Expr.E_SQRT);
		if (skipIgnoreCase("tan"))
			return parseFunc(Expr.E_TAN);
		if (skipIgnoreCase("tri"))
			return parseFunc(Expr.E_TRIANGLE);
		if (skipIgnoreCase("saw"))
			return parseFunc(Expr.E_SAWTOOTH);
		if (skipIgnoreCase("floor"))
			return parseFunc(Expr.E_FLOOR);
		if (skipIgnoreCase("ceil"))
			return parseFunc(Expr.E_CEIL);
		if (skipIgnoreCase("integrate"))
			return parseFunc(Expr.E_INTEGRATE);
		if (skipIgnoreCase("diff"))
			return parseFunc(Expr.E_DIFF);
		if (skipIgnoreCase("last"))
			return parseFunc(Expr.E_LAST);
		if (skipIgnoreCase("lag"))
			return parseLag();  // Special parser that assigns buffer index at parse time
		if (skipIgnoreCase("min"))
			return parseFuncMulti(Expr.E_MIN, 2, 1000);
		if (skipIgnoreCase("max"))
			return parseFuncMulti(Expr.E_MAX, 2, 1000);
		if (skipIgnoreCase("pwl"))
			return parseFuncMulti(Expr.E_PWL, 2, 1000);
		if (skipIgnoreCase("mod"))
			return parseFuncMulti(Expr.E_MOD, 2, 2);
		if (skipIgnoreCase("step"))
			return parseFuncMulti(Expr.E_STEP, 1, 2);
		if (skipIgnoreCase("select"))
			return parseFuncMulti(Expr.E_SELECT, 3, 3);
		if (skipIgnoreCase("clamp"))
			return parseFuncMulti(Expr.E_CLAMP, 3, 3);
		if (skipIgnoreCase("pwr"))
			return parseFuncMulti(Expr.E_PWR, 2, 2);
		if (skipIgnoreCase("pwrs"))
			return parseFuncMulti(Expr.E_PWRS, 2, 2);

		// Then check for valid identifiers (potential node names)
		if (isValidIdentifier(token)) {
			String nodeRef = token;
			getToken();
			return new Expr(Expr.E_NODE_REF, nodeRef);
}	
		// Finally try numeric parsing	
		try {
			Expr e = new Expr(Expr.E_VAL, Double.valueOf(token).doubleValue());
			getToken();
			return e;
		} catch (Exception e) {
			if (token.length() == 0)
			setError("unexpected end of input");
			else
			setError("unrecognized token: " + token);
			return new Expr(Expr.E_VAL, 0);
		}
    }

    // Helper method to get labeled nodes in consistent sorted order
    private String[] getSortedLabeledNodes() {
	return LabeledNodeElm.getSortedLabeledNodeNames();
    }

    ExprParser(String s) {
	text = s; // Keep original case - we'll handle case insensitivity in comparisons
	tlen = text.length();
	pos = 0;
	err = null;
	// Reset lag index counter so each expression parse starts fresh
	Expr.resetLagIndexCounter();
	getToken();
    }
    
    String gotError() { return err; }
};
