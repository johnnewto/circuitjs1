package com.lushprojects.circuitjs1.client;

import com.google.gwt.canvas.dom.client.Context2d;
import com.lushprojects.circuitjs1.client.elements.electronics.measurement.OutputElm;
import com.lushprojects.circuitjs1.client.elements.electronics.measurement.ProbeElm;
import java.util.Vector;

final class Scope2DController {
    static final class TimeStepResult {
        final int x;
        final int y;
        final double scaleX;
        final double scaleY;
        final boolean clearNeeded;

        TimeStepResult(int x, int y, double scaleX, double scaleY, boolean clearNeeded) {
            this.x = x;
            this.y = y;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.clearNeeded = clearNeeded;
        }
    }

    private Scope2DController() {
    }

    static TimeStepResult computeTimeStepPoint(
            boolean manualScale,
            double v,
            double yval,
            double currentScaleX,
            double currentScaleY,
            int width,
            int height,
            double xManScale,
            double yManScale,
            int xManVPosition,
            int yManVPosition,
            int manDivisions,
            int vPositionSteps) {
        if (!manualScale) {
            double scaleX = currentScaleX;
            double scaleY = currentScaleY;
            boolean clearNeeded = false;
            while (v > scaleX || v < -scaleX) {
                scaleX *= 2;
                clearNeeded = true;
            }
            while (yval > scaleY || yval < -scaleY) {
                scaleY *= 2;
                clearNeeded = true;
            }
            double xa = v / scaleX;
            double ya = yval / scaleY;
            int x = (int) (width * (1 + xa) * .499);
            int y = (int) (height * (1 - ya) * .499);
            return new TimeStepResult(x, y, scaleX, scaleY, clearNeeded);
        }

        double gridPx = calcGridPx(width, height, manDivisions);
        int x = (int) (width * .499 + (v / xManScale) * gridPx
                + gridPx * manDivisions * (double) xManVPosition / (double) vPositionSteps);
        int y = (int) (height * .499 - (yval / yManScale) * gridPx
                - gridPx * manDivisions * (double) yManVPosition / (double) vPositionSteps);
        return new TimeStepResult(x, y, currentScaleX, currentScaleY, false);
    }

    static double calcGridPx(int width, int height, int manDivisions) {
        int minDimension = Math.min(width, height);
        return ((double) minDimension / 2) / ((double) manDivisions / 2 + 0.05);
    }

    static int[] drawTraceSegment(Context2d imageContext, boolean printable, int drawOx, int drawOy, int x2, int y2) {
        if (drawOx == -1) {
            return new int[] {x2, y2};
        }
        imageContext.setStrokeStyle(printable ? "#000000" : "#ffffff");
        imageContext.beginPath();
        imageContext.moveTo(drawOx, drawOy);
        imageContext.lineTo(x2, y2);
        imageContext.stroke();
        return new int[] {x2, y2};
    }

    static int fade2dCanvas(Context2d imageContext, int alphaCounter, boolean printable, int width, int height) {
        int next = alphaCounter + 1;
        if (next > 2) {
            next = 0;
            imageContext.setGlobalAlpha(0.01);
            imageContext.setFillStyle(printable ? "#ffffff" : "#202020");
            imageContext.fillRect(0, 0, width, height);
            imageContext.setGlobalAlpha(1.0);
        }
        return next;
    }

    static void selectY(CirSim sim, Vector<ScopePlot> plots) {
        CircuitElm yElm = (plots.size() == 2) ? plots.get(1).elm : null;
        int e = (yElm == null) ? -1 : sim.locateElm(yElm);
        int firstE = e;
        while (true) {
            for (e++; e < sim.elmList.size(); e++) {
                CircuitElm ce = sim.getElm(e);
                if ((ce instanceof OutputElm || ce instanceof ProbeElm) && ce != plots.get(0).elm) {
                    yElm = ce;
                    if (plots.size() == 1) {
                        plots.add(new ScopePlot(yElm, Scope.UNITS_V));
                    } else {
                        plots.get(1).elm = yElm;
                        plots.get(1).units = Scope.UNITS_V;
                    }
                    return;
                }
            }
            if (firstE == -1) {
                return;
            }
            e = firstE = -1;
        }
    }
}
