package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.GodlyTableElm;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

import java.util.ArrayList;

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
        ArrayList<String> equationBlocks = new ArrayList<String>();
        for (EquationTableElm eqTable : ctx.getEquationTables()) {
            String block = ctx.exportEquationTable(eqTable);
            if (block != null && !block.trim().isEmpty()) {
                equationBlocks.add(block);
            }
        }
        for (GodlyTableElm godlyTable : ctx.getGodlyTables()) {
            String block = ctx.exportGodlyTable(godlyTable);
            if (block != null && !block.trim().isEmpty()) {
                equationBlocks.add(block);
            }
        }
        ctx.setEquationBlocks(equationBlocks);
        return "";
    }
}
