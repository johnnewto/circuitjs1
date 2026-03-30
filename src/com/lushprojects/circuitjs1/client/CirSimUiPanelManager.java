package com.lushprojects.circuitjs1.client;


import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.user.client.ui.RootLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.Frame;
import com.lushprojects.circuitjs1.client.io.LoadFile;
import com.lushprojects.circuitjs1.client.io.SFCRExporter;
import com.lushprojects.circuitjs1.client.runner.RuntimeMode;
import com.lushprojects.circuitjs1.client.ui.InfoViewerDialog;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

public class CirSimUiPanelManager {
    private final CirSim sim;
    
    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Window")
    private interface WindowLike {
        void postMessage(Object message, String targetOrigin);
    }
    
    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "HTMLIFrameElement")
    private interface IFrameElementLike {
        @JsProperty(name = "contentWindow")
        WindowLike getContentWindow();
    }
    
    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
    private static class ModelInfoSyncMessage {
        @JsProperty
        String type;
        
        @JsProperty
        String markdown;
    }

    CirSimUiPanelManager(CirSim sim) {
        this.sim = sim;
    }

    public void setiFrameHeight() {
        if (sim.iFrame == null)
            return;
        int cumheight = 0;
        for (int i = 0; i < sim.verticalPanel.getWidgetIndex(sim.iFrame); i++) {
            if (sim.verticalPanel.getWidget(i) != sim.loadFileInput) {
                cumheight = cumheight + sim.verticalPanel.getWidget(i).getOffsetHeight();
                if (sim.verticalPanel.getWidget(i).getStyleName().contains("topSpace"))
                    cumheight += 12;
            }
        }
        int ih = RootLayoutPanel.get().getOffsetHeight() - (sim.hideMenu ? 0 : CirSim.MENUBARHEIGHT) - cumheight;
        if (ih < 0)
            ih = 0;
        sim.iFrame.setHeight(ih + "px");
    }

    void createNewLoadFile() {
        int idx = sim.verticalPanel.getWidgetIndex(sim.loadFileInput);
        LoadFile newlf = new LoadFile(sim);
        sim.verticalPanel.insert(newlf, idx);
        sim.verticalPanel.remove(idx + 1);
        sim.loadFileInput = newlf;
    }

    public void addWidgetToVerticalPanel(Widget w) {
        if (RuntimeMode.isNonInteractiveRuntime() || w == null || sim.verticalPanel == null)
            return;
        if (sim.iFrame != null) {
            int i = sim.verticalPanel.getWidgetIndex(sim.iFrame);
            sim.verticalPanel.insert(w, i);
            setiFrameHeight();
        } else
            sim.verticalPanel.add(w);
    }

    public void removeWidgetFromVerticalPanel(Widget w) {
        if (RuntimeMode.isNonInteractiveRuntime() || w == null || sim.verticalPanel == null)
            return;
        sim.verticalPanel.remove(w);
        if (sim.iFrame != null)
            setiFrameHeight();
    }

    public void addWidgetToLeftPanel(Widget w) {
        if (RuntimeMode.isNonInteractiveRuntime() || w == null || sim.leftPanel == null)
            return;
        sim.leftPanel.add(w);
    }

    public void removeWidgetFromLeftPanel(Widget w) {
        if (RuntimeMode.isNonInteractiveRuntime() || w == null || sim.leftPanel == null)
            return;
        sim.leftPanel.remove(w);
    }

    public void showModelInfoEditorInLeftPanel(String markdown) {
        if (RuntimeMode.isNonInteractiveRuntime() || sim.leftPanel == null)
            return;

        if (sim.leftModelInfoFrame == null) {
            Frame frame = new Frame();
            frame.getElement().setId("leftModelInfoEditorFrame");
            frame.getElement().getStyle().setProperty("border", "0");
            frame.setWidth("100%");
            int leftHeight = Math.max(300, RootLayoutPanel.get().getOffsetHeight() - CirSim.TOOLBARHEIGHT - 20);
            frame.setHeight(leftHeight + "px");
            sim.leftModelInfoFrame = frame;
            sim.leftPanel.setWidth("100%");
            sim.leftPanel.add(frame);
        }

        InputElement leftTrigger = null;
        try {
            leftTrigger = (InputElement) Document.get().getElementById("leftTrigger").cast();
        } catch (Throwable t) {
            leftTrigger = null;
        }
        if (leftTrigger != null)
            leftTrigger.setChecked(true);

        sim.layoutPanel.setWidgetSize(sim.leftPanel, CirSim.LEFTPANELWIDTH);
        if (sim.leftPanelCheckboxLabel != null)
            sim.leftPanelCheckboxLabel.getStyle().setProperty("left", CirSim.LEFTPANELWIDTH + "px");
        sim.layoutPanel.forceLayout();
        sim.getViewportController().setCanvasSize();

        sim.leftModelInfoFrame.setUrl(InfoViewerDialog.createModelInfoEditorPanelDataUrl(markdown));
        sim.repaint();
    }

    public void refreshModelInfoEditorAfterCircuitMutation() {
        if (RuntimeMode.isNonInteractiveRuntime() || sim.leftModelInfoFrame == null)
            return;
        String refreshedMarkdown;
        try {
            refreshedMarkdown = new SFCRExporter(sim).export();
        } catch (Throwable t) {
            return;
        }
        if (refreshedMarkdown == null || refreshedMarkdown.isEmpty())
            return;
        postModelInfoSyncMessage(sim.leftModelInfoFrame.getElement(), refreshedMarkdown);
    }

    private static void postModelInfoSyncMessage(Element frameElement, String markdown) {
        try {
            if (frameElement == null)
                return;
            IFrameElementLike iframe = (IFrameElementLike) frameElement.cast();
            WindowLike contentWindow = iframe.getContentWindow();
            if (contentWindow == null)
                return;
            ModelInfoSyncMessage message = new ModelInfoSyncMessage();
            message.type = "info-viewer-sync-markdown";
            message.markdown = markdown != null ? markdown : "";
            contentWindow.postMessage(message, "*");
        } catch (Throwable t) {
        }
    }

    void allowSave(boolean enabled) {
        if (sim.saveFileItem != null)
            sim.saveFileItem.setEnabled(enabled);
    }
}
