package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

public class HintsBlockExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.HINTS;
    }

    @Override
    public int exportOrder() {
        return 80;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        return ctx.getExporter().exportHintsForHandler();
    }
}
