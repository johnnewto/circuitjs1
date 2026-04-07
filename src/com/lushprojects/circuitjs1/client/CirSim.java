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

import com.lushprojects.circuitjs1.client.scope.Scope;

import com.lushprojects.circuitjs1.client.ui.EditInfo;


import com.lushprojects.circuitjs1.client.util.*;

import com.lushprojects.circuitjs1.client.elements.ActionScheduler;

import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramElm;
import com.lushprojects.circuitjs1.client.elements.economics.*;
import com.lushprojects.circuitjs1.client.elements.electronics.electromechanical.SwitchElm;
import com.lushprojects.circuitjs1.client.elements.electronics.passives.*;
import com.lushprojects.circuitjs1.client.elements.electronics.wiring.*;
import com.lushprojects.circuitjs1.client.elements.misc.*;

// GWT conversion (c) 2015 by Iain Sharp

// For information about the theory behind this, see Electronic Circuit & System Simulation Methods by Pillage
// or https://github.com/sharpie7/circuitjs1/blob/master/INTERNALS.md

import java.util.Vector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Random;
import java.lang.Math;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.user.client.ui.CellPanel;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.dom.client.CanvasElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Frame;
import com.lushprojects.circuitjs1.client.core.ConfigProvider;
import com.lushprojects.circuitjs1.client.core.ConsoleLogger;
import com.lushprojects.circuitjs1.client.core.MatrixStamper;
import com.lushprojects.circuitjs1.client.core.CircuitNode;
import com.lushprojects.circuitjs1.client.core.SimulationContext;
import com.lushprojects.circuitjs1.client.core.SimulationTimingState;
import com.lushprojects.circuitjs1.client.core.SolverMatrixState;
import com.lushprojects.circuitjs1.client.io.ImportExportHelper;
import com.lushprojects.circuitjs1.client.io.LoadFile;
import com.lushprojects.circuitjs1.client.io.ClipboardManager;
import com.lushprojects.circuitjs1.client.io.SFCRDocumentManager;
import com.lushprojects.circuitjs1.client.io.SFCRDocumentState;
import com.lushprojects.circuitjs1.client.io.SetupListLoader;
import com.lushprojects.circuitjs1.client.registry.TableMasterRegistryManager;
import com.lushprojects.circuitjs1.client.ui.CheckboxMenuItem;
import com.lushprojects.circuitjs1.client.ui.EditDialog;
import com.lushprojects.circuitjs1.client.ui.EditDialogActions;
import com.lushprojects.circuitjs1.client.ui.FloatingControlPanel;
import com.lushprojects.circuitjs1.client.ui.Toolbar;
import com.lushprojects.circuitjs1.client.ui.InfoDialogActions;
import com.lushprojects.circuitjs1.client.ui.MathElementsTestDialog;
import com.lushprojects.circuitjs1.client.ui.MenuUiState;
import com.lushprojects.circuitjs1.client.ui.ScopeViewerDialog;
import com.lushprojects.circuitjs1.client.ui.Scrollbar;
import com.lushprojects.circuitjs1.client.ui.ScrollValuePopup;
import com.lushprojects.circuitjs1.client.ui.TableElementsTestDialog;
import com.lushprojects.circuitjs1.client.util.Locale;
import com.lushprojects.circuitjs1.client.runner.RunnerPanelUi;
import com.lushprojects.circuitjs1.client.runner.RuntimeMode;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsMethod; 
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

/**
 * CirSim - Main Circuit Simulator Class

 * This is the central controller for CircuitJS1, an electronic circuit simulator
 * that runs in web browsers. It implements Modified Nodal Analysis (MNA) based on
 * "Electronic Circuit and System Simulation Methods" by Pillage, Rohrer, & Visweswariah.

 * ARCHITECTURE:
 * - Circuit simulation uses MNA matrix equation: X = A⁻¹B
 *   where A is admittance matrix, B is right-hand side, X is solution (node voltages + source currents)
 * - Linear elements (R, L, C) are stamped once during analysis
 * - Nonlinear elements (diodes, transistors) require iterative solving
 * - Time integration uses Backward Euler (stable) or Trapezoidal (accurate) methods

 * MAIN LOOP (updateCircuit):
 * 1. Analyze circuit structure (if needed) - build node list, validate connections
 * 2. Stamp circuit matrix (if needed) - populate MNA matrices
 * 3. Run simulation iterations - solve matrix, update element states
 * 4. Draw graphics - render circuit visualization and scopes

 * PERFORMANCE OPTIMIZATIONS:
 * - Matrix simplification removes trivial rows (reduces O(n³) LU decomposition cost)
 * - Wire closure calculation groups connected wires to same node (smaller matrix)
 * - Element arrays cached to avoid type checks in inner loops
 * - Adaptive timestep reduces iterations when convergence is difficult
 *
 * @author Paul Falstad, Iain Sharp
 * @see <a href="https://github.com/sharpie7/circuitjs1/blob/master/INTERNALS.md">...</a>
 */
public class CirSim implements ConfigProvider, ConsoleLogger {

	@JsFunction
	interface SaveDialogSuccessCallback {
		Object onSuccess(SaveDialogResult result);
	}

	@JsFunction
	interface SaveDialogFailureCallback {
		Object onFailure(Object error);
	}

	@JsFunction
	interface OpenFileCallback {
		void onOpen(String text, String name);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	private static class StyleLike {
		@JsProperty(name = "display") native String getDisplay();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	static class SaveDialogResult {
		@JsProperty(name = "canceled") native boolean isCanceled();
		@JsProperty(name = "filePath") native Object getFilePath();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Promise")
	static class PromiseLike {
		@JsMethod(name = "then") native void then(SaveDialogSuccessCallback success, SaveDialogFailureCallback failure);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "window")
	static class GlobalWindowLike {
		@JsProperty(name = "devicePixelRatio") static native double getDevicePixelRatio();
		@JsProperty(name = "openFile") static native Object getOpenFileFunction();
		@JsProperty(name = "startCircuitText") static native String getStartCircuitText();
		@JsProperty(name = "navigator") static native NavigatorLike getNavigator();
		@JsProperty(name = "CircuitJS1") static native CircuitJsApi getCircuitJS1();
		@JsProperty(name = "CircuitJS1") static native void setCircuitJS1(CircuitJsApi api);
		@JsProperty(name = "oncircuitjsloaded") static native OnCircuitLoadedHook getOnCircuitJsLoaded();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Navigator")
	static class NavigatorLike {
		@JsProperty(name = "language") native String getLanguage();
		@JsProperty(name = "userLanguage") native String getUserLanguage();
	}

	@JsMethod(namespace = JsPackage.GLOBAL, name = "getComputedStyle")
	private static native StyleLike getComputedStyle(Element element);

	@JsMethod(namespace = JsPackage.GLOBAL, name = "LZString.decompressFromEncodedURIComponent")
	private static native String decompressUri(String value);

	@JsMethod(namespace = JsPackage.GLOBAL, name = "LZString.compressToEncodedURIComponent")
	private static native String compressUri(String value);

	@JsMethod(namespace = JsPackage.GLOBAL, name = "showSaveDialog")
	static native PromiseLike showSaveDialog();

	@JsMethod(namespace = JsPackage.GLOBAL, name = "saveFile")
	static native void saveFile(Object file, String dump);

	@JsMethod(namespace = JsPackage.GLOBAL, name = "openFile")
	static native void openFile(OpenFileCallback callback);

	@JsMethod(namespace = JsPackage.GLOBAL, name = "toggleDevTools")
	static native void toggleDevToolsNative();

	@JsFunction
	interface TouchEventHandler {
		void handle(TouchEventLike event);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "DOMRect")
	static class DomRectLike {
		@JsProperty(name = "y") native double getY();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Touch")
	static class TouchLike {
		@JsProperty(name = "clientX") native double getClientX();
		@JsProperty(name = "clientY") native double getClientY();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "TouchList")
	static class TouchListLike {
		@JsProperty(name = "length") native int getLength();
		@JsMethod(name = "item") native TouchLike item(int index);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "TouchEvent")
	static class TouchEventLike {
		@JsProperty(name = "touches") native TouchListLike getTouches();
		@JsProperty(name = "timeStamp") native double getTimeStamp();
		@JsMethod native void preventDefault();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	static class MouseEventInitLike {
		@JsProperty(name = "clientX") native void setClientX(double x);
		@JsProperty(name = "clientY") native void setClientY(double y);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "MouseEvent")
	static class MouseEventLike {
		public MouseEventLike(String type, MouseEventInitLike init) {}
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "HTMLCanvasElement")
	static class CanvasElementLike {
		@JsMethod(name = "addEventListener") native void addEventListener(String type, TouchEventHandler handler, boolean useCapture);
		@JsMethod(name = "dispatchEvent") native void dispatchEvent(MouseEventLike event);
		@JsMethod(name = "getBoundingClientRect") native DomRectLike getBoundingClientRect();
	}

	@JsMethod(namespace = JsPackage.GLOBAL, name = "Object")
	static native MouseEventInitLike newMouseEventInit();

	@JsFunction
	interface OnCircuitLoadedHook {
		void call(CircuitJsApi api);
	}

	@JsFunction
	interface Hook0 {
		void call();
	}

	@JsFunction
	interface HookBool {
		void call(boolean value);
	}

	@JsFunction
	interface HookDouble {
		void call(double value);
	}

	@JsFunction
	interface HookStringBool {
		void call(String value, boolean flag);
	}

	@JsFunction
	interface HookStringDouble {
		Object call(String value, double n);
	}

	@JsFunction
	interface HookStringToDouble {
		double call(String value);
	}

	@JsFunction
	interface HookNoArgDouble {
		double call();
	}

	@JsFunction
	interface HookNoArgBoolean {
		boolean call();
	}

	@JsFunction
	interface HookNoArgString {
		String call();
	}

	@JsFunction
	interface HookNoArgArrayString {
		JsArrayString call();
	}

	@JsFunction
	interface HookNoArgElements {
		JsArray<JavaScriptObject> call();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "XMLHttpRequest")
	private static class NativeXhr {
	    NativeXhr() {}
	    @JsMethod native void open(String method, String url, boolean async);
	    @JsMethod native void send();
	    @JsProperty native int getStatus();
	    @JsProperty native String getResponseText();
	}

	@JsFunction
	interface ApiHook {
		void call(CircuitJsApi api);
	}

	@JsFunction
	interface SvgHook {
		void call(CircuitJsApi api, String svgData);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	static class CircuitJsApi {
		@JsProperty(name = "setSimRunning") native void setSetSimRunning(HookBool hook);
		@JsProperty(name = "reset") native void setReset(Hook0 hook);
		@JsProperty(name = "step") native void setStep(Hook0 hook);
		@JsProperty(name = "getTime") native void setGetTime(HookNoArgDouble hook);
		@JsProperty(name = "getTimeStep") native void setGetTimeStep(HookNoArgDouble hook);
		@JsProperty(name = "setTimeStep") native void setSetTimeStep(HookDouble hook);
		@JsProperty(name = "getMaxTimeStep") native void setGetMaxTimeStep(HookNoArgDouble hook);
		@JsProperty(name = "setMaxTimeStep") native void setSetMaxTimeStep(HookDouble hook);
		@JsProperty(name = "isRunning") native void setIsRunning(HookNoArgBoolean hook);
		@JsProperty(name = "getNodeVoltage") native void setGetNodeVoltage(HookStringToDouble hook);
		@JsProperty(name = "setExtVoltage") native void setSetExtVoltage(HookStringDouble hook);
		@JsProperty(name = "getElements") native void setGetElements(HookNoArgElements hook);
		@JsProperty(name = "getCircuitAsSVG") native void setGetCircuitAsSVG(HookNoArgString hook);
		@JsProperty(name = "exportCircuit") native void setExportCircuit(HookNoArgString hook);
		@JsProperty(name = "importCircuit") native void setImportCircuit(HookStringBool hook);
		@JsProperty(name = "importCircuitFromCTZ") native void setImportCircuitFromCTZ(HookStringBool hook);
		@JsProperty(name = "getSliderValue") native void setGetSliderValue(HookStringToDouble hook);
		@JsProperty(name = "setSliderValue") native void setSetSliderValue(HookStringDouble hook);
		@JsProperty(name = "getSliderNames") native void setGetSliderNames(HookNoArgArrayString hook);
		@JsProperty(name = "getLabeledNodeNames") native void setGetLabeledNodeNames(HookNoArgArrayString hook);
		@JsProperty(name = "getLabeledNodeValue") native void setGetLabeledNodeValue(HookStringToDouble hook);
		@JsProperty(name = "getComputedValueNames") native void setGetComputedValueNames(HookNoArgArrayString hook);
		@JsProperty(name = "setExprPerfProbeEnabled") native void setSetExprPerfProbeEnabled(HookBool hook);
		@JsProperty(name = "resetExprPerfProbe") native void setResetExprPerfProbe(Hook0 hook);
		@JsProperty(name = "getExprPerfProbeReport") native void setGetExprPerfProbeReport(HookNoArgString hook);

		@JsProperty(name = "onupdate") native ApiHook getOnUpdate();
		@JsProperty(name = "onanalyze") native ApiHook getOnAnalyze();
		@JsProperty(name = "ontimestep") native ApiHook getOnTimeStep();
		@JsProperty(name = "onsvgrendered") native SvgHook getOnSvgRendered();
	}

	@JsMethod(namespace = JsPackage.GLOBAL, name = "navigator.clipboard.writeText")
	private static native void clipboardWriteText(String text);

    Random random;
    FloatingControlPanel floatingControlPanel;
    MenuItem aboutItem;
    MenuItem importFromLocalFileItem, importFromTextItem, exportAsUrlItem, exportAsLocalFileItem, exportAsTextItem,
            printItem, recoverItem, saveFileItem;
	MenuItem editLookupTablesItem;
    MenuItem importFromDropboxItem;
    MenuItem undoItem, redoItem, cutItem, copyItem, pasteItem, selectAllItem, optionsItem, flipXItem, flipYItem, flipXYItem;
    MenuBar optionsMenuBar;
    public CheckboxMenuItem dotsCheckItem;
    public CheckboxMenuItem voltsCheckItem;
    public CheckboxMenuItem powerCheckItem;
    public CheckboxMenuItem smallGridCheckItem;
    CheckboxMenuItem crossHairCheckItem;
    public CheckboxMenuItem showValuesCheckItem;
    CheckboxMenuItem euroResistorCheckItem;
    CheckboxMenuItem euroGatesCheckItem;
    public CheckboxMenuItem printableCheckItem;
    CheckboxMenuItem conventionCheckItem;
    CheckboxMenuItem noEditCheckItem;
    CheckboxMenuItem mouseWheelEditCheckItem;
    public CheckboxMenuItem toolbarCheckItem;
    CheckboxMenuItem electronicsModeCheckItem;
    CheckboxMenuItem economicsModeCheckItem;
    CheckboxMenuItem weightedPriorityCheckItem;
    private final MenuUiState menuUiState = new MenuUiState();
    
    enum ToolbarType { ELECTRONICS, ECONOMICS }
    ToolbarType currentToolbarType = ToolbarType.ECONOMICS;
    public String voltageUnitSymbol = "$"; // Custom voltage unit symbol ($ for economics default)
    public String timeUnitSymbol = "yr"; // Custom time unit symbol (yr for economics default)
	public int infoViewerUpdateIntervalMs = 100; // InfoViewer live update throttling interval
    public boolean useWeightedPriority = false; // Weighted priority for Asset/Equity columns
	private final SFCRDocumentManager sfcrDocumentManager = new SFCRDocumentManager();

	public SFCRDocumentState getSFCRDocumentState() {
	return sfcrDocumentManager.getState();
	}

	public String getModelInfoEditorContent() {
	    return sfcrDocumentManager.getModelInfoEditorContent();
	}

    private Label powerLabel;
    private Label titleLabel;
    private Scrollbar speedBar;
    private Scrollbar currentBar;
    private Scrollbar powerBar;
    MenuItem elmEditMenuItem;
    MenuItem elmCutMenuItem;
    MenuItem elmCopyMenuItem;
    MenuItem elmDeleteMenuItem;
    MenuItem elmScopeMenuItem;
    MenuItem elmFloatScopeMenuItem;
    MenuItem elmAddScopeMenuItem;
    MenuItem elmSplitMenuItem;
    MenuItem elmSliderMenuItem;
    MenuItem elmSankeyMenuItem;
	MenuItem elmDagBlocksMenuItem;
	MenuItem elmEquationTableDebugMenuItem;
	MenuItem elmEquationTableReferenceMenuItem;
	MenuItem elmBringToFrontMenuItem;
	MenuItem elmSendToBackMenuItem;
    MenuItem elmFlipXMenuItem, elmFlipYMenuItem, elmFlipXYMenuItem;
    MenuItem elmSwapMenuItem;
    MenuItem stackAllItem;
    MenuItem unstackAllItem;
    MenuItem combineAllItem;
    MenuItem separateAllItem;
    boolean hideMenu = false;
    Element sidePanelCheckboxLabel;
    Element leftPanelCheckboxLabel;
   
    // Class addingClass;
    // Mathematical constants
    static final double pi = 3.14159265358979323846;
    
    // Mouse interaction modes - determine how mouse events are interpreted
    public static final int MODE_ADD_ELM = 0;        // Adding new circuit element
    static final int MODE_DRAG_ALL = 1;       // Dragging entire circuit
    static final int MODE_DRAG_ROW = 2;       // Dragging table row
    static final int MODE_DRAG_COLUMN = 3;    // Dragging table column
    static final int MODE_DRAG_SELECTED = 4;  // Dragging selected elements
    static final int MODE_DRAG_POST = 5;      // Dragging element terminal/post
    static final int MODE_SELECT = 6;         // Selecting elements (default)
    static final int MODE_DRAG_SPLITTER = 7;  // Dragging scope panel splitter
    
    // UI layout constants
    static final int infoWidth = 200;         // Width of info panel in pixels
    
    int gridSize, gridMask, gridRound;
    public boolean analyzeFlag;
    boolean needsStamp, savedFlag;
    boolean dumpMatrix;
    boolean needsRecoverySave;  // Defer recovery save until drag completes
    boolean dcAnalysisFlag;
    // boolean useBufferedImage;
    boolean isMac;
    String ctrlMetaKey;
    
    // Simulation timing state
    private final SimulationTimingState timingState = new SimulationTimingState();

    // Hint system (shows helpful formulas)
    int hintType = -1;             // Type of hint to display (HINT_LC, HINT_RC, etc.)
    int hintItem1, hintItem2;      // Elements involved in hint
    
    // Error/stop handling
    String stopMessage;            // Error message when simulation stops
	String warningMessage;         // Non-fatal warning shown in bottom-left status area
    
    // Mouse wheel sensitivity
    double wheelSensitivity = 1;
    
    // Frame rate control
    double minFrameRate = 20;      // Target minimum frame rate (FPS)
	// Adaptive timestep control - reduces timestep when convergence is difficult
	public boolean adjustTimeStep = false;
	
	// Convergence check threshold - subiterations before marking element as non-converged
	public int convergenceCheckThreshold = 100;
	
	// Developer mode - shows additional debug info (framerate, steprate, performance metrics)
	public boolean developerMode = false;
	
	// Equation table MNA mode - when true, equation tables create electrical outputs
    private boolean equationTableMnaMode = true;

	// Experimental: Newton Jacobian stamping for EquationTable VOLTAGE_MODE MNA rows.
	public boolean equationTableNewtonJacobianEnabled = false;

	// Experimental: Broyden quasi-Newton updates for EquationTable Jacobians.
	public boolean equationTableBroydenJacobianEnabled = false;

	// Global base convergence tolerance used by all EquationTableElm instances
    private double equationTableConvergenceTolerance = 0.001;

	// Default SFCR lookup behavior: true=clamped endpoints (pwl), false=extrapolating (pwlx)
    private boolean sfcrLookupClampDefault = true;

	// When true, include the electronics circuit library in the Circuits menu
	boolean showElectronicsCircuits = false;

	// When true, append timestamp query parameter when loading setup lists/circuits
	boolean enableCacheBustedUrls = true;

	// Global toggle for offscreen table blit caching
	public boolean tableRenderCacheEnabled = true;

	// When true, auto-open model info viewer after loading SFCR with info content
	public boolean autoOpenModelInfoOnLoad = true;
	
	// Circuit hint types - show helpful formulas when related elements are present
	static final int HINT_LC = 1;      // LC resonant frequency hint
	static final int HINT_RC = 2;      // RC time constant hint
	static final int HINT_3DB_C = 3;   // RC cutoff frequency hint (capacitor)
    static final int HINT_TWINT = 4;   // Twin-T notch filter hint
    static final int HINT_3DB_L = 5;   // RL cutoff frequency hint (inductor)
    // Circuit element storage
    public Vector<CircuitElm> elmList;           // Dynamic list of all circuit elements
    Vector<Adjustable> adjustables;       // Elements with adjustable sliders
    
    // Cached arrays for performance - avoid type checks in simulation loop
    CircuitElm elmArr[];                  // Cached array copy of elmList
    ScopeElm scopeElmArr[];               // Cached array of scope elements only
    
    // Element references for UI interaction
    public CircuitElm dragElm;                   // Element currently being dragged
    CircuitElm stopElm;                   // Element that caused simulation to stop
    private CircuitElm mouseElm = null;
    private TableElm lastInteractedTable = null; // Track last table clicked for draw order
    boolean didSwitch = false;
    private final SolverMatrixState solverMatrixState = new SolverMatrixState();
    
    // MNA node number currently highlighted (from hovering a LabeledNodeElm), or -1
    int highlightedNode = -1;
    CircuitElm plotXElm, plotYElm;
    SwitchElm heldSwitchElm;
    // Circuit-global value array for E_GSLOT fast expression evaluation path.
    // Filled once per subiteration (after applySolvedRightSide + commitPendingToCurrentValues).
    // Indexed by slot number stored inside each E_GSLOT Expr node.
    public double[] circuitVariables;           // Flat value array: node voltages + computed values
    String[] slotNames;                  // Parallel name array: slotNames[i] is the name for circuitVariables[i]
    public java.util.HashMap<String, Integer> nameToSlot; // name → circuitVariables[] slot index (used at analysis time only)
    
    // Circuit state flags
    boolean simRunning;                   // True when simulation is actively running
    boolean simRunningBeforeDrag;         // Saved state: was simulation running before drag started?
    
    // Circuit dimensions
    public int voltageSourceCount;               // Number of voltage sources (adds rows to matrix)
    // public boolean useFrame;
    public int scopeCount;
    public Scope scopes[];
    boolean showResistanceInVoltageSources;
    boolean hideInfoBox;
    int scopeColCount[];
    boolean isExporting; // flag to indicate we're exporting an image
    // Class dumpTypes[], shortcuts[];
    String shortcuts[];
    String clipboard;
    Rectangle circuitArea;
    boolean unsavedChanges;
    HashMap<String, String> classToLabelMap;
    Toolbar toolbar;

    DockLayoutPanel layoutPanel;
    VerticalPanel verticalPanel;
    VerticalPanel leftPanel;
    CellPanel buttonPanel;
    static final int SCOPE_MIN_MAX_BUTTON_SIZE = 24;

    
    // Menu definition loaded from menulist.txt
    String menuDefinition = null;
    boolean menuDefinitionLoaded = false;

    LoadFile loadFileInput;
    Frame iFrame;
	Frame leftModelInfoFrame;

    Canvas cv;
    public Context2d cvcontext;

    // canvas width/height in px (before device pixel ratio scaling)
    int canvasWidth, canvasHeight;

    static final int MENUBARHEIGHT = 30;
    static final int TOOLBARHEIGHT = 40;
    static int VERTICALPANELWIDTH = 166; // default (right panel)
    static int LEFTPANELWIDTH = 166; // default (left panel)
    static final int POSTGRABSQ = 25;
    static final int MINPOSTGRABSIZE = 256;
    private final Timer timer = new Timer() {
        public void run() {
            updateCircuit();
        }
    };
    private final int FASTTIMER = 16;

    public int getrand(int x) {
        int q = random.nextInt();
        if (q < 0)
            q = -q;
        return q % x;
    }

	public static int getVerticalPanelWidthForUi() {
	    return VERTICALPANELWIDTH;
	}

	public static float devicePixelRatio() {
		double ratio = GlobalWindowLike.getDevicePixelRatio();
		return (float) (ratio > 0 ? ratio : 1.0);
	}
    boolean isMobile(Element element) {
	if (element == null)
	    return false;
	StyleLike style = getComputedStyle(element);
	return style != null && !"none".equals(style.getDisplay());
    }
    
	String decompress(String dump) {
		return decompressUri(dump);
	}

//    Circuit applet;


public CirSim() {
		//	super("Circuit Simulator v1.6d");
		//	applet = a;
		//	useFrame = false;
		theSim = this;
	}

	private final RunnerController runnerController = new RunnerController(this);

	// Extracted CirSim helper delegates
	private final CirSimCommandRouter commandRouter = new CirSimCommandRouter(this);
	private final CircuitIOService circuitIOService = new CircuitIOService(this);
	private final CirSimMenuBuilder menuBuilder = new CirSimMenuBuilder(this);
	private final ScopeManager scopeManager = new ScopeManager(this);
	private final ViewportController viewportController = new ViewportController(this);
	private final FlipTransformController flipTransformController = new FlipTransformController(this);
	private final ClipboardManager clipboardManager = new ClipboardManager(this);
	private final MouseInputHandler mouseInputHandler = new MouseInputHandler(this);
	private final CircuitAnalyzer circuitAnalyzer = new CircuitAnalyzer(this);
	private final SimulationContextAdapter simulationContext = new SimulationContextAdapter(this);
	private final MatrixStamper matrixStamper = new MatrixStamper(this);
	private final CircuitRenderer circuitRenderer = new CircuitRenderer(this);
	private final SimulationLoop simulationLoop = new SimulationLoop(this);
	private final SetupListLoader setupListLoader = new SetupListLoader(this);
	private final EditDialogActions editDialogActions = new EditDialogActions(this);
	private final InfoDialogActions infoDialogActions = new InfoDialogActions(this);
	private final ImportExportHelper importExportHelper = new ImportExportHelper(this);
	private final UndoRedoManager undoRedoManager = new UndoRedoManager(this);
	private final ToolbarModeManager toolbarModeManager = new ToolbarModeManager(this);
	private final CirSimInitializer initializer = new CirSimInitializer(this);
	private final StatusInfoRenderer statusInfoRenderer = new StatusInfoRenderer(this);
	private final ExportCompositeActions exportCompositeActions = new ExportCompositeActions(this);
	private final CircuitValueSlotManager circuitValueSlotManager = new CircuitValueSlotManager(this);
	private final JsApiBridge jsApiBridge = new JsApiBridge(this);
	private final CirSimPreferencesManager preferencesManager = new CirSimPreferencesManager(this);
	private final TableMasterRegistryManager tableMasterRegistryManager = new TableMasterRegistryManager(this);
	private final CirSimUiPanelManager uiPanelManager = new CirSimUiPanelManager(this);
	private final CirSimPlatformInterop platformInterop = new CirSimPlatformInterop(this);
	private final CirSimBootstrap bootstrap = new CirSimBootstrap(this);
	private final CirSimDiagnostics diagnostics = new CirSimDiagnostics(this);

	MouseInputHandler getMouseInputHandler() {
	    return mouseInputHandler;
	}

	public MenuUiState getMenuUiState() {
	    return menuUiState;
	}

	public CircuitAnalyzer getCircuitAnalyzer() {
	    return circuitAnalyzer;
	}

	public SimulationContext getSimulationContext() {
	    return simulationContext;
	}

	public SimulationTimingState getTimingState() {
	    return timingState;
	}

	public SolverMatrixState getSolverMatrixState() {
	    return solverMatrixState;
	}


	boolean isMouseWasOverSplitter() { return mouseInputHandler.isMouseWasOverSplitter(); }
	int getMouseMode() { return mouseInputHandler.getMouseMode(); }
	int getTempMouseMode() { return mouseInputHandler.getTempMouseMode(); }
	void setTempMouseMode(int value) { mouseInputHandler.setTempMouseMode(value); }
	String getMouseModeStr() { return mouseInputHandler.getMouseModeStr(); }
	void setMouseModeStr(String value) { mouseInputHandler.setMouseModeStr(value); }
    public boolean isHandleDragInProgress(CircuitElm elm) {
	return elm != null && mouseElm == elm && mouseInputHandler.getTempMouseMode() == MODE_DRAG_POST && elm.lastHandleGrabbed >= 0;
    }
    public int getMouseCursorX() { return mouseInputHandler.getMouseCursorX(); }
    public int getMouseCursorY() { return mouseInputHandler.getMouseCursorY(); }
    public boolean isAddElementModeForUi() { return mouseInputHandler.getMouseMode() == MODE_ADD_ELM; }
    public void setCursorStyleForUi(String cursorStyle) { mouseInputHandler.setCursorStyle(cursorStyle); }
    public double getWheelSensitivity() { return wheelSensitivity; }
    public void setWheelSensitivityForUi(double wheelSensitivity) { this.wheelSensitivity = wheelSensitivity; }
    public double getMinFrameRateForUi() { return minFrameRate; }
    public void setMinFrameRateForUi(double minFrameRate) { this.minFrameRate = minFrameRate; }
    public int getGraphicsUpdateIntervalForUi() { return graphicsUpdateInterval; }
    public void setGraphicsUpdateIntervalForUi(int graphicsUpdateInterval) { this.graphicsUpdateInterval = graphicsUpdateInterval; }
    public boolean isShowElectronicsCircuitsEnabledForUi() { return showElectronicsCircuits; }
    public void setShowElectronicsCircuitsEnabledForUi(boolean showElectronicsCircuits) { this.showElectronicsCircuits = showElectronicsCircuits; }
    public void setCacheBustedUrlsEnabledForUi(boolean enableCacheBustedUrls) { this.enableCacheBustedUrls = enableCacheBustedUrls; }
    public boolean isDcAnalysisForUi() { return dcAnalysisFlag; }
    public java.util.Random getRandom() { return random; }
    public boolean isShowResistanceInVoltageSources() { return showResistanceInVoltageSources; }
	Rectangle getSelectedArea() { return mouseInputHandler.getSelectedArea(); }
	boolean isDragging() { return mouseInputHandler.isDragging(); }
	int getMousePost() { return mouseInputHandler.getMousePost(); }

	ScopeManager getScopeManager() {
	    return scopeManager;
	}

    public int getScopeSelectedIndexForScope() {
	return scopeManager.getScopeSelected();
    }

	ViewportController getViewportController() {
	    return viewportController;
	}

	public CirSimPreferencesManager getPreferencesManager() {
	    return preferencesManager;
	}

	public CirSimUiPanelManager getUiPanelManager() {
	    return uiPanelManager;
	}

	public void createNewLoadFileInputForIo() {
	    uiPanelManager.createNewLoadFile();
	}

	public void setCircuitTitleForIo(String title) {
	    setCircuitTitle(title);
	}

	public void setUnsavedChangesForIo(boolean unsavedChanges) {
	    this.unsavedChanges = unsavedChanges;
	}

	InfoDialogActions getInfoDialogActions() {
	    return infoDialogActions;
	}

	public EditDialogActions getEditDialogActions() {
	    return editDialogActions;
	}

	CircuitIOService getCircuitIOService() {
	    return circuitIOService;
	}

	public void readCircuitFromModel(String circuitText) {
	    circuitIOService.readCircuit(circuitText);
	}

    public String dumpCircuit() {
	    return circuitIOService.dumpCircuit();
	}

	public void installTouchHandlersForUi(CanvasElement element) {
	    CirSimPlatformInterop.installTouchHandlers(null, element);
	}

	public String getStatusBackgroundColorHexForUi() {
	    return statusInfoRenderer.getBackgroundColor().getHexValue();
	}

	ExportCompositeActions getExportCompositeActions() {
	    return exportCompositeActions;
	}

	public CustomCompositeModel getCircuitAsCompositeForUi() {
	    return exportCompositeActions.getCircuitAsComposite();
	}

	public Canvas getCircuitAsCanvas(int type) {
		return exportCompositeActions.getCircuitAsCanvas(type);
	}

	public String getCircuitAsSvg() {
		return exportCompositeActions.getCircuitAsSVG();
	}

	public Canvas getScopesAsCanvas() {
		return exportCompositeActions.getScopesAsCanvas();
	}

	public ImportExportHelper getImportExportHelper() {
	    return importExportHelper;
	}

	public void readCircuitFromImportHelper(String circuitText, int flags) {
	    getCircuitIOService().readCircuit(circuitText, flags);
	}

	public void setAllowSaveFromImportHelper(boolean allowSave) {
	    getUiPanelManager().allowSave(allowSave);
	}

	public boolean isDotsEnabledForExport() {
	    return dotsCheckItem != null && dotsCheckItem.getState();
	}

	public boolean isSmallGridEnabledForExport() {
	    return smallGridCheckItem != null && smallGridCheckItem.getState();
	}

	public boolean isVoltsEnabledForExport() {
	    return voltsCheckItem != null && voltsCheckItem.getState();
	}

	public boolean isPowerEnabledForExport() {
	    return powerCheckItem != null && powerCheckItem.getState();
	}

	public boolean isShowValuesEnabledForExport() {
	    return showValuesCheckItem != null && showValuesCheckItem.getState();
	}

	public boolean isAdjustTimeStepEnabledForExport() {
	    return adjustTimeStep;
	}

	public double getMaxTimeStepForExport() {
	    return getMaxTimeStep();
	}

	public double getIterCountForExport() {
	    return getSimulationLoop().getIterCount();
	}

	public double getVoltageRangeForExport() {
	    return CircuitElm.voltageRange;
	}

	public double getMinTimeStepForExport() {
	    return getTimingState().minTimeStep;
	}

	public String getVoltageUnitSymbolForExport() {
	    return voltageUnitSymbol;
	}

	public boolean hasToolbarStateForExport() {
	    return toolbarCheckItem != null;
	}

	public boolean isToolbarVisibleForExport() {
	    return toolbarCheckItem != null && toolbarCheckItem.getState();
	}

	public boolean isEquationTableMnaModeForExport() {
	    return equationTableMnaMode;
	}

	public boolean isEquationTableNewtonJacobianEnabledForExport() {
	    return equationTableNewtonJacobianEnabled;
	}

	public boolean isEquationTableBroydenJacobianEnabledForExport() {
	    return equationTableBroydenJacobianEnabled;
	}

	public double getEquationTableConvergenceToleranceForExport() {
	    return equationTableConvergenceTolerance;
	}

	public boolean isSfcrLookupClampDefaultForExport() {
	    return sfcrLookupClampDefault;
	}

	public int getConvergenceCheckThresholdForExport() {
	    return convergenceCheckThreshold;
	}

	public int getElementCountForImportExport() {
	    return elmList.size();
	}

	public int getImportSubcircuitsFlagForImportExport() {
	    return RC_SUBCIRCUITS;
	}

	public int getImportRetainFlagForImportExport() {
	    return RC_RETAIN;
	}

	public String decompressForImportHelper(String ctzData) {
	    return decompress(ctzData);
	}

	public String escapeTokenForImportExport(String text) {
	    return CustomLogicModel.escape(text);
	}

	public String unescapeTokenForImportExport(String text) {
	    return CustomLogicModel.unescape(text);
	}

	public String getElementDumpWithUidForImportExport(CircuitElm ce) {
	    ensureElementHasZOrder(ce);
	    String d = ce.dump();
	    if (d == null) {
	        return null;
	    }
	    return d + " U:" + CustomLogicModel.escape(ce.getPersistentUid()) + " Z:" + ce.getZOrder();
	}

	public String getElementUidForImportExport(CircuitElm ce) {
	    return ce.getPersistentUid();
	}

	public void setElementUidForImportExport(CircuitElm ce, String uid) {
	    ce.setPersistentUid(uid);
	}

	public int getElementZOrderForImportExport(CircuitElm ce) {
	    ensureElementHasZOrder(ce);
	    return ce.getZOrder();
	}

	public void setElementZOrderForImportExport(CircuitElm ce, int zOrder) {
	    ce.setZOrder(zOrder);
	}

	public void setupScopesForImportExport() {
	    getScopeManager().setupScopes();
	}

	public String generatePersistentUidForImportExport() {
	    return CircuitElm.generatePersistentUid();
	}

	CircuitValueSlotManager getCircuitValueSlotManager() {
	    return circuitValueSlotManager;
	}

	public double getLabeledNodeVoltageForUi(String name) {
	    return circuitValueSlotManager.getLabeledNodeVoltage(name);
	}

	public double resolveSlotValueForUi(String name) {
	    return circuitValueSlotManager.resolveSlotValue(name);
	}

	public String formatAdjustableValueForUi(String sliderName, double value) {
	    for (int i = 0; i < adjustables.size(); i++) {
		Adjustable adj = adjustables.get(i);
		if (adj.sliderText == null || !adj.sliderText.equals(sliderName)) {
		    continue;
		}
		EditInfo ei = adj.elm.getEditInfo(adj.editItem);
		if (ei == null) {
		    continue;
		}
		try {
		    String customText = adj.elm.getSliderUnitText(adj.editItem, ei, value);
		    if (customText != null) {
			return customText;
		    }
		} catch (Exception e) {
		    // Fall through to default formatting when no custom formatter is available.
		}
		return EditDialog.unitString(ei, value);
	    }
	    return CircuitElm.showFormat.format(value);
	}

	public java.util.List<String> getAdjustableNamesForElements() {
	    java.util.ArrayList<String> names = new java.util.ArrayList<String>();
	    for (int i = 0; i < adjustables.size(); i++) {
		Adjustable adj = adjustables.get(i);
		if (adj.sliderText != null && !adj.sliderText.isEmpty()) {
		    names.add(adj.sliderText);
		}
	    }
	    return names;
	}

	public double getAdjustableValueForElements(String sliderName) {
	    return circuitValueSlotManager.getSliderValue(sliderName);
	}

	public boolean setAdjustableValueForElements(String sliderName, double value) {
	    return circuitValueSlotManager.setSliderValue(sliderName, value);
	}

	public Color getStatusBackgroundColorForElements() {
	    return statusInfoRenderer.getBackgroundColor();
	}

	public void updateRunStopButtonForElements() {
	    updateRunStopButton();
	}

	CirSimCommandRouter getCommandRouter() {
	    return commandRouter;
	}

	CirSimMenuBuilder getMenuBuilder() {
	    return menuBuilder;
	}

	public TableMasterRegistryManager getTableMasterRegistryManager() {
	    return tableMasterRegistryManager;
	}

	public SFCRDocumentManager getSFCRDocumentManager() {
	    return sfcrDocumentManager;
	}

	CirSimBootstrap getBootstrap() {
	    return bootstrap;
	}

	public void initializeRunnerForHeadlessExecution() {
	    bootstrap.initRunner();
	}

	public void analyzeAndPreStampForHeadlessExecution() {
	    analyzeCircuit();
	    preStampAndStampCircuit();
	}

	CirSimInitializer getInitializer() {
	    return initializer;
	}

	ToolbarModeManager getToolbarModeManager() {
	    return toolbarModeManager;
	}

	SetupListLoader getSetupListLoader() {
	    return setupListLoader;
	}

	RunnerController getRunnerController() {
	    return runnerController;
	}

	public ClipboardManager getClipboardManager() {
	    return clipboardManager;
	}

	FlipTransformController getFlipTransformController() {
	    return flipTransformController;
	}

	StatusInfoRenderer getStatusInfoRenderer() {
	    return statusInfoRenderer;
	}

	MatrixStamper getMatrixStamper() {
	    return matrixStamper;
	}

	public int getVoltageSourceCountForMatrix(CircuitElm elm) {
	    return elm.getVoltageSourceCount();
	}

	SimulationLoop getSimulationLoop() {
	    return simulationLoop;
	}

	JsApiBridge getJsApiBridge() {
	    return jsApiBridge;
	}

	CirSimPlatformInterop getPlatformInterop() {
		return platformInterop;
	}

	public boolean isElectron() {
		return platformInterop.isElectron();
	}

	public java.util.Vector<Adjustable> getAdjustablesForUi() {
	    return adjustables;
	}

	UndoRedoManager getUndoRedoManager() {
	    return undoRedoManager;
	}

	public void pushUndoForUi() {
	    undoRedoManager.pushUndo();
	}

	public boolean isEditingLocked() {
	    return noEditCheckItem != null && noEditCheckItem.getState();
	}

	public void setEditingLocked(boolean locked) {
	    if (noEditCheckItem != null)
		noEditCheckItem.setState(locked);
	}

	public void copyImageToClipboardForUi() {
	    doImageToClipboardCore();
	}

	public static int cacImageTypeForUi() {
	    return CAC_IMAGE;
	}

    void launchRunnerFromQuery(QueryParameters qp) {
	runnerController.launchFromQuery(qp);
    }

    private void onRunnerLoadFileSuccess(String text, Command successCallback) {
	getCircuitIOService().readCircuit(text, RC_KEEP_TITLE);
	unsavedChanges = false;
	if (successCallback != null)
	    successCallback.execute();
    }

    private void loadFileFromURLRunner(String url, final Command successCallback, final Command failureCallback) {
	final String loadUrl = getCircuitIOService().getLoadUrl(url);
	console("loadFileFromURLRunner request: " + loadUrl);
	final NativeXhr xhr = new NativeXhr();
	try {
	    xhr.open("GET", loadUrl, false);
	    xhr.send();
	    int status = xhr.getStatus();
	    if (status >= 200 && status < 300) {
		console("loadFileFromURLRunner success: " + loadUrl + " status=" + status);
		onRunnerLoadFileSuccess(xhr.getResponseText(), successCallback);
		return;
	    }
	    console("loadFileFromURLRunner HTTP failure: " + loadUrl + " status=" + status);
	    if (failureCallback != null)
		failureCallback.execute();
	} catch (Throwable t) {
	    console("loadFileFromURLRunner xhr exception: " + t + " (falling back to RequestBuilder)");
	    getCircuitIOService().loadFileFromURL(url, successCallback, failureCallback);
	}
    }

    void loadFileFromURLRunnerWithCallbacks(String url, final Runnable successCallback, final Runnable failureCallback) {
	Command successCommand = (successCallback == null) ? null : new Command() {
	    public void execute() {
		successCallback.run();
	    }
	};
	Command failureCommand = (failureCallback == null) ? null : failureCallback::run;
	loadFileFromURLRunner(url, successCommand, failureCommand);
    }

    String startCircuit = null;
    String startLabel = null;
    String startCircuitText = null;
    String startCircuitLink = null;
//    String baseURL = "http://www.falstad.com/circuit/";
    
    MenuItem menuItemWithShortcut(String icon, String text, String shortcut, MyCommand cmd) {
	final String edithtml="<div style=\"white-space:nowrap\"><div style=\"display:inline-block;width:100%;\"><i class=\"cirjsicon-";
	String nbsp = "&nbsp;";
	if (icon=="") nbsp="";
	String sn=edithtml + icon + "\"></i>" + nbsp + Locale.LS(text) + "</div>" + shortcut + "</div>";
	return new MenuItem(SafeHtmlUtils.fromTrustedString(sn), cmd);
    }
    
    MenuItem iconMenuItem(String icon, String text, Command cmd) {
        String icoStr = "<i class=\"cirjsicon-" + icon + "\"></i>&nbsp;" + Locale.LS(text); //<i class="cirjsicon-"></i>&nbsp;
        return new MenuItem(SafeHtmlUtils.fromTrustedString(icoStr), cmd);
    }
    
    // get circuit bounds.  remember this doesn't use setBbox().  That is calculated when we draw
    // the circuit, but this needs to be ready before we first draw it, so we use this crude method
    Rectangle getCircuitBounds() {
    	int minx = 30000, maxx = -30000, miny = 30000, maxy = -30000;
    	for (CircuitElm ce : elmList) {
    		// centered text causes problems when trying to center the circuit,
    		// so we special-case it here
    		if (!ce.isCenteredText()) {
    			minx = Math.min(ce.x, Math.min(ce.x2, minx));
    			maxx = Math.max(ce.x, Math.max(ce.x2, maxx));
    		}
    		miny = Math.min(ce.y, Math.min(ce.y2, miny));
    		maxy = Math.max(ce.y, Math.max(ce.y2, maxy));
    	}
    	if (minx > maxx)
    	    return null;
    	return new Rectangle(minx, miny, maxx-minx, maxy-miny);
    }

    long lastTime = 0, lastFrameTime, lastIterTime, secTime = 0;
    int frames = 0;
    int steps = 0;
    int framerate = 0, steprate = 0;
    private static CirSim theSim;

    public static CirSim getInstance() {
	return theSim;
    }

    public boolean isCacheBustedUrlsEnabled() {
	return enableCacheBustedUrls;
    }
    
    // Test dialog for mathematical elements
    private static MathElementsTestDialog mathTestDialog = null;
    
    // Test dialog for table elements
    private static TableElementsTestDialog tableTestDialog = null;
    
    // Graphics update throttling - reduce redraw rate for better performance
    int graphicsFrameCounter = 0;
    int graphicsUpdateInterval = 2; // Update graphics every N frames (configurable in options)

    
    public void setSimRunning(boolean s) {
	if (RuntimeMode.isNonInteractiveRuntime()) {
	    simRunning = s;
	    return;
	}
    	if (s) {
    	    	if (stopMessage != null)
    	    	    return;
    		simRunning = true;
    		timer.scheduleRepeating(FASTTIMER);
    		
    		// Clear paused state when user manually starts simulation
    		ActionScheduler scheduler = ActionScheduler.getInstance();
    		if (scheduler != null) {
    		    scheduler.clearPausedState();
    		}
    		
    		updateRunStopButton();
    	} else {
    		simRunning = false;
    		timer.cancel();
    		
    		// Cancel any pending action timer when user stops simulation
    		ActionScheduler scheduler = ActionScheduler.getInstance();
    		if (scheduler != null) {
    		    scheduler.cancelResumeTimer();
    		}
    		
    		updateRunStopButton();
		repaint();
    	}
    }
    
    /**
     * Update the Run/Stop button appearance based on simulation state.
     * Delegates to FloatingControlPanel.
     */
    private void updateRunStopButton() {
    	if (floatingControlPanel != null) {
    	    floatingControlPanel.updateRunStopButton();
    	}
    }
    
    /**
     * Step the circuit forward by one simulation iteration
     * This runs the circuit once even if paused, useful for debugging
     * or for seeing initial conditions after reset
     */
    public void stepCircuit() {
        // Temporarily enable simulation to run one iteration
        boolean wasRunning = simRunning;
        
        // If stopped, run one update cycle
        if (!wasRunning) {
			if (SequenceDiagramElm.advanceManualAnimationStep(this)) {
				repaint();
				return;
			}
            simRunning = true;
            updateCircuit();
            simRunning = false;
            repaint();
        }
    }
    
    public boolean simIsRunning() {
    	return simRunning;
    }
    
    private boolean needsRepaint;
    
    public void repaint() {
	if (RuntimeMode.isNonInteractiveRuntime())
	    return;
	if (!needsRepaint) {
	    needsRepaint = true;
	    Scheduler.get().scheduleFixedDelay(new Scheduler.RepeatingCommand() {
		public boolean execute() {
		      updateCircuit();
		      needsRepaint = false;
		      return false;
		  }
	    }, FASTTIMER);
	}
    }
    
    // *****************************************************************
    //                     UPDATE CIRCUIT - MAIN LOOP
    // This is the heart of the simulator, called every frame
    // *****************************************************************
    
    /**
     * Main simulation loop - called every frame to update and render the circuit.
     * 
     * PERFORMANCE CRITICAL - This method runs every frame (target 20-60 FPS)
     * 
     * PHASES:
     * 1. ANALYZE (if needed) - Build node list, validate circuit structure
     *    - calculateWireClosure(): Group wires into nodes
     *    - makeNodeList(): Assign node numbers
     *    - validateCircuit(): Check for problematic configurations
     *    Cost: ~10-50ms for medium circuits (SKIPPED during drag)
     *    
     * 2. STAMP (if needed) - Populate MNA matrices
     *    - stamp(): Each element contributes to matrix (linear elements)
     *    - simplifyMatrix(): Remove trivial rows for performance
     *    - CircuitMatrixOps.luFactor(): Decompose matrix (for linear circuits only)
     *    Cost: ~5-20ms including O(n³) LU factorization (SKIPPED during drag)
     *    
     * 3. SIMULATE (if running) - Solve circuit for current timestep
     *    - runCircuit(): Iterate to convergence (nonlinear) or solve once (linear)
     *    - Matrix solve via LU decomposition: O(n³) cost
     *    Cost: ~1-10ms per frame (PAUSED during drag)
     *    
     * 4. RENDER - Draw circuit elements, scopes, and UI
     *    - Element drawing: each element draws itself
     *    - Scope drawing: voltage/current waveforms
     *    - Debug info: framerate, performance metrics
     *    Cost: ~5-15ms (ALWAYS runs for smooth visual feedback)
     * 
     * DRAG OPTIMIZATION: During drag operations, simulation is paused and analysis
     *                    is deferred until drag completes. This gives smooth 60 FPS
     *                    dragging even for large circuits (200+ elements).
     */
    public void updateCircuit() {
	simulationLoop.updateCircuit();
    }

    boolean scopeMenuIsSelected(Scope s) {
	if (getScopeManager().getScopeMenuSelected() < 0)
	    return false;
	if (getScopeManager().getScopeMenuSelected() < scopeCount)
	    return scopes[getScopeManager().getScopeMenuSelected()] == s;
	return scopeManager.getNthScopeElm(getScopeManager().getScopeMenuSelected()-scopeCount).elmScope == s; 
    }

    public boolean isScopeMenuSelectedForScope(Scope s) {
	return scopeMenuIsSelected(s);
    }
    
//    public void toggleSwitch(int n) {
//	int i;
//	for (i = 0; i != elmList.size(); i++) {
//	    CircuitElm ce = getElm(i);
//	    if (ce instanceof SwitchElm) {
//		n--;
//		if (n == 0) {
//		    ((SwitchElm) ce).toggle();
//		    analyzeFlag = true;
//		    cv.repaint();
//		    return;
//		}
//	    }
//	}
//    }
    
    public void needAnalyze() {
	analyzeFlag = true;
    	repaint();
	if (RuntimeMode.isGwt())
	    enableDisableMenuItems();
    }
    
    Vector<Point> postDrawList = new Vector<>();
    Vector<Point> badConnectionList = new Vector<>();
    CircuitElm voltageSources[];

    public CircuitElm getVoltageSourceElementForUi(int index) {
	if (voltageSources == null || index < 0 || index >= voltageSourceCount) {
	    return null;
	}
	return voltageSources[index];
    }

    public CircuitNode getCircuitNode(int n) {
	return circuitAnalyzer.getCircuitNode(n);
    }

    public CircuitElm getElm(int n) {
	if (n >= elmList.size())
	    return null;
	return elmList.elementAt(n);
    }

	public void addElement(CircuitElm ce) {
	    if (ce == null)
		return;
	    ensureElementHasZOrder(ce);
	    elmList.addElement(ce);
	}

	void ensureElementHasZOrder(CircuitElm ce) {
	    if (ce == null || ce.hasAssignedZOrder())
		return;
	    ce.setZOrder(getNextZOrderValue());
	}

	private int getNextZOrderValue() {
	    int maxZOrder = -1;
	    for (int i = 0; i != elmList.size(); i++) {
		CircuitElm ce = elmList.elementAt(i);
		if (ce.hasAssignedZOrder() && ce.getZOrder() > maxZOrder)
		    maxZOrder = ce.getZOrder();
	    }
	    return maxZOrder + 1;
	}

	private ArrayList<CircuitElm> getElementsSortedByZOrder(final boolean descending, final boolean selectedOnTop) {
	    ArrayList<CircuitElm> ordered = new ArrayList<CircuitElm>(elmList.size());
	    for (int i = 0; i != elmList.size(); i++) {
		CircuitElm ce = elmList.elementAt(i);
		ensureElementHasZOrder(ce);
		ordered.add(ce);
	    }
	    Collections.sort(ordered, new Comparator<CircuitElm>() {
		public int compare(CircuitElm a, CircuitElm b) {
		    if (selectedOnTop && a.isSelected() != b.isSelected())
			return a.isSelected() ? 1 : -1;
		    int cmp = a.getZOrder() < b.getZOrder() ? -1 : (a.getZOrder() > b.getZOrder() ? 1 : 0);
		    return descending ? -cmp : cmp;
		}
	    });
	    return ordered;
	}

	ArrayList<CircuitElm> getElementsInDrawOrder() {
	    return getElementsSortedByZOrder(false, true);
	}

	ArrayList<CircuitElm> getElementsInPickOrder() {
	    return getElementsSortedByZOrder(true, false);
	}

	private boolean hasSelectedElements() {
	    for (int i = 0; i != elmList.size(); i++) {
		if (elmList.elementAt(i).isSelected())
		    return true;
	    }
	    return false;
	}

	private void rewriteZOrder(ArrayList<CircuitElm> ordered) {
	    for (int i = 0; i != ordered.size(); i++)
		ordered.get(i).setZOrder(i);
	}

	private void markVisualOrderChanged() {
	    needsRecoverySave = true;
	    unsavedChanges = true;
	}

	void bringToFront(CircuitElm fallbackElm) {
	    reorderVisualSelection(fallbackElm, true);
	}

	void sendToBack(CircuitElm fallbackElm) {
	    reorderVisualSelection(fallbackElm, false);
	}

	private void reorderVisualSelection(CircuitElm fallbackElm, boolean toFront) {
	    ArrayList<CircuitElm> ordered = getElementsSortedByZOrder(false, false);
	    boolean useSelection = hasSelectedElements();
	    ArrayList<CircuitElm> targets = new ArrayList<CircuitElm>();
	    ArrayList<CircuitElm> others = new ArrayList<CircuitElm>();
	    for (int i = 0; i != ordered.size(); i++) {
		CircuitElm ce = ordered.get(i);
		boolean isTarget = useSelection ? ce.isSelected() : ce == fallbackElm;
		if (isTarget)
		    targets.add(ce);
		else
		    others.add(ce);
	    }
	    if (targets.isEmpty())
		return;
	    ArrayList<CircuitElm> reordered = new ArrayList<CircuitElm>(ordered.size());
	    if (toFront) {
		reordered.addAll(others);
		reordered.addAll(targets);
	    } else {
		reordered.addAll(targets);
		reordered.addAll(others);
	    }
	    rewriteZOrder(reordered);
	    markVisualOrderChanged();
	}

    public String[] getSortedLabeledNodeNames() {
	return LabeledNodeElm.getSortedLabeledNodeNames();
    }

    public Rectangle getCircuitArea() {
	return new Rectangle(circuitArea);
    }

    public int getGridSize() {
	return gridSize;
    }

    public CircuitElm getPlotXElm() {
        return plotXElm;
    }

    public CircuitElm getPlotYElm() {
        return plotYElm;
    }

    public boolean isEuroResistorForUi() {
	return euroResistorCheckItem.getState();
    }

    public boolean isEuroGatesForUi() {
	return euroGatesCheckItem.getState();
    }

    public int getElementCount() {
	return elmList.size();
    }

    public boolean isPositionClearForVariablePlacement(int gx, int gy) {
	int minDistance = gridSize * 3;
	for (int i = 0; i < elmList.size(); i++) {
	    CircuitElm ce = getElm(i);
	    Rectangle bbox = ce.getBoundingBox();
	    if (bbox != null) {
		int margin = minDistance;
		if (ce instanceof TableElm || ce instanceof GodlyTableElm) {
		    margin = gridSize * 2;
		}
		if (gx >= bbox.x - margin && gx <= bbox.x + bbox.width + margin &&
		    gy >= bbox.y - margin && gy <= bbox.y + bbox.height + margin) {
		    return false;
		}
	    } else {
		int dx1 = ce.x - gx;
		int dy1 = ce.y - gy;
		double dist1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
		if (dist1 < minDistance)
		    return false;

		int dx2 = ce.x2 - gx;
		int dy2 = ce.y2 - gy;
		double dist2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);
		if (dist2 < minDistance)
		    return false;

		if (ce.x != ce.x2 || ce.y != ce.y2) {
		    double lineLength = Math.sqrt((ce.x2 - ce.x) * (ce.x2 - ce.x) +
						 (ce.y2 - ce.y) * (ce.y2 - ce.y));
		    if (lineLength > 0) {
			double distToLine = Math.abs((ce.y2 - ce.y) * gx - (ce.x2 - ce.x) * gy +
						     ce.x2 * ce.y - ce.y2 * ce.x) / lineLength;
			double t = ((gx - ce.x) * (ce.x2 - ce.x) + (gy - ce.y) * (ce.y2 - ce.y)) /
				   (lineLength * lineLength);
			if (t >= 0 && t <= 1 && distToLine < minDistance)
			    return false;
		    }
		}
	    }
	}
	return true;
    }

    public Adjustable findAdjustable(CircuitElm elm, int item) {
	int i;
	for (i = 0; i != adjustables.size(); i++) {
	    Adjustable a = adjustables.get(i);
	    if (a.elm == elm && a.editItem == item)
		return a;
	}
	return null;
    }
    
    public static void console(String text) {
	if (RunnerPanelUi.isRunnerStdoutEnabled())
	    RunnerPanelUi.appendRunnerStdout(text);
	if (RuntimeMode.isGwt())
	    GWT.log(text);
	else
	    System.err.println(text);
    }

    @Override
    public void log(String message) {
	console(message);
    }

    public void alertOrWarn(String message) {
	if (RuntimeMode.isGwt())
	    Window.alert(message);
	else
	    console("WARNING: " + message);
    }

	public static void debugger() {
	}
    
    static class NodeMapEntry {
	int node;
	NodeMapEntry() { node = -1; }
	NodeMapEntry(int n) { node = n; }
    }
    
    static class WireInfo {
	CircuitElm wire;
	Vector<CircuitElm> neighbors;
	int post;
	WireInfo(CircuitElm w) {
	    wire = w;
	}
    }
    


    /**
     * Register table masters in priority order (highest priority first).
     * 
     * CRITICAL FOR STOCK-FLOW DIAGRAMS: This ensures higher priority tables
     * register first and become masters, preventing replacements that would
     * cause duplicate voltage sources.
     * 
     * PROBLEM: Without priority ordering, tables register in circuit order.
     * Lower priority tables may temporarily become masters, create output pins,
     * then get replaced by higher priority tables, leaving duplicate voltage sources.
     * 
     * SOLUTION: Sort tables by priority before registration.
     * - Higher priority tables register first and "win" master status
     * - Lower priority tables find existing masters and become followers
     * - No replacements occur, no duplicate voltage sources created
     * 
     * PUBLIC: Can be called from TableEditDialog to recalculate masters immediately
     * after priority or stock name changes, ensuring UI lock status is updated.
     * 
     * @see TableElm#registerAsMasterOnly()
     * @see ComputedValues
     */
    // analyze the circuit when something changes, so it can be simulated.
    // Most of this has been moved to preStampCircuit() so it can be avoided if the simulation is stopped.
    void analyzeCircuit() {
	circuitAnalyzer.analyzeCircuit();
    }

    // do the rest of the pre-stamp circuit analysis
    boolean preStampCircuit() {
	return circuitAnalyzer.preStampCircuit(true);
    }

    // do pre-stamping and then stamp circuit
    void preStampAndStampCircuit() {
	circuitAnalyzer.preStampAndStampCircuit();
    }

    // stamp the matrix, meaning populate the matrix as required to simulate the circuit (for all linear elements, at least).
    // this gets called after something changes in the circuit, and also when auto-adjusting timestep
    void stampCircuit() {
	circuitAnalyzer.stampCircuit();
    }

	public void stop(String s, CircuitElm ce) {
	stopMessage = Locale.LS(s);
	solverMatrixState.circuitMatrix = null;  // causes an exception
	stopElm = ce;
	setSimRunning(false);
	analyzeFlag = false;
//	cv.repaint();
    }
    
    /**
     * Stamp voltage-controlled voltage source (VCVS).
     * 
     * Controls voltage source 'vs' based on voltage difference V(n1) - V(n2).
     * Output voltage = coef × (V(n1) - V(n2))
     * 
     * Must also call stampVoltageSource() to establish the output terminals.
     * 
     * @param n1 Control voltage positive node
     * @param n2 Control voltage negative node
     * @param coef Voltage gain coefficient
     * @param vs Voltage source index to control
     */
    public void stampVCVS(int n1, int n2, double coef, int vs) {
	matrixStamper.stampVCVS(n1, n2, coef, vs);
    }
    
    /**
     * Stamp independent voltage source into MNA matrix.
     * 
     * MODIFIED NODAL ANALYSIS: Voltage sources add an extra row/column to the matrix
     * because we need to solve for the source current (unknown).
     * 
     * For voltage source from n1 to n2 with voltage v:
     * - Voltage constraint: V(n2) - V(n1) = v
     * - Current constraint: I(n1) = -I(vs), I(n2) = I(vs)
     * 
     * Matrix entries:
     * - Row(vs): -V(n1) + V(n2) = v          [voltage equation]
     * - Row(n1): ... + I(vs) = ...           [KCL at n1]
     * - Row(n2): ... - I(vs) = ...           [KCL at n2]
     * 
     * @param n1 Source node (negative terminal)
     * @param n2 Destination node (positive terminal)
     * @param vs Voltage source index
     * @param v Voltage value
     */
    public void stampVoltageSource(int n1, int n2, int vs, double v) {
	matrixStamper.stampVoltageSource(n1, n2, vs, v);
    }

    // use this if the amount of voltage is going to be updated in doStep(), by updateVoltageSource()
    public void stampVoltageSource(int n1, int n2, int vs) {
	matrixStamper.stampVoltageSource(n1, n2, vs);
    }
    
    // update voltage source in doStep()
    public void updateVoltageSource(int n1, int n2, int vs, double v) {
	matrixStamper.updateVoltageSource(n1, n2, vs, v);
    }
    
    public void stampResistor(int n1, int n2, double r) {
	matrixStamper.stampResistor(n1, n2, r);
    }

    public void stampConductance(int n1, int n2, double r0) {
	matrixStamper.stampConductance(n1, n2, r0);
    }

    // specify that current from cn1 to cn2 is equal to voltage from vn1 to 2, divided by g
    public void stampVCCurrentSource(int cn1, int cn2, int vn1, int vn2, double g) {
	matrixStamper.stampVCCurrentSource(cn1, cn2, vn1, vn2, g);
    }

    public void stampCurrentSource(int n1, int n2, double i) {
	matrixStamper.stampCurrentSource(n1, n2, i);
    }

    // stamp a current source from n1 to n2 depending on current through vs
    public void stampCCCS(int n1, int n2, int vs, double gain) {
	matrixStamper.stampCCCS(n1, n2, vs, gain);
    }

    // stamp value x in row i, column j, meaning that a voltage change
    // of dv in node j will increase the current into node i by x dv.
    // (Unless i or j is a voltage source node.)
    public void stampMatrix(int i, int j, double x) {
	matrixStamper.stampMatrix(i, j, x);
    }

    // stamp value x on the right side of row i, representing an
    // independent current source flowing into node i
    public void stampRightSide(int i, double x) {
	matrixStamper.stampRightSide(i, x);
    }

    // indicate that the value on the right side of row i changes in doStep()
    public void stampRightSide(int i) {
	matrixStamper.stampRightSide(i);
    }
    
    // indicate that the values on the left side of row i change in doStep()
    public void stampNonLinear(int i) {
	matrixStamper.stampNonLinear(i);
    }

	private boolean converged;

    public double getTimeStep() {
	return getTimingState().timeStep;
    }

    public void setTimeStep(double timeStep) {
	getTimingState().timeStep = timeStep;
    }

    public double getTime() {
	return getTimingState().t;
    }

    public void setTime(double time) {
	getTimingState().t = time;
    }

    public double getMaxTimeStep() {
	return getTimingState().maxTimeStep;
    }

    public void setMaxTimeStep(double maxTimeStep) {
	getTimingState().maxTimeStep = maxTimeStep;
    }

    public int getSubIterations() {
	return subIterations;
    }

    public boolean isConverged() {
	return converged;
    }

    public String getStopMessageForTesting() {
	return stopMessage;
    }

    public void setConverged(boolean converged) {
	this.converged = converged;
    }

    @Override
    public boolean isEquationTableMnaMode() {
	return equationTableMnaMode;
    }

    public void setEquationTableMnaMode(boolean equationTableMnaMode) {
	this.equationTableMnaMode = equationTableMnaMode;
    }

    @Override
    public boolean isSfcrLookupClampDefault() {
	return sfcrLookupClampDefault;
    }

    public void setSfcrLookupClampDefault(boolean sfcrLookupClampDefault) {
	this.sfcrLookupClampDefault = sfcrLookupClampDefault;
    }

    @Override
    public double getEquationTableConvergenceTolerance() {
	return equationTableConvergenceTolerance;
    }

    public void setEquationTableConvergenceTolerance(double equationTableConvergenceTolerance) {
	if (equationTableConvergenceTolerance > 0) {
	    this.equationTableConvergenceTolerance = equationTableConvergenceTolerance;
	}
    }
    int subIterations;
    int periodicInterval = 100; // process every 100 timesteps
	int nextPeriodicTime = 0;

	// Kept as package-visible wrappers because other controllers call sim.min/max.
	int min(int a, int b) { return Math.min(a, b); }
    int max(int a, int b) { return Math.max(a, b); }

    public void resetAction(){
    	int i;
    	analyzeFlag = true;
    	timingState.t = timingState.timeStepAccum = 0;
    	timingState.timeStepCount = 0;
    	timingState.realTimeStart = System.currentTimeMillis();
    	
    	// Clear computed values before resetting elements to prevent stale values
    	ComputedValues.clearComputedValues();
    	
    	// Clear master table registrations to ensure clean state
    	ComputedValues.clearMasterTables();
		    	
    	// Clear node voltages to ensure clean start
    	zeroVoltages(solverMatrixState.nodeVoltages);
    	zeroVoltages(solverMatrixState.lastNodeVoltages);
 
    	
    	for (i = 0; i != elmList.size(); i++)
		getElm(i).reset();
	for (i = 0; i != scopeCount; i++)
		scopes[i].resetGraph(true);
	
	// Reset action scheduler
	ActionScheduler scheduler = ActionScheduler.getInstance(this);
	scheduler.reset();
	
    	repaint();
    }

    // Avoid duplicated loops when resetting solver state arrays.
    private void zeroVoltages(double[] values) {
	if (values == null)
	    return;
	for (int i = 0; i < values.length; i++)
	    values[i] = 0.0;
    }

    public void onScenarioActivated(boolean resetPlots, boolean openPlotlyViewer) {
	if (resetPlots) {
	    for (int i = 0; i != scopeCount; i++)
		scopes[i].resetGraph(true);
	}
	if (openPlotlyViewer) {
	    new ScopeViewerDialog(this, null, true);
	}
    }

	public void openMathTestDialogCore() {
	if (mathTestDialog == null) {
	    mathTestDialog = new MathElementsTestDialog();
	}
	mathTestDialog.show();
	}

	public void openTableTestDialogCore() {
	if (tableTestDialog == null) {
	    tableTestDialog = new TableElementsTestDialog();
	}
	tableTestDialog.show();
	}

	String compressForUrl(String dump) {
	return compressUri(dump);
	}

    private static void clipboardWriteImage(CanvasElement cv) {
	try {
	    clipboardWriteText(cv.toDataUrl("image/png"));
	} catch (Throwable t) {
	}
    }

	private void doImageToClipboardCore() {
	Canvas cv = CirSim.getInstance().getExportCompositeActions().getCircuitAsCanvas(CAC_IMAGE);
	clipboardWriteImage(cv.getCanvasElement());
	}

    static class ElementDumpParseResult {
	StringTokenizer tokenizer;
	String uid;
	ElementDumpParseResult(StringTokenizer tokenizer, String uid) {
	    this.tokenizer = tokenizer;
	    this.uid = uid;
	}
    }

    void setCircuitTitle(String s) {
	if (s != null && titleLabel != null)
	    titleLabel.setText(s);
    }

    void clearCircuitTitle() {
	if (titleLabel != null)
	    titleLabel.setText(null);
    }

    void setDefaultControlBars() {
	if (speedBar != null)
	    speedBar.setValue(117);
	if (currentBar != null)
	    currentBar.setValue(50);
	if (powerBar != null)
	    powerBar.setValue(50);
    }

	void setSpeedBarForInit(Scrollbar bar) {
	speedBar = bar;
	}

	void setCurrentBarForInit(Scrollbar bar) {
	currentBar = bar;
	}

	void setPowerBarForInit(Scrollbar bar) {
	powerBar = bar;
	}

	void setPowerLabelForInit(Label label) {
	powerLabel = label;
	}

	void setTitleLabelForInit(Label label) {
	titleLabel = label;
	}
    
    static final int RC_RETAIN = 1;
    static final int RC_NO_CENTER = 2;
    static final int RC_SUBCIRCUITS = 4;
    static final int RC_KEEP_TITLE = 8;

    // delete sliders for an element
    void deleteSliders(CircuitElm elm) {
	int i;
	if (adjustables == null)
	    return;
	for (i = adjustables.size()-1; i >= 0; i--) {
	    Adjustable adj = adjustables.get(i);
	    if (adj.elm == elm) {
		adj.deleteSlider(this);
		adjustables.remove(i);
	    }
	}
    }
    
    void readHint(StringTokenizer st) {
	hintType  = Integer.parseInt(st.nextToken());
	hintItem1 = Integer.parseInt(st.nextToken());
	hintItem2 = Integer.parseInt(st.nextToken());
    }

    void readOptions(StringTokenizer st, int importFlags) {
	int flags = Integer.parseInt(st.nextToken());
	
	if ((importFlags & RC_RETAIN) != 0) {
            // need to set small grid if pasted circuit uses it
	    if ((flags & 2) != 0 && smallGridCheckItem != null)
		smallGridCheckItem.setState(true);
	    return;
	}
	
	if (dotsCheckItem != null)
	    dotsCheckItem.setState((flags & 1) != 0);
	if (smallGridCheckItem != null)
	    smallGridCheckItem.setState((flags & 2) != 0);
	if (voltsCheckItem != null)
	    voltsCheckItem.setState((flags & 4) == 0);
	if (powerCheckItem != null)
	    powerCheckItem.setState((flags & 8) == 8);
	if (showValuesCheckItem != null)
	    showValuesCheckItem.setState((flags & 16) == 0);
	adjustTimeStep = (flags & 64) != 0;
	timingState.maxTimeStep = timingState.timeStep = new Double (st.nextToken()).doubleValue();
	double sp = Double.parseDouble(st.nextToken());
	int sp2 = (int) (Math.log(10*sp)*24+61.5);
	//int sp2 = (int) (Math.log(sp)*24+1.5);
	if (speedBar != null)
	    speedBar.setValue(sp2);
	if (currentBar != null)
	    currentBar.setValue(Integer.parseInt(st.nextToken()));
	else
	    st.nextToken();
	CircuitElm.voltageRange = new Double (st.nextToken()).doubleValue();

	try {
	    int pbv = Integer.parseInt(st.nextToken());
	    if (powerBar != null)
		powerBar.setValue(pbv);
	    timingState.minTimeStep = Double.parseDouble(st.nextToken());
	} catch (Exception e) {
	}
	preferencesManager.setGrid();
    }
    
    public int snapGrid(int x) {
	return (x+gridRound) & gridMask;
    }

    int locateElm(CircuitElm elm) {
	int i;
	for (i = 0; i != elmList.size(); i++)
	    if (elm == elmList.elementAt(i))
		return i;
	return -1;
    }

    public int locateElmForScope(CircuitElm elm) {
	return locateElm(elm);
    }

    void doSplit(CircuitElm ce) {
	int x = snapGrid(inverseTransformX(getMenuX()));
	int y = snapGrid(inverseTransformY(getMenuY()));
	if (ce == null || !(ce instanceof WireElm))
	    return;
	if (ce.x == ce.x2)
	    x = ce.x;
	else
	    y = ce.y;
	
	// don't create zero-length wire
	if (x == ce.x && y == ce.y || x == ce.x2 && y == ce.y2)
	    return;
	
	WireElm newWire = new WireElm(x, y);
	newWire.drag(ce.x2, ce.y2);
	ce.drag(x, y);
	addElement(newWire);
	needAnalyze();
    }
    
    void enableDisableMenuItems() {
	boolean canFlipX = true;
	boolean canFlipY = true;
	boolean canFlipXY = true;
	int selCount = clipboardManager.countSelected();
	for (CircuitElm elm : elmList) {
	    if (elm.isSelected() || selCount == 0) {
		if (!elm.canFlipX())
		    canFlipX = false;
		if (!elm.canFlipY())
		    canFlipY = false;
		if (!elm.canFlipXY())
		    canFlipXY = false;
		// If there is at least one selected element, we can stop once all options are disabled.
		if (selCount > 0 && !canFlipX && !canFlipY && !canFlipXY)
		    break;
	    }
	}
	cutItem.setEnabled(selCount > 0);
	copyItem.setEnabled(selCount > 0);
	flipXItem.setEnabled(canFlipX);
	flipYItem.setEnabled(canFlipY);
	flipXYItem.setEnabled(canFlipXY);
    }

    /**
     * Set the element currently under the mouse cursor.
     * 
     * Updates visual feedback (element highlighting) and notifies
     * adjustable elements (sliders) about the current mouse element.
     * 
     * @param ce Element to set as mouse element (or null)
     */
    void setMouseElm(CircuitElm ce) {
	if (ce == mouseElm)
	    return;
	if (mouseElm != null)
	    mouseElm.setMouseElm(false);
	if (ce != null)
	    ce.setMouseElm(true);
	mouseElm = ce;
	for (int i = 0; i < adjustables.size(); i++)
	    adjustables.get(i).setMouseElm(ce);
	updateHighlightedNode(ce);
    }

	public void setMouseElmForUi(CircuitElm ce) {
	    setMouseElm(ce);
	}

    private void updateHighlightedNode(CircuitElm ce) {
	// Track highlighted MNA node for cross-element highlighting.
	// Use labeled node mapping when available, then fall back to the first physical node.
	if (ce instanceof LabeledNodeElm) {
	    Integer labelNode = LabeledNodeElm.getByName(((LabeledNodeElm) ce).getName());
	    highlightedNode = (labelNode != null) ? labelNode : ce.nodes[0];
	    return;
	}
	highlightedNode = -1;
    }

    void removeZeroLengthElements() {
    	int i;
    	boolean changed = false;
    	for (i = elmList.size()-1; i >= 0; i--) {
    		CircuitElm ce = getElm(i);
    		if (ce.x == ce.x2 && ce.y == ce.y2) {
    			elmList.removeElementAt(i);
    			ce.delete();
    			changed = true;
    		}
    	}
    	// Re-analysis is expensive; skip it if nothing was removed.
    	if (changed)
    	    needAnalyze();
    }
    
    /**
     * Convert screen coordinates to circuit grid coordinates.
     * Inverts the circuit transform (zoom and pan).
     * 
     * @param x Screen X coordinate (pixels from left edge)
     * @return Grid X coordinate in circuit space
     */
    public int inverseTransformX(double x) {
	return getViewportController().inverseTransformX(x);
    }

    /**
     * Convert screen coordinates to circuit grid coordinates.
     * Inverts the circuit transform (zoom and pan).
     * 
     * @param y Screen Y coordinate (pixels from top edge)
     * @return Grid Y coordinate in circuit space
     */
    public int inverseTransformY(double y) {
	return getViewportController().inverseTransformY(y);
    }
    
    /**
     * Convert circuit grid coordinates to screen coordinates.
     * Applies circuit transform (zoom and pan).
     * 
     * @param x Grid X coordinate in circuit space
     * @return Screen X coordinate (pixels)
     */
    private int transformX(double x) {
	return getViewportController().transformX(x);
    }

    public int transformXForUiElement(double x) {
	return transformX(x);
    }
    
    /**
     * Convert circuit grid coordinates to screen coordinates.
     * Applies circuit transform (zoom and pan).
     * 
     * @param y Grid Y coordinate in circuit space
     * @return Screen Y coordinate (pixels)
     */
    private int transformY(double y) {
	return getViewportController().transformY(y);
    }

    public int transformYForUiElement(double y) {
	return transformY(y);
    }

    double[] getTransform() {
	return getViewportController().getTransform();
    }

    public double[] getTransformForUiElement() {
	return getTransform();
    }

    public boolean isExportingImage() {
	return isExporting;
    }

    int getMenuClientX() {
	return getViewportController().getMenuClientX();
    }

    void setMenuClientX(int value) {
	getViewportController().setMenuClientX(value);
    }

    int getMenuClientY() {
	return getViewportController().getMenuClientY();
    }

    void setMenuClientY(int value) {
	getViewportController().setMenuClientY(value);
    }

    private int getMenuX() {
	return getViewportController().getMenuX();
    }

    void setMenuX(int value) {
	getViewportController().setMenuX(value);
    }

    private int getMenuY() {
	return getViewportController().getMenuY();
    }

    void setMenuY(int value) {
	getViewportController().setMenuY(value);
    }

    static int lastSubcircuitMenuUpdate;
    
	    private static final int MAX_NORMALIZED_WHEEL_DELTA = 150;

	    // Modern browsers can report large pixel deltas from high-resolution
	    // trackpads. Clamp to keep zoom/edit interactions consistent.
	    public static int normalizeWheelDelta(int deltaY) {
		if (deltaY > MAX_NORMALIZED_WHEEL_DELTA)
		    return MAX_NORMALIZED_WHEEL_DELTA;
		if (deltaY < -MAX_NORMALIZED_WHEEL_DELTA)
		    return -MAX_NORMALIZED_WHEEL_DELTA;
		return deltaY;
	    }
    void setPowerBarEnable() {
    	if (powerCheckItem.getState()) {
    	    powerLabel.setStyleName("disabled", false);
    	    powerBar.enable();
    	} else {
    	    powerLabel.setStyleName("disabled", true);
    	    powerBar.disable();
    	}
    }

    void scrollValues(int x, int y, int deltay) {
    	if (mouseElm!=null && !dialogIsShowing() && getScopeManager().getScopeSelected() == -1)
    		if (mouseElm instanceof ResistorElm || mouseElm instanceof CapacitorElm ||  mouseElm instanceof InductorElm) {
    			CirSimDialogCoordinator.setScrollValuePopup(new ScrollValuePopup(x, y, deltay, mouseElm, this));
    		}
    }
    
    void enableItems() {
    }
    
    public void setToolbar() {
	layoutPanel.setWidgetHidden(toolbar, !toolbarCheckItem.getState());
	getViewportController().setCanvasSize();
    }
    
	CircuitElm getMouseElmForRouting() {
	return mouseElm;
	}

	void setLastInteractedTableForRouting(TableElm table) {
	lastInteractedTable = table;
	}

	TableElm getLastInteractedTableForRouting() {
	return lastInteractedTable;
	}

	public int getCurrentBarValueForRouting() {
	return currentBar.getValue();
	}

	public int getPowerBarValueForRouting() {
	return powerBar.getValue();
	}

	int getSpeedBarValueForRouting() {
	return speedBar.getValue();
	}

	CircuitRenderer getCircuitRendererForRouting() {
	return circuitRenderer;
	}

//    public void keyPressed(KeyEvent e) {}
//    public void keyReleased(KeyEvent e) {}
    
	boolean dialogIsShowing() {
	return CirSimDialogCoordinator.isAnyDialogShowing(menuUiState.contextPanel);
    }

    public boolean isDialogShowingForScope() {
	return dialogIsShowing();
    }

    public String formatTimeFixedForScope(double time) {
	return preferencesManager.formatTimeFixed(time);
    }
    
    void updateToolbar() {
	toolbar.setModeLabel(classToLabelMap.get(getMouseModeStr()));
	toolbar.highlightButton(getMouseModeStr());
    }

    public String getLabelTextForClass(String cls) {
	return classToLabelMap.get(cls);
    }

    public void updateModels() {
	int i;
	for (i = 0; i != elmList.size(); i++)
	    elmList.get(i).updateModels();
    }
    

    
    // For debugging
	void logElementRegistryInferenceReport() {
	diagnostics.logElementRegistryInferenceReport();
	}

	boolean loadedCanvas2SVG = false;

	static final int CAC_PRINT = 0;
	private static final int CAC_IMAGE = 1;
	static final int CAC_SVG   = 2;

}
