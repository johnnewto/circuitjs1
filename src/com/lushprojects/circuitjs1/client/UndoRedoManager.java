package com.lushprojects.circuitjs1.client;

class UndoRedoManager {
    private final CirSim sim;

    UndoRedoManager(CirSim sim) {
        this.sim = sim;
    }

    void pushUndo() {
        sim.redoStack.removeAllElements();
        String s = sim.getCircuitIOService().dumpCircuit();
        if (sim.undoStack.size() > 0 &&
                s.compareTo(sim.undoStack.lastElement().dump) == 0)
            return;
        sim.undoStack.add(sim.new UndoItem(s));
        enableUndoRedo();
        sim.savedFlag = false;
    }

    void doUndo() {
        if (sim.undoStack.size() == 0)
            return;
        sim.redoStack.add(sim.new UndoItem(sim.getCircuitIOService().dumpCircuit()));
        CirSim.UndoItem ui = sim.undoStack.remove(sim.undoStack.size() - 1);
        sim.loadUndoItem(ui);
        enableUndoRedo();
    }

    void doRedo() {
        if (sim.redoStack.size() == 0)
            return;
        sim.undoStack.add(sim.new UndoItem(sim.getCircuitIOService().dumpCircuit()));
        CirSim.UndoItem ui = sim.redoStack.remove(sim.redoStack.size() - 1);
        sim.loadUndoItem(ui);
        enableUndoRedo();
    }

    void doRecover() {
        pushUndo();
        sim.getCircuitIOService().readCircuit(sim.recovery);
        sim.getUiPanelManager().allowSave(false);
        sim.recoverItem.setEnabled(false);
    }

    void enableUndoRedo() {
        sim.redoItem.setEnabled(sim.redoStack.size() > 0);
        sim.undoItem.setEnabled(sim.undoStack.size() > 0);
    }
}