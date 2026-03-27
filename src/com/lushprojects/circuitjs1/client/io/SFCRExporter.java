/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client.io;

import com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.GodlyTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.SFCSankeyElm;
import com.lushprojects.circuitjs1.client.elements.economics.SFCTableElm;
import com.lushprojects.circuitjs1.client.elements.misc.ActionTimeElm;

import java.util.ArrayList;

import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockExportHandlerRegistry;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRTemplateMerger;
import com.lushprojects.circuitjs1.client.io.sfcr.handlers.SFCRBlockExportHandler;

/**
 * Exports circuit in SFCR-compatible text format.
 *
 * Generates human-readable Stock-Flow Consistent model definitions compatible
 * with the R sfcr package (https://github.com/joaomacalos/sfcr).
 *
 * Output blocks:
 *   @init       - Simulation settings (timestep, units)
 *   @action     - Action Time schedule (timed target updates)
 *   (inline markdown) - Model documentation (no @info wrapper)
 *   @equations  - All equations (from EquationTableElm, GodlyTableElm)
 *   @lookup     - Named lookup tables referenced by lookup(name, x[, clamp]) equations
 *   @matrix     - Transaction matrices (from SFCTableElm)
 *   @hints      - Variable documentation
 *   @circuit    - Non-SFCR elements (passthrough)
 *   @scope      - Docked and undocked scopes with trace references (UID-based)
 *
 * @see SFCRParser
 * @see <a href="../dev_docs/SFCR_FORMAT_REFERENCE.md">SFCR Format Reference</a>
 */
public class SFCRExporter {

    public enum ExportSyntax {
        BLOCK_FORMAT,
        R_STYLE
    }

    // =========================================================================
    // Fields
    // =========================================================================

    private final CirSim sim;
    private final ExportSyntax exportSyntax;

    // =========================================================================
    // Constructor & Public API
    // =========================================================================

    /** Create a new SFCR exporter. */
    public SFCRExporter(CirSim sim) {
        this(sim, ExportSyntax.R_STYLE);
    }

    /** Create a new SFCR exporter with explicit syntax style. */
    public SFCRExporter(CirSim sim, ExportSyntax syntax) {
        this.sim = sim;
        this.exportSyntax = (syntax == null) ? ExportSyntax.R_STYLE : syntax;
    }

    /** Get the simulator instance. */
    public CirSim getSim() {
        return sim;
    }

    /** Get the export syntax style. */
    public ExportSyntax getExportSyntax() {
        return exportSyntax;
    }

    /** Export the current circuit in SFCR format. */
    public String export() {
        SFCRExportContext ctx = new SFCRExportContext(sim, exportSyntax);
        ctx.clearScopeElmsExportedAsBlocks();
        categorizeElements(ctx);
        ctx.resetLookupExportState();

        if (sim != null && sim.getSFCRDocumentManager().getModelInfoSourceText() != null
                && !sim.getSFCRDocumentManager().getModelInfoSourceText().trim().isEmpty()) {
            ctx.seedLookupNamesFromTemplate(sim.getSFCRDocumentManager().getModelInfoSourceText());
            String merged = SFCRTemplateMerger.export(
                    sim.getSFCRDocumentManager().getModelInfoSourceText(), ctx);
            if (merged != null && !merged.trim().isEmpty()) {
                return SFCRTemplateMerger.normalizeBlankLinesOutsideFences(merged);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# CircuitJS1 SFCR Export\n");
        sb.append("# Generated from circuit simulation\n");
        sb.append("\n");

        for (SFCRBlockExportHandler handler : SFCRBlockExportHandlerRegistry.getOrderedHandlers()) {
            ctx.appendExportBlock(sb, handler.export(ctx));
        }

        String inlineDocs = SFCRTemplateMerger.exportInlineDocumentation(sim);
        if (!inlineDocs.isEmpty()) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                sb.append("\n");
            }
            sb.append("\n");
            sb.append(inlineDocs);
        }

        return SFCRTemplateMerger.normalizeBlankLinesOutsideFences(sb.toString());
    }

    // =========================================================================
    // Element categorization
    // =========================================================================

    private void categorizeElements(SFCRExportContext ctx) {
        ArrayList<EquationTableElm> eqTables = new ArrayList<EquationTableElm>();
        ArrayList<SFCTableElm> matrixTables = new ArrayList<SFCTableElm>();
        ArrayList<GodlyTableElm> godlyTableList = new ArrayList<GodlyTableElm>();
        ArrayList<SFCSankeyElm> sankeyList = new ArrayList<SFCSankeyElm>();
        ArrayList<CircuitElm> otherElms = new ArrayList<CircuitElm>();
        ActionTimeElm actionElm = null;

        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            if (elm instanceof EquationTableElm) {
                eqTables.add((EquationTableElm) elm);
            } else if (elm instanceof SFCTableElm) {
                matrixTables.add((SFCTableElm) elm);
            } else if (elm instanceof GodlyTableElm) {
                godlyTableList.add((GodlyTableElm) elm);
            } else if (elm instanceof SFCSankeyElm) {
                sankeyList.add((SFCSankeyElm) elm);
            } else if (elm instanceof ActionTimeElm) {
                if (actionElm == null) {
                    actionElm = (ActionTimeElm) elm;
                }
            } else {
                otherElms.add(elm);
            }
        }

        ctx.setEquationTables(eqTables);
        ctx.setSfcTables(matrixTables);
        ctx.setGodlyTables(godlyTableList);
        ctx.setSankeyDiagrams(sankeyList);
        ctx.setOtherElements(otherElms);
        ctx.setActionTimeElm(actionElm);
    }
}
