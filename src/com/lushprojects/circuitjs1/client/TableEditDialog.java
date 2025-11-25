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
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.FocusEvent;
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
    
    // Column type enumeration for financial accounting
    public enum ColumnType {
        ASSET,
        LIABILITY,
        EQUITY
        // Note: Last column (when cols >= 4) is implicitly A_L_E computed column
    }
    
    /**
     * Check if a column is the A_L_E computed column
     * The last column is A_L_E when there are 4 or more columns
     */
    private boolean isALEColumn(int col) {
        return col == dataCols - 1 && dataCols >= 4;
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
        double assets = 0.0;
        double liabilities = 0.0;
        double equity = 0.0;
        
        for (int col = 0; col < dataCols; col++) {
            if (columnTypes[col] == ColumnType.ASSET) {
                assets += initialValues[col];
            } else if (columnTypes[col] == ColumnType.LIABILITY) {
                liabilities += initialValues[col];
            } else if (columnTypes[col] == ColumnType.EQUITY) {
                equity += initialValues[col];
            }
        }
        
        return assets - liabilities - equity;
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
                // This prevents issues with A_L_E columns which don't have enum type
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
     */
    private void populateColumnTypeRow() {
        editGrid.setText(TYPE_ROW, BUTTON_COL, "");
        editGrid.setText(TYPE_ROW, LABEL_COL, "Type:");
        
        for (int col = 0; col < dataCols; col++) {
            if (isALEColumn(col)) {
                // A_L_E column gets a fixed label
                Label aleLabel = new Label("🧮 A-L-E");
                aleLabel.addStyleName("computed-column");
                aleLabel.setTitle("Assets - Liabilities - Equity (computed)");
                editGrid.setWidget(TYPE_ROW, DATA_START_COL + col, aleLabel);
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
                Label aleLabel = new Label("A-L-E"); // Label for A-L-E column
                aleLabel.addStyleName("tableStockInput");
                aleLabel.addStyleName("computed-column");
                aleLabel.setTitle("Assets - Liabilities - Equity (computed)");
                editGrid.setWidget(STOCK_VALUES_ROW, DATA_START_COL + col, aleLabel);
            } else {
                TextBox stockBox = createStockValueTextBox(col);
                editGrid.setWidget(STOCK_VALUES_ROW, DATA_START_COL + col, stockBox);
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
                Label aleInitialLabel = new Label(Double.toString(computedInitial));
                aleInitialLabel.addStyleName("tableInitialInput");
                aleInitialLabel.addStyleName("computed-column");
                aleInitialLabel.setTitle("Computed: Assets - Liabilities - Equity");
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
            TextBox flowDescBox = createFlowDescriptionTextBox(row);
            editGrid.setWidget(gridRow, LABEL_COL, flowDescBox);
            
            // Data columns
            for (int col = 0; col < dataCols; col++) {
                if (isALEColumn(col)) {
                    // A_L_E cells are computed and read-only - show calculated equation
                    String aleEquation = calculateALECellEquation(row);
                    Label aleLabel = new Label(aleEquation);
                    aleLabel.addStyleName("tableCellInput");
                    aleLabel.addStyleName("computed-column");
                    aleLabel.setTitle("Computed: Assets - Liabilities - Equity");
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
    
    /**
     * Create textbox for editing flow descriptions
     */
    private TextBox createFlowDescriptionTextBox(final int row) {
        final TextBox textBox = new TextBox();
        // Initialize with row description from TableElm
        String rowDesc = tableElement.getRowDescription(row);
        textBox.setText(rowDesc != null ? rowDesc : "");
        textBox.addStyleName("tableFlowInput");
        
        // Prevent keyboard events from deleting table element while typing
        preventKeyboardPropagation(textBox);
        
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
    
    /**
     * Create textbox for editing stock values (column headers)
     */
    private TextBox createStockValueTextBox(final int col) {
        final TextBox textBox = new TextBox();
        textBox.setText(stockValues[col]); // Initialize with H0, H1, H2, etc.
        textBox.addStyleName("tableStockInput");
        
        // Prevent keyboard events from deleting table element while typing
        preventKeyboardPropagation(textBox);
        
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
    
    /**
     * Create textbox for editing initial condition values
     */
    private TextBox createInitialValueTextBox(final int col) {
        final TextBox textBox = new TextBox();
        textBox.setText(Double.toString(initialValues[col]));
        textBox.addStyleName("tableInitialInput");
        
        // Prevent keyboard events from deleting table element while typing
        preventKeyboardPropagation(textBox);
        
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                try {
                    double value = Double.parseDouble(textBox.getText());
                    initialValues[col] = value;
                    textBox.removeStyleName("error");
                    
                    // Recalculate A_L_E initial value when any initial value changes
                    updateALEColumns();
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
    
    /**
     * Create textbox for editing cell equations with autocomplete (bash-style Tab completion)
     * Returns a VerticalPanel containing hint label + textbox
     */
    private VerticalPanel createCellTextBox(final int row, final int col) {
        // Create the completion list from all available variables
        final java.util.List<String> completionList = createCompletionList();
        
        // Create the textbox
        final TextBox textBox = new TextBox();
        textBox.setText(cellData[row][col]);
        textBox.addStyleName("tableCellInput");
        textBox.setWidth("100%");
        
        // Prevent keyboard events from deleting table element while typing
        preventKeyboardPropagation(textBox);
        
        // Setup autocomplete panel with hint label
        VerticalPanel container = new VerticalPanel();
        container.setWidth("100%");
        
        // Create hint label (initially hidden, appears when typing)
        final Label hintLabel = AutocompleteHelper.createHintLabel();
        
        // Assemble: hint label above textbox
        container.add(hintLabel);
        container.add(textBox);
        
        // Initialize autocomplete state for this textbox
        final AutocompleteHelper.AutocompleteState state = new AutocompleteHelper.AutocompleteState();
        autocompleteStates.put(textBox, state);
        
        // Handle Tab key: cycle through completions
        textBox.addKeyDownHandler(new KeyDownHandler() {
            public void onKeyDown(KeyDownEvent event) {
                if (event.getNativeKeyCode() == KeyCodes.KEY_TAB) {
                    event.preventDefault();
                    event.stopPropagation();
                    AutocompleteHelper.handleTabCompletion(textBox, completionList, hintLabel, state);
                }
            }
        });
        
        // Handle typing: show matches in real-time and validate symbols
        textBox.addKeyPressHandler(new KeyPressHandler() {
            public void onKeyPress(KeyPressEvent event) {
                // Wait for character to be added to textbox, then update display
                com.google.gwt.core.client.Scheduler.get().scheduleDeferred(
                    new com.google.gwt.core.client.Scheduler.ScheduledCommand() {
                        public void execute() {
                            AutocompleteHelper.updateMatchDisplay(textBox, completionList, hintLabel, state);
                        }
                    }
                );
            }
        });
        
        // Add key up handler to track changes
        textBox.addKeyUpHandler(new KeyUpHandler() {
            public void onKeyUp(KeyUpEvent event) {
                cellData[row][col] = textBox.getText();
                textBox.addStyleName("modified");
                markChanged();
                
                // Recalculate A_L_E columns when any cell changes
                updateALEColumns();
            }
        });
        
        // Select all on focus
        textBox.addFocusHandler(new FocusHandler() {
            public void onFocus(FocusEvent event) {
                textBox.selectAll();
            }
        });
        
        // Validate immediately on creation - show only undefined symbols
        com.google.gwt.core.client.Scheduler.get().scheduleDeferred(
            new com.google.gwt.core.client.Scheduler.ScheduledCommand() {
                public void execute() {
                    AutocompleteHelper.validateOnOpen(textBox, completionList, hintLabel);
                }
            }
        );
        
        return container;
    }
    
    //=== AUTOCOMPLETE HELPER METHODS ==============================================
    //=============================================================================
    
    /**
     * Create completion list from all available variables
     */
    private java.util.List<String> createCompletionList() {
        java.util.List<String> list = new java.util.ArrayList<String>();
        
        // Add labeled node names
        if (LabeledNodeElm.labelList != null && !LabeledNodeElm.labelList.isEmpty()) {
            for (String labelName : LabeledNodeElm.labelList.keySet()) {
                list.add(labelName);
            }
        }
        
        // Add stock variables (column headers from this and other tables)
        java.util.Set<String> stockNames = StockFlowRegistry.getAllStockNames();
        if (stockNames != null && !stockNames.isEmpty()) {
            for (String stockName : stockNames) {
                if (!list.contains(stockName)) {
                    list.add(stockName);
                }
            }
        }
        
        // Add variables used in cell equations
        java.util.Set<String> cellVariables = StockFlowRegistry.getAllCellEquationVariables();
        if (cellVariables != null && !cellVariables.isEmpty()) {
            for (String varName : cellVariables) {
                if (!list.contains(varName)) {
                    list.add(varName);
                }
            }
        }
        
        // Add current table's stock values (excluding A-L-E computed column)
        if (stockValues != null) {
            for (int col = 0; col < stockValues.length; col++) {
                // Skip A-L-E column - it's computed, not a real stock
                if (isALEColumn(col)) {
                    continue;
                }
                String stock = stockValues[col];
                if (stock != null && !stock.trim().isEmpty() && !list.contains(stock)) {
                    list.add(stock);
                }
            }
        }
        
        // Add math functions and constants
        String[] builtIns = {"sin", "cos", "tan", "asin", "acos", "atan", "atan2",
                            "exp", "log", "log10", "sqrt", "abs", "floor", "ceil",
                            "min", "max", "pi", "e", "t"};
        for (String fn : builtIns) {
            if (!list.contains(fn)) {
                list.add(fn);
            }
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
        
        // Prevent deleting Equity column
        if (columnTypes[colIndex] == ColumnType.EQUITY) {
            setStatus("Cannot delete Equity column - it is required");
            return;
        }
        
        // Prevent deleting Computed column (A_L_E)
        if (isALEColumn(colIndex)) {
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
            // If priority changed, clear master tables and computed values to force re-registration
            // and avoid stale computed values causing convergence issues.
            if (oldPriority != newPriority) {
                ComputedValues.clearMasterTables();
                ComputedValues.clearComputedValues();
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
            
            // Trigger synchronization with other tables sharing the same stocks
            StockFlowRegistry.synchronizeRelatedTables(tableElement);
            
            // Update table display
            tableElement.setPoints();
            
            // Force full circuit analysis (important when priority changes)
            sim.needAnalyze();
        }
        
        hasChanges = false;
        updateButtonStates();
        statusLabel.setText("Changes applied and tables synchronized");
        
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
        
        ColumnType type = columnTypes[col];
        
        // Cannot delete Computed (A_L_E) column
        if (isALEColumn(col)) return false;
        
        // Cannot delete if it's the last Asset or Liability (need at least one of each)
        if (type == ColumnType.ASSET && countColumnsByType(ColumnType.ASSET) <= 1) return false;
        if (type == ColumnType.LIABILITY && countColumnsByType(ColumnType.LIABILITY) <= 1) return false;
        
        // Equity columns can be deleted freely (no minimum requirement)
        
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