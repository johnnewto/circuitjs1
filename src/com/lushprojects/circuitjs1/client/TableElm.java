/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.Button;
import com.lushprojects.circuitjs1.client.TableEditDialog.ColumnType;

/**
 * Simplified Table Element - Displays voltage values from equations
 * Extends ChipElm to provide output pins at each column
 */
public class TableElm extends ChipElm {
    protected int rows = 0;  // Start with zero rows for new tables
    protected int cols = 3;
    protected int cellWidthInGrids = 6;  // Width of each cell in grid units (cspc)
    protected int cellHeight = 16; // Height of each cell in pixels (for drawing)
    protected int cellSpacing = 0;  // Spacing between cells in pixels (for drawing), zero for best appearance
    protected double[] initialValues; // Values for initial conditions row
    protected boolean showInitialValues = false; // Control visibility of initial conditions row
    protected String[] outputNames; // Names for connecting outputs to labeled nodes (also used as column headers)
    protected String[] rowDescriptions; // Descriptions/labels for each row (left column in spreadsheet view)
    protected String tableTitle = "Table"; // Title for the table (displayed in edit dialog and component)
    protected String tableUnits = ""; // Units to display in table cells ("" , $ or V)
    protected int decimalPlaces = 2; // Number of decimal places to show
    protected ColumnType[] columnTypes; // Type for each column (Asset/Liability/Equity/Computed)
    
    // All cells now use equations
    // Variables available: a-i map to labeled nodes OR use labeled node names directly
    // Example equations: "a+b", "node1+node2", "sin(t)", "max(a,b,c)", "vcc>2.5?5:0"
    String[][] cellEquations;     // Equation strings for each cell
    Expr[][] compiledExpressions; // Compiled expressions for evaluation
    ExprState[][] expressionStates; // Expression evaluation state for each cell
    
    // Note: No need to track labeled nodes anymore with direct resolution
    
    // Constructor for new table FROM MENU - receives auto-increment flag
    public TableElm(int xx, int yy) {
        this(xx, yy, true); // Always auto-increment for menu-created tables
    }

    // Internal constructor with control over auto-increment
    private TableElm(int xx, int yy, boolean autoIncrement) {
        super(xx, yy);
        noDiagonal = true;
        
        if (autoIncrement) {
            tableTitle = "Table " + nextTableNumber;
            nextTableNumber++;
        } else {
            tableTitle = "Table"; // Default for programmatic creation
        }
        
        initTable();
        setupPins();
        setSize(sim.smallGridCheckItem.getState() ? 1 : 2);
    }

    // File loading constructor - NEVER auto-increments
    public TableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        noDiagonal = true;
        parseTableData(st);
        
        // Update counter based on loaded table title
        updateTableCounter(tableTitle);
        
        initTable();
        setupPins();
        setSize((f & FLAG_SMALL) != 0 ? 1 : 2);
    }
    
    private void initTable() {
        // Initialize equation arrays
        if (cellEquations == null) {
            cellEquations = new String[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    // Default equation is just the node name (like "node1", "vcc", etc.)
                    cellEquations[row][col] = "";
                }
            }
//            CirSim.console("TableElm: Initialized cellEquations array");
        }
        
        if (compiledExpressions == null) {
            compiledExpressions = new Expr[rows][cols];
//            CirSim.console("TableElm: Initialized compiledExpressions array");
        }
        
        if (expressionStates == null) {
            expressionStates = new ExprState[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    expressionStates[row][col] = new ExprState(1); // Only need time variable
                }
            }
        }
        
        // Initialize output names if not set
        if (outputNames == null) {
            outputNames = new String[cols];
            for (int i = 0; i < cols; i++) {
                outputNames[i] = "Stock_" + (i + 1);
            }
        }
        
        // Initialize row descriptions if not set
        if (rowDescriptions == null) {
            rowDescriptions = new String[rows];
            for (int i = 0; i < rows; i++) {
                rowDescriptions[i] = "Flow_" + (i + 1);
            }
        }
        
        // Initialize initial conditions values if not set
        if (initialValues == null) {
            initialValues = new double[cols];
            // Initialize with zero values
            for (int i = 0; i < cols; i++) {
                initialValues[i] = 0.0;
            }
        }
        
        // Initialize column types if not set
        if (columnTypes == null) {
            columnTypes = new ColumnType[cols];
            // Default: first column Asset, second Liability, third Equity, rest Assets
            for (int i = 0; i < cols; i++) {
                if (i == 0) {
                    columnTypes[i] = ColumnType.ASSET;
                } else if (i == 1) {
                    columnTypes[i] = ColumnType.LIABILITY;
                } else if (i == 2) {
                    columnTypes[i] = ColumnType.EQUITY;
                } else if (i == 3) {
                    columnTypes[i] = ColumnType.A_L_E;
                } else {
                    columnTypes[i] = ColumnType.ASSET;
                }
            }
        }
        
        // Ensure no null or empty values exist
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (cellEquations[row][col] == null || cellEquations[row][col].trim().isEmpty()) {
                    cellEquations[row][col] = "";
                }
            }
        }
        
        for (int i = 0; i < cols; i++) {
            if (outputNames[i] == null || outputNames[i].trim().isEmpty()) {
                outputNames[i] = "Stock" + (i + 1);
            }
        }
        
        // Compile all equations
        recompileAllEquations();
    }
    
    private boolean isValidCell(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }
    
    // Enhanced version that evaluates equations for cell values
    protected double getVoltageForCell(int row, int col) {
        if (!isValidCell(row, col)) {
            return 0.0;
        }
        
        // All cells now use equations
        if (compiledExpressions[row][col] != null) {
            // Evaluate the compiled expression
            ExprState state = expressionStates[row][col];
            updateExpressionState(state);
            return compiledExpressions[row][col].eval(state);

        }
        return 0.0;
    }
    

        
    // Recompile all equations when labeled nodes change
    private void recompileAllEquations() {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (cellEquations[row][col] != null && !cellEquations[row][col].trim().isEmpty()) {
                    compileEquation(row, col, cellEquations[row][col]);
                }
            }
        }
    }
    
    private void updateExpressionState(ExprState state) {
        // Only update time - direct node resolution handles everything else
        state.t = sim != null ? sim.t : 0.0;
        
        // All node references are resolved directly in Expr.eval() via E_NODE_REF
    }
    
    private void compileEquation(int row, int col, String equation) {
        if (equation == null || equation.trim().isEmpty()) {
            compiledExpressions[row][col] = null;
            return;
        }
        
        try {
            ExprParser parser = new ExprParser(equation);
            compiledExpressions[row][col] = parser.parseExpression();
            String err = parser.gotError();
            if (err != null) {
                // Provide helpful error message with available variables
                String availableVars = getAvailableVariablesString();
                CirSim.console("TableElm: Parse error in equation [" + row + "][" + col + "]: " + equation + ": " + err);
                CirSim.console("TableElm: " + availableVars);
                compiledExpressions[row][col] = null;
                return;
            }
        } catch (Exception e) {
            String availableVars = getAvailableVariablesString();
            CirSim.console("TableElm: Exception parsing equation [" + row + "][" + col + "]: " + e.getMessage());
            CirSim.console("TableElm: " + availableVars);
            compiledExpressions[row][col] = null;
        }
    }
    
    // Helper method to show which variables are available for equations
    // Update helper method documentation
    private String getAvailableVariablesString() {
        StringBuilder sb = new StringBuilder();
        String[] availableNodes = LabeledNodeElm.getSortedLabeledNodeNames();
        
        sb.append("Available: t (time)");
        
        // Show direct node names (only method now)
        for (String nodeName : availableNodes) {
            sb.append(", ").append(nodeName);
        }
        
        if (availableNodes.length == 0) {
            sb.append(", no labeled nodes in circuit");
        }
        
        return sb.toString();
    }

    // Helper method to find a LabeledNodeElm with matching text
    private LabeledNodeElm findLabeledNode(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        
        String trimmedName = name.trim();
        for (int i = 0; i < sim.elmList.size(); i++) {
            Object o = sim.elmList.elementAt(i);
            if (o instanceof LabeledNodeElm) {
                LabeledNodeElm lne = (LabeledNodeElm) o;
                if (trimmedName.equals(lne.text)) {
                    return lne;
                }
            }
        }
        return null;
    }
        
    // Public methods for managing equations
    public void setCellEquation(int row, int col, String equation) {
        if (isValidCell(row, col)) {
            cellEquations[row][col] = equation != null ? equation : "";
            compileEquation(row, col, cellEquations[row][col]);
        }
    }
    
    public String getCellEquation(int row, int col) {
        if (isValidCell(row, col)) {
            return cellEquations[row][col];
        }
        return "";
    }
    
   
    protected void registerComputedValueAsLabeledNode(String labelName, double voltage) {
        if (labelName == null || labelName.isEmpty()) {
            return;
        }
        
        // Store the computed voltage value in ComputedValues
        ComputedValues.setComputedValue(labelName, voltage);
    }
    
    // Static method to get computed values by other elements
    public static Double getComputedValue(String labelName) {
        return ComputedValues.getComputedValue(labelName);
    }
    
    // Custom method to format values with table units and decimal places
    protected String getTableFormattedText(double value) {
        // Format the number to the specified decimal places using GWT-compatible approach
        double multiplier = Math.pow(10, decimalPlaces);
        double rounded = Math.round(value * multiplier) / multiplier;
        String formattedValue = Double.toString(rounded);
        
        // Ensure we have the right number of decimal places
        if (formattedValue.indexOf('.') == -1) {
            formattedValue += ".";
        }
        
        int dotIndex = formattedValue.indexOf('.');
        int currentDecimals = formattedValue.length() - dotIndex - 1;
        
        // Pad with zeros if needed
        while (currentDecimals < decimalPlaces) {
            formattedValue += "0";
            currentDecimals++;
        }
        
        // Truncate if too many decimals
        if (currentDecimals > decimalPlaces) {
            formattedValue = formattedValue.substring(0, dotIndex + decimalPlaces + 1);
        }
        
        return formattedValue + tableUnits;
    }
    
    // Use the standard CircuitJS1 voltage coloring system
    protected Color getCellVoltageColor(double voltage) {
        // Use the same voltage coloring as other circuit elements
        // This properly handles zero voltage with gray and uses the global voltage range setting
        return getVoltageColor(null, voltage);
    }
    
    // Helper class to return grid-aligned cell width info
    protected static class GridAlignedCellInfo {
        int originalWidth;
        int alignedWidth;
        int columnPitch;
        int oddMultiple;
        
        GridAlignedCellInfo(int originalWidth, int alignedWidth, int columnPitch, int oddMultiple) {
            this.originalWidth = originalWidth;
            this.alignedWidth = alignedWidth;
            this.columnPitch = columnPitch;
            this.oddMultiple = oddMultiple;
        }
        
        String getStatusMessage() {
            if (alignedWidth == originalWidth) {
                return "Cell width aligned to grid";
            } else {
                return "Rounding " + originalWidth + " to " + alignedWidth + 
                       " (column pitch=" + columnPitch + "=" + oddMultiple + "×cspc)";
            }
        }
    }
    
    // Helper method to get cell width in pixels
    protected int getCellWidthPixels() {
        return cellWidthInGrids * cspc;  // Convert grid units to pixels
    }
    
    // Calculate grid-aligned cell width for pin positioning (REMOVED - no longer needed)
    // Cell width is already in grid units, so column pitch in grid units is just cellWidthInGrids
    
    @Override
    void setupPins() {
        // Cell width is in cspc units (single grid spacing)
        // But chip sizeX and sizeY must be in cspc2 units (double grid spacing)
        int rowDescColWidth = cellWidthInGrids;  // In cspc units
        int extraRows = (showInitialValues ? 1 : 0) + 1 + 1;
        
        // Calculate table dimensions in pixels
        int tableWidthPixels = (rowDescColWidth + cols * cellWidthInGrids) * cspc + 2 * cspc;  // Add margins
        int tableHeightPixels = (rows + extraRows) * cellHeight + (rows + extraRows + 1) * cellSpacing + 20;
        
        // Set chip size in cspc2 units (cspc2 = 2*cspc)
        sizeX = (tableWidthPixels + cspc2 - 1) / cspc2; // Round up
        sizeY = (tableHeightPixels + cspc2 - 1) / cspc2; // Round up

        // Create output pins on bottom edge
        pins = new Pin[cols];
        for (int i = 0; i < cols; i++) {
            String label = (outputNames != null && i < outputNames.length) ?
                          outputNames[i] : "Stock" + (i + 1);

            // Calculate pin X position
            // Column center in pixels (relative to table origin x):

            int cellWidthPixels = cellWidthInGrids * cspc;
            int rowDescColWidthPixels = cellWidthInGrids * cspc;
            int columnCenterX = rowDescColWidthPixels + cellSpacing * 2 + i * (cellWidthPixels + cellSpacing) + cellWidthPixels / 2;
            
            // Pin position is relative to x0 = x + cspc2, measured in cspc2 units
            // Column center is at: x + columnCenterX
            // Relative to x0: (x + columnCenterX) - (x + cspc2) = columnCenterX - cspc2
            // In cspc2 units: (columnCenterX - cspc2) / cspc2
            int pinX = (columnCenterX - cspc2) / cspc2;
            
            // Pin at bottom of chip (SIDE_S)
            pins[i] = new Pin(pinX, SIDE_S, label);
            pins[i].output = true;
        }
    }

    @Override
    int getVoltageSourceCount() {
        return cols; // One voltage source per output pin
    }

    @Override
    int getPostCount() {
        return cols; // One post per column
    }

    @Override
    void setPoints() {
        super.setPoints();

        // Calculate table dimensions in pixels for bounding box
        int cellWidthPixels = getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        int tableWidth = rowDescColWidth + cellSpacing + cols * cellWidthPixels + (cols + 1) * cellSpacing;
        int extraRows = (showInitialValues ? 1 : 0) + 1 + 1;
        int tableHeight = (rows + extraRows) * cellHeight + (rows + extraRows + 1) * cellSpacing + 20;

        // Align table origin with chip origin
        // The table should start at the chip's top-left corner
        int tableX = x;
        int tableY = y;

        // Set bounding box to exactly match the table size
        setBbox(tableX, tableY, tableX + tableWidth, tableY + tableHeight);
        
        // Override pin Y positions for precise placement at bottom of table
        // After super.setPoints() sets up pins using chip coordinate system,
        // we adjust only the Y positions to align with the actual table bottom
        // Keep the X positions from super.setPoints() as they're already correct
        int tableBottomY = tableY + tableHeight; // Bottom of table in pixels
        
        for (int i = 0; i < pins.length; i++) {
            Pin p = pins[i];
            
            // Keep the X positions from super.setPoints(), only override Y
            int pinXPixel = p.post.x;
            
            // Set pin positions with correct Y alignment
            // Post Y must be rounded to cspc for wire connection snapping

            int postY = tableBottomY + 3 * cellHeight / 2;  // allow for the Computed row
            postY = ((postY + cspc/2) / cspc) * cspc;  // Round to nearest cspc
            
            // Stub is where the pin meets the chip
            p.post = new Point(pinXPixel, postY);
            p.stub = new Point(pinXPixel, tableBottomY + cspc/2);
            p.textloc = new Point(pinXPixel, tableBottomY);
        }
    }

    // Helper method to get table origin coordinates
    private int getTableX() {
        // Table starts at chip origin
        return x;
    }

    private int getTableY() {
        // Table starts at chip origin  
        return y;
    }

    @Override
    void draw(Graphics g) {
        int tableX = getTableX();
        int tableY = getTableY();
        int cellWidthPixels = getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        
        // Calculate the actual table height by accumulating all components
        int titleHeight = 10 + 5; // Title offset + space after
        int typeRowHeight = cellHeight + cellSpacing;
        int headerRowHeight = cellHeight + cellSpacing;
        int initialRowHeight = showInitialValues ? (cellHeight + cellSpacing) : 0;
        int dataRowsHeight = rows * (cellHeight + cellSpacing);
        int computedRowHeight = cellHeight + cellSpacing;
        
        int tableWidth = rowDescColWidth + cellSpacing + cols * cellWidthPixels + (cols + 1) * cellSpacing;
        int tableHeight = titleHeight + typeRowHeight + headerRowHeight + initialRowHeight + dataRowsHeight + computedRowHeight;

        // Draw table background
        g.setColor(needsHighlight() ? selectColor : Color.white);
        g.fillRect(tableX, tableY, tableWidth, tableHeight);

        // Draw table border
        g.setColor(Color.black);
        g.drawRect(tableX, tableY, tableWidth, tableHeight);

        // Draw components in order with consistent positioning
        int currentY = 10; // Start position after table border
        
        // 1. Draw title
        drawTitle(g, currentY);
        currentY += 5; // Space after title
        
        // 2. Draw column type row
        drawColumnTypeRow(g, currentY);
        currentY += cellHeight + cellSpacing; // Move down by row height
        
        // 3. Draw column headers
        drawColumnHeaders(g, currentY);
        currentY += cellHeight + cellSpacing; // Move down by row height
        
        // 4. Draw initial conditions row if enabled
        if (showInitialValues) {
            drawInitialConditionsRow(g, currentY);
            currentY += cellHeight + cellSpacing;
        }

        // 5. Draw table cells with voltages (includes row descriptions)
        drawTableCells(g, currentY);
        currentY += rows * (cellHeight + cellSpacing);

        // 6. Draw computed row
        drawSumRow(g, currentY);
        currentY += cellHeight + cellSpacing;

        // 7. Draw chip pins and posts at the bottom
        drawPins(g);
    }
    
    private void drawTitle(Graphics g, int offsetY) {
        int tableX = getTableX();
        int tableY = getTableY();
        int cellWidthPixels = getCellWidthPixels();
        
        // Draw title centered at top of table
        g.setColor(Color.black);
        int rowDescColWidth = cellWidthPixels;
        int tableWidth = rowDescColWidth + cellSpacing + cols * cellWidthPixels + (cols + 1) * cellSpacing;
        int titleY = tableY + offsetY;
        drawCenteredText(g, tableTitle, tableX + tableWidth / 2, titleY, true);
    }


    private void drawColumnHeaders(Graphics g, int offsetY) {
        g.setColor(Color.black);
        int tableX = getTableX();
        int tableY = getTableY();
        int headerY = tableY + offsetY;
        int cellWidthPixels = getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;

        // Draw row description column header cell text
        int rowDescHeaderX = tableX + cellSpacing;
        drawCenteredText(g, "Flows↓/Stocks→", rowDescHeaderX + rowDescColWidth/2, headerY + cellHeight/2, true);

        // Draw data column header cells text
        for (int col = 0; col < cols; col++) {
            int cellX = tableX + rowDescColWidth + cellSpacing * 2 + col * (cellWidthPixels + cellSpacing);
            
            String header = (outputNames != null && col < outputNames.length) ?
                           outputNames[col] : "Stock" + (col + 1);
            drawCenteredText(g, header, cellX + cellWidthPixels/2, headerY + cellHeight/2, true);
        }
        
        // Draw grid lines for this row
        g.setColor(Color.black);
        int tableWidth = rowDescColWidth + cellSpacing * 2 + cols * (cellWidthPixels + cellSpacing);
        
        // Horizontal lines (top and bottom of row)
        g.drawLine(tableX, headerY, tableX + tableWidth, headerY);
        g.drawLine(tableX, headerY + cellHeight, tableX + tableWidth, headerY + cellHeight);
        
        // Vertical lines
        // Left edge
        g.drawLine(tableX, headerY, tableX, headerY + cellHeight);
        // After description column
        int x = tableX + cellSpacing + rowDescColWidth;
        g.drawLine(x, headerY, x, headerY + cellHeight);
        // Between and after data columns
        for (int col = 0; col <= cols; col++) {
            x = tableX + rowDescColWidth + cellSpacing * 2 + col * (cellWidthPixels + cellSpacing);
            g.drawLine(x, headerY, x, headerY + cellHeight);
        }
        
        // Draw separator line after column headers
        g.setColor(Color.black);
        g.drawLine(tableX, headerY + cellHeight, tableX + tableWidth, headerY + cellHeight);
    }
    
    private void drawColumnTypeRow(Graphics g, int offsetY) {
        int tableX = getTableX();
        int tableY = getTableY();
        int cellWidthPixels = getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        int typeRowY = tableY + offsetY;
        
        // Draw row description column cell text
        g.setColor(Color.black);
        drawCenteredText(g, "Type", tableX + cellSpacing + rowDescColWidth/2, typeRowY + cellHeight/2, true);
        
        // Draw column type cells text
        for (int col = 0; col < cols; col++) {
            int cellX = tableX + rowDescColWidth + cellSpacing * 2 + col * (cellWidthPixels + cellSpacing);
            
            // Draw column type text
            String typeName = getColumnTypeName(col);
            drawCenteredText(g, typeName, cellX + cellWidthPixels/2, typeRowY + cellHeight/2, true);
        }
        
        // Draw grid lines for this row
        g.setColor(Color.black);
        int tableWidth = rowDescColWidth + cellSpacing * 2 + cols * (cellWidthPixels + cellSpacing);
        
        // Horizontal lines (top and bottom of row)
        g.drawLine(tableX, typeRowY, tableX + tableWidth, typeRowY);
        g.drawLine(tableX, typeRowY + cellHeight, tableX + tableWidth, typeRowY + cellHeight);
        
        // Vertical lines
        // Left edge
        g.drawLine(tableX, typeRowY, tableX, typeRowY + cellHeight);
        // After description column
        int x = tableX + cellSpacing + rowDescColWidth;
        g.drawLine(x, typeRowY, x, typeRowY + cellHeight);
        // Between and after data columns
        for (int col = 0; col <= cols; col++) {
            x = tableX + rowDescColWidth + cellSpacing * 2 + col * (cellWidthPixels + cellSpacing);
            g.drawLine(x, typeRowY, x, typeRowY + cellHeight);
        }
        
        // Draw separator line after column type row
        g.setColor(Color.black);
        g.drawLine(tableX, typeRowY + cellHeight, tableX + tableWidth, typeRowY + cellHeight);
    }

    private void drawInitialConditionsRow(Graphics g, int offsetY) {
        int tableX = getTableX();
        int tableY = getTableY();
        int cellWidthPixels = getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;
        int initialRowY = tableY + offsetY;
        
        // Draw row description cell for initial conditions
        g.setColor(Color.black);
        drawCenteredText(g, "Initial", tableX + cellSpacing + rowDescColWidth/2, initialRowY + cellHeight/2, true);

        for (int col = 0; col < cols; col++) {
            int cellX = tableX + rowDescColWidth + cellSpacing * 2 + col * (cellWidthPixels + cellSpacing);
            
            // Get initial conditions value for this column
            double initialValue = (initialValues != null && col < initialValues.length) ? 
                                 initialValues[col] : 0.0;
            
            // Draw initial conditions cell background - always white
            g.setColor(Color.white);
            g.fillRect(cellX, initialRowY, cellWidthPixels, cellHeight);
            
            // Draw value with text color based on voltage
            g.setColor(getCellVoltageColor(initialValue));
            String voltageText = getTableFormattedText(initialValue);
            drawCenteredText(g, voltageText, cellX + cellWidthPixels/2, initialRowY + cellHeight/2, true);
        }
        
        // Draw grid lines for this row
        g.setColor(needsHighlight() ? selectColor : Color.black);
        int tableWidth = rowDescColWidth + cellSpacing * 2 + cols * (cellWidthPixels + cellSpacing);
        
        // Horizontal lines (top and bottom of row)
        g.drawLine(tableX, initialRowY, tableX + tableWidth, initialRowY);
        g.drawLine(tableX, initialRowY + cellHeight, tableX + tableWidth, initialRowY + cellHeight);
        
        // Vertical lines
        // Left edge
        g.drawLine(tableX, initialRowY, tableX, initialRowY + cellHeight);
        // After description column
        int x = tableX + cellSpacing + rowDescColWidth;
        g.drawLine(x, initialRowY, x, initialRowY + cellHeight);
        // Between and after data columns
        for (int col = 0; col <= cols; col++) {
            x = tableX + rowDescColWidth + cellSpacing * 2 + col * (cellWidthPixels + cellSpacing);
            g.drawLine(x, initialRowY, x, initialRowY + cellHeight);
        }
        
        // Draw separator line after initial conditions row
        g.setColor(needsHighlight() ? selectColor : Color.black);
        g.drawLine(tableX, initialRowY + cellHeight, tableX + tableWidth, initialRowY + cellHeight);
    }

    private void drawTableCells(Graphics g, int offsetY) {
        int tableX = getTableX();
        int tableY = getTableY();
        int cellWidthPixels = getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;

        // Use the passed offsetY directly - no need to recalculate
        int baseY = offsetY;

        for (int row = 0; row < rows; row++) {
            int cellY = tableY + baseY + row * (cellHeight + cellSpacing);
            
            // Draw row description text
            g.setColor(Color.black);
            String rowDesc = (rowDescriptions != null && row < rowDescriptions.length) ?
                            rowDescriptions[row] : "Row" + (row + 1);
            drawCenteredText(g, rowDesc, tableX + cellSpacing + rowDescColWidth/2, cellY + cellHeight/2, true);
            
            // Draw data cells for this row
            for (int col = 0; col < cols; col++) {
                int cellX = tableX + rowDescColWidth + cellSpacing * 2 + col * (cellWidthPixels + cellSpacing);
                
                // Get voltage using equation evaluation
                double voltage = getVoltageForCell(row, col);
                
                // Draw cell background - always white
                g.setColor(Color.white);
                g.fillRect(cellX, cellY, cellWidthPixels, cellHeight);
                
                // Display equation and voltage in cell (only if equation is not empty)
                // Set text color based on voltage using CircuitJS1 standard colors
                String equation = cellEquations[row][col];
                if (equation != null && !equation.trim().isEmpty()) {
                    g.setColor(getCellVoltageColor(voltage));
                    String displayText = equation;
                    if (displayText.length() > 8) {
                        displayText = displayText.substring(0, 6) + ".."; // Truncate long equations
                    }
                    String voltageText = getTableFormattedText(voltage);
                    String combinedText = displayText + " = " + voltageText;
                    drawCenteredText(g, combinedText, cellX + cellWidthPixels/2, cellY + cellHeight/2, true);
                }
                // Don't display anything for empty cells
            }
            
            // Draw grid lines for this row
            g.setColor(needsHighlight() ? selectColor : Color.black);
            int tableWidth = rowDescColWidth + cellSpacing * 2 + cols * (cellWidthPixels + cellSpacing);
            
            // Horizontal lines (top and bottom of row)
            g.drawLine(tableX, cellY, tableX + tableWidth, cellY);
            g.drawLine(tableX, cellY + cellHeight, tableX + tableWidth, cellY + cellHeight);
            
            // Vertical lines
            // Left edge
            g.drawLine(tableX, cellY, tableX, cellY + cellHeight);
            // After description column
            int x = tableX + cellSpacing + rowDescColWidth;
            g.drawLine(x, cellY, x, cellY + cellHeight);
            // Between and after data columns
            for (int col = 0; col <= cols; col++) {
                x = tableX + rowDescColWidth + cellSpacing * 2 + col * (cellWidthPixels + cellSpacing);
                g.drawLine(x, cellY, x, cellY + cellHeight);
            }
        }
    }

    protected void drawSumRow(Graphics g, int offsetY) {
        int tableX = getTableX();
        int tableY = getTableY();
        int cellWidthPixels = getCellWidthPixels();
        int rowDescColWidth = cellWidthPixels;

        // Use the passed offsetY directly - no need to recalculate
        int sumRowY = tableY + offsetY;

        // Draw row description text for computed row
        g.setColor(Color.black);
        drawCenteredText(g, "Computed", tableX + cellSpacing + rowDescColWidth/2, sumRowY + cellHeight/2, true);

        for (int col = 0; col < cols; col++) {
            int cellX = tableX + rowDescColWidth + cellSpacing * 2 + col * (cellWidthPixels + cellSpacing);
            
            // Get the already-calculated sum from computed values (calculated in doStep())
            String sumLabelName = outputNames[col];
            Double computedSum = ComputedValues.getComputedValue(sumLabelName);
            double computedValue = (computedSum != null) ? computedSum.doubleValue() : 0.0;
            
            // Draw sum cell background - always white
            g.setColor(Color.white);
            g.fillRect(cellX, sumRowY, cellWidthPixels, cellHeight);
            
            // Draw column name and value with text color based on voltage
            g.setColor(getCellVoltageColor(computedValue));
            String sumText = getTableFormattedText(computedValue);
            String combinedText = sumLabelName + ": " + sumText;
            drawCenteredText(g, combinedText, cellX + cellWidthPixels/2, sumRowY + cellHeight/2, true);
        }
        
        // Draw grid lines for computed row
        g.setColor(needsHighlight() ? selectColor : Color.black);
        int tableWidth = rowDescColWidth + cellSpacing * 2 + cols * (cellWidthPixels + cellSpacing);
        
        // Horizontal lines (top and bottom of row)
        g.drawLine(tableX, sumRowY, tableX + tableWidth, sumRowY);
        g.drawLine(tableX, sumRowY + cellHeight, tableX + tableWidth, sumRowY + cellHeight);
        
        // Vertical lines
        // Left edge
        g.drawLine(tableX, sumRowY, tableX, sumRowY + cellHeight);
        // After description column
        int x = tableX + cellSpacing + rowDescColWidth;
        g.drawLine(x, sumRowY, x, sumRowY + cellHeight);
        // Between and after data columns
        for (int col = 0; col <= cols; col++) {
            x = tableX + rowDescColWidth + cellSpacing * 2 + col * (cellWidthPixels + cellSpacing);
            g.drawLine(x, sumRowY, x, sumRowY + cellHeight);
        }
        
        // Draw separator line before computed row (emphasize it's special)
        g.setColor(needsHighlight() ? selectColor : Color.black);
        g.drawLine(tableX, sumRowY, tableX + tableWidth, sumRowY);
    }

    
    private void drawPins(Graphics g) {
        // Draw pins on the bottom edge
        for (int i = 0; i < getPostCount(); i++) {
            Pin p = pins[i];
            setVoltageColor(g, volts[i]);
            Point a = p.post;
            Point b = p.stub;
            drawThickLine(g, a, b);
            p.curcount = updateDotCount(p.current, p.curcount);
            drawDots(g, b, a, p.curcount);
        }
        drawPosts(g);
    }
    private double[] lastColumnSums;
   
    // Calculate computed values during simulation step (not during drawing)
    @Override
    public void doStep() {
        // Update input pin values from circuit
        for (int i = 0; i < getPostCount(); i++) {
            Pin p = pins[i];
            if (!p.output)
                p.value = volts[i] > getThreshold();
        }

        // Always compute column sums for matrix stamping
        if (lastColumnSums == null) {
            lastColumnSums = new double[cols];
        }

        for (int col = 0; col < cols; col++) {
            double columnSum = 0.0;
            String name = outputNames[col];
            
            // Check to see if column computed value is already calculated by another element
            Double existingValue = ComputedValues.getComputedValue(name);
            boolean alreadyComputed = ComputedValues.isComputedThisStep(name);
            
            if (alreadyComputed && existingValue != null) {
                // Use the already computed value
                columnSum = existingValue.doubleValue();
            } else {
                // Compute the value ourselves
                for (int row = 0; row < rows; row++) {
                    double v = getVoltageForCell(row, col);
                    columnSum += v;
                }
            }

            // Check for convergence
            Double diff = Math.abs(columnSum - lastColumnSums[col]);
            if (diff > 1e-6) {
                sim.converged = false;
            }

            lastColumnSums[col] = columnSum;

            // Update output pin voltage source with converged column sum
            if (col < pins.length && pins[col].output) {
                sim.updateVoltageSource(0, nodes[col], pins[col].voltSource, columnSum);
            }
            

        }
    }

    @Override
    public void stepFinished() {
        // Register computed values for other elements to use - do this after convergence
        for (int col = 0; col < cols; col++) {
            String name = outputNames[col];
            
            // Only register if we computed it ourselves (not if we used a pre-computed value)
            boolean alreadyComputed = ComputedValues.isComputedThisStep(name);
            if (!alreadyComputed && lastColumnSums != null) {
                registerComputedValueAsLabeledNode(name, lastColumnSums[col]);    
                ComputedValues.markComputedThisStep(name);
            }
        }
    }
    
    boolean nonLinear() {
        // Always nonlinear since we use equations (which may be nonlinear)
        return true;
    }
    
    // Has electrical connections via output pins
    @Override
    boolean isWireEquivalent() { return false; }

    @Override
    boolean isRemovableWire() { return false; }

    @Override
    boolean isDigitalChip() { return false; } // Analog chip with voltage outputs

    @Override
    String getChipName() { return "Table"; }

    @Override
    void stamp() {
        // Stamp voltage sources for all output pins
        for (int i = 0; i < getPostCount(); i++) {
            Pin p = pins[i];
            if (p.output) {
                sim.stampVoltageSource(0, nodes[i], p.voltSource);
            }
        }
        
        // Connect to labeled nodes if output names are specified
        if (outputNames != null) {
            for (int col = 0; col < cols && col < outputNames.length; col++) {
                String outputName = outputNames[col];
                if (outputName != null && !outputName.trim().isEmpty()) {
                    LabeledNodeElm labeledNode = findLabeledNode(outputName);
                    if (labeledNode != null) {
                        // Stamp low-value resistor between output node and labeled node
                        double resistance = 1e-6; // 1μΩ - very low resistance
                        int outputNode = nodes[col]; // Output node from table
                        int labeledNodeNum = labeledNode.getNode(0); // Get labeled node number
                        
                        // Validate node numbers before stamping
                        if (outputNode >= 0 && labeledNodeNum >= 0 && sim.nodeList != null && 
                            outputNode < sim.nodeList.size() && labeledNodeNum < sim.nodeList.size()) {
                            //CirSim.console("TableElm: Stamping resistor between nodes " + outputNode + " and " + labeledNodeNum + " (resistance=" + resistance + ")");
                            sim.stampResistor(outputNode, labeledNodeNum, resistance);
                            //CirSim.console("TableElm: Successfully stamped resistor for output '" + outputName + "'");
                        }
                        // Error handling removed - invalid nodes are silently ignored
                    } else {
//                        CirSim.console("TableElm: No labeled node found for output name '" + outputName + "'");
                        ;
                    }
                }
            }
        }
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.dump()).append(" ").append(rows).append(" ").append(cols).append(" ").append(showInitialValues);
        sb.append(" ").append(CustomLogicModel.escape(tableUnits)).append(" ").append(decimalPlaces);
        sb.append(" ").append(CustomLogicModel.escape(tableTitle));
        
        // Serialize output names (used as column headers)
        for (int col = 0; col < cols; col++) {
            sb.append(" ").append(CustomLogicModel.escape(outputNames[col]));
        }
        
        // Serialize row descriptions
        for (int row = 0; row < rows; row++) {
            sb.append(" ").append(CustomLogicModel.escape(rowDescriptions[row]));
        }
        
        // Always serialize initial conditions values
        if (initialValues != null) {
            for (int col = 0; col < cols; col++) {
                sb.append(" ").append(initialValues[col]);
            }
        }
        
        // Serialize column types
        if (columnTypes != null) {
            for (int col = 0; col < cols; col++) {
                sb.append(" ").append(columnTypes[col].name());
            }
        }
        
        // Serialize equation data only
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                String equation = (cellEquations[row][col] != null) ? cellEquations[row][col] : "";
                sb.append(" ").append(CustomLogicModel.escape(equation));
            }
        }
        
        return sb.toString();
    }

    protected void parseTableData(StringTokenizer st) {
        try {
            // Parse basic dimensions
            if (st.hasMoreTokens()) rows = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens()) cols = Integer.parseInt(st.nextToken());
            
            // Initialize arrays
            outputNames = new String[cols];
            cellEquations = new String[rows][cols];
            initialValues = new double[cols];
            rowDescriptions = new String[rows];
            
            // Parse table properties 
            showInitialValues = st.hasMoreTokens() ? Boolean.parseBoolean(st.nextToken()) : false;
            tableUnits = st.hasMoreTokens() ? CustomLogicModel.unescape(st.nextToken()) : "";
            decimalPlaces = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 2;
            tableTitle = st.hasMoreTokens() ? CustomLogicModel.unescape(st.nextToken()) : "Table";
            
            // Parse column headers
            for (int col = 0; col < cols; col++) {
                if (st.hasMoreTokens()) {
                    outputNames[col] = CustomLogicModel.unescape(st.nextToken());
                } else {
                    outputNames[col] = "Stock" + (col + 1);
                }
            }
            
            // Parse row descriptions (if available - for backwards compatibility)
            for (int row = 0; row < rows; row++) {
                if (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    // Try to determine if this is a row description or initial value
                    try {
                        // If it parses as a double, it's probably an initial value
                        Double.parseDouble(token);
                        // It's a number, so no row descriptions in this file
                        // Use default row descriptions
                        for (int r = 0; r < rows; r++) {
                            rowDescriptions[r] = "Row" + (r + 1);
                        }
                        // Don't consume this token - it's for initial values
                        break;
                    } catch (NumberFormatException e) {
                        // It's a string, so it's a row description
                        rowDescriptions[row] = CustomLogicModel.unescape(token);
                    }
                } else {
                    rowDescriptions[row] = "Row" + (row + 1);
                }
            }
            
            // Parse initial values
            for (int col = 0; col < cols; col++) {
                if (st.hasMoreTokens()) {
                    try {
                        initialValues[col] = Double.parseDouble(st.nextToken());
                    } catch (NumberFormatException e) {
                        initialValues[col] = 0.0;
                    }
                } else {
                    initialValues[col] = 0.0;
                }
            }
            
            // Parse column types (if available in saved data)
            columnTypes = new ColumnType[cols];
            boolean hasColumnTypes = false;
            for (int col = 0; col < cols; col++) {
                if (st.hasMoreTokens()) {
                    String typeToken = st.nextToken();
                    // Check if this looks like a column type or an equation
                    try {
                        columnTypes[col] = ColumnType.valueOf(typeToken);
                        hasColumnTypes = true;
                    } catch (IllegalArgumentException e) {
                        // Not a valid column type, must be start of equations
                        // Push back by saving for equation parsing
                        if (cellEquations == null) {
                            cellEquations = new String[rows][cols];
                        }
                        cellEquations[0][col] = CustomLogicModel.unescape(typeToken);
                        break;
                    }
                } else {
                    break;
                }
            }
            
            // If no column types were found, initialize with defaults
            if (!hasColumnTypes) {
                for (int col = 0; col < cols; col++) {
                    if (col == 0) {
                        columnTypes[col] = ColumnType.ASSET;
                    } else if (col == 1) {
                        columnTypes[col] = ColumnType.LIABILITY;
                    } else if (col == 2) {
                        columnTypes[col] = ColumnType.EQUITY;
                    } else if (col == 3) {
                        columnTypes[col] = ColumnType.A_L_E;
                    } else {
                        columnTypes[col] = ColumnType.ASSET;
                    }
                }
            }
            
            // Parse cell equations
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    if (st.hasMoreTokens()) {
                        cellEquations[row][col] = CustomLogicModel.unescape(st.nextToken());
                    } else {
                        cellEquations[row][col] = "";
                    }
                }
            }
            
            CirSim.console("TableElm: Successfully parsed simplified table data");
            
        } catch (Exception e) {
            CirSim.console("TableElm: Error parsing table data: " + e.getMessage());
            // Initialize with defaults on error
            initializeDefaults();
        }
    }
    
    private void initializeDefaults() {
        if (outputNames == null) {
            outputNames = new String[cols];
            for (int col = 0; col < cols; col++) {
                outputNames[col] = "Stock" + (col + 1);
            }
        }
        if (rowDescriptions == null) {
            rowDescriptions = new String[rows];
            for (int row = 0; row < rows; row++) {
                rowDescriptions[row] = "Row" + (row + 1);
            }
        }
        if (columnTypes == null) {
            columnTypes = new ColumnType[cols];
            for (int col = 0; col < cols; col++) {
                if (col == 0) {
                    columnTypes[col] = ColumnType.ASSET;
                } else if (col == 1) {
                    columnTypes[col] = ColumnType.LIABILITY;
                } else if (col == 2) {
                    columnTypes[col] = ColumnType.EQUITY;
                } else if (col == 3) {
                    columnTypes[col] = ColumnType.A_L_E;
                } else {
                    columnTypes[col] = ColumnType.ASSET;
                }
            }
        }
        if (cellEquations == null) {
            cellEquations = new String[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    cellEquations[row][col] = "";
                }
            }
        }
        if (initialValues == null) {
            initialValues = new double[cols];
            for (int col = 0; col < cols; col++) {
                initialValues[col] = 0.0;
            }
        }
        showInitialValues = false;
        tableUnits = "";
        decimalPlaces = 2;
        tableTitle = "Table";
    }

    int getDumpType() { 
        return 253; // Choose unused dump type
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) return new EditInfo("Table Title", tableTitle);
        if (n == 1) return new EditInfo("Cell Width (grids)", cellWidthInGrids, 1, 20);
        if (n == 2) return new EditInfo("Cell Height", cellHeight, 16, 48);
        if (n == 3) return new EditInfo("Cell Spacing", cellSpacing, 0, 5);
        if (n == 4) {
            EditInfo ei = new EditInfo("Show Initial Values", 0, -1, -1);
            ei.checkbox = new Checkbox("", showInitialValues);
            return ei;
        }
        if (n == 5) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.button = new Button("Edit Table Data...");
            return ei;
        }
        
        return null;
    }

    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            tableTitle = ei.textf.getText();
        } else if (n == 1) {
            cellWidthInGrids = Math.max(1, (int)ei.value);
        } else if (n == 2) {
            cellHeight = Math.max(16, (int)ei.value);
        } else if (n == 3) {
            cellSpacing = Math.max(0, (int)ei.value);

        } else if (n == 4) {
            showInitialValues = ei.checkbox.getValue();

        } else if (n == 5) {
            // Open table editing dialog
            openTableEditDialog();
        }
        setupPins(); // Recalculate chip size and pin positions
        setPoints();
        sim.analyzeFlag = true; // Trigger circuit re-analysis for pin position changes
    }

    // Resize table method for use by TableEditDialog
    public void resizeTable(int newRows, int newCols) {
        String[] oldOutputNames = outputNames;
        String[][] oldEquations = cellEquations;
        double[] oldInitialConditions = initialValues;
        ColumnType[] oldColumnTypes = columnTypes;
        String[] oldRowDescriptions = rowDescriptions;

        // Update dimensions
        rows = newRows;
        cols = newCols;

        // Create new arrays
        outputNames = new String[cols];
        cellEquations = new String[rows][cols];
        compiledExpressions = new Expr[rows][cols];
        expressionStates = new ExprState[rows][cols];
        initialValues = new double[cols];
        columnTypes = new ColumnType[cols];
        rowDescriptions = new String[rows];

        // Initialize ALL cells with empty strings (null equivalent)
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                cellEquations[row][col] = "";
                compiledExpressions[row][col] = null;
                expressionStates[row][col] = new ExprState(1); // Only need time variable
            }
        }

        // Copy over existing data where possible
        if (oldEquations != null) {
            int copyRows = Math.min(rows, oldEquations.length);
            for (int row = 0; row < copyRows; row++) {
                if (oldEquations[row] != null) {
                    int copyCols = Math.min(cols, oldEquations[row].length);
                    for (int col = 0; col < copyCols; col++) {
                        if (oldEquations[row][col] != null) {
                            cellEquations[row][col] = oldEquations[row][col];
                            // Recompile equations for copied cells
                            if (!cellEquations[row][col].isEmpty()) {
                                compileEquation(row, col, cellEquations[row][col]);
                            }
                        }
                    }
                }
            }
        }

        // Copy over existing output names where possible (already handled below)

        // Copy over existing initial conditions where possible
        if (oldInitialConditions != null) {
            for (int col = 0; col < Math.min(cols, oldInitialConditions.length); col++) {
                initialValues[col] = oldInitialConditions[col];
            }
        }

        // Initialize remaining initial conditions values with zeros
        for (int col = 0; col < cols; col++) {
            if (oldInitialConditions == null || col >= oldInitialConditions.length) {
                initialValues[col] = 0.0;
            }
        }

        // Copy over existing output names where possible
        if (oldOutputNames != null) {
            for (int col = 0; col < Math.min(cols, oldOutputNames.length); col++) {
                outputNames[col] = oldOutputNames[col];
            }
        }

        // Initialize remaining output names
        for (int col = 0; col < cols; col++) {
            if (oldOutputNames == null || col >= oldOutputNames.length || 
                outputNames[col] == null || outputNames[col].isEmpty()) {
                outputNames[col] = "Stock_" + (col + 1);
            }
        }
        
        // Copy over existing column types where possible
        if (oldColumnTypes != null) {
            for (int col = 0; col < Math.min(cols, oldColumnTypes.length); col++) {
                columnTypes[col] = oldColumnTypes[col];
            }
        }
        
        // Initialize remaining column types with default values
        for (int col = 0; col < cols; col++) {
            if (oldColumnTypes == null || col >= oldColumnTypes.length) {
                if (col == 0) {
                    columnTypes[col] = ColumnType.ASSET;
                } else if (col == 1) {
                    columnTypes[col] = ColumnType.LIABILITY;
                } else if (col == 2) {
                    columnTypes[col] = ColumnType.EQUITY;
                } else if (col == 3) {
                    columnTypes[col] = ColumnType.A_L_E;
                } else {
                    columnTypes[col] = ColumnType.ASSET;
                }
            }
        }
        
        // Copy over existing row descriptions where possible
        if (oldRowDescriptions != null) {
            for (int row = 0; row < Math.min(rows, oldRowDescriptions.length); row++) {
                rowDescriptions[row] = oldRowDescriptions[row];
            }
        }
        
        // Initialize remaining row descriptions with default values
        for (int row = 0; row < rows; row++) {
            if (oldRowDescriptions == null || row >= oldRowDescriptions.length ||
                rowDescriptions[row] == null || rowDescriptions[row].isEmpty()) {
                rowDescriptions[row] = "Flow_" + (row + 1);
            }
        }

        // Recreate pins with new column count
        setupPins();
        allocNodes();
        setPoints();
    }
    
    protected void openTableEditDialog() {
        // Open the enhanced table editing dialog
        if (sim != null) {
            TableEditDialog dialog = new TableEditDialog(this, sim);
            dialog.show();
        }
    }
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "Equation Table (" + rows + "x" + cols + ")";
        arr[1] = "All cells use equations (node names or expressions)";

        int idx = 2;

        // Show output pin values
        for (int col = 0; col < Math.min(cols, 3) && idx < arr.length - 1; col++) {
            String header = outputNames[col];
            double output = lastColumnSums != null ? lastColumnSums[col] : 0.0;
            arr[idx++] = header + " = " + getTableFormattedText(output);
        }

        if (cols > 3 && idx < arr.length - 1) {
            arr[idx++] = "... (" + cols + " outputs total)";
        }
    }
    

    public String getCellLabel(int row, int col) {
        if (isValidCell(row, col)) {
            return cellEquations[row][col];
        }
        return "";
    }

    public void setColumnHeader(int col, String header) {
        if (col >= 0 && col < cols) {
            outputNames[col] = header;
        }
    }

    public String getColumnHeader(int col) {
        if (col >= 0 && col < cols) {
            return outputNames[col];
        }
        return "";
    }
    
    // Getter methods for TableEditDialog
    public int getRows() { return rows; }
    public int getCols() { return cols; }
    
    public double getInitialValue(int col) {
        if (col >= 0 && col < cols && initialValues != null && col < initialValues.length) {
            return initialValues[col];
        }
        return 0.0;
    }
    
    public void setInitialConditionValue(int col, double value) {
        if (col >= 0 && col < cols && initialValues != null && col < initialValues.length) {
            initialValues[col] = value;
        }
    }
    
    // Additional methods for equation support in TableEditDialog

    
    public String getCellDisplayText(int row, int col) {
        if (isValidCell(row, col)) {
            return cellEquations[row][col];
        }
        return "";
    }
    
    public void setCellDisplayText(int row, int col, String text) {
        if (isValidCell(row, col)) {
            setCellEquation(row, col, text);
        }
    }
    
    public double getCellVoltage(int row, int col) {
        return getVoltageForCell(row, col);
    }

    // Add a method to be called when the circuit is fully loaded
    public void circuitLoaded() {
        // Recompile all equations now that the circuit and labeled nodes should be available
        CirSim.console("TableElm: Circuit loaded, recompiling all equations...");
        recompileAllEquations();
    }
    
    // Column type accessor methods for TableEditDialog
    public ColumnType getColumnType(int col) {
        if (col >= 0 && col < cols && columnTypes != null && col < columnTypes.length) {
            return columnTypes[col];
        }
        return ColumnType.ASSET; // Default
    }
    
    public void setColumnType(int col, ColumnType type) {
        if (col >= 0 && col < cols) {
            if (columnTypes == null || columnTypes.length != cols) {
                // Initialize if needed
                columnTypes = new ColumnType[cols];
                for (int i = 0; i < cols; i++) {
                    columnTypes[i] = ColumnType.ASSET;
                }
            }
            columnTypes[col] = type;
        }
    }
    
    public String getColumnTypeName(int col) {
        ColumnType type = getColumnType(col);
        switch (type) {
            case ASSET: return "Asset";
            case LIABILITY: return "Liability";
            case EQUITY: return "Equity";
            case A_L_E: return "A-L-E";
            default: return "Unknown";
        }
    }
    
    // Table title accessor methods
    public String getTableTitle() {
        return tableTitle;
    }
    
    public void setTableTitle(String title) {
        this.tableTitle = (title != null) ? title : "Table";
    }
    
    // Row description accessor methods
    public String getRowDescription(int row) {
        if (row >= 0 && row < rows && rowDescriptions != null && row < rowDescriptions.length) {
            return rowDescriptions[row];
        }
        return "Row" + (row + 1);
    }
    
    public void setRowDescription(int row, String description) {
        if (row >= 0 && row < rows) {
            if (rowDescriptions == null || rowDescriptions.length != rows) {
                rowDescriptions = new String[rows];
                for (int i = 0; i < rows; i++) {
                    rowDescriptions[i] = "Row" + (i + 1);
                }
            }
            rowDescriptions[row] = description;
        }
    }
    
    // Add static counter for table numbering
    private static int nextTableNumber = 1;

    // Add method to reset counter when loading circuits
    public static void resetTableCounter() {
        nextTableNumber = 1;
    }

    // Add method to update counter based on existing tables
    public static void updateTableCounter(String title) {
        // Extract number from title like "Table 5"
        if (title != null && title.startsWith("Table ")) {
            try {
                String numStr = title.substring(6).trim();
                int num = Integer.parseInt(numStr);
                if (num >= nextTableNumber) {
                    nextTableNumber = num + 1;
                }
            } catch (NumberFormatException e) {
                // Not a numbered table, ignore
            }
        }
    }
}


