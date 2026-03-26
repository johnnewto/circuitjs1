package com.lushprojects.circuitjs1.client.io.sfcr.handlers;

import com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.GodlyTableElm;
import com.lushprojects.circuitjs1.client.elements.economics.TableColumn;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;
import com.lushprojects.circuitjs1.client.registry.HintRegistry;

import java.util.HashSet;
import java.util.Set;

public class HintsBlockExportHandler implements SFCRBlockExportHandler {
    @Override
    public SFCRBlockType blockType() {
        return SFCRBlockType.HINTS;
    }

    @Override
    public int exportOrder() {
        return 80;
    }

    @Override
    public String export(SFCRExportContext ctx) {
        return exportBlock(ctx);
    }

    public String exportBlock(SFCRExportContext ctx) {
        Set<String> names = HintRegistry.getAllNames();
        if (names.isEmpty()) {
            return "";
        }

        Set<String> namesCoveredByEquationBlocks = collectNamesCoveredByEquationBlocks(ctx);

        StringBuilder sb = new StringBuilder();
        sb.append("@hints\n");
        int exportedCount = 0;

        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            if (namesCoveredByEquationBlocks.contains(name.trim())) {
                continue;
            }
            String hint = HintRegistry.getHint(name);
            if (hint != null && !hint.trim().isEmpty()) {
                sb.append("  ").append(name.trim()).append(": ").append(hint).append("\n");
                exportedCount++;
            }
        }

        if (exportedCount == 0) {
            return "";
        }

        sb.append("@end\n");
        return sb.toString();
    }

    private Set<String> collectNamesCoveredByEquationBlocks(SFCRExportContext ctx) {
        Set<String> covered = new HashSet<String>();

        for (EquationTableElm eqTable : ctx.getEquationTables()) {
            if (eqTable == null) {
                continue;
            }
            int rowCount = eqTable.getRowCount();
            for (int row = 0; row < rowCount; row++) {
                String sourceName = eqTable.getOutputName(row);
                if (sourceName == null) {
                    continue;
                }
                String trimmed = sourceName.trim();
                if (trimmed.isEmpty() || EquationTableElm.isCommentRowName(trimmed)) {
                    continue;
                }
                covered.add(trimmed);
            }
        }

        for (GodlyTableElm godlyTable : ctx.getGodlyTables()) {
            if (godlyTable == null) {
                continue;
            }
            int cols = godlyTable.getCols();
            for (int col = 0; col < cols; col++) {
                TableColumn column = godlyTable.getColumn(col);
                if (column == null || column.isALE()) {
                    continue;
                }
                String stockName = column.getStockName();
                if (stockName == null) {
                    continue;
                }
                String trimmed = stockName.trim();
                if (!trimmed.isEmpty()) {
                    covered.add(trimmed);
                }
            }
        }

        return covered;
    }
}
