package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.economics.EquationTableElm;
import com.lushprojects.circuitjs1.client.io.SFCRParseResult;
import com.lushprojects.circuitjs1.client.io.SFCRParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("SFCRParser")
@DisplayName("Goodwin predator-prey economy circuit")
class GoodwinPredatorPreyModelTest extends CircuitJavaSimTestBase {

    private static final Path CIRCUIT_PATH = Path.of(
            "src/com/lushprojects/circuitjs1/public/circuits/economics/econ_Goodwin_Predator_Prey.txt");

    @Test
    @DisplayName("source file parses into expected equations blocks")
    void sourceFileParsesIntoExpectedBlocks() throws Exception {
        String text = Files.readString(CIRCUIT_PATH, StandardCharsets.UTF_8);

        SFCRParseResult result = SFCRParser.parseToResult(text);
        assertNotNull(result, "Goodwin circuit should parse successfully");
        assertNotNull(result.findBlock("equations", "Parameters"),
                "Expected Parameters equations block");
        assertNotNull(result.findBlock("equations", "Goodwin.Cycle"),
                "Expected Goodwin.Cycle equations block");
        assertTrue(result.hints.containsKey("employment_rate"),
                "Hints should describe employment_rate");
        assertTrue(result.hints.containsKey("wage_share"),
                "Hints should describe wage_share");
    }

    @Test
    @DisplayName("source file loads and runs in circuit java")
    void sourceFileLoadsAndRuns() throws Exception {
        loadCircuit(CIRCUIT_PATH.toString());

        assertTrue(sim.elmList.size() > 0, "Goodwin circuit should create simulator elements");
        int equationTables = 0;
        for (int i = 0; i < sim.elmList.size(); i++) {
            if (sim.getElm(i) instanceof EquationTableElm) {
                equationTables++;
            }
        }
        assertTrue(equationTables >= 2, "Goodwin circuit should create the parameter and cycle equation tables");

        double startTime = sim.getTimingState().t;
        runSteps(12);

        assertNull(sim.stopMessage, "Simulation should run without stop errors");
        assertTrue(sim.getTimingState().t >= startTime, "Simulation time should advance while the model runs");
    }
}
