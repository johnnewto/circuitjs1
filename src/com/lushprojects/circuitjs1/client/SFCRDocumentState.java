package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

public class SFCRDocumentState {
    private final HashMap<String, Vector<String>> blockComments = new HashMap<String, Vector<String>>();
    private String modelInfoContent;
    private String modelInfoSourceText;
    private String currentCircuitFile;

    public void clearBlockComments() {
        blockComments.clear();
    }

    public void setBlockComments(String key, Vector<String> comments) {
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

    public Vector<String> getBlockComments(String key) {
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

    public void setModelInfoContent(String value) {
        modelInfoContent = value;
    }

    public String getModelInfoContent() {
        return modelInfoContent;
    }

    public void setModelInfoSourceText(String value) {
        modelInfoSourceText = value;
    }

    public String getModelInfoSourceText() {
        return modelInfoSourceText;
    }

    public String getModelInfoEditorContent() {
        if (modelInfoSourceText != null && !modelInfoSourceText.isEmpty()) {
            return modelInfoSourceText;
        }
        return modelInfoContent;
    }

    public void clearModelInfo() {
        modelInfoSourceText = null;
        modelInfoContent = null;
    }

    public void setCurrentCircuitFile(String value) {
        currentCircuitFile = value;
    }

    public String getCurrentCircuitFile() {
        return currentCircuitFile;
    }
}
