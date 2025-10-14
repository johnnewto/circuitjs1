package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class ExprState {
    //int n;
    double values[];
    double lastValues[];
    double lastOutput;
    double t;
    ExprState(int xx) {
	//n = xx;
	values = new double[9];
	lastValues = new double[9];
	values[4] = Math.E;
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
    }
}

class Expr {
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
	case E_DIV: return left.eval(es)/right.eval(es);
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
	case E_MOD:
	    return left.eval(es) % right.eval(es);
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
	case E_NODE_REF:
	    // Direct node reference - get voltage from labeled node or computed value
	    if (CirSim.theSim != null && nodeName != null) {
		// First check for computed values (from TableElm)
		Double computedValue = ComputedValues.getComputedValue(nodeName);
		if (computedValue != null) {
		    return computedValue.doubleValue();
		}
		// Then check for actual labeled node voltage
		return CirSim.theSim.getLabeledNodeVoltage(nodeName);
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
    static final int E_TERNARY = 32;
    static final int E_OR = 33;
    static final int E_AND = 34;
    static final int E_EQUALS = 35;
    static final int E_LEQ = 36;
    static final int E_GEQ = 37;
    static final int E_LESS = 38;
    static final int E_GREATER = 39;
    static final int E_NEQ = 40;
    static final int E_NOT = 41;
    static final int E_FLOOR = 42;
    static final int E_CEIL = 43;
    static final int E_ASIN = 44;
    static final int E_ACOS = 45;
    static final int E_ATAN = 46;
    static final int E_SINH = 47;
    static final int E_COSH = 48;
    static final int E_TANH = 49;
    static final int E_A = 50;
    static final int E_DADT = E_A+10; // must be E_A+10
    static final int E_LASTA = E_DADT+10; // should be at end and equal to E_DADT+10
    static final int E_NODE_REF = E_LASTA+10; // Direct node reference by name
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
		} else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_') {
			// Support identifiers with letters, numbers, and underscores
			// Must start with letter or underscore
			for (i = pos; i != tlen; i++) {
				char ch = text.charAt(i);
				if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || 
					  (ch >= '0' && ch <= '9') || ch == '_'))
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
    boolean isValidIdentifier(String token) {
	if (token == null || token.length() == 0)
	    return false;
	
	// First character must be letter or underscore
	char first = token.charAt(0);
	if (!((first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z') || first == '_'))
	    return false;
	
	// Remaining characters must be letters, numbers, or underscores
	for (int i = 1; i < token.length(); i++) {
	    char c = token.charAt(i);
	    if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || 
		  (c >= '0' && c <= '9') || c == '_'))
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
		// Handle single-letter variables a-i (case insensitive)
		if (token.length() == 1) {
			char c = Character.toLowerCase(token.charAt(0));
			if (c >= 'a' && c <= 'i') {
			getToken();
			return new Expr(Expr.E_A + (c-'a'));
			}
		}
		
		// // Check if token is a labeled node name (case-insensitive comparison)
		// if (LabeledNodeElm.labelList != null && !LabeledNodeElm.labelList.isEmpty()) {
		// 	String[] availableNodes = getSortedLabeledNodes();
		// 	for (String availNode : availableNodes) {
		// 		if (availNode.equalsIgnoreCase(token)) {
		// 			getToken();
		// 			return new Expr(Expr.E_NODE_REF, availNode);  // Direct node reference
		// 		}
		// 	}
		// }
		// Handle last variables (lasta, lastb, etc.) - case insensitive
		if (token.toLowerCase().startsWith("last") && token.length() == 5) {
			char c = Character.toLowerCase(token.charAt(4));
			if (c >= 'a' && c <= 'i') {
			getToken();
			return new Expr(Expr.E_LASTA + (c-'a'));
			}
		}
		// Handle derivatives (dadt, dbdt, etc.) - case insensitive
		if (token.toLowerCase().endsWith("dt") && token.toLowerCase().startsWith("d") && token.length() == 4) {
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
	getToken();
    }
    
    String gotError() { return err; }
};
