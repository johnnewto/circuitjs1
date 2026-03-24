package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.*;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.dom.client.CanvasElement;
import com.lushprojects.circuitjs1.client.ui.ExportAsImageDialog;
import com.lushprojects.circuitjs1.client.ui.LookupTablesEditorDialog;
import com.lushprojects.circuitjs1.client.util.Locale;

public class EditDialogActions {
    private final CirSim sim;
    public EditDialogActions(CirSim sim) {
        this.sim = sim;
    }
    public void doEdit(Editable eable) {
        sim.getClipboardManager().clearSelection();
        sim.getUndoRedoManager().pushUndo();

        EditInfo firstInfo = eable.getEditInfo(0);
        if (firstInfo == null) {
            return;
        }

        if (CirSimDialogCoordinator.getEditDialog() != null) {
            CirSimDialogCoordinator.getEditDialog().setVisible(false);
            CirSimDialogCoordinator.setEditDialog(null);
        }
        CirSimDialogCoordinator.setEditDialog(new EditDialog(eable, sim));
        CirSimDialogCoordinator.getEditDialog().show();
    }

    public void doSliders(CircuitElm ce) {
        sim.getClipboardManager().clearSelection();
        sim.getUndoRedoManager().pushUndo();
        CirSimDialogCoordinator.setDialogShowing(new SliderDialog(ce, sim));
        CirSimDialogCoordinator.getDialogShowing().show();
    }

    public void doEditLookupTables() {
        if (sim.noEditCheckItem != null && sim.noEditCheckItem.getState()) {
            sim.alertOrWarn(Locale.LS("Editing disabled.  Re-enable from the Options menu."));
            return;
        }
        CirSimDialogCoordinator.setDialogShowing(new LookupTablesEditorDialog(sim));
    }

    public void doExportAsImage() {
        CirSimDialogCoordinator.setDialogShowing(new ExportAsImageDialog(CirSim.CAC_IMAGE));
        CirSimDialogCoordinator.getDialogShowing().show();
    }

    public void doImageToClipboard() {
        sim.doImageToClipboardCore();
    }

    public void doCreateSubcircuit() {
        EditCompositeModelDialog dlg = new EditCompositeModelDialog();
        if (!dlg.createModel())
            return;
        dlg.createDialog();
        CirSimDialogCoordinator.setDialogShowing(dlg);
        CirSimDialogCoordinator.getDialogShowing().show();
    }
}
