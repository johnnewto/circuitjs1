/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.elements.economics;

import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
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
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.user.client.ui.ListBox;
import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.registry.HintRegistry;
import com.lushprojects.circuitjs1.client.util.*;
import com.lushprojects.circuitjs1.client.elements.EquationTableMarkdownDebugDialog;
import com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm.RowOutputMode;
import com.lushprojects.circuitjs1.client.elements.electronics.wiring.LabeledNodeElm;
import com.lushprojects.circuitjs1.client.ui.Dialog;
import com.lushprojects.circuitjs1.client.ui.EditDialog;
import com.lushprojects.circuitjs1.client.ui.ReferenceDocs;
import com.lushprojects.circuitjs1.client.util.Locale;

/**
 * EquationTableEditDialog - Grid-based editor for EquationTableElm
 * 
 * Displays a grid with columns: Buttons | Output Name | Equation
 * Each row represents one equation with move up/down, add, delete buttons.
 * 
 * Pattern follows TableEditDialog for row manipulation.
 */
/**
 * EquationTableEditDialog — Modal editor for configuring all rows of an {@link EquationTableElm}.
 *
 * <h3>Layout</h3>
 * The dialog is organized as a vertically stacked main panel:
 * <ol>
 *   <li><b>Title bar</b> — table name textbox + live row count badge.</li>
 *   <li><b>Scrollable grid</b> — one row per equation, columns:
 *       Buttons | Node(s) | Equation | Initial (t=0) | Mode | Shunt R |
 *       Integ. | Hint</li>
 *   <li><b>Button bar</b> — Apply, OK, Properties, Debug, Reference, Toggle Modes, Close.</li>
 *   <li><b>Status label</b> — feedback after row operations and Apply.</li>
 * </ol>
 *
 * <h3>Apply Flow</h3>
 * {@link #applyChanges()} copies the local parallel arrays back to {@link EquationTableElm},
 * calls {@code parseAllEquationsPublic()}, {@code allocNodes()}, and {@code setPoints()},
 * then triggers {@code sim.needAnalyze()} so the circuit re-analyses with updated equations.
 *
 * <h3>Comment Rows</h3>
 * Rows whose Node(s) field starts with {@code #} are treated as non-simulating comment rows.
 * Their equation field becomes the comment text, the Mode is locked to PARAM, and styling
 * is adjusted to indicate the special row type.
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
    private static final int COL_MODE = 4;
    private static final int COL_SHUNT_RESISTANCE = 5;
    private static final int COL_INTEGRATION = 6;
    private static final int COL_HINT = 7;
    private static final int NUM_COLS = 8;
    
    // Grid row indices
    private static final int HEADER_ROW = 0;
    private static final int DATA_START_ROW = 1;
    
    /** Maximum number of equation rows supported */
    private static final int MAX_ROWS = EquationTableElm.MAX_ROWS;
    
    // Core references
    private final EquationTableElm tableElement;
    private final CirSim sim;
    
    // UI Components
    private ScrollPanel scrollPanel;
    private Grid editGrid;
    private Button applyButton;
    private Label statusLabel;
    private TextBox tableNameBox;
    private TextBox nominalColorBox;
    private TextBox realColorBox;
    
    // Data storage (copied from element for editing)
    private int rowCount;
    private String[] outputNames;
    private String[] equations;
    private String[] initialEquations;
    private RowOutputMode[] outputModes;
    private double[] shuntResistances;
    private boolean[] useBackwardEuler;
    private String[] hints;
    private String nominalVariableColorHex;
    private String realVariableColorHex;
    
    // Track changes
    private boolean hasChanges = false;
    
    // Debug dialog reference
    private EquationTableMarkdownDebugDialog debugDialog = null;
    
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
        outputModes = new RowOutputMode[MAX_ROWS];
        shuntResistances = new double[MAX_ROWS];
        useBackwardEuler = new boolean[MAX_ROWS];
        hints = new String[MAX_ROWS];
        nominalVariableColorHex = tableElement.getNominalVariableColorHex();
        realVariableColorHex = tableElement.getRealVariableColorHex();
        
        for (int i = 0; i < MAX_ROWS; i++) {
            outputNames[i] = tableElement.getUIDisplayOutputName(i);
            equations[i] = tableElement.getEquation(i);
            initialEquations[i] = tableElement.getInitialEquation(i);
            outputModes[i] = tableElement.getOutputMode(i);
            shuntResistances[i] = tableElement.getFlowShuntResistance(i);
            useBackwardEuler[i] = tableElement.getUseBackwardEuler(i);
            // Get hint from central HintRegistry using source name only
            String sourceName = EquationTableElm.parseCombinedName(outputNames[i])[0];
            String hint = HintRegistry.getHint(sourceName);
            hints[i] = (hint != null) ? hint : "";

            if (tableElement.isCommentRow(i)) {
                outputNames[i] = "#";
                equations[i] = tableElement.getCommentText(i);
                initialEquations[i] = "";
                outputModes[i] = RowOutputMode.PARAM_MODE;
            }
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
        titlePanel.setWidth("100%");
        titlePanel.setSpacing(4);
        titlePanel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
        titlePanel.getElement().getStyle().setProperty("padding", "4px 8px");
        titlePanel.getElement().getStyle().setProperty("border", "1px solid #d0d7de");
        titlePanel.getElement().getStyle().setProperty("borderRadius", "8px");
        titlePanel.getElement().getStyle().setProperty("backgroundColor", "#f8fafc");

        Label titleLabel = new Label("Table Name:");
        titleLabel.getElement().getStyle().setProperty("fontWeight", "600");
        titleLabel.getElement().getStyle().setProperty("color", "#4b5563");
        titleLabel.setWidth("88px");
        titlePanel.add(titleLabel);
        
        tableNameBox = new TextBox();
        tableNameBox.setText(tableElement.getTableName());
        tableNameBox.setWidth("220px");
        tableNameBox.setTitle("Equation table name");
        tableNameBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                markChanged();
            }
        });
        titlePanel.add(tableNameBox);
        
        // Row count label
        Label rowCountLabel = new Label("Rows: " + rowCount);
        rowCountLabel.getElement().getStyle().setProperty("marginLeft", "6px");
        rowCountLabel.getElement().getStyle().setProperty("padding", "2px 6px");
        rowCountLabel.getElement().getStyle().setProperty("backgroundColor", "#eef2f7");
        rowCountLabel.getElement().getStyle().setProperty("borderRadius", "999px");
        rowCountLabel.getElement().getStyle().setProperty("color", "#374151");
        titlePanel.add(rowCountLabel);

        Label nominalColorLabel = new Label("Nominal:");
        nominalColorLabel.getElement().getStyle().setProperty("marginLeft", "10px");
        nominalColorLabel.setTitle("Uppercase nominal / money variable color (#RRGGBB)");
        titlePanel.add(nominalColorLabel);

        nominalColorBox = new TextBox();
        nominalColorBox.setWidth("84px");
        nominalColorBox.setText(nominalVariableColorHex);
        nominalColorBox.setTitle("Uppercase nominal / money variable color (#RRGGBB)");
        nominalColorBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                markChanged();
            }
        });
        titlePanel.add(nominalColorBox);

        Label realColorLabel = new Label("Real:");
        realColorLabel.getElement().getStyle().setProperty("marginLeft", "6px");
        realColorLabel.setTitle("Lowercase real variable color (#RRGGBB)");
        titlePanel.add(realColorLabel);

        realColorBox = new TextBox();
        realColorBox.setWidth("84px");
        realColorBox.setText(realVariableColorHex);
        realColorBox.setTitle("Lowercase real variable color (#RRGGBB)");
        realColorBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                markChanged();
            }
        });
        titlePanel.add(realColorBox);

        mainPanel.add(titlePanel);
        
        // Scrollable table area
        scrollPanel = new ScrollPanel();
        scrollPanel.setWidth("1200px");
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
                sim.getEditDialogActions().doEdit(tableElement);
            }
        });
        buttonPanel.add(propertiesButton);
        
        Button debugButton = new Button("Debug");
        debugButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                showDebugDialog();
            }
        });
        buttonPanel.add(debugButton);

        Button referenceButton = new Button(Locale.LS("Reference"));
        referenceButton.setTitle("Open EquationTable reference documentation");
        referenceButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                ReferenceDocs.openMarkdownReference(
                    "EquationTable Reference",
                    "docs/reference/EquationTableReference.md");
            }
        });
        buttonPanel.add(referenceButton);

        Button toggleModesButton = new Button("Toggle Flow↔Voltage");
        toggleModesButton.setTitle("Toggle row modes: Flow ↔ Voltage (Param/comment rows unchanged)");
        toggleModesButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                toggleFlowVoltageModes();
            }
        });
        buttonPanel.add(toggleModesButton);

        Button classifyModesButton = new Button("Classify Modes");
        classifyModesButton.setTitle("Set cyclic rows to Voltage and non-cyclic rows to Param");
        classifyModesButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                classifyRowsByCyclicMode();
            }
        });
        buttonPanel.add(classifyModesButton);

        Button convertParamsButton = new Button("Params→Voltage");
        convertParamsButton.setTitle("Convert Param rows to Voltage (comment rows unchanged)");
        convertParamsButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                convertParamRowsToVoltage();
            }
        });
        buttonPanel.add(convertParamsButton);

        Button toggleDelaySyntaxButton = new Button("Toggle last↔delay");
        toggleDelaySyntaxButton.setTitle("Toggle syntax: last(Name)/Name[-1] ↔ delay(Name)");
        toggleDelaySyntaxButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                toggleLagDelaySyntax();
            }
        });
        buttonPanel.add(toggleDelaySyntaxButton);
        
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
        
        Label headerOutput = new Label("Node(s)");
        headerOutput.getElement().getStyle().setProperty("fontWeight", "bold");
        headerOutput.setTitle("Node name (ground-referenced by default). Use From\u2192To for two explicit nodes. Separators: , -> -||-");
        editGrid.setWidget(HEADER_ROW, COL_OUTPUT_NAME, headerOutput);
        
        Label headerEquation = new Label("Equation");
        headerEquation.getElement().getStyle().setProperty("fontWeight", "bold");
        editGrid.setWidget(HEADER_ROW, COL_EQUATION, headerEquation);
        
        Label headerInitial = new Label("Initial (t=0)");
        headerInitial.getElement().getStyle().setProperty("fontWeight", "bold");
        editGrid.setWidget(HEADER_ROW, COL_INITIAL_VALUE, headerInitial);
        
        Label headerMode = new Label("Mode");
        headerMode.getElement().getStyle().setProperty("fontWeight", "bold");
        headerMode.setTitle("Output mode");
        editGrid.setWidget(HEADER_ROW, COL_MODE, headerMode);
        
        Label headerCap = new Label("Shunt R");
        headerCap.getElement().getStyle().setProperty("fontWeight", "bold");
        headerCap.setTitle("FLOW: Shunt resistance (real load to ground).");
        editGrid.setWidget(HEADER_ROW, COL_SHUNT_RESISTANCE, headerCap);
        
        Label headerInteg = new Label("Integ.");
        headerInteg.getElement().getStyle().setProperty("fontWeight", "bold");
        headerInteg.setTitle("Integration method (Trapezoidal or Backward Euler)");
        editGrid.setWidget(HEADER_ROW, COL_INTEGRATION, headerInteg);
        
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
        // Output name textbox (combined format: "S1->S2" for Flow, "rate" for Voltage)
        final TextBox outputNameBox = new TextBox();
        outputNameBox.setText(outputNames[row]);
        outputNameBox.setWidth("110px");
        outputNameBox.setTitle("Node name (ground-referenced by default). Use From\u2192To for two explicit nodes. Separators: , -> -||-");
        outputNameBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                outputNames[row] = outputNameBox.getText();
                markChanged();
            }
        });
        addSelectAllOnFocus(outputNameBox);
        editGrid.setWidget(gridRow, COL_OUTPUT_NAME, outputNameBox);
        
        // Equation textbox with autocomplete
        final VerticalPanel eqPanel = createEquationTextBox(row, false);
        editGrid.setWidget(gridRow, COL_EQUATION, eqPanel);
        final TextBox equationBox = (eqPanel.getWidgetCount() > 0 && eqPanel.getWidget(0) instanceof TextBox)
            ? (TextBox) eqPanel.getWidget(0) : null;
        
        // Initial value textbox with autocomplete
        final VerticalPanel initPanel = createEquationTextBox(row, true);
        editGrid.setWidget(gridRow, COL_INITIAL_VALUE, initPanel);
        final TextBox initialBox = (initPanel.getWidgetCount() > 0 && initPanel.getWidget(0) instanceof TextBox)
            ? (TextBox) initPanel.getWidget(0) : null;
        
        // Output mode dropdown
        final ListBox modeBox = new ListBox();
        modeBox.addItem("Voltage", "VOLTAGE_MODE");
        modeBox.addItem("Flow\u2192", "FLOW_MODE");
        modeBox.addItem("Param", "PARAM_MODE");
        // Set selected based on current mode
        RowOutputMode currentMode = outputModes[row];
        if (currentMode == RowOutputMode.FLOW_MODE) modeBox.setSelectedIndex(1);
        else if (currentMode == RowOutputMode.PARAM_MODE) modeBox.setSelectedIndex(2);
        else modeBox.setSelectedIndex(0);
        modeBox.addChangeHandler(new ChangeHandler() {
            public void onChange(ChangeEvent event) {
                String val = modeBox.getSelectedValue();
                RowOutputMode previousMode = outputModes[row];
                if ("FLOW_MODE".equals(val)) outputModes[row] = RowOutputMode.FLOW_MODE;
                else if ("PARAM_MODE".equals(val)) outputModes[row] = RowOutputMode.PARAM_MODE;
                else outputModes[row] = RowOutputMode.VOLTAGE_MODE;
                if (outputModes[row] == RowOutputMode.FLOW_MODE && previousMode != RowOutputMode.FLOW_MODE) {
                    shuntResistances[row] = EquationTableElm.getDefaultFlowShuntResistance();
                }
                markChanged();
                // Enable/disable shunt field based on mode
                updateModeFields(gridRow, outputModes[row]);
            }
        });
        editGrid.setWidget(gridRow, COL_MODE, modeBox);

        Widget eqWidget = eqPanel.getWidget(0);
        if (eqWidget instanceof TextBox) {
            ((TextBox) eqWidget).addKeyUpHandler(new KeyUpHandler() {
                public void onKeyUp(KeyUpEvent event) {
                    updateModeLockUi(row, gridRow, modeBox, outputNameBox, equationBox, initialBox);
                }
            });
            ((TextBox) eqWidget).addBlurHandler(new BlurHandler() {
                public void onBlur(BlurEvent event) {
                    updateModeLockUi(row, gridRow, modeBox, outputNameBox, equationBox, initialBox);
                }
            });
        }
        Widget initWidget = initPanel.getWidget(0);
        if (initWidget instanceof TextBox) {
            ((TextBox) initWidget).addKeyUpHandler(new KeyUpHandler() {
                public void onKeyUp(KeyUpEvent event) {
                    updateModeLockUi(row, gridRow, modeBox, outputNameBox, equationBox, initialBox);
                }
            });
            ((TextBox) initWidget).addBlurHandler(new BlurHandler() {
                public void onBlur(BlurEvent event) {
                    updateModeLockUi(row, gridRow, modeBox, outputNameBox, equationBox, initialBox);
                }
            });
        }

        outputNameBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                updateModeLockUi(row, gridRow, modeBox, outputNameBox, equationBox, initialBox);
            }
        });
        outputNameBox.addBlurHandler(new BlurHandler() {
            public void onBlur(BlurEvent event) {
                updateModeLockUi(row, gridRow, modeBox, outputNameBox, equationBox, initialBox);
            }
        });

        updateModeLockUi(row, gridRow, modeBox, outputNameBox, equationBox, initialBox);
        
        // Mode parameter value (FLOW: Shunt R)
        final TextBox capBox = new TextBox();
        capBox.setText(CircuitElm.getShortUnitText(shuntResistances[row], ""));
        capBox.setWidth("50px");
        capBox.setTitle(getModeParamTooltip(outputModes[row]));
        capBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                try {
                    shuntResistances[row] = EditDialog.parseUnits(capBox.getText());
                    if (shuntResistances[row] <= 0) {
                        shuntResistances[row] = (outputModes[row] == RowOutputMode.FLOW_MODE)
                            ? EquationTableElm.getDefaultFlowShuntResistance()
                            : 1.0;
                    }
                    capBox.getElement().getStyle().clearBackgroundColor();
                    markChanged();
                } catch (Exception e) {
                    capBox.getElement().getStyle().setProperty("backgroundColor", "#ffcccc");
                }
            }
        });
        capBox.addBlurHandler(new BlurHandler() {
            public void onBlur(BlurEvent event) {
                capBox.setText(CircuitElm.getShortUnitText(shuntResistances[row], ""));
            }
        });
        addSelectAllOnFocus(capBox);
        // Enable for FLOW only
        capBox.setEnabled(outputModes[row] == RowOutputMode.FLOW_MODE);
        editGrid.setWidget(gridRow, COL_SHUNT_RESISTANCE, capBox);
        
        // Integration method dropdown (legacy; disabled)
        final ListBox integBox = new ListBox();
        integBox.addItem("Trap", "trap");
        integBox.addItem("Euler", "euler");
        integBox.setSelectedIndex(useBackwardEuler[row] ? 1 : 0);
        integBox.setTitle("Integration: Trapezoidal (more accurate) or Backward Euler (more stable)");
        integBox.addChangeHandler(new ChangeHandler() {
            public void onChange(ChangeEvent event) {
                useBackwardEuler[row] = "euler".equals(integBox.getSelectedValue());
                markChanged();
            }
        });
        integBox.setEnabled(false);
        editGrid.setWidget(gridRow, COL_INTEGRATION, integBox);
        
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
        textBox.setWidth("386px");
        
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
    
    //=========================================================================
    // AUTOCOMPLETE HELPERS
    //=========================================================================

    /**
     * Build the list of autocomplete candidates for an equation textbox.
     *
     * The list includes (in order):
     * <ol>
     *   <li>All stock names from {@link StockFlowRegistry}.</li>
     *   <li>All labeled node names from {@link LabeledNodeElm}.</li>
     *   <li>Output names defined in this table's current row data.</li>
     *   <li>FLOW computed-value keys ({@code <name>.flow}) for FLOW rows.</li>
     *   <li>Built-in math functions: sin, cos, tan, exp, log, sqrt, abs, etc.</li>
     *   <li>Constants: {@code pi}, {@code t}.</li>
     * </ol>
     *
     * @param row Zero-based row index.
     * @return Mutable list of candidate completion strings.
     */
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

            // Add FLOW computed-value keys (<output>.flow) so equations can
            // reference FLOW magnitudes without clobbering stock/node values.
            String[] parts = EquationTableElm.parseCombinedName(outputNames[i]);
            String flowKey = EquationTableElm.getFlowComputedKeyForName(parts[0]);
            if (flowKey != null && !completions.contains(flowKey)) {
                completions.add(flowKey);
            }
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
    * Update shunt and integration field enabled states based on mode
     */
    private void updateModeFields(int gridRow, RowOutputMode mode) {
        Widget capWidget = editGrid.getWidget(gridRow, COL_SHUNT_RESISTANCE);
        Widget integWidget = editGrid.getWidget(gridRow, COL_INTEGRATION);
        
        if (capWidget instanceof TextBox) {
            ((TextBox) capWidget).setEnabled(mode == RowOutputMode.FLOW_MODE);
            ((TextBox) capWidget).setTitle(getModeParamTooltip(mode));
        }
        if (integWidget instanceof ListBox) {
            ((ListBox) integWidget).setEnabled(false);
        }
    }

    /**
    * Return a context-sensitive tooltip for the Shunt R field based on the current mode.
     *
     * <ul>
     *   <li>FLOW_MODE  — field controls shunt resistance to ground.</li>
     *   <li>PARAM/VOLTAGE — field is not applicable and the tooltip says so.</li>
     * </ul>
     *
     * @param mode Current row output mode.
    * @return Tooltip string for the shunt-resistance field.
     */
    private String getModeParamTooltip(RowOutputMode mode) {
        if (mode == RowOutputMode.FLOW_MODE) {
            return "Shunt R for FLOW. Lower values create a real electrical load to ground.";
        }
        if (mode == RowOutputMode.PARAM_MODE) {
            return "Not used in PARAM mode.";
        }
        return "Not used in VOLTAGE mode.";
    }

    //=========================================================================
    // COMMENT ROW DETECTION
    //=========================================================================

    /**
     * Classifies why the Mode dropdown for a row should be locked (if at all).
     * <ul>
     *   <li>{@code NONE}    — free mode selection; user can change it via the dropdown.</li>
     *   <li>{@code COMMENT} — row name starts with {@code #}; mode is locked to PARAM.</li>
     * </ul>
     */
    private enum ModeLockType {
        NONE,
        COMMENT
    }

    /**
     * Determine whether and why the Mode dropdown for {@code row} should be locked.
     *
     * @param row Zero-based row index into the local parallel arrays.
     * @return Lock type describing whether/why the dropdown is locked.
     */
    private ModeLockType getModeLockType(int row) {
        if (isCommentRowByInput(row)) {
            return ModeLockType.COMMENT;
        }
        return ModeLockType.NONE;
    }

    /**
     * Return {@code true} if the current Node(s) input for {@code row} identifies it
     * as a comment row (source name starts with {@code #}).
     *
     * Uses the local {@code outputNames[]} copy rather than the live element state,
     * so it reflects in-flight editor changes before Apply is clicked.
     *
     * @param row Zero-based row index.
     * @return {@code true} if the row should be treated as a non-simulating comment.
     */
    private boolean isCommentRowByInput(int row) {
        if (row < 0 || row >= rowCount) {
            return false;
        }
        String displayName = outputNames[row];
        if (displayName == null) {
            return false;
        }
        String[] parts = EquationTableElm.parseCombinedName(displayName);
        String sourceName = (parts != null && parts.length > 0) ? parts[0] : displayName;
        return EquationTableElm.isCommentRowName(sourceName);
    }

    /** Keep mode UI consistent with comment-row lock state. */
    private void updateModeLockUi(int row, int gridRow, ListBox modeBox,
                                   TextBox outputNameBox, TextBox equationBox, TextBox initialBox) {
        ModeLockType lockType = getModeLockType(row);
        if (lockType == ModeLockType.COMMENT) {
            outputModes[row] = RowOutputMode.PARAM_MODE;
            modeBox.setItemText(0, "# Comment");
            modeBox.setSelectedIndex(0);
            modeBox.setEnabled(false);
            modeBox.setTitle("Comment row: non-simulating metadata row (mode locked)");
        } else {
            modeBox.setItemText(0, "Voltage");
            modeBox.setEnabled(true);
            modeBox.setTitle("Output mode");
        }

        updateCommentRowFieldWidths(lockType == ModeLockType.COMMENT, outputNameBox, equationBox, initialBox);
        updateNodeColumnEmphasis(row, lockType == ModeLockType.COMMENT, outputNameBox);
        updateModeFields(gridRow, outputModes[row]);
    }

    /**
     * Visually de-emphasize the Node(s) column when equation text already contains
     * an explicit assignment LHS (e.g. "X - X[-1] = ...").
     */
    private void updateNodeColumnEmphasis(int row, boolean isCommentRow, TextBox outputNameBox) {
        if (outputNameBox == null) {
            return;
        }

        if (isCommentRow) {
            outputNameBox.getElement().getStyle().clearColor();
            outputNameBox.getElement().getStyle().clearBackgroundColor();
            outputNameBox.getElement().getStyle().clearProperty("opacity");
            return;
        }

        boolean equationHasExplicitLhs = containsStandaloneAssignment(equations[row]);
        if (equationHasExplicitLhs) {
            outputNameBox.getElement().getStyle().setProperty("opacity", "0.72");
            outputNameBox.getElement().getStyle().setProperty("color", "#6b7280");
            outputNameBox.getElement().getStyle().setProperty("backgroundColor", "#f8fafc");
            outputNameBox.setTitle("Output variable is still used for semantics; equation already includes explicit LHS");
        } else {
            outputNameBox.getElement().getStyle().clearProperty("opacity");
            outputNameBox.getElement().getStyle().clearColor();
            outputNameBox.getElement().getStyle().clearBackgroundColor();
            outputNameBox.setTitle("Node name (ground-referenced by default). Use From→To for two explicit nodes. Separators: , -> -||-");
        }
    }

    /** Return true if expression contains assignment '=' not part of ==, <=, >=, or !=. */
    private boolean containsStandaloneAssignment(String expression) {
        if (expression == null || expression.isEmpty()) {
            return false;
        }
        for (int i = 0; i < expression.length(); i++) {
            if (expression.charAt(i) != '=') {
                continue;
            }
            char prev = (i > 0) ? expression.charAt(i - 1) : '\0';
            char next = (i + 1 < expression.length()) ? expression.charAt(i + 1) : '\0';
            if (prev == '=' || prev == '<' || prev == '>' || prev == '!' || next == '=') {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Adjust the widths and font weights of per-row input fields based on whether the
     * row is a comment row.
     *
     * Comment rows compress the Node(s) field (just shows "#") and bold text to make
     * the comment content stand out visually from equation rows.
     *
     * @param isCommentRow {@code true} to apply comment-row styling.
     * @param outputNameBox The Node(s) textbox for the row.
     * @param equationBox   The Equation textbox (reused as comment text field).
     * @param initialBox    The Initial (t=0) textbox (hidden/narrowed for comments).
     */
    private void updateCommentRowFieldWidths(boolean isCommentRow, TextBox outputNameBox,
                                             TextBox equationBox, TextBox initialBox) {
        if (outputNameBox != null) {
            outputNameBox.setWidth(isCommentRow ? "24px" : "110px");
            outputNameBox.getElement().getStyle().setProperty("fontWeight", isCommentRow ? "bold" : "normal");
        }
        if (initialBox != null) {
            initialBox.setWidth("90px");
            initialBox.getElement().getStyle().setProperty("fontWeight", isCommentRow ? "bold" : "normal");
        }
        if (equationBox != null) {
            equationBox.setWidth("386px");
            equationBox.getElement().getStyle().setProperty("fontWeight", isCommentRow ? "bold" : "normal");
        }
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
            outputModes[i + 1] = outputModes[i];
            shuntResistances[i + 1] = shuntResistances[i];
            useBackwardEuler[i + 1] = useBackwardEuler[i];
            hints[i + 1] = hints[i];
        }
        
        // Initialize new row
        outputNames[insertAt] = "Y" + (insertAt + 1);
        equations[insertAt] = "0";
        initialEquations[insertAt] = "";
        outputModes[insertAt] = RowOutputMode.VOLTAGE_MODE;
        shuntResistances[insertAt] = EquationTableElm.getDefaultFlowShuntResistance();
        useBackwardEuler[insertAt] = false;
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
            outputModes[i] = outputModes[i + 1];
            shuntResistances[i] = shuntResistances[i + 1];
            useBackwardEuler[i] = useBackwardEuler[i + 1];
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
        
        RowOutputMode tempMode = outputModes[fromIndex];
        outputModes[fromIndex] = outputModes[toIndex];
        outputModes[toIndex] = tempMode;
        
        double tempShuntR = shuntResistances[fromIndex];
        shuntResistances[fromIndex] = shuntResistances[toIndex];
        shuntResistances[toIndex] = tempShuntR;
        
        boolean tempEuler = useBackwardEuler[fromIndex];
        useBackwardEuler[fromIndex] = useBackwardEuler[toIndex];
        useBackwardEuler[toIndex] = tempEuler;
        
        String tempHint = hints[fromIndex];
        hints[fromIndex] = hints[toIndex];
        hints[toIndex] = tempHint;
        
        String direction = (fromIndex < toIndex) ? "down" : "up";
        setStatus("Row " + (fromIndex + 1) + " moved " + direction);
        markChanged();
        populateGrid();
    }

    /**
     * Toggle row output modes globally: FLOW_MODE ↔ VOLTAGE_MODE.
     * PARAM_MODE and comment rows are left unchanged.
     */
    private void toggleFlowVoltageModes() {
        int switchedToFlow = 0;
        int switchedToVoltage = 0;

        for (int row = 0; row < rowCount; row++) {
            if (isCommentRowByInput(row)) {
                continue;
            }

            if (outputModes[row] == RowOutputMode.FLOW_MODE) {
                outputModes[row] = RowOutputMode.VOLTAGE_MODE;
                switchedToVoltage++;
            } else if (outputModes[row] == RowOutputMode.VOLTAGE_MODE) {
                outputModes[row] = RowOutputMode.FLOW_MODE;
                if (shuntResistances[row] <= 0) {
                    shuntResistances[row] = EquationTableElm.getDefaultFlowShuntResistance();
                }
                switchedToFlow++;
            }
        }

        if (switchedToFlow == 0 && switchedToVoltage == 0) {
            setStatus("No Flow/Voltage rows to toggle");
            return;
        }

        setStatus("Toggled modes: " + switchedToFlow + " to Flow, " + switchedToVoltage + " to Voltage");
        markChanged();
        populateGrid();
    }

    /**
     * Classify row modes in one pass:
     * cyclic rows -> VOLTAGE_MODE, non-cyclic rows -> PARAM_MODE.
     * Comment rows are skipped.
     */
    private void classifyRowsByCyclicMode() {
        int convertedToVoltage = 0;
        int convertedToParam = 0;

        for (int row = 0; row < rowCount; row++) {
            if (isCommentRowByInput(row)) {
                continue;
            }

            boolean isCyclic = "cyclic".equals(tableElement.getRowClassification(row));
            RowOutputMode targetMode = isCyclic ? RowOutputMode.VOLTAGE_MODE : RowOutputMode.PARAM_MODE;

            if (outputModes[row] != targetMode) {
                outputModes[row] = targetMode;
                if (isCyclic) {
                    convertedToVoltage++;
                } else {
                    convertedToParam++;
                }
            }
        }

        if (convertedToVoltage == 0 && convertedToParam == 0) {
            setStatus("Rows already match cyclic mode classification");
            return;
        }

        setStatus("Classified modes: " + convertedToVoltage + " cyclic→Voltage, " + convertedToParam + " non-cyclic→Param");
        markChanged();
        populateGrid();
    }

    /**
     * Convert all editable PARAM rows to VOLTAGE mode.
     * Comment rows are left unchanged because they are always locked to PARAM.
     */
    private void convertParamRowsToVoltage() {
        int convertedCount = EquationTableModeConversionHelper.convertParamRowsToVoltage(outputModes, outputNames, rowCount);

        if (convertedCount == 0) {
            setStatus("No Param rows to convert to Voltage");
            return;
        }

        setStatus("Converted " + convertedCount + " Param row(s) to Voltage");
        markChanged();
        populateGrid();
    }

    /**
     * Toggle lagged-reference syntax in row equations:
        * - delay(Name) -> Name[-1]
        * - Name[-1] -> last(Name)
        * - last(Name) -> delay(Name)
     */
    private void toggleLagDelaySyntax() {
        int changedCells = 0;

        for (int row = 0; row < rowCount; row++) {
            if (isCommentRowByInput(row)) {
                continue;
            }

            String newEq = toggleLagDelaySyntaxInText(equations[row]);
            if (!safeEquals(newEq, equations[row])) {
                equations[row] = newEq;
                changedCells++;
            }

            String newInitEq = toggleLagDelaySyntaxInText(initialEquations[row]);
            if (!safeEquals(newInitEq, initialEquations[row])) {
                initialEquations[row] = newInitEq;
                changedCells++;
            }
        }

        if (changedCells == 0) {
            setStatusWithEquivalence("No delay()/[-1]/last() patterns found");
            return;
        }

        setStatusWithEquivalence("Toggled lag/delay syntax in " + changedCells + " field(s)");
        markChanged();
        populateGrid();
    }

    private void setStatusWithEquivalence(String baseMessage) {
        setStatus(baseMessage + "  |  Equivalence check: " + buildEquivalenceSummary());
    }

    private String buildEquivalenceSummary() {
        int lastCount = 0;
        int indexCount = 0;
        int delayCount = 0;

        for (int row = 0; row < rowCount; row++) {
            if (isCommentRowByInput(row)) {
                continue;
            }

            lastCount += countSimpleFunctionCalls(equations[row], "last");
            lastCount += countSimpleFunctionCalls(initialEquations[row], "last");

            indexCount += countIndexMinusOneRefs(equations[row]);
            indexCount += countIndexMinusOneRefs(initialEquations[row]);

            delayCount += countSimpleFunctionCalls(equations[row], "delay");
            delayCount += countSimpleFunctionCalls(initialEquations[row], "delay");
        }

        return "last=" + lastCount + ", [-1]=" + indexCount + ", delay=" + delayCount;
    }

    private int countSimpleFunctionCalls(String text, String fnName) {
        if (text == null || text.length() == 0) {
            return 0;
        }

        int count = 0;
        int i = 0;
        int n = text.length();

        while (i < n) {
            if (matchesWordIgnoreCase(text, i, fnName)) {
                int p = i + fnName.length();
                p = skipWhitespace(text, p);
                if (p < n && text.charAt(p) == '(') {
                    int q = skipWhitespace(text, p + 1);
                    if (q < n && isIdentifierStartChar(text.charAt(q))) {
                        q++;
                        while (q < n && isIdentifierPartChar(text.charAt(q))) {
                            q++;
                        }
                        q = skipWhitespace(text, q);
                        if (q < n && text.charAt(q) == ')') {
                            count++;
                            i = q + 1;
                            continue;
                        }
                    }
                }
            }
            i++;
        }

        return count;
    }

    private int countIndexMinusOneRefs(String text) {
        if (text == null || text.length() == 0) {
            return 0;
        }

        int count = 0;
        int i = 0;
        int n = text.length();

        while (i < n) {
            if (isIdentifierStartChar(text.charAt(i))) {
                i++;
                while (i < n && isIdentifierPartChar(text.charAt(i))) {
                    i++;
                }

                int p = skipWhitespace(text, i);
                if (p < n && text.charAt(p) == '[') {
                    int q = skipWhitespace(text, p + 1);
                    if (q < n && text.charAt(q) == '-') {
                        q = skipWhitespace(text, q + 1);
                        if (q < n && text.charAt(q) == '1') {
                            q = skipWhitespace(text, q + 1);
                            if (q < n && text.charAt(q) == ']') {
                                count++;
                                i = q + 1;
                                continue;
                            }
                        }
                    }
                }
                continue;
            }
            i++;
        }

        return count;
    }

    private boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private String toggleLagDelaySyntaxInText(String text) {
        if (text == null || text.length() == 0) {
            return text;
        }

        // 3-way cycle for simple identifiers:
        // delay(Name) -> Name[-1] -> last(Name) -> delay(Name)

        // Step 1: delay(Name) -> Name[-1]
        String toIndex = replaceSimpleFunctionCallWithIndex(text, "delay");
        if (!safeEquals(toIndex, text)) {
            return toIndex;
        }

        // Step 2: Name[-1] -> last(Name)
        String toLast = replaceIndexMinusOneWithFunction(text, "last");
        if (!safeEquals(toLast, text)) {
            return toLast;
        }

        // Step 3: last(Name) -> delay(Name)
        return replaceSimpleFunctionCall(text, "last", "delay");
    }

    private String replaceSimpleFunctionCall(String text, String fromFn, String toFn) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = text.length();

        while (i < n) {
            if (matchesWordIgnoreCase(text, i, fromFn)) {
                int fnStart = i;
                int p = i + fromFn.length();
                p = skipWhitespace(text, p);
                if (p < n && text.charAt(p) == '(') {
                    int q = skipWhitespace(text, p + 1);
                    if (q < n && isIdentifierStartChar(text.charAt(q))) {
                        int idStart = q;
                        q++;
                        while (q < n && isIdentifierPartChar(text.charAt(q))) {
                            q++;
                        }
                        String ident = text.substring(idStart, q);
                        q = skipWhitespace(text, q);
                        if (q < n && text.charAt(q) == ')') {
                            out.append(toFn).append("(").append(ident).append(")");
                            i = q + 1;
                            continue;
                        }
                    }
                }
                // Not a simple function call; copy one char and continue.
                out.append(text.charAt(fnStart));
                i = fnStart + 1;
                continue;
            }

            out.append(text.charAt(i));
            i++;
        }

        return out.toString();
    }

    private String replaceSimpleFunctionCallWithIndex(String text, String fromFn) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = text.length();

        while (i < n) {
            if (matchesWordIgnoreCase(text, i, fromFn)) {
                int fnStart = i;
                int p = i + fromFn.length();
                p = skipWhitespace(text, p);
                if (p < n && text.charAt(p) == '(') {
                    int q = skipWhitespace(text, p + 1);
                    if (q < n && isIdentifierStartChar(text.charAt(q))) {
                        int idStart = q;
                        q++;
                        while (q < n && isIdentifierPartChar(text.charAt(q))) {
                            q++;
                        }
                        String ident = text.substring(idStart, q);
                        q = skipWhitespace(text, q);
                        if (q < n && text.charAt(q) == ')') {
                            out.append(ident).append("[-1]");
                            i = q + 1;
                            continue;
                        }
                    }
                }
                out.append(text.charAt(fnStart));
                i = fnStart + 1;
                continue;
            }

            out.append(text.charAt(i));
            i++;
        }

        return out.toString();
    }

    private String replaceIndexMinusOneWithFunction(String text, String toFn) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = text.length();

        while (i < n) {
            if (isIdentifierStartChar(text.charAt(i))) {
                int idStart = i;
                i++;
                while (i < n && isIdentifierPartChar(text.charAt(i))) {
                    i++;
                }
                String ident = text.substring(idStart, i);

                int p = skipWhitespace(text, i);
                if (p < n && text.charAt(p) == '[') {
                    int q = skipWhitespace(text, p + 1);
                    if (q < n && text.charAt(q) == '-') {
                        q = skipWhitespace(text, q + 1);
                        if (q < n && text.charAt(q) == '1') {
                            q = skipWhitespace(text, q + 1);
                            if (q < n && text.charAt(q) == ']') {
                                out.append(toFn).append("(").append(ident).append(")");
                                i = q + 1;
                                continue;
                            }
                        }
                    }
                }

                out.append(ident);
                continue;
            }

            out.append(text.charAt(i));
            i++;
        }

        return out.toString();
    }

    private int skipWhitespace(String text, int index) {
        int i = index;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private boolean isIdentifierStartChar(char ch) {
        return Character.isLetter(ch) || ch == '_' || ch == '\\';
    }

    private boolean isIdentifierPartChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '\\' || ch == '^' || ch == '{' || ch == '}' || ch == '.';
    }

    private boolean matchesWordIgnoreCase(String text, int start, String word) {
        int end = start + word.length();
        if (start < 0 || end > text.length()) {
            return false;
        }
        if (!text.regionMatches(true, start, word, 0, word.length())) {
            return false;
        }

        char prev = (start > 0) ? text.charAt(start - 1) : '\0';
        char next = (end < text.length()) ? text.charAt(end) : '\0';
        if (start > 0 && isIdentifierPartChar(prev)) {
            return false;
        }
        if (end < text.length() && isIdentifierPartChar(next)) {
            return false;
        }
        return true;
    }
    
    /**
     * Apply changes to the element
     */
    private void applyChanges() {
        String nominalColor = nominalColorBox.getText() == null ? "" : nominalColorBox.getText().trim();
        String realColor = realColorBox.getText() == null ? "" : realColorBox.getText().trim();
        if (!EquationTableVariableColoring.isValidColorHex(nominalColor)) {
            setStatus("Nominal color must use #RRGGBB");
            return;
        }
        if (!EquationTableVariableColoring.isValidColorHex(realColor)) {
            setStatus("Real color must use #RRGGBB");
            return;
        }

        tableElement.setNominalVariableColorHex(nominalColor);
        tableElement.setRealVariableColorHex(realColor);

        // Apply table name
        tableElement.setTableName(tableNameBox.getText());
        
        // Apply row count
        tableElement.setRowCount(rowCount);
        
        // Apply row data
        for (int row = 0; row < rowCount; row++) {
            String outputName = outputNames[row] == null ? "" : outputNames[row].trim();
            boolean isCommentRow = EquationTableElm.isCommentRowName(outputName);

            if (isCommentRow) {
                String commentText = equations[row] == null ? "" : equations[row].trim();
                outputName = commentText.isEmpty() ? "#" : ("# " + commentText);
            }

            tableElement.setOutputName(row, outputName);  // Parses "A->B" format

            if (isCommentRow) {
                tableElement.setEquation(row, "");
                tableElement.setInitialEquation(row, "");
                tableElement.setOutputMode(row, RowOutputMode.PARAM_MODE);
                tableElement.setFlowShuntResistance(row, EquationTableElm.getDefaultFlowShuntResistance());
                tableElement.setUseBackwardEuler(row, false);
                continue;
            }

            tableElement.setEquation(row, equations[row]);
            tableElement.setInitialEquation(row, initialEquations[row]);
            tableElement.setOutputMode(row, outputModes[row]);
            tableElement.setFlowShuntResistance(row, shuntResistances[row]);
            tableElement.setUseBackwardEuler(row, useBackwardEuler[row]);
            // Save hint to central HintRegistry using source name only
            String sourceName = EquationTableElm.parseCombinedName(outputName)[0];
            if (hints[row] != null && !hints[row].trim().isEmpty()) {
                HintRegistry.setHint(sourceName, hints[row]);
            }
        }
        
        // Trigger reparse and node reallocation
        tableElement.parseAllEquationsPublic();
        tableElement.allocNodesPublic();
        tableElement.setPoints();
        
        hasChanges = false;
        applyButton.setEnabled(false);
        setStatus("Changes applied");
        
        if (sim != null) {
            sim.needAnalyze();
            sim.repaint();
            sim.getUiPanelManager().refreshModelInfoEditorAfterCircuitMutation();
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
     * Show the debug dialog for inspecting equation table state
     */
    private void showDebugDialog() {
        if (debugDialog != null) {
            debugDialog.refresh();
            if (!debugDialog.isShowing()) {
                debugDialog.show();
            }
        } else {
            debugDialog = new EquationTableMarkdownDebugDialog(tableElement);
            debugDialog.show();
        }
    }
    
    /**
     * Close the dialog
     */
    public void closeDialog() {
        hide();
        CirSimDialogCoordinator.clearDialogShowingIf(this);
    }
}
