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
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.Style.Cursor;
import com.lushprojects.circuitjs1.client.util.Locale;
import com.lushprojects.circuitjs1.client.ActionScheduler.ScheduledAction;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import java.util.List;

/**
 * Non-modal dialog for managing scheduled actions in the simulation.
 * Allows creating, editing, and deleting actions that execute at specific times.
 * Independent of ActionTimeElm elements.
 */
public class ActionTimeDialog extends DialogBox {
    private CirSim sim;
    private FlexTable actionTable;
    private ScrollPanel scrollPanel;
    private Label currentTimeLabel;
    private ActionScheduler scheduler;
    private static ActionTimeDialog instance = null;
    private static final int DIALOG_WIDTH = 750;
    private static final int LEFT_MARGIN = 20;
    private int draggedRow = -1;
    private int draggedActionId = -1;
    
    public static void openDialog(CirSim s) {
        if (instance == null) {
            instance = new ActionTimeDialog(s);
        } else {
            instance.refresh();
        }
        instance.show();
        
        // Position on right-hand side
        int windowWidth = com.google.gwt.user.client.Window.getClientWidth();
        int windowHeight = com.google.gwt.user.client.Window.getClientHeight();
        int dialogHeight = 500;
        
        int left = windowWidth - DIALOG_WIDTH - LEFT_MARGIN;
        int top = Math.max(20, (windowHeight - dialogHeight) / 2);
        
        instance.setPopupPosition(left, top);
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
    
    private ActionTimeDialog(CirSim s) {
        sim = s;
        scheduler = ActionScheduler.getInstance(sim);
        
        // Make dialog non-modal
        setModal(false);
        setGlassEnabled(false);
        
        // Prevent keyboard events from propagating to circuit
        Event.addNativePreviewHandler(new NativePreviewHandler() {
            public void onPreviewNativeEvent(NativePreviewEvent event) {
                if (isShowing()) {
                    int type = event.getTypeInt();
                    if ((type & Event.ONKEYDOWN) != 0 || (type & Event.ONKEYPRESS) != 0 || (type & Event.ONKEYUP) != 0) {
                        event.cancel();
                        event.getNativeEvent().stopPropagation();
                    }
                }
            }
        });
        
        setText(Locale.LS("Action Time Schedule"));
        
        VerticalPanel vp = new VerticalPanel();
        vp.setWidth(DIALOG_WIDTH + "px");
        setWidget(vp);
        
        // Add current time display
        currentTimeLabel = new Label();
        currentTimeLabel.getElement().getStyle().setMarginBottom(10, Unit.PX);
        currentTimeLabel.getElement().getStyle().setFontSize(12, Unit.PX);
        currentTimeLabel.getElement().getStyle().setFontWeight(com.google.gwt.dom.client.Style.FontWeight.BOLD);
        vp.add(currentTimeLabel);
        
        // Add description label
        Label descLabel = new Label(Locale.LS("Manage scheduled actions in simulation"));
        descLabel.getElement().getStyle().setMarginBottom(10, Unit.PX);
        descLabel.getElement().getStyle().setFontSize(11, Unit.PX);
        vp.add(descLabel);
        
        // Create scrollable table
        scrollPanel = new ScrollPanel();
        scrollPanel.setSize(DIALOG_WIDTH + "px", "400px");
        vp.add(scrollPanel);
        
        actionTable = new FlexTable();
        actionTable.setCellPadding(5);
        actionTable.setCellSpacing(0);
        actionTable.setWidth("100%");
        actionTable.getElement().getStyle().setProperty("borderCollapse", "collapse");
        scrollPanel.setWidget(actionTable);
        
        // Add table headers
        actionTable.setText(0, 0, Locale.LS("⋮⋮"));
        actionTable.setText(0, 1, Locale.LS("Time"));
        actionTable.setText(0, 2, Locale.LS("Slider/Action"));
        actionTable.setText(0, 3, Locale.LS("Value"));
        actionTable.setText(0, 4, Locale.LS("Text"));
        actionTable.setText(0, 5, Locale.LS("Enabled"));
        actionTable.setText(0, 6, Locale.LS("Status"));
        actionTable.setText(0, 7, Locale.LS("Actions"));
        
        // Style headers
        for (int col = 0; col < 8; col++) {
            actionTable.getCellFormatter().getElement(0, col).getStyle().setProperty("fontWeight", "bold");
            actionTable.getCellFormatter().getElement(0, col).getStyle().setProperty("backgroundColor", "#e0e0e0");
            actionTable.getCellFormatter().getElement(0, col).getStyle().setProperty("padding", "5px");
            actionTable.getCellFormatter().getElement(0, col).getStyle().setProperty("borderBottom", "2px solid #999");
        }
        // Make drag handle column narrower
        actionTable.getCellFormatter().getElement(0, 0).getStyle().setProperty("width", "30px");
        actionTable.getCellFormatter().getElement(0, 0).getStyle().setProperty("textAlign", "center");
        
        // Add bottom buttons
        HorizontalPanel hp = new HorizontalPanel();
        hp.setWidth("100%");
        hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
        hp.getElement().getStyle().setMarginTop(10, Unit.PX);
        vp.add(hp);
        
        // Add Pause Time input
        HorizontalPanel pausePanel = new HorizontalPanel();
        pausePanel.setSpacing(5);
        pausePanel.getElement().getStyle().setMarginRight(20, Unit.PX);
        
        Label pauseLabel = new Label(Locale.LS("Pause Time (s):"));
        pauseLabel.getElement().getStyle().setMarginRight(5, Unit.PX);
        
        final TextBox pauseTimeBox = new TextBox();
        pauseTimeBox.setWidth("60px");
        pauseTimeBox.setValue(String.valueOf(scheduler.getPauseTime()));
        pauseTimeBox.setTitle("Pause simulation for this many seconds after each action (0 = no pause, use ▶ icon to advance)");
        
        pauseTimeBox.addChangeHandler(new ChangeHandler() {
            public void onChange(ChangeEvent event) {
                try {
                    double pauseTime = Double.parseDouble(pauseTimeBox.getValue());
                    scheduler.setPauseTime(pauseTime);
                } catch (NumberFormatException e) {
                    pauseTimeBox.setValue(String.valueOf(scheduler.getPauseTime()));
                }
            }
        });
        
        pausePanel.add(pauseLabel);
        pausePanel.add(pauseTimeBox);
        
        // Add Animation Time input
        HorizontalPanel animPanel = new HorizontalPanel();
        animPanel.setSpacing(5);
        animPanel.getElement().getStyle().setMarginRight(20, Unit.PX);
        
        Label animLabel = new Label(Locale.LS("Animation Time (s):"));
        animLabel.getElement().getStyle().setMarginRight(5, Unit.PX);
        
        final TextBox animTimeBox = new TextBox();
        animTimeBox.setWidth("60px");
        animTimeBox.setValue(String.valueOf(scheduler.getAnimationTime()));
        animTimeBox.setTitle("Duration for animating slider changes (minimum 0.1s)");
        
        animTimeBox.addChangeHandler(new ChangeHandler() {
            public void onChange(ChangeEvent event) {
                try {
                    double animTime = Double.parseDouble(animTimeBox.getValue());
                    scheduler.setAnimationTime(animTime);
                } catch (NumberFormatException e) {
                    animTimeBox.setValue(String.valueOf(scheduler.getAnimationTime()));
                }
            }
        });
        
        animPanel.add(animLabel);
        animPanel.add(animTimeBox);
        
        // Add Enable Element checkbox
        HorizontalPanel enablePanel = new HorizontalPanel();
        enablePanel.setSpacing(5);
        enablePanel.getElement().getStyle().setMarginRight(20, Unit.PX);
        
        final CheckBox enableElementCheckbox = new CheckBox(Locale.LS("Enable Element"));
        enableElementCheckbox.setValue(isAnyActionElementEnabled());
        enableElementCheckbox.setTitle("Enable/disable ActionTimeElm elements (controls scheduler activation)");
        
        enableElementCheckbox.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                boolean enabled = enableElementCheckbox.getValue();
                setAllActionElementsEnabled(enabled);
            }
        });
        
        enablePanel.add(enableElementCheckbox);
        
        Button addButton = new Button(Locale.LS("Add Action"));
        Button refreshButton = new Button(Locale.LS("Refresh"));
        Button clearButton = new Button(Locale.LS("Clear All"));
        Button closeButton = new Button(Locale.LS("Close"));
        
        hp.add(pausePanel);
        hp.add(animPanel);
        hp.add(enablePanel);
        hp.add(addButton);
        hp.add(clearButton);
        hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
        hp.add(refreshButton);
        hp.add(closeButton);
        
        addButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                showEditDialog(null);
            }
        });
        
        clearButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                if (com.google.gwt.user.client.Window.confirm("Clear all actions?")) {
                    scheduler.clearAll();
                    refresh();
                }
            }
        });
        
        refreshButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                refresh();
            }
        });
        
        closeButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                hide();
            }
        });
        
        // Populate initial data
        refresh();
    }
    
    private void refresh() {
        // Update dialog title with simulation state icon
        String icon = sim.simIsRunning() ? "⏸" : "▶";
        setText(Locale.LS("Action Time Schedule") + " " + icon);
        
        // Update current time display
        String timeText = "Current Time: " + CircuitElm.getUnitText(sim.t, "s");
        currentTimeLabel.setText(timeText);
        
        // Clear existing rows (except header)
        while (actionTable.getRowCount() > 1) {
            actionTable.removeRow(1);
        }
        
        // Get all actions from scheduler
        List<ScheduledAction> actions = scheduler.getAllActions();
        
        // Populate table
        int row = 1;
        for (final ScheduledAction action : actions) {
            final int currentRow = row;
            final int currentIndex = row - 1;  // Index in actions list
            
            // Drag handle column
            Label dragHandle = new Label("⋮⋮");
            dragHandle.getElement().getStyle().setProperty("cursor", "move");
            dragHandle.getElement().getStyle().setProperty("fontSize", "18px");
            dragHandle.getElement().getStyle().setProperty("color", "#999");
            dragHandle.getElement().getStyle().setProperty("textAlign", "center");
            dragHandle.getElement().getStyle().setProperty("userSelect", "none");
            dragHandle.setTitle("Drag to reorder");
            
            // Add drag handlers
            final int actionId = action.id;
            dragHandle.addDomHandler(new MouseDownHandler() {
                public void onMouseDown(MouseDownEvent event) {
                    draggedRow = currentRow;
                    draggedActionId = actionId;
                    actionTable.getRowFormatter().getElement(currentRow).getStyle().setProperty("opacity", "0.5");
                    event.preventDefault();
                }
            }, MouseDownEvent.getType());
            
            actionTable.setWidget(row, 0, dragHandle);
            
            // Add row mouse over handler for drop target
            actionTable.getRowFormatter().addStyleName(row, "action-row");
            final com.google.gwt.dom.client.Element rowElement = actionTable.getRowFormatter().getElement(row);
            rowElement.setAttribute("data-row", String.valueOf(currentRow));
            rowElement.setAttribute("data-index", String.valueOf(currentIndex));
            
            com.google.gwt.user.client.Event.sinkEvents(rowElement, com.google.gwt.user.client.Event.ONMOUSEOVER | com.google.gwt.user.client.Event.ONMOUSEUP | com.google.gwt.user.client.Event.ONDBLCLICK);
            com.google.gwt.user.client.Event.setEventListener(rowElement, new com.google.gwt.user.client.EventListener() {
                public void onBrowserEvent(com.google.gwt.user.client.Event event) {
                    if (draggedRow > 0 && event.getTypeInt() == com.google.gwt.user.client.Event.ONMOUSEOVER) {
                        // Show drop target indicator
                        if (currentRow != draggedRow) {
                            rowElement.getStyle().setProperty("borderTop", "2px solid blue");
                        }
                    } else if (event.getTypeInt() == com.google.gwt.user.client.Event.ONMOUSEUP) {
                        // Handle drop
                        if (draggedRow > 0 && draggedRow != currentRow) {
                            scheduler.moveAction(draggedActionId, currentIndex);
                        }
                        // Reset drag state
                        if (draggedRow > 0) {
                            actionTable.getRowFormatter().getElement(draggedRow).getStyle().clearOpacity();
                        }
                        draggedRow = -1;
                        draggedActionId = -1;
                        // Clear all drop indicators
                        for (int i = 1; i < actionTable.getRowCount(); i++) {
                            actionTable.getRowFormatter().getElement(i).getStyle().setProperty("borderTop", "");
                        }
                    } else if (event.getTypeInt() == com.google.gwt.user.client.Event.ONDBLCLICK) {
                        // Handle double-click - open edit dialog
                        showEditDialog(action);
                    }
                }
            });
            
            // Action Time column
            actionTable.setText(row, 1, CircuitElm.getUnitText(action.actionTime, "s"));
            
            // Slider Name column
            String sliderName = action.sliderName;
            if (sliderName == null || sliderName.isEmpty()) {
                sliderName = "(none)";
            }
            actionTable.setText(row, 2, sliderName);
            
            // Slider Value column
            String valueText = getFormattedSliderValue(action.sliderName, action.sliderValue);
            actionTable.setText(row, 3, valueText);
            
            // Display Text column (show current text)
            String displayText;
            if (sim.t >= action.actionTime) {
                displayText = action.postText;
            } else {
                displayText = action.preText;
            }
            // Truncate if too long
            if (displayText.length() > 20) {
                displayText = displayText.substring(0, 17) + "...";
            }
            actionTable.setText(row, 4, displayText);
            
            // Enabled column (checkbox)
            final CheckBox enabledCheckbox = new CheckBox();
            enabledCheckbox.setValue(action.enabled);
            enabledCheckbox.setTitle("Enable/disable this action");
            enabledCheckbox.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    action.enabled = enabledCheckbox.getValue();
                    scheduler.updateAction(action);
                    refresh();
                }
            });
            actionTable.setWidget(row, 5, enabledCheckbox);
            
            // Status column - use state machine
            String status;
            Color statusColor;
            switch (action.state) {
                case READY:
                    status = "⚡ Ready";
                    statusColor = new Color(0, 200, 0);
                    break;
                case PENDING:
                    double timeToAction = action.actionTime - sim.t;
                    status = "⏱ " + CircuitElm.getUnitText(timeToAction, "s");
                    statusColor = new Color(150, 150, 0);
                    break;
                case WAITING:
                    status = "⏸ Waiting";
                    statusColor = new Color(255, 165, 0);
                    break;
                case EXECUTING:
                    status = "▶ Executing";
                    statusColor = new Color(0, 100, 200);
                    break;
                case COMPLETED:
                    status = "✓ Done";
                    statusColor = new Color(0, 150, 0);
                    break;
                default:
                    status = "?";
                    statusColor = new Color(150, 150, 150);
                    break;
            }
            actionTable.setText(row, 6, status);
            actionTable.getCellFormatter().getElement(row, 6).getStyle().setProperty("color", 
                "rgb(" + statusColor.getRed() + "," + statusColor.getGreen() + "," + statusColor.getBlue() + ")");
            actionTable.getCellFormatter().getElement(row, 6).getStyle().setProperty("fontWeight", "bold");
            
            // Actions column (Copy/Edit/Delete/Paste buttons)
            HorizontalPanel actionPanel = new HorizontalPanel();
            actionPanel.setSpacing(3);
            
            Button copyBtn = new Button("Copy");
            copyBtn.getElement().getStyle().setFontSize(10, Unit.PX);
            copyBtn.setTitle("Copy this action");
            copyBtn.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    scheduler.copyAction(action.id);
                    refresh();
                }
            });
            
            Button pasteBtn = new Button("Paste");
            pasteBtn.getElement().getStyle().setFontSize(10, Unit.PX);
            pasteBtn.setTitle("Paste copied action here");
            pasteBtn.setEnabled(scheduler.hasClipboard());
            pasteBtn.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    scheduler.pasteAction();
                    refresh();
                }
            });
            
            Button editBtn = new Button("Edit");
            editBtn.getElement().getStyle().setFontSize(10, Unit.PX);
            editBtn.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    showEditDialog(action);
                }
            });
            
            Button deleteBtn = new Button("Del");
            deleteBtn.getElement().getStyle().setFontSize(10, Unit.PX);
            deleteBtn.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    if (com.google.gwt.user.client.Window.confirm("Delete this action?")) {
                        scheduler.deleteAction(action.id);
                        refresh();
                    }
                }
            });
            

            actionPanel.add(editBtn);
            actionPanel.add(deleteBtn);
            actionPanel.add(copyBtn);
            actionPanel.add(pasteBtn);
            actionTable.setWidget(row, 7, actionPanel);
            
            // Style the row with alternating colors
            if (row % 2 == 0) {
                actionTable.getRowFormatter().getElement(row).getStyle().setProperty("backgroundColor", "#f9f9f9");
            }
            
            // Gray out disabled actions
            if (!action.enabled) {
                actionTable.getRowFormatter().getElement(row).getStyle().setProperty("opacity", "0.5");
                actionTable.getRowFormatter().getElement(row).getStyle().setProperty("color", "#888");
            }
            
            // Highlight row based on state
            if (action.state == ActionScheduler.ActionState.COMPLETED) {
                actionTable.getRowFormatter().getElement(row).getStyle().setProperty("backgroundColor", "#c8e6c9");
            } else if (action.state == ActionScheduler.ActionState.WAITING || action.state == ActionScheduler.ActionState.EXECUTING) {
                actionTable.getRowFormatter().getElement(row).getStyle().setProperty("backgroundColor", "#fff3cd");
            }
            
            // Add border between rows
            for (int col = 0; col < 8; col++) {
                actionTable.getCellFormatter().getElement(row, col).getStyle().setProperty("borderBottom", "1px solid #ddd");
            }
            
            row++;
        }
        
        // Show message if no actions found
        if (actions.isEmpty() || row == 1) {
            actionTable.setText(1, 0, Locale.LS("No actions scheduled. Click 'Add Action' to create one."));
            actionTable.getFlexCellFormatter().setColSpan(1, 0, 8);
            actionTable.getCellFormatter().getElement(1, 0).getStyle().setProperty("fontStyle", "italic");
            actionTable.getCellFormatter().getElement(1, 0).getStyle().setProperty("color", "#999");
            actionTable.getCellFormatter().getElement(1, 0).getStyle().setProperty("textAlign", "center");
            actionTable.getCellFormatter().getElement(1, 0).getStyle().setProperty("padding", "20px");
        }
    }
    
    private void showEditDialog(final ScheduledAction existingAction) {
        final DialogBox editDialog = new DialogBox();
        editDialog.setModal(true);
        editDialog.setGlassEnabled(true);
        editDialog.setText(existingAction == null ? "Add New Action" : "Edit Action");
        
        VerticalPanel vp = new VerticalPanel();
        vp.setSpacing(10);
        vp.getElement().getStyle().setPadding(15, Unit.PX);
        editDialog.setWidget(vp);
        
        // Create form fields
        final TextBox timeBox = new TextBox();
        timeBox.setWidth("200px");
        timeBox.setValue(existingAction == null ? CircuitElm.getUnitText(sim.t, "") : String.valueOf(existingAction.actionTime));
        
        final ListBox sliderBox = new ListBox();
        sliderBox.setWidth("200px");
        List<String> sliders = scheduler.getAvailableSliders();
        sliderBox.addItem("(none)", "");
        for (String slider : sliders) {
            sliderBox.addItem(slider, slider);
        }
        if (existingAction != null && existingAction.sliderName != null) {
            for (int i = 0; i < sliderBox.getItemCount(); i++) {
                if (sliderBox.getValue(i).equals(existingAction.sliderName)) {
                    sliderBox.setSelectedIndex(i);
                    break;
                }
            }
        }
        
        final TextBox valueBox = new TextBox();
        valueBox.setWidth("200px");
        valueBox.setValue(existingAction == null ? "0.0" : String.valueOf(existingAction.sliderValue));
        
        // Add change handler to slider dropdown to auto-populate current value
        sliderBox.addChangeHandler(new ChangeHandler() {
            public void onChange(ChangeEvent event) {
                String selectedSlider = sliderBox.getValue(sliderBox.getSelectedIndex());
                if (selectedSlider != null && !selectedSlider.isEmpty()) {
                    double currentValue = scheduler.getSliderValue(selectedSlider);
                    valueBox.setValue(String.valueOf(currentValue));
                }
            }
        });
        
        final TextBox postTextBox = new TextBox();
        postTextBox.setWidth("200px");
        postTextBox.setValue(existingAction == null ? "After" : existingAction.postText);
        
        final CheckBox enabledBox = new CheckBox();
        enabledBox.setValue(existingAction == null ? true : existingAction.enabled);
        
        // Add form rows
        vp.add(createFormRow("Action Time (s):", timeBox));
        vp.add(createFormRow("Slider/Action Name:", sliderBox));
        vp.add(createFormRow("Value:", valueBox));
        vp.add(createFormRow("Display Text:", postTextBox));
        vp.add(createFormRow("Enabled:", enabledBox));
        
        // Add buttons
        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setSpacing(10);
        buttonPanel.getElement().getStyle().setMarginTop(15, Unit.PX);
        
        Button saveButton = new Button("Save");
        Button cancelButton = new Button("Cancel");
        
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        vp.add(buttonPanel);
        
        saveButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                try {
                    double time = Double.parseDouble(timeBox.getValue());
                    String slider = sliderBox.getValue(sliderBox.getSelectedIndex());
                    double value = Double.parseDouble(valueBox.getValue());
                    String preText = "";  // No longer editable
                    String postText = postTextBox.getValue();
                    boolean enabled = enabledBox.getValue();
                    boolean stopSimulation = false; // Stop simulation not exposed in UI
                    
                    if (existingAction == null) {
                        // Create new action
                        ScheduledAction newAction = new ScheduledAction(0, time, slider,
                            value, preText, postText, enabled, stopSimulation);
                        scheduler.addAction(newAction);
                    } else {
                        // Update existing action
                        existingAction.actionTime = time;
                        existingAction.sliderName = slider;
                        existingAction.sliderValue = value;
                        existingAction.preText = preText;
                        existingAction.postText = postText;
                        existingAction.enabled = enabled;
                        existingAction.stopSimulation = stopSimulation;
                        scheduler.updateAction(existingAction);
                    }
                    
                    refresh();
                    editDialog.hide();
                } catch (NumberFormatException ex) {
                    com.google.gwt.user.client.Window.alert("Invalid number format");
                }
            }
        });
        
        cancelButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                editDialog.hide();
            }
        });
        
        editDialog.center();
        editDialog.show();
    }
    
    /**
     * Get formatted value for a slider/action, checking element for custom formatting
     */
    private String getFormattedSliderValue(String sliderName, double value) {
        // Find the adjustable with this name
        for (int i = 0; i < sim.adjustables.size(); i++) {
            Adjustable adj = sim.adjustables.get(i);
            if (adj.sliderText != null && adj.sliderText.equals(sliderName)) {
                EditInfo ei = adj.elm.getEditInfo(adj.editItem);
                if (ei != null) {
                    // Check if element has custom slider text formatting
                    try {
                        String customText = adj.elm.getSliderUnitText(adj.editItem, ei, value);
                        if (customText != null)
                            return customText;
                    } catch (Exception e) {
                        // Element doesn't have custom formatting
                    }
                    // Use default formatting
                    return EditDialog.unitString(ei, value);
                }
            }
        }
        // Fallback to simple format
        return CircuitElm.showFormat.format(value);
    }
    
    private HorizontalPanel createFormRow(String labelText, com.google.gwt.user.client.ui.Widget widget) {
        HorizontalPanel row = new HorizontalPanel();
        row.setWidth("100%");
        row.setSpacing(10);
        
        Label label = new Label(labelText);
        label.setWidth("150px");
        label.getElement().getStyle().setProperty("textAlign", "right");
        
        row.add(label);
        row.add(widget);
        
        return row;
    }
    
    /**
     * Check if any ActionTimeElm is enabled in the circuit
     */
    private boolean isAnyActionElementEnabled() {
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof ActionTimeElm && ((ActionTimeElm) ce).enabled) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Set enabled state for all ActionTimeElm elements in the circuit
     */
    private void setAllActionElementsEnabled(boolean enabled) {
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof ActionTimeElm) {
                ((ActionTimeElm) ce).enabled = enabled;
            }
        }
        sim.needAnalyze();
    }
}
