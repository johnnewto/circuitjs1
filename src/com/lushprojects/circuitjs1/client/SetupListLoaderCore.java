package com.lushprojects.circuitjs1.client;


import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.lushprojects.circuitjs1.client.util.Locale;

public class SetupListLoaderCore {
    private final CirSim sim;

    public SetupListLoaderCore(CirSim sim) {
        this.sim = sim;
    }

    public void getSetupList(final boolean openDefault) {
        MenuBar circuitsMenu = new MenuBar(true);
        circuitsMenu.setAutoOpen(true);
        sim.getMenuUiState().menuBar.addItem(Locale.LS("Circuits"), circuitsMenu);
        addDialogsMenu();

        loadSetupListIntoMenu("setuplist_economics.txt", circuitsMenu, openDefault, sim.showElectronicsCircuits);
    }

    private void addDialogsMenu() {
        MenuBar varBrowserMenu = new MenuBar(true);
        varBrowserMenu.setAutoOpen(true);
        varBrowserMenu.addItem(sim.menuItemWithShortcut("list-ul", "Variable Browser...", "\\", new MyCommand("edit", "variablebrowser")));
        varBrowserMenu.addItem(sim.menuItemWithShortcut("doc-text", "Glossary Editor...", "", new MyCommand("edit", "hinteditor")));
        varBrowserMenu.addItem(sim.menuItemWithShortcut("clock-o", "Action Time Schedule...", "", new MyCommand("edit", "actiontimedialog")));
        varBrowserMenu.addItem(sim.menuItemWithShortcut("code", "Embedded Viewer...", "", new MyCommand("edit", "iframeviewer")));
        varBrowserMenu.addItem(sim.menuItemWithShortcut("check-square-o", "Math Elements Test Suite...", "", new MyCommand("edit", "mathtestdialog")));
        varBrowserMenu.addItem(sim.menuItemWithShortcut("table", "Table Elements Test Suite...", "", new MyCommand("edit", "tabletestdialog")));
        sim.getMenuUiState().menuBar.addItem(Locale.LS("Dialogs"), varBrowserMenu);
        if (sim.getMenuUiState().helpMenuBar != null && sim.getMenuUiState().helpMenuBar.getParent() == null) {
            sim.getMenuUiState().menuBar.addItem(Locale.LS("Help"), sim.getMenuUiState().helpMenuBar);
        }
    }

    private void loadSetupListIntoMenu(final String setupListPath, final MenuBar circuitsMenu,
                                       final boolean openDefault, final boolean loadElectronicsAfter) {
        final String circuitPrefix = setupListPath.equals("setuplist_economics.txt") ? "economics/" :
                (setupListPath.equals("setuplist_electronics.txt") ? "electronics/" : "");
        String url = GWT.getModuleBaseURL() + setupListPath;
        String cacheBustedUrl = sim.getCircuitIOService().getLoadUrl(url);
        RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.GET, cacheBustedUrl);
        try {
            requestBuilder.sendRequest(null, new RequestCallback() {
                public void onError(Request request, Throwable exception) {
                    if (!sim.hideMenu)
                        sim.alertOrWarn(Locale.LS("Can't load circuit list!"));
                    GWT.log("File Error Response", exception);
                }

                public void onResponseReceived(Request request, Response response) {
                    if (response.getStatusCode() == Response.SC_OK) {
                        String text = response.getText();
                        processSetupList(text.getBytes(), openDefault, circuitsMenu, circuitPrefix);
                        if (loadElectronicsAfter)
                            loadSetupListIntoMenu("setuplist_electronics.txt", circuitsMenu, false, false);
                    } else {
                        if (!sim.hideMenu)
                            sim.alertOrWarn(Locale.LS("Can't load circuit list!"));
                        GWT.log("Bad file server response:" + response.getStatusText());
                    }
                }
            });
        } catch (RequestException e) {
            GWT.log("failed file reading", e);
        }
    }

    private void processSetupList(byte b[], final boolean openDefault, MenuBar circuitsMenu, String circuitPrefix) {
        int len = b.length;
        MenuBar currentMenuBar;
        MenuBar stack[] = new MenuBar[6];
        int stackptr = 0;
        currentMenuBar = circuitsMenu;
        stack[stackptr++] = currentMenuBar;
        int p;
        for (p = 0; p < len; ) {
            int l;
            for (l = 0; l != len - p; l++)
                if (b[l + p] == '\n' || b[l + p] == '\r') {
                    l++;
                    break;
                }
            String line = new String(b, p, l - 1);
            if (line.isEmpty() || line.charAt(0) == '#')
                ;
            else if (line.charAt(0) == '+') {
                MenuBar n = new MenuBar(true);
                n.setAutoOpen(true);
                currentMenuBar.addItem(Locale.LS(line.substring(1)), n);
                currentMenuBar = stack[stackptr++] = n;
            } else if (line.charAt(0) == '-') {
                if (stackptr > 1)
                    currentMenuBar = stack[--stackptr - 1];
            } else {
                int i = line.indexOf(' ');
                if (i > 0) {
                    String title = Locale.LS(line.substring(i + 1));
                    boolean first = false;
                    if (line.charAt(0) == '>')
                        first = true;
                    String file = line.substring(first ? 1 : 0, i);
                    String prefixedFile = circuitPrefix + file;
                    currentMenuBar.addItem(new MenuItem(title,
                            new MyCommand("circuits", "setup " + prefixedFile + " " + title)));
                    if (file.equals(sim.startCircuit) && sim.startLabel == null) {
                        sim.startLabel = title;
                        sim.setCircuitTitle(title);
                    }
                    if (first && sim.startCircuit == null) {
                        sim.startCircuit = prefixedFile;
                        sim.startLabel = title;
                        if (openDefault && sim.stopMessage == null)
                            sim.getCircuitIOService().readSetupFile(sim.startCircuit, sim.startLabel);
                    }
                }
            }
            p += l;
        }
    }
}
