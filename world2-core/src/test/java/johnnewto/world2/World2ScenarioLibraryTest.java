package johnnewto.world2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("World2ScenarioLibrary")
class World2ScenarioLibraryTest {

    @Test
    @DisplayName("loads scenario index and resolves known scenario IDs")
    void loadsScenarioIndex() {
        World2ScenarioLibrary library = new World2ScenarioLibrary();

        List<World2Scenario> scenarios = library.getAllScenarios();
        assertFalse(scenarios.isEmpty());
        assertEquals("1", scenarios.get(0).getId());
        assertEquals("Standard run", scenarios.get(0).getDisplayName());

        World2Scenario scenario = library.getScenario("6.2");
        assertEquals("6.2", scenario.getId());
    }

    @Test
    @DisplayName("throws for unknown scenario ID")
    void throwsOnUnknownScenarioId() {
        World2ScenarioLibrary library = new World2ScenarioLibrary();

        assertThrows(IllegalArgumentException.class, () -> library.getScenario("does-not-exist"));
    }
}
