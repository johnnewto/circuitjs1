package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class SFCRDocumentState {
    private final HashMap<String, Vector<String>> blockComments = new HashMap<String, Vector<String>>();
    private String modelInfoContent;
    private String modelInfoSourceText;
    private String currentCircuitFile;

    void clearBlockComments() {
        blockComments.clear();
    }

    void setBlockComments(String key, Vector<String> comments) {
        if (key == null || key.length() == 0) {
            return;
        }
        if (comments == null || comments.size() == 0) {
            blockComments.remove(key);
            return;
        }
        Vector<String> copy = new Vector<String>();
        for (int i = 0; i < comments.size(); i++) {
            copy.add(comments.get(i));
        }
        blockComments.put(key, copy);
    }

    Vector<String> getBlockComments(String key) {
        if (key == null || key.length() == 0) {
            return null;
        }
        Vector<String> comments = blockComments.get(key);
        if (comments == null) {
            return null;
        }
        Vector<String> copy = new Vector<String>();
        for (int i = 0; i < comments.size(); i++) {
            copy.add(comments.get(i));
        }
        return copy;
    }

    void setModelInfoContent(String value) {
        modelInfoContent = value;
    }

    String getModelInfoContent() {
        return modelInfoContent;
    }

    void setModelInfoSourceText(String value) {
        modelInfoSourceText = value;
    }

    String getModelInfoSourceText() {
        return modelInfoSourceText;
    }

    String getModelInfoEditorContent() {
        if (modelInfoSourceText != null && !modelInfoSourceText.isEmpty()) {
            return modelInfoSourceText;
        }
        return modelInfoContent;
    }

    void clearModelInfo() {
        modelInfoSourceText = null;
        modelInfoContent = null;
    }

    void setCurrentCircuitFile(String value) {
        currentCircuitFile = value;
    }

    String getCurrentCircuitFile() {
        return currentCircuitFile;
    }
}
