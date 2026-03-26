package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

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
        return ctx.getExporter().exportLookupBlocksForHandler();
    }
}
