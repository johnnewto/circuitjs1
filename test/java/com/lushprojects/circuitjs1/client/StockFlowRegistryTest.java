package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.economics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("StockFlowRegistry")
@DisplayName("StockFlowRegistry — stock registration and cache invalidation")
class StockFlowRegistryTest {

    private StockTableView createView(final String title) {
        return new StockTableView() {
            public String getTableTitle() { return title; }
            public int getRows() { return 0; }
            public int getCols() { return 0; }
            public String getRowDescription(int row) { return ""; }
            public String getColumnHeader(int col) { return ""; }
        };
    }

    @BeforeEach
    void setUp() {
        StockFlowRegistry.clearRegistry();
        ComputedValues.resetForTesting();
    }

    @Test
    @DisplayName("register/unregister transitions isSharedStock correctly")
    void testRegisterUnregisterAndSharedStockDetection() {
        StockTableView tableA = createView("TableA");
        StockTableView tableB = createView("TableB");

        StockFlowRegistry.registerStock("Cash", tableA);
        StockFlowRegistry.registerStock("Cash", tableB);

        assertTrue(StockFlowRegistry.isStock("Cash"));
        assertTrue(StockFlowRegistry.isSharedStock("Cash"));
        Set<String> shared = StockFlowRegistry.getSharedStocks();
        assertTrue(shared.contains("Cash"));

        StockFlowRegistry.unregisterStock("Cash", tableA);
        assertFalse(StockFlowRegistry.isSharedStock("Cash"),
            "After removing one table, stock should no longer be shared");

        StockFlowRegistry.unregisterStock("Cash", tableB);
        assertTrue(StockFlowRegistry.getTablesForStock("Cash").isEmpty());

        StockFlowRegistry.registerStock("Cash", tableA);
        assertTrue(StockFlowRegistry.isStock("Cash"));
    }

    @Test
    @DisplayName("registerStock and unregisterStock invalidate merged-rows cache")
    void testCacheInvalidationOnRegisterAndUnregister() {
        StockTableView table = createView("LoansTable");
        StockFlowRegistry.TestSupport.seedMergedRowsCache("Loans", new LinkedHashSet<String>());
        assertTrue(StockFlowRegistry.TestSupport.isMergedRowsCached("Loans"));

        StockFlowRegistry.registerStock("Loans", table);
        assertFalse(StockFlowRegistry.TestSupport.isMergedRowsCached("Loans"),
                "Register should invalidate cache for that stock");

        StockFlowRegistry.TestSupport.seedMergedRowsCache("Loans", new LinkedHashSet<String>());
        StockFlowRegistry.unregisterStock("Loans", table);
        assertFalse(StockFlowRegistry.TestSupport.isMergedRowsCached("Loans"),
                "Unregister should invalidate cache for that stock");
    }
}
