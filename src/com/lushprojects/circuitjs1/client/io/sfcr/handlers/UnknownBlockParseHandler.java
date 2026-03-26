package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;

public class UnknownBlockParseHandler {
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx) {
        String header = (startIndex >= 0 && startIndex < lines.length) ? lines[startIndex].trim() : "";
        ctx.addWarning(startIndex + 1, "Unknown SFCR directive: " + header);
        int i = startIndex + 1;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.startsWith("@end")) {
                return ParseResult.next(i + 1);
            }
            if (line.startsWith("@")) {
                return ParseResult.next(i);
            }
            i++;
        }
        return ParseResult.next(i);
    }
}
