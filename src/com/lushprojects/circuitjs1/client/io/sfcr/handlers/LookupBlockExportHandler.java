package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.LookupDefinition;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

import java.util.ArrayList;

public class LookupBlockExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.LOOKUP;
    }

    @Override
    public int exportOrder() {
        return 50;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        return exportBlock(ctx);
    }

    public String exportBlock(SFCRExportContext ctx) {
        ArrayList<LookupDefinition> specs = ctx.getLookupExportSpecs();
        if (specs.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (LookupDefinition spec : specs) {
            sb.append("@lookup ").append(spec.name);
            if (spec.scope != null && !spec.scope.isEmpty()) {
                sb.append(" scope=").append(spec.scope);
            }
            sb.append("\n");
            for (int c = 0; c < spec.comments.size(); c++) {
                String comment = spec.comments.get(c);
                if (comment == null) {
                    continue;
                }
                String trimmed = comment.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (!trimmed.startsWith("#")) {
                    trimmed = "# " + trimmed;
                }
                sb.append("  ").append(trimmed).append("\n");
            }
            for (int i = 0; i < spec.xs.size(); i++) {
                sb.append("  ")
                  .append(spec.xs.get(i).doubleValue())
                  .append(", ")
                  .append(spec.ys.get(i).doubleValue())
                  .append("\n");
            }
            sb.append("@end\n\n");
        }
        return sb.toString().trim();
    }
}
