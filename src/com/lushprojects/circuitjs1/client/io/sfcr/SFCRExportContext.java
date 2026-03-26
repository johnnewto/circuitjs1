package com.lushprojects.circuitjs1.client.io.sfcr;

import com.lushprojects.circuitjs1.client.io.SFCRExporter;

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

    public List<String> getEquationBlocks() {
        return equationBlocks;
    }

    public void setEquationBlocks(List<String> equationBlocks) {
        this.equationBlocks = (equationBlocks == null) ? new ArrayList<String>() : equationBlocks;
    }
}
