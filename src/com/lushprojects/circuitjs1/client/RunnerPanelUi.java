package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.dom.client.Document;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.RootPanel;

final class RunnerPanelUi {
    private static final int RUNNER_STDOUT_MAX_LINES = 2000;
    private static boolean runnerStdoutEnabled = false;
    private static final ArrayList<String> runnerStdoutLines = new ArrayList<String>();

    private RunnerPanelUi() {
    }

    static boolean isRunnerStdoutEnabled() {
        return runnerStdoutEnabled;
    }

    static void setRunnerStdoutEnabled(boolean enabled) {
        runnerStdoutEnabled = enabled;
    }

    static void clearRunnerStdout() {
        runnerStdoutLines.clear();
    }

    static void appendRunnerStdout(String text) {
        if (text == null)
            return;
        runnerStdoutLines.add(text);
        if (runnerStdoutLines.size() > RUNNER_STDOUT_MAX_LINES)
            runnerStdoutLines.remove(0);
        appendRunnerStdoutDomLine(SafeHtmlUtils.htmlEscape(text));
    }

    static String getRunnerStdoutHtml() {
        if (runnerStdoutLines.isEmpty())
            return SafeHtmlUtils.htmlEscape("(no output yet)");
        List<String> escapedLines = new ArrayList<String>();
        for (int i = 0; i < runnerStdoutLines.size(); i++) {
            escapedLines.add(SafeHtmlUtils.htmlEscape(runnerStdoutLines.get(i)));
        }
        return String.join("<br/>", escapedLines);
    }

    private static void appendRunnerStdoutDomLine(String escapedLine) {
        com.google.gwt.dom.client.Element pane = Document.get().getElementById("runner-stdout-pre");
        if (pane == null)
            return;
        String html = pane.getInnerHTML();
        if ("(no output yet)".equals(html))
            html = "";
        if (html.length() > 0)
            html += "<br/>";
        html += escapedLine;
        pane.setInnerHTML(html);
        pane.setScrollTop(pane.getScrollHeight());
    }

    static void updateRunnerStatusMessage(String message) {
        com.google.gwt.dom.client.Element el = Document.get().getElementById("runner-status-message");
        if (el != null)
            el.setInnerText(message != null ? message : "");
    }

    static void renderRunnerTableStatus(String message) {
        String content = SimulationExportCore.buildRunnerTableStatusContentHtml(message);
        RootPanel.get().getElement().setInnerHTML(SimulationExportCore.buildRunnerTableTabbedHtml(
            "Output Table", content, getRunnerStdoutHtml()));
    }

    static void renderRunnerStatus(String message) {
        String content = SimulationExportCore.buildRunnerStatusContentHtml(message);
        RootPanel.get().getElement().setInnerHTML(SimulationExportCore.buildRunnerTabbedHtml(
            "Runner Output", content, false, "", "", getRunnerStdoutHtml()));
    }

    static void renderRunnerTableError(String message) {
        String content = SimulationExportCore.buildRunnerTableErrorContentHtml(message);
        RootPanel.get().getElement().setInnerHTML(SimulationExportCore.buildRunnerTableTabbedHtml(
            "Output Table", content, getRunnerStdoutHtml()));
    }

    static void renderRunnerError(String message) {
        String content = SimulationExportCore.buildRunnerErrorContentHtml(message);
        RootPanel.get().getElement().setInnerHTML(SimulationExportCore.buildRunnerTabbedHtml(
            "Runner Output", content, false, "", "", getRunnerStdoutHtml()));
    }
}