package com.lushprojects.circuitjs1.client.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.CircuitJavaSimTestBase;
import com.lushprojects.circuitjs1.client.VariableHistoryStore;
import com.lushprojects.circuitjs1.client.elements.electronics.wiring.LabeledNodeElm;
import com.lushprojects.circuitjs1.client.util.Rectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scope variable history reuse")
class ScopeVariableHistoryReuseTest extends CircuitJavaSimTestBase {

    @Test
    @DisplayName("standard scrolling mode preloads circular buffers from captured variable history")
    void standardModePreloadsCircularBuffersFromCapturedVariableHistory() throws Exception {
	Scope scope = createVariableScopeWithHistory();

	ScopePlot plot = scope.plots.get(0);

	assertFalse(scope.isUsingHistoryInStandardModeForRender(plot));
	assertEquals(3, plot.samplesCaptured);
	assertEquals(0, plot.startIndex(scope.scopePointCount));
	assertEquals(10.0, plot.maxValues[0], 1e-12);
	assertEquals(12.5, plot.maxValues[1], 1e-12);
	assertEquals(15.0, plot.maxValues[plot.ptr], 1e-12);
	assertEquals(15.0, plot.minValues[plot.ptr], 1e-12);
    }

    @Test
    @DisplayName("draw-from-zero reuses captured variable history for labeled-node scopes")
    void drawFromZeroReusesCapturedVariableHistoryForLabeledNodeScopes() throws Exception {
	Scope scope = createVariableScopeWithHistory();

	assertFalse(scope.isDrawFromZeroEnabledForUi());
	assertEquals(5.0, sim.getTime(), 1e-12);

	scope.toggleDrawFromZero();

	assertTrue(scope.isDrawFromZeroEnabledForUi());
	assertEquals(5.0, sim.getTime(), 1e-12, "existing variable history should avoid a simulation reset");
	assertTrue(scope.hasHistoryForExport(), "scope should expose history when variable history exists");
	assertEquals(3, scope.getHistorySizeForRender());
	assertEquals(1.0, scope.getHistorySampleIntervalForRender(), 1e-12);

	VariableHistoryStore.SeriesSnapshot snapshot = scope.getHistorySnapshotForRender(scope.plots.get(0));
	assertNotNull(snapshot);
	assertEquals(3, snapshot.size());
	assertEquals(10.0, snapshot.minValues[0], 1e-12);
	assertEquals(12.5, snapshot.maxValues[1], 1e-12);
	assertEquals(15.0, snapshot.values[2], 1e-12);
    }

    @Test
    @DisplayName("explicit reset clears preloaded circular buffers")
    void explicitResetClearsPreloadedCircularBuffers() throws Exception {
	Scope scope = createVariableScopeWithHistory();
	ScopePlot plot = scope.plots.get(0);

	assertEquals(3, plot.samplesCaptured);

	scope.resetGraph(true);

	assertEquals(1, plot.samplesCaptured);
	assertEquals(0, plot.ptr);
	assertEquals(0.0, plot.minValues[plot.ptr], 1e-12);
	assertEquals(0.0, plot.maxValues[plot.ptr], 1e-12);
    }

    @Test
    @DisplayName("adding a variable trace preloads reusable history into scrolling scope")
    void addingVariableTracePreloadsReusableHistory() throws Exception {
	Scope scope = createVariableScopeWithHistory();
	LabeledNodeElm secondElm = new LabeledNodeElm(48, 48);
	secondElm.text = "Inflation";
	sim.getVariableHistoryStore().captureVariableSample("Inflation", 3.0, 1.0);
	sim.getVariableHistoryStore().captureVariableSample("Inflation", 4.0, 1.5);
	sim.getVariableHistoryStore().captureVariableSample("Inflation", 5.0, 2.0);

	int oldPlotCount = scope.plots.size();
	ScopeSelectionService.addValue(scope, 0, secondElm);

	assertTrue(scope.plots.size() > oldPlotCount);
	ScopePlot addedPlot = scope.plots.get(scope.plots.size() - 1);
	assertEquals(3, addedPlot.samplesCaptured);
	assertEquals(1.0, addedPlot.maxValues[0], 1e-12);
	assertEquals(1.5, addedPlot.maxValues[1], 1e-12);
	assertEquals(2.0, addedPlot.maxValues[addedPlot.ptr], 1e-12);
	assertNotEquals(0.0, scope.plots.get(0).maxValues[scope.plots.get(0).ptr], 1e-12);
    }

    private Scope createVariableScopeWithHistory() throws Exception {
	loadCircuit("src/com/lushprojects/circuitjs1/public/circuits/economics/lrc.txt");

	LabeledNodeElm variableElm = new LabeledNodeElm(32, 32);
	variableElm.text = "GDP";
	CircuitElm.setColorScaleForUi();

	Scope scope = new Scope(sim);
	scope.clearPlotsInternal();
	scope.addPlotInternal(variableElm, Scope.UNITS_V, Scope.VAL_VOLTAGE,
		scope.getManScaleFromMaxScale(Scope.UNITS_V, false));
	scope.setShowVoltageVisible(true);
	scope.calcVisiblePlots();
	scope.resetGraph();
	scope.setRectForEmbedded(new Rectangle(0, 0, 240, 120));

	scope.setSpeedForPersistence(1);
	sim.getVariableHistoryStore().captureVariableSample("GDP", 3.0, 10.0);
	sim.getVariableHistoryStore().captureVariableSample("GDP", 4.0, 12.5);
	sim.getVariableHistoryStore().captureVariableSample("GDP", 5.0, 15.0);
	sim.setTime(5.0);
	scope.resetGraph();

	return scope;
    }
}