package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

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
        return ctx.getExporter().exportActionBlockForHandler();
    }
}
