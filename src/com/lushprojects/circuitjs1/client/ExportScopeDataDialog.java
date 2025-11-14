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
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.lushprojects.circuitjs1.client.util.Locale;

/**
 * Dialog for exporting scope data in CSV or JSON format.
 * Supports exporting either circular buffer (visible data) or full history (drawFromZero mode).
 */
public class ExportScopeDataDialog extends Dialog {
	
	VerticalPanel vp;
	Scope scope;
	RadioButton csvButton, jsonButton;
	RadioButton circularButton, historyButton;
	
	public ExportScopeDataDialog(Scope s) {
		super();
		scope = s;
		
		vp = new VerticalPanel();
		setWidget(vp);
		setText(Locale.LS("Export Scope Data"));
		
		vp.add(new Label(Locale.LS("Choose export format:")));
		
		HorizontalPanel formatPanel = new HorizontalPanel();
		csvButton = new RadioButton("format", "CSV");
		jsonButton = new RadioButton("format", "JSON");
		csvButton.setValue(true);
		formatPanel.add(csvButton);
		formatPanel.add(jsonButton);
		vp.add(formatPanel);
		
		vp.add(new Label("")); // Spacer
		vp.add(new Label(Locale.LS("Choose data source:")));
		
		HorizontalPanel dataPanel = new HorizontalPanel();
		circularButton = new RadioButton("data", Locale.LS("Visible Data (Circular Buffer)"));
		historyButton = new RadioButton("data", Locale.LS("Full History"));
		circularButton.setValue(true);
		dataPanel.add(circularButton);
		
		// Only show history option if drawFromZero is enabled and history exists
		if (scope.drawFromZero && scope.historySize > 0) {
			dataPanel.add(historyButton);
		} else {
			historyButton.setEnabled(false);
		}
		vp.add(dataPanel);
		
		HorizontalPanel hp = new HorizontalPanel();
		hp.setWidth("100%");
		hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
		hp.setStyleName("topSpace");
		vp.add(hp);
		
		Button exportButton = new Button(Locale.LS("Export"));
		Button cancelButton = new Button(Locale.LS("Cancel"));
		hp.add(exportButton);
		hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
		hp.add(cancelButton);
		
		exportButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				doExport();
				closeDialog();
			}
		});
		
		cancelButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				closeDialog();
			}
		});
		
		this.center();
	}
	
	void doExport() {
		boolean useCSV = csvButton.getValue();
		boolean useHistory = historyButton.getValue() && scope.drawFromZero && scope.historySize > 0;
		
		String data;
		String filename;
		
		if (useCSV) {
			data = useHistory ? scope.exportHistoryAsCSV() : scope.exportCircularBufferAsCSV();
			filename = "scope-data.csv";
		} else {
			data = useHistory ? scope.exportHistoryAsJSON() : scope.exportCircularBufferAsJSON();
			filename = "scope-data.json";
		}
		
		if (ExportAsLocalFileDialog.downloadIsSupported()) {
			new ExportAsLocalFileDialog(data) {
				{
					textBox.setText(filename);
				}
			};
		} else {
			// Fallback: show in text dialog
			new ExportAsTextDialog(scope.sim, data);
		}
	}
}
