package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramElm;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

public class PlantUmlBlockExportHandler implements SFCRBlockExportHandler {
    private static final int DEFAULT_DIAGRAM_WIDTH = 560;

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
        System.out.println("DEBUG PlantUmlBlockExportHandler.export(): result=[[" + sb.toString().replace("\n", "\\n") + "]]");
        return sb.toString();
    }

    public String exportOne(SFCRExportContext ctx, SequenceDiagramElm diagram) {
        StringBuilder sb = new StringBuilder();
        ctx.appendLeadingBlockComments(sb, "plantuml", "");
        sb.append("```{r}\n");
        String rewritten = rewriteStartUml(diagram.getPlantUmlSource(), diagram);
        System.out.println("DEBUG exportOne: rewritten length=" + rewritten.length());
        System.out.println("DEBUG exportOne: rewritten contains ```=" + rewritten.contains("```"));
        System.out.println("DEBUG exportOne: rewritten=[[" + rewritten.replace("\n", "\\n") + "]]");
        sb.append(rewritten);
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append("\n");
        }
        sb.append("```\n");
        System.out.println("DEBUG exportOne: final sb=[[" + sb.toString().replace("\n", "\\n") + "]]");
        return sb.toString();
    }

    private String rewriteStartUml(String source, SequenceDiagramElm diagram) {
        String safeSource = (source == null || source.isEmpty()) ? "@startuml\n@end" : source;
        String[] lines = safeSource.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        boolean hasEndDirective = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            // Skip any closing ``` backticks that may have been included in the source
            if (trimmed.startsWith("```")) {
                continue;
            }
            if ("@end".equals(trimmed) || "@enduml".equals(trimmed)) {
                hasEndDirective = true;
            }
            if (!replaced && trimmed.startsWith("@startuml")) {
                out.append("@startuml")
                    .append(ctxPosition(diagram))
                    .append(" w=").append(diagram.getRenderedDiagramWidth())
                    .append(" h=").append(diagram.getRenderedDiagramHeight());
                if (diagram.getDiagramWidth() != DEFAULT_DIAGRAM_WIDTH) {
                    out.append(" width=").append(diagram.getDiagramWidth());
                }
                if (Math.abs(diagram.getDiagramScale() - 1.0) > 1e-9) {
                    out.append(" scale=").append(diagram.getDiagramScale());
                }
                replaced = true;
            } else {
                out.append(line);
            }
            if (i < lines.length - 1) {
                out.append("\n");
            }
        }

        if (!hasEndDirective) {
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                out.append("\n");
            }
            out.append("@end");
        }

        return out.toString();
    }

    private String ctxPosition(SequenceDiagramElm diagram) {
        return " x=" + diagram.x + " y=" + diagram.y;
    }
}
