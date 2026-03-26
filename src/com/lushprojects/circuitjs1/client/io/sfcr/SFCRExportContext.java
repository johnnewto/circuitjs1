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
import com.lushprojects.circuitjs1.client.io.SFCRUtil;
import com.lushprojects.circuitjs1.client.io.SFCRBlockCommentRegistry;
import com.lushprojects.circuitjs1.client.scope.Scope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Context object for SFCR export operations.
 * 
 * Holds all mutable state during export and provides access to categorized
 * circuit elements. Handlers use this context instead of calling back to
 * the exporter.
 */
public class SFCRExportContext {
    
    // =========================================================================
    // Core References
    // =========================================================================
    
    private final CirSim sim;
    private final SFCRExporter.ExportSyntax exportSyntax;
    
    // =========================================================================
    // Categorized Elements (populated during categorization)
    // =========================================================================
    
    private ArrayList<EquationTableElm> equationTables = new ArrayList<EquationTableElm>();
    private ArrayList<SFCTableElm> sfcTables = new ArrayList<SFCTableElm>();
    private ArrayList<GodlyTableElm> godlyTables = new ArrayList<GodlyTableElm>();
    private ArrayList<SFCSankeyElm> sankeyDiagrams = new ArrayList<SFCSankeyElm>();
    private ArrayList<CircuitElm> otherElements = new ArrayList<CircuitElm>();
    private ActionTimeElm actionTimeElm = null;
    
    // =========================================================================
    // Export State (mutable during export)
    // =========================================================================
    
    private HashSet<CircuitElm> scopeElmsExportedAsBlocks = new HashSet<CircuitElm>();
    private ArrayList<LookupDefinition> lookupExportSpecs = new ArrayList<LookupDefinition>();
    private HashMap<String, LookupDefinition> lookupExportBySignature = new HashMap<String, LookupDefinition>();
    private HashMap<String, ArrayList<String>> lookupCommentsByNameScope = new HashMap<String, ArrayList<String>>();
    private List<String> equationBlocks = new ArrayList<String>();
    
    // =========================================================================
    // Legacy Exporter Reference (for methods not yet migrated)
    // =========================================================================
    
    private final SFCRExporter exporter;

    // =========================================================================
    // Constructor
    // =========================================================================
    
    public SFCRExportContext(SFCRExporter exporter, CirSim sim, SFCRExporter.ExportSyntax syntax) {
        this.exporter = exporter;
        this.sim = sim;
        this.exportSyntax = syntax;
    }
    
    /**
     * Legacy constructor for backward compatibility.
     * Copies current exporter state into the context.
     * @deprecated Use the full constructor with sim and syntax parameters.
     */
    public SFCRExportContext(SFCRExporter exporter) {
        this.exporter = exporter;
        this.sim = exporter.getSim();
        this.exportSyntax = exporter.getExportSyntax();
        
        // Copy mutable state from exporter for backward compatibility
        // during transition period
        this.lookupExportSpecs = exporter.getLookupExportSpecsInternal();
        this.lookupExportBySignature = exporter.getLookupExportBySignatureInternal();
        this.lookupCommentsByNameScope = exporter.getLookupCommentsByNameScopeInternal();
    }
    
    // =========================================================================
    // Core Access
    // =========================================================================
    
    public CirSim getSim() {
        return sim;
    }
    
    public SFCRExporter.ExportSyntax getExportSyntax() {
        return exportSyntax;
    }

    // =========================================================================
    // Categorized Element Access
    // =========================================================================
    
    public ArrayList<EquationTableElm> getEquationTables() {
        return equationTables;
    }
    
    public void setEquationTables(ArrayList<EquationTableElm> tables) {
        this.equationTables = (tables != null) ? tables : new ArrayList<EquationTableElm>();
    }

    public ArrayList<GodlyTableElm> getGodlyTables() {
        return godlyTables;
    }
    
    public void setGodlyTables(ArrayList<GodlyTableElm> tables) {
        this.godlyTables = (tables != null) ? tables : new ArrayList<GodlyTableElm>();
    }

    public ArrayList<SFCTableElm> getSfcTables() {
        return sfcTables;
    }
    
    public void setSfcTables(ArrayList<SFCTableElm> tables) {
        this.sfcTables = (tables != null) ? tables : new ArrayList<SFCTableElm>();
    }
    
    public ArrayList<SFCSankeyElm> getSankeyDiagrams() {
        return sankeyDiagrams;
    }
    
    public void setSankeyDiagrams(ArrayList<SFCSankeyElm> diagrams) {
        this.sankeyDiagrams = (diagrams != null) ? diagrams : new ArrayList<SFCSankeyElm>();
    }
    
    public ArrayList<CircuitElm> getOtherElements() {
        return otherElements;
    }
    
    public void setOtherElements(ArrayList<CircuitElm> elements) {
        this.otherElements = (elements != null) ? elements : new ArrayList<CircuitElm>();
    }
    
    public ActionTimeElm getActionTimeElm() {
        return actionTimeElm;
    }
    
    public void setActionTimeElm(ActionTimeElm elm) {
        this.actionTimeElm = elm;
    }

    // =========================================================================
    // Scope Tracking
    // =========================================================================
    
    public int getScopeCount() {
        return (sim != null) ? sim.scopeCount : 0;
    }

    public Scope getScopeAt(int index) {
        return (sim != null && sim.scopes != null && index >= 0 && index < sim.scopeCount) 
            ? sim.scopes[index] : null;
    }

    public int getElmListSize() {
        return (sim != null && sim.elmList != null) ? sim.elmList.size() : 0;
    }

    public CircuitElm getElmAt(int index) {
        return (sim != null && sim.elmList != null && index >= 0 && index < sim.elmList.size())
            ? sim.elmList.get(index) : null;
    }

    public void markScopeElmExportedAsBlock(ScopeElm scopeElm) {
        if (scopeElm != null) {
            scopeElmsExportedAsBlocks.add(scopeElm);
        }
    }
    
    public boolean isScopeElmExportedAsBlock(ScopeElm scopeElm) {
        return scopeElm != null && scopeElmsExportedAsBlocks.contains(scopeElm);
    }
    
    public void clearScopeElmsExportedAsBlocks() {
        scopeElmsExportedAsBlocks.clear();
    }

    // =========================================================================
    // Lookup Export State
    // =========================================================================
    
    public ArrayList<LookupDefinition> getLookupExportSpecs() {
        return lookupExportSpecs;
    }
    
    public void setLookupExportSpecs(ArrayList<LookupDefinition> specs) {
        this.lookupExportSpecs = (specs != null) ? specs : new ArrayList<LookupDefinition>();
    }
    
    public HashMap<String, LookupDefinition> getLookupExportBySignature() {
        return lookupExportBySignature;
    }
    
    public void setLookupExportBySignature(HashMap<String, LookupDefinition> map) {
        this.lookupExportBySignature = (map != null) ? map : new HashMap<String, LookupDefinition>();
    }
    
    public HashMap<String, ArrayList<String>> getLookupCommentsByNameScope() {
        return lookupCommentsByNameScope;
    }
    
    public void setLookupCommentsByNameScope(HashMap<String, ArrayList<String>> map) {
        this.lookupCommentsByNameScope = (map != null) ? map : new HashMap<String, ArrayList<String>>();
    }
    
    public void resetLookupExportState() {
        lookupExportSpecs.clear();
        lookupExportBySignature.clear();
        lookupCommentsByNameScope.clear();
    }

    // =========================================================================
    // Equation Blocks (intermediate state between collect and emit)
    // =========================================================================

    public List<String> getEquationBlocks() {
        return equationBlocks;
    }

    public void setEquationBlocks(List<String> equationBlocks) {
        this.equationBlocks = (equationBlocks == null) ? new ArrayList<String>() : equationBlocks;
    }

    // =========================================================================
    // Export Helpers (stateless utilities)
    // =========================================================================
    
    public String formatPosition(CircuitElm elm) {
        if (elm == null) return "";
        int x = elm.x;
        int y = elm.y;
        if (x == 0 && y == 0) return "";
        return " x=" + x + " y=" + y;
    }

    public String sanitizeName(String text) {
        return SFCRUtil.sanitizeName(text);
    }

    public String escapeTableCell(String text) {
        return SFCRUtil.escapeTableCell(text);
    }

    public void appendLeadingBlockComments(StringBuilder sb, String blockType, String blockName) {
        if (sim == null || sb == null) {
            return;
        }
        String key = SFCRBlockCommentRegistry.makeKey(blockType, blockName);
        java.util.Vector<String> comments = sim.getSFCRDocumentState().getBlockComments(key);
        if (comments == null || comments.size() == 0) {
            return;
        }
        for (int i = 0; i < comments.size(); i++) {
            String line = comments.get(i);
            if (line == null) {
                continue;
            }
            sb.append(line).append("\n");
        }
        sb.append("\n");
    }

    public void appendExportBlock(StringBuilder sb, String block) {
        if (block == null || block.isEmpty()) {
            return;
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append("\n");
        }
        sb.append(block);
        if (!block.endsWith("\n")) {
            sb.append("\n");
        }
    }

    // =========================================================================
    // Scope Block Export (complex logic, delegates to exporter for now)
    // =========================================================================

    public boolean appendScopeBlock(StringBuilder sb, Scope s, int defaultIndex, String defaultPrefix, ScopeElm scopeElm) {
        return exporter.appendScopeBlockPublic(sb, s, defaultIndex, defaultPrefix, scopeElm);
    }

    // =========================================================================
    // Element Export (delegates to exporter for now - complex logic)
    // =========================================================================

    public String exportEquationTable(EquationTableElm eqTable) {
        return exporter.exportEquationTable(eqTable, exportSyntax);
    }

    public String exportGodlyTable(GodlyTableElm godlyTable) {
        return exporter.exportGodlyTable(godlyTable, exportSyntax);
    }

    public String exportMatrixTable(SFCTableElm sfcTable) {
        return exporter.exportMatrixTable(sfcTable, exportSyntax);
    }

    public String exportCircuitElements() {
        if (otherElements.isEmpty()) {
            return "";
        }
        return exporter.exportCircuitElements(otherElements, scopeElmsExportedAsBlocks);
    }
}
