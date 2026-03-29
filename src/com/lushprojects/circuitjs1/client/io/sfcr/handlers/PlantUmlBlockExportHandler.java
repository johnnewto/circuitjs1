package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramElm;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

public class PlantUmlBlockExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.PLANTUML;
    }

    @Override
    public int exportOrder() {
        return 75;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (SequenceDiagramElm diagram : ctx.getSequenceDiagrams()) {
            ctx.appendExportBlock(sb, exportOne(ctx, diagram));
        }
        return sb.toString();
    }

    public String exportOne(SFCRExportContext ctx, SequenceDiagramElm diagram) {
        StringBuilder sb = new StringBuilder();
        ctx.appendLeadingBlockComments(sb, "plantuml", "");
        sb.append("@plantuml");
        sb.append(ctx.formatPosition(diagram)).append("\n");
        sb.append("width: ").append(diagram.getDiagramWidth()).append("\n");
        if (Math.abs(diagram.getDiagramScale() - 1.0) > 1e-9) {
            sb.append("scale: ").append(diagram.getDiagramScale()).append("\n");
        }

        String source = diagram.getPlantUmlSource();
        if (source != null && !source.isEmpty()) {
            sb.append(source);
            if (!source.endsWith("\n")) {
                sb.append("\n");
            }
        }

        sb.append("@end\n");
        return sb.toString();
    }
}
