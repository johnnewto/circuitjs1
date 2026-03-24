package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.*;


import java.util.Vector;

import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.PopupPanel;
import com.lushprojects.circuitjs1.client.ui.CheckboxMenuItem;
import com.lushprojects.circuitjs1.client.ui.ScopePopupMenu;
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
