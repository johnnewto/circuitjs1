package com.lushprojects.circuitjs1.client.ui;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.CirSimDialogCoordinator;
import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.Editable;
import com.lushprojects.circuitjs1.client.util.Locale;

public class EditDialogActions {
    private final CirSim sim;
    public EditDialogActions(CirSim sim) {
        this.sim = sim;
    }
    public void doEdit(Editable eable) {
        sim.getClipboardManager().clearSelection();
        sim.pushUndoForUi();

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
        sim.pushUndoForUi();
        CirSimDialogCoordinator.setDialogShowing(new SliderDialog(ce, sim));
        CirSimDialogCoordinator.getDialogShowing().show();
    }

    public void doEditLookupTables() {
        if (sim.isEditingLocked()) {
            sim.alertOrWarn(Locale.LS("Editing disabled.  Re-enable from the Options menu."));
            return;
        }
        CirSimDialogCoordinator.setDialogShowing(new LookupTablesEditorDialog(sim));
    }

    public void doExportAsImage() {
        CirSimDialogCoordinator.setDialogShowing(new ExportAsImageDialog(CirSim.cacImageTypeForUi()));
        CirSimDialogCoordinator.getDialogShowing().show();
    }

    public void doImageToClipboard() {
        sim.copyImageToClipboardForUi();
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
