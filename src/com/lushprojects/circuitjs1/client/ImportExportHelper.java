package com.lushprojects.circuitjs1.client;

class ImportExportHelper {
    private final CirSim sim;

    ImportExportHelper(CirSim sim) {
        this.sim = sim;
    }

    void importCircuitFromText(String circuitText, boolean subcircuitsOnly) {
        int flags = subcircuitsOnly ? (CirSim.RC_SUBCIRCUITS | CirSim.RC_RETAIN) : 0;
        if (circuitText != null) {
            sim.getCircuitIOService().readCircuit(circuitText, flags);
            sim.getUiPanelManager().allowSave(false);
        }
    }

    void importCircuitFromCTZ(String ctzData, boolean subcircuitsOnly) {
        if (ctzData != null && !ctzData.isEmpty()) {
            String circuitText = sim.decompress(ctzData);
            if (circuitText != null) {
                importCircuitFromText(circuitText, subcircuitsOnly);
            }
        }
    }

    String dumpOptions() {
        int f = (sim.dotsCheckItem.getState()) ? 1 : 0;
        f |= (sim.smallGridCheckItem.getState()) ? 2 : 0;
        f |= (sim.voltsCheckItem.getState()) ? 0 : 4;
        f |= (sim.powerCheckItem.getState()) ? 8 : 0;
        f |= (sim.showValuesCheckItem.getState()) ? 0 : 16;
        f |= sim.adjustTimeStep ? 64 : 0;
        String dump = "$ " + f + " " +
            sim.getTimingState().maxTimeStep + " " + sim.getSimulationLoop().getIterCount() + " " +
            sim.getCurrentBarValueForRouting() + " " + CircuitElm.voltageRange + " " +
            sim.getPowerBarValueForRouting() + " " + sim.getTimingState().minTimeStep + "\n";

        if (!sim.voltageUnitSymbol.equals("V")) {
            dump += "% voltageUnit " + CustomLogicModel.escape(sim.voltageUnitSymbol) + "\n";
        }

        if (sim.toolbarCheckItem != null) {
            dump += "% showToolbar " + (sim.toolbarCheckItem.getState() ? "true" : "false") + "\n";
        }

        dump += "% equationTableMnaMode " + (sim.equationTableMnaMode ? "true" : "false") + "\n";
        dump += "% equationTableNewtonJacobianEnabled " + (sim.equationTableNewtonJacobianEnabled ? "true" : "false") + "\n";
        dump += "% equationTableConvergenceTolerance " + sim.equationTableConvergenceTolerance + "\n";
        dump += "% sfcrLookupClampDefault " + (sim.sfcrLookupClampDefault ? "true" : "false") + "\n";
        dump += "% convergenceCheckThreshold " + sim.convergenceCheckThreshold + "\n";

        String lookupDump = LookupTableRegistry.dumpAll();
        if (lookupDump != null && !lookupDump.isEmpty()) {
            dump += lookupDump;
        }

        return dump;
    }

    String getElementDumpWithUid(CircuitElm ce) {
        String d = ce.dump();
        if (d == null)
            return null;
        return d + " U:" + CustomLogicModel.escape(ce.getPersistentUid());
    }

    CirSim.ElementDumpParseResult parseElementTokensWithUid(StringTokenizer st) {
        java.util.ArrayList<String> tokens = new java.util.ArrayList<String>();
        String uid = null;
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            if (uid == null && tok.startsWith("U:")) {
                uid = CustomLogicModel.unescape(tok.substring(2));
                continue;
            }
            tokens.add(tok);
        }
        String remaining = "";
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0)
                remaining += " ";
            remaining += tokens.get(i);
        }
        return new CirSim.ElementDumpParseResult(new StringTokenizer(remaining, " "), uid);
    }

    CircuitElm findElmByUid(String uid) {
        if (uid == null || uid.isEmpty())
            return null;
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (uid.equals(ce.getPersistentUid()))
                return ce;
        }
        return null;
    }

    void assignPersistentUid(CircuitElm ce, String uidFromFile) {
        String uid = uidFromFile;
        if (uid == null || uid.isEmpty())
            uid = ce.getPersistentUid();
        if (!uid.equals(ce.getPersistentUid()))
            ce.setPersistentUid(uid);
        while (findElmByUid(uid) != null) {
            uid = CircuitElm.generatePersistentUid();
            ce.setPersistentUid(uid);
        }
    }
}