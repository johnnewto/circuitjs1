package com.lushprojects.circuitjs1.client.io;

final class SFCRBlockCommentRegistry {
    public static final String TYPE_EQUATIONS = "equations";
    public static final String TYPE_MATRIX = "matrix";
    public static final String TYPE_SCOPE = "scope";
    public static final String TYPE_SANKEY = "sankey";

    private SFCRBlockCommentRegistry() {
    }

    public static String makeKey(String blockType, String blockName) {
        String type = (blockType == null) ? "" : blockType.trim().toLowerCase();
        String name = normalizeName(blockName);
        return type + "|" + name;
    }

    private static String normalizeName(String blockName) {
        if (blockName == null) {
            return "";
        }
        return blockName.trim().replaceAll("\\s+", "_");
    }
}
