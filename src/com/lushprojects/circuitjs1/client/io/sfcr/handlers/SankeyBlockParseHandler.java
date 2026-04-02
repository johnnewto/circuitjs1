package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.elements.economics.SFCSankeyElm;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;
import com.lushprojects.circuitjs1.client.util.StringTokenizer;

public class SankeyBlockParseHandler implements SFCRBlockParseHandler {
    @Override
    public String[] supportedDirectives() {
        return new String[]{"@sankey"};
    }

    @Override
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx) {
        String headerLine = lines[startIndex].trim();
        SFCRParser.BlockHeaderInfo blockPos = ctx.parseBlockHeader(headerLine, "@sankey");

        String sourceName = "";
        String layout = "LINEAR";
        int width = 300;
        int height = 250;
        boolean showScaleBar = true;
        double fixedMaxScale = 0;
        boolean useHighWaterMark = false;
        boolean showFlowValues = false;
        String uidFromFile = null;

        int i = startIndex + 1;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.equals("@end") || line.startsWith("@")) {
                if (line.equals("@end")) {
                    i++;
                }
                break;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }

            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String key = line.substring(0, colonIdx).trim().toLowerCase();
                String value = line.substring(colonIdx + 1).trim();
                int commentIdx = value.indexOf('#');
                if (commentIdx >= 0) {
                    value = value.substring(0, commentIdx).trim();
                }

                if (key.equals("source")) {
                    sourceName = value;
                } else if (key.equals("uid")) {
                    uidFromFile = value;
                } else if (key.equals("layout")) {
                    layout = value.toUpperCase();
                    if (!layout.equals("CIRCULAR")) {
                        layout = "LINEAR";
                    }
                } else if (key.equals("width")) {
                    try {
                        width = Integer.parseInt(value);
                    } catch (Exception e) {
                    }
                } else if (key.equals("height")) {
                    try {
                        height = Integer.parseInt(value);
                    } catch (Exception e) {
                    }
                } else if (key.equals("showscalebar")) {
                    showScaleBar = value.equalsIgnoreCase("true") || value.equals("1");
                } else if (key.equals("fixedmaxscale")) {
                    try {
                        fixedMaxScale = Double.parseDouble(value);
                    } catch (Exception e) {
                    }
                } else if (key.equals("usehighwatermark")) {
                    useHighWaterMark = value.equalsIgnoreCase("true") || value.equals("1");
                } else if (key.equals("showflowlabels") || key.equals("showflowvalues")) {
                    showFlowValues = value.equalsIgnoreCase("true") || value.equals("1");
                }
            }
            i++;
        }

        int posX = blockPos.hasPosition() ? blockPos.x : ctx.getCurrentX();
        int posY = blockPos.hasPosition() ? blockPos.y : ctx.getCurrentY();

        String dumpStr = "466 " + posX + " " + posY + " " + (posX + 16) + " " + (posY + 16) + " 0 "
            + SFCRParser.escapeToken(sourceName) + " " + layout + " " + width + " " + height + " "
            + (showScaleBar ? "1" : "0") + " " + fixedMaxScale + " "
            + (useHighWaterMark ? "1" : "0") + " " + (showFlowValues ? "1" : "0");

        if (ctx.hasPendingResult()) {
            ctx.addBlockDump("sankey", "", dumpStr);
            if (!blockPos.hasPosition()) {
                ctx.setCurrentPosition(posX, posY + height + ctx.getElementSpacing());
            }
            return ParseResult.next(i);
        }

        SFCSankeyElm sankeyElm = new SFCSankeyElm(posX, posY);
        try {
            StringTokenizer st = new StringTokenizer(dumpStr);
            st.nextToken();
            int x1 = Integer.parseInt(st.nextToken());
            int y1 = Integer.parseInt(st.nextToken());
            int x2 = Integer.parseInt(st.nextToken());
            int y2 = Integer.parseInt(st.nextToken());
            int flags = Integer.parseInt(st.nextToken());
            sankeyElm = new SFCSankeyElm(x1, y1, x2, y2, flags, st);
        } catch (Exception e) {
            CirSim.console("SFCRParser: Error creating Sankey element: " + e.getMessage());
        }

        CirSim sim = ctx.getSim();
        sim.getImportExportHelper().assignPersistentUid(sankeyElm, uidFromFile);
        sim.addElement(sankeyElm);
        ctx.addCreatedElement(sankeyElm);

        if (!blockPos.hasPosition()) {
            ctx.setCurrentPosition(posX, posY + height + ctx.getElementSpacing());
        }
        return ParseResult.next(i);
    }
}
