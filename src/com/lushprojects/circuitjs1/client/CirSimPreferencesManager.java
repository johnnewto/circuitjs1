package com.lushprojects.circuitjs1.client;

import com.google.gwt.http.client.URL;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.storage.client.Storage;
import com.google.gwt.user.client.Window;

class CirSimPreferencesManager {
    private final CirSim sim;

    CirSimPreferencesManager(CirSim sim) {
        this.sim = sim;
    }

    void setColors(String positiveColor, String negativeColor, String neutralColor, String selectColor, String currentColor) {
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor != null) {
            if (positiveColor == null)
                positiveColor = stor.getItem("positiveColor");
            if (negativeColor == null)
                negativeColor = stor.getItem("negativeColor");
            if (neutralColor == null)
                neutralColor = stor.getItem("neutralColor");
            if (selectColor == null)
                selectColor = stor.getItem("selectColor");
            if (currentColor == null)
                currentColor = stor.getItem("currentColor");

            String customUnit = stor.getItem("voltageUnitSymbol");
            if (customUnit != null && !customUnit.isEmpty())
                sim.voltageUnitSymbol = customUnit;
        }

        if (positiveColor != null)
            CircuitElm.positiveColor = new Color(URL.decodeQueryString(positiveColor));
        else if (getOptionFromStorage("alternativeColor", false))
            CircuitElm.positiveColor = Color.blue;

        if (negativeColor != null)
            CircuitElm.negativeColor = new Color(URL.decodeQueryString(negativeColor));
        if (neutralColor != null)
            CircuitElm.neutralColor = new Color(URL.decodeQueryString(neutralColor));

        if (selectColor != null)
            CircuitElm.selectColor = new Color(URL.decodeQueryString(selectColor));
        else
            CircuitElm.selectColor = Color.cyan;

        CircuitElm.connectedColor = new Color(0, 140, 140);

        if (currentColor != null)
            CircuitElm.currentColor = new Color(URL.decodeQueryString(currentColor));
        else
            CircuitElm.currentColor = sim.conventionCheckItem.getState() ? Color.yellow : Color.cyan;

        CircuitElm.setColorScale();
    }

    void setWheelSensitivity() {
        sim.wheelSensitivity = 1;
        try {
            Storage stor = Storage.getLocalStorageIfSupported();
            sim.wheelSensitivity = Double.parseDouble(stor.getItem("wheelSensitivity"));

            String guiStr = stor.getItem("graphicsUpdateInterval");
            if (guiStr != null) {
                int gui = Integer.parseInt(guiStr);
                if (gui >= 1 && gui <= 10)
                    sim.graphicsUpdateInterval = gui;
            }

            String eqTolStr = stor.getItem("equationTableConvergenceTolerance");
            if (eqTolStr != null) {
                double eqTol = Double.parseDouble(eqTolStr);
                if (eqTol > 0)
                    sim.equationTableConvergenceTolerance = eqTol;
            }
        } catch (Exception e) {
        }
    }

    boolean getOptionFromStorage(String key, boolean val) {
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null)
            return val;
        String s = stor.getItem(key);
        if (s == null)
            return val;
        return s == "true";
    }

    void setOptionInStorage(String key, boolean val) {
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null)
            return;
        stor.setItem(key, val ? "true" : "false");
    }

    void showVoltageUnitDialog() {
        String newSymbol = Window.prompt("Enter voltage unit symbol (e.g., V, $, €, or leave blank for no unit):", sim.voltageUnitSymbol);
        if (newSymbol != null) {
            newSymbol = newSymbol.trim();
            if (newSymbol.isEmpty())
                newSymbol = "V";
            sim.voltageUnitSymbol = newSymbol;
            Storage stor = Storage.getLocalStorageIfSupported();
            if (stor != null)
                stor.setItem("voltageUnitSymbol", sim.voltageUnitSymbol);
            sim.repaint();
        }
    }

    void saveShortcuts() {
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null)
            return;
        String str = "1";
        int i;
        for (i = 0; i != sim.shortcuts.length; i++) {
            String sh = sim.shortcuts[i];
            if (sh == null)
                continue;
            str += ";" + i + "=" + sh;
        }
        stor.setItem("shortcuts", str);
    }

    void loadShortcuts() {
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null)
            return;
        String str = stor.getItem("shortcuts");
        if (str == null)
            return;
        String keys[] = str.split(";");

        int i;
        for (i = 0; i != sim.shortcuts.length; i++)
            sim.shortcuts[i] = null;

        for (i = 0; i != sim.getMenuUiState().mainMenuItems.size(); i++) {
            CheckboxMenuItem item = sim.getMenuUiState().mainMenuItems.get(i);
            if (item.getShortcut().length() > 1)
                break;
            item.setShortcut("");
        }

        for (i = 1; i < keys.length; i++) {
            String arr[] = keys[i].split("=");
            if (arr.length != 2)
                continue;
            int c = Integer.parseInt(arr[0]);
            String className = arr[1];
            sim.shortcuts[c] = className;

            int j;
            for (j = 0; j != sim.getMenuUiState().mainMenuItems.size(); j++) {
                if (sim.getMenuUiState().mainMenuItemNames.get(j) == className) {
                    CheckboxMenuItem item = sim.getMenuUiState().mainMenuItems.get(j);
                    item.setShortcut(Character.toString((char) c));
                    break;
                }
            }
        }
    }

    String formatTimeFixed(double t) {
        NumberFormat fixedFmt = NumberFormat.getFormat("0.00");
        String u = sim.timeUnitSymbol;
        double va = Math.abs(t);
        if (va < 1e-14)
            return "0.00 " + u;
        if (va < 1e-9)
            return fixedFmt.format(t * 1e12) + " p" + u;
        if (va < 1e-6)
            return fixedFmt.format(t * 1e9) + " n" + u;
        if (va < 1e-3)
            return fixedFmt.format(t * 1e6) + " μ" + u;
        if (va < 1)
            return fixedFmt.format(t * 1e3) + " m" + u;
        if (va < 1e3)
            return fixedFmt.format(t) + " " + u;
        if (va < 1e6)
            return fixedFmt.format(t * 1e-3) + " k" + u;
        return NumberFormat.getFormat("#.##E000").format(t) + " " + u;
    }

    void setGrid() {
        if (sim.smallGridCheckItem != null)
            sim.gridSize = (sim.smallGridCheckItem.getState()) ? 8 : 16;
        else
            sim.gridSize = 16;
        sim.gridMask = ~(sim.gridSize - 1);
        sim.gridRound = sim.gridSize / 2 - 1;
    }

    boolean weAreInUS(boolean orCanada) {
        try {
            CirSim.NavigatorLike nav = CirSim.GlobalWindowLike.getNavigator();
            String l = nav != null ? nav.getLanguage() : null;
            if (l == null && nav != null)
                l = nav.getUserLanguage();
            if (l == null || l.length() <= 2)
                return false;
            String suffix = l.substring(l.length() - 2).toUpperCase();
            return ("US".equals(suffix) || ("CA".equals(suffix) && orCanada));
        } catch (Exception e) {
            return false;
        }
    }

    boolean weAreInGermany() {
        try {
            CirSim.NavigatorLike nav = CirSim.GlobalWindowLike.getNavigator();
            String l = nav != null ? nav.getLanguage() : null;
            if (l == null && nav != null)
                l = nav.getUserLanguage();
            return l != null && l.toUpperCase().startsWith("DE");
        } catch (Exception e) {
            return false;
        }
    }

}