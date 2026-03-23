package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.io.LookupTableRegistry;
import com.lushprojects.circuitjs1.client.io.SFCRExporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SFCRExporter lookup naming")
class SFCRExporterLookupNamingTest {

    @Test
    @DisplayName("keeps pwlx expression unchanged and does not extract lookup spec")
    void keepsPwlxUnchangedAndSkipsExtraction() throws Exception {
        SFCRExporter exporter = new SFCRExporter(null, SFCRExporter.ExportSyntax.R_STYLE);
        resetLookupState(exporter);

        String rewritten = rewrite(exporter, "pwlx(QL_R, 0, 1.2, 1, 1.0)", "World2");

        assertEquals("pwlx(QL_R, 0, 1.2, 1, 1.0)", rewritten);

        ArrayList<?> specs = lookupSpecs(exporter);
        assertEquals(0, specs.size(), "pwlx should not create @lookup export specs");
    }

    @Test
    @DisplayName("native lookup call registers export spec from registry")
    void nativeLookupRegistersExportSpec() throws Exception {
        SFCRExporter exporter = new SFCRExporter(null, SFCRExporter.ExportSyntax.R_STYLE);
        resetLookupState(exporter);

        LookupTableRegistry.clear();
        ArrayList<Double> xs = new ArrayList<Double>();
        ArrayList<Double> ys = new ArrayList<Double>();
        xs.add(0.0);
        ys.add(1.2);
        xs.add(1.0);
        ys.add(1.0);
        LookupTableRegistry.registerScoped("World2", "BRMM", xs, ys);

        String rewritten = rewrite(exporter, "lookup(BRMM, QL_R)", "World2");
        assertEquals("lookup(BRMM, QL_R)", rewritten);

        ArrayList<?> specs = lookupSpecs(exporter);
        assertEquals(1, specs.size(), "Native lookup should create one export spec");
        assertEquals("BRMM", getField(specs.get(0), "name"));
        assertEquals("World2", getField(specs.get(0), "scope"));
    }

    @Test
    @DisplayName("does not duplicate spec for repeated native lookup reference")
    void doesNotDuplicateSpecForRepeatedNativeLookup() throws Exception {
        SFCRExporter exporter = new SFCRExporter(null, SFCRExporter.ExportSyntax.R_STYLE);
        resetLookupState(exporter);

        LookupTableRegistry.clear();
        ArrayList<Double> xs = new ArrayList<Double>();
        ArrayList<Double> ys = new ArrayList<Double>();
        xs.add(0.0);
        ys.add(1.2);
        xs.add(1.0);
        ys.add(1.0);
        LookupTableRegistry.registerScoped("World2", "BRMM", xs, ys);

        rewrite(exporter, "lookup(BRMM, A)", "World2");
        rewrite(exporter, "lookup(BRMM, B)", "World2");

        ArrayList<?> specs = lookupSpecs(exporter);
        assertEquals(1, specs.size());
        assertEquals("BRMM", getField(specs.get(0), "name"));
    }

    @Test
    @DisplayName("seeding template lookup names still works")
    void seedLookupNamesFromTemplateStillWorks() throws Exception {
        SFCRExporter exporter = new SFCRExporter(null, SFCRExporter.ExportSyntax.R_STYLE);
        resetLookupState(exporter);

        Method seed = SFCRExporter.class.getDeclaredMethod("seedLookupNamesFromTemplate", String.class);
        seed.setAccessible(true);
        seed.invoke(exporter,
            "@lookup BRMM scope=World2\n" +
            "  0, 1.2\n" +
            "  1, 1.0\n" +
            "@end\n");

        ArrayList<?> specs = lookupSpecs(exporter);
        assertEquals(1, specs.size());
        assertEquals("BRMM", getField(specs.get(0), "name"));
    }

    @Test
    @DisplayName("template lookup comments are carried into exported lookup blocks")
    void templateLookupCommentsArePreservedInExport() throws Exception {
        SFCRExporter exporter = new SFCRExporter(null, SFCRExporter.ExportSyntax.R_STYLE);
        resetLookupState(exporter);

        Method seed = SFCRExporter.class.getDeclaredMethod("seedLookupNamesFromTemplate", String.class);
        seed.setAccessible(true);
        seed.invoke(exporter,
            "@lookup BRMM scope=World2\n" +
            "  # birth multiplier vs material standard of living (MSL)\n" +
            "  0, 1.2\n" +
            "  1, 1.0\n" +
            "@end\n");

        Method exportLookupBlocks = SFCRExporter.class.getDeclaredMethod("exportLookupBlocks");
        exportLookupBlocks.setAccessible(true);
        String text = (String) exportLookupBlocks.invoke(exporter);

        assertNotNull(text);
        assertTrue(text.contains("# birth multiplier vs material standard of living (MSL)"), text);
    }

    @Test
    @DisplayName("template comments survive when lookup points come from registry")
    void templateCommentsSurviveRegistryBackedSpec() throws Exception {
        SFCRExporter exporter = new SFCRExporter(null, SFCRExporter.ExportSyntax.R_STYLE);
        resetLookupState(exporter);

        Method seed = SFCRExporter.class.getDeclaredMethod("seedLookupNamesFromTemplate", String.class);
        seed.setAccessible(true);
        seed.invoke(exporter,
            "@lookup BRMM scope=World2\n" +
            "  # birth multiplier vs material standard of living (MSL)\n" +
            "  0, 9.0\n" +
            "  1, 9.0\n" +
            "@end\n");

        LookupTableRegistry.clear();
        ArrayList<Double> xs = new ArrayList<Double>();
        ArrayList<Double> ys = new ArrayList<Double>();
        xs.add(0.0);
        ys.add(1.2);
        xs.add(1.0);
        ys.add(1.0);
        LookupTableRegistry.registerScoped("World2", "BRMM", xs, ys);

        rewrite(exporter, "lookup(BRMM, QL_R)", "World2");

        Method exportLookupBlocks = SFCRExporter.class.getDeclaredMethod("exportLookupBlocks");
        exportLookupBlocks.setAccessible(true);
        String text = (String) exportLookupBlocks.invoke(exporter);

        assertNotNull(text);
        assertTrue(text.contains("# birth multiplier vs material standard of living (MSL)"), text);
    }

    @Test
    @DisplayName("unknown native lookup name does not create export spec")
    void unknownLookupDoesNotCreateSpec() throws Exception {
        SFCRExporter exporter = new SFCRExporter(null, SFCRExporter.ExportSyntax.R_STYLE);
        resetLookupState(exporter);

        LookupTableRegistry.clear();
        String rewritten = rewrite(exporter, "lookup(UnknownTable, X)", "World2");
        assertEquals("lookup(UnknownTable, X)", rewritten);

        assertTrue(lookupSpecs(exporter).isEmpty());
    }

    @Test
    @DisplayName("does not duplicate when template has _lookup name and expression uses base name")
    void doesNotDuplicateTemplateLookupSuffixVariant() throws Exception {
        SFCRExporter exporter = new SFCRExporter(null, SFCRExporter.ExportSyntax.R_STYLE);
        resetLookupState(exporter);

        Method seed = SFCRExporter.class.getDeclaredMethod("seedLookupNamesFromTemplate", String.class);
        seed.setAccessible(true);
        seed.invoke(exporter,
            "@lookup CFIFR_lookup scope=World2\n" +
            "  0, 1.0\n" +
            "  1, 0.3\n" +
            "@end\n");

        LookupTableRegistry.clear();
        ArrayList<Double> xs = new ArrayList<Double>();
        ArrayList<Double> ys = new ArrayList<Double>();
        xs.add(0.0);
        ys.add(1.0);
        xs.add(1.0);
        ys.add(0.3);
        LookupTableRegistry.registerScoped("World2", "CFIFR", xs, ys);

        rewrite(exporter, "lookup(CFIFR, FR)", "World2");

        ArrayList<?> specs = lookupSpecs(exporter);
        assertEquals(1, specs.size(), "lookup spec should be deduped by point signature");

        Method exportLookupBlocks = SFCRExporter.class.getDeclaredMethod("exportLookupBlocks");
        exportLookupBlocks.setAccessible(true);
        String text = (String) exportLookupBlocks.invoke(exporter);

        assertNotNull(text);
        assertTrue(text.contains("@lookup CFIFR scope=World2"), text);
        assertFalse(text.contains("@lookup CFIFR_lookup scope=World2"), text);
    }

    private static void resetLookupState(SFCRExporter exporter) throws Exception {
        Method reset = SFCRExporter.class.getDeclaredMethod("resetLookupExportState");
        reset.setAccessible(true);
        reset.invoke(exporter);
    }

    private static String rewrite(SFCRExporter exporter, String expr, String scope) throws Exception {
        Method rewrite = SFCRExporter.class.getDeclaredMethod(
            "rewriteExpressionForLookupExport",
            String.class,
            String.class
        );
        rewrite.setAccessible(true);
        return (String) rewrite.invoke(exporter, expr, scope);
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
