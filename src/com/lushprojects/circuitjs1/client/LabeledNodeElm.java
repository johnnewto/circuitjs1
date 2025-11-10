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

import java.util.HashMap;
import com.lushprojects.circuitjs1.client.util.Locale;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.MultiWordSuggestOracle;

class LabeledNodeElm extends CircuitElm {
    final int FLAG_ESCAPE = 4;
    final int FLAG_INTERNAL = 1;
    final int FLAG_SHOW_ALL_NODES = 8;
    final int FLAG_SHOW_ALL_CIRCUIT_NODES = 16;
    final int FLAG_SHOW_VOLTAGE = 32;
    
    public LabeledNodeElm(int xx, int yy) {
	super(xx, yy);
	text = "label";
    }
    public LabeledNodeElm(int xa, int ya, int xb, int yb, int f,
	    StringTokenizer st) {
	super(xa, ya, xb, yb, f);
	text = st.nextToken();
	if ((flags & FLAG_ESCAPE) == 0) {
	    // old-style dump before escape/unescape
	    while (st.hasMoreTokens())
		text += ' ' + st.nextToken();
	} else {
	    // new-style dump
	    text = CustomLogicModel.unescape(text); 
	}
    }
    String dump() {
	flags |= FLAG_ESCAPE;
	return super.dump() + " " + CustomLogicModel.escape(text);
    }

    String text;
    
    static class LabelEntry {
	Point point;
	int node;
	
	LabelEntry() {
	}
	
	LabelEntry(Point point, int node) {
	    this.point = point;
	    this.node = node;
	}
    }
    
    static HashMap<String,LabelEntry> labelList;
    
    // Cache for sorted node names to avoid repeated sorting
    private static String[] cachedSortedNodes;
    private static int lastKnownSize = -1;
    
    // Cache for reverse lookup: node number -> label name
    private static HashMap<Integer, String> nodeToLabelCache;
    
    boolean isInternal() { return (flags & FLAG_INTERNAL) != 0; }
    boolean showLabelNodes() { return (flags & FLAG_SHOW_ALL_NODES) != 0; }
    boolean showAllCircuitNodes() { return (flags & FLAG_SHOW_ALL_CIRCUIT_NODES) != 0; }
    boolean showVoltage() { return (flags & FLAG_SHOW_VOLTAGE) != 0; }

    public static native void console(String text)
    /*-{
	    console.log(text);
	}-*/;

    static void resetNodeList() {
		labelList = new HashMap<String,LabelEntry>();
		invalidateCache();
    }
    
    // Get sorted array of labeled node names (cached for performance)
    public static String[] getSortedLabeledNodeNames() {
        if (labelList == null || labelList.isEmpty()) {
            cachedSortedNodes = new String[0];
            lastKnownSize = 0;
            return cachedSortedNodes;
        }
        
        // Check if we need to invalidate cache (size changed)
        if (cachedSortedNodes == null || lastKnownSize != labelList.size()) {
            // Convert keySet to array and sort
            java.util.Set<String> keySet = labelList.keySet();
            cachedSortedNodes = keySet.toArray(new String[keySet.size()]);
            java.util.Arrays.sort(cachedSortedNodes);
            lastKnownSize = labelList.size();
        }
        
        return cachedSortedNodes;
    }
    
    // Call this whenever labelList is modified to invalidate cache
    private static void invalidateCache() {
        cachedSortedNodes = null;
        lastKnownSize = -1;
        nodeToLabelCache = null; // Invalidate reverse lookup cache too
    }
    
    // Build reverse lookup cache if needed
    private static void ensureNodeToLabelCache() {
        if (nodeToLabelCache == null && labelList != null && !labelList.isEmpty()) {
            nodeToLabelCache = new HashMap<Integer, String>();
            for (String labelName : labelList.keySet()) {
                LabelEntry entry = labelList.get(labelName);
                if (entry != null && entry.node >= 0) { // Only cache actual nodes (node >= 0)
                    nodeToLabelCache.put(entry.node, labelName);
                }
            }
        }
    }
    final int circleSize = 17;
    void setPoints() {
		super.setPoints();
		lead1 = interpPoint(point1, point2, 1-circleSize/dn);
    }
    
    // get post we're connected to
    Point getConnectedPost() {
		LabelEntry le = labelList.get(text);
		if (le != null)
			return le.point;
		
		// this is the first time calcWireClosure() encountered this label.  so save point1 and
		// return null for now, but return point1 the next time we see this label so that all nodes
		// with the same label are connected
		le = new LabelEntry();
		le.point = point1;
		labelList.put(text, le);
		invalidateCache(); // Cache is now invalid due to new entry
		return null;
    }
    
    void setNode(int p, int n) {
		super.setNode(p, n);
		
		// save node number so we can return it in getByName()
		LabelEntry le = labelList.get(text);
		if (le != null) { // should never happen
			le.node = n;
			// Invalidate reverse lookup cache since node assignments changed
			nodeToLabelCache = null;
		}
    }
    
    int getDumpType() { return 207; }
    int getPostCount() { return 1; }
    
    // this is basically a wire, since it just connects two or more nodes together
    boolean isWireEquivalent() { return true; }
    boolean isRemovableWire() { return true; }
    
    static Integer getByName(String n) {
		if (labelList == null)
			return null;
		LabelEntry le = labelList.get(n);
		if (le == null)
			return null;
		return le.node;
    }
    
    
    static String getNameByNode(int nodeNumber) {
		if (labelList == null)
			return null;
		
		// Use cached reverse lookup for better performance
		ensureNodeToLabelCache();
		if (nodeToLabelCache != null) {
			return nodeToLabelCache.get(nodeNumber);
		}
		
		// Fallback to linear search if cache building failed
		for (String labelName : labelList.keySet()) {
			LabelEntry entry = labelList.get(labelName);
			if (entry != null && entry.node == nodeNumber) {
				return labelName;
			}
		}
		return null;
    }
    
    void draw(Graphics g) {
		setVoltageColor(g, volts[0]);
		drawThickLine(g, point1, lead1);
		g.setColor(needsHighlight() ? selectColor : whiteColor);
		setPowerColor(g, false);

		// Set consistent font before drawing label
    	g.setFont(unitsFont);
		interpPoint(point1, point2, ps2, 1+11./dn);
		setBbox(point1, ps2, circleSize);
		
		// Display label with optional voltage and stock indicator
		String displayText = text;
		
		// Add star prefix if this label is a stock
		if (StockFlowRegistry.isStock(text)) {
		    displayText = "★" + text;
		}
		
		if (showVoltage()) {
		    String voltageText = " = " + getVoltageText(volts[0]);
		    displayText = displayText + voltageText;
		}
		drawLabeledNode(g, displayText, point1, lead1);

		curcount = updateDotCount(current, curcount);
		drawDots(g, point1, lead1, curcount);
		drawPosts(g);
    }
    double getCurrentIntoNode(int n) { return -current; }
    void setCurrent(int x, double c) { current = c; }
    double getVoltageDiff() { 
        return volts[0]; 
    }
	
    void getInfo(String arr[]) {
		// Add stock indicator prefix if applicable
		String displayName = text;
		if (StockFlowRegistry.isStock(text)) {
		    displayName = "★" + text;
		}
		
		arr[0] = Locale.LS(displayName) + " (" + Locale.LS("Labeled Node") + ")";
		arr[1] = "I = " + getCurrentText(getCurrent());
		
		arr[2] = "V = " + getVoltageText(volts[0]);
		
		// Add node number information for debugging
		LabelEntry le = labelList.get(text);
		if (le != null)
			arr[3] = "Node: " + le.node;
		else
			arr[3] = "Node: not assigned";
		
		// Show stock status if this is a stock
		int idx = 4;
		if (StockFlowRegistry.isStock(text)) {
		    arr[idx++] = "Stock: Yes (registered in table)";
		    if (StockFlowRegistry.isSharedStock(text)) {
		        arr[idx++] = "Shared: Yes (used by multiple tables)";
		    }
		}
		
		// Show all labeled nodes if flag is set
		if (showLabelNodes() && labelList != null && !labelList.isEmpty()) {

			arr[idx++] = "All Labeled Nodes:";
			for (String labelName : labelList.keySet()) {
				LabelEntry entry = labelList.get(labelName);
				String nodeInfo = entry != null ? 
					"Node " + entry.node : "not assigned";
				

				
				arr[idx++] = "  " + labelName + ": " + nodeInfo;
				if (idx >= 30) break; // Prevent array overflow
			}
		}
		

		
		// Show all circuit nodes if flag is set
		// if (showAllCircuitNodes() && sim != null && sim.nodeVoltages != null) {
		// 	int idx = (showLabelNodes() && labelList != null && !labelList.isEmpty()) ? 
		// 			4 + Math.min(labelList.size() + 1, arr.length - 4) : 4;
		if (showAllCircuitNodes()) {
			arr[idx++] = "All Circuit Nodes:";
			// Node 0 is ground
			String groundLabel = getNameByNode(0);
			String groundLabelText = groundLabel != null ? " (" + groundLabel + ")" : "";
			arr[idx++] = "  Node 0: 0.00V (Ground)" + groundLabelText;
			// Show other nodes with their voltages
			for (int nodeNum = 1; nodeNum <= sim.nodeVoltages.length && idx < 30; nodeNum++) {
				double voltage = sim.nodeVoltages[nodeNum - 1]; // nodeVoltages is 0-indexed but excludes ground
				String labelName = getNameByNode(nodeNum);
				String nodeLabel = labelName != null ? " (" + labelName + ")" : "";
				arr[idx++] = "  Node " + nodeNum + ": " + getVoltageText(voltage) + nodeLabel;
			}
			
		}
    }

    public EditInfo getEditInfo(int n) {
	if (n == 0) {
	    EditInfo ei = new EditInfo("Text", 0, -1, -1);
	    ei.text = text;
	    
	    // Create autocomplete SuggestBox with existing labeled nodes, stock variables, and cell equation variables
	    MultiWordSuggestOracle oracle = new MultiWordSuggestOracle();
	    
	    // Add existing labeled node names
	    if (labelList != null && !labelList.isEmpty()) {
		for (String labelName : labelList.keySet()) {
		    oracle.add(labelName);
		}
	    }
	    
	    // Add stock variables from TableElm cell equations
	    java.util.Set<String> stockNames = StockFlowRegistry.getAllStockNames();
	    if (stockNames != null && !stockNames.isEmpty()) {
		for (String stockName : stockNames) {
		    oracle.add(stockName);
		}
	    }
	    
	    // Add variables used in cell equations
	    java.util.Set<String> cellVariables = StockFlowRegistry.getAllCellEquationVariables();
	    if (cellVariables != null && !cellVariables.isEmpty()) {
		for (String varName : cellVariables) {
		    oracle.add(varName);
		}
	    }
	    
	    ei.suggestBox = new SuggestBox(oracle);
	    ei.suggestBox.setText(text);
	    ei.suggestBox.setWidth("200px");
	    
	    return ei;
	}
        if (n == 1) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Internal Node", isInternal());
            return ei;
        }
        if (n == 2) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Show Voltage", showVoltage());
            return ei;
        }
        if (n == 3) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Show All Labeled Nodes", showLabelNodes());
            return ei;
        }
        if (n == 4) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Show All Circuit Nodes", showAllCircuitNodes());
            return ei;
        }
	return null;
    }
    public void setEditValue(int n, EditInfo ei) {
	if (n == 0) {
	    // Get text from either SuggestBox or regular textf
	    if (ei.suggestBox != null)
		text = ei.suggestBox.getText();
	    else if (ei.textf != null)
		text = ei.textf.getText();
	    else if (ei.text != null)
		text = ei.text;
	}
	if (n == 1)
	    flags = ei.changeFlag(flags, FLAG_INTERNAL);
	if (n == 2)
	    flags = ei.changeFlag(flags, FLAG_SHOW_VOLTAGE);
	if (n == 3)
	    flags = ei.changeFlag(flags, FLAG_SHOW_ALL_NODES);
	if (n == 4)
	    flags = ei.changeFlag(flags, FLAG_SHOW_ALL_CIRCUIT_NODES);
    }
    @Override String getScopeText(int v) {
	return text;
    }
    
    String getName() { return text; }
}
