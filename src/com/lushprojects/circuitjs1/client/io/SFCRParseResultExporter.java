/*
    Copyright (C) Paul Falstad and Iain Sharp

    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.io;


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
        appendCircuitBlock(sb, result.blockDumps);

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
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

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
}
