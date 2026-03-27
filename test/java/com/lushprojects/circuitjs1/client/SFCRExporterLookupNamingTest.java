package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.io.LookupTableRegistry;
import com.lushprojects.circuitjs1.client.io.SFCRExporter;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRBlockType;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRExportContext;
import com.lushprojects.circuitjs1.client.io.sfcr.SFCRTemplateMerger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        SFCRExportContext ctx = new SFCRExportContext(null, SFCRExporter.ExportSyntax.R_STYLE);
        ctx.resetLookupExportState();

        String rewritten = rewrite(ctx, "pwlx(QL_R, 0, 1.2, 1, 1.0)", "World2");

        assertEquals("pwlx(QL_R, 0, 1.2, 1, 1.0)", rewritten);

        ArrayList<?> specs = ctx.getLookupExportSpecs();
        assertEquals(0, specs.size(), "pwlx should not create @lookup export specs");
    }

    @Test
    @DisplayName("native lookup call registers export spec from registry")
    void nativeLookupRegistersExportSpec() throws Exception {
        SFCRExportContext ctx = new SFCRExportContext(null, SFCRExporter.ExportSyntax.R_STYLE);
        ctx.resetLookupExportState();

        LookupTableRegistry.clear();
        ArrayList<Double> xs = new ArrayList<Double>();
        ArrayList<Double> ys = new ArrayList<Double>();
        xs.add(0.0);
        ys.add(1.2);
        xs.add(1.0);
        ys.add(1.0);
        LookupTableRegistry.registerScoped("World2", "BRMM", xs, ys);

        String rewritten = rewrite(ctx, "lookup(BRMM, QL_R)", "World2");
        assertEquals("lookup(BRMM, QL_R)", rewritten);

        ArrayList<?> specs = ctx.getLookupExportSpecs();
        assertEquals(1, specs.size(), "Native lookup should create one export spec");
        assertEquals("BRMM", getField(specs.get(0), "name"));
        assertEquals("World2", getField(specs.get(0), "scope"));
    }

    @Test
    @DisplayName("does not duplicate spec for repeated native lookup reference")
    void doesNotDuplicateSpecForRepeatedNativeLookup() throws Exception {
        SFCRExportContext ctx = new SFCRExportContext(null, SFCRExporter.ExportSyntax.R_STYLE);
        ctx.resetLookupExportState();

        LookupTableRegistry.clear();
        ArrayList<Double> xs = new ArrayList<Double>();
        ArrayList<Double> ys = new ArrayList<Double>();
        xs.add(0.0);
        ys.add(1.2);
        xs.add(1.0);
        ys.add(1.0);
        LookupTableRegistry.registerScoped("World2", "BRMM", xs, ys);

        rewrite(ctx, "lookup(BRMM, A)", "World2");
        rewrite(ctx, "lookup(BRMM, B)", "World2");

        ArrayList<?> specs = ctx.getLookupExportSpecs();
        assertEquals(1, specs.size());
        assertEquals("BRMM", getField(specs.get(0), "name"));
    }

    @Test
    @DisplayName("seeding template lookup names still works")
    void seedLookupNamesFromTemplateStillWorks() throws Exception {
        SFCRExportContext ctx = new SFCRExportContext(null, SFCRExporter.ExportSyntax.R_STYLE);
        ctx.resetLookupExportState();

        ctx.seedLookupNamesFromTemplate(
            "@lookup BRMM scope=World2\n" +
            "  0, 1.2\n" +
            "  1, 1.0\n" +
            "@end\n");

        ArrayList<?> specs = ctx.getLookupExportSpecs();
        assertEquals(1, specs.size());
        assertEquals("BRMM", getField(specs.get(0), "name"));
    }

    @Test
    @DisplayName("template lookup comments are carried into exported lookup blocks")
    void templateLookupCommentsArePreservedInExport() throws Exception {
        SFCRExportContext ctx = new SFCRExportContext(null, SFCRExporter.ExportSyntax.R_STYLE);
        ctx.resetLookupExportState();

        ctx.seedLookupNamesFromTemplate(
            "@lookup BRMM scope=World2\n" +
            "  # birth multiplier vs material standard of living (MSL)\n" +
            "  0, 1.2\n" +
            "  1, 1.0\n" +
            "@end\n");

        String text = SFCRTemplateMerger.renderBlocksForType(SFCRBlockType.LOOKUP, ctx);

        assertNotNull(text);
        assertTrue(text.contains("# birth multiplier vs material standard of living (MSL)"), text);
    }

    @Test
    @DisplayName("template comments survive when lookup points come from registry")
    void templateCommentsSurviveRegistryBackedSpec() throws Exception {
        SFCRExportContext ctx = new SFCRExportContext(null, SFCRExporter.ExportSyntax.R_STYLE);
        ctx.resetLookupExportState();

        ctx.seedLookupNamesFromTemplate(
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

        rewrite(ctx, "lookup(BRMM, QL_R)", "World2");

        String text = SFCRTemplateMerger.renderBlocksForType(SFCRBlockType.LOOKUP, ctx);

        assertNotNull(text);
        assertTrue(text.contains("# birth multiplier vs material standard of living (MSL)"), text);
    }

    @Test
    @DisplayName("unknown native lookup name does not create export spec")
    void unknownLookupDoesNotCreateSpec() throws Exception {
        SFCRExportContext ctx = new SFCRExportContext(null, SFCRExporter.ExportSyntax.R_STYLE);
        ctx.resetLookupExportState();

        LookupTableRegistry.clear();
        String rewritten = rewrite(ctx, "lookup(UnknownTable, X)", "World2");
        assertEquals("lookup(UnknownTable, X)", rewritten);

        assertTrue(ctx.getLookupExportSpecs().isEmpty());
    }

    @Test
    @DisplayName("does not duplicate when template has _lookup name and expression uses base name")
    void doesNotDuplicateTemplateLookupSuffixVariant() throws Exception {
        SFCRExportContext ctx = new SFCRExportContext(null, SFCRExporter.ExportSyntax.R_STYLE);
        ctx.resetLookupExportState();

        ctx.seedLookupNamesFromTemplate(
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

        rewrite(ctx, "lookup(CFIFR, FR)", "World2");

        ArrayList<?> specs = ctx.getLookupExportSpecs();
        assertEquals(1, specs.size(), "lookup spec should be deduped by point signature");

        String text = SFCRTemplateMerger.renderBlocksForType(SFCRBlockType.LOOKUP, ctx);

        assertNotNull(text);
        assertTrue(text.contains("@lookup CFIFR scope=World2"), text);
        assertFalse(text.contains("@lookup CFIFR_lookup scope=World2"), text);
    }

    private static String rewrite(SFCRExportContext ctx, String expr, String scope) throws Exception {
        Method rewrite = SFCRExportContext.class.getDeclaredMethod(
            "rewriteExpressionForLookupExport",
            String.class,
            String.class
        );
        rewrite.setAccessible(true);
        return (String) rewrite.invoke(ctx, expr, scope);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}

