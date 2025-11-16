/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.Label;
import com.lushprojects.circuitjs1.client.util.Locale;
import com.google.gwt.event.dom.client.MouseWheelEvent;
import com.google.gwt.event.dom.client.MouseWheelHandler;

/**
 * ODEElm - Simple ODE Calculator with Integration
 * 
 * This element calculates ordinary differential equations (ODEs) by:
 * 1. Evaluating a user-defined equation that can reference labeled nodes
 * 2. Integrating the result over time using numerical integration
 * 
 * Features:
 * - Single output pin providing the integrated value
 * - No input pins - equation references labeled nodes (like TableElm)
 * - User-editable equation string (e.g., "node1 + node2", "sin(t)", etc.)
 * - Initial value parameter for integration
 * - Compact visual representation
 * 
 * Integration equation: y[n+1] = y[n] + dt * f(t, labeled_nodes)
 * Where f(t, labeled_nodes) is the user's equation
 * 
 * Example uses:
 * - Equation: "rate" with labeled node "rate" = 5 -> integrates 5/sec
 * - Equation: "price - cost" -> integrates profit over time
 * - Equation: "-decay * stock" -> exponential decay
 * - Equation: "Predator_Births-(Predator*a)" with slider 'a' -> adjustable death rate
 */
class ODEElm extends ChipElm implements MouseWheelHandler {
    private String equationString = "1";    // User's equation string
    private Expr compiledExpr;              // Compiled expression
    private ExprState exprState;            // Expression evaluation state
    private double integratedValue;         // Current integration value
    private double initialValue = 0.0;      // Initial condition
    private double lastEquationValue = 0.0; // Last evaluated equation value (for convergence)
    
    // Slider support (variable 'a' in equation)
    Scrollbar slider;
    Label label;
    String sliderText;
    double sliderValue = 0.5;  // Default slider value
    double sliderMin = 0.0;    // Minimum slider value
    double sliderMax = 1.0;    // Maximum slider value
    boolean showPercentage = false; // Show slider value as percentage
    
    static final int FLAG_SHOW_PERCENTAGE = 1;
    
    // Constructor for menu creation
    public ODEElm(int xx, int yy) {
        super(xx, yy);
        noDiagonal = true;
        equationString = "1";
        initialValue = 0.0;
        sliderText = "Slider Value";
        sliderValue = 0.5;
        sliderMin = 0.0;
        sliderMax = 1.0;
        setupPins();
        parseEquation();
        initIntegration();
        createSlider();
    }
    
    // Constructor for file loading
    public ODEElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        noDiagonal = true;
        
        // Parse equation string (must be escaped)
        if (st.hasMoreTokens()) {
            equationString = CustomLogicModel.unescape(st.nextToken());
        } else {
            equationString = "1";
        }
        
        // Parse initial value
        if (st.hasMoreTokens()) {
            try {
                initialValue = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                initialValue = 0.0;
            }
        }
        
        // Parse slider parameters
        if (st.hasMoreTokens()) {
            try {
                sliderValue = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                sliderValue = 0.5;
            }
        }
        if (st.hasMoreTokens()) {
            try {
                sliderMin = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                sliderMin = 0.0;
            }
        }
        if (st.hasMoreTokens()) {
            try {
                sliderMax = Double.parseDouble(st.nextToken());
            } catch (Exception e) {
                sliderMax = 1.0;
            }
        }
        if (st.hasMoreTokens()) {
            sliderText = CustomLogicModel.unescape(st.nextToken());
        } else {
            sliderText = "Slider Value";
        }
        
        showPercentage = (f & FLAG_SHOW_PERCENTAGE) != 0;
        
        setupPins();
        parseEquation();
        initIntegration();
        createSlider();
    }
    
    void setupPins() {
        sizeX = 3;  // Wider
        sizeY = 1;  // Shorter
        pins = new Pin[1]; // Single output pin
        pins[0] = new Pin(0, SIDE_E, "");
        pins[0].output = true;
        allocNodes();
    }
    
    String getChipName() { return "ODE"; }
    
    int getDumpType() { return 261; } // Unique dump type
    
    int getPostCount() { return 1; } // Single output post
    
    int getVoltageSourceCount() { return 1; } // One voltage source for output
    
    boolean hasCurrentOutput() { return false; }
    
    private void initIntegration() {
        exprState = new ExprState(1); // 1 input variable 'a' for slider
        exprState.lastOutput = initialValue;
        integratedValue = initialValue;
    }
    
    void createSlider() {
        // Format the initial value text
        String valueStr;
        if (showPercentage) {
            double percentValue = sliderValue * 100;
            int intPart = (int) percentValue;
            int decimalPart = (int) ((percentValue - intPart) * 10);
            valueStr = intPart + "." + decimalPart + "%";
        } else {
            valueStr = getShortUnitText(sliderValue, "");
        }
        
        sim.addWidgetToVerticalPanel(label = new Label(Locale.LS(sliderText) + ": " + valueStr));
        label.addStyleName("topSpace");
        int value = (int) ((sliderValue - sliderMin) * 100 / (sliderMax - sliderMin));
        sim.addWidgetToVerticalPanel(slider = new Scrollbar(Scrollbar.HORIZONTAL, value, 1, 0, 101, 
                null, this));
    }
    
    void delete() {
        if (label != null)
            sim.removeWidgetFromVerticalPanel(label);
        if (slider != null)
            sim.removeWidgetFromVerticalPanel(slider);
        super.delete();
    }
    
    private void parseEquation() {
        try {
            ExprParser parser = new ExprParser(equationString);
            compiledExpr = parser.parseExpression();
            String err = parser.gotError();
            if (err != null) {
                CirSim.console("ODEElm: Parse error in equation '" + equationString + "': " + err);
                compiledExpr = null;
            }
        } catch (Exception e) {
            CirSim.console("ODEElm: Error parsing equation '" + equationString + "': " + e.getMessage());
            compiledExpr = null;
        }
    }
    
    // Get convergence limit (similar to GodlyTableElm)
    double getConvergeLimit() {
        double relativeTolerance;
        if (sim.subIterations < 10)
            relativeTolerance = 0.001;  // 0.1% for early iterations
        else if (sim.subIterations < 100)
            relativeTolerance = 0.01;   // 1% for mid iterations
        else
            relativeTolerance = 0.1;    // 10% for late iterations
        
        // Scale by magnitude
        double maxMagnitude = Math.max(1.0, Math.abs(integratedValue));
        maxMagnitude = Math.max(maxMagnitude, Math.abs(lastEquationValue));
        
        return maxMagnitude * relativeTolerance;
    }
    
    void stamp() {
        int vn = pins[0].voltSource + sim.nodeList.size();
        sim.stampNonLinear(vn);
        sim.stampVoltageSource(0, nodes[0], pins[0].voltSource);
    }
    
    void doStep() {
        // On first timestep, set initial value
        if (sim.timeStepCount == 0) {
            exprState.lastOutput = initialValue;
            integratedValue = initialValue;
        }
        
        // Update slider value from UI
        if (slider != null) {
            double oldSliderValue = sliderValue;
            sliderValue = slider.getValue() * (sliderMax - sliderMin) / 100.0 + sliderMin;
            
            // Update label if value changed
            if (label != null && Math.abs(oldSliderValue - sliderValue) > 1e-10) {
                String valueStr;
                if (showPercentage) {
                    double percentValue = sliderValue * 100;
                    int intPart = (int) percentValue;
                    int decimalPart = (int) ((percentValue - intPart) * 10);
                    valueStr = intPart + "." + decimalPart + "%";
                } else {
                    valueStr = getShortUnitText(sliderValue, "");
                }
                label.setText(Locale.LS(sliderText) + ": " + valueStr);
            }
        }
        
        int vn = pins[0].voltSource + sim.nodeList.size();
        
        if (compiledExpr != null) {
            // Set slider value as variable 'a' in expression
            exprState.values[0] = sliderValue;
            
            // Evaluate equation to get derivative f(t, labeled_nodes, a)
            exprState.t = sim.t;
            double equationValue = compiledExpr.eval(exprState);
            
            // Check equation convergence
            double convergeLimit = getConvergeLimit();
            if (Math.abs(equationValue - lastEquationValue) > convergeLimit) {
                sim.converged = false;
            }
            lastEquationValue = equationValue;
            
            // Perform integration: y[n+1] = y[n] + dt * f(t)
            integratedValue = exprState.lastOutput + sim.timeStep * equationValue;
            
            // Check output voltage convergence
            double outputVoltage = volts[0];
            double voltageDiff = Math.abs(outputVoltage - integratedValue);
            double threshold = Math.max(Math.abs(integratedValue) * 0.01, 1e-6);
            if (voltageDiff > threshold && sim.subIterations < 100) {
                sim.converged = false;
            }
            
            // Stamp the right side with the integrated value
            sim.stampRightSide(vn, integratedValue);
        }
    }
    
    void stepFinished() {
        // Update integration state for next timestep
        if (exprState != null) {
            exprState.lastOutput = integratedValue;
        }
    }
    
    @Override
    public void reset() {
        super.reset();
        // Reset integration to initial condition
        if (exprState != null) {
            exprState.reset();
            exprState.lastOutput = initialValue;
        }
        integratedValue = initialValue;
        lastEquationValue = 0.0;
    }
    
    void setCurrent(int vn, double c) {
        if (pins[0].voltSource == vn) {
            pins[0].current = c;
        }
    }
    
    @Override
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.dump());
        sb.append(" ").append(CustomLogicModel.escape(equationString));
        sb.append(" ").append(initialValue);
        sb.append(" ").append(sliderValue);
        sb.append(" ").append(sliderMin);
        sb.append(" ").append(sliderMax);
        sb.append(" ").append(CustomLogicModel.escape(sliderText));
        return sb.toString();
    }
    
    void draw(Graphics g) {
        drawChip(g);
        
        // Draw integral symbol with initial value as subscript in box
        int mid_x = (rectPointsX[0] + rectPointsX[1] + rectPointsX[2] + rectPointsX[3]) / 4;
        int mid_y = (rectPointsY[0] + rectPointsY[1] + rectPointsY[2] + rectPointsY[3]) / 4;
        
        boolean selected = needsHighlight();
        
        // Draw integral symbol
        Font mainFont = new Font("SansSerif", selected ? Font.BOLD : 0, 18);
        g.setFont(mainFont);
        g.setColor(selected ? selectColor : whiteColor);
        drawCenteredText(g, "∫", mid_x - 8, mid_y, true);
        g.restore();
        
        // Draw initial value as subscript (smaller, lower, to the right)
        Font subscriptFont = new Font("SansSerif", 0, 10);
        g.setFont(subscriptFont);
        g.setColor(selected ? selectColor : whiteColor);
        String initStr = getShortUnitText(initialValue, "");
        drawCenteredText(g, initStr, mid_x + 8, mid_y + 6, true);
        g.restore();
        
        // Draw full equation below the box (not truncated)
        // Replace 'a' with actual slider value in display
        String displayEquation = equationString;
        if (slider != null) {
            sliderValue = slider.getValue() * (sliderMax - sliderMin) / 100.0 + sliderMin;
            String valueStr = getShortUnitText(sliderValue, "");
            displayEquation = displayEquation.replaceAll("\\ba\\b", valueStr);
        }
        
        int bottom_y = Math.max(rectPointsY[0], Math.max(rectPointsY[1], 
                                Math.max(rectPointsY[2], rectPointsY[3])));
        Font smallFont = new Font("SansSerif", 0, 10);
        g.setFont(smallFont);
        g.setColor(selected ? selectColor : whiteColor);
        drawCenteredText(g, "d/dt=" + displayEquation, mid_x, bottom_y + 12, true);
        g.restore();
        
        // Draw slider value text below equation
        String sliderValueText;
        if (showPercentage) {
            double percentValue = sliderValue * 100;
            // Manual formatting for percentage (GWT doesn't support String.format)
            int intPart = (int) percentValue;
            int decimalPart = (int) ((percentValue - intPart) * 10);
            sliderValueText = sliderText + " = " + intPart + "." + decimalPart + "%";
        } else {
            sliderValueText = sliderText + " = " + getShortUnitText(sliderValue, "");
        }
        g.setFont(smallFont);
        g.setColor(selected ? selectColor : whiteColor);
        drawCenteredText(g, sliderValueText, mid_x, bottom_y + 24, true);
        g.restore();
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Equation (d/dt)", 0, -1, -1);
            ei.text = equationString;
            ei.disallowSliders();
            return ei;
        }
        if (n == 1) {
            EditInfo ei = new EditInfo("Initial Value y(0)", initialValue);
            return ei;
        }
        if (n == 2) {
            EditInfo ei = new EditInfo("Slider Initial Value", sliderValue, sliderMin, sliderMax);
            return ei;
        }
        if (n == 3) {
            EditInfo ei = new EditInfo("Slider Min", sliderMin);
            return ei;
        }
        if (n == 4) {
            EditInfo ei = new EditInfo("Slider Max", sliderMax);
            return ei;
        }
        if (n == 5) {
            EditInfo ei = new EditInfo("Slider Text (value a = equation)", 0, -1, -1);
            ei.text = sliderText;
            return ei;
        }
        if (n == 6) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Show as Percentage", showPercentage);
            return ei;
        }
        return null;
    }
    
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            equationString = ei.textf.getText();
            parseEquation();
        }
        if (n == 1) {
            initialValue = ei.value;
            // Reset integration to new initial value
            if (exprState != null) {
                exprState.lastOutput = initialValue;
            }
            integratedValue = initialValue;
        }
        if (n == 2) {
            sliderValue = ei.value;
            // Update the slider position
            if (slider != null) {
                int pos = (int) ((sliderValue - sliderMin) * 100 / (sliderMax - sliderMin));
                slider.setValue(pos);
            }
        }
        if (n == 3) {
            sliderMin = ei.value;
            // Update slider position if needed
            if (slider != null) {
                int pos = (int) ((sliderValue - sliderMin) * 100 / (sliderMax - sliderMin));
                slider.setValue(pos);
            }
        }
        if (n == 4) {
            sliderMax = ei.value;
            // Update slider position if needed
            if (slider != null) {
                int pos = (int) ((sliderValue - sliderMin) * 100 / (sliderMax - sliderMin));
                slider.setValue(pos);
            }
        }
        if (n == 5) {
            sliderText = ei.textf.getText();
            if (label != null) {
                label.setText(Locale.LS(sliderText));
                sim.setiFrameHeight();
            }
        }
        if (n == 6) {
            showPercentage = ei.checkbox.getState();
            // Update flags
            flags = showPercentage ? (flags | FLAG_SHOW_PERCENTAGE) : (flags & ~FLAG_SHOW_PERCENTAGE);
        }
    }
    
    @Override
    void getInfo(String arr[]) {
        arr[0] = "ODE Integrator";
        arr[1] = "Equation: d/dt = " + equationString;
        arr[2] = "Initial Value: " + getVoltageText(initialValue);
        arr[3] = "Slider 'a': " + getVoltageText(sliderValue);
        arr[4] = "Current Output: " + getVoltageText(integratedValue);
        arr[5] = "Time: " + getUnitText(sim.t, "s");
    }
    
    void setMouseElm(boolean v) {
        super.setMouseElm(v);
        if (slider != null)
            slider.draw();
    }
    
    public void onMouseWheel(MouseWheelEvent e) {
        if (slider != null)
            slider.onMouseWheel(e);
    }
    
    // Custom slider text formatting for Adjustable class
    public String getSliderUnitText(int n, EditInfo ei, double value) {
        // Only format the slider value field (n == 2)
        if (n != 2)
            return null;
        
        if (showPercentage) {
            double percentValue = value * 100;
            // Manual formatting for percentage (GWT doesn't support String.format)
            int intPart = (int) percentValue;
            int decimalPart = (int) ((percentValue - intPart) * 10);
            return intPart + "." + decimalPart + "%";
        } else {
            return getShortUnitText(value, "");
        }
    }
}
