/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client.util;

import java.util.HashMap;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class Locale {

    public static HashMap<String, String> localizationMap;

    public static String ohmString = "\u03a9";
    public static String muString = "\u03bc";
    
    // Greek symbol mapping (LaTeX-style to Unicode)
    private static HashMap<String, String> greekSymbols;
    
    static {
        greekSymbols = new HashMap<String, String>();
        // Lowercase Greek letters
        greekSymbols.put("\\alpha", "\u03b1");    // α
        greekSymbols.put("\\beta", "\u03b2");     // β
        greekSymbols.put("\\gamma", "\u03b3");    // γ
        greekSymbols.put("\\delta", "\u03b4");    // δ
        greekSymbols.put("\\epsilon", "\u03b5");  // ε
        greekSymbols.put("\\zeta", "\u03b6");     // ζ
        greekSymbols.put("\\eta", "\u03b7");      // η
        greekSymbols.put("\\theta", "\u03b8");    // θ
        greekSymbols.put("\\iota", "\u03b9");     // ι
        greekSymbols.put("\\kappa", "\u03ba");    // κ
        greekSymbols.put("\\lambda", "\u03bb");   // λ
        greekSymbols.put("\\mu", "\u03bc");       // μ
        greekSymbols.put("\\nu", "\u03bd");       // ν
        greekSymbols.put("\\xi", "\u03be");       // ξ
        greekSymbols.put("\\omicron", "\u03bf");  // ο
        greekSymbols.put("\\pi", "\u03c0");       // π
        greekSymbols.put("\\rho", "\u03c1");      // ρ
        greekSymbols.put("\\sigma", "\u03c3");    // σ
        greekSymbols.put("\\tau", "\u03c4");      // τ
        greekSymbols.put("\\upsilon", "\u03c5");  // υ
        greekSymbols.put("\\phi", "\u03c6");      // φ
        greekSymbols.put("\\chi", "\u03c7");      // χ
        greekSymbols.put("\\psi", "\u03c8");      // ψ
        greekSymbols.put("\\omega", "\u03c9");    // ω
        
        // Uppercase Greek letters
        greekSymbols.put("\\Alpha", "\u0391");    // Α
        greekSymbols.put("\\Beta", "\u0392");     // Β
        greekSymbols.put("\\Gamma", "\u0393");    // Γ
        greekSymbols.put("\\Delta", "\u0394");    // Δ
        greekSymbols.put("\\Epsilon", "\u0395");  // Ε
        greekSymbols.put("\\Zeta", "\u0396");     // Ζ
        greekSymbols.put("\\Eta", "\u0397");      // Η
        greekSymbols.put("\\Theta", "\u0398");    // Θ
        greekSymbols.put("\\Iota", "\u0399");     // Ι
        greekSymbols.put("\\Kappa", "\u039a");    // Κ
        greekSymbols.put("\\Lambda", "\u039b");   // Λ
        greekSymbols.put("\\Mu", "\u039c");       // Μ
        greekSymbols.put("\\Nu", "\u039d");       // Ν
        greekSymbols.put("\\Xi", "\u039e");       // Ξ
        greekSymbols.put("\\Omicron", "\u039f");  // Ο
        greekSymbols.put("\\Pi", "\u03a0");       // Π
        greekSymbols.put("\\Rho", "\u03a1");      // Ρ
        greekSymbols.put("\\Sigma", "\u03a3");    // Σ
        greekSymbols.put("\\Tau", "\u03a4");      // Τ
        greekSymbols.put("\\Upsilon", "\u03a5");  // Υ
        greekSymbols.put("\\Phi", "\u03a6");      // Φ
        greekSymbols.put("\\Chi", "\u03a7");      // Χ
        greekSymbols.put("\\Psi", "\u03a8");      // Ψ
        greekSymbols.put("\\Omega", "\u03a9");    // Ω
        
        // Common math symbols
        greekSymbols.put("\\degree", "\u00b0");   // °
        greekSymbols.put("\\pm", "\u00b1");       // ±
        greekSymbols.put("\\times", "\u00d7");    // ×
        greekSymbols.put("\\div", "\u00f7");      // ÷
        greekSymbols.put("\\infty", "\u221e");    // ∞
        greekSymbols.put("\\sqrt", "\u221a");     // √
        greekSymbols.put("\\approx", "\u2248");   // ≈
        greekSymbols.put("\\neq", "\u2260");      // ≠
        greekSymbols.put("\\leq", "\u2264");      // ≤
        greekSymbols.put("\\geq", "\u2265");      // ≥
    }
    
    /**
     * Convert LaTeX-style escape sequences to Unicode characters.
     * Processes strings like "\\beta = 2.5" to "β = 2.5"
     * 
     * @param input String potentially containing \\symbol sequences
     * @return String with symbols converted to Unicode
     */
    public static String convertGreekSymbols(String input) {
        if (input == null || input.indexOf('\\') < 0)
            return input;
            
        String result = input;
        
        // Replace all known Greek symbols
        for (String symbol : greekSymbols.keySet()) {
            result = result.replace(symbol, greekSymbols.get(symbol));
        }
        
        return result;
    }

    public static String LS(String s) {
        if (s == null)
            return null;

        if (s.length() == 0) { // empty strings trip up the 'if (ix != s.length() - 1)' below
            return s;
        }

        String sm = localizationMap.get(s);
        if (sm != null)
            return sm;

        // use trailing ~ to differentiate strings that are the same in English but need
        // different translations.
        // remove these if there's no translation.
        int ix = s.indexOf('~');
        if (ix != s.length() - 1)
            return s;

        s = s.substring(0, ix);
        sm = localizationMap.get(s);
        if (sm != null)
            return sm;

        return s;
    }

    public static SafeHtml LSHTML(String s) {
        return SafeHtmlUtils.fromTrustedString(LS(s));
    }

}
