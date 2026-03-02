/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.util.Locale;

final class InfoViewerSimpleMarkdown {

    private InfoViewerSimpleMarkdown() {
    }

    /**
     * Convert simple markdown to HTML without external libraries.
     * Supports basic formatting for fallback display.
     */
    static String convert(String markdown) {
        if (markdown == null) {
            return "";
        }

        StringBuilder html = new StringBuilder();
        html.append("<div style='font-family: sans-serif; line-height: 1.6;'>");

        String[] lines = markdown.split("\\n");
        boolean inCodeBlock = false;
        boolean inList = false;

        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    html.append("</pre>");
                    inCodeBlock = false;
                } else {
                    html.append("<pre style='background:#f4f4f4; padding:10px; overflow-x:auto;'>");
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                html.append(escapeHtml(line)).append("\\n");
                continue;
            }

            if (line.startsWith("### ")) {
                html.append("<h3>").append(formatWithGreekAndSubscripts(line.substring(4))).append("</h3>");
                continue;
            }
            if (line.startsWith("## ")) {
                html.append("<h2>").append(formatWithGreekAndSubscripts(line.substring(3))).append("</h2>");
                continue;
            }
            if (line.startsWith("# ")) {
                html.append("<h1>").append(formatWithGreekAndSubscripts(line.substring(2))).append("</h1>");
                continue;
            }

            if (line.trim().matches("^[=]{3,}$") || line.trim().matches("^[-]{3,}$")) {
                html.append("<hr>");
                continue;
            }

            if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(formatInline(line.trim().substring(2))).append("</li>");
                continue;
            } else if (inList && !line.trim().isEmpty()) {
                html.append("</ul>");
                inList = false;
            }

            if (line.trim().isEmpty()) {
                html.append("<br>");
                continue;
            }

            html.append("<p>").append(formatInline(line)).append("</p>");
        }

        if (inList) {
            html.append("</ul>");
        }
        if (inCodeBlock) {
            html.append("</pre>");
        }

        html.append("</div>");
        return html.toString();
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