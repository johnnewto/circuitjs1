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

package com.lushprojects.circuitjs1.client.ui;
import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;

import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.elements.annotation.TextElm;
import com.lushprojects.circuitjs1.client.test.TableElementsTest;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

/**
 * TableElementsTestDialog - Dialog for running table element tests
 * 
 * This dialog provides a UI for running the complete test suite for table-based
 * circuit elements (GodleyTableElm, CTMElm, etc.)
 * 
 * Features:
 * - Non-modal dialog that stays on top
 * - Run all tests or individual test methods
 * - Live console output showing test progress
 * - Color-coded pass/fail indicators
 */
public class TableElementsTestDialog {

    @JsFunction
    private interface ConsoleMethod {
        void invoke(Object msg);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Console")
    private static class ConsoleLike {
        @JsProperty(name = "log") native ConsoleMethod getLog();
        @JsProperty(name = "log") native void setLog(ConsoleMethod method);
        @JsProperty(name = "error") native ConsoleMethod getError();
        @JsProperty(name = "error") native void setError(ConsoleMethod method);
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "window")
    private static class GlobalWindowLike {
        @JsProperty(name = "console") static native ConsoleLike getConsole();
        @JsProperty(name = "_oldLog") static native ConsoleMethod getOldLog();
        @JsProperty(name = "_oldLog") static native void setOldLog(ConsoleMethod method);
        @JsProperty(name = "_oldError") static native ConsoleMethod getOldError();
        @JsProperty(name = "_oldError") static native void setOldError(ConsoleMethod method);
    }
    
    private DialogBox dialog;
    private TextArea outputArea;
    private CirSim sim;
    private TableElementsTest testSuite;
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
    public TableElementsTestDialog() {
        this.sim = CirSim.getInstance();
        this.testSuite = new TableElementsTest();
        createDialog();
    }
    
    /**
     * Create the dialog UI
     */
    private void createDialog() {
        dialog = new DialogBox();
        dialog.setText("Table Elements Test Suite");
        dialog.setModal(false);
        dialog.setGlassEnabled(false);
        
        VerticalPanel panel = new VerticalPanel();
        panel.setWidth("600px");
        
        // Test info header
        VerticalPanel infoPanel = new VerticalPanel();
        infoPanel.getElement().getStyle().setProperty("padding", "10px");
        infoPanel.getElement().getStyle().setProperty("backgroundColor", "#f0f0f0");
        infoPanel.getElement().getStyle().setProperty("borderBottom", "2px solid #ccc");
        
        addInfoText(infoPanel, "Test Coverage: Table-based circuit elements");
        addInfoText(infoPanel, "Elements: GodleyTableElm, CTMElm, Stock-Flow models");
        
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
    
    private void addInfoText(VerticalPanel panel, String text) {
        Label label = new Label(text);
        label.getElement().getStyle().setProperty("fontSize", "12px");
        label.getElement().getStyle().setProperty("marginBottom", "5px");
        panel.add(label);
    }
    
    public void show() {
        dialog.show();
        dialog.setPopupPosition(
            com.google.gwt.user.client.Window.getClientWidth() - 620,
            50
        );
    }
    
    private void hide() {
        dialog.hide();
    }
    
    public boolean isShowing() {
        return dialog.isShowing();
    }
    
    private void clearOutput() {
        outputArea.setText("");
        passCount = 0;
        failCount = 0;
    }
    
    private void appendOutput(String text) {
        String current = outputArea.getText();
        outputArea.setText(current + text + "\n");
        outputArea.getElement().setScrollTop(outputArea.getElement().getScrollHeight());
    }
    
    private void runAllTests() {
        clearOutput();
        runButton.setEnabled(false);
        runButton.setText("⏳ Running...");
        
        appendOutput("========================================");
        appendOutput("CircuitJS1 Table Elements Test Suite");
        appendOutput("========================================");
        appendOutput("");
        appendOutput("Running " + testSuite.getTestNames().length + " tests...");
        appendOutput("");
        
        passCount = 0;
        failCount = 0;
        
        redirectConsoleOutput();
        
        try {
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
    
    private String detectCanvasTest() {
        return findCanvasTestLabel(testSuite.getTestNames());
    }

    private String findCanvasTestLabel(String[] testNames) {
        if (testNames == null || testNames.length == 0)
            return null;
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (!(ce instanceof TextElm))
                continue;
            String text = ((TextElm) ce).text;
            if (text == null)
                continue;
            for (String testName : testNames) {
                if (text.equals(testName))
                    return testName;
            }
        }
        return null;
    }
    
    private void runSelectedTest() {
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
        
        redirectConsoleOutput();
        
        try {
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
    
    private void runCanvasTest() {
        String testName = detectCanvasTest();
        
        if (testName == null) {
            clearOutput();
            appendOutput("❌ No test circuit detected on canvas");
            appendOutput("");
            appendOutput("To run a canvas test:");
            appendOutput("1. Load a test circuit with a label matching a test name");
            appendOutput("2. Click 'Run Test on Canvas'");
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
        
        redirectConsoleOutput();
        
        try {
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
    
    private void redirectConsoleOutput() {
        final ConsoleLike console = GlobalWindowLike.getConsole();
        final ConsoleMethod oldLog = console.getLog();
        final ConsoleMethod oldError = console.getError();

        GlobalWindowLike.setOldLog(oldLog);
        GlobalWindowLike.setOldError(oldError);

        console.setLog(new ConsoleMethod() {
            public void invoke(Object msg) {
                if (oldLog != null)
                    oldLog.invoke(msg);
                handleConsoleLog(String.valueOf(msg));
            }
        });

        console.setError(new ConsoleMethod() {
            public void invoke(Object msg) {
                if (oldError != null)
                    oldError.invoke(msg);
                handleConsoleError(String.valueOf(msg));
            }
        });
    }
    
    private void restoreConsoleOutput() {
        ConsoleLike console = GlobalWindowLike.getConsole();
        ConsoleMethod oldLog = GlobalWindowLike.getOldLog();
        ConsoleMethod oldError = GlobalWindowLike.getOldError();
        if (oldLog != null)
            console.setLog(oldLog);
        if (oldError != null)
            console.setError(oldError);
    }
    
    private void handleConsoleLog(String msg) {
        appendOutput(msg);
        
        if (msg.indexOf("PASS:") >= 0 || msg.indexOf("✓") >= 0) {
            passCount++;
        } else if (msg.indexOf("FAIL:") >= 0 || msg.indexOf("ERROR:") >= 0) {
            failCount++;
        }
    }
    
    private void handleConsoleError(String msg) {
        appendOutput("ERROR: " + msg);
        if (msg.indexOf("FAIL:") >= 0 || msg.indexOf("✗") >= 0 || msg.indexOf("AssertionError") >= 0) {
            failCount++;
        }
    }
}
