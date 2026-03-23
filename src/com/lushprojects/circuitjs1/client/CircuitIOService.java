package com.lushprojects.circuitjs1.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.storage.client.Storage;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.http.client.URL;
import com.lushprojects.circuitjs1.client.io.InfoViewerContentBuilder;
import com.lushprojects.circuitjs1.client.io.ImportExportHelper;
import com.lushprojects.circuitjs1.client.io.LookupDefinition;
import com.lushprojects.circuitjs1.client.io.LookupTableRegistry;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import com.lushprojects.circuitjs1.client.registry.ElementFactoryFacade;
import com.lushprojects.circuitjs1.client.util.Locale;

final class CircuitIOService {
    private final CirSim sim;
    private String recovery;

    CircuitIOService(CirSim sim) {
        this.sim = sim;
    }

    String getRecovery() {
        return recovery;
    }

    void writeRecoveryToStorage() {
        CirSim.console("write recovery");
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null)
            return;
        String s = dumpCircuit();
        stor.setItem("circuitRecovery", s);
    }

    void readRecoveryFromStorage() {
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null)
            return;
        recovery = stor.getItem("circuitRecovery");
    }

    void doExportAsUrl() {
        String dump = dumpCircuit();
        CirSimDialogCoordinator.setDialogShowing(new ExportAsUrlDialog(dump));
        CirSimDialogCoordinator.getDialogShowing().show();
    }

    void doOpenRunnerOutputTable() {
        String dump = dumpCircuit();
        String[] start = Window.Location.getHref().split("\\?");
        String query;
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor != null) {
            String key = "nonInteractiveDump-" + System.currentTimeMillis();
            stor.setItem(key, dump);
            query = "?runner=1&nonInteractive=1&nonInteractiveDumpKey=" + URL.encodeQueryString(key);
        } else {
            query = "?runner=1&nonInteractive=1&ctz=" + sim.compressForUrl(dump);
        }
        Window.open(start[0] + query, "_blank", "");
    }

    String getRunnerDumpFromStorage(String key) {
        if (key == null || key.length() == 0)
            return null;
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null)
            return null;
        String dump = stor.getItem(key);
        return dump;
    }

    void doExportAsText() {
        String dump = dumpCircuit();
        CirSimDialogCoordinator.setDialogShowing(new ExportAsTextDialog(sim, dump));
        CirSimDialogCoordinator.getDialogShowing().show();
    }

    void doExportAsSFCR() {
        CirSimDialogCoordinator.setDialogShowing(new ExportAsSFCRDialog(sim));
        CirSimDialogCoordinator.getDialogShowing().show();
    }

    void doExportAsLocalFile() {
        String dump = dumpCircuit();
        CirSimDialogCoordinator.setDialogShowing(new ExportAsLocalFileDialog(dump));
        CirSimDialogCoordinator.getDialogShowing().show();
    }

    String dumpCircuit() {
        int i;
        CustomLogicModel.clearDumpedFlags();
        CustomCompositeModel.clearDumpedFlags();
        DiodeModel.clearDumpedFlags();
        TransistorModel.clearDumpedFlags();

        String dump = sim.getImportExportHelper().dumpOptions();

        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            String m = ce.dumpModel();
            if (m != null && !m.isEmpty())
                dump += m + "\n";
            dump += sim.getImportExportHelper().getElementDumpWithUid(ce) + "\n";
        }
        for (i = 0; i != sim.scopeCount; i++) {
            String d = sim.scopes[i].dump();
            if (d != null)
                dump += d + "\n";
        }
        for (i = 0; i != sim.adjustables.size(); i++) {
            Adjustable adj = sim.adjustables.get(i);
            dump += "38 " + adj.dump() + "\n";
        }
        ActionScheduler scheduler = ActionScheduler.getInstance(sim);
        String schedulerDump = scheduler.dump();
        if (schedulerDump != null && !schedulerDump.isEmpty()) {
            dump += schedulerDump;
        }
        String hintsDump = HintRegistry.dumpAll();
        if (hintsDump != null && !hintsDump.isEmpty()) {
            dump += hintsDump;
        }
        if (sim.hintType != -1)
            dump += "h " + sim.hintType + " " + sim.hintItem1 + " " +
            sim.hintItem2 + "\n";
        return dump;
    }

    void readCircuit(String text, int flags) {
        if (text != null && !text.trim().isEmpty()) {
            String preview = text.length() > 200 ? text.substring(0, 200) : text;
            CirSim.console("readCircuit text preview: " + preview.replace("\n", "\\n"));
            CirSim.console("isSFCRFormat result: " + SFCRParser.isSFCRFormat(text));
        }

        if (SFCRParser.isSFCRFormat(text)) {
            String currentFile = sim.getSFCRDocumentManager().getCurrentCircuitFile();
            CirSim.console("Parsing mode: SFCRParser (SFCR format detected)" + (currentFile != null ? " - " + currentFile : ""));
            readCircuit(new byte[0], flags);

            SFCRParser parser = new SFCRParser(sim);
            if (parser.parse(text)) {
                CirSim.console("Loaded SFCR model with " + parser.getCreatedElements().size() + " elements");
                sim.getSFCRDocumentManager().setModelInfoSourceText(text);
                sim.getSFCRDocumentManager().setModelInfoContent(InfoViewerContentBuilder.buildModelInfoMarkdown(text, parser.getInfoContent()));
                String editorContent = sim.getModelInfoEditorContent();
                sim.getSFCRDocumentManager().refreshModelInfoMenuItems();

                if (RuntimeMode.isGwt() && sim.autoOpenModelInfoOnLoad && editorContent != null && !editorContent.isEmpty())
                    sim.getInfoDialogActions().doViewModelInfo();

                java.util.ArrayList<String> rawLines = parser.getRawCircuitLines();
                if (!rawLines.isEmpty()) {
                    CirSim.console("Processing " + rawLines.size() + " raw circuit elements");
                    for (String line : rawLines) {
                        try {
                            processCircuitLine(line);
                        } catch (Exception e) {
                            CirSim.console("Error processing circuit line: " + line + " - " + e.getMessage());
                        }
                    }
                }

                if (RuntimeMode.isGwt())
                    parser.applyParsedScopes();

                if ((flags & CirSim.RC_NO_CENTER) == 0) {
                    ViewportElm viewportElm = sim.getViewportController().findViewportElm();
                    if (viewportElm != null) {
                        sim.getViewportController().applyViewportTransform(viewportElm);
                    } else {
                        sim.getViewportController().centreCircuit();
                    }
                }

                sim.needAnalyze();
            } else {
                CirSim.console("Failed to parse SFCR model");
            }
            if ((flags & CirSim.RC_KEEP_TITLE) == 0)
                sim.clearCircuitTitle();
            return;
        }

        if (text != null && !text.trim().isEmpty()) {
            sim.getSFCRDocumentManager().clearModelInfo();
            String currentFile = sim.getSFCRDocumentManager().getCurrentCircuitFile();
            CirSim.console("Parsing mode: Standard circuit format" + (currentFile != null ? " - " + currentFile : ""));
        }
        readCircuit(text.getBytes(), flags);
        if ((flags & CirSim.RC_KEEP_TITLE) == 0)
            sim.clearCircuitTitle();
    }

    void readCircuit(String text) {
        readCircuit(text, 0);
    }

    void processCircuitLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }

        line = line.trim();

        StringTokenizer st = new StringTokenizer(line);
        String type = st.nextToken();
        int tint = type.charAt(0);
        if (tint > 127 && tint < 256)
            tint = type.charAt(0) - 256;

        try {
            if (type.length() > 1 || !Character.isLetter(type.charAt(0))) {
                tint = Integer.parseInt(type);
            }

            int x1 = Integer.parseInt(st.nextToken());
            int y1 = Integer.parseInt(st.nextToken());
            int x2 = Integer.parseInt(st.nextToken());
            int y2 = Integer.parseInt(st.nextToken());
            int f = Integer.parseInt(st.nextToken());

            ImportExportHelper.ElementDumpParseResult parsed = sim.getImportExportHelper().parseElementTokensWithUid(st);
            CircuitElm newce = ElementFactoryFacade.createFromDumpType(tint, x1, y1, x2, y2, f, parsed.tokenizer);
            if (newce == null) {
                CirSim.console("Unrecognized element type: " + type);
                return;
            }

            newce.setPoints();
            sim.getImportExportHelper().assignPersistentUid(newce, parsed.uid);
            sim.elmList.addElement(newce);

        } catch (Exception e) {
            CirSim.console("Error parsing circuit line: " + line + " - " + e.getMessage());
        }
    }

    void readSetupFile(String str, String title) {
        String[] candidates;
        if (str.indexOf('/') >= 0)
            candidates = new String[] { str };
        else
            candidates = new String[] { str, "economics/" + str, "electronics/" + str };
        readSetupFileCandidates(candidates, 0, title);
    }

    void readSetupFileCandidates(final String[] candidates, final int index, final String title) {
        if (index >= candidates.length) {
            sim.alertOrWarn(Locale.LS("Can't load circuit!"));
            return;
        }
        final String circuitPath = candidates[index];
        String url = GWT.getModuleBaseURL() + "circuits/" + circuitPath;
        CirSim.console("Loading circuit file: circuits/" + circuitPath);
        loadFileFromURL(url, new Command() {
            public void execute() {
                if (title != null)
                    sim.setCircuitTitle(title);
                sim.unsavedChanges = false;
                sim.getSFCRDocumentManager().setCurrentCircuitFile("circuits/" + circuitPath);
            }
        }, new Command() {
            public void execute() {
                readSetupFileCandidates(candidates, index + 1, title);
            }
        });
    }

    void loadFileFromURL(String url, final Command successCallback, final Command failureCallback) {
        final String loadUrl = getLoadUrl(url);
        CirSim.console("loadFileFromURL request: " + loadUrl);
        final boolean[] completed = new boolean[] { false };
        final Timer watchdog5s = new Timer() {
            @Override
            public void run() {
                if (!completed[0])
                    CirSim.console("loadFileFromURL still waiting after 5s: " + loadUrl);
            }
        };
        final Timer watchdog15s = new Timer() {
            @Override
            public void run() {
                if (completed[0])
                    return;
                completed[0] = true;
                CirSim.console("loadFileFromURL timeout after 15s: " + loadUrl);
                if (failureCallback != null)
                    failureCallback.execute();
            }
        };
        watchdog5s.schedule(5000);
        watchdog15s.schedule(15000);
        RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.GET, loadUrl);
        requestBuilder.setTimeoutMillis(15000);

        try {
            Request activeRequest = requestBuilder.sendRequest(null, new RequestCallback() {
                public void onError(Request request, Throwable exception) {
                    try {
                        if (completed[0])
                            return;
                        completed[0] = true;
                        watchdog5s.cancel();
                        watchdog15s.cancel();
                        String msg = "loadFileFromURL onError: " + loadUrl;
                        if (exception != null && exception.getMessage() != null)
                            msg += " - " + exception.getMessage();
                        CirSim.console(msg);
                        if (failureCallback != null)
                            failureCallback.execute();
                    } catch (Throwable t) {
                        CirSim.console("loadFileFromURL onError callback exception: " + t);
                    }
                }

                public void onResponseReceived(Request request, Response response) {
                    boolean circuitLoaded = false;
                    boolean httpSuccess = false;
                    try {
                        if (completed[0])
                            return;
                        completed[0] = true;
                        watchdog5s.cancel();
                        watchdog15s.cancel();
                        if (response.getStatusCode()==Response.SC_OK) {
                            httpSuccess = true;
                            circuitLoaded = true;
                            CirSim.console("loadFileFromURL success: " + loadUrl + " status=" + response.getStatusCode());
                            String text = response.getText();
                            try {
                                readCircuit(text, CirSim.RC_KEEP_TITLE);
                            } catch (Throwable readEx) {
                                CirSim.console("loadFileFromURL readCircuit exception: " + readEx);
                            }
                            if (RuntimeMode.isGwt())
                                try {
                                    sim.getUiPanelManager().allowSave(false);
                                } catch (Throwable saveEx) {
                                    CirSim.console("loadFileFromURL allowSave exception: " + saveEx);
                                }
                            sim.unsavedChanges = false;
                            if (successCallback != null)
                                try {
                                    successCallback.execute();
                                } catch (Throwable successEx) {
                                    CirSim.console("loadFileFromURL success callback exception: " + successEx);
                                }
                        }
                        else {
                            CirSim.console("loadFileFromURL HTTP failure: " + loadUrl +
                                " status=" + response.getStatusCode() +
                                " text=" + response.getStatusText());
                            if (failureCallback != null)
                                failureCallback.execute();
                        }
                    } catch (Throwable t) {
                        CirSim.console("loadFileFromURL onResponse callback exception: " + t);
                        if (!circuitLoaded && !httpSuccess && failureCallback != null)
                            failureCallback.execute();
                    }
                }
            });
            CirSim.console("loadFileFromURL sendRequest returned" + (activeRequest == null ? " (null request)" : ""));
        } catch (Throwable e) {
            if (completed[0])
                return;
            completed[0] = true;
            watchdog5s.cancel();
            watchdog15s.cancel();
            CirSim.console("loadFileFromURL request exception: " + loadUrl +
                (e.getMessage() != null ? " - " + e.getMessage() : ""));
            if (failureCallback != null)
                failureCallback.execute();
        }

    }

    String getLoadUrl(String url) {
        String result = url;
        if (RuntimeMode.isNonInteractiveRuntime())
            result = result + (result.indexOf('?') >= 0 ? "&" : "?") + "nonInteractive=1";
        if (!sim.enableCacheBustedUrls)
            return result;
        return result + (result.indexOf('?') >= 0 ? "&" : "?") + "v=" + System.currentTimeMillis();
    }

    void readCircuit(byte b[], int flags) {
        int i;
        int len = b.length;
        boolean transformLoaded = false;
        if ((flags & CirSim.RC_RETAIN) == 0) {
            StockFlowRegistry.clearRegistry();
            ComputedValues.clearMasterTables();
            ComputedValues.clearComputedValues();
            LookupTableRegistry.clear();
            HintRegistry.clear();
            ActionScheduler scheduler = ActionScheduler.getInstance(sim);
            scheduler.clearAll();

            sim.getMouseInputHandler().clearMouseElm();
            for (i = 0; i != sim.elmList.size(); i++) {
                CircuitElm ce = sim.getElm(i);
                ce.delete();
            }
            sim.getTimingState().t = sim.getTimingState().timeStepAccum = 0;
            sim.elmList.removeAllElements();
            sim.hintType = -1;
            sim.getTimingState().maxTimeStep = (sim.currentToolbarType == CirSim.ToolbarType.ECONOMICS) ? 0.01 : 5e-6;
            sim.getTimingState().minTimeStep = 50e-12;
            if (sim.dotsCheckItem != null)
                sim.dotsCheckItem.setState(false);
            if (sim.smallGridCheckItem != null)
                sim.smallGridCheckItem.setState(false);
            if (sim.powerCheckItem != null)
                sim.powerCheckItem.setState(false);
            if (sim.voltsCheckItem != null)
                sim.voltsCheckItem.setState(true);
            if (sim.showValuesCheckItem != null)
                sim.showValuesCheckItem.setState(true);
            sim.getPreferencesManager().setGrid();
            sim.setDefaultControlBars();
            CircuitElm.voltageRange = 5;
            sim.scopeCount = 0;
            sim.lastIterTime = 0;
            sim.voltageUnitSymbol = (sim.currentToolbarType == CirSim.ToolbarType.ECONOMICS) ? "$" : "V";
            sim.timeUnitSymbol = (sim.currentToolbarType == CirSim.ToolbarType.ECONOMICS) ? "yr" : "s";
        }
        boolean subs = (flags & CirSim.RC_SUBCIRCUITS) != 0;
        int p;
        for (p = 0; p < len; ) {
            int l;
            int linelen = len-p;
            for (l = 0; l != len-p; l++)
                if (b[l+p] == '\n' || b[l+p] == '\r') {
                    linelen = l++;
                    if (l+p < b.length && b[l+p] == '\n')
                        l++;
                    break;
                }
            String line = new String(b, p, linelen);
            StringTokenizer st = new StringTokenizer(line, " +\t\n\r\f");
            while (st.hasMoreTokens()) {
                String type = st.nextToken();
                int tint = type.charAt(0);
                try {
                    if (subs && tint != '.')
                        continue;
                    if (tint == 'o') {
                        Scope sc = new Scope(sim);
                        sc.position = sim.scopeCount;
                        sc.undump(st);
                        sim.scopes[sim.scopeCount++] = sc;
                        break;
                    }
                    if (tint == 'h') {
                        sim.readHint(st);
                        break;
                    }
                    if (tint == '$') {
                        sim.readOptions(st, flags);
                        break;
                    }
                    if (tint == '!') {
                        CustomLogicModel.undumpModel(st);
                        break;
                    }
                    if (tint == '%') {
                        if (st.hasMoreTokens()) {
                            String settingType = st.nextToken();
                            if (settingType.equals("voltageUnit") && st.hasMoreTokens()) {
                                sim.voltageUnitSymbol = CustomLogicModel.unescape(st.nextToken());
                            } else if (settingType.equals("viewport") && st.hasMoreTokens()) {
                                try {
                                    int viewMinX = Integer.parseInt(st.nextToken());
                                    int viewMinY = Integer.parseInt(st.nextToken());
                                    int viewMaxX = Integer.parseInt(st.nextToken());
                                    int viewMaxY = Integer.parseInt(st.nextToken());

                                    sim.getViewportController().setCircuitArea();
                                    int viewWidth = viewMaxX - viewMinX;
                                    int viewHeight = viewMaxY - viewMinY;

                                    if (viewWidth > 0 && viewHeight > 0) {
                                        double scaleX = (double)sim.circuitArea.width / viewWidth;
                                        double scaleY = (double)sim.circuitArea.height / viewHeight;
                                        double scale = Math.min(scaleX, scaleY);

                                        double translateX = (sim.circuitArea.width - viewWidth * scale) / 2 - viewMinX * scale;
                                        double translateY = (sim.circuitArea.height - viewHeight * scale) / 2 - viewMinY * scale;

                                        sim.getViewportController().setTransform(scale, translateX, translateY);
                                        transformLoaded = true;
                                    }
                                } catch (Exception e) {
                                }
                            } else if (settingType.equals("transform") && st.hasMoreTokens()) {
                                try {
                                    double scale = Double.parseDouble(st.nextToken());
                                    double translateX = Double.parseDouble(st.nextToken());
                                    double translateY = Double.parseDouble(st.nextToken());
                                    sim.getViewportController().setTransform(scale, translateX, translateY);
                                    transformLoaded = true;
                                } catch (Exception e) {
                                }
                            } else if (settingType.equals("showToolbar") && st.hasMoreTokens()) {
                                String value = st.nextToken();
                                if (sim.toolbarCheckItem != null) {
                                    sim.toolbarCheckItem.setState(value.equals("true"));
                                    sim.setToolbar();
                                }
                            } else if (settingType.equals("equationTableMnaMode") && st.hasMoreTokens()) {
                                sim.equationTableMnaMode = st.nextToken().equals("true");
                            } else if (settingType.equals("equationTableNewtonJacobianEnabled") && st.hasMoreTokens()) {
                                sim.equationTableNewtonJacobianEnabled = st.nextToken().equals("true");
                            } else if (settingType.equals("equationTableConvergenceTolerance") && st.hasMoreTokens()) {
                                try {
                                    sim.equationTableConvergenceTolerance = Double.parseDouble(st.nextToken());
                                } catch (Exception e) {
                                }
                            } else if (settingType.equals("sfcrLookupClampDefault") && st.hasMoreTokens()) {
                                sim.sfcrLookupClampDefault = st.nextToken().equals("true");
                            } else if (settingType.equals("convergenceCheckThreshold") && st.hasMoreTokens()) {
                                try {
                                    sim.convergenceCheckThreshold = Integer.parseInt(st.nextToken());
                                } catch (Exception e) {
                                }
                            } else if (settingType.equals("AS") || settingType.equals("AST")) {
                                ActionScheduler scheduler = ActionScheduler.getInstance(sim);
                                scheduler.load(line);
                            } else if (settingType.equals("Hint")) {
                                HintRegistry.parseHintLine(line);
                            } else if (settingType.equals("lookup") && st.hasMoreTokens()) {
                                LookupDefinition def = new LookupDefinition();
                                def.name = st.nextToken();
                                while (st.hasMoreTokens()) {
                                    String tok = st.nextToken();
                                    if (tok.startsWith("scope=")) {
                                        def.scope = tok.substring(6).trim();
                                    } else {
                                        int comma = tok.indexOf(',');
                                        if (comma > 0) {
                                            try {
                                                def.xs.add(Double.valueOf(Double.parseDouble(tok.substring(0, comma))));
                                                def.ys.add(Double.valueOf(Double.parseDouble(tok.substring(comma + 1))));
                                            } catch (Exception e) { }
                                        }
                                    }
                                }
                                if (def.name != null && !def.name.isEmpty() && def.xs.size() >= 2) {
                                    LookupTableRegistry.register(def);
                                }
                            }
                        }
                        break;
                    }
                    if (tint == '?' || tint == 'B') {
                        break;
                    }

                    if (tint >= '0' && tint <= '9')
                        tint = Integer.parseInt(type);

                    if (tint == 34) {
                        DiodeModel.undumpModel(st);
                        break;
                    }
                    if (tint == 32) {
                        TransistorModel.undumpModel(st);
                        break;
                    }
                    if (tint == 38) {
                        Adjustable adj = new Adjustable(st, sim);
                        if (adj.elm != null)
                            sim.adjustables.add(adj);
                        break;
                    }
                    if (tint == '.') {
                        CustomCompositeModel.undumpModel(st);
                        break;
                    }
                    int x1 = Integer.parseInt(st.nextToken());
                    int y1 = Integer.parseInt(st.nextToken());
                    int x2 = Integer.parseInt(st.nextToken());
                    int y2 = Integer.parseInt(st.nextToken());
                    int f  = Integer.parseInt(st.nextToken());
                    ImportExportHelper.ElementDumpParseResult parsed = sim.getImportExportHelper().parseElementTokensWithUid(st);
                    CircuitElm newce = ElementFactoryFacade.createFromDumpType(tint, x1, y1, x2, y2, f, parsed.tokenizer);
                    if (newce==null) {
                        System.out.println("unrecognized dump type: " + type);
                        break;
                    }
                    newce.setPoints();
                    sim.getImportExportHelper().assignPersistentUid(newce, parsed.uid);
                    sim.elmList.addElement(newce);
                } catch (Exception ee) {
                    ee.printStackTrace();
                    CirSim.console("exception while undumping " + ee);
                    break;
                }
                break;
            }
            p += l;

        }
        if (RuntimeMode.isGwt()) {
            sim.setPowerBarEnable();
            sim.enableItems();
        }
        if ((flags & CirSim.RC_RETAIN) == 0) {
            if (RuntimeMode.isGwt()) {
                for (i = 0; i < sim.adjustables.size(); i++) {
                    if (!sim.adjustables.get(i).createSlider(sim))
                        sim.adjustables.remove(i--);
                }
            }
        }
        sim.needAnalyze();

        if ((flags & CirSim.RC_NO_CENTER) == 0 && !transformLoaded) {
            ViewportElm viewportElm = sim.getViewportController().findViewportElm();
            if (viewportElm != null) {
                sim.getViewportController().applyViewportTransform(viewportElm);
            } else {
                sim.getViewportController().centreCircuit();
            }
        }
        if ((flags & CirSim.RC_SUBCIRCUITS) != 0)
            sim.updateModels();

        if ((flags & CirSim.RC_RETAIN) == 0) {
            sim.resetAction();
        }

        StockFlowRegistry.synchronizeAllTables();

        AudioInputElm.clearCache();
        DataInputElm.clearCache();
    }
}
