package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

public class InitBlockExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.INIT;
    }

    @Override
    public int exportOrder() {
        return 10;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        return ctx.getExporter().exportInitBlockForHandler();
    }
}
