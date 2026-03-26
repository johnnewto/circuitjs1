package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

public class CircuitBlockExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.CIRCUIT;
    }

    @Override
    public int exportOrder() {
        return 90;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        return ctx.exportCircuitElements();
    }
}
