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

import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.lushprojects.circuitjs1.client.util.Locale;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;

public class ExportAsTextDialog extends Dialog {
	
	VerticalPanel vp;
	CirSim sim;
	TextArea textArea;
	CheckBox javaLiteralCheckBox;
	String originalText;
	
	public ExportAsTextDialog(CirSim asim, String s) {
		super();
		closeOnEnter = false;
		sim = asim;
		originalText = s;
		TextArea ta;
		Button okButton, importButton, copyButton;
		vp=new VerticalPanel();
		setWidget(vp);
		setText(Locale.LS("Export as Text"));
		vp.add(new Label(Locale.LS("Text file for this circuit is...")));
		vp.add(ta= new TextArea());
		ta.setWidth("400px");
		ta.setHeight("300px");
		ta.setText(s);
		textArea = ta;
		
		// Add Java literal checkbox
		javaLiteralCheckBox = new CheckBox(Locale.LS("Java Literal Format"));
		javaLiteralCheckBox.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
			public void onValueChange(ValueChangeEvent<Boolean> event) {
				updateTextFormat();
			}
		});
		vp.add(javaLiteralCheckBox);
	//	vp.add(la2 = new Label(sim.LS("To save this file select it all (eg click in text and type control-A) and copy to your clipboard (eg control-C) before pasting to an empty text file (eg on Windows Notepad) and saving as a new file."), true));
	//	la2.setWidth("300px");
		HorizontalPanel hp = new HorizontalPanel();
		hp.setWidth("100%");
		hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
		hp.setStyleName("topSpace");
		vp.add(hp);
		hp.add(okButton = new Button(Locale.LS("OK")));
		hp.add(copyButton = new Button(Locale.LS("Copy to Clipboard")));
		hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
		hp.add(importButton = new Button(Locale.LS("Re-Import")));
		okButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				closeDialog();
			}
		});
		importButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				String s;
				sim.pushUndo();
				closeDialog();
//				s=textBox.getHTML();
//				s=s.replace("<br>", "\r");
				s=textArea.getText();
				if (s!=null) {
					sim.readCircuit(s);
					sim.allowSave(false);
				}
			}
		});
		copyButton.addClickHandler(new ClickHandler() {
		    public void onClick(ClickEvent event) {
			textArea.setFocus(true);
			textArea.selectAll();
			copyToClipboard();
			textArea.setSelectionRange(0,0);
		    }
		});
		this.center();
	}
	
	private void updateTextFormat() {
		if (javaLiteralCheckBox.getValue()) {
			// Convert to Java literal format
			String javaLiteral = convertToJavaLiteral(originalText);
			textArea.setText(javaLiteral);
		} else {
			// Show original format
			textArea.setText(originalText);
		}
	}
	
	private String convertToJavaLiteral(String text) {
		StringBuilder sb = new StringBuilder();
		sb.append("String circuit = \n");
		
		String[] lines = text.split("\n");
		for (int i = 0; i < lines.length; i++) {
			sb.append("    \"");
			// Escape any special characters
			String line = lines[i];
			line = line.replace("\\", "\\\\");  // Escape backslashes first
			line = line.replace("\"", "\\\"");  // Escape quotes
			sb.append(line);
			sb.append("\\n\"");
			if (i < lines.length - 1) {
				sb.append(" +");
			} else {
				sb.append(";");
			}
			sb.append("\n");
		}
		
		return sb.toString();
	}
	
	private static native boolean copyToClipboard() /*-{
	    return $doc.execCommand('copy');
	}-*/;

}
