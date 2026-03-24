package com.lushprojects.circuitjs1.client.elements.electronics.misc;

import com.lushprojects.circuitjs1.client.ui.EditInfo;

import com.lushprojects.circuitjs1.client.ui.Choice;

import com.lushprojects.circuitjs1.client.ui.EditCompositeModelDialog;

import com.lushprojects.circuitjs1.client.elements.ChipElm;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;


import java.util.Vector;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.lushprojects.circuitjs1.client.util.Locale;

// instances of subcircuits

public class CustomCompositeElm extends CompositeElm {
    private String modelName;
    private CustomCompositeChipElm chip;
    private int postCount;
    int inputCount, outputCount;
    private CustomCompositeModel model;
    public static String lastModelName = "default";
    private static final int FLAG_SMALL = 2;
    
    public CustomCompositeElm(int xx, int yy) {
	super(xx, yy);
	
	// use last model as default when creating new element in UI.
	// use default otherwise, to avoid infinite recursion when creating nested subcircuits.
	modelName = (xx == 0 && yy == 0) ? "default" : lastModelName;
		
	flags |= FLAG_ESCAPE;
	if (sim.smallGridCheckItem.getState())
	    flags |= FLAG_SMALL;
	updateModels();
    }

    public CustomCompositeElm(int xx, int yy, String name) {
	super(xx, yy);
	modelName = name;
	flags |= FLAG_ESCAPE;
	if (sim.smallGridCheckItem.getState())
	    flags |= FLAG_SMALL;
	updateModels();
    }
    
    public CustomCompositeElm(int xa, int ya, int xb, int yb, int f,
            StringTokenizer st) {
	super(xa, ya, xb, yb, f);
	modelName = CustomLogicModel.unescape(st.nextToken());
	updateModels(st);
    }
    
    public String dump() {
	// insert model name before the elements
	String s = super.dumpWithMask(0);
	s += " " + CustomLogicModel.escape(modelName);
	s += dumpElements();
	return s;
    }
    
    protected String dumpModel() {
	String modelStr = "";
	
	// dump models of all children
	for (int i = 0; i < compElmList.size(); i++) {
	    CircuitElm ce = compElmList.get(i);
	    String m = ce.dumpModelForExternal();
	    if (m != null && !m.isEmpty()) {
		if (!modelStr.isEmpty())
		    modelStr += "\n";
		modelStr += m;
	    }
	}
	if (model.isDumped())
	    return modelStr;
	
	// dump our model
	if (!modelStr.isEmpty())
	    modelStr += "\n";
	modelStr += model.dumpForExternal();
	
	return modelStr;
    }
    
    protected void draw(Graphics g) {
	int i;
	for (i = 0; i != postCount; i++) {
	    chip.setPinVoltage(i, volts[i]);
	    chip.pins[i].current = getCurrentIntoNode(i); 
	}
	chip.setSelected(needsHighlight());
	chip.drawChipForComposite(g);
	boundingBox = chip.getChipBoundingBox();
    }

    protected void setPoints() {
	chip = new CustomCompositeChipElm(x, y);
	chip.x2 = x2;
	chip.y2 = y2;
	chip.flags = (flags & (ChipElm.FLAG_FLIP_X | ChipElm.FLAG_FLIP_Y | ChipElm.FLAG_FLIP_XY));
        if (x2-x > model.getSizeX()*16 && this == sim.dragElm)
	    flags &= ~FLAG_SMALL;
	chip.setChipSize((flags & FLAG_SMALL) != 0 ? 1 : 2);
	chip.setLabel(model.showLabel() ? model.getName() : null);
	
	chip.sizeX = model.getSizeX();
	chip.sizeY = model.getSizeY();
	chip.allocPins(postCount);
	int i;
	for (i = 0; i != postCount; i++) {
	    chip.setPin(i, model.getExternalPinPos(i), model.getExternalPinSide(i), model.getExternalPinName(i));
	}
	
	chip.updateChipPoints();
	for (i = 0; i != getPostCount(); i++)
	    setPost(i, chip.getChipPost(i));
    }

    protected void updateModels() {
	updateModels(null);
    }
    
    protected void flipX(int center2, int count) {
	flags ^= ChipElm.FLAG_FLIP_X;
	if (count != 1) {
	    int xs = (chip.flippedSizeX+1)*chip.cspc2;
	    x  = center2-x - xs;
	    x2 = center2-x2;
	}
	setPoints();
    }

    protected void flipY(int center2, int count) {
	flags ^= ChipElm.FLAG_FLIP_Y;
	if (count != 1) {
	    int xs = (chip.flippedSizeY-1)*chip.cspc2;
	    y  = center2-y - xs;
	    y2 = center2-y2;
	}
	setPoints();
    }

    private boolean isFlippedX() { return (flags & ChipElm.FLAG_FLIP_X) != 0; }
    private boolean isFlippedY() { return (flags & ChipElm.FLAG_FLIP_Y) != 0; }

    protected void flipXY(int xmy, int count) {
	flags ^= ChipElm.FLAG_FLIP_XY;

        // FLAG_FLIP_XY is applied first.  So need to swap X and Y
        if (isFlippedX() != isFlippedY())
            flags ^= ChipElm.FLAG_FLIP_X|ChipElm.FLAG_FLIP_Y;

	if (count != 1) {
	    x += chip.cspc2;
	    super.flipXY(xmy, count);
	    x -= chip.cspc2;
	}
	setPoints();
    }

    private void updateModels(StringTokenizer st) {
	model = CustomCompositeModel.getModelWithName(modelName);
	if (model == null)
	    return;
	postCount = model.getExternalPinCount();
	int externalNodes[] = new int[postCount];
	int i;
	for (i = 0; i != postCount; i++)
	    externalNodes[i] = model.getExternalPinNode(i);
	if (st == null)
	    st = new StringTokenizer(model.getElmDump(), " ");
	loadComposite(st, model.getNodeList(), externalNodes);
	allocNodes();
	setPoints();
    }
    
    protected int getPostCount() { return postCount; }
    
    private Vector<CustomCompositeModel> models;
    
    public EditInfo getEditInfo(int n) {
	// if model is built in, don't allow it to be changed
	if (model.isBuiltin())
	    n += 2;
	
	if (n == 0) {
	    EditInfo ei = new EditInfo(EditInfo.makeLink("subcircuits.html", "Model Name"), 0, -1, -1);
            models = CustomCompositeModel.getModelList();
            ei.choice = new Choice();
            int i;
            for (i = 0; i != models.size(); i++) {
                CustomCompositeModel ccm = models.get(i);
                ei.choice.add(ccm.getName());
                if (ccm == model)
                    ei.choice.select(i);
            }
	    return ei;
	}
        if (n == 1) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.button = new Button(Locale.LS("Edit Pin Layout"));
            return ei;
        }
        if (n == 2 && model.canLoadModelCircuit()) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.button = new Button(Locale.LS("Load Model Circuit"));
            return ei;
        }
	return null;
    }

    public void setEditValue(int n, EditInfo ei) {
	if (model.isBuiltin())
	    n += 2;
	if (n == 0) {
            model = models.get(ei.choice.getSelectedIndex());
	    lastModelName = modelName = model.getName();
	    updateModels();
	    setPoints();
	    return;
	}
        if (n == 1) {
            if (model.getName().equals("default")) {
        	Window.alert(Locale.LS("Can't edit this model."));
        	return;
            }
            EditCompositeModelDialog dlg = new EditCompositeModelDialog();
            dlg.setModel(model);
            dlg.createDialog();
            CirSimDialogCoordinator.setDialogShowing(dlg);
            dlg.show();
            return;
        }
        if (n == 2) {
		    sim.readCircuitFromModel(model.getModelCircuit());
            CirSimDialogCoordinator.getEditDialog().closeDialog();
        }
    }
    
    protected int getDumpType() { return 410; }

    protected void getInfo(String arr[]) {
	super.getInfo(arr);
	if (model.isBuiltin())
	    arr[0] = model.getName().substring(1);
	else
	    arr[0] = "subcircuit (" + model.getName() + ")";
	int i;
	for (i = 0; i != postCount; i++) {
	    if (i+1 >= arr.length)
		break;
	    arr[i+1] = model.getExternalPinName(i) + " = " + getVoltageText(volts[i]);
	}
    }
}
