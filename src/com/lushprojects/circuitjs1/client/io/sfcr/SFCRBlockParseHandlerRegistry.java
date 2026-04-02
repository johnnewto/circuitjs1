package com.lushprojects.circuitjs1.client.io.sfcr;

import com.lushprojects.circuitjs1.client.io.sfcr.handlers.ActionBlockParseHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.CircuitBlockParseHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.EquationsBlockParseHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.HintsBlockParseHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.InfoBlockParseHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.InitBlockParseHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.LookupBlockParseHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.MatrixBlockParseHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.PlantUmlBlockParseHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.SFCRBlockParseHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.ScopeBlockParseHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.SankeyBlockParseHandler;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.ZOrderBlockParseHandler;

import java.util.HashMap;
import java.util.Map;

public final class SFCRBlockParseHandlerRegistry {
    private static final Map<String, SFCRBlockParseHandler> HANDLERS = new HashMap<String, SFCRBlockParseHandler>();

    static {
        register(new InitBlockParseHandler());
        register(new ActionBlockParseHandler());
        register(new MatrixBlockParseHandler());
        register(new EquationsBlockParseHandler("@equations"));
        register(new EquationsBlockParseHandler("@parameters"));
        register(new LookupBlockParseHandler());
        register(new HintsBlockParseHandler());
        register(new ScopeBlockParseHandler());
        register(new ZOrderBlockParseHandler());
        register(new CircuitBlockParseHandler());
        register(new SankeyBlockParseHandler());
        register(new PlantUmlBlockParseHandler());
        register(new InfoBlockParseHandler());
    }

    private SFCRBlockParseHandlerRegistry() {
    }

    private static void register(SFCRBlockParseHandler handler) {
        String[] directives = handler.supportedDirectives();
        for (int i = 0; i < directives.length; i++) {
            HANDLERS.put(directives[i], handler);
        }
    }

    public static SFCRBlockParseHandler getHandler(String directive) {
        return HANDLERS.get(directive);
    }
}
