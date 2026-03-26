package com.lushprojects.circuitjs1.client;

import com.google.gwt.storage.client.Storage;
import com.lushprojects.circuitjs1.client.elements.electronics.semiconductors.TransistorElm;
import com.lushprojects.circuitjs1.client.runner.RuntimeMode;
import com.lushprojects.circuitjs1.client.util.StringTokenizer;

final class ScopePersistence {
    private ScopePersistence() {
    }

    static String dump(Scope scope) {
        ScopePlot vPlot = scope.plots.get(0);
        CircuitElm elm = vPlot.elm;
        if (elm == null) {
            return null;
        }
        int flags = scope.getFlags();
        int eno = scope.sim.locateElm(elm);
        if (eno < 0) {
            return null;
        }
        String x = "o " + eno + " " +
                vPlot.scopePlotSpeed + " " + vPlot.value + " "
                + exportAsDecOrHex(flags, scope.FLAG_PERPLOTFLAGS) + " " +
                ((flags & scope.FLAG_PLOT_REFS) != 0 ? getElementRefToken(vPlot.elm) + " " : "") +
                scope.scale[Scope.UNITS_V] + " " + scope.scale[Scope.UNITS_A] + " " + scope.position + " " +
                scope.plots.size();
        if ((flags & scope.FLAG_DIVISIONS) != 0) {
            x += " " + scope.manDivisions;
        }

        if ((flags & scope.FLAG_MAX_SCALE_LIMITS) != 0) {
            for (int i = 0; i < scope.maxScaleLimit.length; i++) {
                if (scope.maxScaleLimit[i] != null) {
                    x += " L" + i + ":" + scope.maxScaleLimit[i];
                }
            }
        }

        for (int i = 0; i < scope.plots.size(); i++) {
            ScopePlot p = scope.plots.get(i);
            if ((flags & scope.FLAG_PERPLOTFLAGS) != 0) {
                x += " " + Integer.toHexString(p.getPlotFlags());
            }
            if ((flags & scope.FLAG_PLOT_REFS) != 0 && i > 0) {
                x += " " + getElementRefToken(p.elm);
            }
            if (i > 0) {
                x += " " + scope.sim.locateElm(p.elm) + " " + p.value;
            }
            if (p.units > Scope.UNITS_A) {
                x += " " + scope.scale[p.units];
            }
            if (scope.isManualScale()) {
                x += " " + p.manScale + " " + p.manVPosition;
            }
        }
        if (scope.text != null) {
            x += " " + CustomLogicModel.escape(scope.text);
        }
        if (scope.title != null) {
            x += " T:" + CustomLogicModel.escape(scope.title);
        }
        return x;
    }

    static void undump(Scope scope, StringTokenizer st) {
        scope.initialize();
        int e = Integer.parseInt(st.nextToken());
        if (e == -1) {
            return;
        }
        CircuitElm ce = scope.sim.getElm(e);
        scope.speed = Integer.parseInt(st.nextToken());
        int value = Integer.parseInt(st.nextToken());

        int flags = importDecOrHex(st.nextToken());
        boolean hasPlotRefs = (flags & scope.FLAG_PLOT_REFS) != 0;
        String plot0RefToken = null;
        if (hasPlotRefs && st.hasMoreTokens()) {
            plot0RefToken = st.nextToken();
        }
        ce = resolveElementRef(scope, plot0RefToken, e);

        if (!(ce instanceof TransistorElm) && value == Scope.VAL_POWER_OLD) {
            value = Scope.VAL_POWER;
        }

        scope.scale[Scope.UNITS_V] = Double.parseDouble(st.nextToken());
        scope.scale[Scope.UNITS_A] = Double.parseDouble(st.nextToken());
        if (scope.scale[Scope.UNITS_V] == 0) {
            scope.scale[Scope.UNITS_V] = .5;
        }
        if (scope.scale[Scope.UNITS_A] == 0) {
            scope.scale[Scope.UNITS_A] = 1;
        }
        scope.scaleX = scope.scale[Scope.UNITS_V];
        scope.scaleY = scope.scale[Scope.UNITS_A];
        scope.scale[Scope.UNITS_OHMS] = scope.scale[Scope.UNITS_W] = scope.scale[Scope.UNITS_V];
        scope.text = null;
        boolean plot2dFlag = (flags & 64) != 0;
        boolean hasPlotFlags = (flags & scope.FLAG_PERPLOTFLAGS) != 0;
        boolean hasMaxLimits = (flags & scope.FLAG_MAX_SCALE_LIMITS) != 0;

        if ((flags & scope.FLAG_PLOTS) != 0) {
            try {
                scope.position = Integer.parseInt(st.nextToken());
                int sz = Integer.parseInt(st.nextToken());
                scope.manDivisions = 8;
                if ((flags & scope.FLAG_DIVISIONS) != 0) {
                    scope.manDivisions = Scope.lastManDivisions = Integer.parseInt(st.nextToken());
                }

                if (hasMaxLimits) {
                    while (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        if (token.startsWith("L") && token.contains(":")) {
                            try {
                                int colonPos = token.indexOf(':');
                                int unit = Integer.parseInt(token.substring(1, colonPos));
                                double limit = Double.parseDouble(token.substring(colonPos + 1));
                                scope.maxScaleLimit[unit] = limit;
                            } catch (Exception ex) {
                                // Ignore malformed limit tokens
                            }
                        } else {
                            String remaining = token;
                            while (st.hasMoreTokens()) {
                                remaining += " " + st.nextToken();
                            }
                            st = new StringTokenizer(remaining, " ");
                            break;
                        }
                    }
                }

                if (ce == null) {
                    ce = scope.sim.getElm(e);
                }
                if (ce == null) {
                    return;
                }
                int u = ce.getScopeUnits(value);
                if (u > Scope.UNITS_A) {
                    scope.scale[u] = Double.parseDouble(st.nextToken());
                }
                scope.setValue(value, ce);
                while (scope.plots.size() > 1) {
                    scope.plots.removeElementAt(1);
                }

                int plotFlags = 0;
                for (int i = 0; i != sz; i++) {
                    if (hasPlotFlags) {
                        plotFlags = Integer.parseInt(st.nextToken(), 16);
                    }
                    String refToken = null;
                    if (hasPlotRefs && i > 0) {
                        refToken = st.nextToken();
                    }
                    if (i != 0) {
                        int ne = Integer.parseInt(st.nextToken());
                        int val = Integer.parseInt(st.nextToken());
                        CircuitElm resolvedElm = resolveElementRef(scope, refToken, ne);
                        if (resolvedElm == null) {
                            resolvedElm = scope.sim.getElm(ne);
                        }
                        if (resolvedElm == null) {
                            resolvedElm = ce;
                        }
                        u = resolvedElm.getScopeUnits(val);
                        if (u > Scope.UNITS_A) {
                            scope.scale[u] = Double.parseDouble(st.nextToken());
                        }
                        scope.plots.add(new ScopePlot(resolvedElm, u, val, scope.getManScaleFromMaxScale(u, false)));
                    }
                    ScopePlot p = scope.plots.get(i);
                    p.acCoupled = (plotFlags & ScopePlot.FLAG_AC) != 0;
                    if ((flags & scope.FLAG_PERPLOT_MAN_SCALE) != 0) {
                        p.manScaleSet = true;
                        p.manScale = Double.parseDouble(st.nextToken());
                        p.manVPosition = Integer.parseInt(st.nextToken());
                    }
                }
                parseTextAndTitle(scope, st);
            } catch (Exception ee) {
                // Keep legacy behavior: tolerate malformed scope dump lines.
            }
        } else {
            CircuitElm yElm = null;
            int ivalue = 0;
            scope.manDivisions = 8;
            try {
                scope.position = Integer.parseInt(st.nextToken());
                int ye = -1;
                if ((flags & scope.FLAG_YELM) != 0) {
                    ye = Integer.parseInt(st.nextToken());
                    if (ye != -1) {
                        yElm = scope.sim.getElm(ye);
                    }
                    if (!plot2dFlag) {
                        yElm = null;
                    }
                }
                if ((flags & scope.FLAG_IVALUE) != 0) {
                    ivalue = Integer.parseInt(st.nextToken());
                }
                parseTextAndTitle(scope, st);
            } catch (Exception ee) {
                // Keep legacy behavior: tolerate malformed scope dump lines.
            }
            scope.setValues(value, ivalue, scope.sim.getElm(e), yElm);
        }
        if (scope.text != null) {
            scope.text = CustomLogicModel.unescape(scope.text);
        }
        if (scope.title != null) {
            scope.title = CustomLogicModel.unescape(scope.title);
        }
        scope.plot2d = plot2dFlag;
        scope.setFlags(flags);
    }

    static void saveAsDefault(Scope scope) {
        if (RuntimeMode.isNonInteractiveRuntime()) {
            return;
        }
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null) {
            return;
        }
        ScopePlot vPlot = scope.plots.get(0);
        int flags = scope.getFlags();
        stor.setItem("scopeDefaults", "1 " + flags + " " + vPlot.scopePlotSpeed);
        CirSim.console("saved defaults " + flags);
    }

    static boolean loadDefaults(Scope scope) {
        if (RuntimeMode.isNonInteractiveRuntime()) {
            return false;
        }
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null) {
            return false;
        }
        String str = stor.getItem("scopeDefaults");
        if (str == null) {
            return false;
        }
        String arr[] = str.split(" ");
        int flags = Integer.parseInt(arr[1]);
        scope.setFlags(flags);
        scope.speed = Integer.parseInt(arr[2]);
        return true;
    }

    static String exportAsDecOrHex(int v, int thresh) {
        if (v >= thresh) {
            return "x" + Integer.toHexString(v);
        }
        return Integer.toString(v);
    }

    static int importDecOrHex(String s) {
        if (s.charAt(0) == 'x') {
            return Integer.parseInt(s.substring(1), 16);
        }
        return Integer.parseInt(s);
    }

    private static String getElementRefToken(CircuitElm elm) {
        if (elm == null) {
            return "U:";
        }
        return "U:" + CustomLogicModel.escape(elm.getPersistentUid());
    }

    private static CircuitElm resolveElementRef(Scope scope, String token, int fallbackIndex) {
        if (token != null && token.startsWith("U:")) {
            String target = CustomLogicModel.unescape(token.substring(2));
            if (!target.isEmpty()) {
                for (int i = 0; i < scope.sim.elmList.size(); i++) {
                    CircuitElm candidate = scope.sim.getElm(i);
                    if (candidate != null && target.equals(candidate.getPersistentUid())) {
                        return candidate;
                    }
                }
            }
        }
        if (token != null && token.startsWith("R:")) {
            String target = token.substring(2);
            if (!target.isEmpty()) {
                for (int i = 0; i < scope.sim.elmList.size(); i++) {
                    CircuitElm candidate = scope.sim.getElm(i);
                    if (candidate == null) {
                        continue;
                    }
                    String d = candidate.dump();
                    if (d != null && CustomLogicModel.escape(d).equals(target)) {
                        return candidate;
                    }
                }
            }
        }
        if (fallbackIndex >= 0) {
            return scope.sim.getElm(fallbackIndex);
        }
        return null;
    }

    private static void parseTextAndTitle(Scope scope, StringTokenizer st) {
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (token.startsWith("T:")) {
                if (scope.title == null) {
                    scope.title = token.substring(2);
                } else {
                    scope.title += " " + token;
                }
            } else {
                if (scope.text == null) {
                    scope.text = token;
                } else {
                    scope.text += " " + token;
                }
            }
        }
    }
}
