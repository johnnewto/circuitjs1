package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.CustomLogicModel;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.io.sfcr.ParseResult;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRParseContext;
import com.lushprojects.circuitjs1.client.scope.Scope;

public class ScopeBlockParseHandler implements SFCRBlockParseHandler {
    @Override
    public String[] supportedDirectives() {
        return new String[]{"@scope"};
    }

    @Override
    public ParseResult parse(String[] lines, int startIndex, SFCRParseContext ctx) {
        String header = lines[startIndex].trim();

        if (!ctx.looksLikeScopeBlock(lines, startIndex)) {
            String varName = header.substring(6).trim();
            ctx.addScopeVariable(varName);
            return ParseResult.next(startIndex + 1);
        }

        SFCRParser.ScopeBlockSpec spec = new SFCRParser.ScopeBlockSpec();
        String rest = header.substring(6).trim();
        if (!rest.isEmpty()) {
            String[] parts = rest.split("\\s+");
            StringBuilder nameBuilder = new StringBuilder();
            for (int p = 0; p < parts.length; p++) {
                String part = parts[p];
                if (part.toLowerCase().startsWith("position=")) {
                    try {
                        spec.position = Integer.parseInt(part.substring(9));
                    } catch (Exception e) {
                    }
                } else {
                    if (nameBuilder.length() > 0) {
                        nameBuilder.append(" ");
                    }
                    nameBuilder.append(part);
                }
            }
            if (nameBuilder.length() > 0) {
                spec.name = nameBuilder.toString();
            }
        }

        int i = startIndex + 1;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.equals("@end")) {
                i++;
                break;
            }
            if (line.startsWith("@")) {
                break;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }

            int sep = line.indexOf(':');
            int eq = line.indexOf('=');
            int split = (sep >= 0) ? sep : eq;
            if (split > 0) {
                String key = line.substring(0, split).trim().toLowerCase();
                String value = line.substring(split + 1).trim();

                if (key.equals("speed")) {
                    try {
                        spec.speed = Integer.parseInt(value);
                    } catch (Exception e) {
                    }
                } else if (key.equals("flags")) {
                    try {
                        spec.flags = Scope.importDecOrHex(value);
                    } catch (Exception e) {
                    }
                } else if (key.equals("title")) {
                    spec.title = CustomLogicModel.unescape(value);
                } else if (key.equals("label")) {
                    spec.label = CustomLogicModel.unescape(value);
                } else if (key.equals("source") || key.equals("trace")) {
                    SFCRParser.ScopeTraceSpec trace = parseScopeTraceSpec(value);
                    if (trace != null && trace.uid != null && trace.uid.length() > 0) {
                        if (key.equals("source")) {
                            spec.traces.add(0, trace);
                        } else {
                            spec.traces.add(trace);
                        }
                    }
                } else if (key.equals("x1")) {
                    try { spec.x1 = Integer.parseInt(value); } catch (Exception e) {}
                } else if (key.equals("y1")) {
                    try { spec.y1 = Integer.parseInt(value); } catch (Exception e) {}
                } else if (key.equals("x2")) {
                    try { spec.x2 = Integer.parseInt(value); } catch (Exception e) {}
                } else if (key.equals("y2")) {
                    try { spec.y2 = Integer.parseInt(value); } catch (Exception e) {}
                } else if (key.equals("elmuid")) {
                    spec.elmUid = value;
                }
            }
            i++;
        }

        ctx.addScopeBlock(spec);
        return ParseResult.next(i);
    }

    private SFCRParser.ScopeTraceSpec parseScopeTraceSpec(String payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }

        SFCRParser.ScopeTraceSpec trace = new SFCRParser.ScopeTraceSpec();
        String[] parts = payload.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.toLowerCase().startsWith("uid:") || part.toLowerCase().startsWith("uid=")) {
                trace.uid = part.substring(4);
            } else if (part.toLowerCase().startsWith("value:") || part.toLowerCase().startsWith("value=")) {
                try {
                    trace.value = Integer.parseInt(part.substring(6));
                } catch (Exception e) {
                }
            }
        }

        if (trace.uid == null || trace.uid.isEmpty()) {
            return null;
        }
        return trace;
    }
}
