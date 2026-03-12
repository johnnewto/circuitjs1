package com.lushprojects.circuitjs1.client;

class SFCRBlockCommentRegistry {
    static final String TYPE_EQUATIONS = "equations";
    static final String TYPE_MATRIX = "matrix";
    static final String TYPE_SCOPE = "scope";
    static final String TYPE_SANKEY = "sankey";

    static String makeKey(String blockType, String blockName) {
        String type = (blockType == null) ? "" : blockType.trim().toLowerCase();
        String name = normalizeName(blockName);
        return type + "|" + name;
    }

    static String normalizeName(String blockName) {
        if (blockName == null) {
            return "";
        }
        return blockName.trim().replaceAll("\\s+", "_");
    }
}