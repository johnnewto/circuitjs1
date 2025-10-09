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
    
    // Data storage
    private String[][] cellLabels;
    private String[] columnHeaders;
    private int rows, cols;
    
    // Cell editing management
    private List<List<TextBox>> cellTextBoxes;
    private List<TextBox> headerTextBoxes;
    
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
        cellLabels = new String[rows][cols];
        columnHeaders = new String[cols];
        
        // Copy cell labels
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                cellLabels[row][col] = tableElement.getCellLabel(row, col);
                if (cellLabels[row][col] == null) {
                    cellLabels[row][col] = "node" + (row * cols + col + 1);
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
        // Create grid with extra row for headers
        editGrid = new Grid(rows + 1, cols + 1);
        editGrid.addStyleName("tableEditGrid");
        editGrid.setCellSpacing(1);
        editGrid.setCellPadding(2);
        
        // Initialize TextBox collections
        cellTextBoxes = new ArrayList<List<TextBox>>();
        headerTextBoxes = new ArrayList<TextBox>();
        
        // Add row headers (row numbers)
        editGrid.setText(0, 0, "");  // Top-left corner
        for (int row = 0; row < rows; row++) {
            Label rowLabel = new Label("" + (row + 1));
            rowLabel.addStyleName("tableRowHeader");
            editGrid.setWidget(row + 1, 0, rowLabel);
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
        
        // Add cell editors
        for (int row = 0; row < rows; row++) {
            List<TextBox> rowTextBoxes = new ArrayList<TextBox>();
            cellTextBoxes.add(rowTextBoxes);
            
            for (int col = 0; col < cols; col++) {
                TextBox cellBox = createCellTextBox(row, col);
                rowTextBoxes.add(cellBox);
                editGrid.setWidget(row + 1, col + 1, cellBox);
            }
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
                    navigateToCell(0, col);
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
    
    private TextBox createCellTextBox(final int row, final int col) {
        TextBox textBox = new TextBox();
        textBox.setText(cellLabels[row][col]);
        textBox.addStyleName("tableCellInput");
        
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
                
                cellLabels[row][col] = textBox.getText();
                textBox.addStyleName("modified");
                markChanged();
                
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
    
    private void validateCell(TextBox textBox, int row, int col) {
        String text = textBox.getText().trim();
        String originalText = text;
        
        // Basic validation - no empty cells
        if (text.isEmpty()) {
            if (row >= 0) {
                text = "node" + (row * cols + col + 1);
            } else {
                text = "Col" + (col + 1);
            }
        }
        
        // Remove invalid characters (keep alphanumeric, underscore, hyphen)
        text = text.replaceAll("[^a-zA-Z0-9_\\-]", "");
        
        // Ensure it starts with a letter or underscore
        if (!text.matches("^[a-zA-Z_].*")) {
            if (row >= 0) {
                text = "node" + text;
            } else {
                text = "col" + text;
            }
        }
        
        // Allow duplicate labels - no validation needed
        
        // Update textbox if changed
        if (!text.equals(originalText)) {
            textBox.setText(text);
        }
        
        // Store the validated value
        if (row >= 0) {
            cellLabels[row][col] = text;
        } else {
            columnHeaders[col] = text;
        }
    }
    

    
    private void addRow() {
        rows++;
        
        // Expand cellLabels array
        String[][] newCellLabels = new String[rows][cols];
        for (int r = 0; r < rows - 1; r++) {
            System.arraycopy(cellLabels[r], 0, newCellLabels[r], 0, cols);
        }
        
        // Initialize new row with simple naming
        for (int c = 0; c < cols; c++) {
            newCellLabels[rows - 1][c] = "node" + ((rows - 1) * cols + c + 1);
        }
        
        cellLabels = newCellLabels;
        markChanged();
        populateGrid();
    }
    
    private void removeRow() {
        if (rows <= 1) {
            statusLabel.setText("Cannot remove row - table must have at least one row");
            return;
        }
        
        rows--;
        
        // Shrink cellLabels array
        String[][] newCellLabels = new String[rows][cols];
        for (int r = 0; r < rows; r++) {
            System.arraycopy(cellLabels[r], 0, newCellLabels[r], 0, cols);
        }
        
        cellLabels = newCellLabels;
        markChanged();
        populateGrid();
    }
    
    private void addColumn() {
        cols++;
        
        // Expand arrays
        String[][] newCellLabels = new String[rows][cols];
        String[] newColumnHeaders = new String[cols];
        
        for (int r = 0; r < rows; r++) {
            System.arraycopy(cellLabels[r], 0, newCellLabels[r], 0, cols - 1);
            newCellLabels[r][cols - 1] = "node" + (r * cols + (cols - 1) + 1);
        }
        
        System.arraycopy(columnHeaders, 0, newColumnHeaders, 0, cols - 1);
        newColumnHeaders[cols - 1] = "Col" + cols;
        
        cellLabels = newCellLabels;
        columnHeaders = newColumnHeaders;
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
        String[][] newCellLabels = new String[rows][cols];
        String[] newColumnHeaders = new String[cols];
        
        for (int r = 0; r < rows; r++) {
            System.arraycopy(cellLabels[r], 0, newCellLabels[r], 0, cols);
        }
        
        System.arraycopy(columnHeaders, 0, newColumnHeaders, 0, cols);
        
        cellLabels = newCellLabels;
        columnHeaders = newColumnHeaders;
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
        
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                tableElement.setCellLabel(row, col, cellLabels[row][col]);
            }
        }
        
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
        
        // Clear cell modification styling
        for (List<TextBox> rowBoxes : cellTextBoxes) {
            for (TextBox cellBox : rowBoxes) {
                cellBox.removeStyleName("modified");
            }
        }
    }
    
    @Override
    public void closeDialog() {
        super.closeDialog();
    }
}