package com.lushprojects.circuitjs1.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFCRMixedModeRegressionTest {

    @Test
    void testMixedModeRowsPreservedAcrossParseExportParse() throws Exception {
        String projectDir = System.getProperty("projectDir");
        assertNotNull(projectDir, "projectDir system property must be set by Gradle test task");

        Path fixtureFile = Paths.get(projectDir,
                "test", "resources", "sfcr", "mixed_modes_fixture.md");
        assertTrue(Files.exists(fixtureFile), "Fixture file not found: " + fixtureFile.toAbsolutePath());

        String originalText = new String(Files.readAllBytes(fixtureFile));
        SFCRParseResult first = SFCRParser.parseToResult(originalText);
        assertNotNull(first);

        SFCRParseResult.BlockDump firstEq = first.findBlock("equations", "MixedModes");
        assertNotNull(firstEq, "Expected equations block 'MixedModes'");

        String exported = SFCRParseResultExporter.export(first);
        SFCRParseResult second = SFCRParser.parseToResult(exported);
        assertNotNull(second);

        SFCRParseResult.BlockDump secondEq = second.findBlock("equations", "MixedModes");
        assertNotNull(secondEq, "Round-trip must preserve equations block name");

        List<Integer> firstModes = extractModeOrdinals(firstEq.dumpString);
        List<Integer> secondModes = extractModeOrdinals(secondEq.dumpString);

        assertEquals(3, firstModes.size(), "Fixture should contain exactly 3 rows");
        assertEquals(firstModes, secondModes, "Mode ordinals must survive round-trip");
        assertEquals(Integer.valueOf(0), secondModes.get(0), "Vout should remain VOLTAGE mode");
        assertEquals(Integer.valueOf(1), secondModes.get(1), "FlowAB should remain FLOW mode");
        assertEquals(Integer.valueOf(3), secondModes.get(2), "Gain should remain PARAM mode");

        List<String> secondTargets = extractTargets(secondEq.dumpString);
        assertEquals("NodeB", secondTargets.get(1), "FLOW target node must survive round-trip");
    }

    private List<Integer> extractModeOrdinals(String equationDump) {
        ArrayList<Integer> modes = new ArrayList<Integer>();
        StringTokenizer tok = new StringTokenizer(equationDump);

        tok.nextToken(); // type
        tok.nextToken(); // x1
        tok.nextToken(); // y1
        tok.nextToken(); // x2
        tok.nextToken(); // y2
        tok.nextToken(); // flags
        tok.nextToken(); // title
        int rowCount = Integer.parseInt(tok.nextToken());

        for (int row = 0; row < rowCount; row++) {
            tok.nextToken(); // outputName
            tok.nextToken(); // equation
            tok.nextToken(); // initialEquation
            tok.nextToken(); // sliderVarName
            tok.nextToken(); // sliderValue
            modes.add(Integer.valueOf(Integer.parseInt(tok.nextToken()))); // modeOrdinal
            tok.nextToken(); // targetNodeName
            tok.nextToken(); // capacitance
            tok.nextToken(); // shuntResistance
            tok.nextToken(); // useBackwardEuler
        }

        return modes;
    }

    private List<String> extractTargets(String equationDump) {
        ArrayList<String> targets = new ArrayList<String>();
        StringTokenizer tok = new StringTokenizer(equationDump);

        tok.nextToken(); // type
        tok.nextToken(); // x1
        tok.nextToken(); // y1
        tok.nextToken(); // x2
        tok.nextToken(); // y2
        tok.nextToken(); // flags
        tok.nextToken(); // title
        int rowCount = Integer.parseInt(tok.nextToken());

        for (int row = 0; row < rowCount; row++) {
            tok.nextToken(); // outputName
            tok.nextToken(); // equation
            tok.nextToken(); // initialEquation
            tok.nextToken(); // sliderVarName
            tok.nextToken(); // sliderValue
            tok.nextToken(); // modeOrdinal
            targets.add(tok.nextToken()); // targetNodeName
            tok.nextToken(); // capacitance
            tok.nextToken(); // shuntResistance
            tok.nextToken(); // useBackwardEuler
        }

        return targets;
    }

    @Test
    void testFlowModeInferredFromArrowSyntaxRoundTrip() throws Exception {
        String projectDir = System.getProperty("projectDir");
        assertNotNull(projectDir, "projectDir system property must be set by Gradle test task");

        Path fixtureFile = Paths.get(projectDir,
                "test", "resources", "sfcr", "mixed_modes_inferred_flow_fixture.md");
        assertTrue(Files.exists(fixtureFile), "Fixture file not found: " + fixtureFile.toAbsolutePath());

        String originalText = new String(Files.readAllBytes(fixtureFile));
        SFCRParseResult first = SFCRParser.parseToResult(originalText);
        assertNotNull(first);

        SFCRParseResult.BlockDump firstEq = first.findBlock("equations", "MixedModesInferred");
        assertNotNull(firstEq, "Expected equations block 'MixedModesInferred'");

        String exported = SFCRParseResultExporter.export(first);
        SFCRParseResult second = SFCRParser.parseToResult(exported);
        assertNotNull(second);

        SFCRParseResult.BlockDump secondEq = second.findBlock("equations", "MixedModesInferred");
        assertNotNull(secondEq, "Round-trip must preserve equations block name");

        List<Integer> modes = extractModeOrdinals(secondEq.dumpString);
        List<String> targets = extractTargets(secondEq.dumpString);

        assertEquals(2, modes.size(), "Fixture should contain exactly 2 rows");
        assertEquals(Integer.valueOf(1), modes.get(0), "Arrow syntax row should be inferred as FLOW mode");
        assertEquals("NodeC", targets.get(0), "Arrow syntax target should be preserved");
        assertEquals(Integer.valueOf(0), modes.get(1), "Plain row should remain VOLTAGE mode");
    }
}
