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
	
	/**
	 * Adds bash-style tab completion to a TextBox.
	 * Creates a vertical panel with a hint label above the textbox.
	 * Returns the panel that should be added to the dialog instead of the textbox alone.
	 */
	private VerticalPanel addAutocompleteHandler(final TextBox textBox, final java.util.List<String> completionList) {
	    // Create a vertical panel to hold hint label and textbox
	    VerticalPanel autocompletePanel = new VerticalPanel();
	    autocompletePanel.setWidth("100%");
	    
	    // Create hint label (initially hidden)
	    final Label hintLabel = new Label();
	    hintLabel.setStyleName("autocomplete-hint");
	    hintLabel.setVisible(false);
	    hintLabel.getElement().getStyle().setProperty("fontSize", "11px");
	    hintLabel.getElement().getStyle().setProperty("color", "#666");
	    hintLabel.getElement().getStyle().setProperty("fontFamily", "monospace");
	    hintLabel.getElement().getStyle().setProperty("whiteSpace", "pre-wrap");
	    hintLabel.getElement().getStyle().setProperty("marginBottom", "2px");
	    hintLabel.getElement().getStyle().setProperty("padding", "2px 4px");
	    hintLabel.getElement().getStyle().setProperty("backgroundColor", "#f0f0f0");
	    hintLabel.getElement().getStyle().setProperty("border", "1px solid #ccc");
	    hintLabel.getElement().getStyle().setProperty("borderRadius", "3px");
	    
	    // Add hint label and textbox to the panel
	    autocompletePanel.add(hintLabel);
	    autocompletePanel.add(textBox);
	    
	    // Store reference for updates
	    autocompleteHintLabel = hintLabel;
	    
	    // Add key down handler for Tab completion
	    textBox.addKeyDownHandler(new KeyDownHandler() {
		public void onKeyDown(KeyDownEvent event) {
		    if (event.getNativeKeyCode() == KeyCodes.KEY_TAB) {
			event.preventDefault();
			event.stopPropagation();
			handleTabCompletion(textBox, completionList, hintLabel);
		    }
		}
	    });
	    
	    // Add key press handler to show matches as user types
	    textBox.addKeyPressHandler(new KeyPressHandler() {
		public void onKeyPress(KeyPressEvent event) {
		    // Schedule showing matches after the character is added to the textbox
		    com.google.gwt.core.client.Scheduler.get().scheduleDeferred(new com.google.gwt.core.client.Scheduler.ScheduledCommand() {
			public void execute() {
			    showMatchesForCurrentWord(textBox, completionList, hintLabel);
			}
		    });
		}
	    });
	    
	    return autocompletePanel;
	}
	
	/**
	 * Handles tab completion similar to bash:
	 * - First tab: complete to longest common prefix or show first match
	 * - Subsequent tabs: cycle through matching completions
	 * - Shows available matches in label above input when multiple options exist
	 */
	private void handleTabCompletion(TextBox textBox, java.util.List<String> completionList, Label hintLabel) {
	    String text = textBox.getText();
	    int cursorPos = textBox.getCursorPos();
	    
	    // Find the word being completed (before cursor)
	    String beforeCursor = text.substring(0, cursorPos);
	    int wordStart = findWordStart(beforeCursor);
	    String prefix = beforeCursor.substring(wordStart);
	    
	    // Check if this is a new completion or continuation
	    boolean newCompletion = !prefix.equals(lastAutocompletePrefix);
	    
	    if (newCompletion) {
		// Find all matches for this prefix
		autocompleteMatches = findMatches(prefix, completionList);
		autocompleteIndex = 0;
		lastAutocompletePrefix = prefix;
		
		if (autocompleteMatches.isEmpty()) {
		    hintLabel.setVisible(false);
		    return; // No matches
		}
		
		if (autocompleteMatches.size() == 1) {
		    // Only one match, complete it
		    replaceWord(textBox, wordStart, cursorPos, autocompleteMatches.get(0));
		    hintLabel.setVisible(false);
		    return;
		}
		
		// Multiple matches: show them and complete to longest common prefix
		showMatchesInLabel(autocompleteMatches, hintLabel);
		
		String commonPrefix = findLongestCommonPrefix(autocompleteMatches);
		if (commonPrefix.length() > prefix.length()) {
		    replaceWord(textBox, wordStart, cursorPos, commonPrefix);
		    lastAutocompletePrefix = commonPrefix;
		    return;
		}
	    }
	    
	    // Cycle through matches
	    if (autocompleteMatches != null && !autocompleteMatches.isEmpty()) {
		String completion = autocompleteMatches.get(autocompleteIndex);
		replaceWord(textBox, wordStart, cursorPos, completion);
		
		// Update hint to show current selection
		showMatchesInLabel(autocompleteMatches, hintLabel);
		
		// Move to next match
		autocompleteIndex = (autocompleteIndex + 1) % autocompleteMatches.size();
	    }
	}
	
	/**
	 * Finds the start of the current word (identifier).
	 * Words can contain letters, numbers, underscores.
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
	
	/**
	 * Finds all completion entries that start with the given prefix.
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
	 * Finds the longest common prefix among all matches.
	 */
	private String findLongestCommonPrefix(java.util.List<String> matches) {
	    if (matches.isEmpty()) {
		return "";
	    }
	    
	    String first = matches.get(0);
	    int prefixLen = first.length();
	    
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
	
	/**
	 * Replaces the current word with the completion.
	 */
	private void replaceWord(TextBox textBox, int wordStart, int cursorPos, String completion) {
	    String text = textBox.getText();
	    String before = text.substring(0, wordStart);
	    String after = text.substring(cursorPos);
	    
	    String newText = before + completion + after;
	    textBox.setText(newText);
	    
	    // Position cursor after the completed word
	    int newCursorPos = wordStart + completion.length();
	    textBox.setCursorPos(newCursorPos);
	}
	
	/**
	 * Shows available matches in the label above the input field.
	 * Formats matches in columns for readability.
	 */
	private void showMatchesInLabel(java.util.List<String> matches, Label hintLabel) {
	    if (matches == null || matches.isEmpty() || hintLabel == null) {
		if (hintLabel != null) {
		    hintLabel.setVisible(false);
		}
		return;
	    }
	    
	    // Build a formatted string showing all matches
	    StringBuilder sb = new StringBuilder();
	    sb.append("Matches (").append(matches.size()).append("): ");
	    
	    // Show matches inline, separated by spaces
	    for (int i = 0; i < matches.size() && i < 20; i++) {  // Limit to 20 matches
		if (i > 0) {
		    sb.append("  ");
		}
		sb.append(matches.get(i));
	    }
	    
	    if (matches.size() > 20) {
		sb.append("  ...");
	    }
	    
	    hintLabel.setText(sb.toString());
	    hintLabel.setVisible(true);
	}
	
	/**
	 * Shows matching completions for the current word being typed.
	 * This is called automatically as the user types (not just on Tab).
	 */
	private void showMatchesForCurrentWord(TextBox textBox, java.util.List<String> completionList, Label hintLabel) {
	    String text = textBox.getText();
	    int cursorPos = textBox.getCursorPos();
	    
	    // Find the word being typed (before cursor)
	    String beforeCursor = text.substring(0, cursorPos);
	    int wordStart = findWordStart(beforeCursor);
	    String prefix = beforeCursor.substring(wordStart);
	    
	    // If prefix is empty or too short, hide the hint
	    if (prefix.length() < 1) {
		hintLabel.setVisible(false);
		return;
	    }
	    
	    // Find all matches for this prefix
	    java.util.List<String> matches = findMatches(prefix, completionList);
	    
	    if (matches.isEmpty()) {
		hintLabel.setVisible(false);
	    } else if (matches.size() == 1 && matches.get(0).equalsIgnoreCase(prefix)) {
		// User has typed the complete match, hide hint
		hintLabel.setVisible(false);
	    } else {
		// Show available matches
		showMatchesInLabel(matches, hintLabel);
	    }
	}
	
	/**
	 * Shows available matches to the browser console (like bash does).
	 * Formats matches in columns for readability.
	 * DEPRECATED: Now using label display instead.
	 */
	private void showMatches(java.util.List<String> matches) {
	    if (matches == null || matches.isEmpty()) {
		return;
	    }
	    
	    // Build a formatted string showing all matches
	    StringBuilder sb = new StringBuilder();
	    sb.append("Available completions (").append(matches.size()).append("):\n");
	    
	    // Format in columns (4 per line)
	    int columns = 4;
	    int maxWidth = 0;
	    
	    // Find longest match for column width
	    for (String match : matches) {
		maxWidth = Math.max(maxWidth, match.length());
	    }
	    maxWidth += 2; // Add padding
	    
	    // Build columnar output
	    for (int i = 0; i < matches.size(); i++) {
		String match = matches.get(i);
		sb.append(match);
		
		// Add padding to align columns
		for (int j = match.length(); j < maxWidth; j++) {
		    sb.append(" ");
		}
		
		// New line after every 'columns' items or at the end
		if ((i + 1) % columns == 0 || i == matches.size() - 1) {
		    sb.append("\n");
		}
	    }
	    
	    // Output to console
	    console(sb.toString());
	}
	
	/**
	 * Native console.log for debugging and showing completion matches.
	 */
	private native void console(String text) /*-{
	    console.log(text);
	}-*/;
	
	/**
	 * Resets the autocomplete state when user types something other than tab.
	 */
	private void resetAutocompleteState(Label hintLabel) {
	    lastAutocompletePrefix = null;
	    autocompleteIndex = 0;
	    autocompleteMatches = null;
	    if (hintLabel != null) {
		hintLabel.setVisible(false);
	    }
	}
	
	public void closeDialog()
	{
		super.closeDialog();
		if (CirSim.editDialog == this)
		    CirSim.editDialog = null;
		if (CirSim.customLogicEditDialog == this)
		    CirSim.customLogicEditDialog = null;
	}
}

