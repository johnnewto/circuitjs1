package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;

public class ZOrderBlockParseHandler implements SFCRBlockParseHandler {
    @Override
    public String[] supportedDirectives() {
        return new String[]{"@zorder"};
    }

    @Override
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx) {
        int i = startIndex + 1;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.equals("@end")) {
                i++;
                break;
            }
            if (line.startsWith("@")) {
                break;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }

            String uid = null;
            Integer zOrder = null;
            String[] parts = line.split("\\s+");
            for (int p = 0; p < parts.length; p++) {
                String part = parts[p];
                String lower = part.toLowerCase();
                if (lower.startsWith("uid:") || lower.startsWith("uid=")) {
                    uid = part.substring(4);
                } else if (lower.startsWith("z:") || lower.startsWith("z=")) {
                    try {
                        zOrder = Integer.valueOf(part.substring(2));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (uid != null && uid.length() > 0 && zOrder != null) {
                ctx.registerZOrder(uid, zOrder.intValue());
            }
            i++;
        }
        return ParseResult.next(i);
    }
}