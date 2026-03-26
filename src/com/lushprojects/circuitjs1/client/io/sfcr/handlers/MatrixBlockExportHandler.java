package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.elements.economics.SFCTableElm;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

public class MatrixBlockExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.MATRIX;
    }

    @Override
    public int exportOrder() {
        return 60;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (SFCTableElm sfcTable : ctx.getSfcTables()) {
            ctx.appendExportBlock(sb, ctx.exportMatrixTable(sfcTable));
        }
        return sb.toString();
    }
}
