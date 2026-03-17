package com.lushprojects.circuitjs1.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.lushprojects.circuitjs1.client.util.Locale;

public class LookupTablesEditorDialog extends Dialog {

    private final CirSim sim;
    private final TextArea textArea;

    public LookupTablesEditorDialog(CirSim sim) {
        super();
        this.sim = sim;
        closeOnEnter = false;

        VerticalPanel vp = new VerticalPanel();
        setWidget(vp);
        setText(Locale.LS("Edit Lookup Tables"));

        vp.add(new Label(Locale.LS("Edit all @lookup blocks below.")));

        textArea = new TextArea();
        textArea.setWidth("720px");
        textArea.setHeight("380px");
        textArea.setText(LookupBlocksTextUtil.extractLookupBlocks(getWorkingModelSource()));
        vp.add(textArea);

        HorizontalPanel buttons = new HorizontalPanel();
        vp.add(buttons);

        Button saveButton = new Button(Locale.LS("Save"));
        saveButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                saveChanges();
            }
        });
        buttons.add(saveButton);

        Button cancelButton = new Button(Locale.LS("Cancel"));
        cancelButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                closeDialog();
            }
        });
        buttons.add(cancelButton);

        center();
        show();
    }

    private String getWorkingModelSource() {
        String source = sim.getModelInfoEditorContent();
        if (source != null && SFCRParser.isSFCRFormat(source)) {
            return source;
        }
        return new SFCRExporter(sim).export();
    }

    private void saveChanges() {
        String base = getWorkingModelSource();
        String editedLookupBlocks = textArea.getText();
        String merged = LookupBlocksTextUtil.mergeLookupBlocks(base, editedLookupBlocks);

        try {
            SFCRParser.parseToResult(merged, true);
        } catch (Throwable t) {
            sim.alertOrWarn(Locale.LS("Lookup text is not valid SFCR: ") + t.getMessage());
            return;
        }

        sim.pushUndo();
        sim.importCircuitFromText(merged, false);
        closeDialog();
    }
}