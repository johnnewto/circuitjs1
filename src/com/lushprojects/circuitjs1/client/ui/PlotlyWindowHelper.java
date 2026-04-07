package com.lushprojects.circuitjs1.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.TextResource;
import com.google.gwt.user.client.Window;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

final class PlotlyWindowHelper {
    interface PlotlyViewerResources extends ClientBundle {
        PlotlyViewerResources INSTANCE = GWT.create(PlotlyViewerResources.class);

        @Source("ScopeViewerTemplate.html")
        TextResource scopeViewerTemplate();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Document")
    private static class DocumentLike {
        @JsMethod native void write(String text);
        @JsMethod native void close();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Window")
    private static class WindowLike {
        @JsProperty(name = "document") native DocumentLike getDocument();
        @JsProperty(name = "closed") native boolean isClosed();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Array")
    private static class WindowArrayLike {
        WindowArrayLike() {}
        @JsProperty(name = "length") native int getLength();
        @JsMethod(name = "push") native int push(WindowLike value);
        @JsMethod(name = "shift") native WindowLike shift();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "window")
    private static class GlobalWindowLike {
        @JsMethod(name = "open") static native WindowLike open(String url, String target, String features);
        @JsProperty(name = "plotlyWindows") static native WindowArrayLike getPlotlyWindows();
        @JsProperty(name = "plotlyWindows") static native void setPlotlyWindows(WindowArrayLike windows);
    }

    private PlotlyWindowHelper() {
    }

    static String generatePlotlyHTML(String jsonData, String timeUnitSymbol) {
        String template = PlotlyViewerResources.INSTANCE.scopeViewerTemplate().getText();
        return template
                .replace("__SCOPE_DATA_JSON__", jsonData)
                .replace("__TIME_UNIT_SYMBOL__", escapeJavaScriptString(timeUnitSymbol));
    }

    static String escapeJSON(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    static String escapeJavaScriptString(String s) {
        return escapeJSON(s);
    }

    static boolean openWindowWithHTML(String html, String blockedMessage) {
        WindowArrayLike windows = GlobalWindowLike.getPlotlyWindows();
        if (windows == null) {
            windows = new WindowArrayLike();
            GlobalWindowLike.setPlotlyWindows(windows);
        }

        WindowLike newWindow = GlobalWindowLike.open("", "_blank", "width=1400,height=900");
        if (newWindow == null) {
            Window.alert(blockedMessage);
            return false;
        }

        newWindow.getDocument().write(html);
        newWindow.getDocument().close();

        int len = windows.getLength();
        for (int i = 0; i < len; i++) {
            WindowLike existing = windows.shift();
            if (existing != null && !existing.isClosed()) {
                windows.push(existing);
            }
        }
        windows.push(newWindow);
        return true;
    }
}