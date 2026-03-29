package com.lushprojects.circuitjs1.client.io.sfcr;

import com.lushprojects.circuitjs1.client.io.sfcr.handlers.ActionBlockExportHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.CircuitBlockExportHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.EquationBlocksCollectExportHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.EquationBlocksEmitExportHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.HintsBlockExportHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.InitBlockExportHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.LookupBlockExportHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.MatrixBlockExportHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.PlantUmlBlockExportHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.SFCRBlockExportHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.SankeyBlockExportHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.ScopeBlockExportHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class SFCRBlockExportHandlerRegistry {
    private static final List<SFCRBlockExportHandler> ORDERED_HANDLERS;

    static {
        ArrayList<SFCRBlockExportHandler> handlers = new ArrayList<SFCRBlockExportHandler>();
        handlers.add(new InitBlockExportHandler());
        handlers.add(new ActionBlockExportHandler());
        handlers.add(new EquationBlocksCollectExportHandler());
        handlers.add(new LookupBlockExportHandler());
        handlers.add(new EquationBlocksEmitExportHandler());
        handlers.add(new MatrixBlockExportHandler());
        handlers.add(new SankeyBlockExportHandler());
        handlers.add(new PlantUmlBlockExportHandler());
        handlers.add(new HintsBlockExportHandler());
        handlers.add(new CircuitBlockExportHandler());
        handlers.add(new ScopeBlockExportHandler());
        Collections.sort(handlers, new Comparator<SFCRBlockExportHandler>() {
            @Override
            public int compare(SFCRBlockExportHandler a, SFCRBlockExportHandler b) {
                int ao = a.exportOrder();
                int bo = b.exportOrder();
                if (ao < bo) return -1;
                if (ao > bo) return 1;
                return 0;
            }
        });
        ORDERED_HANDLERS = Collections.unmodifiableList(handlers);
    }

    private SFCRBlockExportHandlerRegistry() {
    }

    public static List<SFCRBlockExportHandler> getOrderedHandlers() {
        return ORDERED_HANDLERS;
    }
}
