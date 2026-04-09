package com.lushprojects.circuitjs1.client.elements.economics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EquationTable variable coloring helpers")
class EquationTableVariableColoringTest {

    @Test
    @DisplayName("classifies uppercase variables as nominal")
    void classifiesUppercaseVariablesAsNominal() {
        assertEquals(
            EquationTableVariableColoring.VariableKind.NOMINAL,
            EquationTableVariableColoring.classifyToken("MoneyStock", false));
        assertEquals(
            EquationTableVariableColoring.VariableKind.NOMINAL,
            EquationTableVariableColoring.classifyToken("\\Alpha", false));
    }

    @Test
    @DisplayName("classifies lowercase variables as real")
    void classifiesLowercaseVariablesAsReal() {
        assertEquals(
            EquationTableVariableColoring.VariableKind.REAL,
            EquationTableVariableColoring.classifyToken("wages", false));
        assertEquals(
            EquationTableVariableColoring.VariableKind.REAL,
            EquationTableVariableColoring.classifyToken("\\beta", false));
    }

    @Test
    @DisplayName("leaves functions and ground uncolored")
    void leavesFunctionsAndGroundUncolored() {
        assertEquals(
            EquationTableVariableColoring.VariableKind.OTHER,
            EquationTableVariableColoring.classifyToken("integrate", true));
        assertEquals(
            EquationTableVariableColoring.VariableKind.OTHER,
            EquationTableVariableColoring.classifyToken("gnd", false));
    }

    @Test
    @DisplayName("validates hex colors")
    void validatesHexColors() {
        assertTrue(EquationTableVariableColoring.isValidColorHex("#4A8CFF"));
        assertTrue(EquationTableVariableColoring.isValidColorHex("#2fb35f"));
        assertFalse(EquationTableVariableColoring.isValidColorHex("blue"));
        assertFalse(EquationTableVariableColoring.isValidColorHex("#12345"));
    }
}