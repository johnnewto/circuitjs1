package com.lushprojects.circuitjs1.client.ui;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import java.util.Vector;

public class SubcircuitDialog extends Dialog {

    private VerticalPanel mainPanel;
    private ListBox subcircuitListBox;
    private Button deleteButton;
    private Button doneButton;

    private Vector<CustomCompositeModel> subcircuits;

    public SubcircuitDialog(CirSim sim) {
        setText("Subcircuit Manager");
        //setAnimationEnabled(true);
        setGlassEnabled(true);

        mainPanel = new VerticalPanel();
        mainPanel.setSpacing(10);
	mainPanel.setWidth("400px");
	//mainPanel.setHeight("400px");

        //Label selectLabel = new Label("Select a subcircuit to delete:");
        //mainPanel.add(selectLabel);

        subcircuitListBox = new ListBox();
        subcircuitListBox.setVisibleItemCount(5);
        subcircuitListBox.setWidth("100%");

	subcircuits = new Vector<CustomCompositeModel>();
	Vector<CustomCompositeModel> modelList = CustomCompositeModel.getModelList();
	int i;
	for (i = 0; i != modelList.size(); i++) {
	    CustomCompositeModel model = modelList.get(i);
	    if (model.isBuiltin())
		continue;
	    subcircuits.add(model);
	    subcircuitListBox.addItem(model.getNameForUi());
	}

        mainPanel.add(subcircuitListBox);

        deleteButton = new Button("Delete");
        deleteButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                handleDelete();
            }
        });
        mainPanel.add(deleteButton);

        doneButton = new Button("Done");
        doneButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                hide();
            }
        });
        mainPanel.add(doneButton);

        setWidget(mainPanel);
	this.center();
    }

	private void handleDelete() {
        int selectedIndex = subcircuitListBox.getSelectedIndex();
        if (selectedIndex == -1) {
            Window.alert("Please select a subcircuit to delete.");
            return;
        }

        CustomCompositeModel selectedSubcircuit = subcircuits.get(selectedIndex);
        boolean confirm = Window.confirm("Are you sure you want to delete " + selectedSubcircuit.getNameForUi() + "?");

        if (confirm) {
            subcircuits.remove(selectedIndex);
	    selectedSubcircuit.remove();
            subcircuitListBox.removeItem(selectedIndex);
        }
    }
}
