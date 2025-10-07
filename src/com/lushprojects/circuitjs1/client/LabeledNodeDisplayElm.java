/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.util.Locale;

/**
 * Display element that shows voltage of a labeled node
 * without having input pins or affecting the circuit
 */
class LabeledNodeDisplayElm extends LabeledNodeElm {
       
    public LabeledNodeDisplayElm(int xx, int yy) {
        super(xx, yy);
    }
    
    public LabeledNodeDisplayElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
    }

    
    int getDumpType() {    return 254;}
    
    void draw(Graphics g) {
		setVoltageColor(g, volts[0]);
		// drawThickLine(g, point1, lead1);
		g.setColor(needsHighlight() ? selectColor : whiteColor);
		setPowerColor(g, false);
		interpPoint(point1, point2, ps2, 1+11./dn);
		setBbox(point1, ps2, circleSize);
		drawLabeledNode(g, text, point1, lead1);

		curcount = updateDotCount(current, curcount);
		drawDots(g, point1, lead1, curcount);
		// drawPosts(g);
    }

    void getInfo(String arr[]) {
		arr[0] = Locale.LS(text) + " (" + Locale.LS("Labeled Display") + ")";
		arr[1] = "I = " + getCurrentText(getCurrent());
		arr[2] = "V = " + getVoltageText(volts[0]);
		
		// Add node number information for debugging
		LabelEntry le = labelList.get(text);
		if (le != null) 
			arr[3] = "Node: " + le.node;
		else 
			arr[3] = "Node: not assigned";

    		// Show all labeled nodes if flag is set
		if (showLabelNodes() && labelList != null && !labelList.isEmpty()) {
			int idx = 4;
			arr[idx++] = "All Labeled Nodes:";
			for (String labelName : labelList.keySet()) {
				LabelEntry entry = labelList.get(labelName);
				String nodeInfo = entry != null ? 
					"Node " + entry.node : "not assigned";
				arr[idx++] = "  " + labelName + ": " + nodeInfo;
				if (idx >= arr.length) break; // Prevent array overflow
			}
		}
    }
}