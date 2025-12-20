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

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.Location;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.RootPanel;

/**
 * Draggable floating control panel overlaying the circuit canvas.
 * Provides Run/Stop, Reset, Step, Lock, and Fullscreen controls.
 */
public class FloatingControlPanel {
    
    private final CirSim sim;
    private final HorizontalPanel panel;
    private Button runStopButton;
    
    // Drag state
    private int dragOffsetX, dragOffsetY;
    private boolean isDragging;
    private boolean transformCleared;
    
    public FloatingControlPanel(CirSim sim) {
        this.sim = sim;
        this.panel = new HorizontalPanel();
        initPanel();
    }
    
    /** Initialize panel layout, handlers, and buttons. */
    private void initPanel() {
        panel.setStyleName("floatingControlPanel");
        panel.setSpacing(5);
        
        // Constrain panel on window resize
        Window.addResizeHandler(e -> constrainToWindow());
        
        setupDragHandlers();
        createButtons();
        
        RootPanel.get().add(panel);
        
        // Ensure panel is visible after layout is complete
        Scheduler.get().scheduleDeferred(() -> constrainToWindow());
    }
    
    /** Set up mouse handlers for dragging the panel. */
    private void setupDragHandlers() {
        // Start drag on mouse down
        panel.addDomHandler(e -> {
            isDragging = true;
            Element elem = panel.getElement();
            int visualLeft = (int) getBoundingRectLeft(elem);
            int visualTop = (int) getBoundingRectTop(elem);
            
            // Clear CSS transform on first drag, switch to explicit positioning
            if (!transformCleared) {
                Style style = elem.getStyle();
                style.clearProperty("transform");
                style.setProperty("left", visualLeft + "px");
                style.setProperty("top", visualTop + "px");
                transformCleared = true;
            }
            
            dragOffsetX = e.getClientX() - visualLeft;
            dragOffsetY = e.getClientY() - visualTop;
            e.preventDefault();
        }, MouseDownEvent.getType());
        
        // Move panel while dragging
        RootPanel.get().addDomHandler(e -> {
            if (!isDragging) return;
            
            int left = constrainX(e.getClientX() - dragOffsetX);
            int top = constrainY(e.getClientY() - dragOffsetY);
            
            Style style = panel.getElement().getStyle();
            style.setProperty("left", left + "px");
            style.setProperty("top", top + "px");
            e.preventDefault();
        }, MouseMoveEvent.getType());
        
        // End drag on mouse up
        RootPanel.get().addDomHandler(e -> isDragging = false, MouseUpEvent.getType());
    }
    
    /** Create all control buttons. */
    private void createButtons() {
        // Run/Stop button with special state handling
        runStopButton = createButton("Run", "floatingButton", e -> {
            ActionScheduler scheduler = ActionScheduler.getInstance();
            if (scheduler != null && scheduler.isPaused() && scheduler.hasPendingTimer()) {
                // Cancel ActionScheduler pause and stop
                scheduler.cancelResumeTimer();
                sim.setSimRunning(false);
            } else {
                sim.setSimRunning(!sim.simIsRunning());
            }
        });
        
        // Reset button
        createButton("Reset", "floatingButton", e -> sim.resetAction());
        
        // Step button - single step through simulation
        createButton("Step", "floatingButton", e -> {
            sim.setSimRunning(false);
            sim.stepCircuit();
        });
        
        // Lock button - toggle edit lock
        createIconButton("cirjsicon-lock-open", "Toggle Edit Lock", e -> {
            boolean locked = !sim.noEditCheckItem.getState();
            sim.noEditCheckItem.setState(locked);
            ((Button) e.getSource()).setHTML(locked 
                ? "<i class=\"cirjsicon-lock\"></i>" 
                : "<i class=\"cirjsicon-lock-open\"></i>");
        });
        
        // Fullscreen button
        createIconButton("cirjsicon-resize-full-alt", "Toggle Full Screen", e -> {
            if (Graphics.isFullScreen)
                Graphics.exitFullScreen();
            else
                Graphics.viewFullScreen();
        });
        
        // Share button - create short URL and copy to clipboard
        if (isShortUrlSupported()) {
            createIconButton("cirjsicon-export", "Share Circuit (Copy Short URL)", e -> {
                shareCircuit((Button) e.getSource());
            });
        }
    }
    
    /** Check if short URL feature is supported (configured in circuitjs.html) */
    private boolean isShortUrlSupported() {
        return ExportAsUrlDialog.getShortRelayUrl() != null;
    }
    
    /** Share the circuit by creating a short URL and copying to clipboard */
    private void shareCircuit(Button shareButton) {
        // Get the circuit dump
        String dump = sim.dumpCircuit();
        
        // Build the URL (same as ExportAsUrlDialog)
        String[] start = Location.getHref().split("\\?");
        if (CirSim.isElectron())
            start[0] = "https://johnnewto.github.io/circuitjs1/circuitjs.html";
        String query = "?ctz=" + compress(dump) + "&editable=false";
        String requrl = URL.encodeQueryString(query);
        
        // Get relay URL
        String relayUrl = ExportAsUrlDialog.getShortRelayUrl();
        if (relayUrl == null)
            relayUrl = "shortrelay.php";
        String url = relayUrl + "?v=" + requrl;
        
        // Update button to show loading state
        String originalHtml = shareButton.getHTML();
        shareButton.setHTML("<i class=\"cirjsicon-cw share-spinner\"></i>");
        shareButton.setEnabled(false);
        
        // Make the request
        RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.GET, url);
        try {
            requestBuilder.sendRequest(null, new RequestCallback() {
                public void onError(Request request, Throwable exception) {
                    GWT.log("Share Error", exception);
                    shareButton.setHTML(originalHtml);
                    shareButton.setEnabled(true);
                    Window.alert(Locale.LS("Failed to create short URL"));
                }

                public void onResponseReceived(Request request, Response response) {
                    shareButton.setHTML(originalHtml);
                    shareButton.setEnabled(true);
                    
                    if (response.getStatusCode() == Response.SC_OK) {
                        String shortUrl = response.getText().trim();
                        copyToClipboard(shortUrl);
                        showShareNotification(shortUrl);
                    } else {
                        Window.alert(Locale.LS("Shortener error: ") + response.getStatusText());
                    }
                }
            });
        } catch (RequestException e) {
            GWT.log("Share request failed", e);
            shareButton.setHTML(originalHtml);
            shareButton.setEnabled(true);
            Window.alert(Locale.LS("Failed to create short URL"));
        }
    }
    
    /** Show a toast notification that URL was copied */
    private void showShareNotification(String shortUrl) {
        // Create toast notification element
        com.google.gwt.dom.client.DivElement toast = 
            com.google.gwt.dom.client.Document.get().createDivElement();
        toast.setInnerHTML("<i class=\"cirjsicon-export\"></i>&nbsp;" + 
            Locale.LS("Short URL copied!") + " <span class=\"toast-url\">" + shortUrl + "</span>");
        toast.setClassName("toast-notification");
        
        com.google.gwt.dom.client.BodyElement body = 
            com.google.gwt.dom.client.Document.get().getBody();
        body.appendChild(toast);
        
        // Remove after animation completes (3 seconds)
        com.google.gwt.user.client.Timer timer = new com.google.gwt.user.client.Timer() {
            @Override
            public void run() {
                toast.removeFromParent();
            }
        };
        timer.schedule(3500);
    }
    
    /** Compress circuit data using LZString (same as ExportAsUrlDialog) */
    private native String compress(String dump) /*-{
        return $wnd.LZString.compressToEncodedURIComponent(dump);
    }-*/;
    
    /** Copy text to clipboard */
    private native void copyToClipboard(String text) /*-{
        // Use the fallback method which is more reliable
        var textArea = document.createElement("textarea");
        textArea.value = text;
        textArea.style.position = "fixed";  // Avoid scrolling to bottom
        textArea.style.top = "0";
        textArea.style.left = "0";
        textArea.style.width = "2em";
        textArea.style.height = "2em";
        textArea.style.padding = "0";
        textArea.style.border = "none";
        textArea.style.outline = "none";
        textArea.style.boxShadow = "none";
        textArea.style.background = "transparent";
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        try {
            document.execCommand('copy');
        } catch (err) {
            console.error('Failed to copy to clipboard:', err);
        }
        document.body.removeChild(textArea);
    }-*/;
    
    /** Create a text button. */
    private Button createButton(String text, String style, ClickHandler handler) {
        Button btn = new Button(Locale.LS(text));
        btn.setStyleName(style);
        btn.addClickHandler(handler);
        panel.add(btn);
        return btn;
    }
    
    /** Create an icon button with tooltip. */
    private Button createIconButton(String iconClass, String tooltip, ClickHandler handler) {
        Button btn = new Button();
        btn.setHTML("<i class=\"" + iconClass + "\"></i>");
        btn.setTitle(Locale.LS(tooltip));
        btn.setStyleName("floatingButton floatingButton-icon");
        btn.addClickHandler(handler);
        panel.add(btn);
        return btn;
    }
    
    /** Constrain X position to keep entire panel visible. */
    private int constrainX(int x) {
        int panelWidth = panel.getOffsetWidth();
        int maxLeft = Window.getClientWidth() - panelWidth;
        return Math.max(0, Math.min(x, maxLeft));
    }
    
    /** Constrain Y position to keep panel visible. */
    private int constrainY(int y) {
        int maxTop = Window.getClientHeight() - panel.getOffsetHeight();
        return Math.max(0, Math.min(y, maxTop));
    }
    
    /** Ensure panel stays within window bounds. */
    private void constrainToWindow() {
        int left = panel.getAbsoluteLeft();
        int top = panel.getAbsoluteTop();
        int newLeft = constrainX(left);
        int newTop = constrainY(top);
        
        if (newLeft != left || newTop != top) {
            Style style = panel.getElement().getStyle();
            style.setProperty("left", newLeft + "px");
            style.setProperty("top", newTop + "px");
        }
    }
    
    /**
     * Update Run/Stop button appearance based on simulation state.
     * States: Red (stopped), Orange (paused by ActionScheduler), Green (running)
     */
    public void updateRunStopButton() {
        ActionScheduler scheduler = ActionScheduler.getInstance();
        String text, styleModifier;
        
        if (scheduler != null && scheduler.isPaused() && scheduler.hasPendingTimer()) {
            text = "Paused";
            styleModifier = "floatingButton-paused";
        } else if (!sim.simIsRunning()) {
            text = "Run";
            styleModifier = "floatingButton-stopped";
        } else {
            text = "Stop";
            styleModifier = "floatingButton-running";
        }
        
        runStopButton.setText(Locale.LS(text));
        runStopButton.setStyleName("floatingButton floatingButton-runstop " + styleModifier);
    }
    
    // Accessors for external use
    public Button getRunStopButton() { return runStopButton; }
    
    // Native methods to get visual position including CSS transforms
    private native double getBoundingRectLeft(Element elem) /*-{
        return elem.getBoundingClientRect().left;
    }-*/;
    
    private native double getBoundingRectTop(Element elem) /*-{
        return elem.getBoundingClientRect().top;
    }-*/;
}
