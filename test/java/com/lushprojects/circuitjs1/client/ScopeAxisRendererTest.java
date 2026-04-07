package com.lushprojects.circuitjs1.client.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scope axis renderer")
class ScopeAxisRendererTest {
    @Test
    @DisplayName("axis tick labels drop trailing zero decimals")
    void trimAxisTickLabelDropsTrailingZeroDecimals() {
        assertEquals("12V", ScopeAxisRenderer.trimAxisTickLabel("12.00V"));
        assertEquals("12.5V", ScopeAxisRenderer.trimAxisTickLabel("12.50V"));
        assertEquals("0A", ScopeAxisRenderer.trimAxisTickLabel("-0.00A"));
    }

    @Test
    @DisplayName("axis tick labels preserve significant decimals and exponents")
    void trimAxisTickLabelPreservesSignificantDigits() {
        assertEquals("12.34V", ScopeAxisRenderer.trimAxisTickLabel("12.34V"));
        assertEquals("1.2E3V", ScopeAxisRenderer.trimAxisTickLabel("1.20E3V"));
        assertEquals("3.1mA", ScopeAxisRenderer.trimAxisTickLabel("3.10mA"));
    }

    @Test
    @DisplayName("time axis labels trim fixed two-decimal times")
    void trimAxisTickLabelHandlesTimeLabels() {
        assertEquals("1 s", ScopeAxisRenderer.trimAxisTickLabel("1.00 s"));
        assertEquals("2.5 ms", ScopeAxisRenderer.trimAxisTickLabel("2.50 ms"));
        assertEquals("100 μs", ScopeAxisRenderer.trimAxisTickLabel("100.00 μs"));
    }
}