package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.MenuItem;

final class SFCRDocumentManager {
    private final SFCRDocumentState state = new SFCRDocumentState();
    private MenuItem fileModelInfoMenuItem;
    private MenuItem helpModelInfoMenuItem;

    SFCRDocumentState getState() {
        return state;
    }

    void bindModelInfoMenuItems(MenuItem fileItem, MenuItem helpItem) {
        fileModelInfoMenuItem = fileItem;
        helpModelInfoMenuItem = helpItem;
        refreshModelInfoMenuItems();
    }

    void setModelInfoContent(String value) {
        state.setModelInfoContent(value);
        refreshModelInfoMenuItems();
    }

    String getModelInfoContent() {
        return state.getModelInfoContent();
    }

    void setModelInfoSourceText(String value) {
        state.setModelInfoSourceText(value);
        refreshModelInfoMenuItems();
    }

    String getModelInfoSourceText() {
        return state.getModelInfoSourceText();
    }

    String getModelInfoEditorContent() {
        return state.getModelInfoEditorContent();
    }

    void clearModelInfo() {
        state.clearModelInfo();
        refreshModelInfoMenuItems();
    }

    void setCurrentCircuitFile(String value) {
        state.setCurrentCircuitFile(value);
    }

    String getCurrentCircuitFile() {
        return state.getCurrentCircuitFile();
    }

    void refreshModelInfoMenuItems() {
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
