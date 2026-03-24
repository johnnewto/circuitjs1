package com.lushprojects.circuitjs1.client;


import com.google.gwt.user.client.ui.RootLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.lushprojects.circuitjs1.client.io.LoadFile;

public class CirSimUiPanelManager {
    private final CirSim sim;

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

    void allowSave(boolean enabled) {
        if (sim.saveFileItem != null)
            sim.saveFileItem.setEnabled(enabled);
    }
}
