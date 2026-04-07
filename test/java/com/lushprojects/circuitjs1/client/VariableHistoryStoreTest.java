package com.lushprojects.circuitjs1.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Variable history store")
class VariableHistoryStoreTest {
    @Test
    @DisplayName("downsamples bounded history and preserves extrema envelope")
    void downsampledHistoryPreservesExtrema() {
        VariableHistoryStore store = new VariableHistoryStore();

        for (int i = 0; i < 600; i++) {
            store.captureSample("x", i, i % 2 == 0 ? 10 : -10);
        }

        VariableHistoryStore.SeriesSnapshot snapshot = store.getSeriesSnapshot("x");
        assertTrue(snapshot.size() <= 512);
        assertTrue(snapshot.hasEnvelope());
        assertEquals(-10.0, snapshot.minValues[0], 1e-12);
        assertEquals(10.0, snapshot.maxValues[0], 1e-12);
    }

    @Test
    @DisplayName("clear removes tracked variable history")
    void clearRemovesHistory() {
        VariableHistoryStore store = new VariableHistoryStore();

        store.captureSample("profit", 0.0, 1.25);
        store.captureSample("profit", 1.0, 2.5);
        assertTrue(store.hasHistory("profit"));

        store.clear();

        assertEquals(0, store.getTrackedSeriesCount());
        assertTrue(!store.hasHistory("profit"));
    }

    @Test
    @DisplayName("generic shared series preserves min max and interval after downsampling")
    void genericSeriesPreservesEnvelopeAndTimeBase() {
        VariableHistoryStore store = new VariableHistoryStore();

        for (int i = 0; i < 600; i++) {
            store.captureSeriesSample("scope:1:plot:0", "Voltage", i, -i, i);
        }

        VariableHistoryStore.SeriesSnapshot snapshot = store.getSeriesSnapshotByKey("scope:1:plot:0");
        assertTrue(snapshot.size() <= 512);
        assertEquals(0.0, snapshot.time[0], 1e-12);
        assertEquals(2.0, snapshot.averageSampleInterval(), 1e-12);
        assertEquals(-1.0, snapshot.minValues[0], 1e-12);
        assertEquals(1.0, snapshot.maxValues[0], 1e-12);
    }
}