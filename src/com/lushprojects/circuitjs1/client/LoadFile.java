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

import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;

import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

public class LoadFile extends FileUpload implements  ChangeHandler {

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Window")
	private static class WindowLike {
	    @JsProperty(name = "File") native Object getFile();
	    @JsProperty(name = "FileReader") native Object getFileReader();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Document")
	private static class DocumentLike {
	    @JsMethod native InputElementLike getElementById(String id);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Element")
	private static class ElementLike {
	    @JsMethod native void click();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "HTMLInputElement")
	private static class InputElementLike extends ElementLike {
	    @JsProperty native FileListLike getFiles();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "FileList")
	private static class FileListLike {
	    @JsProperty native int getLength();
	    @JsMethod native FileLike item(int index);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "File")
	private static class FileLike {
	    @JsProperty native double getSize();
	    @JsProperty native String getName();
	}

	@JsFunction
	private interface FileReaderOnLoad {
	    void onLoad(Object event);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "FileReader")
	private static class FileReaderLike {
	    public FileReaderLike() {}
	    @JsProperty native void setOnload(FileReaderOnLoad onload);
	    @JsProperty native String getResult();
	    @JsMethod native void readAsText(FileLike file);
	}

	@JsProperty(namespace = JsPackage.GLOBAL, name = "window")
	private static native WindowLike getWindow();

	@JsProperty(namespace = JsPackage.GLOBAL, name = "document")
	private static native DocumentLike getDocument();

	@JsMethod(namespace = JsPackage.GLOBAL, name = "alert")
	private static native void alert(String message);
	
	static CirSim sim;
	
	static public final boolean isSupported() {
	    WindowLike window = getWindow();
	    return window != null && window.getFile() != null && window.getFileReader() != null;
	}
	
	static public void doLoadCallback(String s, String t) {
		CirSim.console("Loading local file: " + t);
		sim.pushUndo();
		sim.getCircuitIOService().readCircuit(s);
		sim.getUiPanelManager().createNewLoadFile();
		sim.setCircuitTitle(t);
		sim.currentCircuitFile = "local: " + t;
		ExportAsLocalFileDialog.setLastFileName(t);
		sim.unsavedChanges = false;
	}
	
	LoadFile(CirSim s) {
		super();
		sim=s;
		this.setName("Import");
		this.getElement().setId("LoadFileElement");
		this.addChangeHandler(this);
		this.addStyleName("offScreen");
	}
	
	
	
	public void onChange(ChangeEvent e) {
		doLoad();
	}
	
	
	public final void click() {
	    InputElementLike input = getLoadFileInput();
	    if (input != null) {
		input.click();
	    }
	}
	
	private static InputElementLike getLoadFileInput() {
	    DocumentLike document = getDocument();
	    if (document == null) {
		return null;
	    }
	    return document.getElementById("LoadFileElement");
	}

	static public final void doLoad() {
	    InputElementLike input = getLoadFileInput();
	    if (input == null) {
		return;
	    }
	    FileListLike files = input.getFiles();
	    if (files == null || files.getLength() < 1) {
		return;
	    }
	    final FileLike file = files.item(0);
	    if (file == null) {
		return;
	    }
	    if (file.getSize() >= 128000) {
		alert("File too large!");
		return;
	    }
	    final FileReaderLike reader = new FileReaderLike();
	    reader.setOnload(event -> {
		String text = reader.getResult();
		doLoadCallback(text, file.getName());
	    });
	    reader.readAsText(file);
	}
	
}
