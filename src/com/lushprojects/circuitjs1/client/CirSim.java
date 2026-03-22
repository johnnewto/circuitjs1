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

// GWT conversion (c) 2015 by Iain Sharp

// For information about the theory behind this, see Electronic Circuit & System Simulation Methods by Pillage
// or https://github.com/sharpie7/circuitjs1/blob/master/INTERNALS.md

import java.util.Vector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.lang.Math;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CellPanel;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootLayoutPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.canvas.dom.client.Context2d.LineCap;
import com.google.gwt.event.dom.client.MouseEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.ScriptInjector;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.xhr.client.XMLHttpRequest;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.dom.client.CanvasElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.LabelElement;
import com.google.gwt.dom.client.MetaElement;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.storage.client.Storage;
import com.google.gwt.user.client.ui.PopupPanel;
import static com.google.gwt.event.dom.client.KeyCodes.*;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.Widget;
import com.lushprojects.circuitjs1.client.util.Locale;
import com.google.gwt.user.client.Window.ClosingEvent;
import com.google.gwt.user.client.Window.Navigator;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.i18n.client.NumberFormat;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

/**
 * CirSim - Main Circuit Simulator Class
 * 
 * This is the central controller for CircuitJS1, an electronic circuit simulator
 * that runs in web browsers. It implements Modified Nodal Analysis (MNA) based on
 * "Electronic Circuit and System Simulation Methods" by Pillage, Rohrer, & Visweswariah.
 * 
 * ARCHITECTURE:
 * - Circuit simulation uses MNA matrix equation: X = A⁻¹B
 *   where A is admittance matrix, B is right-hand side, X is solution (node voltages + source currents)
 * - Linear elements (R, L, C) are stamped once during analysis
 * - Nonlinear elements (diodes, transistors) require iterative solving
 * - Time integration uses Backward Euler (stable) or Trapezoidal (accurate) methods
 * 
 * MAIN LOOP (updateCircuit):
 * 1. Analyze circuit structure (if needed) - build node list, validate connections
 * 2. Stamp circuit matrix (if needed) - populate MNA matrices
 * 3. Run simulation iterations - solve matrix, update element states
 * 4. Draw graphics - render circuit visualization and scopes
 * 
 * PERFORMANCE OPTIMIZATIONS:
 * - Matrix simplification removes trivial rows (reduces O(n³) LU decomposition cost)
 * - Wire closure calculation groups connected wires to same node (smaller matrix)
 * - Element arrays cached to avoid type checks in inner loops
 * - Adaptive timestep reduces iterations when convergence is difficult
 * 
 * @author Paul Falstad, Iain Sharp
 * @see https://github.com/sharpie7/circuitjs1/blob/master/INTERNALS.md
 */
public class CirSim {

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
		@JsMethod(name = "postMessage") static native void postMessage(Object message, String targetOrigin);
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

	@JsMethod(namespace = JsPackage.GLOBAL, name = "C2S")
	private static native JavaScriptObject createC2SContext(int w, int h);

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	private static class SvgContextLike {
		@JsMethod(name = "getSerializedSvg") native String getSerializedSvg();
	}

	@JsFunction
	interface TouchEventHandler {
		void handle(TouchEventLike event);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "DOMRect")
	static class DomRectLike {
		@JsProperty(name = "left") native double getLeft();
		@JsProperty(name = "top") native double getTop();
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
	interface HookStringToString {
		String call(String value);
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
	    public NativeXhr() {}
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
    Button dumpMatrixButton;
    FloatingControlPanel floatingControlPanel;
    MenuItem aboutItem;
    MenuItem importFromLocalFileItem, importFromTextItem, exportAsUrlItem, exportAsLocalFileItem, exportAsTextItem,
            printItem, recoverItem, saveFileItem;
	MenuItem editLookupTablesItem;
    MenuItem importFromDropboxItem;
    MenuItem undoItem, redoItem, cutItem, copyItem, pasteItem, selectAllItem, optionsItem, flipXItem, flipYItem, flipXYItem;
    MenuBar optionsMenuBar;
    CheckboxMenuItem dotsCheckItem;
    CheckboxMenuItem voltsCheckItem;
    CheckboxMenuItem powerCheckItem;
    CheckboxMenuItem smallGridCheckItem;
    CheckboxMenuItem crossHairCheckItem;
    CheckboxMenuItem showValuesCheckItem;
    CheckboxMenuItem conductanceCheckItem;
    CheckboxMenuItem euroResistorCheckItem;
    CheckboxMenuItem euroGatesCheckItem;
    CheckboxMenuItem printableCheckItem;
    CheckboxMenuItem conventionCheckItem;
    CheckboxMenuItem noEditCheckItem;
    CheckboxMenuItem mouseWheelEditCheckItem;
    CheckboxMenuItem toolbarCheckItem;
    CheckboxMenuItem electronicsModeCheckItem;
    CheckboxMenuItem economicsModeCheckItem;
    CheckboxMenuItem weightedPriorityCheckItem;
    
    enum ToolbarType { ELECTRONICS, ECONOMICS }
    ToolbarType currentToolbarType = ToolbarType.ECONOMICS;
    String voltageUnitSymbol = "$"; // Custom voltage unit symbol ($ for economics default)
    String timeUnitSymbol = "yr"; // Custom time unit symbol (yr for economics default)
	int infoViewerUpdateIntervalMs = 100; // InfoViewer live update throttling interval
    boolean useWeightedPriority = false; // Weighted priority for Asset/Equity columns
    String modelInfoContent = null; // Markdown info content from @info block in SFCR files
	String modelInfoSourceText = null; // Full SFCR source for editing in InfoViewer
	private SFCRDocumentState sfcrDocumentState = new SFCRDocumentState();
    MenuItem viewModelInfoItem; // Menu item for viewing model info
	MenuItem helpViewModelInfoItem; // Help menu item for viewing model info
    String currentCircuitFile = null; // Current circuit file name and location for display

	SFCRDocumentState getSFCRDocumentState() {
	return sfcrDocumentState;
	}

	String getModelInfoEditorContent() {
	    if (modelInfoSourceText != null && !modelInfoSourceText.isEmpty()) {
		return modelInfoSourceText;
	    }
	    return modelInfoContent;
	}

    private Label powerLabel;
    private Label titleLabel;
    private Scrollbar speedBar;
    private Scrollbar currentBar;
    private Scrollbar powerBar;
    MenuBar elmMenuBar;
	MenuBar helpMenuBar;
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
    MenuItem elmFlipXMenuItem, elmFlipYMenuItem, elmFlipXYMenuItem;
    MenuItem elmSwapMenuItem;
    MenuItem stackAllItem;
    MenuItem unstackAllItem;
    MenuItem combineAllItem;
    MenuItem separateAllItem;
    MenuBar mainMenuBar;
    boolean hideMenu = false;
    MenuBar selectScopeMenuBar;
    Vector<MenuItem> selectScopeMenuItems;
    MenuBar subcircuitMenuBar[];
    MenuItem scopeRemovePlotMenuItem;
    MenuItem scopeSelectYMenuItem;
    ScopePopupMenu scopePopupMenu;
    Element sidePanelCheckboxLabel;
   
    String lastCursorStyle;
    boolean mouseWasOverSplitter = false;

    // Class addingClass;
    PopupPanel contextPanel = null;
    int mouseMode = MODE_SELECT;
    int tempMouseMode = MODE_SELECT;
    String mouseModeStr = "Select";
    // Mathematical constants
    static final double pi = 3.14159265358979323846;
    
    // Mouse interaction modes - determine how mouse events are interpreted
    static final int MODE_ADD_ELM = 0;        // Adding new circuit element
    static final int MODE_DRAG_ALL = 1;       // Dragging entire circuit
    static final int MODE_DRAG_ROW = 2;       // Dragging table row
    static final int MODE_DRAG_COLUMN = 3;    // Dragging table column
    static final int MODE_DRAG_SELECTED = 4;  // Dragging selected elements
    static final int MODE_DRAG_POST = 5;      // Dragging element terminal/post
    static final int MODE_SELECT = 6;         // Selecting elements (default)
    static final int MODE_DRAG_SPLITTER = 7;  // Dragging scope panel splitter
    
    // UI layout constants
    static final int infoWidth = 200;         // Width of info panel in pixels
    
    int dragGridX, dragGridY, dragScreenX, dragScreenY, initDragGridX, initDragGridY;
    long mouseDownTime;
    long zoomTime;
    int mouseCursorX = -1;
    int mouseCursorY = -1;
    Rectangle selectedArea;
    int gridSize, gridMask, gridRound;
    boolean dragging;
    boolean analyzeFlag, needsStamp, savedFlag;
    boolean dumpMatrix;
    boolean needsRecoverySave;  // Defer recovery save until drag completes
    boolean dcAnalysisFlag;
    // boolean useBufferedImage;
    boolean isMac;
    String ctrlMetaKey;
    
    // Simulation time control
    double t;                      // Current simulation time (seconds)
    long realTimeStart;            // Real wall-clock time when simulation started (ms)
    int pause = 10;                // Milliseconds between frames (lower = faster)
    
    // Scope and menu selection
    int scopeSelected = -1;        // Currently selected scope panel index
    int scopeMenuSelected = -1;    // Scope selected via menu
    int menuScope = -1;            // Scope for context menu
    int menuPlot = -1;             // Plot for context menu
    
    // Hint system (shows helpful formulas)
    int hintType = -1;             // Type of hint to display (HINT_LC, HINT_RC, etc.)
    int hintItem1, hintItem2;      // Elements involved in hint
    
    // Error/stop handling
    String stopMessage;            // Error message when simulation stops
	String warningMessage;         // Non-fatal warning shown in bottom-left status area
    
    // Timestep control
    double timeStep;               // Current timestep (time between iterations)
    double maxTimeStep;            // Maximum timestep (reduced when convergence is difficult)
    double minTimeStep;            // Minimum allowed timestep
    double timeStepAccum;          // Accumulated time since timeStepCount increment
    int timeStepCount;             // Counter incremented each maxTimeStep advance
    
    // Mouse wheel sensitivity
    double wheelSensitivity = 1;
    
    // Frame rate control
    double minFrameRate = 20;      // Target minimum frame rate (FPS)
	// Adaptive timestep control - reduces timestep when convergence is difficult
	boolean adjustTimeStep = false;
	
	// Convergence check threshold - subiterations before marking element as non-converged
	int convergenceCheckThreshold = 100;
	
	// Developer mode - shows additional debug info (framerate, steprate, performance metrics)
	boolean developerMode = false;
	
	// Equation table MNA mode - when true, equation tables create electrical outputs
	boolean equationTableMnaMode = true;

	// Experimental: Newton Jacobian stamping for EquationTable VOLTAGE_MODE MNA rows.
	boolean equationTableNewtonJacobianEnabled = false;

	// Global base convergence tolerance used by all EquationTableElm instances
	double equationTableConvergenceTolerance = 0.001;

	// Default SFCR lookup behavior: true=clamped endpoints (pwl), false=extrapolating (pwlx)
	boolean sfcrLookupClampDefault = true;

	// When true, include the electronics circuit library in the Circuits menu
	boolean showElectronicsCircuits = false;

	// When true, append timestamp query parameter when loading setup lists/circuits
	boolean enableCacheBustedUrls = true;

	// Global toggle for offscreen table blit caching
	boolean tableRenderCacheEnabled = true;

	// When true, auto-open model info viewer after loading SFCR with info content
	boolean autoOpenModelInfoOnLoad = true;
	
	// Circuit hint types - show helpful formulas when related elements are present
	static final int HINT_LC = 1;      // LC resonant frequency hint
	static final int HINT_RC = 2;      // RC time constant hint
	static final int HINT_3DB_C = 3;   // RC cutoff frequency hint (capacitor)
    static final int HINT_TWINT = 4;   // Twin-T notch filter hint
    static final int HINT_3DB_L = 5;   // RL cutoff frequency hint (inductor)
    // Circuit element storage
    Vector<CircuitElm> elmList;           // Dynamic list of all circuit elements
    Vector<Adjustable> adjustables;       // Elements with adjustable sliders
    
    // Cached arrays for performance - avoid type checks in simulation loop
    CircuitElm elmArr[];                  // Cached array copy of elmList
    ScopeElm scopeElmArr[];               // Cached array of scope elements only
    
    // Element references for UI interaction
    CircuitElm dragElm;                   // Element currently being dragged
    CircuitElm menuElm;                   // Element with context menu open
    CircuitElm stopElm;                   // Element that caused simulation to stop
    private CircuitElm mouseElm = null;
    private TableElm lastInteractedTable = null; // Track last table clicked for draw order
    boolean didSwitch = false;
    int mousePost = -1;
    
    // MNA node number currently highlighted (from hovering a LabeledNodeElm), or -1
    int highlightedNode = -1;
    CircuitElm plotXElm, plotYElm;
    int draggingPost;
    SwitchElm heldSwitchElm;
    // Modified Nodal Analysis (MNA) matrix data structures
    // The circuit is solved by: circuitMatrix × nodeVoltages = circuitRightSide
    double circuitMatrix[][];             // [A] Admittance/conductance matrix (after simplification)
    double circuitRightSide[];            // [B] Known values (current sources, voltage sources)
    double nodeVoltages[];                // [X] Solution vector (node voltages + voltage source currents)
    double lastNodeVoltages[];            // Previous solution for convergence checking

    // Circuit-global value array for E_GSLOT fast expression evaluation path.
    // Filled once per subiteration (after applySolvedRightSide + commitPendingToCurrentValues).
    // Indexed by slot number stored inside each E_GSLOT Expr node.
    double[] circuitVariables;           // Flat value array: node voltages + computed values
    String[] slotNames;                  // Parallel name array: slotNames[i] is the name for circuitVariables[i]
    java.util.HashMap<String, Integer> nameToSlot; // name → circuitVariables[] slot index (used at analysis time only)
    double origMatrix[][];                // Original matrix before simplification
    double origRightSide[];               // Original right side before simplification
    RowInfo circuitRowInfo[];             // Metadata for each matrix row (optimization info)
    int circuitPermute[];                 // Row permutation for LU decomposition
    
    // Circuit state flags
    boolean simRunning;                   // True when simulation is actively running
    boolean simRunningBeforeDrag;         // Saved state: was simulation running before drag started?
    boolean circuitNonLinear;             // True if circuit has nonlinear elements (diodes, transistors)
    boolean circuitNeedsMap;              // True if matrix simplification created row mapping
    
    // Circuit dimensions
    int voltageSourceCount;               // Number of voltage sources (adds rows to matrix)
    int circuitMatrixSize;                // Size of matrix after simplification
    int circuitMatrixFullSize;            // Size of matrix before simplification
    // public boolean useFrame;
    int scopeCount;
    Scope scopes[];
    boolean showResistanceInVoltageSources;
    boolean hideInfoBox;
    int scopeColCount[];
    boolean isExporting; // flag to indicate we're exporting an image
    static EditDialog editDialog, customLogicEditDialog, diodeModelEditDialog;
    static ScrollValuePopup scrollValuePopup;
    static Dialog dialogShowing;
    static AboutBox aboutBox;
    // Class dumpTypes[], shortcuts[];
    String shortcuts[];
    String clipboard;
    String recovery;
    Rectangle circuitArea;
    Vector<UndoItem> undoStack, redoStack;
    double transform[];
    boolean unsavedChanges;
    HashMap<String, String> classToLabelMap;
    Toolbar toolbar;

    DockLayoutPanel layoutPanel;
    MenuBar menuBar;
    MenuBar drawMenuBar;
    MenuBar fileMenuBar;
    VerticalPanel verticalPanel;
    CellPanel buttonPanel;
    private boolean mouseDragging;
    double scopeHeightFraction = 0.2;
    boolean scopePanelMinimized = false;
    double normalScopeHeightFraction = 0.2;
    static final int SCOPE_MIN_MAX_BUTTON_SIZE = 24;

    Vector<CheckboxMenuItem> mainMenuItems = new Vector<CheckboxMenuItem>();
    Vector<String> mainMenuItemNames = new Vector<String>();
    
    // Menu definition loaded from menulist.txt
    String menuDefinition = null;
    boolean menuDefinitionLoaded = false;

    LoadFile loadFileInput;
    Frame iFrame;

    Canvas cv;
    Context2d cvcontext;

    // canvas width/height in px (before device pixel ratio scaling)
    int canvasWidth, canvasHeight;

    static final int MENUBARHEIGHT = 30;
    static final int TOOLBARHEIGHT = 40;
    static int VERTICALPANELWIDTH = 166; // default
    static final int POSTGRABSQ = 25;
    static final int MINPOSTGRABSIZE = 256;
    final Timer timer = new Timer() {
        public void run() {
            updateCircuit();
        }
    };
    final int FASTTIMER = 16;

    int getrand(int x) {
        int q = random.nextInt();
        if (q < 0)
            q = -q;
        return q % x;
    }

	static float devicePixelRatio() {
		double ratio = GlobalWindowLike.getDevicePixelRatio();
		return (float) (ratio > 0 ? ratio : 1.0);
	}

    void checkCanvasSize() {
	viewportController.checkCanvasSize();
    }

    boolean isMobile(Element element) {
	if (element == null)
	    return false;
	StyleLike style = getComputedStyle(element);
	return style != null && !"none".equals(style.getDisplay());
    }
    
    void setCircuitArea() {
	viewportController.setCircuitArea();
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

	ScopeManager getScopeManager() {
	    return scopeManager;
	}

	ViewportController getViewportController() {
	    return viewportController;
	}

	CirSimPreferencesManager getPreferencesManager() {
	    return preferencesManager;
	}

	CirSimUiPanelManager getUiPanelManager() {
	    return uiPanelManager;
	}

	InfoDialogActions getInfoDialogActions() {
	    return infoDialogActions;
	}

	EditDialogActions getEditDialogActions() {
	    return editDialogActions;
	}

	CircuitIOService getCircuitIOService() {
	    return circuitIOService;
	}

	ExportCompositeActions getExportCompositeActions() {
	    return exportCompositeActions;
	}

	ImportExportHelper getImportExportHelper() {
	    return importExportHelper;
	}

	CircuitValueSlotManager getCircuitValueSlotManager() {
	    return circuitValueSlotManager;
	}

	CirSimCommandRouter getCommandRouter() {
	    return commandRouter;
	}

	CirSimMenuBuilder getMenuBuilder() {
	    return menuBuilder;
	}

	TableMasterRegistryManager getTableMasterRegistryManager() {
	    return tableMasterRegistryManager;
	}

	CirSimBootstrap getBootstrap() {
	    return bootstrap;
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

	ClipboardManager getClipboardManager() {
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

	SimulationLoop getSimulationLoop() {
	    return simulationLoop;
	}

	JsApiBridge getJsApiBridge() {
	    return jsApiBridge;
	}

	CirSimPlatformInterop getPlatformInterop() {
	    return platformInterop;
	}

	UndoRedoManager getUndoRedoManager() {
	    return undoRedoManager;
	}

    void launchRunnerFromQuery(QueryParameters qp) {
	runnerController.launchFromQuery(qp);
    }

    void onRunnerLoadFileSuccess(String text, Command successCallback) {
	getCircuitIOService().readCircuit(text, RC_KEEP_TITLE);
	unsavedChanges = false;
	if (successCallback != null)
	    successCallback.execute();
    }

    void loadFileFromURLRunner(String url, final Command successCallback, final Command failureCallback) {
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
	Command failureCommand = (failureCallback == null) ? null : new Command() {
	    public void execute() {
		failureCallback.run();
	    }
	};
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
    
    boolean shown = false;
    
    void composeSubcircuitMenu() {
	menuBuilder.composeSubcircuitMenu();
    }
    

    CheckboxMenuItem getClassCheckItem(String s, String t) {
	return menuBuilder.getClassCheckItem(s, t);
    }
    
    
    // get circuit bounds.  remember this doesn't use setBbox().  That is calculated when we draw
    // the circuit, but this needs to be ready before we first draw it, so we use this crude method
    Rectangle getCircuitBounds() {
    	int i;
    	int minx = 30000, maxx = -30000, miny = 30000, maxy = -30000;
    	for (i = 0; i != elmList.size(); i++) {
    		CircuitElm ce = getElm(i);
    		// centered text causes problems when trying to center the circuit,
    		// so we special-case it here
    		if (!ce.isCenteredText()) {
    			minx = min(ce.x, min(ce.x2, minx));
    			maxx = max(ce.x, max(ce.x2, maxx));
    		}
    		miny = min(ce.y, min(ce.y2, miny));
    		maxy = max(ce.y, max(ce.y2, maxy));
    	}
    	if (minx > maxx)
    	    return null;
    	return new Rectangle(minx, miny, maxx-minx, maxy-miny);
    }

    long lastTime = 0, lastFrameTime, lastIterTime, secTime = 0;
    int frames = 0;
    int steps = 0;
    int framerate = 0, steprate = 0;
    static CirSim theSim;
    
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
    void updateRunStopButton() {
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
            simRunning = true;
            updateCircuit();
            simRunning = false;
            repaint();
        }
    }
    
    public boolean simIsRunning() {
    	return simRunning;
    }
    
    boolean needsRepaint;
    
    void repaint() {
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
     *    - lu_factor(): Decompose matrix (for linear circuits only)
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

    /**
     * Draw hint tooltip for the currently hovered element.
     * This is called after all elements are drawn so tooltips appear on top.
     */
    void drawHintTooltip(Graphics g) {
		statusInfoRenderer.drawHintTooltip(g);
    }

    void drawActionSchedulerMessage(Graphics g, Context2d context) {
		statusInfoRenderer.drawActionSchedulerMessage(g, context);
    }
    
    void drawBottomArea(Graphics g) {
	statusInfoRenderer.drawBottomArea(g);
    }
    
    Color getBackgroundColor() {
	return statusInfoRenderer.getBackgroundColor();
    }

    /**
     * Detect collisions where EquationTable PARAM names match physical LabeledNode
     * names. These collisions can change name-resolution behavior in MNA mode.
     */
	void updateEquationParameterCollisionWarning() {
	statusInfoRenderer.updateEquationParameterCollisionWarning();
    }
    
    int oldScopeCount = -1;
    
    boolean scopeMenuIsSelected(Scope s) {
	if (scopeMenuSelected < 0)
	    return false;
	if (scopeMenuSelected < scopeCount)
	    return scopes[scopeMenuSelected] == s;
	return scopeManager.getNthScopeElm(scopeMenuSelected-scopeCount).elmScope == s; 
    }
    
    String getHint() {
	return statusInfoRenderer.getHint();
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
    
    void needAnalyze() {
	analyzeFlag = true;
    	repaint();
	if (RuntimeMode.isGwt())
	    enableDisableMenuItems();
    }
    
    Vector<CircuitNode> nodeList;
    Vector<Point> postDrawList = new Vector<Point>();
    Vector<Point> badConnectionList = new Vector<Point>();
    CircuitElm voltageSources[];

    public CircuitNode getCircuitNode(int n) {
	if (n >= nodeList.size())
	    return null;
	return nodeList.elementAt(n);
    }

    public CircuitElm getElm(int n) {
	if (n >= elmList.size())
	    return null;
	return elmList.elementAt(n);
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

    void alertOrWarn(String message) {
	if (RuntimeMode.isGwt())
	    Window.alert(message);
	else
	    console("WARNING: " + message);
    }

	public static void debugger() {
	}
    
    class NodeMapEntry {
	int node;
	NodeMapEntry() { node = -1; }
	NodeMapEntry(int n) { node = n; }
    }
    // map points to node numbers
    HashMap<Point,NodeMapEntry> nodeMap;
    
    class WireInfo {
	CircuitElm wire;
	Vector<CircuitElm> neighbors;
	int post;
	WireInfo(CircuitElm w) {
	    wire = w;
	}
    }
    
    // info about each wire and its neighbors, used to calculate wire currents
    Vector<WireInfo> wireInfoList;
    
    /**
     * Calculate wire closure - group connected wire equivalents to same node.
     * 
     * PERFORMANCE OPTIMIZATION: This dramatically speeds up simulation by reducing
     * matrix size. Without this, each wire adds 2+ rows to the matrix.
     * 
     * Groups the following into single nodes:
     * - Wire elements (direct connections)
     * - LabeledNodeElm with matching labels (virtual wires)
     * - GroundElm elements (all ground nodes merge to node 0)
     * 
     * ALGORITHM:
     * - Build nodeMap: Point → NodeMapEntry (shared for connected points)
     * - Merge entries when wires connect different node groups
     * - Result: All connected points map to same NodeMapEntry
     * 
     * Note: Actual node numbers assigned later in makeNodeList()
     */
    void calculateWireClosure() {
	circuitAnalyzer.calculateWireClosure();
    }
    
    /**
     * Generate wire info for current calculation.
     * 
     * PROBLEM: Wire elements have same voltage at both terminals, so we can't
     * use voltage differences to calculate current (like resistors do).
     * 
     * OLD SOLUTION: Treat wires as zero-voltage sources → adds 2 matrix rows per wire
     * 
     * NEW SOLUTION: Calculate wire current from neighbor currents instead.
     * By Kirchhoff's Current Law (KCL): wire current = -sum of neighbor currents
     * 
     * This method builds WireInfo objects containing:
     * - wire: The wire element
     * - post: Which terminal (0 or 1) to use for calculation
     * - neighbors: List of elements connected to that terminal
     * 
     * DEPENDENCY ORDERING: Wires are reordered so each wire's neighbors are
     * processed before it. This ensures all neighbor currents are available.
     * If circular dependency detected → error (wire loop)
     * 
     * @return true if successful, false if wire loop detected
     */
    boolean calcWireInfo() {
	return circuitAnalyzer.calcWireInfo();
    }

    // find or allocate ground node
    void setGroundNode(boolean subcircuit) {
	circuitAnalyzer.setGroundNode(subcircuit);
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

    // make list of nodes
    void makeNodeList() {
	circuitAnalyzer.makeNodeList();
    }
    
    Vector<Integer> unconnectedNodes;
    Vector<CircuitElm> nodesWithGroundConnection;
    int nodesWithGroundConnectionCount;
    
    void findUnconnectedNodes() {
	circuitAnalyzer.findUnconnectedNodes();
    }
    
    // take list of unconnected nodes, which we identified earlier, and connect them to ground
    // with a big resistor.  otherwise we will get matrix errors.  The resistor has to be big,
    // otherwise circuits like 555 Square Wave will break
    void connectUnconnectedNodes() {
	circuitAnalyzer.connectUnconnectedNodes();
    }
    
    boolean validateCircuit() {
	return circuitAnalyzer.validateCircuit();
    }
    
    // analyze the circuit when something changes, so it can be simulated.
    // Most of this has been moved to preStampCircuit() so it can be avoided if the simulation is stopped.
    void analyzeCircuit() {
	circuitAnalyzer.analyzeCircuit();
    }

    // do the rest of the pre-stamp circuit analysis
    boolean preStampCircuit(boolean subcircuit) {
	return circuitAnalyzer.preStampCircuit(subcircuit);
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

    // simplify the matrix; this speeds things up quite a bit, especially for digital circuits.
    // or at least it did before we added wire removal
    boolean simplifyMatrix(int matrixSize) {
	return circuitAnalyzer.simplifyMatrix(matrixSize);
    }
    
    /**
     * Build list of circuit posts (connection points) that need to be drawn.
     * 
     * DRAWING RULES:
     * - Posts shared by exactly 2 elements: HIDDEN (clean connection)
     * - Posts with 1 or 3+ connections: VISIBLE (junction indicator)
     * - Posts with 1 connection inside another element's bbox: BAD CONNECTION (red dot)
     * 
     * Note: TableElm posts are always hidden but remain electrically functional.
     * 
     * We can't use node list for this because wires have same node at both ends.
     */
    void makePostDrawList() {
	circuitAnalyzer.makePostDrawList();
    }

    /**
     * State object to find paths in circuit for validation.
     * 
     * Used to detect problematic circuit configurations:
     * - INDUCT: Find current path for inductors (needs path without current sources)
     * - VOLTAGE: Find voltage source loops (voltage sources + wires only)
     * - SHORT: Find shorted capacitors (wires only)
     * - CAP_V: Find capacitor/voltage loops (ideal caps + voltage sources + wires)
     * 
     * Uses depth-first search from source node to destination node,
     * respecting connection rules based on path type.
     */
    class FindPathInfo {
	static final int INDUCT  = 1;
	static final int VOLTAGE = 2;
	static final int SHORT   = 3;
	static final int CAP_V   = 4;
	boolean visited[];
	int dest;
	CircuitElm firstElm;
	int type;

	// State object to help find loops in circuit subject to various conditions (depending on type_)
	// elm_ = source and destination element.  dest_ = destination node.
	FindPathInfo(int type_, CircuitElm elm_, int dest_) {
	    dest = dest_;
	    type = type_;
	    firstElm = elm_;
	    visited  = new boolean[nodeList.size()];
	}

	// look through circuit for loop starting at node n1 of firstElm, for a path back to
	// dest node of firstElm
	boolean findPath(int n1) {
	    if (n1 == dest)
		return true;

	    // depth first search, don't need to revisit already visited nodes!
	    if (visited[n1])
		return false;

	    visited[n1] = true;
	    CircuitNode cn = getCircuitNode(n1);
	    int i;
	    if (cn == null)
		return false;
	    for (i = 0; i != cn.links.size(); i++) {
		CircuitNodeLink cnl = cn.links.get(i);
		CircuitElm ce = cnl.elm;
		if (checkElm(n1, ce))
		    return true;
	    }
	    if (n1 == 0) {
		for (i = 0; i != nodesWithGroundConnection.size(); i++)
		    if (checkElm(0, nodesWithGroundConnection.get(i)))
			return true;
	    }
	    return false;
	}
	
	boolean checkElm(int n1, CircuitElm ce) {
		if (ce == firstElm)
		    return false;
		if (type == INDUCT) {
		    // inductors need a path free of current sources
		    if (ce instanceof CurrentElm)
			return false;
		}
		if (type == VOLTAGE) {
		    // when checking for voltage loops, we only care about voltage sources/wires/ground
		    if (!(ce.isWireEquivalent() || ce instanceof VoltageElm || ce instanceof GroundElm))
			return false;
		}
		// when checking for shorts, just check wires
		if (type == SHORT && !ce.isWireEquivalent())
		    return false;
		if (type == CAP_V) {
		    // checking for capacitor/voltage source loops
		    if (!(ce.isWireEquivalent() || ce.isIdealCapacitor() || ce instanceof VoltageElm))
			return false;
		}
		if (n1 == 0) {
		    // look for posts which have a ground connection;
		    // our path can go through ground
		    int j;
		    for (j = 0; j != ce.getConnectionNodeCount(); j++)
			if (ce.hasGroundConnection(j) && findPath(ce.getConnectionNode(j)))
			    return true;
		}
		int j;
		for (j = 0; j != ce.getConnectionNodeCount(); j++) {
		    if (ce.getConnectionNode(j) == n1) {
			if (ce.hasGroundConnection(j) && findPath(0))
			    return true;
			if (type == INDUCT && ce instanceof InductorElm) {
			    // inductors can use paths with other inductors of matching current
			    double c = ce.getCurrent();
			    if (j == 0)
				c = -c;
			    if (Math.abs(c-firstElm.getCurrent()) > 1e-10)
				continue;
			}
			int k;
			for (k = 0; k != ce.getConnectionNodeCount(); k++) {
			    if (j == k)
				continue;
			    if (ce.getConnection(j, k) && findPath(ce.getConnectionNode(k))) {
				//System.out.println("got findpath " + n1);
				return true;
			    }
			}
		    }
		}
	    return false;
	}
    }

    void stop(String s, CircuitElm ce) {
	stopMessage = Locale.LS(s);
	circuitMatrix = null;  // causes an exception
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
    void stampVCVS(int n1, int n2, double coef, int vs) {
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
    void stampVoltageSource(int n1, int n2, int vs, double v) {
	matrixStamper.stampVoltageSource(n1, n2, vs, v);
    }

    // use this if the amount of voltage is going to be updated in doStep(), by updateVoltageSource()
    void stampVoltageSource(int n1, int n2, int vs) {
	matrixStamper.stampVoltageSource(n1, n2, vs);
    }
    
    // update voltage source in doStep()
    void updateVoltageSource(int n1, int n2, int vs, double v) {
	matrixStamper.updateVoltageSource(n1, n2, vs, v);
    }
    
    void stampResistor(int n1, int n2, double r) {
	matrixStamper.stampResistor(n1, n2, r);
    }

    void stampConductance(int n1, int n2, double r0) {
	matrixStamper.stampConductance(n1, n2, r0);
    }

    // specify that current from cn1 to cn2 is equal to voltage from vn1 to 2, divided by g
    void stampVCCurrentSource(int cn1, int cn2, int vn1, int vn2, double g) {
	matrixStamper.stampVCCurrentSource(cn1, cn2, vn1, vn2, g);
    }

    void stampCurrentSource(int n1, int n2, double i) {
	matrixStamper.stampCurrentSource(n1, n2, i);
    }

    // stamp a current source from n1 to n2 depending on current through vs
    void stampCCCS(int n1, int n2, int vs, double gain) {
	matrixStamper.stampCCCS(n1, n2, vs, gain);
    }

    // stamp value x in row i, column j, meaning that a voltage change
    // of dv in node j will increase the current into node i by x dv.
    // (Unless i or j is a voltage source node.)
    void stampMatrix(int i, int j, double x) {
	matrixStamper.stampMatrix(i, j, x);
    }

    // stamp value x on the right side of row i, representing an
    // independent current source flowing into node i
    void stampRightSide(int i, double x) {
	matrixStamper.stampRightSide(i, x);
    }

    // indicate that the value on the right side of row i changes in doStep()
    void stampRightSide(int i) {
	matrixStamper.stampRightSide(i);
    }
    
    // indicate that the values on the left side of row i change in doStep()
    void stampNonLinear(int i) {
	matrixStamper.stampNonLinear(i);
    }
    
    // Get information about what element/node is associated with a matrix row
    // Used for debugging singular matrix errors
    // The 'row' parameter is the simplified matrix row; we need to map back to original
    String getMatrixRowInfo(int row) {
	return matrixStamper.getMatrixRowInfo(row);
    }

    double getIterCount() {
	return simulationLoop.getIterCount();
    }

    // we need to calculate wire currents for every iteration if someone is viewing a wire in the
    // scope.  Otherwise we can do it only once per frame.
    boolean canDelayWireProcessing() {
	return simulationLoop.canDelayWireProcessing();
    }
    
    boolean converged;
    int subIterations;
    int periodicInterval = 100; // process every 100 timesteps
	int nextPeriodicTime = 0;

    void runCircuit(boolean didAnalyze) {
	simulationLoop.runCircuit(didAnalyze);
	}

    // set node voltages given right side found by solving matrix
    void applySolvedRightSide(double rs[]) {
	simulationLoop.applySolvedRightSide(rs);
    }
    
    // set node voltages in each element given an array of node voltages
    void setNodeVoltages(double nv[]) {
	simulationLoop.setNodeVoltages(nv);
    }
    
    // we removed wires from the matrix to speed things up.  in order to display wire currents,
    // we need to calculate them now.
    void calcWireCurrents() {
	simulationLoop.calcWireCurrents();
    }
    
    int min(int a, int b) { return (a < b) ? a : b; }
    int max(int a, int b) { return (a > b) ? a : b; }
    
    public void resetAction(){
    	int i;
    	analyzeFlag = true;
    	if (t == 0)
    	    setSimRunning(true);
    	t = timeStepAccum = 0;
    	timeStepCount = 0;
    	realTimeStart = System.currentTimeMillis();
    	
    	// Clear computed values before resetting elements to prevent stale values
    	ComputedValues.clearComputedValues();
    	
    	// Clear master table registrations to ensure clean state
    	ComputedValues.clearMasterTables();
		    	
    	// Clear node voltages to ensure clean start
    	if (nodeVoltages != null) {
    	    for (i = 0; i < nodeVoltages.length; i++) {
    	        nodeVoltages[i] = 0.0;
    	    }
    	}
    	if (lastNodeVoltages != null) {
    	    for (i = 0; i < lastNodeVoltages.length; i++) {
    	        lastNodeVoltages[i] = 0.0;
    	    }
    	}
 
    	
    	for (i = 0; i != elmList.size(); i++)
		getElm(i).reset();
	for (i = 0; i != scopeCount; i++)
		scopes[i].resetGraph(true);
	
	// Reset action scheduler
	ActionScheduler scheduler = ActionScheduler.getInstance(this);
	scheduler.reset();
	
    	repaint();
    }

    void onScenarioActivated(boolean resetPlots, boolean openPlotlyViewer) {
	if (resetPlots) {
	    for (int i = 0; i != scopeCount; i++)
		scopes[i].resetGraph(true);
	}
	if (openPlotlyViewer) {
	    new ScopeViewerDialog(this, null, true);
	}
    }
    
    /**
     * Open the Math Elements Test Dialog
     */
    void openMathTestDialog() {
	infoDialogActions.openMathTestDialog();
    }
    
    /**
     * Open the table elements test dialog
     */
    void openTableTestDialog() {
	infoDialogActions.openTableTestDialog();
    }

	void openMathTestDialogCore() {
	if (mathTestDialog == null) {
	    mathTestDialog = new MathElementsTestDialog();
	}
	mathTestDialog.show();
	}

	void openTableTestDialogCore() {
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

	void doImageToClipboardCore() {
	Canvas cv = CirSim.theSim.getExportCompositeActions().getCircuitAsCanvas(CAC_IMAGE);
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

	void getSetupList(final boolean openDefault) {
	    setupListLoader.getSetupList(openDefault);
	}

	void addDialogsMenu() {
	    setupListLoader.addDialogsMenu();
	}

	void loadSetupListIntoMenu(final String setupListPath, final MenuBar circuitsMenu,
			final boolean openDefault, final boolean loadElectronicsAfter) {
	    setupListLoader.loadSetupListIntoMenu(setupListPath, circuitsMenu, openDefault, loadElectronicsAfter);
	}
		
	void processSetupList(byte b[], final boolean openDefault, MenuBar circuitsMenu, String circuitPrefix) {
	    setupListLoader.processSetupList(b, openDefault, circuitsMenu, circuitPrefix);
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
	maxTimeStep = timeStep = new Double (st.nextToken()).doubleValue();
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
	    minTimeStep = Double.parseDouble(st.nextToken());
	} catch (Exception e) {
	}
	preferencesManager.setGrid();
    }
    
    int snapGrid(int x) {
	return (x+gridRound) & gridMask;
    }

    int locateElm(CircuitElm elm) {
	int i;
	for (i = 0; i != elmList.size(); i++)
	    if (elm == elmList.elementAt(i))
		return i;
	return -1;
    }

    void doSplit(CircuitElm ce) {
	int x = snapGrid(inverseTransformX(menuX));
	int y = snapGrid(inverseTransformY(menuY));
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
	elmList.addElement(newWire);
	needAnalyze();
    }
    
    void enableDisableMenuItems() {
	boolean canFlipX = true;
	boolean canFlipY = true;
	boolean canFlipXY = true;
	int selCount = clipboardManager.countSelected();
	for (CircuitElm elm : elmList)
	    if (elm.isSelected() || selCount == 0) {
		if (!elm.canFlipX())
		    canFlipX = false;
		if (!elm.canFlipY())
		    canFlipY = false;
		if (!elm.canFlipXY())
		    canFlipXY = false;
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
    	if (ce!=mouseElm) {
    		if (mouseElm!=null)
    			mouseElm.setMouseElm(false);
    		if (ce!=null)
    			ce.setMouseElm(true);
    		mouseElm=ce;
    		int i;
    		for (i = 0; i < adjustables.size(); i++)
    		    adjustables.get(i).setMouseElm(ce);
    		
			// Track highlighted MNA node for cross-element highlighting.
			// Use labelList node when available, then fall back to physical nodes[0].
    		if (ce instanceof LabeledNodeElm) {
    		    Integer labelNode = LabeledNodeElm.getByName(((LabeledNodeElm) ce).getName());
    		    highlightedNode = (labelNode != null) ? labelNode : ce.nodes[0];
    		} else
    		    highlightedNode = -1;
    	}
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
    	needAnalyze();
    }
    
    /**
     * Draws the minimize/maximize button at the 0.1 fraction line (fixed position).
     */
    void drawScopeMinMaxButton(Graphics g) {
	scopeManager.drawScopeMinMaxButton(g);
    }
    
    /**
     * Toggles the scope panel between minimized and normal height.
     * Uses the same minimum height (0.1) as the splitter dragging constraint.
     */
    void toggleScopePanelSize() {
	scopeManager.toggleScopePanelSize();
    }
    
    /**
     * Convert screen coordinates to circuit grid coordinates.
     * Inverts the circuit transform (zoom and pan).
     * 
     * @param x Screen X coordinate (pixels from left edge)
     * @return Grid X coordinate in circuit space
     */
    int inverseTransformX(double x) {
	return (int) ((x-transform[4])/transform[0]);
    }

    /**
     * Convert screen coordinates to circuit grid coordinates.
     * Inverts the circuit transform (zoom and pan).
     * 
     * @param y Screen Y coordinate (pixels from top edge)
     * @return Grid Y coordinate in circuit space
     */
    int inverseTransformY(double y) {
	return (int) ((y-transform[5])/transform[3]);
    }
    
    /**
     * Convert circuit grid coordinates to screen coordinates.
     * Applies circuit transform (zoom and pan).
     * 
     * @param x Grid X coordinate in circuit space
     * @return Screen X coordinate (pixels)
     */
    int transformX(double x) {
	return (int) ((x*transform[0]) + transform[4]);
    }
    
    /**
     * Convert circuit grid coordinates to screen coordinates.
     * Applies circuit transform (zoom and pan).
     * 
     * @param y Grid Y coordinate in circuit space
     * @return Screen Y coordinate (pixels)
     */
    int transformY(double y) {
	return (int) ((y*transform[3]) + transform[5]);
    }
    
    int menuClientX, menuClientY;
    int menuX, menuY;
    
    static int lastSubcircuitMenuUpdate;
    
	    static final int MAX_NORMALIZED_WHEEL_DELTA = 150;

	    // Modern browsers can report large pixel deltas from high-resolution
	    // trackpads. Clamp to keep zoom/edit interactions consistent.
	    public static int normalizeWheelDelta(int deltaY) {
		if (deltaY > MAX_NORMALIZED_WHEEL_DELTA)
		    return MAX_NORMALIZED_WHEEL_DELTA;
		if (deltaY < -MAX_NORMALIZED_WHEEL_DELTA)
		    return -MAX_NORMALIZED_WHEEL_DELTA;
		return deltaY;
	    }

    void setCircuitScale(double newScale, boolean menu) {
	viewportController.setCircuitScale(newScale, menu);
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
    	if (mouseElm!=null && !dialogIsShowing() && scopeSelected == -1)
    		if (mouseElm instanceof ResistorElm || mouseElm instanceof CapacitorElm ||  mouseElm instanceof InductorElm) {
    			scrollValuePopup = new ScrollValuePopup(x, y, deltay, mouseElm, this);
    		}
    }
    
    void enableItems() {
    }
    
    void setToolbar() {
	layoutPanel.setWidgetHidden(toolbar, !toolbarCheckItem.getState());
	getViewportController().setCanvasSize();
    }
    
    void loadUndoItem(UndoItem ui) {
	getCircuitIOService().readCircuit(ui.dump, RC_NO_CENTER);
	transform[0] = transform[3] = ui.scale;
	transform[4] = ui.transform4;
	transform[5] = ui.transform5;
    }

    /**
     * Write circuit state to browser localStorage for crash recovery.
     * 
     * PERFORMANCE NOTE: This is relatively expensive for large circuits:
     * - dumpCircuit() serializes entire circuit to string (~1-10ms)
     * - localStorage.setItem() writes to disk (~1-5ms)
     * 
     * Should be called sparingly:
     * - After drag completes (not during every mouse move)
     * - After adding/deleting elements
     * - After major circuit changes
     * 
     * Previously called on every mouse move during drag, causing lag.
     * Now deferred via needsRecoverySave flag until drag completes.
     */
    void writeRecoveryToStorage() {
	console("write recovery");
    	Storage stor = Storage.getLocalStorageIfSupported();
    	if (stor == null)
    		return;
		String s = getCircuitIOService().dumpCircuit();
    	stor.setItem("circuitRecovery", s);
    }

    void readRecovery() {
	Storage stor = Storage.getLocalStorageIfSupported();
	if (stor == null)
		return;
	recovery = stor.getItem("circuitRecovery");
    }
	CircuitElm getMouseElmForRouting() {
	return mouseElm;
	}

	boolean isMouseDraggingForRouting() {
	return mouseDragging;
	}

	void setMouseDraggingForRouting(boolean value) {
	mouseDragging = value;
	}

	void setLastInteractedTableForRouting(TableElm table) {
	lastInteractedTable = table;
	}

	TableElm getLastInteractedTableForRouting() {
	return lastInteractedTable;
	}

	int getCurrentBarValueForRouting() {
	return currentBar.getValue();
	}

	int getPowerBarValueForRouting() {
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
    	if (editDialog!=null && editDialog.isShowing())
    		return true;
        if (customLogicEditDialog!=null && customLogicEditDialog.isShowing())
                return true;
        if (diodeModelEditDialog!=null && diodeModelEditDialog.isShowing())
                return true;
       	if (dialogShowing != null && dialogShowing.isShowing())
       		return true;
    	if (contextPanel!=null && contextPanel.isShowing())
    		return true;
    	if (scrollValuePopup != null && scrollValuePopup.isShowing())
    		return true;
    	if (aboutBox !=null && aboutBox.isShowing())
    		return true;
    	if (VariableBrowserDialog.isOpen())
    		return true;
    	if (ActionTimeDialog.isOpen())
    		return true;
    	return false;
    }
    
    void updateToolbar() {
	toolbar.setModeLabel(classToLabelMap.get(mouseModeStr));
	toolbar.highlightButton(mouseModeStr);
    }

    String getLabelTextForClass(String cls) {
	return classToLabelMap.get(cls);
    }

    // factors a matrix into upper and lower triangular matrices by
    // gaussian elimination.  On entry, a[0..n-1][0..n-1] is the
    // matrix to be factored.  ipvt[] returns an integer vector of pivot
    // indices, used in the lu_solve() routine.
    // Returns -1 on success, or the problematic row index on failure (singular matrix)
    static int lu_factor(double a[][], int n, int ipvt[]) {
	int badRow = LUSolver.factor(a, n, ipvt);
	if (badRow >= 0) {
	    console("didn't avoid zero at row " + badRow);
	    console("  Non-zero entries in column " + badRow + ":");
	    for (int dbg = 0; dbg < n; dbg++) {
		if (a[dbg][badRow] != 0.0) {
		    console("    row " + dbg + ": " + a[dbg][badRow]);
		}
	    }
	    console("  Non-zero entries in row " + badRow + ":");
	    for (int dbg = 0; dbg < n; dbg++) {
		if (a[badRow][dbg] != 0.0) {
		    console("    col " + dbg + ": " + a[badRow][dbg]);
		}
	    }
	}
	return badRow;
    }

    // Solves the set of n linear equations using a LU factorization
    // previously performed by lu_factor.  On input, b[0..n-1] is the right
    // hand side of the equations, and on output, contains the solution.
    static void lu_solve(double a[][], int n, int ipvt[], double b[]) {
	LUSolver.solve(a, n, ipvt, b);
    }

    
    public static CircuitElm createCe(int tint, int x1, int y1, int x2, int y2, int f, StringTokenizer st) {
	CircuitElm registryElement = ElementRegistry.createFromDumpType(tint, x1, y1, x2, y2, f, st);
	if (registryElement != null)
	    return registryElement;
	return createCeLegacy(tint, x1, y1, x2, y2, f, st);
    }

    static CircuitElm createCeLegacy(int tint, int x1, int y1, int x2, int y2, int f, StringTokenizer st) {
	return ElementLegacyFactory.createCeLegacy(tint, x1, y1, x2, y2, f, st);
    }

    public static CircuitElm constructElement(String n, int x1, int y1){
	ElementRegistry.NameLookupResult lookupResult = ElementRegistry.createFromClassKey(n, x1, y1);
	if (lookupResult != null) {
	    if (lookupResult.entry != null && lookupResult.entry.alias && lookupResult.entry.deprecationMessage != null) {
		console(lookupResult.entry.deprecationMessage);
	    }
	    return lookupResult.element;
	}
	return constructElementLegacy(n, x1, y1);
	}

	static CircuitElm constructElementLegacy(String n, int x1, int y1){
	return ElementLegacyFactory.constructElementLegacy(n, x1, y1);
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
	static final int CAC_IMAGE = 1;
	static final int CAC_SVG   = 2;
	
	// create SVG context using canvas2svg
	static Context2d createSVGContext(int w, int h) {
	    return createC2SContext(w, h).cast();
	}
	
	static String getSerializedSVG(Context2d context) {
	    return ((SvgContextLike) (Object) context).getSerializedSvg();
	}
	
	
	static void invertMatrix(double a[][], int n) {
	    int ipvt[] = new int[n];
	    lu_factor(a, n, ipvt);
	    int i, j;
	    double b[] = new double[n];
	    double inva[][] = new double[n][n];
	    
	    // solve for each column of identity matrix
	    for (i = 0; i != n; i++) {
		for (j = 0; j != n; j++)
		    b[j] = 0;
		b[i] = 1;
		lu_solve(a, n, ipvt, b);
		for (j = 0; j != n; j++)
		    inva[j][i] = b[j];
	    }
	    
	    // return in original matrix
	    for (i = 0; i != n; i++)
		for (j = 0; j != n; j++)
		    a[i][j] = inva[i][j];
	}
	
    // -----------------------------------------------------------------------
    // E_GSLOT support: circuit-global variable array
    // -----------------------------------------------------------------------

	/**
	 * Get list of all slider names in the circuit
	 * @return Array of slider names
	 */
	JsArrayString getJSArrayString() {
	    return JavaScriptObject.createArray().cast();
	}

	void setExprPerfProbeEnabled(boolean enabled) {
	    Expr.setPerfProbeEnabled(enabled);
	}

	void resetExprPerfProbe() {
	    Expr.resetPerfProbe();
	}

	String getExprPerfProbeReport() {
	    return Expr.getPerfProbeReport();
	}
	
	// ========== END LABELED NODE & COMPUTED VALUE API METHODS ==========

	JsArray<JavaScriptObject> getJSArray() {
	    return jsApiBridge.getJSArray();
	}
	
	JsArray<JavaScriptObject> getJSElements() {
	    return jsApiBridge.getJSElements();
	}
	
	void setupJSInterface() {
	    jsApiBridge.setupJSInterface();
	}
	
	void callUpdateHook() {
	    jsApiBridge.callUpdateHook();
	}
	
	void callAnalyzeHook() {
	    jsApiBridge.callAnalyzeHook();
	}
    

	void callTimeStepHook() {
	    jsApiBridge.callTimeStepHook();
	}
	
	void callSVGRenderedHook(String svgData) {
		jsApiBridge.callSVGRenderedHook(svgData);
	}

	class UndoItem {
	    public String dump;
	    public double scale, transform4, transform5;
	    UndoItem(String d) {
		dump = d;
		scale = transform[0];
		transform4 = transform[4];
		transform5 = transform[5];
	    }
	}

}

