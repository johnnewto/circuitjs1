package johnnewto.world2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class World2ScenarioLibrary {
    private static final String RESOURCE_PATH = "/world2/scenarios/index.csv";

    private final Map<String, World2Scenario> scenariosById;

    public World2ScenarioLibrary() {
        this.scenariosById = loadScenarios();
    }

    public List<World2Scenario> getAllScenarios() {
        return Collections.unmodifiableList(new ArrayList<World2Scenario>(scenariosById.values()));
    }

    public World2Scenario getScenario(String scenarioId) {
        if (scenarioId == null || scenarioId.trim().isEmpty()) {
            return scenariosById.get("1");
        }
        World2Scenario scenario = scenariosById.get(scenarioId.trim());
        if (scenario == null) {
            throw new IllegalArgumentException("Unknown World2 scenario: " + scenarioId);
        }
        return scenario;
    }

    private Map<String, World2Scenario> loadScenarios() {
        Map<String, World2Scenario> loaded = new LinkedHashMap<String, World2Scenario>();
        InputStream in = World2ScenarioLibrary.class.getResourceAsStream(RESOURCE_PATH);
        if (in == null) {
            throw new IllegalStateException("Missing scenario resource: " + RESOURCE_PATH);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmedLine.split(",", -1);
                if (parts.length != 10) {
                    throw new IllegalStateException("Invalid scenario line: " + trimmedLine);
                }
                World2Scenario scenario = new World2Scenario(
                        parts[0].trim(),
                        parts[1].trim(),
                        parseDouble(parts[2]),
                        parseDouble(parts[3]),
                        parseDouble(parts[4]),
                        parseDouble(parts[5]),
                        parseDouble(parts[6]),
                        parseDouble(parts[7]),
                        parseDouble(parts[8]),
                        parseDouble(parts[9]));
                loaded.put(scenario.getId(), scenario);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read scenario resource", exception);
        }

        return loaded;
    }

    private double parseDouble(String value) {
        return Double.parseDouble(value.trim());
    }
}
