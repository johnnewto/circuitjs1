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

import com.lushprojects.circuitjs1.client.util.Locale;
import com.lushprojects.circuitjs1.client.ActionScheduler.ScheduledAction;
import java.util.List;

/**
 * ActionTimeElm displays the status of scheduled actions from ActionScheduler.
 * It's a visual indicator element that shows current actions and their states.
 * Double-click to open the Action Time Dialog for full management.
 */
class ActionTimeElm extends CircuitElm {
    boolean enabled;
    private boolean playPauseIconHovered = false;
    private Rectangle playPauseIconRect = null;
    
    public ActionTimeElm(int xx, int yy) {
        super(xx, yy);
        enabled = true; // Enabled by default
    }
    
    public ActionTimeElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        enabled = true; // Default to enabled for backward compatibility
        // For backward compatibility, read old format but don't use it
        // Just consume the tokens
        try {
            st.nextToken(); // actionTime
            st.nextToken(); // preText
            st.nextToken(); // postText
            st.nextToken(); // sliderName
            st.nextToken(); // sliderValueBefore
            st.nextToken(); // sliderValueAfter
            st.nextToken(); // enabled (old format)
        } catch (Exception e) {
            // Old format or partial tokens - ignore
        }
        // Try to read enabled flag for this element
        try {
            enabled = Boolean.parseBoolean(st.nextToken());
        } catch (Exception e) {
            // No enabled flag in saved file, use default
        }
    }
    
    String dump() { 
        // Save enabled state
        return super.dump() + " " + enabled; 
    }
    
    void reset() {
        // Nothing to reset - display element only
    }
    
    int getDumpType() { 
        return 432; 
    }
    
    int getPostCount() { 
        return 0; // No electrical connections
    }
    
    void setPoints() {
        super.setPoints();
    }
    
    void draw(Graphics g) {
        g.save();
        boolean selected = needsHighlight();
        
        // Calculate the center point between point1 and point2
        int cx = (x + x2) / 2;
        int cy = (y + y2) / 2;
        
        // Get actions from scheduler
        ActionScheduler scheduler = ActionScheduler.getInstance(sim);
        List<ScheduledAction> actions = scheduler.getAllActions();
        
        // Apply gray filter if disabled
        if (!enabled) {
            g.context.setGlobalAlpha(0.5);
        }
        
        // Calculate box dimensions
        Font f = new Font("SansSerif", Font.BOLD, 14);
        g.setFont(f);
        
        int width = 200;
        int lineHeight = 18;
        int headerHeight = 30;
        int actionHeight = 20;
        int height = headerHeight + Math.max(40, actions.size() * actionHeight + 20);
        
        // Draw box background
        g.setColor(new Color(240, 248, 255)); // Light blue
        g.fillRect(cx - width/2, cy - height/2, width, height);
        
        // Draw border
        g.setColor(selected ? selectColor : Color.black);
        g.setLineWidth(2.0);
        g.drawRect(cx - width/2, cy - height/2, width, height);
        
        // Draw header
        g.setColor(new Color(100, 149, 237)); // Cornflower blue
        g.fillRect(cx - width/2, cy - height/2, width, headerHeight);
        
        // Draw play/pause icon on the LEFT side of header
        String playPauseIcon = sim.simIsRunning() ? "⏸" : "▶";
        int iconX = cx - width/2 + 15;
        int iconY = cy - height/2 + 18;
        
        // Store icon bounds for click detection - extend bounds to fully cover the icon
        int iconSize = 24;
        playPauseIconRect = new Rectangle(iconX - 14, iconY - 18, iconSize+6, iconSize+4);
        
        // Highlight icon if hovered
        if (playPauseIconHovered) {
            g.setColor(new Color(70, 70, 70)); // Slightly lighter background
            g.fillRect(playPauseIconRect.x, playPauseIconRect.y, playPauseIconRect.width, playPauseIconRect.height);
            g.setColor(selectColor); // Blue when hovering
        } else {
            g.setColor(Color.white);
        }
        
        f = new Font("SansSerif", Font.BOLD, 16);
        g.setFont(f);
        g.drawString(playPauseIcon, iconX - 6, iconY);
        
        // Draw title centered
        f = new Font("SansSerif", Font.BOLD, 14);
        g.setFont(f);
        g.setColor(Color.white);
        String title = "Action Schedule";
        int textWidth = (int)g.context.measureText(title).getWidth();
        g.drawString(title, cx - textWidth/2, cy - height/2 + 18);
        
        // Draw current time
        f = new Font("SansSerif", 0, 11);
        g.setFont(f);
        String timeText = "t=" + getUnitText(sim.t, "s");
        textWidth = (int)g.context.measureText(timeText).getWidth();
        g.drawString(timeText, cx - width/2 + 10, cy - height/2 + headerHeight + 5);
        
        // Draw actions
        if (actions.isEmpty()) {
            g.setColor(Color.gray);
            g.setFont(new Font("SansSerif", 0, 11));
            String msg = "No actions scheduled";
            textWidth = (int)g.context.measureText(msg).getWidth();
            g.drawString(msg, cx - textWidth/2, cy);
            
            // Draw hint to double-click
            msg = "(Double-click to add)";
            textWidth = (int)g.context.measureText(msg).getWidth();
            g.drawString(msg, cx - textWidth/2, cy + 15);
        } else {
            int yPos = cy - height/2 + headerHeight + 15;
            int maxDisplay = Math.min(actions.size(), 8); // Show max 8 actions
            
            for (int i = 0; i < maxDisplay; i++) {
                ScheduledAction action = actions.get(i);
                
                // Draw background for completed actions (green)
                if (action.state == ActionScheduler.ActionState.COMPLETED) {
                    g.setColor(new Color(200, 230, 201)); // Darker green #c8e6c9
                    int bgX = cx - width/2 + 5;
                    int bgY = yPos - 12;
                    int bgWidth = width - 10;
                    int bgHeight = actionHeight;
                    g.fillRect(bgX, bgY, bgWidth, bgHeight);
                }
                
                g.setFont(new Font("SansSerif", 0, 10));
                
                // Determine status and color based on state machine
                Color statusColor = Color.gray; // Initialize with default
                String statusSymbol = "○";
                Color textColor = Color.black;
                
                // Gray out disabled actions
                if (!action.enabled) {
                    statusColor = new Color(150, 150, 150); // Gray
                    textColor = new Color(150, 150, 150);
                }
                
                // Use state machine for status display
                switch (action.state) {
                    case READY:
                        // Ready to execute immediately at t=0
                        if (action.enabled) {
                            statusColor = new Color(0, 200, 0); // Bright green
                        }
                        statusSymbol = "⚡";
                        break;
                        
                    case PENDING:
                        if (action.enabled) {
                            statusColor = new Color(150, 150, 0); // Yellow
                        }
                        statusSymbol = "⏱";
                        break;
                        
                    case WAITING:
                        // Waiting for timer - show pause icon
                        if (action.enabled) {
                            statusColor = new Color(255, 165, 0); // Orange
                        }
                        statusSymbol = "⏸";
                        break;
                        
                    case EXECUTING:
                        // Transient state - show in progress
                        if (action.enabled) {
                            statusColor = new Color(0, 100, 200); // Blue
                        }
                        statusSymbol = "▶";
                        break;
                        
                    case COMPLETED:
                        // Completed - show checkmark
                        if (action.enabled) {
                            statusColor = new Color(0, 150, 0); // Green
                        }
                        statusSymbol = "✓";
                        break;
                }
                
                // Draw status symbol
                g.setColor(statusColor);
                g.setFont(new Font("SansSerif", 0, 14));
                g.drawString(statusSymbol, cx - width/2 + 10, yPos);
                
                // Draw action info
                g.setFont(new Font("SansSerif", 0, 10));
                g.setColor(textColor);
                String actionText = getUnitText(action.actionTime, "s") + ": ";
                if (action.stopSimulation) {
                    actionText += "[STOP SIMULATION]";
                } else if (action.sliderName != null && !action.sliderName.isEmpty()) {
                    actionText += action.sliderName + "=" + 
                                 getFormattedSliderValue(action.sliderName, action.sliderValue);
                } else {
                    actionText += "(no action)";
                }
                
                // Truncate if too long
                if (actionText.length() > 30) {
                    actionText = actionText.substring(0, 27) + "...";
                }
                
                g.drawString(actionText, cx - width/2 + 30, yPos);
                
                yPos += actionHeight;
            }
            
            // Show "and X more..." if there are more actions
            if (actions.size() > maxDisplay) {
                g.setColor(Color.gray);
                g.setFont(new Font("SansSerif", 0, 10));
                String moreText = "...and " + (actions.size() - maxDisplay) + " more";
                textWidth = (int)g.context.measureText(moreText).getWidth();
                g.drawString(moreText, cx - textWidth/2, yPos);
            }
        }
        
        // // Draw double-click hint at bottom
        // g.setColor(Color.gray);
        // g.setFont(new Font("SansSerif", 0, 9));
        // String hint = "Double-click to manage";
        // textWidth = (int)g.context.measureText(hint).getWidth();
        // g.drawString(hint, cx - textWidth/2, cy + height/2 - 8);
        
        // Set bounding box
        setBbox(cx - width/2 - 5, cy - height/2 - 5, cx + width/2 + 5, cy + height/2 + 5);
        
        g.restore();
    }
    
    void stepFinished() {
        // No action needed - scheduler handles execution
    }
    
    void getInfo(String arr[]) {
        arr[0] = "Action Schedule Display";
        arr[1] = "element enabled = " + (enabled ? "yes" : "no");
        arr[2] = "current time = " + getUnitText(sim.t, "s");
        
        ActionScheduler scheduler = ActionScheduler.getInstance(sim);
        List<ScheduledAction> actions = scheduler.getAllActions();
        
        int activeCount = 0;
        int pendingCount = 0;
        int waitingCount = 0;
        int completedCount = 0;
        for (ScheduledAction action : actions) {
            if (action.enabled) {
                activeCount++;
                switch (action.state) {
                    case READY:
                        pendingCount++;  // Count READY as pending
                        break;
                    case PENDING:
                        pendingCount++;
                        break;
                    case WAITING:
                        waitingCount++;
                        break;
                    case EXECUTING:
                        waitingCount++;  // Count EXECUTING as waiting
                        break;
                    case COMPLETED:
                        completedCount++;
                        break;
                }
            }
        }
        
        arr[3] = "total actions = " + activeCount;
        arr[4] = "pending = " + pendingCount;
        arr[5] = "waiting = " + waitingCount;
        arr[6] = "completed = " + completedCount;
        arr[7] = "Double-click to manage actions";
    }
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Enabled", enabled);
            return ei;
        }
        if (n == 1) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.text = "This element displays scheduled actions";
            ei.text += "\n\nDouble-click to open Action Time Dialog";
            ei.text += "\nfor full action management.";
            ei.text += "\n\nWhen disabled, the action scheduler is inactive";
            ei.text += "\nand no actions will execute.";
            return ei;
        }
        return null;
    }
    
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            enabled = ei.checkbox.getState();
        }
    }
    
    /**
     * Get the play/pause icon bounds for click detection
     */
    Rectangle getPlayPauseIconRect() {
        return playPauseIconRect;
    }
    
    /**
     * Check if mouse is over the play/pause icon
     */
    boolean isPlayPauseIconHovered() {
        return playPauseIconHovered;
    }
    
    /**
     * Set the play/pause icon hover state
     */
    void setPlayPauseIconHovered(boolean hovered) {
        playPauseIconHovered = hovered;
    }
    
    /**
     * Check if a point is inside the play/pause icon
     */
    boolean isPointInPlayPauseIcon(int x, int y) {
        if (playPauseIconRect == null) return false;
        return x >= playPauseIconRect.x && x <= playPauseIconRect.x + playPauseIconRect.width &&
               y >= playPauseIconRect.y && y <= playPauseIconRect.y + playPauseIconRect.height;
    }
    
    /**
     * Get formatted value for a slider/action, checking element for custom formatting
     */
    private String getFormattedSliderValue(String sliderName, double value) {
        // Find the adjustable with this name
        for (int i = 0; i < sim.adjustables.size(); i++) {
            Adjustable adj = sim.adjustables.get(i);
            if (adj.sliderText != null && adj.sliderText.equals(sliderName)) {
                EditInfo ei = adj.elm.getEditInfo(adj.editItem);
                if (ei != null) {
                    // Check if element has custom slider text formatting
                    try {
                        String customText = adj.elm.getSliderUnitText(adj.editItem, ei, value);
                        if (customText != null)
                            return customText;
                    } catch (Exception e) {
                        // Element doesn't have custom formatting
                    }
                    // Use default formatting
                    return EditDialog.unitString(ei, value);
                }
            }
        }
        // Fallback to simple format
        return CircuitElm.showFormat.format(value);
    }
    
    /**
     * Handle click on play/pause icon
     */
    void handlePlayPauseIconClick() {
        // Simply toggle simulation state, just like Run/Stop button
        if (sim.simIsRunning()) {
            // Currently running - stop it
            sim.setSimRunning(false);
        } else {
            // Currently stopped - start it
            sim.setSimRunning(true);
        }
    }
    
    // Handle right-click to open dialog
    void doRightClick(int mx, int my) {
        ActionTimeDialog.openDialog(sim);
    }
    
    // Override to prevent trying to find voltages (no posts)
    void setNodeVoltage(int n, double c) {
    }
    
    void stamp() {
        // No electrical connections
    }
}
