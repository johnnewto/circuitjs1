package com.lushprojects.circuitjs1.client;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.dom.client.CanvasElement;
import com.lushprojects.circuitjs1.client.util.Locale;

class EditDialogActions {
    private final CirSim sim;

    EditDialogActions(CirSim sim) {
        this.sim = sim;
    }

    void doEdit(Editable eable) {
        sim.clearSelection();
        sim.pushUndo();

        EditInfo firstInfo = eable.getEditInfo(0);
        if (firstInfo == null) {
            return;
        }

        if (CirSim.editDialog != null) {
            CirSim.editDialog.setVisible(false);
            CirSim.editDialog = null;
        }
        CirSim.editDialog = new EditDialog(eable, sim);
        CirSim.editDialog.show();
    }

    void doSliders(CircuitElm ce) {
        sim.clearSelection();
        sim.pushUndo();
        CirSim.dialogShowing = new SliderDialog(ce, sim);
        CirSim.dialogShowing.show();
    }

    void doEditLookupTables() {
        if (sim.noEditCheckItem != null && sim.noEditCheckItem.getState()) {
            sim.alertOrWarn(Locale.LS("Editing disabled.  Re-enable from the Options menu."));
            return;
        }
        CirSim.dialogShowing = new LookupTablesEditorDialog(sim);
    }

    void doExportAsImage() {
        CirSim.dialogShowing = new ExportAsImageDialog(CirSim.CAC_IMAGE);
        CirSim.dialogShowing.show();
    }

    void doImageToClipboard() {
        sim.doImageToClipboardCore();
    }

    void doCreateSubcircuit() {
        EditCompositeModelDialog dlg = new EditCompositeModelDialog();
        if (!dlg.createModel())
            return;
        dlg.createDialog();
        CirSim.dialogShowing = dlg;
        CirSim.dialogShowing.show();
    }
}