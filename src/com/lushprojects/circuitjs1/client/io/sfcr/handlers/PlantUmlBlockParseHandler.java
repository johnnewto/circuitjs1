package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramElm;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.SFCRUtil;
import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;
import com.lushprojects.circuitjs1.client.registry.ElementFactoryFacade;
import com.lushprojects.circuitjs1.client.util.StringTokenizer;

public class PlantUmlBlockParseHandler implements SFCRBlockParseHandler {
    @Override
    public String[] supportedDirectives() {
        return new String[]{"@startuml"};
    }

    @Override
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx) {
        String headerLine = lines[startIndex].trim();
        SFCRParser.BlockHeaderInfo blockPos = ctx.parseBlockHeader(headerLine, "@startuml");

        int posX = blockPos.hasPosition() ? blockPos.x : ctx.getCurrentX();
        int posY = blockPos.hasPosition() ? blockPos.y : ctx.getCurrentY();
        int width = 560;
        int frameWidth = -1;
        int frameHeight = -1;
        double scale = 1.0;
        boolean sourceStarted = false;
        StringBuilder source = new StringBuilder();

        int i = startIndex + 1;
        while (i < lines.length) {
            String rawLine = lines[i];
            String trimmed = rawLine.trim();
            if ("@end".equals(trimmed)) {
                i++;
                break;
            }

            if (!sourceStarted) {
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    i++;
                    continue;
                }
                int colonIdx = trimmed.indexOf(':');
                if (colonIdx > 0) {
                    String key = trimmed.substring(0, colonIdx).trim().toLowerCase();
                    String value = trimmed.substring(colonIdx + 1).trim();
                    if ("width".equals(key)) {
                        try {
                            width = Integer.parseInt(value);
                        } catch (Exception e) {
                            width = 560;
                        }
                        i++;
                        continue;
                    }
                    if ("framewidth".equals(key)) {
                        try {
                            frameWidth = Integer.parseInt(value);
                        } catch (Exception e) {
                            frameWidth = -1;
                        }
                        i++;
                        continue;
                    }
                    if ("frameheight".equals(key)) {
                        try {
                            frameHeight = Integer.parseInt(value);
                        } catch (Exception e) {
                            frameHeight = -1;
                        }
                        i++;
                        continue;
                    }
                    if ("scale".equals(key)) {
                        try {
                            scale = Math.max(.1, Double.parseDouble(value));
                        } catch (Exception e) {
                            scale = 1.0;
                        }
                        i++;
                        continue;
                    }
                }
            }

            sourceStarted = true;
            if (source.length() > 0) {
                source.append("\n");
            }
            source.append(rawLine);
            i++;
        }

        StartUmlMetadata startUmlMetadata = extractStartUmlMetadata(source.toString());
        if (startUmlMetadata.hasPosition) {
            posX = startUmlMetadata.x;
            posY = startUmlMetadata.y;
        }
        if (startUmlMetadata.hasWidth) {
            width = startUmlMetadata.width;
        }
        if (startUmlMetadata.hasScale) {
            scale = startUmlMetadata.scale;
        }
        if (startUmlMetadata.hasFrameWidth) {
            frameWidth = startUmlMetadata.frameWidth;
        }
        if (startUmlMetadata.hasFrameHeight) {
            frameHeight = startUmlMetadata.frameHeight;
        }
        source = new StringBuilder(startUmlMetadata.sanitizedSource);

        String blockName = blockPos.name;
        if (blockName == null || "plantuml".equalsIgnoreCase(blockName.trim())) {
            blockName = "";
        }

        int x2 = posX + ((frameWidth > 0) ? frameWidth : 16);
        int y2 = posY + ((frameHeight > 0) ? frameHeight : 16);

        String dumpStr = "467 " + posX + " " + posY + " " + x2 + " " + y2
            + " 0 " + SFCRUtil.escapeToken(source.toString()) + " " + width + " " + scale;

        SequenceDiagramElm sequenceElm = createElementFromDump(dumpStr);
        int nextY = posY + ((sequenceElm != null) ? sequenceElm.getRenderedDiagramHeight() : 1000) + ctx.getElementSpacing();

        if (ctx.hasPendingResult()) {
            ctx.addBlockDump("plantuml", blockName, dumpStr);
            if (!blockPos.hasPosition()) {
                ctx.setCurrentPosition(posX, nextY);
            }
            return ParseResult.next(i);
        }

        if (sequenceElm != null) {
            CirSim sim = ctx.getSim();
            sim.getImportExportHelper().assignPersistentUid(sequenceElm, startUmlMetadata.uid);
            sim.addElement(sequenceElm);
            ctx.addCreatedElement(sequenceElm);
            if (!blockPos.hasPosition()) {
                ctx.setCurrentPosition(posX, nextY);
            }
        } else {
            CirSim.console("SFCRParser: Error creating PlantUML sequence diagram element");
        }
        return ParseResult.next(i);
    }

    private StartUmlMetadata extractStartUmlMetadata(String sourceText) {
        StartUmlMetadata metadata = new StartUmlMetadata();
        metadata.sanitizedSource = sourceText == null ? "" : sourceText;
        if (sourceText == null || sourceText.isEmpty()) {
            return metadata;
        }

        String[] lines = sourceText.split("\n", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String rawLine = lines[lineIndex];
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.startsWith("@startuml")) {
                break;
            }

            String[] parts = trimmed.split("\\s+");
            StringBuilder rebuilt = new StringBuilder("@startuml");
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.isEmpty()) {
                    continue;
                }
                String lower = part.toLowerCase();
                if (lower.startsWith("x=")) {
                    try {
                        metadata.x = Integer.parseInt(part.substring(part.indexOf('=') + 1));
                        metadata.hasPosition = true;
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                if (lower.startsWith("y=")) {
                    try {
                        metadata.y = Integer.parseInt(part.substring(part.indexOf('=') + 1));
                        metadata.hasPosition = true;
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                if (lower.startsWith("scale=")) {
                    try {
                        metadata.scale = Math.max(.1, Double.parseDouble(part.substring(part.indexOf('=') + 1)));
                        metadata.hasScale = true;
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                if (lower.startsWith("uid=")) {
                    metadata.uid = part.substring(part.indexOf('=') + 1);
                    continue;
                }
                if (lower.startsWith("width=")) {
                    try {
                        metadata.width = Integer.parseInt(part.substring(part.indexOf('=') + 1));
                        metadata.hasWidth = true;
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                if (lower.startsWith("w=")) {
                    try {
                        metadata.frameWidth = Integer.parseInt(part.substring(part.indexOf('=') + 1));
                        metadata.hasFrameWidth = true;
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                if (lower.startsWith("h=")) {
                    try {
                        metadata.frameHeight = Integer.parseInt(part.substring(part.indexOf('=') + 1));
                        metadata.hasFrameHeight = true;
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                if (lower.startsWith("framewidth=")) {
                    try {
                        metadata.frameWidth = Integer.parseInt(part.substring(part.indexOf('=') + 1));
                        metadata.hasFrameWidth = true;
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                if (lower.startsWith("frameheight=")) {
                    try {
                        metadata.frameHeight = Integer.parseInt(part.substring(part.indexOf('=') + 1));
                        metadata.hasFrameHeight = true;
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                rebuilt.append(" ").append(part);
            }

            lines[lineIndex] = rebuilt.toString();
            StringBuilder sanitized = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    sanitized.append("\n");
                }
                sanitized.append(lines[i]);
            }
            metadata.sanitizedSource = sanitized.toString();
            break;
        }

        return metadata;
    }

    private static class StartUmlMetadata {
        boolean hasPosition;
        boolean hasWidth;
        boolean hasScale;
        boolean hasFrameWidth;
        boolean hasFrameHeight;
        int x;
        int y;
        int width;
        int frameWidth;
        int frameHeight;
        double scale = 1.0;
        String uid;
        String sanitizedSource;
    }

    private SequenceDiagramElm createElementFromDump(String dumpStr) {
        try {
            StringTokenizer st = new StringTokenizer(dumpStr);
            int type = Integer.parseInt(st.nextToken());
            int xa = Integer.parseInt(st.nextToken());
            int ya = Integer.parseInt(st.nextToken());
            int xb = Integer.parseInt(st.nextToken());
            int yb = Integer.parseInt(st.nextToken());
            int flags = Integer.parseInt(st.nextToken());
            CircuitElm ce = ElementFactoryFacade.createFromDumpType(type, xa, ya, xb, yb, flags, st);
            if (ce instanceof SequenceDiagramElm) {
                return (SequenceDiagramElm) ce;
            }
        } catch (Exception e) {
            CirSim.console("SFCRParser: Error parsing PlantUML dump: " + e.getMessage());
        }
        return null;
    }
}
