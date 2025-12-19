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

import com.lushprojects.circuitjs1.client.TableColumn.ColumnType;
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
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.lushprojects.circuitjs1.client.util.Locale;
import java.util.HashMap;
import java.util.Map;



//  FIXED: Keyboard events now prevented from propagating to circuit editor
// there might be  issues with col3 being ALE


/**
 * TableEditDialog - Dynamic table editor for Stock-Flow tables
 * 
 * This dialog provides a structured interface for editing financial accounting tables
 * with three types of columns: Assets, Liabilities, and Equity, plus a computed A-L-E column.
 * 
 * Key Features:
 * - Visual row/column manipulation with contextual buttons
 * - Automatic synchronization with related tables sharing the same stock variables
 * - Real-time validation and change tracking
 * - Markdown debug view for inspecting table structure
 */
 public class TableEditDialog extends Dialog {
    
    //=== CONSTANTS AND CONFIGURATION ===============================================
    // =============================================================================
    
    // Fixed structure labels according to specification
    private static final String FLOWS_LABEL = "Flows↓/Stock Vars →";
    private static final String INITIAL_CONDITIONS_LABEL = "InitialConditions";
    
    // Unicode symbols for contextual buttons
    private static final String SYMBOL_ADD = "⧾";           // Add column/row button
    private static final String SYMBOL_DELETE = "⧿";       // Delete column/row button  
    private static final String SYMBOL_LEFT = "⇐";         // Move left button
    private static final String SYMBOL_RIGHT = "⇒";        // Move right button
    private static final String SYMBOL_UP = "⇑";           // Move up button
    private static final String SYMBOL_DOWN = "⇓";         // Move down button
    
    // Grid structure indices
    private static final int TABLE_NAME_ROW = 0;
    private static final int TYPE_ROW = 1;
    private static final int BUTTON_ROW = 2;
    private static final int STOCK_VALUES_ROW = 3;
    private static final int INITIAL_ROW = 4;
    private static final int DATA_START_ROW = 5;
    
    private static final int BUTTON_COL = 0;
    private static final int LABEL_COL = 1;
    private static final int DATA_START_COL = 2;
    
    //=== ENUMS AND NESTED CLASSES ================================================
    //=============================================================================
    
    /**
     * Check if a column is the A_L_E computed column
     * The last column is A_L_E when there are 4 or more columns
     */
    private boolean isALEColumn(int col) {
        // Check if this table actually shows A-L-E column
        if (!tableElement.showALE) {
            return false;
        }
        return col == dataCols - 1 && dataCols >= 4;
    }
    
    /**
     * Check if a column is a master column (this table computes the stock value)
     * Master columns are editable, non-master columns are locked (read-only)
     * 
     * @param col Column index to check
     * @return true if this table is the master for this stock, false otherwise
     */
    private boolean isMasterColumn(int col) {
        if (col < 0 || col >= dataCols) return false;
        if (isALEColumn(col)) return false; // A-L-E is computed, not a master column
        
        String stockName = stockValues[col];
        if (stockName == null || stockName.trim().isEmpty()) return false;
        
        return ComputedValues.isMasterTable(stockName.trim(), tableElement);
    }
    
    /**
     * Container for column data during move operations
     */
    private static class ColumnData {
        String[] cellData;
        String stockValue;  // Stock value is used as column header
        double initialValue;
        ColumnType type;
        String columnHeaderText;  // Table name or source table name (for CTM)
        
        ColumnData(int rows) {
            cellData = new String[rows];
        }
    }
    
    /**
     * State machine for column movement operations
     * Simplified: only restrict movement into the computed A-L-E column
     */
    private static class ColumnMoveStateMachine {
        
        /**
         * Represents a column movement transition result
         */
        static class MoveTransition {
            boolean isValid;
            String statusMessage;
            
            MoveTransition(boolean valid, String message) {
                this.isValid = valid;
                this.statusMessage = message;
            }
        }
        
        private final TableEditDialog dialog;
        
        ColumnMoveStateMachine(TableEditDialog dialog) {
            this.dialog = dialog;
        }
        
        /**
         * Calculate the movement transition - only prevent moving into computed column
         */
        MoveTransition calculateTransition(int fromIndex, int toIndex) {
            // Check if trying to move into the computed A-L-E column
            if (dialog.isALEColumn(toIndex)) {
                return new MoveTransition(false, "Cannot move column into computed A-L-E column position");
            }
            
            // Check if trying to move the computed column itself
            if (dialog.isALEColumn(fromIndex)) {
                return new MoveTransition(false, "Cannot move computed A-L-E column");
            }
            
            // All other moves are allowed
            ColumnType type = dialog.columnTypes[fromIndex];
            String typeName = (type != null) ? type.name() : "Column";
            return new MoveTransition(true, typeName + " column moved successfully");
        }
    }
    
    //=== INSTANCE VARIABLES ======================================================
    //=============================================================================
    
    // Core references
    private final TableElm tableElement;
    private final CirSim sim;
    
    // UI Components
    private ScrollPanel scrollPanel;
    private Grid editGrid;
    private Button applyButton;
    private Label statusLabel;
    private TextBox tableTitleBox;
    private Choice priorityChoice;
    
    // Data storage for dynamic content
    private String[][] cellData;  // Editable cell content
    private String[] stockValues; // Stock variable names - also used as column headers
    private double[] initialValues; // Initial condition values
    private int dataRows, dataCols; // Number of data rows/columns (excluding fixed structure)
    private ColumnType[] columnTypes; // Type for each column (Asset/Liability/Equity/Computed)
    private String[] columnHeaderTexts; // Header text per column (table name or source table name for CTM)
    
    // Button management
    private Map<String, Button> contextualButtons;
    
    // Track changes
    private boolean hasChanges = false;
    
    // Debug dialog for markdown view
    private TableMarkdownDebugDialog debugDialog = null;
    
    // Autocomplete state tracking (per-textbox)
    private java.util.Map<TextBox, AutocompleteHelper.AutocompleteState> autocompleteStates = 
        new java.util.HashMap<TextBox, AutocompleteHelper.AutocompleteState>();
    
    //=== CONSTRUCTOR ===============================================================
    // =============================================================================
    
    public TableEditDialog(TableElm tableElm, CirSim cirSim) {
        super();
        this.closeOnEnter = false; // Don't close dialog on Enter - just complete current edit
        this.tableElement = tableElm;
        this.sim = cirSim;
        
        // Initialize with data from TableElm or defaults for new table
        this.dataRows = Math.max(1, tableElm.getRows()); // At least 1 data row
        
        // For CTM, use actual column count (can be less than 4)
        // For regular tables, enforce minimum of 4 columns (A, L, E, A-L-E)
        boolean isCTM = tableElm instanceof CurrentTransactionsMatrixElm;
        if (isCTM) {
            this.dataCols = Math.max(1, tableElm.getCols()); // CTM: use actual count
        } else {
            this.dataCols = Math.max(4, tableElm.getCols()); // Regular: at least 4 columns
        }
        
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
        
        // Position dialog on the left side of the window
        show();
        setPopupPosition(20, 20);  // 20px from left edge, 20px from top
    }
    
    //=== NATIVE JAVASCRIPT METHODS FOR RESIZING ====================================
    // =============================================================================
    
    /**
     * Add CSS for resizable panels (inner content only, not dialog windows)
     */
    private native void addResizableStyles() /*-{
        // Add resize handle CSS only once
        if (!$doc.getElementById('resizable-panel-style')) {
            var style = $doc.createElement('style');
            style.id = 'resizable-panel-style';
            style.textContent = 
                '.resizable-panel {' +
                '  resize: both !important;' +
                '  overflow: auto !important;' +
                '  min-width: 300px !important;' +
                '  min-height: 200px !important;' +
                '}';
            $doc.head.appendChild(style);
        }
    }-*/;
    
    /**
     * Make a panel resizable (inner content areas only)
     */
    private native void makeResizable(com.google.gwt.dom.client.Element element) /*-{
        element.classList.add('resizable-panel');
    }-*/;
    
    //=== INITIALIZATION METHODS ====================================================
    // =============================================================================
    
    /**
     * Calculate A-L-E equation string for display in a specific row
     * NOTE: This is for DISPLAY ONLY in the dialog. The actual A_L_E calculation
     * happens in TableElm.doStep() using direct arithmetic, not expression parsing.
     * 
     * Returns equation string: sum of asset cells - sum of liability cells - equity cell
     * Example: "(asset1+asset2) - (liability1) - (equity1)"
     */
    private String calculateALECellEquation(int row) {
        // Use the static helper method with the current tableElement
        return calculateALECellEquation(tableElement, row);
    }
    
    /**
     * Wrap expression in parentheses if it contains operators
     * Used for display purposes when building A_L_E equation strings
     */
    private String wrapIfComplex(String expr) {
        if (expr.contains("+") || expr.contains("-") || 
            expr.contains("*") || expr.contains("/")) {
            return "(" + expr + ")";
        }
        return expr;
    }
    
    /**
     * Calculate A-L-E equation for a specific table and row (for markdown display)
     * Static helper method that works with any TableElm instance
     */
    private String calculateALECellEquation(TableElm table, int row) {
        StringBuilder eq = new StringBuilder();
        boolean hasAssets = false;
        
        // Skip the last column (A-L-E itself)
        int numCols = table.getCols() - 1;
        
        // Add asset terms (positive)
        for (int col = 0; col < numCols; col++) {
            if (table.getColumnType(col) == ColumnType.ASSET) {
                String cell = table.getCellEquation(row, col);
                if (cell != null && !cell.trim().isEmpty()) {
                    if (hasAssets) eq.append(" + ");
                    eq.append(wrapIfComplex(cell));
                    hasAssets = true;
                }
            }
        }
        
        // If no assets were found, start with "0"
        if (!hasAssets) {
            eq.append("0");
        }
        
        // Subtract liability terms
        for (int col = 0; col < numCols; col++) {
            if (table.getColumnType(col) == ColumnType.LIABILITY) {
                String cell = table.getCellEquation(row, col);
                if (cell != null && !cell.trim().isEmpty()) {
                    eq.append(" - ").append(wrapIfComplex(cell));
                }
            }
        }
        
        // Subtract equity term
        for (int col = 0; col < numCols; col++) {
            if (table.getColumnType(col) == ColumnType.EQUITY) {
                String cell = table.getCellEquation(row, col);
                if (cell != null && !cell.trim().isEmpty()) {
                    eq.append(" - ").append(wrapIfComplex(cell));
                }
            }
        }
        
        return eq.toString();
    }
    
    /**
     * Calculate A-L-E initial value for display
     * NOTE: This is for DISPLAY ONLY. The actual A_L_E calculation happens in TableElm.
     * Returns: sum(Assets) - sum(Liabilities) - Equity
     */
    private double calculateALEInitialValue() {
        // Convert parallel arrays to temporary list for calculation
        java.util.List<TableColumn> tempColumns = new java.util.ArrayList<TableColumn>();
        for (int col = 0; col < dataCols; col++) {
            tempColumns.add(new TableColumn("", columnTypes[col], initialValues[col], 0));
        }
        
        return TableColumn.calculateALE(tempColumns, new TableColumn.ValueExtractor() {
            public double getValue(TableColumn col) {
                return col.getInitialValue();
            }
        });
    }
    
    /**
     * Copy data from TableElm into local arrays for editing
     */
    private void copyTableData() {
        // Initialize data arrays
        cellData = new String[dataRows][dataCols];
        
        // Initialize stock values (used as column headers) with defaults from specification
        // Data columns start after "Flow Description" column
        stockValues = new String[dataCols];
        initialValues = new double[dataCols];
        columnTypes = new ColumnType[dataCols];
        columnHeaderTexts = new String[dataCols];
        
        // Set default stock values and types according to specification
        // Initial configuration: 1 Asset, 1 Liability, 1 Equity, and last column is A_L_E (if 4+ columns)
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
        
        // All remaining columns (including column 3) get default stock names and ASSET type
        for (int col = 3; col < dataCols; col++) {
            stockValues[col] = "Stock" + col;
            columnTypes[col] = ColumnType.ASSET;
        }
        
        // For tables with 4+ columns, the LAST column is always A_L_E
        if (dataCols >= 4) {
            stockValues[dataCols - 1] = "A-L-E";  // A_L_E column label
            // Note: Column type can remain ASSET, we detect A_L_E positionally
        }
        
        // Copy existing data from TableElm if available
        if (tableElement != null) {
            int existingRows = tableElement.getRows();
            int existingCols = tableElement.getCols();
            
            // FIRST: Copy column types from TableElm to override defaults
            for (int col = 0; col < Math.min(dataCols, existingCols); col++) {
                ColumnType existingType = tableElement.getColumnType(col);
                // Only override if we got a valid type (not null)
                if (existingType != null) {
                    columnTypes[col] = existingType;
                }
                // If null, keep the default type we set earlier (ASSET, LIABILITY, or EQUITY)
            }
            
            // THEN: Copy cell equations (skip A_L_E column - computed only)
            for (int row = 0; row < Math.min(dataRows, existingRows); row++) {
                for (int col = 0; col < Math.min(dataCols, existingCols); col++) {
                    if (!isALEColumn(col)) {
                        cellData[row][col] = tableElement.getCellEquation(row, col);
                        if (cellData[row][col] == null) {
                            cellData[row][col] = "";
                        }
                    }
                }
            }
            
            // Copy stock values (column headers) and initial values
            for (int col = 0; col < Math.min(dataCols, existingCols); col++) {
                // Only copy stock value if not A_L_E column
                if (!isALEColumn(col)) {
                    String existingHeader = tableElement.getColumnHeader(col);
                    if (existingHeader != null && !existingHeader.trim().isEmpty()) {
                        stockValues[col] = existingHeader;
                    }
                    initialValues[col] = tableElement.getInitialValue(col);
                } else {
                    stockValues[col] = "A-L-E"; // Label for A_L_E column
                    // Initial value will be computed
                }
            }
            
            // Initialize column header texts (source table name for CTM, table title for regular tables)
            boolean isCTM = tableElement instanceof CurrentTransactionsMatrixElm;
            if (isCTM) {
                CurrentTransactionsMatrixElm ctm = (CurrentTransactionsMatrixElm) tableElement;
                for (int col = 0; col < Math.min(dataCols, existingCols); col++) {
                    String sourceTableName = ctm.getSourceTableName(col);
                    columnHeaderTexts[col] = (sourceTableName != null && !sourceTableName.isEmpty()) 
                        ? sourceTableName : tableElement.getTableTitle();
                }
            } else {
                String tableTitle = tableElement.getTableTitle();
                for (int col = 0; col < dataCols; col++) {
                    columnHeaderTexts[col] = tableTitle;
                }
            }
        } else {
            // No existing table data - initialize with defaults
            String tableTitle = tableElement.getTableTitle();
            for (int col = 0; col < dataCols; col++) {
                columnHeaderTexts[col] = tableTitle;
            }
        }
        
        // Ensure all cells have non-null values (except A_L_E which will be computed)
        for (int row = 0; row < dataRows; row++) {
            for (int col = 0; col < dataCols; col++) {
                if (!isALEColumn(col) && cellData[row][col] == null) {
                    cellData[row][col] = "";
                }
            }
        }
        
        // A_L_E cells remain empty/blank in the dialog (computed values shown in circuit, not in editor)
        for (int row = 0; row < dataRows; row++) {
            for (int col = 0; col < dataCols; col++) {
                if (isALEColumn(col)) {
                    cellData[row][col] = ""; // Keep blank - no equation
                }
            }
        }
    }
    
    /**
     * Setup the main UI components
     */
    private void setupUI() {
        // Initialize resizable styles
        addResizableStyles();
        
        // UI Components
        VerticalPanel mainPanel = new VerticalPanel();
        mainPanel.setWidth("100%");
        setWidget(mainPanel);
        
        // Table title editor at the top
        HorizontalPanel titlePanel = new HorizontalPanel();
        // titlePanel.setSpacing(1);
        titlePanel.addStyleName("topSpace");
        Label titleLabel = new Label("Table Title:");
        titleLabel.setWidth("80px");
        titlePanel.add(titleLabel);
        
        tableTitleBox = new TextBox();
        tableTitleBox.setText(tableElement.getTableTitle());
        tableTitleBox.setWidth("300px");
        tableTitleBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                markChanged();
            }
        });
        titlePanel.add(tableTitleBox);
        
        // Add priority dropdown
        Label priorityLabel = new Label("Priority:");
        priorityLabel.setWidth("60px");
        priorityLabel.getElement().getStyle().setProperty("marginLeft", "20px");
        titlePanel.add(priorityLabel);
        
        priorityChoice = new Choice();
        for (int i = 1; i <= 9; i++) {
            priorityChoice.add(String.valueOf(i));
        }
        int currentPriority = tableElement.getPriority();
        // Clamp to 1-9 range
        if (currentPriority < 1) currentPriority = 1;
        if (currentPriority > 9) currentPriority = 9;
        priorityChoice.select(currentPriority - 1); // 0-indexed
        priorityChoice.setTitle("Priority for master table selection (higher = evaluated first)");
        priorityChoice.addChangeHandler(new com.google.gwt.event.dom.client.ChangeHandler() {
            public void onChange(com.google.gwt.event.dom.client.ChangeEvent event) {
                markChanged();
            }
        });
        titlePanel.add(priorityChoice);
        
        mainPanel.add(titlePanel);
        
        // Scrollable table area (main content)
        scrollPanel = new ScrollPanel();
        // Width will be set dynamically in populateGrid()
        scrollPanel.setHeight("250px");
        scrollPanel.addStyleName("topSpace");
        mainPanel.add(scrollPanel);
        
        // Make scroll panel resizable
        makeResizable(scrollPanel.getElement());
        
        // Testing panel (collapsible)
        addTestingPanel(mainPanel);
        
        // Bottom buttons
        HorizontalPanel buttonPanel = createBottomButtons();
        mainPanel.add(buttonPanel);
        
        // Status label at bottom
        statusLabel = new Label("Dynamic Table Editor - Use contextual buttons to modify structure");
        statusLabel.addStyleName("topSpace");
        mainPanel.add(statusLabel);
    }
    
    /**
     * Create the bottom button panel with action buttons
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
                // Save current data first
                applyChanges();
                
                // Hide this dialog
                closeDialog();
                
                // Open standard properties dialog directly
                sim.doEdit(tableElement);
            }
        });
        buttonPanel.add(propertiesButton);
        
        // Debug button to show markdown representation
        Button debugButton = new Button(Locale.LS("Debug"));
        debugButton.setTitle("Show markdown representation of tables");
        debugButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                showMarkdownDebug();
            }
        });
        buttonPanel.add(debugButton);
        
        // Add spacer to push Close to the right
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
     * Add testing panel for Stock Flow Synchronization test cases
     */
    private void addTestingPanel(VerticalPanel mainPanel) {
        final VerticalPanel testPanel = new VerticalPanel();
        testPanel.setWidth("100%");
        testPanel.addStyleName("topSpace");
        
        // Toggle button to show/hide tests
        final Button toggleButton = new Button("🧪 Show Sync Tests");
        toggleButton.addClickHandler(new ClickHandler() {
            private boolean expanded = false;
            public void onClick(ClickEvent event) {
                expanded = !expanded;
                testPanel.setVisible(expanded);
                toggleButton.setText(expanded ? "🧪 Hide Sync Tests" : "🧪 Show Sync Tests");
            }
        });
        mainPanel.add(toggleButton);
        
        testPanel.setVisible(false);
        
        Label testLabel = new Label("Stock Flow Synchronization Test Cases:");
        testLabel.getElement().getStyle().setProperty("fontWeight", "bold");
        testLabel.getElement().getStyle().setProperty("marginBottom", "5px");
        testPanel.add(testLabel);
        
        // Single row with all test buttons
        HorizontalPanel buttonRow = new HorizontalPanel();
        buttonRow.setSpacing(3);
        buttonRow.add(createTestButton("1: Sync", "Test initial synchronization", new ClickHandler() {
            public void onClick(ClickEvent event) {
                testCase1_Synchronization();
            }
        }));
        buttonRow.add(createTestButton("2: Del Row", "Test row deletion sync", new ClickHandler() {
            public void onClick(ClickEvent event) {
                testCase2_RowDeletion();
            }
        }));
        buttonRow.add(createTestButton("3: Mod Row", "Test row modification sync", new ClickHandler() {
            public void onClick(ClickEvent event) {
                testCase3_RowModification();
            }
        }));
        buttonRow.add(createTestButton("4: Rename", "Test stock renaming (⚠️)", new ClickHandler() {
            public void onClick(ClickEvent event) {
                testCase4_StockRenaming();
            }
        }));
        buttonRow.add(createTestButton("5: Del Stock", "Test stock deletion", new ClickHandler() {
            public void onClick(ClickEvent event) {
                testCase5_StockDeletion();
            }
        }));
        buttonRow.add(createTestButton("6: Add Stock", "Test stock addition", new ClickHandler() {
            public void onClick(ClickEvent event) {
                testCase6_StockAddition();
            }
        }));
        buttonRow.add(createTestButton("7: New Table", "Test table creation", new ClickHandler() {
            public void onClick(ClickEvent event) {
                testCase7_TableCreation();
            }
        }));
        buttonRow.add(createTestButton("8: Del Table", "Test table deletion", new ClickHandler() {
            public void onClick(ClickEvent event) {
                testCase8_TableDeletion();
            }
        }));
        buttonRow.add(createTestButton("9: Load", "Test table loading", new ClickHandler() {
            public void onClick(ClickEvent event) {
                testCase9_TableLoading();
            }
        }));
        buttonRow.add(createTestButton("10: Update", "Test table update", new ClickHandler() {
            public void onClick(ClickEvent event) {
                testCase10_TableUpdate();
            }
        }));
        buttonRow.add(createTestButton("11: Manual", "Test manual sync", new ClickHandler() {
            public void onClick(ClickEvent event) {
                testCase11_ManualSync();
            }
        }));
        buttonRow.add(createTestButton("📊 Info", "Show registry diagnostics", new ClickHandler() {
            public void onClick(ClickEvent event) {
                showDiagnostics();
            }
        }));
        testPanel.add(buttonRow);
        
        mainPanel.add(testPanel);
    }
    
    private Button createTestButton(String text, String title, ClickHandler handler) {
        Button btn = new Button(text);
        btn.setTitle(title);
        btn.addClickHandler(handler);
        btn.getElement().getStyle().setProperty("fontSize", "11px");
        btn.getElement().getStyle().setProperty("padding", "2px 5px");
        return btn;
    }
    
    //=== TEST CASE IMPLEMENTATIONS =================================================
    // =============================================================================
    
    private void testCase1_Synchronization() {
        applyChanges(); // Save current state first
        StockFlowRegistry.synchronizeRelatedTables(tableElement);
        setStatus("✅ Case 1: Synchronized with related tables. Check other tables sharing stocks: " + 
                 StockFlowRegistry.getSharedStocks());
        populateGrid(); // Refresh display
    }
    
    private void testCase2_RowDeletion() {
        if (dataRows <= 1) {
            setStatus("⚠️ Case 2: Cannot test - need at least 2 rows");
            return;
        }
        // Delete first data row as test
        deleteRow(0);
        setStatus("✅ Case 2: Deleted first row. Change synced to related tables.");
    }
    
    private void testCase3_RowModification() {
        if (dataRows < 1 || dataCols < 1) {
            setStatus("⚠️ Case 3: Cannot test - need at least 1 row and 1 column");
            return;
        }
        // Modify first cell as test
        String oldValue = cellData[0][0];
        String newValue = oldValue + "_MODIFIED";
        cellData[0][0] = newValue;
        tableElement.setCellEquation(0, 0, newValue);
        applyChanges();
        setStatus("✅ Case 3: Modified cell [0,0]: '" + oldValue + "' → '" + newValue + "'. Change synced.");
        populateGrid();
    }
    
    private void testCase4_StockRenaming() {
        if (dataCols < 1) {
            setStatus("⚠️ Case 4: Cannot test - need at least 1 column");
            return;
        }
        String oldName = stockValues[0];
        String newName = oldName + "_RENAMED";
        stockValues[0] = newName;
        tableElement.setColumnHeader(0, newName);
        applyChanges();
        setStatus("⚠️ Case 4: Renamed stock '" + oldName + "' → '" + newName + 
                 "'. NOTE: Sync is commented out in setColumnHeader(). Other tables NOT updated.");
        populateGrid();
    }
    
    private void testCase5_StockDeletion() {
        if (dataCols <= 1) {
            setStatus("⚠️ Case 5: Cannot test - need at least 2 columns");
            return;
        }
        String deletedStock = stockValues[0];
        deleteColumn(0);
        setStatus("✅ Case 5: Deleted stock '" + deletedStock + "'. Local only - other tables unaffected.");
    }
    
    private void testCase6_StockAddition() {
        insertColumnAfter(dataCols - 1);
        String newStock = stockValues[dataCols - 1];
        setStatus("✅ Case 6: Added new stock '" + newStock + "'. Will sync if this stock exists in other tables.");
    }
    
    private void testCase7_TableCreation() {
        setStatus("ℹ️ Case 7: To test table creation, use Circuit menu → 'Add Element' → 'Table'. " +
                 "New tables auto-register their stocks and sync with existing tables.");
    }
    
    private void testCase8_TableDeletion() {
        boolean confirm = com.google.gwt.user.client.Window.confirm(
            "Case 8: Delete this table element?\n\n" +
            "This will test table deletion cleanup. Other tables will remain intact.\n" +
            "This action cannot be undone from the test panel.");
        if (confirm) {
            closeDialog();
            tableElement.delete();
            setStatus("✅ Case 8: Table deleted. Other tables unaffected.");
        } else {
            setStatus("ℹ️ Case 8: Table deletion cancelled.");
        }
    }
    
    private void testCase9_TableLoading() {
        setStatus("ℹ️ Case 9: To test table loading, use File → 'Import' or 'Open'. " +
                 "Circuit load calls clearRegistry() then synchronizeAllTables() at end.");
    }
    
    private void testCase10_TableUpdate() {
        // Simulate a table update by modifying and syncing
        applyChanges();
        StockFlowRegistry.synchronizeRelatedTables(tableElement);
        setStatus("✅ Case 10: Applied changes and synced. Real updates happen on add/delete/move row operations.");
    }
    
    private void testCase11_ManualSync() {
        applyChanges();
        tableElement.synchronizeWithRelatedTables();
        setStatus("✅ Case 11: Manual synchronization complete via tableElement.synchronizeWithRelatedTables()");
        populateGrid();
    }
    
    private void showDiagnostics() {
        String info = StockFlowRegistry.getDiagnosticInfo();
        com.google.gwt.user.client.Window.alert(info);
        setStatus("📊 Registry diagnostics displayed (see alert)");
    }
    
    //=== MARKDOWN DEBUG VIEW =======================================================
    // =============================================================================
    
    /**
     * Show markdown representation of all tables sharing stocks with current table
     */
    private void showMarkdownDebug() {
        // If debug dialog already exists, just refresh and show it
        if (debugDialog != null) {
            debugDialog.refresh();
            if (!debugDialog.isShowing()) {
                debugDialog.show();
            }
        } else {
            // Create new dialog
            debugDialog = new TableMarkdownDebugDialog(tableElement);
            debugDialog.show();
        }
    }
    
    //=== GRID CREATION AND POPULATION ==============================================
    // =============================================================================
    
    /**
     * Create the edit grid with proper dimensions
     */
    private void createGrid() {
        // Calculate grid dimensions according to updated specification
        // Rows: Table name + Column types + Button controls + Stock values + Flows label + Initial conditions + data rows
        int totalGridRows = DATA_START_ROW + dataRows;
        // Cols: Buttons column + Label column + data columns  
        int totalGridCols = 2 + dataCols; // Button col + Label col + data columns
        
        editGrid = new Grid(totalGridRows, totalGridCols);
        editGrid.addStyleName("tableEditGrid");
        editGrid.setCellSpacing(1);
        editGrid.setCellPadding(2);
        
        // Clear contextual buttons for refresh
        contextualButtons.clear();
        
        populateTableNameRow();
        populateColumnTypeRow();
        populateFixedStructure();
        populateDataCells();
        populateContextualButtons();
    }
    
    /**
     * Refresh the grid display
     */
    private void populateGrid() {
        createGrid();
        scrollPanel.setWidget(editGrid);
        updateButtonStates();
        
        // Calculate dynamic width based on table content
        setDialogWidth();
        
        // Auto-update debug window when grid changes
        if (debugDialog != null && debugDialog.isShowing()) {
            debugDialog.refresh();
        }
    }
    
    /**
     * Calculate and set dialog width based on table columns, constrained by window width
     */
    private void setDialogWidth() {
        // Estimate column widths:
        // - Button column: ~60px
        // - Label column: ~150px
        // - Data columns: ~120px each
        // - Padding and borders: ~30px
        
        int buttonColWidth = 60;
        int labelColWidth = 150;
        int dataColWidth = 120;
        int padding = 30;
        
        int calculatedWidth = buttonColWidth + labelColWidth + (dataCols * dataColWidth) + padding;
        
        // Get window width and constrain to 90% of it
        int windowWidth = com.google.gwt.user.client.Window.getClientWidth();
        int maxWidth = (int)(windowWidth * 0.9);
        
        // Use minimum of 400px, maximum of window width
        int finalWidth = Math.max(400, Math.min(calculatedWidth, maxWidth));
        
        scrollPanel.setWidth(finalWidth + "px");
    }
    
    /**
     * Populate table name header row (row 0)
     */
    private void populateTableNameRow() {
        // First two columns empty
        editGrid.setText(TABLE_NAME_ROW, BUTTON_COL, "");
        editGrid.setText(TABLE_NAME_ROW, LABEL_COL, "");
        
        // Put column header text in each data column header (moves with column)
        for (int col = 0; col < dataCols; col++) {
            String columnHeaderText = columnHeaderTexts[col];
            if (columnHeaderText == null || columnHeaderText.isEmpty()) {
                columnHeaderText = tableElement.getTableTitle(); // Fallback
            }
            
            Label tableNameLabel = new Label(columnHeaderText);
            tableNameLabel.addStyleName("tableNameHeader");
            tableNameLabel.getElement().getStyle().setProperty("fontWeight", "bold");
            tableNameLabel.getElement().getStyle().setProperty("fontSize", "12px");
            tableNameLabel.getElement().getStyle().setProperty("padding", "4px");
            tableNameLabel.getElement().getStyle().setProperty("textAlign", "center");
            editGrid.setWidget(TABLE_NAME_ROW, DATA_START_COL + col, tableNameLabel);
        }
    }
    
    /**
     * Populate column type row with editable dropdowns (row 1)
     * Shows lock icon for non-master columns
     */
    private void populateColumnTypeRow() {
        editGrid.setText(TYPE_ROW, BUTTON_COL, "");
        editGrid.setText(TYPE_ROW, LABEL_COL, "Type:");
        
        for (int col = 0; col < dataCols; col++) {
            if (isALEColumn(col)) {
                // A_L_E column gets a fixed label
                boolean isCTM = tableElement instanceof CurrentTransactionsMatrixElm;
                String labelText = isCTM ? "🧮 Computed" : "🧮 A-L-E";
                String titleText = isCTM ? "Sum of all regular columns (computed)" : "Assets - Liabilities - Equity (computed)";
                
                Label aleLabel = new Label(labelText);
                aleLabel.addStyleName("computed-column");
                aleLabel.setTitle(titleText);
                editGrid.setWidget(TYPE_ROW, DATA_START_COL + col, aleLabel);
            } else if (!isMasterColumn(col)) {
                // Non-master column: show type with sync indicator (editable, syncs to master)
                String stockName = stockValues[col];
                TableElm masterTable = ComputedValues.getMasterTable(stockName != null ? stockName.trim() : "");
                String masterTableName = (masterTable != null) ? masterTable.getTableTitle() : "unknown";
                
                // Get the column type and add sync symbol
                ColumnType colType = columnTypes[col];
                String typeLabel;
                if (colType == ColumnType.ASSET) {
                    typeLabel = "🔄 💹 Asset";
                } else if (colType == ColumnType.LIABILITY) {
                    typeLabel = "🔄 📄 Liability";
                } else if (colType == ColumnType.EQUITY) {
                    typeLabel = "🔄 🏦 Equity";
                } else {
                    typeLabel = "🔄 Synced";
                }
                
                Label syncLabel = new Label(typeLabel);
                syncLabel.addStyleName("synced-column");
                syncLabel.setTitle("Synced: Stock '" + stockName + "' is mastered by table '" + masterTableName + "'. Edits update master and sync all tables.");
                editGrid.setWidget(TYPE_ROW, DATA_START_COL + col, syncLabel);
            } else {
                // Create dropdown for column type selection
                final int finalCol = col;
                final Choice typeChoice = new Choice();
                typeChoice.add("💹 Asset");
                typeChoice.add("📄 Liability");
                typeChoice.add("🏦 Equity");
                
                // Set current selection based on column type
                ColumnType colType = columnTypes[col];
                if (colType == ColumnType.ASSET) {
                    typeChoice.select(0);
                } else if (colType == ColumnType.LIABILITY) {
                    typeChoice.select(1);
                } else if (colType == ColumnType.EQUITY) {
                    typeChoice.select(2);
                }
                
                typeChoice.addChangeHandler(new com.google.gwt.event.dom.client.ChangeHandler() {
                    public void onChange(com.google.gwt.event.dom.client.ChangeEvent event) {
                        // Update column type based on selection
                        int selection = typeChoice.getSelectedIndex();
                        if (selection == 0) {
                            columnTypes[finalCol] = ColumnType.ASSET;
                        } else if (selection == 1) {
                            columnTypes[finalCol] = ColumnType.LIABILITY;
                        } else if (selection == 2) {
                            columnTypes[finalCol] = ColumnType.EQUITY;
                        }
                        markChanged();
                        // Recalculate A_L_E columns when type changes
                        updateALEColumns();
                    }
                });
                
                editGrid.setWidget(TYPE_ROW, DATA_START_COL + col, typeChoice);
            }
        }
    }
    
    /**
     * Populate fixed structure rows (buttons, stock values, initial values)
     */
    private void populateFixedStructure() {
        // Row 2: Control buttons (populated in populateContextualButtons)
        editGrid.setText(BUTTON_ROW, BUTTON_COL, "");
        editGrid.setText(BUTTON_ROW, LABEL_COL, "");
        
        // Row 3: Stock Values - editable row for output stock values
        editGrid.setText(STOCK_VALUES_ROW, BUTTON_COL, "");
        editGrid.setText(STOCK_VALUES_ROW, LABEL_COL, FLOWS_LABEL);
        
        // Add editable stock value inputs
        for (int col = 0; col < dataCols; col++) {
            if (isALEColumn(col)) {
                // A_L_E column gets a disabled label instead of textbox
                boolean isCTM = tableElement instanceof CurrentTransactionsMatrixElm;
                String labelText = isCTM ? "SUM" : "A-L-E";
                String titleText = isCTM ? "Sum of all regular columns (computed)" : "Assets - Liabilities - Equity (computed)";
                
                Label aleLabel = new Label(labelText);
                aleLabel.addStyleName("tableStockInput");
                aleLabel.addStyleName("computed-column");
                aleLabel.setTitle(titleText);
                editGrid.setWidget(STOCK_VALUES_ROW, DATA_START_COL + col, aleLabel);
            } else {
                VerticalPanel stockPanel = createStockValueTextBox(col);
                editGrid.setWidget(STOCK_VALUES_ROW, DATA_START_COL + col, stockPanel);
            }
        }
        
        
        // Row 4: Initial conditions
        editGrid.setText(INITIAL_ROW, BUTTON_COL, "");
        editGrid.setText(INITIAL_ROW, LABEL_COL, INITIAL_CONDITIONS_LABEL);
        
        // Add initial condition value inputs
        for (int col = 0; col < dataCols; col++) {
            if (isALEColumn(col)) {
                // A_L_E column gets computed initial value (read-only)
                double computedInitial = calculateALEInitialValue();
                boolean isCTM = tableElement instanceof CurrentTransactionsMatrixElm;
                String titleText = isCTM ? "Computed: Sum of all initial values" : "Computed: Assets - Liabilities - Equity";
                
                Label aleInitialLabel = new Label(Double.toString(computedInitial));
                aleInitialLabel.addStyleName("tableInitialInput");
                aleInitialLabel.addStyleName("computed-column");
                aleInitialLabel.setTitle(titleText);
                editGrid.setWidget(INITIAL_ROW, DATA_START_COL + col, aleInitialLabel);
            } else {
                TextBox initialBox = createInitialValueTextBox(col);
                editGrid.setWidget(INITIAL_ROW, DATA_START_COL + col, initialBox);
            }
        }
    }
    
    /**
     * Populate data cells with editable textboxes
     */
    private void populateDataCells() {
        // Add data row content
        for (int row = 0; row < dataRows; row++) {
            int gridRow = DATA_START_ROW + row;
            
            // Button column (populated in populateContextualButtons)
            editGrid.setText(gridRow, BUTTON_COL, "");
            
            // Label column - editable text for flow descriptions
            VerticalPanel flowDescPanel = createFlowDescriptionTextBox(row);
            editGrid.setWidget(gridRow, LABEL_COL, flowDescPanel);
            
            // Data columns
            for (int col = 0; col < dataCols; col++) {
                if (isALEColumn(col)) {
                    // A_L_E cells are computed and read-only - show calculated equation
                    String aleEquation = calculateALECellEquation(row);
                    boolean isCTM = tableElement instanceof CurrentTransactionsMatrixElm;
                    String titleText = isCTM ? "Computed: Sum of all regular columns" : "Computed: Assets - Liabilities - Equity";
                    
                    Label aleLabel = new Label(aleEquation);
                    aleLabel.addStyleName("tableCellInput");
                    aleLabel.addStyleName("computed-column");
                    aleLabel.setTitle(titleText);
                    editGrid.setWidget(gridRow, DATA_START_COL + col, aleLabel);
                } else {
                    VerticalPanel cellPanel = createCellTextBox(row, col);
                    editGrid.setWidget(gridRow, DATA_START_COL + col, cellPanel);
                }
            }
        }
    }
    
    /**
     * Add contextual control buttons for row/column manipulation
     */
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
                String colTypeName = (colType != null) ? colType.name() : "ASSET";
                Button addColBtn = createButton(SYMBOL_ADD, "Add " + colTypeName + " column after " + stockValues[col]);
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
            
            // Movement buttons - allow free movement except into computed column
            if (canMoveColumn(col)) {
                // Check if we can move left
                if (canMoveLeftWithinType(col)) {
                    Button moveLeftBtn = createButton(SYMBOL_LEFT, "Move column left");
                    moveLeftBtn.addClickHandler(new ClickHandler() {
                        public void onClick(ClickEvent event) {
                            moveColumn(finalCol, finalCol - 1);
                        }
                    });
                    buttonPanel.add(moveLeftBtn);
                }
                
                // Check if we can move right
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
    
    //=== TEXTBOX CREATION METHODS ==================================================
    // =============================================================================
    
    //=== DUPLICATE VALIDATION =====================================================
    //=============================================================================
    
    /**
     * Functional interface for getting a name at a given index
     */
    private interface NameProvider {
        String getName(int index);
    }
    
    /**
     * Generic duplicate finder - checks if a name exists at another index
     * @param name The name to check
     * @param excludeIndex The index to exclude from checking
     * @param count Total number of items to check
     * @param nameProvider Function to get name at each index
     * @param skipPredicate Optional predicate to skip certain indices (e.g., A-L-E column)
     * @return The index where duplicate exists, or -1 if no duplicate
     */
    private int findDuplicate(String name, int excludeIndex, int count, 
                              NameProvider nameProvider, int skipIndex) {
        if (name == null || name.trim().isEmpty()) {
            return -1;
        }
        String trimmedName = name.trim();
        for (int i = 0; i < count; i++) {
            if (i == excludeIndex || i == skipIndex) continue;
            String existing = nameProvider.getName(i);
            if (existing != null && existing.trim().equalsIgnoreCase(trimmedName)) {
                return i;
            }
        }
        return -1;
    }
    
    /** Check for duplicate flow description in another row */
    private int findDuplicateFlowDescription(String name, int excludeRow) {
        return findDuplicate(name, excludeRow, dataRows, 
            new NameProvider() { public String getName(int i) { return tableElement.getRowDescription(i); } },
            -1);
    }
    
    /** Check for duplicate stock name in another column (skips A-L-E column) */
    private int findDuplicateStockName(String name, int excludeCol) {
        return findDuplicate(name, excludeCol, dataCols,
            new NameProvider() { public String getName(int i) { return isALEColumn(i) ? null : stockValues[i]; } },
            -1);
    }
    
    /**
     * Rename a flow description in all tables that have the same description at any row.
     * This propagates the rename across all TableElm instances in the circuit.
     */
    private void renameFlowDescriptionInAllTables(String oldDesc, String newDesc) {
        if (oldDesc == null || oldDesc.equals(newDesc)) return;
        if (sim == null || sim.elmList == null) return;
        
        int count = 0;
        
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.elmList.elementAt(i);
            if (ce instanceof TableElm && ce != tableElement) {
                TableElm otherTable = (TableElm) ce;
                int rows = otherTable.getRows();
                for (int r = 0; r < rows; r++) {
                    String desc = otherTable.getRowDescription(r);
                    if (oldDesc.equals(desc)) {
                        otherTable.setRowDescription(r, newDesc);
                        count++;
                    }
                }
            }
        }
        
        if (count > 0) {
            setStatus("✓ Renamed '" + oldDesc + "' → '" + newDesc + "' in " + count + " other table(s)");
        }
    }
    
    /**
     * Apply duplicate validation styling to a textbox
     * @param textBox The textbox to style
     * @param duplicateIndex The index of the duplicate (-1 if none)
     * @param name The name being checked
     * @param itemType "row" or "column" for status message
     */
    private void applyDuplicateStyle(TextBox textBox, int duplicateIndex, String name, String itemType) {
        if (duplicateIndex >= 0 && name != null && !name.trim().isEmpty()) {
            textBox.addStyleName("error-input");
            String msg = "Duplicate: '" + name.trim() + "' already exists in " + itemType + " " + (duplicateIndex + 1);
            textBox.setTitle(msg);
            setStatus("⚠️ " + msg);
        } else {
            textBox.removeStyleName("error-input");
            textBox.setTitle("");
        }
    }
    
    //=== TEXTBOX CREATION HELPERS =================================================
    //=============================================================================
    
    /**
     * Create a container panel with hint label for autocomplete textboxes
     */
    private VerticalPanel createAutocompleteContainer(TextBox textBox, Label hintLabel) {
        VerticalPanel container = new VerticalPanel();
        container.setWidth("100%");
        container.add(hintLabel);
        container.add(textBox);
        return container;
    }
    
    /**
     * Setup common keyboard handlers for textboxes with autocomplete
     * @param textBox The textbox to configure
     * @param completionList List of completion options
     * @param hintLabel Label for showing hints
     * @param state Autocomplete state
     * @param useSimpleCompletion true for whole-text matching, false for word-level
     * @param onEnter Optional action to run on Enter key (after blur)
     */
    private void setupAutocompleteHandlers(final TextBox textBox, 
                                           final java.util.List<String> completionList,
                                           final Label hintLabel,
                                           final AutocompleteHelper.AutocompleteState state,
                                           final boolean useSimpleCompletion,
                                           final Runnable onEnter) {
        // Tab key: cycle through completions, Enter key: finish editing
        textBox.addKeyDownHandler(new KeyDownHandler() {
            public void onKeyDown(KeyDownEvent event) {
                if (event.getNativeKeyCode() == KeyCodes.KEY_TAB) {
                    event.preventDefault();
                    event.stopPropagation();
                    if (useSimpleCompletion) {
                        AutocompleteHelper.handleSimpleTabCompletion(textBox, completionList, hintLabel, state);
                    } else {
                        AutocompleteHelper.handleTabCompletion(textBox, completionList, hintLabel, state);
                    }
                } else if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                    event.preventDefault();
                    state.reset();
                    hintLabel.setVisible(false);
                    textBox.setFocus(false);
                    if (onEnter != null) {
                        onEnter.run();
                    }
                }
            }
        });
        
        // Show matches in real-time while typing
        textBox.addKeyPressHandler(new KeyPressHandler() {
            public void onKeyPress(KeyPressEvent event) {
                com.google.gwt.core.client.Scheduler.get().scheduleDeferred(
                    new com.google.gwt.core.client.Scheduler.ScheduledCommand() {
                        public void execute() {
                            if (useSimpleCompletion) {
                                AutocompleteHelper.updateSimpleMatchDisplay(textBox, completionList, hintLabel, state);
                            } else {
                                AutocompleteHelper.updateMatchDisplay(textBox, completionList, hintLabel, state);
                            }
                        }
                    }
                );
            }
        });
    }
    
    /** Add select-all-on-focus behavior to a textbox */
    private void addSelectAllOnFocus(final TextBox textBox) {
        textBox.addFocusHandler(new FocusHandler() {
            public void onFocus(FocusEvent event) {
                textBox.selectAll();
            }
        });
    }
    
    //=== TEXTBOX CREATION METHODS ==================================================
    // =============================================================================
    
    /**
     * Create textbox for editing flow descriptions with bash-style Tab autocomplete
     */
    private VerticalPanel createFlowDescriptionTextBox(final int row) {
        final java.util.List<String> completionList = createFlowDescriptionCompletionList();
        
        // Create and configure textbox
        final TextBox textBox = new TextBox();
        String rowDesc = tableElement.getRowDescription(row);
        textBox.setText(rowDesc != null ? rowDesc : "");
        textBox.addStyleName("tableFlowInput");
        textBox.setWidth("100%");
        
        // Track original value for rename propagation
        final String[] originalDesc = new String[] { rowDesc };
        
        // Check for initial duplicate
        int initialDuplicate = findDuplicateFlowDescription(rowDesc, row);
        if (initialDuplicate >= 0 && rowDesc != null) {
            applyDuplicateStyle(textBox, initialDuplicate, rowDesc, "row");
        }
        
        preventKeyboardPropagation(textBox);
        
        // Setup autocomplete
        final Label hintLabel = AutocompleteHelper.createHintLabel();
        final AutocompleteHelper.AutocompleteState state = new AutocompleteHelper.AutocompleteState();
        autocompleteStates.put(textBox, state);
        
        setupAutocompleteHandlers(textBox, completionList, hintLabel, state, true, null);
        
        // Track changes with duplicate validation
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                String newDesc = textBox.getText();
                applyDuplicateStyle(textBox, findDuplicateFlowDescription(newDesc, row), newDesc, "row");
                tableElement.setRowDescription(row, newDesc);
                textBox.addStyleName("modified");
                markChanged();
            }
        });
        
        // Propagate rename to all tables on blur
        textBox.addBlurHandler(new BlurHandler() {
            public void onBlur(BlurEvent event) {
                String newDesc = textBox.getText();
                if (originalDesc[0] != null && !originalDesc[0].equals(newDesc)) {
                    renameFlowDescriptionInAllTables(originalDesc[0], newDesc);
                    originalDesc[0] = newDesc; // Update for next edit
                }
            }
        });
        
        // Update original on focus (in case value was changed externally)
        textBox.addFocusHandler(new FocusHandler() {
            public void onFocus(FocusEvent event) {
                originalDesc[0] = textBox.getText();
            }
        });
        
        return createAutocompleteContainer(textBox, hintLabel);
    }
    
    private Button createButton(String symbol, String tooltip) {
        Button button = new Button(symbol);
        button.addStyleName("contextualButton");
        button.setTitle(tooltip);
        return button;
    }
    
    /**
     * Create TextBox for editing stock values (column headers) with bash-style Tab autocomplete
     */
    private VerticalPanel createStockValueTextBox(final int col) {
        final java.util.List<String> completionList = createStockNameCompletionList();
        
        // Create and configure textbox
        final TextBox textBox = new TextBox();
        textBox.setText(stockValues[col]);
        textBox.addStyleName("tableStockInput");
        textBox.setWidth("100%");
        
        // Check for initial duplicate
        int initialDuplicate = findDuplicateStockName(stockValues[col], col);
        if (initialDuplicate >= 0) {
            applyDuplicateStyle(textBox, initialDuplicate, stockValues[col], "column");
        }
        
        preventKeyboardPropagation(textBox);
        
        // Setup autocomplete
        final Label hintLabel = AutocompleteHelper.createHintLabel();
        final AutocompleteHelper.AutocompleteState state = new AutocompleteHelper.AutocompleteState();
        autocompleteStates.put(textBox, state);
        
        setupAutocompleteHandlers(textBox, completionList, hintLabel, state, false, null);
        
        // Track changes with duplicate validation
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                String newName = textBox.getText();
                int duplicateCol = findDuplicateStockName(newName, col);
                applyDuplicateStyle(textBox, duplicateCol, newName, "column");
                
                if (duplicateCol < 0) {
                    stockValues[col] = newName;
                    textBox.addStyleName("modified");
                    markChanged();
                }
            }
        });
        
        addSelectAllOnFocus(textBox);
        return createAutocompleteContainer(textBox, hintLabel);
    }

    //=== COMPLETION LIST BUILDERS =================================================
    //=============================================================================
    
    /** Helper to add unique non-empty items to a list */
    private void addUnique(java.util.List<String> list, String item) {
        if (item != null && !item.trim().isEmpty() && !list.contains(item.trim())) {
            list.add(item.trim());
        }
    }
    
    /** Helper to add all items from a set to a list (unique) */
    private void addAllUnique(java.util.List<String> list, java.util.Set<String> items) {
        if (items != null) {
            for (String item : items) {
                addUnique(list, item);
            }
        }
    }
    
    /** Create completion list for flow descriptions (unique row names from all tables) */
    private java.util.List<String> createFlowDescriptionCompletionList() {
        java.util.List<String> list = new java.util.ArrayList<String>();
        
        if (sim != null && sim.elmList != null) {
            for (int i = 0; i < sim.elmList.size(); i++) {
                CircuitElm elm = sim.elmList.elementAt(i);
                if (elm instanceof TableElm) {
                    TableElm table = (TableElm) elm;
                    for (int r = 0; r < table.getRows(); r++) {
                        String flowDesc = table.getRowDescription(r);
                        // Skip default "Row1", "Row2", etc.
                        if (flowDesc != null && !flowDesc.startsWith("Row")) {
                            addUnique(list, flowDesc);
                        }
                    }
                }
            }
        }
        
        java.util.Collections.sort(list);
        return list;
    }
    
    /** Create completion list for stock names */
    private java.util.List<String> createStockNameCompletionList() {
        java.util.List<String> list = new java.util.ArrayList<String>();
        
        // Add registered stock names
        addAllUnique(list, StockFlowRegistry.getAllStockNames());
        
        // Add stock names from all tables
        if (sim != null && sim.elmList != null) {
            for (int i = 0; i < sim.elmList.size(); i++) {
                CircuitElm elm = sim.elmList.elementAt(i);
                if (elm instanceof TableElm) {
                    TableElm table = (TableElm) elm;
                    for (int c = 0; c < table.getCols(); c++) {
                        String header = table.getColumnHeader(c);
                        if (header != null && !header.equals("A-L-E")) {
                            addUnique(list, header);
                        }
                    }
                }
            }
        }
        
        java.util.Collections.sort(list);
        return list;
    }
    
    /**
     * Create textbox for editing initial condition values
     */
    private TextBox createInitialValueTextBox(final int col) {
        final TextBox textBox = new TextBox();
        textBox.setText(Double.toString(initialValues[col]));
        textBox.addStyleName("tableInitialInput");
        
        preventKeyboardPropagation(textBox);
        
        // Enter key finishes editing
        textBox.addKeyDownHandler(new KeyDownHandler() {
            public void onKeyDown(KeyDownEvent event) {
                if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                    event.preventDefault();
                    textBox.setFocus(false);
                }
            }
        });
        
        // Parse and validate numeric input
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                try {
                    initialValues[col] = Double.parseDouble(textBox.getText());
                    textBox.removeStyleName("error");
                    updateALEColumns();
                } catch (NumberFormatException e) {
                    textBox.addStyleName("error");
                }
                textBox.addStyleName("modified");
                markChanged();
            }
        });
        
        addSelectAllOnFocus(textBox);
        return textBox;
    }
    
    /**
     * Create textbox for editing cell equations with autocomplete
     * Non-master columns sync changes to the master table
     */
    private VerticalPanel createCellTextBox(final int row, final int col) {
        final boolean isMaster = isMasterColumn(col);
        final String stockName = stockValues[col];
        final TableElm masterTable = isMaster ? null : 
            ComputedValues.getMasterTable(stockName != null ? stockName.trim() : "");
        final java.util.List<String> completionList = createCompletionList();
        
        // Create and configure textbox
        final TextBox textBox = new TextBox();
        textBox.setText(cellData[row][col]);
        textBox.addStyleName("tableCellInput");
        textBox.setWidth("100%");
        
        // Style non-master columns to indicate they sync to master
        if (!isMaster && masterTable != null) {
            textBox.addStyleName("synced-column");
            textBox.setTitle("Synced: Changes update master table '" + masterTable.getTableTitle() + "' and all related tables");
        }
        
        preventKeyboardPropagation(textBox);
        
        // Setup autocomplete
        final Label hintLabel = AutocompleteHelper.createHintLabel();
        final AutocompleteHelper.AutocompleteState state = new AutocompleteHelper.AutocompleteState();
        autocompleteStates.put(textBox, state);
        
        // Sync action for Enter/Blur
        final Runnable syncAction = new Runnable() {
            public void run() {
                if (!isMaster && masterTable != null) {
                    updateMasterAndSync(masterTable, stockName, row, col, textBox.getText());
                } else {
                    syncMasterToRelatedTables();
                }
            }
        };
        
        setupAutocompleteHandlers(textBox, completionList, hintLabel, state, false, syncAction);
        
        // Track changes
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                cellData[row][col] = textBox.getText();
                textBox.addStyleName("modified");
                markChanged();
                updateALEColumns();
            }
        });
        
        addSelectAllOnFocus(textBox);
        
        // Sync on blur
        textBox.addBlurHandler(new BlurHandler() {
            public void onBlur(BlurEvent event) {
                syncAction.run();
            }
        });
        
        // Validate on creation
        com.google.gwt.core.client.Scheduler.get().scheduleDeferred(
            new com.google.gwt.core.client.Scheduler.ScheduledCommand() {
                public void execute() {
                    AutocompleteHelper.validateOnOpen(textBox, completionList, hintLabel);
                }
            }
        );
        
        return createAutocompleteContainer(textBox, hintLabel);
    }
    
    //=== AUTOCOMPLETE HELPER METHODS ==============================================
    //=============================================================================
    
    /** Math functions and constants available in expressions */
    private static final String[] BUILTIN_FUNCTIONS = {
        "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
        "exp", "log", "log10", "sqrt", "abs", "floor", "ceil",
        "min", "max", "pi", "e", "t"
    };
    
    /** Create completion list from all available variables and functions */
    private java.util.List<String> createCompletionList() {
        java.util.List<String> list = new java.util.ArrayList<String>();
        
        // Add labeled node names
        if (LabeledNodeElm.labelList != null) {
            for (String labelName : LabeledNodeElm.labelList.keySet()) {
                addUnique(list, labelName);
            }
        }
        
        // Add registered stock and cell equation variables
        addAllUnique(list, StockFlowRegistry.getAllStockNames());
        addAllUnique(list, StockFlowRegistry.getAllCellEquationVariables());
        
        // Add current table's stock values (excluding A-L-E)
        if (stockValues != null) {
            for (int col = 0; col < stockValues.length; col++) {
                if (!isALEColumn(col)) {
                    addUnique(list, stockValues[col]);
                }
            }
        }
        
        // Add math functions and constants
        for (String fn : BUILTIN_FUNCTIONS) {
            addUnique(list, fn);
        }
        
        return list;
    }
    
    /**
     * Update all A_L_E column cells with recalculated equations
     */
    private void updateALEColumns() {
        for (int row = 0; row < dataRows; row++) {
            for (int col = 0; col < dataCols; col++) {
                if (isALEColumn(col)) {
                    // Calculate updated A-L-E equation based on current cellData
                    String aleEquation = calculateALEEquationFromCellData(row);
                    
                    // Update the label widget in the grid with the new equation
                    com.google.gwt.user.client.ui.Widget widget = editGrid.getWidget(DATA_START_ROW + row, DATA_START_COL + col);
                    if (widget instanceof Label) {
                        ((Label) widget).setText(aleEquation);
                    }
                }
            }
        }
        
        // Also update initial value
        for (int col = 0; col < dataCols; col++) {
            if (isALEColumn(col)) {
                double newInitialValue = calculateALEInitialValue();
                com.google.gwt.user.client.ui.Widget widget = editGrid.getWidget(INITIAL_ROW, DATA_START_COL + col);
                if (widget instanceof Label) {
                    ((Label) widget).setText(Double.toString(newInitialValue));
                }
            }
        }
    }
    
    /**
     * Calculate A-L-E equation from current cellData (used during editing)
     * This reads from the cellData array which is updated as the user types
     */
    private String calculateALEEquationFromCellData(int row) {
        StringBuilder eq = new StringBuilder();
        boolean first = true;
        
        // Add asset terms (positive)
        for (int col = 0; col < dataCols; col++) {
            if (columnTypes[col] == ColumnType.ASSET) {
                String cell = cellData[row][col];
                if (cell != null && !cell.trim().isEmpty()) {
                    if (!first) eq.append(" + ");
                    eq.append(wrapIfComplex(cell));
                    first = false;
                }
            }
        }
        
        // Subtract liability terms
        for (int col = 0; col < dataCols; col++) {
            if (columnTypes[col] == ColumnType.LIABILITY) {
                String cell = cellData[row][col];
                if (cell != null && !cell.trim().isEmpty()) {
                    eq.append(" - ").append(wrapIfComplex(cell));
                }
            }
        }
        
        // Subtract equity term
        for (int col = 0; col < dataCols; col++) {
            if (columnTypes[col] == ColumnType.EQUITY) {
                String cell = cellData[row][col];
                if (cell != null && !cell.trim().isEmpty()) {
                    eq.append(" - ").append(wrapIfComplex(cell));
                }
            }
        }
        
        return eq.length() > 0 ? eq.toString() : "0";
    }
    
    //=== COLUMN MOVEMENT HELPERS ===================================================
    // =============================================================================
    
    /**
     * Check if we can move a column left
     */
    private boolean canMoveLeftWithinType(int col) {
        if (col <= 0) return false;
        if (!canMoveColumn(col)) return false;
        if (isALEColumn(col)) return false; // Can't move computed column
        
        // Can move left unless we're moving into computed column position
        return !isALEColumn(col - 1);
    }
    
    /**
     * Check if we can move a column right
     */
    private boolean canMoveRightWithinType(int col) {
        if (col >= dataCols - 1) return false;
        if (!canMoveColumn(col)) return false;
        if (isALEColumn(col)) return false; // Can't move computed column
        
        // Can move right unless we're moving into computed column position
        return !isALEColumn(col + 1);
    }
    
    //=== ROW AND COLUMN MANIPULATION METHODS =======================================
    // =============================================================================
    
    /**
     * Insert a new row after the specified index
     */
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
        
        // IMMEDIATE UPDATE: Rebuild row descriptions with new row inserted
        // This must be done BEFORE resizeTable() to ensure proper copying
        String[] newRowDescriptions = new String[dataRows];
        
        // Copy row descriptions up to the insertion point
        for (int r = 0; r <= rowIndex; r++) {
            String desc = tableElement.getRowDescription(r);
            newRowDescriptions[r] = desc != null ? desc : "";
        }
        
        // Initialize new row description
        newRowDescriptions[rowIndex + 1] = "";
        
        // Copy remaining row descriptions
        for (int r = rowIndex + 1; r < dataRows - 1; r++) {
            String desc = tableElement.getRowDescription(r);
            newRowDescriptions[r + 1] = desc != null ? desc : "";
        }
        
        // Now resize table and apply changes
        tableElement.resizeTable(dataRows, dataCols);
        
        // Apply row descriptions to resized table
        for (int row = 0; row < dataRows; row++) {
            tableElement.setRowDescription(row, newRowDescriptions[row]);
        }
        
        // Apply all cell equations to TableElm
        for (int row = 0; row < dataRows; row++) {
            for (int col = 0; col < dataCols; col++) {
                tableElement.setCellEquation(row, col, cellData[row][col]);
            }
        }
        
        // NOTE: Synchronization deferred until Apply/OK to avoid syncing incomplete edits
        // StockFlowRegistry.synchronizeRelatedTables(tableElement);
        
        setStatus("Row added after row " + (rowIndex + 1) + ". Total rows: " + dataRows);
        markChanged();
        populateGrid();
        
        // Refresh simulation display
        if (sim != null) {
            sim.repaint();
        }
    }
    
    // Delete a row at the specified index
    private void deleteRow(int rowIndex) {
        if (dataRows <= 1) {
            setStatus("Cannot delete the last row - at least one row is required");
            return;
        }
        // Capture flow description and any non-zero flow/stock pairs in this row
        String flowDescToDelete = tableElement.getRowDescription(rowIndex);
        if (flowDescToDelete != null) flowDescToDelete = flowDescToDelete.trim();
        java.util.Set<String> nonZeroStocks = new java.util.HashSet<String>();
        for (int c = 0; c < dataCols; c++) {
            String eq = cellData[rowIndex][c];
            if (eq != null && !eq.trim().isEmpty() && !eq.trim().equals("0")) {
                String stockName = stockValues[c];
                if (stockName != null && !stockName.trim().isEmpty()) {
                    nonZeroStocks.add(stockName);
                }
            }
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
        
        // IMMEDIATE UPDATE: Rebuild row descriptions excluding deleted row
        // This must be done BEFORE resizeTable() to ensure proper copying
        String[] newRowDescriptions = new String[dataRows];
        int newRowDesc = 0;
        for (int r = 0; r < dataRows + 1; r++) {
            if (r != rowIndex) {
                String desc = tableElement.getRowDescription(r);
                newRowDescriptions[newRowDesc] = desc != null ? desc : "";
                newRowDesc++;
            }
        }
        
        // Now resize table and apply changes
        tableElement.resizeTable(dataRows, dataCols);
        
        // Apply row descriptions to resized table
        for (int row = 0; row < dataRows; row++) {
            tableElement.setRowDescription(row, newRowDescriptions[row]);
        }
        
        // Apply all cell equations to TableElm
        for (int row = 0; row < dataRows; row++) {
            for (int col = 0; col < dataCols; col++) {
                tableElement.setCellEquation(row, col, cellData[row][col]);
            }
        }
        
        // NOTE: Synchronization deferred until Apply/OK to avoid syncing incomplete edits
        // StockFlowRegistry.synchronizeRelatedTables(tableElement);
        setStatus("Row " + (rowIndex + 1) + " deleted. Total rows: " + dataRows);
        markChanged();
        populateGrid();
        // Cascade delete: remove same flow rows in other tables for any non-zero flow/stock pairs
        if (flowDescToDelete != null && !flowDescToDelete.isEmpty() && !nonZeroStocks.isEmpty()) {
            int deleted = StockFlowRegistry.deleteMatchingFlowRows(tableElement, flowDescToDelete, nonZeroStocks);
            if (deleted > 0) {
                setStatus(statusLabel.getText() + " | Removed " + deleted + " matching row(s) from other tables");
            }
        }
        // Refresh simulation display
        if (sim != null) {
            sim.repaint();
        }
    }
    
    /**
     * Move a row from one index to another (swap)
     */
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
        
        // IMMEDIATE UPDATE: Apply equation changes to TableElm right away
        for (int col = 0; col < dataCols; col++) {
            tableElement.setCellEquation(fromIndex, col, cellData[fromIndex][col]);
            tableElement.setCellEquation(toIndex, col, cellData[toIndex][col]);
        }
        
        // NOTE: Synchronization deferred until Apply/OK to avoid syncing incomplete edits
        // StockFlowRegistry.synchronizeRelatedTables(tableElement);
        
        String direction = (fromIndex < toIndex) ? "down" : "up";
        setStatus("Row " + (fromIndex + 1) + " moved " + direction + " to position " + (toIndex + 1));
        markChanged();
        populateGrid();
        
        // Refresh simulation display
        if (sim != null) {
            sim.repaint();
        }
    }
    
    /**
     * Insert a new column after the specified index
     */
    private void insertColumnAfter(int colIndex) {
        dataCols++;
        
        // Expand arrays
        String[][] newCellData = new String[dataRows][dataCols];
        String[] newStockValues = new String[dataCols];
        double[] newInitialValues = new double[dataCols];
        ColumnType[] newColumnTypes = new ColumnType[dataCols];
        String[] newColumnHeaderTexts = new String[dataCols];
        
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
        
        // Copy stock values, initial values, types, and header texts
        for (int c = 0; c <= colIndex; c++) {
            newStockValues[c] = stockValues[c];
            newInitialValues[c] = initialValues[c];
            newColumnTypes[c] = columnTypes[c];
            newColumnHeaderTexts[c] = columnHeaderTexts[c];
        }
        newStockValues[colIndex + 1] = "H" + (colIndex + 1); // Assign next H number
        newInitialValues[colIndex + 1] = 0.0;
        newColumnTypes[colIndex + 1] = newColumnType;
        newColumnHeaderTexts[colIndex + 1] = columnHeaderTexts[colIndex]; // Inherit header text from previous column
        for (int c = colIndex + 1; c < dataCols - 1; c++) {
            newStockValues[c + 1] = stockValues[c];
            newInitialValues[c + 1] = initialValues[c];
            newColumnTypes[c + 1] = columnTypes[c];
            newColumnHeaderTexts[c + 1] = columnHeaderTexts[c];
        }
        
        cellData = newCellData;
        stockValues = newStockValues;
        initialValues = newInitialValues;
        columnTypes = newColumnTypes;
        columnHeaderTexts = newColumnHeaderTexts;
        
        String colTypeName = (newColumnType != null) ? newColumnType.name() : "ASSET";
        setStatus("New " + colTypeName + " column added after " + stockValues[colIndex] + ". Total columns: " + dataCols);
        markChanged();
        populateGrid();
    }
    
    /**
     * Delete a column at the specified index
     */
    private void deleteColumn(int colIndex) {
        if (dataCols <= 1) {
            setStatus("Cannot delete the last column - at least one column is required");
            return;
        }
        
        // // Prevent deleting Equity column
        // if (columnTypes[colIndex] == ColumnType.EQUITY) {
        //     setStatus("Cannot delete Equity column - it is required");
        //     return;
        // }
        
        // Prevent deleting Computed column (A_L_E)
        if (isALEColumn(colIndex)) {
            setStatus("Cannot delete Computed (A-L-E) column - it is required");
            return;
        }
        
        // // Prevent deleting if it's the last Asset or last Liability
        // if (columnTypes[colIndex] == ColumnType.ASSET && countColumnsByType(ColumnType.ASSET) <= 1) {
        //     setStatus("Cannot delete the last ASSET column - at least one is required");
        //     return;
        // }
        
        // if (columnTypes[colIndex] == ColumnType.LIABILITY && countColumnsByType(ColumnType.LIABILITY) <= 1) {
        //     setStatus("Cannot delete the last LIABILITY column - at least one is required");
        //     return;
        // }
        
        String deletedColumnName = stockValues[colIndex];
        ColumnType deletedType = columnTypes[colIndex];
        
        dataCols--;
        
        // Shrink arrays
        String[][] newCellData = new String[dataRows][dataCols];
        String[] newStockValues = new String[dataCols];
        double[] newInitialValues = new double[dataCols];
        ColumnType[] newColumnTypes = new ColumnType[dataCols];
        String[] newColumnHeaderTexts = new String[dataCols];
        
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
        
        // Copy stock values, initial values, types, and header texts excluding deleted column
        int newCol = 0;
        for (int c = 0; c < dataCols + 1; c++) {
            if (c != colIndex) {
                newStockValues[newCol] = stockValues[c];
                newInitialValues[newCol] = initialValues[c];
                newColumnTypes[newCol] = columnTypes[c];
                newColumnHeaderTexts[newCol] = columnHeaderTexts[c];
                newCol++;
            }
        }
        
        cellData = newCellData;
        stockValues = newStockValues;
        initialValues = newInitialValues;
        columnTypes = newColumnTypes;
        columnHeaderTexts = newColumnHeaderTexts;
        
        String deletedTypeName = (deletedType != null) ? deletedType.name() : "ASSET";
        setStatus(deletedTypeName + " column '" + deletedColumnName + "' deleted. Total columns: " + dataCols);
        markChanged();
        populateGrid();
    }
    
    //=== COLUMN MOVEMENT LOGIC =====================================================
    // =============================================================================
    
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
                         sourceData.initialValue, sourceData.type, sourceData.columnHeaderText);
        
        // Copy backup to source
        overwriteColumnAt(fromIndex, destBackup.cellData, destBackup.stockValue,
                         destBackup.initialValue, destBackup.type, destBackup.columnHeaderText);
        
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
        
        // Only prevent moving the computed A-L-E column
        if (isALEColumn(fromIndex)) {
            setStatus("Cannot move A-L-E column - it is computed");
            return false;
        }
        
        // Prevent moving into the A-L-E column position
        if (isALEColumn(toIndex)) {
            setStatus("Cannot move column into A-L-E position");
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
        data.columnHeaderText = columnHeaderTexts[index];
        return data;
    }
    
    /**
     * Overwrite a column at the specified index with new data
     */
    private void overwriteColumnAt(int colIndex, String[] colData, String stockValue, 
                                   double initial, ColumnType type, String headerText) {
        // Overwrite cell data
        for (int r = 0; r < dataRows; r++) {
            cellData[r][colIndex] = colData[r];
        }
        
        // Overwrite metadata
        stockValues[colIndex] = stockValue;
        initialValues[colIndex] = initial;
        columnTypes[colIndex] = type;
        columnHeaderTexts[colIndex] = headerText;
    }
    
    /**
     * Set status message (helper method)
     */
    private void setStatus(String message) {
        statusLabel.setText(message);
    }
    
    //=== CHANGE MANAGEMENT =========================================================
    // =============================================================================
    
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
    
    /**
     * Enable/disable buttons based on change state
     */
    private void updateButtonStates() {
        applyButton.setEnabled(hasChanges);
    }
    
    /**
     * Mark that the dialog has unsaved changes
     */
    private void markChanged() {
        if (!hasChanges) {
            hasChanges = true;
            updateButtonStates();
            statusLabel.setText("Table modified - use Apply or OK to save changes");
        }
    }
    
    /**
     * Update the master table with changes from a non-master column and sync all tables.
     * When editing a non-master column, this updates the master table's data first,
     * then synchronizes all related tables (including this one).
     * 
     * @param masterTable The master table that owns this stock
     * @param stockName The stock name being edited
     * @param row The row index being edited
     * @param col The column index in this table
     * @param newValue The new cell value
     */
    private void updateMasterAndSync(TableElm masterTable, String stockName, int row, int col, String newValue) {
        if (masterTable == null || stockName == null) {
            return;
        }
        
        // Find the column in the master table that has this stock name
        int masterCol = -1;
        for (int c = 0; c < masterTable.getCols(); c++) {
            String masterStock = masterTable.getColumnHeader(c);
            if (masterStock != null && masterStock.trim().equalsIgnoreCase(stockName.trim())) {
                masterCol = c;
                break;
            }
        }
        
        if (masterCol < 0) {
            setStatus("⚠️ Could not find stock '" + stockName + "' in master table '" + masterTable.getTableTitle() + "'");
            return;
        }
        
        // Update the master table's cell
        masterTable.setCellEquation(row, masterCol, newValue);
        
        // Update our local data too
        cellData[row][col] = newValue;
        
        // Sync from master to all related tables
        StockFlowRegistry.synchronizeRelatedTables(masterTable);
        
        setStatus("✓ Updated master table '" + masterTable.getTableTitle() + "' and synced all related tables");
    }
    
    /**
     * Sync changes from master table to all related tables sharing the same stocks.
     * Called automatically when editing finishes (Enter key or blur).
     * Only syncs if this table is actually the master for at least one stock.
     */
    private void syncMasterToRelatedTables() {
        // First apply changes to the underlying TableElm
        applyChangesToTableElm();
        
        // Then sync to related tables
        StockFlowRegistry.synchronizeRelatedTables(tableElement);
    }
    
    /**
     * Apply current dialog data to the underlying TableElm without closing
     */
    private void applyChangesToTableElm() {
        // Copy cell data to TableElm
        for (int row = 0; row < dataRows; row++) {
            for (int col = 0; col < dataCols; col++) {
                if (!isALEColumn(col)) {
                    tableElement.setCellEquation(row, col, cellData[row][col]);
                }
            }
        }
        
        // Copy stock values (column headers)
        for (int col = 0; col < dataCols; col++) {
            if (!isALEColumn(col)) {
                tableElement.setColumnHeader(col, stockValues[col]);
            }
        }
        
        // Copy initial values
        for (int col = 0; col < dataCols; col++) {
            if (!isALEColumn(col)) {
                tableElement.setInitialConditionValue(col, initialValues[col]);
            }
        }
        
        // Copy column types
        for (int col = 0; col < dataCols; col++) {
            if (!isALEColumn(col)) {
                tableElement.setColumnType(col, columnTypes[col]);
            }
        }
    }
    
    /**
     * Recalculate master table assignments and refresh the grid
     * This ensures lock status is updated after priority or stock name changes
     */
    private void recalculateMastersAndRefresh() {
        // Clear existing master registrations
        ComputedValues.clearMasterTables();
        ComputedValues.clearComputedValues();
        
        // Re-register all tables in priority order
        if (sim != null) {
            sim.registerTableMastersInPriorityOrder();
        }
        
        // Refresh the grid to update lock status
        populateGrid();
    }
    
    /**
     * Apply all pending changes to the TableElm and synchronize with related tables
     */
    private void applyChanges() {
        if (!hasChanges) return;
        
        // Apply data changes with new size
        if (tableElement != null) {
            // Apply table title
            String newTitle = tableTitleBox.getText().trim();
            if (!newTitle.isEmpty()) {
                tableElement.setTableTitle(newTitle);
            }
            
            // Apply priority from dropdown
            int oldPriority = tableElement.getPriority();
            int newPriority = priorityChoice.getSelectedIndex() + 1; // Convert from 0-indexed to 1-9
            tableElement.setPriority(newPriority);
            boolean priorityChanged = (oldPriority != newPriority);
            
            // Track if any stock names changed (affects master calculation)
            boolean stockNamesChanged = false;
            for (int col = 0; col < dataCols; col++) {
                String oldStockName = tableElement.getColumnHeader(col);
                String newStockName = stockValues[col];
                if (!isALEColumn(col) && oldStockName != null && newStockName != null &&
                    !oldStockName.trim().equals(newStockName.trim())) {
                    stockNamesChanged = true;
                    break;
                }
            }
            
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
            
            // Mark table as manually edited (custom mode) if it's a CurrentTransactionsMatrixElm
            if (tableElement instanceof CurrentTransactionsMatrixElm) {
                tableElement.initMode = 2; // Set to custom mode when columns are manually edited
                CurrentTransactionsMatrixElm ctm = (CurrentTransactionsMatrixElm) tableElement;
                ctm.updateCustomStockNamesFromColumns(); // Update custom names from edited columns
            }
            
            // Trigger synchronization with other tables sharing the same stocks
            StockFlowRegistry.synchronizeRelatedTables(tableElement);
            
            // Update table display
            tableElement.setPoints();
            
            // If priority or stock names changed, recalculate masters immediately
            // This updates lock status in the dialog before needAnalyze() is called
            if (priorityChanged || stockNamesChanged) {
                recalculateMastersAndRefresh();
                statusLabel.setText("Changes applied - master assignments recalculated");
            }
            
            // Force full circuit analysis (important when priority changes)
            sim.needAnalyze();
        }
        
        hasChanges = false;
        updateButtonStates();
        if (statusLabel.getText().equals("Table modified - use Apply or OK to save changes")) {
            statusLabel.setText("Changes applied and tables synchronized");
        }
        
        // Update debug window after applying changes
        if (debugDialog != null && debugDialog.isShowing()) {
            debugDialog.refresh();
        }
        
        // Refresh the simulation display
        if (sim != null) {
            sim.repaint();
        }
    }
    
    //=== PUBLIC ACCESSOR AND PERMISSION METHODS ====================================
    // =============================================================================
    
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
    
    // Public accessor methods for column types - used by TableElm
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
    
    // Column operation permission checks
    public boolean canMoveColumn(int col) {
        if (col < 0 || col >= dataCols || columnTypes == null) return false;
        return !isALEColumn(col);
    }
    
    public boolean canDeleteColumn(int col) {
        if (col < 0 || col >= dataCols || columnTypes == null) return false;
        
        // Cannot delete Computed (A_L_E) column
        if (isALEColumn(col)) return false;
        
        // All regular columns (Asset, Liability, Equity) can be deleted freely
        return true;
    }
    
    public boolean canAddColumnAfter(int col) {
        if (col < 0 || col >= dataCols || columnTypes == null) return false;
        
        // Cannot add after Computed (A_L_E) column
        return !isALEColumn(col);
    }

    
    @Override
    public void closeDialog() {
        super.closeDialog();
    }
}