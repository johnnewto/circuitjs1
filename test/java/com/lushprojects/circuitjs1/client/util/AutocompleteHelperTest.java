package com.lushprojects.circuitjs1.client.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AutocompleteHelper built-in symbol recognition")
class AutocompleteHelperTest {

    @Test
    @DisplayName("recognizes historical and stateful expression functions as built-ins")
    void recognizesHistoricalAndStatefulFunctions() {
        ArrayList<String> completions = new ArrayList<String>();

        assertTrue(AutocompleteHelper.isKnownSymbol("last", completions));
        assertTrue(AutocompleteHelper.isKnownSymbol("lag", completions));
        assertTrue(AutocompleteHelper.isKnownSymbol("delay", completions));
        assertTrue(AutocompleteHelper.isKnownSymbol("integrate", completions));
        assertTrue(AutocompleteHelper.isKnownSymbol("diff", completions));
        assertTrue(AutocompleteHelper.isKnownSymbol("lookup", completions));
    }

    @Test
    @DisplayName("still flags unknown identifiers that are not built-ins or completions")
    void stillFlagsUnknownIdentifiers() {
        ArrayList<String> completions = new ArrayList<String>();

        assertFalse(AutocompleteHelper.isKnownSymbol("definitelyUnknownSymbol", completions));
    }
}