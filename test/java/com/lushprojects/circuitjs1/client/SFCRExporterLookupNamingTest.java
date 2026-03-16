package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SFCRExporter lookup naming")
class SFCRExporterLookupNamingTest {

    @Test
    @DisplayName("uses preferred lookup name when available")
    void usesPreferredLookupNameWhenAvailable() throws Exception {
        SFCRExporter exporter = new SFCRExporter(null, SFCRExporter.ExportSyntax.R_STYLE);
        resetLookupState(exporter);

        String rewritten = rewrite(exporter, "pwlx(QL_R, 0, 1.2, 1, 1.0)", "World2", "BRMM");

        assertTrue(rewritten.contains("lookup(BRMM, QL_R)"), "Expression should use preferred lookup name");

        ArrayList<?> specs = lookupSpecs(exporter);
        assertEquals(1, specs.size(), "One lookup spec should be extracted");
        assertEquals("BRMM", getField(specs.get(0), "name"));
        assertEquals("World2", getField(specs.get(0), "scope"));
    }

    @Test
    @DisplayName("adds numeric suffix for name collisions within same scope")
    void addsSuffixForNameCollisionsInSameScope() throws Exception {
        SFCRExporter exporter = new SFCRExporter(null, SFCRExporter.ExportSyntax.R_STYLE);
        resetLookupState(exporter);

        String first = rewrite(exporter, "pwlx(A, 0, 1, 1, 2)", "World2", "BRMM");
        String second = rewrite(exporter, "pwlx(B, 0, 3, 1, 4)", "World2", "BRMM");

        assertTrue(first.contains("lookup(BRMM, A)"));
        assertTrue(second.contains("lookup(BRMM_2, B)"));

        ArrayList<?> specs = lookupSpecs(exporter);
        assertEquals(2, specs.size(), "Two unique lookup specs should exist");
        assertEquals("BRMM", getField(specs.get(0), "name"));
        assertEquals("BRMM_2", getField(specs.get(1), "name"));
    }

    @Test
    @DisplayName("falls back to Lookup_N when preferred name is empty")
    void fallsBackToLookupNWhenPreferredNameMissing() throws Exception {
        SFCRExporter exporter = new SFCRExporter(null, SFCRExporter.ExportSyntax.R_STYLE);
        resetLookupState(exporter);

        String rewritten = rewrite(exporter, "pwlx(X, 0, 1, 1, 2)", "World2", "");

        assertTrue(rewritten.contains("lookup(Lookup_1, X)"));

        ArrayList<?> specs = lookupSpecs(exporter);
        assertEquals(1, specs.size());
        String name = (String) getField(specs.get(0), "name");
        assertTrue(name.startsWith("Lookup_"));
        assertFalse(name.isEmpty());
    }

    @Test
    @DisplayName("reuses template lookup name for matching points and scope")
    void reusesTemplateLookupNameForMatchingSignature() throws Exception {
        SFCRExporter exporter = new SFCRExporter(null, SFCRExporter.ExportSyntax.R_STYLE);
        resetLookupState(exporter);

        Method seed = SFCRExporter.class.getDeclaredMethod("seedLookupNamesFromTemplate", String.class);
        seed.setAccessible(true);
        seed.invoke(exporter,
            "@lookup BRMM scope=World2\n" +
            "  0, 1.2\n" +
            "  1, 1.0\n" +
            "@end\n");

        String rewritten = rewrite(exporter, "pwlx(QL_R, 0, 1.2, 1, 1.0)", "World2", "SomeOtherName");
        assertTrue(rewritten.contains("lookup(BRMM, QL_R)"),
            "Matching signature should reuse template lookup name");

        ArrayList<?> specs = lookupSpecs(exporter);
        assertEquals(1, specs.size());
        assertEquals("BRMM", getField(specs.get(0), "name"));
    }

    private static void resetLookupState(SFCRExporter exporter) throws Exception {
        Method reset = SFCRExporter.class.getDeclaredMethod("resetLookupExportState");
        reset.setAccessible(true);
        reset.invoke(exporter);
    }

    private static String rewrite(SFCRExporter exporter, String expr, String scope, String preferred) throws Exception {
        Method rewrite = SFCRExporter.class.getDeclaredMethod(
            "rewriteExpressionForLookupExport",
            String.class,
            String.class,
            String.class
        );
        rewrite.setAccessible(true);
        return (String) rewrite.invoke(exporter, expr, scope, preferred);
    }

    private static ArrayList<?> lookupSpecs(SFCRExporter exporter) throws Exception {
        Field field = SFCRExporter.class.getDeclaredField("lookupExportSpecs");
        field.setAccessible(true);
        return (ArrayList<?>) field.get(exporter);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
