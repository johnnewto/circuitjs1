package com.lushprojects.circuitjs1.client;

import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsOverlay;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

public class ImportFromDropbox {

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	private static class DropboxLike {
		@JsMethod native boolean isBrowserSupported();
		@JsMethod native void choose(ChooseOptions options);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Window")
	private static class WindowLike {
		@JsProperty native DropboxLike getDropbox();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	private static class NavigatorLike {
		@JsProperty native String getUserAgent();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	private static class FileLike {
		@JsProperty native String getLink();
		@JsProperty native double getBytes();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Array")
	private static class FileArrayLike {
		@JsProperty(name = "length") native int getLength();
		@JsMethod(name = "at") native FileLike at(int index);
	}

	@JsFunction
	private interface SuccessCallback {
		void onSuccess(FileArrayLike files);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	private static class ChooseOptions {
		@JsProperty native void setSuccess(SuccessCallback callback);
		@JsProperty native void setLinkType(String linkType);
		@JsProperty native void setMultiselect(boolean multiselect);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "XMLHttpRequest")
	private static class XMLHttpRequestLike {
		public XMLHttpRequestLike() {}
		@JsMethod native void open(String method, String url, boolean async);
		@JsMethod native void send();
		@JsProperty native String getResponseText();
	}

	@JsProperty(namespace = JsPackage.GLOBAL, name = "window")
	private static native WindowLike getWindow();

	@JsProperty(namespace = JsPackage.GLOBAL, name = "navigator")
	private static native NavigatorLike getNavigator();

	
	static CirSim sim;
	
	ImportFromDropbox( CirSim asim ){
		sim=asim;
//		CirSim.console("importing");
		doDropboxImport();
//		CirSim.console("returned");
	}
	
	static public final boolean isSupported() {
		try {
			NavigatorLike navigator = getNavigator();
			String ua = (navigator == null) ? null : navigator.getUserAgent();
			if (ua != null && ua.contains("Firefox/"))
				return false;
			WindowLike window = getWindow();
			DropboxLike dropbox = (window == null) ? null : window.getDropbox();
			return dropbox != null && dropbox.isBrowserSupported();
		} catch (Exception e) {
			return false;
		}
	}
	
	static public void doLoadCallback(String s) {
		sim.getUndoRedoManager().pushUndo();
		sim.getCircuitIOService().readCircuit(s);
	}
	
	
	public final void doDropboxImport() {
		ChooseOptions options = new ChooseOptions();
		options.setLinkType("direct");
		options.setMultiselect(false);
		options.setSuccess(files -> {
			try {
				if (files == null || files.getLength() < 1)
					return;
				FileLike file = files.at(0);
				if (file == null || file.getBytes() >= 100000)
					return;
				XMLHttpRequestLike xhr = new XMLHttpRequestLike();
				xhr.open("GET", file.getLink(), false);
				xhr.send();
				doLoadCallback(xhr.getResponseText());
			} catch (Exception e) {
			}
		});
		WindowLike window = getWindow();
		if (window != null && window.getDropbox() != null)
			window.getDropbox().choose(options);
	}
}
