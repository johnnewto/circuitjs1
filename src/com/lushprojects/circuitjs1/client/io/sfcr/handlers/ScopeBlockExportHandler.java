package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.elements.misc.ScopeElm;
import com.lushprojects.circuitjs1.client.scope.Scope;
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
        return exportBlock(ctx);
    }

    public String exportBlock(SFCRExportContext ctx) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < ctx.getScopeCount(); i++) {
            Scope s = ctx.getScopeAt(i);
            if (!ctx.appendScopeBlock(sb, s, i + 1, "Scope", null)) {
                continue;
            }
        }

        int embeddedIndex = 1;
        for (int i = 0; i < ctx.getElmListSize(); i++) {
            CircuitElm elm = ctx.getElmAt(i);
            if (!(elm instanceof ScopeElm)) {
                continue;
            }
            ScopeElm scopeElm = (ScopeElm) elm;
            if (ctx.appendScopeBlock(sb, scopeElm.elmScope, embeddedIndex++, "Embedded_Scope", scopeElm)) {
                ctx.markScopeElmExportedAsBlock(scopeElm);
            }
        }

        return sb.toString();
    }
}
