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

import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.lushprojects.circuitjs1.client.util.Locale;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * Non-modal dialog that displays all available variables (stocks, labeled nodes, parameters)
 * in a table view. Variables can be clicked to place on canvas as labeled nodes.
 * Dialog stays on top and doesn't steal focus.
 */
public class VariableBrowserDialog extends DialogBox {
    private CirSim sim;
    private FlexTable varTable;
    private ScrollPanel scrollPanel;
    private static VariableBrowserDialog instance = null; // Singleton instance
    private static final int DIALOG_WIDTH = 200;
    private static final int LEFT_MARGIN = 20;
    private static final int PLACEMENT_OFFSET = 80;  // How far left from dialog to start placement (in pixels)
    
    public static void openDialog(CirSim s) {
        if (instance == null) {
            instance = new VariableBrowserDialog(s);
        } else {
            instance.refresh();
        }
        instance.show();
        
        // Position on right-hand side instead of center
        int windowWidth = com.google.gwt.user.client.Window.getClientWidth();
        int windowHeight = com.google.gwt.user.client.Window.getClientHeight();
        int dialogHeight = 450; // Approximate height with margins
        
        // Position on right side with some margin from edge
        int left = windowWidth - DIALOG_WIDTH - LEFT_MARGIN;
        int top = Math.max(20, (windowHeight - dialogHeight) / 2);
        
        instance.setPopupPosition(left, top);
    }
    
    public static void closeDialog() {
        if (instance != null) {
            instance.hide();
        }
    }
    
    public static boolean isOpen() {
        return instance != null && instance.isShowing();
    }
    
    public static void refreshIfOpen() {
        if (instance != null && instance.isShowing()) {
            instance.refresh();
        }
    }
    
    private VariableBrowserDialog(CirSim s) {
        sim = s;
        
        // Make dialog non-modal
        setModal(false);
        setGlassEnabled(false);
        
        // Prevent keyboard events from propagating to circuit
        Event.addNativePreviewHandler(new NativePreviewHandler() {
            public void onPreviewNativeEvent(NativePreviewEvent event) {
                if (isShowing()) {
                    int type = event.getTypeInt();
                    if ((type & Event.ONKEYDOWN) != 0 || (type & Event.ONKEYPRESS) != 0 || (type & Event.ONKEYUP) != 0) {
                        event.cancel();
                        event.getNativeEvent().stopPropagation();
                    }
                }
            }
        });
        
        setText(Locale.LS("Variable Browser"));
        
        VerticalPanel vp = new VerticalPanel();
        vp.setWidth("200px");  // Half the original 400px width
        setWidget(vp);
        
        // Add description label
        Label descLabel = new Label(Locale.LS("Click variable to place"));
        descLabel.getElement().getStyle().setMarginBottom(10, Unit.PX);
        descLabel.getElement().getStyle().setFontSize(11, Unit.PX);
        vp.add(descLabel);
        
        // Create scrollable table
        scrollPanel = new ScrollPanel();
        scrollPanel.setSize("200px", "400px");  // Half the original 400px width
        vp.add(scrollPanel);
        
        varTable = new FlexTable();
        varTable.setCellPadding(5);
        varTable.setCellSpacing(0);
        varTable.setWidth("100%");
        varTable.getElement().getStyle().setProperty("borderCollapse", "collapse");
        scrollPanel.setWidget(varTable);
        
        // Add table headers
        varTable.setText(0, 0, Locale.LS("Variable Name"));
        varTable.setText(0, 1, Locale.LS("Type"));
        varTable.getRowFormatter().getElement(0).getStyle().setProperty("fontWeight", "bold");
        varTable.getRowFormatter().getElement(0).getStyle().setProperty("backgroundColor", "#e0e0e0");
        
        // Add bottom buttons
        HorizontalPanel hp = new HorizontalPanel();
        hp.setWidth("100%");
        hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
        hp.getElement().getStyle().setMarginTop(10, Unit.PX);
        vp.add(hp);
        
        Button refreshButton = new Button(Locale.LS("Refresh"));
        Button closeButton = new Button(Locale.LS("Close"));
        hp.add(refreshButton);
        hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
        hp.add(closeButton);
        
        refreshButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                refresh();
            }
        });
        
        closeButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                hide();
            }
        });
        
        // Populate initial data
        refresh();
    }
    
    private void refresh() {
        // Clear existing rows (except header)
        while (varTable.getRowCount() > 1) {
            varTable.removeRow(1);
        }
        
        // Collect all variables with deduplication
        List<VariableInfo> variables = new ArrayList<VariableInfo>();
        Set<String> addedNames = new java.util.HashSet<String>();  // Track already-added names
        
        // Add stock variables from TableElm (first priority)
        Set<String> stockNames = StockFlowRegistry.getAllStockNames();
        if (stockNames != null && !stockNames.isEmpty()) {
            for (String stockName : stockNames) {
                if (!addedNames.contains(stockName)) {
                    variables.add(new VariableInfo(stockName, "Stock"));
                    addedNames.add(stockName);
                }
            }
        }
        
        // Add labeled node names (second priority)
        String[] labeledNodes = LabeledNodeElm.getSortedLabeledNodeNames();
        if (labeledNodes != null && labeledNodes.length > 0) {
            for (String nodeName : labeledNodes) {
                if (!addedNames.contains(nodeName)) {
                    variables.add(new VariableInfo(nodeName, "node"));
                    addedNames.add(nodeName);
                }
            }
        }
        
        // Add variables from cell equations (third priority)
        Set<String> cellVariables = StockFlowRegistry.getAllCellEquationVariables();
        if (cellVariables != null && !cellVariables.isEmpty()) {
            for (String varName : cellVariables) {
                if (!addedNames.contains(varName)) {
                    variables.add(new VariableInfo(varName, "Variable"));
                    addedNames.add(varName);
                }
            }
        }
        
        // Sort alphabetically by name
        Collections.sort(variables);
        
        // Populate table
        int row = 1;
        for (final VariableInfo var : variables) {
            varTable.setText(row, 0, var.name);
            varTable.setText(row, 1, var.type);
            
            // Add click handler to place variable on canvas
            final int currentRow = row;
            varTable.getCellFormatter().getElement(currentRow, 0).getStyle().setProperty("cursor", "pointer");
            varTable.getCellFormatter().getElement(currentRow, 0).getStyle().setProperty("color", "#0066cc");
            varTable.getCellFormatter().getElement(currentRow, 0).getStyle().setProperty("textDecoration", "underline");
            
            // Style the row with alternating colors
            if (row % 2 == 0) {
                varTable.getRowFormatter().getElement(row).getStyle().setProperty("backgroundColor", "#f9f9f9");
            }
            
            // Add hover effect
            varTable.getRowFormatter().getElement(row).setTitle(Locale.LS("Click to place on canvas"));
            
            // Create click handler using a wrapper element
            com.google.gwt.dom.client.Element cellElement = varTable.getCellFormatter().getElement(currentRow, 0);
            com.google.gwt.user.client.Event.sinkEvents(cellElement, com.google.gwt.user.client.Event.ONCLICK);
            com.google.gwt.user.client.Event.setEventListener(cellElement, new com.google.gwt.user.client.EventListener() {
                public void onBrowserEvent(com.google.gwt.user.client.Event event) {
                    if (com.google.gwt.user.client.Event.ONCLICK == event.getTypeInt()) {
                        placeVariableOnCanvas(var.name);
                    }
                }
            });
            
            row++;
        }
        
        // Show message if no variables found
        if (variables.isEmpty()) {
            varTable.setText(1, 0, Locale.LS("No variables found"));
            varTable.getFlexCellFormatter().setColSpan(1, 0, 2);
            varTable.getCellFormatter().getElement(1, 0).getStyle().setProperty("fontStyle", "italic");
            varTable.getCellFormatter().getElement(1, 0).getStyle().setProperty("color", "#999");
        }
    }
    
    private void placeVariableOnCanvas(String varName) {
        // Don't close the dialog - keep it open for placing more variables
        
        // Get actual dialog position (in case user moved it)
        int dialogScreenX = getAbsoluteLeft();
        
        // Calculate placement starting point (further left from dialog edge)
        int placementScreenX = dialogScreenX - PLACEMENT_OFFSET;
        
        // Convert dialog edge from screen to grid coordinates
        int dialogLeftGx = sim.inverseTransformX(placementScreenX);
        
        // Use vertical center of canvas as starting point
        int centerY = sim.circuitArea.height / 2;
        int startGy = sim.inverseTransformY(centerY);
        
        // Vertical zig-zag pattern: start near dialog, work left
        int verticalSpacing = sim.gridSize * 6;   // Spacing between vertical positions
        int horizontalStep = sim.gridSize * 10;   // How far left to move each column
        int maxColumns = 10;                      // Maximum number of columns to try
        int positionsPerColumn = 15;              // How many vertical positions per column
        
        int gx = dialogLeftGx;
        int gy = startGy;
        
        // Search in vertical zig-zag pattern
        boolean foundSpot = false;
        for (int col = 0; col < maxColumns && !foundSpot; col++) {
            // Calculate horizontal position for this column (moving left)
            int columnGx = dialogLeftGx - (col * horizontalStep);
            
            // Try positions vertically in this column (alternating up/down from center)
            for (int pos = 0; pos < positionsPerColumn && !foundSpot; pos++) {
                // Alternate: 0 -> center, 1 -> up, 2 -> down, 3 -> up further, 4 -> down further...
                int verticalOffset;
                if (pos == 0) {
                    verticalOffset = 0;  // Start at center
                } else if (pos % 2 == 1) {
                    verticalOffset = -((pos + 1) / 2) * verticalSpacing;  // Up
                } else {
                    verticalOffset = (pos / 2) * verticalSpacing;  // Down
                }
                
                int testGx = sim.snapGrid(columnGx);
                int testGy = sim.snapGrid(startGy + verticalOffset);
                
                // Check if this position is clear
                if (isPositionClear(testGx, testGy)) {
                    gx = testGx;
                    gy = testGy;
                    foundSpot = true;
                }
            }
        }
        
        // If no clear spot found, just use near the dialog
        if (!foundSpot) {
            gx = sim.snapGrid(dialogLeftGx);
            gy = sim.snapGrid(startGy);
        }
        
        // Create a labeled node element at the found position
        LabeledNodeElm elm = new LabeledNodeElm(gx, gy);
        elm.text = varName;  // Set the label text (not 'name')
        
        // Set a proper shaft length - extend to the right and slightly down for visibility
        // This creates the line from the connection point (x,y) to the label (x2,y2)
        int shaftLength = sim.gridSize * 4;  // Make shaft visible
        elm.x2 = gx + shaftLength;
        elm.y2 = gy;
        elm.setPoints();  // Update internal geometry
        
        // Add to circuit
        sim.elmList.addElement(elm);
        sim.needAnalyze();
        
        // Select the new element so it's highlighted
        sim.clearSelection();
        elm.setSelected(true);
        
        // Don't force drag mode - let user click and drag normally
        sim.repaint();
    }
    
    // Check if a position is clear of other elements
    private boolean isPositionClear(int gx, int gy) {
        // Define a minimum safe distance (in grid units)
        int minDistance = sim.gridSize * 3;
        
        // Check against all existing elements
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            
            // Use bounding box for all elements - most accurate collision detection
            Rectangle bbox = ce.getBoundingBox();
            if (bbox != null) {
                // Add extra margin around all elements
                // Use larger margin for tables and other large elements
                int margin = minDistance;
                if (ce instanceof TableElm || ce instanceof GodlyTableElm) {
                    margin = sim.gridSize * 2;  // Extra space around tables
                }
                
                // Check if position is within or near the element's bounding box
                if (gx >= bbox.x - margin && gx <= bbox.x + bbox.width + margin &&
                    gy >= bbox.y - margin && gy <= bbox.y + bbox.height + margin) {
                    return false;  // Position overlaps with or is too close to element
                }
            } else {
                // Fallback for elements without bounding box - use point-based checks
                
                // Check distance to first point
                int dx1 = ce.x - gx;
                int dy1 = ce.y - gy;
                double dist1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
                
                if (dist1 < minDistance)
                    return false;
                
                // Check distance to second point (for two-terminal elements)
                int dx2 = ce.x2 - gx;
                int dy2 = ce.y2 - gy;
                double dist2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);
                
                if (dist2 < minDistance)
                    return false;
                
                // Check if point is near the line between element endpoints
                if (ce.x != ce.x2 || ce.y != ce.y2) {
                    double lineLength = Math.sqrt((ce.x2 - ce.x) * (ce.x2 - ce.x) + 
                                                 (ce.y2 - ce.y) * (ce.y2 - ce.y));
                    if (lineLength > 0) {
                        // Calculate perpendicular distance to line
                        double distToLine = Math.abs((ce.y2 - ce.y) * gx - (ce.x2 - ce.x) * gy + 
                                                     ce.x2 * ce.y - ce.y2 * ce.x) / lineLength;
                        
                        // Check if point projects onto the line segment
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
    
    // Helper class to store variable information
    private static class VariableInfo implements Comparable<VariableInfo> {
        String name;
        String type;
        
        VariableInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }
        
        public int compareTo(VariableInfo other) {
            return this.name.compareToIgnoreCase(other.name);
        }
    }
}
