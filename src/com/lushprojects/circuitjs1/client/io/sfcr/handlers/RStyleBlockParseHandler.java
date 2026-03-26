package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;

import java.util.Vector;

public class RStyleBlockParseHandler {
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx, Vector<String> pendingBlockComments) {
        com.lushprojects.circuitjs1.client.io.SFCRParser.RStyleBlockMetadata metadata =
            ctx.consumeRStyleMetadataFromComments(pendingBlockComments);
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
        String blockName = ctx.extractRStyleAssignmentName(block, "Equations");

        if (block.contains("sfcr_matrix")) {
            blockName = ctx.extractRStyleAssignmentName(block, "Matrix");
            ctx.storePendingMatrixBlockComments(blockName, pendingBlockComments);
            ctx.parseRStyleMatrix(block, metadata);
        } else if (block.contains("sfcr_set")) {
            ctx.storePendingEquationsBlockComments(blockName, pendingBlockComments);
            ctx.parseRStyleEquations(block, metadata);
        } else if (pendingBlockComments != null) {
            pendingBlockComments.clear();
        }

        return ParseResult.next(i);
    }
}
