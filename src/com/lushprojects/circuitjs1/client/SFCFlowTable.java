/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.TableColumn.ColumnType;
import java.util.ArrayList;

/**
 * SFC table variant where each sector column is an electrical node anchor.
 *
 * Cells are treated as labeled-node names. During wire closure, all labels in a
 * column are pre-registered to that column post, causing them to merge into one
 * MNA node. This enforces KCL with no table stamping.
 */
public class SFCFlowTable extends TableElm {
    private static final String TITLE_PREFIX = "SFC Flow Table";
    private static int nextTableNumber = 1;

    protected boolean highlightImbalances = true;
    protected double balanceTolerance = 1e-6;
    protected boolean showFlowValues = false;

    private SFCFlowTableRenderer flowRenderer;
    private double[] stockLastVoltage;
    private double[] stockLastCurrent;
    private double[] stockCurSourceValue;

    private boolean useBackwardEuler = false;

    public SFCFlowTable(int xx, int yy) {
        super(xx, yy);
        tableTitle = TITLE_PREFIX + " " + nextTableNumber;
        nextTableNumber++;
        showALE = false;
        showInitialValues = true;
        collapsedMode = false;

        initializeFlowTable();
        if (equationManager != null) {
            equationManager.setUseConvergedValues(true);
        }

        flowRenderer = new SFCFlowTableRenderer(this);
        setupPins();
        setPoints();
    }

    public SFCFlowTable(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        showALE = false;

        if (equationManager != null) {
            equationManager.setUseConvergedValues(true);
        }

        if (st.hasMoreTokens()) {
            try {
                highlightImbalances = Boolean.parseBoolean(st.nextToken());
            } catch (Exception e) {
                highlightImbalances = true;
            }
        }
        if (st.hasMoreTokens()) {
            try {
                balanceTolerance = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                balanceTolerance = 1e-6;
            }
        }
        if (st.hasMoreTokens()) {
            try {
                showFlowValues = Boolean.parseBoolean(st.nextToken());
            } catch (Exception e) {
                showFlowValues = false;
            }
        }
        if (st.hasMoreTokens()) {
            try {
                useBackwardEuler = Boolean.parseBoolean(st.nextToken());
            } catch (Exception e) {
                useBackwardEuler = false;
            }
        }
        if (showFlowValues && showCellValues != 1 && showCellValues != 2) {
            showCellValues = 2;
        }

        updateFlowTableCounter(tableTitle);
        ensureSigmaColumn();
        allocStockStateCache();

        flowRenderer = new SFCFlowTableRenderer(this);
        setupPins();
        setPoints();
    }

    @Override
    int getDumpType() {
        return 270;
    }

    @Override
    public String dump() {
        return super.dump() + " " + highlightImbalances + " " + balanceTolerance + " " + showFlowValues + " " + useBackwardEuler;
    }

    @Override
    public EditInfo getEditInfo(int n) {
        if (n == 6) {
            EditInfo ei = new EditInfo("Cell Display Mode", "");
            ei.choice = new Choice();
            ei.choice.add("Equation");
            ei.choice.add("Equation: Value");
            ei.choice.add("Value");
            ei.choice.add("Flow");
            ei.choice.add("Equation: Flow");
            int modeIndex;
            if (showFlowValues) {
                modeIndex = (showCellValues == 1) ? 4 : 3;
            } else {
                modeIndex = Math.max(0, Math.min(2, showCellValues));
            }
            ei.choice.select(modeIndex);
            return ei;
        }
        if (n == 8) {
            EditInfo ei = new EditInfo("Stock Integration", "");
            ei.choice = new Choice();
            ei.choice.add("Trapezoidal");
            ei.choice.add("Backward Euler");
            ei.choice.select(useBackwardEuler ? 1 : 0);
            return ei;
        }
        return super.getEditInfo(n);
    }

    @Override
    public void setEditValue(int n, EditInfo ei) {
        if (n == 6) {
            int mode = ei.choice.getSelectedIndex();
            if (mode == 3) {
                showFlowValues = true;
                showCellValues = 2;
            } else if (mode == 4) {
                showFlowValues = true;
                showCellValues = 1;
            } else {
                showFlowValues = false;
                showCellValues = mode;
            }
            setupPins();
            setPoints();
            sim.analyzeFlag = true;
            return;
        }
        if (n == 8) {
            useBackwardEuler = (ei.choice.getSelectedIndex() == 1);
            sim.analyzeFlag = true;
            return;
        }
        super.setEditValue(n, ei);
    }

    private void initializeFlowTable() {
        String[] sectorNames = {"Households", "Firms", "Banks", "Govt"};
        String[] transactionNames = {"Consumption", "Wages", "Interest", "Taxes"};

        rows = transactionNames.length;
        rowDescriptions = new String[rows];
        for (int i = 0; i < rows; i++) {
            rowDescriptions[i] = transactionNames[i];
        }

        columns = new ArrayList<TableColumn>();
        for (int col = 0; col < sectorNames.length; col++) {
            TableColumn column = new TableColumn(sectorNames[col], ColumnType.SECTOR, 0.0, rows);
            for (int row = 0; row < rows; row++) {
                column.setCellEquation(row, "");
            }
            columns.add(column);
        }

        columns.add(new TableColumn("Σ", ColumnType.COMPUTED, 0.0, rows));
        equationManager.recompileAllEquations();
        allocStockStateCache();
    }

    private void ensureSigmaColumn() {
        if (columns == null) {
            columns = new ArrayList<TableColumn>();
        }
        if (columns.isEmpty()) {
            columns.add(new TableColumn("Σ", ColumnType.COMPUTED, 0.0, rows));
            return;
        }
        TableColumn last = columns.get(columns.size() - 1);
        if (last.getType() != ColumnType.COMPUTED) {
            columns.add(new TableColumn("Σ", ColumnType.COMPUTED, 0.0, rows));
        }
    }

    private void allocStockStateCache() {
        int count = Math.max(0, getSectorColumnCount());
        stockLastVoltage = new double[count];
        stockLastCurrent = new double[count];
        stockCurSourceValue = new double[count];

        initializeStockStateFromInitialValues(false);
    }

    private void initializeStockStateFromInitialValues(boolean writeToVolts) {
        int count = Math.max(0, getSectorColumnCount());
        for (int col = 0; col < count; col++) {
            double initialStock = getInitialValue(col);
            stockLastVoltage[col] = initialStock;
            stockLastCurrent[col] = 0.0;
            stockCurSourceValue[col] = 0.0;
            if (writeToVolts && volts != null && col >= 0 && col < volts.length) {
                volts[col] = initialStock;
            }
        }
    }

    private int getSectorNode(int col) {
        if (nodes == null || col < 0 || col >= nodes.length) {
            return -1;
        }
        int node = nodes[col];
        return (node > 0) ? node : -1;
    }

    @Override
    void draw(Graphics g) {
        if (flowRenderer != null) {
            flowRenderer.draw(g);
        } else {
            super.draw(g);
        }
    }

    @Override
    int getPostCount() {
        return getCols();
    }

    @Override
    int getVoltageSourceCount() {
        return 0;
    }

    @Override
    int getInternalNodeCount() {
        return 0;
    }

    @Override
    public boolean nonLinear() {
        return true;
    }

    @Override
    public boolean hasGroundConnection(int n) {
        return false;
    }

    @Override
    public void reset() {
        super.reset();

        int sectorCount = getSectorColumnCount();
        if (stockLastVoltage == null || stockLastVoltage.length != sectorCount) {
            allocStockStateCache();
        } else {
            initializeStockStateFromInitialValues(true);
        }
    }

    @Override
    /**
     * Stamps the Phase-2 stock companion branch (to ground) for each sector column.
     */
    void stamp() {
        // Phase 2: STOCK behavior lives on the rendered Σ row (internal-only),
        // not in editable data rows. Each sector column gets a capacitor
        // companion branch to ground on the column node.
        int sectorCount = getSectorColumnCount();
        for (int col = 0; col < sectorCount; col++) {
            int node = getSectorNode(col);
            if (node <= 0) {
                continue;
            }
            double compR = getStockCompanionResistance(col);
            sim.stampResistor(node, 0, compR);
            sim.stampNonLinear(node);
            sim.stampRightSide(node);
        }
    }

    @Override
    /**
     * Prepares Norton current-source values from the previous stock state.
     */
    public void startIteration() {
        int sectorCount = getSectorColumnCount();
        for (int col = 0; col < sectorCount; col++) {
            double compR = getStockCompanionResistance(col);
            if (useBackwardEuler) {
                stockCurSourceValue[col] = -stockLastVoltage[col] / compR;
            } else {
                stockCurSourceValue[col] = -stockLastVoltage[col] / compR - stockLastCurrent[col];
            }
        }
    }

    @Override
    /**
     * Stamps the per-column stock Norton current sources for this sub-iteration.
     */
    public void doStep() {
        int sectorCount = getSectorColumnCount();
        for (int col = 0; col < sectorCount; col++) {
            int node = getSectorNode(col);
            if (node <= 0) {
                continue;
            }
            sim.stampCurrentSource(node, 0, stockCurSourceValue[col]);
        }
    }

    @Override
    /**
     * Commits stock state from solved node voltages and refreshes displayed cell values.
     */
    public void stepFinished() {
        updateStockStateAfterStep();
        updateCachedCellDisplayValues();
    }

    /**
     * Updates last stock voltage/current state from solved sector-node voltages.
     */
    private void updateStockStateAfterStep() {
        int sectorCount = getSectorColumnCount();
        for (int col = 0; col < sectorCount; col++) {
            int node = getSectorNode(col);
            if (node <= 0) {
                continue;
            }
            if (volts == null || col < 0 || col >= volts.length) {
                continue;
            }

            double v = volts[col];
            double compR = getStockCompanionResistance(col);

            stockLastVoltage[col] = v;
            stockLastCurrent[col] = v / compR + stockCurSourceValue[col];
        }
    }

    @Override
    protected boolean shouldRegisterStocks() {
        return false;
    }

    @Override
    void registerLabels() {
        int sectorCount = getSectorColumnCount();
        for (int col = 0; col < sectorCount; col++) {
            Point post = getPost(col);
            if (post == null) {
                continue;
            }

            // Phase 2: stock-column wiring by header names.
            // Header name maps to the column node regardless of cell labels.
            String headerName = getHeaderLabel(col);
            if (headerName != null) {
                LabeledNodeElm.preRegisterLabel(headerName, post);
            }

            for (int row = 0; row < rows; row++) {
                String label = getCellNodeLabel(row, col);
                if (label != null) {
                    LabeledNodeElm.preRegisterLabel(label, post);
                }
            }
        }
    }

    @Override
    void setNode(int p, int n) {
        super.setNode(p, n);

        if (p < 0 || p >= getSectorColumnCount()) {
            return;
        }

        Point post = getPost(p);
        if (post == null) {
            return;
        }

        String headerName = getHeaderLabel(p);
        if (headerName != null) {
            LabeledNodeElm.registerLabeledNode(headerName, post, n);
        }

        for (int row = 0; row < rows; row++) {
            String label = getCellNodeLabel(row, p);
            if (label != null) {
                LabeledNodeElm.registerLabeledNode(label, post, n);
            }
        }
    }

    private String getCellNodeLabel(int row, int col) {
        if (columns == null || col < 0 || col >= columns.size() || row < 0 || row >= rows) {
            return null;
        }
        String value = columns.get(col).getCellEquation(row);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "0".equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private void updateCachedCellDisplayValues() {
        if (columns == null) {
            return;
        }

        int sectorCount = getSectorColumnCount();
        for (int col = 0; col < sectorCount; col++) {
            TableColumn column = columns.get(col);
            if (column == null) {
                continue;
            }

            double columnSum = 0.0;
            for (int row = 0; row < rows; row++) {
                String label = getCellNodeLabel(row, col);
                double value = resolveLabelValue(label, col);
                column.setCachedCellValue(row, value);
                columnSum += value;
            }
            column.setLastSum(columnSum);
        }
    }

    private double resolveLabelValue(String label, int col) {
        if (label == null) {
            return 0.0;
        }

        if (showFlowValues) {
            Double publishedFlow = getPublishedFlowForLabel(label);
            if (publishedFlow != null) {
                return publishedFlow.doubleValue();
            }
            // TODO: JN I comment this out for now because the Published flows should be working

            // double eqTableFlow = getEquationTableFlowMagnitude(label);
            // if (eqTableFlow != 0.0) {
            //     return eqTableFlow;  
            // }

            // // Prefer branch-specific current for the exact referenced label.
            // LabeledNodeElm labeledElm = findLabeledNode(label);
            // if (labeledElm != null) {
            //     return Math.abs(labeledElm.getCurrent());
            // }

            // Integer labeledNode = LabeledNodeElm.getByName(label);
            // if (labeledNode != null && labeledNode.intValue() > 0) {
            //     return Math.abs(computeExternalCurrentForNode(labeledNode.intValue()));
            // }
            // if (nodes != null && col >= 0 && col < nodes.length && nodes[col] > 0) {
            //     return Math.abs(computeExternalCurrentForNode(nodes[col]));
            // }
            return 0.0;
        }

        Integer labeledNode = LabeledNodeElm.getByName(label);
        if (labeledNode != null && nodes != null && col >= 0 && col < nodes.length && labeledNode.intValue() == nodes[col]) {
            if (volts != null && col >= 0 && col < volts.length) {
                return volts[col];
            }
        }

        LabeledNodeElm labeledElm = findLabeledNode(label);
        if (labeledElm != null && labeledElm.volts != null && labeledElm.volts.length > 0) {
            return labeledElm.volts[0];
        }

        Double computed = ComputedValues.getComputedValue(label);
        return (computed != null) ? computed.doubleValue() : 0.0;
    }

    private Double getPublishedFlowForLabel(String label) {
        Double flowValue = ComputedValues.getComputedFlowValue(label);
        if (flowValue != null) {
            return flowValue.doubleValue();
        }
        return null;
    }

    private double getEquationTableFlowMagnitude(String label) {
        if (sim == null || sim.elmList == null || label == null || label.isEmpty()) {
            return 0.0;
        }

        double maxMagnitude = 0.0;
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm element = sim.elmList.elementAt(i);
            if (!(element instanceof EquationTableElm)) {
                continue;
            }

            EquationTableElm eq = (EquationTableElm) element;
            int rowCount = eq.getRowCount();
            for (int row = 0; row < rowCount; row++) {
                if (eq.getOutputMode(row) != EquationTableElm.RowOutputMode.FLOW_MODE) {
                    continue;
                }

                String source = eq.getOutputName(row);
                String target = eq.getTargetNodeName(row);
                boolean targetIsGround = (target == null || target.trim().isEmpty() ||
                    target.trim().equalsIgnoreCase("gnd"));

                boolean matchesSource = label.equals(source);
                boolean matchesTarget = (!targetIsGround && label.equals(target));
                if (matchesSource || matchesTarget) {
                    double magnitude = Math.abs(eq.getDisplayValue(row));
                    if (magnitude > maxMagnitude) {
                        maxMagnitude = magnitude;
                    }
                }
            }
        }

        return maxMagnitude;
    }

    private double computeExternalCurrentForNode(int nodeNum) {
        CircuitNode circuitNode;
        try {
            circuitNode = sim.getCircuitNode(nodeNum);
        } catch (Exception e) {
            return 0.0;
        }
        if (circuitNode == null || circuitNode.links == null) {
            return 0.0;
        }

        double currentIntoAttachedElements = 0.0;
        for (int i = 0; i < circuitNode.links.size(); i++) {
            CircuitNodeLink link = circuitNode.links.elementAt(i);
            if (link == null || link.elm == null) {
                continue;
            }
            if (link.elm == this || link.elm.isWireEquivalent()) {
                continue;
            }
            currentIntoAttachedElements += link.elm.getCurrentIntoNode(link.num);
        }
        return currentIntoAttachedElements;
    }

    private String getHeaderLabel(int col) {
        if (columns == null || col < 0 || col >= columns.size()) {
            return null;
        }
        String header = columns.get(col).getStockName();
        if (header == null) {
            return null;
        }
        String trimmed = header.trim();
        if (trimmed.isEmpty() || "Σ".equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private double getStockCapacitance(int col) {
        // Σ-row stock branch is internal-only; capacitance is global/default for now.
        return 1.0;
    }

    private double getStockCompanionResistance(int col) {
        double cap = getStockCapacitance(col);
        return useBackwardEuler ? (sim.timeStep / cap) : (sim.timeStep / (2 * cap));
    }

    public int getSectorColumnCount() {
        if (columns == null) {
            return 0;
        }
        int count = 0;
        for (TableColumn col : columns) {
            if (col.getType() == ColumnType.SECTOR) {
                count++;
            }
        }
        return count;
    }

    /**
     * @deprecated Prefer {@link #getStockVoltageTotal()} for explicit Phase-2 naming.
     */
    @Deprecated
    public double getGrandTotal() {
        return getStockVoltageTotal();
    }

    public double getStockColumnVoltage(int col) {
        if (volts == null || col < 0 || col >= volts.length) {
            return 0.0;
        }
        return volts[col];
    }

    public double getStockVoltageTotal() {
        int sectorCount = getSectorColumnCount();
        double total = 0.0;
        for (int col = 0; col < sectorCount; col++) {
            total += getStockColumnVoltage(col);
        }
        return total;
    }

    public boolean shouldHighlightImbalances() {
        return highlightImbalances;
    }

    public boolean isShowFlowValues() {
        return showFlowValues;
    }

    public boolean isUseBackwardEuler() {
        return useBackwardEuler;
    }

    public double getBalanceTolerance() {
        return balanceTolerance;
    }

    @Override
    String getChipName() {
        return "SFC Flow Table";
    }

    @Override
    void getInfo(String arr[]) {
        arr[0] = "SFC Flow Table (" + rows + " transactions × " + getSectorColumnCount() + " sectors)";
        arr[1] = "Column nodes obey KCL via labeled-node merging";
        if (arr.length > 2) {
            arr[2] = "Stock Voltage Total (Σ): " + CircuitElm.showFormat.format(getStockVoltageTotal());
        }
        if (arr.length > 3) {
            arr[3] = "Σ row = stock capacitors on column nodes";
        }
        if (arr.length > 4) {
            arr[4] = "Stock integration: " + (useBackwardEuler ? "Backward Euler" : "Trapezoidal");
        }
    }

    public static void resetCounter() {
        nextTableNumber = 1;
    }

    private static void updateFlowTableCounter(String title) {
        if (title != null && title.startsWith(TITLE_PREFIX + " ")) {
            try {
                String numStr = title.substring((TITLE_PREFIX + " ").length());
                int num = Integer.parseInt(numStr.trim());
                if (num >= nextTableNumber) {
                    nextTableNumber = num + 1;
                }
            } catch (NumberFormatException e) {
            }
        }
    }
}