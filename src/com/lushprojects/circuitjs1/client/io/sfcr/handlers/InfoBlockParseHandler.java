package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;

public class InfoBlockParseHandler implements SFCRBlockParseHandler {
    @Override
    public String[] supportedDirectives() {
        return new String[]{"@info"};
    }

    @Override
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx) {
        String headerLine = lines[startIndex].trim();
        String title = headerLine.length() > 5 ? headerLine.substring(5).trim() : "";

        StringBuilder content = new StringBuilder();
        int i = startIndex + 1;

        boolean hasMarkdownHeader = false;
        if (i < lines.length) {
            String firstLine = lines[i].trim();
            if (firstLine.startsWith("#") && !firstLine.equals("@end") && !firstLine.startsWith("@")) {
                hasMarkdownHeader = true;
            }
        }
        if (!hasMarkdownHeader && !title.isEmpty()) {
            content.append("# ").append(title).append("\n\n");
        }

        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.equals("@end") || (trimmed.startsWith("@") && !trimmed.equals("@end"))) {
                if (trimmed.equals("@end")) {
                    i++;
                }
                break;
            }
            content.append(line).append("\n");
            i++;
        }

        ctx.setInfoContent(content.toString());
        return ParseResult.next(i);
    }
}
