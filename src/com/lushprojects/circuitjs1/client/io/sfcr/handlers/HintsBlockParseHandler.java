package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;

public class HintsBlockParseHandler implements SFCRBlockParseHandler {
    @Override
    public String[] supportedDirectives() {
        return new String[]{"@hints"};
    }

    @Override
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx) {
        int i = startIndex + 1;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.startsWith("@end")) {
                i++;
                break;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String varName = ctx.normalizeVariableName(line.substring(0, colonIdx).trim());
                String description = line.substring(colonIdx + 1).trim();
                ctx.registerHint(varName, description);
            }
            i++;
        }
        return ParseResult.next(i);
    }
}
