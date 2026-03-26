package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;

public interface SFCRBlockParseHandler {
    String[] supportedDirectives();

    ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx);
}
