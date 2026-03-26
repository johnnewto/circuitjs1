package com.lushprojects.circuitjs1.client.io.sfcr;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.GodlyTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.SFCSankeyElm;
import com.lushprojects.circuitjs1.client.elements.economics.SFCTableElm;
import com.lushprojects.circuitjs1.client.elements.misc.ActionTimeElm;
import com.lushprojects.circuitjs1.client.elements.misc.ScopeElm;
import com.lushprojects.circuitjs1.client.io.LookupDefinition;
import com.lushprojects.circuitjs1.client.io.SFCRExporter;
import com.lushprojects.circuitjs1.client.scope.Scope;

import java.util.ArrayList;
import java.util.List;

public class SFCRExportContext {
    private final SFCRExporter exporter;
    private List<String> equationBlocks = new ArrayList<String>();

    public SFCRExportContext(SFCRExporter exporter) {
        this.exporter = exporter;
    }

    public SFCRExporter getExporter() {
        return exporter;
    }

    public CirSim getSim() {
        return exporter.getSimForHandler();
    }

    public ActionTimeElm getActionTimeElm() {
        return exporter.getActionTimeElmForHandler();
    }

    public ArrayList<LookupDefinition> getLookupExportSpecs() {
        return exporter.getLookupExportSpecsForHandler();
    }

    public ArrayList<SFCSankeyElm> getSankeyDiagrams() {
        return exporter.getSankeyDiagramsForHandler();
    }

    public int getScopeCount() {
        return exporter.getScopeCountForHandler();
    }

    public Scope getScopeAt(int index) {
        return exporter.getScopeAtForHandler(index);
    }

    public int getElmListSize() {
        return exporter.getElmListSizeForHandler();
    }

    public CircuitElm getElmAt(int index) {
        return exporter.getElmAtForHandler(index);
    }

    public void markScopeElmExportedAsBlock(ScopeElm scopeElm) {
        exporter.markScopeElmExportedAsBlockForHandler(scopeElm);
    }

    public boolean appendScopeBlock(StringBuilder sb, Scope s, int defaultIndex, String defaultPrefix, ScopeElm scopeElm) {
        return exporter.appendScopeBlockForHandler(sb, s, defaultIndex, defaultPrefix, scopeElm);
    }

    public String formatPosition(CircuitElm elm) {
        return exporter.formatPositionForHandler(elm);
    }

    public String sanitizeName(String text) {
        return exporter.sanitizeNameForHandler(text);
    }

    public String escapeTableCell(String text) {
        return exporter.escapeTableCellForHandler(text);
    }

    public void appendLeadingBlockComments(StringBuilder sb, String blockType, String blockName) {
        exporter.appendLeadingBlockCommentsForHandler(sb, blockType, blockName);
    }

    public void appendExportBlock(StringBuilder sb, String block) {
        exporter.appendExportBlockForHandler(sb, block);
    }

    public ArrayList<EquationTableElm> getEquationTables() {
        return exporter.getEquationTablesForHandler();
    }

    public ArrayList<GodlyTableElm> getGodlyTables() {
        return exporter.getGodlyTablesForHandler();
    }

    public ArrayList<SFCTableElm> getSfcTables() {
        return exporter.getSfcTablesForHandler();
    }

    public String exportEquationTable(EquationTableElm eqTable) {
        return exporter.exportEquationTableForHandler(eqTable);
    }

    public String exportGodlyTable(GodlyTableElm godlyTable) {
        return exporter.exportGodlyTableForHandler(godlyTable);
    }

    public String exportMatrixTable(SFCTableElm sfcTable) {
        return exporter.exportMatrixBlockForHandler(sfcTable);
    }

    public String exportCircuitElements() {
        return exporter.exportCircuitElementsForHandler();
    }

    public List<String> getEquationBlocks() {
        return equationBlocks;
    }

    public void setEquationBlocks(List<String> equationBlocks) {
        this.equationBlocks = (equationBlocks == null) ? new ArrayList<String>() : equationBlocks;
    }
}
