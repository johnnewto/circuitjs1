package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;

public class CircuitBlockParseHandler implements SFCRBlockParseHandler {
    @Override
    public String[] supportedDirectives() {
        return new String[]{"@circuit"};
    }

    @Override
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx) {
        int i = startIndex + 1;
        String hintedBlockType = "";
        String hintedBlockName = "";

        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.equals("@end") || line.startsWith("@")) {
                if (line.equals("@end")) {
                    return ParseResult.next(i + 1);
                }
                return ParseResult.next(i);
            }
            if (line.isEmpty()) {
                i++;
                continue;
            }
            if (line.startsWith("#")) {
                if (ctx.hasPendingResult() && line.startsWith("#@sfcrblock")) {
                    String[] parts = line.split("\\s+", 3);
                    hintedBlockType = (parts.length >= 2) ? parts[1].trim() : "";
                    hintedBlockName = (parts.length >= 3) ? parts[2].trim() : "";
                }
                i++;
                continue;
            }
            if (ctx.isActionElementFromActionBlock() && line.startsWith("432 ")) {
                i++;
                continue;
            }
            if (line.startsWith("lookup ")) {
                String parsedLookupBlockName = ctx.parseLookupDumpLineFromCircuit(line);
                if (parsedLookupBlockName != null) {
                    if (ctx.hasPendingResult()) {
                        String blockName = hintedBlockName;
                        if (blockName == null || blockName.isEmpty()) {
                            blockName = parsedLookupBlockName;
                        }
                        ctx.addBlockDump("lookup", blockName, line);
                    }
                    hintedBlockType = "";
                    hintedBlockName = "";
                    i++;
                    continue;
                }
            }

            ctx.addRawCircuitLine(line);
            if (ctx.hasPendingResult()) {
                String blockType = hintedBlockType;
                String blockName = hintedBlockName;
                if (blockType.isEmpty()) {
                    blockType = ctx.inferBlockTypeFromCircuitDumpLine(line);
                    blockName = "";
                }
                if (blockType != null && !blockType.isEmpty()) {
                    ctx.addBlockDump(blockType, blockName, line);
                }
                hintedBlockType = "";
                hintedBlockName = "";
            }
            i++;
        }

        return ParseResult.next(i);
    }
}
