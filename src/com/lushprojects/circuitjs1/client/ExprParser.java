package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.electronics.wiring.LabeledNodeElm;

public class ExprParser {
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
			// dot namespaces (Y3.flow), and LaTeX formatting (Z_1, x^2, Z_{banks})
			// Must start with letter, underscore, or backslash
			for (i = pos; i != tlen; i++) {
				char ch = text.charAt(i);
				if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || 
					  (ch >= '0' && ch <= '9') || ch == '_' || ch == '\\' || ch == '^' || ch == '{' || ch == '}' || ch == '.'))
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
	
	// Remaining characters must be letters, numbers, underscores, backslashes,
	// script markers, or dot namespace separators.
	for (int i = 1; i < token.length(); i++) {
	    char c = token.charAt(i);
	    if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || 
		  (c >= '0' && c <= '9') || c == '_' || c == '\\' || c == '^' || c == '{' || c == '}' || c == '.'))
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

    public Expr parseExpression() {
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

    // Special parser for smooth() that assigns a unique state index at parse time
    Expr parseSmooth() {
	skipOrError("(");
	Expr e1 = parse();  // The input expression
	skipOrError(",");
	Expr e2 = parse();  // The theta expression
	skipOrError(")");
	Expr e = new Expr(e1, e2, Expr.E_SMOOTH);
	int assignedIndex = Expr.nextSmoothIndex++;
	if (assignedIndex >= ExprState.MAX_SMOOTH_STATES) {
	    setError("too many smooth() calls in expression (max " + ExprState.MAX_SMOOTH_STATES + ")");
	    assignedIndex = ExprState.MAX_SMOOTH_STATES - 1;
	}
	e.smoothIndex = assignedIndex;
	return e;
    }

    // Parser for delay(x[, tau]) where tau defaults to 1
    Expr parseDelay() {
	skipOrError("(");
	Expr e1 = parse();  // The input expression
	Expr e2 = new Expr(Expr.E_VAL, 1.0);  // Default tau
	if (skip(",")) {
	    e2 = parse();
	}
	skipOrError(")");
	Expr e = new Expr(e1, e2, Expr.E_DELAY);
	int assignedIndex = Expr.nextSmoothIndex++;
	if (assignedIndex >= ExprState.MAX_SMOOTH_STATES) {
	    setError("too many delay()/smooth() calls in expression (max " + ExprState.MAX_SMOOTH_STATES + ")");
	    assignedIndex = ExprState.MAX_SMOOTH_STATES - 1;
	}
	e.smoothIndex = assignedIndex;
	return e;
    }

    // Parser for lookup(TableName, x[, clampFlag])
    Expr parseLookup() {
	skipOrError("(");
	if (!isValidIdentifier(token)) {
	    setError("lookup() first argument must be a lookup table name");
	    return new Expr(Expr.E_VAL, 0);
	}
	String tableName = token;
	getToken();
	skipOrError(",");
	Expr xExpr = parse();
	Expr clampExpr = null;
	if (skip(",")) {
	    clampExpr = parse();
	}
	skipOrError(")");
	Expr e = new Expr(xExpr, null, Expr.E_LOOKUP);
	if (clampExpr != null) {
	    e.children.add(clampExpr);
	}
	e.nodeName = tableName;
	return e;
    }

    /**
     * Parse postfix lag alias index after a variable name.
     * Accepts token-level forms equivalent to -1, including spaced variants
     * like "-1" and "- 1" (because tokenization strips whitespace).
     */
    private boolean parsePostfixLastAliasIndex(String closeToken) {
	int sign = 1;
	if (skip("+")) {
	    sign = 1;
	} else if (skip("-")) {
	    sign = -1;
	}

	double numeric;
	try {
	    numeric = Double.valueOf(token).doubleValue();
	    getToken();
	} catch (Exception e) {
	    return false;
	}

	skipOrError(closeToken);
	double value = sign * numeric;
	return Math.abs(value + 1.0) < 1e-12;
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
		if (skipIgnoreCase("smooth"))
			return parseSmooth();
		if (skipIgnoreCase("delay"))
			return parseDelay();
		if (skipIgnoreCase("min"))
			return parseFuncMulti(Expr.E_MIN, 2, 1000);
		if (skipIgnoreCase("max"))
			return parseFuncMulti(Expr.E_MAX, 2, 1000);
		if (skipIgnoreCase("pwl"))
			return parseFuncMulti(Expr.E_PWL, 2, 1000);
		if (skipIgnoreCase("pwlx"))
			return parseFuncMulti(Expr.E_PWLX, 5, 1000);
		if (skipIgnoreCase("lookup"))
			return parseLookup();
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

			// Alternative lagged-reference syntax: X(-1) == last(X)
			// Keep scope intentionally narrow to avoid introducing generic function-call
			// semantics on arbitrary identifiers.
			if (skip("(")) {
				if (parsePostfixLastAliasIndex(")")) {
					return new Expr(new Expr(Expr.E_NODE_REF, nodeRef), null, Expr.E_LAST);
				}
				setError("only (-1) is supported after variable names; use last(" + nodeRef + ")");
				return new Expr(Expr.E_NODE_REF, nodeRef);
			}

			// Alternative SFCR-style syntax: X[-1] == last(X)
			if (skip("[")) {
				if (parsePostfixLastAliasIndex("]")) {
					return new Expr(new Expr(Expr.E_NODE_REF, nodeRef), null, Expr.E_LAST);
				}
				setError("only [-1] is supported after variable names; use last(" + nodeRef + ")");
				return new Expr(Expr.E_NODE_REF, nodeRef);
			}

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

    public ExprParser(String s) {
	text = s; // Keep original case - we'll handle case insensitivity in comparisons
	tlen = text.length();
	pos = 0;
	err = null;
	// Reset lag index counter so each expression parse starts fresh
	Expr.resetLagIndexCounter();
	getToken();
    }
    
    public String gotError() { return err; }
};
