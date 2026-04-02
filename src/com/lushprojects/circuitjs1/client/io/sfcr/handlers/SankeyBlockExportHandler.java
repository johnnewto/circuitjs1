package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.elements.economics.SFCSankeyElm;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

public class SankeyBlockExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.SANKEY;
    }

    @Override
    public int exportOrder() {
        return 70;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        return exportBlocks(ctx);
    }

    public String exportBlocks(SFCRExportContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (SFCSankeyElm sankey : ctx.getSankeyDiagrams()) {
            ctx.appendExportBlock(sb, exportOne(ctx, sankey));
        }
        return sb.toString();
    }

    public String exportOne(SFCRExportContext ctx, SFCSankeyElm sankey) {
        StringBuilder sb = new StringBuilder();

        String sourceName = sankey.getSourceTableName();
        String layout = sankey.getLayoutMode().name().toLowerCase();
        int width = sankey.getWidth();
        int height = sankey.getHeight();

        ctx.appendLeadingBlockComments(sb, "sankey", "");

        sb.append("@sankey");
        sb.append(ctx.formatPosition(sankey)).append("\n");
        sb.append("  uid: ").append(sankey.getPersistentUid()).append("\n");

        if (sourceName != null && !sourceName.isEmpty()) {
            sb.append("  source: ").append(sourceName).append("\n");
        }
        sb.append("  layout: ").append(layout).append("\n");
        sb.append("  width: ").append(width).append("\n");
        sb.append("  height: ").append(height).append("\n");

        sb.append("  showScaleBar: ").append(sankey.getShowScaleBar()).append("\n");
        if (sankey.getFixedMaxScale() > 0) {
            sb.append("  fixedMaxScale: ").append(sankey.getFixedMaxScale()).append("\n");
        }
        sb.append("  useHighWaterMark: ").append(sankey.getUseHighWaterMark()).append("\n");
        sb.append("  showFlowValues: ").append(sankey.getShowFlowValues()).append("\n");

        sb.append("@end\n");
        return sb.toString();
    }
}
