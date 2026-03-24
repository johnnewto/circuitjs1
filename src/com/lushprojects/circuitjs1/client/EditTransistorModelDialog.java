package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.*;


import com.lushprojects.circuitjs1.client.elements.electronics.semiconductors.TransistorElm;

public class EditTransistorModelDialog extends EditDialog {

    TransistorModel model;
    TransistorElm transistorElm;
    
    public EditTransistorModelDialog(TransistorModel dm, CirSim f, TransistorElm te) {
	super(dm, f);
	model = dm;
        transistorElm = te;
	applyButton.removeFromParent();
    }

    public void apply() {
	super.apply();
//	if (model.name == null || model.name.length() == 0)
//	    model.pickName();
	if (transistorElm != null)
	    transistorElm.newModelCreated(model);
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
