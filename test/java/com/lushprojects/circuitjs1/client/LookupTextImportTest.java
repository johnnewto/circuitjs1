package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Standard text lookup import")
class LookupTextImportTest extends CircuitJavaSimTestBase {

    @Test
    @DisplayName("imports % lookup lines in standard circuit text")
    void importsPercentLookupLine() throws Exception {
        String text =
            "$ 0 5.0E-6 50 50 5 50 5.0E-11\n" +
            "% lookup BRMM scope=World2 0,1.2 1,1.0\n";

        sim.readCircuit(text, 0);

        LookupTableRegistry.LookupTableSnapshot snap = LookupTableRegistry.getSnapshot("World2", "BRMM");
        assertNotNull(snap, "Expected lookup table to be registered from % lookup line");
        assertTrue(snap.xs.size() >= 2, "Expected at least two lookup points");
    }

    @Test
    @DisplayName("standard text with inline @lookup words is not misdetected as SFCR")
    void inlineLookupWordDoesNotTriggerSfcrMode() {
        String text =
            "$ 0 5.0E-6 50 50 5 50 5.0E-11\n" +
            "% Hint BRMM this note mentions @lookup but is not an SFCR block\n" +
            "% lookup BRMM scope=World2 0,1.2 1,1.0\n";

        assertFalse(SFCRParser.isSFCRFormat(text),
                "Classic text dump should not be classified as SFCR due to inline @lookup text");
    }

    @Test
    @DisplayName("scoped lookup overrides global when both exist")
    void scopedLookupOverridesGlobal() {
        LookupTableRegistry.clear();

        String text =
            "$ 0 5.0E-6 50 50 5 50 5.0E-11\n" +
            "% lookup BRMM 0,10 1,10\n" +
            "% lookup BRMM scope=World2 0,20 1,20\n";

        sim.readCircuit(text, 0);

        double world2Value = LookupTableRegistry.evaluate("World2:BRMM", 0.5, true);
        double globalValue = LookupTableRegistry.evaluate("BRMM", 0.5, true);

        assertEquals(20.0, world2Value, 1e-12, "Scoped table should override global for World2:BRMM");
        assertEquals(10.0, globalValue, 1e-12, "Global lookup should remain available by bare name");
    }

    @Test
    @DisplayName("scoped name falls back to global when scoped table is missing")
    void scopedNameFallsBackToGlobal() {
        LookupTableRegistry.clear();

        String text =
            "$ 0 5.0E-6 50 50 5 50 5.0E-11\n" +
            "% lookup BRMM 0,7 1,7\n";

        sim.readCircuit(text, 0);

        double fallbackValue = LookupTableRegistry.evaluate("World2:BRMM", 0.5, true);
        double globalValue = LookupTableRegistry.evaluate("BRMM", 0.5, true);

        assertEquals(7.0, fallbackValue, 1e-12, "Missing scoped lookup should fall back to global BRMM");
        assertEquals(7.0, globalValue, 1e-12, "Global lookup should evaluate as expected");
    }
}
