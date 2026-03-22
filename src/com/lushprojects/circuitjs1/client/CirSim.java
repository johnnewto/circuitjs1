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
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.ContextMenuEvent;
import com.google.gwt.event.dom.client.ContextMenuHandler;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.event.dom.client.MouseWheelEvent;
import com.google.gwt.event.dom.client.MouseWheelHandler;
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
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
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
public class CirSim implements MouseDownHandler, MouseMoveHandler, MouseUpHandler,
ClickHandler, DoubleClickHandler, ContextMenuHandler, NativePreviewHandler,
MouseOutHandler, MouseWheelHandler {

	@JsFunction
	private interface SaveDialogSuccessCallback {
		Object onSuccess(SaveDialogResult result);
	}

	@JsFunction
	private interface SaveDialogFailureCallback {
		Object onFailure(Object error);
	}

	@JsFunction
	private interface OpenFileCallback {
		void onOpen(String text, String name);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	private static class StyleLike {
		@JsProperty(name = "display") native String getDisplay();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	private static class SaveDialogResult {
		@JsProperty(name = "canceled") native boolean isCanceled();
		@JsProperty(name = "filePath") native Object getFilePath();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Promise")
	private static class PromiseLike {
		@JsMethod(name = "then") native void then(SaveDialogSuccessCallback success, SaveDialogFailureCallback failure);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "window")
	private static class GlobalWindowLike {
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
	private static class NavigatorLike {
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
	private static native PromiseLike showSaveDialog();

	@JsMethod(namespace = JsPackage.GLOBAL, name = "saveFile")
	private static native void saveFile(Object file, String dump);

	@JsMethod(namespace = JsPackage.GLOBAL, name = "openFile")
	private static native void openFile(OpenFileCallback callback);

	@JsMethod(namespace = JsPackage.GLOBAL, name = "toggleDevTools")
	private static native void toggleDevToolsNative();

	@JsMethod(namespace = JsPackage.GLOBAL, name = "C2S")
	private static native JavaScriptObject createC2SContext(int w, int h);

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	private static class SvgContextLike {
		@JsMethod(name = "getSerializedSvg") native String getSerializedSvg();
	}

	@JsFunction
	private interface TouchEventHandler {
		void handle(TouchEventLike event);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "DOMRect")
	private static class DomRectLike {
		@JsProperty(name = "left") native double getLeft();
		@JsProperty(name = "top") native double getTop();
		@JsProperty(name = "y") native double getY();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Touch")
	private static class TouchLike {
		@JsProperty(name = "clientX") native double getClientX();
		@JsProperty(name = "clientY") native double getClientY();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "TouchList")
	private static class TouchListLike {
		@JsProperty(name = "length") native int getLength();
		@JsMethod(name = "item") native TouchLike item(int index);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "TouchEvent")
	private static class TouchEventLike {
		@JsProperty(name = "touches") native TouchListLike getTouches();
		@JsProperty(name = "timeStamp") native double getTimeStamp();
		@JsMethod native void preventDefault();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	private static class MouseEventInitLike {
		@JsProperty(name = "clientX") native void setClientX(double x);
		@JsProperty(name = "clientY") native void setClientY(double y);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "MouseEvent")
	private static class MouseEventLike {
		public MouseEventLike(String type, MouseEventInitLike init) {}
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "HTMLCanvasElement")
	private static class CanvasElementLike {
		@JsMethod(name = "addEventListener") native void addEventListener(String type, TouchEventHandler handler, boolean useCapture);
		@JsMethod(name = "dispatchEvent") native void dispatchEvent(MouseEventLike event);
		@JsMethod(name = "getBoundingClientRect") native DomRectLike getBoundingClientRect();
	}

	@JsMethod(namespace = JsPackage.GLOBAL, name = "Object")
	private static native MouseEventInitLike newMouseEventInit();

	@JsFunction
	private interface OnCircuitLoadedHook {
		void call(CircuitJsApi api);
	}

	@JsFunction
	private interface Hook0 {
		void call();
	}

	@JsFunction
	private interface HookBool {
		void call(boolean value);
	}

	@JsFunction
	private interface HookDouble {
		void call(double value);
	}

	@JsFunction
	private interface HookStringBool {
		void call(String value, boolean flag);
	}

	@JsFunction
	private interface HookStringDouble {
		Object call(String value, double n);
	}

	@JsFunction
	private interface HookStringToDouble {
		double call(String value);
	}

	@JsFunction
	private interface HookStringToString {
		String call(String value);
	}

	@JsFunction
	private interface HookNoArgDouble {
		double call();
	}

	@JsFunction
	private interface HookNoArgBoolean {
		boolean call();
	}

	@JsFunction
	private interface HookNoArgString {
		String call();
	}

	@JsFunction
	private interface HookNoArgArrayString {
		JsArrayString call();
	}

	@JsFunction
	private interface HookNoArgElements {
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
	private interface ApiHook {
		void call(CircuitJsApi api);
	}

	@JsFunction
	private interface SvgHook {
		void call(CircuitJsApi api, String svgData);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	private static class CircuitJsApi {
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
        if (cv.getCoordinateSpaceWidth() != (int) (canvasWidth * devicePixelRatio()))
            setCanvasSize();
    }

    boolean isMobile(Element element) {
	if (element == null)
	    return false;
	StyleLike style = getComputedStyle(element);
	return style != null && !"none".equals(style.getDisplay());
    }
    
    public void setCanvasSize(){
    	int width, height;
    	width=(int)RootLayoutPanel.get().getOffsetWidth();
    	height=(int)RootLayoutPanel.get().getOffsetHeight();
    	height=height-(hideMenu?0:MENUBARHEIGHT);

    	//not needed on mobile since the width of the canvas' container div is set to 100% in ths CSS file
    	if (!isMobile(sidePanelCheckboxLabel))
    	    width=width-VERTICALPANELWIDTH;
	if (toolbarCheckItem.getState())
	    height -= TOOLBARHEIGHT;

    	width = Math.max(width, 0);   // avoid exception when setting negative width
    	height = Math.max(height, 0);
    	
		if (cv != null) {
			cv.setWidth(width + "PX");
			cv.setHeight(height + "PX");
			canvasWidth = width;
			canvasHeight = height;
			float scale = devicePixelRatio();
			cv.setCoordinateSpaceWidth((int)(width*scale));
			cv.setCoordinateSpaceHeight((int)(height*scale));
		}

    	setCircuitArea();

	// recenter circuit in case canvas was hidden at startup
    	if (transform[0] == 0)
    	    centreCircuit();
    }
    
    void setCircuitArea() {
    	int height = canvasHeight;
    	int width = canvasWidth;
    	int h = (int) ((double)height * scopeHeightFraction);
    	/*if (h < 128 && winSize.height > 300)
		  h = 128;*/
    	if (scopeCount == 0)
    	    h = 0;
    	circuitArea = new Rectangle(0, 0, width, height-h);
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
	private final CirSimBootstrap bootstrap = new CirSimBootstrap(this);
	private final CirSimDiagnostics diagnostics = new CirSimDiagnostics(this);

    public void initRunner() {
	bootstrap.initRunner();
    }

    public void initRunnerPanel(QueryParameters qp) {
	bootstrap.initRunnerPanel(qp);
    }

    void launchRunnerFromQuery(QueryParameters qp) {
	runnerController.launchFromQuery(qp);
    }

    void onRunnerLoadFileSuccess(String text, Command successCallback) {
	readCircuit(text, RC_KEEP_TITLE);
	unsavedChanges = false;
	if (successCallback != null)
	    successCallback.execute();
    }

    void loadFileFromURLRunner(String url, final Command successCallback, final Command failureCallback) {
	final String loadUrl = getLoadUrl(url);
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
	    loadFileFromURL(url, successCallback, failureCallback);
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
    
    public void init() {

	//sets the meta tag to allow the css media queries to work
	MetaElement meta = Document.get().createMetaElement();
	meta.setName("viewport");
	meta.setContent("width=device-width");
	NodeList<com.google.gwt.dom.client.Element> node = Document.get().getElementsByTagName("head");
	node.getItem(0).appendChild(meta);

	
	boolean printable = false;
	boolean convention = true;
	boolean euroRes = false;
	boolean usRes = false;
	boolean running = true;
	boolean hideSidebar = false;
	boolean noEditing = false;
	boolean mouseWheelEdit = false;
	MenuBar m;

	CircuitElm.initClass(this);
	readRecovery();

	QueryParameters qp = new QueryParameters();
	String positiveColor = null;
	String negativeColor = null;
	String neutralColor = null;
	String selectColor = null;
	String currentColor = null;
	String mouseModeReq = null;
	boolean euroGates = false;

	try {
	    //baseURL = applet.getDocumentBase().getFile();
	    // look for circuit embedded in URL
	    //		String doc = applet.getDocumentBase().toString();
	    String cct=qp.getValue("cct");
	    if (cct!=null)
		startCircuitText = cct.replace("%24", "$");
	    if (startCircuitText == null)
		startCircuitText = getElectronStartCircuitText();
	    String ctz=qp.getValue("ctz");
	    if (ctz!= null)
		startCircuitText = decompress(ctz);
	    String nonInteractiveDumpKey = qp.getValue("nonInteractiveDumpKey");
	    if (startCircuitText == null && nonInteractiveDumpKey != null)
		startCircuitText = getRunnerDumpFromStorage(nonInteractiveDumpKey);
	    startCircuit = qp.getValue("startCircuit");
	    startLabel   = qp.getValue("startLabel");
	    startCircuitLink = qp.getValue("startCircuitLink");
	    euroRes = qp.getBooleanValue("euroResistors", false);
	    euroGates = qp.getBooleanValue("IECGates", getOptionFromStorage("euroGates", weAreInGermany()));
	    usRes = qp.getBooleanValue("usResistors",  false);
	    running = qp.getBooleanValue("running", true);
	    hideSidebar = qp.getBooleanValue("hideSidebar", false);
	    hideMenu = qp.getBooleanValue("hideMenu", false);
	    printable = qp.getBooleanValue("whiteBackground", getOptionFromStorage("whiteBackground", true));
	    convention = qp.getBooleanValue("conventionalCurrent",
		    getOptionFromStorage("conventionalCurrent", true));
	    noEditing = !qp.getBooleanValue("editable", true);
	    mouseWheelEdit = qp.getBooleanValue("mouseWheelEdit", getOptionFromStorage("mouseWheelEdit", true));
	    useWeightedPriority = getOptionFromStorage("weightedPriority", false);
	    showElectronicsCircuits = getOptionFromStorage("showElectronicsCircuits", false);
	    enableCacheBustedUrls = getOptionFromStorage("enableCacheBustedUrls", true);
	    tableRenderCacheEnabled = getOptionFromStorage("tableRenderCacheEnabled", true);
	    autoOpenModelInfoOnLoad = getOptionFromStorage("autoOpenModelInfoOnLoad", true);
	    equationTableNewtonJacobianEnabled = getOptionFromStorage("equationTableNewtonJacobianEnabled", false);
	    positiveColor = qp.getValue("positiveColor");
	    negativeColor = qp.getValue("negativeColor");
	    neutralColor = qp.getValue("neutralColor");
	    selectColor = qp.getValue("selectColor");
	    currentColor = qp.getValue("currentColor");
	    mouseModeReq = qp.getValue("mouseMode");
	    hideInfoBox = qp.getBooleanValue("hideInfoBox", false);
	} catch (Exception e) { }

	boolean euroSetting = false;
	if (euroRes)
	    euroSetting = true;
	else if (usRes)
	    euroSetting = false;
	else
	    euroSetting = getOptionFromStorage("euroResistors", !weAreInUS(true));

	transform = new double[6];
	String os = Navigator.getPlatform();
	isMac = (os.toLowerCase().contains("mac"));
	ctrlMetaKey = (isMac) ? Locale.LS("Cmd-") : Locale.LS("Ctrl-");

	shortcuts = new String[127];

	layoutPanel = new DockLayoutPanel(Unit.PX);

	fileMenuBar = new MenuBar(true);
	if (isElectron())
	    fileMenuBar.addItem(menuItemWithShortcut("window", "New Window...", Locale.LS(ctrlMetaKey + "N"),
		    new MyCommand("file", "newwindow")));
	
	fileMenuBar.addItem(iconMenuItem("doc-new", "New Blank Circuit", new MyCommand("file", "newblankcircuit")));
	importFromLocalFileItem = menuItemWithShortcut("folder", "Open File...", Locale.LS(ctrlMetaKey + "O"),
		new MyCommand("file","importfromlocalfile"));
	importFromLocalFileItem.setEnabled(LoadFile.isSupported());
	fileMenuBar.addItem(importFromLocalFileItem);
	importFromTextItem = iconMenuItem("doc-text", "Import From Text...", new MyCommand("file","importfromtext"));
	fileMenuBar.addItem(importFromTextItem);
	importFromDropboxItem = iconMenuItem("dropbox", "Import From Dropbox...", new MyCommand("file", "importfromdropbox"));
	fileMenuBar.addItem(importFromDropboxItem);
	if (isElectron()) {
	    saveFileItem = fileMenuBar.addItem(menuItemWithShortcut("floppy", "Save", Locale.LS(ctrlMetaKey + "S"),
		    new MyCommand("file", "save")));
	    fileMenuBar.addItem(iconMenuItem("floppy", "Save As...", new MyCommand("file", "saveas")));
	} else {
	    exportAsLocalFileItem = menuItemWithShortcut("floppy", "Save As...", Locale.LS(ctrlMetaKey + "S"),
		    new MyCommand("file","exportaslocalfile"));
	    exportAsLocalFileItem.setEnabled(ExportAsLocalFileDialog.downloadIsSupported());
	    fileMenuBar.addItem(exportAsLocalFileItem);
	}
	exportAsUrlItem = iconMenuItem("export", "Export As Link...", new MyCommand("file","exportasurl"));
	fileMenuBar.addItem(exportAsUrlItem);
	fileMenuBar.addItem(iconMenuItem("line-chart", "Open Runner Output Table...", new MyCommand("file", "openrunnertable")));
	exportAsTextItem = iconMenuItem("export", "Export As Text...", new MyCommand("file","exportastext"));
	fileMenuBar.addItem(exportAsTextItem);
	fileMenuBar.addItem(iconMenuItem("export", "Export As SFCR...", new MyCommand("file","exportassfcr")));
	editLookupTablesItem = iconMenuItem("table", "Edit Lookup Tables...", new MyCommand("file", "editlookuptables"));
	fileMenuBar.addItem(editLookupTablesItem);
	viewModelInfoItem = iconMenuItem("doc-text", "View Model Info...", new MyCommand("file","viewmodelinfo"));
	viewModelInfoItem.setEnabled(false); // Enabled when info content is available
	fileMenuBar.addItem(viewModelInfoItem);
	fileMenuBar.addItem(iconMenuItem("image", "Export As Image...", new MyCommand("file","exportasimage")));
	fileMenuBar.addItem(iconMenuItem("image", "Copy Circuit Image to Clipboard", new MyCommand("file","copypng")));
	fileMenuBar.addItem(iconMenuItem("image", "Export As SVG...", new MyCommand("file","exportassvg")));    	
	fileMenuBar.addItem(iconMenuItem("microchip", "Create Subcircuit...", new MyCommand("file","createsubcircuit")));
	fileMenuBar.addItem(iconMenuItem("magic", "Find DC Operating Point", new MyCommand("file", "dcanalysis")));
	recoverItem = iconMenuItem("back-in-time", "Recover Auto-Save", new MyCommand("file","recover"));
	recoverItem.setEnabled(recovery != null);
	fileMenuBar.addItem(recoverItem);
	printItem = menuItemWithShortcut("print", "Print...", Locale.LS(ctrlMetaKey + "P"), new MyCommand("file","print"));
	fileMenuBar.addItem(printItem);
	fileMenuBar.addSeparator();
	fileMenuBar.addItem(iconMenuItem("resize-full-alt", "Toggle Full Screen", new MyCommand("view", "fullscreen")));
	fileMenuBar.addSeparator();
	aboutItem = iconMenuItem("info-circled", "About...", (Command)null);
	fileMenuBar.addItem(aboutItem);
	aboutItem.setScheduledCommand(new MyCommand("file","about"));

	int width=(int)RootLayoutPanel.get().getOffsetWidth();
	VERTICALPANELWIDTH = width/5;
	if (VERTICALPANELWIDTH > 166)
	    VERTICALPANELWIDTH = 166;
	if (VERTICALPANELWIDTH < 128)
	    VERTICALPANELWIDTH = 128;

	menuBar = new MenuBar();
	menuBar.addItem(Locale.LS("File"), fileMenuBar);
	verticalPanel=new VerticalPanel();

	verticalPanel.getElement().addClassName("verticalPanel");
	verticalPanel.getElement().setId("painel");
	InputElement sidePanelCheckbox = Document.get().createCheckInputElement();
	sidePanelCheckboxLabel = Document.get().createLabelElement();
	sidePanelCheckboxLabel.addClassName("triggerLabel");
	sidePanelCheckbox.setId("trigger");
	sidePanelCheckboxLabel.setAttribute("for", "trigger" );
	sidePanelCheckbox.addClassName("trigger");
	InputElement topPanelCheckbox = Document.get().createCheckInputElement(); 
	LabelElement topPanelCheckboxLabel = Document.get().createLabelElement();
	topPanelCheckbox.setId("toptrigger");
	topPanelCheckbox.addClassName("toptrigger");
	topPanelCheckboxLabel.addClassName("toptriggerlabel");
	topPanelCheckboxLabel.setAttribute("for", "toptrigger");

	// make buttons side by side if there's room
	buttonPanel=(VERTICALPANELWIDTH == 166) ? new HorizontalPanel() : new VerticalPanel();

	m = new MenuBar(true);
	m.addItem(undoItem = menuItemWithShortcut("ccw", "Undo", Locale.LS(ctrlMetaKey + "Z"), new MyCommand("edit","undo")));
	m.addItem(redoItem = menuItemWithShortcut("cw", "Redo", Locale.LS(ctrlMetaKey + "Y"), new MyCommand("edit","redo")));
	m.addSeparator();
	m.addItem(cutItem = menuItemWithShortcut("scissors", "Cut", Locale.LS(ctrlMetaKey + "X"), new MyCommand("edit","cut")));
	m.addItem(copyItem = menuItemWithShortcut("copy", "Copy", Locale.LS(ctrlMetaKey + "C"), new MyCommand("edit","copy")));
	m.addItem(pasteItem = menuItemWithShortcut("paste", "Paste", Locale.LS(ctrlMetaKey + "V"), new MyCommand("edit","paste")));
	pasteItem.setEnabled(false);

	m.addItem(menuItemWithShortcut("clone", "Duplicate", Locale.LS(ctrlMetaKey + "D"), new MyCommand("edit","duplicate")));

	m.addSeparator();
	m.addItem(selectAllItem = menuItemWithShortcut("select-all", "Select All", Locale.LS(ctrlMetaKey + "A"), new MyCommand("edit","selectAll")));
	m.addSeparator();
	m.addItem(menuItemWithShortcut("search", "Find Component...", "/", new MyCommand("edit", "search")));
	m.addItem(iconMenuItem("target", weAreInUS(false) ? "Center Circuit" : "Centre Circuit", new MyCommand("edit", "centrecircuit")));
	// m.addItem(menuItemWithShortcut("target", "Center Circuit", "c", new MyCommand("edit", "centrecircuit")));
	m.addItem(menuItemWithShortcut("zoom-11", "Zoom 100%", "0", new MyCommand("zoom", "zoom100")));
	m.addItem(menuItemWithShortcut("zoom-in", "Zoom In", "+", new MyCommand("zoom", "zoomin")));
	m.addItem(menuItemWithShortcut("zoom-out", "Zoom Out", "-", new MyCommand("zoom", "zoomout")));
	m.addItem(flipXItem = iconMenuItem("flip-x", "Flip X", new MyCommand("edit", "flipx")));
	m.addItem(flipYItem = iconMenuItem("flip-y", "Flip Y", new MyCommand("edit", "flipy")));
	m.addItem(flipXYItem = iconMenuItem("flip-x-y", "Flip XY", new MyCommand("edit", "flipxy")));
	menuBar.addItem(Locale.LS("Edit"),m);

	drawMenuBar = new MenuBar(true);
	drawMenuBar.setAutoOpen(true);

	menuBar.addItem(Locale.LS("Draw"), drawMenuBar);

	m = new MenuBar(true);
	m.addItem(stackAllItem = iconMenuItem("lines", "Stack All", new MyCommand("scopes", "stackAll")));
	m.addItem(unstackAllItem = iconMenuItem("columns", "Unstack All", new MyCommand("scopes", "unstackAll")));
	m.addItem(combineAllItem = iconMenuItem("object-group", "Combine All", new MyCommand("scopes", "combineAll")));
	m.addItem(separateAllItem = iconMenuItem("object-ungroup", "Separate All", new MyCommand("scopes", "separateAll")));
	m.addSeparator();
	m.addItem(iconMenuItem("line-chart", "View All Scopes in Plotly...", new MyCommand("scopes", "viewAllPlotly")));
	menuBar.addItem(Locale.LS("Scopes"), m);

	optionsMenuBar = m = new MenuBar(true );
	menuBar.addItem(Locale.LS("Options"), optionsMenuBar);
	m.addItem(dotsCheckItem = new CheckboxMenuItem(Locale.LS("Show Current")));
	dotsCheckItem.setState(true);
	m.addItem(voltsCheckItem = new CheckboxMenuItem(Locale.LS("Show Voltage"),
		new Command() { public void execute(){
		    if (voltsCheckItem.getState())
			powerCheckItem.setState(false);
		    setPowerBarEnable();
		}
	}));
	voltsCheckItem.setState(true);
	m.addItem(powerCheckItem = new CheckboxMenuItem(Locale.LS("Show Power"),
		new Command() { public void execute(){
		    if (powerCheckItem.getState())
			voltsCheckItem.setState(false);
		    setPowerBarEnable();
		}
	}));
	m.addItem(showValuesCheckItem = new CheckboxMenuItem(Locale.LS("Show Values")));
	showValuesCheckItem.setState(true);

	helpMenuBar = new MenuBar(true);
	helpViewModelInfoItem = menuItemWithShortcut("doc-text", "View Model Info...", "", new MyCommand("help", "viewmodelinfo"));
	helpViewModelInfoItem.setEnabled(false);
	helpMenuBar.addItem(helpViewModelInfoItem);
	helpMenuBar.addItem(menuItemWithShortcut("folder", "Reference Docs...", "", new MyCommand("help", "referencedocs")));

	//m.add(conductanceCheckItem = getCheckItem(LS("Show Conductance")));
	m.addItem(smallGridCheckItem = new CheckboxMenuItem(Locale.LS("Small Grid"),
		new Command() { public void execute(){
		    setGrid();
		}
	}));
	m.addItem(toolbarCheckItem = new CheckboxMenuItem(Locale.LS("Toolbar"),
		new Command() { public void execute(){
		    setToolbar();
		}
	}));
	toolbarCheckItem.setState(!hideMenu && !noEditing && !hideSidebar);
	
	m.addItem(electronicsModeCheckItem = new CheckboxMenuItem(Locale.LS("Electronics Mode"),
		new Command() { public void execute(){
		    if (!electronicsModeCheckItem.getState()) {
			// Don't allow unchecking - must have one selected
			electronicsModeCheckItem.setState(true);
			return;
		    }
		    switchToElectronicsToolbar();
		}
	}));
	electronicsModeCheckItem.setState(currentToolbarType == ToolbarType.ELECTRONICS);
	
	m.addItem(economicsModeCheckItem = new CheckboxMenuItem(Locale.LS("Economics Mode"),
		new Command() { public void execute(){
		    if (!economicsModeCheckItem.getState()) {
			// Don't allow unchecking - must have one selected
			economicsModeCheckItem.setState(true);
			return;
		    }
		    switchToEconomicsToolbar();
		}
	}));
	economicsModeCheckItem.setState(currentToolbarType == ToolbarType.ECONOMICS);
	m.addItem(crossHairCheckItem = new CheckboxMenuItem(Locale.LS("Show Cursor Cross Hairs"),
		new Command() { public void execute(){
		    setOptionInStorage("crossHair", crossHairCheckItem.getState());
		}
	}));
	crossHairCheckItem.setState(getOptionFromStorage("crossHair", false));
	m.addItem(euroResistorCheckItem = new CheckboxMenuItem(Locale.LS("European Resistors"),
		new Command() { public void execute(){
		    setOptionInStorage("euroResistors", euroResistorCheckItem.getState());
		    toolbar.setEuroResistors(euroResistorCheckItem.getState());
		}
	}));
	euroResistorCheckItem.setState(euroSetting);
	m.addItem(euroGatesCheckItem = new CheckboxMenuItem(Locale.LS("IEC Gates"),
		new Command() { public void execute(){
		    setOptionInStorage("euroGates", euroGatesCheckItem.getState());
		    int i;
		    for (i = 0; i != elmList.size(); i++)
			getElm(i).setPoints();
		}
	}));
	euroGatesCheckItem.setState(euroGates);
	m.addItem(printableCheckItem = new CheckboxMenuItem(Locale.LS("White Background"),
		new Command() { public void execute(){
		    int i;
		    for (i=0;i<scopeCount;i++)
			scopes[i].setRect(scopes[i].rect);
		    setOptionInStorage("whiteBackground", printableCheckItem.getState());
		}
	}));
	printableCheckItem.setState(printable);

	m.addItem(conventionCheckItem = new CheckboxMenuItem(Locale.LS("Conventional Current Motion"),
		new Command() { public void execute(){
		    setOptionInStorage("conventionalCurrent", conventionCheckItem.getState());
		    String cc = CircuitElm.currentColor.getHexValue();
		    // change the current color if it hasn't changed from the default
		    if (cc.equals("#ffff00") || cc.equals("#00ffff"))
			CircuitElm.currentColor = conventionCheckItem.getState() ? Color.yellow : Color.cyan;
		}
	}));
	conventionCheckItem.setState(convention);
	m.addItem(noEditCheckItem = new CheckboxMenuItem(Locale.LS("Disable Editing")));
	noEditCheckItem.setState(noEditing);

	m.addItem(mouseWheelEditCheckItem = new CheckboxMenuItem(Locale.LS("Edit Values With Mouse Wheel"),
		new Command() { public void execute(){
		    setOptionInStorage("mouseWheelEdit", mouseWheelEditCheckItem.getState());
		}
	}));
	mouseWheelEditCheckItem.setState(mouseWheelEdit);

	m.addItem(weightedPriorityCheckItem = new CheckboxMenuItem(Locale.LS("Weighted Priority by Type (Asset/Equity +10)"),
		new Command() { public void execute(){
		    useWeightedPriority = weightedPriorityCheckItem.getState();
		    setOptionInStorage("weightedPriority", useWeightedPriority);
		    // Clear and re-register all masters with new weighted priorities
		    ComputedValues.clearMasterTables();
		    ComputedValues.clearComputedValues();
		    needAnalyze();
		}
	}));
	weightedPriorityCheckItem.setState(useWeightedPriority);

	m.addItem(new CheckboxAlignedMenuItem(Locale.LS("Shortcuts..."), new MyCommand("options", "shortcuts")));
	m.addItem(new CheckboxAlignedMenuItem(Locale.LS("Subcircuits..."), new MyCommand("options", "subcircuits")));
	m.addItem(new CheckboxAlignedMenuItem(Locale.LS("Voltage Unit Symbol..."), new MyCommand("options", "voltageunit")));
	m.addItem(new CheckboxAlignedMenuItem(Locale.LS("Element Registry Inference Report"), new MyCommand("options", "elementregistryreport")));
	m.addItem(optionsItem = new CheckboxAlignedMenuItem(Locale.LS("Other Options..."), new MyCommand("options","other")));
	if (isElectron())
	    m.addItem(new CheckboxAlignedMenuItem(Locale.LS("Toggle Dev Tools"), new MyCommand("options","devtools")));

	mainMenuBar = new MenuBar(true);
	mainMenuBar.setAutoOpen(true);
	composeMainMenu(mainMenuBar, 0);
	composeMainMenu(drawMenuBar, 1);
	loadShortcuts();

	layoutPanel.getElement().appendChild(topPanelCheckbox);
	layoutPanel.getElement().appendChild(topPanelCheckboxLabel);	

	toolbar = new EconomicsToolbar();
	toolbar.setEuroResistors(euroSetting);
	if (!hideMenu)
	    layoutPanel.addNorth(menuBar, MENUBARHEIGHT);

	if (hideSidebar)
	    VERTICALPANELWIDTH = 0;
	else {
		layoutPanel.getElement().appendChild(sidePanelCheckbox);
		layoutPanel.getElement().appendChild(sidePanelCheckboxLabel);
	    layoutPanel.addEast(verticalPanel, VERTICALPANELWIDTH);
	}
	layoutPanel.addNorth(toolbar, TOOLBARHEIGHT);
	menuBar.getElement().insertFirst(menuBar.getElement().getChild(1));
	menuBar.getElement().getFirstChildElement().setAttribute("onclick", "document.getElementsByClassName('toptrigger')[0].checked = false");
	RootLayoutPanel.get().add(layoutPanel);

	cv =Canvas.createIfSupported();
	if (cv==null) {
	    RootPanel.get().add(new Label("Not working. You need a browser that supports the CANVAS element."));
	    return;
	}

	Window.addResizeHandler(new ResizeHandler() {
	    public void onResize(ResizeEvent event) {
		repaint();
	    }
	});

	cvcontext=cv.getContext2d();
	setToolbar(); // calls setCanvasSize()
	layoutPanel.add(cv);
	verticalPanel.add(buttonPanel);
	
	// Create floating control panel with Run/Stop, Reset, Step, Lock and Fullscreen buttons
	floatingControlPanel = new FloatingControlPanel(this);
	
/*
	dumpMatrixButton = new Button("Dump Matrix");
	dumpMatrixButton.addClickHandler(new ClickHandler() {
	    public void onClick(ClickEvent event) { dumpMatrix = true; }});
	verticalPanel.add(dumpMatrixButton);// IES for debugging
*/
	

	if (LoadFile.isSupported())
	    verticalPanel.add(loadFileInput = new LoadFile(this));

	Label l;
	verticalPanel.add(l = new Label(Locale.LS("Simulation Speed")));
	l.addStyleName("topSpace");

	// was max of 140
	verticalPanel.add( speedBar = new Scrollbar(Scrollbar.HORIZONTAL, 3, 1, 0, 260));

	verticalPanel.add( l = new Label(Locale.LS("Current Speed")));
	l.addStyleName("topSpace");
	currentBar = new Scrollbar(Scrollbar.HORIZONTAL, 50, 1, 1, 100);
	verticalPanel.add(currentBar);
	verticalPanel.add(powerLabel = new Label (Locale.LS("Power Brightness")));
	powerLabel.addStyleName("topSpace");
	verticalPanel.add(powerBar = new Scrollbar(Scrollbar.HORIZONTAL,
		50, 1, 1, 100));
	setPowerBarEnable();

	//	verticalPanel.add(new Label(""));
	//        Font f = new Font("SansSerif", 0, 10);
	l = new Label(Locale.LS("Current Circuit:"));
	l.addStyleName("topSpace");
	//        l.setFont(f);
	titleLabel = new Label("Label");
	//        titleLabel.setFont(f);
	verticalPanel.add(l);
	verticalPanel.add(titleLabel);

	verticalPanel.add(iFrame = new Frame("iframe.html"));
	iFrame.setWidth(VERTICALPANELWIDTH+"px");
	iFrame.setHeight("100 px");
	iFrame.getElement().setAttribute("scrolling", "no");

	setGrid();
	elmList = new Vector<CircuitElm>();
	adjustables = new Vector<Adjustable>();
	//	setupList = new Vector();
	undoStack = new Vector<UndoItem>();
	redoStack = new Vector<UndoItem>();


	scopes = new Scope[20];
	scopeColCount = new int[20];
	scopeCount = 0;

	random = new Random();
	//	cv.setBackground(Color.black);
	//	cv.setForeground(Color.lightGray);

	elmMenuBar = new MenuBar(true);
	elmMenuBar.setAutoOpen(true);
	selectScopeMenuBar = new MenuBar(true) {
	    @Override
	    
	    // when mousing over scope menu item, select associated scope
	    public void onBrowserEvent(Event event) {
		int currentItem = -1;
		EventTarget eventTarget = event.getEventTarget();
		Element targetElement = Element.is(eventTarget) ? Element.as(eventTarget) : null;
		int i;
		for (i = 0; i != selectScopeMenuItems.size(); i++) {
		    MenuItem item = selectScopeMenuItems.get(i);
		    if (targetElement != null && item.getElement().isOrHasChild(targetElement)) {
			//MenuItem found here
			currentItem = i;
		    }
		}
		switch (event.getTypeInt()) {
		case Event.ONMOUSEOVER:
		    scopeMenuSelected = currentItem; 
		    break;              
		case Event.ONMOUSEOUT:
		    scopeMenuSelected = -1;
		    break;              
		}
		super.onBrowserEvent(event);
	    }
	};
	
	elmMenuBar.addItem(elmEditMenuItem = new MenuItem(Locale.LS("Edit..."),new MyCommand("elm","edit")));
	elmMenuBar.addItem(elmScopeMenuItem = new MenuItem(Locale.LS("View in New Scope"), new MyCommand("elm","viewInScope")));
	elmMenuBar.addItem(elmFloatScopeMenuItem  = new MenuItem(Locale.LS("View in New Undocked Scope"), new MyCommand("elm","viewInFloatScope")));
	elmMenuBar.addItem(elmAddScopeMenuItem = new MenuItem(Locale.LS("Add to Existing Scope"), new MyCommand("elm", "addToScope0")));
	elmMenuBar.addItem(elmCutMenuItem = new MenuItem(Locale.LS("Cut"),new MyCommand("elm","cut")));
	elmMenuBar.addItem(elmCopyMenuItem = new MenuItem(Locale.LS("Copy"),new MyCommand("elm","copy")));
	elmMenuBar.addItem(elmDeleteMenuItem = new MenuItem(Locale.LS("Delete"),new MyCommand("elm","delete")));
	elmMenuBar.addItem(                    new MenuItem(Locale.LS("Duplicate"),new MyCommand("elm","duplicate")));
	elmMenuBar.addItem(elmSwapMenuItem = new MenuItem(Locale.LS("Swap Terminals"),new MyCommand("elm","flip")));
	elmMenuBar.addItem(elmFlipXMenuItem =  new MenuItem(Locale.LS("Flip X"),new MyCommand("elm","flipx")));
	elmMenuBar.addItem(elmFlipYMenuItem =  new MenuItem(Locale.LS("Flip Y"),new MyCommand("elm","flipy")));
	elmMenuBar.addItem(elmFlipXYMenuItem =  new MenuItem(Locale.LS("Flip XY"),new MyCommand("elm","flipxy")));
	elmMenuBar.addItem(elmSplitMenuItem = menuItemWithShortcut("", "Split Wire", Locale.LS(ctrlMetaKey + "click"), new MyCommand("elm","split")));
	elmMenuBar.addItem(elmSliderMenuItem = new MenuItem(Locale.LS("Sliders..."),new MyCommand("elm","sliders")));
	elmMenuBar.addItem(elmSankeyMenuItem = new MenuItem(Locale.LS("View Sankey Diagram..."),new MyCommand("elm","viewSankey")));
	elmMenuBar.addItem(elmDagBlocksMenuItem = new MenuItem(Locale.LS("View DAG Blocks Plot..."),new MyCommand("elm","viewDagBlocks")));
	elmMenuBar.addItem(elmEquationTableDebugMenuItem = new MenuItem(Locale.LS("View EquationTable Debug Info..."),new MyCommand("elm","viewEquationTableDebug")));
	elmMenuBar.addItem(elmEquationTableReferenceMenuItem = new MenuItem(Locale.LS("View EquationTable Reference..."),new MyCommand("elm","viewEquationTableReference")));

	scopePopupMenu = new ScopePopupMenu();

	setColors(positiveColor, negativeColor, neutralColor, selectColor, currentColor);
	setWheelSensitivity();

	if (startCircuitText != null) {
	    console("Loading embedded circuit from URL");
	    getSetupList(false);
	    readCircuit(startCircuitText);
	    currentCircuitFile = "embedded";
	    unsavedChanges = false;
	} else {
	    if (stopMessage == null && startCircuitLink!=null) {
		readCircuit("");
		getSetupList(false);
		ImportFromDropboxDialog.setSim(this);
		ImportFromDropboxDialog.doImportDropboxLink(startCircuitLink, false);
		// currentCircuitFile set by ImportFromDropboxDialog
	    } else {
		readCircuit("");
		if (stopMessage == null && startCircuit != null) {
		    getSetupList(false);
		    readSetupFile(startCircuit, startLabel);
		    // currentCircuitFile set by readSetupFile
		}
		else
		    getSetupList(true);
	    }
	}

	if (mouseModeReq != null)
	    menuPerformed("main", mouseModeReq);

	enableUndoRedo();
	enablePaste();
	enableDisableMenuItems();
	setiFrameHeight();
	cv.addMouseDownHandler(this);
	cv.addMouseMoveHandler(this);
	cv.addMouseOutHandler(this);
	cv.addMouseUpHandler(this);
	cv.addClickHandler(this);
	cv.addDoubleClickHandler(this);
	doTouchHandlers(this, cv.getCanvasElement());
	cv.addDomHandler(this, ContextMenuEvent.getType());	
	menuBar.addDomHandler(new ClickHandler() {
	    public void onClick(ClickEvent event) {
		doMainMenuChecks();
	    }
	}, ClickEvent.getType());	
	Event.addNativePreviewHandler(this);
	cv.addMouseWheelHandler(this);

	Window.addWindowClosingHandler(new Window.ClosingHandler() {
	    public void onWindowClosing(ClosingEvent event) {
		// there is a bug in electron that makes it impossible to close the app if this warning is given
		if (unsavedChanges && !isElectron())
		    event.setMessage(Locale.LS("Are you sure?  There are unsaved changes."));
	    }
	});
	setupJSInterface();
	
	setSimRunning(running);
	
	// Load menu definition
	loadMenuDefinition();
    }
    
    // Load menu definition from menulist.txt
    void loadMenuDefinition() {
	String url = GWT.getModuleBaseURL() + "menulist.txt";
	RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.GET, getLoadUrl(url));
	try {
	    requestBuilder.sendRequest(null, new RequestCallback() {
		public void onError(Request request, Throwable exception) {
		    console("Warning: Can't load menu definition, using hardcoded menu");
		    GWT.log("Menu definition file error", exception);
		    menuDefinitionLoaded = false;
		}

		public void onResponseReceived(Request request, Response response) {
		    if (response.getStatusCode() == Response.SC_OK) {
			menuDefinition = response.getText();
			menuDefinitionLoaded = true;
			console("Menu definition loaded successfully");
			// Rebuild menus now that definition is loaded
			rebuildMenusFromDefinition();
		    } else {
			console("Warning: Can't load menu definition, using hardcoded menu");
			GWT.log("Bad menu definition response: " + response.getStatusText());
			menuDefinitionLoaded = false;
		    }
		}
	    });
	} catch (RequestException e) {
	    console("Warning: Can't load menu definition, using hardcoded menu");
	    GWT.log("Failed loading menu definition", e);
	    menuDefinitionLoaded = false;
	}
    }
    
    // Rebuild menus after menu definition is loaded
    void rebuildMenusFromDefinition() {
	// Clear existing menu items
	mainMenuBar.clearItems();
	drawMenuBar.clearItems();
	
	// Rebuild both menus with new definition
	composeMainMenu(mainMenuBar, 0);
	composeMainMenu(drawMenuBar, 1);
	
	// Recompose subcircuit menus if needed
	composeSubcircuitMenu();
	
	console("Menus rebuilt from definition");
    }

    void setColors(String positiveColor, String negativeColor, String neutralColor, String selectColor, String currentColor) {
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor != null) {
            if (positiveColor == null)
        	positiveColor = stor.getItem("positiveColor");
            if (negativeColor == null)
        	negativeColor = stor.getItem("negativeColor");
            if (neutralColor == null)
        	neutralColor = stor.getItem("neutralColor");
            if (selectColor == null)
        	selectColor = stor.getItem("selectColor");
            if (currentColor == null)
        	currentColor = stor.getItem("currentColor");
            
            // Load custom voltage unit symbol
            String customUnit = stor.getItem("voltageUnitSymbol");
            if (customUnit != null && !customUnit.isEmpty())
                voltageUnitSymbol = customUnit;
        }
        
	if (positiveColor != null)
	    CircuitElm.positiveColor = new Color(URL.decodeQueryString(positiveColor));
	else if (getOptionFromStorage("alternativeColor", false))
	    CircuitElm.positiveColor = Color.blue;
	
	if (negativeColor != null)
	    CircuitElm.negativeColor = new Color(URL.decodeQueryString(negativeColor));
	if (neutralColor != null)
	    CircuitElm.neutralColor = new Color(URL.decodeQueryString(neutralColor));

	if (selectColor != null)
	    CircuitElm.selectColor = new Color(URL.decodeQueryString(selectColor));
	else
	    CircuitElm.selectColor = Color.cyan;
	// Connected-node highlight: darker cyan to distinguish from direct selection
	CircuitElm.connectedColor = new Color(0, 140, 140);
	
	if (currentColor != null)
	    CircuitElm.currentColor = new Color(URL.decodeQueryString(currentColor));
	else
	    CircuitElm.currentColor = conventionCheckItem.getState() ? Color.yellow : Color.cyan;
	    
	CircuitElm.setColorScale();
    }
    
    void setWheelSensitivity() {
	wheelSensitivity = 1;
	try {
	    Storage stor = Storage.getLocalStorageIfSupported();
	    wheelSensitivity = Double.parseDouble(stor.getItem("wheelSensitivity"));
	    
	    // Load graphics update interval setting
	    String guiStr = stor.getItem("graphicsUpdateInterval");
	    if (guiStr != null) {
		int gui = Integer.parseInt(guiStr);
		if (gui >= 1 && gui <= 10)
		    graphicsUpdateInterval = gui;
	    }

	    String eqTolStr = stor.getItem("equationTableConvergenceTolerance");
	    if (eqTolStr != null) {
		double eqTol = Double.parseDouble(eqTolStr);
		if (eqTol > 0)
		    equationTableConvergenceTolerance = eqTol;
	    }
	} catch (Exception e) {}
    }

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
    
    boolean getOptionFromStorage(String key, boolean val) {
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null)
            return val;
        String s = stor.getItem(key);
        if (s == null)
            return val;
        return s == "true";
    }

    void setOptionInStorage(String key, boolean val) {
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null)
            return;
        stor.setItem(key,  val ? "true" : "false");
    }
    
    void showVoltageUnitDialog() {
        String newSymbol = Window.prompt("Enter voltage unit symbol (e.g., V, $, €, or leave blank for no unit):", voltageUnitSymbol);
        if (newSymbol != null) {
            newSymbol = newSymbol.trim();
            if (newSymbol.isEmpty())
                newSymbol = "V"; // Default back to V if empty
            voltageUnitSymbol = newSymbol;
            Storage stor = Storage.getLocalStorageIfSupported();
            if (stor != null)
                stor.setItem("voltageUnitSymbol", voltageUnitSymbol);
            repaint();
        }
    }
    
    // save shortcuts to local storage
    void saveShortcuts() {
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null)
            return;
        String str = "1";
        int i;
        // format: version;code1=ClassName;code2=ClassName;etc
        for (i = 0; i != shortcuts.length; i++) {
            String sh = shortcuts[i];
            if (sh == null)
        		continue;
            str += ";" + i + "=" + sh;
        }
        stor.setItem("shortcuts", str);
    }
    
    // load shortcuts from local storage
    void loadShortcuts() {
        Storage stor = Storage.getLocalStorageIfSupported();
        if (stor == null)
            return;
        String str = stor.getItem("shortcuts");
        if (str == null)
            return;
        String keys[] = str.split(";");
        
        // clear existing shortcuts
        int i;
        for (i = 0; i != shortcuts.length; i++)
            shortcuts[i] = null;
        
        // clear shortcuts from menu
        for (i = 0; i != mainMenuItems.size(); i++) {
            CheckboxMenuItem item = mainMenuItems.get(i);
            // stop when we get to drag menu items
            if (item.getShortcut().length() > 1)
        		break;
            item.setShortcut("");
        }
        
        // go through keys (skipping version at start)
        for (i = 1; i < keys.length; i++) {
            String arr[] = keys[i].split("=");
            if (arr.length != 2)
        	continue;
            int c = Integer.parseInt(arr[0]);
            String className = arr[1];
            shortcuts[c] = className;
            
            // find menu item and fix it
            int j;
            for (j = 0; j != mainMenuItems.size(); j++) {
        		if (mainMenuItemNames.get(j) == className) {
        		    CheckboxMenuItem item = mainMenuItems.get(j);
        		    item.setShortcut(Character.toString((char)c));
        		    break;
        		}
            }
        }
    }
    
    // install touch handlers
    // don't feel like rewriting this in java.  Anyway, java doesn't let us create mouse
    // events and dispatch them.
    static void doTouchHandlers(final CirSim sim, CanvasElement cv) {
	final CanvasElementLike canvas = (CanvasElementLike) (Object) cv;
	final double[] lastTap = new double[] { 0 };
	final double[] lastScale = new double[] { 1 };
	final Timer[] longPressTimer = new Timer[] { null };

	canvas.addEventListener("touchstart", new TouchEventHandler() {
	    public void handle(TouchEventLike e) {
		TouchListLike touches = e.getTouches();
		if (touches == null || touches.getLength() < 1)
		    return;
		e.preventDefault();
		if (longPressTimer[0] != null)
		    longPressTimer[0].cancel();

		double ts = e.getTimeStamp();
		boolean isDoubleTap = (ts - lastTap[0] < 300);
		if (!isDoubleTap) {
		    longPressTimer[0] = new Timer() {
			public void run() {
			    sim.longPress();
			}
		    };
		    longPressTimer[0].schedule(500);
		}
		lastTap[0] = ts;

		TouchLike touch1 = touches.item(0);
		TouchLike touch2 = touches.item(touches.getLength()-1);
		lastScale[0] = Math.hypot(touch1.getClientX()-touch2.getClientX(), touch1.getClientY()-touch2.getClientY());

		double cx = .5 * (touch1.getClientX() + touch2.getClientX());
		double cy = .5 * (touch1.getClientY() + touch2.getClientY());
		MouseEventInitLike init = newMouseEventInit();
		init.setClientX(cx);
		init.setClientY(cy);
		MouseEventLike mouseEvent = new MouseEventLike(isDoubleTap ? "dblclick" : "mousedown", init);
		canvas.dispatchEvent(mouseEvent);
		if (touches.getLength() > 1)
		    sim.twoFingerTouch((int) cx, (int) (cy - canvas.getBoundingClientRect().getY()));
	    }
	}, false);

	canvas.addEventListener("touchend", new TouchEventHandler() {
	    public void handle(TouchEventLike e) {
		e.preventDefault();
		if (longPressTimer[0] != null)
		    longPressTimer[0].cancel();
		MouseEventInitLike init = newMouseEventInit();
		canvas.dispatchEvent(new MouseEventLike("mouseup", init));
	    }
	}, false);

	canvas.addEventListener("touchmove", new TouchEventHandler() {
	    public void handle(TouchEventLike e) {
		TouchListLike touches = e.getTouches();
		if (touches == null || touches.getLength() < 1)
		    return;
		e.preventDefault();
		if (longPressTimer[0] != null)
		    longPressTimer[0].cancel();

		TouchLike touch1 = touches.item(0);
		TouchLike touch2 = touches.item(touches.getLength()-1);
		if (touches.getLength() > 1) {
		    double newScale = Math.hypot(touch1.getClientX()-touch2.getClientX(), touch1.getClientY()-touch2.getClientY());
		    if (lastScale[0] > 0)
			sim.zoomCircuit(40*(Math.log(newScale)-Math.log(lastScale[0])));
		    lastScale[0] = newScale;
		}

		double cx = .5 * (touch1.getClientX() + touch2.getClientX());
		double cy = .5 * (touch1.getClientY() + touch2.getClientY());
		MouseEventInitLike init = newMouseEventInit();
		init.setClientX(cx);
		init.setClientY(cy);
		canvas.dispatchEvent(new MouseEventLike("mousemove", init));
	    }
	}, false);
    }
    
    boolean shown = false;
    
    // this is called twice, once for the Draw menu, once for the right mouse popup menu
    public void composeMainMenu(MenuBar mainMenuBar, int num) {
	menuBuilder.composeMainMenu(mainMenuBar, num);
    }
    
	// Delegated to CirSimMenuBuilder
    void composeMainMenuFromFile(MenuBar mainMenuBar, int num) {
	menuBuilder.composeMainMenuFromFile(mainMenuBar, num);
    }
    
	// Delegated to CirSimMenuBuilder
    void composeMainMenuHardcoded(MenuBar mainMenuBar, int num) {
	menuBuilder.composeMainMenuHardcoded(mainMenuBar, num);
    }
    
    void composeSubcircuitMenu() {
	if (subcircuitMenuBar == null)
	    return;
	int mi;
	
	// there are two menus to update: the one in the Draw menu, and the one in the right mouse menu
	for (mi = 0; mi != 2; mi++) {
	    MenuBar menu = subcircuitMenuBar[mi];
	    menu.clearItems();
	    Vector<CustomCompositeModel> list = CustomCompositeModel.getModelList();
	    int i;
	    for (i = 0; i != list.size(); i++) {
		String name = list.get(i).name;
		menu.addItem(getClassCheckItem(Locale.LS("Add ") + name, "CustomCompositeElm:" + name));
	    }
	}
	lastSubcircuitMenuUpdate = CustomCompositeModel.sequenceNumber;
    }
    
    public void composeSelectScopeMenu(MenuBar sb) {
	sb.clearItems();
	selectScopeMenuItems = new Vector<MenuItem>();
	for( int i = 0; i < scopeCount; i++) {
	    String s, l;
	    s = Locale.LS("Scope")+" "+ Integer.toString(i+1);
	    l=scopes[i].getScopeMenuName();
	    if (l!="")
		s+=" ("+SafeHtmlUtils.htmlEscape(l)+")";
	    selectScopeMenuItems.add(new MenuItem(s ,new MyCommand("elm", "addToScope"+Integer.toString(i))));
	}
	int c = countScopeElms();
	for (int j = 0; j < c; j++) {
	    String s,l;
	    s = Locale.LS("Undocked Scope")+" "+ Integer.toString(j+1);
	    l = getNthScopeElm(j).elmScope.getScopeMenuName();
	    if (l!="")
		s += " ("+SafeHtmlUtils.htmlEscape(l)+")";
	    selectScopeMenuItems.add(new MenuItem(s, new MyCommand("elm", "addToScope"+Integer.toString(scopeCount+j))));
	}
	for (MenuItem mi : selectScopeMenuItems)
	    sb.addItem(mi);
    }
    
    public void setiFrameHeight() {
    	if (iFrame==null)
    		return;
    	int i;
    	int cumheight=0;
    	for (i=0; i < verticalPanel.getWidgetIndex(iFrame); i++) {
    		if (verticalPanel.getWidget(i) !=loadFileInput) {
    			cumheight=cumheight+verticalPanel.getWidget(i).getOffsetHeight();
    			if (verticalPanel.getWidget(i).getStyleName().contains("topSpace"))
    					cumheight+=12;
    		}
    	}
    	int ih=RootLayoutPanel.get().getOffsetHeight()-(hideMenu?0:MENUBARHEIGHT)-cumheight;
    	if (ih<0)
    		ih=0;
    	iFrame.setHeight(ih+"px");
    }
    


    


    CheckboxMenuItem getClassCheckItem(String s, String t) {
	if (classToLabelMap == null)
	    classToLabelMap = new HashMap<String, String>();
	classToLabelMap.put(t, s);

    	// try {
    	//   Class c = Class.forName(t);
    	String shortcut="";
    	CircuitElm elm = null;
    	try {
    	    elm = constructElement(t, 0, 0);
    	} catch (Exception e) {}
    	CheckboxMenuItem mi;
    	//  register(c, elm);
    	if ( elm!=null ) {
    		if (elm.needsShortcut() ) {
    			shortcut += (char)elm.getShortcut();
    			if (shortcuts[elm.getShortcut()] != null && !shortcuts[elm.getShortcut()].equals(t))
    			    console("already have shortcut for " + (char)elm.getShortcut() + " " + elm);
    			shortcuts[elm.getShortcut()]=t;
    		}
    		elm.delete();
    	}
//    	else
//    		GWT.log("Coudn't create class: "+t);
    	//	} catch (Exception ee) {
    	//	    ee.printStackTrace();
    	//	}
    	if (shortcut=="")
    		mi= new CheckboxMenuItem(s);
    	else
    		mi = new CheckboxMenuItem(s, shortcut);
    	mi.setScheduledCommand(new MyCommand("main", t) );
    	mainMenuItems.add(mi);
    	mainMenuItemNames.add(t);
    	return mi;
    }
    
    

    
    void centreCircuit() {
	viewportController.centreCircuit();
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

	// Delegated to ViewportController
    ViewportElm findViewportElm() {
	return viewportController.findViewportElm();
    }
    
	// Delegated to ViewportController
    void applyViewportTransform(ViewportElm viewport) {
	viewportController.applyViewportTransform(viewport);
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
        if (mouseElm == null) return;
        
        String hint = null;
        String valueStr = null;
        String label = null;
		java.util.ArrayList<String> tooltipLines = new java.util.ArrayList<String>();
        boolean isInScope = false;
        
        // Check if it's a LabeledNodeElm
        if (mouseElm instanceof LabeledNodeElm) {
            LabeledNodeElm lne = (LabeledNodeElm) mouseElm;
            hint = HintRegistry.getHint(lne.text);
            label = lne.text;
            valueStr = CircuitElm.showFormat.format(lne.volts[0]);
        }
        // Check if it's an EquationTableElm with a hovered row
        else if (mouseElm instanceof EquationTableElm) {
            EquationTableElm ete = (EquationTableElm) mouseElm;
            int hoveredRow = ete.getHoveredRow();
            if (hoveredRow >= 0 && hoveredRow < ete.getRowCount()) {
				hint = "Equation";
				String hintExpandedEquation = ete.getHintExpandedEquationForDisplay(hoveredRow);
				if (hintExpandedEquation != null && !hintExpandedEquation.trim().isEmpty()) {
					tooltipLines.add(hintExpandedEquation);
				}
            }
        }
        // Check if it's a ScopeElm (undocked scope) with a selected plot
        else if (mouseElm instanceof ScopeElm) {
            ScopeElm se = (ScopeElm) mouseElm;
            CircuitElm plotElm = se.elmScope.getElm();
            if (plotElm instanceof LabeledNodeElm) {
                LabeledNodeElm lne = (LabeledNodeElm) plotElm;
                hint = HintRegistry.getHint(lne.text);
                label = lne.text;
                isInScope = true;  // Don't show value for scope
            }
        }
        
        // Draw the tooltip if we have one
        if (hint != null && !hint.trim().isEmpty()) {
			if (tooltipLines.isEmpty()) {
				// Build display text: "Hint Description: Label = Value" or just "Hint Description: Label" if in scope area
				String displayText;
				if (scopeSelected != -1 || isInScope) {
					// Mouse is over scope area - don't show value
					displayText = hint + ":   " + label;
				} else {
					// Mouse is over circuit area - show value
					displayText = hint + ":   " + label + " = " + valueStr;
				}
				tooltipLines.add(displayText);
            }
            
            g.context.setFont("500 12px system-ui, -apple-system, sans-serif");
			int hintWidth = 0;
			for (String line : tooltipLines) {
				int lineWidth = (int) g.context.measureText(line).getWidth() + 16;
				if (lineWidth > hintWidth) {
					hintWidth = lineWidth;
				}
			}
			if (hintWidth <= 0) {
				return;
			}

			int lineHeight = 16;
			int verticalPadding = 8;
			int hintHeight = verticalPadding * 2 + tooltipLines.size() * lineHeight;
            int radius = 6;
            
            // Position above the mouse cursor in screen coordinates
            int tooltipX = mouseCursorX - hintWidth / 2;
            int tooltipY = mouseCursorY - hintHeight - 10;
            
            // Keep on screen
            if (tooltipX < 8) tooltipX = 8;
            if (tooltipX + hintWidth > canvasWidth - 8) tooltipX = canvasWidth - hintWidth - 8;
            if (tooltipY < 8) tooltipY = mouseCursorY + 20; // Below cursor if too high
            
            // Draw shadow
            g.context.setShadowColor("rgba(0, 0, 0, 0.25)");
            g.context.setShadowBlur(8);
            g.context.setShadowOffsetX(0);
            g.context.setShadowOffsetY(2);
            
            // Draw rounded rectangle background
            g.context.beginPath();
            g.context.moveTo(tooltipX + radius, tooltipY);
            g.context.lineTo(tooltipX + hintWidth - radius, tooltipY);
            g.context.quadraticCurveTo(tooltipX + hintWidth, tooltipY, tooltipX + hintWidth, tooltipY + radius);
            g.context.lineTo(tooltipX + hintWidth, tooltipY + hintHeight - radius);
            g.context.quadraticCurveTo(tooltipX + hintWidth, tooltipY + hintHeight, tooltipX + hintWidth - radius, tooltipY + hintHeight);
            g.context.lineTo(tooltipX + radius, tooltipY + hintHeight);
            g.context.quadraticCurveTo(tooltipX, tooltipY + hintHeight, tooltipX, tooltipY + hintHeight - radius);
            g.context.lineTo(tooltipX, tooltipY + radius);
            g.context.quadraticCurveTo(tooltipX, tooltipY, tooltipX + radius, tooltipY);
            g.context.closePath();
            
            // Fill with modern dark color
            g.context.setFillStyle("#1e1e2e");
            g.context.fill();
            
            // Reset shadow before drawing border and text
            g.context.setShadowColor("transparent");
            g.context.setShadowBlur(0);
            g.context.setShadowOffsetX(0);
            g.context.setShadowOffsetY(0);
            
            // Draw accent border
            g.context.setStrokeStyle("#6c7086");
            g.context.setLineWidth(1);
            g.context.stroke();
            
            // Draw tooltip text
            g.context.setFillStyle("#cdd6f4");
			int textY = tooltipY + verticalPadding + 12;
			for (int i = 0; i < tooltipLines.size(); i++) {
				g.context.fillText(tooltipLines.get(i), tooltipX + 8, textY + i * lineHeight);
			}
        }
    }

    void drawActionSchedulerMessage(Graphics g, Context2d context) {
        ActionScheduler scheduler = ActionScheduler.getInstance();
        if (scheduler != null && scheduler.hasDisplayMessage()) {
            String message = scheduler.getDisplayMessage();
            
            // Save graphics state
            g.save();
            
            // Set color based on background (white on black, black on white)
            if (printableCheckItem.getState()) {
                g.context.setFillStyle("#000000"); // Black text on white background
            } else {
                g.context.setFillStyle("#FFFFFF"); // White text on black background
            }
            
            // Draw the message text centered at top
            g.context.setFont("bold 24px sans-serif");
            g.context.setTextAlign("center");
            g.context.setTextBaseline("top");
            
            // Center horizontally - use canvas width / 2
            int centerX = (context == cvcontext) ? circuitArea.width / 2 : 
                         (int) (context.getCanvas().getWidth() / 2);
            int topY = 15;
            
            g.context.fillText(message, centerX, topY);
            
            g.restore();
        }
    }
    
    void drawBottomArea(Graphics g) {
	int leftX = 0;
	int h = 0;
	if (stopMessage == null && scopeCount == 0) {
	    leftX = max(canvasWidth-infoWidth, 0);
	    int h0 = (int) (canvasHeight * scopeHeightFraction);
	    h = (mouseElm == null) ? 70 : h0;
	    if (hideInfoBox)
		h = 0;
	}
	if (stopMessage != null && circuitArea.height > canvasHeight-30)
	    h = 30;
	g.setColor(printableCheckItem.getState() ? "#eee" : "#202020");  // Dark gray - same as undocked scopes
	g.fillRect(leftX, circuitArea.height-h, circuitArea.width, canvasHeight-circuitArea.height+h);
	g.setFont(CircuitElm.unitsFont);
	int ct = scopeCount;
	if (stopMessage != null)
	    ct = 0;
	int i;
	Scope.clearCursorInfo();
	for (i = 0; i != ct; i++)
	    scopes[i].selectScope(mouseCursorX, mouseCursorY, dragging);
	if (scopeElmArr != null)
	    for (i=0; i != scopeElmArr.length; i++)
		scopeElmArr[i].selectScope(mouseCursorX, mouseCursorY, dragging);
	for (i = 0; i != ct; i++)
	    scopes[i].draw(g);
	if (mouseWasOverSplitter) {
		g.setColor(CircuitElm.selectColor);
		g.setLineWidth(4.0);
		g.drawLine(0, circuitArea.height-2, circuitArea.width, circuitArea.height-2);
		g.setLineWidth(1.0);
	}
	// Highlight the actual splitter line when hovering over minimize/maximize button
	if (scopeCount > 0 && mouseIsOverScopeMinMaxButton(mouseCursorX, mouseCursorY)) {
	    // Draw line but stop before the button area (with some padding)
	    int lineEndX = circuitArea.width - SCOPE_MIN_MAX_BUTTON_SIZE - 20;
	    g.setColor(CircuitElm.selectColor);
	    g.setLineWidth(3.0);
	    g.drawLine(0, circuitArea.height-2, lineEndX, circuitArea.height-2);
	    g.setLineWidth(1.0);
	}
	// Draw minimize/maximize button on the splitter line
	if (scopeCount > 0) {
	    drawScopeMinMaxButton(g);
	}
	g.setColor(CircuitElm.whiteColor);

	if (stopMessage != null) {
	    g.drawString(stopMessage, 10, canvasHeight-10);
	} else if (!hideInfoBox) {
	    // in JS it doesn't matter how big this is, there's no out-of-bounds exception
	    String info[] = new String[10];
	    int infoIdx = 0;
	    
	    // Time is now shown in top left, so start with element info
	    if (mouseElm != null) {
		if (mousePost == -1) {
		    // Show element info
		    String[] elmInfo = new String[10];
		    mouseElm.getInfo(elmInfo);
		    for (int idx = 0; idx < elmInfo.length && elmInfo[idx] != null; idx++) {
		        info[infoIdx++] = Locale.LS(elmInfo[idx]);
		    }
		} else {
		    info[infoIdx++] = "V = " + CircuitElm.getUnitText(mouseElm.getPostVoltage(mousePost), "V");
		    // Add node name if available
		    String nodeName = LabeledNodeElm.getNameByNode(mouseElm.nodes[mousePost]);
		    if (nodeName != null)
			info[infoIdx++] = "Node: " + nodeName;
		}

            
//		/* //shownodes
//		for (i = 0; i != mouseElm.getPostCount(); i++)
//		    info[0] += " " + mouseElm.nodes[i];
//		if (mouseElm.getVoltageSourceCount() > 0)
//		    info[0] += ";" + (mouseElm.getVoltageSource()+nodeList.size());
//		*/
		
	    } else {
	    	// When no element is selected, show timestep info
	    	info[0] = Locale.LS("time step = ") + CircuitElm.getUnitText(timeStep, "s");
	    }
	    if (hintType != -1) {
		for (i = 0; info[i] != null; i++)
		    ;
		String s = getHint();
		if (s == null)
		    hintType = -1;
		else
		    info[i] = s;
	    }
	    int x = leftX + 5;
	    if (ct != 0)
		x = scopes[ct-1].rightEdge();
//	    x = max(x, canvasWidth*2/3);
	  //  x=cv.getCoordinateSpaceWidth()*2/3;
	    
	    // count lines of data
	    int lineCount = 0;
	    for (lineCount = 0; info[lineCount] != null; lineCount++)
		;
	    int badnodes = badConnectionList.size();
	    if (badnodes > 0)
		info[lineCount++] = badnodes + ((badnodes == 1) ?
					Locale.LS(" bad connection") : Locale.LS(" bad connections"));
	    if (savedFlag)
		info[lineCount++] = "(saved)";
	    
	    // Show cursor position (grid-snapped coordinates)
	    int snapX = snapGrid(inverseTransformX(mouseCursorX));
	    int snapY = snapGrid(inverseTransformY(mouseCursorY));
	    info[lineCount++] = "cursor: (" + snapX + ", " + snapY + ")";
	    
	    // Show equation table mode
	    info[lineCount++] = "EqnTable: " + (equationTableMnaMode ? "MNA" : "Computed");

	    // Calculate required height for all lines (15 pixels per line plus initial offset)
	    int requiredHeight = 15 * (lineCount + 1);
	    int availableHeight = canvasHeight - (circuitArea.height - h);
	    
	    // Adjust ybase upward if not enough space
	    int ybase = circuitArea.height - h;
	    if (requiredHeight > availableHeight) {
		ybase = canvasHeight - requiredHeight;
	    }
		// int ybase = circuitArea.y+10;

	    for (i = 0; info[i] != null; i++)
		g.drawString(info[i], x, ybase+15*(i+1));
	}
	if (stopMessage == null && warningMessage != null && !warningMessage.isEmpty()) {
	    g.setColor(Color.red);
	    g.drawString(warningMessage, 10, canvasHeight-10);
	    g.setColor(CircuitElm.whiteColor);
	}
    }
    
    Color getBackgroundColor() {
	if (printableCheckItem.getState())
	    return Color.white;
	return Color.black;
    }

    /**
     * Detect collisions where EquationTable PARAM names match physical LabeledNode
     * names. These collisions can change name-resolution behavior in MNA mode.
     */
	void updateEquationParameterCollisionWarning() {
	if (elmList == null || elmList.isEmpty()) {
	    warningMessage = null;
	    return;
	}

	java.util.HashSet<String> labeledNames = new java.util.HashSet<String>();
	for (int i = 0; i < elmList.size(); i++) {
	    CircuitElm ce = getElm(i);
	    if (ce instanceof LabeledNodeElm) {
		LabeledNodeElm lne = (LabeledNodeElm) ce;
		if (lne.text != null) {
		    String name = lne.text.trim();
		    if (!name.isEmpty()) {
			labeledNames.add(name);
		    }
		}
	    }
	}

	if (labeledNames.isEmpty()) {
	    warningMessage = null;
	    return;
	}

	java.util.HashSet<String> collisions = new java.util.HashSet<String>();
	for (int i = 0; i < elmList.size(); i++) {
	    CircuitElm ce = getElm(i);
	    if (!(ce instanceof EquationTableElm)) {
		continue;
	    }

	    EquationTableElm table = (EquationTableElm) ce;
	    int rows = table.getRowCount();
	    for (int row = 0; row < rows; row++) {
		if (table.getOutputMode(row) != EquationTableElm.RowOutputMode.PARAM_MODE) {
		    continue;
		}
		String outputName = table.getOutputName(row);
		if (outputName == null) {
		    continue;
		}
		String paramName = outputName.trim();
		if (!paramName.isEmpty() && labeledNames.contains(paramName)) {
		    collisions.add(paramName);
		}
	    }
	}

	if (collisions.isEmpty()) {
	    warningMessage = null;
	    return;
	}

	java.util.ArrayList<String> sorted = new java.util.ArrayList<String>(collisions);
	java.util.Collections.sort(sorted);
	StringBuilder sb = new StringBuilder();
	sb.append("Warning: PARAM/LabeledNode name collision: ");
	for (int i = 0; i < sorted.size(); i++) {
	    if (i > 0) {
		sb.append(", ");
	    }
	    sb.append(sorted.get(i));
	}
	warningMessage = sb.toString();
    }
    
    int oldScopeCount = -1;
    
    boolean scopeMenuIsSelected(Scope s) {
	if (scopeMenuSelected < 0)
	    return false;
	if (scopeMenuSelected < scopeCount)
	    return scopes[scopeMenuSelected] == s;
	return getNthScopeElm(scopeMenuSelected-scopeCount).elmScope == s; 
    }
    
    void setupScopes() {
	scopeManager.setupScopes();
    }
    
    String getHint() {
	CircuitElm c1 = getElm(hintItem1);
	CircuitElm c2 = getElm(hintItem2);
	if (c1 == null || c2 == null)
	    return null;
	if (hintType == HINT_LC) {
	    if (!(c1 instanceof InductorElm))
		return null;
	    if (!(c2 instanceof CapacitorElm))
		return null;
	    InductorElm ie = (InductorElm) c1;
	    CapacitorElm ce = (CapacitorElm) c2;
	    return Locale.LS("res.f = ") + CircuitElm.getUnitText(1/(2*pi*Math.sqrt(ie.inductance*
						    ce.capacitance)), "Hz");
	}
	if (hintType == HINT_RC) {
	    if (!(c1 instanceof ResistorElm))
		return null;
	    if (!(c2 instanceof CapacitorElm))
		return null;
	    ResistorElm re = (ResistorElm) c1;
	    CapacitorElm ce = (CapacitorElm) c2;
	    return "RC = " + CircuitElm.getUnitText(re.resistance*ce.capacitance,
					 "s");
	}
	if (hintType == HINT_3DB_C) {
	    if (!(c1 instanceof ResistorElm))
		return null;
	    if (!(c2 instanceof CapacitorElm))
		return null;
	    ResistorElm re = (ResistorElm) c1;
	    CapacitorElm ce = (CapacitorElm) c2;
	    return Locale.LS("f.3db = ") +
		CircuitElm.getUnitText(1/(2*pi*re.resistance*ce.capacitance), "Hz");
	}
	if (hintType == HINT_3DB_L) {
	    if (!(c1 instanceof ResistorElm))
		return null;
	    if (!(c2 instanceof InductorElm))
		return null;
	    ResistorElm re = (ResistorElm) c1;
	    InductorElm ie = (InductorElm) c2;
	    return Locale.LS("f.3db = ") +
		CircuitElm.getUnitText(re.resistance/(2*pi*ie.inductance), "Hz");
	}
	if (hintType == HINT_TWINT) {
	    if (!(c1 instanceof ResistorElm))
		return null;
	    if (!(c2 instanceof CapacitorElm))
		return null;
	    ResistorElm re = (ResistorElm) c1;
	    CapacitorElm ce = (CapacitorElm) c2;
	    return Locale.LS("fc = ") +
		CircuitElm.getUnitText(1/(2*pi*re.resistance*ce.capacitance), "Hz");
	}
	return null;
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
    public void registerTableMastersInPriorityOrder() {
	// Collect all tables
	java.util.ArrayList<TableElm> tables = new java.util.ArrayList<TableElm>();
	for (int i = 0; i != elmList.size(); i++) {
	    CircuitElm ce = getElm(i);
	    if (ce instanceof TableElm) {
		TableElm te = (TableElm)ce;
		tables.add(te);
		// console("[PRIORITY_ORDER] Found table '" + te.getTableTitle() + "' with priority=" + te.getPriority());
	    }
	}
	
	// console("[PRIORITY_ORDER] Collected " + tables.size() + " tables, now sorting...");
	
	// Sort by priority (highest first)
	// Using simple bubble sort since table count is typically small (<10)
	for (int i = 0; i < tables.size(); i++) {
	    for (int j = i + 1; j < tables.size(); j++) {
		if (tables.get(j).getPriority() > tables.get(i).getPriority()) {
		    // Swap
		    TableElm temp = tables.get(i);
		    tables.set(i, tables.get(j));
		    tables.set(j, temp);
		}
	    }
	}
	
	// console("[PRIORITY_ORDER] After sorting:");
	for (int i = 0; i < tables.size(); i++) {
	    TableElm table = tables.get(i);
	    // console("[PRIORITY_ORDER]   " + i + ": '" + table.getTableTitle() + "' (priority=" + table.getPriority() + ")");
	}
	
	// Register in priority order
	for (int i = 0; i < tables.size(); i++) {
	    TableElm table = tables.get(i);
	    // console("[PRIORITY_ORDER] Processing table '" + table.getTableTitle() + "' (priority=" + table.getPriority() + ")");
	    table.registerAsMasterOnly();
	}
	
	// Update pin output flags to match new master status
	// console("[PRIORITY_ORDER] Updating pin output flags to match new master assignments...");
	for (int i = 0; i < tables.size(); i++) {
	    TableElm table = tables.get(i);
	    table.updatePinOutputFlags();
	}
	
	// console("[PRIORITY_ORDER] Table master registration completed");
    }
    
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
    
    static void electronSaveAsCallback(String s) {
	s = s.substring(s.lastIndexOf('/')+1);
	s = s.substring(s.lastIndexOf('\\')+1);
	theSim.setCircuitTitle(s);
	theSim.allowSave(true);
	theSim.savedFlag = true;
	theSim.repaint();
    }

    static void electronSaveCallback() {
	theSim.savedFlag = true;
	theSim.repaint();
    }
        
	static void electronSaveAs(final String dump) {
		showSaveDialog().then(new SaveDialogSuccessCallback() {
			public Object onSuccess(SaveDialogResult file) {
				if (file == null || file.isCanceled())
					return null;
				saveFile(file, dump);
				Object path = file.getFilePath();
				if (path != null)
					electronSaveAsCallback(path.toString());
				return null;
			}
		}, new SaveDialogFailureCallback() {
			public Object onFailure(Object error) {
				console("electronSaveAs failed: " + error);
				return null;
			}
		});
	}

	static void electronSave(String dump) {
		saveFile(null, dump);
		electronSaveCallback();
	}
    
    static void electronOpenFileCallback(String text, String name) {
	LoadFile.doLoadCallback(text, name);
	theSim.allowSave(true);
    }
    
	static void electronOpenFile() {
		openFile(new OpenFileCallback() {
			public void onOpen(String text, String name) {
				electronOpenFileCallback(text, name);
			}
		});
	}
    
	static void toggleDevTools() {
		toggleDevToolsNative();
	}
    
	static boolean isElectron() {
		return GlobalWindowLike.getOpenFileFunction() != null;
	}

	static String getElectronStartCircuitText() {
	    return GlobalWindowLike.getStartCircuitText();
	}
    
    void allowSave(boolean b) {
	if (saveFileItem != null)
	    saveFileItem.setEnabled(b);
    }
    
    public void menuPerformed(String menu, String item) {
	commandRouter.menuPerformed(menu, item);
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

    /**
     * Open the iframe viewer dialog with external documentation
     * Uses CSS selector to show only the split-right content panel
     */
    void openIframeViewer() {
	infoDialogActions.openIframeViewer();
    }

	void openReferenceDocsViewer() {
	infoDialogActions.openReferenceDocsViewer();
	}
    
    int countScopeElms() {
	return scopeManager.countScopeElms();
    }
    
    ScopeElm getNthScopeElm(int n) {
	return scopeManager.getNthScopeElm(n);
    }
    
    
    boolean canStackScope(int s) {
	return scopeManager.canStackScope(s);
    }
    
    boolean canCombineScope(int s) {
	return scopeManager.canCombineScope(s);
    }
    
    boolean canUnstackScope(int s) {
	return scopeManager.canUnstackScope(s);
    }

    void stackScope(int s) {
	scopeManager.stackScope(s);
    }

    void unstackScope(int s) {
	scopeManager.unstackScope(s);
    }

    void combineScope(int s) {
	scopeManager.combineScope(s);
    }
    

    void stackAll() {
	scopeManager.stackAll();
    }

    void unstackAll() {
	scopeManager.unstackAll();
    }

    void combineAll() {
	scopeManager.combineAll();
    }
    
    void separateAll() {
	scopeManager.separateAll();
    }

    void doEdit(Editable eable) {
	editDialogActions.doEdit(eable);
    }
    
    void doSliders(CircuitElm ce) {
	editDialogActions.doSliders(ce);
    }


    void doExportAsUrl()
    {
	circuitIOService.doExportAsUrl();
    }

	void doOpenRunnerOutputTable()
	{
	circuitIOService.doOpenRunnerOutputTable();
	}

	String getRunnerDumpFromStorage(String key) {
	return circuitIOService.getRunnerDumpFromStorage(key);
	}

	String compressForUrl(String dump) {
	return compressUri(dump);
	}
    
    void doExportAsText()
    {
	circuitIOService.doExportAsText();
    }

    void doExportAsSFCR()
    {
	circuitIOService.doExportAsSFCR();
    }

    void doViewModelInfo()
    {
	infoDialogActions.doViewModelInfo();
    }

    void doEditLookupTables()
    {
	editDialogActions.doEditLookupTables();
    }

    void doExportAsImage()
    {
	editDialogActions.doExportAsImage();
    }

    private static void clipboardWriteImage(CanvasElement cv) {
	try {
	    clipboardWriteText(cv.toDataUrl("image/png"));
	} catch (Throwable t) {
	}
    }

	void doImageToClipboardCore() {
	Canvas cv = CirSim.theSim.getCircuitAsCanvas(CAC_IMAGE);
	clipboardWriteImage(cv.getCanvasElement());
	}

    void doImageToClipboard()
    {
	editDialogActions.doImageToClipboard();
    }
    
    void doCreateSubcircuit()
    {
	editDialogActions.doCreateSubcircuit();
    }
    
    void doExportAsLocalFile() {
	circuitIOService.doExportAsLocalFile();
    }

    public void importCircuitFromText(String circuitText, boolean subcircuitsOnly) {
	importExportHelper.importCircuitFromText(circuitText, subcircuitsOnly);
    }
    
    /**
     * Import circuit from compressed CTZ format (used in URL parameters).
     * This allows loading circuits via JS API without reloading the entire app.
     * 
     * @param ctzData The LZString compressed circuit data (from ctz= URL parameter)
     * @param subcircuitsOnly If true, only import subcircuits (keep existing elements)
     */
    public void importCircuitFromCTZ(String ctzData, boolean subcircuitsOnly) {
        importExportHelper.importCircuitFromCTZ(ctzData, subcircuitsOnly);
    }

    String dumpOptions() {
	return importExportHelper.dumpOptions();
    }
    
    String dumpCircuit() {
	return circuitIOService.dumpCircuit();
    }

    String getElementDumpWithUid(CircuitElm ce) {
	return importExportHelper.getElementDumpWithUid(ce);
    }

    static class ElementDumpParseResult {
	StringTokenizer tokenizer;
	String uid;
	ElementDumpParseResult(StringTokenizer tokenizer, String uid) {
	    this.tokenizer = tokenizer;
	    this.uid = uid;
	}
    }

    ElementDumpParseResult parseElementTokensWithUid(StringTokenizer st) {
	return importExportHelper.parseElementTokensWithUid(st);
    }

    CircuitElm findElmByUid(String uid) {
	return importExportHelper.findElmByUid(uid);
    }

    void assignPersistentUid(CircuitElm ce, String uidFromFile) {
	importExportHelper.assignPersistentUid(ce, uidFromFile);
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

    void readCircuit(String text, int flags) {
	circuitIOService.readCircuit(text, flags);
    }

    void readCircuit(String text) {
	circuitIOService.readCircuit(text);
    }

    /**
     * Process a single circuit element definition line.
     * Used by SFCRParser to add raw circuit elements from @circuit blocks.
     * 
     * @param line The element definition line (e.g., "431 480 64 592 160 0 50 true false")
     */
    void processCircuitLine(String line) {
	circuitIOService.processCircuitLine(line);
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
    
	void readSetupFile(String str, String title) {
	    circuitIOService.readSetupFile(str, title);
	}

	void readSetupFileCandidates(final String[] candidates, final int index, final String title) {
	    circuitIOService.readSetupFileCandidates(candidates, index, title);
	}
	
	void loadFileFromURL(String url, final Command successCallback, final Command failureCallback) {
	    circuitIOService.loadFileFromURL(url, successCallback, failureCallback);
	}

	String getLoadUrl(String url) {
	    return circuitIOService.getLoadUrl(url);
	}

    static final int RC_RETAIN = 1;
    static final int RC_NO_CENTER = 2;
    static final int RC_SUBCIRCUITS = 4;
    static final int RC_KEEP_TITLE = 8;

    void readCircuit(byte b[], int flags) {
	circuitIOService.readCircuit(b, flags);
    }

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
	setGrid();
    }
    
    int snapGrid(int x) {
	return (x+gridRound) & gridMask;
    }

	boolean doSwitch(int x, int y) {
		return mouseInputHandler.doSwitch(x, y);
	}

	boolean doTableCollapseToggle(int x, int y) {
		return mouseInputHandler.doTableCollapseToggle(x, y);
	}

    int locateElm(CircuitElm elm) {
	int i;
	for (i = 0; i != elmList.size(); i++)
	    if (elm == elmList.elementAt(i))
		return i;
	return -1;
    }
    
    public void mouseDragged(MouseMoveEvent e) {
	mouseInputHandler.mouseDragged(e);
    }
    
    void dragSplitter(int x, int y) {
	mouseInputHandler.dragSplitter(x, y);
    }

    void dragAll(int x, int y) {
	mouseInputHandler.dragAll(x, y);
    }

    void dragRow(int x, int y) {
	mouseInputHandler.dragRow(x, y);
    }

    void dragColumn(int x, int y) {
	mouseInputHandler.dragColumn(x, y);
    }

    boolean onlyGraphicsElmsSelected() {
	return mouseInputHandler.onlyGraphicsElmsSelected();
    }
    
    boolean dragSelected(int x, int y) {
	return mouseInputHandler.dragSelected(x, y);
    }

    void dragPost(int x, int y, boolean all) {
	mouseInputHandler.dragPost(x, y, all);
    }

    void doFlip() {
	mouseInputHandler.doFlip();
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
    
    /**
     * Select elements within a rectangular area (rubber-band selection).
     * 
     * Called when user drags to create a selection rectangle.
     * Elements partially or fully within the rectangle are selected.
     * 
     * @param x Current drag position X
     * @param y Current drag position Y
     * @param add If true, add to existing selection; if false, replace selection
     */
    void selectArea(int x, int y, boolean add) {
	mouseInputHandler.selectArea(x, y, add);
    }

    void enableDisableMenuItems() {
	boolean canFlipX = true;
	boolean canFlipY = true;
	boolean canFlipXY = true;
	int selCount = countSelected();
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
    
    boolean mouseIsOverSplitter(int x, int y) {
	return mouseInputHandler.mouseIsOverSplitter(x, y);
    }
    
    /**
     * Draws the minimize/maximize button at the 0.1 fraction line (fixed position).
     */
    void drawScopeMinMaxButton(Graphics g) {
	// Position button at the 0.1 (10%) fraction line, not at current splitter position
	int minHeightY = (int)(canvasHeight * (1.0 - 0.1)); // Y position for 0.1 fraction
	int buttonX = circuitArea.width - SCOPE_MIN_MAX_BUTTON_SIZE - 10;
	int buttonY = minHeightY - SCOPE_MIN_MAX_BUTTON_SIZE / 2;
	
	// Set color based on cursor position
	boolean hover = mouseIsOverScopeMinMaxButton(mouseCursorX, mouseCursorY);
	g.setColor(hover ? CircuitElm.selectColor : Color.gray);
	
	// Draw button background (rounded rectangle)
	g.context.save();
	g.context.setLineWidth(1.5);
	g.context.strokeRect(buttonX, buttonY, SCOPE_MIN_MAX_BUTTON_SIZE, SCOPE_MIN_MAX_BUTTON_SIZE);
	
	// Draw arrow (up for minimize, down for maximize)
	int centerX = buttonX + SCOPE_MIN_MAX_BUTTON_SIZE / 2;
	int centerY = buttonY + SCOPE_MIN_MAX_BUTTON_SIZE / 2;
	int arrowSize = 6;
	
	g.context.beginPath();
	if (!scopePanelMinimized) {
	    // Draw upward arrow (minimize)
	    g.context.moveTo(centerX, centerY - arrowSize/2);
	    g.context.lineTo(centerX - arrowSize, centerY + arrowSize/2);
	    g.context.moveTo(centerX, centerY - arrowSize/2);
	    g.context.lineTo(centerX + arrowSize, centerY + arrowSize/2);
	} else {
	    // Draw downward arrow (maximize)
	    g.context.moveTo(centerX, centerY + arrowSize/2);
	    g.context.lineTo(centerX - arrowSize, centerY - arrowSize/2);
	    g.context.moveTo(centerX, centerY + arrowSize/2);
	    g.context.lineTo(centerX + arrowSize, centerY - arrowSize/2);
	}
	g.context.stroke();
	g.context.restore();
    }
    
    /**
     * Checks if mouse is over the scope minimize/maximize button.
     * Button is positioned at the 0.1 fraction line for consistency.
     */
    boolean mouseIsOverScopeMinMaxButton(int x, int y) {
	if (scopeCount == 0)
	    return false;
	// Position button at the 0.1 (10%) fraction line, not at current splitter position
	int minHeightY = (int)(canvasHeight * (1.0 - 0.1)); // Y position for 0.1 fraction
	int buttonX = circuitArea.width - SCOPE_MIN_MAX_BUTTON_SIZE - 10;
	int buttonY = minHeightY - SCOPE_MIN_MAX_BUTTON_SIZE / 2;
	return x >= buttonX && x <= buttonX + SCOPE_MIN_MAX_BUTTON_SIZE &&
	       y >= buttonY && y <= buttonY + SCOPE_MIN_MAX_BUTTON_SIZE;
    }
    
    /**
     * Toggles the scope panel between minimized and normal height.
     * Uses the same minimum height (0.1) as the splitter dragging constraint.
     */
    void toggleScopePanelSize() {
	scopePanelMinimized = !scopePanelMinimized;
	if (scopePanelMinimized) {
	    // Store current height and minimize to same minimum as splitter allows
	    normalScopeHeightFraction = scopeHeightFraction;
	    scopeHeightFraction = 0.1; // Minimize to 10% (same as splitter minimum)
	} else {
	    // Restore normal height
	    scopeHeightFraction = normalScopeHeightFraction;
	}
	setCircuitArea();
	repaint();
    }
    
    /**
     * Update hover state for ActionTimeElm play/pause icons
     */
    void updateActionTimeElmIconHover(int gx, int gy) {
	mouseInputHandler.updateActionTimeElmIconHover(gx, gy);
    }

    public void onMouseMove(MouseMoveEvent e) {
	mouseInputHandler.onMouseMove(e);
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
    
    

    /**
     * Handle mouse selection and hover detection.
     * Called on mouse move events to determine which element is under the cursor.
     * 
     * SELECTION PRIORITY:
     * 1. Scope panel splitter (if hovering over it)
     * 2. Current mouseElm's handles (if close to a handle)
     * 3. Elements whose bounding box contains cursor (closest one wins)
     * 4. Scope panels (if cursor inside scope rectangle)
     * 5. Element posts (if within 26 pixel radius)
     * 
     * SETS:
     * - mouseElm: Element under cursor (or null)
     * - mousePost: Terminal/post number if hovering over post (or -1)
     * - draggingPost: Post being dragged (or -1)
     * - scopeSelected: Scope index if hovering over scope (or -1)
     * - plotXElm, plotYElm: For XY plot scopes
     */
    public void mouseSelect(MouseEvent<?> e) {
	mouseInputHandler.mouseSelect(e);
    }



    public void onContextMenu(ContextMenuEvent e) {
	mouseInputHandler.onContextMenu(e);
    }
    
    void doPopupMenu() {
	mouseInputHandler.doPopupMenu();
    }

    boolean canSplit(CircuitElm ce) {
	return mouseInputHandler.canSplit(ce);
    }
    
    // check if the user can create sliders for this element
    boolean sliderItemEnabled(CircuitElm elm) {
	return mouseInputHandler.sliderItemEnabled(elm);
    }

    void longPress() {
	mouseInputHandler.longPress();
    }
    
    void twoFingerTouch(int x, int y) {
	mouseInputHandler.twoFingerTouch(x, y);
    }
    
//    public void mouseClicked(MouseEvent e) {
    public void onClick(ClickEvent e) {
	mouseInputHandler.onClick(e);
    }
    
    public void onDoubleClick(DoubleClickEvent e){
	mouseInputHandler.onDoubleClick(e);
    }
    
//    public void mouseEntered(MouseEvent e) {
//    }
    
    public void onMouseOut(MouseOutEvent e) {
	mouseInputHandler.onMouseOut(e);
    }

    void clearMouseElm() {
	mouseInputHandler.clearMouseElm();
    }
    
    int menuClientX, menuClientY;
    int menuX, menuY;
    
    public void onMouseDown(MouseDownEvent e) {
	mouseInputHandler.onMouseDown(e);
    }

    static int lastSubcircuitMenuUpdate;
    
    // check/uncheck/enable/disable menu items as appropriate when menu bar clicked on, or when
    // right mouse menu accessed.  also displays shortcuts as a side effect
    void doMainMenuChecks() {
	mouseInputHandler.doMainMenuChecks();
    }
    
 
    public void onMouseUp(MouseUpEvent e) {
	mouseInputHandler.onMouseUp(e);
    }
    
    public void onMouseWheel(MouseWheelEvent e) {
	mouseInputHandler.onMouseWheel(e);
    }

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

	void zoomCircuit(double dy) { viewportController.zoomCircuit(dy); }

    void zoomCircuit(double dy, boolean menu) {
	viewportController.zoomCircuit(dy, menu);
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
    
    // Format time with fixed 2 decimal places and appropriate SI prefix
    String formatTimeFixed(double t) {
	NumberFormat fixedFmt = NumberFormat.getFormat("0.00");
	String u = timeUnitSymbol;
	double va = Math.abs(t);
	if (va < 1e-14)
	    return "0.00 " + u;
	if (va < 1e-9)
	    return fixedFmt.format(t*1e12) + " p" + u;
	if (va < 1e-6)
	    return fixedFmt.format(t*1e9) + " n" + u;
	if (va < 1e-3)
	    return fixedFmt.format(t*1e6) + " μ" + u;
	if (va < 1)
	    return fixedFmt.format(t*1e3) + " m" + u;
	if (va < 1e3)
	    return fixedFmt.format(t) + " " + u;
	if (va < 1e6)
	    return fixedFmt.format(t*1e-3) + " k" + u;
	return NumberFormat.getFormat("#.##E000").format(t) + " " + u;
    }
    
    void setGrid() {
	if (smallGridCheckItem != null)
	    gridSize = (smallGridCheckItem.getState()) ? 8 : 16;
	else
	    gridSize = 16;
	gridMask = ~(gridSize-1);
	gridRound = gridSize/2-1;
    }

    void setToolbar() {
	layoutPanel.setWidgetHidden(toolbar, !toolbarCheckItem.getState());
	setCanvasSize();
    }
    
    void switchToElectronicsToolbar() {
	toolbarModeManager.switchToElectronicsToolbar();
    }
    
    void switchToEconomicsToolbar() {
	toolbarModeManager.switchToEconomicsToolbar();
    }

    // Mode selector method - switches toolbar and sets appropriate voltage unit
    void setMode(ToolbarType mode) {
	toolbarModeManager.setMode(mode);
    }
    
    void setElectronicsMode() {
	toolbarModeManager.setElectronicsMode();
    }
    
    void setEconomicsMode() {
	toolbarModeManager.setEconomicsMode();
    }

    void pushUndo() {
	undoRedoManager.pushUndo();
    }

    void doUndo() {
	undoRedoManager.doUndo();
    }

    void doRedo() {
	undoRedoManager.doRedo();
    }

    void loadUndoItem(UndoItem ui) {
	readCircuit(ui.dump, RC_NO_CENTER);
	transform[0] = transform[3] = ui.scale;
	transform[4] = ui.transform4;
	transform[5] = ui.transform5;
    }
    
    void doRecover() {
	undoRedoManager.doRecover();
    }
    
    void enableUndoRedo() {
	undoRedoManager.enableUndoRedo();
    }

    void setMouseMode(int mode)
    {
	mouseInputHandler.setMouseMode(mode);
    }
    
    void setCursorStyle(String s) {
	mouseInputHandler.setCursorStyle(s);
    }
    


    void setMenuSelection() {
	clipboardManager.setMenuSelection();
    }

    int countSelected() {
	return clipboardManager.countSelected();
    }

    void flipX() {
	flipTransformController.flipX();
    }

    void flipY() {
	flipTransformController.flipY();
    }

    void flipXY() {
	flipTransformController.flipXY();
    }

    void doCut() {
	clipboardManager.doCut();
    }

    void writeClipboardToStorage() {
	clipboardManager.writeClipboardToStorage();
    }
    
    void readClipboardFromStorage() {
	clipboardManager.readClipboardFromStorage();
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
    	String s = dumpCircuit();
    	stor.setItem("circuitRecovery", s);
    }

    void readRecovery() {
	Storage stor = Storage.getLocalStorageIfSupported();
	if (stor == null)
		return;
	recovery = stor.getItem("circuitRecovery");
    }


    void deleteUnusedScopeElms() {
	scopeManager.deleteUnusedScopeElms();
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
    
    void doDelete(boolean pushUndoFlag) {
	clipboardManager.doDelete(pushUndoFlag);
    }
    
    boolean willDelete( CircuitElm ce ) {
	return clipboardManager.willDelete(ce);
    }
    
    String copyOfSelectedElms() {
	return clipboardManager.copyOfSelectedElms();
    }
    
    void doCopy() {
	clipboardManager.doCopy();
    }

    void enablePaste() {
	clipboardManager.enablePaste();
    }

    void doDuplicate() {
	clipboardManager.doDuplicate();
    }
    
    void doPaste(String dump) {
	clipboardManager.doPaste(dump);
    }

    void clearSelection() {
	clipboardManager.clearSelection();
    }
    
    void doSelectAll() {
	clipboardManager.doSelectAll();
    }
    
    boolean anySelectedButMouse() {
	return clipboardManager.anySelectedButMouse();
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
    
    public void onPreviewNativeEvent(NativePreviewEvent e) {
	mouseInputHandler.onPreviewNativeEvent(e);
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

    
    void createNewLoadFile() {
    	// This is a hack to fix what IMHO is a bug in the <INPUT FILE element
    	// reloading the same file doesn't create a change event so importing the same file twice
    	// doesn't work unless you destroy the original input element and replace it with a new one
    	int idx=verticalPanel.getWidgetIndex(loadFileInput);
    	LoadFile newlf=new LoadFile(this);
    	verticalPanel.insert(newlf, idx);
    	verticalPanel.remove(idx+1);
    	loadFileInput=newlf;
    }

    void addWidgetToVerticalPanel(Widget w) {
	if (RuntimeMode.isNonInteractiveRuntime() || w == null || verticalPanel == null)
	    return;
    	if (iFrame!=null) {
    		int i=verticalPanel.getWidgetIndex(iFrame);
    		verticalPanel.insert(w, i);
    		setiFrameHeight();
    	}
    	else
    		verticalPanel.add(w);
    }
    
    void removeWidgetFromVerticalPanel(Widget w){
	if (RuntimeMode.isNonInteractiveRuntime() || w == null || verticalPanel == null)
	    return;
    	verticalPanel.remove(w);
    	if (iFrame!=null)
    		setiFrameHeight();
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
    

    
    
    boolean weAreInUS(boolean orCanada) {
    try {
	NavigatorLike nav = GlobalWindowLike.getNavigator();
	String l = nav != null ? nav.getLanguage() : null;
	if (l == null && nav != null)
	    l = nav.getUserLanguage();
	if (l == null || l.length() <= 2)
	    return false;
	String suffix = l.substring(l.length()-2).toUpperCase();
	return ("US".equals(suffix) || ("CA".equals(suffix) && orCanada));
    } catch (Exception e) {
	return false;
    }
    }

	boolean weAreInGermany() {
	try {
	NavigatorLike nav = GlobalWindowLike.getNavigator();
	String l = nav != null ? nav.getLanguage() : null;
	if (l == null && nav != null)
	    l = nav.getUserLanguage();
	return l != null && l.toUpperCase().startsWith("DE");
	} catch (Exception e) {
	return false;
	}
	}
    
    // For debugging
	void logElementRegistryInferenceReport() {
	diagnostics.logElementRegistryInferenceReport();
	}

	// For debugging
    void dumpNodelist() {

	CircuitNode nd;
	CircuitElm e;
	int i,j;
	String s;
	String cs;
//
//	for(i=0; i<nodeList.size(); i++) {
//	    s="Node "+i;
//	    nd=nodeList.get(i);
//	    for(j=0; j<nd.links.size();j++) {
//		s=s+" " + nd.links.get(j).num + " " +nd.links.get(j).elm.getDumpType();
//	    }
//	    console(s);
//	}
	console("Elm list Dump");
	for (i=0;i<elmList.size(); i++) {
	    e=elmList.get(i);
	    cs = e.getDumpClass().toString();
	    int p = cs.lastIndexOf('.');
	    cs = cs.substring(p+1);
	    if (cs=="WireElm") 
		continue;
	    if (cs=="LabeledNodeElm")
		cs = cs+" "+((LabeledNodeElm)e).text;
	    if (cs=="TransistorElm") {
		if (((TransistorElm)e).pnp == -1)
		    cs= "PTransistorElm";
		else
		    cs = "NTransistorElm";
	    }
	    s=cs;
	    for(j=0; j<e.getPostCount(); j++) {
		s=s+" "+e.nodes[j];
	    }
	    console(s);
	}
    }
    
	void printCanvas(CanvasElement cv) {
	    String img = cv.toDataUrl("image/png");
	    Window.open("data:text/html,<html><head><title>Print Circuit</title></head><body><img src='" + URL.encodeQueryString(img) + "'/></body></html>", "print", "height=500,width=500,status=yes,location=no");
	}

	void doDCAnalysis() {
	    dcAnalysisFlag = true;
	    resetAction();
	}
	
	void doPrint() {
	    Canvas cv = getCircuitAsCanvas(CAC_PRINT);
	    printCanvas(cv.getCanvasElement());
	}

	boolean loadedCanvas2SVG = false;

	boolean initializeSVGScriptIfNecessary(final String followupAction) {
		// load canvas2svg if we haven't already
		if (!loadedCanvas2SVG) {
			ScriptInjector.fromUrl("canvas2svg.js").setCallback(new Callback<Void,Exception>() {
				public void onFailure(Exception reason) {
					alertOrWarn("Can't load canvas2svg.js.");
				}
				public void onSuccess(Void result) {
					loadedCanvas2SVG = true;
					if (followupAction.equals("doExportAsSVG")) {
						doExportAsSVG();
					} else if (followupAction.equals("doExportAsSVGFromAPI")) {
						doExportAsSVGFromAPI();
					}
				}
			}).inject();
			return false;
		}
		return true;
	}

	void doExportAsSVG() {
		if (!initializeSVGScriptIfNecessary("doExportAsSVG")) {
			return;
		}
		dialogShowing = new ExportAsImageDialog(CAC_SVG);
		dialogShowing.show();
	}

	public void doExportAsSVGFromAPI() {
		if (!initializeSVGScriptIfNecessary("doExportAsSVGFromAPI")) {
			return;
		}
		String svg = getCircuitAsSVG();
		callSVGRenderedHook(svg);
	}

	static final int CAC_PRINT = 0;
	static final int CAC_IMAGE = 1;
	static final int CAC_SVG   = 2;
	
	public Canvas getCircuitAsCanvas(int type) {
	    	// create canvas to draw circuit into
	    	Canvas cv = Canvas.createIfSupported();
	    	Rectangle bounds = getCircuitBounds();
	    	
		// add some space on edges because bounds calculation is not perfect
	    	int wmargin = 140;
	    	int hmargin = 100;
	    	int w = (bounds.width*2+wmargin) ;
	    	int h = (bounds.height*2+hmargin) ;
	    	cv.setCoordinateSpaceWidth(w);
	    	cv.setCoordinateSpaceHeight(h);
	    
		Context2d context = cv.getContext2d();
		drawCircuitInContext(context, type, bounds, w, h);
		return cv;
	}
	
	// Get all scopes rendered to a single canvas
	public Canvas getScopesAsCanvas() {
		if (scopeCount == 0)
			return null;
		
		// Calculate bounding box for all scopes
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = 0;
		int maxY = 0;
		int margin = 10;
		
		// Find the extents of all scopes
		for (int i = 0; i < scopeCount; i++) {
			Scope s = scopes[i];
			if (s.rect.x < minX) minX = s.rect.x;
			if (s.rect.y < minY) minY = s.rect.y;
			int right = s.rect.x + s.rect.width;
			int bottom = s.rect.y + s.rect.height;
			if (right > maxX) maxX = right;
			if (bottom > maxY) maxY = bottom;
		}
		
		// Calculate canvas size (only the scope area, not the full canvas)
		int canvasWidth = maxX - minX + margin * 2;
		int canvasHeight = maxY - minY + margin * 2;
		
		// Create canvas
		Canvas cv = Canvas.createIfSupported();
		cv.setCoordinateSpaceWidth(canvasWidth);
		cv.setCoordinateSpaceHeight(canvasHeight);
		
		Context2d context = cv.getContext2d();
		Graphics g = new Graphics(context);
		
		// Set background color based on printable setting
		if (printableCheckItem.getState()) {
			CircuitElm.whiteColor = Color.black;
			CircuitElm.lightGrayColor = Color.black;
			g.setColor(Color.white);
		} else {
			CircuitElm.whiteColor = Color.white;
			CircuitElm.lightGrayColor = Color.lightGray;
			g.setColor(Color.black);
		}
		g.fillRect(0, 0, canvasWidth, canvasHeight);
		
		// Translate the context so scopes are drawn at the correct position
		// (offset by -minX, -minY to move them to origin, then add margin)
		context.translate(margin - minX, margin - minY);
		
		// Draw each scope
		for (int i = 0; i < scopeCount; i++) {
			scopes[i].draw(g);
		}
		
		return cv;
	}
	
	// create SVG context using canvas2svg
	static Context2d createSVGContext(int w, int h) {
	    return createC2SContext(w, h).cast();
	}
	
	static String getSerializedSVG(Context2d context) {
	    return ((SvgContextLike) (Object) context).getSerializedSvg();
	}
	
	public String getCircuitAsSVG() {
	    Rectangle bounds = getCircuitBounds();

	    // add some space on edges because bounds calculation is not perfect
	    int wmargin = 140;
	    int hmargin = 100;
	    int w = (bounds.width+wmargin) ;
	    int h = (bounds.height+hmargin) ;
	    Context2d context = createSVGContext(w, h);
	    drawCircuitInContext(context, CAC_SVG, bounds, w, h);
	    return getSerializedSVG(context);
	}
	
	void drawCircuitInContext(Context2d context, int type, Rectangle bounds, int w, int h) {
		Graphics g = new Graphics(context);
		context.setTransform(1, 0, 0, 1, 0, 0);
	    	double oldTransform[] = Arrays.copyOf(transform, 6);
	        
	        double scale = 1;
	        
		// Set flag to indicate we're exporting
		isExporting = true;
		
		// turn on white background, turn off current display
		boolean p = printableCheckItem.getState();
		boolean c = dotsCheckItem.getState();
		boolean print = (type == CAC_PRINT);
		if (print)
		    printableCheckItem.setState(true);
	        if (printableCheckItem.getState()) {
	            CircuitElm.whiteColor = Color.black;
	            CircuitElm.lightGrayColor = Color.black;
	            g.setColor(Color.white);
	        } else {
	            CircuitElm.whiteColor = Color.white;
	            CircuitElm.lightGrayColor = Color.lightGray;
	            g.setColor(Color.black);
	        }
	        g.fillRect(0, 0, w, h);
		dotsCheckItem.setState(false);

	    	int wmargin = 140;
	    	int hmargin = 100;
	        if (bounds != null)
	            scale = Math.min(w /(double)(bounds.width+wmargin),
	                             h/(double)(bounds.height+hmargin));
	        
	        // ScopeElms need the transform array to be updated
		transform[0] = transform[3] = scale;
		transform[4] = -(bounds.x-wmargin/2);
		transform[5] = -(bounds.y-hmargin/2);
		context.scale(scale, scale);
		context.translate(transform[4], transform[5]);
		context.setLineCap(Context2d.LineCap.ROUND);
		
		// draw elements
		int i;
		for (i = 0; i != elmList.size(); i++) {
		    getElm(i).draw(g);
		}
		for (i = 0; i != postDrawList.size(); i++) {
		    CircuitElm.drawPost(g, postDrawList.get(i));
		}
		
		// Draw action scheduler display message if present
		context.setTransform(1, 0, 0, 1, 0, 0);
		drawActionSchedulerMessage(g, context);

		// restore everything
		printableCheckItem.setState(p);
		dotsCheckItem.setState(c);
		transform = oldTransform;
		isExporting = false;
	}
	
	boolean isSelection() {
	    for (int i = 0; i != elmList.size(); i++)
		if (getElm(i).isSelected())
		    return true;
	    return false;
	}
	
	public CustomCompositeModel getCircuitAsComposite() {
	    int i;
	    String nodeDump = "";
	    String dump = "";
//	    String models = "";
	    CustomLogicModel.clearDumpedFlags();
	    DiodeModel.clearDumpedFlags();
	    TransistorModel.clearDumpedFlags();
            Vector<LabeledNodeElm> sideLabels[] = new Vector[] {
                new Vector<LabeledNodeElm>(), new Vector<LabeledNodeElm>(),
                new Vector<LabeledNodeElm>(), new Vector<LabeledNodeElm>()
            };
	    Vector<ExtListEntry> extList = new Vector<ExtListEntry>();
	    boolean sel = isSelection();
	    
	    boolean used[] = new boolean[nodeList.size()];
	    boolean extnodes[] = new boolean[nodeList.size()];
	    
	    // redo node allocation to avoid auto-assigning ground
	    if (!preStampCircuit(true))
		return null;

	    // find all the labeled nodes, get a list of them, and create a node number map
	    for (i = 0; i != elmList.size(); i++) {
		CircuitElm ce = getElm(i);
		if (sel && !ce.isSelected())
		    continue;
		if (ce instanceof LabeledNodeElm) {
		    LabeledNodeElm lne = (LabeledNodeElm) ce;
		    String label = lne.text;
		    if (lne.isInternal())
			continue;
		    
		    // already added to list?
		    if (extnodes[ce.getNode(0)])
			continue;
		    
                    int side = ChipElm.SIDE_W;
                    if (Math.abs(ce.dx) >= Math.abs(ce.dy) && ce.dx > 0) side = ChipElm.SIDE_E;
                    if (Math.abs(ce.dx) <= Math.abs(ce.dy) && ce.dy < 0) side = ChipElm.SIDE_N;
                    if (Math.abs(ce.dx) <= Math.abs(ce.dy) && ce.dy > 0) side = ChipElm.SIDE_S;
                    
		    // create ext list entry for external nodes
                    sideLabels[side].add(lne);
		    extnodes[ce.getNode(0)] = true;
		    if (ce.getNode(0) == 0) {
		        alertOrWarn("Node \"" + lne.text + "\" can't be connected to ground");
			return null;
		    }
		}
	    }
	    
            Collections.sort(sideLabels[ChipElm.SIDE_W], (LabeledNodeElm a, LabeledNodeElm b) -> Integer.signum(a.y - b.y));
            Collections.sort(sideLabels[ChipElm.SIDE_E], (LabeledNodeElm a, LabeledNodeElm b) -> Integer.signum(a.y - b.y));
            Collections.sort(sideLabels[ChipElm.SIDE_N], (LabeledNodeElm a, LabeledNodeElm b) -> Integer.signum(a.x - b.x));
            Collections.sort(sideLabels[ChipElm.SIDE_S], (LabeledNodeElm a, LabeledNodeElm b) -> Integer.signum(a.x - b.x));

            for (int side = 0; side < sideLabels.length; side++) {
                for (int pos = 0; pos < sideLabels[side].size(); pos++) {
                    LabeledNodeElm lne = sideLabels[side].get(pos);
                    ExtListEntry ent = new ExtListEntry(lne.text, lne.getNode(0), pos, side);
                    extList.add(ent);
                }
            }

	    // output all the elements
	    for (i = 0; i != elmList.size(); i++) {
		CircuitElm ce = getElm(i);
		if (sel && !ce.isSelected())
		    continue;
		// don't need these elements dumped
		if (ce instanceof WireElm || ce instanceof LabeledNodeElm || ce instanceof ScopeElm)
		    continue;
		if (ce instanceof GraphicElm || ce instanceof GroundElm)
		    continue;
		int j;
		if (nodeDump.length() > 0)
		    nodeDump += "\r";
		nodeDump += ce.getClass().getSimpleName();
		for (j = 0; j != ce.getPostCount(); j++) {
		    int n = ce.getNode(j);
		    used[n] = true;
		    nodeDump += " " + n;
		}
		
	        // save positions
                int x1 = ce.x;  int y1 = ce.y;
                int x2 = ce.x2; int y2 = ce.y2;
                
                // set them to 0 so they're easy to remove
                ce.x = ce.y = ce.x2 = ce.y2 = 0;

                String tstring = ce.dump();
                tstring = tstring.replaceFirst("[A-Za-z0-9]+ 0 0 0 0 ", ""); // remove unused tint_x1 y1 x2 y2 coords for internal components
                
                // restore positions
                ce.x = x1; ce.y = y1; ce.x2 = x2; ce.y2 = y2;
                if (dump.length() > 0)
                    dump += " ";
                dump += CustomLogicModel.escape(tstring);
	    }
	    
	    for (i = 0; i != extList.size(); i++) {
		ExtListEntry ent = extList.get(i);
		if (!used[ent.node]) {
		    alertOrWarn("Node \"" + ent.name + "\" is not used!");
		    return null;
		}
	    }
	
	    boolean first = true;
	    for (i = 0; i != unconnectedNodes.size(); i++) {
		int q = unconnectedNodes.get(i);
		if (!extnodes[q] && used[q]) {
		    if (nodesWithGroundConnectionCount == 0 && first) {
			first = false;
			continue;
		    }
		    alertOrWarn("Some nodes are unconnected!");
		    return null;
		}
	    }	    

	    CustomCompositeModel ccm = new CustomCompositeModel();
	    ccm.nodeList = nodeDump;
	    ccm.elmDump = dump;
	    ccm.extList = extList;
	    return ccm;
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
     * Build nameToSlot and circuitVariables[] after all stamp() calls have run
     * (so pre-registered computed names are fully populated in ComputedValues).
     * Called from stampCircuit() between the first stamp pass and postStamp().
     */
    void buildCircuitVariableSlots() {
	nameToSlot = new java.util.HashMap<String, Integer>();
	int slot = 0;

	// Labeled nodes first so expression trees can reference physical node voltages
	String[] labeledNames = LabeledNodeElm.getSortedLabeledNodeNames();
	if (labeledNames != null) {
	    for (String name : labeledNames) {
		if (name != null && !nameToSlot.containsKey(name))
		    nameToSlot.put(name, slot++);
	    }
	}

	// All pre-registered and runtime computed names
	java.util.Set<String> allComputedNames = ComputedValues.getAllNames();
	if (allComputedNames != null) {
	    for (String name : allComputedNames) {
		if (name != null && !nameToSlot.containsKey(name))
		    nameToSlot.put(name, slot++);
	    }
	}

	// Parameter names (sliders, PARAM rows) — stored in a separate registry
	// in ComputedValues; getAllNames() does not include them.
	java.util.Set<String> allParamNames = ComputedValues.getAllParameterNames();
	console("[buildSlots] paramNames=" + (allParamNames != null ? allParamNames.toString() : "NULL"));
	if (allParamNames != null) {
	    for (String name : allParamNames) {
		if (name != null && !nameToSlot.containsKey(name))
		    nameToSlot.put(name, slot++);
	    }
	}

	// Build the parallel plain array: slotNames[i] = name at index i.
	// This lets syncAllSlots() iterate a simple array instead of HashMap.entrySet().
	circuitVariables = new double[slot];
	slotNames = new String[slot];
	for (java.util.Map.Entry<String, Integer> e : nameToSlot.entrySet())
	    slotNames[e.getValue()] = e.getKey();

	syncAllSlots();
    }

    /**
     * Synchronise circuitVariables[] from the live simulation state
     * (nodeVoltages[] + ComputedValues current buffer).  Replicates the
     * same resolution-order waterfall as E_NODE_REF so that E_GSLOT eval
     * returns exactly the same value without any HashMap lookups.
     * <p>
     * Called once per subiteration, after applySolvedRightSide() and
     * commitPendingToCurrentValues() have both been applied.
     */
    void syncAllSlots() {
	if (circuitVariables == null || slotNames == null)
	    return;
	// Plain array loop — no HashMap overhead, no iterator allocation.
	for (int s = 0; s < slotNames.length; s++) {
	    String name = slotNames[s];
	    if (name != null)
		circuitVariables[s] = resolveSlotValue(name);
	}
    }

    /**
     * Resolve a single name to its current value using the same priority
     * waterfall as Expr's E_NODE_REF case for CURRENT_CONTEXT.
     */
    double resolveSlotValue(String name) {
	if (equationTableMnaMode) {
	    // Priority 1: PARAM override (parameter names shadow physical nodes)
	    if (ComputedValues.isParameterName(name)) {
		Double v = ComputedValues.getComputedValue(name);
		if (v != null) return v;
	    }
	    // Priority 2: Flow value
	    Double flowVal = ComputedValues.getComputedFlowValue(name);
	    if (flowVal != null) return flowVal;
	    // Priority 3: Labeled node voltage
	    Integer node = LabeledNodeElm.getByName(name);
	    if (node != null && node != 0
		    && nodeVoltages != null && (node - 1) < nodeVoltages.length)
		return nodeVoltages[node - 1];
	    // Priority 4: Generic computed value
	    Double cv = ComputedValues.getComputedValue(name);
	    return cv != null ? cv : 0.0;
	} else {
	    // Pure-computational mode
	    Double cv = ComputedValues.getComputedFlowOrValue(name);
	    return cv != null ? cv : 0.0;
	}
    }

    double getLabeledNodeVoltage(String name) {
	    Integer node = LabeledNodeElm.getByName(name);
	    if (node == null || node == 0)
		return 0;
	    // subtract one because ground is not included in nodeVoltages[]
	    return nodeVoltages[node.intValue()-1];
	}
	
	/**
	 * Get a message listing all unresolved references from expression evaluation.
	 * @return Message string, or null if no unresolved references
	 */
	String getUnresolvedReferencesMessage() {
	    java.util.Vector<String> unresolved = Expr.getUnresolvedReferences();
	    if (unresolved.size() == 0) return null;
	    StringBuilder sb = new StringBuilder("Not found: ");
	    for (int i = 0; i < unresolved.size(); i++) {
		if (i > 0) sb.append(", ");
		sb.append(unresolved.get(i));
	    }
	    return sb.toString();
	}
	
	void setExtVoltage(String name, double v) {
	    int i;
	    for (i = 0; i != elmList.size(); i++) {
		CircuitElm ce = getElm(i);
		if (ce instanceof ExtVoltageElm) {
		    ExtVoltageElm eve = (ExtVoltageElm) ce;
		    if (eve.getName().equals(name))
			eve.setVoltage(v);
		}
	    }
	}

	// ========== SLIDER API METHODS ==========
	
	/**
	 * Find an adjustable slider by its name
	 */
	Adjustable findAdjustableByName(String name) {
	    for (int i = 0; i < adjustables.size(); i++) {
	        Adjustable adj = adjustables.get(i);
	        if (adj.sliderText != null && adj.sliderText.equals(name)) {
	            return adj;
	        }
	    }
	    return null;
	}
	
	/**
	 * Get the current value of a slider by name
	 * @param name The slider name/label
	 * @return The current value, or NaN if not found
	 */
	double getSliderValue(String name) {
	    Adjustable adj = findAdjustableByName(name);
	    if (adj != null) {
	        EditInfo ei = adj.elm.getEditInfo(adj.editItem);
	        if (ei != null) {
	            return ei.value;
	        }
	    }
	    return Double.NaN;
	}
	
	/**
	 * Set the value of a slider by name
	 * @param name The slider name/label
	 * @param value The new value to set
	 * @return true if successful, false if slider not found
	 */
	boolean setSliderValue(String name, double value) {
	    Adjustable adj = findAdjustableByName(name);
	    if (adj != null) {
	        adj.setSliderValue(value);
	        EditInfo ei = adj.elm.getEditInfo(adj.editItem);
	        if (ei != null) {
	            ei.value = value;
	            adj.elm.setEditValue(adj.editItem, ei);
	            analyzeFlag = true;
	            
	            // Update the slider label to show current value
	            if (adj.label != null) {
	                String valueStr = adj.getFormattedValue(ei, value);
	                adj.updateLabelHTML(adj.sliderText, valueStr);
	            }
	            return true;
	        }
	    }
	    return false;
	}
	
	/**
	 * Get list of all slider names in the circuit
	 * @return Array of slider names
	 */
	JsArrayString getJSArrayString() {
	    return JavaScriptObject.createArray().cast();
	}
	
	JsArrayString getSliderNames() {
	    JsArrayString names = getJSArrayString();
	    for (int i = 0; i < adjustables.size(); i++) {
	        Adjustable adj = adjustables.get(i);
	        if (adj.sliderText != null) {
	            names.push(adj.sliderText);
	        }
	    }
	    return names;
	}
	
	// ========== END SLIDER API METHODS ==========

	// ========== LABELED NODE & COMPUTED VALUE API METHODS ==========
	
	/**
	 * Get list of all labeled node names in the circuit
	 * @return Array of labeled node names
	 */
	JsArrayString getLabeledNodeNames() {
	    JsArrayString names = getJSArrayString();
	    java.util.Set<String> nodeNames = LabeledNodeElm.getAllNodeNames();
	    if (nodeNames != null) {
	        for (String name : nodeNames) {
	            names.push(name);
	        }
	    }
	    return names;
	}
	
	/**
	 * Get a labeled node voltage value by name
	 * This combines both regular labeled nodes and computed values
	 * @param name The name of the labeled node or computed value
	 * @return The voltage value, or 0 if not found
	 */
	double getLabeledNodeValue(String name) {
	    // First try computed values (from tables, etc.)
	    Double computed = ComputedValues.getComputedValue(name);
	    if (computed != null) {
	        return computed;
	    }
	    // Fall back to regular labeled node voltage
	    return getLabeledNodeVoltage(name);
	}
	
	/**
	 * Get list of all computed value names in the circuit
	 * @return Array of computed value names
	 */
	JsArrayString getComputedValueNames() {
	    JsArrayString names = getJSArrayString();
	    java.util.Set<String> valueNames = ComputedValues.getAllNames();
	    if (valueNames != null) {
	        for (String name : valueNames) {
	            names.push(name);
	        }
	    }
	    return names;
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
	    return JavaScriptObject.createArray().cast();
	}
	
	JsArray<JavaScriptObject> getJSElements() {
	    int i;
	    JsArray<JavaScriptObject> arr = getJSArray();
	    for (i = 0; i != elmList.size(); i++) {
		CircuitElm ce = getElm(i);
		ce.addJSMethods();
		arr.push(ce.getJavaScriptObject());
	    }
	    return arr;
	}
	
	void setupJSInterface() {
	    final CirSim that = this;
	    CircuitJsApi api = (CircuitJsApi) (Object) JavaScriptObject.createObject();
	    api.setSetSimRunning(new HookBool() { public void call(boolean run) { that.setSimRunning(run); } });
	    api.setReset(new Hook0() { public void call() { that.resetAction(); } });
	    api.setStep(new Hook0() { public void call() { that.stepCircuit(); } });
	    api.setGetTime(new HookNoArgDouble() { public double call() { return that.t; } });
	    api.setGetTimeStep(new HookNoArgDouble() { public double call() { return that.timeStep; } });
	    api.setSetTimeStep(new HookDouble() { public void call(double ts) { that.timeStep = ts; } });
	    api.setGetMaxTimeStep(new HookNoArgDouble() { public double call() { return that.maxTimeStep; } });
	    api.setSetMaxTimeStep(new HookDouble() { public void call(double ts) { that.maxTimeStep = that.timeStep = ts; } });
	    api.setIsRunning(new HookNoArgBoolean() { public boolean call() { return that.simIsRunning(); } });
	    api.setGetNodeVoltage(new HookStringToDouble() { public double call(String n) { return that.getLabeledNodeVoltage(n); } });
	    api.setSetExtVoltage(new HookStringDouble() { public Object call(String n, double v) { that.setExtVoltage(n, v); return null; } });
	    api.setGetElements(new HookNoArgElements() { public JsArray<JavaScriptObject> call() { return that.getJSElements(); } });
	    api.setGetCircuitAsSVG(new HookNoArgString() { public String call() { return that.getCircuitAsSVG(); } });
	    api.setExportCircuit(new HookNoArgString() { public String call() { return that.dumpCircuit(); } });
	    api.setImportCircuit(new HookStringBool() { public void call(String c, boolean s) { that.importCircuitFromText(c, s); } });
	    api.setImportCircuitFromCTZ(new HookStringBool() { public void call(String ctz, boolean s) { that.importCircuitFromCTZ(ctz, s); } });
	    api.setGetSliderValue(new HookStringToDouble() { public double call(String name) { return that.getSliderValue(name); } });
	    api.setSetSliderValue(new HookStringDouble() { public Object call(String name, double value) { return that.setSliderValue(name, value); } });
	    api.setGetSliderNames(new HookNoArgArrayString() { public JsArrayString call() { return that.getSliderNames(); } });
	    api.setGetLabeledNodeNames(new HookNoArgArrayString() { public JsArrayString call() { return that.getLabeledNodeNames(); } });
	    api.setGetLabeledNodeValue(new HookStringToDouble() { public double call(String name) { return that.getLabeledNodeValue(name); } });
	    api.setGetComputedValueNames(new HookNoArgArrayString() { public JsArrayString call() { return that.getComputedValueNames(); } });
	    api.setSetExprPerfProbeEnabled(new HookBool() { public void call(boolean enabled) { that.setExprPerfProbeEnabled(enabled); } });
	    api.setResetExprPerfProbe(new Hook0() { public void call() { that.resetExprPerfProbe(); } });
	    api.setGetExprPerfProbeReport(new HookNoArgString() { public String call() { return that.getExprPerfProbeReport(); } });

	    GlobalWindowLike.setCircuitJS1(api);
	    OnCircuitLoadedHook hook = GlobalWindowLike.getOnCircuitJsLoaded();
	    if (hook != null)
		hook.call(api);
	}
	
	void callUpdateHook() {
	    CircuitJsApi api = GlobalWindowLike.getCircuitJS1();
	    if (api == null)
		return;
	    ApiHook hook = api.getOnUpdate();
	    if (hook != null)
		hook.call(api);
	}
	
		void callAnalyzeHook() {
			CircuitJsApi api = GlobalWindowLike.getCircuitJS1();
			if (api == null)
				return;
			ApiHook hook = api.getOnAnalyze();
			if (hook != null)
				hook.call(api);
	}
    

	void callTimeStepHook() {
	    CircuitJsApi api = GlobalWindowLike.getCircuitJS1();
	    if (api == null)
		return;
	    ApiHook hook = api.getOnTimeStep();
	    if (hook != null)
		hook.call(api);
	}
	
	void callSVGRenderedHook(String svgData) {
		CircuitJsApi api = GlobalWindowLike.getCircuitJS1();
		if (api == null)
			return;
		SvgHook hook = api.getOnSvgRendered();
		if (hook != null)
			hook.call(api, svgData);
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

