package com.lushprojects.circuitjs1.client.elements.economics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EquationTableElm stock classification")
class EquationTableElmStockClassificationTest {

    @Test
    @DisplayName("detects integrate root as stock equation")
    void detectsIntegrateRootAsStockEquation() {
        assertTrue(EquationTableElm.isStockEquation("H", "integrate(wages - taxes)"));
    }

    @Test
    @DisplayName("detects lagged self accumulation as stock equation")
    void detectsLaggedSelfAccumulationAsStockEquation() {
        assertTrue(EquationTableElm.isStockEquation("H", "H = last(H) + wages - taxes"));
        assertTrue(EquationTableElm.isStockEquation("H", "H[-1] + wages"));
        assertTrue(EquationTableElm.isStockEquation("H", "wages + H(-1)"));
    }

    @Test
    @DisplayName("rejects non-accumulating lag expressions")
    void rejectsNonAccumulatingLagExpressions() {
        assertFalse(EquationTableElm.isStockEquation("H", "last(H)"));
        assertFalse(EquationTableElm.isStockEquation("H", "last(F) + wages"));
    }

    @Test
    @DisplayName("returns false for malformed stock-like expressions")
    void returnsFalseForMalformedStockLikeExpressions() {
        assertFalse(EquationTableElm.isStockEquation("H", "H = last(H) +"));
    }
}