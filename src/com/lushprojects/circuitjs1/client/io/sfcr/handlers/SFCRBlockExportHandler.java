package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

public interface SFCRBlockExportHandler {
    SFCRBlockType blockType();

    int exportOrder();

    String export(SFCRExportContext ctx);
}
