package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.economics.*;

import com.google.gwt.canvas.dom.client.Context2d.LineCap;
import com.lushprojects.circuitjs1.client.ui.InfoViewerDialog;
import com.lushprojects.circuitjs1.client.util.PerfMonitor;

class CircuitRenderer {
    private final CirSim sim;

    CircuitRenderer(CirSim sim) {
        this.sim = sim;
    }

    boolean drawGraphicsIfNeeded(PerfMonitor perfmon, boolean didAnalyze, CircuitElm mouseElm) {
        CircuitElm.powerMult = Math.exp(sim.getPowerBarValueForRouting() / 4.762 - 7);

        sim.graphicsFrameCounter++;

        boolean shouldDrawGraphics = sim.isDragging() ||
                                     !sim.simRunning ||
                                     didAnalyze ||
                                     (sim.graphicsFrameCounter >= sim.graphicsUpdateInterval);

        if (shouldDrawGraphics) {
            sim.graphicsFrameCounter = 0;

            perfmon.startContext("graphics");

            Graphics g = new Graphics(sim.cvcontext);
            if (sim.printableCheckItem.getState()) {
                CircuitElm.whiteColor = Color.black;
                CircuitElm.lightGrayColor = Color.black;
                g.setColor(new Color(245, 245, 245));
                sim.cv.getElement().getStyle().setBackgroundColor("#f5f5f5");
            } else {
                CircuitElm.whiteColor = Color.white;
                CircuitElm.lightGrayColor = Color.lightGray;
                g.setColor(Color.black);
                sim.cv.getElement().getStyle().setBackgroundColor("#000");
            }

            g.fillRect(0, 0, sim.canvasWidth, sim.canvasHeight);

            g.setFont(CircuitElm.unitsFont);

            g.context.setLineCap(LineCap.ROUND);

            if (sim.noEditCheckItem.getState())
                g.drawLock(20, 30);

            g.setColor(Color.white);

            double scale = CirSim.devicePixelRatio();
            double[] transform = sim.getTransform();
            sim.cvcontext.setTransform(transform[0] * scale, 0, 0, transform[3] * scale, transform[4] * scale, transform[5] * scale);

            perfmon.startContext("elm.draw()");

            int tableOriginalIndex = -1;
            TableElm lastInteractedTable = sim.getLastInteractedTableForRouting();
            if (lastInteractedTable != null && sim.elmList.contains(lastInteractedTable)) {
                tableOriginalIndex = sim.elmList.indexOf(lastInteractedTable);
                if (tableOriginalIndex != sim.elmList.size() - 1) {
                    sim.elmList.remove(tableOriginalIndex);
                    sim.elmList.add(lastInteractedTable);
                }
            }

            if (sim.powerCheckItem.getState()) {
                g.setColor(Color.gray);
                for (int i = 0; i != sim.elmList.size(); i++) {
                    sim.getElm(i).draw(g);
                }
            } else {
                for (int i = 0; i != sim.elmList.size(); i++) {
                    sim.getElm(i).draw(g);
                }
            }

            if (tableOriginalIndex >= 0 && tableOriginalIndex != sim.elmList.size() - 1) {
                sim.elmList.remove(lastInteractedTable);
                sim.elmList.insertElementAt(lastInteractedTable, tableOriginalIndex);
            }

            perfmon.stopContext();

            if (sim.getMouseMode() != CirSim.MODE_DRAG_ROW && sim.getMouseMode() != CirSim.MODE_DRAG_COLUMN) {
                for (int i = 0; i != sim.postDrawList.size(); i++)
                    CircuitElm.drawPost(g, sim.postDrawList.get(i));
            }

            if (sim.getTempMouseMode() == CirSim.MODE_DRAG_ROW ||
                sim.getTempMouseMode() == CirSim.MODE_DRAG_COLUMN ||
                sim.getTempMouseMode() == CirSim.MODE_DRAG_POST ||
                sim.getTempMouseMode() == CirSim.MODE_DRAG_SELECTED) {
                for (int i = 0; i != sim.elmList.size(); i++) {
                    CircuitElm ce = sim.getElm(i);
                    if (ce != mouseElm || sim.getTempMouseMode() != CirSim.MODE_DRAG_POST) {
                        g.setColor(Color.gray);
                        g.fillOval(ce.x - 3, ce.y - 3, 7, 7);
                        g.fillOval(ce.x2 - 3, ce.y2 - 3, 7, 7);
                    } else {
                        ce.drawHandles(g, CircuitElm.selectColor);
                    }
                }
            }

            if (sim.getTempMouseMode() == CirSim.MODE_SELECT && mouseElm != null) {
                mouseElm.drawHandles(g, CircuitElm.selectColor);
            }

            if (sim.dragElm != null && (sim.dragElm.x != sim.dragElm.x2 || sim.dragElm.y != sim.dragElm.y2)) {
                sim.dragElm.draw(g);
                sim.dragElm.drawHandles(g, CircuitElm.selectColor);
            }

            for (int i = 0; i != sim.badConnectionList.size(); i++) {
                Point cn = sim.badConnectionList.get(i);
                g.setColor(Color.red);
                g.fillOval(cn.x - 3, cn.y - 3, 7, 7);
            }

            Rectangle selectedArea = sim.getSelectedArea();
            if (selectedArea != null) {
                g.setColor(CircuitElm.selectColor);
                g.drawRect(selectedArea.x, selectedArea.y, selectedArea.width, selectedArea.height);
            }

            if (sim.crossHairCheckItem.getState() && sim.getMouseCursorX() >= 0
                && sim.getMouseCursorX() <= sim.circuitArea.width && sim.getMouseCursorY() <= sim.circuitArea.height) {
                g.setColor(Color.gray);
                int x = sim.snapGrid(sim.inverseTransformX(sim.getMouseCursorX()));
                int y = sim.snapGrid(sim.inverseTransformY(sim.getMouseCursorY()));
                g.drawLine(x, sim.inverseTransformY(0), x, sim.inverseTransformY(sim.circuitArea.height));
                g.drawLine(sim.inverseTransformX(0), y, sim.inverseTransformX(sim.circuitArea.width), y);
            }

            sim.cvcontext.setTransform(scale, 0, 0, scale, 0, 0);

            perfmon.startContext("drawBottomArea()");
            sim.getStatusInfoRenderer().drawBottomArea(g);
            perfmon.stopContext();

            g.setColor(Color.white);

            perfmon.stopContext();
        }

        return shouldDrawGraphics;
    }

    void drawStatus(Graphics g, boolean shouldDrawGraphics, PerfMonitor perfmon, double iterCount) {
        g.setColor(CircuitElm.whiteColor);
        int height = 15;
        int increment = 15;

        String currentCircuitFile = sim.getSFCRDocumentManager().getCurrentCircuitFile();
        if (currentCircuitFile != null) {
            g.drawString("File: " + currentCircuitFile, 10, height);
            height += increment;
        }

        String timeStr = "t = " + sim.getPreferencesManager().formatTimeFixed(sim.getTime());
        double timerate = 160 * iterCount * sim.getTimeStep();
        if (timerate >= .1)
            timeStr += " (" + CircuitElm.showFormat.format(timerate) + "x)";
        g.drawString(timeStr, 10, height);

        double realElapsed = (System.currentTimeMillis() - sim.getTimingState().realTimeStart) / 1000.0;
        g.drawString("real = " + CircuitElm.showFormat.format(realElapsed) + "s", 10, height += increment);

        g.drawString("Framerate: " + CircuitElm.showFormat.format(sim.framerate), 10, height += increment);
        g.drawString("subiter: " + sim.getSubIterations(), 10, height += increment);

        String unresolvedMsg = sim.getCircuitValueSlotManager().getUnresolvedReferencesMessage();
        if (unresolvedMsg != null) {
            g.setColor(Color.red);
            g.drawString(unresolvedMsg, 10, height += increment);
            g.setColor(CircuitElm.whiteColor);
        }

        if (shouldDrawGraphics && sim.developerMode) {
            g.drawString("Steprate: " + CircuitElm.showFormat.format(sim.steprate), 10, height += increment);
            g.drawString("Steprate/iter: " + CircuitElm.showFormat.format(sim.steprate / iterCount), 10, height += increment);
            g.drawString("iterc: " + CircuitElm.showFormat.format(iterCount), 10, height += increment);

            g.drawString("Frames: " + sim.frames, 10, height += increment);

            height += (increment * 2);

            String perfmonResult = PerfMonitor.buildString(perfmon).toString();
            String[] splits = perfmonResult.split("\n");
            for (int x = 0; x < splits.length; x++) {
                g.drawString(splits[x], 10, height + (increment * x));
            }
        }
    }

    void finalizeFrame(Graphics g, CircuitElm mouseElm) {
        if (sim.stopElm != null && sim.stopElm != mouseElm)
            sim.stopElm.setMouseElm(false);

        sim.frames++;

        if (sim.dcAnalysisFlag) {
            sim.dcAnalysisFlag = false;
            sim.analyzeFlag = true;
        }

        sim.lastFrameTime = sim.lastTime;

        sim.getStatusInfoRenderer().drawHintTooltip(g);

        sim.getStatusInfoRenderer().drawActionSchedulerMessage(g, sim.cvcontext);

        if (RuntimeMode.isGwt())
            InfoViewerDialog.pushLiveDataUpdate();

        if (RuntimeMode.isGwt())
            sim.getJsApiBridge().callUpdateHook();
    }
}
