package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;

import java.util.ArrayList;
import java.util.HashMap;

public class EquationsBlockParseHandler implements SFCRBlockParseHandler {
    private final String directive;

    public EquationsBlockParseHandler(String directive) {
        this.directive = directive;
    }

    @Override
    public String[] supportedDirectives() {
        return new String[]{directive};
    }

    @Override
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx) {
        String headerLine = lines[startIndex].trim();
        SFCRParser.BlockHeaderInfo blockPos = ctx.parseBlockHeader(headerLine, directive);
        String blockName = blockPos.name;

        int savedX = ctx.getCurrentX();
        int savedY = ctx.getCurrentY();
        if (blockPos.hasPosition()) {
            ctx.setCurrentPosition(blockPos.x, blockPos.y);
        }

        ArrayList<String> outputNames = new ArrayList<String>();
        ArrayList<String> equations = new ArrayList<String>();
        ArrayList<Integer> outputModes = new ArrayList<Integer>();
        ArrayList<String> targetNodeNames = new ArrayList<String>();
        ArrayList<String> sliderVarNames = new ArrayList<String>();
        ArrayList<Double> sliderValues = new ArrayList<Double>();
        ArrayList<String> initialEquations = new ArrayList<String>();
        Boolean invisible = null;

        int i = startIndex + 1;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.startsWith("@end")) {
                i++;
                break;
            }
            if (line.startsWith("#")) {
                appendCommentRow(line, outputNames, equations, outputModes, targetNodeNames,
                    sliderVarNames, sliderValues, initialEquations);
                i++;
                continue;
            }
            if (line.isEmpty()) {
                i++;
                continue;
            }

            int blockMetaSep = line.indexOf(':');
            if (blockMetaSep > 0) {
                String key = line.substring(0, blockMetaSep).trim().toLowerCase();
                String value = line.substring(blockMetaSep + 1).trim();
                if (key.equals("invisible") || key.equals("hidden")) {
                    invisible = parseBoolean(value);
                    i++;
                    continue;
                }
            }

            String inlineComment = null;
            int commentIdx = line.indexOf('#');
            if (commentIdx > 0) {
                inlineComment = line.substring(commentIdx + 1).trim();
                line = line.substring(0, commentIdx).trim();
            }

            String[] parts = null;
            if (line.contains("~")) {
                parts = line.split("~", 2);
            } else if (line.contains("=")) {
                parts = line.split("=", 2);
            }

            if (parts != null && parts.length == 2) {
                String leftPart = parts[0].trim();
                String rightPart = parts[1].trim();

                String exprText = rightPart;
                HashMap<String, String> rowMeta = new HashMap<String, String>();
                int metaIdx = rightPart.indexOf(';');
                if (metaIdx >= 0) {
                    exprText = rightPart.substring(0, metaIdx).trim();
                    String metaText = rightPart.substring(metaIdx + 1).trim();
                    String[] metaParts = metaText.split(";");
                    for (int m = 0; m < metaParts.length; m++) {
                        String token = metaParts[m].trim();
                        int eq = token.indexOf('=');
                        if (eq > 0) {
                            String key = token.substring(0, eq).trim().toLowerCase();
                            String val = token.substring(eq + 1).trim();
                            rowMeta.put(key, val);
                        }
                    }
                }

                String[] lhsAliasParts = ctx.splitDifferenceLeftAlias(leftPart);
                boolean hasDifferenceAlias = lhsAliasParts[1] != null && !lhsAliasParts[1].isEmpty();

                String[] nameParts = SFCRParser.parseCombinedNameLocal(lhsAliasParts[0]);
                String name = ctx.normalizeVariableName(nameParts[0]);

                String targetName = "";
                if (nameParts[1] != null && !nameParts[1].trim().isEmpty()) {
                    targetName = ctx.normalizeVariableName(nameParts[1].trim());
                }

                String expr = ctx.normalizeExpression(exprText);
                expr = ctx.rewriteLookupCalls(expr, blockName);

                outputNames.add(name);
                if (hasDifferenceAlias) {
                    String lhsDisplay = lhsAliasParts[0] + " - " + lhsAliasParts[1];
                    equations.add(lhsDisplay + " = " + expr);
                } else {
                    equations.add(expr);
                }

                int mode = SFCRParser.parseModeOrdinal(rowMeta.get("mode"));
                if (mode == 0 && !targetName.isEmpty()) {
                    mode = 1;
                }
                outputModes.add(mode);

                if (targetName.isEmpty()) {
                    String metaTarget = rowMeta.get("target");
                    if (metaTarget != null && !metaTarget.trim().isEmpty()) {
                        targetName = ctx.normalizeVariableName(metaTarget.trim());
                    }
                }
                targetNodeNames.add(targetName);

                String sliderVar = rowMeta.get("slider");
                sliderVarNames.add(sliderVar == null ? "" : sliderVar.trim());

                double sliderValue = 0.0;
                String sliderValueStr = rowMeta.get("slidervalue");
                if (sliderValueStr != null) {
                    try {
                        sliderValue = Double.parseDouble(sliderValueStr);
                    } catch (Exception e) {
                    }
                }
                sliderValues.add(Double.valueOf(sliderValue));

                String initEq = rowMeta.get("initial");
                if (initEq != null && !initEq.trim().isEmpty()) {
                    initEq = ctx.rewriteLookupCalls(initEq, blockName);
                }
                initialEquations.add((initEq != null) ? initEq : "");

                if (inlineComment != null && !inlineComment.isEmpty() && !ctx.hasHint(name)) {
                    ctx.registerHint(name, inlineComment);
                }
            }

            i++;
        }

        if (!outputNames.isEmpty()) {
            ctx.createEquationTable(blockName, outputNames, equations, outputModes,
                targetNodeNames, sliderVarNames, sliderValues, initialEquations, invisible);
        }

        if (blockPos.hasPosition()) {
            ctx.setCurrentPosition(savedX, savedY);
        }

        return ParseResult.next(i);
    }

    private void appendCommentRow(String comment,
                                  ArrayList<String> outputNames,
                                  ArrayList<String> equations,
                                  ArrayList<Integer> outputModes,
                                  ArrayList<String> targetNodeNames,
                                  ArrayList<String> sliderVarNames,
                                  ArrayList<Double> sliderValues,
                                  ArrayList<String> initialEquations) {
        if (comment == null) return;
        String text = comment.trim();
        if (text.startsWith("#")) {
            text = text.substring(1).trim();
        }
        if (text.isEmpty()) return;

        outputNames.add("# " + text);
        equations.add("");
        outputModes.add(3);
        targetNodeNames.add("");
        sliderVarNames.add("");
        sliderValues.add(Double.valueOf(0));
        initialEquations.add("");
    }

    private Boolean parseBoolean(String value) {
        return Boolean.valueOf("true".equalsIgnoreCase(value) || "1".equals(value)
            || "yes".equalsIgnoreCase(value));
    }
}
