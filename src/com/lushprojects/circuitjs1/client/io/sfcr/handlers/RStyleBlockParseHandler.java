package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;

import java.util.Vector;

public class RStyleBlockParseHandler {
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx, Vector<String> pendingBlockComments) {
        SFCRParser parser = ctx.getParser();
        SFCRParser.RStyleBlockMetadata metadata = parser.consumeRStyleMetadataFromCommentsForHandler(pendingBlockComments);
        StringBuilder blockText = new StringBuilder();
        int i = startIndex;
        int parenDepth = 0;
        boolean inBlock = false;

        while (i < lines.length) {
            String line = lines[i];
            blockText.append(line).append("\n");

            for (char c : line.toCharArray()) {
                if (c == '(') {
                    parenDepth++;
                    inBlock = true;
                }
                if (c == ')') {
                    parenDepth--;
                }
            }

            i++;
            if (inBlock && parenDepth == 0) {
                break;
            }
        }

        String block = blockText.toString();
        String blockName = parser.extractRStyleAssignmentNameForHandler(block, "Equations");

        if (block.contains("sfcr_matrix")) {
            blockName = parser.extractRStyleAssignmentNameForHandler(block, "Matrix");
            parser.storePendingMatrixBlockCommentsForHandler(blockName, pendingBlockComments);
            parser.parseRStyleMatrixForHandler(block, metadata);
        } else if (block.contains("sfcr_set")) {
            parser.storePendingEquationsBlockCommentsForHandler(blockName, pendingBlockComments);
            parser.parseRStyleEquationsForHandler(block, metadata);
        } else if (pendingBlockComments != null) {
            pendingBlockComments.clear();
        }

        return ParseResult.next(i);
    }
}
