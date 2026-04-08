package com.lushprojects.circuitjs1.client.elements.economics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("EquationTableEditDialog mode conversion helpers")
class EquationTableEditDialogTest {

    @Test
    @DisplayName("convertParamRowsToVoltage changes param rows but preserves comments and existing modes")
    void convertParamRowsToVoltageChangesOnlyEditableParamRows() {
        EquationTableElm.RowOutputMode[] modes = new EquationTableElm.RowOutputMode[] {
            EquationTableElm.RowOutputMode.PARAM_MODE,
            EquationTableElm.RowOutputMode.FLOW_MODE,
            EquationTableElm.RowOutputMode.PARAM_MODE,
            EquationTableElm.RowOutputMode.VOLTAGE_MODE,
            EquationTableElm.RowOutputMode.PARAM_MODE
        };
        String[] names = new String[] {
            "alpha",
            "beta->gamma",
            "# comment row",
            "delta",
            "epsilon"
        };

        int converted = EquationTableModeConversionHelper.convertParamRowsToVoltage(modes, names, 5);

        assertEquals(2, converted);
        assertEquals(EquationTableElm.RowOutputMode.VOLTAGE_MODE, modes[0]);
        assertEquals(EquationTableElm.RowOutputMode.FLOW_MODE, modes[1]);
        assertEquals(EquationTableElm.RowOutputMode.PARAM_MODE, modes[2]);
        assertEquals(EquationTableElm.RowOutputMode.VOLTAGE_MODE, modes[3]);
        assertEquals(EquationTableElm.RowOutputMode.VOLTAGE_MODE, modes[4]);
    }

    @Test
    @DisplayName("convertParamRowsToVoltage tolerates empty input")
    void convertParamRowsToVoltageToleratesEmptyInput() {
        int converted = EquationTableModeConversionHelper.convertParamRowsToVoltage(null, null, 0);

        assertEquals(0, converted);
    }
}