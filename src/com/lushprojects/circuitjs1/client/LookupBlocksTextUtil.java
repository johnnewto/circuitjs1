package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashMap;

class LookupBlocksTextUtil {

    private static class LookupBlock {
        String key;
        String text;
        int startLine;
        int endLine;
    }

    static String extractLookupBlocks(String source) {
        if (source == null || source.trim().isEmpty()) {
            return "";
        }
        ArrayList<LookupBlock> blocks = parseLookupBlocks(source);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) {
                out.append("\n\n");
            }
            out.append(blocks.get(i).text);
        }
        return out.toString().trim();
    }

    static String mergeLookupBlocks(String source, String lookupBlocks) {
        String base = (source == null) ? "" : source;
        String[] lines = base.split("\\n", -1);
        ArrayList<LookupBlock> sourceBlocks = parseLookupBlocks(base);
        String edited = (lookupBlocks == null) ? "" : lookupBlocks.trim();
        ArrayList<LookupBlock> editedBlocks = parseLookupBlocks(edited);

        HashMap<String, ArrayList<LookupBlock>> editedByKey = new HashMap<String, ArrayList<LookupBlock>>();
        ArrayList<LookupBlock> editedInOrder = new ArrayList<LookupBlock>();
        for (int i = 0; i < editedBlocks.size(); i++) {
            LookupBlock b = editedBlocks.get(i);
            editedInOrder.add(b);
            ArrayList<LookupBlock> byKey = editedByKey.get(b.key);
            if (byKey == null) {
                byKey = new ArrayList<LookupBlock>();
                editedByKey.put(b.key, byKey);
            }
            byKey.add(b);
        }

        HashMap<Integer, LookupBlock> replacementByStartLine = new HashMap<Integer, LookupBlock>();
        for (int i = 0; i < sourceBlocks.size(); i++) {
            LookupBlock src = sourceBlocks.get(i);
            LookupBlock replacement = takeFirstByKey(editedByKey, src.key);
            if (replacement == null && !editedInOrder.isEmpty()) {
                replacement = editedInOrder.remove(0);
            } else if (replacement != null) {
                editedInOrder.remove(replacement);
            }
            if (replacement != null) {
                replacementByStartLine.put(Integer.valueOf(src.startLine), replacement);
            }
        }

        StringBuilder out = new StringBuilder();
        int lineIndex = 0;
        int sourceBlockIndex = 0;
        while (lineIndex < lines.length) {
            if (sourceBlockIndex < sourceBlocks.size() && lineIndex == sourceBlocks.get(sourceBlockIndex).startLine) {
                LookupBlock src = sourceBlocks.get(sourceBlockIndex);
                LookupBlock replacement = replacementByStartLine.get(Integer.valueOf(src.startLine));
                if (replacement != null && replacement.text != null && !replacement.text.trim().isEmpty()) {
                    appendWithTrailingNewline(out, replacement.text);
                }
                lineIndex = src.endLine + 1;
                sourceBlockIndex++;
                continue;
            }
            out.append(lines[lineIndex]).append("\n");
            lineIndex++;
        }

        for (int i = 0; i < editedInOrder.size(); i++) {
            LookupBlock extra = editedInOrder.get(i);
            if (extra.text == null || extra.text.trim().isEmpty()) {
                continue;
            }
            if (out.length() > 0 && !endsWithBlankLine(out)) {
                out.append("\n");
            }
            out.append("\n");
            appendWithTrailingNewline(out, extra.text);
        }

        return out.toString().trim();
    }

    private static LookupBlock takeFirstByKey(HashMap<String, ArrayList<LookupBlock>> byKey, String key) {
        ArrayList<LookupBlock> blocks = byKey.get(key);
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        return blocks.remove(0);
    }

    private static ArrayList<LookupBlock> parseLookupBlocks(String source) {
        ArrayList<LookupBlock> blocks = new ArrayList<LookupBlock>();
        if (source == null || source.trim().isEmpty()) {
            return blocks;
        }

        String[] lines = source.split("\\n", -1);
        int i = 0;
        while (i < lines.length) {
            String trimmed = lines[i].trim();
            if (!trimmed.startsWith("@lookup")) {
                i++;
                continue;
            }

            LookupBlock block = new LookupBlock();
            block.startLine = i;
            block.key = buildLookupIdentity(trimmed);
            StringBuilder text = new StringBuilder();

            while (i < lines.length) {
                String line = lines[i];
                text.append(line);
                String t = line.trim();
                if (t.startsWith("@end")) {
                    break;
                }
                text.append("\n");
                i++;
            }

            block.endLine = Math.min(i, lines.length - 1);
            block.text = text.toString().trim();
            blocks.add(block);
            i++;
        }

        return blocks;
    }

    private static String buildLookupIdentity(String lookupHeaderLine) {
        String header = (lookupHeaderLine == null) ? "" : lookupHeaderLine.trim();
        if (!header.startsWith("@lookup")) {
            return header.toLowerCase();
        }
        String body = header.substring("@lookup".length()).trim();
        if (body.isEmpty()) {
            return "";
        }

        String lookupName = "";
        String scopeName = "";
        String[] tokens = body.split("\\s+");
        for (int t = 0; t < tokens.length; t++) {
            String token = tokens[t].trim();
            if (token.isEmpty()) {
                continue;
            }
            int eq = token.indexOf('=');
            if (eq > 0) {
                String key = token.substring(0, eq).trim().toLowerCase();
                String val = token.substring(eq + 1).trim();
                if ("scope".equals(key) || "local".equals(key) || "equations".equals(key) || "table".equals(key)) {
                    scopeName = SFCRUtil.sanitizeName(SFCRUtil.normalizeVariableName(val));
                }
            } else if (lookupName.isEmpty()) {
                lookupName = SFCRUtil.sanitizeName(SFCRUtil.normalizeVariableName(token));
            }
        }

        return lookupName + "|" + scopeName;
    }

    private static void appendWithTrailingNewline(StringBuilder out, String text) {
        out.append(text);
        if (text.length() == 0 || text.charAt(text.length() - 1) != '\n') {
            out.append("\n");
        }
    }

    private static boolean endsWithBlankLine(StringBuilder sb) {
        int len = sb.length();
        if (len == 0) {
            return true;
        }
        if (len >= 2 && sb.charAt(len - 1) == '\n' && sb.charAt(len - 2) == '\n') {
            return true;
        }
        return false;
    }
}