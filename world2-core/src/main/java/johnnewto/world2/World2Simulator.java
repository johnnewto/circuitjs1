package johnnewto.world2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class World2Simulator {
    private static final double LA = 135e6;
    private static final double PDN = 26.5;
    private static final double CIAFN = 0.3;
    private static final double ECIRN = 1.0;
    private static final double CIAFT = 15.0;
    private static final double POLS = 3.6e9;
    private static final double FN = 1.0;
    private static final double QLS = 1.0;

    private static final double PI = 1.65e9;
    private static final double NRI = 900e9;
    private static final double CII = 0.4e9;
    private static final double POLI = 0.2e9;
    private static final double CIAFI = 0.2;

    private static final String[] SWITCH_FUNCTION_NAMES = new String[] {
        "BRN", "DRN", "CIDN", "CIGN", "FC", "NRUN", "POLN"
    };

    private static final String[] TABLE_FUNCTION_NAMES = new String[] {
        "BRCM", "BRFM", "BRMM", "BRPM",
        "DRCM", "DRFM", "DRMM", "DRPM",
        "CFIFR", "CIM", "CIQR", "FCM", "FPCI", "FPM",
        "NREM", "NRMM", "POLAT", "POLCM",
        "QLC", "QLF", "QLM", "QLP"
    };

    private final World2ScenarioLibrary scenarioLibrary;

    public World2Simulator(World2ScenarioLibrary scenarioLibrary) {
        this.scenarioLibrary = scenarioLibrary;
    }

    public World2RunResult run(String scenarioId, int steps, double dt) {
        World2Scenario scenario = scenarioLibrary.getScenario(scenarioId);
        int totalSteps = steps > 0 ? steps : computeStepsFromRange(scenario, dt);
        if (totalSteps < 2) {
            totalSteps = 2;
        }
        double[] time = new double[totalSteps];
        for (int index = 0; index < totalSteps; index++) {
            time[index] = scenario.getYearMin() + index * dt;
        }

        FunctionSet functionSet = loadFunctionSetForScenario(scenario.getId());

        double[] p = new double[totalSteps];
        double[] br = new double[totalSteps];
        double[] dr = new double[totalSteps];
        double[] cr = new double[totalSteps];
        double[] nr = new double[totalSteps];
        double[] nrur = new double[totalSteps];
        double[] nrfr = new double[totalSteps];
        double[] ci = new double[totalSteps];
        double[] cir = new double[totalSteps];
        double[] cig = new double[totalSteps];
        double[] cid = new double[totalSteps];
        double[] cira = new double[totalSteps];
        double[] msl = new double[totalSteps];
        double[] ecir = new double[totalSteps];
        double[] ciaf = new double[totalSteps];
        double[] fr = new double[totalSteps];
        double[] pol = new double[totalSteps];
        double[] polr = new double[totalSteps];
        double[] polg = new double[totalSteps];
        double[] pola = new double[totalSteps];
        double[] ql = new double[totalSteps];

        p[0] = PI;
        nr[0] = NRI;
        nrfr[0] = 1.0;
        ci[0] = CII;
        cr[0] = PI / (LA * PDN);
        cir[0] = CII / PI;
        pol[0] = POLI;
        polg[0] = PI * functionSet.poln.value(time[0]) * functionSet.polcm.value(cir[0]);
        polr[0] = POLI / POLS;
        pola[0] = POLI / functionSet.polat.value(polr[0]);
        ciaf[0] = CIAFI;
        cira[0] = cir[0] * CIAFI / CIAFN;
        fr[0] = (functionSet.fpci.value(cira[0]) * functionSet.fcm.value(cr[0])
            * functionSet.fpm.value(polr[0]) * functionSet.fc.value(time[0])) / FN;
        ecir[0] = (cir[0] * (1.0 - ciaf[0]) * functionSet.nrem.value(nrfr[0])) / (1.0 - CIAFN);
        msl[0] = ecir[0] / ECIRN;
        ql[0] = QLS * functionSet.qlm.value(msl[0]) * functionSet.qlc.value(cr[0])
            * functionSet.qlf.value(fr[0]) * functionSet.qlp.value(polr[0]);

        for (int k = 1; k < totalSteps; k++) {
            int j = k - 1;

            br[k] = p[j] * functionSet.brn.value(time[j])
                * functionSet.brmm.value(msl[j])
                * functionSet.brcm.value(cr[j])
                * functionSet.brfm.value(fr[j])
                * functionSet.brpm.value(polr[j]);
            dr[k] = p[j] * functionSet.drn.value(time[j])
                * functionSet.drmm.value(msl[j])
                * functionSet.drpm.value(polr[j])
                * functionSet.drfm.value(fr[j])
                * functionSet.drcm.value(cr[j]);
            p[k] = Math.max(1.0, p[j] + (br[k] - dr[k]) * dt);

            nrur[k] = p[j] * functionSet.nrun.value(time[j]) * functionSet.nrmm.value(msl[j]);
            nr[k] = Math.max(0.0, nr[j] - nrur[k] * dt);
            nrfr[k] = nr[k] / NRI;

            cid[k] = ci[j] * functionSet.cidn.value(time[j]);
            cig[k] = p[j] * functionSet.cim.value(msl[j]) * functionSet.cign.value(time[j]);
            ci[k] = Math.max(1.0, ci[j] + dt * (cig[k] - cid[k]));
            cr[k] = p[k] / (LA * PDN);
            cir[k] = ci[k] / p[k];

            polg[k] = p[j] * functionSet.poln.value(time[j]) * functionSet.polcm.value(cir[j]);
            pola[k] = pol[j] / functionSet.polat.value(polr[j]);
            pol[k] = Math.max(0.0, pol[j] + (polg[k] - pola[k]) * dt);
            polr[k] = pol[k] / POLS;

            ciaf[k] = ciaf[j] + (functionSet.cfifr.value(fr[j])
                * functionSet.ciqr.value(functionSet.qlm.value(msl[j]) / functionSet.qlf.value(fr[j]))
                - ciaf[j]) * (dt / CIAFT);

            cira[k] = cir[k] * ciaf[k] / CIAFN;
            fr[k] = (functionSet.fcm.value(cr[k])
                * functionSet.fpci.value(cira[k])
                * functionSet.fpm.value(polr[k])
                * functionSet.fc.value(time[k])) / FN;
            ecir[k] = (cir[k] * (1.0 - ciaf[k]) * functionSet.nrem.value(nrfr[k])) / (1.0 - CIAFN);
            msl[k] = ecir[k] / ECIRN;
            ql[k] = QLS * functionSet.qlm.value(msl[k])
                * functionSet.qlc.value(cr[k])
                * functionSet.qlf.value(fr[k])
                * functionSet.qlp.value(polr[k]);
        }

        List<World2DataPoint> series = new ArrayList<World2DataPoint>(totalSteps);
        for (int index = 0; index < totalSteps; index++) {
            series.add(new World2DataPoint(time[index], p[index], polr[index], ci[index], ql[index], nr[index]));
        }

        return new World2RunResult(scenario, dt, series);
    }

    private int computeStepsFromRange(World2Scenario scenario, double dt) {
        double range = Math.max(0.0, scenario.getYearMax() - scenario.getYearMin());
        return (int) Math.ceil(range / dt) + 1;
    }

    private FunctionSet loadFunctionSetForScenario(String scenarioId) {
        Map<String, LinearTable> tableFunctions = loadTableFunctions("world2/functions_table_default.json");
        if ("4".equals(scenarioId)) {
            Map<String, LinearTable> override = loadTableFunctions("world2/scenarios/functions_table_scenario_4.json");
            tableFunctions.putAll(override);
        }

        String switchPath = "1".equals(scenarioId)
                ? "world2/functions_switch_default.json"
                : "world2/scenarios/functions_switch_scenario_" + scenarioId + ".json";
        Map<String, SwitchFunction> switchFunctions = loadSwitchFunctions(switchPath);
        if (switchFunctions.isEmpty()) {
            switchFunctions = loadSwitchFunctions("world2/functions_switch_default.json");
        }

        return new FunctionSet(tableFunctions, switchFunctions);
    }

    private Map<String, LinearTable> loadTableFunctions(String resourcePath) {
        JsonArray tableArray = loadJsonArray(resourcePath);
        Map<String, LinearTable> functions = new HashMap<String, LinearTable>();
        for (int index = 0; index < tableArray.size(); index++) {
            JsonObject row = tableArray.get(index).getAsJsonObject();
            String name = row.get("y.name").getAsString();
            double[] x = toDoubleArray(row.getAsJsonArray("x.values"));
            double[] y = toDoubleArray(row.getAsJsonArray("y.values"));
            functions.put(name, new LinearTable(name, x, y));
        }
        for (int index = 0; index < TABLE_FUNCTION_NAMES.length; index++) {
            String functionName = TABLE_FUNCTION_NAMES[index];
            if (!functions.containsKey(functionName)) {
                throw new IllegalStateException("Missing table function " + functionName + " in " + resourcePath);
            }
        }
        return functions;
    }

    private Map<String, SwitchFunction> loadSwitchFunctions(String resourcePath) {
        JsonArray switchArray = loadJsonArray(resourcePath);
        Map<String, SwitchFunction> functions = new HashMap<String, SwitchFunction>();
        for (int index = 0; index < switchArray.size(); index++) {
            JsonObject row = switchArray.get(index).getAsJsonObject();
            for (int nameIndex = 0; nameIndex < SWITCH_FUNCTION_NAMES.length; nameIndex++) {
                String name = SWITCH_FUNCTION_NAMES[nameIndex];
                if (row.has(name) && row.has(name + "1")) {
                    double beforeValue = row.get(name).getAsDouble();
                    double afterValue = row.get(name + "1").getAsDouble();
                    double triggerYear = row.get("trigger.value").getAsDouble();
                    functions.put(name, new SwitchFunction(name, beforeValue, afterValue, triggerYear));
                }
            }
        }
        return functions;
    }

    private JsonArray loadJsonArray(String resourcePath) {
        InputStream stream = World2Simulator.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            return new JsonArray();
        }
        try (InputStreamReader reader = new InputStreamReader(stream)) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.getAsJsonArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse resource " + resourcePath, exception);
        }
    }

    private double[] toDoubleArray(JsonArray jsonArray) {
        double[] values = new double[jsonArray.size()];
        for (int index = 0; index < jsonArray.size(); index++) {
            values[index] = jsonArray.get(index).getAsDouble();
        }
        return values;
    }

    private static class LinearTable {
        private final String name;
        private final double[] x;
        private final double[] y;

        LinearTable(String name, double[] x, double[] y) {
            this.name = name;
            this.x = x;
            this.y = y;
        }

        double value(double xValue) {
            if (xValue <= x[0]) {
                return y[0];
            }
            int last = x.length - 1;
            if (xValue >= x[last]) {
                return y[last];
            }
            for (int index = 0; index < last; index++) {
                if (xValue >= x[index] && xValue <= x[index + 1]) {
                    double range = x[index + 1] - x[index];
                    if (range == 0.0) {
                        return y[index];
                    }
                    double alpha = (xValue - x[index]) / range;
                    return y[index] + alpha * (y[index + 1] - y[index]);
                }
            }
            throw new IllegalStateException("Failed lookup for " + name + " at x=" + xValue);
        }
    }

    private static class SwitchFunction {
        private final String name;
        private final double beforeValue;
        private final double afterValue;
        private final double triggerYear;

        SwitchFunction(String name, double beforeValue, double afterValue, double triggerYear) {
            this.name = name;
            this.beforeValue = beforeValue;
            this.afterValue = afterValue;
            this.triggerYear = triggerYear;
        }

        double value(double time) {
            if (Double.isNaN(time)) {
                throw new IllegalStateException("Invalid time for switch " + name);
            }
            return time < triggerYear ? beforeValue : afterValue;
        }
    }

    private static class FunctionSet {
        private final SwitchFunction brn;
        private final SwitchFunction drn;
        private final SwitchFunction cidn;
        private final SwitchFunction cign;
        private final SwitchFunction fc;
        private final SwitchFunction nrun;
        private final SwitchFunction poln;

        private final LinearTable brcm;
        private final LinearTable brfm;
        private final LinearTable brmm;
        private final LinearTable brpm;
        private final LinearTable drcm;
        private final LinearTable drfm;
        private final LinearTable drmm;
        private final LinearTable drpm;
        private final LinearTable cfifr;
        private final LinearTable cim;
        private final LinearTable ciqr;
        private final LinearTable fcm;
        private final LinearTable fpci;
        private final LinearTable fpm;
        private final LinearTable nrem;
        private final LinearTable nrmm;
        private final LinearTable polat;
        private final LinearTable polcm;
        private final LinearTable qlc;
        private final LinearTable qlf;
        private final LinearTable qlm;
        private final LinearTable qlp;

        FunctionSet(Map<String, LinearTable> tableFunctions, Map<String, SwitchFunction> switchFunctions) {
            brn = requiredSwitch(switchFunctions, "BRN");
            drn = requiredSwitch(switchFunctions, "DRN");
            cidn = requiredSwitch(switchFunctions, "CIDN");
            cign = requiredSwitch(switchFunctions, "CIGN");
            fc = requiredSwitch(switchFunctions, "FC");
            nrun = requiredSwitch(switchFunctions, "NRUN");
            poln = requiredSwitch(switchFunctions, "POLN");

            brcm = requiredTable(tableFunctions, "BRCM");
            brfm = requiredTable(tableFunctions, "BRFM");
            brmm = requiredTable(tableFunctions, "BRMM");
            brpm = requiredTable(tableFunctions, "BRPM");
            drcm = requiredTable(tableFunctions, "DRCM");
            drfm = requiredTable(tableFunctions, "DRFM");
            drmm = requiredTable(tableFunctions, "DRMM");
            drpm = requiredTable(tableFunctions, "DRPM");
            cfifr = requiredTable(tableFunctions, "CFIFR");
            cim = requiredTable(tableFunctions, "CIM");
            ciqr = requiredTable(tableFunctions, "CIQR");
            fcm = requiredTable(tableFunctions, "FCM");
            fpci = requiredTable(tableFunctions, "FPCI");
            fpm = requiredTable(tableFunctions, "FPM");
            nrem = requiredTable(tableFunctions, "NREM");
            nrmm = requiredTable(tableFunctions, "NRMM");
            polat = requiredTable(tableFunctions, "POLAT");
            polcm = requiredTable(tableFunctions, "POLCM");
            qlc = requiredTable(tableFunctions, "QLC");
            qlf = requiredTable(tableFunctions, "QLF");
            qlm = requiredTable(tableFunctions, "QLM");
            qlp = requiredTable(tableFunctions, "QLP");
        }

        private static SwitchFunction requiredSwitch(Map<String, SwitchFunction> switchFunctions, String name) {
            SwitchFunction function = switchFunctions.get(name);
            if (function == null) {
                throw new IllegalStateException("Missing switch function " + name);
            }
            return function;
        }

        private static LinearTable requiredTable(Map<String, LinearTable> tableFunctions, String name) {
            LinearTable function = tableFunctions.get(name);
            if (function == null) {
                throw new IllegalStateException("Missing table function " + name);
            }
            return function;
        }
    }
}
