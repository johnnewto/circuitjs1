/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client.ui;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.lushprojects.circuitjs1.client.util.Locale;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;

import java.util.Vector;
import java.util.Collections;
import java.util.Comparator;

import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.ListBox;

public class SearchDialog extends Dialog {
	
	private VerticalPanel vp;
	private CirSim sim;
	TextArea textArea;
	private TextBox textBox;
	private Button okButton;
	private ListBox listBox;
	
	public SearchDialog(CirSim asim) {
		super();
		sim = asim;
		Button cancelButton;
	vp=new VerticalPanel();
	setWidget(vp);
	textBox = new TextBox();
	textBox.setMaxLength(15);
	vp.add(textBox);
	// Prevent keyboard events from propagating to circuit editor
	preventKeyboardPropagation(textBox);
	textBox.addKeyUpHandler(
		new KeyUpHandler() {
	    public void onKeyUp(KeyUpEvent ev) {
		search();
	    }
	});

	setText(Locale.LS("Find Component"));		listBox = new ListBox();
		listBox.setWidth("100%");
		listBox.addDoubleClickHandler(new DoubleClickHandler() {
		    public void onDoubleClick(DoubleClickEvent ev) {
			apply();
		    }
		});
		listBox.setVisibleItemCount(10);
		vp.add(listBox);
		Vector<String> items = getSearchableMainMenuItemNames();
		for (int i = 0; i != items.size(); i++)
			listBox.addItem(items.get(i));

		HorizontalPanel hp = new HorizontalPanel();
		hp.setWidth("100%");
		hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
		hp.setStyleName("topSpace");
		vp.add(hp);
		hp.add(okButton = new Button(Locale.LS("OK")));
		hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
		hp.add(cancelButton = new Button(Locale.LS("Cancel")));
		okButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
			    apply();
			}
		});
		cancelButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				closeDialog();
			}
		});
		this.center();
		textBox.setFocus(true);
	}
	
	protected void apply() {
	    String s = listBox.getSelectedItemText();
	    if (s != null) {
		for (int i = 0; i != sim.getMenuUiState().mainMenuItems.size(); i++) {
		    CheckboxMenuItem item = sim.getMenuUiState().mainMenuItems.get(i);
		    if (s.equals(item.getName())) {
			item.getScheduledCommand().execute();
			break;
		    }
		}
	    }
	    closeDialog();
	}
	
	private void search() {
	    String str = textBox.getText().toLowerCase();
	    listBox.clear();
	    Vector<String> items = new Vector<String>();
	    Vector<String> allItems = getSearchableMainMenuItemNames();
	    for (int i = 0; i != allItems.size(); i++) {
		String itemName = allItems.get(i);
		if (itemName.toLowerCase().contains(str)) {
		    if (!items.contains(itemName))
			items.add(itemName);
		}
	    }
            Collections.sort(items, new Comparator<String>() {
                public int compare(String a, String b) {
                    return a.compareTo(b);
                }
            });
            for (int i = 0; i != items.size(); i++)
        	listBox.addItem(items.get(i));
            if (items.size() > 0)
		listBox.setItemSelected(0, true);
	}

	private Vector<String> getSearchableMainMenuItemNames() {
	    Vector<String> items = new Vector<String>();
	    for (int i = 0; i != sim.getMenuUiState().mainMenuItems.size(); i++) {
		CheckboxMenuItem item = sim.getMenuUiState().mainMenuItems.get(i);
		if (item.getShortcut().length() > 1)
		    break;
		items.add(item.getName());
	    }
	    return items;
	}
}
