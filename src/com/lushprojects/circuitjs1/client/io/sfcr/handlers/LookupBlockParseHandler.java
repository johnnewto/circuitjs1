package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.LookupDefinition;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;

public class LookupBlockParseHandler implements SFCRBlockParseHandler {
    @Override
    public String[] supportedDirectives() {
        return new String[]{"@lookup"};
    }

    @Override
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx) {
        SFCRParser parser = ctx.getParser();
        LookupDefinition table = parser.parseLookupHeaderForHandler(lines[startIndex].trim());
        if (table == null) {
            return ParseResult.next(startIndex + 1);
        }

        int i = startIndex + 1;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.startsWith("@end")) {
                i++;
                break;
            }
            if (line.startsWith("#")) {
                table.comments.add(line);
            } else if (!line.isEmpty() && !line.startsWith("%")) {
                SFCRParser.LookupPoint point = parser.parseLookupPointForHandler(line);
                if (point != null) {
                    table.xs.add(Double.valueOf(point.x));
                    table.ys.add(Double.valueOf(point.y));
                }
            }
            i++;
        }

        if (table.xs.size() < 2) {
            return ParseResult.next(i);
        }
        if (!parser.isStrictlyIncreasingForHandler(table.xs)) {
            return ParseResult.next(i);
        }

        parser.registerLookupTableForHandler(table);
        return ParseResult.next(i);
    }
}
