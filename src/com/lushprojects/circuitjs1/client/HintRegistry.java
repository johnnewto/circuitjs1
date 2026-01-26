/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
        } else {
            // Don't trim - allow space-only hints as placeholders
            hints.put(key, hint);
        }
    }
    
    /**
     * Get hint for a variable name
     * @return hint text or null if not found
     */
    public static String getHint(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        return hints.get(name.trim());
    }
    
    /**
     * Check if a hint exists for a name
     */
    public static boolean hasHint(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        return hints.containsKey(name.trim());
    }
    
    /**
     * Remove a hint
     */
    public static void removeHint(String name) {
        if (name == null || name.trim().isEmpty()) return;
        hints.remove(name.trim());
    }
    
    /**
     * Clear all hints (called when loading new circuit)
     */
    public static void clear() {
        hints.clear();
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
}
