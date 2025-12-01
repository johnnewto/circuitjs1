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
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;

/**
 * MathElementsTestDialog - Always-on-top dialog for running mathematical element tests
 * 
 * This dialog provides a UI for running the complete test suite for mathematical
 * circuit elements (AdderElm, SubtracterElm, MultiplyElm, etc.) without interfering
 * with the main CircuitJS1 interface.
 * 
 * Features:
 * - Non-modal dialog that stays on top
 * - Run all tests or individual test methods
 * - Live console output showing test progress
 * - Color-coded pass/fail indicators
 * - Resizable text area for viewing detailed results
 */
public class MathElementsTestDialog {
    
    private DialogBox dialog;
    private TextArea outputArea;
    private CirSim sim;
    private MathElementsTest testSuite;
    private Button runButton;
    private Button runCanvasTestButton;
    private Button runSelectedTestButton;
    private ListBox testSelector;
    private Button clearButton;
    private Button closeButton;
    private int passCount = 0;
    private int failCount = 0;
    
    /**
     * Create the test dialog
     */
    public MathElementsTestDialog() {
        this.sim = CirSim.theSim;
        this.testSuite = new MathElementsTest();
        createDialog();
    }
    
    /**
     * Create the dialog UI
     */
    private void createDialog() {
        dialog = new DialogBox();
        dialog.setText("Math Elements Test Suite");
        dialog.setModal(false);  // Non-modal so it doesn't block interaction
        dialog.setGlassEnabled(false);  // No glass pane background
        
        VerticalPanel panel = new VerticalPanel();
        panel.setWidth("600px");
        
        // Test info header
        VerticalPanel infoPanel = new VerticalPanel();
        infoPanel.getElement().getStyle().setProperty("padding", "10px");
        infoPanel.getElement().getStyle().setProperty("backgroundColor", "#f0f0f0");
        infoPanel.getElement().getStyle().setProperty("borderBottom", "2px solid #ccc");
        
        addInfoText(infoPanel, "Test Coverage: 16 test methods covering 10 mathematical element types");
        addInfoText(infoPanel, "Elements: AdderElm, SubtracterElm, MultiplyElm, MultiplyConstElm, DividerElm, DifferentiatorElm, IntegratorElm, ODEElm, EquationElm, PercentElm");
        
        panel.add(infoPanel);
        
        // Output text area
        outputArea = new TextArea();
        outputArea.setText("Click 'Run All Tests' to start testing...\n");
        outputArea.setWidth("580px");
        outputArea.setHeight("500px");
        outputArea.setReadOnly(true);
        outputArea.getElement().getStyle().setProperty("fontFamily", "monospace");
        outputArea.getElement().getStyle().setProperty("fontSize", "12px");
        outputArea.getElement().getStyle().setProperty("backgroundColor", "#1e1e1e");
        outputArea.getElement().getStyle().setProperty("color", "#d4d4d4");
        outputArea.getElement().getStyle().setProperty("padding", "10px");
        outputArea.getElement().getStyle().setProperty("marginTop", "10px");
        
        panel.add(outputArea);
        
        // Test selector row
        HorizontalPanel selectorPanel = new HorizontalPanel();
        selectorPanel.setSpacing(10);
        selectorPanel.getElement().getStyle().setProperty("marginTop", "10px");
        selectorPanel.getElement().getStyle().setProperty("alignItems", "center");
        
        Label selectorLabel = new Label("Select Test:");
        selectorLabel.getElement().getStyle().setProperty("fontWeight", "bold");
        selectorPanel.add(selectorLabel);
        
        testSelector = new ListBox();
        testSelector.setWidth("300px");
        testSelector.getElement().getStyle().setProperty("padding", "5px");
        
        // Populate with test names
        String[] testNames = testSuite.getTestNames();
        for (String testName : testNames) {
            testSelector.addItem(testName);
        }
        selectorPanel.add(testSelector);
        
        runSelectedTestButton = new Button("▶ Run Selected Test");
        runSelectedTestButton.getElement().setAttribute("style", 
            "background-color: #FF9800 !important; " +
            "background-image: none !important; " +
            "color: white !important; " +
            "padding: 8px 16px !important; " +
            "font-size: 14px !important; " +
            "font-weight: bold !important; " +
            "border: 2px solid #F57C00 !important; " +
            "border-radius: 4px !important; " +
            "cursor: pointer !important; " +
            "box-shadow: 0 2px 4px rgba(0,0,0,0.3) !important;");
        
        runSelectedTestButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                runSelectedTest();
            }
        });
        selectorPanel.add(runSelectedTestButton);
        
        panel.add(selectorPanel);
        
        // Buttons
        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setSpacing(5);
        buttonPanel.getElement().getStyle().setProperty("marginTop", "10px");
        
        runButton = new Button("▶ Run All Tests");
        runButton.getElement().setAttribute("style", 
            "background-color: #4CAF50 !important; " +
            "background-image: none !important; " +
            "color: white !important; " +
            "padding: 10px 20px !important; " +
            "font-size: 14px !important; " +
            "font-weight: bold !important; " +
            "border: 2px solid #388E3C !important; " +
            "border-radius: 4px !important; " +
            "cursor: pointer !important; " +
            "box-shadow: 0 2px 4px rgba(0,0,0,0.3) !important;");
        
        runButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                runAllTests();
            }
        });
        buttonPanel.add(runButton);
        
        runCanvasTestButton = new Button("▶ Run Test on Canvas");
        runCanvasTestButton.getElement().setAttribute("style", 
            "background-color: #2196F3 !important; " +
            "background-image: none !important; " +
            "color: white !important; " +
            "padding: 10px 20px !important; " +
            "font-size: 14px !important; " +
            "font-weight: bold !important; " +
            "border: 2px solid #1976D2 !important; " +
            "border-radius: 4px !important; " +
            "cursor: pointer !important; " +
            "box-shadow: 0 2px 4px rgba(0,0,0,0.3) !important;");
        
        runCanvasTestButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                runCanvasTest();
            }
        });
        buttonPanel.add(runCanvasTestButton);
        
        clearButton = new Button("Clear Output");
        clearButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                clearOutput();
            }
        });
        buttonPanel.add(clearButton);
        
        closeButton = new Button("Close");
        closeButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                hide();
            }
        });
        buttonPanel.add(closeButton);
        
        panel.add(buttonPanel);
        dialog.setWidget(panel);
    }
    
    /**
     * Add info text to panel
     */
    private void addInfoText(VerticalPanel panel, String text) {
        com.google.gwt.user.client.ui.Label label = new com.google.gwt.user.client.ui.Label(text);
        label.getElement().getStyle().setProperty("fontSize", "12px");
        label.getElement().getStyle().setProperty("marginBottom", "5px");
        panel.add(label);
    }
    
    /**
     * Show the dialog
     */
    public void show() {
        dialog.show();
        
        // Position in center-right instead of centering
        dialog.setPopupPosition(
            com.google.gwt.user.client.Window.getClientWidth() - 920,  // 180px width + 20px margin
            50  // 50px from top
        );
    }
    
    /**
     * Hide the dialog
     */
    public void hide() {
        dialog.hide();
    }
    
    /**
     * Check if dialog is currently showing
     */
    public boolean isShowing() {
        return dialog.isShowing();
    }
    
    /**
     * Clear output area
     */
    public void clearOutput() {
        outputArea.setText("");
        passCount = 0;
        failCount = 0;
    }
    
    /**
     * Append text to output
     */
    private void appendOutput(String text) {
        String current = outputArea.getText();
        outputArea.setText(current + text + "\n");
        
        // Auto-scroll to bottom
        outputArea.getElement().setScrollTop(outputArea.getElement().getScrollHeight());
    }
    
    /**
     * Run all tests
     */
    public void runAllTests() {
        clearOutput();
        runButton.setEnabled(false);
        runButton.setText("⏳ Running...");
        
        appendOutput("========================================");
        appendOutput("CircuitJS1 Math Elements Test Suite");
        appendOutput("========================================");
        appendOutput("");
        appendOutput("Running 16 tests...");
        appendOutput("");
        
        passCount = 0;
        failCount = 0;
        
        // Override console output to capture test results
        redirectConsoleOutput();
        
        try {
            // Run the test suite
            testSuite.runAllTests();
            
            appendOutput("");
            appendOutput("========================================");
            appendOutput("Test Results:");
            appendOutput("  ✓ Passed: " + passCount);
            if (failCount > 0) {
                appendOutput("  ✗ Failed: " + failCount);
            }
            appendOutput("========================================");
            
            if (failCount == 0) {
                appendOutput("");
                appendOutput("🎉 All tests PASSED!");
            } else {
                appendOutput("");
                appendOutput("⚠️  Some tests FAILED - check output above");
            }
            
        } catch (Exception e) {
            appendOutput("");
            appendOutput("❌ ERROR: Test execution failed");
            appendOutput("Exception: " + e.getMessage());
            if (e.getStackTrace() != null && e.getStackTrace().length > 0) {
                appendOutput("Stack trace:");
                for (int i = 0; i < Math.min(5, e.getStackTrace().length); i++) {
                    appendOutput("  " + e.getStackTrace()[i].toString());
                }
            }
            failCount++;
        } finally {
            restoreConsoleOutput();
            runButton.setEnabled(true);
            runButton.setText("▶ Run All Tests");
        }
    }
    
    /**
     * Detect which test circuit is currently on the canvas
     */
    private String detectCanvasTest() {
        // Look for text element with test name
        for (int i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof TextElm) {
                TextElm te = (TextElm) ce;
                String text = te.text;
                // Check if this matches a known test name
                String[] testNames = testSuite.getTestNames();
                for (String testName : testNames) {
                    if (text != null && text.equals(testName)) {
                        return testName;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Run the selected test from the dropdown
     */
    public void runSelectedTest() {
        int selectedIndex = testSelector.getSelectedIndex();
        if (selectedIndex < 0) {
            clearOutput();
            appendOutput("❌ No test selected");
            return;
        }
        
        String testName = testSelector.getItemText(selectedIndex);
        
        clearOutput();
        runSelectedTestButton.setEnabled(false);
        runSelectedTestButton.setText("⏳ Running...");
        
        appendOutput("========================================");
        appendOutput("Running Selected Test: " + testName);
        appendOutput("========================================");
        appendOutput("");
        
        passCount = 0;
        failCount = 0;
        
        // Override console output to capture test results
        redirectConsoleOutput();
        
        try {
            // Run the specific test
            testSuite.runTest(testName);
            
            appendOutput("");
            appendOutput("========================================");
            appendOutput("Test Result:");
            if (failCount == 0) {
                appendOutput("  ✓ PASSED");
            } else {
                appendOutput("  ✗ FAILED");
            }
            appendOutput("========================================");
            
        } catch (Exception e) {
            appendOutput("");
            appendOutput("❌ ERROR: Test execution failed");
            appendOutput("Exception: " + e.getMessage());
            if (e.getStackTrace() != null && e.getStackTrace().length > 0) {
                appendOutput("Stack trace:");
                for (int i = 0; i < Math.min(5, e.getStackTrace().length); i++) {
                    appendOutput("  " + e.getStackTrace()[i].toString());
                }
            }
            failCount++;
        } finally {
            restoreConsoleOutput();
            runSelectedTestButton.setEnabled(true);
            runSelectedTestButton.setText("▶ Run Selected Test");
        }
    }
    
    /**
     * Run the test that's currently loaded on the canvas
     */
    public void runCanvasTest() {
        String testName = detectCanvasTest();
        
        if (testName == null) {
            clearOutput();
            appendOutput("❌ No test circuit detected on canvas");
            appendOutput("");
            appendOutput("To run a canvas test:");
            appendOutput("1. Load a test circuit (it will have a label like 'testAdderElm')");
            appendOutput("2. Click 'Run Test on Canvas' to test that specific circuit");
            appendOutput("");
            appendOutput("Or click 'Run All Tests' to run the complete test suite.");
            return;
        }
        
        clearOutput();
        runCanvasTestButton.setEnabled(false);
        runCanvasTestButton.setText("⏳ Running...");
        
        appendOutput("========================================");
        appendOutput("Running Canvas Test: " + testName);
        appendOutput("========================================");
        appendOutput("");
        
        passCount = 0;
        failCount = 0;
        
        // Override console output to capture test results
        redirectConsoleOutput();
        
        try {
            // Run the specific test
            testSuite.runTest(testName);
            
            appendOutput("");
            appendOutput("========================================");
            appendOutput("Test Result:");
            if (failCount == 0) {
                appendOutput("  ✓ PASSED");
            } else {
                appendOutput("  ✗ FAILED");
            }
            appendOutput("========================================");
            
        } catch (Exception e) {
            appendOutput("");
            appendOutput("❌ ERROR: Test execution failed");
            appendOutput("Exception: " + e.getMessage());
            if (e.getStackTrace() != null && e.getStackTrace().length > 0) {
                appendOutput("Stack trace:");
                for (int i = 0; i < Math.min(5, e.getStackTrace().length); i++) {
                    appendOutput("  " + e.getStackTrace()[i].toString());
                }
            }
            failCount++;
        } finally {
            restoreConsoleOutput();
            runCanvasTestButton.setEnabled(true);
            runCanvasTestButton.setText("▶ Run Test on Canvas");
        }
    }
    
    // Store original console methods
    private native void redirectConsoleOutput() /*-{
        var self = this;
        var oldLog = $wnd.console.log;
        var oldError = $wnd.console.error;
        
        $wnd.console.log = function(msg) {
            oldLog.apply($wnd.console, arguments);
            self.@com.lushprojects.circuitjs1.client.MathElementsTestDialog::handleConsoleLog(Ljava/lang/String;)(String(msg));
        };
        
        $wnd.console.error = function(msg) {
            oldError.apply($wnd.console, arguments);
            self.@com.lushprojects.circuitjs1.client.MathElementsTestDialog::handleConsoleError(Ljava/lang/String;)(String(msg));
        };
        
        // Store old methods for restoration
        $wnd._oldLog = oldLog;
        $wnd._oldError = oldError;
    }-*/;
    
    private native void restoreConsoleOutput() /*-{
        if ($wnd._oldLog) {
            $wnd.console.log = $wnd._oldLog;
        }
        if ($wnd._oldError) {
            $wnd.console.error = $wnd._oldError;
        }
    }-*/;
    
    /**
     * Handle console.log output
     */
    private void handleConsoleLog(String msg) {
        appendOutput(msg);
        
        // Count pass/fail
        if (msg.indexOf("PASS:") >= 0 || msg.indexOf("✓") >= 0) {
            passCount++;
        } else if (msg.indexOf("FAIL:") >= 0 || msg.indexOf("ERROR:") >= 0) {
            failCount++;
        }
    }
    
    /**
     * Handle console.error output
     */
    private void handleConsoleError(String msg) {
        appendOutput("ERROR: " + msg);
        if (msg.indexOf("FAIL:") >= 0 || msg.indexOf("✗") >= 0 || msg.indexOf("AssertionError") >= 0) {
            failCount++;
        }
    }
}
