package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Element registry bootstrap metadata")
class ElementRegistryTest extends CircuitJavaSimTestBase {

    @BeforeEach
    void resetRegistry() {
        ElementRegistry.resetForTests();
    }

    @Test
    @DisplayName("registers legacy alias DecadeElm to RingCounterElm")
    void registersDecadeAlias() {
        ElementRegistry.ensureInitialized();

        ElementRegistry.Entry entry = ElementRegistry.getEntryByClassKey("DecadeElm");
        assertNotNull(entry, "Expected DecadeElm alias to be present");
        assertTrue(entry.alias, "Expected DecadeElm to be marked as alias");
        assertEquals("RingCounterElm", entry.canonicalClassKey,
                "Expected DecadeElm alias to resolve to RingCounterElm");
        assertEquals(ElementCategory.ELECTRONICS, entry.category,
                "Expected DecadeElm alias category to be electronics");
    }

    @Test
    @DisplayName("keeps legacy dump type 270 mapped to SFCTableElm")
    void preservesLegacyDumpType270() {
        ElementRegistry.ensureInitialized();

        ElementRegistry.Entry entry = ElementRegistry.getEntryByDumpType(270);
        assertNotNull(entry, "Expected dump type 270 to be registered");
        assertEquals("SFCTableElm", entry.canonicalClassKey,
                "Expected dump type 270 to map to SFCTableElm canonical class");
        assertEquals(ElementCategory.ECONOMICS, entry.category,
                "Expected dump type 270 category to be economics");
    }

    @Test
    @DisplayName("registers canonical math element with math category")
    void registersMathCategoryElement() {
        ElementRegistry.ensureInitialized();

        ElementRegistry.Entry entry = ElementRegistry.getEntryByClassKey("MultiplyElm");
        assertNotNull(entry, "Expected MultiplyElm to be registered");
        assertEquals(ElementCategory.MATH, entry.category,
                "Expected MultiplyElm to be categorized as math");
    }

    @Test
    @DisplayName("registers SFCKclNodeTableElm alias to SFCTableElm")
    void registersSfckclAlias() {
        ElementRegistry.ensureInitialized();

        ElementRegistry.Entry entry = ElementRegistry.getEntryByClassKey("SFCKclNodeTableElm");
        assertNotNull(entry, "Expected SFCKclNodeTableElm alias to be present");
        assertTrue(entry.alias, "Expected SFCKclNodeTableElm to be marked as alias");
        assertEquals("SFCTableElm", entry.canonicalClassKey,
                "Expected SFCKclNodeTableElm alias to resolve to SFCTableElm");
        assertEquals(ElementCategory.ECONOMICS, entry.category,
                "Expected SFCKclNodeTableElm category to be economics");
    }

    @Test
    @DisplayName("registers equation table element with economics category")
    void registersEquationTableAsEconomics() {
        ElementRegistry.ensureInitialized();

        ElementRegistry.Entry entry = ElementRegistry.getEntryByClassKey("EquationTableElm");
        assertNotNull(entry, "Expected EquationTableElm to be registered");
        assertEquals(ElementCategory.ECONOMICS, entry.category,
                "Expected EquationTableElm to be categorized as economics");
    }

        @Test
        @DisplayName("dynamically resolves and caches unmapped legacy class key")
        void dynamicallyCachesLegacyClassKey() {
                ElementRegistry.ensureInitialized();

                ElementRegistry.Entry before = ElementRegistry.getEntryByClassKey("LineElm");
                assertNull(before, "Expected LineElm to be absent before dynamic fallback");

                ElementRegistry.NameLookupResult lookupResult = ElementRegistry.createFromClassKey("LineElm", 10, 10);
                assertNotNull(lookupResult, "Expected registry to resolve LineElm via legacy fallback");
                assertNotNull(lookupResult.element, "Expected created element for LineElm");

                ElementRegistry.Entry after = ElementRegistry.getEntryByClassKey("LineElm");
                assertNotNull(after, "Expected LineElm to be cached after dynamic fallback");
                assertEquals(ElementCategory.UI_SUPPORT, after.category,
                                "Expected dynamically cached legacy entries to infer category from class key");
        }

    @Test
    @DisplayName("tracks inferred diagnostics for dynamic class-key and dump-type fallback")
    void tracksInferredDiagnostics() {
        ElementRegistry.ensureInitialized();

        ElementRegistry.createFromClassKey("LineElm", 10, 10);
        ElementRegistry.createFromDumpType(423, 10, 10, 20, 20, 0, new StringTokenizer(""));

        Map<String, ElementCategory> classInferred = ElementRegistry.getInferredClassKeyCategoriesSnapshot();
        Map<Integer, ElementCategory> dumpInferred = ElementRegistry.getInferredDumpTypeCategoriesSnapshot();

        assertTrue(classInferred.containsKey("LineElm"), "Expected inferred class-key diagnostics to include LineElm");
        assertEquals(ElementCategory.UI_SUPPORT, classInferred.get("LineElm"),
                "Expected inferred class-key category for LineElm to be UI_SUPPORT");

        assertTrue(dumpInferred.containsKey(423), "Expected inferred dump-type diagnostics to include 423");
        assertEquals(ElementCategory.UI_SUPPORT, dumpInferred.get(423),
                "Expected inferred dump-type category for 423 to be UI_SUPPORT");

        String report = ElementRegistry.buildInferredUsageReport();
        assertTrue(report.contains("LineElm"), "Expected inferred report to contain LineElm");
        assertTrue(report.contains("423"), "Expected inferred report to contain dump type 423");
    }
}
