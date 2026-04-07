package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.scope.Scope;


import com.lushprojects.circuitjs1.client.util.*;

import java.util.Random;
import java.util.Vector;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.LabelElement;
import com.google.gwt.dom.client.MetaElement;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.CanvasElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ContextMenuEvent;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.storage.client.Storage;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.ClosingEvent;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.RootLayoutPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SplitLayoutPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.lushprojects.circuitjs1.client.elements.economics.ComputedValues;
import com.lushprojects.circuitjs1.client.io.LoadFile;
import com.lushprojects.circuitjs1.client.ui.EconomicsToolbar;
import com.lushprojects.circuitjs1.client.ui.CheckboxMenuItem;
import com.lushprojects.circuitjs1.client.ui.ExportAsLocalFileDialog;
import com.lushprojects.circuitjs1.client.ui.FloatingControlPanel;
import com.lushprojects.circuitjs1.client.ui.ImportFromDropboxDialog;
import com.lushprojects.circuitjs1.client.ui.ScopePopupMenu;
import com.lushprojects.circuitjs1.client.ui.Scrollbar;
import com.lushprojects.circuitjs1.client.util.Locale;

final class CirSimInitializer {
    private final CirSim sim;

    CirSimInitializer(CirSim sim) {
        this.sim = sim;
    }

    public void init() {

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

        CircuitElm.initClass(sim);
        sim.getCircuitIOService().readRecoveryFromStorage();

        QueryParameters qp = new QueryParameters();
        String positiveColor = null;
        String negativeColor = null;
        String neutralColor = null;
        String selectColor = null;
        String currentColor = null;
        String mouseModeReq = null;
        boolean euroGates = false;

        try {
            String cct = qp.getValue("cct");
            if (cct != null)
                sim.startCircuitText = cct.replace("%24", "$");
            if (sim.startCircuitText == null)
                sim.startCircuitText = sim.getPlatformInterop().getElectronStartCircuitText();
            String ctz = qp.getValue("ctz");
            if (ctz != null)
                sim.startCircuitText = sim.decompress(ctz);
            String nonInteractiveDumpKey = qp.getValue("nonInteractiveDumpKey");
            if (sim.startCircuitText == null && nonInteractiveDumpKey != null)
                sim.startCircuitText = sim.getCircuitIOService().getRunnerDumpFromStorage(nonInteractiveDumpKey);
            sim.startCircuit = qp.getValue("startCircuit");
            sim.startLabel = qp.getValue("startLabel");
            sim.startCircuitLink = qp.getValue("startCircuitLink");
            euroRes = qp.getBooleanValue("euroResistors", false);
            euroGates = qp.getBooleanValue("IECGates", sim.getPreferencesManager().getOptionFromStorage("euroGates", sim.getPreferencesManager().weAreInGermany()));
            usRes = qp.getBooleanValue("usResistors", false);
            running = qp.getBooleanValue("running", true);
            hideSidebar = qp.getBooleanValue("hideSidebar", false);
            sim.hideMenu = qp.getBooleanValue("hideMenu", false);
            printable = qp.getBooleanValue("whiteBackground", sim.getPreferencesManager().getOptionFromStorage("whiteBackground", true));
            convention = qp.getBooleanValue("conventionalCurrent", sim.getPreferencesManager().getOptionFromStorage("conventionalCurrent", true));
            noEditing = !qp.getBooleanValue("editable", true);
            mouseWheelEdit = qp.getBooleanValue("mouseWheelEdit", sim.getPreferencesManager().getOptionFromStorage("mouseWheelEdit", true));
            sim.useWeightedPriority = sim.getPreferencesManager().getOptionFromStorage("weightedPriority", false);
            sim.showElectronicsCircuits = sim.getPreferencesManager().getOptionFromStorage("showElectronicsCircuits", false);
            sim.enableCacheBustedUrls = sim.getPreferencesManager().getOptionFromStorage("enableCacheBustedUrls", true);
            sim.tableRenderCacheEnabled = sim.getPreferencesManager().getOptionFromStorage("tableRenderCacheEnabled", true);
            sim.autoOpenModelInfoOnLoad = sim.getPreferencesManager().getOptionFromStorage("autoOpenModelInfoOnLoad", true);
            sim.equationTableNewtonJacobianEnabled = sim.getPreferencesManager().getOptionFromStorage("equationTableNewtonJacobianEnabled", false);
            sim.equationTableBroydenJacobianEnabled = sim.getPreferencesManager().getOptionFromStorage("equationTableBroydenJacobianEnabled", false);
            positiveColor = qp.getValue("positiveColor");
            negativeColor = qp.getValue("negativeColor");
            neutralColor = qp.getValue("neutralColor");
            selectColor = qp.getValue("selectColor");
            currentColor = qp.getValue("currentColor");
            mouseModeReq = qp.getValue("mouseMode");
            sim.hideInfoBox = qp.getBooleanValue("hideInfoBox", false);
        } catch (Exception e) {
        }

        boolean euroSetting = false;
        if (euroRes)
            euroSetting = true;
        else if (usRes)
            euroSetting = false;
        else
            euroSetting = sim.getPreferencesManager().getOptionFromStorage("euroResistors", !sim.getPreferencesManager().weAreInUS(true));

        sim.getViewportController().setTransformRaw(new double[] {1, 0, 0, 1, 0, 0});
        String os = Window.Navigator.getPlatform();
        sim.isMac = (os.toLowerCase().contains("mac"));
        sim.ctrlMetaKey = (sim.isMac) ? Locale.LS("Cmd-") : Locale.LS("Ctrl-");

        sim.shortcuts = new String[127];

        sim.layoutPanel = new SplitLayoutPanel(6);

        sim.getMenuUiState().fileMenuBar = new MenuBar(true);
        if (sim.getPlatformInterop().isElectron())
            sim.getMenuUiState().fileMenuBar.addItem(sim.menuItemWithShortcut("window", "New Window...", Locale.LS(sim.ctrlMetaKey + "N"),
                    new MyCommand("file", "newwindow")));

        sim.getMenuUiState().fileMenuBar.addItem(sim.iconMenuItem("doc-new", "New Blank Circuit", new MyCommand("file", "newblankcircuit")));
        sim.importFromLocalFileItem = sim.menuItemWithShortcut("folder", "Open File...", Locale.LS(sim.ctrlMetaKey + "O"),
                new MyCommand("file", "importfromlocalfile"));
        sim.importFromLocalFileItem.setEnabled(LoadFile.isSupported());
        sim.getMenuUiState().fileMenuBar.addItem(sim.importFromLocalFileItem);
        sim.importFromTextItem = sim.iconMenuItem("doc-text", "Import From Text...", new MyCommand("file", "importfromtext"));
        sim.getMenuUiState().fileMenuBar.addItem(sim.importFromTextItem);
        sim.importFromDropboxItem = sim.iconMenuItem("dropbox", "Import From Dropbox...", new MyCommand("file", "importfromdropbox"));
        sim.getMenuUiState().fileMenuBar.addItem(sim.importFromDropboxItem);
        if (sim.getPlatformInterop().isElectron()) {
            sim.saveFileItem = sim.getMenuUiState().fileMenuBar.addItem(sim.menuItemWithShortcut("floppy", "Save", Locale.LS(sim.ctrlMetaKey + "S"),
                    new MyCommand("file", "save")));
            sim.getMenuUiState().fileMenuBar.addItem(sim.iconMenuItem("floppy", "Save As...", new MyCommand("file", "saveas")));
        } else {
            sim.exportAsLocalFileItem = sim.menuItemWithShortcut("floppy", "Save As...", Locale.LS(sim.ctrlMetaKey + "S"),
                    new MyCommand("file", "exportaslocalfile"));
            sim.exportAsLocalFileItem.setEnabled(ExportAsLocalFileDialog.downloadIsSupported());
            sim.getMenuUiState().fileMenuBar.addItem(sim.exportAsLocalFileItem);
        }
        sim.exportAsUrlItem = sim.iconMenuItem("export", "Export As Link...", new MyCommand("file", "exportasurl"));
        sim.getMenuUiState().fileMenuBar.addItem(sim.exportAsUrlItem);
        sim.getMenuUiState().fileMenuBar.addItem(sim.iconMenuItem("line-chart", "Open Runner Output Table...", new MyCommand("file", "openrunnertable")));
        sim.exportAsTextItem = sim.iconMenuItem("export", "Export As Text...", new MyCommand("file", "exportastext"));
        sim.getMenuUiState().fileMenuBar.addItem(sim.exportAsTextItem);
        sim.getMenuUiState().fileMenuBar.addItem(sim.iconMenuItem("export", "Export As SFCR...", new MyCommand("file", "exportassfcr")));
        sim.editLookupTablesItem = sim.iconMenuItem("table", "Edit Lookup Tables...", new MyCommand("file", "editlookuptables"));
        sim.getMenuUiState().fileMenuBar.addItem(sim.editLookupTablesItem);
        MenuItem viewModelInfoItem = sim.iconMenuItem("doc-text", "View Model Info...", new MyCommand("file", "viewmodelinfo"));
        viewModelInfoItem.setEnabled(false);
        sim.getMenuUiState().fileMenuBar.addItem(viewModelInfoItem);
        sim.getMenuUiState().fileMenuBar.addItem(sim.iconMenuItem("edit", "Open Model Info Editor (LHS)", new MyCommand("file", "viewmodelinfoeditor")));
        sim.getMenuUiState().fileMenuBar.addItem(sim.iconMenuItem("image", "Export As Image...", new MyCommand("file", "exportasimage")));
        sim.getMenuUiState().fileMenuBar.addItem(sim.iconMenuItem("image", "Copy Circuit Image to Clipboard", new MyCommand("file", "copypng")));
        sim.getMenuUiState().fileMenuBar.addItem(sim.iconMenuItem("image", "Export As SVG...", new MyCommand("file", "exportassvg")));
        sim.getMenuUiState().fileMenuBar.addItem(sim.iconMenuItem("microchip", "Create Subcircuit...", new MyCommand("file", "createsubcircuit")));
        sim.getMenuUiState().fileMenuBar.addItem(sim.iconMenuItem("magic", "Find DC Operating Point", new MyCommand("file", "dcanalysis")));
        sim.recoverItem = sim.iconMenuItem("back-in-time", "Recover Auto-Save", new MyCommand("file", "recover"));
        sim.recoverItem.setEnabled(sim.getCircuitIOService().getRecovery() != null);
        sim.getMenuUiState().fileMenuBar.addItem(sim.recoverItem);
        sim.printItem = sim.menuItemWithShortcut("print", "Print...", Locale.LS(sim.ctrlMetaKey + "P"), new MyCommand("file", "print"));
        sim.getMenuUiState().fileMenuBar.addItem(sim.printItem);
        sim.getMenuUiState().fileMenuBar.addSeparator();
        sim.getMenuUiState().fileMenuBar.addItem(sim.iconMenuItem("resize-full-alt", "Toggle Full Screen", new MyCommand("view", "fullscreen")));
        sim.getMenuUiState().fileMenuBar.addSeparator();
        sim.aboutItem = sim.iconMenuItem("info-circled", "About...", (Command) null);
        sim.getMenuUiState().fileMenuBar.addItem(sim.aboutItem);
        sim.aboutItem.setScheduledCommand(new MyCommand("file", "about"));

        int width = (int) RootLayoutPanel.get().getOffsetWidth();
        int defaultPanelWidth = width / 5;
        if (defaultPanelWidth > 166)
            defaultPanelWidth = 166;
        if (defaultPanelWidth < 128)
            defaultPanelWidth = 128;
        final int panelMinWidth = 128;
        final int panelMaxWidth = Math.max(166, width / 2);
        final String rightPanelWidthKey = "rightPanelWidth";
        final String leftPanelWidthKey = "leftPanelWidth";
        final String leftPanelOpenKey = "leftPanelOpen";
        CirSim.VERTICALPANELWIDTH = loadPanelWidthFromStorage(rightPanelWidthKey, defaultPanelWidth, panelMinWidth, panelMaxWidth);
        CirSim.LEFTPANELWIDTH = loadPanelWidthFromStorage(leftPanelWidthKey, defaultPanelWidth, panelMinWidth, panelMaxWidth);
        final boolean leftPanelOpen = sim.getPreferencesManager().getOptionFromStorage(leftPanelOpenKey, false);

        sim.getMenuUiState().menuBar = new MenuBar();
        sim.getMenuUiState().menuBar.addItem(Locale.LS("File"), sim.getMenuUiState().fileMenuBar);
        sim.verticalPanel = new VerticalPanel();

        sim.verticalPanel.getElement().addClassName("verticalPanel");
        sim.verticalPanel.getElement().setId("painel");
        InputElement sidePanelCheckbox = Document.get().createCheckInputElement();
        sim.sidePanelCheckboxLabel = Document.get().createLabelElement();
        sim.sidePanelCheckboxLabel.addClassName("triggerLabel");
        sidePanelCheckbox.setId("trigger");
        sim.sidePanelCheckboxLabel.setAttribute("for", "trigger");
        sidePanelCheckbox.addClassName("trigger");
        sidePanelCheckbox.setChecked(false);
        
        // Left panel setup
        sim.leftPanel = new VerticalPanel();
        sim.leftPanel.getElement().addClassName("leftPanel");
        sim.leftPanel.getElement().setId("leftPainel");
        InputElement leftPanelCheckbox = Document.get().createCheckInputElement();
        sim.leftPanelCheckboxLabel = Document.get().createLabelElement();
        sim.leftPanelCheckboxLabel.addClassName("leftTriggerLabel");
        leftPanelCheckbox.setId("leftTrigger");
        sim.leftPanelCheckboxLabel.setAttribute("for", "leftTrigger");
        leftPanelCheckbox.addClassName("leftTrigger");
        leftPanelCheckbox.setChecked(leftPanelOpen);
        
        InputElement topPanelCheckbox = Document.get().createCheckInputElement();
        LabelElement topPanelCheckboxLabel = Document.get().createLabelElement();
        topPanelCheckbox.setId("toptrigger");
        topPanelCheckbox.addClassName("toptrigger");
        topPanelCheckboxLabel.addClassName("toptriggerlabel");
        topPanelCheckboxLabel.setAttribute("for", "toptrigger");

        sim.buttonPanel = (CirSim.VERTICALPANELWIDTH == 166) ? new HorizontalPanel() : new VerticalPanel();

        m = new MenuBar(true);
        m.addItem(sim.undoItem = sim.menuItemWithShortcut("ccw", "Undo", Locale.LS(sim.ctrlMetaKey + "Z"), new MyCommand("edit", "undo")));
        m.addItem(sim.redoItem = sim.menuItemWithShortcut("cw", "Redo", Locale.LS(sim.ctrlMetaKey + "Y"), new MyCommand("edit", "redo")));
        m.addSeparator();
        m.addItem(sim.cutItem = sim.menuItemWithShortcut("scissors", "Cut", Locale.LS(sim.ctrlMetaKey + "X"), new MyCommand("edit", "cut")));
        m.addItem(sim.copyItem = sim.menuItemWithShortcut("copy", "Copy", Locale.LS(sim.ctrlMetaKey + "C"), new MyCommand("edit", "copy")));
        m.addItem(sim.pasteItem = sim.menuItemWithShortcut("paste", "Paste", Locale.LS(sim.ctrlMetaKey + "V"), new MyCommand("edit", "paste")));
        sim.pasteItem.setEnabled(false);

        m.addItem(sim.menuItemWithShortcut("clone", "Duplicate", Locale.LS(sim.ctrlMetaKey + "D"), new MyCommand("edit", "duplicate")));

        m.addSeparator();
        m.addItem(sim.selectAllItem = sim.menuItemWithShortcut("select-all", "Select All", Locale.LS(sim.ctrlMetaKey + "A"), new MyCommand("edit", "selectAll")));
        m.addSeparator();
        m.addItem(sim.menuItemWithShortcut("search", "Find Component...", "/", new MyCommand("edit", "search")));
        m.addItem(sim.iconMenuItem("target", sim.getPreferencesManager().weAreInUS(false) ? "Center Circuit" : "Centre Circuit", new MyCommand("edit", "centrecircuit")));
        m.addItem(sim.menuItemWithShortcut("zoom-11", "Zoom 100%", "0", new MyCommand("zoom", "zoom100")));
        m.addItem(sim.menuItemWithShortcut("zoom-in", "Zoom In", "+", new MyCommand("zoom", "zoomin")));
        m.addItem(sim.menuItemWithShortcut("zoom-out", "Zoom Out", "-", new MyCommand("zoom", "zoomout")));
        m.addItem(sim.flipXItem = sim.iconMenuItem("flip-x", "Flip X", new MyCommand("edit", "flipx")));
        m.addItem(sim.flipYItem = sim.iconMenuItem("flip-y", "Flip Y", new MyCommand("edit", "flipy")));
        m.addItem(sim.flipXYItem = sim.iconMenuItem("flip-x-y", "Flip XY", new MyCommand("edit", "flipxy")));
        sim.getMenuUiState().menuBar.addItem(Locale.LS("Edit"), m);

        sim.getMenuUiState().drawMenuBar = new MenuBar(true);
        sim.getMenuUiState().drawMenuBar.setAutoOpen(true);

        sim.getMenuUiState().menuBar.addItem(Locale.LS("Draw"), sim.getMenuUiState().drawMenuBar);

        m = new MenuBar(true);
        m.addItem(sim.stackAllItem = sim.iconMenuItem("lines", "Stack All", new MyCommand("scopes", "stackAll")));
        m.addItem(sim.unstackAllItem = sim.iconMenuItem("columns", "Unstack All", new MyCommand("scopes", "unstackAll")));
        m.addItem(sim.combineAllItem = sim.iconMenuItem("object-group", "Combine All", new MyCommand("scopes", "combineAll")));
        m.addItem(sim.separateAllItem = sim.iconMenuItem("object-ungroup", "Separate All", new MyCommand("scopes", "separateAll")));
        m.addSeparator();
        m.addItem(sim.iconMenuItem("line-chart", "View All Scopes in Plotly...", new MyCommand("scopes", "viewAllPlotly")));
        sim.getMenuUiState().menuBar.addItem(Locale.LS("Scopes"), m);

        sim.optionsMenuBar = m = new MenuBar(true);
        sim.getMenuUiState().menuBar.addItem(Locale.LS("Options"), sim.optionsMenuBar);
        m.addItem(sim.dotsCheckItem = new CheckboxMenuItem(Locale.LS("Show Current")));
        sim.dotsCheckItem.setState(true);
        m.addItem(sim.voltsCheckItem = new CheckboxMenuItem(Locale.LS("Show Voltage"),
                new Command() {
                    public void execute() {
                        if (sim.voltsCheckItem.getState())
                            sim.powerCheckItem.setState(false);
                        sim.setPowerBarEnable();
                    }
                }));
        sim.voltsCheckItem.setState(true);
        m.addItem(sim.powerCheckItem = new CheckboxMenuItem(Locale.LS("Show Power"),
                new Command() {
                    public void execute() {
                        if (sim.powerCheckItem.getState())
                            sim.voltsCheckItem.setState(false);
                        sim.setPowerBarEnable();
                    }
                }));
        m.addItem(sim.showValuesCheckItem = new CheckboxMenuItem(Locale.LS("Show Values")));
        sim.showValuesCheckItem.setState(true);

        sim.getMenuUiState().helpMenuBar = new MenuBar(true);
        MenuItem helpViewModelInfoItem = sim.menuItemWithShortcut("doc-text", "View Model Info...", "", new MyCommand("help", "viewmodelinfo"));
        helpViewModelInfoItem.setEnabled(false);
        sim.getMenuUiState().helpMenuBar.addItem(helpViewModelInfoItem);
        sim.getMenuUiState().helpMenuBar.addItem(sim.menuItemWithShortcut("edit", "Open Model Info Editor (LHS)", "", new MyCommand("help", "viewmodelinfoeditor")));
        sim.getSFCRDocumentManager().bindModelInfoMenuItems(viewModelInfoItem, helpViewModelInfoItem);
        sim.getMenuUiState().helpMenuBar.addItem(sim.menuItemWithShortcut("folder", "Reference Docs...", "", new MyCommand("help", "referencedocs")));

        m.addItem(sim.smallGridCheckItem = new CheckboxMenuItem(Locale.LS("Small Grid"),
                new Command() {
                    public void execute() {
                        sim.getPreferencesManager().setGrid();
                    }
                }));
        m.addItem(sim.toolbarCheckItem = new CheckboxMenuItem(Locale.LS("Toolbar"),
                new Command() {
                    public void execute() {
                        sim.setToolbar();
                    }
                }));
        sim.toolbarCheckItem.setState(!sim.hideMenu && !noEditing && !hideSidebar);

        m.addItem(sim.electronicsModeCheckItem = new CheckboxMenuItem(Locale.LS("Electronics Mode"),
                new Command() {
                    public void execute() {
                        if (!sim.electronicsModeCheckItem.getState()) {
                            sim.electronicsModeCheckItem.setState(true);
                            return;
                        }
                        sim.getToolbarModeManager().setElectronicsMode();
                    }
                }));
        sim.electronicsModeCheckItem.setState(sim.currentToolbarType == CirSim.ToolbarType.ELECTRONICS);

        m.addItem(sim.economicsModeCheckItem = new CheckboxMenuItem(Locale.LS("Economics Mode"),
                new Command() {
                    public void execute() {
                        if (!sim.economicsModeCheckItem.getState()) {
                            sim.economicsModeCheckItem.setState(true);
                            return;
                        }
                        sim.getToolbarModeManager().setEconomicsMode();
                    }
                }));
        sim.economicsModeCheckItem.setState(sim.currentToolbarType == CirSim.ToolbarType.ECONOMICS);
        m.addItem(sim.crossHairCheckItem = new CheckboxMenuItem(Locale.LS("Show Cursor Cross Hairs"),
                new Command() {
                    public void execute() {
                        sim.getPreferencesManager().setOptionInStorage("crossHair", sim.crossHairCheckItem.getState());
                    }
                }));
        sim.crossHairCheckItem.setState(sim.getPreferencesManager().getOptionFromStorage("crossHair", false));
        m.addItem(sim.euroResistorCheckItem = new CheckboxMenuItem(Locale.LS("European Resistors"),
                new Command() {
                    public void execute() {
                        sim.getPreferencesManager().setOptionInStorage("euroResistors", sim.euroResistorCheckItem.getState());
                        sim.toolbar.setEuroResistors(sim.euroResistorCheckItem.getState());
                    }
                }));
        sim.euroResistorCheckItem.setState(euroSetting);
        m.addItem(sim.euroGatesCheckItem = new CheckboxMenuItem(Locale.LS("IEC Gates"),
                new Command() {
                    public void execute() {
                        sim.getPreferencesManager().setOptionInStorage("euroGates", sim.euroGatesCheckItem.getState());
                        int i;
                        for (i = 0; i != sim.elmList.size(); i++)
                            sim.getElm(i).setPoints();
                    }
                }));
        sim.euroGatesCheckItem.setState(euroGates);
        m.addItem(sim.printableCheckItem = new CheckboxMenuItem(Locale.LS("White Background"),
                new Command() {
                    public void execute() {
                        int i;
                        for (i = 0; i < sim.scopeCount; i++)
                            sim.scopes[i].setRect(sim.scopes[i].getRectForEmbedded());
                        sim.getPreferencesManager().setOptionInStorage("whiteBackground", sim.printableCheckItem.getState());
                    }
                }));
        sim.printableCheckItem.setState(printable);

        m.addItem(sim.conventionCheckItem = new CheckboxMenuItem(Locale.LS("Conventional Current Motion"),
                new Command() {
                    public void execute() {
                        sim.getPreferencesManager().setOptionInStorage("conventionalCurrent", sim.conventionCheckItem.getState());
                        String cc = CircuitElm.currentColor.getHexValue();
                        if (cc.equals("#ffff00") || cc.equals("#00ffff"))
                            CircuitElm.currentColor = sim.conventionCheckItem.getState() ? Color.yellow : Color.cyan;
                    }
                }));
        sim.conventionCheckItem.setState(convention);
        m.addItem(sim.noEditCheckItem = new CheckboxMenuItem(Locale.LS("Disable Editing")));
        sim.noEditCheckItem.setState(noEditing);

        m.addItem(sim.mouseWheelEditCheckItem = new CheckboxMenuItem(Locale.LS("Edit Values With Mouse Wheel"),
                new Command() {
                    public void execute() {
                        sim.getPreferencesManager().setOptionInStorage("mouseWheelEdit", sim.mouseWheelEditCheckItem.getState());
                    }
                }));
        sim.mouseWheelEditCheckItem.setState(mouseWheelEdit);

        m.addItem(sim.weightedPriorityCheckItem = new CheckboxMenuItem(Locale.LS("Weighted Priority by Type (Asset/Equity +10)"),
                new Command() {
                    public void execute() {
                        sim.useWeightedPriority = sim.weightedPriorityCheckItem.getState();
                        sim.getPreferencesManager().setOptionInStorage("weightedPriority", sim.useWeightedPriority);
                        ComputedValues.clearMasterTables();
                        ComputedValues.clearComputedValues();
                        sim.needAnalyze();
                    }
                }));
        sim.weightedPriorityCheckItem.setState(sim.useWeightedPriority);

        m.addItem(new CheckboxAlignedMenuItem(Locale.LS("Shortcuts..."), new MyCommand("options", "shortcuts")));
        m.addItem(new CheckboxAlignedMenuItem(Locale.LS("Subcircuits..."), new MyCommand("options", "subcircuits")));
        m.addItem(new CheckboxAlignedMenuItem(Locale.LS("Voltage Unit Symbol..."), new MyCommand("options", "voltageunit")));
        m.addItem(new CheckboxAlignedMenuItem(Locale.LS("Element Registry Inference Report"), new MyCommand("options", "elementregistryreport")));
        m.addItem(sim.optionsItem = new CheckboxAlignedMenuItem(Locale.LS("Other Options..."), new MyCommand("options", "other")));
        if (sim.getPlatformInterop().isElectron())
            m.addItem(new CheckboxAlignedMenuItem(Locale.LS("Toggle Dev Tools"), new MyCommand("options", "devtools")));

        sim.getMenuUiState().mainMenuBar = new MenuBar(true);
        sim.getMenuUiState().mainMenuBar.setAutoOpen(true);
        sim.getMenuBuilder().composeMainMenu(sim.getMenuUiState().mainMenuBar, 0);
        sim.getMenuBuilder().composeMainMenu(sim.getMenuUiState().drawMenuBar, 1);
        sim.getPreferencesManager().loadShortcuts();

        sim.layoutPanel.getElement().appendChild(topPanelCheckbox);
        sim.layoutPanel.getElement().appendChild(topPanelCheckboxLabel);

        sim.toolbar = new EconomicsToolbar();
        sim.toolbar.setEuroResistors(euroSetting);
        if (!sim.hideMenu)
            sim.layoutPanel.addNorth(sim.getMenuUiState().menuBar, CirSim.MENUBARHEIGHT);

        if (hideSidebar)
            CirSim.VERTICALPANELWIDTH = 0;
        else {
            sim.layoutPanel.getElement().appendChild(sidePanelCheckbox);
            sim.layoutPanel.getElement().appendChild(sim.sidePanelCheckboxLabel);
            sim.layoutPanel.addEast(sim.verticalPanel, CirSim.VERTICALPANELWIDTH);

            final com.google.gwt.user.client.Element rightCheckboxElement = sidePanelCheckbox.cast();
            DOM.sinkEvents(rightCheckboxElement, Event.ONCHANGE);
            DOM.setEventListener(rightCheckboxElement, new EventListener() {
                public void onBrowserEvent(Event event) {
                    if (event.getTypeInt() == Event.ONCHANGE) {
                        if (sidePanelCheckbox.isChecked()) {
                            sim.layoutPanel.setWidgetSize(sim.verticalPanel, CirSim.VERTICALPANELWIDTH);
                        } else {
                            int currentWidth = clampPanelWidth(sim.verticalPanel.getOffsetWidth(), panelMinWidth, panelMaxWidth);
                            if (currentWidth > 0) {
                                CirSim.VERTICALPANELWIDTH = currentWidth;
                                savePanelWidthToStorage(rightPanelWidthKey, CirSim.VERTICALPANELWIDTH);
                            }
                            sim.layoutPanel.setWidgetSize(sim.verticalPanel, 0);
                        }
                        updateRightPanelTogglePosition(sidePanelCheckbox.isChecked());
                        sim.layoutPanel.forceLayout();
                        if (sim.iFrame != null) {
                            sim.iFrame.setWidth(CirSim.VERTICALPANELWIDTH + "px");
                            sim.getUiPanelManager().setiFrameHeight();
                        }
                        sim.repaint();
                    }
                }
            });

            sim.layoutPanel.setWidgetSize(sim.verticalPanel, 0);
            updateRightPanelTogglePosition(false);
        }
        // Add left panel
        sim.layoutPanel.getElement().appendChild(leftPanelCheckbox);
        sim.layoutPanel.getElement().appendChild(sim.leftPanelCheckboxLabel);
        sim.layoutPanel.addWest(sim.leftPanel, CirSim.LEFTPANELWIDTH);

        final com.google.gwt.user.client.Element leftCheckboxElement = leftPanelCheckbox.cast();
        DOM.sinkEvents(leftCheckboxElement, Event.ONCHANGE);
        DOM.setEventListener(leftCheckboxElement, new EventListener() {
            public void onBrowserEvent(Event event) {
                if (event.getTypeInt() == Event.ONCHANGE) {
                    if (leftPanelCheckbox.isChecked()) {
                        sim.layoutPanel.setWidgetSize(sim.leftPanel, CirSim.LEFTPANELWIDTH);
                    } else {
                        int currentWidth = clampPanelWidth(sim.leftPanel.getOffsetWidth(), panelMinWidth, panelMaxWidth);
                        if (currentWidth > 0) {
                            CirSim.LEFTPANELWIDTH = currentWidth;
                            savePanelWidthToStorage(leftPanelWidthKey, CirSim.LEFTPANELWIDTH);
                        }
                        sim.layoutPanel.setWidgetSize(sim.leftPanel, 0);
                    }
                    sim.getPreferencesManager().setOptionInStorage(leftPanelOpenKey, leftPanelCheckbox.isChecked());
                    updateLeftPanelTogglePosition(leftPanelCheckbox.isChecked());
                    sim.layoutPanel.forceLayout();
                    sim.getViewportController().setCanvasSize();
                    sim.repaint();
                }
            }
        });

        sim.layoutPanel.setWidgetSize(sim.leftPanel, leftPanelOpen ? CirSim.LEFTPANELWIDTH : 0);
        updateLeftPanelTogglePosition(leftPanelOpen);

        final boolean hideSidebarFinal = hideSidebar;

        Event.addNativePreviewHandler(new Event.NativePreviewHandler() {
            public void onPreviewNativeEvent(Event.NativePreviewEvent event) {
                if (event.getTypeInt() != Event.ONMOUSEUP)
                    return;
                boolean anyChange = false;

                if (!hideSidebarFinal && sidePanelCheckbox.isChecked()) {
                    int currentRightWidth = clampPanelWidth(sim.verticalPanel.getOffsetWidth(), panelMinWidth, panelMaxWidth);
                    if (currentRightWidth > 0 && currentRightWidth != CirSim.VERTICALPANELWIDTH) {
                        CirSim.VERTICALPANELWIDTH = currentRightWidth;
                        savePanelWidthToStorage(rightPanelWidthKey, CirSim.VERTICALPANELWIDTH);
                        if (sim.iFrame != null)
                            sim.iFrame.setWidth(CirSim.VERTICALPANELWIDTH + "px");
                        anyChange = true;
                    }
                    updateRightPanelTogglePosition(true);
                }

                if (leftPanelCheckbox.isChecked()) {
                    int currentLeftWidth = clampPanelWidth(sim.leftPanel.getOffsetWidth(), panelMinWidth, panelMaxWidth);
                    if (currentLeftWidth > 0 && currentLeftWidth != CirSim.LEFTPANELWIDTH) {
                        CirSim.LEFTPANELWIDTH = currentLeftWidth;
                        savePanelWidthToStorage(leftPanelWidthKey, CirSim.LEFTPANELWIDTH);
                        anyChange = true;
                    }
                    updateLeftPanelTogglePosition(true);
                }

                if (anyChange && sim.iFrame != null)
                    sim.getUiPanelManager().setiFrameHeight();
            }
        });
        
        sim.layoutPanel.addNorth(sim.toolbar, CirSim.TOOLBARHEIGHT);
        sim.getMenuUiState().menuBar.getElement().insertFirst(sim.getMenuUiState().menuBar.getElement().getChild(1));
        sim.getMenuUiState().menuBar.getElement().getFirstChildElement().setAttribute("onclick", "document.getElementsByClassName('toptrigger')[0].checked = false");
        RootLayoutPanel.get().add(sim.layoutPanel);

        sim.cv = Canvas.createIfSupported();
        if (sim.cv == null) {
            RootPanel.get().add(new Label("Not working. You need a browser that supports the CANVAS element."));
            return;
        }

        Window.addResizeHandler(new ResizeHandler() {
            public void onResize(ResizeEvent event) {
                sim.repaint();
            }
        });

        sim.cvcontext = sim.cv.getContext2d();
        sim.setToolbar();
        sim.layoutPanel.add(sim.cv);
        sim.verticalPanel.add(sim.buttonPanel);

        sim.floatingControlPanel = new FloatingControlPanel(sim);

        if (LoadFile.isSupported())
            sim.verticalPanel.add(sim.loadFileInput = new LoadFile(sim));

        Label l;
        sim.verticalPanel.add(l = new Label(Locale.LS("Simulation Speed")));
        l.addStyleName("topSpace");

        Scrollbar speedBar = new Scrollbar(Scrollbar.HORIZONTAL, 3, 1, 0, 260);
        sim.setSpeedBarForInit(speedBar);
        sim.verticalPanel.add(speedBar);

        sim.verticalPanel.add(l = new Label(Locale.LS("Current Speed")));
        l.addStyleName("topSpace");
        Scrollbar currentBar = new Scrollbar(Scrollbar.HORIZONTAL, 50, 1, 1, 100);
        sim.setCurrentBarForInit(currentBar);
        sim.verticalPanel.add(currentBar);
        Label powerLabel = new Label(Locale.LS("Power Brightness"));
        sim.setPowerLabelForInit(powerLabel);
        sim.verticalPanel.add(powerLabel);
        powerLabel.addStyleName("topSpace");
        Scrollbar powerBar = new Scrollbar(Scrollbar.HORIZONTAL, 50, 1, 1, 100);
        sim.setPowerBarForInit(powerBar);
        sim.verticalPanel.add(powerBar);
        sim.setPowerBarEnable();

        l = new Label(Locale.LS("Current Circuit:"));
        l.addStyleName("topSpace");
        Label titleLabel = new Label("Label");
        sim.setTitleLabelForInit(titleLabel);
        sim.verticalPanel.add(l);
        sim.verticalPanel.add(titleLabel);

        sim.verticalPanel.add(sim.iFrame = new Frame("iframe.html"));
        sim.iFrame.setWidth(CirSim.VERTICALPANELWIDTH + "px");
        sim.iFrame.setHeight("100 px");
        sim.iFrame.getElement().setAttribute("scrolling", "no");

        sim.getPreferencesManager().setGrid();
        sim.elmList = new Vector<CircuitElm>();
        sim.adjustables = new Vector<Adjustable>();

        sim.scopes = new Scope[20];
        sim.scopeColCount = new int[20];
        sim.scopeCount = 0;

        sim.random = new Random();

        sim.getMenuUiState().elmMenuBar = new MenuBar(true);
        sim.getMenuUiState().elmMenuBar.setAutoOpen(true);
        sim.getMenuUiState().selectScopeMenuBar = new MenuBar(true) {
            @Override
            public void onBrowserEvent(Event event) {
                int currentItem = -1;
                EventTarget eventTarget = event.getEventTarget();
                Element targetElement = Element.is(eventTarget) ? Element.as(eventTarget) : null;
                int i;
                for (i = 0; i != sim.getMenuUiState().selectScopeMenuItems.size(); i++) {
                    MenuItem item = sim.getMenuUiState().selectScopeMenuItems.get(i);
                    if (targetElement != null && item.getElement().isOrHasChild(targetElement)) {
                        currentItem = i;
                    }
                }
                switch (event.getTypeInt()) {
                case Event.ONMOUSEOVER:
                    sim.getScopeManager().setScopeMenuSelected(currentItem);
                    break;
                case Event.ONMOUSEOUT:
                    sim.getScopeManager().setScopeMenuSelected(-1);
                    break;
                }
                super.onBrowserEvent(event);
            }
        };

        sim.getMenuUiState().elmMenuBar.addItem(sim.elmEditMenuItem = new MenuItem(Locale.LS("Edit..."), new MyCommand("elm", "edit")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmScopeMenuItem = new MenuItem(Locale.LS("View in New Scope"), new MyCommand("elm", "viewInScope")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmFloatScopeMenuItem = new MenuItem(Locale.LS("View in New Undocked Scope"), new MyCommand("elm", "viewInFloatScope")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmAddScopeMenuItem = new MenuItem(Locale.LS("Add to Existing Scope"), new MyCommand("elm", "addToScope0")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmCutMenuItem = new MenuItem(Locale.LS("Cut"), new MyCommand("elm", "cut")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmCopyMenuItem = new MenuItem(Locale.LS("Copy"), new MyCommand("elm", "copy")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmDeleteMenuItem = new MenuItem(Locale.LS("Delete"), new MyCommand("elm", "delete")));
        sim.getMenuUiState().elmMenuBar.addItem(new MenuItem(Locale.LS("Duplicate"), new MyCommand("elm", "duplicate")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmBringToFrontMenuItem = new MenuItem(Locale.LS("Bring to Front"), new MyCommand("elm", "bringToFront")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmSendToBackMenuItem = new MenuItem(Locale.LS("Send to Back"), new MyCommand("elm", "sendToBack")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmSwapMenuItem = new MenuItem(Locale.LS("Swap Terminals"), new MyCommand("elm", "flip")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmFlipXMenuItem = new MenuItem(Locale.LS("Flip X"), new MyCommand("elm", "flipx")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmFlipYMenuItem = new MenuItem(Locale.LS("Flip Y"), new MyCommand("elm", "flipy")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmFlipXYMenuItem = new MenuItem(Locale.LS("Flip XY"), new MyCommand("elm", "flipxy")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmSplitMenuItem = sim.menuItemWithShortcut("", "Split Wire", Locale.LS(sim.ctrlMetaKey + "click"), new MyCommand("elm", "split")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmSliderMenuItem = new MenuItem(Locale.LS("Sliders..."), new MyCommand("elm", "sliders")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmSankeyMenuItem = new MenuItem(Locale.LS("View Sankey Diagram..."), new MyCommand("elm", "viewSankey")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmDagBlocksMenuItem = new MenuItem(Locale.LS("View DAG Blocks Plot..."), new MyCommand("elm", "viewDagBlocks")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmEquationTableDebugMenuItem = new MenuItem(Locale.LS("View EquationTable Debug Info..."), new MyCommand("elm", "viewEquationTableDebug")));
        sim.getMenuUiState().elmMenuBar.addItem(sim.elmEquationTableReferenceMenuItem = new MenuItem(Locale.LS("View EquationTable Reference..."), new MyCommand("elm", "viewEquationTableReference")));

        sim.getMenuUiState().scopePopupMenu = new ScopePopupMenu();

        sim.getPreferencesManager().setColors(positiveColor, negativeColor, neutralColor, selectColor, currentColor);
        sim.getPreferencesManager().setWheelSensitivity();

        if (sim.startCircuitText != null) {
            CirSim.console("Loading embedded circuit from URL");
            sim.getSetupListLoader().getSetupList(false);
            sim.getCircuitIOService().readCircuit(sim.startCircuitText);
            sim.getSFCRDocumentManager().setCurrentCircuitFile("embedded");
            sim.unsavedChanges = false;
        } else {
            if (sim.stopMessage == null && sim.startCircuitLink != null) {
                sim.getCircuitIOService().readCircuit("");
                sim.getSetupListLoader().getSetupList(false);
                ImportFromDropboxDialog.setSim(sim);
                ImportFromDropboxDialog.doImportDropboxLink(sim.startCircuitLink, false);
            } else {
                sim.getCircuitIOService().readCircuit("");
                if (sim.stopMessage == null && sim.startCircuit != null) {
                    sim.getSetupListLoader().getSetupList(false);
                    sim.getCircuitIOService().readSetupFile(sim.startCircuit, sim.startLabel);
                } else
                    sim.getSetupListLoader().getSetupList(true);
            }
        }

        if (mouseModeReq != null)
	    sim.getCommandRouter().menuPerformed("main", mouseModeReq);

        sim.getUndoRedoManager().enableUndoRedo();
        sim.getClipboardManager().enablePaste();
        sim.enableDisableMenuItems();
        sim.getUiPanelManager().setiFrameHeight();
        sim.cv.addMouseDownHandler(sim.getMouseInputHandler());
        sim.cv.addMouseMoveHandler(sim.getMouseInputHandler());
        sim.cv.addMouseOutHandler(sim.getMouseInputHandler());
        sim.cv.addMouseUpHandler(sim.getMouseInputHandler());
        sim.cv.addClickHandler(sim.getMouseInputHandler());
        sim.cv.addDoubleClickHandler(sim.getMouseInputHandler());
        CirSimPlatformInterop.installTouchHandlers(sim, (CanvasElement) sim.cv.getCanvasElement());
        sim.cv.addDomHandler(sim.getMouseInputHandler(), ContextMenuEvent.getType());
        sim.getMenuUiState().menuBar.addDomHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                sim.getMouseInputHandler().doMainMenuChecks();
            }
        }, ClickEvent.getType());
        Event.addNativePreviewHandler(sim.getMouseInputHandler());
        sim.cv.addMouseWheelHandler(sim.getMouseInputHandler());

        Window.addWindowClosingHandler(new Window.ClosingHandler() {
            public void onWindowClosing(ClosingEvent event) {
                if (sim.unsavedChanges && !sim.getPlatformInterop().isElectron())
                    event.setMessage(Locale.LS("Are you sure?  There are unsaved changes."));
            }
        });
        sim.getJsApiBridge().setupJSInterface();

        sim.setSimRunning(running);

        loadMenuDefinition();
    }

    private int clampPanelWidth(int value, int min, int max) {
        if (value < min)
            return min;
        if (value > max)
            return max;
        return value;
    }

    private int loadPanelWidthFromStorage(String key, int defaultWidth, int min, int max) {
        int fallback = clampPanelWidth(defaultWidth, min, max);
        Storage storage = Storage.getLocalStorageIfSupported();
        if (storage == null)
            return fallback;
        try {
            String value = storage.getItem(key);
            if (value == null || value.isEmpty())
                return fallback;
            int parsed = Integer.parseInt(value);
            return clampPanelWidth(parsed, min, max);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void savePanelWidthToStorage(String key, int width) {
        Storage storage = Storage.getLocalStorageIfSupported();
        if (storage == null)
            return;
        storage.setItem(key, Integer.toString(width));
    }

    private void updateRightPanelTogglePosition(boolean open) {
        if (sim.sidePanelCheckboxLabel == null)
            return;
        sim.sidePanelCheckboxLabel.getStyle().setProperty("right", open ? (CirSim.VERTICALPANELWIDTH + "px") : "0px");
    }

    private void updateLeftPanelTogglePosition(boolean open) {
        if (sim.leftPanelCheckboxLabel == null)
            return;
        sim.leftPanelCheckboxLabel.getStyle().setProperty("left", open ? (CirSim.LEFTPANELWIDTH + "px") : "0px");
    }

    private void loadMenuDefinition() {
        String url = com.google.gwt.core.client.GWT.getModuleBaseURL() + "menulist.txt";
        RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.GET, sim.getCircuitIOService().getLoadUrl(url));
        try {
            requestBuilder.sendRequest(null, new RequestCallback() {
                public void onError(Request request, Throwable exception) {
                    CirSim.console("Warning: Can't load menu definition, using hardcoded menu");
                    com.google.gwt.core.client.GWT.log("Menu definition file error", exception);
                    sim.menuDefinitionLoaded = false;
                }

                public void onResponseReceived(Request request, Response response) {
                    if (response.getStatusCode() == Response.SC_OK) {
                        sim.menuDefinition = response.getText();
                        sim.menuDefinitionLoaded = true;
                        CirSim.console("Menu definition loaded successfully");
                        rebuildMenusFromDefinition();
                    } else {
                        CirSim.console("Warning: Can't load menu definition, using hardcoded menu");
                        com.google.gwt.core.client.GWT.log("Bad menu definition response: " + response.getStatusText());
                        sim.menuDefinitionLoaded = false;
                    }
                }
            });
        } catch (RequestException e) {
            CirSim.console("Warning: Can't load menu definition, using hardcoded menu");
            com.google.gwt.core.client.GWT.log("Failed loading menu definition", e);
            sim.menuDefinitionLoaded = false;
        }
    }

    private void rebuildMenusFromDefinition() {
        sim.getMenuUiState().mainMenuBar.clearItems();
        sim.getMenuUiState().drawMenuBar.clearItems();

        sim.getMenuBuilder().composeMainMenu(sim.getMenuUiState().mainMenuBar, 0);
        sim.getMenuBuilder().composeMainMenu(sim.getMenuUiState().drawMenuBar, 1);

        sim.getMenuBuilder().composeSubcircuitMenu();

        CirSim.console("Menus rebuilt from definition");
    }
}
