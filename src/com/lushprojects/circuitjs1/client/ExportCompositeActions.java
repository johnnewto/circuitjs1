package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.ScriptInjector;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Window;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;

final class ExportCompositeActions {
    private final CirSim sim;

    @JsMethod(namespace = JsPackage.GLOBAL, name = "C2S")
    private static native JavaScriptObject createC2SContext(int w, int h);

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
    private static class SvgContextLike {
        @JsMethod(name = "getSerializedSvg") native String getSerializedSvg();
    }

    private static Context2d createSVGContext(int w, int h) {
        return createC2SContext(w, h).cast();
    }

    private static String getSerializedSVG(Context2d context) {
        return ((SvgContextLike) (Object) context).getSerializedSvg();
    }

    ExportCompositeActions(CirSim sim) {
        this.sim = sim;
    }

    void printCanvas(com.google.gwt.dom.client.CanvasElement cv) {
        String img = cv.toDataUrl("image/png");
        Window.open(
                "data:text/html,<html><head><title>Print Circuit</title></head><body><img src='" + URL.encodeQueryString(img)
                        + "'/></body></html>",
                "print", "height=500,width=500,status=yes,location=no");
    }

    void doDCAnalysis() {
        sim.dcAnalysisFlag = true;
        sim.resetAction();
    }

    void doPrint() {
        Canvas cv = getCircuitAsCanvas(CirSim.CAC_PRINT);
        printCanvas(cv.getCanvasElement());
    }

    boolean initializeSVGScriptIfNecessary(final String followupAction) {
        if (!sim.loadedCanvas2SVG) {
            ScriptInjector.fromUrl("canvas2svg.js").setCallback(new Callback<Void, Exception>() {
                public void onFailure(Exception reason) {
                    sim.alertOrWarn("Can't load canvas2svg.js.");
                }

                public void onSuccess(Void result) {
                    sim.loadedCanvas2SVG = true;
                    if (followupAction.equals("doExportAsSVG")) {
                        doExportAsSVG();
                    } else if (followupAction.equals("doExportAsSVGFromAPI")) {
                        doExportAsSVGFromAPI();
                    }
                }
            }).inject();
            return false;
        }
        return true;
    }

    void doExportAsSVG() {
        if (!initializeSVGScriptIfNecessary("doExportAsSVG")) {
            return;
        }
        CirSimDialogCoordinator.setDialogShowing(new ExportAsImageDialog(CirSim.CAC_SVG));
        CirSimDialogCoordinator.getDialogShowing().show();
    }

    public void doExportAsSVGFromAPI() {
        if (!initializeSVGScriptIfNecessary("doExportAsSVGFromAPI")) {
            return;
        }
        String svg = getCircuitAsSVG();
        sim.getJsApiBridge().callSVGRenderedHook(svg);
    }

    public Canvas getCircuitAsCanvas(int type) {
        Canvas cv = Canvas.createIfSupported();
        Rectangle bounds = sim.getCircuitBounds();

        int wmargin = 140;
        int hmargin = 100;
        int w = (bounds.width * 2 + wmargin);
        int h = (bounds.height * 2 + hmargin);
        cv.setCoordinateSpaceWidth(w);
        cv.setCoordinateSpaceHeight(h);

        Context2d context = cv.getContext2d();
        drawCircuitInContext(context, type, bounds, w, h);
        return cv;
    }

    public Canvas getScopesAsCanvas() {
        if (sim.scopeCount == 0)
            return null;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = 0;
        int maxY = 0;
        int margin = 10;

        for (int i = 0; i < sim.scopeCount; i++) {
            Scope s = sim.scopes[i];
            if (s.rect.x < minX)
                minX = s.rect.x;
            if (s.rect.y < minY)
                minY = s.rect.y;
            int right = s.rect.x + s.rect.width;
            int bottom = s.rect.y + s.rect.height;
            if (right > maxX)
                maxX = right;
            if (bottom > maxY)
                maxY = bottom;
        }

        int canvasWidth = maxX - minX + margin * 2;
        int canvasHeight = maxY - minY + margin * 2;

        Canvas cv = Canvas.createIfSupported();
        cv.setCoordinateSpaceWidth(canvasWidth);
        cv.setCoordinateSpaceHeight(canvasHeight);

        Context2d context = cv.getContext2d();
        Graphics g = new Graphics(context);

        if (sim.printableCheckItem.getState()) {
            CircuitElm.whiteColor = Color.black;
            CircuitElm.lightGrayColor = Color.black;
            g.setColor(Color.white);
        } else {
            CircuitElm.whiteColor = Color.white;
            CircuitElm.lightGrayColor = Color.lightGray;
            g.setColor(Color.black);
        }
        g.fillRect(0, 0, canvasWidth, canvasHeight);

        context.translate(margin - minX, margin - minY);

        for (int i = 0; i < sim.scopeCount; i++) {
            sim.scopes[i].draw(g);
        }

        return cv;
    }

    public String getCircuitAsSVG() {
        Rectangle bounds = sim.getCircuitBounds();

        int wmargin = 140;
        int hmargin = 100;
        int w = (bounds.width + wmargin);
        int h = (bounds.height + hmargin);
        Context2d context = createSVGContext(w, h);
        drawCircuitInContext(context, CirSim.CAC_SVG, bounds, w, h);
        return getSerializedSVG(context);
    }

    void drawCircuitInContext(Context2d context, int type, Rectangle bounds, int w, int h) {
        Graphics g = new Graphics(context);
        context.setTransform(1, 0, 0, 1, 0, 0);
        double oldTransform[] = Arrays.copyOf(sim.transform, 6);

        double scale = 1;

        sim.isExporting = true;

        boolean p = sim.printableCheckItem.getState();
        boolean c = sim.dotsCheckItem.getState();
        boolean print = (type == CirSim.CAC_PRINT);
        if (print)
            sim.printableCheckItem.setState(true);
        if (sim.printableCheckItem.getState()) {
            CircuitElm.whiteColor = Color.black;
            CircuitElm.lightGrayColor = Color.black;
            g.setColor(Color.white);
        } else {
            CircuitElm.whiteColor = Color.white;
            CircuitElm.lightGrayColor = Color.lightGray;
            g.setColor(Color.black);
        }
        g.fillRect(0, 0, w, h);
        sim.dotsCheckItem.setState(false);

        int wmargin = 140;
        int hmargin = 100;
        if (bounds != null)
            scale = Math.min(w / (double) (bounds.width + wmargin), h / (double) (bounds.height + hmargin));

        sim.transform[0] = sim.transform[3] = scale;
        sim.transform[4] = -(bounds.x - wmargin / 2);
        sim.transform[5] = -(bounds.y - hmargin / 2);
        context.scale(scale, scale);
        context.translate(sim.transform[4], sim.transform[5]);
        context.setLineCap(Context2d.LineCap.ROUND);

        for (int i = 0; i != sim.elmList.size(); i++) {
            sim.getElm(i).draw(g);
        }
        for (int i = 0; i != sim.postDrawList.size(); i++) {
            CircuitElm.drawPost(g, sim.postDrawList.get(i));
        }

        context.setTransform(1, 0, 0, 1, 0, 0);
        sim.getStatusInfoRenderer().drawActionSchedulerMessage(g, context);

        sim.printableCheckItem.setState(p);
        sim.dotsCheckItem.setState(c);
        sim.transform = oldTransform;
        sim.isExporting = false;
    }

    boolean isSelection() {
        for (int i = 0; i != sim.elmList.size(); i++)
            if (sim.getElm(i).isSelected())
                return true;
        return false;
    }

    public CustomCompositeModel getCircuitAsComposite() {
        int i;
        String nodeDump = "";
        String dump = "";
        CustomLogicModel.clearDumpedFlags();
        DiodeModel.clearDumpedFlags();
        TransistorModel.clearDumpedFlags();
        @SuppressWarnings("unchecked")
        Vector<LabeledNodeElm> sideLabels[] = new Vector[] { new Vector<LabeledNodeElm>(), new Vector<LabeledNodeElm>(),
                new Vector<LabeledNodeElm>(), new Vector<LabeledNodeElm>() };
        Vector<ExtListEntry> extList = new Vector<ExtListEntry>();
        boolean sel = isSelection();

        boolean used[] = new boolean[sim.nodeList.size()];
        boolean extnodes[] = new boolean[sim.nodeList.size()];

        if (!sim.preStampCircuit())
            return null;

        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (sel && !ce.isSelected())
                continue;
            if (ce instanceof LabeledNodeElm) {
                LabeledNodeElm lne = (LabeledNodeElm) ce;
                if (lne.isInternal())
                    continue;

                if (extnodes[ce.getNode(0)])
                    continue;

                int side = ChipElm.SIDE_W;
                if (Math.abs(ce.dx) >= Math.abs(ce.dy) && ce.dx > 0)
                    side = ChipElm.SIDE_E;
                if (Math.abs(ce.dx) <= Math.abs(ce.dy) && ce.dy < 0)
                    side = ChipElm.SIDE_N;
                if (Math.abs(ce.dx) <= Math.abs(ce.dy) && ce.dy > 0)
                    side = ChipElm.SIDE_S;

                sideLabels[side].add(lne);
                extnodes[ce.getNode(0)] = true;
                if (ce.getNode(0) == 0) {
                    sim.alertOrWarn("Node \"" + lne.text + "\" can't be connected to ground");
                    return null;
                }
            }
        }

        Collections.sort(sideLabels[ChipElm.SIDE_W], (LabeledNodeElm a, LabeledNodeElm b) -> Integer.signum(a.y - b.y));
        Collections.sort(sideLabels[ChipElm.SIDE_E], (LabeledNodeElm a, LabeledNodeElm b) -> Integer.signum(a.y - b.y));
        Collections.sort(sideLabels[ChipElm.SIDE_N], (LabeledNodeElm a, LabeledNodeElm b) -> Integer.signum(a.x - b.x));
        Collections.sort(sideLabels[ChipElm.SIDE_S], (LabeledNodeElm a, LabeledNodeElm b) -> Integer.signum(a.x - b.x));

        for (int side = 0; side < sideLabels.length; side++) {
            for (int pos = 0; pos < sideLabels[side].size(); pos++) {
                LabeledNodeElm lne = sideLabels[side].get(pos);
                ExtListEntry ent = new ExtListEntry(lne.text, lne.getNode(0), pos, side);
                extList.add(ent);
            }
        }

        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (sel && !ce.isSelected())
                continue;
            if (ce instanceof WireElm || ce instanceof LabeledNodeElm || ce instanceof ScopeElm)
                continue;
            if (ce instanceof GraphicElm || ce instanceof GroundElm)
                continue;
            int j;
            if (nodeDump.length() > 0)
                nodeDump += "\r";
            nodeDump += ce.getClass().getSimpleName();
            for (j = 0; j != ce.getPostCount(); j++) {
                int n = ce.getNode(j);
                used[n] = true;
                nodeDump += " " + n;
            }

            int x1 = ce.x;
            int y1 = ce.y;
            int x2 = ce.x2;
            int y2 = ce.y2;

            ce.x = ce.y = ce.x2 = ce.y2 = 0;

            String tstring = ce.dump();
            tstring = tstring.replaceFirst("[A-Za-z0-9]+ 0 0 0 0 ", "");

            ce.x = x1;
            ce.y = y1;
            ce.x2 = x2;
            ce.y2 = y2;
            if (dump.length() > 0)
                dump += " ";
            dump += CustomLogicModel.escape(tstring);
        }

        for (i = 0; i != extList.size(); i++) {
            ExtListEntry ent = extList.get(i);
            if (!used[ent.node]) {
                sim.alertOrWarn("Node \"" + ent.name + "\" is not used!");
                return null;
            }
        }

        boolean first = true;
        for (i = 0; i != sim.unconnectedNodes.size(); i++) {
            int q = sim.unconnectedNodes.get(i);
            if (!extnodes[q] && used[q]) {
                if (sim.nodesWithGroundConnectionCount == 0 && first) {
                    first = false;
                    continue;
                }
                sim.alertOrWarn("Some nodes are unconnected!");
                return null;
            }
        }

        CustomCompositeModel ccm = new CustomCompositeModel();
        ccm.nodeList = nodeDump;
        ccm.elmDump = dump;
        ccm.extList = extList;
        return ccm;
    }
}
