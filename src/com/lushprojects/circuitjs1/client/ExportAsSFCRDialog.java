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
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.lushprojects.circuitjs1.client.util.Locale;

public class ExportAsSFCRDialog extends Dialog {

    VerticalPanel vp;
    CirSim sim;
    TextArea textArea;
    RadioButton blockFormatButton;
    RadioButton rStyleButton;

    public ExportAsSFCRDialog(CirSim asim) {
        super();
        closeOnEnter = false;
        sim = asim;

        vp = new VerticalPanel();
        setWidget(vp);
        setText(Locale.LS("Export As SFCR"));

        vp.add(new Label(Locale.LS("SFCR text for this circuit:")));

        HorizontalPanel formatPanel = new HorizontalPanel();
        formatPanel.setStyleName("topSpace");
        formatPanel.setSpacing(8);
        formatPanel.add(new Label(Locale.LS("Format:")));

        blockFormatButton = new RadioButton("sfcrExportFormat", "Block format (@equations/@matrix)");
        rStyleButton = new RadioButton("sfcrExportFormat", "R sfcr syntax (sfcr_set/sfcr_matrix)");
        blockFormatButton.setValue(true);

        ValueChangeHandler<Boolean> formatChangeHandler = new ValueChangeHandler<Boolean>() {
            public void onValueChange(ValueChangeEvent<Boolean> event) {
                updateExportText();
            }
        };
        blockFormatButton.addValueChangeHandler(formatChangeHandler);
        rStyleButton.addValueChangeHandler(formatChangeHandler);

        formatPanel.add(blockFormatButton);
        formatPanel.add(rStyleButton);
        vp.add(formatPanel);

        textArea = new TextArea();
        textArea.setWidth("600px");
        textArea.setHeight("350px");
        vp.add(textArea);

        HorizontalPanel hp = new HorizontalPanel();
        hp.setWidth("100%");
        hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
        hp.setStyleName("topSpace");
        vp.add(hp);

        Button okButton = new Button(Locale.LS("OK"));
        Button copyButton = new Button(Locale.LS("Copy to Clipboard"));
        Button importButton = new Button(Locale.LS("Re-Import"));

        hp.add(okButton);
        hp.add(copyButton);
        hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
        hp.add(importButton);

        okButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                closeDialog();
            }
        });

        copyButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                textArea.setFocus(true);
                textArea.selectAll();
                copyToClipboard();
                textArea.setSelectionRange(0, 0);
            }
        });

        importButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                String s;
                sim.pushUndo();
                closeDialog();
                s = textArea.getText();
                if (s != null) {
                    sim.readCircuit(s);
                    sim.allowSave(false);
                }
            }
        });

        updateExportText();
        this.center();
    }

    private void updateExportText() {
        SFCRExporter.ExportSyntax syntax = blockFormatButton.getValue()
                ? SFCRExporter.ExportSyntax.BLOCK_FORMAT
                : SFCRExporter.ExportSyntax.R_STYLE;
        SFCRExporter exporter = new SFCRExporter(sim, syntax);
        textArea.setText(exporter.export());
    }

    private static native boolean copyToClipboard() /*-{
        return $doc.execCommand('copy');
    }-*/;
}
