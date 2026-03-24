package com.lushprojects.circuitjs1.client;


import com.google.gwt.user.client.ui.MenuItem;

public final class SFCRDocumentManager {
    private final SFCRDocumentState state = new SFCRDocumentState();
    private MenuItem fileModelInfoMenuItem;
    private MenuItem helpModelInfoMenuItem;

    public SFCRDocumentState getState() {
        return state;
    }

    public void bindModelInfoMenuItems(MenuItem fileItem, MenuItem helpItem) {
        fileModelInfoMenuItem = fileItem;
        helpModelInfoMenuItem = helpItem;
        refreshModelInfoMenuItems();
    }

    public void setModelInfoContent(String value) {
        state.setModelInfoContent(value);
        refreshModelInfoMenuItems();
    }

    public String getModelInfoContent() {
        return state.getModelInfoContent();
    }

    public void setModelInfoSourceText(String value) {
        state.setModelInfoSourceText(value);
        refreshModelInfoMenuItems();
    }

    public String getModelInfoSourceText() {
        return state.getModelInfoSourceText();
    }

    public String getModelInfoEditorContent() {
        return state.getModelInfoEditorContent();
    }

    public void clearModelInfo() {
        state.clearModelInfo();
        refreshModelInfoMenuItems();
    }

    public void setCurrentCircuitFile(String value) {
        state.setCurrentCircuitFile(value);
    }

    public String getCurrentCircuitFile() {
        return state.getCurrentCircuitFile();
    }

    public void refreshModelInfoMenuItems() {
        String editorContent = state.getModelInfoEditorContent();
        boolean enabled = editorContent != null && !editorContent.isEmpty();
        if (RuntimeMode.isGwt() && fileModelInfoMenuItem != null) {
            fileModelInfoMenuItem.setEnabled(enabled);
        }
        if (RuntimeMode.isGwt() && helpModelInfoMenuItem != null) {
            helpModelInfoMenuItem.setEnabled(enabled);
        }
    }
}
