package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.scope.Scope;

import com.lushprojects.circuitjs1.client.ui.EditInfo;


import com.lushprojects.circuitjs1.client.util.*;

import com.lushprojects.circuitjs1.client.elements.ActionTimeDialog;

import com.lushprojects.circuitjs1.client.elements.annotation.*;
import com.lushprojects.circuitjs1.client.elements.economics.*;
import com.lushprojects.circuitjs1.client.elements.electronics.digital.LogicInputElm;
import com.lushprojects.circuitjs1.client.elements.electronics.electromechanical.SwitchElm;
import com.lushprojects.circuitjs1.client.elements.electronics.passives.PotElm;
import com.lushprojects.circuitjs1.client.elements.electronics.sources.VarRailElm;
import com.lushprojects.circuitjs1.client.elements.electronics.wiring.WireElm;
import com.lushprojects.circuitjs1.client.elements.misc.*;

import static com.google.gwt.event.dom.client.KeyCodes.KEY_A;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_BACKSPACE;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_C;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_D;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_DELETE;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_ENTER;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_ESCAPE;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_N;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_O;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_P;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_S;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_SPACE;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_V;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_X;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_Y;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_Z;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ContextMenuEvent;
import com.google.gwt.event.dom.client.ContextMenuHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.event.dom.client.MouseWheelEvent;
import com.google.gwt.event.dom.client.MouseWheelHandler;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.user.client.ui.MenuItem;
import com.lushprojects.circuitjs1.client.registry.ElementFactoryFacade;
import com.google.gwt.user.client.ui.PopupPanel;
import com.lushprojects.circuitjs1.client.ui.Dialog;

class MouseInputHandler implements MouseDownHandler, MouseMoveHandler, MouseUpHandler,
    ClickHandler, DoubleClickHandler, ContextMenuHandler, NativePreviewHandler,
    MouseOutHandler, MouseWheelHandler {
    private final CirSim sim;
    private String lastCursorStyle;
    private boolean mouseWasOverSplitter;
    private int mouseMode = CirSim.MODE_SELECT;
    private int tempMouseMode = CirSim.MODE_SELECT;
    private String mouseModeStr = "Select";
    private int dragGridX;
    private int dragGridY;
    private int dragScreenX;
    private int dragScreenY;
    private int initDragGridX;
    private int initDragGridY;
    private long mouseDownTime;
    private long zoomTime;
    private int mouseCursorX = -1;
    private int mouseCursorY = -1;
    private Rectangle selectedArea;
    private boolean dragging;
    private int mousePost = -1;
    private int draggingPost;
    private boolean mouseDragging;

    MouseInputHandler(CirSim sim) {
        this.sim = sim;
    }

    String getLastCursorStyle() { return lastCursorStyle; }
    void setLastCursorStyle(String value) { lastCursorStyle = value; }
    boolean isMouseWasOverSplitter() { return mouseWasOverSplitter; }
    void setMouseWasOverSplitter(boolean value) { mouseWasOverSplitter = value; }
    int getMouseMode() { return mouseMode; }
    void setMouseModeValue(int value) { mouseMode = value; }
    int getTempMouseMode() { return tempMouseMode; }
    void setTempMouseMode(int value) { tempMouseMode = value; }
    String getMouseModeStr() { return mouseModeStr; }
    void setMouseModeStr(String value) { mouseModeStr = value; }
    int getDragGridX() { return dragGridX; }
    void setDragGridX(int value) { dragGridX = value; }
    int getDragGridY() { return dragGridY; }
    void setDragGridY(int value) { dragGridY = value; }
    int getDragScreenX() { return dragScreenX; }
    void setDragScreenX(int value) { dragScreenX = value; }
    int getDragScreenY() { return dragScreenY; }
    void setDragScreenY(int value) { dragScreenY = value; }
    int getInitDragGridX() { return initDragGridX; }
    void setInitDragGridX(int value) { initDragGridX = value; }
    int getInitDragGridY() { return initDragGridY; }
    void setInitDragGridY(int value) { initDragGridY = value; }
    long getMouseDownTime() { return mouseDownTime; }
    void setMouseDownTime(long value) { mouseDownTime = value; }
    long getZoomTime() { return zoomTime; }
    void setZoomTime(long value) { zoomTime = value; }
    int getMouseCursorX() { return mouseCursorX; }
    void setMouseCursorX(int value) { mouseCursorX = value; }
    int getMouseCursorY() { return mouseCursorY; }
    void setMouseCursorY(int value) { mouseCursorY = value; }
    Rectangle getSelectedArea() { return selectedArea; }
    void setSelectedArea(Rectangle value) { selectedArea = value; }
    boolean isDragging() { return dragging; }
    void setDragging(boolean value) { dragging = value; }
    int getMousePost() { return mousePost; }
    void setMousePost(int value) { mousePost = value; }
    int getDraggingPost() { return draggingPost; }
    void setDraggingPost(int value) { draggingPost = value; }
    private boolean isMouseDragging() { return mouseDragging; }
    private void setMouseDragging(boolean value) { mouseDragging = value; }

    private boolean doSwitch(int x, int y) {
        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (mouseElm == null || !(mouseElm instanceof SwitchElm))
            return false;
        SwitchElm se = (SwitchElm) mouseElm;
        if (!se.containsSwitchRect(x, y))
            return false;
        se.toggle();
        if (se.isMomentarySwitch())
            sim.heldSwitchElm = se;
        if (!(se instanceof LogicInputElm))
            sim.needAnalyze();
        return true;
    }

    private boolean doTableCollapseToggle(int x, int y) {
        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (mouseElm == null || !(mouseElm instanceof TableElm))
            return false;
        TableElm te = (TableElm) mouseElm;
        if (!te.isCollapseArrowClicked(x, y))
            return false;
        te.toggleCollapsedMode();
        sim.setLastInteractedTableForRouting(te);
        sim.repaint();
        return true;
    }

    private void mouseDragged(MouseMoveEvent e) {
        if (e.getNativeButton() == com.google.gwt.dom.client.NativeEvent.BUTTON_RIGHT) {
            if (!(e.isMetaKeyDown() ||
                  e.isShiftKeyDown() ||
                  e.isControlKeyDown() ||
                  e.isAltKeyDown()))
                return;
        }

        if (tempMouseMode == CirSim.MODE_DRAG_SPLITTER) {
            dragSplitter(e.getX(), e.getY());
            return;
        }
        int gx = sim.inverseTransformX(e.getX());
        int gy = sim.inverseTransformY(e.getY());
        if (!sim.circuitArea.contains(e.getX(), e.getY()))
            return;
        boolean changed = false;
        if (sim.dragElm != null)
            sim.dragElm.drag(gx, gy);
        boolean success = true;
        switch (tempMouseMode) {
        case CirSim.MODE_DRAG_ALL:
            dragAll(e.getX(), e.getY());
            break;
        case CirSim.MODE_DRAG_ROW:
            dragRow(sim.snapGrid(gx), sim.snapGrid(gy));
            changed = true;
            break;
        case CirSim.MODE_DRAG_COLUMN:
            dragColumn(sim.snapGrid(gx), sim.snapGrid(gy));
            changed = true;
            break;
        case CirSim.MODE_DRAG_POST:
            if (sim.getMouseElmForRouting() != null) {
                dragPost(sim.snapGrid(gx), sim.snapGrid(gy), e.isShiftKeyDown());
                changed = true;
            }
            break;
        case CirSim.MODE_SELECT:
            if (sim.getMouseElmForRouting() == null)
                selectArea(gx, gy, e.isShiftKeyDown());
            else if (!sim.noEditCheckItem.getState()) {
                if (System.currentTimeMillis() - mouseDownTime < 150)
                    return;

                tempMouseMode = CirSim.MODE_DRAG_SELECTED;
                changed = success = dragSelected(gx, gy);
            }
            break;
        case CirSim.MODE_DRAG_SELECTED:
            changed = success = dragSelected(gx, gy);
            break;

        }
        dragging = true;
        if (success) {
            dragScreenX = e.getX();
            dragScreenY = e.getY();
            dragGridX = sim.inverseTransformX(dragScreenX);
            dragGridY = sim.inverseTransformY(dragScreenY);
            if (!(tempMouseMode == CirSim.MODE_DRAG_SELECTED && onlyGraphicsElmsSelected())) {
                dragGridX = sim.snapGrid(dragGridX);
                dragGridY = sim.snapGrid(dragGridY);
            }
        }
        if (changed) {
            sim.needsRecoverySave = true;
        }
        sim.repaint();
    }

    private void dragSplitter(int x, int y) {
        double h = (double) sim.canvasHeight;
        if (h < 1)
            h = 1;
        sim.getScopeManager().setScopeHeightFraction(1.0 - (((double) y) / h));
        if (sim.getScopeManager().getScopeHeightFraction() < 0.1)
            sim.getScopeManager().setScopeHeightFraction(0.1);
        if (sim.getScopeManager().getScopeHeightFraction() > 0.9)
            sim.getScopeManager().setScopeHeightFraction(0.9);
        sim.getViewportController().setCircuitArea();
        sim.repaint();
    }

    private void dragAll(int x, int y) {
        int dx = x - dragScreenX;
        int dy = y - dragScreenY;
        if (dx == 0 && dy == 0)
            return;
        sim.getViewportController().translate(dx, dy);
        dragScreenX = x;
        dragScreenY = y;
    }

    private void dragRow(int x, int y) {
        int dy = y - dragGridY;
        if (dy == 0)
            return;
        int i;
        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce.y == dragGridY)
                ce.movePoint(0, 0, dy);
            if (ce.y2 == dragGridY)
                ce.movePoint(1, 0, dy);
        }
        sim.removeZeroLengthElements();
    }

    private void dragColumn(int x, int y) {
        int dx = x - dragGridX;
        if (dx == 0)
            return;
        int i;
        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce.x == dragGridX)
                ce.movePoint(0, dx, 0);
            if (ce.x2 == dragGridX)
                ce.movePoint(1, dx, 0);
        }
        sim.removeZeroLengthElements();
    }

    private boolean onlyGraphicsElmsSelected() {
        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (mouseElm != null && !(mouseElm instanceof GraphicElm))
            return false;
        int i;
        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce.isSelected() && !(ce instanceof GraphicElm))
                return false;
        }
        return true;
    }

    private boolean dragSelected(int x, int y) {
        boolean me = false;
        int i;
        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (mouseElm != null && !mouseElm.isSelected())
            mouseElm.setSelected(me = true);

        if (!onlyGraphicsElmsSelected()) {
            x = sim.snapGrid(x);
            y = sim.snapGrid(y);
        }

        int dx = x - dragGridX;
        int dy = y - dragGridY;
        if (dx == 0 && dy == 0) {
            if (me && mouseElm != null)
                mouseElm.setSelected(false);
            return false;
        }
        boolean allowed = true;

        for (i = 0; allowed && i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce.isSelected() && !ce.allowMove(dx, dy))
                allowed = false;
        }

        if (allowed) {
            for (i = 0; i != sim.elmList.size(); i++) {
                CircuitElm ce = sim.getElm(i);
                if (ce.isSelected())
                    ce.move(dx, dy);
            }
            sim.needAnalyze();
        }

        if (me && mouseElm != null)
            mouseElm.setSelected(false);

        return allowed;
    }

    private void dragPost(int x, int y, boolean all) {
        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (mouseElm == null)
            return;
        if (draggingPost == -1) {
            draggingPost =
                (Graphics.distanceSq(mouseElm.x, mouseElm.y, x, y) >
                 Graphics.distanceSq(mouseElm.x2, mouseElm.y2, x, y)) ? 1 : 0;
        }
        int dx = x - dragGridX;
        int dy = y - dragGridY;
        if (dx == 0 && dy == 0)
            return;

        if (all) {
            int i;
            for (i = 0; i != sim.elmList.size(); i++) {
                CircuitElm e = sim.elmList.get(i);

                int p = 0;
                if (e.x == dragGridX && e.y == dragGridY)
                    p = 0;
                else if (e.x2 == dragGridX && e.y2 == dragGridY)
                    p = 1;
                else
                    continue;
                e.movePoint(p, dx, dy);
            }
        } else
            mouseElm.movePoint(draggingPost, dx, dy);
        sim.needAnalyze();
    }

    void doFlip() {
        sim.getMenuUiState().menuElm.flipPosts();
        sim.needAnalyze();
    }

    private void selectArea(int x, int y, boolean add) {
        int x1 = sim.min(x, initDragGridX);
        int x2 = sim.max(x, initDragGridX);
        int y1 = sim.min(y, initDragGridY);
        int y2 = sim.max(y, initDragGridY);
        selectedArea = new Rectangle(x1, y1, x2 - x1, y2 - y1);
        int i;
        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            ce.selectRect(selectedArea, add);
        }
        sim.enableDisableMenuItems();
    }

    private boolean mouseIsOverSplitter(int x, int y) {
        boolean isOverSplitter;
        if (sim.scopeCount == 0)
            return false;
        isOverSplitter = ((x >= 0) && (x < sim.circuitArea.width) &&
                          (y >= sim.circuitArea.height - 5) && (y < sim.circuitArea.height));
        if (isOverSplitter != mouseWasOverSplitter) {
            if (isOverSplitter)
                setCursorStyle("cursorSplitter");
            else
                setMouseMode(mouseMode);
        }
        mouseWasOverSplitter = isOverSplitter;
        return isOverSplitter;
    }

    private void updateActionTimeElmIconHover(int gx, int gy) {
        for (int i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof ActionTimeElm) {
                ActionTimeElm ate = (ActionTimeElm) ce;
                boolean hovered = ate.isPointInPlayPauseIcon(gx, gy);
                ate.setPlayPauseIconHovered(hovered);
            }
        }
    }

    @Override
    public void onMouseMove(MouseMoveEvent e) {
        e.preventDefault();
        mouseCursorX = e.getX();
        mouseCursorY = e.getY();
        if (isMouseDragging()) {
            mouseDragged(e);
            return;
        }
        mouseSelect(e);
        sim.getScopeManager().setScopeMenuSelected(-1);
    }

    private void mouseSelect(MouseEvent<?> e) {
        CircuitElm newMouseElm = null;
        mouseCursorX = e.getX();
        mouseCursorY = e.getY();
        int sx = e.getX();
        int sy = e.getY();
        int gx = sim.inverseTransformX(sx);
        int gy = sim.inverseTransformY(sy);
        dragGridX = sim.snapGrid(gx);
        dragGridY = sim.snapGrid(gy);
        dragScreenX = sx;
        dragScreenY = sy;
        draggingPost = -1;
        int i;

        mousePost = -1;
        sim.plotXElm = sim.plotYElm = null;

        if (mouseIsOverSplitter(sx, sy)) {
            sim.setMouseElm(null);
            return;
        }

        if (sim.circuitArea.contains(sx, sy)) {
            CircuitElm currentMouseElm = sim.getMouseElmForRouting();
            if (currentMouseElm != null && (currentMouseElm.getHandleGrabbedClose(gx, gy, CirSim.POSTGRABSQ, CirSim.MINPOSTGRABSIZE) >= 0)) {
                newMouseElm = currentMouseElm;
            } else {
                int bestDist = 100000000;
                for (i = 0; i != sim.elmList.size(); i++) {
                    CircuitElm ce = sim.getElm(i);
                    if (ce.boundingBox.contains(gx, gy)) {
                        int dist = ce.getMouseDistance(gx, gy);
                        if (dist >= 0 && dist < bestDist) {
                            bestDist = dist;
                            newMouseElm = ce;
                        }
                    }
                }
            }
        }
        sim.getScopeManager().setScopeSelected(-1);
        if (newMouseElm == null) {
            for (i = 0; i != sim.scopeCount; i++) {
                Scope s = sim.scopes[i];
                if (s.containsScreenPoint(sx, sy)) {
                    newMouseElm = s.getElm();
                    if (s.isPlotXyEnabled()) {
                        sim.plotXElm = s.getXElm();
                        sim.plotYElm = s.getYElm();
                    }
                    sim.getScopeManager().setScopeSelected(i);
                }
            }
            for (i = 0; i != sim.elmList.size(); i++) {
                CircuitElm ce = sim.getElm(i);
                if (mouseMode == CirSim.MODE_DRAG_POST) {
                    if (ce.getHandleGrabbedClose(gx, gy, CirSim.POSTGRABSQ, 0) > 0) {
                        newMouseElm = ce;
                        break;
                    }
                }
                int j;
                int jn = ce.getPostCount();
                for (j = 0; j != jn; j++) {
                    Point pt = ce.getPost(j);
                    if (Graphics.distanceSq(pt.x, pt.y, gx, gy) < 26) {
                        newMouseElm = ce;
                        mousePost = j;
                        break;
                    }
                }
            }
        } else {
            mousePost = -1;
            for (i = 0; i != newMouseElm.getPostCount(); i++) {
                Point pt = newMouseElm.getPost(i);
                if (Graphics.distanceSq(pt.x, pt.y, gx, gy) < 26)
                    mousePost = i;
            }
        }
        sim.repaint();
        sim.setMouseElm(newMouseElm);

        updateActionTimeElmIconHover(gx, gy);
    }

    @Override
    public void onContextMenu(ContextMenuEvent e) {
        e.preventDefault();
        if (!sim.dialogIsShowing()) {
            sim.setMenuClientX(e.getNativeEvent().getClientX());
            sim.setMenuClientY(e.getNativeEvent().getClientY());
            doPopupMenu();
        }
    }

    private void doPopupMenu() {
        if (sim.noEditCheckItem.getState() || sim.dialogIsShowing())
            return;
        sim.getMenuUiState().menuElm = sim.getMouseElmForRouting();
        sim.getScopeManager().setMenuScope(-1);
        sim.getScopeManager().setMenuPlot(-1);
        int x, y;
        if (sim.getScopeManager().getScopeSelected() != -1) {
            if (sim.scopes[sim.getScopeManager().getScopeSelected()].canMenu()) {
                sim.getScopeManager().setMenuScope(sim.getScopeManager().getScopeSelected());
                sim.getScopeManager().setMenuPlot(sim.scopes[sim.getScopeManager().getScopeSelected()].getSelectedPlotIndex());
                sim.getMenuUiState().scopePopupMenu.doScopePopupChecks(false, sim.getScopeManager().canStackScope(sim.getScopeManager().getScopeSelected()), sim.getScopeManager().canCombineScope(sim.getScopeManager().getScopeSelected()),
                                                      sim.getScopeManager().canUnstackScope(sim.getScopeManager().getScopeSelected()), sim.scopes[sim.getScopeManager().getScopeSelected()]);
                sim.getMenuUiState().contextPanel = new PopupPanel(true);
                sim.getMenuUiState().contextPanel.add(sim.getMenuUiState().scopePopupMenu.getMenuBar());
                y = Math.max(0, Math.min(sim.getMenuClientY(), sim.canvasHeight - 160));
                sim.getMenuUiState().contextPanel.setPopupPosition(sim.getMenuClientX(), y);
                sim.getMenuUiState().contextPanel.show();
            }
        } else if (sim.getMouseElmForRouting() != null) {
            CircuitElm mouseElm = sim.getMouseElmForRouting();
            if (!(mouseElm instanceof ScopeElm)) {
                sim.elmScopeMenuItem.setEnabled(mouseElm.canViewInScope());
                sim.elmFloatScopeMenuItem.setEnabled(mouseElm.canViewInScope());
                if ((sim.scopeCount + sim.getScopeManager().countScopeElms()) <= 1) {
                    sim.elmAddScopeMenuItem.setCommand(new MyCommand("elm", "addToScope0"));
                    sim.elmAddScopeMenuItem.setSubMenu(null);
                    sim.elmAddScopeMenuItem.setEnabled(mouseElm.canViewInScope() && (sim.scopeCount + sim.getScopeManager().countScopeElms()) > 0);
                } else {
                    sim.getMenuBuilder().composeSelectScopeMenu(sim.getMenuUiState().selectScopeMenuBar);
                    sim.elmAddScopeMenuItem.setCommand(null);
                    sim.elmAddScopeMenuItem.setSubMenu(sim.getMenuUiState().selectScopeMenuBar);
                    sim.elmAddScopeMenuItem.setEnabled(mouseElm.canViewInScope());
                }
                sim.elmEditMenuItem.setEnabled(mouseElm.getEditInfo(0) != null);
                sim.elmSwapMenuItem.setEnabled(mouseElm.getPostCount() == 2);
                sim.elmSplitMenuItem.setEnabled(canSplit(mouseElm));
                sim.elmSliderMenuItem.setEnabled(sliderItemEnabled(mouseElm));
                sim.elmSankeyMenuItem.setEnabled(mouseElm instanceof SFCTableElm);
                sim.elmSequenceDiagramMenuItem.setEnabled(mouseElm instanceof SFCTableElm);
                sim.elmDagBlocksMenuItem.setEnabled(mouseElm instanceof EquationTableElm);
                sim.elmEquationTableDebugMenuItem.setEnabled(mouseElm instanceof EquationTableElm);
                sim.elmEquationTableReferenceMenuItem.setEnabled(mouseElm instanceof EquationTableElm);
                boolean canFlipX = mouseElm.canFlipX();
                boolean canFlipY = mouseElm.canFlipY();
                boolean canFlipXY = mouseElm.canFlipXY();
                for (CircuitElm elm : sim.elmList)
                    if (elm.isSelected()) {
                        if (!elm.canFlipX())
                            canFlipX = false;
                        if (!elm.canFlipY())
                            canFlipY = false;
                        if (!elm.canFlipXY())
                            canFlipXY = false;
                    }
                sim.elmFlipXMenuItem.setEnabled(canFlipX);
                sim.elmFlipYMenuItem.setEnabled(canFlipY);
                sim.elmFlipXYMenuItem.setEnabled(canFlipXY);
                sim.getMenuUiState().contextPanel = new PopupPanel(true);
                sim.getMenuUiState().contextPanel.add(sim.getMenuUiState().elmMenuBar);
                sim.getMenuUiState().contextPanel.setPopupPosition(sim.getMenuClientX(), sim.getMenuClientY());
                sim.getMenuUiState().contextPanel.show();
            } else {
                ScopeElm s = (ScopeElm) mouseElm;
                if (s.elmScope.canMenu()) {
                    sim.getScopeManager().setMenuPlot(s.elmScope.getSelectedPlotIndex());
                    sim.getMenuUiState().scopePopupMenu.doScopePopupChecks(true, false, false, false, s.elmScope);
                    sim.getMenuUiState().contextPanel = new PopupPanel(true);
                    sim.getMenuUiState().contextPanel.add(sim.getMenuUiState().scopePopupMenu.getMenuBar());
                    sim.getMenuUiState().contextPanel.setPopupPosition(sim.getMenuClientX(), sim.getMenuClientY());
                    sim.getMenuUiState().contextPanel.show();
                }
            }
        } else {
            doMainMenuChecks();
            sim.getMenuUiState().contextPanel = new PopupPanel(true);
            sim.getMenuUiState().contextPanel.add(sim.getMenuUiState().mainMenuBar);
            x = Math.max(0, Math.min(sim.getMenuClientX(), sim.canvasWidth - 400));
            y = Math.max(0, Math.min(sim.getMenuClientY(), sim.canvasHeight - 450));
            sim.getMenuUiState().contextPanel.setPopupPosition(x, y);
            sim.getMenuUiState().contextPanel.show();
        }
    }

    private boolean canSplit(CircuitElm ce) {
        if (!(ce instanceof WireElm))
            return false;
        WireElm we = (WireElm) ce;
        if (we.x == we.x2 || we.y == we.y2)
            return true;
        return false;
    }

    private boolean sliderItemEnabled(CircuitElm elm) {
        int i;

        if (elm instanceof VarRailElm || elm instanceof PotElm)
            return false;

        for (i = 0; ; i++) {
            EditInfo ei = elm.getEditInfo(i);
            if (ei == null)
                return false;
            if (ei.canCreateAdjustable())
                return true;
        }
    }

    void longPress() {
        doPopupMenu();
    }

    void twoFingerTouch(int x, int y) {
        tempMouseMode = CirSim.MODE_DRAG_ALL;
        dragScreenX = x;
        dragScreenY = y;
    }

    @Override
    public void onClick(ClickEvent e) {
        e.preventDefault();
        if ((e.getNativeButton() == com.google.gwt.dom.client.NativeEvent.BUTTON_MIDDLE))
            sim.scrollValues(e.getNativeEvent().getClientX(), e.getNativeEvent().getClientY(), 0);
    }

    @Override
    public void onDoubleClick(DoubleClickEvent e) {
        e.preventDefault();
        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (mouseElm != null && !(mouseElm instanceof SwitchElm) && !sim.noEditCheckItem.getState()) {
            if (mouseElm instanceof TextElm) {
                TextElm te = (TextElm) mouseElm;
                if (te.isHyperlink() && te.handleLinkClick()) {
                    return;
                }
            }
            if (mouseElm instanceof TableElm) {
                TableElm te = (TableElm) mouseElm;
                int gx = sim.inverseTransformX(e.getX());
                int gy = sim.inverseTransformY(e.getY());
                if (!te.isCollapseArrowClicked(gx, gy)) {
                    te.openTableEditDialog();
                }
            } else if (mouseElm instanceof EquationTableElm) {
                ((EquationTableElm) mouseElm).openEditDialog();
            } else if (mouseElm instanceof ActionTimeElm) {
                ActionTimeDialog.openDialog(sim);
            } else {
                sim.getEditDialogActions().doEdit(mouseElm);
            }
        }
    }

    @Override
    public void onMouseOut(MouseOutEvent e) {
        mouseCursorX = -1;
    }

    void clearMouseElm() {
        sim.getScopeManager().setScopeSelected(-1);
        sim.setMouseElm(null);
        sim.plotXElm = sim.plotYElm = null;
    }

    @Override
    public void onMouseDown(MouseDownEvent e) {
        e.preventDefault();

        sim.cv.setFocus(true);

        sim.stopElm = null;
        sim.setMenuX(e.getX());
        sim.setMenuClientX(e.getX());
        sim.setMenuY(e.getY());
        sim.setMenuClientY(e.getY());
        mouseDownTime = System.currentTimeMillis();

        sim.getClipboardManager().enablePaste();

        if (e.getNativeButton() != com.google.gwt.dom.client.NativeEvent.BUTTON_LEFT && e.getNativeButton() != com.google.gwt.dom.client.NativeEvent.BUTTON_MIDDLE)
            return;

        mouseSelect(e);

        setMouseDragging(true);
        sim.didSwitch = false;

        sim.simRunningBeforeDrag = sim.simRunning;

        if (sim.getScopeManager().mouseIsOverScopeMinMaxButton(e.getX(), e.getY())) {
            sim.getScopeManager().toggleScopePanelSize();
            setMouseDragging(false);
            return;
        }

        if (mouseWasOverSplitter) {
            tempMouseMode = CirSim.MODE_DRAG_SPLITTER;
            return;
        }
        if (e.getNativeButton() == com.google.gwt.dom.client.NativeEvent.BUTTON_LEFT) {
            tempMouseMode = mouseMode;
            if (e.isAltKeyDown() && e.isMetaKeyDown())
                tempMouseMode = CirSim.MODE_DRAG_COLUMN;
            else if (e.isAltKeyDown() && e.isShiftKeyDown())
                tempMouseMode = CirSim.MODE_DRAG_ROW;
            else if (e.isShiftKeyDown())
                tempMouseMode = CirSim.MODE_SELECT;
            else if (e.isAltKeyDown())
                tempMouseMode = CirSim.MODE_DRAG_ALL;
            else if (e.isControlKeyDown() || e.isMetaKeyDown())
                tempMouseMode = CirSim.MODE_DRAG_POST;
        } else
            tempMouseMode = CirSim.MODE_DRAG_ALL;


        if (sim.noEditCheckItem.getState())
            tempMouseMode = CirSim.MODE_SELECT;

        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (!(sim.dialogIsShowing()) && ((sim.getScopeManager().getScopeSelected() != -1 && sim.scopes[sim.getScopeManager().getScopeSelected()].cursorInSettingsWheel()) ||
                                         (sim.getScopeManager().getScopeSelected() == -1 && mouseElm instanceof ScopeElm && ((ScopeElm) mouseElm).elmScope.cursorInSettingsWheel()))) {
            if (sim.noEditCheckItem.getState())
                return;
            Scope s;
            if (sim.getScopeManager().getScopeSelected() != -1)
                s = sim.scopes[sim.getScopeManager().getScopeSelected()];
            else
                s = ((ScopeElm) mouseElm).elmScope;
            s.properties();
            sim.getClipboardManager().clearSelection();
            setMouseDragging(false);
            return;
        }

        int gx = sim.inverseTransformX(e.getX());
        int gy = sim.inverseTransformY(e.getY());

        for (int i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof ActionTimeElm) {
                ActionTimeElm ate = (ActionTimeElm) ce;
                if (ate.isPointInPlayPauseIcon(gx, gy)) {
                    ate.handlePlayPauseIconClick();
                    setMouseDragging(false);
                    return;
                }
            }
        }

        if (doSwitch(gx, gy)) {
            sim.didSwitch = true;
            return;
        }

        if (doTableCollapseToggle(gx, gy)) {
            sim.didSwitch = true;
            return;
        }

        if (mouseElm instanceof TableElm) {
            sim.setLastInteractedTableForRouting((TableElm) mouseElm);
        }

        if (tempMouseMode == CirSim.MODE_SELECT && mouseElm != null && !sim.noEditCheckItem.getState() &&
            mouseElm.getHandleGrabbedClose(gx, gy, CirSim.POSTGRABSQ, CirSim.MINPOSTGRABSIZE) >= 0 &&
            !sim.getClipboardManager().anySelectedButMouse())
            tempMouseMode = CirSim.MODE_DRAG_POST;

        if (tempMouseMode != CirSim.MODE_SELECT && tempMouseMode != CirSim.MODE_DRAG_SELECTED)
            sim.getClipboardManager().clearSelection();

        sim.getUndoRedoManager().pushUndo();
        initDragGridX = gx;
        initDragGridY = gy;
        dragging = true;

        if (sim.simRunning) {
            sim.simRunning = false;
        }

        if (tempMouseMode != CirSim.MODE_ADD_ELM)
            return;

        int x0 = sim.snapGrid(gx);
        int y0 = sim.snapGrid(gy);
        if (!sim.circuitArea.contains(e.getX(), e.getY()))
            return;

        try {
            sim.dragElm = ElementFactoryFacade.constructFromClassKey(mouseModeStr, x0, y0);
        } catch (Exception ex) {
            CirSim.debugger();
        }
    }

    void doMainMenuChecks() {
        int c = sim.getMenuUiState().mainMenuItems.size();
        int i;
        for (i = 0; i < c; i++) {
            String s = sim.getMenuUiState().mainMenuItemNames.get(i);
            sim.getMenuUiState().mainMenuItems.get(i).setState(s == mouseModeStr);
        }
        sim.stackAllItem.setEnabled(sim.scopeCount > 1 && sim.scopes[sim.scopeCount - 1].getStackPosition() > 0);
        sim.unstackAllItem.setEnabled(sim.scopeCount > 1 && sim.scopes[sim.scopeCount - 1].getStackPosition() != sim.scopeCount - 1);
        sim.combineAllItem.setEnabled(sim.scopeCount > 1);
        sim.separateAllItem.setEnabled(sim.scopeCount > 0);

        if (CirSim.lastSubcircuitMenuUpdate != CustomCompositeModel.sequenceNumber)
            sim.getMenuBuilder().composeSubcircuitMenu();
    }

    @Override
    public void onMouseUp(MouseUpEvent e) {
        e.preventDefault();
        setMouseDragging(false);

        if (tempMouseMode == CirSim.MODE_SELECT && selectedArea == null)
            sim.getClipboardManager().clearSelection();

        if (tempMouseMode == CirSim.MODE_DRAG_POST && draggingPost == -1)
            sim.doSplit(sim.getMouseElmForRouting());

        tempMouseMode = mouseMode;
        selectedArea = null;

        if (dragging && sim.simRunningBeforeDrag && !sim.simRunning) {
            sim.simRunning = true;
        }

        dragging = false;
        setMouseDragging(false);

        boolean circuitChanged = false;
        if (sim.heldSwitchElm != null) {
            sim.heldSwitchElm.mouseUp();
            sim.heldSwitchElm = null;
            circuitChanged = true;
        }
        if (sim.dragElm != null) {
            if (sim.dragElm.creationFailed()) {
                sim.dragElm.delete();
                if (mouseMode == CirSim.MODE_SELECT || mouseMode == CirSim.MODE_DRAG_SELECTED)
                    sim.getClipboardManager().clearSelection();
            }
            else {
                sim.elmList.addElement(sim.dragElm);
                sim.dragElm.draggingDone();
                circuitChanged = true;
                sim.needsRecoverySave = true;
                sim.unsavedChanges = true;
                if (mouseMode == CirSim.MODE_ADD_ELM) {
                    setMouseMode(CirSim.MODE_SELECT);
                    tempMouseMode = CirSim.MODE_SELECT;
                }
            }
            sim.dragElm = null;
        }
        if (circuitChanged) {
            sim.needAnalyze();
            sim.getUndoRedoManager().pushUndo();
        }
        if (circuitChanged || sim.needsRecoverySave) {
            sim.getUiPanelManager().refreshModelInfoEditorAfterCircuitMutation();
        }
        if (sim.needsRecoverySave) {
            sim.getCircuitIOService().writeRecoveryToStorage();
            sim.needsRecoverySave = false;
        }
        if (sim.dragElm != null)
            sim.dragElm.delete();
        sim.dragElm = null;
        sim.repaint();
    }

    @Override
    public void onMouseWheel(MouseWheelEvent e) {
        e.preventDefault();
        int wheelDelta = CirSim.normalizeWheelDelta(e.getDeltaY());
        if (wheelDelta == 0)
            return;

        boolean zoomOnly = System.currentTimeMillis() < zoomTime + 1000;

        if (!sim.mouseWheelEditCheckItem.getState())
            zoomOnly = true;

        if (!zoomOnly)
            sim.scrollValues(e.getNativeEvent().getClientX(), e.getNativeEvent().getClientY(), wheelDelta);

        CircuitElm mouseElm = sim.getMouseElmForRouting();
        boolean wheelEditedCircuit = false;
        if (mouseElm instanceof com.google.gwt.event.dom.client.MouseWheelHandler && !zoomOnly) {
            ((com.google.gwt.event.dom.client.MouseWheelHandler) mouseElm).onMouseWheel(e);
            wheelEditedCircuit = true;
        }
        else if (sim.getScopeManager().getScopeSelected() != -1 && !zoomOnly) {
            sim.scopes[sim.getScopeManager().getScopeSelected()].onMouseWheel(e);
            wheelEditedCircuit = true;
        }
        else if (!sim.dialogIsShowing()) {
            mouseCursorX = e.getX();
            mouseCursorY = e.getY();
            sim.getViewportController().zoomCircuit(-wheelDelta * sim.wheelSensitivity, false);
            zoomTime = System.currentTimeMillis();
        }
        if (wheelEditedCircuit)
            sim.getUiPanelManager().refreshModelInfoEditorAfterCircuitMutation();
        sim.repaint();
    }

    @Override
    public void onPreviewNativeEvent(NativePreviewEvent e) {
        int cc = e.getNativeEvent().getCharCode();
        int t = e.getTypeInt();
        int code = e.getNativeEvent().getKeyCode();
        if (sim.dialogIsShowing()) {
            if (CirSimDialogCoordinator.getScrollValuePopup() != null && CirSimDialogCoordinator.getScrollValuePopup().isShowing() &&
                (t & Event.ONKEYDOWN) != 0) {
                if (code == KEY_ESCAPE || code == KEY_SPACE)
                    CirSimDialogCoordinator.getScrollValuePopup().close(false);
                if (code == KEY_ENTER)
                    CirSimDialogCoordinator.getScrollValuePopup().close(true);
            }

            Dialog dlg = CirSimDialogCoordinator.getEditDialog();
            if (CirSimDialogCoordinator.getDiodeModelEditDialog() != null)
                dlg = CirSimDialogCoordinator.getDiodeModelEditDialog();
            if (CirSimDialogCoordinator.getCustomLogicEditDialog() != null)
                dlg = CirSimDialogCoordinator.getCustomLogicEditDialog();
            if (CirSimDialogCoordinator.getDialogShowing() != null)
                dlg = CirSimDialogCoordinator.getDialogShowing();


            if (dlg != null && dlg.isShowing()) {
                if ((t & Event.ONKEYDOWN) != 0) {
                    if (code == KEY_ESCAPE) {
                        dlg.closeDialog();
                    }
                    if (code == KEY_ENTER) {
                        dlg.enterPressed();
                    }
                }
                return;
            }
        }

        if ((t & Event.ONKEYPRESS) != 0) {
            if (cc == '-') {
                sim.getCommandRouter().menuPerformed("key", "zoomout");
                e.cancel();
            }
            if (cc == '+' || cc == '=') {
                sim.getCommandRouter().menuPerformed("key", "zoomin");
                e.cancel();
            }
            if (cc == '0') {
                sim.getCommandRouter().menuPerformed("key", "zoom100");
                e.cancel();
            }
            if (cc == '/' && sim.shortcuts['/'] == null) {
                sim.getCommandRouter().menuPerformed("key", "search");
                e.cancel();
            }
        }

        if (sim.noEditCheckItem.getState())
            return;

        if ((t & Event.ONKEYDOWN) != 0) {
            if (code == KEY_BACKSPACE || code == KEY_DELETE) {
                if (sim.getScopeManager().getScopeSelected() != -1) {
                    sim.scopes[sim.getScopeManager().getScopeSelected()].setElm(null);
                    sim.getScopeManager().setScopeSelected(-1);
                } else {
                    sim.getMenuUiState().menuElm = null;
                    sim.getUndoRedoManager().pushUndo();
                    sim.getClipboardManager().doDelete(true);
                    e.cancel();
                }
            }
            if (code == KEY_ESCAPE) {
                setMouseMode(CirSim.MODE_SELECT);
                mouseModeStr = "Select";
                sim.updateToolbar();
                tempMouseMode = mouseMode;
                e.cancel();
            }

            if (e.getNativeEvent().getCtrlKey() || e.getNativeEvent().getMetaKey()) {
                if (code == KEY_C) {
                    sim.getCommandRouter().menuPerformed("key", "copy");
                    e.cancel();
                }
                if (code == KEY_X) {
                    sim.getCommandRouter().menuPerformed("key", "cut");
                    e.cancel();
                }
                if (code == KEY_V) {
                    sim.getCommandRouter().menuPerformed("key", "paste");
                    e.cancel();
                }
                if (code == KEY_Z) {
                    sim.getCommandRouter().menuPerformed("key", "undo");
                    e.cancel();
                }
                if (code == KEY_Y) {
                    sim.getCommandRouter().menuPerformed("key", "redo");
                    e.cancel();
                }
                if (code == KEY_D) {
                    sim.getCommandRouter().menuPerformed("key", "duplicate");
                    e.cancel();
                }
                if (code == KEY_A) {
                    sim.getCommandRouter().menuPerformed("key", "selectAll");
                    e.cancel();
                }
                if (code == KEY_P) {
                    sim.getCommandRouter().menuPerformed("key", "print");
                    e.cancel();
                }
                if (code == KEY_N && sim.getPlatformInterop().isElectron()) {
                    sim.getCommandRouter().menuPerformed("key", "newwindow");
                    e.cancel();
                }
                if (code == KEY_S) {
                    String cmd = "exportaslocalfile";
                    if (sim.getPlatformInterop().isElectron())
                        cmd = sim.saveFileItem.isEnabled() ? "save" : "saveas";
                    sim.getCommandRouter().menuPerformed("key", cmd);
                    e.cancel();
                }
                if (code == KEY_O) {
                    sim.getCommandRouter().menuPerformed("key", "importfromlocalfile");
                    e.cancel();
                }
            }
        }
        if ((t & Event.ONKEYPRESS) != 0) {
            if (cc > 32 && cc < 127) {
                String c = sim.shortcuts[cc];
                e.cancel();
                if (c == null)
                    return;
                setMouseMode(CirSim.MODE_ADD_ELM);
                mouseModeStr = c;
                sim.updateToolbar();
                tempMouseMode = mouseMode;
            }
            if (cc == 32) {
                setMouseMode(CirSim.MODE_SELECT);
                mouseModeStr = "Select";
                sim.updateToolbar();
                tempMouseMode = mouseMode;
                e.cancel();
            }
        }
    }

    void setMouseMode(int mode) {
        mouseMode = mode;
        if (mode == CirSim.MODE_ADD_ELM) {
            setCursorStyle("cursorCross");
        } else {
            setCursorStyle("cursorPointer");
        }
    }

    void setCursorStyle(String s) {
        if (lastCursorStyle != null)
            sim.cv.removeStyleName(lastCursorStyle);
        sim.cv.addStyleName(s);
        lastCursorStyle = s;
    }
}
