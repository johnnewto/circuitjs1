package com.lushprojects.circuitjs1.client.elements.economics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EquationTable scope plot row encoding")
class EquationTableScopePlotValueTest {

    @Test
    @DisplayName("marks encoded row plots without colliding with built-in scope values")
    void marksEncodedRowPlotsWithoutCollidingWithBuiltInValues() {
        int encoded = EquationTableScopePlotValue.encode("Assets->Liabilities");

        assertTrue(EquationTableScopePlotValue.isEncoded(encoded));
        assertFalse(EquationTableScopePlotValue.isEncoded(0));
        assertFalse(EquationTableScopePlotValue.isEncoded(7));
    }

    @Test
    @DisplayName("matches the same row key and distinguishes different keys")
    void matchesSameRowKeyAndDistinguishesDifferentKeys() {
        int encoded = EquationTableScopePlotValue.encode("cash");

        assertTrue(EquationTableScopePlotValue.matches(encoded, "cash"));
        assertFalse(EquationTableScopePlotValue.matches(encoded, "cash->gnd"));
        assertNotEquals(encoded, EquationTableScopePlotValue.encode("profits"));
    }
}