package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.elements.ActionScheduler;
import com.lushprojects.circuitjs1.client.elements.misc.ActionTimeElm;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;

public class ActionBlockParseHandler implements SFCRBlockParseHandler {
    @Override
    public String[] supportedDirectives() {
        return new String[]{"@action"};
    }

    @Override
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx) {
        SFCRParser.BlockHeaderInfo actionBlockPos = ctx.parseBlockHeader(lines[startIndex].trim(), "@action");
        int endIndex = findBlockEnd(lines, startIndex);
        if (ctx.hasPendingResult()) {
            String blockName = (actionBlockPos == null || actionBlockPos.name == null || actionBlockPos.name.isEmpty())
                    ? "action"
                    : actionBlockPos.name;
            ctx.addBlockDump("action", blockName, collectSourceBlock(lines, startIndex, endIndex));
            return ParseResult.next(endIndex);
        }

        ActionScheduler scheduler = ctx.getActionScheduler();
        if (scheduler == null) {
            return ParseResult.next(endIndex);
        }

        scheduler.clearAll();

        boolean hasAnyActionRows = false;
        boolean actionElmEnabled = true;
        boolean actionElmEnabledSpecified = false;
        boolean actionElmSpecified = false;
        int actionElmX1 = 704;
        int actionElmY1 = 416;
        int actionElmX2 = 720;
        int actionElmY2 = 432;
        int actionElmFlags = 0;
        String actionElmTitle = "Action Schedule";

        if (actionBlockPos != null && actionBlockPos.name != null && !actionBlockPos.name.isEmpty()
                && !actionBlockPos.name.equalsIgnoreCase("action")) {
            actionElmTitle = actionBlockPos.name.replace('_', ' ');
        }
        if (actionBlockPos != null && actionBlockPos.hasPosition()) {
            actionElmX1 = actionBlockPos.x;
            actionElmY1 = actionBlockPos.y;
            actionElmX2 = actionElmX1 + 16;
            actionElmY2 = actionElmY1 + 16;
            actionElmSpecified = true;
        }

        int i = startIndex + 1;
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
                try {
                    if (key.equals("pausetime") || key.equals("pause_time")) {
                        scheduler.setPauseTime(Double.parseDouble(value));
                    } else if (key.equals("enabled") || key.equals("actionelementenabled") || key.equals("action_element_enabled")) {
                        actionElmEnabled = ctx.parseBoolean(value, true);
                        actionElmEnabledSpecified = true;
                    } else if (key.equals("name") || key.equals("title")) {
                        if (value != null && value.trim().length() > 0) {
                            actionElmTitle = value.trim().replace('_', ' ');
                        }
                    } else if (key.equals("element") || key.equals("actionelement") || key.equals("action_element")) {
                        String[] parts = value.trim().split("\\s+");
                        if (parts.length >= 4) {
                            actionElmX1 = Integer.parseInt(parts[0]);
                            actionElmY1 = Integer.parseInt(parts[1]);
                            actionElmX2 = Integer.parseInt(parts[2]);
                            actionElmY2 = Integer.parseInt(parts[3]);
                            if (parts.length >= 5) {
                                actionElmFlags = Integer.parseInt(parts[4]);
                            }
                            actionElmSpecified = true;
                        }
                    }
                } catch (Exception e) {
                    CirSim.console("SFCRParser: Invalid @action setting " + key + "=" + value);
                }
                i++;
                continue;
            }

            if (line.startsWith("|")) {
                if (line.contains("---") || line.toLowerCase().contains("time") && line.toLowerCase().contains("target")) {
                    i++;
                    continue;
                }
                String[] cells = ctx.parseTableRow(line);
                if (cells.length >= 6) {
                    try {
                        double actionTime = Double.parseDouble(cells[0].trim());
                        String target = ctx.unescapeTableCell(cells[1]);
                        String valueSpec = ctx.unescapeTableCell(cells[2]);
                        String postText = ctx.unescapeTableCell(cells[3]);
                        boolean enabled = ctx.parseBoolean(cells[4], true);
                        boolean stop = ctx.parseBoolean(cells[5], false);

                        double numericValue = 0.0;
                        String expression = "";
                        String trimmedValue = valueSpec == null ? "" : valueSpec.trim();
                        if (!trimmedValue.isEmpty()) {
                            char lead = trimmedValue.charAt(0);
                            if (lead == '+' || lead == '-' || lead == '*' || lead == '=') {
                                expression = trimmedValue;
                            } else {
                                numericValue = Double.parseDouble(trimmedValue);
                            }
                        }

                        ActionScheduler.ScheduledAction action =
                                new ActionScheduler.ScheduledAction(0, actionTime, target,
                                        numericValue, "", postText, enabled, stop);
                        action.valueExpression = expression;
                        scheduler.addAction(action);
                        hasAnyActionRows = true;
                    } catch (Exception e) {
                        CirSim.console("SFCRParser: Invalid @action row: " + line);
                    }
                }
            }
            i++;
        }

        if (hasAnyActionRows || actionElmEnabledSpecified || actionElmSpecified) {
            ActionTimeElm actionElm = ctx.findActionTimeElm();
            CirSim sim = ctx.getSim();
            if (actionElm == null) {
                actionElm = new ActionTimeElm(actionElmX1, actionElmY1, actionElmX2, actionElmY2, actionElmFlags, null);
                actionElm.setPointsForImportExport();
                sim.getImportExportHelper().assignPersistentUid(actionElm, null);
                sim.elmList.addElement(actionElm);
                ctx.addCreatedElement(actionElm);
            } else if (actionElmSpecified) {
                actionElm.x = actionElmX1;
                actionElm.y = actionElmY1;
                actionElm.x2 = actionElmX2;
                actionElm.y2 = actionElmY2;
                actionElm.flags = actionElmFlags;
                actionElm.setPointsForImportExport();
            }

            actionElm.title = actionElmTitle;
            if (actionElmEnabledSpecified || actionElmSpecified || hasAnyActionRows) {
                actionElm.enabled = actionElmEnabled;
            }
            ctx.setActionElementFromActionBlock(true);
        }
        return ParseResult.next(i);
    }

    private int findBlockEnd(String[] lines, int startIndex) {
        int i = startIndex + 1;
        while (i < lines.length) {
            if (lines[i].trim().startsWith("@end")) {
                return i + 1;
            }
            i++;
        }
        return lines.length;
    }

    private String collectSourceBlock(String[] lines, int startIndex, int endIndex) {
        StringBuilder block = new StringBuilder();
        for (int i = startIndex; i < endIndex && i < lines.length; i++) {
            if (block.length() > 0) {
                block.append("\n");
            }
            block.append(lines[i]);
        }
        return block.toString();
    }
}
