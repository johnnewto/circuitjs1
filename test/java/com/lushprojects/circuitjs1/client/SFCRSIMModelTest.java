package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Headless SFCR simulation regression")
class SFCRSIMModelTest extends HeadlessSimTest {

    private void loadMixedModesFixture() throws Exception {
        loadCircuitText(TestFixtures.loadSfcr("mixed_modes_fixture.md"));
    }

    @Test
    @DisplayName("loads mixed-modes fixture and runs headless")
    void sfcrModelLoadsAndRunsHeadless() throws Exception {
        loadMixedModesFixture();

        assertTrue(sim.elmList.size() > 0, "Fixture should create elements");
        runSteps(5);

        assertNotNull(sim, "Simulator must remain available after stepping");
        assertNull(sim.stopMessage, "Headless stepping should not stop with an error");
    }

    @Test
    @DisplayName("mixed-modes fixture creates EquationTable element")
    void mixedModesFixtureCreatesEquationTableElement() throws Exception {
        loadMixedModesFixture();

        boolean hasEquationTable = false;
        for (int i = 0; i < sim.elmList.size(); i++) {
            if (sim.getElm(i) instanceof EquationTableElm) {
                hasEquationTable = true;
                break;
            }
        }

        assertTrue(hasEquationTable, "Fixture should create at least one EquationTableElm");
    }

    @Test
    @DisplayName("mixed-modes fixture parses expected equations block")
    void mixedModesFixtureParsesExpectedEquationBlock() throws Exception {
        String source = TestFixtures.loadSfcr("mixed_modes_fixture.md");
        SFCRParseResult result = SFCRParser.parseToResult(source);

        assertNotNull(result, "SFCR parse result should be created");
        assertNotNull(result.findBlock("equations", "MixedModes"),
                "Expected equations block 'MixedModes' in parsed fixture");
    }

    @Test
    @DisplayName("headless stepping keeps simulation time monotonic")
    void headlessRunAdvancesSimulationTime() throws Exception {
        loadMixedModesFixture();

        double startTime = sim.t;
        runSteps(5);

        assertTrue(sim.t >= startTime, "Simulation time should not go backwards in headless mode");
        assertNull(sim.stopMessage, "Simulation should not stop while advancing time");
    }
}
