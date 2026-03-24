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

import com.lushprojects.circuitjs1.client.elements.electronics.digital.SRAMElm;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

public class SRAMLoadFile extends EditDialogLoadFile {

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Element")
	private static class ElementLike {
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
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Document")
	private static class DocumentLike {
		@JsMethod native InputElementLike getElementById(String id);
	}

	@JsFunction
	private interface FileReaderOnLoad {
		void onLoad(Object event);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "FileReader")
	private static class FileReaderLike {
		public FileReaderLike() {}
		@JsProperty native void setOnload(FileReaderOnLoad onload);
		@JsProperty native Object getResult();
		@JsMethod native void readAsArrayBuffer(FileLike file);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "ArrayBuffer")
	private static class ArrayBufferLike {
		@JsProperty native int getByteLength();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "DataView")
	private static class DataViewLike {
		public DataViewLike(ArrayBufferLike buffer) {}
		@JsMethod native int getUint8(int byteOffset);
	}

	@JsProperty(namespace = JsPackage.GLOBAL, name = "document")
	private static native DocumentLike getDocument();
	
	public final void handle() {
		DocumentLike document = getDocument();
		if (document == null)
			return;
		InputElementLike input = document.getElementById("EditDialogLoadFileElement");
		if (input == null)
			return;
		FileListLike files = input.getFiles();
		if (files == null || files.getLength() < 1)
			return;
		final FileLike file = files.item(0);
		if (file == null)
			return;
		if (file.getSize() >= 128000) {
			EditDialogLoadFile.doErrorCallback("Cannot load: That file is too large!");
			return;
		}

		final FileReaderLike reader = new FileReaderLike();
		reader.setOnload(event -> {
			ArrayBufferLike buffer = (ArrayBufferLike) reader.getResult();
			if (buffer == null) {
				doLoadCallback("0:");
				return;
			}
			DataViewLike bytes = new DataViewLike(buffer);
			StringBuilder text = new StringBuilder("0:");
			for (int index = 0; index < buffer.getByteLength(); index++)
				text.append(" ").append(bytes.getUint8(index));
			doLoadCallback(text.toString());
		});
		reader.readAsArrayBuffer(file);
	}
	
	static public void doLoadCallback(String data) {
		SRAMElm.contentsOverride = data;
		CirSimDialogCoordinator.getEditDialog().resetDialog();
		SRAMElm.contentsOverride = null;
	}
}
