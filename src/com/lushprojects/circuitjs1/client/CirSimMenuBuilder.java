package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.util.*;

import com.lushprojects.circuitjs1.client.elements.economics.*;

import java.util.HashMap;
import java.util.Vector;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.lushprojects.circuitjs1.client.registry.ElementFactoryFacade;
import com.lushprojects.circuitjs1.client.ui.CheckboxMenuItem;
import com.lushprojects.circuitjs1.client.util.Locale;

final class CirSimMenuBuilder {
    private final CirSim sim;

    CirSimMenuBuilder(CirSim sim) {
        this.sim = sim;
    }

    void composeMainMenu(MenuBar mainMenuBar, int num) {
        if (sim.menuDefinitionLoaded && sim.menuDefinition != null) {
            composeMainMenuFromFile(mainMenuBar, num);
        } else {
            composeMainMenuHardcoded(mainMenuBar, num);
        }
    }

    void composeMainMenuFromFile(MenuBar mainMenuBar, int num) {
        String[] lines = sim.menuDefinition.split("\n");
        MenuBar currentMenuBar = mainMenuBar;
        MenuBar[] menuStack = new MenuBar[10];
        int stackPtr = 0;
        menuStack[stackPtr++] = mainMenuBar;

        for (String line : lines) {
            line = line.trim();

            if (line.isEmpty() || line.startsWith("#"))
                continue;

            if (line.startsWith("+")) {
                String menuTitle = line.substring(1).trim();
                MenuBar subMenu = new MenuBar(true);

                if (menuTitle.equals("Subcircuits")) {
                    if (sim.getMenuUiState().subcircuitMenuBar == null)
                        sim.getMenuUiState().subcircuitMenuBar = new MenuBar[2];
                    sim.getMenuUiState().subcircuitMenuBar[num] = subMenu;
                }

                currentMenuBar.addItem(SafeHtmlUtils.fromTrustedString(
                    CheckboxMenuItem.checkBoxHtml + Locale.LS("&nbsp;</div>" + menuTitle)), subMenu);
                currentMenuBar = subMenu;
                menuStack[stackPtr++] = subMenu;
                continue;
            }

            if (line.startsWith("-")) {
                stackPtr--;
                currentMenuBar = menuStack[stackPtr - 1];
                continue;
            }

            String[] parts = line.split("\\|", -1);
            if (parts.length < 2)
                continue;

            String className = parts[0].trim();
            String displayName = parts[1].trim();
            String displayShortcut = parts.length > 2 ? parts[2].trim() : "";
            String keyboardShortcut = parts.length > 3 ? parts[3].trim() : "";

            CheckboxMenuItem mi = getClassCheckItem(Locale.LS(displayName), className);
            currentMenuBar.addItem(mi);

            if (displayShortcut != null && !displayShortcut.isEmpty()) {
                if (displayShortcut.contains("(A-M-drag)") && sim.isMac) {
                    displayShortcut = displayShortcut.replace("(A-M-drag)", "(A-Cmd-drag)");
                }
                if (displayShortcut.contains("(Ctrl-drag)") && sim.ctrlMetaKey != null) {
                    displayShortcut = displayShortcut.replace("Ctrl", sim.ctrlMetaKey);
                }
                mi.setShortcut(Locale.LS(displayShortcut));
            }

            if (keyboardShortcut != null && !keyboardShortcut.isEmpty() && keyboardShortcut.length() == 1) {
                char shortcutKey = keyboardShortcut.charAt(0);
                if (sim.shortcuts[shortcutKey] != null && !sim.shortcuts[shortcutKey].equals(className)) {
                    CirSim.console("Warning: Keyboard shortcut '" + shortcutKey + "' already assigned to " + sim.shortcuts[shortcutKey] + ", overriding with " + className);
                }
                sim.shortcuts[shortcutKey] = className;
                if ((displayShortcut == null || displayShortcut.isEmpty()) && mi.getShortcut().isEmpty()) {
                    mi.setShortcut(String.valueOf(shortcutKey));
                }
            }
        }
    }

    void composeMainMenuHardcoded(MenuBar mainMenuBar, int num) {
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Wire"), "WireElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Resistor"), "ResistorElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Multipler"), "MultiplyElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Multiply by Constant"), "MultiplyConstElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Divider"), "DividerElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Percent"), "PercentElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Divide by Constant"), "DivideConstElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Differentiator"), "DifferentiatorElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Integrator"), "IntegratorElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add ODE"), "ODEElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Equation"), "EquationElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Equation Table"), "EquationTableElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Adder"), "AdderElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Subtracter"), "SubtracterElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Table"), "TableElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Godly Table"), "GodlyTableElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add CV Source"), "ComputedValueSourceElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Master Stocks Table"), "StockMasterElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Flows Table"), "FlowsMasterElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Current Transactions Matrix"), "CurrentTransactionsMatrixElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Stop Time"), "StopTimeElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Action Time"), "ActionTimeElm"));
    mainMenuBar.addItem(getClassCheckItem(Locale.LS("Add Scenario"), "ScenarioElm"));
    MenuBar passMenuBar = new MenuBar(true);
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Capacitor"), "CapacitorElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Capacitor (polarized)"), "PolarCapacitorElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Inductor"), "InductorElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Switch"), "SwitchElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Push Switch"), "PushSwitchElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add SPDT Switch"), "Switch2Elm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add DPDT Switch"), "DPDTSwitchElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Make-Before-Break Switch"), "MBBSwitchElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Potentiometer"), "PotElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Transformer"), "TransformerElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Tapped Transformer"), "TappedTransformerElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Custom Transformer"), "CustomTransformerElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Transmission Line"), "TransLineElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Relay"), "RelayElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Relay Coil"), "RelayCoilElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Relay Contact"), "RelayContactElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Photoresistor"), "LDRElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Thermistor"), "ThermistorNTCElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Memristor"), "MemristorElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Spark Gap"), "SparkGapElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Fuse"), "FuseElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Crystal"), "CrystalElm"));
    passMenuBar.addItem(getClassCheckItem(Locale.LS("Add Cross Switch"), "CrossSwitchElm"));
    mainMenuBar.addItem(SafeHtmlUtils.fromTrustedString(CheckboxMenuItem.checkBoxHtml+Locale.LS("&nbsp;</div>Passive Components")), passMenuBar);

    MenuBar inputMenuBar = new MenuBar(true);
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Ground"), "GroundElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Voltage Source (2-terminal)"), "DCVoltageElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add A/C Voltage Source (2-terminal)"), "ACVoltageElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Voltage Source (1-terminal)"), "RailElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add A/C Voltage Source (1-terminal)"), "ACRailElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Square Wave Source (1-terminal)"), "SquareRailElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Clock"), "ClockElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add A/C Sweep"), "SweepElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Variable Voltage"), "VarRailElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Antenna"), "AntennaElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add AM Source"), "AMElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add FM Source"), "FMElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Current Source"), "CurrentElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Noise Generator"), "NoiseElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Audio Input"), "AudioInputElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Data Input"), "DataInputElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add External Voltage (JavaScript)"), "ExtVoltageElm"));
    inputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Table Voltage Source"), "TableVoltageElm"));

    mainMenuBar.addItem(SafeHtmlUtils.fromTrustedString(CheckboxMenuItem.checkBoxHtml+Locale.LS("&nbsp;</div>Inputs and Sources")), inputMenuBar);

    MenuBar outputMenuBar = new MenuBar(true);
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Analog Output"), "OutputElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add LED"), "LEDElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Lamp"), "LampElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Text"), "TextElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Box"), "BoxElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Line"), "LineElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Labeled Node"), "LabeledNodeElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Voltmeter/Scope Probe"), "ProbeElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Ohmmeter"), "OhmMeterElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Ammeter"), "AmmeterElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Wattmeter"), "WattmeterElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Pie Chart"), "PieChartElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Test Point"), "TestPointElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Decimal Display"), "DecimalDisplayElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add LED Array"), "LEDArrayElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Data Export"), "DataRecorderElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Audio Output"), "AudioOutputElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add Stop Trigger"), "StopTriggerElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add DC Motor"), "DCMotorElm"));
    outputMenuBar.addItem(getClassCheckItem(Locale.LS("Add 3-Phase Motor"), "ThreePhaseMotorElm"));
    mainMenuBar.addItem(SafeHtmlUtils.fromTrustedString(CheckboxMenuItem.checkBoxHtml+Locale.LS("&nbsp;</div>Outputs and Labels")), outputMenuBar);

    MenuBar activeMenuBar = new MenuBar(true);
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add Diode"), "DiodeElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add Zener Diode"), "ZenerElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add Transistor (bipolar, NPN)"), "NTransistorElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add Transistor (bipolar, PNP)"), "PTransistorElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add MOSFET (N-Channel)"), "NMosfetElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add MOSFET (P-Channel)"), "PMosfetElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add JFET (N-Channel)"), "NJfetElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add JFET (P-Channel)"), "PJfetElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add SCR"), "SCRElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add DIAC"), "DiacElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add TRIAC"), "TriacElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add Darlington Pair (NPN)"), "NDarlingtonElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add Darlington Pair (PNP)"), "PDarlingtonElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add Varactor/Varicap"), "VaractorElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add Tunnel Diode"), "TunnelDiodeElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add Triode"), "TriodeElm"));
    activeMenuBar.addItem(getClassCheckItem(Locale.LS("Add Unijunction Transistor"), "UnijunctionElm"));
    mainMenuBar.addItem(SafeHtmlUtils.fromTrustedString(CheckboxMenuItem.checkBoxHtml+Locale.LS("&nbsp;</div>Active Components")), activeMenuBar);

    MenuBar activeBlocMenuBar = new MenuBar(true);
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Op Amp (ideal, - on top)"), "OpAmpElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Op Amp (ideal, + on top)"), "OpAmpSwapElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Op Amp (real)"), "OpAmpRealElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Analog Switch (SPST)"), "AnalogSwitchElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Analog Switch (SPDT)"), "AnalogSwitch2Elm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Tristate Buffer"), "TriStateElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Schmitt Trigger"), "SchmittElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Schmitt Trigger (Inverting)"), "InvertingSchmittElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Delay Buffer"), "DelayBufferElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add CCII+"), "CC2Elm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add CCII-"), "CC2NegElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Comparator (Hi-Z/GND output)"), "ComparatorElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add OTA (LM13700 style)"), "OTAElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Voltage-Controlled Voltage Source (VCVS)"), "VCVSElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Voltage-Controlled Current Source (VCCS)"), "VCCSElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Current-Controlled Voltage Source (CCVS)"), "CCVSElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Current-Controlled Current Source (CCCS)"), "CCCSElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Optocoupler"), "OptocouplerElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Time Delay Relay"), "TimeDelayRelayElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add LM317"), "CustomCompositeElm:~LM317-v2"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add TL431"), "CustomCompositeElm:~TL431"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Motor Protection Switch"), "MotorProtectionSwitchElm"));
    activeBlocMenuBar.addItem(getClassCheckItem(Locale.LS("Add Subcircuit Instance"), "CustomCompositeElm"));
    mainMenuBar.addItem(SafeHtmlUtils.fromTrustedString(CheckboxMenuItem.checkBoxHtml+Locale.LS("&nbsp;</div>Active Building Blocks")), activeBlocMenuBar);

    MenuBar gateMenuBar = new MenuBar(true);
    gateMenuBar.addItem(getClassCheckItem(Locale.LS("Add Logic Input"), "LogicInputElm"));
    gateMenuBar.addItem(getClassCheckItem(Locale.LS("Add Logic Output"), "LogicOutputElm"));
    gateMenuBar.addItem(getClassCheckItem(Locale.LS("Add Inverter"), "InverterElm"));
    gateMenuBar.addItem(getClassCheckItem(Locale.LS("Add NAND Gate"), "NandGateElm"));
    gateMenuBar.addItem(getClassCheckItem(Locale.LS("Add NOR Gate"), "NorGateElm"));
    gateMenuBar.addItem(getClassCheckItem(Locale.LS("Add AND Gate"), "AndGateElm"));
    gateMenuBar.addItem(getClassCheckItem(Locale.LS("Add OR Gate"), "OrGateElm"));
    gateMenuBar.addItem(getClassCheckItem(Locale.LS("Add XOR Gate"), "XorGateElm"));
    mainMenuBar.addItem(SafeHtmlUtils.fromTrustedString(CheckboxMenuItem.checkBoxHtml+Locale.LS("&nbsp;</div>Logic Gates, Input and Output")), gateMenuBar);

    MenuBar chipMenuBar = new MenuBar(true);
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add D Flip-Flop"), "DFlipFlopElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add JK Flip-Flop"), "JKFlipFlopElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add T Flip-Flop"), "TFlipFlopElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add 7 Segment LED"), "SevenSegElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add 7 Segment Decoder"), "SevenSegDecoderElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add Multiplexer"), "MultiplexerElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add Demultiplexer"), "DeMultiplexerElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add SIPO shift register"), "SipoShiftElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add PISO shift register"), "PisoShiftElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add Counter"), "CounterElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add Counter w/ Load"), "Counter2Elm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add Ring Counter"), "DecadeElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add Latch"), "LatchElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add Sequence generator"), "SeqGenElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add Adder"), "FullAdderElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add Half Adder"), "HalfAdderElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add Custom Logic"), "UserDefinedLogicElm"));
    chipMenuBar.addItem(getClassCheckItem(Locale.LS("Add Static RAM"), "SRAMElm"));
    mainMenuBar.addItem(SafeHtmlUtils.fromTrustedString(CheckboxMenuItem.checkBoxHtml+Locale.LS("&nbsp;</div>Digital Chips")), chipMenuBar);

    MenuBar achipMenuBar = new MenuBar(true);
    achipMenuBar.addItem(getClassCheckItem(Locale.LS("Add 555 Timer"), "TimerElm"));
    achipMenuBar.addItem(getClassCheckItem(Locale.LS("Add Phase Comparator"), "PhaseCompElm"));
    achipMenuBar.addItem(getClassCheckItem(Locale.LS("Add DAC"), "DACElm"));
    achipMenuBar.addItem(getClassCheckItem(Locale.LS("Add ADC"), "ADCElm"));
    achipMenuBar.addItem(getClassCheckItem(Locale.LS("Add VCO"), "VCOElm"));
    achipMenuBar.addItem(getClassCheckItem(Locale.LS("Add Monostable"), "MonostableElm"));
    mainMenuBar.addItem(SafeHtmlUtils.fromTrustedString(CheckboxMenuItem.checkBoxHtml+Locale.LS("&nbsp;</div>Analog and Hybrid Chips")), achipMenuBar);

    if (sim.getMenuUiState().subcircuitMenuBar == null)
        sim.getMenuUiState().subcircuitMenuBar = new MenuBar[2];
    sim.getMenuUiState().subcircuitMenuBar[num] = new MenuBar(true);
    mainMenuBar.addItem(SafeHtmlUtils.fromTrustedString(CheckboxMenuItem.checkBoxHtml+Locale.LS("&nbsp;</div>Subcircuits")), sim.getMenuUiState().subcircuitMenuBar[num]);

    MenuBar otherMenuBar = new MenuBar(true);
    CheckboxMenuItem mi;
    otherMenuBar.addItem(mi=getClassCheckItem(Locale.LS("Drag All"), "DragAll"));
    mi.setShortcut(Locale.LS("(Alt-drag)"));
    otherMenuBar.addItem(mi=getClassCheckItem(Locale.LS("Drag Row"), "DragRow"));
    mi.setShortcut(Locale.LS("(A-S-drag)"));
    otherMenuBar.addItem(mi=getClassCheckItem(Locale.LS("Drag Column"), "DragColumn"));
    mi.setShortcut(sim.isMac ? Locale.LS("(A-Cmd-drag)") : Locale.LS("(A-M-drag)"));
    otherMenuBar.addItem(getClassCheckItem(Locale.LS("Drag Selected"), "DragSelected"));
    otherMenuBar.addItem(mi=getClassCheckItem(Locale.LS("Drag Post"), "DragPost"));
    mi.setShortcut("(" + sim.ctrlMetaKey + "drag)");

    mainMenuBar.addItem(SafeHtmlUtils.fromTrustedString(CheckboxMenuItem.checkBoxHtml+Locale.LS("&nbsp;</div>Drag")), otherMenuBar);

    mainMenuBar.addItem(mi=getClassCheckItem(Locale.LS("Select/Drag Sel"), "Select"));
    mi.setShortcut(Locale.LS("(space or Shift-drag)"));
    }

    void composeSubcircuitMenu() {
    if (sim.getMenuUiState().subcircuitMenuBar == null)
        return;

    for (int mi = 0; mi != 2; mi++) {
        MenuBar menu = sim.getMenuUiState().subcircuitMenuBar[mi];
        menu.clearItems();
        Vector<CustomCompositeModel> list = CustomCompositeModel.getModelList();
        for (int i = 0; i != list.size(); i++) {
        String name = list.get(i).name;
        menu.addItem(getClassCheckItem(Locale.LS("Add ") + name, "CustomCompositeElm:" + name));
        }
    }
    sim.lastSubcircuitMenuUpdate = CustomCompositeModel.sequenceNumber;
    }

    void composeSelectScopeMenu(MenuBar sb) {
    sb.clearItems();
    sim.getMenuUiState().selectScopeMenuItems = new Vector<MenuItem>();
    for (int i = 0; i < sim.scopeCount; i++) {
        String s;
        String l;
        s = Locale.LS("Scope") + " " + Integer.toString(i + 1);
        l = sim.scopes[i].getScopeMenuName();
        if (l != "")
        s += " (" + SafeHtmlUtils.htmlEscape(l) + ")";
        sim.getMenuUiState().selectScopeMenuItems.add(new MenuItem(s, new MyCommand("elm", "addToScope" + Integer.toString(i))));
    }
    int c = sim.getScopeManager().countScopeElms();
    for (int j = 0; j < c; j++) {
        String s;
        String l;
        s = Locale.LS("Undocked Scope") + " " + Integer.toString(j + 1);
        l = sim.getScopeManager().getNthScopeElm(j).elmScope.getScopeMenuName();
        if (l != "")
        s += " (" + SafeHtmlUtils.htmlEscape(l) + ")";
        sim.getMenuUiState().selectScopeMenuItems
            .add(new MenuItem(s, new MyCommand("elm", "addToScope" + Integer.toString(sim.scopeCount + j))));
    }
    for (MenuItem mi : sim.getMenuUiState().selectScopeMenuItems)
        sb.addItem(mi);
    }

    CheckboxMenuItem getClassCheckItem(String s, String t) {
    if (sim.classToLabelMap == null)
        sim.classToLabelMap = new HashMap<String, String>();
    sim.classToLabelMap.put(t, s);

    String shortcut = "";
    CircuitElm elm = null;
    try {
        elm = ElementFactoryFacade.constructFromClassKey(t, 0, 0);
    } catch (Exception e) {
    }
    CheckboxMenuItem mi;
    if (elm != null) {
        if (elm.needsShortcut()) {
        shortcut += (char) elm.getShortcut();
        if (sim.shortcuts[elm.getShortcut()] != null && !sim.shortcuts[elm.getShortcut()].equals(t))
            CirSim.console("already have shortcut for " + (char) elm.getShortcut() + " " + elm);
        sim.shortcuts[elm.getShortcut()] = t;
        }
        elm.delete();
    }
    if (shortcut == "")
        mi = new CheckboxMenuItem(s);
    else
        mi = new CheckboxMenuItem(s, shortcut);
    mi.setScheduledCommand(new MyCommand("main", t));
    sim.getMenuUiState().mainMenuItems.add(mi);
    sim.getMenuUiState().mainMenuItemNames.add(t);
    return mi;
    }
}
