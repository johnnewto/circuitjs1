package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;

import java.util.ArrayList;

public class MatrixBlockParseHandler implements SFCRBlockParseHandler {
    @Override
    public String[] supportedDirectives() {
        return new String[]{"@matrix"};
    }

    @Override
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx) {
        String headerLine = lines[startIndex].trim();
        SFCRParser.BlockHeaderInfo blockPos = ctx.parseBlockHeader(headerLine, "@matrix");
        String matrixName = blockPos.name;

        int savedX = ctx.getCurrentX();
        int savedY = ctx.getCurrentY();
        if (blockPos.hasPosition()) {
            ctx.setCurrentPosition(blockPos.x, blockPos.y);
        }

        ArrayList<String> columnNames = new ArrayList<String>();
        String matrixType = "transaction_flow";
        Boolean showInitialValues = null;
        Boolean showFlowValues = null;
        Boolean useBackwardEuler = null;

        int i = startIndex + 1;
        ArrayList<String[]> tableRows = new ArrayList<String[]>();
        ArrayList<String> rowNames = new ArrayList<String>();

        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.startsWith("@end")) {
                i++;
                break;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }

            int colonIdx = line.indexOf(':');
            if (colonIdx > 0 && !line.startsWith("|")) {
                String key = line.substring(0, colonIdx).trim().toLowerCase();
                String value = line.substring(colonIdx + 1).trim();

                if (key.equals("columns")) {
                    columnNames = parseCommaSeparatedList(value);
                    i++;
                    continue;
                }
                if (key.equals("type")) {
                    matrixType = value;
                    i++;
                    continue;
                }
                if (key.equals("showflowvalues") || key.equals("show_flow_values")) {
                    showFlowValues = parseBoolean(value);
                    i++;
                    continue;
                }
                if (key.equals("showinitialvalues") || key.equals("show_initial_values")) {
                    showInitialValues = parseBoolean(value);
                    i++;
                    continue;
                }
                if (key.equals("integration")) {
                    String mode = value.toLowerCase();
                    useBackwardEuler = Boolean.valueOf(mode.equals("backward_euler")
                        || mode.equals("backward euler") || mode.equals("backwardeuler"));
                    i++;
                    continue;
                }
                if (key.equals("usebackwardeuler") || key.equals("use_backward_euler")) {
                    useBackwardEuler = parseBoolean(value);
                    i++;
                    continue;
                }
            }

            if (line.startsWith("|")) {
                if (line.contains("---")) {
                    i++;
                    continue;
                }
                String[] cells = ctx.parseTableRow(line);
                if (cells.length > 1) {
                    if (columnNames.isEmpty()) {
                        for (int j = 1; j < cells.length; j++) {
                            columnNames.add(cells[j]);
                        }
                    } else {
                        rowNames.add(cells[0]);
                        String[] rowData = new String[columnNames.size()];
                        for (int j = 0; j < columnNames.size(); j++) {
                            rowData[j] = (j + 1 < cells.length) ? cells[j + 1] : "";
                        }
                        tableRows.add(rowData);
                    }
                }
            }

            i++;
        }

        if (!columnNames.isEmpty() && !tableRows.isEmpty()) {
            ctx.createMatrixTable(matrixName, columnNames, rowNames, tableRows, matrixType,
                showInitialValues, showFlowValues, useBackwardEuler);
        }

        if (blockPos.hasPosition()) {
            ctx.setCurrentPosition(savedX, savedY);
        }
        return ParseResult.next(i);
    }

    private ArrayList<String> parseCommaSeparatedList(String text) {
        ArrayList<String> result = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        String[] parts = text.split(",");
        for (int i = 0; i < parts.length; i++) {
            String trimmed = parts[i].trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private Boolean parseBoolean(String value) {
        return Boolean.valueOf("true".equalsIgnoreCase(value) || "1".equals(value)
            || "yes".equalsIgnoreCase(value));
    }
}
