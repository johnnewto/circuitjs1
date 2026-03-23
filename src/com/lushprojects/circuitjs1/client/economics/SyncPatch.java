package com.lushprojects.circuitjs1.client.economics;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SyncPatch {
    public enum Kind {
        SET_CELL_EQUATION,
        ADD_ROW,
        SET_INITIAL_VALUE
    }

    public final Kind kind;
    public final int row;
    public final int col;
    public final String flowDesc;
    public final String equation;
    public final Map<Integer, String> rowEquations;
    public final double initialValue;

    private SyncPatch(Kind kind,
                     int row,
                     int col,
                     String flowDesc,
                     String equation,
                     Map<Integer, String> rowEquations,
                     double initialValue) {
        this.kind = kind;
        this.row = row;
        this.col = col;
        this.flowDesc = flowDesc;
        this.equation = equation;
        this.rowEquations = rowEquations;
        this.initialValue = initialValue;
    }

    public static SyncPatch setCellEquation(int row, int col, String equation) {
        return new SyncPatch(Kind.SET_CELL_EQUATION, row, col, null, equation, null, 0);
    }

    public static SyncPatch addRow(String flowDesc, Map<Integer, String> rowEquations) {
        return new SyncPatch(
            Kind.ADD_ROW,
            -1,
            -1,
            flowDesc,
            null,
            new LinkedHashMap<Integer, String>(rowEquations),
            0
        );
    }

    public static SyncPatch setInitialValue(int col, double value) {
        return new SyncPatch(Kind.SET_INITIAL_VALUE, -1, col, null, null, null, value);
    }
}
