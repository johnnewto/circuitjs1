/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

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
            case FLOW_MODE:  return "flow";
            case PARAM_MODE: return "param";
            default:         return "voltage";
        }
    }

    /** Parse an SFCR mode keyword into a {@link EquationTableElm.RowOutputMode}. */
    public static EquationTableElm.RowOutputMode parseEquationRowMode(String mode) {
        if (mode == null) return EquationTableElm.RowOutputMode.VOLTAGE_MODE;
        String m = mode.trim().toLowerCase();
        if (m.equals("flow")  || m.equals("flow_mode"))                         return EquationTableElm.RowOutputMode.FLOW_MODE;
        if (m.equals("stock") || m.equals("stock_mode"))                        return EquationTableElm.RowOutputMode.FLOW_MODE;
        if (m.equals("param") || m.equals("parameter") || m.equals("param_mode")) return EquationTableElm.RowOutputMode.PARAM_MODE;
        return EquationTableElm.RowOutputMode.VOLTAGE_MODE;
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
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
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
     * alternative calculus notation: {@code d()} → {@code diff()},
     * {@code ∫()} → {@code integrate()}.
     */
    public static String normalizeExpression(String expr) {
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
        expr = expr.replace("d(", "diff(");
        expr = expr.replace("∫(", "integrate(");

        return expr.trim();
    }
}
