package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class UndoRedoManager {
    private final CirSim sim;
    private final Vector<UndoItem> undoStack = new Vector<UndoItem>();
    private final Vector<UndoItem> redoStack = new Vector<UndoItem>();

    static class UndoItem {
        public final String dump;
        public final double scale;
        public final double transform4;
        public final double transform5;

        UndoItem(String dump, double[] transform) {
            this.dump = dump;
            this.scale = transform[0];
            this.transform4 = transform[4];
            this.transform5 = transform[5];
        }
    }

    UndoRedoManager(CirSim sim) {
        this.sim = sim;
    }

    private UndoItem createUndoItem(String dump) {
        return new UndoItem(dump, sim.transform);
    }

    private void applyUndoItem(UndoItem ui) {
        sim.getCircuitIOService().readCircuit(ui.dump, CirSim.RC_NO_CENTER);
        sim.transform[0] = sim.transform[3] = ui.scale;
        sim.transform[4] = ui.transform4;
        sim.transform[5] = ui.transform5;
    }

    void pushUndo() {
        redoStack.removeAllElements();
        String s = sim.getCircuitIOService().dumpCircuit();
        if (undoStack.size() > 0 &&
                s.compareTo(undoStack.lastElement().dump) == 0)
            return;
        undoStack.add(createUndoItem(s));
        enableUndoRedo();
        sim.savedFlag = false;
    }

    void doUndo() {
        if (undoStack.size() == 0)
            return;
        redoStack.add(createUndoItem(sim.getCircuitIOService().dumpCircuit()));
        UndoItem ui = undoStack.remove(undoStack.size() - 1);
        applyUndoItem(ui);
        enableUndoRedo();
    }

    void doRedo() {
        if (redoStack.size() == 0)
            return;
        undoStack.add(createUndoItem(sim.getCircuitIOService().dumpCircuit()));
        UndoItem ui = redoStack.remove(redoStack.size() - 1);
        applyUndoItem(ui);
        enableUndoRedo();
    }

    void doRecover() {
        pushUndo();
        sim.getCircuitIOService().readCircuit(sim.getCircuitIOService().getRecovery());
        sim.getUiPanelManager().allowSave(false);
        sim.recoverItem.setEnabled(false);
    }

    void enableUndoRedo() {
        sim.redoItem.setEnabled(redoStack.size() > 0);
        sim.undoItem.setEnabled(undoStack.size() > 0);
    }
}
