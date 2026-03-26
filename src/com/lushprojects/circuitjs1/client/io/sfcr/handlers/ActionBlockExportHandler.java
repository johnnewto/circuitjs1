package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.elements.ActionScheduler;
import com.lushprojects.circuitjs1.client.elements.misc.ActionTimeElm;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

public class ActionBlockExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.ACTION;
    }

    @Override
    public int exportOrder() {
        return 20;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        return exportBlock(ctx);
    }

    public String exportBlock(SFCRExportContext ctx) {
        ActionScheduler scheduler = ActionScheduler.getInstance(ctx.getSim());
        ActionTimeElm actionTimeElmForExport = ctx.getActionTimeElm();
        if (scheduler == null && actionTimeElmForExport == null) {
            return "";
        }

        java.util.List<ActionScheduler.ScheduledAction> actions =
            (scheduler == null) ? null : scheduler.getAllActions();
        boolean hasActions = actions != null && !actions.isEmpty();
        if (!hasActions && actionTimeElmForExport == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (actionTimeElmForExport != null) {
            String actionName = ctx.sanitizeName(actionTimeElmForExport.title);
            sb.append("@action ")
              .append(actionName)
              .append(ctx.formatPosition(actionTimeElmForExport))
              .append("\n");
        } else {
            sb.append("@action\n");
        }

        double pauseTime = (scheduler == null) ? 0 : scheduler.getPauseTime();
        sb.append("  pauseTime: ").append(pauseTime).append("\n");

        if (actionTimeElmForExport != null) {
            sb.append("  enabled: ").append(actionTimeElmForExport.enabled).append("\n");
            sb.append("  element: ")
              .append(actionTimeElmForExport.x).append(" ")
              .append(actionTimeElmForExport.y).append(" ")
              .append(actionTimeElmForExport.x2).append(" ")
              .append(actionTimeElmForExport.y2).append(" ")
              .append(actionTimeElmForExport.flags)
              .append("\n");
        }

        if (hasActions) {
            sb.append("\n");
            sb.append("| time | target | value | text | enabled | stop |\n");
            sb.append("|------|--------|-------|------|---------|------|\n");

            for (ActionScheduler.ScheduledAction action : actions) {
                String target = (action.sliderName == null) ? "" : action.sliderName;
                String valueExpr = (action.valueExpression == null) ? "" : action.valueExpression.trim();
                String value = valueExpr.isEmpty() ? Double.toString(action.sliderValue) : valueExpr;
                String text = (action.postText == null) ? "" : action.postText;

                sb.append("| ")
                  .append(action.actionTime)
                  .append(" | ")
                  .append(ctx.escapeTableCell(target))
                  .append(" | ")
                  .append(ctx.escapeTableCell(value))
                  .append(" | ")
                  .append(ctx.escapeTableCell(text))
                  .append(" | ")
                  .append(action.enabled)
                  .append(" | ")
                  .append(action.stopSimulation)
                  .append(" |\n");
            }
        }

        sb.append("@end\n");
        return sb.toString();
    }
}
