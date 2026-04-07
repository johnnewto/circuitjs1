package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

import com.lushprojects.circuitjs1.client.util.Rectangle;
import java.util.Vector;

final class ScopeDataExporter {
    private ScopeDataExporter() {
    }

    static String exportCircularBufferAsCSV(
            Vector<ScopePlot> visiblePlots,
            Rectangle rect,
            int scopePointCount,
            CirSim sim,
            int speed) {
        StringBuilder sb = new StringBuilder();
        sb.append("Time (s)");
        for (int i = 0; i < visiblePlots.size(); i++) {
            ScopePlot p = visiblePlots.get(i);
            String label = getPlotLabel(p, i);
            sb.append(",").append(label).append(" Min");
            sb.append(",").append(label).append(" Max");
        }
        sb.append("\n");

        int width = getCircularBufferDisplayWidth(visiblePlots, rect, scopePointCount);
        if (width <= 0) {
            return "No circular buffer data available\n";
        }

        for (int x = 0; x < width; x++) {
            double time = sim.getTime() - (width - 1 - x) * sim.getMaxTimeStep() * speed;
            sb.append(time);
            for (int i = 0; i < visiblePlots.size(); i++) {
                ScopePlot p = visiblePlots.get(i);
                int ipa = p.startIndex(width);
                int ip = (x + ipa) & (scopePointCount - 1);
                sb.append(",").append(p.minValues[ip]);
                sb.append(",").append(p.maxValues[ip]);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    static String exportCircularBufferAsJSON(
            Vector<ScopePlot> visiblePlots,
            Rectangle rect,
            int scopePointCount,
            CirSim sim,
            int speed) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"source\": \"CircuitJS1 Scope\",\n");
        sb.append("  \"exportType\": \"circularBuffer\",\n");
        sb.append("  \"simulationTime\": ").append(sim.getTime()).append(",\n");
        sb.append("  \"timeStep\": ").append(sim.getMaxTimeStep() * speed).append(",\n");
        sb.append("  \"plots\": [\n");

        int width = getCircularBufferDisplayWidth(visiblePlots, rect, scopePointCount);
        if (width <= 0) {
            sb.append("  ]\n");
            sb.append("}\n");
            return sb.toString();
        }

        for (int i = 0; i < visiblePlots.size(); i++) {
            ScopePlot p = visiblePlots.get(i);
            String label = getPlotLabel(p, i);

            sb.append("    {\n");
            sb.append("      \"name\": \"").append(escapeJSON(label)).append("\",\n");
            sb.append("      \"units\": \"").append(Scope.getScaleUnitsText(p.units)).append("\",\n");
            sb.append("      \"color\": \"").append(p.color).append("\",\n");
            sb.append("      \"time\": [");

            for (int x = 0; x < width; x++) {
                if (x > 0) {
                    sb.append(", ");
                }
                double time = sim.getTime() - (width - 1 - x) * sim.getMaxTimeStep() * speed;
                sb.append(time);
            }
            sb.append("],\n");

            sb.append("      \"values\": [");
            int ipa = p.startIndex(width);
            for (int x = 0; x < width; x++) {
                if (x > 0) {
                    sb.append(", ");
                }
                int ip = (x + ipa) & (scopePointCount - 1);
                double midpoint = (p.minValues[ip] + p.maxValues[ip]) / 2.0;
                sb.append(midpoint);
            }
            sb.append("],\n");

            sb.append("      \"minValues\": [");
            for (int x = 0; x < width; x++) {
                if (x > 0) {
                    sb.append(", ");
                }
                int ip = (x + ipa) & (scopePointCount - 1);
                sb.append(p.minValues[ip]);
            }
            sb.append("],\n");

            sb.append("      \"maxValues\": [");
            for (int x = 0; x < width; x++) {
                if (x > 0) {
                    sb.append(", ");
                }
                int ip = (x + ipa) & (scopePointCount - 1);
                sb.append(p.maxValues[ip]);
            }
            sb.append("]\n");
            sb.append("    }");
            if (i < visiblePlots.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

        static String exportHistoryAsCSV(
            Scope scope,
            Vector<ScopePlot> visiblePlots,
            boolean drawFromZero,
            int historySize,
            double historySampleInterval) {
        if (!drawFromZero || historySize == 0) {
            return "No history data available\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Time (s)");
        for (int i = 0; i < visiblePlots.size(); i++) {
            ScopePlot p = visiblePlots.get(i);
            String label = getPlotLabel(p, i);
            sb.append(",").append(label).append(" Min");
            sb.append(",").append(label).append(" Max");
        }
        sb.append("\n");

        for (int x = 0; x < historySize; x++) {
            double time = x * historySampleInterval;
            sb.append(time);
            for (int i = 0; i < visiblePlots.size(); i++) {
                ScopePlot p = visiblePlots.get(i);
                VariableHistoryStore.SeriesSnapshot historySnapshot = scope.getHistorySnapshotForRender(p);
                if (historySnapshot != null) {
                    sb.append(",").append(historySnapshot.minValues[x]);
                    sb.append(",").append(historySnapshot.maxValues[x]);
                } else {
                    sb.append(",0,0");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

        static String exportHistoryAsJSON(
            Scope scope,
            Vector<ScopePlot> visiblePlots,
            boolean drawFromZero,
            double absoluteStartTime,
            int historySize,
            double historySampleInterval) {
        if (!drawFromZero || historySize == 0) {
            return "{\"error\": \"No history data available\"}\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"source\": \"CircuitJS1 Scope\",\n");
        sb.append("  \"exportType\": \"history\",\n");
        sb.append("  \"startTime\": 0,\n");
        sb.append("  \"absoluteStartTime\": ").append(absoluteStartTime).append(",\n");
        sb.append("  \"historySize\": ").append(historySize).append(",\n");
        sb.append("  \"sampleInterval\": ").append(historySampleInterval).append(",\n");
        sb.append("  \"plots\": [\n");

        for (int i = 0; i < visiblePlots.size(); i++) {
            ScopePlot p = visiblePlots.get(i);
            String label = getPlotLabel(p, i);

            sb.append("    {\n");
            sb.append("      \"name\": \"").append(escapeJSON(label)).append("\",\n");
            sb.append("      \"units\": \"").append(Scope.getScaleUnitsText(p.units)).append("\",\n");
            sb.append("      \"color\": \"").append(p.color).append("\",\n");
            sb.append("      \"time\": [");
            for (int x = 0; x < historySize; x++) {
                if (x > 0) {
                    sb.append(", ");
                }
                double time = x * historySampleInterval;
                sb.append(time);
            }
            sb.append("],\n");

            VariableHistoryStore.SeriesSnapshot historySnapshot = scope.getHistorySnapshotForRender(p);
            if (historySnapshot != null) {
                sb.append("      \"values\": [");
                for (int x = 0; x < historySize; x++) {
                    if (x > 0) {
                        sb.append(", ");
                    }
                    double midpoint = (historySnapshot.minValues[x] + historySnapshot.maxValues[x]) / 2.0;
                    sb.append(midpoint);
                }
                sb.append("],\n");

                sb.append("      \"minValues\": [");
                for (int x = 0; x < historySize; x++) {
                    if (x > 0) {
                        sb.append(", ");
                    }
                    sb.append(historySnapshot.minValues[x]);
                }
                sb.append("],\n");

                sb.append("      \"maxValues\": [");
                for (int x = 0; x < historySize; x++) {
                    if (x > 0) {
                        sb.append(", ");
                    }
                    sb.append(historySnapshot.maxValues[x]);
                }
                sb.append("]\n");
            } else {
                sb.append("      \"values\": [],\n");
                sb.append("      \"minValues\": [],\n");
                sb.append("      \"maxValues\": []\n");
            }

            sb.append("    }");
            if (i < visiblePlots.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static int getCircularBufferDisplayWidth(
            Vector<ScopePlot> visiblePlots,
            Rectangle rect,
            int scopePointCount) {
        int width = rect.width;
        if (width > scopePointCount) {
            width = scopePointCount;
        }
        if (visiblePlots.size() > 0) {
            width = visiblePlots.get(0).getDisplayWidth(width);
        }
        return width;
    }

    private static String getPlotLabel(ScopePlot p, int index) {
        String label = p.elm.getScopeTextForScope(p.value);
        if (label == null || label.isEmpty()) {
            label = "Plot " + (index + 1);
        }
        return label;
    }

    private static String escapeJSON(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
