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

import java.util.Date;
import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.lushprojects.circuitjs1.client.util.Locale;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.i18n.client.DateTimeFormat;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;

public class ExportAsImageDialog extends Dialog {
	
	VerticalPanel vp;
	
	@JsMethod(namespace = JsPackage.GLOBAL, name = "encodeURIComponent")
	private static native String encodeURIComponent(String value);

	@JsMethod(namespace = JsPackage.GLOBAL, name = "unescape")
	private static native String unescape(String value);

	@JsMethod(namespace = JsPackage.GLOBAL, name = "btoa")
	private static native String btoa(String value);

	private static String b64encode(String text) {
	  return btoa(unescape(encodeURIComponent(text)));
	}

	public ExportAsImageDialog(int type) {
		super();
		Button okButton;
		Anchor a;
		vp=new VerticalPanel();
		setWidget(vp);
		setText(Locale.LS("Export as Image"));
		vp.add(new Label(Locale.LS("Click on the links below to save your images")));
		Date date = new Date();
		DateTimeFormat dtf = DateTimeFormat.getFormat("yyyyMMdd-HHmm");
		String dataURL;
		String ext = ".png";
		
		// Export circuit
		if (type == CirSim.CAC_IMAGE) {
		    dataURL = CirSim.theSim.getExportCompositeActions().getCircuitAsCanvas(type).toDataUrl();
		} else {
		    String data = CirSim.theSim.getExportCompositeActions().getCircuitAsSVG();
		    dataURL = "data:text/plain;base64," + b64encode(data);
		    ext = ".svg";
		}
		a=new Anchor("Circuit " + ext, dataURL);
		String fname = "circuit-"+ dtf.format(date) + ext;
		a.getElement().setAttribute("Download", fname);
		vp.add(a);
		
		// Export scopes if any exist
		Canvas scopesCanvas = CirSim.theSim.getExportCompositeActions().getScopesAsCanvas();
		if (scopesCanvas != null) {
			String scopesDataURL = scopesCanvas.toDataUrl();
			Anchor scopesAnchor = new Anchor("Scopes " + ext, scopesDataURL);
			String scopesFname = "scopes-" + dtf.format(date) + ext;
			scopesAnchor.getElement().setAttribute("Download", scopesFname);
			vp.add(scopesAnchor);
		}
		
		vp.add(okButton = new Button(Locale.LS("OK")));
		okButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				closeDialog();
			}
		});
		this.center();
	}
}
