/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.io;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm;

/**
 * Shared utility methods for SFCR format parsing and exporting.
 *
 * <p>These are pure static helpers used by both {@link SFCRParser} and
 * {@link SFCRExporter} to keep the two classes in sync on format details.
 */
public class SFCRUtil {

    private SFCRUtil() { /* non-instantiable */ }

    // =========================================================================
    // Name / identifier helpers
    // =========================================================================

    /**
     * Sanitize a display name for use in an SFCR block header.
     * Replaces whitespace runs with underscores.
     */
    public static String sanitizeName(String name) {
        if (name == null) return "Unnamed";
        return name.replaceAll("\\s+", "_");
    }

    // =========================================================================
    // Position formatting
    // =========================================================================

    /**
     * Format position string for SFCR block header.
     * Returns empty string if element is null or at origin.
     */
    public static String formatPosition(CircuitElm elm) {
        if (elm == null) return "";
        int x = elm.x;
        int y = elm.y;
        if (x == 0 && y == 0) return "";
        return " x=" + x + " y=" + y;
    }

    /**
     * Append preserved leading comments for a given block key.
     */
    public static void appendLeadingBlockComments(CirSim sim, StringBuilder sb, String blockType, String blockName) {
        if (sim == null || sb == null) {
            return;
        }
        String key = SFCRBlockCommentRegistry.makeKey(blockType, blockName);
        java.util.Vector<String> comments = sim.getSFCRDocumentState().getBlockComments(key);
        if (comments == null || comments.size() == 0) {
            return;
        }
        for (int i = 0; i < comments.size(); i++) {
            String line = comments.get(i);
            if (line == null) {
                continue;
            }
            sb.append(line).append("\n");
        }
        sb.append("\n");
    }

    // =========================================================================
    // Token escaping (for CircuitJS dump format)
    // =========================================================================

    /**
     * Escape a token for the CircuitJS dump format.
     * Mirrors CustomLogicModel.escape() without loading that class.
     */
    public static String escapeToken(String s) {
        if (s.length() == 0) return "\\0";
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace(" ", "\\s")
                .replace("+", "\\p").replace("=", "\\q").replace("#", "\\h")
                .replace("&", "\\a").replace("\r", "\\r");
    }

    // =========================================================================
    // Combined name parsing (name->target notation)
    // =========================================================================

    /**
     * Parse a combined "name-&gt;target" notation.
     * Mirrors EquationTableElm.parseCombinedName() without loading that class.
     * Returns [name, target] or [name, ""] if no separator found.
     */
    public static String[] parseCombinedName(String combined) {
        if (combined == null) return new String[]{"", ""};
        int arrowIdx = combined.indexOf("->");
        int sepLen = 2;
        if (arrowIdx < 0) { arrowIdx = combined.indexOf("-||-"); sepLen = 4; }
        if (arrowIdx < 0) { arrowIdx = combined.indexOf("\u2192"); sepLen = 1; }   // \u2192 = →
        if (arrowIdx < 0) { arrowIdx = combined.indexOf("\u22A3\u22A2"); sepLen = 2; }  // \u22A3\u22A2 = ⊣⊢
        if (arrowIdx < 0) { arrowIdx = combined.indexOf(","); sepLen = 1; }
        if (arrowIdx >= 0) {
            return new String[]{
                combined.substring(0, arrowIdx).trim(),
                combined.substring(arrowIdx + sepLen).trim()
            };
        }
        return new String[]{combined.trim(), ""};
    }

    // =========================================================================
    // Mode ordinal parsing
    // =========================================================================

    /**
     * Parse a row mode string to its ordinal value.
     * Returns 0 for voltage mode, 1 for legacy flow/stock compatibility, 3 for param mode.
     */
    public static int parseModeOrdinal(String mode) {
        if (mode == null) return 0;
        String m = mode.toLowerCase().trim();
        if (m.equals("flow") || m.equals("flow_mode") || m.equals("stock") || m.equals("stock_mode")) return 1;
        if (m.equals("param") || m.equals("parameter") || m.equals("param_mode")) return 3;
        return 0;
    }

    // =========================================================================
    // Markdown table cell escaping
    // =========================================================================

    /** Escape pipe characters in a markdown table cell value. */
    public static String escapeTableCell(String text) {
        if (text == null) return "";
        return text.replace("|", "\\|");
    }

    /** Unescape pipe characters in a parsed markdown table cell. */
    public static String unescapeTableCell(String text) {
        if (text == null) return "";
        return text.replace("\\|", "|").trim();
    }

    // =========================================================================
    // EquationTableElm.RowOutputMode serialization
    // =========================================================================

    /** Serialize a {@link EquationTableElm.RowOutputMode} to its SFCR keyword. */
    public static String formatEquationRowMode(EquationTableElm.RowOutputMode mode) {
        if (mode == null) return "voltage";
        switch (mode) {
            case PARAM_MODE: return "param";
            default:         return "voltage";
        }
    }

    /** Parse an SFCR mode keyword into a {@link EquationTableElm.RowOutputMode}. */
    public static EquationTableElm.RowOutputMode parseEquationRowMode(String mode) {
        if (mode == null) return EquationTableElm.RowOutputMode.VOLTAGE_MODE;
        String m = mode.trim().toLowerCase();
        if (m.equals("flow")  || m.equals("flow_mode"))                         return EquationTableElm.RowOutputMode.VOLTAGE_MODE;
        if (m.equals("stock") || m.equals("stock_mode"))                        return EquationTableElm.RowOutputMode.VOLTAGE_MODE;
        if (m.equals("param") || m.equals("parameter") || m.equals("param_mode")) return EquationTableElm.RowOutputMode.PARAM_MODE;
        return EquationTableElm.RowOutputMode.VOLTAGE_MODE;
    }

    // =========================================================================
    // Boolean parsing
    // =========================================================================

    /**
     * Parse flexible boolean strings (true/false, 1/0, yes/no).
     */
    public static boolean parseBoolean(String text, boolean defaultValue) {
        if (text == null) return defaultValue;
        String t = text.trim().toLowerCase();
        if (t.equals("true") || t.equals("1") || t.equals("yes")) return true;
        if (t.equals("false") || t.equals("0") || t.equals("no")) return false;
        return defaultValue;
    }

    // =========================================================================
    // Identifier character helpers
    // =========================================================================

    /**
     * Check if a character can start a SFCR identifier (variable/lookup name).
     */
    public static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '\\';
    }

    /**
     * Check if a character can be part of a SFCR identifier (after the first char).
     */
    public static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '\\' || c == '^' || c == '{' || c == '}' || c == '.';
    }

    /**
     * Check if a string looks like a valid SFCR identifier.
     */
    public static boolean isValidIdentifier(String name) {
        if (name == null || name.isEmpty()) return false;
        if (!isIdentifierStart(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) {
            if (!isIdentifierPart(name.charAt(i))) return false;
        }
        return true;
    }

    // =========================================================================
    // Parenthesis matching
    // =========================================================================

    /**
     * Find the index of the closing parenthesis matching the one at openIndex.
     * Returns -1 if not found.
     */
    public static int findMatchingParen(String text, int openIndex) {
        if (text == null || openIndex < 0 || openIndex >= text.length()) {
            return -1;
        }
        int depth = 0;
        boolean inHashComment = false;
        char quoteChar = 0;
        boolean escaped = false;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inHashComment) {
                if (c == '\n' || c == '\r') {
                    inHashComment = false;
                }
                continue;
            }
            if (quoteChar != 0) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == quoteChar) {
                    quoteChar = 0;
                }
                continue;
            }
            if (c == '#') {
                inHashComment = true;
                continue;
            }
            if (c == '\'' || c == '"') {
                quoteChar = c;
                escaped = false;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    // =========================================================================
    // Table row parsing
    // =========================================================================

    /**
     * Parse a markdown table row into cells, trimming leading/trailing pipes and whitespace.
     */
    public static String[] parseTableRow(String line) {
        if (line == null) return new String[0];
        String l = line;
        if (l.startsWith("|")) l = l.substring(1);
        if (l.endsWith("|")) l = l.substring(0, l.length() - 1);
        
        String[] parts = l.split("\\|", -1);
        java.util.ArrayList<String> cells = new java.util.ArrayList<String>();
        for (String part : parts) {
            cells.add(part.trim());
        }
        
        // Remove trailing empty cells
        while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
            cells.remove(cells.size() - 1);
        }
        
        return cells.toArray(new String[0]);
    }

    // =========================================================================
    // Parenthesis counting
    // =========================================================================

    /**
     * Count the net change in parenthesis depth over a single line of text.
     * Returns a positive value when there are more opening than closing
     * parentheses, negative when there are more closing, and 0 when balanced.
     * Null/empty input returns 0.
     */
    public static int parenthesesDelta(String line) {
        if (line == null || line.isEmpty()) {
            return 0;
        }
        int delta = 0;
        char quoteChar = 0;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoteChar != 0) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (ch == '\\') {
                    escaped = true;
                    continue;
                }
                if (ch == quoteChar) {
                    quoteChar = 0;
                }
                continue;
            }
            if (ch == '#') {
                break;
            }
            if (ch == '\'' || ch == '"') {
                quoteChar = ch;
                escaped = false;
                continue;
            }
            if (ch == '(') {
                delta++;
            } else if (ch == ')') {
                delta--;
            }
        }
        return delta;
    }

    // =========================================================================
    // Expression / variable name normalization (import-side)
    // =========================================================================

    /**
     * Normalize a variable name coming from SFCR text.
     * Converts common Unicode / plain-text Greek symbol representations to the
     * backslash-prefixed form used internally (e.g. {@code α} → {@code \alpha}).
     */
    public static String normalizeVariableName(String name) {
        if (name == null) return "";

        // Protect already-escaped backslash forms first
        name = name.replace("\\alpha", "\u0001ALPHA\u0001");
        name = name.replace("\\theta", "\u0001THETA\u0001");
        name = name.replace("\\Delta", "\u0001DELTA\u0001");

        // Replace unescaped variants
        name = name.replace("alpha1",   "\u0001ALPHA\u0001_1");
        name = name.replace("alpha2",   "\u0001ALPHA\u0001_2");
        name = name.replace("alpha_1",  "\u0001ALPHA\u0001_1");
        name = name.replace("alpha_2",  "\u0001ALPHA\u0001_2");
        name = name.replace("α_1",      "\u0001ALPHA\u0001_1");
        name = name.replace("α_2",      "\u0001ALPHA\u0001_2");
        name = name.replace("α1",       "\u0001ALPHA\u0001_1");
        name = name.replace("α2",       "\u0001ALPHA\u0001_2");
        name = name.replace("theta",    "\u0001THETA\u0001");
        name = name.replace("θ",        "\u0001THETA\u0001");
        name = name.replace("Delta",    "\u0001DELTA\u0001");
        name = name.replace("∆",        "\u0001DELTA\u0001");

        // Restore backslash-prefixed forms
        name = name.replace("\u0001ALPHA\u0001", "\\alpha");
        name = name.replace("\u0001THETA\u0001", "\\theta");
        name = name.replace("\u0001DELTA\u0001", "\\Delta");

        return name.trim();
    }

    /**
     * Normalize an expression coming from SFCR text.
     * Converts Greek symbols (same as {@link #normalizeVariableName}) and maps
     * alternative notation while preserving user-facing aliases where possible.
     * In particular, {@code d(Name)} is preserved as written and interpreted by
     * the expression parser as a period-to-period difference, while
     * {@code ∫()} maps to {@code integrate()}.
     */
    public static String normalizeExpression(String expr) {
        return normalizeExpressionInternal(expr, true);
    }

    public static boolean isZeroOneConditionalExpression(String expr) {
        if (expr == null) {
            return false;
        }
        String trimmed = expr.trim();
        int q = findTopLevelChar(trimmed, '?');
        if (q < 0) {
            return false;
        }
        int colon = findMatchingTernaryColon(trimmed, q + 1);
        if (colon < 0) {
            return false;
        }
        String whenTrue = trimmed.substring(q + 1, colon).trim();
        String whenFalse = trimmed.substring(colon + 1).trim();
        return isNumericLiteral(whenTrue, 1) && isNumericLiteral(whenFalse, 0);
    }

    private static String normalizeExpressionInternal(String expr, boolean allowConditionalRewrite) {
        if (expr == null) return "";

        // Protect already-escaped backslash forms first
        expr = expr.replace("\\alpha", "\u0001ALPHA\u0001");
        expr = expr.replace("\\theta", "\u0001THETA\u0001");

        // Replace unescaped Greek variants
        expr = expr.replace("alpha1",  "\u0001ALPHA\u0001_1");
        expr = expr.replace("alpha2",  "\u0001ALPHA\u0001_2");
        expr = expr.replace("alpha_1", "\u0001ALPHA\u0001_1");
        expr = expr.replace("alpha_2", "\u0001ALPHA\u0001_2");
        expr = expr.replace("α_1",     "\u0001ALPHA\u0001_1");
        expr = expr.replace("α_2",     "\u0001ALPHA\u0001_2");
        expr = expr.replace("α1",      "\u0001ALPHA\u0001_1");
        expr = expr.replace("α2",      "\u0001ALPHA\u0001_2");
        expr = expr.replace("theta",   "\u0001THETA\u0001");
        expr = expr.replace("θ",       "\u0001THETA\u0001");

        // Restore backslash-prefixed forms
        expr = expr.replace("\u0001ALPHA\u0001", "\\alpha");
        expr = expr.replace("\u0001THETA\u0001", "\\theta");

        // Calculus notation aliases
        expr = expr.replace("∆(", "diff(");
        expr = expr.replace("∫(", "integrate(");

        expr = normalizeLaggedReferences(expr);
        expr = normalizeLeadingDecimalLiterals(expr);

        if (allowConditionalRewrite) {
            String ternary = rewriteRStyleIfElseToTernary(expr);
            if (ternary != null) {
                expr = ternary;
            }
        }

        return expr.trim();
    }

    private static String rewriteRStyleIfElseToTernary(String expr) {
        String trimmed = expr == null ? "" : expr.trim();
        if (!trimmed.startsWith("if")) {
            return null;
        }

        int condOpen = trimmed.indexOf('(');
        if (condOpen < 0) {
            return null;
        }
        int condClose = findMatchingParen(trimmed, condOpen);
        if (condClose < 0) {
            return null;
        }

        String prefix = trimmed.substring(0, condOpen).trim();
        if (!"if".equals(prefix)) {
            return null;
        }

        int trueOpen = skipWhitespace(trimmed, condClose + 1);
        if (trueOpen >= trimmed.length() || trimmed.charAt(trueOpen) != '{') {
            return null;
        }
        int trueClose = findMatchingBrace(trimmed, trueOpen);
        if (trueClose < 0) {
            return null;
        }

        int elseIdx = skipWhitespace(trimmed, trueClose + 1);
        if (!trimmed.startsWith("else", elseIdx)) {
            return null;
        }
        int falseOpen = skipWhitespace(trimmed, elseIdx + 4);
        if (falseOpen >= trimmed.length() || trimmed.charAt(falseOpen) != '{') {
            return null;
        }
        int falseClose = findMatchingBrace(trimmed, falseOpen);
        if (falseClose < 0) {
            return null;
        }
        if (skipWhitespace(trimmed, falseClose + 1) != trimmed.length()) {
            return null;
        }

        String condition = normalizeExpressionInternal(trimmed.substring(condOpen + 1, condClose), false);
        String whenTrue = normalizeExpressionInternal(trimmed.substring(trueOpen + 1, trueClose), true);
        String whenFalse = normalizeExpressionInternal(trimmed.substring(falseOpen + 1, falseClose), true);
        return "(" + condition + ") ? " + whenTrue + " : " + whenFalse;
    }

    private static String normalizeLaggedReferences(String expr) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (!isIdentifierStart(c)) {
                out.append(c);
                i++;
                continue;
            }

            int start = i;
            i++;
            while (i < expr.length() && isIdentifierPart(expr.charAt(i))) {
                i++;
            }
            String ident = expr.substring(start, i);

            int afterIdent = skipWhitespace(expr, i);
            if (afterIdent + 3 < expr.length() && expr.startsWith("[-1]", afterIdent)) {
                out.append("last(").append(ident).append(")");
                i = afterIdent + 4;
                continue;
            }
            if (afterIdent + 3 < expr.length() && expr.startsWith("(-1)", afterIdent)) {
                out.append("last(").append(ident).append(")");
                i = afterIdent + 4;
                continue;
            }

            out.append(ident);
        }
        return out.toString();
    }


    private static String normalizeLeadingDecimalLiterals(String expr) {
        String normalized = expr.replaceAll("\\s-\\.([0-9]+)", " - 0.$1");
        return normalized.replaceAll("(?<![0-9A-Za-z_])\\.([0-9]+)", "0.$1");
    }

    private static int skipWhitespace(String text, int index) {
        int i = index;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int findMatchingBrace(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findTopLevelChar(String text, char target) {
        int parenDepth = 0;
        int braceDepth = 0;
        int bracketDepth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;
            else if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth--;
            else if (c == target && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) return i;
        }
        return -1;
    }

    private static int findMatchingTernaryColon(String text, int startIndex) {
        int nestedTernary = 0;
        int parenDepth = 0;
        int braceDepth = 0;
        int bracketDepth = 0;
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;
            else if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth--;
            else if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                if (c == '?') {
                    nestedTernary++;
                } else if (c == ':') {
                    if (nestedTernary == 0) {
                        return i;
                    }
                    nestedTernary--;
                }
            }
        }
        return -1;
    }

    private static boolean isNumericLiteral(String text, int expected) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        try {
            return Math.abs(Double.parseDouble(text.trim()) - expected) < 1e-12;
        } catch (Exception e) {
            return false;
        }
    }
}
