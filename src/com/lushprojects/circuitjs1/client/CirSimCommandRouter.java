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
            sim.aboutBox = new AboutBox(circuitjs1.versionString);
        if (item=="importfromlocalfile") {
            sim.pushUndo();
            if (sim.isElectron())
                sim.electronOpenFile();
            else
                sim.loadFileInput.click();
        }
        if (item=="newwindow") {
            Window.open(Document.get().getURL(), "_blank", "");
        }
        if (item=="save")
            sim.electronSave(sim.getCircuitIOService().dumpCircuit());
        if (item=="saveas")
            sim.electronSaveAs(sim.getCircuitIOService().dumpCircuit());
        if (item=="importfromtext") {
            sim.dialogShowing = new ImportFromTextDialog(sim);
        }
        if (item=="importfromdropbox") {
            sim.dialogShowing = new ImportFromDropboxDialog(sim);
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
            sim.doRecover();

        if ((menu=="elm" || menu=="scopepop") && sim.contextPanel!=null)
            sim.contextPanel.hide();
        if (menu=="options" && item=="shortcuts") {
            sim.dialogShowing = new ShortcutsDialog(sim);
            sim.dialogShowing.show();
        }
        if (menu=="options" && item=="subcircuits") {
            sim.dialogShowing = new SubcircuitDialog(sim);
            sim.dialogShowing.show();
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
            sim.dialogShowing = new SearchDialog(sim);
            sim.dialogShowing.show();
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
            sim.openMathTestDialog();
        }
        if (item=="tabletestdialog") {
            sim.openTableTestDialog();
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
            CirSim.toggleDevTools();
        if (item=="undo")
            sim.doUndo();
        if (item=="redo")
            sim.doRedo();

        if (menu == "key" && sim.getMouseElmForRouting() != null) {
            sim.menuElm = sim.getMouseElmForRouting();
            menu = "elm";
        }
        if (menu != "elm")
            sim.menuElm = null;

        if (item == "cut") {
            sim.doCut();
        }
        if (item == "copy") {
            sim.doCopy();
        }
        if (item=="paste")
            sim.doPaste(null);
        if (item=="duplicate") {
            sim.doDuplicate();
        }
        if (item=="flip")
            sim.getMouseInputHandler().doFlip();
        if (item=="split")
            sim.doSplit(sim.menuElm);
        if (item=="selectAll")
            sim.doSelectAll();

        if (item=="centrecircuit") {
            sim.pushUndo();
            sim.getViewportController().centreCircuit();
        }
        if (item=="zoomToViewport") {
            ViewportElm viewport = sim.getViewportController().findViewportElm();
            if (viewport != null) {
                sim.pushUndo();
                sim.getViewportController().applyViewportTransform(viewport);
            } else {
                sim.getMouseInputHandler().setMouseMode(CirSim.MODE_ADD_ELM);
                sim.mouseModeStr = "ViewportElm";
                sim.toolbar.setModeLabel("Viewport");
                sim.toolbar.highlightButton(sim.mouseModeStr);
            }
        }
        if (item=="flipx") {
            sim.pushUndo();
            sim.flipX();
        }
        if (item=="flipy") {
            sim.pushUndo();
            sim.flipY();
        }
        if (item=="flipxy") {
            sim.pushUndo();
            sim.flipXY();
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
            sim.setCircuitScale(1, true);
        if (menu=="elm" && item=="edit")
            sim.getEditDialogActions().doEdit(sim.menuElm);
        if (item=="delete") {
            if (menu!="elm")
                sim.menuElm = null;
            sim.pushUndo();
            sim.doDelete(true);
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
            sim.pushUndo();
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
                sim.doDelete(false);
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
            sim.pushUndo();
            int sp = item.indexOf(' ', 6);
            sim.getCircuitIOService().readSetupFile(item.substring(6, sp), item.substring(sp+1));
        }
        if (item=="newblankcircuit") {
            sim.pushUndo();
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