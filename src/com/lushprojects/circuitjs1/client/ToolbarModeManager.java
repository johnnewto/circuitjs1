package com.lushprojects.circuitjs1.client;


import com.lushprojects.circuitjs1.client.ui.EconomicsToolbar;
import com.lushprojects.circuitjs1.client.ui.ElectronicsToolbar;

class ToolbarModeManager {
    private final CirSim sim;

    ToolbarModeManager(CirSim sim) {
        this.sim = sim;
    }

    void switchToElectronicsToolbar() {
        if (sim.currentToolbarType == CirSim.ToolbarType.ELECTRONICS) {
            sim.electronicsModeCheckItem.setState(true);
            sim.economicsModeCheckItem.setState(false);
            return;
        }

        sim.voltageUnitSymbol = "V";
        sim.timeUnitSymbol = "s";

        boolean toolbarVisible = sim.toolbarCheckItem.getState();

        sim.layoutPanel.remove(sim.toolbar);

        sim.currentToolbarType = CirSim.ToolbarType.ELECTRONICS;
        sim.toolbar = new ElectronicsToolbar();
        sim.toolbar.setEuroResistors(sim.euroResistorCheckItem.getState());

        if (!sim.hideMenu && sim.getMenuUiState().menuBar.getParent() == sim.layoutPanel) {
            sim.layoutPanel.insertNorth(sim.toolbar, CirSim.TOOLBARHEIGHT, sim.getMenuUiState().menuBar);
        } else {
            sim.layoutPanel.addNorth(sim.toolbar, CirSim.TOOLBARHEIGHT);
        }

        sim.electronicsModeCheckItem.setState(true);
        sim.economicsModeCheckItem.setState(false);

        sim.layoutPanel.setWidgetHidden(sim.toolbar, !toolbarVisible);
        sim.getViewportController().setCanvasSize();
    }

    void switchToEconomicsToolbar() {
        if (sim.currentToolbarType == CirSim.ToolbarType.ECONOMICS) {
            sim.electronicsModeCheckItem.setState(false);
            sim.economicsModeCheckItem.setState(true);
            return;
        }

        sim.voltageUnitSymbol = "$";
        sim.timeUnitSymbol = "yr";

        boolean toolbarVisible = sim.toolbarCheckItem.getState();

        sim.layoutPanel.remove(sim.toolbar);

        sim.currentToolbarType = CirSim.ToolbarType.ECONOMICS;
        sim.toolbar = new EconomicsToolbar();
        sim.toolbar.setEuroResistors(sim.euroResistorCheckItem.getState());

        if (!sim.hideMenu && sim.getMenuUiState().menuBar.getParent() == sim.layoutPanel) {
            sim.layoutPanel.insertNorth(sim.toolbar, CirSim.TOOLBARHEIGHT, sim.getMenuUiState().menuBar);
        } else {
            sim.layoutPanel.addNorth(sim.toolbar, CirSim.TOOLBARHEIGHT);
        }

        sim.electronicsModeCheckItem.setState(false);
        sim.economicsModeCheckItem.setState(true);

        sim.layoutPanel.setWidgetHidden(sim.toolbar, !toolbarVisible);
        sim.getViewportController().setCanvasSize();
    }

    void setMode(CirSim.ToolbarType mode) {
        if (mode == CirSim.ToolbarType.ELECTRONICS) {
            switchToElectronicsToolbar();
        } else {
            switchToEconomicsToolbar();
        }
    }

    void setElectronicsMode() {
        setMode(CirSim.ToolbarType.ELECTRONICS);
    }

    void setEconomicsMode() {
        setMode(CirSim.ToolbarType.ECONOMICS);
    }
}
