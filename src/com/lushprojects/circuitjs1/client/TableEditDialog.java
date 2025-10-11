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

import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.CheckBox;

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.BlurEvent;
import com.lushprojects.circuitjs1.client.util.Locale;
import java.util.ArrayList;
import java.util.List;

/**
 * TableEditDialog - Enhanced spreadsheet-like editor for TableElm
 * Provides a dedicated dialog with Grid layout for editing table cell labels and column headers
 */
public class TableEditDialog extends Dialog {
    
    private TableElm tableElement;
    private CirSim sim;
    
    // UI Components
    private VerticalPanel mainPanel;
    private ScrollPanel scrollPanel;
    private Grid editGrid;
    private HorizontalPanel buttonPanel;
    private Button okButton, cancelButton, applyButton;
    private Button addRowButton, removeRowButton, addColButton, removeColButton;
    private Label statusLabel;
    private CheckBox initialConditionsCheckBox;
    
    // Data storage
    private String[][] cellEquations;  // Store equation text for each cell (now the only mode)
    private String[] columnHeaders;
    private int rows, cols;
    private boolean hasInitialConditions;
    private double[] initialConditionsValues;
    
    // Cell editing management
    private List<List<TextBox>> cellTextBoxes;
    private List<List<Label>> statusLabels;        // Status/error labels for each cell
    private List<TextBox> headerTextBoxes;
    private List<TextBox> initialConditionsTextBoxes;  // Text boxes for initial conditions
    
    // Track changes
    private boolean hasChanges = false;
    
    public TableEditDialog(TableElm tableElm, CirSim cirSim) {
        super();
        this.tableElement = tableElm;
        this.sim = cirSim;
        
        // Copy current table data
        this.rows = tableElm.rows;
        this.cols = tableElm.cols;
        copyTableData();
        
        setText(Locale.LS("Edit Table Data"));
        setupUI();
        populateGrid();
        center();
    }
    
    private void copyTableData() {
        cellEquations = new String[rows][cols];
        columnHeaders = new String[cols];
        
        // Copy initial conditions data
        hasInitialConditions = tableElement.getHasInitialConditions();
        initialConditionsValues = new double[cols];
        for (int col = 0; col < cols; col++) {
            initialConditionsValues[col] = tableElement.getInitialConditionValue(col);
        }
        
        // Copy cell equations (now the only data type)
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                cellEquations[row][col] = tableElement.getCellEquation(row, col);
                if (cellEquations[row][col] == null) {
                    cellEquations[row][col] = "node" + (row * cols + col + 1);
                }
            }
        }
        
        // Copy column headers
        for (int col = 0; col < cols; col++) {
            columnHeaders[col] = tableElement.getColumnHeader(col);
            if (columnHeaders[col] == null) {
                columnHeaders[col] = "Col" + (col + 1);
            }
        }
    }
    
    private void setupUI() {
        mainPanel = new VerticalPanel();
        mainPanel.setWidth("100%");
        setWidget(mainPanel);
        
        // Status label
        statusLabel = new Label("Table Editor - Navigation: Tab/Enter/Ctrl+Arrows | Shortcuts: Ctrl+S (Apply)");
        statusLabel.addStyleName("topSpace");
        mainPanel.add(statusLabel);
        
        // Equation help text
        Label equationHelp = new Label("All cells are equations - Use node names directly (e.g. 'vcc', 'gnd') or variables a-i for labeled nodes | Examples: 'a+b', 'vcc*0.5', 'sin(t)', 'max(a,b,c)', 'vcc>2.5?5:0'");
        equationHelp.addStyleName("topSpace");
        equationHelp.getElement().getStyle().setProperty("fontSize", "11px");
        equationHelp.getElement().getStyle().setProperty("color", "#666");
        mainPanel.add(equationHelp);
        
        // Initial conditions checkbox
        initialConditionsCheckBox = new CheckBox("Show Initial Conditions Row");
        initialConditionsCheckBox.setValue(hasInitialConditions);
        initialConditionsCheckBox.addValueChangeHandler(event -> {
            hasInitialConditions = event.getValue();
            markChanged();
            populateGrid(); // Regenerate grid to show/hide initial conditions row
        });
        initialConditionsCheckBox.addStyleName("topSpace");
        mainPanel.add(initialConditionsCheckBox);
        
        // Table editing controls
        HorizontalPanel controlPanel = new HorizontalPanel();
        controlPanel.addStyleName("topSpace");
        controlPanel.setSpacing(5);
        
        addRowButton = new Button(Locale.LS("Add Row"));
        addRowButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                addRow();
            }
        });
        controlPanel.add(addRowButton);
        
        removeRowButton = new Button(Locale.LS("Remove Row"));
        removeRowButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                removeRow();
            }
        });
        controlPanel.add(removeRowButton);
        
        addColButton = new Button(Locale.LS("Add Column"));
        addColButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                addColumn();
            }
        });
        controlPanel.add(addColButton);
        
        removeColButton = new Button(Locale.LS("Remove Column"));
        removeColButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                removeColumn();
            }
        });
        controlPanel.add(removeColButton);
        
        mainPanel.add(controlPanel);
        
        // Scrollable table area
        scrollPanel = new ScrollPanel();
        scrollPanel.setSize("600px", "400px");
        scrollPanel.addStyleName("topSpace");
        mainPanel.add(scrollPanel);
        
        // Bottom buttons
        buttonPanel = new HorizontalPanel();
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
        
        okButton = new Button(Locale.LS("OK"));
        okButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                applyChanges();
                closeDialog();
            }
        });
        buttonPanel.add(okButton);
        
        buttonPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
        cancelButton = new Button(Locale.LS("Cancel"));
        cancelButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                if (hasChanges) {
                    if (!com.google.gwt.user.client.Window.confirm(
                            Locale.LS("You have unsaved changes. Are you sure you want to cancel?"))) {
                        return;
                    }
                }
                closeDialog();
            }
        });
        buttonPanel.add(cancelButton);
        
        mainPanel.add(buttonPanel);
    }
    
    private void createGrid() {
        // Calculate grid rows: header + (optional initial conditions * 2) + (equation rows * 2)
        int extraRows = hasInitialConditions ? 2 : 0; // Initial conditions input + status rows
        int totalGridRows = 1 + extraRows + (rows * 2); // 1 for headers + initial conditions + equation rows
        
        editGrid = new Grid(totalGridRows, cols + 1);
        editGrid.addStyleName("tableEditGrid");
        editGrid.setCellSpacing(1);
        editGrid.setCellPadding(2);
        
        // Initialize collections
        cellTextBoxes = new ArrayList<List<TextBox>>();
        statusLabels = new ArrayList<List<Label>>();
        headerTextBoxes = new ArrayList<TextBox>();
        initialConditionsTextBoxes = new ArrayList<TextBox>();
        
        // Add row headers (row numbers)
        editGrid.setText(0, 0, "");  // Top-left corner
        
        int gridRow = 1; // Start after header row
        
        // Add initial conditions row header if enabled
        if (hasInitialConditions) {
            Label initialLabel = new Label("Initial");
            initialLabel.addStyleName("tableRowHeader");
            initialLabel.getElement().getStyle().setProperty("color", "#007700");
            editGrid.setWidget(gridRow, 0, initialLabel);
            gridRow++; // Status row
            editGrid.setText(gridRow, 0, "");
            gridRow++; // Move to next row
        }
        
        // Add regular row headers (equation rows)
        for (int row = 0; row < rows; row++) {
            Label rowLabel = new Label("" + (row + 1));
            rowLabel.addStyleName("tableRowHeader");
            editGrid.setWidget(gridRow, 0, rowLabel);
            gridRow++; // Status row
            editGrid.setText(gridRow, 0, "");
            gridRow++; // Move to next row
        }
    }
    
    private void populateGrid() {
        createGrid();
        
        // Add column headers
        for (int col = 0; col < cols; col++) {
            TextBox headerBox = createHeaderTextBox(col);
            headerTextBoxes.add(headerBox);
            editGrid.setWidget(0, col + 1, headerBox);
        }
        
        int gridRow = 1; // Start after header row
        
        // Add initial conditions row if enabled
        if (hasInitialConditions) {
            for (int col = 0; col < cols; col++) {
                TextBox initialBox = createInitialConditionsTextBox(col);
                initialConditionsTextBoxes.add(initialBox);
                editGrid.setWidget(gridRow, col + 1, initialBox);
            }
            gridRow++; // Status row for initial conditions
            
            // Add status labels for initial conditions
            for (int col = 0; col < cols; col++) {
                Label statusLabel = new Label("Initial condition value");
                statusLabel.addStyleName("cellStatusLabel");
                statusLabel.getElement().getStyle().setProperty("fontSize", "10px");
                statusLabel.getElement().getStyle().setProperty("color", "#007700");
                editGrid.setWidget(gridRow, col + 1, statusLabel);
            }
            gridRow++; // Move to equation rows
        }
        
        // Add cell editors for equations
        for (int row = 0; row < rows; row++) {
            List<TextBox> rowTextBoxes = new ArrayList<TextBox>();
            List<Label> rowStatusLabels = new ArrayList<Label>();
            
            cellTextBoxes.add(rowTextBoxes);
            statusLabels.add(rowStatusLabels);
            
            for (int col = 0; col < cols; col++) {
                // Create input box
                TextBox cellBox = createCellTextBox(row, col);
                rowTextBoxes.add(cellBox);
                editGrid.setWidget(gridRow, col + 1, cellBox);
                
                // Create status label
                Label statusLabel = createStatusLabel(row, col);
                rowStatusLabels.add(statusLabel);
                editGrid.setWidget(gridRow + 1, col + 1, statusLabel);
            }
            
            gridRow += 2; // Move by 2 (input row + status row)
        }
        
        scrollPanel.setWidget(editGrid);
        updateButtonStates();
    }
    
    private TextBox createHeaderTextBox(final int col) {
        TextBox textBox = new TextBox();
        textBox.setText(columnHeaders[col]);
        textBox.addStyleName("tableHeaderInput");
        
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                int keyCode = event.getNativeKeyCode();
                
                // Handle Ctrl+S for apply
                if (event.isControlKeyDown() && keyCode == 83) { // Ctrl+S
                    applyChanges();
                    return;
                }
                
                columnHeaders[col] = textBox.getText();
                textBox.addStyleName("modified");
                markChanged();
                
                // Handle navigation
                if (keyCode == KeyCodes.KEY_ENTER) {
                    if (hasInitialConditions) {
                        navigateToInitialConditions(col);
                    } else {
                        navigateToCell(0, col);
                    }
                } else if (keyCode == KeyCodes.KEY_TAB && !event.isShiftKeyDown()) {
                    navigateToNextHeader(col);
                } else if (keyCode == KeyCodes.KEY_TAB && event.isShiftKeyDown()) {
                    navigateToPrevHeader(col);
                }
            }
        });
        
        setupTextBoxHandlers(textBox, -1, col);
        return textBox;
    }

    private TextBox createInitialConditionsTextBox(final int col) {
        TextBox textBox = new TextBox();
        textBox.setText(String.valueOf(initialConditionsValues[col]));
        textBox.addStyleName("tableCellInput");
        textBox.addStyleName("initialConditionsInput");
        textBox.setTitle("Enter initial condition value for this column");
        
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                int keyCode = event.getNativeKeyCode();
                
                // Handle Ctrl+S for apply
                if (event.isControlKeyDown() && keyCode == 83) { // Ctrl+S
                    applyChanges();
                    return;
                }
                
                // Try to parse the value
                try {
                    double value = Double.parseDouble(textBox.getText().trim());
                    initialConditionsValues[col] = value;
                    textBox.removeStyleName("error");
                } catch (NumberFormatException e) {
                    textBox.addStyleName("error");
                }
                
                textBox.addStyleName("modified");
                markChanged();
                
                // Handle navigation
                if (keyCode == KeyCodes.KEY_ENTER) {
                    navigateToCell(0, col);
                } else if (keyCode == KeyCodes.KEY_TAB && !event.isShiftKeyDown()) {
                    navigateToNextInitialConditions(col);
                } else if (keyCode == KeyCodes.KEY_TAB && event.isShiftKeyDown()) {
                    navigateToPrevInitialConditions(col);
                }
            }
        });
        
        setupTextBoxHandlers(textBox, -2, col); // Use -2 to indicate initial conditions
        return textBox;
    }
    
    private Label createStatusLabel(final int row, final int col) {
        Label label = new Label();
        label.addStyleName("cellStatusLabel");
        label.getElement().getStyle().setProperty("fontSize", "10px");
        updateStatusLabel(label, row, col);
        return label;
    }
    
    private void updateStatusLabel(Label label, int row, int col) {
        // All cells are now equation mode
        String equation = cellEquations[row][col];
        if (equation == null || equation.trim().isEmpty()) {
            label.setText("Enter equation or node name");
            label.getElement().getStyle().setProperty("color", "#999");
        } else {
            // Validate equation
            String error = validateEquation(equation);
            if (error != null) {
                label.setText("Error: " + error);
                label.getElement().getStyle().setProperty("color", "#cc0000");
            } else {
                // Show current value
                double value = tableElement.getCellVoltage(row, col);
                label.setText("= " + formatVoltage(value));
                label.getElement().getStyle().setProperty("color", "#007700");
            }
        }
    }
    

    
    private String validateEquation(String equation) {
        if (equation == null || equation.trim().isEmpty()) {
            return null; // Empty equations are allowed
        }
        
        try {
            // Try to parse the equation using CircuitJS1's expression parser
            ExprParser parser = new ExprParser(equation);
            Expr expr = parser.parseExpression();
            String parseError = parser.gotError();
            
            if (parseError != null) {
                return parseError;
            }
            
            // Try to evaluate with dummy values to catch runtime errors
            ExprState state = new ExprState(9);
            for (int i = 0; i < state.values.length; i++) {
                state.values[i] = 1.0; // Dummy voltage values
            }
            state.t = 0.0;
            
            expr.eval(state);
            return null; // No error
            
        } catch (Exception e) {
            return e.getMessage();
        }
    }
    
    private String formatVoltage(double voltage) {
        if (Math.abs(voltage) < 1e-10) {
            return "0V";
        } else if (Math.abs(voltage) >= 1.0) {
            // Format to 3 decimal places
            double rounded = Math.round(voltage * 1000.0) / 1000.0;
            return rounded + "V";
        } else if (Math.abs(voltage) >= 0.001) {
            // Format to 1 decimal place in millivolts
            double millivolts = voltage * 1000;
            double rounded = Math.round(millivolts * 10.0) / 10.0;
            return rounded + "mV";
        } else {
            // Format to 1 decimal place in microvolts
            double microvolts = voltage * 1000000;
            double rounded = Math.round(microvolts * 10.0) / 10.0;
            return rounded + "μV";
        }
    }
    
    private TextBox createCellTextBox(final int row, final int col) {
        TextBox textBox = new TextBox();
        
        // Set initial content (always equation now)
        textBox.setText(cellEquations[row][col]);
        textBox.setTitle("Enter equation or node name (examples: 'vcc', 'a+b', 'sin(t)')");
        textBox.addStyleName("tableCellInput");
        textBox.addStyleName("equationInput");
        
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                int keyCode = event.getNativeKeyCode();
                
                // Handle Ctrl+S for apply
                if (event.isControlKeyDown() && keyCode == 83) { // Ctrl+S
                    applyChanges();
                    return;
                }
                
                // Handle arrow key navigation (when not editing text)
                if (event.isControlKeyDown()) {
                    switch (keyCode) {
                        case KeyCodes.KEY_UP:
                            navigateToCell(row - 1, col);
                            return;
                        case KeyCodes.KEY_DOWN:
                            navigateToCell(row + 1, col);
                            return;
                        case KeyCodes.KEY_LEFT:
                            navigateToCell(row, col - 1);
                            return;
                        case KeyCodes.KEY_RIGHT:
                            navigateToCell(row, col + 1);
                            return;
                    }
                }
                
                // Store text in equation array
                String text = textBox.getText();
                cellEquations[row][col] = text;
                
                textBox.addStyleName("modified");
                markChanged();
                
                // Update status label with validation
                Label statusLabel = statusLabels.get(row).get(col);
                updateStatusLabel(statusLabel, row, col);
                
                // Handle standard navigation
                if (keyCode == KeyCodes.KEY_ENTER) {
                    navigateToCell(row + 1, col);
                } else if (keyCode == KeyCodes.KEY_TAB && !event.isShiftKeyDown()) {
                    navigateToCell(row, col + 1);
                } else if (keyCode == KeyCodes.KEY_TAB && event.isShiftKeyDown()) {
                    navigateToCell(row, col - 1);
                }
            }
        });
        
        setupTextBoxHandlers(textBox, row, col);
        return textBox;
    }
    
    private void setupTextBoxHandlers(TextBox textBox, final int row, final int col) {
        textBox.addFocusHandler(new FocusHandler() {
            public void onFocus(FocusEvent event) {
                textBox.selectAll();
            }
        });
        
        textBox.addBlurHandler(new BlurHandler() {
            public void onBlur(BlurEvent event) {
                // Validate content if needed
                validateCell(textBox, row, col);
            }
        });
    }
    
    private void navigateToCell(int targetRow, int targetCol) {
        // Navigate within bounds
        if (targetRow >= rows) targetRow = 0;
        if (targetRow < 0) targetRow = rows - 1;
        if (targetCol >= cols) {
            targetCol = 0;
            targetRow++;
            if (targetRow >= rows) targetRow = 0;
        }
        if (targetCol < 0) {
            targetCol = cols - 1;
            targetRow--;
            if (targetRow < 0) targetRow = rows - 1;
        }
        
        // Focus the target cell
        if (targetRow >= 0 && targetRow < rows && 
            targetCol >= 0 && targetCol < cols) {
            TextBox targetBox = cellTextBoxes.get(targetRow).get(targetCol);
            targetBox.setFocus(true);
        }
    }
    
    private void navigateToNextHeader(int currentCol) {
        int nextCol = (currentCol + 1) % cols;
        headerTextBoxes.get(nextCol).setFocus(true);
    }
    
    private void navigateToPrevHeader(int currentCol) {
        int prevCol = (currentCol - 1 + cols) % cols;
        headerTextBoxes.get(prevCol).setFocus(true);
    }
    
    private void navigateToInitialConditions(int col) {
        if (hasInitialConditions && col >= 0 && col < initialConditionsTextBoxes.size()) {
            initialConditionsTextBoxes.get(col).setFocus(true);
        }
    }
    
    private void navigateToNextInitialConditions(int currentCol) {
        int nextCol = (currentCol + 1) % cols;
        if (hasInitialConditions && nextCol < initialConditionsTextBoxes.size()) {
            initialConditionsTextBoxes.get(nextCol).setFocus(true);
        }
    }
    
    private void navigateToPrevInitialConditions(int currentCol) {
        int prevCol = (currentCol - 1 + cols) % cols;
        if (hasInitialConditions && prevCol < initialConditionsTextBoxes.size()) {
            initialConditionsTextBoxes.get(prevCol).setFocus(true);
        }
    }
    
    private void validateCell(TextBox textBox, int row, int col) {
        String text = textBox.getText().trim();
        
        if (row < 0) {
            // Header validation
            if (text.isEmpty()) {
                text = "Col" + (col + 1);
            }
            // Remove invalid characters (keep alphanumeric, underscore, hyphen)
            text = text.replaceAll("[^a-zA-Z0-9_\\-]", "");
            // Ensure it starts with a letter or underscore
            if (!text.matches("^[a-zA-Z_].*")) {
                text = "col" + text;
            }
            
            // Update textbox if changed
            if (!text.equals(textBox.getText())) {
                textBox.setText(text);
            }
            columnHeaders[col] = text;
            
        } else {
            // Cell equation validation - store as-is, validation happens in updateStatusLabel
            cellEquations[row][col] = text;
        }
        
        // Update status label for cells (not headers)
        if (row >= 0) {
            Label statusLabel = statusLabels.get(row).get(col);
            updateStatusLabel(statusLabel, row, col);
        }
    }
    

    
    private void addRow() {
        rows++;
        
        // Expand arrays
        String[][] newCellEquations = new String[rows][cols];
        
        // Copy existing data
        for (int r = 0; r < rows - 1; r++) {
            System.arraycopy(cellEquations[r], 0, newCellEquations[r], 0, cols);
        }
        
        // Initialize new row
        for (int c = 0; c < cols; c++) {
            newCellEquations[rows - 1][c] = "node" + ((rows - 1) * cols + c + 1);
        }
        
        cellEquations = newCellEquations;
        markChanged();
        populateGrid();
    }
    
    private void removeRow() {
        if (rows <= 1) {
            statusLabel.setText("Cannot remove row - table must have at least one row");
            return;
        }
        
        rows--;
        
        // Shrink arrays
        String[][] newCellEquations = new String[rows][cols];
        
        // Copy existing data
        for (int r = 0; r < rows; r++) {
            System.arraycopy(cellEquations[r], 0, newCellEquations[r], 0, cols);
        }
        
        cellEquations = newCellEquations;
        markChanged();
        populateGrid();
    }
    
    private void addColumn() {
        cols++;
        
        // Expand arrays
        String[][] newCellEquations = new String[rows][cols];
        String[] newColumnHeaders = new String[cols];
        double[] newInitialConditionsValues = new double[cols];
        
        // Copy and expand existing data
        for (int r = 0; r < rows; r++) {
            System.arraycopy(cellEquations[r], 0, newCellEquations[r], 0, cols - 1);
            
            // Initialize new column
            newCellEquations[r][cols - 1] = "node" + (r * cols + (cols - 1) + 1);
        }
        
        System.arraycopy(columnHeaders, 0, newColumnHeaders, 0, cols - 1);
        newColumnHeaders[cols - 1] = "Col" + cols;
        
        System.arraycopy(initialConditionsValues, 0, newInitialConditionsValues, 0, cols - 1);
        newInitialConditionsValues[cols - 1] = 0.0; // Default initial condition value
        
        cellEquations = newCellEquations;
        columnHeaders = newColumnHeaders;
        initialConditionsValues = newInitialConditionsValues;
        markChanged();
        populateGrid();
    }
    
    private void removeColumn() {
        if (cols <= 1) {
            statusLabel.setText("Cannot remove column - table must have at least one column");
            return;
        }
        
        cols--;
        
        // Shrink arrays
        String[][] newCellEquations = new String[rows][cols];
        String[] newColumnHeaders = new String[cols];
        double[] newInitialConditionsValues = new double[cols];
        
        // Copy existing data
        for (int r = 0; r < rows; r++) {
            System.arraycopy(cellEquations[r], 0, newCellEquations[r], 0, cols);
        }
        
        System.arraycopy(columnHeaders, 0, newColumnHeaders, 0, cols);
        System.arraycopy(initialConditionsValues, 0, newInitialConditionsValues, 0, cols);
        
        cellEquations = newCellEquations;
        columnHeaders = newColumnHeaders;
        initialConditionsValues = newInitialConditionsValues;
        markChanged();
        populateGrid();
    }
    
    private void updateButtonStates() {
        removeRowButton.setEnabled(rows > 1);
        removeColButton.setEnabled(cols > 1);
        applyButton.setEnabled(hasChanges);
    }
    
    private void markChanged() {
        if (!hasChanges) {
            hasChanges = true;
            updateButtonStates();
            statusLabel.setText("Table modified - use Apply or OK to save changes");
        }
    }
    
    private void applyChanges() {
        if (!hasChanges) return;
        
        // Apply data changes with new size
        tableElement.resizeTable(rows, cols);
        
        // Apply initial conditions settings
        tableElement.setHasInitialConditions(hasInitialConditions);
        for (int col = 0; col < cols; col++) {
            tableElement.setInitialConditionValue(col, initialConditionsValues[col]);
        }
        
        // Apply cell equations
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                tableElement.setCellEquation(row, col, cellEquations[row][col]);
            }
        }
        
        // Apply column headers
        for (int col = 0; col < cols; col++) {
            tableElement.setColumnHeader(col, columnHeaders[col]);
        }
        
        // Update table display
        tableElement.setPoints();
        
        hasChanges = false;
        updateButtonStates();
        statusLabel.setText("Changes applied successfully");
        
        // Clear modified styling
        clearModifiedStyling();
        
        // Refresh the simulation display
        if (sim != null) {
            sim.repaint();
        }
    }
    
    private void clearModifiedStyling() {
        // Clear header modification styling
        for (TextBox headerBox : headerTextBoxes) {
            headerBox.removeStyleName("modified");
        }
        
        // Clear initial conditions modification styling
        if (initialConditionsTextBoxes != null) {
            for (TextBox initialBox : initialConditionsTextBoxes) {
                initialBox.removeStyleName("modified");
                initialBox.removeStyleName("error");
            }
        }
        
        // Clear cell modification styling
        for (List<TextBox> rowBoxes : cellTextBoxes) {
            for (TextBox cellBox : rowBoxes) {
                cellBox.removeStyleName("modified");
            }
        }
        
        // Update all status labels to show current values
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Label statusLabel = statusLabels.get(row).get(col);
                updateStatusLabel(statusLabel, row, col);
            }
        }
    }
    
    @Override
    public void closeDialog() {
        super.closeDialog();
    }
}