package johnnewto.world2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("World2Simulator")
class World2SimulatorTest {

    @Test
    @DisplayName("runs standard scenario and returns stable CSV header")
    void runsStandardScenario() {
        World2ScenarioLibrary library = new World2ScenarioLibrary();
        World2Simulator simulator = new World2Simulator(library);

        World2RunResult result = simulator.run("1", 25, 0.2);

        assertEquals("1", result.getScenario().getId());
        assertEquals(25, result.getSeries().size());
        assertEquals(1900.0, result.getSeries().get(0).getTime(), 1e-9);
        assertFalse(result.toCsv().isEmpty());
        assertTrue(result.toCsv().startsWith("t,P,POLR,CI,QL,NR\n"));
    }

    @Test
    @DisplayName("defaults step count from scenario range when steps is non-positive")
    void defaultsStepCountFromScenarioRange() {
        World2ScenarioLibrary library = new World2ScenarioLibrary();
        World2Simulator simulator = new World2Simulator(library);

        World2RunResult result = simulator.run("1", 0, 0.2);

        int expectedSteps = (int) Math.ceil((2100.0 - 1900.0) / 0.2) + 1;
        assertEquals(expectedSteps, result.getSeries().size());
    }
}
