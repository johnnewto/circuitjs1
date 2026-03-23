package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("Circuit serialization round-trip")
class SerializationRoundTripTest extends CircuitJavaSimTestBase {

    @Test
    @DisplayName("preserves element class names and dump types after export/import")
    void preservesClassNamesAndDumpTypes() throws Exception {
        String sourceText = readProjectFile("tests/fullrect.txt");
        String optionsLine = firstLine(sourceText);
        loadCircuitText(sourceText);

        String dump = exportElementDumpOnly(optionsLine, sim);
        List<String> namesBefore = classNames(sim);
        List<Integer> dumpTypesBefore = dumpTypes(sim);
        assertFalse(namesBefore.isEmpty(), "Expected at least one element after loading test circuit");

        CirSim reloaded = new CirSim();
        reloaded.getBootstrap().initRunner();
        reloaded.getCircuitIOService().readCircuit(dump, 0);
        reloaded.analyzeCircuit();

        List<String> namesAfter = classNames(reloaded);
        List<Integer> dumpTypesAfter = dumpTypes(reloaded);

        assertEquals(namesBefore, namesAfter, "Element class names should be stable across round-trip");
        assertEquals(dumpTypesBefore, dumpTypesAfter, "Element dump types should be stable across round-trip");
    }

    private static String exportElementDumpOnly(String optionsLine, CirSim s) {
        StringBuilder out = new StringBuilder();
        out.append(optionsLine).append('\n');
        CustomLogicModel.clearDumpedFlags();
        CustomCompositeModel.clearDumpedFlags();
        DiodeModel.clearDumpedFlags();
        TransistorModel.clearDumpedFlags();
        for (CircuitElm elm : s.elmList) {
            String model = elm.dumpModel();
            if (model != null && !model.isEmpty()) {
                out.append(model).append('\n');
            }
            out.append(s.getImportExportHelper().getElementDumpWithUid(elm)).append('\n');
        }
        return out.toString();
    }

    private static String readProjectFile(String relativePath) throws Exception {
        Path path = Paths.get(System.getProperty("projectDir"), relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String firstLine(String text) {
        int idx = text.indexOf('\n');
        return idx >= 0 ? text.substring(0, idx) : text;
    }

    private static List<String> classNames(CirSim s) {
        List<String> names = new ArrayList<String>();
        for (CircuitElm elm : s.elmList) {
            names.add(elm.getClassName());
        }
        return names;
    }

    private static List<Integer> dumpTypes(CirSim s) {
        List<Integer> types = new ArrayList<Integer>();
        for (CircuitElm elm : s.elmList) {
            types.add(elm.getDumpType());
        }
        return types;
    }
}
