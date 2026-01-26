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
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.lushprojects.circuitjs1.client.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * Dialog for viewing and editing the HintRegistry (glossary of variable hints).
 * Allows adding, editing, and deleting hints for any variable name.
 */
public class HintEditorDialog extends DialogBox {
    private CirSim sim;
    private FlexTable hintTable;
    private ScrollPanel scrollPanel;
    private TextBox newNameBox;
    private TextBox newHintBox;
    private Label statusLabel;
    private static HintEditorDialog instance = null;
    private static final int DIALOG_WIDTH = 400;
    
    public static void openDialog(CirSim s) {
        if (instance == null) {
            instance = new HintEditorDialog(s);
        } else {
            instance.refresh();
        }
        instance.show();
        instance.center();
    }
    
    public static void closeDialog() {
        if (instance != null) {
            instance.hide();
        }
    }
    
    public static boolean isOpen() {
        return instance != null && instance.isShowing();
    }
    
    public static void refreshIfOpen() {
        if (instance != null && instance.isShowing()) {
            instance.refresh();
        }
    }
    
    private HintEditorDialog(CirSim s) {
        sim = s;
        
        setModal(false);
        setGlassEnabled(false);
        
        setText(Locale.LS("Hint Editor (Glossary)"));
        
        VerticalPanel vp = new VerticalPanel();
        vp.setWidth(DIALOG_WIDTH + "px");
        setWidget(vp);
        
        // Add description label
        Label descLabel = new Label(Locale.LS("Edit hints for variables. Hints appear when hovering over elements."));
        descLabel.getElement().getStyle().setMarginBottom(8, Unit.PX);
        descLabel.getElement().getStyle().setFontSize(11, Unit.PX);
        descLabel.getElement().getStyle().setColor("#555");
        vp.add(descLabel);
        
        // Add new hint section
        HorizontalPanel addPanel = new HorizontalPanel();
        addPanel.setSpacing(3);
        addPanel.getElement().getStyle().setMarginBottom(8, Unit.PX);
        vp.add(addPanel);
        
        Label nameLabel = new Label(Locale.LS("Name:"));
        nameLabel.getElement().getStyle().setFontSize(12, Unit.PX);
        addPanel.add(nameLabel);
        
        newNameBox = new TextBox();
        newNameBox.setWidth("80px");
        newNameBox.getElement().getStyle().setProperty("padding", "3px 5px");
        addPanel.add(newNameBox);
        
        Label hintLabel = new Label(Locale.LS("Hint:"));
        hintLabel.getElement().getStyle().setFontSize(12, Unit.PX);
        hintLabel.getElement().getStyle().setMarginLeft(5, Unit.PX);
        addPanel.add(hintLabel);
        
        newHintBox = new TextBox();
        newHintBox.setWidth("180px");
        newHintBox.getElement().getStyle().setProperty("padding", "3px 5px");
        addPanel.add(newHintBox);
        
        Button addButton = new Button(Locale.LS("Add"));
        addButton.getElement().getStyle().setProperty("padding", "3px 10px");
        addButton.getElement().getStyle().setMarginLeft(5, Unit.PX);
        addButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                addNewHint();
            }
        });
        addPanel.add(addButton);
        
        // Create scrollable table
        scrollPanel = new ScrollPanel();
        scrollPanel.setSize("380px", "420px");
        vp.add(scrollPanel);
        
        hintTable = new FlexTable();
        hintTable.setCellPadding(2);
        hintTable.setCellSpacing(0);
        hintTable.setWidth("100%");
        hintTable.getElement().getStyle().setProperty("borderCollapse", "collapse");
        hintTable.getElement().getStyle().setFontSize(12, Unit.PX);
        scrollPanel.setWidget(hintTable);
        
        // Status label
        statusLabel = new Label("");
        statusLabel.getElement().getStyle().setMarginTop(5, Unit.PX);
        statusLabel.getElement().getStyle().setFontSize(11, Unit.PX);
        statusLabel.getElement().getStyle().setColor("#666");
        vp.add(statusLabel);
        
        // Add bottom buttons
        HorizontalPanel hp = new HorizontalPanel();
        hp.setWidth("100%");
        hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
        hp.getElement().getStyle().setMarginTop(8, Unit.PX);
        hp.setSpacing(3);
        vp.add(hp);
        
        Button refreshButton = new Button(Locale.LS("Refresh"));
        refreshButton.getElement().getStyle().setProperty("padding", "3px 8px");
        refreshButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                refresh();
            }
        });
        hp.add(refreshButton);
        
        Button importButton = new Button(Locale.LS("Import Variables"));
        importButton.getElement().getStyle().setProperty("padding", "3px 8px");
        importButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                importFromVariables();
            }
        });
        hp.add(importButton);
        
        // Spacer
        Label spacer = new Label();
        spacer.setWidth("100%");
        hp.add(spacer);
        hp.setCellWidth(spacer, "100%");
        
        Button closeButton = new Button(Locale.LS("Close"));
        closeButton.getElement().getStyle().setProperty("padding", "3px 10px");
        closeButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                hide();
            }
        });
        hp.add(closeButton);
        
        // Populate initial data
        refresh();
    }
    
    private void refresh() {
        // Clear existing rows
        hintTable.removeAllRows();
        
        // Add table headers
        hintTable.setText(0, 0, Locale.LS("Variable"));
        hintTable.setText(0, 1, Locale.LS("Description"));
        hintTable.setText(0, 2, ""); // Actions column
        hintTable.getRowFormatter().getElement(0).getStyle().setProperty("fontWeight", "600");
        hintTable.getRowFormatter().getElement(0).getStyle().setProperty("backgroundColor", "#f0f0f0");
        hintTable.getRowFormatter().getElement(0).getStyle().setProperty("borderBottom", "1px solid #ccc");
        hintTable.getCellFormatter().getElement(0, 0).getStyle().setProperty("padding", "4px 8px");
        hintTable.getCellFormatter().getElement(0, 1).getStyle().setProperty("padding", "4px 8px");
        
        // Get all hint names and sort them
        Set<String> names = HintRegistry.getAllNames();
        List<String> sortedNames = new ArrayList<String>(names);
        Collections.sort(sortedNames, String.CASE_INSENSITIVE_ORDER);
        
        // Populate table
        int row = 1;
        for (final String name : sortedNames) {
            String hint = HintRegistry.getHint(name);
            
            // Name column (non-editable, with Greek/subscript rendering)
            HTML nameLabel = new HTML(Locale.convertToHTML(name));
            nameLabel.getElement().getStyle().setProperty("fontWeight", "500");
            nameLabel.getElement().getStyle().setProperty("padding", "2px 6px");
            hintTable.setWidget(row, 0, nameLabel);
            
            // Hint column (editable textbox)
            final TextBox hintBox = new TextBox();
            hintBox.setText(hint != null ? hint : "");
            hintBox.setWidth("230px");
            hintBox.getElement().getStyle().setProperty("padding", "2px 4px");
            hintBox.getElement().getStyle().setFontSize(12, Unit.PX);
            final String varName = name;
            hintBox.addKeyUpHandler(new KeyUpHandler() {
                public void onKeyUp(KeyUpEvent event) {
                    // Auto-save on typing
                    String newHint = hintBox.getText().trim();
                    if (!newHint.isEmpty()) {
                        HintRegistry.setHint(varName, newHint);
                        setStatus("Saved: " + varName);
                    }
                }
            });
            hintTable.setWidget(row, 1, hintBox);
            
            // Delete button
            Button deleteBtn = new Button("✕");
            deleteBtn.setTitle(Locale.LS("Delete hint"));
            deleteBtn.getElement().getStyle().setProperty("padding", "1px 6px");
            deleteBtn.getElement().getStyle().setFontSize(10, Unit.PX);
            deleteBtn.getElement().getStyle().setColor("#888");
            deleteBtn.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    HintRegistry.removeHint(varName);
                    setStatus("Deleted: " + varName);
                    refresh();
                }
            });
            hintTable.setWidget(row, 2, deleteBtn);
            
            // Style alternating rows
            if (row % 2 == 0) {
                hintTable.getRowFormatter().getElement(row).getStyle().setProperty("backgroundColor", "#fafafa");
            }
            
            row++;
        }
        
        // Show message if no hints found
        if (sortedNames.isEmpty()) {
            hintTable.setText(1, 0, Locale.LS("No hints defined"));
            hintTable.getFlexCellFormatter().setColSpan(1, 0, 3);
            hintTable.getCellFormatter().getElement(1, 0).getStyle().setProperty("fontStyle", "italic");
            hintTable.getCellFormatter().getElement(1, 0).getStyle().setProperty("color", "#999");
        }
        
        setStatus("Total hints: " + sortedNames.size());
    }
    
    private void addNewHint() {
        String name = newNameBox.getText().trim();
        String hint = newHintBox.getText().trim();
        
        if (name.isEmpty()) {
            setStatus("Please enter a variable name");
            return;
        }
        if (hint.isEmpty()) {
            setStatus("Please enter a hint");
            return;
        }
        
        HintRegistry.setHint(name, hint);
        setStatus("Added hint for " + name);
        
        // Clear input fields
        newNameBox.setText("");
        newHintBox.setText("");
        
        refresh();
    }
    
    /**
     * Import all variables from the circuit that don't have hints yet
     */
    private void importFromVariables() {
        int added = 0;
        
        // Import stock variables
        Set<String> stockNames = StockFlowRegistry.getAllStockNames();
        if (stockNames != null) {
            for (String name : stockNames) {
                if (!HintRegistry.hasHint(name)) {
                    HintRegistry.setHint(name, " "); // Add space as placeholder
                    added++;
                }
            }
        }
        
        // Import labeled node names
        String[] labeledNodes = LabeledNodeElm.getSortedLabeledNodeNames();
        if (labeledNodes != null) {
            for (String name : labeledNodes) {
                if (!HintRegistry.hasHint(name)) {
                    HintRegistry.setHint(name, " "); // Add space as placeholder
                    added++;
                }
            }
        }
        
        // Import cell equation variables
        Set<String> cellVars = StockFlowRegistry.getAllCellEquationVariables();
        if (cellVars != null) {
            for (String name : cellVars) {
                if (!HintRegistry.hasHint(name)) {
                    HintRegistry.setHint(name, ""); // Add empty hint as placeholder
                    added++;
                }
            }
        }
        
        if (added > 0) {
            setStatus("Imported " + added + " new variables");
            refresh();
        } else {
            setStatus("No new variables to import");
        }
    }
    
    private void setStatus(String msg) {
        statusLabel.setText(msg);
    }
}
