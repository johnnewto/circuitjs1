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

package com.lushprojects.circuitjs1.client;


import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.ValueBoxBase;

class Dialog extends DialogBox  {

	boolean closeOnEnter;

	Dialog() {
		closeOnEnter = true;
	}
	
	/**
	 * Prevents keyboard events from propagating to the circuit editor.
	 * This stops delete/backspace keys from deleting circuit elements
	 * while the user is typing in a text field.
	 * @param textBox The TextBox to protect
	 */
	protected static void preventKeyboardPropagation(TextBox textBox) {
	    textBox.addKeyDownHandler(new com.google.gwt.event.dom.client.KeyDownHandler() {
		public void onKeyDown(com.google.gwt.event.dom.client.KeyDownEvent event) {
		    event.stopPropagation();
		}
	    });
	    textBox.addKeyPressHandler(new com.google.gwt.event.dom.client.KeyPressHandler() {
		public void onKeyPress(com.google.gwt.event.dom.client.KeyPressEvent event) {
		    event.stopPropagation();
		}
	    });
	    textBox.addKeyUpHandler(new com.google.gwt.event.dom.client.KeyUpHandler() {
		public void onKeyUp(com.google.gwt.event.dom.client.KeyUpEvent event) {
		    event.stopPropagation();
		}
	    });
	}
	
	/**
	 * Prevents keyboard events from propagating to the circuit editor.
	 * This overload works with ValueBoxBase (used by SuggestBox).
	 * @param valueBox The ValueBoxBase to protect
	 */
	protected static void preventKeyboardPropagation(ValueBoxBase<?> valueBox) {
	    valueBox.addKeyDownHandler(new com.google.gwt.event.dom.client.KeyDownHandler() {
		public void onKeyDown(com.google.gwt.event.dom.client.KeyDownEvent event) {
		    event.stopPropagation();
		}
	    });
	    valueBox.addKeyPressHandler(new com.google.gwt.event.dom.client.KeyPressHandler() {
		public void onKeyPress(com.google.gwt.event.dom.client.KeyPressEvent event) {
		    event.stopPropagation();
		}
	    });
	    valueBox.addKeyUpHandler(new com.google.gwt.event.dom.client.KeyUpHandler() {
		public void onKeyUp(com.google.gwt.event.dom.client.KeyUpEvent event) {
		    event.stopPropagation();
		}
	    });
	}

	public void closeDialog()
	{
		hide();
		if (CirSim.dialogShowing == this)
		    CirSim.dialogShowing = null;
	}
	
	
	public void enterPressed() {
	    if (closeOnEnter) {
		apply();
		closeDialog();
	    }
	}

	void apply() {
	}
}

