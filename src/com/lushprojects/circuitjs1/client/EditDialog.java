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


import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.lushprojects.circuitjs1.client.util.Locale;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;

interface Editable {
    EditInfo getEditInfo(int n);
    void setEditValue(int n, EditInfo ei);
}

class EditDialog extends Dialog {
	Editable elm;
	CirSim cframe;
	Button applyButton, okButton, cancelButton;
	EditInfo einfos[];
	int einfocount;
	final int barmax = 1000;
	VerticalPanel mainPanel;
	HorizontalPanel bottomButtonPanel;
	static NumberFormat noCommaFormat = NumberFormat.getFormat("####.##########");
	
	// Autocomplete state tracking
	private String lastAutocompletePrefix = null;
	private int autocompleteIndex = 0;
	private java.util.List<String> autocompleteMatches = null;
	private Label autocompleteHintLabel = null;  // Label to show matches above input

	EditDialog(Editable ce, CirSim f) {
//		super(f, "Edit Component", false);
		super(); // Do we need this?
		setText(Locale.LS("Edit Component"));
		cframe = f;
		elm = ce;
//		setLayout(new EditDialogLayout());
		mainPanel=new VerticalPanel();
		setWidget(mainPanel);
		einfos = new EditInfo[10];
//		noCommaFormat = DecimalFormat.getInstance();
//		noCommaFormat.setMaximumFractionDigits(10);
//		noCommaFormat.setGroupingUsed(false);
		bottomButtonPanel = new HorizontalPanel();
		bottomButtonPanel.setWidth("100%");
		bottomButtonPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
		bottomButtonPanel.setStyleName("topSpace");
		mainPanel.add(bottomButtonPanel);
		applyButton = new Button(Locale.LS("Apply"));
		bottomButtonPanel.add(applyButton);
		applyButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				apply();
			}
		});
		bottomButtonPanel.add(okButton = new Button(Locale.LS("OK")));
		okButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				apply();
				closeDialog();
			}
		});
		bottomButtonPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
		bottomButtonPanel.add(cancelButton = new Button(Locale.LS("Cancel")));
		cancelButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				closeDialog();
			}
		});
		buildDialog();
		this.center();
	}
	
	void buildDialog() {
		int i;
		HorizontalPanel hp = new HorizontalPanel();
		VerticalPanel vp = new VerticalPanel();
		mainPanel.insert(hp, mainPanel.getWidgetIndex(bottomButtonPanel));
		hp.add(vp);
		for (i = 0; ; i++) {
			Label l = null;
			einfos[i] = elm.getEditInfo(i);
			if (einfos[i] == null)
				break;
			final EditInfo ei = einfos[i];
			String name = Locale.LS(ei.name);
			if (ei.name.startsWith("<"))
			    vp.add(l = new HTML(name));
			else
			    vp.add(l = new Label(name));
			if (i!=0 && l != null)
				l.setStyleName("topSpace");
			if (ei.choice != null) {
				vp.add(ei.choice);
				ei.choice.addChangeHandler( new ChangeHandler() {
					public void onChange(ChangeEvent e){
						itemStateChanged(e);
					}
				});
			} else if (ei.checkbox != null) {
				vp.add(ei.checkbox);
				ei.checkbox.addValueChangeHandler( new ValueChangeHandler<Boolean>() {
					public void onValueChange(ValueChangeEvent<Boolean> e){
						itemStateChanged(e);
					}
				});
			} else if (ei.button != null) {
			    vp.add(ei.button);
			    if (ei.loadFile != null) {
			    	//Open file dialog
			    	vp.add(ei.loadFile);
				    ei.button.addClickHandler( new ClickHandler() {
						public void onClick(ClickEvent event) {
					    	ei.loadFile.open();
						}
				    });
			    } else {
			    	//Normal button press
				    ei.button.addClickHandler( new ClickHandler() {
						public void onClick(ClickEvent event) {
						    itemStateChanged(event);
						}
				    });
			    }
			} else if (ei.textArea != null) {
			    vp.add(ei.textArea);
			    closeOnEnter = false;
			} else if (ei.suggestBox != null) {
			    vp.add(ei.suggestBox);
			    ei.suggestBox.setWidth("200px");
			} else if (ei.widget != null) {
			    vp.add(ei.widget);
		} else {
		    // Create text box for value input
		    ei.textf = new TextBox();
		    // Prevent keyboard events from deleting circuit elements while typing
		    preventKeyboardPropagation(ei.textf);
		    
    // Configure and add textbox (with or without autocomplete)
		    if (ei.text != null) {
		ei.textf.setText(ei.text);
		ei.textf.setVisibleLength(50);
    }
		    if (ei.text == null) {
		ei.textf.setText(unitString(ei));
    }
    // Attach KeyUpHandler if provided for immediate updates
		    if (ei.keyUpHandler != null) {
		ei.textf.addKeyUpHandler(ei.keyUpHandler);
    }
    
    // Add autocomplete handler if completionList is provided
	if (ei.completionList != null && !ei.completionList.isEmpty()) {
		VerticalPanel autocompletePanel = addAutocompleteHandler(ei.textf, ei.completionList);
		// Add the panel (with or without inline checkbox)
		if (ei.checkboxInline != null) {
		    HorizontalPanel hPanel = new HorizontalPanel();
		    hPanel.add(autocompletePanel);
		    hPanel.add(ei.checkboxInline);
		    hPanel.getElement().getStyle().setProperty("alignItems", "center");
		    ei.checkboxInline.getElement().getStyle().setPaddingLeft(10, Unit.PX);
		    vp.add(hPanel);
		    // Add change handler for inline checkbox
		    ei.checkboxInline.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
			public void onValueChange(ValueChangeEvent<Boolean> e) {
			    itemStateChanged(e);
			}
		    });
		} else {
		    vp.add(autocompletePanel);
		}
	} else {
			// No autocomplete - add textbox with or without inline checkbox
			if (ei.checkboxInline != null) {
			    HorizontalPanel hPanel = new HorizontalPanel();
			    hPanel.add(ei.textf);
			    hPanel.add(ei.checkboxInline);
			    hPanel.getElement().getStyle().setProperty("alignItems", "center");
			    ei.checkboxInline.getElement().getStyle().setPaddingLeft(10, Unit.PX);
			    vp.add(hPanel);
			    // Add change handler for inline checkbox
			    ei.checkboxInline.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
				public void onValueChange(ValueChangeEvent<Boolean> e) {
				    itemStateChanged(e);
				}
			    });
			} else {
			    // Just add the textbox alone
			    vp.add(ei.textf);
			}
	}
		}
		if (vp.getWidgetCount() > 15) {
			    // start a new column
			    vp = new VerticalPanel();
			    hp.add(vp);
			    vp.getElement().getStyle().setPaddingLeft(10, Unit.PX);
			}
		}
		einfocount = i;
	}

	static final double ROOT2 = 1.41421356237309504880;
	
	double diffFromInteger(double x) {
	    return Math.abs(x-Math.round(x));
	}
	
	String unitString(EditInfo ei) {
	    // for voltage elements, express values in rms if that would be shorter
	    if (elm != null && elm instanceof VoltageElm &&
		Math.abs(ei.value) > 1e-4 &&
		diffFromInteger(ei.value*1e4) > diffFromInteger(ei.value*1e4/ROOT2))
		return unitString(ei, ei.value/ROOT2) + "rms";
	    return unitString(ei, ei.value);
	}

	static String unitString(EditInfo ei, double v) {
		double va = Math.abs(v);
		if (ei != null && ei.dimensionless)
			return noCommaFormat.format(v);
		if (Double.isInfinite(va))
			return noCommaFormat.format(v);
		if (v == 0) return "0";
		if (va < 1e-12)
			return noCommaFormat.format(v*1e15) + "f";
		if (va < 1e-9)
			return noCommaFormat.format(v*1e12) + "p";
		if (va < 1e-6)
			return noCommaFormat.format(v*1e9) + "n";
		if (va < 1e-3)
			return noCommaFormat.format(v*1e6) + "u";
		if (va < 1 /*&& !ei.forceLargeM*/)
			return noCommaFormat.format(v*1e3) + "m";
		if (va < 1e3)
			return noCommaFormat.format(v);
		if (va < 1e6)
			return noCommaFormat.format(v*1e-3) + "k";
		if (va < 1e9)
			return noCommaFormat.format(v*1e-6) + "M";
		return noCommaFormat.format(v*1e-9) + "G";
	}

	double parseUnits(EditInfo ei) throws java.text.ParseException {
		String s = ei.textf.getText();
		return parseUnits(s);
	}
	
	static double parseUnits(String s) throws java.text.ParseException {
		s = s.trim();
		double rmsMult = 1;
		if (s.endsWith("rms")) {
		    s = s.substring(0, s.length()-3).trim();
		    rmsMult = ROOT2;
		}
		// rewrite shorthand (eg "2k2") in to normal format (eg 2.2k) using regex
		s=s.replaceAll("([0-9]+)([pPnNuUmMkKgG])([0-9]+)", "$1.$3$2");
		// rewrite meg to M
		s=s.replaceAll("[mM][eE][gG]$", "M");
		int len = s.length();
		char uc = s.charAt(len-1);
		double mult = 1;
		switch (uc) {
		case 'f': case 'F': mult = 1e-15; break;
		case 'p': case 'P': mult = 1e-12; break;
		case 'n': case 'N': mult = 1e-9; break;
		case 'u': case 'U': mult = 1e-6; break;

		// for ohm values, we used to assume mega for lowercase m, otherwise milli
		case 'm': mult = /*(ei.forceLargeM) ? 1e6 : */ 1e-3; break;

		case 'k': case 'K': mult = 1e3; break;
		case 'M': mult = 1e6; break;
		case 'G': case 'g': mult = 1e9; break;
		}
		if (mult != 1)
			s = s.substring(0, len-1).trim();
		return noCommaFormat.parse(s) * mult * rmsMult;
	}

	void apply() {
		int i;
		for (i = 0; i != einfocount; i++) {
			EditInfo ei = einfos[i];
			if (ei.textf!=null && ei.text==null) {
				try {
					double d = parseUnits(ei);
					ei.value = d;
				} catch (Exception ex) { /* ignored */ }
			}
			if (ei.suggestBox != null) {
			    // Get text from SuggestBox for text-based fields
			    ei.text = ei.suggestBox.getText();
			}
			if (ei.button != null)
			    continue;
			elm.setEditValue(i, ei);
			
			// update slider if any
			if (elm instanceof CircuitElm) {
			    Adjustable adj = cframe.findAdjustable((CircuitElm)elm, i);
			    if (adj != null)
				adj.setSliderValue(ei.value);
			}
		}
		cframe.needAnalyze();
	}

	public void itemStateChanged(GwtEvent e) {
	    Object src = e.getSource();
	    int i;
	    boolean changed = false;
	    boolean applied = false;
	    for (i = 0; i != einfocount; i++) {
		EditInfo ei = einfos[i];
		if (ei.choice == src || ei.checkbox == src || ei.button == src) {
		    
		    // if we're pressing a button, make sure to apply changes first
		    if (ei.button == src && !ei.newDialog) {
			apply();
			applied = true;
		    }
		    
		    elm.setEditValue(i, ei);
		    if (ei.newDialog)
			changed = true;
		    cframe.needAnalyze();
		}
	    }
	    if (changed) {
		// apply changes before we reset everything
		// (need to check if we already applied changes; otherwise Diode create simple model button doesn't work)
		if (!applied)
		    apply();
		
		clearDialog();
		buildDialog();
	    }
	}
	
	public void resetDialog() {
	    clearDialog();
	    buildDialog();
	}
	
	public void clearDialog() {
		while (mainPanel.getWidget(0)!=bottomButtonPanel)
			mainPanel.remove(0);
	}
	
	// ============================================================================
	// AUTOCOMPLETE FUNCTIONALITY
	// Provides bash-style tab completion with real-time match display
	// ============================================================================
	
	/**
	 * Creates an autocomplete-enabled text input.
	 * 
	 * Returns a VerticalPanel containing:
	 * - A hint label (shows matching completions as user types)
	 * - The TextBox
	 * 
	 * @param textBox The text input to enhance with autocomplete
	 * @param completionList List of available completion strings
	 * @return VerticalPanel to add to the dialog (instead of textBox alone)
	 */
	private VerticalPanel addAutocompleteHandler(final TextBox textBox, final java.util.List<String> completionList) {
	    // Setup container panel
	    VerticalPanel container = new VerticalPanel();
	    container.setWidth("100%");
	    
	    // Create hint label (initially hidden, appears when typing)
	    final Label hintLabel = createHintLabel();
	    
	    // Assemble: hint label above textbox
	    container.add(hintLabel);
	    container.add(textBox);
	    
	    // Store reference for state management
	    autocompleteHintLabel = hintLabel;
	    
	    // Handle Tab key: cycle through completions
	    textBox.addKeyDownHandler(new KeyDownHandler() {
		public void onKeyDown(KeyDownEvent event) {
		    if (event.getNativeKeyCode() == KeyCodes.KEY_TAB) {
			event.preventDefault();
			event.stopPropagation();
			handleTabCompletion(textBox, completionList, hintLabel);
		    }
		}
	    });
	    
	    // Handle typing: show matches in real-time and validate symbols
	    textBox.addKeyPressHandler(new KeyPressHandler() {
		public void onKeyPress(KeyPressEvent event) {
		    // Wait for character to be added to textbox, then update display
		    com.google.gwt.core.client.Scheduler.get().scheduleDeferred(
			new com.google.gwt.core.client.Scheduler.ScheduledCommand() {
			    public void execute() {
				updateMatchDisplay(textBox, completionList, hintLabel);
			    }
			}
		    );
		}
	    });
	    
	    // Validate immediately on dialog open - show only undefined symbols
	    com.google.gwt.core.client.Scheduler.get().scheduleDeferred(
		new com.google.gwt.core.client.Scheduler.ScheduledCommand() {
		    public void execute() {
			validateOnOpen(textBox, completionList, hintLabel);
		    }
		}
	    );
	    
	    return container;
	}
	
	/**
	 * Creates and styles the hint label that displays available matches.
	 */
	private Label createHintLabel() {
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
	
	/**
	 * Handles Tab key completion (bash-style behavior).
	 * 
	 * First Tab:
	 * - Single match: complete it immediately
	 * - Multiple matches: complete to longest common prefix, show all matches
	 * 
	 * Subsequent Tabs: cycle through all matching options
	 */
	private void handleTabCompletion(TextBox textBox, java.util.List<String> completionList, Label hintLabel) {
	    // Extract the word being completed
	    String prefix = getCurrentWord(textBox);
	    
	    // Check if this is a new completion request or continuing previous one
	    boolean isNewRequest = !prefix.equals(lastAutocompletePrefix);
	    
	    if (isNewRequest) {
		startNewCompletion(textBox, prefix, completionList, hintLabel);
	    } else {
		cycleToNextMatch(textBox, hintLabel);
	    }
	}
	
	/**
	 * Starts a new completion request for the given prefix.
	 */
	private void startNewCompletion(TextBox textBox, String prefix, 
					java.util.List<String> completionList, Label hintLabel) {
	    // Find all matching completions
	    autocompleteMatches = findMatches(prefix, completionList);
	    autocompleteIndex = 0;
	    lastAutocompletePrefix = prefix;
	    
	    // No matches found
	    if (autocompleteMatches.isEmpty()) {
		hintLabel.setVisible(false);
		return;
	    }
	    
	    // Exactly one match: complete it immediately
	    if (autocompleteMatches.size() == 1) {
		completeCurrentWord(textBox, autocompleteMatches.get(0));
		hintLabel.setVisible(false);
		return;
	    }
	    
	    // Multiple matches: complete with first match immediately
	    completeCurrentWord(textBox, autocompleteMatches.get(0));
	    
	    // Update lastAutocompletePrefix to the completed word so subsequent tabs continue cycling
	    lastAutocompletePrefix = autocompleteMatches.get(0);
	    
	    // Update display with first match highlighted
	    displayMatches(autocompleteMatches, hintLabel, 0);
	}
	
	/**
	 * Cycles to the next match in the completion list.
	 */
	private void cycleToNextMatch(TextBox textBox, Label hintLabel) {
	    if (autocompleteMatches == null || autocompleteMatches.isEmpty()) {
		return;
	    }
	    
	    // Replace with current match
	    String completion = autocompleteMatches.get(autocompleteIndex);
	    completeCurrentWord(textBox, completion);
	    
	    // Update lastAutocompletePrefix to the completed word so next tab continues cycling
	    lastAutocompletePrefix = completion;
	    
	    // Update display with current match highlighted
	    displayMatches(autocompleteMatches, hintLabel, autocompleteIndex);
	    
	    // Advance to next match (wrap around)
	    autocompleteIndex = (autocompleteIndex + 1) % autocompleteMatches.size();
	}
	
	/**
	 * Validates the expression when dialog first opens.
	 * Shows only undefined symbols, not completion matches.
	 */
	private void validateOnOpen(TextBox textBox, java.util.List<String> completionList, Label hintLabel) {
	    String text = textBox.getText().trim();
	    
	    // Check for undefined symbols in the full expression
	    java.util.List<String> undefinedSymbols = new java.util.ArrayList<String>();
	    if (!text.isEmpty()) {
		java.util.Set<String> identifiers = extractIdentifiers(text);
		
		for (String identifier : identifiers) {
		    if (!isKnownSymbol(identifier, completionList)) {
			undefinedSymbols.add(identifier);
		    }
		}
	    }
	    
	    // Show only undefined symbols (no completion matches on open)
	    if (!undefinedSymbols.isEmpty()) {
		displayUndefinedSymbols(undefinedSymbols, hintLabel);
	    } else {
		hintLabel.setVisible(false);
	    }
	}
	
	/**
	 * Updates the match display as the user types (called on every keystroke).

	 * Shows matches automatically without needing to press Tab.
	 * Shows both undefined symbols (in red) and available matches.
	 */
	private void updateMatchDisplay(TextBox textBox, java.util.List<String> completionList, Label hintLabel) {
	    String text = textBox.getText().trim();
	    
	    // Check for undefined symbols in the full expression
	    java.util.List<String> undefinedSymbols = new java.util.ArrayList<String>();
	    if (!text.isEmpty()) {
		java.util.Set<String> identifiers = extractIdentifiers(text);
		
		for (String identifier : identifiers) {
		    if (!isKnownSymbol(identifier, completionList)) {
			undefinedSymbols.add(identifier);
		    }
		}
	    }
	    
	    // Get completion matches for current word
	    String prefix = getCurrentWord(textBox);
	    java.util.List<String> matches = new java.util.ArrayList<String>();
	    
	    if (prefix.length() >= 1) {
		matches = findMatches(prefix, completionList);
		
		// Remove exact match or complete word from matches display
		if (matches.size() == 1 && matches.get(0).equalsIgnoreCase(prefix)) {
		    matches.clear();
		}
	    }
	    
	    // Display both undefined symbols and matches
	    if (!undefinedSymbols.isEmpty() || !matches.isEmpty()) {
		displayValidationAndMatches(undefinedSymbols, matches, hintLabel);
	    } else {
		hintLabel.setVisible(false);
	    }
	}
	
	// ============================================================================
	// HELPER METHODS: Text extraction and manipulation
	// ============================================================================
	
	/**
	 * Gets the word currently being typed (before cursor).
	 * A word consists of letters, digits, and underscores.
	 */
	private String getCurrentWord(TextBox textBox) {
	    String text = textBox.getText();
	    int cursorPos = textBox.getCursorPos();
	    String beforeCursor = text.substring(0, cursorPos);
	    int wordStart = findWordStart(beforeCursor);
	    return beforeCursor.substring(wordStart);
	}
	
	/**
	 * Replaces the current word with the given completion.
	 */
	private void completeCurrentWord(TextBox textBox, String completion) {
	    String text = textBox.getText();
	    int cursorPos = textBox.getCursorPos();
	    String beforeCursor = text.substring(0, cursorPos);
	    int wordStart = findWordStart(beforeCursor);
	    
	    // Build new text: before + completion + after
	    String before = text.substring(0, wordStart);
	    String after = text.substring(cursorPos);
	    textBox.setText(before + completion + after);
	    
	    // Move cursor to end of completed word
	    textBox.setCursorPos(wordStart + completion.length());
	}
	
	/**
	 * Finds the start position of the current word.
	 * Words consist of letters, digits, and underscores.
	 */
	private int findWordStart(String text) {
	    int pos = text.length() - 1;
	    while (pos >= 0) {
		char c = text.charAt(pos);
		if (!Character.isLetterOrDigit(c) && c != '_') {
		    break;
		}
		pos--;
	    }
	    return pos + 1;
	}
	
	// ============================================================================
	// HELPER METHODS: Matching and filtering
	// ============================================================================
	
	/**
	 * Finds all completions that start with the given prefix (case-insensitive).
	 */
	private java.util.List<String> findMatches(String prefix, java.util.List<String> completionList) {
	    java.util.List<String> matches = new java.util.ArrayList<String>();
	    String lowerPrefix = prefix.toLowerCase();
	    
	    for (String item : completionList) {
		if (item.toLowerCase().startsWith(lowerPrefix)) {
		    matches.add(item);
		}
	    }
	    
	    return matches;
	}
	
	/**
	 * Finds the longest prefix common to all matches (case-insensitive comparison).
	 * Used to auto-complete as far as possible when multiple matches exist.
	 */
	private String findLongestCommonPrefix(java.util.List<String> matches) {
	    if (matches.isEmpty()) {
		return "";
	    }
	    
	    String first = matches.get(0);
	    int prefixLen = first.length();
	    
	    // Compare first match with all others to find common prefix length
	    for (int i = 1; i < matches.size(); i++) {
		String current = matches.get(i);
		prefixLen = Math.min(prefixLen, current.length());
		
		for (int j = 0; j < prefixLen; j++) {
		    if (Character.toLowerCase(first.charAt(j)) != 
			Character.toLowerCase(current.charAt(j))) {
			prefixLen = j;
			break;
		    }
		}
	    }
	    
	    return first.substring(0, prefixLen);
	}
	
	// ============================================================================
	// HELPER METHODS: Display
	// ============================================================================
	
	/**
	 * Displays available matches in the hint label.
	 * Shows up to 20 matches inline, separated by spaces.
	 */
	private void displayMatches(java.util.List<String> matches, Label hintLabel) {
	    displayMatches(matches, hintLabel, -1);
	}
	
	/**
	 * Displays available matches in the hint label with highlighting.
	 * Shows up to 20 matches inline, separated by spaces.
	 * @param highlightIndex Index of match to highlight with brackets, or -1 for no highlighting
	 */
	private void displayMatches(java.util.List<String> matches, Label hintLabel, int highlightIndex) {
	    if (matches == null || matches.isEmpty() || hintLabel == null) {
		if (hintLabel != null) {
		    hintLabel.setVisible(false);
		}
		return;
	    }
	    
	    // Format: "Matches (3): stock1  [stock2]  stock3"
	    StringBuilder sb = new StringBuilder();
	    sb.append("Matches (").append(matches.size()).append("): ");
	    
	    int maxDisplay = 20;  // Limit to prevent overflow
	    for (int i = 0; i < matches.size() && i < maxDisplay; i++) {
		if (i > 0) {
		    sb.append("  ");
		}
		if (i == highlightIndex) {
		    sb.append("[").append(matches.get(i)).append("]");
		} else {
		    sb.append(matches.get(i));
		}
	    }
	    
	    if (matches.size() > maxDisplay) {
		sb.append("  ...");
	    }
	    
	    hintLabel.setText(sb.toString());
	    hintLabel.setVisible(true);
	}
	
	/**
	 * Displays both validation errors (undefined symbols) and available matches.
	 * Shows undefined symbols in red, followed by matches in gray.
	 */
	private void displayValidationAndMatches(java.util.List<String> undefinedSymbols, 
						 java.util.List<String> matches, Label hintLabel) {
	    if (hintLabel == null) {
		return;
	    }
	    
	    // If nothing to show, hide the label
	    if ((undefinedSymbols == null || undefinedSymbols.isEmpty()) && 
		(matches == null || matches.isEmpty())) {
		hintLabel.setVisible(false);
		return;
	    }
	    
	    // Build HTML with colored sections
	    StringBuilder sb = new StringBuilder();
	    
	    // Add undefined symbols in red (if any)
	    if (undefinedSymbols != null && !undefinedSymbols.isEmpty()) {
		sb.append("<span style='color: #cc0000; font-weight: bold;'>Undefined: ");
		for (int i = 0; i < undefinedSymbols.size(); i++) {
		    if (i > 0) {
			sb.append(", ");
		    }
		    sb.append(undefinedSymbols.get(i));
		}
		sb.append("</span>");
	    }
	    
	    // Add separator if we have both undefined and matches
	    if (!undefinedSymbols.isEmpty() && !matches.isEmpty()) {
		sb.append("<br>");
	    }
	    
	    // Add matches in gray (if any)
	    if (matches != null && !matches.isEmpty()) {
		sb.append("<span style='color: #666;'>Matches (").append(matches.size()).append("): ");
		
		int maxDisplay = 20;
		for (int i = 0; i < matches.size() && i < maxDisplay; i++) {
		    if (i > 0) {
			sb.append("  ");
		    }
		    sb.append(matches.get(i));
		}
		
		if (matches.size() > maxDisplay) {
		    sb.append("  ...");
		}
		sb.append("</span>");
	    }
	    
	    // Use HTML to allow colored text
	    hintLabel.getElement().setInnerHTML(sb.toString());
	    hintLabel.setVisible(true);
	}
	
	/**
	 * Displays undefined symbols in red text in the hint label.
	 * DEPRECATED: Use displayValidationAndMatches instead.
	 */
	private void displayUndefinedSymbols(java.util.List<String> undefinedSymbols, Label hintLabel) {
	    if (undefinedSymbols == null || undefinedSymbols.isEmpty() || hintLabel == null) {
		if (hintLabel != null) {
		    hintLabel.setVisible(false);
		}
		return;
	    }
	    
	    // Build HTML with red colored text
	    StringBuilder sb = new StringBuilder();
	    sb.append("<span style='color: #cc0000; font-weight: bold;'>Undefined: ");
	    for (int i = 0; i < undefinedSymbols.size(); i++) {
		if (i > 0) {
		    sb.append(", ");
		}
		sb.append(undefinedSymbols.get(i));
	    }
	    sb.append("</span>");
	    
	    // Use HTML to allow colored text
	    hintLabel.getElement().setInnerHTML(sb.toString());
	    hintLabel.setVisible(true);
	}
	
	/**
	 * Extracts all identifiers (variable names) from an expression.
	 * Identifiers are sequences of letters, digits, and underscores.
	 */
	private java.util.Set<String> extractIdentifiers(String expression) {
	    java.util.Set<String> identifiers = new java.util.HashSet<String>();
	    
	    StringBuilder currentWord = new StringBuilder();
	    for (int i = 0; i < expression.length(); i++) {
		char c = expression.charAt(i);
		
		if (Character.isLetterOrDigit(c) || c == '_') {
		    currentWord.append(c);
		} else {
		    // End of word
		    if (currentWord.length() > 0) {
			String word = currentWord.toString();
			// Only add if it starts with a letter (not a number)
			if (Character.isLetter(word.charAt(0))) {
			    identifiers.add(word);
			}
			currentWord.setLength(0);
		    }
		}
	    }
	    
	    // Don't forget the last word
	    if (currentWord.length() > 0) {
		String word = currentWord.toString();
		if (Character.isLetter(word.charAt(0))) {
		    identifiers.add(word);
		}
	    }
	    
	    return identifiers;
	}
	
	/**
	 * Checks if a symbol is known (exists in completion list or is a built-in).
	 * Uses case-sensitive matching for user-defined symbols.
	 */
	private boolean isKnownSymbol(String symbol, java.util.List<String> completionList) {
	    // Check if it's in the completion list (CASE-SENSITIVE)
	    for (String item : completionList) {
		if (item.equals(symbol)) {
		    return true;
		}
	    }
	    
	    // Known built-in functions and constants are case-insensitive
	    // (These might not be in the completion list)
	    String lowerSymbol = symbol.toLowerCase();
	    java.util.Set<String> builtins = new java.util.HashSet<String>();
	    builtins.add("sin");
	    builtins.add("cos");
	    builtins.add("tan");
	    builtins.add("exp");
	    builtins.add("log");
	    builtins.add("ln");
	    builtins.add("sqrt");
	    builtins.add("abs");
	    builtins.add("min");
	    builtins.add("max");
	    builtins.add("pow");
	    builtins.add("atan2");
	    builtins.add("floor");
	    builtins.add("ceil");
	    builtins.add("round");
	    builtins.add("pi");
	    builtins.add("e");
	    builtins.add("t");
	    
	    return builtins.contains(lowerSymbol);
	}
	
	// End of autocomplete functionality
	// ============================================================================
	
	/**
	 * Native console.log for debugging.
	 */
	private native void console(String text) /*-{
	    console.log(text);
	}-*/;
	
	public void closeDialog()
	{
		super.closeDialog();
		if (CirSim.editDialog == this)
		    CirSim.editDialog = null;
		if (CirSim.customLogicEditDialog == this)
		    CirSim.customLogicEditDialog = null;
	}
}

