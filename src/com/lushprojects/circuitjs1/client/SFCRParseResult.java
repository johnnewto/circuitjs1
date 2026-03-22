/*
    Copyright (C) Paul Falstad and Iain Sharp

    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain-Java result object produced by {@link SFCRParser#parseToResult(String)}.
 *
 * <p>Contains the parsed content of an SFCR document as raw strings and maps,
 * with no references to GWT, CirSim, or any CircuitElm subclass.  Safe to
 * instantiate and inspect from plain-Java unit tests running on a standard JVM.
 *
 * <p>Each element block (equations, matrix) is captured as a
 * {@link BlockDump} whose {@code dumpString} is the CircuitJS serialization
 * format that would normally be fed to {@code ElementFactoryFacade.createFromDumpType(...)}.
 */
public class SFCRParseResult {

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /** A single element block captured in dump-string form. */
    public static class BlockDump {
        /** Block type: {@code "equations"}, {@code "matrix"}, {@code "sankey"}, {@code "action"}. */
        public final String blockType;
        /** Block name as it appeared in the SFCR source (spaces replaced by underscores). */
        public final String blockName;
        /** CircuitJS element serialization string, ready for {@code ElementFactoryFacade.createFromDumpType(...)}. */
        public final String dumpString;

        public BlockDump(String blockType, String blockName, String dumpString) {
            this.blockType = blockType;
            this.blockName = blockName;
            this.dumpString = dumpString;
        }

        @Override
        public String toString() {
            return "BlockDump[" + blockType + "/" + blockName + "]";
        }
    }

    // -------------------------------------------------------------------------
    // Public fields
    // -------------------------------------------------------------------------

    /** Raw key→value pairs from the {@code @init} block (e.g. {@code "timestep" → "0.02"}). */
    public Map<String, String> initSettings = new HashMap<String, String>();

    /** Ordered list of element dump strings produced by the block parsers. */
    public List<BlockDump> blockDumps = new ArrayList<BlockDump>();

    /**
     * Variable hints extracted from inline {@code # ...} comments in equation blocks
     * and from explicit {@code @hints} sections.
     * Key = variable name; value = hint text.
     */
    public Map<String, String> hints = new HashMap<String, String>();

    /**
     * Leading markdown/comment lines that precede each structural block.
     * Key = composite key produced by {@link SFCRBlockCommentRegistry#makeKey}.
     */
    public Map<String, List<String>> blockComments = new HashMap<String, List<String>>();

    // -------------------------------------------------------------------------
    // Convenience queries
    // -------------------------------------------------------------------------

    /** Return all block dumps whose {@code blockType} equals {@code type}. */
    public List<BlockDump> getBlocksByType(String type) {
        List<BlockDump> result = new ArrayList<BlockDump>();
        for (BlockDump bd : blockDumps) {
            if (type.equals(bd.blockType)) {
                result.add(bd);
            }
        }
        return result;
    }

    /** Return the first block dump with the given type and name, or {@code null}. */
    public BlockDump findBlock(String type, String name) {
        for (BlockDump bd : blockDumps) {
            if (type.equals(bd.blockType) && name.equals(bd.blockName)) {
                return bd;
            }
        }
        return null;
    }
}
