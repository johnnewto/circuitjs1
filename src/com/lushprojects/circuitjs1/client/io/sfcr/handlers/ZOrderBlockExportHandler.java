package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class ZOrderBlockExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.ZORDER;
    }

    @Override
    public int exportOrder() {
        return 95;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        if (ctx == null || ctx.getSim() == null || ctx.getSim().elmList == null || ctx.getSim().elmList.isEmpty()) {
            return "";
        }

        ArrayList<CircuitElm> ordered = new ArrayList<CircuitElm>();
        for (int i = 0; i < ctx.getSim().elmList.size(); i++) {
            CircuitElm elm = ctx.getSim().elmList.get(i);
            if (elm == null) {
                continue;
            }
            String uid = elm.getPersistentUid();
            if (uid == null || uid.isEmpty()) {
                continue;
            }
            ordered.add(elm);
        }

        if (ordered.isEmpty()) {
            return "";
        }

        Collections.sort(ordered, new Comparator<CircuitElm>() {
            @Override
            public int compare(CircuitElm a, CircuitElm b) {
                if (a.getZOrder() < b.getZOrder()) return -1;
                if (a.getZOrder() > b.getZOrder()) return 1;
                return a.getPersistentUid().compareTo(b.getPersistentUid());
            }
        });

        StringBuilder sb = new StringBuilder();
        sb.append("@zorder\n");
        for (int i = 0; i < ordered.size(); i++) {
            CircuitElm elm = ordered.get(i);
            sb.append("  uid:").append(elm.getPersistentUid())
              .append(" z:").append(elm.getZOrder())
              .append("\n");
        }
        sb.append("@end\n");
        return sb.toString();
    }
}