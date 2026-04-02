package com.lushprojects.circuitjs1.client.io;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.util.StringTokenizer;

public class ImportExportHelper {
    private final CirSim sim;

    public static class ElementDumpParseResult {
        public final StringTokenizer tokenizer;
        public final String uid;
        public final Integer zOrder;

        ElementDumpParseResult(StringTokenizer tokenizer, String uid, Integer zOrder) {
            this.tokenizer = tokenizer;
            this.uid = uid;
            this.zOrder = zOrder;
        }
    }

    public ImportExportHelper(CirSim sim) {
        this.sim = sim;
    }

    public void importCircuitFromText(String circuitText, boolean subcircuitsOnly) {
        int flags = subcircuitsOnly
                ? (sim.getImportSubcircuitsFlagForImportExport() | sim.getImportRetainFlagForImportExport())
                : 0;
        if (circuitText != null) {
            sim.readCircuitFromImportHelper(circuitText, flags);
            sim.setAllowSaveFromImportHelper(false);
        }
    }

    public void importCircuitFromCTZ(String ctzData, boolean subcircuitsOnly) {
        if (ctzData != null && !ctzData.isEmpty()) {
            String circuitText = sim.decompressForImportHelper(ctzData);
            if (circuitText != null) {
                importCircuitFromText(circuitText, subcircuitsOnly);
            }
        }
    }

    public String dumpOptions() {
        int f = (sim.isDotsEnabledForExport()) ? 1 : 0;
        f |= (sim.isSmallGridEnabledForExport()) ? 2 : 0;
        f |= (sim.isVoltsEnabledForExport()) ? 0 : 4;
        f |= (sim.isPowerEnabledForExport()) ? 8 : 0;
        f |= (sim.isShowValuesEnabledForExport()) ? 0 : 16;
        f |= sim.isAdjustTimeStepEnabledForExport() ? 64 : 0;
        String dump = "$ " + f + " " +
            sim.getMaxTimeStepForExport() + " " + sim.getIterCountForExport() + " " +
            sim.getCurrentBarValueForRouting() + " " + sim.getVoltageRangeForExport() + " " +
            sim.getPowerBarValueForRouting() + " " + sim.getMinTimeStepForExport() + "\n";

        if (!sim.getVoltageUnitSymbolForExport().equals("V")) {
            dump += "% voltageUnit " + sim.escapeTokenForImportExport(sim.getVoltageUnitSymbolForExport()) + "\n";
        }

        if (sim.hasToolbarStateForExport()) {
            dump += "% showToolbar " + (sim.isToolbarVisibleForExport() ? "true" : "false") + "\n";
        }

        dump += "% equationTableMnaMode " + (sim.isEquationTableMnaModeForExport() ? "true" : "false") + "\n";
        dump += "% equationTableNewtonJacobianEnabled " + (sim.isEquationTableNewtonJacobianEnabledForExport() ? "true" : "false") + "\n";
        dump += "% equationTableConvergenceTolerance " + sim.getEquationTableConvergenceToleranceForExport() + "\n";
        dump += "% sfcrLookupClampDefault " + (sim.isSfcrLookupClampDefaultForExport() ? "true" : "false") + "\n";
        dump += "% convergenceCheckThreshold " + sim.getConvergenceCheckThresholdForExport() + "\n";

        String lookupDump = LookupTableRegistry.dumpAll();
        if (lookupDump != null && !lookupDump.isEmpty()) {
            dump += lookupDump;
        }

        return dump;
    }

    public String getElementDumpWithUid(CircuitElm ce) {
        return sim.getElementDumpWithUidForImportExport(ce);
    }

    public ElementDumpParseResult parseElementTokensWithUid(StringTokenizer st) {
        java.util.ArrayList<String> tokens = new java.util.ArrayList<String>();
        String uid = null;
        Integer zOrder = null;
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            if (uid == null && tok.startsWith("U:")) {
                uid = sim.unescapeTokenForImportExport(tok.substring(2));
                continue;
            }
            if (zOrder == null && tok.startsWith("Z:")) {
		try {
		    zOrder = Integer.valueOf(tok.substring(2));
		    continue;
		} catch (NumberFormatException ignored) {
		    // Preserve malformed metadata as ordinary element data.
		}
            }
            tokens.add(tok);
        }
        String remaining = "";
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0)
                remaining += " ";
            remaining += tokens.get(i);
        }
        return new ElementDumpParseResult(new StringTokenizer(remaining, " "), uid, zOrder);
    }

    public CircuitElm findElmByUid(String uid) {
        if (uid == null || uid.isEmpty())
            return null;
        for (int i = 0; i < sim.getElementCountForImportExport(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (uid.equals(sim.getElementUidForImportExport(ce)))
                return ce;
        }
        return null;
    }

    public void assignPersistentUid(CircuitElm ce, String uidFromFile) {
        String uid = uidFromFile;
        if (uid == null || uid.isEmpty())
            uid = sim.getElementUidForImportExport(ce);
        if (!uid.equals(sim.getElementUidForImportExport(ce)))
            sim.setElementUidForImportExport(ce, uid);
        while (findElmByUid(uid) != null) {
            uid = sim.generatePersistentUidForImportExport();
            sim.setElementUidForImportExport(ce, uid);
        }
    }
}
