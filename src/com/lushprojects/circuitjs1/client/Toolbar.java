package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.*;

/**
 * Abstract base class for toolbars (Electronics and Economics)
 */
public abstract class Toolbar extends HorizontalPanel {
    
    public abstract void setModeLabel(String text);
    public abstract void highlightButton(String key);
    public abstract void setEuroResistors(boolean euro);
}
