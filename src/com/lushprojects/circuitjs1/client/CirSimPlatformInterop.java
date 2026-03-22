package com.lushprojects.circuitjs1.client;

import com.google.gwt.dom.client.CanvasElement;
import com.google.gwt.user.client.Timer;

final class CirSimPlatformInterop {
    private final CirSim sim;

    CirSimPlatformInterop(CirSim sim) {
        this.sim = sim;
    }

    static void installTouchHandlers(final CirSim sim, CanvasElement cv) {
        final CirSim.CanvasElementLike canvas = (CirSim.CanvasElementLike) (Object) cv;
        final double[] lastTap = new double[] { 0 };
        final double[] lastScale = new double[] { 1 };
        final Timer[] longPressTimer = new Timer[] { null };

        canvas.addEventListener("touchstart", new CirSim.TouchEventHandler() {
            public void handle(CirSim.TouchEventLike e) {
                CirSim.TouchListLike touches = e.getTouches();
                if (touches == null || touches.getLength() < 1)
                    return;
                e.preventDefault();
                if (longPressTimer[0] != null)
                    longPressTimer[0].cancel();

                double ts = e.getTimeStamp();
                boolean isDoubleTap = (ts - lastTap[0] < 300);
                if (!isDoubleTap) {
                    longPressTimer[0] = new Timer() {
                        public void run() {
                            sim.longPress();
                        }
                    };
                    longPressTimer[0].schedule(500);
                }
                lastTap[0] = ts;

                CirSim.TouchLike touch1 = touches.item(0);
                CirSim.TouchLike touch2 = touches.item(touches.getLength() - 1);
                lastScale[0] = Math.hypot(touch1.getClientX() - touch2.getClientX(), touch1.getClientY() - touch2.getClientY());

                double cx = .5 * (touch1.getClientX() + touch2.getClientX());
                double cy = .5 * (touch1.getClientY() + touch2.getClientY());
                CirSim.MouseEventInitLike init = CirSim.newMouseEventInit();
                init.setClientX(cx);
                init.setClientY(cy);
                CirSim.MouseEventLike mouseEvent = new CirSim.MouseEventLike(isDoubleTap ? "dblclick" : "mousedown", init);
                canvas.dispatchEvent(mouseEvent);
                if (touches.getLength() > 1)
                    sim.twoFingerTouch((int) cx, (int) (cy - canvas.getBoundingClientRect().getY()));
            }
        }, false);

        canvas.addEventListener("touchend", new CirSim.TouchEventHandler() {
            public void handle(CirSim.TouchEventLike e) {
                e.preventDefault();
                if (longPressTimer[0] != null)
                    longPressTimer[0].cancel();
                CirSim.MouseEventInitLike init = CirSim.newMouseEventInit();
                canvas.dispatchEvent(new CirSim.MouseEventLike("mouseup", init));
            }
        }, false);

        canvas.addEventListener("touchmove", new CirSim.TouchEventHandler() {
            public void handle(CirSim.TouchEventLike e) {
                CirSim.TouchListLike touches = e.getTouches();
                if (touches == null || touches.getLength() < 1)
                    return;
                e.preventDefault();
                if (longPressTimer[0] != null)
                    longPressTimer[0].cancel();

                CirSim.TouchLike touch1 = touches.item(0);
                CirSim.TouchLike touch2 = touches.item(touches.getLength() - 1);
                if (touches.getLength() > 1) {
                    double newScale = Math.hypot(touch1.getClientX() - touch2.getClientX(), touch1.getClientY() - touch2.getClientY());
                    if (lastScale[0] > 0)
                        sim.zoomCircuit(40 * (Math.log(newScale) - Math.log(lastScale[0])));
                    lastScale[0] = newScale;
                }

                double cx = .5 * (touch1.getClientX() + touch2.getClientX());
                double cy = .5 * (touch1.getClientY() + touch2.getClientY());
                CirSim.MouseEventInitLike init = CirSim.newMouseEventInit();
                init.setClientX(cx);
                init.setClientY(cy);
                canvas.dispatchEvent(new CirSim.MouseEventLike("mousemove", init));
            }
        }, false);
    }

    void electronSaveAsCallback(String s) {
        s = s.substring(s.lastIndexOf('/') + 1);
        s = s.substring(s.lastIndexOf('\\') + 1);
        sim.setCircuitTitle(s);
        sim.allowSave(true);
        sim.savedFlag = true;
        sim.repaint();
    }

    void electronSaveCallback() {
        sim.savedFlag = true;
        sim.repaint();
    }

    void electronSaveAs(final String dump) {
        CirSim.showSaveDialog().then(new CirSim.SaveDialogSuccessCallback() {
            public Object onSuccess(CirSim.SaveDialogResult file) {
                if (file == null || file.isCanceled())
                    return null;
                CirSim.saveFile(file, dump);
                Object path = file.getFilePath();
                if (path != null)
                    electronSaveAsCallback(path.toString());
                return null;
            }
        }, new CirSim.SaveDialogFailureCallback() {
            public Object onFailure(Object error) {
                CirSim.console("electronSaveAs failed: " + error);
                return null;
            }
        });
    }

    void electronSave(String dump) {
        CirSim.saveFile(null, dump);
        electronSaveCallback();
    }

    void electronOpenFileCallback(String text, String name) {
        LoadFile.doLoadCallback(text, name);
        sim.allowSave(true);
    }

    void electronOpenFile() {
        CirSim.openFile(new CirSim.OpenFileCallback() {
            public void onOpen(String text, String name) {
                electronOpenFileCallback(text, name);
            }
        });
    }

    void toggleDevTools() {
        CirSim.toggleDevToolsNative();
    }

    boolean isElectron() {
        return CirSim.GlobalWindowLike.getOpenFileFunction() != null;
    }

    String getElectronStartCircuitText() {
        return CirSim.GlobalWindowLike.getStartCircuitText();
    }
}