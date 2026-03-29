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
            if (isPlantUmlBlock(block)) {
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

    private static void appendPlantUmlBlock(StringBuilder sb, SFCRParseResult.BlockDump block) {
        DecodedPlantUmlDump decoded = decodePlantUmlDump(block);
        if (decoded == null) {
            ArrayList<SFCRParseResult.BlockDump> fallback = new ArrayList<SFCRParseResult.BlockDump>();
            fallback.add(block);
            appendCircuitBlock(sb, fallback);
            sb.append("\n");
            return;
        }

        sb.append("@plantuml");
        if (block.blockName != null && block.blockName.trim().length() > 0) {
            sb.append(" ").append(block.blockName.trim());
        }
        sb.append(" x=").append(decoded.x);
        sb.append(" y=").append(decoded.y).append("\n");
        sb.append("width: ").append(decoded.width).append("\n");
        if (Math.abs(decoded.scale - 1.0) > 1e-9) {
            sb.append("scale: ").append(decoded.scale).append("\n");
        }
        if (decoded.source != null && decoded.source.length() > 0) {
            sb.append(decoded.source);
            if (!decoded.source.endsWith("\n")) {
                sb.append("\n");
            }
        }
        sb.append("@end\n\n");
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
            st.nextToken();
            st.nextToken();
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
        double scale = 1.0;
        String source;
    }
}
