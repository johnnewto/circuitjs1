package com.lushprojects.circuitjs1.client;

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
import com.google.gwt.user.client.ui.PopupPanel;

class MouseInputHandler implements MouseDownHandler, MouseMoveHandler, MouseUpHandler,
    ClickHandler, DoubleClickHandler, ContextMenuHandler, NativePreviewHandler,
    MouseOutHandler, MouseWheelHandler {
    private final CirSim sim;

    MouseInputHandler(CirSim sim) {
        this.sim = sim;
    }

    boolean doSwitch(int x, int y) {
        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (mouseElm == null || !(mouseElm instanceof SwitchElm))
            return false;
        SwitchElm se = (SwitchElm) mouseElm;
        if (!se.getSwitchRect().contains(x, y))
            return false;
        se.toggle();
        if (se.momentary)
            sim.heldSwitchElm = se;
        if (!(se instanceof LogicInputElm))
            sim.needAnalyze();
        return true;
    }

    boolean doTableCollapseToggle(int x, int y) {
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

    void mouseDragged(MouseMoveEvent e) {
        if (e.getNativeButton() == com.google.gwt.dom.client.NativeEvent.BUTTON_RIGHT) {
            if (!(e.isMetaKeyDown() ||
                  e.isShiftKeyDown() ||
                  e.isControlKeyDown() ||
                  e.isAltKeyDown()))
                return;
        }

        if (sim.tempMouseMode == CirSim.MODE_DRAG_SPLITTER) {
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
        switch (sim.tempMouseMode) {
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
                if (System.currentTimeMillis() - sim.mouseDownTime < 150)
                    return;

                sim.tempMouseMode = CirSim.MODE_DRAG_SELECTED;
                changed = success = dragSelected(gx, gy);
            }
            break;
        case CirSim.MODE_DRAG_SELECTED:
            changed = success = dragSelected(gx, gy);
            break;

        }
        sim.dragging = true;
        if (success) {
            sim.dragScreenX = e.getX();
            sim.dragScreenY = e.getY();
            sim.dragGridX = sim.inverseTransformX(sim.dragScreenX);
            sim.dragGridY = sim.inverseTransformY(sim.dragScreenY);
            if (!(sim.tempMouseMode == CirSim.MODE_DRAG_SELECTED && onlyGraphicsElmsSelected())) {
                sim.dragGridX = sim.snapGrid(sim.dragGridX);
                sim.dragGridY = sim.snapGrid(sim.dragGridY);
            }
        }
        if (changed) {
            sim.needsRecoverySave = true;
        }
        sim.repaint();
    }

    void dragSplitter(int x, int y) {
        double h = (double) sim.canvasHeight;
        if (h < 1)
            h = 1;
        sim.scopeHeightFraction = 1.0 - (((double) y) / h);
        if (sim.scopeHeightFraction < 0.1)
            sim.scopeHeightFraction = 0.1;
        if (sim.scopeHeightFraction > 0.9)
            sim.scopeHeightFraction = 0.9;
        sim.setCircuitArea();
        sim.repaint();
    }

    void dragAll(int x, int y) {
        int dx = x - sim.dragScreenX;
        int dy = y - sim.dragScreenY;
        if (dx == 0 && dy == 0)
            return;
        sim.transform[4] += dx;
        sim.transform[5] += dy;
        sim.dragScreenX = x;
        sim.dragScreenY = y;
    }

    void dragRow(int x, int y) {
        int dy = y - sim.dragGridY;
        if (dy == 0)
            return;
        int i;
        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce.y == sim.dragGridY)
                ce.movePoint(0, 0, dy);
            if (ce.y2 == sim.dragGridY)
                ce.movePoint(1, 0, dy);
        }
        sim.removeZeroLengthElements();
    }

    void dragColumn(int x, int y) {
        int dx = x - sim.dragGridX;
        if (dx == 0)
            return;
        int i;
        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce.x == sim.dragGridX)
                ce.movePoint(0, dx, 0);
            if (ce.x2 == sim.dragGridX)
                ce.movePoint(1, dx, 0);
        }
        sim.removeZeroLengthElements();
    }

    boolean onlyGraphicsElmsSelected() {
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

    boolean dragSelected(int x, int y) {
        boolean me = false;
        int i;
        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (mouseElm != null && !mouseElm.isSelected())
            mouseElm.setSelected(me = true);

        if (!onlyGraphicsElmsSelected()) {
            x = sim.snapGrid(x);
            y = sim.snapGrid(y);
        }

        int dx = x - sim.dragGridX;
        int dy = y - sim.dragGridY;
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

    void dragPost(int x, int y, boolean all) {
        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (mouseElm == null)
            return;
        if (sim.draggingPost == -1) {
            sim.draggingPost =
                (Graphics.distanceSq(mouseElm.x, mouseElm.y, x, y) >
                 Graphics.distanceSq(mouseElm.x2, mouseElm.y2, x, y)) ? 1 : 0;
        }
        int dx = x - sim.dragGridX;
        int dy = y - sim.dragGridY;
        if (dx == 0 && dy == 0)
            return;

        if (all) {
            int i;
            for (i = 0; i != sim.elmList.size(); i++) {
                CircuitElm e = sim.elmList.get(i);

                int p = 0;
                if (e.x == sim.dragGridX && e.y == sim.dragGridY)
                    p = 0;
                else if (e.x2 == sim.dragGridX && e.y2 == sim.dragGridY)
                    p = 1;
                else
                    continue;
                e.movePoint(p, dx, dy);
            }
        } else
            mouseElm.movePoint(sim.draggingPost, dx, dy);
        sim.needAnalyze();
    }

    void doFlip() {
        sim.menuElm.flipPosts();
        sim.needAnalyze();
    }

    void selectArea(int x, int y, boolean add) {
        int x1 = sim.min(x, sim.initDragGridX);
        int x2 = sim.max(x, sim.initDragGridX);
        int y1 = sim.min(y, sim.initDragGridY);
        int y2 = sim.max(y, sim.initDragGridY);
        sim.selectedArea = new Rectangle(x1, y1, x2 - x1, y2 - y1);
        int i;
        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            ce.selectRect(sim.selectedArea, add);
        }
        sim.enableDisableMenuItems();
    }

    boolean mouseIsOverSplitter(int x, int y) {
        boolean isOverSplitter;
        if (sim.scopeCount == 0)
            return false;
        isOverSplitter = ((x >= 0) && (x < sim.circuitArea.width) &&
                          (y >= sim.circuitArea.height - 5) && (y < sim.circuitArea.height));
        if (isOverSplitter != sim.mouseWasOverSplitter) {
            if (isOverSplitter)
                setCursorStyle("cursorSplitter");
            else
                setMouseMode(sim.mouseMode);
        }
        sim.mouseWasOverSplitter = isOverSplitter;
        return isOverSplitter;
    }

    void updateActionTimeElmIconHover(int gx, int gy) {
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
        sim.mouseCursorX = e.getX();
        sim.mouseCursorY = e.getY();
        if (sim.isMouseDraggingForRouting()) {
            mouseDragged(e);
            return;
        }
        mouseSelect(e);
        sim.scopeMenuSelected = -1;
    }

    void mouseSelect(MouseEvent<?> e) {
        CircuitElm newMouseElm = null;
        sim.mouseCursorX = e.getX();
        sim.mouseCursorY = e.getY();
        int sx = e.getX();
        int sy = e.getY();
        int gx = sim.inverseTransformX(sx);
        int gy = sim.inverseTransformY(sy);
        sim.dragGridX = sim.snapGrid(gx);
        sim.dragGridY = sim.snapGrid(gy);
        sim.dragScreenX = sx;
        sim.dragScreenY = sy;
        sim.draggingPost = -1;
        int i;

        sim.mousePost = -1;
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
        sim.scopeSelected = -1;
        if (newMouseElm == null) {
            for (i = 0; i != sim.scopeCount; i++) {
                Scope s = sim.scopes[i];
                if (s.rect.contains(sx, sy)) {
                    newMouseElm = s.getElm();
                    if (s.plotXY) {
                        sim.plotXElm = s.getXElm();
                        sim.plotYElm = s.getYElm();
                    }
                    sim.scopeSelected = i;
                }
            }
            for (i = 0; i != sim.elmList.size(); i++) {
                CircuitElm ce = sim.getElm(i);
                if (sim.mouseMode == CirSim.MODE_DRAG_POST) {
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
                        sim.mousePost = j;
                        break;
                    }
                }
            }
        } else {
            sim.mousePost = -1;
            for (i = 0; i != newMouseElm.getPostCount(); i++) {
                Point pt = newMouseElm.getPost(i);
                if (Graphics.distanceSq(pt.x, pt.y, gx, gy) < 26)
                    sim.mousePost = i;
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
            sim.menuClientX = e.getNativeEvent().getClientX();
            sim.menuClientY = e.getNativeEvent().getClientY();
            doPopupMenu();
        }
    }

    void doPopupMenu() {
        if (sim.noEditCheckItem.getState() || sim.dialogIsShowing())
            return;
        sim.menuElm = sim.getMouseElmForRouting();
        sim.menuScope = -1;
        sim.menuPlot = -1;
        int x, y;
        if (sim.scopeSelected != -1) {
            if (sim.scopes[sim.scopeSelected].canMenu()) {
                sim.menuScope = sim.scopeSelected;
                sim.menuPlot = sim.scopes[sim.scopeSelected].selectedPlot;
                sim.scopePopupMenu.doScopePopupChecks(false, sim.getScopeManager().canStackScope(sim.scopeSelected), sim.getScopeManager().canCombineScope(sim.scopeSelected),
                                                      sim.getScopeManager().canUnstackScope(sim.scopeSelected), sim.scopes[sim.scopeSelected]);
                sim.contextPanel = new PopupPanel(true);
                sim.contextPanel.add(sim.scopePopupMenu.getMenuBar());
                y = Math.max(0, Math.min(sim.menuClientY, sim.canvasHeight - 160));
                sim.contextPanel.setPopupPosition(sim.menuClientX, y);
                sim.contextPanel.show();
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
                    sim.getMenuBuilder().composeSelectScopeMenu(sim.selectScopeMenuBar);
                    sim.elmAddScopeMenuItem.setCommand(null);
                    sim.elmAddScopeMenuItem.setSubMenu(sim.selectScopeMenuBar);
                    sim.elmAddScopeMenuItem.setEnabled(mouseElm.canViewInScope());
                }
                sim.elmEditMenuItem.setEnabled(mouseElm.getEditInfo(0) != null);
                sim.elmSwapMenuItem.setEnabled(mouseElm.getPostCount() == 2);
                sim.elmSplitMenuItem.setEnabled(canSplit(mouseElm));
                sim.elmSliderMenuItem.setEnabled(sliderItemEnabled(mouseElm));
                sim.elmSankeyMenuItem.setEnabled(mouseElm instanceof SFCTableElm);
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
                sim.contextPanel = new PopupPanel(true);
                sim.contextPanel.add(sim.elmMenuBar);
                sim.contextPanel.setPopupPosition(sim.menuClientX, sim.menuClientY);
                sim.contextPanel.show();
            } else {
                ScopeElm s = (ScopeElm) mouseElm;
                if (s.elmScope.canMenu()) {
                    sim.menuPlot = s.elmScope.selectedPlot;
                    sim.scopePopupMenu.doScopePopupChecks(true, false, false, false, s.elmScope);
                    sim.contextPanel = new PopupPanel(true);
                    sim.contextPanel.add(sim.scopePopupMenu.getMenuBar());
                    sim.contextPanel.setPopupPosition(sim.menuClientX, sim.menuClientY);
                    sim.contextPanel.show();
                }
            }
        } else {
            doMainMenuChecks();
            sim.contextPanel = new PopupPanel(true);
            sim.contextPanel.add(sim.mainMenuBar);
            x = Math.max(0, Math.min(sim.menuClientX, sim.canvasWidth - 400));
            y = Math.max(0, Math.min(sim.menuClientY, sim.canvasHeight - 450));
            sim.contextPanel.setPopupPosition(x, y);
            sim.contextPanel.show();
        }
    }

    boolean canSplit(CircuitElm ce) {
        if (!(ce instanceof WireElm))
            return false;
        WireElm we = (WireElm) ce;
        if (we.x == we.x2 || we.y == we.y2)
            return true;
        return false;
    }

    boolean sliderItemEnabled(CircuitElm elm) {
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
        sim.tempMouseMode = CirSim.MODE_DRAG_ALL;
        sim.dragScreenX = x;
        sim.dragScreenY = y;
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
        sim.mouseCursorX = -1;
    }

    void clearMouseElm() {
        sim.scopeSelected = -1;
        sim.setMouseElm(null);
        sim.plotXElm = sim.plotYElm = null;
    }

    @Override
    public void onMouseDown(MouseDownEvent e) {
        e.preventDefault();

        sim.cv.setFocus(true);

        sim.stopElm = null;
        sim.menuX = sim.menuClientX = e.getX();
        sim.menuY = sim.menuClientY = e.getY();
        sim.mouseDownTime = System.currentTimeMillis();

        sim.enablePaste();

        if (e.getNativeButton() != com.google.gwt.dom.client.NativeEvent.BUTTON_LEFT && e.getNativeButton() != com.google.gwt.dom.client.NativeEvent.BUTTON_MIDDLE)
            return;

        mouseSelect(e);

        sim.setMouseDraggingForRouting(true);
        sim.didSwitch = false;

        sim.simRunningBeforeDrag = sim.simRunning;

        if (sim.getScopeManager().mouseIsOverScopeMinMaxButton(e.getX(), e.getY())) {
            sim.toggleScopePanelSize();
            sim.setMouseDraggingForRouting(false);
            return;
        }

        if (sim.mouseWasOverSplitter) {
            sim.tempMouseMode = CirSim.MODE_DRAG_SPLITTER;
            return;
        }
        if (e.getNativeButton() == com.google.gwt.dom.client.NativeEvent.BUTTON_LEFT) {
            sim.tempMouseMode = sim.mouseMode;
            if (e.isAltKeyDown() && e.isMetaKeyDown())
                sim.tempMouseMode = CirSim.MODE_DRAG_COLUMN;
            else if (e.isAltKeyDown() && e.isShiftKeyDown())
                sim.tempMouseMode = CirSim.MODE_DRAG_ROW;
            else if (e.isShiftKeyDown())
                sim.tempMouseMode = CirSim.MODE_SELECT;
            else if (e.isAltKeyDown())
                sim.tempMouseMode = CirSim.MODE_DRAG_ALL;
            else if (e.isControlKeyDown() || e.isMetaKeyDown())
                sim.tempMouseMode = CirSim.MODE_DRAG_POST;
        } else
            sim.tempMouseMode = CirSim.MODE_DRAG_ALL;


        if (sim.noEditCheckItem.getState())
            sim.tempMouseMode = CirSim.MODE_SELECT;

        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (!(sim.dialogIsShowing()) && ((sim.scopeSelected != -1 && sim.scopes[sim.scopeSelected].cursorInSettingsWheel()) ||
                                         (sim.scopeSelected == -1 && mouseElm instanceof ScopeElm && ((ScopeElm) mouseElm).elmScope.cursorInSettingsWheel()))) {
            if (sim.noEditCheckItem.getState())
                return;
            Scope s;
            if (sim.scopeSelected != -1)
                s = sim.scopes[sim.scopeSelected];
            else
                s = ((ScopeElm) mouseElm).elmScope;
            s.properties();
            sim.clearSelection();
            sim.setMouseDraggingForRouting(false);
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
                    sim.setMouseDraggingForRouting(false);
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

        if (sim.tempMouseMode == CirSim.MODE_SELECT && mouseElm != null && !sim.noEditCheckItem.getState() &&
            mouseElm.getHandleGrabbedClose(gx, gy, CirSim.POSTGRABSQ, CirSim.MINPOSTGRABSIZE) >= 0 &&
            !sim.anySelectedButMouse())
            sim.tempMouseMode = CirSim.MODE_DRAG_POST;

        if (sim.tempMouseMode != CirSim.MODE_SELECT && sim.tempMouseMode != CirSim.MODE_DRAG_SELECTED)
            sim.clearSelection();

        sim.pushUndo();
        sim.initDragGridX = gx;
        sim.initDragGridY = gy;
        sim.dragging = true;

        if (sim.simRunning) {
            sim.simRunning = false;
        }

        if (sim.tempMouseMode != CirSim.MODE_ADD_ELM)
            return;

        int x0 = sim.snapGrid(gx);
        int y0 = sim.snapGrid(gy);
        if (!sim.circuitArea.contains(e.getX(), e.getY()))
            return;

        try {
            sim.dragElm = CirSim.constructElement(sim.mouseModeStr, x0, y0);
        } catch (Exception ex) {
            CirSim.debugger();
        }
    }

    void doMainMenuChecks() {
        int c = sim.mainMenuItems.size();
        int i;
        for (i = 0; i < c; i++) {
            String s = sim.mainMenuItemNames.get(i);
            sim.mainMenuItems.get(i).setState(s == sim.mouseModeStr);
        }
        sim.stackAllItem.setEnabled(sim.scopeCount > 1 && sim.scopes[sim.scopeCount - 1].position > 0);
        sim.unstackAllItem.setEnabled(sim.scopeCount > 1 && sim.scopes[sim.scopeCount - 1].position != sim.scopeCount - 1);
        sim.combineAllItem.setEnabled(sim.scopeCount > 1);
        sim.separateAllItem.setEnabled(sim.scopeCount > 0);

        if (CirSim.lastSubcircuitMenuUpdate != CustomCompositeModel.sequenceNumber)
            sim.composeSubcircuitMenu();
    }

    @Override
    public void onMouseUp(MouseUpEvent e) {
        e.preventDefault();
        sim.setMouseDraggingForRouting(false);

        if (sim.tempMouseMode == CirSim.MODE_SELECT && sim.selectedArea == null)
            sim.clearSelection();

        if (sim.tempMouseMode == CirSim.MODE_DRAG_POST && sim.draggingPost == -1)
            sim.doSplit(sim.getMouseElmForRouting());

        sim.tempMouseMode = sim.mouseMode;
        sim.selectedArea = null;

        if (sim.dragging && sim.simRunningBeforeDrag && !sim.simRunning) {
            sim.simRunning = true;
        }

        sim.dragging = false;
        sim.setMouseDraggingForRouting(false);

        boolean circuitChanged = false;
        if (sim.heldSwitchElm != null) {
            sim.heldSwitchElm.mouseUp();
            sim.heldSwitchElm = null;
            circuitChanged = true;
        }
        if (sim.dragElm != null) {
            if (sim.dragElm.creationFailed()) {
                sim.dragElm.delete();
                if (sim.mouseMode == CirSim.MODE_SELECT || sim.mouseMode == CirSim.MODE_DRAG_SELECTED)
                    sim.clearSelection();
            }
            else {
                sim.elmList.addElement(sim.dragElm);
                sim.dragElm.draggingDone();
                circuitChanged = true;
                sim.needsRecoverySave = true;
                sim.unsavedChanges = true;
                if (sim.mouseMode == CirSim.MODE_ADD_ELM) {
                    setMouseMode(CirSim.MODE_SELECT);
                    sim.tempMouseMode = CirSim.MODE_SELECT;
                }
            }
            sim.dragElm = null;
        }
        if (circuitChanged) {
            sim.needAnalyze();
            sim.pushUndo();
        }
        if (sim.needsRecoverySave) {
            sim.writeRecoveryToStorage();
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

        boolean zoomOnly = System.currentTimeMillis() < sim.zoomTime + 1000;

        if (!sim.mouseWheelEditCheckItem.getState())
            zoomOnly = true;

        if (!zoomOnly)
            sim.scrollValues(e.getNativeEvent().getClientX(), e.getNativeEvent().getClientY(), wheelDelta);

        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (mouseElm instanceof com.google.gwt.event.dom.client.MouseWheelHandler && !zoomOnly)
            ((com.google.gwt.event.dom.client.MouseWheelHandler) mouseElm).onMouseWheel(e);
        else if (sim.scopeSelected != -1 && !zoomOnly)
            sim.scopes[sim.scopeSelected].onMouseWheel(e);
        else if (!sim.dialogIsShowing()) {
            sim.mouseCursorX = e.getX();
            sim.mouseCursorY = e.getY();
            sim.getViewportController().zoomCircuit(-wheelDelta * sim.wheelSensitivity, false);
            sim.zoomTime = System.currentTimeMillis();
        }
        sim.repaint();
    }

    @Override
    public void onPreviewNativeEvent(NativePreviewEvent e) {
        int cc = e.getNativeEvent().getCharCode();
        int t = e.getTypeInt();
        int code = e.getNativeEvent().getKeyCode();
        if (sim.dialogIsShowing()) {
            if (CirSim.scrollValuePopup != null && CirSim.scrollValuePopup.isShowing() &&
                (t & Event.ONKEYDOWN) != 0) {
                if (code == KEY_ESCAPE || code == KEY_SPACE)
                    CirSim.scrollValuePopup.close(false);
                if (code == KEY_ENTER)
                    CirSim.scrollValuePopup.close(true);
            }

            Dialog dlg = CirSim.editDialog;
            if (CirSim.diodeModelEditDialog != null)
                dlg = CirSim.diodeModelEditDialog;
            if (CirSim.customLogicEditDialog != null)
                dlg = CirSim.customLogicEditDialog;
            if (CirSim.dialogShowing != null)
                dlg = CirSim.dialogShowing;


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
                if (sim.scopeSelected != -1) {
                    sim.scopes[sim.scopeSelected].setElm(null);
                    sim.scopeSelected = -1;
                } else {
                    sim.menuElm = null;
                    sim.pushUndo();
                    sim.doDelete(true);
                    e.cancel();
                }
            }
            if (code == KEY_ESCAPE) {
                setMouseMode(CirSim.MODE_SELECT);
                sim.mouseModeStr = "Select";
                sim.updateToolbar();
                sim.tempMouseMode = sim.mouseMode;
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
                if (code == KEY_N && CirSim.isElectron()) {
                    sim.getCommandRouter().menuPerformed("key", "newwindow");
                    e.cancel();
                }
                if (code == KEY_S) {
                    String cmd = "exportaslocalfile";
                    if (CirSim.isElectron())
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
                sim.mouseModeStr = c;
                sim.updateToolbar();
                sim.tempMouseMode = sim.mouseMode;
            }
            if (cc == 32) {
                setMouseMode(CirSim.MODE_SELECT);
                sim.mouseModeStr = "Select";
                sim.updateToolbar();
                sim.tempMouseMode = sim.mouseMode;
                e.cancel();
            }
        }
    }

    void setMouseMode(int mode) {
        sim.mouseMode = mode;
        if (mode == CirSim.MODE_ADD_ELM) {
            setCursorStyle("cursorCross");
        } else {
            setCursorStyle("cursorPointer");
        }
    }

    void setCursorStyle(String s) {
        if (sim.lastCursorStyle != null)
            sim.cv.removeStyleName(sim.lastCursorStyle);
        sim.cv.addStyleName(s);
        sim.lastCursorStyle = s;
    }
}