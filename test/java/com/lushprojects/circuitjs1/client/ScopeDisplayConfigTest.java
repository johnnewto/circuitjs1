package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScopeDisplayConfigTest {
    @Test
    void modePredicatesMatchFlags() {
        ScopeDisplayConfig cfg = new ScopeDisplayConfig(false, true, false, true, true, true);
        assertTrue(cfg.is2DMode());
        assertFalse(cfg.isFFTMode());
        assertFalse(cfg.isMultiLhsActive(3));
        assertFalse(cfg.isDrawFromZeroActive());
    }

    @Test
    void multiLhsOnlyActiveWhenNot2dOrFftAndMultiplePlots() {
        ScopeDisplayConfig cfg = new ScopeDisplayConfig(false, false, false, true, false, false);
        assertTrue(cfg.isMultiLhsActive(2));
        assertFalse(cfg.isMultiLhsActive(1));

        ScopeDisplayConfig fftCfg = new ScopeDisplayConfig(false, false, true, true, false, false);
        assertFalse(fftCfg.isMultiLhsActive(3));
    }
}
