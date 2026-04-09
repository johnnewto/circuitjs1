package com.lushprojects.circuitjs1.client.elements.economics;

/**
 * Encodes equation-table row identifiers into reserved scope-plot values.
 */
final class EquationTableScopePlotValue {
    private static final int ROW_PLOT_MARKER = 0x40000000;
    private static final int ROW_PLOT_MASK = 0x3fffffff;

    private EquationTableScopePlotValue() {
    }

    static int encode(String rowKey) {
        String normalized = normalize(rowKey);
        return ROW_PLOT_MARKER | (normalized.hashCode() & ROW_PLOT_MASK);
    }

    static boolean isEncoded(int value) {
        return (value & ROW_PLOT_MARKER) != 0;
    }

    static boolean matches(int encodedValue, String rowKey) {
        return isEncoded(encodedValue) && encodedValue == encode(rowKey);
    }

    static String normalize(String rowKey) {
        return rowKey == null ? "" : rowKey.trim();
    }
}