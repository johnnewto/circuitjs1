package com.lushprojects.circuitjs1.client;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;

final class JsApiBridge {
    private final CirSim sim;

    JsApiBridge(CirSim sim) {
        this.sim = sim;
    }

    JsArray<JavaScriptObject> getJSArray() {
        return JavaScriptObject.createArray().cast();
    }

    JsArray<JavaScriptObject> getJSElements() {
        JsArray<JavaScriptObject> arr = getJSArray();
        for (int i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            ce.addJSMethods();
            arr.push(ce.getJavaScriptObject());
        }
        return arr;
    }

    void setupJSInterface() {
        final CirSim that = sim;
        CirSim.CircuitJsApi api = (CirSim.CircuitJsApi) (Object) JavaScriptObject.createObject();
        api.setSetSimRunning(new CirSim.HookBool() {
            public void call(boolean run) {
                that.setSimRunning(run);
            }
        });
        api.setReset(new CirSim.Hook0() {
            public void call() {
                that.resetAction();
            }
        });
        api.setStep(new CirSim.Hook0() {
            public void call() {
                that.stepCircuit();
            }
        });
        api.setGetTime(new CirSim.HookNoArgDouble() {
            public double call() {
                return that.getTimingState().t;
            }
        });
        api.setGetTimeStep(new CirSim.HookNoArgDouble() {
            public double call() {
                return that.getTimingState().timeStep;
            }
        });
        api.setSetTimeStep(new CirSim.HookDouble() {
            public void call(double ts) {
                that.getTimingState().timeStep = ts;
            }
        });
        api.setGetMaxTimeStep(new CirSim.HookNoArgDouble() {
            public double call() {
                return that.getTimingState().maxTimeStep;
            }
        });
        api.setSetMaxTimeStep(new CirSim.HookDouble() {
            public void call(double ts) {
                that.getTimingState().maxTimeStep = that.getTimingState().timeStep = ts;
            }
        });
        api.setIsRunning(new CirSim.HookNoArgBoolean() {
            public boolean call() {
                return that.simIsRunning();
            }
        });
        api.setGetNodeVoltage(new CirSim.HookStringToDouble() {
            public double call(String n) {
                return that.getCircuitValueSlotManager().getLabeledNodeVoltage(n);
            }
        });
        api.setSetExtVoltage(new CirSim.HookStringDouble() {
            public Object call(String n, double v) {
                that.getCircuitValueSlotManager().setExtVoltage(n, v);
                return null;
            }
        });
        api.setGetElements(new CirSim.HookNoArgElements() {
            public JsArray<JavaScriptObject> call() {
                return getJSElements();
            }
        });
        api.setGetCircuitAsSVG(new CirSim.HookNoArgString() {
            public String call() {
                return that.getExportCompositeActions().getCircuitAsSVG();
            }
        });
        api.setExportCircuit(new CirSim.HookNoArgString() {
            public String call() {
                return that.getCircuitIOService().dumpCircuit();
            }
        });
        api.setImportCircuit(new CirSim.HookStringBool() {
            public void call(String c, boolean s) {
                that.getImportExportHelper().importCircuitFromText(c, s);
            }
        });
        api.setImportCircuitFromCTZ(new CirSim.HookStringBool() {
            public void call(String ctz, boolean s) {
                that.getImportExportHelper().importCircuitFromCTZ(ctz, s);
            }
        });
        api.setGetSliderValue(new CirSim.HookStringToDouble() {
            public double call(String name) {
                return that.getCircuitValueSlotManager().getSliderValue(name);
            }
        });
        api.setSetSliderValue(new CirSim.HookStringDouble() {
            public Object call(String name, double value) {
                return that.getCircuitValueSlotManager().setSliderValue(name, value);
            }
        });
        api.setGetSliderNames(new CirSim.HookNoArgArrayString() {
            public JsArrayString call() {
                return that.getCircuitValueSlotManager().getSliderNames();
            }
        });
        api.setGetLabeledNodeNames(new CirSim.HookNoArgArrayString() {
            public JsArrayString call() {
                return that.getCircuitValueSlotManager().getLabeledNodeNames();
            }
        });
        api.setGetLabeledNodeValue(new CirSim.HookStringToDouble() {
            public double call(String name) {
                return that.getCircuitValueSlotManager().getLabeledNodeValue(name);
            }
        });
        api.setGetComputedValueNames(new CirSim.HookNoArgArrayString() {
            public JsArrayString call() {
                return that.getCircuitValueSlotManager().getComputedValueNames();
            }
        });
        api.setSetExprPerfProbeEnabled(new CirSim.HookBool() {
            public void call(boolean enabled) {
                Expr.setPerfProbeEnabled(enabled);
            }
        });
        api.setResetExprPerfProbe(new CirSim.Hook0() {
            public void call() {
                Expr.resetPerfProbe();
            }
        });
        api.setGetExprPerfProbeReport(new CirSim.HookNoArgString() {
            public String call() {
                return Expr.getPerfProbeReport();
            }
        });

        CirSim.GlobalWindowLike.setCircuitJS1(api);
        CirSim.OnCircuitLoadedHook hook = CirSim.GlobalWindowLike.getOnCircuitJsLoaded();
        if (hook != null)
            hook.call(api);
    }

    void callUpdateHook() {
        CirSim.CircuitJsApi api = CirSim.GlobalWindowLike.getCircuitJS1();
        if (api == null)
            return;
        CirSim.ApiHook hook = api.getOnUpdate();
        if (hook != null)
            hook.call(api);
    }

    void callAnalyzeHook() {
        CirSim.CircuitJsApi api = CirSim.GlobalWindowLike.getCircuitJS1();
        if (api == null)
            return;
        CirSim.ApiHook hook = api.getOnAnalyze();
        if (hook != null)
            hook.call(api);
    }

    void callTimeStepHook() {
        CirSim.CircuitJsApi api = CirSim.GlobalWindowLike.getCircuitJS1();
        if (api == null)
            return;
        CirSim.ApiHook hook = api.getOnTimeStep();
        if (hook != null)
            hook.call(api);
    }

    void callSVGRenderedHook(String svgData) {
        CirSim.CircuitJsApi api = CirSim.GlobalWindowLike.getCircuitJS1();
        if (api == null)
            return;
        CirSim.SvgHook hook = api.getOnSvgRendered();
        if (hook != null)
            hook.call(api, svgData);
    }
}
