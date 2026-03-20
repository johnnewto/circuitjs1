package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NumFmt non-interactive formatting")
class NumFmtNonInteractiveTest {

    @AfterEach
    void resetRuntimeMode() {
        RuntimeMode.setNonInteractiveRuntime(false);
    }

    @Test
    @DisplayName("formats large values without overflow clamp")
    void formatsLargeValuesWithoutOverflowClamp() {
        RuntimeMode.setNonInteractiveRuntime(true);
        NumFmt.Formatter formatter = NumFmt.forPattern("0.###############");

        String formatted = formatter.format(1_650_000_000d);

        assertEquals("1650000000", formatted);
        assertTrue(!formatted.startsWith("9223"), "Formatter must not clamp to Long.MAX rounding artifact");
    }
}
