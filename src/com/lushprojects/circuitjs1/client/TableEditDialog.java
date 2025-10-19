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
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.lushprojects.circuitjs1.client.util.Locale;
import java.util.HashMap;
import java.util.Map;

/**
 * TableEditDialog - Dynamic table editor implementing markdown specification
 * Provides structured editing with contextual buttons for row/column manipulation
 */
public class TableEditDialog extends Dialog {
    
    private final TableElm tableElement;
    private final CirSim sim;
    
    private ScrollPanel scrollPanel;
    private Grid editGrid;
    private Button applyButton;
    private Label statusLabel;
    
    // Fixed structure according to markdown specification
    private static final String FLOWS_LABEL = "Flows↓/Stock Vars →";
    private static final String INITIAL_CONDITIONS_LABEL = "InitialConditions";
    
    // Unicode symbols for contextual buttons - easily configurable
    private static final String SYMBOL_ADD = "⧾";           // Add column/row button
    private static final String SYMBOL_DELETE = "⧿";       // Delete column/row button  
    private static final String SYMBOL_LEFT = "⇐";         // Move left button
    private static final String SYMBOL_RIGHT = "⇒";        // Move right button
    private static final String SYMBOL_UP = "⇑";           // Move up button
    private static final String SYMBOL_DOWN = "⇓";         // Move down button
    
    // Column type enumeration for financial accounting
    public enum ColumnType {
        ASSET,
        LIABILITY,
        EQUITY,
        A_L_E  // For A-L-E column
    }
    
    /**
     * Container for column data during move operations
     */
    private static class ColumnData {
        String[] cellData;
        String stockValue;  // Stock value is used as column header
        double initialValue;
        ColumnType type;
        
        ColumnData(int rows) {
            cellData = new String[rows];
        }
    }
    
    /**
     * State machine for column movement operations
     * Simplified: columns can only move within their own type group
     */
    private static class ColumnMoveStateMachine {
        
        /**
         * Represents the region a column currently occupies
         */
        enum Region {
            ASSET_REGION,      // Left side - Asset columns
            LIABILITY_REGION,  // Center - Liability columns
            EQUITY_REGION,     // Right side - Equity column only
            COMPUTED_REGION    // Rightmost - A-L-E column only
        }
        
        /**
         * Represents a column movement transition
         */
        static class MoveTransition {
            Region fromRegion;
            Region toRegion;
            ColumnType originalType;
            ColumnType newType;
            boolean isValid;
            String statusMessage;
            
            MoveTransition(Region from, Region to, ColumnType original) {
                this.fromRegion = from;
                this.toRegion = to;
                this.originalType = original;
                this.newType = original; // Type never changes in simplified version
                this.isValid = true;
            }
        }
        
        private final TableEditDialog dialog;
        
        ColumnMoveStateMachine(TableEditDialog dialog) {
            this.dialog = dialog;
        }
        
        /**
         * Determine which region a column index belongs to
         */
        Region getRegion(int colIndex) {
            if (colIndex < 0 || colIndex >= dialog.dataCols) {
                return null;
            }
            
            ColumnType type = dialog.columnTypes[colIndex];
            
            // Computed column is always rightmost
            if (type == ColumnType.A_L_E) {
                return Region.COMPUTED_REGION;
            }
            
            // Equity column is always right of liabilities
            if (type == ColumnType.EQUITY) {
                return Region.EQUITY_REGION;
            }
            
            // Find Asset-Liability boundary
            int boundary = dialog.findAssetLiabilityBoundary();
            
            if (colIndex < boundary) {
                return Region.ASSET_REGION;
            } else {
                return Region.LIABILITY_REGION;
            }
        }
        
        /**
         * Calculate the movement transition - simplified to only allow same-region moves
         */
        MoveTransition calculateTransition(int fromIndex, int toIndex) {
            Region fromRegion = getRegion(fromIndex);
            Region toRegion = getRegion(toIndex);
            ColumnType originalType = dialog.columnTypes[fromIndex];
            
            MoveTransition transition = new MoveTransition(fromRegion, toRegion, originalType);
            
            // Check if trying to move to different region
            if (fromRegion != toRegion) {
                transition.isValid = false;
                transition.statusMessage = "Cannot move " + originalType.name() + " column to " + toRegion.name().replace("_", " ") + 
                                          ". Columns can only move within their own type group.";
                return transition;
            }
            
            // Moving within same region - allowed
            transition.statusMessage = originalType.name() + " column moved within " + fromRegion.name().replace("_", " ");
            return transition;
        }
    }
    
    // Data storage for dynamic content
    private String[][] cellData;  // Editable cell content
    private String[] stockValues; // Stock variable names (H0, H1, H2, etc.) - also used as column headers
    private double[] initialValues; // Initial condition values
    private int dataRows, dataCols; // Number of data rows/columns (excluding fixed structure)
    private ColumnType[] columnTypes; // Type for each column (Asset/Liability/Equity/Computed)
    
    // Grid structure indices
    private static final int HEADER_ROW = 0;
    private static final int BUTTON_ROW = 1;
    private static final int STOCK_VALUES_ROW = 2;  // New editable row for output stock values
    // private static final int FLOWS_ROW = 2; // New editable row for output stock values
    private static final int INITIAL_ROW = 3;
    private static final int DATA_START_ROW = 4;
    
    private static final int BUTTON_COL = 0;
    private static final int LABEL_COL = 1;  // New column for labels
    private static final int DATA_START_COL = 2;
    
    // Button management
    private Map<String, Button> contextualButtons;
    
    // Track changes
    private boolean hasChanges = false;    public TableEditDialog(TableElm tableElm, CirSim cirSim) {
        super();
        this.tableElement = tableElm;
        this.sim = cirSim;
        
        // Initialize with data from TableElm or defaults for new table
        this.dataRows = Math.max(1, tableElm.getRows()); // At least 1 data row
        this.dataCols = Math.max(4, tableElm.getCols()); // At least Assets, Liabilities, Equity, A-L-E columns
        
        // Initialize contextual buttons map
        contextualButtons = new HashMap<String, Button>();
        
        copyTableData();
        
        // Use table title from TableElm, or default to "Edit Table Data"
        String dialogTitle = tableElm.getTableTitle();
        if (dialogTitle == null || dialogTitle.trim().isEmpty()) {
            dialogTitle = "Table";
        }
        setText(Locale.LS("Edit " + dialogTitle));
        
        setupUI();
        populateGrid();
        center();
    }
    
    
    private void copyTableData() {
        // Initialize data arrays
        cellData = new String[dataRows][dataCols];
        
        // Initialize stock values (used as column headers) with defaults from specification
        // Data columns start after "Flow Description" column
        stockValues = new String[dataCols];
        initialValues = new double[dataCols];
        columnTypes = new ColumnType[dataCols];
        
        // Set default stock values and types according to specification
        // Initial configuration: 1 Asset, 1 Liability, 1 Equity, 1 Computed (A-L-E)
        if (dataCols >= 1) {
            stockValues[0] = "Stock0";
            columnTypes[0] = ColumnType.ASSET;
        }
        if (dataCols >= 2) {
            stockValues[1] = "Stock1";
            columnTypes[1] = ColumnType.LIABILITY;
        }
        if (dataCols >= 3) {
            stockValues[2] = "Stock2";
            columnTypes[2] = ColumnType.EQUITY;
        }
        if (dataCols >= 4) {
            stockValues[3] = "Stock3";
            columnTypes[3] = ColumnType.A_L_E;
        }
        
        // Additional columns get default H names and are Assets by default
        for (int col = 4; col < dataCols; col++) {
            stockValues[col] = "Stock" + col;
            columnTypes[col] = ColumnType.ASSET;
        }
        
        // Copy existing data from TableElm if available
        if (tableElement != null) {
            int existingRows = tableElement.getRows();
            int existingCols = tableElement.getCols();
            
            // Copy cell equations
            for (int row = 0; row < Math.min(dataRows, existingRows); row++) {
                for (int col = 0; col < Math.min(dataCols, existingCols); col++) {
                    cellData[row][col] = tableElement.getCellEquation(row, col);
                    if (cellData[row][col] == null) {
                        cellData[row][col] = "";
                    }
                }
            }
            
            // Copy stock values (column headers) and types if they exist
            for (int col = 0; col < Math.min(dataCols, existingCols); col++) {
                String existingHeader = tableElement.getColumnHeader(col);
                if (existingHeader != null && !existingHeader.trim().isEmpty()) {
                    stockValues[col] = existingHeader;
                }
                
                initialValues[col] = tableElement.getInitialValue(col);
                
                // Copy column type if available
                ColumnType existingType = tableElement.getColumnType(col);
                if (existingType != null) {
                    columnTypes[col] = existingType;
                }
            }
        }
        
        // Ensure all cells have non-null values
        for (int row = 0; row < dataRows; row++) {
            for (int col = 0; col < dataCols; col++) {
                if (cellData[row][col] == null) {
                    cellData[row][col] = "";
                }
            }
        }
    }
    
    private void setupUI() {
        // UI Components
        VerticalPanel mainPanel = new VerticalPanel();
        mainPanel.setWidth("100%");
        setWidget(mainPanel);
        
        // Scrollable table area (main content)
        scrollPanel = new ScrollPanel();
        scrollPanel.setSize("800px", "500px");
        scrollPanel.addStyleName("topSpace");
        mainPanel.add(scrollPanel);
        
        // Bottom buttons
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
        
        buttonPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
        Button cancelButton = new Button(Locale.LS("Cancel"));
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
        
        // Status label at bottom
        statusLabel = new Label("Dynamic Table Editor - Use contextual buttons to modify structure");
        statusLabel.addStyleName("topSpace");
        mainPanel.add(statusLabel);
    }
    
    private void createGrid() {
        // Calculate grid dimensions according to updated specification
        // Rows: Header + Button controls + Stock values + Flows label + Initial conditions + data rows
        int totalGridRows = DATA_START_ROW + dataRows;
        // Cols: Buttons column + Label column + data columns  
        int totalGridCols = 2 + dataCols; // Button col + Label col + data columns
        
        editGrid = new Grid(totalGridRows, totalGridCols);
        editGrid.addStyleName("tableEditGrid");
        editGrid.setCellSpacing(1);
        editGrid.setCellPadding(2);
        
        // Clear contextual buttons for refresh
        contextualButtons.clear();
        
        populateFixedStructure();
        populateDataCells();
        populateContextualButtons();
    }
    
    private void populateGrid() {
        createGrid();
        scrollPanel.setWidget(editGrid);
        updateButtonStates();
    }
    
    private void populateFixedStructure() {
        // Row 0: Headers - first two columns empty, then data headers with type indicators
        editGrid.setText(HEADER_ROW, BUTTON_COL, "");
        editGrid.setText(HEADER_ROW, LABEL_COL, "");
        
        // Add data column headers with type indicators
        for (int col = 0; col < dataCols; col++) {
            ColumnType colType = columnTypes[col];
            
            // Add type indicator emoji/symbol
            String typeIndicator = "";
            switch (colType) {
                case ASSET: typeIndicator = "💰"; break;
                case LIABILITY: typeIndicator = "📄"; break;
                case EQUITY: typeIndicator = "🏦"; break;
                case A_L_E: typeIndicator = "🧮"; break;
            }
            
            editGrid.setText(HEADER_ROW, DATA_START_COL + col, typeIndicator + " [" + colType.name() + "]");
        }
        
        // Row 1: Control buttons (populated in populateContextualButtons)
        editGrid.setText(BUTTON_ROW, BUTTON_COL, "");
        editGrid.setText(BUTTON_ROW, LABEL_COL, "");
        
        // Row 2: Stock Values - editable row for output stock values
        editGrid.setText(STOCK_VALUES_ROW, BUTTON_COL, "");
        editGrid.setText(STOCK_VALUES_ROW, LABEL_COL, FLOWS_LABEL);
        
        // Add editable stock value inputs
        for (int col = 0; col < dataCols; col++) {
            TextBox stockBox = createStockValueTextBox(col);
            editGrid.setWidget(STOCK_VALUES_ROW, DATA_START_COL + col, stockBox);
        }
        
        
        // Row 3: Initial conditions
        editGrid.setText(INITIAL_ROW, BUTTON_COL, "");
        editGrid.setText(INITIAL_ROW, LABEL_COL, INITIAL_CONDITIONS_LABEL);
        
        // Add initial condition value inputs
        for (int col = 0; col < dataCols; col++) {
            TextBox initialBox = createInitialValueTextBox(col);
            editGrid.setWidget(INITIAL_ROW, DATA_START_COL + col, initialBox);
        }
    }
    
    private void populateDataCells() {
        // Add data row content
        for (int row = 0; row < dataRows; row++) {
            int gridRow = DATA_START_ROW + row;
            
            // Button column (populated in populateContextualButtons)
            editGrid.setText(gridRow, BUTTON_COL, "");
            
            // Label column - editable text for flow descriptions
            TextBox flowDescBox = createFlowDescriptionTextBox(row);
            editGrid.setWidget(gridRow, LABEL_COL, flowDescBox);
            
            // Data columns
            for (int col = 0; col < dataCols; col++) {
                TextBox cellBox = createCellTextBox(row, col);
                editGrid.setWidget(gridRow, DATA_START_COL + col, cellBox);
            }
        }
    }
    
    private void populateContextualButtons() {
        // Add contextual control buttons per markdown specification
        
        // Column control buttons in button row (row 1)
        for (int col = 0; col < dataCols; col++) {
            HorizontalPanel buttonPanel = new HorizontalPanel();
            buttonPanel.setSpacing(2);
            
            final int finalCol = col;
            ColumnType colType = columnTypes[col];
            
            // Add column button - only if not Equity or Computed
            if (canAddColumnAfter(col)) {
                Button addColBtn = createButton(SYMBOL_ADD, "Add " + colType.name() + " column after " + stockValues[col]);
                addColBtn.addClickHandler(new ClickHandler() {
                    public void onClick(ClickEvent event) {
                        insertColumnAfter(finalCol);
                    }
                });
                buttonPanel.add(addColBtn);
            }
            
            // Delete column button - only if allowed
            if (canDeleteColumn(col)) {
                Button delColBtn = createButton(SYMBOL_DELETE, "Delete column " + stockValues[col]);
                delColBtn.addClickHandler(new ClickHandler() {
                    public void onClick(ClickEvent event) {
                        deleteColumn(finalCol);
                    }
                });
                buttonPanel.add(delColBtn);
            }
            
            // Movement buttons - smart display based on column count in type group
            if (canMoveColumn(col)) {
                int typeCount = countColumnsByType(colType);
                
                // Only show movement arrows if there are 2+ columns of this type
                if (typeCount >= 2) {
                    // Check if we can move left within same type
                    if (canMoveLeftWithinType(col)) {
                        Button moveLeftBtn = createButton(SYMBOL_LEFT, "Move column left");
                        moveLeftBtn.addClickHandler(new ClickHandler() {
                            public void onClick(ClickEvent event) {
                                moveColumn(finalCol, finalCol - 1);
                            }
                        });
                        buttonPanel.add(moveLeftBtn);
                    }
                    
                    // Check if we can move right within same type
                    if (canMoveRightWithinType(col)) {
                        Button moveRightBtn = createButton(SYMBOL_RIGHT, "Move column right");
                        moveRightBtn.addClickHandler(new ClickHandler() {
                            public void onClick(ClickEvent event) {
                                moveColumn(finalCol, finalCol + 1);
                            }
                        });
                        buttonPanel.add(moveRightBtn);
                    }
                }
                // If only 1 column of this type, no movement buttons (just + button from above)
            }
            
            editGrid.setWidget(BUTTON_ROW, DATA_START_COL + col, buttonPanel);
        }
        
        // Row control buttons in button column
        for (int row = 0; row < dataRows; row++) {
            HorizontalPanel buttonPanel = new HorizontalPanel();
            buttonPanel.setSpacing(2);
            
            // Add row button
            Button addRowBtn = createButton(SYMBOL_ADD, "Add row after row " + (row + 1));
            final int finalRow = row;
            addRowBtn.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    insertRowAfter(finalRow);
                }
            });
            buttonPanel.add(addRowBtn);
            
            // Delete row button
            if (dataRows > 1) { // Don't allow deleting the last row
                Button delRowBtn = createButton(SYMBOL_DELETE, "Delete row " + (row + 1));
                delRowBtn.addClickHandler(new ClickHandler() {
                    public void onClick(ClickEvent event) {
                        deleteRow(finalRow);
                    }
                });
                buttonPanel.add(delRowBtn);
            }
            
            // Movement buttons
            if (row > 0) {
                Button moveUpBtn = createButton(SYMBOL_UP, "Move row up");
                moveUpBtn.addClickHandler(new ClickHandler() {
                    public void onClick(ClickEvent event) {
                        moveRow(finalRow, finalRow - 1);
                    }
                });
                buttonPanel.add(moveUpBtn);
            }
            
            if (row < dataRows - 1) {
                Button moveDownBtn = createButton(SYMBOL_DOWN, "Move row down");
                moveDownBtn.addClickHandler(new ClickHandler() {
                    public void onClick(ClickEvent event) {
                        moveRow(finalRow, finalRow + 1);
                    }
                });
                buttonPanel.add(moveDownBtn);
            }
            
            editGrid.setWidget(DATA_START_ROW + row, BUTTON_COL, buttonPanel);
        }
    }
    
    private TextBox createFlowDescriptionTextBox(final int row) {
        final TextBox textBox = new TextBox();
        // Initialize with row description from TableElm
        String rowDesc = tableElement.getRowDescription(row);
        textBox.setText(rowDesc != null ? rowDesc : "");
        textBox.addStyleName("tableFlowInput");
        
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                // Store flow description in TableElm's rowDescriptions
                tableElement.setRowDescription(row, textBox.getText());
                textBox.addStyleName("modified");
                markChanged();
            }
        });
        
        return textBox;
    }
    
    private Button createButton(String symbol, String tooltip) {
        Button button = new Button(symbol);
        button.addStyleName("contextualButton");
        button.setTitle(tooltip);
        return button;
    }
    
    private TextBox createStockValueTextBox(final int col) {
        final TextBox textBox = new TextBox();
        textBox.setText(stockValues[col]); // Initialize with H0, H1, H2, etc.
        textBox.addStyleName("tableStockInput");
        
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                // Store stock value name
                stockValues[col] = textBox.getText();
                textBox.addStyleName("modified");
                markChanged();
            }
        });
        
        textBox.addFocusHandler(new FocusHandler() {
            public void onFocus(FocusEvent event) {
                textBox.selectAll();
            }
        });
        
        return textBox;
    }
    
    private TextBox createInitialValueTextBox(final int col) {
        final TextBox textBox = new TextBox();
        textBox.setText(Double.toString(initialValues[col]));
        textBox.addStyleName("tableInitialInput");
        
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                try {
                    double value = Double.parseDouble(textBox.getText());
                    initialValues[col] = value;
                    textBox.removeStyleName("error");
                } catch (NumberFormatException e) {
                    textBox.addStyleName("error");
                    // Don't update the value if it's not valid
                }
                textBox.addStyleName("modified");
                markChanged();
            }
        });
        
        textBox.addFocusHandler(new FocusHandler() {
            public void onFocus(FocusEvent event) {
                textBox.selectAll();
            }
        });
        
        return textBox;
    }
    
    private TextBox createCellTextBox(final int row, final int col) {
        final TextBox textBox = new TextBox();
        textBox.setText(cellData[row][col]);
        textBox.addStyleName("tableCellInput");
        
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                cellData[row][col] = textBox.getText();
                textBox.addStyleName("modified");
                markChanged();
            }
        });
        
        textBox.addFocusHandler(new FocusHandler() {
            public void onFocus(FocusEvent event) {
                textBox.selectAll();
            }
        });
        
        return textBox;
    }
    
    // Helper methods for smart button display
    
    /**
     * Check if we can move a column left within its type group
     */
    private boolean canMoveLeftWithinType(int col) {
        if (col <= 0) return false;
        if (!canMoveColumn(col)) return false;
        
        ColumnType currentType = columnTypes[col];
        ColumnType leftType = columnTypes[col - 1];
        
        // Can only move left if the column to the left is the same type
        return currentType == leftType;
    }
    
    /**
     * Check if we can move a column right within its type group
     */
    private boolean canMoveRightWithinType(int col) {
        if (col >= dataCols - 1) return false;
        if (!canMoveColumn(col)) return false;
        
        ColumnType currentType = columnTypes[col];
        ColumnType rightType = columnTypes[col + 1];
        
        // Can only move right if the column to the right is the same type
        return currentType == rightType;
    }
    
    // Row and column manipulation methods
    private void insertRowAfter(int rowIndex) {
        dataRows++;
        
        // Expand cell data array
        String[][] newCellData = new String[dataRows][dataCols];
        
        // Copy existing data
        for (int r = 0; r <= rowIndex; r++) {
            System.arraycopy(cellData[r], 0, newCellData[r], 0, dataCols);
        }
        
        // Initialize new row
        for (int c = 0; c < dataCols; c++) {
            newCellData[rowIndex + 1][c] = "";
        }
        
        // Copy remaining rows
        for (int r = rowIndex + 1; r < dataRows - 1; r++) {
            System.arraycopy(cellData[r], 0, newCellData[r + 1], 0, dataCols);
        }
        
        cellData = newCellData;
        setStatus("Row added after row " + (rowIndex + 1) + ". Total rows: " + dataRows);
        markChanged();
        populateGrid();
    }
    
    private void deleteRow(int rowIndex) {
        if (dataRows <= 1) {
            setStatus("Cannot delete the last row - at least one row is required");
            return;
        }
        
        dataRows--;
        
        // Shrink cell data array
        String[][] newCellData = new String[dataRows][dataCols];
        
        // Copy data excluding deleted row
        int newRow = 0;
        for (int r = 0; r < dataRows + 1; r++) {
            if (r != rowIndex) {
                System.arraycopy(cellData[r], 0, newCellData[newRow], 0, dataCols);
                newRow++;
            }
        }
        
        cellData = newCellData;
        setStatus("Row " + (rowIndex + 1) + " deleted. Total rows: " + dataRows);
        markChanged();
        populateGrid();
    }
    
    private void moveRow(int fromIndex, int toIndex) {
        if (fromIndex == toIndex || toIndex < 0 || toIndex >= dataRows) return;
        
        // Swap row data (cell equations)
        String[] tempRow = cellData[fromIndex];
        cellData[fromIndex] = cellData[toIndex];
        cellData[toIndex] = tempRow;
        
        // Swap row descriptions in TableElm to keep them synchronized
        String tempDesc = tableElement.getRowDescription(fromIndex);
        tableElement.setRowDescription(fromIndex, tableElement.getRowDescription(toIndex));
        tableElement.setRowDescription(toIndex, tempDesc);
        
        String direction = (fromIndex < toIndex) ? "down" : "up";
        setStatus("Row " + (fromIndex + 1) + " moved " + direction + " to position " + (toIndex + 1));
        markChanged();
        populateGrid();
    }
    
    private void insertColumnAfter(int colIndex) {
        dataCols++;
        
        // Expand arrays
        String[][] newCellData = new String[dataRows][dataCols];
        String[] newStockValues = new String[dataCols];
        double[] newInitialValues = new double[dataCols];
        ColumnType[] newColumnTypes = new ColumnType[dataCols];
        
        // Copy existing data
        for (int r = 0; r < dataRows; r++) {
            for (int c = 0; c <= colIndex; c++) {
                newCellData[r][c] = cellData[r][c];
            }
            newCellData[r][colIndex + 1] = ""; // New column
            for (int c = colIndex + 1; c < dataCols - 1; c++) {
                newCellData[r][c + 1] = cellData[r][c];
            }
        }
        
        // Determine the type for the new column based on the column after which it's inserted
        ColumnType newColumnType = columnTypes[colIndex]; // Same type as the column before it
        
        // Copy stock values, initial values, and types
        for (int c = 0; c <= colIndex; c++) {
            newStockValues[c] = stockValues[c];
            newInitialValues[c] = initialValues[c];
            newColumnTypes[c] = columnTypes[c];
        }
        newStockValues[colIndex + 1] = "H" + (colIndex + 1); // Assign next H number
        newInitialValues[colIndex + 1] = 0.0;
        newColumnTypes[colIndex + 1] = newColumnType;
        for (int c = colIndex + 1; c < dataCols - 1; c++) {
            newStockValues[c + 1] = stockValues[c];
            newInitialValues[c + 1] = initialValues[c];
            newColumnTypes[c + 1] = columnTypes[c];
        }
        
        cellData = newCellData;
        stockValues = newStockValues;
        initialValues = newInitialValues;
        columnTypes = newColumnTypes;
        
        setStatus("New " + newColumnType.name() + " column added after " + stockValues[colIndex] + ". Total columns: " + dataCols);
        markChanged();
        populateGrid();
    }
    
    private void deleteColumn(int colIndex) {
        if (dataCols <= 1) {
            setStatus("Cannot delete the last column - at least one column is required");
            return;
        }
        
        // Prevent deleting Equity column
        if (columnTypes[colIndex] == ColumnType.EQUITY) {
            setStatus("Cannot delete Equity column - it is required");
            return;
        }
        
        // Prevent deleting Computed column
        if (columnTypes[colIndex] == ColumnType.A_L_E) {
            setStatus("Cannot delete Computed (A-L-E) column - it is required");
            return;
        }
        
        // Prevent deleting if it's the last Asset or last Liability
        if (columnTypes[colIndex] == ColumnType.ASSET && countColumnsByType(ColumnType.ASSET) <= 1) {
            setStatus("Cannot delete the last ASSET column - at least one is required");
            return;
        }
        
        if (columnTypes[colIndex] == ColumnType.LIABILITY && countColumnsByType(ColumnType.LIABILITY) <= 1) {
            setStatus("Cannot delete the last LIABILITY column - at least one is required");
            return;
        }
        
        String deletedColumnName = stockValues[colIndex];
        ColumnType deletedType = columnTypes[colIndex];
        
        dataCols--;
        
        // Shrink arrays
        String[][] newCellData = new String[dataRows][dataCols];
        String[] newStockValues = new String[dataCols];
        double[] newInitialValues = new double[dataCols];
        ColumnType[] newColumnTypes = new ColumnType[dataCols];
        
        // Copy data excluding deleted column
        for (int r = 0; r < dataRows; r++) {
            int newCol = 0;
            for (int c = 0; c < dataCols + 1; c++) {
                if (c != colIndex) {
                    newCellData[r][newCol] = cellData[r][c];
                    newCol++;
                }
            }
        }
        
        // Copy stock values, initial values, and types excluding deleted column
        int newCol = 0;
        for (int c = 0; c < dataCols + 1; c++) {
            if (c != colIndex) {
                newStockValues[newCol] = stockValues[c];
                newInitialValues[newCol] = initialValues[c];
                newColumnTypes[newCol] = columnTypes[c];
                newCol++;
            }
        }
        
        cellData = newCellData;
        stockValues = newStockValues;
        initialValues = newInitialValues;
        columnTypes = newColumnTypes;
        
        setStatus(deletedType.name() + " column '" + deletedColumnName + "' deleted. Total columns: " + dataCols);
        markChanged();
        populateGrid();
    }
    
    /**
     * Move a column from one position to another within the same type group
     * Simplified: no type conversions or auto-creation
     */
    private void moveColumn(int fromIndex, int toIndex) {
        // Validate basic constraints
        if (!isValidMove(fromIndex, toIndex)) {
            return;
        }
        
        // Create state machine
        ColumnMoveStateMachine stateMachine = new ColumnMoveStateMachine(this);
        
        // Calculate transition to validate same-region move
        ColumnMoveStateMachine.MoveTransition transition = stateMachine.calculateTransition(fromIndex, toIndex);
        
        // Check if move is valid (same region only)
        if (!transition.isValid) {
            setStatus(transition.statusMessage);
            return;
        }
        
        // Simple swap approach: backup destination, copy source to dest, copy backup to source
        ColumnData destBackup = extractColumn(toIndex);
        ColumnData sourceData = extractColumn(fromIndex);
        
        // Copy source to destination
        overwriteColumnAt(toIndex, sourceData.cellData, sourceData.stockValue, 
                         sourceData.initialValue, sourceData.type);
        
        // Copy backup to source
        overwriteColumnAt(fromIndex, destBackup.cellData, destBackup.stockValue,
                         destBackup.initialValue, destBackup.type);
        
        // Update UI
        setStatus(transition.statusMessage);
        markChanged();
        populateGrid();
    }
    
    /**
     * Validates if a column move is allowed
     */
    private boolean isValidMove(int fromIndex, int toIndex) {
        if (fromIndex == toIndex || toIndex < 0 || toIndex >= dataCols) {
            return false;
        }
        
        ColumnType type = columnTypes[fromIndex];
        if (type == ColumnType.EQUITY || type == ColumnType.A_L_E) {
            setStatus("Cannot move " + type.name() + " column - it must remain fixed");
            return false;
        }
        
        return true;
    }
    
    /**
     * Extracts all data for a column
     */
    private ColumnData extractColumn(int index) {
        ColumnData data = new ColumnData(dataRows);
        for (int r = 0; r < dataRows; r++) {
            data.cellData[r] = cellData[r][index];
        }
        data.stockValue = stockValues[index];
        data.initialValue = initialValues[index];
        data.type = columnTypes[index];
        return data;
    }
    
    /**
     * Overwrite a column at the specified index with new data
     */
    private void overwriteColumnAt(int colIndex, String[] colData, String stockValue, 
                                   double initial, ColumnType type) {
        // Overwrite cell data
        for (int r = 0; r < dataRows; r++) {
            cellData[r][colIndex] = colData[r];
        }
        
        // Overwrite metadata
        stockValues[colIndex] = stockValue;
        initialValues[colIndex] = initial;
        columnTypes[colIndex] = type;
    }
    
    /**
     * Set status message (helper method)
     */
    private void setStatus(String message) {
        statusLabel.setText(message);
    }
    
    /**
     * Find the index that separates Asset columns from Liability columns
     * Returns the index of the first Liability column
     */
    private int findAssetLiabilityBoundary() {
        for (int i = 0; i < dataCols; i++) {
            if (columnTypes[i] == ColumnType.LIABILITY) {
                return i;
            }
        }
        // If no Liability found, boundary is after all columns
        return dataCols;
    }
    

    
    private void updateButtonStates() {
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
        if (tableElement != null) {
            tableElement.resizeTable(dataRows, dataCols);
            
            // Apply cell equations
            for (int row = 0; row < dataRows; row++) {
                for (int col = 0; col < dataCols; col++) {
                    tableElement.setCellEquation(row, col, cellData[row][col]);
                }
            }
            
            // Apply stock values (column headers), initial values, and types
            for (int col = 0; col < dataCols; col++) {
                tableElement.setColumnHeader(col, stockValues[col]);
                tableElement.setInitialConditionValue(col, initialValues[col]);
                tableElement.setColumnType(col, columnTypes[col]);
            }
            
            // Update table display
            tableElement.setPoints();
        }
        
        hasChanges = false;
        updateButtonStates();
        statusLabel.setText("Changes applied successfully");
        
        // Refresh the simulation display
        if (sim != null) {
            sim.repaint();
        }
    }
    
    /**
     * Count columns of a specific type
     */
    private int countColumnsByType(ColumnType type) {
        int count = 0;
        for (ColumnType colType : columnTypes) {
            if (colType == type) {
                count++;
            }
        }
        return count;
    }
    
    // Public accessor methods for column types - can be used by TableElm
    public ColumnType getColumnType(int col) {
        if (col >= 0 && col < dataCols && columnTypes != null) {
            return columnTypes[col];
        }
        return ColumnType.ASSET; // Default
    }
    
    public void setColumnType(int col, ColumnType type) {
        if (col >= 0 && col < dataCols && columnTypes != null) {
            columnTypes[col] = type;
        }
    }
    
    public String getColumnTypeName(int col) {
        ColumnType type = getColumnType(col);
        return TableRenderer.getColumnTypeName(type);
    }
    
    // Check if a column can be moved, deleted, or if more can be added
    public boolean canMoveColumn(int col) {
        if (col < 0 || col >= dataCols || columnTypes == null) return false;
        return columnTypes[col] != ColumnType.EQUITY && columnTypes[col] != ColumnType.A_L_E;
    }
    
    public boolean canDeleteColumn(int col) {
        if (col < 0 || col >= dataCols || columnTypes == null) return false;
        
        ColumnType type = columnTypes[col];
        
        // Cannot delete Equity or Computed columns
        if (type == ColumnType.EQUITY || type == ColumnType.A_L_E) return false;
        
        // Cannot delete if it's the last Asset or Liability
        if (type == ColumnType.ASSET && countColumnsByType(ColumnType.ASSET) <= 1) return false;
        if (type == ColumnType.LIABILITY && countColumnsByType(ColumnType.LIABILITY) <= 1) return false;
        
        return true;
    }
    
    public boolean canAddColumnAfter(int col) {
        if (col < 0 || col >= dataCols || columnTypes == null) return false;
        
        // Cannot add after Equity or Computed columns
        return columnTypes[col] != ColumnType.EQUITY && columnTypes[col] != ColumnType.A_L_E;
    }

    
    @Override
    public void closeDialog() {
        super.closeDialog();
    }
}