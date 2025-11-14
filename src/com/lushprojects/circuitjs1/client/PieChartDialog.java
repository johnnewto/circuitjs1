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
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.lushprojects.circuitjs1.client.util.Locale;
import java.util.Vector;

/**
 * Custom dialog for editing pie chart slices with node names and colors side by side.
 */
public class PieChartDialog extends Dialog {
    
    PieChartElm pieChart;
    CirSim sim;
    Vector<TextBox> nodeNameBoxes;
    Vector<Choice> colorChoices;
    Button addButton, removeButton;
    Grid sliceGrid;
    VerticalPanel mainPanel;
    
    // Color palette (same as PieChartElm)
    static final String colors[] = {
        "#FFFFFF", "#FFFF00", "#00FF00", "#00FFFF", "#FF00FF", "#FF0000",
        "#FFA500", "#90EE90", "#87CEEB", "#DDA0DD", "#FFB6C1"
    };
    static final String colorNames[] = {
        "White", "Yellow", "Green", "Cyan", "Magenta", 
        "Red", "Orange", "Light Green", "Sky Blue", "Plum", "Pink"
    };
    
    public PieChartDialog(PieChartElm pc, CirSim s) {
        super();
        pieChart = pc;
        sim = s;
        
        setText(Locale.LS("Edit Pie Chart"));
        
        mainPanel = new VerticalPanel();
        setWidget(mainPanel);
        
        // Title
        Label title = new Label(Locale.LS("Pie Chart Slices"));
        title.setStyleName("topSpace");
        mainPanel.add(title);
        
        // Create grid with headers
        rebuildSliceGrid();
        
        // Add/Remove buttons
        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setStyleName("topSpace");
        addButton = new Button(Locale.LS("Add Slice"));
        removeButton = new Button(Locale.LS("Remove Last Slice"));
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        mainPanel.add(buttonPanel);
        
        addButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                addSlice();
            }
        });
        
        removeButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                removeLastSlice();
            }
        });
        
        // Bottom buttons
        HorizontalPanel bottomPanel = new HorizontalPanel();
        bottomPanel.setWidth("100%");
        bottomPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
        bottomPanel.setStyleName("topSpace");
        mainPanel.add(bottomPanel);
        
        Button okButton = new Button(Locale.LS("OK"));
        Button cancelButton = new Button(Locale.LS("Cancel"));
        bottomPanel.add(okButton);
        bottomPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
        bottomPanel.add(cancelButton);
        
        okButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                apply();
                closeDialog();
            }
        });
        
        cancelButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                closeDialog();
            }
        });
        
        this.center();
    }
    
    void rebuildSliceGrid() {
        if (sliceGrid != null)
            mainPanel.remove(sliceGrid);
        
        nodeNameBoxes = new Vector<TextBox>();
        colorChoices = new Vector<Choice>();
        
        int sliceCount = pieChart.nodeNames.length;
        
        // Grid: header row + slice rows
        sliceGrid = new Grid(sliceCount + 1, 3);
        sliceGrid.setStyleName("topSpace");
        
        // Header row
        sliceGrid.setText(0, 0, "#");
        sliceGrid.setText(0, 1, Locale.LS("Node Name"));
        sliceGrid.setText(0, 2, Locale.LS("Color"));
        
        // Add slice rows
        for (int i = 0; i < sliceCount; i++) {
            sliceGrid.setText(i + 1, 0, String.valueOf(i + 1));
            
            // Node name text box
            TextBox nameBox = new TextBox();
            nameBox.setText(pieChart.nodeNames[i]);
            nameBox.setWidth("150px");
            sliceGrid.setWidget(i + 1, 1, nameBox);
            nodeNameBoxes.add(nameBox);
            
            // Color choice
            Choice colorChoice = new Choice();
            for (int j = 0; j < colors.length; j++) {
                colorChoice.add(colorNames[j]);
                if (pieChart.nodeColors[i].equalsIgnoreCase(colors[j])) {
                    colorChoice.select(j);
                }
            }
            sliceGrid.setWidget(i + 1, 2, colorChoice);
            colorChoices.add(colorChoice);
        }
        
        mainPanel.insert(sliceGrid, 1); // Insert after title
    }
    
    void addSlice() {
        // Add a new slice with default values
        String[] newNodeNames = new String[pieChart.nodeNames.length + 1];
        String[] newNodeColors = new String[pieChart.nodeColors.length + 1];
        
        // Copy existing
        for (int i = 0; i < pieChart.nodeNames.length; i++) {
            newNodeNames[i] = pieChart.nodeNames[i];
            newNodeColors[i] = pieChart.nodeColors[i];
        }
        
        // Add new
        newNodeNames[pieChart.nodeNames.length] = "node" + (pieChart.nodeNames.length + 1);
        newNodeColors[pieChart.nodeColors.length] = colors[(pieChart.nodeColors.length % colors.length)];
        
        pieChart.nodeNames = newNodeNames;
        pieChart.nodeColors = newNodeColors;
        pieChart.nodeValues = new double[newNodeNames.length];
        
        rebuildSliceGrid();
    }
    
    void removeLastSlice() {
        if (pieChart.nodeNames.length <= 1)
            return; // Keep at least one slice
        
        String[] newNodeNames = new String[pieChart.nodeNames.length - 1];
        String[] newNodeColors = new String[pieChart.nodeColors.length - 1];
        
        for (int i = 0; i < newNodeNames.length; i++) {
            newNodeNames[i] = pieChart.nodeNames[i];
            newNodeColors[i] = pieChart.nodeColors[i];
        }
        
        pieChart.nodeNames = newNodeNames;
        pieChart.nodeColors = newNodeColors;
        pieChart.nodeValues = new double[newNodeNames.length];
        
        rebuildSliceGrid();
    }
    
    void apply() {
        // Update node names from text boxes
        for (int i = 0; i < nodeNameBoxes.size(); i++) {
            pieChart.nodeNames[i] = nodeNameBoxes.get(i).getText().trim();
        }
        
        // Update colors from choices
        for (int i = 0; i < colorChoices.size(); i++) {
            int colorIndex = colorChoices.get(i).getSelectedIndex();
            pieChart.nodeColors[i] = colors[colorIndex];
        }
        
        sim.needAnalyze();
    }
}
