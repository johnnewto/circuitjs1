package com.lushprojects.circuitjs1.client;

class InfoDialogActions {
    private final CirSim sim;

    InfoDialogActions(CirSim sim) {
        this.sim = sim;
    }

    void doViewModelInfo() {
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

    void openMathTestDialog() {
        sim.openMathTestDialogCore();
    }

    void openTableTestDialog() {
        sim.openTableTestDialogCore();
    }

    void openIframeViewer() {
        IframeViewerDialog.openUrlWithSelector("Documentation",
            "../docs/money/index.html", "");
    }

    void openReferenceDocsViewer() {
        String viewerUrl = com.google.gwt.core.client.GWT.getModuleBaseURL() +
            "docs/markdown-viewer.html?doc=reference/ReferenceIndex.md";
        IframeViewerDialog.openDialog("Reference Docs",
            viewerUrl, 900, 700);
    }
}