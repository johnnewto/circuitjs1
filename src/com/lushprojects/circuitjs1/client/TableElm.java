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

// Interface to capture LabeledNodeElm behavior for table rows
interface LabeledNodeInterface {
    Point getConnectedPostForNode(String nodeName, Point defaultPoint);
    void setNodeForLabel(String nodeName, int nodeNumber);
    boolean isWireEquivalent();
}

class TableElm extends ChipElm implements LabeledNodeInterface {
    // Node names for each row
    String[] nodeNames;
    int rowCount = 4; // Default number of rows
    
    // Flags for display options
    static final int FLAG_SHOW_VOLTAGE = 1;
    
    // Helper method to check voltage display option
    boolean showVoltage() { 
        return (flags & FLAG_SHOW_VOLTAGE) != 0; 
    }
    
    // Format voltage for compact display in table
    String getCompactVoltageText(double voltage) {
        if (Math.abs(voltage) < 1e-3) {
            return "0V";
        } else if (Math.abs(voltage) >= 1000) {
            return Math.round(voltage / 100.0) / 10.0 + "kV";
        } else if (Math.abs(voltage) >= 100) {
            return Math.round(voltage) + "V";
        } else if (Math.abs(voltage) >= 10) {
            return Math.round(voltage * 10.0) / 10.0 + "V";
        } else {
            return Math.round(voltage * 100.0) / 100.0 + "V";
        }
    }
    
    // Constructor for UI creation
    public TableElm(int xx, int yy) {
        super(xx, yy);
        rowCount = 4;
        initializeNodeNames();
        setupPins();
    }
    
    // Constructor for loading from file
    public TableElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
        
        if (st.hasMoreTokens()) {
            rowCount = Integer.parseInt(st.nextToken());
        } else {
            rowCount = 4;
        }
        
        nodeNames = new String[rowCount];
        for (int i = 0; i < rowCount; i++) {
            nodeNames[i] = st.hasMoreTokens() ? 
                CustomLogicModel.unescape(st.nextToken()) : "node" + i;
        }
        
        setupPins();
    }
    
    void initializeNodeNames() {
        nodeNames = new String[rowCount];
        for (int i = 0; i < rowCount; i++) {
            nodeNames[i] = "node" + i;
        }
    }
    
    // Implement LabeledNodeInterface methods
    public Point getConnectedPostForNode(String nodeName, Point defaultPoint) {
        // Ensure LabeledNodeElm system is initialized
        if (LabeledNodeElm.labelList == null) {
            LabeledNodeElm.resetNodeList();
        }
        
        LabeledNodeElm.LabelEntry le = LabeledNodeElm.labelList.get(nodeName);
        if (le != null) {
            return le.point;
        }
        
        // First occurrence - we need to create a temporary LabeledNodeElm to create the entry
        // This is a workaround since LabelEntry is a non-static inner class
        createLabelEntryForNode(nodeName, defaultPoint);
        return null; // First occurrence returns null per LabeledNodeElm convention
    }
    
    // Helper method to create LabelEntry using a temporary LabeledNodeElm
    private void createLabelEntryForNode(String nodeName, Point point) {
        console("DEBUG: Creating LabelEntry for node '" + nodeName + "' at point " + point.x + "," + point.y);
        
        // Create a temporary LabeledNodeElm to access its inner class
        LabeledNodeElm tempNode = new LabeledNodeElm(point.x, point.y);
        tempNode.text = nodeName;
        
        // Manually create the entry by calling its getConnectedPost method
        // This will create and register the LabelEntry in the static labelList
        tempNode.point1 = point;
        Point result = tempNode.getConnectedPost();
        
        console("DEBUG: LabelEntry created for '" + nodeName + "', getConnectedPost returned: " + 
                (result == null ? "null (first occurrence)" : "existing point"));
    }
    
    public void setNodeForLabel(String nodeName, int nodeNumber) {
        LabeledNodeElm.LabelEntry le = LabeledNodeElm.labelList.get(nodeName);
        if (le != null) {
            console("DEBUG: Setting node number " + nodeNumber + " for label '" + nodeName + "'");
            le.node = nodeNumber;
        } else {
            console("DEBUG: WARNING - No LabelEntry found for '" + nodeName + "' when setting node " + nodeNumber);
        }
    }
    
    public boolean isWireEquivalent() {
        return true; // Table acts like multiple labeled wires
    }
    
    void setupPins() {
        pins = new Pin[rowCount];
        for (int i = 0; i < rowCount; i++) {
            pins[i] = new Pin(i, SIDE_E, getNodeName(i));
            pins[i].output = false; // These are connection points, not outputs
        }
        // Ensure volts array is properly sized
        if (volts == null || volts.length != rowCount) {
            volts = new double[rowCount];
        }
        allocNodes();
    }
    
    String getNodeName(int index) {
        if (index < 0 || index >= nodeNames.length) {
            return "node" + index;
        }
        return nodeNames[index];
    }
    
    int getRowCount() {
        return rowCount;
    }
    
    void setRowCount(int count) {
        if (count < 1) count = 1;
        if (count > 20) count = 20; // Reasonable maximum
        
        String[] oldNames = nodeNames;
        rowCount = count;
        nodeNames = new String[rowCount];
        
        // Copy existing names
        for (int i = 0; i < rowCount; i++) {
            if (i < oldNames.length) {
                nodeNames[i] = oldNames[i];
            } else {
                nodeNames[i] = "node" + i;
            }
        }
        
        // Resize volts array
        volts = new double[rowCount];
        
        allocNodes();
        setupPins();
        setPoints();
        
        // Initialize nodes with LabeledNodeElm system
        initializeNodeConnectivity();
    }
    
    // Initialize all table nodes with the LabeledNodeElm system
    void initializeNodeConnectivity() {
        console("DEBUG: Initializing node connectivity for TableElm with " + rowCount + " rows");
        
        if (pins == null) {
            console("DEBUG: WARNING - pins array is null, skipping initialization");
            return;
        }
        
        // Ensure LabeledNodeElm system is ready
        if (LabeledNodeElm.labelList == null) {
            console("DEBUG: LabeledNodeElm.labelList was null, calling resetNodeList()");
            LabeledNodeElm.resetNodeList();
        }
        
        // Pre-register all node names to avoid issues during wire closure
        for (int i = 0; i < getPostCount(); i++) {
            if (pins[i] != null && pins[i].post != null) {
                console("DEBUG: Registering node " + i + " '" + nodeNames[i] + "' at post " + 
                       pins[i].post.x + "," + pins[i].post.y);
                getConnectedPostForNode(nodeNames[i], pins[i].post);
            } else {
                console("DEBUG: WARNING - Pin " + i + " or its post is null");
            }
        }
        
        // Log final status
        debugLogConnectionStatus();
    }
    
    int getDumpType() { 
        return 253; // Unused number in 200-300 range
    }
    
    int getPostCount() { 
        return rowCount; 
    }

    int getVoltageSourceCount() {
        return 0;
    }
    
    boolean isDigitalChip() { 
        return false; // This handles analog node connections
    }
    
    String dump() {
        String result = super.dump();
        result += " " + rowCount;
        for (String name : nodeNames) {
            result += " " + CustomLogicModel.escape(name);
        }
        return result;
    }
    
    void setPoints() {
        // Calculate table size based on row count
        sizeX = 3; // Fixed width for single column
        sizeY = Math.max(rowCount, 2); // Height based on rows
        
        // Call parent setPoints for basic geometry
        super.setPoints();
        
        // Override pin positions to be on right side only
        repositionPinsToRightSide();
        
        // Initialize node connectivity after positions are set
        initializeNodeConnectivity();
    }
    
    // Override to update voltages from circuit simulation
    void startIteration() {
        // Update volts[] array with actual node voltages from simulation
        for (int i = 0; i < getPostCount(); i++) {
            if (nodes != null && i < nodes.length && nodes[i] != 0) {
                volts[i] = sim.nodeVoltages[nodes[i]];
            } else {
                volts[i] = 0;
            }
        }
    }
    
    void stepFinished() {
        // Update volts[] array after simulation step
        for (int i = 0; i < getPostCount(); i++) {
            if (nodes != null && i < nodes.length && nodes[i] != 0) {
                volts[i] = sim.nodeVoltages[nodes[i]];
            } else {
                volts[i] = 0;
            }
        }
    }
    
    void repositionPinsToRightSide() {
        if (pins == null || rectPointsY == null) return;
        
        // Move all pins to right edge, evenly spaced vertically
        int rowHeight = (rectPointsY[2] - rectPointsY[0]) / rowCount;
        for (int i = 0; i < getPostCount(); i++) {
            Pin p = pins[i];
            int pinY = rectPointsY[0] + (int)((i + 0.5) * rowHeight);
            
            // Post is ON the right edge or slightly outside
            p.post = new Point(rectPointsX[2], pinY);
            p.stub = new Point(rectPointsX[2] - cspc/2, pinY);
            p.textloc = new Point(rectPointsX[0] + 5, pinY);
        }
    }
    
    void drawChip(Graphics g) {
        if (rectPointsX == null || rectPointsY == null) return;
        
        // Draw table background
        g.setColor(sim.getBackgroundColor());
        g.fillRect(rectPointsX[0], rectPointsY[0], 
                   rectPointsX[2] - rectPointsX[0], 
                   rectPointsY[2] - rectPointsY[0]);
        
        // Draw table border
        g.setColor(needsHighlight() ? selectColor : lightGrayColor);
        drawThickPolygon(g, rectPointsX, rectPointsY, 4);
        
        // Draw row separators
        int rowHeight = (rectPointsY[2] - rectPointsY[0]) / rowCount;
        g.setColor(lightGrayColor);
        for (int i = 1; i < rowCount; i++) {
            int y = rectPointsY[0] + i * rowHeight;
            g.drawLine(rectPointsX[0], y, rectPointsX[2], y);
        }
        
        // Draw node names and posts
        Font f = new Font("normal", 0, 10*csize);
        g.setFont(f);
        
        for (int i = 0; i < getPostCount(); i++) {
            Pin p = pins[i];
            
            // Draw post connection on RIGHT side (may overlap table edge)
            setVoltageColor(g, volts[i]);
            Point a = p.post;
            Point b = p.stub;
            drawThickLine(g, a, b);
            
            // Draw current dots
            p.curcount = updateDotCount(p.current, p.curcount);
            drawDots(g, b, a, p.curcount);
            
            // Draw node name label (LEFT side of table) with debug info
            g.setColor(whiteColor);
            int labelX = rectPointsX[0] + 5; // Small margin from left edge
            int labelY = rectPointsY[0] + (int)((i + 0.5) * rowHeight);
            
            // Main node name
            String displayText = nodeNames[i];
            
            // Add voltage if show voltage flag is set
            if (showVoltage()) {
                String voltageText = getCompactVoltageText(volts[i]);
                displayText += " " + voltageText;
            }
            
            g.drawString(displayText, labelX, labelY + 4);
            
            // Debug: Show connection status with colored indicator
            drawConnectionDebugIndicator(g, i, labelX + 60, labelY);
        }
        
        drawPosts(g);
    }
    
    // Override to participate in wire closure calculation
    // TableElm needs special handling since it has multiple posts with different names
    Point getConnectedPost() {
        // Standard ChipElm expects single post connectivity
        // TableElm handles connectivity per row during wire closure
        return null;
    }
    
    // Custom method to handle multiple named posts during wire closure
    Point getConnectedPostForRow(int rowIndex) {
        if (rowIndex >= nodeNames.length || pins == null) return null;
        
        String nodeName = nodeNames[rowIndex];
        Point rowPost = pins[rowIndex].post;
        
        return getConnectedPostForNode(nodeName, rowPost);
    }
    
    // Integration with wire closure calculation
    // Each table row participates in wire closure like a LabeledNodeElm
    void participateInWireClosure() {
        // This should be called during CirSim.calcWireClosure()
        // Register each table row's node name and post
        for (int i = 0; i < getPostCount(); i++) {
            getConnectedPostForRow(i);
        }
    }
    
    void setNode(int p, int n) {
        super.setNode(p, n);
        
        // Update voltage immediately if simulation data is available
        if (p < getPostCount() && sim != null && sim.nodeVoltages != null && n != 0 && n < sim.nodeVoltages.length) {
            volts[p] = sim.nodeVoltages[n];
        }
        
        // Update LabeledNodeElm system with our node assignments
        if (p < nodeNames.length) {
            String nodeName = nodeNames[p];
            console("DEBUG: Setting node " + n + " for post " + p + " ('" + nodeName + "') voltage=" + volts[p] + "V");
            setNodeForLabel(nodeName, n);
        }
    }
    
    public EditInfo getChipEditInfo(int n) {
        if (n < nodeNames.length) {
            return new EditInfo("Row " + (n+1) + " Name", nodeNames[n]);
        }
        if (n == nodeNames.length) {
            return new EditInfo("Row Count", rowCount, 1, 20);
        }
        if (n == nodeNames.length + 1) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Show Voltage", (flags & FLAG_SHOW_VOLTAGE) != 0);
            return ei;
        }
        return null;
    }
    
    public void setChipEditValue(int n, EditInfo ei) {
        if (n < nodeNames.length) {
            String oldName = nodeNames[n];
            nodeNames[n] = ei.textf.getText();
            console("DEBUG: Node name changed from '" + oldName + "' to '" + nodeNames[n] + "'");
            setupPins(); // Refresh pin labels
            setPoints(); // Recalculate layout
        }
        if (n == nodeNames.length) {
            console("DEBUG: Row count changed from " + rowCount + " to " + (int)ei.value);
            setRowCount((int)ei.value);
        }
        if (n == nodeNames.length + 1) {
            flags = ei.changeFlag(flags, FLAG_SHOW_VOLTAGE);
            console("DEBUG: Show voltage flag changed to " + ((flags & FLAG_SHOW_VOLTAGE) != 0));
        }
        
        // Log updated status
        debugLogConnectionStatus();
    }
    
    void getInfo(String arr[]) {
        // Provide basic info plus debug details in the lower window
        arr[0] = "table (debug mode)";
        arr[1] = "rows: " + rowCount + (showVoltage() ? " [voltage shown]" : "");
        
        // Debug info for first few rows
        for (int i = 0; i < Math.min(nodeNames.length, 3); i++) {
            String connectionStatus = getNodeConnectionDebugInfo(i);
            arr[i+2] = nodeNames[i] + ": " + getVoltageText(volts[i]) + " " + connectionStatus;
        }
        
        if (nodeNames.length > 3) {
            arr[5] = "...";
        }
        
        // Add debug summary
        if (nodeNames.length <= 3) {
            arr[nodeNames.length + 2] = "Debug: " + getConnectedNodesCount() + "/" + rowCount + " nodes connected";
        }
    }
    
    // Debug helper to show connection status for a node
    String getNodeConnectionDebugInfo(int rowIndex) {
        if (rowIndex >= nodeNames.length) return "[invalid]";
        
        String nodeName = nodeNames[rowIndex];
        
        // Check if this node exists in LabeledNodeElm system
        if (LabeledNodeElm.labelList == null) {
            return "[no labelList]";
        }
        
        LabeledNodeElm.LabelEntry le = LabeledNodeElm.labelList.get(nodeName);
        if (le == null) {
            return "[not in labelList]";
        }
        
        // Check if node number is assigned
        boolean hasNodeNumber = (le.node != 0);
        boolean hasPoint = (le.point != null);
        
        return "[" + (hasNodeNumber ? "node:" + le.node : "no-node") + 
               "," + (hasPoint ? "connected" : "no-point") + "]";
    }
    
    // Count how many nodes are properly connected
    int getConnectedNodesCount() {
        int connected = 0;
        if (LabeledNodeElm.labelList == null) return 0;
        
        for (int i = 0; i < nodeNames.length; i++) {
            LabeledNodeElm.LabelEntry le = LabeledNodeElm.labelList.get(nodeNames[i]);
            if (le != null && le.node != 0) {
                connected++;
            }
        }
        return connected;
    }
    
    // Override connection methods to handle table-specific connectivity
    boolean getConnection(int n1, int n2) {
        // Each row is an independent connection point - no internal connections
        return false;
    }
    
    boolean hasGroundConnection(int n) {
        // Let the simulator handle ground connections through normal circuit analysis
        return false;
    }
    
    // Table supports named node lookups like LabeledNodeElm
    boolean supportsLabeledNodes() {
        return true;
    }
    
    // Debug visual indicator for connection status
    void drawConnectionDebugIndicator(Graphics g, int rowIndex, int x, int y) {
        if (rowIndex >= nodeNames.length) return;
        
        String nodeName = nodeNames[rowIndex];
        boolean isConnected = false;
        boolean hasNodeNumber = false;
        
        // Check connection status
        if (LabeledNodeElm.labelList != null) {
            LabeledNodeElm.LabelEntry le = LabeledNodeElm.labelList.get(nodeName);
            if (le != null) {
                isConnected = (le.point != null);
                hasNodeNumber = (le.node != 0);
            }
        }
        
        // Draw colored indicator
        int indicatorSize = 4;
        if (hasNodeNumber && isConnected) {
            g.setColor(Color.green); // Fully connected
        } else if (isConnected) {
            g.setColor(Color.yellow); // Connected but no node number yet
        } else {
            g.setColor(Color.red); // Not connected
        }
        
        g.fillOval(x, y, indicatorSize, indicatorSize);
        
        // Optional: add text debug info
        g.setColor(lightGrayColor);
        Font smallFont = new Font("normal", 0, 6*csize);
        g.setFont(smallFont);
        if (hasNodeNumber) {
            LabeledNodeElm.LabelEntry le = LabeledNodeElm.labelList.get(nodeName);
            g.drawString("n" + le.node, x + 6, y + 4);
        }
    }

    // Debug console output
    public static native void console(String text)
    /*-{
        console.log(text);
    }-*/;
    
    // Debug method to log connection status
    void debugLogConnectionStatus() {
        console("=== TableElm Debug Info ===");
        console("Row count: " + rowCount);
        console("LabelList initialized: " + (LabeledNodeElm.labelList != null));
        
        if (LabeledNodeElm.labelList != null) {
            for (int i = 0; i < nodeNames.length; i++) {
                LabeledNodeElm.LabelEntry le = LabeledNodeElm.labelList.get(nodeNames[i]);
                String status = nodeNames[i] + ": ";
                if (le == null) {
                    status += "NOT_IN_LIST";
                } else {
                    status += "node=" + le.node + ", point=" + (le.point != null ? "set" : "null");
                }
                console("  " + status);
            }
        }
        console("Connected nodes: " + getConnectedNodesCount() + "/" + rowCount);
    }

    double getCurrentIntoNode(int n) {
        if (n >= getPostCount()) return 0;
        // Return current flowing into this node from connected elements
        return -pins[n].current;
    }
    
    void reset() {
        super.reset();
        // No additional reset needed for table element
    }
    
    int getShortcut() { 
        return 0; // No keyboard shortcut
    }
    
    // Debug method to test if nodes with same names have same voltage (wire behavior)
    void debugTestWireBehavior() {
        console("=== Testing Wire Behavior ===");
        
        if (LabeledNodeElm.labelList == null) {
            console("No labelList - cannot test");
            return;
        }
        
        // Check each node name and see if all instances have same voltage
        for (int i = 0; i < nodeNames.length; i++) {
            String nodeName = nodeNames[i];
            LabeledNodeElm.LabelEntry le = LabeledNodeElm.labelList.get(nodeName);
            
            if (le != null && le.node != 0) {
                double tableVoltage = volts[i];
                console("Node '" + nodeName + "': TableElm voltage = " + 
                       getCompactVoltageText(tableVoltage) + ", node# = " + le.node);
                
                // Check if current is flowing (indicates wire behavior)
                double current = getCurrentIntoNode(i);
                String currentStr = (Math.abs(current) < 1e-9) ? "0A" : 
                    (Math.round(current * 1000.0) / 1000.0) + "A";
                console("  Current into node: " + currentStr);
            } else {
                console("Node '" + nodeName + "': NOT CONNECTED or no node number assigned");
            }
        }
    }

    // *Debug  set voltage of x'th node, called by simulator logic
    void setNodeVoltage(int n, double c) {
        volts[n] = c;
        calculateCurrent();
    }
}