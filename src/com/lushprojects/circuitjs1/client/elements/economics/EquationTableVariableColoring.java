package com.lushprojects.circuitjs1.client.elements.economics;

import com.lushprojects.circuitjs1.client.util.Color;

/**
 * Pure helpers for classifying rendered equation-table variable tokens and
 * validating per-table color settings.
 */
final class EquationTableVariableColoring {

    enum VariableKind {
        NOMINAL,
        REAL,
        OTHER
    }

    private EquationTableVariableColoring() {
    }

    static VariableKind classifyToken(String token, boolean functionLike) {
        if (token == null || token.isEmpty() || functionLike) {
            return VariableKind.OTHER;
        }
        if ("gnd".equalsIgnoreCase(token)) {
            return VariableKind.OTHER;
        }

        char firstLetter = findFirstLetter(token);
        if (firstLetter == 0) {
            return VariableKind.OTHER;
        }
        if (Character.isUpperCase(firstLetter)) {
            return VariableKind.NOMINAL;
        }
        if (Character.isLowerCase(firstLetter)) {
            return VariableKind.REAL;
        }
        return VariableKind.OTHER;
    }

    static boolean isValidColorHex(String colorText) {
        return colorText != null && colorText.matches("#[0-9a-fA-F]{6}");
    }

    static Color parseColorOrDefault(String colorText, Color fallback) {
        if (!isValidColorHex(colorText)) {
            return fallback;
        }
        try {
            return new Color(colorText);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static char findFirstLetter(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isLetter(c)) {
                return c;
            }
        }
        return 0;
    }
}