package com.lushprojects.circuitjs1.client.core;

public interface SimulationContext {
    void stampResistor(int n1, int n2, double r);
    void stampConductance(int n1, int n2, double g);
    void stampMatrix(int i, int j, double x);
    void stampRightSide(int i, double x);
    void stampRightSide(int i);
    void stampNonLinear(int i);
    void stampVoltageSource(int n1, int n2, int vs, double v);
    void stampVoltageSource(int n1, int n2, int vs);
    void updateVoltageSource(int n1, int n2, int vs, double v);
    void stampCurrentSource(int n1, int n2, double i);
    void stampVCCurrentSource(int cn1, int cn2, int vn1, int vn2, double g);
    void stampCCCS(int n1, int n2, int vs, double gain);
    void stampVCVS(int n1, int n2, double coef, int vs);

    double getTimeStep();
    double getTime();
    int getSubIterations();
    boolean isConverged();
    void setConverged(boolean converged);
}
