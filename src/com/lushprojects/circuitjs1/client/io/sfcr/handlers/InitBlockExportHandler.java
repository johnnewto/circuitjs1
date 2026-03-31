package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;

public class InitBlockExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.INIT;
    }

    @Override
    public int exportOrder() {
        return 10;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        return exportBlock(ctx);
    }

    public String exportBlock(SFCRExportContext ctx) {
        CirSim sim = ctx.getSim();
        StringBuilder sb = new StringBuilder();
        sb.append("@init\n");

        sb.append("  timestep: ").append(sim.getMaxTimeStep()).append("\n");

        if (sim.voltageUnitSymbol != null && !sim.voltageUnitSymbol.equals("V")) {
            sb.append("  voltageUnit: ").append(sim.voltageUnitSymbol).append("\n");
        }

        if (sim.timeUnitSymbol != null && !sim.timeUnitSymbol.isEmpty()) {
            sb.append("  timeUnit: ").append(sim.timeUnitSymbol).append("\n");
        }

        sb.append("  showDots: ").append(sim.dotsCheckItem.getState()).append("\n");
        sb.append("  showVolts: ").append(sim.voltsCheckItem.getState()).append("\n");
        sb.append("  showValues: ").append(sim.showValuesCheckItem.getState()).append("\n");
        sb.append("  showPower: ").append(sim.powerCheckItem.getState()).append("\n");
        sb.append("  autoAdjustTimestep: ").append(sim.adjustTimeStep).append("\n");
        sb.append("  equationTableMnaMode: ").append(sim.isEquationTableMnaMode()).append("\n");
        sb.append("  EqnTable Newton Jacobian: ").append(sim.equationTableNewtonJacobianEnabled).append("\n");
        sb.append("  equationTableTolerance: ").append(Double.toString(sim.getEquationTableConvergenceTolerance())).append("\n");
        sb.append("  lookupMode: ").append(sim.isSfcrLookupClampDefault() ? "pwl" : "pwlx").append("\n");
        sb.append("  lookupClamp: ").append(sim.isSfcrLookupClampDefault()).append("\n");
        sb.append("  convergenceCheckThreshold: ").append(sim.convergenceCheckThreshold).append("\n");
        sb.append("  infoViewerUpdateIntervalMs: ").append(sim.infoViewerUpdateIntervalMs).append("\n");
        sb.append("  Auto-Open Model Info on Load: ").append(sim.autoOpenModelInfoOnLoad).append("\n");

        sb.append("@end\n");
        return sb.toString();
    }
}
