package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.economics.TableContentView;

class TableContentViewStub implements TableContentView {
    private final String tableTitle;
    private final String[] headers;
    private final String[] rowDescs;
    private final String[][] equations;
    private final double[] initValues;

    TableContentViewStub(String tableTitle,
                         String[] headers,
                         String[] rowDescs,
                         String[][] equations,
                         double[] initValues) {
        this.tableTitle = tableTitle;
        this.headers = headers;
        this.rowDescs = rowDescs;
        this.equations = equations;
        this.initValues = initValues;
    }

    public String getTableTitle() {
        return tableTitle;
    }

    public int getRows() {
        return rowDescs.length;
    }

    public int getCols() {
        return headers.length;
    }

    public String getRowDescription(int row) {
        return rowDescs[row];
    }

    public String getColumnHeader(int col) {
        return headers[col];
    }

    public String getCellEquation(int row, int col) {
        return equations[row][col];
    }

    public int findColumnByStockName(String stockName) {
        if (stockName == null) {
            return -1;
        }
        String trimmed = stockName.trim();
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i];
            if (header != null && trimmed.equals(header.trim())) {
                return i;
            }
        }
        return -1;
    }

    public double getInitialValue(int col) {
        return initValues[col];
    }

    TableContentViewStub applyPatches(java.util.List<SyncPatch> patches) {
        String[] newHeaders = headers.clone();
        String[] newRows = rowDescs.clone();
        String[][] newEquations = new String[equations.length][];
        for (int i = 0; i < equations.length; i++) {
            newEquations[i] = equations[i].clone();
        }
        double[] newInit = initValues.clone();

        java.util.ArrayList<SyncPatch> addRows = new java.util.ArrayList<SyncPatch>();
        for (SyncPatch patch : patches) {
            if (patch.kind == SyncPatch.Kind.ADD_ROW) {
                addRows.add(patch);
                continue;
            }
            if (patch.kind == SyncPatch.Kind.SET_CELL_EQUATION) {
                newEquations[patch.row][patch.col] = patch.equation;
                continue;
            }
            if (patch.kind == SyncPatch.Kind.SET_INITIAL_VALUE) {
                newInit[patch.col] = patch.initialValue;
            }
        }

        if (!addRows.isEmpty()) {
            int original = newRows.length;
            String[] expandedRows = new String[original + addRows.size()];
            String[][] expandedEqs = new String[original + addRows.size()][newHeaders.length];

            for (int i = 0; i < original; i++) {
                expandedRows[i] = newRows[i];
                for (int c = 0; c < newHeaders.length; c++) {
                    expandedEqs[i][c] = newEquations[i][c];
                }
            }

            int rowIndex = original;
            for (SyncPatch patch : addRows) {
                expandedRows[rowIndex] = patch.flowDesc;
                for (int c = 0; c < newHeaders.length; c++) {
                    expandedEqs[rowIndex][c] = "";
                }
                for (java.util.Map.Entry<Integer, String> entry : patch.rowEquations.entrySet()) {
                    expandedEqs[rowIndex][entry.getKey().intValue()] = entry.getValue();
                }
                rowIndex++;
            }

            newRows = expandedRows;
            newEquations = expandedEqs;
        }

        return new TableContentViewStub(tableTitle, newHeaders, newRows, newEquations, newInit);
    }
}
