package com.lushprojects.circuitjs1.client;

import java.util.Vector;

import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.PopupPanel;
import com.lushprojects.circuitjs1.client.ui.CheckboxMenuItem;
import com.lushprojects.circuitjs1.client.ui.ScopePopupMenu;

final class MenuUiState {
    CircuitElm menuElm;
    PopupPanel contextPanel;

    MenuBar menuBar;
    MenuBar drawMenuBar;
    MenuBar fileMenuBar;
    MenuBar elmMenuBar;
    MenuBar helpMenuBar;
    MenuBar mainMenuBar;
    MenuBar selectScopeMenuBar;
    MenuBar[] subcircuitMenuBar;

    ScopePopupMenu scopePopupMenu;

    Vector<CheckboxMenuItem> mainMenuItems = new Vector<CheckboxMenuItem>();
    Vector<String> mainMenuItemNames = new Vector<String>();
    Vector<MenuItem> selectScopeMenuItems;
}
