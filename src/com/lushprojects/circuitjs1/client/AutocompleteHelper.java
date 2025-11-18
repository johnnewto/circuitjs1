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

import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Label;

/**
 * AutocompleteHelper - Shared bash-style Tab completion functionality
 * 
 * Provides reusable autocomplete logic for TextBox fields across dialogs.
 * Implements bash-style completion with Tab cycling and real-time validation.
 */
public class AutocompleteHelper {
    
    /**
     * State for bash-style autocomplete (per TextBox)
     */
    public static class AutocompleteState {
        public java.util.List<String> currentMatches;
        public int currentMatchIndex;
        public int completionStartPos;
        public String originalWord;
        
        public AutocompleteState() {
            reset();
        }
        
        public void reset() {
            currentMatches = null;
            currentMatchIndex = -1;
            completionStartPos = -1;
            originalWord = null;
        }
        
        public boolean isActive() {
            return currentMatches != null && !currentMatches.isEmpty();
        }
    }
    
    /**
     * Handles Tab key completion (bash-style behavior)
     */
    public static void handleTabCompletion(TextBox textBox, java.util.List<String> completionList, 
                                          Label hintLabel, AutocompleteState state) {
        if (!state.isActive()) {
            // First Tab press - start new completion
            startNewCompletion(textBox, completionList, hintLabel, state);
        } else {
            // Subsequent Tab presses - cycle through matches
            cycleToNextMatch(textBox, hintLabel, state);
        }
    }
    
    /**
     * Start new completion on first Tab press
     */
    private static void startNewCompletion(TextBox textBox, java.util.List<String> completionList, 
                                          Label hintLabel, AutocompleteState state) {
        String currentWord = getCurrentWord(textBox);
        if (currentWord.isEmpty()) {
            state.reset();
            hintLabel.setVisible(false);
            return;
        }
        
        // Find all matches
        java.util.List<String> matches = findMatches(currentWord, completionList);
        
        if (matches.isEmpty()) {
            state.reset();
            hintLabel.setVisible(false);
            return;
        }
        
        if (matches.size() == 1) {
            // Single match - complete immediately
            completeCurrentWord(textBox, matches.get(0));
            state.reset();
            hintLabel.setVisible(false);
        } else {
            // Multiple matches - complete with first match immediately
            completeCurrentWord(textBox, matches.get(0));
            
            // Setup state for cycling
            state.currentMatches = matches;
            state.currentMatchIndex = 0; // Start at first match (already inserted)
            state.completionStartPos = findWordStart(textBox);
            state.originalWord = currentWord;
            
            // Show matches with first one highlighted
            displayMatches(matches, hintLabel, 0);
        }
    }
    
    /**
     * Cycle to next match on subsequent Tab presses
     */
    private static void cycleToNextMatch(TextBox textBox, Label hintLabel, AutocompleteState state) {
        if (!state.isActive()) return;
        
        state.currentMatchIndex = (state.currentMatchIndex + 1) % state.currentMatches.size();
        String nextMatch = state.currentMatches.get(state.currentMatchIndex);
        
        // Replace from completion start position
        String text = textBox.getText();
        
        // Find word boundaries
        int wordStart = state.completionStartPos;
        int wordEnd = wordStart;
        while (wordEnd < text.length() && isIdentifierChar(text.charAt(wordEnd))) {
            wordEnd++;
        }
        
        // Replace word
        String newText = text.substring(0, wordStart) + nextMatch + text.substring(wordEnd);
        textBox.setText(newText);
        textBox.setCursorPos(wordStart + nextMatch.length());
        
        // Update hint to highlight current match
        displayMatches(state.currentMatches, hintLabel, state.currentMatchIndex);
    }
    
    /**
     * Validate on open - show only undefined symbols (no matches)
     */
    public static void validateOnOpen(TextBox textBox, java.util.List<String> completionList, Label hintLabel) {
        String text = textBox.getText();
        if (text.trim().isEmpty()) {
            hintLabel.setVisible(false);
            return;
        }
        
        // Extract identifiers from equation
        java.util.List<String> identifiers = extractIdentifiers(text);
        
        // Find undefined symbols
        java.util.List<String> undefinedSymbols = new java.util.ArrayList<String>();
        for (String id : identifiers) {
            if (!isKnownSymbol(id, completionList)) {
                if (!undefinedSymbols.contains(id)) {
                    undefinedSymbols.add(id);
                }
            }
        }
        
        // Display only undefined symbols
        displayUndefinedSymbols(undefinedSymbols, hintLabel);
    }
    
    /**
     * Update match display while typing - show both undefined symbols AND matches
     */
    public static void updateMatchDisplay(TextBox textBox, java.util.List<String> completionList, 
                                         Label hintLabel, AutocompleteState state) {
        // Reset tab completion state when user types
        state.reset();
        
        String text = textBox.getText();
        if (text.trim().isEmpty()) {
            hintLabel.setVisible(false);
            return;
        }
        
        // Extract identifiers from equation
        java.util.List<String> identifiers = extractIdentifiers(text);
        
        // Find undefined symbols
        java.util.List<String> undefinedSymbols = new java.util.ArrayList<String>();
        for (String id : identifiers) {
            if (!isKnownSymbol(id, completionList)) {
                if (!undefinedSymbols.contains(id)) {
                    undefinedSymbols.add(id);
                }
            }
        }
        
        // Get current word for matching
        String currentWord = getCurrentWord(textBox);
        java.util.List<String> matches = new java.util.ArrayList<String>();
        if (!currentWord.isEmpty()) {
            matches = findMatches(currentWord, completionList);
        }
        
        // Display both undefined symbols and matches
        displayValidationAndMatches(undefinedSymbols, matches, hintLabel);
    }
    
    /**
     * Create styled hint label for displaying autocomplete hints
     */
    public static Label createHintLabel() {
        Label label = new Label();
        label.setStyleName("autocomplete-hint");
        label.setVisible(false);
        
        // Style: small monospace text in a subtle bordered box
        label.getElement().getStyle().setProperty("fontSize", "11px");
        label.getElement().getStyle().setProperty("color", "#666");
        label.getElement().getStyle().setProperty("fontFamily", "monospace");
        label.getElement().getStyle().setProperty("whiteSpace", "pre-wrap");
        label.getElement().getStyle().setProperty("marginBottom", "2px");
        label.getElement().getStyle().setProperty("padding", "2px 4px");
        label.getElement().getStyle().setProperty("backgroundColor", "#f0f0f0");
        label.getElement().getStyle().setProperty("border", "1px solid #ccc");
        label.getElement().getStyle().setProperty("borderRadius", "3px");
        
        return label;
    }
    
    // ===== PRIVATE HELPER METHODS =====
    
    /**
     * Get the word at current cursor position
     */
    private static String getCurrentWord(TextBox textBox) {
        String text = textBox.getText();
        int pos = textBox.getCursorPos();
        
        if (text.isEmpty() || pos < 0) return "";
        
        // Find word start
        int start = findWordStart(textBox);
        
        // Find word end
        int end = pos;
        while (end < text.length() && isIdentifierChar(text.charAt(end))) {
            end++;
        }
        
        if (start >= end) return "";
        return text.substring(start, end);
    }
    
    /**
     * Replace current word with completion
     */
    private static void completeCurrentWord(TextBox textBox, String completion) {
        String text = textBox.getText();
        int pos = textBox.getCursorPos();
        
        // Find word boundaries
        int wordStart = findWordStart(textBox);
        int wordEnd = pos;
        while (wordEnd < text.length() && isIdentifierChar(text.charAt(wordEnd))) {
            wordEnd++;
        }
        
        // Replace word
        String newText = text.substring(0, wordStart) + completion + text.substring(wordEnd);
        textBox.setText(newText);
        textBox.setCursorPos(wordStart + completion.length());
    }
    
    /**
     * Find start of word at cursor
     */
    private static int findWordStart(TextBox textBox) {
        String text = textBox.getText();
        int pos = textBox.getCursorPos();
        
        if (pos <= 0) return 0;
        
        int start = pos - 1;
        while (start >= 0 && isIdentifierChar(text.charAt(start))) {
            start--;
        }
        return start + 1;
    }
    
    /**
     * Check if character is valid in identifier
     */
    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
    
    /**
     * Find all completions matching prefix (case-insensitive)
     */
    private static java.util.List<String> findMatches(String prefix, java.util.List<String> completionList) {
        java.util.List<String> matches = new java.util.ArrayList<String>();
        String lowerPrefix = prefix.toLowerCase();
        
        for (String item : completionList) {
            if (item.toLowerCase().startsWith(lowerPrefix)) {
                if (!matches.contains(item)) {
                    matches.add(item);
                }
            }
        }
        
        java.util.Collections.sort(matches);
        return matches;
    }
    
    /**
     * Display matches in hint label with optional highlighting
     */
    private static void displayMatches(java.util.List<String> matches, Label hintLabel, int highlightIndex) {
        if (matches.isEmpty()) {
            hintLabel.setVisible(false);
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) sb.append("  ");
            if (i == highlightIndex) {
                sb.append("[").append(matches.get(i)).append("]");
            } else {
                sb.append(matches.get(i));
            }
        }
        
        hintLabel.setText(sb.toString());
        hintLabel.setVisible(true);
    }
    
    /**
     * Display both validation errors and matches
     */
    private static void displayValidationAndMatches(java.util.List<String> undefinedSymbols, 
                                                   java.util.List<String> matches, Label hintLabel) {
        if (undefinedSymbols.isEmpty() && matches.isEmpty()) {
            hintLabel.setVisible(false);
            return;
        }
        
        StringBuilder html = new StringBuilder();
        
        // Show undefined symbols in red
        if (!undefinedSymbols.isEmpty()) {
            html.append("<span style='color:#cc0000'>Undefined: ");
            for (int i = 0; i < undefinedSymbols.size(); i++) {
                if (i > 0) html.append(", ");
                html.append(undefinedSymbols.get(i));
            }
            html.append("</span>");
        }
        
        // Show matches in gray
        if (!matches.isEmpty()) {
            if (html.length() > 0) {
                html.append("<span style='color:#666'> | Matches: </span>");
            }
            html.append("<span style='color:#666'>");
            for (int i = 0; i < matches.size(); i++) {
                if (i > 0) html.append("  ");
                html.append(matches.get(i));
            }
            html.append("</span>");
        }
        
        hintLabel.getElement().setInnerHTML(html.toString());
        hintLabel.setVisible(true);
    }
    
    /**
     * Display only undefined symbols (for validation on open)
     */
    private static void displayUndefinedSymbols(java.util.List<String> undefinedSymbols, Label hintLabel) {
        if (undefinedSymbols.isEmpty()) {
            hintLabel.setVisible(false);
            return;
        }
        
        StringBuilder html = new StringBuilder();
        html.append("<span style='color:#cc0000'>Undefined: ");
        for (int i = 0; i < undefinedSymbols.size(); i++) {
            if (i > 0) html.append(", ");
            html.append(undefinedSymbols.get(i));
        }
        html.append("</span>");
        
        hintLabel.getElement().setInnerHTML(html.toString());
        hintLabel.setVisible(true);
    }
    
    /**
     * Extract identifiers from equation text
     */
    private static java.util.List<String> extractIdentifiers(String text) {
        java.util.List<String> identifiers = new java.util.ArrayList<String>();
        StringBuilder currentId = new StringBuilder();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c) || c == '_') {
                currentId.append(c);
            } else if (Character.isDigit(c) && currentId.length() > 0) {
                currentId.append(c);
            } else {
                if (currentId.length() > 0) {
                    identifiers.add(currentId.toString());
                    currentId = new StringBuilder();
                }
            }
        }
        
        if (currentId.length() > 0) {
            identifiers.add(currentId.toString());
        }
        
        return identifiers;
    }
    
    /**
     * Check if a symbol is known (case-sensitive for user symbols, case-insensitive for built-ins)
     */
    private static boolean isKnownSymbol(String symbol, java.util.List<String> completionList) {
        // Check user-defined symbols (case-sensitive)
        for (String item : completionList) {
            if (item.equals(symbol)) {
                return true;
            }
        }
        
        // Check built-in functions (case-insensitive)
        String lowerSymbol = symbol.toLowerCase();
        String[] builtIns = {"sin", "cos", "tan", "asin", "acos", "atan", "atan2",
                            "exp", "log", "log10", "sqrt", "abs", "floor", "ceil",
                            "min", "max", "pi", "e", "t"};
        for (String fn : builtIns) {
            if (fn.equals(lowerSymbol)) {
                return true;
            }
        }
        
        return false;
    }
}
