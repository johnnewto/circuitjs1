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

import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.lushprojects.circuitjs1.client.EquationTableElm.RowOutputMode;

/**
 * EquationTableMarkdownDebugDialog - Debug dialog for inspecting EquationTableElm state
 * 
 * Shows a formatted markdown view of the equation table's internal state including:
 * - Table configuration (name, mode, row count)
 * - Per-row details: output names, equations, classifications, output modes,
 *   current values, slider variables, convergence state
 * - MNA node assignments and voltage source allocations
 * - ComputedValues registry entries for this table
 * - Circuit matrix info
 * 
 * Modeled after TableMarkdownDebugDialog for GodleyTable debugging.
 * 
 * Features:
 * - Resizable text area for viewing large output
 * - Non-modal so it can be kept open while editing
 * - Auto-refresh to update content on demand
 * - Copy to clipboard support
 */
public class EquationTableMarkdownDebugDialog {
    
    private DialogBox dialog;
    private TextArea textArea;
    private EquationTableElm sourceTable;
    private CirSim sim;
    
    /**
     * Create the debug dialog for an EquationTableElm
     * @param sourceTable The equation table to inspect
     */
    public EquationTableMarkdownDebugDialog(EquationTableElm sourceTable) {
        this.sourceTable = sourceTable;
        this.sim = CirSim.theSim;
        createDialog();
    }
    
    /**
     * Create the dialog UI
     */
    private void createDialog() {
        dialog = new DialogBox();
        dialog.setText("Equation Table Debug: " + sourceTable.getTableName());
        dialog.setModal(false);
        dialog.setGlassEnabled(false);
        
        VerticalPanel panel = new VerticalPanel();
        panel.setWidth("800px");
        
        // Text area with markdown content
        textArea = new TextArea();
        textArea.setText(generateMarkdownContent());
        textArea.setWidth("780px");
        textArea.setHeight("500px");
        textArea.setReadOnly(true);
        textArea.getElement().getStyle().setProperty("fontFamily", "monospace");
        textArea.getElement().getStyle().setProperty("fontSize", "11px");
        textArea.getElement().getStyle().setProperty("backgroundColor", "#1e1e1e");
        textArea.getElement().getStyle().setProperty("color", "#d4d4d4");
        textArea.getElement().getStyle().setProperty("padding", "10px");
        
        // Make text area resizable
        makeResizable(textArea.getElement());
        
        panel.add(textArea);
        
        // Buttons
        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setSpacing(5);
        buttonPanel.getElement().getStyle().setProperty("marginTop", "10px");
        
        Button copyButton = new Button("Copy to Clipboard");
        copyButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                textArea.setFocus(true);
                textArea.selectAll();
                copyToClipboard();
                textArea.setSelectionRange(0, 0);
            }
        });
        buttonPanel.add(copyButton);
        
        Button refreshButton = new Button("Refresh");
        refreshButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                refresh();
            }
        });
        buttonPanel.add(refreshButton);
        
        Button viewRenderedButton = new Button("View Rendered");
        viewRenderedButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                showRenderedView();
            }
        });
        buttonPanel.add(viewRenderedButton);
        
        Button closeButton = new Button("Close");
        closeButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                hide();
            }
        });
        buttonPanel.add(closeButton);
        
        panel.add(buttonPanel);
        dialog.setWidget(panel);
        
        addResizableStyles();
    }
    
    public void show() {
        dialog.show();
        dialog.setPopupPosition(
            com.google.gwt.user.client.Window.getClientWidth() - 820,
            20
        );
    }
    
    public void hide() {
        dialog.hide();
    }
    
    public boolean isShowing() {
        return dialog.isShowing();
    }
    
    public void refresh() {
        textArea.setText(generateMarkdownContent());
    }
    
    /**
     * Show the markdown content rendered as HTML in a new browser window.
     * Always opens a fresh window to avoid stale content issues.
     */
    public void showRenderedView() {
        String markdown = generateMarkdownContent();
        String title = "Equation Table Debug: " + sourceTable.getTableName();
        String html = InfoViewerDialog.generateMarkdownViewerHTMLPublic(title, markdown);
        openNewWindow(html);
    }
    
    /**
     * Open HTML content in a truly new browser window (not reusing existing).
     */
    private static native void openNewWindow(String html) /*-{
        // Use _blank to always open a new window
        var newWindow = $wnd.open('', '_blank', 'width=900,height=700');
        if (newWindow) {
            newWindow.document.open();
            newWindow.document.write(html);
            newWindow.document.close();
            newWindow.focus();
        } else {
            alert('Please allow pop-ups for this site to view the rendered debug info.');
        }
    }-*/;
    
    private static native boolean copyToClipboard() /*-{
        return $doc.execCommand('copy');
    }-*/;
    
    // =========================================================================
    // CONTENT GENERATION
    // =========================================================================
    
    private String generateMarkdownContent() {
        StringBuilder md = new StringBuilder();
        
        md.append("# Equation Table Debug View\n\n");
        
        appendTableOverview(md);
        appendCircuitMatrixInfo(md);
        appendRowDetailsTable(md);
        appendPerRowDetails(md);
        appendComputedValuesInfo(md);
        appendLabeledNodeInfo(md);
        appendAllEquationTablesInCircuit(md);
        appendMatrixEquationSummary(md);
        
        return md.toString();
    }
    
    // =========================================================================
    // TABLE OVERVIEW
    // =========================================================================
    
    private void appendTableOverview(StringBuilder md) {
        md.append("## Table Overview\n\n");
        
        md.append("| Property | Value |\n");
        md.append("|----------|-------|\n");
        md.append("| Table Name | ").append(sourceTable.getTableName()).append(" |\n");
        md.append("| Row Count | ").append(sourceTable.getRowCount()).append(" |\n");
        md.append("| MNA Mode | ").append(sourceTable.isMnaMode() ? "**YES** (Electrical)" : "NO (Pure Computational)").append(" |\n");
        md.append("| nonLinear() | ").append(sourceTable.nonLinear() ? "true" : "false").append(" |\n");
        int currentSourceCount = 0;
        for (int row = 0; row < sourceTable.getRowCount(); row++) {
            if (sourceTable.getOutputMode(row) == RowOutputMode.FLOW_MODE)
                currentSourceCount++;
        }
        md.append("| Current Sources | ").append(currentSourceCount).append(" |\n");
        md.append("| Voltage Sources | ").append(sourceTable.getVoltageSourceCount()).append(" |\n");
        md.append("| Internal Nodes | ").append(sourceTable.getInternalNodeCount()).append(" |\n");
        md.append("| Post Count | ").append(sourceTable.getPostCount()).append(" |\n");
        md.append("| Object ID | #").append(System.identityHashCode(sourceTable)).append(" |\n");
        md.append("| Position | (").append(sourceTable.x).append(", ").append(sourceTable.y).append(") |\n");
        md.append("| Dump Type | 266 |\n");
        md.append("\n");
    }
    
    // =========================================================================
    // CIRCUIT MATRIX INFO
    // =========================================================================
    
    private void appendCircuitMatrixInfo(StringBuilder md) {
        md.append("## Circuit Matrix Info\n\n");
        
        if (sim != null) {
            md.append("| Property | Value |\n");
            md.append("|----------|-------|\n");
            md.append("| Matrix Size | ").append(sim.circuitMatrixSize)
              .append(" x ").append(sim.circuitMatrixSize).append(" |\n");
            md.append("| Nodes | ")
              .append(sim.nodeList != null ? sim.nodeList.size() : 0)
              .append(" (including ground) |\n");
            md.append("| Voltage Sources | ").append(sim.voltageSourceCount).append(" |\n");
            md.append("| Time | ").append(sim.t).append(" |\n");
            md.append("| Time Step | ").append(sim.timeStep).append(" |\n");
            md.append("| Sub-iterations | ").append(sim.subIterations).append(" |\n");
            md.append("| EqnTable MNA Mode (global) | ").append(sim.equationTableMnaMode).append(" |\n");
        } else {
            md.append("*(Simulator not available)*\n");
        }
        md.append("\n");
    }
    
    // =========================================================================
    // ROW SUMMARY TABLE
    // =========================================================================
    
    private void appendRowDetailsTable(StringBuilder md) {
        md.append("## Row Summary\n\n");
        
        // Header
        md.append("| # | Output | Mode | Class | Equation | Target | Initial | Value | Slider |\n");
        md.append("|---|--------|------|-------|----------|--------|---------|-------|--------|\n");
        
        int rowCount = sourceTable.getRowCount();
        for (int row = 0; row < rowCount; row++) {
            String outputName = sourceTable.getOutputName(row);
            String equation = sourceTable.getEquation(row);
            String initialEq = sourceTable.getInitialEquation(row);
            RowOutputMode mode = sourceTable.getOutputMode(row);
            String classification = sourceTable.getRowClassification(row);
            double value = sourceTable.getOutputValue(row);
            String sliderVar = sourceTable.getSliderVarName(row);
            double sliderVal = sourceTable.getSliderValue(row);
            String target = sourceTable.getTargetNodeName(row);
            
            // Mode icon
            String modeStr;
            switch (mode) {
                case FLOW_MODE: modeStr = "FLOW"; break;
                case SECTOR_MODE: modeStr = "SECTOR"; break;
                default: modeStr = "VOLTAGE"; break;
            }
            
            // Classification icon
            String classIcon;
            if ("alias".equals(classification)) classIcon = "-> alias";
            else classIcon = "~ dynamic";
            
            // Format value
            String valueStr = CircuitElm.getShortUnitText(value, "");
            
            // Initial equation (wrap for KaTeX if needed)
            String initStr = (initialEq != null && !initialEq.trim().isEmpty()) ? wrapForKaTeX(initialEq) : "-";
            
            // Target node (wrap for KaTeX if needed)
            String targetStr = (target != null && !target.trim().isEmpty()) ? wrapForKaTeX(target) : "-";
            
            // Output and equation wrapped for KaTeX
            String outputWrapped = wrapForKaTeX(outputName);
            String equationWrapped = wrapForKaTeX(truncate(equation, 30));
            
            md.append("| ").append(row)
              .append(" | ").append(outputWrapped)
              .append(" | ").append(modeStr)
              .append(" | ").append(classIcon)
              .append(" | ").append(equationWrapped)
              .append(" | ").append(targetStr)
              .append(" | ").append(initStr)
              .append(" | ").append(valueStr)
              .append(" | ").append(sliderVar).append("=").append(sliderVal)
              .append(" |\n");
        }
        md.append("\n");
    }
    
    // =========================================================================
    // PER-ROW DETAILED INFO
    // =========================================================================
    
    private void appendPerRowDetails(StringBuilder md) {
        md.append("## Per-Row Details\n\n");
        
        int rowCount = sourceTable.getRowCount();
        for (int row = 0; row < rowCount; row++) {
            String outputName = sourceTable.getOutputName(row);
            String equation = sourceTable.getEquation(row);
            String initialEq = sourceTable.getInitialEquation(row);
            RowOutputMode mode = sourceTable.getOutputMode(row);
            String classification = sourceTable.getRowClassification(row);
            double value = sourceTable.getOutputValue(row);
            String sliderVar = sourceTable.getSliderVarName(row);
            double sliderVal = sourceTable.getSliderValue(row);
            
            md.append("### Row ").append(row).append(": ").append(wrapForKaTeX(outputName)).append("\n\n");
            
            // Mode description
            String modeDesc;
            switch (mode) {
                case FLOW_MODE:
                    String target = sourceTable.getTargetNodeName(row);
                    modeDesc = "FLOW_MODE: current source from " + wrapForKaTeX(outputName) + " to " + 
                        (target != null && !target.isEmpty() ? wrapForKaTeX(target) : "(none)");
                    break;
                case SECTOR_MODE:
                    String sTarget = sourceTable.getTargetNodeName(row);
                    double cap = sourceTable.getCapacitance(row);
                    modeDesc = "SECTOR_MODE: companion model, C=" + cap + 
                        ", target=" + (sTarget != null && !sTarget.isEmpty() ? wrapForKaTeX(sTarget) : "gnd");
                    break;
                default:
                    modeDesc = "VOLTAGE_MODE: drives labeled node via voltage source";
                    break;
            }
            
            md.append("- **Mode:** ").append(modeDesc).append("\n");
            md.append("- **Classification:** ").append(classification).append("\n");
            md.append("- **Equation:** ").append(wrapForKaTeX(equation)).append("\n");
            
            if (initialEq != null && !initialEq.trim().isEmpty()) {
                md.append("- **Initial Equation (t=0):** ").append(wrapForKaTeX(initialEq)).append("\n");
            }
            
            md.append("- **Current Value:** ").append(CircuitElm.getUnitText(value, "")).append("\n");
            md.append("- **Slider:** ").append(wrapForKaTeX(sliderVar)).append(" = ").append(sliderVal).append("\n");
            
            // Hint from HintRegistry
            String hint = HintRegistry.getHint(outputName);
            if (hint != null && !hint.trim().isEmpty()) {
                md.append("- **Hint:** ").append(hint).append("\n");
            }
            
            // ComputedValues lookup
            Double cv = ComputedValues.getComputedValue(outputName);
            if (cv != null) {
                md.append("- **ComputedValues[").append(wrapForKaTeX(outputName)).append("]:** ").append(cv).append("\n");
            } else {
                md.append("- **ComputedValues[").append(wrapForKaTeX(outputName)).append("]:** *(not registered)*\n");
            }
            
            // Labeled node lookup
            if (sourceTable.isMnaMode()) {
                Integer nodeNum = LabeledNodeElm.getByName(outputName);
                if (nodeNum != null && nodeNum >= 0) {
                    md.append("- **LabeledNode:** node #").append(nodeNum).append("\n");
                    // Try to read voltage from sim
                    double nodeVoltage = sim.getLabeledNodeVoltage(outputName);
                    md.append("- **Node Voltage:** ").append(CircuitElm.getUnitText(nodeVoltage, "V")).append("\n");
                } else {
                    md.append("- **LabeledNode:** *(not found)*\n");
                }
                
                // For FLOW_MODE/SECTOR_MODE, show target node info too
                if (mode == RowOutputMode.FLOW_MODE || mode == RowOutputMode.SECTOR_MODE) {
                    String target = sourceTable.getTargetNodeName(row);
                    if (target != null && !target.trim().isEmpty() && !target.equalsIgnoreCase("gnd")) {
                        Integer targetNode = LabeledNodeElm.getByName(target.trim());
                        if (targetNode != null && targetNode >= 0) {
                            md.append("- **Target LabeledNode[").append(wrapForKaTeX(target)).append("]:** node #").append(targetNode).append("\n");
                            md.append("- **Target Voltage:** ").append(
                                CircuitElm.getUnitText(sim.getLabeledNodeVoltage(target.trim()), "V")).append("\n");
                        } else {
                            md.append("- **Target LabeledNode[").append(wrapForKaTeX(target)).append("]:** *(not found)*\n");
                        }
                    } else {
                        md.append("- **Target:** ground (0V)\n");
                    }
                }
            }
            
            md.append("\n");
        }
    }
    
    // =========================================================================
    // COMPUTED VALUES REGISTRY
    // =========================================================================
    
    private void appendComputedValuesInfo(StringBuilder md) {
        md.append("## ComputedValues Registry (this table's outputs)\n\n");
        
        md.append("| Output Name | ComputedValues | Match |\n");
        md.append("|-------------|----------------|-------|\n");
        
        int rowCount = sourceTable.getRowCount();
        for (int row = 0; row < rowCount; row++) {
            String name = sourceTable.getOutputName(row);
            double tableValue = sourceTable.getOutputValue(row);
            Double cvValue = ComputedValues.getComputedValue(name);
            
            String cvStr = (cvValue != null) ? CircuitElm.getShortUnitText(cvValue, "") : "*(missing)*";
            String tableStr = CircuitElm.getShortUnitText(tableValue, "");
            
            boolean match = (cvValue != null && Math.abs(cvValue - tableValue) < 1e-10);
            String matchStr = (cvValue == null) ? "N/A" : (match ? "OK" : "MISMATCH");
            
            md.append("| ").append(wrapForKaTeX(name)).append(" | ").append(cvStr)
              .append(" (table: ").append(tableStr).append(")")
              .append(" | ").append(matchStr)
              .append(" |\n");
        }
        
        // Also show slider variables in ComputedValues
        md.append("\n**Slider Variables in ComputedValues:**\n\n");
        for (int row = 0; row < rowCount; row++) {
            String sliderVar = sourceTable.getSliderVarName(row);
            if (sliderVar != null && !sliderVar.isEmpty()) {
                Double sv = ComputedValues.getComputedValue(sliderVar);
                md.append("- ").append(wrapForKaTeX(sliderVar)).append(": ");
                if (sv != null) {
                    md.append(sv).append(" (slider setting: ").append(sourceTable.getSliderValue(row)).append(")\n");
                } else {
                    md.append("*(not registered)*\n");
                }
            }
        }
        md.append("\n");
    }
    
    // =========================================================================
    // LABELED NODE INFO
    // =========================================================================
    
    private void appendLabeledNodeInfo(StringBuilder md) {
        if (!sourceTable.isMnaMode()) {
            md.append("## Labeled Nodes\n\n*(Pure computational mode - no labeled nodes)*\n\n");
            return;
        }
        
        md.append("## Labeled Nodes (for this table's outputs)\n\n");
        
        md.append("| Output Name | Node # | Voltage | Is Alias |\n");
        md.append("|-------------|--------|---------|----------|\n");
        
        int rowCount = sourceTable.getRowCount();
        for (int row = 0; row < rowCount; row++) {
            String name = sourceTable.getOutputName(row);
            boolean isAlias = sourceTable.isAliasRow(row);
            
            Integer nodeNum = LabeledNodeElm.getByName(name);
            String nodeStr = (nodeNum != null && nodeNum >= 0) ? "#" + nodeNum : "*(none)*";
            String voltStr = (nodeNum != null && nodeNum >= 0) ? 
                CircuitElm.getShortUnitText(sim.getLabeledNodeVoltage(name), "V") : "-";
            
            md.append("| ").append(wrapForKaTeX(name)).append(" | ").append(nodeStr)
              .append(" | ").append(voltStr)
              .append(" | ").append(isAlias ? "YES" : "no")
              .append(" |\n");
        }
        md.append("\n");
    }
    
    // =========================================================================
    // ALL EQUATION TABLES IN CIRCUIT
    // =========================================================================
    
    private void appendAllEquationTablesInCircuit(StringBuilder md) {
        md.append("## All EquationTableElm in Circuit\n\n");
        
        if (sim == null || sim.elmList == null) {
            md.append("*(No circuit elements)*\n\n");
            return;
        }
        
        int count = 0;
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.elmList.get(i);
            if (ce instanceof EquationTableElm) {
                EquationTableElm eqt = (EquationTableElm) ce;
                boolean isCurrent = (eqt == sourceTable);
                
                md.append("- ");
                if (isCurrent) md.append("**[THIS] ");
                md.append(eqt.getTableName());
                if (isCurrent) md.append("**");
                md.append(" (").append(eqt.getRowCount()).append(" rows, #")
                  .append(System.identityHashCode(eqt)).append(")");
                
                // List output names
                md.append(" → outputs: ");
                for (int row = 0; row < eqt.getRowCount(); row++) {
                    if (row > 0) md.append(", ");
                    md.append(wrapForKaTeX(eqt.getOutputName(row)));
                }
                md.append("\n");
                count++;
            }
        }
        
        if (count == 0) {
            md.append("*(None found)*\n");
        }
        md.append("\n");
    }
    
    // =========================================================================
    // MATRIX EQUATION SUMMARY  (X = A^-1 B)
    // =========================================================================
    
    /**
     * Append the MNA matrix equation summary.
     * Following INTERNALS.md "The Matrix Equation":
     *   A is the admittance matrix (rows = nodes + voltage sources)
     *   B is the right-side vector (current sources + voltage source values)
     *   X is the solution vector (node voltages + voltage source currents)
     *
     * Rows 0..(nodeCount-2) correspond to circuit nodes 1..N (node 0 = ground, omitted).
     * Rows (nodeCount-1).. correspond to voltage source constraint equations.
     */
    private void appendMatrixEquationSummary(StringBuilder md) {
        md.append("## Matrix Equation: X = A\u207B\u00B9 B\n\n");
        
        if (sim == null || sim.circuitMatrix == null) {
            md.append("*(Matrix not available — circuit may not be analyzed yet)*\n\n");
            return;
        }
        
        int matSize = sim.circuitMatrixSize;
        int fullSize = sim.circuitMatrixFullSize;
        int nodeCount = (sim.nodeList != null) ? sim.nodeList.size() : 0;
        int vsCount = sim.voltageSourceCount;
        
        md.append("Matrix dimensions: **").append(matSize).append(" x ").append(matSize).append("**");
        if (matSize != fullSize)
            md.append(" (simplified from ").append(fullSize).append(" x ").append(fullSize).append(")");
        md.append("\n\n");
        
        md.append("Composition: **").append(nodeCount - 1).append("** node rows (nodes 1..").append(nodeCount - 1)
          .append(", ground node 0 omitted) + **").append(vsCount).append("** voltage source rows\n\n");
        
        // Build row/column labels for the full-size matrix
        String[] rowLabels = buildMatrixRowLabels(fullSize, nodeCount, vsCount);
        
        // --- A matrix ---
        appendMatrixA(md, matSize, fullSize, rowLabels);
        
        // --- X solution vector and B right-side vector side by side ---
        appendVectorsXB(md, matSize, fullSize, nodeCount, rowLabels);
    }
    
    /**
     * Build human-readable labels for each row/column of the full-size matrix.
     */
    private String[] buildMatrixRowLabels(int fullSize, int nodeCount, int vsCount) {
        String[] labels = new String[fullSize];
        for (int i = 0; i < fullSize; i++) {
            if (i < nodeCount - 1) {
                // Node row: row i corresponds to node (i+1)
                int nodeNum = i + 1;
                String desc = "n" + nodeNum;
                // Try to find the node name
                String nodeName = getNodeName(nodeNum);
                if (nodeName != null) {
                    desc += " (" + wrapForKaTeX(nodeName);
                    // Check if internal
                    if (sim.nodeList != null && nodeNum < sim.nodeList.size()) {
                        CircuitNode cn = sim.getCircuitNode(nodeNum);
                        if (cn != null && cn.internal)
                            desc += ",int";
                    }
                    desc += ")";
                }
                labels[i] = desc;
            } else {
                // Voltage source row
                int vsNum = i - (nodeCount - 1);
                String desc = "vs" + vsNum;
                // Find which element owns this voltage source and get its name
                if (sim.voltageSources != null && vsNum < vsCount) {
                    CircuitElm owner = sim.voltageSources[vsNum];
                    if (owner != null) {
                        String ownerName = getElementOutputName(owner, vsNum);
                        desc += " (" + wrapForKaTeX(ownerName) + ")";
                    }
                }
                labels[i] = desc;
            }
        }
        return labels;
    }
    
    /**
     * Wrap text in $...$ for KaTeX rendering if it contains Greek symbols or subscripts.
     * Greek symbols: \alpha, \beta, etc.
     * Subscripts: var_1, X_t
     * Superscripts: x^2
     */
    private String wrapForKaTeX(String text) {
        if (text == null) return "";
        // Check if text contains LaTeX-style notations that need KaTeX rendering
        if (text.contains("\\") || text.contains("_") || text.contains("^")) {
            return "$" + text + "$";
        }
        return text;
    }
    
    /**
     * Get a human-readable name for a circuit node.
     * Tries LabeledNodeElm registry first, then checks element types.
     */
    private String getNodeName(int nodeNum) {
        // First try the LabeledNodeElm registry
        String labelName = LabeledNodeElm.getNameByNode(nodeNum);
        if (labelName != null) {
            return labelName;
        }
        
        // Otherwise, look at what's connected and try to get a name
        if (sim.nodeList == null || nodeNum >= sim.nodeList.size()) {
            return null;
        }
        
        CircuitNode cn = sim.getCircuitNode(nodeNum);
        if (cn == null || cn.links.size() == 0) {
            return null;
        }
        
        // Check first connected element for a name
        CircuitNodeLink firstLink = cn.links.elementAt(0);
        CircuitElm elm = firstLink.elm;
        
        if (elm instanceof EquationTableElm) {
            EquationTableElm eqt = (EquationTableElm) elm;
            // Try to find which row this node corresponds to
            String outputName = findEquationTableOutputForNode(eqt, nodeNum);
            if (outputName != null) {
                return outputName;
            }
        } else if (elm instanceof LabeledNodeElm) {
            return ((LabeledNodeElm) elm).getName();
        } else if (elm instanceof SFCStockElm) {
            return ((SFCStockElm) elm).getStockName() + ", SFCStock";
        } else if (elm instanceof SFCFlowElm) {
            return ((SFCFlowElm) elm).getFlowName() + ", SFCFlow";
        }
        
        // Fallback to element class name
        return elm.getClass().getSimpleName().replace("Elm", "");
    }
    
    /**
     * Find the output name for an EquationTableElm internal node.
     */
    private String findEquationTableOutputForNode(EquationTableElm eqt, int nodeNum) {
        // Check each row's output
        for (int row = 0; row < eqt.getRowCount(); row++) {
            String outputName = eqt.getOutputName(row);
            if (outputName != null && !outputName.isEmpty()) {
                // Check if this row's target matches the node
                Integer targetNode = LabeledNodeElm.getByName(outputName.trim());
                if (targetNode != null && targetNode == nodeNum) {
                    return outputName;
                }
            }
        }
        return null;
    }
    
    /**
     * Get a human-readable name for a voltage source owner element.
     */
    private String getElementOutputName(CircuitElm owner, int vsNum) {
        if (owner instanceof EquationTableElm) {
            EquationTableElm eqt = (EquationTableElm) owner;
            // The VS index within this element
            int localVs = vsNum - owner.voltSource;
            // Try to find which row this corresponds to
            int vsIdx = 0;
            for (int row = 0; row < eqt.getRowCount(); row++) {
                RowOutputMode mode = eqt.getOutputMode(row);
                // VOLTAGE_MODE and SECTOR_MODE rows use voltage sources
                if (mode == RowOutputMode.VOLTAGE_MODE || mode == RowOutputMode.SECTOR_MODE) {
                    if (!eqt.isAliasRow(row)) {
                        if (vsIdx == localVs) {
                            String outputName = eqt.getOutputName(row);
                            if (outputName != null && !outputName.isEmpty()) {
                                return outputName;
                            }
                        }
                        vsIdx++;
                    }
                }
            }
        } else if (owner instanceof LabeledNodeElm) {
            return ((LabeledNodeElm) owner).getName();
        }
        // Fallback to class name
        return owner.getClass().getSimpleName().replace("Elm", "");
    }
    
    /**
     * Append the A matrix (admittance matrix) in table form.
     * Uses origMatrix (stamped values before doStep modifications) when available.
     */
    private void appendMatrixA(StringBuilder md, int matSize, int fullSize, String[] rowLabels) {
        md.append("### A Matrix (Admittance)\n\n");
        
        if (matSize > 30) {
            md.append("*(Matrix too large to display: ").append(matSize).append(" x ").append(matSize).append(")*\n\n");
            return;
        }
        
        // Use origMatrix if available (shows stamped values), otherwise circuitMatrix
        double[][] matrix = (sim.origMatrix != null) ? sim.origMatrix : sim.circuitMatrix;
        
        // If matrix was simplified, we show the simplified matrix with mapped labels
        int displaySize = matSize;
        
        // Header row: | | col0 | col1 | ... |
        md.append("| ");
        // Column for row labels
        md.append(" | ");
        for (int col = 0; col < displaySize; col++) {
            String colLabel = getSimplifiedLabel(col, fullSize, rowLabels);
            md.append(colLabel).append(" | ");
        }
        md.append("\n");
        
        // Separator
        md.append("|---|");
        for (int col = 0; col < displaySize; col++) {
            md.append("---|");
        }
        md.append("\n");
        
        // Data rows
        for (int row = 0; row < displaySize; row++) {
            String rowLabel = getSimplifiedLabel(row, fullSize, rowLabels);
            md.append("| **").append(rowLabel).append("** | ");
            for (int col = 0; col < displaySize; col++) {
                double val = matrix[row][col];
                md.append(formatMatrixValue(val)).append(" | ");
            }
            md.append("\n");
        }
        md.append("\n");
    }
    
    /**
     * Append X (solution) and B (right-side) vectors in a combined table.
     *
     * X = solution vector: node voltages followed by voltage source currents.
     *     Built from nodeVoltages[] and voltage source element currents,
     *     NOT from circuitRightSide (which holds B after nonlinear convergence).
     * B = right-side vector: current source contributions for node rows,
     *     voltage source values for VS rows. After convergence break,
     *     circuitRightSide holds the full B (origRightSide + doStep stamps).
     */
    private void appendVectorsXB(StringBuilder md, int matSize, int fullSize, int nodeCount, String[] rowLabels) {
        md.append("### X (Solution) and B (Right Side) Vectors\n\n");
        md.append("| Row | Label | Meaning | X (solution) | B (right side) |\n");
        md.append("|-----|-------|---------|-------------|----------------|\n");
        
        // B vector: circuitRightSide holds the full B (origRightSide + doStep stamps)
        // after convergence, because the subiteration loop breaks BEFORE lu_solve.
        double[] bVec = sim.circuitRightSide;
        
        // Build X from authoritative sources: nodeVoltages[] and VS element currents
        // (circuitRightSide is NOT the solution — it holds B after convergence break)
        double[] xVec = buildSolutionVector(matSize, fullSize, nodeCount);
        
        for (int i = 0; i < matSize; i++) {
            String label = getSimplifiedLabel(i, fullSize, rowLabels);
            
            // Determine meaning of this row in the solution
            String meaning = getRowMeaning(i, fullSize, nodeCount);
            
            double xVal = (xVec != null && i < xVec.length) ? xVec[i] : 0;
            double bVal = (bVec != null && i < bVec.length) ? bVec[i] : 0;
            
            md.append("| ").append(i)
              .append(" | ").append(label)
              .append(" | ").append(meaning)
              .append(" | ").append(formatMatrixValue(xVal))
              .append(" | ").append(formatMatrixValue(bVal))
              .append(" |\n");
        }
        md.append("\n");
        
        md.append("**Legend:** X contains node voltages (V) and VS currents (A). "
                + "B contains independent current source values (A) for node rows "
                + "and voltage source values (V) for VS rows.\n\n");
    }
    
    /**
     * Build the solution vector X from nodeVoltages[] and voltage source currents.
     *
     * circuitRightSide cannot be used for X because for nonlinear circuits,
     * the subiteration loop breaks BEFORE lu_solve when convergence is detected,
     * leaving circuitRightSide holding B (not X). The actual solution is stored
     * in nodeVoltages[] (for node rows) and element.current (for VS rows) by
     * applySolvedRightSide() from the previous subiteration.
     */
    private double[] buildSolutionVector(int matSize, int fullSize, int nodeCount) {
        double[] xVec = new double[matSize];
        
        if (sim.circuitRowInfo == null)
            return xVec;
        
        for (int j = 0; j < fullSize && j < sim.circuitRowInfo.length; j++) {
            RowInfo ri = sim.circuitRowInfo[j];
            int mappedIdx = sim.circuitNeedsMap ? ri.mapCol : j;
            if (mappedIdx < 0 || mappedIdx >= matSize)
                continue;
            
            double val = 0;
            if (ri.type == RowInfo.ROW_CONST) {
                val = ri.value;
            } else if (j < nodeCount - 1) {
                // Node row: read from nodeVoltages[]
                if (sim.nodeVoltages != null && j < sim.nodeVoltages.length)
                    val = sim.nodeVoltages[j];
            } else {
                // Voltage source row: read current from the VS owner element
                int vsIdx = j - (nodeCount - 1);
                if (sim.voltageSources != null && vsIdx < sim.voltageSourceCount) {
                    CircuitElm owner = sim.voltageSources[vsIdx];
                    if (owner != null)
                        val = owner.getCurrent();
                }
            }
            
            xVec[mappedIdx] = val;
        }
        
        return xVec;
    }
    
    /**
     * Get a label for a simplified-matrix row, mapping back through circuitRowInfo if needed.
     */
    private String getSimplifiedLabel(int simplifiedIdx, int fullSize, String[] rowLabels) {
        if (sim.circuitRowInfo != null && sim.circuitNeedsMap) {
            // Map simplified row back to original row
            for (int i = 0; i < fullSize && i < sim.circuitRowInfo.length; i++) {
                if (sim.circuitRowInfo[i].mapRow == simplifiedIdx) {
                    return (i < rowLabels.length) ? rowLabels[i] : ("r" + i);
                }
            }
        }
        // No mapping or direct correspondence
        return (simplifiedIdx < rowLabels.length) ? rowLabels[simplifiedIdx] : ("r" + simplifiedIdx);
    }
    
    /**
     * Get a human-readable meaning for what the solution value at this row represents.
     */
    private String getRowMeaning(int simplifiedIdx, int fullSize, int nodeCount) {
        // Map back to original row
        int origRow = simplifiedIdx;
        if (sim.circuitRowInfo != null && sim.circuitNeedsMap) {
            for (int i = 0; i < fullSize && i < sim.circuitRowInfo.length; i++) {
                if (sim.circuitRowInfo[i].mapRow == simplifiedIdx) {
                    origRow = i;
                    break;
                }
            }
        }
        
        if (origRow < nodeCount - 1) {
            return "Node voltage (V)";
        } else {
            return "VS current (A)";
        }
    }
    
    /**
     * Format a matrix/vector value for display.
     * Shows 0 cleanly, uses scientific notation for very small/large values.
     */
    private String formatMatrixValue(double val) {
        if (val == 0) return "0";
        double abs = Math.abs(val);
        if (abs >= 0.001 && abs < 1e6) {
            // Remove trailing zeros from fixed-point display
            String s = CircuitElm.showFormat.format(val);
            return s;
        }
        // Scientific notation for very small or large
        if (abs < 0.001 || abs >= 1e6) {
            int exp = (int) Math.floor(Math.log(abs) / Math.log(10));
            double mantissa = val / Math.pow(10, exp);
            return CircuitElm.showFormat.format(mantissa) + "e" + exp;
        }
        return CircuitElm.showFormat.format(val);
    }
    
    // =========================================================================
    // UTILITY METHODS
    // =========================================================================
    
    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }
    
    private native void addResizableStyles() /*-{
        if (!$doc.getElementById('resizable-panel-style')) {
            var style = $doc.createElement('style');
            style.id = 'resizable-panel-style';
            style.textContent = 
                '.resizable-panel {' +
                '  resize: both !important;' +
                '  overflow: auto !important;' +
                '  min-width: 300px !important;' +
                '  min-height: 200px !important;' +
                '}';
            $doc.head.appendChild(style);
        }
    }-*/;
    
    private native void makeResizable(com.google.gwt.dom.client.Element element) /*-{
        element.classList.add('resizable-panel');
    }-*/;
}
