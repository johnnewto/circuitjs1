package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

public class ScopeBlockExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.SCOPE;
    }

    @Override
    public int exportOrder() {
        return 100;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        return ctx.getExporter().exportScopesForHandler();
    }
}
