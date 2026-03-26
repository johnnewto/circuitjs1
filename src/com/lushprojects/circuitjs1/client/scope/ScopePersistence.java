package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;

import com.google.gwt.storage.client.Storage;
import com.lushprojects.circuitjs1.client.elements.electronics.semiconductors.TransistorElm;
import com.lushprojects.circuitjs1.client.runner.RuntimeMode;
import com.lushprojects.circuitjs1.client.util.StringTokenizer;

final class ScopePersistence {
    private ScopePersistence() {
    }

    static String dump(Scope scope) {
        ScopePlot vPlot = scope.getPlotAt(0);
        CircuitElm elm = vPlot.elm;
        if (elm == null) {
            return null;
        }
        int flags = scope.getFlags();
        int eno = scope.locateElement(elm);
        if (eno < 0) {
            return null;
        }
        String x = "o " + eno + " " +
                vPlot.scopePlotSpeed + " " + vPlot.value + " "
                + exportAsDecOrHex(flags, scope.getPerPlotFlagsMaskForPersistence()) + " " +
                ((flags & scope.getPlotRefsMaskForPersistence()) != 0 ? getElementRefToken(vPlot.elm) + " " : "") +
                scope.getScaleForUnit(Scope.UNITS_V) + " " + scope.getScaleForUnit(Scope.UNITS_A) + " " + scope.getPositionForPersistence() + " " +
                scope.getPlotCount();
        if ((flags & scope.getDivisionsMaskForPersistence()) != 0) {
            x += " " + scope.getManDivisionsForPersistence();
        }

        if ((flags & scope.getMaxScaleLimitsMaskForPersistence()) != 0) {
            for (int i = 0; i <= Scope.UNITS_OHMS; i++) {
                Double limit = scope.getMaxScaleLimitForUnit(i);
                if (limit != null) {
                    x += " L" + i + ":" + limit;
                }
            }
        }

        for (int i = 0; i < scope.getPlotCount(); i++) {
            ScopePlot p = scope.getPlotAt(i);
            if ((flags & scope.getPerPlotFlagsMaskForPersistence()) != 0) {
                x += " " + Integer.toHexString(p.getPlotFlags());
            }
            if ((flags & scope.getPlotRefsMaskForPersistence()) != 0 && i > 0) {
                x += " " + getElementRefToken(p.elm);
            }
            if (i > 0) {
                x += " " + scope.locateElement(p.elm) + " " + p.value;
            }
            if (p.units > Scope.UNITS_A) {
                x += " " + scope.getScaleForUnit(p.units);
            }
            if (scope.isManualScale()) {
                x += " " + p.manScale + " " + p.manVPosition;
            }
        }
        if (scope.getRawLabelText() != null) {
            x += " " + CustomLogicModel.escape(scope.getRawLabelText());
        }
        if (scope.getRawTitleText() != null) {
            x += " T:" + CustomLogicModel.escape(scope.getRawTitleText());
        }
        return x;
    }

    static void undump(Scope scope, StringTokenizer st) {
        scope.initialize();
        int e = Integer.parseInt(st.nextToken());
        if (e == -1) {
            return;
        }
        CircuitElm ce = scope.getElementAt(e);
        scope.setSpeedForPersistence(Integer.parseInt(st.nextToken()));
        int value = Integer.parseInt(st.nextToken());

        int flags = importDecOrHex(st.nextToken());
        boolean hasPlotRefs = (flags & scope.getPlotRefsMaskForPersistence()) != 0;
        String plot0RefToken = null;
        if (hasPlotRefs && st.hasMoreTokens()) {
            plot0RefToken = st.nextToken();
        }
        ce = resolveElementRef(scope, plot0RefToken, e);

        if (!(ce instanceof TransistorElm) && value == Scope.VAL_POWER_OLD) {
            value = Scope.VAL_POWER;
        }

        scope.setScaleForUnit(Scope.UNITS_V, Double.parseDouble(st.nextToken()));
        scope.setScaleForUnit(Scope.UNITS_A, Double.parseDouble(st.nextToken()));
        if (scope.getScaleForUnit(Scope.UNITS_V) == 0) {
            scope.setScaleForUnit(Scope.UNITS_V, .5);
        }
        if (scope.getScaleForUnit(Scope.UNITS_A) == 0) {
            scope.setScaleForUnit(Scope.UNITS_A, 1);
        }
        scope.initializeScaleFromVoltageAndCurrent();
        scope.clearLabelText();
        boolean plot2dFlag = (flags & 64) != 0;
        boolean hasPlotFlags = (flags & scope.getPerPlotFlagsMaskForPersistence()) != 0;
        boolean hasMaxLimits = (flags & scope.getMaxScaleLimitsMaskForPersistence()) != 0;

        if ((flags & scope.getPlotsMaskForPersistence()) != 0) {
            try {
                scope.setPositionForPersistence(Integer.parseInt(st.nextToken()));
                int sz = Integer.parseInt(st.nextToken());
                scope.setManDivisions(8);
                if ((flags & scope.getDivisionsMaskForPersistence()) != 0) {
                    scope.setManDivisions(Integer.parseInt(st.nextToken()));
                }

                if (hasMaxLimits) {
                    while (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        if (token.startsWith("L") && token.contains(":")) {
                            try {
                                int colonPos = token.indexOf(':');
                                int unit = Integer.parseInt(token.substring(1, colonPos));
                                double limit = Double.parseDouble(token.substring(colonPos + 1));
                                scope.setMaxScaleLimitForUnit(unit, limit);
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
                    ce = scope.getElementAt(e);
                }
                if (ce == null) {
                    return;
                }
                int u = ce.getScopeUnitsForScope(value);
                if (u > Scope.UNITS_A) {
                    scope.setScaleForUnit(u, Double.parseDouble(st.nextToken()));
                }
                scope.setValue(value, ce);
                scope.trimPlotsToSize(1);

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
                            resolvedElm = scope.getElementAt(ne);
                        }
                        if (resolvedElm == null) {
                            resolvedElm = ce;
                        }
                        u = resolvedElm.getScopeUnitsForScope(val);
                        if (u > Scope.UNITS_A) {
                            scope.setScaleForUnit(u, Double.parseDouble(st.nextToken()));
                        }
                        scope.addPlotInternal(resolvedElm, u, val, scope.getManScaleFromMaxScale(u, false));
                    }
                    boolean acCoupled = (plotFlags & ScopePlot.FLAG_AC) != 0;
                    if ((flags & scope.getPerPlotManualScaleMaskForPersistence()) != 0) {
                        double manScale = Double.parseDouble(st.nextToken());
                        int manPos = Integer.parseInt(st.nextToken());
                        scope.setPlotStateFromPersistence(i, acCoupled, true, manScale, manPos);
                    } else {
                        ScopePlot p = scope.getPlotAt(i);
                        if (p != null) {
                            scope.setPlotStateFromPersistence(i, acCoupled, p.manScaleSet, p.manScale, p.manVPosition);
                        }
                    }
                }
                parseTextAndTitle(scope, st);
            } catch (Exception ee) {
                // Keep legacy behavior: tolerate malformed scope dump lines.
            }
        } else {
            CircuitElm yElm = null;
            int ivalue = 0;
            scope.setManDivisions(8);
            try {
                scope.setPositionForPersistence(Integer.parseInt(st.nextToken()));
                int ye = -1;
                if ((flags & scope.getYElmMaskForPersistence()) != 0) {
                    ye = Integer.parseInt(st.nextToken());
                    if (ye != -1) {
                        yElm = scope.getElementAt(ye);
                    }
                    if (!plot2dFlag) {
                        yElm = null;
                    }
                }
                if ((flags & scope.getIValueMaskForPersistence()) != 0) {
                    ivalue = Integer.parseInt(st.nextToken());
                }
                parseTextAndTitle(scope, st);
            } catch (Exception ee) {
                // Keep legacy behavior: tolerate malformed scope dump lines.
            }
            scope.setValues(value, ivalue, scope.getElementAt(e), yElm);
        }
        if (scope.getRawLabelText() != null) {
            scope.setRawLabelText(CustomLogicModel.unescape(scope.getRawLabelText()));
        }
        if (scope.getRawTitleText() != null) {
            scope.setRawTitleText(CustomLogicModel.unescape(scope.getRawTitleText()));
        }
        scope.setPlot2dForPersistence(plot2dFlag);
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
        ScopePlot vPlot = scope.getPlotAt(0);
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
        scope.setSpeedForPersistence(Integer.parseInt(arr[2]));
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
                for (int i = 0; i < scope.getElementCount(); i++) {
                    CircuitElm candidate = scope.getElementAt(i);
                    if (candidate != null && target.equals(candidate.getPersistentUid())) {
                        return candidate;
                    }
                }
            }
        }
        if (token != null && token.startsWith("R:")) {
            String target = token.substring(2);
            if (!target.isEmpty()) {
                for (int i = 0; i < scope.getElementCount(); i++) {
                    CircuitElm candidate = scope.getElementAt(i);
                    if (candidate == null) {
                        continue;
                    }
                    String d = candidate.dumpForScope();
                    if (d != null && CustomLogicModel.escape(d).equals(target)) {
                        return candidate;
                    }
                }
            }
        }
        if (fallbackIndex >= 0) {
            return scope.getElementAt(fallbackIndex);
        }
        return null;
    }

    private static void parseTextAndTitle(Scope scope, StringTokenizer st) {
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (token.startsWith("T:")) {
                scope.appendTitleToken(token.substring(2));
            } else {
                scope.appendLabelToken(token);
            }
        }
    }
}
