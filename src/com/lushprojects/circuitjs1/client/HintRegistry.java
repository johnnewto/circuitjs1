/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.lushprojects.circuitjs1.client.util.Locale;

/**
 * HintRegistry - Centralized storage for variable/node hints (glossary).
 * 
 * Stores hints keyed by variable name. Any element (LabeledNodeElm, EquationTableElm,
 * Scopes, etc.) can query this registry to get a hint for a given name.
 * 
 * Hints are stored in circuit files as:
 *   % Hint varname Description text here
 * 
 * This allows a single definition of hints that apply everywhere the variable appears.
 */
public class HintRegistry {
    
    // Hash map storing hints keyed by variable name
    private static Map<String, String> hints = new HashMap<String, String>();
    // Secondary index for normalized-name lookups (Greek and script variants)
    private static Map<String, String> normalizedHints = new HashMap<String, String>();
    
    /**
     * Set a hint for a variable name
     */
    public static void setHint(String name, String hint) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        String key = name.trim();
        if (hint == null) {
            hints.remove(key);
            rebuildNormalizedIndex();
        } else {
            // Don't trim - allow space-only hints as placeholders
            hints.put(key, hint);
            String normalizedKey = normalizeHintKey(key);
            if (normalizedKey != null && !normalizedKey.isEmpty()) {
                normalizedHints.put(normalizedKey, hint);
            }
        }
    }
    
    /**
     * Get hint for a variable name
     * @return hint text or null if not found
     */
    public static String getHint(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String key = name.trim();
        String direct = hints.get(key);
        if (direct != null) {
            return direct;
        }

        String normalizedKey = normalizeHintKey(key);
        if (normalizedKey == null || normalizedKey.isEmpty()) {
            return null;
        }
        return normalizedHints.get(normalizedKey);
    }
    
    /**
     * Check if a hint exists for a name
     */
    public static boolean hasHint(String name) {
        return getHint(name) != null;
    }
    
    /**
     * Remove a hint
     */
    public static void removeHint(String name) {
        if (name == null || name.trim().isEmpty()) return;
        hints.remove(name.trim());
        rebuildNormalizedIndex();
    }
    
    /**
     * Clear all hints (called when loading new circuit)
     */
    public static void clear() {
        hints.clear();
        normalizedHints.clear();
    }
    
    /**
     * Get all hint names
     */
    public static Set<String> getAllNames() {
        return new java.util.HashSet<String>(hints.keySet());
    }
    
    /**
     * Get number of hints stored
     */
    public static int size() {
        return hints.size();
    }
    
    /**
     * Parse a hint line from circuit file
     * Format: % Hint varname Description text here
     */
    public static void parseHintLine(String line) {
        // Line may come in as "% Hint varname Description text" or "Hint varname Description text"
        if (line == null) {
            return;
        }
        
        // Strip "% " prefix if present
        String trimmedLine = line;
        if (trimmedLine.startsWith("% ")) {
            trimmedLine = trimmedLine.substring(2);
        }
        
        if (!trimmedLine.startsWith("Hint ")) {
            return;
        }
        
        String rest = trimmedLine.substring(5).trim(); // Remove "Hint "
        int spaceIdx = rest.indexOf(' ');
        if (spaceIdx > 0) {
            String name = rest.substring(0, spaceIdx);
            String hint = rest.substring(spaceIdx + 1).trim();
            // Unescape the hint text (spaces stored as \s)
            hint = CustomLogicModel.unescape(hint);
            setHint(name, hint);
        }
    }
    
    /**
     * Dump all hints as circuit file lines
     * @return String with all "% Hint ..." lines
     */
    public static String dumpAll() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : hints.entrySet()) {
            String hint = entry.getValue();
            // Skip blank/space-only hints
            if (hint == null || hint.trim().isEmpty()) {
                continue;
            }
            sb.append("% Hint ");
            sb.append(entry.getKey());
            sb.append(" ");
            // Escape the hint text (spaces as \s)
            sb.append(CustomLogicModel.escape(hint));
            sb.append("\n");
        }
        return sb.toString();
    }

    /** Rebuild normalized hint index from primary map. */
    private static void rebuildNormalizedIndex() {
        normalizedHints.clear();
        for (Map.Entry<String, String> entry : hints.entrySet()) {
            String normalizedKey = normalizeHintKey(entry.getKey());
            if (normalizedKey != null && !normalizedKey.isEmpty()) {
                normalizedHints.put(normalizedKey, entry.getValue());
            }
        }
    }

    /**
     * Normalize names so lookups match across formatting variants:
     * - LaTeX Greek (\alpha) and Unicode Greek (α)
     * - underscore/caret script markers and Unicode sub/superscript glyphs
     */
    private static String normalizeHintKey(String key) {
        if (key == null) {
            return null;
        }

        String converted = Locale.convertGreekSymbols(key.trim());
        if (converted == null || converted.isEmpty()) {
            return "";
        }

        StringBuilder normalized = new StringBuilder(converted.length());
        for (int i = 0; i < converted.length(); i++) {
            char c = converted.charAt(i);

            if (Character.isWhitespace(c) || c == '_' || c == '^' || c == '{' || c == '}') {
                continue;
            }

            char scriptBase = scriptCharToBase(c);
            if (scriptBase != 0) {
                normalized.append(scriptBase);
                continue;
            }

            normalized.append(c);
        }

        return normalized.toString();
    }

    /** Convert Unicode sub/superscript codepoints to base characters when available. */
    private static char scriptCharToBase(char c) {
        switch (c) {
            case '\u2080': return '0';
            case '\u2081': return '1';
            case '\u2082': return '2';
            case '\u2083': return '3';
            case '\u2084': return '4';
            case '\u2085': return '5';
            case '\u2086': return '6';
            case '\u2087': return '7';
            case '\u2088': return '8';
            case '\u2089': return '9';
            case '\u2070': return '0';
            case '\u00B9': return '1';
            case '\u00B2': return '2';
            case '\u00B3': return '3';
            case '\u2074': return '4';
            case '\u2075': return '5';
            case '\u2076': return '6';
            case '\u2077': return '7';
            case '\u2078': return '8';
            case '\u2079': return '9';
            case '\u2090': return 'a';
            case '\u2091': return 'e';
            case '\u2092': return 'o';
            case '\u2093': return 'x';
            case '\u2095': return 'h';
            case '\u2096': return 'k';
            case '\u2097': return 'l';
            case '\u2098': return 'm';
            case '\u2099': return 'n';
            case '\u209A': return 'p';
            case '\u209B': return 's';
            case '\u209C': return 't';
            case '\u207F': return 'n';
            default: return 0;
        }
    }
}
