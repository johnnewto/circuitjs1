package com.lushprojects.circuitjs1.client.io;

import com.lushprojects.circuitjs1.client.elements.economics.TableColumn.ColumnType;

import java.util.ArrayList;

public class SFCRTableDumpBuilderService {
    public static class DumpBuildResult {
        public final String dump;
        public final int y2;
        public final boolean truncated;
        public final int originalRowCount;
        public final int finalRowCount;

        DumpBuildResult(String dump, int y2, boolean truncated, int originalRowCount, int finalRowCount) {
            this.dump = dump;
            this.y2 = y2;
            this.truncated = truncated;
            this.originalRowCount = originalRowCount;
            this.finalRowCount = finalRowCount;
        }
    }

    public DumpBuildResult buildMatrixDump(String name, int currentX, int currentY,
                                    ArrayList<String> columnNames, ArrayList<String> columnTypes,
                                    ArrayList<String> rowNames,
                                    ArrayList<String[]> tableRows, Boolean showInitialValuesOverride,
                                    Boolean invisibleOverride) {
        int rows = rowNames.size();
        if (rows <= 0 || columnNames == null || columnNames.isEmpty()) {
            return null;
        }

        boolean hasSumColumn = false;
        String lastCol = columnNames.get(columnNames.size() - 1).trim();
        if (lastCol.equals("Σ") || lastCol.equalsIgnoreCase("Sigma")
            || lastCol.equalsIgnoreCase("Sum") || lastCol.equalsIgnoreCase("Total")
            || lastCol.equalsIgnoreCase("Row total") || lastCol.equals("∑")) {
            hasSumColumn = true;
        }

        int cols = hasSumColumn ? columnNames.size() : columnNames.size() + 1;
        if (cols <= 1) {
            return null;
        }

        int x1 = currentX;
        int y1 = currentY;
        int x2 = currentX + 400;
        int y2 = currentY + (rows + 3) * 16;

        StringBuilder dump = new StringBuilder();
        int flags = (invisibleOverride != null && invisibleOverride.booleanValue()) ? 2 : 0;
        dump.append("265 ").append(x1).append(" ").append(y1).append(" ");
        dump.append(x2).append(" ").append(y2).append(" ").append(flags).append(" ");
        dump.append(rows).append(" ");
        dump.append(cols).append(" ");
        dump.append("6 16 0 ");

        boolean showInitial = (showInitialValuesOverride != null) ? showInitialValuesOverride.booleanValue() : false;
        dump.append(showInitial ? "true 2 1 false 5 0 false " : "false 2 1 false 5 0 false ");
        dump.append(SFCRUtil.escapeToken(name.replace("_", " "))).append(" ");
        dump.append("\\0 ");

        if (hasSumColumn) {
            for (int i = 0; i < columnNames.size() - 1; i++) {
                dump.append(SFCRUtil.escapeToken(columnNames.get(i))).append(" ");
            }
            dump.append("Σ ");
        } else {
            for (String col : columnNames) {
                dump.append(SFCRUtil.escapeToken(col)).append(" ");
            }
            dump.append("Σ ");
        }

        for (String row : rowNames) {
            dump.append(SFCRUtil.escapeToken(row)).append(" ");
        }
        for (int i = 0; i < cols; i++) {
            dump.append("0 ");
        }
        for (int i = 0; i < cols - 1; i++) {
            dump.append(resolveMatrixColumnType(columnTypes, i).name()).append(" ");
        }
        dump.append("COMPUTED ");

        int dataCols = cols - 1;
        for (int r = 0; r < rows; r++) {
            String[] rowData = tableRows.get(r);
            for (int c = 0; c < dataCols; c++) {
                String eq = (c < rowData.length) ? rowData[c] : "";
                if (eq.equals("-") || eq.trim().isEmpty()) {
                    eq = "";
                }
                dump.append(SFCRUtil.escapeToken(eq)).append(" ");
            }
            dump.append("\\0 ");
        }
        dump.append("true 0.000001");
        return new DumpBuildResult(dump.toString(), y2, false, rows, rows);
    }

    private ColumnType resolveMatrixColumnType(ArrayList<String> columnTypes, int index) {
        if (columnTypes == null || index < 0 || index >= columnTypes.size()) {
            return ColumnType.NONE;
        }
        String raw = columnTypes.get(index);
        if (raw == null) {
            return ColumnType.NONE;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return ColumnType.NONE;
        }
        String upper = normalized.toUpperCase();
        if (upper.equals("NONE")) {
            return ColumnType.NONE;
        }
        if (upper.equals("ASSET")) {
            return ColumnType.ASSET;
        }
        if (upper.equals("LIABILITY")) {
            return ColumnType.LIABILITY;
        }
        if (upper.equals("EQUITY")) {
            return ColumnType.EQUITY;
        }
        if (upper.equals("SECTOR")) {
            return ColumnType.SECTOR;
        }
        if (upper.equals("COMPUTED")) {
            return ColumnType.COMPUTED;
        }
        return ColumnType.NONE;
    }

    public DumpBuildResult buildEquationDump(String name, int currentX, int currentY,
                                      ArrayList<String> outputNames, ArrayList<String> equations,
                                      ArrayList<Integer> outputModes, ArrayList<String> targetNodeNames,
                                      ArrayList<String> sliderVarNames, ArrayList<Double> sliderValues,
                                      ArrayList<String> initialEquations, Boolean invisibleOverride) {
        int rows = outputNames.size();
        if (rows == 0) {
            return null;
        }
        int originalRows = rows;
        boolean truncated = false;
        if (rows > com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm.MAX_ROWS) {
            rows = com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm.MAX_ROWS;
            truncated = true;
        }

        int x1 = currentX;
        int y1 = currentY;
        int x2 = currentX + 200;
        int y2 = currentY + (rows + 2) * 16;

        StringBuilder dump = new StringBuilder();
        int flags = 2;
        if (invisibleOverride != null && invisibleOverride.booleanValue()) {
            flags |= 4;
        }
        dump.append("266 ").append(x1).append(" ").append(y1).append(" ");
        dump.append(x2).append(" ").append(y2).append(" ").append(flags).append(" ");
        dump.append(SFCRUtil.escapeToken(name.replace("_", " "))).append(" ");
        dump.append(rows).append(" ");

        for (int i = 0; i < rows; i++) {
            dump.append(SFCRUtil.escapeToken(outputNames.get(i))).append(" ");
            dump.append(SFCRUtil.escapeToken(equations.get(i))).append(" ");
            String initEq = (i < initialEquations.size() && initialEquations.get(i) != null)
                ? initialEquations.get(i) : "";
            dump.append(SFCRUtil.escapeToken(initEq)).append(" ");
            String sliderVar = (i < sliderVarNames.size() && sliderVarNames.get(i) != null)
                ? sliderVarNames.get(i) : "";
            dump.append(SFCRUtil.escapeToken(sliderVar)).append(" ");
            double sliderValue = (i < sliderValues.size() && sliderValues.get(i) != null)
                ? sliderValues.get(i).doubleValue() : 0.5;
            dump.append(sliderValue).append(" ");
            int modeOrdinal = (i < outputModes.size() && outputModes.get(i) != null) ? outputModes.get(i) : 0;
            dump.append(modeOrdinal).append(" ");
            String target = (i < targetNodeNames.size() && targetNodeNames.get(i) != null)
                ? targetNodeNames.get(i) : "";
            dump.append(SFCRUtil.escapeToken(target)).append(" ");
            dump.append("1.0 ");
            dump.append("1.0 ");
            dump.append("0 ");
        }

        return new DumpBuildResult(dump.toString(), y2, truncated, originalRows, rows);
    }
}
