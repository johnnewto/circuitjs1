/*
    Copyright (C) Paul Falstad and Iain Sharp

    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.io;


import com.lushprojects.circuitjs1.client.CustomLogicModel;
import com.lushprojects.circuitjs1.client.util.StringTokenizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

/**
 * Pure-Java exporter for {@link SFCRParseResult}.
 *
 * <p>Produces SFCR text that can be parsed again by
 * {@link SFCRParser#parseToResult(String)} without requiring CirSim/GWT.
 */
public class SFCRParseResultExporter {
    private static final int DEFAULT_PLANTUML_WIDTH = 560;


    /** Export parse result to SFCR text using a compact @circuit dump representation. */
    public static String export(SFCRParseResult result) {
        if (result == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# CircuitJS1 SFCR Export (result-mode)\n");
        sb.append("# Generated from SFCRParseResult\n\n");

        appendInitBlock(sb, result.initSettings);
        appendHintsBlock(sb, result.hints);
        appendStructuralBlocksPreservingOrder(sb, result.blockDumps);

        return sb.toString().trim() + "\n";
    }

    private static void appendInitBlock(StringBuilder sb, Map<String, String> initSettings) {
        if (initSettings == null || initSettings.isEmpty()) {
            return;
        }

        sb.append("@init\n");
        ArrayList<String> keys = new ArrayList<String>(initSettings.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            String value = initSettings.get(key);
            if (value == null) {
                value = "";
            }
            sb.append("  ").append(key).append(": ").append(value).append("\n");
        }
        sb.append("@end\n\n");
    }

    private static void appendHintsBlock(StringBuilder sb, Map<String, String> hints) {
        if (hints == null || hints.isEmpty()) {
            return;
        }

        sb.append("@hints\n");
        ArrayList<String> keys = new ArrayList<String>(hints.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            String value = hints.get(key);
            if (value == null) {
                value = "";
            }
            sb.append("  ").append(key).append(": ").append(value).append("\n");
        }
        sb.append("@end\n\n");
    }

    private static void appendCircuitBlock(StringBuilder sb, java.util.List<SFCRParseResult.BlockDump> blocks) {
        sb.append("@circuit\n");
        for (SFCRParseResult.BlockDump block : blocks) {
            if (block == null || block.dumpString == null || block.dumpString.trim().isEmpty()) {
                continue;
            }
            String blockType = (block.blockType == null) ? "" : block.blockType.trim();
            String blockName = (block.blockName == null) ? "" : block.blockName.trim();
            if (!blockType.isEmpty()) {
                sb.append("#@sfcrblock ").append(blockType);
                if (!blockName.isEmpty()) {
                    sb.append(" ").append(blockName);
                }
                sb.append("\n");
            }
            sb.append(block.dumpString.trim()).append("\n");
        }
        sb.append("@end\n");
    }

    private static void appendStructuralBlocksPreservingOrder(StringBuilder sb, java.util.List<SFCRParseResult.BlockDump> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        ArrayList<SFCRParseResult.BlockDump> pendingCircuitBlocks = new ArrayList<SFCRParseResult.BlockDump>();
        for (SFCRParseResult.BlockDump block : blocks) {
            if (isActionBlock(block)) {
                flushCircuitBlocks(sb, pendingCircuitBlocks);
                appendActionBlock(sb, block);
            } else if (isPlantUmlBlock(block)) {
                flushCircuitBlocks(sb, pendingCircuitBlocks);
                appendPlantUmlBlock(sb, block);
            } else {
                pendingCircuitBlocks.add(block);
            }
        }
        flushCircuitBlocks(sb, pendingCircuitBlocks);
    }

    private static void flushCircuitBlocks(StringBuilder sb, ArrayList<SFCRParseResult.BlockDump> pendingCircuitBlocks) {
        if (pendingCircuitBlocks.isEmpty()) {
            return;
        }
        appendCircuitBlock(sb, pendingCircuitBlocks);
        sb.append("\n");
        pendingCircuitBlocks.clear();
    }

    private static boolean isPlantUmlBlock(SFCRParseResult.BlockDump block) {
        if (block == null) {
            return false;
        }
        if ("plantuml".equals(block.blockType)) {
            return true;
        }
        return block.dumpString != null && block.dumpString.trim().startsWith("467 ");
    }

    private static boolean isActionBlock(SFCRParseResult.BlockDump block) {
        if (block == null) {
            return false;
        }
        if ("action".equals(block.blockType)) {
            return true;
        }
        return block.dumpString != null && block.dumpString.trim().startsWith("@action");
    }

    private static void appendActionBlock(StringBuilder sb, SFCRParseResult.BlockDump block) {
        if (block == null || block.dumpString == null || block.dumpString.trim().isEmpty()) {
            return;
        }
        sb.append(block.dumpString.trim()).append("\n\n");
    }

    private static void appendPlantUmlBlock(StringBuilder sb, SFCRParseResult.BlockDump block) {
        DecodedPlantUmlDump decoded = decodePlantUmlDump(block);
        if (decoded == null) {
            ArrayList<SFCRParseResult.BlockDump> fallback = new ArrayList<SFCRParseResult.BlockDump>();
            fallback.add(block);
            appendCircuitBlock(sb, fallback);
            sb.append("\n");
            return;
        }

        sb.append("```{r}\n");
        sb.append(rewriteStartUml(decoded));
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append("\n");
        }
        sb.append("```\n");
        sb.append("\n");
    }

    private static String rewriteStartUml(DecodedPlantUmlDump decoded) {
        String source = (decoded.source == null || decoded.source.isEmpty()) ? "@startuml\n@end" : decoded.source;
        String[] lines = source.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        boolean hasEndDirective = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            // Skip any closing ``` backticks that may have been included in the source
            if (trimmed.startsWith("```")) {
                continue;
            }
            if ("@end".equals(trimmed) || "@enduml".equals(trimmed)) {
                hasEndDirective = true;
            }
            if (!replaced && trimmed.startsWith("@startuml")) {
                out.append("@startuml x=").append(decoded.x)
                    .append(" y=").append(decoded.y)
                    .append(" w=").append(decoded.frameWidth)
                    .append(" h=").append(decoded.frameHeight);
                if (decoded.width != DEFAULT_PLANTUML_WIDTH) {
                    out.append(" width=").append(decoded.width);
                }
                if (Math.abs(decoded.scale - 1.0) > 1e-9) {
                    out.append(" scale=").append(decoded.scale);
                }
                replaced = true;
            } else {
                out.append(line);
            }
            if (i < lines.length - 1) {
                out.append("\n");
            }
        }
        if (!hasEndDirective) {
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                out.append("\n");
            }
            out.append("@end");
        }
        return out.toString();
    }

    private static DecodedPlantUmlDump decodePlantUmlDump(SFCRParseResult.BlockDump block) {
        if (block == null || block.dumpString == null) {
            return null;
        }
        try {
            StringTokenizer st = new StringTokenizer(block.dumpString);
            st.nextToken();
            DecodedPlantUmlDump decoded = new DecodedPlantUmlDump();
            decoded.x = Integer.parseInt(st.nextToken());
            decoded.y = Integer.parseInt(st.nextToken());
            int x2 = Integer.parseInt(st.nextToken());
            int y2 = Integer.parseInt(st.nextToken());
            decoded.frameWidth = Math.abs(x2 - decoded.x);
            decoded.frameHeight = Math.abs(y2 - decoded.y);
            st.nextToken();
            decoded.source = st.hasMoreTokens() ? CustomLogicModel.unescape(st.nextToken()) : "";
            decoded.width = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 560;
            decoded.scale = st.hasMoreTokens() ? Double.parseDouble(st.nextToken()) : 1.0;
            return decoded;
        } catch (Exception e) {
            return null;
        }
    }

    private static class DecodedPlantUmlDump {
        int x;
        int y;
        int width;
        int frameWidth;
        int frameHeight;
        double scale = 1.0;
        String source;
    }
}
