package com.lushprojects.circuitjs1.client;

import com.google.gwt.dom.client.Document;
import com.google.gwt.user.client.Window;
import com.lushprojects.circuitjs1.client.util.Locale;

final class CirSimCommandRouter {
    private final CirSim sim;

    CirSimCommandRouter(CirSim sim) {
        this.sim = sim;
    }

    void menuPerformed(String menu, String item) {
        if ((menu=="edit" || menu=="main" || menu=="scopes") && sim.noEditCheckItem.getState()) {
            sim.alertOrWarn(Locale.LS("Editing disabled.  Re-enable from the Options menu."));
            return;
        }
        if (item=="about")
            CirSimDialogCoordinator.setAboutBox(new AboutBox(circuitjs1.versionString));
        if (item=="importfromlocalfile") {
            sim.getUndoRedoManager().pushUndo();
            if (sim.getPlatformInterop().isElectron())
                sim.getPlatformInterop().electronOpenFile();
            else
                sim.loadFileInput.click();
        }
        if (item=="newwindow") {
            Window.open(Document.get().getURL(), "_blank", "");
        }
        if (item=="save")
            sim.getPlatformInterop().electronSave(sim.getCircuitIOService().dumpCircuit());
        if (item=="saveas")
            sim.getPlatformInterop().electronSaveAs(sim.getCircuitIOService().dumpCircuit());
        if (item=="importfromtext") {
            CirSimDialogCoordinator.setDialogShowing(new ImportFromTextDialog(sim));
        }
        if (item=="importfromdropbox") {
            CirSimDialogCoordinator.setDialogShowing(new ImportFromDropboxDialog(sim));
        }
        if (item=="exportasurl") {
            sim.getCircuitIOService().doExportAsUrl();
            sim.unsavedChanges = false;
        }
        if (item=="openrunnertable") {
            sim.getCircuitIOService().doOpenRunnerOutputTable();
        }
        if (item=="exportaslocalfile") {
            sim.getCircuitIOService().doExportAsLocalFile();
            sim.unsavedChanges = false;
        }
        if (item=="exportastext") {
            sim.getCircuitIOService().doExportAsText();
            sim.unsavedChanges = false;
        }
        if (item=="exportassfcr") {
            sim.getCircuitIOService().doExportAsSFCR();
            sim.unsavedChanges = false;
        }
        if (item=="viewmodelinfo") {
            sim.getInfoDialogActions().doViewModelInfo();
        }
        if (item=="editlookuptables") {
            sim.getEditDialogActions().doEditLookupTables();
        }
        if (item=="exportasimage")
            sim.getEditDialogActions().doExportAsImage();
        if (item=="copypng") {
            sim.getEditDialogActions().doImageToClipboard();
            if (sim.contextPanel!=null)
                sim.contextPanel.hide();
        }
        if (item=="exportassvg")
            sim.getExportCompositeActions().doExportAsSVG();
        if (item=="createsubcircuit")
            sim.getEditDialogActions().doCreateSubcircuit();
        if (item=="dcanalysis")
            sim.getExportCompositeActions().doDCAnalysis();
        if (item=="print")
            sim.getExportCompositeActions().doPrint();
        if (item=="recover")
            sim.getUndoRedoManager().doRecover();

        if ((menu=="elm" || menu=="scopepop") && sim.contextPanel!=null)
            sim.contextPanel.hide();
        if (menu=="options" && item=="shortcuts") {
            CirSimDialogCoordinator.setDialogShowing(new ShortcutsDialog(sim));
            CirSimDialogCoordinator.getDialogShowing().show();
        }
        if (menu=="options" && item=="subcircuits") {
            CirSimDialogCoordinator.setDialogShowing(new SubcircuitDialog(sim));
            CirSimDialogCoordinator.getDialogShowing().show();
        }
        if (menu=="options" && item=="voltageunit") {
            sim.getPreferencesManager().showVoltageUnitDialog();
        }
        if (menu=="options" && item=="elementregistryreport") {
            sim.logElementRegistryInferenceReport();
        }
        if (menu=="options" && item=="toggleEdit") {
            sim.noEditCheckItem.setState(!sim.noEditCheckItem.getState());
        }
        if (item=="search") {
            CirSimDialogCoordinator.setDialogShowing(new SearchDialog(sim));
            CirSimDialogCoordinator.getDialogShowing().show();
        }
        if (item=="variablebrowser") {
            VariableBrowserDialog.openDialog(sim);
        }
        if (item=="hinteditor") {
            HintEditorDialog.openDialog(sim);
        }
        if (item=="actiontimedialog") {
            ActionTimeDialog.openDialog(sim);
        }
        if (item=="mathtestdialog") {
            sim.getInfoDialogActions().openMathTestDialog();
        }
        if (item=="tabletestdialog") {
            sim.getInfoDialogActions().openTableTestDialog();
        }
        if (item=="iframeviewer") {
            sim.getInfoDialogActions().openIframeViewer();
        }
        if (item=="referencedocs") {
            sim.getInfoDialogActions().openReferenceDocsViewer();
        }
        if (menu=="options" && item=="other")
            sim.getEditDialogActions().doEdit(new EditOptions(sim));
        if (item=="devtools")
            sim.getPlatformInterop().toggleDevTools();
        if (item=="undo")
            sim.getUndoRedoManager().doUndo();
        if (item=="redo")
            sim.getUndoRedoManager().doRedo();

        if (menu == "key" && sim.getMouseElmForRouting() != null) {
            sim.menuElm = sim.getMouseElmForRouting();
            menu = "elm";
        }
        if (menu != "elm")
            sim.menuElm = null;

        if (item == "cut") {
            sim.getClipboardManager().doCut();
        }
        if (item == "copy") {
            sim.getClipboardManager().doCopy();
        }
        if (item=="paste")
            sim.getClipboardManager().doPaste(null);
        if (item=="duplicate") {
            sim.getClipboardManager().doDuplicate();
        }
        if (item=="flip")
            sim.getMouseInputHandler().doFlip();
        if (item=="split")
            sim.doSplit(sim.menuElm);
        if (item=="selectAll")
            sim.getClipboardManager().doSelectAll();

        if (item=="centrecircuit") {
            sim.getUndoRedoManager().pushUndo();
            sim.getViewportController().centreCircuit();
        }
        if (item=="zoomToViewport") {
            ViewportElm viewport = sim.getViewportController().findViewportElm();
            if (viewport != null) {
                sim.getUndoRedoManager().pushUndo();
                sim.getViewportController().applyViewportTransform(viewport);
            } else {
                sim.getMouseInputHandler().setMouseMode(CirSim.MODE_ADD_ELM);
                sim.mouseModeStr = "ViewportElm";
                sim.toolbar.setModeLabel("Viewport");
                sim.toolbar.highlightButton(sim.mouseModeStr);
            }
        }
        if (item=="flipx") {
            sim.getUndoRedoManager().pushUndo();
            sim.getFlipTransformController().flipX();
        }
        if (item=="flipy") {
            sim.getUndoRedoManager().pushUndo();
            sim.getFlipTransformController().flipY();
        }
        if (item=="flipxy") {
            sim.getUndoRedoManager().pushUndo();
            sim.getFlipTransformController().flipXY();
        }
        if (item=="stackAll")
            sim.getScopeManager().stackAll();
        if (item=="unstackAll")
            sim.getScopeManager().unstackAll();
        if (item=="combineAll")
            sim.getScopeManager().combineAll();
        if (item=="separateAll")
            sim.getScopeManager().separateAll();
        if (item=="viewAllPlotly")
            new ScopeViewerDialog(sim, null, true);
        if (item=="zoomin")
            sim.getViewportController().zoomCircuit(20, true);
        if (item=="zoomout")
            sim.getViewportController().zoomCircuit(-20, true);
        if (item=="zoom100")
            sim.getViewportController().setCircuitScale(1, true);
        if (menu=="elm" && item=="edit")
            sim.getEditDialogActions().doEdit(sim.menuElm);
        if (item=="delete") {
            if (menu!="elm")
                sim.menuElm = null;
            sim.getUndoRedoManager().pushUndo();
            sim.getClipboardManager().doDelete(true);
        }
        if (item=="sliders")
            sim.getEditDialogActions().doSliders(sim.menuElm);

        if (item=="viewSankey" && (sim.menuElm instanceof SFCTableElm)) {
            SFCSankeyViewer viewer = new SFCSankeyViewer((TableElm) sim.menuElm);
            viewer.openDialog();
        }

        if (item=="viewDagBlocks" && (sim.menuElm instanceof EquationTableElm)) {
            SFCRDagBlocksViewer viewer = new SFCRDagBlocksViewer(sim);
            viewer.openExternalWindow();
        }

        if (item=="viewEquationTableDebug" && (sim.menuElm instanceof EquationTableElm)) {
            new EquationTableMarkdownDebugDialog((EquationTableElm) sim.menuElm).show();
        }

        if (item=="viewEquationTableReference" && (sim.menuElm instanceof EquationTableElm)) {
            ReferenceDocs.openMarkdownReference(
                "EquationTable Reference",
                "docs/reference/EquationTableReference.md");
        }

        if (item=="viewInScope" && sim.menuElm != null) {
            int i;
            for (i = 0; i != sim.scopeCount; i++)
                if (sim.scopes[i].getElm() == null)
                    break;
            if (i == sim.scopeCount) {
                if (sim.scopeCount == sim.scopes.length)
                    return;
                sim.scopeCount++;
                sim.scopes[i] = new Scope(sim);
                sim.scopes[i].position = i;
            }
            sim.scopes[i].setElm(sim.menuElm);
            if (i > 0)
                sim.scopes[i].speed = sim.scopes[i-1].speed;
        }

        if (item=="viewInFloatScope" && sim.menuElm != null) {
            ScopeElm newScope = new ScopeElm(sim.snapGrid(sim.menuElm.x+50), sim.snapGrid(sim.menuElm.y+50));
            sim.elmList.addElement(newScope);
            newScope.setScopeElm(sim.menuElm);
            sim.needAnalyze();
        }

        if (item.startsWith("addToScope") && sim.menuElm != null) {
            int n;
            n = Integer.parseInt(item.substring(10));
            if (n < sim.scopeCount + sim.getScopeManager().countScopeElms()) {
                if (n < sim.scopeCount )
                    sim.scopes[n].addElm(sim.menuElm);
                else
                    sim.getScopeManager().getNthScopeElm(n-sim.scopeCount).elmScope.addElm(sim.menuElm);
            }
            sim.scopeMenuSelected = -1;
        }

        if (menu=="scopepop") {
            sim.getUndoRedoManager().pushUndo();
            Scope s;
            if (sim.menuScope != -1 )
                s= sim.scopes[sim.menuScope];
            else
                s= ((ScopeElm)sim.getMouseElmForRouting()).elmScope;

            if (item=="dock") {
                if (sim.scopeCount == sim.scopes.length)
                    return;
                sim.scopes[sim.scopeCount] = ((ScopeElm)sim.getMouseElmForRouting()).elmScope;
                ((ScopeElm)sim.getMouseElmForRouting()).clearElmScope();
                sim.scopes[sim.scopeCount].position = sim.scopeCount;
                sim.scopeCount++;
                sim.getClipboardManager().doDelete(false);
            }
            if (item=="undock") {
                CircuitElm elm = s.getElm();
                ScopeElm newScope = new ScopeElm(sim.snapGrid(elm.x+50), sim.snapGrid(elm.y+50));
                sim.elmList.addElement(newScope);
                newScope.setElmScope(sim.scopes[sim.menuScope]);

                int i;
                for (i = sim.menuScope; i < sim.scopeCount; i++)
                    sim.scopes[i] = sim.scopes[i+1];
                sim.scopeCount--;

                sim.needAnalyze();
            }
            if (item=="remove")
                s.setElm(null);
            if (item=="removeplot")
                s.removePlot(sim.menuPlot);
            if (item=="speed2")
                s.speedUp();
            if (item=="speed1/2")
                s.slowDown();
            if (item=="maxscale")
                s.maxScale();
            if (item=="stack")
                sim.getScopeManager().stackScope(sim.menuScope);
            if (item=="unstack")
                sim.getScopeManager().unstackScope(sim.menuScope);
            if (item=="combine")
                sim.getScopeManager().combineScope(sim.menuScope);
            if (item=="selecty")
                s.selectY();
            if (item=="reset")
                s.resetGraph(true);
            if (item=="properties")
                s.properties();
            if (item=="exportdata")
                new ExportScopeDataDialog(s);
            if (item=="viewplotly")
                new ScopeViewerDialog(sim, s, true);
            if (item=="drawfromzero")
                s.toggleDrawFromZero();
            sim.getScopeManager().deleteUnusedScopeElms();
        }
        if (menu=="circuits" && item.indexOf("setup ") ==0) {
            sim.getUndoRedoManager().pushUndo();
            int sp = item.indexOf(' ', 6);
            sim.getCircuitIOService().readSetupFile(item.substring(6, sp), item.substring(sp+1));
        }
        if (item=="newblankcircuit") {
            sim.getUndoRedoManager().pushUndo();
            sim.getCircuitIOService().readSetupFile("electronics/blank.txt", "Blank Circuit");
        }

        if (menu=="main") {
            if (sim.contextPanel!=null)
                sim.contextPanel.hide();
            sim.getMouseInputHandler().setMouseMode(CirSim.MODE_ADD_ELM);
            String s = item;
            if (s.length() > 0)
                sim.mouseModeStr = s;
            if (s.compareTo("DragAll") == 0)
                sim.getMouseInputHandler().setMouseMode(CirSim.MODE_DRAG_ALL);
            else if (s.compareTo("DragRow") == 0)
                sim.getMouseInputHandler().setMouseMode(CirSim.MODE_DRAG_ROW);
            else if (s.compareTo("DragColumn") == 0)
                sim.getMouseInputHandler().setMouseMode(CirSim.MODE_DRAG_COLUMN);
            else if (s.compareTo("DragSelected") == 0)
                sim.getMouseInputHandler().setMouseMode(CirSim.MODE_DRAG_SELECTED);
            else if (s.compareTo("DragPost") == 0)
                sim.getMouseInputHandler().setMouseMode(CirSim.MODE_DRAG_POST);
            else if (s.compareTo("Select") == 0)
                sim.getMouseInputHandler().setMouseMode(CirSim.MODE_SELECT);

            sim.updateToolbar();
            sim.tempMouseMode = sim.mouseMode;
        }
        if (item=="fullscreen") {
            if (! Graphics.isFullScreen)
                Graphics.viewFullScreen();
            else
                Graphics.exitFullScreen();
            sim.getViewportController().centreCircuit();
        }

        sim.repaint();
    }
}
