package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;

public class InitBlockParseHandler implements SFCRBlockParseHandler {
    @Override
    public String[] supportedDirectives() {
        return new String[]{"@init"};
    }

    @Override
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx) {
        SFCRParser parser = ctx.getParser();
        String headerLine = lines[startIndex].trim();

        String inlineParams = headerLine.substring(5).trim();
        if (!inlineParams.isEmpty()) {
            parser.parseInitInlineForHandler(inlineParams);
            return ParseResult.next(startIndex + 1);
        }

        int i = startIndex + 1;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.startsWith("@end") || (line.startsWith("@") && !line.startsWith("@end"))) {
                break;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }
            int colonIdx = line.indexOf(':');
            int eqIdx = line.indexOf('=');
            int sepIdx = colonIdx >= 0 ? colonIdx : eqIdx;
            if (sepIdx > 0) {
                String key = line.substring(0, sepIdx).trim();
                String value = line.substring(sepIdx + 1).trim();
                int commentIdx = value.indexOf('#');
                if (commentIdx >= 0) {
                    value = value.substring(0, commentIdx).trim();
                }
                parser.registerInitSettingForHandler(key, value);
            }
            i++;
        }
        if (i < lines.length && lines[i].trim().startsWith("@end")) {
            i++;
        }
        return ParseResult.next(i);
    }
}
