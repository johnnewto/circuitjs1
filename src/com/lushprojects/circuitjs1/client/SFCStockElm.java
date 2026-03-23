/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * SFCStockElm - Stock-Flow Consistent Stock Element
 * 
 * Represents an economic stock (Household savings, Firm deposits, Bank reserves, etc.)
 * using the current-as-flow MNA approach.
 * 
 * <h3>Physical Analogy:</h3>
 * The sector is modeled as a capacitor to ground:
 * <ul>
 *   <li>Voltage at the node = Stock level (account balance, inventory, etc.)</li>
 *   <li>Current into the node = Net flow into the sector</li>
 *   <li>Capacitance = How responsive stock is to flows (typically 1.0)</li>
 * </ul>
 * 
 * <h3>MNA Formulation:</h3>
 * The capacitor companion model (trapezoidal/backward Euler) ensures:
 * <pre>
 *   C * dV/dt = I_net
 *   ∫I_net dt = C * ΔV (stock change = integral of net flow)
 * </pre>
 * 
 * <h3>SFC Interpretation:</h3>
 * KCL at the sector node automatically enforces:
 * <pre>
 *   ΣInflows - ΣOutflows = dStock/dt
 * </pre>
 * This IS the stock-flow consistent accounting identity!
 * 
 * <h3>Features:</h3>
 * <ul>
 *   <li>Named stock for reference in flow equations</li>
 *   <li>Configurable initial stock value</li>
 *   <li>Visual display of current stock level</li>
 *   <li>Connects to SFCFlowElm for inter-stock transactions</li>
 * </ul>
 * 
 * @see SFCFlowElm For defining flows between sectors
 */
public class SFCStockElm extends CircuitElm {
    
    /** Stock name (e.g., "H_h", "H_f", "H_b", "H_g") */
    private String stockName = "Stock";
    
    /** Initial stock value (balance at t=0) */
    private double initialStock = 0;
    
    /** Stock capacitance - typically 1.0 for unit stock */
    private double stockCapacitance = 1.0;
    
    /** Companion model resistance (computed from capacitance and timestep) */
    private double compResistance;
    
    /** Current stock value (voltage at node) */
    private double stockValue;
    
    /** Current source value for companion model */
    private double curSourceValue;
    
    /** Net current into sector (computed after solve) */
    private double netCurrent;
    
    /** Flag for trapezoidal vs backward Euler integration */
    private static final int FLAG_BACK_EULER = 2;
    
    /** Label offset for drawing (unused but kept for future extension) */
    @SuppressWarnings("unused")
    private Point labelPoint;
    @SuppressWarnings("unused")
    private Point stockDisplayPoint;
    
    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================
    
    /**
     * Constructor for creating element from UI menu.
     */
    public SFCStockElm(int xx, int yy) {
        super(xx, yy);
        stockName = "Stock";
        initialStock = 0;
        stockCapacitance = 1.0;
    }
    
    /**
     * Constructor for loading from file.
     */
    public SFCStockElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        
        if (st.hasMoreTokens()) {
            stockName = CustomLogicModel.unescape(st.nextToken());
        }
        if (st.hasMoreTokens()) {
            try {
                initialStock = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                initialStock = 0;
            }
        }
        if (st.hasMoreTokens()) {
            try {
                stockCapacitance = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                stockCapacitance = 1.0;
            }
        }
    }
    
    // =========================================================================
    // ELEMENT IDENTIFICATION
    // =========================================================================
    
    @Override
    protected int getDumpType() { return 268; }  // Unique element type
    
    @Override
    protected String dump() {
        return super.dump() + " " + 
               CustomLogicModel.escape(stockName) + " " + 
               initialStock + " " + 
               stockCapacitance;
    }
    
    // =========================================================================
    // GEOMETRY
    // =========================================================================
    
    @Override
    protected int getPostCount() { return 1; }  // Single node representing the stock
    
    @Override
    void registerLabels() {
        // Pre-register stock name in the labeled node system BEFORE wire closure.
        // This allows LabeledNodeElm elements with the same name to merge their
        // post with this element's post, so they share the same MNA node.
        if (stockName != null && !stockName.isEmpty()) {
            LabeledNodeElm.preRegisterLabel(stockName, point1);
        }
    }
    
    @Override
    void setNode(int p, int n) {
        super.setNode(p, n);
        
        // Update the labeled node entry with the assigned MNA node number.
        // Needed for LabeledNodeElm.getByName() to return this node,
        // especially when no LabeledNodeElm exists for this stock name.
        if (p == 0 && stockName != null && !stockName.isEmpty()) {
            LabeledNodeElm.registerLabeledNode(stockName, point1, n);
        }
    }
    
    @Override
    protected void setPoints() {
        super.setPoints();
        // Set up label position above the post
        labelPoint = new Point(x, y - 20);
        stockDisplayPoint = new Point(x, y + 20);
    }
    
    // =========================================================================
    // MNA CIRCUIT INTERFACE
    // =========================================================================
    
    /**
     * Check if using trapezoidal integration (more accurate but can oscillate).
     */
    boolean isTrapezoidal() { 
        return (flags & FLAG_BACK_EULER) == 0; 
    }
    
    @Override
    protected void reset() {
        super.reset();
        stockValue = initialStock;
        volts[0] = initialStock;
        curSourceValue = 0;
        netCurrent = 0;
        compResistance = 0;  // Will be set in stamp()
    }
    
    @Override
    protected void stamp() {
        double dt = getSimulationContext().getTimeStep();
        // Capacitor companion model
        // Trapezoidal: compResistance = dt/(2*C)
        // Backward Euler: compResistance = dt/C
        if (isTrapezoidal()) {
            compResistance = dt / (2 * stockCapacitance);
        } else {
            compResistance = dt / stockCapacitance;
        }
        
        if (sim.dcAnalysisFlag) {
            // For DC analysis, treat as open circuit with initial voltage
            // Stamp high resistance to maintain voltage
            sim.stampResistor(nodes[0], 0, 1e8);
            return;
        }
        
        // Stamp companion resistor
        sim.stampResistor(nodes[0], 0, compResistance);
        
        // Mark right side as changing in doStep
        sim.stampRightSide(nodes[0]);
    }
    
    @Override
    void startIteration() {
        // Guard against division by zero before first stamp()
        if (compResistance <= 0) {
            curSourceValue = 0;
            return;
        }
        
        // Compute current source for companion model at start of timestep
        if (isTrapezoidal()) {
            // Trapezoidal: curSource = -V_old/R - I_old
            curSourceValue = -stockValue / compResistance - netCurrent;
        } else {
            // Backward Euler: curSource = -V_old/R
            curSourceValue = -stockValue / compResistance;
        }
    }
    
    @Override
    protected void doStep() {
        if (sim.dcAnalysisFlag) {
            return;
        }
        
        // Stamp current source for companion model
        sim.stampCurrentSource(nodes[0], 0, curSourceValue);
    }
    
    @Override
    protected void stepFinished() {
        // Update stock value from solved voltage
        stockValue = volts[0];
        
        // Calculate net current
        calculateCurrent();
        
        // Register stock value for use by other elements
        ComputedValues.setComputedValue(stockName, stockValue, this);
        ComputedValues.markComputedThisStep(stockName);
    }
    
    @Override
    void calculateCurrent() {
        if (compResistance > 0) {
            // Current through companion resistor
            netCurrent = volts[0] / compResistance + curSourceValue;
        }
    }
    
    @Override
    double getCurrent() {
        return netCurrent;
    }
    
    @Override
    protected double getCurrentIntoNode(int n) {
        // Current into the stock node (positive = inflow)
        return -netCurrent;
    }
    
    @Override
    double getVoltageDiff() {
        // Single-post element - voltage relative to ground
        return volts[0];
    }
    
    // =========================================================================
    // DRAWING
    // =========================================================================
    
    @Override
    protected void draw(Graphics g) {
        // Set color based on stock value
        setVoltageColor(g, volts[0]);
        
        // Draw a circle representing the stock
        int radius = 15;
        // Draw stock outline (use context.arc for stroke)
        setVoltageColor(g, volts[0]);
        g.context.beginPath();
        g.context.arc(x, y, radius, 0, 2 * Math.PI);
        g.context.stroke();
        
        // Draw capacitor symbol inside (showing stock nature)
        int capY = y;
        int capWidth = 8;
        g.drawLine(x - capWidth, capY - 3, x + capWidth, capY - 3);
        g.drawLine(x - capWidth, capY + 3, x + capWidth, capY + 3);
        
        // Draw stock name above
        g.setColor(whiteColor);
        drawCenteredText(g, stockName, x, y - 25, true);
        
        // Draw stock value below
        String stockText = getVoltageText(stockValue);
        drawCenteredText(g, stockText, x, y + 25, true);
        
        // Draw the post
        drawPost(g, point1);
        
        // Draw current dots (showing flow)
        if (sim.dragElm != this) {
            updateDotCount();
            // Draw dots moving toward/away from stock based on current direction
        }
        
        // Draw net flow indicator
        if (Math.abs(netCurrent) > 1e-9) {
            String flowDir = netCurrent > 0 ? "↓" : "↑";
            String flowText = flowDir + " " + getCurrentText(Math.abs(netCurrent));
            g.setColor(netCurrent > 0 ? Color.green : Color.red);
            drawCenteredText(g, flowText, x, y + 40, true);
        }
        
        setBbox(x - radius - 10, y - radius - 30, x + radius + 10, y + radius + 50);
    }
    
    // =========================================================================
    // INFO DISPLAY
    // =========================================================================
    
    @Override
    protected void getInfo(String arr[]) {
        arr[0] = "SFC Stock: " + stockName;
        arr[1] = "Stock = " + getVoltageText(stockValue);
        arr[2] = "Net Flow = " + getCurrentText(netCurrent);
        arr[3] = "Initial = " + getVoltageText(initialStock);
        arr[4] = "C = " + getUnitText(stockCapacitance, "");
    }
    
    // =========================================================================
    // EDIT INTERFACE
    // =========================================================================
    
    @Override
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Stock Name", 0, -1, -1);
            ei.text = stockName;
            return ei;
        }
        if (n == 1) {
            return new EditInfo("Initial Stock", initialStock, -1e6, 1e6);
        }
        if (n == 2) {
            return new EditInfo("Capacitance", stockCapacitance, 0.001, 1000);
        }
        if (n == 3) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Trapezoidal Integration", isTrapezoidal());
            return ei;
        }
        return null;
    }
    
    @Override
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            stockName = ei.textf.getText();
        }
        if (n == 1) {
            initialStock = ei.value;
        }
        if (n == 2 && ei.value > 0) {
            stockCapacitance = ei.value;
        }
        if (n == 3) {
            if (ei.checkbox.getState()) {
                flags &= ~FLAG_BACK_EULER;
            } else {
                flags |= FLAG_BACK_EULER;
            }
        }
    }
    
    // =========================================================================
    // ACCESSORS
    // =========================================================================
    
    /** Get stock name for flow equation references */
    public String getStockName() { return stockName; }
    
    /** Get current stock value */
    public double getStockValue() { return stockValue; }
    
    /** Get net current (flow) into stock */
    public double getNetCurrent() { return netCurrent; }
}
