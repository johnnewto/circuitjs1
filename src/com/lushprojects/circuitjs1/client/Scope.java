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

import com.google.gwt.event.dom.client.MouseWheelEvent;
import com.google.gwt.storage.client.Storage;
import com.lushprojects.circuitjs1.client.util.Locale;

import java.util.Vector;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context2d;

// plot of single value on a scope
/**
 * Represents a single plot within a scope, tracking values over time.
 * Each plot can display voltage, current, power, or resistance for a circuit element.
 */
class ScopePlot {
    // Plot flags
    static final int FLAG_AC = 1;
    
    // Default values
    private static final double DEFAULT_MAN_SCALE = 1.0;
    private static final double DEFAULT_AC_ALPHA = 0.9999;
    private static final double AC_ALPHA_TIME_CONSTANT = 1.15;
    
    // Data storage
    double minValues[], maxValues[];
    int scopePointCount;
    int ptr; // Pointer to the current sample in circular buffer
    
    // Plot configuration
    int value; // The property being shown (e.g., VAL_CURRENT, VAL_VOLTAGE)
    int scopePlotSpeed; // In sim timestep units per pixel
    int units; // Display units (UNITS_V, UNITS_A, UNITS_W, UNITS_OHMS)
    
    // State tracking
    double lastUpdateTime;
    double lastValue;
    String color;
    CircuitElm elm;
    
    // History buffers for drawFromZero mode (not circular, grows linearly)
    double historyMinValues[], historyMaxValues[];
    
    // Manual scale settings
    // Has a manual scale in "/div" format been set by the user?
    // If false, scale is inferred from "MaxValue" format or auto-calculated
    boolean manScaleSet = false; 
    double manScale = DEFAULT_MAN_SCALE; // Units per division
    int manVPosition = 0; // 0 is center of screen. +V_POSITION_STEPS/2 is top of screen
    
    // Display parameters
    double gridMult;
    double plotOffset;
    
    // AC coupling filter
    boolean acCoupled = false;
    double acAlpha = DEFAULT_AC_ALPHA; // Filter coefficient for AC coupling (y[i] = alpha * (y[i-1] + x[i] - x[i-1]))
    double acLastOut = 0; // Store y[i-1] term for AC coupling filter
    
    /**
     * Creates a new ScopePlot for the given element and units.
     * @param e The circuit element to monitor
     * @param u The units for display (UNITS_V, UNITS_A, etc.)
     */
    ScopePlot(CircuitElm e, int u) {
	elm = e;
	units = u;
    }
    
    /**
     * Creates a new ScopePlot with manual scale settings.
     * @param e The circuit element to monitor
     * @param u The units for display
     * @param v The value type to display (VAL_VOLTAGE, VAL_CURRENT, etc.)
     * @param manS Manual scale value (units per division)
     */
    ScopePlot(CircuitElm e, int u, int v, double manS) {
	elm = e;
	units = u;
	value = v;
	manScale = manS;
	// Ohms can only be positive, so move the v position to the bottom.
	// Power can be negative for caps and inductors, but still move to the bottom (for backward compatibility)
	if (units == Scope.UNITS_OHMS || units == Scope.UNITS_W)
	    manVPosition = -Scope.V_POSITION_STEPS / 2;
    }

    /**
     * Returns the starting index in the circular buffer for displaying the given width.
     * @param w Width in pixels to display
     * @return Starting index in circular buffer
     */
    int startIndex(int w) {
	return ptr + scopePointCount - w; 
    }
    
    /**
     * Resets the plot buffers and speed settings.
     * @param spc New scope point count (buffer size)
     * @param sp New speed (timestep units per pixel)
     * @param full If true, discard all old data; if false, preserve what fits
     */
    void reset(int spc, int sp, boolean full) {
	int oldSpc = scopePointCount;
	scopePointCount = spc;
	if (scopePlotSpeed != sp)
	    oldSpc = 0; // throw away old data
	scopePlotSpeed = sp;
	// Adjust the time constant of the AC coupled filter in proportion to the number of samples
	// we are seeing on the scope (if my maths is right). The constant is empirically determined
	acAlpha = 1.0 - 1.0 / (AC_ALPHA_TIME_CONSTANT * scopePlotSpeed * scopePointCount);
	double oldMin[] = minValues;
	double oldMax[] = maxValues;
    	minValues = new double[scopePointCount];
    	maxValues = new double[scopePointCount];
    	if (oldMin != null && !full) {
    	    // preserve old data if possible
    	    int i;
    	    for (i = 0; i != scopePointCount && i != oldSpc; i++) {
    		int i1 = (-i) & (scopePointCount-1);
    		int i2 = (ptr-i) & (oldSpc-1);
    		minValues[i1] = oldMin[i2];
    		maxValues[i1] = oldMax[i2];
    	    }
    	} else
    	    lastUpdateTime = CirSim.theSim.t;
    	ptr = 0;
    }

    /**
     * Records a timestep sample for this plot.
     * Updates min/max values and applies AC coupling if enabled.
     */
    void timeStep() {
	if (elm == null)
		return;
	
	double v = elm.getScopeValue(value);
	
	// AC coupling filter: 1st order IIR high pass filter
	// Formula: y[i] = alpha × (y[i-1] + x[i] - x[i-1])
	// We calculate for all iterations (even DC coupled) to prime the data in case they switch to AC later
	double newAcOut = acAlpha * (acLastOut + v - lastValue);
	lastValue = v;
	acLastOut = newAcOut;
	
	if (isAcCoupled())
	    v = newAcOut;
	
	// Update min/max for current sample point
	if (v < minValues[ptr])
		minValues[ptr] = v;
	if (v > maxValues[ptr])
		maxValues[ptr] = v;
	
	// Advance to next sample point if enough time has elapsed
	if (CirSim.theSim.t - lastUpdateTime >= CirSim.theSim.maxTimeStep * scopePlotSpeed) {
	    ptr = (ptr + 1) & (scopePointCount - 1);
	    minValues[ptr] = maxValues[ptr] = v;
	    lastUpdateTime += CirSim.theSim.maxTimeStep * scopePlotSpeed;
	}
    }
    
    String getUnitText(double v) {
	switch (units) {
	case Scope.UNITS_V:
	    return CircuitElm.getVoltageText(v);
	case Scope.UNITS_A:
	    return CircuitElm.getCurrentText(v);
	case Scope.UNITS_OHMS:
	    return CircuitElm.getUnitText(v, Locale.ohmString);
	case Scope.UNITS_W:
	    return CircuitElm.getUnitText(v, "W");
	}
	return null;
    }

    // Color palette for multiple plots
    static final String colors[] = {
	    "#FF0000", "#FF8000", "#FF00FF", "#7F00FF",
	    "#0000FF", "#0080FF", "#FFFF00", "#00FFFF", 
    };
    
    /**
     * Assigns a color to this plot based on its type and count.
     * @param count Plot count (0 for default color based on units, >0 for cycling through palette)
     */
    void assignColor(int count) {
	if (count > 0) {
	    color = colors[(count - 1) % colors.length];
	    return;
	}
	// Default colors based on units
	switch (units) {
	case Scope.UNITS_V:
	    color = CircuitElm.positiveColor.getHexValue();
	    break;
	case Scope.UNITS_A:
	    color = (CirSim.theSim.printableCheckItem.getState()) ? "#A0A000" : "#FFFF00";
	    break;
	default:
	    color = (CirSim.theSim.printableCheckItem.getState()) ? "#000000" : "#FFFFFF";
	    break;
	}
    }
    
    /**
     * Enables or disables AC coupling for this plot.
     * AC coupling only works for voltage plots.
     * @param b true to enable AC coupling
     */
    void setAcCoupled(boolean b) {
	if (canAcCouple()) {
	    acCoupled = b;
	}
	else
	    acCoupled = false;
    }
    
    /**
     * Checks if AC coupling is allowed for this plot.
     * @return true if this plot displays voltage
     */
    boolean canAcCouple() {
	return units == Scope.UNITS_V;
    }
    
    /**
     * Checks if AC coupling is currently enabled.
     * @return true if AC coupled
     */
    boolean isAcCoupled() {
	return acCoupled;
    }
    
    /**
     * Returns serialization flags for this plot.
     * @return Bit flags representing plot state
     */
    int getPlotFlags() {
	return (acCoupled ? FLAG_AC : 0);
    }
}

/**
 * Scope class - displays time-series waveforms and XY plots of circuit values.
 * Supports multiple modes: standard scrolling view, draw-from-zero mode, 2D plots, FFT analysis.
 */
class Scope {
    // ====================
    // FLAG CONSTANTS
    // ====================
    // Dump format flags (for serialization)
    final int FLAG_YELM = 32;
    final int FLAG_IVALUE = 2048;
    final int FLAG_PLOTS = 4096; // New-style dump with multiple plots
    final int FLAG_PERPLOTFLAGS = 1<<18; // Per-plot flags in dump
    final int FLAG_PERPLOT_MAN_SCALE = 1<<19; // Manual scale included in each plot
    final int FLAG_MAN_SCALE = 16;
    final int FLAG_DIVISIONS = 1<<21; // Dump manDivisions
    final int FLAG_DRAW_FROM_ZERO = 1<<22; // Draw from t=0 on left, growing right
    final int FLAG_AUTO_SCALE_TIME = 1<<23; // Auto-adjust time scale when reaching edge
    final int FLAG_MAX_SCALE_LIMITS = 1<<24; // Max scale limits present
    
    // ====================
    // VALUE TYPE CONSTANTS
    // ====================
    static final int VAL_VOLTAGE = 0;
    static final int VAL_POWER_OLD = 1; // Legacy power value (conflicts with VAL_IB)
    static final int VAL_R = 2; // Resistance
    static final int VAL_CURRENT = 3;
    static final int VAL_POWER = 7;
    // Transistor-specific values
    static final int VAL_IB = 1;
    static final int VAL_IC = 2;
    static final int VAL_IE = 3;
    static final int VAL_VBE = 4;
    static final int VAL_VBC = 5;
    static final int VAL_VCE = 6;
    
    // ====================
    // UNIT CONSTANTS
    // ====================
    static final int UNITS_V = 0; // Volts
    static final int UNITS_A = 1; // Amperes
    static final int UNITS_W = 2; // Watts
    static final int UNITS_OHMS = 3; // Ohms
    static final int UNITS_COUNT = 4;
    
    // ====================
    // DISPLAY CONSTANTS
    // ====================
    static final double multa[] = {2.0, 2.5, 2.0}; // Grid scaling multipliers
    static final int V_POSITION_STEPS = 200; // Vertical position adjustment range
    static final double MIN_MAN_SCALE = 1e-9; // Minimum manual scale value
    static final int SETTINGS_WHEEL_SIZE = 36; // Size of settings wheel in pixels
    static final int SETTINGS_WHEEL_MARGIN = 100; // Minimum size needed to show settings wheel
    static final int SHADOW_OFFSET = 4; // Shadow offset in pixels
    static final int SHADOW_BLUR = 8; // Shadow blur radius
    static final int MIN_PIXEL_SPACING = 20; // Minimum spacing between gridlines in pixels
    
    // ====================
    // INSTANCE VARIABLES - Data
    // ====================
    int scopePointCount = 128; // Size of circular buffer (power of 2)
    FFT fft;
    int position; // Position in scope stack
    int speed; // Sim timestep units per pixel
    int stackCount; // Number of scopes in this column
    String text; // Custom label text
    String title; // Custom title text (displayed at top center)
    Rectangle rect;
    
    // ====================
    // INSTANCE VARIABLES - Display Settings
    // ====================
    private boolean manualScale;
    boolean showI, showV, showScale, showMax, showMin, showFreq;
    boolean plot2d; // 2D plot mode
    boolean plotXY; // XY plot mode
    boolean maxScale; // Auto-scale to maximum value
    boolean logSpectrum; // Logarithmic FFT display
    boolean showFFT, showNegative, showRMS, showAverage, showDutyCycle, showElmInfo;
    
    // Maximum scale limits (null = no limit)
    Double maxScaleLimit[] = new Double[UNITS_COUNT];
    
    // Draw-from-zero mode variables
    boolean drawFromZero; // Draw from t=0 on left, growing right
    boolean autoScaleTime; // Auto-adjust time scale when reaching edge
    double startTime; // Simulation time when scope was reset (for drawFromZero mode)
    int historySize; // Current number of samples in history buffers
    int historyCapacity; // Maximum capacity of history buffers before downsampling
    double historySampleInterval; // Time interval between history samples (increases with downsampling)
    
    // ====================
    // INSTANCE VARIABLES - Working Data
    // ====================
    Vector<ScopePlot> plots, visiblePlots;
    int draw_ox, draw_oy; // 2D plot drawing coordinates
    CirSim sim;
    Canvas imageCanvas; // Canvas for 2D plots
    Context2d imageContext;
    int alphaCounter = 0; // Counter for 2D plot fade effect
    double scopeTimeStep; // Check if sim timestep has changed
    double scale[]; // Max value to scale the display - indexed by UNITS_*
    boolean reduceRange[];
    double scaleX, scaleY;  // For X-Y plots
    double wheelDeltaY; // Mouse wheel accumulator
    int selectedPlot; // Currently selected plot index
    ScopePropertiesDialog properties;
    String curColor, voltColor; // Legacy color variables
    double gridStepX, gridStepY; // Grid spacing
    double displayGridStepX; // Grid spacing for display text (saved from first plot)
    double maxValue, minValue; // Calculated from visible data
    int manDivisions; // Number of vertical divisions when in manual mode
    static int lastManDivisions;
    boolean drawGridLines; // Flag to draw gridlines once per frame
    boolean somethingSelected; // Is one of our plots selected?
    
    // ====================
    // ACTION MARKER HOVER
    // ====================
    int hoveredActionIndex = -1; // Index of currently hovered action marker (-1 if none)
    int lastHoveredActionIndex = -1; // Previous hovered action index for detecting changes
    int lastDisplayedActionIndex = -1; // Last action annotation displayed (persists)
    String lastDisplayedActionText = null; // postText of last displayed action (to compare with triggered actions)
    boolean lastDisplayedWasHover = false; // True if last displayed came from hover (not trigger)
    int lastLoggedActionIndex = -1; // Last action we logged (to reduce logging frequency)
    int mouseX = -1, mouseY = -1; // Last mouse position in scope coordinates
    boolean mouseButtonDown = false; // Track if mouse button is pressed
    java.util.HashMap<Integer, Integer> actionVerticalPositions = new java.util.HashMap<Integer, Integer>(); // Stored Y positions for each action ID
    
    // ====================
    // STATIC VARIABLES - Cursor Tracking
    // ====================
    static double cursorTime;
    static int cursorUnits;
    static Scope cursorScope;
    
    /**
     * Creates a new Scope instance.
     * @param s The simulator instance
     */
    Scope(CirSim s) {
    	sim = s;
    	scale = new double[UNITS_COUNT];
    	reduceRange = new boolean[UNITS_COUNT];
	manDivisions = lastManDivisions;
    	
    	rect = new Rectangle(0, 0, 1, 1);
   	imageCanvas = Canvas.createIfSupported();
   	imageContext = imageCanvas.getContext2d();
	allocImage();
    	initialize();
    }
    
    /**
     * Show or hide current plots.
     * @param b true to show current plots
     */
    void showCurrent(boolean b) {
	showI = b;
	if (b && !showingVoltageAndMaybeCurrent())
	    setValue(0);
	calcVisiblePlots();
    }
    
    /**
     * Show or hide voltage plots.
     * @param b true to show voltage plots
     */
    void showVoltage(boolean b) {
	showV = b;
	if (b && !showingVoltageAndMaybeCurrent())
	    setValue(0);
	calcVisiblePlots();
    }

    void showMax(boolean b) { showMax = b; }
    void showScale(boolean b) { showScale = b; }
    void showMin(boolean b) { showMin = b; }
    void showFreq(boolean b) { showFreq = b; }
    
    /**
     * Show or hide FFT display.
     * @param b true to show FFT
     */
    void showFFT(boolean b) {
      showFFT = b;
      if (!showFFT)
    	  fft = null;
    }
    
    /**
     * Sets manual scale mode.
     * @param value true to enable manual scale
     * @param roundup true to round up the scale to a sensible value
     */
    void setManualScale(boolean value, boolean roundup) { 
	if (value != manualScale)
	    clear2dView();
	manualScale = value; 
	for (ScopePlot p : plots) {
	    if (!p.manScaleSet) {
		p.manScale = getManScaleFromMaxScale(p.units, roundup);
		p.manVPosition = 0;
		p.manScaleSet = true;
	    }
	}
    }
    
    /**
     * Resets the scope graph, using default settings.
     */
    void resetGraph() { 
	resetGraph(false); 
    }
    
    /**
     * Resets the scope graph.
     * @param full true to discard all old data
     */
    void resetGraph(boolean full) {
    	resetGraph(full, true);  // Default: clear history
    }
    
    /**
     * Resets the scope graph with control over history preservation.
     * @param full true to discard all old data from circular buffers
     * @param clearHistory true to clear history buffers (for drawFromZero mode)
     */
    void resetGraph(boolean full, boolean clearHistory) {
    	scopePointCount = 1;
    	while (scopePointCount <= rect.width)
    		scopePointCount *= 2;
    	if (plots == null)
    	    plots = new Vector<ScopePlot>();
    	showNegative = false;
    	int i;
    	for (i = 0; i != plots.size(); i++)
    	    plots.get(i).reset(scopePointCount, speed, full);
		calcVisiblePlots();
    	scopeTimeStep = sim.maxTimeStep;
    	
    	// Clear action annotation persistence on reset
    	if (clearHistory) {
    	    lastDisplayedActionIndex = -1;
    	    lastDisplayedActionText = null;
    	    lastDisplayedWasHover = false;
    	    lastLoggedActionIndex = -1;
    	    actionVerticalPositions.clear();  // Clear stored vertical positions
    	}
    	
    	// Record start time and initialize history for drawFromZero mode
    	if (drawFromZero) {
    	    // When clearing history (reset or first load), start from current simulation time
    	    // When preserving history (zoom/resize), keep the existing startTime
    	    if (clearHistory) {
    	        startTime = sim.t;  // Start from current simulation time
    	        historySize = 0;
    	        historyCapacity = scopePointCount * 4; // Start with 4x the display size
    	        historySampleInterval = sim.maxTimeStep * speed;
    	        
    	        // Initialize history buffers for each plot
    	        for (i = 0; i != plots.size(); i++) {
    	            ScopePlot p = plots.get(i);
    	            p.historyMinValues = new double[historyCapacity];
    	            p.historyMaxValues = new double[historyCapacity];
    	        }
    	    } else {
    	        // Preserve history, just update sample interval
    	        historySampleInterval = sim.maxTimeStep * speed;
    	        
    	        // Check if buffers need to be allocated (e.g., after loading from file)
    	        boolean needsAllocation = false;
    	        for (i = 0; i != plots.size(); i++) {
    	            ScopePlot p = plots.get(i);
    	            if (p.historyMinValues == null || p.historyMaxValues == null) {
    	                needsAllocation = true;
    	                break;
    	            }
    	        }
    	        
    	        if (needsAllocation) {
    	            // Allocating buffers for first time after load - start from current time
    	            // Use current scopePointCount (based on updated rect.width) for capacity calculation
    	            startTime = sim.t;
    	            historySize = 0;
    	            historyCapacity = scopePointCount * 4;  // Based on CURRENT scopePointCount, not old one
    	            for (i = 0; i != plots.size(); i++) {
    	                ScopePlot p = plots.get(i);
    	                p.historyMinValues = new double[historyCapacity];
    	                p.historyMaxValues = new double[historyCapacity];
    	            }
    	        } else {
    	            // Check if we need to resize buffers due to scope resize/zoom
    	            int newCapacity = scopePointCount * 4;
    	            if (newCapacity != historyCapacity) {
    	                historyCapacity = newCapacity;
    	                // Reallocate buffers and copy existing data
    	                for (i = 0; i != plots.size(); i++) {
    	                    ScopePlot p = plots.get(i);
    	                    double[] newMinValues = new double[historyCapacity];
    	                    double[] newMaxValues = new double[historyCapacity];
    	                    // Copy existing history data
    	                    int copySize = Math.min(historySize, historyCapacity);
    	                    for (int j = 0; j < copySize; j++) {
    	                        newMinValues[j] = p.historyMinValues[j];
    	                        newMaxValues[j] = p.historyMaxValues[j];
    	                    }
    	                    p.historyMinValues = newMinValues;
    	                    p.historyMaxValues = newMaxValues;
    	                    if (historySize > historyCapacity) {
    	                        historySize = historyCapacity; // Truncate if new capacity is smaller
    	                    }
    	                }
    	            }
    	        }
    	    }
    	} else {
    	    //CirSim.console("resetGraph: drawFromZero=false, clearing history buffers");
    	    // Clear history buffers when not in drawFromZero mode
    	    historySize = 0;
    	    for (i = 0; i != plots.size(); i++) {
    		ScopePlot p = plots.get(i);
    		p.historyMinValues = null;
    		p.historyMaxValues = null;
    	    }
    	}
    	
    	allocImage();
    }
    
    void setManualScaleValue(int plotId, double d) {
	if (plotId >= visiblePlots.size() )
	    return; // Shouldn't happen, but just in case...
	clear2dView();
	visiblePlots.get(plotId).manScale=d;
	visiblePlots.get(plotId).manScaleSet=true;
    }
    
    double getScaleValue() {
	if (visiblePlots.size() == 0)
	    return 0;
	ScopePlot p = visiblePlots.get(0);
	return scale[p.units];
    }
    
    String getScaleUnitsText() {
	if (visiblePlots.size() == 0)
	    return "V";
	ScopePlot p = visiblePlots.get(0);
	return getScaleUnitsText(p.units);
    }
    
    static String getScaleUnitsText(int units) {
	switch (units) {
	case UNITS_A: return "A";
	case UNITS_OHMS: return Locale.ohmString;
	case UNITS_W: return "W";
	default: return "V";
	}
    }
    
    void setManDivisions(int d) {
	manDivisions = lastManDivisions = d;
    }
    
    /**
     * Sets the maximum scale limit for current unit type (prevents auto-scale from exceeding this value).
     * @param limit The maximum limit, or null to disable
     */
    void setMaxScaleLimit(Double limit) {
	if (visiblePlots.size() == 0)
	    return;
	int units = visiblePlots.get(0).units;
	maxScaleLimit[units] = limit;
	if (limit != null && scale[units] > limit)
	    scale[units] = limit;
    }
    
    /**
     * Gets the maximum scale limit for current unit type.
     * @return The maximum limit, or null if not set
     */
    Double getMaxScaleLimit() {
	if (visiblePlots.size() == 0)
	    return null;
	int units = visiblePlots.get(0).units;
	return maxScaleLimit[units];
    }

    /**
     * Checks if this scope is active (has plots with valid elements).
     * @return true if scope has at least one plot with a valid element
     */
    boolean active() { 
	return plots.size() > 0 && plots.get(0).elm != null; 
    }
    
    /**
     * Initializes the scope with default settings.
     * Sets up default scales, speeds, and display options.
     */
    void initialize() {
    	resetGraph();
    	// Set default scales for each unit type
    	scale[UNITS_W] = scale[UNITS_OHMS] = scale[UNITS_V] = 5;
    	scale[UNITS_A] = .1;
    	scaleX = 5;
    	scaleY = .1;
    	speed = 64;
    	
    	// Set default display flags
    	showMax = false;
    	showV = showI = false;
    	showScale = true;  // Show scale by default
    	showFreq = manualScale = showMin = showElmInfo = false;
    	showFFT = false;
    	plot2d = false;
    	
    	if (!loadDefaults()) {
    	    // Set showV appropriately depending on what plots are present
    	    // Don't automatically show current - user must enable it manually
    	    int i;
    	    for (i = 0; i != plots.size(); i++) {
    		ScopePlot plot = plots.get(i);
    		if (plot.units == UNITS_V)
    		    showV = true;
    		// Don't default to showing current
    		// if (plot.units == UNITS_A)
    		//    showI = true;
    	    }
    	}
    }
    
    /**
     * Calculates which plots should be visible based on current display settings.
     * In normal mode, filters by showV/showI flags and assigns colors.
     * In 2D mode, shows only the first two plots.
     */
    void calcVisiblePlots() {
	visiblePlots = new Vector<ScopePlot>();
	int i;
	int voltCount = 0, currentCount = 0, otherCount = 0;
	
	if (!plot2d) {
	    // Normal mode: filter by voltage/current/other and assign colors
	    for (i = 0; i != plots.size(); i++) {
		ScopePlot plot = plots.get(i);
		if (plot.units == UNITS_V) {
		    if (showV) {
			visiblePlots.add(plot);
			plot.assignColor(voltCount++);
		    }
		} else if (plot.units == UNITS_A) {
		    if (showI) {
			visiblePlots.add(plot);
			plot.assignColor(currentCount++);
		    }
		} else {
		    visiblePlots.add(plot);
		    plot.assignColor(otherCount++);
		}
	    }
	} else { 
	    // 2D mode: show first two plots only
	    for (i = 0; (i < 2) && (i < plots.size()); i++) {
		visiblePlots.add(plots.get(i));
	    }
	}
    }
    
    void setRect(Rectangle r) {
	int w = this.rect.width;
	this.rect = r;
	if (this.rect.width != w)
	    resetGraph(false, !drawFromZero);  // Preserve history when drawFromZero is enabled
    }
    
    int getWidth() { return rect.width; }
    
    int rightEdge() { return rect.x+rect.width; }
	
    void setElm(CircuitElm ce) {
	plots = new Vector<ScopePlot>();
    	if (ce instanceof TransistorElm)
    	    setValue(VAL_VCE, ce);
    	else
    	    setValue(0, ce);
    	initialize();
    }
    
    void addElm(CircuitElm ce) {
    	if (ce instanceof TransistorElm)
    	    addValue(VAL_VCE, ce);
    	else
    	    addValue(0, ce);
    }

    void setValue(int val) {
	if (plots.size() > 2 || plots.size() == 0)
	    return;
	CircuitElm ce = plots.firstElement().elm;
	if (plots.size() == 2 && plots.get(1).elm != ce)
	    return;
	plot2d = plotXY = false;
	setValue(val, ce);
    }
    
    void addValue(int val, CircuitElm ce) {
	if (val == 0) {
	    plots.add(new ScopePlot(ce, UNITS_V, VAL_VOLTAGE, getManScaleFromMaxScale(UNITS_V, false)));
	    
	    // create plot for current if applicable
	    if (ce != null &&
		    sim.dotsCheckItem.getState() &&
		    !(ce instanceof OutputElm ||
		    ce instanceof LogicOutputElm ||
		    ce instanceof AudioOutputElm ||
		    ce instanceof ProbeElm))
		plots.add(new ScopePlot(ce, UNITS_A, VAL_CURRENT, getManScaleFromMaxScale(UNITS_A, false)));
	} else {
	    int u = ce.getScopeUnits(val);
	    plots.add(new ScopePlot(ce, u, val, getManScaleFromMaxScale(u, false)));
	    if (u == UNITS_V)
		showV = true;
	    // Don't default to showing current
	    // if (u == UNITS_A)
	    //	showI = true;
	}
	calcVisiblePlots();
	resetGraph();
    }
    
    void setValue(int val, CircuitElm ce) {
	plots = new Vector<ScopePlot>();
	addValue(val, ce);
//    	initialize();
    }

    void setValues(int val, int ival, CircuitElm ce, CircuitElm yelm) {
	if (ival > 0) {
	    plots = new Vector<ScopePlot>();
	    plots.add(new ScopePlot(ce, ce.getScopeUnits( val),  val, getManScaleFromMaxScale(ce.getScopeUnits( val), false)));
	    plots.add(new ScopePlot(ce, ce.getScopeUnits(ival), ival, getManScaleFromMaxScale(ce.getScopeUnits(ival), false)));
	    return;
	}
	if (yelm != null) {
	    plots = new Vector<ScopePlot>();
	    plots.add(new ScopePlot(ce,   ce.getScopeUnits( val), 0, getManScaleFromMaxScale(ce.getScopeUnits( val), false)));
	    plots.add(new ScopePlot(yelm, ce.getScopeUnits(ival), 0, getManScaleFromMaxScale(ce.getScopeUnits( val), false)));
	    return;
	}
	setValue(val);
    }
    
    void setText(String s) {
	text = s;
    }
    
    String getText() {
	return text;
    }
    
    void setTitle(String s) {
	title = s;
    }
    
    String getTitle() {
	return title;
    }
    
    boolean showingValue(int v) {
	int i;
	for (i = 0; i != plots.size(); i++) {
	    ScopePlot sp = plots.get(i);
	    if (sp.value != v)
		return false;
	}
	return true;
    }

    // returns true if we have a plot of voltage and nothing else (except current).
    // The default case is a plot of voltage and current, so we're basically checking if that case is true. 
    boolean showingVoltageAndMaybeCurrent() {
	int i;
	boolean gotv = false;
	for (i = 0; i != plots.size(); i++) {
	    ScopePlot sp = plots.get(i);
	    if (sp.value == VAL_VOLTAGE)
		gotv = true;
	    else if (sp.value != VAL_CURRENT)
		return false;
	}
	return gotv;
    }
    

    void combine(Scope s) {
	/*
	// if voltage and current are shown, remove current
	if (plots.size() == 2 && plots.get(0).elm == plots.get(1).elm)
	    plots.remove(1);
	if (s.plots.size() == 2 && s.plots.get(0).elm == s.plots.get(1).elm)
	    plots.add(s.plots.get(0));
	else
	*/
	plots = visiblePlots;
	plots.addAll(s.visiblePlots);
	s.plots.removeAllElements();
	calcVisiblePlots();
    }

    // separate this scope's plots into separate scopes and return them in arr[pos], arr[pos+1], etc.  return new length of array.
    int separate(Scope arr[], int pos) {
	int i;
	ScopePlot lastPlot = null;
	for (i = 0; i != visiblePlots.size(); i++) {
	    if (pos >= arr.length)
		return pos;
	    Scope s = new Scope(sim);
	    ScopePlot sp = visiblePlots.get(i);
	    if (lastPlot != null && lastPlot.elm == sp.elm && lastPlot.value == VAL_VOLTAGE && sp.value == VAL_CURRENT)
		continue;
	    s.setValue(sp.value, sp.elm);
	    s.position = pos;
	    arr[pos++] = s;
	    lastPlot = sp;
	    s.setFlags(getFlags());
	    s.setSpeed(speed);
	}
	return pos;
    }

    void removePlot(int plot) {
	if (plot < visiblePlots.size()) {
	    ScopePlot p = visiblePlots.get(plot);
	    plots.remove(p);
	    calcVisiblePlots();
	}
    }
    
    // called for each timestep
    void timeStep() {
	int i;
	for (i = 0; i != plots.size(); i++)
	    plots.get(i).timeStep();

	int x=0;
	int y=0;
	
	// For 2d plots we draw here rather than in the drawing routine
    	if (plot2d && imageContext!=null && plots.size()>=2) {
    	    double v = plots.get(0).lastValue;
    	    double yval = plots.get(1).lastValue;
    	    if (!isManualScale()) {
        	    boolean newscale = false;
        	    while (v > scaleX || v < -scaleX) {
        		scaleX *= 2;
        		newscale = true;
        	    }
        	    while (yval > scaleY || yval < -scaleY) {
        		scaleY *= 2;
        		newscale = true;
        	    }
        	    if (newscale)
        		clear2dView();
        	    double xa = v   /scaleX;
        	    double ya = yval/scaleY;
        	    x = (int) (rect.width *(1+xa)*.499);
        	    y = (int) (rect.height*(1-ya)*.499);
    	    } else {
    		double gridPx = calc2dGridPx(rect.width, rect.height);
    		x=(int)(rect.width*.499+(v/plots.get(0).manScale)*gridPx+gridPx*manDivisions*(double)(plots.get(0).manVPosition)/(double)(V_POSITION_STEPS));
    		y=(int)(rect.height*.499-(yval/plots.get(1).manScale)*gridPx-gridPx*manDivisions*(double)(plots.get(1).manVPosition)/(double)(V_POSITION_STEPS));

    	    }
    	    drawTo(x, y);
    	}
    	
    	// Capture data to history for drawFromZero mode
    	if (drawFromZero && !plot2d) {
    	    captureToHistory();
    	}
    }
    
    /**
     * Captures current scope data to history buffers for drawFromZero mode.
     * Only captures at the defined sample interval to avoid excessive memory use.
     * Automatically downsamples if history capacity is reached.
     */
    void captureToHistory() {
	// Only capture at the defined sample interval
	if (historySize > 0) {
	    double lastSampleTime = startTime + (historySize - 1) * historySampleInterval;
	    if (sim.t < lastSampleTime + historySampleInterval * 0.9)
		return; // Too soon for next sample
	}
	
	// Check if we need to downsample
	if (historySize >= historyCapacity) {
	    downsampleHistory();
	}
	
	// Verify all history buffers are allocated
	if (!areHistoryBuffersAllocated()) {
	    CirSim.console("captureToHistory: Not all history buffers allocated, skipping capture. " +
		"drawFromZero=" + drawFromZero + ", plots.size()=" + plots.size());
	    return;
	}
	
	// Capture current values from each plot's circular buffer
	for (int i = 0; i < plots.size(); i++) {
	    ScopePlot p = plots.get(i);
	    if (p.historyMinValues != null && p.historyMaxValues != null) {
		// Get the most recent value from the circular buffer
		int idx = p.ptr & (scopePointCount - 1);
		p.historyMinValues[historySize] = p.minValues[idx];
		p.historyMaxValues[historySize] = p.maxValues[idx];
	    }
	}
	
	historySize++;
    }
    
    /**
     * Checks if all plots have their history buffers allocated.
     * @return true if all buffers are allocated, false otherwise
     */
    private boolean areHistoryBuffersAllocated() {
	for (int i = 0; i < plots.size(); i++) {
	    ScopePlot p = plots.get(i);
	    if (p.historyMinValues == null || p.historyMaxValues == null) {
		// CirSim.console("captureToHistory: Plot " + i + " missing history buffers! " +
		//     "historyMinValues=" + (p.historyMinValues == null ? "NULL" : "allocated") + 
		//     ", historyMaxValues=" + (p.historyMaxValues == null ? "NULL" : "allocated"));
		return false;
	    }
	}
	return true;
    }
    
    /**
     * Downsamples history by a factor of 2 to make room for more data.
     * Preserves peaks by keeping minimum of mins and maximum of maxs.
     */
    void downsampleHistory() {
	int newSize = historySize / 2;
	historySampleInterval *= 2; // Double the time between samples
	
	// CirSim.console("Downsampling scope history: " + historySize + " -> " + newSize + 
	//             " samples, interval: " + historySampleInterval + "x");
	
	for (int i = 0; i < plots.size(); i++) {
	    ScopePlot p = plots.get(i);
	    if (p.historyMinValues == null || p.historyMaxValues == null)
		continue;
	    
	    // Downsample by taking min/max of pairs
	    for (int j = 0; j < newSize; j++) {
		int src1 = j * 2;
		int src2 = j * 2 + 1;
		
		// Keep the minimum of mins and maximum of maxs to preserve peaks
		p.historyMinValues[j] = Math.min(p.historyMinValues[src1], 
		                                  src2 < historySize ? p.historyMinValues[src2] : p.historyMinValues[src1]);
		p.historyMaxValues[j] = Math.max(p.historyMaxValues[src1],
		                                  src2 < historySize ? p.historyMaxValues[src2] : p.historyMaxValues[src1]);
	    }
	}
	
	historySize = newSize;
    }

    /**
     * Calculates 2D grid pixel spacing based on window dimensions.
     * @param width Window width in pixels
     * @param height Window height in pixels
     * @return Grid spacing in pixels
     */
    double calc2dGridPx(int width, int height) {
	int minDimension = Math.min(width, height);
	return ((double) minDimension / 2) / ((double) manDivisions / 2 + 0.05);
    }
    
    
    /**
     * Draws a line from the last 2D plot position to a new position.
     * @param x2 New X coordinate
     * @param y2 New Y coordinate
     */
    void drawTo(int x2, int y2) {
    	if (draw_ox == -1) {
    		draw_ox = x2;
    		draw_oy = y2;
    		return;
    	}
    	
    	// Set stroke color based on print mode
	imageContext.setStrokeStyle(sim.printableCheckItem.getState() ? "#000000" : "#ffffff");
	imageContext.beginPath();
	imageContext.moveTo(draw_ox, draw_oy);
	imageContext.lineTo(x2, y2);
	imageContext.stroke();
	
    	draw_ox = x2;
    	draw_oy = y2;
    }
	
    /**
     * Clears the 2D view canvas.
     */
    void clear2dView() {
    	if (imageContext != null) {
    		// Set background color based on print mode
    		String bgColor = sim.printableCheckItem.getState() ? "#eee" : "#202020";
    		imageContext.setFillStyle(bgColor);
    		imageContext.fillRect(0, 0, rect.width - 1, rect.height - 1);
    	}
    	draw_ox = draw_oy = -1;
    }
	
    /*
    void adjustScale(double x) {
	scale[UNITS_V] *= x;
	scale[UNITS_A] *= x;
	scale[UNITS_OHMS] *= x;
	scale[UNITS_W] *= x;
	scaleX *= x;
	scaleY *= x;
    }
    */
    
    void setMaxScale(boolean s) {
	// This procedure is added to set maxscale to an explicit value instead of just having a toggle
	// We call the toggle procedure first because it has useful side-effects and then set the value explicitly.
	maxScale();
	maxScale = s;
    }
    
    void maxScale() {
	if (plot2d) {
	    double x = 1e-8;
	    scale[UNITS_V] *= x;
	    scale[UNITS_A] *= x;
	    scale[UNITS_OHMS] *= x;
	    scale[UNITS_W] *= x;
	    scaleX *= x; // For XY plots
	    scaleY *= x;
	    return;
	}
	// toggle max scale.  This isn't on by default because, for the examples, we sometimes want two plots
	// matched to the same scale so we can show one is larger.  Also, for some fast-moving scopes
	// (like for AM detector), the amplitude varies over time but you can't see that if the scale is
	// constantly adjusting.  It's also nice to set the default scale to hide noise and to avoid
	// having the scale moving around a lot when a circuit starts up.
	maxScale = !maxScale;
	showNegative = false;
    }
    
    void toggleDrawFromZero() {
	drawFromZero = !drawFromZero;
	if (drawFromZero) {
	    // Always start from t=0 by resetting simulation
	    sim.resetAction();
	    startTime = 0.0;
	    // Enable auto scale time when draw from zero is enabled
	    autoScaleTime = true;
	} else {
	    // Disable auto scale time when draw from zero is disabled
	    autoScaleTime = false;
	}
	resetGraph();
    }
    
    void toggleAutoScaleTime() {
	autoScaleTime = !autoScaleTime;
	sim.needAnalyze();
    }

    void drawFFTVerticalGridLines(Graphics g) {
      // Draw x-grid lines and label the frequencies in the FFT that they point to.
      int prevEnd = 0;
      int divs = 20;
      double maxFrequency = 1 / (sim.maxTimeStep * speed * divs * 2);
      for (int i = 0; i < divs; i++) {
        int x = rect.width * i / divs;
        if (x < prevEnd) continue;
        String s = ((int) Math.round(i * maxFrequency)) + "Hz";
        int sWidth = (int) Math.ceil(g.context.measureText(s).getWidth());
        prevEnd = x + sWidth + 4;
        if (i > 0) {
          g.setColor("#880000");
          g.drawLine(x, 0, x, rect.height);
        }
        g.setColor("#FF0000");
        g.drawString(s, x + 2, rect.height);
      }
    }

    void drawFFT(Graphics g) {
    	if (fft == null || fft.getSize() != scopePointCount)
    		fft = new FFT(scopePointCount);
      double[] real = new double[scopePointCount];
      double[] imag = new double[scopePointCount];
      ScopePlot plot = (visiblePlots.size() == 0) ? plots.firstElement() : visiblePlots.firstElement();
      double maxV[] = plot.maxValues;
      double minV[] = plot.minValues;
      int ptr = plot.ptr;
      for (int i = 0; i < scopePointCount; i++) {
	  int ii = (ptr - i + scopePointCount) & (scopePointCount - 1);
	  // need to average max and min or else it could cause average of function to be > 0, which
	  // produces spike at 0 Hz that hides rest of spectrum
	  real[i] = .5*(maxV[ii]+minV[ii]);
	  imag[i] = 0;
      }
      fft.fft(real, imag, true);
      double maxM = 1e-8;
      for (int i = 0; i < scopePointCount / 2; i++) {
    	  double m = fft.magnitude(real[i], imag[i]);
    	  if (m > maxM)
    		  maxM = m;
      }
      int prevX = 0;
      g.setColor("#FF0000");
      if (!logSpectrum) {
	  int prevHeight = 0;
	  int y = (rect.height - 1) - 12;
	  for (int i = 0; i < scopePointCount / 2; i++) {
	      int x = 2 * i * rect.width / scopePointCount;
	      // rect.width may be greater than or less than scopePointCount/2,
	      // so x may be greater than or equal to prevX.
	      double magnitude = fft.magnitude(real[i], imag[i]);
	      int height = (int) ((magnitude * y) / maxM);
	      if (x != prevX)
		  g.drawLine(prevX, y - prevHeight, x, y - height);
	      prevHeight = height;
	      prevX = x;
	  }
      } else {
	  int y0 = 5;
	  int prevY = 0;
	  double ymult = rect.height/10;
	  double val0 = Math.log(scale[plot.units])*ymult;
	  for (int i = 0; i < scopePointCount / 2; i++) {
	      int x = 2 * i * rect.width / scopePointCount;
	      // rect.width may be greater than or less than scopePointCount/2,
	      // so x may be greater than or equal to prevX.
	      double val = Math.log(fft.magnitude(real[i], imag[i]));
	      int y = y0-(int) (val*ymult-val0);
	      if (x != prevX)
		  g.drawLine(prevX, prevY, x, y);
	      prevY = y;
	      prevX = x;
	  }
      }
    }
    
    /**
     * Draws the settings wheel icon in the bottom-left corner of the scope.
     * @param g Graphics context
     */
    void drawSettingsWheel(Graphics g) {
	if (!showSettingsWheel())
	    return;
	
	// Settings wheel dimensions
	final int OUTER_RADIUS = 8;
	final int INNER_RADIUS = 5;
	final int INNER_RADIUS_45 = 4;
	final int OUTER_RADIUS_45 = 6;
	
	g.context.save();
	
	// Set color based on cursor position
	g.setColor(cursorInSettingsWheel() ? CircuitElm.selectColor : Color.dark_gray);
	
	// Position at bottom-left corner
	g.context.translate(rect.x + 18, rect.y + rect.height - 18);
	
	// Draw center circle
	CircuitElm.drawThickCircle(g, 0, 0, INNER_RADIUS);
	
	// Draw horizontal spokes
	CircuitElm.drawThickLine(g, -OUTER_RADIUS, 0, -INNER_RADIUS, 0);
	CircuitElm.drawThickLine(g, OUTER_RADIUS, 0, INNER_RADIUS, 0);
	
	// Draw vertical spokes
	CircuitElm.drawThickLine(g, 0, -OUTER_RADIUS, 0, -INNER_RADIUS);
	CircuitElm.drawThickLine(g, 0, OUTER_RADIUS, 0, INNER_RADIUS);
	
	// Draw diagonal spokes
	CircuitElm.drawThickLine(g, -OUTER_RADIUS_45, -OUTER_RADIUS_45, -INNER_RADIUS_45, -INNER_RADIUS_45);
	CircuitElm.drawThickLine(g, OUTER_RADIUS_45, -OUTER_RADIUS_45, INNER_RADIUS_45, -INNER_RADIUS_45);
	CircuitElm.drawThickLine(g, -OUTER_RADIUS_45, OUTER_RADIUS_45, -INNER_RADIUS_45, INNER_RADIUS_45);
	CircuitElm.drawThickLine(g, OUTER_RADIUS_45, OUTER_RADIUS_45, INNER_RADIUS_45, INNER_RADIUS_45);
	
	g.context.restore();
    }

    void draw2d(Graphics g) {
    	if (imageContext==null)
    		return;
    	g.context.save();
    	g.context.translate(rect.x, rect.y);
    	g.clipRect(0, 0, rect.width, rect.height);
    	
    	alphaCounter++;
    	
    	if (alphaCounter>2) {
    		// fade out plot
    		alphaCounter=0;
    		imageContext.setGlobalAlpha(0.01);
    		if (sim.printableCheckItem.getState()) {
    			imageContext.setFillStyle("#ffffff");
    		} else {
    			imageContext.setFillStyle("#202020");  // Dark gray - same as undocked scopes
    		}
    		imageContext.fillRect(0,0,rect.width,rect.height);
    		imageContext.setGlobalAlpha(1.0);
    	}
    	
    	g.context.drawImage(imageContext.getCanvas(), 0.0, 0.0);
//    	g.drawImage(image, r.x, r.y, null);
    	g.setColor(CircuitElm.whiteColor);
    	g.fillOval(draw_ox-2, draw_oy-2, 5, 5);
    	// Axis
    	g.setColor(CircuitElm.positiveColor);
    	g.drawLine(0, rect.height/2, rect.width-1, rect.height/2);
    	if (!plotXY)
    		g.setColor(Color.yellow);
    	g.drawLine(rect.width/2, 0, rect.width/2, rect.height-1);
    	if (isManualScale()) {
    	    double gridPx=calc2dGridPx(rect.width, rect.height);
    	    g.setColor("#404040");
    	    for(int i=-manDivisions; i<=manDivisions; i++) {
    		if (i!=0)
    		    g.drawLine((int)(gridPx*i)+rect.width/2, 0,(int)(gridPx*i)+rect.width/2, rect.height);
    		    g.drawLine(0, (int)(gridPx*i)+rect.height/2,rect.width, (int)(gridPx*i)+rect.height/2);
    	    }
    	}
	textY=10;
	g.setColor(CircuitElm.whiteColor);
    	if (text != null) {
    	    drawInfoText(g, text);
	}
    	if (showScale && plots.size()>=2 && isManualScale()) {
    	    ScopePlot px = plots.get(0);
    	    String sx=px.getUnitText(px.manScale);
    	    ScopePlot py = plots.get(1);
    	    String sy=py.getUnitText(py.manScale);
    	    drawInfoText(g,"X="+sx+"/div, Y="+sy+"/div");
    	}
    	drawTitle(g);
    	g.context.restore();
    	drawSettingsWheel(g);
		if ( !sim.dialogIsShowing() && rect.contains(sim.mouseCursorX, sim.mouseCursorY) && plots.size()>=2) {
			double gridPx=calc2dGridPx(rect.width, rect.height);
			String info[] = new String [3];  // Increased from 2 to 3
			ScopePlot px = plots.get(0);
			ScopePlot py = plots.get(1);
			double xValue;
			double yValue;
			if (isManualScale()) {
				xValue = px.manScale*((double)(sim.mouseCursorX-rect.x-rect.width/2)/gridPx-manDivisions*px.manVPosition/(double)(V_POSITION_STEPS));
				yValue = py.manScale*((double)(-sim.mouseCursorY+rect.y+rect.height/2)/gridPx-manDivisions*py.manVPosition/(double)(V_POSITION_STEPS));
				} else {
				xValue = ((double)(sim.mouseCursorX-rect.x)/(0.499*(double)(rect.width))-1.0)*scaleX;
				yValue = -((double)(sim.mouseCursorY-rect.y)/(0.499*(double)(rect.height))-1.0)*scaleY;
			}
			// Add plot name as first element
			String plotName = getScopeLabelOrText();
			if (plotName == null || plotName.isEmpty()) {
				plotName = "2D Plot";
			}
			info[0] = plotName;
			info[1] = px.getUnitText(xValue);
			info[2] = py.getUnitText(yValue);
			
			drawCursorInfo(g, info, 3, sim.mouseCursorX, true);  // Changed from 2 to 3
		}
    }
	
  
    
    /**
     * Determines if settings wheel should be shown based on scope size.
     * @return true if scope is large enough to display settings wheel
     */
    boolean showSettingsWheel() {
	return rect.height > SETTINGS_WHEEL_MARGIN && rect.width > SETTINGS_WHEEL_MARGIN;
    }
    
    /**
     * Checks if cursor is over the settings wheel icon.
     * @return true if cursor is within settings wheel bounds
     */
    boolean cursorInSettingsWheel() {
	return showSettingsWheel() &&
		sim.mouseCursorX >= rect.x &&
		sim.mouseCursorX <= rect.x + SETTINGS_WHEEL_SIZE &&
		sim.mouseCursorY >= rect.y + rect.height - SETTINGS_WHEEL_SIZE && 
		sim.mouseCursorY <= rect.y + rect.height;
    }
    
    // does another scope have something selected?
    void checkForSelectionElsewhere() {
	// if mouse is here, then selection is already set by checkForSelection()
	if (cursorScope == this)
	    return;
	
	if (cursorScope == null || visiblePlots.size() == 0) {
	    selectedPlot = -1;
	    return;
	}
	
	// find a plot with same units as selected plot
	int i;
	for (i = 0; i != visiblePlots.size(); i++) {
	    ScopePlot p = visiblePlots.get(i);
	    if (p.units == cursorUnits) {
		selectedPlot = i;
		return;
	    }
	}
	
	// default if we can't find anything with matching units
	selectedPlot = 0;
    }
    
    /**
     * Draw vertical marker lines at action times
     */
    void drawActionTimeMarkers(Graphics g, double startTime, double displayTimeSpan) {
	ActionScheduler scheduler = ActionScheduler.getInstance();
	if (scheduler == null)
	    return;
	
	// Only draw markers if there's an enabled ActionTimeElm in the circuit
	boolean anyEnabled = false;
	for (int i = 0; i < sim.elmList.size(); i++) {
	    CircuitElm ce = sim.getElm(i);
	    if (ce instanceof ActionTimeElm && ((ActionTimeElm) ce).enabled) {
		anyEnabled = true;
		break;
	    }
	}
	if (!anyEnabled)
	    return;
	
	java.util.List<ActionScheduler.ScheduledAction> allActions = scheduler.getAllActions();
	java.util.List<ActionScheduler.ScheduledAction> enabledActions = new java.util.ArrayList<ActionScheduler.ScheduledAction>();
	
	// Filter for enabled actions
	for (ActionScheduler.ScheduledAction action : allActions) {
	    if (action.enabled) {
		enabledActions.add(action);
	    }
	}
	
	if (enabledActions.isEmpty())
	    return;
	
	// Update hovered action based on mouse position
	updateHoveredAction(enabledActions, startTime, displayTimeSpan);
	
	// Draw markers for each action time
	g.context.save();
	g.setColor("#FF6B6B"); // Red/coral color for action markers
	g.context.setLineWidth(2);
	
	for (int i = 0; i < enabledActions.size(); i++) {
	    ActionScheduler.ScheduledAction action = enabledActions.get(i);
	    double timeFromStart = action.actionTime - startTime;
	    if (timeFromStart < 0 || timeFromStart > displayTimeSpan)
		continue;
	    
	    // Skip t=0 actions
	    if (action.actionTime <= 0)
		continue;
	    
	    int gx = (int) (rect.width * timeFromStart / displayTimeSpan);
	    if (gx >= 0 && gx < rect.width) {
		// Draw thicker line if hovered
		if (i == hoveredActionIndex) {
		    g.context.setLineWidth(4);
		}
		g.drawLine(gx, 0, gx, rect.height-1);
		if (i == hoveredActionIndex) {
		    g.context.setLineWidth(2);
		}
	    }
	}
	
	g.context.restore();
	
	// Draw annotations for all completed actions with vertical positioning
	drawAllActionAnnotations(g, enabledActions, startTime, displayTimeSpan);
    }
    
    /**
     * Draw annotations for all completed actions, positioned to avoid overlap
     */
    void drawAllActionAnnotations(Graphics g, java.util.List<ActionScheduler.ScheduledAction> enabledActions,
				   double startTime, double displayTimeSpan) {
	// Collect completed actions, keeping only the last action at each unique time
	java.util.List<ActionScheduler.ScheduledAction> completedActions = new java.util.ArrayList<ActionScheduler.ScheduledAction>();
	java.util.HashMap<Double, ActionScheduler.ScheduledAction> actionsByTime = new java.util.HashMap<Double, ActionScheduler.ScheduledAction>();
	
	for (ActionScheduler.ScheduledAction action : enabledActions) {
	    if (action.state == ActionScheduler.ActionState.COMPLETED && 
		action.postText != null && !action.postText.trim().isEmpty() &&
		action.actionTime > 0) {
		// Keep only the last action at each time (higher ID = more recent)
		ActionScheduler.ScheduledAction existing = actionsByTime.get(action.actionTime);
		if (existing == null || action.id > existing.id) {
		    actionsByTime.put(action.actionTime, action);
		}
	    }
	}
	
	// Convert to list
	completedActions.addAll(actionsByTime.values());
	
	if (completedActions.isEmpty())
	    return;
	
	// Calculate base vertical positions for each action
	int boxHeight = 24;
	int boxSpacing = 4;
	
	// Draw each completed action
	for (int i = 0; i < completedActions.size(); i++) {
	    ActionScheduler.ScheduledAction action = completedActions.get(i);
	    
	    // Calculate vertical position (stacked)
	    int verticalSlot = i;
	    int popupY;
	    
	    // Check if we have a stored position for this action
	    Integer storedY = actionVerticalPositions.get(action.id);
	    
	    // If hovering AND mouse button is down, move hovered action to mouse Y position and adjust others
	    if (hoveredActionIndex >= 0 && mouseButtonDown) {
		int hoveredIdx = -1;
		for (int j = 0; j < completedActions.size(); j++) {
		    if (completedActions.get(j).id == enabledActions.get(hoveredActionIndex).id) {
			hoveredIdx = j;
			break;
		    }
		}
		
		if (hoveredIdx >= 0) {
		    if (i == hoveredIdx) {
			// Hovered action follows mouse Y and stores position
			popupY = mouseY;
			actionVerticalPositions.put(action.id, popupY);
			drawActionAnnotationAtPosition(g, action, startTime, displayTimeSpan, popupY);
			continue;
		    } else {
			// Other actions: use stored position if available, otherwise calculate
			if (storedY != null) {
			    popupY = storedY;
			} else {
			    // Calculate position avoiding hovered action
			    int hoveredY = mouseY;
			    int normalY = 30 + verticalSlot * (boxHeight + boxSpacing);
			    
			    // If this action's normal position would overlap with hovered, shift it
			    if (Math.abs(normalY - hoveredY) < boxHeight + boxSpacing) {
				if (i < hoveredIdx) {
				    // Actions before hovered: shift up
				    popupY = hoveredY - (hoveredIdx - i) * (boxHeight + boxSpacing);
				} else {
				    // Actions after hovered: shift down
				    popupY = hoveredY + (i - hoveredIdx) * (boxHeight + boxSpacing);
				}
			    } else {
				popupY = normalY;
			    }
			    actionVerticalPositions.put(action.id, popupY);
			}
			drawActionAnnotationAtPosition(g, action, startTime, displayTimeSpan, popupY);
			continue;
		    }
		}
	    }
	    
	    // Not hovering: use stored position if available, otherwise default position
	    if (storedY != null) {
		popupY = storedY;
	    } else {
		popupY = 30 + verticalSlot * (boxHeight + boxSpacing);
		actionVerticalPositions.put(action.id, popupY);
	    }
	    drawActionAnnotationAtPosition(g, action, startTime, displayTimeSpan, popupY);
	}
    }
    
    /**
     * Update which action marker is being hovered
     */
    void updateHoveredAction(java.util.List<ActionScheduler.ScheduledAction> enabledActions, 
			      double startTime, double displayTimeSpan) {
	hoveredActionIndex = -1;
	
	if (mouseX < 0 || mouseY < 0)
	    return;
	
	final int HOVER_THRESHOLD = 10; // pixels
	
	for (int i = 0; i < enabledActions.size(); i++) {
	    ActionScheduler.ScheduledAction action = enabledActions.get(i);
	    double timeFromStart = action.actionTime - startTime;
	    if (timeFromStart < 0 || timeFromStart > displayTimeSpan)
		continue;
	    
	    int gx = (int) (rect.width * timeFromStart / displayTimeSpan);
	    if (Math.abs(mouseX - gx) < HOVER_THRESHOLD) {
		hoveredActionIndex = i;
		break;
	    }
	}
	
	// Track last hovered index for detecting changes
	if (hoveredActionIndex >= 0) {
	    lastHoveredActionIndex = hoveredActionIndex;
	}
    }
    
    /**
     * Draw annotation popup for action at specific Y position
     */
    void drawActionAnnotationAtPosition(Graphics g, ActionScheduler.ScheduledAction action, 
					 double startTime, double displayTimeSpan, int popupY) {
	double timeFromStart = action.actionTime - startTime;
	if (timeFromStart < 0 || timeFromStart > displayTimeSpan)
	    return;
	
	// Skip if postText is empty
	if (action.postText == null || action.postText.isEmpty())
	    return;
	
	int gx = (int) (rect.width * timeFromStart / displayTimeSpan);
	
	// Build annotation text
	String text = action.postText;
	
	// Add time and slider info if available
	if (action.sliderName != null && !action.sliderName.isEmpty()) {
	    text += " @ t=" + CircuitElm.getUnitText(action.actionTime, "s");
	}
	
	g.context.save();
	g.context.setFont("12px sans-serif");
	
	// Measure text
	double textWidth = g.context.measureText(text).getWidth();
	int padding = 6;
	int boxWidth = (int) textWidth + padding * 2;
	int boxHeight = 20;
	// Position popup horizontally centered on marker
	int popupX = gx - boxWidth / 2;
	
	// Keep popup within bounds horizontally
	if (popupX < 0) popupX = 0;
	if (popupX + boxWidth > rect.width) popupX = rect.width - boxWidth;
	
	// Keep popup within bounds vertically
	if (popupY < 0) popupY = 0;
	if (popupY + boxHeight > rect.height) popupY = rect.height - boxHeight;
	
	// Draw background
	g.context.setFillStyle("rgba(255, 107, 107, 0.3)");
	g.context.fillRect(popupX, popupY, boxWidth, boxHeight);
	
	// Draw border
	g.context.setStrokeStyle("rgba(255, 107, 107, 0.1)");
	g.context.setLineWidth(2);
	g.context.strokeRect(popupX, popupY, boxWidth, boxHeight);
	
	// Draw arrow pointing to marker
	g.context.beginPath();
	g.context.moveTo(gx, popupY + boxHeight);
	g.context.lineTo(gx - 6, popupY + boxHeight);
	g.context.lineTo(gx + 6, popupY + boxHeight);
	g.context.closePath();
	g.context.fill();
	
	// Draw text
	g.context.setFillStyle("white");
	g.context.setTextAlign("center");
	g.context.setTextBaseline("middle");
	g.context.fillText(text, popupX + boxWidth / 2, popupY + boxHeight / 2);
	
	g.context.restore();
    }
    
    void draw(Graphics g) {
	if (plots.size() == 0)
	    return;
    	
    	// reset if timestep changed
    	if (scopeTimeStep != sim.maxTimeStep) {
    	    scopeTimeStep = sim.maxTimeStep;
    	    resetGraph();
    	}
    	
    	
    	if (plot2d) {
    		draw2d(g);
    		return;
    	}

    	drawSettingsWheel(g);
    	g.context.save();
    	g.setColor(Color.red);
    	g.context.translate(rect.x, rect.y);    	
    	g.clipRect(0, 0, rect.width, rect.height);

        if (showFFT) {
            drawFFTVerticalGridLines(g);
            drawFFT(g);
        }

    	int i;
    	for (i = 0; i != UNITS_COUNT; i++) {
    	    reduceRange[i] = false;
    	    if (maxScale && !manualScale)
    		scale[i] = 1e-4;
    	}
    	
    	int si;
    	somethingSelected = false;  // is one of our plots selected?
    	
    	for (si = 0; si != visiblePlots.size(); si++) {
    	    ScopePlot plot = visiblePlots.get(si);
    	    calcPlotScale(plot);
    	    if (sim.scopeSelected == -1 && plot.elm !=null && plot.elm.isMouseElm())
    		somethingSelected = true;
    	    reduceRange[plot.units] = true;
    	}
    	
    	boolean sel = sim.scopeMenuIsSelected(this);
    	
    	checkForSelectionElsewhere();
    	if (selectedPlot >= 0)
    	    somethingSelected = true;

    	drawGridLines = true;
    	boolean allPlotsSameUnits = true;
    	for (i = 1; i < visiblePlots.size(); i++) {
    	    if (visiblePlots.get(i).units != visiblePlots.get(0).units)
    		allPlotsSameUnits = false; // Don't draw horizontal grid lines unless all plots are in same units
    	}
    	
    	if ((allPlotsSameUnits || showMax || showMin) && visiblePlots.size() > 0)
    	    calcMaxAndMin(visiblePlots.firstElement().units);
    	
    	// Track if this is the first plot drawn to save gridStepX for display
    	boolean firstPlotDrawn = false;
    	
    	// draw volt plots on top (last), then current plots underneath, then everything else
    	for (i = 0; i != visiblePlots.size(); i++) {
    	    if (visiblePlots.get(i).units > UNITS_A && i != selectedPlot) {
    		drawPlot(g, visiblePlots.get(i), allPlotsSameUnits, false, sel);
    		if (!firstPlotDrawn) {
    		    displayGridStepX = gridStepX;
    		    firstPlotDrawn = true;
    		}
    	    }
    	}
    	for (i = 0; i != visiblePlots.size(); i++) {
    	    if (visiblePlots.get(i).units == UNITS_A && i != selectedPlot) {
    		drawPlot(g, visiblePlots.get(i), allPlotsSameUnits, false, sel);
    		if (!firstPlotDrawn) {
    		    displayGridStepX = gridStepX;
    		    firstPlotDrawn = true;
    		}
    	    }
    	}
    	for (i = 0; i != visiblePlots.size(); i++) {
    	    if (visiblePlots.get(i).units == UNITS_V && i != selectedPlot) {
    		drawPlot(g, visiblePlots.get(i), allPlotsSameUnits, false, sel);
    		if (!firstPlotDrawn) {
    		    displayGridStepX = gridStepX;
    		    firstPlotDrawn = true;
    		}
    	    }
    	}
    	// draw selection on top.  only works if selection chosen from scope
    	if (selectedPlot >= 0 && selectedPlot < visiblePlots.size()) {
    	    drawPlot(g, visiblePlots.get(selectedPlot), allPlotsSameUnits, true, sel);
    	    if (!firstPlotDrawn) {
    		displayGridStepX = gridStepX;
    		firstPlotDrawn = true;
    	    }
    	}
    	
        drawInfoTexts(g);
    	drawTitle(g);
    	
    	g.restore();
    	
    	drawCursor(g);
    	
    	if (plots.get(0).ptr > 5 && !manualScale) {
    	    for (i = 0; i != UNITS_COUNT; i++)
    		if (scale[i] > 1e-4 && reduceRange[i])
    		    scale[i] /= 2;
    	}
    	
    	if ( (properties != null ) && properties.isShowing() )
    	    properties.refreshDraw();

    }

    
    // calculate maximum and minimum values for all plots of given units
    void calcMaxAndMin(int units) {
	maxValue = -1e8;
	minValue = 1e8;
    	int i;
    	int si;
    	for (si = 0; si != visiblePlots.size(); si++) {
    	    ScopePlot plot = visiblePlots.get(si);
    	    if (plot.units != units)
    		continue;
    	    int ipa = plot.startIndex(rect.width);
    	    double maxV[] = plot.maxValues;
    	    double minV[] = plot.minValues;
    	    for (i = 0; i != rect.width; i++) {
    		int ip = (i+ipa) & (scopePointCount-1);
    		if (maxV[ip] > maxValue)
    		    maxValue = maxV[ip];
    		if (minV[ip] < minValue)
    		    minValue = minV[ip];
    	    }
        }
    }
    
    // adjust scale of a plot
    void calcPlotScale(ScopePlot plot) {
		if (manualScale)
			return;
    	int i;
    	int ipa = plot.startIndex(rect.width);
    	double maxV[] = plot.maxValues;
    	double minV[] = plot.minValues;
    	double max = 0;
    	double gridMax = scale[plot.units];
    	for (i = 0; i != rect.width; i++) {
    	    int ip = (i+ipa) & (scopePointCount-1);
    	    if (maxV[ip] > max)
    			max = maxV[ip];
    	    if (minV[ip] < -max)
    			max = -minV[ip];
    	}
    	// scale fixed at maximum?
    	if (maxScale)
    	    gridMax = Math.max(max, gridMax);
    	else
    	    // adjust in powers of two
    	    while (max > gridMax)
    			gridMax *= 2;
    	
    	// Apply maximum scale limit if set
    	if (maxScaleLimit[plot.units] != null && gridMax > maxScaleLimit[plot.units])
    	    gridMax = maxScaleLimit[plot.units];
    	
    	scale[plot.units] = gridMax;
    }
    
    /**
     * Calculates the grid step for the X (time) axis.
     * @return Grid step in simulation time units
     */
    double calcGridStepX() {
	int multptr = 0;
    	double gsx = 1e-15;
    	double ts = sim.maxTimeStep * speed;
    	
    	while (gsx < ts * MIN_PIXEL_SPACING) {
    	    gsx *= multa[(multptr++) % 3];
    	}
    	return gsx;
    }

    /**
     * Gets the maximum grid value from manual scale settings.
     * @param plot The plot to calculate for
     * @return Maximum display value
     */
    double getGridMaxFromManScale(ScopePlot plot) {
	return ((double)(manDivisions) / 2 + 0.05) * plot.manScale;
    }
    
    /**
     * Convert simulation time to circular buffer index for drawFromZero mode
     * @param time Absolute simulation time
     * @param plot The plot being drawn
     * @return Index in minValues/maxValues arrays
     */
    int getBufferIndexForTime(double time, ScopePlot plot) {
	// Calculate how many timesteps from start
	double timePerStep = sim.maxTimeStep * speed;
	int stepsFromStart = (int)((time - startTime) / timePerStep);
	
	// Map to circular buffer, accounting for wraparound
	int totalSteps = (int)((sim.t - startTime) / timePerStep);
	int offset = totalSteps - stepsFromStart;
	
	// Get current pointer position and subtract offset
	int currentPos = plot.ptr;
	return (currentPos - offset) & (scopePointCount - 1);
    }
    
    void drawPlot(Graphics g, ScopePlot plot, boolean allPlotsSameUnits, boolean selected, boolean allSelected) {
	if (plot.elm == null)
	    return;
    	int i;
    	String col;
    	
    	double gridMid, positionOffset;
    	int multptr=0;
    	int x = 0;
    	final int maxy = (rect.height-1)/2;

    	String color = (somethingSelected) ? "#A0A0A0" : plot.color;
	if (allSelected || (sim.scopeSelected == -1  && plot.elm.isMouseElm()))
    	    color = CircuitElm.selectColor.getHexValue();
	else if (selected)
	    color = plot.color;
    	int ipa = plot.startIndex(rect.width);
    	double maxV[] = plot.maxValues;
    	double minV[] = plot.minValues;
    	double gridMax;
    	
    	
    	// Calculate the max value (positive) to show and the value at the mid point of the grid
    	if (!isManualScale()) {
    	    	gridMax = scale[plot.units];
    	    	gridMid = 0;
    	    	positionOffset = 0;
        	if (allPlotsSameUnits) {
        	    // if we don't have overlapping scopes of different units, we can move zero around.
        	    // Put it at the bottom if the scope is never negative.
        	    double mx = gridMax;
        	    double mn = 0;
        	    if (maxScale) {
        		// scale is maxed out, so fix boundaries of scope at maximum and minimum. 
        		mx = maxValue;
        		mn = minValue;
        	    } else if (showNegative || minValue < (mx+mn)*.5 - (mx-mn)*.55) {
        		mn = -gridMax;
        		showNegative = true;
        	    }
        	    gridMid = (mx+mn)*.5;
        	    gridMax = (mx-mn)*.55;  // leave space at top and bottom
        	}
    	} else {
    	    gridMid =0;
    	    gridMax = getGridMaxFromManScale(plot);
    	    positionOffset = gridMax*2.0*(double)(plot.manVPosition)/(double)(V_POSITION_STEPS);
    	}
    	plot.plotOffset = -gridMid+positionOffset;
    	
    	plot.gridMult = maxy/gridMax;
    	
    	int minRangeLo = -10-(int) (gridMid*plot.gridMult);
    	int minRangeHi =  10-(int) (gridMid*plot.gridMult);
    	if (!isManualScale()) {
    	    gridStepY = 1e-8;    	
        	while (gridStepY < 20*gridMax/maxy) {
      			gridStepY *=multa[(multptr++)%3];
        	}
    	} else {
    	    gridStepY = plot.manScale;
    	}

    	String minorDiv = "#404040";
    	String majorDiv = "#A0A0A0";
    	if (sim.printableCheckItem.getState()) {
    	    minorDiv = "#D0D0D0";
    	    majorDiv = "#808080";
    	    curColor = "#A0A000";
    	}
    	if (allSelected)
    	    majorDiv = CircuitElm.selectColor.getHexValue();
    	
    	// Vertical (T) gridlines
    	double ts = sim.maxTimeStep*speed;
    	gridStepX = calcGridStepX();

    	boolean highlightCenter = !isManualScale();
    	
    	if (drawGridLines) {
    	    // horizontal gridlines
    	    
    	    // don't show hgridlines if lines are too close together (except for center line)
    	    boolean showHGridLines = (gridStepY != 0) && (isManualScale() || allPlotsSameUnits); // Will only show center line if we have mixed units
    	    for (int ll = -100; ll <= 100; ll++) {
    		if (ll != 0 && !showHGridLines)
    		    continue;
    		int yl = maxy-(int) ((ll*gridStepY-gridMid)*plot.gridMult);
    		if (yl < 0 || yl >= rect.height-1)
    		    continue;
    		col = ll == 0 && highlightCenter ? majorDiv : minorDiv;
    		g.setColor(col);
    		g.drawLine(0,yl,rect.width-1,yl);
    	    }
    	    
    	    // vertical gridlines (time axis)
    	    if (drawFromZero && !plot2d) {
    		// Draw from zero mode: gridlines start at t=0 on left
    		double elapsedTime = sim.t - startTime;
    		double displayTimeSpan;
    		
    		if (autoScaleTime && elapsedTime > 0) {
    		    // Auto-scale: time span covers entire simulation from start
    		    displayTimeSpan = elapsedTime;
    		} else {
    		    // Fixed scale: time span is fixed based on speed
    		    displayTimeSpan = ts * rect.width;
    		}
    		
    		// Adjust gridStepX if gridlines are too close together
    		// Calculate pixel spacing with current gridStepX
    		double pixelSpacing = rect.width * gridStepX / displayTimeSpan;
    		int scalePtr = 0;
    		while (pixelSpacing < 20 && displayTimeSpan > 0) {
    		    // Gridlines too close - increase spacing using standard scale pattern
    		    gridStepX *= multa[scalePtr % 3];
    		    pixelSpacing = rect.width * gridStepX / displayTimeSpan;
    		    scalePtr++;
    		}
    		
    		// Align gridlines to nice intervals starting from t=0
    		double gridStart = startTime - (startTime % gridStepX);
    		
    		for (int ll = 0; ; ll++) {
    		    double tl = gridStart + gridStepX * ll;
    		    if (tl < startTime)
    			continue;
    		    
    		    // Calculate pixel position based on time since start
    		    double timeFromStart = tl - startTime;
    		    int gx = (int) (rect.width * timeFromStart / displayTimeSpan);
    		    
    		    if (gx < 0)
    			continue;
    		    if (gx >= rect.width)
    			break;
    		    
    		    col = minorDiv;
    		    if (((tl + gridStepX/4) % (gridStepX*10)) < gridStepX) {
    			col = majorDiv;
    		    }
    		    g.setColor(col);
    		    g.drawLine(gx, 0, gx, rect.height-1);
    		}
    		
    		// Draw t=0 line in highlighted color
    		g.setColor(majorDiv);
    		g.drawLine(0, 0, 0, rect.height-1);
    		
    		// Draw action time markers
    		drawActionTimeMarkers(g, startTime, displayTimeSpan);
    	    } else {
    		// Normal scrolling mode: gridlines scroll with time
    		double tstart = sim.t-sim.maxTimeStep*speed*rect.width;
    		double tx = sim.t-(sim.t % gridStepX);

    		for (int ll = 0; ; ll++) {
    		    double tl = tx-gridStepX*ll;
    		    int gx = (int) ((tl-tstart)/ts);
    		    if (gx < 0)
    			break;
    		    if (gx >= rect.width)
    			continue;
    		    if (tl < 0)
    			continue;
    		    col = minorDiv;
    		    // first = 0;
    		    if (((tl+gridStepX/4) % (gridStepX*10)) < gridStepX) {
    			col = majorDiv;
    		    }
    		    g.setColor(col);
    		    g.drawLine(gx,0,gx,rect.height-1);
    		}
    	    }
    	}
    	
    	// only need gridlines drawn once
    	drawGridLines = false;

        g.setColor(color);
        
        if (isManualScale()) {
            // draw zero point
            int y0= maxy-(int) (plot.gridMult*plot.plotOffset);
            g.drawLine(0, y0, 8, y0);
            g.drawString("0", 0, y0-2);
        }
        
        // Optimize: Use batched drawing for scope waveform
        // This reduces thousands of context.beginPath()/stroke() calls to just 2
        g.startBatch();
        
        int ox = -1, oy = -1;
        int prevMaxY = -1;  // Track previous max Y point for connecting lines
        
        if (drawFromZero && !plot2d) {
            // Draw from zero mode: use history buffers instead of circular buffer
            if (plot.historyMinValues == null || historySize == 0) {
        	// No history data yet
        	g.endBatch();
        	return;
            }
            
            double[] histMinV = plot.historyMinValues;
            double[] histMaxV = plot.historyMaxValues;
            
            if (autoScaleTime) {
                // Auto-scale: map entire history to window width
                for (i = 0; i < rect.width; i++) {
                    // Map pixel to history index
                    int histIdx = (i * historySize) / rect.width;
                    if (histIdx >= historySize)
                        histIdx = historySize - 1;
                    
                    int minvy = (int) (plot.gridMult*(histMinV[histIdx]+plot.plotOffset));
                    int maxvy = (int) (plot.gridMult*(histMaxV[histIdx]+plot.plotOffset));
                    
                    if (minvy < minRangeLo || maxvy > minRangeHi) {
                        reduceRange[plot.units] = false;
                        minRangeLo = -1000;
                        minRangeHi = 1000;
                    }
                    
                    // Clamp Y coordinates to valid drawing range to prevent overlapping axis
                    int y1 = maxy - minvy;
                    int y2 = maxy - maxvy;
                    y1 = Math.max(0, Math.min(rect.height - 1, y1));
                    y2 = Math.max(0, Math.min(rect.height - 1, y2));
                    
                    // Draw vertical segment for this pixel (captures min/max range)
                    if (y1 != y2) {
                        g.drawLine(x+i, y1, x+i, y2);
                    }
                    
                    // Connect to previous point with diagonal line
                    if (prevMaxY != -1) {
                        // Draw from previous max to current min (for continuity)
                        int prevY = Math.max(0, Math.min(rect.height - 1, maxy - prevMaxY));
                        g.drawLine(x+i-1, prevY, x+i, y1);
                    }
                    
                    prevMaxY = maxvy;
                    
                    if (ox != -1) {
                        if (minvy == oy && maxvy == oy)
                            continue;
                        int oyY = Math.max(0, Math.min(rect.height - 1, maxy - oy));
                        g.drawLine(ox, oyY, x+i, oyY);
                        ox = oy = -1;
                    }
                    if (minvy == maxvy) {
                        ox = x+i;
                        oy = minvy;
                        continue;
                    }
                }
            } else {
                // Fixed scale: show most recent data that fits
                double elapsedTime = sim.t - startTime;
                double timePerPixel = sim.maxTimeStep * speed;
                int pixelsNeeded = (int)(elapsedTime / timePerPixel);
                int pixelsUsed = Math.min(pixelsNeeded, rect.width);
                
                if (pixelsUsed < rect.width) {
                    // Not enough data to fill screen yet, start from beginning
                    for (i = 0; i < pixelsUsed; i++) {
                        double time = i * timePerPixel;
                        int histIdx = (int)(time / historySampleInterval);
                        if (histIdx >= historySize)
                            histIdx = historySize - 1;
                        
                        int minvy = (int) (plot.gridMult*(histMinV[histIdx]+plot.plotOffset));
                        int maxvy = (int) (plot.gridMult*(histMaxV[histIdx]+plot.plotOffset));
                        
                        if (minvy < minRangeLo || maxvy > minRangeHi) {
                            reduceRange[plot.units] = false;
                            minRangeLo = -1000;
                            minRangeHi = 1000;
                        }
                        
                        // Clamp Y coordinates to valid drawing range to prevent overlapping axis
                        int y1 = maxy - minvy;
                        int y2 = maxy - maxvy;
                        y1 = Math.max(0, Math.min(rect.height - 1, y1));
                        y2 = Math.max(0, Math.min(rect.height - 1, y2));
                        
                        // Draw vertical segment for this pixel
                        if (y1 != y2) {
                            g.drawLine(x+i, y1, x+i, y2);
                        }
                        
                        // Connect to previous point
                        if (prevMaxY != -1) {
                            int prevY = Math.max(0, Math.min(rect.height - 1, maxy - prevMaxY));
                            g.drawLine(x+i-1, prevY, x+i, y1);
                        }
                        
                        prevMaxY = maxvy;
                        
                        if (ox != -1) {
                            if (minvy == oy && maxvy == oy)
                                continue;
                            int oyY = Math.max(0, Math.min(rect.height - 1, maxy - oy));
                            g.drawLine(ox, oyY, x+i, oyY);
                            ox = oy = -1;
                        }
                        if (minvy == maxvy) {
                            ox = x+i;
                            oy = minvy;
                            continue;
                        }
                    }
                } else {
                    // Screen is full, show most recent window
                    double windowTimeSpan = rect.width * timePerPixel;
                    double startTime = elapsedTime - windowTimeSpan;
                    int startPixel = 0;
                    
                    for (i = startPixel; i < rect.width; i++) {
                        double time = startTime + i * timePerPixel;
                        int histIdx = (int)(time / historySampleInterval);
                        if (histIdx < 0) histIdx = 0;
                        if (histIdx >= historySize) histIdx = historySize - 1;
                        
                        int minvy = (int) (plot.gridMult*(histMinV[histIdx]+plot.plotOffset));
                        int maxvy = (int) (plot.gridMult*(histMaxV[histIdx]+plot.plotOffset));
                        
                        if (minvy < minRangeLo || maxvy > minRangeHi) {
                            reduceRange[plot.units] = false;
                            minRangeLo = -1000;
                            minRangeHi = 1000;
                        }
                        
                        // Clamp Y coordinates to valid drawing range to prevent overlapping axis
                        int y1 = maxy - minvy;
                        int y2 = maxy - maxvy;
                        y1 = Math.max(0, Math.min(rect.height - 1, y1));
                        y2 = Math.max(0, Math.min(rect.height - 1, y2));
                        
                        // Draw vertical segment for this pixel
                        if (y1 != y2) {
                            g.drawLine(x+i, y1, x+i, y2);
                        }
                        
                        // Connect to previous point
                        if (prevMaxY != -1) {
                            int prevY = Math.max(0, Math.min(rect.height - 1, maxy - prevMaxY));
                            g.drawLine(x+i-1, prevY, x+i, y1);
                        }
                        
                        prevMaxY = maxvy;
                        
                        if (ox != -1) {
                            if (minvy == oy && maxvy == oy)
                                continue;
                            int oyY = Math.max(0, Math.min(rect.height - 1, maxy - oy));
                            g.drawLine(ox, oyY, x+i, oyY);
                            ox = oy = -1;
                        }
                        if (minvy == maxvy) {
                            ox = x+i;
                            oy = minvy;
                            continue;
                        }
                    }
                }
            }
        } else {
            // Original right-to-left scrolling behavior
            for (i = 0; i != rect.width; i++) {
                int ip = (i+ipa) & (scopePointCount-1);
                int minvy = (int) (plot.gridMult*(minV[ip]+plot.plotOffset));
                int maxvy = (int) (plot.gridMult*(maxV[ip]+plot.plotOffset));
                
                if (minvy < minRangeLo || maxvy > minRangeHi) {
                    // we got a value outside min range, so we don't need to rescale later
                    reduceRange[plot.units] = false;
                    minRangeLo = -1000;
                    minRangeHi = 1000; // avoid triggering this test again
                }
                
                // Clamp Y coordinates to valid drawing range to prevent overlapping axis
                int y1 = maxy - minvy;
                int y2 = maxy - maxvy;
                y1 = Math.max(0, Math.min(rect.height - 1, y1));
                y2 = Math.max(0, Math.min(rect.height - 1, y2));
                
                // Draw vertical segment for this pixel
                if (y1 != y2) {
                    g.drawLine(x+i, y1, x+i, y2);
                }
                
                // Connect to previous point
                if (prevMaxY != -1) {
                    int prevY = Math.max(0, Math.min(rect.height - 1, maxy - prevMaxY));
                    g.drawLine(x+i-1, prevY, x+i, y1);
                }
                
                prevMaxY = maxvy;
                
                if (ox != -1) {
                    if (minvy == oy && maxvy == oy)
                        continue;
                    int oyY = Math.max(0, Math.min(rect.height - 1, maxy - oy));
                    g.drawLine(ox, oyY, x+i, oyY);
                    ox = oy = -1;
                }
                if (minvy == maxvy) {
                    ox = x+i;
                    oy = minvy;
                    continue;
                }
            } // for (i=0...)
        }
        
        if (ox != -1) {
            int oyY = Math.max(0, Math.min(rect.height - 1, maxy - oy));
            g.drawLine(ox, oyY, x+i-1, oyY); // Horizontal
        }
        
        g.endBatch();
        
    }

    static void clearCursorInfo() {
	cursorScope = null;
	cursorTime = -1;
    }
    
    void selectScope(int mouseX, int mouseY, boolean mouseButtonDown) {
	if (!rect.contains(mouseX, mouseY)) {
	    // Clear mouse position when outside scope
	    this.mouseX = -1;
	    this.mouseY = -1;
	    this.mouseButtonDown = false;
	    return;
	}
	
	// Store mouse position and button state relative to scope rectangle
	this.mouseX = mouseX - rect.x;
	this.mouseY = mouseY - rect.y;
	this.mouseButtonDown = mouseButtonDown;
	
	if (plot2d || visiblePlots.size() == 0)
	    cursorTime = -1;
	else {
	    if (drawFromZero) {
		// Time is proportional to x position from left
		double elapsedTime = sim.t - startTime;
		double relativeX = (mouseX - rect.x) / (double)rect.width;
		
		if (autoScaleTime && elapsedTime > 0) {
		    // Mouse position maps directly to elapsed time
		    cursorTime = startTime + (relativeX * elapsedTime);
		} else {
		    // Mouse position maps to actual time with fixed scale
		    cursorTime = startTime + (relativeX * rect.width * sim.maxTimeStep * speed);
		}
	    } else {
		// Original right-to-left scrolling calculation
		cursorTime = sim.t-sim.maxTimeStep*speed*(rect.x+rect.width-mouseX);
	    }
	}
    	checkForSelection(mouseX, mouseY);
    	cursorScope = this;
    }
    
    // find selected plot
    void checkForSelection(int mouseX, int mouseY) {
	if (sim.dialogIsShowing())
	    return;
	if (!rect.contains(mouseX, mouseY)) {
	    selectedPlot = -1;
	    return;
	}
	if (plots.size() == 0) {
	    selectedPlot = -1;
	    return;
	}
	int ipa = plots.get(0).startIndex(rect.width);
	int ip = (mouseX-rect.x+ipa) & (scopePointCount-1);
    	int maxy = (rect.height-1)/2;
    	int y = maxy;
    	int i;
    	int bestdist = 10000;
    	int best = -1;
    	for (i = 0; i != visiblePlots.size(); i++) {
    	    ScopePlot plot = visiblePlots.get(i);
    	    int maxvy = (int) (plot.gridMult*(plot.maxValues[ip]+plot.plotOffset));
    	    int dist = Math.abs(mouseY-(rect.y+y-maxvy));
    	    if (dist < bestdist) {
    		bestdist = dist;
    		best = i;
    	    }
    	}
    	selectedPlot = best;
    	if (selectedPlot >= 0)
    	    cursorUnits = visiblePlots.get(selectedPlot).units;
    }
    
    void drawCursor(Graphics g) {
    if (sim.dialogIsShowing())
        return;
    if (cursorScope == null)
        return;
    String info[] = new String[5];  // Increased from 4 to 5
    int cursorX = -1;
    int ct = 0;
    
    // Add plot name as first element
    String plotName = getScopeLabelOrText();
    if (plotName != null && !plotName.isEmpty()) {
        info[ct++] = plotName;
    }
    
    if (cursorTime >= 0) {
        // Calculate cursor X position from cursorTime
        if (drawFromZero && !plot2d) {
            // Draw from zero mode: calculate position based on time from start
            double elapsedTime = sim.t - startTime;
            double displayTimeSpan;
            
            if (autoScaleTime && elapsedTime > 0) {
                displayTimeSpan = elapsedTime;
            } else {
                displayTimeSpan = sim.maxTimeStep * speed * rect.width;
            }
            
            double timeFromStart = cursorTime - startTime;
            cursorX = rect.x + (int)(rect.width * timeFromStart / displayTimeSpan);
        } else {
            // Normal scrolling mode
            cursorX = -(int) ((sim.t-cursorTime)/(sim.maxTimeStep*speed) - rect.x - rect.width);
        }
        
        if (cursorX >= rect.x) {
        int maxy = (rect.height-1)/2;
        int y = maxy;
        if (visiblePlots.size() > 0) {
            ScopePlot plot = visiblePlots.get(selectedPlot >= 0 ? selectedPlot : 0);
            double value;
            
            if (drawFromZero && !plot2d && plot.historyMaxValues != null) {
                // Get value from history buffer
                double timeFromStart = cursorTime - startTime;
                int historyIndex = (int)(timeFromStart / historySampleInterval);
                if (historyIndex >= 0 && historyIndex < historySize) {
                    value = plot.historyMaxValues[historyIndex];
                } else {
                    value = 0;
                }
            } else {
                // Get value from circular buffer
                int ipa = plots.get(0).startIndex(rect.width);
                int ip = (cursorX-rect.x+ipa) & (scopePointCount-1);
                value = plot.maxValues[ip];
            }
            
            info[ct++] = plot.getUnitText(value);
            int maxvy = (int) (plot.gridMult*(value+plot.plotOffset));
            g.setColor(plot.color);
            g.fillOval(cursorX-2, rect.y+y-maxvy-2, 5, 5);
        }
        }
    }
    
    // show FFT even if there's no plots (in which case cursorTime/cursorX will be invalid)
        if (showFFT && cursorScope == this) {
            double maxFrequency = 1 / (sim.maxTimeStep * speed * 2);
            if (cursorX < 0)
            cursorX = sim.mouseCursorX;
            info[ct++] = CircuitElm.getUnitText(maxFrequency*(sim.mouseCursorX-rect.x)/rect.width, "Hz");
        } else if (cursorX < rect.x)
            return;
        
    if (visiblePlots.size() > 0)
        info[ct++] = sim.formatTimeFixed(cursorTime);
    
    if (cursorScope != this) {
        // don't show cursor info if not enough room, or stacked with selected one
        // (position == -1 for embedded scopes)
        if (rect.height < 40 || (position >= 0 && cursorScope.position == position)) {
        drawCursorInfo(g, null, 0, cursorX, false);
        return;
        }
    }
    drawCursorInfo(g, info, ct, cursorX, false);
    }
    
    void drawCursorInfo(Graphics g, String[] info, int ct, int x, Boolean drawY) {
	int szw = 0, szh = 15*ct;
	int i;
	for (i = 0; i != ct; i++) {
	    int w=(int)g.context.measureText(info[i]).getWidth();
	    if (w > szw)
		szw = w;
	}

	g.setColor(CircuitElm.whiteColor);
	g.drawLine(x, rect.y, x, rect.y+rect.height);
	if (drawY)
	    g.drawLine(rect.x, sim.mouseCursorY, rect.x+rect.width, sim.mouseCursorY);
	g.setColor(sim.printableCheckItem.getState() ? Color.white : Color.black);
	int bx = x;
	if (bx < szw/2)
	    bx = szw/2;
	g.fillRect(bx-szw/2, rect.y-szh, szw, szh);
	g.setColor(CircuitElm.whiteColor);
	for (i = 0; i != ct; i++) {
	    int w=(int)g.context.measureText(info[i]).getWidth();
	    g.drawString(info[i], bx-w/2, rect.y-2-(ct-1-i)*15);
	}
	
    }

    boolean canShowRMS() {
	if (visiblePlots.size() == 0)
	    return false;
	ScopePlot plot = visiblePlots.firstElement();
	return (plot.units == Scope.UNITS_V || plot.units == Scope.UNITS_A);
    }
    
    // calc RMS and display it
    void drawRMS(Graphics g) {
	if (!canShowRMS()) {
	    // needed for backward compatibility
	    showRMS = false;
	    showAverage = true;
	    drawAverage(g);
	    return;
	}
	ScopePlot plot = visiblePlots.firstElement();
	int i;
	double avg = 0;
    	int ipa = plot.ptr+scopePointCount-rect.width;
    	double maxV[] = plot.maxValues;
    	double minV[] = plot.minValues;
    	double mid = (maxValue+minValue)/2;
	int state = -1;
	
	// skip zeroes
	for (i = 0; i != rect.width; i++) {
	    int ip = (i+ipa) & (scopePointCount-1);
	    if (maxV[ip] != 0) {
		if (maxV[ip] > mid)
		    state = 1;
		break;
	    }
	}
	int firstState = -state;
	int start = i;
	int end = 0;
	int waveCount = 0;
	double endAvg = 0;
	for (; i != rect.width; i++) {
	    int ip = (i+ipa) & (scopePointCount-1);
	    boolean sw = false;
	    
	    // switching polarity?
	    if (state == 1) {
		if (maxV[ip] < mid)
		    sw = true;
	    } else if (minV[ip] > mid)
		sw = true;
	    
	    if (sw) {
		state = -state;
		
		// completed a full cycle?
		if (firstState == state) {
		    if (waveCount == 0) {
			start = i;
			firstState = state;
			avg = 0;
		    }
		    waveCount++;
		    end = i;
		    endAvg = avg;
		}
	    }
	    if (waveCount > 0) {
		double m = (maxV[ip]+minV[ip])*.5;
		avg += m*m;
	    }
	}
	double rms;
	if (waveCount > 1) {
	    rms = Math.sqrt(endAvg/(end-start));
	    drawInfoText(g, plot.getUnitText(rms) + "rms");
	}
    }
    
    void drawScale(ScopePlot plot, Graphics g) {
    	    if (!isManualScale()) {
        	    if ( gridStepY!=0 && (!(showV && showI))) {
        		String vScaleText=" V=" + plot.getUnitText(gridStepY)+"/div";
        	    	drawInfoText(g, "H="+CircuitElm.getUnitText(displayGridStepX, "s")+"/div" + vScaleText);
        	    }
    	    }  else {
    		if (rect.y + rect.height <= textY+5)
    		    return;
    		double x = 0;
    		String hs = "H="+CircuitElm.getUnitText(displayGridStepX, "s")+"/div";
    		g.drawString(hs, 0, textY);
    		x+=g.measureWidth(hs);
		final double bulletWidth = 17;
    		for (int i=0; i<visiblePlots.size(); i++) {
    		    ScopePlot p=visiblePlots.get(i);
    		    String s=p.getUnitText(p.manScale);
    		    if (p!=null) {
    			String vScaleText="="+s+"/div";
    			double vScaleWidth=g.measureWidth(vScaleText);
    			if (x+bulletWidth+vScaleWidth > rect.width) {
    			    x=0;
    			    textY += 15;
    			    if (rect.y + rect.height <= textY+5)
    	    		    	return;
    			}
    			g.setColor(p.color);
    			g.fillOval((int)x+7, textY-9, 8, 8);
    			x+=bulletWidth;
    			g.setColor(CircuitElm.whiteColor);
    			g.drawString(vScaleText, (int)x, textY);
    			x+=vScaleWidth;
    		    }
    		}
    		textY += 15;
    	    }

	
    }
    
    void drawAverage(Graphics g) {
	ScopePlot plot = visiblePlots.firstElement();
	int i;
	double avg = 0;
    	int ipa = plot.ptr+scopePointCount-rect.width;
    	double maxV[] = plot.maxValues;
    	double minV[] = plot.minValues;
    	double mid = (maxValue+minValue)/2;
	int state = -1;
	
	// skip zeroes
	for (i = 0; i != rect.width; i++) {
	    int ip = (i+ipa) & (scopePointCount-1);
	    if (maxV[ip] != 0) {
		if (maxV[ip] > mid)
		    state = 1;
		break;
	    }
	}
	int firstState = -state;
	int start = i;
	int end = 0;
	int waveCount = 0;
	double endAvg = 0;
	for (; i != rect.width; i++) {
	    int ip = (i+ipa) & (scopePointCount-1);
	    boolean sw = false;
	    
	    // switching polarity?
	    if (state == 1) {
		if (maxV[ip] < mid)
		    sw = true;
	    } else if (minV[ip] > mid)
		sw = true;
	    
	    if (sw) {
		state = -state;
		
		// completed a full cycle?
		if (firstState == state) {
		    if (waveCount == 0) {
			start = i;
			firstState = state;
			avg = 0;
		    }
		    waveCount++;
		    end = i;
		    endAvg = avg;
		}
	    }
	    if (waveCount > 0) {
		double m = (maxV[ip]+minV[ip])*.5;
		avg += m;
	    }
	}
	if (waveCount > 1) {
	    avg = (endAvg/(end-start));
	    drawInfoText(g, plot.getUnitText(avg) + Locale.LS(" average"));
	}
    }

    void drawDutyCycle(Graphics g) {
	ScopePlot plot = visiblePlots.firstElement();
	int i;
    	int ipa = plot.ptr+scopePointCount-rect.width;
    	double maxV[] = plot.maxValues;
    	double minV[] = plot.minValues;
    	double mid = (maxValue+minValue)/2;
	int state = -1;
	
	// skip zeroes
	for (i = 0; i != rect.width; i++) {
	    int ip = (i+ipa) & (scopePointCount-1);
	    if (maxV[ip] != 0) {
		if (maxV[ip] > mid)
		    state = 1;
		break;
	    }
	}
	int firstState = 1;
	int start = i;
	int end = 0;
	int waveCount = 0;
	int dutyLen = 0;
	int middle = 0;
	for (; i != rect.width; i++) {
	    int ip = (i+ipa) & (scopePointCount-1);
	    boolean sw = false;
	    
	    // switching polarity?
	    if (state == 1) {
		if (maxV[ip] < mid)
		    sw = true;
	    } else if (minV[ip] > mid)
		sw = true;
	    
	    if (sw) {
		state = -state;
		
		// completed a full cycle?
		if (firstState == state) {
		    if (waveCount == 0) {
			start = end = i;
		    } else {
			end = start;
			start = i;
			dutyLen = end-middle;
		    }
		    waveCount++;
		} else
		    middle = i;
	    }
	}
	if (waveCount > 1) {
	    int duty = 100*dutyLen/(end-start);
	    drawInfoText(g, Locale.LS("Duty cycle ") + duty + "%");
	}
    }

    // calc frequency if possible and display it
    void drawFrequency(Graphics g) {
	// try to get frequency
	// get average
	double avg = 0;
	int i;
	ScopePlot plot = visiblePlots.firstElement();
    	int ipa = plot.ptr+scopePointCount-rect.width;
    	double minV[] = plot.minValues;
    	double maxV[] = plot.maxValues;
	for (i = 0; i != rect.width; i++) {
	    int ip = (i+ipa) & (scopePointCount-1);
	    avg += minV[ip]+maxV[ip];
	}
	avg /= i*2;
	int state = 0;
	double thresh = avg*.05;
	int oi = 0;
	double avperiod = 0;
	int periodct = -1;
	double avperiod2 = 0;
	// count period lengths
	for (i = 0; i != rect.width; i++) {
	    int ip = (i+ipa) & (scopePointCount-1);
	    double q = maxV[ip]-avg;
	    int os = state;
	    if (q < thresh)
		state = 1;
	    else if (q > -thresh)
		state = 2;
	    if (state == 2 && os == 1) {
		int pd = i-oi;
		oi = i;
		// short periods can't be counted properly
		if (pd < 12)
		    continue;
		// skip first period, it might be too short
		if (periodct >= 0) {
		    avperiod += pd;
		    avperiod2 += pd*pd;
		}
		periodct++;
	    }
	}
	avperiod /= periodct;
	avperiod2 /= periodct;
	double periodstd = Math.sqrt(avperiod2-avperiod*avperiod);
	double freq = 1/(avperiod*sim.maxTimeStep*speed);
	// don't show freq if standard deviation is too great
	if (periodct < 1 || periodstd > 2)
	    freq = 0;
	// System.out.println(freq + " " + periodstd + " " + periodct);
	if (freq != 0)
	    drawInfoText(g, CircuitElm.getUnitText(freq, "Hz"));
    }

    void drawElmInfo(Graphics g) {
	String info[] = new String[1];
	getElm().getInfo(info);
	int i;
	for (i = 0; info[i] != null; i++)
	    drawInfoText(g, info[i]);
    }
    
    int textY;
    
    void drawInfoText(Graphics g, String text) {
	if (rect.y + rect.height <= textY+5)
	    return;
	g.drawString(text, 0, textY);
	textY += 15;
    }
    
    /**
     * Draws the scope title at the top right of the display.
     * @param g Graphics context
     */
    void drawTitle(Graphics g) {
	if (title == null || title.isEmpty())
	    return;
	
	g.context.save();
	g.setColor(CircuitElm.whiteColor);
	
	// Measure the text width
	g.context.setFont("bold 14px sans-serif");
	double textWidth = g.context.measureText(title).getWidth();
	
	// Draw at top right with 20px margin
	int x = (int)(rect.width - textWidth - 20);
	int y = 15; // Top margin
	
	g.context.fillText(title, x, y);
	g.context.restore();
    }
    
    void drawInfoTexts(Graphics g) {
    	g.setColor(CircuitElm.whiteColor);
    	textY = 10;
    	
    	if (visiblePlots.size() == 0) {
    	    if (showElmInfo)
    		drawElmInfo(g);
    	    return;
    	}
    	ScopePlot plot = visiblePlots.firstElement();
    	if (showScale) 
    	    drawScale(plot, g);
//    	if (showMax || showMin)
//    	    calcMaxAndMin(plot.units);
    	if (showMax)
    	    drawInfoText(g, "Max="+plot.getUnitText(maxValue));
    	if (showMin) {
    	    int ym=rect.height-5;
    	    g.drawString("Min="+plot.getUnitText(minValue), 0, ym);
    	}
    	if (showRMS)
    	    drawRMS(g);
    	if (showAverage)
    	    drawAverage(g);
    	if (showDutyCycle)
    	    drawDutyCycle(g);
    	
    	// Draw plot names in their respective colors
    	if (visiblePlots.size() >= 1) {
    	    // Show each plot name with its own color and current value
    	    for (int i = 0; i < visiblePlots.size(); i++) {
    		ScopePlot p = visiblePlots.get(i);
    		if (p.elm != null) {
    		    String plotText = p.elm.getScopeText(p.value);
    		    if (plotText != null && !plotText.isEmpty()) {
    			if (rect.y + rect.height <= textY+5)
    			    break;
    			g.setColor(p.color);
    			// Add current value to the plot name
    			String valueText = p.getUnitText(p.lastValue);
    			g.drawString(Locale.LS(plotText) + ": " + valueText, 0, textY);
    			textY += 15;
    		    }
    		}
    	    }
    	    g.setColor(CircuitElm.whiteColor);
    	} else {
    	    // Fallback for no plots - use getScopeLabelOrText
    	    String t = getScopeLabelOrText(true);
    	    if (t != null &&  t!= "") 
    		drawInfoText(g, t);
    	}
    	
    	if (showFreq)
    	    drawFrequency(g);
    	if (showElmInfo)
    	    drawElmInfo(g);
    }

    String getScopeText() {
	// stacked scopes?  don't show text
	if (stackCount != 1)
	    return null;
	
	// multiple elms?  don't show text (unless one is selected)
	if (selectedPlot < 0 && getSingleElm() == null)
	    return null;
	
	// no visible plots?
	if (visiblePlots.size() == 0)
	    return null;
	
	ScopePlot plot = visiblePlots.firstElement();
	if (selectedPlot >= 0 && visiblePlots.size() > selectedPlot)
	    plot = visiblePlots.get(selectedPlot);
	if (plot.elm == null)
		return "";
	else
	    	return plot.elm.getScopeText(plot.value);
    }

    String getScopeLabelOrText() {
	return getScopeLabelOrText(false);
    }

    String getScopeLabelOrText(boolean forInfo) {
    	String t = text;
    	if (t == null) {
    	    // if we're drawing the info and showElmInfo is true, return null so we don't print redundant info.
    	    // But don't do that if we're getting the scope label to generate "Add to Existing Scope" menu.
    	    if (forInfo && showElmInfo)
    		return null;
    	    t = getScopeText();
    	    if (t==null)
    		return "";
    	    return Locale.LS(t);
    	}
    	else
    	    return t;
    }
    
    /**
     * Gets the scope name for display in menus.
     * Prioritizes title over label text.
     * @return Display name (title if available, otherwise label text, or empty string)
     */
    String getScopeMenuName() {
	if (title != null && !title.isEmpty())
	    return title;
	return getScopeLabelOrText();
    }
    
    void setSpeed(int sp) {
	if (sp < 1)
	    sp = 1;
	if (sp > 1024)
	    sp = 1024;
	speed = sp;
	resetGraph();
    }
    
    void properties() {
	properties = new ScopePropertiesDialog(sim, this);
	// CirSim.dialogShowing = properties;
    }
    
    void speedUp() {
	// Don't change speed in Draw From Zero mode (auto-scale manages time scale)
	if (drawFromZero)
	    return;
	if (speed > 1) {
	    speed /= 2;
	    // Preserve history when in drawFromZero mode
	    resetGraph(false, !drawFromZero);
	}
    }

    void slowDown() {
	// Don't change speed in Draw From Zero mode (auto-scale manages time scale)
	if (drawFromZero)
	    return;
	if (speed < 1024)
	    speed *= 2;
	// Preserve history when in drawFromZero mode
    	resetGraph(false, !drawFromZero);
    }
    
    void setPlotPosition(int plot, int v) {
	visiblePlots.get(plot).manVPosition = v;
    }
	
    // get scope element, returning null if there's more than one
    CircuitElm getSingleElm() {
	CircuitElm elm = plots.get(0).elm;
	int i;
	for (i = 1; i < plots.size(); i++) {
	    if (plots.get(i).elm != elm)
		return null;
	}
	return elm;
    }
    
    boolean canMenu() {
    	return (plots.get(0).elm != null);
    }
    
    boolean canShowResistance() {
    	CircuitElm elm = getSingleElm();
    	return elm != null && elm.canShowValueInScope(VAL_R);
    }
    
    boolean isShowingVceAndIc() {
	return plot2d && plots.size() == 2 && plots.get(0).value == VAL_VCE && plots.get(1).value == VAL_IC;
    }

    int getFlags() {
    	int flags = (showI ? 1 : 0) | (showV ? 2 : 0) |
			(showMax ? 0 : 4) |   // showMax used to be always on
			(showFreq ? 8 : 0) |
			// In this version we always dump manual settings using the PERPLOT format
			(isManualScale() ? (FLAG_MAN_SCALE | FLAG_PERPLOT_MAN_SCALE): 0) |
			(plot2d ? 64 : 0) |
			(plotXY ? 128 : 0) | (showMin ? 256 : 0) | (showScale? 512:0) |
			(showFFT ? 1024 : 0) | (maxScale ? 8192 : 0) | (showRMS ? 16384 : 0) |
			(showDutyCycle ? 32768 : 0) | (logSpectrum ? 65536 : 0) |
			(showAverage ? (1<<17) : 0) | (showElmInfo ? (1<<20) : 0);
	flags |= FLAG_PLOTS; // 4096
	int allPlotFlags = 0;
	for (ScopePlot p : plots) {
	    allPlotFlags |= p.getPlotFlags();
	
	}
	// If none of our plots has a flag set we will use the old format with no plot flags, or
	// else we will set FLAG_PLOTFLAGS and include flags in all plots
	flags |= (allPlotFlags !=0) ? FLAG_PERPLOTFLAGS :0; // (1<<18)

	if (isManualScale())
	    flags |= FLAG_DIVISIONS;
	
	// Add new flags for drawFromZero feature
	flags |= (drawFromZero ? FLAG_DRAW_FROM_ZERO : 0);
	flags |= (autoScaleTime ? FLAG_AUTO_SCALE_TIME : 0);
	
	// Add flag for max scale limits
	boolean hasMaxLimits = false;
	for (int i = 0; i < UNITS_COUNT; i++) {
	    if (maxScaleLimit[i] != null) {
		hasMaxLimits = true;
		break;
	    }
	}
	if (hasMaxLimits)
	    flags |= FLAG_MAX_SCALE_LIMITS;
	
	return flags;
    }
    

    
    String dump() {
	ScopePlot vPlot = plots.get(0);
	
	CircuitElm elm = vPlot.elm;
    	if (elm == null)
    		return null;
    	int flags = getFlags();
    	int eno = sim.locateElm(elm);
    	if (eno < 0)
    		return null;
    	String x = "o " + eno + " " +
    			vPlot.scopePlotSpeed + " " + vPlot.value + " " 
    			+ exportAsDecOrHex(flags, FLAG_PERPLOTFLAGS) + " " +
    			scale[UNITS_V] + " " + scale[UNITS_A] + " " + position + " " +
    			plots.size();
	if ((flags & FLAG_DIVISIONS) != 0)
	    x += " " + manDivisions;
	
	// Dump max scale limits if any are set
	if ((flags & FLAG_MAX_SCALE_LIMITS) != 0) {
	    for (int i = 0; i < UNITS_COUNT; i++) {
		if (maxScaleLimit[i] != null)
		    x += " L" + i + ":" + maxScaleLimit[i];
	    }
	}
	
    	int i;
    	for (i = 0; i < plots.size(); i++) {
    	    ScopePlot p = plots.get(i);
    	    if ((flags & FLAG_PERPLOTFLAGS) !=0)
    		x += " " + Integer.toHexString(p.getPlotFlags()); // NB always export in Hex (no prefix)
    	    if (i > 0)
    		x += " " + sim.locateElm(p.elm) + " " + p.value;
    	    // dump scale if units are not V or A
    	    if (p.units > UNITS_A)
    		x += " " + scale[p.units];
    	    if (isManualScale()) {// In this version we always dump manual settings using the PERPLOT format
    	        x += " " + p.manScale + " "  
    		+ p.manVPosition;
    	    }
    	}
    	if (text != null)
    	    	x += " " + CustomLogicModel.escape(text);
    	if (title != null)
    	    	x += " T:" + CustomLogicModel.escape(title);
    	return x;
    }
    
    void undump(StringTokenizer st) {
    	initialize();
    	int e = new Integer(st.nextToken()).intValue();
    	if (e == -1)
    		return;
    	CircuitElm ce = sim.getElm(e);
    	setElm(ce);
    	speed = new Integer(st.nextToken()).intValue();
    	int value = new Integer(st.nextToken()).intValue();
    	
    	// fix old value for VAL_POWER which doesn't work for transistors (because it's the same as VAL_IB) 
    	if (!(ce instanceof TransistorElm) && value == VAL_POWER_OLD)
    	    value = VAL_POWER;
    	
    	int flags = importDecOrHex(st.nextToken());
    	scale[UNITS_V] = new Double(st.nextToken()).doubleValue();
    	scale[UNITS_A] = new Double(st.nextToken()).doubleValue();
    	if (scale[UNITS_V] == 0)
    	    scale[UNITS_V] = .5;
    	if (scale[UNITS_A] == 0)
    	    scale[UNITS_A] = 1;
    	scaleX = scale[UNITS_V];
    	scaleY = scale[UNITS_A];
    	scale[UNITS_OHMS] = scale[UNITS_W] = scale[UNITS_V];
    	text = null;
    	boolean plot2dFlag = (flags & 64) != 0;
    	boolean hasPlotFlags = (flags & FLAG_PERPLOTFLAGS) != 0;
    	boolean hasMaxLimits = (flags & FLAG_MAX_SCALE_LIMITS) != 0;
    	
    	if ((flags & FLAG_PLOTS) != 0) {
    	    // new-style dump
    	    try {
    		position = Integer.parseInt(st.nextToken());
    		int sz = Integer.parseInt(st.nextToken());
		manDivisions = 8;
		if ((flags & FLAG_DIVISIONS) != 0)
		    manDivisions = lastManDivisions = Integer.parseInt(st.nextToken());
		
		// Parse max scale limits if present
		if (hasMaxLimits) {
		    while (st.hasMoreTokens()) {
			String token = st.nextToken();
			if (token.startsWith("L") && token.contains(":")) {
			    // Parse limit token: L<unit>:<value>
			    try {
				int colonPos = token.indexOf(':');
				int unit = Integer.parseInt(token.substring(1, colonPos));
				double limit = Double.parseDouble(token.substring(colonPos + 1));
				maxScaleLimit[unit] = limit;
			    } catch (Exception ex) {
				// Ignore malformed limit tokens
			    }
			} else {
			    // Not a limit token, we need to parse it as the first plot data
			    // Put the token back by creating a new StringTokenizer
			    String remaining = token;
			    while (st.hasMoreTokens())
				remaining += " " + st.nextToken();
			    st = new StringTokenizer(remaining, " ");
			    break;
			}
		    }
		}
		
    		int i;
    		int u = ce.getScopeUnits(value);
		if (u > UNITS_A)
		    scale[u] = Double.parseDouble(st.nextToken());
    		setValue(value);
    		// setValue(0) creates an extra plot for current, so remove that
    		while (plots.size() > 1)
    		    plots.removeElementAt(1);
		
    		int plotFlags = 0;
    		for (i = 0; i != sz; i++) {
    		    if (hasPlotFlags)
    			plotFlags=Integer.parseInt(st.nextToken(), 16); // Import in hex (no prefix)
    		    if (i!=0) {
        		    int ne = Integer.parseInt(st.nextToken());
        		    int val = Integer.parseInt(st.nextToken());
        		    CircuitElm elm = sim.getElm(ne);
        		    u = elm.getScopeUnits(val);
        		    if (u > UNITS_A)
        			scale[u] = Double.parseDouble(st.nextToken());
        		    plots.add(new ScopePlot(elm, u, val, getManScaleFromMaxScale(u, false)));
    		    }
    		    ScopePlot p = plots.get(i);
    		    p.acCoupled = (plotFlags & ScopePlot.FLAG_AC) != 0;
    		    if ( (flags & FLAG_PERPLOT_MAN_SCALE) != 0) {
    			p.manScaleSet = true;
    			p.manScale=Double.parseDouble(st.nextToken());
    			p.manVPosition=Integer.parseInt(st.nextToken());
    		    }
    		}
    		while (st.hasMoreTokens()) {
    		    String token = st.nextToken();
    		    if (token.startsWith("T:")) {
    			// This is the title
    			if (title == null)
    			    title = token.substring(2); // Remove "T:" prefix
    			else
    			    title += " " + token;
    		    } else {
    			// This is the text (label)
    			if (text == null)
    			    text = token;
    			else
    			    text += " " + token;
    		    }
    		}
    	    } catch (Exception ee) {
    	    }
    	} else {
    	    // old-style dump
    	    CircuitElm yElm = null;
    	    int ivalue = 0;
	    manDivisions = 8;
    	    try {
    		position = new Integer(st.nextToken()).intValue();
    		int ye = -1;
    		if ((flags & FLAG_YELM) != 0) {
    		    ye = new Integer(st.nextToken()).intValue();
    		    if (ye != -1)
    			yElm = sim.getElm(ye);
    		    // sinediode.txt has yElm set to something even though there's no xy plot...?
    		    if (!plot2dFlag)
    			yElm = null;
    		}
    		if ((flags & FLAG_IVALUE) !=0) {
    		    ivalue = new Integer(st.nextToken()).intValue();
    		}
    		while (st.hasMoreTokens()) {
    		    String token = st.nextToken();
    		    if (token.startsWith("T:")) {
    			// This is the title
    			if (title == null)
    			    title = token.substring(2); // Remove "T:" prefix
    			else
    			    title += " " + token;
    		    } else {
    			// This is the text (label)
    			if (text == null)
    			    text = token;
    			else
    			    text += " " + token;
    		    }
    		}
    	    } catch (Exception ee) {
    	    }
    	    setValues(value, ivalue, sim.getElm(e), yElm);
    	}
    	if (text != null)
    	    text = CustomLogicModel.unescape(text);
    	if (title != null)
    	    title = CustomLogicModel.unescape(title);
    	plot2d = plot2dFlag;
    	setFlags(flags);
    	
    	// // Check if drawFromZero was loaded and history buffers need initialization
    	// CirSim.console("undump: drawFromZero=" + drawFromZero + ", autoScaleTime=" + autoScaleTime + 
    	//     ", plots.size()=" + plots.size() + ", rect.width=" + rect.width);

    	// Note: Don't call resetGraph here - it will be called by setRect() later with proper dimensions
    	// If we call it here, the rect is still default size and historyCapacity will be too small
    }
    
    void setFlags(int flags) {
    	showI = (flags & 1) != 0;
    	showV = (flags & 2) != 0;
    	showMax = (flags & 4) == 0;
    	showFreq = (flags & 8) != 0;
    	manualScale = (flags & FLAG_MAN_SCALE) != 0;
    	plotXY = (flags & 128) != 0;
    	showMin = (flags & 256) != 0;
    	showScale = (flags & 512) !=0;
    	showFFT((flags & 1024) != 0);
    	maxScale = (flags & 8192) != 0;
    	showRMS = (flags & 16384) != 0;
    	showDutyCycle = (flags & 32768) != 0;
    	logSpectrum = (flags & 65536) != 0;
    	showAverage = (flags & (1<<17)) != 0;
    	showElmInfo = (flags & (1<<20)) != 0;
    	
    	// Load new drawFromZero flags
    	drawFromZero = (flags & FLAG_DRAW_FROM_ZERO) != 0;
    	autoScaleTime = (flags & FLAG_AUTO_SCALE_TIME) != 0;
    }
    
    void saveAsDefault() {
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null)
            return;
	ScopePlot vPlot = plots.get(0);
    	int flags = getFlags();
    	
    	// store current scope settings as default.  1 is a version code
    	stor.setItem("scopeDefaults", "1 " + flags + " " + vPlot.scopePlotSpeed);
    	CirSim.console("saved defaults " + flags);
    }

    boolean loadDefaults() {
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null)
            return false;
        String str = stor.getItem("scopeDefaults");
        if (str == null)
            return false;
        String arr[] = str.split(" ");
        int flags = Integer.parseInt(arr[1]);
        setFlags(flags);
        speed = Integer.parseInt(arr[2]);
        return true;
    }
    
    void allocImage() {
	if (imageCanvas != null) {
	    imageCanvas.setWidth(rect.width + "PX");
	    imageCanvas.setHeight(rect.height + "PX");
	    imageCanvas.setCoordinateSpaceWidth(rect.width);
	    imageCanvas.setCoordinateSpaceHeight(rect.height);
	    clear2dView();
	}
    }
    
    void handleMenu(String mi, boolean state) {
	if (mi == "maxscale")
	    	maxScale();
    	if (mi == "showvoltage")
    		showVoltage(state);
    	if (mi == "showcurrent")
    		showCurrent(state);
    	if (mi=="showscale")
    		showScale(state);
    	if (mi == "showpeak")
    		showMax(state);
    	if (mi == "shownegpeak")
    		showMin(state);
    	if (mi == "showfreq")
    		showFreq(state);
    	if (mi == "showfft")
    		showFFT(state);
    	if (mi == "logspectrum")
    	    	logSpectrum = state;
    	if (mi == "showrms")
    	    	showRMS = state;
    	if (mi == "showaverage")
	    	showAverage = state;
    	if (mi == "showduty")
    	    	showDutyCycle = state;
    	if (mi == "showelminfo")
	    	showElmInfo = state;
    	if (mi == "showpower")
    		setValue(VAL_POWER);
    	if (mi == "showib")
    		setValue(VAL_IB);
    	if (mi == "showic")
    		setValue(VAL_IC);
    	if (mi == "showie")
    		setValue(VAL_IE);
    	if (mi == "showvbe")
    		setValue(VAL_VBE);
    	if (mi == "showvbc")
    		setValue(VAL_VBC);
    	if (mi == "showvce")
    		setValue(VAL_VCE);
    	if (mi == "showvcevsic") {
    		plot2d = true;
    		plotXY = false;
    		setValues(VAL_VCE, VAL_IC, getElm(), null);
    		resetGraph();
    	}

    	if (mi == "showvvsi") {
    		plot2d = state;
    		plotXY = false;
    		resetGraph();
    	}
    	if (mi == "manualscale")
		setManualScale(state, true);
    	if (mi == "plotxy") {
    		plotXY = plot2d = state;
    		if (plot2d)
    		    plots = visiblePlots;
    		if (plot2d && plots.size() == 1)
    		    selectY();
    		resetGraph();
    	}
    	if (mi == "showresistance")
    		setValue(VAL_R);
    	if (mi == "drawfromzero") {
    		drawFromZero = state;
    		if (state) {
    		    startTime = sim.t;
    		}
    		resetGraph();
    	}
    	if (mi == "autoscaletime") {
    		autoScaleTime = state;
    		sim.needAnalyze();
    	}
    }

//    void select() {
//    	sim.setMouseElm(elm);
//    	if (plotXY) {
//    		sim.plotXElm = elm;
//    		sim.plotYElm = yElm;
//    	}
//    }

    void selectY() {
	CircuitElm yElm = (plots.size() == 2) ? plots.get(1).elm : null;
    	int e = (yElm == null) ? -1 : sim.locateElm(yElm);
    	int firstE = e;
    	while (true) {
    	    for (e++; e < sim.elmList.size(); e++) {
    		CircuitElm ce = sim.getElm(e);
    		if ((ce instanceof OutputElm || ce instanceof ProbeElm) &&
    			ce != plots.get(0).elm) {
    		    yElm = ce;
    		    if (plots.size() == 1)
    			plots.add(new ScopePlot(yElm, UNITS_V));
    		    else {
    			plots.get(1).elm = yElm;
    			plots.get(1).units = UNITS_V;
    		    }
    		    return;
    		}
    	    }
    	    if (firstE == -1)
    		return;
    	    e = firstE = -1;
    	}
    	// not reached
    }
    
    void onMouseWheel(MouseWheelEvent e) {
        wheelDeltaY += e.getDeltaY()*sim.wheelSensitivity;
        if (wheelDeltaY > 5) {
            slowDown();
            wheelDeltaY = 0;
        }
        if (wheelDeltaY < -5) {
            speedUp();
	    wheelDeltaY = 0;
    	}
    }
    
    CircuitElm getElm() {
	if (selectedPlot >= 0 && visiblePlots.size() > selectedPlot)
	    return visiblePlots.get(selectedPlot).elm;
	return visiblePlots.size() > 0 ? visiblePlots.get(0).elm : plots.get(0).elm;
    }

    boolean viewingWire() {
	int i;
	for (i = 0; i != plots.size(); i++)
	    if (plots.get(i).elm instanceof WireElm)
		return true;
	return false;
    }
    
    CircuitElm getXElm() {
	return getElm();
    }
    CircuitElm getYElm() {
	if (plots.size() == 2)
	    return plots.get(1).elm;
	return null;
    }
    
    boolean needToRemove() {
	boolean ret = true;
	boolean removed = false;
	int i;
	for (i = 0; i != plots.size(); i++) {
	   ScopePlot plot = plots.get(i);
	   if (sim.locateElm(plot.elm) < 0) {
	       plots.remove(i--);
	       removed = true;
	   } else
	       ret = false;
	}
	if (removed)
	    calcVisiblePlots();
	return ret;
    }

    public boolean isManualScale() {
	return manualScale;
    }
    
    public double getManScaleFromMaxScale(int units, boolean roundUp) {
	// When the user manually switches to manual scale (and we don't already have a setting) then
	// call with "roundUp=true" to get a "sensible" suggestion for the scale. When importing from
	// a legacy file then call with "roundUp=false" to stay as close as possible to the old presentation
	double s =scale[units];
	if ( units > UNITS_A)
	    s = 0.5*s;
	if (roundUp)
	    return ScopePropertiesDialog.nextHighestScale((2*s)/(double)(manDivisions));
	else 
	    return (2*s)/(double)(manDivisions);
    }
    
    static String exportAsDecOrHex(int v, int thresh) {
	// If v>=thresh then export as hex value prefixed by "x", else export as decimal
	// Allows flags to be exported as dec if in an old value (for compatibility) or in hex if new value
	if (v>=thresh)
	    return "x"+Integer.toHexString(v);
	else
	    return Integer.toString(v);
    }
    
    static int importDecOrHex(String s) {
	if (s.charAt(0) == 'x')
	    return Integer.parseInt(s.substring(1), 16);
	else
	    return Integer.parseInt(s);
    }
    
    /**
     * Exports circular buffer data as CSV format.
     * @return CSV string with time, plot values
     */
    String exportCircularBufferAsCSV() {
	StringBuilder sb = new StringBuilder();
	
	// Header row
	sb.append("Time (s)");
	for (int i = 0; i < visiblePlots.size(); i++) {
	    ScopePlot p = visiblePlots.get(i);
	    String label = p.elm.getScopeText(p.value);
	    if (label == null || label.isEmpty())
		label = "Plot " + (i+1);
	    sb.append(",").append(label).append(" Min");
	    sb.append(",").append(label).append(" Max");
	}
	sb.append("\n");
	
	// Data rows
	int width = rect.width;
	if (width > scopePointCount)
	    width = scopePointCount;
	
	for (int x = 0; x < width; x++) {
	    // Calculate time for this sample
	    double time = sim.t - (width - x) * sim.maxTimeStep * speed;
	    sb.append(time);
	    
	    for (int i = 0; i < visiblePlots.size(); i++) {
		ScopePlot p = visiblePlots.get(i);
		int ipa = p.startIndex(width);
		int ip = (x + ipa) & (scopePointCount - 1);
		sb.append(",").append(p.minValues[ip]);
		sb.append(",").append(p.maxValues[ip]);
	    }
	    sb.append("\n");
	}
	
	return sb.toString();
    }
    
    /**
     * Exports circular buffer data as JSON format.
     * @return JSON string with metadata and data arrays
     */
    String exportCircularBufferAsJSON() {
	StringBuilder sb = new StringBuilder();
	sb.append("{\n");
	sb.append("  \"source\": \"CircuitJS1 Scope\",\n");
	sb.append("  \"exportType\": \"circularBuffer\",\n");
	sb.append("  \"simulationTime\": ").append(sim.t).append(",\n");
	sb.append("  \"timeStep\": ").append(sim.maxTimeStep * speed).append(",\n");
	sb.append("  \"plots\": [\n");
	
	int width = rect.width;
	if (width > scopePointCount)
	    width = scopePointCount;
	
	for (int i = 0; i < visiblePlots.size(); i++) {
	    ScopePlot p = visiblePlots.get(i);
	    String label = p.elm.getScopeText(p.value);
	    if (label == null || label.isEmpty())
		label = "Plot " + (i+1);
	    
	    sb.append("    {\n");
	    sb.append("      \"name\": \"").append(escapeJSON(label)).append("\",\n");
	    sb.append("      \"units\": \"").append(Scope.getScaleUnitsText(p.units)).append("\",\n");
	    sb.append("      \"color\": \"").append(p.color).append("\",\n");
	    sb.append("      \"time\": [");
	    
	    // Time array
	    for (int x = 0; x < width; x++) {
		if (x > 0) sb.append(", ");
		double time = sim.t - (width - x) * sim.maxTimeStep * speed;
		sb.append(time);
	    }
	    sb.append("],\n");
	    
	    // Min values array
	    sb.append("      \"minValues\": [");
	    int ipa = p.startIndex(width);
	    for (int x = 0; x < width; x++) {
		if (x > 0) sb.append(", ");
		int ip = (x + ipa) & (scopePointCount - 1);
		sb.append(p.minValues[ip]);
	    }
	    sb.append("],\n");
	    
	    // Max values array
	    sb.append("      \"maxValues\": [");
	    for (int x = 0; x < width; x++) {
		if (x > 0) sb.append(", ");
		int ip = (x + ipa) & (scopePointCount - 1);
		sb.append(p.maxValues[ip]);
	    }
	    sb.append("]\n");
	    sb.append("    }");
	    if (i < visiblePlots.size() - 1)
		sb.append(",");
	    sb.append("\n");
	}
	
	sb.append("  ]\n");
	sb.append("}\n");
	return sb.toString();
    }
    
    /**
     * Exports full history data as CSV format (for drawFromZero mode).
     * @return CSV string with time, plot values
     */
    String exportHistoryAsCSV() {
	if (!drawFromZero || historySize == 0)
	    return "No history data available\n";
	
	StringBuilder sb = new StringBuilder();
	
	// Header row
	sb.append("Time (s)");
	for (int i = 0; i < visiblePlots.size(); i++) {
	    ScopePlot p = visiblePlots.get(i);
	    String label = p.elm.getScopeText(p.value);
	    if (label == null || label.isEmpty())
		label = "Plot " + (i+1);
	    sb.append(",").append(label).append(" Min");
	    sb.append(",").append(label).append(" Max");
	}
	sb.append("\n");
	
	// Data rows
	for (int x = 0; x < historySize; x++) {
	    double time = startTime + x * historySampleInterval;
	    sb.append(time);
	    
	    for (int i = 0; i < visiblePlots.size(); i++) {
		ScopePlot p = visiblePlots.get(i);
		if (p.historyMinValues != null && p.historyMaxValues != null) {
		    sb.append(",").append(p.historyMinValues[x]);
		    sb.append(",").append(p.historyMaxValues[x]);
		} else {
		    sb.append(",0,0");
		}
	    }
	    sb.append("\n");
	}
	
	return sb.toString();
    }
    
    /**
     * Exports full history data as JSON format (for drawFromZero mode).
     * @return JSON string with metadata and data arrays
     */
    String exportHistoryAsJSON() {
	if (!drawFromZero || historySize == 0)
	    return "{\"error\": \"No history data available\"}\n";
	
	StringBuilder sb = new StringBuilder();
	sb.append("{\n");
	sb.append("  \"source\": \"CircuitJS1 Scope\",\n");
	sb.append("  \"exportType\": \"history\",\n");
	sb.append("  \"startTime\": ").append(startTime).append(",\n");
	sb.append("  \"historySize\": ").append(historySize).append(",\n");
	sb.append("  \"sampleInterval\": ").append(historySampleInterval).append(",\n");
	sb.append("  \"plots\": [\n");
	
	for (int i = 0; i < visiblePlots.size(); i++) {
	    ScopePlot p = visiblePlots.get(i);
	    String label = p.elm.getScopeText(p.value);
	    if (label == null || label.isEmpty())
		label = "Plot " + (i+1);
	    
	    sb.append("    {\n");
	    sb.append("      \"name\": \"").append(escapeJSON(label)).append("\",\n");
	    sb.append("      \"units\": \"").append(Scope.getScaleUnitsText(p.units)).append("\",\n");
	    sb.append("      \"color\": \"").append(p.color).append("\",\n");
	    
	    // Time array
	    sb.append("      \"time\": [");
	    for (int x = 0; x < historySize; x++) {
		if (x > 0) sb.append(", ");
		double time = startTime + x * historySampleInterval;
		sb.append(time);
	    }
	    sb.append("],\n");
	    
	    if (p.historyMinValues != null && p.historyMaxValues != null) {
		// Min values array
		sb.append("      \"minValues\": [");
		for (int x = 0; x < historySize; x++) {
		    if (x > 0) sb.append(", ");
		    sb.append(p.historyMinValues[x]);
		}
		sb.append("],\n");
		
		// Max values array
		sb.append("      \"maxValues\": [");
		for (int x = 0; x < historySize; x++) {
		    if (x > 0) sb.append(", ");
		    sb.append(p.historyMaxValues[x]);
		}
		sb.append("]\n");
	    } else {
		sb.append("      \"minValues\": [],\n");
		sb.append("      \"maxValues\": []\n");
	    }
	    
	    sb.append("    }");
	    if (i < visiblePlots.size() - 1)
		sb.append(",");
	    sb.append("\n");
	}
	
	sb.append("  ]\n");
	sb.append("}\n");
	return sb.toString();
    }
    
    /**
     * Escapes a string for safe inclusion in JSON.
     * @param s String to escape
     * @return Escaped string
     */
    private String escapeJSON(String s) {
	return s.replace("\\", "\\\\")
		.replace("\"", "\\\"")
		.replace("\n", "\\n")
		.replace("\r", "\\r")
		.replace("\t", "\\t");
    }
}
