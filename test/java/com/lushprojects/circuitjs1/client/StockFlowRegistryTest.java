package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("StockFlowRegistry")
class StockFlowRegistryTest {

    @BeforeEach
    void setUp() {
        StockFlowRegistry.clearRegistry();
    }

    @Test
    void testRegisterUnregisterAndSharedStockDetection() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, List<TableElm>> stockToTables =
            (Map<String, List<TableElm>>) getPrivateStaticField(StockFlowRegistry.class, "stockToTables");

        ArrayList<TableElm> seeded = new ArrayList<TableElm>();
        seeded.add(null);
        seeded.add(null);
        stockToTables.put("Cash", seeded);

        assertTrue(StockFlowRegistry.isStock("Cash"));
        assertTrue(StockFlowRegistry.isSharedStock("Cash"));
        Set<String> shared = StockFlowRegistry.getSharedStocks();
        assertTrue(shared.contains("Cash"));

        StockFlowRegistry.unregisterStock("Cash", null);
        assertFalse(StockFlowRegistry.isSharedStock("Cash"),
            "After removing one table, stock should no longer be shared");

        StockFlowRegistry.unregisterStock("Cash", null);
        assertTrue(StockFlowRegistry.getTablesForStock("Cash").isEmpty());

        StockFlowRegistry.registerStock("Cash", null);
        assertTrue(StockFlowRegistry.isStock("Cash"));
    }

    @Test
        void testCacheInvalidationOnRegisterAndUnregisterWithNullTable() throws Exception {

        @SuppressWarnings("unchecked")
        Map<String, LinkedHashSet<String>> cache =
                (Map<String, LinkedHashSet<String>>) getPrivateStaticField(StockFlowRegistry.class, "mergedRowsCache");

        cache.put("Loans", new LinkedHashSet<String>());
        assertTrue(cache.containsKey("Loans"));

        StockFlowRegistry.registerStock("Loans", null);
        assertFalse(cache.containsKey("Loans"), "Register should invalidate cache for that stock");

        cache.put("Loans", new LinkedHashSet<String>());
        StockFlowRegistry.unregisterStock("Loans", null);
        assertFalse(cache.containsKey("Loans"), "Unregister should invalidate cache for that stock");
    }

    private static Object getPrivateStaticField(Class<?> cls, String name) throws Exception {
        Field field = cls.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
