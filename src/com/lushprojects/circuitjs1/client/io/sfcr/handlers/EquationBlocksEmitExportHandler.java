package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

import java.util.List;

public class EquationBlocksEmitExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.EQUATIONS;
    }

    @Override
    public int exportOrder() {
        return 40;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        List<String> blocks = ctx.getEquationBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            String b = blocks.get(i);
            if (b == null || b.trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(b.trim()).append("\n");
        }
        return sb.toString();
    }
}
