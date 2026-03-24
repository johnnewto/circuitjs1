/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.ui;
import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;

import com.lushprojects.circuitjs1.client.util.Locale;

public final class InfoViewerSimpleMarkdown {

    private InfoViewerSimpleMarkdown() {
    }

    /**
     * Convert simple markdown to HTML without external libraries.
     * Supports basic formatting for fallback display.
     */
    public static String convert(String markdown) {
        if (markdown == null) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        out.append(openDiv("font-family: sans-serif; line-height: 1.6;"));

        String[] lines = markdown.split("\\n");
        boolean inCodeBlock = false;
        boolean inList = false;

        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    out.append(closeTag("pre"));
                    inCodeBlock = false;
                } else {
                    out.append(openPre("background:#f4f4f4; padding:10px; overflow-x:auto;"));
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                out.append(escapeHtml(line)).append("\\n");
                continue;
            }

            if (line.startsWith("### ")) {
                out.append(wrapTag("h3", formatWithGreekAndSubscripts(line.substring(4))));
                continue;
            }
            if (line.startsWith("## ")) {
                out.append(wrapTag("h2", formatWithGreekAndSubscripts(line.substring(3))));
                continue;
            }
            if (line.startsWith("# ")) {
                out.append(wrapTag("h1", formatWithGreekAndSubscripts(line.substring(2))));
                continue;
            }

            if (line.trim().matches("^[=]{3,}$") || line.trim().matches("^[-]{3,}$")) {
                out.append(voidTag("hr"));
                continue;
            }

            if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                if (!inList) {
                    out.append(openTag("ul"));
                    inList = true;
                }
                out.append(wrapTag("li", formatInline(line.trim().substring(2))));
                continue;
            } else if (inList && !line.trim().isEmpty()) {
                out.append(closeTag("ul"));
                inList = false;
            }

            if (line.trim().isEmpty()) {
                out.append(voidTag("br"));
                continue;
            }

            out.append(wrapTag("p", formatInline(line)));
        }

        if (inList) {
            out.append(closeTag("ul"));
        }
        if (inCodeBlock) {
            out.append(closeTag("pre"));
        }

        out.append(closeTag("div"));
        return out.toString();
    }

    private static String openTag(String tag) {
        return "<" + tag + ">";
    }

    private static String closeTag(String tag) {
        return "</" + tag + ">";
    }

    private static String wrapTag(String tag, String content) {
        return openTag(tag) + content + closeTag(tag);
    }

    private static String voidTag(String tag) {
        return "<" + tag + ">";
    }

    private static String openDiv(String style) {
        return "<div style='" + style + "'>";
    }

    private static String openPre(String style) {
        return "<pre style='" + style + "'>";
    }

    private static String formatInline(String text) {
        text = formatWithGreekAndSubscripts(text);

        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        text = text.replaceAll("__(.+?)__", "<strong>$1</strong>");

        text = text.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        text = text.replaceAll("_(.+?)_", "<em>$1</em>");

        text = text.replaceAll("`(.+?)`", "<code style='background:#f4f4f4; padding:2px 4px;'>$1</code>");
        return text;
    }

    private static String formatWithGreekAndSubscripts(String text) {
        if (text == null) {
            return "";
        }
        return Locale.convertToHTML(text);
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
