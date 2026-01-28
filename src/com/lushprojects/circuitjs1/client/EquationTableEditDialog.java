/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.lushprojects.circuitjs1.client.util.Locale;

/**
 * EquationTableEditDialog - Grid-based editor for EquationTableElm
 * 
 * Displays a grid with columns: Buttons | Output Name | Equation | Slider Var | Slider Value
 * Each row represents one equation with move up/down, add, delete buttons.
 * 
 * Pattern follows TableEditDialog for row manipulation.
 */
public class EquationTableEditDialog extends Dialog {
    
    // Unicode symbols for contextual buttons
    private static final String SYMBOL_ADD = "➕";
    private static final String SYMBOL_DELETE = "➖";
    private static final String SYMBOL_UP = "⇑";
    private static final String SYMBOL_DOWN = "⇓";
    
    // Grid column indices
    private static final int COL_BUTTONS = 0;
    private static final int COL_OUTPUT_NAME = 1;
    private static final int COL_EQUATION = 2;
    private static final int COL_INITIAL_VALUE = 3;
    private static final int COL_SLIDER_VAR = 4;
    private static final int COL_SLIDER_VALUE = 5;
    private static final int COL_HINT = 6;
    private static final int NUM_COLS = 7;
    
    // Grid row indices
    private static final int HEADER_ROW = 0;
    private static final int DATA_START_ROW = 1;
    
    // Max rows
    private static final int MAX_ROWS = 8;
    
    // Core references
    private final EquationTableElm tableElement;
    private final CirSim sim;
    
    // UI Components
    private ScrollPanel scrollPanel;
    private Grid editGrid;
    private Button applyButton;
    private Label statusLabel;
    private TextBox tableNameBox;
    
    // Data storage (copied from element for editing)
    private int rowCount;
    private String[] outputNames;
    private String[] equations;
    private String[] initialEquations;
    private String[] sliderVarNames;
    private double[] sliderValues;
    private String[] hints;
    
    // Track changes
    private boolean hasChanges = false;
    
    // Autocomplete state per textbox
    private java.util.Map<TextBox, AutocompleteHelper.AutocompleteState> autocompleteStates = 
        new java.util.HashMap<TextBox, AutocompleteHelper.AutocompleteState>();
    
    public EquationTableEditDialog(EquationTableElm tableElm, CirSim cirSim) {
        super();
        this.closeOnEnter = false;
        this.tableElement = tableElm;
        this.sim = cirSim;
        
        // Copy data from element
        copyTableData();
        
        setText(Locale.LS("Edit Equation Table"));
        
        setupUI();
        populateGrid();
        
        show();
        setPopupPosition(20, 20);
    }
    
    /**
     * Copy data from EquationTableElm for editing
     */
    private void copyTableData() {
        rowCount = tableElement.getRowCount();
        outputNames = new String[MAX_ROWS];
        equations = new String[MAX_ROWS];
        initialEquations = new String[MAX_ROWS];
        sliderVarNames = new String[MAX_ROWS];
        sliderValues = new double[MAX_ROWS];
        hints = new String[MAX_ROWS];
        
        for (int i = 0; i < MAX_ROWS; i++) {
            outputNames[i] = tableElement.getOutputName(i);
            equations[i] = tableElement.getEquation(i);
            initialEquations[i] = tableElement.getInitialEquation(i);
            sliderVarNames[i] = tableElement.getSliderVarName(i);
            sliderValues[i] = tableElement.getSliderValue(i);
            // Get hint from central HintRegistry
            String hint = HintRegistry.getHint(outputNames[i]);
            hints[i] = (hint != null) ? hint : "";
        }
    }
    
    /**
     * Setup the main UI components
     */
    private void setupUI() {
        VerticalPanel mainPanel = new VerticalPanel();
        mainPanel.setWidth("100%");
        setWidget(mainPanel);
        
        // Table name editor at the top
        HorizontalPanel titlePanel = new HorizontalPanel();
        titlePanel.addStyleName("topSpace");
        Label titleLabel = new Label("Table Name:");
        titleLabel.setWidth("80px");
        titlePanel.add(titleLabel);
        
        tableNameBox = new TextBox();
        tableNameBox.setText(tableElement.getTableName());
        tableNameBox.setWidth("200px");
        tableNameBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                markChanged();
            }
        });
        titlePanel.add(tableNameBox);
        
        // Row count label
        Label rowCountLabel = new Label("Rows: " + rowCount);
        rowCountLabel.getElement().getStyle().setProperty("marginLeft", "20px");
        titlePanel.add(rowCountLabel);
        
        mainPanel.add(titlePanel);
        
        // Scrollable table area
        scrollPanel = new ScrollPanel();
        scrollPanel.setWidth("780px");
        scrollPanel.setHeight("300px");
        scrollPanel.addStyleName("topSpace");
        mainPanel.add(scrollPanel);
        
        // Bottom buttons
        HorizontalPanel buttonPanel = createBottomButtons();
        mainPanel.add(buttonPanel);
        
        // Status label
        statusLabel = new Label("Edit equations - use buttons to add/remove/move rows");
        statusLabel.addStyleName("topSpace");
        mainPanel.add(statusLabel);
    }
    
    /**
     * Create the bottom button panel
     */
    private HorizontalPanel createBottomButtons() {
        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setWidth("100%");
        buttonPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
        buttonPanel.addStyleName("topSpace");
        buttonPanel.setSpacing(5);
        
        applyButton = new Button(Locale.LS("Apply"));
        applyButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                applyChanges();
            }
        });
        buttonPanel.add(applyButton);
        
        Button okButton = new Button(Locale.LS("OK"));
        okButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                applyChanges();
                closeDialog();
            }
        });
        buttonPanel.add(okButton);
        
        Button propertiesButton = new Button(Locale.LS("Properties..."));
        propertiesButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                applyChanges();
                closeDialog();
                sim.doEdit(tableElement);
            }
        });
        buttonPanel.add(propertiesButton);
        
        // Spacer
        Label spacer = new Label();
        spacer.setWidth("100%");
        buttonPanel.add(spacer);
        buttonPanel.setCellWidth(spacer, "100%");
        
        Button closeButton = new Button(Locale.LS("Close"));
        closeButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                closeDialog();
            }
        });
        buttonPanel.add(closeButton);
        
        return buttonPanel;
    }
    
    /**
     * Create and populate the grid
     */
    private void populateGrid() {
        // Grid: header row + data rows
        int totalRows = DATA_START_ROW + rowCount;
        editGrid = new Grid(totalRows, NUM_COLS);
        editGrid.addStyleName("tableEditGrid");
        editGrid.setCellSpacing(1);
        editGrid.setCellPadding(2);
        
        // Header row
        editGrid.setText(HEADER_ROW, COL_BUTTONS, "");
        
        Label headerOutput = new Label("Output Name");
        headerOutput.getElement().getStyle().setProperty("fontWeight", "bold");
        editGrid.setWidget(HEADER_ROW, COL_OUTPUT_NAME, headerOutput);
        
        Label headerEquation = new Label("Equation");
        headerEquation.getElement().getStyle().setProperty("fontWeight", "bold");
        editGrid.setWidget(HEADER_ROW, COL_EQUATION, headerEquation);
        
        Label headerInitial = new Label("Initial (t=0)");
        headerInitial.getElement().getStyle().setProperty("fontWeight", "bold");
        editGrid.setWidget(HEADER_ROW, COL_INITIAL_VALUE, headerInitial);
        
        Label headerSliderVar = new Label("Slider Var");
        headerSliderVar.getElement().getStyle().setProperty("fontWeight", "bold");
        editGrid.setWidget(HEADER_ROW, COL_SLIDER_VAR, headerSliderVar);
        
        Label headerSliderVal = new Label("Slider Value");
        headerSliderVal.getElement().getStyle().setProperty("fontWeight", "bold");
        editGrid.setWidget(HEADER_ROW, COL_SLIDER_VALUE, headerSliderVal);
        
        Label headerHint = new Label("Hint");
        headerHint.getElement().getStyle().setProperty("fontWeight", "bold");
        editGrid.setWidget(HEADER_ROW, COL_HINT, headerHint);
        
        // Data rows
        for (int row = 0; row < rowCount; row++) {
            populateDataRow(row);
        }
        
        scrollPanel.setWidget(editGrid);
    }
    
    /**
     * Populate a single data row
     */
    private void populateDataRow(final int row) {
        int gridRow = DATA_START_ROW + row;
        
        // Buttons column
        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setSpacing(1);
        
        // Add row button
        Button addBtn = createButton(SYMBOL_ADD, "Add row after this one");
        addBtn.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                insertRowAfter(row);
            }
        });
        buttonPanel.add(addBtn);
        
        // Delete row button (only if more than 1 row)
        if (rowCount > 1) {
            Button delBtn = createButton(SYMBOL_DELETE, "Delete this row");
            delBtn.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    deleteRow(row);
                }
            });
            buttonPanel.add(delBtn);
        }
        
        // Move up button (not on first row)
        if (row > 0) {
            Button upBtn = createButton(SYMBOL_UP, "Move row up");
            upBtn.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    moveRow(row, row - 1);
                }
            });
            buttonPanel.add(upBtn);
        }
        
        // Move down button (not on last row)
        if (row < rowCount - 1) {
            Button downBtn = createButton(SYMBOL_DOWN, "Move row down");
            downBtn.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    moveRow(row, row + 1);
                }
            });
            buttonPanel.add(downBtn);
        }
        
        editGrid.setWidget(gridRow, COL_BUTTONS, buttonPanel);
        
        // Output name textbox
        final TextBox outputNameBox = new TextBox();
        outputNameBox.setText(outputNames[row]);
        outputNameBox.setWidth("80px");
        outputNameBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                outputNames[row] = outputNameBox.getText();
                markChanged();
            }
        });
        addSelectAllOnFocus(outputNameBox);
        editGrid.setWidget(gridRow, COL_OUTPUT_NAME, outputNameBox);
        
        // Equation textbox with autocomplete
        VerticalPanel eqPanel = createEquationTextBox(row, false);
        editGrid.setWidget(gridRow, COL_EQUATION, eqPanel);
        
        // Initial value textbox with autocomplete
        VerticalPanel initPanel = createEquationTextBox(row, true);
        editGrid.setWidget(gridRow, COL_INITIAL_VALUE, initPanel);
        
        // Slider variable name textbox
        final TextBox sliderVarBox = new TextBox();
        sliderVarBox.setText(sliderVarNames[row]);
        sliderVarBox.setWidth("60px");
        sliderVarBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                sliderVarNames[row] = sliderVarBox.getText();
                markChanged();
            }
        });
        addSelectAllOnFocus(sliderVarBox);
        editGrid.setWidget(gridRow, COL_SLIDER_VAR, sliderVarBox);
        
        // Slider value textbox
        final TextBox sliderValBox = new TextBox();
        sliderValBox.setText(CircuitElm.getShortUnitText(sliderValues[row], ""));
        sliderValBox.setWidth("80px");
        sliderValBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                try {
                    sliderValues[row] = EditDialog.parseUnits(sliderValBox.getText());
                    sliderValBox.getElement().getStyle().clearBackgroundColor();
                    markChanged();
                } catch (Exception e) {
                    sliderValBox.getElement().getStyle().setProperty("backgroundColor", "#ffcccc");
                }
            }
        });
        sliderValBox.addBlurHandler(new BlurHandler() {
            public void onBlur(BlurEvent event) {
                // Reformat on blur
                sliderValBox.setText(CircuitElm.getShortUnitText(sliderValues[row], ""));
            }
        });
        addSelectAllOnFocus(sliderValBox);
        editGrid.setWidget(gridRow, COL_SLIDER_VALUE, sliderValBox);
        
        // Hint textbox
        final TextBox hintBox = new TextBox();
        hintBox.setText(hints[row]);
        hintBox.setWidth("120px");
        hintBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                hints[row] = hintBox.getText();
                markChanged();
            }
        });
        addSelectAllOnFocus(hintBox);
        editGrid.setWidget(gridRow, COL_HINT, hintBox);
    }
    
    /**
     * Create equation textbox with autocomplete
     * @param row the row index
     * @param isInitial true for initial value equation, false for main equation
     */
    private VerticalPanel createEquationTextBox(final int row, final boolean isInitial) {
        final TextBox textBox = new TextBox();
        textBox.setText(isInitial ? initialEquations[row] : equations[row]);
        textBox.setWidth(isInitial ? "100px" : "200px");
        
        final Label hintLabel = new Label();
        hintLabel.getElement().getStyle().setProperty("fontSize", "10px");
        hintLabel.getElement().getStyle().setProperty("color", "#888");
        hintLabel.setVisible(false);
        
        final java.util.List<String> completionList = createCompletionList(row);
        final AutocompleteHelper.AutocompleteState state = new AutocompleteHelper.AutocompleteState();
        autocompleteStates.put(textBox, state);
        
        textBox.addKeyDownHandler(new KeyDownHandler() {
            public void onKeyDown(KeyDownEvent event) {
                if (event.getNativeKeyCode() == KeyCodes.KEY_TAB) {
                    event.preventDefault();
                    event.stopPropagation();
                    AutocompleteHelper.handleTabCompletion(textBox, completionList, hintLabel, state);
                } else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
                    state.reset();
                    hintLabel.setVisible(false);
                }
            }
        });
        
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                if (isInitial) {
                    initialEquations[row] = textBox.getText();
                } else {
                    equations[row] = textBox.getText();
                }
                markChanged();
                
                int keyCode = event.getNativeKeyCode();
                if (keyCode != KeyCodes.KEY_TAB && keyCode != KeyCodes.KEY_SHIFT) {
                    state.reset();
                    hintLabel.setVisible(false);
                }
            }
        });
        
        textBox.addBlurHandler(new BlurHandler() {
            public void onBlur(BlurEvent event) {
                state.reset();
                hintLabel.setVisible(false);
            }
        });
        
        addSelectAllOnFocus(textBox);
        
        VerticalPanel container = new VerticalPanel();
        container.add(textBox);
        container.add(hintLabel);
        return container;
    }
    
    /**
     * Create completion list for equation autocomplete
     */
    private java.util.List<String> createCompletionList(int row) {
        java.util.List<String> completions = new java.util.ArrayList<String>();
        
        // Add stock variables
        java.util.Set<String> stockNames = StockFlowRegistry.getAllStockNames();
        if (stockNames != null) {
            for (String name : stockNames) {
                if (!completions.contains(name)) completions.add(name);
            }
        }
        
        // Add labeled node names
        String[] labeledNodes = LabeledNodeElm.getSortedLabeledNodeNames();
        if (labeledNodes != null) {
            for (String name : labeledNodes) {
                if (!completions.contains(name)) completions.add(name);
            }
        }
        
        // Add output names from this table
        for (int i = 0; i < rowCount; i++) {
            if (!completions.contains(outputNames[i])) {
                completions.add(outputNames[i]);
            }
        }
        
        // Add this row's slider variable
        if (!completions.contains(sliderVarNames[row])) {
            completions.add(sliderVarNames[row]);
        }
        
        // Add math functions
        String[] funcs = {"sin", "cos", "tan", "exp", "log", "sqrt", "abs", "min", "max", "pow", "floor", "ceil"};
        for (String f : funcs) {
            if (!completions.contains(f)) completions.add(f);
        }
        
        // Add constants
        if (!completions.contains("pi")) completions.add("pi");
        if (!completions.contains("t")) completions.add("t");
        
        return completions;
    }
    
    /**
     * Add select-all-on-focus behavior
     */
    private void addSelectAllOnFocus(final TextBox textBox) {
        textBox.addFocusHandler(new FocusHandler() {
            public void onFocus(FocusEvent event) {
                textBox.selectAll();
            }
        });
    }
    
    /**
     * Create a styled button
     */
    private Button createButton(String symbol, String tooltip) {
        Button btn = new Button(symbol);
        btn.setTitle(tooltip);
        btn.setStyleName("contextButton");
        return btn;
    }
    
    /**
     * Insert a new row after the specified index
     */
    private void insertRowAfter(int afterRow) {
        if (rowCount >= MAX_ROWS) {
            setStatus("Maximum rows (" + MAX_ROWS + ") reached");
            return;
        }
        
        int insertAt = afterRow + 1;
        
        // Shift rows down
        for (int i = rowCount - 1; i >= insertAt; i--) {
            outputNames[i + 1] = outputNames[i];
            equations[i + 1] = equations[i];
            initialEquations[i + 1] = initialEquations[i];
            sliderVarNames[i + 1] = sliderVarNames[i];
            sliderValues[i + 1] = sliderValues[i];
            hints[i + 1] = hints[i];
        }
        
        // Initialize new row
        outputNames[insertAt] = "Y" + (insertAt + 1);
        equations[insertAt] = "0";
        initialEquations[insertAt] = "";
        sliderVarNames[insertAt] = String.valueOf((char)('a' + insertAt));
        sliderValues[insertAt] = 0.5;
        hints[insertAt] = "hint" + (insertAt + 1);
        
        rowCount++;
        
        setStatus("Row added at position " + (insertAt + 1) + ". Total rows: " + rowCount);
        markChanged();
        populateGrid();
    }
    
    /**
     * Delete a row
     */
    private void deleteRow(int rowIndex) {
        if (rowCount <= 1) {
            setStatus("Cannot delete the last row");
            return;
        }
        
        // Shift rows up
        for (int i = rowIndex; i < rowCount - 1; i++) {
            outputNames[i] = outputNames[i + 1];
            equations[i] = equations[i + 1];
            initialEquations[i] = initialEquations[i + 1];
            sliderVarNames[i] = sliderVarNames[i + 1];
            sliderValues[i] = sliderValues[i + 1];
            hints[i] = hints[i + 1];
        }
        
        rowCount--;
        
        setStatus("Row " + (rowIndex + 1) + " deleted. Total rows: " + rowCount);
        markChanged();
        populateGrid();
    }
    
    /**
     * Move a row (swap with adjacent row)
     */
    private void moveRow(int fromIndex, int toIndex) {
        if (fromIndex == toIndex || toIndex < 0 || toIndex >= rowCount) return;
        
        // Swap all data
        String tempName = outputNames[fromIndex];
        outputNames[fromIndex] = outputNames[toIndex];
        outputNames[toIndex] = tempName;
        
        String tempEq = equations[fromIndex];
        equations[fromIndex] = equations[toIndex];
        equations[toIndex] = tempEq;
        
        String tempInit = initialEquations[fromIndex];
        initialEquations[fromIndex] = initialEquations[toIndex];
        initialEquations[toIndex] = tempInit;
        
        String tempVar = sliderVarNames[fromIndex];
        sliderVarNames[fromIndex] = sliderVarNames[toIndex];
        sliderVarNames[toIndex] = tempVar;
        
        double tempVal = sliderValues[fromIndex];
        sliderValues[fromIndex] = sliderValues[toIndex];
        sliderValues[toIndex] = tempVal;
        
        String tempHint = hints[fromIndex];
        hints[fromIndex] = hints[toIndex];
        hints[toIndex] = tempHint;
        
        String direction = (fromIndex < toIndex) ? "down" : "up";
        setStatus("Row " + (fromIndex + 1) + " moved " + direction);
        markChanged();
        populateGrid();
    }
    
    /**
     * Apply changes to the element
     */
    private void applyChanges() {
        // Apply table name
        tableElement.setTableName(tableNameBox.getText());
        
        // Apply row count
        tableElement.setRowCount(rowCount);
        
        // Apply row data
        for (int row = 0; row < rowCount; row++) {
            tableElement.setOutputName(row, outputNames[row]);
            tableElement.setEquation(row, equations[row]);
            tableElement.setInitialEquation(row, initialEquations[row]);
            tableElement.setSliderVarName(row, sliderVarNames[row]);
            tableElement.setSliderValue(row, sliderValues[row]);
            // Save hint to central HintRegistry
            if (hints[row] != null && !hints[row].trim().isEmpty()) {
                HintRegistry.setHint(outputNames[row], hints[row]);
            }
        }
        
        // Trigger reparse and node reallocation
        tableElement.parseAllEquationsPublic();
        tableElement.allocNodes();
        tableElement.setPoints();
        
        hasChanges = false;
        applyButton.setEnabled(false);
        setStatus("Changes applied");
        
        if (sim != null) {
            sim.needAnalyze();
            sim.repaint();
        }
    }
    
    /**
     * Mark that changes have been made
     */
    private void markChanged() {
        hasChanges = true;
        applyButton.setEnabled(true);
    }
    
    /**
     * Set status message
     */
    private void setStatus(String message) {
        statusLabel.setText(message);
    }
    
    /**
     * Close the dialog
     */
    public void closeDialog() {
        hide();
        CirSim.dialogShowing = null;
    }
}
