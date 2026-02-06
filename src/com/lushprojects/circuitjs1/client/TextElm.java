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

import java.util.Vector;

import com.lushprojects.circuitjs1.client.util.Locale;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;

class TextElm extends GraphicElm {
    String text;
    Vector<String> lines;
    int size;
    String colorRGBA; // RGBA format: red, green, blue, alpha (8 hex digits)
    boolean isBeingEdited; // Flag to disable highlight during editing
//    final int FLAG_CENTER = 1;
    final int FLAG_BAR = 2;
    final int FLAG_ESCAPE = 4;
    final int FLAG_OPPOSITE_BG = 8; // Use opposite background color
    public TextElm(int xx, int yy) {
	super(xx, yy);
	text = "hello";
	lines = new Vector<String>();
	lines.add(text);
	size = 24;
	colorRGBA = "808080FF"; // Default: opaque gray
	isBeingEdited = false;
    }
    public TextElm(int xa, int ya, int xb, int yb, int f,
		   StringTokenizer st) {
	super(xa, ya, xb, yb, f);
	size = Integer.parseInt(st.nextToken());
	text = st.nextToken();
	if ((flags & FLAG_ESCAPE) == 0) {
	    // old-style dump before escape/unescape
	    while (st.hasMoreTokens())
		text += ' ' + st.nextToken();
	    text=text.replaceAll("%2[bB]", "+");
	} else {
	    // new-style dump
	    text = CustomLogicModel.unescape(text); 
	}
	// Load color if present (backward compatible)
	if (st.hasMoreTokens()) {
	    colorRGBA = st.nextToken();
	} else {
	    colorRGBA = "808080FF"; // Default: opaque gray
	}
	split();
    }
    void split() {
	int i;
	lines = new Vector<String>();
	StringBuffer sb = new StringBuffer(text);
	for (i = 0; i < sb.length(); i++) {
	    char c = sb.charAt(i);
	    if (c == '\\') {
		sb.deleteCharAt(i);
		c = sb.charAt(i);
		if (c == 'n') {
		    lines.add(sb.substring(0, i));
		    sb.delete(0, i+1);
		    i = -1;
		    continue;
		}
	    }
	}
	lines.add(sb.toString());
    }
    String dump() {
	flags |= FLAG_ESCAPE;
	return super.dump() + " " + size + " " + CustomLogicModel.escape(text) + " " + colorRGBA;
	//return super.dump() + " " + size + " " + text;
    }
    int getDumpType() { return 'x'; }
    void drag(int xx, int yy) {
	x = xx;
	y = yy;
	x2 = xx+16;
	y2 = yy;
    }
    void draw(Graphics g) {
	//Graphics2D g2 = (Graphics2D)g;
	//g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
	//	RenderingHints.VALUE_ANTIALIAS_ON);
	g.save();
	
	// Draw opposite background if flag is set
	if ((flags & FLAG_OPPOSITE_BG) != 0) {
	    drawOppositeBackground(g);
	}
	
	// Apply color with alpha blending
	// Disable highlight when being edited so user can see color changes immediately
	if (needsHighlight() && !isBeingEdited) {
	    g.setColor(selectColor);
	} else {
	    applyColorWithAlpha(g);
	}
	
	Font f = new Font("SansSerif", 0, size);
	g.setFont(f);
//	FontMetrics fm = g.getFontMetrics();
	int i;
	int maxw = -1;
	for (i = 0; i != lines.size(); i++) {
//	    int w = fm.stringWidth((String) (lines.elementAt(i)));
		int w= (int)g.context.measureText((String) (lines.elementAt(i))).getWidth();
	    if (w > maxw)
		maxw = w;
	}
	int cury = y;
	setBbox(x, y, x, y);
	for (i = 0; i != lines.size(); i++) {
	    String s = (String) (lines.elementAt(i));
	    s = Locale.LS(s);
	    
	    // Check for hyperlink pattern [[...]] and style differently
	    int linkStart = s.indexOf("[[");
	    int linkEnd = s.indexOf("]]");
	    if (linkStart >= 0 && linkEnd > linkStart) {
		// Draw text before link
		String before = s.substring(0, linkStart);
		String linkText = s.substring(linkStart + 2, linkEnd);
		String after = s.substring(linkEnd + 2);
		
		int curx = x;
		
		// Draw before text in normal color
		if (before.length() > 0) {
		    g.drawString(before, curx, cury);
		    curx += (int)g.context.measureText(before).getWidth();
		}
		
		// Draw link text in blue with underline
		if (needsHighlight() && !isBeingEdited) {
		    g.setColor(selectColor);
		} else {
		    g.setColor("#4488FF");
		}
		g.drawString(linkText, curx, cury);
		int linkWidth = (int)g.context.measureText(linkText).getWidth();
		g.drawLine(curx, cury + 2, curx + linkWidth, cury + 2); // underline
		curx += linkWidth;
		
		// Restore color and draw after text
		if (needsHighlight() && !isBeingEdited) {
		    g.setColor(selectColor);
		} else {
		    applyColorWithAlpha(g);
		}
		if (after.length() > 0) {
		    g.drawString(after, curx, cury);
		    curx += (int)g.context.measureText(after).getWidth();
		}
		
		int sw = curx - x;
		adjustBbox(x, cury-g.currentFontSize, x+sw, cury+3);
	    } else {
		// Normal text, no link
		int sw=(int)g.context.measureText(s).getWidth();
		g.drawString(s, x, cury);
		if ((flags & FLAG_BAR) != 0) {
		    int by = cury-g.currentFontSize;
		    g.drawLine(x, by, x+sw-1, by);
		}
		adjustBbox(x, cury-g.currentFontSize, x+sw, cury+3);
	    }
	    cury += g.currentFontSize+3;
	}
	x2 = boundingBox.x + boundingBox.width;
	y2 = boundingBox.y + boundingBox.height;
	g.restore();
    }
    
    /**
     * Draw opposite background color behind text
     * White background on black canvas, black background on white canvas
     */
    void drawOppositeBackground(Graphics g) {
	// Calculate text bounding box first
	g.save();
	Font f = new Font("SansSerif", 0, size);
	g.setFont(f);
	
	int maxw = 0;
	for (int i = 0; i != lines.size(); i++) {
	    int w = (int)g.context.measureText((String) (lines.elementAt(i))).getWidth();
	    if (w > maxw)
		maxw = w;
	}
	
	int totalHeight = lines.size() * (g.currentFontSize + 3);
	int padding = 4;
	
	// Determine background color based on canvas mode
	boolean isWhiteBackground = sim.printableCheckItem.getState();
	String bgColor = isWhiteBackground ? "rgba(0, 0, 0, 0.3)" : "rgba(255, 255, 255, 0.3)";
	
	// Draw semi-transparent background rectangle
	g.context.setFillStyle(bgColor);
	g.context.fillRect(x - padding, y - g.currentFontSize - padding, 
	                   maxw + 2 * padding, totalHeight + 2 * padding);
	
	g.restore();
    }
    
    /**
     * Parse RGBA color string and apply to graphics context with alpha blending
     * Supports formats: "RRGGBBAA" or "RR GG BB AA"
     */
    void applyColorWithAlpha(Graphics g) {
	try {
	    // Remove spaces from input
	    String colorHex = colorRGBA.replaceAll("\\s+", "");
	    
	    // Ensure we have exactly 8 hex digits
	    if (colorHex.length() != 8) {
		// Fallback to gray if invalid
		g.setColor(lightGrayColor);
		return;
	    }
	    
	    // Parse RGBA components
	    int red = Integer.parseInt(colorHex.substring(0, 2), 16);
	    int green = Integer.parseInt(colorHex.substring(2, 4), 16);
	    int blue = Integer.parseInt(colorHex.substring(4, 6), 16);
	    int alpha = Integer.parseInt(colorHex.substring(6, 8), 16);
	    
	    // Apply color with alpha using rgba() format
	    double alphaValue = alpha / 255.0;
	    String rgbaColor = "rgba(" + red + "," + green + "," + blue + "," + alphaValue + ")";
	    g.setColor(rgbaColor);
	} catch (Exception e) {
	    // Fallback to default if parsing fails
	    g.setColor(lightGrayColor);
	}
    }
    
    /**
     * Format RGBA string for display (adds spaces between components)
     */
    String formatColorForDisplay() {
	String hex = colorRGBA.replaceAll("\\s+", "");
	if (hex.length() == 8) {
	    return hex.substring(0, 2) + " " + hex.substring(2, 4) + " " + 
	           hex.substring(4, 6) + " " + hex.substring(6, 8);
	}
	return hex;
    }
    
    public EditInfo getEditInfo(int n) {
	if (n == 0) {
	    EditInfo ei = new EditInfo("Text", 0, -1, -1);
	    ei.text = text;
	    return ei;
	}
	if (n == 1)
	    return new EditInfo("Size", size, 5, 100);
	if (n == 2) {
	    EditInfo ei = new EditInfo("", 0, -1, -1);
	    ei.checkbox =
		new Checkbox("Draw Bar On Top", (flags & FLAG_BAR) != 0);
	    return ei;
	}
	if (n == 3) {
	    EditInfo ei = new EditInfo("Color (RGBA hex: RR GG BB AA or RRGGBBAA)", 0, -1, -1);
	    ei.text = formatColorForDisplay();
	    // Add handler for immediate color updates as user types
	    ei.keyUpHandler = new KeyUpHandler() {
		public void onKeyUp(KeyUpEvent event) {
		    String input = ei.textf.getText().trim();
		    String newColor = normalizeColorInput(input);
		    if (!newColor.equals(colorRGBA)) {
			colorRGBA = newColor;
			isBeingEdited = true;
			// Request redraw to show color immediately
			if (sim != null) {
			    sim.needAnalyze();
			}
		    }
		}
	    };
	    return ei;
	}
	if (n == 4) {
	    EditInfo ei = new EditInfo("", 0, -1, -1);
	    ei.checkbox =
		new Checkbox("Opposite Background", (flags & FLAG_OPPOSITE_BG) != 0);
	    return ei;
	}
	return null;
    }
    public void setEditValue(int n, EditInfo ei) {
	if (n == 0) {
	    text = ei.textf.getText();
	    split();
	}
	if (n == 1)
	    size = (int) ei.value;
	if (n == 2) {
	    if (ei.checkbox.getState())
		flags |= FLAG_BAR;
	    else
		flags &= ~FLAG_BAR;
	}
	if (n == 3) {
	    String input = ei.textf.getText().trim();
	    // Validate and normalize the color input
	    String newColor = normalizeColorInput(input);
	    if (!newColor.equals(colorRGBA)) {
		colorRGBA = newColor;
		// Mark as being edited to disable highlight and show color immediately
		isBeingEdited = true;
		// Reset the flag after a brief moment (will be handled by next draw cycle)
		// The flag will be cleared when user clicks elsewhere
	    }
	}
	if (n == 4) {
	    if (ei.checkbox.getState())
		flags |= FLAG_OPPOSITE_BG;
	    else
		flags &= ~FLAG_OPPOSITE_BG;
	}
    }
    
    // Override needsHighlight to handle editing state
    @Override
    public boolean needsHighlight() {
	boolean highlight = super.needsHighlight();
	// Clear editing flag if no longer highlighted (user clicked elsewhere)
	if (!highlight && isBeingEdited) {
	    isBeingEdited = false;
	}
	return highlight;
    }
    
    /**
     * Normalize color input to RRGGBBAA format (no spaces)
     * Accepts "RRGGBBAA" or "RR GG BB AA" formats
     */
    String normalizeColorInput(String input) {
	// Remove all spaces
	String normalized = input.replaceAll("\\s+", "");
	
	// Validate hex format and length
	if (normalized.matches("[0-9A-Fa-f]{8}")) {
	    return normalized.toUpperCase();
	}
	
	// If invalid, return current color unchanged
	return colorRGBA;
    }
    
//    boolean isCenteredText() { return (flags & FLAG_CENTER) != 0; }
    void getInfo(String arr[]) {
	arr[0] = text;
    }
    @Override
    int getShortcut() { return 't'; }
    
    /**
     * Check if this text element contains a hyperlink pattern [[...]]
     * and return the link text if found.
     * Supports:
     *   [[info]] - opens model info dialog
     *   [[url:http://example.com]] - opens URL in new window
     */
    String getLinkText() {
	// Check for [[...]] pattern in any line
	for (int i = 0; i < lines.size(); i++) {
	    String line = lines.get(i);
	    int start = line.indexOf("[[");
	    int end = line.indexOf("]]");
	    if (start >= 0 && end > start + 2) {
		return line.substring(start + 2, end);
	    }
	}
	return null;
    }
    
    /**
     * Check if this text element is a clickable hyperlink.
     */
    boolean isHyperlink() {
	return getLinkText() != null;
    }
    
    /**
     * Handle click on hyperlink.
     * Returns true if the click was handled.
     */
    boolean handleLinkClick() {
	String linkText = getLinkText();
	if (linkText == null) return false;
	
	String lowerLink = linkText.toLowerCase().trim();
	
	// Handle [[info]] - open model info dialog
	if (lowerLink.equals("info") || lowerLink.equals("model info") || 
	    lowerLink.equals("about") || lowerLink.equals("help")) {
	    if (sim.modelInfoContent != null && !sim.modelInfoContent.isEmpty()) {
		InfoViewerDialog.showInfoInIframe("Model Information", sim.modelInfoContent);
	    } else {
		com.google.gwt.user.client.Window.alert("No model information available.\nAdd an @info block to your SFCR file.");
	    }
	    return true;
	}
	
	// Handle [[url:...]] - open URL in new window
	if (lowerLink.startsWith("url:")) {
	    String url = linkText.substring(4).trim();
	    com.google.gwt.user.client.Window.open(url, "_blank", "");
	    return true;
	}
	
	return false;
    }
}
