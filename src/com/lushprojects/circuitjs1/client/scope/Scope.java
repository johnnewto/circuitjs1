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

package com.lushprojects.circuitjs1.client.scope;

import com.lushprojects.circuitjs1.client.*;


import com.lushprojects.circuitjs1.client.util.*;

import com.lushprojects.circuitjs1.client.elements.ActionScheduler;
import com.lushprojects.circuitjs1.client.runner.RuntimeMode;

import com.google.gwt.event.dom.client.MouseWheelEvent;
import com.lushprojects.circuitjs1.client.elements.electronics.digital.LogicOutputElm;
import com.lushprojects.circuitjs1.client.elements.electronics.measurement.*;
import com.lushprojects.circuitjs1.client.elements.electronics.wiring.WireElm;
import com.lushprojects.circuitjs1.client.elements.misc.ActionTimeElm;
import com.lushprojects.circuitjs1.client.util.Locale;

import java.util.Vector;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context2d;

/**
 * Scope class - displays time-series waveforms and XY plots of circuit values.
 * Supports multiple modes: standard scrolling view, draw-from-zero mode, 2D plots, FFT analysis.
 */
public class Scope {
    // ====================
    // FLAG CONSTANTS
    // ====================
    // Dump format flags (for serialization)
    final int FLAG_YELM = 32;
    final int FLAG_IVALUE = 2048;
    final int FLAG_PLOTS = 4096; // New-style dump with multiple plots
    public final int FLAG_PERPLOTFLAGS = 1<<18; // Per-plot flags in dump
    final int FLAG_PERPLOT_MAN_SCALE = 1<<19; // Manual scale included in each plot
    final int FLAG_MAN_SCALE = 16;
    final int FLAG_DIVISIONS = 1<<21; // Dump manDivisions
    final int FLAG_DRAW_FROM_ZERO = 1<<22; // Draw from t=0 on left, growing right
    final int FLAG_AUTO_SCALE_TIME = 1<<23; // Auto-adjust time scale when reaching edge
    final int FLAG_MAX_SCALE_LIMITS = 1<<24; // Max scale limits present
	final int FLAG_PLOT_REFS = 1<<25; // Per-plot stable element references present
    final int FLAG_MULTI_LHS_AXES = 1<<26; // Show multiple left-side scales for overlaid traces
    
    // ====================
    // VALUE TYPE CONSTANTS
    // ====================
    public static final int VAL_VOLTAGE = 0;
    static final int VAL_POWER_OLD = 1; // Legacy power value (conflicts with VAL_IB)
    public static final int VAL_R = 2; // Resistance
    public static final int VAL_CURRENT = 3;
    public static final int VAL_POWER = 7;
    // Transistor-specific values
    public static final int VAL_IB = 1;
    public static final int VAL_IC = 2;
    public static final int VAL_IE = 3;
    public static final int VAL_VBE = 4;
    public static final int VAL_VBC = 5;
    public static final int VAL_VCE = 6;
    
    // ====================
    // UNIT CONSTANTS
    // ====================
    public static final int UNITS_V = 0; // Volts
    public static final int UNITS_A = 1; // Amperes
    public static final int UNITS_W = 2; // Watts
    public static final int UNITS_OHMS = 3; // Ohms
    private static final int UNITS_COUNT = 4;
    
    // ====================
    // DISPLAY CONSTANTS
    // ====================
    public static final double multa[] = {2.0, 2.5, 2.0}; // Grid scaling multipliers
    public static final int V_POSITION_STEPS = 200; // Vertical position adjustment range
    public static final double MIN_MAN_SCALE = 1e-9; // Minimum manual scale value
    private static final int SETTINGS_WHEEL_SIZE = 36; // Size of settings wheel in pixels
    private static final int SETTINGS_WHEEL_MARGIN = 100; // Minimum size needed to show settings wheel
    static final int SHADOW_OFFSET = 4; // Shadow offset in pixels
    static final int SHADOW_BLUR = 8; // Shadow blur radius
    private static final int MIN_PIXEL_SPACING = 20; // Minimum spacing between gridlines in pixels
    private static final int MULTI_LHS_TICK_COUNT = 5;
    private static final double[] MULTI_LHS_NICE_STEP_MULTIPLIERS = {1.0, 2.0, 2.5, 5.0, 10.0};
    
    // ====================
    // INSTANCE VARIABLES - Data
    // ====================
    int scopePointCount = 128; // Size of circular buffer (power of 2)
    private FFT fft;
    public int position; // Position in scope stack
    public int speed; // Sim timestep units per pixel
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
    boolean showFFT;
    boolean showNegative;
    boolean showRMS;
    boolean showAverage;
    boolean showDutyCycle;
    boolean showElmInfo;
    private boolean multiLhsAxes;
    
    // Maximum scale limits (null = no limit)
    Double[] maxScaleLimit = new Double[UNITS_COUNT];
    
    // Draw-from-zero mode variables
    boolean drawFromZero; // Draw from t=0 on left, growing right
    boolean autoScaleTime; // Auto-adjust time scale when reaching edge
    double startTime; // Simulation time when scope was reset (for drawFromZero mode)
    
    // ====================
    // INSTANCE VARIABLES - Working Data
    // ====================
    final ScopeModel model;
    final ScopeRuntimeState runtimeState = new ScopeRuntimeState();
    Vector<ScopePlot> plots;
    public Vector<ScopePlot> visiblePlots;
    CirSim sim;
    Canvas imageCanvas; // Canvas for 2D plots
    private Context2d imageContext;
    double scopeTimeStep; // Check if sim timestep has changed
    double[] scale; // Max value to scale the display - indexed by UNITS_*
    private boolean[] reduceRange;
    double scaleX;
    double scaleY;  // For X-Y plots
    private double wheelDeltaY; // Mouse wheel accumulator
    int selectedPlot; // Currently selected plot index
    private ScopePropertiesDialog properties;
    private String curColor;
    String voltColor; // Legacy color variables
    private double gridStepX;
    private double gridStepY; // Grid spacing
    private double displayGridStepX; // Grid spacing for display text (saved from first plot)
    private double maxValue;
    private double minValue; // Calculated from visible data
    int manDivisions; // Number of vertical divisions when in manual mode
    static int lastManDivisions;
    private boolean drawGridLines; // Flag to draw gridlines once per frame
    private boolean somethingSelected; // Is one of our plots selected?
    
    // ====================
    // ACTION MARKER HOVER
    // ====================
    private int hoveredActionIndex = -1; // Index of currently hovered action marker (-1 if none)
    private int lastHoveredActionIndex = -1; // Previous hovered action index for detecting changes
    int lastDisplayedActionIndex = -1; // Last action annotation displayed (persists)
    String lastDisplayedActionText = null; // postText of last displayed action (to compare with triggered actions)
    boolean lastDisplayedWasHover = false; // True if last displayed came from hover (not trigger)
    int lastLoggedActionIndex = -1; // Last action we logged (to reduce logging frequency)
    private int mouseX = -1;
    private int mouseY = -1; // Last mouse position in scope coordinates
    private boolean mouseButtonDown = false; // Track if mouse button is pressed
    java.util.HashMap<Integer, Integer> actionVerticalPositions = new java.util.HashMap<Integer, Integer>(); // Stored Y positions for each action ID
    
    // ====================
    // ZOOM SCALE FOR UNDOCKED SCOPES
    // ====================
    private double zoomScale = 1.0; // Zoom scale factor for text sizing (1.0 = normal)
    
    // ====================
    // STATIC VARIABLES - Cursor Tracking
    // ====================
    private static double cursorTime;
    private static int cursorUnits;
    private static Scope cursorScope;
    
    /**
     * Creates a new Scope instance.
     * @param s The simulator instance
     */
    public Scope(CirSim s) {
    	sim = s;
        model = new ScopeModel();
    	scale = new double[UNITS_COUNT];
    	reduceRange = new boolean[UNITS_COUNT];
	manDivisions = lastManDivisions;
        plots = model.getPlots();
        visiblePlots = model.getVisiblePlots();
    	
    	rect = new Rectangle(0, 0, 1, 1);
        if (RuntimeMode.isNonInteractiveRuntime()) {
            imageCanvas = null;
            imageContext = null;
        } else {
   	    imageCanvas = Canvas.createIfSupported();
   	    imageContext = imageCanvas != null ? imageCanvas.getContext2d() : null;
        }
	allocImage();
    	initialize();
    }

    void setPlots(Vector<ScopePlot> newPlots) {
        model.setPlots(newPlots);
        plots = model.getPlots();
    }

    private void syncVisiblePlotsFromModel() {
        visiblePlots = model.getVisiblePlots();
    }
    
    /**
     * Set the zoom scale factor for text sizing in undocked scopes.
     * @param scale The zoom scale factor (1.0 = normal)
     */
    private void setZoomScale(double scale) {
	zoomScale = scale;
    }

    public void setZoomScaleForEmbedded(double scale) {
	setZoomScale(scale);
    }
    
    /**
     * Get a scaled font size based on the current zoom scale.
     * @param baseSize The base font size in pixels
     * @return The scaled font size
     */
    private int getScaledFontSize(int baseSize) {
	return (int) Math.round(baseSize * zoomScale);
    }
    
    /**
     * Get a font string with scaled size.
     * @param baseSize The base font size in pixels
     * @param bold Whether the font should be bold
     * @return The font string (e.g., "bold 14px sans-serif")
     */
    private String getScaledFont(int baseSize, boolean bold) {
	int scaledSize = getScaledFontSize(baseSize);
	return (bold ? "bold " : "") + scaledSize + "px sans-serif";
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

    void setLogSpectrum(boolean state) {
	logSpectrum = state;
    }

    void setShowRms(boolean state) {
	showRMS = state;
    }

    void setShowAverage(boolean state) {
	showAverage = state;
    }

    void setShowDutyCycle(boolean state) {
	showDutyCycle = state;
    }

    void setShowElmInfo(boolean state) {
	showElmInfo = state;
    }
    
    /**
     * Sets manual scale mode.
     * @param value true to enable manual scale
     * @param roundup true to round up the scale to a sensible value
     */
    public void setManualScale(boolean value, boolean roundup) { 
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
    public void resetGraph() { 
	resetGraph(false); 
    }

    public void resetGraphForEmbedded() {
	resetGraph();
    }
    
    /**
     * Resets the scope graph.
     * @param full true to discard all old data
     */
    public void resetGraph(boolean full) {
    	resetGraph(full, true);  // Default: clear history
    }

    public void resetGraphForEmbedded(boolean full) {
	resetGraph(full);
    }
    
    /**
     * Resets the scope graph with control over history preservation.
     * @param full true to discard all old data from circular buffers
     * @param clearHistory true to clear history buffers (for drawFromZero mode)
     */
    private void resetGraph(boolean full, boolean clearHistory) {
	ScopeLifecycleController.resetGraph(this, full, clearHistory);
    }
    
    public void setManualScaleValue(int plotId, double d) {
	if (plotId >= visiblePlots.size() )
	    return; // Shouldn't happen, but just in case...
	clear2dView();
	visiblePlots.get(plotId).manScale=d;
	visiblePlots.get(plotId).manScaleSet=true;
    }
    
    public double getScaleValue() {
	if (visiblePlots.size() == 0)
	    return 0;
	ScopePlot p = visiblePlots.get(0);
	return scale[p.units];
    }
    
    public String getScaleUnitsText() {
	if (visiblePlots.size() == 0)
	    return "V";
	ScopePlot p = visiblePlots.get(0);
	return getScaleUnitsText(p.units);
    }
    
    public static String getScaleUnitsText(int units) {
	switch (units) {
	case UNITS_A: return "A";
	case UNITS_OHMS: return Locale.ohmString;
	case UNITS_W: return "W";
	default: return "V";
	}
    }
    
    public void setManDivisions(int d) {
	manDivisions = lastManDivisions = d;
    }

    int getManDivisionsForPersistence() {
	return manDivisions;
    }

    int getPerPlotFlagsMaskForPersistence() {
	return FLAG_PERPLOTFLAGS;
    }

    int getPlotRefsMaskForPersistence() {
	return FLAG_PLOT_REFS;
    }

    int getDivisionsMaskForPersistence() {
	return FLAG_DIVISIONS;
    }

    int getMaxScaleLimitsMaskForPersistence() {
	return FLAG_MAX_SCALE_LIMITS;
    }

    int getPlotsMaskForPersistence() {
	return FLAG_PLOTS;
    }

    int getPerPlotManualScaleMaskForPersistence() {
	return FLAG_PERPLOT_MAN_SCALE;
    }

    int getYElmMaskForPersistence() {
	return FLAG_YELM;
    }

    int getIValueMaskForPersistence() {
	return FLAG_IVALUE;
    }
    
    /**
     * Sets the maximum scale limit for current unit type (prevents auto-scale from exceeding this value).
     * @param limit The maximum limit, or null to disable
     */
    public void setMaxScaleLimit(Double limit) {
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
    public Double getMaxScaleLimit() {
	if (visiblePlots.size() == 0)
	    return null;
	int units = visiblePlots.get(0).units;
	return maxScaleLimit[units];
    }

    /**
     * Checks if this scope is active (has plots with valid elements).
     * @return true if scope has at least one plot with a valid element
     */
    public boolean active() { 
	return plots.size() > 0 && plots.get(0).elm != null; 
    }

    public int getVisiblePlotCount() {
	return visiblePlots != null ? visiblePlots.size() : 0;
    }

    private boolean isValidIndex(Vector<ScopePlot> targetPlots, int index) {
	return targetPlots != null && index >= 0 && index < targetPlots.size();
    }

    private ScopePlot getVisiblePlotOrNull(int index) {
	if (!isValidIndex(visiblePlots, index)) {
	    return null;
	}
	return visiblePlots.get(index);
    }

    private ScopePlot getPlotOrNull(int index) {
	if (!isValidIndex(plots, index)) {
	    return null;
	}
	return plots.get(index);
    }

    public CircuitElm getVisiblePlotElement(int index) {
	ScopePlot plot = getVisiblePlotOrNull(index);
	return plot != null ? plot.elm : null;
    }

    public String getVisiblePlotText(int index) {
	ScopePlot plot = getVisiblePlotOrNull(index);
	if (plot == null || plot.elm == null) {
	    return null;
	}
	return plot.elm.getScopeTextForScope(plot.value);
    }

    public double getVisiblePlotLastValue(int index) {
	ScopePlot plot = getVisiblePlotOrNull(index);
	return plot != null ? plot.lastValue : 0.0;
    }

    public static final class VisiblePlotView {
	public final String color;
	public final int units;
	public final double manualScale;
	public final int manualPosition;
	public final boolean acCoupled;
	public final boolean canAcCouple;

	VisiblePlotView(ScopePlot plot) {
	    color = plot.color;
	    units = plot.units;
	    manualScale = plot.manScale;
	    manualPosition = plot.manVPosition;
	    acCoupled = plot.isAcCoupled();
	    canAcCouple = plot.canAcCouple();
	}
    }

    public VisiblePlotView getVisiblePlotView(int index) {
	ScopePlot plot = getVisiblePlotOrNull(index);
	return plot == null ? null : new VisiblePlotView(plot);
    }

    public void setVisiblePlotAcCoupled(int index, boolean acCoupled) {
	ScopePlot plot = getVisiblePlotOrNull(index);
	if (plot == null) {
	    return;
	}
	plot.setAcCoupled(acCoupled);
    }
    
    /**
     * Initializes the scope with default settings.
     * Sets up default scales, speeds, and display options.
     */
    public void initialize() {
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
        multiLhsAxes = false;
    	
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
    public void calcVisiblePlots() {
        model.rebuildVisiblePlots(plot2d, showV, showI);
        syncVisiblePlotsFromModel();
    }

    void applyRectFromLifecycle(Rectangle r) {
	int widthBefore = rect.width;
	rect = r;
	if (rect.width != widthBefore) {
	    resetGraph(false, !drawFromZero);
	}
    }

    void applySetSpeedFromLifecycle(int sp) {
	speed = sp;
	resetGraph(false, true);
    }

    void applySpeedUpFromLifecycle() {
	if (drawFromZero) {
	    return;
	}
	if (speed > 1) {
	    speed /= 2;
	    resetGraph(false, !drawFromZero);
	}
    }

    void applySlowDownFromLifecycle() {
	if (drawFromZero) {
	    return;
	}
	if (speed < 1024) {
	    speed *= 2;
	}
	resetGraph(false, !drawFromZero);
    }

    void applyResetGraphFromLifecycle(boolean full, boolean clearHistory) {
	scopePointCount = 1;
	while (scopePointCount <= rect.width) {
	    scopePointCount *= 2;
	}
	if (plots == null) {
	    setPlots(new Vector<ScopePlot>());
	}
	showNegative = false;
	for (int i = 0; i != plots.size(); i++) {
	    plots.get(i).reset(scopePointCount, speed, full);
	}
	calcVisiblePlots();
	scopeTimeStep = sim.getMaxTimeStep();

	if (clearHistory) {
	    lastDisplayedActionIndex = -1;
	    lastDisplayedActionText = null;
	    lastDisplayedWasHover = false;
	    lastLoggedActionIndex = -1;
	    actionVerticalPositions.clear();
	}

	if (drawFromZero) {
	    double sampleInterval = sim.getMaxTimeStep() * speed;
	    if (clearHistory) {
		startTime = sim.getTime();
		model.initializeHistoryBuffers(scopePointCount, sampleInterval);
	    } else {
		model.setHistorySampleInterval(sampleInterval);
		boolean needsAllocation = !model.areHistoryBuffersAllocated();
		if (needsAllocation) {
		    startTime = sim.getTime();
		    model.initializeHistoryBuffers(scopePointCount, sampleInterval);
		} else {
		    int newCapacity = scopePointCount * 4;
		    if (newCapacity != model.getHistoryCapacity()) {
			model.resizeHistoryBuffers(newCapacity);
		    }
		}
	    }
	} else {
	    model.clearHistoryBuffers();
	}

	allocImage();
    }

    void allocImageFromLifecycle() {
	if (imageCanvas != null) {
	    imageCanvas.setWidth(rect.width + "PX");
	    imageCanvas.setHeight(rect.height + "PX");
	    imageCanvas.setCoordinateSpaceWidth(rect.width);
	    imageCanvas.setCoordinateSpaceHeight(rect.height);
	    clear2dView();
	}
    }
    
    public void setRect(Rectangle r) {
	ScopeLifecycleController.setRect(this, r);
    }

    public void setRectForEmbedded(Rectangle r) {
	setRect(r);
    }

    public Rectangle getRectForEmbedded() {
	return rect;
    }

    public Rectangle getRect() {
	return rect;
    }

    public boolean containsScreenPoint(int x, int y) {
	return rect != null && rect.contains(x, y);
    }
    
    int getWidth() { return rect.width; }
    
    public int rightEdge() { return rect.x+rect.width; }

    void setPlotModes(boolean enablePlot2d, boolean enablePlotXy) {
	plot2d = enablePlot2d;
	plotXY = enablePlotXy;
    }

    public boolean isPlot2dEnabled() {
	return plot2d;
    }

    public boolean isPlotXyEnabled() {
	return plotXY;
    }

    void replacePlotsWithVisiblePlotsSnapshot() {
	setPlots(new Vector<ScopePlot>(visiblePlots));
    }
	
    public void setElm(CircuitElm ce) {
	ScopeSelectionService.setElm(this, ce);
    }

    public void setElmForEmbedded(CircuitElm ce) {
	setElm(ce);
    }
    
    public void addElm(CircuitElm ce) {
	ScopeSelectionService.addElm(this, ce);
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
    
    private void addValue(int val, CircuitElm ce) {
	ScopeSelectionService.addValue(this, val, ce);
    }
    
    void setValue(int val, CircuitElm ce) {
	setPlots(new Vector<ScopePlot>());
	addValue(val, ce);
//    	initialize();
    }

    void setValues(int val, int ival, CircuitElm ce, CircuitElm yelm) {
	if (ival > 0) {
	    setPlots(new Vector<ScopePlot>());
	    plots.add(new ScopePlot(ce, ce.getScopeUnitsForScope( val),  val, getManScaleFromMaxScale(ce.getScopeUnitsForScope( val), false)));
	    plots.add(new ScopePlot(ce, ce.getScopeUnitsForScope(ival), ival, getManScaleFromMaxScale(ce.getScopeUnitsForScope(ival), false)));
	    return;
	}
	if (yelm != null) {
	    setPlots(new Vector<ScopePlot>());
	    plots.add(new ScopePlot(ce,   ce.getScopeUnitsForScope( val), 0, getManScaleFromMaxScale(ce.getScopeUnitsForScope( val), false)));
	    plots.add(new ScopePlot(yelm, ce.getScopeUnitsForScope(ival), 0, getManScaleFromMaxScale(ce.getScopeUnitsForScope( val), false)));
	    return;
	}
	setValue(val);
    }

    void showVceVsIc() {
	setPlotModes(true, false);
	setValues(VAL_VCE, VAL_IC, getElm(), null);
	resetGraph();
    }

    void showVVsI(boolean state) {
	setPlotModes(state, false);
	resetGraph();
    }

    void setPlotXy(boolean state) {
	setPlotModes(state, state);
	if (plot2d) {
	    replacePlotsWithVisiblePlotsSnapshot();
	}
	if (plot2d && plots.size() == 1) {
	    selectY();
	}
	resetGraph();
    }
    
    public void setText(String s) {
	text = s;
    }
    
    public String getText() {
	return text;
    }
    
    public void setTitle(String s) {
	title = s;
    }
    
    public String getTitle() {
	return title;
    }
    
    public boolean showingValue(int v) {
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
    private boolean showingVoltageAndMaybeCurrent() {
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
    

    public void combine(Scope s) {
	ScopeSelectionService.combine(this, s);
    }

    // separate this scope's plots into separate scopes and return them in arr[pos], arr[pos+1], etc.  return new length of array.
    public int separate(Scope arr[], int pos) {
	return ScopeSelectionService.separate(this, arr, pos);
    }

    public void removePlot(int plot) {
	ScopeSelectionService.removePlot(this, plot);
    }
    
    // called for each timestep
    public void timeStep() {
	int i;
	for (i = 0; i != plots.size(); i++)
	    plots.get(i).timeStep();
	ScopeDisplayConfig config = getDisplayConfig();

	// For 2d plots we draw here rather than in the drawing routine
    	if (config.is2DMode() && imageContext!=null && plots.size()>=2) {
    	    double v = plots.get(0).lastValue;
    	    double yval = plots.get(1).lastValue;
    	    Scope2DController.TimeStepResult point = Scope2DController.computeTimeStepPoint(
    	            isManualScale(),
    	            v,
    	            yval,
    	            scaleX,
    	            scaleY,
    	            rect.width,
    	            rect.height,
    	            plots.get(0).manScale,
    	            plots.get(1).manScale,
    	            plots.get(0).manVPosition,
    	            plots.get(1).manVPosition,
    	            manDivisions,
    	            V_POSITION_STEPS);
    	    scaleX = point.scaleX;
    	    scaleY = point.scaleY;
    	    if (point.clearNeeded)
    	        clear2dView();
    	    drawTo(point.x, point.y);
    	}
    	
    	// Capture data to history for drawFromZero mode
    	if (config.isDrawFromZeroActive()) {
    	    if (!model.captureToHistory(sim.getTime(), startTime, scopePointCount)) {
    	        CirSim.console("captureToHistory: Not all history buffers allocated, skipping capture. " +
    			"drawFromZero=" + drawFromZero + ", plots.size()=" + plots.size());
    	    }
    	}
    }

    public void timeStepForEmbedded() {
	timeStep();
    }

    private ScopeDisplayConfig getDisplayConfig() {
        return new ScopeDisplayConfig(manualScale, plot2d, showFFT, multiLhsAxes, drawFromZero, autoScaleTime);
    }

    private ScopeFrameContext buildFrameContext() {
        ScopeDisplayConfig displayConfig = getDisplayConfig();
        int plotLeft = getPlotAreaLeft();
        int plotWidth = getPlotAreaWidth();
        boolean multiLhsActive = displayConfig.isMultiLhsActive(visiblePlots == null ? 0 : visiblePlots.size());
        int plotTop = ScopeLayout.getMultiLhsTopInfoGutterHeight(multiLhsActive, rect.height);
        int timeAxisHeight = ScopeLayout.getMultiLhsTimeAxisHeight(multiLhsActive, rect.height);
        int plotHeight = ScopeLayout.getMainPlotHeight(rect.height, plotTop, timeAxisHeight);
        int stride = getHorizontalPixelStride(displayConfig);
        double timePerPixel = sim.getMaxTimeStep() * speed / stride;
        return new ScopeFrameContext(displayConfig, plotLeft, plotTop, plotWidth, plotHeight, timeAxisHeight, stride, timePerPixel);
    }
    
    /**
     * Calculates 2D grid pixel spacing based on window dimensions.
     * @param width Window width in pixels
     * @param height Window height in pixels
     * @return Grid spacing in pixels
     */
    private double calc2dGridPx(int width, int height) {
	return Scope2DController.calcGridPx(width, height, manDivisions);
    }
    
    
    /**
     * Draws a line from the last 2D plot position to a new position.
     * @param x2 New X coordinate
     * @param y2 New Y coordinate
     */
    private void drawTo(int x2, int y2) {
    	int[] updated = Scope2DController.drawTraceSegment(
    	        imageContext,
    	        sim.printableCheckItem.getState(),
    	        runtimeState.drawOx,
    	        runtimeState.drawOy,
    	        x2,
    	        y2);
    	runtimeState.drawOx = updated[0];
    	runtimeState.drawOy = updated[1];
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
    	runtimeState.drawOx = runtimeState.drawOy = -1;
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
    
    public void setMaxScale(boolean s) {
	// This procedure is added to set maxscale to an explicit value instead of just having a toggle
	// We call the toggle procedure first because it has useful side-effects and then set the value explicitly.
	maxScale();
	maxScale = s;
    }
    
    public void maxScale() {
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
    
    public void toggleDrawFromZero() {
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

    private void drawFFTVerticalGridLines(Graphics g) {
      // Draw x-grid lines and label the frequencies in the FFT that they point to.
      int prevEnd = 0;
      int divs = 20;
      int plotWidth = getPlotAreaWidth();
      double maxFrequency = 1 / (sim.getMaxTimeStep() * speed * divs * 2);
      for (int i = 0; i < divs; i++) {
        int x = plotWidth * i / divs;
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

    private void drawFFT(Graphics g) {
	ScopePlot plot = (visiblePlots.size() == 0) ? plots.firstElement() : visiblePlots.firstElement();
	fft = ScopeFFTHelper.drawSpectrum(
	        g,
	        fft,
	        scopePointCount,
	        plot,
	        getPlotAreaWidth(),
	        rect.height,
	        logSpectrum,
	        scale[plot.units]);
    }
    
    /**
     * Draws the settings wheel icon in the bottom-left corner of the scope.
     * @param g Graphics context
     */
    private void drawSettingsWheel(Graphics g) {
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
	CircuitElm.drawThickCircleForScope(g, 0, 0, INNER_RADIUS);
	
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

    private void draw2d(Graphics g) {
    	if (imageContext==null)
    		return;
    	g.context.save();
    	g.context.translate(rect.x, rect.y);
    	g.clipRect(0, 0, rect.width, rect.height);
    	
    	runtimeState.alphaCounter = Scope2DController.fade2dCanvas(
    	        imageContext,
    	        runtimeState.alphaCounter,
    	        sim.printableCheckItem.getState(),
    	        rect.width,
    	        rect.height);
    	
    	g.context.drawImage(imageContext.getCanvas(), 0.0, 0.0);
//    	g.drawImage(image, r.x, r.y, null);
    	g.setColor(CircuitElm.whiteColor);
    	g.fillOval(runtimeState.drawOx-2, runtimeState.drawOy-2, 5, 5);
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
		if ( !sim.isDialogShowingForScope() && rect.contains(sim.getMouseCursorX(), sim.getMouseCursorY()) && plots.size()>=2) {
			double gridPx=calc2dGridPx(rect.width, rect.height);
			String info[] = new String [3];  // Increased from 2 to 3
			ScopePlot px = plots.get(0);
			ScopePlot py = plots.get(1);
			double xValue;
			double yValue;
			if (isManualScale()) {
				xValue = px.manScale*((double)(sim.getMouseCursorX()-rect.x-rect.width/2)/gridPx-manDivisions*px.manVPosition/(double)(V_POSITION_STEPS));
				yValue = py.manScale*((double)(-sim.getMouseCursorY()+rect.y+rect.height/2)/gridPx-manDivisions*py.manVPosition/(double)(V_POSITION_STEPS));
				} else {
				xValue = ((double)(sim.getMouseCursorX()-rect.x)/(0.499*(double)(rect.width))-1.0)*scaleX;
				yValue = -((double)(sim.getMouseCursorY()-rect.y)/(0.499*(double)(rect.height))-1.0)*scaleY;
			}
			// Add plot name as first element
			String plotName = getScopeLabelOrText();
			if (plotName == null || plotName.isEmpty()) {
				plotName = "2D Plot";
			}
			info[0] = plotName;
			info[1] = px.getUnitText(xValue);
			info[2] = py.getUnitText(yValue);
			
			drawCursorInfo(g, info, 3, sim.getMouseCursorX(), true);  // Changed from 2 to 3
		}
    }
	
  
    
    /**
     * Determines if settings wheel should be shown based on scope size.
     * @return true if scope is large enough to display settings wheel
     */
    private boolean showSettingsWheel() {
	return rect.height > SETTINGS_WHEEL_MARGIN && rect.width > SETTINGS_WHEEL_MARGIN;
    }
    
    /**
     * Checks if cursor is over the settings wheel icon.
     * @return true if cursor is within settings wheel bounds
     */
    public boolean cursorInSettingsWheel() {
	return showSettingsWheel() &&
		sim.getMouseCursorX() >= rect.x &&
		sim.getMouseCursorX() <= rect.x + SETTINGS_WHEEL_SIZE &&
		sim.getMouseCursorY() >= rect.y + rect.height - SETTINGS_WHEEL_SIZE && 
		sim.getMouseCursorY() <= rect.y + rect.height;
    }
    
    // does another scope have something selected?
    private void checkForSelectionElsewhere() {
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
    private void drawActionTimeMarkers(Graphics g, double startTime, double displayTimeSpan) {
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
    int plotWidth = getPlotAreaWidth();
	
	for (int i = 0; i < enabledActions.size(); i++) {
	    ActionScheduler.ScheduledAction action = enabledActions.get(i);
	    if (action.actionTime > sim.getTime())
		continue;
	    double timeFromStart = action.actionTime - startTime;
	    if (timeFromStart < 0 || timeFromStart > displayTimeSpan)
		continue;
	    
	    // Skip t=0 actions
	    if (action.actionTime <= 0)
		continue;
	    
	    int gx = (int) (plotWidth * timeFromStart / displayTimeSpan);
	    if (gx >= 0 && gx < plotWidth) {
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
    private void drawAllActionAnnotations(Graphics g, java.util.List<ActionScheduler.ScheduledAction> enabledActions,
                                          double startTime, double displayTimeSpan) {
	// Collect completed actions, keeping only the last action at each unique time
	java.util.List<ActionScheduler.ScheduledAction> completedActions = new java.util.ArrayList<ActionScheduler.ScheduledAction>();
	java.util.HashMap<Double, ActionScheduler.ScheduledAction> actionsByTime = new java.util.HashMap<Double, ActionScheduler.ScheduledAction>();
	
	for (ActionScheduler.ScheduledAction action : enabledActions) {
	    if (action.postText == null || action.postText.trim().isEmpty())
		continue;
	    if (action.actionTime <= 0 || action.actionTime > sim.getTime())
		continue;
	    if (action.state == ActionScheduler.ActionState.PENDING ||
		action.state == ActionScheduler.ActionState.READY)
		continue;
	    // Keep only the last action at each time (higher ID = more recent)
	    ActionScheduler.ScheduledAction existing = actionsByTime.get(action.actionTime);
	    if (existing == null || action.id > existing.id) {
		actionsByTime.put(action.actionTime, action);
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
    private void updateHoveredAction(java.util.List<ActionScheduler.ScheduledAction> enabledActions,
                                     double startTime, double displayTimeSpan) {
	hoveredActionIndex = -1;
	
	if (mouseX < 0 || mouseY < 0)
	    return;
    int plotWidth = getPlotAreaWidth();
    int plotLeft = getPlotAreaLeft();
    int mousePlotX = mouseX - plotLeft;
    hoveredActionIndex = ScopeInteractionController.findHoveredActionIndex(enabledActions, mousePlotX, plotWidth,
            startTime, displayTimeSpan, 10);
	
	// Track last hovered index for detecting changes
	if (hoveredActionIndex >= 0) {
	    lastHoveredActionIndex = hoveredActionIndex;
	}
    }
    
    /**
     * Draw annotation popup for action at specific Y position
     */
    private void drawActionAnnotationAtPosition(Graphics g, ActionScheduler.ScheduledAction action,
                                                double startTime, double displayTimeSpan, int popupY) {
	double timeFromStart = action.actionTime - startTime;
	if (timeFromStart < 0 || timeFromStart > displayTimeSpan)
	    return;
	
	// Skip if postText is empty
	if (action.postText == null || action.postText.isEmpty())
	    return;
	
    int plotWidth = getPlotAreaWidth();
	int gx = (int) (plotWidth * timeFromStart / displayTimeSpan);
	
	// Build annotation text
	String text = action.postText;
	
	// Add time and slider info if available
	if (action.sliderName != null && !action.sliderName.isEmpty()) {
	    text += " @ t=" + CircuitElm.getUnitText(action.actionTime, "s");
	}
	
	g.context.save();
	g.context.setFont(getScaledFont(12, false));
	
	// Measure text
	double textWidth = g.context.measureText(text).getWidth();
	int padding = (int)(6 * zoomScale);
	int boxWidth = (int) textWidth + padding * 2;
	int boxHeight = (int)(20 * zoomScale);
	// Position popup horizontally centered on marker
	int popupX = gx - boxWidth / 2;
	
	// Keep popup within bounds horizontally
	if (popupX < 0) popupX = 0;
	if (popupX + boxWidth > plotWidth) popupX = plotWidth - boxWidth;
    if (popupX < 0) popupX = 0;
	
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
    
    public void draw(Graphics g) {
	if (plots.size() == 0)
	    return;
	ScopeFrameContext frame = buildFrameContext();
	ScopeDisplayConfig config = frame.displayConfig;
    	
    	// reset if timestep changed
    	if (scopeTimeStep != sim.getMaxTimeStep()) {
    	    scopeTimeStep = sim.getMaxTimeStep();
    	    resetGraph();
    	}
    	
    	
    	if (config.is2DMode()) {
    		draw2d(g);
    		return;
    	}

    	drawSettingsWheel(g);
    	g.context.save();
    	g.setColor(Color.red);
    	g.context.translate(rect.x, rect.y);    	
    	g.clipRect(0, 0, rect.width, rect.height);

        g.context.save();
        g.context.translate(frame.plotLeft, frame.plotTop);
        g.clipRect(0, 0, frame.plotWidth, frame.plotHeight);

        // Render order contract:
        // 1. Grid (background)
        // 2. Waveforms (non-voltage units)
        // 3. Waveforms (current)
        // 4. Waveforms (voltage)
        // 5. Selected plot (topmost)
        // 6. Axes/overlays
        ScopeGridRenderer.render(this, g, frame);

    	int i;
    	for (i = 0; i != UNITS_COUNT; i++) {
    	    reduceRange[i] = false;
    	    if (maxScale && !config.manualScale)
    		scale[i] = 1e-4;
    	}
    	
    	int[] historyIndexRange = getHistoryVisibleIndexRange(frame);
	int si;
    	somethingSelected = false;  // is one of our plots selected?
    	
    	for (si = 0; si != visiblePlots.size(); si++) {
    	    ScopePlot plot = visiblePlots.get(si);
    	    calcPlotScale(plot, frame, historyIndexRange);
    	    if (sim.getScopeSelectedIndexForScope() == -1 && plot.elm !=null && plot.elm.isMouseElmForUi())
    		somethingSelected = true;
    	    reduceRange[plot.units] = true;
    	}
    	
    	boolean sel = sim.isScopeMenuSelectedForScope(this);
    	
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
    	    calcMaxAndMin(visiblePlots.firstElement().units, frame, historyIndexRange);
    	
        ScopeWaveformRenderer.render(this, g, frame, allPlotsSameUnits, sel);

        g.context.restore();

        ScopeAxisRenderer.render(this, g, frame);
        ScopeOverlayRenderer.renderInScope(this, g, frame);
    	
    	g.restore();
    	
    	ScopeOverlayRenderer.renderCursor(this, g, frame);
    	
		if (plots.get(0).samplesCaptured > 5 && !config.manualScale) {
    	    for (i = 0; i != UNITS_COUNT; i++)
    		if (scale[i] > 1e-4 && reduceRange[i])
    		    scale[i] /= 2;
    	}
    	
    	if ( (properties != null ) && properties.isShowing() )
    	    properties.refreshDraw();

    }

    public void drawForEmbedded(Graphics g) {
	draw(g);
    }

    private void drawCursorInfo(Graphics g, String[] info, int ct, int x, boolean drawY) {
        ScopeOverlayRenderer.drawCursorInfo(this, g, info, ct, x, drawY);
    }

    void renderGridLayer(Graphics g, ScopeFrameContext frame) {
        if (frame.displayConfig.isFFTMode()) {
            drawFFTVerticalGridLines(g);
            drawFFT(g);
        }
    }

    void renderWaveformLayers(Graphics g, ScopeFrameContext frame, boolean allPlotsSameUnits, boolean selected) {
        boolean firstPlotDrawn = false;

        for (int i = 0; i != visiblePlots.size(); i++) {
            if (visiblePlots.get(i).units > UNITS_A && i != selectedPlot) {
                drawPlot(g, frame, visiblePlots.get(i), allPlotsSameUnits, false, selected);
                if (!firstPlotDrawn) {
                    displayGridStepX = gridStepX;
                    firstPlotDrawn = true;
                }
            }
        }
        for (int i = 0; i != visiblePlots.size(); i++) {
            if (visiblePlots.get(i).units == UNITS_A && i != selectedPlot) {
                drawPlot(g, frame, visiblePlots.get(i), allPlotsSameUnits, false, selected);
                if (!firstPlotDrawn) {
                    displayGridStepX = gridStepX;
                    firstPlotDrawn = true;
                }
            }
        }
        for (int i = 0; i != visiblePlots.size(); i++) {
            if (visiblePlots.get(i).units == UNITS_V && i != selectedPlot) {
                drawPlot(g, frame, visiblePlots.get(i), allPlotsSameUnits, false, selected);
                if (!firstPlotDrawn) {
                    displayGridStepX = gridStepX;
                    firstPlotDrawn = true;
                }
            }
        }
        if (selectedPlot >= 0 && selectedPlot < visiblePlots.size()) {
            drawPlot(g, frame, visiblePlots.get(selectedPlot), allPlotsSameUnits, true, selected);
            if (!firstPlotDrawn) {
                displayGridStepX = gridStepX;
            }
        }
    }

    void renderAxisLayer(Graphics g, ScopeFrameContext frame) {
        ScopeAxisRenderer.render(this, g, frame);
    }

    void renderOverlayLayer(Graphics g, ScopeFrameContext frame) {
        drawInfoTexts(g);
        drawTitle(g);
    }

    void renderCursorLayer(Graphics g, ScopeFrameContext frame) {
        ScopeOverlayRenderer.renderCursor(this, g, frame);
    }

    
    // calculate maximum and minimum values for all plots of given units
    private void calcMaxAndMin(int units, ScopeFrameContext frame, int[] historyIndexRange) {
	maxValue = -1e8;
	minValue = 1e8;
    	int i;
    	int si;
    	for (si = 0; si != visiblePlots.size(); si++) {
    	    ScopePlot plot = visiblePlots.get(si);
    	    if (plot.units != units)
    		continue;
            if (historyIndexRange != null && plot.historyMinValues != null && plot.historyMaxValues != null) {
                double[] historyMinMax = ScopeScaler.calcMinMaxInRange(plot.historyMinValues, plot.historyMaxValues,
                        historyIndexRange[0], historyIndexRange[1]);
                if (historyMinMax == null) {
                    continue;
                }
                if (historyMinMax[1] > maxValue) {
                    maxValue = historyMinMax[1];
                }
                if (historyMinMax[0] < minValue) {
                    minValue = historyMinMax[0];
                }
                continue;
            }
            int displayWidth = getDisplaySampleWidth(plot, frame);
            if (displayWidth <= 0)
                continue;
            int ipa = plot.startIndex(displayWidth);
            double maxV[] = plot.maxValues;
            double minV[] = plot.minValues;
            for (i = 0; i != displayWidth; i++) {
                int ip = (i+ipa) & (scopePointCount-1);
                if (maxV[ip] > maxValue)
                    maxValue = maxV[ip];
                if (minV[ip] < minValue)
                    minValue = minV[ip];
            }
        }
    }
    
    // adjust scale of a plot
    private void calcPlotScale(ScopePlot plot, ScopeFrameContext frame, int[] historyIndexRange) {
		if (manualScale)
			return;
    	int i;
    	double max = 0;
        if (historyIndexRange != null && plot.historyMinValues != null && plot.historyMaxValues != null) {
            max = ScopeScaler.calcMaxAbsInRange(plot.historyMinValues, plot.historyMaxValues,
                    historyIndexRange[0], historyIndexRange[1]);
        } else {
            int displayWidth = getDisplaySampleWidth(plot, frame);
            if (displayWidth <= 0)
                return;
            int ipa = plot.startIndex(displayWidth);
            double maxV[] = plot.maxValues;
            double minV[] = plot.minValues;
            for (i = 0; i != displayWidth; i++) {
                int ip = (i+ipa) & (scopePointCount-1);
                if (maxV[ip] > max)
                    max = maxV[ip];
                if (minV[ip] < -max)
                    max = -minV[ip];
            }
        }
    	double gridMax = scale[plot.units];
    	scale[plot.units] = ScopeScaler.computeAutoScale(gridMax, max, maxScale, maxScaleLimit[plot.units]);
    }
    
    /**
     * Calculates the grid step for the X (time) axis.
     * @return Grid step in simulation time units
     */
    public double calcGridStepX() {
	return ScopeScaler.calcGridStepX(getTimePerPixel(), MIN_PIXEL_SPACING, multa);
    }

    /**
     * Convert simulation time to circular buffer index for drawFromZero mode
     * @param time Absolute simulation time
     * @param plot The plot being drawn
     * @return Index in minValues/maxValues arrays
     */
    int getBufferIndexForTime(double time, ScopePlot plot) {
	// Calculate how many timesteps from start
	double timePerStep = sim.getMaxTimeStep() * speed;
	int stepsFromStart = (int)((time - startTime) / timePerStep);
	
	// Map to circular buffer, accounting for wraparound
	int totalSteps = (int)((sim.getTime() - startTime) / timePerStep);
	int offset = totalSteps - stepsFromStart;
	
	// Get current pointer position and subtract offset
	int currentPos = plot.ptr;
	return (currentPos - offset) & (scopePointCount - 1);
    }

    private int getHorizontalPixelStride(ScopeDisplayConfig config) {
	if (!config.is2DMode() && speed <= 8)
	    return 4;
	if (!config.is2DMode() && speed <= 16)
	    return 2;
	return 1;
    }

    private int getHorizontalPixelStride() {
	return getHorizontalPixelStride(getDisplayConfig());
    }

    private double getTimePerPixel() {
	return sim.getMaxTimeStep() * speed / getHorizontalPixelStride();
    }

    private int getDisplaySampleWidth(ScopePlot plot, ScopeFrameContext frame) {
	int requiredSamples = (frame.plotWidth + frame.horizontalPixelStride - 1) / frame.horizontalPixelStride;
	return plot.getDisplayWidth(requiredSamples);
    }

    private int getDisplaySampleWidth(ScopePlot plot) {
	int stride = getHorizontalPixelStride();
	int plotWidth = getPlotAreaWidth();
	int requiredSamples = (plotWidth + stride - 1) / stride;
	return plot.getDisplayWidth(requiredSamples);
    }
    
    private void drawPlot(Graphics g, ScopeFrameContext frame, ScopePlot plot, boolean allPlotsSameUnits, boolean selected, boolean allSelected) {
	if (plot.elm == null)
	    return;
    	int i;
    	String col;
    	
	    double gridMid = 0;
    	int x = 0;
        final int plotHeight = frame.plotHeight;
    	final int maxy = (plotHeight-1)/2;
        int plotWidth = frame.plotWidth;

    	String color = (somethingSelected) ? "#A0A0A0" : plot.color;
	boolean mouseHoverSelected = sim.getScopeSelectedIndexForScope() == -1
	        && plot.elm != null
	        && plot.elm.isMouseElmForUi();
	if (allSelected || mouseHoverSelected)
    	    color = CircuitElm.selectColor.getHexValue();
	else if (selected)
	    color = plot.color;
        double traceStrokeWidth = selected ? 2.5 : ((allSelected || mouseHoverSelected) ? 2.0 : 1.0);
	    double maxV[] = plot.maxValues;
	    double minV[] = plot.minValues;
	    boolean multiLhsEnabled = frame.displayConfig.isMultiLhsActive(visiblePlots != null ? visiblePlots.size() : 0);
            ScopeDisplayConfig config = frame.displayConfig;
	    int[] historyIndexRange = getHistoryVisibleIndexRange(frame);
	    double[] axisRange = (!isManualScale() && multiLhsEnabled)
	            ? calcMultiLhsAxisRange(plot, minV, maxV, historyIndexRange) : null;
	    PlotScaleResult scaleResult = frame.plotScaleResults.get(plot);
	    if (scaleResult == null) {
	        scaleResult = ScopeScaler.buildPlotScaleResult(
	                isManualScale(),
	                multiLhsEnabled,
	                allPlotsSameUnits,
	                maxScale,
	                showNegative,
	                minValue,
	                maxValue,
	                scale[plot.units],
	                maxy,
	                manDivisions,
	                plot.manScale,
	                plot.manVPosition,
	                V_POSITION_STEPS,
	                MULTI_LHS_TICK_COUNT,
	                multa,
	                axisRange
	        );
	        frame.plotScaleResults.put(plot, scaleResult);
	    }
	    showNegative = scaleResult.showNegative;
	    gridMid = scaleResult.gridMid;
	    plot.plotOffset = scaleResult.plotOffset;
	    plot.gridMult = scaleResult.gridMult;
	    plot.lhsAxisMin = scaleResult.lhsAxisMin;
	    plot.lhsAxisMax = scaleResult.lhsAxisMax;
	    plot.lhsAxisStep = scaleResult.lhsAxisStep;
	    gridStepY = scaleResult.gridStepY;
	    double gridMax = scaleResult.gridMax;
	    int minRangeLo = -10-(int) (gridMid*plot.gridMult);
	    int minRangeHi =  10-(int) (gridMid*plot.gridMult);

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
	    double ts = frame.timePerPixel;
    	gridStepX = ScopeScaler.calcGridStepX(frame.timePerPixel, MIN_PIXEL_SPACING, multa);

    	boolean highlightCenter = !isManualScale();
    	
    	if (drawGridLines) {
    	    // horizontal gridlines
    	    
    	    // don't show hgridlines if lines are too close together (except for center line)
    	    boolean showHGridLines = (gridStepY != 0) && (isManualScale() || allPlotsSameUnits); // Will only show center line if we have mixed units
    	    for (int ll = -100; ll <= 100; ll++) {
    		if (ll != 0 && !showHGridLines)
    		    continue;
    		int yl = maxy-(int) ((ll*gridStepY-gridMid)*plot.gridMult);
    		if (yl < 0 || yl >= plotHeight-1)
    		    continue;
    		col = ll == 0 && highlightCenter ? majorDiv : minorDiv;
    		g.setColor(col);
    		g.drawLine(0,yl,plotWidth-1,yl);
    	    }
    	    
    	    // vertical gridlines (time axis)
    	    if (config.isDrawFromZeroActive()) {
    		// Draw from zero mode: gridlines start at t=0 on left
    		double elapsedTime = sim.getTime() - startTime;
    		double displayTimeSpan;
    		
    		if (config.autoScaleTime && elapsedTime > 0) {
    		    // Auto-scale: time span covers entire simulation from start
    		    displayTimeSpan = elapsedTime;
    		} else {
    		    // Fixed scale: time span is fixed based on speed
    		    displayTimeSpan = ts * plotWidth;
    		}
    		
    		// Adjust gridStepX if gridlines are too close together
    		// Calculate pixel spacing with current gridStepX
    		double pixelSpacing = plotWidth * gridStepX / displayTimeSpan;
    		int scalePtr = 0;
    		while (pixelSpacing < 20 && displayTimeSpan > 0) {
    		    // Gridlines too close - increase spacing using standard scale pattern
    		    gridStepX *= multa[scalePtr % 3];
    		    pixelSpacing = plotWidth * gridStepX / displayTimeSpan;
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
    		    int gx = (int) (plotWidth * timeFromStart / displayTimeSpan);
    		    
    		    if (gx < 0)
    			continue;
    		    if (gx >= plotWidth)
    			break;
    		    
    		    col = minorDiv;
    		    if (((tl + gridStepX/4) % (gridStepX*10)) < gridStepX) {
    			col = majorDiv;
    		    }
    		    g.setColor(col);
    		    g.drawLine(gx, 0, gx, plotHeight-1);
    		}
    		
    		// Draw t=0 line in highlighted color
    		g.setColor(majorDiv);
    		g.drawLine(0, 0, 0, plotHeight-1);
    		
    		// Draw action time markers
    		drawActionTimeMarkers(g, startTime, displayTimeSpan);
		    } else {
				int displayWidth = getDisplaySampleWidth(plot, frame);
				int displayPixelWidth = displayWidth * frame.horizontalPixelStride;
				if (displayPixelWidth < plotWidth) {
				    double actionStartTime = 0;
				    double actionDisplayTimeSpan = ts * plotWidth;
			    // Initial fill mode: draw static grid and start scrolling only when plot scrolls
			    for (int ll = 0; ; ll++) {
				double tl = ll * gridStepX;
				int gx = (int) (tl / ts);
				if (gx >= plotWidth)
				    break;
				col = (ll % 10 == 0) ? majorDiv : minorDiv;
				g.setColor(col);
				g.drawLine(gx, 0, gx, plotHeight - 1);
			    }
			    drawActionTimeMarkers(g, actionStartTime, actionDisplayTimeSpan);
			} else {
			    // Normal scrolling mode: gridlines scroll with time
			    double tstart = sim.getTime()-ts*plotWidth;
			    double tx = sim.getTime()-(sim.getTime() % gridStepX);

			    for (int ll = 0; ; ll++) {
				double tl = tx-gridStepX*ll;
				int gx = (int) ((tl-tstart)/ts);
				if (gx < 0)
				    break;
				if (gx >= plotWidth)
				    continue;
				if (tl < 0)
				    continue;
				col = minorDiv;
				if (((tl+gridStepX/4) % (gridStepX*10)) < gridStepX) {
				    col = majorDiv;
				}
				g.setColor(col);
				g.drawLine(gx,0,gx,plotHeight-1);
			    }
			    drawActionTimeMarkers(g, tstart, ts * plotWidth);
			}
    	    }
    	}
    	
    	// only need gridlines drawn once
    	drawGridLines = false;

        g.setColor(color);
        g.context.setLineWidth(traceStrokeWidth);
        
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
        int prevY = -1;  // Track previous Y point for connecting lines
        
        if (config.isDrawFromZeroActive()) {
            // Draw from zero mode: use history buffers instead of circular buffer
            if (plot.historyMinValues == null || model.getHistorySize() == 0) {
        	// No history data yet
        	g.endBatch();
        	return;
            }
            
            double[] histMinV = plot.historyMinValues;
            double[] histMaxV = plot.historyMaxValues;
            
            if (config.autoScaleTime) {
                // Auto-scale: map entire history to window width
                for (i = 0; i < plotWidth; i++) {
                    // Map pixel to history index
                    int histIdx = (i * model.getHistorySize()) / plotWidth;
                    if (histIdx >= model.getHistorySize())
                        histIdx = model.getHistorySize() - 1;
                    
                    // Use midpoint (average) of min and max for smoother interpolation
                    double midVal = (histMinV[histIdx] + histMaxV[histIdx]) / 2.0;
                    int midvy = (int) (plot.gridMult * (midVal + plot.plotOffset));
                    
                    int minvy = (int) (plot.gridMult*(histMinV[histIdx]+plot.plotOffset));
                    int maxvy = (int) (plot.gridMult*(histMaxV[histIdx]+plot.plotOffset));
                    
                    if (minvy < minRangeLo || maxvy > minRangeHi) {
                        reduceRange[plot.units] = false;
                        minRangeLo = -1000;
                        minRangeHi = 1000;
                    }
                    
                    // Clamp Y coordinate to valid drawing range
                    int y = maxy - midvy;
                    y = Math.max(0, Math.min(plotHeight - 1, y));
                    
                    // Draw line from previous point to current point
                    if (prevY != -1) {
                        g.drawLine(x+i-1, prevY, x+i, y);
                    }
                    
                    prevY = y;
                }
            } else {
                // Fixed scale: show most recent data that fits
                double elapsedTime = sim.getTime() - startTime;
                double timePerPixel = sim.getMaxTimeStep() * speed;
                int pixelsNeeded = (int)(elapsedTime / timePerPixel);
                int pixelsUsed = Math.min(pixelsNeeded, plotWidth);
                
                if (pixelsUsed < plotWidth) {
                    // Not enough data to fill screen yet, start from beginning
                    for (i = 0; i < pixelsUsed; i++) {
                        double time = i * timePerPixel;
                        int histIdx = (int)(time / model.getHistorySampleInterval());
                        if (histIdx >= model.getHistorySize())
                            histIdx = model.getHistorySize() - 1;
                        
                        // Use midpoint (average) of min and max for smoother interpolation
                        double midVal = (histMinV[histIdx] + histMaxV[histIdx]) / 2.0;
                        int midvy = (int) (plot.gridMult * (midVal + plot.plotOffset));
                        
                        int minvy = (int) (plot.gridMult*(histMinV[histIdx]+plot.plotOffset));
                        int maxvy = (int) (plot.gridMult*(histMaxV[histIdx]+plot.plotOffset));
                        
                        if (minvy < minRangeLo || maxvy > minRangeHi) {
                            reduceRange[plot.units] = false;
                            minRangeLo = -1000;
                            minRangeHi = 1000;
                        }
                        
                        // Clamp Y coordinate to valid drawing range
                        int y = maxy - midvy;
                        y = Math.max(0, Math.min(plotHeight - 1, y));
                        
                        // Draw line from previous point to current point
                        if (prevY != -1) {
                            g.drawLine(x+i-1, prevY, x+i, y);
                        }
                        
                        prevY = y;
                    }
                } else {
                    // Screen is full, show most recent window
                    double windowTimeSpan = plotWidth * timePerPixel;
                    double startTime = elapsedTime - windowTimeSpan;
                    int startPixel = 0;
                    
                    for (i = startPixel; i < plotWidth; i++) {
                        double time = startTime + i * timePerPixel;
                        int histIdx = (int)(time / model.getHistorySampleInterval());
                        if (histIdx < 0) histIdx = 0;
                        if (histIdx >= model.getHistorySize()) histIdx = model.getHistorySize() - 1;
                        
                        // Use midpoint (average) of min and max for smoother interpolation
                        double midVal = (histMinV[histIdx] + histMaxV[histIdx]) / 2.0;
                        int midvy = (int) (plot.gridMult * (midVal + plot.plotOffset));
                        
                        int minvy = (int) (plot.gridMult*(histMinV[histIdx]+plot.plotOffset));
                        int maxvy = (int) (plot.gridMult*(histMaxV[histIdx]+plot.plotOffset));
                        
                        if (minvy < minRangeLo || maxvy > minRangeHi) {
                            reduceRange[plot.units] = false;
                            minRangeLo = -1000;
                            minRangeHi = 1000;
                        }
                        
                        // Clamp Y coordinate to valid drawing range
                        int y = maxy - midvy;
                        y = Math.max(0, Math.min(plotHeight - 1, y));
                        
                        // Draw line from previous point to current point
                        if (prevY != -1) {
                            g.drawLine(x+i-1, prevY, x+i, y);
                        }
                        
                        prevY = y;
                    }
                }
            }
		} else {
		    // Normal mode: fill from left first, then scroll once full width is reached
		    int stride = frame.horizontalPixelStride;
		    int displayWidth = getDisplaySampleWidth(plot, frame);
		    if (displayWidth <= 0) {
			g.endBatch();
			return;
		    }
		    int ipa = plot.startIndex(displayWidth);
		    for (i = 0; i != displayWidth; i++) {
			int ip = (i + ipa) & (scopePointCount-1);
                int curX = x + i*stride;
                
                // Use midpoint (average) of min and max for smoother interpolation
                double midVal = (minV[ip] + maxV[ip]) / 2.0;
                int midvy = (int) (plot.gridMult * (midVal + plot.plotOffset));
                
                int minvy = (int) (plot.gridMult*(minV[ip]+plot.plotOffset));
                int maxvy = (int) (plot.gridMult*(maxV[ip]+plot.plotOffset));
                
                if (minvy < minRangeLo || maxvy > minRangeHi) {
                    // we got a value outside min range, so we don't need to rescale later
                    reduceRange[plot.units] = false;
                    minRangeLo = -1000;
                    minRangeHi = 1000; // avoid triggering this test again
                }
                
                // Clamp Y coordinate to valid drawing range
                int y = maxy - midvy;
                y = Math.max(0, Math.min(plotHeight - 1, y));
                
                // Draw line from previous point to current point
                if (prevY != -1) {
					g.drawLine(curX-stride, prevY, curX, y);
                }
                
                prevY = y;
            } // for (i=0...)
        }
        
        g.endBatch();
        g.context.setLineWidth(1.0);
        
    }

    public static void clearCursorInfo() {
	cursorScope = null;
	cursorTime = -1;
    }
    
    public void selectScope(int mouseX, int mouseY, boolean mouseButtonDown) {
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
	ScopeFrameContext frame = buildFrameContext();
	ScopeDisplayConfig config = frame.displayConfig;
    int plotLeft = frame.plotLeft;
    int plotWidth = frame.plotWidth;
    int localPlotX = this.mouseX - plotLeft;
	
	if (config.is2DMode() || visiblePlots.size() == 0)
	    cursorTime = -1;
	else {
        if (!ScopeInteractionController.isWithinPlotX(localPlotX, plotWidth)) {
            cursorTime = -1;
        } else {
	    if (config.isDrawFromZeroActive()) {
		cursorTime = ScopeInteractionController.mapCursorTimeForDrawFromZero(
		        startTime, sim.getTime(), localPlotX, plotWidth, config.autoScaleTime, frame.timePerPixel);
	    } else {
		int stride = frame.horizontalPixelStride;
		int displayWidth = getDisplaySampleWidth(plots.get(0), frame);
		cursorTime = ScopeInteractionController.mapCursorTimeForScrolling(
		        sim.getTime(), sim.getMaxTimeStep(), speed, localPlotX, stride, displayWidth);
	    }
        }
	}
    	checkForSelection(mouseX, mouseY);
    	cursorScope = this;
    }

    public void selectScopeForEmbedded(int mouseX, int mouseY, boolean mouseButtonDown) {
	selectScope(mouseX, mouseY, mouseButtonDown);
    }
    
    // find selected plot
    private void checkForSelection(int mouseX, int mouseY) {
	if (sim.isDialogShowingForScope())
	    return;
	if (!rect.contains(mouseX, mouseY)) {
	    selectedPlot = -1;
	    return;
	}
	if (plots.size() == 0) {
	    selectedPlot = -1;
	    return;
	}
	ScopeFrameContext frame = buildFrameContext();
	ScopeDisplayConfig config = frame.displayConfig;
	int stride = frame.horizontalPixelStride;
	int requiredSamples = (frame.plotWidth + stride - 1) / stride;
    int localX = mouseX - rect.x - frame.plotLeft;
    int localY = mouseY - rect.y - frame.plotTop;
	if (requiredSamples <= 0 || localY < 0 || localY >= frame.plotHeight) {
	    selectedPlot = -1;
	    return;
	}
	if (localX < 0) {
	    selectedPlot = findNearestMultiLhsAxisSelection(mouseX, frame);
	    if (selectedPlot >= 0)
		cursorUnits = visiblePlots.get(selectedPlot).units;
	    return;
	}
	if (localX >= frame.plotWidth) {
	    selectedPlot = -1;
	    return;
	}
    	int maxy = (frame.plotHeight-1)/2;
	if (config.isDrawFromZeroActive()) {
	    int historyIndex = getHistoryIndexForSelection(frame, localX);
	    if (historyIndex < 0) {
		selectedPlot = -1;
		return;
	    }
	    selectedPlot = ScopeInteractionController.findNearestPlotIndexInHistory(
	            visiblePlots, historyIndex, mouseY, rect.y, frame.plotTop, maxy);
	} else {
	    int sampleX = Math.min(localX / stride, requiredSamples - 1);
	    selectedPlot = ScopeInteractionController.findNearestPlotIndexAtSampleX(
	            visiblePlots, sampleX, requiredSamples, scopePointCount, mouseY, rect.y, frame.plotTop, maxy);
	}
    	if (selectedPlot >= 0)
    	    cursorUnits = visiblePlots.get(selectedPlot).units;
    }

    private int getHistoryIndexForSelection(ScopeFrameContext frame, int localX) {
        return ScopeInteractionController.mapHistoryIndexForSelection(
                frame,
                localX,
                model.getHistorySize(),
                model.getHistorySampleInterval(),
                startTime,
                sim.getTime(),
                sim.getMaxTimeStep(),
                speed);
    }

    private int findNearestMultiLhsAxisSelection(int mouseX, ScopeFrameContext frame) {
        if (!frame.displayConfig.isMultiLhsActive(visiblePlots == null ? 0 : visiblePlots.size())) {
            return -1;
        }
        int axisCount = getMultiLhsAxisCount();
        if (axisCount <= 0) {
            return -1;
        }
        int localScopeX = mouseX - rect.x;
        // Axis spacing is 24 px; allow a generous hover band for labels/ticks.
        return ScopeInteractionController.findNearestMultiLhsAxisIndex(localScopeX, axisCount, 20);
    }
    
    public boolean canShowRMS() {
	if (visiblePlots.size() == 0)
	    return false;
	return ScopeStatsService.canShowRms(visiblePlots.firstElement());
    }
    
    // calc RMS and display it
    private void drawRMS(Graphics g) {
	if (!canShowRMS()) {
	    // needed for backward compatibility
	    showRMS = false;
	    showAverage = true;
	    drawAverage(g);
	    return;
	}
	ScopePlot plot = visiblePlots.firstElement();
        int displayWidth = getDisplaySampleWidth(plot);
        if (displayWidth <= 0)
            return;
	Double rms = ScopeStatsService.computeRms(plot, displayWidth, scopePointCount, (maxValue + minValue) / 2);
	if (rms != null) {
	    drawInfoText(g, plot.getUnitText(rms) + "rms");
	}
    }
    
    private void drawScale(ScopePlot plot, Graphics g) {
	if (!isManualScale()) {
	    if (gridStepY != 0 && (!(showV && showI))) {
		String vScaleText = " V=" + plot.getUnitText(gridStepY) + "/div";
		drawInfoText(g, "H=" + CircuitElm.getUnitText(displayGridStepX, "s") + "/div" + vScaleText);
	    }
	} else {
	    if (rect.y + rect.height <= textY + 5)
		return;
	    double x = getInfoTextX();
	    String hs = "H=" + CircuitElm.getUnitText(displayGridStepX, "s") + "/div";
	    g.drawString(hs, (int) x, textY);
	    x += g.measureWidth(hs);
	    final double bulletWidth = 17;
	    int scaledSpacing = getScaledFontSize(15);
	    for (int i = 0; i < visiblePlots.size(); i++) {
		ScopePlot p = visiblePlots.get(i);
		String s = p.getUnitText(p.manScale);
		if (p != null) {
		    String vScaleText = "=" + s + "/div";
		    double vScaleWidth = g.measureWidth(vScaleText);
		    if (x + bulletWidth + vScaleWidth > rect.width) {
			x = getInfoTextX();
			textY += scaledSpacing;
			if (rect.y + rect.height <= textY + 5)
			    return;
		    }
		    g.setColor(p.color);
		    g.fillOval((int) x + 7, textY - 9, 8, 8);
		    x += bulletWidth;
		    g.setColor(CircuitElm.whiteColor);
		    g.drawString(vScaleText, (int) x, textY);
		    x += vScaleWidth;
		}
	    }
	    textY += scaledSpacing;
	}
    }

    private boolean isMultiLhsAxesDrawEnabled() {
        return getDisplayConfig().isMultiLhsActive(visiblePlots != null ? visiblePlots.size() : 0);
    }

    private int getMultiLhsAxisCount() {
        return ScopeLayout.getMultiLhsAxisCount(visiblePlots == null ? 0 : visiblePlots.size());
    }

    private int getMultiLhsGutterWidth() {
        if (!isMultiLhsAxesDrawEnabled()) {
            return 0;
        }
        int axisCount = getMultiLhsAxisCount();
        if (axisCount <= 0) {
            return 0;
        }
        return ScopeLayout.getMultiLhsGutterWidth(true, axisCount);
    }

    private int getPlotAreaLeft() {
        return ScopeLayout.getPlotAreaLeft(rect.width, getMultiLhsGutterWidth());
    }

    private int getPlotAreaWidth() {
        return ScopeLayout.getPlotAreaWidth(rect.width, getPlotAreaLeft());
    }

    private int getInfoTextX() {
        return ScopeLayout.getInfoTextAnchorX(getPlotAreaLeft());
    }

    private double[] calcMultiLhsAxisRange(ScopePlot plot, double[] minV, double[] maxV, int[] historyIndexRange) {
        if (historyIndexRange != null && plot.historyMinValues != null && plot.historyMaxValues != null) {
            return ScopeScaler.calcMultiLhsAxisRangeLinear(plot.historyMinValues, plot.historyMaxValues,
                    historyIndexRange[0], historyIndexRange[1], showNegative, scale[plot.units],
                    MULTI_LHS_TICK_COUNT, MULTI_LHS_NICE_STEP_MULTIPLIERS);
        }
        int displayWidth = getDisplaySampleWidth(plot);
        if (displayWidth <= 0) {
            return null;
        }
        int ipa = plot.startIndex(displayWidth);
        return ScopeScaler.calcMultiLhsAxisRange(minV, maxV, scopePointCount, ipa, displayWidth,
                showNegative, scale[plot.units], MULTI_LHS_TICK_COUNT, MULTI_LHS_NICE_STEP_MULTIPLIERS);
    }

    private int[] getHistoryVisibleIndexRange(ScopeFrameContext frame) {
        if (!frame.displayConfig.isDrawFromZeroActive()) {
            return null;
        }
        return ScopeScaler.calcHistoryVisibleIndexRange(frame.displayConfig.autoScaleTime,
                model.getHistorySize(),
                frame.plotWidth,
                sim.getTime() - startTime,
                sim.getMaxTimeStep() * speed,
                model.getHistorySampleInterval());
    }

    private void drawAverage(Graphics g) {
	ScopePlot plot = visiblePlots.firstElement();
        int displayWidth = getDisplaySampleWidth(plot);
        if (displayWidth <= 0)
            return;
	Double avg = ScopeStatsService.computeAverage(plot, displayWidth, scopePointCount, (maxValue + minValue) / 2);
	if (avg != null) {
	    drawInfoText(g, plot.getUnitText(avg) + Locale.LS(" average"));
	}
    }

    private void drawDutyCycle(Graphics g) {
	ScopePlot plot = visiblePlots.firstElement();
        int displayWidth = getDisplaySampleWidth(plot);
        if (displayWidth <= 0)
            return;
	Integer duty = ScopeStatsService.computeDutyCyclePercent(plot, displayWidth, scopePointCount, (maxValue + minValue) / 2);
	if (duty != null) {
	    drawInfoText(g, Locale.LS("Duty cycle ") + duty + "%");
	}
    }

    // calc frequency if possible and display it
    private void drawFrequency(Graphics g) {
	ScopePlot plot = visiblePlots.firstElement();
        int displayWidth = getDisplaySampleWidth(plot);
        if (displayWidth <= 0)
            return;
	Double freq = ScopeStatsService.computeFrequency(
		plot, displayWidth, scopePointCount, sim.getMaxTimeStep(), speed);
	if (freq != null)
	    drawInfoText(g, CircuitElm.getUnitText(freq, "Hz"));
    }

    private void drawElmInfo(Graphics g) {
	String info[] = new String[1];
	getElm().getInfoForScope(info);
	int i;
	for (i = 0; info[i] != null; i++)
	    drawInfoText(g, info[i]);
    }
    
    private int textY;
    
    private void drawInfoText(Graphics g, String text) {
	int scaledSpacing = getScaledFontSize(15);
	if (rect.y + rect.height <= textY+5)
	    return;
	g.drawString(text, getInfoTextX(), textY);
	textY += scaledSpacing;
    }
    
    /**
     * Draws the scope title at the top right of the display.
     * @param g Graphics context
     */
    private void drawTitle(Graphics g) {
	if (title == null || title.isEmpty())
	    return;
	
	g.context.save();
	g.setColor(CircuitElm.whiteColor);
	
	// Measure the text width
	g.context.setFont(getScaledFont(14, true));
	double textWidth = g.context.measureText(title).getWidth();
	
	// Draw at top right with 20px margin
	int x = (int)(rect.width - textWidth - 20);
	int y = getScaledFontSize(15); // Top margin
	
	g.context.fillText(title, x, y);
	g.context.restore();
    }
    
    private void drawInfoTexts(Graphics g) {
    	g.setColor(CircuitElm.whiteColor);
    	textY = getScaledFontSize(10);
    	
    	if (visiblePlots.size() == 0) {
    	    if (showElmInfo)
    		drawElmInfo(g);
    	    return;
    	}
    	ScopePlot plot = visiblePlots.firstElement();
    	if (showScale && !isMultiLhsAxesDrawEnabled()) 
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
    	
    	// Draw per-trace legend/value text unless multi-LHS axes mode is active.
    	if (visiblePlots.size() >= 1 && !isMultiLhsAxesDrawEnabled()) {
    	    // Show each plot name with its own color and current value
    	    int scaledSpacing = getScaledFontSize(15);
    	    for (int i = 0; i < visiblePlots.size(); i++) {
    		ScopePlot p = visiblePlots.get(i);
    		if (p.elm != null) {
    		    String plotText = p.elm.getScopeTextForScope(p.value);
    		    if (plotText != null && !plotText.isEmpty()) {
    			if (rect.y + rect.height <= textY+5)
    			    break;
    			g.setColor(p.color);
    			// Add current value to the plot name
    			String valueText = p.getUnitText(p.lastValue);
    			g.drawString(Locale.LS(plotText) + ": " + valueText, 0, textY);
    			textY += scaledSpacing;
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

    private String getScopeText() {
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
	    	return plot.elm.getScopeTextForScope(plot.value);
    }

    private String getScopeLabelOrText() {
	return getScopeLabelOrText(false);
    }

    private String getScopeLabelOrText(boolean forInfo) {
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
    public String getScopeMenuName() {
	if (title != null && !title.isEmpty())
	    return title;
	return getScopeLabelOrText();
    }
    
    public void setSpeed(int sp) {
	ScopeLifecycleController.setSpeed(this, sp);
    }

    public int getCurrentSpeed() {
	return speed;
    }
    
    public void properties() {
	properties = new ScopePropertiesDialog(sim, this);
	// CirSim.dialogShowing = properties;
    }
    
    public void speedUp() {
	ScopeLifecycleController.speedUp(this);
    }

    public void slowDown() {
	ScopeLifecycleController.slowDown(this);
    }
    
    public void setPlotPosition(int plot, int v) {
	visiblePlots.get(plot).manVPosition = v;
    }
	
    // get scope element, returning null if there's more than one
    public CircuitElm getSingleElm() {
	CircuitElm elm = plots.get(0).elm;
	int i;
	for (i = 1; i < plots.size(); i++) {
	    if (plots.get(i).elm != elm)
		return null;
	}
	return elm;
    }
    
    public boolean canMenu() {
    	return (plots.get(0).elm != null);
    }
    
    public boolean canShowResistance() {
    	CircuitElm elm = getSingleElm();
    	return elm != null && elm.canShowValueInScopeForScope(VAL_R);
    }
    
    public boolean isShowingVceAndIc() {
	return plot2d && plots.size() == 2 && plots.get(0).value == VAL_VCE && plots.get(1).value == VAL_IC;
    }

    public int getFlags() {
    	int flags = (showI ? 1 : 0) | (showV ? 2 : 0) |
			(showMax ? 0 : 4) |   // showMax used to be always on
			(showFreq ? 8 : 0) |
			// In this version we always dump manual settings using the PERPLOT format
			(isManualScale() ? (FLAG_MAN_SCALE | FLAG_PERPLOT_MAN_SCALE): 0) |
			(plot2d ? 64 : 0) |
			(plotXY ? 128 : 0) | (showMin ? 256 : 0) | (showScale? 512:0) |
			(showFFT ? 1024 : 0) | (maxScale ? 8192 : 0) | (showRMS ? 16384 : 0) |
			(showDutyCycle ? 32768 : 0) | (logSpectrum ? 65536 : 0) |
			(showAverage ? (1<<17) : 0) | (showElmInfo ? (1<<20) : 0) |
            (multiLhsAxes ? FLAG_MULTI_LHS_AXES : 0);
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

	// Include stable per-plot references so scope bindings survive element line reordering
	flags |= FLAG_PLOT_REFS;
	
	return flags;
    }

    public int getPlotCount() {
	return (plots == null) ? 0 : plots.size();
    }

    ScopePlot getPlotAt(int index) {
	return getPlotOrNull(index);
    }

    void removePlotAt(int index) {
	if (plots != null && index >= 0 && index < plots.size()) {
	    plots.removeElementAt(index);
	}
    }

    void trimPlotsToSize(int size) {
	while (plots != null && plots.size() > size) {
	    plots.removeElementAt(plots.size() - 1);
	}
    }

    Vector<ScopePlot> getVisiblePlotsSnapshot() {
	return new Vector<ScopePlot>(visiblePlots);
    }

    ScopePlot getVisiblePlotAt(int index) {
	return getVisiblePlotOrNull(index);
    }

    void removePlotRef(ScopePlot plot) {
	if (plot != null) {
	    plots.remove(plot);
	}
    }

    public CircuitElm getPlotElement(int index) {
	ScopePlot sp = getPlotOrNull(index);
	return (sp == null) ? null : sp.elm;
    }

    public int getPlotValue(int index) {
	ScopePlot sp = getPlotOrNull(index);
	return (sp == null) ? VAL_VOLTAGE : sp.value;
    }

    public void resetPlots() {
	ScopeSelectionService.resetPlots(this);
    }

    public void addPlot(CircuitElm elm, int units, int value, double manualScale) {
	ScopeSelectionService.addPlot(this, elm, units, value, manualScale);
    }

    void addPlotInternal(CircuitElm elm, int units, int value, double manualScale) {
	if (plots == null) {
	    setPlots(new Vector<ScopePlot>());
	}
	plots.add(new ScopePlot(elm, units, value, manualScale));
    }

    void clearPlotsInternal() {
	setPlots(new Vector<ScopePlot>());
	calcVisiblePlots();
    }

    boolean shouldAutoAddCurrentPlot(CircuitElm ce) {
	return ce != null &&
		sim.dotsCheckItem.getState() &&
		!(ce instanceof OutputElm ||
		  ce instanceof LogicOutputElm ||
		  ce instanceof AudioOutputElm ||
		  ce instanceof ProbeElm);
    }

    void setShowVoltageVisible(boolean state) {
	showV = state;
    }

    public boolean isShowVoltageEnabled() {
	return showV;
    }

    public boolean isShowCurrentEnabled() {
	return showI;
    }

    public boolean isShowScaleEnabled() {
	return showScale;
    }

    public boolean isShowMaxEnabled() {
	return showMax;
    }

    public boolean isShowMinEnabled() {
	return showMin;
    }

    public boolean isShowFreqEnabled() {
	return showFreq;
    }

    public boolean isShowFftEnabled() {
	return showFFT;
    }

    public boolean isLogSpectrumEnabled() {
	return logSpectrum;
    }

    public boolean isShowRmsEnabled() {
	return showRMS;
    }

    public boolean isShowAverageEnabled() {
	return showAverage;
    }

    public boolean isShowDutyCycleEnabled() {
	return showDutyCycle;
    }

    public boolean isShowElmInfoEnabled() {
	return showElmInfo;
    }

    public int getManDivisions() {
	return manDivisions;
    }

    int locateElement(CircuitElm elm) {
	return sim.locateElmForScope(elm);
    }

    CircuitElm getElementAt(int index) {
	return sim.getElm(index);
    }

    int getElementCount() {
	return sim.elmList.size();
    }

    double getScaleForUnit(int unit) {
	return scale[unit];
    }

    void setScaleForUnit(int unit, double value) {
	scale[unit] = value;
    }

    Double getMaxScaleLimitForUnit(int unit) {
	return maxScaleLimit[unit];
    }

    void setMaxScaleLimitForUnit(int unit, Double value) {
	maxScaleLimit[unit] = value;
    }

    void initializeScaleFromVoltageAndCurrent() {
	scaleX = scale[UNITS_V];
	scaleY = scale[UNITS_A];
	scale[UNITS_OHMS] = scale[UNITS_W] = scale[UNITS_V];
    }

    void clearLabelText() {
	text = null;
    }

    void appendLabelToken(String token) {
	if (text == null) {
	    text = token;
	} else {
	    text += " " + token;
	}
    }

    void appendTitleToken(String token) {
	if (title == null) {
	    title = token;
	} else {
	    title += " " + token;
	}
    }

    String getRawLabelText() {
	return text;
    }

    void setRawLabelText(String value) {
	text = value;
    }

    String getRawTitleText() {
	return title;
    }

    void setRawTitleText(String value) {
	title = value;
    }

    void setPlot2dForPersistence(boolean state) {
	plot2d = state;
    }

    void setSpeedForPersistence(int speedValue) {
	speed = speedValue;
    }

    int getSpeedForPersistence() {
	return speed;
    }

    int getPositionForPersistence() {
	return position;
    }

    void setPositionForPersistence(int pos) {
	position = pos;
    }

    public int getStackPosition() {
	return position;
    }

    public void setStackPosition(int pos) {
	position = pos;
    }

    public void setStackCount(int count) {
	stackCount = count;
    }

    public int getSelectedPlotIndex() {
	return selectedPlot;
    }

    public void setShowPeaks(boolean max, boolean min) {
	showMax = max;
	showMin = min;
    }

    public void setShowMaxEnabled(boolean state) {
	showMax = state;
    }

    public void setSpeedAndResetIfChanged(int speedValue) {
	if (speed != speedValue) {
	    speed = speedValue;
	    resetGraph();
	}
    }

    void setPlotStateFromPersistence(int index, boolean acCoupled, boolean manScaleSetValue, double manScaleValue, int manVPos) {
	ScopePlot plot = getPlotAt(index);
	if (plot == null) {
	    return;
	}
	plot.acCoupled = acCoupled;
	plot.manScaleSet = manScaleSetValue;
	plot.manScale = manScaleValue;
	plot.manVPosition = manVPos;
    }

    public String dump() {
	return ScopePersistence.dump(this);
    }

    public String dumpForEmbedded() {
	return dump();
    }
    
    public void undump(StringTokenizer st) {
	ScopePersistence.undump(this, st);
    }

    public void undumpForEmbedded(StringTokenizer st) {
	undump(st);
    }

    public void setPositionForEmbedded(int pos) {
	position = pos;
    }

    public int getPositionForEmbedded() {
	return position;
    }

    public int getSpeedForEmbedded() {
	return speed;
    }
    
    public void setFlags(int flags) {
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
        multiLhsAxes = (flags & FLAG_MULTI_LHS_AXES) != 0;
    	
    	// Load new drawFromZero flags
    	drawFromZero = (flags & FLAG_DRAW_FROM_ZERO) != 0;
    	autoScaleTime = (flags & FLAG_AUTO_SCALE_TIME) != 0;
    }
    
    public void saveAsDefault() {
	ScopePersistence.saveAsDefault(this);
    }

    private boolean loadDefaults() {
	return ScopePersistence.loadDefaults(this);
    }
    
    void allocImage() {
	ScopeLifecycleController.allocImage(this);
    }
    
    public void handleMenu(String mi, boolean state) {
	ScopeMenuController.handleMenu(this, mi, state);
    }

    void applyDrawFromZeroMenu(boolean state) {
	drawFromZero = state;
	if (state)
	    startTime = sim.getTime();
	resetGraph();
    }

    void setAutoScaleTime(boolean state) {
	autoScaleTime = state;
	sim.needAnalyze();
    }

    void setMultiLhsAxes(boolean state) {
	multiLhsAxes = state;
    }

    public void selectY() {
	Scope2DController.selectY(sim, plots);
    }
    
    public void onMouseWheel(MouseWheelEvent e) {
        wheelDeltaY += e.getDeltaY()*sim.getWheelSensitivity();
        if (wheelDeltaY > 5) {
            slowDown();
            wheelDeltaY = 0;
        }
        if (wheelDeltaY < -5) {
            speedUp();
	    wheelDeltaY = 0;
    	}
    }
    
    public CircuitElm getElm() {
	if (selectedPlot >= 0 && visiblePlots.size() > selectedPlot)
	    return visiblePlots.get(selectedPlot).elm;
	return visiblePlots.size() > 0 ? visiblePlots.get(0).elm : plots.get(0).elm;
    }

    public boolean viewingWire() {
	int i;
	for (i = 0; i != plots.size(); i++)
	    if (plots.get(i).elm instanceof WireElm)
		return true;
	return false;
    }
    
    public CircuitElm getXElm() {
	return getElm();
    }
    public CircuitElm getYElm() {
	if (plots.size() == 2)
	    return plots.get(1).elm;
	return null;
    }
    
    public boolean needToRemove() {
	boolean ret = true;
	boolean removed = false;
	int i;
	for (i = 0; i != plots.size(); i++) {
	   ScopePlot plot = plots.get(i);
	   if (sim.locateElmForScope(plot.elm) < 0) {
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
    
    public static String exportAsDecOrHex(int v, int thresh) {
	return ScopePersistence.exportAsDecOrHex(v, thresh);
    }
    
    public static int importDecOrHex(String s) {
	return ScopePersistence.importDecOrHex(s);
    }
    
    /**
     * Exports circular buffer data as CSV format.
     * @return CSV string with time, plot values
     */
    private String exportCircularBufferAsCSV() {
	return ScopeDataExporter.exportCircularBufferAsCSV(visiblePlots, rect, scopePointCount, sim, speed);
    }
    
    /**
     * Exports circular buffer data as JSON format.
     * @return JSON string with metadata and data arrays
     */
    private String exportCircularBufferAsJSON() {
	return ScopeDataExporter.exportCircularBufferAsJSON(visiblePlots, rect, scopePointCount, sim, speed);
    }
    
    /**
     * Exports full history data as CSV format (for drawFromZero mode).
     * @return CSV string with time, plot values
     */
    private String exportHistoryAsCSV() {
	return ScopeDataExporter.exportHistoryAsCSV(
		visiblePlots,
		drawFromZero,
		model.getHistorySize(),
		model.getHistorySampleInterval());
    }
    
    /**
     * Exports full history data as JSON format (for drawFromZero mode).
     * @return JSON string with metadata and data arrays
     */
    private String exportHistoryAsJSON() {
	return ScopeDataExporter.exportHistoryAsJSON(
		visiblePlots,
		drawFromZero,
		startTime,
		model.getHistorySize(),
		model.getHistorySampleInterval());
    }

    public boolean hasHistoryForExport() {
        return drawFromZero && model.getHistorySize() > 0;
    }

    public String exportDataAsCSV(boolean useHistory) {
        return useHistory ? exportHistoryAsCSV() : exportCircularBufferAsCSV();
    }

    public String exportDataAsJSON(boolean useHistory) {
        return useHistory ? exportHistoryAsJSON() : exportCircularBufferAsJSON();
    }

    public CirSim getSimForDialogs() {
        return sim;
    }

    public boolean isMaxScaleEnabledForUi() {
        return maxScale;
    }

    public boolean isDrawFromZeroEnabledForUi() {
        return drawFromZero;
    }

    public boolean isMultiLhsAxesEnabledForUi() {
        return multiLhsAxes;
    }

    static Scope getCursorScopeForRender() {
        return cursorScope;
    }

    static double getCursorTimeForRender() {
        return cursorTime;
    }

    String getScopeLabelOrTextForRender() {
        return getScopeLabelOrText();
    }

    int getDisplaySampleWidthForRender(ScopePlot plot, ScopeFrameContext frame) {
        return getDisplaySampleWidth(plot, frame);
    }

    int getHistorySizeForRender() {
        return model.getHistorySize();
    }

    double getHistorySampleIntervalForRender() {
        return model.getHistorySampleInterval();
    }

    String getScaledFontForRender(int baseSize, boolean bold) {
        return getScaledFont(baseSize, bold);
    }

    boolean isSomethingSelectedForRender() {
        return somethingSelected;
    }

    int getMultiLhsTickCountForRender() {
        return MULTI_LHS_TICK_COUNT;
    }

    double getDisplayGridStepXForRender() {
        return displayGridStepX;
    }

    double getStartTimeForRender() {
        return startTime;
    }

    int getMinPixelSpacingForRender() {
        return MIN_PIXEL_SPACING;
    }
    
}
