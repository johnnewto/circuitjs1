package com.lushprojects.circuitjs1.client.elements.electronics.semiconductors;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.CirSimDialogCoordinator;
import com.lushprojects.circuitjs1.client.elements.electronics.DiodeModel;
import com.lushprojects.circuitjs1.client.ui.EditDialog;

public class EditDiodeModelDialog extends EditDialog {

    private DiodeModel model;
    private DiodeElm diodeElm;
    
    public EditDiodeModelDialog(DiodeModel dm, CirSim f, DiodeElm de) {
	super(dm, f);
	model = dm;
	diodeElm = de;
	applyButton.removeFromParent();
    }

    public void apply() {
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
