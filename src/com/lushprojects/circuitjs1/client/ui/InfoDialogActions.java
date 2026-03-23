package com.lushprojects.circuitjs1.client.ui;
import com.lushprojects.circuitjs1.client.*;

import com.lushprojects.circuitjs1.client.CirSim;

public class InfoDialogActions {
    private final CirSim sim;

    public InfoDialogActions(CirSim sim) {
        this.sim = sim;
    }

    public void doViewModelInfo() {
        String editorContent = sim.getModelInfoEditorContent();
        if (editorContent != null && !editorContent.isEmpty()) {
            try {
                InfoViewerDialog.showInfoInWindow("Model Information", editorContent);
            } catch (Throwable t1) {
                CirSim.console("View Model Info window mode failed: " + t1);
                try {
                    InfoViewerDialog.showInfoInIframe("Model Information", editorContent, false);
                } catch (Throwable t2) {
                    CirSim.console("View Model Info iframe mode failed: " + t2);
                    InfoViewerDialog.showInfo("Model Information", editorContent);
                }
            }
        }
    }

    public void openMathTestDialog() {
        sim.openMathTestDialogCore();
    }

    public void openTableTestDialog() {
        sim.openTableTestDialogCore();
    }

    public void openIframeViewer() {
        IframeViewerDialog.openUrlWithSelector("Documentation",
            "../docs/money/index.html", "");
    }

    public void openReferenceDocsViewer() {
        String viewerUrl = com.google.gwt.core.client.GWT.getModuleBaseURL() +
            "docs/markdown-viewer.html?doc=reference/ReferenceIndex.md";
        IframeViewerDialog.openDialog("Reference Docs",
            viewerUrl, 900, 700);
    }
}
