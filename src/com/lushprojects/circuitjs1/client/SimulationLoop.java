package com.lushprojects.circuitjs1.client;

import com.google.gwt.canvas.dom.client.Context2d.LineCap;
import com.google.gwt.core.client.GWT;
import com.lushprojects.circuitjs1.client.util.PerfMonitor;

class SimulationLoop {
    private final CirSim sim;
    private final CircuitRenderer circuitRenderer;

    SimulationLoop(CirSim sim) {
        this.sim = sim;
        this.circuitRenderer = sim.getCircuitRendererForRouting();
    }

    public void updateCircuit() {
        PerfMonitor perfmon = new PerfMonitor();
        perfmon.startContext("updateCircuit()");

        sim.checkCanvasSize();

        boolean didAnalyze = sim.analyzeFlag;
        if (sim.analyzeFlag || sim.dcAnalysisFlag) {
            perfmon.startContext("analyzeCircuit()");
            sim.analyzeCircuit();
            sim.analyzeFlag = false;
            perfmon.stopContext();
        }

        if (sim.needsStamp && sim.simRunning) {
            perfmon.startContext("stampCircuit()");
            try {
                sim.preStampAndStampCircuit();
            } catch (Exception e) {
                sim.stop("Exception in stampCircuit()", null);
                GWT.log("Exception in stampCircuit", e);
            }
            perfmon.stopContext();
        }

        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (sim.stopElm != null && sim.stopElm != mouseElm)
            sim.stopElm.setMouseElm(true);

        sim.setupScopes();

        Graphics g = new Graphics(sim.cvcontext);

        if (sim.simRunning) {
            if (sim.needsStamp)
                CirSim.console("needsStamp while simRunning?");

            perfmon.startContext("runCircuit()");
            try {
                runCircuit(didAnalyze);
            } catch (Exception e) {
                CirSim.debugger();
                CirSim.console("exception in runCircuit " + e);
                e.printStackTrace();
            }
            perfmon.stopContext();
        }

        long sysTime = System.currentTimeMillis();
        if (sim.simRunning) {
            if (sim.lastTime != 0) {
                int inc = (int) (sysTime - sim.lastTime);
                double c = sim.getCurrentBarValueForRouting();
                c = java.lang.Math.exp(c / 3.5 - 14.2);
                CircuitElm.currentMult = 1.7 * inc * c;
                if (!sim.conventionCheckItem.getState())
                    CircuitElm.currentMult = -CircuitElm.currentMult;
            }
            sim.lastTime = sysTime;
        } else {
            sim.lastTime = 0;
        }

        if (sysTime - sim.secTime >= 1000) {
            sim.framerate = sim.frames;
            sim.steprate = sim.steps;
            sim.frames = 0;
            sim.steps = 0;
            sim.secTime = sysTime;
        }
        if (sysTime - sim.secTime >= 500) {
            for (int i = 0; i != sim.elmList.size(); i++) {
                sim.getElm(i).every500msec();
            }
        }

        boolean shouldDrawGraphics = circuitRenderer.drawGraphicsIfNeeded(perfmon, didAnalyze, mouseElm);

        perfmon.stopContext();

        double iterCount = getIterCount();
        circuitRenderer.drawStatus(g, shouldDrawGraphics, perfmon, iterCount);
        circuitRenderer.finalizeFrame(g, mouseElm);
    }

    double getIterCount() {
        if (RuntimeMode.isNonInteractiveRuntime())
            return 1.0;
        int val = sim.getSpeedBarValueForRouting();
        if (val == 0)
            return 0;

        return .1 * Math.exp((val - 61) / 24.);
    }

    boolean canDelayWireProcessing() {
        int i;
        for (i = 0; i != sim.scopeCount; i++)
            if (sim.scopes[i].viewingWire())
                return false;
        for (i = 0; i != sim.elmList.size(); i++)
            if (sim.getElm(i) instanceof ScopeElm && ((ScopeElm) sim.getElm(i)).elmScope.viewingWire())
                return false;
        return true;
    }

    void runCircuit(boolean didAnalyze) {
        if (sim.circuitMatrix == null || sim.elmList.size() == 0) {
            sim.circuitMatrix = null;
            return;
        }
        boolean nonInteractive = RuntimeMode.isNonInteractiveRuntime();

        boolean debugprint = sim.dumpMatrix;
        sim.dumpMatrix = false;
        long steprate = (long) (160 * getIterCount());
        long tm = System.currentTimeMillis();
        long lit = sim.lastIterTime;
        if (lit == 0) {
            sim.lastIterTime = tm;
            if (!nonInteractive)
                return;
        }

        if (!nonInteractive && 1000 >= steprate * (tm - sim.lastIterTime) && !didAnalyze)
            return;

        boolean delayWireProcessing = canDelayWireProcessing();

        int timeStepCountAtFrameStart = sim.timeStepCount;

        int goodIterations = 100;

        int frameTimeLimit = (int) (1000 / sim.minFrameRate);

        if (sim.timeStepCount >= sim.nextPeriodicTime) {
            for (int i = 0; i != sim.elmArr.length; i++) {
                sim.elmArr[i].nonConverged = false;
            }
            sim.nextPeriodicTime = sim.timeStepCount + sim.periodicInterval;
        }

        for (int iter = 1; ; iter++) {

            if (goodIterations >= 3 && sim.timeStep < sim.maxTimeStep) {
                sim.timeStep = Math.min(sim.timeStep * 2, sim.maxTimeStep);
                CirSim.console("timestep up = " + sim.timeStep + " at " + sim.t);
                sim.stampCircuit();
                goodIterations = 0;
            }

            int i, j, subiter;
            for (i = 0; i != sim.elmArr.length; i++)
                sim.elmArr[i].startIteration();

            ComputedValues.clearPendingValues();
            Expr.clearUnresolvedReferences();

            sim.steps++;

            int subiterCount = (sim.adjustTimeStep && sim.timeStep / 2 > sim.minTimeStep) ? 100 : 200;
            for (subiter = 0; subiter != subiterCount; subiter++) {
                sim.converged = true;
                sim.subIterations = subiter;

                for (i = 0; i != sim.circuitMatrixSize; i++)
                    sim.circuitRightSide[i] = sim.origRightSide[i];
                if (sim.circuitNonLinear) {
                    for (i = 0; i != sim.circuitMatrixSize; i++)
                        for (j = 0; j != sim.circuitMatrixSize; j++)
                            sim.circuitMatrix[i][j] = sim.origMatrix[i][j];
                }

                ComputedValues.resetComputedFlags();

                for (i = 0; i != sim.elmArr.length; i++) {
                    boolean preConverged = sim.converged;
                    sim.elmArr[i].doStep();

                    if (preConverged && !sim.converged) {
                        if (subiter > sim.convergenceCheckThreshold) {
                            sim.elmArr[i].nonConverged = true;
                            if (!(sim.elmArr[i] instanceof EquationTableElm)) {
                                String text = "CirSim: t=" + sim.t + " dt=" + sim.timeStep + " Element causing convergence failure: " +
                                              sim.elmArr[i].getClass().getSimpleName() + " at (" +
                                              sim.elmArr[i].x + "," + sim.elmArr[i].y + ")";
                                CirSim.console(text);
                            }
                        }
                    }
                }

                ComputedValues.commitPendingToCurrentValues();

                if (sim.stopMessage != null)
                    return;
                boolean printit = debugprint;
                debugprint = false;
                if (sim.circuitMatrixSize < 8) {
                    for (j = 0; j != sim.circuitMatrixSize; j++) {
                        for (i = 0; i != sim.circuitMatrixSize; i++) {
                            double x = sim.circuitMatrix[i][j];
                            if (Double.isNaN(x) || Double.isInfinite(x)) {
                                sim.stop("nan/infinite matrix!", null);
                                CirSim.console("circuitMatrix " + i + " " + j + " is " + x);
                                return;
                            }
                        }
                    }
                }
                if (printit) {
                    for (j = 0; j != sim.circuitMatrixSize; j++) {
                        String x = "";
                        for (i = 0; i != sim.circuitMatrixSize; i++)
                            x += sim.circuitMatrix[j][i] + ",";
                        x += "\n";
                        CirSim.console(x);
                    }
                    CirSim.console("done");
                }
                if (sim.circuitNonLinear) {
                    if (sim.converged && subiter > 0)
                        break;
                    int badRow = CirSim.lu_factor(sim.circuitMatrix, sim.circuitMatrixSize, sim.circuitPermute);
                    if (badRow >= 0) {
                        sim.stop("Singular matrix! " + sim.getMatrixRowInfo(badRow), null);
                        return;
                    }
                }
                CirSim.lu_solve(sim.circuitMatrix, sim.circuitMatrixSize, sim.circuitPermute,
                                sim.circuitRightSide);
                applySolvedRightSide(sim.circuitRightSide);
                if (!sim.circuitNonLinear) {
                    if (sim.circuitMatrixSize == 1) {
                        CirSim.console("[runCircuit] circuitNonLinear=false, exiting after first iteration");
                    }
                    break;
                }
            }
            if (subiter == subiterCount) {
                goodIterations = 0;
                if (sim.adjustTimeStep) {
                    sim.timeStep /= 2;
                    CirSim.console("timestep down to " + sim.timeStep + " at " + sim.t);
                }
                if (sim.timeStep < sim.minTimeStep || !sim.adjustTimeStep) {
                    CirSim.console("convergence failed after " + subiter + " iterations");
                    sim.stop("Convergence failed!", null);
                    break;
                }
                setNodeVoltages(sim.lastNodeVoltages);
                sim.stampCircuit();
                continue;
            }
            if (subiter > 20 || sim.timeStep < sim.maxTimeStep)
                CirSim.console("converged after " + subiter + " iterations, timeStep = " + sim.timeStep);
            if (subiter < 3)
                goodIterations++;
            else
                goodIterations = 0;
            sim.t += sim.timeStep;
            sim.timeStepAccum += sim.timeStep;
            if (sim.timeStepAccum >= sim.maxTimeStep) {
                sim.timeStepAccum -= sim.maxTimeStep;
                sim.timeStepCount++;
            }

            for (i = 0; i != sim.elmArr.length; i++)
                sim.elmArr[i].stepFinished();
            if (!delayWireProcessing)
                calcWireCurrents();

            ComputedValues.commitPendingToCurrentValues();
            sim.syncAllSlots();

            ComputedValues.commitConvergedValues();

            ActionScheduler scheduler = ActionScheduler.getInstance(sim);
            if (scheduler != null) {
                scheduler.stepFinished(sim.t);
            }

            for (i = 0; i != sim.scopeCount; i++)
                sim.scopes[i].timeStep();
            for (i = 0; i != sim.scopeElmArr.length; i++)
                sim.scopeElmArr[i].stepScope();
            if (RuntimeMode.isGwt())
                sim.callTimeStepHook();
            for (i = 0; i != sim.lastNodeVoltages.length; i++)
                sim.lastNodeVoltages[i] = sim.nodeVoltages[i];

            tm = System.currentTimeMillis();
            lit = tm;
            if (nonInteractive)
                break;
            if ((sim.timeStepCount - timeStepCountAtFrameStart) * 1000 >= steprate * (tm - sim.lastIterTime) || (tm - sim.lastFrameTime > frameTimeLimit))
                break;
            if (!sim.simRunning)
                break;
        }
        sim.lastIterTime = lit;
        if (delayWireProcessing)
            calcWireCurrents();
    }

    void applySolvedRightSide(double rs[]) {
        int j;
        for (j = 0; j != sim.circuitMatrixFullSize; j++) {
            RowInfo ri = sim.circuitRowInfo[j];
            double res = 0;
            if (ri.type == RowInfo.ROW_CONST)
                res = ri.value;
            else
                res = rs[ri.mapCol];
            if (Double.isNaN(res)) {
                sim.converged = false;
                break;
            }
            if (j < sim.nodeList.size() - 1) {
                sim.nodeVoltages[j] = res;
            } else {
                int ji = j - (sim.nodeList.size() - 1);
                sim.voltageSources[ji].setCurrent(ji, res);
            }
        }

        setNodeVoltages(sim.nodeVoltages);
        sim.syncAllSlots();
    }

    void setNodeVoltages(double nv[]) {
        int j, k;
        for (j = 0; j != nv.length; j++) {
            double res = nv[j];

            CircuitNode cn = sim.getCircuitNode(j + 1);
            for (k = 0; k != cn.links.size(); k++) {
                CircuitNodeLink cnl = cn.links.elementAt(k);
                cnl.elm.setNodeVoltage(cnl.num, res);
            }
        }
    }

    void calcWireCurrents() {
        int i;

        for (i = 0; i != sim.wireInfoList.size(); i++) {
            CirSim.WireInfo wi = sim.wireInfoList.get(i);
            double cur = 0;
            int j;
            Point p = wi.wire.getPost(wi.post);
            for (j = 0; j != wi.neighbors.size(); j++) {
                CircuitElm ce = wi.neighbors.get(j);
                int n = ce.getNodeAtPoint(p.x, p.y);
                cur += ce.getCurrentIntoNode(n);
            }
            if (wi.post == 0 || (wi.wire instanceof LabeledNodeElm))
                wi.wire.setCurrent(-1, cur);
            else
                wi.wire.setCurrent(-1, -cur);
        }
    }
}