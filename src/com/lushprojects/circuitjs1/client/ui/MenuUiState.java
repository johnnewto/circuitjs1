package com.lushprojects.circuitjs1.client.ui;

import com.lushprojects.circuitjs1.client.CircuitElm;
import java.util.Vector;

import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.PopupPanel;

public final class MenuUiState {
    public CircuitElm menuElm;
    public PopupPanel contextPanel;

    public MenuBar menuBar;
    public MenuBar drawMenuBar;
    public MenuBar fileMenuBar;
    public MenuBar elmMenuBar;
    public MenuBar helpMenuBar;
    public MenuBar mainMenuBar;
    public MenuBar selectScopeMenuBar;
    public MenuBar[] subcircuitMenuBar;

    public ScopePopupMenu scopePopupMenu;

    public Vector<CheckboxMenuItem> mainMenuItems = new Vector<CheckboxMenuItem>();
    public Vector<String> mainMenuItemNames = new Vector<String>();
    public Vector<MenuItem> selectScopeMenuItems;
}
