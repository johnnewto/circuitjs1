package com.lushprojects.circuitjs1.client;


import com.lushprojects.circuitjs1.client.core.SimulationContext;

final class SimulationContextAdapter implements SimulationContext {
    private final CirSim sim;

    SimulationContextAdapter(CirSim sim) {
        this.sim = sim;
    }

    @Override
    public void stampResistor(int n1, int n2, double r) {
        sim.stampResistor(n1, n2, r);
    }

    @Override
    public void stampConductance(int n1, int n2, double g) {
        sim.stampConductance(n1, n2, g);
    }

    @Override
    public void stampMatrix(int i, int j, double x) {
        sim.stampMatrix(i, j, x);
    }

    @Override
    public void stampRightSide(int i, double x) {
        sim.stampRightSide(i, x);
    }

    @Override
    public void stampRightSide(int i) {
        sim.stampRightSide(i);
    }

    @Override
    public void stampNonLinear(int i) {
        sim.stampNonLinear(i);
    }

    @Override
    public void stampVoltageSource(int n1, int n2, int vs, double v) {
        sim.stampVoltageSource(n1, n2, vs, v);
    }

    @Override
    public void stampVoltageSource(int n1, int n2, int vs) {
        sim.stampVoltageSource(n1, n2, vs);
    }

    @Override
    public void updateVoltageSource(int n1, int n2, int vs, double v) {
        sim.updateVoltageSource(n1, n2, vs, v);
    }

    @Override
    public void stampCurrentSource(int n1, int n2, double i) {
        sim.stampCurrentSource(n1, n2, i);
    }

    @Override
    public void stampVCCurrentSource(int cn1, int cn2, int vn1, int vn2, double g) {
        sim.stampVCCurrentSource(cn1, cn2, vn1, vn2, g);
    }

    @Override
    public void stampCCCS(int n1, int n2, int vs, double gain) {
        sim.stampCCCS(n1, n2, vs, gain);
    }

    @Override
    public void stampVCVS(int n1, int n2, double coef, int vs) {
        sim.stampVCVS(n1, n2, coef, vs);
    }

    @Override
    public double getTimeStep() {
        return sim.getTimeStep();
    }

    @Override
    public double getTime() {
        return sim.getTime();
    }

    @Override
    public int getSubIterations() {
        return sim.getSubIterations();
    }

    @Override
    public boolean isConverged() {
        return sim.isConverged();
    }

    @Override
    public void setConverged(boolean converged) {
        sim.setConverged(converged);
    }
}
