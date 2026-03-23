package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.economics.*;

import java.util.ArrayList;

public class TableMasterRegistryManager {
    private final CirSim sim;
    public TableMasterRegistryManager(CirSim sim) {
        this.sim = sim;
    }
    public void registerTableMastersInPriorityOrder() {
        ArrayList<TableElm> tables = new ArrayList<TableElm>();
        for (int i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof TableElm) {
                TableElm te = (TableElm) ce;
                tables.add(te);
            }
        }

        for (int i = 0; i < tables.size(); i++) {
            for (int j = i + 1; j < tables.size(); j++) {
                if (tables.get(j).getPriority() > tables.get(i).getPriority()) {
                    TableElm temp = tables.get(i);
                    tables.set(i, tables.get(j));
                    tables.set(j, temp);
                }
            }
        }

        for (int i = 0; i < tables.size(); i++) {
            TableElm table = tables.get(i);
            table.registerAsMasterOnly();
        }

        for (int i = 0; i < tables.size(); i++) {
            TableElm table = tables.get(i);
            table.updatePinOutputFlags();
        }
    }
}