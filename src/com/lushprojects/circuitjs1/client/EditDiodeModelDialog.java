package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.electronics.semiconductors.DiodeElm;

public class EditDiodeModelDialog extends EditDialog {

    DiodeModel model;
    DiodeElm diodeElm;
    
    public EditDiodeModelDialog(DiodeModel dm, CirSim f, DiodeElm de) {
	super(dm, f);
	model = dm;
	diodeElm = de;
	applyButton.removeFromParent();
    }

    void apply() {
	super.apply();
	if (model.name == null || model.name.length() == 0)
	    model.pickName();
	if (diodeElm != null)
	    diodeElm.newModelCreated(model);
    }
    
	public void closeDialog() {
	super.closeDialog();
	EditDialog edlg = CirSimDialogCoordinator.getEditDialog();
	CirSim.console("resetting dialog " + edlg);
	if (edlg != null)
	    edlg.resetDialog();	
	CirSimDialogCoordinator.setDiodeModelEditDialog(null);
    }
}
