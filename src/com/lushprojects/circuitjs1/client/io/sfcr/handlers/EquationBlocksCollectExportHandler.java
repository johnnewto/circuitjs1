package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

public class EquationBlocksCollectExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.EQUATIONS;
    }

    @Override
    public int exportOrder() {
        return 30;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        ctx.setEquationBlocks(ctx.getExporter().buildEquationBlocksForHandler());
        return "";
    }
}
