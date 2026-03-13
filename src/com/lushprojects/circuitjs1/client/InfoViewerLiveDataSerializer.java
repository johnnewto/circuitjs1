/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

final class InfoViewerLiveDataSerializer {

    private InfoViewerLiveDataSerializer() {
    }

    static String buildLiveDataJson(CirSim sim) {
        if (sim == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"circuit-live\"");
        sb.append(",\"running\":").append(sim.simIsRunning());
        sb.append(",\"t\":").append(sim.t);
        sb.append(",\"dt\":").append(sim.timeStep);
        sb.append(",\"vars\":{");

        boolean first = true;
        first = appendLiveVar(sb, "t", sim.t, first);
        first = appendLiveVar(sb, "dt", sim.timeStep, first);

        String[] names = ComputedValues.getComputedValueNames();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            Double value = ComputedValues.getConvergedValue(name);
            if (value == null || value.isNaN() || value.isInfinite()) {
                continue;
            }
            first = appendLiveVar(sb, name, value.doubleValue(), first);
        }

        sb.append("}");
        appendLiveTablesJson(sb, sim);
        appendLiveSankeysJson(sb, sim);
        appendLiveScopesJson(sb, sim);
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendLiveTablesJson(StringBuilder sb, CirSim sim) {
        sb.append(",\"tables\":[");
        boolean firstTable = true;

        if (sim.elmList != null) {
            for (int i = 0; i < sim.elmList.size(); i++) {
                CircuitElm elm = sim.elmList.get(i);
                if (elm instanceof TableElm) {
                    TableElm table = (TableElm) elm;
                    if (!firstTable) sb.append(',');
                    firstTable = false;
                    appendSingleTableSnapshot(sb, table);
                    continue;
                }
                if (elm instanceof EquationTableElm) {
                    EquationTableElm eqTable = (EquationTableElm) elm;
                    if (!firstTable) sb.append(',');
                    firstTable = false;
                    appendEquationTableSnapshot(sb, eqTable);
                }
            }
        }

        sb.append(']');
    }

    private static void appendEquationTableSnapshot(StringBuilder sb, EquationTableElm table) {
        sb.append('{');

        String tableName = table.getTableName();
        if (tableName == null || tableName.trim().isEmpty()) {
            tableName = "Equation Table";
        }

        int rows = Math.max(0, table.getRowCount());

        sb.append("\"type\":\"equation\"");
        sb.append(",\"name\":\"").append(escapeJson(tableName)).append("\"");

        sb.append(",\"cols\":[\"Value\"]");

        sb.append(",\"rowNames\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            String rowName = table.getOutputName(row);
            if (rowName == null || rowName.trim().isEmpty()) {
                rowName = "Row " + (row + 1);
            }
            sb.append('"').append(escapeJson(rowName)).append('"');
        }
        sb.append(']');

        sb.append(",\"values\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            double value = table.getDisplayValue(row);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                value = 0.0;
            }
            sb.append('[').append(value).append(']');
        }
        sb.append(']');

        sb.append(",\"labels\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            String equation = table.getEquation(row);
            if (equation == null) {
                equation = "";
            }
            sb.append("[\"").append(escapeJson(equation.trim())).append("\"]");
        }
        sb.append(']');

        sb.append(",\"hints\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            String outputName = table.getOutputName(row);
            String hint = HintRegistry.getHint(outputName);
            if (hint == null) {
                hint = "";
            }
            sb.append('"').append(escapeJson(hint)).append('"');
        }
        sb.append(']');

        sb.append('}');
    }

    private static void appendSingleTableSnapshot(StringBuilder sb, TableElm table) {
        sb.append('{');

        String tableName = table.getTableTitle();
        if (tableName == null || tableName.trim().isEmpty()) {
            tableName = "Table";
        }

        int rows = Math.max(0, table.getRows());
        int cols = Math.max(0, table.getCols());

        sb.append("\"type\":\"table\"");
        sb.append(",\"name\":\"").append(escapeJson(tableName)).append("\"");

        sb.append(",\"cols\":[");
        for (int col = 0; col < cols; col++) {
            if (col > 0) {
                sb.append(',');
            }
            String header = table.getColumnHeader(col);
            if (header == null || header.trim().isEmpty()) {
                header = "Col" + (col + 1);
            }
            sb.append('"').append(escapeJson(header)).append('"');
        }
        sb.append(']');

        sb.append(",\"rowNames\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            String rowName = table.getRowDescription(row);
            if (rowName == null || rowName.trim().isEmpty()) {
                rowName = "Row " + (row + 1);
            }
            sb.append('"').append(escapeJson(rowName)).append('"');
        }
        sb.append(']');

        sb.append(",\"values\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            sb.append('[');
            for (int col = 0; col < cols; col++) {
                if (col > 0) {
                    sb.append(',');
                }
                double value = 0.0;
                TableColumn column = table.getColumn(col);
                if (column != null) {
                    value = column.getCachedCellValue(row);
                    if (Double.isNaN(value) || Double.isInfinite(value)) {
                        value = 0.0;
                    }
                }
                sb.append(value);
            }
            sb.append(']');
        }
        sb.append(']');

        sb.append(",\"labels\":[");
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                sb.append(',');
            }
            sb.append('[');
            for (int col = 0; col < cols; col++) {
                if (col > 0) {
                    sb.append(',');
                }
                String label = "";
                TableColumn column = table.getColumn(col);
                if (column != null) {
                    String equation = column.getCellEquation(row);
                    if (equation != null) {
                        label = equation.trim();
                    }
                }
                sb.append('"').append(escapeJson(label)).append('"');
            }
            sb.append(']');
        }
        sb.append(']');

        sb.append('}');
    }

    private static void appendLiveSankeysJson(StringBuilder sb, CirSim sim) {
        sb.append(",\"sankeys\":[");
        boolean first = true;
        if (sim.elmList != null) {
            for (int i = 0; i < sim.elmList.size(); i++) {
                CircuitElm elm = sim.elmList.get(i);
                if (!(elm instanceof TableElm)) continue;
                TableElm table = (TableElm) elm;
                String title = table.getTableTitle();
                if (title == null || title.trim().isEmpty()) continue;
                String sankeyJson = new SFCSankeyViewer(table).buildSankeyJSON();
                if (!first) sb.append(',');
                first = false;
                sb.append("{\"name\":\"").append(escapeJson(title.trim())).append("\",\"data\":");
                sb.append(sankeyJson);
                sb.append('}');
            }
        }
        sb.append(']');
    }

    private static void appendLiveScopesJson(StringBuilder sb, CirSim sim) {
        sb.append(",\"scopes\":[");
        boolean firstScope = true;
        if (sim.scopes != null) {
            for (int i = 0; i < sim.scopeCount; i++) {
                Scope scope = sim.scopes[i];
                if (scope == null || !scope.active()) {
                    continue;
                }
                if (scope.visiblePlots == null || scope.visiblePlots.size() == 0) {
                    continue;
                }

                if (!firstScope) {
                    sb.append(',');
                }
                firstScope = false;

                String scopeName = scope.getScopeMenuName();
                if (scopeName == null || scopeName.trim().isEmpty()) {
                    scopeName = "Scope " + (i + 1);
                }

                sb.append("{\"name\":\"").append(escapeJson(scopeName)).append("\"");
                sb.append(",\"traces\":[");
                boolean firstTrace = true;
                for (int p = 0; p < scope.visiblePlots.size(); p++) {
                    ScopePlot plot = scope.visiblePlots.get(p);
                    if (plot == null || plot.elm == null) {
                        continue;
                    }
                    if (!firstTrace) {
                        sb.append(',');
                    }
                    firstTrace = false;

                    String traceName = plot.elm.getScopeText(plot.value);
                    if (traceName == null || traceName.trim().isEmpty()) {
                        traceName = "Trace " + (p + 1);
                    }
                    double value = plot.lastValue;
                    if (Double.isNaN(value) || Double.isInfinite(value)) {
                        value = 0.0;
                    }
                    sb.append("{\"name\":\"").append(escapeJson(traceName)).append("\",\"value\":").append(value).append("}");
                }
                sb.append("]}");
            }
        }
        sb.append(']');
    }

    private static boolean appendLiveVar(StringBuilder sb, String name, double value, boolean first) {
        if (name == null || name.isEmpty() || Double.isNaN(value) || Double.isInfinite(value)) {
            return first;
        }
        if (!first) {
            sb.append(',');
        }
        sb.append('"').append(escapeJson(name)).append('"').append(':').append(value);
        return false;
    }

    private static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}