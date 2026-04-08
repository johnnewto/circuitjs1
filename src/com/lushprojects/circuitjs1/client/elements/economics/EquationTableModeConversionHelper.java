package com.lushprojects.circuitjs1.client.elements.economics;

import com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm.RowOutputMode;

/** Pure helpers for bulk row-mode conversions used by the equation table editor. */
final class EquationTableModeConversionHelper {

    private EquationTableModeConversionHelper() {
    }

    static int convertParamRowsToVoltage(RowOutputMode[] outputModes, String[] outputNames, int rowCount) {
        if (outputModes == null || outputNames == null || rowCount <= 0) {
            return 0;
        }

        int convertedCount = 0;
        int limit = Math.min(rowCount, Math.min(outputModes.length, outputNames.length));
        for (int row = 0; row < limit; row++) {
            if (EquationTableElm.isCommentRowName(outputNames[row])) {
                continue;
            }
            if (outputModes[row] == RowOutputMode.PARAM_MODE) {
                outputModes[row] = RowOutputMode.VOLTAGE_MODE;
                convertedCount++;
            }
        }
        return convertedCount;
    }
}