package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.RootPanel;

final class RunnerController {
    private static final int RUNNER_LIVE_BATCH_SIZE = 5;

    private final CirSim sim;

    private boolean runnerLiveMode;
    private int asyncRunStep;
    private int asyncRunTotalSteps;
    private int asyncRunCompletedSteps;
    private String asyncRunSource;
    private String asyncRunFormat;
    private List<String> asyncRunKeys;
    private StringBuilder asyncRunOutput;
    private StringBuilder asyncRunTableContent;
    private boolean asyncWarnedNoTimeAdvance;

    RunnerController(CirSim sim) {
        this.sim = sim;
    }

    void setRunnerLiveMode(boolean runnerLiveMode) {
        this.runnerLiveMode = runnerLiveMode;
    }

    void launchFromQuery(QueryParameters qp) {
        String cct = normalizeOptionalQueryValue(qp.getValue("cct"));
        String startCircuitText = null;
        if (cct != null)
            startCircuitText = cct.replace("%24", "$");
        if (startCircuitText == null)
            startCircuitText = sim.getPlatformInterop().getElectronStartCircuitText();
        String ctz = normalizeOptionalQueryValue(qp.getValue("ctz"));
        if (ctz != null)
            startCircuitText = sim.decompress(ctz);
        String nonInteractiveDumpKey = normalizeOptionalQueryValue(qp.getValue("nonInteractiveDumpKey"));
        if (startCircuitText == null && nonInteractiveDumpKey != null) {
            startCircuitText = sim.getCircuitIOService().getRunnerDumpFromStorage(nonInteractiveDumpKey);
            if (startCircuitText == null)
                CirSim.console("Runner dump key not found in localStorage: " + nonInteractiveDumpKey);
        }
        String startCircuit = normalizeOptionalQueryValue(qp.getValue("startCircuit"));
        String startLabel = normalizeOptionalQueryValue(qp.getValue("startLabel"));
        String stepsValue = normalizeOptionalQueryValue(qp.getValue("steps"));
        boolean useRunnerQuickStartDefault =
            startCircuitText == null && nonInteractiveDumpKey == null &&
            (startCircuit == null || startCircuit.length() == 0);
        if (useRunnerQuickStartDefault) {
            startCircuit = "circuitjs1/circuits/economics/1debug.md";
            if (stepsValue == null)
                stepsValue = "1";
        }
        int defaultSteps = RunnerLaunchDecision.resolveDefaultSteps(nonInteractiveDumpKey, stepsValue);
        int steps = parsePositiveInt(stepsValue, defaultSteps);
        boolean requestedRunnerLiveMode = qp.getBooleanValue("runnerLive", true);
        String format = normalizeOptionalQueryValue(qp.getValue("format"));
        if (format == null)
            format = "tsv";
        format = format.toLowerCase();
        if (!(("csv".equals(format) || "tsv".equals(format) || "world2".equals(format))))
            format = "tsv";
        boolean effectiveRunnerLiveMode = requestedRunnerLiveMode;
        if ("world2".equals(format) && requestedRunnerLiveMode) {
            effectiveRunnerLiveMode = false;
            CirSim.console("Runner note: format=world2 requires non-live rendering; forcing runnerLive=0.");
        }
        setRunnerLiveMode(effectiveRunnerLiveMode);
        CirSim.console("Runner params: startCircuit=" + startCircuit + ", steps=" + steps + ", format=" + format +
            ", hasCct=" + (startCircuitText != null && startCircuitText.length() > 0) +
            ", runnerLive=" + (runnerLiveMode ? "1" : "0"));

        RunnerPanelUi.renderRunnerStatus("Loading circuit...");

        RunnerLaunchDecision.ImmediateRoute immediateRoute =
            RunnerLaunchDecision.resolveImmediateRoute(startCircuitText, nonInteractiveDumpKey);
        if (immediateRoute == RunnerLaunchDecision.ImmediateRoute.EMBEDDED_TEXT) {
            runRunnerFromText(startCircuitText, steps, "embedded", format);
            return;
        }
        if (immediateRoute == RunnerLaunchDecision.ImmediateRoute.MISSING_DUMP_KEY) {
            RunnerPanelUi.renderRunnerError("Runner dump not found for key '" + nonInteractiveDumpKey + "'. Re-open Runner Output from the simulator tab to generate a fresh key.");
            return;
        }
        if (immediateRoute == RunnerLaunchDecision.ImmediateRoute.NONE &&
            (startCircuit == null || startCircuit.length() == 0)) {
            RunnerPanelUi.renderRunnerError("No data source specified. Use startCircuit=..., cct=..., or ctz=...");
            return;
        }

        try {
            if (startCircuit != null && startCircuit.length() > 0) {
                CirSim.console("Runner dispatching load for startCircuit=" + startCircuit);
                loadRunnerSetupFile(startCircuit, startLabel, steps, format);
                return;
            }
            RunnerPanelUi.renderRunnerError("No data source specified. Use startCircuit=..., cct=..., or ctz=...");
        } catch (Throwable t) {
            CirSim.console("Runner launch dispatch exception: " + t);
            RunnerPanelUi.renderRunnerError("Runner launch failed: " + t);
        }
    }

    void loadRunnerTableSetupFile(String str, String title, final int steps) {
        String normalized = normalizeStartCircuitPath(str);
        String[] candidates;
        if (normalized.indexOf('/') >= 0)
            candidates = new String[] { normalized };
        else
            candidates = new String[] { normalized, "economics/" + normalized, "electronics/" + normalized };
        loadRunnerTableSetupFileCandidates(candidates, 0, str, title, steps);
    }

    private void loadRunnerTableSetupFileCandidates(final String[] candidates, final int index,
        final String requestedStartCircuit, final String title, final int steps) {
        if (index >= candidates.length) {
            CirSim.console("Runner table load failed for all candidates of startCircuit=" + requestedStartCircuit);
            RunnerPanelUi.renderRunnerTableError("Can't load circuit file: " + requestedStartCircuit);
            return;
        }

        final String circuitPath = candidates[index];
        String url = com.google.gwt.core.client.GWT.getModuleBaseURL() + "circuits/" + circuitPath;
        CirSim.console("Runner table trying circuit path: circuits/" + circuitPath);
        RunnerPanelUi.updateRunnerStatusMessage("Loading circuits/" + circuitPath + " ...");
        sim.loadFileFromURLRunnerWithCallbacks(url, new Runnable() {
            public void run() {
                CirSim.console("Runner table loaded circuit path: circuits/" + circuitPath);
                RunnerPanelUi.updateRunnerStatusMessage("Loaded circuits/" + circuitPath);
                if (title != null)
                    sim.setCircuitTitle(title);
                sim.currentCircuitFile = "circuits/" + circuitPath;
                runRunnerTableSimulation(steps, sim.currentCircuitFile);
            }
        }, new Runnable() {
            public void run() {
                CirSim.console("Runner table failed circuit path: circuits/" + circuitPath + " (trying next)");
                RunnerPanelUi.updateRunnerStatusMessage("Failed circuits/" + circuitPath + ", trying next...");
                loadRunnerTableSetupFileCandidates(candidates, index + 1, requestedStartCircuit, title, steps);
            }
        });
    }

    void loadRunnerSetupFile(String str, String title, final int steps, final String format) {
        String normalized = normalizeStartCircuitPath(str);
        String[] candidates;
        if (normalized.indexOf('/') >= 0)
            candidates = new String[] { normalized };
        else
            candidates = new String[] { normalized, "economics/" + normalized, "electronics/" + normalized };
        loadRunnerSetupFileCandidates(candidates, 0, str, title, steps, format);
    }

    private String normalizeStartCircuitPath(String path) {
        if (path == null)
            return "";
        String normalized = path.trim();
        while (normalized.startsWith("/"))
            normalized = normalized.substring(1);
        if (normalized.startsWith("war/"))
            normalized = normalized.substring(4);
        if (normalized.startsWith("circuitjs1/"))
            normalized = normalized.substring(11);
        if (normalized.startsWith("circuits/"))
            normalized = normalized.substring(9);
        return normalized;
    }

    private void loadRunnerSetupFileCandidates(final String[] candidates, final int index,
        final String requestedStartCircuit, final String title, final int steps, final String format) {
        if (index >= candidates.length) {
            CirSim.console("Runner load failed for all candidates of startCircuit=" + requestedStartCircuit);
            RunnerPanelUi.renderRunnerError("Can't load circuit file: " + requestedStartCircuit);
            return;
        }

        final String circuitPath = candidates[index];
        String url = com.google.gwt.core.client.GWT.getModuleBaseURL() + "circuits/" + circuitPath;
        CirSim.console("Runner trying circuit path: circuits/" + circuitPath);
        RunnerPanelUi.updateRunnerStatusMessage("Loading circuits/" + circuitPath + " ...");
        sim.loadFileFromURLRunnerWithCallbacks(url, new Runnable() {
            public void run() {
                CirSim.console("Runner loaded circuit path: circuits/" + circuitPath);
                RunnerPanelUi.updateRunnerStatusMessage("Loaded circuits/" + circuitPath);
                if (title != null)
                    sim.setCircuitTitle(title);
                sim.currentCircuitFile = "circuits/" + circuitPath;
                runRunnerSimulation(steps, sim.currentCircuitFile, format);
            }
        }, new Runnable() {
            public void run() {
                CirSim.console("Runner failed circuit path: circuits/" + circuitPath + " (trying next)");
                RunnerPanelUi.updateRunnerStatusMessage("Failed circuits/" + circuitPath + ", trying next...");
                loadRunnerSetupFileCandidates(candidates, index + 1, requestedStartCircuit, title, steps, format);
            }
        });
    }

    void runRunnerTableFromText(String circuitText, int steps, String source) {
        CirSim.console("Runner table loading embedded circuit text source=" + source + ", length=" + circuitText.length());
        sim.getCircuitIOService().readCircuit(circuitText, 0);
        sim.currentCircuitFile = source;
        runRunnerTableSimulation(steps, source);
    }

    void runRunnerFromText(String circuitText, int steps, String source, String format) {
        CirSim.console("Runner loading embedded circuit text source=" + source + ", length=" + circuitText.length());
        sim.getCircuitIOService().readCircuit(circuitText, 0);
        sim.currentCircuitFile = source;
        runRunnerSimulation(steps, source, format);
    }

    private String normalizeOptionalQueryValue(String value) {
        if (value == null)
            return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "undefined".equalsIgnoreCase(trimmed) || "null".equalsIgnoreCase(trimmed))
            return null;
        return trimmed;
    }

    private int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty())
            return defaultValue;
        try {
            int parsed = Integer.parseInt(value.trim());
            return (parsed > 0) ? parsed : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    void runRunnerSimulation(int steps, String source, String format) {
        CirSim.console("Runner simulation start: source=" + source + ", steps=" + steps + ", format=" + format);
        if (runnerLiveMode) {
            runRunnerSimulationAsync(steps, source, format);
            return;
        }
        sim.analyzeCircuit();
        sim.preStampAndStampCircuit();

        if (sim.stopMessage != null) {
            RunnerPanelUi.renderRunnerError("Analyze warning: " + sim.stopMessage);
            return;
        }

        SimulationExportCore.RunRequest request = new SimulationExportCore.RunRequest();
        request.circuitPath = source;
        request.outputPath = "(browser)";
        request.htmlPath = "(browser)";
        request.steps = steps;
        request.format = format;
        SimulationExportCore.RunResult runResult = SimulationExportCore.run(sim, request, new SimulationExportCore.Diagnostics() {
            public void log(String message) {
                CirSim.console(message);
            }
        });
        String outputText = runResult.outputText != null ? runResult.outputText : "";
        char separator = "tsv".equals(format) ? '\t' : ',';
        Set<String> stockNames = collectRunnerStockNames();

        StringBuilder content = new StringBuilder();
        content.append(SimulationExportCore.buildRunnerSummaryContentHtml(source, steps, format, runResult.rowsWritten));
        if (!runResult.world2Format) {
            content.append(SimulationExportCore.buildDelimitedHtmlReport(outputText, separator, source, steps, stockNames));
        } else {
            content.append(SimulationExportCore.buildRunnerWorld2RawOutputHtml(outputText));
        }

        String extraHtml = SimulationExportCore.buildRunnerWorld2ReportTabHtml(runResult.htmlReport);
        boolean includeExtraTab = false;
        String extraTabTitle = "";
        if (runResult.world2Format && runResult.htmlReport != null) {
            includeExtraTab = true;
            extraTabTitle = "World2 Report";
        }

        RootPanel.get().getElement().setInnerHTML(SimulationExportCore.buildRunnerTabbedHtml(
            "Runner Output", content.toString(), includeExtraTab, extraTabTitle, extraHtml, RunnerPanelUi.getRunnerStdoutHtml()));
    }

    void runRunnerTableSimulation(int steps, String source) {
        CirSim.console("Runner table simulation start: source=" + source + ", steps=" + steps);
        if (runnerLiveMode) {
            runRunnerTableSimulationAsync(steps, source);
            return;
        }
        sim.analyzeCircuit();
        sim.preStampAndStampCircuit();

        Set<String> registered = ComputedValues.getRegisteredComputedNames();
        List<String> keys = new ArrayList<String>(registered != null ? registered : Collections.<String>emptySet());
        Collections.sort(keys);

        StringBuilder content = new StringBuilder();
        content.append(SimulationExportCore.buildRunnerTableDiv("<b>Source:</b> " + SafeHtmlUtils.htmlEscape(source != null ? source : "(none)")));
        content.append(SimulationExportCore.buildRunnerTableDiv("<b>Requested steps:</b> " + steps));

        if (sim.stopMessage != null) {
            content.append(SimulationExportCore.buildRunnerTableStyledDiv("color:#c33; margin-top:8px;",
                "<b>Analyze warning:</b> " + SafeHtmlUtils.htmlEscape(sim.stopMessage)));
        }

        content.append(SimulationExportCore.buildRunnerTableWrapperOpen());
        content.append(SimulationExportCore.buildRunnerTableOpen());
        content.append(SimulationExportCore.buildRunnerTableHeader(keys));
        content.append(SimulationExportCore.buildRunnerTableBodyOpen());

        boolean warnedNoTimeAdvance = false;
        int completedSteps = 0;
        for (int step = 0; step < steps; step++) {
            double prevT = sim.t;
            sim.getSimulationLoop().runCircuit(step == 0);
            ComputedValues.commitConvergedValues();

            List<String> cells = new ArrayList<String>();
            cells.add(SimulationExportCore.buildRunnerTableCell(String.valueOf(sim.t)));
            for (int i = 0; i < keys.size(); i++) {
                Double value = ComputedValues.getConvergedValue(keys.get(i));
                cells.add(SimulationExportCore.buildRunnerTableCell(value != null ? String.valueOf(value) : ""));
            }
            content.append(SimulationExportCore.buildRunnerTableRow(cells));

            completedSteps++;

            if (!warnedNoTimeAdvance && sim.t == prevT)
                warnedNoTimeAdvance = true;
            if (sim.stopMessage != null)
                break;
        }

        content.append(SimulationExportCore.buildRunnerTableWrapperClose());
        content.append(SimulationExportCore.buildRunnerTableStyledDiv("margin-top:8px;", "<b>Completed steps:</b> " + completedSteps));
        if (warnedNoTimeAdvance) {
            content.append(SimulationExportCore.buildRunnerTableStyledDiv("color:#c77; margin-top:6px;",
                "Warning: simulation time did not advance in at least one step."));
        }
        if (sim.stopMessage != null) {
            content.append(SimulationExportCore.buildRunnerTableStyledDiv("color:#c33; margin-top:6px;",
                "<b>Simulation stopped:</b> " + SafeHtmlUtils.htmlEscape(sim.stopMessage)));
        }

        RootPanel.get().getElement().setInnerHTML(SimulationExportCore.buildRunnerTableTabbedHtml(
            "Output Table", content.toString(), RunnerPanelUi.getRunnerStdoutHtml()));
    }

    private void runRunnerSimulationAsync(int totalSteps, String source, String format) {
        CirSim.console("Runner async sim start: source=" + source + ", steps=" + totalSteps);
        sim.analyzeCircuit();
        sim.preStampAndStampCircuit();
        if (sim.stopMessage != null) {
            RunnerPanelUi.renderRunnerError("Analyze warning: " + sim.stopMessage);
            return;
        }
        Set<String> registered = ComputedValues.getRegisteredComputedNames();
        asyncRunKeys = new ArrayList<String>(registered != null ? registered : Collections.<String>emptySet());
        Collections.sort(asyncRunKeys);
        asyncRunStep = 0;
        asyncRunTotalSteps = totalSteps;
        asyncRunCompletedSteps = 0;
        asyncRunSource = source;
        asyncRunFormat = format;
        asyncRunOutput = new StringBuilder();
        asyncWarnedNoTimeAdvance = false;
        char sep = "tsv".equals(format) ? '\t' : ',';
        asyncRunOutput.append("t");
        for (int i = 0; i < asyncRunKeys.size(); i++)
            asyncRunOutput.append(sep).append(asyncRunKeys.get(i));
        asyncRunOutput.append('\n');
        scheduleNextRunnerSimChunk();
    }

    private void scheduleNextRunnerSimChunk() {
        RunnerPanelUi.renderRunnerStatus("Running step " + asyncRunStep + " / " + asyncRunTotalSteps + "...");
        RunnerJsBridge.setRunnerStepFn(new Runnable() {
            public void run() {
                runRunnerSimChunk();
            }
        });
    }

    private void scheduleNextRunnerTableChunk() {
        RunnerJsBridge.setRunnerStepFn(new Runnable() {
            public void run() {
                runRunnerTableChunk();
            }
        });
    }

    private void runRunnerSimChunk() {
        try {
            runRunnerSimChunkInner();
        } catch (Throwable ex) {
            CirSim.console("runRunnerSimChunk exception: " + ex);
            try {
                RunnerPanelUi.renderRunnerError("Async runner error at step " + asyncRunStep + ": " + ex);
            } catch (Throwable ex2) {
                CirSim.console("renderRunnerError also threw: " + ex2);
            }
        }
    }

    private void runRunnerSimChunkInner() {
        char sep = "tsv".equals(asyncRunFormat) ? '\t' : ',';
        int batchEnd = Math.min(asyncRunStep + RUNNER_LIVE_BATCH_SIZE, asyncRunTotalSteps);
        for (int step = asyncRunStep; step < batchEnd; step++) {
            double prevT = sim.t;
            sim.getSimulationLoop().runCircuit(step == 0);
            ComputedValues.commitConvergedValues();
            asyncRunOutput.append(sim.t);
            for (int i = 0; i < asyncRunKeys.size(); i++) {
                Double v = ComputedValues.getConvergedValue(asyncRunKeys.get(i));
                asyncRunOutput.append(sep).append(v != null ? String.valueOf(v) : "");
            }
            asyncRunOutput.append('\n');
            asyncRunCompletedSteps++;
            if (!asyncWarnedNoTimeAdvance && sim.t == prevT)
                asyncWarnedNoTimeAdvance = true;
            if (sim.stopMessage != null)
                break;
        }
        asyncRunStep = batchEnd;
        if (asyncRunStep < asyncRunTotalSteps && sim.stopMessage == null) {
            scheduleNextRunnerSimChunk();
            return;
        }
        RunnerJsBridge.setRunnerStepFn(null);
        String outputText = asyncRunOutput.toString();
        char finalSep = "tsv".equals(asyncRunFormat) ? '\t' : ',';
        Set<String> stockNames = collectRunnerStockNames();
        StringBuilder finalContent = new StringBuilder();
        finalContent.append(SimulationExportCore.buildRunnerSummaryContentHtml(
            asyncRunSource, asyncRunTotalSteps, asyncRunFormat, asyncRunCompletedSteps));
        finalContent.append(SimulationExportCore.buildDelimitedHtmlReport(
            outputText, finalSep, asyncRunSource, asyncRunTotalSteps, stockNames));
        RootPanel.get().getElement().setInnerHTML(SimulationExportCore.buildRunnerTabbedHtml(
            "Runner Output", finalContent.toString(), false, "", "", RunnerPanelUi.getRunnerStdoutHtml()));
    }

    private Set<String> collectRunnerStockNames() {
        java.util.HashSet<String> stocks = new java.util.HashSet<String>();
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (!(ce instanceof EquationTableElm)) {
                continue;
            }
            EquationTableElm table = (EquationTableElm) ce;
            int rowCount = table.getRowCount();
            for (int row = 0; row < rowCount; row++) {
                if (table.isCommentRow(row)) {
                    continue;
                }
                String outputName = table.getOutputName(row);
                String equation = table.getEquation(row);
                if (outputName == null || outputName.trim().isEmpty()) {
                    continue;
                }
                if (EquationTableElm.isStockEquation(outputName, equation)) {
                    stocks.add(outputName.trim());
                }
            }
        }
        return stocks;
    }

    private void runRunnerTableSimulationAsync(int totalSteps, String source) {
        CirSim.console("Runner table async sim start: source=" + source + ", steps=" + totalSteps);
        sim.analyzeCircuit();
        sim.preStampAndStampCircuit();
        Set<String> registered = ComputedValues.getRegisteredComputedNames();
        asyncRunKeys = new ArrayList<String>(registered != null ? registered : Collections.<String>emptySet());
        Collections.sort(asyncRunKeys);
        asyncRunStep = 0;
        asyncRunTotalSteps = totalSteps;
        asyncRunCompletedSteps = 0;
        asyncRunSource = source;
        asyncWarnedNoTimeAdvance = false;
        asyncRunTableContent = new StringBuilder();
        asyncRunTableContent.append(SimulationExportCore.buildRunnerTableDiv(
            "<b>Source:</b> " + SafeHtmlUtils.htmlEscape(source != null ? source : "(none)")));
        asyncRunTableContent.append(SimulationExportCore.buildRunnerTableDiv(
            "<b>Requested steps:</b> " + totalSteps));
        if (sim.stopMessage != null) {
            asyncRunTableContent.append(SimulationExportCore.buildRunnerTableStyledDiv(
                "color:#c33; margin-top:8px;",
                "<b>Analyze warning:</b> " + SafeHtmlUtils.htmlEscape(sim.stopMessage)));
        }
        asyncRunTableContent.append(SimulationExportCore.buildRunnerTableWrapperOpen());
        asyncRunTableContent.append(SimulationExportCore.buildRunnerTableOpen());
        asyncRunTableContent.append(SimulationExportCore.buildRunnerTableHeader(asyncRunKeys));
        asyncRunTableContent.append(SimulationExportCore.buildRunnerTableBodyOpen());
        scheduleNextRunnerTableChunk();
    }

    private void runRunnerTableChunk() {
        try {
            runRunnerTableChunkInner();
        } catch (Throwable ex) {
            RunnerPanelUi.renderRunnerTableError("Async table runner error at step " + asyncRunStep + ": " + ex);
        }
    }

    private void runRunnerTableChunkInner() {
        int batchEnd = Math.min(asyncRunStep + RUNNER_LIVE_BATCH_SIZE, asyncRunTotalSteps);
        for (int step = asyncRunStep; step < batchEnd; step++) {
            double prevT = sim.t;
            sim.getSimulationLoop().runCircuit(step == 0);
            ComputedValues.commitConvergedValues();
            List<String> cells = new ArrayList<String>();
            cells.add(SimulationExportCore.buildRunnerTableCell(String.valueOf(sim.t)));
            for (int i = 0; i < asyncRunKeys.size(); i++) {
                Double v = ComputedValues.getConvergedValue(asyncRunKeys.get(i));
                cells.add(SimulationExportCore.buildRunnerTableCell(v != null ? String.valueOf(v) : ""));
            }
            asyncRunTableContent.append(SimulationExportCore.buildRunnerTableRow(cells));
            asyncRunCompletedSteps++;
            if (!asyncWarnedNoTimeAdvance && sim.t == prevT)
                asyncWarnedNoTimeAdvance = true;
            if (sim.stopMessage != null)
                break;
        }
        asyncRunStep = batchEnd;
        if (asyncRunStep < asyncRunTotalSteps && sim.stopMessage == null) {
            scheduleNextRunnerTableChunk();
            return;
        }
        asyncRunTableContent.append(SimulationExportCore.buildRunnerTableWrapperClose());
        asyncRunTableContent.append(SimulationExportCore.buildRunnerTableStyledDiv(
            "margin-top:8px;", "<b>Completed steps:</b> " + asyncRunCompletedSteps));
        if (asyncWarnedNoTimeAdvance)
            asyncRunTableContent.append(SimulationExportCore.buildRunnerTableStyledDiv(
                "color:#c77; margin-top:6px;",
                "Warning: simulation time did not advance in at least one step."));
        if (sim.stopMessage != null)
            asyncRunTableContent.append(SimulationExportCore.buildRunnerTableStyledDiv(
                "color:#c33; margin-top:6px;",
                "<b>Simulation stopped:</b> " + SafeHtmlUtils.htmlEscape(sim.stopMessage)));
        RunnerJsBridge.setRunnerStepFn(null);
        RootPanel.get().getElement().setInnerHTML(SimulationExportCore.buildRunnerTableTabbedHtml(
            "Output Table", asyncRunTableContent.toString(), RunnerPanelUi.getRunnerStdoutHtml()));
    }
}
