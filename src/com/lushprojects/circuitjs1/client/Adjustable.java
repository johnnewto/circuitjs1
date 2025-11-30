package com.lushprojects.circuitjs1.client;

import java.util.Vector;

import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Label;
import com.lushprojects.circuitjs1.client.util.Locale;

// values with sliders
public class Adjustable implements Command {
    CircuitElm elm;
    double minValue, maxValue;
    int flags;
    String sliderText;
    double stepIncrement; // Amount to add for each step (0 = continuous)
    
    // null if this Adjustable has its own slider, non-null if it's sharing another one.
    Adjustable sharedSlider;
    
    final int FLAG_SHARED = 1;
    
    // index of value in getEditInfo() list that this slider controls
    int editItem;
    
    Label label;
    Scrollbar slider;
    boolean settingValue;
    
    Adjustable(CircuitElm ce, int item) {
	minValue = 0;
	maxValue = 1;
	flags = 0;
	elm = ce;
	editItem = item;
	stepIncrement = 0; // Default to continuous
        EditInfo ei = ce.getEditInfo(editItem);
        if (ei != null && ei.maxVal > 0) {
            minValue = ei.minVal;
            maxValue = ei.maxVal;
        }
    }

    // undump
    Adjustable(StringTokenizer st, CirSim sim) {
	int e = Integer.parseInt(st.nextToken());
	if (e == -1)
	    return;
	stepIncrement = 0; // Default for backward compatibility
	try {
	    String ei = st.nextToken();

	    // forgot to dump a "flags" field in the initial code, so we have to do this to support backward compatibility
	    if (ei.startsWith("F")) {
		flags = Integer.parseInt(ei.substring(1));
		ei = st.nextToken();
	    }
	    
	    editItem = Integer.parseInt(ei);
	    minValue = Double.parseDouble(st.nextToken());
	    maxValue = Double.parseDouble(st.nextToken());
	    if ((flags & FLAG_SHARED) != 0) {
		int ano = Integer.parseInt(st.nextToken());
		sharedSlider = ano == -1 ? null : sim.adjustables.get(ano);
	    }
	    sliderText = CustomLogicModel.unescape(st.nextToken());
	    // Try to read stepIncrement if available (for backward compatibility)
	    if (st.hasMoreTokens()) {
		try {
		    stepIncrement = Double.parseDouble(st.nextToken());
		} catch (Exception ex2) {
		    stepIncrement = 0;
		}
	    }
	} catch (Exception ex) {}
	try {
	    elm = sim.getElm(e);
	} catch (Exception ex) {}
    }
    
    boolean createSlider(CirSim sim) {
	if (elm == null)
	    return false;
	EditInfo ei = elm.getEditInfo(editItem);
	if (ei == null)
	    return false;
	if (sharedSlider != null)
	    return true;
	if (sliderText.length() == 0)
	    return false;
	double value = ei.value;
	createSlider(sim, value);
	return true;
    }

    void createSlider(CirSim sim, double value) {
        EditInfo ei = elm.getEditInfo(editItem);
        String valueStr = getFormattedValue(ei, value);
        sim.addWidgetToVerticalPanel(label = new Label(Locale.LS(sliderText) + ": " + valueStr));
        label.addStyleName("topSpace");
        int intValue = (int) ((value-minValue)*100/(maxValue-minValue));
        sim.addWidgetToVerticalPanel(slider = new Scrollbar(Scrollbar.HORIZONTAL, intValue, 1, 0, 101, this, elm));
    }

    void setSliderValue(double value) {
	if (sharedSlider != null) {
	    sharedSlider.setSliderValue(value);
	    return;
	}
	
	// Validate min/max range before calculating
	if (maxValue <= minValue || slider == null) {
	    return; // Skip update if invalid range or slider is missing
	}
	
        int intValue = (int) ((value-minValue)*100/(maxValue-minValue));
        settingValue = true; // don't recursively set value again in execute()
        slider.setValue(intValue);
        settingValue = false;
        
        // Update label to show current value
        if (label != null) {
            EditInfo ei = elm.getEditInfo(editItem);
            if (ei != null) {
                String valueStr = getFormattedValue(ei, value);
                label.setText(Locale.LS(sliderText) + ": " + valueStr);
            }
        }
    }
    
    public void execute() {
	if (settingValue)
	    return;
	int i;
	CirSim sim = CirSim.theSim;
	for (i = 0; i != sim.adjustables.size(); i++) {
	    Adjustable adj = sim.adjustables.get(i);
	    if (adj == this || adj.sharedSlider == this)
		adj.executeSlider();
	}
    }
    
    void executeSlider() {
	// For non-linear elements, trigger full circuit analysis
	// For linear elements, skip analysis - matrix coefficients update on next stamp
	// if (elm.nonLinear()) {
	    elm.sim.analyzeFlag = true;
	// }
	
	EditInfo ei = elm.getEditInfo(editItem);
	ei.value = getSliderValue();
	elm.setEditValue(editItem, ei);
	
	// Update label to show current value
	if (label != null) {
	    String valueStr = getFormattedValue(ei, ei.value);
	    label.setText(Locale.LS(sliderText) + ": " + valueStr);
	}
	
	// Update ActionScheduler display message when manually adjusting slider
	ActionScheduler scheduler = ActionScheduler.getInstance();
	if (scheduler != null && sliderText != null && !sliderText.isEmpty()) {
	    String valueStr = getFormattedValue(ei, ei.value);
	    scheduler.setManualSliderMessage(sliderText, valueStr);
	}
	
	elm.sim.repaint();
    }
    
    // Helper method to get formatted value, checking for custom formatting from element
    String getFormattedValue(EditInfo ei, double value) {
        if (ei == null)
            return String.valueOf(value);
        
        // Check if element has custom slider text formatting
        try {
            String customText = elm.getSliderUnitText(editItem, ei, value);
            if (customText != null)
                return customText;
        } catch (Exception e) {
            // Element doesn't have getSliderUnitText method or it returned null
        }
        
        // Use default formatting
        return EditDialog.unitString(ei, value);
    }
    
    double getSliderValue() {
	double val = sharedSlider == null ? slider.getValue() : sharedSlider.slider.getValue();
	double rawValue = minValue + (maxValue-minValue)*val/100;
	
	// If stepIncrement > 0, snap to discrete steps
	if (stepIncrement > 0) {
	    int step = (int) Math.round((rawValue - minValue) / stepIncrement);
	    return minValue + step * stepIncrement;
	}
	
	return rawValue;
    }
    
    void deleteSlider(CirSim sim) {
	try {
	    sim.removeWidgetFromVerticalPanel(label);
	    sim.removeWidgetFromVerticalPanel(slider);
	} catch (Exception e) {}
    }
    
    void setMouseElm(CircuitElm e) {
	if (slider != null)
	    slider.draw();
    }
    
    boolean sliderBeingShared() {
	int i;
	for (i = 0; i != CirSim.theSim.adjustables.size(); i++) {
	    Adjustable adj = CirSim.theSim.adjustables.get(i);
	    if (adj.sharedSlider == this)
		return true;
	}
	return false;
    }
    
    String dump() {
	int ano = -1;
	if (sharedSlider != null)
	    ano = CirSim.theSim.adjustables.indexOf(sharedSlider);
	
	return elm.sim.locateElm(elm) + " F1 " + editItem + " " + minValue + " " + maxValue + " " + ano + " " +
			CustomLogicModel.escape(sliderText) + " " + stepIncrement;
    }
    
    /**
     * Highlight slider background (during animation)
     */
    public void highlightSlider(boolean highlight) {
	if (slider == null)
	    return;
	if (highlight) {
	    slider.getElement().getStyle().setBackgroundColor("#ffe082"); // Light amber
	    if (label != null)
		label.getElement().getStyle().setBackgroundColor("#ffe082");
	} else {
	    slider.getElement().getStyle().clearBackgroundColor();
	    if (label != null)
		label.getElement().getStyle().clearBackgroundColor();
	}
    }
    
    // reorder adjustables so that items with sliders come first in the list, followed by items that reference them.
    // this simplifies the UI code, and also makes it much easier to dump/undump the adjustables list, since we will
    // always be undumping the adjustables with sliders first, then the adjustables that reference them.
    static void reorderAdjustables() {
	Vector<Adjustable> newList = new Vector<Adjustable>();
	Vector<Adjustable> oldList = CirSim.theSim.adjustables;
	int i;
	for (i = 0; i != oldList.size(); i++) {
	    Adjustable adj = oldList.get(i);
	    if (adj.sharedSlider == null)
		newList.add(adj);
	}
	for (i = 0; i != oldList.size(); i++) {
	    Adjustable adj = oldList.get(i);
	    if (adj.sharedSlider != null)
		newList.add(adj);
	}
	CirSim.theSim.adjustables = newList;
    }
}
