package com.lushprojects.circuitjs1.client.elements.annotation;

import com.lushprojects.circuitjs1.client.CustomLogicModel;
import com.lushprojects.circuitjs1.client.util.StringTokenizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Sequence diagram wide flows")
class SequenceDiagramElmWideFlowTest {

    @Test
    @DisplayName("wide flow stroke width scales linearly with transaction magnitude")
    void wideFlowStrokeWidthScalesWithMagnitude() {
        assertEquals(SequenceDiagramElm.WIDE_FLOW_MIN_STROKE_WIDTH,
            SequenceDiagramElm.computeWideFlowStrokeWidth(0, 100), 1e-9);
        assertEquals(12.0,
            SequenceDiagramElm.computeWideFlowStrokeWidth(50, 100), 1e-9);
        assertEquals(SequenceDiagramElm.WIDE_FLOW_MAX_STROKE_WIDTH,
            SequenceDiagramElm.computeWideFlowStrokeWidth(100, 100), 1e-9);
    }

    @Test
    @DisplayName("arrowhead geometry is 25 percent larger than before")
    void arrowheadGeometryIsLarger() {
        assertEquals(13, SequenceDiagramElm.computeArrowLength(2.0));
        assertEquals(5, SequenceDiagramElm.computeArrowHalfHeight(2.0));
        assertEquals(19, SequenceDiagramElm.computeArrowLength(12.0));
        assertEquals(8, SequenceDiagramElm.computeArrowHalfHeight(12.0));
    }

    @Test
    @DisplayName("legacy sequence diagram dumps default wide flow mode to enabled")
    void legacyDumpDefaultsWideFlowsToEnabled() {
        String source = CustomLogicModel.escape("@startuml\nA -> B : Payment\\n(10)\n@end");
        StringTokenizer st = new StringTokenizer(source + " 560 1.0 1 250");

        SequenceDiagramElm restored = new SequenceDiagramElm(0, 0, 400, 300, 0, st);

        assertTrue(restored.isWideFlowModeEnabled(),
            "Older dumps without the extra flag should default wide flow rendering to enabled");
    }

    @Test
    @DisplayName("dump round-trip preserves wide flow mode flag")
    void dumpRoundTripPreservesWideFlowFlag() {
        SequenceDiagramElm original = new SequenceDiagramElm(10, 20);
        original.setWideFlowModeEnabled(true);

        StringTokenizer st = new StringTokenizer(original.dump());
        assertEquals("467", st.nextToken(), "Expected SequenceDiagramElm dump type");

        int xa = Integer.parseInt(st.nextToken());
        int ya = Integer.parseInt(st.nextToken());
        int xb = Integer.parseInt(st.nextToken());
        int yb = Integer.parseInt(st.nextToken());
        int flags = Integer.parseInt(st.nextToken());

        SequenceDiagramElm restored = new SequenceDiagramElm(xa, ya, xb, yb, flags, st);

        assertTrue(restored.isWideFlowModeEnabled(),
            "Wide proportional flow rendering should survive dump/undump");
    }

    @Test
    @DisplayName("known sector names reuse Sankey palette colors")
    void knownSectorNamesReuseSankeyPaletteColors() {
        assertEquals("#10B981", SequenceDiagramElm.getWideFlowPaletteColor("Firms"));
        assertEquals("#EF4444", SequenceDiagramElm.getWideFlowPaletteColor("Govt"));
    }
}
