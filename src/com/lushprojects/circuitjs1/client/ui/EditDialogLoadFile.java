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

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.FileUpload;
import com.lushprojects.circuitjs1.client.LoadFile;
import com.lushprojects.circuitjs1.client.util.Locale;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

/*
 * An abstract class for circuitjs which allows components to prompt for files from the user.
 */
public abstract class EditDialogLoadFile extends FileUpload implements ChangeHandler  {

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Element")
	private static class ElementLike {
		@JsMethod native void click();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Document")
	private static class DocumentLike {
		@JsMethod native ElementLike getElementById(String id);
	}

	@JsProperty(namespace = JsPackage.GLOBAL, name = "document")
	private static native DocumentLike getDocument();
	
	static public final boolean isSupported() { return LoadFile.isSupported(); }
	
	static public void doErrorCallback(String msg) {
		Window.alert(Locale.LS(msg));
	}
	
	protected EditDialogLoadFile() {
		super();
		this.setName(Locale.LS("Load File"));
		this.getElement().setId("EditDialogLoadFileElement");
		this.addChangeHandler(this);
		this.addStyleName("offScreen");
		this.setPixelSize(0, 0);
	}
	
	public void onChange(ChangeEvent e) {
		handle();
	}
	
	public final void open() {
		DocumentLike document = getDocument();
		if (document == null)
			return;
		ElementLike element = document.getElementById("EditDialogLoadFileElement");
		if (element != null)
			element.click();
	}
	
	public abstract void handle();
}
